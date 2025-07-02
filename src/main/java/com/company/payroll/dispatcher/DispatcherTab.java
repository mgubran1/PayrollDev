package com.company.payroll.dispatcher;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dispatcher tab for managing driver availability and load assignments
 */
public class DispatcherTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherTab.class);
    
    private final ObservableList<DriverAvailability> driverAvailabilities = FXCollections.observableArrayList();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final LoadDAO loadDAO = new LoadDAO();
    
    private TableView<DriverAvailability> availabilityTable;
    private ComboBox<DriverAvailability.AvailabilityStatus> statusFilter;
    private TextField searchField;
    
    public DispatcherTab() {
        logger.info("Initializing DispatcherTab");
        initializeUI();
        loadDriverAvailability();
    }
    
    private void initializeUI() {
        // Create top toolbar
        HBox toolbar = createToolbar();
        
        // Create the main table
        availabilityTable = createAvailabilityTable();
        
        // Create bottom status bar
        HBox statusBar = createStatusBar();
        
        // Layout
        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(10));
        mainContent.getChildren().addAll(toolbar, availabilityTable, statusBar);
        VBox.setVgrow(availabilityTable, Priority.ALWAYS);
        
        setCenter(mainContent);
    }
    
    private HBox createToolbar() {
        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search driver name or truck...");
        searchField.setPrefWidth(200);
        
        // Status filter
        statusFilter = new ComboBox<>();
        statusFilter.getItems().add(null); // All statuses
        statusFilter.getItems().addAll(DriverAvailability.AvailabilityStatus.values());
        statusFilter.setPromptText("All Statuses");
        
        // Buttons
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadDriverAvailability());
        
        Button updateStatusBtn = new Button("Update Status");
        updateStatusBtn.setOnAction(e -> showUpdateStatusDialog());
        
        Button viewLoadsBtn = new Button("View Loads");
        viewLoadsBtn.setOnAction(e -> showDriverLoads());
        
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        // Layout
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(
            new Label("Search:"), searchField,
            new Separator(),
            new Label("Status:"), statusFilter,
            new Separator(),
            refreshBtn, updateStatusBtn, viewLoadsBtn, settingsBtn
        );
        
        // Add listeners
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTable());
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> filterTable());
        
        return toolbar;
    }
    
    private TableView<DriverAvailability> createAvailabilityTable() {
        TableView<DriverAvailability> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        // Driver Name column
        TableColumn<DriverAvailability, String> nameCol = new TableColumn<>("Driver Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
        nameCol.setMinWidth(150);
        
        // Truck Unit column
        TableColumn<DriverAvailability, String> truckCol = new TableColumn<>("Truck Unit");
        truckCol.setCellValueFactory(new PropertyValueFactory<>("truckUnit"));
        truckCol.setMinWidth(100);
        
        // Trailer column
        TableColumn<DriverAvailability, String> trailerCol = new TableColumn<>("Trailer #");
        trailerCol.setCellValueFactory(new PropertyValueFactory<>("trailerNumber"));
        trailerCol.setMinWidth(100);
        
        // Status column with color coding
        TableColumn<DriverAvailability, DriverAvailability.AvailabilityStatus> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(DriverAvailability.AvailabilityStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status.getDisplayName());
                    setStyle("-fx-background-color: " + status.getColor() + "; -fx-font-weight: bold;");
                }
            }
        });
        statusCol.setMinWidth(120);
        
        // Current Location column
        TableColumn<DriverAvailability, String> locationCol = new TableColumn<>("Current Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("currentLocation"));
        locationCol.setMinWidth(150);
        
        // Expected Return column
        TableColumn<DriverAvailability, LocalDate> returnDateCol = new TableColumn<>("Expected Return");
        returnDateCol.setCellValueFactory(new PropertyValueFactory<>("expectedReturnDate"));
        returnDateCol.setMinWidth(120);
        
        // Notes column
        TableColumn<DriverAvailability, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setMinWidth(200);
        
        table.getColumns().addAll(nameCol, truckCol, trailerCol, statusCol, locationCol, returnDateCol, notesCol);
        table.setItems(driverAvailabilities);
        
        // Double-click to edit
        table.setRowFactory(tv -> {
            TableRow<DriverAvailability> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showUpdateStatusDialog(row.getItem());
                }
            });
            return row;
        });
        
        return table;
    }
    
    private HBox createStatusBar() {
        Label totalLabel = new Label();
        Label availableLabel = new Label();
        Label onRoadLabel = new Label();
        Label offDutyLabel = new Label();
        
        updateStatusCounts(totalLabel, availableLabel, onRoadLabel, offDutyLabel);
        
        HBox statusBar = new HBox(20);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        statusBar.getChildren().addAll(
            totalLabel,
            new Separator(),
            availableLabel,
            new Separator(),
            onRoadLabel,
            new Separator(),
            offDutyLabel
        );
        
        return statusBar;
    }
    
    private void updateStatusCounts(Label total, Label available, Label onRoad, Label offDuty) {
        int totalCount = driverAvailabilities.size();
        int availableCount = (int) driverAvailabilities.stream()
            .filter(d -> d.getStatus() == DriverAvailability.AvailabilityStatus.AVAILABLE)
            .count();
        int onRoadCount = (int) driverAvailabilities.stream()
            .filter(d -> d.isOnRoad())
            .count();
        int offDutyCount = (int) driverAvailabilities.stream()
            .filter(d -> d.getStatus() == DriverAvailability.AvailabilityStatus.OFF_DUTY || 
                        d.getStatus() == DriverAvailability.AvailabilityStatus.ON_LEAVE)
            .count();
        
        total.setText("Total Drivers: " + totalCount);
        available.setText("Available: " + availableCount);
        available.setTextFill(Color.GREEN);
        onRoad.setText("On Road: " + onRoadCount);
        onRoad.setTextFill(Color.ORANGE);
        offDuty.setText("Off Duty: " + offDutyCount);
        offDuty.setTextFill(Color.RED);
    }
    
    private void loadDriverAvailability() {
        logger.info("Loading driver availability");
        driverAvailabilities.clear();
        
        List<Employee> activeDrivers = employeeDAO.getActive().stream()
            .filter(Employee::isDriver)
            .collect(Collectors.toList());
        
        for (Employee driver : activeDrivers) {
            // Create availability record
            DriverAvailability availability = new DriverAvailability(
                driver.getId(),
                driver.getName(),
                driver.getTruckUnit(),
                driver.getTrailerNumber(),
                DriverAvailability.AvailabilityStatus.AVAILABLE
            );
            
            // Check if driver has active loads
            List<Load> activeLoads = loadDAO.getActiveLoadsByDriver(driver.getId());
            if (!activeLoads.isEmpty()) {
                Load currentLoad = activeLoads.get(0);
                availability.setStatus(DriverAvailability.AvailabilityStatus.ON_ROAD);
                availability.setCurrentLoad(currentLoad);
                availability.setCurrentLocation(currentLoad.getDeliveryCity() + ", " + currentLoad.getDeliveryState());
                availability.setExpectedReturnDate(currentLoad.getDeliveryDate());
            }
            
            driverAvailabilities.add(availability);
        }
        
        logger.info("Loaded {} driver availability records", driverAvailabilities.size());
    }
    
    private void filterTable() {
        // Implementation for filtering based on search and status
    }
    
    private void showUpdateStatusDialog() {
        DriverAvailability selected = availabilityTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showUpdateStatusDialog(selected);
        }
    }
    
    private void showUpdateStatusDialog(DriverAvailability driver) {
        // Implementation for status update dialog
    }
    
    private void showDriverLoads() {
        // Implementation for showing driver's load history
    }
    
    private void showSettingsDialog() {
        // Implementation for dispatcher settings dialog
    }
}