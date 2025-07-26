package com.company.payroll.fuel;

import javafx.scene.Node;
import javafx.scene.Scene;
import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.concurrent.Task;
import javafx.concurrent.Service;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Orientation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.company.payroll.util.WindowAware;

public class FuelImportTab extends BorderPane implements WindowAware {
    private static final Logger logger = LoggerFactory.getLogger(FuelImportTab.class);

    private final FuelTransactionDAO dao = new FuelTransactionDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ObservableList<FuelTransaction> allData = FXCollections.observableArrayList();
    private final FilteredList<FuelTransaction> filteredData;
    private final DatePicker startDatePicker = new DatePicker();
    private final DatePicker endDatePicker = new DatePicker();
    
    // Enhanced filtering components
    private final TextField driverSearchField = new TextField();
    private final ComboBox<String> driverFilterCombo = new ComboBox<>();
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final Label recordCountLabel = new Label();
    
    // Configuration for column mapping
    private FuelImportConfig importConfig;
    
    // Add interface for fuel data changes
    public interface FuelDataChangeListener {
        void onFuelDataChanged();
    }
    
    private final List<FuelDataChangeListener> fuelDataChangeListeners = new ArrayList<>();

    public FuelImportTab() {
        logger.info("Initializing FuelImportTab");
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #F5F5F5;");

        // Initialize configuration with defaults
        importConfig = FuelImportConfig.loadDefault();

        // Initialize filtered list
        filteredData = new FilteredList<>(allData, p -> true);

        // Create main components
        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(0));

        // Header Section
        VBox headerSection = createHeaderSection();
        
        // Action Bar
        HBox actionBar = createActionBar();
        
        // Filter Section
        VBox filterSection = createFilterSection();
        
        // Status Bar
        HBox statusBar = createStatusBar();
        
        // Table
        TableView<FuelTransaction> table = createEnhancedTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // Add all components
        mainContainer.getChildren().addAll(headerSection, actionBar, filterSection, statusBar, table);
        
        setCenter(mainContainer);
        
        // Setup event handlers
        setupEventHandlers();
        
        // Set default date range to last 14 days
        setDefaultDateRange();
        
        // Load initial data
        reload();
        populateDriverFilter();
        updateRecordCount();

        logger.info("FuelImportTab initialization complete");
    }
    
    public void addFuelDataChangeListener(FuelDataChangeListener listener) {
        fuelDataChangeListeners.add(listener);
        logger.debug("Added fuel data change listener. Total listeners: {}", fuelDataChangeListeners.size());
    }
    
    public void removeFuelDataChangeListener(FuelDataChangeListener listener) {
        fuelDataChangeListeners.remove(listener);
        logger.debug("Removed fuel data change listener. Total listeners: {}", fuelDataChangeListeners.size());
    }
    
    private void notifyFuelDataChanged() {
        logger.info("Notifying {} fuel data change listeners", fuelDataChangeListeners.size());
        for (FuelDataChangeListener listener : fuelDataChangeListeners) {
            listener.onFuelDataChanged();
        }
    }

    private void applyDateFilter(LocalDate start, LocalDate end) {
        logger.info("Applying date filter - Start: {}, End: {}", start, end);
        
        filteredData.setPredicate(transaction -> {
            if (start == null && end == null) {
                return true;
            }
            
            try {
                LocalDate tranDate = LocalDate.parse(transaction.getTranDate());
                
                if (start != null && end != null) {
                    return !tranDate.isBefore(start) && !tranDate.isAfter(end);
                } else if (start != null) {
                    return !tranDate.isBefore(start);
                } else {
                    return !tranDate.isAfter(end);
                }
            } catch (Exception e) {
                // If date parsing fails, include the transaction
                logger.debug("Failed to parse date: {}", transaction.getTranDate());
                return true;
            }
        });
        
        logger.info("Filtered {} transactions", filteredData.size());
    }

    private void reload() {
        logger.debug("Reloading fuel transactions");
        
        // Update status
        Platform.runLater(() -> {
            statusLabel.setText("Loading data...");
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        });
        
        // Load data in background with date range optimization
        Task<List<FuelTransaction>> loadTask = new Task<List<FuelTransaction>>() {
            @Override
            protected List<FuelTransaction> call() throws Exception {
                // Check if we have date filters set for performance optimization
                LocalDate start = startDatePicker.getValue();
                LocalDate end = endDatePicker.getValue();
                
                if (start != null && end != null) {
                    // Use date range query for better performance
                    logger.debug("Loading fuel transactions with date range: {} to {}", start, end);
                    return dao.getByDateRange(start, end);
                } else {
                    // Fall back to loading all data
                    logger.debug("Loading all fuel transactions");
                    return dao.getAll();
                }
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            allData.setAll(loadTask.getValue());
            logger.info("Loaded {} fuel transactions", allData.size());
            
            // Apply current filters after loading
            applyFilters();
            
            // Update UI
            updateRecordCount();
            populateDriverFilter();
            statusLabel.setText("Data loaded successfully");
            progressBar.setVisible(false);
        });
        
        loadTask.setOnFailed(e -> {
            logger.error("Failed to load fuel transactions", loadTask.getException());
            statusLabel.setText("Failed to load data");
            progressBar.setVisible(false);
            showAlert(Alert.AlertType.ERROR, "Load Error", 
                     "Failed to load fuel transactions: " + loadTask.getException().getMessage());
        });
        
        new Thread(loadTask).start();
    }
    
    /**
     * Setup event handlers for all buttons
     */
    private void setupEventHandlers() {
        // Configure button
        configureBtn.setOnAction(e -> {
            logger.info("Configure import button clicked");
            showConfigurationDialog();
        });
        
        // Add button
        addBtn.setOnAction(e -> {
            logger.info("Add fuel transaction button clicked");
            showEditDialog(null, true);
        });
        
        // Edit button
        editBtn.setOnAction(e -> {
            FuelTransaction t = table.getSelectionModel().getSelectedItem();
            if (t != null) {
                logger.info("Edit fuel transaction button clicked for invoice: {}", t.getInvoice());
                showEditDialog(t, false);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select a transaction to edit.");
            }
        });
        
        // Delete button
        deleteBtn.setOnAction(e -> {
            FuelTransaction t = table.getSelectionModel().getSelectedItem();
            if (t != null) {
                logger.info("Delete fuel transaction button clicked for invoice: {}", t.getInvoice());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Delete");
                confirm.setHeaderText("Delete Fuel Transaction");
                confirm.setContentText("Are you sure you want to delete invoice " + t.getInvoice() + "?");
                
                confirm.showAndWait().ifPresent(result -> {
                    if (result == ButtonType.OK) {
                        logger.info("User confirmed deletion of invoice: {}", t.getInvoice());
                        dao.delete(t.getId());
                        reload();
                        notifyFuelDataChanged();
                    }
                });
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select a transaction to delete.");
            }
        });
        
        // Refresh button
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            reload();
        });
        
        // Import button
        importBtn.setOnAction(e -> {
            logger.info("Import button clicked");
            showImportDialog();
        });
    }
    
    /**
     * Populate driver filter combo box
     */
    private void populateDriverFilter() {
        Set<String> uniqueDrivers = allData.stream()
            .map(FuelTransaction::getDriverName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));
        
        driverFilterCombo.getItems().clear();
        driverFilterCombo.getItems().add(""); // Empty option for all drivers
        driverFilterCombo.getItems().addAll(uniqueDrivers);
    }

    /**
     * Show import dialog with file selection and progress
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Fuel Transactions");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"),
            new FileChooser.ExtensionFilter("All Supported", "*.csv", "*.xlsx")
        );
        
        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            logger.info("Import file selected: {}", file.getAbsolutePath());
            
            // Check file size for warning
            long fileSizeMB = file.length() / (1024 * 1024);
            if (fileSizeMB > 10) {
                Alert warning = new Alert(Alert.AlertType.CONFIRMATION);
                warning.setTitle("Large File Warning");
                warning.setHeaderText("Large File Detected");
                warning.setContentText(String.format(
                    "The selected file is %d MB. Import may take several minutes.\n" +
                    "Do you want to continue?", fileSizeMB));
                
                if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }
            
            // Show progress dialog
            showImportProgressDialog(file);
        }
    }
    
    /**
     * Show import progress dialog
     */
    private void showImportProgressDialog(File file) {
        Stage progressStage = new Stage();
        progressStage.setTitle("Importing Fuel Transactions");
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.initOwner(getScene().getWindow());
        progressStage.setResizable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(400);
        
        Label titleLabel = new Label("Importing from: " + file.getName());
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(350);
        
        Label progressLabel = new Label("Preparing to import...");
        Label detailLabel = new Label("");
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button cancelBtn = new Button("Cancel");
        Button closeBtn = new Button("Close");
        closeBtn.setDisable(true);
        
        buttonBox.getChildren().addAll(cancelBtn, closeBtn);
        
        root.getChildren().addAll(titleLabel, progressBar, progressLabel, detailLabel, buttonBox);
        
        Scene scene = new Scene(root);
        progressStage.setScene(scene);
        
        // Create import task
        ImportTask importTask = new ImportTask(file);
        
        // Bind progress
        progressBar.progressProperty().bind(importTask.progressProperty());
        progressLabel.textProperty().bind(importTask.messageProperty());
        
        // Update detail label periodically
        importTask.importedProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                detailLabel.setText(String.format("Imported: %d, Skipped: %d, Errors: %d",
                    importTask.getImported(), importTask.getSkipped(), importTask.getErrors()));
            });
        });
        
        // Handle completion
        importTask.setOnSucceeded(e -> {
            ImportResult result = importTask.getValue();
            progressLabel.setText("Import completed!");
            cancelBtn.setDisable(true);
            closeBtn.setDisable(false);
            
            // Reload data
            reload();
            if (result.imported > 0) {
                notifyFuelDataChanged();
            }
            
            // Show summary
            showImportSummary(result);
        });
        
        importTask.setOnFailed(e -> {
            progressLabel.setText("Import failed!");
            cancelBtn.setDisable(true);
            closeBtn.setDisable(false);
            
            Throwable error = importTask.getException();
            logger.error("Import failed", error);
            showAlert(Alert.AlertType.ERROR, "Import Error", 
                     "Failed to import file: " + error.getMessage());
        });
        
        importTask.setOnCancelled(e -> {
            progressLabel.setText("Import cancelled");
            progressStage.close();
        });
        
        // Button actions
        cancelBtn.setOnAction(e -> {
            importTask.cancel();
            progressStage.close();
        });
        
        closeBtn.setOnAction(e -> progressStage.close());
        
        // Start import
        Thread importThread = new Thread(importTask);
        importThread.setDaemon(true);
        importThread.start();
        
        progressStage.show();
    }
    
    /**
     * Show import summary dialog
     */
    private void showImportSummary(ImportResult result) {
        Alert summary = new Alert(Alert.AlertType.INFORMATION);
        summary.setTitle("Import Summary");
        summary.setHeaderText("Import Completed");
        
        StringBuilder content = new StringBuilder();
        content.append(String.format("Total Processed: %d\n", result.total));
        content.append(String.format("Successfully Imported: %d\n", result.imported));
        content.append(String.format("Skipped (Duplicates): %d\n", result.skipped));
        content.append(String.format("Errors: %d\n", result.errors));
        
        if (result.errorMessages.size() > 0) {
            content.append("\nFirst few errors:\n");
            result.errorMessages.stream()
                .limit(5)
                .forEach(msg -> content.append("- ").append(msg).append("\n"));
            
            if (result.errorMessages.size() > 5) {
                content.append(String.format("... and %d more errors\n", 
                    result.errorMessages.size() - 5));
            }
        }
        
        summary.setContentText(content.toString());
        summary.showAndWait();
    }
    
    /**
     * Import result class
     */
    private static class ImportResult {
        int total = 0;
        int imported = 0;
        int skipped = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<>();
    }
    
    /**
     * Import task for background processing
     */
    private class ImportTask extends Task<ImportResult> {
        private final File file;
        private int imported = 0;
        private int skipped = 0;
        private int errors = 0;
        
        public ImportTask(File file) {
            this.file = file;
        }
        
        public int getImported() { return imported; }
        public int getSkipped() { return skipped; }
        public int getErrors() { return errors; }
        
        public IntegerProperty importedProperty() {
            return new SimpleIntegerProperty(imported);
        }
        
        @Override
        protected ImportResult call() throws Exception {
            ImportResult result = new ImportResult();
            String fileName = file.getName().toLowerCase();
            
            try {
                List<FuelTransaction> transactions;
                
                updateMessage("Reading file...");
                if (fileName.endsWith(".csv")) {
                    transactions = parseCSVWithProgress(file);
                } else if (fileName.endsWith(".xlsx")) {
                    transactions = parseXLSXWithProgress(file);
                } else {
                    throw new IllegalArgumentException("Unsupported file type");
                }
                
                result.total = transactions.size();
                
                // Process transactions in batches
                updateMessage("Processing transactions...");
                int batchSize = 100;
                
                for (int i = 0; i < transactions.size(); i++) {
                    if (isCancelled()) {
                        updateMessage("Import cancelled");
                        break;
                    }
                    
                    FuelTransaction tx = transactions.get(i);
                    
                    try {
                        if (dao.exists(tx.getInvoice(), tx.getTranDate(), 
                                      tx.getLocationName(), tx.getAmt())) {
                            skipped++;
                            result.skipped++;
                        } else {
                            dao.add(tx);
                            imported++;
                            result.imported++;
                        }
                    } catch (Exception e) {
                        errors++;
                        result.errors++;
                        result.errorMessages.add(String.format("Row %d: %s", i + 2, e.getMessage()));
                        logger.debug("Error importing row {}: {}", i + 2, e.getMessage());
                    }
                    
                    // Update progress
                    updateProgress(i + 1, transactions.size());
                    
                    if (i % batchSize == 0) {
                        updateMessage(String.format("Processing... (%d/%d)", i + 1, transactions.size()));
                    }
                }
                
                updateMessage("Import completed");
                
            } catch (Exception e) {
                logger.error("Import failed", e);
                throw e;
            }
            
            return result;
        }
        
        /**
         * Parse CSV with progress updates
         */
        private List<FuelTransaction> parseCSVWithProgress(File file) throws IOException {
            // For large files, we could count lines first to show accurate progress
            // For now, use the existing parseCSV method
            return parseCSV(file);
        }
        
        /**
         * Parse XLSX with progress updates
         */
        private List<FuelTransaction> parseXLSXWithProgress(File file) throws IOException {
            // For large files, we could count rows first to show accurate progress
            // For now, use the existing parseXLSX method
            return parseXLSX(file);
        }
    }

    private void showEditDialog(FuelTransaction t, boolean isAdd) {
        logger.debug("Showing fuel transaction dialog - isAdd: {}", isAdd);
        Dialog<FuelTransaction> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add Fuel Transaction" : "Edit Fuel Transaction");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField[] fields = new TextField[21];
        String[] values = t == null ? new String[21] : new String[]{
            t.getCardNumber(), t.getTranDate(), t.getTranTime(), t.getInvoice(), t.getUnit(), t.getDriverName(),
            t.getOdometer(), t.getLocationName(), t.getCity(), t.getStateProv(), String.valueOf(t.getFees()),
            t.getItem(), String.valueOf(t.getUnitPrice()), String.valueOf(t.getDiscPPU()), String.valueOf(t.getDiscCost()),
            String.valueOf(t.getQty()), String.valueOf(t.getDiscAmt()), t.getDiscType(), String.valueOf(t.getAmt()),
            t.getDb(), t.getCurrency()
        };

        String[] labels = {
            "Card #", "Tran Date", "Tran Time", "Invoice", "Unit", "Driver Name", "Odometer", "Location Name", "City", "State/ Prov", "Fees",
            "Item", "Unit Price", "Disc PPU", "Disc Cost", "Qty", "Disc Amt", "Disc Type", "Amt", "DB", "Currency"
        };

        GridPane grid = new GridPane();
        grid.setVgap(8); 
        grid.setHgap(10); 
        grid.setPadding(new Insets(10));
        
        // Create a scrollable pane for the form
        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        
        for (int i = 0; i < labels.length; i++) {
            fields[i] = new TextField(values[i] == null ? "" : values[i]);
            fields[i].setPrefWidth(200);
            grid.add(new Label(labels[i] + ":"), 0, i);
            grid.add(fields[i], 1, i);
        }

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setPrefSize(400, 500);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(isAdd && fields[3].getText().trim().isEmpty());
        fields[3].textProperty().addListener((obs, oldV, newV) -> okBtn.setDisable(newV.trim().isEmpty()));

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String[] vals = new String[21];
                for (int i = 0; i < fields.length; i++) vals[i] = fields[i].getText().trim();

                int employeeId = 0;
                List<Employee> matches = employeeDAO.getAll().stream()
                    .filter(e -> e.getName().equalsIgnoreCase(vals[5]) && e.getTruckId().equalsIgnoreCase(vals[4]))
                    .collect(Collectors.toList());
                if (!matches.isEmpty()) {
                    employeeId = matches.get(0).getId();
                    logger.debug("Matched employee {} for driver {} unit {}", employeeId, vals[5], vals[4]);
                } else {
                    logger.debug("No employee match found for driver {} unit {}", vals[5], vals[4]);
                }

                FuelTransaction tx = new FuelTransaction(
                    t == null ? 0 : t.getId(),
                    vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6], vals[7], vals[8], vals[9],
                    parseDouble(vals[10]), vals[11], parseDouble(vals[12]), parseDouble(vals[13]),
                    parseDouble(vals[14]), parseDouble(vals[15]), parseDouble(vals[16]), vals[17], parseDouble(vals[18]),
                    vals[19], vals[20], employeeId
                );
                if (isAdd) {
                    logger.info("Adding new fuel transaction - Invoice: {}", vals[3]);
                    dao.add(tx);
                } else {
                    logger.info("Updating fuel transaction - Invoice: {}", vals[3]);
                    dao.update(tx);
                }
                reload();
                notifyFuelDataChanged();
                return tx;
            }
            return null;
        });

        dialog.showAndWait();
    }

    private List<FuelTransaction> parseCSV(File file) throws IOException {
        logger.info("Parsing CSV file: {}", file.getName());
        List<FuelTransaction> list = new ArrayList<>();
        List<Employee> employees = employeeDAO.getAll();
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String[] headers = br.readLine().split(",");
            logger.debug("CSV headers: {}", Arrays.toString(headers));
            
            // Get column indices using configuration
            Map<String, Integer> columnIndices = importConfig.getColumnIndices(headers);
            logger.debug("Column indices from config: {}", columnIndices);
            
            String line;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                String[] arr = line.split(",", -1);
                if (arr.length < 21) {
                    logger.warn("Line {} has insufficient columns ({}), skipping", lineNumber, arr.length);
                    continue;
                }
                
                // Extract values using configured column indices
                String cardNumber = getValueFromArray(arr, columnIndices, "Card Number");
                String tranDate = getValueFromArray(arr, columnIndices, "Transaction Date");
                String tranTime = getValueFromArray(arr, columnIndices, "Transaction Time");
                String invoice = getValueFromArray(arr, columnIndices, "Invoice");
                String unit = getValueFromArray(arr, columnIndices, "Unit");
                String driverName = getValueFromArray(arr, columnIndices, "Driver Name");
                String odometer = getValueFromArray(arr, columnIndices, "Odometer");
                String locationName = getValueFromArray(arr, columnIndices, "Location Name");
                String city = getValueFromArray(arr, columnIndices, "City");
                String stateProv = getValueFromArray(arr, columnIndices, "State/Province");
                double fees = parseDouble(getValueFromArray(arr, columnIndices, "Fees"));
                String item = getValueFromArray(arr, columnIndices, "Item");
                double unitPrice = parseDouble(getValueFromArray(arr, columnIndices, "Unit Price"));
                double discPPU = parseDouble(getValueFromArray(arr, columnIndices, "Discount PPU"));
                double discCost = parseDouble(getValueFromArray(arr, columnIndices, "Discount Cost"));
                double qty = parseDouble(getValueFromArray(arr, columnIndices, "Quantity"));
                double discAmt = parseDouble(getValueFromArray(arr, columnIndices, "Discount Amount"));
                String discType = getValueFromArray(arr, columnIndices, "Discount Type");
                double amt = parseDouble(getValueFromArray(arr, columnIndices, "Amount"));
                String db = getValueFromArray(arr, columnIndices, "DB");
                String currency = getValueFromArray(arr, columnIndices, "Currency");
                
                // Find employee ID
                int employeeId = 0;
                for (Employee e : employees) {
                    if (e.getName().equalsIgnoreCase(driverName) && e.getTruckId().equalsIgnoreCase(unit)) {
                        employeeId = e.getId();
                        break;
                    }
                }
                
                FuelTransaction t = new FuelTransaction(
                    0, cardNumber, tranDate, tranTime, invoice, unit, driverName, odometer,
                    locationName, city, stateProv, fees, item, unitPrice, discPPU, discCost,
                    qty, discAmt, discType, amt, db, currency, employeeId
                );
                list.add(t);
            }
            logger.info("Parsed {} transactions from CSV", list.size());
        }
        return list;
    }

    private List<FuelTransaction> parseXLSX(File file) throws IOException {
        logger.info("Parsing XLSX file: {}", file.getName());
        List<FuelTransaction> list = new ArrayList<>();
        List<Employee> employees = employeeDAO.getAll();
        
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) {
                logger.warn("Empty XLSX file");
                return list;
            }
            
            Row headerRow = rowIterator.next();
            logger.debug("XLSX has {} columns", headerRow.getLastCellNum());
            
            // Create header mapping for XLSX
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.getCell(i);
                if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    String header = cell.getStringCellValue().trim().toLowerCase();
                    headerMap.put(header, i);
                }
            }
            
            logger.debug("XLSX header mapping: {}", headerMap);
            
            // Get column indices using configuration
            Map<String, Integer> columnIndices = importConfig.getColumnIndices(headerMap);
            logger.debug("Column indices from config: {}", columnIndices);
            
            int rowNumber = 1;
            while (rowIterator.hasNext()) {
                rowNumber++;
                Row row = rowIterator.next();
                
                // Extract values using configured column indices
                String cardNumber = getValueFromRow(row, columnIndices, "Card Number");
                String tranDate = getValueFromRow(row, columnIndices, "Transaction Date");
                String tranTime = getValueFromRow(row, columnIndices, "Transaction Time");
                String invoice = getValueFromRow(row, columnIndices, "Invoice");
                String unit = getValueFromRow(row, columnIndices, "Unit");
                String driverName = getValueFromRow(row, columnIndices, "Driver Name");
                String odometer = getValueFromRow(row, columnIndices, "Odometer");
                String locationName = getValueFromRow(row, columnIndices, "Location Name");
                String city = getValueFromRow(row, columnIndices, "City");
                String stateProv = getValueFromRow(row, columnIndices, "State/Province");
                double fees = parseDouble(getValueFromRow(row, columnIndices, "Fees"));
                String item = getValueFromRow(row, columnIndices, "Item");
                double unitPrice = parseDouble(getValueFromRow(row, columnIndices, "Unit Price"));
                double discPPU = parseDouble(getValueFromRow(row, columnIndices, "Discount PPU"));
                double discCost = parseDouble(getValueFromRow(row, columnIndices, "Discount Cost"));
                double qty = parseDouble(getValueFromRow(row, columnIndices, "Quantity"));
                double discAmt = parseDouble(getValueFromRow(row, columnIndices, "Discount Amount"));
                String discType = getValueFromRow(row, columnIndices, "Discount Type");
                double amt = parseDouble(getValueFromRow(row, columnIndices, "Amount"));
                String db = getValueFromRow(row, columnIndices, "DB");
                String currency = getValueFromRow(row, columnIndices, "Currency");
                
                // Skip empty rows
                if (invoice.isEmpty()) {
                    logger.debug("Skipping empty row {}", rowNumber);
                    continue;
                }
                
                // Find employee ID
                int employeeId = 0;
                for (Employee e : employees) {
                    if (e.getName().equalsIgnoreCase(driverName) && e.getTruckId().equalsIgnoreCase(unit)) {
                        employeeId = e.getId();
                        break;
                    }
                }
                
                FuelTransaction t = new FuelTransaction(
                    0, cardNumber, tranDate, tranTime, invoice, unit, driverName, odometer,
                    locationName, city, stateProv, fees, item, unitPrice, discPPU, discCost,
                    qty, discAmt, discType, amt, db, currency, employeeId
                );
                list.add(t);
            }
            logger.info("Parsed {} transactions from XLSX", list.size());
        }
        return list;
    }
    
    private String getValueFromArray(String[] arr, Map<String, Integer> columnIndices, String fieldName) {
        Integer index = columnIndices.get(fieldName);
        if (index != null && index >= 0 && index < arr.length) {
            return arr[index].trim();
        }
        return "";
    }
    
    private String getValueFromRow(Row row, Map<String, Integer> columnIndices, String fieldName) {
        Integer index = columnIndices.get(fieldName);
        if (index != null && index >= 0) {
            return getCellValue(row, index);
        }
        return "";
    }

    private String getCellValue(Row row, int colIndex) {
        if (colIndex < 0) return "";
        
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        switch (cell.getCellType()) {
            case STRING: 
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    return date.toString();
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.valueOf((long) value);
                    }
                    return String.valueOf(value);
                }
            case BOOLEAN: 
                return String.valueOf(cell.getBooleanCellValue());
            default: 
                return "";
        }
    }

    private double parseDouble(String s) {
        try { 
            return Double.parseDouble(s); 
        } catch (Exception e) { 
            return 0; 
        }
    }

    private void showConfigurationDialog() {
        Stage configStage = new Stage();
        configStage.setTitle("Fuel Import Configuration");
        configStage.initModality(Modality.APPLICATION_MODAL);
        configStage.initOwner(getScene().getWindow());

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label titleLabel = new Label("Configure Fuel Import Column Mapping");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Create configuration form
        GridPane configGrid = new GridPane();
        configGrid.setHgap(15);
        configGrid.setVgap(10);

        // Create text fields for each column mapping
        Map<String, TextField> fieldMap = new LinkedHashMap<>();
        int row = 0;

        for (Map.Entry<String, String> entry : importConfig.getColumnMappings().entrySet()) {
            String fieldName = entry.getKey();
            String currentMapping = entry.getValue();

            Label label = new Label(fieldName + ":");
            label.setPrefWidth(120);
            
            TextField textField = new TextField(currentMapping);
            textField.setPrefWidth(200);
            textField.setPromptText("Enter column header name");
            
            fieldMap.put(fieldName, textField);
            
            configGrid.add(label, 0, row);
            configGrid.add(textField, 1, row);
            row++;
        }

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button saveBtn = new Button("Save Configuration");
        saveBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> {
            // Update configuration
            for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
                String fieldName = entry.getKey();
                String newMapping = entry.getValue().getText().trim();
                if (!newMapping.isEmpty()) {
                    importConfig.setColumnMapping(fieldName, newMapping);
                }
            }
            
            // Save configuration
            importConfig.save();
            
            showAlert(Alert.AlertType.INFORMATION, "Configuration Saved", 
                "Import configuration has been saved successfully.");
            
            configStage.close();
        });

        Button resetBtn = new Button("Reset to Defaults");
        resetBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        resetBtn.setOnAction(e -> {
            importConfig = FuelImportConfig.loadDefault();
            // Update text fields
            for (Map.Entry<String, String> entry : importConfig.getColumnMappings().entrySet()) {
                String fieldName = entry.getKey();
                String mapping = entry.getValue();
                TextField textField = fieldMap.get(fieldName);
                if (textField != null) {
                    textField.setText(mapping);
                }
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> configStage.close());

        buttonBox.getChildren().addAll(saveBtn, resetBtn, cancelBtn);

        // Help text
        Label helpLabel = new Label(
            "Configure the column header names that match your import files.\n" +
            "The system will look for these exact header names (case-insensitive) in your CSV/XLSX files.\n" +
            "Leave empty to skip that field during import."
        );
        helpLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        helpLabel.setWrapText(true);

        root.getChildren().addAll(titleLabel, configGrid, helpLabel, buttonBox);

        // Wrap root in a ScrollPane for vertical scrolling
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportHeight(500);

        Scene scene = new Scene(scrollPane, 500, 600);
        configStage.setScene(scene);
        configStage.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Create header section with title and description
     */
    private VBox createHeaderSection() {
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));
        
        Label title = new Label("Fuel Transaction Management");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#1976D2"));
        
        Label subtitle = new Label("Import and manage fuel transactions from CSV/XLSX files");
        subtitle.setFont(Font.font("Arial", 14));
        subtitle.setTextFill(Color.web("#666666"));
        
        header.getChildren().addAll(title, subtitle);
        return header;
    }
    
    /**
     * Create action bar with styled buttons
     */
    private HBox createActionBar() {
        HBox actionBar = new HBox(12);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setPadding(new Insets(15));
        actionBar.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        // Create buttons with icons
        Button addBtn = createStyledButton("➕ Add", "#4CAF50");
        Button editBtn = createStyledButton("✏️ Edit", "#2196F3");
        Button deleteBtn = createStyledButton("🗑️ Delete", "#F44336");
        Button importBtn = createStyledButton("📥 Import CSV/XLSX", "#FF9800");
        Button configureBtn = createStyledButton("⚙️ Configure", "#9C27B0");
        Button refreshBtn = createStyledButton("🔄 Refresh", "#00BCD4");
        
        // Store button references for event handlers
        this.addBtn = addBtn;
        this.editBtn = editBtn;
        this.deleteBtn = deleteBtn;
        this.importBtn = importBtn;
        this.configureBtn = configureBtn;
        this.refreshBtn = refreshBtn;
        
        actionBar.getChildren().addAll(addBtn, editBtn, deleteBtn, 
                                      new Separator(Orientation.VERTICAL),
                                      importBtn, configureBtn, refreshBtn);
        
        return actionBar;
    }
    
    /**
     * Create filter section with date and driver filters
     */
    private VBox createFilterSection() {
        VBox filterBox = new VBox(10);
        filterBox.setPadding(new Insets(15));
        filterBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label filterTitle = new Label("Filters");
        filterTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        filterTitle.setTextFill(Color.web("#1976D2"));
        
        Label filterSubtitle = new Label("Showing last 14 days by default for optimal performance");
        filterSubtitle.setFont(Font.font("Arial", 12));
        filterSubtitle.setTextFill(Color.web("#666666"));
        filterSubtitle.setStyle("-fx-font-style: italic;");
        
        // Date filter row
        HBox dateFilterRow = new HBox(15);
        dateFilterRow.setAlignment(Pos.CENTER_LEFT);
        
        Label startLabel = new Label("Start Date:");
        Label endLabel = new Label("End Date:");
        startDatePicker.setPrefWidth(150);
        endDatePicker.setPrefWidth(150);
        
        dateFilterRow.getChildren().addAll(startLabel, startDatePicker, endLabel, endDatePicker);
        
        // Driver filter row
        HBox driverFilterRow = new HBox(15);
        driverFilterRow.setAlignment(Pos.CENTER_LEFT);
        
        Label driverLabel = new Label("Driver:");
        driverSearchField.setPromptText("Type to search driver...");
        driverSearchField.setPrefWidth(200);
        
        driverFilterCombo.setPromptText("Select driver");
        driverFilterCombo.setPrefWidth(200);
        driverFilterCombo.setEditable(true);
        
        driverFilterRow.getChildren().addAll(driverLabel, driverSearchField, driverFilterCombo);
        
        // Filter buttons
        HBox filterButtons = new HBox(10);
        filterButtons.setAlignment(Pos.CENTER_LEFT);
        
        Button applyFilterBtn = createStyledButton("🔍 Apply Filters", "#4CAF50");
        Button clearFilterBtn = createStyledButton("❌ Clear Filters", "#FF5722");
        Button showAllBtn = createStyledButton("📊 Show All Data", "#FF9800");
        
        filterButtons.getChildren().addAll(applyFilterBtn, clearFilterBtn, showAllBtn);
        
        // Set up filter actions
        setupFilterActions(applyFilterBtn, clearFilterBtn, showAllBtn);
        
        filterBox.getChildren().addAll(filterTitle, filterSubtitle, new Separator(), 
                                      dateFilterRow, driverFilterRow, filterButtons);
        
        return filterBox;
    }
    
    /**
     * Create status bar with progress and record count
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-background-color: white; -fx-background-radius: 5px;");
        
        statusLabel.setText("Ready");
        statusLabel.setFont(Font.font("Arial", 12));
        
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);
        
        recordCountLabel.setText("0 records");
        recordCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusBar.getChildren().addAll(statusLabel, progressBar, spacer, recordCountLabel);
        
        return statusBar;
    }
    
    /**
     * Create enhanced table with better styling
     */
    private TableView<FuelTransaction> createEnhancedTable() {
        TableView<FuelTransaction> table = new TableView<>(filteredData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setStyle("-fx-background-color: white; -fx-background-radius: 10px;");
        
        // Configure columns
        TableColumn<FuelTransaction, String> colDate = new TableColumn<>("Tran Date");
        colDate.setCellValueFactory(new PropertyValueFactory<>("tranDate"));
        colDate.setPrefWidth(100);
        
        TableColumn<FuelTransaction, String> colTime = new TableColumn<>("Tran Time");
        colTime.setCellValueFactory(new PropertyValueFactory<>("tranTime"));
        colTime.setPrefWidth(80);
        
        TableColumn<FuelTransaction, String> colInv = new TableColumn<>("Invoice");
        colInv.setCellValueFactory(new PropertyValueFactory<>("invoice"));
        colInv.setPrefWidth(80);
        
        TableColumn<FuelTransaction, String> colUnit = new TableColumn<>("Unit");
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unit"));
        colUnit.setPrefWidth(60);
        
        TableColumn<FuelTransaction, String> colDriver = new TableColumn<>("Driver Name");
        colDriver.setCellValueFactory(new PropertyValueFactory<>("driverName"));
        colDriver.setPrefWidth(150);
        
        TableColumn<FuelTransaction, String> colLoc = new TableColumn<>("Location Name");
        colLoc.setCellValueFactory(new PropertyValueFactory<>("locationName"));
        colLoc.setPrefWidth(200);
        
        TableColumn<FuelTransaction, String> colState = new TableColumn<>("State/ Prov");
        colState.setCellValueFactory(new PropertyValueFactory<>("stateProv"));
        colState.setPrefWidth(100);
        
        TableColumn<FuelTransaction, Number> colFees = createNumberColumn("Fees", "fees", 80);
        TableColumn<FuelTransaction, Number> colAmt = createNumberColumn("Amount", "amt", 100);
        
        @SuppressWarnings("unchecked")
        TableColumn<FuelTransaction, ?>[] columns = new TableColumn[] {
            colDate, colTime, colInv, colUnit, colDriver, 
            colLoc, colState, colFees, colAmt
        };
        table.getColumns().addAll(columns);
        
        // Add row styling
        table.setRowFactory(tv -> {
            TableRow<FuelTransaction> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #F8F8F8;");
                    } else {
                        row.setStyle("-fx-background-color: white;");
                    }
                }
            });
            return row;
        });
        
        // Store table reference
        this.table = table;
        
        return table;
    }
    
    /**
     * Create styled button
     */
    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-cursor: hand; " +
            "-fx-background-radius: 5px; -fx-padding: 8 15;", color));
        
        // Add hover effect
        btn.setOnMouseEntered(e -> 
            btn.setStyle(btn.getStyle() + "-fx-opacity: 0.8;"));
        btn.setOnMouseExited(e -> 
            btn.setStyle(btn.getStyle().replace("-fx-opacity: 0.8;", "")));
        
        return btn;
    }
    
    /**
     * Create number column with formatting
     */
    private TableColumn<FuelTransaction, Number> createNumberColumn(String title, String property, int width) {
        TableColumn<FuelTransaction, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        column.setCellFactory(col -> new TableCell<FuelTransaction, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", item.doubleValue()));
                }
            }
        });
        return column;
    }
    
    /**
     * Setup filter actions
     */
    private void setupFilterActions(Button applyBtn, Button clearBtn, Button showAllBtn) {
        applyBtn.setOnAction(e -> applyFilters());
        clearBtn.setOnAction(e -> clearFilters());
        showAllBtn.setOnAction(e -> showAllData());
        
        // Setup driver search field
        driverSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                filterDriverComboBox(newVal);
            }
        });
    }
    
    /**
     * Apply all filters
     */
    private void applyFilters() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        String selectedDriver = driverFilterCombo.getValue();
        
        filteredData.setPredicate(transaction -> {
            // Date filter
            boolean dateMatch = true;
            if (start != null || end != null) {
                try {
                    LocalDate tranDate = LocalDate.parse(transaction.getTranDate());
                    if (start != null && tranDate.isBefore(start)) dateMatch = false;
                    if (end != null && tranDate.isAfter(end)) dateMatch = false;
                } catch (Exception e) {
                    // Include if date parsing fails
                }
            }
            
            // Driver filter
            boolean driverMatch = true;
            if (selectedDriver != null && !selectedDriver.trim().isEmpty()) {
                driverMatch = transaction.getDriverName().toLowerCase()
                    .contains(selectedDriver.toLowerCase());
            }
            
            return dateMatch && driverMatch;
        });
        
        updateRecordCount();
        statusLabel.setText("Filters applied");
    }
    
    /**
     * Set default date range to last 14 days for performance
     */
    private void setDefaultDateRange() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(14);
        
        startDatePicker.setValue(startDate);
        endDatePicker.setValue(endDate);
        
        logger.info("Set default date range: {} to {} (last 14 days)", startDate, endDate);
    }
    
    /**
     * Clear all filters (but keep default 14-day range)
     */
    private void clearFilters() {
        driverSearchField.clear();
        driverFilterCombo.setValue(null);
        
        // Reset to default 14-day range instead of clearing dates
        setDefaultDateRange();
        
        // Reload data with default range
        reload();
        
        statusLabel.setText("Filters cleared - showing last 14 days");
    }
    
    /**
     * Show all data (removes date filters for full access)
     */
    private void showAllData() {
        // Clear all filters including dates
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        driverSearchField.clear();
        driverFilterCombo.setValue(null);
        
        // Reload all data
        reload();
        
        statusLabel.setText("Showing all data - performance may be slower");
        logger.info("Switched to show all data mode");
    }
    
    /**
     * Filter driver combo box based on search text
     */
    private void filterDriverComboBox(String searchText) {
        Set<String> uniqueDrivers = allData.stream()
            .map(FuelTransaction::getDriverName)
            .filter(name -> name.toLowerCase().contains(searchText.toLowerCase()))
            .collect(Collectors.toCollection(TreeSet::new));
        
        driverFilterCombo.getItems().setAll(uniqueDrivers);
        if (!driverFilterCombo.isShowing()) {
            driverFilterCombo.show();
        }
    }
    
    /**
     * Update record count label
     */
    private void updateRecordCount() {
        int showing = filteredData.size();
        int total = allData.size();
        
        // Check if we're in default 14-day mode
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        LocalDate defaultStart = LocalDate.now().minusDays(14);
        LocalDate defaultEnd = LocalDate.now();
        
        boolean isDefaultRange = (start != null && end != null && 
                                 start.equals(defaultStart) && end.equals(defaultEnd));
        
        if (isDefaultRange && (driverFilterCombo.getValue() == null || driverFilterCombo.getValue().trim().isEmpty())) {
            recordCountLabel.setText(String.format("%d records (last 14 days)", showing));
        } else {
            recordCountLabel.setText(String.format("%d of %d records (filtered)", showing, total));
        }
    }
    
    // Button references for event handlers
    private Button addBtn, editBtn, deleteBtn, importBtn, configureBtn, refreshBtn;
    private TableView<FuelTransaction> table;
}