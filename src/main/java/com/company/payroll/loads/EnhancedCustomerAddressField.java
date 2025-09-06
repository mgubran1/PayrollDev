package com.company.payroll.loads;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * ENHANCED CUSTOMER-ADDRESS FIELD
 * 
 * Professional component that combines customer selection with address selection
 * in a single, cohesive interface. Replaces EnhancedCustomerFieldWithClear.java
 * 
 * Features:
 * - Unified customer and address selection
 * - Automatic address loading when customer changes
 * - Default address auto-selection
 * - Modern enterprise styling
 * - Proper resource management
 * - Thread-safe operations
 */
public class EnhancedCustomerAddressField extends VBox implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedCustomerAddressField.class);
    
    // UI Components
    private final Label titleLabel;
    private final UnifiedAutocompleteField<String> customerField;
    private final UnifiedAutocompleteField<CustomerAddress> addressField;
    private final Separator separator;
    
    // Data Properties
    private final StringProperty selectedCustomer = new SimpleStringProperty();
    private final ObjectProperty<CustomerAddress> selectedAddress = new SimpleObjectProperty<>();
    
    // Configuration
    private final boolean isPickupCustomer;
    private final AutocompleteFactory factory;
    
    // Event Handlers
    private Consumer<String> onCustomerChanged;
    private Consumer<CustomerAddress> onAddressChanged;
    
    // Professional styling
    private static final String TITLE_STYLE = """
        -fx-font-size: 13px;
        -fx-font-weight: bold;
        -fx-text-fill: #374151;
        -fx-padding: 0 0 8 0;
        """;
    
    private static final String CONTAINER_STYLE = """
        -fx-background-color: #f8fafc;
        -fx-border-color: #e5e7eb;
        -fx-border-width: 1px;
        -fx-border-radius: 8px;
        -fx-background-radius: 8px;
        -fx-padding: 12px;
        -fx-spacing: 10px;
        """;
    
    /**
     * Constructor for enhanced customer-address field
     * 
     * @param factory The autocomplete factory for creating fields
     * @param isPickupCustomer Whether this is for pickup (true) or drop (false)
     */
    public EnhancedCustomerAddressField(AutocompleteFactory factory, boolean isPickupCustomer) {
        this.factory = factory;
        this.isPickupCustomer = isPickupCustomer;
        
        // Create title
        String titleText = isPickupCustomer ? "🚛 Pickup Customer & Address" : "📍 Drop Customer & Address";
        titleLabel = new Label(titleText);
        titleLabel.setStyle(TITLE_STYLE);
        
        // Create customer field
        customerField = factory.createCustomerAutocomplete(isPickupCustomer, this::handleCustomerSelected);
        
        // Create address field (initially disabled until customer is selected)
        addressField = factory.createAddressAutocomplete(isPickupCustomer, 
            this::getCurrentCustomer, this::handleAddressSelected);
        addressField.getSearchField().setDisable(true);
        addressField.getSearchField().setPromptText("Select customer first...");
        
        // Create separator
        separator = new Separator();
        separator.setStyle("-fx-background-color: #e5e7eb;");
        
        // Setup layout
        setupLayout();
        
        // Bind properties
        bindProperties();
        
        logger.debug("Created EnhancedCustomerAddressField for {}", 
                    isPickupCustomer ? "pickup" : "drop");
    }
    
    private void setupLayout() {
        setStyle(CONTAINER_STYLE);
        setSpacing(10);
        setPadding(new Insets(0));
        
        // Customer section
        VBox customerSection = new VBox(5);
        Label customerLabel = new Label("Customer:");
        customerLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4b5563;");
        customerSection.getChildren().addAll(customerLabel, customerField);
        
        // Address section
        VBox addressSection = new VBox(5);
        Label addressLabel = new Label("Address:");
        addressLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4b5563;");
        addressSection.getChildren().addAll(addressLabel, addressField);
        
        getChildren().addAll(titleLabel, customerSection, separator, addressSection);
        
        // Set growth priorities
        VBox.setVgrow(customerSection, Priority.NEVER);
        VBox.setVgrow(addressSection, Priority.NEVER);
    }
    
    private void bindProperties() {
        // Bind selected customer property
        customerField.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer.set(newVal);
        });
        
        // Bind selected address property  
        addressField.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedAddress.set(newVal);
        });
        
        // Update field states based on selections
        selectedCustomer.addListener((obs, oldCustomer, newCustomer) -> {
            boolean hasCustomer = newCustomer != null && !newCustomer.trim().isEmpty();
            
            // Enable/disable address field
            addressField.getSearchField().setDisable(!hasCustomer);
            
            if (hasCustomer) {
                addressField.getSearchField().setPromptText(
                    isPickupCustomer ? "Enter pickup address..." : "Enter drop address...");
                
                // Load default address for customer
                loadDefaultAddress(newCustomer);
            } else {
                addressField.getSearchField().setPromptText("Select customer first...");
                addressField.setText("");
                selectedAddress.set(null);
            }
        });
    }
    
    private void handleCustomerSelected(String customer) {
        logger.debug("Customer selected: {}", customer);
        
        if (onCustomerChanged != null) {
            onCustomerChanged.accept(customer);
        }
    }
    
    private void handleAddressSelected(CustomerAddress address) {
        logger.debug("Address selected: {}", address != null ? address.getFullAddress() : "null");
        
        if (onAddressChanged != null) {
            onAddressChanged.accept(address);
        }
    }
    
    private String getCurrentCustomer() {
        return selectedCustomer.get();
    }
    
    private void loadDefaultAddress(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) return;
        
        // Load default address asynchronously
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                // Get customer addresses from cache/database
                java.util.List<CustomerAddress> addresses = 
                    EnterpriseDataCacheManager.getInstance().getCustomerAddressesAsync(customerName).get();
                
                // Find default address
                return addresses.stream()
                    .filter(addr -> isPickupCustomer ? addr.isDefaultPickup() : addr.isDefaultDrop())
                    .findFirst()
                    .orElse(null);
                
            } catch (Exception e) {
                logger.error("Error loading default address for customer: " + customerName, e);
                return null;
            }
        }).thenAccept(defaultAddress -> {
            if (defaultAddress != null) {
                javafx.application.Platform.runLater(() -> {
                    // Format address display manually
                    StringBuilder display = new StringBuilder();
                    if (defaultAddress.getLocationName() != null && !defaultAddress.getLocationName().isEmpty()) {
                        display.append(defaultAddress.getLocationName()).append(": ");
                    }
                    display.append(defaultAddress.getFullAddress());
                    if (isPickupCustomer && defaultAddress.isDefaultPickup()) {
                        display.append(" ⭐ Default Pickup");
                    } else if (!isPickupCustomer && defaultAddress.isDefaultDrop()) {
                        display.append(" ⭐ Default Drop");
                    }
                    
                    addressField.setText(display.toString());
                    selectedAddress.set(defaultAddress);
                    
                    logger.debug("Auto-selected default address: {}", defaultAddress.getFullAddress());
                });
            }
        });
    }
    
    // Public API Methods
    
    /**
     * Get the currently selected customer
     */
    public String getSelectedCustomer() {
        return selectedCustomer.get();
    }
    
    /**
     * Get the customer property for binding
     */
    public StringProperty selectedCustomerProperty() {
        return selectedCustomer;
    }
    
    /**
     * Get the currently selected address
     */
    public CustomerAddress getSelectedAddress() {
        return selectedAddress.get();
    }
    
    /**
     * Get the address property for binding
     */
    public ObjectProperty<CustomerAddress> selectedAddressProperty() {
        return selectedAddress;
    }
    
    /**
     * Set the customer (will trigger address loading)
     */
    public void setCustomer(String customer) {
        customerField.setText(customer);
    }
    
    /**
     * Set the address (customer must be set first)
     */
    public void setAddress(CustomerAddress address) {
        if (address != null) {
            // Format address display manually
            StringBuilder display = new StringBuilder();
            if (address.getLocationName() != null && !address.getLocationName().isEmpty()) {
                display.append(address.getLocationName()).append(": ");
            }
            display.append(address.getFullAddress());
            if (isPickupCustomer && address.isDefaultPickup()) {
                display.append(" ⭐ Default Pickup");
            } else if (!isPickupCustomer && address.isDefaultDrop()) {
                display.append(" ⭐ Default Drop");
            }
            
            addressField.setText(display.toString());
            selectedAddress.set(address);
        } else {
            addressField.setText("");
            selectedAddress.set(null);
        }
    }
    
    /**
     * Clear both customer and address
     */
    public void clearAll() {
        customerField.setText("");
        addressField.setText("");
        selectedCustomer.set(null);
        selectedAddress.set(null);
    }
    
    /**
     * Set handler for customer changes
     */
    public void setOnCustomerChanged(Consumer<String> handler) {
        this.onCustomerChanged = handler;
    }
    
    /**
     * Set handler for address changes
     */
    public void setOnAddressChanged(Consumer<CustomerAddress> handler) {
        this.onAddressChanged = handler;
    }
    
    /**
     * Get the customer autocomplete field for advanced configuration
     */
    public UnifiedAutocompleteField<String> getCustomerField() {
        return customerField;
    }
    
    /**
     * Get the address autocomplete field for advanced configuration
     */
    public UnifiedAutocompleteField<CustomerAddress> getAddressField() {
        return addressField;
    }
    
    /**
     * Check if this field is for pickup customer
     */
    public boolean isPickupCustomer() {
        return isPickupCustomer;
    }
    
    /**
     * Enable or disable the entire component
     */
    public void setFieldsDisabled(boolean disabled) {
        customerField.getSearchField().setDisable(disabled);
        if (!disabled && selectedCustomer.get() != null && !selectedCustomer.get().trim().isEmpty()) {
            addressField.getSearchField().setDisable(false);
        } else {
            addressField.getSearchField().setDisable(disabled || selectedCustomer.get() == null);
        }
    }
    
    /**
     * Request focus on the customer field
     */
    public void requestFocus() {
        customerField.getSearchField().requestFocus();
    }
    
    /**
     * Check if the component has valid selections
     */
    public boolean isValid() {
        return selectedCustomer.get() != null && !selectedCustomer.get().trim().isEmpty();
    }
    
    /**
     * Check if the component has both customer and address selected
     */
    public boolean isComplete() {
        return isValid() && selectedAddress.get() != null;
    }
    
    /**
     * Get a summary string of current selections
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (selectedCustomer.get() != null) {
            summary.append("Customer: ").append(selectedCustomer.get());
        }
        
        if (selectedAddress.get() != null) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("Address: ").append(selectedAddress.get().getFullAddress());
        }
        
        return summary.toString();
    }
    
    // Resource Cleanup
    @Override
    public void close() {
        logger.debug("Disposing EnhancedCustomerAddressField");
        
        try {
            if (customerField != null) {
                customerField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing customer field", e);
        }
        
        try {
            if (addressField != null) {
                addressField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing address field", e);
        }
        
        // Clear event handlers to prevent memory leaks
        onCustomerChanged = null;
        onAddressChanged = null;
        
        logger.debug("EnhancedCustomerAddressField disposed successfully");
    }
}
