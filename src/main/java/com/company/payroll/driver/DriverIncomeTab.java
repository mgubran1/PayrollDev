package com.company.payroll.driver;

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
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.payroll.PayrollCalculator;
import com.company.payroll.export.ExcelExporter;
import com.company.payroll.export.PDFExporter;

/**
 * Enhanced Driver Income Tab with real-time data integration
 */
public class DriverIncomeTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(DriverIncomeTab.class);
    
    // UI Components
    private TableView<DriverIncomeData> incomeTable;
    private ComboBox<Employee> driverComboBox;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> periodComboBox;
    private HBox dateBox;
    private Label totalIncomeLabel;
    private Label totalMilesLabel;
    private Label averagePerMileLabel;
    private Label totalLoadsLabel;
    private Label totalFuelLabel;
    private LineChart<String, Number> incomeChart;
    private PieChart expenseBreakdownChart;
    private ProgressIndicator loadingIndicator;
    private VBox contentBox;
    
    // Data
    private final ObservableList<DriverIncomeData> incomeData = FXCollections.observableArrayList();
    private final ObservableList<Employee> drivers = FXCollections.observableArrayList();
    
    // Services
    private final DriverIncomeService incomeService;
    private final EmployeeDAO employeeDAO;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Formatters
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    // Auto-refresh settings
    private static final int AUTO_REFRESH_INTERVAL = 30; // seconds
    private boolean autoRefreshEnabled = true;

    public DriverIncomeTab(EmployeeDAO employeeDAO, LoadDAO loadDAO, 
                          FuelTransactionDAO fuelDAO, PayrollCalculator payrollCalculator) {
        setText("Driver Income");
        setClosable(false);
        
        this.employeeDAO = employeeDAO;
        this.incomeService = new DriverIncomeService(employeeDAO, loadDAO, fuelDAO, payrollCalculator);
        
        initializeUI();
        setupEventHandlers();
        loadInitialData();
        startAutoRefresh();
    }
    
    private void initializeUI() {
        contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setStyle("-fx-background-color: #f0f0f0;");
        
        // Header
        VBox header = createHeader();
        
        // Control Panel
        HBox controlPanel = createControlPanel();
        
        // Summary Cards
        HBox summaryCards = createSummaryCards();
        
        // Main Content Area
        SplitPane mainContent = new SplitPane();
        mainContent.setDividerPositions(0.6);
        
        // Income Table
        VBox tableSection = createTableSection();
        
        // Charts Section
        VBox chartsSection = createChartsSection();
        
        mainContent.getItems().addAll(tableSection, chartsSection);
        
        // Loading Indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        
        contentBox.getChildren().addAll(header, controlPanel, summaryCards, mainContent, loadingIndicator);
        
        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); " +
                       "-fx-background-radius: 10;");
        
        Label titleLabel = new Label("Driver Income Analytics");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Real-time driver earnings and performance tracking");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
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
        
        // Driver Selection
        Label driverLabel = new Label("Driver:");
        driverLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        driverComboBox = new ComboBox<>(drivers);
        driverComboBox.setPrefWidth(200);
        driverComboBox.setPromptText("All Drivers");
        driverComboBox.setConverter(new javafx.util.StringConverter<Employee>() {
            @Override
            public String toString(Employee employee) {
                if (employee == null) return "All Drivers";
                return employee.getName() + 
                    (employee.getTruckUnit() != null ? " (" + employee.getTruckUnit() + ")" : "");
            }
            
            @Override
            public Employee fromString(String string) {
                return null;
            }
        });
        
        // Period Selection
        Label periodLabel = new Label("Period:");
        periodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        periodComboBox = new ComboBox<>();
        periodComboBox.getItems().addAll("This Week", "This Month", "This Year", "Custom Range");
        periodComboBox.setValue("This Month");
        periodComboBox.setPrefWidth(120);

        // Date Range Selection
        Label dateRangeLabel = new Label("Date Range:");
        dateRangeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        startDatePicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        endDatePicker = new DatePicker(LocalDate.now());
        dateBox = new HBox(5, dateRangeLabel, startDatePicker, new Label("to"), endDatePicker);
        dateBox.setVisible(false);
        updateDateRange();
        
        // Buttons
        Button searchButton = createStyledButton("Search", "#3498db", true);
        Button refreshButton = createStyledButton("Refresh", "#27ae60", false);
        Button exportExcelButton = createStyledButton("Export Excel", "#27ae60", false);
        Button exportPdfButton = createStyledButton("Export PDF", "#e74c3c", false);
        
        // Set button actions
        searchButton.setOnAction(e -> loadDriverIncome());
        refreshButton.setOnAction(e -> loadDriverIncome());
        exportExcelButton.setOnAction(e -> exportToExcel());
        exportPdfButton.setOnAction(e -> exportToPDF());
        
        // Auto-refresh toggle
        CheckBox autoRefreshCheck = new CheckBox("Auto Refresh");
        autoRefreshCheck.setSelected(autoRefreshEnabled);
        autoRefreshCheck.setOnAction(e -> {
            autoRefreshEnabled = autoRefreshCheck.isSelected();
            if (autoRefreshEnabled) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
        
        controlPanel.getChildren().addAll(
            driverLabel, driverComboBox,
            new Separator(Orientation.VERTICAL),
            periodLabel, periodComboBox,
            dateBox,
            searchButton, refreshButton,
            new Separator(Orientation.VERTICAL),
            exportExcelButton, exportPdfButton,
            new Separator(Orientation.VERTICAL),
            autoRefreshCheck
        );
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        controlPanel.setEffect(shadow);
        
        return controlPanel;
    }
    
    private HBox createSummaryCards() {
        HBox summaryCards = new HBox(20);
        summaryCards.setPadding(new Insets(10));
        summaryCards.setAlignment(Pos.CENTER);
        
        VBox incomeCard = createSummaryCard("Total Income", "$0.00", "#3498db", totalIncomeLabel = new Label());
        VBox milesCard = createSummaryCard("Total Miles", "0", "#e74c3c", totalMilesLabel = new Label());
        VBox avgCard = createSummaryCard("Avg Per Mile", "$0.00", "#f39c12", averagePerMileLabel = new Label());
        VBox loadsCard = createSummaryCard("Total Loads", "0", "#27ae60", totalLoadsLabel = new Label());
        VBox fuelCard = createSummaryCard("Total Fuel", "$0.00", "#9b59b6", totalFuelLabel = new Label());
        
        summaryCards.getChildren().addAll(incomeCard, milesCard, avgCard, loadsCard, fuelCard);
        
        return summaryCards;
    }
    
    private VBox createSummaryCard(String title, String initialValue, String color, Label valueLabel) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setPrefWidth(180);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.web(color));
        
        valueLabel.setText(initialValue);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        valueLabel.setTextFill(Color.web("#2c3e50"));
        
        card.getChildren().addAll(titleLabel, valueLabel);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
        card.setOnMouseEntered(e -> animateCard(card, 1.05));
        card.setOnMouseExited(e -> animateCard(card, 1.0));
        
        return card;
    }
    
    private VBox createTableSection() {
        VBox tableSection = new VBox(10);
        tableSection.setPadding(new Insets(15));
        tableSection.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        Label tableTitle = new Label("Driver Income Details");
        tableTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        incomeTable = createIncomeTable();
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        tableSection.setEffect(shadow);
        
        tableSection.getChildren().addAll(tableTitle, incomeTable);
        VBox.setVgrow(incomeTable, Priority.ALWAYS);
        
        return tableSection;
    }
    
    private TableView<DriverIncomeData> createIncomeTable() {
        TableView<DriverIncomeData> table = new TableView<>(incomeData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);
        
        // Driver column
        TableColumn<DriverIncomeData, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDriverName()));
        driverCol.setPrefWidth(150);
        
        // Truck/Unit column
        TableColumn<DriverIncomeData, String> truckCol = new TableColumn<>("Truck/Unit");
        truckCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTruckUnit()));
        truckCol.setPrefWidth(100);
        
        // Loads column
        TableColumn<DriverIncomeData, Integer> loadsCol = new TableColumn<>("Loads");
        loadsCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getTotalLoads()).asObject());
        loadsCol.setPrefWidth(80);
        loadsCol.setStyle("-fx-alignment: CENTER;");
        
        // Total Gross column
        TableColumn<DriverIncomeData, String> grossCol = new TableColumn<>("Total Gross");
        grossCol.setCellValueFactory(data -> 
            new SimpleStringProperty(CURRENCY_FORMAT.format(data.getValue().getTotalGross())));
        grossCol.setPrefWidth(120);
        grossCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // Total Miles column
        TableColumn<DriverIncomeData, String> milesCol = new TableColumn<>("Total Miles");
        milesCol.setCellValueFactory(data -> 
            new SimpleStringProperty(String.format("%.1f", data.getValue().getTotalMiles())));
        milesCol.setPrefWidth(100);
        milesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // Fuel column
        TableColumn<DriverIncomeData, String> fuelCol = new TableColumn<>("Fuel Cost");
        fuelCol.setCellValueFactory(data -> 
            new SimpleStringProperty(CURRENCY_FORMAT.format(data.getValue().getTotalFuelAmount())));
        fuelCol.setPrefWidth(100);
        fuelCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // Avg Per Mile column
        TableColumn<DriverIncomeData, String> avgPerMileCol = new TableColumn<>("Avg/Mile");
        avgPerMileCol.setCellValueFactory(data -> 
            new SimpleStringProperty(String.format("$%.3f", data.getValue().getAveragePerMile())));
        avgPerMileCol.setPrefWidth(100);
        avgPerMileCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // Net Pay column
        TableColumn<DriverIncomeData, String> netPayCol = new TableColumn<>("Net Pay");
        netPayCol.setCellValueFactory(data -> 
            new SimpleStringProperty(CURRENCY_FORMAT.format(data.getValue().getNetPay())));
        netPayCol.setPrefWidth(120);
        netPayCol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        netPayCol.setCellFactory(column -> new TableCell<DriverIncomeData, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    DriverIncomeData data = getTableRow().getItem();
                    if (data != null && data.getNetPay() < 0) {
                        setTextFill(Color.RED);
                    } else {
                        setTextFill(Color.web("#27ae60"));
                    }
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
                }
            }
        });
        
        table.getColumns().addAll(driverCol, truckCol, loadsCol, grossCol, 
                                 milesCol, fuelCol, avgPerMileCol, netPayCol);
        
        // Add context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewDetailsItem = new MenuItem("View Details");
        viewDetailsItem.setOnAction(e -> viewDriverDetails(table.getSelectionModel().getSelectedItem()));
        
        MenuItem exportDriverItem = new MenuItem("Export Driver Report");
        exportDriverItem.setOnAction(e -> exportDriverReport(table.getSelectionModel().getSelectedItem()));
        
        contextMenu.getItems().addAll(viewDetailsItem, exportDriverItem);
        table.setContextMenu(contextMenu);
        
        // Double-click to view details
        table.setRowFactory(tv -> {
            TableRow<DriverIncomeData> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewDriverDetails(row.getItem());
                }
            });
            return row;
        });
        
        return table;
    }
    
    private VBox createChartsSection() {
        VBox chartsSection = new VBox(15);
        chartsSection.setPadding(new Insets(15));
        
        // Income Trend Chart
        VBox trendChartBox = createChartBox("Income Trend", createIncomeChart());
        
        // Expense Breakdown Chart
        VBox pieChartBox = createChartBox("Expense Breakdown", createExpenseChart());
        
        chartsSection.getChildren().addAll(trendChartBox, pieChartBox);
        
        return chartsSection;
    }
    
    private VBox createChartBox(String title, Node chart) {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        box.setPadding(new Insets(15));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        box.getChildren().addAll(titleLabel, chart);
        
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        box.setEffect(shadow);
        
        return box;
    }
    
    private LineChart<String, Number> createIncomeChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Period");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Income ($)");
        
        incomeChart = new LineChart<>(xAxis, yAxis);
        incomeChart.setTitle("Income Over Time");
        incomeChart.setPrefHeight(250);
        incomeChart.setCreateSymbols(true);
        incomeChart.setAnimated(true);
        
        return incomeChart;
    }
    
    private PieChart createExpenseChart() {
        expenseBreakdownChart = new PieChart();
        expenseBreakdownChart.setTitle("Expense Categories");
        expenseBreakdownChart.setPrefHeight(250);
        expenseBreakdownChart.setAnimated(true);
        expenseBreakdownChart.setLabelsVisible(true);

        return expenseBreakdownChart;
    }

    private void updateDateRange() {
        String period = periodComboBox.getValue();
        LocalDate now = LocalDate.now();

        switch (period) {
            case "This Week":
                startDatePicker.setValue(now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                endDatePicker.setValue(now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
                break;
            case "This Month":
                startDatePicker.setValue(now.withDayOfMonth(1));
                endDatePicker.setValue(now);
                break;
            case "This Year":
                startDatePicker.setValue(now.withDayOfYear(1));
                endDatePicker.setValue(now);
                break;
        }
    }
    
    private void setupEventHandlers() {
        // Date range changes
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (autoRefreshEnabled && newVal != null) {
                loadDriverIncome();
            }
        });
        
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (autoRefreshEnabled && newVal != null) {
                loadDriverIncome();
            }
        });

        periodComboBox.setOnAction(e -> {
            dateBox.setVisible("Custom Range".equals(periodComboBox.getValue()));
            updateDateRange();
            if (autoRefreshEnabled) {
                loadDriverIncome();
            }
        });

        // Driver selection change
        driverComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (autoRefreshEnabled) {
                loadDriverIncome();
            }
        });
    }
    
    private void loadInitialData() {
        // Load active drivers
        List<Employee> activeDrivers = employeeDAO.getActive().stream()
            .filter(Employee::isDriver)
            .collect(Collectors.toList());
        
        drivers.clear();
        drivers.add(null); // All drivers option
        drivers.addAll(activeDrivers);
        
        // Load initial income data
        loadDriverIncome();
    }
    
    private void loadDriverIncome() {
        Employee selectedDriver = driverComboBox.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        
        if (startDate == null || endDate == null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Date Range", 
                     "Please select both start and end dates.");
            return;
        }
        
        loadingIndicator.setVisible(true);
        
        Task<List<DriverIncomeData>> task = new Task<List<DriverIncomeData>>() {
            @Override
            protected List<DriverIncomeData> call() throws Exception {
                if (selectedDriver != null) {
                    // Load single driver
                    DriverIncomeData data = incomeService.getDriverIncomeData(
                        selectedDriver, startDate, endDate).get();
                    return Collections.singletonList(data);
                } else {
                    // Load all drivers
                    return incomeService.getAllDriversIncomeData(startDate, endDate).get();
                }
            }
        };
        
        task.setOnSucceeded(e -> {
            List<DriverIncomeData> data = task.getValue();
            updateTable(data);
            updateSummaryCards(data);
            updateCharts(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            logger.error("Failed to load driver income data", task.getException());
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to load driver income data: " + task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
    
    private void updateTable(List<DriverIncomeData> data) {
        incomeData.clear();
        incomeData.addAll(data);
        
        // Sort by net pay descending
        incomeData.sort((a, b) -> Double.compare(b.getNetPay(), a.getNetPay()));
        
        // Animate table update
        FadeTransition ft = new FadeTransition(Duration.millis(300), incomeTable);
        ft.setFromValue(0.3);
        ft.setToValue(1.0);
        ft.play();
    }
    
    private void updateSummaryCards(List<DriverIncomeData> data) {
        double totalIncome = data.stream().mapToDouble(DriverIncomeData::getNetPay).sum();
        double totalMiles = data.stream().mapToDouble(DriverIncomeData::getTotalMiles).sum();
        int totalLoads = data.stream().mapToInt(DriverIncomeData::getTotalLoads).sum();
        double totalFuel = data.stream().mapToDouble(DriverIncomeData::getTotalFuelAmount).sum();
        double avgPerMile = totalMiles > 0 ? totalIncome / totalMiles : 0;
        
        animateLabel(totalIncomeLabel, CURRENCY_FORMAT.format(totalIncome));
        animateLabel(totalMilesLabel, String.format("%.1f", totalMiles));
        animateLabel(averagePerMileLabel, String.format("$%.3f", avgPerMile));
        animateLabel(totalLoadsLabel, String.valueOf(totalLoads));
        animateLabel(totalFuelLabel, CURRENCY_FORMAT.format(totalFuel));
    }
    
    private void updateCharts(List<DriverIncomeData> data) {
        // Update line chart
        updateIncomeChart(data);
        
        // Update pie chart
        updateExpenseChart(data);
    }
    
    private void updateIncomeChart(List<DriverIncomeData> data) {
        incomeChart.getData().clear();
        
        if (data.isEmpty()) return;
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Net Income");
        
        // Group by driver for chart
        data.forEach(d -> {
            series.getData().add(new XYChart.Data<>(d.getDriverName(), d.getNetPay()));
        });
        
        incomeChart.getData().add(series);
    }
    
    private void updateExpenseChart(List<DriverIncomeData> data) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        
        double totalFuel = data.stream().mapToDouble(DriverIncomeData::getTotalFuelAmount).sum();
        double totalService = data.stream().mapToDouble(DriverIncomeData::getServiceFee).sum();
        double totalRecurring = data.stream().mapToDouble(DriverIncomeData::getRecurringFees).sum();
        double totalAdvances = data.stream().mapToDouble(DriverIncomeData::getAdvanceRepayments).sum();
        double totalEscrow = data.stream().mapToDouble(DriverIncomeData::getEscrowDeposits).sum();
        double totalOther = data.stream().mapToDouble(DriverIncomeData::getOtherDeductions).sum();
        
        if (totalFuel > 0) pieChartData.add(new PieChart.Data("Fuel", totalFuel));
        if (totalService > 0) pieChartData.add(new PieChart.Data("Service Fees", totalService));
        if (totalRecurring > 0) pieChartData.add(new PieChart.Data("Recurring Fees", totalRecurring));
        if (totalAdvances > 0) pieChartData.add(new PieChart.Data("Advance Repayments", totalAdvances));
        if (totalEscrow > 0) pieChartData.add(new PieChart.Data("Escrow", totalEscrow));
        if (totalOther > 0) pieChartData.add(new PieChart.Data("Other", totalOther));
        
        expenseBreakdownChart.setData(pieChartData);
    }
    
    private void startAutoRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            if (autoRefreshEnabled) {
                Platform.runLater(this::loadDriverIncome);
            }
        }, AUTO_REFRESH_INTERVAL, AUTO_REFRESH_INTERVAL, TimeUnit.SECONDS);
    }
    
    private void stopAutoRefresh() {
        scheduler.shutdown();
    }
    
    private void viewDriverDetails(DriverIncomeData data) {
        if (data == null) return;
        
        DriverDetailsDialog dialog = new DriverDetailsDialog(data);
        dialog.showAndWait();
    }
    
    private void exportDriverReport(DriverIncomeData data) {
        if (data == null) return;
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Driver Report");
        fileChooser.setInitialFileName(String.format("DriverReport_%s_%s.pdf",
            data.getDriverName().replace(" ", "_"),
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            exportToPDF(Collections.singletonList(data), file);
        }
    }
    
    private void exportToExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to Excel");
        fileChooser.setInitialFileName("DriverIncome_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                ExcelExporter exporter = new ExcelExporter();
                exporter.exportDriverIncome(new ArrayList<>(incomeData), file);
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                         "Driver income data exported to Excel successfully!");
            } catch (Exception e) {
                logger.error("Failed to export to Excel", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                         "Failed to export to Excel: " + e.getMessage());
            }
        }
    }
    
    private void exportToPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.setInitialFileName("DriverIncome_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            exportToPDF(new ArrayList<>(incomeData), file);
        }
    }
    
    private void exportToPDF(List<DriverIncomeData> data, File file) {
        try {
            PDFExporter exporter = new PDFExporter();
            exporter.exportDriverIncome(data, file);
            showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                     "Driver income report exported to PDF successfully!");
        } catch (Exception e) {
            logger.error("Failed to export to PDF", e);
            showAlert(Alert.AlertType.ERROR, "Export Failed", 
                     "Failed to export to PDF: " + e.getMessage());
        }
    }
    
    private Button createStyledButton(String text, String color, boolean primary) {
        Button button = new Button(text);
        if (primary) {
            button.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5;", color));
        } else {
            button.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                "-fx-cursor: hand; -fx-background-radius: 5;", color));
        }
        
        button.setOnMouseEntered(e -> button.setOpacity(0.8));
        button.setOnMouseExited(e -> button.setOpacity(1.0));
        
        return button;
    }
    
    private void animateLabel(Label label, String newValue) {
        FadeTransition ft = new FadeTransition(Duration.millis(200), label);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            label.setText(newValue);
            FadeTransition ft2 = new FadeTransition(Duration.millis(200), label);
            ft2.setFromValue(0.0);
            ft2.setToValue(1.0);
            ft2.play();
        });
        ft.play();
    }
    
    private void animateCard(VBox card, double scale) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
        st.setToX(scale);
        st.setToY(scale);
        st.play();
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Clean up resources when tab is closed
     */
    public void cleanup() {
        stopAutoRefresh();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}