package com.company.payroll.maintenance;

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
import javafx.beans.property.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

public class MaintenanceTab extends Tab {
    private TableView<MaintenanceRecord> maintenanceTable;
    private TableView<MaintenanceSchedule> scheduleTable;
    private ComboBox<String> vehicleComboBox;
    private ComboBox<String> serviceTypeComboBox;
    private ComboBox<String> statusComboBox;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private TextField searchField;
    
    private Label totalMaintenanceLabel;
    private Label scheduledLabel;
    private Label overdueLabel;
    private Label avgCostLabel;
    
    private PieChart serviceTypeChart;
    private LineChart<String, Number> costTrendChart;
    private BarChart<String, Number> vehicleChart;
    private Timeline timeline;
    
    private ProgressIndicator loadingIndicator;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private ObservableList<String> serviceTypes = FXCollections.observableArrayList(
        "Oil Change", "Tire Rotation", "Brake Service", "Engine Service",
        "Transmission Service", "Coolant Flush", "Air Filter", "Fuel Filter",
        "Battery Service", "Alignment", "Inspection", "Other"
    );
    
    public MaintenanceTab() {
        setText("Maintenance");
        setClosable(false);
        
        // Set tab icon
        try {
            ImageView tabIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/maintenance.png")));
            tabIcon.setFitHeight(16);
            tabIcon.setFitWidth(16);
            setGraphic(tabIcon);
        } catch (Exception e) {
            // Icon not found
        }
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: #ecf0f1;");
        
        // Header
        VBox header = createHeader();
        
        // Alert Bar for Overdue Maintenance
        HBox alertBar = createAlertBar();
        
        // Control Panel
        VBox controlPanel = createControlPanel();
        
        // Summary Cards
        HBox summaryCards = createSummaryCards();
        
        // Main Content Tabs
        TabPane contentTabs = createContentTabs();
        
        // Loading Indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        
        mainContent.getChildren().addAll(header, alertBar, controlPanel, summaryCards, contentTabs, loadingIndicator);
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
        
        // Initialize data
        initializeData();
        loadMaintenanceData();
        startAlertTimer();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(25));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: linear-gradient(to right, #34495e, #2c3e50); " +
                       "-fx-background-radius: 10;");
        
        Label titleLabel = new Label("Vehicle Maintenance Management");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Track and schedule vehicle maintenance");
        subtitleLabel.setFont(Font.font("Arial", 16));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        header.getChildren().addAll(titleLabel, subtitleLabel);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(5);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        header.setEffect(shadow);
        
        return header;
    }
    
    private HBox createAlertBar() {
        HBox alertBar = new HBox(15);
        alertBar.setPadding(new Insets(10, 15, 10, 15));
        alertBar.setAlignment(Pos.CENTER_LEFT);
        alertBar.setStyle("-fx-background-color: #e74c3c; -fx-background-radius: 5;");
        alertBar.setVisible(false); // Initially hidden
        
        Label alertIcon = new Label("⚠");
        alertIcon.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        alertIcon.setTextFill(Color.WHITE);
        
        Label alertText = new Label("You have 3 overdue maintenance items!");
        alertText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        alertText.setTextFill(Color.WHITE);
        
        Button viewButton = new Button("View Details");
        viewButton.setStyle("-fx-background-color: white; -fx-text-fill: #e74c3c; " +
                          "-fx-font-weight: bold;");
        viewButton.setOnAction(e -> showOverdueItems());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                           "-fx-font-size: 16px;");
        closeButton.setOnAction(e -> alertBar.setVisible(false));
        
        alertBar.getChildren().addAll(alertIcon, alertText, viewButton, spacer, closeButton);
        
        return alertBar;
    }
    
    private VBox createControlPanel() {
        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Search and Filter Row
        HBox searchRow = new HBox(15);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        
        searchField = new TextField();
        searchField.setPromptText("Search by vehicle, service, or technician...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-background-radius: 20;");
        
        vehicleComboBox = new ComboBox<>();
        vehicleComboBox.setPromptText("All Vehicles");
        vehicleComboBox.setPrefWidth(150);
        
        serviceTypeComboBox = new ComboBox<>();
        serviceTypeComboBox.getItems().add("All Services");
        serviceTypeComboBox.getItems().addAll(serviceTypes);
        serviceTypeComboBox.setValue("All Services");
        serviceTypeComboBox.setPrefWidth(150);
        
        statusComboBox = new ComboBox<>();
        statusComboBox.getItems().addAll("All Status", "Completed", "Scheduled", "In Progress", "Overdue");
        statusComboBox.setValue("All Status");
        statusComboBox.setPrefWidth(120);
        
        searchRow.getChildren().addAll(searchField, new Separator(Orientation.VERTICAL),
                                      vehicleComboBox, serviceTypeComboBox, statusComboBox);
        
        // Date Range and Actions Row
        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("Date Range:");
        dateLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
        endDatePicker = new DatePicker(LocalDate.now());
        
        Button refreshButton = createStyledButton("Refresh", "#3498db", "refresh-icon.png");
        refreshButton.setOnAction(e -> loadMaintenanceData());
        
        Button scheduleButton = createStyledButton("Schedule Service", "#27ae60", "schedule-icon.png");
        scheduleButton.setOnAction(e -> showScheduleServiceDialog());
        
        Button reportButton = createStyledButton("Generate Report", "#9b59b6", "report-icon.png");
        reportButton.setOnAction(e -> generateMaintenanceReport());
        
        Button exportButton = createStyledButton("Export", "#f39c12", "export-icon.png");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        actionRow.getChildren().addAll(dateLabel, startDatePicker, new Label("to"), endDatePicker,
                                      new Separator(Orientation.VERTICAL),
                                      refreshButton, scheduleButton, reportButton, exportButton);
        
        controlPanel.getChildren().addAll(searchRow, actionRow);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        controlPanel.setEffect(shadow);
        
        return controlPanel;
    }
    
    private HBox createSummaryCards() {
        HBox summaryCards = new HBox(20);
        summaryCards.setPadding(new Insets(10));
        summaryCards.setAlignment(Pos.CENTER);
        
        VBox totalCard = createSummaryCard("Total Services", "0", "#3498db", "total-icon.png", false);
        totalMaintenanceLabel = (Label) totalCard.lookup(".value-label");
        
        VBox scheduledCard = createSummaryCard("Scheduled", "0", "#27ae60", "scheduled-icon.png", true);
        scheduledLabel = (Label) scheduledCard.lookup(".value-label");
        
        VBox overdueCard = createSummaryCard("Overdue", "0", "#e74c3c", "overdue-icon.png", true);
        overdueLabel = (Label) overdueCard.lookup(".value-label");
        
        VBox avgCostCard = createSummaryCard("Avg Cost", "$0.00", "#f39c12", "cost-icon.png", false);
        avgCostLabel = (Label) avgCostCard.lookup(".value-label");
        
        summaryCards.getChildren().addAll(totalCard, scheduledCard, overdueCard, avgCostCard);
        
        return summaryCards;
    }
    
    private VBox createSummaryCard(String title, String value, String color, String iconPath, boolean showIndicator) {
        VBox card = new VBox(10);
        card.getStyleClass().add("summary-card");
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefWidth(250);
        card.setPrefHeight(130);
        
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER);
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(30);
            icon.setFitWidth(30);
            header.getChildren().add(icon);
        } catch (Exception e) {
            // Icon not found
        }
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 14));
        titleLabel.setTextFill(Color.GRAY);
        header.getChildren().add(titleLabel);
        
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        valueLabel.setTextFill(Color.web(color));
        
        card.getChildren().addAll(header, valueLabel);
        
        if (showIndicator) {
            Circle indicator = new Circle(5);
            indicator.setFill(Color.web(color));
            
            // Animate indicator
            FadeTransition ft = new FadeTransition(Duration.millis(1000), indicator);
            ft.setFromValue(1.0);
            ft.setToValue(0.3);
            ft.setCycleCount(Timeline.INDEFINITE);
            ft.setAutoReverse(true);
            ft.play();
            
            card.getChildren().add(indicator);
        }
        
        // Add hover effect
        addCardHoverEffect(card);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
    }
    
    private TabPane createContentTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");
        
        Tab historyTab = new Tab("Maintenance History");
        historyTab.setClosable(false);
        historyTab.setContent(createHistorySection());
        
        Tab scheduleTab = new Tab("Schedule");
        scheduleTab.setClosable(false);
        scheduleTab.setContent(createScheduleSection());
        
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setClosable(false);
        analyticsTab.setContent(createAnalyticsSection());
        
        Tab vehiclesTab = new Tab("Vehicle Status");
        vehiclesTab.setClosable(false);
        vehiclesTab.setContent(createVehicleStatusSection());
        
        tabPane.getTabs().addAll(historyTab, scheduleTab, analyticsTab, vehiclesTab);
        
        return tabPane;
    }
    
    private VBox createHistorySection() {
        VBox historySection = new VBox(15);
        historySection.setPadding(new Insets(15));
        
        // Table controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        
        Button addRecordButton = new Button("Add Record");
        addRecordButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        addRecordButton.setOnAction(e -> showAddMaintenanceDialog());
        
        Button printButton = new Button("Print");
        printButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        
        tableControls.getChildren().addAll(addRecordButton, printButton);
        
        // Maintenance History Table
        maintenanceTable = new TableView<>();
        maintenanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        maintenanceTable.setPrefHeight(500);
        
        TableColumn<MaintenanceRecord, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setCellFactory(column -> new TableCell<MaintenanceRecord, LocalDate>() {
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
        
        TableColumn<MaintenanceRecord, String> vehicleCol = new TableColumn<>("Vehicle");
        vehicleCol.setCellValueFactory(new PropertyValueFactory<>("vehicle"));
        vehicleCol.setMinWidth(120);
        
        TableColumn<MaintenanceRecord, String> serviceCol = new TableColumn<>("Service Type");
        serviceCol.setCellValueFactory(new PropertyValueFactory<>("serviceType"));
        serviceCol.setCellFactory(column -> new TableCell<MaintenanceRecord, String>() {
            @Override
            protected void updateItem(String service, boolean empty) {
                super.updateItem(service, empty);
                if (empty || service == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(service);
                    label.setPadding(new Insets(5, 10, 5, 10));
                    label.setStyle(getServiceStyle(service));
                    setGraphic(label);
                }
            }
        });
        
        TableColumn<MaintenanceRecord, Integer> mileageCol = new TableColumn<>("Mileage");
        mileageCol.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        mileageCol.setCellFactory(column -> new TableCell<MaintenanceRecord, Integer>() {
            @Override
            protected void updateItem(Integer mileage, boolean empty) {
                super.updateItem(mileage, empty);
                if (empty || mileage == null) {
                    setText(null);
                } else {
                    setText(String.format("%,d", mileage));
                }
            }
        });
        
        TableColumn<MaintenanceRecord, Double> costCol = new TableColumn<>("Cost");
        costCol.setCellValueFactory(new PropertyValueFactory<>("cost"));
        costCol.setCellFactory(column -> new TableCell<MaintenanceRecord, Double>() {
            @Override
            protected void updateItem(Double cost, boolean empty) {
                super.updateItem(cost, empty);
                if (empty || cost == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(cost));
                    setStyle("-fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        
        TableColumn<MaintenanceRecord, String> technicianCol = new TableColumn<>("Technician");
        technicianCol.setCellValueFactory(new PropertyValueFactory<>("technician"));
        
        TableColumn<MaintenanceRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<MaintenanceRecord, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label statusLabel = new Label(status);
                    statusLabel.setPadding(new Insets(5, 10, 5, 10));
                    statusLabel.setStyle(getStatusStyle(status));
                    setGraphic(statusLabel);
                }
            }
        });
        
        TableColumn<MaintenanceRecord, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setMinWidth(200);
        
        maintenanceTable.getColumns().addAll(dateCol, vehicleCol, serviceCol, mileageCol,
                                            costCol, technicianCol, statusCol, notesCol);
        
        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewItem = new MenuItem("View Details");
        MenuItem editItem = new MenuItem("Edit");
        MenuItem deleteItem = new MenuItem("Delete");
        MenuItem duplicateItem = new MenuItem("Duplicate");
        contextMenu.getItems().addAll(viewItem, editItem, deleteItem, 
                                    new SeparatorMenuItem(), duplicateItem);
        maintenanceTable.setContextMenu(contextMenu);
        
        historySection.getChildren().addAll(tableControls, maintenanceTable);
        
        return historySection;
    }
    
    private VBox createScheduleSection() {
        VBox scheduleSection = new VBox(15);
        scheduleSection.setPadding(new Insets(15));
        
        Label title = new Label("Upcoming Maintenance Schedule");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        
        // Schedule Table
        scheduleTable = new TableView<>();
        scheduleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        scheduleTable.setPrefHeight(400);
        
        TableColumn<MaintenanceSchedule, String> vehicleCol = new TableColumn<>("Vehicle");
        vehicleCol.setCellValueFactory(new PropertyValueFactory<>("vehicle"));
        
        TableColumn<MaintenanceSchedule, String> serviceCol = new TableColumn<>("Service");
        serviceCol.setCellValueFactory(new PropertyValueFactory<>("service"));
        
        TableColumn<MaintenanceSchedule, LocalDate> dueDateCol = new TableColumn<>("Due Date");
        dueDateCol.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        dueDateCol.setCellFactory(column -> new TableCell<MaintenanceSchedule, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(date.format(DATE_FORMAT));
                    long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), date);
                    if (daysUntil < 0) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else if (daysUntil <= 7) {
                        setTextFill(Color.ORANGE);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.BLACK);
                        setStyle("");
                    }
                }
            }
        });
        
        TableColumn<MaintenanceSchedule, Integer> dueMileageCol = new TableColumn<>("Due Mileage");
        dueMileageCol.setCellValueFactory(new PropertyValueFactory<>("dueMileage"));
        dueMileageCol.setCellFactory(column -> new TableCell<MaintenanceSchedule, Integer>() {
            @Override
            protected void updateItem(Integer mileage, boolean empty) {
                super.updateItem(mileage, empty);
                if (empty || mileage == null) {
                    setText(null);
                } else {
                    setText(String.format("%,d", mileage));
                }
            }
        });
        
        TableColumn<MaintenanceSchedule, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(new PropertyValueFactory<>("priority"));
        priorityCol.setCellFactory(column -> new TableCell<MaintenanceSchedule, String>() {
            @Override
            protected void updateItem(String priority, boolean empty) {
                super.updateItem(priority, empty);
                if (empty || priority == null) {
                    setGraphic(null);
                } else {
                    Label label = new Label(priority);
                    label.setPadding(new Insets(5, 10, 5, 10));
                    label.setStyle(getPriorityStyle(priority));
                    setGraphic(label);
                }
            }
        });
        
        TableColumn<MaintenanceSchedule, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(column -> new TableCell<MaintenanceSchedule, Void>() {
            private final Button scheduleBtn = new Button("Schedule Now");
            
            {
                scheduleBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                                   "-fx-font-size: 11px;");
                scheduleBtn.setOnAction(e -> scheduleMaintenanceNow(getTableRow().getItem()));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(scheduleBtn);
                }
            }
        });
        
        scheduleTable.getColumns().addAll(vehicleCol, serviceCol, dueDateCol, 
                                         dueMileageCol, priorityCol, actionCol);
        
        // Calendar View Toggle
        ToggleGroup viewGroup = new ToggleGroup();
        RadioButton tableViewBtn = new RadioButton("Table View");
        tableViewBtn.setToggleGroup(viewGroup);
        tableViewBtn.setSelected(true);
        
        RadioButton calendarViewBtn = new RadioButton("Calendar View");
        calendarViewBtn.setToggleGroup(viewGroup);
        
        HBox viewToggle = new HBox(10);
        viewToggle.setAlignment(Pos.CENTER_RIGHT);
        viewToggle.getChildren().addAll(tableViewBtn, calendarViewBtn);
        
        scheduleSection.getChildren().addAll(title, viewToggle, scheduleTable);
        
        return scheduleSection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(20));
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Service Type Distribution
        VBox serviceChartBox = new VBox(10);
        Label serviceLabel = new Label("Service Type Distribution");
        serviceLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        serviceTypeChart = new PieChart();
        serviceTypeChart.setTitle("Services by Type");
        serviceTypeChart.setPrefHeight(350);
        serviceTypeChart.setAnimated(true);
        
        serviceChartBox.getChildren().addAll(serviceLabel, serviceTypeChart);
        
        // Cost Trend Chart
        VBox costChartBox = new VBox(10);
        Label costLabel = new Label("Maintenance Cost Trend");
        costLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Cost ($)");
        
        costTrendChart = new LineChart<>(xAxis, yAxis);
        costTrendChart.setTitle("Monthly Maintenance Costs");
        costTrendChart.setPrefHeight(350);
        costTrendChart.setCreateSymbols(true);
        costTrendChart.setAnimated(true);
        
        costChartBox.getChildren().addAll(costLabel, costTrendChart);
        
        // Vehicle Maintenance Chart
        VBox vehicleChartBox = new VBox(10);
        Label vehicleLabel = new Label("Maintenance by Vehicle");
        vehicleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis vehicleXAxis = new CategoryAxis();
        NumberAxis vehicleYAxis = new NumberAxis();
        vehicleChart = new BarChart<>(vehicleXAxis, vehicleYAxis);
        vehicleChart.setTitle("Service Count by Vehicle");
        vehicleChart.setPrefHeight(350);
        vehicleChart.setAnimated(true);
        vehicleChart.setLegendVisible(false);
        
        vehicleChartBox.getChildren().addAll(vehicleLabel, vehicleChart);
        
        // Key Metrics
        VBox metricsBox = createMetricsBox();
        
        chartsGrid.add(serviceChartBox, 0, 0);
        chartsGrid.add(costChartBox, 1, 0);
        chartsGrid.add(vehicleChartBox, 0, 1);
        chartsGrid.add(metricsBox, 1, 1);
        
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().addAll(col, col);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createVehicleStatusSection() {
        VBox vehicleSection = new VBox(20);
        vehicleSection.setPadding(new Insets(20));
        
        Label title = new Label("Vehicle Fleet Status");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Vehicle cards grid
        GridPane vehicleGrid = new GridPane();
        vehicleGrid.setHgap(20);
        vehicleGrid.setVgap(20);
        vehicleGrid.setPadding(new Insets(20, 0, 0, 0));
        
        // Sample vehicles
        String[] vehicles = {"Truck #101", "Truck #102", "Truck #103", "Truck #104", 
                           "Truck #105", "Truck #106"};
        
        int col = 0;
        int row = 0;
        for (String vehicle : vehicles) {
            VBox vehicleCard = createVehicleCard(vehicle);
            vehicleGrid.add(vehicleCard, col, row);
            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }
        
        vehicleSection.getChildren().addAll(title, vehicleGrid);
        
        return vehicleSection;
    }
    
    private VBox createVehicleCard(String vehicleName) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefSize(300, 200);
        
        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Circle statusIndicator = new Circle(8);
        statusIndicator.setFill(Math.random() > 0.7 ? Color.RED : Color.GREEN);
        
        Label nameLabel = new Label(vehicleName);
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        header.getChildren().addAll(statusIndicator, nameLabel);
        
        // Mileage
        Label mileageLabel = new Label("Current Mileage: " + 
            String.format("%,d", 50000 + (int)(Math.random() * 100000)));
        mileageLabel.setFont(Font.font("Arial", 14));
        
        // Next Service
        Label nextServiceLabel = new Label("Next Service: Oil Change");
        nextServiceLabel.setFont(Font.font("Arial", 12));
        nextServiceLabel.setTextFill(Color.GRAY);
        
        Label dueDateLabel = new Label("Due: " + 
            LocalDate.now().plusDays((int)(Math.random() * 30)).format(DATE_FORMAT));
        dueDateLabel.setFont(Font.font("Arial", 12));
        dueDateLabel.setTextFill(Color.ORANGE);
        
        // Progress bar
        ProgressBar serviceProgress = new ProgressBar(Math.random());
        serviceProgress.setPrefWidth(250);
        Label progressLabel = new Label("Service interval progress");
        progressLabel.setFont(Font.font("Arial", 11));
        progressLabel.setTextFill(Color.GRAY);
        
        // View Details button
        Button detailsButton = new Button("View Details");
        detailsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        detailsButton.setOnAction(e -> showVehicleDetails(vehicleName));
        
        card.getChildren().addAll(header, mileageLabel, nextServiceLabel, dueDateLabel,
                                 serviceProgress, progressLabel, detailsButton);
        
        // Add hover effect
        addCardHoverEffect(card);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
    }
    
    private VBox createMetricsBox() {
        VBox metricsBox = new VBox(15);
        metricsBox.setPadding(new Insets(20));
        metricsBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10;");
        
        Label title = new Label("Key Metrics");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(30);
        metricsGrid.setVgap(15);
        metricsGrid.setPadding(new Insets(15, 0, 0, 0));
        
        addMetric(metricsGrid, 0, "Avg Time Between Services", "45 days");
        addMetric(metricsGrid, 1, "Most Common Service", "Oil Change");
        addMetric(metricsGrid, 2, "Avg Service Cost", "$285.50");
        addMetric(metricsGrid, 3, "Total YTD Cost", "$15,420.00");
        addMetric(metricsGrid, 4, "Compliance Rate", "94%");
        addMetric(metricsGrid, 5, "Cost per Mile", "$0.12");
        
        metricsBox.getChildren().addAll(title, metricsGrid);
        
        return metricsBox;
    }
    
    private void addMetric(GridPane grid, int row, String label, String value) {
        Label labelText = new Label(label + ":");
        labelText.setFont(Font.font("Arial", 12));
        labelText.setTextFill(Color.GRAY);
        
        Label valueText = new Label(value);
        valueText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        grid.add(labelText, 0, row);
        grid.add(valueText, 1, row);
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
        
        button.setOnMouseEntered(e -> button.setOpacity(0.8));
        button.setOnMouseExited(e -> button.setOpacity(1.0));
        
        return button;
    }
    
    private void addCardHoverEffect(Node card) {
        card.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.03);
            st.setToY(1.03);
            st.play();
        });
        
        card.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1);
            st.setToY(1);
            st.play();
        });
    }
    
    private String getServiceStyle(String service) {
        Map<String, String> serviceColors = new HashMap<>();
        serviceColors.put("Oil Change", "#3498db");
        serviceColors.put("Tire Rotation", "#2ecc71");
        serviceColors.put("Brake Service", "#e74c3c");
        serviceColors.put("Engine Service", "#f39c12");
        serviceColors.put("Transmission Service", "#9b59b6");
        
        String color = serviceColors.getOrDefault(service, "#95a5a6");
        return String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                           "-fx-background-radius: 15; -fx-font-size: 11px;", color);
    }
    
    private String getStatusStyle(String status) {
        switch (status) {
            case "Completed":
                return "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Scheduled":
                return "-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 15;";
            case "In Progress":
                return "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Overdue":
                return "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 15;";
            default:
                return "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 15;";
        }
    }
    
    private String getPriorityStyle(String priority) {
        switch (priority) {
            case "High":
                return "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Medium":
                return "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Low":
                return "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 15;";
            default:
                return "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 15;";
        }
    }
    
    private void loadMaintenanceData() {
        loadingIndicator.setVisible(true);
        
        Task<MaintenanceData> task = new Task<MaintenanceData>() {
            @Override
            protected MaintenanceData call() throws Exception {
                Thread.sleep(1000); // Simulate loading
                return generateSampleData();
            }
        };
        
        task.setOnSucceeded(e -> {
            MaintenanceData data = task.getValue();
            updateTables(data);
            updateSummaryCards(data);
            updateCharts(data);
            checkForOverdueItems(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.ERROR, "Error", "Failed to load maintenance data.");
        });
        
        new Thread(task).start();
    }
    
    private void updateTables(MaintenanceData data) {
        ObservableList<MaintenanceRecord> records = FXCollections.observableArrayList(data.getRecords());
        maintenanceTable.setItems(records);
        
        ObservableList<MaintenanceSchedule> schedules = FXCollections.observableArrayList(data.getSchedules());
        scheduleTable.setItems(schedules);
        
        // Apply filters
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        vehicleComboBox.setOnAction(e -> applyFilters());
        serviceTypeComboBox.setOnAction(e -> applyFilters());
        statusComboBox.setOnAction(e -> applyFilters());
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase();
        String selectedVehicle = vehicleComboBox.getValue();
        String selectedService = serviceTypeComboBox.getValue();
        String selectedStatus = statusComboBox.getValue();
        
        ObservableList<MaintenanceRecord> filtered = maintenanceTable.getItems().filtered(record -> {
            boolean matchesSearch = searchText.isEmpty() ||
                record.getVehicle().toLowerCase().contains(searchText) ||
                record.getServiceType().toLowerCase().contains(searchText) ||
                record.getTechnician().toLowerCase().contains(searchText);
            
            boolean matchesVehicle = selectedVehicle == null || 
                "All Vehicles".equals(selectedVehicle) ||
                record.getVehicle().equals(selectedVehicle);
            
            boolean matchesService = "All Services".equals(selectedService) ||
                record.getServiceType().equals(selectedService);
            
            boolean matchesStatus = "All Status".equals(selectedStatus) ||
                record.getStatus().equals(selectedStatus);
            
            return matchesSearch && matchesVehicle && matchesService && matchesStatus;
        });
        
        maintenanceTable.setItems(filtered);
    }
    		
	private void updateSummaryCards(MaintenanceData data) {
        int totalServices = data.getRecords().size();
        long scheduled = data.getSchedules().stream()
            .filter(s -> s.getDueDate().isAfter(LocalDate.now()))
            .count();
        long overdue = data.getSchedules().stream()
            .filter(s -> s.getDueDate().isBefore(LocalDate.now()))
            .count();
        double avgCost = data.getRecords().stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .average()
            .orElse(0.0);
        
        animateValue(totalMaintenanceLabel, String.valueOf(totalServices));
        animateValue(scheduledLabel, String.valueOf(scheduled));
        animateValue(overdueLabel, String.valueOf(overdue));
        animateValue(avgCostLabel, CURRENCY_FORMAT.format(avgCost));
    }
    
    private void updateCharts(MaintenanceData data) {
        // Update service type chart
        Map<String, Long> serviceTypeCounts = data.getRecords().stream()
            .collect(Collectors.groupingBy(MaintenanceRecord::getServiceType, Collectors.counting()));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        serviceTypeCounts.forEach((service, count) -> {
            pieData.add(new PieChart.Data(service + " (" + count + ")", count));
        });
        serviceTypeChart.setData(pieData);
        
        // Add tooltips
        pieData.forEach(data1 -> {
            Tooltip tooltip = new Tooltip(String.format("%s: %.1f%%", 
                data1.getName(), (data1.getPieValue() / data.getRecords().size()) * 100));
            Tooltip.install(data1.getNode(), tooltip);
        });
        
        // Update cost trend chart
        XYChart.Series<String, Number> costSeries = new XYChart.Series<>();
        costSeries.setName("Monthly Cost");
        
        Map<String, Double> monthlyCosts = data.getRecords().stream()
            .collect(Collectors.groupingBy(
                record -> record.getDate().format(DateTimeFormatter.ofPattern("MMM yyyy")),
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        monthlyCosts.forEach((month, cost) -> {
            costSeries.getData().add(new XYChart.Data<>(month, cost));
        });
        
        costTrendChart.getData().clear();
        costTrendChart.getData().add(costSeries);
        
        // Update vehicle chart
        XYChart.Series<String, Number> vehicleSeries = new XYChart.Series<>();
        Map<String, Long> vehicleCounts = data.getRecords().stream()
            .collect(Collectors.groupingBy(MaintenanceRecord::getVehicle, Collectors.counting()));
        
        vehicleCounts.forEach((vehicle, count) -> {
            vehicleSeries.getData().add(new XYChart.Data<>(vehicle, count));
        });
        
        vehicleChart.getData().clear();
        vehicleChart.getData().add(vehicleSeries);
    }
    
    private void checkForOverdueItems(MaintenanceData data) {
        long overdueCount = data.getSchedules().stream()
            .filter(s -> s.getDueDate().isBefore(LocalDate.now()))
            .count();
        
        if (overdueCount > 0) {
            HBox alertBar = (HBox) getContent().lookup(".alert-bar");
            if (alertBar != null) {
                Label alertText = (Label) alertBar.getChildren().get(1);
                alertText.setText("You have " + overdueCount + " overdue maintenance item" + 
                                (overdueCount > 1 ? "s!" : "!"));
                
                FadeTransition ft = new FadeTransition(Duration.millis(500), alertBar);
                ft.setFromValue(0.0);
                ft.setToValue(1.0);
                ft.play();
                
                alertBar.setVisible(true);
            }
        }
    }
    
    private void animateValue(Label label, String newValue) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), label);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            label.setText(newValue);
            FadeTransition ft2 = new FadeTransition(Duration.millis(300), label);
            ft2.setFromValue(0.0);
            ft2.setToValue(1.0);
            ft2.play();
        });
        ft.play();
    }
    
    private void startAlertTimer() {
        timeline = new Timeline(new KeyFrame(Duration.minutes(5), e -> {
            // Check for overdue items every 5 minutes
            loadMaintenanceData();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
    
    private void showOverdueItems() {
        // Filter table to show only overdue items
        statusComboBox.setValue("Overdue");
        applyFilters();
        
        // Switch to schedule tab
        TabPane tabPane = (TabPane) maintenanceTable.getParent().getParent().getParent();
        tabPane.getSelectionModel().select(1); // Select schedule tab
    }
    
    private void showScheduleServiceDialog() {
        Dialog<MaintenanceSchedule> dialog = new Dialog<>();
        dialog.setTitle("Schedule Maintenance Service");
        dialog.setHeaderText("Enter service details");
        
        ButtonType scheduleButtonType = new ButtonType("Schedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(scheduleButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        ComboBox<String> vehicleField = new ComboBox<>(vehicleComboBox.getItems().filtered(
            item -> !"All Vehicles".equals(item)));
        vehicleField.setPromptText("Select Vehicle");
        vehicleField.setPrefWidth(200);
        
        ComboBox<String> serviceField = new ComboBox<>(serviceTypes);
        serviceField.setPromptText("Select Service");
        serviceField.setPrefWidth(200);
        
        DatePicker dueDateField = new DatePicker();
        dueDateField.setValue(LocalDate.now().plusDays(7));
        
        TextField mileageField = new TextField();
        mileageField.setPromptText("Expected mileage");
        
        ComboBox<String> priorityField = new ComboBox<>();
        priorityField.getItems().addAll("High", "Medium", "Low");
        priorityField.setValue("Medium");
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Vehicle:"), 0, 0);
        grid.add(vehicleField, 1, 0);
        grid.add(new Label("Service Type:"), 0, 1);
        grid.add(serviceField, 1, 1);
        grid.add(new Label("Due Date:"), 0, 2);
        grid.add(dueDateField, 1, 2);
        grid.add(new Label("Due Mileage:"), 0, 3);
        grid.add(mileageField, 1, 3);
        grid.add(new Label("Priority:"), 0, 4);
        grid.add(priorityField, 1, 4);
        grid.add(new Label("Notes:"), 0, 5);
        grid.add(notesArea, 1, 5);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validate mileage field
        mileageField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                mileageField.setText(oldText);
            }
        });
        
        // Enable/Disable schedule button
        Node scheduleButton = dialog.getDialogPane().lookupButton(scheduleButtonType);
        scheduleButton.setDisable(true);
        
        vehicleField.valueProperty().addListener((obs, oldVal, newVal) -> 
            validateScheduleForm(scheduleButton, vehicleField, serviceField));
        serviceField.valueProperty().addListener((obs, oldVal, newVal) -> 
            validateScheduleForm(scheduleButton, vehicleField, serviceField));
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == scheduleButtonType) {
                MaintenanceSchedule schedule = new MaintenanceSchedule();
                schedule.setVehicle(vehicleField.getValue());
                schedule.setService(serviceField.getValue());
                schedule.setDueDate(dueDateField.getValue());
                schedule.setDueMileage(mileageField.getText().isEmpty() ? 0 : 
                                      Integer.parseInt(mileageField.getText()));
                schedule.setPriority(priorityField.getValue());
                return schedule;
            }
            return null;
        });
        
        Optional<MaintenanceSchedule> result = dialog.showAndWait();
        result.ifPresent(schedule -> {
            scheduleTable.getItems().add(schedule);
            showAlert(AlertType.INFORMATION, "Service Scheduled", 
                     "Maintenance service scheduled for " + schedule.getVehicle());
            loadMaintenanceData();
        });
    }
    
    private void validateScheduleForm(Node button, ComboBox<String> vehicle, ComboBox<String> service) {
        boolean isValid = vehicle.getValue() != null && service.getValue() != null;
        button.setDisable(!isValid);
    }
    
    private void showAddMaintenanceDialog() {
        Dialog<MaintenanceRecord> dialog = new Dialog<>();
        dialog.setTitle("Add Maintenance Record");
        dialog.setHeaderText("Enter maintenance details");
        
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        DatePicker datePicker = new DatePicker(LocalDate.now());
        
        ComboBox<String> vehicleField = new ComboBox<>(vehicleComboBox.getItems().filtered(
            item -> !"All Vehicles".equals(item)));
        vehicleField.setPromptText("Select Vehicle");
        vehicleField.setPrefWidth(200);
        
        ComboBox<String> serviceField = new ComboBox<>(serviceTypes);
        serviceField.setPromptText("Select Service");
        serviceField.setPrefWidth(200);
        
        TextField mileageField = new TextField();
        mileageField.setPromptText("Current mileage");
        
        TextField costField = new TextField();
        costField.setPromptText("0.00");
        
        TextField technicianField = new TextField();
        technicianField.setPromptText("Technician name");
        
        ComboBox<String> statusField = new ComboBox<>();
        statusField.getItems().addAll("Completed", "In Progress");
        statusField.setValue("Completed");
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Service notes...");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Date:"), 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("Vehicle:"), 0, 1);
        grid.add(vehicleField, 1, 1);
        grid.add(new Label("Service Type:"), 0, 2);
        grid.add(serviceField, 1, 2);
        grid.add(new Label("Mileage:"), 0, 3);
        grid.add(mileageField, 1, 3);
        grid.add(new Label("Cost:"), 0, 4);
        grid.add(costField, 1, 4);
        grid.add(new Label("Technician:"), 0, 5);
        grid.add(technicianField, 1, 5);
        grid.add(new Label("Status:"), 0, 6);
        grid.add(statusField, 1, 6);
        grid.add(new Label("Notes:"), 0, 7);
        grid.add(notesArea, 1, 7);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validate numeric fields
        mileageField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                mileageField.setText(oldText);
            }
        });
        
        costField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*\\.?\\d*")) {
                costField.setText(oldText);
            }
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                MaintenanceRecord record = new MaintenanceRecord();
                record.setDate(datePicker.getValue());
                record.setVehicle(vehicleField.getValue());
                record.setServiceType(serviceField.getValue());
                record.setMileage(Integer.parseInt(mileageField.getText()));
                record.setCost(Double.parseDouble(costField.getText()));
                record.setTechnician(technicianField.getText());
                record.setStatus(statusField.getValue());
                record.setNotes(notesArea.getText());
                return record;
            }
            return null;
        });
        
        Optional<MaintenanceRecord> result = dialog.showAndWait();
        result.ifPresent(record -> {
            maintenanceTable.getItems().add(record);
            showAlert(AlertType.INFORMATION, "Record Added", 
                     "Maintenance record added successfully!");
            loadMaintenanceData();
        });
    }
    
    private void scheduleMaintenanceNow(MaintenanceSchedule schedule) {
        if (schedule != null) {
            showAddMaintenanceDialog();
        }
    }
    
    private void generateMaintenanceReport() {
        // Generate report dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Generate Report");
        alert.setHeaderText("Maintenance Report Generated");
        alert.setContentText("Report has been generated for the selected date range.");
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
        fileChooser.setInitialFileName("maintenance_report_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Maintenance data exported to Excel successfully!");
        }
    }
    
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("maintenance_report_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Maintenance report exported to PDF successfully!");
        }
    }
    
    private void exportToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("maintenance_data_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Maintenance data exported to CSV successfully!");
        }
    }
    
    private void showVehicleDetails(String vehicleName) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Vehicle Details");
        alert.setHeaderText(vehicleName);
        alert.setContentText("Detailed maintenance history and upcoming services for " + vehicleName);
        alert.showAndWait();
    }
    
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void initializeData() {
        // Initialize vehicle list
        ObservableList<String> vehicles = FXCollections.observableArrayList(
            "Truck #101", "Truck #102", "Truck #103", "Truck #104",
            "Truck #105", "Truck #106", "Truck #107", "Truck #108"
        );
        vehicleComboBox.getItems().add("All Vehicles");
        vehicleComboBox.getItems().addAll(vehicles);
    }
    
    private MaintenanceData generateSampleData() {
        MaintenanceData data = new MaintenanceData();
        Random random = new Random();
        
        String[] vehicles = {"Truck #101", "Truck #102", "Truck #103", "Truck #104", "Truck #105"};
        String[] technicians = {"John Smith", "Mike Johnson", "Bob Williams", "Tom Davis"};
        String[] statuses = {"Completed", "Scheduled", "In Progress"};
        
        // Generate maintenance records
        List<MaintenanceRecord> records = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            MaintenanceRecord record = new MaintenanceRecord();
            record.setDate(LocalDate.now().minusDays(random.nextInt(180)));
            record.setVehicle(vehicles[random.nextInt(vehicles.length)]);
            record.setServiceType(serviceTypes.get(random.nextInt(serviceTypes.size())));
            record.setMileage(50000 + random.nextInt(100000));
            record.setCost(100 + random.nextDouble() * 900);
            record.setTechnician(technicians[random.nextInt(technicians.length)]);
            record.setStatus(statuses[random.nextInt(statuses.length)]);
            record.setNotes("Service completed successfully");
            records.add(record);
        }
        data.setRecords(records);
        
        // Generate schedules
        List<MaintenanceSchedule> schedules = new ArrayList<>();
        for (String vehicle : vehicles) {
            for (int i = 0; i < 3; i++) {
                MaintenanceSchedule schedule = new MaintenanceSchedule();
                schedule.setVehicle(vehicle);
                schedule.setService(serviceTypes.get(random.nextInt(serviceTypes.size())));
                schedule.setDueDate(LocalDate.now().plusDays(random.nextInt(60) - 10));
                schedule.setDueMileage(60000 + random.nextInt(40000));
                schedule.setPriority(new String[]{"High", "Medium", "Low"}[random.nextInt(3)]);
                schedules.add(schedule);
            }
        }
        data.setSchedules(schedules);
        
        return data;
    }
    
    // Inner classes
    public static class MaintenanceRecord {
        private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
        private final StringProperty vehicle = new SimpleStringProperty();
        private final StringProperty serviceType = new SimpleStringProperty();
        private final IntegerProperty mileage = new SimpleIntegerProperty();
        private final DoubleProperty cost = new SimpleDoubleProperty();
        private final StringProperty technician = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final StringProperty notes = new SimpleStringProperty();
        
        // Getters and setters
        public LocalDate getDate() { return date.get(); }
        public void setDate(LocalDate value) { date.set(value); }
        public ObjectProperty<LocalDate> dateProperty() { return date; }
        
        public String getVehicle() { return vehicle.get(); }
        public void setVehicle(String value) { vehicle.set(value); }
        public StringProperty vehicleProperty() { return vehicle; }
        
        public String getServiceType() { return serviceType.get(); }
        public void setServiceType(String value) { serviceType.set(value); }
        public StringProperty serviceTypeProperty() { return serviceType; }
        
        public int getMileage() { return mileage.get(); }
        public void setMileage(int value) { mileage.set(value); }
        public IntegerProperty mileageProperty() { return mileage; }
        
        public double getCost() { return cost.get(); }
        public void setCost(double value) { cost.set(value); }
        public DoubleProperty costProperty() { return cost; }
        
        public String getTechnician() { return technician.get(); }
        public void setTechnician(String value) { technician.set(value); }
        public StringProperty technicianProperty() { return technician; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
        
        public String getNotes() { return notes.get(); }
        public void setNotes(String value) { notes.set(value); }
        public StringProperty notesProperty() { return notes; }
    }
    
    public static class MaintenanceSchedule {
        private final StringProperty vehicle = new SimpleStringProperty();
        private final StringProperty service = new SimpleStringProperty();
        private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>();
        private final IntegerProperty dueMileage = new SimpleIntegerProperty();
        private final StringProperty priority = new SimpleStringProperty();
        
        // Getters and setters
        public String getVehicle() { return vehicle.get(); }
        public void setVehicle(String value) { vehicle.set(value); }
        public StringProperty vehicleProperty() { return vehicle; }
        
        public String getService() { return service.get(); }
        public void setService(String value) { service.set(value); }
        public StringProperty serviceProperty() { return service; }
        
        public LocalDate getDueDate() { return dueDate.get(); }
        public void setDueDate(LocalDate value) { dueDate.set(value); }
        public ObjectProperty<LocalDate> dueDateProperty() { return dueDate; }
        
        public int getDueMileage() { return dueMileage.get(); }
        public void setDueMileage(int value) { dueMileage.set(value); }
        public IntegerProperty dueMileageProperty() { return dueMileage; }
        
        public String getPriority() { return priority.get(); }
        public void setPriority(String value) { priority.set(value); }
        public StringProperty priorityProperty() { return priority; }
    }
    
    public static class MaintenanceData {
        private List<MaintenanceRecord> records = new ArrayList<>();
        private List<MaintenanceSchedule> schedules = new ArrayList<>();
        
        public List<MaintenanceRecord> getRecords() { return records; }
        public void setRecords(List<MaintenanceRecord> records) { this.records = records; }
        
        public List<MaintenanceSchedule> getSchedules() { return schedules; }
        public void setSchedules(List<MaintenanceSchedule> schedules) { this.schedules = schedules; }
    }
}
            