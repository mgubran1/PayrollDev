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
    
    // Track current user
    private final String currentUser = System.getProperty("user.name", "mgubran1");
    
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
            MaintenanceTab maintenanceTabContent = new MaintenanceTab(trucksTabContent, trailersTabContent);
            Tab maintenanceTab = new Tab("Maintenance", maintenanceTabContent);
            maintenanceTab.setClosable(false);
            maintenanceTab.setGraphic(createEnhancedTabIcon("🔧", "#34495e"));
            logger.info("Maintenance tab created successfully");

            // Company Expenses tab
            logger.debug("Creating Company Expenses tab");
            CompanyExpensesTab companyExpensesTabContent = new CompanyExpensesTab();
            Tab companyExpensesTab = new Tab("Company Expenses", companyExpensesTabContent);
            companyExpensesTab.setClosable(false);
            companyExpensesTab.setGraphic(createEnhancedTabIcon("💳", "#e74c3c"));
            logger.info("Company Expenses tab created successfully");

            // Loads tab (receives employee data for driver selection)
            logger.debug("Creating Loads tab");
            LoadsTab loadsTab = new LoadsTab(employeesTabContent);
            // LoadsTab already extends Tab, so we add it directly
            loadsTab.setClosable(false);
            loadsTab.setGraphic(createEnhancedTabIcon("📦", "#f39c12"));
            logger.info("Loads tab created successfully");

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
            RevenueTab revenueTabContent = new RevenueTab(payrollTabContent, maintenanceTabContent, companyExpensesTabContent);
            Tab revenueTab = new Tab("Revenue", revenueTabContent);
            revenueTab.setClosable(false);
            revenueTab.setGraphic(createEnhancedTabIcon("📈", "#3498db"));
            logger.info("Revenue tab created successfully");

            // Driver Income tab
            logger.debug("Creating Driver Income tab");
            DriverIncomeTab driverIncomeTabContent = new DriverIncomeTab(payrollTabContent);
            Tab driverIncomeTab = new Tab("Driver Income", driverIncomeTabContent);
            driverIncomeTab.setClosable(false);
            driverIncomeTab.setGraphic(createEnhancedTabIcon("🚗", "#16a085"));
            logger.info("Driver Income tab created successfully");

            // Register PayrollTab as a listener for load data changes
            logger.debug("Registering PayrollTab as LoadDataChangeListener");
            loadsTab.addLoadDataChangeListener(payrollTabContent);
            logger.info("PayrollTab registered as load data change listener");

            // Register PayrollTab as a listener for fuel data changes
            logger.debug("Registering PayrollTab as FuelDataChangeListener");
            fuelImportTabContent.addFuelDataChangeListener(payrollTabContent);
            logger.info("PayrollTab registered as fuel data change listener");

            logger.info("MyTriumph Audit tab created successfully");

            // Add to TabPane in UX order with enhanced animation
            logger.debug("Adding all tabs to TabPane");
            tabPane.getTabs().addAll(
                employeesTab,
                trucksTab,
                trailersTab,
                maintenanceTab,
                companyExpensesTab,
                loadsTab,        // Add LoadsTab directly (it is a Tab)
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
                }
            });

            logger.info("MainController initialization complete");
            
        } catch (Exception e) {
            logger.error("Failed to initialize MainController: {}", e.getMessage(), e);
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
        
        // Show welcome message
        updateStatus("Welcome " + currentUser + "! System ready.", true);
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
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        if (memoryTimeline != null) {
            memoryTimeline.stop();
        }
        // Add any cleanup code here for tabs that need it
        logger.info("MainController shutdown complete");
    }

    public TabPane getTabPane() {
        logger.debug("getTabPane() called");
        return tabPane;
    }
}