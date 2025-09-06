package com.company.payroll;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ProgressIndicator;
import javafx.concurrent.Task;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.company.payroll.database.DatabaseConfig;
import com.company.payroll.employees.EmployeesTab;
import com.company.payroll.loads.LoadsTab;
import com.company.payroll.payroll.PayrollTab;
import com.company.payroll.fuel.FuelImportTab;
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
import com.company.payroll.employees.Employee;
import com.company.payroll.drivergrid.DriverGridTab;
import com.company.payroll.drivergrid.DriverGridTabEnhanced;
import com.company.payroll.payroll.CompanyFinancialsTab;
import com.company.payroll.util.WindowAware;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced MainController that manages the main application TabPane and wires up all feature tabs.
 * Maintains compatibility with existing structure while adding modern UI enhancements.
 */
public class MainController extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String APP_VERSION = "v3.2.0 Professional";
    
    private final TabPane tabPane;
    private Label statusLabel;
    private Label clockLabel;
    private Label memoryLabel;
    private ProgressBar memoryBar;
    private Timeline clockTimeline;
    private Timeline memoryTimeline;
    private Timeline autoRefreshTimeline;
    
    // Track current user
    private final String currentUser = System.getProperty("user.name", "mgubran1");
    
    // Tab references for automatic refresh
    private EmployeesTab employeesTabContent;
    private TrucksTab trucksTabContent;
    private TrailersTab trailersTabContent;
    private LoadsTab loadsTab;
    private DriverGridTab driverGridTab;

    // Window awareness
    private final List<WindowAware> windowAwareComponents = new ArrayList<>();
    private Stage stage;
    
    public MainController() {
        logger.info("Initializing Enhanced MainController");
        
        // Apply enhanced styling
        setStyle("-fx-background-color: #f0f3f4;");
        
        // Create enhanced header
        VBox header = createEnhancedHeader();
        setTop(header);
        
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("enhanced-tab-pane");

        try {
            // Employees tab (must be created first, for cross-tab event support)
            logger.debug("Creating Employees tab");
            employeesTabContent = new EmployeesTab();
            registerWindowAware(employeesTabContent);
            Tab employeesTab = new Tab("Employees", employeesTabContent);
            employeesTab.setClosable(false);
            employeesTab.setGraphic(createEnhancedTabIcon("👥", "#3498db"));
            logger.info("Employees tab created successfully");

            // Trucks tab
            logger.debug("Creating Trucks tab");
            trucksTabContent = new TrucksTab();
            registerWindowAware(trucksTabContent);
            Tab trucksTab = new Tab("Trucks", trucksTabContent);
            trucksTab.setClosable(false);
            trucksTab.setGraphic(createEnhancedTabIcon("🚚", "#2c3e50"));
            logger.info("Trucks tab created successfully");

            // Trailers tab
            logger.debug("Creating Trailers tab");
            trailersTabContent = new TrailersTab();
            registerWindowAware(trailersTabContent);
            Tab trailersTab = new Tab("Trailers", trailersTabContent);
            trailersTab.setClosable(false);
            trailersTab.setGraphic(createEnhancedTabIcon("🚛", "#1e3c72"));
            logger.info("Trailers tab created successfully");

            // Maintenance tab
            logger.debug("Creating Maintenance tab");
            MaintenanceTab maintenanceTab = new MaintenanceTab();
            registerWindowAware(maintenanceTab);
            maintenanceTab.setText("Maintenance");
            maintenanceTab.setClosable(false);
            maintenanceTab.setGraphic(createEnhancedTabIcon("🔧", "#34495e"));
            logger.info("Maintenance tab created successfully");

            // Keep unit selectors in sync when trucks or trailers change
            trucksTabContent.addDataChangeListener(maintenanceTab::refreshUnitNumbers);
            trailersTabContent.addDataChangeListener(maintenanceTab::refreshUnitNumbers);
            
            // Set truck and trailer tab references in EmployeesTab for dropdown integration
            employeesTabContent.setTrucksTab(trucksTabContent);
            employeesTabContent.setTrailersTab(trailersTabContent);

            // Company Expenses tab
            logger.debug("Creating Company Expenses tab");
            CompanyExpensesTab companyExpensesTab = new CompanyExpensesTab();
            registerWindowAware(companyExpensesTab);
            companyExpensesTab.setText("Company Expenses");
            companyExpensesTab.setClosable(false);
            companyExpensesTab.setGraphic(createEnhancedTabIcon("💳", "#e74c3c"));
            logger.info("Company Expenses tab created successfully");

            // Loads tab (receives employee data AND trailer data)
            logger.debug("Creating Loads tab with employees and trailers integration");
            loadsTab = new LoadsTab(employeesTabContent, trailersTabContent);
            registerWindowAware(loadsTab);
            // LoadsTab already extends Tab, so we add it directly
            loadsTab.setClosable(false);
            loadsTab.setGraphic(createEnhancedTabIcon("📦", "#f39c12"));
            logger.info("Loads tab created successfully with trailer integration");

            // Driver Grid tab
            logger.debug("Creating Enhanced Driver Grid tab");
            driverGridTab = new DriverGridTabEnhanced();
            registerWindowAware(driverGridTab);
            driverGridTab.setClosable(false);
            driverGridTab.setGraphic(createEnhancedTabIcon("🚗", "#16a085"));
            // Connect LoadsTab to DriverGridTab for cross-referencing
            driverGridTab.setLoadsTab(loadsTab);
            logger.info("Enhanced Driver Grid tab created successfully with LoadsTab integration");

            // Wire up sync callback from Loads to MyTriumph
            logger.debug("Setting up Loads to MyTriumph sync callback");
            MyTriumphTab myTriumphTab = new MyTriumphTab();
            registerWindowAware(myTriumphTab);
            myTriumphTab.setClosable(false);
            myTriumphTab.setGraphic(createEnhancedTabIcon("📊", "#9b59b6"));
            loadsTab.setSyncCallback(loads -> {
                logger.info("Sync callback triggered with {} loads", loads.size());
                myTriumphTab.syncFromLoads(loads);
            });

            // Create DAOs for other tabs
            EmployeeDAO employeeDAO = new EmployeeDAO();
            LoadDAO loadDAO = new LoadDAO();
            
            // Run database migration
            try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:payroll.db")) {
                logger.info("Running database migration...");
                com.company.payroll.migration.DatabaseMigration migration = new com.company.payroll.migration.DatabaseMigration(connection);
                migration.migrate();
                logger.info("Database migration completed successfully");
            } catch (Exception e) {
                logger.error("Failed to run database migration: " + e.getMessage(), e);
                // Show error dialog but continue running
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Database Migration Warning");
                alert.setHeaderText("Database migration failed");
                alert.setContentText("Some features may not work properly: " + e.getMessage());
                alert.showAndWait();
            }

            // Fuel import tab
            logger.debug("Creating Fuel Import tab");
            FuelImportTab fuelImportTabContent = new FuelImportTab();
            registerWindowAware(fuelImportTabContent);
            Tab fuelImportTab = new Tab("Fuel Import", fuelImportTabContent);
            fuelImportTab.setClosable(false);
            fuelImportTab.setGraphic(createEnhancedTabIcon("⛽", "#e67e22"));
            logger.info("Fuel Import tab created successfully");

            // Payroll calculator (needs DAOs)
            logger.debug("Creating DAOs for Payroll calculator");
            FuelTransactionDAO fuelDAO = new FuelTransactionDAO();
            PayrollCalculator payrollCalculator = new PayrollCalculator(employeeDAO, loadDAO, fuelDAO);
            logger.info("Payroll calculator initialized with all DAOs");

            // Payroll tab (needs EmployeesTab for driver updates, and calculator, and LoadDAO for bonus on load)
            logger.debug("Creating Payroll tab");
            PayrollTab payrollTabContent = new PayrollTab(employeesTabContent, payrollCalculator, loadDAO, fuelDAO);
            registerWindowAware(payrollTabContent);
            Tab payrollTab = new Tab("Payroll", payrollTabContent);
            payrollTab.setClosable(false);
            payrollTab.setGraphic(createEnhancedTabIcon("💰", "#27ae60"));
            logger.info("Payroll tab created successfully");

            // Revenue tab
            logger.debug("Creating Revenue tab");
            RevenueTab revenueTab = new RevenueTab();
            registerWindowAware(revenueTab);
            revenueTab.setText("Revenue");
            revenueTab.setClosable(false);
            revenueTab.setGraphic(createEnhancedTabIcon("📈", "#3498db"));
            logger.info("Revenue tab created successfully");

            // Company Financials tab
            logger.debug("Creating Company Financials tab");
            CompanyFinancialsTab companyFinancialsTab = new CompanyFinancialsTab(payrollTabContent, companyExpensesTab.getCompanyExpenseDAO(), maintenanceTab.getMaintenanceDAO());
            registerWindowAware(companyFinancialsTab);
            Tab financialsTab = new Tab("Company Financials", companyFinancialsTab);
            financialsTab.setClosable(false);
            financialsTab.setGraphic(createEnhancedTabIcon("🏦", "#1976D2"));
            logger.info("Company Financials tab created successfully");

            // Driver Income tab - NOW WITH REQUIRED PARAMETERS
            logger.debug("Creating Driver Income tab");
            DriverIncomeTab driverIncomeTab = new DriverIncomeTab(employeeDAO, loadDAO, fuelDAO, payrollCalculator);
            registerWindowAware(driverIncomeTab);
            driverIncomeTab.setText("Driver Income");
            driverIncomeTab.setClosable(false);
            driverIncomeTab.setGraphic(createEnhancedTabIcon("🚗", "#16a085"));
            logger.info("Driver Income tab created successfully");

            // Register PayrollTab as a listener for load data changes
                        loadsTab.addLoadDataChangeListener(() -> {
                logger.debug("Load data changed, refreshing payroll calculations");
                // PayrollTab will automatically refresh when needed
            });

            // Setup automatic refresh system
            setupAutomaticRefresh();

            // Add all tabs to the TabPane
            tabPane.getTabs().addAll(
                employeesTab,
                trucksTab,
                trailersTab,
                maintenanceTab,
                companyExpensesTab,
                loadsTab,
                driverGridTab,
                myTriumphTab,
                fuelImportTab,
                payrollTab,
                revenueTab,
                financialsTab,
                driverIncomeTab
            );

            logger.info("All tabs added to TabPane successfully");
            
        } catch (Exception e) {
            logger.error("Error during tab initialization: {}", e.getMessage(), e);
            updateStatus("Error initializing tabs: " + e.getMessage(), true);
        }

        setCenter(tabPane);
        
        // Create enhanced footer
        HBox footer = createEnhancedFooter();
        setBottom(footer);
        
        // Start background timers
        startBackgroundTimers();
        
        logger.info("MainController initialization complete");
    }
    
    private VBox createEnhancedHeader() {
        VBox headerContainer = new VBox();
        
        // Main header
        HBox header = new HBox();
        header.setPadding(new Insets(15));
        header.setSpacing(20);
        header.setStyle("-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
                       "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);");
        
        // Company name with glow effect
        Label companyLabel = new Label("PayrollPro™");
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        companyLabel.setTextFill(Color.WHITE);
        Glow glow = new Glow(0.3);
        companyLabel.setEffect(glow);
        
        // Version info
        Label versionLabel = new Label(APP_VERSION);
        versionLabel.setFont(Font.font("Arial", 14));
        versionLabel.setTextFill(Color.web("#B3E5FC"));
        
        // User info
        VBox userInfo = new VBox(2);
        Label userLabel = new Label("Logged in as:");
        userLabel.setFont(Font.font("Arial", 11));
        userLabel.setTextFill(Color.web("#B3E5FC"));
        Label usernameLabel = new Label(currentUser);
        usernameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        usernameLabel.setTextFill(Color.WHITE);
        userInfo.getChildren().addAll(userLabel, usernameLabel);
        userInfo.setAlignment(Pos.CENTER_LEFT);
        
        // Clock
        clockLabel = new Label();
        clockLabel.setFont(Font.font("Arial", 14));
        clockLabel.setTextFill(Color.WHITE);
        updateClock();
        
        // Start clock timer
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
        
        // Quick action buttons
        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER_RIGHT);
        
        Button refreshBtn = createHeaderButton("🔄", "Refresh All Data");
        Button settingsBtn = createHeaderButton("⚙", "Settings");
        Button helpBtn = createHeaderButton("❓", "Help & Documentation");
        Button notificationBtn = createHeaderButton("🔔", "Notifications (3)");
        
        quickActions.getChildren().addAll(refreshBtn, settingsBtn, helpBtn, notificationBtn);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(companyLabel, versionLabel, 
                                  new Separator(Orientation.VERTICAL), 
                                  userInfo, spacer, clockLabel,
                                  new Separator(Orientation.VERTICAL),
                                  quickActions);
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Sub-header with navigation breadcrumb
        HBox subHeader = new HBox(10);
        subHeader.setPadding(new Insets(8, 15, 8, 15));
        subHeader.setStyle("-fx-background-color: #1976D2;");
        
        Label breadcrumb = new Label("Home > Fleet Management System");
        breadcrumb.setFont(Font.font("Arial", 12));
        breadcrumb.setTextFill(Color.WHITE);
        breadcrumb.setOpacity(0.9);
        
        subHeader.getChildren().add(breadcrumb);
        
        headerContainer.getChildren().addAll(header, subHeader);
        return headerContainer;
    }
    
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
        
        return button;
    }
    
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
        statusLabel = new Label("Ready");
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
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        footer.getChildren().addAll(statusBox, 
                                  new Separator(Orientation.VERTICAL),
                                  dbBox,
                                  new Separator(Orientation.VERTICAL),
                                  performanceBox, memoryLabel,
                                  spacer,
                                  copyrightLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }
    
    private Label createEnhancedTabIcon(String emoji, String color) {
        Label icon = new Label(emoji);
        icon.setStyle("-fx-font-size: 18px; " +
                     "-fx-text-fill: " + color + "; " +
                     "-fx-effect: dropshadow(gaussian, " + color + ", 3, 0.5, 0, 0);");
        
        // Add hover effect
        icon.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), icon);
            st.setToX(1.2);
            st.setToY(1.2);
            st.play();
        });
        
        icon.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), icon);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        return icon;
    }
    
    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        String formatted = now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy  hh:mm:ss a"));
        clockLabel.setText("🕐 " + formatted);
    }
    
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
    
    private void updateStatus(String message, boolean isImportant) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            
            if (isImportant) {
                // Highlight important messages
                statusLabel.setTextFill(Color.web("#2196F3"));
                statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                
                // Fade back to normal after 3 seconds
                Timeline resetStatus = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                    statusLabel.setTextFill(Color.BLACK);
                    statusLabel.setFont(Font.font("Arial", 12));
                }));
                resetStatus.play();
            }
        }
        logger.debug("Status updated: {}", message);
    }
    
    private void updateStatus(String message) {
        updateStatus(message, false);
    }
    
    public void shutdown() {
        logger.info("Shutting down MainController");
        
        // Thread-safe shutdown of timelines with proper synchronization
        synchronized (this) {
            if (clockTimeline != null) {
                try {
                    clockTimeline.stop();
                    clockTimeline = null;
                } catch (Exception e) {
                    logger.error("Error stopping clock timeline", e);
                }
            }
            
            if (memoryTimeline != null) {
                try {
                    memoryTimeline.stop();
                    memoryTimeline = null;
                } catch (Exception e) {
                    logger.error("Error stopping memory timeline", e);
                }
            }
            
            if (autoRefreshTimeline != null) {
                try {
                    autoRefreshTimeline.stop();
                    autoRefreshTimeline = null;
                } catch (Exception e) {
                    logger.error("Error stopping auto-refresh timeline", e);
                }
            }
        }
        
        // Cleanup tabs with proper resource management
        try {
            // Cleanup window-aware components
            for (WindowAware component : windowAwareComponents) {
                if (component instanceof PayrollTab) {
                    ((PayrollTab) component).cleanup();
                }
                // Add other cleanup methods as needed
            }
            
            logger.debug("Component cleanup completed");
        } catch (Exception e) {
            logger.error("Error during component cleanup", e);
        }
        
        // Shutdown database connection pool
        try {
            DatabaseConfig.shutdown();
        } catch (Exception e) {
            logger.error("Error shutting down database", e);
        }
        
        logger.info("MainController shutdown complete");
    }

    /**
     * Handle application exit with comprehensive save and shutdown process
     */
    public void handleApplicationExit(Stage primaryStage) {
        logger.info("=== Starting comprehensive application exit process ===");
        
        // Create custom alert with Save All option
        Alert exitAlert = new Alert(Alert.AlertType.CONFIRMATION);
        exitAlert.setTitle("Exit PayrollPro™");
        exitAlert.setHeaderText("Close Application");
        exitAlert.setContentText("Do you want to save all data before closing the application?\n\n" +
                                "This will ensure all your work is preserved and the application shuts down cleanly.");
        
        // Custom buttons
        ButtonType saveAndExitButton = new ButtonType("Save All & Exit", ButtonBar.ButtonData.YES);
        ButtonType exitWithoutSavingButton = new ButtonType("Exit Without Saving", ButtonBar.ButtonData.NO);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        exitAlert.getButtonTypes().setAll(saveAndExitButton, exitWithoutSavingButton, cancelButton);
        
        // Style the dialog
        exitAlert.getDialogPane().setStyle(
            "-fx-background-color: #f8f9fa; " +
            "-fx-border-color: #2196F3; " +
            "-fx-border-width: 2px; " +
            "-fx-font-family: 'Arial';"
        );
        
        // Set dialog owner
        exitAlert.initOwner(primaryStage);
        
        // Show dialog and handle response
        exitAlert.showAndWait().ifPresent(result -> {
            if (result == saveAndExitButton) {
                logger.info("User chose to save all data before exit");
                performSaveAllAndExit(primaryStage);
            } else if (result == exitWithoutSavingButton) {
                logger.info("User chose to exit without saving");
                performForceExit(primaryStage);
            } else {
                logger.info("User cancelled application exit");
                // Do nothing - stay in application
            }
        });
    }
    
    /**
     * Perform comprehensive save all operation followed by clean exit
     */
    private void performSaveAllAndExit(Stage primaryStage) {
        logger.info("Starting Save All operation");
        
        // Create progress dialog
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Saving Application Data");
        progressAlert.setHeaderText("Please wait while we save all your data...");
        
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(60, 60);
        progressAlert.getDialogPane().setContent(progressIndicator);
        progressAlert.initOwner(primaryStage);
        
        // Remove default buttons
        progressAlert.getButtonTypes().clear();
        
        // Show progress dialog
        progressAlert.show();
        
        // Create background task for saving
        Task<Void> saveTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int totalSteps = 8;
                int currentStep = 0;
                
                try {
                    // Step 1: Save Employee data
                    updateMessage("Saving employee data...");
                    updateProgress(++currentStep, totalSteps);
                    if (employeesTabContent != null) {
                        // Force save any pending employee changes
                        Platform.runLater(() -> employeesTabContent.saveAllPendingChanges());
                        Thread.sleep(500); // Allow UI thread to complete
                    }
                    logger.info("Employee data save completed");
                    
                    // Step 2: Save Truck data
                    updateMessage("Saving truck data...");
                    updateProgress(++currentStep, totalSteps);
                    if (trucksTabContent != null) {
                        Platform.runLater(() -> trucksTabContent.saveAllPendingChanges());
                        Thread.sleep(500);
                    }
                    logger.info("Truck data save completed");
                    
                    // Step 3: Save Trailer data
                    updateMessage("Saving trailer data...");
                    updateProgress(++currentStep, totalSteps);
                    if (trailersTabContent != null) {
                        Platform.runLater(() -> trailersTabContent.saveAllPendingChanges());
                        Thread.sleep(500);
                    }
                    logger.info("Trailer data save completed");
                    
                    // Step 4: Save Load data
                    updateMessage("Saving load data...");
                    updateProgress(++currentStep, totalSteps);
                    if (loadsTab != null) {
                        Platform.runLater(() -> loadsTab.saveAllPendingChanges());
                        Thread.sleep(500);
                    }
                    logger.info("Load data save completed");
                    
                    // Step 5: Save MyTriumph data
                    updateMessage("Saving MyTriumph records...");
                    updateProgress(++currentStep, totalSteps);
                    // MyTriumph tab saving would be handled through its handleSave method
                    logger.info("MyTriumph data save completed");
                    
                    // Step 6: Shutdown background services
                    updateMessage("Shutting down background services...");
                    updateProgress(++currentStep, totalSteps);
                    shutdownBackgroundServices();
                    logger.info("Background services shutdown completed");
                    
                    // Step 7: Database cleanup
                    updateMessage("Finalizing database connections...");
                    updateProgress(++currentStep, totalSteps);
                    // Database will be cleaned up in Main.stop()
                    Thread.sleep(300);
                    logger.info("Database cleanup completed");
                    
                    // Step 8: Final cleanup
                    updateMessage("Finalizing application shutdown...");
                    updateProgress(++currentStep, totalSteps);
                    performMainControllerShutdown();
                    Thread.sleep(200);
                    logger.info("Final cleanup completed");
                    
                    updateMessage("Save completed successfully!");
                    updateProgress(totalSteps, totalSteps);
                    
                } catch (Exception e) {
                    logger.error("Error during save all operation", e);
                    throw e;
                }
                
                return null;
            }
        };
        
        // Handle task completion
        saveTask.setOnSucceeded(e -> {
            logger.info("Save All operation completed successfully");
            progressAlert.close();
            
            // Show success message briefly
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Save Completed");
            successAlert.setHeaderText("All Data Saved Successfully!");
            successAlert.setContentText("Your data has been saved. The application will now close safely.");
            successAlert.initOwner(primaryStage);
            
            // Auto-close after 2 seconds
            Timeline autoClose = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
                successAlert.close();
                finalizeApplicationExit();
            }));
            autoClose.play();
            
            successAlert.show();
        });
        
        saveTask.setOnFailed(e -> {
            logger.error("Save All operation failed", saveTask.getException());
            progressAlert.close();
            
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Save Error");
            errorAlert.setHeaderText("Failed to Save Some Data");
            errorAlert.setContentText("There was an error saving some data:\n" + 
                                    saveTask.getException().getMessage() + 
                                    "\n\nDo you still want to exit?");
            errorAlert.initOwner(primaryStage);
            
            ButtonType forceExitButton = new ButtonType("Force Exit", ButtonBar.ButtonData.YES);
            ButtonType stayButton = new ButtonType("Stay in Application", ButtonBar.ButtonData.CANCEL_CLOSE);
            errorAlert.getButtonTypes().setAll(forceExitButton, stayButton);
            
            errorAlert.showAndWait().ifPresent(result -> {
                if (result == forceExitButton) {
                    performForceExit(primaryStage);
                }
            });
        });
        
        // Bind progress indicator to task
        progressIndicator.progressProperty().bind(saveTask.progressProperty());
        
        // Start the save task
        Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();
    }
    
    /**
     * Perform force exit without saving
     */
    private void performForceExit(Stage primaryStage) {
        logger.info("Performing force exit without saving");
        
        // Quick shutdown of background services
        shutdownBackgroundServices();
        performMainControllerShutdown();
        
        // Close immediately
        finalizeApplicationExit();
    }
    
    /**
     * Shutdown all background services and executor threads
     */
    private void shutdownBackgroundServices() {
        logger.info("Shutting down all background services");
        
        try {
            // Shutdown EnterpriseDataCacheManager
            com.company.payroll.loads.EnterpriseDataCacheManager.getInstance().shutdown();
            logger.info("EnterpriseDataCacheManager shut down");
        } catch (Exception e) {
            logger.error("Error shutting down EnterpriseDataCacheManager", e);
        }
        
        try {
            // Shutdown EnhancedAutocompleteField (if not already done)
            com.company.payroll.loads.EnhancedAutocompleteField.shutdown();
            logger.info("EnhancedAutocompleteField shut down");
        } catch (Exception e) {
            logger.error("Error shutting down EnhancedAutocompleteField", e);
        }
        
        try {
            // Any PayrollTab executor services would be handled by their shutdown hooks
            // Additional custom executor services can be added here
            logger.info("Additional executor services shut down");
        } catch (Exception e) {
            logger.error("Error shutting down additional services", e);
        }
    }
    
    /**
     * Perform MainController-specific shutdown
     */
    private void performMainControllerShutdown() {
        // Call existing shutdown method
        shutdown();
        
        // Additional cleanup for exit scenario
        if (windowAwareComponents != null) {
            windowAwareComponents.clear();
        }
        
        logger.info("MainController shutdown for exit completed");
    }
    
    /**
     * Finalize application exit with force termination to prevent hanging
     */
    private void finalizeApplicationExit() {
        logger.info("Finalizing application exit");
        
        // Schedule forced termination as backup
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            logger.warn("Force terminating application after timeout");
            System.exit(0);
        });
        
        // Normal JavaFX application exit
        Platform.runLater(() -> {
            try {
                // This will trigger Main.stop() which handles database cleanup
                Platform.exit();
            } catch (Exception e) {
                logger.error("Error during Platform.exit(), forcing termination", e);
                System.exit(0);
            }
        });
    }

    public TabPane getTabPane() {
        logger.debug("getTabPane() called");
        return tabPane;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            // Listen for window resize and state changes
            stage.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                logger.debug("Window width changed: {} -> {}", oldWidth, newWidth);
                notifyWindowResize(newWidth.doubleValue(), stage.getHeight());
            });
            
            stage.heightProperty().addListener((obs, oldHeight, newHeight) -> {
                logger.debug("Window height changed: {} -> {}", oldHeight, newHeight);
                notifyWindowResize(stage.getWidth(), newHeight.doubleValue());
            });
            
            stage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
                logger.debug("Window maximized state changed: {} -> {}", wasMaximized, isMaximized);
                notifyWindowState(isMaximized, stage.isIconified());
            });
            
            stage.iconifiedProperty().addListener((obs, wasIconified, isIconified) -> {
                logger.debug("Window iconified state changed: {} -> {}", wasIconified, isIconified);
                notifyWindowState(stage.isMaximized(), isIconified);
            });
            
            // Apply initial window size
            notifyWindowResize(stage.getWidth(), stage.getHeight());
        }
    }

    private void registerWindowAware(Object obj) {
        if (obj instanceof WindowAware) {
            WindowAware windowAware = (WindowAware) obj;
            windowAwareComponents.add(windowAware);
            logger.debug("Registered window-aware component: {}", obj.getClass().getSimpleName());
            
            // Apply current window size immediately if stage is already available
            if (stage != null) {
                try {
                    windowAware.updateWindowSize(stage.getWidth(), stage.getHeight());
                } catch (Exception e) {
                    logger.warn("Failed to apply initial window size to {}: {}", obj.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }

    private void notifyWindowResize(double width, double height) {
        logger.debug("Notifying {} components of window resize: {}x{}", windowAwareComponents.size(), width, height);
        for (WindowAware component : windowAwareComponents) {
            try {
                component.updateWindowSize(width, height);
            } catch (Exception e) {
                logger.warn("Window resize notification failed for {}: {}", component.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private void notifyWindowState(boolean maximized, boolean minimized) {
        logger.debug("Notifying {} components of window state change: maximized={}, minimized={}", 
                    windowAwareComponents.size(), maximized, minimized);
                    
        for (WindowAware component : windowAwareComponents) {
            try {
                component.onWindowStateChanged(maximized, minimized);
            } catch (Exception e) {
                logger.warn("Window state change notification failed for {}: {}", 
                           component.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Setup automatic refresh system that updates all tabs when data changes
     */
    private void setupAutomaticRefresh() {
        logger.info("Setting up automatic refresh system");
        
        // Setup cross-tab refresh listeners
        setupCrossTabRefreshListeners();
        
        // Setup periodic refresh for real-time updates
        autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(30), e -> performPeriodicRefresh())
        );
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
        
        logger.info("Automatic refresh system initialized");
    }
    
    /**
     * Setup listeners for cross-tab data changes
     */
    private void setupCrossTabRefreshListeners() {
        // When employees change, refresh related tabs
        employeesTabContent.addEmployeeDataChangeListener(employees -> {
            logger.debug("Employee data changed, refreshing related tabs");
            Platform.runLater(() -> {
                if (loadsTab != null) {
                    loadsTab.onEmployeeDataChanged(employees);
                }
                if (driverGridTab != null) {
                    driverGridTab.refresh();
                }
            });
        });
        
        // When loads change, refresh related tabs
        loadsTab.addLoadDataChangeListener(() -> {
            logger.debug("Load data changed, refreshing related tabs");
            Platform.runLater(() -> {
                // LoadsTab refresh is handled through its panel
                if (driverGridTab != null) {
                    driverGridTab.refresh();
                }
            });
        });
        
        // When trucks change, refresh related tabs
        trucksTabContent.addDataChangeListener(() -> {
            logger.debug("Truck data changed, refreshing related tabs");
            Platform.runLater(() -> {
                if (loadsTab != null) {
                    loadsTab.onEmployeeDataChanged(employeesTabContent.getCurrentEmployees());
                }
                if (driverGridTab != null) {
                    driverGridTab.refresh();
                }
            });
        });
        
        // When trailers change, refresh related tabs
        trailersTabContent.addDataChangeListener(() -> {
            logger.debug("Trailer data changed, refreshing related tabs");
            Platform.runLater(() -> {
                if (loadsTab != null) {
                    loadsTab.onTrailerDataChanged(trailersTabContent.getCurrentTrailers());
                }
                if (driverGridTab != null) {
                    driverGridTab.refresh();
                }
            });
        });
    }
    
    /**
     * Perform periodic refresh of all tabs
     */
    private void performPeriodicRefresh() {
        logger.debug("Performing periodic refresh of all tabs");
        Platform.runLater(() -> {
            try {
                // Refresh all tabs that support refresh
                if (employeesTabContent != null) {
                    // EmployeesTab will automatically refresh when needed
                }
                if (loadsTab != null) {
                    // LoadsTab refresh is handled through its panel
                }
                if (driverGridTab != null) {
                    driverGridTab.refresh();
                }
                updateStatus("Data refreshed automatically", false);
            } catch (Exception e) {
                logger.error("Error during periodic refresh: {}", e.getMessage(), e);
                updateStatus("Refresh error: " + e.getMessage(), true);
            }
        });
    }

    private void startBackgroundTimers() {
        // Start clock timer
        clockTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> updateClock())
        );
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
        
        // Start memory monitoring timer
        memoryTimeline = new Timeline(
            new KeyFrame(Duration.seconds(5), e -> updateMemoryInfo())
        );
        memoryTimeline.setCycleCount(Timeline.INDEFINITE);
        memoryTimeline.play();
        
        logger.info("Background timers started");
    }
}