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
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.payroll.ModernButtonStyles;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.Load.Status;
import javafx.stage.FileChooser;
import com.company.payroll.util.WindowAware;
import java.io.File;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import com.company.payroll.revenue.RevenuePDFExporter;
import com.company.payroll.export.PDFExporter;
import java.io.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.geometry.HPos;
import java.time.DayOfWeek;

/**
 * Enhanced Revenue Management Tab with comprehensive analytics and reporting
 */
public class RevenueTab extends Tab implements WindowAware {
    // UI Components
    private TableView<RevenueEntry> revenueTable;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> customerComboBox;
    private ComboBox<String> periodComboBox;
    private TextField searchField;
    
    // Summary Labels
    private Label totalRevenueLabel;
    private Label totalLoadsLabel;
    private Label avgRevenuePerLoadLabel;
    private Label outstandingLabel;
    private Label collectedLabel;
    private Label pendingLabel;
    
    // Charts
    private LineChart<String, Number> revenueChart;
    private BarChart<String, Number> customerChart;
    private PieChart statusChart;
    private AreaChart<String, Number> trendChart;
    
    // Buttons
    private Button refreshBtn;
    private Button exportBtn;
    private Button printPdfBtn;
    
    // Loading and data
    private ProgressIndicator loadingIndicator;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LoadDAO loadDAO = new LoadDAO();
    private ObservableList<RevenueEntry> allRevenueData = FXCollections.observableArrayList();
    private ObservableList<RevenueEntry> filteredData = FXCollections.observableArrayList();
    
    private String companyName = "Your Company Name";
    private Image companyLogo = null;
    
    private static final String CONFIG_FILE = "revenue_config.dat";
    
    // Main container
    private VBox mainContainer;
    
    // DataChangeListener for cross-tab refresh
    public interface DataChangeListener {
        void onDataChanged();
    }
    private DataChangeListener dataChangeListener;
    
    public void setDataChangeListener(DataChangeListener listener) {
        this.dataChangeListener = listener;
    }
    
    public void onDataChanged() {
        loadRevenueData();
    }
    
    public RevenueTab() {
        setText("Revenue");
        setClosable(false);
        loadConfig();
        
        // Initialize UI
        initializeUI();
        setupEventHandlers();
        
        // Set initial date range based on selected period
        updateDateRange();
        
        // Load initial data
        initializeCustomers();
        loadRevenueData();
    }
    
    private void initializeUI() {
        mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.TOP_CENTER);
        mainContainer.setStyle("-fx-background-color: #F5F5F5;");
        
        // Header
        VBox headerBox = createHeader();
        
        // Date Range Controls
        HBox controlPanel = createControlPanel();
        
        // Summary Dashboard
        GridPane summaryDashboard = createSummaryDashboard();
        
        // Main Content Tabs
        TabPane contentTabs = createContentTabs();
        
        // Details Container (initially hidden)
        
        // Loading Indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        StackPane loadingPane = new StackPane(loadingIndicator);
        loadingPane.setAlignment(Pos.CENTER);
        
        mainContainer.getChildren().addAll(
            headerBox,
            controlPanel,
            summaryDashboard,
            contentTabs,
            loadingPane
        );
        
        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        setContent(scrollPane);
    }
    
    private VBox createHeader() {
        VBox headerBox = new VBox(5);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(20));
        headerBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label titleLabel = new Label("Revenue Analytics Dashboard");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        titleLabel.setTextFill(Color.web("#2E7D32"));
        
        Label subtitleLabel = new Label("Comprehensive Revenue Tracking & Analysis");
        subtitleLabel.setFont(Font.font("Arial", 16));
        subtitleLabel.setTextFill(Color.web("#666666"));
        
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);
        return headerBox;
    }
    
    private HBox createControlPanel() {
        HBox controlPanel = new HBox(15);
        controlPanel.setAlignment(Pos.CENTER);
        controlPanel.setPadding(new Insets(15));
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                             "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        // Search Field
        searchField = new TextField();
        searchField.setPromptText("Search by invoice, customer, or load...");
        searchField.setPrefWidth(250);
        searchField.setStyle("-fx-font-size: 14px;");
        
        // Customer Filter
        Label customerLabel = new Label("Customer:");
        customerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        customerComboBox = new ComboBox<>();
        customerComboBox.setPrefWidth(200);
        customerComboBox.setPromptText("All Customers");
        
        // Period Filter
        Label periodLabel = new Label("Period:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        periodComboBox = new ComboBox<>(FXCollections.observableArrayList(
            "This Week", "Last Week", "This Month", "Last Month", 
            "This Quarter", "Last Quarter", "This Year", "Last Year", 
            "Custom Range"
        ));
        periodComboBox.setValue("This Year");
        periodComboBox.setPrefWidth(150);
        
        // Date Range
        HBox dateBox = new HBox(10);
        dateBox.setAlignment(Pos.CENTER_LEFT);
        startDatePicker = new DatePicker(LocalDate.now().withDayOfYear(1));
        endDatePicker = new DatePicker(LocalDate.now());
        startDatePicker.setPrefWidth(120);
        endDatePicker.setPrefWidth(120);
        dateBox.getChildren().addAll(new Label("From:"), startDatePicker, new Label("To:"), endDatePicker);
        dateBox.setVisible(false);
        
        periodComboBox.setOnAction(e -> {
            dateBox.setVisible("Custom Range".equals(periodComboBox.getValue()));
            if (!"Custom Range".equals(periodComboBox.getValue())) {
                updateDateRange();
            }
        });
        
        // Modern button styling
        refreshBtn = ModernButtonStyles.createPrimaryButton("🔄 Refresh");
        exportBtn = ModernButtonStyles.createSuccessButton("📊 Export");
        printPdfBtn = ModernButtonStyles.createInfoButton("🖨️ Print PDF");
        
        controlPanel.getChildren().addAll(
            searchField,
            new Separator(Orientation.VERTICAL),
            customerLabel, customerComboBox,
            periodLabel, periodComboBox,
            dateBox,
            new Separator(Orientation.VERTICAL),
            refreshBtn, exportBtn, printPdfBtn
        );
        
        return controlPanel;
    }
    
    private void addButtonHoverEffect(Button button, String normalColor, String hoverColor) {
        String baseStyle = button.getStyle();
        button.setOnMouseEntered(e -> 
            button.setStyle(baseStyle.replace(normalColor, hoverColor))
        );
        button.setOnMouseExited(e -> 
            button.setStyle(baseStyle)
        );
    }
    
    private GridPane createSummaryDashboard() {
        GridPane dashboard = new GridPane();
        dashboard.setHgap(15);
        dashboard.setVgap(15);
        dashboard.setPadding(new Insets(20));
        dashboard.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        // Create enhanced summary cards
        VBox revenueCard = createSummaryCard("Total Revenue", "$0.00", "#2E7D32", "💰");
        totalRevenueLabel = (Label) revenueCard.lookup(".value-label");
        
        VBox loadsCard = createSummaryCard("Total Loads", "0", "#1976D2", "🚚");
        totalLoadsLabel = (Label) loadsCard.lookup(".value-label");
        
        VBox avgCard = createSummaryCard("Avg per Load", "$0.00", "#FF6F00", "📊");
        avgRevenuePerLoadLabel = (Label) avgCard.lookup(".value-label");
        
        VBox collectedCard = createSummaryCard("Collected", "$0.00", "#388E3C", "✓");
        collectedLabel = (Label) collectedCard.lookup(".value-label");
        
        VBox pendingCard = createSummaryCard("Pending", "$0.00", "#FFA000", "⏳");
        pendingLabel = (Label) pendingCard.lookup(".value-label");
        
        VBox outstandingCard = createSummaryCard("Outstanding", "$0.00", "#D32F2F", "⚠");
        outstandingLabel = (Label) outstandingCard.lookup(".value-label");
        
        // Add cards to dashboard
        dashboard.add(revenueCard, 0, 0);
        dashboard.add(loadsCard, 1, 0);
        dashboard.add(avgCard, 2, 0);
        dashboard.add(collectedCard, 0, 1);
        dashboard.add(pendingCard, 1, 1);
        dashboard.add(outstandingCard, 2, 1);
        
        // Configure column constraints
        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(33.33);
            col.setHgrow(Priority.ALWAYS);
            dashboard.getColumnConstraints().add(col);
        }
        
        return dashboard;
    }
    
    private VBox createSummaryCard(String title, String value, String color, String emoji) {
        VBox card = new VBox(8);
        card.getStyleClass().add("summary-card");
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; " +
                     "-fx-border-color: " + color + "; -fx-border-width: 0 0 0 4px; " +
                     "-fx-border-radius: 8px;");
        card.setPrefHeight(100);
        
        // Header with emoji
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER);
        
        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 24px;");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        titleLabel.setTextFill(Color.web("#666666"));
        
        header.getChildren().addAll(emojiLabel, titleLabel);
        
        // Value
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        valueLabel.setTextFill(Color.web(color));
        
        // Trend (placeholder)
        Label trendLabel = new Label("");
        trendLabel.setFont(Font.font("Arial", 12));
        trendLabel.setTextFill(Color.web("#666666"));
        
        card.getChildren().addAll(header, valueLabel, trendLabel);
        
        // Add hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle(card.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 15, 0, 0, 5);");
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(1.02);
            st.setToY(1.02);
            st.play();
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle(card.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 15, 0, 0, 5);", ""));
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        return card;
    }
    
    private TabPane createContentTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");
        tabPane.setPrefHeight(500);
        
        // Revenue Table Tab
        Tab tableTab = new Tab("Revenue Details");
        tableTab.setClosable(false);
        tableTab.setContent(createRevenueTableSection());
        
        // Charts Tab
        Tab chartsTab = new Tab("Visual Analytics");
        chartsTab.setClosable(false);
        chartsTab.setContent(createChartsSection());
        
        // Summary Tab
        Tab summaryTab = new Tab("Period Summary");
        summaryTab.setClosable(false);
        summaryTab.setContent(createPeriodSummarySection());
        
        tabPane.getTabs().addAll(tableTab, chartsTab, summaryTab);
        return tabPane;
    }
    
    private VBox createRevenueTableSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        
        // Create table
        revenueTable = new TableView<>();
        revenueTable.setPlaceholder(new Label("No revenue data available"));
        setupRevenueTable();
        
        // Table controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        tableControls.setPadding(new Insets(5));
        
        Label recordsLabel = new Label("0 records");
        recordsLabel.setFont(Font.font("Arial", 12));
        recordsLabel.setTextFill(Color.web("#666666"));
        
        tableControls.getChildren().add(recordsLabel);
        
        section.getChildren().addAll(revenueTable, tableControls);
        VBox.setVgrow(revenueTable, Priority.ALWAYS);
        
        return section;
    }
    
    private void setupRevenueTable() {
        // Invoice Number Column
        TableColumn<RevenueEntry, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        invoiceCol.setPrefWidth(100);
        
        // Date Column
        TableColumn<RevenueEntry, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(100);
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
        
        // Customer Column
        TableColumn<RevenueEntry, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(new PropertyValueFactory<>("customer"));
        customerCol.setPrefWidth(200);
        
        // Load ID Column
        TableColumn<RevenueEntry, String> loadIdCol = new TableColumn<>("Load ID");
        loadIdCol.setCellValueFactory(new PropertyValueFactory<>("loadId"));
        loadIdCol.setPrefWidth(80);
        
        // Amount Column
        TableColumn<RevenueEntry, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(120);
        amountCol.setCellFactory(column -> new TableCell<RevenueEntry, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
                }
            }
        });
        
        // Status Column
        TableColumn<RevenueEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(column -> new TableCell<RevenueEntry, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    String color = switch (status) {
                        case "PAID" -> "#4CAF50";
                        case "PENDING" -> "#FF9800";
                        case "OVERDUE" -> "#F44336";
                        default -> "#666666";
                    };
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        });
        
        // Notes Column
        TableColumn<RevenueEntry, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setPrefWidth(150);
        
        @SuppressWarnings("unchecked")
        TableColumn<RevenueEntry, ?>[] columns = new TableColumn[] {
            invoiceCol, dateCol, customerCol, loadIdCol, 
            amountCol, statusCol, notesCol
        };
        revenueTable.getColumns().addAll(columns);
        
        // Enable sorting
        revenueTable.getSortOrder().add(dateCol);
        dateCol.setSortType(TableColumn.SortType.DESCENDING);
        
        // Row factory for alternating colors
        revenueTable.setRowFactory(tv -> {
            TableRow<RevenueEntry> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #F8F8F8;");
                    } else {
                        row.setStyle("-fx-background-color: white;");
                    }
                }
            });
            return row;
        });
    }
    
    private VBox createChartsSection() {
        VBox section = new VBox(20);
        section.setPadding(new Insets(20));
        section.setStyle("-fx-background-color: #F5F5F5;");
        
        // Create 2x2 grid for charts
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Revenue Trend Chart
        revenueChart = createRevenueLineChart();
        VBox revenueChartBox = createChartBox("Revenue Trend", revenueChart);
        
        // Customer Distribution Chart
        customerChart = createCustomerBarChart();
        VBox customerChartBox = createChartBox("Top Customers", customerChart);
        
        // Status Distribution Chart
        statusChart = createStatusPieChart();
        VBox statusChartBox = createChartBox("Payment Status", statusChart);
        
        // Monthly Comparison Chart
        trendChart = createMonthlyTrendChart();
        VBox trendChartBox = createChartBox("Monthly Comparison", trendChart);
        
        chartsGrid.add(revenueChartBox, 0, 0);
        chartsGrid.add(customerChartBox, 1, 0);
        chartsGrid.add(statusChartBox, 0, 1);
        chartsGrid.add(trendChartBox, 1, 1);
        
        // Configure grid constraints
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            col.setHgrow(Priority.ALWAYS);
            chartsGrid.getColumnConstraints().add(col);
            
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(50);
            row.setVgrow(Priority.ALWAYS);
            chartsGrid.getRowConstraints().add(row);
        }
        
        section.getChildren().add(chartsGrid);
        VBox.setVgrow(chartsGrid, Priority.ALWAYS);
        
        return section;
    }
    
    private VBox createChartBox(String title, Chart chart) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#333333"));
        
        chart.setPrefHeight(250);
        chart.setAnimated(true);
        
        box.getChildren().addAll(titleLabel, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        
        return box;
    }
    
    private LineChart<String, Number> createRevenueLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Date");
        yAxis.setLabel("Revenue ($)");
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        
        return chart;
    }
    
    private BarChart<String, Number> createCustomerBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Customer");
        yAxis.setLabel("Revenue ($)");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        
        return chart;
    }
    
    private PieChart createStatusPieChart() {
        PieChart chart = new PieChart();
        chart.setLegendSide(Side.RIGHT);
        chart.setLabelsVisible(true);
        
        return chart;
    }
    
    private AreaChart<String, Number> createMonthlyTrendChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Month");
        yAxis.setLabel("Revenue ($)");
        
        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(false);
        
        return chart;
    }
    
    private VBox createPeriodSummarySection() {
        VBox section = new VBox(20);
        section.setPadding(new Insets(20));
        section.setAlignment(Pos.TOP_CENTER);
        
        // Period Summary Card
        VBox summaryCard = new VBox(15);
        summaryCard.setPadding(new Insets(20));
        summaryCard.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        summaryCard.setMaxWidth(600);
        
        Label summaryTitle = new Label("Period Summary Report");
        summaryTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        summaryTitle.setTextFill(Color.web("#333333"));
        
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(30);
        summaryGrid.setVgap(15);
        summaryGrid.setAlignment(Pos.CENTER);
        
        // This will be populated dynamically
        summaryGrid.setId("periodSummaryGrid");
        
        summaryCard.getChildren().addAll(summaryTitle, new Separator(), summaryGrid);
        
        // Export Options
        HBox exportOptions = new HBox(15);
        exportOptions.setAlignment(Pos.CENTER);
        exportOptions.setPadding(new Insets(20));
        
        Button exportSummaryBtn = new Button("Export Summary");
        exportSummaryBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                                 "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 20;");
        
        Button printSummaryBtn = new Button("Print Summary");
        printSummaryBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 20;");
        
        exportOptions.getChildren().addAll(exportSummaryBtn, printSummaryBtn);
        
        section.getChildren().addAll(summaryCard, exportOptions);
        
        return section;
    }
    
    
    private void setupEventHandlers() {
        refreshBtn.setOnAction(e -> loadRevenueData());
        exportBtn.setOnAction(e -> exportToCSV());
        printPdfBtn.setOnAction(e -> printToPDF());
        
        searchField.textProperty().addListener((obs, oldText, newText) -> filterData());
        customerComboBox.setOnAction(e -> filterData());
        
        startDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null && endDatePicker.getValue() != null) {
                loadRevenueData();
            }
        });
        
        endDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null && startDatePicker.getValue() != null) {
                loadRevenueData();
            }
        });
    }
    
    
    private void updateDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate start, end;
        
        switch (periodComboBox.getValue()) {
            case "This Week":
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                break;
            case "Last Week":
                start = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = today.minusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                break;
            case "This Month":
                start = today.withDayOfMonth(1);
                end = today.withDayOfMonth(today.lengthOfMonth());
                break;
            case "Last Month":
                start = today.minusMonths(1).withDayOfMonth(1);
                end = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
                break;
            case "This Quarter":
                int quarter = (today.getMonthValue() - 1) / 3;
                start = today.withMonth(quarter * 3 + 1).withDayOfMonth(1);
                end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());
                break;
            case "Last Quarter":
                int lastQuarter = ((today.getMonthValue() - 1) / 3) - 1;
                if (lastQuarter < 0) lastQuarter = 3;
                start = today.withMonth(lastQuarter * 3 + 1).withDayOfMonth(1);
                end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());
                break;
            case "This Year":
                start = today.withDayOfYear(1);  // January 1st of current year
                end = today;                     // Today's date
                break;
            case "Last Year":
                start = today.minusYears(1).withDayOfYear(1);
                end = today.minusYears(1).withDayOfYear(today.minusYears(1).lengthOfYear());
                break;
            default:
                return;
        }
        
        startDatePicker.setValue(start);
        endDatePicker.setValue(end);
    }
    
    private void loadRevenueData() {
        showLoading(true);
        
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                LocalDate start = startDatePicker.getValue();
                LocalDate end = endDatePicker.getValue();
                
                // Load data from LoadDAO
                List<Load> loads = loadDAO.getByDateRange(start, end);
                
                // Convert to RevenueEntry objects
                allRevenueData.clear();
                for (Load load : loads) {
                    if (load.getGrossAmount() > 0) {
                        RevenueEntry entry = new RevenueEntry(
                            "INV-" + load.getId(),
                            load.getPickUpDate() != null ? load.getPickUpDate() : load.getDeliveryDate(),
                            load.getCustomer(),
                            String.valueOf(load.getId()),
                            load.getGrossAmount(),
                            mapLoadStatusToRevenue(load.getStatus()),
                            load.getNotes()
                        );
                        allRevenueData.add(entry);
                    }
                }
                
                return null;
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            showLoading(false);
            filterData();
            updateSummary();
            updateCharts();
            updatePeriodSummary();
        });
        
        loadTask.setOnFailed(e -> {
            showLoading(false);
            showError("Failed to load revenue data", loadTask.getException().getMessage());
        });
        
        new Thread(loadTask).start();
    }
    
    private String mapLoadStatusToRevenue(Load.Status loadStatus) {
        return switch (loadStatus) {
            case PAID -> "PAID";
            case DELIVERED -> "DELIVERED";
            case IN_TRANSIT, ASSIGNED, BOOKED -> "PENDING";
            case CANCELLED -> "CANCELLED";
            default -> "PENDING";
        };
    }
    
    private void filterData() {
        filteredData.clear();
        
        String searchText = searchField.getText().toLowerCase();
        String selectedCustomer = customerComboBox.getValue();
        
        for (RevenueEntry entry : allRevenueData) {
            boolean matchesSearch = searchText.isEmpty() ||
                entry.getInvoiceNumber().toLowerCase().contains(searchText) ||
                entry.getCustomer().toLowerCase().contains(searchText) ||
                entry.getLoadId().toLowerCase().contains(searchText);
            
            boolean matchesCustomer = selectedCustomer == null || 
                selectedCustomer.equals("All Customers") ||
                entry.getCustomer().equals(selectedCustomer);
            
            if (matchesSearch && matchesCustomer) {
                filteredData.add(entry);
            }
        }
        
        revenueTable.setItems(filteredData);
        updateSummary();
    }
    
    private void updateSummary() {
        double totalRevenue = 0;
        double collected = 0;
        double pending = 0;
        double outstanding = 0;
        int totalLoads = filteredData.size();
        
        for (RevenueEntry entry : filteredData) {
            totalRevenue += entry.getAmount();
            
            switch (entry.getStatus()) {
                case "PAID" -> collected += entry.getAmount();
                case "PENDING" -> pending += entry.getAmount();
                case "OVERDUE" -> outstanding += entry.getAmount();
            }
        }
        
        double avgPerLoad = totalLoads > 0 ? totalRevenue / totalLoads : 0;
        
        // Update labels with animation
        animateValueLabel(totalRevenueLabel, totalRevenue, true);
        animateValueLabel(totalLoadsLabel, totalLoads, false);
        animateValueLabel(avgRevenuePerLoadLabel, avgPerLoad, true);
        animateValueLabel(collectedLabel, collected, true);
        animateValueLabel(pendingLabel, pending, true);
        animateValueLabel(outstandingLabel, outstanding, true);
    }
    
    private void animateValueLabel(Label label, double newValue, boolean isCurrency) {
        String oldText = label.getText();
        double oldValue = 0;
        
        try {
            oldValue = Double.parseDouble(oldText.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            // Ignore
        }
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, 
                new KeyValue(label.textProperty(), oldText)),
            new KeyFrame(Duration.millis(500), 
                new KeyValue(label.textProperty(), 
                    isCurrency ? CURRENCY_FORMAT.format(newValue) : String.format("%.0f", newValue)))
        );
        timeline.play();
    }
    
    private void updateCharts() {
        updateRevenueLineChart();
        updateCustomerBarChart();
        updateStatusPieChart();
        updateMonthlyTrendChart();
    }
    
    private void updateRevenueLineChart() {
        revenueChart.getData().clear();
        
        // Group by date
        Map<LocalDate, Double> dailyRevenue = new TreeMap<>();
        for (RevenueEntry entry : filteredData) {
            dailyRevenue.merge(entry.getDate(), entry.getAmount(), Double::sum);
        }
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Revenue");
        
        for (Map.Entry<LocalDate, Double> entry : dailyRevenue.entrySet()) {
            series.getData().add(new XYChart.Data<>(
                entry.getKey().format(DateTimeFormatter.ofPattern("MM/dd")), 
                entry.getValue()
            ));
        }
        
        revenueChart.getData().add(series);
    }
    
    private void updateCustomerBarChart() {
        customerChart.getData().clear();
        
        // Group by customer and get top 10
        Map<String, Double> customerRevenue = new HashMap<>();
        for (RevenueEntry entry : filteredData) {
            customerRevenue.merge(entry.getCustomer(), entry.getAmount(), Double::sum);
        }
        
        List<Map.Entry<String, Double>> sortedCustomers = customerRevenue.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> entry : sortedCustomers) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        customerChart.getData().add(series);
    }
    
    private void updateStatusPieChart() {
        statusChart.getData().clear();
        
        Map<String, Double> statusRevenue = new HashMap<>();
        for (RevenueEntry entry : filteredData) {
            statusRevenue.merge(entry.getStatus(), entry.getAmount(), Double::sum);
        }
        
        for (Map.Entry<String, Double> entry : statusRevenue.entrySet()) {
            PieChart.Data slice = new PieChart.Data(
                entry.getKey() + " (" + CURRENCY_FORMAT.format(entry.getValue()) + ")", 
                entry.getValue()
            );
            statusChart.getData().add(slice);
        }
    }
    
    private void updateMonthlyTrendChart() {
        trendChart.getData().clear();
        
        // Group by month
        Map<String, Double> monthlyRevenue = new TreeMap<>();
        for (RevenueEntry entry : filteredData) {
            String month = entry.getDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));
            monthlyRevenue.merge(month, entry.getAmount(), Double::sum);
        }
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Monthly Revenue");
        
        for (Map.Entry<String, Double> entry : monthlyRevenue.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        trendChart.getData().add(series);
    }
    
    private void updatePeriodSummary() {
        GridPane summaryGrid = (GridPane) mainContainer.lookup("#periodSummaryGrid");
        if (summaryGrid != null) {
            summaryGrid.getChildren().clear();
            
            // Calculate period metrics
            LocalDate start = startDatePicker.getValue();
            LocalDate end = endDatePicker.getValue();
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            
            double totalRevenue = filteredData.stream()
                .mapToDouble(RevenueEntry::getAmount)
                .sum();
            
            double dailyAverage = totalRevenue / daysBetween;
            
            // Find best day
            Map<LocalDate, Double> dailyTotals = new HashMap<>();
            for (RevenueEntry entry : filteredData) {
                dailyTotals.merge(entry.getDate(), entry.getAmount(), Double::sum);
            }
            
            Map.Entry<LocalDate, Double> bestDay = dailyTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
            
            // Add summary rows
            int row = 0;
            addSummaryRow(summaryGrid, row++, "Period:", 
                start.format(DATE_FORMAT) + " - " + end.format(DATE_FORMAT));
            addSummaryRow(summaryGrid, row++, "Total Days:", String.valueOf(daysBetween));
            addSummaryRow(summaryGrid, row++, "Total Revenue:", CURRENCY_FORMAT.format(totalRevenue));
            addSummaryRow(summaryGrid, row++, "Daily Average:", CURRENCY_FORMAT.format(dailyAverage));
            
            if (bestDay != null) {
                addSummaryRow(summaryGrid, row++, "Best Day:", 
                    bestDay.getKey().format(DATE_FORMAT) + " (" + 
                    CURRENCY_FORMAT.format(bestDay.getValue()) + ")");
            }
            
            addSummaryRow(summaryGrid, row++, "Total Loads:", String.valueOf(filteredData.size()));
            addSummaryRow(summaryGrid, row++, "Unique Customers:", 
                String.valueOf(filteredData.stream()
                    .map(RevenueEntry::getCustomer)
                    .distinct()
                    .count()));
        }
    }
    
    private void addSummaryRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        labelNode.setTextFill(Color.web("#666666"));
        
        Label valueNode = new Label(value);
        valueNode.setFont(Font.font("Arial", 14));
        valueNode.setTextFill(Color.web("#333333"));
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
        GridPane.setHalignment(labelNode, HPos.RIGHT);
        GridPane.setHalignment(valueNode, HPos.LEFT);
    }
    
    
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Revenue Data");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("revenue_report_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                // Write headers
                writer.println("Revenue Report");
                writer.println("Date Range: " + startDatePicker.getValue() + " to " + endDatePicker.getValue());
                writer.println();
                writer.println("Invoice #,Date,Customer,Load ID,Amount,Status,Notes");
                
                // Write data
                for (RevenueEntry entry : filteredData) {
                    writer.printf("%s,%s,%s,%s,%.2f,%s,%s%n",
                        entry.getInvoiceNumber(),
                        entry.getDate().format(DATE_FORMAT),
                        entry.getCustomer(),
                        entry.getLoadId(),
                        entry.getAmount(),
                        entry.getStatus(),
                        entry.getNotes() != null ? entry.getNotes().replace(",", ";") : ""
                    );
                }
                
                // Write summary
                writer.println();
                writer.println("SUMMARY");
                writer.println("Total Revenue," + totalRevenueLabel.getText());
                writer.println("Total Loads," + totalLoadsLabel.getText());
                writer.println("Average per Load," + avgRevenuePerLoadLabel.getText());
                writer.println("Collected," + collectedLabel.getText());
                writer.println("Pending," + pendingLabel.getText());
                writer.println("Outstanding," + outstandingLabel.getText());
                
                showInfo("Revenue data exported successfully!");
            } catch (IOException e) {
                showError("Export Failed", e.getMessage());
            }
        }
    }
    
    private void printToPDF() {
        if (filteredData.isEmpty()) {
            showError("No Data", "No revenue data to print. Please load data first.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Revenue Report PDF");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("revenue_report_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                PDFExporter pdfExporter = new PDFExporter(companyName);
                
                // Generate comprehensive revenue report
                pdfExporter.generateComprehensiveRevenueReport(
                    file,
                    startDatePicker.getValue(),
                    endDatePicker.getValue(),
                    new ArrayList<>(filteredData),
                    totalRevenueLabel.getText(),
                    totalLoadsLabel.getText(),
                    avgRevenuePerLoadLabel.getText(),
                    collectedLabel.getText(),
                    pendingLabel.getText(),
                    outstandingLabel.getText()
                );
                
                showInfo("Revenue report PDF generated successfully!");
            } catch (Exception e) {
                showError("PDF Generation Failed", e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        mainContainer.setDisable(show);
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void initializeCustomers() {
        // Load unique customers from database
        Set<String> customers = new HashSet<>();
        customers.add("All Customers");
        
        try {
            List<Load> allLoads = loadDAO.getAll();
            for (Load load : allLoads) {
                if (load.getCustomer() != null && !load.getCustomer().isEmpty()) {
                    customers.add(load.getCustomer());
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        customerComboBox.getItems().clear();
        customerComboBox.getItems().addAll(customers.stream().sorted().collect(Collectors.toList()));
        customerComboBox.setValue("All Customers");
    }
    
    private void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(configFile));
                companyName = props.getProperty("companyName", "Your Company Name");
                String logoPath = props.getProperty("logoPath", "");
                if (!logoPath.isEmpty()) {
                    companyLogo = new Image(new FileInputStream(logoPath));
                }
            }
        } catch (Exception e) {
            // Use defaults
        }
    }
    
    // Data model classes
    public static class RevenueEntry {
        private final SimpleStringProperty invoiceNumber;
        private final ObjectProperty<LocalDate> date;
        private final SimpleStringProperty customer;
        private final SimpleStringProperty loadId;
        private final SimpleDoubleProperty amount;
        private final SimpleStringProperty status;
        private final SimpleStringProperty notes;
        
        public RevenueEntry(String invoiceNumber, LocalDate date, String customer, 
                          String loadId, double amount, String status, String notes) {
            this.invoiceNumber = new SimpleStringProperty(invoiceNumber);
            this.date = new SimpleObjectProperty<>(date);
            this.customer = new SimpleStringProperty(customer);
            this.loadId = new SimpleStringProperty(loadId);
            this.amount = new SimpleDoubleProperty(amount);
            this.status = new SimpleStringProperty(status);
            this.notes = new SimpleStringProperty(notes);
        }
        
        // Getters
        public String getInvoiceNumber() { return invoiceNumber.get(); }
        public LocalDate getDate() { return date.get(); }
        public String getCustomer() { return customer.get(); }
        public String getLoadId() { return loadId.get(); }
        public double getAmount() { return amount.get(); }
        public String getStatus() { return status.get(); }
        public String getNotes() { return notes.get(); }
        
        // Property getters
        public SimpleStringProperty invoiceNumberProperty() { return invoiceNumber; }
        public ObjectProperty<LocalDate> dateProperty() { return date; }
        public SimpleStringProperty customerProperty() { return customer; }
        public SimpleStringProperty loadIdProperty() { return loadId; }
        public SimpleDoubleProperty amountProperty() { return amount; }
        public SimpleStringProperty statusProperty() { return status; }
        public SimpleStringProperty notesProperty() { return notes; }
    }
    
    public static class WeeklyRevenue {
        private final SimpleStringProperty week;
        private final SimpleIntegerProperty loads;
        private final SimpleDoubleProperty revenue;
        private final SimpleDoubleProperty average;
        
        public WeeklyRevenue(String week, int loads, double revenue) {
            this.week = new SimpleStringProperty(week);
            this.loads = new SimpleIntegerProperty(loads);
            this.revenue = new SimpleDoubleProperty(revenue);
            this.average = new SimpleDoubleProperty(loads > 0 ? revenue / loads : 0);
        }
        
        public void addLoad(double amount) {
            loads.set(loads.get() + 1);
            revenue.set(revenue.get() + amount);
            average.set(revenue.get() / loads.get());
        }
        
        // Getters
        public String getWeek() { return week.get(); }
        public int getLoads() { return loads.get(); }
        public double getRevenue() { return revenue.get(); }
        public double getAverage() { return average.get(); }
        
        // Property getters
        public SimpleStringProperty weekProperty() { return week; }
        public SimpleIntegerProperty loadsProperty() { return loads; }
        public SimpleDoubleProperty revenueProperty() { return revenue; }
        public SimpleDoubleProperty averageProperty() { return average; }
    }
    
    public static class CustomerMetrics {
        private final SimpleStringProperty name;
        private final SimpleIntegerProperty totalLoads;
        private final SimpleDoubleProperty totalRevenue;
        private final SimpleStringProperty percentOfTotal;
        
        public CustomerMetrics(String name) {
            this.name = new SimpleStringProperty(name);
            this.totalLoads = new SimpleIntegerProperty(0);
            this.totalRevenue = new SimpleDoubleProperty(0);
            this.percentOfTotal = new SimpleStringProperty("0%");
        }
        
        public void addLoad(double amount) {
            totalLoads.set(totalLoads.get() + 1);
            totalRevenue.set(totalRevenue.get() + amount);
        }
        
        public void calculatePercentage(double total) {
            if (total > 0) {
                double percent = (totalRevenue.get() / total) * 100;
                percentOfTotal.set(String.format("%.1f%%", percent));
            }
        }
        
        // Getters
        public String getName() { return name.get(); }
        public int getTotalLoads() { return totalLoads.get(); }
        public double getTotalRevenue() { return totalRevenue.get(); }
        public String getPercentOfTotal() { return percentOfTotal.get(); }
        
        // Property getters
        public SimpleStringProperty nameProperty() { return name; }
        public SimpleIntegerProperty totalLoadsProperty() { return totalLoads; }
        public SimpleDoubleProperty totalRevenueProperty() { return totalRevenue; }
        public SimpleStringProperty percentOfTotalProperty() { return percentOfTotal; }
    }
}