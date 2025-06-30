package com.company.payroll.expenses;

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
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.util.StringConverter;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import com.company.payroll.expenses.CompanyExpenseDAO;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

public class CompanyExpensesTab extends Tab {
    private TableView<CompanyExpense> expenseTable;
    private ComboBox<String> categoryComboBox;
    private ComboBox<String> vendorComboBox;
    private ComboBox<String> periodComboBox;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private TextField searchField;
    
    private Label totalExpensesLabel;
    private Label monthlyAverageLabel;
    private Label largestExpenseLabel;
    private Label pendingApprovalLabel;
    
    private PieChart categoryChart;
    private LineChart<String, Number> trendChart;
    private BarChart<String, Number> vendorChart;
    private AreaChart<String, Number> comparisonChart;
    
    private ProgressIndicator loadingIndicator;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private ObservableList<String> expenseCategories = FXCollections.observableArrayList(
        "Fuel", "Maintenance", "Insurance", "Office Supplies", "Utilities",
        "Marketing", "Payroll", "Equipment", "Rent", "Professional Services",
        "Travel", "Software", "Hardware", "Other"
    );

    private final CompanyExpenseDAO expenseDAO = new CompanyExpenseDAO();
    
    public CompanyExpensesTab() {
        setText("Company Expenses");
        setClosable(false);
        
        // Set tab icon
        try {
            ImageView tabIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/expenses.png")));
            tabIcon.setFitHeight(16);
            tabIcon.setFitWidth(16);
            setGraphic(tabIcon);
        } catch (Exception e) {
            // Icon not found
        }
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: #f8f9fa;");
        
        // Header
        VBox header = createHeader();
        
        // Control Panel
        VBox controlPanel = createControlPanel();
        
        // Summary Cards
        HBox summaryCards = createSummaryCards();
        
        // Main Content with TabPane
        TabPane contentTabs = createContentTabs();
        
        // Loading Indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        StackPane loadingPane = new StackPane(loadingIndicator);
        loadingPane.setAlignment(Pos.CENTER);
        
        mainContent.getChildren().addAll(header, controlPanel, summaryCards, contentTabs, loadingPane);
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
        
        // Initialize data
        initializeData();
        loadExpenseData();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(25));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: linear-gradient(to right, #e74c3c, #c0392b); " +
                       "-fx-background-radius: 10;");
        
        Label titleLabel = new Label("Company Expense Tracker");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Monitor and control your business expenses");
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
    
    private VBox createControlPanel() {
        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Search and Filter Row
        HBox searchRow = new HBox(15);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        
        // Search Field
        searchField = new TextField();
        searchField.setPromptText("Search expenses...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-background-radius: 20;");
        
        // Category Filter
        Label categoryLabel = new Label("Category:");
        categoryLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().add("All Categories");
        categoryComboBox.getItems().addAll(expenseCategories);
        categoryComboBox.setValue("All Categories");
        categoryComboBox.setPrefWidth(150);
        
        // Vendor Filter
        Label vendorLabel = new Label("Vendor:");
        vendorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        vendorComboBox = new ComboBox<>();
        vendorComboBox.setPromptText("All Vendors");
        vendorComboBox.setPrefWidth(150);
        
        searchRow.getChildren().addAll(searchField, new Separator(Orientation.VERTICAL),
                                      categoryLabel, categoryComboBox,
                                      vendorLabel, vendorComboBox);
        
        // Date Range Row
        HBox dateRow = new HBox(15);
        dateRow.setAlignment(Pos.CENTER_LEFT);
        
        Label periodLabel = new Label("Period:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        periodComboBox = new ComboBox<>();
        periodComboBox.getItems().addAll("This Month", "Last Month", "This Quarter", 
                                       "Last Quarter", "This Year", "Custom Range");
        periodComboBox.setValue("This Month");
        periodComboBox.setPrefWidth(150);
        
        startDatePicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        endDatePicker = new DatePicker(LocalDate.now());
        HBox dateRange = new HBox(5);
        dateRange.getChildren().addAll(new Label("From:"), startDatePicker, 
                                     new Label("To:"), endDatePicker);
        dateRange.setVisible(false);
        
        periodComboBox.setOnAction(e -> {
            dateRange.setVisible("Custom Range".equals(periodComboBox.getValue()));
            updateDateRange();
        });
        
        dateRow.getChildren().addAll(periodLabel, periodComboBox, dateRange);
        
        // Action Buttons Row
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        
        Button refreshButton = createStyledButton("Refresh", "#3498db", "refresh-icon.png");
        refreshButton.setOnAction(e -> loadExpenseData());
        
        Button addExpenseButton = createStyledButton("Add Expense", "#27ae60", "add-icon.png");
        addExpenseButton.setOnAction(e -> showAddExpenseDialog());
        
        Button importButton = createStyledButton("Import", "#f39c12", "import-icon.png");
        importButton.setOnAction(e -> importExpenses());
        
        Button exportButton = createStyledButton("Export", "#9b59b6", "export-icon.png");
        exportButton.setOnAction(e -> showExportMenu(exportButton));
        
        buttonRow.getChildren().addAll(refreshButton, addExpenseButton, importButton, exportButton);
        
        controlPanel.getChildren().addAll(searchRow, dateRow, new Separator(), buttonRow);
        
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
        
        VBox totalCard = createSummaryCard("Total Expenses", "$0.00", "#e74c3c", "total-icon.png");
        totalExpensesLabel = (Label) totalCard.lookup(".value-label");
        
        VBox avgCard = createSummaryCard("Monthly Average", "$0.00", "#3498db", "average-icon.png");
        monthlyAverageLabel = (Label) avgCard.lookup(".value-label");
        
        VBox largestCard = createSummaryCard("Largest Expense", "$0.00", "#f39c12", "largest-icon.png");
        largestExpenseLabel = (Label) largestCard.lookup(".value-label");
        
        VBox pendingCard = createSummaryCard("Pending Approval", "0", "#9b59b6", "pending-icon.png");
        pendingApprovalLabel = (Label) pendingCard.lookup(".value-label");
        
        summaryCards.getChildren().addAll(totalCard, avgCard, largestCard, pendingCard);
        
        return summaryCards;
    }
    
    private VBox createSummaryCard(String title, String value, String color, String iconPath) {
        VBox card = new VBox(10);
        card.getStyleClass().add("summary-card");
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        card.setPrefWidth(250);
        card.setPrefHeight(120);
        
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
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        valueLabel.setTextFill(Color.web(color));
        
        // Progress indicator for pending approvals
        if (title.contains("Pending")) {
            ProgressBar progressBar = new ProgressBar(0.3);
            progressBar.setPrefWidth(200);
            progressBar.setStyle("-fx-accent: " + color);
            card.getChildren().addAll(header, valueLabel, progressBar);
        } else {
            card.getChildren().addAll(header, valueLabel);
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
        
        Tab expensesTab = new Tab("Expense List");
        expensesTab.setClosable(false);
        expensesTab.setContent(createExpenseTableSection());
        
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setClosable(false);
        analyticsTab.setContent(createAnalyticsSection());
        
        Tab budgetTab = new Tab("Budget");
        budgetTab.setClosable(false);
        budgetTab.setContent(createBudgetSection());
        
        Tab approvalTab = new Tab("Approvals");
        approvalTab.setClosable(false);
        approvalTab.setContent(createApprovalSection());
        
        tabPane.getTabs().addAll(expensesTab, analyticsTab, budgetTab, approvalTab);
        
        return tabPane;
    }
    
    private VBox createExpenseTableSection() {
        VBox tableSection = new VBox(15);
        tableSection.setPadding(new Insets(15));
        
        // Table Controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        
        CheckBox selectAllCheckBox = new CheckBox("Select All");
        Button deleteButton = new Button("Delete Selected");
        deleteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        deleteButton.setDisable(true);
        
        Button approveButton = new Button("Approve Selected");
        approveButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        approveButton.setDisable(true);
        
        tableControls.getChildren().addAll(selectAllCheckBox, deleteButton, approveButton);
        
        // Expense Table
        expenseTable = new TableView<>();
        expenseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        expenseTable.setPrefHeight(500);
        expenseTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Checkbox column
        TableColumn<CompanyExpense, Boolean> selectCol = new TableColumn<>();
        selectCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectCol.setCellFactory(column -> new CheckBoxTableCell<>());
        selectCol.setMaxWidth(50);
        
        TableColumn<CompanyExpense, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
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
        
        TableColumn<CompanyExpense, String> vendorCol = new TableColumn<>("Vendor");
        vendorCol.setCellValueFactory(new PropertyValueFactory<>("vendor"));
        vendorCol.setMinWidth(150);
        
        TableColumn<CompanyExpense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setCellFactory(column -> new TableCell<CompanyExpense, String>() {
            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(category);
                    label.setPadding(new Insets(5, 10, 5, 10));
                    label.setStyle(getCategoryStyle(category));
                    setGraphic(label);
                }
            }
        });
        
        TableColumn<CompanyExpense, String> descriptionCol = new TableColumn<>("Description");
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setMinWidth(200);
        
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
                    statusLabel.setPadding(new Insets(5, 10, 5, 10));
                    statusLabel.setStyle(getStatusStyle(status));
                    setGraphic(statusLabel);
                }
            }
        });
        
        TableColumn<CompanyExpense, String> receiptCol = new TableColumn<>("Receipt");
        receiptCol.setCellValueFactory(new PropertyValueFactory<>("hasReceipt"));
        receiptCol.setCellFactory(column -> new TableCell<CompanyExpense, String>() {
            @Override
            protected void updateItem(String hasReceipt, boolean empty) {
                super.updateItem(hasReceipt, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Button viewButton = new Button("View");
                    viewButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                                      "-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
                    viewButton.setOnAction(e -> viewReceipt());
                    setGraphic(viewButton);
                }
            }
        });
        
        expenseTable.getColumns().addAll(selectCol, dateCol, vendorCol, categoryCol, 
                                       descriptionCol, amountCol, statusCol, receiptCol);
        
        // Enable inline editing
        expenseTable.setEditable(true);
        vendorCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descriptionCol.setCellFactory(TextFieldTableCell.forTableColumn());
        
        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit");
        MenuItem duplicateItem = new MenuItem("Duplicate");
        MenuItem deleteItem = new MenuItem("Delete");
        MenuItem viewReceiptItem = new MenuItem("View Receipt");
        MenuItem attachReceiptItem = new MenuItem("Attach Receipt");
        contextMenu.getItems().addAll(editItem, duplicateItem, deleteItem,
                                    new SeparatorMenuItem(), viewReceiptItem, attachReceiptItem);
        expenseTable.setContextMenu(contextMenu);
        
        // Keyboard shortcuts
        expenseTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                deleteSelectedExpenses();
            } else if (event.isControlDown() && event.getCode() == KeyCode.N) {
                showAddExpenseDialog();
            }
        });
        
        tableSection.getChildren().addAll(tableControls, expenseTable);
        
        return tableSection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(20));
        
        // Charts Grid
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Category Breakdown Chart
        VBox categoryChartBox = new VBox(10);
        Label categoryLabel = new Label("Expense by Category");
        categoryLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        categoryChart = new PieChart();
        categoryChart.setTitle("Category Distribution");
        categoryChart.setPrefHeight(350);
        categoryChart.setAnimated(true);
        categoryChart.setLabelsVisible(true);
        
        categoryChartBox.getChildren().addAll(categoryLabel, categoryChart);
        
        // Trend Chart
        VBox trendChartBox = new VBox(10);
        Label trendLabel = new Label("Monthly Expense Trend");
        trendLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Amount ($)");
        
        trendChart = new LineChart<>(xAxis, yAxis);
        trendChart.setTitle("12-Month Trend");
        trendChart.setPrefHeight(350);
        trendChart.setCreateSymbols(true);
        trendChart.setAnimated(true);
        
        trendChartBox.getChildren().addAll(trendLabel, trendChart);
        
        // Vendor Chart
        VBox vendorChartBox = new VBox(10);
        Label vendorLabel = new Label("Top Vendors");
        vendorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis vendorXAxis = new CategoryAxis();
        NumberAxis vendorYAxis = new NumberAxis();
        vendorChart = new BarChart<>(vendorXAxis, vendorYAxis);
        vendorChart.setTitle("Top 10 Vendors by Spending");
        vendorChart.setPrefHeight(350);
        vendorChart.setAnimated(true);
        vendorChart.setLegendVisible(false);
        
        vendorChartBox.getChildren().addAll(vendorLabel, vendorChart);
        
        // Year-over-Year Comparison
        VBox comparisonChartBox = new VBox(10);
        Label comparisonLabel = new Label("Year-over-Year Comparison");
        comparisonLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        CategoryAxis compXAxis = new CategoryAxis();
        NumberAxis compYAxis = new NumberAxis();
        comparisonChart = new AreaChart<>(compXAxis, compYAxis);
        comparisonChart.setTitle("YoY Expense Comparison");
        comparisonChart.setPrefHeight(350);
        comparisonChart.setAnimated(true);
        
        comparisonChartBox.getChildren().addAll(comparisonLabel, comparisonChart);
        
        // Add charts to grid
        chartsGrid.add(categoryChartBox, 0, 0);
        chartsGrid.add(trendChartBox, 1, 0);
        chartsGrid.add(vendorChartBox, 0, 1);
        chartsGrid.add(comparisonChartBox, 1, 1);
        
        // Make charts responsive
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().addAll(col, col);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createBudgetSection() {
        VBox budgetSection = new VBox(20);
        budgetSection.setPadding(new Insets(20));
        budgetSection.setAlignment(Pos.TOP_CENTER);
        
        Label title = new Label("Budget Management");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Budget Overview Cards
        HBox budgetCards = new HBox(20);
        budgetCards.setAlignment(Pos.CENTER);
        budgetCards.setPadding(new Insets(20, 0, 20, 0));
        
        VBox totalBudgetCard = createBudgetCard("Total Budget", "$50,000", Color.web("#3498db"));
        VBox spentCard = createBudgetCard("Spent", "$32,500", Color.web("#e74c3c"));
        VBox remainingCard = createBudgetCard("Remaining", "$17,500", Color.web("#27ae60"));
        VBox projectionCard = createBudgetCard("Projected", "$48,000", Color.web("#f39c12"));
        
        budgetCards.getChildren().addAll(totalBudgetCard, spentCard, remainingCard, projectionCard);
        
        // Category Budget Table
        TableView<BudgetCategory> budgetTable = new TableView<>();
        budgetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        budgetTable.setPrefHeight(400);
        
        TableColumn<BudgetCategory, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        
        TableColumn<BudgetCategory, Double> budgetCol = new TableColumn<>("Budget");
        budgetCol.setCellValueFactory(new PropertyValueFactory<>("budget"));
        budgetCol.setCellFactory(column -> new TableCell<BudgetCategory, Double>() {
            @Override
            protected void updateItem(Double budget, boolean empty) {
                super.updateItem(budget, empty);
                if (empty || budget == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(budget));
                }
            }
        });
        
        TableColumn<BudgetCategory, Double> spentCol = new TableColumn<>("Spent");
        spentCol.setCellValueFactory(new PropertyValueFactory<>("spent"));
        spentCol.setCellFactory(column -> new TableCell<BudgetCategory, Double>() {
            @Override
            protected void updateItem(Double spent, boolean empty) {
                super.updateItem(spent, empty);
                if (empty || spent == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(spent));
                }
            }
        });
        
        TableColumn<BudgetCategory, Double> remainCol = new TableColumn<>("Remaining");
        remainCol.setCellValueFactory(new PropertyValueFactory<>("remaining"));
        remainCol.setCellFactory(column -> new TableCell<BudgetCategory, Double>() {
            @Override
            protected void updateItem(Double remaining, boolean empty) {
                super.updateItem(remaining, empty);
                if (empty || remaining == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(remaining));
                    if (remaining < 0) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.BLACK);
                        setStyle("");
                    }
                }
            }
        });
        
        TableColumn<BudgetCategory, Double> percentCol = new TableColumn<>("% Used");
        percentCol.setCellValueFactory(new PropertyValueFactory<>("percentUsed"));
        percentCol.setCellFactory(column -> new TableCell<BudgetCategory, Double>() {
            @Override
            protected void updateItem(Double percent, boolean empty) {
                super.updateItem(percent, empty);
                if (empty || percent == null) {
                    setGraphic(null);
                } else {
                    ProgressBar progressBar = new ProgressBar(percent / 100);
                    progressBar.setPrefWidth(100);
                    
                    if (percent > 90) {
                        progressBar.setStyle("-fx-accent: #e74c3c;");
                    } else if (percent > 70) {
                        progressBar.setStyle("-fx-accent: #f39c12;");
                    } else {
                        progressBar.setStyle("-fx-accent: #27ae60;");
                    }
                    
                    Label percentLabel = new Label(String.format("%.1f%%", percent));
                    
                    HBox box = new HBox(5);
                    box.setAlignment(Pos.CENTER);
                    box.getChildren().addAll(progressBar, percentLabel);
                    setGraphic(box);
                }
            }
        });
        
        budgetTable.getColumns().addAll(catCol, budgetCol, spentCol, remainCol, percentCol);
        
        // Sample budget data
        ObservableList<BudgetCategory> budgetData = FXCollections.observableArrayList();
        for (String category : expenseCategories) {
            double budget = 3000 + Math.random() * 7000;
            double spent = budget * (0.3 + Math.random() * 0.7);
            budgetData.add(new BudgetCategory(category, budget, spent));
        }
        budgetTable.setItems(budgetData);
        
        // Edit Budget Button
        Button editBudgetButton = new Button("Edit Budget");
        editBudgetButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        editBudgetButton.setOnAction(e -> showEditBudgetDialog());
        
        budgetSection.getChildren().addAll(title, budgetCards, budgetTable, editBudgetButton);
        
        return budgetSection;
    }
    
    private VBox createApprovalSection() {
        VBox approvalSection = new VBox(20);
        approvalSection.setPadding(new Insets(20));
        
        Label title = new Label("Expense Approvals");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Approval Stats
        HBox statsBox = new HBox(30);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(20));
        
        Label pendingLabel = new Label("12");
        pendingLabel.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        pendingLabel.setTextFill(Color.web("#f39c12"));
        VBox pendingBox = new VBox(5);
        pendingBox.setAlignment(Pos.CENTER);
        pendingBox.getChildren().addAll(pendingLabel, new Label("Pending"));
        
        Label approvedLabel = new Label("45");
        approvedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        approvedLabel.setTextFill(Color.web("#27ae60"));
        VBox approvedBox = new VBox(5);
        approvedBox.setAlignment(Pos.CENTER);
        approvedBox.getChildren().addAll(approvedLabel, new Label("Approved"));
        
        Label rejectedLabel = new Label("3");
        rejectedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        rejectedLabel.setTextFill(Color.web("#e74c3c"));
        VBox rejectedBox = new VBox(5);
        rejectedBox.setAlignment(Pos.CENTER);
        rejectedBox.getChildren().addAll(rejectedLabel, new Label("Rejected"));
        
        statsBox.getChildren().addAll(pendingBox, approvedBox, rejectedBox);
        
        // Pending Approvals Table
        TableView<CompanyExpense> approvalTable = new TableView<>();
        approvalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        approvalTable.setPrefHeight(400);
        
        // Copy columns from expense table but add approval actions
        TableColumn<CompanyExpense, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        
        TableColumn<CompanyExpense, String> employeeCol = new TableColumn<>("Employee");
        employeeCol.setCellValueFactory(new PropertyValueFactory<>("employee"));
        
        TableColumn<CompanyExpense, String> vendorCol = new TableColumn<>("Vendor");
        vendorCol.setCellValueFactory(new PropertyValueFactory<>("vendor"));
        
        TableColumn<CompanyExpense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        
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
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<CompanyExpense, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(column -> new TableCell<CompanyExpense, Void>() {
            private final Button approveBtn = new Button("Approve");
            private final Button rejectBtn = new Button("Reject");
            private final Button detailsBtn = new Button("Details");
            
            {
                approveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                                  "-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
                rejectBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                 "-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
                detailsBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                                  "-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
                
                approveBtn.setOnAction(e -> approveExpense(getTableRow().getItem()));
                rejectBtn.setOnAction(e -> rejectExpense(getTableRow().getItem()));
                detailsBtn.setOnAction(e -> showExpenseDetails(getTableRow().getItem()));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5);
                    buttons.setAlignment(Pos.CENTER);
                    buttons.getChildren().addAll(approveBtn, rejectBtn, detailsBtn);
                    setGraphic(buttons);
                }
            }
        });
        
        approvalTable.getColumns().addAll(dateCol, employeeCol, vendorCol, categoryCol, 
                                         amountCol, actionsCol);
        
        // Filter pending expenses
        ObservableList<CompanyExpense> pendingExpenses = FXCollections.observableArrayList();
        // Add sample pending expenses
        approvalTable.setItems(pendingExpenses);
        
        approvalSection.getChildren().addAll(title, statsBox, approvalTable);
        
        return approvalSection;
    }
    
    private VBox createBudgetCard(String title, String value, Color color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 14));
        titleLabel.setTextFill(Color.GRAY);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        valueLabel.setTextFill(color);
        
        card.getChildren().addAll(titleLabel, valueLabel);
        
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
    
    private String getCategoryStyle(String category) {
        Map<String, String> categoryColors = new HashMap<>();
        categoryColors.put("Fuel", "#e74c3c");
        categoryColors.put("Maintenance", "#3498db");
        categoryColors.put("Insurance", "#9b59b6");
        categoryColors.put("Office Supplies", "#f39c12");
        categoryColors.put("Utilities", "#1abc9c");
        categoryColors.put("Marketing", "#e67e22");
        categoryColors.put("Payroll", "#34495e");
        categoryColors.put("Equipment", "#16a085");
        
        String color = categoryColors.getOrDefault(category, "#95a5a6");
        return String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                           "-fx-background-radius: 15; -fx-font-size: 11px;", color);
    }
    
    private String getStatusStyle(String status) {
        switch (status) {
            case "Approved":
                return "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Pending":
                return "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-background-radius: 15;";
            case "Rejected":
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
    
    private void loadExpenseData() {
        loadingIndicator.setVisible(true);

        Task<List<CompanyExpense>> task = new Task<List<CompanyExpense>>() {
            @Override
            protected List<CompanyExpense> call() throws Exception {
                List<com.company.payroll.expenses.CompanyExpense> all = expenseDAO.getAll();
                LocalDate start = startDatePicker.getValue();
                LocalDate end = endDatePicker.getValue();
                if (start != null && end != null) {
                    all = all.stream()
                        .filter(e -> e.getExpenseDate() != null &&
                             !e.getExpenseDate().isBefore(start) &&
                             !e.getExpenseDate().isAfter(end))
                        .collect(Collectors.toList());
                }
                return all.stream()
                    .map(CompanyExpensesTab.this::mapToViewModel)
                    .collect(Collectors.toList());
            }
        };
        
        task.setOnSucceeded(e -> {
            List<CompanyExpense> data = task.getValue();
            updateTable(data);
            updateSummaryCards(data);
            updateCharts(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.ERROR, "Error", "Failed to load expense data.");
        });
        
        new Thread(task).start();
    }
    
    private void updateTable(List<CompanyExpense> data) {
        ObservableList<CompanyExpense> tableData = FXCollections.observableArrayList(data);
        expenseTable.setItems(tableData);
        
        // Apply filters
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        categoryComboBox.setOnAction(e -> applyFilters());
        vendorComboBox.setOnAction(e -> applyFilters());
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase();
        String selectedCategory = categoryComboBox.getValue();
        String selectedVendor = vendorComboBox.getValue();
        
        ObservableList<CompanyExpense> filtered = expenseTable.getItems().filtered(expense -> {
            boolean matchesSearch = searchText.isEmpty() ||
                expense.getVendor().toLowerCase().contains(searchText) ||
                expense.getDescription().toLowerCase().contains(searchText) ||
                expense.getCategory().toLowerCase().contains(searchText);
            
            boolean matchesCategory = "All Categories".equals(selectedCategory) ||
                selectedCategory == null ||
                expense.getCategory().equals(selectedCategory);
            
            boolean matchesVendor = selectedVendor == null ||
                "All Vendors".equals(selectedVendor) ||
                expense.getVendor().equals(selectedVendor);
            
            return matchesSearch && matchesCategory && matchesVendor;
        });
        
        expenseTable.setItems(filtered);
    }
    
    private void updateSummaryCards(List<CompanyExpense> data) {
        double total = data.stream().mapToDouble(CompanyExpense::getAmount).sum();
        double monthlyAvg = total / 12; // Simplified calculation
        double largest = data.stream().mapToDouble(CompanyExpense::getAmount).max().orElse(0);
        long pending = data.stream().filter(e -> "Pending".equals(e.getStatus())).count();
        
        animateValue(totalExpensesLabel, CURRENCY_FORMAT.format(total));
        animateValue(monthlyAverageLabel, CURRENCY_FORMAT.format(monthlyAvg));
        animateValue(largestExpenseLabel, CURRENCY_FORMAT.format(largest));
        animateValue(pendingApprovalLabel, String.valueOf(pending));
    }
    
    private void updateCharts(List<CompanyExpense> data) {
        // Update category chart
        Map<String, Double> categoryTotals = data.stream()
            .collect(Collectors.groupingBy(
                CompanyExpense::getCategory,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        categoryTotals.forEach((category, total) -> {
            pieData.add(new PieChart.Data(category + " (" + CURRENCY_FORMAT.format(total) + ")", total));
        });
        categoryChart.setData(pieData);
        
        // Add tooltips
        pieData.forEach(data1 -> {
            Tooltip tooltip = new Tooltip(String.format("%s: %.1f%%", 
                data1.getName(), (data1.getPieValue() / data.stream()
                    .mapToDouble(CompanyExpense::getAmount).sum()) * 100));
            Tooltip.install(data1.getNode(), tooltip);
        });
        
        // Update trend chart
        XYChart.Series<String, Number> trendSeries = new XYChart.Series<>();
        trendSeries.setName("Monthly Expenses");
        
        Map<String, Double> monthlyTotals = data.stream()
            .collect(Collectors.groupingBy(
                expense -> expense.getDate().format(DateTimeFormatter.ofPattern("MMM")),
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        monthlyTotals.forEach((month, total) -> {
            trendSeries.getData().add(new XYChart.Data<>(month, total));
        });
        
        trendChart.getData().clear();
        trendChart.getData().add(trendSeries);
        
        // Update vendor chart
        XYChart.Series<String, Number> vendorSeries = new XYChart.Series<>();
        Map<String, Double> vendorTotals = data.stream()
            .collect(Collectors.groupingBy(
                CompanyExpense::getVendor,
                Collectors.summingDouble(CompanyExpense::getAmount)
            ));
        
        vendorTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                vendorSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            });
        
        vendorChart.getData().clear();
        vendorChart.getData().add(vendorSeries);
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
    	
	private void showAddExpenseDialog() {
        Dialog<CompanyExpense> dialog = new Dialog<>();
        dialog.setTitle("Add New Expense");
        dialog.setHeaderText("Enter expense details");
        
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField vendorField = new TextField();
        vendorField.setPromptText("Vendor name");
        
        ComboBox<String> categoryField = new ComboBox<>(expenseCategories);
        categoryField.setPromptText("Select category");
        
        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Description");
        descriptionArea.setPrefRowCount(3);
        
        CheckBox receiptCheckBox = new CheckBox("Receipt attached");
        
        grid.add(new Label("Date:"), 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("Vendor:"), 0, 1);
        grid.add(vendorField, 1, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(categoryField, 1, 2);
        grid.add(new Label("Amount:"), 0, 3);
        grid.add(amountField, 1, 3);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descriptionArea, 1, 4);
        grid.add(receiptCheckBox, 1, 5);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validate amount field
        amountField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*\\.?\\d*")) {
                amountField.setText(oldText);
            }
        });
        
        // Enable/Disable add button
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        
        vendorField.textProperty().addListener((obs, oldVal, newVal) -> 
            validateExpenseForm(addButton, vendorField, categoryField, amountField));
        categoryField.valueProperty().addListener((obs, oldVal, newVal) -> 
            validateExpenseForm(addButton, vendorField, categoryField, amountField));
        amountField.textProperty().addListener((obs, oldVal, newVal) -> 
            validateExpenseForm(addButton, vendorField, categoryField, amountField));
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                CompanyExpense expense = new CompanyExpense();
                expense.setDate(datePicker.getValue());
                expense.setVendor(vendorField.getText());
                expense.setCategory(categoryField.getValue());
                expense.setAmount(Double.parseDouble(amountField.getText()));
                expense.setDescription(descriptionArea.getText());
                expense.setStatus("Pending");
                expense.setHasReceipt(receiptCheckBox.isSelected() ? "Yes" : "No");
                expense.setEmployee("mgubran1"); // Current user
                return expense;
            }
            return null;
        });
        
        Optional<CompanyExpense> result = dialog.showAndWait();
        result.ifPresent(expense -> {
            expenseTable.getItems().add(expense);
            showAlert(AlertType.INFORMATION, "Expense Added", 
                     "Expense for " + CURRENCY_FORMAT.format(expense.getAmount()) + " added successfully!");
            loadExpenseData(); // Refresh data
        });
    }
    
    private void validateExpenseForm(Node button, TextField vendor, ComboBox<String> category, TextField amount) {
        boolean isValid = !vendor.getText().trim().isEmpty() &&
                         category.getValue() != null &&
                         !amount.getText().trim().isEmpty() &&
                         amount.getText().matches("\\d+\\.?\\d*");
        button.setDisable(!isValid);
    }
    
    private void importExpenses() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Expenses");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        
        File file = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            loadingIndicator.setVisible(true);
            
            Task<Integer> importTask = new Task<Integer>() {
                @Override
                protected Integer call() throws Exception {
                    // Simulate import process
                    Thread.sleep(2000);
                    return 15; // Number of imported expenses
                }
            };
            
            importTask.setOnSucceeded(e -> {
                loadingIndicator.setVisible(false);
                int count = importTask.getValue();
                showAlert(AlertType.INFORMATION, "Import Successful", 
                         count + " expenses imported successfully!");
                loadExpenseData();
            });
            
            importTask.setOnFailed(e -> {
                loadingIndicator.setVisible(false);
                showAlert(AlertType.ERROR, "Import Failed", 
                         "Failed to import expenses. Please check the file format.");
            });
            
            new Thread(importTask).start();
        }
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
        fileChooser.setInitialFileName("expenses_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // Export logic here
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Expenses exported to Excel successfully!");
        }
    }
    
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("expense_report_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // PDF export logic
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Expense report exported to PDF successfully!");
        }
    }
    
    private void exportToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("expenses_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // CSV export logic
            showAlert(AlertType.INFORMATION, "Export Successful", 
                     "Expenses exported to CSV successfully!");
        }
    }
    
    private void viewReceipt() {
        CompanyExpense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // In a real application, this would open the receipt image/PDF
            showAlert(AlertType.INFORMATION, "Receipt Viewer", 
                     "Viewing receipt for expense: " + selected.getVendor());
        }
    }
    
    private void deleteSelectedExpenses() {
        ObservableList<CompanyExpense> selected = expenseTable.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) {
            Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
            confirmAlert.setTitle("Delete Expenses");
            confirmAlert.setHeaderText("Delete " + selected.size() + " expense(s)?");
            confirmAlert.setContentText("This action cannot be undone.");
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                expenseTable.getItems().removeAll(selected);
                showAlert(AlertType.INFORMATION, "Expenses Deleted", 
                         selected.size() + " expense(s) deleted successfully.");
                loadExpenseData();
            }
        }
    }
    
    private void showEditBudgetDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Edit Budget");
        dialog.setHeaderText("Set budget limits for each category");
        
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        Map<String, TextField> budgetFields = new HashMap<>();
        int row = 0;
        
        for (String category : expenseCategories) {
            Label label = new Label(category + ":");
            TextField field = new TextField();
            field.setPromptText("0.00");
            field.setText(String.format("%.2f", 3000 + Math.random() * 7000));
            budgetFields.put(category, field);
            
            grid.add(label, 0, row);
            grid.add(field, 1, row);
            row++;
        }
        
        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setPrefHeight(400);
        dialog.getDialogPane().setContent(scrollPane);
        
        dialog.showAndWait();
    }
    
    private void approveExpense(CompanyExpense expense) {
        if (expense != null) {
            expense.setStatus("Approved");
            showAlert(AlertType.INFORMATION, "Expense Approved", 
                     "Expense from " + expense.getVendor() + " has been approved.");
            loadExpenseData();
        }
    }
    
    private void rejectExpense(CompanyExpense expense) {
        if (expense != null) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Reject Expense");
            dialog.setHeaderText("Reason for rejection:");
            dialog.setContentText("Please provide a reason:");
            
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(reason -> {
                expense.setStatus("Rejected");
                showAlert(AlertType.INFORMATION, "Expense Rejected", 
                         "Expense from " + expense.getVendor() + " has been rejected.\nReason: " + reason);
                loadExpenseData();
            });
        }
    }
    
    private void showExpenseDetails(CompanyExpense expense) {
        if (expense != null) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Expense Details");
            alert.setHeaderText("Expense Information");
            
            String details = String.format(
                "Date: %s\n" +
                "Vendor: %s\n" +
                "Category: %s\n" +
                "Amount: %s\n" +
                "Description: %s\n" +
                "Status: %s\n" +
                "Employee: %s\n" +
                "Receipt: %s",
                expense.getDate().format(DATE_FORMAT),
                expense.getVendor(),
                expense.getCategory(),
                CURRENCY_FORMAT.format(expense.getAmount()),
                expense.getDescription(),
                expense.getStatus(),
                expense.getEmployee(),
                expense.getHasReceipt()
            );
            
            alert.setContentText(details);
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

    private CompanyExpense mapToViewModel(com.company.payroll.expenses.CompanyExpense exp) {
        CompanyExpense ce = new CompanyExpense();
        ce.setDate(exp.getExpenseDate());
        ce.setVendor(exp.getVendor());
        ce.setCategory(exp.getCategory());
        ce.setDescription(exp.getDescription());
        ce.setAmount(exp.getAmount());
        ce.setStatus(exp.getStatus());
        ce.setHasReceipt((exp.getReceiptNumber() != null && !exp.getReceiptNumber().isEmpty()) ? "Yes" : "No");
        ce.setEmployee(exp.getEmployeeId());
        return ce;
    }
    
    private void initializeData() {
        try {
            List<com.company.payroll.expenses.CompanyExpense> all = expenseDAO.getAll();
            Set<String> vendors = all.stream()
                .map(com.company.payroll.expenses.CompanyExpense::getVendor)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
            vendorComboBox.getItems().add("All Vendors");
            vendorComboBox.getItems().addAll(vendors);
        } catch (Exception e) {
            vendorComboBox.getItems().add("All Vendors");
        }
    }
    
    
    // Inner class for Company Expense
    public static class CompanyExpense {
        private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
        private final StringProperty vendor = new SimpleStringProperty();
        private final StringProperty category = new SimpleStringProperty();
        private final StringProperty description = new SimpleStringProperty();
        private final DoubleProperty amount = new SimpleDoubleProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final StringProperty hasReceipt = new SimpleStringProperty();
        private final StringProperty employee = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        
        // Getters and setters
        public LocalDate getDate() { return date.get(); }
        public void setDate(LocalDate value) { date.set(value); }
        public ObjectProperty<LocalDate> dateProperty() { return date; }
        
        public String getVendor() { return vendor.get(); }
        public void setVendor(String value) { vendor.set(value); }
        public StringProperty vendorProperty() { return vendor; }
        
        public String getCategory() { return category.get(); }
        public void setCategory(String value) { category.set(value); }
        public StringProperty categoryProperty() { return category; }
        
        public String getDescription() { return description.get(); }
        public void setDescription(String value) { description.set(value); }
        public StringProperty descriptionProperty() { return description; }
        
        public double getAmount() { return amount.get(); }
        public void setAmount(double value) { amount.set(value); }
        public DoubleProperty amountProperty() { return amount; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
        
        public String getHasReceipt() { return hasReceipt.get(); }
        public void setHasReceipt(String value) { hasReceipt.set(value); }
        public StringProperty hasReceiptProperty() { return hasReceipt; }
        
        public String getEmployee() { return employee.get(); }
        public void setEmployee(String value) { employee.set(value); }
        public StringProperty employeeProperty() { return employee; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
    }
    
    // Inner class for Budget Category
    public static class BudgetCategory {
        private final StringProperty category = new SimpleStringProperty();
        private final DoubleProperty budget = new SimpleDoubleProperty();
        private final DoubleProperty spent = new SimpleDoubleProperty();
        private final DoubleProperty remaining = new SimpleDoubleProperty();
        private final DoubleProperty percentUsed = new SimpleDoubleProperty();
        
        public BudgetCategory(String category, double budget, double spent) {
            this.category.set(category);
            this.budget.set(budget);
            this.spent.set(spent);
            this.remaining.set(budget - spent);
            this.percentUsed.set((spent / budget) * 100);
        }
        
        // Getters
        public String getCategory() { return category.get(); }
        public double getBudget() { return budget.get(); }
        public double getSpent() { return spent.get(); }
        public double getRemaining() { return remaining.get(); }
        public double getPercentUsed() { return percentUsed.get(); }
        
        // Properties
        public StringProperty categoryProperty() { return category; }
        public DoubleProperty budgetProperty() { return budget; }
        public DoubleProperty spentProperty() { return spent; }
        public DoubleProperty remainingProperty() { return remaining; }
        public DoubleProperty percentUsedProperty() { return percentUsed; }
    }
}