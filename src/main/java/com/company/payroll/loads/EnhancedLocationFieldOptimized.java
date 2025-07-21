package com.company.payroll.loads;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Optimized location field that uses on-demand database queries instead of pre-loading all data.
 * This component provides fast autocomplete functionality with minimal memory usage.
 */
public class EnhancedLocationFieldOptimized extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedLocationFieldOptimized.class);
    
    // Core components
    private final TextField locationTextField;
    private final Button clearButton;
    private final LoadDAO loadDAO;
    
    // State management
    private final StringProperty customer = new SimpleStringProperty();
    private final StringProperty locationString = new SimpleStringProperty();
    private SimpleAutocompleteHandler autocompleteHandler;
    
    // Performance settings
    private static final int SEARCH_DELAY_MS = 300; // Delay before searching
    private static final int MAX_RESULTS = 50; // Maximum results to show
    private static final int MIN_SEARCH_LENGTH = 2; // Minimum characters to trigger search
    
    // Threading
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private CompletableFuture<Void> currentSearchTask;
    private long lastSearchTime = 0;
    
    public EnhancedLocationFieldOptimized(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
        
        // Create components
        locationTextField = new TextField();
        locationTextField.setPromptText("Type to search locations...");
        locationTextField.setPrefWidth(300);
        
        // Create clear button
        clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearLocation());
        clearButton.setStyle("-fx-font-size: 12px; -fx-padding: 5px 10px;");
        
        // Layout
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(locationTextField, Priority.ALWAYS);
        
        getChildren().addAll(locationTextField, clearButton);
        
        // Setup autocomplete with on-demand search
        setupOptimizedAutocomplete();
        
        // Bind properties
        locationTextField.textProperty().bindBidirectional(locationString);
    }
    
    private void setupOptimizedAutocomplete() {
        // Remove existing handler if any
        if (autocompleteHandler != null) {
            autocompleteHandler.dispose();
        }
        
        // Create autocomplete handler
        autocompleteHandler = new SimpleAutocompleteHandler(
            locationTextField,
            searchText -> {
                // Don't search if text is too short
                if (searchText.length() < MIN_SEARCH_LENGTH) {
                    return Collections.emptyList();
                }
                
                // Perform database search
                return searchLocations(searchText);
            },
            selectedAddress -> {
                if (selectedAddress != null) {
                    handleAddressSelection(selectedAddress);
                }
            }
        );
    }
    
    /**
     * Search locations in database based on search text
     */
    private List<String> searchLocations(String searchText) {
        try {
            // Cancel any pending search
            if (currentSearchTask != null && !currentSearchTask.isDone()) {
                currentSearchTask.cancel(true);
            }
            
            // Search with customer filter if available
            final String customerFilter = customer.get();
            List<CustomerAddress> addresses = loadDAO.searchAddresses(searchText, customerFilter, MAX_RESULTS);
            
            // Convert to display strings
            return addresses.stream()
                .map(addr -> {
                    StringBuilder display = new StringBuilder();
                    
                    // Add location name if available
                    if (addr.getLocationName() != null && !addr.getLocationName().isEmpty()) {
                        display.append(addr.getLocationName()).append(" - ");
                    }
                    
                    // Add address
                    display.append(addr.getFullAddress());
                    
                    // Add customer name if not filtering by customer
                    if (customerFilter == null || customerFilter.isEmpty()) {
                        if (addr.getCustomerName() != null) {
                            display.append(" (").append(addr.getCustomerName()).append(")");
                        }
                    }
                    
                    // Add default indicators
                    display.append(addr.getDefaultStatusText());
                    
                    return display.toString();
                })
                .distinct()
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Error searching locations", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Handle when an address is selected from autocomplete
     */
    private void handleAddressSelection(String selectedAddress) {
        // Extract the actual address part (remove customer name and status indicators)
        String cleanAddress = selectedAddress;
        
        // Remove customer name in parentheses
        int parenIndex = cleanAddress.lastIndexOf(" (");
        if (parenIndex > 0) {
            // Check if this is a customer name or status indicator
            String suffix = cleanAddress.substring(parenIndex);
            if (!suffix.contains("Default")) {
                cleanAddress = cleanAddress.substring(0, parenIndex);
            }
        }
        
        // Remove location name prefix if present
        int dashIndex = cleanAddress.indexOf(" - ");
        if (dashIndex > 0) {
            cleanAddress = cleanAddress.substring(dashIndex + 3);
        }
        
        // Remove default status indicators
        final String finalCleanAddress = cleanAddress.replace(" (Default for Pickup & Drop)", "")
                                 .replace(" (Default Pickup)", "")
                                 .replace(" (Default Drop)", "")
                                 .trim();
        
        // Update the text field with clean address
        Platform.runLater(() -> {
            locationTextField.setText(finalCleanAddress);
            locationString.set(finalCleanAddress);
        });
    }
    
    /**
     * Clear the location field
     */
    private void clearLocation() {
        locationTextField.clear();
        locationString.set("");
        customer.set("");
    }
    
    /**
     * Set customer to filter locations
     */
    public void setCustomer(String customerName) {
        customer.set(customerName);
        
        // If we have a customer, search for their default location
        if (customerName != null && !customerName.isEmpty() && 
            (locationTextField.getText() == null || locationTextField.getText().isEmpty())) {
            
            CompletableFuture.runAsync(() -> {
                List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(customerName);
                if (!addresses.isEmpty()) {
                    // Find default address
                    CustomerAddress defaultAddr = addresses.stream()
                        .filter(CustomerAddress::isDefaultPickup)
                        .findFirst()
                        .orElse(addresses.stream()
                            .filter(CustomerAddress::isDefaultDrop)
                            .findFirst()
                            .orElse(null));
                    
                    if (defaultAddr != null) {
                        Platform.runLater(() -> {
                            locationTextField.setText(defaultAddr.getFullAddress());
                        });
                    }
                }
            }, searchExecutor);
        }
    }
    
    /**
     * Get the current location string
     */
    public String getLocationString() {
        return locationString.get();
    }
    
    /**
     * Set the location string
     */
    public void setLocationString(String location) {
        locationString.set(location);
    }
    
    /**
     * Get the location property
     */
    public StringProperty locationStringProperty() {
        return locationString;
    }
    
    /**
     * Get the text field component
     */
    public TextField getLocationTextField() {
        return locationTextField;
    }
    
    /**
     * Get the customer property
     */
    public StringProperty customerProperty() {
        return customer;
    }
    
    /**
     * Enable or disable the field
     */
    public void setFieldDisabled(boolean disable) {
        locationTextField.setDisable(disable);
        clearButton.setDisable(disable);
    }
    
    /**
     * Request focus on the text field
     */
    public void requestFocus() {
        locationTextField.requestFocus();
    }
    
    /**
     * Shutdown the executor when done
     */
    public void shutdown() {
        searchExecutor.shutdown();
        if (autocompleteHandler != null) {
            autocompleteHandler.dispose();
        }
    }
}