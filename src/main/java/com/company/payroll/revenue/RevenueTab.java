package com.company.payroll.revenue;

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

public class RevenueTab extends Tab {
    private TableView<RevenueEntry> revenueTable;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> customerComboBox;
    private ComboBox<String> periodComboBox;
    private TextField searchField;
    
    private Label totalRevenueLabel;
    private Label totalLoadsLabel;
    private Label avgRevenuePerLoadLabel;
    private Label outstandingLabel;
    
    private LineChart<String, Number> revenueChart;
    private BarChart<String, Number> customerChart;
    private PieChart statusChart;
    private AreaChart<String, Number> trendChart;
    
    private ProgressIndicator loadingIndicator;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    public RevenueTab() {
        setText("Revenue");
        setClosable(false);
        
        // Set tab icon
        try {
            ImageView tabIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/revenue.png")));
            tabIcon.setFitHeight(16);
            tabIcon.setFitWidth(16);
            setGraphic(tabIcon);
        } catch (Exception e) {
            // Icon not found
        }
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: #f5f5f5;");
        
        // Header
        VBox header = createHeader();
        
        // Control Panel
        HBox controlPanel = createControlPanel();
        
        // Summary Dashboard
        GridPane summaryDashboard = createSummaryDashboard();
        
        // Main Content Area with Charts and Table
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
        
        // Initialize with sample data
        initializeSampleData();
        loadRevenueData();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: linear-gradient(to right, #16a085, #27ae60); " +
                       "-fx-background-radius: 10;");
        
        Label titleLabel = new Label("Revenue Management");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Track and analyze your business revenue");
        subtitleLabel.setFont(Font.font("Arial", 16));
        subtitleLabel.setTextFill(Color.WHITE);
        subtitleLabel.setOpacity(0.9);
        
        header.getChildren().addAll(titleLabel, subtitleLabel);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(5);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        header.setEffect(shadow);
        
        return header;
    }
    
    private HBox createControlPanel() {
        HBox controlPanel = new HBox(15);
        controlPanel.setPadding(new Insets(15));
        controlPanel.setAlignment(Pos.CENTER_LEFT);
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Search Field
        searchField = new TextField();
        searchField.setPromptText("Search by invoice, customer, or load...");
        searchField.setPrefWidth(250);
        searchField.setStyle("-fx-background-radius: 20;");
        
        // Customer Filter
        Label customerLabel = new Label("Customer:");
        customerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        customerComboBox = new ComboBox<>();
        customerComboBox.setPromptText("All Customers");
        customerComboBox.setPrefWidth(180);
        
        // Period Selection
        Label periodLabel = new Label("Period:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        periodComboBox = new ComboBox<>();
        periodComboBox.getItems().addAll("This Month", "Last Month", "This Quarter", "Last Quarter", 
                                       "This Year", "Last Year", "Custom Range");
        periodComboBox.setValue("This Month");
        periodComboBox.setPrefWidth(150);
        
        // Date Range
        startDatePicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        endDatePicker = new DatePicker(LocalDate.now());
        HBox dateBox = new HBox(5);
        dateBox.getChildren().addAll(new Label("From:"), startDatePicker, new Label("To:"), endDatePicker);
        dateBox.setVisible(false);
        
        periodComboBox.setOnAction(e -> {
            dateBox.setVisible("Custom Range".equals(periodComboBox.getValue()));
            updateDateRange();
        });
        
        // Action Buttons
        Button refreshButton = createStyledButton("Refresh", "#3498db", "refresh-icon.png");
        refreshButton.setOnAction(e -> loadRevenueData());
        
        Button exportButton = createStyledButton("Export", "#27ae60", "export-icon.png");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        Button invoiceButton = createStyledButton("New Invoice", "#e74c3c", "invoice-icon.png");
        invoiceButton.setOnAction(e -> createNewInvoice());
        
        controlPanel.getChildren().addAll(
            searchField,
            new Separator(Orientation.VERTICAL),
            customerLabel, customerComboBox,
            periodLabel, periodComboBox,
            dateBox,
            new Separator(Orientation.VERTICAL),
            refreshButton, exportButton, invoiceButton
        );
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        controlPanel.setEffect(shadow);
        
        return controlPanel;
    }
    
    private GridPane createSummaryDashboard() {
        GridPane dashboard = new GridPane();
        dashboard.setHgap(20);
        dashboard.setVgap(20);
        dashboard.setPadding(new Insets(15));
        
        // Total Revenue Card
        VBox revenueCard = createDashboardCard("Total Revenue", "$0.00", "#3498db", 
                                              "revenue-icon.png", true);
        totalRevenueLabel = (Label) revenueCard.lookup(".value-label");
        
        // Total Loads Card
        VBox loadsCard = createDashboardCard("Total Loads", "0", "#9b59b6", 
                                           "loads-icon.png", false);
        totalLoadsLabel = (Label) loadsCard.lookup(".value-label");
        
        // Average Revenue Card
        VBox avgCard = createDashboardCard("Avg Revenue/Load", "$0.00", "#f39c12", 
                                         "average-icon.png", true);
        avgRevenuePerLoadLabel = (Label) avgCard.lookup(".value-label");
        
        // Outstanding Card
        VBox outstandingCard = createDashboardCard("Outstanding", "$0.00", "#e74c3c", 
                                                 "outstanding-icon.png", true);
        outstandingLabel = (Label) outstandingCard.lookup(".value-label");
        
        dashboard.add(revenueCard, 0, 0);
        dashboard.add(loadsCard, 1, 0);
        dashboard.add(avgCard, 2, 0);
        dashboard.add(outstandingCard, 3, 0);
        
        // Make cards responsive
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(25);
        dashboard.getColumnConstraints().addAll(col, col, col, col);
        
        return dashboard;
    }
    
    private VBox createDashboardCard(String title, String value, String color, 
                                   String iconPath, boolean isCurrency) {
        VBox card = new VBox(10);
        card.getStyleClass().add("dashboard-card");
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefHeight(120);
        
        // Header with icon
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER);
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(30);
            icon.setFitWidth(30);
            icon.setPreserveRatio(true);
            header.getChildren().add(icon);
        } catch (Exception e) {
            // Icon not found
        }
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        titleLabel.setTextFill(Color.GRAY);
        header.getChildren().add(titleLabel);
        
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        valueLabel.setTextFill(Color.web(color));
        
        // Trend indicator
        HBox trendBox = new HBox(5);
        trendBox.setAlignment(Pos.CENTER);
        Label trendIcon = new Label("▲");
        trendIcon.setTextFill(Color.GREEN);
        Label trendValue = new Label("+12.5%");
        trendValue.setFont(Font.font("Arial", 12));
        trendValue.setTextFill(Color.GREEN);
        trendBox.getChildren().addAll(trendIcon, trendValue);
        
        card.getChildren().addAll(header, valueLabel, trendBox);
        
        // Add hover effect
        addCardHoverEffect(card);
        
        // Add shadow
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
    }
    
    private TabPane createContentTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");
        
        // Revenue Details Tab
        Tab detailsTab = new Tab("Revenue Details");
        detailsTab.setClosable(false);
        detailsTab.setContent(createRevenueTableSection());
        
        // Analytics Tab
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setClosable(false);
        analyticsTab.setContent(createAnalyticsSection());
        
        // Reports Tab
        Tab reportsTab = new Tab("Reports");
        reportsTab.setClosable(false);
        reportsTab.setContent(createReportsSection());
        
        tabPane.getTabs().addAll(detailsTab, analyticsTab, reportsTab);
        
        return tabPane;
    }
    
    private VBox createRevenueTableSection() {
        VBox tableSection = new VBox(15);
        tableSection.setPadding(new Insets(15));
        
        // Table Controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        
        Button addButton = new Button("Add Revenue");
        addButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        
        Button editButton = new Button("Edit");
        Button deleteButton = new Button("Delete");
        
        tableControls.getChildren().addAll(addButton, editButton, deleteButton);
        
        // Revenue Table
        revenueTable = new TableView<>();
        revenueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        revenueTable.setPrefHeight(400);
        
        // Create columns
        TableColumn<RevenueEntry, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        invoiceCol.setMinWidth(100);
        
        TableColumn<RevenueEntry, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setCellFactory(column -> new TableCell<RevenueEntry, LocalDate>() {
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
        
        TableColumn<RevenueEntry, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(new PropertyValueFactory<>("customer"));
        customerCol.setMinWidth(150);
        
        TableColumn<RevenueEntry, String> loadCol = new TableColumn<>("Load ID");
        loadCol.setCellValueFactory(new PropertyValueFactory<>("loadId"));
        
        TableColumn<RevenueEntry, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setCellFactory(column -> new TableCell<RevenueEntry, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<RevenueEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<RevenueEntry, String>() {
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
        
        TableColumn<RevenueEntry, LocalDate> dueDateCol = new TableColumn<>("Due Date");
        dueDateCol.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        dueDateCol.setCellFactory(column -> new TableCell<RevenueEntry, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATE_FORMAT));
                    if (date.isBefore(LocalDate.now()) && 
                        "Pending".equals(getTableRow().getItem().getStatus())) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.BLACK);
                        setStyle("");
                    }
                }
            }
        });
        
        revenueTable.getColumns().addAll(invoiceCol, dateCol, customerCol, loadCol, 
                                        amountCol, statusCol, dueDateCol);
        
        // Enable row selection
        revenueTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Add context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewItem = new MenuItem("View Details");
        MenuItem editItem = new MenuItem("Edit");
        MenuItem deleteItem = new MenuItem("Delete");
        MenuItem markPaidItem = new MenuItem("Mark as Paid");
        contextMenu.getItems().addAll(viewItem, editItem, deleteItem, 
                                    new SeparatorMenuItem(), markPaidItem);
        revenueTable.setContextMenu(contextMenu);
        
        tableSection.getChildren().addAll(tableControls, revenueTable);
        
        return tableSection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(15));
        
        // Revenue Trend Chart
        VBox trendChartBox = new VBox(10);
        Label trendLabel = new Label("Revenue Trend");
        trendLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Revenue ($)");
        
        trendChart = new AreaChart<>(xAxis, yAxis);
        trendChart.setTitle("Monthly Revenue Trend");
        trendChart.setPrefHeight(300);
        trendChart.setCreateSymbols(true);
        trendChart.setAnimated(true);
        
        trendChartBox.getChildren().addAll(trendLabel, trendChart);
        
        // Customer Revenue Chart
        VBox customerChartBox = new VBox(10);
        Label customerLabel = new Label("Top Customers");
        customerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis customerXAxis = new CategoryAxis();
        NumberAxis customerYAxis = new NumberAxis();
        customerChart = new BarChart<>(customerXAxis, customerYAxis);
        customerChart.setTitle("Revenue by Customer");
        customerChart.setPrefHeight(300);
        customerChart.setAnimated(true);
        
        customerChartBox.getChildren().addAll(customerLabel, customerChart);
        
        // Payment Status Chart
        VBox statusChartBox = new VBox(10);
        Label statusLabel = new Label("Payment Status");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        statusChart = new PieChart();
        statusChart.setTitle("Payment Status Distribution");
        statusChart.setPrefHeight(300);
        statusChart.setAnimated(true);
        statusChart.setLabelsVisible(true);
        
        statusChartBox.getChildren().addAll(statusLabel, statusChart);
        
        // Layout charts in grid
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        chartsGrid.add(trendChartBox, 0, 0, 2, 1);
        chartsGrid.add(customerChartBox, 0, 1);
        chartsGrid.add(statusChartBox, 1, 1);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createReportsSection() {
        VBox reportsSection = new VBox(20);
        reportsSection.setPadding(new Insets(20));
        reportsSection.setAlignment(Pos.TOP_CENTER);
        
        Label title = new Label("Revenue Reports");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        GridPane reportsGrid = new GridPane();
        reportsGrid.setHgap(20);
        reportsGrid.setVgap(20);
        reportsGrid.setAlignment(Pos.CENTER);
        
        // Create report cards
        VBox monthlyReport = createReportCard("Monthly Revenue Report", 
                                            "Generate detailed monthly revenue reports", 
                                            "monthly-icon.png");
        VBox quarterlyReport = createReportCard("Quarterly Revenue Report", 
                                              "Generate quarterly revenue analysis", 
                                              "quarterly-icon.png");
        VBox annualReport = createReportCard("Annual Revenue Report", 
                                           "Generate comprehensive annual reports", 
                                           "annual-icon.png");
        VBox customerReport = createReportCard("Customer Revenue Report", 
                                             "Analyze revenue by customer", 
                                             "customer-icon.png");
        VBox agingReport = createReportCard("Aging Report", 
                                          "View outstanding invoices by age", 
                                          "aging-icon.png");
        VBox customReport = createReportCard("Custom Report", 
                                           "Create custom revenue reports", 
                                           "custom-icon.png");
        
        reportsGrid.add(monthlyReport, 0, 0);
        reportsGrid.add(quarterlyReport, 1, 0);
        reportsGrid.add(annualReport, 2, 0);
        reportsGrid.add(customerReport, 0, 1);
        reportsGrid.add(agingReport, 1, 1);
        reportsGrid.add(customReport, 2, 1);
        
        reportsSection.getChildren().addAll(title, reportsGrid);
        
        return reportsSection;
    }
    
    private VBox createReportCard(String title, String description, String iconPath) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(250, 200);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(50);
            icon.setFitWidth(50);
            icon.setPreserveRatio(true);
            card.getChildren().add(icon);
        } catch (Exception e) {
            // Icon not found
        }
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setWrapText(true);
        titleLabel.setAlignment(Pos.CENTER);
        
        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Arial", 12));
        descLabel.setTextFill(Color.GRAY);
        descLabel.setWrapText(true);
        descLabel.setAlignment(Pos.CENTER);
        
        Button generateButton = new Button("Generate");
        generateButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        generateButton.setOnAction(e -> generateReport(title));
        
        card.getChildren().addAll(titleLabel, descLabel, generateButton);
        
        // Add hover effect
        addCardHoverEffect(card);
        
        // Add shadow
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        return card;
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
        
        // Add hover effect
        button.setOnMouseEntered(e -> button.setOpacity(0.8));
        button.setOnMouseExited(e -> button.setOpacity(1.0));
        
        return button;
    }
    
    private void addCardHoverEffect(Node card) {
        card.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });
        
        card.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1);
            st.setToY(1);
            st.play();
        });
    }
    
    private String getStatusStyle(String status) {
        switch (status) {
            case "Paid":
                return "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Pending":
                return "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Overdue":
                return "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 15;";
            default:
                return "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 15;";
        }
    }
    
    private void updateDateRange() {
        String period = periodComboBox.getValue();
        LocalDate now = LocalDate.now();
        
        switch (period) {
            case "This Month":
                startDatePicker.setValue(now.withDayOfMonth(1));
                endDatePicker.setValue(now);
                break;
            case "Last Month":
                LocalDate lastMonth = now.minusMonths(1);
                startDatePicker.setValue(lastMonth.withDayOfMonth(1));
                endDatePicker.setValue(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
                break;
            case "This Quarter":
                int quarter = (now.getMonthValue() - 1) / 3;
                startDatePicker.setValue(now.withMonth(quarter * 3 + 1).withDayOfMonth(1));
                endDatePicker.setValue(now);
                break;
            case "This Year":
                startDatePicker.setValue(now.withDayOfYear(1));
                endDatePicker.setValue(now);
                break;
        }
    }
    
    private void loadRevenueData() {
        loadingIndicator.setVisible(true);
        
        Task<List<RevenueEntry>> task = new Task<List<RevenueEntry>>() {
            @Override
            protected List<RevenueEntry> call() throws Exception {
                Thread.sleep(1000); // Simulate loading
                return generateSampleData();
            }
        };
        
        task.setOnSucceeded(e -> {
            List<RevenueEntry> data = task.getValue();
            updateTable(data);
            updateSummaryCards(data);
            updateCharts(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.ERROR, "Error", "Failed to load revenue data.");
        });
        
        new Thread(task).start();
    }
    
    private void updateTable(List<RevenueEntry> data) {
        ObservableList<RevenueEntry> tableData = FXCollections.observableArrayList(data);
        revenueTable.setItems(tableData);
        
        // Apply search filter
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isEmpty()) {
                revenueTable.setItems(tableData);
            } else {
                ObservableList<RevenueEntry> filtered = tableData.filtered(entry ->
                    entry.getInvoiceNumber().toLowerCase().contains(newText.toLowerCase()) ||
                    entry.getCustomer().toLowerCase().contains(newText.toLowerCase()) ||
                    entry.getLoadId().toLowerCase().contains(newText.toLowerCase())
                );
                revenueTable.setItems(filtered);
            }
        });
    }
    
    private void updateSummaryCards(List<RevenueEntry> data) {
        double totalRevenue = data.stream().mapToDouble(RevenueEntry::getAmount).sum();
        int totalLoads = data.size();
        double avgRevenue = totalLoads > 0 ? totalRevenue / totalLoads : 0;
        double outstanding = data.stream()
            .filter(e -> !"Paid".equals(e.getStatus()))
            .mapToDouble(RevenueEntry::getAmount)
            .sum();
        
        animateValue(totalRevenueLabel, CURRENCY_FORMAT.format(totalRevenue));
        animateValue(totalLoadsLabel, String.format("%,d", totalLoads));
        animateValue(avgRevenuePerLoadLabel, CURRENCY_FORMAT.format(avgRevenue));
        animateValue(outstandingLabel, CURRENCY_FORMAT.format(outstanding));
    }
    
    private void updateCharts(List<RevenueEntry> data) {
        // Update trend chart
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue");
        
        Map<String, Double> monthlyRevenue = data.stream()
            .collect(Collectors.groupingBy(
                entry -> entry.getDate().format(DateTimeFormatter.ofPattern("MMM yyyy")),
                Collectors.summingDouble(RevenueEntry::getAmount)
            ));
        
        monthlyRevenue.forEach((month, revenue) -> {
            series.getData().add(new XYChart.Data<>(month, revenue));
        });
        
        trendChart.getData().clear();
        trendChart.getData().add(series);
        
        // Update customer chart
        XYChart.Series<String, Number> customerSeries = new XYChart.Series<>();
        Map<String, Double> customerRevenue = data.stream()
            .collect(Collectors.groupingBy(
                RevenueEntry::getCustomer,
                Collectors.summingDouble(RevenueEntry::getAmount)
            ));
        
        customerRevenue.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                customerSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            });
        
        customerChart.getData().clear();
        customerChart.getData().add(customerSeries);
        customerChart.setLegendVisible(false);
        
        // Update status chart
        Map<String, Long> statusCount = data.stream()
            .collect(Collectors.groupingBy(RevenueEntry::getStatus, Collectors.counting()));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        statusCount.forEach((status, count) -> {
            pieData.add(new PieChart.Data(status + " (" + count + ")", count));
        });
		
        // Add tooltips to pie chart
        statusChart.setData(pieData);
        pieData.forEach(data1 -> {
            Tooltip tooltip = new Tooltip(String.format("%s: %.0f (%.1f%%)", 
                data1.getName(), data1.getPieValue(), 
                (data1.getPieValue() / data.size()) * 100));
            Tooltip.install(data1.getNode(), tooltip);
        });
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
        fileChooser.setInitialFileName("revenue_report_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // Export logic would go here
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Revenue data exported to Excel successfully!");
        }
    }
    
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("revenue_report_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // PDF export logic would go here
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Revenue data exported to PDF successfully!");
        }
    }
    
    private void exportToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("revenue_report_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // CSV export logic would go here
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Revenue data exported to CSV successfully!");
        }
    }
    
    private void createNewInvoice() {
        // Create invoice dialog
        Dialog<RevenueEntry> dialog = new Dialog<>();
        dialog.setTitle("New Invoice");
        dialog.setHeaderText("Create New Revenue Invoice");
        
        // Set dialog buttons
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);
        
        // Create form fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField invoiceField = new TextField();
        invoiceField.setPromptText("INV-" + String.format("%06d", new Random().nextInt(999999)));
        
        ComboBox<String> customerField = new ComboBox<>(customerComboBox.getItems());
        customerField.setPromptText("Select Customer");
        customerField.setPrefWidth(200);
        
        TextField loadIdField = new TextField();
        loadIdField.setPromptText("Load ID");
        
        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        
        DatePicker invoiceDatePicker = new DatePicker(LocalDate.now());
        DatePicker dueDatePicker = new DatePicker(LocalDate.now().plusDays(30));
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Invoice Number:"), 0, 0);
        grid.add(invoiceField, 1, 0);
        grid.add(new Label("Customer:"), 0, 1);
        grid.add(customerField, 1, 1);
        grid.add(new Label("Load ID:"), 0, 2);
        grid.add(loadIdField, 1, 2);
        grid.add(new Label("Amount:"), 0, 3);
        grid.add(amountField, 1, 3);
        grid.add(new Label("Invoice Date:"), 0, 4);
        grid.add(invoiceDatePicker, 1, 4);
        grid.add(new Label("Due Date:"), 0, 5);
        grid.add(dueDatePicker, 1, 5);
        grid.add(new Label("Notes:"), 0, 6);
        grid.add(notesArea, 1, 6);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validate amount field
        amountField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*\\.?\\d*")) {
                amountField.setText(oldText);
            }
        });
        
        // Enable/Disable create button
        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);
        
        customerField.valueProperty().addListener((obs, oldVal, newVal) -> {
            createButton.setDisable(newVal == null || amountField.getText().isEmpty());
        });
        
        amountField.textProperty().addListener((obs, oldVal, newVal) -> {
            createButton.setDisable(newVal.isEmpty() || customerField.getValue() == null);
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                RevenueEntry entry = new RevenueEntry();
                entry.setInvoiceNumber(invoiceField.getText());
                entry.setCustomer(customerField.getValue());
                entry.setLoadId(loadIdField.getText());
                entry.setAmount(Double.parseDouble(amountField.getText()));
                entry.setDate(invoiceDatePicker.getValue());
                entry.setDueDate(dueDatePicker.getValue());
                entry.setStatus("Pending");
                return entry;
            }
            return null;
        });
        
        Optional<RevenueEntry> result = dialog.showAndWait();
        result.ifPresent(entry -> {
            revenueTable.getItems().add(entry);
            showAlert(AlertType.INFORMATION, "Invoice Created", 
                     "Invoice " + entry.getInvoiceNumber() + " created successfully!");
            loadRevenueData(); // Refresh data
        });
    }
    
    private void generateReport(String reportType) {
        // Show loading
        loadingIndicator.setVisible(true);
        
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(2000); // Simulate report generation
                return null;
            }
        };
        
        task.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.INFORMATION, "Report Generated", 
                     reportType + " has been generated successfully!");
        });
        
        new Thread(task).start();
    }
    
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void initializeSampleData() {
        // Initialize customer list
        ObservableList<String> customers = FXCollections.observableArrayList(
            "ABC Transport Inc.", "XYZ Logistics", "Global Shipping Co.", 
            "Express Delivery LLC", "Fast Freight Services", "Prime Movers Inc.",
            "Continental Transport", "Pacific Logistics", "Atlantic Shipping",
            "Mountain Express"
        );
        customerComboBox.setItems(customers);
        customerComboBox.getItems().add(0, "All Customers");
    }
    
    private List<RevenueEntry> generateSampleData() {
        List<RevenueEntry> data = new ArrayList<>();
        Random random = new Random();
        String[] customers = {"ABC Transport Inc.", "XYZ Logistics", "Global Shipping Co.", 
                            "Express Delivery LLC", "Fast Freight Services"};
        String[] statuses = {"Paid", "Pending", "Overdue"};
        
        for (int i = 0; i < 50; i++) {
            RevenueEntry entry = new RevenueEntry();
            entry.setInvoiceNumber("INV-" + String.format("%06d", 1000 + i));
            entry.setDate(LocalDate.now().minusDays(random.nextInt(90)));
            entry.setCustomer(customers[random.nextInt(customers.length)]);
            entry.setLoadId("LD" + String.format("%04d", random.nextInt(10000)));
            entry.setAmount(1000 + random.nextDouble() * 9000);
            entry.setStatus(statuses[random.nextInt(statuses.length)]);
            entry.setDueDate(entry.getDate().plusDays(30));
            data.add(entry);
        }
        
        return data;
    }
    
    // Inner class for Revenue Entry
    public static class RevenueEntry {
        private final StringProperty invoiceNumber = new SimpleStringProperty();
        private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
        private final StringProperty customer = new SimpleStringProperty();
        private final StringProperty loadId = new SimpleStringProperty();
        private final DoubleProperty amount = new SimpleDoubleProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>();
        
        // Getters and setters
        public String getInvoiceNumber() { return invoiceNumber.get(); }
        public void setInvoiceNumber(String value) { invoiceNumber.set(value); }
        public StringProperty invoiceNumberProperty() { return invoiceNumber; }
        
        public LocalDate getDate() { return date.get(); }
        public void setDate(LocalDate value) { date.set(value); }
        public ObjectProperty<LocalDate> dateProperty() { return date; }
        
        public String getCustomer() { return customer.get(); }
        public void setCustomer(String value) { customer.set(value); }
        public StringProperty customerProperty() { return customer; }
        
        public String getLoadId() { return loadId.get(); }
        public void setLoadId(String value) { loadId.set(value); }
        public StringProperty loadIdProperty() { return loadId; }
        
        public double getAmount() { return amount.get(); }
        public void setAmount(double value) { amount.set(value); }
        public DoubleProperty amountProperty() { return amount; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
        
        public LocalDate getDueDate() { return dueDate.get(); }
        public void setDueDate(LocalDate value) { dueDate.set(value); }
        public ObjectProperty<LocalDate> dueDateProperty() { return dueDate; }
    }
}
    