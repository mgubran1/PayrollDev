package com.company.payroll.employees;

import com.company.payroll.database.DatabaseConfig;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;
import javafx.scene.control.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive dialog for configuring payment methods for employees.
 * Supports percentage, flat rate, and per-mile payment configurations.
 */
public class PaymentMethodConfigurationDialog extends Dialog<Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodConfigurationDialog.class);
    
    private final ObservableList<EmployeePaymentRow> employees = FXCollections.observableArrayList();
    private final EmployeeDAO employeeDAO;
    private final PaymentMethodHistoryDAO historyDAO;
    
    private TabPane tabPane;
    private Tab percentageTab;
    private Tab flatRateTab;
    private Tab perMileTab;
    private TableView<EmployeePaymentRow> percentageTable;
    private TableView<EmployeePaymentRow> flatRateTable;
    private TableView<EmployeePaymentRow> perMileTable;
    
    private DatePicker effectiveDatePicker;
    private TextArea notesArea;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button saveButton;
    
    // Current payment type being configured
    private PaymentType currentPaymentType = PaymentType.PERCENTAGE;
    
    public PaymentMethodConfigurationDialog() {
        EmployeeDAO tempEmployeeDAO;
        PaymentMethodHistoryDAO tempHistoryDAO;
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            tempEmployeeDAO = new EmployeeDAO(conn);
            tempHistoryDAO = new PaymentMethodHistoryDAO(conn);
        } catch (Exception e) {
            logger.error("Error initializing DAOs", e);
            tempEmployeeDAO = new EmployeeDAO();
            tempHistoryDAO = null;
        }
        
        this.employeeDAO = tempEmployeeDAO;
        this.historyDAO = tempHistoryDAO;
        
        initializeDialog();
        loadEmployees();
    }
    
    private void initializeDialog() {
        setTitle("Payment Method Configuration");
        setHeaderText("Configure Employee Payment Methods");
        setResizable(true);
        
        // Create main layout
        VBox mainLayout = new VBox(10);
        mainLayout.setPadding(new Insets(10));
        mainLayout.setPrefSize(1000, 700);
        
        // Instructions
        Label instructions = new Label("Select employees and configure their payment methods. Changes will take effect on the specified date.");
        instructions.setWrapText(true);
        instructions.setStyle("-fx-font-style: italic;");
        
        // Effective date section
        HBox dateSection = new HBox(10);
        dateSection.setAlignment(Pos.CENTER_LEFT);
        Label dateLabel = new Label("Effective Date:");
        effectiveDatePicker = new DatePicker(LocalDate.now());
        effectiveDatePicker.setConverter(new StringConverter<LocalDate>() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            
            @Override
            public String toString(LocalDate date) {
                return date != null ? formatter.format(date) : "";
            }
            
            @Override
            public LocalDate fromString(String string) {
                return string != null && !string.isEmpty() ? LocalDate.parse(string, formatter) : null;
            }
        });
        dateSection.getChildren().addAll(dateLabel, effectiveDatePicker);
        
        // Create tabbed interface
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Percentage tab
        percentageTab = new Tab("Percentage Payment");
        percentageTab.setContent(createPercentageTab());
        
        // Flat rate tab
        flatRateTab = new Tab("Flat Rate Payment");
        flatRateTab.setContent(createFlatRateTab());
        
        // Per mile tab
        perMileTab = new Tab("Per Mile Payment");
        perMileTab.setContent(createPerMileTab());
        
        tabPane.getTabs().addAll(percentageTab, flatRateTab, perMileTab);
        
        // Tab change listener
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == percentageTab) {
                currentPaymentType = PaymentType.PERCENTAGE;
            } else if (newTab == flatRateTab) {
                currentPaymentType = PaymentType.FLAT_RATE;
            } else if (newTab == perMileTab) {
                currentPaymentType = PaymentType.PER_MILE;
            }
        });
        
        // Notes section
        VBox notesSection = new VBox(5);
        Label notesLabel = new Label("Configuration Notes:");
        notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.setPromptText("Enter any notes about this configuration change...");
        notesSection.getChildren().addAll(notesLabel, notesArea);
        
        // Status section
        HBox statusSection = new HBox(10);
        statusSection.setAlignment(Pos.CENTER_LEFT);
        statusLabel = new Label("");
        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        statusSection.getChildren().addAll(statusLabel, progressBar);
        
        // Add all sections to main layout
        mainLayout.getChildren().addAll(
            instructions,
            new Separator(),
            dateSection,
            tabPane,
            notesSection,
            statusSection
        );
        
        // Set dialog content
        getDialogPane().setContent(mainLayout);
        
        // Add buttons
        ButtonType saveButtonType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Enable/disable save button based on selection
        saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        
        // Result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return saveChanges();
            }
            return false;
        });
    }
    
    private VBox createPercentageTab() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));
        
        // Instructions
        Label instructions = new Label("Configure percentage-based payment where drivers receive a percentage of the load gross amount.");
        instructions.setWrapText(true);
        
        // Add a section to switch employees to this payment method
        VBox switchSection = new VBox(5);
        switchSection.setPadding(new Insets(10));
        switchSection.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-radius: 5;");
        
        Label switchLabel = new Label("Switch Employees to Percentage Payment:");
        switchLabel.setStyle("-fx-font-weight: bold;");
        
        ComboBox<Employee> employeeSwitchBox = new ComboBox<>();
        employeeSwitchBox.setPromptText("Select employee to switch to percentage payment...");
        employeeSwitchBox.setPrefWidth(400);
        
        // Populate with employees NOT currently using percentage
        try {
            List<Employee> nonPercentageEmployees = employeeDAO.getAll().stream()
                .filter(emp -> emp.getStatus() == Employee.Status.ACTIVE)
                .filter(emp -> {
                    PaymentType type = emp.getPaymentType();
                    if (historyDAO != null) {
                        PaymentMethodHistory history = historyDAO.getEffectivePaymentMethod(
                            emp.getId(), LocalDate.now());
                        if (history != null) {
                            type = history.getPaymentType();
                        }
                    }
                    return type != PaymentType.PERCENTAGE;
                })
                .collect(Collectors.toList());
            employeeSwitchBox.getItems().addAll(nonPercentageEmployees);
        } catch (Exception e) {
            logger.error("Error loading non-percentage employees", e);
        }
        
        employeeSwitchBox.setConverter(new StringConverter<Employee>() {
            @Override
            public String toString(Employee emp) {
                if (emp == null) return "";
                return emp.getName() + " - " + emp.getTruckUnit() + " (Currently: " + emp.getPaymentMethodDescription() + ")";
            }
            
            @Override
            public Employee fromString(String string) {
                return null;
            }
        });
        
        Button addToPercentageBtn = new Button("Add to Percentage Table");
        addToPercentageBtn.setOnAction(e -> {
            Employee selected = employeeSwitchBox.getValue();
            if (selected != null) {
                // Create a new row for this employee
                EmployeePaymentRow newRow = new EmployeePaymentRow(selected);
                newRow.setSelected(true);
                
                // Add listeners
                newRow.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    updateSaveButtonState();
                });
                newRow.validationStatusProperty().addListener((obs, oldStatus, newStatus) -> {
                    updateSaveButtonState();
                });
                
                // Add to the percentage table
                percentageTable.getItems().add(newRow);
                employees.add(newRow);
                
                // Remove from dropdown
                employeeSwitchBox.getItems().remove(selected);
                employeeSwitchBox.setValue(null);
                
                updateStatus("Added " + selected.getName() + " to percentage payment configuration");
                updateSaveButtonState();
            }
        });
        
        HBox switchControls = new HBox(10);
        switchControls.setAlignment(Pos.CENTER_LEFT);
        switchControls.getChildren().addAll(employeeSwitchBox, addToPercentageBtn);
        
        switchSection.getChildren().addAll(switchLabel, switchControls);
        
        // Create table
        percentageTable = new TableView<>();
        percentageTable.setEditable(true);
        percentageTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Checkbox column
        TableColumn<EmployeePaymentRow, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        // Employee name column
        TableColumn<EmployeePaymentRow, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(200);
        
        // Current payment method column
        TableColumn<EmployeePaymentRow, String> currentMethodCol = new TableColumn<>("Current Method");
        currentMethodCol.setCellValueFactory(cellData -> cellData.getValue().currentMethodProperty());
        currentMethodCol.setPrefWidth(150);
        
        // Driver percentage column
        TableColumn<EmployeePaymentRow, Double> driverCol = new TableColumn<>("Driver %");
        driverCol.setCellValueFactory(cellData -> cellData.getValue().driverPercentProperty().asObject());
        driverCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        driverCol.setPrefWidth(100);
        driverCol.setEditable(true);
        driverCol.setOnEditCommit(event -> {
            event.getRowValue().setDriverPercent(event.getNewValue());
            updatePercentageTotals();
        });
        
        // Company percentage column
        TableColumn<EmployeePaymentRow, Double> companyCol = new TableColumn<>("Company %");
        companyCol.setCellValueFactory(cellData -> cellData.getValue().companyPercentProperty().asObject());
        companyCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        companyCol.setPrefWidth(100);
        companyCol.setEditable(true);
        companyCol.setOnEditCommit(event -> {
            event.getRowValue().setCompanyPercent(event.getNewValue());
            updatePercentageTotals();
        });
        
        // Service fee percentage column
        TableColumn<EmployeePaymentRow, Double> serviceFeeCol = new TableColumn<>("Service Fee %");
        serviceFeeCol.setCellValueFactory(cellData -> cellData.getValue().serviceFeePercentProperty().asObject());
        serviceFeeCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        serviceFeeCol.setPrefWidth(100);
        serviceFeeCol.setEditable(true);
        serviceFeeCol.setOnEditCommit(event -> {
            event.getRowValue().setServiceFeePercent(event.getNewValue());
            updatePercentageTotals();
        });
        
        // Total column
        TableColumn<EmployeePaymentRow, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cellData -> cellData.getValue().percentageTotalProperty());
        totalCol.setPrefWidth(80);
        totalCol.setCellFactory(column -> new TableCell<EmployeePaymentRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    double total = Double.parseDouble(item.replace("%", ""));
                    if (Math.abs(total - 100.0) > 0.01) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.GREEN);
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
        
        percentageTable.getColumns().addAll(selectCol, nameCol, currentMethodCol, 
                                           driverCol, companyCol, serviceFeeCol, totalCol);
        
        // Bulk edit section
        HBox bulkEditSection = new HBox(10);
        bulkEditSection.setAlignment(Pos.CENTER_LEFT);
        bulkEditSection.setPadding(new Insets(10, 0, 10, 0));
        
        Label bulkLabel = new Label("Bulk Edit Selected:");
        TextField driverField = new TextField();
        driverField.setPromptText("Driver %");
        driverField.setPrefWidth(80);
        
        TextField companyField = new TextField();
        companyField.setPromptText("Company %");
        companyField.setPrefWidth(80);
        
        TextField serviceFeeField = new TextField();
        serviceFeeField.setPromptText("Service %");
        serviceFeeField.setPrefWidth(80);
        
        Button applyBulkButton = new Button("Apply to Selected");
        applyBulkButton.setOnAction(e -> applyBulkPercentages(driverField, companyField, serviceFeeField));
        
        bulkEditSection.getChildren().addAll(bulkLabel, driverField, companyField, serviceFeeField, applyBulkButton);
        
        layout.getChildren().addAll(instructions, switchSection, new Separator(), percentageTable, bulkEditSection);
        
        return layout;
    }
    
    private VBox createFlatRateTab() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));
        
        // Instructions
        Label instructions = new Label("Configure flat rate payment where drivers receive a fixed amount per completed load. " +
                                     "Note: The default flat rate can be overridden on individual loads.");
        instructions.setWrapText(true);
        
        // Add a section to switch employees to this payment method
        VBox switchSection = new VBox(5);
        switchSection.setPadding(new Insets(10));
        switchSection.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-radius: 5;");
        
        Label switchLabel = new Label("Switch Employees to Flat Rate Payment:");
        switchLabel.setStyle("-fx-font-weight: bold;");
        
        ComboBox<Employee> employeeSwitchBox = new ComboBox<>();
        employeeSwitchBox.setPromptText("Select employee to switch to flat rate payment...");
        employeeSwitchBox.setPrefWidth(400);
        
        // Populate with employees NOT currently using flat rate
        try {
            List<Employee> nonFlatRateEmployees = employeeDAO.getAll().stream()
                .filter(emp -> emp.getStatus() == Employee.Status.ACTIVE)
                .filter(emp -> {
                    PaymentType type = emp.getPaymentType();
                    if (historyDAO != null) {
                        PaymentMethodHistory history = historyDAO.getEffectivePaymentMethod(
                            emp.getId(), LocalDate.now());
                        if (history != null) {
                            type = history.getPaymentType();
                        }
                    }
                    return type != PaymentType.FLAT_RATE;
                })
                .collect(Collectors.toList());
            employeeSwitchBox.getItems().addAll(nonFlatRateEmployees);
        } catch (Exception e) {
            logger.error("Error loading non-flat rate employees", e);
        }
        
        employeeSwitchBox.setConverter(new StringConverter<Employee>() {
            @Override
            public String toString(Employee emp) {
                if (emp == null) return "";
                return emp.getName() + " - " + emp.getTruckUnit() + " (Currently: " + emp.getPaymentMethodDescription() + ")";
            }
            
            @Override
            public Employee fromString(String string) {
                return null;
            }
        });
        
        Button addToFlatRateBtn = new Button("Add to Flat Rate Table");
        addToFlatRateBtn.setOnAction(e -> {
            Employee selected = employeeSwitchBox.getValue();
            if (selected != null) {
                // Create a new row for this employee
                EmployeePaymentRow newRow = new EmployeePaymentRow(selected);
                newRow.setSelected(true);
                newRow.setFlatRateAmount(0.0); // Start with 0, user needs to set amount
                
                // Add listeners
                newRow.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    updateSaveButtonState();
                });
                newRow.validationStatusProperty().addListener((obs, oldStatus, newStatus) -> {
                    updateSaveButtonState();
                });
                
                // Add to the flat rate table
                flatRateTable.getItems().add(newRow);
                employees.add(newRow);
                
                // Remove from dropdown
                employeeSwitchBox.getItems().remove(selected);
                employeeSwitchBox.setValue(null);
                
                updateStatus("Added " + selected.getName() + " to flat rate payment configuration");
                updateSaveButtonState();
            }
        });
        
        HBox switchControls = new HBox(10);
        switchControls.setAlignment(Pos.CENTER_LEFT);
        switchControls.getChildren().addAll(employeeSwitchBox, addToFlatRateBtn);
        
        switchSection.getChildren().addAll(switchLabel, switchControls);
        
        // Create table
        flatRateTable = new TableView<>();
        flatRateTable.setEditable(true);
        flatRateTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Checkbox column
        TableColumn<EmployeePaymentRow, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        // Employee name column
        TableColumn<EmployeePaymentRow, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(200);
        
        // Current payment method column
        TableColumn<EmployeePaymentRow, String> currentMethodCol = new TableColumn<>("Current Method");
        currentMethodCol.setCellValueFactory(cellData -> cellData.getValue().currentMethodProperty());
        currentMethodCol.setPrefWidth(150);
        
        // Flat rate amount column
        TableColumn<EmployeePaymentRow, Double> flatRateCol = new TableColumn<>("Flat Rate ($)");
        flatRateCol.setCellValueFactory(cellData -> cellData.getValue().flatRateAmountProperty().asObject());
        flatRateCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        flatRateCol.setPrefWidth(150);
        flatRateCol.setEditable(true);
        flatRateCol.setOnEditCommit(event -> {
            event.getRowValue().setFlatRateAmount(event.getNewValue());
            validateFlatRate(event.getRowValue());
        });
        
        // Validation status column
        TableColumn<EmployeePaymentRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().validationStatusProperty());
        statusCol.setPrefWidth(200);
        statusCol.setCellFactory(column -> new TableCell<EmployeePaymentRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Warning")) {
                        setTextFill(Color.ORANGE);
                    } else if (item.contains("Error")) {
                        setTextFill(Color.RED);
                    } else {
                        setTextFill(Color.GREEN);
                    }
                }
            }
        });
        
        flatRateTable.getColumns().addAll(selectCol, nameCol, currentMethodCol, flatRateCol, statusCol);
        
        // Bulk edit section
        HBox bulkEditSection = new HBox(10);
        bulkEditSection.setAlignment(Pos.CENTER_LEFT);
        bulkEditSection.setPadding(new Insets(10, 0, 10, 0));
        
        Label bulkLabel = new Label("Bulk Edit Selected:");
        TextField flatRateField = new TextField();
        flatRateField.setPromptText("Flat Rate Amount");
        flatRateField.setPrefWidth(120);
        
        Button applyBulkButton = new Button("Apply to Selected");
        applyBulkButton.setOnAction(e -> applyBulkFlatRate(flatRateField));
        
        bulkEditSection.getChildren().addAll(bulkLabel, flatRateField, applyBulkButton);
        
        // Recommended rates section
        VBox recommendedSection = new VBox(5);
        Label recommendedLabel = new Label("Recommended Flat Rates:");
        recommendedLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        GridPane ratesGrid = new GridPane();
        ratesGrid.setHgap(20);
        ratesGrid.setVgap(5);
        ratesGrid.add(new Label("Local (< 200 miles):"), 0, 0);
        ratesGrid.add(new Label("$200 - $400"), 1, 0);
        ratesGrid.add(new Label("Regional (200-500 miles):"), 0, 1);
        ratesGrid.add(new Label("$400 - $800"), 1, 1);
        ratesGrid.add(new Label("Long Distance (> 500 miles):"), 0, 2);
        ratesGrid.add(new Label("$800 - $1500"), 1, 2);
        
        recommendedSection.getChildren().addAll(recommendedLabel, ratesGrid);
        
        layout.getChildren().addAll(instructions, switchSection, new Separator(), flatRateTable, bulkEditSection, new Separator(), recommendedSection);
        
        return layout;
    }
    
    private VBox createPerMileTab() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));
        
        // Instructions
        Label instructions = new Label("Configure per-mile payment where drivers receive payment based on distance traveled. " +
                                     "Zip codes will be required for all loads using this payment method.");
        instructions.setWrapText(true);
        instructions.setTextFill(Color.DARKBLUE);
        
        // Create table
        perMileTable = new TableView<>();
        perMileTable.setEditable(true);
        perMileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Checkbox column
        TableColumn<EmployeePaymentRow, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        // Employee name column
        TableColumn<EmployeePaymentRow, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(200);
        
        // Current payment method column
        TableColumn<EmployeePaymentRow, String> currentMethodCol = new TableColumn<>("Current Method");
        currentMethodCol.setCellValueFactory(cellData -> cellData.getValue().currentMethodProperty());
        currentMethodCol.setPrefWidth(150);
        
        // Per mile rate column
        TableColumn<EmployeePaymentRow, Double> perMileCol = new TableColumn<>("Rate ($/mile)");
        perMileCol.setCellValueFactory(cellData -> cellData.getValue().perMileRateProperty().asObject());
        perMileCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        perMileCol.setPrefWidth(150);
        perMileCol.setEditable(true);
        perMileCol.setOnEditCommit(event -> {
            event.getRowValue().setPerMileRate(event.getNewValue());
            validatePerMileRate(event.getRowValue());
        });
        
        // Validation status column
        TableColumn<EmployeePaymentRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().validationStatusProperty());
        statusCol.setPrefWidth(200);
        statusCol.setCellFactory(column -> new TableCell<EmployeePaymentRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Warning")) {
                        setTextFill(Color.ORANGE);
                    } else if (item.contains("Error")) {
                        setTextFill(Color.RED);
                    } else {
                        setTextFill(Color.GREEN);
                    }
                }
            }
        });
        
        perMileTable.getColumns().addAll(selectCol, nameCol, currentMethodCol, perMileCol, statusCol);
        
        // Bulk edit section
        HBox bulkEditSection = new HBox(10);
        bulkEditSection.setAlignment(Pos.CENTER_LEFT);
        bulkEditSection.setPadding(new Insets(10, 0, 10, 0));
        
        Label bulkLabel = new Label("Bulk Edit Selected:");
        TextField perMileField = new TextField();
        perMileField.setPromptText("Per Mile Rate");
        perMileField.setPrefWidth(120);
        
        Button applyBulkButton = new Button("Apply to Selected");
        applyBulkButton.setOnAction(e -> applyBulkPerMileRate(perMileField));
        
        bulkEditSection.getChildren().addAll(bulkLabel, perMileField, applyBulkButton);
        
        // Important notice
        VBox noticeSection = new VBox(5);
        Label noticeLabel = new Label("⚠ Important Notice:");
        noticeLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        noticeLabel.setTextFill(Color.DARKORANGE);
        
        TextArea noticeText = new TextArea(
            "• Zip codes will be REQUIRED for all loads when using per-mile payment\n" +
            "• Distance is calculated from pickup to delivery zip codes\n" +
            "• Actual road distance is estimated (typically 15% more than straight-line)\n" +
            "• Ensure all customer addresses have valid zip codes before switching"
        );
        noticeText.setEditable(false);
        noticeText.setPrefRowCount(4);
        noticeText.setStyle("-fx-background-color: #FFF3CD; -fx-border-color: #FFC107;");
        
        noticeSection.getChildren().addAll(noticeLabel, noticeText);
        
        // Recommended rates section
        VBox recommendedSection = new VBox(5);
        Label recommendedLabel = new Label("Industry Standard Per-Mile Rates:");
        recommendedLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        GridPane ratesGrid = new GridPane();
        ratesGrid.setHgap(20);
        ratesGrid.setVgap(5);
        ratesGrid.add(new Label("Owner Operators:"), 0, 0);
        ratesGrid.add(new Label("$1.50 - $3.00/mile"), 1, 0);
        ratesGrid.add(new Label("Company Drivers:"), 0, 1);
        ratesGrid.add(new Label("$0.50 - $0.70/mile"), 1, 1);
        ratesGrid.add(new Label("Specialized/Hazmat:"), 0, 2);
        ratesGrid.add(new Label("$2.00 - $4.00/mile"), 1, 2);
        
        recommendedSection.getChildren().addAll(recommendedLabel, ratesGrid);
        
        layout.getChildren().addAll(instructions, perMileTable, bulkEditSection, 
                                  new Separator(), noticeSection, 
                                  new Separator(), recommendedSection);
        
        return layout;
    }
    
    private void loadEmployees() {
        try {
            List<Employee> allEmployees = employeeDAO.getAll();
            // Filter to get only active employees
            List<Employee> employeeList = allEmployees.stream()
                .filter(emp -> emp.getStatus() == Employee.Status.ACTIVE)
                .collect(Collectors.toList());
            employees.clear();
            
            // Create filtered lists for each payment type
            ObservableList<EmployeePaymentRow> percentageEmployees = FXCollections.observableArrayList();
            ObservableList<EmployeePaymentRow> flatRateEmployees = FXCollections.observableArrayList();
            ObservableList<EmployeePaymentRow> perMileEmployees = FXCollections.observableArrayList();
            
            for (Employee emp : employeeList) {
                EmployeePaymentRow row = new EmployeePaymentRow(emp);
                
                // Load current payment method from history
                PaymentType currentType = emp.getPaymentType();
                if (historyDAO != null) {
                    PaymentMethodHistory current = historyDAO.getEffectivePaymentMethod(
                        emp.getId(), LocalDate.now());
                    if (current != null) {
                        row.updateFromHistory(current);
                        currentType = current.getPaymentType();
                    }
                }
                
                // Add listener to detect selection changes
                row.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    updateSaveButtonState();
                });
                
                // Add listener to detect validation status changes
                row.validationStatusProperty().addListener((obs, oldStatus, newStatus) -> {
                    updateSaveButtonState();
                });
                
                employees.add(row);
                
                // Add to appropriate filtered list based on current payment type
                switch (currentType) {
                    case PERCENTAGE:
                        percentageEmployees.add(row);
                        break;
                    case FLAT_RATE:
                        flatRateEmployees.add(row);
                        break;
                    case PER_MILE:
                        perMileEmployees.add(row);
                        break;
                }
            }
            
            // Set filtered items for each table
            percentageTable.setItems(percentageEmployees);
            flatRateTable.setItems(flatRateEmployees);
            perMileTable.setItems(perMileEmployees);
            
            updateStatus(String.format("Loaded: %d percentage, %d flat rate, %d per mile employees",
                        percentageEmployees.size(), flatRateEmployees.size(), perMileEmployees.size()));
            
            // Update tab text with counts
            percentageTab.setText(String.format("Percentage Payment (%d)", percentageEmployees.size()));
            flatRateTab.setText(String.format("Flat Rate Payment (%d)", flatRateEmployees.size()));
            perMileTab.setText(String.format("Per Mile Payment (%d)", perMileEmployees.size()));
            
            // Update button state after loading
            updateSaveButtonState();
            
        } catch (Exception e) {
            logger.error("Error loading employees", e);
            showError("Failed to load employees: " + e.getMessage());
        }
    }
    
    private void updatePercentageTotals() {
        for (EmployeePaymentRow row : employees) {
            if (row.isSelected()) {
                double total = row.getDriverPercent() + row.getCompanyPercent() + row.getServiceFeePercent();
                row.setPercentageTotal(String.format("%.2f%%", total));
                
                if (Math.abs(total - 100.0) > 0.01) {
                    row.setValidationStatus("Error: Total must equal 100%");
                } else {
                    row.setValidationStatus("Valid");
                }
            }
        }
        updateSaveButtonState();
    }
    
    private void validateFlatRate(EmployeePaymentRow row) {
        double rate = row.getFlatRateAmount();
        if (rate <= 0) {
            row.setValidationStatus("Error: Rate must be greater than $0");
        } else if (rate > 5000) {
            row.setValidationStatus("Warning: Rate exceeds $5000");
        } else if (rate < 100) {
            row.setValidationStatus("Warning: Rate below $100");
        } else {
            row.setValidationStatus("Valid");
        }
        updateSaveButtonState();
    }
    
    private void validatePerMileRate(EmployeePaymentRow row) {
        double rate = row.getPerMileRate();
        if (rate <= 0) {
            row.setValidationStatus("Error: Rate must be greater than $0");
        } else if (rate > 5.00) {
            row.setValidationStatus("Warning: Rate exceeds $5.00/mile");
        } else if (rate < 0.50) {
            row.setValidationStatus("Warning: Rate below $0.50/mile");
        } else {
            row.setValidationStatus("Valid");
        }
        updateSaveButtonState();
    }
    
    private void applyBulkPercentages(TextField driverField, TextField companyField, TextField serviceFeeField) {
        try {
            Double driverPct = parseDoubleOrNull(driverField.getText());
            Double companyPct = parseDoubleOrNull(companyField.getText());
            Double serviceFeePct = parseDoubleOrNull(serviceFeeField.getText());
            
            int updated = 0;
            for (EmployeePaymentRow row : employees) {
                if (row.isSelected()) {
                    if (driverPct != null) row.setDriverPercent(driverPct);
                    if (companyPct != null) row.setCompanyPercent(companyPct);
                    if (serviceFeePct != null) row.setServiceFeePercent(serviceFeePct);
                    updated++;
                }
            }
            
            updatePercentageTotals();
            updateStatus("Updated " + updated + " employees");
            
        } catch (Exception e) {
            showError("Invalid percentage values");
        }
    }
    
    private void applyBulkFlatRate(TextField flatRateField) {
        try {
            Double flatRate = parseDoubleOrNull(flatRateField.getText());
            if (flatRate == null) {
                showError("Please enter a valid flat rate amount");
                return;
            }
            
            int updated = 0;
            for (EmployeePaymentRow row : employees) {
                if (row.isSelected()) {
                    row.setFlatRateAmount(flatRate);
                    validateFlatRate(row);
                    updated++;
                }
            }
            
            updateStatus("Updated " + updated + " employees");
            
        } catch (Exception e) {
            showError("Invalid flat rate value");
        }
    }
    
    private void applyBulkPerMileRate(TextField perMileField) {
        try {
            Double perMileRate = parseDoubleOrNull(perMileField.getText());
            if (perMileRate == null) {
                showError("Please enter a valid per-mile rate");
                return;
            }
            
            int updated = 0;
            for (EmployeePaymentRow row : employees) {
                if (row.isSelected()) {
                    row.setPerMileRate(perMileRate);
                    validatePerMileRate(row);
                    updated++;
                }
            }
            
            updateStatus("Updated " + updated + " employees");
            
        } catch (Exception e) {
            showError("Invalid per-mile rate value");
        }
    }
    
    private boolean saveChanges() {
        List<EmployeePaymentRow> selectedEmployees = employees.stream()
            .filter(EmployeePaymentRow::isSelected)
            .collect(Collectors.toList());
        
        if (selectedEmployees.isEmpty()) {
            showError("No employees selected");
            return false;
        }
        
        // Validate all selections
        for (EmployeePaymentRow row : selectedEmployees) {
            if (!row.getValidationStatus().equals("Valid")) {
                showError("Please fix validation errors before saving");
                return false;
            }
        }
        
        LocalDate effectiveDate = effectiveDatePicker.getValue();
        if (effectiveDate == null) {
            showError("Please select an effective date");
            return false;
        }
        
        String notes = notesArea.getText();
        
        // Confirm changes
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Payment Method Changes");
        confirm.setHeaderText("Save Payment Method Configuration?");
        confirm.setContentText(String.format(
            "This will update %d employees to %s payment effective %s.\n\n" +
            "Are you sure you want to proceed?",
            selectedEmployees.size(),
            currentPaymentType.getDisplayName(),
            effectiveDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        ));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return false;
        }
        
        // Save changes
        progressBar.setVisible(true);
        updateStatus("Saving changes...");
        
        int successCount = 0;
        int errorCount = 0;
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            PaymentMethodHistoryDAO dao = new PaymentMethodHistoryDAO(conn);
            
            for (EmployeePaymentRow row : selectedEmployees) {
                try {
                    PaymentMethodHistory history = new PaymentMethodHistory();
                    history.setEmployeeId(row.getEmployee().getId());
                    history.setPaymentType(currentPaymentType);
                    history.setEffectiveDate(effectiveDate);
                    history.setNotes(notes);
                    history.setCreatedBy("PaymentConfig");
                    
                    // Set payment type specific values
                    switch (currentPaymentType) {
                        case PERCENTAGE:
                            history.setDriverPercent(row.getDriverPercent());
                            history.setCompanyPercent(row.getCompanyPercent());
                            history.setServiceFeePercent(row.getServiceFeePercent());
                            break;
                        case FLAT_RATE:
                            history.setFlatRateAmount(row.getFlatRateAmount());
                            break;
                        case PER_MILE:
                            history.setPerMileRate(row.getPerMileRate());
                            break;
                    }
                    
                    if (dao.createPaymentMethodHistory(history)) {
                        successCount++;
                    } else {
                        errorCount++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Error saving payment method for employee " + row.getName(), e);
                    errorCount++;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error saving payment method changes", e);
            showError("Failed to save changes: " + e.getMessage());
            return false;
        } finally {
            progressBar.setVisible(false);
        }
        
        // Show results
        updateStatus(String.format("Saved %d employees, %d errors", successCount, errorCount));
        
        if (successCount > 0) {
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Payment Methods Updated");
            success.setHeaderText("Changes Saved Successfully");
            success.setContentText(String.format(
                "Updated %d employees to %s payment effective %s.",
                successCount,
                currentPaymentType.getDisplayName(),
                effectiveDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
            ));
            success.showAndWait();
            return true;
        }
        
        return false;
    }
    
    private Double parseDoubleOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void updateSaveButtonState() {
        if (saveButton == null) return;
        
        // Check if any employees are selected
        boolean hasSelection = employees.stream().anyMatch(EmployeePaymentRow::isSelected);
        
        // Check if all selected employees have valid configuration
        boolean allValid = employees.stream()
            .filter(EmployeePaymentRow::isSelected)
            .allMatch(row -> "Valid".equals(row.getValidationStatus()));
        
        // Enable save button only if employees are selected and all are valid
        saveButton.setDisable(!hasSelection || !allValid);
    }
    
    /**
     * Model class for employee payment configuration row.
     */
    public static class EmployeePaymentRow {
        private final Employee employee;
        private final javafx.beans.property.BooleanProperty selected;
        private final SimpleStringProperty name;
        private final SimpleStringProperty currentMethod;
        private final SimpleDoubleProperty driverPercent;
        private final SimpleDoubleProperty companyPercent;
        private final SimpleDoubleProperty serviceFeePercent;
        private final SimpleStringProperty percentageTotal;
        private final SimpleDoubleProperty flatRateAmount;
        private final SimpleDoubleProperty perMileRate;
        private final SimpleStringProperty validationStatus;
        
        public EmployeePaymentRow(Employee employee) {
            this.employee = employee;
            this.selected = new javafx.beans.property.SimpleBooleanProperty(false);
            this.name = new SimpleStringProperty(employee.getName());
            this.currentMethod = new SimpleStringProperty(getPaymentMethodDescription(employee));
            this.driverPercent = new SimpleDoubleProperty(employee.getDriverPercent());
            this.companyPercent = new SimpleDoubleProperty(employee.getCompanyPercent());
            this.serviceFeePercent = new SimpleDoubleProperty(employee.getServiceFeePercent());
            this.percentageTotal = new SimpleStringProperty(calculatePercentageTotal());
            this.flatRateAmount = new SimpleDoubleProperty(employee.getFlatRateAmount());
            this.perMileRate = new SimpleDoubleProperty(employee.getPerMileRate());
            this.validationStatus = new SimpleStringProperty("Valid");
        }
        
        public void updateFromHistory(PaymentMethodHistory history) {
            this.currentMethod.set(history.getDescription());
            if (history.getPaymentType() == PaymentType.PERCENTAGE) {
                this.driverPercent.set(history.getDriverPercent());
                this.companyPercent.set(history.getCompanyPercent());
                this.serviceFeePercent.set(history.getServiceFeePercent());
                this.percentageTotal.set(calculatePercentageTotal());
            } else if (history.getPaymentType() == PaymentType.FLAT_RATE) {
                this.flatRateAmount.set(history.getFlatRateAmount());
            } else if (history.getPaymentType() == PaymentType.PER_MILE) {
                this.perMileRate.set(history.getPerMileRate());
            }
        }
        
        private String getPaymentMethodDescription(Employee emp) {
            if (emp.getPaymentType() == null) {
                return "Percentage (Default)";
            }
            return emp.getPaymentMethodDescription();
        }
        
        private String calculatePercentageTotal() {
            double total = driverPercent.get() + companyPercent.get() + serviceFeePercent.get();
            return String.format("%.2f%%", total);
        }
        
        // Property getters
        public Employee getEmployee() { return employee; }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }
        
        public SimpleStringProperty nameProperty() { return name; }
        public String getName() { return name.get(); }
        
        public SimpleStringProperty currentMethodProperty() { return currentMethod; }
        public String getCurrentMethod() { return currentMethod.get(); }
        
        public SimpleDoubleProperty driverPercentProperty() { return driverPercent; }
        public double getDriverPercent() { return driverPercent.get(); }
        public void setDriverPercent(double value) { 
            this.driverPercent.set(value);
            this.percentageTotal.set(calculatePercentageTotal());
        }
        
        public SimpleDoubleProperty companyPercentProperty() { return companyPercent; }
        public double getCompanyPercent() { return companyPercent.get(); }
        public void setCompanyPercent(double value) { 
            this.companyPercent.set(value);
            this.percentageTotal.set(calculatePercentageTotal());
        }
        
        public SimpleDoubleProperty serviceFeePercentProperty() { return serviceFeePercent; }
        public double getServiceFeePercent() { return serviceFeePercent.get(); }
        public void setServiceFeePercent(double value) { 
            this.serviceFeePercent.set(value);
            this.percentageTotal.set(calculatePercentageTotal());
        }
        
        public SimpleStringProperty percentageTotalProperty() { return percentageTotal; }
        public String getPercentageTotal() { return percentageTotal.get(); }
        public void setPercentageTotal(String value) { this.percentageTotal.set(value); }
        
        public SimpleDoubleProperty flatRateAmountProperty() { return flatRateAmount; }
        public double getFlatRateAmount() { return flatRateAmount.get(); }
        public void setFlatRateAmount(double value) { this.flatRateAmount.set(value); }
        
        public SimpleDoubleProperty perMileRateProperty() { return perMileRate; }
        public double getPerMileRate() { return perMileRate.get(); }
        public void setPerMileRate(double value) { this.perMileRate.set(value); }
        
        public SimpleStringProperty validationStatusProperty() { return validationStatus; }
        public String getValidationStatus() { return validationStatus.get(); }
        public void setValidationStatus(String value) { this.validationStatus.set(value); }
    }
    
    /**
     * Custom double string converter that handles formatting.
     */
    private static class DoubleStringConverter extends StringConverter<Double> {
        @Override
        public String toString(Double value) {
            if (value == null) return "";
            return String.format("%.2f", value);
        }
        
        @Override
        public Double fromString(String string) {
            if (string == null || string.trim().isEmpty()) return 0.0;
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
    }
}
