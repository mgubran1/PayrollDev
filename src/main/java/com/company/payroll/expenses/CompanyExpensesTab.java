package com.company.payroll.expenses;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.payroll.payroll.ModernButtonStyles;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressIndicator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import com.company.payroll.config.FilterConfig;
import com.company.payroll.config.DocumentManagerConfig;
import com.company.payroll.config.DocumentManagerSettingsDialog;

/**
 * Enhanced Company Expenses Tab for tracking all business expenses in the trucking fleet management system
 */
public class CompanyExpensesTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(CompanyExpensesTab.class);
    
    // Config file constants (same as PayrollTab)
    private static final String CONFIG_FILE = "payroll_config.properties";
    private static final String COMPANY_NAME_KEY = "company.name";
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String CURRENT_USER = System.getProperty("user.name", "mgubran1");
    
    // Data access object
    private final CompanyExpenseDAO expenseDAO = new CompanyExpenseDAO();
    
    // UI Components
    private TableView<CompanyExpense> expenseTable;
    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private ComboBox<String> departmentFilter;
    private ComboBox<String> paymentMethodFilter;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    
    // Summary labels
    private Label totalExpensesLabel;
    private Label totalRecordsLabel;
    private Label avgExpenseLabel;
    private Label operatingExpensesLabel;
    private Label administrativeExpensesLabel;
    
    // Charts
    private PieChart expenseByCategoryChart;
    private BarChart<String, Number> monthlyTrendChart;
    private PieChart paymentMethodChart;
    private LineChart<String, Number> yearOverYearChart;
    
    // Data
    private ObservableList<CompanyExpense> allExpenses = FXCollections.observableArrayList();
    private FilteredList<CompanyExpense> filteredExpenses;
    
    // Analytics list views
    private ListView<String> topCategoriesList;
    private ListView<String> departmentBreakdownList;
    
    // Document storage path
    private String docStoragePath = DocumentManagerConfig.getExpenseStoragePath();
    
    // Expense categories specific to trucking business
    private final ObservableList<String> expenseCategories = FXCollections.observableArrayList(
        "Fuel", "Insurance", "Permits & Licenses", "Office Supplies", "Utilities",
        "Marketing & Advertising", "Professional Services", "Equipment Purchase",
        "Rent/Lease", "Employee Benefits", "Training & Certification", 
        "Communications", "Software & Technology", "Travel & Lodging",
        "Meals & Entertainment", "Bank Fees", "Taxes", "Other"
    );
    
    // Departments
    private final ObservableList<String> departments = FXCollections.observableArrayList(
        "Operations", "Administration", "Finance", "Human Resources", 
        "Safety & Compliance", "Dispatch", "Marketing", "IT"
    );
    
    // Payment methods
    private final ObservableList<String> paymentMethods = FXCollections.observableArrayList(
        "Company Credit Card", "Company Debit Card", "Check", "Cash", 
        "ACH Transfer", "Wire Transfer", "Fuel Card", "Other"
    );
    
    public CompanyExpensesTab() {
        setText("Company Expenses");
        setClosable(false);
        
        logger.info("Initializing CompanyExpensesTab");
        
        // Create document directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(docStoragePath));
        } catch (Exception e) {
            logger.error("Failed to create document directory", e);
        }
        
        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(15));
        mainContent.setStyle("-fx-background-color: #f5f5f5;");
        
        // Header
        HBox header = createHeader();
        
        // Control Panel
        VBox controlPanel = createControlPanel();
        
        // Summary Cards
        HBox summaryCards = createSummaryCards();
        
        // Content TabPane
        TabPane contentTabPane = createContentTabPane();
        
        mainContent.getChildren().addAll(header, controlPanel, summaryCards, contentTabPane);
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
        
        // Initialize data
        loadData();
    }
    
    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(15));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #34495e; -fx-background-radius: 5;");
        
        // Get company name from config
        String companyName = getCompanyNameFromConfig();
        
        Label companyLabel = new Label(companyName);
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        companyLabel.setTextFill(Color.WHITE);
        
        Label titleLabel = new Label("Company Expense Management");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.LIGHTGRAY);
        
        Label subtitleLabel = new Label("Track and manage all business expenses for your trucking company");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        VBox titleBox = new VBox(3, companyLabel, titleLabel, subtitleLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Total expenses display
        Label totalLabel = new Label("Total Company Expenses:");
        totalLabel.setFont(Font.font("Arial", 14));
        totalLabel.setTextFill(Color.WHITE);
        
        totalExpensesLabel = new Label("$0.00");
        totalExpensesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        totalExpensesLabel.setTextFill(Color.LIGHTGREEN);
        
        VBox totalBox = new VBox(5, totalLabel, totalExpensesLabel);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        
        header.getChildren().addAll(titleBox, spacer, totalBox);
        
        return header;
    }
    
    private VBox createControlPanel() {
        VBox controlPanel = new VBox(10);
        controlPanel.setPadding(new Insets(15));
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
        
        // Search and Filter Row
        HBox searchRow = new HBox(15);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        
        searchField = new TextField();
        searchField.setPromptText("Search by vendor, description, invoice...");
        searchField.setPrefWidth(250);
        
        categoryFilter = new ComboBox<>();
        categoryFilter.getItems().add("All Categories");
        categoryFilter.getItems().addAll(expenseCategories);
        categoryFilter.setValue("All Categories");
        categoryFilter.setPrefWidth(150);
        
        departmentFilter = new ComboBox<>();
        departmentFilter.getItems().add("All Departments");
        departmentFilter.getItems().addAll(departments);
        departmentFilter.setValue("All Departments");
        departmentFilter.setPrefWidth(150);
        
        paymentMethodFilter = new ComboBox<>();
        paymentMethodFilter.getItems().add("All Payment Methods");
        paymentMethodFilter.getItems().addAll(paymentMethods);
        paymentMethodFilter.setValue("All Payment Methods");
        paymentMethodFilter.setPrefWidth(160);
        
        searchRow.getChildren().addAll(
            new Label("Search:"), searchField,
            new Separator(Orientation.VERTICAL),
            new Label("Category:"), categoryFilter,
            new Label("Department:"), departmentFilter,
            new Label("Payment:"), paymentMethodFilter
        );
        
        // Date Range and Actions Row
        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        
        // Load saved date range or use defaults
        FilterConfig.DateRange savedRange = FilterConfig.loadExpenseDateRange();
        startDatePicker = new DatePicker(savedRange.getStartDate());
        endDatePicker = new DatePicker(savedRange.getEndDate());
        
        // Style the date pickers
        configureDatePicker(startDatePicker);
        configureDatePicker(endDatePicker);
        
        Button refreshButton = ModernButtonStyles.createPrimaryButton("🔄 Refresh");
        refreshButton.setOnAction(e -> loadData());
        
        Button addExpenseButton = ModernButtonStyles.createSuccessButton("➕ Add Expense");
        addExpenseButton.setOnAction(e -> showAddExpenseDialog());
        
        Button documentManagerButton = ModernButtonStyles.createInfoButton("📄 Document Manager");
        documentManagerButton.setOnAction(e -> showDocumentManager());
        
        Button generateReportButton = ModernButtonStyles.createDangerButton("📊 Generate Report");
        generateReportButton.setOnAction(e -> generateReport());
        
        Button importButton = ModernButtonStyles.createInfoButton("📥 Import CSV/XLSX");
        importButton.setOnAction(e -> showImportDialog());
        
        Button exportButton = ModernButtonStyles.createWarningButton("📤 Export");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        
        actionRow.getChildren().addAll(
            new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker,
            new Separator(Orientation.VERTICAL),
            refreshButton, addExpenseButton, documentManagerButton,
            importButton, generateReportButton, exportButton
        );
        
        controlPanel.getChildren().addAll(searchRow, new Separator(), actionRow);
        
        // Add listeners for filtering
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        categoryFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        departmentFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        paymentMethodFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        
        // Add auto-save listeners for date filters
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
            // Auto-save date range
            if (newVal != null && endDatePicker.getValue() != null) {
                FilterConfig.saveExpenseDateRange(newVal, endDatePicker.getValue());
            }
        });
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
            // Auto-save date range
            if (newVal != null && startDatePicker.getValue() != null) {
                FilterConfig.saveExpenseDateRange(startDatePicker.getValue(), newVal);
            }
        });
        
        return controlPanel;
    }
    
    private HBox createSummaryCards() {
        HBox summaryCards = new HBox(20);
        summaryCards.setPadding(new Insets(10));
        summaryCards.setAlignment(Pos.CENTER);
        
        VBox totalRecordsCard = createSummaryCard("Total Records", "0", "#3498db");
        totalRecordsLabel = (Label) totalRecordsCard.lookup(".value-label");
        
        VBox avgExpenseCard = createSummaryCard("Average Expense", "$0.00", "#27ae60");
        avgExpenseLabel = (Label) avgExpenseCard.lookup(".value-label");
        
        VBox operatingCard = createSummaryCard("Operating Expenses", "$0.00", "#e74c3c");
        operatingExpensesLabel = (Label) operatingCard.lookup(".value-label");
        
        VBox administrativeCard = createSummaryCard("Administrative", "$0.00", "#f39c12");
        administrativeExpensesLabel = (Label) administrativeCard.lookup(".value-label");
        
        summaryCards.getChildren().addAll(totalRecordsCard, avgExpenseCard, operatingCard, administrativeCard);
        
        return summaryCards;
    }
    
    private VBox createSummaryCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
        card.setPrefWidth(200);
        card.setPrefHeight(100);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 12));
        titleLabel.setTextFill(Color.GRAY);
        
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        valueLabel.setTextFill(Color.web(color));
        
        card.getChildren().addAll(titleLabel, valueLabel);
        
        return card;
    }
    
    private TabPane createContentTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Expense History Tab
        Tab historyTab = new Tab("Expense History");
        historyTab.setContent(createHistorySection());
        
        // Analytics Tab
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setContent(createAnalyticsSection());
        
        tabPane.getTabs().addAll(historyTab, analyticsTab);
        
        return tabPane;
    }
    
    private VBox createHistorySection() {
        VBox historySection = new VBox(10);
        historySection.setPadding(new Insets(10));
        
        // Table controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        
        CheckBox recurringCheckBox = new CheckBox("Show Recurring Only");
        recurringCheckBox.setOnAction(e -> applyFilters());
        
        Button uploadDocButton = ModernButtonStyles.createPrimaryButton("📤 Upload Receipt");
        uploadDocButton.setOnAction(e -> uploadDocument());
        
        Button printDocButton = ModernButtonStyles.createSuccessButton("🖨️ Print Receipt");
        printDocButton.setOnAction(e -> printSelectedDocument());
        
        Button editButton = ModernButtonStyles.createWarningButton("✏️ Edit");
        editButton.setOnAction(e -> editSelectedExpense());
        
        Button deleteButton = ModernButtonStyles.createDangerButton("🗑️ Delete");
        deleteButton.setOnAction(e -> deleteSelectedExpense());
        
        Button approveButton = ModernButtonStyles.createInfoButton("✅ Approve");
        approveButton.setOnAction(e -> approveSelectedExpense());
        
        tableControls.getChildren().addAll(recurringCheckBox, new Separator(Orientation.VERTICAL),
                                          uploadDocButton, printDocButton, editButton, deleteButton, approveButton);
        
        // Expense Table
        expenseTable = new TableView<>();
        expenseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        expenseTable.setPrefHeight(400);
        
        TableColumn<CompanyExpense, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("expenseDate"));
        dateCol.setCellFactory(column -> new TableCell<CompanyExpense, LocalDate>() {
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
        dateCol.setPrefWidth(100);
        
        TableColumn<CompanyExpense, String> vendorCol = new TableColumn<>("Vendor");
        vendorCol.setCellValueFactory(new PropertyValueFactory<>("vendor"));
        vendorCol.setPrefWidth(150);
        
        TableColumn<CompanyExpense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setCellFactory(column -> new TableCell<CompanyExpense, String>() {
            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(category);
                    // Color code by category
                    if (category.contains("Fuel")) {
                        setStyle("-fx-background-color: #ffe6e6;");
                    } else if (category.contains("Insurance") || category.contains("Permits")) {
                        setStyle("-fx-background-color: #e6f3ff;");
                    } else if (category.contains("Office") || category.contains("Administrative")) {
                        setStyle("-fx-background-color: #fffbe6;");
                    }
                }
            }
        });
        categoryCol.setPrefWidth(140);
        
        TableColumn<CompanyExpense, String> departmentCol = new TableColumn<>("Department");
        departmentCol.setCellValueFactory(new PropertyValueFactory<>("department"));
        departmentCol.setPrefWidth(120);
        
        TableColumn<CompanyExpense, String> descriptionCol = new TableColumn<>("Description");
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setPrefWidth(200);
        
        TableColumn<CompanyExpense, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setCellFactory(column -> new TableCell<CompanyExpense, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                    setStyle("-fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        amountCol.setPrefWidth(100);
        
        TableColumn<CompanyExpense, String> paymentMethodCol = new TableColumn<>("Payment Method");
        paymentMethodCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        paymentMethodCol.setPrefWidth(140);
        
        TableColumn<CompanyExpense, String> receiptCol = new TableColumn<>("Receipt #");
        receiptCol.setCellValueFactory(new PropertyValueFactory<>("receiptNumber"));
        receiptCol.setPrefWidth(100);
        
        TableColumn<CompanyExpense, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<CompanyExpense, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label statusLabel = new Label(status);
                    statusLabel.setPadding(new Insets(2, 8, 2, 8));
                    statusLabel.setStyle(getStatusStyle(status));
                    setGraphic(statusLabel);
                }
            }
        });
        statusCol.setPrefWidth(100);
        
        TableColumn<CompanyExpense, Boolean> recurringCol = new TableColumn<>("Recurring");
        recurringCol.setCellValueFactory(new PropertyValueFactory<>("recurring"));
        recurringCol.setCellFactory(column -> new TableCell<CompanyExpense, Boolean>() {
            @Override
            protected void updateItem(Boolean recurring, boolean empty) {
                super.updateItem(recurring, empty);
                if (empty || recurring == null) {
                    setText(null);
                } else {
                    setText(recurring ? "Yes" : "No");
                    if (recurring) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #3498db;");
                    }
                }
            }
        });
        recurringCol.setPrefWidth(80);
        
        expenseTable.getColumns().setAll(java.util.List.of(
                dateCol, vendorCol, categoryCol, departmentCol,
                descriptionCol, amountCol, paymentMethodCol,
                receiptCol, statusCol, recurringCol));
        
        // Double-click to edit
        expenseTable.setRowFactory(tv -> {
            TableRow<CompanyExpense> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showEditExpenseDialog(row.getItem());
                }
            });
            return row;
        });
        
        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit");
        MenuItem duplicateItem = new MenuItem("Duplicate");
        MenuItem deleteItem = new MenuItem("Delete");
        MenuItem viewReceiptItem = new MenuItem("View Receipt");
        MenuItem approveItem = new MenuItem("Approve/Reject");
        
        editItem.setOnAction(e -> editSelectedExpense());
        duplicateItem.setOnAction(e -> duplicateSelectedExpense());
        deleteItem.setOnAction(e -> deleteSelectedExpense());
        viewReceiptItem.setOnAction(e -> viewSelectedReceipt());
        approveItem.setOnAction(e -> approveSelectedExpense());
        
        contextMenu.getItems().addAll(editItem, duplicateItem, deleteItem,
                                    new SeparatorMenuItem(), viewReceiptItem, approveItem);
        expenseTable.setContextMenu(contextMenu);
        
        historySection.getChildren().addAll(tableControls, expenseTable);
        
        return historySection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(20));
        analyticsSection.setStyle("-fx-background-color: #F5F5F5;");
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Expense by Category Chart
        VBox categoryChartBox = createChartBox("Expenses by Category");
        
        expenseByCategoryChart = new PieChart();
        expenseByCategoryChart.setTitle("Category Distribution");
        expenseByCategoryChart.setPrefHeight(300);
        expenseByCategoryChart.setAnimated(true);
        
        categoryChartBox.getChildren().add(expenseByCategoryChart);
        
        // Monthly Trend Chart
        VBox monthlyChartBox = createChartBox("Monthly Expense Trend");
        
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Month");
        xAxis.setTickLabelFont(Font.font("Arial", 11));
        xAxis.setTickLabelFill(Color.BLACK);
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Total Cost ($)");
        yAxis.setTickLabelFont(Font.font("Arial", 11));
        yAxis.setTickLabelFill(Color.BLACK);
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override
            public String toString(Number value) {
                return "$" + String.format("%,.0f", value);
            }
        });
        
        monthlyTrendChart = new BarChart<>(xAxis, yAxis);
        monthlyTrendChart.setTitle("12-Month Trend");
        monthlyTrendChart.setPrefHeight(300);
        monthlyTrendChart.setAnimated(true);
        monthlyTrendChart.setLegendVisible(false);
        monthlyTrendChart.setStyle("-fx-font-family: Arial;");
        
        // Style the bar colors
        monthlyTrendChart.lookupAll(".default-color0.chart-bar").forEach(n -> n.setStyle("-fx-bar-fill: #3498db;"));
        
        monthlyChartBox.getChildren().add(monthlyTrendChart);
        
        // Payment Method Chart
        VBox paymentChartBox = createChartBox("Payment Method Distribution");
        
        paymentMethodChart = new PieChart();
        paymentMethodChart.setTitle("Payment Methods");
        paymentMethodChart.setPrefHeight(300);
        paymentMethodChart.setAnimated(true);
        
        paymentChartBox.getChildren().add(paymentMethodChart);
        
        // Year-over-Year Comparison
        VBox yoyChartBox = createChartBox("Year-over-Year Comparison");
        
        CategoryAxis yoyXAxis = new CategoryAxis();
        yoyXAxis.setTickLabelFont(Font.font("Arial", 11));
        yoyXAxis.setTickLabelFill(Color.BLACK);
        
        NumberAxis yoyYAxis = new NumberAxis();
        yoyYAxis.setLabel("Amount ($)");
        yoyYAxis.setTickLabelFont(Font.font("Arial", 11));
        yoyYAxis.setTickLabelFill(Color.BLACK);
        yoyYAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yoyYAxis) {
            @Override
            public String toString(Number value) {
                return "$" + String.format("%,.0f", value);
            }
        });
        
        yearOverYearChart = new LineChart<>(yoyXAxis, yoyYAxis);
        yearOverYearChart.setTitle("YoY Expense Trend");
        yearOverYearChart.setPrefHeight(300);
        yearOverYearChart.setAnimated(true);
        yearOverYearChart.setStyle("-fx-font-family: Arial;");
        
        yoyChartBox.getChildren().add(yearOverYearChart);
        
        // Top Expense Categories
        VBox topCategoriesBox = createTopCategoriesBox();
        
        // Department Breakdown
        VBox departmentBox = createDepartmentBreakdownBox();
        
        
        chartsGrid.add(categoryChartBox, 0, 0);
        chartsGrid.add(monthlyChartBox, 1, 0);
        chartsGrid.add(paymentChartBox, 0, 1);
        chartsGrid.add(yoyChartBox, 1, 1);
        chartsGrid.add(topCategoriesBox, 0, 2);
        chartsGrid.add(departmentBox, 1, 2);
        
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().addAll(col, col);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    
    private VBox createTopCategoriesBox() {
        VBox topCategoriesBox = new VBox(10);
        topCategoriesBox.setPadding(new Insets(15));
        topCategoriesBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label title = new Label("Top 5 Expense Categories");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#3498db"));
        
        topCategoriesList = new ListView<>();
        topCategoriesList.setPrefHeight(200);
        topCategoriesList.setPlaceholder(new Label("No data available"));
        topCategoriesList.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-radius: 5px;");
        topCategoriesList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setFont(Font.font("Arial", 12));
                    setTextFill(Color.BLACK);
                    setPadding(new Insets(8, 10, 8, 10));
                    
                    // Alternate row colors
                    if (getIndex() % 2 == 0) {
                        setStyle("-fx-background-color: white;");
                    } else {
                        setStyle("-fx-background-color: #F5F5F5;");
                    }
                }
            }
        });
        
        topCategoriesBox.getChildren().addAll(title, topCategoriesList);
        
        return topCategoriesBox;
    }
    
    private VBox createDepartmentBreakdownBox() {
        VBox departmentBox = new VBox(10);
        departmentBox.setPadding(new Insets(15));
        departmentBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                             "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label title = new Label("Department Expense Breakdown");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#3498db"));
        
        departmentBreakdownList = new ListView<>();
        departmentBreakdownList.setPrefHeight(200);
        departmentBreakdownList.setPlaceholder(new Label("No data available"));
        departmentBreakdownList.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-radius: 5px;");
        departmentBreakdownList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setFont(Font.font("Arial", 12));
                    setTextFill(Color.BLACK);
                    setPadding(new Insets(8, 10, 8, 10));
                    
                    // Alternate row colors
                    if (getIndex() % 2 == 0) {
                        setStyle("-fx-background-color: white;");
                    } else {
                        setStyle("-fx-background-color: #F5F5F5;");
                    }
                }
            }
        });
        
        departmentBox.getChildren().addAll(title, departmentBreakdownList);
        
        return departmentBox;
    }
    
    private VBox createChartBox(String title) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        box.setMaxWidth(Double.MAX_VALUE);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#3498db"));
        
        box.getChildren().add(titleLabel);
        return box;
    }
    
    private void updateTopCategoriesList(List<CompanyExpense> expenses) {
        Map<String, Double> categoryTotals = expenses.stream()
            .collect(Collectors.groupingBy(
                CompanyExpense::getCategory,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        List<String> topCategories = categoryTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(5)
            .map(entry -> String.format("%s: %s", entry.getKey(), CURRENCY_FORMAT.format(entry.getValue())))
            .collect(Collectors.toList());
        
        if (topCategoriesList != null) {
            topCategoriesList.setItems(FXCollections.observableArrayList(topCategories));
        }
    }
    
    private void updateDepartmentBreakdownList(List<CompanyExpense> expenses) {
        Map<String, Double> departmentTotals = expenses.stream()
            .filter(expense -> expense.getDepartment() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getDepartment,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        List<String> departmentBreakdown = departmentTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(entry -> String.format("%s: %s", entry.getKey(), CURRENCY_FORMAT.format(entry.getValue())))
            .collect(Collectors.toList());
        
        if (departmentBreakdownList != null) {
            departmentBreakdownList.setItems(FXCollections.observableArrayList(departmentBreakdown));
        }
    }
    
    
    private void loadData() {
        logger.info("Loading company expense data");
        
        try {
            List<CompanyExpense> expenses = expenseDAO.findByDateRange(
                startDatePicker.getValue(), 
                endDatePicker.getValue()
            );
            
            allExpenses.clear();
            allExpenses.addAll(expenses);
            
            filteredExpenses = new FilteredList<>(allExpenses, p -> true);
            SortedList<CompanyExpense> sortedExpenses = new SortedList<>(filteredExpenses);
            sortedExpenses.comparatorProperty().bind(expenseTable.comparatorProperty());
            expenseTable.setItems(sortedExpenses);
            
            applyFilters();
            updateSummaryCards();
            updateCharts();
            
            logger.info("Loaded {} expense records", expenses.size());
            
        } catch (Exception e) {
            logger.error("Failed to load expense data", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load expense data: " + e.getMessage());
        }
    }
    
    private void applyFilters() {
        if (filteredExpenses == null) return;
        
        filteredExpenses.setPredicate(expense -> {
            // Search text filter
            String searchText = searchField.getText().toLowerCase();
            if (!searchText.isEmpty()) {
                boolean matchesSearch = 
                    (expense.getVendor() != null && expense.getVendor().toLowerCase().contains(searchText)) ||
                    (expense.getDescription() != null && expense.getDescription().toLowerCase().contains(searchText)) ||
                    (expense.getReceiptNumber() != null && expense.getReceiptNumber().toLowerCase().contains(searchText)) ||
                    (expense.getCategory() != null && expense.getCategory().toLowerCase().contains(searchText));
                
                if (!matchesSearch) return false;
            }
            
            // Category filter
            String category = categoryFilter.getValue();
            if (!"All Categories".equals(category) && category != null) {
                if (!category.equals(expense.getCategory())) {
                    return false;
                }
            }
            
            // Department filter
            String department = departmentFilter.getValue();
            if (!"All Departments".equals(department) && department != null) {
                if (!department.equals(expense.getDepartment())) {
                    return false;
                }
            }
            
            // Payment method filter
            String paymentMethod = paymentMethodFilter.getValue();
            if (!"All Payment Methods".equals(paymentMethod) && paymentMethod != null) {
                if (!paymentMethod.equals(expense.getPaymentMethod())) {
                    return false;
                }
            }
            
            // Date range filter
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null && expense.getExpenseDate().isBefore(startDate)) {
                return false;
            }
            if (endDate != null && expense.getExpenseDate().isAfter(endDate)) {
                return false;
            }
            
            return true;
        });
        
        updateSummaryCards();
        updateCharts();
    }
    
    private void updateSummaryCards() {
        List<CompanyExpense> visibleExpenses = filteredExpenses != null ? 
            new ArrayList<>(filteredExpenses) : new ArrayList<>();
        
        // Total expenses
        double totalExpenses = visibleExpenses.stream()
            .mapToDouble(CompanyExpense::getAmount)
            .sum();
        totalExpensesLabel.setText(CURRENCY_FORMAT.format(totalExpenses));
        
        // Total records
        totalRecordsLabel.setText(String.valueOf(visibleExpenses.size()));
        
        // Average expense
        double avgExpense = visibleExpenses.isEmpty() ? 0 : totalExpenses / visibleExpenses.size();
        avgExpenseLabel.setText(CURRENCY_FORMAT.format(avgExpense));
        
        // Operating expenses (Fuel, Permits, Insurance, etc.)
        Set<String> operatingCategories = Set.of("Fuel", "Permits & Licenses", "Insurance", 
                                                 "Equipment Purchase", "Communications");
        double operatingExpenses = visibleExpenses.stream()
            .filter(e -> operatingCategories.contains(e.getCategory()))
            .mapToDouble(CompanyExpense::getAmount)
            .sum();
        operatingExpensesLabel.setText(CURRENCY_FORMAT.format(operatingExpenses));
        
        // Administrative expenses
        Set<String> adminCategories = Set.of("Office Supplies", "Professional Services", 
                                           "Software & Technology", "Marketing & Advertising");
        double adminExpenses = visibleExpenses.stream()
            .filter(e -> adminCategories.contains(e.getCategory()))
            .mapToDouble(CompanyExpense::getAmount)
            .sum();
        administrativeExpensesLabel.setText(CURRENCY_FORMAT.format(adminExpenses));
    }
    
    private void updateCharts() {
        List<CompanyExpense> visibleExpenses = filteredExpenses != null ? 
            new ArrayList<>(filteredExpenses) : new ArrayList<>();
        
        // Update expense by category chart
        Map<String, Double> categoryTotals = visibleExpenses.stream()
            .filter(expense -> expense.getCategory() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getCategory,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        categoryTotals.forEach((category, total) -> {
            pieData.add(new PieChart.Data(category + " (" + CURRENCY_FORMAT.format(total) + ")", total));
        });
        expenseByCategoryChart.setData(pieData);
        
        // Update monthly trend chart
        // Group by year-month for proper sorting
        Map<String, Double> monthlyTotals = visibleExpenses.stream()
            .filter(expense -> expense.getExpenseDate() != null)
            .collect(Collectors.groupingBy(
                expense -> expense.getExpenseDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        XYChart.Series<String, Number> monthlySeries = new XYChart.Series<>();
        
        // Sort by year-month and convert to display format
        monthlyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                // Convert yyyy-MM to MMM yyyy for display
                String yearMonth = entry.getKey();
                try {
                    LocalDate date = LocalDate.parse(yearMonth + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String displayMonth = date.format(DateTimeFormatter.ofPattern("MMM yyyy"));
                    monthlySeries.getData().add(new XYChart.Data<>(displayMonth, entry.getValue()));
                } catch (Exception e) {
                    // Fallback to original if parsing fails
                    monthlySeries.getData().add(new XYChart.Data<>(yearMonth, entry.getValue()));
                }
            });
        
        monthlyTrendChart.getData().clear();
        monthlyTrendChart.getData().add(monthlySeries);
        
        // Update payment method chart
        Map<String, Double> paymentMethodTotals = visibleExpenses.stream()
            .filter(expense -> expense.getPaymentMethod() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getPaymentMethod,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        ObservableList<PieChart.Data> paymentData = FXCollections.observableArrayList();
        paymentMethodTotals.forEach((method, total) -> {
            paymentData.add(new PieChart.Data(method + " (" + CURRENCY_FORMAT.format(total) + ")", total));
        });
        paymentMethodChart.setData(paymentData);
        
        // Update year-over-year chart (simplified - would need historical data)
        updateYearOverYearChart(visibleExpenses);
        
        
        // Update bar chart colors
        monthlyTrendChart.lookupAll(".default-color0.chart-bar").forEach(n -> n.setStyle("-fx-bar-fill: #3498db;"));
        
        // Update top categories list
        updateTopCategoriesList(visibleExpenses);
        
        // Update department breakdown list
        updateDepartmentBreakdownList(visibleExpenses);
    }
    
    private void updateYearOverYearChart(List<CompanyExpense> expenses) {
        // Group by year and month
        Map<String, Map<Integer, Double>> yearMonthTotals = expenses.stream()
            .collect(Collectors.groupingBy(
                e -> e.getExpenseDate().getMonth().toString(),
                Collectors.groupingBy(
                    e -> e.getExpenseDate().getYear(),
                    Collectors.summingDouble(CompanyExpense::getAmount)
                )
            ));
        
        yearOverYearChart.getData().clear();
        
        // Create series for each year
        Set<Integer> years = expenses.stream()
            .map(e -> e.getExpenseDate().getYear())
            .collect(Collectors.toSet());
        
        for (Integer year : years) {
            XYChart.Series<String, Number> yearSeries = new XYChart.Series<>();
            yearSeries.setName(String.valueOf(year));
            
            for (String month : Arrays.asList("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                                            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")) {
                Double amount = yearMonthTotals.getOrDefault(month, Collections.emptyMap()).getOrDefault(year, 0.0);
                yearSeries.getData().add(new XYChart.Data<>(month.substring(0, 3), amount));
            }
            
            yearOverYearChart.getData().add(yearSeries);
        }
    }
    
    private void showAddExpenseDialog() {
        showExpenseDialog(null, true);
    }
    
    private void showEditExpenseDialog(CompanyExpense expense) {
        showExpenseDialog(expense, false);
    }
    
    private void showExpenseDialog(CompanyExpense expense, boolean isAdd) {
        Dialog<CompanyExpense> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add Company Expense" : "Edit Company Expense");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Date
        DatePicker datePicker = new DatePicker(expense != null ? expense.getExpenseDate() : LocalDate.now());
        
        // Vendor
        TextField vendorField = new TextField();
        vendorField.setPromptText("Vendor name");
        if (expense != null) vendorField.setText(expense.getVendor());
        
        // Category
        ComboBox<String> categoryCombo = new ComboBox<>(expenseCategories);
        categoryCombo.setPromptText("Select category");
        if (expense != null) categoryCombo.setValue(expense.getCategory());
        
        // Department
        ComboBox<String> departmentCombo = new ComboBox<>(departments);
        departmentCombo.setPromptText("Select department");
        departmentCombo.setValue(expense != null ? expense.getDepartment() : "Operations");
        
        // Description
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Description of expense...");
        descriptionArea.setPrefRowCount(3);
        if (expense != null) descriptionArea.setText(expense.getDescription());
        
        // Amount
        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        if (expense != null) amountField.setText(String.valueOf(expense.getAmount()));
        
        // Payment Method
        ComboBox<String> paymentMethodCombo = new ComboBox<>(paymentMethods);
        paymentMethodCombo.setPromptText("Select payment method");
        if (expense != null) paymentMethodCombo.setValue(expense.getPaymentMethod());
        
        // Receipt Number
        TextField receiptField = new TextField();
        receiptField.setPromptText("Receipt/Invoice number");
        if (expense != null) receiptField.setText(expense.getReceiptNumber());
        
        // Recurring
        CheckBox recurringCheckBox = new CheckBox("Recurring Expense");
        if (expense != null) recurringCheckBox.setSelected(expense.isRecurring());
        
        // Status (for approval workflow)
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Pending", "Approved", "Rejected", "Under Review");
        statusCombo.setValue(expense != null ? expense.getStatus() : "Pending");
        
        // Notes
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(2);
        if (expense != null) notesArea.setText(expense.getNotes());
        
        // Layout
        int row = 0;
        grid.add(new Label("Date:"), 0, row);
        grid.add(datePicker, 1, row++);
        
        grid.add(new Label("Vendor:"), 0, row);
        grid.add(vendorField, 1, row++);
        
        grid.add(new Label("Category:"), 0, row);
        grid.add(categoryCombo, 1, row++);
        
        grid.add(new Label("Department:"), 0, row);
        grid.add(departmentCombo, 1, row++);
        
        grid.add(new Label("Description:"), 0, row);
        grid.add(descriptionArea, 1, row++);
        
        grid.add(new Label("Amount:"), 0, row);
        grid.add(amountField, 1, row++);
        
        grid.add(new Label("Payment Method:"), 0, row);
        grid.add(paymentMethodCombo, 1, row++);
        
        grid.add(new Label("Receipt #:"), 0, row);
        grid.add(receiptField, 1, row++);
        
        grid.add(recurringCheckBox, 1, row++);
        
        grid.add(new Label("Status:"), 0, row);
        grid.add(statusCombo, 1, row++);
        
        grid.add(new Label("Notes:"), 0, row);
        grid.add(notesArea, 1, row++);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validation
        amountField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*\\.?\\d*")) {
                amountField.setText(oldText);
            }
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    CompanyExpense result = expense != null ? expense : new CompanyExpense();
                    
                    result.setExpenseDate(datePicker.getValue());
                    result.setVendor(vendorField.getText());
                    result.setCategory(categoryCombo.getValue());
                    result.setDepartment(departmentCombo.getValue());
                    result.setDescription(descriptionArea.getText());
                    result.setAmount(amountField.getText().isEmpty() ? 0 : 
                                   Double.parseDouble(amountField.getText()));
                    result.setPaymentMethod(paymentMethodCombo.getValue());
                    result.setReceiptNumber(receiptField.getText());
                    result.setRecurring(recurringCheckBox.isSelected());
                    result.setStatus(statusCombo.getValue());
                    result.setNotes(notesArea.getText());
                    result.setEmployeeId(CURRENT_USER);
                    
                    return result;
                } catch (Exception e) {
                    logger.error("Error creating expense record", e);
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(result -> {
            try {
                expenseDAO.save(result);
                loadData();
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         isAdd ? "Company expense added successfully" : 
                                "Company expense updated successfully");
            } catch (Exception e) {
                logger.error("Failed to save expense record", e);
                showAlert(Alert.AlertType.ERROR, "Error", 
                         "Failed to save expense record: " + e.getMessage());
            }
        });
    }
    
    private void editSelectedExpense() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEditExpenseDialog(selected);
        } else {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an expense to edit");
        }
    }
    
    private void duplicateSelectedExpense() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            CompanyExpense duplicate = new CompanyExpense();
            duplicate.setVendor(selected.getVendor());
            duplicate.setCategory(selected.getCategory());
            duplicate.setDepartment(selected.getDepartment());
            duplicate.setDescription(selected.getDescription());
            duplicate.setAmount(selected.getAmount());
            duplicate.setPaymentMethod(selected.getPaymentMethod());
            duplicate.setRecurring(selected.isRecurring());
            duplicate.setExpenseDate(LocalDate.now());
            duplicate.setStatus("Pending");
            
            showExpenseDialog(duplicate, true);
        }
    }
    
    private void deleteSelectedExpense() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an expense to delete");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this expense record?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Deletion");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    expenseDAO.delete(selected.getId());
                    loadData();
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Expense record deleted successfully");
                } catch (Exception e) {
                    logger.error("Failed to delete expense record", e);
                    showAlert(Alert.AlertType.ERROR, "Error", 
                             "Failed to delete record: " + e.getMessage());
                }
            }
        });
    }
    
    private void approveSelectedExpense() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an expense to approve/reject");
            return;
        }
        
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Approve/Reject Expense");
        dialog.setHeaderText("Review expense from " + selected.getVendor());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Approved", "Rejected", "Under Review");
        statusCombo.setValue(selected.getStatus());
        
        TextArea commentsArea = new TextArea();
        commentsArea.setPromptText("Comments...");
        commentsArea.setPrefRowCount(3);
        
        grid.add(new Label("Status:"), 0, 0);
        grid.add(statusCombo, 1, 0);
        grid.add(new Label("Comments:"), 0, 1);
        grid.add(commentsArea, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return statusCombo.getValue();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(status -> {
            selected.setStatus(status);
            if (!commentsArea.getText().isEmpty()) {
                selected.setNotes(selected.getNotes() + "\n[" + CURRENT_USER + " - " + 
                                LocalDate.now() + "]: " + commentsArea.getText());
            }
            
            try {
                expenseDAO.save(selected);
                loadData();
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         "Expense status updated to: " + status);
            } catch (Exception e) {
                logger.error("Failed to update expense status", e);
                showAlert(Alert.AlertType.ERROR, "Error", 
                         "Failed to update status: " + e.getMessage());
            }
        });
    }
    
    private void viewSelectedReceipt() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewDocument(selected.getId(), null);
        }
    }
    
    private String getStatusStyle(String status) {
        switch (status) {
            case "Approved":
                return "-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-background-radius: 3;";
            case "Pending":
                return "-fx-background-color: #fff3cd; -fx-text-fill: #856404; -fx-background-radius: 3;";
            case "Rejected":
                return "-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-background-radius: 3;";
            case "Under Review":
                return "-fx-background-color: #d1ecf1; -fx-text-fill: #0c5460; -fx-background-radius: 3;";
            default:
                return "-fx-background-color: #e2e3e5; -fx-text-fill: #383d41; -fx-background-radius: 3;";
        }
    }
    
    private void showDocumentManager() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an expense record to manage documents");
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Company Expense Document Manager - " + selected.getVendor() + " - " + 
                       selected.getExpenseDate().format(DATE_FORMAT));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(800);
        content.setPrefHeight(600);
        
        // Header with expense info
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Documents for " + selected.getVendor());
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        Label dateLabel = new Label("Expense Date: " + selected.getExpenseDate().format(DATE_FORMAT));
        dateLabel.setStyle("-fx-text-fill: #666;");
        
        headerBox.getChildren().addAll(titleLabel, new Separator(Orientation.VERTICAL), dateLabel);
        
        // Document list section
        VBox documentSection = createDocumentListSection(selected);
        
        // Action buttons section
        HBox actionButtons = createDocumentActionButtons(selected, documentSection);
        
        // Settings button
        Button settingsButton = ModernButtonStyles.createSecondaryButton("⚙️ Settings");
        settingsButton.setOnAction(e -> {
            DocumentManagerSettingsDialog settingsDialog = 
                new DocumentManagerSettingsDialog((Stage) expenseTable.getScene().getWindow());
            settingsDialog.showAndWait();
            // Refresh storage path after settings change
            docStoragePath = DocumentManagerConfig.getExpenseStoragePath();
        });
        
        HBox topButtons = new HBox(10, settingsButton);
        topButtons.setAlignment(Pos.CENTER_RIGHT);
        
        content.getChildren().addAll(headerBox, documentSection, actionButtons, topButtons);
        VBox.setVgrow(documentSection, Priority.ALWAYS);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
    
    /**
     * Create document list section
     */
    private VBox createDocumentListSection(CompanyExpense expense) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📄 Documents");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        ListView<String> docListView = new ListView<>();
        docListView.setPrefHeight(300);
        updateDocumentList(docListView, expense.getId());
        
        // Folder info
        Path folderPath = DocumentManagerConfig.getExpenseFolderPath(
            expense.getCategory(), expense.getDepartment());
        
        Label folderInfo = new Label("Storage: " + folderPath.toString());
        folderInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        
        section.getChildren().addAll(sectionTitle, docListView, folderInfo);
        
        return section;
    }
    
    /**
     * Create document action buttons
     */
    private HBox createDocumentActionButtons(CompanyExpense expense, VBox documentSection) {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button uploadBtn = ModernButtonStyles.createPrimaryButton("📤 Upload");
        Button viewBtn = ModernButtonStyles.createInfoButton("👁️ View");
        Button printBtn = ModernButtonStyles.createSecondaryButton("🖨️ Print");
        Button deleteBtn = ModernButtonStyles.createDangerButton("🗑️ Delete");
        Button openFolderBtn = ModernButtonStyles.createSuccessButton("📁 Open Folder");
        
        // Get document list view from the section
        ListView<String> docListView = (ListView<String>) documentSection.getChildren().get(1);
        
        uploadBtn.setOnAction(e -> {
            uploadDocumentForExpense(expense.getId());
            updateDocumentList(docListView, expense.getId());
        });
        
        viewBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                viewDocument(expense.getId(), selectedDoc);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                         "Please select a document to view");
            }
        });
        
        printBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                printDocument(expense.getId(), selectedDoc);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                         "Please select a document to print");
            }
        });
        
        deleteBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                deleteDocument(expense.getId(), selectedDoc);
                updateDocumentList(docListView, expense.getId());
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                         "Please select a document to delete");
            }
        });
        
        openFolderBtn.setOnAction(e -> {
            Path folderPath = DocumentManagerConfig.getExpenseFolderPath(
                expense.getCategory(), expense.getDepartment());
            DocumentManagerConfig.openFolder(folderPath);
        });
        
        buttonBox.getChildren().addAll(uploadBtn, viewBtn, printBtn, deleteBtn, openFolderBtn);
        
        return buttonBox;
    }
    
    private void uploadDocument() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an expense record to upload document");
            return;
        }
        
        uploadDocumentForExpense(selected.getId());
    }
    
    private void uploadDocumentForExpense(int expenseId) {
        try {
            CompanyExpense expense = expenseDAO.findById(expenseId);
            if (expense == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Expense record not found");
                return;
            }
            
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Upload Document for " + expense.getVendor());
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Document Files", "*.doc", "*.docx", "*.txt")
            );
            
            File selectedFile = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
            if (selectedFile != null) {
                // Create organized path structure
                Path documentPath = DocumentManagerConfig.createExpenseDocumentPath(
                    expense.getCategory(),
                    expense.getDepartment(),
                    expense.getReceiptNumber() != null ? expense.getReceiptNumber() : "EXP" + expense.getId(),
                    selectedFile.getName()
                );
                
                // Copy file to organized location
                Files.copy(selectedFile.toPath(), documentPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Uploaded document to: {}", documentPath);
                
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         "Document uploaded successfully");
            }
        } catch (Exception e) {
            logger.error("Failed to upload document", e);
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to upload document: " + e.getMessage());
        }
    }
    
    private void updateDocumentList(ListView<String> listView, int expenseId) {
        try {
            CompanyExpense expense = expenseDAO.findById(expenseId);
            if (expense == null) {
                listView.setItems(FXCollections.observableArrayList());
                return;
            }
            
            // Use the new organized folder structure
            List<String> documents = DocumentManagerConfig.listExpenseDocuments(
                expense.getCategory(), expense.getDepartment());
            
            listView.setItems(FXCollections.observableArrayList(documents));
            
        } catch (Exception e) {
            logger.error("Failed to list documents", e);
            listView.setItems(FXCollections.observableArrayList());
        }
    }
    
    private void viewDocument(int expenseId, String document) {
        try {
            CompanyExpense expense = expenseDAO.findById(expenseId);
            if (expense == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Expense record not found");
                return;
            }
            
            Path folderPath = DocumentManagerConfig.getExpenseFolderPath(
                expense.getCategory(), expense.getDepartment());
            Path docPath = folderPath.resolve(document);
            
            File file = docPath.toFile();
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Cannot Open", 
                         "Cannot open file. File is saved at: " + docPath);
            }
        } catch (Exception e) {
            logger.error("Failed to open document", e);
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to open document: " + e.getMessage());
        }
    }
    
    private void printDocument(int expenseId, String document) {
        try {
            CompanyExpense expense = expenseDAO.findById(expenseId);
            if (expense == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Expense record not found");
                return;
            }
            
            Path folderPath = DocumentManagerConfig.getExpenseFolderPath(
                expense.getCategory(), expense.getDepartment());
            Path docPath = folderPath.resolve(document);
            
            File file = docPath.toFile();
            if (java.awt.Desktop.isDesktopSupported() && 
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.PRINT)) {
                java.awt.Desktop.getDesktop().print(file);
                logger.info("Printing document: {}", docPath);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Print Not Supported", 
                         "Printing is not supported. Please open the file first.");
                viewDocument(expenseId, document);
            }
        } catch (Exception e) {
            logger.error("Failed to print document", e);
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to print document: " + e.getMessage());
        }
    }
    
    private void deleteDocument(int expenseId, String document) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this document?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Deletion");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    CompanyExpense expense = expenseDAO.findById(expenseId);
                    if (expense == null) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Expense record not found");
                        return;
                    }
                    
                    Path folderPath = DocumentManagerConfig.getExpenseFolderPath(
                        expense.getCategory(), expense.getDepartment());
                    Path docPath = folderPath.resolve(document);
                    
                    Files.delete(docPath);
                    logger.info("Deleted document: {}", docPath);
                } catch (Exception e) {
                    logger.error("Failed to delete document", e);
                    showAlert(Alert.AlertType.ERROR, "Error", 
                             "Failed to delete document: " + e.getMessage());
                }
            }
        });
    }
    
    private void printSelectedDocument() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select an expense record to print documents");
            return;
        }
        
        try {
            Path expenseDir = Paths.get(docStoragePath, String.valueOf(selected.getId()));
            if (!Files.exists(expenseDir) || !Files.isDirectory(expenseDir)) {
                showAlert(Alert.AlertType.INFORMATION, "No Documents", 
                         "No documents found for this expense record");
                return;
            }
            
            List<String> documents = Files.list(expenseDir)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
            
            if (documents.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Documents", 
                         "No documents found for this expense record");
                return;
            }
            
            if (documents.size() == 1) {
                printDocument(selected.getId(), documents.get(0));
            } else {
                // Show selection dialog
                Dialog<String> dialog = new Dialog<>();
                dialog.setTitle("Select Document to Print");
                dialog.setHeaderText("Choose a document to print");
                
                ListView<String> docList = new ListView<>(FXCollections.observableArrayList(documents));
                docList.setPrefHeight(300);
                
                dialog.getDialogPane().setContent(docList);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                
                dialog.setResultConverter(dialogButton -> {
                    if (dialogButton == ButtonType.OK) {
                        return docList.getSelectionModel().getSelectedItem();
                    }
                    return null;
                });
                
                dialog.showAndWait().ifPresent(doc -> 
                    printDocument(selected.getId(), doc)
                );
            }
            
        } catch (Exception e) {
            logger.error("Error accessing documents", e);
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Error accessing documents: " + e.getMessage());
        }
    }
    
    private void generateReport() {
        logger.info("Generating company expense report");
        
        List<CompanyExpense> expenses = filteredExpenses != null ? 
            new ArrayList<>(filteredExpenses) : new ArrayList<>();
        
        if (expenses.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Data", 
                     "No expense records to report");
            return;
        }
        
        StringBuilder report = new StringBuilder();
        report.append("COMPANY EXPENSE REPORT\n");
        report.append("======================\n\n");
        report.append("Report Date: ").append(LocalDate.now().format(DATE_FORMAT)).append("\n");
        report.append("Date Range: ").append(startDatePicker.getValue().format(DATE_FORMAT))
              .append(" to ").append(endDatePicker.getValue().format(DATE_FORMAT)).append("\n\n");
        
        // Summary
        double totalExpenses = expenses.stream().mapToDouble(CompanyExpense::getAmount).sum();
        double avgExpense = totalExpenses / expenses.size();
        
        report.append("SUMMARY\n");
        report.append("-------\n");
        report.append("Total Records: ").append(expenses.size()).append("\n");
        report.append("Total Expenses: ").append(CURRENCY_FORMAT.format(totalExpenses)).append("\n");
        report.append("Average Expense: ").append(CURRENCY_FORMAT.format(avgExpense)).append("\n\n");
        
        // By Category
        Map<String, Double> categoryTotals = expenses.stream()
            .filter(expense -> expense.getCategory() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getCategory,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        report.append("BY CATEGORY\n");
        report.append("-----------\n");
        categoryTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(entry -> {
                report.append(entry.getKey()).append(": ")
                      .append(CURRENCY_FORMAT.format(entry.getValue())).append("\n");
            });
        report.append("\n");
        
        // By Department
        Map<String, Double> departmentTotals = expenses.stream()
            .filter(expense -> expense.getDepartment() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getDepartment,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        report.append("BY DEPARTMENT\n");
        report.append("-------------\n");
        departmentTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(entry -> {
                report.append(entry.getKey()).append(": ")
                      .append(CURRENCY_FORMAT.format(entry.getValue())).append("\n");
            });
        report.append("\n");
        
        // Top Vendors
        Map<String, Double> vendorTotals = expenses.stream()
            .filter(expense -> expense.getVendor() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getVendor,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        report.append("TOP 10 VENDORS\n");
        report.append("--------------\n");
        vendorTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                report.append(entry.getKey()).append(": ")
                      .append(CURRENCY_FORMAT.format(entry.getValue())).append("\n");
            });
        report.append("\n");
        
        // Payment Methods
        Map<String, Double> paymentMethodTotals = expenses.stream()
            .filter(expense -> expense.getPaymentMethod() != null)
            .collect(Collectors.groupingBy(
                CompanyExpense::getPaymentMethod,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        report.append("BY PAYMENT METHOD\n");
        report.append("-----------------\n");
        paymentMethodTotals.forEach((method, total) -> {
            report.append(method).append(": ")
                  .append(CURRENCY_FORMAT.format(total)).append("\n");
        });
        
        // Show report in dialog
        TextArea reportArea = new TextArea(report.toString());
        reportArea.setEditable(false);
        reportArea.setFont(Font.font("Courier New", 12));
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Company Expense Report");
        dialog.getDialogPane().setContent(reportArea);
        dialog.getDialogPane().setPrefSize(700, 600);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.showAndWait();
    }
    
    
    
    private void showExportMenu(Button exportButton) {
        ContextMenu exportMenu = new ContextMenu();
        
        MenuItem csvItem = new MenuItem("Export to CSV");
        csvItem.setOnAction(e -> exportToCSV());
        
        MenuItem excelItem = new MenuItem("Export to Excel");
        excelItem.setOnAction(e -> exportToExcel());
        
        MenuItem pdfItem = new MenuItem("Export to PDF");
        pdfItem.setOnAction(e -> exportToPDF());
        
        exportMenu.getItems().addAll(csvItem, excelItem, pdfItem);
        exportMenu.show(exportButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }
    
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("company_expenses_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Write headers
                writer.println("Date,Vendor,Category,Department,Description,Amount,Payment Method,Receipt #,Status,Recurring");
                
                // Write data
                List<CompanyExpense> expenses = filteredExpenses != null ? 
                    new ArrayList<>(filteredExpenses) : new ArrayList<>();
                    
                for (CompanyExpense expense : expenses) {
                    writer.printf("%s,%s,%s,%s,%s,%.2f,%s,%s,%s,%s%n",
                        expense.getExpenseDate().format(DATE_FORMAT),
                        expense.getVendor(),
                        expense.getCategory(),
                        expense.getDepartment(),
                        expense.getDescription() != null ? expense.getDescription().replace(",", ";") : "",
                        expense.getAmount(),
                        expense.getPaymentMethod(),
                        expense.getReceiptNumber() != null ? expense.getReceiptNumber() : "",
                        expense.getStatus(),
                        expense.isRecurring() ? "Yes" : "No"
                    );
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                         "Company expenses exported to CSV successfully!");
                         
            } catch (Exception e) {
                logger.error("Failed to export to CSV", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                         "Failed to export data: " + e.getMessage());
            }
        }
    }
    
    private void exportToExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Excel File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("company_expenses_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // Note: In a real implementation, you would use Apache POI here
            showAlert(Alert.AlertType.INFORMATION, "Export to Excel", 
                     "Excel export functionality would be implemented here using Apache POI");
        }
    }
    
    private void exportToPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("company_expense_report_" + LocalDate.now() + ".pdf");
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                // Get company name from config file
                String companyName = getCompanyNameFromConfig();
                
                // Get date range from filters
                LocalDate startDate = startDatePicker.getValue() != null ? startDatePicker.getValue() : LocalDate.now().withDayOfYear(1);
                LocalDate endDate = endDatePicker.getValue() != null ? endDatePicker.getValue() : LocalDate.now();
                
                // Use the new PDF exporter
                List<CompanyExpense> expenses = filteredExpenses != null ? new ArrayList<>(filteredExpenses) : new ArrayList<>();
                CompanyExpensePDFExporter exporter = new CompanyExpensePDFExporter(companyName, expenses, startDate, endDate);
                exporter.exportToPDF(file);
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Company expense report exported to PDF successfully!");
            } catch (Exception e) {
                logger.error("Failed to export to PDF", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", "Failed to export data: " + e.getMessage());
            }
        }
    }
    
    private String getCompanyNameFromConfig() {
        String defaultName = "Company";
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                String name = props.getProperty(COMPANY_NAME_KEY);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (Exception e) {
                logger.warn("Could not load company name from config: " + e.getMessage());
            }
        }
        
        return defaultName;
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    
    public CompanyExpenseDAO getCompanyExpenseDAO() {
        return expenseDAO;
    }
    
    private void configureDatePicker(DatePicker datePicker) {
        // Set the style for the date picker
        datePicker.setStyle("-fx-font-family: Arial; -fx-font-size: 12px;");
        
        // Custom date formatter to ensure consistent display
        datePicker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                if (date != null) {
                    return DATE_FORMAT.format(date);
                } else {
                    return "";
                }
            }
            
            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    return LocalDate.parse(string, DATE_FORMAT);
                } else {
                    return null;
                }
            }
        });
        
        // Apply style to ensure black text in the calendar popup
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setStyle("-fx-text-fill: black; -fx-font-family: Arial;");
            }
        });
    }
    
    /**
     * Show import dialog for CSV/XLSX files
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Company Expenses");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (selectedFile != null) {
            showImportProgressDialog(selectedFile);
        }
    }
    
    /**
     * Show import progress dialog with better error handling
     */
    private void showImportProgressDialog(File selectedFile) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Importing Expenses");
        progressDialog.setHeaderText("Processing " + selectedFile.getName());
        
        // Create progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressIndicator.setPrefSize(50, 50);
        
        // Create status label
        Label statusLabel = new Label("Validating file...");
        statusLabel.setStyle("-fx-font-weight: bold;");
        
        VBox content = new VBox(15, progressIndicator, statusLabel);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(content);
        progressDialog.getDialogPane().getButtonTypes().clear();
        
        // Add cancel button
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> progressDialog.close());
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        
        Task<ImportResult> importTask = new Task<ImportResult>() {
            @Override
            protected ImportResult call() throws Exception {
                updateMessage("Reading file...");
                Thread.sleep(100); // Give UI time to update
                
                updateMessage("Validating headers...");
                Thread.sleep(100);
                
                updateMessage("Processing data...");
                Thread.sleep(100);
                
                return importExpensesFromFile(selectedFile);
            }
        };
        
        // Update status label based on task message
        importTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null) {
                statusLabel.setText(newMsg);
            }
        });
        
        importTask.setOnSucceeded(event -> {
            progressDialog.close();
            ImportResult result = importTask.getValue();
            showImportResults(result, selectedFile.getName());
        });
        
        importTask.setOnFailed(event -> {
            progressDialog.close();
            Throwable exception = importTask.getException();
            logger.error("Import failed", exception);
            
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Import Failed");
            errorAlert.setHeaderText("Failed to import expenses");
            errorAlert.setContentText("Error: " + exception.getMessage());
            errorAlert.showAndWait();
        });
        
        importTask.setOnCancelled(event -> {
            progressDialog.close();
            showAlert(Alert.AlertType.INFORMATION, "Import Cancelled", 
                     "Import was cancelled by user.");
        });
        
        // Start the task
        new Thread(importTask).start();
        progressDialog.showAndWait();
    }
    
    /**
     * Import expenses from CSV/XLSX file
     */
    private ImportResult importExpensesFromFile(File file) throws Exception {
        ImportResult result = new ImportResult();
        
        try {
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".csv")) {
                importFromCSV(file, result);
            } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                importFromExcel(file, result);
            } else {
                throw new IllegalArgumentException("Unsupported file format. Please use CSV or Excel files.");
            }
        } catch (Exception e) {
            logger.error("Import failed", e);
            result.errors.add("Import failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Import from CSV file with better error handling
     */
    private void importFromCSV(File file, ImportResult result) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); // Read header
            if (line == null) {
                throw new IllegalArgumentException("File is empty or invalid");
            }
            
            // Validate header
            String[] headers = parseCSVLine(line);
            validateHeaders(headers);
            
            int lineNumber = 1;
            Set<String> existingReceiptNumbers = getExistingReceiptNumbers();
            int processedLines = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                processedLines++;
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] values = parseCSVLine(line);
                    
                    // Ensure we have enough columns
                    if (values.length < 10) {
                        result.errors.add("Line " + lineNumber + ": Expected 10 columns, found " + values.length + " - skipped");
                        continue;
                    }
                    
                    // Validate required fields
                    if (values[0].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Date is required - skipped");
                        continue;
                    }
                    
                    if (values[1].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Vendor is required - skipped");
                        continue;
                    }
                    
                    if (values[5].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Amount is required - skipped");
                        continue;
                    }
                    
                    CompanyExpense expense = createExpenseFromValues(values);
                    
                    // Check for duplicate receipt number
                    if (expense.getReceiptNumber() != null && !expense.getReceiptNumber().trim().isEmpty()) {
                        if (existingReceiptNumbers.contains(expense.getReceiptNumber().trim())) {
                            result.skipped++;
                            result.errors.add("Line " + lineNumber + ": Duplicate receipt number '" + 
                                           expense.getReceiptNumber() + "' - skipped");
                            continue;
                        }
                        existingReceiptNumbers.add(expense.getReceiptNumber().trim());
                    }
                    
                    expenseDAO.save(expense);
                    result.imported++;
                    result.totalFound++;
                    
                } catch (Exception e) {
                    result.errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
            
            if (processedLines == 0) {
                throw new IllegalArgumentException("No data rows found in file");
            }
        }
    }
    
    /**
     * Import from Excel file
     */
    private void importFromExcel(File file, ImportResult result) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("No data found in Excel file");
            }
            
            // Validate header
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("No header row found");
            }
            
            String[] headers = new String[headerRow.getLastCellNum()];
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers[i] = cell != null ? cell.toString().trim() : "";
            }
            validateHeaders(headers);
            
            Set<String> existingReceiptNumbers = getExistingReceiptNumbers();
            
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                
                try {
                    String[] values = new String[10];
                    for (int i = 0; i < 10 && i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i);
                        values[i] = cell != null ? cell.toString().trim() : "";
                    }
                    
                    CompanyExpense expense = createExpenseFromValues(values);
                    
                    // Check for duplicate receipt number
                    if (expense.getReceiptNumber() != null && !expense.getReceiptNumber().trim().isEmpty()) {
                        if (existingReceiptNumbers.contains(expense.getReceiptNumber().trim())) {
                            result.skipped++;
                            result.errors.add("Row " + (rowNum + 1) + ": Duplicate receipt number '" + 
                                           expense.getReceiptNumber() + "' - skipped");
                            continue;
                        }
                        existingReceiptNumbers.add(expense.getReceiptNumber().trim());
                    }
                    
                    expenseDAO.save(expense);
                    result.imported++;
                    result.totalFound++;
                } catch (Exception e) {
                    result.errors.add("Row " + (rowNum + 1) + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Validate CSV/Excel headers
     */
    private void validateHeaders(String[] headers) throws Exception {
        String[] expectedHeaders = {"Date", "Vendor", "Category", "Department", "Description", 
                                  "Amount", "Payment Method", "Receipt #", "Status", "Recurring"};
        
        if (headers.length < expectedHeaders.length) {
            throw new IllegalArgumentException("Invalid header format. Expected columns: " + 
                                           String.join(", ", expectedHeaders));
        }
        
        for (int i = 0; i < expectedHeaders.length; i++) {
            if (!headers[i].trim().equalsIgnoreCase(expectedHeaders[i])) {
                throw new IllegalArgumentException("Invalid header at column " + (i + 1) + 
                                               ". Expected: " + expectedHeaders[i] + 
                                               ", Found: " + headers[i]);
            }
        }
    }
    
    /**
     * Parse CSV line handling quoted values and edge cases
     */
    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        
        // Add the last value
        values.add(currentValue.toString().trim());
        
        // Ensure we have exactly 10 values
        while (values.size() < 10) {
            values.add("");
        }
        
        // Trim all values and handle nulls
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                values.set(i, "");
            } else {
                values.set(i, value.trim());
            }
        }
        
        return values.toArray(new String[0]);
    }
    
    /**
     * Create CompanyExpense from CSV/Excel values
     */
    private CompanyExpense createExpenseFromValues(String[] values) throws Exception {
        CompanyExpense expense = new CompanyExpense();
        
        // Date
        try {
            String dateStr = values[0].trim();
            if (!dateStr.isEmpty()) {
                LocalDate date = parseDateFlexible(dateStr);
                expense.setExpenseDate(date);
            } else {
                expense.setExpenseDate(LocalDate.now());
            }
        } catch (Exception e) {
            throw new Exception("Invalid date format: " + values[0] + ". Expected MM/dd/yyyy, MM-dd-yyyy, or MM/dd/yy");
        }
        
        // Vendor
        expense.setVendor(values[1].trim());
        
        // Category
        String category = values[2].trim();
        if (!expenseCategories.contains(category)) {
            category = "Other";
        }
        expense.setCategory(category);
        
        // Department
        String department = values[3].trim();
        if (!departments.contains(department)) {
            department = "Operations";
        }
        expense.setDepartment(department);
        
        // Description
        expense.setDescription(values[4].trim());
        
        // Amount
        try {
            String amountStr = values[5].trim();
            // Remove currency symbols, commas, and spaces
            amountStr = amountStr.replaceAll("[$,€£¥\\s]", "");
            // Handle negative amounts in parentheses
            if (amountStr.startsWith("(") && amountStr.endsWith(")")) {
                amountStr = "-" + amountStr.substring(1, amountStr.length() - 1);
            }
            expense.setAmount(Double.parseDouble(amountStr));
        } catch (NumberFormatException e) {
            throw new Exception("Invalid amount: " + values[5]);
        }
        
        // Payment Method
        String paymentMethod = values[6].trim();
        if (!paymentMethods.contains(paymentMethod)) {
            paymentMethod = "Other";
        }
        expense.setPaymentMethod(paymentMethod);
        
        // Receipt Number
        expense.setReceiptNumber(values[7].trim());
        
        // Status
        String status = values[8].trim();
        if (status.isEmpty()) {
            status = "Pending";
        }
        expense.setStatus(status);
        
        // Recurring
        String recurringStr = values[9].trim().toLowerCase();
        expense.setRecurring(recurringStr.equals("yes") || recurringStr.equals("true") || 
                           recurringStr.equals("1") || recurringStr.equals("y"));
        
        return expense;
    }
    
    /**
     * Get existing receipt numbers to prevent duplicates
     */
    private Set<String> getExistingReceiptNumbers() {
        Set<String> receiptNumbers = new HashSet<>();
        try {
            List<CompanyExpense> existingExpenses = expenseDAO.getAll();
            for (CompanyExpense expense : existingExpenses) {
                if (expense.getReceiptNumber() != null && !expense.getReceiptNumber().trim().isEmpty()) {
                    receiptNumbers.add(expense.getReceiptNumber().trim());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get existing receipt numbers", e);
        }
        return receiptNumbers;
    }
    
    /**
     * Show import results dialog
     */
    private void showImportResults(ImportResult result, String fileName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Results");
        alert.setHeaderText("Import completed for " + fileName);
        
        StringBuilder content = new StringBuilder();
        content.append("Total records found: ").append(result.totalFound).append("\n");
        content.append("Successfully imported: ").append(result.imported).append("\n");
        content.append("Skipped (duplicates): ").append(result.skipped).append("\n");
        
        if (!result.errors.isEmpty()) {
            content.append("\nErrors:\n");
            for (String error : result.errors) {
                content.append("• ").append(error).append("\n");
            }
        }
        
        alert.setContentText(content.toString());
        
        // Add scrollable text area for errors if there are many
        if (result.errors.size() > 10) {
            TextArea errorArea = new TextArea(content.toString());
            errorArea.setEditable(false);
            errorArea.setPrefRowCount(15);
            errorArea.setPrefColumnCount(80);
            alert.getDialogPane().setContent(errorArea);
        }
        
        alert.showAndWait();
        
        // Refresh data after import
        loadData();
    }
    
    /**
     * Parse date with multiple format support
     */
    private LocalDate parseDateFlexible(String dateStr) throws Exception {
        String[] patterns = {
            "MM/dd/yyyy",
            "MM-dd-yyyy", 
            "MM/dd/yy",
            "MM-dd-yy",
            "M/d/yyyy",
            "M-d-yyyy",
            "M/d/yy",
            "M-d-yy"
        };
        
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception e) {
                // Continue to next pattern
            }
        }
        
        throw new Exception("Unable to parse date: " + dateStr);
    }
    
    /**
     * Import result class
     */
    private static class ImportResult {
        int totalFound = 0;
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
    }
}