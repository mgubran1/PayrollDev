package com.company.payroll.dispatcher;

import com.company.payroll.drivers.Driver;
import com.company.payroll.loads.Load;
import com.company.payroll.utils.GeoCoordinates;
import com.company.payroll.utils.RouteCalculator;
import com.company.payroll.utils.MapProvider;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced dialog for real-time fleet tracking and management
 * 
 * @author Payroll System
 * @version 2.0
 */
public class FleetTrackingDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(FleetTrackingDialog.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CURRENT_USER = "mgubran1";
    private static final LocalDateTime CURRENT_TIME = LocalDateTime.parse("2025-07-02 10:57:38", DATETIME_FORMAT);
    
    // Core components
    private final DispatcherController controller;
    private final Timeline refreshTimeline;
    private final ObjectProperty<DispatcherDriverStatus> selectedDriver = new SimpleObjectProperty<>();
    
    // UI Components - Map
    private WebView mapView;
    private WebEngine mapEngine;
    private StackPane mapContainer;
    private ComboBox<String> mapTypeCombo;
    
    // UI Components - Driver list
    private TableView<DispatcherDriverStatus> driversTable;
    private FilteredList<DispatcherDriverStatus> filteredDrivers;
    private TextField searchField;
    private ComboBox<String> statusFilterCombo;
    
    // UI Components - Details panels
    private TitledPane driverDetailsPane;
    private GridPane driverDetailsGrid;
    private TitledPane loadDetailsPane;
    private GridPane loadDetailsGrid;
    private TitledPane routeDetailsPane;
    private VBox routeDetailsBox;
    
    // UI Components - History/Charts
    private LineChart<Number, Number> speedChart;
    private TableView<LocationHistoryEntry> locationHistoryTable;
    
    // UI Components - Controls
    private Label statusLabel;
    private ProgressIndicator refreshIndicator;
    private ToggleButton autoRefreshToggle;
    private Label lastUpdatedLabel;
    
    // Tracking data
    private Map<String, DriverMarker> driverMarkers = new HashMap<>();
    
    /**
     * Constructor initializing dialog with controller and owner window
     */
    public FleetTrackingDialog(DispatcherController controller, Window owner) {
        this.controller = controller;
        
        initOwner(owner);
        initModality(Modality.NONE); // Allow user to interact with other windows
        
        setTitle("Fleet Tracking");
        setHeaderText("Real-Time Fleet Tracking and Management");
        
        // Setup styling
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/fleet-tracking.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("fleet-tracking-dialog");
        
        // Initialize the UI
        initializeUI();
        setupBindings();
        
        // Initialize map
        initializeMap();
        
        // Set up auto-refresh
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshData()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        
        // Initial data load
        loadData();
        
        // Add close button
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        
        // Handle closing
        setOnCloseRequest(e -> {
            stopAutoRefresh();
            logger.info("Fleet tracking dialog closed by {}", CURRENT_USER);
        });
        
        logger.info("Fleet tracking dialog initialized by {} at {}", CURRENT_USER, CURRENT_TIME.format(DATETIME_FORMAT));
    }
    
    /**
     * Initialize the UI components
     */
    private void initializeUI() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPrefSize(1200, 800);
        
        // Create the map view panel (center)
        mapContainer = createMapPanel();
        mainLayout.setCenter(mapContainer);
        
        // Create the drivers list (left)
        VBox driversPanel = createDriversPanel();
        mainLayout.setLeft(driversPanel);
        
        // Create details panel (right)
        VBox detailsPanel = createDetailsPanel();
        mainLayout.setRight(detailsPanel);
        
        // Create status bar (bottom)
        HBox statusBar = createStatusBar();
        mainLayout.setBottom(statusBar);
        
        // Create toolbar (top)
        HBox toolBar = createToolBar();
        mainLayout.setTop(toolBar);
        
        getDialogPane().setContent(mainLayout);
    }
    
    /**
     * Create the map panel
     */
    private StackPane createMapPanel() {
        StackPane mapStack = new StackPane();
        mapStack.setMinSize(600, 500);
        mapStack.getStyleClass().add("map-container");
        
        mapView = new WebView();
        mapView.setPrefSize(800, 600);
        
        mapEngine = mapView.getEngine();
        
        ProgressIndicator mapLoading = new ProgressIndicator();
        mapLoading.setMaxSize(100, 100);
        mapLoading.visibleProperty().bind(mapEngine.getLoadWorker().runningProperty());
        
        // Map controls overlay
        VBox mapControls = new VBox(10);
        mapControls.setAlignment(Pos.TOP_RIGHT);
        mapControls.setPadding(new Insets(10));
        
        mapTypeCombo = new ComboBox<>();
        mapTypeCombo.getItems().addAll("Road", "Satellite", "Hybrid", "Terrain");
        mapTypeCombo.setValue("Road");
        mapTypeCombo.setOnAction(e -> changeMapType(mapTypeCombo.getValue()));
        
        Button zoomInBtn = createControlButton("+", "Zoom in", e -> zoomMap(1));
        Button zoomOutBtn = createControlButton("-", "Zoom out", e -> zoomMap(-1));
        Button centerBtn = createControlButton("⌂", "Center map", e -> centerMap());
        Button refreshBtn = createControlButton("↻", "Refresh map", e -> refreshMap());
        Button trackAllBtn = createControlButton("👁", "Show all drivers", e -> trackAllDrivers());
        
        HBox zoomBox = new HBox(5, zoomOutBtn, zoomInBtn);
        VBox buttonBox = new VBox(5, mapTypeCombo, zoomBox, centerBtn, refreshBtn, trackAllBtn);
        buttonBox.setStyle("-fx-background-color: rgba(255,255,255,0.8); -fx-padding: 5; -fx-spacing: 5;");
        
        mapControls.getChildren().add(buttonBox);
        
        mapStack.getChildren().addAll(mapView, mapLoading, mapControls);
        StackPane.setAlignment(mapControls, Pos.TOP_RIGHT);
        StackPane.setMargin(mapControls, new Insets(10));
        
        return mapStack;
    }
    
    /**
     * Create the drivers list panel
     */
    private VBox createDriversPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(350);
        panel.getStyleClass().add("drivers-panel");
        
        // Search and filter controls
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        searchField = new TextField();
        searchField.setPromptText("Search drivers...");
        searchField.setPrefWidth(200);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
        });
        
        searchBox.getChildren().addAll(new Label("🔍"), searchField);
        
        // Status filter
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        statusFilterCombo = new ComboBox<>();
        statusFilterCombo.getItems().addAll(
            "All", "Available", "On Road", "Loading/Unloading", "Off Duty", "Break"
        );
        statusFilterCombo.setValue("All");
        statusFilterCombo.setOnAction(e -> applyFilters());
        
        filterBox.getChildren().addAll(new Label("Status:"), statusFilterCombo);
        
        // Drivers table
        driversTable = new TableView<>();
        driversTable.getStyleClass().add("drivers-table");
        driversTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<DispatcherDriverStatus, String> nameCol = new TableColumn<>("Driver");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
        
        TableColumn<DispatcherDriverStatus, DispatcherDriverStatus.Status> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(createStatusCellFactory());
        
        TableColumn<DispatcherDriverStatus, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        
        driversTable.getColumns().addAll(nameCol, statusCol, locationCol);
        
        // Create observable list and filtered wrapper
        ObservableList<DispatcherDriverStatus> driversList = FXCollections.observableArrayList();
        filteredDrivers = new FilteredList<>(driversList);
        driversTable.setItems(filteredDrivers);
        
        // Selection listener
        driversTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedDriver.set(newVal);
            if (newVal != null) {
                updateDriverDetails(newVal);
                trackDriver(newVal);
                updateLocationHistory(newVal);
                updateSpeedChart(newVal);
            }
        });
        
        // Quick actions bar
        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER);
        
        Button trackBtn = new Button("Track");
        trackBtn.setOnAction(e -> {
            DispatcherDriverStatus selected = selectedDriver.get();
            if (selected != null) {
                trackDriver(selected);
            }
        });
        trackBtn.disableProperty().bind(selectedDriver.isNull());
        
        Button messageBtn = new Button("Message");
        messageBtn.setOnAction(e -> {
            DispatcherDriverStatus selected = selectedDriver.get();
            if (selected != null) {
                showMessageDialog(selected);
            }
        });
        messageBtn.disableProperty().bind(selectedDriver.isNull());
        
        Button updateBtn = new Button("Update Status");
        updateBtn.setOnAction(e -> {
            DispatcherDriverStatus selected = selectedDriver.get();
            if (selected != null) {
                showUpdateStatusDialog(selected);
            }
        });
        updateBtn.disableProperty().bind(selectedDriver.isNull());
        
        quickActions.getChildren().addAll(trackBtn, messageBtn, updateBtn);
        
        panel.getChildren().addAll(
            new Label("Drivers"), searchBox, filterBox, 
            driversTable, quickActions);
        
        VBox.setVgrow(driversTable, Priority.ALWAYS);
        
        return panel;
    }
    
    /**
     * Create the details panel
     */
    private VBox createDetailsPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(350);
        panel.getStyleClass().add("details-panel");
        
        // Driver details
        driverDetailsGrid = new GridPane();
        driverDetailsGrid.setHgap(10);
        driverDetailsGrid.setVgap(5);
        driverDetailsGrid.setPadding(new Insets(10));
        
        driverDetailsPane = new TitledPane("Driver Details", driverDetailsGrid);
        driverDetailsPane.setCollapsible(true);
        driverDetailsPane.setExpanded(true);
        
        // Load details
        loadDetailsGrid = new GridPane();
        loadDetailsGrid.setHgap(10);
        loadDetailsGrid.setVgap(5);
        loadDetailsGrid.setPadding(new Insets(10));
        
        loadDetailsPane = new TitledPane("Current Load", loadDetailsGrid);
        loadDetailsPane.setCollapsible(true);
        loadDetailsPane.setExpanded(true);
        
        // Route details
        routeDetailsBox = new VBox(10);
        routeDetailsBox.setPadding(new Insets(10));
        
        routeDetailsPane = new TitledPane("Route Information", routeDetailsBox);
        routeDetailsPane.setCollapsible(true);
        routeDetailsPane.setExpanded(true);
        
        // Speed chart
        NumberAxis xAxis = new NumberAxis("Time", 0, 60, 15);
        NumberAxis yAxis = new NumberAxis("Speed (mph)", 0, 100, 10);
        speedChart = new LineChart<>(xAxis, yAxis);
        speedChart.setTitle("Speed History");
        speedChart.setCreateSymbols(false);
        speedChart.setAnimated(false);
        
        // Location history
        locationHistoryTable = new TableView<>();
        locationHistoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<LocationHistoryEntry, LocalDateTime> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setCellFactory(col -> new TableCell<LocationHistoryEntry, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
            }
        });
        
        TableColumn<LocationHistoryEntry, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        
        TableColumn<LocationHistoryEntry, Double> speedCol = new TableColumn<>("Speed");
        speedCol.setCellValueFactory(new PropertyValueFactory<>("speed"));
        speedCol.setCellFactory(col -> new TableCell<LocationHistoryEntry, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f mph", item));
                }
            }
        });
        
        locationHistoryTable.getColumns().addAll(timeCol, locationCol, speedCol);
        
        TitledPane historyPane = new TitledPane("Location History", locationHistoryTable);
        historyPane.setCollapsible(true);
        
        // Accordion to save space
        Accordion accordion = new Accordion();
        accordion.getPanes().addAll(
            driverDetailsPane, loadDetailsPane, routeDetailsPane, 
            new TitledPane("Speed Chart", speedChart), historyPane
        );
        
        panel.getChildren().add(accordion);
        
        return panel;
    }
    
    /**
     * Create the toolbar
     */
    private HBox createToolBar() {
        HBox toolBar = new HBox(10);
        toolBar.setPadding(new Insets(10));
        toolBar.setAlignment(Pos.CENTER_LEFT);
        toolBar.getStyleClass().add("tool-bar");
        
        Label titleLabel = new Label("Fleet Tracking");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        ToggleGroup viewGroup = new ToggleGroup();
        
        ToggleButton mapViewBtn = new ToggleButton("Map View");
        mapViewBtn.setSelected(true);
        mapViewBtn.setToggleGroup(viewGroup);
        mapViewBtn.setOnAction(e -> showMapView());
        
        ToggleButton tableViewBtn = new ToggleButton("Table View");
        tableViewBtn.setToggleGroup(viewGroup);
        tableViewBtn.setOnAction(e -> showTableView());
        
        ToggleButton dashboardBtn = new ToggleButton("Dashboard");
        dashboardBtn.setToggleGroup(viewGroup);
        dashboardBtn.setOnAction(e -> showDashboardView());
        
        // Extra controls
        Button settingsBtn = new Button("⚙");
        settingsBtn.setTooltip(new Tooltip("Settings"));
        settingsBtn.setOnAction(e -> showSettings());
        
        Button helpBtn = new Button("?");
        helpBtn.setTooltip(new Tooltip("Help"));
        helpBtn.setOnAction(e -> showHelp());
        
        toolBar.getChildren().addAll(
            titleLabel, spacer, 
            mapViewBtn, tableViewBtn, dashboardBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            settingsBtn, helpBtn
        );
        
        return toolBar;
    }
    
    /**
     * Create the status bar
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        statusLabel = new Label("Ready");
        
        refreshIndicator = new ProgressIndicator();
        refreshIndicator.setMaxSize(16, 16);
        refreshIndicator.setVisible(false);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        autoRefreshToggle = new ToggleButton("Auto Refresh");
        autoRefreshToggle.setSelected(true);
        autoRefreshToggle.setOnAction(e -> {
            if (autoRefreshToggle.isSelected()) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
        
        lastUpdatedLabel = new Label("Last updated: " + CURRENT_TIME.format(DATETIME_FORMAT));
        
        statusBar.getChildren().addAll(statusLabel, refreshIndicator, spacer, autoRefreshToggle, lastUpdatedLabel);
        
        return statusBar;
    }
    
    /**
     * Initialize the map
     */
    private void initializeMap() {
        try {
            // Load the map HTML template
            InputStream mapTemplateStream = getClass().getResourceAsStream("/templates/tracking_map.html");
            Scanner scanner = new Scanner(mapTemplateStream, "UTF-8");
            String mapTemplate = scanner.useDelimiter("\\A").next();
            scanner.close();
            
            // Initialize with the HTML template
            mapEngine.loadContent(mapTemplate);
            
            // Wait for map to be ready
            mapEngine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
                if (newDoc != null) {
                    // Map is loaded, initialize JavaScript bridge
                    initializeJavaScriptBridge();
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to initialize map", e);
            statusLabel.setText("Map initialization failed");
            
            // Fallback to blank map with error
            mapEngine.loadContent("<html><body style='background:#f0f0f0;'>" +
                "<div style='padding:20px;'>Map loading failed. " +
                "Please check your internet connection and try again.</div></body></html>");
        }
    }
    
    /**
     * Set up JavaScript bridge between JavaFX and the map
     */
    private void initializeJavaScriptBridge() {
        // Set up driver click event
        mapEngine.executeScript(
            "window.onDriverMarkerClicked = function(driverId) {" +
            "    window.java.onDriverMarkerClicked(driverId);" +
            "};"
        );
        
        // Register Java callback for driver marker clicks
        // Note: This is a simplified example, real implementation would use proper JSObject
        // and the netscape.javascript.JSObject package
        // I'm using a dummy implementation for demonstration
        
        // In a real implementation, we'd have:
        /*
        JSObject window = (JSObject) mapEngine.executeScript("window");
        window.setMember("java", new JavaScriptCallback());
        */
        
        // Instead, we'll simulate map setup completion
        Platform.runLater(() -> {
            logger.info("Map initialized");
            statusLabel.setText("Map initialized");
            centerMap();
        });
    }
    
    /**
     * Set up data bindings
     */
    private void setupBindings() {
        // Disable detail panels when no driver is selected
        driverDetailsPane.disableProperty().bind(selectedDriver.isNull());
        loadDetailsPane.disableProperty().bind(selectedDriver.isNull());
        routeDetailsPane.disableProperty().bind(selectedDriver.isNull());
    }
    
    /**
     * Load initial data
     */
    private void loadData() {
        refreshIndicator.setVisible(true);
        statusLabel.setText("Loading data...");
        
        // Get all driver statuses
        ObservableList<DispatcherDriverStatus> allDrivers = controller.getDriverStatuses();
        
        // Update our filtered list
        filteredDrivers.setPredicate(driver -> true); // Show all initially
        driversTable.setItems(filteredDrivers);
        
        // Add drivers to map
        allDrivers.forEach(this::addDriverToMap);
        
        refreshIndicator.setVisible(false);
        statusLabel.setText("Loaded " + allDrivers.size() + " drivers");
        lastUpdatedLabel.setText("Last updated: " + LocalDateTime.now().format(DATETIME_FORMAT));
        
        // If auto-refresh is enabled, start the timeline
        if (autoRefreshToggle.isSelected()) {
            startAutoRefresh();
        }
    }
    
    /**
     * Refresh data from controller
     */
    private void refreshData() {
        if (!isShowing()) return;
        
        refreshIndicator.setVisible(true);
        statusLabel.setText("Refreshing data...");
        
        // Preserve selection
        DispatcherDriverStatus currentSelection = selectedDriver.get();
        String currentDriverId = currentSelection != null ? currentSelection.getDriverId() : null;
        
        // Refresh driver statuses
        controller.refreshData();
        ObservableList<DispatcherDriverStatus> refreshedDrivers = controller.getDriverStatuses();
        
        // Update map markers
        for (DispatcherDriverStatus driver : refreshedDrivers) {
            updateDriverOnMap(driver);
        }
        
        // Restore selection if possible
        if (currentDriverId != null) {
            for (DispatcherDriverStatus driver : refreshedDrivers) {
                if (driver.getDriverId().equals(currentDriverId)) {
                    Platform.runLater(() -> {
                        driversTable.getSelectionModel().select(driver);
                    });
                    break;
                }
            }
        }
        
        refreshIndicator.setVisible(false);
        statusLabel.setText("Data refreshed");
        lastUpdatedLabel.setText("Last updated: " + LocalDateTime.now().format(DATETIME_FORMAT));
    }
    
    /**
     * Add a driver marker to the map
     */
    private void addDriverToMap(DispatcherDriverStatus driver) {
        // In a real implementation, this would use proper map API calls
        // For this example, I'm simulating the functionality
        
        DriverMarker marker = new DriverMarker(
            driver.getDriverId(),
            driver.getDriverName(),
            getRandomCoordinates(), // Simulated location
            driver.getStatus(),
            driver.getCurrentSpeed(),
            driver.getHeading()
        );
        
        driverMarkers.put(driver.getDriverId(), marker);
        
        // In real implementation, call JavaScript to add marker
        // mapEngine.executeScript("addDriverMarker('" + marker.toJson() + "')");
        
        logger.debug("Added driver marker for {}", driver.getDriverName());
    }
    
    /**
     * Update a driver's position on the map
     */
    private void updateDriverOnMap(DispatcherDriverStatus driver) {
        DriverMarker marker = driverMarkers.get(driver.getDriverId());
        
        if (marker == null) {
            addDriverToMap(driver);
            return;
        }
        
        // Update marker position (simulated)
        marker.setCoordinates(getRandomCoordinates());
        marker.setStatus(driver.getStatus());
        marker.setSpeed(driver.getCurrentSpeed());
        marker.setHeading(driver.getHeading());
        
        // In real implementation, call JavaScript to update marker
        // mapEngine.executeScript("updateDriverMarker('" + marker.toJson() + "')");
        
        logger.debug("Updated driver marker for {}", driver.getDriverName());
    }
    
    /**
     * Track a specific driver on the map
     */
    private void trackDriver(DispatcherDriverStatus driver) {
        // Center map on driver and zoom in
        DriverMarker marker = driverMarkers.get(driver.getDriverId());
        
        if (marker != null) {
            // In real implementation, call JavaScript to center on driver
            // mapEngine.executeScript("centerOnDriver('" + driver.getDriverId() + "')");
            
            statusLabel.setText("Tracking driver: " + driver.getDriverName());
            logger.debug("Tracking driver {}", driver.getDriverName());
        }
    }
    
    /**
     * Track all drivers by showing the entire fleet
     */
    private void trackAllDrivers() {
        // In real implementation, call JavaScript to show all drivers
        // mapEngine.executeScript("showAllDrivers()");
        
        statusLabel.setText("Showing all drivers");
        logger.debug("Showing all drivers on map");
    }
    
    /**
     * Change the map type
     */
    private void changeMapType(String mapType) {
        // In real implementation, call JavaScript to change map type
        // mapEngine.executeScript("changeMapType('" + mapType + "')");
        
        statusLabel.setText("Map type changed to: " + mapType);
    }
    
    /**
     * Zoom the map
     */
    private void zoomMap(int zoomDelta) {
        // In real implementation, call JavaScript to zoom
        // mapEngine.executeScript("zoomMap(" + zoomDelta + ")");
        
        statusLabel.setText("Zoom " + (zoomDelta > 0 ? "in" : "out"));
    }
    
    /**
     * Center the map on default location
     */
    private void centerMap() {
        // In real implementation, call JavaScript to center map
        // mapEngine.executeScript("centerMap()");
        
        statusLabel.setText("Map centered");
    }
    
    /**
     * Refresh the map
     */
    private void refreshMap() {
        // In real implementation, reload map or refresh markers
        // mapEngine.executeScript("refreshMap()");
        
        statusLabel.setText("Map refreshed");
    }
    
    /**
     * Show map view (default)
     */
    private void showMapView() {
        mapContainer.getChildren().get(0).setVisible(true);
        statusLabel.setText("Map view");
    }
    
    /**
     * Show table view (alternative view)
     */
    private void showTableView() {
        // In a real implementation, this would switch to a table view of all driver locations
        mapContainer.getChildren().get(0).setVisible(false);
        
        // Create table view if needed
        if (mapContainer.getChildren().size() == 1) {
            TableView<DriverMarker> driversLocationTable = new TableView<>();
            driversLocationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            
            TableColumn<DriverMarker, String> nameCol = new TableColumn<>("Driver");
            nameCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
            
            TableColumn<DriverMarker, DispatcherDriverStatus.Status> statusCol = new TableColumn<>("Status");
            statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
            statusCol.setCellFactory(createStatusCellFactory());
            
            TableColumn<DriverMarker, GeoCoordinates> locationCol = new TableColumn<>("Coordinates");
            locationCol.setCellValueFactory(new PropertyValueFactory<>("coordinates"));
            
            TableColumn<DriverMarker, Double> speedCol = new TableColumn<>("Speed");
            speedCol.setCellValueFactory(new PropertyValueFactory<>("speed"));
            
            TableColumn<DriverMarker, Double> headingCol = new TableColumn<>("Heading");
            headingCol.setCellValueFactory(new PropertyValueFactory<>("heading"));
            
            driversLocationTable.getColumns().addAll(nameCol, statusCol, locationCol, speedCol, headingCol);
            driversLocationTable.setItems(FXCollections.observableArrayList(driverMarkers.values()));
            
            mapContainer.getChildren().add(driversLocationTable);
        } else {
            mapContainer.getChildren().get(1).setVisible(true);
        }
        
        statusLabel.setText("Table view");
    }
    
    /**
     * Show dashboard view (summary)
     */
    private void showDashboardView() {
        // In a real implementation, this would show a dashboard with KPIs
        // For now, just a placeholder
        mapContainer.getChildren().get(0).setVisible(false);
        
        if (mapContainer.getChildren().size() <= 2) {
            GridPane dashboard = new GridPane();
            dashboard.setHgap(20);
            dashboard.setVgap(20);
            dashboard.setPadding(new Insets(20));
            
            // Sample KPI tiles
            dashboard.add(createKpiTile("Active Drivers", "12", "#4CAF50"), 0, 0);
            dashboard.add(createKpiTile("In Transit", "8", "#2196F3"), 1, 0);
            dashboard.add(createKpiTile("Idle", "3", "#FFC107"), 2, 0);
            dashboard.add(createKpiTile("Off Duty", "1", "#607D8B"), 3, 0);
            
            dashboard.add(createKpiTile("Fleet Utilization", "76%", "#9C27B0"), 0, 1);
            dashboard.add(createKpiTile("On-Time Rate", "94%", "#4CAF50"), 1, 1);
            dashboard.add(createKpiTile("Average Speed", "58 mph", "#FF9800"), 2, 1);
            dashboard.add(createKpiTile("Active Loads", "15", "#2196F3"), 3, 1);
            
            mapContainer.getChildren().add(dashboard);
        } else {
            // Dashboard already exists, just show it
            mapContainer.getChildren().get(2).setVisible(true);
        }
        
        statusLabel.setText("Dashboard view");
    }
    
    /**
     * Update driver details panel
     */
    private void updateDriverDetails(DispatcherDriverStatus driver) {
        driverDetailsGrid.getChildren().clear();
        
        int row = 0;
        addDetailRow(driverDetailsGrid, "Name:", driver.getDriverName(), row++);
        addDetailRow(driverDetailsGrid, "Status:", driver.getStatus().getDisplayName(), row++);
        addDetailRow(driverDetailsGrid, "Location:", driver.getLocation(), row++);
        addDetailRow(driverDetailsGrid, "Truck:", driver.getTruckNumber(), row++);
        addDetailRow(driverDetailsGrid, "Trailer:", driver.getTrailerNumber(), row++);
        
        if (driver.getETA() != null) {
            addDetailRow(driverDetailsGrid, "ETA:", driver.getETA().format(DATETIME_FORMAT), row++);
        }
        
        addDetailRow(driverDetailsGrid, "Hours Today:", String.format("%.1f", driver.getHoursWorkedToday()), row++);
        addDetailRow(driverDetailsGrid, "Hours Week:", String.format("%.1f", driver.getHoursWorkedWeek()), row++);
        
        // Update load details if driver has a current load
        updateLoadDetails(driver.getCurrentLoad());
        
        // Update route details
        updateRouteDetails(driver);
    }
    
    /**
     * Update load details panel
     */
    private void updateLoadDetails(Load load) {
        loadDetailsGrid.getChildren().clear();
        
        if (load == null) {
            loadDetailsGrid.add(new Label("No active load"), 0, 0);
            return;
        }
        
        int row = 0;
        addDetailRow(loadDetailsGrid, "Load #:", load.getLoadNumber(), row++);
        addDetailRow(loadDetailsGrid, "Customer:", load.getCustomer(), row++);
        addDetailRow(loadDetailsGrid, "Origin:", load.getOriginCity() + ", " + load.getOriginState(), row++);
        addDetailRow(loadDetailsGrid, "Destination:", load.getDestCity() + ", " + load.getDestState(), row++);
        addDetailRow(loadDetailsGrid, "Pickup Date:", load.getPickupDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")), row++);
        addDetailRow(loadDetailsGrid, "Delivery Date:", load.getDeliveryDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")), row++);
        addDetailRow(loadDetailsGrid, "Status:", load.getStatus().toString(), row++);
        addDetailRow(loadDetailsGrid, "Miles:", String.format("%.0f", load.getMiles()), row++);
    }
    
    /**
     * Update route details panel
     */
    private void updateRouteDetails(DispatcherDriverStatus driver) {
        routeDetailsBox.getChildren().clear();
        
        Load load = driver.getCurrentLoad();
        if (load == null) {
            routeDetailsBox.getChildren().add(new Label("No active route"));
            return;
        }
        
        // Route summary
        Label summaryLabel = new Label(load.getOriginCity() + ", " + load.getOriginState() + 
            " → " + load.getDestCity() + ", " + load.getDestState());
        summaryLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        // Progress bar for route completion
        double progress = 0.75; // Simulated progress
        ProgressBar progressBar = new ProgressBar(progress);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        
        Label progressLabel = new Label(String.format("%.0f%% complete", progress * 100));
        progressLabel.setAlignment(Pos.CENTER_RIGHT);
        
        // ETA and distance
        HBox etaBox = new HBox(10);
        etaBox.setAlignment(Pos.CENTER);
        
        VBox etaInfo = new VBox(5);
        etaInfo.setAlignment(Pos.CENTER);
        Label etaLabel = new Label("ETA");
        Label etaValue = new Label(driver.getETA() != null ? 
            driver.getETA().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) : "N/A");
        etaInfo.getChildren().addAll(etaLabel, etaValue);
        
        VBox distanceInfo = new VBox(5);
        distanceInfo.setAlignment(Pos.CENTER);
        Label distanceLabel = new Label("Distance");
        Label distanceValue = new Label(String.format("%.0f miles", load.getMiles()));
        distanceInfo.getChildren().addAll(distanceLabel, distanceValue);
        
        VBox remainingInfo = new VBox(5);
        remainingInfo.setAlignment(Pos.CENTER);
        Label remainingLabel = new Label("Remaining");
        Label remainingValue = new Label(String.format("%.0f miles", load.getMiles() * (1 - progress)));
        remainingInfo.getChildren().addAll(remainingLabel, remainingValue);
        
        etaBox.getChildren().addAll(etaInfo, new Separator(javafx.geometry.Orientation.VERTICAL),
            distanceInfo, new Separator(javafx.geometry.Orientation.VERTICAL), remainingInfo);
        
        // Waypoints
        TitledPane waypointsPane = new TitledPane("Waypoints", createWaypointsList(load));
        waypointsPane.setExpanded(false);
        
        routeDetailsBox.getChildren().addAll(
            summaryLabel, progressBar, progressLabel,
            new Separator(), etaBox, waypointsPane
        );
    }
    
    /**
     * Create a list of waypoints for the route
     */
    private Node createWaypointsList(Load load) {
        VBox waypointsBox = new VBox(5);
        
        // Start point
        HBox startBox = new HBox(10);
        startBox.setAlignment(Pos.CENTER_LEFT);
        Circle startCircle = new Circle(5, Color.GREEN);
        Label startLabel = new Label("Start: " + load.getOriginCity() + ", " + load.getOriginState());
        startBox.getChildren().addAll(startCircle, startLabel);
        
        // Some intermediate points (simulated)
        HBox midPoint1 = new HBox(10);
        midPoint1.setAlignment(Pos.CENTER_LEFT);
        Circle mid1Circle = new Circle(5, Color.GRAY);
        Label mid1Label = new Label("Interstate 95");
        midPoint1.getChildren().addAll(mid1Circle, mid1Label);
        
        HBox midPoint2 = new HBox(10);
        midPoint2.setAlignment(Pos.CENTER_LEFT);
        Circle mid2Circle = new Circle(5, Color.GRAY);
        Label mid2Label = new Label("Truck Stop - Flying J");
        midPoint2.getChildren().addAll(mid2Circle, mid2Label);
        
        // End point
        HBox endBox = new HBox(10);
        endBox.setAlignment(Pos.CENTER_LEFT);
        Circle endCircle = new Circle(5, Color.RED);
        Label endLabel = new Label("End: " + load.getDestCity() + ", " + load.getDestState());
        endBox.getChildren().addAll(endCircle, endLabel);
        
        waypointsBox.getChildren().addAll(startBox, midPoint1, midPoint2, endBox);
        
        return new ScrollPane(waypointsBox);
    }
    
    /**
     * Update location history table
     */
    private void updateLocationHistory(DispatcherDriverStatus driver) {
        // Create simulated history data
        List<LocationHistoryEntry> history = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < 20; i++) {
            LocalDateTime timestamp = now.minusMinutes(i * 15);
            String location = "Interstate 95 Mile " + (100 - i);
            double speed = 55 + (Math.random() * 10 - 5);
            
            history.add(new LocationHistoryEntry(timestamp, location, speed));
        }
        
        locationHistoryTable.setItems(FXCollections.observableArrayList(history));
    }
    
    /**
     * Update the speed chart
     */
    private void updateSpeedChart(DispatcherDriverStatus driver) {
        speedChart.getData().clear();
        
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Speed (mph)");
        
        // Generate some sample data
        for (int i = 0; i < 60; i++) {
            double speed = 55 + (Math.sin(i * 0.2) * 10);
            series.getData().add(new XYChart.Data<>(i, speed));
        }
        
        speedChart.getData().add(series);
    }
    
    /**
     * Add a detail row to the grid
     */
    private void addDetailRow(GridPane grid, String label, String value, int row) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font(null, FontWeight.BOLD, 12));
        Label valueNode = new Label(value != null ? value : "");
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
    
    /**
     * Create cell factory for status column
     */
    private Callback<TableColumn<DispatcherDriverStatus, DispatcherDriverStatus.Status>, TableCell<DispatcherDriverStatus, DispatcherDriverStatus.Status>> createStatusCellFactory() {
        return column -> new TableCell<DispatcherDriverStatus, DispatcherDriverStatus.Status>() {
            @Override
            protected void updateItem(DispatcherDriverStatus.Status status, boolean empty) {
                super.updateItem(status, empty);
                
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(status.getDisplayName());
                    setStyle("-fx-background-color: " + status.getColor() + "33; -fx-text-fill: " + status.getColor());
                }
            }
        };
    }
    
    /**
     * Create a button for map controls
     */
    private Button createControlButton(String text, String tooltip, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(handler);
        button.getStyleClass().add("map-control-button");
        return button;
    }
    
    /**
     * Create a KPI tile for the dashboard
     */
    private VBox createKpiTile(String title, String value, String color) {
        VBox tile = new VBox(5);
        tile.setAlignment(Pos.CENTER);
        tile.setPadding(new Insets(15));
        tile.setMinWidth(150);
        tile.setMinHeight(100);
        tile.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        tile.setEffect(new DropShadow(5, Color.gray(0.5, 0.5)));
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        
        tile.getChildren().addAll(titleLabel, valueLabel);
        
        return tile;
    }
    
    /**
     * Apply filters to driver list
     */
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase();
        String statusFilter = statusFilterCombo.getValue();
        
        filteredDrivers.setPredicate(driver -> {
            // Apply text search
            boolean matchesSearch = true;
            if (searchText != null && !searchText.isEmpty()) {
                matchesSearch = driver.getDriverName().toLowerCase().contains(searchText) ||
                               (driver.getLocation() != null && 
                                driver.getLocation().toLowerCase().contains(searchText));
            }
            
            // Apply status filter
            boolean matchesStatus = true;
            if (!"All".equals(statusFilter)) {
                switch (statusFilter) {
                    case "Available":
                        matchesStatus = driver.getStatus() == DispatcherDriverStatus.Status.AVAILABLE;
                        break;
                    case "On Road":
                        matchesStatus = driver.getStatus() == DispatcherDriverStatus.Status.ON_ROAD ||
                                       driver.getStatus() == DispatcherDriverStatus.Status.RETURNING;
                        break;
                    case "Loading/Unloading":
                        matchesStatus = driver.getStatus() == DispatcherDriverStatus.Status.LOADING ||
                                       driver.getStatus() == DispatcherDriverStatus.Status.UNLOADING;
                        break;
                    case "Off Duty":
                        matchesStatus = driver.getStatus() == DispatcherDriverStatus.Status.OFF_DUTY ||
                                       driver.getStatus() == DispatcherDriverStatus.Status.SLEEPER;
                        break;
                    case "Break":
                        matchesStatus = driver.getStatus() == DispatcherDriverStatus.Status.BREAK;
                        break;
                }
            }
            
            return matchesSearch && matchesStatus;
        });
    }
    
    /**
     * Start auto-refresh
     */
    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.play();
            logger.info("Auto-refresh started");
        }
    }
    
    /**
     * Stop auto-refresh
     */
    private void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            logger.info("Auto-refresh stopped");
        }
    }
    
    /**
     * Show message dialog for sending messages to drivers
     */
    private void showMessageDialog(DispatcherDriverStatus driver) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Message Driver");
        alert.setHeaderText("Send Message to " + driver.getDriverName());
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        ComboBox<String> messageTypeCombo = new ComboBox<>();
        messageTypeCombo.getItems().addAll("Text", "Push Notification", "In-Cab Message");
        messageTypeCombo.setValue("Text");
        
        TextArea messageArea = new TextArea();
        messageArea.setPromptText("Enter your message here...");
        messageArea.setPrefRowCount(5);
        
        CheckBox urgentCheck = new CheckBox("Mark as Urgent");
        CheckBox requestResponseCheck = new CheckBox("Request Response");
        
        content.getChildren().addAll(
            new Label("Message Type:"),
            messageTypeCombo,
            new Label("Message:"),
            messageArea,
            urgentCheck,
            requestResponseCheck
        );
        
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().addAll(ButtonType.SEND, ButtonType.CANCEL);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.SEND) {
            // Send message in real implementation
            statusLabel.setText("Message sent to " + driver.getDriverName());
        }
    }
    
    /**
     * Show update status dialog
     */
    private void showUpdateStatusDialog(DispatcherDriverStatus driverStatus) {
        UpdateStatusDialog dialog = new UpdateStatusDialog(controller, getDialogPane().getScene().getWindow());
        
        // Pre-select the driver
        dialog.preSelectDriver(driverStatus);
        
        dialog.showAndWait().ifPresent(success -> {
            if (success) {
                refreshData();
            }
        });
    }
    
    /**
     * Show settings dialog
     */
    private void showSettings() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Tracking Settings");
        alert.setHeaderText("Fleet Tracking Settings");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        ComboBox<String> refreshIntervalCombo = new ComboBox<>();
        refreshIntervalCombo.getItems().addAll("10 seconds", "30 seconds", "1 minute", "5 minutes");
        refreshIntervalCombo.setValue("30 seconds");
        
        CheckBox showSpeedCheck = new CheckBox("Show speed indicators");
        showSpeedCheck.setSelected(true);
        
        CheckBox showRoutesCheck = new CheckBox("Show routes on map");
        showRoutesCheck.setSelected(true);
        
        CheckBox enableAlertsCheck = new CheckBox("Enable alerts for idle drivers");
        enableAlertsCheck.setSelected(true);
        
        grid.add(new Label("Auto-refresh Interval:"), 0, 0);
        grid.add(refreshIntervalCombo, 1, 0);
        grid.add(showSpeedCheck, 0, 1);
        grid.add(showRoutesCheck, 0, 2);
        grid.add(enableAlertsCheck, 0, 3);
        
        alert.getDialogPane().setContent(grid);
        alert.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        alert.showAndWait();
    }
    
    /**
     * Show help dialog
     */
    private void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Fleet Tracking Help");
        alert.setHeaderText("Fleet Tracking Help");
        alert.setContentText(
            "The Fleet Tracking module allows you to view real-time locations of all drivers.\n\n" +
            "• View driver locations on the map\n" +
            "• Filter drivers by status or search by name\n" +
            "• Track routes and estimated arrival times\n" +
            "• View speed and location history\n" +
            "• Send messages to drivers\n" +
            "• Update driver status directly from the map\n\n" +
            "For more information, please refer to the user manual."
        );
        
        alert.showAndWait();
    }
    
    /**
     * Generate random coordinates for simulation
     */
    private GeoCoordinates getRandomCoordinates() {
        // Base coordinates for Chicago area
        double baseLat = 41.8781;
        double baseLng = -87.6298;
        
        // Random offset within approximately 50 miles
        double latOffset = (Math.random() - 0.5) * 0.5;
        double lngOffset = (Math.random() - 0.5) * 0.5;
        
        return new GeoCoordinates(baseLat + latOffset, baseLng + lngOffset);
    }
    
    /**
     * Class for tracking driver markers on the map
     */
    private static class DriverMarker {
        private final String driverId;
        private final String driverName;
        private GeoCoordinates coordinates;
        private DispatcherDriverStatus.Status status;
        private double speed;
        private double heading;
        
        public DriverMarker(String driverId, String driverName, GeoCoordinates coordinates,
                          DispatcherDriverStatus.Status status, double speed, double heading) {
            this.driverId = driverId;
            this.driverName = driverName;
            this.coordinates = coordinates;
            this.status = status;
            this.speed = speed;
            this.heading = heading;
        }
        
        public String getDriverId() { return driverId; }
        public String getDriverName() { return driverName; }
        
        public GeoCoordinates getCoordinates() { return coordinates; }
        public void setCoordinates(GeoCoordinates coordinates) { this.coordinates = coordinates; }
        
        public DispatcherDriverStatus.Status getStatus() { return status; }
        public void setStatus(DispatcherDriverStatus.Status status) { this.status = status; }
        
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        
        public double getHeading() { return heading; }
        public void setHeading(double heading) { this.heading = heading; }
        
        public String toJson() {
            // Simple JSON representation for JavaScript interop
            return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"lat\":%f,\"lng\":%f,\"status\":\"%s\",\"color\":\"%s\"," +
                "\"speed\":%f,\"heading\":%f}",
                driverId, driverName, coordinates.getLatitude(), coordinates.getLongitude(),
                status.getDisplayName(), status.getColor(), speed, heading
            );
        }
    }
    
    /**
     * Class for location history entries
     */
    public static class LocationHistoryEntry {
        private final LocalDateTime timestamp;
        private final String location;
        private final double speed;
        
        public LocationHistoryEntry(LocalDateTime timestamp, String location, double speed) {
            this.timestamp = timestamp;
            this.location = location;
            this.speed = speed;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getLocation() { return location; }
        public double getSpeed() { return speed; }
    }
}