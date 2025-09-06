package com.company.payroll.loads;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MIGRATION EXAMPLE: LoadsPanel Integration
 * 
 * This example shows how to replace all the duplicate autocomplete implementations
 * in LoadsPanel with the new unified autocomplete system.
 * 
 * BEFORE: 7 different autocomplete classes with critical bugs
 * AFTER:  Single unified system with proper resource management
 */
public class LoadsPanelMigrationExample extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(LoadsPanelMigrationExample.class);
    
    private final LoadDAO loadDAO;
    private final AutocompleteFactory autocompleteFactory;
    
    // NEW: Unified autocomplete components
    private EnhancedCustomerAddressField pickupCustomerField;
    private EnhancedCustomerAddressField dropCustomerField;
    private UnifiedAutocompleteField<String> truckField;
    private UnifiedAutocompleteField<String> trailerField;
    private UnifiedAutocompleteField<String> driverField;
    
    public LoadsPanelMigrationExample(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
        this.autocompleteFactory = new AutocompleteFactory(loadDAO);
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        logger.info("LoadsPanel migrated to unified autocomplete system");
    }
    
    private void initializeComponents() {
        // REPLACEMENT FOR: Multiple customer/address field implementations
        pickupCustomerField = autocompleteFactory.createEnhancedCustomerField(true);
        dropCustomerField = autocompleteFactory.createEnhancedCustomerField(false);
        
        // REPLACEMENT FOR: EnhancedAutocompleteField implementations
        truckField = createTruckAutocomplete();
        trailerField = createTrailerAutocomplete();
        driverField = createDriverAutocomplete();
    }
    
    // EXAMPLE: Creating custom autocomplete for trucks (simplified to avoid compilation errors)
    private UnifiedAutocompleteField<String> createTruckAutocomplete() {
        UnifiedAutocompleteField<String> field = new UnifiedAutocompleteField<>("Enter truck number...");
        
        // Configure for truck search (using loadDAO for demonstration)
        field.setSearchProvider(query -> 
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                // In real implementation, you would query trucks from database
                // For now, return empty list to avoid compilation errors
                return new java.util.ArrayList<String>();
            })
        );
        
        field.setOnSelectionHandler(truck -> {
            logger.debug("Truck selected: {}", truck);
            handleTruckSelection(truck);
        });
        
        // Configure action button for adding new trucks
        field.setActionButtonText("🚛");
        field.setActionButtonTooltip("Add new truck");
        field.setOnActionHandler(this::showAddTruckDialog);
        
        return field;
    }
    
    private UnifiedAutocompleteField<String> createTrailerAutocomplete() {
        UnifiedAutocompleteField<String> field = new UnifiedAutocompleteField<>("Enter trailer number...");
        
        field.setSearchProvider(query -> 
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                // In real implementation, you would query trailers from database
                return new java.util.ArrayList<String>();
            })
        );
        
        field.setOnSelectionHandler(trailer -> {
            logger.debug("Trailer selected: {}", trailer);
            handleTrailerSelection(trailer);
        });
        
        field.setActionButtonText("📦");
        field.setActionButtonTooltip("Add new trailer");
        
        return field;
    }
    
    private UnifiedAutocompleteField<String> createDriverAutocomplete() {
        UnifiedAutocompleteField<String> field = new UnifiedAutocompleteField<>("Enter driver name...");
        
        field.setSearchProvider(query -> 
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                // In real implementation, you would query drivers from database
                return new java.util.ArrayList<String>();
            })
        );
        
        field.setOnSelectionHandler(driver -> {
            logger.debug("Driver selected: {}", driver);
            handleDriverSelection(driver);
        });
        
        field.setActionButtonText("👨‍✈️");
        field.setActionButtonTooltip("Add new driver");
        
        return field;
    }
    
    private void setupLayout() {
        setSpacing(20);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #f8fafc;");
        
        // Create sections with professional styling
        VBox pickupSection = createSection("📤 PICKUP INFORMATION", pickupCustomerField);
        VBox dropSection = createSection("📥 DROP INFORMATION", dropCustomerField);
        VBox equipmentSection = createEquipmentSection();
        
        getChildren().addAll(pickupSection, dropSection, equipmentSection);
    }
    
    private VBox createSection(String title, EnhancedCustomerAddressField field) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #e5e7eb;
            -fx-border-width: 1px;
            -fx-border-radius: 8px;
            -fx-background-radius: 8px;
            """);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("""
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-text-fill: #1f2937;
            -fx-padding: 0 0 10 0;
            """);
        
        section.getChildren().addAll(titleLabel, field);
        return section;
    }
    
    private VBox createEquipmentSection() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(15));
        section.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #e5e7eb;
            -fx-border-width: 1px;
            -fx-border-radius: 8px;
            -fx-background-radius: 8px;
            """);
        
        Label titleLabel = new Label("🚛 EQUIPMENT ASSIGNMENT");
        titleLabel.setStyle("""
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-text-fill: #1f2937;
            -fx-padding: 0 0 10 0;
            """);
        
        VBox truckContainer = new VBox(5);
        truckContainer.getChildren().addAll(
            new Label("Truck:") {{ setStyle("-fx-font-weight: bold; -fx-text-fill: #4b5563;"); }},
            truckField
        );
        
        VBox trailerContainer = new VBox(5);
        trailerContainer.getChildren().addAll(
            new Label("Trailer:") {{ setStyle("-fx-font-weight: bold; -fx-text-fill: #4b5563;"); }},
            trailerField
        );
        
        VBox driverContainer = new VBox(5);
        driverContainer.getChildren().addAll(
            new Label("Driver:") {{ setStyle("-fx-font-weight: bold; -fx-text-fill: #4b5563;"); }},
            driverField
        );
        
        section.getChildren().addAll(titleLabel, truckContainer, trailerContainer, driverContainer);
        return section;
    }
    
    private void setupEventHandlers() {
        // PICKUP EVENTS
        pickupCustomerField.setOnCustomerChanged(customer -> {
            logger.debug("Pickup customer changed: {}", customer);
            // Auto-load customer defaults, update UI, etc.
            updatePickupInformation(customer);
        });
        
        pickupCustomerField.setOnAddressChanged(address -> {
            logger.debug("Pickup address changed: {}", address != null ? address.getFullAddress() : "null");
            updatePickupAddress(address);
        });
        
        // DROP EVENTS  
        dropCustomerField.setOnCustomerChanged(customer -> {
            logger.debug("Drop customer changed: {}", customer);
            updateDropInformation(customer);
        });
        
        dropCustomerField.setOnAddressChanged(address -> {
            logger.debug("Drop address changed: {}", address != null ? address.getFullAddress() : "null");
            updateDropAddress(address);
        });
        
        // EQUIPMENT EVENTS
        // Event handlers are already set in the create methods above
    }
    
    // Event handler implementations
    private void updatePickupInformation(String customer) {
        // Implementation for pickup customer changes
        // This would update related fields, load defaults, etc.
    }
    
    private void updatePickupAddress(CustomerAddress address) {
        // Implementation for pickup address changes
        // This would update distance calculations, routing, etc.
    }
    
    private void updateDropInformation(String customer) {
        // Implementation for drop customer changes
    }
    
    private void updateDropAddress(CustomerAddress address) {
        // Implementation for drop address changes
    }
    
    private void handleTruckSelection(String truck) {
        // Implementation for truck selection
        // Update load capacity, driver restrictions, etc.
    }
    
    private void handleTrailerSelection(String trailer) {
        // Implementation for trailer selection  
    }
    
    private void handleDriverSelection(String driver) {
        // Implementation for driver selection
        // Check driver availability, restrictions, etc.
    }
    
    private void showAddTruckDialog() {
        // Implementation for adding new trucks
        logger.info("Add truck dialog requested");
    }
    
    // PROPER RESOURCE CLEANUP - CRITICAL FOR PREVENTING MEMORY LEAKS
    public void dispose() {
        logger.info("Disposing LoadsPanel autocomplete components");
        
        try {
            if (pickupCustomerField != null) {
                pickupCustomerField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing pickup customer field", e);
        }
        
        try {
            if (dropCustomerField != null) {
                dropCustomerField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing drop customer field", e);
        }
        
        try {
            if (truckField != null) {
                truckField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing truck field", e);
        }
        
        try {
            if (trailerField != null) {
                trailerField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing trailer field", e);
        }
        
        try {
            if (driverField != null) {
                driverField.close();
            }
        } catch (Exception e) {
            logger.error("Error disposing driver field", e);
        }
        
        logger.info("LoadsPanel autocomplete disposal completed");
    }
    
    // PUBLIC API FOR LOAD CREATION/EDITING
    
    /**
     * Creates load data map from the current field selections
     * Returns a map instead of Load object to avoid compilation issues
     */
    public java.util.Map<String, Object> createLoadDataFromFields() {
        java.util.Map<String, Object> loadData = new java.util.HashMap<>();
        
        // Set pickup information
        if (pickupCustomerField.isComplete()) {
            loadData.put("pickupCustomer", pickupCustomerField.getSelectedCustomer());
            CustomerAddress pickupAddr = pickupCustomerField.getSelectedAddress();
            if (pickupAddr != null) {
                loadData.put("pickupAddress", pickupAddr.getFullAddress());
                loadData.put("pickupCity", pickupAddr.getCity());
                loadData.put("pickupState", pickupAddr.getState());
            }
        }
        
        // Set drop information
        if (dropCustomerField.isComplete()) {
            loadData.put("dropCustomer", dropCustomerField.getSelectedCustomer());
            CustomerAddress dropAddr = dropCustomerField.getSelectedAddress();
            if (dropAddr != null) {
                loadData.put("dropAddress", dropAddr.getFullAddress());
                loadData.put("dropCity", dropAddr.getCity());
                loadData.put("dropState", dropAddr.getState());
            }
        }
        
        // Set equipment
        if (truckField.getSelectedItem() != null) {
            loadData.put("truckNumber", truckField.getSelectedItem());
        }
        
        if (trailerField.getSelectedItem() != null) {
            loadData.put("trailerNumber", trailerField.getSelectedItem());
        }
        
        if (driverField.getSelectedItem() != null) {
            loadData.put("driverName", driverField.getSelectedItem());
        }
        
        return loadData;
    }
    
    /**
     * Loads field values from actual Load object using proper getter methods
     */
    public void loadFromLoad(Load load) {
        if (load == null) return;
        
        // Set pickup information using actual Load class fields
        pickupCustomerField.setCustomer(load.getCustomer());
        // Address will be auto-loaded when customer is set
        
        // Set drop information using actual Load class fields
        if (load.getCustomer2() != null) {
            dropCustomerField.setCustomer(load.getCustomer2());
        }
        
        // Set equipment - using actual Load class methods
        if (load.getDriver() != null && load.getDriver().getName() != null) {
            driverField.setText(load.getDriver().getName());
        }
        
        // Note: Load class doesn't have direct truck/trailer getters,
        // so you would need to implement these based on your actual Load class API
    }
    
    public boolean isValidLoad() {
        return pickupCustomerField.isValid() && 
               dropCustomerField.isValid() &&
               truckField.getSelectedItem() != null &&
               driverField.getSelectedItem() != null;
    }
    
    public String getValidationSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (!pickupCustomerField.isValid()) {
            summary.append("• Pickup customer is required\n");
        }
        
        if (!dropCustomerField.isValid()) {
            summary.append("• Drop customer is required\n");
        }
        
        if (truckField.getSelectedItem() == null) {
            summary.append("• Truck assignment is required\n");
        }
        
        if (driverField.getSelectedItem() == null) {
            summary.append("• Driver assignment is required\n");
        }
        
        return summary.toString();
    }
}
