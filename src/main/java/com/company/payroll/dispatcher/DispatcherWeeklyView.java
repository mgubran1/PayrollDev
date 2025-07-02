package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import com.company.payroll.dispatcher.LoadDetailsDialog;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Enhanced Weekly grid view for dispatcher with advanced features
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherWeeklyView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherWeeklyView.class);
    
    // UI Components
    private final DispatcherController controller;
    private final GridPane weekGrid;
    private final DatePicker weekPicker;
    private final Label weekNumberLabel;
    private final ProgressIndicator loadingIndicator;
    private final TableView<WeekSummary> summaryTable;
    
    // Data
    private LocalDate weekStart;
    private final ObservableList<DispatcherDriverStatus> filteredDrivers;
    private final Map<String, DayCell> dayCellCache;
    private final Map<Load, LoadEntry> loadEntryCache;
    
    // Configuration
    private static final int DAY_WIDTH = 180;
    private static final int DRIVER_ROW_HEIGHT = 120;
    private static final int MAX_LOADS_DISPLAY = 5;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE MM/dd");
    private static final DateTimeFormatter FULL_DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d");
    
    // Colors
    private static final Map<Load.Status, Color> STATUS_COLORS = Map.of(
        Load.Status.BOOKED, Color.web("#cfe2ff"),
        Load.Status.ASSIGNED, Color.web("#fff3cd"),
        Load.Status.IN_TRANSIT, Color.web("#d1ecf1"),
        Load.Status.DELIVERED, Color.web("#d4edda"),
        Load.Status.PAID, Color.web("#e2e3e5"),
        Load.Status.CANCELLED, Color.web("#f8d7da")
    );
    
    // View options
    private boolean compactMode = false;
    private boolean showWeekends = true;
    private boolean highlightConflicts = true;
    
    public DispatcherWeeklyView(DispatcherController controller) {
        this.controller = controller;
        this.weekGrid = new GridPane();
        this.weekPicker = new DatePicker(LocalDate.now());
        this.weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        this.weekNumberLabel = new Label();
        this.loadingIndicator = new ProgressIndicator();
        this.summaryTable = new TableView<>();
        this.filteredDrivers = FXCollections.observableArrayList();
        this.dayCellCache = new HashMap<>();
        this.loadEntryCache = new HashMap<>();
        
        initializeUI();
        setupDragAndDrop();
        refresh();
    }
    
    private void initializeUI() {
        getStylesheets().add(getClass().getResource("/styles/dispatcher-weekly.css").toExternalForm());
        getStyleClass().add("weekly-view");
        
        // Main layout with split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setDividerPositions(0.75);
        
        // Top section - week grid
        VBox topSection = new VBox();
        topSection.getChildren().addAll(createToolbar(), createWeekContent());
        
        // Bottom section - summary
        VBox bottomSection = createSummarySection();
        
        splitPane.getItems().addAll(topSection, bottomSection);
        setCenter(splitPane);
        
        // Status bar
        setBottom(createStatusBar());
    }
    
    private VBox createToolbar() {
        VBox toolbarContainer = new VBox();
        
        // Main toolbar
        HBox mainToolbar = new HBox(15);
        mainToolbar.setPadding(new Insets(10));
        mainToolbar.setAlignment(Pos.CENTER_LEFT);
        mainToolbar.getStyleClass().add("main-toolbar");
        
        // Week navigation
        Label weekLabel = new Label("Week:");
        weekLabel.getStyleClass().add("toolbar-label");
        
        weekPicker.setPrefWidth(140);
        updateWeekNumber();
        
        Button prevWeekBtn = createStyledButton("◀ Previous", "Navigate to previous week", e -> navigateWeek(-1));
        Button nextWeekBtn = createStyledButton("Next ▶", "Navigate to next week", e -> navigateWeek(1));
        Button currentWeekBtn = createStyledButton("Current Week", "Go to current week", e -> navigateToCurrentWeek());
        
        // View options
        ToggleButton compactToggle = new ToggleButton("Compact");
        compactToggle.setTooltip(new Tooltip("Toggle compact view"));
        compactToggle.setSelected(compactMode);
        compactToggle.setOnAction(e -> {
            compactMode = compactToggle.isSelected();
            refresh();
        });
        
        CheckBox weekendsCheck = new CheckBox("Show Weekends");
        weekendsCheck.setSelected(showWeekends);
        weekendsCheck.setOnAction(e -> {
            showWeekends = weekendsCheck.isSelected();
            refresh();
        });
        
        CheckBox conflictsCheck = new CheckBox("Highlight Conflicts");
        conflictsCheck.setSelected(highlightConflicts);
        conflictsCheck.setOnAction(e -> {
            highlightConflicts = conflictsCheck.isSelected();
            refresh();
        });
        
        // Actions
        Button refreshBtn = createStyledButton("🔄 Refresh", "Refresh data", e -> refresh());
        Button printBtn = createStyledButton("🖨 Print", "Print weekly view", e -> printWeeklyView());
        Button exportBtn = createStyledButton("📊 Export", "Export to CSV", e -> exportToCSV());
        
        mainToolbar.getChildren().addAll(
            weekLabel, weekPicker, weekNumberLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            prevWeekBtn, currentWeekBtn, nextWeekBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            compactToggle, weekendsCheck, conflictsCheck,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            refreshBtn, printBtn, exportBtn
        );
        
        // Filter toolbar
        HBox filterToolbar = createFilterToolbar();
        
        toolbarContainer.getChildren().addAll(mainToolbar, filterToolbar);
        return toolbarContainer;
    }
    
    private HBox createFilterToolbar() {
        HBox filterBar = new HBox(10);
        filterBar.setPadding(new Insets(5, 10, 10, 10));
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("filter-toolbar");
        
        // Search field
        TextField searchField = new TextField();
        searchField.setPromptText("Search drivers or loads...");
        searchField.setPrefWidth(250);
        searchField.textProperty().addListener((obs, old, text) -> applyFilters());
        
        // Customer filter
        ComboBox<String> customerFilter = new ComboBox<>();
        customerFilter.setPromptText("All Customers");
        customerFilter.setPrefWidth(200);
        loadCustomers(customerFilter);
        customerFilter.setOnAction(e -> applyFilters());
        
        // Load status filter
        ComboBox<Load.Status> statusFilter = new ComboBox<>();
        statusFilter.setPromptText("All Statuses");
        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(Load.Status.values());
        statusFilter.setPrefWidth(150);
        statusFilter.setOnAction(e -> applyFilters());
        
        // Driver group filter
        ComboBox<String> groupFilter = new ComboBox<>();
        groupFilter.setPromptText("All Groups");
        groupFilter.getItems().addAll("All Drivers", "Owner Operators", "Company Drivers", "Lease Operators");
        groupFilter.setValue("All Drivers");
        groupFilter.setPrefWidth(150);
        groupFilter.setOnAction(e -> applyFilters());
        
        // Clear filters button
        Button clearBtn = new Button("Clear Filters");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            customerFilter.setValue(null);
            statusFilter.setValue(null);
            groupFilter.setValue("All Drivers");
            applyFilters();
        });
        
        filterBar.getChildren().addAll(
            new Label("Filter:"),
            searchField,
            customerFilter,
            statusFilter,
            groupFilter,
            clearBtn
        );
        
        return filterBar;
    }
    
    private Node createWeekContent() {
        ScrollPane scrollPane = new ScrollPane(weekGrid);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("week-scroll-pane");
        
        weekGrid.getStyleClass().add("week-grid");
        weekGrid.setHgap(1);
        weekGrid.setVgap(1);
        
        // Loading overlay
        StackPane contentStack = new StackPane(scrollPane);
        loadingIndicator.setMaxSize(100, 100);
        loadingIndicator.setVisible(false);
        contentStack.getChildren().add(loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);
        
        return contentStack;
    }
    
    private VBox createSummarySection() {
        VBox summarySection = new VBox(10);
        summarySection.setPadding(new Insets(10));
        summarySection.getStyleClass().add("summary-section");
        
        Label summaryTitle = new Label("Week Summary");
        summaryTitle.getStyleClass().add("summary-title");
        
        // Configure summary table
        configureSummaryTable();
        
        // Summary statistics
        HBox statsBox = createSummaryStats();
        
        summarySection.getChildren().addAll(summaryTitle, statsBox, summaryTable);
        VBox.setVgrow(summaryTable, Priority.ALWAYS);
        
        return summarySection;
    }
    
    private void configureSummaryTable() {
        TableColumn<WeekSummary, String> metricCol = new TableColumn<>("Metric");
        metricCol.setCellValueFactory(new PropertyValueFactory<>("metric"));
        metricCol.setPrefWidth(200);
        
        // Day columns
        List<TableColumn<WeekSummary, String>> dayColumns = new ArrayList<>();
        DayOfWeek[] days = showWeekends ? 
            DayOfWeek.values() : 
            new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                           DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
        
        for (DayOfWeek day : days) {
            TableColumn<WeekSummary, String> dayCol = new TableColumn<>(
                day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            );
            dayCol.setCellValueFactory(param -> {
                WeekSummary summary = param.getValue();
                return new SimpleStringProperty(summary.getDayValue(day));
            });
            dayCol.setPrefWidth(80);
            dayCol.getStyleClass().add("day-column");
            dayColumns.add(dayCol);
        }
        
        TableColumn<WeekSummary, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("total"));
        totalCol.setPrefWidth(100);
        totalCol.getStyleClass().add("total-column");
        
        summaryTable.getColumns().clear();
        summaryTable.getColumns().add(metricCol);
        summaryTable.getColumns().addAll(dayColumns);
        summaryTable.getColumns().add(totalCol);
        
        summaryTable.setRowFactory(tv -> {
            TableRow<WeekSummary> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null && newItem.isHighlight()) {
                    row.getStyleClass().add("highlighted-row");
                } else {
                    row.getStyleClass().remove("highlighted-row");
                }
            });
            return row;
        });
    }
    
    private HBox createSummaryStats() {
        HBox statsBox = new HBox(30);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(10));
        statsBox.getStyleClass().add("stats-box");
        
        // Calculate statistics
        int totalLoads = 0;
        int totalMiles = 0;
        double totalRevenue = 0;
        Set<String> activeDrivers = new HashSet<>();
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            List<Load> weekLoads = driver.getAssignedLoads().stream()
                .filter(this::isLoadInWeek)
                .collect(Collectors.toList());
            
            if (!weekLoads.isEmpty()) {
                activeDrivers.add(driver.getDriverName());
                totalLoads += weekLoads.size();
                for (Load load : weekLoads) {
                    totalRevenue += load.getGrossAmount();
                    // totalMiles += load.getMiles(); // If miles tracking is available
                }
            }
        }
        
        VBox driversBox = createStatBox("Active Drivers", String.valueOf(activeDrivers.size()), "👥");
        VBox loadsBox = createStatBox("Total Loads", String.valueOf(totalLoads), "📦");
        VBox revenueBox = createStatBox("Total Revenue", String.format("$%,.2f", totalRevenue), "💰");
        VBox milesBox = createStatBox("Total Miles", String.format("%,d", totalMiles), "🛣");
        
        statsBox.getChildren().addAll(driversBox, loadsBox, revenueBox, milesBox);
        
        return statsBox;
    }
    
    private VBox createStatBox(String label, String value, String icon) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("stat-box");
        
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("stat-icon");
        
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("stat-label");
        
        box.getChildren().addAll(iconLabel, valueLabel, nameLabel);
        
        // Add hover effect
        box.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), box);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });
        
        box.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), box);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        return box;
    }
    
    // Continued in next message...
	    // ... continuing from previous section ...
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        // Week range label
        Label weekRangeLabel = new Label();
        updateWeekRangeLabel(weekRangeLabel);
        
        // Driver count
        Label driverCountLabel = new Label();
        
        // Load distribution
        Label loadDistLabel = new Label();
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Last update time
        Label lastUpdateLabel = new Label();
        updateLastRefreshTime(lastUpdateLabel);
        
        statusBar.getChildren().addAll(
            weekRangeLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            driverCountLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            loadDistLabel,
            spacer,
            lastUpdateLabel
        );
        
        return statusBar;
    }
    
    private Button createStyledButton(String text, String tooltip, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(handler);
        button.getStyleClass().add("toolbar-button");
        button.setCursor(Cursor.HAND);
        return button;
    }
    
    private void setupDragAndDrop() {
        // Enable drag and drop for load reassignment between days
        weekGrid.setOnDragOver(event -> {
            if (event.getGestureSource() != weekGrid && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });
    }
    
    private void refresh() {
        logger.info("Refreshing weekly view for week starting: {}", weekStart);
        
        loadingIndicator.setVisible(true);
        
        Task<Void> refreshTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Refresh data
                controller.refreshAll();
                
                // Apply filters
                applyFilters();
                
                // Build grid on FX thread
                javafx.application.Platform.runLater(() -> {
                    weekGrid.getChildren().clear();
                    weekGrid.getColumnConstraints().clear();
                    weekGrid.getRowConstraints().clear();
                    dayCellCache.clear();
                    loadEntryCache.clear();
                    
                    buildWeekGrid();
                    updateSummaryTable();
                    updateStatusBar();
                });
                
                return null;
            }
            
            @Override
            protected void succeeded() {
                loadingIndicator.setVisible(false);
                logger.info("Weekly view refresh completed");
            }
            
            @Override
            protected void failed() {
                loadingIndicator.setVisible(false);
                logger.error("Failed to refresh weekly view", getException());
                showErrorAlert("Failed to refresh weekly view", getException().getMessage());
            }
        };
        
        new Thread(refreshTask).start();
    }
    
    private void buildWeekGrid() {
        // Column constraints
        // Driver column
        ColumnConstraints driverCol = new ColumnConstraints(150);
        driverCol.setHgrow(Priority.NEVER);
        weekGrid.getColumnConstraints().add(driverCol);
        
        // Day columns
        int numDays = showWeekends ? 7 : 5;
        for (int i = 0; i < numDays; i++) {
            ColumnConstraints dayCol = new ColumnConstraints(DAY_WIDTH);
            dayCol.setHgrow(Priority.ALWAYS);
            weekGrid.getColumnConstraints().add(dayCol);
        }
        
        // Row constraints
        RowConstraints headerRow = new RowConstraints(50);
        headerRow.setVgrow(Priority.NEVER);
        weekGrid.getRowConstraints().add(headerRow);
        
        // Build header
        buildWeekHeader();
        
        // Build driver rows
        int rowIndex = 1;
        for (DispatcherDriverStatus driver : filteredDrivers) {
            if (hasLoadsInWeek(driver) || !compactMode) {
                RowConstraints driverRow = new RowConstraints(
                    compactMode ? DRIVER_ROW_HEIGHT - 20 : DRIVER_ROW_HEIGHT
                );
                driverRow.setVgrow(Priority.NEVER);
                weekGrid.getRowConstraints().add(driverRow);
                
                buildDriverWeekRow(driver, rowIndex++);
            }
        }
        
        // Add spacing row
        RowConstraints spacerRow = new RowConstraints(20);
        weekGrid.getRowConstraints().add(spacerRow);
        rowIndex++;
        
        // Build daily summary row
        buildDailySummaryRow(rowIndex);
    }
    
    private void buildWeekHeader() {
        // Corner cell
        VBox cornerCell = new VBox(3);
        cornerCell.setAlignment(Pos.CENTER);
        cornerCell.getStyleClass().add("week-corner-cell");
        cornerCell.setPadding(new Insets(5));
        
        Label weekLabel = new Label("Week " + getWeekNumber());
        weekLabel.getStyleClass().add("week-number-label");
        
        Label rangeLabel = new Label(formatWeekRange());
        rangeLabel.getStyleClass().add("week-range-label");
        
        cornerCell.getChildren().addAll(weekLabel, rangeLabel);
        weekGrid.add(cornerCell, 0, 0);
        
        // Day headers
        int colIndex = 1;
        for (int i = 0; i < (showWeekends ? 7 : 5); i++) {
            LocalDate date = weekStart.plusDays(showWeekends ? i : getWeekdayOffset(i));
            VBox dayHeader = createDayHeader(date);
            weekGrid.add(dayHeader, colIndex++, 0);
        }
    }
    
    private VBox createDayHeader(LocalDate date) {
        VBox dayHeader = new VBox(3);
        dayHeader.setAlignment(Pos.CENTER);
        dayHeader.setPadding(new Insets(5));
        dayHeader.getStyleClass().add("day-header-cell");
        
        // Day name
        Label dayNameLabel = new Label(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        dayNameLabel.getStyleClass().add("day-name-label");
        
        // Date
        Label dateLabel = new Label(date.format(DateTimeFormatter.ofPattern("MMM d")));
        dateLabel.getStyleClass().add("date-label");
        
        // Load count for the day
        int loadCount = countLoadsForDay(date);
        Label countLabel = new Label(loadCount + " loads");
        countLabel.getStyleClass().add("day-load-count");
        
        dayHeader.getChildren().addAll(dayNameLabel, dateLabel, countLabel);
        
        // Highlight today
        if (date.equals(LocalDate.now())) {
            dayHeader.getStyleClass().add("today-header");
        }
        
        // Weekend styling
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dayHeader.getStyleClass().add("weekend-header");
        }
        
        return dayHeader;
    }
    
    private void buildDriverWeekRow(DispatcherDriverStatus driver, int rowIndex) {
        // Driver info cell
        VBox driverCell = createDriverWeekCell(driver);
        weekGrid.add(driverCell, 0, rowIndex);
        
        // Day cells
        int colIndex = 1;
        for (int dayOffset = 0; dayOffset < (showWeekends ? 7 : 5); dayOffset++) {
            LocalDate date = weekStart.plusDays(showWeekends ? dayOffset : getWeekdayOffset(dayOffset));
            DayCell dayCell = createEnhancedDayCell(driver, date);
            weekGrid.add(dayCell, colIndex++, rowIndex);
            
            // Cache the cell
            String key = driver.getDriverName() + "_" + date;
            dayCellCache.put(key, dayCell);
        }
    }
    
    private VBox createDriverWeekCell(DispatcherDriverStatus driver) {
        VBox driverCell = new VBox(3);
        driverCell.setPadding(new Insets(5));
        driverCell.getStyleClass().add("driver-week-cell");
        driverCell.setPrefHeight(compactMode ? DRIVER_ROW_HEIGHT - 20 : DRIVER_ROW_HEIGHT);
        
        // Driver name with status
        HBox nameBox = new HBox(5);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        
        Circle statusDot = new Circle(4);
        statusDot.setFill(Color.web(driver.getStatus().getColor()));
        
        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.getStyleClass().add("driver-name");
        
        nameBox.getChildren().addAll(statusDot, nameLabel);
        
        // Vehicle info
        Label vehicleLabel = new Label(driver.getTruckUnit());
        vehicleLabel.getStyleClass().add("vehicle-info");
        
        // Week statistics
        WeekStats stats = calculateWeekStats(driver);
        
        Label statsLabel = new Label(String.format("%d loads • %.0f hrs", 
            stats.loadCount, stats.totalHours));
        statsLabel.getStyleClass().add("week-stats");
        
        // Revenue if available
        if (stats.totalRevenue > 0) {
            Label revenueLabel = new Label(String.format("$%,.0f", stats.totalRevenue));
            revenueLabel.getStyleClass().add("revenue-label");
            driverCell.getChildren().addAll(nameBox, vehicleLabel, statsLabel, revenueLabel);
        } else {
            driverCell.getChildren().addAll(nameBox, vehicleLabel, statsLabel);
        }
        
        // Progress bar for utilization
        if (!compactMode) {
            ProgressBar utilizationBar = new ProgressBar(stats.utilization);
            utilizationBar.setPrefWidth(130);
            utilizationBar.getStyleClass().add("utilization-bar");
            
            Label utilizationLabel = new Label(String.format("%.0f%% utilized", stats.utilization * 100));
            utilizationLabel.getStyleClass().add("utilization-label");
            
            driverCell.getChildren().addAll(utilizationBar, utilizationLabel);
        }
        
        // Context menu
        ContextMenu contextMenu = createDriverWeekContextMenu(driver);
        driverCell.setOnContextMenuRequested(e -> 
            contextMenu.show(driverCell, e.getScreenX(), e.getScreenY())
        );
        
        // Click handler
        driverCell.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showDriverWeekDetails(driver);
            }
        });
        
        return driverCell;
    }
    
    private DayCell createEnhancedDayCell(DispatcherDriverStatus driver, LocalDate date) {
        DayCell cell = new DayCell(driver, date);
        cell.setPrefHeight(compactMode ? DRIVER_ROW_HEIGHT - 20 : DRIVER_ROW_HEIGHT);
        cell.getStyleClass().add("day-cell");
        
        // Get loads for this day
        List<Load> dayLoads = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, date))
            .sorted(Comparator.comparing(this::getLoadSortTime))
            .collect(Collectors.toList());
        
        // Create load entries
        VBox loadContainer = new VBox(2);
        loadContainer.setPadding(new Insets(2));
        
        int displayCount = Math.min(dayLoads.size(), compactMode ? 3 : MAX_LOADS_DISPLAY);
        for (int i = 0; i < displayCount; i++) {
            Load load = dayLoads.get(i);
            LoadEntry entry = createEnhancedLoadEntry(load, date);
            loadContainer.getChildren().add(entry);
            loadEntryCache.put(load, entry);
        }
        
        // More indicator
        if (dayLoads.size() > displayCount) {
            Label moreLabel = new Label(String.format("+%d more", dayLoads.size() - displayCount));
            moreLabel.getStyleClass().add("more-loads-label");
            moreLabel.setOnMouseClicked(e -> showAllDayLoads(driver, date, dayLoads));
            moreLabel.setCursor(Cursor.HAND);
            loadContainer.getChildren().add(moreLabel);
        }
        
        cell.setContent(loadContainer);
        
        // Styling
        if (date.equals(LocalDate.now())) {
            cell.getStyleClass().add("today-cell");
        }
        
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            cell.getStyleClass().add("weekend-cell");
        }
        
        // Highlight conflicts
        if (highlightConflicts && hasConflictsOnDay(driver, date)) {
            cell.getStyleClass().add("conflict-cell");
        }
        
        // Drop target for load reassignment
        cell.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                cell.getStyleClass().add("drag-over");
            }
            e.consume();
        });
        
        cell.setOnDragExited(e -> {
            cell.getStyleClass().remove("drag-over");
        });
        
        cell.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String loadId = db.getString();
                success = handleLoadReassignment(loadId, driver, date);
            }
            e.setDropCompleted(success);
            e.consume();
        });
        
        return cell;
    }
    
    private LoadEntry createEnhancedLoadEntry(Load load, LocalDate date) {
        LoadEntry entry = new LoadEntry(load, date);
        entry.getStyleClass().add("load-entry");
        
        // Determine load type for the day
        LoadDayType dayType = determineLoadDayType(load, date);
        
        // Icon and styling based on type
        String icon = dayType.getIcon();
        Color bgColor = STATUS_COLORS.getOrDefault(load.getStatus(), Color.LIGHTGRAY);
        
        entry.setStyle(String.format(
            "-fx-background-color: %s; -fx-padding: 2 4 2 4; -fx-background-radius: 3;",
            toRGBCode(bgColor)
        ));
        
        // Content
        HBox content = new HBox(3);
        content.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("load-icon");
        
        Label loadNumber = new Label(load.getLoadNumber());
        loadNumber.getStyleClass().add("load-number");
        
        // Time if applicable
        String timeStr = getLoadTimeForDay(load, date);
        if (timeStr != null) {
            Label timeLabel = new Label(timeStr);
            timeLabel.getStyleClass().add("load-time");
            content.getChildren().addAll(iconLabel, loadNumber, timeLabel);
        } else {
            content.getChildren().addAll(iconLabel, loadNumber);
        }
        
        entry.setGraphic(content);
        
        // Tooltip
        Tooltip tooltip = createLoadDayTooltip(load, date, dayType);
        entry.setTooltip(tooltip);
        
        // Event handlers
        setupLoadEntryHandlers(entry, load);
        
        return entry;
    }
    
    // ... Additional helper methods and enhancements continue ...
	    // ... continuing from where I left off ...
    
    private void updateSummaryTable() {
        ObservableList<WeekSummary> summaryData = FXCollections.observableArrayList();
        
        // Load counts by day
        WeekSummary loadCounts = new WeekSummary("Total Loads");
        WeekSummary pickupCounts = new WeekSummary("Pickups");
        WeekSummary deliveryCounts = new WeekSummary("Deliveries");
        WeekSummary activeDrivers = new WeekSummary("Active Drivers");
        WeekSummary revenue = new WeekSummary("Revenue");
        
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            
            int loads = 0, pickups = 0, deliveries = 0;
            Set<String> driversOnDay = new HashSet<>();
            double dayRevenue = 0;
            
            for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
                for (Load load : driver.getAssignedLoads()) {
                    if (isLoadOnDate(load, date)) {
                        loads++;
                        driversOnDay.add(driver.getDriverName());
                        dayRevenue += load.getGrossAmount();
                        
                        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
                            pickups++;
                        }
                        if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
                            deliveries++;
                        }
                    }
                }
            }
            
            loadCounts.setDayValue(dayOfWeek, String.valueOf(loads));
            pickupCounts.setDayValue(dayOfWeek, String.valueOf(pickups));
            deliveryCounts.setDayValue(dayOfWeek, String.valueOf(deliveries));
            activeDrivers.setDayValue(dayOfWeek, String.valueOf(driversOnDay.size()));
            revenue.setDayValue(dayOfWeek, String.format("$%,.0f", dayRevenue));
        }
        
        // Calculate totals
        loadCounts.calculateTotal();
        pickupCounts.calculateTotal();
        deliveryCounts.calculateTotal();
        activeDrivers.calculateUniqueTotal();
        revenue.calculateRevenueTotal();
        
        summaryData.addAll(loadCounts, pickupCounts, deliveryCounts, activeDrivers, revenue);
        
        summaryTable.setItems(summaryData);
    }
    
    private void buildDailySummaryRow(int rowIndex) {
        // Summary label
        Label summaryLabel = new Label("Daily Totals");
        summaryLabel.getStyleClass().add("daily-summary-label");
        summaryLabel.setAlignment(Pos.CENTER);
        weekGrid.add(summaryLabel, 0, rowIndex);
        
        // Daily summaries
        int colIndex = 1;
        for (int dayOffset = 0; dayOffset < (showWeekends ? 7 : 5); dayOffset++) {
            LocalDate date = weekStart.plusDays(showWeekends ? dayOffset : getWeekdayOffset(dayOffset));
            VBox summaryCell = createDailySummaryCell(date);
            weekGrid.add(summaryCell, colIndex++, rowIndex);
        }
    }
    
    private VBox createDailySummaryCell(LocalDate date) {
        VBox summary = new VBox(3);
        summary.setPadding(new Insets(5));
        summary.setAlignment(Pos.CENTER);
        summary.getStyleClass().add("daily-summary-cell");
        
        // Count loads by type
        AtomicInteger pickups = new AtomicInteger(0);
        AtomicInteger deliveries = new AtomicInteger(0);
        AtomicInteger inTransit = new AtomicInteger(0);
        double dayRevenue = 0;
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            for (Load load : driver.getAssignedLoads()) {
                if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
                    pickups.incrementAndGet();
                    dayRevenue += load.getGrossAmount() * 0.5; // Half on pickup
                }
                if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
                    deliveries.incrementAndGet();
                    dayRevenue += load.getGrossAmount() * 0.5; // Half on delivery
                }
                if (isLoadInTransit(load, date)) {
                    inTransit.incrementAndGet();
                }
            }
        }
        
        // Activity summary
        if (pickups.get() > 0 || deliveries.get() > 0) {
            HBox activityBox = new HBox(5);
            activityBox.setAlignment(Pos.CENTER);
            
            if (pickups.get() > 0) {
                Label pickupLabel = new Label("▲" + pickups.get());
                pickupLabel.getStyleClass().add("pickup-count");
                activityBox.getChildren().add(pickupLabel);
            }
            
            if (deliveries.get() > 0) {
                Label deliveryLabel = new Label("▼" + deliveries.get());
                deliveryLabel.getStyleClass().add("delivery-count");
                activityBox.getChildren().add(deliveryLabel);
            }
            
            summary.getChildren().add(activityBox);
        }
        
        if (inTransit.get() > 0) {
            Label transitLabel = new Label("↔" + inTransit.get() + " in transit");
            transitLabel.getStyleClass().add("transit-count");
            summary.getChildren().add(transitLabel);
        }
        
        // Revenue
        if (dayRevenue > 0) {
            Label revenueLabel = new Label(String.format("$%,.0f", dayRevenue));
            revenueLabel.getStyleClass().add("daily-revenue");
            summary.getChildren().add(revenueLabel);
        }
        
        return summary;
    }
    
    // Event handlers
    private void navigateWeek(int weeks) {
        weekStart = weekStart.plusWeeks(weeks);
        weekPicker.setValue(weekStart);
        updateWeekNumber();
        refresh();
    }
    
    private void navigateToCurrentWeek() {
        weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        weekPicker.setValue(weekStart);
        updateWeekNumber();
        refresh();
    }
    
    private void updateWeekNumber() {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear());
        weekNumberLabel.setText("(Week " + weekNumber + ")");
    }
    
    private void printWeeklyView() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getScene().getWindow())) {
            PageLayout pageLayout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.LANDSCAPE, Printer.MarginType.DEFAULT);
            job.getJobSettings().setPageLayout(pageLayout);
            
            boolean success = job.printPage(weekGrid);
            if (success) {
                job.endJob();
            }
        }
    }
    
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Weekly Schedule");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("weekly_schedule_" + weekStart.toString() + ".csv");
        
        java.io.File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                writer.write("Driver,Truck,Status");
                for (int i = 0; i < 7; i++) {
                    LocalDate date = weekStart.plusDays(i);
                    writer.write("," + date.format(DateTimeFormatter.ofPattern("EEE MM/dd")));
                }
                writer.write("\n");
                
                // Write driver data
                for (DispatcherDriverStatus driver : filteredDrivers) {
                    writer.write(String.format("%s,%s,%s",
                        driver.getDriverName(),
                        driver.getTruckUnit(),
                        driver.getStatus().getDisplayName()
                    ));
                    
                    for (int i = 0; i < 7; i++) {
                        LocalDate date = weekStart.plusDays(i);
                        List<Load> dayLoads = driver.getAssignedLoads().stream()
                            .filter(load -> isLoadOnDate(load, date))
                            .collect(Collectors.toList());
                        
                        writer.write(",");
                        if (!dayLoads.isEmpty()) {
                            writer.write(dayLoads.stream()
                                .map(Load::getLoadNumber)
                                .collect(Collectors.joining(";")));
                        }
                    }
                    writer.write("\n");
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Weekly schedule exported successfully to:\n" + file.getAbsolutePath());
                alert.showAndWait();
                
            } catch (IOException e) {
                logger.error("Failed to export CSV", e);
                showErrorAlert("Export Failed", "Failed to export weekly schedule: " + e.getMessage());
            }
        }
    }
    
    // Helper methods
    private boolean isLoadInWeek(Load load) {
        LocalDate weekEnd = weekStart.plusDays(6);
        
        if (load.getPickUpDate() != null) {
            if (!load.getPickUpDate().isBefore(weekStart) && !load.getPickUpDate().isAfter(weekEnd)) {
                return true;
            }
        }
        
        if (load.getDeliveryDate() != null) {
            if (!load.getDeliveryDate().isBefore(weekStart) && !load.getDeliveryDate().isAfter(weekEnd)) {
                return true;
            }
        }
        
        // Check if load spans the week
        if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
            return !load.getDeliveryDate().isBefore(weekStart) && !load.getPickUpDate().isAfter(weekEnd);
        }
        
        return false;
    }
    
    private boolean isLoadOnDate(Load load, LocalDate date) {
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
            return true;
        }
        if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
            return true;
        }
        return isLoadInTransit(load, date);
    }
    
    private boolean isLoadInTransit(Load load, LocalDate date) {
        if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
            return date.isAfter(load.getPickUpDate()) && date.isBefore(load.getDeliveryDate());
        }
        return false;
    }
    
    private LoadDayType determineLoadDayType(Load load, LocalDate date) {
        boolean isPickup = load.getPickUpDate() != null && load.getPickUpDate().equals(date);
        boolean isDelivery = load.getDeliveryDate() != null && load.getDeliveryDate().equals(date);
        
        if (isPickup && isDelivery) {
            return LoadDayType.SAME_DAY;
        } else if (isPickup) {
            return LoadDayType.PICKUP;
        } else if (isDelivery) {
            return LoadDayType.DELIVERY;
        } else {
            return LoadDayType.IN_TRANSIT;
        }
    }
    
    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    // Inner classes
    private static class WeekSummary {
        private final SimpleStringProperty metric;
        private final Map<DayOfWeek, String> dayValues;
        private final SimpleStringProperty total;
        private boolean highlight;
        
        public WeekSummary(String metric) {
            this.metric = new SimpleStringProperty(metric);
            this.dayValues = new HashMap<>();
            this.total = new SimpleStringProperty("0");
            this.highlight = false;
        }
        
        public void setDayValue(DayOfWeek day, String value) {
            dayValues.put(day, value);
        }
        
        public String getDayValue(DayOfWeek day) {
            return dayValues.getOrDefault(day, "0");
        }
        
        public void calculateTotal() {
            int sum = dayValues.values().stream()
                .mapToInt(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .sum();
            total.set(String.valueOf(sum));
        }
        
        public void calculateRevenueTotal() {
            double sum = dayValues.values().stream()
                .mapToDouble(v -> {
                    try {
                        return Double.parseDouble(v.replace("$", "").replace(",", ""));
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                })
                .sum();
            total.set(String.format("$%,.0f", sum));
        }
        
        public void calculateUniqueTotal() {
            // For drivers, we need unique count across the week
            total.set(String.valueOf(dayValues.size()));
        }
        
        // Property getters
        public String getMetric() { return metric.get(); }
        public String getTotal() { return total.get(); }
        public boolean isHighlight() { return highlight; }
    }
    
    private static class WeekStats {
        int loadCount;
        double totalHours;
        double totalRevenue;
        double utilization;
    }
    
    private WeekStats calculateWeekStats(DispatcherDriverStatus driver) {
        WeekStats stats = new WeekStats();
        
        List<Load> weekLoads = driver.getAssignedLoads().stream()
            .filter(this::isLoadInWeek)
            .collect(Collectors.toList());
        
        stats.loadCount = weekLoads.size();
        stats.totalRevenue = weekLoads.stream()
            .mapToDouble(Load::getGrossAmount)
            .sum();
        
        // Calculate hours (simplified - would need actual time tracking)
        stats.totalHours = weekLoads.size() * 8.0; // Assume 8 hours per load
        stats.utilization = Math.min(stats.totalHours / 40.0, 1.0); // 40 hour week
        
        return stats;
    }
    
    private static class DayCell extends VBox {
        private final DispatcherDriverStatus driver;
        private final LocalDate date;
        
        public DayCell(DispatcherDriverStatus driver, LocalDate date) {
            this.driver = driver;
            this.date = date;
            setPadding(new Insets(2));
        }
        
        public void setContent(Node content) {
            getChildren().clear();
            getChildren().add(content);
        }
    }
    
    private static class LoadEntry extends Label {
        private final Load load;
        private final LocalDate date;
        
        public LoadEntry(Load load, LocalDate date) {
            this.load = load;
            this.date = date;
        }
    }
    
    private enum LoadDayType {
        PICKUP("▲", "Pickup"),
        DELIVERY("▼", "Delivery"),
        IN_TRANSIT("↔", "In Transit"),
        SAME_DAY("⇅", "Same Day");
        
        private final String icon;
        private final String description;
        
        LoadDayType(String icon, String description) {
            this.icon = icon;
            this.description = description;
        }
        
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
    }
}