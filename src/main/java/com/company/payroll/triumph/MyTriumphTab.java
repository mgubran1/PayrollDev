package com.company.payroll.triumph;

import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MyTriumphTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(MyTriumphTab.class);
    
    private final MyTriumphDAO triumphDAO = new MyTriumphDAO();
    private final LoadDAO loadDAO = new LoadDAO();

    private final ObservableList<MyTriumphRecord> allRecords = FXCollections.observableArrayList();
    private final ObservableList<Load> deliveredLoads = FXCollections.observableArrayList();

    private final TableView<MyTriumphRecord> table = new TableView<>();
    private final Label sumLabel = new Label();
    private final TextField searchField = new TextField();
    private final ComboBox<String> searchCombo = new ComboBox<>();
    
    // Date range controls for unbilled loads
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private CheckBox useCustomDateRange;

    public MyTriumphTab() {
        super("MyTriumph Audit");
        logger.info("Initializing MyTriumphTab");

        reloadAll();

        setContent(buildPanel());
        logger.info("MyTriumphTab initialization complete");
    }

    private Node buildPanel() {
        logger.debug("Building MyTriumph panel");
        searchField.setPromptText("Search by PO, DTR_NAME, or INV_DATE...");
        searchCombo.getItems().addAll("PO", "DTR_NAME", "INV_DATE", "INVOICE#");
        searchCombo.setValue("PO");

        HBox searchBox = new HBox(8, new Label("Search:"), searchField, searchCombo);
        searchBox.setPadding(new Insets(8));

        Button importBtn = ModernButtonStyles.createPrimaryButton("📥 Import .xlsx");
        Button addBtn = ModernButtonStyles.createSuccessButton("➕ Add");
        Button editBtn = ModernButtonStyles.createWarningButton("✏️ Edit");
        Button removeBtn = ModernButtonStyles.createDangerButton("🗑️ Remove");
        Button saveBtn = ModernButtonStyles.createSuccessButton("💾 Save");
        Button refreshBtn = ModernButtonStyles.createPrimaryButton("🔄 Refresh");
        Button syncFromLoadsBtn = ModernButtonStyles.createInfoButton("🔄 Sync from Loads");
        Button copyUnbilledBtn = ModernButtonStyles.createSecondaryButton("📋 Copy Unbilled for Excel");

        HBox buttonBox = new HBox(8, importBtn, addBtn, editBtn, removeBtn, saveBtn, refreshBtn, syncFromLoadsBtn, copyUnbilledBtn);
        buttonBox.setPadding(new Insets(8));

        // Table setup
        TableColumn<MyTriumphRecord, String> colDTR = new TableColumn<>("DTR_NAME");
        colDTR.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getDtrName()));
        colDTR.setCellFactory(TextFieldTableCell.forTableColumn());
        colDTR.setOnEditCommit(evt -> {
            MyTriumphRecord rec = evt.getRowValue();
            String old = rec.getDtrName();
            rec.setDtrName(evt.getNewValue());
            logger.debug("Edited DTR_NAME from '{}' to '{}'", old, evt.getNewValue());
        });
        colDTR.setPrefWidth(150);

        TableColumn<MyTriumphRecord, String> colINV = new TableColumn<>("INVOICE#");
        colINV.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getInvoiceNumber()));
        colINV.setCellFactory(TextFieldTableCell.forTableColumn());
        colINV.setOnEditCommit(evt -> {
            MyTriumphRecord rec = evt.getRowValue();
            String old = rec.getInvoiceNumber();
            rec.setInvoiceNumber(evt.getNewValue());
            logger.debug("Edited INVOICE# from '{}' to '{}'", old, evt.getNewValue());
        });
        colINV.setPrefWidth(100);

        TableColumn<MyTriumphRecord, String> colDATE = new TableColumn<>("INV_DATE");
        colDATE.setCellValueFactory(param -> new ReadOnlyStringWrapper(
                param.getValue().getInvoiceDate() != null ? param.getValue().getInvoiceDate().toString() : ""));
        colDATE.setCellFactory(TextFieldTableCell.forTableColumn());
        colDATE.setOnEditCommit(evt -> {
            MyTriumphRecord rec = evt.getRowValue();
            String old = rec.getInvoiceDate() != null ? rec.getInvoiceDate().toString() : "";
            try {
                rec.setInvoiceDate(java.time.LocalDate.parse(evt.getNewValue()));
                logger.debug("Edited INV_DATE from '{}' to '{}'", old, evt.getNewValue());
            } catch (Exception e) { 
                logger.warn("Invalid date format: {}", evt.getNewValue());
            }
        });
        colDATE.setPrefWidth(100);

        TableColumn<MyTriumphRecord, String> colPO = new TableColumn<>("PO");
        colPO.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getPo()));
        colPO.setCellFactory(TextFieldTableCell.forTableColumn());
        colPO.setOnEditCommit(evt -> {
            MyTriumphRecord rec = evt.getRowValue();
            String old = rec.getPo();
            rec.setPo(evt.getNewValue());
            logger.debug("Edited PO from '{}' to '{}'", old, evt.getNewValue());
        });
        colPO.setPrefWidth(120);

        TableColumn<MyTriumphRecord, Double> colAMT = new TableColumn<>("INVAMT");
        colAMT.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().getInvAmt()).asObject());
        colAMT.setCellFactory(col -> new TableCell<MyTriumphRecord, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", item));
                }
            }
        });
        colAMT.setPrefWidth(100);

        TableColumn<MyTriumphRecord, String> colSource = new TableColumn<>("Source");
        colSource.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getSource() == null ? "IMPORT" : param.getValue().getSource()));
        colSource.setPrefWidth(80);

        TableColumn<MyTriumphRecord, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getMatchStatus() == null ? "" : param.getValue().getMatchStatus()));
        colStatus.setCellFactory(col -> new TableCell<MyTriumphRecord, String>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(s);
                setStyle("");
                if (!empty) {
                    MyTriumphRecord rec = getTableRow().getItem();
                    if (rec != null) {
                        if ("LOAD".equals(rec.getSource()) && !rec.isMatched()) {
                            setStyle("-fx-background-color:#ffcccc; -fx-font-weight:bold;"); // Light red for pending
                        } else if (s != null && s.equalsIgnoreCase("BILLED")) {
                            setStyle("-fx-background-color:#b7f9b7; -fx-font-weight:bold;");
                        } else if (s != null && s.equalsIgnoreCase("UNBILLED")) {
                            setStyle("-fx-background-color:#ffc8c8; -fx-font-weight:bold;");
                        }
                    }
                }
            }
        });
        colStatus.setPrefWidth(100);

        TableColumn<MyTriumphRecord, String> colDriver = new TableColumn<>("Driver");
        colDriver.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getDriverName() == null ? "" : param.getValue().getDriverName()));
        colDriver.setPrefWidth(120);

        table.getColumns().setAll(java.util.List.of(
                colDTR, colINV, colDATE, colPO, colAMT, colSource, colStatus, colDriver));
        table.setEditable(true);
        table.setItems(makeFilteredList());

        // Set row factory to apply light red background to entire row
        table.setRowFactory(tv -> new TableRow<MyTriumphRecord>() {
            @Override
            protected void updateItem(MyTriumphRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    if ("LOAD".equals(item.getSource()) && !item.isMatched()) {
                        setStyle("-fx-background-color:#ffcccc;"); // Light red for entire row
                    } else {
                        setStyle("");
                    }
                } else {
                    setStyle("");
                }
            }
        });

        // Sum label
        updateSum();

        // Import button logic
        importBtn.setOnAction(e -> {
            logger.info("Import button clicked");
            FileChooser fc = new FileChooser();
            fc.setTitle("Import MyTriumph .xlsx");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            File file = fc.showOpenDialog(table.getScene().getWindow());
            if (file != null) {
                logger.info("Import file selected: {}", file.getAbsolutePath());
                handleImport(file);
            } else {
                logger.info("Import cancelled");
            }
        });

        addBtn.setOnAction(e -> {
            logger.info("Add button clicked");
            handleAdd();
        });
        editBtn.setOnAction(e -> {
            logger.info("Edit button clicked");
            handleEdit();
        });
        removeBtn.setOnAction(e -> {
            logger.info("Remove button clicked");
            handleRemove();
        });
        saveBtn.setOnAction(e -> {
            logger.info("Save button clicked");
            handleSave();
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            reloadAll();
        });
        syncFromLoadsBtn.setOnAction(e -> {
            logger.info("Sync from Loads button clicked");
            handleSyncFromLoads();
        });
        copyUnbilledBtn.setOnAction(e -> {
            logger.info("Copy Unbilled button clicked");
            handleCopyUnbilledForExcel();
        });

        searchField.textProperty().addListener((obs, oldV, newV) -> {
            logger.debug("Search text changed: {}", newV);
            table.setItems(makeFilteredList());
        });
        searchCombo.valueProperty().addListener((obs, oldV, newV) -> {
            logger.debug("Search column changed: {}", newV);
            table.setItems(makeFilteredList());
        });

        VBox vbox = new VBox(searchBox, buttonBox, table, sumLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
        return vbox;
    }

    private void handleCopyUnbilledForExcel() {
        logger.info("Handling copy unbilled for Excel");
        
        // Create dialog for date selection and filtering options
        Dialog<Map<String, Object>> dateDialog = new Dialog<>();
        dateDialog.setTitle("Copy Unbilled Loads for Excel");
        dateDialog.setHeaderText("Configure unbilled loads export");
        dateDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(20));
        
        // Default invoice date
        DatePicker invoiceDatePicker = new DatePicker(LocalDate.now());
        grid.add(new Label("Invoice Date:"), 0, 0);
        grid.add(invoiceDatePicker, 1, 0);
        
        // Date range filter for selecting unbilled loads
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        LocalDate lastDayOfMonth = today.withDayOfMonth(today.getMonth().length(today.isLeapYear()));
        
        useCustomDateRange = new CheckBox("Filter unbilled loads by date range");
        grid.add(useCustomDateRange, 0, 1, 2, 1);
        
        startDatePicker = new DatePicker(firstDayOfMonth.minusMonths(2)); // Look back 2 months by default
        endDatePicker = new DatePicker(lastDayOfMonth);
        
        grid.add(new Label("Start Date:"), 0, 2);
        grid.add(startDatePicker, 1, 2);
        grid.add(new Label("End Date:"), 0, 3);
        grid.add(endDatePicker, 1, 3);
        
        // Disable date pickers if checkbox is not selected
        startDatePicker.setDisable(true);
        endDatePicker.setDisable(true);
        useCustomDateRange.selectedProperty().addListener((obs, old, isSelected) -> {
            startDatePicker.setDisable(!isSelected);
            endDatePicker.setDisable(!isSelected);
        });
        
        // Add month quick selectors
        ComboBox<YearMonth> monthSelector = new ComboBox<>();
        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            monthSelector.getItems().add(currentMonth.minusMonths(i));
        }
        monthSelector.setConverter(new javafx.util.StringConverter<YearMonth>() {
            @Override
            public String toString(YearMonth yearMonth) {
                if (yearMonth == null) return "";
                return yearMonth.getMonth().toString() + " " + yearMonth.getYear();
            }
            
            @Override
            public YearMonth fromString(String string) {
                return null; // Not needed for combo box
            }
        });
        
        Button applyMonthBtn = ModernButtonStyles.createPrimaryButton("📅 Apply Selected Month");
        applyMonthBtn.setOnAction(e -> {
            YearMonth selected = monthSelector.getValue();
            if (selected != null) {
                LocalDate start = selected.atDay(1);
                LocalDate end = selected.atEndOfMonth();
                startDatePicker.setValue(start);
                endDatePicker.setValue(end);
                useCustomDateRange.setSelected(true);
            }
        });
        
        grid.add(new Label("Quick Select:"), 0, 4);
        grid.add(monthSelector, 1, 4);
        grid.add(applyMonthBtn, 1, 5);
        
        dateDialog.getDialogPane().setContent(grid);
        
        dateDialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                Map<String, Object> result = new HashMap<>();
                result.put("invoiceDate", invoiceDatePicker.getValue());
                result.put("useCustomDateRange", useCustomDateRange.isSelected());
                result.put("startDate", startDatePicker.getValue());
                result.put("endDate", endDatePicker.getValue());
                return result;
            }
            return null;
        });
        
        Optional<Map<String, Object>> result = dateDialog.showAndWait();
        if (result.isPresent()) {
            Map<String, Object> config = result.get();
            LocalDate invoiceDate = (LocalDate) config.get("invoiceDate");
            boolean useCustomRange = (boolean) config.get("useCustomDateRange");
            LocalDate startDate = (LocalDate) config.get("startDate");
            LocalDate endDate = (LocalDate) config.get("endDate");
            
            copyUnbilledToClipboard(invoiceDate, useCustomRange, startDate, endDate);
        }
    }
    
    private void copyUnbilledToClipboard(LocalDate invoiceDate, boolean useCustomRange, LocalDate startDate, LocalDate endDate) {
        logger.info("Copying unbilled loads to clipboard - Invoice Date: {}, Using Custom Range: {}, Start: {}, End: {}", 
            invoiceDate, useCustomRange, startDate, endDate);
        
        // Get all delivered/paid loads that have PO numbers but no matching billed record
        List<Load> unbilledLoads = new ArrayList<>();
        Set<String> billedPOs = allRecords.stream()
            .filter(rec -> "IMPORT".equals(rec.getSource()) || rec.isMatched())
            .map(MyTriumphRecord::getPo)
            .filter(Objects::nonNull)
            .map(String::toUpperCase)
            .collect(Collectors.toSet());
        
        for (Load load : deliveredLoads) {
            if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
                String poUpper = load.getPONumber().toUpperCase();
                if (!billedPOs.contains(poUpper)) {
                    unbilledLoads.add(load);
                }
            }
        }
        
        // Apply date filter if requested
        if (useCustomRange && startDate != null && endDate != null) {
            unbilledLoads = unbilledLoads.stream()
                .filter(load -> load.getDeliveryDate() != null)
                .filter(load -> !load.getDeliveryDate().isBefore(startDate) && !load.getDeliveryDate().isAfter(endDate))
                .collect(Collectors.toList());
        }
        
        if (unbilledLoads.isEmpty()) {
            showInfo("No unbilled loads found to copy." + 
                (useCustomRange ? " Try adjusting the date range." : ""));
            return;
        }
        
        // Format date for Excel
        DateTimeFormatter excelFormatter = DateTimeFormatter.ofPattern("M/d/yy");
        String formattedDate = invoiceDate.format(excelFormatter);
        
        // Build clipboard content with tab-separated values
        StringBuilder clipboardText = new StringBuilder();
        
        for (Load load : unbilledLoads) {
            // DTR_NAME (Customer from loads)
            clipboardText.append(load.getCustomer() != null ? load.getCustomer() : "");
            clipboardText.append("\t");
            
            // INVOICE# - always "CREATE" for unbilled
            clipboardText.append("CREATE");
            clipboardText.append("\t");
            
            // INV_DATE - formatted date
            clipboardText.append(formattedDate);
            clipboardText.append("\t");
            
            // PO
            clipboardText.append(load.getPONumber() != null ? load.getPONumber() : "");
            clipboardText.append("\t");
            
            // INVAMT - without $ sign, just the numeric value
            clipboardText.append(String.format("%.2f", load.getGrossAmount()));
            clipboardText.append("\n");
        }
        
        // Remove last newline
        if (clipboardText.length() > 0) {
            clipboardText.setLength(clipboardText.length() - 1);
        }
        
        // Copy to clipboard
        ClipboardContent content = new ClipboardContent();
        content.putString(clipboardText.toString());
        Clipboard.getSystemClipboard().setContent(content);
        
        // Show notification
        String message = String.format("Copied %d unbilled loads to clipboard.", unbilledLoads.size());
        if (unbilledLoads.size() > 25) {
            message += String.format("\n\nWARNING: You have %d records, but the Excel template only has 25 rows (2-26).\nPlease add %d more rows to your Excel document before pasting.", 
                unbilledLoads.size(), unbilledLoads.size() - 25);
        }
        
        logger.info("Copied {} unbilled loads to clipboard", unbilledLoads.size());
        showInfo(message);
    }

    private void handleImport(File file) {
        logger.info("Starting import from file: {}", file.getName());
        try {
            List<MyTriumphRecord> imported = MyTriumphExcelImporter.importFromXlsx(file);

            if (imported.isEmpty()) {
                logger.warn("No valid invoice rows found in file");
                showError("No valid invoice rows found in the selected file.");
                return;
            }

            int total = imported.size();
            int updatedExisting = 0;
            int added = 0;
            int skippedDuplicates = 0;

            for (MyTriumphRecord rec : imported) {
                // Check if PO already exists
                MyTriumphRecord existing = triumphDAO.getByPO(rec.getPo());
                
                if (existing != null) {
                    logger.debug("Updating existing record for PO: {}", rec.getPo());
                    // Update the existing record with new invoice data
                    triumphDAO.updateByPO(rec.getPo(), rec);
                    updatedExisting++;
                    
                    // Update in-memory record
                    for (MyTriumphRecord memRec : allRecords) {
                        if (memRec.getPo().equals(rec.getPo())) {
                            memRec.setDtrName(rec.getDtrName());
                            memRec.setInvoiceNumber(rec.getInvoiceNumber());
                            memRec.setInvoiceDate(rec.getInvoiceDate());
                            memRec.setInvAmt(rec.getInvAmt());
                            memRec.setSource("IMPORT");
                            memRec.setMatched(true);
                            break;
                        }
                    }
                } else {
                    logger.debug("Adding new record for PO: {}", rec.getPo());
                    // Add new record
                    rec.setSource("IMPORT");
                    rec.setMatched(false);
                    
                    int id = triumphDAO.add(rec);
                    if (id > 0) {
                        rec.setId(id);
                        allRecords.add(rec);
                        added++;
                    }
                }
            }
            
            reloadCrossReference();
            updateSum();
            table.refresh();
            
            StringBuilder importMsg = new StringBuilder();
            importMsg.append("Import complete: ");
            if (added > 0) importMsg.append(added).append(" new records added. ");
            if (updatedExisting > 0) importMsg.append(updatedExisting).append(" existing records updated. ");
            
            logger.info("Import complete - Added: {}, Updated: {}", added, updatedExisting);
            showInfo(importMsg.toString());
        } catch (Exception e) {
            logger.error("Import failed: {}", e.getMessage(), e);
            showError("Import failed: " + e.getMessage());
        }
    }

    private void handleSyncFromLoads() {
        logger.info("Starting sync from loads");
        List<Load> toSync = loadDAO.getByStatus(Load.Status.DELIVERED);
        toSync.addAll(loadDAO.getByStatus(Load.Status.PAID));
        
        List<Load> loadsWithPO = toSync.stream()
            .filter(l -> l.getPONumber() != null && !l.getPONumber().isEmpty())
            .collect(Collectors.toList());
        
        logger.info("Found {} delivered/paid loads with PO numbers", loadsWithPO.size());
        
        if (loadsWithPO.isEmpty()) {
            showInfo("No delivered/paid loads with PO numbers found.");
            return;
        }
        
        int synced = 0;
        for (Load load : loadsWithPO) {
            synced += syncFromLoads(Arrays.asList(load));
        }
        
        logger.info("Sync complete - {} loads synced", synced);
        showInfo("Synced " + synced + " loads to MyTriumph audit.");
    }

    public int syncFromLoads(List<Load> deliveredLoads) {
        logger.debug("Syncing {} loads", deliveredLoads.size());
        int added = 0;
        for (Load load : deliveredLoads) {
            if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
                // Check if PO already exists
                if (!triumphDAO.existsByPO(load.getPONumber())) {
                    logger.debug("Adding placeholder for PO: {} from load: {}", 
                        load.getPONumber(), load.getLoadNumber());
                    MyTriumphRecord rec = new MyTriumphRecord(
                        0,
                        load.getCustomer() != null ? load.getCustomer() : "",
                        "PENDING-" + load.getLoadNumber(),
                        load.getDeliveryDate(),
                        load.getPONumber(),
                        load.getGrossAmount()
                    );
                    rec.setSource("LOAD");
                    rec.setMatched(false);
                    int id = triumphDAO.add(rec);
                    if (id > 0) {
                        rec.setId(id);
                        added++;
                    }
                } else {
                    logger.debug("PO {} already exists, skipping", load.getPONumber());
                }
            }
        }
        if (added > 0) {
            reloadAll();
        }
        return added;
    }

    private void handleAdd() {
        logger.debug("Showing add dialog");
        Dialog<MyTriumphRecord> dlg = createEditDialog(null);
        dlg.showAndWait().ifPresent(rec -> {
            if (!validateRecord(rec)) {
                logger.warn("Invalid record data");
                showError("All fields are required and INVAMT must be > 0");
                return;
            }
            
            // Check if PO already exists
            if (triumphDAO.existsByPO(rec.getPo())) {
                logger.warn("Duplicate PO: {}", rec.getPo());
                showError("A record with PO '" + rec.getPo() + "' already exists. Use Edit to modify it.");
                return;
            }
            
            logger.info("Adding new record - PO: {}", rec.getPo());
            int id = triumphDAO.add(rec);
            if (id > 0) {
                rec.setId(id);
                allRecords.add(rec);
                reloadCrossReference();
                updateSum();
            } else {
                logger.error("Failed to add record");
                showError("Failed to add record.");
            }
        });
    }

    private void handleEdit() {
        MyTriumphRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logger.warn("No record selected for edit");
            return;
        }
        logger.info("Editing record - PO: {}", selected.getPo());
        Dialog<MyTriumphRecord> dlg = createEditDialog(selected);
        dlg.showAndWait().ifPresent(rec -> {
            if (!validateRecord(rec)) {
                logger.warn("Invalid record data");
                showError("All fields are required and INVAMT must be > 0");
                return;
            }
            triumphDAO.update(rec);
            table.refresh();
            reloadCrossReference();
            updateSum();
        });
    }

    private void handleRemove() {
        MyTriumphRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logger.warn("No record selected for removal");
            return;
        }
        
        logger.info("Remove confirmation for PO: {}", selected.getPo());
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete record with PO '" + selected.getPo() + "'?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                logger.info("Removing record - PO: {}", selected.getPo());
                int idx = allRecords.indexOf(selected);
                triumphDAO.delete(selected.getId());
                allRecords.remove(selected);
                reloadCrossReference();
                updateSum();
            } else {
                logger.info("Remove cancelled");
            }
        });
    }

    private void handleSave() {
        logger.info("Saving all records");
        int updated = 0;
        for (MyTriumphRecord rec : allRecords) {
            if (rec.getId() > 0) {
                triumphDAO.update(rec);
                updated++;
            }
            else rec.setId(triumphDAO.add(rec));
        }
        logger.info("Saved {} records", updated);
        showInfo("Saved " + updated + " records.");
    }

    private void reloadAll() {
        logger.debug("Reloading all data");
        allRecords.setAll(triumphDAO.getAll());
        deliveredLoads.setAll(loadDAO.getByStatus(Load.Status.DELIVERED));
        deliveredLoads.addAll(loadDAO.getByStatus(Load.Status.PAID));
        reloadCrossReference();
        table.setItems(makeFilteredList());
        updateSum();
        logger.info("Data reload complete - Records: {}, Delivered loads: {}", 
            allRecords.size(), deliveredLoads.size());
    }

    private void reloadCrossReference() {
        logger.debug("Reloading cross references");
        Map<String, Load> poToLoad = deliveredLoads.stream()
                .filter(l -> l.getPONumber() != null && !l.getPONumber().isEmpty())
                .collect(Collectors.toMap(
                        l -> l.getPONumber().trim().toUpperCase(),
                        l -> l, (a, b) -> a));
        
        int matchedCount = 0;
        for (MyTriumphRecord rec : allRecords) {
            String po = rec.getPo() == null ? "" : rec.getPo().trim().toUpperCase();
            Load match = poToLoad.get(po);
            
            // A record is BILLED if it has an IMPORT source or is marked as matched
            // Otherwise it's UNBILLED
            if ("IMPORT".equals(rec.getSource()) || rec.isMatched()) {
                rec.setMatchStatus("BILLED");
            } else {
                rec.setMatchStatus("UNBILLED");
            }
            
            if (match != null) {
                rec.setDriverName(match.getDriver() != null ? match.getDriver().getName() : "");
                matchedCount++;
            } else {
                rec.setDriverName("");
            }
        }
        logger.debug("Cross reference complete - {} records matched", matchedCount);
    }

    private FilteredList<MyTriumphRecord> makeFilteredList() {
        String searchText = searchField.getText().trim().toLowerCase();
        String searchCol = searchCombo.getValue();
        logger.debug("Creating filtered list - Search: '{}', Column: {}", searchText, searchCol);
        Predicate<MyTriumphRecord> pred = r -> {
            if (searchText.isEmpty()) return true;
            if ("PO".equals(searchCol) && r.getPo() != null && r.getPo().toLowerCase().contains(searchText)) return true;
            if ("DTR_NAME".equals(searchCol) && r.getDtrName() != null && r.getDtrName().toLowerCase().contains(searchText)) return true;
            if ("INV_DATE".equals(searchCol) && r.getInvoiceDate() != null && r.getInvoiceDate().toString().toLowerCase().contains(searchText)) return true;
            if ("INVOICE#".equals(searchCol) && r.getInvoiceNumber() != null && r.getInvoiceNumber().toLowerCase().contains(searchText)) return true;
            return false;
        };
        return new FilteredList<>(allRecords, pred);
    }

    private void updateSum() {
        double sum = allRecords.stream()
                .filter(r -> r.getInvAmt() > 0)
                .mapToDouble(MyTriumphRecord::getInvAmt)
                .sum();
        sumLabel.setText("Total INVAMT: $" + String.format("%,.2f", sum));
        logger.debug("Updated sum: ${}", String.format("%,.2f", sum));
    }

    private Dialog<MyTriumphRecord> createEditDialog(MyTriumphRecord rec) {
        Dialog<MyTriumphRecord> dlg = new Dialog<>();
        dlg.setTitle(rec == null ? "Add Record" : "Edit Record");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField dtrField = new TextField();
        TextField invField = new TextField();
        DatePicker datePicker = new DatePicker();
        TextField poField = new TextField();
        TextField amtField = new TextField();

        if (rec != null) {
            dtrField.setText(rec.getDtrName());
            invField.setText(rec.getInvoiceNumber());
            if (rec.getInvoiceDate() != null) datePicker.setValue(rec.getInvoiceDate());
            poField.setText(rec.getPo());
            amtField.setText(String.valueOf(rec.getInvAmt()));
            
            // PO field should not be editable for existing records
            poField.setEditable(false);
            poField.setStyle("-fx-background-color: #f0f0f0;");
        }

        GridPane grid = new GridPane();
        grid.setVgap(8); grid.setHgap(10);
        grid.addRow(0, new Label("DTR_NAME:"), dtrField);
        grid.addRow(1, new Label("INVOICE#:"), invField);
        grid.addRow(2, new Label("INV_DATE:"), datePicker);
        grid.addRow(3, new Label("PO:"), poField);
        grid.addRow(4, new Label("INVAMT:"), amtField);

        // Add source selection
        ComboBox<String> sourceBox = new ComboBox<>(
                FXCollections.observableArrayList("IMPORT", "LOAD"));
        grid.addRow(5, new Label("Source:"), sourceBox);
        if (rec != null) {
            sourceBox.setValue(rec.getSource());
        } else {
            sourceBox.setValue("IMPORT");
        }
        
        // Add matched checkbox
        CheckBox matchedCheck = new CheckBox("Matched");
        grid.addRow(6, new Label("Status:"), matchedCheck);
        if (rec != null) {
            matchedCheck.setSelected(rec.isMatched());
        } else {
            matchedCheck.setSelected(false);
        }

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String dtr = dtrField.getText().trim();
                    String inv = invField.getText().trim();
                    java.time.LocalDate date = datePicker.getValue();
                    String po = poField.getText().trim();
                    double amt = Double.parseDouble(amtField.getText().trim());
                    String source = sourceBox.getValue();
                    boolean matched = matchedCheck.isSelected();
                    
                    if (rec == null) {
                        MyTriumphRecord newRec = new MyTriumphRecord(0, dtr, inv, date, po, amt);
                        newRec.setSource(source);
                        newRec.setMatched(matched);
                        return newRec;
                    }
                    rec.setDtrName(dtr);
                    rec.setInvoiceNumber(inv);
                    rec.setInvoiceDate(date);
                    // Don't change PO for existing records
                    rec.setInvAmt(amt);
                    rec.setSource(source);
                    rec.setMatched(matched);
                    return rec;
                } catch (Exception e) {
                    logger.error("Error parsing dialog input: {}", e.getMessage());
                    showError("Bad input: " + e.getMessage());
                }
            }
            return null;
        });
        return dlg;
    }

    private boolean validateRecord(MyTriumphRecord rec) {
        if (rec == null) return false;
        if (rec.getDtrName() == null || rec.getDtrName().trim().isEmpty()) return false;
        if (rec.getInvoiceNumber() == null || rec.getInvoiceNumber().trim().isEmpty()) return false;
        if (rec.getPo() == null || rec.getPo().trim().isEmpty()) return false;
        if (rec.getInvAmt() <= 0) return false;
        return true;
    }

    private void showError(String msg) {
        logger.error("Showing error dialog: {}", msg);
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        logger.info("Showing info dialog: {}", msg);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText("Info");
        alert.showAndWait();
    }
}