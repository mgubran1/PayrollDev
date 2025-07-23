package com.company.payroll.trailers;

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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;

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
import java.util.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.concurrent.Task;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced Trailers tab for managing fleet information with focus on document tracking
 * and inspection expiration dates.
 */
public class TrailersTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(TrailersTab.class);
    
    private final TrailerDAO trailerDAO = new TrailerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ObservableList<Trailer> trailers = FXCollections.observableArrayList();
    private final TableView<Trailer> table = new TableView<>();
    private final Map<String, Employee> driverMap = new HashMap<>();

    // Listeners notified when trailer data changes
    private final List<Runnable> dataChangeListeners = new ArrayList<>();
    
    // Document storage path
    private final String docStoragePath = "trailer_documents";
    
    public TrailersTab() {
        logger.info("Initializing TrailersTab");
        // Create document directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(docStoragePath));
        } catch (Exception e) {
            logger.error("Failed to create document directory", e);
        }
        
        initializeUI();
        loadData();
        logger.info("TrailersTab initialization complete");
    }
    
    private void initializeUI() {
        // --- SEARCH/FILTER CONTROLS ---
        TextField searchField = new TextField();
        searchField.setPromptText("Search trailer #, vin, type...");
        
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "ACTIVE", "MAINTENANCE", "AVAILABLE", "OUT_OF_SERVICE");
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
        
        TableColumn<Trailer, String> numberCol = new TableColumn<>("Trailer #");
        numberCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTrailerNumber()));
        numberCol.setPrefWidth(100);
        
        TableColumn<Trailer, String> vinCol = new TableColumn<>("VIN#");
        vinCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVin()));
        vinCol.setPrefWidth(150);
        
        TableColumn<Trailer, String> makeModelCol = new TableColumn<>("Make/Model");
        makeModelCol.setCellValueFactory(c -> {
            String make = c.getValue().getMake() != null ? c.getValue().getMake() : "";
            String model = c.getValue().getModel() != null ? c.getValue().getModel() : "";
            String year = c.getValue().getYear() > 0 ? String.valueOf(c.getValue().getYear()) : "";
            return new SimpleStringProperty(year + " " + make + " " + model);
        });
        makeModelCol.setPrefWidth(150);
        
        TableColumn<Trailer, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType()));
        typeCol.setPrefWidth(100);
        
        TableColumn<Trailer, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        statusCol.setPrefWidth(100);
        
        TableColumn<Trailer, String> assignedToCol = new TableColumn<>("Assigned To");
        assignedToCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAssignedDriver()));
        assignedToCol.setPrefWidth(150);
        
        TableColumn<Trailer, LocalDate> regExpiryCol = new TableColumn<>("Registration Expiry");
        regExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getRegistrationExpiryDate()));
        regExpiryCol.setCellFactory(getExpiryDateCellFactory());
        regExpiryCol.setPrefWidth(150);
        
        TableColumn<Trailer, LocalDate> insExpiryCol = new TableColumn<>("Insurance Expiry");
        insExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getInsuranceExpiryDate()));
        insExpiryCol.setCellFactory(getExpiryDateCellFactory());
        insExpiryCol.setPrefWidth(150);
        
        TableColumn<Trailer, LocalDate> leaseExpiryCol = new TableColumn<>("Lease Agreement Expiry");
        leaseExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLeaseAgreementExpiryDate()));
        leaseExpiryCol.setCellFactory(getExpiryDateCellFactory());
        leaseExpiryCol.setPrefWidth(150);
        
        TableColumn<Trailer, LocalDate> inspectionCol = new TableColumn<>("Inspection");
        inspectionCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLastInspectionDate()));
        inspectionCol.setCellFactory(getInspectionDateCellFactory());
        inspectionCol.setPrefWidth(120);
        
        TableColumn<Trailer, LocalDate> inspExpiryCol = new TableColumn<>("Inspection Expiry");
        inspExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getNextInspectionDueDate()));
        inspExpiryCol.setCellFactory(getExpiryDateCellFactory());
        inspExpiryCol.setPrefWidth(150);
        
        TableColumn<Trailer, String> plateCol = new TableColumn<>("License Plate");
        plateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLicensePlate()));
        plateCol.setPrefWidth(120);
        
        table.getColumns().setAll(java.util.List.of(
                numberCol, vinCol, makeModelCol, typeCol, statusCol, assignedToCol,
                regExpiryCol, insExpiryCol, leaseExpiryCol, inspectionCol, inspExpiryCol, plateCol));
        
        // --- FILTERED/SORTED VIEW ---
        FilteredList<Trailer> filteredTrailers = new FilteredList<>(trailers, p -> true);
        
        searchField.textProperty().addListener((obs, oldVal, newVal) -> 
            filteredTrailers.setPredicate(trailer -> filterTrailer(trailer, newVal, statusFilter.getValue()))
        );
        
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> 
            filteredTrailers.setPredicate(trailer -> filterTrailer(trailer, searchField.getText(), newVal))
        );
        
        SortedList<Trailer> sortedTrailers = new SortedList<>(filteredTrailers);
        sortedTrailers.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedTrailers);
        
        // --- DOUBLE-CLICK FOR EDIT ---
        table.setRowFactory(tv -> {
            TableRow<Trailer> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for trailer: {}", row.getItem().getTrailerNumber());
                    showTrailerDialog(row.getItem(), false);
                }
            });
            return row;
        });
        
        // --- BUTTONS ---
        Button addBtn = ModernButtonStyles.createPrimaryButton("➕ Add Trailer");
        Button editBtn = ModernButtonStyles.createSecondaryButton("✏️ Edit");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete");
        Button refreshBtn = ModernButtonStyles.createSuccessButton("🔄 Refresh");
        Button importBtn = ModernButtonStyles.createInfoButton("📥 Import CSV/XLSX");
        Button exportBtn = ModernButtonStyles.createInfoButton("📊 Export");
        Button uploadBtn = ModernButtonStyles.createInfoButton("📤 Upload Document");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print Document");
        
        uploadBtn.setOnAction(e -> uploadDocument());
        printBtn.setOnAction(e -> printSelectedTrailerDocuments());
        importBtn.setOnAction(e -> showImportDialog());
        exportBtn.setOnAction(e -> showExportDialog());
        
        addBtn.setOnAction(e -> {
            logger.info("Add trailer button clicked");
            showTrailerDialog(null, true);
        });
        
        editBtn.setOnAction(e -> {
            Trailer selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit trailer button clicked for: {}", selected.getTrailerNumber());
                showTrailerDialog(selected, false);
            }
        });
        
        deleteBtn.setOnAction(e -> {
            Trailer selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete trailer button clicked for: {}", selected.getTrailerNumber());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete trailer \"" + selected.getTrailerNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of trailer: {}", selected.getTrailerNumber());
                        trailerDAO.delete(selected.getId());
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
        Label totalTrailersLabel = new Label("Total Trailers: 0");
        Label expiringItemsLabel = new Label("Expiring Items: 0");
        expiringItemsLabel.setTextFill(Color.RED);
        
        HBox statusBar = new HBox(20, totalTrailersLabel, expiringItemsLabel);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        // Update counts when data changes
        trailers.addListener((javafx.collections.ListChangeListener<Trailer>) c -> {
            totalTrailersLabel.setText("Total Trailers: " + trailers.size());
            
            LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
            long expiringCount = 0;
            
            for (Trailer t : trailers) {
                // Check registration expiry
                if (t.getRegistrationExpiryDate() != null && 
                    t.getRegistrationExpiryDate().isBefore(thirtyDaysFromNow)) {
                    expiringCount++;
                    continue;
                }
                // Check insurance expiry
                if (t.getInsuranceExpiryDate() != null && 
                    t.getInsuranceExpiryDate().isBefore(thirtyDaysFromNow)) {
                    expiringCount++;
                    continue;
                }
                // Check lease agreement expiry
                if (t.getLeaseAgreementExpiryDate() != null && 
                    t.getLeaseAgreementExpiryDate().isBefore(thirtyDaysFromNow)) {
                    expiringCount++;
                    continue;
                }
                // Check inspection expiry
                if (t.getNextInspectionDueDate() != null && 
                    t.getNextInspectionDueDate().isBefore(thirtyDaysFromNow)) {
                    expiringCount++;
                }
            }
                
            expiringItemsLabel.setText("Expiring Items (30 days): " + expiringCount);
            expiringItemsLabel.setVisible(expiringCount > 0);
        });
        
        return statusBar;
    }
    
    private void loadData() {
        logger.info("Loading trailer data");
        List<Trailer> trailerList = trailerDAO.findAll();
        List<Employee> employees = employeeDAO.getAll();
        
        // Create a map of trailer number to driver
        driverMap.clear();
        for (Employee emp : employees) {
            if (emp.getTrailerNumber() != null && !emp.getTrailerNumber().isEmpty()) {
                driverMap.put(emp.getTrailerNumber(), emp);
            }
        }
        
        // Set driver names based on assignments
        for (Trailer trailer : trailerList) {
            Employee driver = driverMap.get(trailer.getTrailerNumber());
            if (driver != null) {
                trailer.setAssignedDriver(driver.getName());
            }
        }
        
        trailers.setAll(trailerList);
        logger.info("Loaded {} trailers", trailers.size());
    }
    
    private boolean filterTrailer(Trailer trailer, String searchText, String status) {
        boolean matchesSearch = true;
        if (searchText != null && !searchText.isEmpty()) {
            String lowerSearch = searchText.toLowerCase();
            matchesSearch = (trailer.getTrailerNumber() != null && trailer.getTrailerNumber().toLowerCase().contains(lowerSearch))
                || (trailer.getVin() != null && trailer.getVin().toLowerCase().contains(lowerSearch))
                || (trailer.getMake() != null && trailer.getMake().toLowerCase().contains(lowerSearch))
                || (trailer.getModel() != null && trailer.getModel().toLowerCase().contains(lowerSearch))
                || (trailer.getType() != null && trailer.getType().toLowerCase().contains(lowerSearch))
                || (trailer.getAssignedDriver() != null && trailer.getAssignedDriver().toLowerCase().contains(lowerSearch));
        }
        
        boolean matchesStatus = true;
        if (status != null && !status.equals("All")) {
            matchesStatus = status.equals(trailer.getStatus().name());
        }
        
        return matchesSearch && matchesStatus;
    }
    
    private void showTrailerDialog(Trailer trailer, boolean isAdd) {
        logger.debug("Showing trailer dialog - isAdd: {}", isAdd);
        Dialog<Trailer> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add New Trailer" : "Edit Trailer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Fields
        TextField numberField = new TextField();
        TextField vinField = new TextField();
        TextField makeField = new TextField();
        TextField modelField = new TextField();
        TextField yearField = new TextField();
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(
                "Dry Van", "Reefer", "Flatbed", "Step Deck", "Double Drop", "Container", "Tanker", "Other"));
        ComboBox<TrailerStatus> statusBox = new ComboBox<>(FXCollections.observableArrayList(TrailerStatus.values()));
        statusBox.setValue(TrailerStatus.ACTIVE); // Set default value for new trailers
        DatePicker regExpiryPicker = new DatePicker();
        DatePicker insExpiryPicker = new DatePicker();
        DatePicker leaseExpiryPicker = new DatePicker();
        DatePicker inspectionPicker = new DatePicker();
        DatePicker inspExpiryPicker = new DatePicker();
        TextField plateField = new TextField();
        TextField lengthField = new TextField();
        TextField capacityField = new TextField();
        
        // Populate fields if editing
        if (trailer != null) {
            numberField.setText(trailer.getTrailerNumber());
            vinField.setText(trailer.getVin());
            makeField.setText(trailer.getMake());
            modelField.setText(trailer.getModel());
            yearField.setText(trailer.getYear() > 0 ? Integer.toString(trailer.getYear()) : "");
            typeBox.setValue(trailer.getType());
            statusBox.setValue(trailer.getStatus());
            regExpiryPicker.setValue(trailer.getRegistrationExpiryDate());
            insExpiryPicker.setValue(trailer.getInsuranceExpiryDate());
            leaseExpiryPicker.setValue(trailer.getLeaseAgreementExpiryDate());
            inspectionPicker.setValue(trailer.getLastInspectionDate());
            inspExpiryPicker.setValue(trailer.getNextInspectionDueDate());
            plateField.setText(trailer.getLicensePlate());
            lengthField.setText(trailer.getLength() > 0 ? String.valueOf(trailer.getLength()) : "");
            capacityField.setText(trailer.getCapacity() > 0 ? String.valueOf(trailer.getCapacity()) : "");
        }
        
        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        int row = 0;
        grid.add(new Label("Trailer Number*:"), 0, row);
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
        grid.add(plateField, 1, row++);
        
        grid.add(new Label("Length (ft):"), 0, row);
        grid.add(lengthField, 1, row++);
        
        grid.add(new Label("Capacity (cu ft):"), 0, row);
        grid.add(capacityField, 1, row++);
        
        grid.add(new Label("Registration Expiry:"), 0, row);
        grid.add(regExpiryPicker, 1, row++);
        
        grid.add(new Label("Insurance Expiry:"), 0, row);
        grid.add(insExpiryPicker, 1, row++);
        
        grid.add(new Label("Lease Agreement Expiry:"), 0, row);
        grid.add(leaseExpiryPicker, 1, row++);
        
        grid.add(new Label("Inspection:"), 0, row);
        grid.add(inspectionPicker, 1, row++);
        
        grid.add(new Label("Inspection Expiry:"), 0, row);
        grid.add(inspExpiryPicker, 1, row++);
        
        // Error label for validation messages
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        grid.add(errorLabel, 0, row++, 2, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(numberField.textProperty().isEmpty());
        
        // Handle duplicate trailer numbers
        numberField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                boolean duplicate = trailers.stream()
                    .anyMatch(t -> t.getTrailerNumber().equalsIgnoreCase(newVal.trim()) &&
                             (isAdd || t.getId() != (trailer == null ? -1 : trailer.getId())));
                
                errorLabel.setText(duplicate ? "Trailer number already exists" : "");
                okButton.setDisable(duplicate);
            }
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    Trailer result = trailer != null ? trailer : new Trailer();
                    
                    // Safe text extraction with null checks
                    result.setTrailerNumber(numberField != null ? numberField.getText() : "");
                    if (result.getTrailerNumber() != null) result.setTrailerNumber(result.getTrailerNumber().trim());
                    
                    result.setVin(vinField != null ? vinField.getText() : "");
                    if (result.getVin() != null) result.setVin(result.getVin().trim());
                    
                    result.setMake(makeField != null ? makeField.getText() : "");
                    if (result.getMake() != null) result.setMake(result.getMake().trim());
                    
                    result.setModel(modelField != null ? modelField.getText() : "");
                    if (result.getModel() != null) result.setModel(result.getModel().trim());
                    
                    // Safe year parsing
                    if (yearField != null && yearField.getText() != null && !yearField.getText().trim().isEmpty()) {
                        try {
                            result.setYear(Integer.parseInt(yearField.getText().trim()));
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid year format: {}", yearField != null ? yearField.getText() : "null");
                        }
                    }
                    
                    // Safe length parsing
                    if (lengthField != null && lengthField.getText() != null && !lengthField.getText().trim().isEmpty()) {
                        try {
                            result.setLength(Double.parseDouble(lengthField.getText().trim()));
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid length format: {}", lengthField != null ? lengthField.getText() : "null");
                        }
                    }
                    
                    // Safe capacity parsing
                    if (capacityField != null && capacityField.getText() != null && !capacityField.getText().trim().isEmpty()) {
                        try {
                            result.setCapacity(Double.parseDouble(capacityField.getText().trim()));
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid capacity format: {}", capacityField != null ? capacityField.getText() : "null");
                        }
                    }
                    
                    result.setType(typeBox != null ? typeBox.getValue() : null);
                    result.setStatus(statusBox != null ? statusBox.getValue() : TrailerStatus.ACTIVE);
                    
                    result.setLicensePlate(plateField != null ? plateField.getText() : "");
                    if (result.getLicensePlate() != null) result.setLicensePlate(result.getLicensePlate().trim());
                    
                    result.setRegistrationExpiryDate(regExpiryPicker != null ? regExpiryPicker.getValue() : null);
                    result.setInsuranceExpiryDate(insExpiryPicker != null ? insExpiryPicker.getValue() : null);
                    result.setLeaseAgreementExpiryDate(leaseExpiryPicker != null ? leaseExpiryPicker.getValue() : null);
                    result.setLastInspectionDate(inspectionPicker != null ? inspectionPicker.getValue() : null);
                    result.setNextInspectionDueDate(inspExpiryPicker != null ? inspExpiryPicker.getValue() : null);
                    
                    return result;
                } catch (Exception ex) {
                    logger.error("Error in trailer dialog", ex);
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(result -> {
            if (isAdd) {
                trailerDAO.save(result);
                logger.info("Added new trailer: {}", result.getTrailerNumber());
            } else {
                trailerDAO.save(result);
                logger.info("Updated trailer: {}", result.getTrailerNumber());
            }
            loadData();
            notifyDataChange();
        });
    }
    
    private void showDocumentManager() {
        logger.info("Opening document manager");
        Trailer selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, 
                      "Please select a trailer to manage documents", 
                      ButtonType.OK).showAndWait();
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Document Manager - " + selected.getTrailerNumber());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create main layout
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setPrefWidth(800);
        mainContent.setPrefHeight(600);
        
        // Header with compliance status
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Documents for " + selected.getTrailerNumber());
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        // Compliance status indicator
        HBox complianceBox = createComplianceStatusIndicator(selected.getTrailerNumber());
        
        headerBox.getChildren().addAll(titleLabel, complianceBox);
        
        // Document list with enhanced features
        VBox documentSection = createDocumentListSection(selected.getTrailerNumber());
        
        // Action buttons
        HBox actionButtons = createDocumentActionButtons(selected.getTrailerNumber(), documentSection);
        
        mainContent.getChildren().addAll(headerBox, documentSection, actionButtons);
        dialog.getDialogPane().setContent(mainContent);
        dialog.showAndWait();
    }
    
    private void uploadDocument() {
        Trailer selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, 
                      "Please select a trailer to upload document", 
                      ButtonType.OK).showAndWait();
            return;
        }
        
        uploadDocumentForTrailer(selected.getTrailerNumber());
    }
    
    /**
     * Create compliance status indicator showing required documents
     */
    private HBox createComplianceStatusIndicator(String trailerNumber) {
        HBox complianceBox = new HBox(10);
        complianceBox.setAlignment(Pos.CENTER_LEFT);
        
        // Get configuration and check for required documents
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        List<String> requiredDocs = config.getRequiredDocuments("trailer");
        
        // Check which required documents are present
        Set<String> availableDocs = new HashSet<>();
        for (String docType : requiredDocs) {
            if (hasDocument(trailerNumber, docType)) {
                availableDocs.add(docType);
            }
        }
        
        boolean allCompliant = config.isCompliant("trailer", availableDocs);
        List<String> missingDocs = config.getMissingDocuments("trailer", availableDocs);
        
        // Create status indicators for each required document
        VBox statusIndicators = new VBox(5);
        for (String docType : requiredDocs) {
            boolean hasDoc = availableDocs.contains(docType);
            Circle statusLight = new Circle(6);
            statusLight.setFill(hasDoc ? Color.GREEN : Color.RED);
            Label docLabel = new Label(docType);
            docLabel.setFont(Font.font("Arial", 10));
            
            VBox indicator = new VBox(2, statusLight, docLabel);
            statusIndicators.getChildren().add(indicator);
        }
        
        // Overall compliance status
        Circle overallStatus = new Circle(10);
        overallStatus.setFill(allCompliant ? Color.GREEN : Color.ORANGE);
        String statusText = allCompliant ? "Compliant" : "Missing Documents";
        if (!allCompliant && !missingDocs.isEmpty()) {
            statusText += " (" + missingDocs.size() + " missing)";
        }
        Label overallLabel = new Label(statusText);
        overallLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        // Add configuration button
        Button configBtn = new Button("⚙️ Configure");
        configBtn.setStyle("-fx-font-size: 10px;");
        configBtn.setOnAction(e -> showComplianceConfiguration());
        
        complianceBox.getChildren().addAll(statusIndicators, new VBox(2, overallStatus, overallLabel), configBtn);
        
        return complianceBox;
    }
    
    private void showComplianceConfiguration() {
        DOTComplianceConfigDialog dialog = new DOTComplianceConfigDialog();
        dialog.showAndWait();
        
        // Refresh the document manager if it's open
        // This will update the compliance status with new configuration
    }
    
    /**
     * Create enhanced document list section with categorization
     */
    private VBox createDocumentListSection(String trailerNumber) {
        VBox documentSection = new VBox(10);
        
        // Get categories from configuration
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> trailerReqs = config.getTrailerRequirements();
        String[] categories = trailerReqs.keySet().toArray(new String[0]);
        
        // Create categorized document list
        ListView<DocumentItem> docListView = new ListView<>();
        docListView.setCellFactory(param -> new DocumentListCell());
        docListView.setPrefHeight(300);
        
        // Load and categorize documents
        ObservableList<DocumentItem> documents = loadCategorizedDocuments(trailerNumber, categories);
        docListView.setItems(documents);
        
        // Add selection listener for multiple selection
        docListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        documentSection.getChildren().addAll(
            new Label("Documents (Select multiple for merge):"),
            docListView
        );
        
        return documentSection;
    }
    
    /**
     * Create action buttons for document management
     */
    private HBox createDocumentActionButtons(String trailerNumber, VBox documentSection) {
        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER);
        actionButtons.setPadding(new Insets(10, 0, 0, 0));
        
        Button uploadBtn = ModernButtonStyles.createPrimaryButton("📤 Upload Document");
        Button viewBtn = ModernButtonStyles.createInfoButton("👁️ View Selected");
        Button mergeBtn = ModernButtonStyles.createSecondaryButton("🔗 Merge Selected");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print Selected");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete Selected");
        Button folderBtn = ModernButtonStyles.createSecondaryButton("📁 Open Folder");
        
        // Get the ListView from the document section
        ListView<DocumentItem> docListView = (ListView<DocumentItem>) documentSection.getChildren().get(1);
        
        uploadBtn.setOnAction(e -> {
            uploadDocumentForTrailerEnhanced(trailerNumber);
            refreshDocumentList(docListView, trailerNumber);
        });
        
        viewBtn.setOnAction(e -> {
            ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
            for (DocumentItem doc : selectedDocs) {
                viewDocument(trailerNumber, doc.getFileName());
            }
        });
        
        mergeBtn.setOnAction(e -> {
            ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
            if (selectedDocs.size() > 1) {
                mergeDocuments(trailerNumber, selectedDocs);
            } else {
                new Alert(Alert.AlertType.INFORMATION, 
                         "Please select multiple documents to merge", 
                         ButtonType.OK).showAndWait();
            }
        });
        
        printBtn.setOnAction(e -> {
            ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
            for (DocumentItem doc : selectedDocs) {
                printDocument(trailerNumber, doc.getFileName());
            }
        });
        
        deleteBtn.setOnAction(e -> {
            ObservableList<DocumentItem> selectedDocs = docListView.getSelectionModel().getSelectedItems();
            if (!selectedDocs.isEmpty()) {
                deleteSelectedDocuments(trailerNumber, selectedDocs);
                refreshDocumentList(docListView, trailerNumber);
            }
        });
        
        folderBtn.setOnAction(e -> openTrailerFolder(trailerNumber));
        
        actionButtons.getChildren().addAll(uploadBtn, viewBtn, mergeBtn, printBtn, deleteBtn, folderBtn);
        return actionButtons;
    }
    
    private void uploadDocumentForTrailer(String trailerNumber) {
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
            
            // Get document types from configuration
            DOTComplianceConfig config = DOTComplianceConfig.getInstance();
            Map<String, Boolean> requirements = config.getTrailerRequirements();
            List<String> documentTypes = new ArrayList<>(requirements.keySet());
            documentTypes.add("Other"); // Always include Other option
            
            ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(documentTypes));
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
                    String desc = descField != null ? descField.getText() : "";
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
                    // Create trailer's directory if it doesn't exist
                    Path trailerDir = Paths.get(docStoragePath, trailerNumber);
                    Files.createDirectories(trailerDir);
                    
                    // Create a filename with timestamp to avoid duplicates
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String extension = selectedFile.getName().substring(
                            selectedFile.getName().lastIndexOf('.'));
                    
                    String newFileName = docType.get().replaceAll("[\\\\/:*?\"<>|]", "_") 
                                       + "_" + timestamp + extension;
                    
                    Path destPath = trailerDir.resolve(newFileName);
                    Files.copy(selectedFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    logger.info("Uploaded document for trailer {}: {}", trailerNumber, newFileName);
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
    
    private void updateDocumentList(ListView<String> listView, String trailerNumber) {
        try {
            Path trailerDir = Paths.get(docStoragePath, trailerNumber);
            if (Files.exists(trailerDir)) {
                List<String> files = Files.list(trailerDir)
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
    
    private void viewDocument(String trailerNumber, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, trailerNumber, document);
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
    
    private void printDocument(String trailerNumber, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, trailerNumber, document);
            File file = docPath.toFile();
            if (Desktop.isDesktopSupported() && Desktop.isSupported(Desktop.Action.PRINT)) {
                Desktop.getDesktop().print(file);
                logger.info("Printing document: {}", docPath);
            } else {
                logger.warn("Printing is not supported");
                new Alert(Alert.AlertType.INFORMATION, 
                         "Printing is not supported directly. Please open the file first.", 
                         ButtonType.OK).showAndWait();
                viewDocument(trailerNumber, document);
            }
        } catch (Exception e) {
            logger.error("Failed to print document", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to print document: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private void deleteDocument(String trailerNumber, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, trailerNumber, document);
            
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
    
    private void printSelectedTrailerDocuments() {
        Trailer selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, 
                      "Please select a trailer to print documents", 
                      ButtonType.OK).showAndWait();
            return;
        }
        
        try {
            Path trailerDir = Paths.get(docStoragePath, selected.getTrailerNumber());
            if (!Files.exists(trailerDir) || !Files.isDirectory(trailerDir)) {
                new Alert(Alert.AlertType.INFORMATION, 
                          "No documents found for this trailer", 
                          ButtonType.OK).showAndWait();
                return;
            }
            
            List<String> documents = Files.list(trailerDir)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            
            if (documents.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, 
                          "No documents found for this trailer", 
                          ButtonType.OK).showAndWait();
                return;
            }
            
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Select Document to Print");
            dialog.setHeaderText("Choose a document to print for " + selected.getTrailerNumber());
            
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
                printDocument(selected.getTrailerNumber(), doc)
            );
            
        } catch (Exception e) {
            logger.error("Error accessing documents", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Error accessing documents: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    private <T> Callback<TableColumn<Trailer, LocalDate>, TableCell<Trailer, LocalDate>> 
    getExpiryDateCellFactory() {
        return col -> new TableCell<Trailer, LocalDate>() {
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
    
    private <T> Callback<TableColumn<Trailer, LocalDate>, TableCell<Trailer, LocalDate>> 
    getInspectionDateCellFactory() {
        return col -> new TableCell<Trailer, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(date == null ? "" : date.toString());
                // No color coding for inspection dates - they are not expiry dates
                setStyle("");
            }
        };
    }
    
    private void showExpiryAlerts() {
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        List<ExpiryInfo> allExpiries = new ArrayList<>();
        List<DocumentComplianceInfo> missingDocuments = new ArrayList<>();
        
        // Collect all expiring items and check document compliance
        for (Trailer trailer : trailers) {
            // Check registration expiry
            if (trailer.getRegistrationExpiryDate() != null && 
                trailer.getRegistrationExpiryDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(trailer, "Registration", 
                    trailer.getRegistrationExpiryDate()));
            }
            
            // Check insurance expiry
            if (trailer.getInsuranceExpiryDate() != null && 
                trailer.getInsuranceExpiryDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(trailer, "Insurance", 
                    trailer.getInsuranceExpiryDate()));
            }
            
            // Check lease agreement expiry
            if (trailer.getLeaseAgreementExpiryDate() != null && 
                trailer.getLeaseAgreementExpiryDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(trailer, "Lease Agreement", 
                    trailer.getLeaseAgreementExpiryDate()));
            }
            
            // Check inspection expiry
            if (trailer.getNextInspectionDueDate() != null && 
                trailer.getNextInspectionDueDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(trailer, "Inspection", 
                    trailer.getNextInspectionDueDate()));
            }
            
            // Check for missing required documents
            checkMissingDocuments(trailer, missingDocuments);
        }
        
        if (allExpiries.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, 
                     "No documents expiring within the next 30 days", 
                     ButtonType.OK).showAndWait();
            return;
        }
        
        // Sort by expiry date (earliest first)
        allExpiries.sort((a, b) -> a.expiryDate.compareTo(b.expiryDate));
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Document Expiry Alerts");
        dialog.setHeaderText(String.format("Documents Expiring Within 30 Days (%d items)", allExpiries.size()));
        
        TableView<ExpiryInfo> alertTable = new TableView<>();
        
        TableColumn<ExpiryInfo, String> numberCol = new TableColumn<>("Trailer #");
        numberCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().trailer.getTrailerNumber()));
        numberCol.setPrefWidth(100);
        
        TableColumn<ExpiryInfo, String> typeCol = new TableColumn<>("Trailer Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().trailer.getType()));
        typeCol.setPrefWidth(120);
        
        TableColumn<ExpiryInfo, String> assignedCol = new TableColumn<>("Assigned To");
        assignedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().trailer.getAssignedDriver()));
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
                        case "Lease Agreement":
                            setStyle("-fx-text-fill: #9C27B0; -fx-font-weight: bold;");
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
        alertTable.setItems(FXCollections.observableArrayList(allExpiries));
        
        // Add summary label
        Label summaryLabel = new Label();
        long expiredCount = allExpiries.stream().filter(e -> e.expiryDate.isBefore(LocalDate.now())).count();
        long expiringThisWeek = allExpiries.stream()
            .filter(e -> !e.expiryDate.isBefore(LocalDate.now()) && 
                         e.expiryDate.isBefore(LocalDate.now().plusWeeks(1)))
            .count();
        
        summaryLabel.setText(String.format("Summary: %d expired, %d expiring this week, %d total",
            expiredCount, expiringThisWeek, allExpiries.size()));
        summaryLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10; -fx-text-fill: #D32F2F;");
        
        VBox content = new VBox(10, summaryLabel, alertTable);
        content.setPrefWidth(860);
        content.setPrefHeight(500);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    public void refreshFromEmployeeChanges(List<Employee> employees) {
        logger.info("Refreshing trailers based on employee changes");
        // Update driver assignments from employee data
        for (Employee emp : employees) {
            if (emp.getTrailerNumber() != null && !emp.getTrailerNumber().isEmpty()) {
                trailers.stream()
                    .filter(t -> t.getTrailerNumber().equals(emp.getTrailerNumber()))
                    .findFirst()
                    .ifPresent(t -> {
                        t.setAssignedDriver(emp.getName());
                        trailerDAO.save(t);
                    });
            }
        }

        // Reload data to refresh the view
        loadData();
        notifyDataChange();
    }

    /** Register a listener to be notified when trailer data changes. */
    public void addDataChangeListener(Runnable listener) {
        if (listener != null) {
            dataChangeListeners.add(listener);
        }
    }

    /**
     * Get a snapshot of the current list of trailers.
     *
     * @return list of trailers currently loaded in the tab
     */
    public List<Trailer> getCurrentTrailers() {
        return new ArrayList<>(trailers);
    }

    /** Notify listeners that trailer data has changed. */
    private void notifyDataChange() {
        for (Runnable r : dataChangeListeners) {
            try {
                r.run();
            } catch (Exception ex) {
                logger.warn("Trailer data change listener threw exception", ex);
            }
        }
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
    
    // Inner class to hold expiry information
    private static class ExpiryInfo {
        final Trailer trailer;
        final String documentType;
        final LocalDate expiryDate;
        
        ExpiryInfo(Trailer trailer, String documentType, LocalDate expiryDate) {
            this.trailer = trailer;
            this.documentType = documentType;
            this.expiryDate = expiryDate;
        }
    }
    
    private static class DocumentComplianceInfo {
        final Trailer trailer;
        final String missingDocument;
        final String severity;
        
        DocumentComplianceInfo(Trailer trailer, String missingDocument, String severity) {
            this.trailer = trailer;
            this.missingDocument = missingDocument;
            this.severity = severity;
        }
    }

    /**
     * Show import dialog
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Trailer Data");
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
        progressDialog.setTitle("Importing Trailers");
        progressDialog.setHeaderText("Importing trailer data from " + selectedFile.getName());
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
                    
                    List<Trailer> trailerList = TrailerCSVImporter.importTrailers(selectedFile.toPath());
                    result.totalFound = trailerList.size();
                    
                    updateMessage("Processing trailers...");
                    updateProgress(50, 100);
                    
                    if (!trailerList.isEmpty()) {
                        updateMessage("Saving to database...");
                        updateProgress(75, 100);
                        
                        List<Trailer> savedTrailers = trailerDAO.addOrUpdateAll(trailerList);
                        result.imported = savedTrailers.size();
                        result.savedTrailers = savedTrailers; // Store for later use
                        
                        updateMessage("Import completed successfully!");
                        updateProgress(100, 100);
                    } else {
                        updateMessage("No valid trailers found in file");
                        result.errors.add("No valid trailer data found in the file");
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
            
            // Use the saved trailers that already have proper IDs
            List<Trailer> savedTrailers = result.savedTrailers;
            logger.info("Import completed with {} trailers, refreshing UI", savedTrailers.size());
            
            // Log IDs for debugging
            for (Trailer trailer : savedTrailers) {
                logger.debug("Trailer in result: {} (ID: {})", trailer.getTrailerNumber(), trailer.getId());
            }
            
            // Clear and reload the observable list with the correct data
            trailers.clear();
            trailers.addAll(savedTrailers);
            logger.info("Updated observable list with {} trailers", trailers.size());
            
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
            alert.setHeaderText("Failed to import trailers");
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
            summaryLabel.setText("✓ Successfully imported " + result.imported + " trailers");
            summaryLabel.setTextFill(Color.web("#4CAF50"));
        } else {
            summaryLabel.setText("⚠ No trailers were imported");
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
        List<Trailer> savedTrailers = new ArrayList<>(); // Added for storing saved trailers
    }
    
    /**
     * Update driver assignments for all trailers
     */
    private void updateDriverAssignments() {
        List<Employee> employees = employeeDAO.getAll();
        
        // Create a map of trailer number to driver
        driverMap.clear();
        for (Employee emp : employees) {
            if (emp.getTrailerNumber() != null && !emp.getTrailerNumber().isEmpty()) {
                driverMap.put(emp.getTrailerNumber(), emp);
            }
        }
        
        // Set driver names based on assignments
        for (Trailer trailer : trailers) {
            Employee driver = driverMap.get(trailer.getTrailerNumber());
            if (driver != null) {
                trailer.setAssignedDriver(driver.getName());
            }
        }
    }

    /**
     * Force refresh the entire table and all related data structures
     */
    private void forceRefreshTable() {
        logger.debug("Forcing table refresh");
        
        // Simply refresh the table - this is the safest approach
        // The observable list will automatically update the table
        table.refresh();
        
        // Update status bar
        updateStatusBar();
    }
    
    /**
     * Update status bar with current counts
     */
    private void updateStatusBar() {
        // This will be called by the existing listener in createStatusBar()
        // The listener automatically updates when trailers list changes
    }
    
    // ========== ENHANCED DOCUMENT MANAGEMENT HELPER METHODS ==========
    
    /**
     * Check for missing required documents for a trailer
     */
    private void checkMissingDocuments(Trailer trailer, List<DocumentComplianceInfo> missingDocuments) {
        String trailerNumber = trailer.getTrailerNumber();
        
        // Get configuration and check for required documents
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> requirements = config.getTrailerRequirements();
        
        for (Map.Entry<String, Boolean> entry : requirements.entrySet()) {
            if (entry.getValue() && !hasDocument(trailerNumber, entry.getKey())) {
                // Determine severity based on document type
                String severity = isCriticalDocument(entry.getKey()) ? "HIGH" : 
                                isImportantDocument(entry.getKey()) ? "MEDIUM" : "LOW";
                missingDocuments.add(new DocumentComplianceInfo(trailer, entry.getKey(), severity));
            }
        }
    }
    
    private boolean isCriticalDocument(String documentType) {
        String[] criticalDocs = {
            "Annual DOT Inspection", "Registration Document"
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
            "Insurance Documents", "Trailer DOT Inspection"
        };
        for (String important : importantDocs) {
            if (documentType.contains(important)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if a trailer has a specific document type
     */
    private boolean hasDocument(String trailerNumber, String documentType) {
        try {
            Path trailerDir = Paths.get(docStoragePath, trailerNumber);
            if (!Files.exists(trailerDir)) {
                return false;
            }
            
            return Files.list(trailerDir)
                .anyMatch(file -> file.getFileName().toString().contains(documentType));
        } catch (Exception e) {
            logger.error("Error checking document existence", e);
            return false;
        }
    }
    
    /**
     * Load and categorize documents for a trailer
     */
    private ObservableList<DocumentItem> loadCategorizedDocuments(String trailerNumber, String[] categories) {
        ObservableList<DocumentItem> documents = FXCollections.observableArrayList();
        
        try {
            Path trailerDir = Paths.get(docStoragePath, trailerNumber);
            if (!Files.exists(trailerDir)) {
                return documents;
            }
            
            Files.list(trailerDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String category = determineDocumentCategory(fileName, categories);
                    documents.add(new DocumentItem(fileName, category, file.toFile()));
                });
            
            // Sort by category and then by filename
            documents.sort((a, b) -> {
                int catCompare = a.getCategory().compareTo(b.getCategory());
                return catCompare != 0 ? catCompare : a.getFileName().compareTo(b.getFileName());
            });
            
        } catch (Exception e) {
            logger.error("Error loading categorized documents", e);
        }
        
        return documents;
    }
    
    /**
     * Determine document category based on filename
     */
    private String determineDocumentCategory(String fileName, String[] categories) {
        String lowerFileName = fileName.toLowerCase();
        
        for (String category : categories) {
            String lowerCategory = category.toLowerCase();
            if (lowerFileName.contains(lowerCategory.replace(" ", "")) ||
                lowerFileName.contains(lowerCategory.replace(" ", "_"))) {
                return category;
            }
        }
        
        return "Other";
    }
    
    /**
     * Enhanced upload with proper file naming
     */
    private void uploadDocumentForTrailerEnhanced(String trailerNumber) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Document to Upload");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
        if (selectedFile != null) {
            showDocumentTypeDialog(trailerNumber, selectedFile);
        }
    }
    
    /**
     * Show document type selection dialog with enhanced categories
     */
    private void showDocumentTypeDialog(String trailerNumber, File selectedFile) {
        Dialog<String> typeDialog = new Dialog<>();
        typeDialog.setTitle("Document Type");
        typeDialog.setHeaderText("Select document type for " + trailerNumber);
        
        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Annual DOT Inspection", "Insurance Documents", "Registration Document",
                "Trailer DOT Inspection", "Lease Agreement Expiry", "Trailer Lease Expiry",
                "Tire Inspection or Replacement Records", "Other"));
        typeCombo.setValue("Other");
        
        TextField descField = new TextField();
        descField.setPromptText("Document description (optional)");
        
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
                String desc = descField != null ? descField.getText() : "";
                if (desc == null || desc.trim().isEmpty()) {
                    return type;
                }
                return type + " - " + desc;
            }
            return null;
        });
        
        Optional<String> docType = typeDialog.showAndWait();
        
        if (docType.isPresent()) {
            saveDocumentWithProperNaming(trailerNumber, selectedFile, docType.get());
        }
    }
    
    /**
     * Save document with proper naming convention
     */
    private void saveDocumentWithProperNaming(String trailerNumber, File selectedFile, String docType) {
        try {
            // Create trailer's directory if it doesn't exist
            Path trailerDir = Paths.get(docStoragePath, trailerNumber);
            Files.createDirectories(trailerDir);
            
            // Create filename following the convention: TrailerNumber_DocumentType.ext
            String extension = selectedFile.getName().substring(
                    selectedFile.getName().lastIndexOf('.'));
            
            // Clean document type for filename
            String cleanDocType = docType.replaceAll("[\\\\/:*?\"<>|]", "_")
                                       .replaceAll("\\s+", "");
            
            String newFileName = trailerNumber + "_" + cleanDocType + extension;
            
            // Check if file exists and add counter if needed
            Path destPath = trailerDir.resolve(newFileName);
            int counter = 1;
            while (Files.exists(destPath)) {
                String baseName = newFileName.substring(0, newFileName.lastIndexOf('.'));
                String ext = newFileName.substring(newFileName.lastIndexOf('.'));
                newFileName = baseName + "_" + counter + ext;
                destPath = trailerDir.resolve(newFileName);
                counter++;
            }
            
            Files.copy(selectedFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Uploaded document for trailer {}: {}", trailerNumber, newFileName);
            new Alert(Alert.AlertType.INFORMATION, 
                     "Document uploaded successfully: " + newFileName, 
                     ButtonType.OK).showAndWait();
            
        } catch (Exception e) {
            logger.error("Failed to upload document", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to upload document: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    /**
     * Refresh document list
     */
    private void refreshDocumentList(ListView<DocumentItem> docListView, String trailerNumber) {
        // Get categories from configuration
        DOTComplianceConfig config = DOTComplianceConfig.getInstance();
        Map<String, Boolean> trailerReqs = config.getTrailerRequirements();
        String[] categories = trailerReqs.keySet().toArray(new String[0]);
        
        ObservableList<DocumentItem> documents = loadCategorizedDocuments(trailerNumber, categories);
        docListView.setItems(documents);
    }
    
    /**
     * Merge selected documents into a single PDF
     */
    private void mergeDocuments(String trailerNumber, ObservableList<DocumentItem> selectedDocs) {
        try {
            // For now, we'll create a simple text file listing the documents
            // In a full implementation, you'd use a PDF library like iText or Apache PDFBox
            Path trailerDir = Paths.get(docStoragePath, trailerNumber);
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path mergedFile = trailerDir.resolve(trailerNumber + "_MergedDocuments_" + timestamp + ".txt");
            
            List<String> content = new ArrayList<>();
            content.add("Merged Documents for Trailer: " + trailerNumber);
            content.add("Generated: " + java.time.LocalDateTime.now());
            content.add("");
            
            for (DocumentItem doc : selectedDocs) {
                content.add("Document: " + doc.getFileName());
                content.add("Category: " + doc.getCategory());
                content.add("Path: " + doc.getFile().getAbsolutePath());
                content.add("");
            }
            
            Files.write(mergedFile, content);
            
            logger.info("Created merged document list: {}", mergedFile);
            new Alert(Alert.AlertType.INFORMATION, 
                     "Documents merged successfully. File: " + mergedFile.getFileName(), 
                     ButtonType.OK).showAndWait();
            
        } catch (Exception e) {
            logger.error("Failed to merge documents", e);
            new Alert(Alert.AlertType.ERROR, 
                     "Failed to merge documents: " + e.getMessage(), 
                     ButtonType.OK).showAndWait();
        }
    }
    
    /**
     * Delete selected documents
     */
    private void deleteSelectedDocuments(String trailerNumber, ObservableList<DocumentItem> selectedDocs) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Selected Documents");
        confirm.setContentText("Are you sure you want to delete " + selectedDocs.size() + " document(s)?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int deletedCount = 0;
                for (DocumentItem doc : selectedDocs) {
                    try {
                        Path docPath = Paths.get(docStoragePath, trailerNumber, doc.getFileName());
                        if (Files.exists(docPath)) {
                            Files.delete(docPath);
                            deletedCount++;
                            logger.info("Deleted document: {}", doc.getFileName());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to delete document: {}", doc.getFileName(), e);
                    }
                }
                
                new Alert(Alert.AlertType.INFORMATION, 
                         "Deleted " + deletedCount + " document(s)", 
                         ButtonType.OK).showAndWait();
            }
        });
    }
    
    // ========== HELPER CLASSES ==========
    
    /**
     * Document item class for enhanced document management
     */
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
            return category + ": " + fileName;
        }
    }
    
    /**
     * Custom cell factory for document list
     */
    private static class DocumentListCell extends ListCell<DocumentItem> {
        @Override
        protected void updateItem(DocumentItem item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox content = new HBox(10);
                content.setAlignment(Pos.CENTER_LEFT);
                
                // Category indicator
                Circle indicator = new Circle(6);
                indicator.setFill(getCategoryColor(item.getCategory()));
                
                // Document info
                VBox info = new VBox(2);
                Label nameLabel = new Label(item.getFileName());
                nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                Label categoryLabel = new Label(item.getCategory());
                categoryLabel.setFont(Font.font("Arial", 10));
                categoryLabel.setTextFill(Color.GRAY);
                
                info.getChildren().addAll(nameLabel, categoryLabel);
                content.getChildren().addAll(indicator, info);
                
                setGraphic(content);
                setText(null);
            }
        }
        
        private Color getCategoryColor(String category) {
            return switch (category) {
                case "Annual DOT Inspection" -> Color.GREEN;
                case "Registration Document" -> Color.BLUE;
                case "Insurance Documents" -> Color.ORANGE;
                case "Trailer DOT Inspection" -> Color.PURPLE;
                case "Lease Agreement Expiry" -> Color.RED;
                case "Trailer Lease Expiry" -> Color.DARKRED;
                case "Tire Inspection or Replacement Records" -> Color.BROWN;
                default -> Color.GRAY;
            };
        }
    }
    
    /**
     * Open trailer's document folder
     */
    private void openTrailerFolder(String trailerNumber) {
        try {
            Path trailerDir = Paths.get(docStoragePath, trailerNumber);
            if (!Files.exists(trailerDir)) {
                Files.createDirectories(trailerDir);
            }
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(trailerDir.toFile());
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Folder Location");
                alert.setContentText("Trailer folder: " + trailerDir.toString());
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Failed to open trailer folder", e);
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
        dialog.setTitle("Export Trailers");
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
     * Export trailers data to CSV file
     */
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Trailers to CSV");
        fileChooser.setInitialFileName("trailers_" + 
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
                writer.write("Trailer Number,Year,Make,Model,VIN,Type,Status,License Plate,Registration Expiry," +
                           "Insurance Expiry,Last Inspection Date,Next Inspection Due,Current Location," +
                           "Assigned Truck,Assigned Driver");
                writer.newLine();
                
                // Filter the trailers if search filter is applied
                List<Trailer> exportList = new ArrayList<>();
                if (table.getItems() != null) {
                    exportList.addAll(table.getItems());
                } else {
                    exportList.addAll(trailers);
                }
                
                // Write data
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                for (Trailer trailer : exportList) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCSV(trailer.getTrailerNumber())).append(",");
                    line.append(trailer.getYear() > 0 ? trailer.getYear() : "").append(",");
                    line.append(escapeCSV(trailer.getMake())).append(",");
                    line.append(escapeCSV(trailer.getModel())).append(",");
                    line.append(escapeCSV(trailer.getVin())).append(",");
                    line.append(escapeCSV(trailer.getType())).append(",");
                    line.append(trailer.getStatus() != null ? trailer.getStatus().name() : "ACTIVE").append(",");
                    line.append(escapeCSV(trailer.getLicensePlate())).append(",");
                    line.append(trailer.getRegistrationExpiryDate() != null ? 
                            trailer.getRegistrationExpiryDate().format(dateFormatter) : "").append(",");
                    line.append(trailer.getInsuranceExpiryDate() != null ? 
                            trailer.getInsuranceExpiryDate().format(dateFormatter) : "").append(",");
                    line.append(trailer.getLastInspectionDate() != null ? 
                            trailer.getLastInspectionDate().format(dateFormatter) : "").append(",");
                    line.append(trailer.getNextInspectionDueDate() != null ? 
                            trailer.getNextInspectionDueDate().format(dateFormatter) : "").append(",");
                    line.append(escapeCSV(trailer.getCurrentLocation())).append(",");
                    line.append(escapeCSV(trailer.getAssignedTruck())).append(",");
                    line.append(escapeCSV(trailer.getAssignedDriver()));
                    
                    writer.write(line.toString());
                    writer.newLine();
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("Export Completed");
                alert.setContentText("Exported " + exportList.size() + " trailers to CSV successfully.");
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
     * Export trailers data to XLSX file
     */
    private void exportToXLSX() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Trailers to Excel");
        fileChooser.setInitialFileName("trailers_" + 
                java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Trailers");
                
                // Create header row
                String[] headers = {"Trailer Number", "Year", "Make", "Model", "VIN", "Type", "Status", 
                                   "License Plate", "Registration Expiry", "Insurance Expiry", 
                                   "Last Inspection Date", "Next Inspection Due", "Current Location",
                                   "Assigned Truck", "Assigned Driver"};
                
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
                
                // Filter the trailers if search filter is applied
                List<Trailer> exportList = new ArrayList<>();
                if (table.getItems() != null) {
                    exportList.addAll(table.getItems());
                } else {
                    exportList.addAll(trailers);
                }
                
                // Create data rows
                int rowNum = 1;
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("mm/dd/yyyy"));
                
                for (Trailer trailer : exportList) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    
                    row.createCell(0).setCellValue(trailer.getTrailerNumber());
                    if (trailer.getYear() > 0) {
                        row.createCell(1).setCellValue(trailer.getYear());
                    }
                    row.createCell(2).setCellValue(trailer.getMake());
                    row.createCell(3).setCellValue(trailer.getModel());
                    row.createCell(4).setCellValue(trailer.getVin());
                    row.createCell(5).setCellValue(trailer.getType());
                    row.createCell(6).setCellValue(trailer.getStatus() != null ? 
                            trailer.getStatus().name() : "ACTIVE");
                    row.createCell(7).setCellValue(trailer.getLicensePlate());
                    
                    // Registration expiry date
                    org.apache.poi.ss.usermodel.Cell regExpiryCell = row.createCell(8);
                    if (trailer.getRegistrationExpiryDate() != null) {
                        regExpiryCell.setCellValue(java.util.Date.from(trailer.getRegistrationExpiryDate().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        regExpiryCell.setCellStyle(dateStyle);
                    }
                    
                    // Insurance expiry date
                    org.apache.poi.ss.usermodel.Cell insExpiryCell = row.createCell(9);
                    if (trailer.getInsuranceExpiryDate() != null) {
                        insExpiryCell.setCellValue(java.util.Date.from(trailer.getInsuranceExpiryDate().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        insExpiryCell.setCellStyle(dateStyle);
                    }
                    
                    // Last inspection date
                    org.apache.poi.ss.usermodel.Cell lastInspCell = row.createCell(10);
                    if (trailer.getLastInspectionDate() != null) {
                        lastInspCell.setCellValue(java.util.Date.from(trailer.getLastInspectionDate().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        lastInspCell.setCellStyle(dateStyle);
                    }
                    
                    // Next inspection due date
                    org.apache.poi.ss.usermodel.Cell nextInspCell = row.createCell(11);
                    if (trailer.getNextInspectionDueDate() != null) {
                        nextInspCell.setCellValue(java.util.Date.from(trailer.getNextInspectionDueDate().atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant()));
                        nextInspCell.setCellStyle(dateStyle);
                    }
                    
                    row.createCell(12).setCellValue(trailer.getCurrentLocation());
                    row.createCell(13).setCellValue(trailer.getAssignedTruck());
                    row.createCell(14).setCellValue(trailer.getAssignedDriver());
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
                alert.setContentText("Exported " + exportList.size() + " trailers to Excel successfully.");
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
}