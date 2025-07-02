package com.company.payroll.dispatcher;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import com.company.payroll.driver.DriverDetailsDialog;
import com.company.payroll.driver.DriverIncomeData;
import com.company.payroll.driver.DriverIncomeService;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.payroll.PayrollCalculator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.effect.DropShadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Fleet status overview with driver locations and availability - Modern UI
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
        getStyleClass().add("dispatcher-container");
        
        // Header with modern summary cards
        VBox header = createModernHeader();
        setTop(header);
        
        // Main content - driver table with modern styling
        setupModernDriverTable();
        
        // Wrap in styled container
        VBox tableContainer = new VBox(15);
        tableContainer.setPadding(new Insets(20));
        tableContainer.setStyle("-fx-background-color: #FAFAFA;");
        
        Label tableTitle = new Label("Fleet Overview");
        tableTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        driverTable.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        VBox.setVgrow(driverTable, Priority.ALWAYS);
        
        tableContainer.getChildren().addAll(tableTitle, driverTable);
        
        setCenter(tableContainer);
        
        // Bottom toolbar with modern controls
        HBox toolbar = createModernToolbar();
        setBottom(toolbar);
    }
    
    private VBox createModernHeader() {
        VBox header = new VBox(20);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #FFFFFF; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label titleLabel = new Label("Fleet Status Overview");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        // Status summary cards in a responsive grid
        FlowPane summaryGrid = createModernSummaryGrid();
        
        header.getChildren().addAll(titleLabel, summaryGrid);
        
        return header;
    }
    
    private FlowPane createModernSummaryGrid() {
        FlowPane grid = new FlowPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPrefWrapLength(1000);
        
        // Calculate status counts
        updateStatusCounts();
        
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            VBox statusCard = createModernStatusCard(status, statusCounts.getOrDefault(status, 0));
            grid.getChildren().add(statusCard);
        }
        
        // Total drivers card
        VBox totalCard = createTotalCard();
        grid.getChildren().add(totalCard);
        
        return grid;
    }
    
    private VBox createModernStatusCard(DispatcherDriverStatus.Status status, int count) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setPrefWidth(140);
        card.setPrefHeight(120);
        card.setStyle(String.format(
            "-fx-background-color: white; -fx-background-radius: 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 3); " +
            "-fx-border-color: %s; -fx-border-width: 0 0 3 0; -fx-border-radius: 0 0 12 12;",
            status.getColor()
        ));
        
        Circle indicator = new Circle(10);
        indicator.setFill(Color.web(status.getColor()));
        indicator.setEffect(new DropShadow(5, Color.web(status.getColor(), 0.3)));
        
        Label statusLabel = new Label(status.getDisplayName());
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #757575;");
        
        Label countLabel = new Label(String.valueOf(count));
        countLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        card.getChildren().addAll(indicator, countLabel, statusLabel);
        
        // Hover effect
        card.setOnMouseEntered(e -> {
            card.setScaleX(1.05);
            card.setScaleY(1.05);
            card.setStyle(card.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 12, 0, 0, 4);");
        });
        
        card.setOnMouseExited(e -> {
            card.setScaleX(1.0);
            card.setScaleY(1.0);
            card.setStyle(card.getStyle().replace(
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 12, 0, 0, 4);",
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 3);"
            ));
        });
        
        return card;
    }
    
    private VBox createTotalCard() {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setPrefWidth(160);
        card.setPrefHeight(120);
        card.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #2196F3, #1976D2); " +
            "-fx-background-radius: 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(33,150,243,0.3), 12, 0, 0, 4);"
        );
        
        Label iconLabel = new Label("🚚");
        iconLabel.setStyle("-fx-font-size: 24px;");
        
        int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
        Label countLabel = new Label(String.valueOf(total));
        countLabel.setStyle("-fx-font-size: 36px; -fx-font-weight: 700; -fx-text-fill: white;");
        
        Label titleLabel = new Label("Total Fleet");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: white;");
        
        card.getChildren().addAll(iconLabel, countLabel, titleLabel);
        
        // Hover effect
        card.setOnMouseEntered(e -> {
            card.setScaleX(1.05);
            card.setScaleY(1.05);
        });
        
        card.setOnMouseExited(e -> {
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        });
        
        return card;
    }
    
    private void setupModernDriverTable() {
        driverTable.setRowFactory(tv -> {
            TableRow<DispatcherDriverStatus> row = new TableRow<>();
            row.setStyle("-fx-background-color: white; -fx-border-width: 0;");
            
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-background-color: #F5F5F5; -fx-cursor: hand;");
                }
            });
            
            row.setOnMouseExited(e -> {
                row.setStyle("-fx-background-color: white;");
            });
            
            return row;
        });
        
        // Status column with modern indicator
        TableColumn<DispatcherDriverStatus, DispatcherDriverStatus.Status> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(DispatcherDriverStatus.Status status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.setPadding(new Insets(5));
                    
                    Circle indicator = new Circle(6);
                    indicator.setFill(Color.web(status.getColor()));
                    
                    Label label = new Label(status.getDisplayName());
                    label.setStyle("-fx-font-size: 12px; -fx-font-weight: 500;");
                    
                    box.getChildren().addAll(indicator, label);
                    setGraphic(box);
                }
            }
        });
        statusCol.setPrefWidth(130);
        
        // Driver name column
        TableColumn<DispatcherDriverStatus, String> nameCol = new TableColumn<>("Driver");
        nameCol.setCellValueFactory(data -> data.getValue().driverNameProperty());
        nameCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                } else {
                    setText(name);
                    setStyle("-fx-font-weight: 600; -fx-text-fill: #212121;");
                }
            }
        });
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
        locationCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String location, boolean empty) {
                super.updateItem(location, empty);
                if (empty || location == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(5);
                    box.setAlignment(Pos.CENTER_LEFT);
                    
                    Label pin = new Label("📍");
                    pin.setStyle("-fx-font-size: 12px;");
                    
                    Label text = new Label(location);
                    text.setStyle("-fx-font-size: 12px;");
                    
                    box.getChildren().addAll(pin, text);
                    setGraphic(box);
                }
            }
        });
        locationCol.setPrefWidth(200);
        
        // Current/Next load column
        TableColumn<DispatcherDriverStatus, String> loadCol = new TableColumn<>("Current/Next Load");
        loadCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            if (driver.getCurrentLoad() != null) {
                return new SimpleStringProperty("📦 " + driver.getCurrentLoad().getLoadNumber() + 
                    " (" + driver.getCurrentLoad().getCustomer() + ")");
            } else if (driver.getNextLoad() != null) {
                return new SimpleStringProperty("⏭ " + driver.getNextLoad().getLoadNumber() + 
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
                return new SimpleStringProperty("🕐 " + driver.getEstimatedAvailableTime().format(TIME_FORMAT));
            } else if (driver.isAvailable()) {
                return new SimpleStringProperty("✅ Available Now");
            } else {
                return new SimpleStringProperty("N/A");
            }
        });
        etaCol.setPrefWidth(140);
        
        // Hours worked column with progress indicator
        TableColumn<DispatcherDriverStatus, String> hoursCol = new TableColumn<>("Hours Today/Week");
        hoursCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            return new SimpleStringProperty(String.format("%.1f / %.1f", 
                driver.getHoursWorkedToday(), driver.getHoursWorkedWeek()));
        });
        hoursCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    DispatcherDriverStatus driver = getTableRow().getItem();
                    if (driver != null) {
                        VBox box = new VBox(3);
                        box.setAlignment(Pos.CENTER_LEFT);
                        
                        Label text = new Label(item);
                        text.setStyle("-fx-font-size: 12px;");
                        
                        ProgressBar dailyProgress = new ProgressBar(driver.getHoursWorkedToday() / 11.0);
                        dailyProgress.setPrefWidth(100);
                        dailyProgress.setPrefHeight(6);
                        dailyProgress.setStyle("-fx-accent: #4CAF50;");
                        
                        box.getChildren().addAll(text, dailyProgress);
                        setGraphic(box);
                    }
                }
            }
        });
        hoursCol.setPrefWidth(140);
        
        // Action column
        TableColumn<DispatcherDriverStatus, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button updateBtn = createActionButton("Update", "#2196F3");
            private final Button detailsBtn = createActionButton("Details", "#4CAF50");

            {
                HBox buttons = new HBox(5);
                buttons.setAlignment(Pos.CENTER);
                buttons.getChildren().addAll(updateBtn, detailsBtn);
                setGraphic(buttons);
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    DispatcherDriverStatus driver = getTableRow().getItem();
                    if (driver != null) {
                        updateBtn.setOnAction(e -> updateDriverStatus(driver));
                        detailsBtn.setOnAction(e -> showDriverDetails(driver));
                        
                        HBox buttons = new HBox(5);
                        buttons.setAlignment(Pos.CENTER);
                        buttons.getChildren().addAll(updateBtn, detailsBtn);
                        setGraphic(buttons);
                    }
                }
            }
        });
        actionCol.setPrefWidth(180);
        
        driverTable.getColumns().setAll(java.util.List.of(
                statusCol, nameCol, truckCol, trailerCol,
                locationCol, loadCol, etaCol, hoursCol, actionCol));
        driverTable.setItems(drivers);
    }
    
    private Button createActionButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: white; -fx-text-fill: %s; -fx-font-size: 11px; " +
            "-fx-font-weight: 600; -fx-padding: 5px 12px; -fx-background-radius: 4px; " +
            "-fx-border-color: %s; -fx-border-width: 1px; -fx-cursor: hand;",
            color, color
        ));
        
        button.setOnMouseEntered(e -> {
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 11px; " +
                "-fx-font-weight: 600; -fx-padding: 5px 12px; -fx-background-radius: 4px; " +
                "-fx-border-width: 0; -fx-cursor: hand;",
                color
            ));
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(String.format(
                "-fx-background-color: white; -fx-text-fill: %s; -fx-font-size: 11px; " +
                "-fx-font-weight: 600; -fx-padding: 5px 12px; -fx-background-radius: 4px; " +
                "-fx-border-color: %s; -fx-border-width: 1px; -fx-cursor: hand;",
                color, color
            ));
        });
        
        return button;
    }
    
    private HBox createModernToolbar() {
        HBox toolbar = new HBox(15);
        toolbar.setPadding(new Insets(15));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #FFFFFF; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, -2);");
        
        Button refreshBtn = createToolbarButton("🔄 Refresh", "#4CAF50", true);
        refreshBtn.setOnAction(e -> refresh());
        
        Button exportBtn = createToolbarButton("📊 Export Status", "#2196F3", false);
        exportBtn.setOnAction(e -> exportFleetStatus());
        
        CheckBox autoRefreshCheck = new CheckBox("Auto-refresh (30s)");
        autoRefreshCheck.setSelected(true);
        autoRefreshCheck.setStyle("-fx-font-size: 13px;");
        // TODO: Implement auto-refresh toggle
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label updateLabel = new Label("Last updated: " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        updateLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #757575;");
        
        toolbar.getChildren().addAll(refreshBtn, exportBtn, autoRefreshCheck, spacer, updateLabel);
        
        return toolbar;
    }
    
    private Button createToolbarButton(String text, String color, boolean isPrimary) {
        Button button = new Button(text);
        if (isPrimary) {
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: 600; " +
                "-fx-background-radius: 5px; -fx-padding: 8px 16px; -fx-cursor: hand;",
                color
            ));
        } else {
            button.setStyle(String.format(
                "-fx-background-color: white; -fx-text-fill: %s; -fx-font-weight: 600; " +
                "-fx-background-radius: 5px; -fx-padding: 8px 16px; -fx-cursor: hand; " +
                "-fx-border-color: %s; -fx-border-width: 1px;",
                color, color
            ));
        }
        
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);");
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);", ""));
        });
        
        return button;
    }
    
    private void refresh() {
        logger.info("Refreshing fleet status");
        controller.refreshAll();
        
        drivers.clear();
        drivers.addAll(controller.getDriverStatuses());
        
        updateStatusCounts();
        
        // Update header
        VBox header = (VBox) getTop();
        FlowPane summaryGrid = (FlowPane) header.getChildren().get(1);
        summaryGrid.getChildren().clear();
        
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            VBox statusCard = createModernStatusCard(status, statusCounts.getOrDefault(status, 0));
            summaryGrid.getChildren().add(statusCard);
        }
        
        VBox totalCard = createTotalCard();
        summaryGrid.getChildren().add(totalCard);
        
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

        // Build the services needed to fetch income data on demand
        EmployeeDAO employeeDAO = new EmployeeDAO();
        LoadDAO loadDAO = new LoadDAO();
        FuelTransactionDAO fuelDAO = new FuelTransactionDAO();
        PayrollCalculator payrollCalculator = new PayrollCalculator(employeeDAO, loadDAO, fuelDAO);
        DriverIncomeService incomeService = new DriverIncomeService(employeeDAO, loadDAO, fuelDAO, payrollCalculator);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        try {
            DriverIncomeData data = incomeService
                .getDriverIncomeData(driver.getDriver(), startDate, endDate)
                .get();
            new DriverDetailsDialog(data).showAndWait();
        } catch (Exception e) {
            logger.error("Failed to load driver details", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to load driver details");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
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