package com.company.payroll.trucks;

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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

/**
 * Enhanced Trucks tab for managing fleet information with focus on document tracking
 * and expiration dates.
 */
public class TrucksTab extends BorderPane {
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
        
        TableColumn<Truck, String> permitCol = new TableColumn<>("Permit Numbers");
        permitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPermitNumbers()));
        permitCol.setPrefWidth(120);
        
        table.getColumns().setAll(java.util.List.of(
                unitCol, vinCol, makeModelCol, typeCol, statusCol, driverCol,
                regExpiryCol, insExpiryCol, inspExpiryCol, permitCol));
        
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
        Button uploadBtn = ModernButtonStyles.createInfoButton("📤 Upload Document");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print Document");
        
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
        
        HBox btnBox = new HBox(12, addBtn, editBtn, deleteBtn, refreshBtn, uploadBtn, printBtn);
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
        
        trucks.setAll(truckList);
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
        TextField permitField = new TextField();
        TextField licenseField = new TextField();
        
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
            permitField.setText(truck.getPermitNumbers());
            licenseField.setText(truck.getLicensePlate());
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
        
        grid.add(new Label("Permit Numbers:"), 0, row);
        grid.add(permitField, 1, row++);
        
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
                    result.setNumber(numberField.getText().trim());
                    result.setVin(vinField.getText().trim());
                    result.setMake(makeField.getText().trim());
                    result.setModel(modelField.getText().trim());
                    
                    try {
                        if (!yearField.getText().trim().isEmpty()) {
                            result.setYear(Integer.parseInt(yearField.getText().trim()));
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid year format: {}", yearField.getText());
                    }
                    
                    result.setType(typeBox.getValue());
                    result.setStatus(statusBox.getValue());
                    result.setLicensePlate(licenseField.getText().trim());
                    result.setRegistrationExpiryDate(regExpiryPicker.getValue());
                    result.setInsuranceExpiryDate(insExpiryPicker.getValue());
                    result.setNextInspectionDue(inspExpiryPicker.getValue());
                    result.setPermitNumbers(permitField.getText().trim());
                    
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
            loadData();
            notifyDataChange();
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
        dialog.setTitle("Document Manager - " + selected.getNumber());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create document list view
        ListView<String> docListView = new ListView<>();
        updateDocumentList(docListView, selected.getNumber());
        
        // Buttons for document operations
        Button uploadBtn = ModernButtonStyles.createPrimaryButton("📤 Upload");
        Button viewBtn = ModernButtonStyles.createInfoButton("👁️ View");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete");
        
        uploadBtn.setOnAction(e -> {
            uploadDocumentForTruck(selected.getNumber());
            updateDocumentList(docListView, selected.getNumber());
        });
        
        viewBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                viewDocument(selected.getNumber(), selectedDoc);
            }
        });
        
        printBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                printDocument(selected.getNumber(), selectedDoc);
            }
        });
        
        deleteBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                deleteDocument(selected.getNumber(), selectedDoc);
                updateDocumentList(docListView, selected.getNumber());
            }
        });
        
        HBox buttonBox = new HBox(10, uploadBtn, viewBtn, printBtn, deleteBtn);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setAlignment(Pos.CENTER);
        
        VBox content = new VBox(10, 
                               new Label("Documents for " + selected.getNumber()), 
                               docListView, 
                               buttonBox);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setPrefHeight(400);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
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
        List<Truck> expiringTrucks = trucks.stream()
            .filter(t -> (t.getRegistrationExpiryDate() != null && 
                         t.getRegistrationExpiryDate().isBefore(LocalDate.now().plusMonths(1))) ||
                        (t.getInsuranceExpiryDate() != null && 
                         t.getInsuranceExpiryDate().isBefore(LocalDate.now().plusMonths(1))) ||
                        (t.getNextInspectionDue() != null && 
                         t.getNextInspectionDue().isBefore(LocalDate.now().plusMonths(1))))
            .collect(java.util.stream.Collectors.toList());
            
        if (expiringTrucks.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, 
                     "No documents expiring within the next month", 
                     ButtonType.OK).showAndWait();
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Document Expiry Alerts");
        dialog.setHeaderText("Documents Expiring Within 30 Days");
        
        TableView<Truck> alertTable = new TableView<>();
        
        TableColumn<Truck, String> unitCol = new TableColumn<>("Truck/Unit");
        unitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumber()));
        
        TableColumn<Truck, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDriver()));
        
        TableColumn<Truck, LocalDate> regCol = new TableColumn<>("Registration");
        regCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getRegistrationExpiryDate()));
        regCol.setCellFactory(getExpiryDateCellFactory());
        
        TableColumn<Truck, LocalDate> insCol = new TableColumn<>("Insurance");
        insCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getInsuranceExpiryDate()));
        insCol.setCellFactory(getExpiryDateCellFactory());
        
        TableColumn<Truck, LocalDate> inspCol = new TableColumn<>("Inspection");
        inspCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getNextInspectionDue()));
        inspCol.setCellFactory(getExpiryDateCellFactory());
        
        alertTable.getColumns().setAll(java.util.List.of(unitCol, driverCol, regCol, insCol, inspCol));
        alertTable.setItems(FXCollections.observableArrayList(expiringTrucks));
        
        VBox content = new VBox(10, alertTable);
        content.setPrefWidth(600);
        content.setPrefHeight(400);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
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
}