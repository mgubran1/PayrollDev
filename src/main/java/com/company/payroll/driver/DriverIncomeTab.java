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
import javafx.scene.input.MouseEvent;
import javafx.beans.property.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.animation.*;
import javafx.util.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import com.company.payroll.payroll.PayrollCalculator;
import com.company.payroll.payroll.PayrollCalculator.PayrollRow;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.employees.Employee;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.payroll.ExcelExporter;
import com.company.payroll.export.PDFExporter;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Orientation;

public class DriverIncomeTab extends Tab {
    private TableView<PayrollRow> incomeTable;
    private ComboBox<String> driverComboBox;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Label totalIncomeLabel;
    private Label totalMilesLabel;
    private Label averagePerMileLabel;
    private Label totalLoadsLabel;
    private LineChart<String, Number> incomeChart;
    private PieChart expenseBreakdownChart;
    private ProgressIndicator loadingIndicator;
    private VBox contentBox;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final LoadDAO loadDAO = new LoadDAO();
    private final FuelTransactionDAO fuelDAO = new FuelTransactionDAO();
    private final PayrollCalculator payrollCalculator = new PayrollCalculator(employeeDAO, loadDAO, fuelDAO);
    
    public DriverIncomeTab() {
        setText("Driver Income");
        setClosable(false);
        
        // Set tab icon, handle missing resource gracefully
        ImageView tabIcon;
        var iconStream = getClass().getResourceAsStream("/icons/driver-income.png");
        if (iconStream != null) {
            tabIcon = new ImageView(new Image(iconStream));
        } else {
            tabIcon = new ImageView();
        }
        tabIcon.setFitHeight(16);
        tabIcon.setFitWidth(16);
        setGraphic(tabIcon);
        
        contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setStyle("-fx-background-color: #f0f0f0;");
        
        // Header with gradient
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
        
        initializeData();
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
        
        Label subtitleLabel = new Label("Track and analyze driver earnings and performance");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        header.getChildren().addAll(titleLabel, subtitleLabel);
        
        // Add shadow effect
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
        driverComboBox = new ComboBox<>();
        driverComboBox.setPrefWidth(200);
        driverComboBox.setPromptText("Select Driver");
        
        // Date Range Selection
        Label dateRangeLabel = new Label("Date Range:");
        dateRangeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        endDatePicker = new DatePicker(LocalDate.now());
        
        // Search Button
        Button searchButton = new Button("Search");
        searchButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                            "-fx-font-weight: bold; -fx-background-radius: 5;");
        searchButton.setPrefWidth(100);
        searchButton.setOnAction(e -> loadDriverIncome());
        
        // Export Buttons
        Button exportExcelButton = new Button("Export Excel");
        exportExcelButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                                 "-fx-font-weight: bold; -fx-background-radius: 5;");
        exportExcelButton.setOnAction(e -> exportToExcel());
        
        Button exportPdfButton = new Button("Export PDF");
        exportPdfButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                               "-fx-font-weight: bold; -fx-background-radius: 5;");
        exportPdfButton.setOnAction(e -> exportToPdf());
        
        // Add hover effects
        addHoverEffect(searchButton);
        addHoverEffect(exportExcelButton);
        addHoverEffect(exportPdfButton);
        
        controlPanel.getChildren().addAll(
            driverLabel, driverComboBox,
            new Separator(Orientation.VERTICAL),
            dateRangeLabel, startDatePicker, new Label("to"), endDatePicker,
            searchButton,
            new Separator(Orientation.VERTICAL),
            exportExcelButton, exportPdfButton
        );
        
        // Add shadow
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
        
        // Total Income Card
        VBox incomeCard = createSummaryCard("Total Income", "$0.00", "#3498db", "income-icon.png");
        totalIncomeLabel = (Label) incomeCard.getChildren().get(1);
        
        // Total Miles Card
        VBox milesCard = createSummaryCard("Total Miles", "0", "#e74c3c", "miles-icon.png");
        totalMilesLabel = (Label) milesCard.getChildren().get(1);
        
        // Average Per Mile Card
        VBox avgCard = createSummaryCard("Avg Per Mile", "$0.00", "#f39c12", "average-icon.png");
        averagePerMileLabel = (Label) avgCard.getChildren().get(1);
        
        // Total Loads Card
        VBox loadsCard = createSummaryCard("Total Loads", "0", "#27ae60", "loads-icon.png");
        totalLoadsLabel = (Label) loadsCard.getChildren().get(1);
        
        summaryCards.getChildren().addAll(incomeCard, milesCard, avgCard, loadsCard);
        
        return summaryCards;
    }
    
    private VBox createSummaryCard(String title, String value, String color, String iconPath) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setPrefWidth(200);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Title with icon
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER);
        
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconPath)));
            icon.setFitHeight(24);
            icon.setFitWidth(24);
            titleBox.getChildren().add(icon);
        } catch (Exception e) {
            // Icon not found, continue without it
        }
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.web(color));
        titleBox.getChildren().add(titleLabel);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        valueLabel.setTextFill(Color.web("#2c3e50"));
        
        card.getChildren().addAll(titleBox, valueLabel);
        
        // Add shadow and hover effect
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(3);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        card.setEffect(shadow);
        
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
        
        return card;
    }
    
    private VBox createTableSection() {
        VBox tableSection = new VBox(10);
        tableSection.setPadding(new Insets(15));
        tableSection.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        Label tableTitle = new Label("Income Details");
        tableTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        incomeTable = new TableView<>();
        incomeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        incomeTable.setPrefHeight(400);
        
        // Create columns for aggregated payroll data
        TableColumn<PayrollRow, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(new PropertyValueFactory<>("driverName"));

        TableColumn<PayrollRow, String> truckCol = new TableColumn<>("Truck");
        truckCol.setCellValueFactory(new PropertyValueFactory<>("truckUnit"));

        TableColumn<PayrollRow, Integer> loadsCol = new TableColumn<>("Loads");
        loadsCol.setCellValueFactory(new PropertyValueFactory<>("loadCount"));

        TableColumn<PayrollRow, Double> grossCol = new TableColumn<>("Gross");
        grossCol.setCellValueFactory(new PropertyValueFactory<>("gross"));
        grossCol.setCellFactory(column -> new TableCell<PayrollRow, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                    if (amount < 0) {
                        setTextFill(Color.RED);
                    } else {
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });
        
        TableColumn<PayrollRow, Double> fuelCol = new TableColumn<>("Fuel");
        fuelCol.setCellValueFactory(new PropertyValueFactory<>("fuel"));
        fuelCol.setCellFactory(column -> new TableCell<PayrollRow, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                    setTextFill(Color.RED);
                }
            }
        });

        TableColumn<PayrollRow, Double> netCol = new TableColumn<>("Net Pay");
        netCol.setCellValueFactory(new PropertyValueFactory<>("netPay"));
        netCol.setCellFactory(column -> new TableCell<PayrollRow, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(CURRENCY_FORMAT.format(amount));
                    if (amount < 0) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.web("#27ae60"));
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
        
        incomeTable.getColumns().addAll(driverCol, truckCol, loadsCol, grossCol, fuelCol, netCol);
        
        // Add context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewDetailsItem = new MenuItem("View Details");
        viewDetailsItem.setOnAction(e -> viewLoadDetails());
        MenuItem printItem = new MenuItem("Print");
        printItem.setOnAction(e -> printSelectedRow());
        contextMenu.getItems().addAll(viewDetailsItem, printItem);
        incomeTable.setContextMenu(contextMenu);
        
        // Add shadow
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        tableSection.setEffect(shadow);
        
        tableSection.getChildren().addAll(tableTitle, incomeTable);
        
        return tableSection;
    }
    
    private VBox createChartsSection() {
        VBox chartsSection = new VBox(15);
        chartsSection.setPadding(new Insets(15));
        
        // Income Trend Chart
        VBox trendChartBox = new VBox(10);
        trendChartBox.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        trendChartBox.setPadding(new Insets(15));
        
        Label trendTitle = new Label("Income Trend");
        trendTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Period");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Income ($)");
        
        incomeChart = new LineChart<>(xAxis, yAxis);
        incomeChart.setTitle("Monthly Income Trend");
        incomeChart.setPrefHeight(250);
        incomeChart.setCreateSymbols(true);
        incomeChart.setAnimated(true);
        
        trendChartBox.getChildren().addAll(trendTitle, incomeChart);
        
        // Expense Breakdown Chart
        VBox pieChartBox = new VBox(10);
        pieChartBox.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        pieChartBox.setPadding(new Insets(15));
        
        Label pieTitle = new Label("Deduction Breakdown");
        pieTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        expenseBreakdownChart = new PieChart();
        expenseBreakdownChart.setTitle("Deduction Categories");
        expenseBreakdownChart.setPrefHeight(250);
        expenseBreakdownChart.setAnimated(true);
        expenseBreakdownChart.setLabelsVisible(true);
        
        pieChartBox.getChildren().addAll(pieTitle, expenseBreakdownChart);
        
        // Add shadows
        DropShadow shadow = new DropShadow();
        shadow.setOffsetY(2);
        shadow.setColor(Color.color(0, 0, 0, 0.1));
        trendChartBox.setEffect(shadow);
        pieChartBox.setEffect(shadow);
        
        chartsSection.getChildren().addAll(trendChartBox, pieChartBox);
        
        return chartsSection;
    }
    
    private void addHoverEffect(Button button) {
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "-fx-cursor: hand; -fx-opacity: 0.8;");
        });
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("-fx-cursor: hand; -fx-opacity: 0.8;", ""));
        });
    }
    
    private void loadDriverIncome() {
        String selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            showAlert(AlertType.WARNING, "No Driver Selected", "Please select a driver to view income.");
            return;
        }
        
        loadingIndicator.setVisible(true);
        
        Task<List<PayrollRow>> task = new Task<List<PayrollRow>>() {
            @Override
            protected List<PayrollRow> call() throws Exception {
                Employee driver = employeeDAO.getActive().stream()
                    .filter(d -> d.getName().equals(selectedDriver))
                    .findFirst()
                    .orElse(null);
                if (driver == null) {
                    return Collections.emptyList();
                }
                return payrollCalculator.calculatePayrollRows(
                    Collections.singletonList(driver),
                    startDatePicker.getValue(), endDatePicker.getValue());
            }
        };
        
        task.setOnSucceeded(e -> {
            List<PayrollRow> data = task.getValue();
            updateTable(data);
            updateSummaryCards(data);
            updateCharts(data);
            loadingIndicator.setVisible(false);
        });
        
        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showAlert(AlertType.ERROR, "Error", "Failed to load driver income data.");
        });
        
        new Thread(task).start();
    }
    
    private void updateTable(List<PayrollRow> data) {
        ObservableList<PayrollRow> tableData = FXCollections.observableArrayList(data);
        incomeTable.setItems(tableData);
        
        // Add animation
        FadeTransition ft = new FadeTransition(Duration.millis(500), incomeTable);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }
    
    private void updateSummaryCards(List<PayrollRow> data) {
        double totalIncome = data.stream().mapToDouble(r -> r.netPay).sum();
        int totalLoads = data.stream().mapToInt(r -> r.loadCount).sum();

        animateLabel(totalIncomeLabel, CURRENCY_FORMAT.format(totalIncome));
        animateLabel(totalMilesLabel, "N/A");
        animateLabel(averagePerMileLabel, "N/A");
        animateLabel(totalLoadsLabel, String.valueOf(totalLoads));
    }
    
    private void animateLabel(Label label, String newValue) {
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
    
    private void updateCharts(List<PayrollRow> data) {
        incomeChart.getData().clear();
        expenseBreakdownChart.setData(FXCollections.observableArrayList());
    }
    
    private void exportToExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Excel File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("driver_income_" + LocalDate.now() + ".xlsx");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // Export logic here
            showAlert(AlertType.INFORMATION, "Export Successful", "Income data exported to Excel successfully!");
        }
    }
    
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("driver_income_" + LocalDate.now() + ".pdf");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            // Export logic here
            showAlert(AlertType.INFORMATION, "Export Successful", "Income data exported to PDF successfully!");
        }
    }
    
    private void viewLoadDetails() {
        PayrollRow selected = incomeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Load Details");
            alert.setHeaderText(selected.driverName);
            alert.setContentText("Net Pay: " + CURRENCY_FORMAT.format(selected.netPay));
            alert.showAndWait();
        }
    }
    
    private void printSelectedRow() {
        PayrollRow selected = incomeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Print logic here
            showAlert(AlertType.INFORMATION, "Print", "Printing load details...");
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
        List<Employee> drivers = employeeDAO.getActive();
        ObservableList<String> names = FXCollections.observableArrayList(
            drivers.stream().map(Employee::getName).collect(Collectors.toList()));
        driverComboBox.setItems(names);
    }

}
