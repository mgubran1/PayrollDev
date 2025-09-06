package com.company.payroll.trucks;

import com.company.payroll.config.DOTComplianceConfig;
import com.company.payroll.config.DOTComplianceConfigDialog;
import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;
import com.company.payroll.util.WindowAware;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.concurrent.Task;

/**
 * Enhanced Trucks tab for managing fleet information with focus on document tracking
 * and expiration dates.
 */
public class TrucksTab extends BorderPane implements WindowAware {
    private static final Logger logger = LoggerFactory.getLogger(TrucksTab.class);
    
    private final TruckDAO truckDAO = new TruckDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ObservableList<Truck> trucks = FXCollections.observableArrayList();
    private final TableView<Truck> table = new TableView<>();
    private final Map<String, Employee> driverMap = new HashMap<>();

    // Listeners notified when truck data changes
    private final List<Runnable> dataChangeListeners = new ArrayList<>();
    
    // Document storage path
    private final String docStoragePath = "truck_documents";
    
    public TrucksTab() {
        logger.info("Initializing TrucksTab");
        // Create document directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(docStoragePath));
        } catch (Exception e) {
            logger.error("Failed to create document directory", e);
        }
        
        initializeUI();
        loadData();
        logger.info("TrucksTab initialization complete");
    }
    
    private void initializeUI() {
        // --- SEARCH/FILTER CONTROLS ---
        TextField searchField = new TextField();
        searchField.setPromptText("Search unit, vin, driver...");
        
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "Active", "Maintenance", "Available", "Out of Service");
        statusFilter.setValue("All");
        statusFilter.setPromptText("Status");
        
        Button documentsBtn = ModernButtonStyles.createInfoButton("📄 Document Manager");
        documentsBtn.setOnAction(e -> showDocumentManager());
        
        Button expiryAlertBtn = ModernButtonStyles.createWarningButton("⚠️ Show Expiry Alerts");
        expiryAlertBtn.setOnAction(e -> showExpiryAlerts());
        
        HBox filterBox = new HBox(12, new Label("Search:"), searchField, 
                                new Label("Status:"), statusFilter, 
                                documentsBtn, expiryAlertBtn);
        filterBox.setPadding(new Insets(10, 10, 0, 10));
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        // --- TABLE ---
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        TableColumn<Truck, String> unitCol = new TableColumn<>("Truck/Unit");
        unitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumber()));
        unitCol.setPrefWidth(100);
        
        TableColumn<Truck, String> vinCol = new TableColumn<>("VIN#");
        vinCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVin()));
        vinCol.setPrefWidth(150);
        
        TableColumn<Truck, String> makeModelCol = new TableColumn<>("Make/Model");
        makeModelCol.setCellValueFactory(c -> {
            String make = c.getValue().getMake() != null ? c.getValue().getMake() : "";
            String model = c.getValue().getModel() != null ? c.getValue().getModel() : "";
            String year = c.getValue().getYear() > 0 ? String.valueOf(c.getValue().getYear()) : "";
            return new SimpleStringProperty(year + " " + make + " " + model);
        });
        makeModelCol.setPrefWidth(150);
        
        TableColumn<Truck, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType()));
        typeCol.setPrefWidth(100);
        
        TableColumn<Truck, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        statusCol.setPrefWidth(100);
        
        TableColumn<Truck, String> driverCol = new TableColumn<>("Assigned Driver");
        driverCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDriver()));
        driverCol.setPrefWidth(150);
        
        TableColumn<Truck, LocalDate> regExpiryCol = new TableColumn<>("Registration Expiry");
        regExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getRegistrationExpiryDate()));
        regExpiryCol.setCellFactory(getExpiryDateCellFactory());
        regExpiryCol.setPrefWidth(150);
        
        TableColumn<Truck, LocalDate> insExpiryCol = new TableColumn<>("Insurance Expiry");
        insExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getInsuranceExpiryDate()));
        insExpiryCol.setCellFactory(getExpiryDateCellFactory());
        insExpiryCol.setPrefWidth(150);
        
        TableColumn<Truck, LocalDate> inspExpiryCol = new TableColumn<>("Inspection Expiry");
        inspExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getNextInspectionDue()));
        inspExpiryCol.setCellFactory(getExpiryDateCellFactory());
        inspExpiryCol.setPrefWidth(150);
        
        TableColumn<Truck, LocalDate> inspectionCol = new TableColumn<>("Inspection");
        inspectionCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getInspection()));
        inspectionCol.setCellFactory(column -> new TableCell<Truck, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")));
                    setStyle(""); // No special styling for inspection dates
                }
            }
        });
        inspectionCol.setPrefWidth(120);
        
        TableColumn<Truck, String> permitCol = new TableColumn<>("Permit Numbers");
        permitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPermitNumbers()));
        permitCol.setPrefWidth(120);
        
        table.getColumns().setAll(java.util.List.of(
                unitCol, vinCol, makeModelCol, typeCol, statusCol, driverCol,
                regExpiryCol, insExpiryCol, inspExpiryCol, inspectionCol, permitCol));
        
        // --- FILTERED/SORTED VIEW ---
        FilteredList<Truck> filteredTrucks = new FilteredList<>(trucks, p -> true);
        
        searchField.textProperty().addListener((obs, oldVal, newVal) -> 
            filteredTrucks.setPredicate(truck -> filterTruck(truck, newVal, statusFilter.getValue()))
        );
        
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> 
            filteredTrucks.setPredicate(truck -> filterTruck(truck, searchField.getText(), newVal))
        );
        
        SortedList<Truck> sortedTrucks = new SortedList<>(filteredTrucks);
        sortedTrucks.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedTrucks);
        
        // --- DOUBLE-CLICK FOR EDIT ---
        table.setRowFactory(tv -> {
            TableRow<Truck> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for truck: {}", row.getItem().getNumber());
                    showTruckDialog(row.getItem(), false);
                }
            });
            return row;
        });
        
        // --- BUTTONS ---
        Button addBtn = ModernButtonStyles.createPrimaryButton("➕ Add Truck");
        Button editBtn = ModernButtonStyles.createSecondaryButton("✏️ Edit");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete");
        Button refreshBtn = ModernButtonStyles.createSuccessButton("🔄 Refresh");
        Button importBtn = ModernButtonStyles.createInfoButton("📥 Import CSV/XLSX");
        Button uploadBtn = ModernButtonStyles.createInfoButton("📤 Upload Document");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print Document");
        Button exportBtn = ModernButtonStyles.createInfoButton("📊 Export");
        
        uploadBtn.setOnAction(e -> uploadDocument());
        printBtn.setOnAction(e -> printSelectedTruckDocuments());
        
        addBtn.setOnAction(e -> {
            logger.info("Add truck button clicked");
            showTruckDialog(null, true);
        });
        
        editBtn.setOnAction(e -> {
            Truck selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit truck button clicked for: {}", selected.getNumber());
                showTruckDialog(selected, false);
            }
        });
        
        deleteBtn.setOnAction(e -> {
            Truck selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete truck button clicked for: {}", selected.getNumber());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete truck \"" + selected.getNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of truck: {}", selected.getNumber());
                        truckDAO.delete(selected.getId());
                        loadData();
                        notifyDataChange();
                    }
                });
            }
        });
        
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            loadData();
        });
        
        importBtn.setOnAction(e -> {
            logger.info("Import button clicked");
            showImportDialog();
        });
        
        exportBtn.setOnAction(e -> {
            logger.info("Export button clicked");
            showExportDialog();
        });
        
        HBox btnBox = new HBox(12, addBtn, editBtn, deleteBtn, refreshBtn, importBtn, exportBtn, uploadBtn, printBtn);
        btnBox.setPadding(new Insets(12));
        btnBox.setAlignment(Pos.CENTER_LEFT);
        
        // --- STATUS BAR ---
        HBox statusBar = createStatusBar();
        
        // --- LAYOUT ASSEMBLY ---
        VBox vbox = new VBox(filterBox, table, statusBar);
        setCenter(vbox);
        setBottom(btnBox);
        setPadding(new Insets(10));
    }
    
    private HBox createStatusBar() {
        Label totalTrucksLabel = new Label("Total Trucks: 0");
        Label expiringDocsLabel = new Label("Expiring Documents: 0");
        expiringDocsLabel.setTextFill(Color.RED);
        
        HBox statusBar = new HBox(20, totalTrucksLabel, expiringDocsLabel);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        // Update counts when data changes
        trucks.addListener((javafx.collections.ListChangeListener<Truck>) c -> {
            totalTrucksLabel.setText("Total Trucks: " + trucks.size());
            
            long expiringCount = trucks.stream()
                .filter(t -> (t.getRegistrationExpiryDate() != null && 
                             t.getRegistrationExpiryDate().isBefore(LocalDate.now().plusMonths(1))) ||
                            (t.getInsuranceExpiryDate() != null && 
                             t.getInsuranceExpiryDate().isBefore(LocalDate.now().plusMonths(1))) ||
                            (t.getNextInspectionDue() != null && 
                             t.getNextInspectionDue().isBefore(LocalDate.now().plusMonths(1))))
                .count();
                
            expiringDocsLabel.setText("Expiring Documents: " + expiringCount);
            expiringDocsLabel.setVisible(expiringCount > 0);
        });
        
        return statusBar;
    }
    
    private void loadData() {
        logger.info("Loading truck data");
        List<Truck> truckList = truckDAO.findAll();
        List<Employee> employees = employeeDAO.getAll();
        
        // Create a map of truck unit to driver
        driverMap.clear();
        for (Employee emp : employees) {
            if (emp.getTruckUnit() != null && !emp.getTruckUnit().isEmpty()) {
                driverMap.put(emp.getTruckUnit(), emp);
            }
        }
        
        // Set driver names based on assignments
        for (Truck truck : truckList) {
            Employee driver = driverMap.get(truck.getNumber());
            if (driver != null) {
                truck.setDriver(driver.getName());
            }
        }
        
        // Clear and reload the observable list to trigger UI updates
        trucks.clear();
        trucks.addAll(truckList);
        
        // Force table refresh
        table.refresh();
        
        logger.info("Loaded {} trucks", trucks.size());
    }
    
    private boolean filterTruck(Truck truck, String searchText, String status) {
        boolean matchesSearch = true;
        if (searchText != null && !searchText.isEmpty()) {
            String lowerSearch = searchText.toLowerCase();
            matchesSearch = (truck.getNumber() != null && truck.getNumber().toLowerCase().contains(lowerSearch))
                || (truck.getVin() != null && truck.getVin().toLowerCase().contains(lowerSearch))
                || (truck.getMake() != null && truck.getMake().toLowerCase().contains(lowerSearch))
                || (truck.getModel() != null && truck.getModel().toLowerCase().contains(lowerSearch))
                || (truck.getDriver() != null && truck.getDriver().toLowerCase().contains(lowerSearch));
        }
        
        boolean matchesStatus = true;
        if (status != null && !status.equals("All")) {
            matchesStatus = status.equals(truck.getStatus());
        }
        
        return matchesSearch && matchesStatus;
    }
    
    private void showTruckDialog(Truck truck, boolean isAdd) {
        logger.debug("Showing truck dialog - isAdd: {}", isAdd);
        Dialog<Truck> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add New Truck" : "Edit Truck");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Fields
        TextField numberField = new TextField();
        TextField vinField = new TextField();
        TextField makeField = new TextField();
        TextField modelField = new TextField();
        TextField yearField = new TextField();
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(
                "Semi Truck (Tractor)", "Day Cab", "Sleeper Cab", "Box Truck", "Straight Truck", 
                "Dump Truck", "Flatbed Truck", "Refrigerated Truck", "Tanker Truck", 
                "Car Carrier", "Tow Truck", "Cargo Van", "Sprinter Van", "Pickup Truck", 
                "Stake Bed Truck", "Garbage Truck", "Cement Mixer", "Other"));
        typeBox.setPromptText("Select truck type");
        typeBox.setPrefWidth(200);
        ComboBox<String> statusBox = new ComboBox<>(FXCollections.observableArrayList(
                "Active", "Available", "Maintenance", "Out of Service", "In Transit"));
        DatePicker regExpiryPicker = new DatePicker();
        DatePicker insExpiryPicker = new DatePicker();
        DatePicker inspExpiryPicker = new DatePicker();
        DatePicker inspectionPicker = new DatePicker();
        TextField permitField = new TextField();
        TextField licenseField = new TextField();
        ComboBox<String> driverBox = new ComboBox<>();
        
        // Populate driver list
        List<Employee> employees = employeeDAO.getAll();
        List<String> driverNames = employees.stream()
            .map(Employee::getName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(java.util.stream.Collectors.toList());
        driverBox.getItems().addAll(driverNames);
        driverBox.setPromptText("Select driver");
        driverBox.setEditable(true);
        
        // Populate fields if editing
        if (truck != null) {
            numberField.setText(truck.getNumber());
            vinField.setText(truck.getVin());
            makeField.setText(truck.getMake());
            modelField.setText(truck.getModel());
            yearField.setText(truck.getYear() > 0 ? Integer.toString(truck.getYear()) : "");
            typeBox.setValue(truck.getType());
            statusBox.setValue(truck.getStatus());
            regExpiryPicker.setValue(truck.getRegistrationExpiryDate());
            insExpiryPicker.setValue(truck.getInsuranceExpiryDate());
            inspExpiryPicker.setValue(truck.getNextInspectionDue());
            inspectionPicker.setValue(truck.getInspection());
            permitField.setText(truck.getPermitNumbers());
            licenseField.setText(truck.getLicensePlate());
            driverBox.setValue(truck.getDriver());
        }
        
        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        int row = 0;
        grid.add(new Label("Truck/Unit Number*:"), 0, row);
        grid.add(numberField, 1, row++);
        
        grid.add(new Label("VIN:"), 0, row);
        grid.add(vinField, 1, row++);
        
        grid.add(new Label("Make:"), 0, row);
        grid.add(makeField, 1, row++);
        
        grid.add(new Label("Model:"), 0, row);
        grid.add(modelField, 1, row++);
        
        grid.add(new Label("Year:"), 0, row);
        grid.add(yearField, 1, row++);
        
        grid.add(new Label("Type:"), 0, row);
        grid.add(typeBox, 1, row++);
        
        grid.add(new Label("Status:"), 0, row);
        grid.add(statusBox, 1, row++);
        
        grid.add(new Label("License Plate:"), 0, row);
        grid.add(licenseField, 1, row++);
        
        grid.add(new Label("Registration Expiry:"), 0, row);
        grid.add(regExpiryPicker, 1, row++);
        
        grid.add(new Label("Insurance Expiry:"), 0, row);
        grid.add(insExpiryPicker, 1, row++);
        
        grid.add(new Label("Inspection Expiry:"), 0, row);
        grid.add(inspExpiryPicker, 1, row++);
        
        grid.add(new Label("Inspection:"), 0, row);
        grid.add(inspectionPicker, 1, row++);
        
        // Auto-calculate Inspection Expiry when Inspection date changes
        inspectionPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Set Inspection Expiry to 365 days after Inspection date
                LocalDate expiryDate = newVal.plusDays(365);
                inspExpiryPicker.setValue(expiryDate);
            }
        });
        
        grid.add(new Label("Permit Numbers:"), 0, row);
        grid.add(permitField, 1, row++);
        
        grid.add(new Label("Assigned Driver:"), 0, row);
        grid.add(driverBox, 1, row++);
        
        // Error label for validation messages
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        grid.add(errorLabel, 0, row++, 2, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(numberField.textProperty().isEmpty());
        
        // Handle duplicate truck numbers
        numberField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                boolean duplicate = trucks.stream()
                    .anyMatch(t -> t.getNumber().equalsIgnoreCase(newVal.trim()) &&
                             (isAdd || t.getId() != (truck == null ? -1 : truck.getId())));
                
                errorLabel.setText(duplicate ? "Truck number already exists" : "");
                okButton.setDisable(duplicate);
            }
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    Truck result = truck != null ? truck : new Truck();
                    
                    // Safe text extraction with null checks
                    result.setNumber(numberField != null ? numberField.getText() : "");
                    if (result.getNumber() != null) result.setNumber(result.getNumber().trim());
                    
                    result.setVin(vinField != null ? vinField.getText() : "");
                    if (result.getVin() != null) result.setVin(result.getVin().trim());
                    
                    result.setMake(makeField != null ? makeField.getText() : "");
                    if (result.getMake() != null) result.setMake(result.getMake().trim());
                    
                    result.setModel(modelField != null ? modelField.getText() : "");
                    if (result.getModel() != null) result.setModel(result.getModel().trim());
                    
                    try {
                        if (yearField != null && yearField.getText() != null && !yearField.getText().trim().isEmpty()) {
                            result.setYear(Integer.parseInt(yearField.getText().trim()));
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid year format: {}", yearField != null ? yearField.getText() : "null");
                    }
                    
                    result.setType(typeBox != null ? typeBox.getValue() : null);
                    result.setStatus(statusBox != null ? statusBox.getValue() : null);
                    
                    result.setLicensePlate(licenseField != null ? licenseField.getText() : "");
                    if (result.getLicensePlate() != null) result.setLicensePlate(result.getLicensePlate().trim());
                    
                    result.setRegistrationExpiryDate(regExpiryPicker != null ? regExpiryPicker.getValue() : null);
                    result.setInsuranceExpiryDate(insExpiryPicker != null ? insExpiryPicker.getValue() : null);
                    result.setNextInspectionDue(inspExpiryPicker != null ? inspExpiryPicker.getValue() : null);
                    
                    result.setPermitNumbers(permitField != null ? permitField.getText() : "");
                    if (result.getPermitNumbers() != null) result.setPermitNumbers(result.getPermitNumbers().trim());
                    
                    result.setInspection(inspectionPicker != null ? inspectionPicker.getValue() : null);
                    result.setDriver(driverBox != null ? driverBox.getValue() : null);
                    
                    // Auto-calculate Inspection Expiry if Inspection date is set
                    if (inspectionPicker != null && inspectionPicker.getValue() != null) {
                        LocalDate inspectionExpiry = inspectionPicker.getValue().plusDays(365);
                        result.setNextInspectionDue(inspectionExpiry);
                    }
                    
                    // Set default values for new trucks
                    if (isAdd) {
                        result.setAssigned(false);
                    }
                    
                    return result;
                } catch (Exception ex) {
                    logger.error("Error in truck dialog", ex);
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(result -> {
            if (isAdd) {
                truckDAO.save(result);
                logger.info("Added new truck: {}", result.getNumber());
            } else {
                truckDAO.save(result);
                logger.info("Updated truck: {}", result.getNumber());
            }
            // Force refresh the data to ensure UI updates
            loadData();
            notifyDataChange();
            
            // Force refresh the table to show changes immediately
            forceRefreshTable();
        });
    }
    
    private void showDocumentManager() {
        logger.info("Opening document manager");
        Truck selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, 
                      "Please select a truck to manage documents", 
                      ButtonType.OK).showAndWait();
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Truck Document Manager - " + selected.getNumber());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create compliance status indicator
        HBox complianceIndicator = createComplianceStatusIndicator(selected.getNumber());
        
        // Create document list section
        VBox documentSection = createDocumentListSection(selected.getNumber());
        
        // Create action buttons
        HBox actionButtons = createDocumentActionButtons(selected.getNumber(), documentSection);
        
        VBox content = new VBox(15, complianceIndicator, documentSection, actionButtons);
        content.setPadding(new Insets(20));
        content.setPrefWidth(800);
        content.setPrefHeight(600);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
    
    private HBox createComplianceStatusIndicator(String truckNumber) {
        Label statusLabel = new Label("Compliance Status:");
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        // Get configuration and check for required documents
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        List<String> requiredDocs = config.getRequiredDocuments("truck");
        
        // Check which required documents are present
        Set<String> availableDocs = new HashSet<>();
        for (String docType : requiredDocs) {
            if (hasDocument(truckNumber, docType)) {
                availableDocs.add(docType);
            }
        }
        
        boolean allCompliant = config.isCompliant("truck", availableDocs);
        List<String> missingDocs = config.getMissingDocuments("truck", availableDocs);
        
        Circle statusLight = new Circle(8);
        statusLight.setFill(allCompliant ? Color.GREEN : Color.RED);
        statusLight.setStroke(Color.BLACK);
        statusLight.setStrokeWidth(1);
        
        String statusText = allCompliant ? "FULLY COMPLIANT" : "COMPLIANCE GAPS DETECTED";
        if (!allCompliant && !missingDocs.isEmpty()) {
            statusText += " (" + missingDocs.size() + " missing)";
        }
        
        Label statusLabel2 = new Label(statusText);
        statusLabel2.setFont(Font.font("System", FontWeight.BOLD, 12));
        statusLabel2.setTextFill(allCompliant ? Color.GREEN : Color.RED);
        
        // Add configuration button
        Button configBtn = new Button("⚙️ Configure");
        configBtn.setStyle("-fx-font-size: 10px;");
        configBtn.setOnAction(e -> showComplianceConfiguration());
        
        HBox indicator = new HBox(10, statusLabel, statusLight, statusLabel2, configBtn);
        indicator.setAlignment(Pos.CENTER_LEFT);
        indicator.setPadding(new Insets(10));
        indicator.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 5;");
        
        return indicator;
    }
    
    private void showComplianceConfiguration() {
        DOTComplianceConfigDialog dialog = new DOTComplianceConfigDialog();
        dialog.showAndWait();
        
        // Refresh the document manager if it's open
        // This will update the compliance status with new configuration
    }
    
    private VBox createDocumentListSection(String truckNumber) {
        Label titleLabel = new Label("Document Library");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Get categories from configuration
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> truckReqs = config.getTruckRequirements();
        String[] categories = truckReqs.keySet().toArray(new String[0]);
        
        ObservableList<DocumentItem> documents = loadCategorizedDocuments(truckNumber, categories);
        
        ListView<DocumentItem> docListView = new ListView<>(documents);
        docListView.setCellFactory(param -> new DocumentListCell());
        docListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        VBox section = new VBox(10, titleLabel, docListView);
        section.setPrefHeight(400);
        
        return section;
    }
    
    private HBox createDocumentActionButtons(String truckNumber, VBox documentSection) {
        Button uploadBtn = ModernButtonStyles.createPrimaryButton("📤 Upload Document");
        Button viewBtn = ModernButtonStyles.createInfoButton("👁️ View");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print");
        Button mergeBtn = ModernButtonStyles.createSuccessButton("🔗 Merge Selected");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete");
        Button folderBtn = ModernButtonStyles.createSecondaryButton("📁 Open Folder");
        
        ListView<DocumentItem> docListView = (ListView<DocumentItem>) documentSection.getChildren().get(1);
        
        uploadBtn.setOnAction(e -> uploadDocumentForTruckEnhanced(truckNumber));
        
        viewBtn.setOnAction(e -> {
            DocumentItem selected = docListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewDocument(truckNumber, selected.getFileName());
            }
        });
        
        printBtn.setOnAction(e -> {
            DocumentItem selected = docListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                printDocument(truckNumber, selected.getFileName());
            }
        });
        
        mergeBtn.setOnAction(e -> {
            ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
            if (!selectedDocs.isEmpty()) {
                mergeDocuments(truckNumber, selectedDocs);
            }
        });
        
        deleteBtn.setOnAction(e -> {
            ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
            if (!selectedDocs.isEmpty()) {
                deleteSelectedDocuments(truckNumber, selectedDocs);
                refreshDocumentList(docListView, truckNumber);
            }
        });
        
        folderBtn.setOnAction(e -> openTruckFolder(truckNumber));
        
        HBox buttonBox = new HBox(10, uploadBtn, viewBtn, printBtn, mergeBtn, deleteBtn, folderBtn);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setAlignment(Pos.CENTER);
        
        return buttonBox;
    }
    
    private void uploadDocument() {
        Truck selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, 
                      "Please select a truck to upload document", 
                      ButtonType.OK).showAndWait();
            return;
        }
        
        uploadDocumentForTruck(selected.getNumber());
    }
    
    private void uploadDocumentForTruck(String truckNumber) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Document to Upload");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
        if (selectedFile != null) {
            // Ask for document type
            Dialog<String> typeDialog = new Dialog<>();
            typeDialog.setTitle("Document Type");
            typeDialog.setHeaderText("Select document type");
            
            ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "Registration", "Insurance", "Inspection", "Permit", "Maintenance", "Other"));
            typeCombo.setValue("Other");
            
            TextField descField = new TextField();
            descField.setPromptText("Document description");
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            grid.add(new Label("Type:"), 0, 0);
            grid.add(typeCombo, 1, 0);
            grid.add(new Label("Description:"), 0, 1);
            grid.add(descField, 1, 1);
            
            typeDialog.getDialogPane().setContent(grid);
            typeDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            typeDialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    String type = typeCombo.getValue();
                    String desc = descField.getText();
                    if (desc == null || desc.trim().isEmpty()) {
                        return type;
                    }
                    return type + " - " + desc;
                }
                return null;
            });
            
            Optional<String> docType = typeDialog.showAndWait();
            
            if (docType.isPresent()) {
                try {
                    // Create truck's directory if it doesn't exist
                    Path truckDir = Paths.get(docStoragePath, truckNumber);
                    Files.createDirectories(truckDir);
                    
                    // Create a filename with timestamp to avoid duplicates
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String extension = selectedFile.getName().substring(
                            selectedFile.getName().lastIndexOf('.'));
                    
                    String newFileName = docType.get().replaceAll("[\\\\/:*?\"<>|]", "_") 
                                       + "_" + timestamp + extension;
                    
                    Path destPath = truckDir.resolve(newFileName);
                    Files.copy(selectedFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    logger.info("Uploaded document for truck {}: {}", truckNumber, newFileName);
                    new Alert(Alert.AlertType.INFORMATION, 
                             "Document uploaded successfully", 
                             ButtonType.OK).showAndWait();
                    
                } catch (Exception e) {
                    logger.error("Failed to upload document", e);
                    new Alert(Alert.AlertType.ERROR, 
                             "Failed to upload document: " + e.getMessage(), 
                             ButtonType.OK).showAndWait();
                }
            }
        }
    }
    
    private void updateDocumentList(ListView<String> listView, String truckNumber) {
        try {
            Path truckDir = Paths.get(docStoragePath, truckNumber);
            if (Files.exists(truckDir)) {
                List<String> files = Files.list(truckDir)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
                listView.setItems(FXCollections.observableArrayList(files));
            } else {
                listView.setItems(FXCollections.observableArrayList());
            }
        } catch (Exception e) {
            logger.error("Failed to list documents", e);
            listView.setItems(FXCollections.observableArrayList());
        }
    }
    
    private void viewDocument(String truckNumber, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, truckNumber, document);
            File file = docPath.toFile();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            } else {
                logger.warn("Desktop is not supported, cannot open file");
                new Alert(Alert.AlertType.INFORMATION, 
                         "Cannot open file. File is saved at: " + docPath, 
                         ButtonType.OK).showAndWait();
            }
        } catch (Exception e) {
            logger.error("Failed to open document", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to open document: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private void printDocument(String truckNumber, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, truckNumber, document);
            File file = docPath.toFile();
            if (Desktop.isDesktopSupported() && Desktop.isSupported(Desktop.Action.PRINT)) {
                Desktop.getDesktop().print(file);
                logger.info("Printing document: {}", docPath);
            } else {
                logger.warn("Printing is not supported");
                new Alert(Alert.AlertType.INFORMATION, 
                         "Printing is not supported directly. Please open the file first.", 
                         ButtonType.OK).showAndWait();
                viewDocument(truckNumber, document);
            }
        } catch (Exception e) {
            logger.error("Failed to print document", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to print document: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private void deleteDocument(String truckNumber, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, truckNumber, document);
            
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to delete this document?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Confirm Deletion");
            
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    try {
                        Files.delete(docPath);
                        logger.info("Deleted document: {}", docPath);
                    } catch (Exception e) {
                        logger.error("Failed to delete document", e);
                        new Alert(Alert.AlertType.ERROR, 
                                 "Failed to delete document: " + e.getMessage(), 
                                 ButtonType.OK).showAndWait();
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error preparing document deletion", e);
        }
    }
    
    private void printSelectedTruckDocuments() {
        Truck selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, 
                      "Please select a truck to print documents", 
                      ButtonType.OK).showAndWait();
            return;
        }
        
        try {
            Path truckDir = Paths.get(docStoragePath, selected.getNumber());
            if (!Files.exists(truckDir) || !Files.isDirectory(truckDir)) {
                new Alert(Alert.AlertType.INFORMATION, 
                          "No documents found for this truck", 
                          ButtonType.OK).showAndWait();
                return;
            }
            
            List<String> documents = Files.list(truckDir)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            
            if (documents.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, 
                          "No documents found for this truck", 
                          ButtonType.OK).showAndWait();
                return;
            }
            
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Select Document to Print");
            dialog.setHeaderText("Choose a document to print for " + selected.getNumber());
            
            ListView<String> docList = new ListView<>(FXCollections.observableArrayList(documents));
            docList.setPrefHeight(300);
            dialog.getDialogPane().setContent(docList);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return docList.getSelectionModel().getSelectedItem();
                }
                return null;
            });
            
            dialog.showAndWait().ifPresent(doc -> 
                printDocument(selected.getNumber(), doc)
            );
            
        } catch (Exception e) {
            logger.error("Error accessing documents", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Error accessing documents: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private void showExpiryAlerts() {
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        List<ExpiryInfo> allExpiries = new ArrayList<>();
        List<DocumentComplianceInfo> missingDocuments = new ArrayList<>();
        
        // Collect all expiring items and check document compliance
        for (Truck truck : trucks) {
            // Check registration expiry
            if (truck.getRegistrationExpiryDate() != null && 
                truck.getRegistrationExpiryDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(truck, "Registration", 
                    truck.getRegistrationExpiryDate()));
            }
            
            // Check insurance expiry
            if (truck.getInsuranceExpiryDate() != null && 
                truck.getInsuranceExpiryDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(truck, "Insurance", 
                    truck.getInsuranceExpiryDate()));
            }
            
            // Check inspection expiry
            if (truck.getNextInspectionDue() != null && 
                truck.getNextInspectionDue().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(truck, "Inspection", 
                    truck.getNextInspectionDue()));
            }
            
            // Check for missing required documents
            checkMissingDocuments(truck, missingDocuments);
        }
        
        if (allExpiries.isEmpty() && missingDocuments.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, 
                     "No documents expiring within the next 30 days and no compliance gaps detected", 
                     ButtonType.OK).showAndWait();
            return;
        }
        
        // Sort by expiry date (earliest first)
        allExpiries.sort((a, b) -> a.expiryDate.compareTo(b.expiryDate));
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Document Expiry & Compliance Alerts");
        dialog.setHeaderText(String.format("Documents Expiring Within 30 Days (%d items) + Compliance Gaps (%d items)", 
                                         allExpiries.size(), missingDocuments.size()));
        
        // Create tabbed pane for expiring documents and compliance gaps
        TabPane tabPane = new TabPane();
        
        // Expiring documents tab
        if (!allExpiries.isEmpty()) {
            Tab expiringTab = new Tab("Expiring Documents", createExpiryTable(allExpiries));
            expiringTab.setClosable(false);
            tabPane.getTabs().add(expiringTab);
        }
        
        // Compliance gaps tab
        if (!missingDocuments.isEmpty()) {
            Tab complianceTab = new Tab("Compliance Gaps", createComplianceTable(missingDocuments));
            complianceTab.setClosable(false);
            tabPane.getTabs().add(complianceTab);
        }
        
        VBox content = new VBox(10, tabPane);
        content.setPrefWidth(900);
        content.setPrefHeight(500);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
    
    private TableView<ExpiryInfo> createExpiryTable(List<ExpiryInfo> expiries) {
        TableView<ExpiryInfo> alertTable = new TableView<>();
        
        TableColumn<ExpiryInfo, String> numberCol = new TableColumn<>("Truck #");
        numberCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().truck.getNumber()));
        numberCol.setPrefWidth(100);
        
        TableColumn<ExpiryInfo, String> typeCol = new TableColumn<>("Truck Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().truck.getType()));
        typeCol.setPrefWidth(120);
        
        TableColumn<ExpiryInfo, String> assignedCol = new TableColumn<>("Assigned To");
        assignedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().truck.getDriver()));
        assignedCol.setPrefWidth(150);
        
        TableColumn<ExpiryInfo, String> docTypeCol = new TableColumn<>("Document Type");
        docTypeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().documentType));
        docTypeCol.setPrefWidth(120);
        docTypeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(type);
                    // Color code by document type
                    switch (type) {
                        case "Registration":
                            setStyle("-fx-text-fill: #1976D2; -fx-font-weight: bold;");
                            break;
                        case "Insurance":
                            setStyle("-fx-text-fill: #388E3C; -fx-font-weight: bold;");
                            break;
                        case "Inspection":
                            setStyle("-fx-text-fill: #F57C00; -fx-font-weight: bold;");
                            break;
                    }
                }
            }
        });
        
        TableColumn<ExpiryInfo, LocalDate> expiryCol = new TableColumn<>("Expiry Date");
        expiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().expiryDate));
        expiryCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                
                if (empty || date == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(date.toString());
                    
                    LocalDate now = LocalDate.now();
                    if (date.isBefore(now)) {
                        // Expired
                        setStyle("-fx-background-color: #ffcccc; -fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (date.isBefore(now.plusWeeks(2))) {
                        // Warning: Expiring within 2 weeks
                        setStyle("-fx-background-color: #fff2cc; -fx-text-fill: #7F6000; -fx-font-weight: bold;");
                    } else if (date.isBefore(now.plusMonths(1))) {
                        // Notice: Expiring within a month
                        setStyle("-fx-background-color: #e6f2ff; -fx-text-fill: #0066cc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        expiryCol.setPrefWidth(120);
        
        TableColumn<ExpiryInfo, String> daysCol = new TableColumn<>("Days Until Expiry");
        daysCol.setCellValueFactory(c -> {
            LocalDate expiry = c.getValue().expiryDate;
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiry);
            return new SimpleStringProperty(days < 0 ? "EXPIRED (" + Math.abs(days) + " days)" : days + " days");
        });
        daysCol.setPrefWidth(150);
        daysCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String days, boolean empty) {
                super.updateItem(days, empty);
                if (empty || days == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(days);
                    if (days.startsWith("EXPIRED")) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (days.startsWith("0 days") || days.equals("1 days")) {
                        setStyle("-fx-text-fill: #F57C00; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        alertTable.getColumns().setAll(java.util.List.of(numberCol, typeCol, assignedCol, docTypeCol, expiryCol, daysCol));
        alertTable.setItems(FXCollections.observableArrayList(expiries));
        
        return alertTable;
    }
    
    private TableView<DocumentComplianceInfo> createComplianceTable(List<DocumentComplianceInfo> missingDocs) {
        TableView<DocumentComplianceInfo> complianceTable = new TableView<>();
        
        TableColumn<DocumentComplianceInfo, String> numberCol = new TableColumn<>("Truck #");
        numberCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().truck.getNumber()));
        numberCol.setPrefWidth(100);
        
        TableColumn<DocumentComplianceInfo, String> assignedCol = new TableColumn<>("Assigned To");
        assignedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().truck.getDriver()));
        assignedCol.setPrefWidth(150);
        
        TableColumn<DocumentComplianceInfo, String> missingDocCol = new TableColumn<>("Missing Document");
        missingDocCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().missingDocument));
        missingDocCol.setPrefWidth(200);
        
        TableColumn<DocumentComplianceInfo, String> severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().severity));
        severityCol.setPrefWidth(100);
        severityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(severity);
                    if ("CRITICAL".equals(severity)) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if ("HIGH".equals(severity)) {
                        setStyle("-fx-text-fill: #F57C00; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        complianceTable.getColumns().setAll(java.util.List.of(numberCol, assignedCol, missingDocCol, severityCol));
        complianceTable.setItems(FXCollections.observableArrayList(missingDocs));
        
        return complianceTable;
    }
    
    private void checkMissingDocuments(Truck truck, List<DocumentComplianceInfo> missingDocuments) {
        String truckNumber = truck.getNumber();
        
        // Get configuration and check for required documents
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> requirements = config.getTruckRequirements();
        
        for (Map.Entry<String, Boolean> entry : requirements.entrySet()) {
            if (entry.getValue() && !hasDocument(truckNumber, entry.getKey())) {
                // Determine severity based on document type
                String severity = isCriticalDocument(entry.getKey()) ? "CRITICAL" : 
                                isImportantDocument(entry.getKey()) ? "HIGH" : "MEDIUM";
                missingDocuments.add(new DocumentComplianceInfo(truck, entry.getKey(), severity));
            }
        }
    }
    
    private boolean isCriticalDocument(String documentType) {
        String[] criticalDocs = {
            "Annual DOT Inspection", "Truck Registration", "IRP Registration", 
            "IFTA Documentation", "DOT Cab Card"
        };
        for (String critical : criticalDocs) {
            if (documentType.contains(critical)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isImportantDocument(String documentType) {
        String[] importantDocs = {
            "Insurance Documents", "Title or Lease Agreements"
        };
        for (String important : importantDocs) {
            if (documentType.contains(important)) {
                return true;
            }
        }
        return false;
    }
    
    // Helper classes for expiry and compliance tracking
    private static class ExpiryInfo {
        final Truck truck;
        final String documentType;
        final LocalDate expiryDate;
        
        ExpiryInfo(Truck truck, String documentType, LocalDate expiryDate) {
            this.truck = truck;
            this.documentType = documentType;
            this.expiryDate = expiryDate;
        }
    }
    
    private static class DocumentComplianceInfo {
        final Truck truck;
        final String missingDocument;
        final String severity;
        
        DocumentComplianceInfo(Truck truck, String missingDocument, String severity) {
            this.truck = truck;
            this.missingDocument = missingDocument;
            this.severity = severity;
        }
    }
    
    private <T> Callback<TableColumn<Truck, LocalDate>, TableCell<Truck, LocalDate>> 
    getExpiryDateCellFactory() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                
                if (empty || date == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(date.toString());
                    
                    LocalDate now = LocalDate.now();
                    if (date.isBefore(now)) {
                        // Expired
                        setStyle("-fx-background-color: #ffcccc; -fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (date.isBefore(now.plusWeeks(2))) {
                        // Warning: Expiring within 2 weeks
                        setStyle("-fx-background-color: #fff2cc; -fx-text-fill: #7F6000; -fx-font-weight: bold;");
                    } else if (date.isBefore(now.plusMonths(1))) {
                        // Notice: Expiring within a month
                        setStyle("-fx-background-color: #e6f2ff; -fx-text-fill: #0066cc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        };
    }

    public void refreshFromEmployeeChanges(List<Employee> employees) {
        logger.info("Refreshing trucks based on employee changes");
        // Update driver assignments from employee data
        for (Employee emp : employees) {
            if (emp.getTruckUnit() != null && !emp.getTruckUnit().isEmpty()) {
                trucks.stream()
                    .filter(t -> t.getNumber().equals(emp.getTruckUnit()))
                    .findFirst()
                    .ifPresent(t -> {
                        t.setDriver(emp.getName());
                        t.setAssigned(true);
                        truckDAO.save(t);
                    });
            }
        }
        
        // Reload data to refresh the view
        loadData();
        notifyDataChange();
    }

    /** Register a listener to be notified when truck data changes. */
    public void addDataChangeListener(Runnable listener) {
        if (listener != null) {
            dataChangeListeners.add(listener);
        }
    }

    /** Notify listeners that truck data has changed. */
    private void notifyDataChange() {
        for (Runnable r : dataChangeListeners) {
            try {
                r.run();
            } catch (Exception ex) {
                logger.warn("Truck data change listener threw exception", ex);
            }
        }
    }
    
    /**
     * Get a snapshot of the current list of trucks.
     *
     * @return list of trucks currently loaded in the tab
     */
    public List<Truck> getCurrentTrucks() {
        return new ArrayList<>(trucks);
    }
    
    /**
     * Show import dialog for CSV/XLSX files.
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Truck Data");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );

        File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
        if (selectedFile != null) {
            // Show progress dialog
            showImportProgressDialog(selectedFile);
        }
    }
    
    /**
     * Show import progress dialog with detailed feedback
     */
    private void showImportProgressDialog(File selectedFile) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Importing Trucks");
        progressDialog.setHeaderText("Importing truck data from " + selectedFile.getName());
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label statusLabel = new Label("Reading file...");
        statusLabel.setFont(Font.font("Arial", 14));
        
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);
        
        Label detailLabel = new Label("");
        detailLabel.setFont(Font.font("Arial", 12));
        detailLabel.setTextFill(Color.web("#666666"));
        
        content.getChildren().addAll(statusLabel, progressBar, detailLabel);
        progressDialog.getDialogPane().setContent(content);
        
        // Create import task
        Task<ImportResult> importTask = new Task<ImportResult>() {
            @Override
            protected ImportResult call() throws Exception {
                ImportResult result = new ImportResult();
                
                try {
                    updateMessage("Reading file...");
                    updateProgress(0, 100);
                    
                    List<Truck> trucks = TruckCSVImporter.importTrucks(selectedFile.toPath());
                    result.totalFound = trucks.size();
                    
                    updateMessage("Processing trucks...");
                    updateProgress(50, 100);
                    
                    if (!trucks.isEmpty()) {
                        updateMessage("Saving to database...");
                        updateProgress(75, 100);
                        
                        List<Truck> savedTrucks = truckDAO.addOrUpdateAll(trucks);
                        result.imported = savedTrucks.size();
                        result.savedTrucks = savedTrucks; // Store for later use
                        
                        updateMessage("Import completed successfully!");
                        updateProgress(100, 100);
                    } else {
                        updateMessage("No valid trucks found in file");
                        result.errors.add("No valid truck data found in the file");
                    }
                    
                } catch (Exception e) {
                    logger.error("Import failed", e);
                    result.errors.add("Import failed: " + e.getMessage());
                    throw e;
                }
                
                return result;
            }
        };
        
        // Update UI based on task progress
        importTask.messageProperty().addListener((obs, oldVal, newVal) -> {
            statusLabel.setText(newVal);
        });
        
        importTask.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                progressBar.setVisible(true);
                progressBar.setProgress(newVal.doubleValue());
            }
        });
        
        // Handle completion
        importTask.setOnSucceeded(e -> {
            ImportResult result = importTask.getValue();
            progressDialog.close();
            
            // Use the saved trucks that already have proper IDs
            List<Truck> savedTrucks = result.savedTrucks;
            logger.info("Import completed with {} trucks, refreshing UI", savedTrucks.size());
            
            // Log IDs for debugging
            for (Truck truck : savedTrucks) {
                logger.debug("Truck in result: {} (ID: {})", truck.getNumber(), truck.getId());
            }
            
            // Clear and reload the observable list with the correct data
            trucks.clear();
            trucks.addAll(savedTrucks);
            logger.info("Updated observable list with {} trucks", trucks.size());
            
            // Update driver assignments
            updateDriverAssignments();
            
            // Force refresh the table to show changes immediately
            forceRefreshTable();
            
            // Notify data change listeners
            notifyDataChange();
            
            // Show results
            showImportResults(result, selectedFile.getName());
        });
        
        importTask.setOnFailed(e -> {
            progressDialog.close();
            Throwable error = importTask.getException();
            logger.error("Import failed", error);
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Import Error");
            alert.setHeaderText("Failed to import trucks");
            alert.setContentText("Error: " + error.getMessage());
            alert.showAndWait();
        });
        
        // Handle cancellation
        progressDialog.setOnCloseRequest(e -> {
            if (importTask.isRunning()) {
                importTask.cancel();
            }
        });
        
        // Start import
        Thread importThread = new Thread(importTask);
        importThread.setDaemon(true);
        importThread.start();
        
        progressDialog.showAndWait();
    }
    
    /**
     * Show import results dialog
     */
    private void showImportResults(ImportResult result, String fileName) {
        Dialog<Void> resultsDialog = new Dialog<>();
        resultsDialog.setTitle("Import Results");
        resultsDialog.setHeaderText("Import completed for " + fileName);
        resultsDialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label summaryLabel = new Label();
        summaryLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        if (result.imported > 0) {
            summaryLabel.setText("✓ Successfully imported " + result.imported + " trucks");
            summaryLabel.setTextFill(Color.web("#4CAF50"));
        } else {
            summaryLabel.setText("⚠ No trucks were imported");
            summaryLabel.setTextFill(Color.web("#FF9800"));
        }
        
        Label detailsLabel = new Label();
        detailsLabel.setFont(Font.font("Arial", 12));
        detailsLabel.setTextFill(Color.web("#666666"));
        
        StringBuilder details = new StringBuilder();
        details.append("Total records found: ").append(result.totalFound).append("\n");
        details.append("Successfully imported: ").append(result.imported).append("\n");
        
        if (!result.errors.isEmpty()) {
            details.append("\nErrors:\n");
            for (String error : result.errors) {
                details.append("• ").append(error).append("\n");
            }
        }
        
        detailsLabel.setText(details.toString());
        
        content.getChildren().addAll(summaryLabel, detailsLabel);
        resultsDialog.getDialogPane().setContent(content);
        
        resultsDialog.showAndWait();
    }
    
    /**
     * Import result class
     */
    private static class ImportResult {
        int totalFound = 0;
        int imported = 0;
        List<String> errors = new ArrayList<>();
        List<Truck> savedTrucks = new ArrayList<>(); // Added for storing saved trucks
    }
    
    private static class Desktop {
        public static enum Action { PRINT }
        
        public static boolean isDesktopSupported() {
            return java.awt.Desktop.isDesktopSupported();
        }
        
        public static java.awt.Desktop getDesktop() {
            return java.awt.Desktop.getDesktop();
        }
        
        public static boolean isSupported(Action action) {
            if (action == Action.PRINT) {
                return java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.PRINT);
            }
            return false;
        }
    }

    /**
     * Update driver assignments for all trucks
     */
    private void updateDriverAssignments() {
        List<Employee> employees = employeeDAO.getAll();
        
        // Create a map of truck unit to driver
        driverMap.clear();
        for (Employee emp : employees) {
            if (emp.getTruckUnit() != null && !emp.getTruckUnit().isEmpty()) {
                driverMap.put(emp.getTruckUnit(), emp);
            }
        }
        
        // Set driver names based on assignments
        for (Truck truck : trucks) {
            Employee driver = driverMap.get(truck.getNumber());
            if (driver != null) {
                truck.setDriver(driver.getName());
            }
        }
    }

    /**
     * Force refresh the entire table and all related data structures
     */
    private void forceRefreshTable() {
        logger.debug("Forcing table refresh");
        
        // Get the current items from the table
        ObservableList<Truck> currentItems = table.getItems();
        
        // If we have a SortedList with a FilteredList source, trigger refresh on the filtered list
        if (currentItems instanceof SortedList) {
            @SuppressWarnings("unchecked")
            SortedList<Truck> sortedList = (SortedList<Truck>) currentItems;
            ObservableList<? extends Truck> source = sortedList.getSource();
            
            if (source instanceof FilteredList) {
                @SuppressWarnings("unchecked")
                FilteredList<Truck> filteredList = (FilteredList<Truck>) source;
                // Trigger a refresh by temporarily changing the predicate
                java.util.function.Predicate<? super Truck> currentPredicate = filteredList.getPredicate();
                filteredList.setPredicate(null);
                filteredList.setPredicate(currentPredicate);
            }
        }
        
        // Force table refresh
        table.refresh();
        
        // Update status bar
        updateStatusBar();
    }
    
    /**
     * Update status bar with current counts
     */
    private void updateStatusBar() {
        // This will be called by the existing listener in createStatusBar()
        // The listener automatically updates when trucks list changes
    }
    
    // Helper methods for document management
    
    private boolean hasDocument(String truckNumber, String documentType) {
        try {
            Path truckDir = Paths.get(docStoragePath, truckNumber);
            if (!Files.exists(truckDir)) {
                return false;
            }
            
            return Files.list(truckDir)
                .anyMatch(file -> file.getFileName().toString().contains(documentType));
        } catch (Exception e) {
            logger.error("Error checking for document: {}", e.getMessage());
            return false;
        }
    }
    
    private ObservableList<DocumentItem> loadCategorizedDocuments(String truckNumber, String[] categories) {
        ObservableList<DocumentItem> documents = FXCollections.observableArrayList();
        
        try {
            Path truckDir = Paths.get(docStoragePath, truckNumber);
            if (!Files.exists(truckDir)) {
                return documents;
            }
            
            Files.list(truckDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String category = determineDocumentCategory(fileName, categories);
                    documents.add(new DocumentItem(fileName, category, file.toFile()));
                });
            
            // Sort by category, then by filename
            documents.sort((a, b) -> {
                int categoryCompare = a.getCategory().compareTo(b.getCategory());
                return categoryCompare != 0 ? categoryCompare : a.getFileName().compareTo(b.getFileName());
            });
            
        } catch (Exception e) {
            logger.error("Error loading categorized documents: {}", e.getMessage());
        }
        
        return documents;
    }
    
    private String determineDocumentCategory(String fileName, String[] categories) {
        String lowerFileName = fileName.toLowerCase();
        
        for (String category : categories) {
            if (lowerFileName.contains(category.toLowerCase())) {
                return category;
            }
        }
        
        return "Other";
    }
    
    private void uploadDocumentForTruckEnhanced(String truckNumber) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Document to Upload");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
        if (selectedFile != null) {
            showDocumentTypeDialog(truckNumber, selectedFile);
        }
    }
    
    private void showDocumentTypeDialog(String truckNumber, File selectedFile) {
        Dialog<String> typeDialog = new Dialog<>();
        typeDialog.setTitle("Document Type Selection");
        typeDialog.setHeaderText("Select document category and provide description");
        
        // Get document types from configuration
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> requirements = config.getTruckRequirements();
        List<String> documentTypes = new ArrayList<>(requirements.keySet());
        documentTypes.add("Other"); // Always include Other option
        
        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(documentTypes));
        typeCombo.setValue("Other");
        
        TextField descField = new TextField();
        descField.setPromptText("Document description (optional)");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Category:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);
        
        typeDialog.getDialogPane().setContent(grid);
        typeDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        typeDialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String type = typeCombo.getValue();
                String desc = descField.getText();
                if (desc != null && !desc.trim().isEmpty()) {
                    return type + " - " + desc;
                }
                return type;
            }
            return null;
        });
        
        Optional<String> docType = typeDialog.showAndWait();
        
        if (docType.isPresent()) {
            saveDocumentWithProperNaming(truckNumber, selectedFile, docType.get());
        }
    }
    
    private void saveDocumentWithProperNaming(String truckNumber, File selectedFile, String docType) {
        try {
            // Create truck's directory if it doesn't exist
            Path truckDir = Paths.get(docStoragePath, truckNumber);
            Files.createDirectories(truckDir);
            
            // Create filename with proper naming convention
            String extension = selectedFile.getName().substring(
                    selectedFile.getName().lastIndexOf('.'));
            
            // Clean the document type for filename
            String cleanDocType = docType.replaceAll("[\\\\/:*?\"<>|]", "_");
            
            // Create filename: TruckNumber_DocumentType.ext
            String newFileName = truckNumber + "_" + cleanDocType + extension;
            
            // Check for duplicates and add number if needed
            Path destPath = truckDir.resolve(newFileName);
            int counter = 1;
            while (Files.exists(destPath)) {
                String baseName = newFileName.substring(0, newFileName.lastIndexOf('.'));
                String ext = newFileName.substring(newFileName.lastIndexOf('.'));
                newFileName = baseName + "_" + counter + ext;
                destPath = truckDir.resolve(newFileName);
                counter++;
            }
            
            Files.copy(selectedFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Uploaded document for truck {}: {}", truckNumber, newFileName);
            new Alert(Alert.AlertType.INFORMATION, 
                     "Document uploaded successfully", 
                     ButtonType.OK).showAndWait();
            
        } catch (Exception e) {
            logger.error("Failed to upload document", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to upload document: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private void refreshDocumentList(ListView<DocumentItem> docListView, String truckNumber) {
        // Get categories from configuration
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> truckReqs = config.getTruckRequirements();
        String[] categories = truckReqs.keySet().toArray(new String[0]);
        
        ObservableList<DocumentItem> documents = loadCategorizedDocuments(truckNumber, categories);
        docListView.setItems(documents);
    }
    
    private void mergeDocuments(String truckNumber, ObservableList<DocumentItem> selectedDocs) {
        if (selectedDocs.size() < 2) {
            new Alert(Alert.AlertType.INFORMATION, 
                     "Please select at least 2 documents to merge", 
                     ButtonType.OK).showAndWait();
            return;
        }
        
        try {
            // Create merged document content
            StringBuilder mergedContent = new StringBuilder();
            mergedContent.append("MERGED DOCUMENTS FOR TRUCK: ").append(truckNumber).append("\n");
            mergedContent.append("Date: ").append(LocalDate.now()).append("\n\n");
            
            for (DocumentItem doc : selectedDocs) {
                mergedContent.append("Document: ").append(doc.getFileName()).append("\n");
                mergedContent.append("Category: ").append(doc.getCategory()).append("\n");
                mergedContent.append("Path: ").append(doc.getFile().getAbsolutePath()).append("\n");
                mergedContent.append("-".repeat(50)).append("\n");
            }
            
            // Save merged document
            Path truckDir = Paths.get(docStoragePath, truckNumber);
            String mergedFileName = truckNumber + "_Merged_Documents_" + 
                                 LocalDate.now().toString().replace("-", "") + ".txt";
            Path mergedPath = truckDir.resolve(mergedFileName);
            
            Files.write(mergedPath, mergedContent.toString().getBytes());
            
            logger.info("Created merged document: {}", mergedFileName);
            new Alert(Alert.AlertType.INFORMATION, 
                     "Documents merged successfully: " + mergedFileName, 
                     ButtonType.OK).showAndWait();
            
        } catch (Exception e) {
            logger.error("Failed to merge documents", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to merge documents: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private void deleteSelectedDocuments(String truckNumber, ObservableList<DocumentItem> selectedDocs) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + selectedDocs.size() + " selected document(s)?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                int deletedCount = 0;
                for (DocumentItem doc : selectedDocs) {
                    try {
                        Path docPath = Paths.get(docStoragePath, truckNumber, doc.getFileName());
                        if (Files.deleteIfExists(docPath)) {
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        logger.error("Failed to delete document: {}", doc.getFileName(), e);
                    }
                }
                
                logger.info("Deleted {} documents for truck {}", deletedCount, truckNumber);
                new Alert(Alert.AlertType.INFORMATION, 
                         "Deleted " + deletedCount + " document(s)", 
                         ButtonType.OK).showAndWait();
            }
        });
    }
    
    // Document item class for categorized document management
    private static class DocumentItem {
        private final String fileName;
        private final String category;
        private final File file;
        
        public DocumentItem(String fileName, String category, File file) {
            this.fileName = fileName;
            this.category = category;
            this.file = file;
        }
        
        public String getFileName() { return fileName; }
        public String getCategory() { return category; }
        public File getFile() { return file; }
        
        @Override
        public String toString() {
            return fileName;
        }
    }
    
    // Custom cell factory for document list with category color coding
    private static class DocumentListCell extends ListCell<DocumentItem> {
        @Override
        protected void updateItem(DocumentItem item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                setText(item.getFileName());
                
                // Color code by category
                Color categoryColor = getCategoryColor(item.getCategory());
                setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold;", 
                                    colorToHex(categoryColor)));
            }
        }
        
        private Color getCategoryColor(String category) {
            switch (category) {
                case "Annual DOT Inspection":
                case "Truck Registration":
                case "IRP Registration":
                case "IFTA Documentation":
                case "DOT Cab Card":
                    return Color.DARKGREEN; // Critical compliance documents
                case "Insurance Documents":
                case "Title or Lease Agreements":
                    return Color.DARKBLUE; // Legal documents
                case "Preventive Maintenance Records":
                case "Brake System Inspection":
                case "Tire Inspection Logs":
                    return Color.ORANGE; // Maintenance documents
                case "Emissions Compliance":
                case "Truck Repair Receipts":
                case "DVIRs":
                    return Color.PURPLE; // Operational documents
                default:
                    return Color.BLACK; // Other documents
            }
        }
        
        private String colorToHex(Color color) {
            return String.format("#%02X%02X%02X",
                    (int) (color.getRed() * 255),
                    (int) (color.getGreen() * 255),
                    (int) (color.getBlue() * 255));
        }
    }
    
    /**
     * Open truck's document folder
     */
    private void openTruckFolder(String truckNumber) {
        try {
            Path truckDir = Paths.get(docStoragePath, truckNumber);
            if (!Files.exists(truckDir)) {
                Files.createDirectories(truckDir);
            }
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(truckDir.toFile());
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Folder Location");
                alert.setContentText("Truck folder: " + truckDir.toString());
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Failed to open truck folder", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Open Failed");
            alert.setContentText("Failed to open folder: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Show export dialog for CSV/XLSX files.
     */
    private void showExportDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Export Trucks");
        dialog.setHeaderText("Choose export format");
        
        ButtonType csvButtonType = new ButtonType("CSV", ButtonBar.ButtonData.OK_DONE);
        ButtonType xlsxButtonType = new ButtonType("XLSX", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = ButtonType.CANCEL;
        
        dialog.getDialogPane().getButtonTypes().addAll(csvButtonType, xlsxButtonType, cancelButtonType);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == csvButtonType) {
                return "CSV";
            } else if (dialogButton == xlsxButtonType) {
                return "XLSX";
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(format -> {
            if (format.equals("CSV")) {
                exportToCSV();
            } else if (format.equals("XLSX")) {
                exportToXLSX();
            }
        });
    }
    
    /**
     * Export trucks data to CSV file
     */
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Trucks to CSV");
        fileChooser.setInitialFileName("trucks_" + 
                java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                
                // Write BOM for Excel UTF-8 recognition
                writer.write('\ufeff');
                
                // Write headers
                writer.write("Truck/Unit,VIN#,Make,Model,Year,Type,Status,Registration Expiry," +
                           "Insurance Expiry,Inspection Expiry,Inspection,Permit,License Plate,Driver");
                writer.newLine();
                
                // Filter the trucks if search filter is applied
                List<Truck> exportList = new ArrayList<>();
                if (table.getItems() != null) {
                    exportList.addAll(table.getItems());
                } else {
                    exportList.addAll(trucks);
                }
                
                // Write data
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                for (Truck truck : exportList) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCSV(truck.getNumber())).append(",");
                    line.append(escapeCSV(truck.getVin())).append(",");
                    line.append(escapeCSV(truck.getMake())).append(",");
                    line.append(escapeCSV(truck.getModel())).append(",");
                    line.append(truck.getYear()).append(",");
                    line.append(escapeCSV(truck.getType())).append(",");
                    line.append(escapeCSV(truck.getStatus())).append(",");
                    line.append(truck.getRegistrationExpiryDate() != null ? 
                            truck.getRegistrationExpiryDate().format(dateFormatter) : "").append(",");
                    line.append(truck.getInsuranceExpiryDate() != null ? 
                            truck.getInsuranceExpiryDate().format(dateFormatter) : "").append(",");
                    line.append(truck.getNextInspectionDue() != null ? 
                            truck.getNextInspectionDue().format(dateFormatter) : "").append(",");
                    line.append(truck.getInspection() != null ? 
                            truck.getInspection().format(dateFormatter) : "").append(",");
                    line.append(escapeCSV(truck.getPermitNumbers())).append(",");
                    line.append(escapeCSV(truck.getLicensePlate())).append(",");
                    line.append(escapeCSV(truck.getDriver()));
                    
                    writer.write(line.toString());
                    writer.newLine();
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("Export Completed");
                alert.setContentText("Exported " + exportList.size() + " trucks to CSV successfully.");
                alert.showAndWait();
                
            } catch (IOException e) {
                logger.error("Failed to export to CSV", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText("Export Error");
                alert.setContentText("Failed to export data: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }
    
    /**
     * Export trucks data to XLSX file
     */
    private void exportToXLSX() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Trucks to Excel");
        fileChooser.setInitialFileName("trucks_" + 
                java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Trucks");
                
                // Create header row
                String[] headers = {"Truck/Unit", "VIN#", "Make", "Model", "Year", "Type", "Status", 
                                   "Registration Expiry", "Insurance Expiry", "Inspection Expiry", 
                                   "Inspection", "Permit", "License Plate", "Driver"};
                
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    // Make the header bold
                    org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                    org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                    headerFont.setBold(true);
                    headerStyle.setFont(headerFont);
                    cell.setCellStyle(headerStyle);
                }
                
                // Filter the trucks if search filter is applied
                List<Truck> exportList = new ArrayList<>();
                if (table.getItems() != null) {
                    exportList.addAll(table.getItems());
                } else {
                    exportList.addAll(trucks);
                }
                
                // Create data rows
                int rowNum = 1;
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("mm/dd/yyyy"));
                
                for (Truck truck : exportList) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    
                    row.createCell(0).setCellValue(truck.getNumber());
                    row.createCell(1).setCellValue(truck.getVin());
                    row.createCell(2).setCellValue(truck.getMake());
                    row.createCell(3).setCellValue(truck.getModel());
                    row.createCell(4).setCellValue(truck.getYear());
                    row.createCell(5).setCellValue(truck.getType());
                    row.createCell(6).setCellValue(truck.getStatus());
                    
                    // Registration expiry date
                    org.apache.poi.ss.usermodel.Cell regExpiryCell = row.createCell(7);
                    if (truck.getRegistrationExpiryDate() != null) {
                        regExpiryCell.setCellValue(java.util.Date.from(truck.getRegistrationExpiryDate().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        regExpiryCell.setCellStyle(dateStyle);
                    }
                    
                    // Insurance expiry date
                    org.apache.poi.ss.usermodel.Cell insExpiryCell = row.createCell(8);
                    if (truck.getInsuranceExpiryDate() != null) {
                        insExpiryCell.setCellValue(java.util.Date.from(truck.getInsuranceExpiryDate().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        insExpiryCell.setCellStyle(dateStyle);
                    }
                    
                    // Inspection expiry date
                    org.apache.poi.ss.usermodel.Cell inspExpiryCell = row.createCell(9);
                    if (truck.getNextInspectionDue() != null) {
                        inspExpiryCell.setCellValue(java.util.Date.from(truck.getNextInspectionDue().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        inspExpiryCell.setCellStyle(dateStyle);
                    }
                    
                    // Inspection date
                    org.apache.poi.ss.usermodel.Cell inspCell = row.createCell(10);
                    if (truck.getInspection() != null) {
                        inspCell.setCellValue(java.util.Date.from(truck.getInspection().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        inspCell.setCellStyle(dateStyle);
                    }
                    
                    row.createCell(11).setCellValue(truck.getPermitNumbers());
                    row.createCell(12).setCellValue(truck.getLicensePlate());
                    row.createCell(13).setCellValue(truck.getDriver());
                }
                
                // Auto-size columns for better readability
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
                
                // Write to file
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    workbook.write(outputStream);
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("Export Completed");
                alert.setContentText("Exported " + exportList.size() + " trucks to Excel successfully.");
                alert.showAndWait();
                
            } catch (Exception e) {
                logger.error("Failed to export to Excel", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText("Export Error");
                alert.setContentText("Failed to export data: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }
    
    /**
     * Escape special characters for CSV format
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Save all pending changes (called during application exit)
     */
    public void saveAllPendingChanges() {
        logger.info("Saving all pending truck changes during application exit");
        try {
            // Force refresh table to ensure any pending edits are committed
            if (table != null) {
                table.refresh();
            }
            
            // Any additional save logic can be added here
            // The data is already saved automatically when changes are made via the dialogs
            
            logger.info("All pending truck changes saved successfully");
        } catch (Exception e) {
            logger.error("Error saving pending truck changes", e);
        }
    }
}