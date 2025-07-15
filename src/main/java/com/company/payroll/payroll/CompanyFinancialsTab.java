package com.company.payroll.payroll;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.paint.Color;
import javafx.scene.control.Tooltip;
import java.time.LocalDate;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import java.util.List;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import com.company.payroll.expenses.CompanyExpenseDAO;
import com.company.payroll.maintenance.MaintenanceDAO;
import com.company.payroll.expenses.CompanyExpense;
import com.company.payroll.maintenance.MaintenanceRecord;
import java.util.stream.Collectors;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.HashSet;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.Chart;
import javafx.util.Duration;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.layout.StackPane;
import com.company.payroll.export.PDFExporter;

/**
 * Professional Company Financials Tab - Displays comprehensive financial data including:
 * - Weekly driver financial data from PayrollTab
 * - Detailed company expense breakdowns
 * - Maintenance cost analysis
 * - Visual charts and analytics
 */
public class CompanyFinancialsTab extends BorderPane {
    
    // UI Components
    private final DatePicker startDatePicker = new DatePicker(LocalDate.now().withDayOfYear(1));
    private final DatePicker endDatePicker = new DatePicker(LocalDate.now());
    private final Button refreshBtn = new Button("🔄 Refresh");
    private final Button exportBtn = new Button("📊 Export");
    private final Button detailsBtn = new Button("📋 Show Details");
    private final Button printPdfBtn = new Button("🖨️ Print PDF");
    private final TableView<FinancialRow> financialTable = new TableView<>();
    
    // Summary Labels
    private final Label totalGrossLabel = new Label("$0.00");
    private final Label totalServiceFeeLabel = new Label("$0.00");
    private final Label totalCompanyPayLabel = new Label("$0.00");
    private final Label totalCompanyNetLabel = new Label("$0.00");
    private final Label totalExpensesLabel = new Label("$0.00");
    private final Label finalNetLabel = new Label("$0.00");
    
    // Expense Detail Components
    private final TableView<ExpenseRow> expenseTable = new TableView<>();
    private final TableView<MaintenanceRow> maintenanceTable = new TableView<>();
    private final PieChart expensePieChart = new PieChart();
    private final BarChart<String, Number> monthlyExpenseChart;
    
    // Detail containers
    private final VBox mainContainer = new VBox(20);
    private final VBox detailsContainer = new VBox(15);
    private final TabPane expenseTabs = new TabPane();
    private boolean detailsVisible = false;
    
    // Data storage
    private List<CompanyExpense> currentExpenses = new ArrayList<>();
    private List<MaintenanceRecord> currentMaintenance = new ArrayList<>();
    
    // Dependencies
    private final PayrollTab payrollTab;
    private final CompanyExpenseDAO companyExpenseDAO;
    private final MaintenanceDAO maintenanceDAO;
    
    // Company name
    private String companyName = "Company";

    public CompanyFinancialsTab(PayrollTab payrollTab, CompanyExpenseDAO companyExpenseDAO, MaintenanceDAO maintenanceDAO) {
        this.payrollTab = payrollTab;
        this.companyExpenseDAO = companyExpenseDAO;
        this.maintenanceDAO = maintenanceDAO;
        
        // Get company name from PayrollTab
        if (payrollTab != null) {
            this.companyName = payrollTab.getCompanyName();
        }
        
        // Initialize charts with explicit axis configuration
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Month");
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarkVisible(true);
        xAxis.setSide(Side.BOTTOM);
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Total Cost ($)");
        yAxis.setTickLabelsVisible(true);
        yAxis.setTickMarkVisible(true);
        yAxis.setSide(Side.LEFT);
        yAxis.setAutoRanging(true);
        
        monthlyExpenseChart = new BarChart<>(xAxis, yAxis);
        monthlyExpenseChart.setTitle("Monthly Expense Totals");
        monthlyExpenseChart.setLegendVisible(true);
        monthlyExpenseChart.setAnimated(true);
        monthlyExpenseChart.setCategoryGap(10);
        monthlyExpenseChart.setBarGap(3);
        
        initializeUI();
        setupEventHandlers();
        
        // Ensure CSS is loaded for chart styling
        getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        refreshData();
    }
    
    private void initializeUI() {
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #F5F5F5;");
        
        // Header
        VBox headerBox = createHeader();
        
        // Date Range Controls
        HBox dateControls = createDateControls();
        
        // Create main TabPane
        TabPane mainTabPane = new TabPane();
        mainTabPane.setStyle("-fx-background-color: white;");
        mainTabPane.setPrefHeight(800);
        
        // Financial Data Tab
        Tab financialDataTab = new Tab("Financial Data");
        financialDataTab.setClosable(false);
        
        // Main content container for financial data
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.TOP_CENTER);
        
        // Financial Summary Section
        VBox summarySection = createSummarySection();
        
        // Revenue Table
        VBox revenueSection = createRevenueSection();
        
        // Basic Expense Summary
        VBox basicExpenseSection = createBasicExpenseSection();
        
        // Detailed expense container (initially hidden)
        detailsContainer.setVisible(false);
        detailsContainer.setManaged(false);
        setupDetailedExpenseView();
        
        // Add all components to financial data container
        mainContainer.getChildren().addAll(
            summarySection, 
            revenueSection, 
            basicExpenseSection,
            detailsContainer
        );
        
        ScrollPane financialScrollPane = new ScrollPane(mainContainer);
        financialScrollPane.setFitToWidth(true);
        financialScrollPane.setStyle("-fx-background-color: transparent;");
        financialDataTab.setContent(financialScrollPane);
        
        // Visual Analytics Tab
        Tab visualAnalyticsTab = new Tab("Visual Analytics");
        visualAnalyticsTab.setClosable(false);
        visualAnalyticsTab.setContent(createVisualAnalyticsSection());
        
        // Add tabs to TabPane
        mainTabPane.getTabs().addAll(financialDataTab, visualAnalyticsTab);
        
        // Main layout
        VBox mainLayout = new VBox(15);
        mainLayout.getChildren().addAll(headerBox, dateControls, mainTabPane);
        
        setCenter(mainLayout);
    }
    
    private VBox createHeader() {
        VBox headerBox = new VBox(5);
        headerBox.setAlignment(Pos.CENTER);
        
        Label title = new Label(companyName + " Financial Dashboard");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        title.setTextFill(Color.web("#1976D2"));
        title.setId("companyTitle"); // For dynamic updates
        
        Label subtitle = new Label("Comprehensive Revenue & Expense Analysis");
        subtitle.setFont(Font.font("Arial", 16));
        subtitle.setTextFill(Color.web("#666666"));
        
        headerBox.getChildren().addAll(title, subtitle);
        return headerBox;
    }
    
    private HBox createDateControls() {
        HBox dateControls = new HBox(15);
        dateControls.setAlignment(Pos.CENTER);
        dateControls.setPadding(new Insets(15));
        dateControls.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                             "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label dateRangeLabel = new Label("Date Range:");
        dateRangeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        dateRangeLabel.setTextFill(Color.web("#333333"));
        
        Label toLabel = new Label("to");
        toLabel.setFont(Font.font("Arial", 14));
        toLabel.setTextFill(Color.web("#333333"));
        
        // Style date pickers
        startDatePicker.setPrefWidth(150);
        endDatePicker.setPrefWidth(150);
        
        // Apply modern button styling
        ModernButtonStyles.applyPrimaryStyle(refreshBtn);
        ModernButtonStyles.applySuccessStyle(exportBtn);
        ModernButtonStyles.applyWarningStyle(detailsBtn);
        ModernButtonStyles.applyInfoStyle(printPdfBtn);
        
        dateControls.getChildren().addAll(
            dateRangeLabel, startDatePicker,
            toLabel, endDatePicker,
            refreshBtn, exportBtn, detailsBtn, printPdfBtn
        );
        
        return dateControls;
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
    
    private VBox createSummarySection() {
        VBox summaryBox = new VBox(10);
        summaryBox.setPadding(new Insets(20));
        summaryBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        summaryBox.setAlignment(Pos.CENTER);
        
        Label summaryTitle = new Label("Financial Summary");
        summaryTitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        summaryTitle.setTextFill(Color.web("#1976D2"));
        
        // Create grid for summary data
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(40);
        summaryGrid.setVgap(10);
        summaryGrid.setAlignment(Pos.CENTER);
        
        // Style all value labels
        styleValueLabel(totalGrossLabel, "#2E7D32", 16);
        styleValueLabel(totalServiceFeeLabel, "#1976D2", 16);
        styleValueLabel(totalCompanyPayLabel, "#1976D2", 16);
        styleValueLabel(totalCompanyNetLabel, "#1565C0", 18);
        styleValueLabel(totalExpensesLabel, "#D32F2F", 16);
        styleValueLabel(finalNetLabel, "#1B5E20", 20);
        
        // Add rows with styled labels
        addSummaryRow(summaryGrid, 0, "Total Gross Revenue:", totalGrossLabel, false);
        addSummaryRow(summaryGrid, 1, "Total Service Fee:", totalServiceFeeLabel, false);
        addSummaryRow(summaryGrid, 2, "Total Company Pay:", totalCompanyPayLabel, false);
        
        // Separator
        Separator sep1 = new Separator();
        summaryGrid.add(sep1, 0, 3, 2, 1);
        GridPane.setMargin(sep1, new Insets(5, 0, 5, 0));
        
        addSummaryRow(summaryGrid, 4, "Total Company Revenue:", totalCompanyNetLabel, true);
        addSummaryRow(summaryGrid, 5, "Total Expenses:", totalExpensesLabel, false);
        
        // Separator
        Separator sep2 = new Separator();
        summaryGrid.add(sep2, 0, 6, 2, 1);
        GridPane.setMargin(sep2, new Insets(5, 0, 5, 0));
        
        addSummaryRow(summaryGrid, 7, "Net Profit:", finalNetLabel, true);
        
        summaryBox.getChildren().addAll(summaryTitle, summaryGrid);
        return summaryBox;
    }
    
    private void addSummaryRow(GridPane grid, int row, String labelText, Label valueLabel, boolean bold) {
        Label label = new Label(labelText);
        label.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 14));
        label.setTextFill(Color.web("#333333"));
        
        grid.add(label, 0, row);
        grid.add(valueLabel, 1, row);
        GridPane.setHalignment(valueLabel, javafx.geometry.HPos.RIGHT);
    }
    
    private void styleValueLabel(Label label, String color, int size) {
        label.setFont(Font.font("Arial", FontWeight.BOLD, size));
        label.setTextFill(Color.web(color));
    }
    
    private VBox createRevenueSection() {
        VBox revenueBox = new VBox(15);
        revenueBox.setPadding(new Insets(20));
        revenueBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        // Title with subtitle
        VBox titleBox = new VBox(5);
        Label revenueTitle = new Label("Revenue Breakdown by Driver");
        revenueTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        revenueTitle.setTextFill(Color.web("#1976D2"));
        
        Label subtitle = new Label("Aggregated driver performance for the selected period");
        subtitle.setFont(Font.font("Arial", 12));
        subtitle.setTextFill(Color.web("#7f8c8d"));
        
        titleBox.getChildren().addAll(revenueTitle, subtitle);
        
        // Setup revenue table
        setupRevenueTable();
        financialTable.setPrefHeight(400);
        
        // Apply header styling after scene is shown
        Platform.runLater(() -> {
            financialTable.lookupAll(".column-header-background").forEach(node -> {
                node.setStyle("-fx-background-color: #2c3e50;");
            });
            financialTable.lookupAll(".column-header .label").forEach(node -> {
                node.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
            });
        });
        
        revenueBox.getChildren().addAll(titleBox, financialTable);
        return revenueBox;
    }
    
    private void setupRevenueTable() {
        financialTable.getColumns().clear();
        
        // Create columns with professional styling
        TableColumn<FinancialRow, String> weekCol = new TableColumn<>("Date Range");
        weekCol.setCellValueFactory(data -> data.getValue().weekProperty());
        weekCol.setPrefWidth(250);
        weekCol.setStyle("-fx-alignment: CENTER-LEFT;");
        
        // Custom cell factory for date range column
        weekCol.setCellFactory(column -> new TableCell<FinancialRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else if (item.equals("TOTAL_ROW")) {
                    setText("SUMMARY");
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                } else {
                    setText(item);
                    setStyle("-fx-padding: 0 0 0 10;");
                }
            }
        });
        
        TableColumn<FinancialRow, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(data -> data.getValue().driverProperty());
        driverCol.setPrefWidth(220);
        driverCol.setStyle("-fx-alignment: CENTER-LEFT;");
        
        // Custom cell factory for driver column
        driverCol.setCellFactory(column -> new TableCell<FinancialRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if (getTableRow() != null && getTableRow().getItem() != null && 
                        getTableRow().getItem().getWeek().equals("TOTAL_ROW")) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                    } else {
                        setStyle("-fx-padding: 0 0 0 10;");
                    }
                }
            }
        });
        
        TableColumn<FinancialRow, String> grossCol = new TableColumn<>("Gross Revenue");
        grossCol.setCellValueFactory(data -> data.getValue().grossProperty());
        grossCol.setPrefWidth(130);
        styleMoneyColumn(grossCol);
        
        TableColumn<FinancialRow, String> serviceFeeCol = new TableColumn<>("Service Fee");
        serviceFeeCol.setCellValueFactory(data -> data.getValue().serviceFeeProperty());
        serviceFeeCol.setPrefWidth(130);
        styleMoneyColumn(serviceFeeCol);
        
        TableColumn<FinancialRow, String> companyPayCol = new TableColumn<>("Company Pay");
        companyPayCol.setCellValueFactory(data -> data.getValue().companyPayProperty());
        companyPayCol.setPrefWidth(130);
        styleMoneyColumn(companyPayCol);
        
        TableColumn<FinancialRow, String> companyNetCol = new TableColumn<>("Company Revenue");
        companyNetCol.setCellValueFactory(data -> data.getValue().companyNetProperty());
        companyNetCol.setPrefWidth(140);
        styleMoneyColumn(companyNetCol);
        
        @SuppressWarnings("unchecked")
        TableColumn<FinancialRow, ?>[] financialColumns = new TableColumn[] {
            weekCol, driverCol, grossCol, serviceFeeCol, companyPayCol, companyNetCol
        };
        financialTable.getColumns().addAll(financialColumns);
        
        // Professional table styling
        financialTable.setStyle("-fx-font-size: 13px; " +
                              "-fx-selection-bar: #3498db; " +
                              "-fx-selection-bar-non-focused: #bdc3c7; " +
                              "-fx-border-color: #e0e0e0; " +
                              "-fx-border-width: 1; " +
                              "-fx-border-radius: 5; " +
                              "-fx-background-radius: 5;");
        
        // Style column headers
        financialTable.lookupAll(".column-header").forEach(node -> {
            node.setStyle("-fx-background-color: #2c3e50; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-weight: bold; " +
                         "-fx-font-size: 14px; " +
                         "-fx-padding: 10 5 10 5;");
        });
        
        // Set placeholder
        Label placeholder = new Label("Click 'Refresh' to load financial data");
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d;");
        financialTable.setPlaceholder(placeholder);
        
        // Set row height
        financialTable.setFixedCellSize(35);
        
        // Professional table styling
        financialTable.setRowFactory(tv -> {
            TableRow<FinancialRow> row = new TableRow<FinancialRow>() {
                @Override
                protected void updateItem(FinancialRow item, boolean empty) {
                    super.updateItem(item, empty);
                    
                    if (item == null || empty) {
                        setStyle("");
                        setGraphic(null);
                    } else {
                        // Check if this is the total row
                        if (item.getWeek().equals("TOTAL_ROW")) {
                            // Add top border for separation
                            setStyle("-fx-background-color: #f5f5f5; " +
                                   "-fx-border-color: #2c3e50 transparent transparent transparent; " +
                                   "-fx-border-width: 2 0 0 0; " +
                                   "-fx-font-weight: bold;");
                        } else {
                            // Regular row styling with subtle alternation
                            if (getIndex() % 2 == 0) {
                                setStyle("-fx-background-color: #fafafa;");
                            } else {
                                setStyle("-fx-background-color: white;");
                            }
                        }
                    }
                }
            };
            
            // Add hover effect
            row.setOnMouseEntered(e -> {
                if (row.getItem() != null && !row.getItem().getWeek().equals("TOTAL_ROW")) {
                    row.setStyle(row.getStyle() + "-fx-background-color: #e8f4f8;");
                }
            });
            
            row.setOnMouseExited(e -> {
                if (row.getItem() != null) {
                    // Restore original style based on row type
                    if (row.getItem().getWeek().equals("TOTAL_ROW")) {
                        row.setStyle("-fx-background-color: #f5f5f5; " +
                                   "-fx-border-color: #2c3e50 transparent transparent transparent; " +
                                   "-fx-border-width: 2 0 0 0; " +
                                   "-fx-font-weight: bold;");
                    } else if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #fafafa;");
                    } else {
                        row.setStyle("-fx-background-color: white;");
                    }
                }
            });
            
            return row;
        });
    }
    
    private void styleMoneyColumn(TableColumn<FinancialRow, String> column) {
        column.setCellFactory(tc -> new TableCell<FinancialRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    
                    // Check if this is a total row
                    if (getTableRow() != null && getTableRow().getItem() != null && 
                        getTableRow().getItem().getWeek().equals("TOTAL_ROW")) {
                        setStyle("-fx-font-weight: bold; " +
                               "-fx-text-fill: #2c3e50; " +
                               "-fx-font-size: 14px; " +
                               "-fx-padding: 0 10 0 0;");
                    } else {
                        // Color code based on value
                        if (item.startsWith("$") && !item.equals("$0.00")) {
                            if (column.getText().contains("Service Fee") || column.getText().contains("Expense")) {
                                setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: normal; -fx-padding: 0 10 0 0;");
                            } else {
                                setStyle("-fx-text-fill: #27ae60; -fx-font-weight: normal; -fx-padding: 0 10 0 0;");
                            }
                        } else {
                            setStyle("-fx-font-weight: normal; -fx-padding: 0 10 0 0;");
                        }
                    }
                }
            }
        });
        
        // Set column alignment
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }
    
    private VBox createBasicExpenseSection() {
        VBox expenseBox = new VBox(10);
        expenseBox.setPadding(new Insets(20));
        expenseBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label expenseTitle = new Label("Expense Overview");
        expenseTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        expenseTitle.setTextFill(Color.web("#D32F2F"));
        
        // Create expense summary grid
        GridPane expenseGrid = new GridPane();
        expenseGrid.setHgap(30);
        expenseGrid.setVgap(15);
        expenseGrid.setPadding(new Insets(10));
        
        // This will be populated in refreshData()
        expenseGrid.setId("expenseGrid");
        
        expenseBox.getChildren().addAll(expenseTitle, expenseGrid);
        return expenseBox;
    }
    
    private void setupDetailedExpenseView() {
        detailsContainer.setPadding(new Insets(20));
        detailsContainer.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                                 "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label detailsTitle = new Label("Detailed Expense Analysis");
        detailsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        detailsTitle.setTextFill(Color.web("#D32F2F"));
        
        // Setup tabs
        Tab expenseTab = new Tab("Company Expenses");
        expenseTab.setClosable(false);
        expenseTab.setContent(createExpenseTabContent());
        
        Tab maintenanceTab = new Tab("Maintenance");
        maintenanceTab.setClosable(false);
        maintenanceTab.setContent(createMaintenanceTabContent());
        
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setClosable(false);
        analyticsTab.setContent(createAnalyticsTabContent());
        
        expenseTabs.getTabs().addAll(expenseTab, maintenanceTab, analyticsTab);
        expenseTabs.setTabMinWidth(150);
        
        detailsContainer.getChildren().addAll(detailsTitle, expenseTabs);
    }
    
    private VBox createExpenseTabContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        
        // Setup expense table
        setupExpenseTable();
        expenseTable.setPrefHeight(400);
        
        content.getChildren().add(expenseTable);
        return content;
    }
    
    private void setupExpenseTable() {
        expenseTable.getColumns().clear();
        
        TableColumn<ExpenseRow, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> data.getValue().dateProperty());
        dateCol.setPrefWidth(100);
        
        TableColumn<ExpenseRow, String> vendorCol = new TableColumn<>("Vendor");
        vendorCol.setCellValueFactory(data -> data.getValue().vendorProperty());
        vendorCol.setPrefWidth(150);
        
        TableColumn<ExpenseRow, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(data -> data.getValue().categoryProperty());
        categoryCol.setPrefWidth(120);
        
        TableColumn<ExpenseRow, String> descriptionCol = new TableColumn<>("Description");
        descriptionCol.setCellValueFactory(data -> data.getValue().descriptionProperty());
        descriptionCol.setPrefWidth(200);
        
        TableColumn<ExpenseRow, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(data -> data.getValue().amountProperty().asObject());
        amountCol.setPrefWidth(100);
        amountCol.setCellFactory(tc -> new TableCell<ExpenseRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", item));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<ExpenseRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setPrefWidth(100);
        
        @SuppressWarnings("unchecked")
        TableColumn<ExpenseRow, ?>[] expenseColumns = new TableColumn[] {
            dateCol, vendorCol, categoryCol, descriptionCol, amountCol, statusCol
        };
        expenseTable.getColumns().addAll(expenseColumns);
        expenseTable.setPlaceholder(new Label("No expense data available"));
    }
    
    private VBox createMaintenanceTabContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        
        // Setup maintenance table
        setupMaintenanceTable();
        maintenanceTable.setPrefHeight(400);
        
        content.getChildren().add(maintenanceTable);
        return content;
    }
    
    private void setupMaintenanceTable() {
        maintenanceTable.getColumns().clear();
        
        TableColumn<MaintenanceRow, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> data.getValue().dateProperty());
        dateCol.setPrefWidth(100);
        
        TableColumn<MaintenanceRow, String> vehicleCol = new TableColumn<>("Vehicle");
        vehicleCol.setCellValueFactory(data -> data.getValue().vehicleProperty());
        vehicleCol.setPrefWidth(120);
        
        TableColumn<MaintenanceRow, String> serviceCol = new TableColumn<>("Service Type");
        serviceCol.setCellValueFactory(data -> data.getValue().serviceTypeProperty());
        serviceCol.setPrefWidth(150);
        
        TableColumn<MaintenanceRow, String> providerCol = new TableColumn<>("Provider");
        providerCol.setCellValueFactory(data -> data.getValue().providerProperty());
        providerCol.setPrefWidth(150);
        
        TableColumn<MaintenanceRow, Double> costCol = new TableColumn<>("Total Cost");
        costCol.setCellValueFactory(data -> data.getValue().costProperty().asObject());
        costCol.setPrefWidth(100);
        costCol.setCellFactory(tc -> new TableCell<MaintenanceRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", item));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<MaintenanceRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setPrefWidth(100);
        
        @SuppressWarnings("unchecked")
        TableColumn<MaintenanceRow, ?>[] maintenanceColumns = new TableColumn[] {
            dateCol, vehicleCol, serviceCol, providerCol, costCol, statusCol
        };
        maintenanceTable.getColumns().addAll(maintenanceColumns);
        maintenanceTable.setPlaceholder(new Label("No maintenance data available"));
    }
    
    private VBox createAnalyticsTabContent() {
        VBox content = new VBox(25);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f8f9fa;");
        
        // Expense distribution pie chart with enhanced styling
        VBox pieChartBox = createStyledChartBox("📊 Expense Distribution", createEnhancedPieChart());
        
        // Monthly trends bar chart with enhanced styling
        VBox barChartBox = createStyledChartBox("📈 Monthly Expense Trends", createEnhancedBarChart());
        
        // Add both charts to a horizontal layout for better space utilization
        HBox chartsRow = new HBox(20);
        chartsRow.getChildren().addAll(pieChartBox, barChartBox);
        HBox.setHgrow(pieChartBox, Priority.ALWAYS);
        HBox.setHgrow(barChartBox, Priority.ALWAYS);
        
        content.getChildren().add(chartsRow);
        
        // Add additional analytics below
        HBox analyticsCards = createAnalyticsCards();
        content.getChildren().add(analyticsCards);
        
        return content;
    }
    
    private VBox createStyledChartBox(String title, Node chart) {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: white; " +
                    "-fx-background-radius: 10; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#2c3e50"));
        
        // Ensure the chart has space for axis labels
        if (chart instanceof BarChart) {
            BarChart<?, ?> barChart = (BarChart<?, ?>) chart;
            barChart.setMinHeight(400);
            barChart.setPrefHeight(450);
            
            // Add extra padding to ensure axis labels are visible
            VBox chartContainer = new VBox(chart);
            chartContainer.setPadding(new Insets(0, 20, 20, 40)); // Extra padding for axes
            box.getChildren().addAll(titleLabel, chartContainer);
        } else {
            box.getChildren().addAll(titleLabel, chart);
        }
        
        return box;
    }
    
    private PieChart createEnhancedPieChart() {
        expensePieChart.setPrefSize(400, 350);
        expensePieChart.setMinSize(350, 300);
        expensePieChart.setLegendSide(Side.BOTTOM);
        expensePieChart.setAnimated(true);
        expensePieChart.setLabelLineLength(10);
        expensePieChart.setLabelsVisible(true);
        expensePieChart.setStartAngle(90);
        
        // Apply CSS classes for pie chart
        expensePieChart.getStyleClass().addAll("pie-chart", "expense-pie-chart");
        
        return expensePieChart;
    }
    
    private BarChart<String, Number> createEnhancedBarChart() {
        monthlyExpenseChart.setPrefSize(500, 350);
        monthlyExpenseChart.setMinSize(400, 300);
        monthlyExpenseChart.setAnimated(true);
        monthlyExpenseChart.setLegendVisible(true);
        monthlyExpenseChart.setLegendSide(Side.BOTTOM);
        monthlyExpenseChart.setVerticalGridLinesVisible(false);
        
        // Style the axes
        CategoryAxis xAxis = (CategoryAxis) monthlyExpenseChart.getXAxis();
        xAxis.setLabel("Month");
        xAxis.setTickLabelRotation(30);
        xAxis.setGapStartAndEnd(true);
        xAxis.setTickLabelGap(5);
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarkVisible(true);
        xAxis.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        xAxis.setTickLabelFont(Font.font("Arial", 11));
        
        NumberAxis yAxis = (NumberAxis) monthlyExpenseChart.getYAxis();
        yAxis.setLabel("Total Cost ($)");
        yAxis.setForceZeroInRange(true);
        yAxis.setTickLabelsVisible(true);
        yAxis.setTickMarkVisible(true);
        yAxis.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        yAxis.setTickLabelFont(Font.font("Arial", 11));
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number value) {
                return "$" + String.format("%,.0f", value.doubleValue());
            }
            
            @Override
            public Number fromString(String string) {
                return Double.valueOf(string.replace("$", "").replace(",", ""));
            }
        });
        
        // Remove conflicting inline styles - let CSS handle it
        monthlyExpenseChart.setStyle("");
        
        // Make sure the chart has proper padding for axis labels
        monthlyExpenseChart.setPadding(new Insets(10, 10, 10, 10));
        
        // Apply CSS classes for better styling control
        monthlyExpenseChart.getStyleClass().addAll("bar-chart", "expense-bar-chart", "company-financials-chart");
        
        // Force axis style classes
        xAxis.getStyleClass().addAll("axis", "x-axis", "category-axis");
        yAxis.getStyleClass().addAll("axis", "y-axis", "number-axis");
        
        return monthlyExpenseChart;
    }
    
    private HBox createAnalyticsCards() {
        HBox cards = new HBox(15);
        cards.setPadding(new Insets(10, 0, 0, 0));
        cards.setAlignment(Pos.CENTER);
        
        // Create analytics summary cards
        VBox topExpenseCard = createAnalyticsCard("🔝 Top Expense Category", "Loading...", "#e74c3c");
        VBox avgMonthlyCard = createAnalyticsCard("📊 Avg Monthly Expense", "Loading...", "#3498db");
        VBox trendCard = createAnalyticsCard("📈 Expense Trend", "Loading...", "#2ecc71");
        VBox projectionCard = createAnalyticsCard("🎯 Year Projection", "Loading...", "#f39c12");
        
        cards.getChildren().addAll(topExpenseCard, avgMonthlyCard, trendCard, projectionCard);
        return cards;
    }
    
    private VBox createAnalyticsCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setPrefWidth(180);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; " +
                     "-fx-background-radius: 8; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.web(color));
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        valueLabel.setTextFill(Color.web("#2c3e50"));
        valueLabel.setId(title.replaceAll("[^a-zA-Z]", "") + "Value");
        
        card.getChildren().addAll(titleLabel, valueLabel);
        
        // Add hover effect
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() + 
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 3);"));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace(
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 3);",
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);")));
        
        return card;
    }
    
    private void setupEventHandlers() {
        refreshBtn.setOnAction(e -> refreshData());
        exportBtn.setOnAction(e -> exportToCSV());
        detailsBtn.setOnAction(e -> toggleDetails());
        printPdfBtn.setOnAction(e -> printToPDF());
        
        startDatePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && endDatePicker.getValue() != null) {
                refreshData();
            }
        });
        
        endDatePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && startDatePicker.getValue() != null) {
                refreshData();
            }
        });
    }
    
    private void toggleDetails() {
        detailsVisible = !detailsVisible;
        
        if (detailsVisible) {
            detailsBtn.setText("📋 Hide Details");
            detailsContainer.setVisible(true);
            detailsContainer.setManaged(true);
            
            // Animate appearance
            FadeTransition fade = new FadeTransition(Duration.millis(300), detailsContainer);
            fade.setFromValue(0);
            fade.setToValue(1);
            
            TranslateTransition slide = new TranslateTransition(Duration.millis(300), detailsContainer);
            slide.setFromY(-20);
            slide.setToY(0);
            
            ParallelTransition parallel = new ParallelTransition(fade, slide);
            parallel.play();
        } else {
            detailsBtn.setText("📋 Show Details");
            
            // Animate disappearance
            FadeTransition fade = new FadeTransition(Duration.millis(300), detailsContainer);
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setOnFinished(e -> {
                detailsContainer.setVisible(false);
                detailsContainer.setManaged(false);
            });
            fade.play();
        }
    }
    
    private void refreshData() {
        if (payrollTab == null) {
            showError("PayrollTab not available for data calculation.");
            return;
        }
        
        // Update company name
        String oldCompanyName = this.companyName;
        this.companyName = payrollTab.getCompanyName();
        
        // Update headers if company name changed
        if (!oldCompanyName.equals(this.companyName)) {
            updateCompanyNameInUI();
        }
        
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        
        if (start == null || end == null) {
            showError("Please select both start and end dates.");
            return;
        }
        
        if (end.isBefore(start)) {
            showError("End date must be after start date.");
            return;
        }
        
        // Clear existing data
        financialTable.getItems().clear();
        expenseTable.getItems().clear();
        maintenanceTable.getItems().clear();
        
        // Calculate revenue data
        List<FinancialRow> allRows = calculateRevenueData(start, end);
        financialTable.getItems().addAll(allRows);
        
        // Load expense data
        loadExpenseData(start, end);
        
        // Update charts
        updateCharts();
        
        // Show success message
        showInfo(String.format("Data refreshed for %s to %s", 
            start.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
            end.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))));
        
        // Update visual analytics
        updateVisualAnalytics();
    }
    
    private List<FinancialRow> calculateRevenueData(LocalDate start, LocalDate end) {
        // Map to accumulate totals by driver
        Map<String, DriverTotals> driverTotalsMap = new HashMap<>();
        
        double totalGross = 0.0;
        double totalServiceFee = 0.0;
        double totalCompanyPay = 0.0;
        
        // Start from the Monday of the week containing the start date
        LocalDate currentWeekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        // Calculate the date range display for the summary
        String dateRangeDisplay = String.format("%s - %s", 
            start.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
            end.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
        
        while (!currentWeekStart.isAfter(end)) {
            LocalDate weekEnd = currentWeekStart.plusDays(6); // Sunday
            
            // Only process if this week overlaps with our date range
            if (!weekEnd.isBefore(start)) {
                LocalDate effectiveStart = currentWeekStart.isBefore(start) ? start : currentWeekStart;
                LocalDate effectiveEnd = weekEnd.isAfter(end) ? end : weekEnd;
                
                // Get payroll data for this specific week
                List<PayrollCalculator.PayrollRow> weekRows = payrollTab.getPayrollDataForDateRange(effectiveStart, effectiveEnd);
                
                for (PayrollCalculator.PayrollRow row : weekRows) {
                    double gross = row.gross;
                    double serviceFee = row.serviceFee;
                    double companyPay = row.companyPay;
                    
                    // Accumulate totals by driver
                    DriverTotals driverTotals = driverTotalsMap.computeIfAbsent(
                        row.driverName, 
                        k -> new DriverTotals()
                    );
                    
                    driverTotals.gross += gross;
                    driverTotals.serviceFee += serviceFee;
                    driverTotals.companyPay += companyPay;
                    driverTotals.weekCount++;
                    
                    totalGross += gross;
                    totalServiceFee += serviceFee;
                    totalCompanyPay += companyPay;
                }
            }
            
            currentWeekStart = currentWeekStart.plusWeeks(1);
        }
        
        // Convert accumulated totals to FinancialRow objects
        List<FinancialRow> allRows = new ArrayList<>();
        
        // Sort drivers by name for consistent display
        List<Map.Entry<String, DriverTotals>> sortedDrivers = new ArrayList<>(driverTotalsMap.entrySet());
        sortedDrivers.sort(Map.Entry.comparingByKey());
        
        for (Map.Entry<String, DriverTotals> entry : sortedDrivers) {
            String driverName = entry.getKey();
            DriverTotals totals = entry.getValue();
            
            double companyNet = Math.abs(totals.serviceFee) + totals.companyPay;
            
            // Calculate weekly averages
            double avgGross = totals.weekCount > 0 ? totals.gross / totals.weekCount : 0;
            double avgNet = totals.weekCount > 0 ? companyNet / totals.weekCount : 0;
            
            // Create display with totals and averages
            String periodInfo = String.format("%s (%d weeks)", dateRangeDisplay, totals.weekCount);
            String driverInfo = String.format("%s [Avg/Week: $%,.2f]", driverName, avgGross);
            
            allRows.add(new FinancialRow(
                periodInfo,
                driverInfo,
                String.format("$%,.2f", totals.gross),
                String.format("$%,.2f", Math.abs(totals.serviceFee)),
                String.format("$%,.2f", totals.companyPay),
                String.format("$%,.2f", companyNet)
            ));
        }
        
        // Add summary row if there are multiple drivers
        if (allRows.size() > 1) {
            // Add total row with special marker
            double totalCompanyNet = Math.abs(totalServiceFee) + totalCompanyPay;
            int totalDrivers = driverTotalsMap.size();
            
            allRows.add(new FinancialRow(
                "TOTAL_ROW",  // Special marker for styling
                String.format("Total for %d Drivers", totalDrivers),
                String.format("$%,.2f", totalGross),
                String.format("$%,.2f", Math.abs(totalServiceFee)),
                String.format("$%,.2f", totalCompanyPay),
                String.format("$%,.2f", totalCompanyNet)
            ));
        }
        
        // Update summary labels
        double totalCompanyNet = Math.abs(totalServiceFee) + totalCompanyPay;
        
        totalGrossLabel.setText(String.format("$%,.2f", totalGross));
        totalServiceFeeLabel.setText(String.format("$%,.2f", Math.abs(totalServiceFee)));
        totalCompanyPayLabel.setText(String.format("$%,.2f", totalCompanyPay));
        totalCompanyNetLabel.setText(String.format("$%,.2f", totalCompanyNet));
        
        return allRows;
    }
    
    // Helper class to accumulate driver totals
    private static class DriverTotals {
        double gross = 0.0;
        double serviceFee = 0.0;
        double companyPay = 0.0;
        int weekCount = 0;
    }
    
    private void loadExpenseData(LocalDate start, LocalDate end) {
        // Load company expenses
        currentExpenses = companyExpenseDAO.findByDateRange(start, end);
        
        // Load maintenance records
        try {
            currentMaintenance = maintenanceDAO.findByDateRange(start, end);
        } catch (Exception e) {
            currentMaintenance = new ArrayList<>();
            System.err.println("Error loading maintenance records: " + e.getMessage());
        }
        
        // Calculate totals
        double totalCompanyExpenses = currentExpenses.stream()
            .mapToDouble(CompanyExpense::getAmount)
            .sum();
        
        double totalMaintenanceExpenses = currentMaintenance.stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        
        double totalExpenses = totalCompanyExpenses + totalMaintenanceExpenses;
        
        // Update expense labels
        totalExpensesLabel.setText(String.format("$%,.2f", totalExpenses));
        
        // Calculate final net
        double companyNet = parseMoneyLabel(totalCompanyNetLabel.getText());
        double finalNet = companyNet - totalExpenses;
        finalNetLabel.setText(String.format("$%,.2f", finalNet));
        
        // Update expense grid in basic section
        updateExpenseGrid(totalCompanyExpenses, totalMaintenanceExpenses);
        
        // Populate detailed tables
        populateExpenseTable();
        populateMaintenanceTable();
    }
    
    private void updateExpenseGrid(double companyExpenses, double maintenanceExpenses) {
        GridPane expenseGrid = (GridPane) mainContainer.lookup("#expenseGrid");
        if (expenseGrid != null) {
            expenseGrid.getChildren().clear();
            
            // Company expenses by category
            Map<String, Double> categoryTotals = new HashMap<>();
            for (CompanyExpense expense : currentExpenses) {
                categoryTotals.merge(expense.getCategory(), expense.getAmount(), Double::sum);
            }
            
            int row = 0;
            Label companyExpenseHeader = new Label("Company Expenses:");
            companyExpenseHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            companyExpenseHeader.setTextFill(Color.web("#666666"));
            expenseGrid.add(companyExpenseHeader, 0, row++, 2, 1);
            
            for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                Label catLabel = new Label("  " + entry.getKey() + ":");
                catLabel.setFont(Font.font("Arial", 13));
                catLabel.setTextFill(Color.web("#333333"));
                
                Label amountLabel = new Label(String.format("$%,.2f", entry.getValue()));
                amountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
                amountLabel.setTextFill(Color.web("#D32F2F"));
                
                expenseGrid.add(catLabel, 0, row);
                expenseGrid.add(amountLabel, 1, row);
                GridPane.setHalignment(amountLabel, javafx.geometry.HPos.RIGHT);
                row++;
            }
            
            // Add separator
            Separator sep = new Separator();
            expenseGrid.add(sep, 0, row++, 2, 1);
            GridPane.setMargin(sep, new Insets(5, 0, 5, 0));
            
            // Maintenance summary
            Label maintenanceHeader = new Label("Maintenance Expenses:");
            maintenanceHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            maintenanceHeader.setTextFill(Color.web("#666666"));
            expenseGrid.add(maintenanceHeader, 0, row++, 2, 1);
            
            // Group maintenance by service type
            Map<String, Double> serviceTypeTotals = new HashMap<>();
            for (MaintenanceRecord record : currentMaintenance) {
                serviceTypeTotals.merge(record.getServiceType(), record.getCost(), Double::sum);
            }
            
            for (Map.Entry<String, Double> entry : serviceTypeTotals.entrySet()) {
                Label serviceLabel = new Label("  " + entry.getKey() + ":");
                serviceLabel.setFont(Font.font("Arial", 13));
                serviceLabel.setTextFill(Color.web("#333333"));
                
                Label amountLabel = new Label(String.format("$%,.2f", entry.getValue()));
                amountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
                amountLabel.setTextFill(Color.web("#D32F2F"));
                
                expenseGrid.add(serviceLabel, 0, row);
                expenseGrid.add(amountLabel, 1, row);
                GridPane.setHalignment(amountLabel, javafx.geometry.HPos.RIGHT);
                row++;
            }
            
            // Total row
            Separator sep2 = new Separator();
            expenseGrid.add(sep2, 0, row++, 2, 1);
            GridPane.setMargin(sep2, new Insets(5, 0, 5, 0));
            
            Label totalLabel = new Label("Total Expenses:");
            totalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            totalLabel.setTextFill(Color.web("#333333"));
            
            Label totalAmountLabel = new Label(String.format("$%,.2f", companyExpenses + maintenanceExpenses));
            totalAmountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            totalAmountLabel.setTextFill(Color.web("#B71C1C"));
            
            expenseGrid.add(totalLabel, 0, row);
            expenseGrid.add(totalAmountLabel, 1, row);
            GridPane.setHalignment(totalAmountLabel, javafx.geometry.HPos.RIGHT);
        }
    }
    
    private void populateExpenseTable() {
        ObservableList<ExpenseRow> expenseRows = FXCollections.observableArrayList();
        
        for (CompanyExpense expense : currentExpenses) {
            expenseRows.add(new ExpenseRow(
                expense.getExpenseDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                expense.getVendor(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getStatus()
            ));
        }
        
        expenseTable.setItems(expenseRows);
    }
    
    private void populateMaintenanceTable() {
        ObservableList<MaintenanceRow> maintenanceRows = FXCollections.observableArrayList();
        
        for (MaintenanceRecord record : currentMaintenance) {
            maintenanceRows.add(new MaintenanceRow(
                record.getServiceDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                record.getVehicle(),
                record.getServiceType(),
                record.getServiceProvider() != null ? record.getServiceProvider() : record.getTechnician(),
                record.getCost(),
                record.getStatus()
            ));
        }
        
        maintenanceTable.setItems(maintenanceRows);
    }
    
    private void updateCharts() {
        // Update pie chart with enhanced formatting
        Map<String, Double> combinedCategories = new LinkedHashMap<>();
        
        // Add company expense categories
        for (CompanyExpense expense : currentExpenses) {
            if (expense.getCategory() != null && expense.getAmount() > 0) {
                combinedCategories.merge(expense.getCategory(), expense.getAmount(), Double::sum);
            }
        }
        
        // Add maintenance as a category
        double maintenanceTotal = currentMaintenance.stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        if (maintenanceTotal > 0) {
            combinedCategories.put("Maintenance", maintenanceTotal);
        }
        
        // Sort by value descending for better visualization
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(combinedCategories.entrySet());
        sortedEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        double total = sortedEntries.stream().mapToDouble(Map.Entry::getValue).sum();
        
        for (Map.Entry<String, Double> entry : sortedEntries) {
            double percentage = (entry.getValue() / total) * 100;
            PieChart.Data slice = new PieChart.Data(
                String.format("%s\n$%,.0f (%.1f%%)", entry.getKey(), entry.getValue(), percentage), 
                entry.getValue()
            );
            pieData.add(slice);
        }
        
        expensePieChart.setData(pieData);
        
        // Apply custom colors to pie slices
        String[] colors = {"#3498db", "#e74c3c", "#2ecc71", "#f39c12", "#9b59b6", 
                          "#1abc9c", "#34495e", "#e67e22", "#95a5a6", "#d35400"};
        
        for (int i = 0; i < pieData.size() && i < colors.length; i++) {
            PieChart.Data data = pieData.get(i);
            data.getNode().setStyle("-fx-pie-color: " + colors[i] + ";");
            
            // Add tooltip
            String label = data.getName().split("\n")[0]; // Get category name from label
            Tooltip tooltip = new Tooltip(String.format("%s\nAmount: $%,.2f\nPercentage: %.1f%%",
                label, data.getPieValue(), (data.getPieValue() / total) * 100));
            Tooltip.install(data.getNode(), tooltip);
            
            // Add hover effect
            data.getNode().setOnMouseEntered(e -> {
                data.getNode().setScaleX(1.1);
                data.getNode().setScaleY(1.1);
            });
            data.getNode().setOnMouseExited(e -> {
                data.getNode().setScaleX(1.0);
                data.getNode().setScaleY(1.0);
            });
        }
        
        // Update bar chart (monthly trends)
        updateMonthlyTrendsChart();
        
        // Update analytics cards
        updateAnalyticsCards();
    }
    
    private void updateMonthlyTrendsChart() {
        monthlyExpenseChart.getData().clear();
        
        // Group expenses by month name (aggregate across all years)
        Map<Integer, Double> monthlyExpensesMap = new TreeMap<>();
        Map<Integer, Double> monthlyMaintenanceMap = new TreeMap<>();
        
        String[] monthNames = {"January", "February", "March", "April", "May", "June", 
                              "July", "August", "September", "October", "November", "December"};
        
        // Initialize all months with 0
        for (int i = 1; i <= 12; i++) {
            monthlyExpensesMap.put(i, 0.0);
            monthlyMaintenanceMap.put(i, 0.0);
        }
        
        // Process company expenses - aggregate by month across all years
        for (CompanyExpense expense : currentExpenses) {
            int month = expense.getExpenseDate().getMonthValue();
            monthlyExpensesMap.merge(month, expense.getAmount(), Double::sum);
        }
        
        // Process maintenance - aggregate by month across all years
        for (MaintenanceRecord record : currentMaintenance) {
            int month = record.getServiceDate().getMonthValue();
            monthlyMaintenanceMap.merge(month, record.getCost(), Double::sum);
        }
        
        // Create series with styled data
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Company Expenses");
        
        XYChart.Series<String, Number> maintenanceSeries = new XYChart.Series<>();
        maintenanceSeries.setName("Maintenance Costs");
        
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total Expenses");
        
        // Add data for each month
        for (int monthNum = 1; monthNum <= 12; monthNum++) {
            String monthLabel = monthNames[monthNum - 1];
            double expenses = monthlyExpensesMap.get(monthNum);
            double maintenance = monthlyMaintenanceMap.get(monthNum);
            double total = expenses + maintenance;
            
            // Only add to chart if there's data for this month
            if (total > 0) {
                XYChart.Data<String, Number> expenseData = new XYChart.Data<>(monthLabel, expenses);
                XYChart.Data<String, Number> maintenanceData = new XYChart.Data<>(monthLabel, maintenance);
                XYChart.Data<String, Number> totalData = new XYChart.Data<>(monthLabel, total);
                
                expenseSeries.getData().add(expenseData);
                maintenanceSeries.getData().add(maintenanceData);
                totalSeries.getData().add(totalData);
            }
        }
        
        @SuppressWarnings("unchecked")
        XYChart.Series<String, Number>[] series = new XYChart.Series[] {
            expenseSeries, maintenanceSeries, totalSeries
        };
        monthlyExpenseChart.getData().addAll(series);
        
        // Ensure axes are properly rendered with labels
        Platform.runLater(() -> {
            // Force axis label refresh
            CategoryAxis xAxis = (CategoryAxis) monthlyExpenseChart.getXAxis();
            xAxis.setLabel("Month");
            xAxis.requestLayout();
            
            NumberAxis yAxis = (NumberAxis) monthlyExpenseChart.getYAxis();
            yAxis.setLabel("Total Cost ($)");
            yAxis.requestLayout();
            
            monthlyExpenseChart.requestLayout();
        });
        
        // Style the bars
        Platform.runLater(() -> {
            // Company Expenses - Blue
            for (XYChart.Data<String, Number> data : expenseSeries.getData()) {
                Node bar = data.getNode();
                if (bar != null) {
                    bar.setStyle("-fx-bar-fill: #3498db;");
                    
                    // Add tooltip
                    Tooltip tooltip = new Tooltip(String.format("Company Expenses\n%s Total: $%,.2f", 
                        data.getXValue(), data.getYValue().doubleValue()));
                    Tooltip.install(bar, tooltip);
                }
            }
            
            // Maintenance - Red
            for (XYChart.Data<String, Number> data : maintenanceSeries.getData()) {
                Node bar = data.getNode();
                if (bar != null) {
                    bar.setStyle("-fx-bar-fill: #e74c3c;");
                    
                    // Add tooltip
                    Tooltip tooltip = new Tooltip(String.format("Maintenance Costs\n%s Total: $%,.2f", 
                        data.getXValue(), data.getYValue().doubleValue()));
                    Tooltip.install(bar, tooltip);
                }
            }
            
            // Total - Green
            for (XYChart.Data<String, Number> data : totalSeries.getData()) {
                Node bar = data.getNode();
                if (bar != null) {
                    bar.setStyle("-fx-bar-fill: #2ecc71;");
                    
                    // Add tooltip
                    Tooltip tooltip = new Tooltip(String.format("Total Expenses\n%s Total: $%,.2f", 
                        data.getXValue(), data.getYValue().doubleValue()));
                    Tooltip.install(bar, tooltip);
                }
            }
        });
    }
    
    private void updateAnalyticsCards() {
        Platform.runLater(() -> {
            // Find top expense category
            Map<String, Double> categoryTotals = new HashMap<>();
            for (CompanyExpense expense : currentExpenses) {
                if (expense.getCategory() != null) {
                    categoryTotals.merge(expense.getCategory(), expense.getAmount(), Double::sum);
                }
            }
            
            String topCategory = "N/A";
            double topAmount = 0;
            for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                if (entry.getValue() > topAmount) {
                    topAmount = entry.getValue();
                    topCategory = entry.getKey();
                }
            }
            
            Label topExpenseValue = (Label) mainContainer.lookup("#TopExpenseCategoryValue");
            if (topExpenseValue != null) {
                topExpenseValue.setText(String.format("%s\n$%,.0f", topCategory, topAmount));
            }
            
            // Calculate average monthly expense
            Set<LocalDate> months = new HashSet<>();
            double totalExpenses = 0;
            
            for (CompanyExpense expense : currentExpenses) {
                months.add(expense.getExpenseDate().withDayOfMonth(1));
                totalExpenses += expense.getAmount();
            }
            
            for (MaintenanceRecord record : currentMaintenance) {
                months.add(record.getServiceDate().withDayOfMonth(1));
                totalExpenses += record.getCost();
            }
            
            double avgMonthly = months.isEmpty() ? 0 : totalExpenses / months.size();
            
            Label avgMonthlyValue = (Label) mainContainer.lookup("#AvgMonthlyExpenseValue");
            if (avgMonthlyValue != null) {
                avgMonthlyValue.setText(String.format("$%,.0f", avgMonthly));
            }
            
            // Calculate trend
            String trend = "Stable";
            if (months.size() >= 2) {
                List<LocalDate> sortedMonths = new ArrayList<>(months);
                sortedMonths.sort(LocalDate::compareTo);
                
                LocalDate lastMonth = sortedMonths.get(sortedMonths.size() - 1);
                LocalDate previousMonth = sortedMonths.get(sortedMonths.size() - 2);
                
                double lastMonthTotal = 0;
                double previousMonthTotal = 0;
                
                for (CompanyExpense expense : currentExpenses) {
                    LocalDate expMonth = expense.getExpenseDate().withDayOfMonth(1);
                    if (expMonth.equals(lastMonth)) lastMonthTotal += expense.getAmount();
                    if (expMonth.equals(previousMonth)) previousMonthTotal += expense.getAmount();
                }
                
                for (MaintenanceRecord record : currentMaintenance) {
                    LocalDate recMonth = record.getServiceDate().withDayOfMonth(1);
                    if (recMonth.equals(lastMonth)) lastMonthTotal += record.getCost();
                    if (recMonth.equals(previousMonth)) previousMonthTotal += record.getCost();
                }
                
                double change = ((lastMonthTotal - previousMonthTotal) / previousMonthTotal) * 100;
                if (change > 5) trend = String.format("↑ %.1f%%", change);
                else if (change < -5) trend = String.format("↓ %.1f%%", Math.abs(change));
                else trend = "→ Stable";
            }
            
            Label trendValue = (Label) mainContainer.lookup("#ExpenseTrendValue");
            if (trendValue != null) {
                trendValue.setText(trend);
                if (trend.startsWith("↑")) trendValue.setTextFill(Color.web("#e74c3c"));
                else if (trend.startsWith("↓")) trendValue.setTextFill(Color.web("#2ecc71"));
                else trendValue.setTextFill(Color.web("#3498db"));
            }
            
            // Year projection
            double yearProjection = avgMonthly * 12;
            Label projectionValue = (Label) mainContainer.lookup("#YearProjectionValue");
            if (projectionValue != null) {
                projectionValue.setText(String.format("$%,.0f", yearProjection));
            }
        });
    }
    
    private double parseMoneyLabel(String text) {
        return Double.parseDouble(text.replaceAll("[^0-9.-]", ""));
    }
    
    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Financial Data");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("financial_report_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                // Write header
                writer.println("Company Financial Report");
                writer.println("Date Range: " + startDatePicker.getValue() + " to " + endDatePicker.getValue());
                writer.println();
                
                // Write summary
                writer.println("SUMMARY");
                writer.println("Total Gross Revenue," + totalGrossLabel.getText());
                writer.println("Total Service Fee," + totalServiceFeeLabel.getText());
                writer.println("Total Company Pay," + totalCompanyPayLabel.getText());
                writer.println("Total Company Revenue," + totalCompanyNetLabel.getText());
                writer.println("Total Expenses," + totalExpensesLabel.getText());
                writer.println("Net Profit," + finalNetLabel.getText());
                writer.println();
                
                // Write revenue details
                writer.println("REVENUE DETAILS");
                writer.println("Week,Driver,Gross Revenue,Service Fee,Company Pay,Company Revenue");
                for (FinancialRow row : financialTable.getItems()) {
                    writer.printf("%s,%s,%s,%s,%s,%s%n",
                        row.weekProperty().get(),
                        row.driverProperty().get(),
                        row.grossProperty().get(),
                        row.serviceFeeProperty().get(),
                        row.companyPayProperty().get(),
                        row.companyNetProperty().get()
                    );
                }
                writer.println();
                
                // Write expense details
                writer.println("COMPANY EXPENSES");
                writer.println("Date,Vendor,Category,Description,Amount,Status");
                for (CompanyExpense expense : currentExpenses) {
                    writer.printf("%s,%s,%s,%s,$%.2f,%s%n",
                        expense.getExpenseDate(),
                        expense.getVendor(),
                        expense.getCategory(),
                        expense.getDescription().replace(",", ";"),
                        expense.getAmount(),
                        expense.getStatus()
                    );
                }
                writer.println();
                
                // Write maintenance details
                writer.println("MAINTENANCE EXPENSES");
                writer.println("Date,Vehicle,Service Type,Provider,Cost,Status");
                for (MaintenanceRecord record : currentMaintenance) {
                    writer.printf("%s,%s,%s,%s,$%.2f,%s%n",
                        record.getServiceDate(),
                        record.getVehicle(),
                        record.getServiceType(),
                        record.getServiceProvider() != null ? record.getServiceProvider() : record.getTechnician(),
                        record.getCost(),
                        record.getStatus()
                    );
                }
                
                showInfo("Financial report exported successfully!");
            } catch (IOException e) {
                showError("Failed to export report: " + e.getMessage());
            }
        }
    }
    
    private void printToPDF() {
        if (financialTable.getItems().isEmpty()) {
            showError("No data to print. Please refresh data first.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Financial Report PDF");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("financial_report_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                PDFExporter pdfExporter = new PDFExporter(companyName);
                
                // Create breakdown rows for expense categories
                ObservableList<BreakdownRow> breakdownRows = FXCollections.observableArrayList();
                
                // Add company expense categories
                Map<String, Double> categoryTotals = new HashMap<>();
                for (CompanyExpense expense : currentExpenses) {
                    categoryTotals.merge(expense.getCategory(), expense.getAmount(), Double::sum);
                }
                
                for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                    breakdownRows.add(new BreakdownRow(
                        entry.getKey(),
                        "-", // No gross for expenses
                        "-", // No service fee for expenses
                        "-", // No company pay for expenses
                        String.format("$%,.2f", entry.getValue())
                    ));
                }
                
                // Add maintenance summary
                double maintenanceTotal = currentMaintenance.stream()
                    .mapToDouble(MaintenanceRecord::getCost)
                    .sum();
                if (maintenanceTotal > 0) {
                    breakdownRows.add(new BreakdownRow(
                        "Maintenance",
                        "-",
                        "-",
                        "-",
                        String.format("$%,.2f", maintenanceTotal)
                    ));
                }
                
                // Generate comprehensive financial report
                pdfExporter.generateComprehensiveFinancialReport(
                    file,
                    startDatePicker.getValue(),
                    endDatePicker.getValue(),
                    totalGrossLabel.getText(),
                    totalServiceFeeLabel.getText(),
                    totalCompanyPayLabel.getText(),
                    totalCompanyNetLabel.getText(),
                    totalExpensesLabel.getText(),
                    finalNetLabel.getText(),
                    financialTable.getItems(),
                    currentExpenses,
                    currentMaintenance,
                    breakdownRows
                );
                
                showInfo("Financial report PDF generated successfully!");
            } catch (Exception e) {
                showError("Failed to generate PDF: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Update Visual Analytics charts with real data
     */
    private void updateVisualAnalytics() {
        // Get the Visual Analytics tab content
        TabPane mainTabPane = (TabPane) ((VBox) getCenter()).getChildren().get(2);
        if (mainTabPane.getTabs().size() < 2) return;
        
        Tab visualTab = mainTabPane.getTabs().get(1);
        ScrollPane scrollPane = (ScrollPane) visualTab.getContent();
        VBox analyticsContainer = (VBox) scrollPane.getContent();
        
        // Update charts with real data
        updateRevenueExpenseLineChart(analyticsContainer);
        updateExpenseDistributionPieChart(analyticsContainer);
        updateMonthlyPerformanceBarChart(analyticsContainer);
        updateProfitMarginAreaChart(analyticsContainer);
        updateKeyMetrics(analyticsContainer);
        updateFinancialHealthScore(analyticsContainer);
    }
    
    private void updateRevenueExpenseLineChart(VBox container) {
        // Find the chart in the container
        @SuppressWarnings("unchecked")
        LineChart<String, Number> chart = findChart(container, LineChart.class);
        if (chart == null) return;
        
        chart.getData().clear();
        
        // Create revenue series
        XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
        revenueSeries.setName("Revenue");
        
        // Create expense series
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");
        
        // Group data by month
        Map<String, Double> monthlyRevenue = new LinkedHashMap<>();
        Map<String, Double> monthlyExpenses = new LinkedHashMap<>();
        
        // Process financial table data
        for (FinancialRow row : financialTable.getItems()) {
            LocalDate weekStart = LocalDate.parse(row.getWeek().replace("Week of ", ""), 
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            String monthKey = weekStart.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            
            double gross = parseAmount(row.getGross());
            monthlyRevenue.merge(monthKey, gross, Double::sum);
        }
        
        // Process expense data
        LocalDate current = startDatePicker.getValue();
        while (!current.isAfter(endDatePicker.getValue())) {
            String monthKey = current.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            final LocalDate currentMonth = current; // Make it final for lambda
            
            double expenses = currentExpenses.stream()
                .filter(e -> e.getExpenseDate().getMonth() == currentMonth.getMonth() && 
                           e.getExpenseDate().getYear() == currentMonth.getYear())
                .mapToDouble(CompanyExpense::getAmount)
                .sum();
            
            expenses += currentMaintenance.stream()
                .filter(m -> m.getDate().getMonth() == currentMonth.getMonth() && 
                           m.getDate().getYear() == currentMonth.getYear())
                .mapToDouble(MaintenanceRecord::getCost)
                .sum();
            
            monthlyExpenses.put(monthKey, expenses);
            current = current.plusMonths(1);
        }
        
        // Add data to series with labels
        for (String month : monthlyRevenue.keySet()) {
            double revenueValue = monthlyRevenue.get(month);
            double expenseValue = monthlyExpenses.getOrDefault(month, 0.0);
            
            XYChart.Data<String, Number> revenueData = new XYChart.Data<>(month, revenueValue);
            XYChart.Data<String, Number> expenseData = new XYChart.Data<>(month, expenseValue);
            
            revenueSeries.getData().add(revenueData);
            expenseSeries.getData().add(expenseData);
        }
        
        @SuppressWarnings("unchecked")
        XYChart.Series<String, Number>[] series = new XYChart.Series[] {
            revenueSeries, expenseSeries
        };
        chart.getData().addAll(series);
        
        // Add value labels to data points
        for (XYChart.Series<String, Number> dataSeries : chart.getData()) {
            for (XYChart.Data<String, Number> data : dataSeries.getData()) {
                addDataLabel(data);
            }
        }
    }
    
    private void updateExpenseDistributionPieChart(VBox container) {
        PieChart chart = findChart(container, PieChart.class);
        if (chart == null) return;
        
        chart.getData().clear();
        
        // Calculate expense categories
        Map<String, Double> categoryTotals = new HashMap<>();
        
        // Company expenses by category
        for (CompanyExpense expense : currentExpenses) {
            categoryTotals.merge(expense.getCategory(), expense.getAmount(), Double::sum);
        }
        
        // Maintenance as a category
        double maintenanceTotal = currentMaintenance.stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        if (maintenanceTotal > 0) {
            categoryTotals.put("Maintenance", maintenanceTotal);
        }
        
        // Add driver payments as a category
        double driverPayments = financialTable.getItems().stream()
            .mapToDouble(row -> parseAmount(row.getCompanyPay()))
            .sum();
        if (driverPayments > 0) {
            categoryTotals.put("Driver Payments", driverPayments);
        }
        
        // Calculate total for percentages
        double total = categoryTotals.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // Create pie chart data with percentages
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            double percentage = (entry.getValue() / total) * 100;
            PieChart.Data slice = new PieChart.Data(
                String.format("%s\n$%,.0f (%.1f%%)", entry.getKey(), entry.getValue(), percentage), 
                entry.getValue()
            );
            chart.getData().add(slice);
        }
        
        // Style the pie chart
        chart.setLabelLineLength(20);
        chart.setLabelsVisible(true);
    }
    
    private void updateMonthlyPerformanceBarChart(VBox container) {
        @SuppressWarnings("unchecked")
        BarChart<String, Number> chart = findChart(container, BarChart.class);
        if (chart == null) return;
        
        chart.getData().clear();
        
        // Create series
        XYChart.Series<String, Number> grossSeries = new XYChart.Series<>();
        grossSeries.setName("Gross Revenue");
        
        XYChart.Series<String, Number> netSeries = new XYChart.Series<>();
        netSeries.setName("Net Revenue");
        
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");
        
        // Group by month
        Map<String, Double> monthlyGross = new LinkedHashMap<>();
        Map<String, Double> monthlyExpense = new LinkedHashMap<>();
        
        // Process financial data
        for (FinancialRow row : financialTable.getItems()) {
            LocalDate weekStart = LocalDate.parse(row.getWeek().replace("Week of ", ""), 
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            String monthKey = weekStart.format(DateTimeFormatter.ofPattern("MMM"));
            
            double gross = parseAmount(row.getGross());
            monthlyGross.merge(monthKey, gross, Double::sum);
        }
        
        // Process expenses
        for (CompanyExpense expense : currentExpenses) {
            String monthKey = expense.getExpenseDate().format(DateTimeFormatter.ofPattern("MMM"));
            monthlyExpense.merge(monthKey, expense.getAmount(), Double::sum);
        }
        
        for (MaintenanceRecord record : currentMaintenance) {
            String monthKey = record.getDate().format(DateTimeFormatter.ofPattern("MMM"));
            monthlyExpense.merge(monthKey, record.getCost(), Double::sum);
        }
        
        // Add data to series
        for (String month : monthlyGross.keySet()) {
            double gross = monthlyGross.get(month);
            double expense = monthlyExpense.getOrDefault(month, 0.0);
            double net = gross - expense;
            
            grossSeries.getData().add(new XYChart.Data<>(month, gross));
            netSeries.getData().add(new XYChart.Data<>(month, net));
            expenseSeries.getData().add(new XYChart.Data<>(month, expense));
        }
        
        @SuppressWarnings("unchecked")
        XYChart.Series<String, Number>[] series = new XYChart.Series[] {
            grossSeries, netSeries, expenseSeries
        };
        chart.getData().addAll(series);
        
        // Add value labels to bars
        for (XYChart.Series<String, Number> dataSeries : chart.getData()) {
            for (XYChart.Data<String, Number> data : dataSeries.getData()) {
                addBarLabel(data);
            }
        }
    }
    
    private void updateProfitMarginAreaChart(VBox container) {
        @SuppressWarnings("unchecked")
        AreaChart<String, Number> chart = findChart(container, AreaChart.class);
        if (chart == null) return;
        
        chart.getData().clear();
        
        XYChart.Series<String, Number> marginSeries = new XYChart.Series<>();
        marginSeries.setName("Profit Margin %");
        
        // Calculate profit margin by week
        for (FinancialRow row : financialTable.getItems()) {
            double gross = parseAmount(row.getGross());
            double net = parseAmount(row.getCompanyNet());
            double margin = gross > 0 ? (net / gross) * 100 : 0;
            
            String weekLabel = row.getWeek().replace("Week of ", "");
            marginSeries.getData().add(new XYChart.Data<>(weekLabel, margin));
        }
        
        chart.getData().add(marginSeries);
        
        // Add percentage labels
        for (XYChart.Data<String, Number> data : marginSeries.getData()) {
            addPercentageLabel(data);
        }
    }
    
    private void updateKeyMetrics(VBox container) {
        // Find metrics box
        HBox metricsBox = null;
        for (Node node : container.getChildren()) {
            if (node instanceof HBox && ((HBox) node).getChildren().size() == 4) {
                metricsBox = (HBox) node;
                break;
            }
        }
        if (metricsBox == null) return;
        
        // Calculate metrics
        double currentRevenue = financialTable.getItems().stream()
            .mapToDouble(row -> parseAmount(row.getGross()))
            .sum();
        
        double totalExpenses = currentExpenses.stream()
            .mapToDouble(CompanyExpense::getAmount)
            .sum() + currentMaintenance.stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        
        double expenseRatio = currentRevenue > 0 ? (totalExpenses / currentRevenue) * 100 : 0;
        double profitMargin = currentRevenue > 0 ? ((currentRevenue - totalExpenses) / currentRevenue) * 100 : 0;
        double cashFlow = currentRevenue - totalExpenses;
        
        // Update metric cards
        updateMetricCard((VBox) metricsBox.getChildren().get(0), 
            String.format("%+.1f%%", calculateGrowthRate()), 
            calculateGrowthRate() >= 0 ? "↑" : "↓",
            calculateGrowthRate() >= 0 ? Color.web("#4CAF50") : Color.web("#F44336"));
        
        updateMetricCard((VBox) metricsBox.getChildren().get(1), 
            String.format("%.1f%%", expenseRatio), 
            expenseRatio > 70 ? "↑" : "→",
            expenseRatio > 70 ? Color.web("#F44336") : Color.web("#FF9800"));
        
        updateMetricCard((VBox) metricsBox.getChildren().get(2), 
            String.format("%.1f%%", profitMargin), 
            profitMargin > 25 ? "↑" : "↓",
            profitMargin > 25 ? Color.web("#2196F3") : Color.web("#FF9800"));
        
        updateMetricCard((VBox) metricsBox.getChildren().get(3), 
            String.format("$%,.0f", cashFlow), 
            cashFlow > 0 ? "↑" : "↓",
            cashFlow > 0 ? Color.web("#9C27B0") : Color.web("#F44336"));
    }
    
    private void updateMetricCard(VBox card, String value, String indicator, Color indicatorColor) {
        // Update value and indicator
        HBox valueBox = (HBox) card.getChildren().get(1);
        Label valueLabel = (Label) valueBox.getChildren().get(0);
        Label indicatorLabel = (Label) valueBox.getChildren().get(1);
        
        valueLabel.setText(value);
        indicatorLabel.setText(indicator);
        indicatorLabel.setTextFill(indicatorColor);
    }
    
    private void updateFinancialHealthScore(VBox container) {
        // Find health score box
        VBox healthBox = null;
        for (Node node : container.getChildren()) {
            if (node instanceof VBox) {
                VBox vbox = (VBox) node;
                if (!vbox.getChildren().isEmpty() && 
                    vbox.getChildren().get(0) instanceof Label) {
                    Label titleLabel = (Label) vbox.getChildren().get(0);
                    if (titleLabel.getId() != null && titleLabel.getId().equals("healthTitle")) {
                        healthBox = vbox;
                        // Update the title with current company name
                        titleLabel.setText(companyName + " - Financial Health Score");
                        break;
                    }
                }
            }
        }
        if (healthBox == null) return;
        
        // Calculate health score (0-100)
        double score = calculateHealthScore();
        String status = score >= 80 ? "EXCELLENT" : score >= 60 ? "GOOD" : score >= 40 ? "FAIR" : "POOR";
        Color scoreColor = score >= 80 ? Color.web("#4CAF50") : 
                          score >= 60 ? Color.web("#2196F3") : 
                          score >= 40 ? Color.web("#FF9800") : Color.web("#F44336");
        
        // Update score visualization
        StackPane scorePane = (StackPane) healthBox.getChildren().get(1);
        Arc scoreArc = (Arc) scorePane.getChildren().get(1);
        scoreArc.setLength(-score * 3.6); // Convert to degrees
        scoreArc.setStroke(scoreColor);
        
        VBox scoreContent = (VBox) scorePane.getChildren().get(2);
        Label scoreLabel = (Label) scoreContent.getChildren().get(0);
        Label statusLabel = (Label) scoreContent.getChildren().get(1);
        
        scoreLabel.setText(String.format("%.0f", score));
        scoreLabel.setTextFill(scoreColor);
        statusLabel.setText(status);
        statusLabel.setTextFill(scoreColor);
    }
    
    private double calculateHealthScore() {
        // Simple health score calculation based on various metrics
        double score = 50; // Base score
        
        // Revenue metrics
        double revenue = financialTable.getItems().stream()
            .mapToDouble(row -> parseAmount(row.getGross()))
            .sum();
        
        double expenses = currentExpenses.stream()
            .mapToDouble(CompanyExpense::getAmount)
            .sum() + currentMaintenance.stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        
        // Profitability factor
        double profitMargin = revenue > 0 ? ((revenue - expenses) / revenue) : 0;
        score += profitMargin * 30; // Up to 30 points for profit margin
        
        // Growth factor
        double growth = calculateGrowthRate();
        score += Math.min(growth, 20); // Up to 20 points for growth
        
        return Math.max(0, Math.min(100, score));
    }
    
    private double calculateGrowthRate() {
        // Simple growth calculation - would need historical data for accurate calculation
        // For now, return a placeholder
        return 12.5;
    }
    
    private double parseAmount(String amount) {
        return Double.parseDouble(amount.replaceAll("[^0-9.-]", ""));
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Chart> T findChart(VBox container, Class<T> chartClass) {
        for (Node node : container.getChildren()) {
            if (node instanceof GridPane) {
                GridPane grid = (GridPane) node;
                for (Node child : grid.getChildren()) {
                    if (child instanceof VBox) {
                        VBox box = (VBox) child;
                        for (Node chartNode : box.getChildren()) {
                            if (chartClass.isInstance(chartNode)) {
                                return (T) chartNode;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Update company name in UI headers
     */
    private void updateCompanyNameInUI() {
        // Update main header
        Label companyTitle = (Label) lookup("#companyTitle");
        if (companyTitle != null) {
            companyTitle.setText(companyName + " Financial Dashboard");
        }
        
        // Update analytics header
        Label analyticsTitle = (Label) lookup("#analyticsTitle");
        if (analyticsTitle != null) {
            analyticsTitle.setText(companyName + " - Visual Analytics Dashboard");
        }
        
        // Update health score title
        Label healthTitle = (Label) lookup("#healthTitle");
        if (healthTitle != null) {
            healthTitle.setText(companyName + " - Financial Health Score");
        }
    }
    
    /**
     * Refresh company name from PayrollTab
     */
    public void refreshCompanyName() {
        if (payrollTab != null) {
            this.companyName = payrollTab.getCompanyName();
            updateCompanyNameInUI();
        }
    }
    
    /**
     * Add data label to line/area chart data point
     */
    private void addDataLabel(XYChart.Data<String, Number> data) {
        double value = data.getYValue().doubleValue();
        Label label = new Label(String.format("$%,.0f", value));
        label.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        
        StackPane node = new StackPane();
        node.getChildren().add(label);
        data.setNode(node);
    }
    
    /**
     * Add value label to bar chart
     */
    private void addBarLabel(XYChart.Data<String, Number> data) {
        double value = data.getYValue().doubleValue();
        Label label = new Label(String.format("$%,.0f", value));
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: white; " +
                      "-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 2 4 2 4; " +
                      "-fx-background-radius: 3px;");
        
        // Position label above the bar
        data.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-bar-fill: #1976D2;");
                StackPane container = new StackPane();
                container.getChildren().addAll(newNode, label);
                StackPane.setAlignment(label, Pos.TOP_CENTER);
                data.setNode(container);
            }
        });
    }
    
    /**
     * Add percentage label to area chart
     */
    private void addPercentageLabel(XYChart.Data<String, Number> data) {
        double value = data.getYValue().doubleValue();
        Label label = new Label(String.format("%.1f%%", value));
        label.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        
        StackPane node = new StackPane();
        node.getChildren().add(label);
        data.setNode(node);
    }
    
    /**
     * Financial Row class for displaying driver financial data
     */
    public static class FinancialRow {
        private final SimpleStringProperty week;
        private final SimpleStringProperty driver;
        private final SimpleStringProperty gross;
        private final SimpleStringProperty serviceFee;
        private final SimpleStringProperty companyPay;
        private final SimpleStringProperty companyNet;
        
        public FinancialRow(String week, String driver, String gross, String serviceFee, String companyPay, String companyNet) {
            this.week = new SimpleStringProperty(week);
            this.driver = new SimpleStringProperty(driver);
            this.gross = new SimpleStringProperty(gross);
            this.serviceFee = new SimpleStringProperty(serviceFee);
            this.companyPay = new SimpleStringProperty(companyPay);
            this.companyNet = new SimpleStringProperty(companyNet);
        }
        
        public SimpleStringProperty weekProperty() { return week; }
        public SimpleStringProperty driverProperty() { return driver; }
        public SimpleStringProperty grossProperty() { return gross; }
        public SimpleStringProperty serviceFeeProperty() { return serviceFee; }
        public SimpleStringProperty companyPayProperty() { return companyPay; }
        public SimpleStringProperty companyNetProperty() { return companyNet; }
        
        // Getter methods for easier access
        public String getWeek() { return week.get(); }
        public String getDriver() { return driver.get(); }
        public String getGross() { return gross.get(); }
        public String getServiceFee() { return serviceFee.get(); }
        public String getCompanyPay() { return companyPay.get(); }
        public String getCompanyNet() { return companyNet.get(); }
    }
    
    /**
     * Expense Row class for displaying company expense data
     */
    public static class ExpenseRow {
        private final SimpleStringProperty date;
        private final SimpleStringProperty vendor;
        private final SimpleStringProperty category;
        private final SimpleStringProperty description;
        private final SimpleDoubleProperty amount;
        private final SimpleStringProperty status;
        
        public ExpenseRow(String date, String vendor, String category, String description, double amount, String status) {
            this.date = new SimpleStringProperty(date);
            this.vendor = new SimpleStringProperty(vendor);
            this.category = new SimpleStringProperty(category);
            this.description = new SimpleStringProperty(description);
            this.amount = new SimpleDoubleProperty(amount);
            this.status = new SimpleStringProperty(status);
        }
        
        public SimpleStringProperty dateProperty() { return date; }
        public SimpleStringProperty vendorProperty() { return vendor; }
        public SimpleStringProperty categoryProperty() { return category; }
        public SimpleStringProperty descriptionProperty() { return description; }
        public SimpleDoubleProperty amountProperty() { return amount; }
        public SimpleStringProperty statusProperty() { return status; }
    }
    
    /**
     * Maintenance Row class for displaying maintenance data
     */
    public static class MaintenanceRow {
        private final SimpleStringProperty date;
        private final SimpleStringProperty vehicle;
        private final SimpleStringProperty serviceType;
        private final SimpleStringProperty provider;
        private final SimpleDoubleProperty cost;
        private final SimpleStringProperty status;
        
        public MaintenanceRow(String date, String vehicle, String serviceType, String provider, double cost, String status) {
            this.date = new SimpleStringProperty(date);
            this.vehicle = new SimpleStringProperty(vehicle);
            this.serviceType = new SimpleStringProperty(serviceType);
            this.provider = new SimpleStringProperty(provider);
            this.cost = new SimpleDoubleProperty(cost);
            this.status = new SimpleStringProperty(status);
        }
        
        public SimpleStringProperty dateProperty() { return date; }
        public SimpleStringProperty vehicleProperty() { return vehicle; }
        public SimpleStringProperty serviceTypeProperty() { return serviceType; }
        public SimpleStringProperty providerProperty() { return provider; }
        public SimpleDoubleProperty costProperty() { return cost; }
        public SimpleStringProperty statusProperty() { return status; }
    }
    
    /**
     * Breakdown Row class for displaying expense breakdown data
     */
    public static class BreakdownRow {
        private final SimpleStringProperty category;
        private final SimpleStringProperty gross;
        private final SimpleStringProperty serviceFee;
        private final SimpleStringProperty companyPay;
        private final SimpleStringProperty companyNet;
        
        public BreakdownRow(String category, String gross, String serviceFee, String companyPay, String companyNet) {
            this.category = new SimpleStringProperty(category);
            this.gross = new SimpleStringProperty(gross);
            this.serviceFee = new SimpleStringProperty(serviceFee);
            this.companyPay = new SimpleStringProperty(companyPay);
            this.companyNet = new SimpleStringProperty(companyNet);
        }
        
        public SimpleStringProperty categoryProperty() { return category; }
        public SimpleStringProperty grossProperty() { return gross; }
        public SimpleStringProperty serviceFeeProperty() { return serviceFee; }
        public SimpleStringProperty companyPayProperty() { return companyPay; }
        public SimpleStringProperty companyNetProperty() { return companyNet; }
    }
    
    /**
     * Legacy constructor for compatibility
     */
    @Deprecated
    public CompanyFinancialsTab() {
        this(null, null, null);
    }
    
    /**
     * Create Visual Analytics Section with professional charts
     */
    private ScrollPane createVisualAnalyticsSection() {
        VBox analyticsContainer = new VBox(20);
        analyticsContainer.setPadding(new Insets(20));
        analyticsContainer.setStyle("-fx-background-color: #F5F5F5;");
        
        // Analytics Header
        Label analyticsTitle = new Label(companyName + " - Visual Analytics Dashboard");
        analyticsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        analyticsTitle.setTextFill(Color.web("#1976D2"));
        analyticsTitle.setId("analyticsTitle"); // For dynamic updates
        
        // Create chart grid
        GridPane chartGrid = new GridPane();
        chartGrid.setHgap(20);
        chartGrid.setVgap(20);
        chartGrid.setPadding(new Insets(20));
        
        // Revenue vs Expenses Line Chart
        LineChart<String, Number> revenueExpenseChart = createRevenueExpenseLineChart();
        VBox revenueExpenseBox = createChartBox(revenueExpenseChart, "Revenue vs Expenses Trend");
        chartGrid.add(revenueExpenseBox, 0, 0);
        
        // Expense Distribution Pie Chart
        PieChart expenseDistributionChart = createExpenseDistributionPieChart();
        VBox expenseDistBox = createChartBox(expenseDistributionChart, "Expense Distribution");
        chartGrid.add(expenseDistBox, 1, 0);
        
        // Monthly Performance Bar Chart
        BarChart<String, Number> monthlyPerformanceChart = createMonthlyPerformanceBarChart();
        VBox monthlyPerfBox = createChartBox(monthlyPerformanceChart, "Monthly Performance");
        chartGrid.add(monthlyPerfBox, 0, 1);
        
        // Profit Margin Area Chart
        AreaChart<String, Number> profitMarginChart = createProfitMarginAreaChart();
        VBox profitMarginBox = createChartBox(profitMarginChart, "Profit Margin Trend");
        chartGrid.add(profitMarginBox, 1, 1);
        
        // Key Metrics Cards
        HBox metricsBox = createKeyMetricsCards();
        
        // Financial Health Score
        VBox healthScoreBox = createFinancialHealthScore();
        
        // Add all components
        analyticsContainer.getChildren().addAll(
            analyticsTitle,
            new Separator(),
            metricsBox,
            healthScoreBox,
            chartGrid
        );
        
        ScrollPane scrollPane = new ScrollPane(analyticsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        return scrollPane;
    }
    
    private VBox createChartBox(Node chart, String title) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#333333"));
        
        if (chart instanceof Chart) {
            ((Chart) chart).setPrefSize(450, 300);
            ((Chart) chart).setAnimated(true);
        }
        
        box.getChildren().addAll(titleLabel, chart);
        return box;
    }
    
    private LineChart<String, Number> createRevenueExpenseLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Period");
        yAxis.setLabel("Amount ($)");
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.BOTTOM);
        
        // Data will be populated in updateVisualAnalytics()
        return chart;
    }
    
    private PieChart createExpenseDistributionPieChart() {
        PieChart chart = new PieChart();
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        chart.setLabelsVisible(true);
        chart.setStartAngle(90);
        
        // Data will be populated in updateVisualAnalytics()
        return chart;
    }
    
    private BarChart<String, Number> createMonthlyPerformanceBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Month");
        yAxis.setLabel("Amount ($)");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setBarGap(3);
        chart.setCategoryGap(20);
        
        // Data will be populated in updateVisualAnalytics()
        return chart;
    }
    
    private AreaChart<String, Number> createProfitMarginAreaChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Period");
        yAxis.setLabel("Profit Margin (%)");
        
        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);
        
        // Data will be populated in updateVisualAnalytics()
        return chart;
    }
    
    private HBox createKeyMetricsCards() {
        HBox metricsBox = new HBox(15);
        metricsBox.setAlignment(Pos.CENTER);
        metricsBox.setPadding(new Insets(20));
        
        // Revenue Growth Card
        VBox revenueGrowthCard = createMetricCard(
            "Revenue Growth", "+12.5%", "↑", Color.web("#4CAF50"), 
            "Compared to last period"
        );
        
        // Expense Ratio Card
        VBox expenseRatioCard = createMetricCard(
            "Expense Ratio", "68.2%", "→", Color.web("#FF9800"),
            "Of total revenue"
        );
        
        // Net Profit Margin Card
        VBox profitMarginCard = createMetricCard(
            "Net Profit Margin", "31.8%", "↑", Color.web("#2196F3"),
            "Above industry average"
        );
        
        // Cash Flow Card
        VBox cashFlowCard = createMetricCard(
            "Cash Flow", "$45,230", "↑", Color.web("#9C27B0"),
            "Positive trend"
        );
        
        metricsBox.getChildren().addAll(
            revenueGrowthCard, expenseRatioCard, profitMarginCard, cashFlowCard
        );
        
        return metricsBox;
    }
    
    private VBox createMetricCard(String title, String value, String indicator, 
                                  Color indicatorColor, String subtitle) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setPrefWidth(200);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 12));
        titleLabel.setTextFill(Color.web("#666666"));
        
        HBox valueBox = new HBox(5);
        valueBox.setAlignment(Pos.CENTER);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        valueLabel.setTextFill(Color.web("#333333"));
        
        Label indicatorLabel = new Label(indicator);
        indicatorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        indicatorLabel.setTextFill(indicatorColor);
        
        valueBox.getChildren().addAll(valueLabel, indicatorLabel);
        
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setFont(Font.font("Arial", 10));
        subtitleLabel.setTextFill(Color.web("#999999"));
        
        card.getChildren().addAll(titleLabel, valueBox, subtitleLabel);
        
        // Add hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle(card.getStyle() + " -fx-scale-x: 1.05; -fx-scale-y: 1.05;");
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle(card.getStyle().replace(" -fx-scale-x: 1.05; -fx-scale-y: 1.05;", ""));
        });
        
        return card;
    }
    
    private VBox createFinancialHealthScore() {
        VBox healthBox = new VBox(15);
        healthBox.setAlignment(Pos.CENTER);
        healthBox.setPadding(new Insets(20));
        healthBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label healthTitle = new Label(companyName + " - Financial Health Score");
        healthTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        healthTitle.setTextFill(Color.web("#333333"));
        healthTitle.setId("healthTitle"); // For dynamic updates
        
        // Score visualization (could be replaced with a gauge chart)
        StackPane scorePane = new StackPane();
        scorePane.setPrefSize(200, 200);
        
        Circle outerCircle = new Circle(80);
        outerCircle.setFill(Color.TRANSPARENT);
        outerCircle.setStroke(Color.web("#E0E0E0"));
        outerCircle.setStrokeWidth(20);
        
        Arc scoreArc = new Arc(0, 0, 80, 80, 90, -288); // 80% of 360 degrees
        scoreArc.setType(ArcType.OPEN);
        scoreArc.setFill(Color.TRANSPARENT);
        scoreArc.setStroke(Color.web("#4CAF50"));
        scoreArc.setStrokeWidth(20);
        scoreArc.setStrokeLineCap(StrokeLineCap.ROUND);
        
        Label scoreLabel = new Label("80");
        scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        scoreLabel.setTextFill(Color.web("#4CAF50"));
        
        Label scoreText = new Label("GOOD");
        scoreText.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        scoreText.setTextFill(Color.web("#4CAF50"));
        
        VBox scoreContent = new VBox(5);
        scoreContent.setAlignment(Pos.CENTER);
        scoreContent.getChildren().addAll(scoreLabel, scoreText);
        
        scorePane.getChildren().addAll(outerCircle, scoreArc, scoreContent);
        
        // Health indicators
        GridPane indicatorsGrid = new GridPane();
        indicatorsGrid.setHgap(20);
        indicatorsGrid.setVgap(10);
        indicatorsGrid.setAlignment(Pos.CENTER);
        
        addHealthIndicator(indicatorsGrid, 0, "Liquidity", "Excellent", Color.web("#4CAF50"));
        addHealthIndicator(indicatorsGrid, 1, "Profitability", "Good", Color.web("#4CAF50"));
        addHealthIndicator(indicatorsGrid, 2, "Efficiency", "Fair", Color.web("#FF9800"));
        addHealthIndicator(indicatorsGrid, 3, "Growth", "Strong", Color.web("#2196F3"));
        
        healthBox.getChildren().addAll(healthTitle, scorePane, new Separator(), indicatorsGrid);
        
        return healthBox;
    }
    
    private void addHealthIndicator(GridPane grid, int row, String metric, String status, Color color) {
        Label metricLabel = new Label(metric + ":");
        metricLabel.setFont(Font.font("Arial", 12));
        metricLabel.setTextFill(Color.web("#666666"));
        
        Label statusLabel = new Label(status);
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        statusLabel.setTextFill(color);
        
        grid.add(metricLabel, 0, row);
        grid.add(statusLabel, 1, row);
    }
}