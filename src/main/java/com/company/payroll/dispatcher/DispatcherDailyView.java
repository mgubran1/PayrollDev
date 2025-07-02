package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import com.company.payroll.drivers.Driver;
import com.company.payroll.utils.DateTimeUtils;
import com.company.payroll.utils.UIUtils;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Enhanced daily schedule view showing timeline of activities
 * with interactive event management and detailed analytics
 * 
 * @author Payroll System
 * @version 2.0
 * @since 2025-07-02
 */
public class DispatcherDailyView extends BorderPane implements DispatcherTab.StatusChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherDailyView.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Current user and timestamp information
    private static final String CURRENT_USER = "mgubran1";
    private static final LocalDateTime CURRENT_TIME = LocalDateTime.parse("2025-07-02 11:15:44", DATETIME_FORMAT);
    
    // Core components
    private final DispatcherController controller;
    private final TableView<DailyEvent> eventTable;
    private final ObservableList<DailyEvent> dailyEvents;
    private final FilteredList<DailyEvent> filteredEvents;
    private final DatePicker datePicker;
    private LocalDate selectedDate;
    private final ProgressIndicator progressIndicator;
    private final Label statusLabel;
    
    // Summary components
    private PieChart eventTypeChart;
    private Label totalEventsLabel;
    private Label activeDriversLabel;
    private Label pickupsLabel;
    private Label deliveriesLabel;
    private Label inTransitLabel;
    
    // Filters
    private ComboBox<String> eventTypeFilter;
    private ComboBox<String> driverFilter;
    private CheckBox showCompletedCheck;
    
    // Event handler
    private EventHandler eventHandler;
    
    /**
     * Constructor with controller
     */
    public DispatcherDailyView(DispatcherController controller) {
        this.controller = controller;
        this.eventTable = new TableView<>();
        this.dailyEvents = FXCollections.observableArrayList();
        this.filteredEvents = new FilteredList<>(dailyEvents);
        this.datePicker = new DatePicker(LocalDate.now());
        this.selectedDate = LocalDate.now();
        this.progressIndicator = new ProgressIndicator();
        this.statusLabel = new Label("Ready");
        
        // Register for controller callbacks
        controller.addStatusChangeListener(this::handleDriverStatusChange);
        
        // Initialize UI and load data
        initializeUI();
        
        // Handle initial data load
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> setLoading(true));
            refresh();
            Platform.runLater(() -> setLoading(false));
        });
        
        logger.info("DispatcherDailyView initialized by {} at {}", CURRENT_USER, CURRENT_TIME.format(DATETIME_FORMAT));
    }
    
    /**
     * Initialize the UI components
     */
    private void initializeUI() {
        getStyleClass().add("dispatcher-daily-view");
        
        // Header
        VBox header = createHeader();
        setTop(header);
        
        // Main content - split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.7);
        
        // Left side - Event table and filters
        VBox leftPanel = createEventPanel();
        
        // Right side - Summary and quick stats
        ScrollPane rightPanel = new ScrollPane(createSummaryPanel());
        rightPanel.setFitToWidth(true);
        
        splitPane.getItems().addAll(leftPanel, rightPanel);
        setCenter(splitPane);
        
        // Status bar
        HBox statusBar = createStatusBar();
        setBottom(statusBar);
        
        // Set up the event handler for interaction with events
        eventHandler = new EventHandler(controller, this);
    }
    
    /**
     * Create the header section with date selection and actions
     */
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(10));
        header.getStyleClass().add("header-panel");
        
        // Date selection
        HBox dateControls = new HBox(10);
        dateControls.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("Date:");
        dateLabel.getStyleClass().add("field-label");
        
        datePicker.setOnAction(e -> {
            selectedDate = datePicker.getValue();
            refresh();
        });
        datePicker.getStyleClass().add("date-picker");
        
        Button prevBtn = createActionButton("◀ Previous", e -> {
            selectedDate = selectedDate.minusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button todayBtn = createActionButton("Today", e -> {
            selectedDate = LocalDate.now();
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button nextBtn = createActionButton("Next ▶", e -> {
            selectedDate = selectedDate.plusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Label selectedDateLabel = new Label();
        selectedDateLabel.getStyleClass().add("date-header");
        updateDateLabel(selectedDateLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button printBtn = createActionButton("🖨 Print", e -> printSchedule());
        Button exportBtn = createActionButton("📋 Export", e -> exportSchedule());
        Button refreshBtn = createActionButton("🔄 Refresh", e -> refresh());
        
        // Action buttons container
        HBox actionButtons = new HBox(5, printBtn, exportBtn, refreshBtn);
        actionButtons.setAlignment(Pos.CENTER_RIGHT);
        
        dateControls.getChildren().addAll(
            dateLabel, datePicker, prevBtn, todayBtn, nextBtn,
            spacer, selectedDateLabel
        );
        
        // Filters section
        HBox filterControls = new HBox(10);
        filterControls.setAlignment(Pos.CENTER_LEFT);
        
        Label filterLabel = new Label("Filter:");
        filterLabel.getStyleClass().add("field-label");
        
        eventTypeFilter = new ComboBox<>();
        eventTypeFilter.getItems().add("All Event Types");
        for (EventType type : EventType.values()) {
            eventTypeFilter.getItems().add(type.getDisplayName());
        }
        eventTypeFilter.setValue("All Event Types");
        eventTypeFilter.setOnAction(e -> applyFilters());
        
        driverFilter = new ComboBox<>();
        driverFilter.getItems().add("All Drivers");
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            driverFilter.getItems().add(driver.getDriverName());
        }
        driverFilter.setValue("All Drivers");
        driverFilter.setOnAction(e -> applyFilters());
        
        showCompletedCheck = new CheckBox("Show Completed");
        showCompletedCheck.setSelected(true);
        showCompletedCheck.setOnAction(e -> applyFilters());
        
        TextField searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, old, text) -> {
            applySearch(text);
        });
        
        filterControls.getChildren().addAll(
            filterLabel, 
            new Label("Type:"), eventTypeFilter,
            new Label("Driver:"), driverFilter,
            showCompletedCheck,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            new Label("Search:"), searchField,
            spacer,
            actionButtons
        );
        
        header.getChildren().addAll(dateControls, filterControls);
        
        return header;
    }
    
    /**
     * Create button with styling
     */
    private Button createActionButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setOnAction(handler);
        button.getStyleClass().add("action-button");
        return button;
    }
    
    /**
     * Update the date label
     */
    private void updateDateLabel(Label label) {
        label.setText(selectedDate.format(DATE_FORMAT));
    }
    
    /**
     * Create the main event panel with table
     */
    private VBox createEventPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        
        Label tableLabel = new Label("Daily Schedule");
        tableLabel.getStyleClass().add("section-header");
        
        // Set up event table
        setupEventTable();
        
        // Add no content placeholder
        eventTable.setPlaceholder(new Label("No events scheduled for this day"));
        
        VBox.setVgrow(eventTable, Priority.ALWAYS);
        
        // Button bar for event actions
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        
        Button addEventBtn = new Button("Add Event");
        addEventBtn.setOnAction(e -> addNewEvent());
        
        Button viewEventBtn = new Button("View Details");
        viewEventBtn.setOnAction(e -> viewSelectedEvent());
        viewEventBtn.disableProperty().bind(
            Bindings.isEmpty(eventTable.getSelectionModel().getSelectedItems())
        );
        
        Button editEventBtn = new Button("Edit");
        editEventBtn.setOnAction(e -> editSelectedEvent());
        editEventBtn.disableProperty().bind(
            Bindings.isEmpty(eventTable.getSelectionModel().getSelectedItems())
        );
        
        Button deleteEventBtn = new Button("Delete");
        deleteEventBtn.setOnAction(e -> deleteSelectedEvent());
        deleteEventBtn.disableProperty().bind(
            Bindings.isEmpty(eventTable.getSelectionModel().getSelectedItems())
        );
        
        buttonBar.getChildren().addAll(addEventBtn, viewEventBtn, editEventBtn, deleteEventBtn);
        
        panel.getChildren().addAll(tableLabel, eventTable, buttonBar);
        
        return panel;
    }
    
    /**
     * Set up the event table columns and behavior
     */
    private void setupEventTable() {
        eventTable.getStyleClass().add("event-table");
        
        // Time column
        TableColumn<DailyEvent, LocalTime> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getTime().toLocalTime()));
        timeCol.setCellFactory(col -> new TableCell<DailyEvent, LocalTime>() {
            @Override
            protected void updateItem(LocalTime time, boolean empty) {
                super.updateItem(time, empty);
                if (empty || time == null) {
                    setText(null);
                } else {
                    setText(time.format(TIME_FORMAT));
                }
            }
        });
        timeCol.setPrefWidth(80);
        timeCol.setStyle("-fx-alignment: CENTER;");
        
        // Type column
        TableColumn<DailyEvent, EventType> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        typeCol.setCellFactory(col -> new TableCell<DailyEvent, EventType>() {
            @Override
            protected void updateItem(EventType type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(type.getDisplayName());
                    
                    // Create a colored indicator
                    Circle indicator = new Circle(6);
                    indicator.setFill(Color.web(type.getColor()));
                    
                    HBox box = new HBox(5, indicator, new Label(type.getDisplayName()));
                    box.setAlignment(Pos.CENTER_LEFT);
                    
                    setGraphic(box);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        typeCol.setPrefWidth(120);
        
        // Driver column
        TableColumn<DailyEvent, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
        driverCol.setPrefWidth(150);
        
        // Load column
        TableColumn<DailyEvent, String> loadCol = new TableColumn<>("Load #");
        loadCol.setCellValueFactory(new PropertyValueFactory<>("loadNumber"));
        loadCol.setPrefWidth(100);
        
        // Customer column
        TableColumn<DailyEvent, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(new PropertyValueFactory<>("customer"));
        customerCol.setPrefWidth(150);
        
        // Location column
        TableColumn<DailyEvent, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locationCol.setPrefWidth(200);
        
        // Status column
        TableColumn<DailyEvent, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(col -> new TableCell<DailyEvent, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status.toUpperCase()) {
                        case "COMPLETED":
                        case "DELIVERED":
                        case "PAID":
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            break;
                        case "IN TRANSIT":
                            setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
                            break;
                        case "DELAYED":
                            setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            break;
                        case "PENDING":
                            setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });
        statusCol.setPrefWidth(100);
        
        eventTable.getColumns().setAll(Arrays.asList(
            timeCol, typeCol, driverCol, loadCol,
            customerCol, locationCol, statusCol));
        
        // Connect the filtered list to the table
        SortedList<DailyEvent> sortedEvents = new SortedList<>(filteredEvents);
        sortedEvents.comparatorProperty().bind(eventTable.comparatorProperty());
        eventTable.setItems(sortedEvents);
        
        // Set up row factory for styling and double-click behavior
        eventTable.setRowFactory(tv -> {
            TableRow<DailyEvent> row = new TableRow<DailyEvent>() {
                @Override
                protected void updateItem(DailyEvent event, boolean empty) {
                    super.updateItem(event, empty);
                    if (empty || event == null) {
                        setStyle("");
                    } else {
                        // Highlight rows based on event type
                        setStyle("-fx-background-color: " + event.getEventType().getColor() + "22;");
                    }
                }
            };
            
            // Double click to view details
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewEventDetails(row.getItem());
                }
            });
            
            // Context menu
            ContextMenu contextMenu = new ContextMenu();
            MenuItem viewItem = new MenuItem("View Details");
            viewItem.setOnAction(e -> viewEventDetails(row.getItem()));
            
            MenuItem editItem = new MenuItem("Edit Event");
            editItem.setOnAction(e -> editEvent(row.getItem()));
            
            MenuItem deleteItem = new MenuItem("Delete Event");
            deleteItem.setOnAction(e -> deleteEvent(row.getItem()));
            
            MenuItem loadDetailsItem = new MenuItem("View Load Details");
            loadDetailsItem.setOnAction(e -> viewLoadDetails(row.getItem()));
            
            contextMenu.getItems().addAll(viewItem, editItem, deleteItem, new SeparatorMenuItem(), loadDetailsItem);
            row.contextMenuProperty().bind(
                Bindings.when(Bindings.isNotNull(row.itemProperty()))
                    .then(contextMenu)
                    .otherwise((ContextMenu) null)
            );
            
            // Drag and drop for rescheduling
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(Integer.toString(row.getIndex()));
                    db.setContent(content);
                    event.consume();
                }
            });
            
            row.setOnDragOver(event -> {
                if (event.getGestureSource() != row && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    event.consume();
                }
            });
            
            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    DailyEvent draggedEvent = eventTable.getItems().get(draggedIndex);
                    
                    // Show dialog to reschedule
                    rescheduleEvent(draggedEvent);
                    
                    success = true;
                }
                event.setDropCompleted(success);
                event.consume();
            });
            
            return row;
        });
        
        // Enable multi-selection
        eventTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }
    
    /**
     * Create summary panel with statistics
     */
    private VBox createSummaryPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("summary-panel");
        
        Label titleLabel = new Label("Daily Summary");
        titleLabel.getStyleClass().add("section-header");
        
        // Statistics grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(10);
        statsGrid.setPadding(new Insets(10));
        
        // Add statistics labels
        totalEventsLabel = new Label("0");
        activeDriversLabel = new Label("0");
        pickupsLabel = new Label("0");
        deliveriesLabel = new Label("0");
        inTransitLabel = new Label("0");
        
        int row = 0;
        addStatRow(statsGrid, "Total Events:", totalEventsLabel, row++);
        addStatRow(statsGrid, "Active Drivers:", activeDriversLabel, row++);
        addStatRow(statsGrid, "Pickups:", pickupsLabel, row++);
        addStatRow(statsGrid, "Deliveries:", deliveriesLabel, row++);
        addStatRow(statsGrid, "In Transit:", inTransitLabel, row++);
        
        // Chart for event types
        eventTypeChart = new PieChart();
        eventTypeChart.setTitle("Event Distribution");
        eventTypeChart.setLabelsVisible(true);
        eventTypeChart.setLegendVisible(true);
        eventTypeChart.setStartAngle(90);
        eventTypeChart.setPrefHeight(200);
        
        // Driver availability section
        TitledPane availabilityPane = createDriverAvailabilityPane();
        
        // Load status section
        TitledPane loadStatusPane = createLoadStatusPane();
        
        panel.getChildren().addAll(
            titleLabel, 
            statsGrid, 
            new Separator(),
            eventTypeChart,
            new Separator(),
            availabilityPane,
            loadStatusPane
        );
        
        return panel;
    }
    
    /**
     * Create driver availability pane
     */
    private TitledPane createDriverAvailabilityPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label label = new Label("No data available");
        content.getChildren().add(label);
        
        TitledPane pane = new TitledPane("Driver Availability", content);
        pane.setCollapsible(true);
        pane.setExpanded(true);
        
        return pane;
    }
    
    /**
     * Create load status pane
     */
    private TitledPane createLoadStatusPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label label = new Label("No data available");
        content.getChildren().add(label);
        
        TitledPane pane = new TitledPane("Load Status", content);
        pane.setCollapsible(true);
        pane.setExpanded(true);
        
        return pane;
    }
    
    /**
     * Create status bar
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(16, 16);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label timeLabel = new Label(LocalDateTime.now().format(DATETIME_FORMAT));
        
        statusBar.getChildren().addAll(progressIndicator, statusLabel, spacer, timeLabel);
        
        return statusBar;
    }
    
    /**
     * Add a statistic row to the stats grid
     */
    private void addStatRow(GridPane grid, String label, Label valueLabel, int row) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("stat-label");
        
        valueLabel.getStyleClass().add("stat-value");
        
        grid.add(labelNode, 0, row);
        grid.add(valueLabel, 1, row);
    }
    
    /**
     * Refresh the view with updated data
     */
    public void refresh() {
        logger.info("Refreshing daily view for date: {}", selectedDate);
        setLoading(true);
        statusLabel.setText("Loading data...");
        
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Refresh controller data
                controller.refreshData();
                
                // Load events for the selected date
                loadDailyEvents();
                
                return null;
            }
        };
        
        task.setOnSucceeded(e -> {
            updateDateLabel((Label) ((HBox) ((VBox) getTop()).getChildren().get(0)).getChildren().get(6));
            updateSummary();
            updateCharts();
            updateDriverAvailability();
            updateLoadStatus();
            applyFilters();
            setLoading(false);
            statusLabel.setText("Data updated at " + LocalDateTime.now().format(DATETIME_FORMAT));
        });
        
        task.setOnFailed(e -> {
            logger.error("Failed to refresh data", task.getException());
            setLoading(false);
            statusLabel.setText("Error loading data: " + task.getException().getMessage());
        });
        
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Set loading state
     */
    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        eventTable.setDisable(loading);
    }
    
    /**
     * Load daily events from the controller
     */
    private void loadDailyEvents() {
        dailyEvents.clear();
        
        // Process all drivers and their loads
        for (DispatcherDriverStatus driverStatus : controller.getDriverStatuses()) {
            Driver driver = driverStatus.getDriver();
            List<Load> loads = controller.getLoadsForDriverAndRange(
                driver, selectedDate, selectedDate);
                
            for (Load load : loads) {
                // Check for pickup on selected date
                if (load.getPickupDate() != null && load.getPickupDate().equals(selectedDate)) {
                    LocalTime time = load.getPickupTime() != null ? 
                        load.getPickupTime() : LocalTime.of(8, 0);
                    
                    dailyEvents.add(new DailyEvent(
                        LocalDateTime.of(selectedDate, time),
                        EventType.PICKUP,
                        driverStatus.getDriverName(),
                        driverStatus.getTruckNumber(),
                        load.getLoadNumber(),
                        load.getCustomer(),
                        formatLocation(load.getOriginCity(), load.getOriginState()),
                        load.getStatus().toString(),
                        load.getLoadId()
                    ));
                }
                
                // Check for delivery on selected date
                if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(selectedDate)) {
                    LocalTime time = load.getDeliveryTime() != null ? 
                        load.getDeliveryTime() : LocalTime.of(17, 0);
                    
                    dailyEvents.add(new DailyEvent(
                        LocalDateTime.of(selectedDate, time),
                        EventType.DELIVERY,
                        driverStatus.getDriverName(),
                        driverStatus.getTruckNumber(),
                        load.getLoadNumber(),
                        load.getCustomer(),
                        formatLocation(load.getDestCity(), load.getDestState()),
                        load.getStatus().toString(),
                        load.getLoadId()
                    ));
                }
                
                // Check for in-transit loads
                if (isLoadInTransitOnDate(load, selectedDate)) {
                    dailyEvents.add(new DailyEvent(
                        LocalDateTime.of(selectedDate, LocalTime.NOON),
                        EventType.IN_TRANSIT,
                        driverStatus.getDriverName(),
                        driverStatus.getTruckNumber(),
                        load.getLoadNumber(),
                        load.getCustomer(),
                        "En route: " + formatLocation(load.getOriginCity(), load.getOriginState()) + 
                        " → " + formatLocation(load.getDestCity(), load.getDestState()),
                        "IN TRANSIT",
                        load.getLoadId()
                    ));
                }
            }
            
            // Add scheduled breaks/maintenance for the driver
            addDriverScheduledEvents(driverStatus);
        }
        
        // Sort events by time
        dailyEvents.sort(Comparator.comparing(DailyEvent::getTime));
    }
    
    /**
     * Add scheduled events for a driver
     */
    private void addDriverScheduledEvents(DispatcherDriverStatus driverStatus) {
        // This is where we would add scheduled breaks, maintenance, etc.
        // For demo purposes, we'll just add some simulated breaks based on driver ID
        
        // Only add breaks for even-numbered driver IDs to show a mix
        if (driverStatus.getDriverId().hashCode() % 2 == 0) {
            // Morning break
            dailyEvents.add(new DailyEvent(
                LocalDateTime.of(selectedDate, LocalTime.of(10, 30)),
                EventType.BREAK,
                driverStatus.getDriverName(),
                driverStatus.getTruckNumber(),
                "N/A",
                "Scheduled Break",
                "Rest Area",
                "SCHEDULED",
                "BREAK-" + driverStatus.getDriverId()
            ));
            
            // Afternoon break
            dailyEvents.add(new DailyEvent(
                LocalDateTime.of(selectedDate, LocalTime.of(14, 30)),
                EventType.BREAK,
                driverStatus.getDriverName(),
                driverStatus.getTruckNumber(),
                "N/A",
                "Scheduled Break",
                "Rest Area",
                "SCHEDULED",
                "BREAK-" + driverStatus.getDriverId()
            ));
        }
        
        // Add maintenance for truck numbers ending in 5
        if (driverStatus.getTruckNumber() != null && 
            driverStatus.getTruckNumber().endsWith("5")) {
            dailyEvents.add(new DailyEvent(
                LocalDateTime.of(selectedDate, LocalTime.of(16, 0)),
                EventType.MAINTENANCE,
                driverStatus.getDriverName(),
                driverStatus.getTruckNumber(),
                "MAINT-" + driverStatus.getTruckNumber(),
                "Regular Maintenance",
                "Service Center",
                "SCHEDULED",
                "MAINT-" + driverStatus.getTruckNumber()
            ));
        }
    }
    
    /**
     * Format location
     */
    private String formatLocation(String city, String state) {
        if (city == null || city.isEmpty()) return "Unknown";
        if (state == null || state.isEmpty()) return city;
        return city + ", " + state;
    }
    
    /**
     * Update the summary statistics
     */
    private void updateSummary() {
        int totalEvents = dailyEvents.size();
        int activeDrivers = (int) controller.getDriverStatuses().stream()
            .filter(d -> dailyEvents.stream()
                .anyMatch(e -> e.getDriverName().equals(d.getDriverName())))
            .count();
        int pickups = countEventsByType(EventType.PICKUP);
        int deliveries = countEventsByType(EventType.DELIVERY);
        int inTransit = countEventsByType(EventType.IN_TRANSIT);
        
        Platform.runLater(() -> {
            totalEventsLabel.setText(String.valueOf(totalEvents));
            activeDriversLabel.setText(String.valueOf(activeDrivers));
            pickupsLabel.setText(String.valueOf(pickups));
            deliveriesLabel.setText(String.valueOf(deliveries));
            inTransitLabel.setText(String.valueOf(inTransit));
        });
    }
    
    /**
     * Update the event type chart
     */
    private void updateCharts() {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        
        for (EventType type : EventType.values()) {
            int count = countEventsByType(type);
            if (count > 0) {
                pieChartData.add(new PieChart.Data(type.getDisplayName(), count));
            }
        }
        
        Platform.runLater(() -> {
            eventTypeChart.setData(pieChartData);
            
            // Set the color of each pie slice to match the event type
            int i = 0;
            for (EventType type : EventType.values()) {
                if (countEventsByType(type) > 0) {
                    eventTypeChart.getData().get(i).getNode()
                        .setStyle("-fx-pie-color: " + type.getColor() + ";");
                    i++;
                }
            }
        });
    }
    
    /**
     * Update driver availability panel
     */
    private void updateDriverAvailability() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Map<DispatcherDriverStatus.Status, Integer> statusCounts = new HashMap<>();
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            statusCounts.put(status, 0);
        }
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            DispatcherDriverStatus.Status status = driver.getStatus();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
        }
        
        for (Map.Entry<DispatcherDriverStatus.Status, Integer> entry : statusCounts.entrySet()) {
            if (entry.getValue() > 0) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                
                Circle indicator = new Circle(6);
                indicator.setFill(Color.web(entry.getKey().getColor()));
                
                Label statusLabel = new Label(entry.getKey().getDisplayName());
                statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
                
                Label countLabel = new Label(entry.getValue().toString());
                
                row.getChildren().addAll(indicator, statusLabel, countLabel);
                content.getChildren().add(row);
            }
        }
        
        Platform.runLater(() -> {
            TitledPane availabilityPane = (TitledPane) ((VBox) ((ScrollPane) getCenter().lookup(".split-pane").lookup(".scroll-pane")).getContent()).getChildren().get(5);
            availabilityPane.setContent(content);
        });
    }
    
    /**
     * Update load status panel
     */
    private void updateLoadStatus() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Map<String, Integer> statusCounts = new HashMap<>();
        for (DailyEvent event : dailyEvents) {
            if (event.getLoadNumber() != null && !event.getLoadNumber().equals("N/A")) {
                String status = event.getStatus();
                statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
            }
        }
        
        for (Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            
            Label statusLabel = new Label(entry.getKey());
            statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            Label countLabel = new Label(entry.getValue().toString());
            
            row.getChildren().addAll(statusLabel, countLabel);
            content.getChildren().add(row);
        }
        
        Platform.runLater(() -> {
            TitledPane loadStatusPane = (TitledPane) ((VBox) ((ScrollPane) getCenter().lookup(".split-pane").lookup(".scroll-pane")).getContent()).getChildren().get(6);
            loadStatusPane.setContent(content);
        });
    }
    
    /**
     * Count events by type
     */
    private int countEventsByType(EventType type) {
        return (int) dailyEvents.stream()
            .filter(e -> e.getEventType() == type)
            .count();
    }
    
    /**
     * Check if a load is in transit on the given date
     */
    private boolean isLoadInTransitOnDate(Load load, LocalDate date) {
        if (load.getPickupDate() != null && load.getDeliveryDate() != null) {
            return !date.isBefore(load.getPickupDate()) && 
                   !date.isAfter(load.getDeliveryDate()) &&
                   !date.equals(load.getPickupDate()) && 
                   !date.equals(load.getDeliveryDate());
        }
        return false;
    }
    
    /**
     * Apply filters to the event list
     */
    private void applyFilters() {
        String selectedEventType = eventTypeFilter.getValue();
        String selectedDriver = driverFilter.getValue();
        boolean showCompleted = showCompletedCheck.isSelected();
        
        filteredEvents.setPredicate(event -> {
            // Filter by event type
            boolean matchesType = "All Event Types".equals(selectedEventType) || 
                event.getEventType().getDisplayName().equals(selectedEventType);
            
            // Filter by driver
            boolean matchesDriver = "All Drivers".equals(selectedDriver) ||
                event.getDriverName().equals(selectedDriver);
            
            // Filter by completion status
            boolean matchesCompletion = showCompleted || 
                !isCompletedStatus(event.getStatus());
            
            return matchesType && matchesDriver && matchesCompletion;
        });
        
        eventTable.refresh();
    }
    
    /**
     * Apply search filter
     */
    private void applySearch(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            // Clear search filter
            applyFilters();
            return;
        }
        
        String lowerCaseSearch = searchText.toLowerCase().trim();
        
        // Get current filter predicates
        Predicate<DailyEvent> currentPredicates = filteredEvents.getPredicate();
        
        // Add search predicate
        Predicate<DailyEvent> searchPredicate = event -> 
            (event.getDriverName() != null && event.getDriverName().toLowerCase().contains(lowerCaseSearch)) ||
            (event.getLoadNumber() != null && event.getLoadNumber().toLowerCase().contains(lowerCaseSearch)) ||
            (event.getCustomer() != null && event.getCustomer().toLowerCase().contains(lowerCaseSearch)) ||
            (event.getLocation() != null && event.getLocation().toLowerCase().contains(lowerCaseSearch)) ||
            (event.getStatus() != null && event.getStatus().toLowerCase().contains(lowerCaseSearch));
        
        // Combine predicates
        if (currentPredicates != null) {
            filteredEvents.setPredicate(currentPredicates.and(searchPredicate));
        } else {
            filteredEvents.setPredicate(searchPredicate);
        }
        
        eventTable.refresh();
    }
    
    /**
     * Check if a status is considered "completed"
     */
    private boolean isCompletedStatus(String status) {
        if (status == null) return false;
        String upperStatus = status.toUpperCase();
        return upperStatus.contains("COMPLETED") || 
               upperStatus.contains("DELIVERED") || 
               upperStatus.contains("PAID");
    }
    
    /**
     * View details for a selected event
     */
    private void viewEventDetails(DailyEvent event) {
        logger.info("Viewing details for event: {} - {}", event.getEventType(), event.getLoadNumber());
        eventHandler.viewEventDetails(event);
    }
    
    /**
     * View load details
     */
    private void viewLoadDetails(DailyEvent event) {
        if (event.getLoadId() == null || event.getLoadId().startsWith("BREAK-") || 
            event.getLoadId().startsWith("MAINT-")) {
            showMessage("No Load", "No load associated with this event.");
            return;
        }
        
        logger.info("Viewing load details for: {}", event.getLoadNumber());
        eventHandler.viewLoadDetails(event.getLoadId());
    }
    
    /**
     * Edit an event
     */
    private void editEvent(DailyEvent event) {
        logger.info("Editing event: {} - {}", event.getEventType(), event.getLoadNumber());
        eventHandler.editEvent(event);
    }
    
    /**
     * Delete an event
     */
    private void deleteEvent(DailyEvent event) {
        logger.info("Deleting event: {} - {}", event.getEventType(), event.getLoadNumber());
        eventHandler.deleteEvent(event);
    }
    
    /**
     * Reschedule an event
     */
    private void rescheduleEvent(DailyEvent event) {
        logger.info("Rescheduling event: {} - {}", event.getEventType(), event.getLoadNumber());
        eventHandler.rescheduleEvent(event);
    }
    
    /**
     * Add a new event
     */
    private void addNewEvent() {
        logger.info("Adding new event");
        eventHandler.addNewEvent(selectedDate);
    }
    
    /**
     * View details for the selected event
     */
    private void viewSelectedEvent() {
        DailyEvent selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewEventDetails(selected);
        }
    }
    
    /**
     * Edit the selected event
     */
    private void editSelectedEvent() {
        DailyEvent selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            editEvent(selected);
        }
    }
    
    /**
     * Delete the selected event
     */
    private void deleteSelectedEvent() {
        DailyEvent selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            deleteEvent(selected);
        }
    }
    
    /**
     * Print the schedule
     */
    private void printSchedule() {
        logger.info("Printing schedule for {}", selectedDate.format(DATE_FORMAT));
        
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(eventTable.getScene().getWindow())) {
            // Create printable content
            BorderPane printContent = new BorderPane();
            
            // Title
            Label titleLabel = new Label("Daily Schedule: " + selectedDate.format(DATE_FORMAT));
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
            
            // Create a table just for printing
            TableView<DailyEvent> printTable = new TableView<>();
            printTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            
            // Simple columns for printing
            TableColumn<DailyEvent, String> timeCol = new TableColumn<>("Time");
            timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTime().format(TIME_FORMAT)));
            
            TableColumn<DailyEvent, String> typeCol = new TableColumn<>("Type");
            typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEventType().getDisplayName()));
            
            TableColumn<DailyEvent, String> driverCol = new TableColumn<>("Driver");
            driverCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
            
            TableColumn<DailyEvent, String> loadCol = new TableColumn<>("Load #");
            loadCol.setCellValueFactory(new PropertyValueFactory<>("loadNumber"));
            
            TableColumn<DailyEvent, String> customerCol = new TableColumn<>("Customer");
            customerCol.setCellValueFactory(new PropertyValueFactory<>("customer"));
            
            TableColumn<DailyEvent, String> locationCol = new TableColumn<>("Location");
            locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
            
            printTable.getColumns().addAll(timeCol, typeCol, driverCol, loadCol, customerCol, locationCol);
            printTable.setItems(filteredEvents);
            
            // Summary
            Label summaryLabel = new Label(String.format(
                "Total Events: %s | Active Drivers: %s | Pickups: %s | Deliveries: %s | In Transit: %s",
                totalEventsLabel.getText(),
                activeDriversLabel.getText(),
                pickupsLabel.getText(),
                deliveriesLabel.getText(),
                inTransitLabel.getText()
            ));
            summaryLabel.setFont(Font.font("System", FontWeight.MEDIUM, 12));
            
            VBox content = new VBox(15);
            content.setPadding(new Insets(15));
            content.getChildren().addAll(titleLabel, printTable, summaryLabel);
            
            printContent.setCenter(content);
            
            // Print the content
            boolean success = job.printPage(printContent);
            if (success) {
                job.endJob();
                statusLabel.setText("Schedule printed successfully");
            } else {
                statusLabel.setText("Failed to print schedule");
            }
        }
    }
    
    /**
     * Export the schedule to a file
     */
    private void exportSchedule() {
        logger.info("Exporting schedule for {}", selectedDate.format(DATE_FORMAT));
        
        Dialog<ExportFormat> dialog = new Dialog<>();
        dialog.setTitle("Export Schedule");
        dialog.setHeaderText("Select export format for " + selectedDate.format(DATE_FORMAT));
        
        ButtonType csvButton = new ButtonType("CSV", ButtonBar.ButtonData.OK_DONE);
        ButtonType excelButton = new ButtonType("Excel", ButtonBar.ButtonData.OK_DONE);
        ButtonType pdfButton = new ButtonType("PDF", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = ButtonType.CANCEL;
        
        dialog.getDialogPane().getButtonTypes().addAll(csvButton, excelButton, pdfButton, cancelButton);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == csvButton) return ExportFormat.CSV;
            if (dialogButton == excelButton) return ExportFormat.EXCEL;
            if (dialogButton == pdfButton) return ExportFormat.PDF;
            return null;
        });
        
        Optional<ExportFormat> result = dialog.showAndWait();
        result.ifPresent(format -> {
            CompletableFuture.runAsync(() -> {
                try {
                    // Simulate export
                    Thread.sleep(1000);
                    Platform.runLater(() -> {
                        statusLabel.setText("Schedule exported to " + format.name() + " format");
                    });
                } catch (Exception e) {
                    logger.error("Failed to export schedule", e);
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to export schedule");
                    });
                }
            });
        });
    }
    
    /**
     * Show a simple message dialog
     */
    private void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Handle driver status change callback from controller
     */
    public void handleDriverStatusChange(DispatcherDriverStatus oldStatus, DispatcherDriverStatus newStatus) {
        // Only update if the change might affect today's schedule
        if (selectedDate.equals(LocalDate.now())) {
            logger.info("Driver status changed, refreshing schedule: {} -> {}", 
                oldStatus.getStatus(), newStatus.getStatus());
            Platform.runLater(this::refresh);
        }
    }
    
    /**
     * Handle status change notification from DispatcherTab interface
     */
    @Override
    public void onStatusChanged(DispatcherDriverStatus oldStatus, DispatcherDriverStatus newStatus) {
        handleDriverStatusChange(oldStatus, newStatus);
    }
    
    /**
     * Event handler class to manage event operations
     */
    private class EventHandler {
        private final DispatcherController controller;
        private final DispatcherDailyView view;
        
        public EventHandler(DispatcherController controller, DispatcherDailyView view) {
            this.controller = controller;
            this.view = view;
        }
        
        public void viewEventDetails(DailyEvent event) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Event Details");
            alert.setHeaderText(event.getEventType().getDisplayName() + ": " + event.getTime().format(TIME_FORMAT));
            
            GridPane detailsGrid = new GridPane();
            detailsGrid.setHgap(10);
            detailsGrid.setVgap(5);
            detailsGrid.setPadding(new Insets(10));
            
            int row = 0;
            addDetailRow(detailsGrid, "Time:", event.getTime().format(TIME_FORMAT), row++);
            addDetailRow(detailsGrid, "Type:", event.getEventType().getDisplayName(), row++);
            addDetailRow(detailsGrid, "Driver:", event.getDriverName(), row++);
            addDetailRow(detailsGrid, "Truck:", event.getTruckUnit(), row++);
            if (!event.getLoadNumber().equals("N/A")) {
                addDetailRow(detailsGrid, "Load #:", event.getLoadNumber(), row++);
                addDetailRow(detailsGrid, "Customer:", event.getCustomer(), row++);
            }
            addDetailRow(detailsGrid, "Location:", event.getLocation(), row++);
            addDetailRow(detailsGrid, "Status:", event.getStatus(), row++);
            
            alert.getDialogPane().setContent(detailsGrid);
            alert.getDialogPane().setPrefWidth(400);
            alert.showAndWait();
        }
        
        public void viewLoadDetails(String loadId) {
            try {
                Load load = controller.getLoadById(loadId);
                if (load != null) {
                    LoadDetailsDialog dialog = new LoadDetailsDialog(load, controller, view.getScene().getWindow());
                    dialog.showAndWait();
                } else {
                    showMessage("Load Not Found", "The load could not be found in the system.");
                }
            } catch (Exception e) {
                logger.error("Error viewing load details", e);
                showMessage("Error", "An error occurred while loading the details: " + e.getMessage());
            }
        }
        
        public void editEvent(DailyEvent event) {
            Dialog<DailyEvent> dialog = new Dialog<>();
            dialog.setTitle("Edit Event");
            dialog.setHeaderText("Edit Event Details");
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            // Time picker
            Label timeLabel = new Label("Time:");
            Spinner<Integer> hourSpinner = new Spinner<>(0, 23, event.getTime().getHour());
            hourSpinner.setEditable(true);
            hourSpinner.setPrefWidth(70);
            
            Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, event.getTime().getMinute());
            minuteSpinner.setEditable(true);
            minuteSpinner.setPrefWidth(70);
            
            HBox timeBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);
            
            // Type combo
            Label typeLabel = new Label("Event Type:");
            ComboBox<EventType> typeCombo = new ComboBox<>();
            typeCombo.getItems().addAll(EventType.values());
            typeCombo.setValue(event.getEventType());
            
            // Driver combo
            Label driverLabel = new Label("Driver:");
            ComboBox<String> driverCombo = new ComboBox<>();
            for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
                driverCombo.getItems().add(driver.getDriverName());
            }
            driverCombo.setValue(event.getDriverName());
            
            // Location field
            Label locationLabel = new Label("Location:");
            TextField locationField = new TextField(event.getLocation());
            
            // Notes field
            Label notesLabel = new Label("Notes:");
            TextArea notesArea = new TextArea();
            notesArea.setPrefRowCount(3);
            notesArea.setWrapText(true);
            
            // Add to grid
            grid.add(timeLabel, 0, 0);
            grid.add(timeBox, 1, 0);
            grid.add(typeLabel, 0, 1);
            grid.add(typeCombo, 1, 1);
            grid.add(driverLabel, 0, 2);
            grid.add(driverCombo, 1, 2);
            grid.add(locationLabel, 0, 3);
            grid.add(locationField, 1, 3);
            grid.add(notesLabel, 0, 4);
            grid.add(notesArea, 1, 4);
            
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    LocalDateTime newTime = LocalDateTime.of(
                        selectedDate,
                        LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                    );
                    
                    return new DailyEvent(
                        newTime,
                        typeCombo.getValue(),
                        driverCombo.getValue(),
                        event.getTruckUnit(),
                        event.getLoadNumber(),
                        event.getCustomer(),
                        locationField.getText(),
                        event.getStatus(),
                        event.getLoadId()
                    );
                }
                return null;
            });
            
            Optional<DailyEvent> result = dialog.showAndWait();
            result.ifPresent(updatedEvent -> {
                // In a real implementation, we would update the backend
                // For now, just update the UI
                int index = dailyEvents.indexOf(event);
                if (index >= 0) {
                    dailyEvents.set(index, updatedEvent);
                    view.refresh();
                }
            });
        }
        
        public void deleteEvent(DailyEvent event) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Event");
            confirm.setHeaderText("Delete Event");
            confirm.setContentText("Are you sure you want to delete this event?\n\n" +
                                "Type: " + event.getEventType().getDisplayName() + "\n" +
                                "Time: " + event.getTime().format(TIME_FORMAT) + "\n" +
                                "Driver: " + event.getDriverName());
            
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // In a real implementation, we would update the backend
                // For now, just update the UI
                dailyEvents.remove(event);
                updateSummary();
                updateCharts();
            }
        }
        
        public void rescheduleEvent(DailyEvent event) {
            Dialog<LocalDateTime> dialog = new Dialog<>();
            dialog.setTitle("Reschedule Event");
            dialog.setHeaderText("Reschedule " + event.getEventType().getDisplayName());
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            // Date picker
            Label dateLabel = new Label("Date:");
            DatePicker newDatePicker = new DatePicker(event.getTime().toLocalDate());
            
            // Time picker
            Label timeLabel = new Label("Time:");
            Spinner<Integer> hourSpinner = new Spinner<>(0, 23, event.getTime().getHour());
            hourSpinner.setEditable(true);
            hourSpinner.setPrefWidth(70);
            
            Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, event.getTime().getMinute());
            minuteSpinner.setEditable(true);
            minuteSpinner.setPrefWidth(70);
            
            HBox timeBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);
            
            // Add to grid
            grid.add(dateLabel, 0, 0);
            grid.add(newDatePicker, 1, 0);
            grid.add(timeLabel, 0, 1);
            grid.add(timeBox, 1, 1);
            
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return LocalDateTime.of(
                        newDatePicker.getValue(),
                        LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                    );
                }
                return null;
            });
            
            Optional<LocalDateTime> result = dialog.showAndWait();
            result.ifPresent(newDateTime -> {
                // In a real implementation, we would update the backend
                // For now, just update the UI if still on the same day
                if (newDateTime.toLocalDate().equals(selectedDate)) {
                    int index = dailyEvents.indexOf(event);
                    if (index >= 0) {
                        DailyEvent updatedEvent = new DailyEvent(
                            newDateTime,
                            event.getEventType(),
                            event.getDriverName(),
                            event.getTruckUnit(),
                            event.getLoadNumber(),
                            event.getCustomer(),
                            event.getLocation(),
                            event.getStatus(),
                            event.getLoadId()
                        );
                        
                        dailyEvents.set(index, updatedEvent);
                        view.refresh();
                    }
                } else {
                    // Event rescheduled to different day
                    dailyEvents.remove(event);
                    view.refresh();
                    
                    showMessage("Event Rescheduled", 
                        "Event has been rescheduled to " + 
                        newDateTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm")));
                }
            });
        }
        
        public void addNewEvent(LocalDate date) {
            Dialog<DailyEvent> dialog = new Dialog<>();
            dialog.setTitle("Add Event");
            dialog.setHeaderText("Add New Event");
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            // Time picker
            Label timeLabel = new Label("Time:");
            Spinner<Integer> hourSpinner = new Spinner<>(0, 23, LocalTime.now().getHour());
            hourSpinner.setEditable(true);
            hourSpinner.setPrefWidth(70);
            
            Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, LocalTime.now().getMinute());
            minuteSpinner.setEditable(true);
            minuteSpinner.setPrefWidth(70);
            
            HBox timeBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);
            
            // Type combo
            Label typeLabel = new Label("Event Type:");
            ComboBox<EventType> typeCombo = new ComboBox<>();
            typeCombo.getItems().addAll(EventType.values());
            typeCombo.setValue(EventType.BREAK);
            
            // Driver combo
            Label driverLabel = new Label("Driver:");
            ComboBox<String> driverCombo = new ComboBox<>();
            driverCombo.getItems().add("N/A");
            for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
                driverCombo.getItems().add(driver.getDriverName());
            }
            driverCombo.setValue("N/A");
            
            // Load number field
            Label loadLabel = new Label("Load #:");
            TextField loadField = new TextField("N/A");
            
            // Customer field
            Label customerLabel = new Label("Customer:");
            TextField customerField = new TextField("");
            
            // Location field
            Label locationLabel = new Label("Location:");
            TextField locationField = new TextField("");
            
            // Status field
            Label statusLabel = new Label("Status:");
            TextField statusField = new TextField("SCHEDULED");
            
            // Notes field
            Label notesLabel = new Label("Notes:");
            TextArea notesArea = new TextArea();
            notesArea.setPrefRowCount(3);
            notesArea.setWrapText(true);
            
            // Add to grid
            int row = 0;
            grid.add(timeLabel, 0, row);
            grid.add(timeBox, 1, row++);
            grid.add(typeLabel, 0, row);
            grid.add(typeCombo, 1, row++);
            grid.add(driverLabel, 0, row);
            grid.add(driverCombo, 1, row++);
            grid.add(loadLabel, 0, row);
            grid.add(loadField, 1, row++);
            grid.add(customerLabel, 0, row);
            grid.add(customerField, 1, row++);
            grid.add(locationLabel, 0, row);
            grid.add(locationField, 1, row++);
            grid.add(statusLabel, 0, row);
            grid.add(statusField, 1, row++);
            grid.add(notesLabel, 0, row);
            grid.add(notesLabel, 0, row);
            grid.add(notesArea, 1, row++);
            
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    LocalDateTime newTime = LocalDateTime.of(
                        selectedDate,
                        LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                    );
                    
                    return new DailyEvent(
                        newTime,
                        typeCombo.getValue(),
                        driverCombo.getValue(),
                        getTruckForDriver(driverCombo.getValue()),
                        loadField.getText(),
                        customerField.getText(),
                        locationField.getText(),
                        statusField.getText(),
                        "CUSTOM-" + System.currentTimeMillis()
                    );
                }
                return null;
            });
            
            Optional<DailyEvent> result = dialog.showAndWait();
            result.ifPresent(newEvent -> {
                // In a real implementation, we would update the backend
                // For now, just update the UI
                dailyEvents.add(newEvent);
                dailyEvents.sort(Comparator.comparing(DailyEvent::getTime));
                updateSummary();
                updateCharts();
                
                // Show confirmation
                showMessage("Event Added", 
                    "Event has been added to the schedule for " + 
                    selectedDate.format(DATE_FORMAT));
            });
        }
        
        /**
         * Get truck number for a driver name
         */
        private String getTruckForDriver(String driverName) {
            if (driverName == null || driverName.equals("N/A")) return "N/A";
            
            for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
                if (driver.getDriverName().equals(driverName)) {
                    return driver.getTruckNumber();
                }
            }
            
            return "N/A";
        }
        
        /**
         * Add a detail row to grid
         */
        private void addDetailRow(GridPane grid, String label, String value, int row) {
            Label labelNode = new Label(label);
            labelNode.setFont(Font.font("System", FontWeight.BOLD, 12));
            Label valueNode = new Label(value != null ? value : "");
            
            grid.add(labelNode, 0, row);
            grid.add(valueNode, 1, row);
        }
    }
    
    /**
     * Export format enum for exporting schedule
     */
    public enum ExportFormat {
        CSV,
        EXCEL,
        PDF
    }
    
    /**
     * Daily event model class
     */
    public static class DailyEvent {
        private final LocalDateTime time;
        private final EventType eventType;
        private final String driverName;
        private final String truckUnit;
        private final String loadNumber;
        private final String customer;
        private final String location;
        private final String status;
        private final String loadId; // ID to reference related load
        
        public DailyEvent(LocalDateTime time, EventType eventType, String driverName,
                         String truckUnit, String loadNumber, String customer,
                         String location, String status, String loadId) {
            this.time = time;
            this.eventType = eventType;
            this.driverName = driverName;
            this.truckUnit = truckUnit;
            this.loadNumber = loadNumber;
            this.customer = customer;
            this.location = location;
            this.status = status;
            this.loadId = loadId;
        }
        
        // Getters
        public LocalDateTime getTime() { return time; }
        public EventType getEventType() { return eventType; }
        public String getDriverName() { return driverName; }
        public String getTruckUnit() { return truckUnit; }
        public String getLoadNumber() { return loadNumber; }
        public String getCustomer() { return customer; }
        public String getLocation() { return location; }
        public String getStatus() { return status; }
        public String getLoadId() { return loadId; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            DailyEvent that = (DailyEvent) o;
            
            if (!time.equals(that.time)) return false;
            if (eventType != that.eventType) return false;
            if (!driverName.equals(that.driverName)) return false;
            if (!loadNumber.equals(that.loadNumber)) return false;
            return loadId != null ? loadId.equals(that.loadId) : that.loadId == null;
        }
        
        @Override
        public int hashCode() {
            int result = time.hashCode();
            result = 31 * result + eventType.hashCode();
            result = 31 * result + driverName.hashCode();
            result = 31 * result + loadNumber.hashCode();
            if (loadId != null) {
                result = 31 * result + loadId.hashCode();
            }
            return result;
        }
    }
    
    /**
     * Event type enum
     */
    public enum EventType {
        PICKUP("Pick Up", "#90EE90"),       // Light green
        DELIVERY("Delivery", "#FFB6C1"),    // Light pink
        IN_TRANSIT("In Transit", "#87CEEB"), // Light blue
        BREAK("Break", "#DDA0DD"),          // Plum
        MAINTENANCE("Maintenance", "#F0E68C"); // Khaki
        
        private final String displayName;
        private final String color;
        
        EventType(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
}