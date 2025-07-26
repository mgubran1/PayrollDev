package com.company.payroll.drivergrid;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.loads.LoadLocation;
import com.company.payroll.loads.LoadsTab;
import com.company.payroll.drivergrid.LoadStatusUtil;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import com.company.payroll.util.WindowAware;

public class DriverGridTab extends Tab implements WindowAware {
    private final BorderPane mainLayout = new BorderPane();
    private final VBox leftPanel = new VBox(15);
    private final GridPane weekGrid = new GridPane();
    private final HBox weekHeaderRow = new HBox();
    private final ScrollPane weekGridScrollPane = new ScrollPane(weekGrid);
    private final TextField searchField = new TextField();
    private final DatePicker weekFilterPicker = new DatePicker(LocalDate.now());
    private final Button refreshBtn = new Button("🔄 Refresh");
    private final Label statusMsg = new Label("");
    private LocalDate weekStart = LocalDate.now().with(DayOfWeek.SUNDAY);
    private final ObservableList<LoadRow> allRows = FXCollections.observableArrayList();
    private final ObservableList<LoadRow> filteredRows = FXCollections.observableArrayList();
    private final Map<Employee, List<LoadConflict>> conflictMap = new HashMap<>();
    
    // Loads tab integration
    private LoadsTab loadsTab;
    

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter AMERICAN_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a").withLocale(Locale.US);
    private static final DateTimeFormatter AMERICAN_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd").withLocale(Locale.US);

    public DriverGridTab() {
        super("Driver Grid");
        setClosable(false);
        weekStart = LocalDate.now().with(DayOfWeek.SUNDAY);
        setupUI();
        setContent(mainLayout);
        // Always select today on tab creation
        weekFilterPicker.setValue(LocalDate.now());
        refresh();
        // Listen for tab selection to always show current week
        this.setOnSelectionChanged(e -> {
            if (this.isSelected()) {
                weekStart = LocalDate.now().with(DayOfWeek.SUNDAY);
                weekFilterPicker.setValue(LocalDate.now());
                refresh();
            }
        });
    }
    
    public void setLoadsTab(LoadsTab loadsTab) {
        this.loadsTab = loadsTab;
        // Listen for load data changes from Loads tab
        if (loadsTab != null) {
            loadsTab.addLoadDataChangeListener(this::refresh);
        }
    }

    private void setupUI() {
        leftPanel.setPadding(new Insets(20));
        leftPanel.setStyle("-fx-background-color: #f8fafc;");
        
        // Create main header with title and week navigation
        HBox mainHeader = new HBox(20);
        mainHeader.setAlignment(Pos.CENTER_LEFT);
        mainHeader.setPadding(new Insets(0, 0, 20, 0));
        mainHeader.getStyleClass().add("driver-grid-main-header");
        
        // Left side: Title and week navigation
        VBox leftSide = new VBox(10);
        leftSide.setAlignment(Pos.CENTER_LEFT);
        leftSide.getStyleClass().add("driver-grid-left-side");
        
        // Title
        Label titleLabel = new Label("Driver Scheduling Grid");
        titleLabel.getStyleClass().add("driver-grid-title");
        
        // Week navigation
        HBox weekControls = new HBox(10);
        weekControls.setAlignment(Pos.CENTER_LEFT);
        weekControls.getStyleClass().add("driver-grid-week-nav");
        Button prevWeekBtn = new Button("◀ Previous");
        Button todayBtn = new Button("Today");
        Button nextWeekBtn = new Button("Next ▶");
        Label weekLabel = new Label();
        weekLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        weekLabel.setTextFill(Color.rgb(31, 41, 55));
        prevWeekBtn.setOnAction(e -> { weekStart = weekStart.minusWeeks(1); weekFilterPicker.setValue(weekStart); refresh(); });
        nextWeekBtn.setOnAction(e -> { weekStart = weekStart.plusWeeks(1); weekFilterPicker.setValue(weekStart); refresh(); });
        todayBtn.setOnAction(e -> { weekStart = LocalDate.now().with(DayOfWeek.SUNDAY); weekFilterPicker.setValue(LocalDate.now()); refresh(); });
        weekFilterPicker.valueProperty().addListener((obs, oldV, newV) -> { if (newV != null) { weekStart = newV.with(DayOfWeek.SUNDAY); refresh(); }});
        weekControls.getChildren().addAll(prevWeekBtn, todayBtn, weekFilterPicker, nextWeekBtn);
        
        leftSide.getChildren().addAll(titleLabel, weekControls);
        
        // Right side: Action buttons and summary statistics
        VBox rightSide = new VBox(15);
        rightSide.setAlignment(Pos.TOP_RIGHT);
        rightSide.getStyleClass().add("driver-grid-right-side");
        
        // Action buttons row
        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER_RIGHT);
        actionButtons.getStyleClass().add("driver-grid-action-buttons");
        Button scheduleLoadBtn = new Button("📅 Schedule Load");
        Button assignDriverBtn = new Button("👤 Assign Driver");
        Button exportScheduleBtn = new Button("📊 Export Schedule");
        Button printViewBtn = new Button("🖨️ Print View");
        Button refreshBtn = new Button("🔄 Refresh");
        CheckBox autoRefreshCheck = new CheckBox("Auto-refresh");
        
        // Style action buttons
        scheduleLoadBtn.getStyleClass().add("driver-grid-action-button");
        assignDriverBtn.getStyleClass().add("driver-grid-action-button");
        exportScheduleBtn.getStyleClass().add("driver-grid-action-button");
        printViewBtn.getStyleClass().add("driver-grid-action-button");
        refreshBtn.getStyleClass().add("driver-grid-action-button");
        autoRefreshCheck.getStyleClass().add("driver-grid-auto-refresh");
        
        actionButtons.getChildren().addAll(scheduleLoadBtn, assignDriverBtn, exportScheduleBtn, printViewBtn, refreshBtn, autoRefreshCheck);
        
        // Summary statistics boxes row
        HBox summaryBoxes = new HBox(15);
        summaryBoxes.setAlignment(Pos.CENTER_RIGHT);
        summaryBoxes.getStyleClass().add("driver-grid-summary-container");
        
        totalLoadsBox = createSummaryBox("Total Loads", "0");
        activeDriversBox = createSummaryBox("Active Drivers", "0");
        conflictsBox = createSummaryBox("Conflicts", "0");
        unassignedBox = createSummaryBox("Unassigned", "0");
        
        summaryBoxes.getChildren().addAll(totalLoadsBox, activeDriversBox, conflictsBox, unassignedBox);
        
        // Multi-stop indicator
        Label multiStopIndicator = new Label("- Multi-stop load indicator");
        multiStopIndicator.getStyleClass().add("driver-grid-multi-stop-indicator");
        
        rightSide.getChildren().addAll(actionButtons, summaryBoxes, multiStopIndicator);
        
        // Add left and right sides to main header
        mainHeader.getChildren().addAll(leftSide, rightSide);
        HBox.setHgrow(leftSide, Priority.ALWAYS);
        
        leftPanel.getChildren().add(mainHeader);
        
        // Search and filter section
        HBox searchFilterSection = new HBox(15);
        searchFilterSection.setAlignment(Pos.CENTER_LEFT);
        searchFilterSection.setPadding(new Insets(0, 0, 20, 0));
        searchFilterSection.getStyleClass().add("driver-grid-search-section");
        
        Label searchLabel = new Label("Search:");
        searchField.setPromptText("Search by load #, driver, truck");
        searchField.setMinWidth(200);
        searchField.getStyleClass().add("driver-grid-search-field");
        searchField.textProperty().addListener((obs, oldV, newV) -> applyAdvancedFilter());
        
        ComboBox<String> driverFilter = new ComboBox<>();
        driverFilter.setPromptText("Driver: All Driv...");
        driverFilter.setMinWidth(150);
        driverFilter.getStyleClass().add("driver-grid-filter-combo");
        
        ComboBox<String> customerFilter = new ComboBox<>();
        customerFilter.setPromptText("Customer: All Cust...");
        customerFilter.setMinWidth(150);
        customerFilter.getStyleClass().add("driver-grid-filter-combo");
        
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.setPromptText("Status: All Stat...");
        statusFilter.setMinWidth(150);
        statusFilter.getStyleClass().add("driver-grid-filter-combo");
        
        CheckBox showConflictsOnly = new CheckBox("Show Conflicts Only");
        showConflictsOnly.getStyleClass().add("driver-grid-checkbox");
        CheckBox showUnassigned = new CheckBox("Show Unassigned");
        showUnassigned.getStyleClass().add("driver-grid-checkbox");
        
        Button clearFiltersBtn = new Button("Clear Filters");
        clearFiltersBtn.getStyleClass().add("driver-grid-action-button");
        
        searchFilterSection.getChildren().addAll(searchLabel, searchField, driverFilter, customerFilter, statusFilter, showConflictsOnly, showUnassigned, clearFiltersBtn);
        leftPanel.getChildren().add(searchFilterSection);
        
        leftPanel.getChildren().addAll(createLegend());
        weekHeaderRow.setSpacing(1);
        weekHeaderRow.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 0 1 1 0;");
        weekHeaderRow.setMinHeight(50);
        weekHeaderRow.setMaxHeight(50);
        leftPanel.getChildren().add(weekHeaderRow);
        weekGrid.setHgap(1);
        weekGrid.setVgap(1);
        weekGrid.setStyle("-fx-background-color: #e5e7eb; -fx-border-color: #d1d5db; -fx-border-width: 2px; -fx-border-radius: 8;");
        weekGrid.setPadding(new Insets(2));
        weekGridScrollPane.setFitToWidth(false);
        weekGridScrollPane.setPannable(true);
        weekGridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        weekGridScrollPane.setPrefHeight(480);
        weekGridScrollPane.setStyle("-fx-background: #f8fafc; -fx-border-color: #d1d5db; -fx-border-radius: 8;");
        weekGridScrollPane.hvalueProperty().addListener((obs, oldV, newV) -> {
            double hVal = newV.doubleValue();
            weekHeaderRow.setTranslateX(-hVal * (weekGrid.getWidth() - weekGridScrollPane.getViewportBounds().getWidth()));
        });
        weekGridScrollPane.viewportBoundsProperty().addListener((obs, o, n) -> adjustColumnWidths());
        leftPanel.getChildren().add(weekGridScrollPane);
        refreshBtn.setOnAction(e -> refresh());
        mainLayout.setLeft(leftPanel);
        mainLayout.setStyle("-fx-background-color: #f8fafc;");
    }
    
    private VBox createSummaryBox(String title, String value) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("driver-grid-summary-box");
        
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("driver-grid-summary-value");
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("driver-grid-summary-title");
        
        box.getChildren().addAll(valueLabel, titleLabel);
        return box;
    }

    public void refresh() {
        // Do not reset weekStart here, so navigation works
        setupData();
        detectConflicts();
        applyAdvancedFilter();
    }

    private void setupData() {
        allRows.clear();
        EmployeeDAO employeeDAO = new EmployeeDAO();
        LoadDAO loadDAO = new LoadDAO();
        List<Employee> allEmployees = employeeDAO.getAll();
        for (Employee driver : allEmployees) {
            if (!driver.isDriver()) continue;
            List<Load> loads = loadDAO.getByDriver(driver.getId());
            for (Load load : loads) {
                allRows.add(new LoadRow(driver, load));
            }
        }
    }

    private void detectConflicts() {
        conflictMap.clear();
        Map<Employee, List<LoadRow>> driverLoads = new HashMap<>();
        
        // Group loads by driver
        for (LoadRow row : allRows) {
            driverLoads.computeIfAbsent(row.driver, k -> new ArrayList<>()).add(row);
        }
        
        // Detect conflicts for each driver
        for (Map.Entry<Employee, List<LoadRow>> entry : driverLoads.entrySet()) {
            Employee driver = entry.getKey();
            List<LoadRow> loads = entry.getValue();
            List<LoadConflict> conflicts = new ArrayList<>();
            
            for (int i = 0; i < loads.size(); i++) {
                for (int j = i + 1; j < loads.size(); j++) {
                    LoadConflict conflict = checkTimeConflict(loads.get(i).load, loads.get(j).load);
                    if (conflict != null) {
                        conflicts.add(conflict);
                    }
                }
            }
            
            if (!conflicts.isEmpty()) {
                conflictMap.put(driver, conflicts);
            }
        }
    }

    private LoadConflict checkTimeConflict(Load load1, Load load2) {
        LocalDate pickup1 = load1.getPickUpDate();
        LocalDate delivery1 = load1.getDeliveryDate();
        LocalDate pickup2 = load2.getPickUpDate();
        LocalDate delivery2 = load2.getDeliveryDate();
        
        if (pickup1 == null || delivery1 == null || pickup2 == null || delivery2 == null) {
            return null;
        }
        
        LocalTime pickupTime1 = load1.getPickUpTime();
        LocalTime deliveryTime1 = load1.getDeliveryTime();
        LocalTime pickupTime2 = load2.getPickUpTime();
        LocalTime deliveryTime2 = load2.getDeliveryTime();
        
        // If times are not set, treat as conflict
        if (pickupTime1 == null || deliveryTime1 == null || pickupTime2 == null || deliveryTime2 == null) {
            return new LoadConflict(load1, load2, "Time not specified - potential conflict");
        }
        
        ZonedDateTime start1 = ZonedDateTime.of(pickup1, pickupTime1, DEFAULT_ZONE);
        ZonedDateTime end1 = ZonedDateTime.of(delivery1, deliveryTime1, DEFAULT_ZONE);
        ZonedDateTime start2 = ZonedDateTime.of(pickup2, pickupTime2, DEFAULT_ZONE);
        ZonedDateTime end2 = ZonedDateTime.of(delivery2, deliveryTime2, DEFAULT_ZONE);
        
        // Allow back-to-back: end1 <= start2 or end2 <= start1 is OK
        if (!end1.isAfter(start2) || !end2.isAfter(start1)) {
            return null;
        }
        
        // Otherwise, overlap
        if (end1.isAfter(start2) && end2.isAfter(start1)) {
            return new LoadConflict(load1, load2, "Time overlap conflict");
        }
        
        return null;
    }

    private void applyAdvancedFilter() {
        System.out.println("[DEBUG] Total loads loaded: " + allRows.size());
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        LocalDate weekBase = weekFilterPicker.getValue() != null ? weekFilterPicker.getValue().with(DayOfWeek.SUNDAY) : weekStart;
        LocalDate weekEnd = weekBase.plusDays(6);
        Predicate<LoadRow> pred = row -> {
            boolean matchesSearch =
                row.driver.getName().toLowerCase().contains(search) ||
                (row.driver.getTruckUnit() != null && row.driver.getTruckUnit().toLowerCase().contains(search)) ||
                row.load.getLoadNumber().toLowerCase().contains(search);
            LocalDate pickup = row.load.getPickUpDate();
            LocalDate delivery = row.load.getDeliveryDate();
            boolean matchesDate = pickup != null && delivery != null && !delivery.isBefore(weekBase) && !pickup.isAfter(weekEnd);
            return matchesSearch && matchesDate;
        };
        List<LoadRow> filtered = allRows.stream().filter(pred).toList();
        filteredRows.setAll(filtered);
        updateWeeklyGrid();
        updateSummaryCard();
    }

    private void updateWeeklyGrid() {
        weekGrid.getChildren().clear();
        weekGrid.getColumnConstraints().clear();
        weekGrid.getRowConstraints().clear();
        // Columns: 0 = driver info, 1-7 = days
        ColumnConstraints driverCol = new ColumnConstraints(180);
        driverCol.setHgrow(Priority.NEVER);
        weekGrid.getColumnConstraints().add(driverCol);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints(140);
            dayCol.setHgrow(Priority.ALWAYS);
            weekGrid.getColumnConstraints().add(dayCol);
        }
        buildHeaderRow();
        List<Employee> drivers = filteredRows.stream().map(r -> r.driver).distinct().toList();
        for (int i = 0; i < drivers.size(); i++) {
            Employee driver = drivers.get(i);
            buildDriverRow(driver, i + 1, i);
        }

        adjustColumnWidths();
    }

    private void adjustColumnWidths() {
        double viewportWidth = weekGridScrollPane.getViewportBounds().getWidth();
        if (viewportWidth == 0) return;
        double available = viewportWidth - 180; // subtract driver column
        double dayWidth = Math.max(140, available / 7);
        ObservableList<ColumnConstraints> cols = weekGrid.getColumnConstraints();
        if (cols.size() == 8) {
            for (int i = 1; i < cols.size(); i++) {
                ColumnConstraints cc = cols.get(i);
                cc.setPrefWidth(dayWidth);
                cc.setMinWidth(dayWidth);
            }
        }
        weekGrid.getChildren().forEach(node -> {
            Integer col = GridPane.getColumnIndex(node);
            if (col != null && col > 0 && node instanceof Region region) {
                region.setMinWidth(dayWidth);
                region.setPrefWidth(dayWidth);
            }
        });
    }

    private void buildHeaderRow() {
        weekHeaderRow.getChildren().clear();
        VBox cornerCell = new VBox();
        cornerCell.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 0 1 1 0;");
        cornerCell.setMinHeight(50);
        cornerCell.setMinWidth(180);
        weekHeaderRow.getChildren().add(cornerCell);
        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d");
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            VBox dayHeader = new VBox(2);
            dayHeader.setAlignment(Pos.CENTER);
            dayHeader.setPadding(new Insets(8));
            dayHeader.setMinWidth(140);
            dayHeader.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 0 1 1 0;");
            Label dayName = new Label(dayNames[d]);
            dayName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            dayName.setTextFill(Color.rgb(55, 65, 81));
            Label dateLabel = new Label(day.format(dateFormatter));
            dateLabel.setFont(Font.font("Segoe UI", 10));
            dateLabel.setTextFill(Color.rgb(107, 114, 128));
            if (day.equals(LocalDate.now())) {
                dayHeader.setStyle(dayHeader.getStyle() + "-fx-background-color: #dbeafe; -fx-border-color: #3b82f6; -fx-border-width: 0 1 1 2;");
                dayName.setTextFill(Color.rgb(30, 64, 175));
                dateLabel.setTextFill(Color.rgb(59, 130, 246));
            }
            dayHeader.getChildren().addAll(dayName, dateLabel);
            weekHeaderRow.getChildren().add(dayHeader);
        }
    }

    private void buildDriverRow(Employee driver, int rowIndex, int zebraIndex) {
        VBox driverCell = new VBox(8);
        driverCell.setPadding(new Insets(12));
        driverCell.setStyle((zebraIndex % 2 == 1 ? "-fx-background-color: #f3f4f6;" : "-fx-background-color: white;") + "-fx-border-color: #e5e7eb; -fx-border-width: 0 1 1 0;");
        driverCell.setMinHeight(100);
        driverCell.setMaxHeight(100);
        driverCell.setPrefHeight(100);
        Label nameLabel = new Label(driver.getName());
        nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        nameLabel.setTextFill(Color.rgb(31, 41, 55));
        nameLabel.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(nameLabel, new Tooltip("Click to view driver's loads"));
        nameLabel.setOnMouseClicked(e -> {/* Show load details dialog if needed */});
        nameLabel.setWrapText(true);
        Label truckLabel = new Label("🚛 " + driver.getTruckUnit());
        truckLabel.setFont(Font.font("Segoe UI", 11));
        truckLabel.setTextFill(Color.rgb(107, 114, 128));
        truckLabel.setWrapText(true);
        HBox statusBox = new HBox(6);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Circle statusDot = new Circle(4);
        statusDot.setFill(Color.web("#10b981"));
        Label statusLabel = new Label("Active");
        statusLabel.setFont(Font.font("Segoe UI", 10));
        statusLabel.setTextFill(Color.rgb(107, 114, 128));
        statusBox.getChildren().addAll(statusDot, statusLabel);
        driverCell.getChildren().addAll(nameLabel, truckLabel, statusBox);
        weekGrid.add(driverCell, 0, rowIndex);
        StackPane[] dayCells = new StackPane[7];
        for (int d = 0; d < 7; d++) {
            final int dayIndex = d;
            StackPane dayCell = new StackPane();
            dayCell.setMinHeight(100);
            dayCell.setMaxHeight(100);
            dayCell.setPrefHeight(100);
            dayCell.setStyle((zebraIndex % 2 == 1 ? "-fx-background-color: #f3f4f6;" : "-fx-background-color: white;") + "-fx-border-color: #e5e7eb; -fx-border-width: 0 1 1 0;");
            
            // Add context menu for scheduling
            dayCell.setOnContextMenuRequested(e -> {
                ContextMenu contextMenu = new ContextMenu();
                MenuItem scheduleItem = new MenuItem("📅 Schedule Load");
                scheduleItem.setOnAction(event -> showScheduleLoadDialog(driver, weekStart.plusDays(dayIndex)));
                contextMenu.getItems().add(scheduleItem);
                contextMenu.show(dayCell, e.getScreenX(), e.getScreenY());
            });
            
            weekGrid.add(dayCell, d + 1, rowIndex);
            dayCells[d] = dayCell;
        }
        List<LoadRow> driverLoads = filteredRows.stream().filter(r -> r.driver.equals(driver)).toList();
        int barIndex = 0;
        for (LoadRow loadRow : driverLoads) {
            LocalDate pickup = loadRow.load.getPickUpDate();
            LocalDate delivery = loadRow.load.getDeliveryDate();
            if (pickup == null || delivery == null) continue;
            int startCol = (int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, pickup);
            int endCol = (int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, delivery);
            startCol = Math.max(0, startCol);
            endCol = Math.min(6, endCol);
            if (endCol < 0 || startCol > 6) continue;
            double barHeight = 32;
            double barMargin = 8 + barIndex * (barHeight + 6);
            String color = LoadStatusUtil.colorFor(loadRow.load.getStatus());
            
            // Check if this load has conflicts
            boolean hasConflict = conflictMap.containsKey(driver) && 
                conflictMap.get(driver).stream().anyMatch(c -> 
                    c.load1.equals(loadRow.load) || c.load2.equals(loadRow.load));
            
            if (hasConflict) {
                color = "#dc2626"; // Red for conflicts
            }
            
            for (int d = startCol; d <= endCol; d++) {
                Pane bar = new Pane();
                String barStyle = "-fx-background-color: " + color + "; -fx-background-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 2, 0, 0, 1);";
                if (hasConflict) {
                    barStyle += "; -fx-border-color: #991b1b; -fx-border-width: 2; -fx-border-radius: 6;";
                }
                bar.setStyle(barStyle);
                bar.setMinHeight(barHeight);
                bar.setMaxHeight(barHeight);
                bar.setPrefHeight(barHeight);
                bar.setTranslateY(barMargin);
                
                String tooltipText = buildLoadTooltipText(loadRow.load, hasConflict);
                
                Tooltip tip = new Tooltip(tooltipText);
                Tooltip.install(bar, tip);
                bar.setOnMouseClicked(e -> {/* Show load details dialog if needed */});
                dayCells[d].getChildren().add(bar);
                if (d == startCol) {
                    Label infoLabel = new Label(LoadStatusUtil.iconFor(loadRow.load.getStatus()) + " " +
                            loadRow.load.getLoadNumber() + "\n" + loadRow.load.getCustomer());
                    infoLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                    infoLabel.setTextFill(Color.WHITE);
                    infoLabel.setStyle("-fx-background-radius: 6; -fx-padding: 2 8 2 8;");
                    infoLabel.setTranslateY(barMargin + 4);
                    infoLabel.setWrapText(true);
                    dayCells[d].getChildren().add(infoLabel);
                }
            }
            barIndex++;
        }
    }

    private void showScheduleLoadDialog(Employee driver, LocalDate selectedDate) {
        Dialog<Load> dialog = new Dialog<>();
        dialog.setTitle("Schedule Load for " + driver.getName());
        dialog.setHeaderText("Schedule a new load for " + selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        
        // Set the button types
        ButtonType scheduleButtonType = new ButtonType("Schedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(scheduleButtonType, ButtonType.CANCEL);
        
        // Create the custom content
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20, 150, 10, 10));
        
        // Conflict warning section
        VBox conflictWarningBox = new VBox(5);
        conflictWarningBox.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #fecaca; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 10;");
        Label conflictTitle = new Label("⚠️ Potential Conflicts");
        conflictTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        conflictTitle.setTextFill(Color.rgb(220, 38, 38));
        Label conflictText = new Label("Check existing loads for this driver before scheduling");
        conflictText.setFont(Font.font("Segoe UI", 11));
        conflictText.setTextFill(Color.rgb(107, 114, 128));
        conflictWarningBox.getChildren().addAll(conflictTitle, conflictText);
        
        // Show existing loads for this driver
        List<Load> existingLoads = allRows.stream()
            .filter(row -> row.driver.equals(driver))
            .map(row -> row.load)
            .toList();
        
        if (!existingLoads.isEmpty()) {
            VBox existingLoadsBox = new VBox(5);
            existingLoadsBox.setStyle("-fx-background-color: #f0f9ff; -fx-border-color: #bae6fd; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 10;");
            Label existingTitle = new Label("📋 Existing Loads for " + driver.getName());
            existingTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            existingTitle.setTextFill(Color.rgb(30, 64, 175));
            existingLoadsBox.getChildren().add(existingTitle);
            
            for (Load load : existingLoads) {
                String pickupTime = formatTime(load.getPickUpTime());
                String deliveryTime = formatTime(load.getDeliveryTime());
                String loadInfo = String.format(
                    "%s - %s (%s %s → %s %s)",
                    load.getLoadNumber(),
                    load.getCustomer(),
                    formatDate(load.getPickUpDate()),
                    pickupTime,
                    formatDate(load.getDeliveryDate()),
                    deliveryTime
                );
                Label loadLabel = new Label("• " + loadInfo);
                loadLabel.setFont(Font.font("Segoe UI", 10));
                loadLabel.setTextFill(Color.rgb(55, 65, 81));
                existingLoadsBox.getChildren().add(loadLabel);
            }
            contentBox.getChildren().add(existingLoadsBox);
        }
        
        contentBox.getChildren().add(conflictWarningBox);
        
        // Form fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        TextField loadNumberField = new TextField();
        loadNumberField.setPromptText("Load Number");
        TextField customerField = new TextField();
        customerField.setPromptText("Customer");
        TextField pickupLocationField = new TextField();
        pickupLocationField.setPromptText("Pickup Location");
        TextField deliveryLocationField = new TextField();
        deliveryLocationField.setPromptText("Delivery Location");
        DatePicker pickupDatePicker = new DatePicker(selectedDate);
        DatePicker deliveryDatePicker = new DatePicker(selectedDate);
        TextField pickupTimeField = new TextField();
        pickupTimeField.setPromptText("Pickup Time (HH:MM)");
        TextField deliveryTimeField = new TextField();
        deliveryTimeField.setPromptText("Delivery Time (HH:MM)");
        ComboBox<Load.Status> statusComboBox = new ComboBox<>();
        statusComboBox.getItems().addAll(Load.Status.values());
        statusComboBox.setValue(Load.Status.BOOKED);
        
        grid.add(new Label("Load Number:"), 0, 0);
        grid.add(loadNumberField, 1, 0);
        grid.add(new Label("Customer:"), 0, 1);
        grid.add(customerField, 1, 1);
        grid.add(new Label("Pickup Location:"), 0, 2);
        grid.add(pickupLocationField, 1, 2);
        grid.add(new Label("Delivery Location:"), 0, 3);
        grid.add(deliveryLocationField, 1, 3);
        grid.add(new Label("Pickup Date:"), 0, 4);
        grid.add(pickupDatePicker, 1, 4);
        grid.add(new Label("Pickup Time:"), 0, 5);
        grid.add(pickupTimeField, 1, 5);
        grid.add(new Label("Delivery Date:"), 0, 6);
        grid.add(deliveryDatePicker, 1, 6);
        grid.add(new Label("Delivery Time:"), 0, 7);
        grid.add(deliveryTimeField, 1, 7);
        grid.add(new Label("Status:"), 0, 8);
        grid.add(statusComboBox, 1, 8);
        
        Label pickupTimeHelper = new Label("e.g. 2:30 PM or 14:30 (America/Chicago)");
        pickupTimeHelper.setFont(Font.font("Segoe UI", 9));
        pickupTimeHelper.setTextFill(Color.rgb(107, 114, 128));
        grid.add(pickupTimeHelper, 2, 5);
        Label deliveryTimeHelper = new Label("e.g. 4:00 PM or 16:00 (America/Chicago)");
        deliveryTimeHelper.setFont(Font.font("Segoe UI", 9));
        deliveryTimeHelper.setTextFill(Color.rgb(107, 114, 128));
        grid.add(deliveryTimeHelper, 2, 7);
        
        contentBox.getChildren().add(grid);
        dialog.getDialogPane().setContent(contentBox);
        
        // Request focus on the first field
        Platform.runLater(loadNumberField::requestFocus);
        
        // Convert the result to a Load when the schedule button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == scheduleButtonType) {
                try {
                    // Validate for conflicts before creating
                    LocalDate pickupDate = pickupDatePicker.getValue();
                    LocalDate deliveryDate = deliveryDatePicker.getValue();
                    LocalTime pickupTime = parseTime(pickupTimeField.getText());
                    LocalTime deliveryTime = parseTime(deliveryTimeField.getText());
                    
                    // Check for conflicts with existing loads
                    List<String> conflicts = new ArrayList<>();
                    for (Load existingLoad : existingLoads) {
                        LoadConflict conflict = checkTimeConflict(
                            new Load(0, loadNumberField.getText(), "", customerField.getText(), 
                                   pickupLocationField.getText(), deliveryLocationField.getText(), 
                                   driver, driver.getTruckUnit(), statusComboBox.getValue(), 
                                   0.0, "", pickupDate, pickupTime, deliveryDate, deliveryTime, 
                                   "", false, false),
                            existingLoad
                        );
                        if (conflict != null) {
                            conflicts.add("Conflict with " + existingLoad.getLoadNumber() + ": " + conflict.description);
                        }
                    }
                    
                    if (!conflicts.isEmpty()) {
                        String conflictMessage = "Scheduling conflicts detected:\n\n" + String.join("\n", conflicts);
                        showAlert("Scheduling Conflict", conflictMessage);
                        return null;
                    }
                    
                    // Create a new Load with required parameters
                    Load newLoad = new Load(
                        0, // ID will be auto-generated
                        loadNumberField.getText(),
                        "", // PO Number
                        customerField.getText(),
                        pickupLocationField.getText(),
                        deliveryLocationField.getText(),
                        driver, // Set the driver object directly
                        driver.getTruckUnit(), // Truck unit snapshot
                        statusComboBox.getValue(),
                        0.0, // Gross amount (can be updated later)
                        "", // Notes
                        pickupDate,
                        pickupTime,
                        deliveryDate,
                        deliveryTime,
                        "", // Reminder
                        false, // Has lumper
                        false // Has revised rate confirmation
                    );
                    return newLoad;
                } catch (Exception e) {
                    showAlert("Error", "Invalid input: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
        
        Optional<Load> result = dialog.showAndWait();
        result.ifPresent(load -> {
            try {
                LoadDAO loadDAO = new LoadDAO();
                loadDAO.add(load);
                showAlert("Success", "Load scheduled successfully for " + driver.getName());
                refresh();
            } catch (Exception e) {
                showAlert("Error", "Failed to schedule load: " + e.getMessage());
            }
        });
    }

    private String formatTime(LocalTime time) {
        return time == null ? "No time" : time.atDate(LocalDate.now()).atZone(DEFAULT_ZONE).format(AMERICAN_TIME_FORMAT);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "No date" : date.atStartOfDay(DEFAULT_ZONE).format(AMERICAN_DATE_FORMAT);
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return null;
        try {
            // Try 24-hour first
            return LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception e) {
            try {
                // Try 12-hour with AM/PM
                return LocalTime.parse(timeStr.trim().toUpperCase(), DateTimeFormatter.ofPattern("h:mm a", Locale.US));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid time format. Use 2:30 PM or 14:30");
            }
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private HBox createLegend() {
        HBox legend = new HBox(20);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPadding(new Insets(10, 0, 15, 0));
        legend.getStyleClass().add("driver-grid-legend");
        Label legendTitle = new Label("Load Status:");
        legendTitle.getStyleClass().add("driver-grid-legend-title");
        legend.getChildren().add(legendTitle);
        for (Load.Status status : Load.Status.values()) {
            HBox statusItem = new HBox(8);
            statusItem.setAlignment(Pos.CENTER_LEFT);
            statusItem.getStyleClass().add("driver-grid-legend-item");
            Rectangle rect = new Rectangle(16, 16);
            rect.setFill(Color.web(LoadStatusUtil.colorFor(status)));
            rect.getStyleClass().add("driver-grid-legend-rect");
            Label lbl = new Label(LoadStatusUtil.iconFor(status) + " " + status.toString());
            lbl.getStyleClass().add("driver-grid-legend-label");
            statusItem.getChildren().addAll(rect, lbl);
            legend.getChildren().add(statusItem);
        }
        // Add conflict indicator
        HBox conflictItem = new HBox(8);
        conflictItem.setAlignment(Pos.CENTER_LEFT);
        conflictItem.getStyleClass().add("driver-grid-legend-item");
        Rectangle conflictRect = new Rectangle(16, 16);
        conflictRect.setFill(Color.web("#dc2626"));
        conflictRect.getStyleClass().add("driver-grid-legend-rect");
        Label conflictLbl = new Label("⚠️ Scheduling Conflict");
        conflictLbl.getStyleClass().add("driver-grid-legend-label");
        conflictItem.getChildren().addAll(conflictRect, conflictLbl);
        legend.getChildren().add(conflictItem);
        return legend;
    }
    
    private String buildLoadTooltipText(Load load, boolean hasConflict) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append(LoadStatusUtil.iconFor(load.getStatus())).append(" ")
               .append(load.getLoadNumber()).append("\n")
               .append("Status: ").append(load.getStatus()).append("\n")
               .append("Customer: ").append(load.getCustomer()).append("\n")
               .append("Pickup: ").append(load.getPickUpLocation()).append(" ").append(load.getPickUpDate()).append(" ").append(load.getPickUpTime()).append("\n")
               .append("Delivery: ").append(load.getDropLocation()).append(" ").append(load.getDeliveryDate()).append(" ").append(load.getDeliveryTime());
        
        // Add additional locations if they exist
        List<LoadLocation> locations = load.getLocations();
        if (locations != null && !locations.isEmpty()) {
            List<LoadLocation> additionalPickups = locations.stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
                
            List<LoadLocation> additionalDrops = locations.stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
            
            if (!additionalPickups.isEmpty()) {
                tooltip.append("\nAdditional Pickups:");
                for (LoadLocation pickup : additionalPickups) {
                    tooltip.append("\n  • ").append(pickup.getCustomer()).append(" - ").append(pickup.getAddress());
                }
            }
            
            if (!additionalDrops.isEmpty()) {
                tooltip.append("\nAdditional Drops:");
                for (LoadLocation drop : additionalDrops) {
                    tooltip.append("\n  • ").append(drop.getCustomer()).append(" - ").append(drop.getAddress());
                }
            }
        }
        
        if (hasConflict) {
            tooltip.append("\n⚠️ SCHEDULING CONFLICT DETECTED");
        }
        
        return tooltip.toString();
    }

    private static class LoadRow {
        final Employee driver;
        final Load load;
        LoadRow(Employee driver, Load load) {
            this.driver = driver;
            this.load = load;
        }
    }

    private static class LoadConflict {
        final Load load1;
        final Load load2;
        final String description;
        
        LoadConflict(Load load1, Load load2, String description) {
            this.load1 = load1;
            this.load2 = load2;
            this.description = description;
        }
    }

    // Update summary card to always use filteredRows and conflictMap
    private int activeDrivers = 0;
    private int totalLoads = 0;
    private int completedLoads = 0;
    private int conflictCount = 0;
    
    // References to summary boxes for updating
    private VBox totalLoadsBox;
    private VBox activeDriversBox;
    private VBox conflictsBox;
    private VBox unassignedBox;

    private void updateSummaryCard() {
        Set<Employee> drivers = new HashSet<>();
        int completed = 0;
        for (LoadRow row : filteredRows) {
            drivers.add(row.driver);
            if (row.load.getStatus() == Load.Status.DELIVERED || row.load.getStatus() == Load.Status.PAID) {
                completed++;
            }
        }
        activeDrivers = drivers.size();
        totalLoads = filteredRows.size();
        completedLoads = completed;
        conflictCount = (int) conflictMap.values().stream().flatMap(List::stream).count();
        
        // Update the summary box labels
        Platform.runLater(() -> {
            if (totalLoadsBox != null) {
                Label valueLabel = (Label) totalLoadsBox.getChildren().get(0);
                valueLabel.setText(String.valueOf(totalLoads));
            }
            if (activeDriversBox != null) {
                Label valueLabel = (Label) activeDriversBox.getChildren().get(0);
                valueLabel.setText(String.valueOf(activeDrivers));
            }
            if (conflictsBox != null) {
                Label valueLabel = (Label) conflictsBox.getChildren().get(0);
                valueLabel.setText(String.valueOf(conflictCount));
            }
            if (unassignedBox != null) {
                Label valueLabel = (Label) unassignedBox.getChildren().get(0);
                valueLabel.setText(String.valueOf(0)); // TODO: Calculate unassigned loads
            }
        });
    }
} 