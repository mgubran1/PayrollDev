package com.company.payroll.loads;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class LoadConfirmationConfigDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(LoadConfirmationConfigDialog.class);
    
    private final LoadConfirmationConfig config;
    private TextArea policyArea;
    private TextField dispatcherNameField;
    private TextField dispatcherPhoneField;
    private TextField dispatcherEmailField;
    private TextField dispatcherFaxField;
    private TextField logoPathField;
    
    public LoadConfirmationConfigDialog() {
        this.config = LoadConfirmationConfig.getInstance();
        
        setTitle("Load Confirmation Configuration");
        setHeaderText("Configure Load Confirmation Settings");
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        getDialogPane().setContent(createContent());
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(500);
        
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
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Dispatcher Information Section
        Label dispatcherLabel = new Label("Dispatcher Information");
        dispatcherLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        GridPane dispatcherGrid = new GridPane();
        dispatcherGrid.setHgap(10);
        dispatcherGrid.setVgap(10);
        dispatcherGrid.setPadding(new Insets(10, 0, 0, 20));
        
        dispatcherNameField = new TextField();
        dispatcherPhoneField = new TextField();
        dispatcherEmailField = new TextField();
        dispatcherFaxField = new TextField();
        
        int row = 0;
        dispatcherGrid.add(new Label("Name:"), 0, row);
        dispatcherGrid.add(dispatcherNameField, 1, row++);
        
        dispatcherGrid.add(new Label("Phone:"), 0, row);
        dispatcherGrid.add(dispatcherPhoneField, 1, row++);
        
        dispatcherGrid.add(new Label("Email:"), 0, row);
        dispatcherGrid.add(dispatcherEmailField, 1, row++);
        
        dispatcherGrid.add(new Label("Fax:"), 0, row);
        dispatcherGrid.add(dispatcherFaxField, 1, row++);
        
        // Set column constraints
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(80);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        dispatcherGrid.getColumnConstraints().addAll(col0, col1);
        
        // Company Logo Section
        Label logoLabel = new Label("Company Logo");
        logoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        HBox logoBox = new HBox(10);
        logoBox.setPadding(new Insets(0, 0, 0, 20));
        logoPathField = new TextField();
        logoPathField.setEditable(false);
        logoPathField.setPrefWidth(400);
        
        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> browseForLogo());
        
        Button clearLogoBtn = new Button("Clear");
        clearLogoBtn.setOnAction(e -> logoPathField.clear());
        
        logoBox.getChildren().addAll(logoPathField, browseBtn, clearLogoBtn);
        
        // Pickup and Delivery Policy Section
        Label policyLabel = new Label("Pickup and Delivery Policy");
        policyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        policyArea = new TextArea();
        policyArea.setPrefRowCount(10);
        policyArea.setWrapText(true);
        policyArea.setPromptText("Enter your pickup and delivery policy here...");
        
        VBox policyBox = new VBox(5);
        policyBox.setPadding(new Insets(0, 0, 0, 20));
        policyBox.getChildren().add(policyArea);
        VBox.setVgrow(policyArea, Priority.ALWAYS);
        
        // Add all sections to content
        content.getChildren().addAll(
            dispatcherLabel,
            dispatcherGrid,
            new Separator(),
            logoLabel,
            logoBox,
            new Separator(),
            policyLabel,
            policyBox
        );
        
        // Make the policy area grow
        VBox.setVgrow(policyBox, Priority.ALWAYS);
        
        return content;
    }
    
    private void browseForLogo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Company Logo");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        // Set initial directory if logo path exists
        String currentPath = logoPathField.getText();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists() && currentFile.getParentFile() != null) {
                fileChooser.setInitialDirectory(currentFile.getParentFile());
            }
        }
        
        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            logoPathField.setText(selectedFile.getAbsolutePath());
        }
    }
    
    private void loadCurrentValues() {
        dispatcherNameField.setText(config.getDispatcherName());
        dispatcherPhoneField.setText(config.getDispatcherPhone());
        dispatcherEmailField.setText(config.getDispatcherEmail());
        dispatcherFaxField.setText(config.getDispatcherFax());
        logoPathField.setText(config.getCompanyLogoPath());
        policyArea.setText(config.getPickupDeliveryPolicy());
    }
    
    private void saveConfiguration() {
        config.setDispatcherName(dispatcherNameField.getText().trim());
        config.setDispatcherPhone(dispatcherPhoneField.getText().trim());
        config.setDispatcherEmail(dispatcherEmailField.getText().trim());
        config.setDispatcherFax(dispatcherFaxField.getText().trim());
        config.setCompanyLogoPath(logoPathField.getText().trim());
        config.setPickupDeliveryPolicy(policyArea.getText());
        
        config.save();
        logger.info("Load confirmation configuration saved");
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Configuration Saved");
        alert.setHeaderText(null);
        alert.setContentText("Load confirmation settings have been saved successfully.");
        alert.showAndWait();
    }
}