package com.company.payroll.loads;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

public class CustomerLocationDialog extends Dialog<CustomerLocation> {
    private final TextField locationNameField;
    private final TextField addressField;
    private final TextField cityField;
    private final TextField stateField;
    private final CheckBox defaultCheckBox;
    private final String locationType;
    
    public CustomerLocationDialog(String locationType, CustomerLocation location) {
        this.locationType = locationType;
        
        setTitle(location == null ? "Add " + locationType + " Location" : "Edit " + locationType + " Location");
        setHeaderText("Enter location details");
        initModality(Modality.APPLICATION_MODAL);
        
        // Create form fields
        locationNameField = new TextField();
        locationNameField.setPromptText("Location name (optional)");
        
        addressField = new TextField();
        addressField.setPromptText("Street address");
        
        cityField = new TextField();
        cityField.setPromptText("City");
        
        stateField = new TextField();
        stateField.setPromptText("State");
        stateField.setPrefWidth(100);
        
        
        defaultCheckBox = new CheckBox("Set as default location");
        
        // Pre-fill if editing
        if (location != null) {
            locationNameField.setText(location.getLocationName());
            addressField.setText(location.getAddress());
            cityField.setText(location.getCity());
            stateField.setText(location.getState());
            defaultCheckBox.setSelected(location.isDefault());
        }
        
        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));
        
        // Row 0: Location Name
        grid.add(new Label("Location Name:"), 0, 0);
        grid.add(locationNameField, 1, 0, 3, 1);
        
        // Row 1: Address
        grid.add(new Label("Address:"), 0, 1);
        grid.add(addressField, 1, 1, 3, 1);
        
        // Row 2: City
        grid.add(new Label("City:"), 0, 2);
        grid.add(cityField, 1, 2, 3, 1);
        
        // Row 3: State
        grid.add(new Label("State:"), 0, 3);
        grid.add(stateField, 1, 3);
        
        // Row 4: Default checkbox
        grid.add(defaultCheckBox, 1, 4, 3, 1);
        
        // Set column constraints
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(80);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setMinWidth(40);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setMinWidth(120);
        
        grid.getColumnConstraints().addAll(col0, col1, col2, col3);
        
        // Button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Enable/Disable save button depending on whether address is entered
        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        
        // Validation
        addressField.textProperty().addListener((observable, oldValue, newValue) -> {
            saveButton.setDisable(newValue.trim().isEmpty());
        });
        
        getDialogPane().setContent(grid);
        
        // Focus on address field by default
        addressField.requestFocus();
        
        // Convert the result to CustomerLocation when save button is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                CustomerLocation result = location != null ? location : new CustomerLocation();
                result.setLocationType(this.locationType);
                result.setLocationName(locationNameField.getText().trim());
                result.setAddress(addressField.getText().trim());
                result.setCity(cityField.getText().trim());
                result.setState(stateField.getText().trim());
                result.setDefault(defaultCheckBox.isSelected());
                return result;
            }
            return null;
        });
    }
}