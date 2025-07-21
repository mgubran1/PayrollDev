package com.company.payroll.config;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;
import com.company.payroll.loads.EnterpriseDataCacheManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Document Manager Settings Dialog - Professional configuration interface
 * for document storage paths and folder organization settings.
 */
public class DocumentManagerSettingsDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(DocumentManagerSettingsDialog.class);
    
    // UI Components
    private TextField maintenancePathField;
    private TextField expensePathField;
    private TextField loadsPathField;
    private TextField mergedLoadsPathField;
    private CheckBox autoCreateFoldersCheckBox;
    private CheckBox sanitizeFilenamesCheckBox;
    private Button maintenanceBrowseButton;
    private Button expenseBrowseButton;
    private Button loadsBrowseButton;
    private Button mergedLoadsBrowseButton;
    private Button testMaintenanceButton;
    private Button testExpenseButton;
    private Button testLoadsButton;
    private Button testMergedLoadsButton;
    private Button resetButton;
    private Button refreshCachesButton;
    
    // Preview components
    private TextArea maintenancePreviewArea;
    private TextArea expensePreviewArea;
    private TextArea loadsPreviewArea;
    
    public DocumentManagerSettingsDialog(Stage owner) {
        setTitle("Document Manager Settings");
        setHeaderText("Configure Document Storage and Organization");
        
        // Set dialog properties
        setResizable(true);
        getDialogPane().setMinWidth(750);
        getDialogPane().setMinHeight(750);
        getDialogPane().setPrefWidth(750);
        getDialogPane().setPrefHeight(750);
        getDialogPane().setMaxWidth(900);
        getDialogPane().setMaxHeight(900);
        initOwner(owner);
        
        // Create content with scroll pane
        VBox content = createContent();
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportWidth(650);
        scrollPane.setPrefViewportHeight(600);
        scrollPane.setMaxHeight(700);
        
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
        content.setPadding(new Insets(15));
        content.setPrefWidth(650);
        content.setPrefHeight(600);
        content.setMaxHeight(700);
        
        // Title
        Label titleLabel = new Label("Document Manager Configuration");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        // Storage Paths Section
        VBox storageSection = createStoragePathsSection();
        
        // Settings Section
        VBox settingsSection = createSettingsSection();
        
        // Folder Structure Preview Section
        VBox previewSection = createPreviewSection();
        
        // Action Buttons
        HBox actionButtons = createActionButtons();
        
        content.getChildren().addAll(
            titleLabel,
            new Separator(),
            storageSection,
            new Separator(),
            settingsSection,
            new Separator(),
            previewSection,
            new Separator(),
            actionButtons
        );
        
        return content;
    }
    
    /**
     * Create storage paths configuration section
     */
    private VBox createStoragePathsSection() {
        VBox section = new VBox(12);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📁 Storage Paths");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        // Maintenance Path
        VBox maintenancePathBox = new VBox(3);
        Label maintenanceLabel = new Label("Maintenance Documents Path:");
        maintenancePathField = new TextField();
        maintenancePathField.setPromptText("Enter path for maintenance documents");
        maintenancePathField.setPrefWidth(400);
        
        HBox maintenanceButtonBox = new HBox(8);
        maintenanceBrowseButton = ModernButtonStyles.createSecondaryButton("Browse");
        testMaintenanceButton = ModernButtonStyles.createInfoButton("Test Path");
        maintenanceButtonBox.getChildren().addAll(maintenanceBrowseButton, testMaintenanceButton);
        
        maintenancePathBox.getChildren().addAll(maintenanceLabel, maintenancePathField, maintenanceButtonBox);
        
        // Expense Path
        VBox expensePathBox = new VBox(3);
        Label expenseLabel = new Label("Company Expense Documents Path:");
        expensePathField = new TextField();
        expensePathField.setPromptText("Enter path for expense documents");
        expensePathField.setPrefWidth(400);
        
        HBox expenseButtonBox = new HBox(8);
        expenseBrowseButton = ModernButtonStyles.createSecondaryButton("Browse");
        testExpenseButton = ModernButtonStyles.createInfoButton("Test Path");
        expenseButtonBox.getChildren().addAll(expenseBrowseButton, testExpenseButton);
        
        expensePathBox.getChildren().addAll(expenseLabel, expensePathField, expenseButtonBox);
        
        // Loads Path
        VBox loadsPathBox = new VBox(3);
        Label loadsLabel = new Label("Loads Documents Path:");
        loadsPathField = new TextField();
        loadsPathField.setPromptText("Enter path for loads documents");
        loadsPathField.setPrefWidth(400);
        
        HBox loadsButtonBox = new HBox(8);
        loadsBrowseButton = ModernButtonStyles.createSecondaryButton("Browse");
        testLoadsButton = ModernButtonStyles.createInfoButton("Test Path");
        loadsButtonBox.getChildren().addAll(loadsBrowseButton, testLoadsButton);
        
        loadsPathBox.getChildren().addAll(loadsLabel, loadsPathField, loadsButtonBox);
        
        // Merged Loads Path
        VBox mergedLoadsPathBox = new VBox(3);
        Label mergedLoadsLabel = new Label("Merged Load Documents Path:");
        mergedLoadsPathField = new TextField();
        mergedLoadsPathField.setPromptText("Enter path for merged load documents");
        mergedLoadsPathField.setPrefWidth(400);
        
        HBox mergedLoadsButtonBox = new HBox(8);
        mergedLoadsBrowseButton = ModernButtonStyles.createSecondaryButton("Browse");
        testMergedLoadsButton = ModernButtonStyles.createInfoButton("Test Path");
        mergedLoadsButtonBox.getChildren().addAll(mergedLoadsBrowseButton, testMergedLoadsButton);
        
        mergedLoadsPathBox.getChildren().addAll(mergedLoadsLabel, mergedLoadsPathField, mergedLoadsButtonBox);
        
        section.getChildren().addAll(sectionTitle, maintenancePathBox, expensePathBox, loadsPathBox, mergedLoadsPathBox);
        
        // Add button handlers
        setupButtonHandlers();
        
        // Add listeners to update preview when paths change
        maintenancePathField.textProperty().addListener((obs, oldVal, newVal) -> updateMaintenancePreview());
        expensePathField.textProperty().addListener((obs, oldVal, newVal) -> updateExpensePreview());
        loadsPathField.textProperty().addListener((obs, oldVal, newVal) -> updateLoadsPreview());
        
        return section;
    }
    
    /**
     * Create settings configuration section
     */
    private VBox createSettingsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("⚙️ Settings");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        // Auto-create folders setting
        autoCreateFoldersCheckBox = new CheckBox("Automatically create folder structure");
        autoCreateFoldersCheckBox.setTooltip(new Tooltip("Create folders automatically when uploading documents"));
        
        // Sanitize filenames setting
        sanitizeFilenamesCheckBox = new CheckBox("Sanitize filenames for safe storage");
        sanitizeFilenamesCheckBox.setTooltip(new Tooltip("Remove invalid characters from filenames"));
        
        section.getChildren().addAll(sectionTitle, autoCreateFoldersCheckBox, sanitizeFilenamesCheckBox);
        
        return section;
    }
    
    /**
     * Create folder structure preview section
     */
    private VBox createPreviewSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📋 Folder Structure Preview");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        // Maintenance structure preview
        VBox maintenancePreview = new VBox(3);
        Label maintenancePreviewLabel = new Label("Maintenance Documents:");
        maintenancePreviewArea = new TextArea();
        maintenancePreviewArea.setPrefRowCount(3);
        maintenancePreviewArea.setPrefHeight(70);
        maintenancePreviewArea.setEditable(false);
        maintenancePreview.getChildren().addAll(maintenancePreviewLabel, maintenancePreviewArea);
        
        // Expense structure preview
        VBox expensePreview = new VBox(3);
        Label expensePreviewLabel = new Label("Company Expense Documents:");
        expensePreviewArea = new TextArea();
        expensePreviewArea.setPrefRowCount(3);
        expensePreviewArea.setPrefHeight(70);
        expensePreviewArea.setEditable(false);
        expensePreview.getChildren().addAll(expensePreviewLabel, expensePreviewArea);
        
        // Loads structure preview
        VBox loadsPreview = new VBox(3);
        Label loadsPreviewLabel = new Label("Loads Documents:");
        loadsPreviewArea = new TextArea();
        loadsPreviewArea.setPrefRowCount(5);
        loadsPreviewArea.setPrefHeight(100);
        loadsPreviewArea.setEditable(false);
        loadsPreview.getChildren().addAll(loadsPreviewLabel, loadsPreviewArea);
        
        section.getChildren().addAll(sectionTitle, maintenancePreview, expensePreview, loadsPreview);
        
        return section;
    }
    
    /**
     * Create action buttons
     */
    private HBox createActionButtons() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(5, 0, 5, 0));
        
        resetButton = ModernButtonStyles.createWarningButton("Reset to Defaults");
        resetButton.setOnAction(e -> resetToDefaults());

        refreshCachesButton = ModernButtonStyles.createInfoButton("Refresh Data");
        refreshCachesButton.setOnAction(e -> {
            refreshCachesButton.setDisable(true);
            EnterpriseDataCacheManager.getInstance().invalidateAllCaches()
                    .thenRun(() -> Platform.runLater(() -> {
                        refreshCachesButton.setDisable(false);
                        showAlert(Alert.AlertType.INFORMATION, "Data Refreshed",
                                "All cached data has been refreshed.");
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            refreshCachesButton.setDisable(false);
                            showAlert(Alert.AlertType.ERROR, "Refresh Failed",
                                    "Failed to refresh data: " + ex.getMessage());
                        });
                        return null;
                    });
        });

        buttonBox.getChildren().addAll(refreshCachesButton, resetButton);
        
        return buttonBox;
    }
    
    /**
     * Setup button event handlers
     */
    private void setupButtonHandlers() {
        // Maintenance path browse
        maintenanceBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Maintenance Documents Directory");
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
            
            File selected = chooser.showDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                maintenancePathField.setText(selected.getAbsolutePath());
            }
        });
        
        // Expense path browse
        expenseBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Expense Documents Directory");
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
            
            File selected = chooser.showDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                expensePathField.setText(selected.getAbsolutePath());
            }
        });
        
        // Test maintenance path
        testMaintenanceButton.setOnAction(e -> testPath(maintenancePathField.getText(), "Maintenance"));
        
        // Test expense path
        testExpenseButton.setOnAction(e -> testPath(expensePathField.getText(), "Expense"));
        
        // Loads path browse
        loadsBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Loads Documents Directory");
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
            
            File selected = chooser.showDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                loadsPathField.setText(selected.getAbsolutePath());
            }
        });
        
        // Merged loads path browse
        mergedLoadsBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Merged Load Documents Directory");
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
            
            File selected = chooser.showDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                mergedLoadsPathField.setText(selected.getAbsolutePath());
            }
        });
        
        // Test loads path
        testLoadsButton.setOnAction(e -> testPath(loadsPathField.getText(), "Loads"));
        
        // Test merged loads path
        testMergedLoadsButton.setOnAction(e -> testPath(mergedLoadsPathField.getText(), "Merged Loads"));
    }
    
    /**
     * Test if a path is valid and writable
     */
    private void testPath(String path, String pathType) {
        if (path == null || path.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Path", 
                     pathType + " path cannot be empty.");
            return;
        }
        
        try {
            Path testPath = Paths.get(path);
            
            if (!Files.exists(testPath)) {
                // Try to create the directory
                Files.createDirectories(testPath);
                showAlert(Alert.AlertType.INFORMATION, "Path Test Successful", 
                         pathType + " path created successfully: " + path);
            } else if (Files.isDirectory(testPath) && Files.isWritable(testPath)) {
                showAlert(Alert.AlertType.INFORMATION, "Path Test Successful", 
                         pathType + " path is valid and writable: " + path);
            } else {
                showAlert(Alert.AlertType.ERROR, "Path Test Failed", 
                         pathType + " path is not writable: " + path);
            }
        } catch (Exception ex) {
            logger.error("Path test failed for " + pathType, ex);
            showAlert(Alert.AlertType.ERROR, "Path Test Failed", 
                     "Failed to test " + pathType + " path: " + ex.getMessage());
        }
    }
    
    /**
     * Load current settings into UI
     */
    private void loadCurrentSettings() {
        maintenancePathField.setText(DocumentManagerConfig.getMaintenanceStoragePath());
        expensePathField.setText(DocumentManagerConfig.getExpenseStoragePath());
        loadsPathField.setText(DocumentManagerConfig.getLoadsStoragePath());
        mergedLoadsPathField.setText(DocumentManagerConfig.getMergedLoadsPath());
        autoCreateFoldersCheckBox.setSelected(DocumentManagerConfig.isAutoCreateFolders());
        sanitizeFilenamesCheckBox.setSelected(DocumentManagerConfig.isSanitizeFilenames());
        
        // Update previews with current settings
        updateMaintenancePreview();
        updateExpensePreview();
        updateLoadsPreview();
    }
    
    /**
     * Save settings from UI
     */
    private void saveSettings() {
        try {
            // Validate paths
            if (maintenancePathField.getText() != null && !maintenancePathField.getText().trim().isEmpty()) {
                DocumentManagerConfig.setMaintenanceStoragePath(maintenancePathField.getText().trim());
            }
            
            if (expensePathField.getText() != null && !expensePathField.getText().trim().isEmpty()) {
                DocumentManagerConfig.setExpenseStoragePath(expensePathField.getText().trim());
            }
            
            if (loadsPathField.getText() != null && !loadsPathField.getText().trim().isEmpty()) {
                DocumentManagerConfig.setLoadsStoragePath(loadsPathField.getText().trim());
            }
            
            if (mergedLoadsPathField.getText() != null && !mergedLoadsPathField.getText().trim().isEmpty()) {
                DocumentManagerConfig.setMergedLoadsPath(mergedLoadsPathField.getText().trim());
            }
            
            DocumentManagerConfig.setAutoCreateFolders(autoCreateFoldersCheckBox.isSelected());
            DocumentManagerConfig.setSanitizeFilenames(sanitizeFilenamesCheckBox.isSelected());
            
            logger.info("Document manager settings saved successfully");
            
        } catch (Exception e) {
            logger.error("Failed to save document manager settings", e);
            showAlert(Alert.AlertType.ERROR, "Save Failed", 
                     "Failed to save settings: " + e.getMessage());
        }
    }
    
    /**
     * Reset to default settings
     */
    private void resetToDefaults() {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Reset to Defaults");
        confirmDialog.setHeaderText("Reset Document Manager Settings");
        confirmDialog.setContentText("Are you sure you want to reset all settings to their default values?");
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                DocumentManagerConfig.resetToDefaults();
                loadCurrentSettings();
                
                showAlert(Alert.AlertType.INFORMATION, "Settings Reset", 
                         "Document manager settings have been reset to defaults.");
            }
        });
    }
    
    /**
     * Show alert dialog
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
    
    /**
     * Update maintenance documents preview based on current path
     */
    private void updateMaintenancePreview() {
        String path = maintenancePathField.getText();
        if (path == null || path.trim().isEmpty()) {
            path = "maintenance_documents";
        }
        
        String preview = path + "/\n" +
            "├── Truck/\n" +
            "│   ├── 101/\n" +
            "│   │   ├── INV-1001_Truck_Oil_Change.pdf\n" +
            "│   │   └── INV-1002_Truck_Brake_Service.pdf\n" +
            "│   └── 102/\n" +
            "│       └── INV-1003_Truck_Tire_Replacement.pdf\n" +
            "└── Trailer/\n" +
            "    └── TR-201/\n" +
            "        └── INV-2001_Trailer_Inspection.pdf";
        
        maintenancePreviewArea.setText(preview);
    }
    
    /**
     * Update expense documents preview based on current path
     */
    private void updateExpensePreview() {
        String path = expensePathField.getText();
        if (path == null || path.trim().isEmpty()) {
            path = "expense_documents";
        }
        
        String preview = path + "/\n" +
            "├── Fuel/\n" +
            "│   ├── Operations/\n" +
            "│   │   ├── REC-001_Fuel_Operations.pdf\n" +
            "│   │   └── REC-002_Fuel_Operations.pdf\n" +
            "│   └── Maintenance/\n" +
            "│       └── REC-003_Fuel_Maintenance.pdf\n" +
            "└── Insurance/\n" +
            "    └── Administrative/\n" +
            "        └── REC-004_Insurance_Administrative.pdf";
        
        expensePreviewArea.setText(preview);
    }
    
    /**
     * Update loads structure preview
     */
    private void updateLoadsPreview() {
        String path = loadsPathField.getText();
        if (path == null || path.trim().isEmpty()) {
            path = "load_documents";
        }
        
        String mergedPath = mergedLoadsPathField.getText();
        if (mergedPath == null || mergedPath.trim().isEmpty()) {
            mergedPath = "MergedLoadDocuments";
        }
        
        int currentWeek = DocumentManagerConfig.getCurrentWeekNumber();
        
        String preview = "Regular Documents:\n" + path + "/\n" +
            "├── John_Smith/\n" +
            "│   ├── Week_" + (currentWeek-1) + "/\n" +
            "│   │   ├── RATE_CONFIRMATION_LOAD001_" + System.currentTimeMillis() + ".pdf\n" +
            "│   │   └── BOL_LOAD001_" + System.currentTimeMillis() + ".pdf\n" +
            "│   └── Week_" + currentWeek + "/\n" +
            "│       └── POD_LOAD002_" + System.currentTimeMillis() + ".pdf\n" +
            "└── Jane_Doe/\n" +
            "    └── Week_" + currentWeek + "/\n" +
            "        └── LUMPER_LOAD003_" + System.currentTimeMillis() + ".pdf\n\n" +
            "Merged Documents:\n" + mergedPath + "/\n" +
            "└── All_Loads/\n" +
            "    ├── Week_" + (currentWeek-1) + "/\n" +
            "    │   └── MergedLoads_Week" + (currentWeek-1) + "_20250720.pdf\n" +
            "    └── Week_" + currentWeek + "/\n" +
            "        └── MergedLoads_Week" + currentWeek + "_20250720.pdf";
        
        loadsPreviewArea.setText(preview);
    }
} 