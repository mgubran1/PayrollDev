package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class CashAdvancePanel extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(CashAdvancePanel.class);
    
    private final PayrollAdvances payrollAdvances;
    private final ComboBox<Employee> driverComboBox;
    private final DatePicker weekStartPicker;
    private final ObservableList<PayrollAdvances.AdvanceEntry> advanceData = FXCollections.observableArrayList();
    private TableView<PayrollAdvances.AdvanceEntry> advanceTable;
    
    // UI Components for summary
    private Label totalAdvancedLabel;
    private Label totalRepaidLabel;
    private Label currentBalanceLabel;
    private Label activeAdvancesLabel;
    private Label scheduledRepaymentLabel;
    private Label overdueLabel;
    private ProgressBar repaymentProgressBar;
    
    // Settings components
    private TextField maxAdvanceField;
    private TextField weeklyLimitField;
    private TextField maxWeeksField;
    private CheckBox allowMultipleCheckBox;
    private Button updateSettingsButton;
    
    public CashAdvancePanel(PayrollAdvances payrollAdvances, ComboBox<Employee> driverComboBox, DatePicker weekStartPicker) {
        this.payrollAdvances = payrollAdvances;
        this.driverComboBox = driverComboBox;
        this.weekStartPicker = weekStartPicker;
        
        setupUI();
        setupListeners();
        updateAdvanceTable();
    }
    
    private void setupUI() {
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #f5f5f5;");
        
        // Header
        VBox headerBox = createHeaderSection();
        
        // Summary section
        GridPane summaryGrid = createSummarySection();
        
        // Action buttons
        HBox actionButtons = createActionButtons();
        
        // Table
        advanceTable = createAdvanceTable();
        VBox.setVgrow(advanceTable, Priority.ALWAYS);
        
        // Main layout
        VBox mainLayout = new VBox(15);
        mainLayout.getChildren().addAll(headerBox, summaryGrid, actionButtons, advanceTable);
        
        setCenter(mainLayout);
    }
    
    private VBox createHeaderSection() {
        VBox headerBox = new VBox(10);
        
        Label titleLabel = new Label("💰 Cash Advance Management");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#d32f2f"));
        
        HBox settingsBox = new HBox(15);
        settingsBox.setAlignment(Pos.CENTER_LEFT);
        settingsBox.setPadding(new Insets(10));
        
        Label settingsLabel = new Label("Employee Settings:");
        settingsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        // Settings fields
        maxAdvanceField = new TextField();
        maxAdvanceField.setPrefWidth(100);
        maxAdvanceField.setPromptText("Max Amount");
        
        weeklyLimitField = new TextField();
        weeklyLimitField.setPrefWidth(100);
        weeklyLimitField.setPromptText("Weekly Limit");
        
        maxWeeksField = new TextField();
        maxWeeksField.setPrefWidth(80);
        maxWeeksField.setPromptText("Max Weeks");
        
        allowMultipleCheckBox = new CheckBox("Allow Multiple");
        
        updateSettingsButton = new Button("Update Settings");
        updateSettingsButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        updateSettingsButton.setOnAction(e -> updateEmployeeSettings());
        
        settingsBox.getChildren().addAll(
            settingsLabel,
            new Label("Max:"), maxAdvanceField,
            new Label("Weekly:"), weeklyLimitField,
            new Label("Weeks:"), maxWeeksField,
            allowMultipleCheckBox,
            updateSettingsButton
        );
        
        headerBox.getChildren().addAll(titleLabel, settingsBox);
        headerBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        return headerBox;
    }
    
    private void updateEmployeeSettings() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            showAlert(Alert.AlertType.WARNING, "No Driver Selected", "Please select a driver to update settings.");
            return;
        }
        
        try {
            PayrollAdvances.AdvanceSettings settings = new PayrollAdvances.AdvanceSettings();
            
            if (!maxAdvanceField.getText().isEmpty()) {
                settings.setMaxAdvanceAmount(new BigDecimal(maxAdvanceField.getText()));
            }
            if (!weeklyLimitField.getText().isEmpty()) {
                settings.setWeeklyRepaymentLimit(new BigDecimal(weeklyLimitField.getText()));
            }
            if (!maxWeeksField.getText().isEmpty()) {
                settings.setMaxRepaymentWeeks(Integer.parseInt(maxWeeksField.getText()));
            }
            settings.setAllowMultipleAdvances(allowMultipleCheckBox.isSelected());
            
            payrollAdvances.updateEmployeeSettings(selectedDriver, settings);
            
            showAlert(Alert.AlertType.INFORMATION, "Settings Updated", 
                "Settings updated successfully for " + selectedDriver.getName());
            
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter valid numbers.");
        }
    }
    
    private GridPane createSummarySection() {
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(30);
        summaryGrid.setVgap(10);
        summaryGrid.setPadding(new Insets(20));
        summaryGrid.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Initialize labels
        totalAdvancedLabel = createValueLabel("$0.00", Color.BLUE);
        totalRepaidLabel = createValueLabel("$0.00", Color.GREEN);
        currentBalanceLabel = createValueLabel("$0.00", Color.RED);
        activeAdvancesLabel = createValueLabel("0", Color.ORANGE);
        scheduledRepaymentLabel = createValueLabel("$0.00", Color.PURPLE);
        overdueLabel = createValueLabel("None", Color.DARKRED);
        
        // Add to grid
        summaryGrid.add(createFieldLabel("Total Advanced:"), 0, 0);
        summaryGrid.add(totalAdvancedLabel, 1, 0);
        
        summaryGrid.add(createFieldLabel("Total Repaid:"), 2, 0);
        summaryGrid.add(totalRepaidLabel, 3, 0);
        
        summaryGrid.add(createFieldLabel("Current Balance:"), 0, 1);
        summaryGrid.add(currentBalanceLabel, 1, 1);
        
        summaryGrid.add(createFieldLabel("Active Advances:"), 2, 1);
        summaryGrid.add(activeAdvancesLabel, 3, 1);
        
        // Repayment progress
        VBox progressBox = new VBox(5);
        Label progressLabel = new Label("Repayment Progress");
        progressLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        repaymentProgressBar = new ProgressBar(0);
        repaymentProgressBar.setPrefWidth(200);
        repaymentProgressBar.setPrefHeight(20);
        
        progressBox.getChildren().addAll(progressLabel, repaymentProgressBar);
        
        summaryGrid.add(progressBox, 0, 2, 2, 1);
        
        summaryGrid.add(createFieldLabel("This Week's Payment:"), 2, 2);
        summaryGrid.add(scheduledRepaymentLabel, 3, 2);
        
        summaryGrid.add(createFieldLabel("Overdue Status:"), 0, 3);
        summaryGrid.add(overdueLabel, 1, 3);
        
        return summaryGrid;
    }
    
    private void updateSummaryDisplay() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            clearSummaryDisplay();
            return;
        }
        
        Platform.runLater(() -> {
            PayrollAdvances.AdvanceSettings settings = payrollAdvances.getEmployeeSettings(selectedDriver);
            
            // Update settings fields
            maxAdvanceField.setText(settings.getMaxAdvanceAmount().toString());
            weeklyLimitField.setText(settings.getWeeklyRepaymentLimit().toString());
            maxWeeksField.setText(String.valueOf(settings.getMaxRepaymentWeeks()));
            allowMultipleCheckBox.setSelected(settings.isAllowMultipleAdvances());
            
            // Calculate summary values
            BigDecimal totalAdvanced = payrollAdvances.getTotalAdvanced(selectedDriver);
            BigDecimal totalRepaid = payrollAdvances.getTotalRepaid(selectedDriver);
            BigDecimal currentBalance = payrollAdvances.getCurrentBalance(selectedDriver);
            int activeCount = payrollAdvances.getActiveAdvanceCount(selectedDriver);
            
            totalAdvancedLabel.setText(String.format("$%,.2f", totalAdvanced));
            totalRepaidLabel.setText(String.format("$%,.2f", totalRepaid));
            currentBalanceLabel.setText(String.format("$%,.2f", currentBalance));
            activeAdvancesLabel.setText(String.valueOf(activeCount));
            
            // Update progress
            double progress = totalAdvanced.compareTo(BigDecimal.ZERO) > 0 ? 
                totalRepaid.divide(totalAdvanced, 2, RoundingMode.HALF_UP).doubleValue() : 0;
            progress = Math.min(1.0, Math.max(0.0, progress));
            repaymentProgressBar.setProgress(progress);
            
            // Check for this week's scheduled payment
            LocalDate weekStart = weekStartPicker.getValue();
            if (weekStart != null) {
                BigDecimal weeklyAmount = payrollAdvances.getScheduledRepaymentForWeek(selectedDriver, weekStart);
                scheduledRepaymentLabel.setText(String.format("$%,.2f", weeklyAmount));
            }
            
            // Check overdue status
            List<PayrollAdvances.AdvanceEntry> advances = payrollAdvances.getAdvancesForEmployee(selectedDriver);
            long overdueCount = advances.stream()
                .filter(a -> a.getStatus() == PayrollAdvances.AdvanceStatus.ACTIVE)
                .filter(a -> {
                    BigDecimal balance = payrollAdvances.getAdvanceBalance(a.getAdvanceId());
                    return balance.compareTo(BigDecimal.ZERO) > 0 && 
                           LocalDate.now().isAfter(a.getLastRepaymentDate());
                })
                .count();
            
            if (overdueCount > 0) {
                overdueLabel.setText(overdueCount + " overdue");
                overdueLabel.setTextFill(Color.RED);
            } else {
                overdueLabel.setText("None");
                overdueLabel.setTextFill(Color.GREEN);
            }
        });
    }
    
    private void clearSummaryDisplay() {
        Platform.runLater(() -> {
            totalAdvancedLabel.setText("$0.00");
            totalRepaidLabel.setText("$0.00");
            currentBalanceLabel.setText("$0.00");
            activeAdvancesLabel.setText("0");
            scheduledRepaymentLabel.setText("$0.00");
            overdueLabel.setText("None");
            repaymentProgressBar.setProgress(0);
            
            maxAdvanceField.clear();
            weeklyLimitField.clear();
            maxWeeksField.clear();
            allowMultipleCheckBox.setSelected(false);
        });
    }
    
    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        return label;
    }
    
    private Label createValueLabel(String text, Color color) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.NORMAL, 12));
        label.setTextFill(color);
        return label;
    }
    
    private HBox createActionButtons() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 10, 0));
        
        Button createAdvanceBtn = new Button("➕ New Advance");
        createAdvanceBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-weight: bold;");
        createAdvanceBtn.setOnAction(e -> showCreateAdvanceDialog());
        
        Button recordPaymentBtn = new Button("💵 Record Payment");
        recordPaymentBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        recordPaymentBtn.setOnAction(e -> showRecordPaymentDialog());
        
        Button processWeeklyBtn = new Button("📅 Process Weekly");
        processWeeklyBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        processWeeklyBtn.setOnAction(e -> processWeeklyRepayments());
        
        Button viewDetailsBtn = new Button("📋 View Details");
        viewDetailsBtn.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-weight: bold;");
        viewDetailsBtn.setOnAction(e -> showAdvanceDetails());
        
        Button exportBtn = new Button("📊 Export");
        exportBtn.setStyle("-fx-background-color: #9c27b0; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> updateAdvanceTable());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label filterLabel = new Label("Filter:");
        filterLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("All", "Active Only", "Completed", "Overdue");
        filterCombo.setValue("Active Only");
        filterCombo.setOnAction(e -> updateAdvanceTable());
        
        buttonBox.getChildren().addAll(createAdvanceBtn, recordPaymentBtn, processWeeklyBtn, 
            viewDetailsBtn, exportBtn, refreshBtn, spacer, filterLabel, filterCombo);
        
        return buttonBox;
    }
    
    private TableView<PayrollAdvances.AdvanceEntry> createAdvanceTable() {
        TableView<PayrollAdvances.AdvanceEntry> table = new TableView<>();
        table.setItems(advanceData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        // Date column
        TableColumn<PayrollAdvances.AdvanceEntry, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
                }
            }
        });
        dateCol.setPrefWidth(100);
        
        // Type column
        TableColumn<PayrollAdvances.AdvanceEntry, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(entry -> new SimpleStringProperty(entry.getValue().getType()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(type);
                    if (type.equals("ADVANCE")) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else if (type.equals("REPAYMENT")) {
                        setTextFill(Color.GREEN);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.ORANGE);
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
        typeCol.setPrefWidth(100);
        
        // Amount column
        TableColumn<PayrollAdvances.AdvanceEntry, BigDecimal> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", amount.abs()));
                    setAlignment(Pos.CENTER_RIGHT);
                    if (amount.compareTo(BigDecimal.ZERO) < 0) {
                        setTextFill(Color.GREEN);
                    } else {
                        setTextFill(Color.RED);
                    }
                }
            }
        });
        amountCol.setPrefWidth(100);
        
        // Balance column
        TableColumn<PayrollAdvances.AdvanceEntry, BigDecimal> balanceCol = new TableColumn<>("Balance");
        balanceCol.setCellValueFactory(new PropertyValueFactory<>("balance"));
        balanceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", balance));
                    setAlignment(Pos.CENTER_RIGHT);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        balanceCol.setPrefWidth(100);
        
        // Status column
        TableColumn<PayrollAdvances.AdvanceEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(entry -> new SimpleStringProperty(entry.getValue().getStatusDisplay()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "ACTIVE":
                            setTextFill(Color.BLUE);
                            break;
                        case "COMPLETED":
                            setTextFill(Color.GREEN);
                            break;
                        case "DEFAULTED":
                            setTextFill(Color.DARKRED);
                            break;
                        case "FORGIVEN":
                            setTextFill(Color.ORANGE);
                            break;
                        case "CANCELLED":
                            setTextFill(Color.GRAY);
                            break;
                    }
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        statusCol.setPrefWidth(100);
        
        // Notes column
        TableColumn<PayrollAdvances.AdvanceEntry, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(entry -> new SimpleStringProperty(entry.getValue().getNotes()));
        notesCol.setPrefWidth(200);
        
        // Advance ID column (for advances)
        TableColumn<PayrollAdvances.AdvanceEntry, String> advanceIdCol = new TableColumn<>("Advance ID");
        advanceIdCol.setCellValueFactory(entry -> {
            PayrollAdvances.AdvanceEntry e = entry.getValue();
            if (e.getAdvanceType() == PayrollAdvances.AdvanceType.ADVANCE) {
                return new SimpleStringProperty(e.getAdvanceId());
            } else {
                return new SimpleStringProperty(e.getParentAdvanceId());
            }
        });
        advanceIdCol.setPrefWidth(120);
        
        // Actions column
        TableColumn<PayrollAdvances.AdvanceEntry, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");
            
            {
                deleteBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px;");
                deleteBtn.setOnAction(event -> {
                    PayrollAdvances.AdvanceEntry entry = getTableView().getItems().get(getIndex());
                    confirmAndDeleteEntry(entry);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    PayrollAdvances.AdvanceEntry entry = getTableView().getItems().get(getIndex());
                    // Only allow deletion of non-advance entries or cancelled advances
                    if (entry.getAdvanceType() != PayrollAdvances.AdvanceType.ADVANCE || 
                        entry.getStatus() == PayrollAdvances.AdvanceStatus.CANCELLED) {
                        setGraphic(deleteBtn);
                    } else {
                        setGraphic(null);
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });
        actionsCol.setPrefWidth(80);
        
        table.getColumns().setAll(java.util.List.of(
            dateCol, typeCol, amountCol, balanceCol, statusCol,
            advanceIdCol, notesCol, actionsCol));
        
        // Placeholder for empty table
        Label placeholder = new Label("No cash advance entries found");
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        table.setPlaceholder(placeholder);
        
        return table;
    }
    
    private void setupListeners() {
        driverComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateAdvanceTable();
            updateSummaryDisplay();
        });
        
        weekStartPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateSummaryDisplay();
        });
    }
    
    public void updateAdvanceTable() {
        Employee selectedDriver = driverComboBox.getValue();
        
        advanceData.clear();
        
        if (selectedDriver != null) {
            List<PayrollAdvances.AdvanceEntry> entries = payrollAdvances.getEntriesForEmployee(selectedDriver.getId());
            advanceData.addAll(entries);
        } else {
            List<PayrollAdvances.AdvanceEntry> allEntries = payrollAdvances.getAllEntries();
            advanceData.addAll(allEntries);
        }
        
        updateSummaryDisplay();
    }
    
    private void showCreateAdvanceDialog() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            showAlert(Alert.AlertType.WARNING, "No Driver Selected", "Please select a driver first.");
            return;
        }
        
        LocalDate weekStart = weekStartPicker.getValue();
        if (weekStart == null) {
            weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }
        
        Dialog<AdvanceRequest> dialog = new Dialog<>();
        dialog.setTitle("Create Cash Advance");
        dialog.setHeaderText("Create advance for " + selectedDriver.getName());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount");
        
        Spinner<Integer> weeksSpinner = new Spinner<>(1, 52, 4);
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Reason for advance");
        notesArea.setPrefRowCount(3);
        
        TextField approvedByField = new TextField(System.getProperty("user.name", "Manager"));
        
        // Show calculated weekly payment
        Label weeklyPaymentLabel = new Label("Weekly payment: $0.00");
        
        Runnable updateWeeklyPayment = () -> {
            try {
                BigDecimal amount = new BigDecimal(amountField.getText());
                int weeks = weeksSpinner.getValue();
                BigDecimal weekly = amount.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.UP);
                weeklyPaymentLabel.setText(String.format("Weekly payment: $%,.2f", weekly));
            } catch (Exception e) {
                weeklyPaymentLabel.setText("Weekly payment: $0.00");
            }
        };
        
        amountField.textProperty().addListener((obs, old, text) -> updateWeeklyPayment.run());
        weeksSpinner.valueProperty().addListener((obs, old, val) -> updateWeeklyPayment.run());
        
        grid.add(new Label("Amount:"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Weeks to repay:"), 0, 1);
        grid.add(weeksSpinner, 1, 1);
        grid.add(weeklyPaymentLabel, 1, 2);
        grid.add(new Label("Approved by:"), 0, 3);
        grid.add(approvedByField, 1, 3);
        grid.add(new Label("Notes:"), 0, 4);
        grid.add(notesArea, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Platform.runLater(amountField::requestFocus);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    BigDecimal amount = new BigDecimal(amountField.getText());
                    return new AdvanceRequest(amount, weeksSpinner.getValue(), 
                        notesArea.getText(), approvedByField.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
        
        Optional<AdvanceRequest> result = dialog.showAndWait();
        LocalDate finalWeekStart = weekStart;
        
        result.ifPresent(request -> {
            if (request.amount.compareTo(BigDecimal.ZERO) > 0) {
                PayrollAdvances.AdvanceEntry entry = payrollAdvances.createAdvance(
                    selectedDriver, finalWeekStart, request.amount, request.weeks,
                    request.notes, request.approvedBy);
                
                if (entry != null) {
                    updateAdvanceTable();
                    showAlert(Alert.AlertType.INFORMATION, "Advance Created", 
                        String.format("Created advance of $%,.2f for %s", 
                            request.amount, selectedDriver.getName()));
                } else {
                    showAlert(Alert.AlertType.ERROR, "Creation Failed", 
                        "Failed to create advance. Check settings and existing advances.");
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Invalid Amount", "Amount must be greater than zero.");
            }
        });
    }
    
    private void showRecordPaymentDialog() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            showAlert(Alert.AlertType.WARNING, "No Driver Selected", "Please select a driver first.");
            return;
        }
        
        // Get active advances for dropdown
        List<PayrollAdvances.AdvanceEntry> activeAdvances = payrollAdvances.getAdvancesForEmployee(selectedDriver)
            .stream()
            .filter(a -> a.getAdvanceType() == PayrollAdvances.AdvanceType.ADVANCE)
            .filter(a -> a.getStatus() == PayrollAdvances.AdvanceStatus.ACTIVE)
            .collect(java.util.stream.Collectors.toList());
        
        if (activeAdvances.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Active Advances", "No active advances to repay.");
            return;
        }
        
        Dialog<PaymentRequest> dialog = new Dialog<>();
        dialog.setTitle("Record Payment");
        dialog.setHeaderText("Record payment for " + selectedDriver.getName());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        ComboBox<PayrollAdvances.AdvanceEntry> advanceCombo = new ComboBox<>();
        advanceCombo.setItems(FXCollections.observableArrayList(activeAdvances));
        advanceCombo.setConverter(new StringConverter<PayrollAdvances.AdvanceEntry>() {
            @Override
            public String toString(PayrollAdvances.AdvanceEntry entry) {
                if (entry == null) return "";
                BigDecimal balance = payrollAdvances.getAdvanceBalance(entry.getAdvanceId());
                return String.format("%s - $%,.2f (Balance: $%,.2f)", 
                    entry.getAdvanceId(), entry.getAmount(), balance);
            }
            
            @Override
            public PayrollAdvances.AdvanceEntry fromString(String string) {
                return null;
            }
        });
        advanceCombo.getSelectionModel().selectFirst();
        
        TextField amountField = new TextField();
        amountField.setPromptText("Payment amount");
        
        ComboBox<PayrollAdvances.PaymentMethod> methodCombo = new ComboBox<>();
        methodCombo.setItems(FXCollections.observableArrayList(PayrollAdvances.PaymentMethod.values()));
        methodCombo.setValue(PayrollAdvances.PaymentMethod.PAYROLL_DEDUCTION);
        
        TextField referenceField = new TextField();
        referenceField.setPromptText("Reference number (optional)");
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Payment notes");
        notesArea.setPrefRowCount(2);
        
        TextField processedByField = new TextField(System.getProperty("user.name", "Manager"));
        
        grid.add(new Label("Advance:"), 0, 0);
        grid.add(advanceCombo, 1, 0);
        grid.add(new Label("Amount:"), 0, 1);
        grid.add(amountField, 1, 1);
        grid.add(new Label("Method:"), 0, 2);
        grid.add(methodCombo, 1, 2);
        grid.add(new Label("Reference:"), 0, 3);
        grid.add(referenceField, 1, 3);
        grid.add(new Label("Processed by:"), 0, 4);
        grid.add(processedByField, 1, 4);
        grid.add(new Label("Notes:"), 0, 5);
        grid.add(notesArea, 1, 5);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Platform.runLater(amountField::requestFocus);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    PayrollAdvances.AdvanceEntry advance = advanceCombo.getValue();
                    if (advance == null) return null;
                    
                    BigDecimal amount = new BigDecimal(amountField.getText());
                    return new PaymentRequest(advance.getAdvanceId(), amount, 
                        methodCombo.getValue(), referenceField.getText(),
                        notesArea.getText(), processedByField.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
        
        Optional<PaymentRequest> result = dialog.showAndWait();
        
        result.ifPresent(request -> {
            if (request.amount.compareTo(BigDecimal.ZERO) > 0) {
                LocalDate weekStart = weekStartPicker.getValue() != null ? 
                    weekStartPicker.getValue() : LocalDate.now();
                
                PayrollAdvances.AdvanceEntry entry = payrollAdvances.recordRepayment(
                    selectedDriver, weekStart, request.amount, request.advanceId,
                    request.method, request.reference, request.notes, request.processedBy);
                
                if (entry != null) {
                    updateAdvanceTable();
                    showAlert(Alert.AlertType.INFORMATION, "Payment Recorded", 
                        String.format("Recorded payment of $%,.2f", request.amount));
                } else {
                    showAlert(Alert.AlertType.ERROR, "Recording Failed", 
                        "Failed to record payment.");
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Invalid Amount", "Amount must be greater than zero.");
            }
        });
    }
    
    private void processWeeklyRepayments() {
        Employee selectedDriver = driverComboBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        
        if (selectedDriver == null || weekStart == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Information", 
                "Please select both a driver and week start date.");
            return;
        }
        
        BigDecimal weeklyAmount = payrollAdvances.getScheduledRepaymentForWeek(selectedDriver, weekStart);
        
        if (weeklyAmount.compareTo(BigDecimal.ZERO) == 0) {
            showAlert(Alert.AlertType.INFORMATION, "No Payments Due", 
                "No scheduled repayments for this week.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Process Weekly Repayments");
        confirm.setHeaderText("Confirm Weekly Deduction");
        confirm.setContentText(String.format(
            "Process weekly repayment for %s?\nAmount: $%,.2f",
            selectedDriver.getName(), weeklyAmount));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Record repayments for all active advances
            List<PayrollAdvances.AdvanceEntry> activeAdvances = payrollAdvances.getAdvancesForEmployee(selectedDriver)
                .stream()
                .filter(a -> a.getAdvanceType() == PayrollAdvances.AdvanceType.ADVANCE)
                .filter(a -> a.getStatus() == PayrollAdvances.AdvanceStatus.ACTIVE)
                .collect(java.util.stream.Collectors.toList());
            
            int processed = 0;
            for (PayrollAdvances.AdvanceEntry advance : activeAdvances) {
                BigDecimal advanceWeekly = advance.getWeeklyRepaymentAmount();
                if (advanceWeekly != null && advanceWeekly.compareTo(BigDecimal.ZERO) > 0) {
                    PayrollAdvances.AdvanceEntry payment = payrollAdvances.recordRepayment(
                        selectedDriver, weekStart, advanceWeekly, advance.getAdvanceId(),
                        PayrollAdvances.PaymentMethod.PAYROLL_DEDUCTION, 
                        "WEEK-" + weekStart.format(DateTimeFormatter.ofPattern("MMdd")),
                        "Weekly payroll deduction", System.getProperty("user.name", "System"));
                    
                    if (payment != null) {
                        processed++;
                    }
                }
            }
            
            if (processed > 0) {
                updateAdvanceTable();
                showAlert(Alert.AlertType.INFORMATION, "Payments Processed", 
                    String.format("Processed %d weekly repayment(s) totaling $%,.2f", 
                        processed, weeklyAmount));
            }
        }
    }
    
    private void showAdvanceDetails() {
        PayrollAdvances.AdvanceEntry selected = advanceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an entry to view details.");
            return;
        }
        
        if (selected.getAdvanceType() != PayrollAdvances.AdvanceType.ADVANCE) {
            showAlert(Alert.AlertType.INFORMATION, "Entry Details", 
                String.format("%s\nAmount: $%,.2f\nDate: %s\nNotes: %s",
                    selected.getType(),
                    selected.getAmount().abs(),
                    selected.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                    selected.getNotes()));
            return;
        }
        
        // Show advance details with repayment history
        Stage detailStage = new Stage();
        detailStage.setTitle("Advance Details - " + selected.getAdvanceId());
        detailStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        
        // Summary section
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(15);
        summaryGrid.setVgap(5);
        
        int row = 0;
        summaryGrid.add(new Label("Advance ID:"), 0, row);
        summaryGrid.add(new Label(selected.getAdvanceId()), 1, row++);
        
        summaryGrid.add(new Label("Date:"), 0, row);
        summaryGrid.add(new Label(selected.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))), 1, row++);
        
        summaryGrid.add(new Label("Amount:"), 0, row);
        summaryGrid.add(new Label(String.format("$%,.2f", selected.getAmount())), 1, row++);
        
        summaryGrid.add(new Label("Status:"), 0, row);
        Label statusLabel = new Label(selected.getStatusDisplay());
        if (selected.getStatus() == PayrollAdvances.AdvanceStatus.ACTIVE) {
            statusLabel.setTextFill(Color.BLUE);
        } else if (selected.getStatus() == PayrollAdvances.AdvanceStatus.COMPLETED) {
            statusLabel.setTextFill(Color.GREEN);
        }
        summaryGrid.add(statusLabel, 1, row++);
        
        BigDecimal balance = payrollAdvances.getAdvanceBalance(selected.getAdvanceId());
        summaryGrid.add(new Label("Balance:"), 0, row);
        summaryGrid.add(new Label(String.format("$%,.2f", balance)), 1, row++);
        
        summaryGrid.add(new Label("Weekly Payment:"), 0, row);
        summaryGrid.add(new Label(String.format("$%,.2f", selected.getWeeklyRepaymentAmount())), 1, row++);
        
        summaryGrid.add(new Label("Notes:"), 0, row);
        summaryGrid.add(new Label(selected.getNotes()), 1, row++);
        
        // Repayment history table
        TableView<PayrollAdvances.AdvanceEntry> repaymentTable = new TableView<>();
        List<PayrollAdvances.AdvanceEntry> repayments = payrollAdvances.getRepaymentsForAdvance(selected.getAdvanceId());
        repaymentTable.setItems(FXCollections.observableArrayList(repayments));
        
        TableColumn<PayrollAdvances.AdvanceEntry, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(entry -> new SimpleStringProperty(
            entry.getValue().getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))));
        
        TableColumn<PayrollAdvances.AdvanceEntry, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(entry -> new SimpleStringProperty(
            String.format("$%,.2f", entry.getValue().getAmount().abs())));
        
        TableColumn<PayrollAdvances.AdvanceEntry, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(entry -> new SimpleStringProperty(
            entry.getValue().getPaymentMethod() != null ? 
            entry.getValue().getPaymentMethod().toString() : ""));
        
        TableColumn<PayrollAdvances.AdvanceEntry, String> refCol = new TableColumn<>("Reference");
        refCol.setCellValueFactory(entry -> new SimpleStringProperty(
            entry.getValue().getReferenceNumber() != null ? 
            entry.getValue().getReferenceNumber() : ""));
        
        repaymentTable.getColumns().setAll(java.util.List.of(dateCol, amountCol, methodCol, refCol));
        repaymentTable.setPrefHeight(200);
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> detailStage.close());
        
        root.getChildren().addAll(
            new Label("Advance Details"),
            new Separator(),
            summaryGrid,
            new Label("Repayment History"),
            repaymentTable,
            closeBtn
        );
        
        Scene scene = new Scene(root, 600, 500);
        detailStage.setScene(scene);
        detailStage.show();
    }
    
    private void confirmAndDeleteEntry(PayrollAdvances.AdvanceEntry entry) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Entry");
        confirm.setContentText(String.format("Delete %s entry of $%,.2f on %s?",
            entry.getType(), entry.getAmount().abs(),
            entry.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            payrollAdvances.deleteEntry(entry.getId());
            updateAdvanceTable();
            showAlert(Alert.AlertType.INFORMATION, "Entry Deleted", "The entry has been deleted.");
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    // Helper classes for dialogs
    private static class AdvanceRequest {
        final BigDecimal amount;
        final int weeks;
        final String notes;
        final String approvedBy;
        
        AdvanceRequest(BigDecimal amount, int weeks, String notes, String approvedBy) {
            this.amount = amount;
            this.weeks = weeks;
            this.notes = notes;
            this.approvedBy = approvedBy;
        }
    }
    
    private static class PaymentRequest {
        final String advanceId;
        final BigDecimal amount;
        final PayrollAdvances.PaymentMethod method;
        final String reference;
        final String notes;
        final String processedBy;
        
        PaymentRequest(String advanceId, BigDecimal amount, PayrollAdvances.PaymentMethod method,
                      String reference, String notes, String processedBy) {
            this.advanceId = advanceId;
            this.amount = amount;
            this.method = method;
            this.reference = reference;
            this.notes = notes;
            this.processedBy = processedBy;
        }
    }
}