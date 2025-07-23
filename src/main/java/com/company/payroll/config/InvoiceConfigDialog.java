package com.company.payroll.config;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;

/**
 * Invoice Configuration Dialog - Professional interface for managing company information
 */
public class InvoiceConfigDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(InvoiceConfigDialog.class);
    
    // UI Components
    private TextField companyNameField;
    private TextField streetField;
    private TextField cityField;
    private TextField stateField;
    private TextField zipField;
    private TextField emailField;
    private TextField phoneField;
    private TextField faxField;
    private TextField mcField;
    private TextField invoicePrefixField;
    private TextField invoiceTermsField;
    private TextArea invoiceNotesArea;
    
    public InvoiceConfigDialog(Stage owner) {
        setTitle("Invoice Configuration");
        setHeaderText("Configure Company Information for Invoices");
        
        // Set dialog properties
        setResizable(true);
        getDialogPane().setMinWidth(600);
        getDialogPane().setMinHeight(650);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(650);
        initOwner(owner);
        
        // Add CSS class for styling
        getDialogPane().getStyleClass().add("invoice-config-dialog");
        
        // Create content
        VBox content = createContent();
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        getDialogPane().setContent(scrollPane);
        
        // Add buttons
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Handle OK button
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                saveSettings();
                return null;
            }
            return null;
        });
        
        // Initialize with current settings
        loadCurrentSettings();
    }
    
    /**
     * Create the main content layout
     */
    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Title
        Label titleLabel = new Label("Company Information");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        // Company Details Section
        VBox companySection = createCompanyDetailsSection();
        
        // Contact Information Section
        VBox contactSection = createContactSection();
        
        // Invoice Settings Section
        VBox invoiceSection = createInvoiceSettingsSection();
        
        // Preview Section
        VBox previewSection = createPreviewSection();
        
        content.getChildren().addAll(
            titleLabel,
            new Separator(),
            companySection,
            new Separator(),
            contactSection,
            new Separator(),
            invoiceSection,
            new Separator(),
            previewSection
        );
        
        return content;
    }
    
    /**
     * Create company details section
     */
    private VBox createCompanyDetailsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("🏢 Company Details");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        
        // Company Name
        Label nameLabel = new Label("Company Name:");
        companyNameField = new TextField();
        companyNameField.setPrefWidth(350);
        companyNameField.setPromptText("Enter company name");
        
        // Address fields
        Label streetLabel = new Label("Street Address:");
        streetField = new TextField();
        streetField.setPrefWidth(350);
        streetField.setPromptText("Enter street address");
        
        Label cityLabel = new Label("City:");
        cityField = new TextField();
        cityField.setPrefWidth(200);
        cityField.setPromptText("Enter city");
        
        Label stateLabel = new Label("State:");
        stateField = new TextField();
        stateField.setPrefWidth(60);
        stateField.setPromptText("ST");
        
        Label zipLabel = new Label("ZIP Code:");
        zipField = new TextField();
        zipField.setPrefWidth(100);
        zipField.setPromptText("12345");
        
        Label mcLabel = new Label("MC Number:");
        mcField = new TextField();
        mcField.setPrefWidth(150);
        mcField.setPromptText("MC-123456");
        
        // Add to grid
        grid.add(nameLabel, 0, 0);
        grid.add(companyNameField, 1, 0, 3, 1);
        
        grid.add(streetLabel, 0, 1);
        grid.add(streetField, 1, 1, 3, 1);
        
        grid.add(cityLabel, 0, 2);
        grid.add(cityField, 1, 2);
        grid.add(stateLabel, 2, 2);
        grid.add(stateField, 3, 2);
        
        grid.add(zipLabel, 0, 3);
        grid.add(zipField, 1, 3);
        grid.add(mcLabel, 2, 3);
        grid.add(mcField, 3, 3);
        
        section.getChildren().addAll(sectionTitle, grid);
        
        return section;
    }
    
    /**
     * Create contact information section
     */
    private VBox createContactSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📞 Contact Information");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        
        // Email
        Label emailLabel = new Label("Email:");
        emailField = new TextField();
        emailField.setPrefWidth(250);
        emailField.setPromptText("info@company.com");
        
        // Phone
        Label phoneLabel = new Label("Phone:");
        phoneField = new TextField();
        phoneField.setPrefWidth(150);
        phoneField.setPromptText("(555) 123-4567");
        
        // Fax
        Label faxLabel = new Label("Fax:");
        faxField = new TextField();
        faxField.setPrefWidth(150);
        faxField.setPromptText("(555) 123-4568");
        
        // Add to grid
        grid.add(emailLabel, 0, 0);
        grid.add(emailField, 1, 0, 2, 1);
        
        grid.add(phoneLabel, 0, 1);
        grid.add(phoneField, 1, 1);
        
        grid.add(faxLabel, 0, 2);
        grid.add(faxField, 1, 2);
        
        section.getChildren().addAll(sectionTitle, grid);
        
        return section;
    }
    
    /**
     * Create invoice settings section
     */
    private VBox createInvoiceSettingsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("Invoice Settings");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        
        // Invoice Prefix
        Label prefixLabel = new Label("Invoice Prefix:");
        invoicePrefixField = new TextField();
        invoicePrefixField.setPrefWidth(100);
        invoicePrefixField.setPromptText("INV");
        
        // Payment Terms
        Label termsLabel = new Label("Payment Terms:");
        invoiceTermsField = new TextField();
        invoiceTermsField.setPrefWidth(200);
        invoiceTermsField.setPromptText("Net 30");
        
        // Invoice Notes
        Label notesLabel = new Label("Invoice Notes:");
        invoiceNotesArea = new TextArea();
        invoiceNotesArea.setPrefRowCount(3);
        invoiceNotesArea.setPrefWidth(400);
        invoiceNotesArea.setPromptText("Additional notes to appear on invoices...");
        invoiceNotesArea.setWrapText(true);
        
        // Add to grid
        grid.add(prefixLabel, 0, 0);
        grid.add(invoicePrefixField, 1, 0);
        
        grid.add(termsLabel, 0, 1);
        grid.add(invoiceTermsField, 1, 1);
        
        grid.add(notesLabel, 0, 2);
        grid.add(invoiceNotesArea, 1, 2, 2, 1);
        
        section.getChildren().addAll(sectionTitle, grid);
        
        return section;
    }
    
    /**
     * Create preview section
     */
    private VBox createPreviewSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📋 Address Preview");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1976d2;");
        
        TextArea previewArea = new TextArea();
        previewArea.setPrefRowCount(5);
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-control-inner-background: white;");
        
        // Update preview when fields change
        Runnable updatePreview = () -> {
            String preview = companyNameField.getText() + "\n" +
                streetField.getText() + "\n" +
                cityField.getText() + ", " + stateField.getText() + " " + zipField.getText() + "\n" +
                "Phone: " + phoneField.getText() + " | Fax: " + faxField.getText() + "\n" +
                "Email: " + emailField.getText() + " | MC: " + mcField.getText();
            previewArea.setText(preview);
        };
        
        // Add listeners to all fields
        companyNameField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        streetField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        cityField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        stateField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        zipField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        emailField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        phoneField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        faxField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        mcField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        
        section.getChildren().addAll(sectionTitle, previewArea);
        
        // Initial preview update
        updatePreview.run();
        
        return section;
    }
    
    /**
     * Load current settings from configuration
     */
    private void loadCurrentSettings() {
        companyNameField.setText(InvoiceConfig.getCompanyName());
        streetField.setText(InvoiceConfig.getCompanyStreet());
        cityField.setText(InvoiceConfig.getCompanyCity());
        stateField.setText(InvoiceConfig.getCompanyState());
        zipField.setText(InvoiceConfig.getCompanyZip());
        emailField.setText(InvoiceConfig.getCompanyEmail());
        phoneField.setText(InvoiceConfig.getCompanyPhone());
        faxField.setText(InvoiceConfig.getCompanyFax());
        mcField.setText(InvoiceConfig.getCompanyMC());
        invoicePrefixField.setText(InvoiceConfig.getInvoicePrefix());
        invoiceTermsField.setText(InvoiceConfig.getInvoiceTerms());
        invoiceNotesArea.setText(InvoiceConfig.getInvoiceNotes());
    }
    
    /**
     * Save settings to configuration
     */
    private void saveSettings() {
        try {
            InvoiceConfig.setCompanyName(companyNameField.getText().trim());
            InvoiceConfig.setCompanyStreet(streetField.getText().trim());
            InvoiceConfig.setCompanyCity(cityField.getText().trim());
            InvoiceConfig.setCompanyState(stateField.getText().trim());
            InvoiceConfig.setCompanyZip(zipField.getText().trim());
            InvoiceConfig.setCompanyEmail(emailField.getText().trim());
            InvoiceConfig.setCompanyPhone(phoneField.getText().trim());
            InvoiceConfig.setCompanyFax(faxField.getText().trim());
            InvoiceConfig.setCompanyMC(mcField.getText().trim());
            InvoiceConfig.setInvoicePrefix(invoicePrefixField.getText().trim());
            InvoiceConfig.setInvoiceTerms(invoiceTermsField.getText().trim());
            InvoiceConfig.setInvoiceNotes(invoiceNotesArea.getText().trim());
            
            logger.info("Invoice configuration saved successfully");
            
            showAlert(Alert.AlertType.INFORMATION, "Settings Saved", 
                     "Invoice configuration has been saved successfully.");
            
        } catch (Exception e) {
            logger.error("Failed to save invoice configuration", e);
            showAlert(Alert.AlertType.ERROR, "Save Failed", 
                     "Failed to save settings: " + e.getMessage());
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
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }
}