package com.company.payroll.dispatcher;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadStatus;
import com.company.payroll.drivers.Driver;

import java.io.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced dialog for generating comprehensive dispatcher reports with
 * multiple formats, analytics, and export capabilities
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherReportsDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherReportsDialog.class);
    
    // Constants
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();
    
    // Core components
    private final DispatcherController controller;
    private final SimpleBooleanProperty generating = new SimpleBooleanProperty(false);
    
    // UI Components
    private TabPane tabPane;
    private ComboBox<ReportType> reportTypeCombo;
    private ComboBox<DispatcherDriverStatus> driverCombo;
    private CheckBox allDriversCheck;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> periodCombo;
    private ComboBox<ExportFormat> formatCombo;
    
    // Report display components
    private TextArea reportTextArea;
    private WebView reportWebView;
    private TableView<ReportRow> reportTable;
    private VBox chartsContainer;
    private ProgressBar progressBar;
    private Label statusLabel;
    
    // Report data
    private String currentReportHTML;
    private List<ReportRow> currentReportData;
    private Map<String, Object> reportMetadata;
    
    // Report types
    public enum ReportType {
        DRIVER_SUMMARY("Driver Summary", "Summary of driver activity and performance"),
        LOAD_DETAILS("Load Details", "Detailed list of loads with all information"),
        PERFORMANCE_METRICS("Performance Metrics", "Driver performance analytics"),
        FINANCIAL_SUMMARY("Financial Summary", "Revenue and cost analysis"),
        UTILIZATION_REPORT("Utilization Report", "Fleet utilization statistics"),
        COMPLIANCE_REPORT("Compliance Report", "Hours of service and compliance tracking"),
        MAINTENANCE_REPORT("Maintenance Report", "Vehicle maintenance and issues"),
        CUSTOM_REPORT("Custom Report", "Build your own custom report");
        
        private final String displayName;
        private final String description;
        
        ReportType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        @Override
        public String toString() { return displayName; }
    }
    
    // Export formats
    public enum ExportFormat {
        PDF("PDF", ".pdf"),
        EXCEL("Excel", ".xlsx"),
        CSV("CSV", ".csv"),
        HTML("HTML", ".html"),
        JSON("JSON", ".json");
        
        private final String displayName;
        private final String extension;
        
        ExportFormat(String displayName, String extension) {
            this.displayName = displayName;
            this.extension = extension;
        }
        
        public String getDisplayName() { return displayName; }
        public String getExtension() { return extension; }
        
        @Override
        public String toString() { return displayName; }
    }
    
    public DispatcherReportsDialog(DispatcherController controller, Window owner) {
        this.controller = controller;
        this.reportMetadata = new HashMap<>();
        
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Dispatcher Reports");
        setResizable(true);
        
        // Apply styling
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/dispatcher-reports.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("reports-dialog");
        
        initializeUI();
        setupBindings();
        setupKeyboardShortcuts();
        loadPreferences();
    }
    
    private void initializeUI() {
        // Create main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPrefSize(1200, 800);
        
        // Header
        VBox header = createHeader();
        mainLayout.setTop(header);
        
        // Center - Report display
        tabPane = createReportTabs();
        mainLayout.setCenter(tabPane);
        
        // Bottom - Status bar
        HBox statusBar = createStatusBar();
        mainLayout.setBottom(statusBar);
        
        getDialogPane().setContent(mainLayout);
        
        // Dialog buttons
        ButtonType generateButton = new ButtonType("Generate", ButtonBar.ButtonData.OK_DONE);
        ButtonType exportButton = new ButtonType("Export", ButtonBar.ButtonData.APPLY);
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        getDialogPane().getButtonTypes().addAll(generateButton, exportButton, closeButton);
        
        // Button actions
        Button genBtn = (Button) getDialogPane().lookupButton(generateButton);
        genBtn.setDefaultButton(true);
        genBtn.setOnAction(e -> generateReport());
        genBtn.disableProperty().bind(generating);
        
        Button expBtn = (Button) getDialogPane().lookupButton(exportButton);
        expBtn.setOnAction(e -> exportReport());
        expBtn.disableProperty().bind(
            generating.or(Bindings.isEmpty(reportTextArea.textProperty()))
        );
    }
    
    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.getStyleClass().add("report-header");
        
        // Title section
        HBox titleBox = new HBox(20);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Dispatcher Reports");
        titleLabel.getStyleClass().add("report-title");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Quick actions
        Button refreshBtn = createIconButton("🔄", "Refresh data", e -> refreshData());
        Button printBtn = createIconButton("🖨", "Print report", e -> printReport());
        Button helpBtn = createIconButton("❓", "Help", e -> showHelp());
        
        HBox quickActions = new HBox(10, refreshBtn, printBtn, helpBtn);
        
        titleBox.getChildren().addAll(titleLabel, spacer, quickActions);
        
        // Report configuration
        GridPane configGrid = createConfigurationGrid();
        
        header.getChildren().addAll(titleBox, new Separator(), configGrid);
        
        return header;
    }
    
    private GridPane createConfigurationGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.getStyleClass().add("config-grid");
        
        int row = 0;
        
        // Report Type
        Label typeLabel = new Label("Report Type:");
        typeLabel.getStyleClass().add("config-label");
        
        reportTypeCombo = new ComboBox<>();
        reportTypeCombo.getItems().addAll(ReportType.values());
        reportTypeCombo.setValue(ReportType.DRIVER_SUMMARY);
        reportTypeCombo.setPrefWidth(250);
        reportTypeCombo.setConverter(new StringConverter<ReportType>() {
            @Override
            public String toString(ReportType type) {
                return type != null ? type.getDisplayName() : "";
            }
            
            @Override
            public ReportType fromString(String string) {
                return null;
            }
        });
        
        Label typeDescLabel = new Label();
        typeDescLabel.getStyleClass().add("description-label");
        typeDescLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            ReportType selected = reportTypeCombo.getValue();
            return selected != null ? selected.getDescription() : "";
        }, reportTypeCombo.valueProperty()));
        
        grid.add(typeLabel, 0, row);
        grid.add(reportTypeCombo, 1, row);
        grid.add(typeDescLabel, 2, row);
        row++;
        
        // Driver Selection
        Label driverLabel = new Label("Driver:");
        driverLabel.getStyleClass().add("config-label");
        
        driverCombo = new ComboBox<>(controller.getDriverStatuses());
        driverCombo.setPrefWidth(250);
        driverCombo.setConverter(new StringConverter<DispatcherDriverStatus>() {
            @Override
            public String toString(DispatcherDriverStatus status) {
                return status != null ? status.getDriverName() : "";
            }
            
            @Override
            public DispatcherDriverStatus fromString(String string) {
                return null;
            }
        });
        
        allDriversCheck = new CheckBox("All Drivers");
        allDriversCheck.setOnAction(e -> {
            driverCombo.setDisable(allDriversCheck.isSelected());
            if (allDriversCheck.isSelected()) {
                driverCombo.setValue(null);
            }
        });
        
        HBox driverBox = new HBox(10, driverCombo, allDriversCheck);
        driverBox.setAlignment(Pos.CENTER_LEFT);
        
        grid.add(driverLabel, 0, row);
        grid.add(driverBox, 1, row, 2, 1);
        row++;
        
        // Date Range
        Label dateLabel = new Label("Date Range:");
        dateLabel.getStyleClass().add("config-label");
        
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        startDatePicker.setPrefWidth(120);
        
        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPrefWidth(120);
        
        periodCombo = new ComboBox<>();
        periodCombo.getItems().addAll(
            "Custom", "Today", "Yesterday", "This Week", "Last Week",
            "This Month", "Last Month", "This Quarter", "Last Quarter",
            "This Year", "Last Year", "Last 30 Days", "Last 90 Days"
        );
        periodCombo.setValue("Custom");
        periodCombo.setPrefWidth(120);
        periodCombo.setOnAction(e -> updateDateRange());
        
        HBox dateBox = new HBox(10, 
            startDatePicker, new Label("to"), endDatePicker, 
            new Separator(javafx.geometry.Orientation.VERTICAL), periodCombo
        );
        dateBox.setAlignment(Pos.CENTER_LEFT);
        
        grid.add(dateLabel, 0, row);
        grid.add(dateBox, 1, row, 2, 1);
        row++;
        
        // Export Format
        Label formatLabel = new Label("Export Format:");
        formatLabel.getStyleClass().add("config-label");
        
        formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll(ExportFormat.values());
        formatCombo.setValue(ExportFormat.PDF);
        formatCombo.setPrefWidth(120);
        
        CheckBox autoOpenCheck = new CheckBox("Open after export");
        autoOpenCheck.setSelected(true);
        
        HBox formatBox = new HBox(10, formatCombo, autoOpenCheck);
        formatBox.setAlignment(Pos.CENTER_LEFT);
        
        grid.add(formatLabel, 0, row);
        grid.add(formatBox, 1, row, 2, 1);
        
        // Column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(120);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPrefWidth(400);
        col2.setHgrow(Priority.SOMETIMES);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setHgrow(Priority.ALWAYS);
        
        grid.getColumnConstraints().addAll(col1, col2, col3);
        
        return grid;
    }
    
    private TabPane createReportTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Text Report Tab
        Tab textTab = new Tab("Text Report");
        textTab.setContent(createTextReportView());
        
        // Table Report Tab
        Tab tableTab = new Tab("Table View");
        tableTab.setContent(createTableReportView());
        
        // Charts Tab
        Tab chartsTab = new Tab("Analytics");
        chartsTab.setContent(createChartsView());
        
        // Preview Tab
        Tab previewTab = new Tab("Print Preview");
        reportWebView = new WebView();
        ScrollPane webScroll = new ScrollPane(reportWebView);
        webScroll.setFitToWidth(true);
        previewTab.setContent(webScroll);
        
        tabs.getTabs().addAll(textTab, tableTab, chartsTab, previewTab);
        
        return tabs;
    }
    
    private Node createTextReportView() {
        reportTextArea = new TextArea();
        reportTextArea.setEditable(false);
        reportTextArea.setFont(Font.font("Consolas", 12));
        reportTextArea.getStyleClass().add("report-text");
        
        // Add context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> reportTextArea.copy());
        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setOnAction(e -> reportTextArea.selectAll());
        MenuItem findItem = new MenuItem("Find...");
        findItem.setOnAction(e -> showFindDialog());
        
        contextMenu.getItems().addAll(copyItem, selectAllItem, new SeparatorMenuItem(), findItem);
        reportTextArea.setContextMenu(contextMenu);
        
        return reportTextArea;
    }
    
    private Node createTableReportView() {
        reportTable = new TableView<>();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        reportTable.getStyleClass().add("report-table");
        
        // Enable multi-selection
        reportTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Context menu
        ContextMenu tableMenu = new ContextMenu();
        MenuItem exportSelectedItem = new MenuItem("Export Selected Rows");
        exportSelectedItem.setOnAction(e -> exportSelectedRows());
        MenuItem copyTableItem = new MenuItem("Copy Table Data");
        copyTableItem.setOnAction(e -> copyTableData());
        
        tableMenu.getItems().addAll(exportSelectedItem, copyTableItem);
        reportTable.setContextMenu(tableMenu);
        
        return reportTable;
    }
    
    private Node createChartsView() {
        chartsContainer = new VBox(20);
        chartsContainer.setPadding(new Insets(20));
        chartsContainer.setAlignment(Pos.TOP_CENTER);
        
        ScrollPane scrollPane = new ScrollPane(chartsContainer);
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");
        
        progressBar = new ProgressBar();
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label timestampLabel = new Label();
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timestampLabel.setText(LocalDateTime.now().format(DATETIME_FORMAT));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        
        statusBar.getChildren().addAll(statusLabel, progressBar, spacer, timestampLabel);
        
        return statusBar;
    }
    
    private void setupBindings() {
        // Disable driver combo when all drivers selected
        driverCombo.disableProperty().bind(allDriversCheck.selectedProperty());
        
        // Update report when configuration changes
        reportTypeCombo.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                adjustUIForReportType(newVal);
            }
        });
        
        // Validate date range
        startDatePicker.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && endDatePicker.getValue() != null && 
                newVal.isAfter(endDatePicker.getValue())) {
                endDatePicker.setValue(newVal);
            }
        });
        
        endDatePicker.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && startDatePicker.getValue() != null && 
                newVal.isBefore(startDatePicker.getValue())) {
                startDatePicker.setValue(newVal);
            }
        });
    }
    
    private void setupKeyboardShortcuts() {
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN),
            this::generateReport
        );
        
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
            this::exportReport
        );
        
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN),
            this::printReport
        );
        
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
            this::showFindDialog
        );
    }
    
    private void generateReport() {
        if (generating.get()) return;
        
        // Validate inputs
        if (!validateInputs()) return;
        
        generating.set(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // Indeterminate
        statusLabel.setText("Generating report...");
        
        // Clear previous report
        clearReport();
        
        // Get report parameters
        ReportType reportType = reportTypeCombo.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        List<DispatcherDriverStatus> drivers = getSelectedDrivers();
        
        // Generate report in background
        Task<ReportResult> reportTask = new Task<ReportResult>() {
            @Override
            protected ReportResult call() throws Exception {
                updateMessage("Loading data...");
                updateProgress(0, 100);
                
                ReportResult result = new ReportResult();
                
                switch (reportType) {
                    case DRIVER_SUMMARY:
                        result = generateDriverSummary(drivers, startDate, endDate);
                        break;
                    case LOAD_DETAILS:
                        result = generateLoadDetails(drivers, startDate, endDate);
                        break;
                    case PERFORMANCE_METRICS:
                        result = generatePerformanceMetrics(drivers, startDate, endDate);
                        break;
                    case FINANCIAL_SUMMARY:
                        result = generateFinancialSummary(drivers, startDate, endDate);
                        break;
                    case UTILIZATION_REPORT:
                        result = generateUtilizationReport(drivers, startDate, endDate);
                        break;
                    case COMPLIANCE_REPORT:
                        result = generateComplianceReport(drivers, startDate, endDate);
                        break;
                    case MAINTENANCE_REPORT:
                        result = generateMaintenanceReport(drivers, startDate, endDate);
                        break;
                    case CUSTOM_REPORT:
                        result = generateCustomReport(drivers, startDate, endDate);
                        break;
                }
                
                updateProgress(100, 100);
                return result;
            }
        };
        
        statusLabel.textProperty().bind(reportTask.messageProperty());
        progressBar.progressProperty().bind(reportTask.progressProperty());
        
        reportTask.setOnSucceeded(e -> {
            ReportResult result = reportTask.getValue();
            displayReport(result);
            generating.set(false);
            progressBar.setVisible(false);
            statusLabel.setText("Report generated successfully");
            saveReportMetadata(result);
        });
        
        reportTask.setOnFailed(e -> {
            Throwable error = reportTask.getException();
            logger.error("Failed to generate report", error);
            showError("Report Generation Failed", error.getMessage());
            generating.set(false);
            progressBar.setVisible(false);
            statusLabel.setText("Report generation failed");
        });
        
        new Thread(reportTask).start();
    }
    
    private boolean validateInputs() {
        List<String> errors = new ArrayList<>();
        
        if (!allDriversCheck.isSelected() && driverCombo.getValue() == null) {
            errors.add("Please select a driver or check 'All Drivers'");
        }
        
        if (startDatePicker.getValue() == null) {
            errors.add("Please select a start date");
        }
        
        if (endDatePicker.getValue() == null) {
            errors.add("Please select an end date");
        }
        
        if (startDatePicker.getValue() != null && endDatePicker.getValue() != null &&
            startDatePicker.getValue().isAfter(endDatePicker.getValue())) {
            errors.add("Start date must be before end date");
        }
        
        if (!errors.isEmpty()) {
            showValidationErrors(errors);
            return false;
        }
        
        return true;
    }
    
    private List<DispatcherDriverStatus> getSelectedDrivers() {
        if (allDriversCheck.isSelected()) {
            return new ArrayList<>(controller.getDriverStatuses());
        } else {
            return Collections.singletonList(driverCombo.getValue());
        }
    }
    
    private void clearReport() {
        reportTextArea.clear();
        reportTable.getItems().clear();
        reportTable.getColumns().clear();
        chartsContainer.getChildren().clear();
        reportWebView.getEngine().loadContent("");
        currentReportHTML = "";
        currentReportData = null;
    }
    
    private void displayReport(ReportResult result) {
        // Display text report
        reportTextArea.setText(result.getTextReport());
        
        // Display table data
        if (result.getTableData() != null && !result.getTableData().isEmpty()) {
            populateTable(result.getTableData(), result.getColumns());
        }
        
        // Display charts
        if (result.getCharts() != null && !result.getCharts().isEmpty()) {
            displayCharts(result.getCharts());
        }
        
        // Display HTML preview
        currentReportHTML = result.getHtmlReport();
        reportWebView.getEngine().loadContent(currentReportHTML);
        
        // Store data for export
        currentReportData = result.getTableData();
        reportMetadata = result.getMetadata();
    }
    
    private void populateTable(List<ReportRow> data, List<String> columns) {
        reportTable.getColumns().clear();
        
        for (String column : columns) {
            TableColumn<ReportRow, String> col = new TableColumn<>(column);
            col.setCellValueFactory(cellData -> 
                new SimpleStringProperty(cellData.getValue().getValue(column)));
            
            // Add sorting
            col.setSortable(true);
            
            // Add custom cell factory for formatting
            col.setCellFactory(tc -> new TableCell<ReportRow, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        // Apply formatting based on column type
                        if (column.contains("Amount") || column.contains("Revenue") || 
                            column.contains("Cost") || column.contains("Pay")) {
                            setAlignment(Pos.CENTER_RIGHT);
                            setStyle("-fx-font-family: monospace;");
                        } else if (column.contains("Date")) {
                            setAlignment(Pos.CENTER);
                        }
                    }
                }
            });
            
            reportTable.getColumns().add(col);
        }
        
        reportTable.setItems(FXCollections.observableArrayList(data));
    }
    
    private void displayCharts(List<Chart> charts) {
        chartsContainer.getChildren().clear();
        
        for (Chart chart : charts) {
            VBox chartBox = new VBox(10);
            chartBox.setAlignment(Pos.CENTER);
            chartBox.getStyleClass().add("chart-container");
            
            chart.setPrefHeight(400);
            chart.setMaxWidth(800);
            
            chartBox.getChildren().add(chart);
            chartsContainer.getChildren().add(chartBox);
        }
    }
    
    // Report generation methods
    
    private ReportResult generateDriverSummary(List<DispatcherDriverStatus> drivers, 
                                               LocalDate startDate, LocalDate endDate) {
        ReportResult result = new ReportResult();
        StringBuilder text = new StringBuilder();
        List<ReportRow> tableData = new ArrayList<>();
        List<String> columns = Arrays.asList(
            "Driver", "Total Loads", "Completed", "Miles", "Revenue", "Hours", "Avg Load Time"
        );
        
        // Header
        text.append(createReportHeader("Driver Summary Report", startDate, endDate));
        text.append("\n");
        
        // Process each driver
        for (DispatcherDriverStatus driverStatus : drivers) {
            Driver driver = driverStatus.getDriver();
            List<Load> loads = controller.getLoadsForDriverAndRange(driver, startDate, endDate);
            
            // Calculate metrics
            int totalLoads = loads.size();
            long completedLoads = loads.stream()
                .filter(l -> l.getStatus() == LoadStatus.DELIVERED || 
                            l.getStatus() == LoadStatus.PAID)
                .count();
            double totalMiles = loads.stream()
                .mapToDouble(Load::getMiles)
                .sum();
            double totalRevenue = loads.stream()
                .mapToDouble(Load::getRate)
                .sum();
            double totalHours = calculateTotalHours(loads);
            double avgLoadTime = totalLoads > 0 ? totalHours / totalLoads : 0;
            
            // Add to text report
            text.append(String.format("\n%s\n", driver.getName()));
            text.append(String.format("  Total Loads: %d\n", totalLoads));
            text.append(String.format("  Completed: %d (%.1f%%)\n", 
                completedLoads, (completedLoads * 100.0 / Math.max(totalLoads, 1))));
            text.append(String.format("  Total Miles: %,.0f\n", totalMiles));
            text.append(String.format("  Total Revenue: %s\n", CURRENCY_FORMAT.format(totalRevenue)));
            text.append(String.format("  Total Hours: %.1f\n", totalHours));
            text.append(String.format("  Average Load Time: %.1f hours\n", avgLoadTime));
            
            // Add to table data
            ReportRow row = new ReportRow();
            row.addValue("Driver", driver.getName());
            row.addValue("Total Loads", String.valueOf(totalLoads));
            row.addValue("Completed", String.format("%d (%.1f%%)", 
                completedLoads, (completedLoads * 100.0 / Math.max(totalLoads, 1))));
            row.addValue("Miles", String.format("%,.0f", totalMiles));
            row.addValue("Revenue", CURRENCY_FORMAT.format(totalRevenue));
            row.addValue("Hours", String.format("%.1f", totalHours));
            row.addValue("Avg Load Time", String.format("%.1f hrs", avgLoadTime));
            
            tableData.add(row);
        }
        
        // Summary totals
        text.append("\n").append("=".repeat(60)).append("\n");
        text.append(createSummaryTotals(tableData));
        
        // Create charts
        List<Chart> charts = new ArrayList<>();
        charts.add(createRevenueByDriverChart(tableData));
        charts.add(createLoadsCompletionChart(tableData));
        
        // Set result
        result.setTextReport(text.toString());
        result.setTableData(tableData);
        result.setColumns(columns);
        result.setCharts(charts);
        result.setHtmlReport(generateHTMLReport(text.toString(), tableData, columns));
        result.setMetadata(createMetadata("Driver Summary", startDate, endDate));
        
        return result;
    }
    
    private ReportResult generateLoadDetails(List<DispatcherDriverStatus> drivers,
                                           LocalDate startDate, LocalDate endDate) {
        ReportResult result = new ReportResult();
        StringBuilder text = new StringBuilder();
        List<ReportRow> tableData = new ArrayList<>();
        List<String> columns = Arrays.asList(
            "Load #", "Driver", "Customer", "Origin", "Destination", 
            "Pickup Date", "Delivery Date", "Status", "Miles", "Rate", "Driver Pay"
        );
        
        // Header
        text.append(createReportHeader("Load Details Report", startDate, endDate));
        text.append("\n");
        
        // Process loads
        for (DispatcherDriverStatus driverStatus : drivers) {
            Driver driver = driverStatus.getDriver();
            List<Load> loads = controller.getLoadsForDriverAndRange(driver, startDate, endDate);
            
            for (Load load : loads) {
                // Add to text report
                text.append(String.format("\nLoad #%s - %s\n", 
                    load.getLoadNumber(), load.getCustomer()));
                text.append(String.format("  Driver: %s\n", driver.getName()));
                text.append(String.format("  Route: %s → %s\n", 
                    load.getOriginCity() + ", " + load.getOriginState(),
                    load.getDestCity() + ", " + load.getDestState()));
                text.append(String.format("  Dates: %s to %s\n",
                    load.getPickupDate().format(DATE_FORMAT),
                    load.getDeliveryDate().format(DATE_FORMAT)));
                text.append(String.format("  Status: %s\n", load.getStatus()));
                text.append(String.format("  Miles: %,.0f\n", load.getMiles()));
                text.append(String.format("  Rate: %s\n", CURRENCY_FORMAT.format(load.getRate())));
                text.append(String.format("  Driver Pay: %s\n", 
                    CURRENCY_FORMAT.format(load.getDriverPay())));
                
                // Add to table data
                ReportRow row = new ReportRow();
                row.addValue("Load #", load.getLoadNumber());
                row.addValue("Driver", driver.getName());
                row.addValue("Customer", load.getCustomer());
                row.addValue("Origin", load.getOriginCity() + ", " + load.getOriginState());
                row.addValue("Destination", load.getDestCity() + ", " + load.getDestState());
                row.addValue("Pickup Date", load.getPickupDate().format(DATE_FORMAT));
                row.addValue("Delivery Date", load.getDeliveryDate().format(DATE_FORMAT));
                row.addValue("Status", load.getStatus().toString());
                row.addValue("Miles", String.format("%,.0f", load.getMiles()));
                row.addValue("Rate", CURRENCY_FORMAT.format(load.getRate()));
                row.addValue("Driver Pay", CURRENCY_FORMAT.format(load.getDriverPay()));
                
                tableData.add(row);
            }
        }
        
        // Summary
        text.append("\n").append("=".repeat(60)).append("\n");
        text.append(String.format("Total Loads: %d\n", tableData.size()));
        
        double totalRevenue = tableData.stream()
            .mapToDouble(row -> parseCurrency(row.getValue("Rate")))
            .sum();
        double totalDriverPay = tableData.stream()
            .mapToDouble(row -> parseCurrency(row.getValue("Driver Pay")))
            .sum();
        double totalMiles = tableData.stream()
            .mapToDouble(row -> parseDouble(row.getValue("Miles")))
            .sum();
        
        text.append(String.format("Total Revenue: %s\n", CURRENCY_FORMAT.format(totalRevenue)));
        text.append(String.format("Total Driver Pay: %s\n", CURRENCY_FORMAT.format(totalDriverPay)));
        text.append(String.format("Total Miles: %,.0f\n", totalMiles));
        
        // Create charts
        List<Chart> charts = new ArrayList<>();
        charts.add(createLoadStatusChart(tableData));
        charts.add(createRevenueTimelineChart(tableData));
        
        // Set result
        result.setTextReport(text.toString());
        result.setTableData(tableData);
        result.setColumns(columns);
        result.setCharts(charts);
        result.setHtmlReport(generateHTMLReport(text.toString(), tableData, columns));
        result.setMetadata(createMetadata("Load Details", startDate, endDate));
        
        return result;
    }
    
    private ReportResult generatePerformanceMetrics(List<DispatcherDriverStatus> drivers,
                                                   LocalDate startDate, LocalDate endDate) {
        ReportResult result = new ReportResult();
        StringBuilder text = new StringBuilder();
        List<ReportRow> tableData = new ArrayList<>();
        List<String> columns = Arrays.asList(
            "Driver", "On-Time %", "Utilization %", "Revenue/Mile", 
            "Avg Daily Miles", "Fuel Efficiency", "Safety Score"
        );
        
        text.append(createReportHeader("Performance Metrics Report", startDate, endDate));
        text.append("\n");
        
        // Process metrics for each driver
        for (DispatcherDriverStatus driverStatus : drivers) {
            Driver driver = driverStatus.getDriver();
            List<Load> loads = controller.getLoadsForDriverAndRange(driver, startDate, endDate);
            
            if (loads.isEmpty()) continue;
            
            // Calculate performance metrics
            double onTimePercent = calculateOnTimePercent(loads);
            double utilizationPercent = calculateUtilization(driver, startDate, endDate);
            double revenuePerMile = calculateRevenuePerMile(loads);
            double avgDailyMiles = calculateAvgDailyMiles(loads, startDate, endDate);
            double fuelEfficiency = calculateFuelEfficiency(driver, loads);
            double safetyScore = calculateSafetyScore(driver, startDate, endDate);
            
            // Add to report
            text.append(String.format("\n%s Performance Metrics:\n", driver.getName()));
            text.append(String.format("  On-Time Delivery: %.1f%%\n", onTimePercent));
            text.append(String.format("  Utilization: %.1f%%\n", utilizationPercent));
            text.append(String.format("  Revenue per Mile: %s\n", 
                CURRENCY_FORMAT.format(revenuePerMile)));
            text.append(String.format("  Average Daily Miles: %.0f\n", avgDailyMiles));
            text.append(String.format("  Fuel Efficiency: %.1f MPG\n", fuelEfficiency));
            text.append(String.format("  Safety Score: %.0f/100\n", safetyScore));
            
            // Add to table
            ReportRow row = new ReportRow();
            row.addValue("Driver", driver.getName());
            row.addValue("On-Time %", String.format("%.1f%%", onTimePercent));
            row.addValue("Utilization %", String.format("%.1f%%", utilizationPercent));
            row.addValue("Revenue/Mile", CURRENCY_FORMAT.format(revenuePerMile));
            row.addValue("Avg Daily Miles", String.format("%.0f", avgDailyMiles));
            row.addValue("Fuel Efficiency", String.format("%.1f MPG", fuelEfficiency));
            row.addValue("Safety Score", String.format("%.0f", safetyScore));
            
            tableData.add(row);
        }
        
        // Create performance charts
        List<Chart> charts = new ArrayList<>();
        charts.add(createPerformanceRadarChart(tableData));
        charts.add(createUtilizationChart(tableData));
        
        result.setTextReport(text.toString());
        result.setTableData(tableData);
        result.setColumns(columns);
        result.setCharts(charts);
        result.setHtmlReport(generateHTMLReport(text.toString(), tableData, columns));
        result.setMetadata(createMetadata("Performance Metrics", startDate, endDate));
        
        return result;
    }
    
    // Additional report generation methods would continue...
    // (Financial Summary, Utilization Report, Compliance Report, etc.)
    
    // Helper methods
    
    private String createReportHeader(String title, LocalDate startDate, LocalDate endDate) {
        StringBuilder header = new StringBuilder();
        header.append("=".repeat(60)).append("\n");
        header.append(String.format("%s\n", title.toUpperCase()));
        header.append("=".repeat(60)).append("\n");
        header.append(String.format("Company: %s\n", DispatcherSettings.getInstance().getCompanyName()));
        header.append(String.format("Period: %s to %s\n", 
            startDate.format(DATE_FORMAT), endDate.format(DATE_FORMAT)));
        header.append(String.format("Generated: %s\n", 
            LocalDateTime.now().format(DATETIME_FORMAT)));
        header.append(String.format("Generated by: %s\n", System.getProperty("user.name")));
        header.append("=".repeat(60));
        return header.toString();
    }
    
    private double calculateTotalHours(List<Load> loads) {
        return loads.stream()
            .mapToDouble(load -> {
                if (load.getPickupDate() != null && load.getDeliveryDate() != null) {
                    return ChronoUnit.HOURS.between(
                        load.getPickupDate().atStartOfDay(),
                        load.getDeliveryDate().atStartOfDay()
                    );
                }
                return 0;
            })
            .sum();
    }
    
    private Chart createRevenueByDriverChart(List<ReportRow> data) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Driver");
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Revenue ($)");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Revenue by Driver");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue");
        
        for (ReportRow row : data) {
            String driver = row.getValue("Driver");
            double revenue = parseCurrency(row.getValue("Revenue"));
            series.getData().add(new XYChart.Data<>(driver, revenue));
        }
        
        chart.getData().add(series);
        return chart;
    }
    
    private void exportReport() {
        if (currentReportData == null || currentReportData.isEmpty()) {
            showWarning("No Report", "Please generate a report first");
            return;
        }
        
        ExportFormat format = formatCombo.getValue();
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Report");
        fileChooser.setInitialFileName(generateFileName(format));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(format.getDisplayName(), "*" + format.getExtension())
        );
        
        File file = fileChooser.showSaveDialog(getDialogPane().getScene().getWindow());
        if (file != null) {
            exportToFile(file, format);
        }
    }
    
    private void exportToFile(File file, ExportFormat format) {
        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Exporting to " + format.getDisplayName() + "...");
                
                switch (format) {
                    case PDF:
                        exportToPDF(file);
                        break;
                    case EXCEL:
                        exportToExcel(file);
                        break;
                    case CSV:
                        exportToCSV(file);
                        break;
                    case HTML:
                        exportToHTML(file);
                        break;
                    case JSON:
                        exportToJSON(file);
                        break;
                }
                
                return null;
            }
        };
        
        statusLabel.textProperty().bind(exportTask.messageProperty());
        
        exportTask.setOnSucceeded(e -> {
            statusLabel.setText("Export completed successfully");
            logger.info("Report exported to: {}", file.getAbsolutePath());
            
            // Optionally open the file
            if (file.exists()) {
                try {
                    java.awt.Desktop.getDesktop().open(file);
                } catch (IOException ex) {
                    logger.error("Failed to open exported file", ex);
                }
            }
        });
        
        exportTask.setOnFailed(e -> {
            Throwable error = exportTask.getException();
            logger.error("Export failed", error);
            showError("Export Failed", error.getMessage());
            statusLabel.setText("Export failed");
        });
        
        new Thread(exportTask).start();
    }
    
    private void exportToPDF(File file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                contentStream.beginText();
                contentStream.setLeading(14.5f);
                contentStream.newLineAtOffset(50, 750);
                
                // Write report title
                String title = (String) reportMetadata.get("title");
                contentStream.showText(title != null ? title : "Dispatcher Report");
                contentStream.newLine();
                contentStream.newLine();
                
                // Write report content
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                String[] lines = reportTextArea.getText().split("\n");
                for (String line : lines) {
                    if (line.length() > 80) {
                        // Wrap long lines
                        List<String> wrapped = wrapText(line, 80);
                        for (String wrap : wrapped) {
                            contentStream.showText(wrap);
                            contentStream.newLine();
                        }
                    } else {
                        contentStream.showText(line);
                        contentStream.newLine();
                    }
                }
                
                contentStream.endText();
            }
            
            document.save(file);
        }
    }
    
    private void exportToExcel(File file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            List<String> columns = (List<String>) reportMetadata.get("columns");
            for (int i = 0; i < columns.size(); i++) {
org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            for (ReportRow dataRow : currentReportData) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < columns.size(); i++) {
                    String value = dataRow.getValue(columns.get(i));
                    row.createCell(i).setCellValue(value != null ? value : "");
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }
    
    // Utility methods
    
    private Button createIconButton(String icon, String tooltip, 
                                  javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(handler);
        button.getStyleClass().add("icon-button");
        return button;
    }
    
    private void showValidationErrors(List<String> errors) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Please correct the following errors:");
        alert.setContentText(String.join("\n", errors));
        alert.showAndWait();
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showWarning(String title, String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Inner classes
    
    private static class ReportResult {
        private String textReport = "";
        private String htmlReport = "";
        private List<ReportRow> tableData;
        private List<String> columns;
        private List<Chart> charts;
        private Map<String, Object> metadata;
        
        // Getters and setters
        public String getTextReport() { return textReport; }
        public void setTextReport(String textReport) { this.textReport = textReport; }
        
        public String getHtmlReport() { return htmlReport; }
        public void setHtmlReport(String htmlReport) { this.htmlReport = htmlReport; }
        
        public List<ReportRow> getTableData() { return tableData; }
        public void setTableData(List<ReportRow> tableData) { this.tableData = tableData; }
        
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        
        public List<Chart> getCharts() { return charts; }
        public void setCharts(List<Chart> charts) { this.charts = charts; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    private static class ReportRow {
        private final Map<String, String> values = new HashMap<>();
        
        public void addValue(String column, String value) {
            values.put(column, value);
        }
        
        public String getValue(String column) {
            return values.get(column);
        }
        
        public Map<String, String> getValues() {
            return new HashMap<>(values);
        }
    }
    
    // Additional helper methods would continue...
	
	    // Additional helper methods continuing from line 1270
    
    private Chart createComplianceScoreChart(List<ReportRow> data) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Driver");
        
        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Compliance Score");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Driver Compliance Scores");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Score");
        
        for (ReportRow row : data) {
            String driver = row.getValue("Driver");
            double score = parseDouble(row.getValue("Overall Score"));
            series.getData().add(new XYChart.Data<>(driver, score));
        }
        
        chart.getData().add(series);
        return chart;
    }
    
    private Chart createViolationTrendChart(List<DispatcherDriverStatus> drivers,
                                          LocalDate startDate, LocalDate endDate) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Week");
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Violations");
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Violation Trends");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Total Violations");
        
        // Simulate weekly violation data
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            int violations = (int) (Math.random() * 5);
            series.getData().add(new XYChart.Data<>(
                "Week of " + current.format(DATE_FORMAT), violations
            ));
            current = current.plusWeeks(1);
        }
        
        chart.getData().add(series);
        return chart;
    }
    
    private String createSummaryTotals(List<ReportRow> tableData) {
        StringBuilder summary = new StringBuilder();
        summary.append("SUMMARY TOTALS:\n");
        
        int totalLoads = 0;
        double totalMiles = 0;
        double totalRevenue = 0;
        double totalHours = 0;
        
        for (ReportRow row : tableData) {
            totalLoads += Integer.parseInt(row.getValue("Total Loads"));
            totalMiles += parseDouble(row.getValue("Miles"));
            totalRevenue += parseCurrency(row.getValue("Revenue"));
            totalHours += parseDouble(row.getValue("Hours").replace(" hrs", ""));
        }
        
        summary.append(String.format("  Total Drivers: %d\n", tableData.size()));
        summary.append(String.format("  Total Loads: %d\n", totalLoads));
        summary.append(String.format("  Total Miles: %,.0f\n", totalMiles));
        summary.append(String.format("  Total Revenue: %s\n", CURRENCY_FORMAT.format(totalRevenue)));
        summary.append(String.format("  Total Hours: %.1f\n", totalHours));
        summary.append(String.format("  Average Revenue per Driver: %s\n", 
            CURRENCY_FORMAT.format(totalRevenue / Math.max(tableData.size(), 1))));
        
        return summary.toString();
    }
    
    private String generateHTMLReport(String textReport, List<ReportRow> tableData, List<String> columns) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<title>Dispatcher Report</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("h1 { color: #333; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f2f2f2; font-weight: bold; }\n");
        html.append("tr:nth-child(even) { background-color: #f9f9f9; }\n");
        html.append(".header { background-color: #4CAF50; color: white; padding: 20px; }\n");
        html.append(".footer { margin-top: 30px; font-size: 12px; color: #666; }\n");
        html.append("</style>\n</head>\n<body>\n");
        
        // Header
        html.append("<div class='header'>\n");
        html.append("<h1>").append(reportMetadata.get("title")).append("</h1>\n");
        html.append("</div>\n");
        
        // Report info
        html.append("<p><strong>Company:</strong> ")
            .append(DispatcherSettings.getInstance().getCompanyName())
            .append("</p>\n");
        html.append("<p><strong>Period:</strong> ").append(reportMetadata.get("startDate"))
            .append(" to ").append(reportMetadata.get("endDate")).append("</p>\n");
        html.append("<p><strong>Generated:</strong> ").append(LocalDateTime.now().format(DATETIME_FORMAT)).append("</p>\n");
        
        // Table
        if (tableData != null && !tableData.isEmpty()) {
            html.append("<table>\n<thead>\n<tr>\n");
            for (String column : columns) {
                html.append("<th>").append(column).append("</th>\n");
            }
            html.append("</tr>\n</thead>\n<tbody>\n");
            
            for (ReportRow row : tableData) {
                html.append("<tr>\n");
                for (String column : columns) {
                    String value = row.getValue(column);
                    html.append("<td>").append(value != null ? value : "").append("</td>\n");
                }
                html.append("</tr>\n");
            }
            html.append("</tbody>\n</table>\n");
        }
        
        // Footer
        html.append("<div class='footer'>\n");
        html.append("<p>Generated by ").append(System.getProperty("user.name")).append("</p>\n");
        html.append("</div>\n");
        
        html.append("</body>\n</html>");
        
        return html.toString();
    }
    
    private Map<String, Object> createMetadata(String title, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("startDate", startDate.format(DATE_FORMAT));
        metadata.put("endDate", endDate.format(DATE_FORMAT));
        metadata.put("generatedAt", LocalDateTime.now());
        metadata.put("generatedBy", System.getProperty("user.name"));
        metadata.put("company", DispatcherSettings.getInstance().getCompanyName());
        
        if (currentReportData != null) {
            List<String> columns = new ArrayList<>();
            if (!currentReportData.isEmpty()) {
                columns.addAll(currentReportData.get(0).getValues().keySet());
            }
            metadata.put("columns", columns);
        }
        
        return metadata;
    }
    
    private void saveReportMetadata(ReportResult result) {
        if (result.getMetadata() != null) {
            reportMetadata.putAll(result.getMetadata());
        }
    }
    
    private String generateFileName(ExportFormat format) {
        String reportType = reportTypeCombo.getValue().getDisplayName().replace(" ", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s_Report_%s%s", reportType, timestamp, format.getExtension());
    }
    
    private List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLength) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    private void exportToCSV(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            List<String> columns = (List<String>) reportMetadata.get("columns");
            
            // Write header
            writer.println(String.join(",", columns));
            
            // Write data
            for (ReportRow row : currentReportData) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    String value = row.getValue(column);
                    // Escape commas and quotes
                    if (value != null && (value.contains(",") || value.contains("\""))) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }
                    values.add(value != null ? value : "");
                }
                writer.println(String.join(",", values));
            }
        }
    }
    
    private void exportToHTML(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.write(currentReportHTML);
        }
    }
    
    private void exportToJSON(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("{");
            writer.println("  \"report\": {");
            writer.println("    \"metadata\": " + toJSON(reportMetadata) + ",");
            writer.println("    \"data\": [");
            
            for (int i = 0; i < currentReportData.size(); i++) {
                ReportRow row = currentReportData.get(i);
                writer.print("      " + toJSON(row.getValues()));
                if (i < currentReportData.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            
            writer.println("    ]");
            writer.println("  }");
            writer.println("}");
        }
    }
    
    private String toJSON(Map<String, ?> map) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (count > 0) json.append(", ");
            json.append("\"").append(entry.getKey()).append("\": ");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJSON(value.toString())).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJSON(value.toString())).append("\"");
            }
            count++;
        }
        json.append("}");
        return json.toString();
    }
    
    private String escapeJSON(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
// Stub methods
    private ReportResult generateFinancialSummary(List<DispatcherDriverStatus> d, LocalDate s, LocalDate e) { return new ReportResult(); }
    private ReportResult generateUtilizationReport(List<DispatcherDriverStatus> d, LocalDate s, LocalDate e) { return new ReportResult(); }
    private ReportResult generateComplianceReport(List<DispatcherDriverStatus> d, LocalDate s, LocalDate e) { return new ReportResult(); }
    private ReportResult generateMaintenanceReport(List<DispatcherDriverStatus> d, LocalDate s, LocalDate e) { return new ReportResult(); }
    private ReportResult generateCustomReport(List<DispatcherDriverStatus> d, LocalDate s, LocalDate e) { return new ReportResult(); }
    private Chart createLoadsCompletionChart(List<ReportRow> data) { return new PieChart(); }
    private Chart createLoadStatusChart(List<ReportRow> data) { return new PieChart(); }
    private Chart createRevenueTimelineChart(List<ReportRow> data) { return new LineChart<>(new CategoryAxis(), new NumberAxis()); }
    private double calculateOnTimePercent(List<Load> loads) { return 0; }
    private double calculateUtilization(Driver driver, LocalDate s, LocalDate e) { return 0; }
    private double calculateRevenuePerMile(List<Load> loads) { return 0; }
    private double calculateAvgDailyMiles(List<Load> loads, LocalDate s, LocalDate e) { return 0; }
    private double calculateFuelEfficiency(Driver driver, List<Load> loads) { return 0; }
    private double calculateSafetyScore(Driver driver, LocalDate s, LocalDate e) { return 0; }
    private Chart createPerformanceRadarChart(List<ReportRow> data) { return new PieChart(); }
    private Chart createUtilizationChart(List<ReportRow> data) { return new PieChart(); }

    private double parseCurrency(String value) {
        if (value == null || value.isEmpty()) return 0;
        // Remove currency symbols and commas
        String cleaned = value.replaceAll("[^0-9.-]", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) return 0;
        // Remove commas and other non-numeric characters except . and -
        String cleaned = value.replaceAll("[^0-9.-]", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private double parsePercent(String value) {
        if (value == null || value.isEmpty()) return 0;
        // Remove % symbol
        String cleaned = value.replace("%", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private void refreshData() {
        controller.refreshData();
        driverCombo.setItems(controller.getDriverStatuses());
        statusLabel.setText("Data refreshed");
    }
    
    private void printReport() {
        if (reportTextArea.getText().isEmpty()) {
            showWarning("No Report", "Please generate a report first");
            return;
        }
        
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob != null && printerJob.showPrintDialog(getDialogPane().getScene().getWindow())) {
            boolean success = printerJob.printPage(reportTextArea);
            if (success) {
                printerJob.endJob();
                statusLabel.setText("Report sent to printer");
            } else {
                showError("Print Failed", "Failed to print the report");
            }
        }
    }
    
    private void showHelp() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Report Help");
        alert.setHeaderText("How to Generate Reports");
        alert.setContentText(
            "1. Select a report type from the dropdown\n" +
            "2. Choose a specific driver or select 'All Drivers'\n" +
            "3. Set the date range for the report\n" +
            "4. Click 'Generate' to create the report\n" +
            "5. Use the tabs to view different report formats\n" +
            "6. Click 'Export' to save the report to a file\n\n" +
            "Keyboard Shortcuts:\n" +
            "Ctrl+G - Generate Report\n" +
            "Ctrl+E - Export Report\n" +
            "Ctrl+P - Print Report\n" +
            "Ctrl+F - Find in Report"
        );
        alert.showAndWait();
    }
    
    private void showFindDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Find");
        dialog.setHeaderText("Find in Report");
        dialog.setContentText("Search for:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(searchText -> {
            String reportText = reportTextArea.getText();
            int index = reportText.toLowerCase().indexOf(searchText.toLowerCase());
            if (index >= 0) {
                reportTextArea.selectRange(index, index + searchText.length());
                reportTextArea.requestFocus();
            } else {
                showWarning("Not Found", "Text not found: " + searchText);
            }
        });
    }
    
    private void updateDateRange() {
        String period = periodCombo.getValue();
        LocalDate today = LocalDate.now();
        
        switch (period) {
            case "Today":
                startDatePicker.setValue(today);
                endDatePicker.setValue(today);
                break;
            case "Yesterday":
                startDatePicker.setValue(today.minusDays(1));
                endDatePicker.setValue(today.minusDays(1));
                break;
            case "This Week":
                startDatePicker.setValue(today.with(java.time.DayOfWeek.MONDAY));
                endDatePicker.setValue(today);
                break;
            case "Last Week":
                LocalDate lastWeekStart = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
                startDatePicker.setValue(lastWeekStart);
                endDatePicker.setValue(lastWeekStart.plusDays(6));
                break;
            case "This Month":
                startDatePicker.setValue(today.withDayOfMonth(1));
                endDatePicker.setValue(today);
                break;
            case "Last Month":
                LocalDate lastMonth = today.minusMonths(1);
                startDatePicker.setValue(lastMonth.withDayOfMonth(1));
                endDatePicker.setValue(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
                break;
            case "This Quarter":
                int quarter = (today.getMonthValue() - 1) / 3;
                LocalDate quarterStart = today.withMonth(quarter * 3 + 1).withDayOfMonth(1);
                startDatePicker.setValue(quarterStart);
                endDatePicker.setValue(today);
                break;
            case "Last Quarter":
                LocalDate lastQuarter = today.minusMonths(3);
                int lastQ = (lastQuarter.getMonthValue() - 1) / 3;
                LocalDate lastQStart = lastQuarter.withMonth(lastQ * 3 + 1).withDayOfMonth(1);
                LocalDate lastQEnd = lastQStart.plusMonths(3).minusDays(1);
                startDatePicker.setValue(lastQStart);
                endDatePicker.setValue(lastQEnd);
                break;
            case "This Year":
                startDatePicker.setValue(today.withDayOfYear(1));
                endDatePicker.setValue(today);
                break;
            case "Last Year":
                LocalDate lastYear = today.minusYears(1);
                startDatePicker.setValue(lastYear.withDayOfYear(1));
                endDatePicker.setValue(lastYear.withDayOfYear(lastYear.lengthOfYear()));
                break;
            case "Last 30 Days":
                startDatePicker.setValue(today.minusDays(30));
                endDatePicker.setValue(today);
                break;
            case "Last 90 Days":
                startDatePicker.setValue(today.minusDays(90));
                endDatePicker.setValue(today);
                break;
        }
    }
    
    private void adjustUIForReportType(ReportType type) {
        // Adjust UI elements based on report type
        switch (type) {
            case CUSTOM_REPORT:
                // Show custom report builder
                showCustomReportBuilder();
                break;
            case FINANCIAL_SUMMARY:
                // Enable cost-related options
                break;
            // Add more cases as needed
        }
    }
    
    private void showCustomReportBuilder() {
        // Implementation for custom report builder dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Custom Report Builder");
        alert.setHeaderText("Custom Report Builder");
        alert.setContentText("Custom report builder is not yet implemented in this version.");
        alert.showAndWait();
    }
    
    private void exportSelectedRows() {
        ObservableList<ReportRow> selectedRows = reportTable.getSelectionModel().getSelectedItems();
        if (selectedRows.isEmpty()) {
            showWarning("No Selection", "Please select rows to export");
            return;
        }
        
        // Create a new report with only selected rows
        List<ReportRow> selectedData = new ArrayList<>(selectedRows);
        currentReportData = selectedData;
        
        exportReport();
        
        // Restore original data
        currentReportData = new ArrayList<>(reportTable.getItems());
    }
    
    private void copyTableData() {
        StringBuilder clipboardText = new StringBuilder();
        List<String> columns = (List<String>) reportMetadata.get("columns");
        
        // Add headers
        clipboardText.append(String.join("\t", columns)).append("\n");
        
        // Add selected rows or all rows
        ObservableList<ReportRow> rows = reportTable.getSelectionModel().getSelectedItems();
        if (rows.isEmpty()) {
            rows = reportTable.getItems();
        }
        
        for (ReportRow row : rows) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                values.add(row.getValue(column));
            }
            clipboardText.append(String.join("\t", values)).append("\n");
        }
        
        // Copy to clipboard
        ClipboardContent content = new ClipboardContent();
        content.putString(clipboardText.toString());
        Clipboard.getSystemClipboard().setContent(content);
        
        statusLabel.setText("Table data copied to clipboard");
    }
    
    private void loadPreferences() {
        // Load user preferences for report settings
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        
        String lastReportType = prefs.get("lastReportType", "DRIVER_SUMMARY");
        try {
            reportTypeCombo.setValue(ReportType.valueOf(lastReportType));
        } catch (IllegalArgumentException e) {
            // Default already set
        }
        
        String lastPeriod = prefs.get("lastPeriod", "Last 30 Days");
        periodCombo.setValue(lastPeriod);
        updateDateRange();
        
        String lastExportFormat = prefs.get("lastExportFormat", "PDF");
        try {
            formatCombo.setValue(ExportFormat.valueOf(lastExportFormat));
        } catch (IllegalArgumentException e) {
            // Default already set
        }
        
        allDriversCheck.setSelected(prefs.getBoolean("allDrivers", false));
    }
    
    @Override
    public void close() {
        // Save preferences before closing
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.put("lastReportType", reportTypeCombo.getValue().name());
        prefs.put("lastPeriod", periodCombo.getValue());
        prefs.put("lastExportFormat", formatCombo.getValue().name());
        prefs.putBoolean("allDrivers", allDriversCheck.isSelected());
        
        super.close();
    }
}
