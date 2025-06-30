package com.company.payroll;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import mgubran1.PayrollDev.DashboardTab;
import mgubran1.PayrollDev.InvoiceTab;
import mgubran1.PayrollDev.MaintenanceTab;
import mgubran1.PayrollDev.CompanyRevenueTab;
import mgubran1.PayrollDev.PayStubTab;
import mgubran1.PayrollDev.EmployeeIncomeTab;
import mgubran1.PayrollDev.trucks.TrucksTab;
import mgubran1.PayrollDev.trailers.TrailersTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MainController manages the main application TabPane and wires up all feature tabs.
 * Order: Employees, Loads, Fuel Import, Payroll, MyTriumph Audit.
 */
public class MainController extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private final TabPane tabPane;
    private Label statusLabel;
    private Label clockLabel;
    private Timeline clockTimeline;
    
    public MainController() {
        logger.info("Initializing MainController");
        
        // Create header
        HBox header = createHeader();
        setTop(header);
        
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        try {
            // Dashboard tab
            DashboardTab dashboardTabContent = new DashboardTab();
            Tab dashboardTab = new Tab("Dashboard", dashboardTabContent);
            dashboardTab.setClosable(false);
            dashboardTab.setGraphic(createTabIcon("\uD83D\uDCC8"));

            // Employees tab (must be created first, for cross-tab event support)
            logger.debug("Creating Employees tab");
            EmployeesTab employeesTabContent = new EmployeesTab();
            Tab employeesTab = new Tab("Employees", employeesTabContent);
            employeesTab.setClosable(false);
            employeesTab.setGraphic(createTabIcon("👥"));
            logger.info("Employees tab created successfully");

            // Loads tab (receives employee data for driver selection)
            logger.debug("Creating Loads tab");
            LoadsTab loadsTab = new LoadsTab(employeesTabContent);
            // LoadsTab already extends Tab, so we add it directly
            loadsTab.setClosable(false);
            loadsTab.setGraphic(createTabIcon("📦"));
            logger.info("Loads tab created successfully");

            // Wire up sync callback from Loads to MyTriumph
            logger.debug("Setting up Loads to MyTriumph sync callback");
            MyTriumphTab myTriumphTab = new MyTriumphTab();
            myTriumphTab.setClosable(false);
            myTriumphTab.setGraphic(createTabIcon("📊"));
            loadsTab.setSyncCallback(loads -> {
                logger.info("Sync callback triggered with {} loads", loads.size());
                myTriumphTab.syncFromLoads(loads);
            });

            // Fuel import tab
            logger.debug("Creating Fuel Import tab");
            FuelImportTab fuelImportTabContent = new FuelImportTab();
            Tab fuelImportTab = new Tab("Fuel Import", fuelImportTabContent);
            fuelImportTab.setClosable(false);
            fuelImportTab.setGraphic(createTabIcon("⛽"));
            logger.info("Fuel Import tab created successfully");

            // Trucks tab
            TrucksTab trucksTabContent = new TrucksTab();
            Tab trucksTab = new Tab("Trucks", trucksTabContent);
            trucksTab.setClosable(false);
            trucksTab.setGraphic(createTabIcon("\uD83D\uDE9A"));

            // Trailers tab
            TrailersTab trailersTabContent = new TrailersTab();
            Tab trailersTab = new Tab("Trailers", trailersTabContent);
            trailersTab.setClosable(false);
            trailersTab.setGraphic(createTabIcon("\uD83D\uDEA7"));

            // Maintenance tab
            MaintenanceTab maintenanceTabContent = new MaintenanceTab();
            Tab maintenanceTab = new Tab("Maintenance", maintenanceTabContent);
            maintenanceTab.setClosable(false);
            maintenanceTab.setGraphic(createTabIcon("\u2699"));

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
            payrollTab.setGraphic(createTabIcon("💰"));
            logger.info("Payroll tab created successfully");

            // Register PayrollTab as a listener for load data changes
            logger.debug("Registering PayrollTab as LoadDataChangeListener");
            loadsTab.addLoadDataChangeListener(payrollTabContent);
            logger.info("PayrollTab registered as load data change listener");

            // Register PayrollTab as a listener for fuel data changes
            logger.debug("Registering PayrollTab as FuelDataChangeListener");
            fuelImportTabContent.addFuelDataChangeListener(payrollTabContent);
            logger.info("PayrollTab registered as fuel data change listener");

            // Pay stub tab
            PayStubTab payStubTabContent = new PayStubTab();
            Tab payStubTab = new Tab("Pay Stubs", payStubTabContent);
            payStubTab.setClosable(false);
            payStubTab.setGraphic(createTabIcon("\uD83D\uDCC4"));

            // Employee income tab
            EmployeeIncomeTab employeeIncomeTabContent = new EmployeeIncomeTab();
            Tab employeeIncomeTab = new Tab("Driver Income", employeeIncomeTabContent);
            employeeIncomeTab.setClosable(false);
            employeeIncomeTab.setGraphic(createTabIcon("\uD83D\uDCB0"));

            // Invoice tab
            InvoiceTab invoiceTabContent = new InvoiceTab();
            Tab invoiceTab = new Tab("Invoices", invoiceTabContent);
            invoiceTab.setClosable(false);
            invoiceTab.setGraphic(createTabIcon("\uD83D\uDCCB"));

            // Company revenue tab
            CompanyRevenueTab companyRevenueTabContent = new CompanyRevenueTab();
            Tab companyRevenueTab = new Tab("Company Revenue", companyRevenueTabContent);
            companyRevenueTab.setClosable(false);
            companyRevenueTab.setGraphic(createTabIcon("\uD83D\uDCB5"));

            logger.info("MyTriumph Audit tab created successfully");

            // Add to TabPane in UX order
            logger.debug("Adding all tabs to TabPane");
            tabPane.getTabs().addAll(
                dashboardTab,
                employeesTab,
                loadsTab,
                fuelImportTab,
                trucksTab,
                trailersTab,
                maintenanceTab,
                payrollTab,
                payStubTab,
                employeeIncomeTab,
                invoiceTab,
                companyRevenueTab,
                myTriumphTab
            );
            logger.info("All {} tabs added to TabPane successfully", tabPane.getTabs().size());

            // Log tab selection changes and update status
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (oldTab != null) {
                    logger.debug("Tab deselected: {}", oldTab.getText());
                }
                if (newTab != null) {
                    logger.info("Tab selected: {}", newTab.getText());
                    updateStatus("Active tab: " + newTab.getText());
                }
            });

            logger.info("MainController initialization complete");
            
        } catch (Exception e) {
            logger.error("Failed to initialize MainController: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize application components", e);
        }
        
        // Set the tab pane as center
        setCenter(tabPane);
        
        // Create footer
        HBox footer = createFooter();
        setBottom(footer);
    }
    
    private HBox createHeader() {
        HBox header = new HBox();
        header.setPadding(new Insets(15));
        header.setSpacing(20);
        header.setStyle("-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
                       "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 2);");
        
        // Company name
        Label companyLabel = new Label("PayrollPro™");
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        companyLabel.setTextFill(Color.WHITE);
        
        // Version info
        Label versionLabel = new Label("v0.1.0");
        versionLabel.setFont(Font.font("Arial", 14));
        versionLabel.setTextFill(Color.web("#B3E5FC"));
        
        // Clock
        clockLabel = new Label();
        clockLabel.setFont(Font.font("Arial", 14));
        clockLabel.setTextFill(Color.WHITE);
        updateClock();
        
        // Start clock timer
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
        
        // Spacer
        HBox spacer = new HBox();
        spacer.setMinWidth(100);
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        
        header.getChildren().addAll(companyLabel, versionLabel, spacer, clockLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }
    
    private HBox createFooter() {
        HBox footer = new HBox();
        footer.setPadding(new Insets(10));
        footer.setSpacing(20);
        footer.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 1 0 0 0;");
        
        statusLabel = new Label("Ready");
        statusLabel.setFont(Font.font("Arial", 12));
        
        // Database status
        Label dbLabel = new Label("Database: Connected");
        dbLabel.setFont(Font.font("Arial", 12));
        dbLabel.setTextFill(Color.GREEN);
        
        // User info
        Label userLabel = new Label("User: " + System.getProperty("user.name"));
        userLabel.setFont(Font.font("Arial", 12));
        
        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        
        // Memory info
        Label memoryLabel = new Label();
        memoryLabel.setFont(Font.font("Arial", 12));
        
        // Update memory info periodically
        Timeline memoryTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            long freeMemory = runtime.freeMemory() / 1024 / 1024;
            long usedMemory = totalMemory - freeMemory;
            memoryLabel.setText(String.format("Memory: %d MB / %d MB", usedMemory, totalMemory));
        }));
        memoryTimeline.setCycleCount(Timeline.INDEFINITE);
        memoryTimeline.play();
        
        footer.getChildren().addAll(statusLabel, dbLabel, userLabel, spacer, memoryLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }
    
    private Label createTabIcon(String emoji) {
        Label icon = new Label(emoji);
        icon.setStyle("-fx-font-size: 16px;");
        return icon;
    }
    
    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        String formatted = now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy  hh:mm:ss a"));
        clockLabel.setText(formatted);
    }
    
    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
        logger.debug("Status updated: {}", message);
    }
    
    public void shutdown() {
        logger.info("Shutting down MainController");
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        // Add any cleanup code here for tabs that need it
        logger.info("MainController shutdown complete");
    }

    public TabPane getTabPane() {
        logger.debug("getTabPane() called");
        return tabPane;
    }
}