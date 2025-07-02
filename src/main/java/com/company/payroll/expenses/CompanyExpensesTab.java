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
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Company Expenses Tab for tracking all business expenses in the trucking fleet management system
 */
public class CompanyExpensesTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(CompanyExpensesTab.class);
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
    
    // Document storage path
    private final String docStoragePath = "expense_documents";
    
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
        
        Label titleLabel = new Label("Company Expense Management");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Track and manage all business expenses for your trucking company");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        VBox titleBox = new VBox(5, titleLabel, subtitleLabel);
        
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
        
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        endDatePicker = new DatePicker(LocalDate.now());
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-base: #3498db;");
        refreshButton.setOnAction(e -> loadData());
        
        Button addExpenseButton = new Button("Add Expense");
        addExpenseButton.setStyle("-fx-base: #27ae60;");
        addExpenseButton.setOnAction(e -> showAddExpenseDialog());
        
        Button documentManagerButton = new Button("Document Manager");
        documentManagerButton.setStyle("-fx-base: #9b59b6;");
        documentManagerButton.setOnAction(e -> showDocumentManager());
        
        Button generateReportButton = new Button("Generate Report");
        generateReportButton.setStyle("-fx-base: #e74c3c;");
        generateReportButton.setOnAction(e -> generateReport());
        
        Button exportButton = new Button("Export");
        exportButton.setStyle("-fx-base: #f39c12;");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        Button budgetComparisonButton = new Button("Budget Analysis");
        budgetComparisonButton.setStyle("-fx-base: #1abc9c;");
        budgetComparisonButton.setOnAction(e -> showBudgetComparison());
        
        actionRow.getChildren().addAll(
            new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker,
            new Separator(Orientation.VERTICAL),
            refreshButton, addExpenseButton, documentManagerButton,
            generateReportButton, exportButton, budgetComparisonButton
        );
        
        controlPanel.getChildren().addAll(searchRow, new Separator(), actionRow);
        
        // Add listeners for filtering
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        categoryFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        departmentFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        paymentMethodFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        
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
        
        // Vendors Tab
        Tab vendorsTab = new Tab("Vendors");
        vendorsTab.setContent(createVendorsSection());
        
        tabPane.getTabs().addAll(historyTab, analyticsTab, vendorsTab);
        
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
        
        Button uploadDocButton = new Button("Upload Receipt");
        uploadDocButton.setStyle("-fx-base: #3498db;");
        uploadDocButton.setOnAction(e -> uploadDocument());
        
        Button printDocButton = new Button("Print Receipt");
        printDocButton.setStyle("-fx-base: #27ae60;");
        printDocButton.setOnAction(e -> printSelectedDocument());
        
        Button editButton = new Button("Edit");
        editButton.setStyle("-fx-base: #f39c12;");
        editButton.setOnAction(e -> editSelectedExpense());
        
        Button deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-base: #e74c3c;");
        deleteButton.setOnAction(e -> deleteSelectedExpense());
        
        Button approveButton = new Button("Approve");
        approveButton.setStyle("-fx-base: #1abc9c;");
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
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Expense by Category Chart
        VBox categoryChartBox = new VBox(10);
        Label categoryLabel = new Label("Expenses by Category");
        categoryLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        expenseByCategoryChart = new PieChart();
        expenseByCategoryChart.setTitle("Category Distribution");
        expenseByCategoryChart.setPrefHeight(300);
        expenseByCategoryChart.setAnimated(true);
        
        categoryChartBox.getChildren().addAll(categoryLabel, expenseByCategoryChart);
        
        // Monthly Trend Chart
        VBox monthlyChartBox = new VBox(10);
        Label monthlyLabel = new Label("Monthly Expense Trend");
        monthlyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Amount ($)");
        
        monthlyTrendChart = new BarChart<>(xAxis, yAxis);
        monthlyTrendChart.setTitle("12-Month Trend");
        monthlyTrendChart.setPrefHeight(300);
        monthlyTrendChart.setAnimated(true);
        monthlyTrendChart.setLegendVisible(false);
        
        monthlyChartBox.getChildren().addAll(monthlyLabel, monthlyTrendChart);
        
        // Payment Method Chart
        VBox paymentChartBox = new VBox(10);
        Label paymentLabel = new Label("Payment Method Distribution");
        paymentLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        paymentMethodChart = new PieChart();
        paymentMethodChart.setTitle("Payment Methods");
        paymentMethodChart.setPrefHeight(300);
        paymentMethodChart.setAnimated(true);
        
        paymentChartBox.getChildren().addAll(paymentLabel, paymentMethodChart);
        
        // Year-over-Year Comparison
        VBox yoyChartBox = new VBox(10);
        Label yoyLabel = new Label("Year-over-Year Comparison");
        yoyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        CategoryAxis yoyXAxis = new CategoryAxis();
        NumberAxis yoyYAxis = new NumberAxis();
        yoyYAxis.setLabel("Amount ($)");
        
        yearOverYearChart = new LineChart<>(yoyXAxis, yoyYAxis);
        yearOverYearChart.setTitle("YoY Expense Trend");
        yearOverYearChart.setPrefHeight(300);
        yearOverYearChart.setAnimated(true);
        
        yoyChartBox.getChildren().addAll(yoyLabel, yearOverYearChart);
        
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
    
    private VBox createVendorsSection() {
        VBox vendorsSection = new VBox(15);
        vendorsSection.setPadding(new Insets(15));
        
        Label vendorTitle = new Label("Top Vendors by Expense");
        vendorTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        TableView<VendorSummary> vendorTable = new TableView<>();
        vendorTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        vendorTable.setPrefHeight(300);
        
        TableColumn<VendorSummary, String> vendorNameCol = new TableColumn<>("Vendor Name");
        vendorNameCol.setCellValueFactory(new PropertyValueFactory<>("vendorName"));
        
        TableColumn<VendorSummary, Integer> transactionCountCol = new TableColumn<>("# Transactions");
        transactionCountCol.setCellValueFactory(new PropertyValueFactory<>("transactionCount"));
        
        TableColumn<VendorSummary, Double> totalAmountCol = new TableColumn<>("Total Amount");
        totalAmountCol.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        totalAmountCol.setCellFactory(column -> new TableCell<VendorSummary, Double>() {
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
        
        TableColumn<VendorSummary, Double> avgAmountCol = new TableColumn<>("Average Amount");
        avgAmountCol.setCellValueFactory(new PropertyValueFactory<>("averageAmount"));
        avgAmountCol.setCellFactory(column -> new TableCell<VendorSummary, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                }
            }
        });
        
        TableColumn<VendorSummary, String> lastTransactionCol = new TableColumn<>("Last Transaction");
        lastTransactionCol.setCellValueFactory(new PropertyValueFactory<>("lastTransactionDate"));
        
        vendorTable.getColumns().setAll(java.util.List.of(
                vendorNameCol, transactionCountCol,
                totalAmountCol, avgAmountCol, lastTransactionCol));
        
        // Vendor management buttons
        HBox vendorButtons = new HBox(10);
        vendorButtons.setAlignment(Pos.CENTER_RIGHT);
        
        Button addVendorButton = new Button("Add Vendor");
        addVendorButton.setStyle("-fx-base: #27ae60;");
        
        Button editVendorButton = new Button("Edit Vendor");
        editVendorButton.setStyle("-fx-base: #f39c12;");
        
        Button vendorReportButton = new Button("Vendor Report");
        vendorReportButton.setStyle("-fx-base: #3498db;");
        vendorReportButton.setOnAction(e -> generateVendorReport());
        
        vendorButtons.getChildren().addAll(addVendorButton, editVendorButton, vendorReportButton);
        
        // Vendor chart
        BarChart<String, Number> topVendorsChart = createTopVendorsChart();
        
        vendorsSection.getChildren().addAll(vendorTitle, vendorTable, vendorButtons, 
                                           new Separator(), topVendorsChart);
        
        return vendorsSection;
    }
    
    private VBox createTopCategoriesBox() {
        VBox topCategoriesBox = new VBox(10);
        topCategoriesBox.setPadding(new Insets(15));
        topCategoriesBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label title = new Label("Top 5 Expense Categories");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        ListView<String> categoriesList = new ListView<>();
        categoriesList.setPrefHeight(200);
        categoriesList.setPlaceholder(new Label("No data available"));
        
        topCategoriesBox.getChildren().addAll(title, categoriesList);
        
        return topCategoriesBox;
    }
    
    private VBox createDepartmentBreakdownBox() {
        VBox departmentBox = new VBox(10);
        departmentBox.setPadding(new Insets(15));
        departmentBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label title = new Label("Department Expense Breakdown");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        ListView<String> departmentList = new ListView<>();
        departmentList.setPrefHeight(200);
        departmentList.setPlaceholder(new Label("No data available"));
        
        departmentBox.getChildren().addAll(title, departmentList);
        
        return departmentBox;
    }
    
    private BarChart<String, Number> createTopVendorsChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Total Amount ($)");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Top 10 Vendors by Total Expense");
        chart.setPrefHeight(300);
        chart.setAnimated(true);
        chart.setLegendVisible(false);
        
        return chart;
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
        Map<String, Double> monthlyTotals = visibleExpenses.stream()
            .collect(Collectors.groupingBy(
                expense -> expense.getExpenseDate().format(DateTimeFormatter.ofPattern("MMM yyyy")),
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        XYChart.Series<String, Number> monthlySeries = new XYChart.Series<>();
        monthlyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                monthlySeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            });
        
        monthlyTrendChart.getData().clear();
        monthlyTrendChart.getData().add(monthlySeries);
        
        // Update payment method chart
        Map<String, Double> paymentMethodTotals = visibleExpenses.stream()
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
        dialog.setTitle("Document Manager - " + selected.getVendor() + " - " + 
                       selected.getExpenseDate().format(DATE_FORMAT));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        ListView<String> docListView = new ListView<>();
        updateDocumentList(docListView, selected.getId());
        
        Button uploadBtn = new Button("Upload");
        Button viewBtn = new Button("View");
        Button printBtn = new Button("Print");
        Button deleteBtn = new Button("Delete");
        
        uploadBtn.setOnAction(e -> {
            uploadDocumentForExpense(selected.getId());
            updateDocumentList(docListView, selected.getId());
        });
        
        viewBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                viewDocument(selected.getId(), selectedDoc);
            }
        });
        
        printBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                printDocument(selected.getId(), selectedDoc);
            }
        });
        
        deleteBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                deleteDocument(selected.getId(), selectedDoc);
                updateDocumentList(docListView, selected.getId());
            }
        });
        
        HBox buttonBox = new HBox(10, uploadBtn, viewBtn, printBtn, deleteBtn);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        VBox content = new VBox(10, 
                               new Label("Documents for this expense"), 
                               docListView, 
                               buttonBox);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setPrefHeight(400);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
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
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Document to Upload");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        File selectedFile = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (selectedFile != null) {
            try {
                Path expenseDir = Paths.get(docStoragePath, String.valueOf(expenseId));
                Files.createDirectories(expenseDir);
                
                String timestamp = String.valueOf(System.currentTimeMillis());
                String extension = selectedFile.getName().substring(
                        selectedFile.getName().lastIndexOf('.'));
                String newFileName = "receipt_" + timestamp + extension;
                
                Path destPath = expenseDir.resolve(newFileName);
                Files.copy(selectedFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                
                logger.info("Uploaded document for expense {}: {}", expenseId, newFileName);
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         "Document uploaded successfully");
                
            } catch (Exception e) {
                logger.error("Failed to upload document", e);
                showAlert(Alert.AlertType.ERROR, "Error", 
                         "Failed to upload document: " + e.getMessage());
            }
        }
    }
    
    private void updateDocumentList(ListView<String> listView, int expenseId) {
        try {
            Path expenseDir = Paths.get(docStoragePath, String.valueOf(expenseId));
            if (Files.exists(expenseDir)) {
                List<String> files = Files.list(expenseDir)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
                listView.setItems(FXCollections.observableArrayList(files));
            } else {
                listView.setItems(FXCollections.observableArrayList());
            }
        } catch (Exception e) {
            logger.error("Failed to list documents", e);
            listView.setItems(FXCollections.observableArrayList());
        }
    }
    
    private void viewDocument(int expenseId, String document) {
        try {
            Path expenseDir = Paths.get(docStoragePath, String.valueOf(expenseId));
            
            if (document == null && Files.exists(expenseDir)) {
                // Try to find the first document
                List<Path> docs = Files.list(expenseDir).collect(Collectors.toList());
                if (!docs.isEmpty()) {
                    document = docs.get(0).getFileName().toString();
                }
            }
            
            if (document != null) {
                Path docPath = expenseDir.resolve(document);
                File file = docPath.toFile();
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(file);
                } else {
                    showAlert(Alert.AlertType.INFORMATION, "Cannot Open", 
                             "Cannot open file. File is saved at: " + docPath);
                }
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Document", 
                         "No receipt/document attached to this expense");
            }
        } catch (Exception e) {
            logger.error("Failed to open document", e);
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to open document: " + e.getMessage());
        }
    }
    
    private void printDocument(int expenseId, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, String.valueOf(expenseId), document);
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
                    Path docPath = Paths.get(docStoragePath, String.valueOf(expenseId), document);
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
    
    private void generateVendorReport() {
        logger.info("Generating vendor report");
        
        List<CompanyExpense> expenses = allExpenses;
        Map<String, List<CompanyExpense>> vendorExpenses = expenses.stream()
            .collect(Collectors.groupingBy(CompanyExpense::getVendor));
        
        StringBuilder report = new StringBuilder();
        report.append("VENDOR EXPENSE REPORT\n");
        report.append("====================\n\n");
        report.append("Report Date: ").append(LocalDate.now().format(DATE_FORMAT)).append("\n\n");
        
        vendorExpenses.entrySet().stream()
            .sorted((e1, e2) -> {
                double total1 = e1.getValue().stream().mapToDouble(CompanyExpense::getAmount).sum();
                double total2 = e2.getValue().stream().mapToDouble(CompanyExpense::getAmount).sum();
                return Double.compare(total2, total1);
            })
            .limit(20)
            .forEach(entry -> {
                String vendor = entry.getKey();
                List<CompanyExpense> vendorExps = entry.getValue();
                double total = vendorExps.stream().mapToDouble(CompanyExpense::getAmount).sum();
                double avg = total / vendorExps.size();
                
                report.append("VENDOR: ").append(vendor).append("\n");
                report.append("Total Expenses: ").append(CURRENCY_FORMAT.format(total)).append("\n");
                report.append("Number of Transactions: ").append(vendorExps.size()).append("\n");
                report.append("Average Transaction: ").append(CURRENCY_FORMAT.format(avg)).append("\n");
                
                // Category breakdown
                Map<String, Double> categories = vendorExps.stream()
                    .collect(Collectors.groupingBy(
                        CompanyExpense::getCategory,
                        Collectors.summingDouble(CompanyExpense::getAmount)
                    ));
                
                report.append("Categories:\n");
                categories.forEach((cat, amt) -> {
                    report.append("  - ").append(cat).append(": ")
                          .append(CURRENCY_FORMAT.format(amt)).append("\n");
                });
                
                report.append("\n");
            });
        
        // Show report
        TextArea reportArea = new TextArea(report.toString());
        reportArea.setEditable(false);
        reportArea.setFont(Font.font("Courier New", 12));
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Vendor Report");
        dialog.getDialogPane().setContent(reportArea);
        dialog.getDialogPane().setPrefSize(600, 500);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.showAndWait();
    }
    
    private void showBudgetComparison() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Budget Analysis");
        dialog.setHeaderText("Compare actual expenses to budget");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Mock budget data (in real app, this would come from database)
        Map<String, Double> budgets = new HashMap<>();
        budgets.put("Fuel", 50000.0);
        budgets.put("Insurance", 25000.0);
        budgets.put("Permits & Licenses", 10000.0);
        budgets.put("Office Supplies", 5000.0);
        budgets.put("Professional Services", 15000.0);
        
        Map<String, Double> actuals = filteredExpenses.stream()
            .collect(Collectors.groupingBy(
                CompanyExpense::getCategory,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        int row = 0;
        grid.add(new Label("Category"), 0, row);
        grid.add(new Label("Budget"), 1, row);
        grid.add(new Label("Actual"), 2, row);
        grid.add(new Label("Variance"), 3, row);
        grid.add(new Label("% Used"), 4, row);
        row++;
        
        grid.add(new Separator(), 0, row++, 5, 1);
        
        for (String category : budgets.keySet()) {
            double budget = budgets.get(category);
            double actual = actuals.getOrDefault(category, 0.0);
            double variance = budget - actual;
            double percentUsed = (actual / budget) * 100;
            
            grid.add(new Label(category), 0, row);
            grid.add(new Label(CURRENCY_FORMAT.format(budget)), 1, row);
            
            Label actualLabel = new Label(CURRENCY_FORMAT.format(actual));
            if (actual > budget) {
                actualLabel.setTextFill(Color.RED);
            }
            grid.add(actualLabel, 2, row);
            
            Label varianceLabel = new Label(CURRENCY_FORMAT.format(variance));
            if (variance < 0) {
                varianceLabel.setTextFill(Color.RED);
            } else {
                varianceLabel.setTextFill(Color.GREEN);
            }
            grid.add(varianceLabel, 3, row);
            
            ProgressBar progressBar = new ProgressBar(percentUsed / 100);
            progressBar.setPrefWidth(100);
            if (percentUsed > 90) {
                progressBar.setStyle("-fx-accent: red;");
            } else if (percentUsed > 75) {
                progressBar.setStyle("-fx-accent: orange;");
            } else {
                progressBar.setStyle("-fx-accent: green;");
            }
            
            HBox percentBox = new HBox(5);
            percentBox.setAlignment(Pos.CENTER_LEFT);
            percentBox.getChildren().addAll(progressBar, 
                                           new Label(String.format("%.1f%%", percentUsed)));
            grid.add(percentBox, 4, row);
            
            row++;
        }
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
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
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("company_expense_report_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // Note: In a real implementation, you would use Apache PDFBox or iText here
            showAlert(Alert.AlertType.INFORMATION, "Export to PDF", 
                     "PDF export functionality would be implemented here using Apache PDFBox");
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    // Inner class for vendor summary
    public static class VendorSummary {
        private final String vendorName;
        private final int transactionCount;
        private final double totalAmount;
        private final double averageAmount;
        private final String lastTransactionDate;
        
        public VendorSummary(String vendorName, int transactionCount, double totalAmount, 
                           double averageAmount, String lastTransactionDate) {
            this.vendorName = vendorName;
            this.transactionCount = transactionCount;
            this.totalAmount = totalAmount;
            this.averageAmount = averageAmount;
            this.lastTransactionDate = lastTransactionDate;
        }
        
        // Getters
        public String getVendorName() { return vendorName; }
        public int getTransactionCount() { return transactionCount; }
        public double getTotalAmount() { return totalAmount; }
        public double getAverageAmount() { return averageAmount; }
        public String getLastTransactionDate() { return lastTransactionDate; }
    }
}