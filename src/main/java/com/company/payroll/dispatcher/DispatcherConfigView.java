package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Configuration view for dispatcher settings
 */
public class DispatcherConfigView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherConfigView.class);
    
    private final DispatcherController controller;
    
    // Form fields
    private TextField dispatcherNameField;
    private TextField dispatcherPhoneField;
    private TextField dispatcherEmailField;
    private TextField dispatcherFaxField;
    private TextField woNumberField;
    private TextField companyNameField;
    private TextField logoPathField;
    private TextArea policyTextArea;
    
    public DispatcherConfigView(DispatcherController controller) {
        this.controller = controller;
        initializeUI();
        loadSettings();
    }
    
    private void initializeUI() {
        // Header
        VBox header = createHeader();
        setTop(header);
        
        // Main content
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Dispatcher Information Section
        TitledPane dispatcherPane = createDispatcherSection();
        
        // Company Information Section
        TitledPane companyPane = createCompanySection();
        
        // Policy Section
        TitledPane policyPane = createPolicySection();
        
        // Notification Settings Section
        TitledPane notificationPane = createNotificationSection();
        
        // Advanced Settings Section
        TitledPane advancedPane = createAdvancedSection();
        
        content.getChildren().addAll(dispatcherPane, companyPane, policyPane, 
                                    notificationPane, advancedPane);
        
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        
        setCenter(scrollPane);
        
        // Bottom buttons
        HBox buttonBar = createButtonBar();
        setBottom(buttonBar);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        Label titleLabel = new Label("Dispatcher Configuration");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        
        Label descLabel = new Label("Configure dispatcher settings, company information, and policies");
        descLabel.setStyle("-fx-font-style: italic;");
        
        header.getChildren().addAll(titleLabel, descLabel);
        
        return header;
    }
    
    private TitledPane createDispatcherSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        // Dispatcher Name
        Label nameLabel = new Label("Dispatcher Name:");
        dispatcherNameField = new TextField();
        dispatcherNameField.setPrefWidth(300);
        
        // Phone
        Label phoneLabel = new Label("Phone:");
        dispatcherPhoneField = new TextField();
        dispatcherPhoneField.setPromptText("000-000-0000");
        
        // Email
        Label emailLabel = new Label("Email:");
        dispatcherEmailField = new TextField();
        dispatcherEmailField.setPromptText("dispatcher@company.com");
        
        // Fax
        Label faxLabel = new Label("Fax:");
        dispatcherFaxField = new TextField();
        dispatcherFaxField.setPromptText("000-000-0000");
        
        // WO Number
        Label woLabel = new Label("WO Number:");
        woNumberField = new TextField();
        woNumberField.setPromptText("A-000000");
        
        int row = 0;
        grid.add(nameLabel, 0, row);
        grid.add(dispatcherNameField, 1, row++);
        
        grid.add(phoneLabel, 0, row);
        grid.add(dispatcherPhoneField, 1, row++);
        
        grid.add(emailLabel, 0, row);
        grid.add(dispatcherEmailField, 1, row++);
        
        grid.add(faxLabel, 0, row);
        grid.add(dispatcherFaxField, 1, row++);
        
        grid.add(woLabel, 0, row);
        grid.add(woNumberField, 1, row);
        
        TitledPane pane = new TitledPane("Dispatcher Information", grid);
        pane.setExpanded(true);
        
        return pane;
    }
    
    private TitledPane createCompanySection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        // Company Name
        Label companyLabel = new Label("Company Name:");
        companyNameField = new TextField();
        companyNameField.setPrefWidth(300);
        
        // Logo
        Label logoLabel = new Label("Company Logo:");
        logoPathField = new TextField();
        logoPathField.setPrefWidth(250);
        logoPathField.setEditable(false);
        
        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Company Logo");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            
            File file = fileChooser.showOpenDialog(getScene().getWindow());
            if (file != null) {
                logoPathField.setText(file.getAbsolutePath());
            }
        });
        
        HBox logoBox = new HBox(10, logoPathField, browseBtn);
        
        int row = 0;
        grid.add(companyLabel, 0, row);
        grid.add(companyNameField, 1, row++);
        
        grid.add(logoLabel, 0, row);
        grid.add(logoBox, 1, row);
        
        TitledPane pane = new TitledPane("Company Information", grid);
        
        return pane;
    }
    
    private TitledPane createPolicySection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label instructionLabel = new Label("Enter the pickup and delivery policy text that will be included in dispatch sheets:");
        instructionLabel.setWrapText(true);
        
        policyTextArea = new TextArea();
        policyTextArea.setPrefRowCount(15);
        policyTextArea.setWrapText(true);
        policyTextArea.setPromptText("Enter pickup and delivery policy...");
        
        Button resetDefaultBtn = new Button("Reset to Default Policy");
        resetDefaultBtn.setOnAction(e -> {
            policyTextArea.setText(getDefaultPolicy());
        });
        
        content.getChildren().addAll(instructionLabel, policyTextArea, resetDefaultBtn);
        
        TitledPane pane = new TitledPane("Pickup & Delivery Policy", content);
        
        return pane;
    }
    
    private TitledPane createNotificationSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        CheckBox emailNotificationsCheck = new CheckBox("Enable email notifications");
        CheckBox smsNotificationsCheck = new CheckBox("Enable SMS notifications");
        
        Separator separator = new Separator();
        
        Label alertsLabel = new Label("Alert Settings:");
        alertsLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox latePickupAlert = new CheckBox("Alert on late pickup (30 min before)");
        CheckBox lateDeliveryAlert = new CheckBox("Alert on late delivery (1 hour before)");
        CheckBox driverHoursAlert = new CheckBox("Alert when driver approaches hour limits");
        CheckBox unassignedLoadAlert = new CheckBox("Alert for unassigned loads (24 hours before pickup)");
        
        content.getChildren().addAll(
            emailNotificationsCheck, smsNotificationsCheck,
            separator, alertsLabel,
            latePickupAlert, lateDeliveryAlert, 
            driverHoursAlert, unassignedLoadAlert
        );
        
        TitledPane pane = new TitledPane("Notification Settings", content);
        
        return pane;
    }
    
    private TitledPane createAdvancedSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        // Auto-refresh interval
        Label refreshLabel = new Label("Auto-refresh interval:");
        Spinner<Integer> refreshSpinner = new Spinner<>(10, 300, 30, 10);
        refreshSpinner.setPrefWidth(100);
        Label refreshUnit = new Label("seconds");
        
        HBox refreshBox = new HBox(10, refreshSpinner, refreshUnit);
        
        // Time zone
        Label timezoneLabel = new Label("Time Zone:");
        ComboBox<String> timezoneCombo = new ComboBox<>();
        timezoneCombo.getItems().addAll("Eastern", "Central", "Mountain", "Pacific");
        timezoneCombo.setValue("Eastern");
        
        // Default view
        Label defaultViewLabel = new Label("Default View:");
        ComboBox<String> defaultViewCombo = new ComboBox<>();
        defaultViewCombo.getItems().addAll("Daily View", "12 Hour Grid", "24 Hour Grid", "Weekly Grid", "Fleet Status");
        defaultViewCombo.setValue("Daily View");
        
        int row = 0;
        grid.add(refreshLabel, 0, row);
        grid.add(refreshBox, 1, row++);
        
        grid.add(timezoneLabel, 0, row);
        grid.add(timezoneCombo, 1, row++);
        
        grid.add(defaultViewLabel, 0, row);
        grid.add(defaultViewCombo, 1, row);
        
        TitledPane pane = new TitledPane("Advanced Settings", grid);
        
        return pane;
    }
    
    private HBox createButtonBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        Button saveBtn = new Button("Save Settings");
        saveBtn.setStyle("-fx-font-weight: bold;");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> saveSettings());
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> loadSettings());
        
        Button applyBtn = new Button("Apply");
        applyBtn.setOnAction(e -> {
            saveSettings();
            showSaveConfirmation();
        });
        
        buttonBar.getChildren().addAll(cancelBtn, applyBtn, saveBtn);
        
        return buttonBar;
    }
    
    private void loadSettings() {
        logger.info("Loading dispatcher settings");
        
        dispatcherNameField.setText(DispatcherSettings.getDispatcherName());
        dispatcherPhoneField.setText(DispatcherSettings.getDispatcherPhone());
        dispatcherEmailField.setText(DispatcherSettings.getDispatcherEmail());
        dispatcherFaxField.setText(DispatcherSettings.getDispatcherFax());
        woNumberField.setText(DispatcherSettings.getWONumber());
        companyNameField.setText(DispatcherSettings.getCompanyName());
        logoPathField.setText(DispatcherSettings.getCompanyLogoPath());
        policyTextArea.setText(DispatcherSettings.getPickupDeliveryPolicy());
    }
    
    private void saveSettings() {
        logger.info("Saving dispatcher settings");
        
        DispatcherSettings.setDispatcherName(dispatcherNameField.getText());
        DispatcherSettings.setDispatcherPhone(dispatcherPhoneField.getText());
        DispatcherSettings.setDispatcherEmail(dispatcherEmailField.getText());
        DispatcherSettings.setDispatcherFax(dispatcherFaxField.getText());
        DispatcherSettings.setWONumber(woNumberField.getText());
        DispatcherSettings.setCompanyName(companyNameField.getText());
        DispatcherSettings.setCompanyLogoPath(logoPathField.getText());
        DispatcherSettings.setPickupDeliveryPolicy(policyTextArea.getText());
        
        showSaveConfirmation();
    }
    
    private void showSaveConfirmation() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Settings Saved");
        alert.setHeaderText(null);
        alert.setContentText("Dispatcher settings have been saved successfully.");
        alert.showAndWait();
    }
    
    private String getDefaultPolicy() {
        return """
            Pickup & Delivery Policy
            
            1. Timeliness:
            Deliveries must be completed on time. A fee of $250 will be charged for late pickups or deliveries, in addition to any broker-imposed late fees.
            
            2. Pre-cooling:
            Ensure that the cargo is precooled to the requested temperature before arriving at the shipper.
            
            3. Temperature Control:
            Set the requested temperature on a continuous cycle; do not allow it to start and stop. If set to start and stop there will be consequences and will be held reliable for the whole load.
            
            4. Tracking:
            Use the app specified by the broker to track the load throughout the entire transport process.
            
            5. Documentation:
            Take and send pictures of the product upon loading, including the Bill of Lading (BOL), temperature readings, and seal, to your dispatch group before leaving the shipper.
            
            6. Final Checks:
            Before arriving at the receiver, send pictures of the product, temperature, and seal. Ensure all pictures, including BOLs, are sent to your dispatch group after delivery. Do not cut the seal until instructed to do so by the receiver.
            
            7. Rejection Policy:
            Do not leave the receiver's location if there are any rejections, shortages, or excess items upon delivery until instructed by your dispatcher""";
    }
}