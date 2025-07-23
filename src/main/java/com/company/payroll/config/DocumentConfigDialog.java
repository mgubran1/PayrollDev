package com.company.payroll.config;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.geometry.Pos;
import java.io.File;

/**
 * Dialog for configuring document storage settings
 */
public class DocumentConfigDialog extends Dialog<DocumentConfig> {
    
    private final DocumentConfig config;
    private TextField basePathField;
    private CheckBox autoSaveCheckbox;
    private ComboBox<DocumentConfig.FolderStructure> folderStructureCombo;
    private TextField fileNameTemplateField;
    
    public DocumentConfigDialog() {
        this.config = DocumentConfig.getInstance();
        
        setTitle("Document Configuration");
        setHeaderText("Configure Invoice Storage Settings");
        
        // Create the dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Base path configuration
        Label basePathLabel = new Label("Invoice Base Directory:");
        basePathField = new TextField(config.getInvoiceBasePath());
        basePathField.setPrefWidth(300);
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> browseForDirectory());
        
        HBox pathBox = new HBox(5);
        pathBox.getChildren().addAll(basePathField, browseButton);
        
        // Auto-save checkbox
        autoSaveCheckbox = new CheckBox("Enable automatic saving");
        autoSaveCheckbox.setSelected(config.isAutoSaveEnabled());
        
        // Folder structure selection
        Label folderStructureLabel = new Label("Folder Structure:");
        folderStructureCombo = new ComboBox<>();
        folderStructureCombo.getItems().addAll(DocumentConfig.FolderStructure.values());
        folderStructureCombo.setValue(config.getFolderStructure());
        folderStructureCombo.setConverter(new javafx.util.StringConverter<DocumentConfig.FolderStructure>() {
            @Override
            public String toString(DocumentConfig.FolderStructure structure) {
                return structure != null ? structure.getDescription() : "";
            }
            
            @Override
            public DocumentConfig.FolderStructure fromString(String string) {
                return null;
            }
        });
        
        // File name template
        Label fileNameLabel = new Label("File Name Template:");
        fileNameTemplateField = new TextField(config.getFileNameTemplate());
        fileNameTemplateField.setPrefWidth(400);
        
        Label templateHelpLabel = new Label("Available variables: {PO}, {DRIVER}, {DATE}, {WEEK}, {YEAR}, {MONTH}");
        templateHelpLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");
        
        // Preview section
        Label previewLabel = new Label("Preview:");
        previewLabel.setStyle("-fx-font-weight: bold;");
        
        Label previewPath = new Label();
        previewPath.setStyle("-fx-font-family: monospace; -fx-background-color: #f0f0f0; -fx-padding: 5px;");
        updatePreview(previewPath);
        
        // Update preview when settings change
        basePathField.textProperty().addListener((obs, old, val) -> updatePreview(previewPath));
        folderStructureCombo.valueProperty().addListener((obs, old, val) -> updatePreview(previewPath));
        fileNameTemplateField.textProperty().addListener((obs, old, val) -> updatePreview(previewPath));
        
        // Layout
        grid.add(basePathLabel, 0, 0);
        grid.add(pathBox, 1, 0, 2, 1);
        
        grid.add(autoSaveCheckbox, 0, 1, 3, 1);
        
        grid.add(folderStructureLabel, 0, 2);
        grid.add(folderStructureCombo, 1, 2, 2, 1);
        
        grid.add(fileNameLabel, 0, 3);
        grid.add(fileNameTemplateField, 1, 3, 2, 1);
        grid.add(templateHelpLabel, 1, 4, 2, 1);
        
        grid.add(new Separator(), 0, 5, 3, 1);
        
        grid.add(previewLabel, 0, 6);
        grid.add(previewPath, 0, 7, 3, 1);
        
        getDialogPane().setContent(grid);
        
        // Add buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Convert result when save button is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                // Update config with new values
                config.setInvoiceBasePath(basePathField.getText());
                config.setAutoSaveEnabled(autoSaveCheckbox.isSelected());
                config.setFolderStructure(folderStructureCombo.getValue());
                config.setFileNameTemplate(fileNameTemplateField.getText());
                
                // Save to file
                config.save();
                
                return config;
            }
            return null;
        });
        
        // Style the dialog
        getDialogPane().setMinWidth(600);
        initModality(Modality.APPLICATION_MODAL);
    }
    
    private void browseForDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Invoice Base Directory");
        
        File currentDir = new File(basePathField.getText());
        if (currentDir.exists() && currentDir.isDirectory()) {
            chooser.setInitialDirectory(currentDir);
        }
        
        File selectedDir = chooser.showDialog(getOwner());
        if (selectedDir != null) {
            basePathField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    private void updatePreview(Label previewLabel) {
        try {
            String basePath = basePathField.getText();
            DocumentConfig.FolderStructure structure = folderStructureCombo.getValue();
            String template = fileNameTemplateField.getText();
            
            // Create preview path
            String preview = basePath + File.separator;
            
            switch (structure) {
                case DRIVER_WEEK:
                    preview += "John_Smith" + File.separator + "Week 42" + File.separator;
                    break;
                case WEEK_DRIVER:
                    preview += "Week 42" + File.separator + "John_Smith" + File.separator;
                    break;
                case YEAR_MONTH_DRIVER:
                    preview += "2025" + File.separator + "JULY" + File.separator + "John_Smith" + File.separator;
                    break;
                case DRIVER_YEAR_MONTH:
                    preview += "John_Smith" + File.separator + "2025" + File.separator + "JULY" + File.separator;
                    break;
                case FLAT:
                    // No additional folders
                    break;
            }
            
            // Add filename
            String fileName = template
                .replace("{PO}", "123456")
                .replace("{DRIVER}", "John_Smith")
                .replace("{DATE}", "2025-07-22")
                .replace("{WEEK}", "42")
                .replace("{YEAR}", "2025")
                .replace("{MONTH}", "JULY");
            
            if (!fileName.endsWith(".pdf")) {
                fileName += ".pdf";
            }
            
            preview += fileName;
            previewLabel.setText(preview);
            
        } catch (Exception e) {
            previewLabel.setText("Invalid configuration");
        }
    }
} 