package com.company.payroll.config;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

public class DOTComplianceConfigDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(DOTComplianceConfigDialog.class);
    
    private final DOTComplianceConfig config;
    
    // Truck requirements controls
    private VBox truckRequirementsBox;
    private TextField truckPathField;
    
    // Trailer requirements controls
    private VBox trailerRequirementsBox;
    private TextField trailerPathField;
    
    // Employee requirements controls
    private VBox employeeRequirementsBox;
    private TextField employeePathField;
    
    // Container references for dynamic management
    private VBox truckCheckBoxContainer;
    private VBox trailerCheckBoxContainer;
    private VBox employeeCheckBoxContainer;
    
    public DOTComplianceConfigDialog() {
        this.config = DOTComplianceConfig.getInstance();
        
        setTitle("DOT Compliance Configuration");
        setHeaderText("Configure Required Documents for DOT Compliance");
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        getDialogPane().setContent(createContent());
        getDialogPane().setPrefWidth(800);
        getDialogPane().setPrefHeight(700);
        
        // Load current values
        loadCurrentValues();
        
        // Handle save
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                saveConfiguration();
            }
            return null;
        });
    }
    
    private VBox createContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Title
        Label titleLabel = new Label("DOT Compliance Requirements");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Create tabbed interface
        TabPane tabPane = new TabPane();
        
        // Truck requirements tab
        Tab truckTab = new Tab("Trucks", createTruckSection());
        truckTab.setClosable(false);
        
        // Trailer requirements tab
        Tab trailerTab = new Tab("Trailers", createTrailerSection());
        trailerTab.setClosable(false);
        
        // Employee requirements tab
        Tab employeeTab = new Tab("Employees", createEmployeeSection());
        employeeTab.setClosable(false);
        
        tabPane.getTabs().addAll(truckTab, trailerTab, employeeTab);
        
        content.getChildren().addAll(titleLabel, tabPane);
        
        return content;
    }
    
    private VBox createTruckSection() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(15));
        
        // Header
        Label headerLabel = new Label("Truck Document Requirements");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Document storage path
        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        
        Label pathLabel = new Label("Document Storage Path:");
        truckPathField = new TextField();
        truckPathField.setPromptText("Select folder for truck documents");
        truckPathField.setPrefWidth(300);
        
        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> browseForFolder(truckPathField, "Select Truck Document Folder"));
        
        pathBox.getChildren().addAll(pathLabel, truckPathField, browseBtn);
        
        // Requirements checkboxes
        truckRequirementsBox = createRequirementsBox("Truck", config.getTruckRequirements());
        
        section.getChildren().addAll(headerLabel, pathBox, truckRequirementsBox);
        
        return section;
    }
    
    private VBox createTrailerSection() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(15));
        
        // Header
        Label headerLabel = new Label("Trailer Document Requirements");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Document storage path
        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        
        Label pathLabel = new Label("Document Storage Path:");
        trailerPathField = new TextField();
        trailerPathField.setPromptText("Select folder for trailer documents");
        trailerPathField.setPrefWidth(300);
        
        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> browseForFolder(trailerPathField, "Select Trailer Document Folder"));
        
        pathBox.getChildren().addAll(pathLabel, trailerPathField, browseBtn);
        
        // Requirements checkboxes
        trailerRequirementsBox = createRequirementsBox("Trailer", config.getTrailerRequirements());
        
        section.getChildren().addAll(headerLabel, pathBox, trailerRequirementsBox);
        
        return section;
    }
    
    private VBox createEmployeeSection() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(15));
        
        // Header
        Label headerLabel = new Label("Employee Document Requirements");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Document storage path
        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        
        Label pathLabel = new Label("Document Storage Path:");
        employeePathField = new TextField();
        employeePathField.setPromptText("Select folder for employee documents");
        employeePathField.setPrefWidth(300);
        
        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> browseForFolder(employeePathField, "Select Employee Document Folder"));
        
        pathBox.getChildren().addAll(pathLabel, employeePathField, browseBtn);
        
        // Requirements checkboxes
        employeeRequirementsBox = createRequirementsBox("Employee", config.getEmployeeRequirements());
        
        section.getChildren().addAll(headerLabel, pathBox, employeeRequirementsBox);
        
        return section;
    }
    
    private VBox createRequirementsBox(String vehicleType, Map<String, Boolean> requirements) {
        VBox box = new VBox(8);
        
        Label instructionsLabel = new Label("Select documents required for DOT compliance:");
        instructionsLabel.setStyle("-fx-font-weight: bold;");
        
        // Add management buttons
        HBox managementButtons = new HBox(10);
        managementButtons.setAlignment(Pos.CENTER_LEFT);
        
        Button addBtn = new Button("➕ Add Document");
        addBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setOnAction(e -> addDocumentRequirement(vehicleType, getContainerForType(vehicleType), requirements));
        
        Button editBtn = new Button("✏️ Edit Selected");
        editBtn.setStyle("-fx-background-color: #ffc107; -fx-text-fill: white; -fx-font-weight: bold;");
        editBtn.setOnAction(e -> editSelectedDocument(vehicleType, getContainerForType(vehicleType), requirements));
        
        Button deleteBtn = new Button("🗑️ Delete Selected");
        deleteBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteBtn.setOnAction(e -> deleteSelectedDocument(vehicleType, getContainerForType(vehicleType), requirements));
        
        managementButtons.getChildren().addAll(addBtn, editBtn, deleteBtn);
        
        ScrollPane scrollPane = new ScrollPane();
        VBox checkBoxContainer = new VBox(5);
        
        // Store container reference based on vehicle type
        switch (vehicleType.toLowerCase()) {
            case "truck":
                truckCheckBoxContainer = checkBoxContainer;
                break;
            case "trailer":
                trailerCheckBoxContainer = checkBoxContainer;
                break;
            case "employee":
                employeeCheckBoxContainer = checkBoxContainer;
                break;
        }
        
        for (Map.Entry<String, Boolean> entry : requirements.entrySet()) {
            String docType = entry.getKey();
            
            // Special handling for "Other Document"
            if ("Other Document".equals(docType)) {
                HBox otherDocBox = new HBox(10);
                otherDocBox.setAlignment(Pos.CENTER_LEFT);
                
                CheckBox otherCheckBox = new CheckBox("Other Document:");
                otherCheckBox.setSelected(entry.getValue());
                otherCheckBox.setUserData("Other Document");
                
                TextField customDocField = new TextField();
                customDocField.setPromptText("Enter custom document type");
                customDocField.setPrefWidth(200);
                customDocField.setDisable(!entry.getValue());
                
                // Enable/disable text field based on checkbox
                otherCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    customDocField.setDisable(!newVal);
                });
                
                otherDocBox.getChildren().addAll(otherCheckBox, customDocField);
                checkBoxContainer.getChildren().add(otherDocBox);
            } else {
                CheckBox checkBox = new CheckBox(docType);
                checkBox.setSelected(entry.getValue());
                checkBox.setUserData(docType);
                
                // Color code critical documents
                if (isCriticalDocument(docType)) {
                    checkBox.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
                }
                
                checkBoxContainer.getChildren().add(checkBox);
            }
        }
        
        scrollPane.setContent(checkBoxContainer);
        scrollPane.setPrefHeight(300);
        scrollPane.setFitToWidth(true);
        
        box.getChildren().addAll(instructionsLabel, managementButtons, scrollPane);
        
        return box;
    }
    
    private boolean isCriticalDocument(String documentType) {
        // Define critical DOT documents
        String[] criticalDocs = {
            "Annual DOT Inspection", "Truck Registration", "IRP Registration", 
            "IFTA Documentation", "DOT Cab Card", "CDL License", "Medical Certificate"
        };
        
        for (String critical : criticalDocs) {
            if (documentType.contains(critical)) {
                return true;
            }
        }
        return false;
    }
    
    private void browseForFolder(TextField pathField, String title) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(title);
        
        // Set initial directory if current path exists
        String currentPath = pathField.getText();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                dirChooser.setInitialDirectory(currentDir);
            }
        }
        
        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        File selectedDir = dirChooser.showDialog(stage);
        
        if (selectedDir != null) {
            pathField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    private void loadCurrentValues() {
        // Load document paths
        truckPathField.setText(config.getTruckDocumentPath());
        trailerPathField.setText(config.getTrailerDocumentPath());
        employeePathField.setText(config.getEmployeeDocumentPath());
        
        // Load requirements (checkboxes are already set up in createRequirementsBox)
        updateCheckBoxes(truckRequirementsBox, config.getTruckRequirements());
        updateCheckBoxes(trailerRequirementsBox, config.getTrailerRequirements());
        updateCheckBoxes(employeeRequirementsBox, config.getEmployeeRequirements());
    }
    
    private void updateCheckBoxes(VBox container, Map<String, Boolean> requirements) {
        for (javafx.scene.Node node : container.getChildren()) {
            if (node instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) node;
                VBox content = (VBox) scrollPane.getContent();
                
                for (javafx.scene.Node child : content.getChildren()) {
                    if (child instanceof CheckBox) {
                        CheckBox checkBox = (CheckBox) child;
                        String documentType = (String) checkBox.getUserData();
                        checkBox.setSelected(requirements.getOrDefault(documentType, false));
                    }
                }
            }
        }
    }
    
    private void saveConfiguration() {
        try {
            // Save document paths
            config.setTruckDocumentPath(truckPathField.getText().trim());
            config.setTrailerDocumentPath(trailerPathField.getText().trim());
            config.setEmployeeDocumentPath(employeePathField.getText().trim());
            
            // Save truck requirements
            Map<String, Boolean> truckReqs = getCheckBoxValues(truckRequirementsBox);
            config.setTruckRequirements(truckReqs);
            
            // Save trailer requirements
            Map<String, Boolean> trailerReqs = getCheckBoxValues(trailerRequirementsBox);
            config.setTrailerRequirements(trailerReqs);
            
            // Save employee requirements
            Map<String, Boolean> employeeReqs = getCheckBoxValues(employeeRequirementsBox);
            config.setEmployeeRequirements(employeeReqs);
            
            // Save to file
            config.save();
            
            // Notify about configuration changes
            config.notifyConfigurationChanged();
            
            logger.info("DOT compliance configuration saved successfully");
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Configuration Saved");
            alert.setHeaderText(null);
            alert.setContentText("DOT compliance settings have been saved successfully.\n\nChanges will be reflected in all tabs immediately.");
            alert.showAndWait();
            
        } catch (Exception e) {
            logger.error("Error saving DOT compliance configuration", e);
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to save configuration: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    private Map<String, Boolean> getCheckBoxValues(VBox container) {
        Map<String, Boolean> values = new java.util.LinkedHashMap<>();
        
        for (javafx.scene.Node node : container.getChildren()) {
            if (node instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) node;
                VBox content = (VBox) scrollPane.getContent();
                
                for (javafx.scene.Node child : content.getChildren()) {
                    if (child instanceof CheckBox) {
                        CheckBox checkBox = (CheckBox) child;
                        String documentType = (String) checkBox.getUserData();
                        if (documentType != null) {
                            values.put(documentType, checkBox.isSelected());
                        }
                    } else if (child instanceof HBox) {
                        // Handle "Other Document" case with text field
                        HBox hbox = (HBox) child;
                        for (javafx.scene.Node hboxChild : hbox.getChildren()) {
                            if (hboxChild instanceof CheckBox) {
                                CheckBox checkBox = (CheckBox) hboxChild;
                                String documentType = (String) checkBox.getUserData();
                                if (documentType != null) {
                                    values.put(documentType, checkBox.isSelected());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return values;
    }
    
    /**
     * Add a new document requirement
     */
    private void addDocumentRequirement(String vehicleType, VBox container, Map<String, Boolean> requirements) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add Document Requirement");
        dialog.setHeaderText("Add new document requirement for " + vehicleType);
        
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        TextField documentField = new TextField();
        documentField.setPromptText("Enter document name (e.g., 'Safety Inspection Report')");
        documentField.setPrefWidth(300);
        
        CheckBox requiredCheckBox = new CheckBox("Required for compliance");
        requiredCheckBox.setSelected(true);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            new Label("Document Name:"),
            documentField,
            requiredCheckBox
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String documentName = documentField.getText().trim();
                if (!documentName.isEmpty()) {
                    return documentName;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(documentName -> {
            if (!requirements.containsKey(documentName)) {
                requirements.put(documentName, requiredCheckBox.isSelected());
                
                // Add new checkbox to container
                CheckBox newCheckBox = new CheckBox(documentName);
                newCheckBox.setSelected(requiredCheckBox.isSelected());
                newCheckBox.setUserData(documentName);
                
                // Color code if it's a critical document
                if (isCriticalDocument(documentName)) {
                    newCheckBox.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
                }
                
                container.getChildren().add(newCheckBox);
                
                logger.info("Added new document requirement: {} for {}", documentName, vehicleType);
            } else {
                showAlert(Alert.AlertType.WARNING, "Duplicate Document", 
                         "A document with this name already exists.");
            }
        });
    }
    
    /**
     * Edit selected document requirement
     */
    private void editSelectedDocument(String vehicleType, VBox container, Map<String, Boolean> requirements) {
        CheckBox selectedCheckBox = getSelectedCheckBox(container);
        if (selectedCheckBox == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a document to edit.");
            return;
        }
        
        String oldName = (String) selectedCheckBox.getUserData();
        
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Document Requirement");
        dialog.setHeaderText("Edit document requirement for " + vehicleType);
        
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        TextField documentField = new TextField(oldName);
        documentField.setPrefWidth(300);
        
        CheckBox requiredCheckBox = new CheckBox("Required for compliance");
        requiredCheckBox.setSelected(selectedCheckBox.isSelected());
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            new Label("Document Name:"),
            documentField,
            requiredCheckBox
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String newName = documentField.getText().trim();
                if (!newName.isEmpty()) {
                    return newName;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.equals(oldName) && requirements.containsKey(newName)) {
                showAlert(Alert.AlertType.WARNING, "Duplicate Document", 
                         "A document with this name already exists.");
                return;
            }
            
            // Update the checkbox
            selectedCheckBox.setText(newName);
            selectedCheckBox.setUserData(newName);
            selectedCheckBox.setSelected(requiredCheckBox.isSelected());
            
            // Update requirements map
            requirements.remove(oldName);
            requirements.put(newName, requiredCheckBox.isSelected());
            
            // Update styling
            if (isCriticalDocument(newName)) {
                selectedCheckBox.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            } else {
                selectedCheckBox.setStyle("");
            }
            
            logger.info("Updated document requirement: {} -> {} for {}", oldName, newName, vehicleType);
        });
    }
    
    /**
     * Delete selected document requirement
     */
    private void deleteSelectedDocument(String vehicleType, VBox container, Map<String, Boolean> requirements) {
        CheckBox selectedCheckBox = getSelectedCheckBox(container);
        if (selectedCheckBox == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a document to delete.");
            return;
        }
        
        String documentName = (String) selectedCheckBox.getUserData();
        
        // Prevent deletion of critical documents
        if (isCriticalDocument(documentName)) {
            showAlert(Alert.AlertType.WARNING, "Cannot Delete", 
                     "Critical documents cannot be deleted. You can only disable them.");
            return;
        }
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Deletion");
        confirmDialog.setHeaderText("Delete Document Requirement");
        confirmDialog.setContentText("Are you sure you want to delete '" + documentName + "'?");
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                container.getChildren().remove(selectedCheckBox);
                requirements.remove(documentName);
                logger.info("Deleted document requirement: {} for {}", documentName, vehicleType);
            }
        });
    }
    
    /**
     * Get the selected checkbox from the container
     */
    private CheckBox getSelectedCheckBox(VBox container) {
        for (javafx.scene.Node node : container.getChildren()) {
            if (node instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) node;
                if (checkBox.isFocused() || checkBox.isSelected()) {
                    return checkBox;
                }
            }
        }
        return null;
    }
    
    /**
     * Get the appropriate container for the vehicle type
     */
    private VBox getContainerForType(String vehicleType) {
        switch (vehicleType.toLowerCase()) {
            case "truck":
                return truckCheckBoxContainer;
            case "trailer":
                return trailerCheckBoxContainer;
            case "employee":
                return employeeCheckBoxContainer;
            default:
                return null;
        }
    }
    
    /**
     * Show alert dialog
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
} 