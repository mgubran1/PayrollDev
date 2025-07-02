package com.company.payroll.dispatcher;

import com.company.payroll.drivers.Driver;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadStatus;
import com.company.payroll.exceptions.DispatcherException;
import com.company.payroll.services.DataService;
import com.company.payroll.services.NotificationService;
import com.company.payroll.security.SecurityContext;

import javafx.application.Platform;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Region;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Controller for dispatcher operations, handling interactions between the UI
 * and data services for driver and load management
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherController {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Current user and timestamp information
    private final String currentUser = SecurityContext.getCurrentUser() != null ? 
        SecurityContext.getCurrentUser() : "mgubran1";
    private final LocalDateTime currentDateTime = LocalDateTime.now();
    
    // Services
    private final DataService dataService;
    private final NotificationService notificationService;
    
    // Observable collections for UI binding
    private final ObservableList<DispatcherDriverStatus> driverStatuses;
    private final ObservableList<Load> activeLoads;
    private final Map<String, List<Load>> driverLoadsCache;
    
    // Thread pool for background tasks
    private final ExecutorService executorService;
    
    // Status listeners
    private final List<StatusChangeListener> statusListeners;
    
    /**
     * Constructor - initializes services and data collections
     */
    public DispatcherController() {
        this.dataService = DataService.getInstance();
        this.notificationService = NotificationService.getInstance();
        this.driverStatuses = FXCollections.observableArrayList();
        this.activeLoads = FXCollections.observableArrayList();
        this.driverLoadsCache = new HashMap<>();
        this.executorService = Executors.newFixedThreadPool(3);
        this.statusListeners = new ArrayList<>();
        
        // Initial data load
        initialize();
        
        logger.info("DispatcherController initialized by {} at {}", 
            currentUser, currentDateTime.format(DATETIME_FORMAT));
    }
    
    /**
     * Initialize the controller and load data
     */
    private void initialize() {
        try {
            loadDriverStatuses();
            loadActiveLoads();
            
            // Register for real-time updates if available
            registerForUpdates();
            
            // Start auto-refresh timer
            startAutoRefresh();
            
        } catch (Exception e) {
            logger.error("Failed to initialize dispatcher controller", e);
            showErrorDialog("Initialization Error", 
                "Failed to initialize dispatcher system: " + e.getMessage());
        }
    }
    
    /**
     * Get observable list of driver statuses
     * @return ObservableList of driver statuses
     */
    public ObservableList<DispatcherDriverStatus> getDriverStatuses() {
        return FXCollections.unmodifiableObservableList(driverStatuses);
    }
    
    /**
     * Get observable list of active loads
     * @return ObservableList of loads
     */
    public ObservableList<Load> getActiveLoads() {
        return FXCollections.unmodifiableObservableList(activeLoads);
    }
    
    /**
     * Load driver status data
     */
    private void loadDriverStatuses() {
        List<Driver> drivers = dataService.getAllDrivers();
        
        driverStatuses.clear();
        for (Driver driver : drivers) {
            DispatcherDriverStatus.Status status = determineDriverStatus(driver);
            String location = determineDriverLocation(driver);
            String notes = driver.getNotes();
            LocalDateTime eta = determineDriverETA(driver);
            
            DispatcherDriverStatus driverStatus = new DispatcherDriverStatus(
                driver, status, location, eta, notes
            );
            
            driverStatuses.add(driverStatus);
        }
        
        logger.info("Loaded status for {} drivers", driverStatuses.size());
    }
    
    /**
     * Load active loads data
     */
    private void loadActiveLoads() {
        List<Load> loads = dataService.getActiveLoads();
        activeLoads.setAll(loads);
        logger.info("Loaded {} active loads", activeLoads.size());
    }
    
    /**
     * Determine driver status based on current assignment and state
     * @param driver The driver to check
     * @return Current status
     */
    private DispatcherDriverStatus.Status determineDriverStatus(Driver driver) {
        // In a real system, this would use driver's current state, 
        // ELD status, and assignment data
        
        // Simulated logic for demo
        if (driver.getCurrentLoad() != null) {
            Load currentLoad = driver.getCurrentLoad();
            
            if (currentLoad.getLoadStatus() == LoadStatus.LOADING) {
                return DispatcherDriverStatus.Status.LOADING;
            } else if (currentLoad.getLoadStatus() == LoadStatus.UNLOADING) {
                return DispatcherDriverStatus.Status.UNLOADING;
            } else {
                return DispatcherDriverStatus.Status.ON_ROAD;
            }
        }
        
        if (driver.isOnDuty()) {
            if (driver.isAvailableForDispatch()) {
                return DispatcherDriverStatus.Status.AVAILABLE;
            } else {
                return DispatcherDriverStatus.Status.BREAK;
            }
        }
        
        if (driver.isInSleeper()) {
            return DispatcherDriverStatus.Status.SLEEPER;
        }
        
        return DispatcherDriverStatus.Status.OFF_DUTY;
    }
    
    /**
     * Determine driver location based on current assignment and GPS
     * @param driver The driver to check
     * @return Current location
     */
    private String determineDriverLocation(Driver driver) {
        // In a real system, this would use GPS tracking data and geofencing
        
        // Simulated logic for demo
        if (driver.getCurrentLoad() != null) {
            Load load = driver.getCurrentLoad();
            
            if (load.getLoadStatus() == LoadStatus.LOADING) {
                return "At " + load.getOriginName();
            } else if (load.getLoadStatus() == LoadStatus.UNLOADING) {
                return "At " + load.getDestName();
            } else if (load.getLoadStatus() == LoadStatus.IN_TRANSIT) {
                // Simulate GPS location
                return "En route to " + load.getDestName();
            }
        }
        
        return driver.getHomeTerminal();
    }
    
    /**
     * Determine driver ETA based on current assignment
     * @param driver The driver to check
     * @return Estimated time of arrival
     */
    private LocalDateTime determineDriverETA(Driver driver) {
        if (driver.getCurrentLoad() != null) {
            Load load = driver.getCurrentLoad();
            
            if (load.getLoadStatus() == LoadStatus.IN_TRANSIT) {
                return load.getDeliveryDate().atTime(12, 0);
            } else if (load.getLoadStatus() == LoadStatus.ASSIGNED) {
                return load.getPickupDate().atTime(8, 0);
            }
        }
        
        return null;
    }
    
    /**
     * Get loads for a specific driver in a date range
     * @param driver The driver
     * @param startDate Start date
     * @param endDate End date
     * @return List of loads for the driver
     */
    public List<Load> getLoadsForDriverAndRange(Driver driver, LocalDate startDate, LocalDate endDate) {
        String driverId = driver.getDriverId();
        
        // Check cache first
        String cacheKey = driverId + "_" + startDate + "_" + endDate;
        if (driverLoadsCache.containsKey(cacheKey)) {
            logger.debug("Cache hit for driver loads: {}", cacheKey);
            return driverLoadsCache.get(cacheKey);
        }
        
        logger.info("Fetching loads for driver {} from {} to {}", 
            driver.getName(), startDate, endDate);
        
        List<Load> loads = dataService.getLoadsForDriver(driver, startDate, endDate);
        
        // Cache the result
        driverLoadsCache.put(cacheKey, new ArrayList<>(loads));
        
        return loads;
    }
    
    /**
     * Update driver status
     * @param driverStatus The driver status to update
     * @param newStatus New status
     * @param location New location (can be null to keep current)
     * @param eta New ETA (can be null to keep current)
     * @param notes Notes to add (can be null or empty for no change)
     */
    public void updateDriverStatus(DispatcherDriverStatus driverStatus, 
                                  DispatcherDriverStatus.Status newStatus,
                                  String location, 
                                  LocalDateTime eta, 
                                  String notes) {
        Driver driver = driverStatus.getDriver();
        String oldStatus = driverStatus.getStatus().toString();
        
        logger.info("Updating driver status: {} from {} to {}", 
            driver.getName(), oldStatus, newStatus);
        
        // Update driver in data service
        try {
            // Record previous state for notification
            DispatcherDriverStatus previousStatus = new DispatcherDriverStatus(
                driver, driverStatus.getStatus(), 
                driverStatus.getLocation(), 
                driverStatus.getETA(),
                driverStatus.getNotes()
            );
            
            // Update location if provided
            if (location != null && !location.isEmpty()) {
                driver.setLocation(location);
            }
            
            // Update notes if provided
            if (notes != null && !notes.isEmpty()) {
                String currentNotes = driver.getNotes();
                if (currentNotes != null && !currentNotes.isEmpty()) {
                    driver.setNotes(currentNotes + "\n" + notes);
                } else {
                    driver.setNotes(notes);
                }
            }
            
            // Update status in the data service
            dataService.updateDriverStatus(driver, convertStatus(newStatus));
            
            // Update the observable status object
            driverStatus.setStatus(newStatus);
            if (location != null && !location.isEmpty()) {
                driverStatus.setLocation(location);
            }
            if (eta != null) {
                driverStatus.setETA(eta);
            }
            if (notes != null && !notes.isEmpty()) {
                driverStatus.setNotes(driver.getNotes());
            }
            
            // Notify status listeners
            notifyStatusListeners(previousStatus, driverStatus);
            
        } catch (Exception e) {
            logger.error("Failed to update driver status", e);
            throw new DispatcherException("Failed to update driver status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert internal status to data service status
     * @param status Internal status
     * @return Data service status
     */
    private String convertStatus(DispatcherDriverStatus.Status status) {
        // Map internal status enum to data service status strings
        switch (status) {
            case AVAILABLE: return "AVAILABLE";
            case ON_ROAD: return "ON_ROAD";
            case LOADING: return "LOADING";
            case UNLOADING: return "UNLOADING";
            case BREAK: return "BREAK";
            case OFF_DUTY: return "OFF_DUTY";
            case SLEEPER: return "SLEEPER";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * Refresh all data
     */
    public void refreshData() {
        logger.info("Manual data refresh triggered by {}", currentUser);
        
        Task<Void> refreshTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Refreshing driver statuses...");
                loadDriverStatuses();
                
                updateMessage("Refreshing active loads...");
                loadActiveLoads();
                
                updateMessage("Clearing cache...");
                driverLoadsCache.clear();
                
                return null;
            }
        };
        
        refreshTask.setOnSucceeded(e -> {
            logger.info("Data refresh completed");
        });
        
        refreshTask.setOnFailed(e -> {
            logger.error("Data refresh failed", refreshTask.getException());
        });
        
        executorService.submit(refreshTask);
    }
    
    /**
     * Register for real-time updates
     */
    private void registerForUpdates() {
        // In a real system, this would register for push notifications
        // from the server for real-time updates
        
        dataService.registerForUpdates(update -> {
            if (update.getType().equals("DRIVER_STATUS")) {
                Platform.runLater(() -> handleDriverStatusUpdate(update));
            } else if (update.getType().equals("LOAD_UPDATE")) {
                Platform.runLater(() -> handleLoadUpdate(update));
            }
        });
    }
    
    /**
     * Handle driver status update from server
     * @param update Update data
     */
    private void handleDriverStatusUpdate(DataUpdate update) {
        String driverId = update.getEntityId();
        
        // Find the driver in our list
        for (DispatcherDriverStatus status : driverStatuses) {
            if (status.getDriver().getDriverId().equals(driverId)) {
                // Get updated data
                Driver updatedDriver = dataService.getDriverById(driverId);
                
                // Record previous status for notification
                DispatcherDriverStatus previousStatus = new DispatcherDriverStatus(
                    status.getDriver(), status.getStatus(), 
                    status.getLocation(), status.getETA(), status.getNotes()
                );
                
                // Update the status object
                status.setStatus(determineDriverStatus(updatedDriver));
                status.setLocation(determineDriverLocation(updatedDriver));
                status.setETA(determineDriverETA(updatedDriver));
                status.setNotes(updatedDriver.getNotes());
                status.setDriver(updatedDriver);
                
                // Notify listeners
                notifyStatusListeners(previousStatus, status);
                
                break;
            }
        }
    }
    
    /**
     * Handle load update from server
     * @param update Update data
     */
    private void handleLoadUpdate(DataUpdate update) {
        String loadId = update.getEntityId();
        
        // Update active loads
        for (int i = 0; i < activeLoads.size(); i++) {
            if (activeLoads.get(i).getLoadId().equals(loadId)) {
                Load updatedLoad = dataService.getLoadById(loadId);
                
                if (updatedLoad != null) {
                    activeLoads.set(i, updatedLoad);
                } else {
                    // Load might have been completed or canceled
                    activeLoads.remove(i);
                }
                
                break;
            }
        }
        
        // Clear cache for affected drivers
        String driverId = update.getProperty("driverId");
        if (driverId != null) {
            driverLoadsCache.keySet().stream()
                .filter(key -> key.startsWith(driverId + "_"))
                .collect(Collectors.toList())
                .forEach(driverLoadsCache::remove);
        }
    }
    
    /**
     * Start auto-refresh timer
     */
    private void startAutoRefresh() {
        int refreshInterval = DispatcherSettings.getInstance().getAutoRefreshInterval();
        
        Timer refreshTimer = new Timer("DispatcherRefreshTimer", true);
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    try {
                        loadDriverStatuses();
                        loadActiveLoads();
                    } catch (Exception e) {
                        logger.error("Auto-refresh failed", e);
                    }
                });
            }
        }, refreshInterval * 1000, refreshInterval * 1000);
        
        logger.info("Auto-refresh timer started with interval {} seconds", refreshInterval);
    }
    
    /**
     * Add a listener for status changes
     * @param listener Listener to add
     */
    public void addStatusChangeListener(StatusChangeListener listener) {
        if (!statusListeners.contains(listener)) {
            statusListeners.add(listener);
        }
    }
    
    /**
     * Remove a status change listener
     * @param listener Listener to remove
     */
    public void removeStatusChangeListener(StatusChangeListener listener) {
        statusListeners.remove(listener);
    }
    
    /**
     * Notify all listeners of a status change
     * @param oldStatus Previous status
     * @param newStatus Updated status
     */
    private void notifyStatusListeners(DispatcherDriverStatus oldStatus, DispatcherDriverStatus newStatus) {
        for (StatusChangeListener listener : statusListeners) {
            listener.onStatusChanged(oldStatus, newStatus);
        }
    }
    
    /**
     * Send notification about a status update
     * @param driverStatus Updated driver status
     * @param reason Reason for update
     */
    public void sendStatusNotification(DispatcherDriverStatus driverStatus, String reason) {
        Driver driver = driverStatus.getDriver();
        
        Map<String, String> params = new HashMap<>();
        params.put("driverName", driver.getName());
        params.put("status", driverStatus.getStatus().getDisplayName());
        params.put("location", driverStatus.getLocation());
        params.put("reason", reason);
        params.put("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
        
        CompletableFuture.runAsync(() -> {
            try {
                notificationService.sendNotification(
                    "DRIVER_STATUS_UPDATE",
                    "Driver Status Update: " + driver.getName(),
                    params,
                    Arrays.asList("dispatch", "operations")
                );
                
                logger.info("Status notification sent for driver {}", driver.getName());
            } catch (Exception e) {
                logger.error("Failed to send status notification", e);
            }
        });
    }
    
    /**
     * Assign a load to a driver
     * @param driver The driver to assign
     * @param load The load to assign
     */
    public void assignLoad(Driver driver, Load load) {
        logger.info("Assigning load #{} to driver {}", load.getLoadNumber(), driver.getName());
        
        try {
            // Update the load with the driver assignment
            dataService.assignLoadToDriver(load.getLoadId(), driver.getDriverId());
            
            // Update the driver's current load
            driver.setCurrentLoad(load);
            
            // Update status if driver was available
            for (DispatcherDriverStatus status : driverStatuses) {
                if (status.getDriver().equals(driver) && 
                    status.getStatus() == DispatcherDriverStatus.Status.AVAILABLE) {
                    
                    // Update status to assigned/on road
                    updateDriverStatus(
                        status, 
                        DispatcherDriverStatus.Status.ON_ROAD, 
                        "En route to " + load.getOriginName(), 
                        load.getPickupDate().atTime(8, 0), 
                        "Assigned to load #" + load.getLoadNumber()
                    );
                    
                    break;
                }
            }
            
            // Refresh active loads
            loadActiveLoads();
            
            // Clear cache for this driver
            String driverId = driver.getDriverId();
            driverLoadsCache.keySet().stream()
                .filter(key -> key.startsWith(driverId + "_"))
                .collect(Collectors.toList())
                .forEach(driverLoadsCache::remove);
                
        } catch (Exception e) {
            logger.error("Failed to assign load", e);
            throw new DispatcherException("Failed to assign load: " + e.getMessage(), e);
        }
    }
    
    /**
     * Unassign a load from a driver
     * @param load The load to unassign
     */
    public void unassignLoad(Load load) {
        if (load.getDriver() == null) {
            logger.warn("Attempt to unassign load #{} which has no driver", load.getLoadNumber());
            return;
        }
        
        Driver driver = load.getDriver();
        logger.info("Unassigning load #{} from driver {}", load.getLoadNumber(), driver.getName());
        
        try {
            // Update the load to remove driver assignment
            dataService.unassignLoad(load.getLoadId());
            
            // Update the driver's current load if it matches
            if (driver.getCurrentLoad() != null && 
                driver.getCurrentLoad().getLoadId().equals(load.getLoadId())) {
                driver.setCurrentLoad(null);
                
                // Update driver status if needed
                for (DispatcherDriverStatus status : driverStatuses) {
                    if (status.getDriver().equals(driver)) {
                        updateDriverStatus(
                            status,
                            DispatcherDriverStatus.Status.AVAILABLE,
                            driver.getHomeTerminal(),
                            null,
                            "Unassigned from load #" + load.getLoadNumber()
                        );
                        break;
                    }
                }
            }
            
            // Refresh active loads
            loadActiveLoads();
            
            // Clear cache for this driver
            String driverId = driver.getDriverId();
            driverLoadsCache.keySet().stream()
                .filter(key -> key.startsWith(driverId + "_"))
                .collect(Collectors.toList())
                .forEach(driverLoadsCache::remove);
                
        } catch (Exception e) {
            logger.error("Failed to unassign load", e);
            throw new DispatcherException("Failed to unassign load: " + e.getMessage(), e);
        }
    }
    
    /**
     * Shutdown the controller, releasing resources
     */
    public void shutdown() {
        executorService.shutdown();
        
        try {
            dataService.unregisterForUpdates();
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
        
        logger.info("DispatcherController shutdown by {}", currentUser);
    }
    
    /**
     * Show error dialog
     * @param title Dialog title
     * @param message Error message
     */
    private void showErrorDialog(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            
            // Ensure dialog is resizable for long messages
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
            
            alert.showAndWait();
        });
    }
    
    /**
     * Interface for status change listeners
     */
    public interface StatusChangeListener {
        void onStatusChanged(DispatcherDriverStatus oldStatus, DispatcherDriverStatus newStatus);
    }
    
    /**
     * Data update class for receiving push notifications
     */
    private static class DataUpdate {
        private String type;
        private String entityId;
        private Map<String, String> properties;
        
        public String getType() { return type; }
        public String getEntityId() { return entityId; }
        
        public String getProperty(String key) {
            return properties != null ? properties.get(key) : null;
        }
    }
}