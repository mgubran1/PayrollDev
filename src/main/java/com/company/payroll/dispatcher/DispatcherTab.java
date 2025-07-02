package com.company.payroll.dispatcher;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced Dispatcher Tab with multiple timeline views for load tracking
 * and integration with advanced status dialogs and reports
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherTab.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final DispatcherController controller;
    private final TabPane viewTabs;
    private final AtomicInteger refreshCounter = new AtomicInteger(0);
    private final Label statusLabel;
    private final Label timeLabel;
    private final ProgressIndicator refreshIndicator;
    
    /**
     * Constructor - initializes tab with controller and UI
     */
    public DispatcherTab() {
        super("Dispatcher");
        logger.info("Initializing DispatcherTab at {}", LocalDateTime.now().format(DATETIME_FORMAT));
        
        this.controller = new DispatcherController();
        this.viewTabs = new TabPane();
        this.statusLabel = new Label("Connected");
        this.timeLabel = new Label();
        this.refreshIndicator = new ProgressIndicator(-1);
        refreshIndicator.setVisible(false);
        refreshIndicator.setPrefSize(16, 16);
        
        initializeUI();
        setupBindings();
        setupKeyboardShortcuts();
        startClock();
        
        // Register for controller events
        controller.addStatusChangeListener(this::handleStatusChange);
        
        setClosable(false);
    }
    
    /**
     * Initialize the main UI components
     */
    private void initializeUI() {
        BorderPane mainLayout = new BorderPane();
        
        // Header with controls
        VBox header = createHeader();
        mainLayout.setTop(header);
        
        // Main content - tab pane with different views
        viewTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        viewTabs.getStyleClass().add("dispatcher-tabs");
        
        // Create different view tabs
        Tab dailyTab = new Tab("Daily View");
        dailyTab.setContent(new DispatcherDailyView(controller));
        dailyTab.setGraphic(createTabIcon("/icons/calendar-day.png"));
        
        Tab twelveHourTab = new Tab("12 Hour Grid");
        twelveHourTab.setContent(new DispatcherTimelineView(controller, DispatcherTimelineView.TimelineMode.TWELVE_HOUR));
        twelveHourTab.setGraphic(createTabIcon("/icons/clock.png"));
        
        Tab twentyFourHourTab = new Tab("24 Hour Grid");
        twentyFourHourTab.setContent(new DispatcherTimelineView(controller, DispatcherTimelineView.TimelineMode.TWENTY_FOUR_HOUR));
        twentyFourHourTab.setGraphic(createTabIcon("/icons/clock-24.png"));
        
        Tab weeklyTab = new Tab("Weekly Grid");
        weeklyTab.setContent(new DispatcherWeeklyView(controller));
        weeklyTab.setGraphic(createTabIcon("/icons/calendar-week.png"));
        
        Tab fleetStatusTab = new Tab("Fleet Status");
        fleetStatusTab.setContent(new DispatcherFleetStatusView(controller));
        fleetStatusTab.setGraphic(createTabIcon("/icons/truck.png"));
        
        Tab configTab = new Tab("Configuration");
        configTab.setContent(new DispatcherConfigView(controller));
        configTab.setGraphic(createTabIcon("/icons/settings.png"));
        
        viewTabs.getTabs().addAll(dailyTab, twelveHourTab, twentyFourHourTab, weeklyTab, fleetStatusTab, configTab);
        mainLayout.setCenter(viewTabs);
        
        // Set default tab based on settings
        String defaultView = DispatcherSettings.getInstance().getDefaultView();
        for (Tab tab : viewTabs.getTabs()) {
            if (tab.getText().equals(defaultView)) {
                viewTabs.getSelectionModel().select(tab);
                break;
            }
        }
        
        setContent(mainLayout);
    }
    
    /**
     * Create header panel with title and action buttons
     */
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(10));
        header.getStyleClass().add("header-panel");
        
        // Title and status
        HBox titleBar = new HBox();
        titleBar.setSpacing(20);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Dispatch Control Center");
        titleLabel.getStyleClass().add("header-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusLabel.getStyleClass().add("status-label");
        
        // Refresh status
        HBox refreshBox = new HBox(5);
        refreshBox.setAlignment(Pos.CENTER_RIGHT);
        Label refreshLabel = new Label("Last refresh: Never");
        refreshLabel.getStyleClass().add("refresh-label");
        refreshBox.getChildren().addAll(refreshIndicator, refreshLabel);
        
        // Time display
        timeLabel.getStyleClass().add("time-label");
        
        titleBar.getChildren().addAll(titleLabel, spacer, refreshBox, statusLabel, timeLabel);
        
        // Quick action buttons
        FlowPane actionBar = new FlowPane(10, 10);
        actionBar.setPadding(new Insets(5, 0, 5, 0));
        
        Button refreshBtn = createActionButton("Refresh", "/icons/refresh.png", e -> refresh());
        
        Button assignLoadBtn = createActionButton("Assign Load", "/icons/assign.png", e -> showAssignLoadDialog());
        
        Button updateStatusBtn = createActionButton("Update Status", "/icons/status.png", e -> showUpdateStatusDialog());
        
        Button trackingBtn = createActionButton("Fleet Tracking", "/icons/tracking.png", e -> showFleetTracking());
        
        Button reportsBtn = createActionButton("Reports", "/icons/report.png", e -> showReports());
        
        Button searchBtn = createActionButton("Search", "/icons/search.png", e -> showSearch());
        
        Button helpBtn = createActionButton("Help", "/icons/help.png", e -> showHelp());
        
        actionBar.getChildren().addAll(
            refreshBtn, assignLoadBtn, updateStatusBtn, trackingBtn, reportsBtn, searchBtn, helpBtn
        );
        
        // Search bar
        HBox searchBar = createSearchBar();
        
        header.getChildren().addAll(titleBar, actionBar, searchBar);
        
        return header;
    }
    
    /**
     * Create a styled action button with icon and text
     */
    private Button createActionButton(String text, String iconPath, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        try {
            ImageView icon = new ImageView(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream(iconPath)), 16, 16, true, true));
            button.setGraphic(icon);
        } catch (Exception e) {
            logger.warn("Could not load icon: {}", iconPath, e);
        }
        button.setOnAction(action);
        button.getStyleClass().add("action-button");
        
        return button;
    }
    
    /**
     * Create an icon for tab headers
     */
    private Node createTabIcon(String iconPath) {
        try {
            return new ImageView(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream(iconPath)), 16, 16, true, true));
        } catch (Exception e) {
            logger.warn("Could not load tab icon: {}", iconPath);
            return null;
        }
    }
    
    /**
     * Create search bar for filtering data
     */
    private HBox createSearchBar() {
        HBox searchBar = new HBox(5);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(5, 10, 5, 10));
        searchBar.getStyleClass().add("search-bar");
        
        Label searchIcon = new Label("🔍");
        TextField searchField = new TextField();
        searchField.setPromptText("Search drivers, loads, or locations...");
        searchField.setPrefWidth(300);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        ComboBox<String> searchTypeCombo = new ComboBox<>();
        searchTypeCombo.getItems().addAll("All", "Drivers", "Loads", "Locations");
        searchTypeCombo.setValue("All");
        searchTypeCombo.setPrefWidth(120);
        
        Button clearBtn = new Button("✕");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            controller.clearFilters();
        });
        
        searchField.textProperty().addListener((obs, old, text) -> {
            if (text != null && !text.isEmpty()) {
                controller.applySearchFilter(text, searchTypeCombo.getValue());
            } else {
                controller.clearFilters();
            }
        });
        
        searchBar.getChildren().addAll(searchIcon, searchField, searchTypeCombo, clearBtn);
        
        return searchBar;
    }
    
    /**
     * Set up data bindings
     */
    private void setupBindings() {
        // Bind refresh counter to refresh label
        Bindings.createStringBinding(
            () -> "Last refresh: " + (refreshCounter.get() > 0 ? 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "Never"),
            refreshCounter
        );
    }
    
    /**
     * Set up keyboard shortcuts
     */
    private void setupKeyboardShortcuts() {
        setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case R:
                        refresh();
                        break;
                    case A:
                        showAssignLoadDialog();
                        break;
                    case U:
                        showUpdateStatusDialog();
                        break;
                    case T:
                        showFleetTracking();
                        break;
                    case P:
                        showReports();
                        break;
                    case F:
                        getScene().lookup("TextField").requestFocus();
                        break;
                }
            }
        });
    }
    
    /**
     * Start clock for time display
     */
    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLabel.setText(LocalDateTime.now().format(DATETIME_FORMAT));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }
    
    /**
     * Refresh all dispatcher data
     */
    public void refresh() {
        logger.info("Refreshing dispatcher data");
        refreshIndicator.setVisible(true);
        statusLabel.setText("Refreshing...");
        
        controller.refreshData();
        
        // Simulate async operation
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1.5), e -> {
            refreshIndicator.setVisible(false);
            statusLabel.setText("Connected");
            refreshCounter.incrementAndGet();
        }));
        timer.play();
    }
    
    /**
     * Show assign load dialog
     */
    private void showAssignLoadDialog() {
        logger.info("Opening assign load dialog");
        AssignLoadDialog dialog = new AssignLoadDialog(controller, getTabPane().getScene().getWindow());
        dialog.show();
    }
    
    /**
     * Show update status dialog
     */
    private void showUpdateStatusDialog() {
        logger.info("Opening update status dialog");
        Window window = getTabPane().getScene().getWindow();
        UpdateStatusDialog dialog = new UpdateStatusDialog(controller, window);
        dialog.showAndWait();
    }
    
    /**
     * Show fleet tracking dialog
     */
    private void showFleetTracking() {
        logger.info("Opening fleet tracking dialog");
        FleetTrackingDialog dialog = new FleetTrackingDialog(controller, getTabPane().getScene().getWindow());
        dialog.show();
    }
    
    /**
     * Show reports dialog
     */
    private void showReports() {
        logger.info("Opening reports dialog");
        Window window = getTabPane().getScene().getWindow();
        DispatcherReportsDialog dialog = new DispatcherReportsDialog(controller, window);
        dialog.showAndWait();
    }
    
    /**
     * Show search dialog
     */
    private void showSearch() {
        logger.info("Opening advanced search dialog");
        SearchDialog dialog = new SearchDialog(controller, getTabPane().getScene().getWindow());
        dialog.showAndWait();
    }
    
    /**
     * Show help dialog
     */
    private void showHelp() {
        logger.info("Opening help dialog");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Dispatcher Help");
        alert.setHeaderText("Dispatcher Module Help");
        alert.setContentText(
            "The Dispatcher module allows you to manage drivers, loads, and assignments.\n\n" +
            "• Use the tabs to switch between different views\n" +
            "• The Daily View shows today's schedule\n" +
            "• The Timeline Views show loads on a time grid\n" +
            "• The Fleet Status shows current driver locations\n\n" +
            "Keyboard Shortcuts:\n" +
            "Ctrl+R: Refresh Data\n" +
            "Ctrl+A: Assign Load\n" +
            "Ctrl+U: Update Status\n" +
            "Ctrl+T: Fleet Tracking\n" +
            "Ctrl+P: Reports\n" +
            "Ctrl+F: Focus Search\n\n" +
            "For more help, please consult the user manual or contact support."
        );
        alert.showAndWait();
    }
    
    /**
     * Handle driver status changes from the controller
     */
    private void handleStatusChange(DispatcherDriverStatus oldStatus, DispatcherDriverStatus newStatus) {
        logger.debug("Driver status changed: {} from {} to {}", 
            newStatus.getDriverName(), oldStatus.getStatus(), newStatus.getStatus());
        
        // Update relevant views based on status change
        for (Tab tab : viewTabs.getTabs()) {
            if (tab.getContent() instanceof StatusChangeListener) {
                Platform.runLater(() -> {
                    ((StatusChangeListener) tab.getContent()).onStatusChanged(oldStatus, newStatus);
                });
            }
        }
    }
    
    /**
     * Interface for views that need status change notifications
     */
    public interface StatusChangeListener {
        void onStatusChanged(DispatcherDriverStatus oldStatus, DispatcherDriverStatus newStatus);
    }
}