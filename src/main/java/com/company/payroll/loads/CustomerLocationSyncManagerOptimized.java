package com.company.payroll.loads;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optimized synchronization manager that uses on-demand database queries
 * instead of pre-loading all customer and location data.
 */
public class CustomerLocationSyncManagerOptimized {
    private static final Logger logger = LoggerFactory.getLogger(CustomerLocationSyncManagerOptimized.class);
    
    // Properties for synchronized state
    private final StringProperty pickupCustomer = new SimpleStringProperty();
    private final StringProperty pickupLocation = new SimpleStringProperty();
    private final StringProperty dropCustomer = new SimpleStringProperty();
    private final StringProperty dropLocation = new SimpleStringProperty();
    
    // Data access
    private final LoadDAO loadDAO;
    
    // Performance settings
    private static final int SEARCH_DELAY_MS = 300;
    private static final int MAX_RESULTS = 30;
    private static final int MIN_SEARCH_LENGTH = 1;
    
    // Threading
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Components to sync
    private ComboBox<String> pickupCustomerBox;
    private EnhancedLocationFieldOptimized pickupLocationField;
    private ComboBox<String> dropCustomerBox;
    private EnhancedLocationFieldOptimized dropLocationField;
    
    // Autocomplete handlers
    private SimpleAutocompleteHandler pickupCustomerHandler;
    private SimpleAutocompleteHandler dropCustomerHandler;
    
    // Listeners
    private final List<ChangeListener<?>> activeListeners = new ArrayList<>();
    private boolean suppressEvents = false;
    
    public CustomerLocationSyncManagerOptimized(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
    }
    
    /**
     * Attach synchronization to UI components
     */
    public void attachComponents(ComboBox<String> pickupCustomerBox, 
                                EnhancedLocationFieldOptimized pickupLocationField,
                                ComboBox<String> dropCustomerBox, 
                                EnhancedLocationFieldOptimized dropLocationField) {
        this.pickupCustomerBox = pickupCustomerBox;
        this.pickupLocationField = pickupLocationField;
        this.dropCustomerBox = dropCustomerBox;
        this.dropLocationField = dropLocationField;
        
        setupComponents();
    }
    
    private void setupComponents() {
        // Remove existing listeners to prevent duplicates
        detachComponents();
        
        // Setup customer autocomplete with on-demand search
        setupCustomerAutocomplete(pickupCustomerBox, true);
        setupCustomerAutocomplete(dropCustomerBox, false);
        
        // Setup bidirectional sync
        setupCustomerLocationSync(pickupCustomerBox, pickupLocationField, true);
        setupCustomerLocationSync(dropCustomerBox, dropLocationField, false);
    }
    
    /**
     * Setup autocomplete for customer combo boxes with on-demand search
     */
    private void setupCustomerAutocomplete(ComboBox<String> customerBox, boolean isPickup) {
        customerBox.setEditable(true);
        
        // Remove existing handler
        if (isPickup && pickupCustomerHandler != null) {
            pickupCustomerHandler.dispose();
        } else if (!isPickup && dropCustomerHandler != null) {
            dropCustomerHandler.dispose();
        }
        
        // Get the editor TextField
        TextField editor = customerBox.getEditor();
        
        // Create autocomplete handler
        SimpleAutocompleteHandler handler = new SimpleAutocompleteHandler(
            editor,
            searchText -> {
                // Don't search if text is too short
                if (searchText.length() < MIN_SEARCH_LENGTH) {
                    return Collections.emptyList();
                }
                
                // Search customers in database
                return loadDAO.searchCustomersByName(searchText, MAX_RESULTS);
            },
            selectedCustomer -> {
                if (selectedCustomer != null && !suppressEvents) {
                    suppressEvents = true;
                    Platform.runLater(() -> {
                        customerBox.setValue(selectedCustomer);
                        suppressEvents = false;
                    });
                }
            }
        );
        
        // Store handler reference
        if (isPickup) {
            pickupCustomerHandler = handler;
        } else {
            dropCustomerHandler = handler;
        }
    }
    
    /**
     * Setup bidirectional sync between customer and location fields
     */
    private void setupCustomerLocationSync(ComboBox<String> customerBox, 
                                         EnhancedLocationFieldOptimized locationField, 
                                         boolean isPickup) {
        
        // Customer selection changes location
        ChangeListener<String> customerListener = (obs, oldVal, newVal) -> {
            if (suppressEvents || newVal == null || newVal.isEmpty()) return;
            
            suppressEvents = true;
            try {
                // Update location field with customer filter
                locationField.setCustomer(newVal);
                
                // Update property
                if (isPickup) {
                    pickupCustomer.set(newVal);
                } else {
                    dropCustomer.set(newVal);
                }
                
            } finally {
                suppressEvents = false;
            }
        };
        
        customerBox.valueProperty().addListener(customerListener);
        activeListeners.add(customerListener);
        
        // Location selection updates customer if found
        ChangeListener<String> locationListener = (obs, oldVal, newVal) -> {
            if (suppressEvents || newVal == null || newVal.isEmpty()) return;
            
            // Search for customer associated with this address
            CompletableFuture.runAsync(() -> {
                List<CustomerAddress> matches = loadDAO.searchAddresses(newVal, null, 1);
                if (!matches.isEmpty() && matches.get(0).getCustomerName() != null) {
                    String customerName = matches.get(0).getCustomerName();
                    
                    Platform.runLater(() -> {
                        suppressEvents = true;
                        try {
                            if (!customerName.equals(customerBox.getValue())) {
                                customerBox.setValue(customerName);
                                logger.debug("Auto-populated customer '{}' from {} location", 
                                    customerName, isPickup ? "pickup" : "drop");
                            }
                        } finally {
                            suppressEvents = false;
                        }
                    });
                }
            }, executorService);
            
            // Update property
            if (isPickup) {
                pickupLocation.set(newVal);
            } else {
                dropLocation.set(newVal);
            }
        };
        
        // Monitor location field changes
        locationField.locationStringProperty().addListener(locationListener);
        activeListeners.add(locationListener);
    }
    
    /**
     * Remove all listeners
     */
    public void detachComponents() {
        // Remove all active listeners
        for (ChangeListener<?> listener : activeListeners) {
            if (pickupCustomerBox != null) {
                pickupCustomerBox.valueProperty().removeListener((ChangeListener<String>) listener);
            }
            if (dropCustomerBox != null) {
                dropCustomerBox.valueProperty().removeListener((ChangeListener<String>) listener);
            }
            if (pickupLocationField != null) {
                pickupLocationField.locationStringProperty().removeListener((ChangeListener<String>) listener);
            }
            if (dropLocationField != null) {
                dropLocationField.locationStringProperty().removeListener((ChangeListener<String>) listener);
            }
        }
        activeListeners.clear();
        
        // Dispose autocomplete handlers
        if (pickupCustomerHandler != null) {
            pickupCustomerHandler.dispose();
            pickupCustomerHandler = null;
        }
        if (dropCustomerHandler != null) {
            dropCustomerHandler.dispose();
            dropCustomerHandler = null;
        }
    }
    
    // Getters for properties
    public StringProperty pickupCustomerProperty() { return pickupCustomer; }
    public StringProperty pickupLocationProperty() { return pickupLocation; }
    public StringProperty dropCustomerProperty() { return dropCustomer; }
    public StringProperty dropLocationProperty() { return dropLocation; }
    
    /**
     * Shutdown the executor service when done
     */
    public void shutdown() {
        executorService.shutdown();
        detachComponents();
        
        // Shutdown location fields
        if (pickupLocationField != null) {
            pickupLocationField.shutdown();
        }
        if (dropLocationField != null) {
            dropLocationField.shutdown();
        }
    }
}