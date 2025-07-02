package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.effect.DropShadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced Dispatcher Tab with multiple timeline views for load tracking - Modern UI
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
        mainLayout.setStyle("-fx-background-color: #FAFAFA;");
        
        // Header with modern controls
        VBox header = createModernHeader();
        mainLayout.setTop(header);
        
        // Main content - tab pane with different views and modern styling
        viewTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        viewTabs.getStyleClass().add("modern-tab-pane");
        viewTabs.setStyle(
            "-fx-background-color: white; " +
            "-fx-tab-min-width: 120px; " +
            "-fx-tab-max-width: 200px;"
        );
        
        // Style the tab header area
        viewTabs.lookupAll(".tab-header-area").forEach(node -> {
            node.setStyle("-fx-background-color: white;");
        });
        
        // Create different view tabs with icons
        Tab dailyTab = createModernTab("📅 Daily View", new DispatcherDailyView(controller));
        Tab twelveHourTab = createModernTab("🕐 12 Hour Grid", new DispatcherTimelineView(controller, DispatcherTimelineView.TimelineMode.TWELVE_HOUR));
        Tab twentyFourHourTab = createModernTab("🕰 24 Hour Grid", new DispatcherTimelineView(controller, DispatcherTimelineView.TimelineMode.TWENTY_FOUR_HOUR));
        Tab weeklyTab = createModernTab("📆 Weekly Grid", new DispatcherWeeklyView(controller));
        Tab fleetStatusTab = createModernTab("🚛 Fleet Status", new DispatcherFleetStatusView(controller));
        Tab configTab = createModernTab("⚙️ Configuration", new DispatcherConfigView(controller));
        
        viewTabs.getTabs().addAll(dailyTab, twelveHourTab, twentyFourHourTab, weeklyTab, fleetStatusTab, configTab);
        
        // Add listener to style selected tab
        viewTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (oldTab != null) {
                oldTab.setStyle("");
            }
            if (newTab != null) {
                newTab.setStyle("-fx-font-weight: 700; -fx-text-fill: #2196F3;");
            }
        });
        
        // Select first tab and apply initial style
        if (!viewTabs.getTabs().isEmpty()) {
            viewTabs.getSelectionModel().select(0);
            viewTabs.getTabs().get(0).setStyle("-fx-font-weight: 700; -fx-text-fill: #2196F3;");
        }
        
        // Wrap tabs in a container with shadow
        VBox tabContainer = new VBox(viewTabs);
        tabContainer.setStyle(
            "-fx-background-color: white; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);"
        );
        VBox.setVgrow(viewTabs, Priority.ALWAYS);
        
        mainLayout.setCenter(tabContainer);
        
        setContent(mainLayout);
    }
    
    private Tab createModernTab(String text, javafx.scene.Node content) {
        Tab tab = new Tab(text);
        tab.setContent(content);
        tab.setClosable(false);
        tab.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-padding: 8px 16px;"
        );
        return tab;
    }
    
    private VBox createModernHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 3);"
        );
        
        // Title and status
        HBox titleBar = new HBox();
        titleBar.setSpacing(20);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Dispatch Control Center");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: 700; -fx-text-fill: white;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Status card
        HBox statusCard = createStatusCard();
        
        // Time display
        Label timeLabel = new Label();
        timeLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white; -fx-font-weight: 500;");
        controller.bindTimeLabel(timeLabel);
        
        titleBar.getChildren().addAll(titleLabel, spacer, statusCard, timeLabel);
        
        // Quick action buttons
        HBox actionBar = new HBox(12);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        
        Button refreshBtn = createHeaderButton("🔄 Refresh", true);
        refreshBtn.setOnAction(e -> {
            controller.refreshAll();
            showNotification("Data refreshed successfully", false);
        });
        
        Button assignLoadBtn = createHeaderButton("➕ Assign Load", false);
        assignLoadBtn.setOnAction(e -> controller.showAssignLoadDialog());
        
        Button updateStatusBtn = createHeaderButton("📝 Update Status", false);
        updateStatusBtn.setOnAction(e -> controller.showUpdateStatusDialog());
        
        Button trackingBtn = createHeaderButton("🚚 Fleet Tracking", false);
        trackingBtn.setOnAction(e -> controller.showFleetTracking());
        
        Button reportsBtn = createHeaderButton("📊 Reports", false);
        reportsBtn.setOnAction(e -> controller.showReports());
        
        actionBar.getChildren().addAll(refreshBtn, assignLoadBtn, updateStatusBtn, trackingBtn, reportsBtn);
        
        header.getChildren().addAll(titleBar, actionBar);
        
        return header;
    }
    
    private HBox createStatusCard() {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8, 16, 8, 16));
        card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.2); " +
            "-fx-background-radius: 20px;"
        );
        
        // Status indicator
        Label statusDot = new Label("●");
        statusDot.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 16px;");
        
        Label statusLabel = new Label("Connected");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: 600;");
        
        card.getChildren().addAll(statusDot, statusLabel);
        
        // Pulse animation for status dot
        javafx.animation.Timeline pulse = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0), 
                e -> statusDot.setScaleX(1.0)),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0.5), 
                e -> statusDot.setScaleX(1.2)),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), 
                e -> statusDot.setScaleX(1.0))
        );
        pulse.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        pulse.play();
        
        return card;
    }
    
    private Button createHeaderButton(String text, boolean isPrimary) {
        Button button = new Button(text);
        if (isPrimary) {
            button.setStyle(
                "-fx-background-color: white; " +
                "-fx-text-fill: #2196F3; " +
                "-fx-font-weight: 600; " +
                "-fx-font-size: 14px; " +
                "-fx-padding: 10px 20px; " +
                "-fx-background-radius: 25px; " +
                "-fx-cursor: hand;"
            );
        } else {
            button.setStyle(
                "-fx-background-color: rgba(255,255,255,0.2); " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: 600; " +
                "-fx-font-size: 14px; " +
                "-fx-padding: 10px 20px; " +
                "-fx-background-radius: 25px; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: white; " +
                "-fx-border-width: 2px;"
            );
        }
        
        // Hover effects
        button.setOnMouseEntered(e -> {
            if (isPrimary) {
                button.setStyle(button.getStyle() + 
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3); " +
                    "-fx-scale-x: 1.05; -fx-scale-y: 1.05;");
            } else {
                button.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-text-fill: #2196F3; " +
                    "-fx-font-weight: 600; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-cursor: hand; " +
                    "-fx-border-width: 0; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);"
                );
            }
        });
        
        button.setOnMouseExited(e -> {
            if (isPrimary) {
                button.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-text-fill: #2196F3; " +
                    "-fx-font-weight: 600; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-cursor: hand;"
                );
            } else {
                button.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.2); " +
                    "-fx-text-fill: white; " +
                    "-fx-font-weight: 600; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-cursor: hand; " +
                    "-fx-border-color: white; " +
                    "-fx-border-width: 2px;"
                );
            }
        });
        
        return button;
    }
    
    private void showNotification(String message, boolean isError) {
        // Create a temporary notification
        Label notification = new Label(message);
        notification.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10px 20px; " +
            "-fx-background-radius: 5px; " +
            "-fx-font-weight: 600;",
            isError ? "#F44336" : "#4CAF50"
        ));
        
        // Add to top of content temporarily
        BorderPane content = (BorderPane) getContent();
        StackPane notificationPane = new StackPane(notification);
        notificationPane.setAlignment(Pos.TOP_CENTER);
        notificationPane.setPadding(new Insets(10));
        
        content.getChildren().add(notificationPane);
        
        // Fade out after 3 seconds
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(3),
                e -> content.getChildren().remove(notificationPane)
            )
        );
        timeline.play();
    }
    
    public void refresh() {
        controller.refreshAll();
    }
}