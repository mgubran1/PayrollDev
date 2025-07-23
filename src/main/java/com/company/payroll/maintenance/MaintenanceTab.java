package com.company.payroll.maintenance;

import com.company.payroll.trucks.Truck;
import com.company.payroll.trucks.TruckDAO;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.geometry.Side;
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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
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
import com.company.payroll.config.DocumentManagerConfig;
import com.company.payroll.config.DocumentManagerSettingsDialog;
import com.company.payroll.config.FilterConfig;

/**
 * Simplified MaintenanceTab for tracking fleet maintenance expenses
 */
public class MaintenanceTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(MaintenanceTab.class);
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    // Data access objects
    private final MaintenanceDAO maintenanceDAO = new MaintenanceDAO();
    private final TruckDAO truckDAO = new TruckDAO();
    private final TrailerDAO trailerDAO = new TrailerDAO();
    
    // UI Components
    private TableView<MaintenanceRecord> maintenanceTable;
    private TextField searchField;
    private ComboBox<String> unitTypeFilter;
    private ComboBox<String> unitNumberFilter;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    
    // Summary labels
    private Label totalCostLabel;
    private Label totalRecordsLabel;
    private Label avgCostLabel;
    private Label truckCostLabel;
    private Label trailerCostLabel;
    
    // Charts
    private PieChart costByTypeChart;
    private BarChart<String, Number> monthlyExpenseChart;
    private PieChart truckVsTrailerChart;
    private VBox topMaintenanceUnitsBox;
    private ListView<String> topMaintenanceUnitsList;
    
    // Color mapping for consistent service type colors
    private final Map<String, String> serviceTypeColorMap = new HashMap<>();
    private final String[] availableColors = {
        "#2196F3", // Blue
        "#4CAF50", // Green
        "#FF9800", // Orange
        "#F44336", // Red
        "#9C27B0", // Purple
        "#00BCD4", // Cyan
        "#FFC107", // Amber
        "#795548", // Brown
        "#607D8B", // Blue Grey
        "#E91E63", // Pink
        "#3F51B5", // Indigo
        "#009688"  // Teal
    };
    private int nextColorIndex = 0;
    
    // Data
    private ObservableList<MaintenanceRecord> allRecords = FXCollections.observableArrayList();
    private FilteredList<MaintenanceRecord> filteredRecords;
    
    // Document storage path - now uses configurable path
    private String docStoragePath;
    
    // Config file constants (same as PayrollTab)
    private static final String CONFIG_FILE = "payroll_config.properties";
    private static final String COMPANY_NAME_KEY = "company.name";
    
    public MaintenanceTab() {
        setText("Maintenance");
        setClosable(false);
        
        logger.info("Initializing MaintenanceTab");
        
        // Initialize common service type colors for consistency
        initializeServiceTypeColors();
        
        // Initialize document storage path
        docStoragePath = DocumentManagerConfig.getMaintenanceStoragePath();
        
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
        header.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 5;");
        
        // Get company name from config
        String companyName = getCompanyNameFromConfig();
        
        Label companyLabel = new Label(companyName);
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        companyLabel.setTextFill(Color.WHITE);
        
        Label titleLabel = new Label("Maintenance Expense Tracking");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.LIGHTGRAY);
        
        Label subtitleLabel = new Label("Track maintenance costs for your trucking fleet");
        subtitleLabel.setFont(Font.font("Arial", 12));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        VBox titleBox = new VBox(3, companyLabel, titleLabel, subtitleLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Total cost display
        Label totalLabel = new Label("Total Fleet Maintenance Cost:");
        totalLabel.setFont(Font.font("Arial", 14));
        totalLabel.setTextFill(Color.WHITE);
        
        totalCostLabel = new Label("$0.00");
        totalCostLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        totalCostLabel.setTextFill(Color.LIGHTGREEN);
        
        VBox totalBox = new VBox(5, totalLabel, totalCostLabel);
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
        searchField.setPromptText("Search by unit, service type, vendor...");
        searchField.setPrefWidth(250);
        
        unitTypeFilter = new ComboBox<>();
        unitTypeFilter.getItems().addAll("All Units", "Trucks Only", "Trailers Only");
        unitTypeFilter.setValue("All Units");
        unitTypeFilter.setPrefWidth(120);
        
        unitNumberFilter = new ComboBox<>();
        unitNumberFilter.setPromptText("Select Unit");
        unitNumberFilter.setPrefWidth(150);
        updateUnitNumberFilter();
        
        searchRow.getChildren().addAll(
            new Label("Search:"), searchField,
            new Separator(Orientation.VERTICAL),
            new Label("Type:"), unitTypeFilter,
            new Label("Unit:"), unitNumberFilter
        );
        
        // Date Range and Actions Row
        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        
        // Load saved date range or use defaults
        FilterConfig.DateRange savedRange = FilterConfig.loadMaintenanceDateRange();
        startDatePicker = new DatePicker(savedRange.getStartDate());
        endDatePicker = new DatePicker(savedRange.getEndDate());
        
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
        unitTypeFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateUnitNumberFilter();
            applyFilters();
        });
        unitNumberFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
            // Auto-save date range
            if (newVal != null && endDatePicker.getValue() != null) {
                FilterConfig.saveMaintenanceDateRange(newVal, endDatePicker.getValue());
            }
        });
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
            // Auto-save date range
            if (newVal != null && startDatePicker.getValue() != null) {
                FilterConfig.saveMaintenanceDateRange(startDatePicker.getValue(), newVal);
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
        
        VBox avgCostCard = createSummaryCard("Average Cost", "$0.00", "#27ae60");
        avgCostLabel = (Label) avgCostCard.lookup(".value-label");
        
        VBox truckCostCard = createSummaryCard("Truck Expenses", "$0.00", "#e74c3c");
        truckCostLabel = (Label) truckCostCard.lookup(".value-label");
        
        VBox trailerCostCard = createSummaryCard("Trailer Expenses", "$0.00", "#f39c12");
        trailerCostLabel = (Label) trailerCostCard.lookup(".value-label");
        
        summaryCards.getChildren().addAll(totalRecordsCard, avgCostCard, truckCostCard, trailerCostCard);
        
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
        
        // Maintenance History Tab
        Tab historyTab = new Tab("Maintenance History");
        historyTab.setContent(createHistorySection());
        
        // Analytics Tab
        Tab analyticsTab = new Tab("Analytics");
        analyticsTab.setContent(createAnalyticsSection());
        
        // Add listener to reapply colors when analytics tab is selected
        analyticsTab.setOnSelectionChanged(event -> {
            if (analyticsTab.isSelected()) {
                Platform.runLater(() -> {
                    updateCharts();
                    Platform.runLater(() -> {
                        reapplyAllChartColors();
                    });
                });
            }
        });
        
        tabPane.getTabs().addAll(historyTab, analyticsTab);
        
        return tabPane;
    }
    
    private VBox createHistorySection() {
        VBox historySection = new VBox(10);
        historySection.setPadding(new Insets(10));
        
        // Table controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        
        Button uploadDocButton = ModernButtonStyles.createPrimaryButton("📤 Upload Document");
        uploadDocButton.setOnAction(e -> uploadDocument());
        
        Button printDocButton = ModernButtonStyles.createSuccessButton("🖨️ Print Document");
        printDocButton.setOnAction(e -> printSelectedDocument());
        
        Button editButton = ModernButtonStyles.createWarningButton("✏️ Edit");
        editButton.setOnAction(e -> editSelectedRecord());
        
        Button deleteButton = ModernButtonStyles.createDangerButton("🗑️ Delete");
        deleteButton.setOnAction(e -> deleteSelectedRecord());
        
        tableControls.getChildren().addAll(uploadDocButton, printDocButton, editButton, deleteButton);
        
        // Maintenance Table
        maintenanceTable = new TableView<>();
        maintenanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        maintenanceTable.setPrefHeight(400);
        
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
        dateCol.setPrefWidth(100);
        
        TableColumn<MaintenanceRecord, String> unitTypeCol = new TableColumn<>("Unit Type");
        unitTypeCol.setCellValueFactory(c -> {
            String type = c.getValue().getVehicleType() != null ? 
                         c.getValue().getVehicleType().toString() : "";
            return new SimpleStringProperty(type);
        });
        unitTypeCol.setPrefWidth(80);
        
        TableColumn<MaintenanceRecord, String> unitNumberCol = new TableColumn<>("Unit Number");
        unitNumberCol.setCellValueFactory(new PropertyValueFactory<>("vehicle"));
        unitNumberCol.setPrefWidth(120);
        
        TableColumn<MaintenanceRecord, String> serviceTypeCol = new TableColumn<>("Service Type");
        serviceTypeCol.setCellValueFactory(new PropertyValueFactory<>("serviceType"));
        serviceTypeCol.setPrefWidth(150);
        
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
        mileageCol.setPrefWidth(80);
        
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
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        costCol.setPrefWidth(100);
        
        TableColumn<MaintenanceRecord, String> vendorCol = new TableColumn<>("Vendor");
        vendorCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getServiceProvider()));
        vendorCol.setPrefWidth(150);
        
        TableColumn<MaintenanceRecord, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReceiptNumber()));
        invoiceCol.setPrefWidth(100);
        
        TableColumn<MaintenanceRecord, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setPrefWidth(200);
        
        maintenanceTable.getColumns().setAll(java.util.List.of(
                dateCol, unitTypeCol, unitNumberCol, serviceTypeCol,
                mileageCol, costCol, vendorCol, invoiceCol, notesCol));
        
        // Double-click to edit
        maintenanceTable.setRowFactory(tv -> {
            TableRow<MaintenanceRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showEditExpenseDialog(row.getItem());
                }
            });
            return row;
        });
        
        historySection.getChildren().addAll(tableControls, maintenanceTable);
        
        return historySection;
    }
    
    private VBox createAnalyticsSection() {
        VBox analyticsSection = new VBox(20);
        analyticsSection.setPadding(new Insets(20));
        analyticsSection.setStyle("-fx-background-color: #F5F5F5;");
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Cost by Service Type Chart
        VBox serviceTypeChartBox = createChartBox("Expenses by Service Type");
        
        costByTypeChart = new PieChart();
        costByTypeChart.setPrefHeight(350);
        costByTypeChart.setAnimated(true);
        costByTypeChart.setLabelsVisible(false); // Hide labels on pie to avoid clutter
        costByTypeChart.setLegendSide(Side.BOTTOM);
        costByTypeChart.setStyle("-fx-font-family: 'Arial'; -fx-font-size: 12px;");
        costByTypeChart.setLegendVisible(true);
        costByTypeChart.setPrefWidth(600);
        costByTypeChart.setMaxWidth(Double.MAX_VALUE);
        
        serviceTypeChartBox.getChildren().add(costByTypeChart);
        
        // Monthly Expense Chart
        VBox monthlyChartBox = createChartBox("Monthly Maintenance Expenses");
        
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
        
        monthlyExpenseChart = new BarChart<>(xAxis, yAxis);
        monthlyExpenseChart.setPrefHeight(350);
        monthlyExpenseChart.setAnimated(true);
        monthlyExpenseChart.setLegendVisible(false);
        monthlyExpenseChart.setStyle("-fx-font-family: 'Arial';");
        monthlyExpenseChart.setBarGap(3);
        monthlyExpenseChart.setCategoryGap(20);
        
        monthlyChartBox.getChildren().add(monthlyExpenseChart);
        
        // Truck vs Trailer Chart
        VBox truckTrailerChartBox = createChartBox("Truck vs Trailer Expenses");
        
        truckVsTrailerChart = new PieChart();
        truckVsTrailerChart.setPrefHeight(350);
        truckVsTrailerChart.setAnimated(true);
        truckVsTrailerChart.setLabelsVisible(false); // Hide labels on pie to avoid clutter
        truckVsTrailerChart.setLegendSide(Side.BOTTOM);
        truckVsTrailerChart.setStyle("-fx-font-family: 'Arial'; -fx-font-size: 12px;");
        truckVsTrailerChart.setPrefWidth(600);
        truckVsTrailerChart.setMaxWidth(Double.MAX_VALUE);
        
        truckTrailerChartBox.getChildren().add(truckVsTrailerChart);
        
        // Top Maintenance Items
        topMaintenanceUnitsBox = createTopMaintenanceItemsBox();
        
        // Configure grid constraints for better layout
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);
        
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        col2.setHgrow(Priority.ALWAYS);
        
        chartsGrid.getColumnConstraints().addAll(col1, col2);
        
        // Add row constraints
        RowConstraints row1 = new RowConstraints();
        row1.setVgrow(Priority.ALWAYS);
        row1.setMinHeight(400);
        
        RowConstraints row2 = new RowConstraints();
        row2.setVgrow(Priority.ALWAYS);
        row2.setMinHeight(400);
        
        chartsGrid.getRowConstraints().addAll(row1, row2);
        
        // Add charts to grid
        chartsGrid.add(serviceTypeChartBox, 0, 0);
        chartsGrid.add(monthlyChartBox, 1, 0);
        chartsGrid.add(truckTrailerChartBox, 0, 1);
        chartsGrid.add(topMaintenanceUnitsBox, 1, 1);
        
        // Ensure charts fill their cells
        GridPane.setFillWidth(serviceTypeChartBox, true);
        GridPane.setFillHeight(serviceTypeChartBox, true);
        GridPane.setFillWidth(truckTrailerChartBox, true);
        GridPane.setFillHeight(truckTrailerChartBox, true);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createChartBox(String title) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        box.setMaxWidth(Double.MAX_VALUE);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#1976D2"));
        
        box.getChildren().add(titleLabel);
        return box;
    }
    
    private VBox createTopMaintenanceItemsBox() {
        VBox topItemsBox = new VBox(10);
        topItemsBox.setPadding(new Insets(15));
        topItemsBox.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label title = new Label("Top Maintenance Units");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#1976D2"));
        
        topMaintenanceUnitsList = new ListView<>();
        topMaintenanceUnitsList.setPrefHeight(320);
        topMaintenanceUnitsList.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-radius: 5px;");
        
        // Custom cell factory for better formatting
        topMaintenanceUnitsList.setCellFactory(lv -> new ListCell<String>() {
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
                        setStyle("-fx-background-color: #F8F9FA;");
                    }
                }
            }
        });
        
        topMaintenanceUnitsList.setPlaceholder(new Label("No maintenance data available"));
        
        topItemsBox.getChildren().addAll(title, topMaintenanceUnitsList);
        
        return topItemsBox;
    }
    
    private void loadData() {
        logger.info("Loading maintenance data");

        updateUnitNumberFilter();
        
        try {
            List<MaintenanceRecord> records = maintenanceDAO.findByDateRange(
                startDatePicker.getValue(), 
                endDatePicker.getValue()
            );
            
            allRecords.clear();
            allRecords.addAll(records);
            
            filteredRecords = new FilteredList<>(allRecords, p -> true);
            SortedList<MaintenanceRecord> sortedRecords = new SortedList<>(filteredRecords);
            sortedRecords.comparatorProperty().bind(maintenanceTable.comparatorProperty());
            maintenanceTable.setItems(sortedRecords);
            
            applyFilters();
            updateSummaryCards();
            updateCharts();
            
            // Apply styles after charts are updated with extra delay for legend
            Platform.runLater(() -> {
                applyChartStyles();
                // Re-apply colors after a delay to ensure everything is rendered
                Platform.runLater(() -> {
                    reapplyAllChartColors();
                });
            });
            
            logger.info("Loaded {} maintenance records", records.size());
            
        } catch (Exception e) {
            logger.error("Failed to load maintenance data", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load maintenance data: " + e.getMessage());
        }
    }
    
    private void applyFilters() {
        if (filteredRecords == null) return;
        
        filteredRecords.setPredicate(record -> {
            // Search text filter
            String searchText = searchField.getText().toLowerCase();
            if (!searchText.isEmpty()) {
                boolean matchesSearch = 
                    (record.getVehicle() != null && record.getVehicle().toLowerCase().contains(searchText)) ||
                    (record.getServiceType() != null && record.getServiceType().toLowerCase().contains(searchText)) ||
                    (record.getServiceProvider() != null && record.getServiceProvider().toLowerCase().contains(searchText)) ||
                    (record.getNotes() != null && record.getNotes().toLowerCase().contains(searchText));
                
                if (!matchesSearch) return false;
            }
            
            // Unit type filter
            String unitType = unitTypeFilter.getValue();
            if (!"All Units".equals(unitType)) {
                if ("Trucks Only".equals(unitType) && record.getVehicleType() != MaintenanceRecord.VehicleType.TRUCK) {
                    return false;
                }
                if ("Trailers Only".equals(unitType) && record.getVehicleType() != MaintenanceRecord.VehicleType.TRAILER) {
                    return false;
                }
            }
            
            // Unit number filter
            String selectedUnit = unitNumberFilter.getValue();
            if (selectedUnit != null && !selectedUnit.isEmpty() && !"All".equals(selectedUnit)) {
                if (!selectedUnit.equals(record.getVehicle())) {
                    return false;
                }
            }
            
            // Date range filter
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null && record.getDate().isBefore(startDate)) {
                return false;
            }
            if (endDate != null && record.getDate().isAfter(endDate)) {
                return false;
            }
            
            return true;
        });
        
        updateSummaryCards();
        updateCharts();
    }
    
    private void updateUnitNumberFilter() {
        String previousSelection = unitNumberFilter.getValue();
        unitNumberFilter.getItems().clear();
        unitNumberFilter.getItems().add("All");
        
        String unitType = unitTypeFilter.getValue();
        
        try {
            if ("All Units".equals(unitType) || "Trucks Only".equals(unitType)) {
                List<Truck> trucks = truckDAO.findAll();
                for (Truck truck : trucks) {
                    unitNumberFilter.getItems().add(truck.getNumber());
                }
            }
            
            if ("All Units".equals(unitType) || "Trailers Only".equals(unitType)) {
                List<Trailer> trailers = trailerDAO.findAll();
                for (Trailer trailer : trailers) {
                    unitNumberFilter.getItems().add(trailer.getTrailerNumber());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to update unit number filter", e);
        }
        
        if (unitNumberFilter.getItems().contains(previousSelection)) {
            unitNumberFilter.setValue(previousSelection);
        } else {
            unitNumberFilter.setValue("All");
        }
    }

    /** Called by other tabs when trucks or trailers are modified. */
    public void refreshUnitNumbers() {
        updateUnitNumberFilter();
    }
    
    private void updateSummaryCards() {
        List<MaintenanceRecord> visibleRecords = filteredRecords != null ? 
            new ArrayList<>(filteredRecords) : new ArrayList<>();
        
        // Total cost
        double totalCost = visibleRecords.stream()
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        totalCostLabel.setText(CURRENCY_FORMAT.format(totalCost));
        
        // Total records
        totalRecordsLabel.setText(String.valueOf(visibleRecords.size()));
        
        // Average cost
        double avgCost = visibleRecords.isEmpty() ? 0 : totalCost / visibleRecords.size();
        avgCostLabel.setText(CURRENCY_FORMAT.format(avgCost));
        
        // Truck costs
        double truckCost = visibleRecords.stream()
            .filter(r -> r.getVehicleType() == MaintenanceRecord.VehicleType.TRUCK)
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        truckCostLabel.setText(CURRENCY_FORMAT.format(truckCost));
        
        // Trailer costs
        double trailerCost = visibleRecords.stream()
            .filter(r -> r.getVehicleType() == MaintenanceRecord.VehicleType.TRAILER)
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        trailerCostLabel.setText(CURRENCY_FORMAT.format(trailerCost));
    }
    
    private void updateCharts() {
        List<MaintenanceRecord> visibleRecords = filteredRecords != null ? 
            new ArrayList<>(filteredRecords) : new ArrayList<>();
        
        // Update cost by service type chart
        Map<String, Double> costByType = visibleRecords.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getServiceType,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        // Sort service types for consistent ordering
        List<Map.Entry<String, Double>> sortedCostByType = costByType.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByKey())
            .collect(Collectors.toList());
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        List<String> orderedColors = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : sortedCostByType) {
            String type = entry.getKey();
            Double cost = entry.getValue();
            
            // Get or assign a consistent color for this service type
            String color = getColorForServiceType(type);
            orderedColors.add(color);
            
            PieChart.Data data = new PieChart.Data(type + " (" + CURRENCY_FORMAT.format(cost) + ")", cost);
            pieData.add(data);
        }
        
        costByTypeChart.setData(pieData);
        
        // Apply colors after chart is rendered
        Platform.runLater(() -> {
            // First, apply colors to pie slices
            for (int i = 0; i < pieData.size(); i++) {
                PieChart.Data data = pieData.get(i);
                if (data.getNode() != null) {
                    final String color = orderedColors.get(i);
                    data.getNode().setStyle("-fx-pie-color: " + color + ";");
                }
            }
            
            // Then apply matching colors to legend items with a slight delay to ensure legend is created
            Platform.runLater(() -> {
                applyLegendColorsRobust(costByTypeChart, orderedColors);
            });
        });
        
        // Update monthly expense chart
        Map<String, Double> monthlyExpenses = visibleRecords.stream()
            .collect(Collectors.groupingBy(
                record -> record.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        // Sort by date and format for display
        XYChart.Series<String, Number> monthlySeries = new XYChart.Series<>();
        monthlySeries.setName("Monthly Expenses");
        
        monthlyExpenses.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                // Format month for display
                String monthDisplay = LocalDate.parse(entry.getKey() + "-01").format(DateTimeFormatter.ofPattern("MMM yyyy"));
                XYChart.Data<String, Number> data = new XYChart.Data<>(monthDisplay, entry.getValue());
                monthlySeries.getData().add(data);
            });
        
        monthlyExpenseChart.getData().clear();
        monthlyExpenseChart.getData().add(monthlySeries);
        
        // Style the bars
        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> data : monthlySeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-bar-fill: #2196F3;");
                }
            }
        });
        
        // Update truck vs trailer chart
        double truckTotal = visibleRecords.stream()
            .filter(r -> r.getVehicleType() == MaintenanceRecord.VehicleType.TRUCK)
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
            
        double trailerTotal = visibleRecords.stream()
            .filter(r -> r.getVehicleType() == MaintenanceRecord.VehicleType.TRAILER)
            .mapToDouble(MaintenanceRecord::getCost)
            .sum();
        
        ObservableList<PieChart.Data> truckTrailerData = FXCollections.observableArrayList();
        if (truckTotal > 0) {
            PieChart.Data truckData = new PieChart.Data("Trucks (" + CURRENCY_FORMAT.format(truckTotal) + ")", truckTotal);
            truckTrailerData.add(truckData);
        }
        if (trailerTotal > 0) {
            PieChart.Data trailerData = new PieChart.Data("Trailers (" + CURRENCY_FORMAT.format(trailerTotal) + ")", trailerTotal);
            truckTrailerData.add(trailerData);
        }
        truckVsTrailerChart.setData(truckTrailerData);
        
        // Apply colors
        if (!truckTrailerData.isEmpty()) {
            Platform.runLater(() -> {
                String[] truckTrailerColors = {"#1976D2", "#388E3C"};
                for (int i = 0; i < truckTrailerData.size() && i < truckTrailerColors.length; i++) {
                    PieChart.Data data = truckTrailerData.get(i);
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-pie-color: " + truckTrailerColors[i] + ";");
                    }
                }
                // Apply matching colors to legend with delay
                Platform.runLater(() -> {
                    applyLegendColors(truckVsTrailerChart, truckTrailerColors);
                });
            });
        }
        
        // Update top maintenance units
        updateTopMaintenanceUnits(visibleRecords);
    }
    
    private void updateTopMaintenanceUnits(List<MaintenanceRecord> records) {
        // Calculate maintenance cost by unit
        Map<String, Double> unitCosts = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getVehicle,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        // Sort by cost descending and take top 10
        List<String> topUnits = unitCosts.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .map(entry -> {
                String unitType = records.stream()
                    .filter(r -> r.getVehicle().equals(entry.getKey()))
                    .findFirst()
                    .map(r -> r.getVehicleType() == MaintenanceRecord.VehicleType.TRUCK ? "Truck" : "Trailer")
                    .orElse("Unknown");
                    
                return String.format("%s %s - %s", 
                    unitType, 
                    entry.getKey(), 
                    CURRENCY_FORMAT.format(entry.getValue()));
            })
            .collect(Collectors.toList());
        
        // Update the ListView
        if (topMaintenanceUnitsList != null) {
            topMaintenanceUnitsList.getItems().clear();
            topMaintenanceUnitsList.getItems().addAll(topUnits);
        }
    }
    
    private void showAddExpenseDialog() {
        showMaintenanceDialog(null, true);
    }
    
    private void showEditExpenseDialog(MaintenanceRecord record) {
        showMaintenanceDialog(record, false);
    }
    
    private void showMaintenanceDialog(MaintenanceRecord record, boolean isAdd) {
        Dialog<MaintenanceRecord> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add Maintenance Expense" : "Edit Maintenance Expense");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Date
        DatePicker datePicker = new DatePicker(record != null ? record.getDate() : LocalDate.now());
        
        // Unit Type
        ComboBox<String> unitTypeCombo = new ComboBox<>();
        unitTypeCombo.getItems().addAll("Truck", "Trailer");
        unitTypeCombo.setValue(record != null && record.getVehicleType() == MaintenanceRecord.VehicleType.TRAILER ? 
                              "Trailer" : "Truck");
        
        // Unit Number
        ComboBox<String> unitNumberCombo = new ComboBox<>();
        updateUnitComboBox(unitNumberCombo, unitTypeCombo.getValue());
        
        unitTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateUnitComboBox(unitNumberCombo, newVal);
        });
        
        if (record != null) {
            unitNumberCombo.setValue(record.getVehicle());
        }
        
        // Service Type
        ComboBox<String> serviceTypeCombo = new ComboBox<>();
        serviceTypeCombo.getItems().addAll(
            "Oil Change", "Tire Rotation", "Tire Replacement", "Brake Service", "Brake Replacement",
            "Engine Repair", "Transmission", "Inspection", "DOT Inspection",
            "Preventive Maintenance", "Battery", "Electrical", 
            "Air Filter", "Fuel Filter", "Coolant", "Other"
        );
        serviceTypeCombo.setEditable(true);
        if (record != null) serviceTypeCombo.setValue(record.getServiceType());
        
        // Mileage
        TextField mileageField = new TextField();
        mileageField.setPromptText("Current mileage");
        if (record != null) mileageField.setText(String.valueOf(record.getMileage()));
        
        // Cost
        TextField costField = new TextField();
        costField.setPromptText("0.00");
        if (record != null) costField.setText(String.valueOf(record.getCost()));
        
        // Vendor
        TextField vendorField = new TextField();
        vendorField.setPromptText("Service provider name");
        if (record != null) vendorField.setText(record.getServiceProvider());
        
        // Invoice Number
        TextField invoiceField = new TextField();
        invoiceField.setPromptText("Invoice/Receipt number");
        if (record != null) invoiceField.setText(record.getReceiptNumber());
        
        // Notes
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(3);
        if (record != null) notesArea.setText(record.getNotes());
        
        // Layout
        int row = 0;
        grid.add(new Label("Date:"), 0, row);
        grid.add(datePicker, 1, row++);
        
        grid.add(new Label("Unit Type:"), 0, row);
        grid.add(unitTypeCombo, 1, row++);
        
        grid.add(new Label("Unit Number:"), 0, row);
        grid.add(unitNumberCombo, 1, row++);
        
        grid.add(new Label("Service Type:"), 0, row);
        grid.add(serviceTypeCombo, 1, row++);
        
        grid.add(new Label("Mileage:"), 0, row);
        grid.add(mileageField, 1, row++);
        
        grid.add(new Label("Cost:"), 0, row);
        grid.add(costField, 1, row++);
        
        grid.add(new Label("Vendor:"), 0, row);
        grid.add(vendorField, 1, row++);
        
        grid.add(new Label("Invoice #:"), 0, row);
        grid.add(invoiceField, 1, row++);
        
        grid.add(new Label("Notes:"), 0, row);
        grid.add(notesArea, 1, row++);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validation
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
            if (dialogButton == ButtonType.OK) {
                try {
                    MaintenanceRecord result = record != null ? record : new MaintenanceRecord();
                    
                    result.setDate(datePicker.getValue());
                    result.setVehicleType("Truck".equals(unitTypeCombo.getValue()) ? 
                                         MaintenanceRecord.VehicleType.TRUCK : 
                                         MaintenanceRecord.VehicleType.TRAILER);
                    result.setVehicle(unitNumberCombo.getValue());
                    result.setServiceType(serviceTypeCombo.getValue());
                    result.setMileage(mileageField.getText().isEmpty() ? 0 : 
                                     Integer.parseInt(mileageField.getText()));
                    result.setCost(costField.getText().isEmpty() ? 0 : 
                                  Double.parseDouble(costField.getText()));
                    result.setServiceProvider(vendorField.getText());
                    result.setReceiptNumber(invoiceField.getText());
                    result.setNotes(notesArea.getText());
                    result.setStatus("Completed");
                    
                    return result;
                } catch (Exception e) {
                    logger.error("Error creating maintenance record", e);
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(result -> {
            try {
                maintenanceDAO.save(result);
                loadData();
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         isAdd ? "Maintenance expense added successfully" : 
                                "Maintenance expense updated successfully");
            } catch (Exception e) {
                logger.error("Failed to save maintenance record", e);
                showAlert(Alert.AlertType.ERROR, "Error", 
                         "Failed to save maintenance record: " + e.getMessage());
            }
        });
    }
    
    private void updateUnitComboBox(ComboBox<String> combo, String unitType) {
        combo.getItems().clear();
        
        try {
            if ("Truck".equals(unitType)) {
                List<Truck> trucks = truckDAO.findAll();
                for (Truck truck : trucks) {
                    combo.getItems().add(truck.getNumber());
                }
            } else {
                List<Trailer> trailers = trailerDAO.findAll();
                for (Trailer trailer : trailers) {
                    combo.getItems().add(trailer.getTrailerNumber());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load units", e);
        }
    }
    
    private void editSelectedRecord() {
        MaintenanceRecord selected = maintenanceTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEditExpenseDialog(selected);
        } else {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a record to edit");
        }
    }
    
    private void deleteSelectedRecord() {
        MaintenanceRecord selected = maintenanceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a record to delete");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this maintenance record?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Deletion");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    maintenanceDAO.delete(selected.getId());
                    loadData();
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Maintenance record deleted successfully");
                } catch (Exception e) {
                    logger.error("Failed to delete maintenance record", e);
                    showAlert(Alert.AlertType.ERROR, "Error", 
                             "Failed to delete record: " + e.getMessage());
                }
            }
        });
    }
    
    private void showDocumentManager() {
        MaintenanceRecord selected = maintenanceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a maintenance record to manage documents");
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Maintenance Document Manager - " + selected.getVehicle() + " - " + 
                       selected.getDate().format(DATE_FORMAT));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(800);
        content.setPrefHeight(600);
        
        // Header with unit info
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Documents for " + selected.getVehicle());
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        Label dateLabel = new Label("Service Date: " + selected.getDate().format(DATE_FORMAT));
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
                new DocumentManagerSettingsDialog((Stage) maintenanceTable.getScene().getWindow());
            settingsDialog.showAndWait();
            // Refresh storage path after settings change
            docStoragePath = DocumentManagerConfig.getMaintenanceStoragePath();
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
    private VBox createDocumentListSection(MaintenanceRecord record) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📄 Documents");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        ListView<String> docListView = new ListView<>();
        docListView.setPrefHeight(300);
        updateDocumentList(docListView, record.getId());
        
        // Folder info
        Path folderPath = DocumentManagerConfig.getMaintenanceFolderPath(
            record.getVehicleType().toString(), record.getVehicle());
        
        Label folderInfo = new Label("Storage: " + folderPath.toString());
        folderInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        
        section.getChildren().addAll(sectionTitle, docListView, folderInfo);
        
        return section;
    }
    
    /**
     * Create document action buttons
     */
    private HBox createDocumentActionButtons(MaintenanceRecord record, VBox documentSection) {
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
            uploadDocumentForRecord(record.getId());
            updateDocumentList(docListView, record.getId());
        });
        
        viewBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                viewDocument(record.getId(), selectedDoc);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                         "Please select a document to view");
            }
        });
        
        printBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                printDocument(record.getId(), selectedDoc);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                         "Please select a document to print");
            }
        });
        
        deleteBtn.setOnAction(e -> {
            String selectedDoc = docListView.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                deleteDocument(record.getId(), selectedDoc);
                updateDocumentList(docListView, record.getId());
            } else {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                         "Please select a document to delete");
            }
        });
        
        openFolderBtn.setOnAction(e -> {
            Path folderPath = DocumentManagerConfig.getMaintenanceFolderPath(
                record.getVehicleType().toString(), record.getVehicle());
            DocumentManagerConfig.openFolder(folderPath);
        });
        
        buttonBox.getChildren().addAll(uploadBtn, viewBtn, printBtn, deleteBtn, openFolderBtn);
        
        return buttonBox;
    }
    
    private void uploadDocument() {
        MaintenanceRecord selected = maintenanceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a maintenance record to upload document");
            return;
        }
        
        uploadDocumentForRecord(selected.getId());
    }
    
    private void uploadDocumentForRecord(int recordId) {
        try {
            MaintenanceRecord record = maintenanceDAO.findById(recordId);
            if (record == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Maintenance record not found");
                return;
            }
            
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Upload Document for " + record.getVehicle());
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Document Files", "*.doc", "*.docx", "*.txt")
            );
            
            File selectedFile = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
            if (selectedFile != null) {
                // Create organized path structure
                Path documentPath = DocumentManagerConfig.createMaintenanceDocumentPath(
                    record.getVehicleType().toString(),
                    record.getVehicle(),
                    record.getReceiptNumber() != null ? record.getReceiptNumber() : "NO_INVOICE",
                    record.getServiceType(),
                    selectedFile.getName()
                );
                
                // Copy file to organized location
                Files.copy(selectedFile.toPath(), documentPath, StandardCopyOption.REPLACE_EXISTING);
                
                // Update record with document path
                record.setReceiptPath(documentPath.toString());
                maintenanceDAO.save(record);
                
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         "Document uploaded successfully for " + record.getVehicle() + 
                         "\nSaved to: " + documentPath.getParent());
                
            }
        } catch (Exception e) {
            logger.error("Failed to upload document", e);
            showAlert(Alert.AlertType.ERROR, "Upload Failed", 
                     "Failed to upload document: " + e.getMessage());
        }
    }
    
    private void updateDocumentList(ListView<String> listView, int recordId) {
        try {
            MaintenanceRecord record = maintenanceDAO.findById(recordId);
            if (record == null) {
                listView.setItems(FXCollections.observableArrayList());
                return;
            }
            
            // Use the new organized folder structure
            List<String> documents = DocumentManagerConfig.listMaintenanceDocuments(
                record.getVehicleType().toString(), record.getVehicle());
            
            listView.setItems(FXCollections.observableArrayList(documents));
            
        } catch (Exception e) {
            logger.error("Failed to list documents", e);
            listView.setItems(FXCollections.observableArrayList());
        }
    }
    
    private void viewDocument(int recordId, String document) {
        try {
            MaintenanceRecord record = maintenanceDAO.findById(recordId);
            if (record == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Maintenance record not found");
                return;
            }
            
            Path folderPath = DocumentManagerConfig.getMaintenanceFolderPath(
                record.getVehicleType().toString(), record.getVehicle());
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
    
    private void printDocument(int recordId, String document) {
        try {
            MaintenanceRecord record = maintenanceDAO.findById(recordId);
            if (record == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Maintenance record not found");
                return;
            }
            
            Path folderPath = DocumentManagerConfig.getMaintenanceFolderPath(
                record.getVehicleType().toString(), record.getVehicle());
            Path docPath = folderPath.resolve(document);
            
            File file = docPath.toFile();
            if (java.awt.Desktop.isDesktopSupported() && 
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.PRINT)) {
                java.awt.Desktop.getDesktop().print(file);
                logger.info("Printing document: {}", docPath);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Print Not Supported", 
                         "Printing is not supported. Please open the file first.");
                viewDocument(recordId, document);
            }
        } catch (Exception e) {
            logger.error("Failed to print document", e);
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to print document: " + e.getMessage());
        }
    }
    
    private void deleteDocument(int recordId, String document) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this document?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Deletion");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    MaintenanceRecord record = maintenanceDAO.findById(recordId);
                    if (record == null) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Maintenance record not found");
                        return;
                    }
                    
                    Path folderPath = DocumentManagerConfig.getMaintenanceFolderPath(
                        record.getVehicleType().toString(), record.getVehicle());
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
        MaintenanceRecord selected = maintenanceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", 
                     "Please select a maintenance record to print documents");
            return;
        }
        
        try {
            List<String> documents = DocumentManagerConfig.listMaintenanceDocuments(
                selected.getVehicleType().toString(), selected.getVehicle());
            
            if (documents.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Documents", 
                         "No documents found for this maintenance record");
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
        logger.info("Generating maintenance report");
        
        List<MaintenanceRecord> records = filteredRecords != null ? 
            new ArrayList<>(filteredRecords) : new ArrayList<>();
        
        if (records.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Data", 
                     "No maintenance records to report");
            return;
        }
        
        StringBuilder report = new StringBuilder();
        report.append("FLEET MAINTENANCE EXPENSE REPORT\n");
        report.append("================================\n\n");
        report.append("Report Date: ").append(LocalDate.now().format(DATE_FORMAT)).append("\n");
        report.append("Date Range: ").append(startDatePicker.getValue().format(DATE_FORMAT))
              .append(" to ").append(endDatePicker.getValue().format(DATE_FORMAT)).append("\n\n");
        
        // Summary
        double totalCost = records.stream().mapToDouble(MaintenanceRecord::getCost).sum();
        double avgCost = totalCost / records.size();
        
        report.append("SUMMARY\n");
        report.append("-------\n");
        report.append("Total Records: ").append(records.size()).append("\n");
        report.append("Total Cost: ").append(CURRENCY_FORMAT.format(totalCost)).append("\n");
        report.append("Average Cost: ").append(CURRENCY_FORMAT.format(avgCost)).append("\n\n");
        
        // By Unit Type
        Map<MaintenanceRecord.VehicleType, Double> costByType = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getVehicleType,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        report.append("BY UNIT TYPE\n");
        report.append("------------\n");
        costByType.forEach((type, cost) -> {
            report.append(type).append(": ").append(CURRENCY_FORMAT.format(cost)).append("\n");
        });
        report.append("\n");
        
        // By Service Type
        Map<String, Double> costByService = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getServiceType,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        report.append("BY SERVICE TYPE\n");
        report.append("---------------\n");
        costByService.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(entry -> {
                report.append(entry.getKey()).append(": ")
                      .append(CURRENCY_FORMAT.format(entry.getValue())).append("\n");
            });
        report.append("\n");
        
        // Top Units by Cost
        Map<String, Double> costByUnit = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getVehicle,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        report.append("TOP 10 UNITS BY MAINTENANCE COST\n");
        report.append("---------------------------------\n");
        costByUnit.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                report.append(entry.getKey()).append(": ")
                      .append(CURRENCY_FORMAT.format(entry.getValue())).append("\n");
            });
        
        // Show report in dialog
        TextArea reportArea = new TextArea(report.toString());
        reportArea.setEditable(false);
        reportArea.setFont(Font.font("Courier New", 12));
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Maintenance Report");
        dialog.getDialogPane().setContent(reportArea);
        dialog.getDialogPane().setPrefSize(600, 500);
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
        fileChooser.setInitialFileName("maintenance_report_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Write headers
                writer.println("Date,Unit Type,Unit Number,Service Type,Mileage,Cost,Vendor,Invoice #,Notes");
                
                // Write data
                List<MaintenanceRecord> records = filteredRecords != null ? 
                    new ArrayList<>(filteredRecords) : new ArrayList<>();
                    
                for (MaintenanceRecord record : records) {
                    writer.printf("%s,%s,%s,%s,%d,%.2f,%s,%s,%s%n",
                        record.getDate().format(DATE_FORMAT),
                        record.getVehicleType(),
                        record.getVehicle(),
                        record.getServiceType(),
                        record.getMileage(),
                        record.getCost(),
                        record.getServiceProvider() != null ? record.getServiceProvider() : "",
                        record.getReceiptNumber() != null ? record.getReceiptNumber() : "",
                        record.getNotes() != null ? record.getNotes().replace(",", ";") : ""
                    );
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                         "Maintenance data exported to CSV successfully!");
                         
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
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("maintenance_" + LocalDate.now() + ".xlsx");

        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Maintenance");
                String[] headers = {"Date","Unit Type","Unit Number","Service Type","Mileage","Cost","Vendor","Invoice #","Notes"};
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }

                List<MaintenanceRecord> records = filteredRecords != null ? new ArrayList<>(filteredRecords) : new ArrayList<>();
                int rowIdx = 1;
                for (MaintenanceRecord r : records) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(r.getDate().format(DATE_FORMAT));
                    row.createCell(1).setCellValue(r.getVehicleType().toString());
                    row.createCell(2).setCellValue(r.getVehicle());
                    row.createCell(3).setCellValue(r.getServiceType());
                    row.createCell(4).setCellValue(r.getMileage());
                    row.createCell(5).setCellValue(r.getCost());
                    row.createCell(6).setCellValue(r.getServiceProvider() != null ? r.getServiceProvider() : "");
                    row.createCell(7).setCellValue(r.getReceiptNumber() != null ? r.getReceiptNumber() : "");
                    row.createCell(8).setCellValue(r.getNotes() != null ? r.getNotes() : "");
                }

                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    wb.write(fos);
                }

                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Maintenance data exported to Excel successfully!");
            } catch (Exception e) {
                logger.error("Failed to export to Excel", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", "Failed to export data: " + e.getMessage());
            }
        }
    }

    private void exportToPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("maintenance_report_" + LocalDate.now() + ".pdf");

        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                // Get company name from config file
                String companyName = getCompanyNameFromConfig();
                
                // Get date range from filters
                LocalDate startDate = startDatePicker.getValue() != null ? startDatePicker.getValue() : LocalDate.now().minusMonths(1);
                LocalDate endDate = endDatePicker.getValue() != null ? endDatePicker.getValue() : LocalDate.now();
                
                // Use the new PDF exporter
                List<MaintenanceRecord> records = filteredRecords != null ? new ArrayList<>(filteredRecords) : new ArrayList<>();
                MaintenancePDFExporter exporter = new MaintenancePDFExporter(companyName, records, startDate, endDate);
                exporter.exportToPDF(file);
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Maintenance report exported to PDF successfully!");
            } catch (Exception e) {
                logger.error("Failed to export to PDF", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", "Failed to export data: " + e.getMessage());
            }
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public MaintenanceDAO getMaintenanceDAO() {
        return maintenanceDAO;
    }
    
    private void applyChartStyles() {
        // Style all chart labels to be black
        if (costByTypeChart != null) {
            costByTypeChart.lookupAll(".chart-pie-label").forEach(node -> {
                node.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
            });
            
            // Re-apply legend colors with the same consistent mapping
            ObservableList<PieChart.Data> data = costByTypeChart.getData();
            if (data != null && !data.isEmpty()) {
                List<String> orderedColors = new ArrayList<>();
                for (PieChart.Data pieData : data) {
                    String serviceName = pieData.getName().split(" \\(")[0]; // Extract service type name
                    orderedColors.add(getColorForServiceType(serviceName));
                }
                // Apply with delay to ensure legend is ready
                Platform.runLater(() -> {
                    applyLegendColorsRobust(costByTypeChart, orderedColors);
                });
            }
        }
        
        if (truckVsTrailerChart != null) {
            truckVsTrailerChart.lookupAll(".chart-pie-label").forEach(node -> {
                node.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
            });
            
            // Re-apply legend colors for truck vs trailer chart
            String[] truckTrailerColors = {"#1976D2", "#388E3C"};
            applyLegendColors(truckVsTrailerChart, truckTrailerColors);
        }
        
        if (monthlyExpenseChart != null) {
            monthlyExpenseChart.lookupAll(".axis-label").forEach(node -> {
                node.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
            });
            monthlyExpenseChart.lookupAll(".tick-label").forEach(node -> {
                node.setStyle("-fx-text-fill: black;");
            });
            monthlyExpenseChart.lookupAll(".chart-title").forEach(node -> {
                node.setStyle("-fx-text-fill: black; -fx-font-size: 16px; -fx-font-weight: bold;");
            });
        }
    }
    
    private void applyLegendColors(PieChart chart, String[] colors) {
        applyLegendColorsRobust(chart, Arrays.asList(colors));
    }
    
    private void applyLegendColorsRobust(PieChart chart, List<String> colors) {
        if (chart == null || colors == null || colors.isEmpty()) return;
        
        // Force legend to flow vertically
        configureLegendLayout(chart);
        
        // Get all legend items in order
        List<Node> legendItems = new ArrayList<>();
        for (Node node : chart.lookupAll(".chart-legend-item")) {
            legendItems.add(node);
        }
        
        // Apply colors to legend symbols
        for (int i = 0; i < legendItems.size() && i < colors.size(); i++) {
            Node legendItem = legendItems.get(i);
            String color = colors.get(i);
            
            // Find and style the symbol
            for (Node child : legendItem.lookupAll(".chart-legend-item-symbol")) {
                child.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3px;");
            }
            
            // Ensure text is black
            legendItem.setStyle("-fx-text-fill: black;");
        }
    }
    
    private void configureLegendLayout(PieChart chart) {
        // Find the legend and configure it for vertical layout
        for (Node node : chart.lookupAll(".chart-legend")) {
            if (node instanceof Region) {
                Region legend = (Region) node;
                // Force single column by setting a large preferred width
                legend.setPrefWidth(Region.USE_COMPUTED_SIZE);
                legend.setMaxWidth(Double.MAX_VALUE);
                
                // If it's a FlowPane, configure it
                if (legend instanceof javafx.scene.layout.FlowPane) {
                    javafx.scene.layout.FlowPane flowPane = (javafx.scene.layout.FlowPane) legend;
                    flowPane.setOrientation(Orientation.VERTICAL);
                    flowPane.setPrefWrapLength(0); // Force single column
                    flowPane.setVgap(5);
                    flowPane.setHgap(10);
                }
            }
        }
    }
    
    private String getColorForServiceType(String serviceType) {
        // Check if we already have a color assigned to this service type
        if (serviceTypeColorMap.containsKey(serviceType)) {
            return serviceTypeColorMap.get(serviceType);
        }
        
        // Assign a new color from the available colors
        String color = availableColors[nextColorIndex % availableColors.length];
        serviceTypeColorMap.put(serviceType, color);
        nextColorIndex++;
        
        return color;
    }
    
    private void initializeServiceTypeColors() {
        // Predefined colors for common service types
        serviceTypeColorMap.put("Oil Change", "#4CAF50");          // Green - routine maintenance
        serviceTypeColorMap.put("Tire Rotation", "#2196F3");       // Blue - routine maintenance
        serviceTypeColorMap.put("Tire Replacement", "#1976D2");    // Darker Blue - major tire work
        serviceTypeColorMap.put("Brake Service", "#F44336");       // Red - safety critical
        serviceTypeColorMap.put("Brake Replacement", "#D32F2F");   // Darker Red - major brake work
        serviceTypeColorMap.put("Engine Repair", "#FF9800");       // Orange - major repair
        serviceTypeColorMap.put("Transmission", "#FF5722");        // Deep Orange - major repair
        serviceTypeColorMap.put("Inspection", "#9C27B0");          // Purple - regulatory
        serviceTypeColorMap.put("DOT Inspection", "#7B1FA2");      // Darker Purple - regulatory
        serviceTypeColorMap.put("Preventive Maintenance", "#00BCD4"); // Cyan - preventive
        serviceTypeColorMap.put("Battery", "#795548");             // Brown - electrical
        serviceTypeColorMap.put("Electrical", "#5D4037");          // Darker Brown - electrical
        serviceTypeColorMap.put("Air Filter", "#607D8B");          // Blue Grey - filters
        serviceTypeColorMap.put("Fuel Filter", "#455A64");         // Darker Blue Grey - filters
        serviceTypeColorMap.put("Coolant", "#009688");             // Teal - fluids
        serviceTypeColorMap.put("Other", "#9E9E9E");               // Grey - miscellaneous
        
        // Start color index after predefined colors
        nextColorIndex = 0;
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
    
    private void reapplyAllChartColors() {
        // Reapply colors to expense by service type chart
        if (costByTypeChart != null && costByTypeChart.getData() != null) {
            List<String> orderedColors = new ArrayList<>();
            for (PieChart.Data data : costByTypeChart.getData()) {
                String serviceName = data.getName().split(" \\(")[0];
                String color = getColorForServiceType(serviceName);
                orderedColors.add(color);
                
                // Apply to pie slice
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-pie-color: " + color + ";");
                }
            }
            // Apply to legend
            applyLegendColorsRobust(costByTypeChart, orderedColors);
        }
        
        // Reapply colors to truck vs trailer chart
        if (truckVsTrailerChart != null && truckVsTrailerChart.getData() != null) {
            String[] truckTrailerColors = {"#1976D2", "#388E3C"};
            ObservableList<PieChart.Data> data = truckVsTrailerChart.getData();
            for (int i = 0; i < data.size() && i < truckTrailerColors.length; i++) {
                if (data.get(i).getNode() != null) {
                    data.get(i).getNode().setStyle("-fx-pie-color: " + truckTrailerColors[i] + ";");
                }
            }
            applyLegendColors(truckVsTrailerChart, truckTrailerColors);
        }
    }

    /**
     * Show import dialog for CSV/XLSX files
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Maintenance Records");
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
        progressDialog.setTitle("Importing Maintenance Records");
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
                
                return importMaintenanceFromFile(selectedFile);
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
            errorAlert.setHeaderText("Failed to import maintenance records");
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
     * Import maintenance records from CSV/XLSX file
     */
    private ImportResult importMaintenanceFromFile(File file) throws Exception {
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
            List<String> extraColumns = new ArrayList<>();
            Map<String, Integer> colMap = mapAndValidateHeaders(headers, extraColumns);
            
            int lineNumber = 1;
            Set<String> existingInvoiceNumbers = getExistingInvoiceNumbers();
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
                    if (values.length < 9) {
                        result.errors.add("Line " + lineNumber + ": Expected 9 columns, found " + values.length + " - skipped");
                        continue;
                    }
                    
                    // Validate required fields
                    if (values[0].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Date is required - skipped");
                        continue;
                    }
                    
                    if (values[1].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Unit Type is required - skipped");
                        continue;
                    }
                    
                    if (values[2].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Unit Number is required - skipped");
                        continue;
                    }
                    
                    if (values[3].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Service Type is required - skipped");
                        continue;
                    }
                    
                    if (values[5].trim().isEmpty()) {
                        result.errors.add("Line " + lineNumber + ": Cost is required - skipped");
                        continue;
                    }
                    
                    MaintenanceRecord record = createMaintenanceFromValues(values);
                    
                    // Check for duplicate invoice number
                    if (record.getReceiptNumber() != null && !record.getReceiptNumber().trim().isEmpty()) {
                        if (existingInvoiceNumbers.contains(record.getReceiptNumber().trim())) {
                            result.skipped++;
                            result.errors.add("Line " + lineNumber + ": Duplicate invoice number '" + 
                                           record.getReceiptNumber() + "' - skipped");
                            continue;
                        }
                        existingInvoiceNumbers.add(record.getReceiptNumber().trim());
                    }
                    
                    maintenanceDAO.save(record);
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
            List<String> extraColumns = new ArrayList<>();
            Map<String, Integer> colMap = mapAndValidateHeaders(headers, extraColumns);
            Set<String> existingInvoiceNumbers = getExistingInvoiceNumbers();
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                try {
                    String[] values = new String[headers.length];
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = row.getCell(i);
                        values[i] = cell != null ? cell.toString().trim() : "";
                    }
                    // Extract by header name using colMap
                    String[] mappedValues = new String[9];
                    mappedValues[0] = values[colMap.get("date")];
                    mappedValues[1] = values[colMap.get("unit type")];
                    mappedValues[2] = values[colMap.get("unit number")];
                    mappedValues[3] = values[colMap.get("service type")];
                    mappedValues[4] = values[colMap.get("mileage")];
                    mappedValues[5] = values[colMap.get("cost")];
                    mappedValues[6] = values[colMap.get("vendor")];
                    mappedValues[7] = values[colMap.get("invoice #")];
                    mappedValues[8] = values[colMap.get("notes")];
                    MaintenanceRecord record = createMaintenanceFromValues(mappedValues);
                    // Check for duplicate invoice number
                    if (record.getReceiptNumber() != null && !record.getReceiptNumber().trim().isEmpty()) {
                        if (existingInvoiceNumbers.contains(record.getReceiptNumber().trim())) {
                            result.skipped++;
                            result.errors.add("Row " + (rowNum + 1) + ": Duplicate invoice number '" + record.getReceiptNumber() + "' - skipped");
                            continue;
                        }
                        existingInvoiceNumbers.add(record.getReceiptNumber().trim());
                    }
                    maintenanceDAO.save(record);
                    result.imported++;
                    result.totalFound++;
                } catch (Exception e) {
                    result.errors.add("Row " + (rowNum + 1) + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Validate CSV/Excel headers (case-insensitive)
     */
    private void validateHeaders(String[] headers) throws Exception {
        String[] expectedHeaders = {"Date", "Unit Type", "Unit Number", "Service Type", 
                                  "Mileage", "Cost", "Vendor", "Invoice #", "Notes"};
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
        
        // Ensure we have exactly 9 values
        while (values.size() < 9) {
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
     * Create MaintenanceRecord from CSV/Excel values
     */
    private MaintenanceRecord createMaintenanceFromValues(String[] values) throws Exception {
        MaintenanceRecord record = new MaintenanceRecord();
        
        // Date
        try {
            String dateStr = values[0].trim();
            if (!dateStr.isEmpty()) {
                LocalDate date = parseDateFlexible(dateStr);
                record.setDate(date);
            } else {
                record.setDate(LocalDate.now());
            }
        } catch (Exception e) {
            throw new Exception("Invalid date format: " + values[0] + ". Expected MM/dd/yyyy, MM-dd-yyyy, or MM/dd/yy");
        }
        
        // Unit Type
        String unitType = values[1].trim();
        if (unitType.equalsIgnoreCase("truck")) {
            record.setVehicleType(MaintenanceRecord.VehicleType.TRUCK);
        } else if (unitType.equalsIgnoreCase("trailer")) {
            record.setVehicleType(MaintenanceRecord.VehicleType.TRAILER);
        } else {
            throw new Exception("Invalid unit type: " + unitType + ". Expected 'Truck' or 'Trailer'");
        }
        
        // Unit Number
        record.setVehicle(values[2].trim());
        
        // Service Type
        record.setServiceType(values[3].trim());
        
        // Mileage
        try {
            String mileageStr = values[4].trim();
            if (!mileageStr.isEmpty()) {
                // Remove commas from mileage
                mileageStr = mileageStr.replaceAll(",", "");
                record.setMileage(Integer.parseInt(mileageStr));
            } else {
                record.setMileage(0);
            }
        } catch (NumberFormatException e) {
            throw new Exception("Invalid mileage: " + values[4]);
        }
        
        // Cost
        try {
            String costStr = values[5].trim();
            // Remove currency symbols, commas, and spaces
            costStr = costStr.replaceAll("[$,€£¥\\s]", "");
            // Handle negative amounts in parentheses
            if (costStr.startsWith("(") && costStr.endsWith(")")) {
                costStr = "-" + costStr.substring(1, costStr.length() - 1);
            }
            record.setCost(Double.parseDouble(costStr));
        } catch (NumberFormatException e) {
            throw new Exception("Invalid cost: " + values[5]);
        }
        
        // Vendor
        record.setServiceProvider(values[6].trim());
        
        // Invoice Number
        record.setReceiptNumber(values[7].trim());
        
        // Notes
        record.setNotes(values[8].trim());
        
        // Set default status
        record.setStatus("Completed");
        
        return record;
    }
    
    /**
     * Parse date with highly flexible format support, including MM/dd/YYYY and M/dd/YYYY
     */
    private LocalDate parseDateFlexible(String dateStr) throws Exception {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new Exception("Date string is empty");
        }
        dateStr = dateStr.trim();
        // Try a wide range of patterns, including MM/dd/YYYY and M/dd/YYYY
        String[] patterns = {
            "MM/dd/yyyy", "M/dd/yyyy", "MM/d/yyyy", "M/d/yyyy", // 4-digit year
            "MM-dd-yyyy", "M-dd-yyyy", "MM-d-yyyy", "M-d-yyyy",
            "yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy",
            "d-MMM-yyyy", "d MMM yyyy", "dd MMM yyyy", "MMM d, yyyy",
            "yyyy.MM.dd", "dd.MM.yyyy", "yyyyMMdd", "ddMMyyyy",
            "d-MMM-yy", "d MMM yy", "dd MMM yy", "MMM d, yy",
            "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
            "dd MMMM yyyy", "d MMMM yyyy", "MMMM d, yyyy",
            "dd-MMM-yyyy", "dd/MMM/yyyy", "dd.MM.yyyy",
            "dd MMMM, yyyy", "d MMMM, yyyy", "MMMM dd, yyyy",
            "dd.MM.yy", "dd-MM-yy", "dd/MM/yy"
        };
        for (String pattern : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern).withLocale(Locale.ENGLISH);
                return LocalDate.parse(dateStr, fmt);
            } catch (Exception e) {
                // Try next
            }
        }
        // Try parsing as ISO
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            // Try next
        }
        // Try java.text.SimpleDateFormat as a last resort (for odd formats)
        try {
            java.text.SimpleDateFormat[] fmts = new java.text.SimpleDateFormat[] {
                new java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("d MMM yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
                new java.text.SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
            };
            for (java.text.SimpleDateFormat fmt : fmts) {
                try {
                    java.util.Date d = fmt.parse(dateStr);
                    return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                } catch (Exception e) {
                    // Try next
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        throw new Exception("Unable to parse date: '" + dateStr + "'. Please use a recognizable date format.");
    }
    
    /**
     * Get existing invoice numbers to prevent duplicates
     */
    private Set<String> getExistingInvoiceNumbers() {
        Set<String> invoiceNumbers = new HashSet<>();
        try {
            List<MaintenanceRecord> existingRecords = maintenanceDAO.findAll();
            for (MaintenanceRecord record : existingRecords) {
                if (record.getReceiptNumber() != null && !record.getReceiptNumber().trim().isEmpty()) {
                    invoiceNumbers.add(record.getReceiptNumber().trim());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get existing invoice numbers", e);
        }
        return invoiceNumbers;
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
     * Import result class
     */
    private static class ImportResult {
        int totalFound = 0;
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
    }
    
    /**
     * Validate and map CSV/Excel headers (case-insensitive, allow extra columns, warn for extras)
     * Returns a map of required column name (canonical) to index in the file.
     */
    private Map<String, Integer> mapAndValidateHeaders(String[] headers, List<String> extraColumns) throws Exception {
        String[] required = {"Date", "Unit Type", "Unit Number", "Service Type", "Mileage", "Cost", "Vendor", "Invoice #", "Notes"};
        Map<String, Integer> colMap = new HashMap<>();
        Set<String> requiredSet = new HashSet<>();
        for (String r : required) requiredSet.add(r.toLowerCase());
        Set<String> found = new HashSet<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim();
            String hLower = h.toLowerCase();
            if (requiredSet.contains(hLower)) {
                colMap.put(hLower, i);
                found.add(hLower);
            } else {
                extraColumns.add(h);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String r : required) {
            if (!found.contains(r.toLowerCase())) missing.add(r);
        }
        if (!missing.isEmpty()) {
            throw new Exception("Missing required columns: " + String.join(", ", missing));
        }
        return colMap;
    }
}