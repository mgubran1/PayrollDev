package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeesTab;
import com.company.payroll.fuel.FuelTransaction;
import com.company.payroll.fuel.FuelImportTab;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.loads.LoadsTab;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import com.company.payroll.payroll.CheckBoxListCell;
import com.company.payroll.payroll.CheckListView;
import com.company.payroll.payroll.PayrollHistoryEntry;
import com.company.payroll.payroll.ProgressDialog;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * PayrollTab - Main UI component for payroll management
 * Provides comprehensive payroll calculation, display, and export functionality
 */
public class PayrollTab extends BorderPane implements 
        EmployeesTab.EmployeeDataChangeListener,
        LoadsTab.LoadDataChangeListener,
        FuelImportTab.FuelDataChangeListener {
    
    private static final Logger logger = LoggerFactory.getLogger(PayrollTab.class);
    
    // Core components
    private final PayrollCalculator calculator;
    private final PayrollRecurring payrollRecurring;
    private final PayrollAdvances payrollAdvances;
    private final PayrollOtherAdjustments payrollOtherAdjustments;
    private final PayrollEscrow payrollEscrow;
    private final LoadDAO loadDAO;
    
    // UI Components
    private TextField companyNameField;
    private ComboBox<Integer> yearBox;
    private ComboBox<Integer> weekBox;
    private DatePicker weekStartPicker;
    private DatePicker weekEndPicker;
    private ComboBox<Employee> driverBox;
    private TextField driverSearchField;
    
    // Navigation buttons
    private Button prevWeekBtn;
    private Button nextWeekBtn;
    private Button todayBtn;
    
    // Action buttons
    private Button calcBtn;
    private Button refreshBtn;
    private Button lockWeekBtn;
    private Button printPreviewBtn;
    private Button printPdfBtn;
    private Button exportBtn;
    private Button copyBtn;
    private Button payrollHistoryBtn;
    private Button mergeDocsBtn;
    
    // Data collections
    private final ObservableList<Employee> allDrivers = FXCollections.observableArrayList();
    private final ObservableList<Employee> filteredDrivers = FXCollections.observableArrayList();
    private final ObservableList<PayrollCalculator.PayrollRow> summaryRows = FXCollections.observableArrayList();
    private final ObservableList<Load> loadsRows = FXCollections.observableArrayList();
    private final ObservableList<FuelTransaction> fuelRows = FXCollections.observableArrayList();
    
    // Panels
    private PayrollRecurringPanel payrollRecurringPanel;
    private CashAdvancePanel cashAdvancePanel;
    private PayrollOtherAdjustmentPanel payrollOtherAdjustmentPanel;
    private PayrollEscrowPanel payrollEscrowPanel;
    
    // Summary components
    private final Map<String, Label> summaryLabels = new LinkedHashMap<>();
    private Label netPayLabel;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;
    
    // Tables
    private TabPane tabPane;
    private PayrollSummaryTable summaryTable;
    private PayrollLoadsTable loadsTable;
    private PayrollFuelTable fuelTable;
    
    // Locked weeks tracking
    private final Map<String, Set<LocalDate>> lockedWeeks = new ConcurrentHashMap<>();
    private static final String LOCKED_WEEKS_FILE = "locked_weeks.dat";
    
    // Light Blue Theme Styling
    private static final String HEADER_STYLE = "-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white;";
    private static final String SUB_HEADER_STYLE = "-fx-background-color: #1565C0; -fx-text-fill: white;";
    private static final String BUTTON_STYLE = "-fx-background-color: #42A5F5; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5;";
    private static final String BUTTON_HOVER_STYLE = "-fx-background-color: #2196F3;";
    private static final String PRIMARY_BUTTON_STYLE = "-fx-background-color: #1976D2; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5;";
    private static final String PRIMARY_BUTTON_HOVER_STYLE = "-fx-background-color: #1565C0;";
    
    // Thread management
    private Task<Void> currentCalculationTask;
    private Timer autoCalculateTimer;
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    public PayrollTab(EmployeesTab employeesTab, PayrollCalculator calculator, LoadDAO loadDAO) {
        this.calculator = calculator;
        this.loadDAO = loadDAO;
        this.payrollRecurring = new PayrollRecurring();
        this.payrollAdvances = PayrollAdvances.getInstance();
        this.payrollOtherAdjustments = PayrollOtherAdjustments.getInstance();
        this.payrollEscrow = PayrollEscrow.getInstance();
        
        loadLockedWeeks();
        
        setStyle("-fx-background-color: #f5f7fa;");
        setPadding(new Insets(0));

        // Create header with light blue theme
        VBox headerSection = createHeaderSection();
        
        // Create main content
        VBox mainContent = createMainContent(loadDAO);
        
        // Create ScrollPane for the main content only
        ScrollPane contentScrollPane = new ScrollPane(mainContent);
        contentScrollPane.setFitToWidth(true);
        contentScrollPane.setFitToHeight(false);
        contentScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScrollPane.setStyle("-fx-background-color: #f5f7fa;");
        
        // Use BorderPane layout to keep header fixed
        setTop(headerSection);
        setCenter(contentScrollPane);
        
        // Setup events
        setupEventHandlers();
        
        // Connect to employee tab
        employeesTab.addEmployeeDataChangeListener(this);
        
        // Initial data load
        refreshDrivers(employeesTab.getCurrentEmployees());
        
        // Auto-calculate on initial load
        Platform.runLater(() -> {
            if (weekStartPicker.getValue() != null) {
                autoCalculatePayroll();
            }
        });
        
        // Cleanup on close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executorService.shutdown();
            if (autoCalculateTimer != null) {
                autoCalculateTimer.cancel();
            }
        }));
    }
	
    private VBox createHeaderSection() {
        VBox headerSection = new VBox(0);
        
        // Company header bar with gradient
        HBox headerBar = new HBox();
        headerBar.setStyle(HEADER_STYLE);
        headerBar.setPadding(new Insets(25, 30, 25, 30));
        headerBar.setAlignment(Pos.CENTER_LEFT);
        
        companyNameField = new TextField("TRUCKING COMPANY PAYROLL");
        companyNameField.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                "-fx-font-size: 32px; -fx-font-weight: bold; -fx-border-color: transparent;");
        companyNameField.setPrefWidth(600);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        VBox statusBox = new VBox(5);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        
        Label dateLabel = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
        dateLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-opacity: 0.9;");
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(24, 24);
        loadingIndicator.setVisible(false);
        loadingIndicator.setStyle("-fx-progress-color: white;");
        
        statusBox.getChildren().addAll(statusLabel, dateLabel);
        
        headerBar.getChildren().addAll(companyNameField, spacer, statusBox, loadingIndicator);
        
        // Navigation bar
        HBox navBar = createNavigationBar();
        
        headerSection.getChildren().addAll(headerBar, navBar);
        
        // Enhanced shadow effect
        DropShadow shadow = new DropShadow();
        shadow.setRadius(8.0);
        shadow.setOffsetY(3.0);
        shadow.setColor(Color.color(0.2, 0.2, 0.2, 0.3));
        headerSection.setEffect(shadow);
        
        return headerSection;
    }

    private HBox createNavigationBar() {
        HBox navBar = new HBox(12);
        navBar.setStyle(SUB_HEADER_STYLE);
        navBar.setPadding(new Insets(18, 30, 18, 30));
        navBar.setAlignment(Pos.CENTER_LEFT);
        
        // Week navigation section
        VBox weekSection = new VBox(3);
        Label weekLabel = new Label("WEEK SELECTION");
        weekLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-opacity: 0.8;");
        
        HBox weekControls = new HBox(8);
        
        prevWeekBtn = createNavButton("◀", "Previous Week");
        nextWeekBtn = createNavButton("▶", "Next Week");
        todayBtn = createNavButton("Today", "Go to Current Week");
        
        yearBox = new ComboBox<>(FXCollections.observableArrayList(yearRange()));
        yearBox.setValue(LocalDate.now().getYear());
        yearBox.setPrefWidth(100);
        yearBox.setStyle("-fx-font-weight: bold; -fx-background-radius: 5;");
        
        weekBox = new ComboBox<>(FXCollections.observableArrayList(weekNumbers()));
        weekBox.setPrefWidth(90);
        weekBox.setStyle("-fx-font-weight: bold; -fx-background-radius: 5;");
        int currentWeek = LocalDate.now().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        weekBox.setValue(currentWeek);
        
        weekStartPicker = new DatePicker();
        weekStartPicker.setPrefWidth(130);
        weekStartPicker.setStyle("-fx-background-radius: 5;");
        
        weekEndPicker = new DatePicker();
        weekEndPicker.setPrefWidth(130);
        weekEndPicker.setDisable(true);
        weekEndPicker.setStyle("-fx-background-radius: 5; -fx-opacity: 0.8;");
        
        updateWeekDatePickers();
        
        weekControls.getChildren().addAll(
            prevWeekBtn, 
            new Label("Year:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            yearBox,
            new Label("Week:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            weekBox,
            nextWeekBtn,
            todayBtn,
            new Separator() {{ setOrientation(javafx.geometry.Orientation.VERTICAL); setPrefHeight(30); }},
            weekStartPicker,
            new Label("to") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            weekEndPicker
        );
        
        weekSection.getChildren().addAll(weekLabel, weekControls);
        
        // Separator
        Separator mainSep = new Separator();
        mainSep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSep.setPrefHeight(40);
        
        // Driver selection section with search
        VBox driverSection = new VBox(3);
        Label driverLabel = new Label("DRIVER SELECTION");
        driverLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-opacity: 0.8;");
        
        HBox driverControls = new HBox(8);
        
        driverSearchField = new TextField();
        driverSearchField.setPromptText("Search by name or truck/unit...");
        driverSearchField.setPrefWidth(200);
        driverSearchField.setStyle("-fx-background-radius: 5;");
        
        driverBox = new ComboBox<>();
        driverBox.setPromptText("All Drivers");
        driverBox.setPrefWidth(250);
        driverBox.setStyle("-fx-font-weight: bold; -fx-background-radius: 5;");
        
        Button clearDriverBtn = createNavButton("✕", "Clear Selection");
        clearDriverBtn.setOnAction(e -> {
            driverBox.setValue(null);
            driverSearchField.clear();
        });
        
        driverControls.getChildren().addAll(driverSearchField, driverBox, clearDriverBtn);
        driverSection.getChildren().addAll(driverLabel, driverControls);
        
        // Action buttons section
        Separator actionSep = new Separator();
        actionSep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        actionSep.setPrefHeight(40);
        
        VBox actionSection = new VBox(3);
        Label actionLabel = new Label("ACTIONS");
        actionLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-opacity: 0.8;");
        
        HBox actionControls = new HBox(8);
        
        refreshBtn = createActionButton("↻ Refresh", true);
        calcBtn = createActionButton("Calculate", true);
        lockWeekBtn = createActionButton("🔒 Lock Week", false);
        mergeDocsBtn = createActionButton("📎 Merge Docs", false);
        
        actionControls.getChildren().addAll(refreshBtn, calcBtn, lockWeekBtn, mergeDocsBtn);
        actionSection.getChildren().addAll(actionLabel, actionControls);
        
        navBar.getChildren().addAll(weekSection, mainSep, driverSection, actionSep, actionSection);
        
        return navBar;
    }

    private VBox createMainContent(LoadDAO loadDAO) {
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(25));
        
        // Summary section with enhanced styling
        VBox summarySection = createSummarySection();
        
        // Action buttons bar
        HBox actionBar = createActionBar();
        
        // Tabs with icons
        tabPane = createTabPane(loadDAO);
        
        mainContent.getChildren().addAll(summarySection, actionBar, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        
        return mainContent;
    }

    private VBox createSummarySection() {
        VBox summaryBox = new VBox(15);
        summaryBox.setPadding(new Insets(25));
        summaryBox.setStyle("-fx-background-color: white; -fx-background-radius: 12px;");
        
        // Enhanced shadow
        DropShadow shadow = new DropShadow();
        shadow.setRadius(5.0);
        shadow.setOffsetY(2.0);
        shadow.setColor(Color.color(0.4, 0.4, 0.4, 0.2));
        summaryBox.setEffect(shadow);
        
        // Title
        Label summaryTitle = new Label("PAYROLL SUMMARY");
        summaryTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1976D2;");
        
        Separator titleSep = new Separator();
        titleSep.setPadding(new Insets(5, 0, 10, 0));
        
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(50);
        summaryGrid.setVgap(10);
        
        String[][] summaryOrder = {
            {"Gross Pay:", "gross"}, 
            {"Service Fee:", "serviceFee"}, 
            {"Gross after Service Fee:", "grossAfterServiceFee"},
            {"Company Pay:", "companyPay"}, 
            {"Driver Pay:", "driverPay"},
            {"Fuel Deductions:", "fuel"}, 
            {"Gross after Fuel:", "grossAfterFuel"},
            {"Recurring Fees:", "recurringFees"},
            {"Advances Given:", "advancesGiven"}, 
            {"Advance Repayments:", "advanceRepayments"},
            {"Escrow Deposits:", "escrowDeposits"},
            {"Other Deductions:", "otherDeductions"}, 
            {"Reimbursements:", "reimbursements"}
        };

        int row = 0, col = 0;
        for (String[] pair : summaryOrder) {
            Label label = new Label(pair[0]);
            label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #424242;");
            
            Label value = new Label("$0.00");
            value.setStyle("-fx-font-size: 15px; -fx-text-fill: #616161;");
            summaryLabels.put(pair[1], value);
            
            summaryGrid.add(label, col * 2, row);
            summaryGrid.add(value, col * 2 + 1, row);
            
            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }

        Separator separator = new Separator();
        separator.setPadding(new Insets(15, 0, 15, 0));
        
        HBox netPayBox = new HBox();
        netPayBox.setAlignment(Pos.CENTER);
        netPayBox.setPadding(new Insets(15));
        netPayBox.setStyle("-fx-background-color: linear-gradient(to right, #E3F2FD, #BBDEFB); " +
                          "-fx-background-radius: 8px;");
        
        netPayLabel = new Label("NET PAY: $0.00");
        netPayLabel.setFont(Font.font("System", FontWeight.BOLD, 36));
        netPayLabel.setTextFill(Color.web("#1565C0"));
        
        netPayBox.getChildren().add(netPayLabel);
        
        summaryBox.getChildren().addAll(summaryTitle, titleSep, summaryGrid, separator, netPayBox);
        
        return summaryBox;
    }

    private HBox createActionBar() {
        HBox actionBar = new HBox(12);
        actionBar.setPadding(new Insets(15));
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        
        printPreviewBtn = createSecondaryButton("👁 Print Preview");
        printPdfBtn = createSecondaryButton("📄 Save PDF");
        exportBtn = createSecondaryButton("📊 Export Excel");
        copyBtn = createSecondaryButton("📋 Copy Table");
        payrollHistoryBtn = createSecondaryButton("📅 History");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label quickActionsLabel = new Label("Quick Actions:");
        quickActionsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #616161;");
        
        actionBar.getChildren().addAll(
            quickActionsLabel,
            printPreviewBtn, printPdfBtn, exportBtn, copyBtn, payrollHistoryBtn, spacer
        );
        
        // Add shadow
        DropShadow shadow = new DropShadow();
        shadow.setRadius(3.0);
        shadow.setOffsetY(1.0);
        shadow.setColor(Color.color(0.4, 0.4, 0.4, 0.2));
        actionBar.setEffect(shadow);
        
        return actionBar;
    }

    private TabPane createTabPane(LoadDAO loadDAO) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-font-size: 14px; -fx-background-color: white;");
        
        summaryTable = new PayrollSummaryTable(summaryRows);
        loadsTable = new PayrollLoadsTable(loadsRows);
        fuelTable = new PayrollFuelTable(fuelRows);
        
        payrollRecurringPanel = new PayrollRecurringPanel(payrollRecurring, driverBox, weekStartPicker);
        cashAdvancePanel = new CashAdvancePanel(payrollAdvances, driverBox, weekStartPicker);
        payrollOtherAdjustmentPanel = new PayrollOtherAdjustmentPanel(payrollOtherAdjustments, driverBox, weekStartPicker);
        payrollOtherAdjustmentPanel.setLoadDAO(loadDAO);
        payrollEscrowPanel = new PayrollEscrowPanel(payrollEscrow, driverBox, weekStartPicker);
        
        Tab summaryTab = new Tab("📊 Summary", summaryTable);
        Tab loadsTab = new Tab("🚚 Loads", loadsTable);
        Tab fuelTab = new Tab("⛽ Fuel", fuelTable);
        Tab recurringTab = new Tab("🔄 Recurring", payrollRecurringPanel);
        Tab advancesTab = new Tab("💵 Advances", cashAdvancePanel);
        Tab escrowTab = new Tab("🏦 Escrow", payrollEscrowPanel);
        Tab adjustmentsTab = new Tab("⚙️ Adjustments", payrollOtherAdjustmentPanel);
        
        tabs.getTabs().addAll(summaryTab, loadsTab, fuelTab, recurringTab, advancesTab, escrowTab, adjustmentsTab);
        
        // Style tabs
        tabs.getTabs().forEach(tab -> {
            tab.setStyle("-fx-font-weight: bold;");
        });
        
        return tabs;
    }

    private Button createNavButton(String text, String tooltip) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: rgba(255,255,255,0.3); -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5; " +
                "-fx-padding: 5 10 5 10;");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.4); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; " +
                "-fx-padding: 5 10 5 10;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.3); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; " +
                "-fx-padding: 5 10 5 10;"));
        return btn;
    }

    private Button createActionButton(String text, boolean primary) {
        Button btn = new Button(text);
        if (primary) {
            btn.setStyle(PRIMARY_BUTTON_STYLE);
            btn.setOnMouseEntered(e -> btn.setStyle(PRIMARY_BUTTON_STYLE + PRIMARY_BUTTON_HOVER_STYLE));
            btn.setOnMouseExited(e -> btn.setStyle(PRIMARY_BUTTON_STYLE));
        } else {
            btn.setStyle(BUTTON_STYLE);
            btn.setOnMouseEntered(e -> btn.setStyle(BUTTON_STYLE + BUTTON_HOVER_STYLE));
            btn.setOnMouseExited(e -> btn.setStyle(BUTTON_STYLE));
        }
        return btn;
    }

    private Button createSecondaryButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #E3F2FD; -fx-text-fill: #1976D2; " +
                "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #BBDEFB; " +
                "-fx-text-fill: #1565C0; -fx-font-weight: bold; -fx-background-radius: 5;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #E3F2FD; " +
                "-fx-text-fill: #1976D2; -fx-font-weight: bold; -fx-background-radius: 5;"));
        return btn;
    }

    private void setupEventHandlers() {
        // Week navigation
        prevWeekBtn.setOnAction(e -> navigateWeek(-1));
        nextWeekBtn.setOnAction(e -> navigateWeek(1));
        todayBtn.setOnAction(e -> navigateToToday());
        
        // Date/week changes
        yearBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                updateWeekDatePickers();
                autoCalculatePayroll();
            }
        });
        
        weekBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                updateWeekDatePickers();
                autoCalculatePayroll();
            }
        });
        
        weekStartPicker.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                updateWeekFromDate(n);
                autoCalculatePayroll();
            }
        });
        
        // Driver search functionality
        driverSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterDrivers(newVal);
        });
        
        // Driver selection
        driverBox.valueProperty().addListener((obs, o, n) -> autoCalculatePayroll());
        
        // Action buttons
        refreshBtn.setOnAction(e -> refreshAll());
        calcBtn.setOnAction(e -> calculateAndDisplayPayroll());
        lockWeekBtn.setOnAction(e -> toggleWeekLock());
        mergeDocsBtn.setOnAction(e -> showMergeDocumentsDialog());
        printPreviewBtn.setOnAction(e -> showPrintPreview());
        printPdfBtn.setOnAction(e -> exportToPDF());
        exportBtn.setOnAction(e -> exportToExcel());
        copyBtn.setOnAction(e -> copyTableToClipboard());
        payrollHistoryBtn.setOnAction(e -> showPayrollHistory());
    }

    private void filterDrivers(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredDrivers.setAll(allDrivers);
        } else {
            String search = searchText.toLowerCase().trim();
            List<Employee> filtered = allDrivers.stream()
                .filter(emp -> {
                    String name = emp.getName() != null ? emp.getName().toLowerCase() : "";
                    String truck = emp.getTruckUnit() != null ? emp.getTruckUnit().toLowerCase() : "";
                    return name.contains(search) || truck.contains(search);
                })
                .collect(Collectors.toList());
            filteredDrivers.setAll(filtered);
        }
        
        // Update combo box
        Employee currentSelection = driverBox.getValue();
        driverBox.getItems().clear();
        driverBox.getItems().add(null); // "All Drivers"
        driverBox.getItems().addAll(filteredDrivers);
        
        // Restore selection if still in filtered list
        if (currentSelection != null && filteredDrivers.contains(currentSelection)) {
            driverBox.setValue(currentSelection);
        }
    }

    private void showMergeDocumentsDialog() {
        Employee driver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        
        if (driver == null) {
            showError("Please select a driver to merge documents");
            return;
        }
        
        List<Load> driverLoads = loadsRows.stream()
            .filter(l -> l.getDriver() != null && l.getDriver().getId() == driver.getId())
            .collect(Collectors.toList());
            
        if (driverLoads.isEmpty()) {
            showError("No loads found for the selected driver in this period");
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Merge Load Documents");
        dialog.setHeaderText("Merge documents for " + driver.getName() + " - Week of " + 
                           weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setMinWidth(500);
        
        // Load selection
        Label selectLabel = new Label("Select loads to include documents from:");
        selectLabel.setStyle("-fx-font-weight: bold;");
        
        CheckListView<Load> loadCheckList = new CheckListView<>();
        loadCheckList.getItems().addAll(driverLoads);
        loadCheckList.setPrefHeight(300);
        loadCheckList.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5;");
        
        // Select all/none buttons
        HBox selectionButtons = new HBox(10);
        Button selectAllBtn = new Button("Select All");
        Button selectNoneBtn = new Button("Select None");
        selectAllBtn.setOnAction(e -> loadCheckList.getCheckModel().selectAll());
        selectNoneBtn.setOnAction(e -> loadCheckList.getCheckModel().clearSelection());
        selectionButtons.getChildren().addAll(selectAllBtn, selectNoneBtn);
        
        // Options
        Separator optionsSep = new Separator();
        optionsSep.setPadding(new Insets(10, 0, 10, 0));
        
        Label optionsLabel = new Label("Options:");
        optionsLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox includePayStub = new CheckBox("Include Pay Stub as Cover Page");
        includePayStub.setSelected(true);
        
        CheckBox openAfterMerge = new CheckBox("Open PDF after merging");
        openAfterMerge.setSelected(true);
        
        content.getChildren().addAll(
            selectLabel,
            loadCheckList,
            selectionButtons,
            optionsSep,
            optionsLabel,
            includePayStub,
            openAfterMerge
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Style the dialog
        dialog.getDialogPane().setStyle("-fx-font-size: 14px;");
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setStyle(PRIMARY_BUTTON_STYLE);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                List<Load> selectedLoads = new ArrayList<>(loadCheckList.getCheckModel().getSelectedItems());
                if (selectedLoads.isEmpty()) {
                    showError("Please select at least one load");
                    return null;
                }
                
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Merged PDF");
                fileChooser.setInitialFileName(String.format("PayrollDocs_%s_%s.pdf",
                    driver.getName().replace(" ", "_"),
                    weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                
                File outputFile = fileChooser.showSaveDialog(dialog.getOwner());
                if (outputFile != null) {
                    mergeDocuments(driver, weekStart, selectedLoads, 
                                 includePayStub.isSelected(), outputFile, 
                                 openAfterMerge.isSelected());
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }

    private void mergeDocuments(Employee driver, LocalDate weekStart, List<Load> loads,
                               boolean includePayStub, File outputFile, boolean openAfter) {
        Task<Void> mergeTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Preparing documents...");
                PDFMergerUtility merger = new PDFMergerUtility();
                List<File> tempFiles = new ArrayList<>();
                
                try {
                    // Create pay stub if requested
                    if (includePayStub) {
                        updateMessage("Generating pay stub...");
                        File payStubFile = File.createTempFile("paystub", ".pdf");
                        tempFiles.add(payStubFile);
                        generatePDF(payStubFile, driver, weekStart);
                        merger.addSource(payStubFile);
                    }
                    
                    // Add documents from each load
                    int processedDocs = 0;
                    for (Load load : loads) {
                        updateMessage("Processing load " + load.getLoadNumber() + "...");
                        List<Load.LoadDocument> docs = loadDAO.getDocumentsByLoadId(load.getId());
                        
                        for (Load.LoadDocument doc : docs) {
                            File docFile = new File(doc.getFilePath());
                            if (docFile.exists() && doc.getFilePath().toLowerCase().endsWith(".pdf")) {
                                merger.addSource(docFile);
                                processedDocs++;
                            }
                        }
                    }
                    
                    if (processedDocs == 0 && !includePayStub) {
                        throw new Exception("No PDF documents found in selected loads");
                    }
                    
                    updateMessage("Merging documents...");
                    merger.setDestinationFileName(outputFile.getAbsolutePath());
                    merger.mergeDocuments(null);
                    
                    updateMessage("Documents merged successfully!");
                    return null;
                } finally {
                    // Clean up temp files
                    tempFiles.forEach(File::delete);
                }
            }
            
            @Override
            protected void succeeded() {
                showInfo("Documents merged successfully!");
                if (openAfter && outputFile.exists()) {
                    try {
                        java.awt.Desktop.getDesktop().open(outputFile);
                    } catch (IOException e) {
                        logger.error("Failed to open PDF", e);
                    }
                }
            }
            
            @Override
            protected void failed() {
                Throwable ex = getException();
                logger.error("Failed to merge documents", ex);
                showError("Failed to merge documents: " + ex.getMessage());
            }
        };
        
        // Progress dialog
        ProgressDialog<Void> progressDialog = new ProgressDialog<>(mergeTask);
        progressDialog.setTitle("Merging Documents");
        progressDialog.setHeaderText("Please wait while documents are being merged...");
        progressDialog.initModality(Modality.APPLICATION_MODAL);
        
        executorService.submit(mergeTask);
        progressDialog.showAndWait();
    }

    private void autoCalculatePayroll() {
        // Cancel any existing timer
        if (autoCalculateTimer != null) {
            autoCalculateTimer.cancel();
        }
        
        autoCalculateTimer = new Timer();
        autoCalculateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (weekStartPicker.getValue() != null) {
                        calculateAndDisplayPayroll();
                        updateAllPanels();
                    }
                });
            }
        }, 300); // 300ms delay
    }

    private void updateAllPanels() {
        try {
            payrollRecurringPanel.updateRecurringTable();
            cashAdvancePanel.updateAdvanceTable();
            payrollOtherAdjustmentPanel.updateAdjustmentTable();
            payrollEscrowPanel.updateEscrowTable();
            updateLockStatus();
        } catch (Exception e) {
            logger.error("Error updating panels", e);
        }
    }

    private void navigateWeek(int direction) {
        LocalDate currentStart = weekStartPicker.getValue();
        if (currentStart != null) {
            LocalDate newStart = currentStart.plusWeeks(direction);
            weekStartPicker.setValue(newStart);
        }
    }

    private void navigateToToday() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        weekStartPicker.setValue(weekStart);
    }

    private void updateWeekFromDate(LocalDate date) {
        int year = date.getYear();
        int week = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        
        yearBox.setValue(year);
        weekBox.setValue(week);
        
        LocalDate weekEnd = date.plusDays(6);
        weekEndPicker.setValue(weekEnd);
    }

    private void refreshAll() {
        if (currentCalculationTask != null && currentCalculationTask.isRunning()) {
            currentCalculationTask.cancel();
        }
        
        currentCalculationTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    statusLabel.setText("Refreshing...");
                    loadingIndicator.setVisible(true);
                    setInputsEnabled(false);
                });
                
                Thread.sleep(100);
                
                Platform.runLater(() -> {
                    calculateAndDisplayPayroll();
                    updateAllPanels();
                });
                
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    statusLabel.setText("Ready");
                    loadingIndicator.setVisible(false);
                    setInputsEnabled(true);
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    statusLabel.setText("Error occurred");
                    loadingIndicator.setVisible(false);
                    setInputsEnabled(true);
                    showError("Failed to refresh data: " + getException().getMessage());
                });
            }
        };
        
        executorService.submit(currentCalculationTask);
    }

    private void setInputsEnabled(boolean enabled) {
        yearBox.setDisable(!enabled);
        weekBox.setDisable(!enabled);
        weekStartPicker.setDisable(!enabled);
        driverBox.setDisable(!enabled);
        driverSearchField.setDisable(!enabled);
        prevWeekBtn.setDisable(!enabled);
        nextWeekBtn.setDisable(!enabled);
        todayBtn.setDisable(!enabled);
        refreshBtn.setDisable(!enabled);
        calcBtn.setDisable(!enabled);
    }

    private void toggleWeekLock() {
        Employee driver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        
        if (weekStart == null) {
            showError("Please select a week to lock/unlock");
            return;
        }
        
        String driverId = driver != null ? String.valueOf(driver.getId()) : "ALL";
        Set<LocalDate> driverLocks = lockedWeeks.computeIfAbsent(driverId, k -> ConcurrentHashMap.newKeySet());
        
        boolean isLocked = driverLocks.contains(weekStart);
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(isLocked ? "Unlock Week" : "Lock Week");
        confirm.setHeaderText(isLocked ? "Unlock Payroll Week" : "Lock Payroll Week");
        confirm.setContentText(isLocked ? 
            "Are you sure you want to unlock this week's payroll? This will allow modifications." :
            "Are you sure you want to lock this week's payroll? This will prevent any modifications.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (isLocked) {
                driverLocks.remove(weekStart);
            } else {
                driverLocks.add(weekStart);
            }
            saveLockedWeeks();
            updateLockStatus();
            showInfo("Week " + (isLocked ? "unlocked" : "locked") + " successfully");
        }
    }

    private void updateLockStatus() {
        Employee driver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        
        if (weekStart == null) {
            lockWeekBtn.setText("🔒 Lock Week");
            lockWeekBtn.setDisable(true);
            return;
        }
        
        String driverId = driver != null ? String.valueOf(driver.getId()) : "ALL";
        Set<LocalDate> driverLocks = lockedWeeks.getOrDefault(driverId, Collections.emptySet());
        
        boolean isLocked = driverLocks.contains(weekStart);
        lockWeekBtn.setText(isLocked ? "🔓 Unlock Week" : "🔒 Lock Week");
        lockWeekBtn.setDisable(false);
        
        // Update UI elements
        boolean hasData = !summaryRows.isEmpty();
        printPreviewBtn.setDisable(!hasData);
        printPdfBtn.setDisable(!hasData);
        exportBtn.setDisable(!hasData);
        copyBtn.setDisable(!hasData);
        mergeDocsBtn.setDisable(!hasData || driver == null);
        
        // Disable modification if locked
        tabPane.getTabs().forEach(tab -> {
            if (tab.getContent() instanceof PayrollRecurringPanel ||
                tab.getContent() instanceof CashAdvancePanel ||
                tab.getContent() instanceof PayrollOtherAdjustmentPanel ||
                tab.getContent() instanceof PayrollEscrowPanel) {
                tab.setDisable(isLocked);
            }
        });
    }

    private void showPrintPreview() {
        Employee driver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        
        if (driver == null) {
            showError("Please select a driver to preview pay stub");
            return;
        }
        
        PayrollCalculator.PayrollRow driverRow = summaryRows.stream()
            .filter(r -> r.driverName.equals(driver.getName()))
            .findFirst()
            .orElse(null);
            
        if (driverRow == null) {
            showError("No payroll data found for " + driver.getName());
            return;
        }
        
        Stage previewStage = new Stage();
        previewStage.setTitle("Pay Stub Preview - " + driver.getName());
        previewStage.initModality(Modality.APPLICATION_MODAL);
        previewStage.initStyle(StageStyle.DECORATED);
        
        VBox previewContent = createPayStubContent(driver, weekStart);
        ScrollPane scrollPane = new ScrollPane(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f5f5f5;");
        
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);
        
        Button printBtn = new Button("🖨️ Print");
        printBtn.setStyle(PRIMARY_BUTTON_STYLE);
        printBtn.setOnAction(e -> printPayStub(previewContent));
        
        Button saveBtn = new Button("💾 Save as PDF");
        saveBtn.setStyle(BUTTON_STYLE);
        saveBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Pay Stub");
            fc.setInitialFileName(String.format("PayStub_%s_%s.pdf",
                driver.getName().replace(" ", "_"),
                weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(previewStage);
            if (file != null) {
                try {
                    generatePDF(file, driver, weekStart);
                    showInfo("Pay stub saved successfully");
                } catch (IOException ex) {
                    showError("Failed to save PDF: " + ex.getMessage());
                }
            }
        });
        
        Button emailBtn = new Button("📧 Email");
        emailBtn.setStyle(BUTTON_STYLE);
        emailBtn.setOnAction(e -> {
            showInfo("Email functionality not yet implemented");
        });
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> previewStage.close());
        
        buttonBar.getChildren().addAll(printBtn, saveBtn, emailBtn, closeBtn);
        
        root.getChildren().addAll(scrollPane, buttonBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        Scene scene = new Scene(root, 700, 900);
        previewStage.setScene(scene);
        previewStage.showAndWait();
    }

    private VBox createPayStubContent(Employee driver, LocalDate weekStart) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(40));
        content.setStyle("-fx-background-color: white;");
        content.setPrefWidth(650);
        content.setMaxWidth(650);
        
        // Header with company info
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER);
        
        Label companyName = new Label(companyNameField.getText());
        companyName.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        companyName.setTextFill(Color.web("#1976D2"));
        
        Label payStubTitle = new Label("EARNINGS STATEMENT");
        payStubTitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        payStubTitle.setTextFill(Color.web("#424242"));
        
        header.getChildren().addAll(companyName, payStubTitle);
        
        Separator headerSep = new Separator();
        headerSep.setPadding(new Insets(10, 0, 10, 0));
        
        // Employee and period info
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(30);
        infoGrid.setVgap(8);
        infoGrid.setPadding(new Insets(10));
        infoGrid.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 8px;");
        
        LocalDate weekEnd = weekStart.plusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        
        addStyledPayStubRow(infoGrid, 0, "Employee:", driver.getName(), true);
        addStyledPayStubRow(infoGrid, 1, "Employee ID:", String.valueOf(driver.getId()), false);
        addStyledPayStubRow(infoGrid, 2, "Truck/Unit:", driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A", false);
        addStyledPayStubRow(infoGrid, 3, "Pay Period:", weekStart.format(formatter) + " - " + weekEnd.format(formatter), false);
        addStyledPayStubRow(infoGrid, 4, "Pay Date:", LocalDate.now().format(formatter), false);
        
        // Get payroll data
        PayrollCalculator.PayrollRow row = summaryRows.stream()
            .filter(r -> r.driverName.equals(driver.getName()))
            .findFirst()
            .orElse(null);
        
        if (row != null) {
            // Earnings section
            VBox earningsSection = createPayStubSection("EARNINGS", new String[][] {
                {"Gross Pay", String.format("$%,.2f", row.gross)},
                {"Load Count", String.valueOf(row.loadCount)},
                {"Service Fee", String.format("($%,.2f)", Math.abs(row.serviceFee))},
                {"Driver Pay", String.format("$%,.2f", row.driverPay)}
            }, Color.web("#2E7D32"));
            
            // Deductions section
            VBox deductionsSection = createPayStubSection("DEDUCTIONS", new String[][] {
                {"Fuel", String.format("$%,.2f", Math.abs(row.fuel))},
                {"Recurring Fees", String.format("$%,.2f", Math.abs(row.recurringFees))},
                {"Advance Repayments", String.format("$%,.2f", Math.abs(row.advanceRepayments))},
                {"Escrow Deposits", String.format("$%,.2f", Math.abs(row.escrowDeposits))},
                {"Other Deductions", String.format("$%,.2f", Math.abs(row.otherDeductions))},
                {"Total Deductions", String.format("$%,.2f", 
                    Math.abs(row.fuel) + Math.abs(row.recurringFees) + 
                    Math.abs(row.advanceRepayments) + Math.abs(row.escrowDeposits) + 
                    Math.abs(row.otherDeductions))}
            }, Color.web("#D32F2F"));
            
            // Reimbursements section (if any)
            VBox reimbSection = null;
            if (row.reimbursements > 0) {
                reimbSection = createPayStubSection("REIMBURSEMENTS", new String[][] {
                    {"Total Reimbursements", String.format("$%,.2f", row.reimbursements)}
                }, Color.web("#1976D2"));
            }
            
            // Advances Given section (if any)
            VBox advancesSection = null;
            if (row.advancesGiven > 0) {
                advancesSection = createPayStubSection("ADVANCES", new String[][] {
                    {"Advances Given This Week", String.format("$%,.2f", row.advancesGiven)}
                }, Color.web("#FF9800"));
            }
            
            // Net pay section
            HBox netPayBox = new HBox();
            netPayBox.setPadding(new Insets(20));
            netPayBox.setAlignment(Pos.CENTER);
            netPayBox.setStyle("-fx-background-color: linear-gradient(to right, #E8F5E9, #C8E6C9); " +
                             "-fx-background-radius: 10px;");
            
            Label netPayLbl = new Label("NET PAY: ");
            netPayLbl.setFont(Font.font("Arial", FontWeight.BOLD, 24));
            netPayLbl.setTextFill(Color.web("#1B5E20"));
            
            Label netPayAmt = new Label(String.format("$%,.2f", row.netPay));
            netPayAmt.setFont(Font.font("Arial", FontWeight.BOLD, 24));
            netPayAmt.setTextFill(row.netPay >= 0 ? Color.web("#1B5E20") : Color.web("#D32F2F"));
            
            netPayBox.getChildren().addAll(netPayLbl, netPayAmt);
            
            // Add all sections
            content.getChildren().addAll(
                header, headerSep, infoGrid,
                new Separator(), earningsSection,
                new Separator(), deductionsSection
            );
            
            if (reimbSection != null) {
                content.getChildren().addAll(new Separator(), reimbSection);
            }
            
            if (advancesSection != null) {
                content.getChildren().addAll(new Separator(), advancesSection);
            }
            
            content.getChildren().addAll(new Separator(), netPayBox);
        }
        
        // Footer
        VBox footer = new VBox(5);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(20, 0, 0, 0));
        
        Label footerText = new Label("This is an electronic pay stub. Please retain for your records.");
        footerText.setStyle("-fx-font-style: italic; -fx-text-fill: #757575;");
        
        Label dateGenerated = new Label("Generated on: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")));
        dateGenerated.setStyle("-fx-font-size: 11px; -fx-text-fill: #757575;");
        
        footer.getChildren().addAll(footerText, dateGenerated);
        content.getChildren().add(footer);
        
        return content;
    }

    private VBox createPayStubSection(String title, String[][] items, Color accentColor) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setTextFill(accentColor);
        
        GridPane grid = new GridPane();
        grid.setHgap(200);
        grid.setVgap(5);
        grid.setPadding(new Insets(5, 0, 0, 20));
        
        for (int i = 0; i < items.length; i++) {
            Label label = new Label(items[i][0] + ":");
            label.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
            
            Label value = new Label(items[i][1]);
            value.setFont(Font.font("Arial", 
                i == items.length - 1 ? FontWeight.BOLD : FontWeight.NORMAL, 13));
            
            if (i == items.length - 1 && items.length > 1) {
                Separator sep = new Separator();
                GridPane.setColumnSpan(sep, 2);
                grid.add(sep, 0, i * 2 - 1);
            }
            
            grid.add(label, 0, i * 2);
            grid.add(value, 1, i * 2);
        }
        
        section.getChildren().addAll(titleLabel, grid);
        return section;
    }

    private void addStyledPayStubRow(GridPane grid, int row, String label, String value, boolean bold) {
        Label lblNode = new Label(label);
        lblNode.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 13));
        lblNode.setTextFill(Color.web("#424242"));
        
        Label valNode = new Label(value);
        valNode.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 13));
        valNode.setTextFill(Color.web("#212121"));
        
        grid.add(lblNode, 0, row);
        grid.add(valNode, 1, row);
    }

    private void printPayStub(Node content) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getScene().getWindow())) {
            PageLayout pageLayout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
            job.getJobSettings().setPageLayout(pageLayout);
            
            boolean printed = job.printPage(content);
            if (printed) {
                job.endJob();
                showInfo("Pay stub sent to printer");
            } else {
                showError("Failed to print pay stub");
            }
        }
    }

    private void exportToPDF() {
        Employee driver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        
        if (driver == null) {
            // Export all drivers
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save All Pay Stubs PDF");
            fileChooser.setInitialFileName(String.format("AllPayStubs_%s.pdf", 
                weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            
            File file = fileChooser.showSaveDialog(getScene().getWindow());
            if (file != null) {
                exportAllPayStubsToPDF(file, weekStart);
            }
        } else {
            // Export single driver
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Pay Stub PDF");
            fileChooser.setInitialFileName(String.format("PayStub_%s_%s.pdf", 
                driver.getName().replace(" ", "_"), 
                weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            
            File file = fileChooser.showSaveDialog(getScene().getWindow());
            if (file != null) {
                try {
                    generatePDF(file, driver, weekStart);
                    showInfo("Pay stub PDF saved successfully");
                } catch (Exception e) {
                    logger.error("Failed to generate PDF", e);
                    showError("Failed to generate PDF: " + e.getMessage());
                }
            }
        }
    }
    
    private void exportAllPayStubsToPDF(File file, LocalDate weekStart) {
        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                PDFMergerUtility merger = new PDFMergerUtility();
                List<File> tempFiles = new ArrayList<>();
                
                try {
                                         int current = 0;
                    int total = summaryRows.size();
                    
                    for (PayrollCalculator.PayrollRow row : summaryRows) {
                        current++;
                        updateProgress(current, total);
                        updateMessage("Generating PDF for " + row.driverName + "...");
                        
                        Employee emp = allDrivers.stream()
                            .filter(e -> e.getName().equals(row.driverName))
                            .findFirst()
                            .orElse(null);
                            
                        if (emp != null) {
                            File tempFile = File.createTempFile("paystub_" + emp.getId(), ".pdf");
                            tempFiles.add(tempFile);
                            generatePDF(tempFile, emp, weekStart);
                            merger.addSource(tempFile);
                        }
                    }
                    
                    updateMessage("Merging PDFs...");
                    merger.setDestinationFileName(file.getAbsolutePath());
                    merger.mergeDocuments(null);
                    
                    return null;
                } finally {
                    // Clean up temp files
                    tempFiles.forEach(File::delete);
                }
            }
            
            @Override
            protected void succeeded() {
                showInfo("All pay stubs exported successfully!");
            }
            
            @Override
            protected void failed() {
                logger.error("Failed to export all pay stubs", getException());
                showError("Failed to export: " + getException().getMessage());
            }
        };
        
        ProgressDialog<Void> progressDialog = new ProgressDialog<>(exportTask);
        progressDialog.setTitle("Exporting Pay Stubs");
        progressDialog.setHeaderText("Generating PDF files for all drivers...");
        progressDialog.initModality(Modality.APPLICATION_MODAL);
        
        executorService.submit(exportTask);
        progressDialog.showAndWait();
    }

    private void generatePDF(File file, Employee driver, LocalDate weekStart) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float margin = 50;
                float yPosition = page.getMediaBox().getHeight() - margin;
                float width = page.getMediaBox().getWidth() - 2 * margin;
                
                // Header
                contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f); // Light blue
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(companyNameField.getText());
                contentStream.endText();
                
                yPosition -= 30;
                contentStream.setNonStrokingColor(66f/255f, 66f/255f, 66f/255f); // Dark gray
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("EARNINGS STATEMENT");
                contentStream.endText();
                
                // Draw line
                yPosition -= 20;
                contentStream.setStrokingColor(200f/255f, 200f/255f, 200f/255f);
                contentStream.setLineWidth(1);
                contentStream.moveTo(margin, yPosition);
                contentStream.lineTo(margin + width, yPosition);
                contentStream.stroke();
                
                // Employee info
                yPosition -= 30;
                contentStream.setNonStrokingColor(0, 0, 0);
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Employee Information");
                contentStream.endText();
                
                yPosition -= 20;
                LocalDate weekEnd = weekStart.plusDays(6);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                
                String[][] info = {
                    {"Employee:", driver.getName()},
                    {"Employee ID:", String.valueOf(driver.getId())},
                    {"Truck/Unit:", driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A"},
                    {"Pay Period:", weekStart.format(formatter) + " - " + weekEnd.format(formatter)},
                    {"Pay Date:", LocalDate.now().format(formatter)}
                };
                
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                for (String[] row : info) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText(row[0]);
                    contentStream.endText();
                    
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + 150, yPosition);
                    contentStream.showText(row[1]);
                    contentStream.endText();
                    
                    yPosition -= 18;
                }
                
                // Get payroll data
                PayrollCalculator.PayrollRow payrollRow = summaryRows.stream()
                    .filter(r -> r.driverName.equals(driver.getName()))
                    .findFirst()
                    .orElse(null);
                
                if (payrollRow != null) {
                    // Earnings section
                    yPosition -= 20;
                    contentStream.setNonStrokingColor(46f/255f, 125f/255f, 50f/255f); // Green
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("EARNINGS");
                    contentStream.endText();
                    
                    yPosition -= 20;
                    contentStream.setNonStrokingColor(0, 0, 0);
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    
                    String[][] earnings = {
                        {"Gross Pay:", String.format("$%,.2f", payrollRow.gross)},
                        {"Load Count:", String.valueOf(payrollRow.loadCount)},
                        {"Service Fee:", String.format("($%,.2f)", Math.abs(payrollRow.serviceFee))},
                        {"Driver Pay:", String.format("$%,.2f", payrollRow.driverPay)}
                    };
                    
                    for (String[] row : earnings) {
                        addPDFRow(contentStream, margin, yPosition, row[0], row[1]);
                        yPosition -= 18;
                    }
                    
                    // Deductions section
                    yPosition -= 20;
                    contentStream.setNonStrokingColor(211f/255f, 47f/255f, 47f/255f); // Red
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("DEDUCTIONS");
                    contentStream.endText();
                    
                    yPosition -= 20;
                    contentStream.setNonStrokingColor(0, 0, 0);
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    
                    String[][] deductions = {
                        {"Fuel:", String.format("$%,.2f", Math.abs(payrollRow.fuel))},
                        {"Recurring Fees:", String.format("$%,.2f", Math.abs(payrollRow.recurringFees))},
                        {"Advance Repayments:", String.format("$%,.2f", Math.abs(payrollRow.advanceRepayments))},
                        {"Escrow Deposits:", String.format("$%,.2f", Math.abs(payrollRow.escrowDeposits))},
                        {"Other Deductions:", String.format("$%,.2f", Math.abs(payrollRow.otherDeductions))}
                    };
                    
                    for (String[] row : deductions) {
                        addPDFRow(contentStream, margin, yPosition, row[0], row[1]);
                        yPosition -= 18;
                    }
                    
                    // Total deductions
                    double totalDeductions = Math.abs(payrollRow.fuel) + Math.abs(payrollRow.recurringFees) + 
                                           Math.abs(payrollRow.advanceRepayments) + Math.abs(payrollRow.escrowDeposits) + 
                                           Math.abs(payrollRow.otherDeductions);
                    
                    yPosition -= 5;
                    contentStream.setStrokingColor(200f/255f, 200f/255f, 200f/255f);
                    contentStream.moveTo(margin + 250, yPosition);
                    contentStream.lineTo(margin + width, yPosition);
                    contentStream.stroke();
                    
                    yPosition -= 18;
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                    addPDFRow(contentStream, margin, yPosition, "Total Deductions:", String.format("$%,.2f", totalDeductions));
                    
                    // Reimbursements (if any)
                    if (payrollRow.reimbursements > 0) {
                        yPosition -= 30;
                        contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f); // Blue
                        contentStream.beginText();
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                        contentStream.newLineAtOffset(margin, yPosition);
                        contentStream.showText("REIMBURSEMENTS");
                        contentStream.endText();
                        
                        yPosition -= 20;
                        contentStream.setNonStrokingColor(0, 0, 0);
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                        addPDFRow(contentStream, margin, yPosition, "Total Reimbursements:", 
                                String.format("$%,.2f", payrollRow.reimbursements));
                    }
                    
                    // Advances Given (if any)
                    if (payrollRow.advancesGiven > 0) {
                        yPosition -= 30;
                        contentStream.setNonStrokingColor(255f/255f, 152f/255f, 0f/255f); // Orange
                        contentStream.beginText();
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                        contentStream.newLineAtOffset(margin, yPosition);
                        contentStream.showText("ADVANCES");
                        contentStream.endText();
                        
                        yPosition -= 20;
                        contentStream.setNonStrokingColor(0, 0, 0);
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                        addPDFRow(contentStream, margin, yPosition, "Advances Given This Week:", 
                                String.format("$%,.2f", payrollRow.advancesGiven));
                    }
                    
                    // Net Pay
                    yPosition -= 40;
                    contentStream.setStrokingColor(46f/255f, 125f/255f, 50f/255f);
                    contentStream.setLineWidth(2);
                    contentStream.moveTo(margin, yPosition);
                    contentStream.lineTo(margin + width, yPosition);
                    contentStream.stroke();
                    
                    yPosition -= 25;
                    contentStream.setNonStrokingColor(payrollRow.netPay >= 0 ? 27f/255f : 211f/255f, 
                                                     payrollRow.netPay >= 0 ? 94f/255f : 47f/255f, 
                                                     payrollRow.netPay >= 0 ? 32f/255f : 47f/255f);
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("NET PAY:");
                    contentStream.endText();
                    
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + 350, yPosition);
                    contentStream.showText(String.format("$%,.2f", payrollRow.netPay));
                    contentStream.endText();
                    
                    // Footer
                    yPosition = margin + 50;
                    contentStream.setNonStrokingColor(117f/255f, 117f/255f, 117f/255f);
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("This is an electronic pay stub. Please retain for your records.");
                    contentStream.endText();
                    
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, yPosition - 15);
                    contentStream.showText("Generated on: " + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")));
                    contentStream.endText();
                }
            }
            
            document.save(file);
        }
    }
    
    private void addPDFRow(PDPageContentStream stream, float x, float y, String label, String value) throws IOException {
        stream.beginText();
        stream.newLineAtOffset(x, y);
        stream.showText(label);
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(x + 350, y);
        stream.showText(value);
        stream.endText();
    }

    private void exportToExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Payroll to Excel");
        fileChooser.setInitialFileName("payroll_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                
                // Write BOM for Excel UTF-8 recognition
                writer.write('\ufeff');
                
                // Write headers
                String headers = "Driver,Truck/Unit,Loads,Gross Pay,Service Fee,Gross After Fee," +
                               "Company Pay,Driver Pay,Fuel,After Fuel,Recurring,Advances Given," +
                               "Advance Repayments,Escrow,Other Deductions,Reimbursements,NET PAY";
                writer.write(headers);
                writer.newLine();
                
                // Write data
                for (PayrollCalculator.PayrollRow row : summaryRows) {
                    String truckUnit = "";
                    Employee emp = allDrivers.stream()
                        .filter(e -> e.getName().equals(row.driverName))
                        .findFirst()
                        .orElse(null);
                    if (emp != null && emp.getTruckUnit() != null) {
                        truckUnit = escapeCSV(emp.getTruckUnit());
                    }
                    
                    String line = String.format("%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                        escapeCSV(row.driverName),
                        truckUnit,
                        row.loadCount,
                        row.gross,
                        row.serviceFee,
                        row.grossAfterServiceFee,
                        row.companyPay,
                        row.driverPay,
                        row.fuel,
                        row.grossAfterFuel,
                        row.recurringFees,
                        row.advancesGiven,
                        row.advanceRepayments,
                        row.escrowDeposits,
                        row.otherDeductions,
                        row.reimbursements,
                        row.netPay
                    );
                    writer.write(line);
                    writer.newLine();
                }
                
                showInfo("Payroll data exported successfully");
            } catch (IOException e) {
                logger.error("Failed to export to CSV", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }
    
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void copyTableToClipboard() {
        StringBuilder sb = new StringBuilder();
        
        // Headers
        sb.append("Driver\tTruck/Unit\tLoads\tGross Pay\tService Fee\tGross After Fee\t");
        sb.append("Company Pay\tDriver Pay\tFuel\tAfter Fuel\tRecurring\tAdvances Given\t");
        sb.append("Advance Repayments\tEscrow\tOther Deductions\tReimbursements\tNET PAY\n");
        
        // Data
        for (PayrollCalculator.PayrollRow row : summaryRows) {
            String truckUnit = "";
            Employee emp = allDrivers.stream()
                .filter(e -> e.getName().equals(row.driverName))
                .findFirst()
                .orElse(null);
            if (emp != null && emp.getTruckUnit() != null) {
                truckUnit = emp.getTruckUnit();
            }
            
            sb.append(String.format("%s\t%s\t%d\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\n",
                row.driverName,
                truckUnit,
                row.loadCount,
                row.gross,
                row.serviceFee,
                row.grossAfterServiceFee,
                row.companyPay,
                row.driverPay,
                row.fuel,
                row.grossAfterFuel,
                row.recurringFees,
                row.advancesGiven,
                row.advanceRepayments,
                row.escrowDeposits,
                row.otherDeductions,
                row.reimbursements,
                row.netPay
            ));
        }
        
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
        
        showInfo("Table copied to clipboard");
    }

    private void showPayrollHistory() {
        Stage historyStage = new Stage();
        historyStage.setTitle("Payroll History");
        historyStage.initModality(Modality.APPLICATION_MODAL);
        historyStage.initStyle(StageStyle.DECORATED);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: #f5f7fa;");
        
        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
                       "-fx-background-radius: 8px;");
        
        Label title = new Label("Payroll History");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);
        
        header.getChildren().add(title);
        
        // Filter controls
        HBox filterBox = new HBox(15);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        filterBox.setPadding(new Insets(10));
        filterBox.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        
        DatePicker fromDate = new DatePicker(LocalDate.now().minusMonths(3));
        DatePicker toDate = new DatePicker(LocalDate.now());
        ComboBox<Employee> driverFilter = new ComboBox<>();
        driverFilter.getItems().add(null);
        driverFilter.getItems().addAll(allDrivers);
        driverFilter.setPromptText("All Drivers");
        driverFilter.setPrefWidth(200);
        
        Button searchBtn = createActionButton("Search", true);
        Button exportHistoryBtn = createSecondaryButton("Export");
        
        filterBox.getChildren().addAll(
            new Label("From:"), fromDate,
            new Label("To:"), toDate,
            new Label("Driver:"), driverFilter,
            searchBtn,
            exportHistoryBtn
        );
        
        // History table
        TableView<PayrollHistoryEntry> historyTable = createHistoryTable();
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        
        // Load initial data
        ObservableList<PayrollHistoryEntry> historyData = FXCollections.observableArrayList();
        loadHistoryData(historyData, fromDate.getValue(), toDate.getValue(), null);
        historyTable.setItems(historyData);
        
        // Search action
        searchBtn.setOnAction(e -> {
            loadHistoryData(historyData, fromDate.getValue(), toDate.getValue(), driverFilter.getValue());
        });
        
        // Export action
        exportHistoryBtn.setOnAction(e -> exportHistory(historyData));
        
        // Summary footer
        HBox summaryBox = new HBox(20);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setAlignment(Pos.CENTER);
        summaryBox.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        
        Label recordCount = new Label("Records: " + historyData.size());
        Label totalGross = new Label();
        Label totalNet = new Label();
        
        historyData.addListener((ListChangeListener<PayrollHistoryEntry>) c -> {
            recordCount.setText("Records: " + historyData.size());
            double gross = historyData.stream().mapToDouble(e -> e.gross).sum();
            double net = historyData.stream().mapToDouble(e -> e.netPay).sum();
            totalGross.setText(String.format("Total Gross: $%,.2f", gross));
            totalNet.setText(String.format("Total Net: $%,.2f", net));
        });
        
        summaryBox.getChildren().addAll(recordCount, new Separator(javafx.geometry.Orientation.VERTICAL),
                                       totalGross, new Separator(javafx.geometry.Orientation.VERTICAL),
                                       totalNet);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> historyStage.close());
        
        buttonBox.getChildren().add(closeBtn);
        
        root.getChildren().addAll(header, filterBox, historyTable, summaryBox, buttonBox);
        
        Scene scene = new Scene(root, 1200, 700);
        historyStage.setScene(scene);
        historyStage.showAndWait();
    }

    private TableView<PayrollHistoryEntry> createHistoryTable() {
        TableView<PayrollHistoryEntry> table = new TableView<>();
        table.setStyle("-fx-background-color: white;");
        
        TableColumn<PayrollHistoryEntry, String> dateCol = new TableColumn<>("Week Starting");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))));
        dateCol.setPrefWidth(120);
        
        TableColumn<PayrollHistoryEntry, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().driverName));
        driverCol.setPrefWidth(150);
        
        TableColumn<PayrollHistoryEntry, String> truckCol = new TableColumn<>("Truck/Unit");
        truckCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().truckUnit));
        truckCol.setPrefWidth(100);
        
        TableColumn<PayrollHistoryEntry, Number> loadsCol = new TableColumn<>("Loads");
        loadsCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().loadCount));
        loadsCol.setPrefWidth(80);
        loadsCol.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<PayrollHistoryEntry, Number> grossCol = new TableColumn<>("Gross");
        grossCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().gross));
        grossCol.setCellFactory(col -> createCurrencyCell());
        grossCol.setPrefWidth(100);
        
        TableColumn<PayrollHistoryEntry, Number> deductionsCol = new TableColumn<>("Deductions");
        deductionsCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().totalDeductions));
        deductionsCol.setCellFactory(col -> createCurrencyCell());
        deductionsCol.setPrefWidth(100);
        
        TableColumn<PayrollHistoryEntry, Number> netCol = new TableColumn<>("Net Pay");
        netCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().netPay));
        netCol.setCellFactory(col -> {
            return new TableCell<PayrollHistoryEntry, Number>() {
                @Override
                protected void updateItem(Number value, boolean empty) {
                    super.updateItem(value, empty);
                    if (empty || value == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        double amount = value.doubleValue();
                        setText(String.format("$%,.2f", amount));
                        setAlignment(Pos.CENTER_RIGHT);
                        setStyle("-fx-font-weight: bold;");
                        setTextFill(amount >= 0 ? Color.web("#2E7D32") : Color.web("#D32F2F"));
                    }
                }
            };
        });
        netCol.setPrefWidth(120);
        
        TableColumn<PayrollHistoryEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().locked ? "🔒 Locked" : "🔓 Open"));
        statusCol.setPrefWidth(100);
        statusCol.setStyle("-fx-alignment: CENTER;");
        
        table.getColumns().addAll(dateCol, driverCol, truckCol, loadsCol, grossCol, 
                                 deductionsCol, netCol, statusCol);
        
        // Enable sorting
        table.getSortOrder().add(dateCol);
        dateCol.setSortType(TableColumn.SortType.DESCENDING);
        
        return table;
    }

    private TableCell<PayrollHistoryEntry, Number> createCurrencyCell() {
        return new TableCell<PayrollHistoryEntry, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", value.doubleValue()));
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        };
    }

    private void loadHistoryData(ObservableList<PayrollHistoryEntry> data, 
                                LocalDate from, LocalDate to, Employee driver) {
        data.clear();
        
        // In production, load from database
        // For now, generate sample data based on current calculations
        Task<List<PayrollHistoryEntry>> loadTask = new Task<List<PayrollHistoryEntry>>() {
            @Override
            protected List<PayrollHistoryEntry> call() throws Exception {
                List<PayrollHistoryEntry> entries = new ArrayList<>();
                LocalDate current = from;
                
                while (!current.isAfter(to)) {
                    List<Employee> drivers = driver != null ? List.of(driver) : new ArrayList<>(allDrivers);
                    List<PayrollCalculator.PayrollRow> rows = calculator.calculatePayrollRows(
                        drivers, current, current.plusDays(6));
                    
                    for (PayrollCalculator.PayrollRow row : rows) {
                        Employee emp = allDrivers.stream()
                            .filter(e -> e.getName().equals(row.driverName))
                            .findFirst()
                            .orElse(null);
                        
                        String truckUnit = emp != null && emp.getTruckUnit() != null ? emp.getTruckUnit() : "";
                        
                        double totalDeductions = Math.abs(row.fuel) + Math.abs(row.recurringFees) + 
                                               Math.abs(row.advanceRepayments) + Math.abs(row.escrowDeposits) + 
                                               Math.abs(row.otherDeductions);
                        
                        String driverId = emp != null ? String.valueOf(emp.getId()) : "0";
                        boolean isLocked = lockedWeeks.getOrDefault(driverId, Collections.emptySet())
                                                     .contains(current) ||
                                         lockedWeeks.getOrDefault("ALL", Collections.emptySet())
                                                     .contains(current);
                        
                        entries.add(new PayrollHistoryEntry(
                            current, row.driverName, truckUnit, row.loadCount,
                            row.gross, totalDeductions, row.netPay, isLocked
                        ));
                    }
                    
                    current = current.plusWeeks(1);
                }
                
                return entries;
            }
        };
        
        loadTask.setOnSucceeded(e -> data.addAll(loadTask.getValue()));
        loadTask.setOnFailed(e -> {
            logger.error("Failed to load history", loadTask.getException());
            showError("Failed to load history: " + loadTask.getException().getMessage());
        });
        
        executorService.submit(loadTask);
    }

    private void exportHistory(ObservableList<PayrollHistoryEntry> data) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Payroll History");
        fileChooser.setInitialFileName("payroll_history_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                
                writer.write('\ufeff'); // BOM for Excel
                writer.write("Week Starting,Driver,Truck/Unit,Loads,Gross,Deductions,Net Pay,Status");
                writer.newLine();
                
                for (PayrollHistoryEntry entry : data) {
                    writer.write(String.format("%s,%s,%s,%d,%.2f,%.2f,%.2f,%s",
                        entry.date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                        escapeCSV(entry.driverName),
                        escapeCSV(entry.truckUnit),
                        entry.loadCount,
                        entry.gross,
                        entry.totalDeductions,
                        entry.netPay,
                        entry.locked ? "Locked" : "Open"
                    ));
                    writer.newLine();
                }
                
                showInfo("History exported successfully");
            } catch (IOException e) {
                logger.error("Failed to export history", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }

    @Override
    public void onEmployeeDataChanged(List<Employee> currentList) {
        logger.info("Employee data changed notification received with {} employees", currentList.size());
        refreshDrivers(currentList);
        Platform.runLater(this::autoCalculatePayroll);
    }
    
    @Override
    public void onLoadDataChanged() {
        logger.info("Load data changed notification received");
        Platform.runLater(this::autoCalculatePayroll);
    }
    
    @Override
    public void onFuelDataChanged() {
        logger.info("Fuel data changed notification received");
        Platform.runLater(this::autoCalculatePayroll);
    }

    private void refreshDrivers(List<Employee> drivers) {
        Platform.runLater(() -> {
            allDrivers.setAll(drivers);
            filteredDrivers.setAll(drivers);
            
            Employee currentSelection = driverBox.getValue();
            
            driverBox.getItems().clear();
            driverBox.getItems().add(null);
            driverBox.getItems().addAll(filteredDrivers);
            
            if (currentSelection != null && drivers.contains(currentSelection)) {
                driverBox.setValue(currentSelection);
            }
            
            setupDriverComboBox();
        });
    }

    private void setupDriverComboBox() {
        driverBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (e == null || empty) {
                    setText("All Drivers");
                } else {
                    String display = e.getName();
                    if (e.getTruckUnit() != null && !e.getTruckUnit().isEmpty()) {
                        display += " (" + e.getTruckUnit() + ")";
                    }
                    setText(display);
                }
            }
        });
        
        driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (e == null || empty) {
                    setText("All Drivers");
                } else {
                    String display = e.getName();
                    if (e.getTruckUnit() != null && !e.getTruckUnit().isEmpty()) {
                        display += " (" + e.getTruckUnit() + ")";
                    }
                    setText(display);
                }
            }
        });
    }

    private void updateWeekDatePickers() {
        Integer year = yearBox.getValue();
        Integer week = weekBox.getValue();
        if (year != null && week != null) {
            LocalDate start = getWeekStart(year, week);
            LocalDate end = start.plusDays(6);
            weekStartPicker.setValue(start);
            weekEndPicker.setValue(end);
        }
    }

    private void calculateAndDisplayPayroll() {
        summaryRows.clear();
        loadsRows.clear();
        fuelRows.clear();

        LocalDate start = weekStartPicker.getValue();
        LocalDate end = weekEndPicker.getValue();
        Employee selectedDriver = driverBox.getValue();

        if (start == null || end == null) return;

        List<Employee> drivers = (selectedDriver != null) ? 
            List.of(selectedDriver) : new ArrayList<>(allDrivers);
            
        List<PayrollCalculator.PayrollRow> rows = calculator.calculatePayrollRows(drivers, start, end);
        summaryRows.setAll(rows);

        // Update summary totals
        Map<String, Double> totals = calculator.calculateTotals(rows);
        
        for (Map.Entry<String, Label> entry : summaryLabels.entrySet()) {
            String key = entry.getKey();
            Label label = entry.getValue();
            double value = totals.getOrDefault(key, 0.0);
            label.setText(String.format("$%,.2f", value));
            
            // Color coding
            if (key.equals("gross") || key.equals("grossAfterServiceFee") || 
                key.equals("companyPay") || key.equals("driverPay") || 
                key.equals("reimbursements")) {
                label.setTextFill(Color.web("#2e7d32"));
            } else if (key.equals("serviceFee") || key.equals("fuel") || 
                       key.equals("recurringFees") || key.equals("advanceRepayments") || 
                       key.equals("escrowDeposits") || key.equals("otherDeductions")) {
                label.setTextFill(Color.web("#d32f2f"));
            }
        }
        
        double netPay = totals.getOrDefault("netPay", 0.0);
        netPayLabel.setText(String.format("NET PAY: $%,.2f", netPay));
        netPayLabel.setTextFill(netPay >= 0 ? Color.web("#1565C0") : Color.web("#d32f2f"));

        // Populate loads and fuel tabs
        for (PayrollCalculator.PayrollRow row : rows) {
            loadsRows.addAll(row.loads);
            fuelRows.addAll(row.fuels);
        }
        
        updateLockStatus();
    }

    private LocalDate getWeekStart(int year, int week) {
        LocalDate jan4 = LocalDate.of(year, 1, 4);
        return jan4.with(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(java.time.DayOfWeek.MONDAY);
    }

    private List<Integer> yearRange() {
        int thisYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = thisYear - 5; y <= thisYear + 1; y++) years.add(y);
        return years;
    }

    private List<Integer> weekNumbers() {
        List<Integer> weeks = new ArrayList<>();
        for (int i = 1; i <= 53; i++) weeks.add(i);
        return weeks;
    }

	private void loadLockedWeeks() {
		File file = new File(LOCKED_WEEKS_FILE);
		if (file.exists()) {
			try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
				@SuppressWarnings("unchecked")
				Map<String, Set<LocalDate>> loaded = (Map<String, Set<LocalDate>>) ois.readObject();
				lockedWeeks.putAll(loaded);
			} catch (Exception e) {
				logger.error("Failed to load locked weeks", e);
			}
		}
	}

    private void saveLockedWeeks() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(LOCKED_WEEKS_FILE))) {
            oos.writeObject(lockedWeeks);
        } catch (IOException e) {
            logger.error("Failed to save locked weeks", e);
        }
    }

    /**
     * Expose immutable view of the payroll summary rows for other tabs.
     */
    public javafx.collections.ObservableList<PayrollCalculator.PayrollRow> getSummaryRows() {
        return javafx.collections.FXCollections.unmodifiableObservableList(summaryRows);
    }

    /**
     * Get summary statistics for the currently calculated payroll.
     */
    public PayrollSummaryTable.PayrollSummaryStats getSummaryStats() {
        return summaryTable.getSummaryStats();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initStyle(StageStyle.DECORATED);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("An error occurred");
        alert.setContentText(message);
        alert.initStyle(StageStyle.DECORATED);
        alert.showAndWait();
    }

}