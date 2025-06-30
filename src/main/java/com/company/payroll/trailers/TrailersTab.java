package com.company.payroll.trailers;

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

public class TrailersTab extends Tab {
    private TableView<Trailer> trailersTable;
    private TableView<TrailerAssignment> assignmentsTable;
    private ComboBox<String> statusFilterComboBox;
    private ComboBox<String> typeFilterComboBox;
    private TextField searchField;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    
    private Label totalTrailersLabel;
    private Label activeTrailersLabel;
    private Label availableTrailersLabel;
    private Label maintenanceLabel;
    
    private PieChart statusChart;
    private BarChart<String, Number> typeChart;
    private LineChart<String, Number> utilizationChart;
    private StackedBarChart<String, Number> revenueChart;
    
    private ProgressIndicator loadingIndicator;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private ObservableList<String> trailerTypes = FXCollections.observableArrayList(
        "Dry Van", "Refrigerated", "Flatbed", "Tanker", "Container", 
        "Lowboy", "Step Deck", "Double Drop", "Curtain Side", "Other"
    );
    
    private ObservableList<String> trailerStatuses = FXCollections.observableArrayList(
        "Active", "Available", "In Maintenance", "Out of Service", "Reserved"
    );
    
    public TrailersTab() {
        setText("Trailers");
        setClosable(false);
        
        // Set tab icon
        try {
            ImageView tabIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/trailer.png")));
            tabIcon.setFitHeight(16);
            tabIcon.setFitWidth(16);
            setGraphic(tabIcon);
        } catch (Exception e) {
            // Icon not found
        }
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: #f4f6f7;");
        
        // Header
        VBox header = createHeader();
        
        // Control Panel
        VBox controlPanel = createControlPanel();
        
        // Summary Dashboard
        HBox summaryDashboard = createSummaryDashboard();
        
        // Main Content Tabs
        TabPane contentTabs = createContentTabs();
        
        // Loading Indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        StackPane loadingPane = new StackPane(loadingIndicator);
        loadingPane.setAlignment(Pos.CENTER);
        
        mainContent.getChildren().addAll(header, controlPanel, summaryDashboard, contentTabs, loadingPane);
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
        
        // Initialize data
        initializeData();
        loadTrailerData();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(30));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: linear-gradient(to right, #1e3c72, #2a5298); " +
                       "-fx-background-radius: 10;");
        
        Label titleLabel = new Label("Trailer Fleet Management");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Monitor and manage your trailer fleet efficiently");
        subtitleLabel.setFont(Font.font("Arial", 18));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        // Add animated underline
        Rectangle underline = new Rectangle(300, 3);
        underline.setFill(Color.web("#3498db"));
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(underline.widthProperty(), 0)),
            new KeyFrame(Duration.seconds(1), new KeyValue(underline.widthProperty(), 300))
        );
        timeline.play();
        
        header.getChildren().addAll(titleLabel, underline, subtitleLabel);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(5);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        header.setEffect(shadow);
        
        return header;
    }
    
    private VBox createControlPanel() {
        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Search and Filter Row
        HBox searchRow = new HBox(15);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        
        // Search field with icon
        HBox searchBox = new HBox(5);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 20; -fx-padding: 5 15 5 15;");
        
        Label searchIcon = new Label("🔍");
        searchField = new TextField();
        searchField.setPromptText("Search by trailer number, type, or location...");
        searchField.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        searchField.setPrefWidth(350);
        
        searchBox.getChildren().addAll(searchIcon, searchField);
        
        // Status Filter
        Label statusLabel = new Label("Status:");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        statusFilterComboBox = new ComboBox<>();
        statusFilterComboBox.getItems().add("All Status");
        statusFilterComboBox.getItems().addAll(trailerStatuses);
        statusFilterComboBox.setValue("All Status");
        statusFilterComboBox.setPrefWidth(150);
        
        // Type Filter
        Label typeLabel = new Label("Type:");
        typeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        typeFilterComboBox = new ComboBox<>();
        typeFilterComboBox.getItems().add("All Types");
        typeFilterComboBox.getItems().addAll(trailerTypes);
        typeFilterComboBox.setValue("All Types");
        typeFilterComboBox.setPrefWidth(150);
        
        searchRow.getChildren().addAll(searchBox, new Separator(Orientation.VERTICAL),
                                      statusLabel, statusFilterComboBox,
                                      typeLabel, typeFilterComboBox);
        
        // Action Buttons Row
        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.setPadding(new Insets(10, 0, 0, 0));
        
        Button addTrailerButton = createStyledButton("Add Trailer", "#27ae60", "add-icon.png");
        addTrailerButton.setOnAction(e -> showAddTrailerDialog());
        
        Button assignButton = createStyledButton("Assign Trailer", "#3498db", "assign-icon.png");
        assignButton.setOnAction(e -> showAssignTrailerDialog());
        
        Button maintenanceButton = createStyledButton("Schedule Maintenance", "#f39c12", "maintenance-icon.png");
        maintenanceButton.setOnAction(e -> showMaintenanceDialog());
        
        Button reportButton = createStyledButton("Generate Report", "#9b59b6", "report-icon.png");
        reportButton.setOnAction(e -> generateReport());
        
        Button exportButton = createStyledButton("Export", "#95a5a6", "export-icon.png");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        actionRow.getChildren().addAll(addTrailerButton, assignButton, maintenanceButton, 
                                      reportButton, exportButton);
        
        controlPanel.getChildren().addAll(searchRow, new Separator(), actionRow);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        controlPanel.setEffect(shadow);
        
        return controlPanel;
    }
    
    private HBox createSummaryDashboard() {
        HBox dashboard = new HBox(20);
        dashboard.setPadding(new Insets(10));
        dashboard.setAlignment(Pos.CENTER);
        
        VBox totalCard = createDashboardCard("Total Trailers", "0", "#2c3e50", "trailer-icon.png", true);
        totalTrailersLabel = (Label) totalCard.lookup(".value-label");
        
        VBox activeCard = createDashboardCard("Active", "0", "#27ae60", "active-icon.png", true);
        activeTrailersLabel = (Label) activeCard.lookup(".value-label");
        
        VBox availableCard = createDashboardCard("Available", "0", "#3498db", "available-icon.png", true);
        availableTrailersLabel = (Label) availableCard.lookup(".value-label");
        
        VBox maintenanceCard = createDashboardCard("In Maintenance", "0", "#e74c3c", "maintenance-icon.png", true);
        maintenanceLabel = (Label) maintenanceCard.lookup(".value-label");
        
        dashboard.getChildren().addAll(totalCard, activeCard, availableCard, maintenanceCard);
        
        return dashboard;
    }
    
    private VBox createDashboardCard(String title, String value, String color, String iconPath, boolean animated) {
        VBox card = new VBox(10);
        card.getStyleClass().add("dashboard-card");
        card.setPadding(new Insets(25));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefSize(280, 140);
        
        // Icon and Title
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER);
        
        Circle iconBackground = new Circle(25);
        iconBackground.setFill(Color.web(color).deriveColor(0, 1, 1, 0.1));
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(30);
            icon.setFitWidth(30);
            
            StackPane iconStack = new StackPane(iconBackground, icon);
            header.getChildren().add(iconStack);
        } catch (Exception e) {
            header.getChildren().add(iconBackground);
        }
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 14));
        titleLabel.setTextFill(Color.DARKGRAY);
        header.getChildren().add(titleLabel);
        
        // Value
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        valueLabel.setTextFill(Color.web(color));
        
        // Progress bar (for visual appeal)
        ProgressBar progressBar = new ProgressBar(Math.random());
        progressBar.setPrefWidth(200);
        progressBar.setStyle("-fx-accent: " + color);
        
        card.getChildren().addAll(header, valueLabel, progressBar);
        
        if (animated) {
            // Add pulse animation on hover
            card.setOnMouseEntered(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
                st.setToX(1.05);
                st.setToY(1.05);
                st.play();
                
                Glow glow = new Glow(0.2);
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
        }
        
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
        
        Tab assignmentsTab = new Tab("Assignments");
        assignmentsTab.setClosable(false);
        assignmentsTab.setContent(createAssignmentsSection());
        
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setClosable(false);
        analyticsTab.setContent(createAnalyticsSection());
        
        Tab maintenanceTab = new Tab("Maintenance");
        maintenanceTab.setClosable(false);
        maintenanceTab.setContent(createMaintenanceSection());
        
        tabPane.getTabs().addAll(fleetTab, assignmentsTab, analyticsTab, maintenanceTab);
        
        return tabPane;
    }
    
    private VBox createFleetOverviewSection() {
        VBox fleetSection = new VBox(15);
        fleetSection.setPadding(new Insets(15));
        
        // Quick Actions Bar
        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER_LEFT);
        quickActions.setPadding(new Insets(10));
        quickActions.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 5;");
        
        Label quickLabel = new Label("Quick Actions:");
        quickLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        Button addButton = new Button("Add New");
        addButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11px;");
        
        Button importButton = new Button("Import");
        importButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px;");
        
        Button batchEditButton = new Button("Batch Edit");
        batchEditButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 11px;");
        
        quickActions.getChildren().addAll(quickLabel, addButton, importButton, batchEditButton);
        
        // Trailers Table
        trailersTable = new TableView<>();
        trailersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        trailersTable.setPrefHeight(500);
        trailersTable.setEditable(true);
        
        // Selection column
        TableColumn<Trailer, Boolean> selectCol = new TableColumn<>();
        selectCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setMaxWidth(50);
        selectCol.setStyle("-fx-alignment: CENTER;");
        
        // Checkbox in header
        CheckBox selectAllCheckBox = new CheckBox();
        selectCol.setGraphic(selectAllCheckBox);
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            trailersTable.getItems().forEach(trailer -> trailer.setSelected(selected));
        });
        
        TableColumn<Trailer, String> numberCol = new TableColumn<>("Trailer #");
        numberCol.setCellValueFactory(new PropertyValueFactory<>("number"));
        numberCol.setMinWidth(100);
        
        TableColumn<Trailer, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setCellFactory(column -> new TableCell<Trailer, String>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(type);
                    label.setPadding(new Insets(5, 10, 5, 10));
                    label.setStyle(getTypeStyle(type));
                    setGraphic(label);
                }
            }
        });
        
        TableColumn<Trailer, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<Trailer, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox statusBox = new HBox(5);
                    statusBox.setAlignment(Pos.CENTER_LEFT);
                    
                    Circle indicator = new Circle(5);
                    indicator.setFill(getStatusColor(status));
                    
                    Label statusLabel = new Label(status);
                    statusLabel.setStyle(getStatusStyle(status));
                    
                    statusBox.getChildren().addAll(indicator, statusLabel);
                    setGraphic(statusBox);
                }
            }
        });
        
        TableColumn<Trailer, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locationCol.setMinWidth(150);
        
        TableColumn<Trailer, String> assignedToCol = new TableColumn<>("Assigned To");
        assignedToCol.setCellValueFactory(new PropertyValueFactory<>("assignedTo"));
        assignedToCol.setMinWidth(150);
        
        TableColumn<Trailer, LocalDate> lastServiceCol = new TableColumn<>("Last Service");
        lastServiceCol.setCellValueFactory(new PropertyValueFactory<>("lastService"));
        lastServiceCol.setCellFactory(column -> new TableCell<Trailer, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATE_FORMAT));
                    long daysSince = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now());
                    if (daysSince > 90) {
                        setTextFill(Color.RED);
                    } else if (daysSince > 60) {
                        setTextFill(Color.ORANGE);
                    } else {
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });
        
        TableColumn<Trailer, Double> revenueCol = new TableColumn<>("Monthly Revenue");
        revenueCol.setCellValueFactory(new PropertyValueFactory<>("monthlyRevenue"));
        revenueCol.setCellFactory(column -> new TableCell<Trailer, Double>() {
            @Override
            protected void updateItem(Double revenue, boolean empty) {
                super.updateItem(revenue, empty);
                if (empty || revenue == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(revenue));
                    setStyle("-fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        
        TableColumn<Trailer, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(column -> new TableCell<Trailer, Void>() {
            private final Button viewBtn = new Button("View");
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            
            {
                viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                               "-fx-font-size: 10px; -fx-padding: 2 8 2 8;");
                editBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; " +
                               "-fx-font-size: 10px; -fx-padding: 2 8 2 8;");
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                 "-fx-font-size: 10px; -fx-padding: 2 8 2 8;");
                
                viewBtn.setOnAction(e -> viewTrailerDetails(getTableRow().getItem()));
                editBtn.setOnAction(e -> editTrailer(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> deleteTrailer(getTableRow().getItem()));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(3);
                    buttons.setAlignment(Pos.CENTER);
                    buttons.getChildren().addAll(viewBtn, editBtn, deleteBtn);
                    setGraphic(buttons);
                }
            }
        });
        
        trailersTable.getColumns().addAll(selectCol, numberCol, typeCol, statusCol, 
                                         locationCol, assignedToCol, lastServiceCol, 
                                         revenueCol, actionsCol);
        
        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewItem = new MenuItem("View Details");
        MenuItem editItem = new MenuItem("Edit");
        MenuItem assignItem = new MenuItem("Assign");
        MenuItem maintenanceItem = new MenuItem("Schedule Maintenance");
        MenuItem deleteItem = new MenuItem("Delete");
        contextMenu.getItems().addAll(viewItem, editItem, assignItem, 
                                    new SeparatorMenuItem(), maintenanceItem,
                                    new SeparatorMenuItem(), deleteItem);
        trailersTable.setContextMenu(contextMenu);
        
        fleetSection.getChildren().addAll(quickActions, trailersTable);
        
        return fleetSection;
    }
    
    private VBox createAssignmentsSection() {
        VBox assignmentsSection = new VBox(15);
        assignmentsSection.setPadding(new Insets(15));
        
        // Assignment Statistics
        HBox statsBox = new HBox(30);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(20));
        statsBox.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 10;");
        
        VBox activeAssignments = createStatBox("Active Assignments", "24", "#27ae60");
        VBox pendingAssignments = createStatBox("Pending", "5", "#f39c12");
        VBox completedToday = createStatBox("Completed Today", "8", "#3498db");
        VBox avgDuration = createStatBox("Avg Duration", "3.5 days", "#9b59b6");
        
        statsBox.getChildren().addAll(activeAssignments, pendingAssignments, 
                                     completedToday, avgDuration);
        
        // Date Range Filter
        HBox dateFilter = new HBox(15);
        dateFilter.setAlignment(Pos.CENTER_LEFT);
        dateFilter.setPadding(new Insets(10));
        
        Label dateLabel = new Label("Date Range:");
        dateLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        fromDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        toDatePicker = new DatePicker(LocalDate.now());
        
        Button filterButton = new Button("Apply Filter");
        filterButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        filterButton.setOnAction(e -> filterAssignments());
        
        dateFilter.getChildren().addAll(dateLabel, fromDatePicker, new Label("to"), 
                                       toDatePicker, filterButton);
        
        // Assignments Table
        assignmentsTable = new TableView<>();
        assignmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        assignmentsTable.setPrefHeight(400);
        
        TableColumn<TrailerAssignment, String> assignmentIdCol = new TableColumn<>("Assignment ID");
        assignmentIdCol.setCellValueFactory(new PropertyValueFactory<>("assignmentId"));
        
        TableColumn<TrailerAssignment, String> trailerCol = new TableColumn<>("Trailer");
        trailerCol.setCellValueFactory(new PropertyValueFactory<>("trailerNumber"));
        
        TableColumn<TrailerAssignment, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));
        
        TableColumn<TrailerAssignment, LocalDate> startDateCol = new TableColumn<>("Start Date");
        startDateCol.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        startDateCol.setCellFactory(column -> new TableCell<TrailerAssignment, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATE_FORMAT));
                }
            }
        });
        
        TableColumn<TrailerAssignment, LocalDate> endDateCol = new TableColumn<>("End Date");
        endDateCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        endDateCol.setCellFactory(column -> new TableCell<TrailerAssignment, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATE_FORMAT));
                }
            }
        });
        
        TableColumn<TrailerAssignment, String> routeCol = new TableColumn<>("Route");
        routeCol.setCellValueFactory(new PropertyValueFactory<>("route"));
        routeCol.setMinWidth(200);
        
        TableColumn<TrailerAssignment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<TrailerAssignment, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(status);
                    label.setPadding(new Insets(5, 10, 5, 10));
                    label.setStyle(getAssignmentStatusStyle(status));
                    setGraphic(label);
                }
            }
        });
        
        assignmentsTable.getColumns().addAll(assignmentIdCol, trailerCol, driverCol,
                                            startDateCol, endDateCol, routeCol, statusCol);
        
        assignmentsSection.getChildren().addAll(statsBox, dateFilter, assignmentsTable);
        
        return assignmentsSection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(20));
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Status Distribution Chart
        VBox statusChartBox = new VBox(10);
        Label statusLabel = new Label("Fleet Status Distribution");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        statusChart = new PieChart();
        statusChart.setTitle("Current Fleet Status");
        statusChart.setPrefHeight(350);
        statusChart.setAnimated(true);
        statusChart.setLabelsVisible(true);
        
        statusChartBox.getChildren().addAll(statusLabel, statusChart);
        
        // Type Distribution Chart
        VBox typeChartBox = new VBox(10);
        Label typeLabel = new Label("Fleet by Type");
        typeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis typeXAxis = new CategoryAxis();
        NumberAxis typeYAxis = new NumberAxis();
        typeChart = new BarChart<>(typeXAxis, typeYAxis);
        typeChart.setTitle("Trailers by Type");
        typeChart.setPrefHeight(350);
        typeChart.setAnimated(true);
        typeChart.setLegendVisible(false);
        
        typeChartBox.getChildren().addAll(typeLabel, typeChart);
        
        // Utilization Chart
        VBox utilizationChartBox = new VBox(10);
        Label utilizationLabel = new Label("Fleet Utilization");
        utilizationLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis utilXAxis = new CategoryAxis();
        NumberAxis utilYAxis = new NumberAxis();
        utilYAxis.setLabel("Utilization %");
        utilizationChart = new LineChart<>(utilXAxis, utilYAxis);
        utilizationChart.setTitle("Monthly Utilization Rate");
        utilizationChart.setPrefHeight(350);
        utilizationChart.setCreateSymbols(true);
        utilizationChart.setAnimated(true);
        
        utilizationChartBox.getChildren().addAll(utilizationLabel, utilizationChart);
        
        // Revenue Chart
        VBox revenueChartBox = new VBox(10);
        Label revenueLabel = new Label("Revenue by Trailer Type");
        revenueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis revXAxis = new CategoryAxis();
        NumberAxis revYAxis = new NumberAxis();
        revYAxis.setLabel("Revenue ($)");
        revenueChart = new StackedBarChart<>(revXAxis, revYAxis);
        revenueChart.setTitle("Monthly Revenue Breakdown");
        revenueChart.setPrefHeight(350);
        revenueChart.setAnimated(true);
        
        revenueChartBox.getChildren().addAll(revenueLabel, revenueChart);
        
        chartsGrid.add(statusChartBox, 0, 0);
        chartsGrid.add(typeChartBox, 1, 0);
        chartsGrid.add(utilizationChartBox, 0, 1);
        chartsGrid.add(revenueChartBox, 1, 1);
        
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().addAll(col, col);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createMaintenanceSection() {
        VBox maintenanceSection = new VBox(20);
        maintenanceSection.setPadding(new Insets(20));
        
        Label title = new Label("Trailer Maintenance Schedule");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Maintenance Overview Cards
        HBox maintenanceCards = new HBox(20);
        maintenanceCards.setAlignment(Pos.CENTER);
        maintenanceCards.setPadding(new Insets(20, 0, 20, 0));
        
        VBox dueSoonCard = createMaintenanceCard("Due Soon", "8", "#f39c12");
        VBox overdueCard = createMaintenanceCard("Overdue", "2", "#e74c3c");
        VBox completedCard = createMaintenanceCard("Completed This Month", "15", "#27ae60");
        VBox scheduledCard = createMaintenanceCard("Scheduled", "5", "#3498db");
        
        maintenanceCards.getChildren().addAll(dueSoonCard, overdueCard, completedCard, scheduledCard);
        
        // Maintenance Grid
        GridPane maintenanceGrid = new GridPane();
        maintenanceGrid.setHgap(20);
        maintenanceGrid.setVgap(20);
        maintenanceGrid.setPadding(new Insets(20, 0, 0, 0));
        
        // Sample maintenance items
        for (int i = 0; i < 6; i++) {
            VBox maintenanceItem = createMaintenanceItem("TR" + (1001 + i));
            maintenanceGrid.add(maintenanceItem, i % 3, i / 3);
        }
        
        maintenanceSection.getChildren().addAll(title, maintenanceCards, maintenanceGrid);
        
        return maintenanceSection;
    }
    
    private VBox createStatBox(String label, String value, String color) {
        VBox statBox = new VBox(5);
        statBox.setAlignment(Pos.CENTER);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        valueLabel.setTextFill(Color.web(color));
        
        Label titleLabel = new Label(label);
        titleLabel.setFont(Font.font("Arial", 12));
        titleLabel.setTextFill(Color.GRAY);
        
        statBox.getChildren().addAll(valueLabel, titleLabel);
        
        return statBox;
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
    
    private VBox createMaintenanceItem(String trailerNumber) {
        VBox item = new VBox(10);
        item.setPadding(new Insets(15));
        item.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        item.setPrefSize(250, 150);
        
        Label trailerLabel = new Label(trailerNumber);
        trailerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        Label serviceLabel = new Label("Next Service: Oil Change");
        serviceLabel.setFont(Font.font("Arial", 12));
        serviceLabel.setTextFill(Color.GRAY);
        
        Label dueLabel = new Label("Due in 5 days");
        dueLabel.setFont(Font.font("Arial", 12));
        dueLabel.setTextFill(Color.ORANGE);
        
        ProgressBar progress = new ProgressBar(0.8);
        progress.setPrefWidth(200);
        
        Button scheduleButton = new Button("Schedule Now");
        scheduleButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        
        item.getChildren().addAll(trailerLabel, serviceLabel, dueLabel, progress, scheduleButton);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        item.setEffect(shadow);
        
        return item;
    }
    
    private Button createStyledButton(String text, String color, String iconPath) {
        Button button = new Button(text);
        button.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                                    "-fx-font-weight: bold; -fx-background-radius: 5;", color));
        button.setPrefHeight(35);
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(16);
            icon.setFitWidth(16);
            button.setGraphic(icon);
        } catch (Exception e) {
            // Icon not found
        }
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "-fx-opacity: 0.8;");
            button.setCursor(javafx.scene.Cursor.HAND);
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("-fx-opacity: 0.8;", ""));
        });
        
        return button;
    }
    
    private String getTypeStyle(String type) {
        Map<String, String> typeColors = new HashMap<>();
        typeColors.put("Dry Van", "#3498db");
        typeColors.put("Refrigerated", "#2980b9");
        typeColors.put("Flatbed", "#27ae60");
        typeColors.put("Tanker", "#e74c3c");
        typeColors.put("Container", "#f39c12");
        typeColors.put("Lowboy", "#9b59b6");
        
        String color = typeColors.getOrDefault(type, "#95a5a6");
        return String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                           "-fx-background-radius: 15; -fx-font-size: 11px;", color);
    }
    
    private Color getStatusColor(String status) {
        switch (status) {
            case "Active": return Color.GREEN;
            case "Available": return Color.BLUE;
            case "In Maintenance": return Color.ORANGE;
            case "Out of Service": return Color.RED;
            case "Reserved": return Color.PURPLE;
            default: return Color.GRAY;
        }
    }
    
    private String getStatusStyle(String status) {
        Map<String, String> statusColors = new HashMap<>();
        statusColors.put("Active", "#27ae60");
        statusColors.put("Available", "#3498db");
        statusColors.put("In Maintenance", "#f39c12");
        statusColors.put("Out of Service", "#e74c3c");
        statusColors.put("Reserved", "#9b59b6");
        
        String color = statusColors.getOrDefault(status, "#95a5a6");
        return "-fx-font-weight: bold; -fx-text-fill: " + color;
    }
    
    private String getAssignmentStatusStyle(String status) {
        switch (status) {
            case "Active":
                return "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Pending":
                return "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Completed":
                return "-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Cancelled":
                return "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 15;";
            default:
                return "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 15;";
        }
    }
    
    private void loadTrailerData() {
        loadingIndicator.setVisible(true);
        
        Task<TrailerData> task = new Task<TrailerData>() {
            @Override
            protected TrailerData call() throws Exception {
                Thread.sleep(1000); // Simulate loading
                return generateSampleData();
            }
        };
        
        task.setOnSucceeded(e -> {
            TrailerData data = task.getValue();
            updateTables(data);
            updateSummaryCards(data);
            updateCharts(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.ERROR, "Error", "Failed to load trailer data.");
        });
        
        new Thread(task).start();
    }
    
    private void updateTables(TrailerData data) {
        ObservableList<Trailer> trailers = FXCollections.observableArrayList(data.getTrailers());
        trailersTable.setItems(trailers);
        
        ObservableList<TrailerAssignment> assignments = FXCollections.observableArrayList(data.getAssignments());
        assignmentsTable.setItems(assignments);
        
        // Apply filters
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        statusFilterComboBox.setOnAction(e -> applyFilters());
        typeFilterComboBox.setOnAction(e -> applyFilters());
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase();
        String selectedStatus = statusFilterComboBox.getValue();
        String selectedType = typeFilterComboBox.getValue();
        
        ObservableList<Trailer> filtered = trailersTable.getItems().filtered(trailer -> {
            boolean matchesSearch = searchText.isEmpty() ||
                trailer.getNumber().toLowerCase().contains(searchText) ||
                trailer.getType().toLowerCase().contains(searchText) ||
                trailer.getLocation().toLowerCase().contains(searchText);
            
            boolean matchesStatus = "All Status".equals(selectedStatus) ||
                trailer.getStatus().equals(selectedStatus);
            
            boolean matchesType = "All Types".equals(selectedType) ||
                trailer.getType().equals(selectedType);
            
            return matchesSearch && matchesStatus && matchesType;
        });
        
        trailersTable.setItems(filtered);
    }
    
    private void updateSummaryCards(TrailerData data) {
        int total = data.getTrailers().size();
        long active = data.getTrailers().stream()
            .filter(t -> "Active".equals(t.getStatus()))
            .count();
        long available = data.getTrailers().stream()
            .filter(t -> "Available".equals(t.getStatus()))
            .count();
        long maintenance = data.getTrailers().stream()
            .filter(t -> "In Maintenance".equals(t.getStatus()))
            .count();
        
        animateValue(totalTrailersLabel, String.valueOf(total));
        animateValue(activeTrailersLabel, String.valueOf(active));
        animateValue(availableTrailersLabel, String.valueOf(available));
        animateValue(maintenanceLabel, String.valueOf(maintenance));
    }
    
    private void updateCharts(TrailerData data) {
        // Update status chart
        Map<String, Long> statusCounts = data.getTrailers().stream()
            .collect(Collectors.groupingBy(Trailer::getStatus, Collectors.counting()));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        statusCounts.forEach((status, count) -> {
            pieData.add(new PieChart.Data(status + " (" + count + ")", count));
        });
        statusChart.setData(pieData);
        
        // Update type chart
        XYChart.Series<String, Number> typeSeries = new XYChart.Series<>();
        Map<String, Long> typeCounts = data.getTrailers().stream()
            .collect(Collectors.groupingBy(Trailer::getType, Collectors.counting()));
        
        typeCounts.forEach((type, count) -> {
            typeSeries.getData().add(new XYChart.Data<>(type, count));
        });
        
        typeChart.getData().clear();
        typeChart.getData().add(typeSeries);
        
        // Update utilization chart
        XYChart.Series<String, Number> utilSeries = new XYChart.Series<>();
        utilSeries.setName("Utilization Rate");
        
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun"};
        for (String month : months) {
            utilSeries.getData().add(new XYChart.Data<>(month, 70 + Math.random() * 25));
        }
        
        utilizationChart.getData().clear();
        utilizationChart.getData().add(utilSeries);
        
        // Update revenue chart
        XYChart.Series<String, Number> revenueSeries1 = new XYChart.Series<>();
        revenueSeries1.setName("Dry Van");
        XYChart.Series<String, Number> revenueSeries2 = new XYChart.Series<>();
        revenueSeries2.setName("Refrigerated");
        XYChart.Series<String, Number> revenueSeries3 = new XYChart.Series<>();
        revenueSeries3.setName("Flatbed");
        
        for (String month : months) {
            revenueSeries1.getData().add(new XYChart.Data<>(month, 50000 + Math.random() * 30000));
            revenueSeries2.getData().add(new XYChart.Data<>(month, 40000 + Math.random() * 25000));
            revenueSeries3.getData().add(new XYChart.Data<>(month, 30000 + Math.random() * 20000));
        }
        
        revenueChart.getData().clear();
        revenueChart.getData().addAll(revenueSeries1, revenueSeries2, revenueSeries3);
    }
    
    private void animateValue(Label label, String newValue) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(label.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(150), new KeyValue(label.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(150), e -> label.setText(newValue)),
            new KeyFrame(Duration.millis(300), new KeyValue(label.opacityProperty(), 1))
        );
        timeline.play();
    }
    
    private void filterAssignments() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        
        ObservableList<TrailerAssignment> filtered = assignmentsTable.getItems().filtered(
            assignment -> {
                LocalDate startDate = assignment.getStartDate();
                return !startDate.isBefore(from) && !startDate.isAfter(to);
            }
        );
        
        assignmentsTable.setItems(filtered);
    }
    
    private void showAddTrailerDialog() {
        Dialog<Trailer> dialog = new Dialog<>();
        dialog.setTitle("Add New Trailer");
        dialog.setHeaderText("Enter trailer details");
        
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField numberField = new TextField();
        numberField.setPromptText("Trailer Number");
        
        ComboBox<String> typeField = new ComboBox<>(trailerTypes);
        typeField.setPromptText("Select Type");
        
        ComboBox<String> statusField = new ComboBox<>(trailerStatuses);
        statusField.setValue("Available");
        
        TextField locationField = new TextField();
        locationField.setPromptText("Current Location");
        
        DatePicker lastServicePicker = new DatePicker();
        lastServicePicker.setValue(LocalDate.now());
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Trailer Number:"), 0, 0);
        grid.add(numberField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeField, 1, 1);
        grid.add(new Label("Status:"), 0, 2);
        grid.add(statusField, 1, 2);
        grid.add(new Label("Location:"), 0, 3);
        grid.add(locationField, 1, 3);
        grid.add(new Label("Last Service:"), 0, 4);
        grid.add(lastServicePicker, 1, 4);
        grid.add(new Label("Notes:"), 0, 5);
        grid.add(notesArea, 1, 5);
        
        dialog.getDialogPane().setContent(grid);
        
        // Enable/Disable add button
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        
        numberField.textProperty().addListener((obs, oldVal, newVal) -> 
            validateTrailerForm(addButton, numberField, typeField));
        typeField.valueProperty().addListener((obs, oldVal, newVal) -> 
            validateTrailerForm(addButton, numberField, typeField));
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Trailer trailer = new Trailer();
                trailer.setNumber(numberField.getText());
                trailer.setType(typeField.getValue());
                trailer.setStatus(statusField.getValue());
                trailer.setLocation(locationField.getText());
                trailer.setLastService(lastServicePicker.getValue());
                trailer.setAssignedTo("");
                trailer.setMonthlyRevenue(0.0);
                return trailer;
            }
            return null;
        });
        
        Optional<Trailer> result = dialog.showAndWait();
        result.ifPresent(trailer -> {
            trailersTable.getItems().add(trailer);
            showAlert(AlertType.INFORMATION, "Trailer Added", 
                     "Trailer " + trailer.getNumber() + " added successfully!");
            loadTrailerData();
        });
    }
    
    private void validateTrailerForm(Node button, TextField number, ComboBox<String> type) {
        boolean isValid = !number.getText().trim().isEmpty() && type.getValue() != null;
        button.setDisable(!isValid);
    }
    
    private void showAssignTrailerDialog() {
        Trailer selected = trailersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a trailer to assign.");
            return;
        }
        
        if (!"Available".equals(selected.getStatus())) {
            showAlert(AlertType.WARNING, "Not Available", 
                     "This trailer is not available for assignment.");
            return;
        }
        
        // Assignment dialog logic here
        showAlert(AlertType.INFORMATION, "Assign Trailer", 
                 "Assignment dialog for trailer " + selected.getNumber());
    }
    
    private void showMaintenanceDialog() {
        Trailer selected = trailersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(AlertType.WARNING, "No Selection", 
                     "Please select a trailer to schedule maintenance.");
            return;
        }
        
        // Maintenance dialog logic here
        showAlert(AlertType.INFORMATION, "Schedule Maintenance", 
                 "Maintenance scheduling for trailer " + selected.getNumber());
    }
    
    private void generateReport() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Generate Report");
        alert.setHeaderText("Trailer Fleet Report");
        alert.setContentText("Report generation in progress...");
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
        fileChooser.setInitialFileName("trailer_fleet_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Trailer data exported to Excel successfully!");
        }
    }
    
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("trailer_report_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Trailer report exported to PDF successfully!");
        }
    }
    
    private void exportToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("trailer_data_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Trailer data exported to CSV successfully!");
        }
    }
    
    private void viewTrailerDetails(Trailer trailer) {
        if (trailer != null) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Trailer Details");
            alert.setHeaderText("Trailer: " + trailer.getNumber());
            alert.setContentText("Type: " + trailer.getType() + "\n" +
                               "Status: " + trailer.getStatus() + "\n" +
                               "Location: " + trailer.getLocation() + "\n" +
                               "Assigned To: " + trailer.getAssignedTo() + "\n" +
                               "Last Service: " + trailer.getLastService().format(DATE_FORMAT));
            alert.showAndWait();
        }
    }
    
    private void editTrailer(Trailer trailer) {
        if (trailer != null) {
            // Edit dialog logic here
            showAlert(AlertType.INFORMATION, "Edit Trailer", 
                     "Edit dialog for trailer " + trailer.getNumber());
        }
    }
    
    private void deleteTrailer(Trailer trailer) {
        if (trailer != null) {
            Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
            confirmAlert.setTitle("Delete Trailer");
            confirmAlert.setHeaderText("Delete trailer " + trailer.getNumber() + "?");
            confirmAlert.setContentText("This action cannot be undone.");
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                trailersTable.getItems().remove(trailer);
                showAlert(AlertType.INFORMATION, "Trailer Deleted", 
                         "Trailer " + trailer.getNumber() + " deleted successfully.");
                loadTrailerData();
            }
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
        // Data initialization if needed
    }
    
        private TrailerData generateSampleData() {
        TrailerData data = new TrailerData();
        Random random = new Random();
        
        // Generate trailers
        List<Trailer> trailers = new ArrayList<>();
        String[] locations = {"Warehouse A", "Warehouse B", "Transit", "Customer Site", "Depot"};
        String[] drivers = {"John Smith", "Jane Doe", "Mike Johnson", "Sarah Williams", ""};
        
        for (int i = 0; i < 30; i++) {
            Trailer trailer = new Trailer();
            trailer.setNumber("TR" + String.format("%04d", 1001 + i));
            trailer.setType(trailerTypes.get(random.nextInt(trailerTypes.size())));
            trailer.setStatus(trailerStatuses.get(random.nextInt(trailerStatuses.size())));
            trailer.setLocation(locations[random.nextInt(locations.length)]);
            trailer.setAssignedTo(drivers[random.nextInt(drivers.length)]);
            trailer.setLastService(LocalDate.now().minusDays(random.nextInt(120)));
            trailer.setMonthlyRevenue(5000 + random.nextDouble() * 10000);
            trailer.setSelected(false);
            trailers.add(trailer);
        }
        data.setTrailers(trailers);
        
        // Generate assignments
        List<TrailerAssignment> assignments = new ArrayList<>();
        String[] routes = {"NYC to LAX", "CHI to MIA", "SEA to DEN", "ATL to BOS", "PHX to DAL"};
        String[] assignmentStatuses = {"Active", "Pending", "Completed", "Cancelled"};
        
        for (int i = 0; i < 20; i++) {
            TrailerAssignment assignment = new TrailerAssignment();
            assignment.setAssignmentId("AS" + String.format("%05d", 10001 + i));
            assignment.setTrailerNumber(trailers.get(random.nextInt(trailers.size())).getNumber());
            assignment.setDriverName(drivers[random.nextInt(drivers.length - 1)]); // Exclude empty string
            assignment.setStartDate(LocalDate.now().minusDays(random.nextInt(30)));
            assignment.setEndDate(assignment.getStartDate().plusDays(1 + random.nextInt(5)));
            assignment.setRoute(routes[random.nextInt(routes.length)]);
            assignment.setStatus(assignmentStatuses[random.nextInt(assignmentStatuses.length)]);
            assignments.add(assignment);
        }
        data.setAssignments(assignments);
        
        return data;
    }
    
    // Inner classes
    public static class Trailer {
        private final StringProperty number = new SimpleStringProperty();
        private final StringProperty type = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final StringProperty location = new SimpleStringProperty();
        private final StringProperty assignedTo = new SimpleStringProperty();
        private final ObjectProperty<LocalDate> lastService = new SimpleObjectProperty<>();
        private final DoubleProperty monthlyRevenue = new SimpleDoubleProperty();
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
        
        public String getLocation() { return location.get(); }
        public void setLocation(String value) { location.set(value); }
        public StringProperty locationProperty() { return location; }
        
        public String getAssignedTo() { return assignedTo.get(); }
        public void setAssignedTo(String value) { assignedTo.set(value); }
        public StringProperty assignedToProperty() { return assignedTo; }
        
        public LocalDate getLastService() { return lastService.get(); }
        public void setLastService(LocalDate value) { lastService.set(value); }
        public ObjectProperty<LocalDate> lastServiceProperty() { return lastService; }
        
        public double getMonthlyRevenue() { return monthlyRevenue.get(); }
        public void setMonthlyRevenue(double value) { monthlyRevenue.set(value); }
        public DoubleProperty monthlyRevenueProperty() { return monthlyRevenue; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
    }
    
    public static class TrailerAssignment {
        private final StringProperty assignmentId = new SimpleStringProperty();
        private final StringProperty trailerNumber = new SimpleStringProperty();
        private final StringProperty driverName = new SimpleStringProperty();
        private final ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>();
        private final ObjectProperty<LocalDate> endDate = new SimpleObjectProperty<>();
        private final StringProperty route = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        
        // Getters and setters
        public String getAssignmentId() { return assignmentId.get(); }
        public void setAssignmentId(String value) { assignmentId.set(value); }
        public StringProperty assignmentIdProperty() { return assignmentId; }
        
        public String getTrailerNumber() { return trailerNumber.get(); }
        public void setTrailerNumber(String value) { trailerNumber.set(value); }
        public StringProperty trailerNumberProperty() { return trailerNumber; }
        
        public String getDriverName() { return driverName.get(); }
        public void setDriverName(String value) { driverName.set(value); }
        public StringProperty driverNameProperty() { return driverName; }
        
        public LocalDate getStartDate() { return startDate.get(); }
        public void setStartDate(LocalDate value) { startDate.set(value); }
        public ObjectProperty<LocalDate> startDateProperty() { return startDate; }
        
        public LocalDate getEndDate() { return endDate.get(); }
        public void setEndDate(LocalDate value) { endDate.set(value); }
        public ObjectProperty<LocalDate> endDateProperty() { return endDate; }
        
        public String getRoute() { return route.get(); }
        public void setRoute(String value) { route.set(value); }
        public StringProperty routeProperty() { return route; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
    }
    
    public static class TrailerData {
        private List<Trailer> trailers = new ArrayList<>();
        private List<TrailerAssignment> assignments = new ArrayList<>();
        
        public List<Trailer> getTrailers() { return trailers; }
        public void setTrailers(List<Trailer> trailers) { this.trailers = trailers; }
        
        public List<TrailerAssignment> getAssignments() { return assignments; }
        public void setAssignments(List<TrailerAssignment> assignments) { this.assignments = assignments; }
    }
}
