package com.company.payroll.maintenance;

import com.company.payroll.trucks.Truck;
import com.company.payroll.trucks.TruckDAO;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;
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
import javafx.util.StringConverter;
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
    
    // Data
    private ObservableList<MaintenanceRecord> allRecords = FXCollections.observableArrayList();
    private FilteredList<MaintenanceRecord> filteredRecords;
    
    // Document storage path
    private final String docStoragePath = "maintenance_documents";
    
    public MaintenanceTab() {
        setText("Maintenance");
        setClosable(false);
        
        logger.info("Initializing MaintenanceTab");
        
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
        
        Label titleLabel = new Label("Maintenance Expense Tracking");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("Track maintenance costs for your trucking fleet");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);
        
        VBox titleBox = new VBox(5, titleLabel, subtitleLabel);
        
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
        
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
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
        
        actionRow.getChildren().addAll(
            new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker,
            new Separator(Orientation.VERTICAL),
            refreshButton, addExpenseButton, documentManagerButton,
            generateReportButton, exportButton
        );
        
        controlPanel.getChildren().addAll(searchRow, new Separator(), actionRow);
        
        // Add listeners for filtering
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        unitTypeFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateUnitNumberFilter();
            applyFilters();
        });
        unitNumberFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
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
        
        tabPane.getTabs().addAll(historyTab, analyticsTab);
        
        return tabPane;
    }
    
    private VBox createHistorySection() {
        VBox historySection = new VBox(10);
        historySection.setPadding(new Insets(10));
        
        // Table controls
        HBox tableControls = new HBox(10);
        tableControls.setAlignment(Pos.CENTER_RIGHT);
        
        Button uploadDocButton = new Button("Upload Document");
        uploadDocButton.setStyle("-fx-base: #3498db;");
        uploadDocButton.setOnAction(e -> uploadDocument());
        
        Button printDocButton = new Button("Print Document");
        printDocButton.setStyle("-fx-base: #27ae60;");
        printDocButton.setOnAction(e -> printSelectedDocument());
        
        Button editButton = new Button("Edit");
        editButton.setStyle("-fx-base: #f39c12;");
        editButton.setOnAction(e -> editSelectedRecord());
        
        Button deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-base: #e74c3c;");
        deleteButton.setOnAction(e -> deleteSelectedRecord());
        
        tableControls.getChildren().addAll(uploadDocButton, printDocButton, editButton, deleteButton);
        
        // Maintenance Table
        maintenanceTable = new TableView<>();
        maintenanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
        
        maintenanceTable.getColumns().addAll(dateCol, unitTypeCol, unitNumberCol, serviceTypeCol,
                                            mileageCol, costCol, vendorCol, invoiceCol, notesCol);
        
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
        
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(20);
        chartsGrid.setVgap(20);
        
        // Cost by Service Type Chart
        VBox serviceTypeChartBox = new VBox(10);
        Label serviceTypeLabel = new Label("Expenses by Service Type");
        serviceTypeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        costByTypeChart = new PieChart();
        costByTypeChart.setTitle("Service Type Distribution");
        costByTypeChart.setPrefHeight(300);
        costByTypeChart.setAnimated(true);
        
        serviceTypeChartBox.getChildren().addAll(serviceTypeLabel, costByTypeChart);
        
        // Monthly Expense Chart
        VBox monthlyChartBox = new VBox(10);
        Label monthlyLabel = new Label("Monthly Maintenance Expenses");
        monthlyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Cost ($)");
        
        monthlyExpenseChart = new BarChart<>(xAxis, yAxis);
        monthlyExpenseChart.setTitle("Monthly Expense Trend");
        monthlyExpenseChart.setPrefHeight(300);
        monthlyExpenseChart.setAnimated(true);
        monthlyExpenseChart.setLegendVisible(false);
        
        monthlyChartBox.getChildren().addAll(monthlyLabel, monthlyExpenseChart);
        
        // Truck vs Trailer Chart
        VBox truckTrailerChartBox = new VBox(10);
        Label truckTrailerLabel = new Label("Truck vs Trailer Expenses");
        truckTrailerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        truckVsTrailerChart = new PieChart();
        truckVsTrailerChart.setTitle("Cost Distribution");
        truckVsTrailerChart.setPrefHeight(300);
        truckVsTrailerChart.setAnimated(true);
        
        truckTrailerChartBox.getChildren().addAll(truckTrailerLabel, truckVsTrailerChart);
        
        // Top Maintenance Items
        VBox topItemsBox = createTopMaintenanceItemsBox();
        
        chartsGrid.add(serviceTypeChartBox, 0, 0);
        chartsGrid.add(monthlyChartBox, 1, 0);
        chartsGrid.add(truckTrailerChartBox, 0, 1);
        chartsGrid.add(topItemsBox, 1, 1);
        
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().addAll(col, col);
        
        analyticsSection.getChildren().add(chartsGrid);
        
        return analyticsSection;
    }
    
    private VBox createTopMaintenanceItemsBox() {
        VBox topItemsBox = new VBox(10);
        topItemsBox.setPadding(new Insets(15));
        topItemsBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label title = new Label("Top Maintenance Units");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        ListView<String> topItemsList = new ListView<>();
        topItemsList.setPrefHeight(250);
        
        // This will be populated with data
        topItemsList.setPlaceholder(new Label("No data available"));
        
        topItemsBox.getChildren().addAll(title, topItemsList);
        
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
        
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        costByType.forEach((type, cost) -> {
            pieData.add(new PieChart.Data(type + " (" + CURRENCY_FORMAT.format(cost) + ")", cost));
        });
        costByTypeChart.setData(pieData);
        
        // Update monthly expense chart
        Map<String, Double> monthlyExpenses = visibleRecords.stream()
            .collect(Collectors.groupingBy(
                record -> record.getDate().format(DateTimeFormatter.ofPattern("MMM yyyy")),
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        XYChart.Series<String, Number> monthlySeries = new XYChart.Series<>();
        monthlyExpenses.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                monthlySeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            });
        
        monthlyExpenseChart.getData().clear();
        monthlyExpenseChart.getData().add(monthlySeries);
        
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
            truckTrailerData.add(new PieChart.Data("Trucks (" + CURRENCY_FORMAT.format(truckTotal) + ")", truckTotal));
        }
        if (trailerTotal > 0) {
            truckTrailerData.add(new PieChart.Data("Trailers (" + CURRENCY_FORMAT.format(trailerTotal) + ")", trailerTotal));
        }
        truckVsTrailerChart.setData(truckTrailerData);
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
            "Oil Change", "Tire Replacement", "Tire Rotation", "Brake Service",
            "Engine Repair", "Transmission Service", "Coolant Service", 
            "Battery Replacement", "Inspection", "Registration", "Permits",
            "Body Work", "Electrical", "HVAC", "Other"
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
        dialog.setTitle("Document Manager - " + selected.getVehicle() + " - " + 
                       selected.getDate().format(DATE_FORMAT));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        ListView<String> docListView = new ListView<>();
        updateDocumentList(docListView, selected.getId());
        
        Button uploadBtn = new Button("Upload");
        Button viewBtn = new Button("View");
        Button printBtn = new Button("Print");
        Button deleteBtn = new Button("Delete");
        
        uploadBtn.setOnAction(e -> {
            uploadDocumentForRecord(selected.getId());
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
                               new Label("Documents for this maintenance record"), 
                               docListView, 
                               buttonBox);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setPrefHeight(400);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
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
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Document to Upload");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        File selectedFile = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (selectedFile != null) {
            try {
                Path recordDir = Paths.get(docStoragePath, String.valueOf(recordId));
                Files.createDirectories(recordDir);
                
                String timestamp = String.valueOf(System.currentTimeMillis());
                String extension = selectedFile.getName().substring(
                        selectedFile.getName().lastIndexOf('.'));
                String newFileName = "maintenance_" + timestamp + extension;
                
                Path destPath = recordDir.resolve(newFileName);
                Files.copy(selectedFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                
                logger.info("Uploaded document for record {}: {}", recordId, newFileName);
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         "Document uploaded successfully");
                
            } catch (Exception e) {
                logger.error("Failed to upload document", e);
                showAlert(Alert.AlertType.ERROR, "Error", 
                         "Failed to upload document: " + e.getMessage());
            }
        }
    }
    
    private void updateDocumentList(ListView<String> listView, int recordId) {
        try {
            Path recordDir = Paths.get(docStoragePath, String.valueOf(recordId));
            if (Files.exists(recordDir)) {
                List<String> files = Files.list(recordDir)
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
    
    private void viewDocument(int recordId, String document) {
        try {
            Path docPath = Paths.get(docStoragePath, String.valueOf(recordId), document);
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
            Path docPath = Paths.get(docStoragePath, String.valueOf(recordId), document);
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
                    Path docPath = Paths.get(docStoragePath, String.valueOf(recordId), document);
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
            Path recordDir = Paths.get(docStoragePath, String.valueOf(selected.getId()));
            if (!Files.exists(recordDir) || !Files.isDirectory(recordDir)) {
                showAlert(Alert.AlertType.INFORMATION, "No Documents", 
                         "No documents found for this maintenance record");
                return;
            }
            
            List<String> documents = Files.list(recordDir)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
            
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
        fileChooser.setInitialFileName("maintenance_" + LocalDate.now() + ".pdf");

        File file = fileChooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER);
                doc.addPage(page);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    org.apache.pdfbox.pdmodel.font.PDType1Font font = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
                    cs.setFont(font, 12);
                    float leading = 14f;
                    cs.newLineAtOffset(50, page.getMediaBox().getHeight() - 50);
                    cs.showText("Date | Unit Type | Unit Number | Service Type | Cost");
                    cs.newLineAtOffset(0, -leading);

                    List<MaintenanceRecord> records = filteredRecords != null ? new ArrayList<>(filteredRecords) : new ArrayList<>();
                    for (MaintenanceRecord r : records) {
                        String line = String.format("%s  %s  %s  %s  %.2f",
                                r.getDate().format(DATE_FORMAT),
                                r.getVehicleType(),
                                r.getVehicle(),
                                r.getServiceType(),
                                r.getCost());
                        cs.showText(line);
                        cs.newLineAtOffset(0, -leading);
                    }
                    cs.endText();
                }
                doc.save(file);
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Maintenance data exported to PDF successfully!");
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
}