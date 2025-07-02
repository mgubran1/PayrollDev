package com.company.payroll.dispatcher;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.scene.web.WebView;
import javafx.scene.shape.Circle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced configuration view for dispatcher settings with validation,
 * import/export, templates, and real-time preview
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherConfigView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherConfigView.class);
    
    // Constants
    private static final String CONFIG_FILE = "dispatcher_config.xml";
    private static final String BACKUP_DIR = "config_backups";
    private static final int MAX_BACKUPS = 10;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\d{3}-\\d{3}-\\d{4}$"
    );
    
    // Core Components
    private final DispatcherController controller;
    private final DispatcherSettings settings;
    private final Map<String, Node> fieldMap;
    private final SimpleBooleanProperty hasChanges;
    private final SimpleBooleanProperty formValid;
    
    // Form fields - Dispatcher Info
    private TextField dispatcherNameField;
    private TextField dispatcherPhoneField;
    private TextField dispatcherEmailField;
    private TextField dispatcherFaxField;
    private TextField dispatcherTitleField;
    private TextField dispatcherLicenseField;
    
    // Form fields - Company Info
    private TextField companyNameField;
    private TextField companyAddressField;
    private TextField companyCityField;
    private TextField companyStateField;
    private TextField companyZipField;
    private TextField companyDOTField;
    private TextField companyMCField;
    private TextField logoPathField;
    private ImageView logoPreview;
    
    // Form fields - Policy
    private TextArea policyTextArea;
    private ComboBox<PolicyTemplate> policyTemplateCombo;
    private CheckBox includeSafetyRulesCheck;
    private CheckBox includeInsuranceInfoCheck;
    
    // Form fields - Notifications
    private final Map<String, CheckBox> notificationChecks;
    private final Map<String, Spinner<Integer>> notificationTimings;
    private TextField emailServerField;
    private TextField emailPortField;
    private TextField emailUsernameField;
    private PasswordField emailPasswordField;
    private TextField smsGatewayField;
    private TextField smsApiKeyField;
    
    // Form fields - Advanced
    private Spinner<Integer> refreshIntervalSpinner;
    private ComboBox<ZoneId> timezoneCombo;
    private ComboBox<String> defaultViewCombo;
    private ComboBox<String> themeCombo;
    private Spinner<Integer> sessionTimeoutSpinner;
    private CheckBox enableMetricsCheck;
    private CheckBox enableAuditLogCheck;
    private ComboBox<String> languageCombo;
    
    // UI Components
    private TabPane tabPane;
    private Label statusLabel;
    private ProgressBar saveProgress;
    private Button saveButton;
    private Button applyButton;
    private Timeline autoSaveTimeline;
    
    // Validation
    private final Map<TextField, Label> validationLabels;
    private final Map<String, String> validationErrors;
    
    public DispatcherConfigView(DispatcherController controller) {
        this.controller = controller;
        this.settings = DispatcherSettings.getInstance();
        this.fieldMap = new ConcurrentHashMap<>();
        this.hasChanges = new SimpleBooleanProperty(false);
        this.formValid = new SimpleBooleanProperty(true);
        this.notificationChecks = new HashMap<>();
        this.notificationTimings = new HashMap<>();
        this.validationLabels = new HashMap<>();
        this.validationErrors = new ConcurrentHashMap<>();
        
        initializeUI();
        setupValidation();
        setupKeyboardShortcuts();
        loadSettings();
        setupAutoSave();
    }
    
    private void initializeUI() {
        // Apply professional styling
        getStylesheets().add(getClass().getResource("/styles/dispatcher-config.css").toExternalForm());
        getStyleClass().add("config-view");
        
        // Header with enhanced design
        VBox header = createEnhancedHeader();
        setTop(header);
        
        // Main content with tabs
        tabPane = createTabPane();
        setCenter(tabPane);
        
        // Bottom toolbar with status
        HBox bottomBar = createBottomBar();
        setBottom(bottomBar);
    }
    
    private VBox createEnhancedHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.getStyleClass().add("config-header");
        
        HBox titleRow = new HBox(20);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        
        // Icon
        Label iconLabel = new Label("⚙");
        iconLabel.setFont(Font.font("System", FontWeight.BOLD, 36));
        iconLabel.setTextFill(Color.web("#2196f3"));
        
        VBox titleBox = new VBox(3);
        Label titleLabel = new Label("Dispatcher Configuration");
        titleLabel.getStyleClass().add("config-title");
        
        Label subtitleLabel = new Label("Configure dispatcher settings, company information, and system preferences");
        subtitleLabel.getStyleClass().add("config-subtitle");
        
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Quick actions
        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER_RIGHT);
        
        Button importBtn = createStyledButton("📥 Import", "Import configuration from file", 
            e -> importConfiguration());
        Button exportBtn = createStyledButton("📤 Export", "Export current configuration", 
            e -> exportConfiguration());
        Button templateBtn = createStyledButton("📋 Templates", "Load from template", 
            e -> showTemplateDialog());
        
        quickActions.getChildren().addAll(importBtn, exportBtn, templateBtn);
        
        titleRow.getChildren().addAll(iconLabel, titleBox, spacer, quickActions);
        
        // Search bar for settings
        HBox searchBar = createSearchBar();
        
        header.getChildren().addAll(titleRow, searchBar);
        
        return header;
    }
    
    private HBox createSearchBar() {
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(0, 0, 10, 0));
        
        TextField searchField = new TextField();
        searchField.setPromptText("Search settings...");
        searchField.setPrefWidth(300);
        searchField.getStyleClass().add("search-field");
        
        searchField.textProperty().addListener((obs, old, text) -> {
            if (text != null && !text.isEmpty()) {
                filterSettings(text);
            } else {
                clearFilter();
            }
        });
        
        Label searchIcon = new Label("🔍");
        
        searchBar.getChildren().addAll(searchIcon, searchField);
        
        return searchBar;
    }
    
    private TabPane createTabPane() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("config-tabs");
        
        // Dispatcher Information Tab
        Tab dispatcherTab = new Tab("👤 Dispatcher");
        dispatcherTab.setContent(createDispatcherTab());
        
        // Company Information Tab
        Tab companyTab = new Tab("🏢 Company");
        companyTab.setContent(createCompanyTab());
        
        // Policy & Rules Tab
        Tab policyTab = new Tab("📜 Policy & Rules");
        policyTab.setContent(createPolicyTab());
        
        // Notifications Tab
        Tab notificationTab = new Tab("🔔 Notifications");
        notificationTab.setContent(createNotificationTab());
        
        // Advanced Settings Tab
        Tab advancedTab = new Tab("⚡ Advanced");
        advancedTab.setContent(createAdvancedTab());
        
        // System Info Tab
        Tab systemTab = new Tab("ℹ System");
        systemTab.setContent(createSystemTab());
        
        tabs.getTabs().addAll(dispatcherTab, companyTab, policyTab, 
                             notificationTab, advancedTab, systemTab);
        
        return tabs;
    }
    
    private ScrollPane createDispatcherTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Personal Information
        TitledPane personalPane = new TitledPane("Personal Information", 
            createDispatcherPersonalSection());
        personalPane.setExpanded(true);
        
        // Contact Information
        TitledPane contactPane = new TitledPane("Contact Information", 
            createDispatcherContactSection());
        
        // Credentials
        TitledPane credentialsPane = new TitledPane("Credentials & Certifications", 
            createDispatcherCredentialsSection());
        
        // Signature
        TitledPane signaturePane = new TitledPane("Digital Signature", 
            createSignatureSection());
        
        content.getChildren().addAll(personalPane, contactPane, credentialsPane, signaturePane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private GridPane createDispatcherPersonalSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // Name
        Label nameLabel = new Label("Full Name:");
        dispatcherNameField = createValidatedTextField("Name", true);
        Label nameValidation = createValidationLabel(dispatcherNameField);
        
        grid.add(nameLabel, 0, row);
        grid.add(dispatcherNameField, 1, row);
        grid.add(nameValidation, 2, row++);
        
        // Title
        Label titleLabel = new Label("Title:");
        dispatcherTitleField = createValidatedTextField("Title", false);
        dispatcherTitleField.setPromptText("e.g., Senior Dispatcher");
        
        grid.add(titleLabel, 0, row);
        grid.add(dispatcherTitleField, 1, row++);
        
        // Employee ID
        Label idLabel = new Label("Employee ID:");
        TextField employeeIdField = createValidatedTextField("EmployeeID", false);
        employeeIdField.setText(System.getProperty("user.name", ""));
        employeeIdField.setEditable(false);
        
        grid.add(idLabel, 0, row);
        grid.add(employeeIdField, 1, row++);
        
        return grid;
    }
    
    private GridPane createDispatcherContactSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // Phone
        Label phoneLabel = new Label("Phone:");
        dispatcherPhoneField = createValidatedTextField("Phone", true);
        dispatcherPhoneField.setPromptText("000-000-0000");
        Label phoneValidation = createValidationLabel(dispatcherPhoneField);
        
        grid.add(phoneLabel, 0, row);
        grid.add(dispatcherPhoneField, 1, row);
        grid.add(phoneValidation, 2, row++);
        
        // Email
        Label emailLabel = new Label("Email:");
        dispatcherEmailField = createValidatedTextField("Email", true);
        dispatcherEmailField.setPromptText("dispatcher@company.com");
        Label emailValidation = createValidationLabel(dispatcherEmailField);
        
        grid.add(emailLabel, 0, row);
        grid.add(dispatcherEmailField, 1, row);
        grid.add(emailValidation, 2, row++);
        
        // Fax
        Label faxLabel = new Label("Fax:");
        dispatcherFaxField = createValidatedTextField("Fax", false);
        dispatcherFaxField.setPromptText("000-000-0000");
        Label faxValidation = createValidationLabel(dispatcherFaxField);
        
        grid.add(faxLabel, 0, row);
        grid.add(dispatcherFaxField, 1, row);
        grid.add(faxValidation, 2, row++);
        
        // Mobile
        Label mobileLabel = new Label("Mobile:");
        TextField mobileField = createValidatedTextField("Mobile", false);
        mobileField.setPromptText("000-000-0000");
        
        grid.add(mobileLabel, 0, row);
        grid.add(mobileField, 1, row++);
        
        return grid;
    }
    
    private GridPane createDispatcherCredentialsSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // License Number
        Label licenseLabel = new Label("License #:");
        dispatcherLicenseField = createValidatedTextField("License", false);
        dispatcherLicenseField.setPromptText("Dispatcher license number");
        
        grid.add(licenseLabel, 0, row);
        grid.add(dispatcherLicenseField, 1, row++);
        
        // Certification
        Label certLabel = new Label("Certifications:");
        TextArea certArea = new TextArea();
        certArea.setPrefRowCount(3);
        certArea.setPromptText("List any relevant certifications...");
        
        grid.add(certLabel, 0, row);
        grid.add(certArea, 1, row++);
        
        // Years of Experience
        Label expLabel = new Label("Years of Experience:");
        Spinner<Integer> expSpinner = new Spinner<>(0, 50, 5);
        expSpinner.setPrefWidth(100);
        
        grid.add(expLabel, 0, row);
        grid.add(expSpinner, 1, row++);
        
        return grid;
    }
    
    private VBox createSignatureSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label instructionLabel = new Label("Upload or draw your digital signature:");
        
        HBox signatureBox = new HBox(20);
        signatureBox.setAlignment(Pos.CENTER_LEFT);
        
        // Signature preview
        BorderPane signaturePreview = new BorderPane();
        signaturePreview.setPrefSize(200, 80);
        signaturePreview.setStyle("-fx-border-color: #ccc; -fx-border-width: 1; " +
                                 "-fx-background-color: white;");
        
        Label placeholderLabel = new Label("No signature");
        placeholderLabel.setStyle("-fx-text-fill: #999;");
        signaturePreview.setCenter(placeholderLabel);
        
        // Buttons
        VBox buttonBox = new VBox(10);
        Button uploadBtn = new Button("Upload Image");
        uploadBtn.setOnAction(e -> uploadSignature(signaturePreview));
        
        Button drawBtn = new Button("Draw Signature");
        drawBtn.setOnAction(e -> openSignaturePad(signaturePreview));
        
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> clearSignature(signaturePreview));
        
        buttonBox.getChildren().addAll(uploadBtn, drawBtn, clearBtn);
        
        signatureBox.getChildren().addAll(signaturePreview, buttonBox);
        
        content.getChildren().addAll(instructionLabel, signatureBox);
        
        return content;
    }
    
    private ScrollPane createCompanyTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Basic Information
        TitledPane basicPane = new TitledPane("Basic Information", 
            createCompanyBasicSection());
        basicPane.setExpanded(true);
        
        // Address Information
        TitledPane addressPane = new TitledPane("Address", 
            createCompanyAddressSection());
        
        // Regulatory Information
        TitledPane regulatoryPane = new TitledPane("Regulatory Information", 
            createCompanyRegulatorySection());
        
        // Branding
        TitledPane brandingPane = new TitledPane("Branding", 
            createCompanyBrandingSection());
        
        content.getChildren().addAll(basicPane, addressPane, regulatoryPane, brandingPane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private GridPane createCompanyBasicSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // Company Name
        Label nameLabel = new Label("Company Name:");
        companyNameField = createValidatedTextField("CompanyName", true);
        Label nameValidation = createValidationLabel(companyNameField);
        
        grid.add(nameLabel, 0, row);
        grid.add(companyNameField, 1, row);
        grid.add(nameValidation, 2, row++);
        
        // DBA Name
        Label dbaLabel = new Label("DBA Name:");
        TextField dbaField = createValidatedTextField("DBAName", false);
        dbaField.setPromptText("Doing Business As (if different)");
        
        grid.add(dbaLabel, 0, row);
        grid.add(dbaField, 1, row++);
        
        // Company Type
        Label typeLabel = new Label("Company Type:");
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("LLC", "Corporation", "Partnership", "Sole Proprietorship");
        typeCombo.setValue("LLC");
        
        grid.add(typeLabel, 0, row);
        grid.add(typeCombo, 1, row++);
        
        // EIN
        Label einLabel = new Label("EIN:");
        TextField einField = createValidatedTextField("EIN", false);
        einField.setPromptText("00-0000000");
        
        grid.add(einLabel, 0, row);
        grid.add(einField, 1, row++);
        
        return grid;
    }
    
    private GridPane createCompanyAddressSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // Street Address
        Label addressLabel = new Label("Street Address:");
        companyAddressField = createValidatedTextField("Address", true);
        
        grid.add(addressLabel, 0, row);
        grid.add(companyAddressField, 1, row++);
        
        // City
        Label cityLabel = new Label("City:");
        companyCityField = createValidatedTextField("City", true);
        
        grid.add(cityLabel, 0, row);
        grid.add(companyCityField, 1, row++);
        
        // State
        Label stateLabel = new Label("State:");
        companyStateField = createValidatedTextField("State", true);
        companyStateField.setPrefWidth(100);
        
        // Zip
        Label zipLabel = new Label("ZIP Code:");
        companyZipField = createValidatedTextField("Zip", true);
        companyZipField.setPrefWidth(120);
        
        HBox stateZipBox = new HBox(20);
        stateZipBox.getChildren().addAll(
            new HBox(10, stateLabel, companyStateField),
            new HBox(10, zipLabel, companyZipField)
        );
        
        grid.add(new Label("State/ZIP:"), 0, row);
        grid.add(stateZipBox, 1, row++);
        
        return grid;
    }
    
    private GridPane createCompanyRegulatorySection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // DOT Number
        Label dotLabel = new Label("DOT Number:");
        companyDOTField = createValidatedTextField("DOT", true);
        companyDOTField.setPromptText("0000000");
        Label dotValidation = createValidationLabel(companyDOTField);
        
        grid.add(dotLabel, 0, row);
        grid.add(companyDOTField, 1, row);
        grid.add(dotValidation, 2, row++);
        
        // MC Number
        Label mcLabel = new Label("MC Number:");
        companyMCField = createValidatedTextField("MC", true);
        companyMCField.setPromptText("000000");
        Label mcValidation = createValidationLabel(companyMCField);
        
        grid.add(mcLabel, 0, row);
        grid.add(companyMCField, 1, row);
        grid.add(mcValidation, 2, row++);
        
        // Insurance Info
        Label insuranceLabel = new Label("Insurance Carrier:");
        TextField insuranceField = createValidatedTextField("Insurance", false);
        
        grid.add(insuranceLabel, 0, row);
        grid.add(insuranceField, 1, row++);
        
        // Policy Number
        Label policyNumLabel = new Label("Policy Number:");
        TextField policyNumField = createValidatedTextField("PolicyNum", false);
        
        grid.add(policyNumLabel, 0, row);
        grid.add(policyNumField, 1, row++);
        
        return grid;
    }
    
    private VBox createCompanyBrandingSection() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        
        // Logo upload
        Label logoLabel = new Label("Company Logo:");
        logoLabel.setStyle("-fx-font-weight: bold;");
        
        HBox logoBox = new HBox(15);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        
        // Logo preview
        logoPreview = new ImageView();
        logoPreview.setFitWidth(150);
        logoPreview.setFitHeight(100);
        logoPreview.setPreserveRatio(true);
        logoPreview.setStyle("-fx-border-color: #ddd; -fx-border-width: 1;");
        
        VBox logoControls = new VBox(10);
        
        logoPathField = new TextField();
        logoPathField.setPrefWidth(300);
        logoPathField.setEditable(false);
        logoPathField.setPromptText("No logo selected");
        
        HBox buttonBox = new HBox(10);
        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> browseLogo());
        
        Button clearLogoBtn = new Button("Clear");
        clearLogoBtn.setOnAction(e -> clearLogo());
        
        buttonBox.getChildren().addAll(browseBtn, clearLogoBtn);
        
        logoControls.getChildren().addAll(logoPathField, buttonBox);
        
        logoBox.getChildren().addAll(logoPreview, logoControls);
        
        // Brand colors
        Label colorsLabel = new Label("Brand Colors:");
        colorsLabel.setStyle("-fx-font-weight: bold;");
        
        HBox colorsBox = new HBox(15);
        
        ColorPicker primaryColor = new ColorPicker(Color.web("#2196f3"));
        primaryColor.setPrefWidth(120);
        Label primaryLabel = new Label("Primary:");
        
        ColorPicker secondaryColor = new ColorPicker(Color.web("#ff9800"));
        secondaryColor.setPrefWidth(120);
        Label secondaryLabel = new Label("Secondary:");
        
        colorsBox.getChildren().addAll(
            primaryLabel, primaryColor,
            secondaryLabel, secondaryColor
        );
        
        content.getChildren().addAll(logoLabel, logoBox, 
                                    new Separator(), 
                                    colorsLabel, colorsBox);
        
        return content;
    }
    
    // Continued in next message...
	    // ... continuing from createCompanyBrandingSection() ...
    
    private ScrollPane createPolicyTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Policy Templates
        TitledPane templatesPane = new TitledPane("Policy Templates", 
            createPolicyTemplatesSection());
        templatesPane.setExpanded(true);
        
        // Current Policy
        TitledPane currentPolicyPane = new TitledPane("Pickup & Delivery Policy", 
            createCurrentPolicySection());
        
        // Additional Policies
        TitledPane additionalPane = new TitledPane("Additional Policies", 
            createAdditionalPoliciesSection());
        
        // Policy Preview
        TitledPane previewPane = new TitledPane("Policy Preview", 
            createPolicyPreviewSection());
        
        content.getChildren().addAll(templatesPane, currentPolicyPane, 
                                    additionalPane, previewPane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private VBox createPolicyTemplatesSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label instructionLabel = new Label("Select a template or create custom policy:");
        
        policyTemplateCombo = new ComboBox<>();
        policyTemplateCombo.setPrefWidth(400);
        policyTemplateCombo.getItems().addAll(
            new PolicyTemplate("Standard", getStandardPolicyTemplate()),
            new PolicyTemplate("Refrigerated", getRefrigeratedPolicyTemplate()),
            new PolicyTemplate("Hazmat", getHazmatPolicyTemplate()),
            new PolicyTemplate("Expedited", getExpeditedPolicyTemplate()),
            new PolicyTemplate("Custom", "")
        );
        
        policyTemplateCombo.setConverter(new StringConverter<PolicyTemplate>() {
            @Override
            public String toString(PolicyTemplate template) {
                return template != null ? template.getName() : "";
            }
            
            @Override
            public PolicyTemplate fromString(String string) {
                return null;
            }
        });
        
        policyTemplateCombo.setOnAction(e -> {
            PolicyTemplate selected = policyTemplateCombo.getValue();
            if (selected != null && !selected.getName().equals("Custom")) {
                policyTextArea.setText(selected.getContent());
            }
        });
        
        HBox optionsBox = new HBox(20);
        includeSafetyRulesCheck = new CheckBox("Include Safety Rules");
        includeSafetyRulesCheck.setSelected(true);
        includeInsuranceInfoCheck = new CheckBox("Include Insurance Information");
        includeInsuranceInfoCheck.setSelected(true);
        
        optionsBox.getChildren().addAll(includeSafetyRulesCheck, includeInsuranceInfoCheck);
        
        content.getChildren().addAll(instructionLabel, policyTemplateCombo, optionsBox);
        
        return content;
    }
    
    private VBox createCurrentPolicySection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label instructionLabel = new Label("Edit the policy text below:");
        
        policyTextArea = new TextArea();
        policyTextArea.setPrefRowCount(20);
        policyTextArea.setWrapText(true);
        policyTextArea.setPromptText("Enter pickup and delivery policy...");
        policyTextArea.getStyleClass().add("policy-editor");
        
        // Character count
        Label charCountLabel = new Label("0 / 5000 characters");
        charCountLabel.getStyleClass().add("char-count");
        
        policyTextArea.textProperty().addListener((obs, old, text) -> {
            int length = text != null ? text.length() : 0;
            charCountLabel.setText(length + " / 5000 characters");
            if (length > 5000) {
                charCountLabel.setTextFill(Color.RED);
            } else {
                charCountLabel.setTextFill(Color.GRAY);
            }
            hasChanges.set(true);
        });
        
        HBox buttonBar = new HBox(10);
        Button resetDefaultBtn = new Button("Reset to Default");
        resetDefaultBtn.setOnAction(e -> {
            policyTextArea.setText(DispatcherSettings.getDefaultPolicy());
        });
        
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> policyTextArea.clear());
        
        Button previewBtn = new Button("Preview");
        previewBtn.setOnAction(e -> previewPolicy());
        
        buttonBar.getChildren().addAll(resetDefaultBtn, clearBtn, previewBtn);
        
        content.getChildren().addAll(instructionLabel, policyTextArea, 
                                    charCountLabel, buttonBar);
        
        return content;
    }
    
    private VBox createAdditionalPoliciesSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label titleLabel = new Label("Manage additional policy documents:");
        
        TableView<PolicyDocument> policyTable = new TableView<>();
        policyTable.setPrefHeight(200);
        
        TableColumn<PolicyDocument, String> nameCol = new TableColumn<>("Policy Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setPrefWidth(200);
        
        TableColumn<PolicyDocument, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setPrefWidth(100);
        
        TableColumn<PolicyDocument, String> updatedCol = new TableColumn<>("Last Updated");
        updatedCol.setCellValueFactory(data -> data.getValue().lastUpdatedProperty());
        updatedCol.setPrefWidth(150);
        
        TableColumn<PolicyDocument, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            {
                editBtn.getStyleClass().add("small-button");
                deleteBtn.getStyleClass().add("small-button");
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5, editBtn, deleteBtn);
                    setGraphic(buttons);
                }
            }
        });
        actionCol.setPrefWidth(120);
        
        policyTable.getColumns().addAll(nameCol, typeCol, updatedCol, actionCol);
        
        HBox addBar = new HBox(10);
        Button addPolicyBtn = new Button("Add Policy");
        addPolicyBtn.setOnAction(e -> addNewPolicy());
        
        Button importPolicyBtn = new Button("Import from File");
        importPolicyBtn.setOnAction(e -> importPolicy());
        
        addBar.getChildren().addAll(addPolicyBtn, importPolicyBtn);
        
        content.getChildren().addAll(titleLabel, policyTable, addBar);
        
        return content;
    }
    
    private VBox createPolicyPreviewSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label titleLabel = new Label("Policy Preview (as it will appear on documents):");
        
        WebView previewView = new WebView();
        previewView.setPrefHeight(300);
        
        content.getChildren().addAll(titleLabel, previewView);
        
        return content;
    }
    
    private ScrollPane createNotificationTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // General Settings
        TitledPane generalPane = new TitledPane("General Settings", 
            createNotificationGeneralSection());
        generalPane.setExpanded(true);
        
        // Email Configuration
        TitledPane emailPane = new TitledPane("Email Configuration", 
            createEmailConfigSection());
        
        // SMS Configuration
        TitledPane smsPane = new TitledPane("SMS Configuration", 
            createSMSConfigSection());
        
        // Notification Rules
        TitledPane rulesPane = new TitledPane("Notification Rules", 
            createNotificationRulesSection());
        
        content.getChildren().addAll(generalPane, emailPane, smsPane, rulesPane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private VBox createNotificationGeneralSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        CheckBox emailNotifyCheck = new CheckBox("Enable Email Notifications");
        emailNotifyCheck.setSelected(true);
        notificationChecks.put("email", emailNotifyCheck);
        
        CheckBox smsNotifyCheck = new CheckBox("Enable SMS Notifications");
        notificationChecks.put("sms", smsNotifyCheck);
        
        CheckBox pushNotifyCheck = new CheckBox("Enable Push Notifications");
        notificationChecks.put("push", pushNotifyCheck);
        
        CheckBox soundNotifyCheck = new CheckBox("Enable Sound Alerts");
        soundNotifyCheck.setSelected(true);
        notificationChecks.put("sound", soundNotifyCheck);
        
        Separator separator = new Separator();
        
        Label quietHoursLabel = new Label("Quiet Hours:");
        HBox quietHoursBox = new HBox(10);
        
        ComboBox<String> startHourCombo = new ComboBox<>();
        startHourCombo.getItems().addAll(generateHours());
        startHourCombo.setValue("22:00");
        
        Label toLabel = new Label("to");
        
        ComboBox<String> endHourCombo = new ComboBox<>();
        endHourCombo.getItems().addAll(generateHours());
        endHourCombo.setValue("06:00");
        
        CheckBox enableQuietHours = new CheckBox("Enable");
        
        quietHoursBox.getChildren().addAll(enableQuietHours, startHourCombo, 
                                          toLabel, endHourCombo);
        
        content.getChildren().addAll(
            emailNotifyCheck, smsNotifyCheck, pushNotifyCheck, soundNotifyCheck,
            separator, quietHoursLabel, quietHoursBox
        );
        
        return content;
    }
    
    private GridPane createEmailConfigSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // SMTP Server
        Label serverLabel = new Label("SMTP Server:");
        emailServerField = createValidatedTextField("EmailServer", true);
        emailServerField.setPromptText("smtp.gmail.com");
        
        grid.add(serverLabel, 0, row);
        grid.add(emailServerField, 1, row++);
        
        // Port
        Label portLabel = new Label("Port:");
        emailPortField = createValidatedTextField("EmailPort", true);
        emailPortField.setText("587");
        emailPortField.setPrefWidth(100);
        
        // SSL/TLS
        CheckBox sslCheck = new CheckBox("Use SSL/TLS");
        sslCheck.setSelected(true);
        
        HBox portBox = new HBox(20, emailPortField, sslCheck);
        
        grid.add(portLabel, 0, row);
        grid.add(portBox, 1, row++);
        
        // Username
        Label usernameLabel = new Label("Username:");
        emailUsernameField = createValidatedTextField("EmailUsername", true);
        emailUsernameField.setPromptText("your-email@gmail.com");
        
        grid.add(usernameLabel, 0, row);
        grid.add(emailUsernameField, 1, row++);
        
        // Password
        Label passwordLabel = new Label("Password:");
        emailPasswordField = new PasswordField();
        emailPasswordField.setPromptText("Enter password");
        fieldMap.put("EmailPassword", emailPasswordField);
        
        grid.add(passwordLabel, 0, row);
        grid.add(emailPasswordField, 1, row++);
        
        // Test button
        Button testEmailBtn = new Button("Test Email Configuration");
        testEmailBtn.setOnAction(e -> testEmailConfiguration());
        
        grid.add(new Label(), 0, row);
        grid.add(testEmailBtn, 1, row++);
        
        return grid;
    }
    
    private GridPane createSMSConfigSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // SMS Gateway
        Label gatewayLabel = new Label("SMS Gateway:");
        smsGatewayField = createValidatedTextField("SMSGateway", false);
        smsGatewayField.setPromptText("api.twilio.com");
        
        grid.add(gatewayLabel, 0, row);
        grid.add(smsGatewayField, 1, row++);
        
        // API Key
        Label apiKeyLabel = new Label("API Key:");
        smsApiKeyField = createValidatedTextField("SMSApiKey", false);
        smsApiKeyField.setPromptText("Enter API key");
        
        grid.add(apiKeyLabel, 0, row);
        grid.add(smsApiKeyField, 1, row++);
        
        // Test button
        Button testSMSBtn = new Button("Test SMS Configuration");
        testSMSBtn.setOnAction(e -> testSMSConfiguration());
        
        grid.add(new Label(), 0, row);
        grid.add(testSMSBtn, 1, row++);
        
        return grid;
    }
    
    private VBox createNotificationRulesSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label titleLabel = new Label("Configure when to send notifications:");
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        VBox rulesContainer = new VBox(15);
        
        // Late Pickup Alert
        rulesContainer.getChildren().add(
            createNotificationRule("Late Pickup Alert", 
                "Alert when approaching pickup time", 30, "minutes before")
        );
        
        // Late Delivery Alert
        rulesContainer.getChildren().add(
            createNotificationRule("Late Delivery Alert", 
                "Alert when approaching delivery time", 60, "minutes before")
        );
        
        // Driver Hours Alert
        rulesContainer.getChildren().add(
            createNotificationRule("Driver Hours Alert", 
                "Alert when driver approaches hour limits", 30, "minutes before limit")
        );
        
        // Unassigned Load Alert
        rulesContainer.getChildren().add(
            createNotificationRule("Unassigned Load Alert", 
                "Alert for unassigned loads", 24, "hours before pickup")
        );
        
        // Maintenance Due Alert
        rulesContainer.getChildren().add(
            createNotificationRule("Maintenance Due Alert", 
                "Alert for vehicle maintenance", 48, "hours before due")
        );
        
        content.getChildren().addAll(titleLabel, rulesContainer);
        
        return content;
    }
    
    private HBox createNotificationRule(String title, String description, 
                                       int defaultValue, String unit) {
        HBox ruleBox = new HBox(15);
        ruleBox.setAlignment(Pos.CENTER_LEFT);
        ruleBox.setPadding(new Insets(10));
        ruleBox.getStyleClass().add("notification-rule");
        
        CheckBox enableCheck = new CheckBox();
        enableCheck.setSelected(true);
        
        VBox textBox = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold;");
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");
        textBox.getChildren().addAll(titleLabel, descLabel);
        textBox.setPrefWidth(300);
        
        Spinner<Integer> timingSpinner = new Spinner<>(5, 1440, defaultValue, 5);
        timingSpinner.setPrefWidth(80);
        notificationTimings.put(title, timingSpinner);
        
        Label unitLabel = new Label(unit);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        ruleBox.getChildren().addAll(enableCheck, textBox, spacer, 
                                     timingSpinner, unitLabel);
        
        return ruleBox;
    }
    
    private ScrollPane createAdvancedTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // System Settings
        TitledPane systemPane = new TitledPane("System Settings", 
            createSystemSettingsSection());
        systemPane.setExpanded(true);
        
        // Performance Settings
        TitledPane performancePane = new TitledPane("Performance", 
            createPerformanceSection());
        
        // Security Settings
        TitledPane securityPane = new TitledPane("Security", 
            createSecuritySection());
        
        // Backup Settings
        TitledPane backupPane = new TitledPane("Backup & Recovery", 
            createBackupSection());
        
        content.getChildren().addAll(systemPane, performancePane, 
                                    securityPane, backupPane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private GridPane createSystemSettingsSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        // Auto-refresh interval
        Label refreshLabel = new Label("Auto-refresh interval:");
        refreshIntervalSpinner = new Spinner<>(10, 300, 30, 10);
        refreshIntervalSpinner.setPrefWidth(100);
        Label refreshUnit = new Label("seconds");
        
        HBox refreshBox = new HBox(10, refreshIntervalSpinner, refreshUnit);
        
        grid.add(refreshLabel, 0, row);
        grid.add(refreshBox, 1, row++);
        
        // Time zone
        Label timezoneLabel = new Label("Time Zone:");
        timezoneCombo = new ComboBox<>();
        timezoneCombo.getItems().addAll(ZoneId.getAvailableZoneIds().stream()
            .map(ZoneId::of)
            .sorted(Comparator.comparing(ZoneId::toString))
            .collect(Collectors.toList()));
        timezoneCombo.setValue(ZoneId.systemDefault());
        timezoneCombo.setPrefWidth(250);
        
        grid.add(timezoneLabel, 0, row);
        grid.add(timezoneCombo, 1, row++);
        
        // Default view
        Label defaultViewLabel = new Label("Default View:");
        defaultViewCombo = new ComboBox<>();
        defaultViewCombo.getItems().addAll(
            "Daily View", "12 Hour Grid", "24 Hour Grid", "Weekly Grid", "Fleet Status"
        );
        defaultViewCombo.setValue("Daily View");
        
        grid.add(defaultViewLabel, 0, row);
        grid.add(defaultViewCombo, 1, row++);
        
        // Theme
        Label themeLabel = new Label("Theme:");
        themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark", "Auto");
        themeCombo.setValue("Light");
        
        grid.add(themeLabel, 0, row);
        grid.add(themeCombo, 1, row++);
        
        // Language
        Label languageLabel = new Label("Language:");
        languageCombo = new ComboBox<>();
        languageCombo.getItems().addAll("English", "Spanish", "French");
        languageCombo.setValue("English");
        
        grid.add(languageLabel, 0, row);
        grid.add(languageCombo, 1, row++);
        
        return grid;
    }
    
    private VBox createPerformanceSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Enable metrics
        enableMetricsCheck = new CheckBox("Enable Performance Metrics");
        enableMetricsCheck.setSelected(true);
        
        // Enable audit log
        enableAuditLogCheck = new CheckBox("Enable Audit Logging");
        enableAuditLogCheck.setSelected(true);
        
        // Cache settings
        Label cacheLabel = new Label("Cache Settings:");
        cacheLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox enableCacheCheck = new CheckBox("Enable caching");
        enableCacheCheck.setSelected(true);
        
        HBox cacheSizeBox = new HBox(10);
        Label cacheSizeLabel = new Label("Cache size:");
        Spinner<Integer> cacheSizeSpinner = new Spinner<>(50, 500, 200, 50);
        cacheSizeSpinner.setPrefWidth(100);
        Label mbLabel = new Label("MB");
        cacheSizeBox.getChildren().addAll(cacheSizeLabel, cacheSizeSpinner, mbLabel);
        
        Button clearCacheBtn = new Button("Clear Cache");
        clearCacheBtn.setOnAction(e -> clearCache());
        
        content.getChildren().addAll(
            enableMetricsCheck, enableAuditLogCheck,
            new Separator(),
            cacheLabel, enableCacheCheck, cacheSizeBox, clearCacheBtn
        );
        
        return content;
    }
    
    private VBox createSecuritySection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Session timeout
        HBox timeoutBox = new HBox(10);
        Label timeoutLabel = new Label("Session timeout:");
        sessionTimeoutSpinner = new Spinner<>(5, 120, 30, 5);
        sessionTimeoutSpinner.setPrefWidth(100);
        Label minLabel = new Label("minutes");
        timeoutBox.getChildren().addAll(timeoutLabel, sessionTimeoutSpinner, minLabel);
        
        // Password requirements
        Label passwordLabel = new Label("Password Requirements:");
        passwordLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox requireComplexCheck = new CheckBox("Require complex passwords");
        requireComplexCheck.setSelected(true);
        
        CheckBox require2FACheck = new CheckBox("Require two-factor authentication");
        
        // API access
        Label apiLabel = new Label("API Access:");
        apiLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox enableAPICheck = new CheckBox("Enable API access");
        
        Button generateAPIKeyBtn = new Button("Generate API Key");
        generateAPIKeyBtn.setOnAction(e -> generateAPIKey());
        
        content.getChildren().addAll(
            timeoutBox,
            new Separator(),
            passwordLabel, requireComplexCheck, require2FACheck,
            new Separator(),
            apiLabel, enableAPICheck, generateAPIKeyBtn
        );
        
        return content;
    }
    
    private VBox createBackupSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        CheckBox autoBackupCheck = new CheckBox("Enable automatic backups");
        autoBackupCheck.setSelected(true);
        
        HBox frequencyBox = new HBox(10);
        Label freqLabel = new Label("Backup frequency:");
        ComboBox<String> freqCombo = new ComboBox<>();
        freqCombo.getItems().addAll("Every 6 hours", "Daily", "Weekly");
        freqCombo.setValue("Daily");
        frequencyBox.getChildren().addAll(freqLabel, freqCombo);
        
        HBox locationBox = new HBox(10);
        Label locLabel = new Label("Backup location:");
        TextField backupPathField = new TextField();
        backupPathField.setText(BACKUP_DIR);
        backupPathField.setPrefWidth(250);
        Button browseBackupBtn = new Button("Browse...");
        browseBackupBtn.setOnAction(e -> browseBackupLocation(backupPathField));
        locationBox.getChildren().addAll(locLabel, backupPathField, browseBackupBtn);
        
        HBox retentionBox = new HBox(10);
        Label retentionLabel = new Label("Keep backups for:");
        Spinner<Integer> retentionSpinner = new Spinner<>(7, 365, 30, 7);
        retentionSpinner.setPrefWidth(100);
        Label daysLabel = new Label("days");
        retentionBox.getChildren().addAll(retentionLabel, retentionSpinner, daysLabel);
        
        Separator separator = new Separator();
        
        HBox actionBox = new HBox(10);
        Button backupNowBtn = new Button("Backup Now");
        backupNowBtn.setOnAction(e -> performBackup());
        
        Button restoreBtn = new Button("Restore from Backup");
        restoreBtn.setOnAction(e -> restoreFromBackup());
        
        actionBox.getChildren().addAll(backupNowBtn, restoreBtn);
        
        content.getChildren().addAll(
            autoBackupCheck, frequencyBox, locationBox, retentionBox,
            separator, actionBox
        );
        
        return content;
    }
    
    private ScrollPane createSystemTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // System Information
        TitledPane infoPane = new TitledPane("System Information", 
            createSystemInfoSection());
        infoPane.setExpanded(true);
        
        // Database Status
        TitledPane dbPane = new TitledPane("Database Status", 
            createDatabaseStatusSection());
        
        // Activity Log
        TitledPane logPane = new TitledPane("Recent Activity", 
            createActivityLogSection());
        
        content.getChildren().addAll(infoPane, dbPane, logPane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private GridPane createSystemInfoSection() {
        GridPane grid = createStyledGrid();
        
        int row = 0;
        
        addInfoRow(grid, row++, "Application Version:", "2.0.0");
        addInfoRow(grid, row++, "Java Version:", System.getProperty("java.version"));
        addInfoRow(grid, row++, "JavaFX Version:", System.getProperty("javafx.version"));
        addInfoRow(grid, row++, "Operating System:", 
            System.getProperty("os.name") + " " + System.getProperty("os.version"));
        addInfoRow(grid, row++, "User Name:", System.getProperty("user.name"));
        addInfoRow(grid, row++, "User Home:", System.getProperty("user.home"));
        addInfoRow(grid, row++, "Current Time:", 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        return grid;
    }
    
    private void addInfoRow(GridPane grid, int row, String label, String value) {
        Label lblLabel = new Label(label);
        lblLabel.setStyle("-fx-font-weight: bold;");
        Label valLabel = new Label(value);
        valLabel.setStyle("-fx-font-family: monospace;");
        
        grid.add(lblLabel, 0, row);
        grid.add(valLabel, 1, row);
    }
    
    private VBox createDatabaseStatusSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        HBox statusBox = new HBox(20);
        
        Circle statusIndicator = new Circle(8);
        statusIndicator.setFill(Color.LIGHTGREEN);
        
        Label statusLabel = new Label("Connected");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
        
        statusBox.getChildren().addAll(statusIndicator, statusLabel);
        
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(5);
        
        addInfoRow(statsGrid, 0, "Total Drivers:", "45");
        addInfoRow(statsGrid, 1, "Total Loads:", "1,234");
        addInfoRow(statsGrid, 2, "Active Sessions:", "12");
        addInfoRow(statsGrid, 3, "Database Size:", "256 MB");
        
        Button optimizeBtn = new Button("Optimize Database");
        optimizeBtn.setOnAction(e -> optimizeDatabase());
        
        content.getChildren().addAll(statusBox, statsGrid, optimizeBtn);
        
        return content;
    }
    
    private VBox createActivityLogSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        TableView<ActivityEntry> activityTable = new TableView<>();
        activityTable.setPrefHeight(200);
        
        TableColumn<ActivityEntry, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> data.getValue().timeProperty());
        timeCol.setPrefWidth(150);
        
        TableColumn<ActivityEntry, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(data -> data.getValue().userProperty());
        userCol.setPrefWidth(100);
        
        TableColumn<ActivityEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(data -> data.getValue().actionProperty());
        actionCol.setPrefWidth(300);
        
        activityTable.getColumns().addAll(timeCol, userCol, actionCol);
        
        // Sample data
        ObservableList<ActivityEntry> activities = FXCollections.observableArrayList(
            new ActivityEntry("2025-07-02 10:05:23", "mgubran1", "Configuration updated"),
            new ActivityEntry("2025-07-02 09:45:12", "mgubran1", "Driver status changed"),
            new ActivityEntry("2025-07-02 09:30:45", "system", "Auto-backup completed")
        );
        activityTable.setItems(activities);
        
        Button viewFullLogBtn = new Button("View Full Log");
        viewFullLogBtn.setOnAction(e -> viewFullActivityLog());
        
        content.getChildren().addAll(activityTable, viewFullLogBtn);
        
        return content;
    }
    
    private HBox createBottomBar() {
        HBox bottomBar = new HBox(15);
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.getStyleClass().add("bottom-bar");
        
        // Status
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");
        
        // Progress
        saveProgress = new ProgressBar();
        saveProgress.setPrefWidth(150);
        saveProgress.setVisible(false);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Buttons
        Button revertBtn = new Button("Revert Changes");
        revertBtn.setOnAction(e -> revertChanges());
        revertBtn.disableProperty().bind(hasChanges.not());
        
        applyButton = new Button("Apply");
        applyButton.setOnAction(e -> {
            if (validateAndSave()) {
                statusLabel.setText("Settings applied successfully");
            }
        });
        applyButton.disableProperty().bind(
            Bindings.or(hasChanges.not(), formValid.not())
        );
        
        saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            if (validateAndSave()) {
                statusLabel.setText("Settings saved successfully");
                // Close or navigate away
            }
        });
        saveButton.disableProperty().bind(
            Bindings.or(hasChanges.not(), formValid.not())
        );
        
        bottomBar.getChildren().addAll(
            statusLabel, saveProgress, spacer, 
            revertBtn, applyButton, saveButton
        );
        
        return bottomBar;
    }
    
    // Helper methods
    private GridPane createStyledGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        return grid;
    }
    
    private TextField createValidatedTextField(String fieldId, boolean required) {
        TextField field = new TextField();
        field.setPrefWidth(300);
        fieldMap.put(fieldId, field);
        
        field.textProperty().addListener((obs, old, text) -> {
            validateField(fieldId, text, required);
            hasChanges.set(true);
        });
        
        return field;
    }
    
    private Label createValidationLabel(TextField field) {
        Label label = new Label();
        label.getStyleClass().add("validation-label");
        validationLabels.put(field, label);
        return label;
    }
    
    private Button createStyledButton(String text, String tooltip, 
                                    javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(handler);
        button.getStyleClass().add("styled-button");
        return button;
    }
    
    private void setupValidation() {
        // Email validation
        dispatcherEmailField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateEmail();
            }
        });
        
        // Phone validation
        dispatcherPhoneField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validatePhone(dispatcherPhoneField);
            }
        });
        
        dispatcherFaxField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validatePhone(dispatcherFaxField);
            }
        });
        
        // DOT validation
        companyDOTField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateDOT();
            }
        });
        
        // MC validation
        companyMCField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateMC();
            }
        });
    }
    
    private void validateField(String fieldId, String value, boolean required) {
        if (required && (value == null || value.trim().isEmpty())) {
            validationErrors.put(fieldId, "This field is required");
        } else {
            validationErrors.remove(fieldId);
        }
        
        updateFormValidity();
    }
    
    private void validateEmail() {
        String email = dispatcherEmailField.getText();
        Label validationLabel = validationLabels.get(dispatcherEmailField);
        
        if (email != null && !email.isEmpty()) {
            if (EMAIL_PATTERN.matcher(email).matches()) {
                validationLabel.setText("✓");
                validationLabel.setTextFill(Color.GREEN);
                validationErrors.remove("Email");
            } else {
                validationLabel.setText("Invalid email format");
                validationLabel.setTextFill(Color.RED);
                validationErrors.put("Email", "Invalid email format");
            }
        } else {
            validationLabel.setText("");
        }
        
        updateFormValidity();
    }
    
    private void validatePhone(TextField phoneField) {
        String phone = phoneField.getText();
        Label validationLabel = validationLabels.get(phoneField);
        
        if (phone != null && !phone.isEmpty()) {
            if (PHONE_PATTERN.matcher(phone).matches()) {
                if (validationLabel != null) {
                    validationLabel.setText("✓");
                    validationLabel.setTextFill(Color.GREEN);
                }
                validationErrors.remove(phoneField.getId());
            } else {
                if (validationLabel != null) {
                    validationLabel.setText("Format: 000-000-0000");
                    validationLabel.setTextFill(Color.RED);
                }
                validationErrors.put(phoneField.getId(), "Invalid phone format");
            }
        } else if (validationLabel != null) {
            validationLabel.setText("");
        }
        
        updateFormValidity();
    }
    
    private void validateDOT() {
        String dot = companyDOTField.getText();
        Label validationLabel = validationLabels.get(companyDOTField);
        
        if (dot != null && !dot.isEmpty()) {
            if (dot.matches("^\\d{7}$")) {
                validationLabel.setText("✓");
                validationLabel.setTextFill(Color.GREEN);
                validationErrors.remove("DOT");
            } else {
                validationLabel.setText("Must be 7 digits");
                validationLabel.setTextFill(Color.RED);
                validationErrors.put("DOT", "DOT must be 7 digits");
            }
        } else {
            validationLabel.setText("");
        }
        
        updateFormValidity();
    }
    
    private void validateMC() {
        String mc = companyMCField.getText();
        Label validationLabel = validationLabels.get(companyMCField);
        
        if (mc != null && !mc.isEmpty()) {
            if (mc.matches("^\\d{6}$")) {
                validationLabel.setText("✓");
                validationLabel.setTextFill(Color.GREEN);
                validationErrors.remove("MC");
            } else {
                validationLabel.setText("Must be 6 digits");
                validationLabel.setTextFill(Color.RED);
                validationErrors.put("MC", "MC must be 6 digits");
            }
        } else {
            validationLabel.setText("");
        }
        
        updateFormValidity();
    }
    
    private void updateFormValidity() {
        formValid.set(validationErrors.isEmpty());
    }
    
    private void setupKeyboardShortcuts() {
        getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
            () -> {
                if (saveButton != null && !saveButton.isDisabled()) {
                    saveButton.fire();
                }
            }
        );
    }
    
    private void setupAutoSave() {
        autoSaveTimeline = new Timeline(new KeyFrame(
            Duration.minutes(5),
            e -> {
                if (hasChanges.get() && formValid.get()) {
                    performAutoSave();
                }
            }
        ));
        autoSaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autoSaveTimeline.play();
    }
    
    private void performAutoSave() {
        Platform.runLater(() -> {
            statusLabel.setText("Auto-saving...");
            saveProgress.setVisible(true);
            
            CompletableFuture.runAsync(() -> {
                saveSettings();
            }).thenRun(() -> {
                Platform.runLater(() -> {
                    saveProgress.setVisible(false);
                    statusLabel.setText("Auto-saved at " + 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                });
            });
        });
    }
    
    private void loadSettings() {
        logger.info("Loading dispatcher settings");
        
        DispatcherSettings settings = DispatcherSettings.getInstance();
        
        // Dispatcher info
        dispatcherNameField.setText(settings.getDispatcherName());
        dispatcherPhoneField.setText(settings.getDispatcherPhone());
        dispatcherEmailField.setText(settings.getDispatcherEmail());
        dispatcherFaxField.setText(settings.getDispatcherFax());
        
        // Company info
        companyNameField.setText(settings.getCompanyName());
        companyDOTField.setText(settings.getCompanyDOT());
        companyMCField.setText(settings.getCompanyMC());
        logoPathField.setText(settings.getCompanyLogoPath());
        
        // Policy
        policyTextArea.setText(settings.getPickupDeliveryPolicy());
        
        // Advanced settings
        refreshIntervalSpinner.getValueFactory().setValue(settings.getAutoRefreshInterval());
        timezoneCombo.setValue(settings.getTimezone());
        defaultViewCombo.setValue(settings.getDefaultView());
        
        // Update logo preview
        if (!settings.getCompanyLogoPath().isEmpty()) {
            updateLogoPreview(settings.getCompanyLogoPath());
        }
        
        hasChanges.set(false);
    }
    
    private void saveSettings() {
        logger.info("Saving dispatcher settings");
        
        DispatcherSettings settings = DispatcherSettings.getInstance();
        
        // Dispatcher info
        settings.setDispatcherName(dispatcherNameField.getText());
        settings.setDispatcherPhone(dispatcherPhoneField.getText());
        settings.setDispatcherEmail(dispatcherEmailField.getText());
        settings.setDispatcherFax(dispatcherFaxField.getText());
        
        // Company info
        settings.setCompanyName(companyNameField.getText());
        settings.setCompanyDOT(companyDOTField.getText());
        settings.setCompanyMC(companyMCField.getText());
        settings.setCompanyLogoPath(logoPathField.getText());
        
        // Policy
        settings.setPickupDeliveryPolicy(policyTextArea.getText());
        
        // Advanced settings
        settings.setAutoRefreshInterval(refreshIntervalSpinner.getValue());
        settings.setTimezone(timezoneCombo.getValue());
        settings.setDefaultView(defaultViewCombo.getValue());
        
        // Save to preferences
        settings.saveToPreferences();
        
        hasChanges.set(false);
    }
    
    private boolean validateAndSave() {
        if (!formValid.get()) {
            showValidationErrors();
            return false;
        }
        
        try {
            saveSettings();
            return true;
        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            showErrorAlert("Save Failed", "Failed to save settings: " + e.getMessage());
            return false;
        }
    }
    
    private void showValidationErrors() {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Validation Errors");
        alert.setHeaderText("Please correct the following errors:");
        alert.setContentText(String.join("\n", validationErrors.values()));
        alert.showAndWait();
    }
    
    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    // Action handlers
    private void browseLogo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Company Logo");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            logoPathField.setText(file.getAbsolutePath());
            updateLogoPreview(file.getAbsolutePath());
            hasChanges.set(true);
        }
    }
    
    private void updateLogoPreview(String path) {
        try {
            Image image = new Image("file:" + path);
            logoPreview.setImage(image);
        } catch (Exception e) {
            logger.error("Failed to load logo preview", e);
        }
    }
    
    private void clearLogo() {
        logoPathField.clear();
        logoPreview.setImage(null);
        hasChanges.set(true);
    }
    
    private void importConfiguration() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Configuration");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );
        
        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try {
                DispatcherSettings.getInstance().importFromXML(file);
                loadSettings();
                statusLabel.setText("Configuration imported successfully");
            } catch (Exception e) {
                logger.error("Failed to import configuration", e);
                showErrorAlert("Import Failed", "Failed to import configuration: " + e.getMessage());
            }
        }
    }
    
    private void exportConfiguration() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Configuration");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );
        fileChooser.setInitialFileName("dispatcher_config_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xml");
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                DispatcherSettings.getInstance().exportToXML(file);
                statusLabel.setText("Configuration exported successfully");
            } catch (Exception e) {
                logger.error("Failed to export configuration", e);
                showErrorAlert("Export Failed", "Failed to export configuration: " + e.getMessage());
            }
        }
    }
    
    private void showTemplateDialog() {
        // TODO: Implement template selection dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Templates");
        alert.setHeaderText(null);
        alert.setContentText("Template functionality coming soon!");
        alert.showAndWait();
    }
    
    private void revertChanges() {
        loadSettings();
        statusLabel.setText("Changes reverted");
    }
    
    // Cleanup
    public void cleanup() {
        if (autoSaveTimeline != null) {
            autoSaveTimeline.stop();
        }
    }
    
    // Inner classes
    private static class PolicyTemplate {
        private final String name;
        private final String content;
        
        public PolicyTemplate(String name, String content) {
            this.name = name;
            this.content = content;
        }
        
        public String getName() { return name; }
        public String getContent() { return content; }
    }
    
    private static class PolicyDocument {
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty type = new SimpleStringProperty();
        private final SimpleStringProperty lastUpdated = new SimpleStringProperty();
        public PolicyDocument() {}
        public PolicyDocument(String name, String type, String updated) {
            this.name.set(name);
            this.type.set(type);
            this.lastUpdated.set(updated);
        }
        public StringProperty nameProperty() { return name; }
        public StringProperty typeProperty() { return type; }
        public StringProperty lastUpdatedProperty() { return lastUpdated; }
    }
    
    private static class ActivityEntry {
        private final SimpleStringProperty time;
        private final SimpleStringProperty user;
        private final SimpleStringProperty action;
        
        public ActivityEntry(String time, String user, String action) {
            this.time = new SimpleStringProperty(time);
            this.user = new SimpleStringProperty(user);
            this.action = new SimpleStringProperty(action);
        }
        
        public SimpleStringProperty timeProperty() { return time; }
        public SimpleStringProperty userProperty() { return user; }
        public SimpleStringProperty actionProperty() { return action; }
    }
    
    // Template methods
    private String getStandardPolicyTemplate() {
        return DispatcherSettings.getDefaultPolicy();
    }
    
    private String getRefrigeratedPolicyTemplate() {
        return getStandardPolicyTemplate() + "\n\n" +
            "REFRIGERATED LOAD ADDENDUM:\n" +
            "- Maintain continuous temperature monitoring\n" +
            "- Document temperature readings every 2 hours\n" +
            "- Immediate notification of any temperature deviations";
    }
    
    private String getHazmatPolicyTemplate() {
        return getStandardPolicyTemplate() + "\n\n" +
            "HAZMAT LOAD ADDENDUM:\n" +
            "- Driver must have valid HAZMAT endorsement\n" +
            "- Proper placarding required at all times\n" +
            "- Emergency response information must be readily accessible";
    }
    
    private String getExpeditedPolicyTemplate() {
        return getStandardPolicyTemplate() + "\n\n" +
            "EXPEDITED LOAD ADDENDUM:\n" +
            "- No unauthorized stops permitted\n" +
            "- Direct routing required\n" +
            "- Hourly check-ins with dispatch";
    }
    
    // Stub methods for actions
    private void filterSettings(String searchText) {
        // TODO: Implement settings search/filter
    }
    
    private void clearFilter() {
        // TODO: Clear settings filter
    }
    
    private void uploadSignature(BorderPane preview) {
        // TODO: Implement signature upload
    }
    
    private void openSignaturePad(BorderPane preview) {
        // TODO: Implement signature drawing pad
    }
    
    private void clearSignature(BorderPane preview) {
        // TODO: Clear signature
    }
    
    private void addNewPolicy() {
        // TODO: Add new policy dialog
    }
    
    private void importPolicy() {
        // TODO: Import policy from file
    }
    
    private void previewPolicy() {
        // TODO: Show policy preview
    }
    
    private void testEmailConfiguration() {
        // TODO: Test email settings
    }
    
    private void testSMSConfiguration() {
        // TODO: Test SMS settings
    }
    
    private void clearCache() {
        // TODO: Clear application cache
    }
    
    private void generateAPIKey() {
        // TODO: Generate API key
    }
    
    private void browseBackupLocation(TextField field) {
        // TODO: Browse for backup directory
    }
    
    private void performBackup() {
        // TODO: Perform manual backup
    }
    
    private void restoreFromBackup() {
        // TODO: Restore from backup
    }
    
    private void optimizeDatabase() {
        // TODO: Optimize database
    }
    
    private void viewFullActivityLog() {
        // TODO: Show full activity log
    }
    
    private List<String> generateHours() {
        List<String> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(String.format("%02d:00", h));
        }
        return hours;
    }
}