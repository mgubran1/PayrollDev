package com.company.payroll.dispatcher;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import com.company.payroll.driver.DriverDetailsDialog;
import com.company.payroll.driver.DriverIncomeData;
import com.company.payroll.driver.DriverIncomeService;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.payroll.PayrollCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Enhanced Fleet status overview with real-time tracking, advanced filtering,
 * and comprehensive driver management capabilities
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherFleetStatusView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherFleetStatusView.class);
    
    // Date/Time Formatters
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter FULL_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss");
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    
    // UI Constants
    private static final int AUTO_REFRESH_INTERVAL = 30; // seconds
    private static final double STATUS_BOX_WIDTH = 140;
    private static final double STATUS_BOX_HEIGHT = 90;
    private static final int ANIMATION_DURATION = 300; // milliseconds
    
    // Core Components
    private final DispatcherController controller;
    private final TableView<DispatcherDriverStatus> driverTable;
    private final ObservableList<DispatcherDriverStatus> drivers;
    private final FilteredList<DispatcherDriverStatus> filteredDrivers;
    private final Map<DispatcherDriverStatus.Status, Integer> statusCounts;
    private final Map<DispatcherDriverStatus.Status, VBox> statusBoxes;
    
    // UI Components
    private final TextField searchField;
    private final ComboBox<DispatcherDriverStatus.Status> statusFilterCombo;
    private final CheckBox autoRefreshCheck;
    private final Label lastUpdateLabel;
    private final ProgressBar refreshProgress;
    private final GridPane summaryGrid;
    private VBox totalBox;
    
    // State Management
    private Timeline autoRefreshTimeline;
    private final AtomicBoolean isRefreshing;
    private LocalDateTime lastRefreshTime;
    private final Map<String, LocalDateTime> driverLastUpdateMap;
    
    // Statistics
    private final Map<String, Double> performanceMetrics;
    
    public DispatcherFleetStatusView(DispatcherController controller) {
        this.controller = controller;
        this.driverTable = new TableView<>();
        this.drivers = FXCollections.observableArrayList();
        this.filteredDrivers = new FilteredList<>(drivers, p -> true);
        this.statusCounts = new ConcurrentHashMap<>();
        this.statusBoxes = new HashMap<>();
        this.searchField = new TextField();
        this.statusFilterCombo = new ComboBox<>();
        this.autoRefreshCheck = new CheckBox("Auto-refresh");
        this.lastUpdateLabel = new Label();
        this.refreshProgress = new ProgressBar();
        this.summaryGrid = new GridPane();
        this.totalBox = new VBox();
        this.isRefreshing = new AtomicBoolean(false);
        this.driverLastUpdateMap = new ConcurrentHashMap<>();
        this.performanceMetrics = new ConcurrentHashMap<>();
        
        initializeUI();
        setupEventHandlers();
        startAutoRefresh();
        refresh();
    }
    
    private void initializeUI() {
        // Apply professional styling
        getStylesheets().add(getClass().getResource("/styles/fleet-status.css").toExternalForm());
        getStyleClass().add("fleet-status-view");
        
        // Header with enhanced summary
        VBox header = createEnhancedHeader();
        setTop(header);
        
        // Main content with filtered table
        VBox mainContent = createMainContent();
        setCenter(mainContent);
        
        // Bottom toolbar with controls
        HBox toolbar = createEnhancedToolbar();
        setBottom(toolbar);
        
        // Configure refresh progress
        refreshProgress.setVisible(false);
        refreshProgress.setPrefWidth(150);
    }
    
    private VBox createEnhancedHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.getStyleClass().add("fleet-header");
        
        // Title section
        HBox titleSection = new HBox(20);
        titleSection.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Fleet Status Overview");
        titleLabel.getStyleClass().add("fleet-title");
        
        Label subtitleLabel = new Label("Real-time driver and vehicle tracking");
        subtitleLabel.getStyleClass().add("fleet-subtitle");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Quick stats
        VBox quickStats = createQuickStats();
        
        titleSection.getChildren().addAll(
            new VBox(5, titleLabel, subtitleLabel),
            spacer,
            quickStats
        );
        
        // Status summary grid
        summaryGrid.setHgap(20);
        summaryGrid.setVgap(15);
        summaryGrid.setPadding(new Insets(20, 0, 0, 0));
        summaryGrid.getStyleClass().add("summary-grid");
        
        buildStatusSummary();
        
        header.getChildren().addAll(titleSection, summaryGrid);
        
        return header;
    }
    
    private VBox createQuickStats() {
        VBox stats = new VBox(5);
        stats.setAlignment(Pos.CENTER_RIGHT);
        stats.getStyleClass().add("quick-stats");
        
        // Calculate quick metrics
        int activeLoads = calculateActiveLoads();
        double utilizationRate = calculateUtilizationRate();
        
        Label activeLoadsLabel = new Label(String.format("Active Loads: %d", activeLoads));
        activeLoadsLabel.getStyleClass().add("stat-label");
        
        Label utilizationLabel = new Label(String.format("Fleet Utilization: %.1f%%", utilizationRate));
        utilizationLabel.getStyleClass().add("stat-label");
        
        stats.getChildren().addAll(activeLoadsLabel, utilizationLabel);
        
        return stats;
    }
    
    private void buildStatusSummary() {
        summaryGrid.getChildren().clear();
        statusBoxes.clear();
        
        // Calculate status counts
        updateStatusCounts();
        
        int col = 0;
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            VBox statusBox = createEnhancedStatusBox(status, statusCounts.getOrDefault(status, 0));
            statusBoxes.put(status, statusBox);
            summaryGrid.add(statusBox, col++, 0);
        }
        
        // Total drivers box
        totalBox = createEnhancedTotalBox();
        summaryGrid.add(totalBox, col, 0);
    }
    
    private VBox createEnhancedStatusBox(DispatcherDriverStatus.Status status, int count) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(15));
        box.setPrefSize(STATUS_BOX_WIDTH, STATUS_BOX_HEIGHT);
        box.getStyleClass().addAll("status-box", "status-" + status.name().toLowerCase());
        
        // Status indicator with animation
        Circle indicator = new Circle(10);
        indicator.setFill(Color.web(status.getColor()));
        indicator.setEffect(new DropShadow(5, Color.gray(0.3)));
        
        // Pulse animation for active statuses
        if (status == DispatcherDriverStatus.Status.ON_ROAD || 
            status == DispatcherDriverStatus.Status.LOADING) {
            Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO, e -> indicator.setRadius(10)),
                new KeyFrame(Duration.millis(500), e -> indicator.setRadius(12)),
                new KeyFrame(Duration.millis(1000), e -> indicator.setRadius(10))
            );
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
        }
        
        Label statusLabel = new Label(status.getDisplayName());
        statusLabel.getStyleClass().add("status-name");
        
        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("status-count");
        
        // Percentage of total
        int total = drivers.size();
        double percentage = total > 0 ? (count * 100.0 / total) : 0;
        Label percentLabel = new Label(String.format("%.1f%%", percentage));
        percentLabel.getStyleClass().add("status-percent");
        
        box.getChildren().addAll(indicator, statusLabel, countLabel, percentLabel);
        
        // Click handler to filter by status
        box.setCursor(Cursor.HAND);
        box.setOnMouseClicked(e -> {
            statusFilterCombo.setValue(status);
            applyFilters();
        });
        
        // Hover effect
        box.setOnMouseEntered(e -> box.setStyle("-fx-scale-x: 1.05; -fx-scale-y: 1.05;"));
        box.setOnMouseExited(e -> box.setStyle("-fx-scale-x: 1.0; -fx-scale-y: 1.0;"));
        
        return box;
    }
    
    private VBox createEnhancedTotalBox() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(15));
        box.setPrefSize(STATUS_BOX_WIDTH, STATUS_BOX_HEIGHT);
        box.getStyleClass().add("total-box");
        
        Label titleLabel = new Label("Total Fleet");
        titleLabel.getStyleClass().add("total-title");
        
        int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
        Label countLabel = new Label(String.valueOf(total));
        countLabel.getStyleClass().add("total-count");
        
        // Mini chart showing distribution
        HBox miniChart = createMiniDistributionChart();
        
        box.getChildren().addAll(titleLabel, countLabel, miniChart);
        
        return box;
    }
    
    private HBox createMiniDistributionChart() {
        HBox chart = new HBox(2);
        chart.setAlignment(Pos.CENTER);
        chart.setPrefHeight(10);
        
        int total = drivers.size();
        if (total > 0) {
            for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
                int count = statusCounts.getOrDefault(status, 0);
                if (count > 0) {
                    double width = (count * 100.0 / total);
                    Region bar = new Region();
                    bar.setPrefHeight(10);
                    bar.setPrefWidth(width);
                    bar.setStyle("-fx-background-color: " + status.getColor() + ";");
                    chart.getChildren().add(bar);
                }
            }
        }
        
        return chart;
    }
    
    private VBox createMainContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(0, 20, 20, 20));
        
        // Filter bar
        HBox filterBar = createFilterBar();
        
        // Enhanced driver table
        setupEnhancedDriverTable();
        
        // Wrap table in scroll pane
        ScrollPane scrollPane = new ScrollPane(driverTable);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("fleet-table-scroll");
        
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        content.getChildren().addAll(filterBar, scrollPane);
        
        return content;
    }
    
    private HBox createFilterBar() {
        HBox filterBar = new HBox(15);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(10));
        filterBar.getStyleClass().add("filter-bar");
        
        // Search field
        searchField.setPromptText("Search drivers, trucks, locations...");
        searchField.setPrefWidth(300);
        searchField.getStyleClass().add("search-field");
        
        // Status filter
        statusFilterCombo.setPromptText("All Statuses");
        statusFilterCombo.getItems().add(null); // All option
        statusFilterCombo.getItems().addAll(DispatcherDriverStatus.Status.values());
        statusFilterCombo.setPrefWidth(150);
        
        // Additional filters
        ComboBox<String> loadFilter = new ComboBox<>();
        loadFilter.setPromptText("Load Status");
        loadFilter.getItems().addAll("All", "With Loads", "Without Loads", "Multiple Loads");
        loadFilter.setValue("All");
        loadFilter.setPrefWidth(150);
        
        CheckBox availableOnly = new CheckBox("Available Only");
        
        // Clear filters button
        Button clearBtn = new Button("Clear Filters");
        clearBtn.getStyleClass().add("clear-button");
        clearBtn.setOnAction(e -> clearFilters());
        
        filterBar.getChildren().addAll(
            new Label("Filter:"),
            searchField,
            statusFilterCombo,
            loadFilter,
            availableOnly,
            clearBtn
        );
        
        // Setup filter handlers
        searchField.textProperty().addListener((obs, old, text) -> applyFilters());
        statusFilterCombo.setOnAction(e -> applyFilters());
        loadFilter.setOnAction(e -> applyFilters());
        availableOnly.setOnAction(e -> applyFilters());
        
        return filterBar;
    }
    
    private void setupEnhancedDriverTable() {
        // Configure sorted list
        SortedList<DispatcherDriverStatus> sortedList = new SortedList<>(filteredDrivers);
        sortedList.comparatorProperty().bind(driverTable.comparatorProperty());
        driverTable.setItems(sortedList);
        
        // Clear existing columns
        driverTable.getColumns().clear();
        
        // Status column with enhanced visualization
        TableColumn<DispatcherDriverStatus, DispatcherDriverStatus.Status> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setCellFactory(column -> new EnhancedStatusCell());
        statusCol.setPrefWidth(130);
        
        // Driver info column
        TableColumn<DispatcherDriverStatus, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(data -> data.getValue().driverNameProperty());
        driverCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setGraphic(null);
                } else {
                    DispatcherDriverStatus driver = getTableRow().getItem();
                    if (driver != null) {
                        VBox driverInfo = new VBox(2);
                        Label nameLabel = new Label(name);
                        nameLabel.setStyle("-fx-font-weight: bold;");
                        
                        Label typeLabel = new Label(driver.getDriver().getDriverType());
                        typeLabel.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
                        
                        driverInfo.getChildren().addAll(nameLabel, typeLabel);
                        setGraphic(driverInfo);
                    }
                }
            }
        });
        driverCol.setPrefWidth(160);
        
        // Vehicle column
        TableColumn<DispatcherDriverStatus, String> vehicleCol = new TableColumn<>("Vehicle");
        vehicleCol.setCellValueFactory(data -> {
            String truck = data.getValue().getTruckUnit();
            String trailer = data.getValue().getTrailerNumber();
            return new SimpleStringProperty(truck + (trailer != null ? " / " + trailer : ""));
        });
        vehicleCol.setPrefWidth(140);
        
        // Location column with map link
        TableColumn<DispatcherDriverStatus, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(data -> data.getValue().currentLocationProperty());
        locationCol.setCellFactory(column -> new LocationCell());
        locationCol.setPrefWidth(220);
        
        // Load info column
        TableColumn<DispatcherDriverStatus, String> loadCol = new TableColumn<>("Current/Next Load");
        loadCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            if (driver.getCurrentLoad() != null) {
                Load load = driver.getCurrentLoad();
                return new SimpleStringProperty(String.format("▶ %s (%s)", 
                    load.getLoadNumber(), load.getCustomer()));
            } else if (driver.getNextLoad() != null) {
                Load load = driver.getNextLoad();
                return new SimpleStringProperty(String.format("⏭ %s (%s)", 
                    load.getLoadNumber(), load.getCustomer()));
            } else {
                return new SimpleStringProperty("No assigned loads");
            }
        });
        loadCol.setPrefWidth(200);
        
        // ETA/Availability column
        TableColumn<DispatcherDriverStatus, String> etaCol = new TableColumn<>("ETA/Available");
        etaCol.setCellValueFactory(data -> {
            DispatcherDriverStatus driver = data.getValue();
            if (driver.getEstimatedAvailableTime() != null) {
                LocalDateTime eta = driver.getEstimatedAvailableTime();
                String timeStr = eta.format(TIME_FORMAT);
                
                // Add time until available
                long hoursUntil = LocalDateTime.now().until(eta, java.time.temporal.ChronoUnit.HOURS);
                if (hoursUntil > 0) {
                    return new SimpleStringProperty(String.format("%s (%dh)", timeStr, hoursUntil));
                } else {
                    return new SimpleStringProperty(timeStr);
                }
            } else if (driver.isAvailable()) {
                return new SimpleStringProperty("✓ Available Now");
            } else {
                return new SimpleStringProperty("N/A");
            }
        });
        etaCol.setPrefWidth(140);
        
        // Hours worked column with progress
        TableColumn<DispatcherDriverStatus, String> hoursCol = new TableColumn<>("Hours");
        hoursCol.setCellFactory(column -> new HoursProgressCell());
        hoursCol.setPrefWidth(120);
        
        // Performance column
        TableColumn<DispatcherDriverStatus, Double> performanceCol = new TableColumn<>("Performance");
        performanceCol.setCellValueFactory(data -> 
            new SimpleObjectProperty<>(calculateDriverPerformance(data.getValue())));
        performanceCol.setCellFactory(column -> new PerformanceCell());
        performanceCol.setPrefWidth(100);
        
        // Actions column
        TableColumn<DispatcherDriverStatus, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(column -> new ActionButtonCell());
        actionCol.setPrefWidth(180);
        
        // Add all columns
        driverTable.getColumns().addAll(
            statusCol, driverCol, vehicleCol, locationCol,
            loadCol, etaCol, hoursCol, performanceCol, actionCol
        );
        
        // Configure table properties
        driverTable.setRowFactory(tv -> createEnhancedTableRow());
        driverTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Add context menu
        driverTable.setContextMenu(createTableContextMenu());
    }
    
    // Custom cell implementations
    private class EnhancedStatusCell extends TableCell<DispatcherDriverStatus, DispatcherDriverStatus.Status> {
        @Override
        protected void updateItem(DispatcherDriverStatus.Status status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
            } else {
                HBox box = new HBox(8);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setPadding(new Insets(3));
                
                Circle indicator = new Circle(6);
                indicator.setFill(Color.web(status.getColor()));
                
                Label label = new Label(status.getDisplayName());
                label.setStyle("-fx-font-size: 11;");
                
                // Add time in status if available
                DispatcherDriverStatus driver = getTableRow().getItem();
                if (driver != null) {
                    LocalDateTime lastUpdate = driverLastUpdateMap.get(driver.getDriverName());
                    if (lastUpdate != null) {
                        long minutesInStatus = lastUpdate.until(LocalDateTime.now(), 
                            java.time.temporal.ChronoUnit.MINUTES);
                        if (minutesInStatus > 0) {
                            Label timeLabel = new Label(String.format("(%dm)", minutesInStatus));
                            timeLabel.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
                            box.getChildren().addAll(indicator, label, timeLabel);
                        } else {
                            box.getChildren().addAll(indicator, label);
                        }
                    } else {
                        box.getChildren().addAll(indicator, label);
                    }
                } else {
                    box.getChildren().addAll(indicator, label);
                }
                
                setGraphic(box);
            }
        }
    }
    
    // Continued in next message...
	    // ... continuing from previous section ...
    
    private class LocationCell extends TableCell<DispatcherDriverStatus, String> {
        @Override
        protected void updateItem(String location, boolean empty) {
            super.updateItem(location, empty);
            if (empty || location == null) {
                setGraphic(null);
            } else {
                HBox box = new HBox(5);
                box.setAlignment(Pos.CENTER_LEFT);
                
                Label locationLabel = new Label(location);
                locationLabel.setStyle("-fx-font-size: 11;");
                
                // Add map icon if location is GPS-enabled
                if (isGPSEnabled(location)) {
                    Label mapIcon = new Label("📍");
                    mapIcon.setCursor(Cursor.HAND);
                    mapIcon.setOnMouseClicked(e -> openMapLocation(location));
                    Tooltip.install(mapIcon, new Tooltip("View on map"));
                    box.getChildren().addAll(mapIcon, locationLabel);
                } else {
                    box.getChildren().add(locationLabel);
                }
                
                // Add last update time
                DispatcherDriverStatus driver = getTableRow().getItem();
                if (driver != null) {
                    LocalDateTime lastUpdate = driverLastUpdateMap.get(driver.getDriverName());
                    if (lastUpdate != null) {
                        Label updateLabel = new Label(
                            " (" + getRelativeTime(lastUpdate) + ")"
                        );
                        updateLabel.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
                        box.getChildren().add(updateLabel);
                    }
                }
                
                setGraphic(box);
            }
        }
    }
        private boolean isGPSEnabled(String loc) {
            return loc != null && loc.matches(".*\\d+\\.\\d+.*");
        }

        private void openMapLocation(String loc) {
            // Placeholder for future map integration
        }
    
    private class HoursProgressCell extends TableCell<DispatcherDriverStatus, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
            } else {
                DispatcherDriverStatus driver = getTableRow().getItem();
                if (driver != null) {
                    VBox box = new VBox(3);
                    box.setAlignment(Pos.CENTER_LEFT);
                    
                    // Hours text
                    Label hoursLabel = new Label(String.format("%.1f / %.1f hrs", 
                        driver.getHoursWorkedToday(), driver.getHoursWorkedWeek()));
                    hoursLabel.setStyle("-fx-font-size: 11;");
                    
                    // Progress bar for daily hours
                    ProgressBar dailyProgress = new ProgressBar();
                    dailyProgress.setPrefWidth(100);
                    dailyProgress.setPrefHeight(8);
                    double dailyPercent = driver.getHoursWorkedToday() / 14.0; // 14 hour max
                    dailyProgress.setProgress(Math.min(dailyPercent, 1.0));
                    
                    // Color code based on hours
                    if (dailyPercent > 0.9) {
                        dailyProgress.setStyle("-fx-accent: #dc3545;"); // Red
                    } else if (dailyPercent > 0.75) {
                        dailyProgress.setStyle("-fx-accent: #ffc107;"); // Yellow
                    } else {
                        dailyProgress.setStyle("-fx-accent: #28a745;"); // Green
                    }
                    
                    box.getChildren().addAll(hoursLabel, dailyProgress);
                    setGraphic(box);
                }
            }
        }
    }
    
    private class PerformanceCell extends TableCell<DispatcherDriverStatus, Double> {
        @Override
        protected void updateItem(Double performance, boolean empty) {
            super.updateItem(performance, empty);
            if (empty || performance == null) {
                setGraphic(null);
            } else {
                HBox box = new HBox(5);
                box.setAlignment(Pos.CENTER);
                
                // Performance stars
                int stars = (int) Math.round(performance * 5);
                for (int i = 0; i < 5; i++) {
                    Label star = new Label(i < stars ? "★" : "☆");
                    star.setStyle(i < stars ? 
                        "-fx-text-fill: gold; -fx-font-size: 14;" : 
                        "-fx-text-fill: lightgray; -fx-font-size: 14;");
                    box.getChildren().add(star);
                }
                
                // Percentage
                Label percentLabel = new Label(String.format("%.0f%%", performance * 100));
                percentLabel.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
                box.getChildren().add(percentLabel);
                
                setGraphic(box);
            }
        }
    }
    
    private class ActionButtonCell extends TableCell<DispatcherDriverStatus, Void> {
        private final Button updateBtn = new Button("Update");
        private final Button detailsBtn = new Button("Details");
        private final Button assignBtn = new Button("Assign");
        
        public ActionButtonCell() {
            updateBtn.getStyleClass().add("action-button");
            detailsBtn.getStyleClass().add("action-button");
            assignBtn.getStyleClass().add("action-button");
            
            updateBtn.setPrefWidth(60);
            detailsBtn.setPrefWidth(60);
            assignBtn.setPrefWidth(55);
        }
        
        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
            } else {
                DispatcherDriverStatus driver = getTableRow().getItem();
                if (driver != null) {
                    HBox buttons = new HBox(3);
                    buttons.setAlignment(Pos.CENTER);
                    
                    updateBtn.setOnAction(e -> updateDriverStatus(driver));
                    detailsBtn.setOnAction(e -> showDriverDetails(driver));
                    assignBtn.setOnAction(e -> assignLoadToDriver(driver));
                    
                    // Show assign button only if driver is available
                    if (driver.isAvailable()) {
                        buttons.getChildren().addAll(updateBtn, detailsBtn, assignBtn);
                    } else {
                        buttons.getChildren().addAll(updateBtn, detailsBtn);
                    }
                    
                    setGraphic(buttons);
                }
            }
        }
    }
    
    private TableRow<DispatcherDriverStatus> createEnhancedTableRow() {
        TableRow<DispatcherDriverStatus> row = new TableRow<>();
        
        // Row styling based on status
        row.itemProperty().addListener((obs, oldDriver, newDriver) -> {
            if (newDriver != null) {
                row.getStyleClass().removeAll("available-row", "busy-row", "off-duty-row");
                
                switch (newDriver.getStatus()) {
                    case AVAILABLE:
                        row.getStyleClass().add("available-row");
                        break;
                    case ON_ROAD:
                    case LOADING:
                        row.getStyleClass().add("busy-row");
                        break;
                    case OFF_DUTY:
                        row.getStyleClass().add("off-duty-row");
                        break;
                }
            }
        });
        
        // Double-click handler
        row.setOnMouseClicked(event -> {
            if (!row.isEmpty() && event.getClickCount() == 2) {
                showDriverDetails(row.getItem());
            }
        });
        
        // Context menu
        row.setContextMenu(createRowContextMenu());
        
        return row;
    }
    
    private ContextMenu createTableContextMenu() {
        ContextMenu menu = new ContextMenu();
        
        MenuItem refreshItem = new MenuItem("Refresh Selected");
        refreshItem.setOnAction(e -> refreshSelectedDrivers());
        
        MenuItem exportItem = new MenuItem("Export Selected");
        exportItem.setOnAction(e -> exportSelectedDrivers());
        
        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setOnAction(e -> driverTable.getSelectionModel().selectAll());
        
        MenuItem clearSelectionItem = new MenuItem("Clear Selection");
        clearSelectionItem.setOnAction(e -> driverTable.getSelectionModel().clearSelection());
        
        menu.getItems().addAll(
            refreshItem, exportItem,
            new SeparatorMenuItem(),
            selectAllItem, clearSelectionItem
        );
        
        return menu;
    }
    
    private ContextMenu createRowContextMenu() {
        ContextMenu menu = new ContextMenu();
        
        MenuItem updateStatusItem = new MenuItem("Update Status");
        updateStatusItem.setOnAction(e -> {
            DispatcherDriverStatus driver = driverTable.getSelectionModel().getSelectedItem();
            if (driver != null) updateDriverStatus(driver);
        });
        
        MenuItem viewDetailsItem = new MenuItem("View Details");
        viewDetailsItem.setOnAction(e -> {
            DispatcherDriverStatus driver = driverTable.getSelectionModel().getSelectedItem();
            if (driver != null) showDriverDetails(driver);
        });
        
        MenuItem assignLoadItem = new MenuItem("Assign Load");
        assignLoadItem.setOnAction(e -> {
            DispatcherDriverStatus driver = driverTable.getSelectionModel().getSelectedItem();
            if (driver != null) assignLoadToDriver(driver);
        });
        
        MenuItem viewHistoryItem = new MenuItem("View History");
        viewHistoryItem.setOnAction(e -> {
            DispatcherDriverStatus driver = driverTable.getSelectionModel().getSelectedItem();
            if (driver != null) showDriverHistory(driver);
        });
        
        MenuItem contactDriverItem = new MenuItem("Contact Driver");
        contactDriverItem.setOnAction(e -> {
            DispatcherDriverStatus driver = driverTable.getSelectionModel().getSelectedItem();
            if (driver != null) contactDriver(driver);
        });
        
        menu.getItems().addAll(
            updateStatusItem, viewDetailsItem, assignLoadItem,
            new SeparatorMenuItem(),
            viewHistoryItem, contactDriverItem
        );
        
        return menu;
    }
    
    private HBox createEnhancedToolbar() {
        HBox toolbar = new HBox(15);
        toolbar.setPadding(new Insets(10, 20, 10, 20));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("fleet-toolbar");
        
        // Refresh controls
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("primary-button");
        refreshBtn.setOnAction(e -> refresh());
        
        autoRefreshCheck.setSelected(true);
        autoRefreshCheck.getStyleClass().add("auto-refresh-check");
        
        // Export options
        MenuButton exportBtn = new MenuButton("📊 Export");
        exportBtn.getStyleClass().add("secondary-button");
        
        MenuItem exportCSVItem = new MenuItem("Export to CSV");
        exportCSVItem.setOnAction(e -> exportToCSV());
        
        MenuItem exportPDFItem = new MenuItem("Export to PDF");
        exportPDFItem.setOnAction(e -> exportToPDF());
        
        MenuItem exportEmailItem = new MenuItem("Email Report");
        exportEmailItem.setOnAction(e -> emailFleetReport());
        
        exportBtn.getItems().addAll(exportCSVItem, exportPDFItem, exportEmailItem);
        
        // View options
        MenuButton viewBtn = new MenuButton("👁 View");
        viewBtn.getStyleClass().add("secondary-button");
        
        CheckMenuItem compactViewItem = new CheckMenuItem("Compact View");
        compactViewItem.setOnAction(e -> toggleCompactView(compactViewItem.isSelected()));
        
        CheckMenuItem showOfflineItem = new CheckMenuItem("Show Offline Drivers");
        showOfflineItem.setSelected(true);
        showOfflineItem.setOnAction(e -> applyFilters());
        
        MenuItem columnsItem = new MenuItem("Configure Columns...");
        columnsItem.setOnAction(e -> configureColumns());
        
        viewBtn.getItems().addAll(compactViewItem, showOfflineItem, 
            new SeparatorMenuItem(), columnsItem);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Status indicators
        HBox statusIndicators = new HBox(10);
        statusIndicators.setAlignment(Pos.CENTER_RIGHT);
        
        // Connection status
        Circle connectionStatus = new Circle(5);
        connectionStatus.setFill(Color.LIGHTGREEN);
        Label connectionLabel = new Label("Connected");
        connectionLabel.setStyle("-fx-font-size: 11;");
        
        // Last update time
        updateLastRefreshTime();
        
        statusIndicators.getChildren().addAll(
            connectionStatus, connectionLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            lastUpdateLabel,
            refreshProgress
        );
        
        toolbar.getChildren().addAll(
            refreshBtn, autoRefreshCheck,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            exportBtn, viewBtn,
            spacer,
            statusIndicators
        );
        
        return toolbar;
    }
    
    private void setupEventHandlers() {
        // Auto-refresh toggle
        autoRefreshCheck.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
    }
    
    private void startAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        
        autoRefreshTimeline = new Timeline(new KeyFrame(
            Duration.seconds(AUTO_REFRESH_INTERVAL),
            e -> refresh()
        ));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
        
        logger.info("Auto-refresh started with {} second interval", AUTO_REFRESH_INTERVAL);
    }
    
    private void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
            logger.info("Auto-refresh stopped");
        }
    }
    
    private void refresh() {
        if (isRefreshing.get()) {
            logger.debug("Refresh already in progress, skipping");
            return;
        }
        
        isRefreshing.set(true);
        refreshProgress.setVisible(true);
        
        Task<Void> refreshTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0, 100);
                
                // Refresh controller data
                controller.refreshAll();
                updateProgress(50, 100);
                
                // Update UI on FX thread
                Platform.runLater(() -> {
                    drivers.clear();
                    drivers.addAll(controller.getDriverStatuses());
                    
                    updateStatusCounts();
                    buildStatusSummary();
                    updateLastRefreshTime();
                    
                    // Update performance metrics
                    updatePerformanceMetrics();
                });
                
                updateProgress(100, 100);
                return null;
            }
            
            @Override
            protected void succeeded() {
                refreshProgress.setVisible(false);
                isRefreshing.set(false);
                logger.info("Fleet status refresh completed");
            }
            
            @Override
            protected void failed() {
                refreshProgress.setVisible(false);
                isRefreshing.set(false);
                logger.error("Fleet status refresh failed", getException());
                showErrorAlert("Refresh Failed", 
                    "Failed to refresh fleet status: " + getException().getMessage());
            }
        };
        
        refreshProgress.progressProperty().bind(refreshTask.progressProperty());
        new Thread(refreshTask).start();
    }
    
    private void updateStatusCounts() {
        statusCounts.clear();
        for (DispatcherDriverStatus driver : drivers) {
            statusCounts.merge(driver.getStatus(), 1, Integer::sum);
            
            // Update last update time for driver
            driverLastUpdateMap.put(driver.getDriverName(), LocalDateTime.now());
        }
    }
    
    private void updatePerformanceMetrics() {
        performanceMetrics.clear();
        
        for (DispatcherDriverStatus driver : drivers) {
            double performance = calculateDriverPerformance(driver);
            performanceMetrics.put(driver.getDriverName(), performance);
        }
    }
    
    private double calculateDriverPerformance(DispatcherDriverStatus driver) {
        // Simple performance calculation based on various factors
        double score = 0.5; // Base score
        
        // Factor in completion rate
        long completedLoads = driver.getAssignedLoads().stream()
            .filter(l -> l.getStatus() == Load.Status.DELIVERED || 
                        l.getStatus() == Load.Status.PAID)
            .count();
        long totalLoads = driver.getAssignedLoads().size();
        
        if (totalLoads > 0) {
            double completionRate = (double) completedLoads / totalLoads;
            score = score * 0.5 + completionRate * 0.5;
        }
        
        // Factor in availability
        if (driver.getStatus() == DispatcherDriverStatus.Status.AVAILABLE) {
            score += 0.1;
        }
        
        // Factor in hours worked efficiency
        if (driver.getHoursWorkedWeek() > 0 && driver.getHoursWorkedWeek() < 60) {
            score += 0.1;
        }
        
        return Math.min(score, 1.0);
    }
    
    private void applyFilters() {
        filteredDrivers.setPredicate(driver -> {
            // Search filter
            String searchText = searchField.getText().toLowerCase();
            if (!searchText.isEmpty()) {
                boolean matches = driver.getDriverName().toLowerCase().contains(searchText) ||
                                 driver.getTruckUnit().toLowerCase().contains(searchText) ||
                                 driver.getCurrentLocation().toLowerCase().contains(searchText);
                if (!matches) return false;
            }
            
            // Status filter
            DispatcherDriverStatus.Status statusFilter = statusFilterCombo.getValue();
            if (statusFilter != null && driver.getStatus() != statusFilter) {
                return false;
            }
            
            // Additional filters can be added here
            
            return true;
        });
    }
    
    private void clearFilters() {
        searchField.clear();
        statusFilterCombo.setValue(null);
        applyFilters();
    }
    
    // Utility methods
    private int calculateActiveLoads() {
        return (int) drivers.stream()
            .flatMap(d -> d.getAssignedLoads().stream())
            .filter(l -> l.getStatus() == Load.Status.IN_TRANSIT || 
                        l.getStatus() == Load.Status.ASSIGNED)
            .count();
    }
    
    private double calculateUtilizationRate() {
        if (drivers.isEmpty()) return 0.0;
        
        long busyDrivers = drivers.stream()
            .filter(d -> d.getStatus() == DispatcherDriverStatus.Status.ON_ROAD ||
                        d.getStatus() == DispatcherDriverStatus.Status.LOADING)
            .count();
        
        return (busyDrivers * 100.0) / drivers.size();
    }
    
    private String getRelativeTime(LocalDateTime time) {
        LocalDateTime now = LocalDateTime.now();
        long minutes = time.until(now, java.time.temporal.ChronoUnit.MINUTES);
        
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        
        long days = hours / 24;
        return days + "d ago";
    }
    
    private void updateLastRefreshTime() {
        lastRefreshTime = LocalDateTime.now();
        lastUpdateLabel.setText("Last updated: " + 
            lastRefreshTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
    
    // Action methods
    private void updateDriverStatus(DispatcherDriverStatus driver) {
        logger.info("Updating status for driver: {}", driver.getDriverName());
        new UpdateDriverStatusDialog(driver, controller).showAndWait().ifPresent(updated -> {
            if (updated) {
                refresh();
            }
        });
    }
    
    private void showDriverDetails(DispatcherDriverStatus driver) {
        logger.info("Showing details for driver: {}", driver.getDriverName());
        
        // Build the services needed to fetch income data
        EmployeeDAO employeeDAO = new EmployeeDAO();
        LoadDAO loadDAO = new LoadDAO();
        FuelTransactionDAO fuelDAO = new FuelTransactionDAO();
        PayrollCalculator payrollCalculator = new PayrollCalculator(employeeDAO, loadDAO, fuelDAO);
        DriverIncomeService incomeService = new DriverIncomeService(employeeDAO, loadDAO, fuelDAO, payrollCalculator);
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        
        Task<DriverIncomeData> loadTask = new Task<DriverIncomeData>() {
            @Override
            protected DriverIncomeData call() throws Exception {
                return incomeService.getDriverIncomeData(driver.getDriver(), startDate, endDate).get();
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            DriverIncomeData data = loadTask.getValue();
            new DriverDetailsDialog(data).showAndWait();
        });
        
        loadTask.setOnFailed(e -> {
            logger.error("Failed to load driver details", loadTask.getException());
            showErrorAlert("Error", "Failed to load driver details: " + 
                loadTask.getException().getMessage());
        });
        
        new Thread(loadTask).start();
    }
    
    private void assignLoadToDriver(DispatcherDriverStatus driver) {
        logger.info("Opening load assignment for driver: {}", driver.getDriverName());
        // Open assignment dialog with driver pre-selected
        controller.showAssignLoadDialog();
    }
    
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Fleet Status to CSV");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("fleet_status_" + 
            LocalDateTime.now().format(EXPORT_TIME_FORMAT) + ".csv");
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                writer.write("Status,Driver,Type,Truck,Trailer,Location,Current Load," +
                           "ETA,Hours Today,Hours Week,Performance\n");
                
                // Write data
                for (DispatcherDriverStatus driver : filteredDrivers) {
                    writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%.1f,%.1f,%.1f%%\n",
                        driver.getStatus().getDisplayName(),
                        driver.getDriverName(),
                        driver.getDriver().getDriverType(),
                        driver.getTruckUnit(),
                        driver.getTrailerNumber() != null ? driver.getTrailerNumber() : "",
                        driver.getCurrentLocation(),
                        driver.getCurrentLoad() != null ? driver.getCurrentLoad().getLoadNumber() : "",
                        driver.getEstimatedAvailableTime() != null ? 
                            driver.getEstimatedAvailableTime().format(TIME_FORMAT) : "",
                        driver.getHoursWorkedToday(),
                        driver.getHoursWorkedWeek(),
                        performanceMetrics.getOrDefault(driver.getDriverName(), 0.0) * 100
                    ));
                }
                
                logger.info("Fleet status exported to: {}", file.getAbsolutePath());
                showInfoAlert("Export Successful", 
                    "Fleet status has been exported to:\n" + file.getAbsolutePath());
                    
            } catch (IOException e) {
                logger.error("Failed to export fleet status", e);
                showErrorAlert("Export Failed",
                    "Failed to export fleet status: " + e.getMessage());
            }
        }
    }

    private void refreshSelectedDrivers() {}
    private void exportSelectedDrivers() {}
    private void showDriverHistory(DispatcherDriverStatus d) {}
    private void contactDriver(DispatcherDriverStatus d) {}
    private void exportToPDF() {}
    private void emailFleetReport() {}
    private void toggleCompactView(boolean v) {}
    private void configureColumns() {}
    
    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    // Cleanup
    public void cleanup() {
        stopAutoRefresh();
    }
}
	
	