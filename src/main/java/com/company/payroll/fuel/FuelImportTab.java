package com.company.payroll.fuel;

import javafx.scene.Node;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class FuelImportTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(FuelImportTab.class);

    private final FuelTransactionDAO dao = new FuelTransactionDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ObservableList<FuelTransaction> allData = FXCollections.observableArrayList();
    private final FilteredList<FuelTransaction> filteredData;
    private final DatePicker startDatePicker = new DatePicker();
    private final DatePicker endDatePicker = new DatePicker();
    
    // Add interface for fuel data changes
    public interface FuelDataChangeListener {
        void onFuelDataChanged();
    }
    
    private final List<FuelDataChangeListener> fuelDataChangeListeners = new ArrayList<>();

    public FuelImportTab() {
        logger.info("Initializing FuelImportTab");
        setPadding(new Insets(10));

        // Initialize filtered list
        filteredData = new FilteredList<>(allData, p -> true);

        TableView<FuelTransaction> table = makeTable();

        // Top controls
        VBox topControls = new VBox(8);
        
        // Buttons
        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button importBtn = new Button("Import CSV/XLSX");
        Button refreshBtn = new Button("Refresh");

        HBox actions = new HBox(12, addBtn, editBtn, deleteBtn, importBtn, refreshBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(8,0,10,0));

        // Date range filter controls
        Label startLabel = new Label("Start Date:");
        Label endLabel = new Label("End Date:");
        Button searchBtn = new Button("Search");
        Button clearBtn = new Button("Clear Filter");
        
        startDatePicker.setPrefWidth(120);
        endDatePicker.setPrefWidth(120);
        
        HBox dateFilterBox = new HBox(10, startLabel, startDatePicker, endLabel, endDatePicker, searchBtn, clearBtn);
        dateFilterBox.setAlignment(Pos.CENTER_LEFT);
        dateFilterBox.setPadding(new Insets(0, 0, 10, 0));
        
        topControls.getChildren().addAll(actions, dateFilterBox);

        // Search button action
        searchBtn.setOnAction(e -> {
            LocalDate start = startDatePicker.getValue();
            LocalDate end = endDatePicker.getValue();
            
            if (start != null && end != null && start.isAfter(end)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Invalid Date Range");
                alert.setHeaderText(null);
                alert.setContentText("Start date must be before or equal to end date.");
                alert.showAndWait();
                return;
            }
            
            applyDateFilter(start, end);
        });
        
        // Clear filter button action
        clearBtn.setOnAction(e -> {
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
            filteredData.setPredicate(p -> true);
            logger.info("Date filter cleared");
        });

        addBtn.setOnAction(e -> {
            logger.info("Add fuel transaction button clicked");
            showEditDialog(null, true);
        });
        editBtn.setOnAction(e -> {
            FuelTransaction t = table.getSelectionModel().getSelectedItem();
            if (t != null) {
                logger.info("Edit fuel transaction button clicked for invoice: {}", t.getInvoice());
                showEditDialog(t, false);
            }
        });
        deleteBtn.setOnAction(e -> {
            FuelTransaction t = table.getSelectionModel().getSelectedItem();
            if (t != null) {
                logger.info("Delete fuel transaction button clicked for invoice: {}", t.getInvoice());
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete invoice " + t.getInvoice() + "?", ButtonType.YES, ButtonType.NO);
                a.setHeaderText("Confirm Delete");
                a.showAndWait().ifPresent(b -> {
                    if (b == ButtonType.YES) {
                        logger.info("User confirmed deletion of invoice: {}", t.getInvoice());
                        dao.delete(t.getId());
                        reload();
                        notifyFuelDataChanged();
                    } else {
                        logger.info("User cancelled deletion of invoice: {}", t.getInvoice());
                    }
                });
            }
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            reload();
        });

        importBtn.setOnAction(e -> {
            logger.info("Import button clicked");
            FileChooser fc = new FileChooser();
            fc.setTitle("Import Fuel Transactions");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
            );
            File file = fc.showOpenDialog(getScene().getWindow());
            if (file != null) {
                logger.info("Import file selected: {}", file.getAbsolutePath());
                int imported = 0, skipped = 0;
                String lower = file.getName().toLowerCase();
                try {
                    List<FuelTransaction> importedList = null;
                    if (lower.endsWith(".csv")) {
                        logger.info("Processing CSV file");
                        importedList = parseCSV(file);
                    } else if (lower.endsWith(".xlsx")) {
                        logger.info("Processing XLSX file");
                        importedList = parseXLSX(file);
                    }
                    if (importedList != null && !importedList.isEmpty()) {
                        logger.info("Parsed {} transactions from file", importedList.size());
                        for (FuelTransaction tx : importedList) {
                            if (dao.exists(
                                    tx.getInvoice(),
                                    tx.getTranDate(),
                                    tx.getLocationName(),
                                    tx.getAmt())) {
                                logger.debug("Skipping duplicate transaction - Invoice: {}", tx.getInvoice());
                                skipped++;
                                continue;
                            }
                            dao.add(tx);
                            imported++;
                        }
                        reload();
                        if (imported > 0) {
                            notifyFuelDataChanged();
                        }
                        logger.info("Import complete - Imported: {}, Skipped: {}", imported, skipped);
                        new Alert(Alert.AlertType.INFORMATION,
                                "Import complete!\nImported: " + imported + "\nSkipped (duplicates): " + skipped).showAndWait();
                    } else {
                        logger.warn("No transactions found in import file");
                    }
                } catch (Exception ex) {
                    logger.error("Import failed: {}", ex.getMessage(), ex);
                    new Alert(Alert.AlertType.ERROR, "Import failed: " + ex.getMessage()).showAndWait();
                }
            } else {
                logger.info("Import cancelled - no file selected");
            }
        });

        setTop(topControls);
        setCenter(table);
        reload();
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
        allData.setAll(dao.getAll());
        logger.info("Loaded {} fuel transactions", allData.size());
    }

    private TableView<FuelTransaction> makeTable() {
        TableView<FuelTransaction> table = new TableView<>(filteredData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Only add the columns that should be visible
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

        TableColumn<FuelTransaction, Number> colFees = new TableColumn<>("Fees");
        colFees.setCellValueFactory(new PropertyValueFactory<>("fees"));
        colFees.setCellFactory(col -> new TableCell<FuelTransaction, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item.doubleValue()));
                }
            }
        });
        colFees.setPrefWidth(80);

        TableColumn<FuelTransaction, Number> colAmt = new TableColumn<>("Amt");
        colAmt.setCellValueFactory(new PropertyValueFactory<>("amt"));
        colAmt.setCellFactory(col -> new TableCell<FuelTransaction, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item.doubleValue()));
                }
            }
        });
        colAmt.setPrefWidth(100);

        // Add only the specified columns in order
        table.getColumns().addAll(colDate, colTime, colInv, colUnit, colDriver, colLoc, colState, colFees, colAmt);
        
        return table;
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
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < headers.length; i++)
                map.put(headers[i].trim().toLowerCase(), i);
            
            logger.debug("CSV headers: {}", Arrays.toString(headers));
            
            String line;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                String[] arr = line.split(",", -1);
                if (arr.length < 21) {
                    logger.warn("Line {} has insufficient columns ({}), skipping", lineNumber, arr.length);
                    continue;
                }
                String driverName = arr[5];
                String unit = arr[4];
                int employeeId = 0;
                for (Employee e : employees) {
                    if (e.getName().equalsIgnoreCase(driverName) && e.getTruckId().equalsIgnoreCase(unit)) {
                        employeeId = e.getId();
                        break;
                    }
                }
                FuelTransaction t = new FuelTransaction(
                    0,
                    arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9],
                    parseDouble(arr[10]), arr[11], parseDouble(arr[12]), parseDouble(arr[13]), parseDouble(arr[14]),
                    parseDouble(arr[15]), parseDouble(arr[16]), arr[17], parseDouble(arr[18]), arr[19], arr[20], employeeId
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
            
            // Create column mapping for XLSX
            Map<String, Integer> columnMap = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.getCell(i);
                if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    String header = cell.getStringCellValue().trim().toLowerCase();
                    columnMap.put(header, i);
                }
            }
            
            logger.debug("XLSX column mapping: {}", columnMap);
            
            int rowNumber = 1;
            while (rowIterator.hasNext()) {
                rowNumber++;
                Row row = rowIterator.next();
                String[] arr = new String[21];
                
                // Fill array with empty strings first
                Arrays.fill(arr, "");
                
                // Map known columns
                arr[0] = getCellValue(row, columnMap.getOrDefault("card #", 0));
                arr[1] = getCellValue(row, columnMap.getOrDefault("tran date", 1));
                arr[2] = getCellValue(row, columnMap.getOrDefault("tran time", 2));
                arr[3] = getCellValue(row, columnMap.getOrDefault("invoice", 3));
                arr[4] = getCellValue(row, columnMap.getOrDefault("unit", 4));
                arr[5] = getCellValue(row, columnMap.getOrDefault("driver name", 5));
                arr[6] = getCellValue(row, columnMap.getOrDefault("odometer", 6));
                arr[7] = getCellValue(row, columnMap.getOrDefault("location name", 7));
                arr[8] = getCellValue(row, columnMap.getOrDefault("city", 8));
                arr[9] = getCellValue(row, columnMap.getOrDefault("state/ prov", 9));
                arr[10] = getCellValue(row, columnMap.getOrDefault("fees", 10));
                arr[11] = getCellValue(row, columnMap.getOrDefault("item", 11));
                arr[12] = getCellValue(row, columnMap.getOrDefault("unit price", 12));
                arr[13] = getCellValue(row, columnMap.getOrDefault("disc ppu", 13));
                arr[14] = getCellValue(row, columnMap.getOrDefault("disc cost", 14));
                arr[15] = getCellValue(row, columnMap.getOrDefault("qty", 15));
                arr[16] = getCellValue(row, columnMap.getOrDefault("disc amt", 16));
                arr[17] = getCellValue(row, columnMap.getOrDefault("disc type", 17));
                arr[18] = getCellValue(row, columnMap.getOrDefault("amt", 18));
                arr[19] = getCellValue(row, columnMap.getOrDefault("db", 19));
                arr[20] = getCellValue(row, columnMap.getOrDefault("currency", 20));
                
                // Skip empty rows
                if (arr[3].isEmpty()) {
                    logger.debug("Skipping empty row {}", rowNumber);
                    continue;
                }
                
                String driverName = arr[5];
                String unit = arr[4];
                int employeeId = 0;
                for (Employee e : employees) {
                    if (e.getName().equalsIgnoreCase(driverName) && e.getTruckId().equalsIgnoreCase(unit)) {
                        employeeId = e.getId();
                        break;
                    }
                }
                FuelTransaction t = new FuelTransaction(
                    0,
                    arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9],
                    parseDouble(arr[10]), arr[11], parseDouble(arr[12]), parseDouble(arr[13]), parseDouble(arr[14]),
                    parseDouble(arr[15]), parseDouble(arr[16]), arr[17], parseDouble(arr[18]), arr[19], arr[20], employeeId
                );
                list.add(t);
            }
            logger.info("Parsed {} transactions from XLSX", list.size());
        }
        return list;
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
}