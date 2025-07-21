package com.company.payroll.drivergrid;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.loads.LoadsTab;
import com.company.payroll.loads.LoadLocation;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.geometry.Orientation;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;
import javafx.scene.CacheHint;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Enhanced Driver Grid Tab with professional scheduling features
 */
public class DriverGridTabEnhanced extends DriverGridTab {
    private static final Logger logger = LoggerFactory.getLogger(DriverGridTabEnhanced.class);
    
    // UI Components
    private final BorderPane mainLayout = new BorderPane();
    private final VBox topPanel = new VBox(10);
    private final GridPane weekGrid = new GridPane();
    private final ScrollPane weekGridScrollPane = new ScrollPane();
    
    // Data
    private final ObservableList<LoadScheduleEntry> allEntries = FXCollections.observableArrayList();
    private final FilteredList<LoadScheduleEntry> filteredEntries;
    private final ObservableList<Employee> allDrivers = FXCollections.observableArrayList();
    private final ObservableList<String> allCustomers = FXCollections.observableArrayList();
    private final Map<String, List<LoadConflict>> conflictMap = new HashMap<>();
    private LocalDate weekStart = LocalDate.now().with(DayOfWeek.SUNDAY);
    
    // DAOs
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final LoadDAO loadDAO = new LoadDAO();
    private final TrailerDAO trailerDAO = new TrailerDAO();
    
    // Integration
    private LoadsTab loadsTab;
    
    // Refresh timer
    private Timeline autoRefreshTimeline;
    
    // Search and Filter Controls
    private final TextField searchField = new TextField();
    private final ComboBox<Employee> driverFilterBox = new ComboBox<>();
    private final ComboBox<String> customerFilterBox = new ComboBox<>();
    private final ComboBox<Load.Status> statusFilterBox = new ComboBox<>();
    private final DatePicker weekPicker = new DatePicker();
    private final CheckBox showConflictsOnly = new CheckBox("Show Conflicts Only");
    private final CheckBox showUnassignedLoads = new CheckBox("Show Unassigned");
    
    // Week picker listener and week range label as fields for easy access
    private javafx.beans.value.ChangeListener<LocalDate> weekPickerListener;
    private Label weekRangeLabel;
    
    // Status tracking
    private final Label statusLabel = new Label();
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();
    
    // Style constants
    private static final Map<Load.Status, String> STATUS_COLORS = new HashMap<>() {{
        put(Load.Status.BOOKED, "#2563eb");
        put(Load.Status.ASSIGNED, "#f59e0b");
        put(Load.Status.IN_TRANSIT, "#10b981");
        put(Load.Status.DELIVERED, "#059669");
        put(Load.Status.PAID, "#7c3aed");
        put(Load.Status.CANCELLED, "#ef4444");
        put(Load.Status.PICKUP_LATE, "#ff9999");
        put(Load.Status.DELIVERY_LATE, "#ff6666");
    }};
    
    private static final Map<Load.Status, String> STATUS_ICONS = new HashMap<>() {{
        put(Load.Status.BOOKED, "📘");
        put(Load.Status.ASSIGNED, "📋");
        put(Load.Status.IN_TRANSIT, "🚚");
        put(Load.Status.DELIVERED, "✅");
        put(Load.Status.PAID, "💰");
        put(Load.Status.CANCELLED, "❌");
        put(Load.Status.PICKUP_LATE, "⚠️");
        put(Load.Status.DELIVERY_LATE, "🚨");
    }};
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
    
    public DriverGridTabEnhanced() {
        super(); // Call parent constructor
        setText("Driver Grid"); // Set tab text
        setClosable(false);
        
        filteredEntries = new FilteredList<>(allEntries);
        
        setupUI();
        setupEventHandlers();
        setupAutoRefresh();
        
        // Load CSS styling
        try {
            String css = getClass().getResource("/driver-grid-enhanced.css").toExternalForm();
            mainLayout.getStylesheets().add(css);
        } catch (Exception e) {
            logger.warn("Could not load enhanced driver grid CSS", e);
        }
        
        setContent(mainLayout);
        
        // Initial data load
        Platform.runLater(this::refreshData);
        
        // Setup real-time week tracking for Today button
        setupRealTimeWeekTracking();
    }
    
    private void setupUI() {
        // Top panel with controls
        topPanel.setPadding(new Insets(15));
        topPanel.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
        
        // Header with title and status
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Driver Scheduling Grid");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e293b"));
        
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        
        loadingIndicator.setVisible(false);
        loadingIndicator.setPrefSize(20, 20);
        
        statusLabel.setFont(Font.font("Segoe UI", 12));
        statusLabel.setTextFill(Color.web("#64748b"));
        
        headerBox.getChildren().addAll(titleLabel, spacer1, loadingIndicator, statusLabel);
        
        // Week navigation controls
        HBox weekControls = new HBox(15);
        weekControls.setAlignment(Pos.CENTER_LEFT);
        
        Button prevWeekBtn = new Button("◀ Previous");
        prevWeekBtn.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: bold; " +
                            "-fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;");
        prevWeekBtn.setOnMouseEntered(e -> prevWeekBtn.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #374151; " +
                            "-fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;"));
        prevWeekBtn.setOnMouseExited(e -> prevWeekBtn.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; " +
                            "-fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;"));
        
        Button todayBtn = new Button("Today");
        todayBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; " +
                          "-fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;");
        todayBtn.setOnMouseEntered(e -> todayBtn.setStyle("-fx-background-color: #1e40af; -fx-text-fill: white; " +
                          "-fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;"));
        todayBtn.setOnMouseExited(e -> todayBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; " +
                          "-fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;"));
        
        Button nextWeekBtn = new Button("Next ▶");
        nextWeekBtn.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: bold; " +
                            "-fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;");
        nextWeekBtn.setOnMouseEntered(e -> nextWeekBtn.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #374151; " +
                            "-fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;"));
        nextWeekBtn.setOnMouseExited(e -> nextWeekBtn.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; " +
                            "-fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4; -fx-cursor: hand;"));
        
        weekPicker.setValue(weekStart);
        weekPicker.setPrefWidth(150);
        weekPicker.setEditable(false);
        
        weekRangeLabel = new Label();
        weekRangeLabel.setFont(Font.font("Segoe UI", FontWeight.MEDIUM, 14));
        updateWeekRangeLabel(weekRangeLabel);
        
        weekControls.getChildren().addAll(
            prevWeekBtn, todayBtn, weekPicker, nextWeekBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            weekRangeLabel
        );
        
        // Search and filter controls
        HBox filterBox1 = new HBox(10);
        filterBox1.setAlignment(Pos.CENTER_LEFT);
        
        searchField.setPromptText("Search by load #, driver, truck, customer...");
        searchField.setPrefWidth(300);
        
        driverFilterBox.setPromptText("All Drivers");
        driverFilterBox.setPrefWidth(180);
        driverFilterBox.setItems(allDrivers);
        setupDriverComboBox(driverFilterBox);
        
        customerFilterBox.setPromptText("All Customers");
        customerFilterBox.setPrefWidth(180);
        customerFilterBox.setItems(allCustomers);
        
        statusFilterBox.setPromptText("All Statuses");
        statusFilterBox.setPrefWidth(150);
        statusFilterBox.getItems().add(null); // All option
        statusFilterBox.getItems().addAll(Load.Status.values());
        statusFilterBox.setCellFactory(cb -> new StatusListCell());
        statusFilterBox.setButtonCell(new StatusListCell());
        
        Button clearFiltersBtn = new Button("Clear Filters");
        clearFiltersBtn.getStyleClass().add("secondary-button");
        
        filterBox1.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("Driver:"), driverFilterBox,
            new Label("Customer:"), customerFilterBox
        );
        
        HBox filterBox2 = new HBox(10);
        filterBox2.setAlignment(Pos.CENTER_LEFT);
        
        filterBox2.getChildren().addAll(
            new Label("Status:"), statusFilterBox,
            showConflictsOnly,
            showUnassignedLoads,
            clearFiltersBtn
        );
        
        // Action buttons
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        actionBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button scheduleLoadBtn = new Button("📅 Schedule Load");
        scheduleLoadBtn.getStyleClass().add("primary-button");
        
        Button assignDriverBtn = new Button("👤 Assign Driver");
        assignDriverBtn.getStyleClass().add("secondary-button");
        
        Button exportBtn = new Button("📊 Export Schedule");
        exportBtn.getStyleClass().add("secondary-button");
        
        Button printBtn = new Button("🖨 Print View");
        printBtn.getStyleClass().add("secondary-button");
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        
        CheckBox autoRefreshCheck = new CheckBox("Auto-refresh");
        autoRefreshCheck.setSelected(true);
        autoRefreshCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
        
        actionBox.getChildren().addAll(
            scheduleLoadBtn, assignDriverBtn, 
            new Separator(javafx.geometry.Orientation.VERTICAL),
            exportBtn, printBtn, refreshBtn, autoRefreshCheck
        );
        
        // Summary cards
        HBox summaryBox = createSummaryCards();
        
        // Add a help tip for multi-stop loads
        Label multiStopTip = new Label("📍 = Multi-stop load indicator");
        multiStopTip.setFont(Font.font("Segoe UI", 11));
        multiStopTip.setTextFill(Color.web("#64748b"));
        multiStopTip.setPadding(new Insets(5, 0, 0, 0));
        
        topPanel.getChildren().addAll(
            headerBox, weekControls, 
            new Separator(),
            filterBox1, filterBox2,
            new Separator(),
            actionBox, summaryBox, multiStopTip
        );
        
        // Week grid setup
        setupWeekGrid();
        
        // Main layout
        mainLayout.setTop(topPanel);
        mainLayout.setCenter(weekGridScrollPane);
        
        // Event handlers for controls
        prevWeekBtn.setOnAction(e -> navigateWeek(-1));
        nextWeekBtn.setOnAction(e -> navigateWeek(1));
        todayBtn.setOnAction(e -> navigateToToday());
        
        // Create the week picker listener
        weekPickerListener = (obs, oldVal, newVal) -> {
            if (newVal != null) {
                weekStart = newVal.with(DayOfWeek.SUNDAY);
                updateWeekRangeLabel(weekRangeLabel);
                applyFilters();
            }
        };
        weekPicker.valueProperty().addListener(weekPickerListener);
        
        // Prevent date picker from closing unexpectedly
        weekPicker.setOnShowing(e -> {
            // Ensure the picker remains visible
            Platform.runLater(() -> weekPicker.requestFocus());
        });
        
        // Filter listeners
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        driverFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        customerFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        statusFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        showConflictsOnly.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        showUnassignedLoads.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        
        clearFiltersBtn.setOnAction(e -> clearFilters());
        refreshBtn.setOnAction(e -> refreshData());
        scheduleLoadBtn.setOnAction(e -> showScheduleLoadDialog(null, null));
        assignDriverBtn.setOnAction(e -> showBulkAssignDialog());
        exportBtn.setOnAction(e -> exportSchedule());
        printBtn.setOnAction(e -> printSchedule());
    }
    
    private void setupWeekGrid() {
        // Create main container for the entire grid system
        VBox gridSystemContainer = new VBox(0);
        gridSystemContainer.setStyle("-fx-background-color: #f8fafc;");
        VBox.setVgrow(gridSystemContainer, Priority.ALWAYS);
        
        
        // Configure main grid for driver/load data
        weekGrid.setHgap(1);
        weekGrid.setVgap(1);
        weekGrid.setStyle("-fx-background-color: #e2e8f0;");
        weekGrid.setPadding(new Insets(0, 1, 1, 1));
        
        // Create scroll pane for main grid content only
        weekGridScrollPane.setContent(weekGrid);
        weekGridScrollPane.setFitToWidth(true);
        weekGridScrollPane.setFitToHeight(false);
        weekGridScrollPane.setPannable(true);
        weekGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        weekGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        weekGridScrollPane.setStyle("-fx-background: #f8fafc; -fx-background-color: #f8fafc;");
        weekGridScrollPane.setMinHeight(400);
        weekGridScrollPane.setPrefHeight(600);
        weekGridScrollPane.setCache(true);
        weekGridScrollPane.setCacheHint(CacheHint.SPEED);
        
        
        // Ensure scroll pane expands
        VBox.setVgrow(weekGridScrollPane, Priority.ALWAYS);
        
        // Add scrollable content to container
        gridSystemContainer.getChildren().add(weekGridScrollPane);
        
        // Set as center content
        mainLayout.setCenter(gridSystemContainer);
    }
    
    private HBox createSummaryCards() {
        HBox summaryBox = new HBox(15);
        summaryBox.setPadding(new Insets(15, 0, 0, 0));
        
        // Total loads card
        VBox totalLoadsCard = createSummaryCard("📦", "Total Loads", "0", "#3b82f6");
        
        // Active drivers card
        VBox activeDriversCard = createSummaryCard("🚛", "Active Drivers", "0", "#10b981");
        
        // Conflicts card
        VBox conflictsCard = createSummaryCard("⚠️", "Conflicts", "0", "#ef4444");
        
        // Unassigned card
        VBox unassignedCard = createSummaryCard("❓", "Unassigned", "0", "#f59e0b");
        
        summaryBox.getChildren().addAll(totalLoadsCard, activeDriversCard, conflictsCard, unassignedCard);
        
        return summaryBox;
    }
    
    private VBox createSummaryCard(String icon, String title, String value, String color) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8;"
        );
        card.setPrefWidth(150);
        
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("Segoe UI", 24));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 12));
        titleLabel.setTextFill(Color.web("#64748b"));
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        valueLabel.setTextFill(Color.web(color));
        valueLabel.setId(title.toLowerCase().replace(" ", "-") + "-value");
        
        card.getChildren().addAll(iconLabel, titleLabel, valueLabel);
        
        return card;
    }
    
    private void setupDriverComboBox(ComboBox<Employee> comboBox) {
        comboBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty || driver == null) {
                    setText(null);
                } else {
                    String text = driver.getName();
                    if (driver.getTruckUnit() != null) {
                        text += " (" + driver.getTruckUnit() + ")";
                    }
                    setText(text);
                }
            }
        });
        
        comboBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty || driver == null) {
                    setText("All Drivers");
                } else {
                    String text = driver.getName();
                    if (driver.getTruckUnit() != null) {
                        text += " (" + driver.getTruckUnit() + ")";
                    }
                    setText(text);
                }
            }
        });
    }
    
    private void setupEventHandlers() {
        // Tab selection handler
        setOnSelectionChanged(e -> {
            if (isSelected()) {
                refreshData();
            }
        });
        
        // Drag and drop setup will be added here
        setupDragAndDrop();
    }
    
    private void setupDragAndDrop() {
        // Implementation for drag and drop functionality
        // This will allow dragging loads between days and drivers
    }
    
    private void setupAutoRefresh() {
        autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(30), e -> {
                if (isSelected()) {
                    refreshData();
                }
            })
        );
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        startAutoRefresh();
    }
    
    private void startAutoRefresh() {
        autoRefreshTimeline.play();
    }
    
    private void stopAutoRefresh() {
        autoRefreshTimeline.stop();
    }
    
    private void setupRealTimeWeekTracking() {
        // Check every minute if we need to update the week display
        Timeline weekCheckTimeline = new Timeline(new KeyFrame(Duration.minutes(1), e -> {
            LocalDate realTimeToday = LocalDate.now();
            LocalDate realTimeWeekStart = realTimeToday.with(DayOfWeek.SUNDAY);
            
            // Update the week range label to always show accurate dates
            Platform.runLater(() -> {
                // If currently showing this week, update the label to reflect real-time dates
                if (weekStart.equals(realTimeWeekStart)) {
                    updateWeekRangeLabel(weekRangeLabel);
                }
            });
        }));
        weekCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        weekCheckTimeline.play();
        
        // Also check at midnight for date changes
        scheduleTaskAtMidnight();
    }
    
    private void scheduleTaskAtMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long secondsUntilMidnight = java.time.Duration.between(now, midnight).getSeconds();
        
        Timeline midnightTimeline = new Timeline(new KeyFrame(Duration.seconds(secondsUntilMidnight), e -> {
            // At midnight, check if we need to update the week
            Platform.runLater(() -> {
                LocalDate newToday = LocalDate.now();
                LocalDate newWeekStart = newToday.with(DayOfWeek.SUNDAY);
                
                // If we're currently showing what was "this week" but it's now a new week
                if (!weekStart.equals(newWeekStart)) {
                    // Update the label to show the dates accurately
                    updateWeekRangeLabel(weekRangeLabel);
                }
                
                // Schedule the next midnight check
                scheduleTaskAtMidnight();
            });
        }));
        midnightTimeline.play();
    }
    
    @Override
    public void setLoadsTab(LoadsTab loadsTab) {
        super.setLoadsTab(loadsTab); // Call parent method
        this.loadsTab = loadsTab;
        if (loadsTab != null) {
            loadsTab.addLoadDataChangeListener(this::refreshData);
        }
    }
    
    private void refreshData() {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(true);
            statusLabel.setText("Loading...");
        });
        
        // Load data in background
        new Thread(() -> {
            try {
                // Load all data
                List<Employee> drivers = employeeDAO.getAll().stream()
                    .filter(Employee::isDriver)
                    .collect(Collectors.toList());
                
                List<Load> allLoads = loadDAO.getAll();
                
                // Fetch multiple locations for each load
                for (Load load : allLoads) {
                    List<LoadLocation> locations = loadDAO.getLoadLocations(load.getId());
                    load.setLocations(locations);
                }
                
                Set<String> customers = allLoads.stream()
                    .map(Load::getCustomer)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                
                // Create schedule entries
                List<LoadScheduleEntry> entries = new ArrayList<>();
                for (Load load : allLoads) {
                    Employee driver = load.getDriver();
                    if (driver == null && load.getStatus() == Load.Status.BOOKED) {
                        // Unassigned load
                        driver = new Employee(-1, "Unassigned", "", "", 0, 0, 0, null, "", 
                                            Employee.DriverType.OTHER, "", null, null, Employee.Status.ACTIVE);
                    }
                    if (driver != null) {
                        entries.add(new LoadScheduleEntry(load, driver));
                    }
                }
                
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    allDrivers.setAll(drivers);
                    allCustomers.setAll(customers);
                    allEntries.setAll(entries);
                    
                    detectConflicts();
                    applyFilters();
                    updateSummaryCards();
                    
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Last updated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
                });
                
            } catch (Exception e) {
                logger.error("Error refreshing data", e);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Error loading data");
                    showError("Failed to refresh data: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void detectConflicts() {
        conflictMap.clear();
        
        // Group entries by driver
        Map<Employee, List<LoadScheduleEntry>> driverEntries = allEntries.stream()
            .collect(Collectors.groupingBy(LoadScheduleEntry::getDriver));
        
        for (Map.Entry<Employee, List<LoadScheduleEntry>> entry : driverEntries.entrySet()) {
            Employee driver = entry.getKey();
            List<LoadScheduleEntry> driverLoads = entry.getValue();
            List<LoadConflict> conflicts = new ArrayList<>();
            
            // Check each pair of loads for conflicts
            for (int i = 0; i < driverLoads.size(); i++) {
                for (int j = i + 1; j < driverLoads.size(); j++) {
                    Load load1 = driverLoads.get(i).getLoad();
                    Load load2 = driverLoads.get(j).getLoad();
                    
                    if (hasTimeConflict(load1, load2)) {
                        conflicts.add(new LoadConflict(load1, load2, "Schedule overlap"));
                    }
                }
            }
            
            if (!conflicts.isEmpty()) {
                conflictMap.put(driver.getName(), conflicts);
            }
        }
    }
    
    private boolean hasTimeConflict(Load load1, Load load2) {
        if (load1.getPickUpDate() == null || load1.getDeliveryDate() == null ||
            load2.getPickUpDate() == null || load2.getDeliveryDate() == null) {
            return false;
        }
        
        LocalDateTime start1 = LocalDateTime.of(load1.getPickUpDate(), 
            load1.getPickUpTime() != null ? load1.getPickUpTime() : LocalTime.MIDNIGHT);
        LocalDateTime end1 = LocalDateTime.of(load1.getDeliveryDate(),
            load1.getDeliveryTime() != null ? load1.getDeliveryTime() : LocalTime.MAX);
        
        LocalDateTime start2 = LocalDateTime.of(load2.getPickUpDate(),
            load2.getPickUpTime() != null ? load2.getPickUpTime() : LocalTime.MIDNIGHT);
        LocalDateTime end2 = LocalDateTime.of(load2.getDeliveryDate(),
            load2.getDeliveryTime() != null ? load2.getDeliveryTime() : LocalTime.MAX);
        
        // Check for overlap
        return !(end1.isBefore(start2) || end2.isBefore(start1));
    }
    
    private void applyFilters() {
        Predicate<LoadScheduleEntry> predicate = entry -> {
            Load load = entry.getLoad();
            Employee driver = entry.getDriver();
            
            // Date filter - load must overlap with current week
            LocalDate weekEnd = weekStart.plusDays(6);
            if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
                boolean overlapsWeek = !load.getDeliveryDate().isBefore(weekStart) && 
                                     !load.getPickUpDate().isAfter(weekEnd);
                if (!overlapsWeek) return false;
            }
            
            // Search filter
            String searchText = searchField.getText().toLowerCase();
            if (!searchText.isEmpty()) {
                boolean matches = load.getLoadNumber().toLowerCase().contains(searchText) ||
                    (driver.getName() != null && driver.getName().toLowerCase().contains(searchText)) ||
                    (driver.getTruckUnit() != null && driver.getTruckUnit().toLowerCase().contains(searchText)) ||
                    (load.getCustomer() != null && load.getCustomer().toLowerCase().contains(searchText));
                if (!matches) return false;
            }
            
            // Driver filter
            Employee selectedDriver = driverFilterBox.getValue();
            if (selectedDriver != null && !driver.equals(selectedDriver)) {
                return false;
            }
            
            // Customer filter
            String selectedCustomer = customerFilterBox.getValue();
            if (selectedCustomer != null && !selectedCustomer.equals(load.getCustomer())) {
                return false;
            }
            
            // Status filter
            Load.Status selectedStatus = statusFilterBox.getValue();
            if (selectedStatus != null && load.getStatus() != selectedStatus) {
                return false;
            }
            
            // Conflict filter
            if (showConflictsOnly.isSelected()) {
                boolean hasConflict = conflictMap.containsKey(driver.getName()) &&
                    conflictMap.get(driver.getName()).stream()
                        .anyMatch(c -> c.load1.equals(load) || c.load2.equals(load));
                if (!hasConflict) return false;
            }
            
            // Unassigned filter
            if (showUnassignedLoads.isSelected() && driver.getId() != -1) {
                return false;
            }
            
            return true;
        };
        
        filteredEntries.setPredicate(predicate);
        updateWeekGrid();
        updateSummaryCards();
    }
    
    private void updateWeekGrid() {
        weekGrid.getChildren().clear();
        weekGrid.getColumnConstraints().clear();
        weekGrid.getRowConstraints().clear();
        
        // Column constraints with fixed widths
        ColumnConstraints driverCol = new ColumnConstraints();
        driverCol.setMinWidth(200);
        driverCol.setPrefWidth(200);
        driverCol.setMaxWidth(200);
        driverCol.setHgrow(Priority.NEVER);
        weekGrid.getColumnConstraints().add(driverCol);
        
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setMinWidth(150);
            dayCol.setPrefWidth(150);
            dayCol.setHgrow(Priority.ALWAYS);
            weekGrid.getColumnConstraints().add(dayCol);
        }
        
        // Build header as first row of the main grid
        buildWeekHeaderInGrid();
        
        // Group entries by driver
        Map<Employee, List<LoadScheduleEntry>> driverGroups = filteredEntries.stream()
            .collect(Collectors.groupingBy(LoadScheduleEntry::getDriver));
        
        // Sort drivers (active drivers first, then unassigned)
        List<Employee> sortedDrivers = new ArrayList<>(driverGroups.keySet());
        sortedDrivers.sort((d1, d2) -> {
            if (d1.getId() == -1) return 1; // Unassigned at bottom
            if (d2.getId() == -1) return -1;
            // Sort by truck unit if available, otherwise by name
            if (d1.getTruckUnit() != null && d2.getTruckUnit() != null) {
                try {
                    int unit1 = Integer.parseInt(d1.getTruckUnit());
                    int unit2 = Integer.parseInt(d2.getTruckUnit());
                    return Integer.compare(unit1, unit2);
                } catch (NumberFormatException e) {
                    // Fall back to string comparison
                }
            }
            return d1.getName().compareToIgnoreCase(d2.getName());
        });
        
        // Add row constraint for header row
        RowConstraints headerRowConstraint = new RowConstraints();
        headerRowConstraint.setMinHeight(100);
        headerRowConstraint.setPrefHeight(100);
        headerRowConstraint.setMaxHeight(100);
        headerRowConstraint.setVgrow(Priority.NEVER);
        weekGrid.getRowConstraints().add(headerRowConstraint);
        
        // Build rows (starting from row 1 since row 0 is the header)
        int rowIndex = 1;
        for (Employee driver : sortedDrivers) {
            List<LoadScheduleEntry> driverEntries = driverGroups.get(driver);
            buildDriverRow(driver, driverEntries, rowIndex++);
            
            // Add row constraint for consistent sizing
            RowConstraints rowConstraint = new RowConstraints();
            rowConstraint.setMinHeight(100);
            rowConstraint.setPrefHeight(120);
            rowConstraint.setVgrow(Priority.SOMETIMES);
            weekGrid.getRowConstraints().add(rowConstraint);
        }
        
        // If we have many drivers (e.g., handling 80+ loads), ensure smooth scrolling
        if (sortedDrivers.size() > 10) {
            weekGrid.setCache(true);
            weekGrid.setCacheHint(CacheHint.SPEED);
        }
    }
    
    private void buildWeekHeaderInGrid() {
        logger.info("Building week header in main grid for week starting: {} with date format MM/dd/yyyy", weekStart);
        
        // Driver column header
        VBox driverHeader = new VBox(2);
        driverHeader.setAlignment(Pos.CENTER);
        driverHeader.setPadding(new Insets(10));
        driverHeader.setStyle(
            "-fx-background-color: #1e293b; " +
            "-fx-text-fill: white; " +
            "-fx-border-color: #334155; " +
            "-fx-border-width: 0 1 1 0;"
        );
        driverHeader.setMinHeight(98);
        driverHeader.setPrefHeight(98);
        
        Label driverLabel = new Label("DRIVER / TRUCK");
        driverLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        driverLabel.setTextFill(Color.WHITE);
        
        Label driverSubLabel = new Label("Click name to view details");
        driverSubLabel.setFont(Font.font("Segoe UI", 10));
        driverSubLabel.setTextFill(Color.web("#94a3b8"));
        
        driverHeader.getChildren().addAll(driverLabel, driverSubLabel);
        weekGrid.add(driverHeader, 0, 0);
        
        // Day headers
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String[] dayNames = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            VBox dayHeader = new VBox(2);
            dayHeader.setAlignment(Pos.CENTER);
            dayHeader.setPadding(new Insets(8, 5, 8, 5));
            dayHeader.setMinHeight(98);
            dayHeader.setPrefHeight(98);
            
            boolean isToday = date.equals(LocalDate.now());
            boolean isWeekend = (i == 0 || i == 6);
            
            // Style for each day
            String bgColor = isToday ? "#3b82f6" : (isWeekend ? "#fbbf24" : "#475569");
            dayHeader.setStyle(
                "-fx-background-color: " + bgColor + "; " +
                "-fx-border-color: #334155; " +
                "-fx-border-width: 0 1 1 0;"
            );
            
            // Day name
            Label dayLabel = new Label(dayNames[i]);
            dayLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            dayLabel.setTextFill(Color.WHITE);
            
            // Date (MM/DD/YYYY)
            String formattedDate = date.format(dateFormat);
            Label dateLabel = new Label(formattedDate);
            dateLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            dateLabel.setTextFill(isToday ? Color.WHITE : Color.web("#f1f5f9"));
            
            // Load count
            Label loadCountLabel = new Label("0 loads");
            loadCountLabel.setFont(Font.font("Segoe UI", 9));
            loadCountLabel.setTextFill(Color.web("#e2e8f0"));
            loadCountLabel.setId("day-" + i + "-load-count");
            
            dayHeader.getChildren().addAll(dayLabel, dateLabel);
            
            // Add TODAY badge if current day
            if (isToday) {
                Label todayBadge = new Label("TODAY");
                todayBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                todayBadge.setTextFill(Color.web("#1e293b"));
                todayBadge.setStyle(
                    "-fx-background-color: #fbbf24; " +
                    "-fx-padding: 1 6 1 6; " +
                    "-fx-background-radius: 10;"
                );
                dayHeader.getChildren().add(todayBadge);
            }
            
            dayHeader.getChildren().add(loadCountLabel);
            
            // Add to grid
            weekGrid.add(dayHeader, i + 1, 0);
            
            logger.debug("Added day header for {}: {}", dayNames[i], formattedDate);
        }
        
        // Update load counts
        updateHeaderLoadCounts();
    }
    
    
    private void buildDriverRow(Employee driver, List<LoadScheduleEntry> entries, int rowIndex) {
        // Driver info cell
        VBox driverCell = new VBox(5);
        driverCell.setPadding(new Insets(15));
        driverCell.setStyle(
            "-fx-background-color: " + (rowIndex % 2 == 0 ? "white" : "#f8fafc") + "; " +
            "-fx-border-color: #e2e8f0; -fx-border-width: 0 2 1 0; " +
            "-fx-min-width: 200; -fx-pref-width: 200; -fx-max-width: 200;"
        );
        driverCell.setMinHeight(100);
        driverCell.setPrefHeight(120);
        driverCell.setMaxHeight(150); // Allow some flexibility for multiple loads
        
        Label nameLabel = new Label(driver.getName());
        nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        nameLabel.setTextFill(driver.getId() == -1 ? Color.web("#ef4444") : Color.web("#1e293b"));
        
        if (driver.getId() != -1) {
            Label truckLabel = new Label("🚛 " + (driver.getTruckUnit() != null ? driver.getTruckUnit() : "No Unit"));
            truckLabel.setFont(Font.font("Segoe UI", 12));
            truckLabel.setTextFill(Color.web("#64748b"));
            
            // Driver status indicator
            HBox statusBox = new HBox(5);
            Circle statusDot = new Circle(4);
            statusDot.setFill(Color.web("#10b981"));
            Label statusLabel = new Label("Available");
            statusLabel.setFont(Font.font("Segoe UI", 10));
            statusLabel.setTextFill(Color.web("#64748b"));
            statusBox.getChildren().addAll(statusDot, statusLabel);
            
            driverCell.getChildren().addAll(nameLabel, truckLabel, statusBox);
        } else {
            driverCell.getChildren().add(nameLabel);
        }
        
        weekGrid.add(driverCell, 0, rowIndex);
        
        // Day cells
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            StackPane dayCell = createDayCell(driver, date, entries, rowIndex);
            weekGrid.add(dayCell, day + 1, rowIndex);
        }
    }
    
    private StackPane createDayCell(Employee driver, LocalDate date, List<LoadScheduleEntry> entries, int rowIndex) {
        StackPane cell = new StackPane();
        cell.setStyle(
            "-fx-background-color: " + (rowIndex % 2 == 0 ? "white" : "#f8fafc") + "; " +
            "-fx-border-color: #e2e8f0; -fx-border-width: 0 1 1 0; " +
            "-fx-min-width: 150; -fx-pref-width: 150;"
        );
        cell.setMinHeight(100);
        cell.setPrefHeight(120);
        cell.setMaxHeight(150); // Allow expansion for multiple loads
        
        // Add hover effect for better interactivity
        cell.setOnMouseEntered(e -> {
            if (!cell.getStyle().contains("#e0e7ff")) {
                cell.setStyle(cell.getStyle().replace(rowIndex % 2 == 0 ? "white" : "#f8fafc", "#f0f9ff"));
            }
        });
        cell.setOnMouseExited(e -> {
            cell.setStyle(
                "-fx-background-color: " + (rowIndex % 2 == 0 ? "white" : "#f8fafc") + "; " +
                "-fx-border-color: #e2e8f0; -fx-border-width: 0 1 1 0; " +
                "-fx-min-width: 150; -fx-pref-width: 150;"
            );
        });
        
        VBox loadsContainer = new VBox(3);
        loadsContainer.setPadding(new Insets(5));
        loadsContainer.setAlignment(Pos.TOP_LEFT);
        
        // Find loads for this day
        List<LoadScheduleEntry> dayLoads = entries.stream()
            .filter(entry -> {
                Load load = entry.getLoad();
                return load.getPickUpDate() != null && load.getDeliveryDate() != null &&
                       !date.isBefore(load.getPickUpDate()) && !date.isAfter(load.getDeliveryDate());
            })
            .collect(Collectors.toList());
        
        // Add load bars (limit display to prevent overflow)
        int maxDisplayLoads = 4; // Show max 4 loads per cell
        for (int i = 0; i < Math.min(dayLoads.size(), maxDisplayLoads); i++) {
            HBox loadBar = createLoadBar(dayLoads.get(i), date);
            loadsContainer.getChildren().add(loadBar);
        }
        
        // Add "more" indicator if there are additional loads
        if (dayLoads.size() > maxDisplayLoads) {
            Label moreLabel = new Label(String.format("+%d more", dayLoads.size() - maxDisplayLoads));
            moreLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 10));
            moreLabel.setTextFill(Color.web("#6b7280"));
            moreLabel.setPadding(new Insets(2, 8, 2, 8));
            loadsContainer.getChildren().add(moreLabel);
        }
        
        cell.getChildren().add(loadsContainer);
        
        // Add drop zone for drag and drop
        setupDropZone(cell, driver, date);
        
        // Context menu
        cell.setOnContextMenuRequested(e -> {
            ContextMenu menu = new ContextMenu();
            MenuItem scheduleItem = new MenuItem("📅 Schedule New Load");
            scheduleItem.setOnAction(event -> showScheduleLoadDialog(driver, date));
            menu.getItems().add(scheduleItem);
            
            if (!dayLoads.isEmpty()) {
                menu.getItems().add(new SeparatorMenuItem());
                for (LoadScheduleEntry entry : dayLoads) {
                    Load load = entry.getLoad();
                    String menuText = "Edit " + load.getLoadNumber();
                    if (load.hasMultipleStops()) {
                        int stops = 2 + load.getPickupLocations().size() + load.getDropLocations().size();
                        menuText += " (📍 " + stops + " stops)";
                    }
                    MenuItem loadItem = new MenuItem(menuText);
                    loadItem.setOnAction(event -> editLoad(load));
                    menu.getItems().add(loadItem);
                }
            }
            
            menu.show(cell, e.getScreenX(), e.getScreenY());
        });
        
        return cell;
    }
    
    private HBox createLoadBar(LoadScheduleEntry entry, LocalDate date) {
        Load load = entry.getLoad();
        Employee driver = entry.getDriver();
        
        HBox bar = new HBox(5);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        
        String color = STATUS_COLORS.get(load.getStatus());
        boolean hasConflict = conflictMap.containsKey(driver.getName()) &&
            conflictMap.get(driver.getName()).stream()
                .anyMatch(c -> c.load1.equals(load) || c.load2.equals(load));
        
        if (hasConflict) {
            color = "#ef4444";
        }
        
        bar.setStyle(
            "-fx-background-color: " + color + "; " +
            "-fx-background-radius: 4; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2); " +
            "-fx-cursor: hand;"
        );
        
        // Make the bar smaller to fit more loads
        bar.setMaxHeight(22);
        bar.setPrefHeight(22);
        
        // Load info with multi-stop indicator
        Label iconLabel = new Label(STATUS_ICONS.get(load.getStatus()));
        iconLabel.setFont(Font.font("Segoe UI", 12));
        
        Label loadLabel = new Label(load.getLoadNumber());
        loadLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        loadLabel.setTextFill(Color.WHITE);
        
        // Show time if it's pickup or delivery day
        String timeText = "";
        if (date.equals(load.getPickUpDate()) && load.getPickUpTime() != null) {
            timeText = " • " + load.getPickUpTime().format(TIME_FORMAT);
        } else if (date.equals(load.getDeliveryDate()) && load.getDeliveryTime() != null) {
            timeText = " • " + load.getDeliveryTime().format(TIME_FORMAT);
        }
        
        Label timeLabel = new Label(timeText);
        timeLabel.setFont(Font.font("Segoe UI", 10));
        timeLabel.setTextFill(Color.WHITE);
        
        // Add multi-stop indicator if applicable
        HBox.setHgrow(loadLabel, Priority.ALWAYS);
        bar.getChildren().addAll(iconLabel, loadLabel);
        
        if (load.hasMultipleStops()) {
            int totalStops = load.getPickupLocations().size() + load.getDropLocations().size();
            Label multiStopLabel = new Label("📍" + totalStops);
            multiStopLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            multiStopLabel.setTextFill(Color.WHITE);
            multiStopLabel.setTooltip(new Tooltip(totalStops + " total stops"));
            bar.getChildren().add(multiStopLabel);
        }
        
        bar.getChildren().add(timeLabel);
        
        // Enhanced tooltip with multiple locations
        String tooltipText = buildEnhancedTooltip(load, hasConflict);
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(javafx.util.Duration.millis(200));
        tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: #1e293b; -fx-text-fill: white; -fx-padding: 10;");
        Tooltip.install(bar, tooltip);
        
        // Click to edit
        bar.setCursor(javafx.scene.Cursor.HAND);
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                editLoad(load);
            }
        });
        
        // Setup drag
        setupDragSource(bar, entry);
        
        return bar;
    }
    
    private void setupDragSource(HBox bar, LoadScheduleEntry entry) {
        bar.setOnDragDetected(e -> {
            Dragboard db = bar.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(entry.getLoad().getId()));
            db.setContent(content);
            e.consume();
        });
    }
    
    private void setupDropZone(StackPane cell, Employee driver, LocalDate date) {
        cell.setOnDragOver(e -> {
            if (e.getGestureSource() != cell && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
                cell.setStyle(cell.getStyle() + "; -fx-background-color: #dbeafe;");
            }
            e.consume();
        });
        
        cell.setOnDragExited(e -> {
            cell.setStyle(cell.getStyle().replace("; -fx-background-color: #dbeafe;", ""));
            e.consume();
        });
        
        cell.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            
            if (db.hasString()) {
                int loadId = Integer.parseInt(db.getString());
                // Handle load reassignment
                reassignLoad(loadId, driver, date);
                success = true;
            }
            
            e.setDropCompleted(success);
            e.consume();
        });
    }
    
    private void updateWeekRangeLabel(Label label) {
        LocalDate weekEnd = weekStart.plusDays(6);
        label.setText(weekStart.format(DATE_FORMAT) + " - " + weekEnd.format(DATE_FORMAT));
    }
    
    private void updateSummaryCards() {
        // Calculate summary values
        long totalLoads = filteredEntries.size();
        long activeDrivers = filteredEntries.stream()
            .map(LoadScheduleEntry::getDriver)
            .filter(d -> d.getId() != -1)
            .distinct()
            .count();
        long conflicts = filteredEntries.stream()
            .filter(entry -> {
                Employee driver = entry.getDriver();
                Load load = entry.getLoad();
                return conflictMap.containsKey(driver.getName()) &&
                    conflictMap.get(driver.getName()).stream()
                        .anyMatch(c -> c.load1.equals(load) || c.load2.equals(load));
            })
            .count();
        long unassigned = filteredEntries.stream()
            .filter(entry -> entry.getDriver().getId() == -1)
            .count();
        
        // Count multi-stop loads
        long multiStopLoads = filteredEntries.stream()
            .filter(entry -> entry.getLoad().hasMultipleStops())
            .count();
        
        // Update labels
        Platform.runLater(() -> {
            if (multiStopLoads > 0) {
                updateSummaryValue("total-loads-value", totalLoads + " (" + multiStopLoads + " multi)");
            } else {
                updateSummaryValue("total-loads-value", String.valueOf(totalLoads));
            }
            updateSummaryValue("active-drivers-value", String.valueOf(activeDrivers));
            updateSummaryValue("conflicts-value", String.valueOf(conflicts));
            updateSummaryValue("unassigned-value", String.valueOf(unassigned));
        });
    }
    
    private void updateSummaryValue(String id, String value) {
        mainLayout.lookupAll("#" + id).stream()
            .filter(node -> node instanceof Label)
            .map(node -> (Label) node)
            .forEach(label -> label.setText(value));
    }
    
    private void navigateWeek(int direction) {
        weekStart = weekStart.plusWeeks(direction);
        weekPicker.setValue(weekStart);
        // Force header refresh to show new dates
        Platform.runLater(() -> {
            updateWeekGrid();
        });
    }
    
    private void navigateToToday() {
        // Get the real-time current date - always fetch fresh
        LocalDate realTimeToday = LocalDate.now();
        // Calculate the start of the current week (Sunday)
        LocalDate realTimeWeekStart = realTimeToday.with(DayOfWeek.SUNDAY);
        
        // Log for debugging
        logger.info("Today button clicked - navigating to week of: {}", realTimeWeekStart);
        
        // Update the week start
        weekStart = realTimeWeekStart;
        
        // Update the week picker to show the current week
        // Use Platform.runLater to ensure UI updates happen on JavaFX thread
        Platform.runLater(() -> {
            // Temporarily remove listener to avoid recursive calls
            weekPicker.valueProperty().removeListener(weekPickerListener);
            
            // Set to any date within the current week (picker will snap to Sunday)
            weekPicker.setValue(realTimeToday);
            
            // Force update to week start
            weekStart = realTimeWeekStart;
            
            // Re-add the listener
            weekPicker.valueProperty().addListener(weekPickerListener);
            
            // Force update the week range label to show current week dates
            updateWeekRangeLabel(weekRangeLabel);
            
            // Clear any filters that might be hiding current week data
            if (showConflictsOnly.isSelected() || showUnassignedLoads.isSelected()) {
                showConflictsOnly.setSelected(false);
                showUnassignedLoads.setSelected(false);
            }
            
            // Refresh the entire grid view with current week data
            refreshData();
            
            // Ensure the date picker shows the correct value
            if (!weekPicker.getValue().equals(realTimeToday)) {
                weekPicker.setValue(realTimeToday);
            }
        });
    }
    
    private void clearFilters() {
        searchField.clear();
        driverFilterBox.setValue(null);
        customerFilterBox.setValue(null);
        statusFilterBox.setValue(null);
        showConflictsOnly.setSelected(false);
        showUnassignedLoads.setSelected(false);
    }
    
    private void showScheduleLoadDialog(Employee driver, LocalDate date) {
        // Implementation for schedule load dialog
        if (loadsTab != null) {
            loadsTab.showLoadDialog(null, true);
        }
    }
    
    private void showBulkAssignDialog() {
        logger.debug("Opening driver assignment dialog");
        
        // Get all unassigned loads
        List<Load> unassignedLoads = allEntries.stream()
            .filter(entry -> entry.getDriver().getId() == -1)
            .map(LoadScheduleEntry::getLoad)
            .filter(load -> load.getStatus() == Load.Status.BOOKED || load.getStatus() == Load.Status.ASSIGNED)
            .sorted((l1, l2) -> {
                // Sort by pickup date, then by load number
                if (l1.getPickUpDate() != null && l2.getPickUpDate() != null) {
                    int dateCompare = l1.getPickUpDate().compareTo(l2.getPickUpDate());
                    if (dateCompare != 0) return dateCompare;
                }
                return l1.getLoadNumber().compareTo(l2.getLoadNumber());
            })
            .collect(Collectors.toList());
        
        if (unassignedLoads.isEmpty()) {
            showInfo("No unassigned loads found.");
            return;
        }
        
        // Create dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Assign Drivers to Loads");
        dialog.setHeaderText("Select loads and assign drivers");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().setPrefSize(900, 600);
        
        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        // Summary label
        Label summaryLabel = new Label(String.format("Found %d unassigned load(s)", unassignedLoads.size()));
        summaryLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        
        // Create table for load selection with enhanced styling
        TableView<AssignmentRow> table = new TableView<>();
        ObservableList<AssignmentRow> tableData = FXCollections.observableArrayList();
        
        // Enhanced table styling (matching LoadsTab)
        table.setStyle("-fx-font-size: 12px;");
        table.setRowFactory(tv -> {
            TableRow<AssignmentRow> row = new TableRow<>();
            
            // Set row height
            row.setPrefHeight(35);
            row.setMinHeight(35);
            
            // Add alternating row colors
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #ffffff;");
                    } else {
                        row.setStyle("-fx-background-color: #f9f9f9;");
                    }
                }
            });
            
            // Add hover effect
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    row.setStyle(row.getStyle() + "-fx-background-color: #e8f4f8;");
                }
            });
            
            row.setOnMouseExited(e -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #ffffff;");
                    } else {
                        row.setStyle("-fx-background-color: #f9f9f9;");
                    }
                }
            });
            
            return row;
        });
        
        // Enable unconstrained resize policy for better column control
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);
        table.setMinWidth(900);
        
        // Checkbox column with enhanced styling
        TableColumn<AssignmentRow, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(70);
        selectCol.setMinWidth(60);
        selectCol.setStyle("-fx-alignment: CENTER;");
        selectCol.setEditable(true);
        
        // Load number column
        TableColumn<AssignmentRow, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getLoad().getLoadNumber()));
        loadNumCol.setPrefWidth(110);
        loadNumCol.setMinWidth(90);
        loadNumCol.setStyle("-fx-alignment: CENTER;");
        
        // Customer column
        TableColumn<AssignmentRow, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(cellData -> {
            Load load = cellData.getValue().getLoad();
            String customer1 = load.getCustomer() != null ? load.getCustomer() : "";
            String customer2 = load.getCustomer2() != null ? load.getCustomer2() : "";
            if (customer2.isEmpty() || customer2.equals(customer1)) {
                return new SimpleStringProperty(customer1);
            } else {
                return new SimpleStringProperty(customer1 + " / " + customer2);
            }
        });
        customerCol.setPrefWidth(200);
        customerCol.setMinWidth(150);
        
        // Pickup date column
        TableColumn<AssignmentRow, String> pickupDateCol = new TableColumn<>("Pickup Date");
        pickupDateCol.setCellValueFactory(cellData -> {
            Load load = cellData.getValue().getLoad();
            String dateStr = load.getPickUpDate() != null ? 
                load.getPickUpDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) : "TBD";
            return new SimpleStringProperty(dateStr);
        });
        pickupDateCol.setPrefWidth(120);
        pickupDateCol.setMinWidth(100);
        pickupDateCol.setStyle("-fx-alignment: CENTER;");
        
        // Pickup location column
        TableColumn<AssignmentRow, String> pickupLocCol = new TableColumn<>("Pickup Location");
        pickupLocCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getLoad().getPickUpLocation()));
        pickupLocCol.setPrefWidth(250);
        pickupLocCol.setMinWidth(200);
        
        // Driver selection column
        TableColumn<AssignmentRow, Employee> driverCol = new TableColumn<>("Assign Driver");
        driverCol.setCellValueFactory(cellData -> cellData.getValue().selectedDriverProperty());
        driverCol.setPrefWidth(220);
        driverCol.setMinWidth(180);
        driverCol.setCellFactory(column -> new TableCell<AssignmentRow, Employee>() {
            private final ComboBox<Employee> driverCombo = new ComboBox<>();
            
            {
                driverCombo.setItems(FXCollections.observableArrayList(allDrivers));
                driverCombo.setCellFactory(cb -> new ListCell<Employee>() {
                    @Override
                    protected void updateItem(Employee driver, boolean empty) {
                        super.updateItem(driver, empty);
                        if (empty || driver == null) {
                            setText(null);
                        } else {
                            setText(driver.getName() + 
                                   (driver.getTruckUnit() != null ? " (" + driver.getTruckUnit() + ")" : ""));
                        }
                    }
                });
                driverCombo.setButtonCell(new ListCell<Employee>() {
                    @Override
                    protected void updateItem(Employee driver, boolean empty) {
                        super.updateItem(driver, empty);
                        if (empty || driver == null) {
                            setText("Select Driver...");
                        } else {
                            setText(driver.getName() + 
                                   (driver.getTruckUnit() != null ? " (" + driver.getTruckUnit() + ")" : ""));
                        }
                    }
                });
                
                driverCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    AssignmentRow row = getTableView().getItems().get(getIndex());
                    row.setSelectedDriver(newVal);
                    if (newVal != null) {
                        row.setSelected(true);
                    }
                });
            }
            
            @Override
            protected void updateItem(Employee driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    driverCombo.setValue(driver);
                    setGraphic(driverCombo);
                }
            }
        });
        driverCol.setPrefWidth(200);
        driverCol.setEditable(true);
        
        // Add columns to table
        @SuppressWarnings("unchecked")
        TableColumn<AssignmentRow, ?>[] columns = new TableColumn[] {
            selectCol, loadNumCol, customerCol, pickupDateCol, pickupLocCol, driverCol
        };
        table.getColumns().addAll(columns);
        table.setEditable(true);
        
        // Populate table
        for (Load load : unassignedLoads) {
            tableData.add(new AssignmentRow(load));
        }
        table.setItems(tableData);
        
        // Select/Deselect all buttons with enhanced styling
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(5, 0, 10, 0));
        
        Button selectAllBtn = new Button("Select All");
        selectAllBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold;");
        selectAllBtn.setOnAction(e -> tableData.forEach(row -> row.setSelected(true)));
        
        Button deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-weight: bold;");
        deselectAllBtn.setOnAction(e -> tableData.forEach(row -> row.setSelected(false)));
        
        Button autoAssignBtn = new Button("Auto-Assign Available Drivers");
        autoAssignBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        autoAssignBtn.setOnAction(e -> autoAssignDrivers(tableData));
        
        buttonBox.getChildren().addAll(selectAllBtn, deselectAllBtn, new Separator(Orientation.VERTICAL), autoAssignBtn);
        
        // Wrap table in ScrollPane for horizontal scrolling (matching LoadsTab)
        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setFitToWidth(false);  // Allow horizontal scrolling
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setPrefViewportHeight(400);
        tableScrollPane.setPrefViewportWidth(850);
        
        // Add components to content
        content.getChildren().addAll(summaryLabel, buttonBox, tableScrollPane);
        
        // Dialog buttons
        ButtonType assignButtonType = new ButtonType("Assign Selected", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        
        // Handle assign action
        dialog.setResultConverter(buttonType -> {
            if (buttonType == assignButtonType) {
                // Process assignments
                int assignedCount = 0;
                for (AssignmentRow row : tableData) {
                    if (row.isSelected() && row.getSelectedDriver() != null) {
                        Load load = row.getLoad();
                        Employee driver = row.getSelectedDriver();
                        
                        // Update load with driver
                        load.setDriver(driver);
                        load.setStatus(Load.Status.ASSIGNED);
                        
                        // Save to database
                        loadDAO.update(load);
                        assignedCount++;
                        
                        logger.info("Assigned load {} to driver {}", load.getLoadNumber(), driver.getName());
                    }
                }
                
                if (assignedCount > 0) {
                    showInfo(String.format("Successfully assigned %d load(s) to drivers.", assignedCount));
                    refreshData();
                } else {
                    showWarning("No loads were assigned. Please select loads and drivers.");
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void autoAssignDrivers(ObservableList<AssignmentRow> rows) {
        // Simple auto-assignment logic - assigns to available drivers in rotation
        List<Employee> availableDrivers = new ArrayList<>(allDrivers);
        int driverIndex = 0;
        
        for (AssignmentRow row : rows) {
            if (row.getSelectedDriver() == null && !availableDrivers.isEmpty()) {
                row.setSelectedDriver(availableDrivers.get(driverIndex % availableDrivers.size()));
                row.setSelected(true);
                driverIndex++;
            }
        }
    }
    
    // Inner class for assignment table
    private static class AssignmentRow {
        private final Load load;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleObjectProperty<Employee> selectedDriver = new SimpleObjectProperty<>();
        
        public AssignmentRow(Load load) {
            this.load = load;
        }
        
        public Load getLoad() { return load; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public SimpleBooleanProperty selectedProperty() { return selected; }
        
        public Employee getSelectedDriver() { return selectedDriver.get(); }
        public void setSelectedDriver(Employee driver) { selectedDriver.set(driver); }
        public SimpleObjectProperty<Employee> selectedDriverProperty() { return selectedDriver; }
    }
    
    private void editLoad(Load load) {
        if (loadsTab != null) {
            loadsTab.showLoadDialog(load, false);
        }
    }
    
    private void reassignLoad(int loadId, Employee newDriver, LocalDate newDate) {
        // Implementation for load reassignment via drag and drop
    }
    
    private void exportSchedule() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Schedule");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("driver_schedule_" + weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                exportToCSV(file);
                showInfo("Schedule exported successfully to " + file.getName());
            } catch (IOException e) {
                logger.error("Error exporting schedule", e);
                showError("Failed to export schedule: " + e.getMessage());
            }
        }
    }
    
    private void exportToCSV(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            // Enhanced header with multiple locations
            writer.write("Driver,Truck,Load #,Customer,Status,Total Stops,Pickup Locations,Pickup Dates,Delivery Locations,Delivery Dates\n");
            
            // Data
            for (LoadScheduleEntry entry : filteredEntries) {
                Load load = entry.getLoad();
                Employee driver = entry.getDriver();
                
                // Build location strings for CSV
                StringBuilder pickupLocs = new StringBuilder(load.getPickUpLocation());
                StringBuilder pickupDates = new StringBuilder(formatDateTime(load.getPickUpDate(), load.getPickUpTime()));
                for (LoadLocation loc : load.getPickupLocations()) {
                    pickupLocs.append(" | ");
                    if (loc.getCustomer() != null && !loc.getCustomer().isEmpty()) {
                        pickupLocs.append(loc.getCustomer()).append(" - ");
                    }
                    pickupLocs.append(loc.getCity()).append(", ").append(loc.getState());
                    pickupDates.append(" | ").append(formatDateTime(loc.getDate(), loc.getTime()));
                }
                
                StringBuilder dropLocs = new StringBuilder(load.getDropLocation());
                StringBuilder dropDates = new StringBuilder(formatDateTime(load.getDeliveryDate(), load.getDeliveryTime()));
                for (LoadLocation loc : load.getDropLocations()) {
                    dropLocs.append(" | ");
                    if (loc.getCustomer() != null && !loc.getCustomer().isEmpty()) {
                        dropLocs.append(loc.getCustomer()).append(" - ");
                    }
                    dropLocs.append(loc.getCity()).append(", ").append(loc.getState());
                    dropDates.append(" | ").append(formatDateTime(loc.getDate(), loc.getTime()));
                }
                
                int totalStops = 2 + load.getPickupLocations().size() + load.getDropLocations().size();
                
                writer.write(String.format("%s,%s,%s,%s,%s,%d,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    driver.getName(),
                    driver.getTruckUnit() != null ? driver.getTruckUnit() : "",
                    load.getLoadNumber(),
                    load.getCustomer(),
                    load.getStatus(),
                    totalStops,
                    pickupLocs.toString().replace("\"", "\\\""),
                    pickupDates.toString().replace("\"", "\\\""),
                    dropLocs.toString().replace("\"", "\\\""),
                    dropDates.toString().replace("\"", "\\\"")
                ));
            }
        }
    }
    
    private void printSchedule() {
        // Implementation for printing the schedule view
    }
    
    private String formatDateTime(LocalDate date, LocalTime time) {
        if (date == null) return "TBD";
        String result = date.format(DATE_FORMAT);
        if (time != null) {
            result += " " + time.format(TIME_FORMAT);
        }
        return result;
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String message) {
        // Don't show dialogs during jpackage testing
        if ("true".equals(System.getProperty("jpackage.testing"))) {
            logger.info("Suppressing dialog during jpackage testing: {}", message);
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private String buildEnhancedTooltip(Load load, boolean hasConflict) {
        StringBuilder tooltip = new StringBuilder();
        
        // Basic info
        tooltip.append("Load: ").append(load.getLoadNumber()).append("\n");
        tooltip.append("Customer: ").append(load.getCustomer()).append("\n");
        tooltip.append("Status: ").append(load.getStatus()).append("\n\n");
        
        // Pickup locations
        tooltip.append("PICKUP LOCATIONS:\n");
        
        // Primary pickup
        tooltip.append("  1. ").append(load.getPickUpLocation())
               .append(" (").append(formatDateTime(load.getPickUpDate(), load.getPickUpTime())).append(")\n");
        
        // Additional pickups
        List<LoadLocation> pickups = load.getPickupLocations();
        if (!pickups.isEmpty()) {
            for (LoadLocation loc : pickups) {
                tooltip.append("  ").append(loc.getSequence() + 1).append(". ");
                if (loc.getCustomer() != null && !loc.getCustomer().isEmpty()) {
                    tooltip.append(loc.getCustomer()).append(" - ");
                }
                tooltip.append(loc.getCity()).append(", ").append(loc.getState());
                if (loc.getDate() != null) {
                    tooltip.append(" (").append(formatDateTime(loc.getDate(), loc.getTime())).append(")");
                }
                tooltip.append("\n");
            }
        }
        
        // Drop locations
        tooltip.append("\nDROP LOCATIONS:\n");
        
        // Primary drop
        tooltip.append("  1. ").append(load.getDropLocation())
               .append(" (").append(formatDateTime(load.getDeliveryDate(), load.getDeliveryTime())).append(")\n");
        
        // Additional drops
        List<LoadLocation> drops = load.getDropLocations();
        if (!drops.isEmpty()) {
            for (LoadLocation loc : drops) {
                tooltip.append("  ").append(loc.getSequence() + 1).append(". ");
                if (loc.getCustomer() != null && !loc.getCustomer().isEmpty()) {
                    tooltip.append(loc.getCustomer()).append(" - ");
                }
                tooltip.append(loc.getCity()).append(", ").append(loc.getState());
                if (loc.getDate() != null) {
                    tooltip.append(" (").append(formatDateTime(loc.getDate(), loc.getTime())).append(")");
                }
                tooltip.append("\n");
            }
        }
        
        // Total stops summary
        int totalStops = 2 + pickups.size() + drops.size(); // Primary pickup + drop + additional
        if (totalStops > 2) {
            tooltip.append("\nTotal Stops: ").append(totalStops).append("\n");
        }
        
        // Conflict warning
        if (hasConflict) {
            tooltip.append("\n⚠️ CONFLICT DETECTED");
        }
        
        return tooltip.toString();
    }
    
    private void updateHeaderLoadCounts() {
        // Count loads for each day
        int[] dayCounts = new int[7];
        
        for (LoadScheduleEntry entry : filteredEntries) {
            Load load = entry.getLoad();
            if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
                for (int i = 0; i < 7; i++) {
                    LocalDate date = weekStart.plusDays(i);
                    if (!date.isBefore(load.getPickUpDate()) && !date.isAfter(load.getDeliveryDate())) {
                        dayCounts[i]++;
                    }
                }
            }
        }
        
        // Update the labels in the headers (now in weekGrid)
        for (int i = 0; i < 7; i++) {
            Label countLabel = (Label) weekGrid.lookup("#day-" + i + "-load-count");
            
            if (countLabel != null) {
                int count = dayCounts[i];
                countLabel.setText(count + " load" + (count != 1 ? "s" : ""));
                // Change color based on load count
                if (dayCounts[i] == 0) {
                    countLabel.setTextFill(Color.web("#94a3b8"));
                } else if (dayCounts[i] < 10) {
                    countLabel.setTextFill(Color.web("#10b981"));
                } else if (dayCounts[i] < 20) {
                    countLabel.setTextFill(Color.web("#f59e0b"));
                } else {
                    countLabel.setTextFill(Color.web("#ef4444"));
                }
            }
        }
    }
    
    // Inner classes
    public static class LoadScheduleEntry {
        private final Load load;
        private final Employee driver;
        
        public LoadScheduleEntry(Load load, Employee driver) {
            this.load = load;
            this.driver = driver;
        }
        
        public Load getLoad() { return load; }
        public Employee getDriver() { return driver; }
    }
    
    private static class LoadConflict {
        final Load load1;
        final Load load2;
        final String reason;
        
        LoadConflict(Load load1, Load load2, String reason) {
            this.load1 = load1;
            this.load2 = load2;
            this.reason = reason;
        }
    }
    
    private class StatusListCell extends ListCell<Load.Status> {
        @Override
        protected void updateItem(Load.Status status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setText("All Statuses");
                setGraphic(null);
            } else {
                setText(STATUS_ICONS.get(status) + " " + status.toString());
                setTextFill(Color.web(STATUS_COLORS.get(status)));
            }
        }
    }
}