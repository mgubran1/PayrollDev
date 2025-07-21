package com.company.payroll.loads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.payroll.employees.Employee;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trucks.Truck;
import com.company.payroll.trucks.TruckDAO;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Enterprise-grade data cache manager for high-performance data access
 * Handles 5500+ addresses, customers, trucks, employees, and trailers
 * with intelligent caching, background refresh, and memory optimization
 */
public class EnterpriseDataCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(EnterpriseDataCacheManager.class);
    
    // Singleton instance for global access
    private static volatile EnterpriseDataCacheManager instance;
    private static final Object LOCK = new Object();
    
    // Data access objects
    private final LoadDAO loadDAO;
    private final TruckDAO truckDAO;
    
    // Cache storage with thread-safe collections
    private final ConcurrentHashMap<String, List<CustomerAddress>> customerAddressCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final ObservableList<String> cachedCustomers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<String> cachedBillingEntities = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<Employee> cachedDrivers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<Truck> cachedTrucks = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<Trailer> cachedTrailers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    
    // Cache configuration
    private static final long CACHE_EXPIRY_MS = 1 * 60 * 1000; // 1 minute
    private static final int MAX_CACHE_SIZE = 10000; // Maximum cached customers
    private static final int BACKGROUND_REFRESH_INTERVAL_MINUTES = 3;
    
    // Thread pools for async operations
    private final ExecutorService cacheExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "CacheManager-" + System.currentTimeMillis());
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1); // Lower priority to not block UI
        return t;
    });
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "CacheRefresh-" + System.currentTimeMillis());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY); // Lowest priority for background tasks
        return t;
    });
    
    // Cache statistics for monitoring
    private volatile int cacheHits = 0;
    private volatile int cacheMisses = 0;
    private volatile long lastFullRefresh = 0;
    
    private EnterpriseDataCacheManager() {
        this.loadDAO = new LoadDAO();
        this.truckDAO = new TruckDAO();
        initializeCache();
        startBackgroundRefresh();
    }
    
    /**
     * Get singleton instance with thread-safe lazy initialization
     */
    public static EnterpriseDataCacheManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new EnterpriseDataCacheManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize cache with essential data
     */
    private void initializeCache() {
        logger.info("Initializing enterprise data cache...");
        
        // Load essential data in background to avoid blocking UI
        CompletableFuture.runAsync(() -> {
            try {
                // Load customers and billing entities
                refreshCustomersAsync();
                refreshBillingEntitiesAsync();
                refreshDriversAsync();
                refreshTrucksAsync();
                refreshTrailersAsync();
                
                lastFullRefresh = System.currentTimeMillis();
                logger.info("Enterprise data cache initialized successfully");
                
            } catch (Exception e) {
                logger.error("Error initializing cache", e);
            }
        }, cacheExecutor);
    }
    
    /**
     * Start background refresh scheduler
     */
    private void startBackgroundRefresh() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                logger.debug("Starting background cache refresh");
                refreshCustomersAsync();
                refreshBillingEntitiesAsync();
                refreshDriversAsync();
                refreshTrucksAsync();
                refreshTrailersAsync();
                
                // Clean up expired address cache entries
                cleanupExpiredAddressCache();
                
                lastFullRefresh = System.currentTimeMillis();
                logger.debug("Background cache refresh completed");
                
            } catch (Exception e) {
                logger.error("Error during background cache refresh", e);
            }
        }, BACKGROUND_REFRESH_INTERVAL_MINUTES, BACKGROUND_REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    /**
     * Get customer addresses with intelligent caching
     */
    public CompletableFuture<List<CustomerAddress>> getCustomerAddressesAsync(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        String normalizedCustomer = customerName.trim().toUpperCase();
        
        // Check cache first
        if (isAddressCacheValid(normalizedCustomer)) {
            cacheHits++;
            List<CustomerAddress> cachedAddresses = customerAddressCache.get(normalizedCustomer);
            logger.debug("Cache hit for customer: {} ({} addresses)", normalizedCustomer, cachedAddresses.size());
            return CompletableFuture.completedFuture(new ArrayList<>(cachedAddresses));
        }
        
        // Cache miss - load from database
        cacheMisses++;
        logger.debug("Cache miss for customer: {}, loading from database", normalizedCustomer);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(normalizedCustomer);
                
                // Cache the result
                customerAddressCache.put(normalizedCustomer, new ArrayList<>(addresses));
                cacheTimestamps.put(normalizedCustomer, System.currentTimeMillis());
                
                // Enforce cache size limit
                if (customerAddressCache.size() > MAX_CACHE_SIZE) {
                    cleanupOldestCacheEntries();
                }
                
                logger.debug("Loaded and cached {} addresses for customer: {}", addresses.size(), normalizedCustomer);
                return addresses;
                
            } catch (Exception e) {
                logger.error("Error loading addresses for customer: {}", normalizedCustomer, e);
                return new ArrayList<>();
            }
        }, cacheExecutor);
    }
    
    /**
     * Get all customers (cached)
     */
    public ObservableList<String> getCachedCustomers() {
        if (cachedCustomers.isEmpty()) {
            // Trigger async refresh if cache is empty
            refreshCustomersAsync();
        }
        return cachedCustomers;
    }
    
    /**
     * Get all billing entities (cached)
     */
    public ObservableList<String> getCachedBillingEntities() {
        if (cachedBillingEntities.isEmpty()) {
            // Trigger async refresh if cache is empty
            refreshBillingEntitiesAsync();
        }
        return cachedBillingEntities;
    }
    
    /**
     * Get all drivers (cached)
     */
    public ObservableList<Employee> getCachedDrivers() {
        if (cachedDrivers.isEmpty()) {
            refreshDriversAsync();
        }
        return cachedDrivers;
    }
    
    /**
     * Get all trucks (cached)
     */
    public ObservableList<Truck> getCachedTrucks() {
        if (cachedTrucks.isEmpty()) {
            refreshTrucksAsync();
        }
        return cachedTrucks;
    }
    
    /**
     * Get all trailers (cached)
     */
    public ObservableList<Trailer> getCachedTrailers() {
        if (cachedTrailers.isEmpty()) {
            refreshTrailersAsync();
        }
        return cachedTrailers;
    }
    
    /**
     * Search customers with caching and fuzzy matching
     */
    public List<String> searchCustomers(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return cachedCustomers.stream().limit(limit).collect(Collectors.toList());
        }
        
        String normalizedQuery = query.trim().toLowerCase();
        
        return cachedCustomers.stream()
            .filter(customer -> {
                String customerLower = customer.toLowerCase();
                return customerLower.equals(normalizedQuery) ||
                       customerLower.startsWith(normalizedQuery) ||
                       customerLower.contains(normalizedQuery);
            })
            .sorted((a, b) -> {
                String aLower = a.toLowerCase();
                String bLower = b.toLowerCase();
                
                // Exact match first
                if (aLower.equals(normalizedQuery) && !bLower.equals(normalizedQuery)) return -1;
                if (bLower.equals(normalizedQuery) && !aLower.equals(normalizedQuery)) return 1;
                
                // Starts with second
                if (aLower.startsWith(normalizedQuery) && !bLower.startsWith(normalizedQuery)) return -1;
                if (bLower.startsWith(normalizedQuery) && !aLower.startsWith(normalizedQuery)) return 1;
                
                // Alphabetical order
                return a.compareToIgnoreCase(b);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Add new customer and refresh cache
     */
    public CompletableFuture<Void> addCustomerAsync(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        String normalizedCustomer = customerName.trim().toUpperCase();
        
        return CompletableFuture.runAsync(() -> {
            try {
                loadDAO.addCustomerIfNotExists(normalizedCustomer);
                
                // Update cache immediately
                Platform.runLater(() -> {
                    if (!cachedCustomers.contains(normalizedCustomer)) {
                        cachedCustomers.add(normalizedCustomer);
                        cachedCustomers.sort(String::compareToIgnoreCase);
                    }
                    // Note: Don't automatically add customers to billing entities
                    // Billing entities are managed separately in Customer Settings
                });
                
                logger.debug("Added customer to cache: {}", normalizedCustomer);
                
            } catch (Exception e) {
                logger.error("Error adding customer: {}", normalizedCustomer, e);
                throw new RuntimeException("Failed to add customer: " + normalizedCustomer, e);
            }
        }, cacheExecutor);
    }
    
    /**
     * Add new address and update cache
     */
    public CompletableFuture<Integer> addCustomerAddressAsync(String customerName, String locationName, 
                                                             String address, String city, String state) {
        if (customerName == null || customerName.trim().isEmpty()) {
            return CompletableFuture.completedFuture(-1);
        }
        
        String normalizedCustomer = customerName.trim().toUpperCase();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                int addressId = loadDAO.addCustomerAddress(normalizedCustomer, locationName, address, city, state);
                
                if (addressId > 0) {
                    // Invalidate cache for this customer to force reload
                    customerAddressCache.remove(normalizedCustomer);
                    cacheTimestamps.remove(normalizedCustomer);
                    
                    logger.debug("Added address for customer: {} (ID: {})", normalizedCustomer, addressId);
                } else {
                    logger.warn("Failed to add address for customer: {}", normalizedCustomer);
                }
                
                return addressId;
                
            } catch (Exception e) {
                logger.error("Error adding address for customer: {}", normalizedCustomer, e);
                return -1;
            }
        }, cacheExecutor);
    }
    
    /**
     * Force refresh of specific customer's addresses
     */
    public CompletableFuture<List<CustomerAddress>> refreshCustomerAddressesAsync(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        String normalizedCustomer = customerName.trim().toUpperCase();
        
        // Remove from cache to force reload
        customerAddressCache.remove(normalizedCustomer);
        cacheTimestamps.remove(normalizedCustomer);
        
        return getCustomerAddressesAsync(normalizedCustomer);
    }
    
    /**
     * Invalidate all caches and force refresh
     */
    public CompletableFuture<Void> invalidateAllCaches() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Invalidating all caches");
            
            // Clear address cache
            customerAddressCache.clear();
            cacheTimestamps.clear();
            
            // Refresh all data
            refreshCustomersAsync();
            refreshBillingEntitiesAsync();
            refreshDriversAsync();
            refreshTrucksAsync();
            refreshTrailersAsync();
            
            lastFullRefresh = System.currentTimeMillis();
            logger.info("All caches invalidated and refreshed");
            
        }, cacheExecutor);
    }
    
    /**
     * Get cache statistics
     */
    public String getCacheStatistics() {
        double hitRatio = (cacheHits + cacheMisses) > 0 ? 
            (double) cacheHits / (cacheHits + cacheMisses) * 100 : 0;
        
        return String.format(
            "Cache Stats - Hits: %d, Misses: %d, Hit Ratio: %.1f%%, " +
            "Cached Customers: %d, Cached Addresses: %d, Last Refresh: %d min ago",
            cacheHits, cacheMisses, hitRatio,
            cachedCustomers.size(), customerAddressCache.size(),
            (System.currentTimeMillis() - lastFullRefresh) / 60000
        );
    }
    
    // Private helper methods
    
    private boolean isAddressCacheValid(String customerName) {
        if (!customerAddressCache.containsKey(customerName)) {
            return false;
        }
        
        Long timestamp = cacheTimestamps.get(customerName);
        if (timestamp == null) {
            return false;
        }
        
        return (System.currentTimeMillis() - timestamp) < CACHE_EXPIRY_MS;
    }
    
    private void cleanupExpiredAddressCache() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
            if ((currentTime - entry.getValue()) > CACHE_EXPIRY_MS) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (String key : expiredKeys) {
            customerAddressCache.remove(key);
            cacheTimestamps.remove(key);
        }
        
        if (!expiredKeys.isEmpty()) {
            logger.debug("Cleaned up {} expired cache entries", expiredKeys.size());
        }
    }
    
    private void cleanupOldestCacheEntries() {
        // Remove oldest 20% of cache entries when size limit is exceeded
        int entriesToRemove = MAX_CACHE_SIZE / 5;
        
        List<Map.Entry<String, Long>> sortedEntries = cacheTimestamps.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(entriesToRemove)
            .collect(Collectors.toList());
        
        for (Map.Entry<String, Long> entry : sortedEntries) {
            customerAddressCache.remove(entry.getKey());
            cacheTimestamps.remove(entry.getKey());
        }
        
        logger.debug("Cleaned up {} oldest cache entries", entriesToRemove);
    }
    
    private void refreshCustomersAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                List<String> customers = loadDAO.getAllCustomers();
                Platform.runLater(() -> {
                    cachedCustomers.setAll(customers);
                    logger.debug("Refreshed {} customers in cache", customers.size());
                });
            } catch (Exception e) {
                logger.error("Error refreshing customers", e);
            }
        }, cacheExecutor);
    }
    
    private void refreshBillingEntitiesAsync() {
        // Load billing entities from the proper billing_entities table
        CompletableFuture.runAsync(() -> {
            try {
                List<String> billingEntities = loadDAO.getAllBillingEntities();
                Platform.runLater(() -> {
                    cachedBillingEntities.setAll(billingEntities);
                    logger.debug("Refreshed {} billing entities in cache", billingEntities.size());
                });
            } catch (Exception e) {
                logger.error("Error refreshing billing entities", e);
            }
        }, cacheExecutor);
    }
    
    private void refreshDriversAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                // Note: You'll need to implement this in EmployeeDAO if not already available
                // List<Employee> drivers = employeeDAO.getAllDrivers();
                // For now, we'll assume this method exists or create a placeholder
                logger.debug("Driver cache refresh - method needs implementation");
            } catch (Exception e) {
                logger.error("Error refreshing drivers", e);
            }
        }, cacheExecutor);
    }
    
    private void refreshTrucksAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Truck> trucks = truckDAO.findAll();
                Platform.runLater(() -> {
                    cachedTrucks.setAll(trucks);
                    logger.debug("Refreshed {} trucks in cache", trucks.size());
                });
            } catch (Exception e) {
                logger.error("Error refreshing trucks", e);
            }
        }, cacheExecutor);
    }
    
    private void refreshTrailersAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                // Note: You'll need to implement this in TrailerDAO if not already available
                // List<Trailer> trailers = trailerDAO.getAllTrailers();
                // For now, we'll assume this method exists or create a placeholder
                logger.debug("Trailer cache refresh - method needs implementation");
            } catch (Exception e) {
                logger.error("Error refreshing trailers", e);
            }
        }, cacheExecutor);
    }
    
    /**
     * Shutdown cache manager and cleanup resources
     */
    public void shutdown() {
        logger.info("Shutting down Enterprise Data Cache Manager");
        
        try {
            scheduledExecutor.shutdown();
            cacheExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!cacheExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheExecutor.shutdownNow();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted during shutdown", e);
        }
        
        // Clear all caches
        customerAddressCache.clear();
        cacheTimestamps.clear();
        cachedCustomers.clear();
        cachedBillingEntities.clear();
        cachedDrivers.clear();
        cachedTrucks.clear();
        cachedTrailers.clear();
        
        logger.info("Enterprise Data Cache Manager shut down completed");
    }
}