package com.company.payroll.loads;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Enhanced location field that combines ComboBox functionality with manual text entry
 * and automatic saving to customer locations.
 */
public class EnhancedLocationField extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedLocationField.class);
    
    private final ComboBox<CustomerLocation> locationComboBox;
    private final Button manualEntryButton;
    private final String locationType; // "PICKUP" or "DROP"
    private final LoadDAO loadDAO;
    private String currentCustomer;
    private CustomerLocation customLocation; // For manually entered addresses
    
    public EnhancedLocationField(String locationType, LoadDAO loadDAO) {
        this.locationType = locationType;
        this.loadDAO = loadDAO;
        
        // Setup ComboBox
        locationComboBox = new ComboBox<>();
        locationComboBox.setEditable(false);
        locationComboBox.setPrefWidth(350);
        locationComboBox.setPromptText("Select " + (locationType.equals("PICKUP") ? "pickup" : "drop") + " location");
        
        // Custom cell factory for display
        locationComboBox.setCellFactory(cb -> new ListCell<CustomerLocation>() {
            @Override
            protected void updateItem(CustomerLocation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayText());
                    if (item.isDefault()) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        locationComboBox.setButtonCell(new ListCell<CustomerLocation>() {
            @Override
            protected void updateItem(CustomerLocation item, boolean empty) {
                super.updateItem(item, empty);
                setText((item == null || empty) ? "" : item.getFullAddress());
            }
        });
        
        // Manual entry button
        manualEntryButton = new Button("✏");
        manualEntryButton.setTooltip(new Tooltip("Enter address manually"));
        manualEntryButton.setOnAction(e -> showManualEntryDialog());
        
        // Layout
        this.setSpacing(5);
        this.getChildren().addAll(locationComboBox, manualEntryButton);
        HBox.setHgrow(locationComboBox, Priority.ALWAYS);
    }
    
    private void showManualEntryDialog() {
        Dialog<CustomerLocation> dialog = new Dialog<>();
        dialog.setTitle("Enter " + (locationType.equals("PICKUP") ? "Pickup" : "Drop") + " Location");
        dialog.setHeaderText("Enter address details manually");
        dialog.initModality(Modality.APPLICATION_MODAL);
        
        // Form fields
        TextField addressField = new TextField();
        addressField.setPromptText("Street Address");
        addressField.setPrefWidth(300);
        
        TextField cityField = new TextField();
        cityField.setPromptText("City");
        cityField.setPrefWidth(200);
        
        TextField stateField = new TextField();
        stateField.setPromptText("State (e.g., CA)");
        stateField.setPrefWidth(80);
        
        
        TextField nameField = new TextField();
        nameField.setPromptText("Location Name (Optional)");
        nameField.setPrefWidth(300);
        
        CheckBox saveToCustomerCheck = new CheckBox("Save to Customer Locations");
        saveToCustomerCheck.setSelected(true);
        saveToCustomerCheck.setDisable(currentCustomer == null || currentCustomer.isEmpty());
        
        CheckBox setAsDefaultCheck = new CheckBox("Set as default " + locationType.toLowerCase() + " location");
        setAsDefaultCheck.setDisable(!saveToCustomerCheck.isSelected());
        
        saveToCustomerCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            setAsDefaultCheck.setDisable(!newVal);
            if (!newVal) setAsDefaultCheck.setSelected(false);
        });
        
        // Pre-fill if editing existing custom location
        if (customLocation != null) {
            addressField.setText(customLocation.getAddress());
            cityField.setText(customLocation.getCity());
            stateField.setText(customLocation.getState());
            nameField.setText(customLocation.getLocationName());
        }
        
        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        grid.add(new Label("Address:"), 0, 0);
        grid.add(addressField, 1, 0, 3, 1);
        
        grid.add(new Label("City:"), 0, 1);
        grid.add(cityField, 1, 1);
        grid.add(new Label("State:"), 2, 1);
        grid.add(stateField, 3, 1);
        
        
        grid.add(new Label("Name:"), 0, 3);
        grid.add(nameField, 1, 3, 3, 1);
        
        grid.add(saveToCustomerCheck, 0, 4, 4, 1);
        grid.add(setAsDefaultCheck, 0, 5, 4, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Enable OK button only when required fields are filled
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        
        // Validation
        Runnable validation = () -> {
            boolean valid = !addressField.getText().trim().isEmpty() &&
                           !cityField.getText().trim().isEmpty() &&
                           !stateField.getText().trim().isEmpty();
            okButton.setDisable(!valid);
        };
        
        addressField.textProperty().addListener((obs, oldVal, newVal) -> validation.run());
        cityField.textProperty().addListener((obs, oldVal, newVal) -> validation.run());
        stateField.textProperty().addListener((obs, oldVal, newVal) -> validation.run());
        
        // Result converter
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                CustomerLocation location = new CustomerLocation();
                location.setAddress(addressField.getText().trim());
                location.setCity(cityField.getText().trim());
                location.setState(stateField.getText().trim().toUpperCase());
                location.setLocationName(nameField.getText().trim());
                location.setLocationType(locationType);
                location.setDefault(setAsDefaultCheck.isSelected());
                
                // Save to database if requested
                if (saveToCustomerCheck.isSelected() && currentCustomer != null && !currentCustomer.isEmpty()) {
                    // First ensure the customer exists
                    loadDAO.addCustomerIfNotExists(currentCustomer);
                    
                    int locationId = loadDAO.addCustomerLocation(location, currentCustomer);
                    if (locationId > 0) {
                        location.setId(locationId);
                        logger.info("Saved new customer location: {}", location.getFullAddress());
                        
                        // Refresh the combo box
                        refreshLocations();
                        
                        // Select the newly created location
                        locationComboBox.setValue(location);
                    } else {
                        // If save failed, use as temporary
                        customLocation = location;
                        location.setId(-1);
                    }
                } else {
                    // Use as temporary custom location
                    customLocation = location;
                    location.setId(-1); // Temporary ID
                }
                
                return location;
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(location -> {
            if (location.getId() == -1) {
                // Temporary location, update display
                locationComboBox.setPromptText(location.getFullAddress());
                locationComboBox.setValue(null);
                customLocation = location;
            }
        });
    }
    
    public void setCustomer(String customer) {
        this.currentCustomer = customer;
        refreshLocations();
    }

    /**
     * Return the customer currently associated with this field.
     */
    public String getCurrentCustomer() {
        return currentCustomer;
    }
    
    private void refreshLocations() {
        if (currentCustomer != null && !currentCustomer.isEmpty()) {
            ObservableList<CustomerLocation> locations = FXCollections.observableArrayList(
                loadDAO.getCustomerLocationsFull(currentCustomer, locationType)
            );
            locationComboBox.setItems(locations);
            
            // Auto-select default if available
            locations.stream()
                .filter(CustomerLocation::isDefault)
                .findFirst()
                .ifPresent(locationComboBox::setValue);
        } else {
            locationComboBox.setItems(FXCollections.emptyObservableList());
            locationComboBox.setValue(null);
        }
        customLocation = null;
    }
    
    public CustomerLocation getValue() {
        CustomerLocation selected = locationComboBox.getValue();
        if (selected != null) {
            return selected;
        }
        return customLocation;
    }
    
    public void setValue(CustomerLocation location) {
        if (location != null && location.getId() > 0) {
            locationComboBox.setValue(location);
            customLocation = null;
        } else {
            customLocation = location;
            locationComboBox.setValue(null);
            if (location != null) {
                locationComboBox.setPromptText(location.getFullAddress());
            }
        }
    }
    
    public String getLocationString() {
        CustomerLocation location = getValue();
        return location != null ? location.getFullAddress() : "";
    }
    
    public void setLocationString(String locationString) {
        if (locationString == null || locationString.isEmpty()) {
            setValue(null);
            return;
        }
        
        // Try to match with existing locations
        CustomerLocation match = locationComboBox.getItems().stream()
            .filter(loc -> loc.getFullAddress().equals(locationString))
            .findFirst()
            .orElse(null);
        
        if (match != null) {
            setValue(match);
        } else {
            // Create temporary location from string
            CustomerLocation temp = new CustomerLocation();
            temp.setId(-1);
            temp.setLocationType(locationType);
            
            // Simple parsing - expect "City, State" format
            String[] parts = locationString.split(",");
            if (parts.length >= 2) {
                temp.setCity(parts[0].trim());
                temp.setState(parts[1].trim());
            } else {
                temp.setAddress(locationString);
            }
            
            customLocation = temp;
            locationComboBox.setValue(null);
            locationComboBox.setPromptText(locationString);
        }
    }
}