package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeesTab;
import com.company.payroll.employees.PercentageConfigurationDialog;
// Old percentage history imports removed - using PaymentMethodHistory instead
import com.company.payroll.employees.PaymentMethodConfigurationDialog;
import com.company.payroll.fuel.FuelTransaction;
import com.company.payroll.fuel.FuelTransactionDAO;
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
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import com.company.payroll.payroll.CheckBoxListCell;
import com.company.payroll.payroll.CheckListView;
import com.company.payroll.payroll.PayrollHistoryEntry;
import com.company.payroll.payroll.ProgressDialog;
import com.company.payroll.payroll.ModernButtonStyles;
import com.company.payroll.util.WindowAware;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.Timer;
import java.util.TimerTask;

/**
 * PayrollTab - Main UI component for payroll management
 * Provides comprehensive payroll calculation, display, and export functionality
 */
public class PayrollTab extends BorderPane implements
        EmployeesTab.EmployeeDataChangeListener,
        LoadsTab.LoadDataChangeListener,
        FuelImportTab.FuelDataChangeListener,
        WindowAware {
    
    private static final Logger logger = LoggerFactory.getLogger(PayrollTab.class);
    
    // Core components
    private final PayrollCalculator calculator;
    private final PayrollRecurring payrollRecurring;
    private final PayrollAdvances payrollAdvances;
    private final PayrollOtherAdjustments payrollOtherAdjustments;
    private final PayrollEscrow payrollEscrow;
    private final LoadDAO loadDAO;
    private final FuelTransactionDAO fuelDAO;
    private QuickActions quickActions;
    
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
    private Button configPercentagesBtn;
    private Button configPaymentMethodsBtn;
    
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
    private Label paidStatusLabel;
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

    private static final String CONFIG_FILE = "payroll_config.properties";
    private static final String COMPANY_NAME_KEY = "company.name";
    
    // Store reference for cleanup
    private final EmployeesTab employeesTab;

    public PayrollTab(EmployeesTab employeesTab, PayrollCalculator calculator, LoadDAO loadDAO, FuelTransactionDAO fuelDAO) {
        this.employeesTab = employeesTab;
        this.calculator = calculator;
        this.loadDAO = loadDAO;
        this.fuelDAO = fuelDAO;
        this.payrollRecurring = new PayrollRecurring();
        this.payrollAdvances = PayrollAdvances.getInstance();
        this.payrollOtherAdjustments = PayrollOtherAdjustments.getInstance();
        this.payrollEscrow = PayrollEscrow.getInstance();
        
        // Initialize QuickActions
        this.quickActions = new QuickActions(this, loadDAO, fuelDAO, executorService);
        
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
        
        // Listen for summaryRows changes to update summary
        summaryRows.addListener((javafx.collections.ListChangeListener<PayrollCalculator.PayrollRow>) c -> updateSummaryFromBackend());
        
        // Cleanup on close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanup();
        }));
    }
	
    private VBox createHeaderSection() {
        VBox headerSection = new VBox(0);
        
        // Company header bar with gradient
        HBox headerBar = new HBox();
        headerBar.setStyle(HEADER_STYLE);
        headerBar.setPadding(new Insets(25, 30, 25, 30));
        headerBar.setAlignment(Pos.CENTER_LEFT);
        
        companyNameField = new TextField();
        companyNameField.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                "-fx-font-size: 32px; -fx-font-weight: bold; -fx-border-color: transparent;");
        companyNameField.setPromptText("Enter Company Name");
        companyNameField.setPrefWidth(600);
        companyNameField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(companyNameField, Priority.ALWAYS);
        loadCompanyName();

        Button saveCompanyBtn = ModernButtonStyles.createSuccessButton("Save");
        saveCompanyBtn.setOnAction(e -> saveCompanyName());
        saveCompanyBtn.setMinWidth(80);
        saveCompanyBtn.setFocusTraversable(false);

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
        
        headerBar.getChildren().addAll(companyNameField, saveCompanyBtn, spacer, statusBox, loadingIndicator);
        
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
        configPercentagesBtn = createActionButton("⚙️ Config %", false);
        configPaymentMethodsBtn = createActionButton("💰 Payment Methods", false);
        
        actionControls.getChildren().addAll(refreshBtn, calcBtn, lockWeekBtn, mergeDocsBtn, configPaymentMethodsBtn);
        // Note: configPercentagesBtn is deprecated - use configPaymentMethodsBtn instead
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
            {"Gross Pay:", "gross", "Total gross pay for all drivers."},
            {"Service Fee:", "serviceFee", "Total service fees deducted."},
            {"Gross after Service Fee:", "grossAfterServiceFee", "Gross pay after service fees."},
            {"Company Pay:", "companyPay", "Total company share."},
            {"Driver Pay:", "driverPay", "Total driver share."},
            {"Fuel Deductions:", "fuel", "Total fuel deductions."},
            {"Gross after Fuel:", "grossAfterFuel", "Gross pay after fuel deductions."},
            {"Recurring Fees:", "recurringFees", "Total recurring deductions."},
            {"Advances Given:", "advancesGiven", "Total advances given this week."},
            {"Advance Repayments:", "advanceRepayments", "Total advance repayments this week."},
            {"Escrow Deposits:", "escrowDeposits", "Total escrow deposits."},
            {"Other Deductions:", "otherDeductions", "Other deductions (adjustments, etc)."},
            {"Reimbursements:", "reimbursements", "Total reimbursements (bonuses, etc)."}
        };

        int row = 0, col = 0;
        for (String[] pair : summaryOrder) {
            Label label = new Label(pair[0]);
            label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #424242;");
            label.setTooltip(new Tooltip(pair[2]));
            Label value = new Label("$0.00");
            value.setStyle("-fx-font-size: 15px; -fx-text-fill: #616161;");
            value.setTooltip(new Tooltip(pair[2]));
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
        netPayLabel.setTooltip(new Tooltip("Total net pay for all drivers."));

        netPayBox.getChildren().add(netPayLabel);

        paidStatusLabel = new Label("");
        paidStatusLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        paidStatusLabel.setTextFill(Color.web("#4CAF50"));
        paidStatusLabel.setVisible(false);

        VBox statusContainer = new VBox(paidStatusLabel);
        statusContainer.setAlignment(Pos.CENTER);
        statusContainer.setPadding(new Insets(5));

        summaryBox.getChildren().addAll(summaryTitle, titleSep, summaryGrid, separator, netPayBox, statusContainer);
        
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
        
        // Create summary table with connection and date support
        // The date will be updated when payroll is calculated
        summaryTable = new PayrollSummaryTable(
            summaryRows,
            calculator.getConnection(),
            null,
            row -> {
                LocalDate ws = weekStartPicker.getValue();
                String id = String.valueOf(row.driverId);
                return lockedWeeks.getOrDefault(id, Collections.emptySet()).contains(ws) ||
                       lockedWeeks.getOrDefault("ALL", Collections.emptySet()).contains(ws);
            }
        );
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
        Button btn = ModernButtonStyles.createOutlineButton(text, "white", "rgba(255,255,255,0.9)");
        // Override for navigation specific styling on header background
        String navStyle = 
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8; " +
            "-fx-padding: 8 16 8 16; " +
            "-fx-font-size: 13px; " +
            "-fx-background-color: rgba(255,255,255,0.15); " +
            "-fx-text-fill: white; " +
            "-fx-border-color: rgba(255,255,255,0.3); " +
            "-fx-border-width: 1; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2);";
        btn.setStyle(navStyle);
        btn.setTooltip(new Tooltip(tooltip));
        
        btn.setOnMouseEntered(e -> {
            String hoverStyle = navStyle.replace("rgba(255,255,255,0.15)", "rgba(255,255,255,0.25)")
                .replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2);", 
                         "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 3);");
            btn.setStyle(hoverStyle);
        });
        
        btn.setOnMouseExited(e -> btn.setStyle(navStyle));
        return btn;
    }

    private Button createActionButton(String text, boolean primary) {
        if (primary) {
            return ModernButtonStyles.createPrimaryButton(text);
        } else {
            return ModernButtonStyles.createSecondaryButton(text);
        }
    }

    private Button createSecondaryButton(String text) {
        return ModernButtonStyles.createOutlineButton(text, ModernButtonStyles.Colors.PRIMARY, ModernButtonStyles.Colors.PRIMARY_HOVER);
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
        configPercentagesBtn.setOnAction(e -> showConfigPercentagesDialog());
        configPaymentMethodsBtn.setOnAction(e -> showPaymentMethodConfigurationDialog());
        
        // Quick action buttons using QuickActions
        printPreviewBtn.setOnAction(e -> quickActions.showPrintPreview(driverBox.getValue(), weekStartPicker.getValue(), summaryRows));
        printPdfBtn.setOnAction(e -> quickActions.exportToPDF(driverBox.getValue(), weekStartPicker.getValue(), summaryRows, getAllDrivers()));
        exportBtn.setOnAction(e -> quickActions.exportToExcel(weekStartPicker.getValue(), summaryRows, getAllDrivers()));
        copyBtn.setOnAction(e -> quickActions.copyTableToClipboard(summaryRows, getAllDrivers()));
        payrollHistoryBtn.setOnAction(e -> quickActions.showPayrollHistory(getAllDrivers(), lockedWeeks.keySet(), this::updateLockStatus));
        mergeDocsBtn.setOnAction(e -> quickActions.showMergeDocumentsDialog(driverBox.getValue(), weekStartPicker.getValue(), loadsRows));
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

    // Deprecated: Old percentage configuration dialog - redirects to new payment method configuration
    private void showConfigPercentagesDialog() {
        logger.info("showConfigPercentagesDialog called - redirecting to payment method configuration");
        showPaymentMethodConfigurationDialog();
    }
    
    /**
     * Shows the payment method configuration dialog
     */
    private void showPaymentMethodConfigurationDialog() {
        logger.info("showPaymentMethodConfigurationDialog called");
        try {
            PaymentMethodConfigurationDialog dialog = new PaymentMethodConfigurationDialog();
            Optional<Boolean> result = dialog.showAndWait();
            
            if (result.isPresent() && result.get()) {
                // Configuration saved successfully, refresh payroll display
                logger.info("Payment method configuration updated successfully");
                
                // Show success message
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Payment Methods Updated");
                alert.setHeaderText(null);
                alert.setContentText("Payment method configuration has been updated successfully. " +
                                   "The changes will be reflected in the next payroll calculation.");
                alert.showAndWait();
                
                // Refresh if we have calculated payroll
                if (!summaryRows.isEmpty()) {
                    calculateAndDisplayPayroll();
                }
            }
        } catch (Exception e) {
            logger.error("Error showing payment method configuration dialog", e);
            showError("Failed to open payment method configuration: " + e.getMessage());
        }
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
    
    /**
     * Proper cleanup method to prevent memory leaks
     */
    public void cleanup() {
        logger.info("Cleaning up PayrollTab resources");
        
        // Remove listener from employeesTab to prevent memory leak
        if (employeesTab != null) {
            employeesTab.removeEmployeeDataChangeListener(this);
        }
        
        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
        
        // Cancel timer
        if (autoCalculateTimer != null) {
            autoCalculateTimer.cancel();
            autoCalculateTimer = null;
        }
        
        // Cancel current task
        if (currentCalculationTask != null && !currentCalculationTask.isDone()) {
            currentCalculationTask.cancel(true);
        }
        
        logger.info("PayrollTab cleanup completed");
    }

    private void toggleWeekLock() {
        logger.info("toggleWeekLock called");
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
            try {
                PayrollHistoryDAO historyDAO = new PayrollHistoryDAO();
                
                if (isLocked) {
                    // Unlocking - remove from database
                    driverLocks.remove(weekStart);
                    historyDAO.deletePayrollHistory(weekStart);
                } else {
                    // Locking - save to database
                    if (!summaryRows.isEmpty()) {
                        driverLocks.add(weekStart);
                        historyDAO.savePayrollHistory(summaryRows, weekStart, true);
                    } else {
                        showError("No payroll data to lock. Please calculate payroll first.");
                        return;
                    }
                }
                saveLockedWeeks();
                updateLockStatus();
                showInfo("Week " + (isLocked ? "unlocked" : "locked") + " successfully");
            } catch (Exception e) {
                logger.error("Error updating payroll history lock status", e);
                e.printStackTrace(); // Print full stack trace
                showError("Lock Error: " + e.getClass().getSimpleName() + " - Failed to " + 
                    (isLocked ? "unlock" : "lock") + " week: " + e.getMessage());
            }
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

        paidStatusLabel.setVisible(isLocked);
        paidStatusLabel.setText(isLocked ? "PAID" : "");

        summaryTable.setPaidChecker(row -> {
            String id = String.valueOf(row.driverId);
            return lockedWeeks.getOrDefault(id, Collections.emptySet()).contains(weekStart) ||
                   lockedWeeks.getOrDefault("ALL", Collections.emptySet()).contains(weekStart);
        });

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
        
        // Update the summary table with the current week's start date
        if (summaryTable != null) {
            summaryTable.updateEffectiveDate(start);
        }
        
        // Check if any driver has configured payment methods for next week
        boolean hasConfiguredPaymentMethods = false;
        if (summaryTable != null) {
            for (PayrollCalculator.PayrollRow row : rows) {
                if (summaryTable.getConfiguredPaymentMethods().containsKey(row.driverId)) {
                    hasConfiguredPaymentMethods = true;
                    break;
                }
            }
        }
        
        // Show notification if payment methods were configured
        if (hasConfiguredPaymentMethods) {
            Platform.runLater(() -> {
                showInfo("💰 Payment Methods Configured - Some drivers have new payment methods configured for the upcoming period.");
            });
        }

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
        // After calculation is complete and summaryRows is updated:
        updateSummaryFromBackend();
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

    // Add after summaryRows is updated (e.g., after payroll calculation)
    private void updateSummaryFromBackend() {
        // Use PayrollCalculator and PayrollOtherAdjustments for all summary fields
        double gross = summaryRows.stream().mapToDouble(r -> r.gross).sum();
        double serviceFee = summaryRows.stream().mapToDouble(r -> r.serviceFee).sum();
        double grossAfterServiceFee = summaryRows.stream().mapToDouble(r -> r.grossAfterServiceFee).sum();
        double companyPay = summaryRows.stream().mapToDouble(r -> r.companyPay).sum();
        double driverPay = summaryRows.stream().mapToDouble(r -> r.driverPay).sum();
        double fuel = summaryRows.stream().mapToDouble(r -> r.fuel).sum();
        double grossAfterFuel = summaryRows.stream().mapToDouble(r -> r.grossAfterFuel).sum();
        double recurringFees = summaryRows.stream().mapToDouble(r -> r.recurringFees).sum();
        double advancesGiven = summaryRows.stream().mapToDouble(r -> r.advancesGiven).sum();
        double advanceRepayments = summaryRows.stream().mapToDouble(r -> r.advanceRepayments).sum();
        double escrowDeposits = summaryRows.stream().mapToDouble(r -> r.escrowDeposits).sum();
        double otherDeductions = summaryRows.stream().mapToDouble(r -> r.otherDeductions).sum();
        double reimbursements = summaryRows.stream().mapToDouble(r -> r.reimbursements).sum();
        double netPay = summaryRows.stream().mapToDouble(r -> r.netPay).sum();
        summaryLabels.get("gross").setText(String.format("$%,.2f", gross));
        summaryLabels.get("serviceFee").setText(String.format("$%,.2f", serviceFee));
        summaryLabels.get("grossAfterServiceFee").setText(String.format("$%,.2f", grossAfterServiceFee));
        summaryLabels.get("companyPay").setText(String.format("$%,.2f", companyPay));
        summaryLabels.get("driverPay").setText(String.format("$%,.2f", driverPay));
        summaryLabels.get("fuel").setText(String.format("$%,.2f", fuel));
        summaryLabels.get("grossAfterFuel").setText(String.format("$%,.2f", grossAfterFuel));
        summaryLabels.get("recurringFees").setText(String.format("$%,.2f", recurringFees));
        summaryLabels.get("advancesGiven").setText(String.format("$%,.2f", advancesGiven));
        summaryLabels.get("advanceRepayments").setText(String.format("$%,.2f", advanceRepayments));
        summaryLabels.get("escrowDeposits").setText(String.format("$%,.2f", escrowDeposits));
        summaryLabels.get("otherDeductions").setText(String.format("$%,.2f", otherDeductions));
        summaryLabels.get("reimbursements").setText(String.format("$%,.2f", reimbursements));
        netPayLabel.setText("NET PAY: " + String.format("$%,.2f", netPay));
    }

    // Call updateSummaryFromBackend() after payroll calculation and whenever summaryRows changes
    // Add error/info dialogs for all failed payroll calculations and adjustment operations
    private void showCalculationError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText("Payroll Calculation Error");
        a.showAndWait();
    }
    private void showCalculationInfo(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText("Payroll Info");
        a.showAndWait();
    }

    private void loadCompanyName() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                String name = props.getProperty(COMPANY_NAME_KEY);
                if (name != null && !name.isEmpty()) {
                    companyNameField.setText(name);
                }
            } catch (IOException e) {
                showError("Failed to load company name from config: " + e.getMessage());
            }
        }
    }

    private void saveCompanyName() {
        Properties props = new Properties();
        props.setProperty(COMPANY_NAME_KEY, companyNameField.getText());
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Payroll Configuration");
            showInfo("Company name saved successfully.");
        } catch (IOException e) {
            showError("Failed to save company name: " + e.getMessage());
        }
    }

    /**
     * Get payroll data for a specific date range.
     * This method will reuse existing calculation if the date range matches,
     * otherwise it will calculate new data without affecting the current UI state.
     */
    public String getCompanyName() {
        return companyNameField.getText().isEmpty() ? "Company" : companyNameField.getText();
    }
    
    public List<PayrollCalculator.PayrollRow> getPayrollDataForDateRange(LocalDate start, LocalDate end) {
        // Check if the requested date range matches the current calculation
        LocalDate currentStart = weekStartPicker.getValue();
        LocalDate currentEnd = weekEndPicker.getValue();
        
        if (currentStart != null && currentEnd != null && 
            currentStart.equals(start) && currentEnd.equals(end) && 
            !summaryRows.isEmpty()) {
            // Return current data if date range matches
            return new ArrayList<>(summaryRows);
        }
        
        // Calculate new data for the requested date range without affecting UI
        List<Employee> allDrivers = new ArrayList<>(this.allDrivers);
        return calculator.calculatePayrollRows(allDrivers, start, end);
    }
    
    /**
     * Get all drivers for use by QuickActions
     */
    public List<Employee> getAllDrivers() {
        return new ArrayList<>(allDrivers);
    }
}