package com.company.payroll.employees;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Orientation;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import com.company.payroll.employees.EmployeeDocumentManager;
import com.company.payroll.config.DOTComplianceConfigDialog;

public class EmployeesTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(EmployeesTab.class);
    
    private final ObservableList<Employee> employees = FXCollections.observableArrayList();
    private final EmployeeDAO dao = new EmployeeDAO();
    
    // References to truck and trailer tabs for dropdown data
    private com.company.payroll.trucks.TrucksTab trucksTab;
    private com.company.payroll.trailers.TrailersTab trailersTab;

    // Listeners for employee data changed events
    public interface EmployeeDataChangeListener {
        void onEmployeeDataChanged(List<Employee> currentList);
    }
    private final List<EmployeeDataChangeListener> listeners = new ArrayList<>();

    // UI Components
    private TableView<Employee> table;
    private TextField searchField;
    private Label recordCountLabel;
    private Label statusLabel;
    
    public EmployeesTab() {
        logger.info("Initializing EmployeesTab");
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #F5F5F5;");
        
        // Load from DB on startup
        employees.setAll(dao.getAll());
        logger.info("Loaded {} employees", employees.size());
        
        // Create main container
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
        table = createEnhancedTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // Add all components
        mainContainer.getChildren().addAll(headerSection, actionBar, filterSection, statusBar, table);
        
        setCenter(mainContainer);
        
        // Setup filters and listeners
        setupFilters();
        updateRecordCount();
        
        logger.info("EmployeesTab initialization complete");
    }
    
    /**
     * Original table creation code - to be replaced by createEnhancedTable
     */
    private TableView<Employee> createOriginalTable() {
        TableView<Employee> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Employee, String> nameCol = new TableColumn<>("Driver Name");
        nameCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getName()));

        TableColumn<Employee, String> truckCol = new TableColumn<>("Truck/Unit");
        truckCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTruckUnit()));

        TableColumn<Employee, String> trailerCol = new TableColumn<>("Trailer #");
        trailerCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTrailerNumber()));
        
        TableColumn<Employee, String> phoneCol = new TableColumn<>("Mobile #");
        phoneCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getPhone()));
        
        TableColumn<Employee, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getEmail()));

        TableColumn<Employee, Number> driverPctCol = new TableColumn<>("Driver %");
        driverPctCol.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getDriverPercent()));

        TableColumn<Employee, Number> companyPctCol = new TableColumn<>("Company %");
        companyPctCol.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getCompanyPercent()));

        TableColumn<Employee, Number> serviceFeeCol = new TableColumn<>("Service Fee %");
        serviceFeeCol.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getServiceFeePercent()));

        TableColumn<Employee, String> dobCol = new TableColumn<>("DOB");
        dobCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDob() != null ? e.getValue().getDob().toString() : ""));

        TableColumn<Employee, String> licenseCol = new TableColumn<>("License #");
        licenseCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getLicenseNumber()));

        TableColumn<Employee, String> driverTypeCol = new TableColumn<>("Driver Type");
        driverTypeCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDriverType() != null ? e.getValue().getDriverType().name().replace("_", " ") : ""));

        TableColumn<Employee, String> llcCol = new TableColumn<>("Employee LLC");
        llcCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getEmployeeLLC()));

        TableColumn<Employee, LocalDate> cdlExpiryCol = new TableColumn<>("CDL Expiry");
        cdlExpiryCol.setCellValueFactory(new PropertyValueFactory<>("cdlExpiry"));
        cdlExpiryCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Employee, LocalDate> medExpiryCol = new TableColumn<>("Medical Expiry");
        medExpiryCol.setCellValueFactory(new PropertyValueFactory<>("medicalExpiry"));
        medExpiryCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Employee, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getStatus() != null ? e.getValue().getStatus().name().replace("_", " ") : ""));

        // Documents column with upload button
        TableColumn<Employee, Void> docCol = new TableColumn<>("Documents");
        docCol.setCellFactory(col -> new TableCell<>() {
            private final Button uploadBtn = new Button("Upload");
            private final Button viewBtn = new Button("View");
            private final HBox btnBox = new HBox(6, uploadBtn, viewBtn);
            {
                uploadBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    // Select document type
                    ChoiceDialog<String> typeDialog = new ChoiceDialog<>("Driver License", "Driver License", "Medical", "IRP", "IFTA", "Other");
                    typeDialog.setTitle("Select Document Type");
                    typeDialog.setHeaderText("Select Document Type for " + emp.getName());
                    typeDialog.setContentText("Document Type:");
                    typeDialog.initOwner(getTableView().getScene().getWindow());
                    typeDialog.setGraphic(null);
                    typeDialog.getDialogPane().setPrefWidth(300);
                    typeDialog.getDialogPane().setPrefHeight(150);
                    typeDialog.getDialogPane().setPadding(new Insets(10));
                    typeDialog.getDialogPane().setStyle("-fx-font-size: 14px;");
                    typeDialog.showAndWait().ifPresent(docType -> {
                        FileChooser fc = new FileChooser();
                        fc.setTitle("Upload " + docType + " for " + emp.getName());
                        File file = fc.showOpenDialog(getTableView().getScene().getWindow());
                        if (file != null) {
                            try {
                                String safeName = emp.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
                                String safeType = docType.replaceAll("[^a-zA-Z0-9_\\- ]", "_");
                                String ext = "";
                                int dot = file.getName().lastIndexOf('.');
                                if (dot > 0) ext = file.getName().substring(dot);
                                String baseName = safeName + "_" + safeType + "_" + file.getName().replaceAll("[^a-zA-Z0-9_\\-\\. ]", "_");
                                // Robust: append timestamp if file exists
                                Path folder = Path.of("Employee_Doc", safeName, "AllDocuments");
                                Files.createDirectories(folder);
                                Path dest = folder.resolve(baseName);
                                if (Files.exists(dest)) {
                                    String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now());
                                    dest = folder.resolve(safeName + "_" + safeType + "_" + ts + ext);
                                }
                                Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Document uploaded to: " + dest.toString(), ButtonType.OK);
                                alert.setHeaderText("Upload Successful");
                                alert.showAndWait();
                            } catch (IOException ex) {
                                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to upload: " + ex.getMessage(), ButtonType.OK);
                                alert.setHeaderText("Upload Error");
                                alert.showAndWait();
                            }
                        }
                    });
                });
                viewBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    String safeName = emp.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
                    Path folder = Path.of("Employee_Doc", safeName, "AllDocuments");
                    List<Path> files = new ArrayList<>();
                    if (Files.exists(folder) && Files.isDirectory(folder)) {
                        try (var stream = Files.list(folder)) {
                            stream.filter(Files::isRegularFile).forEach(files::add);
                        } catch (IOException ex) {
                            files.clear();
                        }
                    }
                    Dialog<Void> dialog = new Dialog<>();
                    dialog.setTitle("Documents for " + emp.getName());
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    VBox vbox = new VBox(10);
                    vbox.setPadding(new Insets(10));
                    if (files.isEmpty()) {
                        vbox.getChildren().add(new Label("No documents found."));
                    } else {
                        for (Path file : files) {
                            HBox row = new HBox(10);
                            row.setAlignment(Pos.CENTER_LEFT);
                            String fname = file.getFileName().toString();
                            String docType = "Unknown";
                            String[] parts = fname.split("_");
                            if (parts.length >= 2) docType = parts[1];
                            String date = "";
                            try {
                                date = Files.getLastModifiedTime(file).toString();
                            } catch (IOException ignored) {}
                            Label nameLbl = new Label(fname);
                            Label typeLbl = new Label(docType);
                            Label dateLbl = new Label(date);
                            Button openBtn = new Button("Open");
                            openBtn.setOnAction(ev -> {
                                try {
                                    java.awt.Desktop.getDesktop().open(file.toFile());
                                } catch (Exception ex) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open: " + ex.getMessage(), ButtonType.OK);
                                    alert.setHeaderText("Open Error");
                                    alert.showAndWait();
                                }
                            });
                            Button folderBtn = new Button("Show Folder");
                            folderBtn.setOnAction(ev -> {
                                try {
                                    java.awt.Desktop.getDesktop().open(folder.toFile());
                                } catch (Exception ex) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open folder: " + ex.getMessage(), ButtonType.OK);
                                    alert.setHeaderText("Open Folder Error");
                                    alert.showAndWait();
                                }
                            });
                            row.getChildren().addAll(nameLbl, new Label("|"), typeLbl, new Label("|"), dateLbl, openBtn, folderBtn);
                            vbox.getChildren().add(row);
                        }
                    }
                    dialog.getDialogPane().setContent(new ScrollPane(vbox));
                    dialog.showAndWait();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnBox);
                }
            }
        });
        
        // Action column
        TableColumn<Employee, Void> actionCol = new TableColumn<>("Actions");
        
        // Add all columns to table
        @SuppressWarnings("unchecked")
        TableColumn<Employee, ?>[] columns = new TableColumn[] {
                nameCol, truckCol, trailerCol, phoneCol, emailCol,
                driverPctCol, companyPctCol, serviceFeeCol, dobCol, licenseCol,
                driverTypeCol, llcCol, cdlExpiryCol, medExpiryCol, statusCol, docCol, actionCol
        };
        table.getColumns().addAll(columns);
                
        return table;
    }

    // Dialog for Add/Edit, with duplicate driver name detection
    private void showEmployeeDialog(Employee employee, boolean isAdd) {
        logger.debug("Showing employee dialog - isAdd: {}", isAdd);
        Dialog<Employee> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add Employee" : "Edit Employee");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Fields
        TextField nameField = new TextField();
        ComboBox<String> truckComboBox = new ComboBox<>();
        ComboBox<String> trailerComboBox = new ComboBox<>();
        TextField phoneField = new TextField();
        TextField emailField = new TextField();
        TextField driverPctField = new TextField();
        TextField companyPctField = new TextField();
        TextField serviceFeeField = new TextField();
        DatePicker dobPicker = new DatePicker();
        TextField licenseField = new TextField();
        ComboBox<Employee.DriverType> driverTypeBox = new ComboBox<>(
                FXCollections.observableArrayList(Employee.DriverType.values()));
        TextField llcField = new TextField();
        DatePicker cdlPicker = new DatePicker();
        DatePicker medPicker = new DatePicker();
        ComboBox<Employee.Status> statusBox = new ComboBox<>(
                FXCollections.observableArrayList(Employee.Status.values()));

        // Setup truck dropdown
        truckComboBox.setEditable(true);
        truckComboBox.setPromptText("Select or enter truck/unit");
        if (trucksTab != null) {
            try {
                // Get available trucks (not assigned or can be assigned to current employee)
                java.util.List<com.company.payroll.trucks.Truck> availableTrucks = trucksTab.getCurrentTrucks()
                    .stream()
                    .filter(truck -> truck.getNumber() != null && !truck.getNumber().trim().isEmpty())
                    .filter(truck -> !truck.isAssigned() || 
                           (employee != null && truck.getNumber().equals(employee.getTruckUnit())))
                    .collect(java.util.stream.Collectors.toList());
                
                ObservableList<String> truckOptions = FXCollections.observableArrayList();
                truckOptions.add(""); // Empty option for no assignment
                for (com.company.payroll.trucks.Truck truck : availableTrucks) {
                    String displayText = truck.getNumber();
                    if (truck.getMake() != null || truck.getModel() != null) {
                        displayText += " (" + 
                            (truck.getMake() != null ? truck.getMake() : "") + " " +
                            (truck.getModel() != null ? truck.getModel() : "") + ")";
                    }
                    truckOptions.add(displayText.trim());
                }
                truckComboBox.setItems(truckOptions);
                logger.debug("Loaded {} truck options for employee dialog", truckOptions.size() - 1);
            } catch (Exception e) {
                logger.warn("Failed to load truck options: {}", e.getMessage());
                truckComboBox.setItems(FXCollections.observableArrayList(""));
            }
        } else {
            truckComboBox.setItems(FXCollections.observableArrayList(""));
        }

        // Setup trailer dropdown
        trailerComboBox.setEditable(true);
        trailerComboBox.setPromptText("Select or enter trailer #");
        if (trailersTab != null) {
            try {
                // Get available trailers (not assigned or can be assigned to current employee)
                java.util.List<com.company.payroll.trailers.Trailer> availableTrailers = trailersTab.getCurrentTrailers()
                    .stream()
                    .filter(trailer -> trailer.getTrailerNumber() != null && !trailer.getTrailerNumber().trim().isEmpty())
                    .filter(trailer -> !trailer.isAssigned() || 
                           (employee != null && trailer.getTrailerNumber().equals(employee.getTrailerNumber())))
                    .collect(java.util.stream.Collectors.toList());
                
                ObservableList<String> trailerOptions = FXCollections.observableArrayList();
                trailerOptions.add(""); // Empty option for no assignment
                for (com.company.payroll.trailers.Trailer trailer : availableTrailers) {
                    String displayText = trailer.getTrailerNumber();
                    if (trailer.getType() != null) {
                        displayText += " (" + trailer.getType() + ")";
                    }
                    trailerOptions.add(displayText.trim());
                }
                trailerComboBox.setItems(trailerOptions);
                logger.debug("Loaded {} trailer options for employee dialog", trailerOptions.size() - 1);
            } catch (Exception e) {
                logger.warn("Failed to load trailer options: {}", e.getMessage());
                trailerComboBox.setItems(FXCollections.observableArrayList(""));
            }
        } else {
            trailerComboBox.setItems(FXCollections.observableArrayList(""));
        }

        if (employee != null) {
            nameField.setText(employee.getName());
            // Set truck selection
            if (employee.getTruckUnit() != null && !employee.getTruckUnit().trim().isEmpty()) {
                // Try to find exact match first
                String truckToSelect = employee.getTruckUnit();
                boolean found = false;
                for (String option : truckComboBox.getItems()) {
                    if (option.startsWith(truckToSelect + " ") || option.equals(truckToSelect)) {
                        truckComboBox.setValue(option);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    truckComboBox.setValue(truckToSelect); // Set manually if not in list
                }
            }
            // Set trailer selection
            if (employee.getTrailerNumber() != null && !employee.getTrailerNumber().trim().isEmpty()) {
                // Try to find exact match first
                String trailerToSelect = employee.getTrailerNumber();
                boolean found = false;
                for (String option : trailerComboBox.getItems()) {
                    if (option.startsWith(trailerToSelect + " ") || option.equals(trailerToSelect)) {
                        trailerComboBox.setValue(option);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    trailerComboBox.setValue(trailerToSelect); // Set manually if not in list
                }
            }
            phoneField.setText(employee.getPhone());
            emailField.setText(employee.getEmail());
            driverPctField.setText(String.valueOf(employee.getDriverPercent()));
            companyPctField.setText(String.valueOf(employee.getCompanyPercent()));
            serviceFeeField.setText(String.valueOf(employee.getServiceFeePercent()));
            dobPicker.setValue(employee.getDob());
            licenseField.setText(employee.getLicenseNumber());
            driverTypeBox.setValue(employee.getDriverType());
            llcField.setText(employee.getEmployeeLLC());
            cdlPicker.setValue(employee.getCdlExpiry());
            medPicker.setValue(employee.getMedicalExpiry());
            statusBox.setValue(employee.getStatus());
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        errorLabel.setVisible(false);

        GridPane grid = new GridPane();
        grid.setVgap(7);
        grid.setHgap(12);
        grid.setPadding(new Insets(15, 20, 10, 10));
        int r = 0;
        grid.add(new Label("Driver Name*:"), 0, r);        grid.add(nameField, 1, r++);
        grid.add(new Label("Truck/Unit:"), 0, r);          grid.add(truckComboBox, 1, r++);
        grid.add(new Label("Trailer #:"), 0, r);           grid.add(trailerComboBox, 1, r++);
        grid.add(new Label("Mobile #:"), 0, r);            grid.add(phoneField, 1, r++);
        grid.add(new Label("Email:"), 0, r);               grid.add(emailField, 1, r++);
        grid.add(new Label("Driver %*:"), 0, r);           grid.add(driverPctField, 1, r++);
        grid.add(new Label("Company %:"), 0, r);           grid.add(companyPctField, 1, r++);
        grid.add(new Label("Service Fee %:"), 0, r);       grid.add(serviceFeeField, 1, r++);
        grid.add(new Label("DOB:"), 0, r);                 grid.add(dobPicker, 1, r++);
        grid.add(new Label("License #:"), 0, r);           grid.add(licenseField, 1, r++);
        grid.add(new Label("Driver Type:"), 0, r);         grid.add(driverTypeBox, 1, r++);
        grid.add(new Label("Employee LLC:"), 0, r);        grid.add(llcField, 1, r++);
        grid.add(new Label("CDL Expiry:"), 0, r);          grid.add(cdlPicker, 1, r++);
        grid.add(new Label("Medical Expiry:"), 0, r);      grid.add(medPicker, 1, r++);
        grid.add(new Label("Status:"), 0, r);              grid.add(statusBox, 1, r++);
        grid.add(errorLabel, 1, r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        // Validation logic
        Runnable validate = () -> {
            boolean nameValid = !nameField.getText().trim().isEmpty();
            boolean driverPctValid = isDouble(driverPctField.getText());
            boolean duplicate = checkDuplicateDriverName(
                    nameField.getText().trim(),
                    isAdd ? -1 : (employee != null ? employee.getId() : -1)
            );
            String selectedTruck = extractTruckUnit(truckComboBox.getValue());
            boolean dupTruck = checkDuplicateTruckUnit(
                    selectedTruck,
                    isAdd ? -1 : (employee != null ? employee.getId() : -1)
            );
            
            if (duplicate && nameValid) {
                errorLabel.setText("Driver with this name already exists.");
                errorLabel.setVisible(true);
            } else if (dupTruck && selectedTruck != null && !selectedTruck.trim().isEmpty()) {
                errorLabel.setText("Truck/Unit already assigned to another driver.");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
            okBtn.setDisable(!(nameValid && driverPctValid) || duplicate || dupTruck);
        };

        nameField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        truckComboBox.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        driverPctField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        validate.run(); // initial

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    // Safe text extraction with null checks
                    String name = nameField != null ? nameField.getText() : "";
                    if (name != null) name = name.trim();
                    
                    String truck = truckComboBox != null ? extractTruckUnit(truckComboBox.getValue()) : "";
                    String trailer = trailerComboBox != null ? extractTrailerNumber(trailerComboBox.getValue()) : "";
                    
                    String phone = phoneField != null ? phoneField.getText() : "";
                    if (phone != null) phone = phone.trim();
                    
                    String email = emailField != null ? emailField.getText() : "";
                    if (email != null) email = email.trim();
                    
                    double driverPct = driverPctField != null ? parseDouble(driverPctField.getText()) : 0.0;
                    double companyPct = companyPctField != null ? parseDouble(companyPctField.getText()) : 0.0;
                    double serviceFee = serviceFeeField != null ? parseDouble(serviceFeeField.getText()) : 0.0;
                    
                    LocalDate dob = dobPicker != null ? dobPicker.getValue() : null;
                    
                    String license = licenseField != null ? licenseField.getText() : "";
                    if (license != null) license = license.trim();
                    
                    Employee.DriverType driverType = driverTypeBox != null ? driverTypeBox.getValue() : null;
                    
                    String llc = llcField != null ? llcField.getText() : "";
                    if (llc != null) llc = llc.trim();
                    
                    LocalDate cdlExp = cdlPicker != null ? cdlPicker.getValue() : null;
                    LocalDate medExp = medPicker != null ? medPicker.getValue() : null;
                    
                    Employee.Status status = statusBox != null ? statusBox.getValue() : null;

                    if (isAdd) {
                        logger.info("Adding new employee: {}", name);
                        Employee emp = new Employee(0, name, truck, trailer, driverPct, companyPct, serviceFee, dob, license, driverType, llc, cdlExp, medExp, status);
                        emp.setPhone(phone);
                        emp.setEmail(email);
                        int newId = dao.add(emp);
                        emp.setId(newId);
                        employees.setAll(dao.getAll());
                        notifyEmployeeDataChanged();
                        logger.info("Employee added successfully: {} (ID: {})", name, newId);
                        
                        // Force table refresh to show changes immediately
                        if (table != null) {
                            table.refresh();
                        }
                        
                        return emp;
                    } else {
                        logger.info("Updating employee: {} (ID: {})", name, employee.getId());
                        employee.setName(name);
                        employee.setTruckUnit(truck);
                        employee.setTrailerNumber(trailer);
                        employee.setPhone(phone);
                        employee.setEmail(email);
                        employee.setDriverPercent(driverPct);
                        employee.setCompanyPercent(companyPct);
                        employee.setServiceFeePercent(serviceFee);
                        employee.setDob(dob);
                        employee.setLicenseNumber(license);
                        employee.setDriverType(driverType);
                        employee.setEmployeeLLC(llc);
                        employee.setCdlExpiry(cdlExp);
                        employee.setMedicalExpiry(medExp);
                        employee.setStatus(status);
                        dao.update(employee);
                        employees.setAll(dao.getAll());
                        notifyEmployeeDataChanged();
                        logger.info("Employee updated successfully: {} (ID: {})", name, employee.getId());
                        
                        // Force table refresh to show changes immediately
                        if (table != null) {
                            table.refresh();
                        }
                        
                        return employee;
                    }
                } catch (Exception ex) {
                    logger.error("Error in employee dialog: {}", ex.getMessage(), ex);
                    ex.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    // --- Listener registration for other tabs (e.g. PayrollTab) ---
    public void addEmployeeDataChangeListener(EmployeeDataChangeListener listener) {
        listeners.add(listener);
        logger.debug("Added employee data change listener: {}", listener.getClass().getSimpleName());
    }
    public void removeEmployeeDataChangeListener(EmployeeDataChangeListener listener) {
        listeners.remove(listener);
        logger.debug("Removed employee data change listener: {}", listener.getClass().getSimpleName());
    }
    private void notifyEmployeeDataChanged() {
        logger.debug("Notifying {} listeners of employee data change", listeners.size());
        for (EmployeeDataChangeListener listener : listeners) {
            listener.onEmployeeDataChanged(new ArrayList<>(employees));
        }
    }
    
    // --- Methods to set truck and trailer tab references ---
    public void setTrucksTab(com.company.payroll.trucks.TrucksTab trucksTab) {
        this.trucksTab = trucksTab;
        logger.debug("TrucksTab reference set for dropdown integration");
    }
    
    public void setTrailersTab(com.company.payroll.trailers.TrailersTab trailersTab) {
        this.trailersTab = trailersTab;
        logger.debug("TrailersTab reference set for dropdown integration");
    }

    // Check for duplicate driver name, ignoring case and the current record (for edits)
    private boolean checkDuplicateDriverName(String name, int excludeId) {
        String normName = name.trim().toLowerCase(Locale.ROOT);
        for (Employee emp : employees) {
            if (emp.getId() != excludeId && emp.getName() != null &&
                emp.getName().trim().toLowerCase(Locale.ROOT).equals(normName)) {
                logger.debug("Duplicate driver name found: {}", name);
                return true;
            }
        }
        return false;
    }

    // Check for duplicate truck unit
    private boolean checkDuplicateTruckUnit(String truckUnit, int excludeId) {
        if (truckUnit == null || truckUnit.trim().isEmpty()) return false;
        String normTruck = truckUnit.trim().toLowerCase(Locale.ROOT);
        for (Employee emp : employees) {
            if (emp.getId() != excludeId && emp.getTruckUnit() != null &&
                emp.getTruckUnit().trim().toLowerCase(Locale.ROOT).equals(normTruck)) {
                logger.debug("Duplicate truck unit found: {}", truckUnit);
                return true;
            }
        }
        return false;
    }

    private boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; }
        catch (Exception e) { return false; }
    }
    private double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return 0.0;
        return Double.parseDouble(s.trim());
    }
    
    // Helper method to extract truck unit from combo box display text
    private String extractTruckUnit(String displayText) {
        if (displayText == null || displayText.trim().isEmpty()) {
            return "";
        }
        // If the display text contains parentheses, extract just the unit number
        int parenIndex = displayText.indexOf(" (");
        if (parenIndex > 0) {
            return displayText.substring(0, parenIndex).trim();
        }
        return displayText.trim();
    }
    
    // Helper method to extract trailer number from combo box display text  
    private String extractTrailerNumber(String displayText) {
        if (displayText == null || displayText.trim().isEmpty()) {
            return "";
        }
        // If the display text contains parentheses, extract just the trailer number
        int parenIndex = displayText.indexOf(" (");
        if (parenIndex > 0) {
            return displayText.substring(0, parenIndex).trim();
        }
        return displayText.trim();
    }

    // Color coding for expiry columns
    private Callback<TableColumn<Employee, LocalDate>, TableCell<Employee, LocalDate>> getExpiryCellFactory() {
        return col -> new TableCell<Employee, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(date == null ? "" : date.toString());
                if (!empty && date != null) {
                    LocalDate now = LocalDate.now();
                    if (date.isBefore(now)) {
                        setStyle("-fx-background-color: #ffcccc; -fx-font-weight: bold;"); // Red
                    } else if (date.isBefore(now.plusMonths(2))) {
                        setStyle("-fx-background-color: #fff3cd; -fx-font-weight: bold;"); // Yellow
                    } else {
                        setStyle("");
                    }
                } else {
                    setStyle("");
                }
            }
        };
    }

    // Expose current employees list (unmodifiable copy)
    public List<Employee> getCurrentEmployees() {
        return new ArrayList<>(employees);
    }
    
    /**
     * Create header section with title and description
     */
    private VBox createHeaderSection() {
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));
        
        Label title = new Label("Employee Management");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#1976D2"));
        
        Label subtitle = new Label("Manage drivers, employees, and their information");
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
        Button addBtn = createStyledButton("➕ Add Employee", "#4CAF50");
        Button editBtn = createStyledButton("✏️ Edit", "#2196F3");
        Button deleteBtn = createStyledButton("🗑️ Delete", "#F44336");
        Button refreshBtn = createStyledButton("🔄 Refresh", "#00BCD4");
        Button importBtn = createStyledButton("📥 Import CSV/XLSX", "#FF9800");
        Button percentageBtn = createStyledButton("💰 Configure Percentages", "#FF9800");
        Button exportBtn = createStyledButton("📊 Export", "#9C27B0");
        
        // Setup button actions
        setupButtonActions(addBtn, editBtn, deleteBtn, refreshBtn, importBtn, percentageBtn, exportBtn);
        
        Separator separator1 = new Separator(Orientation.VERTICAL);
        separator1.setPadding(new Insets(0, 5, 0, 5));
        
        Separator separator2 = new Separator(Orientation.VERTICAL);
        separator2.setPadding(new Insets(0, 5, 0, 5));
        
        // Add configure button for DOT compliance
        Button configureBtn = createStyledButton("⚙️ Configure", "#6c757d");
        configureBtn.setOnAction(e -> showDOTComplianceConfiguration());
        
        actionBar.getChildren().addAll(addBtn, editBtn, deleteBtn, separator1, 
                                      importBtn, percentageBtn, exportBtn, refreshBtn, separator2, configureBtn);
        
        return actionBar;
    }
    
    /**
     * Create filter section with enhanced search
     */
    private VBox createFilterSection() {
        VBox filterBox = new VBox(10);
        filterBox.setPadding(new Insets(15));
        filterBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label filterTitle = new Label("Search & Filters");
        filterTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        filterTitle.setTextFill(Color.web("#1976D2"));
        
        // Search field with enhanced styling
        searchField = new TextField();
        searchField.setPromptText("🔍 Search by name, license, truck, phone, email...");
        searchField.setPrefWidth(350);
        searchField.setStyle("-fx-font-size: 14px; -fx-padding: 8px;");
        
        // Filter controls row
        HBox filterControls = new HBox(15);
        filterControls.setAlignment(Pos.CENTER_LEFT);
        
        ComboBox<Employee.Status> statusFilter = new ComboBox<>();
        statusFilter.getItems().add(null); // "Any"
        statusFilter.getItems().addAll(Employee.Status.values());
        statusFilter.setPromptText("All Statuses");
        statusFilter.setPrefWidth(150);
        statusFilter.setId("statusFilter");
        
        ComboBox<Employee.DriverType> typeFilter = new ComboBox<>();
        typeFilter.getItems().add(null); // "Any"
        typeFilter.getItems().addAll(Employee.DriverType.values());
        typeFilter.setPromptText("All Driver Types");
        typeFilter.setPrefWidth(150);
        typeFilter.setId("typeFilter");
        
        // Expiry filter
        ComboBox<String> expiryFilter = new ComboBox<>();
        expiryFilter.getItems().addAll("All", "Expired", "Expiring Soon", "Valid");
        expiryFilter.setValue("All");
        expiryFilter.setPrefWidth(150);
        expiryFilter.setId("expiryFilter");
        
        Button clearFiltersBtn = createStyledButton("❌ Clear", "#FF5722");
        clearFiltersBtn.setOnAction(e -> clearFilters());
        
        filterControls.getChildren().addAll(statusFilter, typeFilter, expiryFilter, clearFiltersBtn);
        
        filterBox.getChildren().addAll(filterTitle, new Separator(), searchField, filterControls);
        
        return filterBox;
    }
    
    /**
     * Create status bar with record count
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-background-color: white; -fx-background-radius: 5px;");
        
        statusLabel = new Label("Ready");
        statusLabel.setFont(Font.font("Arial", 12));
        
        recordCountLabel = new Label("0 employees");
        recordCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        recordCountLabel.setTextFill(Color.web("#1976D2"));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Add stats
        Label activeLabel = new Label();
        activeLabel.setId("activeCount");
        Label inactiveLabel = new Label();
        inactiveLabel.setId("inactiveCount");
        
        updateEmployeeStats(activeLabel, inactiveLabel);
        
        statusBar.getChildren().addAll(statusLabel, spacer, activeLabel, inactiveLabel, recordCountLabel);
        
        return statusBar;
    }
    
    /**
     * Create enhanced table with better styling
     */
    private TableView<Employee> createEnhancedTable() {
        TableView<Employee> enhancedTable = new TableView<>();
        enhancedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        enhancedTable.setStyle("-fx-background-color: white; -fx-background-radius: 10px;");
        enhancedTable.getStyleClass().add("employees-table");
        
        // Create styled columns
        TableColumn<Employee, String> nameCol = createColumn("Driver Name", "name", 150);
        TableColumn<Employee, String> truckCol = createColumn("Truck/Unit", "truckUnit", 80);
        TableColumn<Employee, String> trailerCol = createColumn("Trailer #", "trailerNumber", 80);
        TableColumn<Employee, String> phoneCol = createColumn("Mobile #", "phone", 110);
        TableColumn<Employee, String> emailCol = createColumn("Email", "email", 180);
        
        // Percentage columns with special formatting
        TableColumn<Employee, Number> driverPctCol = createPercentColumn("Driver %", "driverPercent", 70);
        TableColumn<Employee, Number> companyPctCol = createPercentColumn("Company %", "companyPercent", 85);
        TableColumn<Employee, Number> serviceFeeCol = createPercentColumn("Service Fee %", "serviceFeePercent", 95);
        
        TableColumn<Employee, String> dobCol = createColumn("DOB", "dob", 90);
        TableColumn<Employee, String> licenseCol = createColumn("License #", "licenseNumber", 100);
        TableColumn<Employee, String> driverTypeCol = createColumn("Driver Type", "driverType", 100);
        TableColumn<Employee, String> llcCol = createColumn("Employee LLC", "employeeLLC", 120);
        
        // Expiry columns with color coding
        TableColumn<Employee, LocalDate> cdlExpiryCol = new TableColumn<>("CDL Expiry");
        cdlExpiryCol.setCellValueFactory(new PropertyValueFactory<>("cdlExpiry"));
        cdlExpiryCol.setCellFactory(getExpiryCellFactory());
        cdlExpiryCol.setPrefWidth(90);
        
        TableColumn<Employee, LocalDate> medExpiryCol = new TableColumn<>("Medical Expiry");
        medExpiryCol.setCellValueFactory(new PropertyValueFactory<>("medicalExpiry"));
        medExpiryCol.setCellFactory(getExpiryCellFactory());
        medExpiryCol.setPrefWidth(100);
        
        // Status column with color coding
        TableColumn<Employee, String> statusCol = createStatusColumn();
        
        // Documents column
        TableColumn<Employee, Void> docCol = createDocumentsColumn();
        
        @SuppressWarnings("unchecked")
        TableColumn<Employee, ?>[] columns = new TableColumn[] {
            nameCol, truckCol, trailerCol, phoneCol, emailCol, 
            driverPctCol, companyPctCol, serviceFeeCol,
            dobCol, licenseCol, driverTypeCol, llcCol,
            cdlExpiryCol, medExpiryCol, statusCol, docCol
        };
        enhancedTable.getColumns().addAll(columns);
        
        // Add row styling
        enhancedTable.setRowFactory(tv -> {
            TableRow<Employee> row = new TableRow<>();
            
            // Double-click to edit
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for employee: {}", row.getItem().getName());
                    showEmployeeDialog(row.getItem(), false);
                }
            });
            
            // Alternating row colors
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #F8F8F8;");
                    } else {
                        row.setStyle("-fx-background-color: white;");
                    }
                    
                    // Highlight terminated employees
                    if (newItem.getStatus() == Employee.Status.TERMINATED) {
                        row.setStyle(row.getStyle() + "-fx-opacity: 0.7;");
                    }
                }
            });
            
            return row;
        });
        
        return enhancedTable;
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
     * Create standard table column
     */
    private TableColumn<Employee, String> createColumn(String title, String property, int width) {
        TableColumn<Employee, String> column = new TableColumn<>(title);
        
        if (property.equals("name")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getName()));
        } else if (property.equals("truckUnit")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTruckUnit()));
        } else if (property.equals("trailerNumber")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTrailerNumber()));
        } else if (property.equals("phone")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getPhone()));
        } else if (property.equals("email")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getEmail()));
        } else if (property.equals("dob")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDob() != null ? e.getValue().getDob().toString() : ""));
        } else if (property.equals("licenseNumber")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getLicenseNumber()));
        } else if (property.equals("driverType")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDriverType() != null ? e.getValue().getDriverType().name().replace("_", " ") : ""));
        } else if (property.equals("employeeLLC")) {
            column.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getEmployeeLLC()));
        }
        
        column.setPrefWidth(width);
        return column;
    }
    
    /**
     * Create percentage column with formatting
     */
    private TableColumn<Employee, Number> createPercentColumn(String title, String property, int width) {
        TableColumn<Employee, Number> column = new TableColumn<>(title);
        
        if (property.equals("driverPercent")) {
            column.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getDriverPercent()));
        } else if (property.equals("companyPercent")) {
            column.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getCompanyPercent()));
        } else if (property.equals("serviceFeePercent")) {
            column.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getServiceFeePercent()));
        }
        
        column.setCellFactory(col -> new TableCell<Employee, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f%%", item.doubleValue()));
                }
            }
        });
        
        column.setPrefWidth(width);
        return column;
    }
    
    /**
     * Create status column with color coding
     */
    private TableColumn<Employee, String> createStatusColumn() {
        TableColumn<Employee, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(e -> new SimpleStringProperty(
            e.getValue().getStatus() != null ? e.getValue().getStatus().name() : ""));
        
        statusCol.setCellFactory(col -> new TableCell<Employee, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    
                    // Color based on status
                    if (status.equals("ACTIVE")) {
                        setStyle("-fx-background-color: #C8E6C9; -fx-text-fill: #2E7D32; " +
                                "-fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else if (status.equals("INACTIVE")) {
                        setStyle("-fx-background-color: #FFCDD2; -fx-text-fill: #C62828; " +
                                "-fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else if (status.equals("ON_LEAVE")) {
                        setStyle("-fx-background-color: #FFF9C4; -fx-text-fill: #F57F17; " +
                                "-fx-font-weight: bold; -fx-alignment: CENTER;");
                    }
                }
            }
        });
        
        statusCol.setPrefWidth(80);
        return statusCol;
    }
    
    /**
     * Create documents column
     */
    private TableColumn<Employee, Void> createDocumentsColumn() {
        TableColumn<Employee, Void> docCol = new TableColumn<>("Documents");
        
        docCol.setCellFactory(col -> new TableCell<Employee, Void>() {
            private final Button docManagerBtn = createSmallButton("📋 Doc Manager", "#17a2b8");
            private final HBox btnBox = new HBox(5, docManagerBtn);
            
            {
                btnBox.setAlignment(Pos.CENTER);
                setupDocumentButton();
            }
            
            private Button createSmallButton(String text, String color) {
                Button btn = new Button(text);
                btn.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: white; " +
                    "-fx-font-size: 11px; -fx-padding: 4 8; " +
                    "-fx-cursor: hand; -fx-background-radius: 3px;", color));
                
                btn.setOnMouseEntered(e -> 
                    btn.setStyle(btn.getStyle() + "-fx-opacity: 0.8;"));
                btn.setOnMouseExited(e -> 
                    btn.setStyle(btn.getStyle().replace("-fx-opacity: 0.8;", "")));
                
                return btn;
            }
            
            private void setupDocumentButton() {
                docManagerBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    showEmployeeDocumentManager(emp);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnBox);
                }
            }
        });
        
        docCol.setPrefWidth(150);
        return docCol;
    }
    
    /**
     * Setup filters
     */
    private void setupFilters() {
        FilteredList<Employee> filtered = new FilteredList<>(employees, p -> true);
        
        // Get filter components
        @SuppressWarnings("unchecked")
        ComboBox<Employee.Status> statusFilter = (ComboBox<Employee.Status>) lookup("#statusFilter");
        @SuppressWarnings("unchecked")
        ComboBox<Employee.DriverType> typeFilter = (ComboBox<Employee.DriverType>) lookup("#typeFilter");
        @SuppressWarnings("unchecked")
        ComboBox<String> expiryFilter = (ComboBox<String>) lookup("#expiryFilter");
        
        // Setup listeners
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filtered.setPredicate(emp -> filterEmployee(emp, newVal, 
                statusFilter.getValue(), typeFilter.getValue(), expiryFilter.getValue()));
            updateRecordCount();
        });
        
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            filtered.setPredicate(emp -> filterEmployee(emp, searchField.getText(), 
                newVal, typeFilter.getValue(), expiryFilter.getValue()));
            updateRecordCount();
        });
        
        typeFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            filtered.setPredicate(emp -> filterEmployee(emp, searchField.getText(), 
                statusFilter.getValue(), newVal, expiryFilter.getValue()));
            updateRecordCount();
        });
        
        expiryFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            filtered.setPredicate(emp -> filterEmployee(emp, searchField.getText(), 
                statusFilter.getValue(), typeFilter.getValue(), newVal));
            updateRecordCount();
        });
        
        SortedList<Employee> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
    }
    
    /**
     * Enhanced filter logic
     */
    private boolean filterEmployee(Employee emp, String text, Employee.Status status, 
                                 Employee.DriverType type, String expiryFilter) {
        boolean matches = true;
        
        // Text search
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (!lower.isEmpty()) {
            matches &= (emp.getName() != null && emp.getName().toLowerCase(Locale.ROOT).contains(lower)) ||
                      (emp.getTruckUnit() != null && emp.getTruckUnit().toLowerCase(Locale.ROOT).contains(lower)) ||
                      (emp.getTrailerNumber() != null && emp.getTrailerNumber().toLowerCase(Locale.ROOT).contains(lower)) ||
                      (emp.getLicenseNumber() != null && emp.getLicenseNumber().toLowerCase(Locale.ROOT).contains(lower)) ||
                      (emp.getEmail() != null && emp.getEmail().toLowerCase(Locale.ROOT).contains(lower)) ||
                      (emp.getPhone() != null && emp.getPhone().toLowerCase(Locale.ROOT).contains(lower)) ||
                      (emp.getEmployeeLLC() != null && emp.getEmployeeLLC().toLowerCase(Locale.ROOT).contains(lower));
        }
        
        // Status filter
        if (status != null) {
            matches &= emp.getStatus() == status;
        }
        
        // Type filter
        if (type != null) {
            matches &= emp.getDriverType() == type;
        }
        
        // Expiry filter
        if (expiryFilter != null && !expiryFilter.equals("All")) {
            LocalDate now = LocalDate.now();
            boolean hasExpiry = false;
            
            if (expiryFilter.equals("Expired")) {
                hasExpiry = (emp.getCdlExpiry() != null && emp.getCdlExpiry().isBefore(now)) ||
                           (emp.getMedicalExpiry() != null && emp.getMedicalExpiry().isBefore(now));
            } else if (expiryFilter.equals("Expiring Soon")) {
                LocalDate twoMonths = now.plusMonths(2);
                hasExpiry = (emp.getCdlExpiry() != null && emp.getCdlExpiry().isAfter(now) && 
                            emp.getCdlExpiry().isBefore(twoMonths)) ||
                           (emp.getMedicalExpiry() != null && emp.getMedicalExpiry().isAfter(now) && 
                            emp.getMedicalExpiry().isBefore(twoMonths));
            } else if (expiryFilter.equals("Valid")) {
                hasExpiry = (emp.getCdlExpiry() == null || emp.getCdlExpiry().isAfter(now.plusMonths(2))) &&
                           (emp.getMedicalExpiry() == null || emp.getMedicalExpiry().isAfter(now.plusMonths(2)));
            }
            
            matches &= hasExpiry;
        }
        
        return matches;
    }
    
    /**
     * Clear all filters
     */
    private void clearFilters() {
        searchField.clear();
        @SuppressWarnings("unchecked")
        ComboBox<Employee.Status> statusFilter = (ComboBox<Employee.Status>) lookup("#statusFilter");
        @SuppressWarnings("unchecked")
        ComboBox<Employee.DriverType> typeFilter = (ComboBox<Employee.DriverType>) lookup("#typeFilter");
        @SuppressWarnings("unchecked")
        ComboBox<String> expiryFilter = (ComboBox<String>) lookup("#expiryFilter");
        
        if (statusFilter != null) statusFilter.setValue(null);
        if (typeFilter != null) typeFilter.setValue(null);
        if (expiryFilter != null) expiryFilter.setValue("All");
        
        statusLabel.setText("Filters cleared");
    }
    
    /**
     * Update record count
     */
    private void updateRecordCount() {
        int showing = table.getItems().size();
        int total = employees.size();
        recordCountLabel.setText(String.format("%d of %d employees", showing, total));
        
        // Update active/inactive counts
        Label activeLabel = (Label) lookup("#activeCount");
        Label inactiveLabel = (Label) lookup("#inactiveCount");
        if (activeLabel != null && inactiveLabel != null) {
            updateEmployeeStats(activeLabel, inactiveLabel);
        }
    }
    
    /**
     * Update employee statistics
     */
    private void updateEmployeeStats(Label activeLabel, Label inactiveLabel) {
        long activeCount = employees.stream()
            .filter(e -> e.getStatus() == Employee.Status.ACTIVE)
            .count();
        long inactiveCount = employees.stream()
            .filter(e -> e.getStatus() == Employee.Status.TERMINATED)
            .count();
        
        activeLabel.setText("✅ Active: " + activeCount);
        activeLabel.setTextFill(Color.web("#2E7D32"));
        
        inactiveLabel.setText("❌ Terminated: " + inactiveCount);
        inactiveLabel.setTextFill(Color.web("#C62828"));
    }
    
    /**
     * Setup button actions
     */
    private void setupButtonActions(Button addBtn, Button editBtn, Button deleteBtn, 
                                   Button refreshBtn, Button importBtn, Button percentageBtn, Button exportBtn) {
        addBtn.setOnAction(e -> {
            logger.info("Add employee button clicked");
            showEmployeeDialog(null, true);
        });
        
        editBtn.setOnAction(e -> {
            Employee selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit employee button clicked for: {}", selected.getName());
                showEmployeeDialog(selected, false);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an employee to edit.");
            }
        });
        
        deleteBtn.setOnAction(e -> {
            Employee selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete employee button clicked for: {}", selected.getName());
                showDeleteConfirmation(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an employee to delete.");
            }
        });
        
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            refreshData();
        });
        
        importBtn.setOnAction(e -> {
            logger.info("Import button clicked");
            showImportDialog();
        });
        
        percentageBtn.setOnAction(e -> {
            Employee selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showPercentageConfiguration(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an employee to configure percentages.");
            }
        });
        
        exportBtn.setOnAction(e -> {
            // TODO: Implement export functionality
            showAlert(Alert.AlertType.INFORMATION, "Export", 
                     "Export functionality will be implemented soon.");
        });
    }
    
    /**
     * Show delete confirmation dialog
     */
    private void showDeleteConfirmation(Employee employee) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Employee");
        confirm.setContentText(String.format(
            "Are you sure you want to delete:\n\n" +
            "Name: %s\n" +
            "Truck: %s\n\n" +
            "This action cannot be undone.", 
            employee.getName(), employee.getTruckUnit()));
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                logger.info("User confirmed deletion of employee: {}", employee.getName());
                dao.delete(employee.getId());
                refreshData();
                statusLabel.setText("Employee deleted: " + employee.getName());
            }
        });
    }
    
    /**
     * Show percentage configuration dialog
     */
    private void showPercentageConfiguration(Employee employee) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:payroll.db")) {
            PercentageConfigurationDialog dialog = new PercentageConfigurationDialog(conn);
            dialog.showAndWait().ifPresent(result -> {
                if (result != null && !result.isEmpty()) {
                    refreshData();
                    statusLabel.setText("Percentages updated for: " + employee.getName());
                }
            });
        } catch (SQLException ex) {
            logger.error("Failed to open percentage configuration dialog", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR, 
                "Failed to open percentage configuration: " + ex.getMessage(), 
                ButtonType.OK);
            alert.setHeaderText("Database Error");
            alert.showAndWait();
        }
    }
    
    /**
     * Refresh data from database
     */
    private void refreshData() {
        employees.setAll(dao.getAll());
        notifyEmployeeDataChanged();
        updateRecordCount();
        statusLabel.setText("Data refreshed");
    }
    
    /**
     * Show alert dialog
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Show Employee Document Manager
     */
    private void showEmployeeDocumentManager(Employee employee) {
        if (employee == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an employee to manage documents");
            return;
        }
        
        EmployeeDocumentManager docManager = new EmployeeDocumentManager((Stage) getScene().getWindow());
        docManager.showDocumentManager(employee);
    }
    
    /**
     * Show DOT Compliance Configuration Dialog
     */
    private void showDOTComplianceConfiguration() {
        try {
            DOTComplianceConfigDialog configDialog = new DOTComplianceConfigDialog();
            configDialog.showAndWait();
            
            // Refresh the table to reflect any configuration changes
            refreshData();
            statusLabel.setText("DOT compliance configuration updated");
            
        } catch (Exception e) {
            logger.error("Error showing DOT compliance configuration", e);
            showAlert(Alert.AlertType.ERROR, "Configuration Error", 
                     "Failed to open DOT compliance configuration: " + e.getMessage());
        }
    }

    /**
     * Show import dialog for CSV/XLSX files.
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Employee Data");
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
        progressDialog.setTitle("Importing Employees");
        progressDialog.setHeaderText("Importing employee data from " + selectedFile.getName());
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
                    
                    List<Employee> employees = EmployeeCSVImporter.importEmployees(selectedFile.toPath());
                    result.totalFound = employees.size();
                    
                    updateMessage("Processing employees...");
                    updateProgress(50, 100);
                    
                    if (!employees.isEmpty()) {
                        updateMessage("Saving to database...");
                        updateProgress(75, 100);
                        
                        List<Employee> savedEmployees = dao.addOrUpdateAll(employees);
                        result.imported = savedEmployees.size();
                        result.savedEmployees = savedEmployees; // Store the list with IDs
                        
                        updateMessage("Import completed successfully!");
                        updateProgress(100, 100);
                    } else {
                        updateMessage("No valid employees found in file");
                        result.errors.add("No valid employee data found in the file");
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
            
            // Use the saved employees that already have proper IDs
            List<Employee> savedEmployees = result.savedEmployees;
            logger.info("Import completed with {} employees, refreshing UI", savedEmployees.size());
            
            // Log IDs for debugging
            for (Employee emp : savedEmployees) {
                logger.debug("Employee in result: {} (ID: {})", emp.getName(), emp.getId());
            }
            
            // Clear and reload the observable list with the correct data
            employees.clear();
            employees.addAll(savedEmployees);
            logger.info("Updated observable list with {} employees", employees.size());
            
            // Force table refresh to show changes immediately
            if (table != null) {
                table.refresh();
            }
            
            // Notify data change listeners
            notifyEmployeeDataChanged();
            
            // Update record count
            updateRecordCount();
            
            // Show results
            showImportResults(result, selectedFile.getName());
        });
        
        importTask.setOnFailed(e -> {
            progressDialog.close();
            Throwable error = importTask.getException();
            logger.error("Import failed", error);
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Import Error");
            alert.setHeaderText("Failed to import employees");
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
            summaryLabel.setText("✓ Successfully imported " + result.imported + " employees");
            summaryLabel.setTextFill(Color.web("#4CAF50"));
        } else {
            summaryLabel.setText("⚠ No employees were imported");
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
        
        // Update status
        if (result.imported > 0) {
            statusLabel.setText("Imported " + result.imported + " employees from " + fileName);
        }
    }
    
    /**
     * Import result class
     */
    private static class ImportResult {
        int totalFound = 0;
        int imported = 0;
        List<String> errors = new ArrayList<>();
        List<Employee> savedEmployees = new ArrayList<>(); // Added this field
    }
}