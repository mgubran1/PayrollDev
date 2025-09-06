package com.company.payroll.loads;

import com.company.payroll.loads.LoadDAO;
import com.company.payroll.loads.CustomerAddress;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;

/**
 * Comprehensive Address Book Manager with bulk import/export capabilities
 * and robust data normalization for handling large address datasets
 */
public class AddressBookManager {
    private static final Logger logger = LoggerFactory.getLogger(AddressBookManager.class);
    private final LoadDAO loadDAO;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    
    public AddressBookManager(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
    }
    
    /**
     * Shows the main Address Book Manager dialog
     */
    public void showAddressBookManager(Stage owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Address Book Manager");
        dialog.setHeaderText("Manage Customer Addresses");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setResizable(true);
        
        // Set initial size and allow resizing
        dialog.setWidth(800);
        dialog.setHeight(600);
        // dialog.setMinWidth(600); // REMOVED
        // dialog.setMinHeight(400); // REMOVED
        
        // Create scrollable content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMinWidth(600);
        scrollPane.setMinHeight(400);
        
        // Create main content
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setMinWidth(600);
        content.setMinHeight(400);
        content.setStyle("-fx-text-fill: black; -fx-background-color: white;");
        
        // Import section
        VBox importSection = createImportSection();
        
        // Export section
        VBox exportSection = createExportSection();
        
        // Statistics section
        VBox statsSection = createStatisticsSection();
        
        content.getChildren().addAll(importSection, exportSection, statsSection);
        
        // Set the content in the scroll pane
        scrollPane.setContent(content);
        
        // Add close button
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeButtonType);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setStyle("-fx-text-fill: black; -fx-background-color: white;");
        dialog.setResultConverter(dialogButton -> null);
        
        dialog.showAndWait();
    }
    
    private VBox createImportSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");
        
        Label title = new Label("📥 Import Addresses");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: black;");
        
        Label description = new Label(
            "Import addresses from CSV files with columns: Customer, Address, City, State, Zip (optional)\n" +
            "All addresses will be converted to uppercase and duplicates will be automatically removed.\n" +
            "Supports large files with 5000+ addresses for fast, professional processing.\n" +
            "Zip codes are optional but recommended for per-mile payment calculations."
        );
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: black;");
        
        Button importBtn = new Button("📁 Import CSV File");
        importBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        
        // Progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setMaxWidth(200);
        
        // Status label
        Label statusLabel = new Label("Ready to import");
        statusLabel.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
        
        importBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select CSV File");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
            );
            
            File file = fileChooser.showOpenDialog(importBtn.getScene().getWindow());
            if (file != null) {
                // Show progress and status
                progressIndicator.setVisible(true);
                statusLabel.setText("Starting import...");
                importBtn.setDisable(true);
                
                // Start the enhanced import process
                importAddressesFromCSV(file, progressIndicator, statusLabel);
                
                // Re-enable button when done
                progressIndicator.visibleProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal) {
                        importBtn.setDisable(false);
                    }
                });
            }
        });
        
        section.getChildren().addAll(title, description, importBtn, progressIndicator, statusLabel);
        return section;
    }
    
    private VBox createExportSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");
        
        Label title = new Label("📤 Export Addresses");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: black;");
        
        Label description = new Label(
            "Export all customer addresses to CSV or Excel format for backup or external editing."
        );
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: black;");
        
        Button exportCsvBtn = new Button("Export to CSV");
        Button exportExcelBtn = new Button("Export to Excel");
        
        exportCsvBtn.setOnAction(e -> exportToCsv());
        exportExcelBtn.setOnAction(e -> exportToExcel());
        
        HBox buttonBox = new HBox(10, exportCsvBtn, exportExcelBtn);
        
        section.getChildren().addAll(title, description, buttonBox);
        return section;
    }
    
    private VBox createStatisticsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");
        
        Label title = new Label("📊 Address Statistics");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: black;");
        
        // Load statistics asynchronously
        CompletableFuture.supplyAsync(() -> loadDAO.getAddressBookStatistics(), executorService)
            .thenAcceptAsync(stats -> {
                Platform.runLater(() -> {
                    section.getChildren().clear();
                    section.getChildren().add(title);
                    
                    if (stats != null) {
                        Label totalCustomers = new Label("Total Customers: " + stats.get("totalCustomers"));
                        totalCustomers.setStyle("-fx-text-fill: black;");
                        
                        Label totalAddresses = new Label("Total Addresses: " + stats.get("totalAddresses"));
                        totalAddresses.setStyle("-fx-text-fill: black;");
                        
                        Label avgAddressesPerCustomer = new Label("Avg Addresses per Customer: " + stats.get("avgAddressesPerCustomer"));
                        avgAddressesPerCustomer.setStyle("-fx-text-fill: black;");
                        
                        section.getChildren().addAll(totalCustomers, totalAddresses, avgAddressesPerCustomer);
                    } else {
                        Label noData = new Label("No address data available");
                        noData.setStyle("-fx-text-fill: black;");
                        section.getChildren().add(noData);
                    }
                });
            });
        
        return section;
    }
    
    private void importFromCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            showImportProgressDialog(file, "CSV");
        }
    }
    
    private void importFromExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Excel File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            showImportProgressDialog(file, "Excel");
        }
    }
    
    private void showImportProgressDialog(File file, String fileType) {
        Dialog<ImportResult> dialog = new Dialog<>();
        dialog.setTitle("Importing Addresses");
        dialog.setHeaderText("Importing addresses from " + file.getName());
        dialog.initModality(Modality.APPLICATION_MODAL);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(400);
        
        Label statusLabel = new Label("Preparing import...");
        Label detailsLabel = new Label("");
        
        content.getChildren().addAll(progressBar, statusLabel, detailsLabel);
        
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelButtonType);
        dialog.getDialogPane().setContent(content);
        
        // Start import process
        CompletableFuture.supplyAsync(() -> {
            try {
                return fileType.equals("CSV") ? 
                    importFromCsvFile(file, progressBar, statusLabel, detailsLabel) :
                    importFromExcelFile(file, progressBar, statusLabel, detailsLabel);
            } catch (Exception e) {
                logger.error("Import failed", e);
                return new ImportResult(0, 0, 0, "Import failed: " + e.getMessage());
            }
        }, executorService).thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                dialog.setResult(result);
                showImportResultDialog(result);
            });
        });
        
        dialog.showAndWait();
    }
    
    private ImportResult importFromCsvFile(File file, ProgressBar progressBar, Label statusLabel, Label detailsLabel) 
            throws IOException {
        List<AddressRecord> records = new ArrayList<>();
        int totalLines = 0;
        
        // First pass: count lines and parse
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                totalLines++;
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                
                String[] parts = parseCsvLine(line);
                if (parts.length >= 4) {
                    String zipCode = parts.length > 4 ? normalizeString(parts[4]) : "";
                    records.add(new AddressRecord(
                        normalizeString(parts[0]), // Customer Name
                        normalizeString(parts[1]), // Address
                        normalizeString(parts[2]), // City
                        normalizeString(parts[3]), // State
                        zipCode                    // Zip Code (optional)
                    ));
                }
            }
        }
        
        return processImportRecords(records, progressBar, statusLabel, detailsLabel);
    }
    
    private ImportResult importFromExcelFile(File file, ProgressBar progressBar, Label statusLabel, Label detailsLabel) 
            throws IOException {
        List<AddressRecord> records = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows();
            
            for (int i = 1; i < totalRows; i++) { // Skip header row
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row != null) {
                    String customerName = getCellValue((org.apache.poi.ss.usermodel.Cell) row.getCell(0));
                    String address = getCellValue((org.apache.poi.ss.usermodel.Cell) row.getCell(1));
                    String city = getCellValue((org.apache.poi.ss.usermodel.Cell) row.getCell(2));
                    String state = getCellValue((org.apache.poi.ss.usermodel.Cell) row.getCell(3));
                    String zipCode = getCellValue((org.apache.poi.ss.usermodel.Cell) row.getCell(4));
                    
                    if (customerName != null && !customerName.trim().isEmpty()) {
                        records.add(new AddressRecord(
                            normalizeString(customerName),
                            normalizeString(address),
                            normalizeString(city),
                            normalizeString(state),
                            normalizeString(zipCode != null ? zipCode : "")
                        ));
                    }
                }
                
                // Update progress
                final int currentRow = i;
                Platform.runLater(() -> {
                    progressBar.setProgress((double) currentRow / totalRows);
                    statusLabel.setText("Processing row " + currentRow + " of " + totalRows);
                });
            }
        }
        
        return processImportRecords(records, progressBar, statusLabel, detailsLabel);
    }
    
    private ImportResult processImportRecords(List<AddressRecord> records, ProgressBar progressBar, 
                                           Label statusLabel, Label detailsLabel) {
        int totalRecords = records.size();
        int importedCount = 0;
        int duplicateCount = 0;
        int invalidCount = 0;
        
        // Deduplicate records
        Set<String> seenAddresses = new HashSet<>();
        List<AddressRecord> uniqueRecords = new ArrayList<>();
        
        for (AddressRecord record : records) {
            String addressKey = record.getAddressKey();
            if (!seenAddresses.contains(addressKey)) {
                seenAddresses.add(addressKey);
                uniqueRecords.add(record);
            } else {
                duplicateCount++;
            }
        }
        
        // Import unique records
        for (int i = 0; i < uniqueRecords.size(); i++) {
            AddressRecord record = uniqueRecords.get(i);
            
            try {
                // Check if customer exists, create if not
                loadDAO.addCustomerIfNotExists(record.customerName);
                
                // Check if address already exists for this customer
                List<CustomerAddress> existingAddresses = loadDAO.getCustomerAddressBook(record.customerName);
                boolean exists = existingAddresses.stream().anyMatch(addr -> 
                    Objects.equals(normalizeString(addr.getAddress()), record.address) &&
                    Objects.equals(normalizeString(addr.getCity()), record.city) &&
                    Objects.equals(normalizeString(addr.getState()), record.state)
                );
                
                if (!exists) {
                    int addressId = loadDAO.addCustomerAddress(
                        record.customerName, 
                        generateLocationName(record.address, record.city, record.state),
                        record.address, 
                        record.city, 
                        record.state
                    );
                    if (addressId > 0) {
                        importedCount++;
                    }
                } else {
                    duplicateCount++;
                }
            } catch (Exception e) {
                logger.error("Error importing address record", e);
                invalidCount++;
            }
            
            // Update progress
            final int currentIndex = i;
            final int importedCountFinal = importedCount;
            final int duplicateCountFinal = duplicateCount;
            final int invalidCountFinal = invalidCount;
            Platform.runLater(() -> {
                progressBar.setProgress((double) currentIndex / uniqueRecords.size());
                statusLabel.setText("Importing address " + (currentIndex + 1) + " of " + uniqueRecords.size());
                detailsLabel.setText(String.format("Imported: %d, Duplicates: %d, Invalid: %d", 
                    importedCountFinal, duplicateCountFinal, invalidCountFinal));
            });
        }
        
        return new ImportResult(importedCount, duplicateCount, invalidCount, "Import completed successfully");
    }
    
    private void showImportResultDialog(ImportResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Complete");
        alert.setHeaderText("Address Import Results");
        alert.setContentText(String.format(
            "Import completed!\n\n" +
            "✅ Successfully imported: %d addresses\n" +
            "🔄 Duplicates skipped: %d\n" +
            "❌ Invalid entries: %d\n\n" +
            "%s",
            result.importedCount, result.duplicateCount, result.invalidCount, result.message
        ));
        alert.showAndWait();
    }
    
    private void exportToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("address_book_export.csv");
        
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            exportAddressesToCsv(file);
        }
    }
    
    private void exportToExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Excel File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("address_book_export.xlsx");
        
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            exportAddressesToExcel(file);
        }
    }
    
    private void exportAddressesToCsv(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header
            writer.println("Customer Name,Address,City,State,Zip Code,Location Name,Default Pickup,Default Drop");
            
            // Get all customers and their addresses
            List<String> customers = loadDAO.getAllCustomers();
            for (String customer : customers) {
                List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(customer);
                for (CustomerAddress address : addresses) {
                    writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,%s%n",
                        escapeCsvField(customer),
                        escapeCsvField(address.getAddress()),
                        escapeCsvField(address.getCity()),
                        escapeCsvField(address.getState()),
                        escapeCsvField(address.getZipCode() != null ? address.getZipCode() : ""),
                        escapeCsvField(address.getLocationName()),
                        address.isDefaultPickup() ? "Yes" : "No",
                        address.isDefaultDrop() ? "Yes" : "No"
                    );
                }
            }
            
            showInfo("Address book exported successfully to " + file.getName());
        } catch (IOException e) {
            logger.error("Error exporting to CSV", e);
            showError("Error exporting to CSV: " + e.getMessage());
        }
    }
    
    private void exportAddressesToExcel(File file) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Address Book");
            
            // Create header row
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] headers = {"Customer Name", "Address", "City", "State", "Zip Code", "Location Name", "Default Pickup", "Default Drop"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            
            // Add data rows
            int rowNum = 1;
            List<String> customers = loadDAO.getAllCustomers();
            for (String customer : customers) {
                List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(customer);
                for (CustomerAddress address : addresses) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(customer);
                    row.createCell(1).setCellValue(address.getAddress());
                    row.createCell(2).setCellValue(address.getCity());
                    row.createCell(3).setCellValue(address.getState());
                    row.createCell(4).setCellValue(address.getZipCode() != null ? address.getZipCode() : "");
                    row.createCell(5).setCellValue(address.getLocationName());
                    row.createCell(6).setCellValue(address.isDefaultPickup() ? "Yes" : "No");
                    row.createCell(7).setCellValue(address.isDefaultDrop() ? "Yes" : "No");
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
            
            showInfo("Address book exported successfully to " + file.getName());
        } catch (IOException e) {
            logger.error("Error exporting to Excel", e);
            showError("Error exporting to Excel: " + e.getMessage());
        }
    }
    
    // Utility methods
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        
        return result.toArray(new String[0]);
    }
    
    private String getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf((int) cell.getNumericCellValue());
            default: return null;
        }
    }
    
    private String normalizeString(String str) {
        if (str == null) return "";
        return str.trim().toLowerCase()
            .replaceAll("\\s+", " ")
            .replaceAll("[.,;]", "")
            .replaceAll("\\b(st|street|rd|road|ave|avenue|blvd|boulevard)\\b", "");
    }
    
    private String generateLocationName(String address, String city, String state) {
        StringBuilder name = new StringBuilder();
        
        if (city != null && !city.trim().isEmpty()) {
            name.append(city.trim());
        }
        if (state != null && !state.trim().isEmpty()) {
            if (name.length() > 0) name.append(", ");
            name.append(state.trim());
        }
        if (name.length() == 0 && address != null && !address.trim().isEmpty()) {
            String[] addressParts = address.trim().split(" ");
            name.append(addressParts[0]);
        }
        
        return name.toString();
    }
    
    private String escapeCsvField(String field) {
        if (field == null) return "";
        return field.replace("\"", "\"\"");
    }
    
    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    // Data classes
    private static class AddressRecord {
        final String customerName;
        final String address;
        final String city;
        final String state;
        final String zipCode;
        
        AddressRecord(String customerName, String address, String city, String state) {
            this(customerName, address, city, state, "");
        }
        
        AddressRecord(String customerName, String address, String city, String state, String zipCode) {
            this.customerName = customerName;
            this.address = address;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode != null ? zipCode : "";
        }
        
        String getAddressKey() {
            return customerName + "|" + address + "|" + city + "|" + state + (zipCode.isEmpty() ? "" : "|" + zipCode);
        }
    }
    
    private static class ImportResult {
        final int importedCount;
        final int duplicateCount;
        final int invalidCount;
        final String message;
        
        ImportResult(int importedCount, int duplicateCount, int invalidCount, String message) {
            this.importedCount = importedCount;
            this.duplicateCount = duplicateCount;
            this.invalidCount = invalidCount;
            this.message = message;
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Import addresses from CSV file with enhanced performance and robustness
     */
    private void importAddressesFromCSV(File file, ProgressIndicator progressIndicator, Label statusLabel) {
        Task<Void> importTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    updateMessage("Starting import process...");
                    updateProgress(0, 100);
                    
                    // Read all lines first to get total count - use more robust encoding detection
                    List<String> lines = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            lines.add(line);
                        }
                    } catch (Exception e) {
                        // Try with different encoding if UTF-8 fails
                        logger.warn("UTF-8 encoding failed, trying ISO-8859-1: {}", e.getMessage());
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                lines.add(line);
                            }
                        }
                    }
                    
                    if (lines.isEmpty()) {
                        throw new Exception("CSV file is empty");
                    }
                    
                    logger.info("CSV file contains {} lines", lines.size());
                    
                    // Parse header and validate columns
                    String headerLine = lines.get(0);
                    if (headerLine == null || headerLine.trim().isEmpty()) {
                        throw new Exception("CSV file has empty header line");
                    }
                    
                    logger.info("Header line: '{}'", headerLine);
                    
                    String[] headers = headerLine.split(",");
                    if (headers.length < 4) {
                        throw new Exception("CSV file must have at least 4 columns: Customer, Address, City, State. Found: " + headers.length);
                    }
                    
                    Map<String, Integer> columnMap = new HashMap<>();
                    
                    // Find required columns (case-insensitive)
                    for (int i = 0; i < headers.length; i++) {
                        String header = headers[i].trim().toLowerCase();
                        logger.debug("Processing header {}: '{}'", i, header);
                        if (header.equals("customer") || header.equals("customer name")) {
                            columnMap.put("customer", i);
                        } else if (header.equals("address")) {
                            columnMap.put("address", i);
                        } else if (header.equals("city")) {
                            columnMap.put("city", i);
                        } else if (header.equals("state")) {
                            columnMap.put("state", i);
                        }
                    }
                    
                    // Validate required columns
                    if (!columnMap.containsKey("customer") || !columnMap.containsKey("address") || 
                        !columnMap.containsKey("city") || !columnMap.containsKey("state")) {
                        throw new Exception("Missing required columns. Expected: Customer, Address, City, State. Found: " + 
                            String.join(", ", Arrays.stream(headers).map(String::trim).toArray(String[]::new)));
                    }
                    
                    updateMessage("Validating CSV structure...");
                    updateProgress(5, 100);
                    
                    // Process data lines
                    List<String> dataLines = lines.subList(1, lines.size());
                    int totalLines = dataLines.size();
                    
                    updateMessage("Processing " + totalLines + " addresses...");
                    updateProgress(10, 100);
                    
                    // Use Set for fast duplicate detection
                    Set<String> uniqueAddresses = new HashSet<>();
                    List<Address> addressesToInsert = new ArrayList<>();
                    int processedCount = 0;
                    int duplicateCount = 0;
                    int invalidCount = 0;
                    
                    for (int lineIndex = 0; lineIndex < dataLines.size(); lineIndex++) {
                        String line = dataLines.get(lineIndex);
                        try {
                            // Skip empty lines
                            if (line == null || line.trim().isEmpty()) {
                                logger.debug("Skipping empty line at index {}", lineIndex);
                                continue;
                            }
                            
                            logger.debug("Processing line {}: '{}'", lineIndex + 1, line);
                            
                            // Parse CSV line (handle commas in quoted fields)
                            String[] fields = parseCSVLine(line);
                            logger.debug("Parsed {} fields from line {}", fields.length, lineIndex + 1);
                            
                            // Validate we have enough fields
                            int maxColumnIndex = Math.max(
                                Math.max(columnMap.get("customer"), columnMap.get("address")),
                                Math.max(columnMap.get("city"), columnMap.get("state"))
                            );
                            
                            if (fields.length <= maxColumnIndex) {
                                invalidCount++;
                                logger.debug("Skipping invalid line {} - insufficient fields ({} <= {}): {}", 
                                    lineIndex + 1, fields.length, maxColumnIndex, line);
                                continue;
                            }
                            
                            // Extract and normalize data
                            String customer = normalizeField(fields[columnMap.get("customer")]);
                            String address = normalizeField(fields[columnMap.get("address")]);
                            String city = normalizeField(fields[columnMap.get("city")]);
                            String state = normalizeField(fields[columnMap.get("state")]);
                            
                            logger.debug("Extracted data - Customer: '{}', Address: '{}', City: '{}', State: '{}'", 
                                customer, address, city, state);
                            
                            // Validate required fields
                            if (customer.isEmpty() || address.isEmpty() || city.isEmpty() || state.isEmpty()) {
                                invalidCount++;
                                logger.debug("Skipping invalid line {} - missing required fields: {}", lineIndex + 1, line);
                                continue;
                            }
                            
                            // Create unique key for duplicate detection
                            String uniqueKey = (customer + "|" + address + "|" + city + "|" + state).toUpperCase();
                            
                            if (uniqueAddresses.add(uniqueKey)) {
                                // Create address object with all uppercase
                                Address addressObj = new Address();
                                addressObj.setCustomerName(customer.toUpperCase());
                                addressObj.setAddress(address.toUpperCase());
                                addressObj.setCity(city.toUpperCase());
                                addressObj.setState(state.toUpperCase());
                                addressesToInsert.add(addressObj);
                            } else {
                                duplicateCount++;
                            }
                            
                            processedCount++;
                            
                            // Update progress every 100 records
                            if (processedCount % 100 == 0) {
                                int progress = 10 + (int)((processedCount * 70.0) / totalLines);
                                updateProgress(progress, 100);
                                updateMessage("Processed " + processedCount + "/" + totalLines + " addresses...");
                            }
                            
                        } catch (Exception e) {
                            invalidCount++;
                            logger.error("Error processing line {}: '{}' - Error: {}", lineIndex + 1, line, e.getMessage(), e);
                            // Continue processing other lines
                        }
                    }
                    
                    updateMessage("Inserting " + addressesToInsert.size() + " unique addresses...");
                    updateProgress(80, 100);
                    
                    // Batch insert for better performance
                    if (!addressesToInsert.isEmpty()) {
                        loadDAO.batchInsert(addressesToInsert);
                    }
                    
                    updateMessage("Import completed successfully!");
                    updateProgress(100, 100);
                    
                    final int processedCountFinal = processedCount;
                    final int duplicateCountFinal = duplicateCount;
                    final int invalidCountFinal = invalidCount;
                    // Show summary
                    Platform.runLater(() -> {
                        showImportSummary(processedCountFinal, addressesToInsert.size(), duplicateCountFinal, invalidCountFinal);
                        refreshStatistics();
                    });
                    
                } catch (Exception e) {
                    logger.error("Import failed with error: {}", e.getMessage(), e);
                    updateMessage("Import failed: " + e.getMessage());
                    throw e;
                }
                
                return null;
            }
        };
        
        // Handle task completion
        importTask.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            statusLabel.setText("Import completed successfully!");
        });
        
        importTask.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            Throwable exception = importTask.getException();
            statusLabel.setText("Import failed: " + exception.getMessage());
            
            // Show detailed error dialog
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Import Error");
                alert.setHeaderText("Failed to import addresses");
                alert.setContentText("Error: " + exception.getMessage());
                alert.showAndWait();
            });
        });
        
        // Start the import task
        new Thread(importTask).start();
    }
    
    /**
     * Parse CSV line handling quoted fields with commas
     */
    private String[] parseCSVLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new String[0];
        }
        
        // Handle single character lines that might cause issues
        if (line.length() == 1) {
            logger.warn("Found single character line: '{}'", line);
            return new String[]{line};
        }
        
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        try {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                
                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) {
                    fields.add(currentField.toString().trim());
                    currentField.setLength(0);
                } else {
                    currentField.append(c);
                }
            }
            
            // Add the last field
            fields.add(currentField.toString().trim());
            
            logger.debug("Successfully parsed line into {} fields", fields.size());
            return fields.toArray(new String[0]);
            
        } catch (Exception e) {
            logger.error("Error parsing CSV line: '{}' - Error: {}", line, e.getMessage(), e);
            // Return a safe fallback
            return new String[]{line.trim()};
        }
    }
    
    /**
     * Normalize field by removing extra whitespace and quotes
     */
    private String normalizeField(String field) {
        if (field == null) return "";
        
        // Remove surrounding quotes
        field = field.replaceAll("^\"|\"$", "");
        
        // Normalize whitespace
        field = field.replaceAll("\\s+", " ").trim();
        
        return field;
    }
    
    /**
     * Show import summary dialog
     */
    private void showImportSummary(int totalProcessed, int imported, int duplicates, int invalid) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Summary");
        alert.setHeaderText("Address Import Completed");
        
        String content = String.format(
            "Import Summary:\n\n" +
            "• Total records processed: %d\n" +
            "• Successfully imported: %d\n" +
            "• Duplicates skipped: %d\n" +
            "• Invalid records: %d\n\n" +
            "All addresses have been converted to uppercase and duplicates have been removed.",
            totalProcessed, imported, duplicates, invalid
        );
        
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Refresh the statistics displayed in the main dialog.
     */
    private void refreshStatistics() {
        // Statistics will be refreshed when the dialog is reopened
        // This method is called after import to ensure fresh data
        logger.info("Statistics refresh requested");
    }
} 