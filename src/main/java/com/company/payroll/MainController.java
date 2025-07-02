package com.company.payroll;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.effect.BlurType;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.Stage;

import com.company.payroll.employees.EmployeesTab;
import com.company.payroll.loads.LoadsTab;
import com.company.payroll.fuel.FuelImportTab;
import com.company.payroll.payroll.PayrollTab;
import com.company.payroll.payroll.PayrollCalculator;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.triumph.MyTriumphTab;
import com.company.payroll.trucks.TrucksTab;
import com.company.payroll.trailers.TrailersTab;
import com.company.payroll.maintenance.MaintenanceTab;
import com.company.payroll.expenses.CompanyExpensesTab;
import com.company.payroll.revenue.RevenueTab;
import com.company.payroll.driver.DriverIncomeTab;
import com.company.payroll.dispatcher.DispatcherTab;
import com.company.payroll.utils.NotificationCenter;
import com.company.payroll.utils.SystemMonitor;
import com.company.payroll.utils.DataRefreshManager;
import com.company.payroll.settings.SettingsManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced MainController that manages the main application TabPane and wires up all feature tabs.
 * Maintains compatibility with existing structure while adding modern UI enhancements.
 * 
 * @author PayrollPro Team
 * @version 4.0.0 (2025-07-02)
 */
public class MainController extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String APP_VERSION = "v4.0.0 Enterprise";
    private static final String BUILD_DATE = "2025-07-02";
    
    private final TabPane tabPane;
    private Label statusLabel;
    private Label clockLabel;
    private Label memoryLabel;
    private ProgressBar memoryBar;
    private Timeline clockTimeline;
    private Timeline memoryTimeline;
    private Label notificationCountLabel;
    private Button notificationBtn;
    
    // Services and utilities
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final SystemMonitor systemMonitor = new SystemMonitor();
    private final DataRefreshManager dataRefreshManager = new DataRefreshManager();
    private final NotificationCenter notificationCenter = NotificationCenter.getInstance();
    private final SettingsManager settingsManager = SettingsManager.getInstance();
    
    // Track current user and session info
    private final String currentUser = System.getProperty("user.name", "mgubran1");
    private final LocalDateTime sessionStartTime = LocalDateTime.now();
    
    /**
     * Main controller constructor
     */
    public MainController() {
        logger.info("Initializing Enhanced MainController - version {}", APP_VERSION);
        
        // Apply enhanced styling
        setStyle("-fx-background-color: #f0f3f4;");
        getStyleClass().add("main-controller");
        
        // Create enhanced header
        VBox header = createEnhancedHeader();
        setTop(header);
        
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("enhanced-tab-pane");

        try {
            // Initialize all tabs with progress tracking
            initializeTabs();
            logger.info("All tabs initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize MainController: {}", e.getMessage(), e);
            showErrorDialog("Initialization Error", 
                          "Failed to initialize application components", 
                          e.getMessage());
            throw new RuntimeException("Failed to initialize application components", e);
        }
        
        // Set the tab pane as center with padding
        VBox centerContainer = new VBox();
        centerContainer.setPadding(new Insets(10));
        centerContainer.getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        setCenter(centerContainer);
        
        // Create enhanced footer
        HBox footer = createEnhancedFooter();
        setBottom(footer);
        
        // Show welcome message with fade-in
        PauseTransition delay = new PauseTransition(Duration.seconds(0.5));
        delay.setOnFinished(event -> updateStatus("Welcome " + currentUser + "! System ready.", true));
        delay.play();
        
        // Set up auto-refresh task
        setupAutoRefreshTask();
        
        // Set up notification checking
        setupNotificationChecking();
        
        // Monitor system resources
        startSystemMonitoring();
        
        logger.info("MainController initialization complete - User: {}", currentUser);
    }
    
    /**
     * Initialize all application tabs
     */
    private void initializeTabs() {
        try {
            // Employees tab (must be created first, for cross-tab event support)
            logger.debug("Creating Employees tab");
            EmployeesTab employeesTabContent = new EmployeesTab();
            Tab employeesTab = new Tab("Employees", employeesTabContent);
            employeesTab.setClosable(false);
            employeesTab.setGraphic(createEnhancedTabIcon("👥", "#3498db"));
            logger.info("Employees tab created successfully");

            // Trucks tab
            logger.debug("Creating Trucks tab");
            TrucksTab trucksTabContent = new TrucksTab();
            Tab trucksTab = new Tab("Trucks", trucksTabContent);
            trucksTab.setClosable(false);
            trucksTab.setGraphic(createEnhancedTabIcon("🚚", "#2c3e50"));
            logger.info("Trucks tab created successfully");

            // Trailers tab
            logger.debug("Creating Trailers tab");
            TrailersTab trailersTabContent = new TrailersTab();
            Tab trailersTab = new Tab("Trailers", trailersTabContent);
            trailersTab.setClosable(false);
            trailersTab.setGraphic(createEnhancedTabIcon("🚛", "#1e3c72"));
            logger.info("Trailers tab created successfully");

            // Maintenance tab
            logger.debug("Creating Maintenance tab");
            MaintenanceTab maintenanceTab = new MaintenanceTab();
            maintenanceTab.setText("Maintenance");
            maintenanceTab.setClosable(false);
            maintenanceTab.setGraphic(createEnhancedTabIcon("🔧", "#34495e"));
            logger.info("Maintenance tab created successfully");

            // Keep unit selectors in sync when trucks or trailers change
            trucksTabContent.addDataChangeListener(maintenanceTab::refreshUnitNumbers);
            trailersTabContent.addDataChangeListener(maintenanceTab::refreshUnitNumbers);

            // Company Expenses tab
            logger.debug("Creating Company Expenses tab");
            CompanyExpensesTab companyExpensesTab = new CompanyExpensesTab();
            companyExpensesTab.setText("Company Expenses");
            companyExpensesTab.setClosable(false);
            companyExpensesTab.setGraphic(createEnhancedTabIcon("💳", "#e74c3c"));
            logger.info("Company Expenses tab created successfully");

            // Loads tab (receives employee data AND trailer data)
            logger.debug("Creating Loads tab with employees and trailers integration");
            LoadsTab loadsTab = new LoadsTab(employeesTabContent, trailersTabContent);
            // LoadsTab already extends Tab, so we add it directly
            loadsTab.setClosable(false);
            loadsTab.setGraphic(createEnhancedTabIcon("📦", "#f39c12"));
            logger.info("Loads tab created successfully with trailer integration");

            // Wire up sync callback from Loads to MyTriumph
            logger.debug("Setting up Loads to MyTriumph sync callback");
            MyTriumphTab myTriumphTab = new MyTriumphTab();
            myTriumphTab.setClosable(false);
            myTriumphTab.setGraphic(createEnhancedTabIcon("📊", "#9b59b6"));
            loadsTab.setSyncCallback(loads -> {
                logger.info("Sync callback triggered with {} loads", loads.size());
                myTriumphTab.syncFromLoads(loads);
            });

            // Fuel import tab
            logger.debug("Creating Fuel Import tab");
            FuelImportTab fuelImportTabContent = new FuelImportTab();
            Tab fuelImportTab = new Tab("Fuel Import", fuelImportTabContent);
            fuelImportTab.setClosable(false);
            fuelImportTab.setGraphic(createEnhancedTabIcon("⛽", "#e67e22"));
            logger.info("Fuel Import tab created successfully");

            // Payroll calculator (needs DAOs)
            logger.debug("Creating DAOs for Payroll calculator");
            EmployeeDAO employeeDAO = new EmployeeDAO();
            LoadDAO loadDAO = new LoadDAO();
            FuelTransactionDAO fuelDAO = new FuelTransactionDAO();
            PayrollCalculator payrollCalculator = new PayrollCalculator(employeeDAO, loadDAO, fuelDAO);
            logger.info("Payroll calculator initialized with all DAOs");

            // Payroll tab (needs EmployeesTab for driver updates, and calculator, and LoadDAO for bonus on load)
            logger.debug("Creating Payroll tab");
            PayrollTab payrollTabContent = new PayrollTab(employeesTabContent, payrollCalculator, loadDAO);
            Tab payrollTab = new Tab("Payroll", payrollTabContent);
            payrollTab.setClosable(false);
            payrollTab.setGraphic(createEnhancedTabIcon("💰", "#27ae60"));
            logger.info("Payroll tab created successfully");

            // Revenue tab
            logger.debug("Creating Revenue tab");
            RevenueTab revenueTab = new RevenueTab();
            revenueTab.setText("Revenue");
            revenueTab.setClosable(false);
            revenueTab.setGraphic(createEnhancedTabIcon("📈", "#3498db"));
            logger.info("Revenue tab created successfully");

            // Driver Income tab - NOW WITH REQUIRED PARAMETERS
            logger.debug("Creating Driver Income tab");
            DriverIncomeTab driverIncomeTab = new DriverIncomeTab(employeeDAO, loadDAO, fuelDAO, payrollCalculator);
            driverIncomeTab.setText("Driver Income");
            driverIncomeTab.setClosable(false);
            driverIncomeTab.setGraphic(createEnhancedTabIcon("🚗", "#16a085"));
            logger.info("Driver Income tab created successfully");

            // Enhanced Dispatcher tab
            logger.debug("Creating Enhanced Dispatcher tab");
            DispatcherTab dispatcherTab = new DispatcherTab(employeeDAO, loadDAO);
            dispatcherTab.setGraphic(createEnhancedTabIcon("📍", "#8e44ad"));

            // Set up advanced refresh on selection with loading indicator
            dispatcherTab.setOnSelectionChanged(event -> {
                if (dispatcherTab.isSelected()) {
                    logger.debug("Dispatcher tab selected, refreshing data");
                    updateStatus("Loading dispatcher data...");
                    
                    // Execute refresh in background thread with UI update on completion
                    Task<Void> refreshTask = new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            dispatcherTab.refresh();
                            return null;
                        }
                    };
                    
                    refreshTask.setOnSucceeded(e -> {
                        updateStatus("Dispatcher data loaded - " + 
                                   LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    });
                    
                    refreshTask.setOnFailed(e -> {
                        logger.error("Failed to refresh dispatcher data", refreshTask.getException());
                        updateStatus("Failed to load dispatcher data - Check logs", true);
                    });
                    
                    executorService.submit(refreshTask);
                }
            });
            logger.info("Enhanced Dispatcher tab created successfully");

            // Register PayrollTab as a listener for load data changes
            logger.debug("Registering PayrollTab as LoadDataChangeListener");
            loadsTab.addLoadDataChangeListener(payrollTabContent);
            logger.info("PayrollTab registered as load data change listener");

            // Register PayrollTab as a listener for fuel data changes
            logger.debug("Registering PayrollTab as FuelDataChangeListener");
            fuelImportTabContent.addFuelDataChangeListener(payrollTabContent);
            logger.info("PayrollTab registered as fuel data change listener");

            // Register loads tab to update dispatcher when loads change
            logger.debug("Registering Dispatcher for load updates with debouncing");
            loadsTab.addLoadDataChangeListener(new Runnable() {
                private final PauseTransition debouncer = new PauseTransition(Duration.millis(500));
                
                @Override
                public void run() {
                    logger.debug("Load data changed, updating dispatcher (debounced)");
                    
                    // Reset and restart the timer on each call to avoid excessive updates
                    debouncer.setOnFinished(e -> {
                        if (dispatcherTab.isSelected()) {
                            dispatcherTab.refresh();
                            updateStatus("Dispatcher view updated with new load data");
                        } else {
                            // Just mark it for refresh when selected next
                            dispatcherTab.setNeedsRefresh(true);
                        }
                    });
                    
                    debouncer.playFromStart();
                }
            });

            // Also register employee changes to update dispatcher
            logger.debug("Registering Dispatcher for employee updates with debouncing");
            employeesTabContent.addEmployeeDataChangeListener(list -> {
                logger.debug("Employee data changed, updating dispatcher");
                
                // Only refresh immediately if dispatcher tab is active
                if (dispatcherTab.isSelected()) {
                    dispatcherTab.refresh();
                    updateStatus("Dispatcher view updated with employee changes");
                } else {
                    dispatcherTab.setNeedsRefresh(true);
                }
            });

            // Add notifications from dispatcher to notification center
            dispatcherTab.setNotificationCallback(message -> {
                notificationCenter.addNotification(message);
                updateNotificationCounter();
            });

            // Add to TabPane in UX order with enhanced animation
            logger.debug("Adding all tabs to TabPane");
            tabPane.getTabs().addAll(
                employeesTab,
                trucksTab,
                trailersTab,
                maintenanceTab,
                companyExpensesTab,
                loadsTab,        // Add LoadsTab directly (it is a Tab)
                dispatcherTab,   // Add the new Dispatcher tab
                fuelImportTab,
                payrollTab,
                driverIncomeTab,
                revenueTab,
                myTriumphTab     // Add the new MyTriumph Audit Tab
            );
            logger.info("All {} tabs added to TabPane successfully", tabPane.getTabs().size());

            // Enhanced tab selection handling with animations
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (oldTab != null) {
                    logger.debug("Tab deselected: {}", oldTab.getText());
                }
                if (newTab != null) {
                    logger.info("Tab selected: {}", newTab.getText());
                    updateStatus("Active tab: " + newTab.getText(), false);
                    
                    // Animate tab content
                    if (newTab.getContent() != null) {
                        FadeTransition ft = new FadeTransition(Duration.millis(300), newTab.getContent());
                        ft.setFromValue(0.7);
                        ft.setToValue(1.0);
                        ft.play();
                    }
                    
                    // Update breadcrumb
                    updateBreadcrumb("Home > " + newTab.getText());
                }
            });

            // Select default tab (can be configured in settings)
            tabPane.getSelectionModel().select(0);
            
        } catch (Exception e) {
            logger.error("Failed to initialize tabs: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Update breadcrumb navigation
     */
    private void updateBreadcrumb(String path) {
        // Find and update the breadcrumb label in the subheader
        if (getTop() instanceof VBox) {
            VBox headerContainer = (VBox) getTop();
            if (headerContainer.getChildren().size() > 1 && 
                headerContainer.getChildren().get(1) instanceof HBox) {
                
                HBox subHeader = (HBox) headerContainer.getChildren().get(1);
                if (subHeader.getChildren().size() > 0 && 
                    subHeader.getChildren().get(0) instanceof Label) {
                    
                    Label breadcrumb = (Label) subHeader.getChildren().get(0);
                    breadcrumb.setText(path);
                }
            }
        }
    }
    
    /**
     * Create enhanced header with modern styling
     */
    private VBox createEnhancedHeader() {
        VBox headerContainer = new VBox();
        
        // Main header
        HBox header = new HBox();
        header.setPadding(new Insets(15));
        header.setSpacing(20);
        header.setStyle("-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
                       "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);");
        
        // Company logo with glow effect
        StackPane logoPane = new StackPane();
        Label companyLabel = new Label("PayrollPro™");
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        companyLabel.setTextFill(Color.WHITE);
        Glow glow = new Glow(0.3);
        companyLabel.setEffect(glow);
        
        // Try to load logo image if available
        try {
            Image logoImage = new Image(getClass().getResourceAsStream("/images/payroll_logo.png"));
            if (logoImage != null && !logoImage.isError()) {
                ImageView logoView = new ImageView(logoImage);
                logoView.setFitHeight(32);
                logoView.setPreserveRatio(true);
                logoPane.getChildren().add(logoView);
            } else {
                logoPane.getChildren().add(companyLabel);
            }
        } catch (Exception e) {
            // Fallback to text logo if image cannot be loaded
            logoPane.getChildren().add(companyLabel);
        }
        
        // Version info with build date
        VBox versionBox = new VBox(2);
        versionBox.setAlignment(Pos.CENTER_LEFT);
        
        Label versionLabel = new Label(APP_VERSION);
        versionLabel.setFont(Font.font("Arial", 14));
        versionLabel.setTextFill(Color.web("#B3E5FC"));
        
        Label buildDateLabel = new Label("Build: " + BUILD_DATE);
        buildDateLabel.setFont(Font.font("Arial", 10));
        buildDateLabel.setTextFill(Color.web("#B3E5FC"));
        buildDateLabel.setOpacity(0.8);
        
        versionBox.getChildren().addAll(versionLabel, buildDateLabel);
        
        // User info with role
        VBox userInfo = new VBox(2);
        Label userLabel = new Label("Logged in as:");
        userLabel.setFont(Font.font("Arial", 11));
        userLabel.setTextFill(Color.web("#B3E5FC"));
        
        HBox userBox = new HBox(5);
        userBox.setAlignment(Pos.CENTER_LEFT);
        
        Label userIcon = new Label("👤");
        Label usernameLabel = new Label(currentUser);
        usernameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        usernameLabel.setTextFill(Color.WHITE);
        
        userBox.getChildren().addAll(userIcon, usernameLabel);
        
        Label roleLabel = new Label("Administrator");
        roleLabel.setFont(Font.font("Arial", 11));
        roleLabel.setTextFill(Color.web("#B3E5FC"));
        
        userInfo.getChildren().addAll(userLabel, userBox, roleLabel);
        userInfo.setAlignment(Pos.CENTER_LEFT);
        
        // Clock with day of week
        VBox clockBox = new VBox(2);
        clockBox.setAlignment(Pos.CENTER);
        
        clockLabel = new Label();
        clockLabel.setFont(Font.font("Arial", 14));
        clockLabel.setTextFill(Color.WHITE);
        
        Label dayLabel = new Label();
        dayLabel.setFont(Font.font("Arial", 11));
        dayLabel.setTextFill(Color.web("#B3E5FC"));
        
        clockBox.getChildren().addAll(clockLabel, dayLabel);
        
        // Update clock and day
        updateClock(clockLabel, dayLabel);
        
        // Start clock timer
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock(clockLabel, dayLabel)));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
        
        // Quick action buttons
        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER_RIGHT);
        
        Button refreshBtn = createHeaderButton("🔄", "Refresh All Data");
        refreshBtn.setOnAction(e -> refreshAllData());
        
        Button settingsBtn = createHeaderButton("⚙", "Settings");
        settingsBtn.setOnAction(e -> showSettings());
        
        Button helpBtn = createHeaderButton("❓", "Help & Documentation");
        helpBtn.setOnAction(e -> showHelp());
        
        // Notification button with counter
        StackPane notificationPane = new StackPane();
        notificationBtn = createHeaderButton("🔔", "Notifications");
        notificationBtn.setOnAction(e -> showNotifications());
        
        notificationCountLabel = new Label("0");
        notificationCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        notificationCountLabel.setTextFill(Color.WHITE);
        notificationCountLabel.setStyle("-fx-background-color: #e74c3c; " +
                                      "-fx-background-radius: 10; " + 
                                      "-fx-padding: 2 6 2 6;");
        notificationCountLabel.setTranslateX(8);
        notificationCountLabel.setTranslateY(-8);
        
        updateNotificationCounter();
        
        notificationPane.getChildren().addAll(notificationBtn, notificationCountLabel);
        
        quickActions.getChildren().addAll(refreshBtn, settingsBtn, helpBtn, notificationPane);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(
            logoPane, 
            versionBox, 
            new Separator(Orientation.VERTICAL), 
            userInfo, 
            spacer, 
            clockBox,
            new Separator(Orientation.VERTICAL),
            quickActions
        );
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Sub-header with navigation breadcrumb
        HBox subHeader = new HBox(10);
        subHeader.setPadding(new Insets(8, 15, 8, 15));
        subHeader.setStyle("-fx-background-color: #1976D2;");
        
        Label breadcrumb = new Label("Home");
        breadcrumb.setFont(Font.font("Arial", 12));
        breadcrumb.setTextFill(Color.WHITE);
        breadcrumb.setOpacity(0.9);
        
        // Online status indicator
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        
        Circle statusIndicator = new Circle(6);
        statusIndicator.setFill(Color.LIGHTGREEN);
        statusIndicator.setStroke(Color.WHITE);
        statusIndicator.setStrokeWidth(1);
        
        Label onlineLabel = new Label("Online");
        onlineLabel.setTextFill(Color.WHITE);
        onlineLabel.setOpacity(0.9);
        
        statusBox.getChildren().addAll(statusIndicator, onlineLabel);
        
        subHeader.getChildren().addAll(breadcrumb, spacer2, statusBox);
        
        headerContainer.getChildren().addAll(header, subHeader);
        return headerContainer;
    }
    
    /**
     * Create an enhanced button for header
     */
    private Button createHeaderButton(String icon, String tooltip) {
        Button button = new Button(icon);
        button.setStyle("-fx-background-color: transparent; " +
                       "-fx-text-fill: white; " +
                       "-fx-font-size: 18px; " +
                       "-fx-cursor: hand; " +
                       "-fx-padding: 5 10 5 10;");
        button.setTooltip(new Tooltip(tooltip));
        
        // Hover effects
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + 
                          "-fx-background-color: rgba(255,255,255,0.2); " +
                          "-fx-background-radius: 5;");
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.1);
            st.setToY(1.1);
            st.play();
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace(
                          "-fx-background-color: rgba(255,255,255,0.2); " +
                          "-fx-background-radius: 5;", ""));
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        // Click animation
        button.setOnMousePressed(e -> {
            button.setTranslateY(1);
        });
        
        button.setOnMouseReleased(e -> {
            button.setTranslateY(0);
        });
        
        return button;
    }
    
    /**
     * Create enhanced footer with status and system info
     */
    private HBox createEnhancedFooter() {
        HBox footer = new HBox();
        footer.setPadding(new Insets(10));
        footer.setSpacing(20);
        footer.setStyle("-fx-background-color: #F5F5F5; " +
                       "-fx-border-color: #E0E0E0; " +
                       "-fx-border-width: 2 0 0 0;");
        
        // Status with icon
        HBox statusBox = new HBox(5);
        Label statusIcon = new Label("📌");
        statusLabel = new Label("Initializing system...");
        statusLabel.setFont(Font.font("Arial", 12));
        statusBox.getChildren().addAll(statusIcon, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        
        // Database status with animated indicator
        HBox dbBox = new HBox(5);
        Label dbIcon = new Label("🗄");
        Label dbLabel = new Label("Database: Connected");
        dbLabel.setFont(Font.font("Arial", 12));
        dbLabel.setTextFill(Color.GREEN);
        dbBox.getChildren().addAll(dbIcon, dbLabel);
        dbBox.setAlignment(Pos.CENTER_LEFT);
        
        // Performance metrics
        VBox performanceBox = new VBox(2);
        Label perfLabel = new Label("System Performance");
        perfLabel.setFont(Font.font("Arial", 10));
        perfLabel.setTextFill(Color.GRAY);
        
        memoryBar = new ProgressBar();
        memoryBar.setPrefWidth(150);
        memoryBar.setPrefHeight(10);
        
        performanceBox.getChildren().addAll(perfLabel, memoryBar);
        performanceBox.setAlignment(Pos.CENTER_LEFT);
        
        // Memory info
        memoryLabel = new Label();
        memoryLabel.setFont(Font.font("Arial", 11));
        
        // Update memory info periodically
        memoryTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> updateMemoryInfo()));
        memoryTimeline.setCycleCount(Timeline.INDEFINITE);
        memoryTimeline.play();
        
        // Version and copyright
        Label copyrightLabel = new Label("© 2025 PayrollPro™ - All Rights Reserved");
        copyrightLabel.setFont(Font.font("Arial", 10));
        copyrightLabel.setTextFill(Color.GRAY);
        
        // Session info
        Label sessionLabel = new Label("Session started: " + 
            sessionStartTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        sessionLabel.setFont(Font.font("Arial", 10));
        sessionLabel.setTextFill(Color.GRAY);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Status indicators for services
        HBox serviceStatus = new HBox(10);
        serviceStatus.setAlignment(Pos.CENTER_RIGHT);
        
        Label syncStatus = new Label("Sync: Active");
        syncStatus.setFont(Font.font("Arial", 11));
        syncStatus.setTextFill(Color.GREEN);
        
        Label apiStatus = new Label("API: Connected");
        apiStatus.setFont(Font.font("Arial", 11));
        apiStatus.setTextFill(Color.GREEN);
        
        serviceStatus.getChildren().addAll(syncStatus, apiStatus);
        
        footer.getChildren().addAll(
            statusBox, 
            new Separator(Orientation.VERTICAL),
            dbBox,
            new Separator(Orientation.VERTICAL),
            performanceBox, 
            memoryLabel,
            spacer,
            serviceStatus,
            new Separator(Orientation.VERTICAL),
            sessionLabel,
            new Separator(Orientation.VERTICAL),
            copyrightLabel
        );
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }
    
    /**
     * Create enhanced tab icon with animation effects
     */
    private Label createEnhancedTabIcon(String emoji, String color) {
        Label icon = new Label(emoji);
        icon.setStyle("-fx-font-size: 18px; " +
                     "-fx-text-fill: " + color + "; " +
                     "-fx-effect: dropshadow(gaussian, " + color + "77, 3, 0.5, 0, 0);");
        
        // Add hover effect
        icon.setOnMouseEntered(e -> {
            DropShadow glow = new DropShadow(BlurType.THREE_PASS_BOX, Color.web(color), 10, 0.8, 0, 0);
            icon.setEffect(glow);
            
            ScaleTransition st = new ScaleTransition(Duration.millis(100), icon);
            st.setToX(1.2);
            st.setToY(1.2);
            st.play();
        });
        
        icon.setOnMouseExited(e -> {
            DropShadow shadow = new DropShadow(BlurType.THREE_PASS_BOX, Color.web(color + "77"), 3, 0.5, 0, 0);
            icon.setEffect(shadow);
            
            ScaleTransition st = new ScaleTransition(Duration.millis(100), icon);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        return icon;
    }
    
    /**
     * Update clock display with formatted time and date
     */
    private void updateClock(Label clockLabel, Label dayLabel) {
        LocalDateTime now = LocalDateTime.now();
        String formatted = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
        String day = now.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy"));
        
        clockLabel.setText("🕐 " + formatted);
        dayLabel.setText(day);
    }
    
    /**
     * Update memory usage information
     */
    private void updateMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        double usage = (double) usedMemory / totalMemory;
        memoryBar.setProgress(usage);
        
        // Change color based on usage
        if (usage > 0.8) {
            memoryBar.setStyle("-fx-accent: #e74c3c;");
            memoryLabel.setTextFill(Color.RED);
            
            // Log warning if memory usage is high
            if (usage > 0.9) {
                logger.warn("High memory usage detected: {}%", String.format("%.0f", usage * 100));
            }
        } else if (usage > 0.6) {
            memoryBar.setStyle("-fx-accent: #f39c12;");
            memoryLabel.setTextFill(Color.ORANGE);
        } else {
            memoryBar.setStyle("-fx-accent: #27ae60;");
            memoryLabel.setTextFill(Color.GREEN);
        }
        
        memoryLabel.setText(String.format("Memory: %d MB / %d MB (%.0f%%)", 
                                        usedMemory, totalMemory, usage * 100));
    }
    
    /**
     * Update status bar message
     */
    private void updateStatus(String message, boolean isImportant) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            
            if (isImportant) {
                // Highlight important messages
                statusLabel.setTextFill(Color.web("#2196F3"));
                statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                
                // Fade back to normal after 5 seconds
                Timeline resetStatus = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
                    statusLabel.setTextFill(Color.BLACK);
                    statusLabel.setFont(Font.font("Arial", 12));
                }));
                resetStatus.play();
                
                // Log important messages
                logger.info("Important status update: {}", message);
            }
        }
        logger.debug("Status updated: {}", message);
    }
    
    /**
     * Update status with standard importance
     */
    private void updateStatus(String message) {
        updateStatus(message, false);
    }
    
    /**
     * Update notification counter
     */
    private void updateNotificationCounter() {
        int count = notificationCenter.getUnreadCount();
        
        Platform.runLater(() -> {
            notificationCountLabel.setText(String.valueOf(count));
            notificationCountLabel.setVisible(count > 0);
            
            // Animate notification count change
            if (count > 0) {
                ScaleTransition pulse = new ScaleTransition(Duration.millis(200), notificationCountLabel);
                pulse.setFromX(0.8);
                pulse.setFromY(0.8);
                pulse.setToX(1.2);
                pulse.setToY(1.2);
                pulse.setCycleCount(2);
                pulse.setAutoReverse(true);
                pulse.play();
            }
        });
    }
    
    /**
     * Refresh all data in the application
     */
    private void refreshAllData() {
        logger.info("Refreshing all application data");
        updateStatus("Refreshing all data...", true);
        
        // Use DataRefreshManager to coordinate the refresh
        Task<Void> refreshTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                dataRefreshManager.refreshAll(getCurrentTab());
                return null;
            }
        };
        
        refreshTask.setOnSucceeded(e -> {
            updateStatus("All data refreshed successfully");
        });
        
        refreshTask.setOnFailed(e -> {
            logger.error("Failed to refresh all data", refreshTask.getException());
            updateStatus("Data refresh failed - see log for details", true);
        });
        
        // Run in background
        executorService.submit(refreshTask);
    }
    
    /**
     * Get the currently selected tab
     */
    private Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }
    
    /**
     * Setup auto-refresh background task
     */
    private void setupAutoRefreshTask() {
        // Check for settings about auto-refresh
        boolean enableAutoRefresh = settingsManager.getBooleanSetting("enableAutoRefresh", true);
        
        if (enableAutoRefresh) {
            int refreshIntervalMinutes = settingsManager.getIntSetting("refreshIntervalMinutes", 15);
            
            Timeline autoRefreshTimeline = new Timeline(
                new KeyFrame(Duration.minutes(refreshIntervalMinutes), e -> {
                    logger.debug("Auto-refresh triggered");
                    
                    // Only refresh if enabled in settings and app is not busy
                    if (settingsManager.getBooleanSetting("enableAutoRefresh", true) &&
                        !progressIndicator.isVisible()) {
                        
                        // Get current tab and refresh only that one
                        Tab currentTab = getCurrentTab();
                        if (currentTab != null) {
                            updateStatus("Auto-refreshing " + currentTab.getText() + " data...");
                            dataRefreshManager.refreshTab(currentTab);
                        }
                    }
                })
            );
            
            autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
            autoRefreshTimeline.play();
            
            logger.info("Auto-refresh scheduled every {} minutes", refreshIntervalMinutes);
        } else {
            logger.info("Auto-refresh disabled in settings");
        }
    }
    
    /**
     * Setup notification checking
     */
    private void setupNotificationChecking() {
        // Check for new notifications periodically
        Timeline notificationTimeline = new Timeline(
            new KeyFrame(Duration.seconds(30), e -> {
                updateNotificationCounter();
            })
        );
        
        notificationTimeline.setCycleCount(Timeline.INDEFINITE);
        notificationTimeline.play();
        
        // Register for notification updates
        notificationCenter.addNotificationListener(event -> updateNotificationCounter());
    }
    
    /**
     * Start system resource monitoring
     */
    private void startSystemMonitoring() {
        systemMonitor.startMonitoring();
        
        systemMonitor.addAlertListener(alert -> {
            if (alert.isCritical()) {
                logger.warn("System monitor critical alert: {}", alert.getMessage());
                Platform.runLater(() -> {
                    updateStatus("System alert: " + alert.getMessage(), true);
                    notificationCenter.addNotification(
                        "System Alert", 
                        alert.getMessage(), 
                        NotificationCenter.NotificationType.WARNING
                    );
                    updateNotificationCounter();
                });
            }
        });
    }
    
    /**
     * Show settings dialog
     */
    private void showSettings() {
        updateStatus("Opening settings...");
        // In real implementation, show settings dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Settings");
        alert.setHeaderText("Application Settings");
        alert.setContentText("Settings dialog would be shown here.");
        alert.showAndWait();
    }
    
    /**
     * Show help dialog
     */
    private void showHelp() {
        updateStatus("Opening help documentation...");
        // In real implementation, show help dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Help & Documentation");
        alert.setHeaderText("PayrollPro™ Help");
        alert.setContentText("Help documentation would be shown here.");
        alert.showAndWait();
    }
    
    /**
     * Show notifications panel
     */
    private void showNotifications() {
        updateStatus("Opening notifications...");
        // In real implementation, show notifications dialog
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Notifications");
        alert.setHeaderText("System Notifications");
        alert.setContentText("You have " + notificationCenter.getUnreadCount() + 
                           " unread notifications.\n\nNotifications panel would be shown here.");
        alert.showAndWait();
        
        // Mark notifications as read
        notificationCenter.markAllAsRead();
        updateNotificationCounter();
    }
    
    /**
     * Show error dialog
     */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Get tab pane reference
     */
    public TabPane getTabPane() {
        logger.debug("getTabPane() called");
        return tabPane;
    }
    
    /**
     * Shutdown and cleanup
     */
    public void shutdown() {
        logger.info("Shutting down MainController");
        
        // Stop timelines
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        if (memoryTimeline != null) {
            memoryTimeline.stop();
        }
        
        // Stop system monitoring
        systemMonitor.stopMonitoring();
        
        // Shutdown executor service
        try {
            logger.debug("Shutting down executor service");
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Executor service shutdown interrupted", e);
            executorService.shutdownNow();
        } finally {
            if (!executorService.isTerminated()) {
                logger.warn("Forcing executor service shutdown");
                executorService.shutdownNow();
            }
        }
        
        // Add any cleanup code here for tabs that need it
        logger.info("MainController shutdown complete - session duration: {} minutes", 
                  java.time.Duration.between(sessionStartTime, LocalDateTime.now()).toMinutes());
    }
    
    /**
     * Simple circle class for status indicators
     */
    private static class Circle extends javafx.scene.shape.Circle {
        public Circle(double radius) {
            super(radius);
        }
    }
}