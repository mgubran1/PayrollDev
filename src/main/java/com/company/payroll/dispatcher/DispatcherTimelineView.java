package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced Timeline view showing 12 or 24 hour grid with advanced features
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherTimelineView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherTimelineView.class);
    
    public enum TimelineMode {
        TWELVE_HOUR(12, 7, 19, "12-Hour View (7AM-7PM)"),
        TWENTY_FOUR_HOUR(24, 0, 24, "24-Hour View (Full Day)"),
        CUSTOM(0, 0, 0, "Custom Hours");
        
        private final int hours;
        private final int startHour;
        private final int endHour;
        private final String displayName;
        
        TimelineMode(int hours, int startHour, int endHour, String displayName) {
            this.hours = hours;
            this.startHour = startHour;
            this.endHour = endHour;
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    // UI Components
    private final DispatcherController controller;
    private final TimelineMode mode;
    private final ScrollPane scrollPane;
    private final GridPane timelineGrid;
    private final DatePicker datePicker;
    private final Label statusLabel;
    private final ProgressBar loadingProgress;
    private final CheckBox autoRefreshCheck;
    private final ComboBox<TimelineMode> modeSelector;
    
    // Data
    private LocalDate selectedDate;
    private final ObservableList<DispatcherDriverStatus> filteredDrivers;
    private final Map<String, Pane> loadBlockCache;
    private Timeline autoRefreshTimeline;
    private Timeline currentTimeIndicator;
    
    // Configuration
    private static final int HOUR_WIDTH = 120;
    private static final int DRIVER_ROW_HEIGHT = 80;
    private static final int HEADER_HEIGHT = 40;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("ha");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int AUTO_REFRESH_INTERVAL = 30; // seconds
    
    // Colors
    private static final Color CURRENT_HOUR_COLOR = Color.web("#fff3cd");
    private static final Color WEEKEND_COLOR = Color.web("#f8f9fa");
    private static final Color GRID_LINE_COLOR = Color.web("#dee2e6");
    private static final Color HEADER_BG_COLOR = Color.web("#343a40");
    private static final Color HEADER_TEXT_COLOR = Color.WHITE;
    
    public DispatcherTimelineView(DispatcherController controller, TimelineMode mode) {
        this.controller = controller;
        this.mode = mode;
        this.scrollPane = new ScrollPane();
        this.timelineGrid = new GridPane();
        this.datePicker = new DatePicker(LocalDate.now());
        this.selectedDate = LocalDate.now();
        this.statusLabel = new Label("Ready");
        this.loadingProgress = new ProgressBar();
        this.autoRefreshCheck = new CheckBox("Auto-refresh");
        this.modeSelector = new ComboBox<>();
        this.filteredDrivers = FXCollections.observableArrayList();
        this.loadBlockCache = new ConcurrentHashMap<>();
        
        initializeUI();
        setupEventHandlers();
        startTimers();
        refresh();
    }
    
    private void initializeUI() {
        // Apply modern styling
        getStylesheets().add(getClass().getResource("/styles/dispatcher-timeline.css").toExternalForm());
        getStyleClass().add("timeline-view");
        
        // Top toolbar with enhanced controls
        VBox topSection = new VBox();
        topSection.getChildren().addAll(createMainToolbar(), createFilterToolbar());
        setTop(topSection);
        
        // Main timeline grid with smooth scrolling
        configureScrollPane();
        configureTimelineGrid();
        
        // Status bar at bottom
        HBox statusBar = createStatusBar();
        setBottom(statusBar);
        
        // Loading overlay
        loadingProgress.setVisible(false);
        loadingProgress.setPrefWidth(200);
        
        StackPane centerStack = new StackPane(scrollPane, loadingProgress);
        StackPane.setAlignment(loadingProgress, Pos.CENTER);
        setCenter(centerStack);
    }
    
    private HBox createMainToolbar() {
        HBox toolbar = new HBox(15);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("main-toolbar");
        
        // Date navigation
        Label dateLabel = new Label("Date:");
        dateLabel.getStyleClass().add("toolbar-label");
        
        datePicker.setPrefWidth(140);
        datePicker.setConverter(new javafx.util.StringConverter<LocalDate>() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy");
            @Override
            public String toString(LocalDate date) {
                return date != null ? formatter.format(date) : "";
            }
            @Override
            public LocalDate fromString(String string) {
                return string != null && !string.isEmpty() ? LocalDate.parse(string, formatter) : null;
            }
        });
        
        Button prevBtn = createIconButton("◀", "Previous Day", e -> navigateDate(-1));
        Button todayBtn = createIconButton("⌂", "Today", e -> navigateToday());
        Button nextBtn = createIconButton("▶", "Next Day", e -> navigateDate(1));
        
        // Week navigation
        Button prevWeekBtn = createIconButton("◀◀", "Previous Week", e -> navigateDate(-7));
        Button nextWeekBtn = createIconButton("▶▶", "Next Week", e -> navigateDate(7));
        
        // Mode selector
        modeSelector.getItems().addAll(TimelineMode.values());
        modeSelector.setValue(mode);
        modeSelector.setPrefWidth(180);
        
        // Refresh controls
        Button refreshBtn = createIconButton("⟳", "Refresh Now", e -> refresh());
        autoRefreshCheck.setSelected(true);
        autoRefreshCheck.setTooltip(new Tooltip("Auto-refresh every " + AUTO_REFRESH_INTERVAL + " seconds"));
        
        // Export button
        Button exportBtn = createIconButton("📤", "Export Timeline", e -> exportTimeline());
        
        toolbar.getChildren().addAll(
            dateLabel, datePicker, 
            new Separator(javafx.geometry.Orientation.VERTICAL),
            prevWeekBtn, prevBtn, todayBtn, nextBtn, nextWeekBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            new Label("View Mode:"), modeSelector,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            refreshBtn, autoRefreshCheck, exportBtn
        );
        
        return toolbar;
    }
    
    private HBox createFilterToolbar() {
        HBox filterBar = new HBox(10);
        filterBar.setPadding(new Insets(5, 10, 10, 10));
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("filter-toolbar");
        
        // Driver filter
        TextField driverSearchField = new TextField();
        driverSearchField.setPromptText("Search drivers...");
        driverSearchField.setPrefWidth(200);
        
        // Status filter
        ComboBox<DispatcherDriverStatus.Status> statusFilter = new ComboBox<>();
        statusFilter.setPromptText("All Statuses");
        statusFilter.getItems().add(null); // All option
        statusFilter.getItems().addAll(DispatcherDriverStatus.Status.values());
        statusFilter.setPrefWidth(150);
        
        // Load type filter
        ComboBox<String> loadTypeFilter = new ComboBox<>();
        loadTypeFilter.setPromptText("All Load Types");
        loadTypeFilter.getItems().addAll("All Loads", "Pickups Only", "Deliveries Only", "In Transit");
        loadTypeFilter.setValue("All Loads");
        loadTypeFilter.setPrefWidth(150);
        
        // Quick filters
        ToggleGroup quickFilterGroup = new ToggleGroup();
        RadioButton allDriversRadio = new RadioButton("All Drivers");
        RadioButton activeOnlyRadio = new RadioButton("Active Only");
        RadioButton withLoadsRadio = new RadioButton("With Loads");
        
        allDriversRadio.setToggleGroup(quickFilterGroup);
        activeOnlyRadio.setToggleGroup(quickFilterGroup);
        withLoadsRadio.setToggleGroup(quickFilterGroup);
        allDriversRadio.setSelected(true);
        
        filterBar.getChildren().addAll(
            new Label("Filters:"),
            driverSearchField,
            statusFilter,
            loadTypeFilter,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            allDriversRadio, activeOnlyRadio, withLoadsRadio
        );
        
        // Setup filter handlers
        driverSearchField.textProperty().addListener((obs, old, text) -> applyFilters());
        statusFilter.setOnAction(e -> applyFilters());
        loadTypeFilter.setOnAction(e -> applyFilters());
        quickFilterGroup.selectedToggleProperty().addListener((obs, old, toggle) -> applyFilters());
        
        return filterBar;
    }
    
    private void configureScrollPane() {
        scrollPane.setContent(timelineGrid);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        
        // Smooth scrolling
        scrollPane.getContent().setOnScroll(event -> {
            double deltaY = event.getDeltaY() * 0.003;
            double deltaX = event.getDeltaX() * 0.003;
            scrollPane.setVvalue(scrollPane.getVvalue() - deltaY);
            scrollPane.setHvalue(scrollPane.getHvalue() - deltaX);
            event.consume();
        });
    }
    
    private void configureTimelineGrid() {
        timelineGrid.getStyleClass().add("timeline-grid");
        timelineGrid.setGridLinesVisible(false); // We'll draw custom grid lines
        timelineGrid.setHgap(0);
        timelineGrid.setVgap(0);
        
        // Add custom grid line drawing
        timelineGrid.setStyle("-fx-background-color: white;");
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        // Driver count
        Label driverCountLabel = new Label();
        updateDriverCount(driverCountLabel);
        
        // Load count
        Label loadCountLabel = new Label();
        updateLoadCount(loadCountLabel);
        
        // Last update time
        Label lastUpdateLabel = new Label();
        updateLastRefreshTime(lastUpdateLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusBar.getChildren().addAll(
            statusLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            driverCountLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            loadCountLabel,
            spacer,
            lastUpdateLabel
        );
        
        return statusBar;
    }
    
    private Button createIconButton(String icon, String tooltip, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(handler);
        button.getStyleClass().add("icon-button");
        button.setCursor(Cursor.HAND);
        return button;
    }
    
    private void setupEventHandlers() {
        datePicker.setOnAction(e -> {
            selectedDate = datePicker.getValue();
            refresh();
        });
        
        modeSelector.setOnAction(e -> {
            TimelineMode newMode = modeSelector.getValue();
            if (newMode != null && newMode != mode) {
                // Recreate view with new mode
                logger.info("Switching to {} mode", newMode.getDisplayName());
                refresh();
            }
        });
        
        autoRefreshCheck.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
    }
    
    private void startTimers() {
        // Auto-refresh timer
        if (autoRefreshCheck.isSelected()) {
            startAutoRefresh();
        }
        
        // Current time indicator timer
        currentTimeIndicator = new Timeline(
            new KeyFrame(Duration.seconds(60), e -> updateCurrentTimeIndicator())
        );
        currentTimeIndicator.setCycleCount(Animation.INDEFINITE);
        currentTimeIndicator.play();
    }
    
    private void startAutoRefresh() {
        stopAutoRefresh();
        autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(AUTO_REFRESH_INTERVAL), e -> refresh())
        );
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
        logger.debug("Auto-refresh started");
    }
    
    private void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
            logger.debug("Auto-refresh stopped");
        }
    }
	
	    // ... continuing from previous section ...
    
    private void refresh() {
        logger.info("Refreshing timeline view for date: {}", selectedDate);
        
        // Show loading indicator
        statusLabel.setText("Loading...");
        loadingProgress.setVisible(true);
        
        Task<Void> refreshTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0, 100);
                
                // Clear caches
                loadBlockCache.clear();
                
                updateProgress(25, 100);
                
                // Refresh controller data
                controller.refreshAll();
                
                updateProgress(50, 100);
                
                // Apply filters
                applyFilters();
                
                updateProgress(75, 100);
                
                // Build UI on FX thread
                javafx.application.Platform.runLater(() -> {
                    timelineGrid.getChildren().clear();
                    timelineGrid.getColumnConstraints().clear();
                    timelineGrid.getRowConstraints().clear();
                    
                    buildTimelineGrid();
                    updateCurrentTimeIndicator();
                });
                
                updateProgress(100, 100);
                return null;
            }
            
            @Override
            protected void succeeded() {
                loadingProgress.setVisible(false);
                statusLabel.setText("Ready");
                updateLastRefreshTime(null);
                logger.info("Timeline refresh completed");
            }
            
            @Override
            protected void failed() {
                loadingProgress.setVisible(false);
                statusLabel.setText("Error loading data");
                logger.error("Timeline refresh failed", getException());
                showErrorAlert("Failed to refresh timeline", getException().getMessage());
            }
        };
        
        loadingProgress.progressProperty().bind(refreshTask.progressProperty());
        new Thread(refreshTask).start();
    }
    
    private void buildTimelineGrid() {
        TimelineMode currentMode = modeSelector.getValue();
        int startHour = currentMode.startHour;
        int endHour = currentMode.endHour;
        
        // Add column constraints
        // First column for driver names
        ColumnConstraints driverCol = new ColumnConstraints(150);
        driverCol.setHgrow(Priority.NEVER);
        timelineGrid.getColumnConstraints().add(driverCol);
        
        // Hour columns
        for (int hour = startHour; hour < endHour; hour++) {
            ColumnConstraints hourCol = new ColumnConstraints(HOUR_WIDTH);
            hourCol.setHgrow(Priority.NEVER);
            timelineGrid.getColumnConstraints().add(hourCol);
        }
        
        // Row constraints
        RowConstraints headerRow = new RowConstraints(HEADER_HEIGHT);
        headerRow.setVgrow(Priority.NEVER);
        timelineGrid.getRowConstraints().add(headerRow);
        
        // Add header row
        buildHeaderRow(startHour, endHour);
        
        // Add driver rows
        int rowIndex = 1;
        for (DispatcherDriverStatus driver : filteredDrivers) {
            RowConstraints driverRow = new RowConstraints(DRIVER_ROW_HEIGHT);
            driverRow.setVgrow(Priority.NEVER);
            timelineGrid.getRowConstraints().add(driverRow);
            
            buildDriverRow(driver, rowIndex++, startHour, endHour);
        }
        
        // Add summary row
        buildSummaryRow(rowIndex, startHour, endHour);
    }
    
    private void buildHeaderRow(int startHour, int endHour) {
        // Corner cell with date
        VBox cornerCell = new VBox(2);
        cornerCell.setAlignment(Pos.CENTER);
        cornerCell.getStyleClass().add("header-corner-cell");
        
        Label dateLabel = new Label(selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        dateLabel.getStyleClass().add("header-date-label");
        
        Label dayLabel = new Label(selectedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
        dayLabel.getStyleClass().add("header-day-label");
        
        cornerCell.getChildren().addAll(dateLabel, dayLabel);
        timelineGrid.add(cornerCell, 0, 0);
        
        // Hour headers
        int colIndex = 1;
        for (int hour = startHour; hour < endHour; hour++) {
            VBox hourCell = createHourHeader(hour);
            timelineGrid.add(hourCell, colIndex++, 0);
        }
    }
    
    private VBox createHourHeader(int hour) {
        VBox hourCell = new VBox(2);
        hourCell.setAlignment(Pos.CENTER);
        hourCell.getStyleClass().add("hour-header-cell");
        hourCell.setPrefSize(HOUR_WIDTH, HEADER_HEIGHT);
        
        LocalTime time = LocalTime.of(hour, 0);
        Label hourLabel = new Label(time.format(HOUR_FORMAT));
        hourLabel.getStyleClass().add("hour-label");
        
        // Add period indicator for 12-hour mode
        if (modeSelector.getValue() == TimelineMode.TWELVE_HOUR) {
            Label periodLabel = new Label(hour < 12 ? "Morning" : "Afternoon");
            periodLabel.getStyleClass().add("period-label");
            hourCell.getChildren().addAll(hourLabel, periodLabel);
        } else {
            hourCell.getChildren().add(hourLabel);
        }
        
        // Highlight current hour
        if (selectedDate.equals(LocalDate.now()) && LocalTime.now().getHour() == hour) {
            hourCell.getStyleClass().add("current-hour");
        }
        
        // Add click handler for hour selection
        hourCell.setOnMouseClicked(e -> selectHour(hour));
        hourCell.setCursor(Cursor.HAND);
        
        return hourCell;
    }
    
    private void buildDriverRow(DispatcherDriverStatus driver, int rowIndex, int startHour, int endHour) {
        // Driver info cell
        VBox driverCell = createDriverInfoCell(driver);
        timelineGrid.add(driverCell, 0, rowIndex);
        
        // Timeline cells for each hour
        for (int hour = startHour; hour < endHour; hour++) {
            StackPane hourCell = createHourCell(driver, hour, rowIndex);
            timelineGrid.add(hourCell, hour - startHour + 1, rowIndex);
        }
        
        // Overlay load blocks
        overlayLoadBlocks(driver, rowIndex, startHour, endHour);
    }
    
    private VBox createDriverInfoCell(DispatcherDriverStatus driver) {
        VBox driverCell = new VBox(3);
        driverCell.setPadding(new Insets(5));
        driverCell.getStyleClass().add("driver-info-cell");
        driverCell.setPrefHeight(DRIVER_ROW_HEIGHT);
        
        // Driver name with status indicator
        HBox nameBox = new HBox(5);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        
        Circle statusIndicator = new Circle(4);
        statusIndicator.setFill(Color.web(driver.getStatus().getColor()));
        
        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.getStyleClass().add("driver-name-label");
        
        nameBox.getChildren().addAll(statusIndicator, nameLabel);
        
        // Truck and trailer info
        Label truckLabel = new Label(String.format("🚛 %s", driver.getTruckUnit()));
        truckLabel.getStyleClass().add("vehicle-label");
        
        String trailerText = driver.getTrailerNumber() != null ? 
            driver.getTrailerNumber() : "No trailer";
        Label trailerLabel = new Label(String.format("📦 %s", trailerText));
        trailerLabel.getStyleClass().add("vehicle-label");
        
        // Current status
        Label statusLabel = new Label(driver.getStatus().getDisplayName());
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setStyle("-fx-text-fill: " + driver.getStatus().getColor());
        
        // Load count for the day
        long dayLoadCount = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, selectedDate))
            .count();
        
        Label loadCountLabel = new Label(String.format("%d loads", dayLoadCount));
        loadCountLabel.getStyleClass().add("load-count-label");
        
        driverCell.getChildren().addAll(nameBox, truckLabel, trailerLabel, statusLabel, loadCountLabel);
        
        // Context menu
        ContextMenu contextMenu = createDriverContextMenu(driver);
        driverCell.setOnContextMenuRequested(e -> 
            contextMenu.show(driverCell, e.getScreenX(), e.getScreenY())
        );
        
        // Click handler
        driverCell.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showDriverDetails(driver);
            }
        });
        
        driverCell.setCursor(Cursor.HAND);
        
        return driverCell;
    }
    
    private StackPane createHourCell(DispatcherDriverStatus driver, int hour, int rowIndex) {
        StackPane cell = new StackPane();
        cell.setPrefSize(HOUR_WIDTH, DRIVER_ROW_HEIGHT);
        cell.getStyleClass().add("hour-cell");
        
        // Background rectangle for styling
        Rectangle bg = new Rectangle(HOUR_WIDTH, DRIVER_ROW_HEIGHT);
        bg.setFill(Color.TRANSPARENT);
        bg.setStroke(GRID_LINE_COLOR);
        bg.setStrokeWidth(0.5);
        
        // Highlight current hour
        LocalTime now = LocalTime.now();
        if (selectedDate.equals(LocalDate.now()) && now.getHour() == hour) {
            bg.setFill(CURRENT_HOUR_COLOR);
            bg.setOpacity(0.3);
        }
        
        // Weekend highlighting
        if (selectedDate.getDayOfWeek() == DayOfWeek.SATURDAY || 
            selectedDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            bg.setFill(WEEKEND_COLOR);
            bg.setOpacity(0.2);
        }
        
        // Add availability indicator
        if (isDriverAvailable(driver, hour)) {
            Circle availableIndicator = new Circle(3);
            availableIndicator.setFill(Color.LIGHTGREEN);
            StackPane.setAlignment(availableIndicator, Pos.TOP_RIGHT);
            StackPane.setMargin(availableIndicator, new Insets(5, 5, 0, 0));
            cell.getChildren().add(availableIndicator);
        }
        
        cell.getChildren().add(0, bg);
        
        // Drag over handler for load assignment
        cell.setOnDragOver(e -> {
            if (e.getDragboard().hasString() && isDriverAvailable(driver, hour)) {
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
                // Handle load drop
                String loadId = db.getString();
                success = handleLoadDrop(loadId, driver, hour);
            }
            e.setDropCompleted(success);
            e.consume();
        });
        
        return cell;
    }
    
    private void overlayLoadBlocks(DispatcherDriverStatus driver, int rowIndex, int startHour, int endHour) {
        // Get loads for this driver on selected date
        List<Load> dayLoads = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, selectedDate))
            .sorted(Comparator.comparing(this::getLoadStartTime))
            .collect(Collectors.toList());
        
        for (Load load : dayLoads) {
            Pane loadBlock = createEnhancedLoadBlock(load, driver);
            
            // Calculate position and size
            LocalDateTime startTime = getLoadStartTime(load);
            LocalDateTime endTime = getLoadEndTime(load);
            
            if (startTime != null && endTime != null) {
                double startX = calculateTimePosition(startTime, startHour, endHour);
                double endX = calculateTimePosition(endTime, startHour, endHour);
                double width = Math.max(endX - startX, 60); // Minimum width
                
                loadBlock.setLayoutX(startX);
                loadBlock.setPrefWidth(width);
                
                // Find the appropriate cell and add load block
                int startCellHour = Math.max(startTime.getHour(), startHour);
                int cellColumn = startCellHour - startHour + 1;
                
                if (cellColumn > 0 && cellColumn <= endHour - startHour) {
                    Node cellNode = getNodeFromGridPane(timelineGrid, cellColumn, rowIndex);
                    if (cellNode instanceof StackPane) {
                        StackPane cell = (StackPane) cellNode;
                        cell.getChildren().add(loadBlock);
                        
                        // Adjust position within cell
                        double cellOffset = (startTime.getHour() + startTime.getMinute() / 60.0) - startCellHour;
                        loadBlock.setTranslateX(cellOffset * HOUR_WIDTH);
                    }
                }
                
                // Cache the load block
                loadBlockCache.put(load.getLoadNumber(), loadBlock);
            }
        }
    }
    
    private Pane createEnhancedLoadBlock(Load load, DispatcherDriverStatus driver) {
        VBox block = new VBox(2);
        block.setPadding(new Insets(3));
        block.setPrefHeight(DRIVER_ROW_HEIGHT - 15);
        block.setMaxHeight(DRIVER_ROW_HEIGHT - 15);
        block.getStyleClass().add("load-block");
        
        // Apply status color with gradient
        Color statusColor = STATUS_COLORS.getOrDefault(load.getStatus(), Color.LIGHTGRAY);
        LinearGradient gradient = new LinearGradient(0, 0, 0, 1, true, null,
            new Stop(0, statusColor.brighter()),
            new Stop(1, statusColor)
        );
        
        block.setStyle(String.format(
            "-fx-background-color: linear-gradient(to bottom, %s, %s); " +
            "-fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 4; " +
            "-fx-background-radius: 4; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 1, 1);",
            toRGBCode(statusColor.brighter()),
            toRGBCode(statusColor),
            toRGBCode(statusColor.darker())
        ));
        
        // Load header with number and status icon
        HBox header = new HBox(3);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label statusIcon = new Label(getStatusIcon(load.getStatus()));
        statusIcon.getStyleClass().add("load-status-icon");
        
        Label loadNumLabel = new Label(load.getLoadNumber());
        loadNumLabel.getStyleClass().add("load-number-label");
        loadNumLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
        
        header.getChildren().addAll(statusIcon, loadNumLabel);
        
        // Customer label
        Label customerLabel = new Label(load.getCustomer());
        customerLabel.getStyleClass().add("load-customer-label");
        customerLabel.setStyle("-fx-font-size: 10;");
        
        // Route label with icons
        String routeText = String.format("%s → %s", 
            getCityAbbreviation(load.getPickUpLocation()),
            getCityAbbreviation(load.getDropLocation())
        );
        Label routeLabel = new Label(routeText);
        routeLabel.getStyleClass().add("load-route-label");
        routeLabel.setStyle("-fx-font-size: 9;");
        
        // Time label
        String timeText = formatLoadTime(load);
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("load-time-label");
        timeLabel.setStyle("-fx-font-size: 9; -fx-font-style: italic;");
        
        block.getChildren().addAll(header, customerLabel, routeLabel, timeLabel);
        
        // Add warning indicator if needed
        if (hasTimeConflict(load, driver)) {
            Label warningLabel = new Label("⚠");
            warningLabel.setTextFill(Color.RED);
            warningLabel.setStyle("-fx-font-size: 12;");
            warningLabel.setTooltip(new Tooltip("Time conflict detected"));
            StackPane.setAlignment(warningLabel, Pos.TOP_RIGHT);
            StackPane.setMargin(warningLabel, new Insets(2, 2, 0, 0));
            
            StackPane wrapper = new StackPane(block, warningLabel);
            return wrapper;
        }
        
        // Enhanced tooltip
        Tooltip tooltip = createEnhancedTooltip(load);
        Tooltip.install(block, tooltip);
        
        // Event handlers
        setupLoadBlockHandlers(block, load, driver);
        
        return block;
    }
    
    // Continued in next message...
	    // ... continuing from where I left off ...
    
    private void setupLoadBlockHandlers(VBox block, Load load, DispatcherDriverStatus driver) {
        // Double-click to show details
        block.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showLoadDetails(load);
            } else if (e.getButton() == MouseButton.SECONDARY) {
                showLoadContextMenu(load, driver, e.getScreenX(), e.getScreenY());
            }
        });
        
        // Hover effects
        block.setOnMouseEntered(e -> {
            block.setEffect(new Glow(0.3));
            block.setCursor(Cursor.HAND);
        });
        
        block.setOnMouseExited(e -> {
            block.setEffect(new DropShadow(3, Color.gray(0.3)));
        });
        
        // Drag support for reassignment
        block.setOnDragDetected(e -> {
            Dragboard db = block.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString(load.getLoadNumber());
            db.setContent(content);
            e.consume();
        });
    }
    
    private Tooltip createEnhancedTooltip(Load load) {
        VBox content = new VBox(5);
        content.setPadding(new Insets(10));
        
        // Header
        Label headerLabel = new Label("Load Details");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        
        // Load info grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(3);
        
        int row = 0;
        addTooltipRow(grid, row++, "Load #:", load.getLoadNumber());
        addTooltipRow(grid, row++, "PO #:", load.getPONumber());
        addTooltipRow(grid, row++, "Customer:", load.getCustomer());
        addTooltipRow(grid, row++, "Status:", load.getStatus().toString());
        
        // Separator
        Separator sep1 = new Separator();
        
        // Pickup info
        Label pickupLabel = new Label("Pickup");
        pickupLabel.setStyle("-fx-font-weight: bold;");
        addTooltipRow(grid, row++, "Location:", load.getPickUpLocation());
        if (load.getPickUpDate() != null) {
            String pickupTime = load.getPickUpDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (load.getPickUpTime() != null) {
                pickupTime += " @ " + load.getPickUpTime().format(TIME_FORMAT);
            }
            addTooltipRow(grid, row++, "Time:", pickupTime);
        }
        
        // Delivery info
        Label deliveryLabel = new Label("Delivery");
        deliveryLabel.setStyle("-fx-font-weight: bold;");
        addTooltipRow(grid, row++, "Location:", load.getDropLocation());
        if (load.getDeliveryDate() != null) {
            String deliveryTime = load.getDeliveryDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (load.getDeliveryTime() != null) {
                deliveryTime += " @ " + load.getDeliveryTime().format(TIME_FORMAT);
            }
            addTooltipRow(grid, row++, "Time:", deliveryTime);
        }
        
        // Financial info
        Separator sep2 = new Separator();
        addTooltipRow(grid, row++, "Gross:", String.format("$%,.2f", load.getGrossAmount()));
        
        content.getChildren().addAll(headerLabel, grid);
        
        Tooltip tooltip = new Tooltip();
        tooltip.setGraphic(content);
        tooltip.setShowDelay(Duration.millis(500));
        tooltip.setHideDelay(Duration.seconds(10));
        
        return tooltip;
    }
    
    private void addTooltipRow(GridPane grid, int row, String label, String value) {
        Label lblLabel = new Label(label);
        lblLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10;");
        Label valLabel = new Label(value != null ? value : "N/A");
        valLabel.setStyle("-fx-font-size: 10;");
        grid.add(lblLabel, 0, row);
        grid.add(valLabel, 1, row);
    }
    
    private void buildSummaryRow(int rowIndex, int startHour, int endHour) {
        // Summary label cell
        Label summaryLabel = new Label("Hourly Summary");
        summaryLabel.getStyleClass().add("summary-label");
        summaryLabel.setPrefHeight(40);
        summaryLabel.setAlignment(Pos.CENTER);
        weekGrid.add(summaryLabel, 0, rowIndex);
        
        // Hourly summaries
        for (int hour = startHour; hour < endHour; hour++) {
            VBox summaryCell = createHourlySummary(hour);
            weekGrid.add(summaryCell, hour - startHour + 1, rowIndex);
        }
    }
    
    private VBox createHourlySummary(int hour) {
        VBox summary = new VBox(2);
        summary.setPadding(new Insets(5));
        summary.setAlignment(Pos.CENTER);
        summary.getStyleClass().add("hourly-summary-cell");
        summary.setPrefHeight(40);
        
        // Count loads by type for this hour
        int pickups = 0;
        int deliveries = 0;
        int inTransit = 0;
        
        for (DispatcherDriverStatus driver : filteredDrivers) {
            for (Load load : driver.getAssignedLoads()) {
                if (isLoadInHour(load, selectedDate, hour)) {
                    if (isPickupInHour(load, selectedDate, hour)) {
                        pickups++;
                    }
                    if (isDeliveryInHour(load, selectedDate, hour)) {
                        deliveries++;
                    }
                    if (isInTransitDuringHour(load, selectedDate, hour)) {
                        inTransit++;
                    }
                }
            }
        }
        
        if (pickups > 0 || deliveries > 0) {
            Label activityLabel = new Label(String.format("▲%d ▼%d", pickups, deliveries));
            activityLabel.getStyleClass().add("summary-activity-label");
            summary.getChildren().add(activityLabel);
        }
        
        if (inTransit > 0) {
            Label transitLabel = new Label(String.format("↔%d", inTransit));
            transitLabel.getStyleClass().add("summary-transit-label");
            summary.getChildren().add(transitLabel);
        }
        
        return summary;
    }
    
    // Navigation methods
    private void navigateDate(int days) {
        selectedDate = selectedDate.plusDays(days);
        datePicker.setValue(selectedDate);
        refresh();
    }
    
    private void navigateToday() {
        selectedDate = LocalDate.now();
        datePicker.setValue(selectedDate);
        refresh();
    }
    
    // Filter methods
    private void applyFilters() {
        filteredDrivers.clear();
        
        // Get filter values from UI
        String searchText = getSearchText();
        DispatcherDriverStatus.Status statusFilter = getStatusFilter();
        String loadTypeFilter = getLoadTypeFilter();
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            boolean matches = true;
            
            // Apply search filter
            if (searchText != null && !searchText.isEmpty()) {
                matches = driver.getDriverName().toLowerCase().contains(searchText.toLowerCase()) ||
                         driver.getTruckUnit().toLowerCase().contains(searchText.toLowerCase());
            }
            
            // Apply status filter
            if (matches && statusFilter != null) {
                matches = driver.getStatus() == statusFilter;
            }
            
            // Apply load type filter
            if (matches && !"All Loads".equals(loadTypeFilter)) {
                matches = hasLoadsOfType(driver, loadTypeFilter);
            }
            
            if (matches) {
                filteredDrivers.add(driver);
            }
        }
    }
    
    // Helper methods
    private boolean isDriverAvailable(DispatcherDriverStatus driver, int hour) {
        LocalDateTime checkTime = LocalDateTime.of(selectedDate, LocalTime.of(hour, 0));
        return driver.getAvailabilityWindows().stream()
            .anyMatch(window -> window.contains(checkTime));
    }
    
    private boolean hasTimeConflict(Load load, DispatcherDriverStatus driver) {
        LocalDateTime loadStart = getLoadStartTime(load);
        LocalDateTime loadEnd = getLoadEndTime(load);
        
        if (loadStart == null || loadEnd == null) return false;
        
        return driver.getAssignedLoads().stream()
            .filter(other -> !other.equals(load))
            .anyMatch(other -> {
                LocalDateTime otherStart = getLoadStartTime(other);
                LocalDateTime otherEnd = getLoadEndTime(other);
                return otherStart != null && otherEnd != null &&
                       !loadEnd.isBefore(otherStart) && !otherEnd.isBefore(loadStart);
            });
    }
    
    private String getStatusIcon(Load.Status status) {
        switch (status) {
            case BOOKED: return "📅";
            case ASSIGNED: return "👤";
            case IN_TRANSIT: return "🚚";
            case DELIVERED: return "✅";
            case PAID: return "💰";
            case CANCELLED: return "❌";
            default: return "📦";
        }
    }
    
    private String formatLoadTime(Load load) {
        if (load.getPickUpTime() != null && load.getDeliveryTime() != null) {
            return String.format("%s - %s", 
                load.getPickUpTime().format(TIME_FORMAT),
                load.getDeliveryTime().format(TIME_FORMAT));
        } else if (load.getPickUpTime() != null) {
            return "PU: " + load.getPickUpTime().format(TIME_FORMAT);
        } else if (load.getDeliveryTime() != null) {
            return "DEL: " + load.getDeliveryTime().format(TIME_FORMAT);
        }
        return "";
    }
    
    private LocalDateTime getLoadStartTime(Load load) {
        if (load.getPickUpDate() != null) {
            LocalTime time = load.getPickUpTime() != null ? load.getPickUpTime() : LocalTime.of(8, 0);
            return LocalDateTime.of(load.getPickUpDate(), time);
        }
        return null;
    }
    
    private LocalDateTime getLoadEndTime(Load load) {
        if (load.getDeliveryDate() != null) {
            LocalTime time = load.getDeliveryTime() != null ? load.getDeliveryTime() : LocalTime.of(17, 0);
            return LocalDateTime.of(load.getDeliveryDate(), time);
        } else if (load.getPickUpDate() != null) {
            // Estimate end time if no delivery date
            return getLoadStartTime(load).plusHours(8);
        }
        return null;
    }
    
    private double calculateTimePosition(LocalDateTime time, int startHour, int endHour) {
        if (!time.toLocalDate().equals(selectedDate)) {
            if (time.toLocalDate().isBefore(selectedDate)) {
                return 150; // Start of timeline
            } else {
                return 150 + (endHour - startHour) * HOUR_WIDTH; // End of timeline
            }
        }
        
        double hourFraction = time.getHour() + (time.getMinute() / 60.0);
        double position = 150 + (hourFraction - startHour) * HOUR_WIDTH;
        
        return Math.max(150, Math.min(position, 150 + (endHour - startHour) * HOUR_WIDTH));
    }
    
    private String toRGBCode(Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
    
    private void exportTimeline() {
        logger.info("Exporting timeline for date: {}", selectedDate);
        // TODO: Implement timeline export functionality
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Timeline");
        alert.setHeaderText(null);
        alert.setContentText("Timeline export functionality will be implemented soon.");
        alert.showAndWait();
    }
    
    private void showLoadDetails(Load load) {
        logger.info("Showing details for load: {}", load.getLoadNumber());
        new LoadDetailsDialog(load).showAndWait();
    }
    
    private void showDriverDetails(DispatcherDriverStatus driver) {
        logger.info("Showing details for driver: {}", driver.getDriverName());
        // TODO: Implement driver details dialog
    }
    
    // Cleanup
    public void cleanup() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        if (currentTimeIndicator != null) {
            currentTimeIndicator.stop();
        }
    }
}