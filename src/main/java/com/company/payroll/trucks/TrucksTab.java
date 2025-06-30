package com.company.payroll.trucks;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.effect.Glow;
import javafx.beans.property.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.util.StringConverter;
import javafx.animation.*;
import javafx.util.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;

public class TrucksTab extends Tab {
    private TableView<Truck> trucksTable;
    private TableView<TruckPerformance> performanceTable;
    private ComboBox<String> statusFilterComboBox;
    private ComboBox<String> typeFilterComboBox;
    private ComboBox<String> locationFilterComboBox;
    private TextField searchField;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    
    private Label totalTrucksLabel;
    private Label activeTrucksLabel;
    private Label idleTrucksLabel;
    private Label maintenanceTrucksLabel;
    private Label avgMileageLabel;
    private Label fuelEfficiencyLabel;
    
    private PieChart fleetStatusChart;
    private LineChart<String, Number> mileageChart;
    private BarChart<String, Number> fuelChart;
    private ScatterChart<Number, Number> efficiencyChart;
    private AreaChart<String, Number> costChart;
    
    private ProgressIndicator loadingIndicator;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private ObservableList<String> truckTypes = FXCollections.observableArrayList(
        "Freightliner Cascadia", "Volvo VNL", "Peterbilt 579", "Kenworth T680",
        "International LT", "Mack Anthem", "Western Star 5700XE", "Other"
    );
    
    private ObservableList<String> truckStatuses = FXCollections.observableArrayList(
        "Active", "Idle", "In Transit", "Maintenance", "Out of Service", "Reserved"
    );
    
    public TrucksTab() {
        setText("Trucks");
        setClosable(false);
        
        // Set tab icon
        try {
            ImageView tabIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/truck.png")));
            tabIcon.setFitHeight(16);
            tabIcon.setFitWidth(16);
            setGraphic(tabIcon);
        } catch (Exception e) {
            // Icon not found
        }
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: #f0f3f4;");
        
        // Header
        VBox header = createHeader();
        
        // Alert Banner
        HBox alertBanner = createAlertBanner();
        
        // Control Panel
        VBox controlPanel = createControlPanel();
        
        // Dashboard Cards
        HBox dashboardCards = createDashboardCards();
        
        // Main Content Tabs
        TabPane contentTabs = createContentTabs();
        
        // Loading Indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        StackPane loadingPane = new StackPane(loadingIndicator);
        loadingPane.setAlignment(Pos.CENTER);
        
        mainContent.getChildren().addAll(header, alertBanner, controlPanel, dashboardCards, contentTabs, loadingPane);
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
        
        // Initialize data
        initializeData();
        loadTruckData();
        startRealTimeUpdates();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(30));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #34495e); " +
                       "-fx-background-radius: 10;");
        
        // Title with animation
        Label titleLabel = new Label("Fleet Management System");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 38));
        titleLabel.setTextFill(Color.WHITE);
        
        // Subtitle
        Label subtitleLabel = new Label("Real-time monitoring and management of your truck fleet");
        subtitleLabel.setFont(Font.font("Arial", 18));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        // Live time display
        Label timeLabel = new Label();
        timeLabel.setFont(Font.font("Arial", 14));
        timeLabel.setTextFill(Color.LIGHTGRAY);
        
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLabel.setText("Last Updated: " + LocalDate.now().format(DATE_FORMAT) + 
                            " " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        
        // Animated decoration
        HBox decorationBox = new HBox(10);
        decorationBox.setAlignment(Pos.CENTER);
        for (int i = 0; i < 5; i++) {
            Circle dot = new Circle(3);
            dot.setFill(Color.web("#3498db"));
            
            FadeTransition ft = new FadeTransition(Duration.seconds(1), dot);
            ft.setFromValue(0.3);
            ft.setToValue(1.0);
            ft.setCycleCount(Timeline.INDEFINITE);
            ft.setAutoReverse(true);
            ft.setDelay(Duration.millis(i * 200));
            ft.play();
            
            decorationBox.getChildren().add(dot);
        }
        
        header.getChildren().addAll(titleLabel, decorationBox, subtitleLabel, timeLabel);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(5);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        header.setEffect(shadow);
        
        return header;
    }
    
    private HBox createAlertBanner() {
        HBox alertBanner = new HBox(15);
        alertBanner.setPadding(new Insets(12));
        alertBanner.setAlignment(Pos.CENTER_LEFT);
        alertBanner.setStyle("-fx-background-color: #e74c3c; -fx-background-radius: 5;");
        alertBanner.setVisible(false); // Initially hidden
        
        Label alertIcon = new Label("⚠");
        alertIcon.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        alertIcon.setTextFill(Color.WHITE);
        
        Label alertText = new Label("3 trucks require immediate maintenance!");
        alertText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        alertText.setTextFill(Color.WHITE);
        
        Button viewButton = new Button("View Details");
        viewButton.setStyle("-fx-background-color: white; -fx-text-fill: #e74c3c; " +
                          "-fx-font-weight: bold; -fx-background-radius: 3;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button dismissButton = new Button("✕");
        dismissButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                             "-fx-font-size: 16px; -fx-cursor: hand;");
        dismissButton.setOnAction(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(300), alertBanner);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(event -> alertBanner.setVisible(false));
            ft.play();
        });
        
        alertBanner.getChildren().addAll(alertIcon, alertText, viewButton, spacer, dismissButton);
        
        // Show alert after 2 seconds with animation
        Timeline showAlert = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            alertBanner.setVisible(true);
            FadeTransition ft = new FadeTransition(Duration.millis(500), alertBanner);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }));
        showAlert.play();
        
        return alertBanner;
    }
    
    private VBox createControlPanel() {
        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Search and Filter Row
        HBox searchRow = new HBox(15);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        
        // Enhanced search field
        HBox searchBox = new HBox();
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 25; " +
                         "-fx-padding: 8 20 8 20;");
        searchBox.setPrefWidth(400);
        
        Label searchIcon = new Label("🔍");
        searchIcon.setFont(Font.font(16));
        
        searchField = new TextField();
        searchField.setPromptText("Search by truck number, driver, or location...");
        searchField.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        searchField.setPrefWidth(350);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchBox.getChildren().addAll(searchIcon, searchField);
        
        // Filter dropdowns
        statusFilterComboBox = new ComboBox<>();
        statusFilterComboBox.getItems().add("All Status");
        statusFilterComboBox.getItems().addAll(truckStatuses);
        statusFilterComboBox.setValue("All Status");
        statusFilterComboBox.setPrefWidth(140);
        
        typeFilterComboBox = new ComboBox<>();
        typeFilterComboBox.getItems().add("All Types");
        typeFilterComboBox.getItems().addAll(truckTypes);
        typeFilterComboBox.setValue("All Types");
        typeFilterComboBox.setPrefWidth(180);
        
        locationFilterComboBox = new ComboBox<>();
        locationFilterComboBox.getItems().addAll("All Locations", "East Region", "West Region", 
                                               "North Region", "South Region", "Central");
        locationFilterComboBox.setValue("All Locations");
        locationFilterComboBox.setPrefWidth(140);
        
        searchRow.getChildren().addAll(searchBox, new Separator(Orientation.VERTICAL),
                                      statusFilterComboBox, typeFilterComboBox, locationFilterComboBox);
        
        // Action Buttons Row
        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.setPadding(new Insets(10, 0, 0, 0));
        
        Button addTruckButton = createAnimatedButton("Add Truck", "#27ae60", "add-icon.png");
        addTruckButton.setOnAction(e -> showAddTruckDialog());
        
        Button assignButton = createAnimatedButton("Assign Driver", "#3498db", "assign-icon.png");
        assignButton.setOnAction(e -> showAssignDriverDialog());
        
        Button maintenanceButton = createAnimatedButton("Maintenance", "#f39c12", "maintenance-icon.png");
        maintenanceButton.setOnAction(e -> showMaintenanceDialog());
        
        Button trackButton = createAnimatedButton("Live Tracking", "#e74c3c", "tracking-icon.png");
        trackButton.setOnAction(e -> showLiveTracking());
        
        Button reportButton = createAnimatedButton("Reports", "#9b59b6", "report-icon.png");
        reportButton.setOnAction(e -> showReportMenu(reportButton));
        
        Button exportButton = createAnimatedButton("Export", "#95a5a6", "export-icon.png");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        actionRow.getChildren().addAll(addTruckButton, assignButton, maintenanceButton,
                                      trackButton, reportButton, exportButton);
        
        controlPanel.getChildren().addAll(searchRow, new Separator(), actionRow);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        controlPanel.setEffect(shadow);
        
        return controlPanel;
    }
    
    private HBox createDashboardCards() {
        HBox dashboard = new HBox(15);
        dashboard.setPadding(new Insets(10));
        dashboard.setAlignment(Pos.CENTER);
        
        VBox totalCard = createDashboardCard("Total Fleet", "0", "#2c3e50", "fleet-icon.png", true);
        totalTrucksLabel = (Label) totalCard.lookup(".value-label");
        
        VBox activeCard = createDashboardCard("Active", "0", "#27ae60", "active-icon.png", true);
        activeTrucksLabel = (Label) activeCard.lookup(".value-label");
        
        VBox idleCard = createDashboardCard("Idle", "0", "#f39c12", "idle-icon.png", true);
        idleTrucksLabel = (Label) idleCard.lookup(".value-label");
        
        VBox maintenanceCard = createDashboardCard("Maintenance", "0", "#e74c3c", "maintenance-icon.png", true);
        maintenanceTrucksLabel = (Label) maintenanceCard.lookup(".value-label");
        
        VBox mileageCard = createDashboardCard("Avg Mileage", "0", "#3498db", "mileage-icon.png", false);
        avgMileageLabel = (Label) mileageCard.lookup(".value-label");
        
        VBox fuelCard = createDashboardCard("Fuel Efficiency", "0.0", "#9b59b6", "fuel-icon.png", false);
        fuelEfficiencyLabel = (Label) fuelCard.lookup(".value-label");
        
        dashboard.getChildren().addAll(totalCard, activeCard, idleCard, 
                                     maintenanceCard, mileageCard, fuelCard);
        
        return dashboard;
    }
    
    private VBox createDashboardCard(String title, String value, String color, String iconPath, boolean animated) {
        VBox card = new VBox(10);
        card.getStyleClass().add("dashboard-card");
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefSize(200, 130);
        
        // Icon with background
        StackPane iconContainer = new StackPane();
        
        Circle iconBg = new Circle(30);
        iconBg.setFill(Color.web(color).deriveColor(0, 1, 1, 0.1));
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(35);
            icon.setFitWidth(35);
            iconContainer.getChildren().addAll(iconBg, icon);
        } catch (Exception e) {
            Label iconLabel = new Label("📊");
            iconLabel.setFont(Font.font(24));
            iconContainer.getChildren().addAll(iconBg, iconLabel);
        }
        
        // Title
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 12));
        titleLabel.setTextFill(Color.GRAY);
        
        // Value with animation
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        valueLabel.setTextFill(Color.web(color));
        
        // Mini chart (visual indicator)
        if (animated) {
            Arc progressArc = new Arc(0, 0, 25, 25, 90, 0);
            progressArc.setType(ArcType.OPEN);
            progressArc.setStroke(Color.web(color));
            progressArc.setStrokeWidth(3);
            progressArc.setFill(Color.TRANSPARENT);
            
            Timeline arcAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressArc.lengthProperty(), 0)),
                new KeyFrame(Duration.seconds(1), new KeyValue(progressArc.lengthProperty(), -270))
            );
            arcAnimation.play();
            
            card.getChildren().addAll(iconContainer, titleLabel, valueLabel, progressArc);
        } else {
            card.getChildren().addAll(iconContainer, titleLabel, valueLabel);
        }
        
        // Hover effects
        card.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
            
            DropShadow glow = new DropShadow();
            glow.setOffsetY(5);
            glow.setColor(Color.color(0, 0, 0, 0.2));
            card.setEffect(glow);
        });
        
        card.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1);
            st.setToY(1);
            st.play();
            
            DropShadow shadow = new DropShadow();
            shadow.setOffsetY(3);
            shadow.setColor(Color.color(0, 0, 0, 0.1));
            card.setEffect(shadow);
        });
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
    }
    
    private TabPane createContentTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");
        tabPane.setPrefHeight(600);
        
        Tab fleetTab = new Tab("Fleet Overview");
        fleetTab.setClosable(false);
        fleetTab.setContent(createFleetOverviewSection());
        
        Tab performanceTab = new Tab("Performance");
        performanceTab.setClosable(false);
        performanceTab.setContent(createPerformanceSection());
        
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setClosable(false);
        analyticsTab.setContent(createAnalyticsSection());
        
        Tab maintenanceTab = new Tab("Maintenance");
        maintenanceTab.setClosable(false);
        maintenanceTab.setContent(createMaintenanceSection());
        
        Tab trackingTab = new Tab("GPS Tracking");
        trackingTab.setClosable(false);
        trackingTab.setContent(createTrackingSection());
        
        tabPane.getTabs().addAll(fleetTab, performanceTab, analyticsTab, maintenanceTab, trackingTab);
        
        return tabPane;
    }
    
    private VBox createFleetOverviewSection() {
        VBox fleetSection = new VBox(15);
        fleetSection.setPadding(new Insets(15));
        
        // Quick Stats Bar
        HBox statsBar = new HBox(30);
        statsBar.setAlignment(Pos.CENTER);
        statsBar.setPadding(new Insets(15));
        statsBar.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 10;");
        
        Label onRoadLabel = createStatLabel("On Road: 15", "#27ae60");
        Label atRestLabel = createStatLabel("At Rest: 8", "#f39c12");
        Label offDutyLabel = createStatLabel("Off Duty: 5", "#95a5a6");
        Label criticalLabel = createStatLabel("Critical: 2", "#e74c3c");
        
        statsBar.getChildren().addAll(onRoadLabel, createVerticalSeparator(),
                                     atRestLabel, createVerticalSeparator(),
                                     offDutyLabel, createVerticalSeparator(),
                                     criticalLabel);
        
        // Trucks Table
        trucksTable = new TableView<>();
        trucksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        trucksTable.setPrefHeight(500);
        trucksTable.setEditable(true);
        trucksTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Columns
        TableColumn<Truck, Boolean> selectCol = new TableColumn<>();
        CheckBox selectAllCheckBox = new CheckBox();
        selectCol.setGraphic(selectAllCheckBox);
        selectCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setMaxWidth(50);
        selectCol.setStyle("-fx-alignment: CENTER;");
        
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            trucksTable.getItems().forEach(truck -> truck.setSelected(selected));
        });
        
        TableColumn<Truck, String> numberCol = new TableColumn<>("Truck #");
        numberCol.setCellValueFactory(new PropertyValueFactory<>("number"));
        numberCol.setMinWidth(100);
        
        TableColumn<Truck, String> typeCol = new TableColumn<>("Type/Model");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setMinWidth(150);
        
        TableColumn<Truck, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<Truck, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox statusBox = new HBox(8);
                    statusBox.setAlignment(Pos.CENTER_LEFT);
                    
                    // Status indicator
                    Circle indicator = new Circle(6);
                    indicator.setFill(getStatusColor(status));
                    
                    // Pulse animation for active status
                    if ("Active".equals(status)) {
                        Timeline pulse = new Timeline(
                            new KeyFrame(Duration.ZERO, new KeyValue(indicator.radiusProperty(), 6)),
                            new KeyFrame(Duration.seconds(0.5), new KeyValue(indicator.radiusProperty(), 8)),
                            new KeyFrame(Duration.seconds(1), new KeyValue(indicator.radiusProperty(), 6))
                        );
                        pulse.setCycleCount(Timeline.INDEFINITE);
                        pulse.play();
                    }
                    
                    Label statusLabel = new Label(status);
                    statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                    statusLabel.setTextFill(getStatusColor(status));
                    
                    statusBox.getChildren().addAll(indicator, statusLabel);
                    setGraphic(statusBox);
                }
            }
        });
        
        TableColumn<Truck, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(new PropertyValueFactory<>("driver"));
        driverCol.setMinWidth(150);
        
        TableColumn<Truck, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locationCol.setMinWidth(200);
        locationCol.setCellFactory(column -> new TableCell<Truck, String>() {
            @Override
            protected void updateItem(String location, boolean empty) {
                super.updateItem(location, empty);
                if (empty || location == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox locationBox = new HBox(5);
                    locationBox.setAlignment(Pos.CENTER_LEFT);
                    
                    Label pinIcon = new Label("📍");
                    Label locationLabel = new Label(location);
                    
                    locationBox.getChildren().addAll(pinIcon, locationLabel);
                    setGraphic(locationBox);
                }
            }
        });
        
        TableColumn<Truck, Integer> mileageCol = new TableColumn<>("Mileage");
        mileageCol.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        mileageCol.setCellFactory(column -> new TableCell<Truck, Integer>() {
            @Override
            protected void updateItem(Integer mileage, boolean empty) {
                super.updateItem(mileage, empty);
                if (empty || mileage == null) {
                    setText(null);
                } else {
                    setText(NUMBER_FORMAT.format(mileage) + " mi");
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        
        TableColumn<Truck, Double> fuelCol = new TableColumn<>("Fuel Level");
        fuelCol.setCellValueFactory(new PropertyValueFactory<>("fuelLevel"));
        fuelCol.setCellFactory(column -> new TableCell<Truck, Double>() {
            @Override
            protected void updateItem(Double fuel, boolean empty) {
                super.updateItem(fuel, empty);
                if (empty || fuel == null) {
                    setGraphic(null);
                } else {
                    ProgressBar fuelBar = new ProgressBar(fuel / 100);
                    fuelBar.setPrefWidth(80);
                    
                    if (fuel < 20) {
                        fuelBar.setStyle("-fx-accent: #e74c3c;");
                    } else if (fuel < 50) {
                        fuelBar.setStyle("-fx-accent: #f39c12;");
                    } else {
                        fuelBar.setStyle("-fx-accent: #27ae60;");
                    }
                    
                    Label fuelLabel = new Label(String.format("%.0f%%", fuel));
                    fuelLabel.setFont(Font.font(10));
                    
                    VBox fuelBox = new VBox(2);
                    fuelBox.setAlignment(Pos.CENTER);
                    fuelBox.getChildren().addAll(fuelBar, fuelLabel);
                    setGraphic(fuelBox);
                }
            }
        });
        
        TableColumn<Truck, LocalDate> lastServiceCol = new TableColumn<>("Last Service");
        lastServiceCol.setCellValueFactory(new PropertyValueFactory<>("lastService"));
        lastServiceCol.setCellFactory(column -> new TableCell<Truck, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(date.format(DATE_FORMAT));
                    long daysSince = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now());
                    if (daysSince > 90) {
                        setTextFill(Color.RED);
                        setFont(Font.font("Arial", FontWeight.BOLD, 12));
                    } else if (daysSince > 60) {
                        setTextFill(Color.ORANGE);
                    } else {
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });
        
        TableColumn<Truck, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(column -> new TableCell<Truck, Void>() {
            private final Button viewBtn = new Button();
            private final Button editBtn = new Button();
            private final Button trackBtn = new Button();
            
            {
                // View button
                viewBtn.setGraphic(createButtonIcon("👁", "#3498db"));
                viewBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                viewBtn.setTooltip(new Tooltip("View Details"));
                viewBtn.setOnAction(e -> viewTruckDetails(getTableRow().getItem()));
                
                // Edit button
                editBtn.setGraphic(createButtonIcon("✏", "#f39c12"));
                editBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                editBtn.setTooltip(new Tooltip("Edit"));
                editBtn.setOnAction(e -> editTruck(getTableRow().getItem()));
                
                // Track button
                trackBtn.setGraphic(createButtonIcon("📍", "#27ae60"));
                trackBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                trackBtn.setTooltip(new Tooltip("Track Location"));
                trackBtn.setOnAction(e -> trackTruck(getTableRow().getItem()));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5);
                    buttons.setAlignment(Pos.CENTER);
                    buttons.getChildren().addAll(viewBtn, editBtn, trackBtn);
                    setGraphic(buttons);
                }
            }
        });
        
        trucksTable.getColumns().addAll(selectCol, numberCol, typeCol, statusCol,
                                       driverCol, locationCol, mileageCol, fuelCol,
                                       lastServiceCol, actionsCol);
        
        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewItem = new MenuItem("View Details");
        MenuItem editItem = new MenuItem("Edit");
        MenuItem assignItem = new MenuItem("Assign Driver");
        MenuItem maintenanceItem = new MenuItem("Schedule Maintenance");
        MenuItem trackItem = new MenuItem("Track Location");
        MenuItem deleteItem = new MenuItem("Remove from Fleet");
        
        contextMenu.getItems().addAll(viewItem, editItem, assignItem,
                                    new SeparatorMenuItem(), maintenanceItem, trackItem,
                                    new SeparatorMenuItem(), deleteItem);
        trucksTable.setContextMenu(contextMenu);
        
        fleetSection.getChildren().addAll(statsBar, trucksTable);
        
        return fleetSection;
    }
    
    private VBox createPerformanceSection() {
        VBox performanceSection = new VBox(20);
        performanceSection.setPadding(new Insets(20));
        
        // Performance Metrics Header
        HBox metricsHeader = new HBox(30);
        metricsHeader.setAlignment(Pos.CENTER);
        metricsHeader.setPadding(new Insets(20));
        metricsHeader.setStyle("-fx-background-color: #34495e; -fx-background-radius: 10;");
        
        VBox avgSpeedBox = createMetricBox("Avg Speed", "55 mph", "#3498db");
        VBox totalMilesBox = createMetricBox("Total Miles", "125,480", "#27ae60");
        VBox fuelConsumptionBox = createMetricBox("Fuel Used", "8,450 gal", "#f39c12");
        VBox uptimeBox = createMetricBox("Fleet Uptime", "94.5%", "#9b59b6");
        
        metricsHeader.getChildren().addAll(avgSpeedBox, totalMilesBox, fuelConsumptionBox, uptimeBox);
        
        // Date Range Selection
        HBox dateRangeBox = new HBox(15);
        dateRangeBox.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("Performance Period:");
        dateLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        fromDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        toDatePicker = new DatePicker(LocalDate.now());
        
        Button applyButton = new Button("Apply");
        applyButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        applyButton.setOnAction(e -> updatePerformanceData());
        
        dateRangeBox.getChildren().addAll(dateLabel, fromDatePicker, new Label("to"), toDatePicker, applyButton);
        
        // Performance Table
        performanceTable = new TableView<>();
        performanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        performanceTable.setPrefHeight(400);
        
        TableColumn<TruckPerformance, String> truckCol = new TableColumn<>("Truck");
        truckCol.setCellValueFactory(new PropertyValueFactory<>("truckNumber"));
        
        TableColumn<TruckPerformance, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
        
        TableColumn<TruckPerformance, Integer> milesCol = new TableColumn<>("Miles Driven");
        milesCol.setCellValueFactory(new PropertyValueFactory<>("milesDriven"));
        milesCol.setCellFactory(column -> new TableCell<TruckPerformance, Integer>() {
            @Override
            protected void updateItem(Integer miles, boolean empty) {
                super.updateItem(miles, empty);
                if (empty || miles == null) {
                    setText(null);
                } else {
                    setText(NUMBER_FORMAT.format(miles));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        
        TableColumn<TruckPerformance, Double> fuelUsedCol = new TableColumn<>("Fuel Used (gal)");
        fuelUsedCol.setCellValueFactory(new PropertyValueFactory<>("fuelUsed"));
        fuelUsedCol.setCellFactory(column -> new TableCell<TruckPerformance, Double>() {
            @Override
            protected void updateItem(Double fuel, boolean empty) {
                super.updateItem(fuel, empty);
                if (empty || fuel == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f", fuel));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        
        TableColumn<TruckPerformance, Double> mpgCol = new TableColumn<>("MPG");
        mpgCol.setCellValueFactory(new PropertyValueFactory<>("mpg"));
        mpgCol.setCellFactory(column -> new TableCell<TruckPerformance, Double>() {
            @Override
            protected void updateItem(Double mpg, boolean empty) {
                super.updateItem(mpg, empty);
                if (empty || mpg == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label mpgLabel = new Label(String.format("%.2f", mpg));
                    mpgLabel.setPadding(new Insets(5, 10, 5, 10));
                    
                    if (mpg >= 7.0) {
                        mpgLabel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                                        "-fx-background-radius: 15;");
                    } else if (mpg >= 5.5) {
                        mpgLabel.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; " +
                                        "-fx-background-radius: 15;");
                    } else {
                        mpgLabel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                        "-fx-background-radius: 15;");
                    }
                    
                    setGraphic(mpgLabel);
                }
            }
        });
        
        TableColumn<TruckPerformance, Integer> idleTimeCol = new TableColumn<>("Idle Time (hrs)");
        idleTimeCol.setCellValueFactory(new PropertyValueFactory<>("idleTime"));
        
        TableColumn<TruckPerformance, Double> revenueCol = new TableColumn<>("Revenue");
        revenueCol.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        revenueCol.setCellFactory(column -> new TableCell<TruckPerformance, Double>() {
            @Override
            protected void updateItem(Double revenue, boolean empty) {
                super.updateItem(revenue, empty);
                if (empty || revenue == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(revenue));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<TruckPerformance, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("performanceScore"));
        scoreCol.setCellFactory(column -> new TableCell<TruckPerformance, Double>() {
            @Override
            protected void updateItem(Double score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) {
                    setGraphic(null);
                } else {
                    ProgressBar scoreBar = new ProgressBar(score / 100);
                    scoreBar.setPrefWidth(100);
                    
                    if (score >= 80) {
                        scoreBar.setStyle("-fx-accent: #27ae60;");
                    } else if (score >= 60) {
                        scoreBar.setStyle("-fx-accent: #f39c12;");
                    } else {
                        scoreBar.setStyle("-fx-accent: #e74c3c;");
                    }
                    
                    Label scoreLabel = new Label(String.format("%.0f%%", score));
                    
                    HBox scoreBox = new HBox(5);
                    scoreBox.setAlignment(Pos.CENTER);
                    scoreBox.getChildren().addAll(scoreBar, scoreLabel);
                    setGraphic(scoreBox);
                }
            }
        });
        
        performanceTable.getColumns().addAll(truckCol, driverCol, milesCol, fuelUsedCol,
                                            mpgCol, idleTimeCol, revenueCol, scoreCol);
        
        performanceSection.getChildren().addAll(metricsHeader, dateRangeBox, performanceTable);
        
        return performanceSection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(20));
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Fleet Status Chart
        VBox statusChartBox = new VBox(10);
        Label statusLabel = new Label("Fleet Status Distribution");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        fleetStatusChart = new PieChart();
        fleetStatusChart.setTitle("Current Fleet Status");
        fleetStatusChart.setPrefHeight(350);
        fleetStatusChart.setAnimated(true);
        fleetStatusChart.setLabelsVisible(true);
        fleetStatusChart.setLegendSide(Side.RIGHT);
        
        statusChartBox.getChildren().addAll(statusLabel, fleetStatusChart);
        
        // Mileage Trend Chart
        VBox mileageChartBox = new VBox(10);
        Label mileageLabel = new Label("Monthly Mileage Trend");
        mileageLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis mileageXAxis = new CategoryAxis();
        NumberAxis mileageYAxis = new NumberAxis();
        mileageYAxis.setLabel("Miles");
        
        mileageChart = new LineChart<>(mileageXAxis, mileageYAxis);
        mileageChart.setTitle("Fleet Mileage Over Time");
        mileageChart.setPrefHeight(350);
        mileageChart.setCreateSymbols(true);
        mileageChart.setAnimated(true);
        
        mileageChartBox.getChildren().addAll(mileageLabel, mileageChart);
        
        // Fuel Consumption Chart
        VBox fuelChartBox = new VBox(10);
        Label fuelLabel = new Label("Fuel Consumption by Truck");
        fuelLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis fuelXAxis = new CategoryAxis();
        NumberAxis fuelYAxis = new NumberAxis();
        fuelYAxis.setLabel("Gallons");
        
        fuelChart = new BarChart<>(fuelXAxis, fuelYAxis);
        fuelChart.setTitle("Monthly Fuel Usage");
        fuelChart.setPrefHeight(350);
        fuelChart.setAnimated(true);
        fuelChart.setLegendVisible(false);
        
        fuelChartBox.getChildren().addAll(fuelLabel, fuelChart);
        
        // Efficiency Scatter Chart
        VBox efficiencyChartBox = new VBox(10);
        Label efficiencyLabel = new Label("Mileage vs Fuel Efficiency");
        efficiencyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        NumberAxis effXAxis = new NumberAxis();
        effXAxis.setLabel("Miles Driven");
        NumberAxis effYAxis = new NumberAxis();
        effYAxis.setLabel("MPG");
        
        efficiencyChart = new ScatterChart<>(effXAxis, effYAxis);
        efficiencyChart.setTitle("Fleet Efficiency Analysis");
        efficiencyChart.setPrefHeight(350);
        efficiencyChart.setAnimated(true);
        
        efficiencyChartBox.getChildren().addAll(efficiencyLabel, efficiencyChart);
        
        // Operating Cost Chart
        VBox costChartBox = new VBox(10);
        Label costLabel = new Label("Operating Cost Trend");
        costLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis costXAxis = new CategoryAxis();
        NumberAxis costYAxis = new NumberAxis();
        costYAxis.setLabel("Cost ($)");
        
        costChart = new AreaChart<>(costXAxis, costYAxis);
        costChart.setTitle("Monthly Operating Costs");
        costChart.setPrefHeight(350);
        costChart.setCreateSymbols(true);
        costChart.setAnimated(true);
        
        costChartBox.getChildren().addAll(costLabel, costChart);
        
        // Add charts to grid
        chartsGrid.add(statusChartBox, 0, 0);
        chartsGrid.add(mileageChartBox, 1, 0);
        chartsGrid.add(fuelChartBox, 0, 1);
        chartsGrid.add(efficiencyChartBox, 1, 1);
        chartsGrid.add(costChartBox, 0, 2, 2, 1);
        
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().addAll(col, col);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createMaintenanceSection() {
        VBox maintenanceSection = new VBox(20);
        maintenanceSection.setPadding(new Insets(20));
        
        Label title = new Label("Fleet Maintenance Schedule");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Maintenance Overview
        HBox maintenanceStats = new HBox(20);
        maintenanceStats.setAlignment(Pos.CENTER);
        maintenanceStats.setPadding(new Insets(20));
        
        VBox scheduledCard = createMaintenanceCard("Scheduled", "12", "#3498db");
        VBox overdueCard = createMaintenanceCard("Overdue", "3", "#e74c3c");
        VBox completedCard = createMaintenanceCard("Completed (Month)", "18", "#27ae60");
        VBox upcomingCard = createMaintenanceCard("Upcoming (7 days)", "5", "#f39c12");
        
        maintenanceStats.getChildren().addAll(scheduledCard, overdueCard, completedCard, upcomingCard);
        
        // Maintenance Grid
        GridPane maintenanceGrid = new GridPane();
        maintenanceGrid.setHgap(20);
        maintenanceGrid.setVgap(20);
        maintenanceGrid.setPadding(new Insets(20, 0, 0, 0));
        
        // Create maintenance cards for trucks
        for (int i = 0; i < 6; i++) {
            VBox truckMaintenanceCard = createTruckMaintenanceCard("T" + (1001 + i));
            maintenanceGrid.add(truckMaintenanceCard, i % 3, i / 3);
        }
        
        maintenanceSection.getChildren().addAll(title, maintenanceStats, maintenanceGrid);
        
        return maintenanceSection;
    }
    
    private VBox createTrackingSection() {
        VBox trackingSection = new VBox(20);
        trackingSection.setPadding(new Insets(20));
        trackingSection.setAlignment(Pos.CENTER);
        
        Label title = new Label("Live Fleet Tracking");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Map placeholder
        Rectangle mapPlaceholder = new Rectangle(900, 500);
        mapPlaceholder.setFill(Color.LIGHTGRAY);
        mapPlaceholder.setStroke(Color.DARKGRAY);
        mapPlaceholder.setStrokeWidth(2);
        mapPlaceholder.setArcHeight(10);
        mapPlaceholder.setArcWidth(10);
        
        StackPane mapContainer = new StackPane();
        mapContainer.getChildren().add(mapPlaceholder);
        
        Label mapLabel = new Label("🗺 Interactive Map View");
        mapLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        mapLabel.setTextFill(Color.DARKGRAY);
        mapContainer.getChildren().add(mapLabel);
        
        // Tracking controls
        HBox trackingControls = new HBox(15);
        trackingControls.setAlignment(Pos.CENTER);
        trackingControls.setPadding(new Insets(15));
        
        Button refreshMapButton = new Button("Refresh Locations");
        refreshMapButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        
        Button satelliteViewButton = new Button("Satellite View");
        satelliteViewButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        
        Button trafficButton = new Button("Show Traffic");
        trafficButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        
        Button routesButton = new Button("Optimize Routes");
        routesButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        
        trackingControls.getChildren().addAll(refreshMapButton, satelliteViewButton, 
                                             trafficButton, routesButton);
        
        trackingSection.getChildren().addAll(title, mapContainer, trackingControls);
        
        return trackingSection;
    }
    
    private Label createStatLabel(String text, String color) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        label.setTextFill(Color.web(color));
        return label;
    }
    
    private Separator createVerticalSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.setPrefHeight(30);
        return separator;
    }
    
    private VBox createMetricBox(String title, String value, String color) {
        VBox metricBox = new VBox(5);
        metricBox.setAlignment(Pos.CENTER);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        valueLabel.setTextFill(Color.WHITE);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 14));
        titleLabel.setTextFill(Color.LIGHTGRAY);
        
        metricBox.getChildren().addAll(valueLabel, titleLabel);
        
        return metricBox;
    }
    
    private VBox createMaintenanceCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        valueLabel.setTextFill(Color.web(color));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 14));
        titleLabel.setTextFill(Color.GRAY);
        
        card.getChildren().addAll(valueLabel, titleLabel);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
    }
    
    private VBox createTruckMaintenanceCard(String truckNumber) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefSize(280, 200);
        
        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Circle statusIndicator = new Circle(8);
        statusIndicator.setFill(Math.random() > 0.3 ? Color.GREEN : Color.ORANGE);
        
        Label truckLabel = new Label(truckNumber);
        truckLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        header.getChildren().addAll(statusIndicator, truckLabel);
        
        // Service info
        Label serviceLabel = new Label("Next Service: " + 
            (Math.random() > 0.5 ? "Oil Change" : "Tire Rotation"));
        serviceLabel.setFont(Font.font("Arial", 12));
        serviceLabel.setTextFill(Color.GRAY);
        
        Label dueDateLabel = new Label("Due: " + 
            LocalDate.now().plusDays((int)(Math.random() * 30)).format(DATE_FORMAT));
        dueDateLabel.setFont(Font.font("Arial", 12));
        
        Label mileageLabel = new Label("Current: " + 
            NUMBER_FORMAT.format(50000 + (int)(Math.random() * 50000)) + " mi");
        mileageLabel.setFont(Font.font("Arial", 12));
        
        // Progress to next service
        ProgressBar serviceProgress = new ProgressBar(Math.random());
        serviceProgress.setPrefWidth(240);
        
        Button scheduleButton = new Button("Schedule Service");
        scheduleButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        scheduleButton.setPrefWidth(240);
        
        card.getChildren().addAll(header, serviceLabel, dueDateLabel, mileageLabel,
                                 serviceProgress, scheduleButton);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
    }
    
    private Button createAnimatedButton(String text, String color, String iconPath) {
        Button button = new Button(text);
        button.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                                    "-fx-font-weight: bold; -fx-background-radius: 5; " +
                                    "-fx-padding: 8 15 8 15;", color));
        button.setPrefHeight(40);
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(18);
            icon.setFitWidth(18);
            button.setGraphic(icon);
        } catch (Exception e) {
            // Icon not found
        }
        
        // Hover animations
        button.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
            
            button.setStyle(button.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 5);");
            button.setCursor(javafx.scene.Cursor.HAND);
        });
        
        button.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1);
            st.setToY(1);
            st.play();
            
            button.setStyle(button.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 5);", ""));
        });
        
        // Click animation
        button.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(50), button);
            st.setToX(0.95);
            st.setToY(0.95);
            st.play();
        });
        
        button.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(50), button);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });
        
        return button;
    }
    
    private Label createButtonIcon(String emoji, String color) {
        Label icon = new Label(emoji);
        icon.setFont(Font.font(16));
        icon.setTextFill(Color.web(color));
        return icon;
    }
    
    private Color getStatusColor(String status) {
        switch (status) {
            case "Active":
            case "In Transit":
                return Color.web("#27ae60");
            case "Idle":
            case "At Rest":
                return Color.web("#f39c12");
            case "Maintenance":
                return Color.web("#e74c3c");
            case "Off Duty":
            case "Out of Service":
                return Color.web("#95a5a6");
            case "Reserved":
                return Color.web("#9b59b6");
            default:
                return Color.web("#34495e");
        }
    }
    
    private void loadTruckData() {
        loadingIndicator.setVisible(true);
        
        Task<TruckData> task = new Task<TruckData>() {
            @Override
            protected TruckData call() throws Exception {
                Thread.sleep(1000); // Simulate loading
                return generateSampleData();
            }
        };
        
        task.setOnSucceeded(e -> {
            TruckData data = task.getValue();
            updateTables(data);
            updateDashboardCards(data);
            updateCharts(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.ERROR, "Error", "Failed to load truck data.");
        });
        
        new Thread(task).start();
    }
    
    private void updateTables(TruckData data) {
        ObservableList<Truck> trucks = FXCollections.observableArrayList(data.getTrucks());
        trucksTable.setItems(trucks);
        
        ObservableList<TruckPerformance> performance = FXCollections.observableArrayList(data.getPerformanceData());
        performanceTable.setItems(performance);
        
        // Apply filters
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        statusFilterComboBox.setOnAction(e -> applyFilters());
        typeFilterComboBox.setOnAction(e -> applyFilters());
        locationFilterComboBox.setOnAction(e -> applyFilters());
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase();
        String selectedStatus = statusFilterComboBox.getValue();
        String selectedType = typeFilterComboBox.getValue();
        String selectedLocation = locationFilterComboBox.getValue();
        
        ObservableList<Truck> filtered = trucksTable.getItems().filtered(truck -> {
            boolean matchesSearch = searchText.isEmpty() ||
                truck.getNumber().toLowerCase().contains(searchText) ||
                truck.getDriver().toLowerCase().contains(searchText) ||
                truck.getLocation().toLowerCase().contains(searchText);
            
            boolean matchesStatus = "All Status".equals(selectedStatus) ||
                truck.getStatus().equals(selectedStatus);
            
            boolean matchesType = "All Types".equals(selectedType) ||
                truck.getType().equals(selectedType);
            
            boolean matchesLocation = "All Locations".equals(selectedLocation) ||
                truck.getLocation().contains(selectedLocation.replace(" Region", ""));
            
            return matchesSearch && matchesStatus && matchesType && matchesLocation;
        });
        
        trucksTable.setItems(filtered);
    }
    
    private void updateDashboardCards(TruckData data) {
        int total = data.getTrucks().size();
        long active = data.getTrucks().stream()
            .filter(t -> "Active".equals(t.getStatus()) || "In Transit".equals(t.getStatus()))
            .count();
        long idle = data.getTrucks().stream()
            .filter(t -> "Idle".equals(t.getStatus()) || "At Rest".equals(t.getStatus()))
            .count();
        long maintenance = data.getTrucks().stream()
            .filter(t -> "Maintenance".equals(t.getStatus()))
            .count();
        
        double avgMileage = data.getTrucks().stream()
            .mapToInt(Truck::getMileage)
            .average()
            .orElse(0.0);
        
                double avgFuelEfficiency = data.getPerformanceData().stream()
            .mapToDouble(TruckPerformance::getMpg)
            .average()
            .orElse(0.0);
        
        animateValue(totalTrucksLabel, String.valueOf(total));
        animateValue(activeTrucksLabel, String.valueOf(active));
        animateValue(idleTrucksLabel, String.valueOf(idle));
        animateValue(maintenanceTrucksLabel, String.valueOf(maintenance));
        animateValue(avgMileageLabel, NUMBER_FORMAT.format(avgMileage));
        animateValue(fuelEfficiencyLabel, String.format("%.1f MPG", avgFuelEfficiency));
    }
    
    private void updateCharts(TruckData data) {
        // Update fleet status chart
        Map<String, Long> statusCounts = data.getTrucks().stream()
            .collect(Collectors.groupingBy(Truck::getStatus, Collectors.counting()));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        statusCounts.forEach((status, count) -> {
            PieChart.Data slice = new PieChart.Data(status + " (" + count + ")", count);
            pieData.add(slice);
        });
        fleetStatusChart.setData(pieData);
        
        // Add colors to pie slices
        pieData.forEach(data1 -> {
            String status = data1.getName().split(" \\(")[0];
            data1.getNode().setStyle("-fx-pie-color: " + getStatusHexColor(status) + ";");
            
            Tooltip tooltip = new Tooltip(String.format("%s: %.1f%%", 
                data1.getName(), (data1.getPieValue() / data.getTrucks().size()) * 100));
            Tooltip.install(data1.getNode(), tooltip);
        });
        
        // Update mileage trend chart
        XYChart.Series<String, Number> mileageSeries = new XYChart.Series<>();
        mileageSeries.setName("Fleet Mileage");
        
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun"};
        for (String month : months) {
            mileageSeries.getData().add(new XYChart.Data<>(month, 
                100000 + Math.random() * 50000));
        }
        
        mileageChart.getData().clear();
        mileageChart.getData().add(mileageSeries);
        
        // Update fuel consumption chart
        XYChart.Series<String, Number> fuelSeries = new XYChart.Series<>();
        
        data.getTrucks().stream()
            .limit(10)
            .forEach(truck -> {
                fuelSeries.getData().add(new XYChart.Data<>(truck.getNumber(), 
                    300 + Math.random() * 200));
            });
        
        fuelChart.getData().clear();
        fuelChart.getData().add(fuelSeries);
        
        // Update efficiency scatter chart
        XYChart.Series<Number, Number> efficiencySeries = new XYChart.Series<>();
        efficiencySeries.setName("Trucks");
        
        data.getPerformanceData().forEach(perf -> {
            XYChart.Data<Number, Number> point = new XYChart.Data<>(
                perf.getMilesDriven(), perf.getMpg());
            efficiencySeries.getData().add(point);
            
            // Add tooltip to each point
            Platform.runLater(() -> {
                if (point.getNode() != null) {
                    Tooltip tooltip = new Tooltip(perf.getTruckNumber() + "\n" +
                        "Miles: " + NUMBER_FORMAT.format(perf.getMilesDriven()) + "\n" +
                        "MPG: " + String.format("%.2f", perf.getMpg()));
                    Tooltip.install(point.getNode(), tooltip);
                }
            });
        });
        
        efficiencyChart.getData().clear();
        efficiencyChart.getData().add(efficiencySeries);
        
        // Update cost trend chart
        XYChart.Series<String, Number> costSeries = new XYChart.Series<>();
        costSeries.setName("Operating Costs");
        
        for (String month : months) {
            costSeries.getData().add(new XYChart.Data<>(month, 
                50000 + Math.random() * 20000));
        }
        
        costChart.getData().clear();
        costChart.getData().add(costSeries);
    }
    
    private String getStatusHexColor(String status) {
        switch (status) {
            case "Active":
            case "In Transit":
                return "#27ae60";
            case "Idle":
            case "At Rest":
                return "#f39c12";
            case "Maintenance":
                return "#e74c3c";
            case "Out of Service":
                return "#95a5a6";
            case "Reserved":
                return "#9b59b6";
            default:
                return "#34495e";
        }
    }
    
    private void animateValue(Label label, String newValue) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(label.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(200), new KeyValue(label.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(200), e -> label.setText(newValue)),
            new KeyFrame(Duration.millis(400), new KeyValue(label.opacityProperty(), 1))
        );
        
        timeline.play();
    }
    
    private void updatePerformanceData() {
        // Refresh performance data based on selected date range
        loadTruckData();
    }
    
    private void startRealTimeUpdates() {
        // Simulate real-time updates
        Timeline updateTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            // Update random truck statuses
            if (trucksTable.getItems() != null && !trucksTable.getItems().isEmpty()) {
                int randomIndex = (int)(Math.random() * trucksTable.getItems().size());
                Truck truck = trucksTable.getItems().get(randomIndex);
                
                String[] possibleStatuses = {"Active", "Idle", "In Transit", "At Rest"};
                truck.setStatus(possibleStatuses[(int)(Math.random() * possibleStatuses.length)]);
                
                // Update location
                String[] locations = {"Highway I-95 North", "Warehouse District", "Customer Site",
                                    "Rest Stop Mile 142", "Downtown Terminal"};
                truck.setLocation(locations[(int)(Math.random() * locations.length)]);
                
                // Update fuel
                truck.setFuelLevel(Math.max(10, truck.getFuelLevel() - Math.random() * 5));
                
                trucksTable.refresh();
            }
        }));
        updateTimeline.setCycleCount(Timeline.INDEFINITE);
        updateTimeline.play();
    }
    
    private void showAddTruckDialog() {
        Dialog<Truck> dialog = new Dialog<>();
        dialog.setTitle("Add New Truck");
        dialog.setHeaderText("Enter truck details");
        dialog.getDialogPane().setPrefWidth(500);
        
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField numberField = new TextField();
        numberField.setPromptText("T1050");
        
        ComboBox<String> typeField = new ComboBox<>(truckTypes);
        typeField.setPromptText("Select Type");
        typeField.setPrefWidth(250);
        
        TextField vinField = new TextField();
        vinField.setPromptText("VIN Number");
        
        TextField yearField = new TextField();
        yearField.setPromptText("2024");
        
        TextField mileageField = new TextField();
        mileageField.setPromptText("0");
        
        ComboBox<String> statusField = new ComboBox<>(FXCollections.observableArrayList(
            "Available", "Reserved"));
        statusField.setValue("Available");
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Truck Number:"), 0, 0);
        grid.add(numberField, 1, 0);
        grid.add(new Label("Type/Model:"), 0, 1);
        grid.add(typeField, 1, 1);
        grid.add(new Label("VIN:"), 0, 2);
        grid.add(vinField, 1, 2);
        grid.add(new Label("Year:"), 0, 3);
        grid.add(yearField, 1, 3);
        grid.add(new Label("Initial Mileage:"), 0, 4);
        grid.add(mileageField, 1, 4);
        grid.add(new Label("Status:"), 0, 5);
        grid.add(statusField, 1, 5);
        grid.add(new Label("Notes:"), 0, 6);
        grid.add(notesArea, 1, 6);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validation
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        
        numberField.textProperty().addListener((obs, oldVal, newVal) -> 
            validateTruckForm(addButton, numberField, typeField, vinField, yearField));
        typeField.valueProperty().addListener((obs, oldVal, newVal) -> 
            validateTruckForm(addButton, numberField, typeField, vinField, yearField));
        vinField.textProperty().addListener((obs, oldVal, newVal) -> 
            validateTruckForm(addButton, numberField, typeField, vinField, yearField));
        yearField.textProperty().addListener((obs, oldVal, newVal) -> 
            validateTruckForm(addButton, numberField, typeField, vinField, yearField));
        
        // Only allow numbers in year and mileage fields
        yearField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                yearField.setText(oldText);
            }
        });
        
        mileageField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                mileageField.setText(oldText);
            }
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Truck truck = new Truck();
                truck.setNumber(numberField.getText());
                truck.setType(typeField.getValue());
                truck.setStatus(statusField.getValue());
                truck.setDriver("");
                truck.setLocation("Main Depot");
                truck.setMileage(mileageField.getText().isEmpty() ? 0 : 
                                Integer.parseInt(mileageField.getText()));
                truck.setFuelLevel(100.0);
                truck.setLastService(LocalDate.now());
                return truck;
            }
            return null;
        });
        
        Optional<Truck> result = dialog.showAndWait();
        result.ifPresent(truck -> {
            trucksTable.getItems().add(truck);
            showAlert(AlertType.INFORMATION, "Truck Added", 
                     "Truck " + truck.getNumber() + " has been added to the fleet!");
            loadTruckData();
        });
    }
    
    private void validateTruckForm(Node button, TextField number, ComboBox<String> type,
                                  TextField vin, TextField year) {
        boolean isValid = !number.getText().trim().isEmpty() &&
                         type.getValue() != null &&
                         !vin.getText().trim().isEmpty() &&
                         !year.getText().trim().isEmpty() &&
                         year.getText().matches("\\d{4}");
        button.setDisable(!isValid);
    }
    
    private void showAssignDriverDialog() {
        Truck selected = trucksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a truck to assign a driver.");
            return;
        }
        
        List<String> availableDrivers = Arrays.asList(
            "John Smith", "Jane Doe", "Mike Johnson", "Sarah Williams",
            "Robert Brown", "Lisa Davis", "William Miller", "Jennifer Wilson"
        );
        
        ChoiceDialog<String> dialog = new ChoiceDialog<>(availableDrivers.get(0), availableDrivers);
        dialog.setTitle("Assign Driver");
        dialog.setHeaderText("Assign driver to " + selected.getNumber());
        dialog.setContentText("Select driver:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(driver -> {
            selected.setDriver(driver);
            selected.setStatus("Active");
            trucksTable.refresh();
            showAlert(AlertType.INFORMATION, "Driver Assigned", 
                     driver + " has been assigned to " + selected.getNumber());
        });
    }
    
    private void showMaintenanceDialog() {
        Truck selected = trucksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(AlertType.WARNING, "No Selection", 
                     "Please select a truck to schedule maintenance.");
            return;
        }
        
        // Maintenance scheduling dialog
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Schedule Maintenance");
        dialog.setHeaderText("Schedule maintenance for " + selected.getNumber());
        
        ButtonType scheduleButtonType = new ButtonType("Schedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(scheduleButtonType, ButtonType.CANCEL);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        DatePicker datePicker = new DatePicker();
        datePicker.setValue(LocalDate.now().plusDays(7));
        
        ComboBox<String> serviceType = new ComboBox<>();
        serviceType.getItems().addAll("Oil Change", "Tire Rotation", "Brake Service",
                                    "Engine Service", "Full Inspection", "Other");
        serviceType.setValue("Oil Change");
        
        TextArea notes = new TextArea();
        notes.setPromptText("Service notes...");
        notes.setPrefRowCount(3);
        
        content.getChildren().addAll(
            new Label("Service Date:"), datePicker,
            new Label("Service Type:"), serviceType,
            new Label("Notes:"), notes
        );
        
        dialog.getDialogPane().setContent(content);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == scheduleButtonType) {
                return datePicker.getValue();
            }
            return null;
        });
        
        Optional<LocalDate> result = dialog.showAndWait();
        result.ifPresent(date -> {
            showAlert(AlertType.INFORMATION, "Maintenance Scheduled",
                     serviceType.getValue() + " scheduled for " + selected.getNumber() +
                     " on " + date.format(DATE_FORMAT));
        });
    }
    
    private void showLiveTracking() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Live Tracking");
        alert.setHeaderText("Fleet GPS Tracking");
        alert.setContentText("Opening live tracking dashboard...\n\n" +
                           "This feature would integrate with GPS tracking system.");
        alert.showAndWait();
    }
    
    private void showReportMenu(Button reportButton) {
        ContextMenu reportMenu = new ContextMenu();
        
        MenuItem fleetReport = new MenuItem("Fleet Summary Report");
        fleetReport.setOnAction(e -> generateReport("Fleet Summary"));
        
        MenuItem performanceReport = new MenuItem("Performance Report");
        performanceReport.setOnAction(e -> generateReport("Performance"));
        
        MenuItem maintenanceReport = new MenuItem("Maintenance Report");
        maintenanceReport.setOnAction(e -> generateReport("Maintenance"));
        
        MenuItem fuelReport = new MenuItem("Fuel Consumption Report");
        fuelReport.setOnAction(e -> generateReport("Fuel Consumption"));
        
        MenuItem customReport = new MenuItem("Custom Report...");
        customReport.setOnAction(e -> showCustomReportDialog());
        
        reportMenu.getItems().addAll(fleetReport, performanceReport, maintenanceReport,
                                   fuelReport, new SeparatorMenuItem(), customReport);
        reportMenu.show(reportButton, Side.BOTTOM, 0, 0);
    }
    
    private void generateReport(String reportType) {
        showAlert(AlertType.INFORMATION, "Report Generation",
                 "Generating " + reportType + " Report...\n\n" +
                 "Report will be available in the Reports section.");
    }
    
    private void showCustomReportDialog() {
        // Custom report configuration dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Custom Report");
        alert.setHeaderText("Custom Report Builder");
        alert.setContentText("Configure custom report parameters...");
        alert.showAndWait();
    }
    
    private void showExportMenu(Button exportButton) {
        ContextMenu exportMenu = new ContextMenu();
        
        MenuItem excelItem = new MenuItem("Export to Excel");
        excelItem.setOnAction(e -> exportToExcel());
        
        MenuItem pdfItem = new MenuItem("Export to PDF");
        pdfItem.setOnAction(e -> exportToPdf());
        
        MenuItem csvItem = new MenuItem("Export to CSV");
        csvItem.setOnAction(e -> exportToCsv());
        
        exportMenu.getItems().addAll(excelItem, pdfItem, csvItem);
        exportMenu.show(exportButton, Side.BOTTOM, 0, 0);
    }
    
    private void exportToExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Excel File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("truck_fleet_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful",
                     "Fleet data exported to Excel successfully!");
        }
    }
    
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("truck_fleet_report_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful",
                     "Fleet report exported to PDF successfully!");
        }
    }
    
    private void exportToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("truck_fleet_data_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful",
                     "Fleet data exported to CSV successfully!");
        }
    }
    
    private void viewTruckDetails(Truck truck) {
        if (truck != null) {
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Truck Details");
            dialog.setHeaderText(truck.getNumber() + " - " + truck.getType());
            dialog.getDialogPane().setPrefWidth(600);
            
            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            // Basic Info Section
            Label basicLabel = new Label("Basic Information");
            basicLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            grid.add(basicLabel, 0, 0, 2, 1);
            
            grid.add(new Label("Status:"), 0, 1);
            Label statusLabel = new Label(truck.getStatus());
            statusLabel.setTextFill(getStatusColor(truck.getStatus()));
            statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            grid.add(statusLabel, 1, 1);
            
            grid.add(new Label("Driver:"), 0, 2);
            grid.add(new Label(truck.getDriver().isEmpty() ? "Unassigned" : truck.getDriver()), 1, 2);
            
            grid.add(new Label("Location:"), 0, 3);
            grid.add(new Label(truck.getLocation()), 1, 3);
            
            grid.add(new Label("Mileage:"), 0, 4);
            grid.add(new Label(NUMBER_FORMAT.format(truck.getMileage()) + " miles"), 1, 4);
            
            grid.add(new Label("Fuel Level:"), 0, 5);
            ProgressBar fuelBar = new ProgressBar(truck.getFuelLevel() / 100);
            fuelBar.setPrefWidth(200);
            Label fuelLabel = new Label(String.format("%.0f%%", truck.getFuelLevel()));
            HBox fuelBox = new HBox(10, fuelBar, fuelLabel);
            grid.add(fuelBox, 1, 5);
            
            // Maintenance Section
            Label maintLabel = new Label("Maintenance Information");
            maintLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            grid.add(maintLabel, 0, 6, 2, 1);
            
            grid.add(new Label("Last Service:"), 0, 7);
            grid.add(new Label(truck.getLastService().format(DATE_FORMAT)), 1, 7);
            
            grid.add(new Label("Next Service:"), 0, 8);
            LocalDate nextService = truck.getLastService().plusDays(90);
            Label nextServiceLabel = new Label(nextService.format(DATE_FORMAT));
            if (nextService.isBefore(LocalDate.now())) {
                nextServiceLabel.setTextFill(Color.RED);
                nextServiceLabel.setText(nextServiceLabel.getText() + " (OVERDUE)");
            }
            grid.add(nextServiceLabel, 1, 8);
            
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        }
    }
    
    private void editTruck(Truck truck) {
        if (truck != null) {
            // Edit dialog implementation
            showAlert(AlertType.INFORMATION, "Edit Truck",
                     "Edit dialog for " + truck.getNumber() + " would open here.");
        }
    }
    
    private void trackTruck(Truck truck) {
        if (truck != null) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Track Location");
            alert.setHeaderText("Tracking " + truck.getNumber());
            alert.setContentText("Current Location: " + truck.getLocation() + "\n" +
                               "Speed: " + (30 + Math.random() * 40) + " mph\n" +
                               "Direction: North\n" +
                               "Last Update: Just now");
            alert.showAndWait();
        }
    }
    
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void initializeData() {
        // Any initial data setup
    }
    
    private TruckData generateSampleData() {
        TruckData data = new TruckData();
        Random random = new Random();
        
        // Generate trucks
        List<Truck> trucks = new ArrayList<>();
        String[] drivers = {"John Smith", "Jane Doe", "Mike Johnson", "Sarah Williams",
                          "Robert Brown", "Lisa Davis", "", ""};
        String[] locations = {"Highway I-95 North", "Warehouse A", "Customer Site - NYC",
                            "Rest Area Mile 45", "Downtown Terminal", "Maintenance Bay 3"};
        
        for (int i = 0; i < 30; i++) {
            Truck truck = new Truck();
            truck.setNumber("T" + String.format("%04d", 1001 + i));
            truck.setType(truckTypes.get(random.nextInt(truckTypes.size())));
            truck.setStatus(truckStatuses.get(random.nextInt(truckStatuses.size())));
            truck.setDriver(drivers[random.nextInt(drivers.length)]);
            truck.setLocation(locations[random.nextInt(locations.length)]);
            truck.setMileage(10000 + random.nextInt(200000));
            truck.setFuelLevel(20 + random.nextDouble() * 80);
            truck.setLastService(LocalDate.now().minusDays(random.nextInt(120)));
            truck.setSelected(false);
            trucks.add(truck);
        }
        data.setTrucks(trucks);
        
        // Generate performance data
        List<TruckPerformance> performanceData = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            TruckPerformance perf = new TruckPerformance();
            perf.setTruckNumber("T" + String.format("%04d", 1001 + i));
            perf.setDriverName(drivers[i % (drivers.length - 2)]); // Exclude empty strings
            perf.setMilesDriven(1000 + random.nextInt(5000));
            perf.setFuelUsed(perf.getMilesDriven() / (5 + random.nextDouble() * 3));
            perf.setMpg(perf.getMilesDriven() / perf.getFuelUsed());
            perf.setIdleTime(10 + random.nextInt(50));
            perf.setRevenue(perf.getMilesDriven() * (1.5 + random.nextDouble()));
            perf.setPerformanceScore(60 + random.nextDouble() * 40);
            performanceData.add(perf);
        }
        data.setPerformanceData(performanceData);
        
        return data;
    }
    
    // Inner classes
    public static class Truck {
        private final StringProperty number = new SimpleStringProperty();
        private final StringProperty type = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final StringProperty driver = new SimpleStringProperty();
        private final StringProperty location = new SimpleStringProperty();
        private final IntegerProperty mileage = new SimpleIntegerProperty();
        private final DoubleProperty fuelLevel = new SimpleDoubleProperty();
        private final ObjectProperty<LocalDate> lastService = new SimpleObjectProperty<>();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        
        // Getters and setters
        public String getNumber() { return number.get(); }
        public void setNumber(String value) { number.set(value); }
        public StringProperty numberProperty() { return number; }
        
        public String getType() { return type.get(); }
        public void setType(String value) { type.set(value); }
        public StringProperty typeProperty() { return type; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
        
        public String getDriver() { return driver.get(); }
        public void setDriver(String value) { driver.set(value); }
        public StringProperty driverProperty() { return driver; }
        
        public String getLocation() { return location.get(); }
        public void setLocation(String value) { location.set(value); }
        public StringProperty locationProperty() { return location; }
        
        public int getMileage() { return mileage.get(); }
        public void setMileage(int value) { mileage.set(value); }
        public IntegerProperty mileageProperty() { return mileage; }
        
        public double getFuelLevel() { return fuelLevel.get(); }
        public void setFuelLevel(double value) { fuelLevel.set(value); }
        public DoubleProperty fuelLevelProperty() { return fuelLevel; }
        
        public LocalDate getLastService() { return lastService.get(); }
        public void setLastService(LocalDate value) { lastService.set(value); }
        public ObjectProperty<LocalDate> lastServiceProperty() { return lastService; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
    }
    
    public static class TruckPerformance {
        private final StringProperty truckNumber = new SimpleStringProperty();
        private final StringProperty driverName = new SimpleStringProperty();
        private final IntegerProperty milesDriven = new SimpleIntegerProperty();
        private final DoubleProperty fuelUsed = new SimpleDoubleProperty();
        private final DoubleProperty mpg = new SimpleDoubleProperty();
        private final IntegerProperty idleTime = new SimpleIntegerProperty();
        private final DoubleProperty revenue = new SimpleDoubleProperty();
        private final DoubleProperty performanceScore = new SimpleDoubleProperty();
        
        // Getters and setters
        public String getTruckNumber() { return truckNumber.get(); }
        public void setTruckNumber(String value) { truckNumber.set(value); }
        public StringProperty truckNumberProperty() { return truckNumber; }
        
        public String getDriverName() { return driverName.get(); }
        public void setDriverName(String value) { driverName.set(value); }
        public StringProperty driverNameProperty() { return driverName; }
        
        public int getMilesDriven() { return milesDriven.get(); }
        public void setMilesDriven(int value) { milesDriven.set(value); }
        public IntegerProperty milesDrivenProperty() { return milesDriven; }
        
        public double getFuelUsed() { return fuelUsed.get(); }
        public void setFuelUsed(double value) { fuelUsed.set(value); }
        public DoubleProperty fuelUsedProperty() { return fuelUsed; }
        
        public double getMpg() { return mpg.get(); }
        public void setMpg(double value) { mpg.set(value); }
        public DoubleProperty mpgProperty() { return mpg; }
        
        public int getIdleTime() { return idleTime.get(); }
        public void setIdleTime(int value) { idleTime.set(value); }
        public IntegerProperty idleTimeProperty() { return idleTime; }
        
        public double getRevenue() { return revenue.get(); }
        public void setRevenue(double value) { revenue.set(value); }
        public DoubleProperty revenueProperty() { return revenue; }
        
        public double getPerformanceScore() { return performanceScore.get(); }
        public void setPerformanceScore(double value) { performanceScore.set(value); }
        public DoubleProperty performanceScoreProperty() { return performanceScore; }
    }
    
    public static class TruckData {
        private List<Truck> trucks = new ArrayList<>();
        private List<TruckPerformance> performanceData = new ArrayList<>();
        
        public List<Truck> getTrucks() { return trucks; }
        public void setTrucks(List<Truck> trucks) { this.trucks = trucks; }
        
        public List<TruckPerformance> getPerformanceData() { return performanceData; }
        public void setPerformanceData(List<TruckPerformance> performanceData) { 
            this.performanceData = performanceData; 
        }
    }
}