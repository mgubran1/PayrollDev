package com.company.payroll.dispatcher;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Fleet status overview with driver locations and availability
 */
public class DispatcherFleetStatusView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherFleetStatusView.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    
    private final DispatcherController controller;
    private final TableView<DispatcherDriverStatus> driverTable;
    private final ObservableList<DispatcherDriverStatus> drivers;
    private final Map<DispatcherDriverStatus.Status, Integer> statusCounts;
    
    public DispatcherFleetStatusView(DispatcherController controller) {
        this.controller = controller;
        this.driverTable = new TableView<>();
        this.drivers = FXCollections.observableArrayList();
        this.statusCounts = new HashMap<>();
        
        initializeUI();
        refresh();
    }
    
    private void initializeUI() {
        // Header with summary
        VBox header = createHeader();
        setTop(header);
        
        // Main content - driver table
        setupDriverTable();
        
        // Wrap in scroll pane
        ScrollPane scrollPane = new ScrollPane(driverTable);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        
        setCenter(scrollPane);
        
        // Bottom toolbar
        HBox toolbar = createToolbar();
        setBottom(toolbar);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        Label titleLabel = new Label("Fleet Status Overview");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        
        // Status summary grid
        GridPane summaryGrid = createSummaryGrid();
        
        header.getChildren().addAll(titleLabel, summaryGrid);
        
        return header;
    }
    
    private GridPane createSummaryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        
        // Calculate status counts
        updateStatusCounts();
        
        int col = 0;
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            VBox statusBox = createStatusBox(status, statusCounts.getOrDefault(status, 0));
            grid.add(statusBox, col++, 0);
        }
        
        // Total drivers
        VBox totalBox = createTotalBox();
        grid.add(totalBox, col, 0);
        
        return grid;
    }
    
    private VBox createStatusBox(DispatcherDriverStatus.Status status, int count) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
        box.setPrefWidth(120);
        
        Circle indicator = new Circle(8);
        indicator.setFill(Color.web(status.getColor()));
        
        Label statusLabel = new Label(status.getDisplayName());
        statusLabel.setStyle("-fx-font-size: 11;");
        
        Label countLabel = new Label(String.valueOf(count));
        countLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");
        
        box.getChildren().addAll(indicator, statusLabel, countLabel);
        
        return box;
    }
    
    private VBox createTotalBox() {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196f3; -fx-border-radius: 5; -fx-background-radius: 5;");
        box.setPrefWidth(120);
        
        Label titleLabel = new Label("Total Drivers");
        titleLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        
        int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
        Label countLabel = new Label(String.valueOf(total));
        countLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #2196f3;");
        
        box.getChildren().addAll(titleLabel, countLabel);
        
        return box;
    }
    
    private void setupDriverTable() {
        // Status column with color indicator
        TableColumn<DispatcherDriverStatus, DispatcherDriverStatus.Status> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setCellFactory(column -> new TableCell<>() {
            private final Circle indicator = new Circle(6);
            
            @Override
            protected void updateItem(DispatcherDriverStatus.Status status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(5);
                    box.setAlignment(Pos.CENTER_LEFT);
                    indicator.setFill(Color.web(status.getColor()));
                    Label label = new Label(status.getDisplayName());
                    box.getChildren().addAll(indicator, label);
                    setGraphic(box);
                }
            }
        });
        statusCol.setPrefWidth(120);
        
        // Driver name column
        TableColumn<DispatcherDriverStatus, String> nameCol = new TableColumn<>("Driver");
        nameCol.setCellValueFactory(data -> data.getValue().driverNameProperty());
        nameCol.setPrefWidth(150);
        
        // Truck unit column
        TableColumn<DispatcherDriverStatus, String> truckCol = new TableColumn<>("Truck");
        truckCol.setCellValueFactory(data -> data.getValue().truckUnitProperty());
        truckCol.setPrefWidth(80);
        
        // Trailer column
        TableColumn<DispatcherDriverStatus, String> trailerCol = new TableColumn<>("Trailer");
        trailerCol.setCellValueFactory(data -> data.getValue().trailerNumberProperty());
        trailerCol.setPrefWidth(100);
        
        // Current location column
        TableColumn<DispatcherDriverStatus, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(data -> data.getValue().currentLocationProperty());
        locationCol.setPrefWidth(200);
        
        // Current/Next load column
        TableColumn<DispatcherDriverStatus, String> loadCol = new TableColumn<>("Current/Next Load");
        loadCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            if (driver.getCurrentLoad() != null) {
                return new SimpleStringProperty("Current: " + driver.getCurrentLoad().getLoadNumber() + 
                    " (" + driver.getCurrentLoad().getCustomer() + ")");
            } else if (driver.getNextLoad() != null) {
                return new SimpleStringProperty("Next: " + driver.getNextLoad().getLoadNumber() + 
                    " (" + driver.getNextLoad().getCustomer() + ")");
            } else {
                return new SimpleStringProperty("No assigned loads");
            }
        });
        loadCol.setPrefWidth(250);
        
        // ETA column
        TableColumn<DispatcherDriverStatus, String> etaCol = new TableColumn<>("ETA/Available");
        etaCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            if (driver.getEstimatedAvailableTime() != null) {
                return new SimpleStringProperty(driver.getEstimatedAvailableTime().format(TIME_FORMAT));
            } else if (driver.isAvailable()) {
                return new SimpleStringProperty("Available Now");
            } else {
                return new SimpleStringProperty("N/A");
            }
        });
        etaCol.setPrefWidth(120);
        
        // Hours worked column
        TableColumn<DispatcherDriverStatus, String> hoursCol = new TableColumn<>("Hours Today/Week");
        hoursCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            return new SimpleStringProperty(String.format("%.1f / %.1f", 
                driver.getHoursWorkedToday(), driver.getHoursWorkedWeek()));
        });
        hoursCol.setPrefWidth(120);
        
        // Action column
        TableColumn<DispatcherDriverStatus, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button detailsBtn = new Button("Details");
            private final Button updateBtn = new Button("Update");
            private final HBox buttonBox = new HBox(5);
            
            {
                detailsBtn.setStyle("-fx-font-size: 10;");
                updateBtn.setStyle("-fx-font-size: 10;");
                buttonBox.getChildren().addAll(detailsBtn, updateBtn);
                buttonBox.setAlignment(Pos.CENTER);
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    DispatcherDriverStatus driver = getTableRow().getItem();
                    if (driver != null) {
                        detailsBtn.setOnAction(e -> showDriverDetails(driver));
                        updateBtn.setOnAction(e -> updateDriverStatus(driver));
                        setGraphic(buttonBox);
                    }
                }
            }
        });
        actionCol.setPrefWidth(150);
        
        driverTable.getColumns().addAll(statusCol, nameCol, truckCol, trailerCol, 
                                       locationCol, loadCol, etaCol, hoursCol, actionCol);
        driverTable.setItems(drivers);
        
        // Row factory for hover effects
        driverTable.setRowFactory(tv -> {
            TableRow<DispatcherDriverStatus> row = new TableRow<>();
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-background-color: #f0f8ff;");
                }
            });
            row.setOnMouseExited(e -> row.setStyle(""));
            return row;
        });
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> refresh());
        
        Button exportBtn = new Button("📊 Export Status");
        exportBtn.setOnAction(e -> exportFleetStatus());
        
        CheckBox autoRefreshCheck = new CheckBox("Auto-refresh (30s)");
        autoRefreshCheck.setSelected(true);
        // TODO: Implement auto-refresh toggle
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label updateLabel = new Label("Last updated: " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        updateLabel.setStyle("-fx-font-style: italic;");
        
        toolbar.getChildren().addAll(refreshBtn, exportBtn, autoRefreshCheck, spacer, updateLabel);
        
        return toolbar;
    }
    
    private void refresh() {
        logger.info("Refreshing fleet status");
        controller.refreshAll();
        
        drivers.clear();
        drivers.addAll(controller.getDriverStatuses());
        
        updateStatusCounts();
        
        // Update header
        VBox header = (VBox) getTop();
        GridPane summaryGrid = (GridPane) header.getChildren().get(1);
        summaryGrid.getChildren().clear();
        
        int col = 0;
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            VBox statusBox = createStatusBox(status, statusCounts.getOrDefault(status, 0));
            summaryGrid.add(statusBox, col++, 0);
        }
        
        VBox totalBox = createTotalBox();
        summaryGrid.add(totalBox, col, 0);
        
        // Update timestamp
        HBox toolbar = (HBox) getBottom();
        Label updateLabel = (Label) toolbar.getChildren().get(toolbar.getChildren().size() - 1);
        updateLabel.setText("Last updated: " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
    
    private void updateStatusCounts() {
        statusCounts.clear();
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            statusCounts.merge(driver.getStatus(), 1, Integer::sum);
        }
    }
    
    private void showDriverDetails(DispatcherDriverStatus driver) {
        logger.info("Showing details for driver: {}", driver.getDriverName());
        new DriverDetailsDialog(driver).showAndWait();
    }
    
    private void updateDriverStatus(DispatcherDriverStatus driver) {
        logger.info("Updating status for driver: {}", driver.getDriverName());
        new UpdateDriverStatusDialog(driver, controller).showAndWait().ifPresent(updated -> {
            if (updated) {
                refresh();
            }
        });
    }
    
    private void exportFleetStatus() {
        logger.info("Exporting fleet status");
        // TODO: Implement fleet status export
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Fleet Status");
        alert.setHeaderText(null);
        alert.setContentText("Fleet status export will be implemented soon.");
        alert.showAndWait();
    }
}