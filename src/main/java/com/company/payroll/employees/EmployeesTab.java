package com.company.payroll.employees;

import javafx.application.Platform;
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
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
import com.company.payroll.employees.EmployeePercentageHistory;
import com.company.payroll.employees.EmployeePercentageHistoryDAO;

import com.company.payroll.util.WindowAware;

public class EmployeesTab extends BorderPane implements WindowAware {
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
        
        // Payment method fields
        ComboBox<PaymentType> paymentTypeBox = new ComboBox<>(
                FXCollections.observableArrayList(PaymentType.values()));
        paymentTypeBox.setValue(PaymentType.PERCENTAGE); // Default to percentage
        
        // Percentage payment fields
        TextField driverPctField = new TextField();
        TextField companyPctField = new TextField();
        TextField serviceFeeField = new TextField();
        
        // Flat rate field
        TextField flatRateField = new TextField();
        flatRateField.setPromptText("e.g. 500.00");
        
        // Per mile field
        TextField perMileRateField = new TextField();
        perMileRateField.setPromptText("e.g. 1.50");
        
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
            
            // Set payment method
            paymentTypeBox.setValue(employee.getPaymentType() != null ? employee.getPaymentType() : PaymentType.PERCENTAGE);
            
            // Set payment-specific fields
            driverPctField.setText(String.valueOf(employee.getDriverPercent()));
            companyPctField.setText(String.valueOf(employee.getCompanyPercent()));
            serviceFeeField.setText(String.valueOf(employee.getServiceFeePercent()));
            
            if (employee.getFlatRateAmount() > 0) {
                flatRateField.setText(String.valueOf(employee.getFlatRateAmount()));
            }
            
            if (employee.getPerMileRate() > 0) {
                perMileRateField.setText(String.valueOf(employee.getPerMileRate()));
            }
            
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
        
        // Payment method selection
        grid.add(new Label("Payment Method*:"), 0, r);     grid.add(paymentTypeBox, 1, r++);
        
        // Percentage payment fields (shown by default)
        Label driverPctLabel = new Label("Driver %*:");
        Label companyPctLabel = new Label("Company %:");
        Label serviceFeeLabel = new Label("Service Fee %:");
        grid.add(driverPctLabel, 0, r);                    grid.add(driverPctField, 1, r++);
        grid.add(companyPctLabel, 0, r);                   grid.add(companyPctField, 1, r++);
        grid.add(serviceFeeLabel, 0, r);                   grid.add(serviceFeeField, 1, r++);
        
        // Flat rate field (hidden by default)
        Label flatRateLabel = new Label("Flat Rate Amount*:");
        grid.add(flatRateLabel, 0, r);                     grid.add(flatRateField, 1, r++);
        flatRateLabel.setVisible(false);
        flatRateField.setVisible(false);
        flatRateLabel.setManaged(false);
        flatRateField.setManaged(false);
        
        // Per mile field (hidden by default)
        Label perMileLabel = new Label("Per Mile Rate*:");
        grid.add(perMileLabel, 0, r);                      grid.add(perMileRateField, 1, r++);
        perMileLabel.setVisible(false);
        perMileRateField.setVisible(false);
        perMileLabel.setManaged(false);
        perMileRateField.setManaged(false);
        
        grid.add(new Label("DOB:"), 0, r);                 grid.add(dobPicker, 1, r++);
        grid.add(new Label("License #:"), 0, r);           grid.add(licenseField, 1, r++);
        grid.add(new Label("Driver Type:"), 0, r);         grid.add(driverTypeBox, 1, r++);
        grid.add(new Label("Employee LLC:"), 0, r);        grid.add(llcField, 1, r++);
        grid.add(new Label("CDL Expiry:"), 0, r);          grid.add(cdlPicker, 1, r++);
        grid.add(new Label("Medical Expiry:"), 0, r);      grid.add(medPicker, 1, r++);
        grid.add(new Label("Status:"), 0, r);              grid.add(statusBox, 1, r++);
        grid.add(errorLabel, 1, r++);
        
        // Payment type change listener
        paymentTypeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Hide all payment-specific fields first
                driverPctLabel.setVisible(false);
                driverPctLabel.setManaged(false);
                driverPctField.setVisible(false);
                driverPctField.setManaged(false);
                
                companyPctLabel.setVisible(false);
                companyPctLabel.setManaged(false);
                companyPctField.setVisible(false);
                companyPctField.setManaged(false);
                
                serviceFeeLabel.setVisible(false);
                serviceFeeLabel.setManaged(false);
                serviceFeeField.setVisible(false);
                serviceFeeField.setManaged(false);
                
                flatRateLabel.setVisible(false);
                flatRateLabel.setManaged(false);
                flatRateField.setVisible(false);
                flatRateField.setManaged(false);
                
                perMileLabel.setVisible(false);
                perMileLabel.setManaged(false);
                perMileRateField.setVisible(false);
                perMileRateField.setManaged(false);
                
                // Show relevant fields based on payment type
                switch (newVal) {
                    case PERCENTAGE:
                        driverPctLabel.setVisible(true);
                        driverPctLabel.setManaged(true);
                        driverPctField.setVisible(true);
                        driverPctField.setManaged(true);
                        
                        companyPctLabel.setVisible(true);
                        companyPctLabel.setManaged(true);
                        companyPctField.setVisible(true);
                        companyPctField.setManaged(true);
                        
                        serviceFeeLabel.setVisible(true);
                        serviceFeeLabel.setManaged(true);
                        serviceFeeField.setVisible(true);
                        serviceFeeField.setManaged(true);
                        break;
                        
                    case FLAT_RATE:
                        flatRateLabel.setVisible(true);
                        flatRateLabel.setManaged(true);
                        flatRateField.setVisible(true);
                        flatRateField.setManaged(true);
                        break;
                        
                    case PER_MILE:
                        perMileLabel.setVisible(true);
                        perMileLabel.setManaged(true);
                        perMileRateField.setVisible(true);
                        perMileRateField.setManaged(true);
                        break;
                }
                
                // Resize dialog to fit content
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        // Validation logic
        Runnable validate = () -> {
            boolean nameValid = !nameField.getText().trim().isEmpty();
            boolean paymentValid = true;
            String errorText = "";
            
            // Validate based on selected payment type
            PaymentType selectedType = paymentTypeBox.getValue();
            if (selectedType != null) {
                switch (selectedType) {
                    case PERCENTAGE:
                        paymentValid = isDouble(driverPctField.getText()) && 
                                     isDouble(companyPctField.getText()) && 
                                     isDouble(serviceFeeField.getText());
                        
                        if (paymentValid) {
                            double driverPct = parseDouble(driverPctField.getText());
                            double companyPct = parseDouble(companyPctField.getText());
                            double servicePct = parseDouble(serviceFeeField.getText());
                            double total = driverPct + companyPct + servicePct;
                            
                            if (Math.abs(total - 100.0) > 0.01) {
                                paymentValid = false;
                                errorText = "Percentages must total 100% (currently " + String.format("%.2f", total) + "%)";
                            }
                        } else {
                            errorText = "Please enter valid percentage values";
                        }
                        break;
                        
                    case FLAT_RATE:
                        paymentValid = isDouble(flatRateField.getText()) && 
                                     parseDouble(flatRateField.getText()) > 0;
                        if (!paymentValid) {
                            errorText = "Please enter a valid flat rate amount";
                        }
                        break;
                        
                    case PER_MILE:
                        paymentValid = isDouble(perMileRateField.getText()) && 
                                     parseDouble(perMileRateField.getText()) > 0;
                        if (!paymentValid) {
                            errorText = "Please enter a valid per-mile rate";
                        }
                        break;
                }
            }
            
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
            } else if (!paymentValid && !errorText.isEmpty()) {
                errorLabel.setText(errorText);
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
            okBtn.setDisable(!(nameValid && paymentValid) || duplicate || dupTruck);
        };

        // Enhanced input validation for percentage fields
        addPercentageValidation(driverPctField, "Driver %");
        addPercentageValidation(companyPctField, "Company %");
        addCurrencyValidation(serviceFeeField, "Service Fee");
        
        // Add validation for new payment fields
        addCurrencyValidation(flatRateField, "Flat Rate");
        addCurrencyValidation(perMileRateField, "Per Mile Rate");
        
        nameField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        truckComboBox.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        paymentTypeBox.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        driverPctField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        companyPctField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        serviceFeeField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        flatRateField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        perMileRateField.textProperty().addListener((obs, oldV, newV) -> validate.run());
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
                    
                    // Get payment method and related fields
                    PaymentType paymentType = paymentTypeBox != null ? paymentTypeBox.getValue() : PaymentType.PERCENTAGE;
                    double driverPct = 0.0;
                    double companyPct = 0.0;
                    double serviceFee = 0.0;
                    double flatRateAmount = 0.0;
                    double perMileRate = 0.0;
                    
                    // Set values based on payment type
                    switch (paymentType) {
                        case PERCENTAGE:
                            driverPct = driverPctField != null ? parseDouble(driverPctField.getText()) : 0.0;
                            companyPct = companyPctField != null ? parseDouble(companyPctField.getText()) : 0.0;
                            serviceFee = serviceFeeField != null ? parseDouble(serviceFeeField.getText()) : 0.0;
                            break;
                        case FLAT_RATE:
                            flatRateAmount = flatRateField != null ? parseDouble(flatRateField.getText()) : 0.0;
                            break;
                        case PER_MILE:
                            perMileRate = perMileRateField != null ? parseDouble(perMileRateField.getText()) : 0.0;
                            break;
                    }
                    
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
                        logger.info("Adding new employee: {} with payment type: {}", name, paymentType);
                        Employee emp = new Employee(0, name, truck, trailer, driverPct, companyPct, serviceFee, dob, license, driverType, llc, cdlExp, medExp, status);
                        emp.setPhone(phone);
                        emp.setEmail(email);
                        
                        // Set payment method fields
                        emp.setPaymentType(paymentType);
                        emp.setFlatRateAmount(flatRateAmount);
                        emp.setPerMileRate(perMileRate);
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
                        
                        // Update payment method fields
                        employee.setPaymentType(paymentType);
                        employee.setDriverPercent(driverPct);
                        employee.setCompanyPercent(companyPct);
                        employee.setServiceFeePercent(serviceFee);
                        employee.setFlatRateAmount(flatRateAmount);
                        employee.setPerMileRate(perMileRate);
                        
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

    /**
     * Save all pending changes (called during application exit)
     */
    public void saveAllPendingChanges() {
        logger.info("Saving all pending employee changes during application exit");
        try {
            // Force refresh table to ensure any pending edits are committed
            if (table != null) {
                table.refresh();
            }
            
            // Any additional save logic can be added here
            // The data is already saved automatically when changes are made via the dialogs
            
            logger.info("All pending employee changes saved successfully");
        } catch (Exception e) {
            logger.error("Error saving pending employee changes", e);
        }
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
    
    /**
     * Add percentage validation to a text field (0-100% range)
     */
    private void addPercentageValidation(TextField field, String fieldName) {
        field.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.isEmpty()) {
                return;
            }
            
            // Allow digits, decimal point, and % symbol
            if (!newText.matches("^\\d*\\.?\\d{0,2}%?$")) {
                field.setText(oldText);
                return;
            }
            
            // Validate range
            try {
                String cleanText = newText.replace("%", "");
                if (!cleanText.isEmpty()) {
                    double value = Double.parseDouble(cleanText);
                    if (value > 100.0) {
                        field.setText(oldText);
                        return;
                    }
                }
            } catch (NumberFormatException e) {
                field.setText(oldText);
            }
        });
        
        // Add visual feedback
        field.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
            if (!newFocused && !field.getText().isEmpty()) {
                try {
                    String cleanText = field.getText().replace("%", "");
                    double value = Double.parseDouble(cleanText);
                    if (value < 0 || value > 100) {
                        field.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                        field.setTooltip(new Tooltip(fieldName + " must be between 0 and 100"));
                    } else {
                        field.setStyle("");
                        field.setTooltip(null);
                    }
                } catch (NumberFormatException e) {
                    field.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                    field.setTooltip(new Tooltip("Invalid " + fieldName + " format"));
                }
            }
        });
    }
    
    /**
     * Add currency validation to a text field
     */
    private void addCurrencyValidation(TextField field, String fieldName) {
        field.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.isEmpty()) {
                return;
            }
            
            // Allow currency symbols, digits, decimal point, and commas
            if (!newText.matches("^[$€£¥]?\\d{0,8}(,\\d{3})*(\\.\\d{0,2})?$")) {
                field.setText(oldText);
                return;
            }
            
            // Prevent extremely large values
            try {
                String cleanText = newText.replaceAll("[$€£¥,]", "");
                if (!cleanText.isEmpty()) {
                    double value = Double.parseDouble(cleanText);
                    if (value > 999999.99) {
                        field.setText(oldText);
                        return;
                    }
                }
            } catch (NumberFormatException e) {
                field.setText(oldText);
            }
        });
        
        // Add visual feedback
        field.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
            if (!newFocused && !field.getText().isEmpty()) {
                try {
                    String cleanText = field.getText().replaceAll("[$€£¥,]", "");
                    Double.parseDouble(cleanText);
                    field.setStyle("");
                    field.setTooltip(null);
                } catch (NumberFormatException e) {
                    field.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                    field.setTooltip(new Tooltip("Invalid " + fieldName + " format"));
                }
            }
        });
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
        Button exportBtn = createStyledButton("📊 Export", "#9C27B0");
        
        // Setup button actions
        setupButtonActions(addBtn, editBtn, deleteBtn, refreshBtn, importBtn, exportBtn);
        
        Separator separator1 = new Separator(Orientation.VERTICAL);
        separator1.setPadding(new Insets(0, 5, 0, 5));
        
        Separator separator2 = new Separator(Orientation.VERTICAL);
        separator2.setPadding(new Insets(0, 5, 0, 5));
        
        // Add configure button for DOT compliance
        Button configureBtn = createStyledButton("⚙️ Configure", "#6c757d");
        configureBtn.setOnAction(e -> showDOTComplianceConfiguration());
        
        actionBar.getChildren().addAll(addBtn, editBtn, deleteBtn, separator1, 
                                      importBtn, exportBtn, refreshBtn, separator2, configureBtn);
        
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
     * Create percentage column with effective percentage display
     */
    private TableColumn<Employee, Number> createPercentColumn(String title, String property, int width) {
        TableColumn<Employee, Number> column = new TableColumn<>(title);
        
        if (property.equals("driverPercent")) {
            column.setCellValueFactory(e -> {
                Employee emp = e.getValue();
                double effectivePercent = getEffectivePercentage(emp.getId(), emp.getDriverPercent(), "driver");
                return new SimpleDoubleProperty(effectivePercent);
            });
        } else if (property.equals("companyPercent")) {
            column.setCellValueFactory(e -> {
                Employee emp = e.getValue();
                double effectivePercent = getEffectivePercentage(emp.getId(), emp.getCompanyPercent(), "company");
                return new SimpleDoubleProperty(effectivePercent);
            });
        } else if (property.equals("serviceFeePercent")) {
            column.setCellValueFactory(e -> {
                Employee emp = e.getValue();
                double effectivePercent = getEffectivePercentage(emp.getId(), emp.getServiceFeePercent(), "service");
                return new SimpleDoubleProperty(effectivePercent);
            });
        }
        
        column.setCellFactory(col -> new TableCell<Employee, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                } else {
                    double value = item.doubleValue();
                    setText(String.format("%.1f%%", value));
                    
                    // Check if this is a configured percentage (different from base)
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        Employee emp = (Employee) getTableRow().getItem();
                        double basePercent = 0;
                        boolean isConfigured = false;
                        
                        if (property.equals("driverPercent")) {
                            basePercent = emp.getDriverPercent();
                        } else if (property.equals("companyPercent")) {
                            basePercent = emp.getCompanyPercent();
                        } else if (property.equals("serviceFeePercent")) {
                            basePercent = emp.getServiceFeePercent();
                        }
                        
                        // If effective percentage differs from base, it's configured
                        if (Math.abs(value - basePercent) > 0.01) {
                            isConfigured = true;
                        }
                        
                        if (isConfigured) {
                            // Highlight configured percentages with a blue background
                            setStyle("-fx-background-color: #E3F2FD; -fx-font-weight: bold;");
                            setTooltip(new Tooltip("Configured percentage (effective rate)"));
                        } else {
                            setStyle("");
                            setTooltip(null);
                        }
                    }
                }
            }
        });
        
        column.setPrefWidth(width);
        return column;
    }
    
    /**
     * Get the effective percentage for an employee on the current date
     */
    private double getEffectivePercentage(int employeeId, double basePercentage, String percentageType) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:payroll.db")) {
            EmployeePercentageHistoryDAO percentageDAO = new EmployeePercentageHistoryDAO(conn);
            EmployeePercentageHistory history = percentageDAO.getEffectivePercentages(employeeId, LocalDate.now());
            
            if (history != null) {
                switch (percentageType) {
                    case "driver":
                        return history.getDriverPercent();
                    case "company":
                        return history.getCompanyPercent();
                    case "service":
                        return history.getServiceFeePercent();
                    default:
                        return basePercentage;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get effective percentages for employee {}: {}", employeeId, e.getMessage());
        }
        return basePercentage;
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
                                   Button refreshBtn, Button importBtn, Button exportBtn) {
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
        
        exportBtn.setOnAction(e -> {
            logger.info("Export button clicked");
            showExportDialog();
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
     * Refresh data from database
     */
    private void refreshData() {
        employees.setAll(dao.getAll());
        notifyEmployeeDataChanged();
        updateRecordCount();
        statusLabel.setText("Data refreshed");
    }
    
    /**
     * Public method to refresh data with effective percentages from external sources (like PayrollTab)
     * This method ensures that the table displays the current effective percentages
     */
    public void refreshDataWithEffectivePercentages() {
        logger.info("Refreshing employee data with effective percentages");
        
        // Refresh the employee list from database
        employees.setAll(dao.getAll());
        
        // Force table refresh to show updated percentages
        if (table != null) {
            table.refresh();
            
            // Show a brief notification about the percentage update
            Platform.runLater(() -> {
                statusLabel.setText("✅ Percentages updated - showing effective rates");
                statusLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
                
                // Reset status label after 3 seconds
                Timeline timeline = new Timeline(new KeyFrame(
                    Duration.seconds(3),
                    ae -> {
                        statusLabel.setText("Ready");
                        statusLabel.setStyle("");
                    }
                ));
                timeline.play();
            });
        }
        
        // Notify listeners about the data change
        notifyEmployeeDataChanged();
        updateRecordCount();
        
        logger.info("Employee data refreshed with {} employees", employees.size());
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
     * Show export dialog for CSV/XLSX files.
     */
    private void showExportDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Export Employees");
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
     * Export employees data to CSV file
     */
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Employees to CSV");
        fileChooser.setInitialFileName("employees_" + 
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
                writer.write("Driver Name,Truck/Unit,Trailer #,Email,Driver %,Company %,Service Fee %," +
                           "DOB,License #,Driver Type,Employee LLC,CDL Expiry,Medical Expiry,Phone");
                writer.newLine();
                
                // Filter the employees if search filter is applied
                List<Employee> exportList = new ArrayList<>();
                if (table.getItems() instanceof SortedList) {
                    exportList.addAll(table.getItems());
                } else {
                    exportList.addAll(employees);
                }
                
                // Write data
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                for (Employee emp : exportList) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCSV(emp.getName())).append(",");
                    line.append(escapeCSV(emp.getTruckUnit())).append(",");
                    line.append(escapeCSV(emp.getTrailerNumber())).append(",");
                    line.append(escapeCSV(emp.getEmail())).append(",");
                    line.append(String.format("%.2f", emp.getDriverPercent())).append(",");
                    line.append(String.format("%.2f", emp.getCompanyPercent())).append(",");
                    line.append(String.format("%.2f", emp.getServiceFeePercent())).append(",");
                    line.append(emp.getDob() != null ? emp.getDob().format(dateFormatter) : "").append(",");
                    line.append(escapeCSV(emp.getLicenseNumber())).append(",");
                    line.append(escapeCSV(emp.getDriverType() != null ? emp.getDriverType().name() : "")).append(",");
                    line.append(escapeCSV(emp.getEmployeeLLC())).append(",");
                    line.append(emp.getCdlExpiry() != null ? emp.getCdlExpiry().format(dateFormatter) : "").append(",");
                    line.append(emp.getMedicalExpiry() != null ? emp.getMedicalExpiry().format(dateFormatter) : "").append(",");
                    line.append(escapeCSV(emp.getPhone()));
                    
                    writer.write(line.toString());
                    writer.newLine();
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                         "Exported " + exportList.size() + " employees to CSV successfully.");
                statusLabel.setText("Exported " + exportList.size() + " employees to CSV");
            } catch (IOException e) {
                logger.error("Failed to export to CSV", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                         "Failed to export data: " + e.getMessage());
            }
        }
    }
    
    /**
     * Export employees data to XLSX file
     */
    private void exportToXLSX() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Employees to Excel");
        fileChooser.setInitialFileName("employees_" + 
                java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Employees");
                
                // Create header row
                String[] headers = {"Driver Name", "Truck/Unit", "Trailer #", "Email", "Driver %", 
                                   "Company %", "Service Fee %", "DOB", "License #", "Driver Type", 
                                   "Employee LLC", "CDL Expiry", "Medical Expiry", "Phone"};
                
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
                
                // Filter the employees if search filter is applied
                List<Employee> exportList = new ArrayList<>();
                if (table.getItems() instanceof SortedList) {
                    exportList.addAll(table.getItems());
                } else {
                    exportList.addAll(employees);
                }
                
                // Create data rows
                int rowNum = 1;
                for (Employee emp : exportList) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    
                    row.createCell(0).setCellValue(emp.getName());
                    row.createCell(1).setCellValue(emp.getTruckUnit());
                    row.createCell(2).setCellValue(emp.getTrailerNumber());
                    row.createCell(3).setCellValue(emp.getEmail());
                    
                    // Set percentage cells
                    org.apache.poi.ss.usermodel.Cell driverPctCell = row.createCell(4);
                    driverPctCell.setCellValue(emp.getDriverPercent() / 100); // Convert to decimal for Excel percentage
                    
                    org.apache.poi.ss.usermodel.Cell companyPctCell = row.createCell(5);
                    companyPctCell.setCellValue(emp.getCompanyPercent() / 100);
                    
                    org.apache.poi.ss.usermodel.Cell servicePctCell = row.createCell(6);
                    servicePctCell.setCellValue(emp.getServiceFeePercent() / 100);
                    
                    // Create percentage style
                    org.apache.poi.ss.usermodel.CellStyle percentStyle = workbook.createCellStyle();
                    percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
                    driverPctCell.setCellStyle(percentStyle);
                    companyPctCell.setCellStyle(percentStyle);
                    servicePctCell.setCellStyle(percentStyle);
                    
                    // Set date cells
                    org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                    dateStyle.setDataFormat(workbook.createDataFormat().getFormat("mm/dd/yyyy"));
                    
                    org.apache.poi.ss.usermodel.Cell dobCell = row.createCell(7);
                    if (emp.getDob() != null) {
                        dobCell.setCellValue(java.util.Date.from(emp.getDob().atStartOfDay().atZone(
                            java.time.ZoneId.systemDefault()).toInstant()));
                        dobCell.setCellStyle(dateStyle);
                    }
                    
                    row.createCell(8).setCellValue(emp.getLicenseNumber());
                    row.createCell(9).setCellValue(emp.getDriverType() != null ? emp.getDriverType().name() : "");
                    row.createCell(10).setCellValue(emp.getEmployeeLLC());
                    
                    org.apache.poi.ss.usermodel.Cell cdlCell = row.createCell(11);
                    if (emp.getCdlExpiry() != null) {
                        cdlCell.setCellValue(java.util.Date.from(emp.getCdlExpiry().atStartOfDay().atZone(
                            java.time.ZoneId.systemDefault()).toInstant()));
                        cdlCell.setCellStyle(dateStyle);
                    }
                    
                    org.apache.poi.ss.usermodel.Cell medCell = row.createCell(12);
                    if (emp.getMedicalExpiry() != null) {
                        medCell.setCellValue(java.util.Date.from(emp.getMedicalExpiry().atStartOfDay().atZone(
                            java.time.ZoneId.systemDefault()).toInstant()));
                        medCell.setCellStyle(dateStyle);
                    }
                    
                    row.createCell(13).setCellValue(emp.getPhone());
                }
                
                // Auto-size columns for better readability
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
                
                // Write to file
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    workbook.write(outputStream);
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                         "Exported " + exportList.size() + " employees to Excel successfully.");
                statusLabel.setText("Exported " + exportList.size() + " employees to Excel");
            } catch (Exception e) {
                logger.error("Failed to export to Excel", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                         "Failed to export data: " + e.getMessage());
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
            
            // Ensure all UI updates happen on JavaFX Application Thread
            Platform.runLater(() -> {
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
        });
        
        importTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                Throwable error = importTask.getException();
                logger.error("Import failed", error);
                
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Import Error");
                alert.setHeaderText("Failed to import employees");
                alert.setContentText("Error: " + error.getMessage());
                alert.showAndWait();
            });
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