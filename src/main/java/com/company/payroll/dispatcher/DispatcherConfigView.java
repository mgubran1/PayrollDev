package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.effect.DropShadow;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.dispatcher.DispatcherSettings;

import java.io.File;

/**
 * Modern configuration view for dispatcher settings
 */
public class DispatcherConfigView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherConfigView.class);
    
    private final DispatcherController controller;
    
    // Form fields
    private TextField dispatcherNameField;
    private TextField dispatcherPhoneField;
    private TextField dispatcherEmailField;
    private TextField dispatcherFaxField;
    private TextField companyNameField;
    private TextField logoPathField;
    private TextArea policyTextArea;
    
    // Notification checkboxes
    private CheckBox emailNotificationsCheck;
    private CheckBox smsNotificationsCheck;
    private CheckBox latePickupAlert;
    private CheckBox lateDeliveryAlert;
    private CheckBox driverHoursAlert;
    private CheckBox unassignedLoadAlert;
    
    // Advanced settings
    private Spinner<Integer> refreshSpinner;
    private ComboBox<String> timezoneCombo;
    private ComboBox<String> defaultViewCombo;
    
    public DispatcherConfigView(DispatcherController controller) {
        this.controller = controller;
        initializeModernUI();
        loadSettings();
    }
    
    private void initializeModernUI() {
        getStyleClass().add("dispatcher-container");
        setStyle("-fx-background-color: #FAFAFA;");
        
        // Modern header
        VBox header = createModernHeader();
        setTop(header);
        
        // Main content with modern cards
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #FAFAFA;");
        
        // Create modern sections
        VBox dispatcherCard = createModernCard("👤 Dispatcher Information", 
            "Configure dispatcher contact details", createDispatcherSection());
        
        VBox companyCard = createModernCard("🏢 Company Information", 
            "Set company name and branding", createCompanySection());
        
        VBox policyCard = createModernCard("📋 Pickup & Delivery Policy", 
            "Define operational policies", createPolicySection());
        
        VBox notificationCard = createModernCard("🔔 Notification Settings", 
            "Configure alerts and notifications", createNotificationSection());
        
        VBox advancedCard = createModernCard("⚙️ Advanced Settings", 
            "System preferences and defaults", createAdvancedSection());
        
        content.getChildren().addAll(dispatcherCard, companyCard, policyCard, 
                                    notificationCard, advancedCard);
        
        scrollPane.setContent(content);
        setCenter(scrollPane);
        
        // Modern bottom buttons
        HBox buttonBar = createModernButtonBar();
        setBottom(buttonBar);
    }
    
    private VBox createModernHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 3);"
        );
        
        Label titleLabel = new Label("Dispatcher Configuration");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: 700; -fx-text-fill: white;");
        
        Label descLabel = new Label("Configure dispatcher settings, company information, and policies");
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.9);");
        
        // Last saved info
        Label lastSavedLabel = new Label("Last updated: " + DispatcherSettings.getLastModified());
        lastSavedLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.7);");
        
        header.getChildren().addAll(titleLabel, descLabel, lastSavedLabel);
        
        return header;
    }
    
    private VBox createModernCard(String title, String subtitle, Node content) {
        VBox card = new VBox(15);
        card.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3); " +
            "-fx-padding: 20px;"
        );
        
        // Card header
        VBox cardHeader = new VBox(5);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: 600; -fx-text-fill: #212121;");
        
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #757575;");
        
        cardHeader.getChildren().addAll(titleLabel, subtitleLabel);
        
        Separator separator = new Separator();
        separator.setStyle("-fx-padding: 10px 0;");
        
        card.getChildren().addAll(cardHeader, separator, content);
        
        return card;
    }
    
    private GridPane createDispatcherSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        
        // Dispatcher Name
        dispatcherNameField = createModernTextField("Enter dispatcher name");
        
        // Phone
        dispatcherPhoneField = createModernTextField("000-000-0000");
        
        // Email
        dispatcherEmailField = createModernTextField("dispatcher@company.com");
        
        // Fax
        dispatcherFaxField = createModernTextField("000-000-0000");
        
        int row = 0;
        grid.add(createFieldLabel("Dispatcher Name:", true), 0, row);
        grid.add(dispatcherNameField, 1, row++);
        
        grid.add(createFieldLabel("Phone:", true), 0, row);
        grid.add(dispatcherPhoneField, 1, row++);
        
        grid.add(createFieldLabel("Email:", true), 0, row);
        grid.add(dispatcherEmailField, 1, row++);
        
        grid.add(createFieldLabel("Fax:", false), 0, row);
        grid.add(dispatcherFaxField, 1, row);
        
        // Add icons
        addFieldIcon(dispatcherNameField, "👤");
        addFieldIcon(dispatcherPhoneField, "📞");
        addFieldIcon(dispatcherEmailField, "✉️");
        addFieldIcon(dispatcherFaxField, "📠");
        
        return grid;
    }
    
    private GridPane createCompanySection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        
        // Company Name
        companyNameField = createModernTextField("Enter company name");
        
        // Logo
        logoPathField = createModernTextField("");
        logoPathField.setEditable(false);
        logoPathField.setPromptText("No logo selected");
        
        Button browseBtn = createModernButton("Browse...", "#2196F3", false);
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
                logoPathField.setPromptText("");
            }
        });
        
        Button clearBtn = createModernButton("Clear", "#F44336", false);
        clearBtn.setOnAction(e -> {
            logoPathField.setText("");
            logoPathField.setPromptText("No logo selected");
        });
        
        HBox logoBox = new HBox(10);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        logoBox.getChildren().addAll(logoPathField, browseBtn, clearBtn);
        HBox.setHgrow(logoPathField, Priority.ALWAYS);
        
        int row = 0;
        grid.add(createFieldLabel("Company Name:", true), 0, row);
        grid.add(companyNameField, 1, row++);
        
        grid.add(createFieldLabel("Company Logo:", false), 0, row);
        grid.add(logoBox, 1, row);
        
        // Add icons
        addFieldIcon(companyNameField, "🏢");
        
        return grid;
    }
    
    private VBox createPolicySection() {
        VBox content = new VBox(15);
        
        Label instructionLabel = new Label("Enter the pickup and delivery policy text that will be included in dispatch sheets:");
        instructionLabel.setWrapText(true);
        instructionLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #616161;");
        
        policyTextArea = new TextArea();
        policyTextArea.setPrefRowCount(15);
        policyTextArea.setWrapText(true);
        policyTextArea.setPromptText("Enter pickup and delivery policy...");
        policyTextArea.setStyle(
            "-fx-background-color: #FAFAFA; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 10px; " +
            "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
            "-fx-font-size: 13px;"
        );
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button resetDefaultBtn = createModernButton("📄 Reset to Default Policy", "#FF9800", false);
        resetDefaultBtn.setOnAction(e -> {
            policyTextArea.setText(getDefaultPolicy());
            showNotification("Policy reset to default", false);
        });
        
        Button clearBtn = createModernButton("🗑 Clear", "#F44336", false);
        clearBtn.setOnAction(e -> {
            policyTextArea.clear();
        });
        
        buttonBox.getChildren().addAll(clearBtn, resetDefaultBtn);
        
        content.getChildren().addAll(instructionLabel, policyTextArea, buttonBox);
        
        return content;
    }
    
    private VBox createNotificationSection() {
        VBox content = new VBox(15);
        
        // General notifications
        VBox generalBox = new VBox(10);
        Label generalLabel = new Label("General Notifications");
        generalLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #424242;");
        
        emailNotificationsCheck = createModernCheckBox("Enable email notifications");
        smsNotificationsCheck = createModernCheckBox("Enable SMS notifications");
        
        generalBox.getChildren().addAll(generalLabel, emailNotificationsCheck, smsNotificationsCheck);
        
        Separator separator = new Separator();
        
        // Alert settings
        VBox alertBox = new VBox(10);
        Label alertsLabel = new Label("Alert Settings");
        alertsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #424242;");
        
        latePickupAlert = createModernCheckBox("Alert on late pickup (30 min before)");
        lateDeliveryAlert = createModernCheckBox("Alert on late delivery (1 hour before)");
        driverHoursAlert = createModernCheckBox("Alert when driver approaches hour limits");
        unassignedLoadAlert = createModernCheckBox("Alert for unassigned loads (24 hours before pickup)");
        
        alertBox.getChildren().addAll(alertsLabel, latePickupAlert, lateDeliveryAlert, 
                                     driverHoursAlert, unassignedLoadAlert);
        
        content.getChildren().addAll(generalBox, separator, alertBox);
        
        return content;
    }
    
    private GridPane createAdvancedSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        
        // Auto-refresh interval
        refreshSpinner = new Spinner<>(10, 300, 30, 10);
        refreshSpinner.setPrefWidth(100);
        refreshSpinner.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px;"
        );
        
        Label refreshUnit = new Label("seconds");
        refreshUnit.setStyle("-fx-text-fill: #757575;");
        
        HBox refreshBox = new HBox(10);
        refreshBox.setAlignment(Pos.CENTER_LEFT);
        refreshBox.getChildren().addAll(refreshSpinner, refreshUnit);
        
        // Time zone
        timezoneCombo = createModernComboBox();
        timezoneCombo.getItems().addAll("Eastern", "Central", "Mountain", "Pacific");
        timezoneCombo.setValue("Eastern");
        
        // Default view
        defaultViewCombo = createModernComboBox();
        defaultViewCombo.getItems().addAll("Daily View", "12 Hour Grid", "24 Hour Grid", "Weekly Grid", "Fleet Status");
        defaultViewCombo.setValue("Daily View");
        
        int row = 0;
        grid.add(createFieldLabel("Auto-refresh interval:", false), 0, row);
        grid.add(refreshBox, 1, row++);
        
        grid.add(createFieldLabel("Time Zone:", true), 0, row);
        grid.add(timezoneCombo, 1, row++);
        
        grid.add(createFieldLabel("Default View:", true), 0, row);
        grid.add(defaultViewCombo, 1, row);
        
        return grid;
    }
    
    private HBox createModernButtonBar() {
        HBox buttonBar = new HBox(15);
        buttonBar.setPadding(new Insets(20));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-width: 1 0 0 0;"
        );
        
        // Status label
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #757575;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button cancelBtn = createModernButton("Cancel", "#757575", false);
        cancelBtn.setOnAction(e -> {
            loadSettings();
            showNotification("Changes discarded", false);
        });
        
        Button applyBtn = createModernButton("Apply", "#4CAF50", false);
        applyBtn.setOnAction(e -> {
            saveSettings();
            showNotification("Settings applied", false);
        });
        
        Button saveBtn = createModernButton("Save Settings", "#2196F3", true);
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> {
            saveSettings();
            showModernConfirmation();
        });
        
        buttonBar.getChildren().addAll(statusLabel, spacer, cancelBtn, applyBtn, saveBtn);
        
        return buttonBar;
    }
    
    private TextField createModernTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(300);
        field.setStyle(
            "-fx-background-color: #FAFAFA; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 8px 12px; " +
            "-fx-font-size: 14px;"
        );
        
        // Focus effect
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                field.setStyle(field.getStyle() + "-fx-border-color: #2196F3;");
            } else {
                field.setStyle(field.getStyle().replace("-fx-border-color: #2196F3;", "-fx-border-color: #E0E0E0;"));
            }
        });
        
        return field;
    }
    
    private ComboBox<String> createModernComboBox() {
        ComboBox<String> combo = new ComboBox<>();
        combo.setStyle(
            "-fx-background-color: #FAFAFA; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 4px 8px; " +
            "-fx-font-size: 14px;"
        );
        combo.setPrefWidth(200);
        return combo;
    }
    
    private CheckBox createModernCheckBox(String text) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setStyle("-fx-font-size: 13px;");
        return checkBox;
    }
    
    private Button createModernButton(String text, String color, boolean isPrimary) {
        Button button = new Button(text);
        
        if (isPrimary) {
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: 600; " +
                "-fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);",
                color
            ));
        } else {
            button.setStyle(String.format(
                "-fx-background-color: white; -fx-text-fill: %s; -fx-font-weight: 600; " +
                "-fx-background-radius: 5px; -fx-padding: 8px 16px; -fx-cursor: hand; " +
                "-fx-border-color: %s; -fx-border-width: 1px;",
                color, color
            ));
        }
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            if (isPrimary) {
                button.setStyle(button.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0, 0, 3);");
            } else {
                button.setStyle(button.getStyle().replace("white", "#F5F5F5"));
            }
        });
        
        button.setOnMouseExited(e -> {
            if (isPrimary) {
                button.setStyle(button.getStyle().replace(
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0, 0, 3);",
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);"
                ));
            } else {
                button.setStyle(button.getStyle().replace("#F5F5F5", "white"));
            }
        });
        
        return button;
    }
    
    private Label createFieldLabel(String text, boolean required) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: #616161; -fx-font-weight: 500;");
        label.setMinWidth(150);
        
        if (required) {
            Label requiredMark = new Label(" *");
            requiredMark.setStyle("-fx-text-fill: #F44336;");
            label.setGraphic(requiredMark);
            label.setContentDisplay(ContentDisplay.RIGHT);
        }
        
        return label;
    }
    
    private void addFieldIcon(TextField field, String emoji) {
        Label icon = new Label(emoji);
        icon.setStyle("-fx-font-size: 16px; -fx-padding: 0 5px 0 0;");
        
        HBox container = new HBox();
        container.getChildren().addAll(icon, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        
        // Replace the field in its parent
        if (field.getParent() instanceof GridPane) {
            GridPane grid = (GridPane) field.getParent();
            int col = GridPane.getColumnIndex(field);
            int row = GridPane.getRowIndex(field);
            grid.getChildren().remove(field);
            grid.add(container, col, row);
        }
    }
    
    private void loadSettings() {
        logger.info("Loading dispatcher settings");
        
        dispatcherNameField.setText(DispatcherSettings.getDispatcherName());
        dispatcherPhoneField.setText(DispatcherSettings.getDispatcherPhone());
        dispatcherEmailField.setText(DispatcherSettings.getDispatcherEmail());
        dispatcherFaxField.setText(DispatcherSettings.getDispatcherFax());
        companyNameField.setText(DispatcherSettings.getCompanyName());
        logoPathField.setText(DispatcherSettings.getCompanyLogoPath());
        policyTextArea.setText(DispatcherSettings.getPickupDeliveryPolicy());
        
        // Load notification settings
        emailNotificationsCheck.setSelected(DispatcherSettings.isEmailNotificationsEnabled());
        smsNotificationsCheck.setSelected(DispatcherSettings.isSmsNotificationsEnabled());
        latePickupAlert.setSelected(DispatcherSettings.isLatePickupAlertEnabled());
        lateDeliveryAlert.setSelected(DispatcherSettings.isLateDeliveryAlertEnabled());
        driverHoursAlert.setSelected(DispatcherSettings.isDriverHoursAlertEnabled());
        unassignedLoadAlert.setSelected(DispatcherSettings.isUnassignedLoadAlertEnabled());
        
        // Load advanced settings
        refreshSpinner.getValueFactory().setValue(DispatcherSettings.getAutoRefreshInterval());
        timezoneCombo.setValue(DispatcherSettings.getTimeZone());
        defaultViewCombo.setValue(DispatcherSettings.getDefaultView());
    }
    
    private void saveSettings() {
        logger.info("Saving dispatcher settings");
        
        // Validate required fields
        if (!validateSettings()) {
            return;
        }
        
        DispatcherSettings.setDispatcherName(dispatcherNameField.getText());
        DispatcherSettings.setDispatcherPhone(dispatcherPhoneField.getText());
        DispatcherSettings.setDispatcherEmail(dispatcherEmailField.getText());
        DispatcherSettings.setDispatcherFax(dispatcherFaxField.getText());
        DispatcherSettings.setCompanyName(companyNameField.getText());
        DispatcherSettings.setCompanyLogoPath(logoPathField.getText());
        DispatcherSettings.setPickupDeliveryPolicy(policyTextArea.getText());
        
        // Save notification settings
        DispatcherSettings.setEmailNotificationsEnabled(emailNotificationsCheck.isSelected());
        DispatcherSettings.setSmsNotificationsEnabled(smsNotificationsCheck.isSelected());
        DispatcherSettings.setLatePickupAlertEnabled(latePickupAlert.isSelected());
        DispatcherSettings.setLateDeliveryAlertEnabled(lateDeliveryAlert.isSelected());
        DispatcherSettings.setDriverHoursAlertEnabled(driverHoursAlert.isSelected());
        DispatcherSettings.setUnassignedLoadAlertEnabled(unassignedLoadAlert.isSelected());
        
        // Save advanced settings
        DispatcherSettings.setAutoRefreshInterval(refreshSpinner.getValue());
        DispatcherSettings.setTimeZone(timezoneCombo.getValue());
        DispatcherSettings.setDefaultView(defaultViewCombo.getValue());
        
        DispatcherSettings.saveSettings();
    }
    
    private boolean validateSettings() {
        // Check required fields
        if (dispatcherNameField.getText().trim().isEmpty()) {
            showValidationError("Dispatcher name is required");
            dispatcherNameField.requestFocus();
            return false;
        }
        
        if (dispatcherPhoneField.getText().trim().isEmpty()) {
            showValidationError("Dispatcher phone is required");
            dispatcherPhoneField.requestFocus();
            return false;
        }
        
        if (dispatcherEmailField.getText().trim().isEmpty()) {
            showValidationError("Dispatcher email is required");
            dispatcherEmailField.requestFocus();
            return false;
        }
        
        if (companyNameField.getText().trim().isEmpty()) {
            showValidationError("Company name is required");
            companyNameField.requestFocus();
            return false;
        }
        
        // Validate email format
        String email = dispatcherEmailField.getText().trim();
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showValidationError("Please enter a valid email address");
            dispatcherEmailField.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Required Field Missing");
        alert.setContentText(message);
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px;"
        );
        
        alert.showAndWait();
    }
    
    private void showModernConfirmation() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText("Dispatcher settings have been saved successfully.");
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px;"
        );
        
        // Add custom icon
        Label icon = new Label("✅");
        icon.setStyle("-fx-font-size: 48px;");
        alert.setGraphic(icon);
        
        alert.showAndWait();
    }
    
    private void showNotification(String message, boolean isError) {
        // Find the status label in button bar
        HBox buttonBar = (HBox) getBottom();
        if (buttonBar != null && !buttonBar.getChildren().isEmpty()) {
            Label statusLabel = (Label) buttonBar.getChildren().get(0);
            statusLabel.setText(message);
            statusLabel.setStyle(String.format(
                "-fx-font-size: 13px; -fx-text-fill: %s;",
                isError ? "#F44336" : "#4CAF50"
            ));
            
            // Auto-clear after 3 seconds
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                    javafx.util.Duration.seconds(3),
                    e -> statusLabel.setText("")
                )
            );
            timeline.play();
        }
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