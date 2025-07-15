package com.company.payroll.trailers;

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
        
        TableColumn<Trailer, LocalDate> inspExpiryCol = new TableColumn<>("Inspection Expiry");
        inspExpiryCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getNextInspectionDueDate()));
        inspExpiryCol.setCellFactory(getExpiryDateCellFactory());
        inspExpiryCol.setPrefWidth(150);
        
        TableColumn<Trailer, String> plateCol = new TableColumn<>("License Plate");
        plateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLicensePlate()));
        plateCol.setPrefWidth(120);
        
        table.getColumns().setAll(java.util.List.of(
                numberCol, vinCol, makeModelCol, typeCol, statusCol, assignedToCol,
                regExpiryCol, insExpiryCol, inspExpiryCol, plateCol));
        
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
        Button uploadBtn = ModernButtonStyles.createInfoButton("📤 Upload Document");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print Document");
        
        uploadBtn.setOnAction(e -> uploadDocument());
        printBtn.setOnAction(e -> printSelectedTrailerDocuments());
        
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
                    result.setTrailerNumber(numberField.getText().trim());
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
                    
                    try {
                        if (!lengthField.getText().trim().isEmpty()) {
                            result.setLength(Double.parseDouble(lengthField.getText().trim()));
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid length format: {}", lengthField.getText());
                    }
                    
                    try {
                        if (!capacityField.getText().trim().isEmpty()) {
                            result.setCapacity(Double.parseDouble(capacityField.getText().trim()));
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid capacity format: {}", capacityField.getText());
                    }
                    
                    result.setType(typeBox.getValue());
                    result.setStatus(statusBox.getValue() != null ? statusBox.getValue() : TrailerStatus.ACTIVE);
                    result.setLicensePlate(plateField.getText().trim());
                    result.setRegistrationExpiryDate(regExpiryPicker.getValue());
                    result.setInsuranceExpiryDate(insExpiryPicker.getValue());
                    result.setNextInspectionDueDate(inspExpiryPicker.getValue());
                    
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
        
        // Create document list view
        ListView<String> docListView = new ListView<>();
        updateDocumentList(docListView, selected.getTrailerNumber());
        
        // Buttons for document operations
        Button uploadBtn = ModernButtonStyles.createPrimaryButton("📤 Upload");
        Button viewBtn = ModernButtonStyles.createInfoButton("👁️ View");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete");
        
        uploadBtn.setOnAction(e -> {
            uploadDocumentForTrailer(selected.getTrailerNumber());
            updateDocumentList(docListView, selected.getTrailerNumber());
        });
        
        viewBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                viewDocument(selected.getTrailerNumber(), selectedDoc);
            }
        });
        
        printBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                printDocument(selected.getTrailerNumber(), selectedDoc);
            }
        });
        
        deleteBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                deleteDocument(selected.getTrailerNumber(), selectedDoc);
                updateDocumentList(docListView, selected.getTrailerNumber());
            }
        });
        
        HBox buttonBox = new HBox(10, uploadBtn, viewBtn, printBtn, deleteBtn);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setAlignment(Pos.CENTER);
        
        VBox content = new VBox(10, 
                               new Label("Documents for " + selected.getTrailerNumber()), 
                               docListView, 
                               buttonBox);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setPrefHeight(400);
        
        dialog.getDialogPane().setContent(content);
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
            
            ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "Registration", "Insurance", "Inspection", "Maintenance", "Repair", "Other"));
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
    
    private void showExpiryAlerts() {
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        List<ExpiryInfo> allExpiries = new ArrayList<>();
        
        // Collect all expiring items
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
            
            // Check inspection expiry
            if (trailer.getNextInspectionDueDate() != null && 
                trailer.getNextInspectionDueDate().isBefore(thirtyDaysFromNow)) {
                allExpiries.add(new ExpiryInfo(trailer, "Inspection", 
                    trailer.getNextInspectionDueDate()));
            }
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
    
    private <T> Callback<TableColumn<Trailer, LocalDate>, TableCell<Trailer, LocalDate>> 
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
}