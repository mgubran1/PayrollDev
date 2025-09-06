package com.company.payroll.drivergrid;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.loads.LoadsTab;
import com.company.payroll.loads.LoadLocation;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;
import com.company.payroll.drivergrid.LoadStatusUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
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
    
    // Style constants are shared via LoadStatusUtil
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
    
    public DriverGridTabEnhanced() {
        super(); // Call parent constructor but don't let it set up UI
        setText("Driver Grid"); // Set tab text
        setClosable(false);
        
        filteredEntries = new FilteredList<>(allEntries);
        
        // Load CSS styling - use the main styles.css file that contains the layout styles
        try {
            // Base styles
            String baseCss = getClass().getResource("/styles.css").toExternalForm();
            mainLayout.getStylesheets().add(baseCss);

            // Responsive driver grid styles
            String responsiveCss = getClass().getResource("/css/driver-grid-responsive.css").toExternalForm();
            mainLayout.getStylesheets().add(responsiveCss);
        } catch (Exception e) {
            logger.warn("Could not load driver grid CSS", e);
        }
        
        setupUI();
        setupEventHandlers();
        setupAutoRefresh();
        
        setContent(mainLayout);
        
        // Initial data load
        Platform.runLater(this::refreshData);
        
        // Setup real-time week tracking for Today button
        setupRealTimeWeekTracking();
    }
    
    private void setupUI() {
        // Top panel with controls - collapsible
        topPanel.setPadding(new Insets(0));
        topPanel.setStyle("-fx-background-color: #f8fafc;");
        
        // Create collapsible header container
        VBox headerContainer = new VBox(0);
        headerContainer.getStyleClass().add("driver-grid-header-container");
        headerContainer.getStyleClass().add("driver-grid-header-expanded"); // Default to expanded
        
        // Header toggle button row
        HBox toggleRow = new HBox(10);
        toggleRow.setAlignment(Pos.CENTER_LEFT);
        toggleRow.setPadding(new Insets(5, 10, 5, 10));
        
        // Title in toggle row (always visible)
        Label titleLabel = new Label("Driver Scheduling Grid");
        titleLabel.getStyleClass().add("driver-grid-title");
        
        // Week range display (always visible)
        weekRangeLabel = new Label();
        weekRangeLabel.getStyleClass().add("driver-grid-week-range");
        updateWeekRangeLabel(weekRangeLabel);
        HBox.setHgrow(weekRangeLabel, Priority.ALWAYS);
        
        // Header toggle button
        Button toggleHeaderBtn = new Button("▼"); // Down arrow for collapse
        toggleHeaderBtn.getStyleClass().add("driver-grid-collapse-btn");
        toggleHeaderBtn.setTooltip(new Tooltip("Collapse/Expand Header"));
        
        // Compact view button
        Button compactViewBtn = new Button("📱 Compact View");
        compactViewBtn.getStyleClass().add("driver-grid-compact-view-btn");
        compactViewBtn.setOnAction(e -> showCompactView());
        
        // Add components to toggle row
        toggleRow.getChildren().addAll(titleLabel, weekRangeLabel, compactViewBtn, toggleHeaderBtn);
        
        // Main header content that will be hidden/shown
        VBox headerContent = new VBox(5);
        headerContent.setPadding(new Insets(5, 10, 10, 10));
        
        // Week navigation controls in a FlowPane for better wrapping
        FlowPane weekControls = new FlowPane(5, 5);
        weekControls.setAlignment(Pos.CENTER_LEFT);
        weekControls.getStyleClass().add("driver-grid-week-nav");
        weekControls.setPrefWrapLength(400);
        
        Button prevWeekBtn = new Button("◀");
        prevWeekBtn.getStyleClass().add("driver-grid-nav-button");
        prevWeekBtn.setTooltip(new Tooltip("Previous Week"));
        
        Button todayBtn = new Button("Today");
        todayBtn.getStyleClass().add("driver-grid-nav-button");
        todayBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white;");
        
        Button nextWeekBtn = new Button("▶");
        nextWeekBtn.getStyleClass().add("driver-grid-nav-button");
        nextWeekBtn.setTooltip(new Tooltip("Next Week"));
        
        weekPicker.setValue(weekStart);
        weekPicker.setPrefWidth(120);
        weekPicker.setEditable(false);
        
        weekControls.getChildren().addAll(prevWeekBtn, todayBtn, weekPicker, nextWeekBtn);
        
        // Filters in a FlowPane for better wrapping
        FlowPane filtersBox = new FlowPane(5, 5);
        filtersBox.setAlignment(Pos.CENTER_LEFT);
        filtersBox.getStyleClass().add("driver-grid-search-section");
        filtersBox.setPrefWrapLength(700);
        
        searchField.setPromptText("Search...");
        searchField.getStyleClass().add("driver-grid-search-field");
        
        driverFilterBox.setPromptText("All Drivers");
        driverFilterBox.getStyleClass().add("driver-grid-filter-combo");
        driverFilterBox.setItems(allDrivers);
        setupDriverComboBox(driverFilterBox);
        
        customerFilterBox.setPromptText("All Customers");
        customerFilterBox.getStyleClass().add("driver-grid-filter-combo");
        customerFilterBox.setItems(allCustomers);
        
        statusFilterBox.setPromptText("All Statuses");
        statusFilterBox.getStyleClass().add("driver-grid-filter-combo");
        statusFilterBox.getItems().clear();
        statusFilterBox.getItems().add(null); // All option
        statusFilterBox.getItems().addAll(Load.Status.values());
        statusFilterBox.setCellFactory(cb -> new StatusListCell());
        statusFilterBox.setButtonCell(new StatusListCell());
        
        showConflictsOnly.getStyleClass().add("driver-grid-checkbox");
        showUnassignedLoads.getStyleClass().add("driver-grid-checkbox");
        
        Button clearFiltersBtn = new Button("Clear");
        clearFiltersBtn.getStyleClass().add("driver-grid-action-button-secondary");
        
        // Add filter components to filters box
        filtersBox.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("Driver:"), driverFilterBox,
            new Label("Customer:"), customerFilterBox,
            new Label("Status:"), statusFilterBox,
            showConflictsOnly, showUnassignedLoads, clearFiltersBtn
        );
        
        // Action buttons in a FlowPane for better wrapping
        FlowPane actionBox = new FlowPane(5, 5);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        actionBox.getStyleClass().add("driver-grid-action-buttons");
        actionBox.setPrefWrapLength(500);
        
        Button scheduleLoadBtn = new Button("📅 Schedule");
        scheduleLoadBtn.getStyleClass().add("driver-grid-action-button");
        
        Button assignDriverBtn = new Button("👤 Assign");
        assignDriverBtn.getStyleClass().add("driver-grid-action-button");
        
        Button exportBtn = new Button("📊 Export");
        exportBtn.getStyleClass().add("driver-grid-action-button");
        
        Button printBtn = new Button("🖨 Print");
        printBtn.getStyleClass().add("driver-grid-action-button");
        
        Button refreshBtn = new Button("🔄");
        refreshBtn.getStyleClass().add("driver-grid-action-button");
        refreshBtn.setTooltip(new Tooltip("Refresh Data"));
        
        CheckBox autoRefreshCheck = new CheckBox("Auto");
        autoRefreshCheck.setSelected(true);
        autoRefreshCheck.getStyleClass().add("driver-grid-checkbox");
        autoRefreshCheck.setTooltip(new Tooltip("Auto-refresh"));
        
        autoRefreshCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) { startAutoRefresh(); } else { stopAutoRefresh(); }
        });
        
        actionBox.getChildren().addAll(scheduleLoadBtn, assignDriverBtn, exportBtn, printBtn, refreshBtn, autoRefreshCheck);
        
        // Layout the header content
        HBox controlsRow = new HBox(10);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filtersBox, Priority.ALWAYS);
        controlsRow.getChildren().addAll(weekControls, filtersBox);
        
        // Add summary cards to a separate row
        FlowPane summaryBox = createSummaryCards();
        summaryBox.getStyleClass().add("driver-grid-summary-container");
        
        // Multi-stop indicator label
        Label multiStopTip = new Label("- Multi-stop load indicator");
        multiStopTip.getStyleClass().add("driver-grid-multi-stop-indicator");
        
        // Add all components to header content
        headerContent.getChildren().addAll(controlsRow, actionBox, summaryBox, multiStopTip);
        
        // Add toggle row and content to header container
        headerContainer.getChildren().addAll(toggleRow, headerContent);
        
        // Add header container to top panel
        topPanel.getChildren().clear();
        topPanel.getChildren().add(headerContainer);
        
        // Week grid setup with enhanced responsive layout
        setupWeekGrid();
        mainLayout.setTop(topPanel);
        mainLayout.setCenter(weekGridScrollPane);
        
        // Toggle header collapsible functionality
        toggleHeaderBtn.setOnAction(e -> {
            boolean isExpanded = headerContainer.getStyleClass().contains("driver-grid-header-expanded");
            if (isExpanded) {
                // Collapse the header
                headerContainer.getStyleClass().remove("driver-grid-header-expanded");
                headerContainer.getStyleClass().add("driver-grid-header-collapsed");
                headerContent.setVisible(false);
                headerContent.setManaged(false);
                toggleHeaderBtn.setText("▲"); // Up arrow for expand
            } else {
                // Expand the header
                headerContainer.getStyleClass().remove("driver-grid-header-collapsed");
                headerContainer.getStyleClass().add("driver-grid-header-expanded");
                headerContent.setVisible(true);
                headerContent.setManaged(true);
                toggleHeaderBtn.setText("▼"); // Down arrow for collapse
            }
        });
        
        // Add CSS class for dynamic responsive design
        mainLayout.getStyleClass().add("driver-grid-desktop"); // Default class
        
        // Event handlers for controls
        prevWeekBtn.setOnAction(e -> navigateWeek(-1));
        nextWeekBtn.setOnAction(e -> navigateWeek(1));
        todayBtn.setOnAction(e -> navigateToToday());
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
        
        // Setup window resize listener for responsive design
        setupWindowSizeListener();
    }
    
    private void setupWindowSizeListener() {
        mainLayout.widthProperty().addListener((obs, oldVal, newVal) -> {
            double width = newVal.doubleValue();
            applyResponsiveBreakpoint(width);
        });
    }

    private void applyResponsiveBreakpoint(double width) {
        // Clear all breakpoint classes
        mainLayout.getStyleClass().removeAll(
            "driver-grid-mobile", 
            "driver-grid-tablet", 
            "driver-grid-desktop", 
            "driver-grid-wide"
        );
        
        // Apply appropriate class based on width
        if (width < 768) {
            mainLayout.getStyleClass().add("driver-grid-mobile");
            logger.info("Applied mobile breakpoint");
        } else if (width < 1024) {
            mainLayout.getStyleClass().add("driver-grid-tablet");
            logger.info("Applied tablet breakpoint");
        } else if (width < 1440) {
            mainLayout.getStyleClass().add("driver-grid-desktop");
            logger.info("Applied desktop breakpoint");
        } else {
            mainLayout.getStyleClass().add("driver-grid-wide");
            logger.info("Applied wide breakpoint");
        }
        
        // Force layout update
        updateWeekGrid();
    }

    private void setupWeekGrid() {
        // Create main container for the entire grid system
        VBox gridSystemContainer = new VBox(0);
        gridSystemContainer.getStyleClass().add("driver-grid-container");
        VBox.setVgrow(gridSystemContainer, Priority.ALWAYS);
        
        // Configure main grid with responsive column constraints
        weekGrid.setHgap(1);
        weekGrid.setVgap(1);
        weekGrid.getStyleClass().add("driver-grid-week-grid");
        weekGrid.setPadding(new Insets(0, 1, 1, 1));
        
        // Create enhanced scroll pane for the grid
        weekGridScrollPane.setContent(weekGrid);
        weekGridScrollPane.getStyleClass().add("driver-grid-scroll-pane");
        weekGridScrollPane.setFitToWidth(true);
        weekGridScrollPane.setFitToHeight(false);
        weekGridScrollPane.setPannable(true);
        weekGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        weekGridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
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
    
    private FlowPane createSummaryCards() {
        FlowPane summaryBox = new FlowPane(15, 15);
        summaryBox.setPrefWrapLength(650);
        summaryBox.setAlignment(Pos.CENTER);
        
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
        card.getStyleClass().add("driver-grid-summary-box");
        card.setPrefWidth(150);
        
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("Segoe UI", 24));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 12));
        titleLabel.setTextFill(Color.web("#64748b"));
        titleLabel.getStyleClass().add("driver-grid-summary-title");
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        valueLabel.setTextFill(Color.web(color));
        valueLabel.setId(title.toLowerCase().replace(" ", "-") + "-value");
        valueLabel.getStyleClass().add("driver-grid-summary-value");
        
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
        
        // Enhanced responsive column sizing
        ColumnConstraints driverCol = new ColumnConstraints();
        driverCol.setMinWidth(150);
        driverCol.setPercentWidth(18);
        driverCol.setHgrow(Priority.SOMETIMES);
        weekGrid.getColumnConstraints().add(driverCol);

        // All day columns should have equal width
        double dayPercent = 82.0 / 7.0; // distribute remaining width
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setMinWidth(100);
            dayCol.setPercentWidth(dayPercent);
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
            
            // Add horizontal row separator after each driver (except the last one)
            if (sortedDrivers.indexOf(driver) < sortedDrivers.size() - 1) {
                Region rowSeparator = new Region();
                rowSeparator.getStyleClass().add("driver-grid-row-separator");
                rowSeparator.setPrefHeight(1);
                rowSeparator.setMaxWidth(Double.MAX_VALUE);  // Ensure it spans full width
                rowSeparator.setMouseTransparent(true); // Make sure it doesn't block mouse events
                GridPane.setColumnSpan(rowSeparator, 8); // Span all columns
                GridPane.setRowIndex(rowSeparator, rowIndex - 1);
                GridPane.setValignment(rowSeparator, VPos.BOTTOM);
                weekGrid.getChildren().add(0, rowSeparator); // Add at the bottom of z-order
            }
        }
        
        // If there are no drivers/loads, show an empty grid with placeholder cells
        if (sortedDrivers.isEmpty()) {
            addEmptyGridPlaceholder();
        }
        
        // Add vertical day separators
        for (int colIndex = 1; colIndex < 7; colIndex++) {
            // Skip the first and last column as they don't need separators
            Region colSeparator = new Region();
            colSeparator.getStyleClass().add("driver-grid-day-divider");
            colSeparator.setPrefWidth(1);
            colSeparator.setMaxHeight(Double.MAX_VALUE);
            colSeparator.setMouseTransparent(true); // Make sure it doesn't block mouse events
            GridPane.setColumnIndex(colSeparator, colIndex);
            GridPane.setRowSpan(colSeparator, rowIndex);
            GridPane.setHalignment(colSeparator, HPos.LEFT);
            weekGrid.getChildren().add(0, colSeparator); // Add at the bottom of z-order
        }
        
        // If we have many drivers (e.g., handling 80+ loads), ensure smooth scrolling
        if (sortedDrivers.size() > 10) {
            weekGrid.setCache(true);
            weekGrid.setCacheHint(CacheHint.SPEED);
        }
    }

    /**
     * Add placeholder cells when grid is empty
     */
    private void addEmptyGridPlaceholder() {
        // Add one empty row with placeholder cells
        for (int colIndex = 1; colIndex <= 7; colIndex++) {
            final int col = colIndex; // Make it effectively final for lambda
            
            StackPane emptyCell = new StackPane();
            emptyCell.getStyleClass().add("driver-grid-day-cell");
            emptyCell.getStyleClass().add("driver-grid-empty-cell");
            
            Label placeholderLabel = new Label("No loads scheduled");
            placeholderLabel.setTextFill(Color.web("#9ca3af"));
            placeholderLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 12));
            
            if (col == 4) { // Only add the label in the middle column
                emptyCell.getChildren().add(placeholderLabel);
            }
            
            // Make cells clickable to add loads
            Button addBtn = new Button("+");
            addBtn.getStyleClass().add("driver-grid-quick-add-button");
            addBtn.setVisible(false);
            addBtn.setManaged(false);
            addBtn.setOnAction(e -> showScheduleLoadDialog(null, weekStart.plusDays(col - 1)));
            
            StackPane.setAlignment(addBtn, Pos.CENTER);
            emptyCell.getChildren().add(addBtn);
            
            // Show the add button on hover
            emptyCell.setOnMouseEntered(e -> {
                emptyCell.setStyle("-fx-background-color: #f0f9ff;");
                addBtn.setVisible(true);
                addBtn.setManaged(true);
            });
            
            emptyCell.setOnMouseExited(e -> {
                emptyCell.setStyle("");
                addBtn.setVisible(false);
                addBtn.setManaged(false);
            });
            
            // Double-click to schedule
            emptyCell.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    LocalDate date = weekStart.plusDays(col - 1);
                    showScheduleLoadDialog(null, date);
                }
            });
            
            GridPane.setRowIndex(emptyCell, 1);
            GridPane.setColumnIndex(emptyCell, col);
            weekGrid.add(emptyCell, col, 1);
        }
        
        // Add an empty driver cell
        Label emptyDriverLabel = new Label("No drivers assigned");
        emptyDriverLabel.setTextFill(Color.web("#9ca3af"));
        emptyDriverLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 12));
        
        VBox emptyDriverCell = new VBox();
        emptyDriverCell.getStyleClass().add("driver-grid-driver-cell");
        emptyDriverCell.setAlignment(Pos.CENTER);
        emptyDriverCell.getChildren().add(emptyDriverLabel);
        
        weekGrid.add(emptyDriverCell, 0, 1);
    }
    
    private void buildWeekHeaderInGrid() {
        logger.info("Building week header in main grid for week starting: {} with date format MM/dd/yyyy", weekStart);
        
        // Driver column header
        VBox driverHeader = new VBox(2);
        driverHeader.setAlignment(Pos.CENTER);
        driverHeader.setPadding(new Insets(10));
        driverHeader.getStyleClass().add("driver-grid-day-header");
        driverHeader.setStyle("-fx-background-color: #1e293b;");
        
        Label driverLabel = new Label("DRIVER / TRUCK");
        driverLabel.getStyleClass().add("driver-grid-day-name");
        driverLabel.setTextFill(Color.WHITE);
        
        // Removed "Click name to view details" text as requested
        
        driverHeader.getChildren().addAll(driverLabel);
        weekGrid.add(driverHeader, 0, 0);
        
        // Day headers with modern colors
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String[] dayNames = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            VBox dayHeader = new VBox(2);
            dayHeader.setAlignment(Pos.CENTER);
            dayHeader.setPadding(new Insets(8, 5, 8, 5));
            
            boolean isToday = date.equals(LocalDate.now());
            boolean isWeekend = (i == 0 || i == 6);
            
            // Apply appropriate CSS classes and set border styles
            dayHeader.getStyleClass().add("driver-grid-day-header");
            
            // Apply weekend/weekday colors as requested
            if (isWeekend) {
                // Light yellow for Sunday and Saturday
                dayHeader.setStyle("-fx-background-color: #FEF9C3;");
                dayHeader.getStyleClass().add("driver-grid-day-header-weekend");
            } else {
                // Light blue for Monday-Friday
                dayHeader.setStyle("-fx-background-color: #E0F2FE;");
                dayHeader.getStyleClass().add("driver-grid-day-header-weekday");
            }
            
            // Today highlight overrides weekend/weekday colors
            if (isToday) {
                dayHeader.setStyle("-fx-background-color: #CFFAFE;");
                dayHeader.getStyleClass().add("driver-grid-day-header-today");
            }
            
            // Add right border for column separation
            if (i < 6) {
                String currentStyle = dayHeader.getStyle();
                dayHeader.setStyle(currentStyle + "; -fx-border-color: black; -fx-border-width: 0 1px 0 0;");
            }
            
            // Day name
            Label dayLabel = new Label(dayNames[i]);
            dayLabel.getStyleClass().add("driver-grid-day-name");
            dayLabel.setTextFill(Color.web("#1e293b")); // Dark text for better contrast on light backgrounds
            
            // Date (MM/DD/YYYY)
            String formattedDate = date.format(dateFormat);
            Label dateLabel = new Label(formattedDate);
            dateLabel.getStyleClass().add("driver-grid-day-date");
            dateLabel.setTextFill(Color.web("#475569")); // Medium dark text for better contrast
            
            // Load count
            Label loadCountLabel = new Label("0 loads");
            loadCountLabel.setFont(Font.font("Segoe UI", 9));
            loadCountLabel.setTextFill(Color.web("#475569"));
            loadCountLabel.setId("day-" + i + "-load-count");
            
            dayHeader.getChildren().addAll(dayLabel, dateLabel);
            
            // Add TODAY badge if current day
            if (isToday) {
                Label todayBadge = new Label("TODAY");
                todayBadge.setStyle(
                    "-fx-background-color: #0284c7; " + // Darker blue for TODAY badge
                    "-fx-text-fill: white; " +
                    "-fx-padding: 1 6 1 6; " +
                    "-fx-background-radius: 10;" +
                    "-fx-font-size: 9px; " +
                    "-fx-font-weight: bold;"
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
        driverCell.getStyleClass().add("driver-grid-driver-cell");
        
        // Apply zebra striping classes for alternating row colors
        // Note: rowIndex starts at 1 (header is row 0), so we use rowIndex for striping
        if (rowIndex % 2 == 1) {
            driverCell.getStyleClass().add("driver-grid-row-odd");
        } else {
            driverCell.getStyleClass().add("driver-grid-row-even");
        }
        
        Label nameLabel = new Label(driver.getName());
        nameLabel.getStyleClass().add("driver-grid-driver-name");
        nameLabel.setTextFill(driver.getId() == -1 ? Color.web("#ef4444") : Color.web("#1e293b"));
        
        if (driver.getId() != -1) {
            Label truckLabel = new Label("🚛 " + (driver.getTruckUnit() != null ? driver.getTruckUnit() : "No Unit"));
            truckLabel.getStyleClass().add("driver-grid-truck-label");
            
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
            
            // The cell styling is now handled in CSS and createDayCell method
            // No need to add inline styles that might interfere
            
            weekGrid.add(dayCell, day + 1, rowIndex);
        }
    }
    
    private StackPane createDayCell(Employee driver, LocalDate date, List<LoadScheduleEntry> entries, int rowIndex) {
        StackPane cell = new StackPane();
        cell.getStyleClass().add("driver-grid-day-cell");
        
        // Apply zebra striping classes for alternating row colors
        if (rowIndex % 2 == 0) {
            cell.getStyleClass().add("driver-grid-row-even");
        } else {
            cell.getStyleClass().add("driver-grid-row-odd");
        }
        
        // Create loads container
        VBox loadsContainer = new VBox(3);
        loadsContainer.setPadding(new Insets(5));
        loadsContainer.setAlignment(Pos.TOP_LEFT);
        loadsContainer.setPickOnBounds(false); // Allow mouse events to pass through
        loadsContainer.setMouseTransparent(false); // But allow children to receive events
        
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
            // Make sure load bars don't block cell interactions
            loadBar.setPickOnBounds(false);
            loadsContainer.getChildren().add(loadBar);
        }
        
        // Add "more" indicator if there are additional loads
        if (dayLoads.size() > maxDisplayLoads) {
            HBox moreRow = new HBox(5);
            moreRow.setAlignment(Pos.CENTER_LEFT);
            moreRow.setPickOnBounds(false);
            
            Label moreLabel = new Label(String.format("+%d more", dayLoads.size() - maxDisplayLoads));
            moreLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 10));
            moreLabel.setTextFill(Color.web("#6b7280"));
            moreLabel.setPadding(new Insets(2, 8, 2, 8));
            
            Button viewAllBtn = new Button("View All");
            viewAllBtn.getStyleClass().add("driver-grid-small-button");
            viewAllBtn.setOnAction(e -> {
                showLoadsList(driver, date, dayLoads);
                e.consume();
            });
            
            moreRow.getChildren().addAll(moreLabel, viewAllBtn);
            loadsContainer.getChildren().add(moreRow);
        }
        
        // Create a quick add button for this cell
        Button quickAddBtn = new Button("+");
        quickAddBtn.getStyleClass().add("driver-grid-quick-add-button");
        quickAddBtn.setTooltip(new Tooltip("Quick schedule load"));
        quickAddBtn.setVisible(false); // Initially hidden
        quickAddBtn.setManaged(false); // Don't affect layout when hidden
        
        quickAddBtn.setOnAction(e -> {
            showScheduleLoadDialog(driver, date);
            e.consume();
        });
        
        // Position in top-right corner
        StackPane.setAlignment(quickAddBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(quickAddBtn, new Insets(5, 5, 0, 0));
        
        // Create an invisible overlay for mouse events - this should be on top
        Region mouseEventRegion = new Region();
        mouseEventRegion.setPickOnBounds(true);
        mouseEventRegion.setStyle("-fx-background-color: transparent;");
        
        // Add components to cell in correct z-order (first added = bottom)
        cell.getChildren().addAll(loadsContainer, quickAddBtn, mouseEventRegion);
        
        // Add hover effect for better interactivity
        mouseEventRegion.setOnMouseEntered(e -> {
            if (!cell.getStyle().contains("-fx-background-color: #f0f9ff;")) {
                cell.setStyle(cell.getStyle() + "; -fx-background-color: #f0f9ff;");
            }
            quickAddBtn.setVisible(true);
            quickAddBtn.setManaged(true);
            quickAddBtn.toFront(); // Ensure button is on top
        });
        
        mouseEventRegion.setOnMouseExited(e -> {
            // Reset style but preserve borders
            String style = cell.getStyle().replace("; -fx-background-color: #f0f9ff;", "");
            cell.setStyle(style);
            quickAddBtn.setVisible(false);
            quickAddBtn.setManaged(false);
        });
        
        // Double click to add a load
        mouseEventRegion.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                showScheduleLoadDialog(driver, date);
                e.consume();
            }
        });
        
        // Add tooltip to indicate scheduling ability
        Tooltip tooltip = new Tooltip("Right-click to schedule/manage loads\nDouble-click to schedule new load");
        tooltip.setShowDelay(Duration.millis(500));
        Tooltip.install(mouseEventRegion, tooltip);
        
        // Add drop zone for drag and drop
        setupDropZone(cell, driver, date);
        
        // Enhanced context menu with more options - attach to the mouseEventRegion
        mouseEventRegion.setOnContextMenuRequested(e -> {
            ContextMenu menu = new ContextMenu();
            
            // Schedule new load
            MenuItem scheduleItem = new MenuItem("📅 Schedule New Load");
            scheduleItem.setOnAction(event -> showScheduleLoadDialog(driver, date));
            menu.getItems().add(scheduleItem);
            
            // View all loads for this day
            if (!dayLoads.isEmpty()) {
                MenuItem viewAllItem = new MenuItem("👁️ View All Loads (" + dayLoads.size() + ")");
                viewAllItem.setOnAction(event -> showLoadsList(driver, date, dayLoads));
                menu.getItems().add(viewAllItem);
                
                menu.getItems().add(new SeparatorMenuItem());
                
                // Edit individual loads
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
            e.consume();
        });
        
        return cell;
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
                setText(LoadStatusUtil.iconFor(status) + " " + status.toString());
                setTextFill(Color.web(LoadStatusUtil.colorFor(status)));
            }
        }
    }

    /**
     * Show a compact view dialog with drivers and their loads
     */
    private void showCompactView() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Compact Driver Schedule");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create dialog content
        VBox content = new VBox(10);
        content.getStyleClass().add("driver-grid-compact-dialog");
        content.setPrefWidth(800);
        content.setPrefHeight(600);
        content.setMaxHeight(Double.MAX_VALUE);
        
        // Header with date range
        Label headerLabel = new Label("Driver Schedule: " + 
                                     weekStart.format(DateTimeFormatter.ofPattern("MMM d")) + " - " + 
                                     weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        headerLabel.getStyleClass().add("driver-grid-compact-dialog-title");
        
        // Create a simple filter box
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        TextField compactSearch = new TextField();
        compactSearch.setPromptText("Quick search...");
        compactSearch.getStyleClass().add("driver-grid-search-field");
        compactSearch.setPrefWidth(200);
        
        ComboBox<Load.Status> compactStatusFilter = new ComboBox<>();
        compactStatusFilter.getItems().add(null);
        compactStatusFilter.getItems().addAll(Load.Status.values());
        compactStatusFilter.setValue(null);
        compactStatusFilter.setPromptText("All Statuses");
        compactStatusFilter.getStyleClass().add("driver-grid-filter-combo");
        compactStatusFilter.setCellFactory(cb -> new StatusListCell());
        
        filterBox.getChildren().addAll(new Label("Search:"), compactSearch, new Label("Status:"), compactStatusFilter);
        
        // Create the main content area with scrolling
        VBox driversContainer = new VBox(0);
        ScrollPane scrollPane = new ScrollPane(driversContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("driver-grid-compact-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Group entries by driver
        Map<Employee, List<LoadScheduleEntry>> driverGroups = filteredEntries.stream()
            .collect(Collectors.groupingBy(LoadScheduleEntry::getDriver));
        
        // Sort drivers (active drivers first, then unassigned)
        List<Employee> sortedDrivers = new ArrayList<>(driverGroups.keySet());
        sortedDrivers.sort((d1, d2) -> {
            if (d1.getId() == -1) return 1; // Unassigned at bottom
            if (d2.getId() == -1) return -1;
            return d1.getName().compareToIgnoreCase(d2.getName());
        });
        
        // Add driver rows to the container
        for (Employee driver : sortedDrivers) {
            VBox driverSection = createCompactDriverRow(driver, driverGroups.get(driver));
            driversContainer.getChildren().add(driverSection);
        }
        
        // Add search functionality
        compactSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            String searchText = newVal.toLowerCase();
            for (int i = 0; i < driversContainer.getChildren().size(); i++) {
                if (driversContainer.getChildren().get(i) instanceof VBox) {
                    VBox driverSection = (VBox) driversContainer.getChildren().get(i);
                    
                    // Get the driver name from the first label in the driver section
                    String driverName = "";
                    for (javafx.scene.Node node : driverSection.getChildren()) {
                        if (node instanceof Label) {
                            driverName = ((Label) node).getText().toLowerCase();
                            break;
                        }
                    }
                    
                    // Filter logic
                    boolean matchesSearch = driverName.contains(searchText);
                    driverSection.setVisible(matchesSearch);
                    driverSection.setManaged(matchesSearch);
                }
            }
        });
        
        // Add status filter functionality
        compactStatusFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            Load.Status selectedStatus = newVal;
            
            for (int i = 0; i < driversContainer.getChildren().size(); i++) {
                if (driversContainer.getChildren().get(i) instanceof VBox) {
                    VBox driverSection = (VBox) driversContainer.getChildren().get(i);
                    
                    if (selectedStatus == null) {
                        // Show all if no status is selected
                        driverSection.setVisible(true);
                        driverSection.setManaged(true);
                    } else {
                        // Get the driver from the user data
                        Employee driver = (Employee) driverSection.getUserData();
                        if (driver != null) {
                            // Check if driver has loads with the selected status
                            boolean hasMatchingLoads = driverGroups.get(driver).stream()
                                .anyMatch(entry -> entry.getLoad().getStatus() == selectedStatus);
                            
                            driverSection.setVisible(hasMatchingLoads);
                            driverSection.setManaged(hasMatchingLoads);
                        }
                    }
                }
            }
        });
        
        // Add components to content
        content.getChildren().addAll(headerLabel, filterBox, scrollPane);
        
        // Set the content and show the dialog
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().addAll(mainLayout.getStylesheets());
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    /**
     * Creates a compact driver row for the compact view dialog
     */
    private VBox createCompactDriverRow(Employee driver, List<LoadScheduleEntry> entries) {
        VBox driverSection = new VBox(5);
        driverSection.getStyleClass().add("driver-grid-compact-row");
        driverSection.setPadding(new Insets(10));
        driverSection.setUserData(driver); // Store driver for filtering
        
        // Driver header
        Label driverLabel = new Label(driver.getName() + (driver.getTruckUnit() != null ? " (" + driver.getTruckUnit() + ")" : ""));
        driverLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        driverLabel.setTextFill(driver.getId() == -1 ? Color.web("#ef4444") : Color.web("#1e293b"));
        
        // Container for load entries
        VBox loadsContainer = new VBox(3);
        loadsContainer.setPadding(new Insets(5, 0, 0, 15)); // Indent loads under driver
        
        // Group loads by day
        Map<LocalDate, List<LoadScheduleEntry>> loadsByDay = entries.stream()
            .collect(Collectors.groupingBy(entry -> {
                LocalDate pickupDate = entry.getLoad().getPickUpDate();
                return pickupDate != null ? pickupDate : LocalDate.now();
            }));
        
        // Sort days
        List<LocalDate> sortedDays = new ArrayList<>(loadsByDay.keySet());
        Collections.sort(sortedDays);
        
        // Add loads for each day
        boolean hasLoads = false;
        for (LocalDate day : sortedDays) {
            if (day.isBefore(weekStart) || day.isAfter(weekStart.plusDays(6))) {
                continue; // Skip days outside current week
            }
            
            List<LoadScheduleEntry> dayLoads = loadsByDay.get(day);
            if (dayLoads.isEmpty()) {
                continue;
            }
            
            hasLoads = true;
            
            // Day header
            HBox dayHeader = new HBox(5);
            dayHeader.setAlignment(Pos.CENTER_LEFT);
            Label dayLabel = new Label(day.format(DateTimeFormatter.ofPattern("EEEE, MMM d")));
            dayLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            dayLabel.setTextFill(Color.web("#4b5563"));
            dayHeader.getChildren().add(dayLabel);
            
            loadsContainer.getChildren().add(dayHeader);
            
            // Add loads for this day
            for (LoadScheduleEntry entry : dayLoads) {
                Load load = entry.getLoad();
                
                HBox loadRow = new HBox(10);
                loadRow.setAlignment(Pos.CENTER_LEFT);
                
                // Status indicator
                Rectangle statusRect = new Rectangle(10, 10);
                statusRect.setFill(Color.web(LoadStatusUtil.colorFor(load.getStatus())));
                statusRect.setArcWidth(5);
                statusRect.setArcHeight(5);
                
                // Load number and customer
                Label loadLabel = new Label(load.getLoadNumber() + " - " + load.getCustomer());
                loadLabel.setFont(Font.font("Segoe UI", 12));
                
                // Times
                String timeInfo = "";
                if (load.getPickUpTime() != null) {
                    timeInfo += load.getPickUpTime().format(TIME_FORMAT);
                }
                if (load.getDeliveryTime() != null) {
                    timeInfo += " → " + load.getDeliveryTime().format(TIME_FORMAT);
                }
                
                Label timeLabel = new Label(timeInfo);
                timeLabel.setFont(Font.font("Segoe UI", 11));
                timeLabel.setTextFill(Color.web("#6b7280"));
                
                // Add components to load row
                loadRow.getChildren().addAll(statusRect, loadLabel);
                if (!timeInfo.isEmpty()) {
                    loadRow.getChildren().add(timeLabel);
                }
                
                // Multi-stop indicator if applicable
                if (load.hasMultipleStops()) {
                    Label multiStopLabel = new Label("📍" + (2 + load.getPickupLocations().size() + load.getDropLocations().size()) + " stops");
                    multiStopLabel.setFont(Font.font("Segoe UI", 11));
                    multiStopLabel.setTextFill(Color.web("#6b7280"));
                    loadRow.getChildren().add(multiStopLabel);
                }
                
                // Add load row to container
                loadsContainer.getChildren().add(loadRow);
            }
        }
        
        // Add components to driver section
        driverSection.getChildren().add(driverLabel);
        
        if (hasLoads) {
            driverSection.getChildren().add(loadsContainer);
        } else {
            Label noLoadsLabel = new Label("No loads scheduled this week");
            noLoadsLabel.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 12));
            noLoadsLabel.setTextFill(Color.web("#9ca3af"));
            noLoadsLabel.setPadding(new Insets(5, 0, 0, 15));
            driverSection.getChildren().add(noLoadsLabel);
        }
        
        return driverSection;
    }

    @Override
    public void updateWindowSize(double width, double height) {
        // Implement WindowAware interface
        logger.debug("Window resized to: {}x{}", width, height);
        applyResponsiveBreakpoint(width);
    }

    @Override
    public void onWindowStateChanged(boolean maximized, boolean minimized) {
        // Handle window state changes (maximized/minimized)
        logger.debug("Window state changed: maximized={}, minimized={}", maximized, minimized);
        
        if (!minimized && mainLayout.getWidth() > 0) {
            // Reapply responsive behavior when restoring from minimized state
            applyResponsiveBreakpoint(mainLayout.getWidth());
        }
    }

    /**
     * Show a dialog with all loads for a particular day/driver
     */
    private void showLoadsList(Employee driver, LocalDate date, List<LoadScheduleEntry> dayLoads) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Loads for " + driver.getName() + " on " + date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setMaxWidth(600);
        content.setMaxHeight(500);
        
        // Header with action buttons
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Button addLoadBtn = new Button("Schedule New Load");
        addLoadBtn.getStyleClass().add("driver-grid-action-button");
        addLoadBtn.setOnAction(e -> {
            dialog.close();
            showScheduleLoadDialog(driver, date);
        });
        
        header.getChildren().add(addLoadBtn);
        
        // Table of loads
        TableView<LoadScheduleEntry> loadTable = new TableView<>();
        loadTable.setMaxHeight(400);
        
        TableColumn<LoadScheduleEntry, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLoad().getLoadNumber()));
        loadNumCol.setPrefWidth(100);
        
        TableColumn<LoadScheduleEntry, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLoad().getCustomer()));
        customerCol.setPrefWidth(150);
        
        TableColumn<LoadScheduleEntry, String> pickupCol = new TableColumn<>("Pickup");
        pickupCol.setCellValueFactory(data -> {
            Load load = data.getValue().getLoad();
            return new SimpleStringProperty(formatLocation(load.getPickUpLocation()));
        });
        pickupCol.setPrefWidth(150);
        
        TableColumn<LoadScheduleEntry, String> deliveryCol = new TableColumn<>("Delivery");
        deliveryCol.setCellValueFactory(data -> {
            Load load = data.getValue().getLoad();
            return new SimpleStringProperty(formatLocation(load.getDropLocation()));
        });
        deliveryCol.setPrefWidth(150);
        
        TableColumn<LoadScheduleEntry, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(100);
        actionsCol.setCellFactory(col -> new TableCell<LoadScheduleEntry, Void>() {
            private final Button editButton = new Button("Edit");
            {
                editButton.getStyleClass().add("driver-grid-small-button");
                editButton.setOnAction(event -> {
                    LoadScheduleEntry entry = getTableView().getItems().get(getIndex());
                    dialog.close();
                    editLoad(entry.getLoad());
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(editButton);
                }
            }
        });
        
        loadTable.getColumns().addAll(loadNumCol, customerCol, pickupCol, deliveryCol, actionsCol);
        loadTable.setItems(FXCollections.observableArrayList(dayLoads));
        
        content.getChildren().addAll(header, loadTable);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().addAll(mainLayout.getStylesheets());
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    private HBox createLoadBar(LoadScheduleEntry entry, LocalDate date) {
        Load load = entry.getLoad();
        Employee driver = entry.getDriver();
        
        HBox bar = new HBox(3); // Reduce spacing for more compact display
        bar.getStyleClass().add("driver-grid-load-bar");
        bar.setPickOnBounds(true); // Allow the bar itself to be clicked
        
        String color = LoadStatusUtil.colorFor(load.getStatus());
        boolean hasConflict = conflictMap.containsKey(driver.getName()) &&
            conflictMap.get(driver.getName()).stream()
                .anyMatch(c -> c.load1.equals(load) || c.load2.equals(load));
        
        if (hasConflict) {
            color = "#FFCDD2"; // Light red for conflicts with black text
        }
        
        bar.setStyle(
            "-fx-background-color: " + color + "; " +
            "-fx-background-radius: 4; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2); " +
            "-fx-cursor: hand;"
        );
        
        // Load info with multi-stop indicator
        Label iconLabel = new Label(LoadStatusUtil.iconFor(load.getStatus()));
        iconLabel.setFont(Font.font("Segoe UI", 11));
        iconLabel.setMouseTransparent(true); // Icons don't need to capture mouse events
        
        // Determine location to show based on date
        boolean isPickupDate = date.equals(load.getPickUpDate());
        boolean isDeliveryDate = date.equals(load.getDeliveryDate());
        
        // Build display text with load number and location info
        String displayText = load.getLoadNumber();
        
        // Add location information - keep it concise
        if (isPickupDate && load.getPickUpLocation() != null) {
            String pickupLocation = formatLocationShort(load.getPickUpLocation());
            if (!pickupLocation.isEmpty()) {
                displayText += " • P: " + pickupLocation;
            }
        } else if (isDeliveryDate && load.getDropLocation() != null) {
            String deliveryLocation = formatLocationShort(load.getDropLocation());
            if (!deliveryLocation.isEmpty()) {
                displayText += " • D: " + deliveryLocation;
            }
        }
        
        Label loadLabel = new Label(displayText);
        loadLabel.getStyleClass().add("driver-grid-load-label");
        loadLabel.setTextFill(Color.BLACK);
        loadLabel.setMaxWidth(200); // Limit width to prevent overflow
        loadLabel.setEllipsisString("...");
        loadLabel.setMouseTransparent(true); // Labels don't need to capture mouse events
        
        // Show time if available
        String timeText = "";
        if (isPickupDate && load.getPickUpTime() != null) {
            timeText = load.getPickUpTime().format(TIME_FORMAT);
        } else if (isDeliveryDate && load.getDeliveryTime() != null) {
            timeText = load.getDeliveryTime().format(TIME_FORMAT);
        }
        
        // Add components to bar
        HBox.setHgrow(loadLabel, Priority.ALWAYS);
        bar.getChildren().addAll(iconLabel, loadLabel);
        
        // Add time label if present
        if (!timeText.isEmpty()) {
            Label timeLabel = new Label(timeText);
            timeLabel.getStyleClass().add("driver-grid-load-time");
            timeLabel.setTextFill(Color.BLACK);
            timeLabel.setFont(Font.font("Segoe UI", 10));
            timeLabel.setMouseTransparent(true); // Labels don't need to capture mouse events
            bar.getChildren().add(timeLabel);
        }
        
        // Multi-stop indicator
        if (load.hasMultipleStops()) {
            int totalStops = 2 + load.getPickupLocations().size() + load.getDropLocations().size();
            Label multiStopLabel = new Label("📍" + totalStops);
            multiStopLabel.getStyleClass().add("driver-grid-multi-stop");
            multiStopLabel.setTextFill(Color.BLACK);
            multiStopLabel.setTooltip(new Tooltip(totalStops + " total stops"));
            multiStopLabel.setMouseTransparent(true); // Labels don't need to capture mouse events
            bar.getChildren().add(multiStopLabel);
        }
        
        // Enhanced tooltip with full location details
        String tooltipText = buildEnhancedTooltip(load, hasConflict);
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(javafx.util.Duration.millis(200));
        tooltip.getStyleClass().add("driver-grid-tooltip");
        Tooltip.install(bar, tooltip);
        
        // Click to edit - ensure it doesn't conflict with cell events
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                editLoad(load);
                e.consume(); // Prevent event from bubbling to cell
            }
        });
        
        // Setup drag
        setupDragSource(bar, entry);
        
        return bar;
    }

    /**
     * Format a location string to extract city and state - shorter version for grid display
     * @param locationString The full location string
     * @return Formatted city, state string (abbreviated)
     */
    private String formatLocationShort(String locationString) {
        if (locationString == null || locationString.trim().isEmpty()) {
            return "";
        }
        
        // Try to extract just the city and state
        String[] parts = locationString.split(",");
        if (parts.length >= 2) {
            // Get city and state
            String city = parts[parts.length - 2].trim();
            String state = parts[parts.length - 1].trim();
            
            // Abbreviate city if too long
            if (city.length() > 10) {
                city = city.substring(0, 8) + "..";
            }
            
            // Use state abbreviation if possible
            if (state.length() > 2) {
                state = state.substring(0, 2).toUpperCase();
            }
            
            return city + ", " + state;
        } else {
            // If can't parse, just return a shortened version
            String shortened = locationString.trim();
            if (shortened.length() > 15) {
                shortened = shortened.substring(0, 12) + "...";
            }
            return shortened;
        }
    }

    /**
     * Format a location string to extract city and state
     * @param locationString The full location string
     * @return Formatted city, state string
     */
    private String formatLocation(String locationString) {
        if (locationString == null || locationString.trim().isEmpty()) {
            return "";
        }
        
        // Try to extract just the city and state
        String[] parts = locationString.split(",");
        if (parts.length >= 2) {
            // Get last two parts which typically contain City, State
            String cityState = parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
            // Limit length to prevent overflow
            if (cityState.length() > 25) {
                cityState = cityState.substring(0, 22) + "...";
            }
            return cityState;
        } else {
            // If can't parse, just return a shortened version
            String shortened = locationString.trim();
            if (shortened.length() > 25) {
                shortened = shortened.substring(0, 22) + "...";
            }
            return shortened;
        }
    }
}