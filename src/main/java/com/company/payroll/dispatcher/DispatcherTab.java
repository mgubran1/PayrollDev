package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced Dispatcher Tab with multiple timeline views for load tracking
 */
public class DispatcherTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherTab.class);
    
    private final DispatcherController controller;
    private final TabPane viewTabs;
    
    public DispatcherTab() {
        super("Dispatcher");
        logger.info("Initializing DispatcherTab");
        
        this.controller = new DispatcherController();
        this.viewTabs = new TabPane();
        
        initializeUI();
        setClosable(false);
    }
    
    private void initializeUI() {
        BorderPane mainLayout = new BorderPane();
        
        // Header with controls
        VBox header = createHeader();
        mainLayout.setTop(header);
        
        // Main content - tab pane with different views
        viewTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Create different view tabs
        Tab dailyTab = new Tab("Daily View");
        dailyTab.setContent(new DispatcherDailyView(controller));
        
        Tab twelveHourTab = new Tab("12 Hour Grid");
        twelveHourTab.setContent(new DispatcherTimelineView(controller, DispatcherTimelineView.TimelineMode.TWELVE_HOUR));
        
        Tab twentyFourHourTab = new Tab("24 Hour Grid");
        twentyFourHourTab.setContent(new DispatcherTimelineView(controller, DispatcherTimelineView.TimelineMode.TWENTY_FOUR_HOUR));
        
        Tab weeklyTab = new Tab("Weekly Grid");
        weeklyTab.setContent(new DispatcherWeeklyView(controller));
        
        Tab fleetStatusTab = new Tab("Fleet Status");
        fleetStatusTab.setContent(new DispatcherFleetStatusView(controller));
        
        Tab configTab = new Tab("Configuration");
        configTab.setContent(new DispatcherConfigView(controller));
        
        viewTabs.getTabs().addAll(dailyTab, twelveHourTab, twentyFourHourTab, weeklyTab, fleetStatusTab, configTab);
        mainLayout.setCenter(viewTabs);
        
        setContent(mainLayout);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        // Title and status
        HBox titleBar = new HBox();
        titleBar.setSpacing(20);
        
        Label titleLabel = new Label("Dispatch Control Center");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label statusLabel = new Label("Connected");
        statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        
        Label timeLabel = new Label();
        controller.bindTimeLabel(timeLabel);
        
        titleBar.getChildren().addAll(titleLabel, spacer, statusLabel, timeLabel);
        
        // Quick action buttons
        HBox actionBar = new HBox(10);
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> controller.refreshAll());
        
        Button assignLoadBtn = new Button("➕ Assign Load");
        assignLoadBtn.setOnAction(e -> controller.showAssignLoadDialog());
        
        Button updateStatusBtn = new Button("📝 Update Status");
        updateStatusBtn.setOnAction(e -> controller.showUpdateStatusDialog());
        
        Button trackingBtn = new Button("🚚 Fleet Tracking");
        trackingBtn.setOnAction(e -> controller.showFleetTracking());
        
        Button reportsBtn = new Button("📊 Reports");
        reportsBtn.setOnAction(e -> controller.showReports());
        
        actionBar.getChildren().addAll(refreshBtn, assignLoadBtn, updateStatusBtn, trackingBtn, reportsBtn);
        
        header.getChildren().addAll(titleBar, actionBar);
        
        return header;
    }
    
    public void refresh() {
        controller.refreshAll();
    }
}