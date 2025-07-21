package com.company.payroll.employees;

import com.company.payroll.config.DOTComplianceConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified Employee Document Manager
 * Provides comprehensive document management for employees with DOT compliance integration
 */
public class EmployeeDocumentManager {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeDocumentManager.class);
    private static final String EMPLOYEE_DOCS_PATH = "employee_documents";
    private static final String[] SUPPORTED_EXTENSIONS = {".pdf", ".jpg", ".jpeg", ".png"};
    
    private final DOTComplianceConfig complianceConfig;
    private final Stage ownerStage;
    
    public EmployeeDocumentManager(Stage ownerStage) {
        this.ownerStage = ownerStage;
        this.complianceConfig = DOTComplianceConfig.getInstance();
        createDocumentDirectories();
    }
    
    /**
     * Create necessary document directories
     */
    private void createDocumentDirectories() {
        try {
            Path basePath = Paths.get(EMPLOYEE_DOCS_PATH);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }
        } catch (IOException e) {
            logger.error("Failed to create document directories", e);
        }
    }
    
    /**
     * Show the Employee Document Manager dialog
     */
    public void showDocumentManager(Employee employee) {
        if (employee == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an employee to manage documents");
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Employee Document Manager - " + employee.getName());
        dialog.setHeaderText("Manage documents for " + employee.getName() + " (ID: " + employee.getId() + ")");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(800);
        content.setPrefHeight(600);
        
        // Compliance Status Section
        VBox complianceSection = createComplianceStatusSection(employee);
        
        // Document List Section
        VBox documentSection = createDocumentListSection(employee);
        
        // Action Buttons Section
        HBox actionButtons = createActionButtons(employee, documentSection);
        
        // Alerts Panel
        VBox alertsPanel = createAlertsPanel(employee);
        
        content.getChildren().addAll(complianceSection, documentSection, actionButtons, alertsPanel);
        VBox.setVgrow(documentSection, Priority.ALWAYS);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
    
    /**
     * Create compliance status section with status indicator and configure button
     */
    private VBox createComplianceStatusSection(Employee employee) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        // Compliance status indicator
        Circle statusIndicator = new Circle(8);
        boolean isCompliant = isEmployeeCompliant(employee);
        statusIndicator.setFill(isCompliant ? javafx.scene.paint.Color.GREEN : javafx.scene.paint.Color.RED);
        
        Label statusLabel = new Label(isCompliant ? "Compliant" : "Non-Compliant");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        // Configure button
        Button configureBtn = createStyledButton("⚙️ Configure", "#6c757d");
        configureBtn.setOnAction(e -> showComplianceConfiguration());
        
        headerBox.getChildren().addAll(statusIndicator, statusLabel, new Separator(Orientation.VERTICAL), configureBtn);
        
        // Document requirements summary
        Label summaryLabel = new Label("Required Documents: " + getRequiredDocumentsCount(employee) + 
                                     " | Uploaded: " + getUploadedDocumentsCount(employee) + 
                                     " | Missing: " + getMissingDocumentsCount(employee));
        summaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
        
        section.getChildren().addAll(headerBox, summaryLabel);
        return section;
    }
    
    /**
     * Create document list section with ListView
     */
    private VBox createDocumentListSection(Employee employee) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        
        Label titleLabel = new Label("Documents");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        
        ListView<DocumentItem> docListView = new ListView<>();
        docListView.setPrefHeight(300);
        docListView.setCellFactory(lv -> new DocumentListCell());
        
        // Load documents
        refreshDocumentList(docListView, employee);
        
        section.getChildren().addAll(titleLabel, docListView);
        VBox.setVgrow(docListView, Priority.ALWAYS);
        
        return section;
    }
    
    /**
     * Create action buttons
     */
    private HBox createActionButtons(Employee employee, VBox documentSection) {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button uploadBtn = createStyledButton("📤 Upload Document", "#28a745");
        Button viewBtn = createStyledButton("👁️ View Selected", "#17a2b8");
        Button mergeBtn = createStyledButton("🔗 Merge Selected", "#6f42c1");
        Button printBtn = createStyledButton("🖨️ Print Selected", "#fd7e14");
        Button deleteBtn = createStyledButton("🗑️ Delete Selected", "#dc3545");
        Button folderBtn = createStyledButton("📁 Open Folder", "#6c757d");
        
        ListView<DocumentItem> docListView = (ListView<DocumentItem>) documentSection.getChildren().get(1);
        
        uploadBtn.setOnAction(e -> uploadDocument(employee, docListView));
        viewBtn.setOnAction(e -> viewSelectedDocuments(employee, docListView));
        mergeBtn.setOnAction(e -> mergeSelectedDocuments(employee, docListView));
        printBtn.setOnAction(e -> printSelectedDocuments(employee, docListView));
        deleteBtn.setOnAction(e -> deleteSelectedDocuments(employee, docListView));
        folderBtn.setOnAction(e -> openEmployeeFolder(employee));
        
        buttonBox.getChildren().addAll(uploadBtn, viewBtn, mergeBtn, printBtn, deleteBtn, folderBtn);
        return buttonBox;
    }
    
    /**
     * Create alerts panel for missing/expired documents
     */
    private VBox createAlertsPanel(Employee employee) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #ffeaa7; -fx-border-radius: 8; -fx-border-width: 1;");
        section.setMinHeight(120);
        
        Label titleLabel = new Label("⚠️ Compliance Alerts");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #856404;");
        
        // Get alerts
        List<AlertItem> alerts = getComplianceAlertsEnhanced(employee);
        
        if (alerts.isEmpty()) {
            Label noAlertsLabel = new Label("✅ No compliance alerts");
            noAlertsLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic; -fx-font-size: 14px;");
            noAlertsLabel.setPadding(new Insets(10));
            section.getChildren().addAll(titleLabel, noAlertsLabel);
        } else {
            ListView<AlertItem> alertsList = new ListView<>();
            alertsList.setItems(FXCollections.observableArrayList(alerts));
            alertsList.setPrefHeight(120);
            alertsList.setCellFactory(lv -> new AlertListCell());
            alertsList.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            
            section.getChildren().addAll(titleLabel, alertsList);
            VBox.setVgrow(alertsList, Priority.ALWAYS);
        }
        
        return section;
    }
    
    /**
     * Upload document for employee
     */
    private void uploadDocument(Employee employee, ListView<DocumentItem> docListView) {
        // Show document type selection dialog
        List<String> requiredDocs = complianceConfig.getRequiredDocuments("employee");
        ChoiceDialog<String> typeDialog = new ChoiceDialog<>();
        typeDialog.setTitle("Select Document Type");
        typeDialog.setHeaderText("Select document type for " + employee.getName());
        typeDialog.setContentText("Document Type:");
        typeDialog.getItems().addAll(requiredDocs);
        typeDialog.getItems().addAll("Other");
        
        typeDialog.showAndWait().ifPresent(docType -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select " + docType + " for " + employee.getName());
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            
            File selectedFile = fileChooser.showOpenDialog(ownerStage);
            if (selectedFile != null) {
                try {
                    String fileName = saveDocument(employee, docType, selectedFile);
                    showAlert(Alert.AlertType.INFORMATION, "Upload Successful", 
                             "Document saved as: " + fileName);
                    refreshDocumentList(docListView, employee);
                } catch (IOException e) {
                    logger.error("Failed to upload document", e);
                    showAlert(Alert.AlertType.ERROR, "Upload Failed", 
                             "Failed to upload document: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Save document to employee's directory
     */
    private String saveDocument(Employee employee, String docType, File sourceFile) throws IOException {
        String driverName = employee.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
        Path employeeDir = Paths.get(EMPLOYEE_DOCS_PATH, driverName);
        Files.createDirectories(employeeDir);
        
        String safeDocType = docType.replaceAll("[^a-zA-Z0-9_\\- ]", "_");
        String extension = getFileExtension(sourceFile.getName());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        String fileName = driverName + "_" + safeDocType + "_" + timestamp + extension;
        Path destPath = employeeDir.resolve(fileName);
        
        Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }
    
    /**
     * View selected documents
     */
    private void viewSelectedDocuments(Employee employee, ListView<DocumentItem> docListView) {
        ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
        if (selectedDocs.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select documents to view");
            return;
        }
        
        for (DocumentItem doc : selectedDocs) {
            viewDocument(employee, doc.getFileName());
        }
    }
    
    /**
     * View a single document
     */
    private void viewDocument(Employee employee, String fileName) {
        try {
            String driverName = employee.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
            Path docPath = Paths.get(EMPLOYEE_DOCS_PATH, driverName, fileName);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(docPath.toFile());
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Cannot Open", 
                         "File is located at: " + docPath.toString());
            }
        } catch (Exception e) {
            logger.error("Failed to open document", e);
            showAlert(Alert.AlertType.ERROR, "Open Failed", 
                     "Failed to open document: " + e.getMessage());
        }
    }
    
    /**
     * Merge selected documents into a single PDF
     */
    private void mergeSelectedDocuments(Employee employee, ListView<DocumentItem> docListView) {
        ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
        if (selectedDocs.size() < 2) {
            showAlert(Alert.AlertType.INFORMATION, "Selection Required", 
                     "Please select multiple documents to merge");
            return;
        }
        
        // For now, just show a message - PDF merging would require additional libraries
        showAlert(Alert.AlertType.INFORMATION, "Merge Feature", 
                 "Document merging feature requires PDF library integration.\n" +
                 "Selected documents: " + selectedDocs.size());
    }
    
    /**
     * Print selected documents
     */
    private void printSelectedDocuments(Employee employee, ListView<DocumentItem> docListView) {
        ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
        if (selectedDocs.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select documents to print");
            return;
        }
        
        for (DocumentItem doc : selectedDocs) {
            try {
                Path docPath = Paths.get(EMPLOYEE_DOCS_PATH, String.valueOf(employee.getId()), doc.getFileName());
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().print(docPath.toFile());
                }
            } catch (Exception e) {
                logger.error("Failed to print document", e);
            }
        }
        
        showAlert(Alert.AlertType.INFORMATION, "Print Requested", 
                 "Print request sent for " + selectedDocs.size() + " document(s)");
    }
    
    /**
     * Delete selected documents
     */
    private void deleteSelectedDocuments(Employee employee, ListView<DocumentItem> docListView) {
        ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
        if (selectedDocs.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select documents to delete");
            return;
        }
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Deletion");
        confirmDialog.setHeaderText("Delete Documents");
        confirmDialog.setContentText("Are you sure you want to delete " + selectedDocs.size() + " document(s)?");
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int deletedCount = 0;
                for (DocumentItem doc : selectedDocs) {
                    try {
                        String driverName = employee.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
                        Path docPath = Paths.get(EMPLOYEE_DOCS_PATH, driverName, doc.getFileName());
                        Files.deleteIfExists(docPath);
                        deletedCount++;
                    } catch (IOException e) {
                        logger.error("Failed to delete document", e);
                    }
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Deletion Complete", 
                         "Successfully deleted " + deletedCount + " document(s)");
                refreshDocumentList(docListView, employee);
            }
        });
    }
    
    /**
     * Open employee's document folder
     */
    private void openEmployeeFolder(Employee employee) {
        try {
            String driverName = employee.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
            Path employeeDir = Paths.get(EMPLOYEE_DOCS_PATH, driverName);
            if (!Files.exists(employeeDir)) {
                Files.createDirectories(employeeDir);
            }
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(employeeDir.toFile());
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Folder Location", 
                         "Employee folder: " + employeeDir.toString());
            }
        } catch (Exception e) {
            logger.error("Failed to open employee folder", e);
            showAlert(Alert.AlertType.ERROR, "Open Failed", 
                     "Failed to open folder: " + e.getMessage());
        }
    }
    
    /**
     * Show compliance configuration dialog
     */
    private void showComplianceConfiguration() {
        // This would open the DOTComplianceConfigDialog
        // For now, show a simple message
        showAlert(Alert.AlertType.INFORMATION, "Configuration", 
                 "DOT Compliance Configuration can be accessed from the main configuration menu.");
    }
    
    /**
     * Refresh document list
     */
    private void refreshDocumentList(ListView<DocumentItem> docListView, Employee employee) {
        try {
            String driverName = employee.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
            Path employeeDir = Paths.get(EMPLOYEE_DOCS_PATH, driverName);
            List<DocumentItem> documents = new ArrayList<>();
            
            if (Files.exists(employeeDir)) {
                try (var stream = Files.list(employeeDir)) {
                    documents = stream
                        .filter(Files::isRegularFile)
                        .map(path -> new DocumentItem(path.getFileName().toString(), 
                                                    getDocumentType(path.getFileName().toString()),
                                                    getFileSize(path),
                                                    getLastModified(path)))
                        .sorted(Comparator.comparing(DocumentItem::getFileName))
                        .collect(Collectors.toList());
                }
            }
            
            docListView.setItems(FXCollections.observableArrayList(documents));
        } catch (IOException e) {
            logger.error("Failed to refresh document list", e);
            docListView.setItems(FXCollections.observableArrayList());
        }
    }
    
    /**
     * Check if employee is compliant
     */
    private boolean isEmployeeCompliant(Employee employee) {
        Set<String> availableDocs = getAvailableDocuments(employee);
        return complianceConfig.isCompliant("employee", availableDocs);
    }
    
    /**
     * Get available documents for employee
     */
    private Set<String> getAvailableDocuments(Employee employee) {
        Set<String> documents = new HashSet<>();
        try {
            String driverName = employee.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
            Path employeeDir = Paths.get(EMPLOYEE_DOCS_PATH, driverName);
            if (Files.exists(employeeDir)) {
                try (var stream = Files.list(employeeDir)) {
                    documents = stream
                        .filter(Files::isRegularFile)
                        .map(path -> getDocumentType(path.getFileName().toString()))
                        .collect(Collectors.toSet());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to get available documents", e);
        }
        return documents;
    }
    
    /**
     * Get compliance alerts for employee
     */
    private List<String> getComplianceAlerts(Employee employee) {
        List<String> alerts = new ArrayList<>();
        Set<String> availableDocs = getAvailableDocuments(employee);
        List<String> missingDocs = complianceConfig.getMissingDocuments("employee", availableDocs);
        
        for (String missingDoc : missingDocs) {
            alerts.add("Missing: " + missingDoc);
        }
        
        // Check for expired documents (CDL, Medical)
        checkExpiredDocuments(employee, alerts);
        
        return alerts;
    }
    
    /**
     * Get enhanced compliance alerts with icons and tooltips
     */
    private List<AlertItem> getComplianceAlertsEnhanced(Employee employee) {
        List<AlertItem> alerts = new ArrayList<>();
        Set<String> availableDocs = getAvailableDocuments(employee);
        List<String> missingDocs = complianceConfig.getMissingDocuments("employee", availableDocs);
        
        for (String missingDoc : missingDocs) {
            String icon = getAlertIcon(missingDoc);
            String tooltip = getAlertTooltip(missingDoc, employee);
            alerts.add(new AlertItem(icon, "Missing: " + missingDoc, tooltip));
        }
        
        // Check for expired documents (CDL, Medical)
        checkExpiredDocumentsEnhanced(employee, alerts);
        
        return alerts;
    }
    
    /**
     * Get appropriate icon for alert type
     */
    private String getAlertIcon(String documentType) {
        switch (documentType.toLowerCase()) {
            case "cdl license":
                return "🚗";
            case "medical certificate":
                return "🩺";
            case "drug test results":
            case "pre-employment drug test":
            case "random drug test":
            case "post-accident drug test":
                return "🧪";
            case "background check":
            case "psp report":
            case "mvr report":
                return "🔍";
            case "training certificates":
            case "hours of service training":
            case "defensive driving certificate":
                return "📚";
            case "safety training":
                return "🛡️";
            case "hazmat endorsement":
                return "☣️";
            case "employment application":
                return "📝";
            case "resume":
                return "📄";
            case "company policies acknowledgement":
                return "📋";
            case "driver's license verification":
                return "🆔";
            case "driver onboarding checklist":
                return "✅";
            default:
                return "⚠️";
        }
    }
    
    /**
     * Get tooltip text for alert
     */
    private String getAlertTooltip(String documentType, Employee employee) {
        switch (documentType.toLowerCase()) {
            case "cdl license":
                return "Driver " + employee.getName() + " has no valid CDL License on file";
            case "medical certificate":
                return "Driver " + employee.getName() + " has no valid Medical Certificate on file";
            case "drug test results":
                return "Driver " + employee.getName() + " has no Drug Test Results on file";
            case "background check":
                return "Driver " + employee.getName() + " has no Background Check on file";
            case "employment application":
                return "Driver " + employee.getName() + " has no Employment Application on file";
            case "driver onboarding checklist":
                return "Driver " + employee.getName() + " has no completed Onboarding Checklist";
            default:
                return "Driver " + employee.getName() + " is missing required document: " + documentType;
        }
    }
    
    /**
     * Check for expired documents
     */
    private void checkExpiredDocuments(Employee employee, List<String> alerts) {
        LocalDateTime now = LocalDateTime.now();
        
        if (employee.getCdlExpiry() != null && employee.getCdlExpiry().isBefore(now.toLocalDate())) {
            alerts.add("CDL License expired: " + employee.getCdlExpiry());
        }
        
        if (employee.getMedicalExpiry() != null && employee.getMedicalExpiry().isBefore(now.toLocalDate())) {
            alerts.add("Medical Certificate expired: " + employee.getMedicalExpiry());
        }
    }
    
    /**
     * Check for expired documents with enhanced alerts
     */
    private void checkExpiredDocumentsEnhanced(Employee employee, List<AlertItem> alerts) {
        LocalDateTime now = LocalDateTime.now();
        
        if (employee.getCdlExpiry() != null && employee.getCdlExpiry().isBefore(now.toLocalDate())) {
            String tooltip = "Driver " + employee.getName() + "'s CDL License expired on " + employee.getCdlExpiry();
            alerts.add(new AlertItem("🚗", "CDL License expired: " + employee.getCdlExpiry(), tooltip));
        }
        
        if (employee.getMedicalExpiry() != null && employee.getMedicalExpiry().isBefore(now.toLocalDate())) {
            String tooltip = "Driver " + employee.getName() + "'s Medical Certificate expired on " + employee.getMedicalExpiry();
            alerts.add(new AlertItem("🩺", "Medical Certificate expired: " + employee.getMedicalExpiry(), tooltip));
        }
    }
    
    /**
     * Get required documents count
     */
    private int getRequiredDocumentsCount(Employee employee) {
        return complianceConfig.getRequiredDocuments("employee").size();
    }
    
    /**
     * Get uploaded documents count
     */
    private int getUploadedDocumentsCount(Employee employee) {
        return getAvailableDocuments(employee).size();
    }
    
    /**
     * Get missing documents count
     */
    private int getMissingDocumentsCount(Employee employee) {
        Set<String> availableDocs = getAvailableDocuments(employee);
        return complianceConfig.getMissingDocuments("employee", availableDocs).size();
    }
    
    /**
     * Extract document type from filename
     */
    private String getDocumentType(String fileName) {
        String[] parts = fileName.split("_");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "Unknown";
    }
    
    /**
     * Get file size as string
     */
    private String getFileSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "Unknown";
        }
    }
    
    /**
     * Get last modified date as string
     */
    private String getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toString().substring(0, 10);
        } catch (IOException e) {
            return "Unknown";
        }
    }
    
    /**
     * Get file extension
     */
    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }
    
    /**
     * Create styled button
     */
    private Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-font-size: 12px; -fx-padding: 8 16; " +
            "-fx-cursor: hand; -fx-background-radius: 4px;", color));
        
        button.setOnMouseEntered(e -> 
            button.setStyle(button.getStyle() + "-fx-opacity: 0.8;"));
        button.setOnMouseExited(e -> 
            button.setStyle(button.getStyle().replace("-fx-opacity: 0.8;", "")));
        
        return button;
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
     * Document item for ListView
     */
    public static class DocumentItem {
        private final String fileName;
        private final String documentType;
        private final String fileSize;
        private final String lastModified;
        
        public DocumentItem(String fileName, String documentType, String fileSize, String lastModified) {
            this.fileName = fileName;
            this.documentType = documentType;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
        }
        
        public String getFileName() { return fileName; }
        public String getDocumentType() { return documentType; }
        public String getFileSize() { return fileSize; }
        public String getLastModified() { return lastModified; }
        
        @Override
        public String toString() {
            return fileName;
        }
    }
    
    /**
     * Custom ListCell for document items
     */
    private static class DocumentListCell extends ListCell<DocumentItem> {
        @Override
        protected void updateItem(DocumentItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(2);
                Label nameLabel = new Label(item.getFileName());
                nameLabel.setStyle("-fx-font-weight: bold;");
                
                HBox detailsBox = new HBox(10);
                Label typeLabel = new Label("Type: " + item.getDocumentType());
                Label sizeLabel = new Label("Size: " + item.getFileSize());
                Label dateLabel = new Label("Modified: " + item.getLastModified());
                
                typeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
                sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
                dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
                
                detailsBox.getChildren().addAll(typeLabel, sizeLabel, dateLabel);
                content.getChildren().addAll(nameLabel, detailsBox);
                
                setGraphic(content);
                setText(null);
            }
        }
    }
    
    /**
     * Alert item for enhanced compliance alerts
     */
    public static class AlertItem {
        private final String icon;
        private final String message;
        private final String tooltip;
        
        public AlertItem(String icon, String message, String tooltip) {
            this.icon = icon;
            this.message = message;
            this.tooltip = tooltip;
        }
        
        public String getIcon() { return icon; }
        public String getMessage() { return message; }
        public String getTooltip() { return tooltip; }
        
        @Override
        public String toString() {
            return message;
        }
    }
    
    /**
     * Custom ListCell for alert items
     */
    private static class AlertListCell extends ListCell<AlertItem> {
        @Override
        protected void updateItem(AlertItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox content = new HBox(8);
                content.setAlignment(Pos.CENTER_LEFT);
                content.setPadding(new Insets(5, 0, 5, 0));
                
                Label iconLabel = new Label(item.getIcon());
                iconLabel.setStyle("-fx-font-size: 16px;");
                
                Label messageLabel = new Label(item.getMessage());
                messageLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #856404; -fx-font-weight: 500;");
                messageLabel.setWrapText(true);
                
                content.getChildren().addAll(iconLabel, messageLabel);
                
                // Add tooltip
                Tooltip tooltip = new Tooltip(item.getTooltip());
                tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: #333; -fx-text-fill: white;");
                Tooltip.install(content, tooltip);
                
                setGraphic(content);
                setText(null);
            }
        }
    }
} 