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

public class PayrollEscrowPanel extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(PayrollEscrowPanel.class);
    
    private final PayrollEscrow payrollEscrow;
    private final ComboBox<Employee> driverComboBox;
    private final DatePicker weekStartPicker;
    private final ObservableList<PayrollEscrow.EscrowEntry> escrowData = FXCollections.observableArrayList();
    private TableView<PayrollEscrow.EscrowEntry> escrowTable;
    
    // UI Components for summary
    private Label totalDepositsLabel;
    private Label totalWithdrawalsLabel;
    private Label currentBalanceLabel;
    private Label remainingToTargetLabel;
    private Label escrowStatusLabel;
    private Label suggestedWeeklyLabel;
    private ProgressBar escrowProgressBar;
    
    // Add TextField for editable target amount
    private TextField targetAmountField;
    private Button updateTargetButton;
    private static final BigDecimal DEFAULT_TARGET = new BigDecimal("3000.00");
    
    public PayrollEscrowPanel(PayrollEscrow payrollEscrow, ComboBox<Employee> driverComboBox, DatePicker weekStartPicker) {
        this.payrollEscrow = payrollEscrow;
        this.driverComboBox = driverComboBox;
        this.weekStartPicker = weekStartPicker;
        
        setupUI();
        setupListeners();
        updateEscrowTable();
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
        escrowTable = createEscrowTable();
        VBox.setVgrow(escrowTable, Priority.ALWAYS);
        
        // Main layout
        VBox mainLayout = new VBox(15);
        mainLayout.getChildren().addAll(headerBox, summaryGrid, actionButtons, escrowTable);
        
        setCenter(mainLayout);
    }
    
    private VBox createHeaderSection() {
        VBox headerBox = new VBox(10);
        
        Label titleLabel = new Label("🏦 Escrow Account Management");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1976d2"));
        
        HBox targetBox = new HBox(10);
        targetBox.setAlignment(Pos.CENTER_LEFT);
        
        Label targetLabel = new Label("Target Escrow Amount:");
        targetLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Create editable text field for target amount
        targetAmountField = new TextField();
        targetAmountField.setPrefWidth(120);
        targetAmountField.setPromptText("Enter amount");
        targetAmountField.setText(DEFAULT_TARGET.toString());
        targetAmountField.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Add formatter to ensure valid decimal input
        targetAmountField.setTextFormatter(new TextFormatter<>(new StringConverter<BigDecimal>() {
            @Override
            public String toString(BigDecimal value) {
                return value != null ? value.toString() : "";
            }
            
            @Override
            public BigDecimal fromString(String text) {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException e) {
                    return DEFAULT_TARGET;
                }
            }
        }, DEFAULT_TARGET, change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*\\.?\\d{0,2}")) {
                return change;
            }
            return null;
        }));
        
        // Update button
        updateTargetButton = new Button("Update");
        updateTargetButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        updateTargetButton.setOnAction(e -> updateTargetAmount());
        
        // Currency label
        Label currencyLabel = new Label("$");
        currencyLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        targetBox.getChildren().addAll(targetLabel, currencyLabel, targetAmountField, updateTargetButton);
        
        headerBox.getChildren().addAll(titleLabel, targetBox);
        headerBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        return headerBox;
    }
    
    private void updateTargetAmount() {
        try {
            BigDecimal newTarget = new BigDecimal(targetAmountField.getText());
            if (newTarget.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert(Alert.AlertType.WARNING, "Invalid Amount", "Target amount must be greater than zero.");
                return;
            }
            
            Employee selectedDriver = driverComboBox.getValue();
            if (selectedDriver == null) {
                showAlert(Alert.AlertType.WARNING, "No Driver Selected", "Please select a driver to update their target amount.");
                return;
            }
            
            // Update the target amount for the selected driver
            payrollEscrow.setTargetAmount(selectedDriver, newTarget);
            
            // Update the display
            updateSummaryDisplay();
            
            showAlert(Alert.AlertType.INFORMATION, "Target Updated", 
                String.format("Target escrow amount updated to $%,.2f for %s", newTarget, selectedDriver.getName()));
            
            logger.info("Updated target escrow amount to ${} for driver {}", newTarget, selectedDriver.getName());
            
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid number.");
        }
    }
    
    private GridPane createSummarySection() {
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(30);
        summaryGrid.setVgap(10);
        summaryGrid.setPadding(new Insets(20));
        summaryGrid.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        // Initialize labels
        totalDepositsLabel = createValueLabel("$0.00", Color.GREEN);
        totalWithdrawalsLabel = createValueLabel("$0.00", Color.RED);
        currentBalanceLabel = createValueLabel("$0.00", Color.BLUE);
        remainingToTargetLabel = createValueLabel("$0.00", Color.ORANGE);
        escrowStatusLabel = createValueLabel("In Progress", Color.GRAY);
        suggestedWeeklyLabel = createValueLabel("$0.00", Color.PURPLE);
        
        // Add to grid
        summaryGrid.add(createFieldLabel("Total Deposits:"), 0, 0);
        summaryGrid.add(totalDepositsLabel, 1, 0);
        
        summaryGrid.add(createFieldLabel("Total Withdrawals:"), 2, 0);
        summaryGrid.add(totalWithdrawalsLabel, 3, 0);
        
        summaryGrid.add(createFieldLabel("Current Balance:"), 0, 1);
        summaryGrid.add(currentBalanceLabel, 1, 1);
        
        summaryGrid.add(createFieldLabel("Remaining to Target:"), 2, 1);
        summaryGrid.add(remainingToTargetLabel, 3, 1);
        
        // Escrow status with progress bar
        VBox statusBox = new VBox(5);
        escrowStatusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        escrowProgressBar = new ProgressBar(0);
        escrowProgressBar.setPrefWidth(200);
        escrowProgressBar.setPrefHeight(20);
        
        statusBox.getChildren().addAll(escrowStatusLabel, escrowProgressBar);
        
        summaryGrid.add(createFieldLabel("Escrow Status:"), 0, 2);
        summaryGrid.add(statusBox, 1, 2, 3, 1);
        
        summaryGrid.add(createFieldLabel("Suggested weekly deduction:"), 0, 3);
        summaryGrid.add(suggestedWeeklyLabel, 1, 3);
        
        return summaryGrid;
    }
    
    private void updateSummaryDisplay() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            clearSummaryDisplay();
            return;
        }
        
        Platform.runLater(() -> {
            BigDecimal deposits = payrollEscrow.getTotalDeposits(selectedDriver);
            BigDecimal withdrawals = payrollEscrow.getTotalWithdrawals(selectedDriver);
            BigDecimal balance = payrollEscrow.getCurrentBalance(selectedDriver);
            BigDecimal target = payrollEscrow.getTargetAmount(selectedDriver);
            BigDecimal remaining = payrollEscrow.getRemainingToTarget(selectedDriver);
            
            // Update target amount field with driver's specific target
            targetAmountField.setText(target.toString());
            
            totalDepositsLabel.setText(String.format("$%,.2f", deposits));
            totalWithdrawalsLabel.setText(String.format("$%,.2f", withdrawals));
            currentBalanceLabel.setText(String.format("$%,.2f", balance));
            remainingToTargetLabel.setText(String.format("$%,.2f", remaining.max(BigDecimal.ZERO)));
            
            // Update progress
            double progress = target.compareTo(BigDecimal.ZERO) > 0 ? 
                balance.divide(target, 2, RoundingMode.HALF_UP).doubleValue() : 0;
            progress = Math.min(1.0, Math.max(0.0, progress));
            escrowProgressBar.setProgress(progress);
            
            // Update status
            if (balance.compareTo(target) >= 0) {
                escrowStatusLabel.setText("Fully Funded");
                escrowStatusLabel.setTextFill(Color.GREEN);
                suggestedWeeklyLabel.setText("$0.00");
            } else {
                escrowStatusLabel.setText("In Progress");
                escrowStatusLabel.setTextFill(Color.ORANGE);
                
                // Calculate suggested weekly amount (target completion in 6 weeks)
                BigDecimal weeksToComplete = new BigDecimal("6");
                BigDecimal suggested = remaining.divide(weeksToComplete, 2, RoundingMode.CEILING);
                suggested = suggested.max(new BigDecimal("50")).min(new BigDecimal("500"));
                suggestedWeeklyLabel.setText(String.format("$%,.2f", suggested));
            }
        });
    }
    
    private void clearSummaryDisplay() {
        Platform.runLater(() -> {
            totalDepositsLabel.setText("$0.00");
            totalWithdrawalsLabel.setText("$0.00");
            currentBalanceLabel.setText("$0.00");
            remainingToTargetLabel.setText("$0.00");
            escrowStatusLabel.setText("No Driver Selected");
            escrowStatusLabel.setTextFill(Color.GRAY);
            suggestedWeeklyLabel.setText("$0.00");
            escrowProgressBar.setProgress(0);
            targetAmountField.setText(DEFAULT_TARGET.toString());
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
        
        Button addDepositBtn = new Button("➕ Add Deposit");
        addDepositBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        addDepositBtn.setOnAction(e -> showAddDepositDialog());
        
        Button withdrawBtn = new Button("➖ Withdraw");
        withdrawBtn.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-weight: bold;");
        withdrawBtn.setOnAction(e -> showWithdrawDialog());
        
        Button exportBtn = new Button("📊 Export");
        exportBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> updateEscrowTable());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label filterLabel = new Label("Filter by driver:");
        filterLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        buttonBox.getChildren().addAll(addDepositBtn, withdrawBtn, exportBtn, refreshBtn, spacer, filterLabel);
        
        return buttonBox;
    }
    
    private TableView<PayrollEscrow.EscrowEntry> createEscrowTable() {
        TableView<PayrollEscrow.EscrowEntry> table = new TableView<>();
        table.setItems(escrowData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        // Date column
        TableColumn<PayrollEscrow.EscrowEntry, LocalDate> dateCol = new TableColumn<>("Date");
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
        
        // Week column
        TableColumn<PayrollEscrow.EscrowEntry, String> weekCol = new TableColumn<>("Week");
        weekCol.setCellValueFactory(entry -> {
            LocalDate date = entry.getValue().getDate();
            LocalDate weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            return new SimpleStringProperty(weekStart.format(DateTimeFormatter.ofPattern("MM/dd")));
        });
        weekCol.setPrefWidth(80);
        
        // Driver column
        TableColumn<PayrollEscrow.EscrowEntry, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(entry -> new SimpleStringProperty(entry.getValue().getDriverName()));
        driverCol.setPrefWidth(150);
        
        // Type column
        TableColumn<PayrollEscrow.EscrowEntry, String> typeCol = new TableColumn<>("Type");
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
                    if (type.equals("DEPOSIT")) {
                        setTextFill(Color.GREEN);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
        typeCol.setPrefWidth(100);
        
        // Amount column
        TableColumn<PayrollEscrow.EscrowEntry, BigDecimal> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", amount));
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });
        amountCol.setPrefWidth(100);
        
        // Balance column
        TableColumn<PayrollEscrow.EscrowEntry, BigDecimal> balanceCol = new TableColumn<>("Balance");
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
        
        // Remaining column
        TableColumn<PayrollEscrow.EscrowEntry, BigDecimal> remainingCol = new TableColumn<>("Remaining");
        remainingCol.setCellValueFactory(entry -> {
            BigDecimal balance = entry.getValue().getBalance();
            Employee driver = getEmployeeByName(entry.getValue().getDriverName());
            if (driver != null) {
                BigDecimal target = payrollEscrow.getTargetAmount(driver);
                BigDecimal remaining = target.subtract(balance).max(BigDecimal.ZERO);
                return new SimpleObjectProperty<>(remaining);
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        remainingCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal remaining, boolean empty) {
                super.updateItem(remaining, empty);
                if (empty || remaining == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", remaining));
                    setAlignment(Pos.CENTER_RIGHT);
                    if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                        setTextFill(Color.GREEN);
                    } else {
                        setTextFill(Color.ORANGE);
                    }
                }
            }
        });
        remainingCol.setPrefWidth(100);
        
        // Notes column
        TableColumn<PayrollEscrow.EscrowEntry, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(entry -> new SimpleStringProperty(entry.getValue().getNotes()));
        notesCol.setPrefWidth(200);
        
        // Actions column
        TableColumn<PayrollEscrow.EscrowEntry, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");
            
            {
                deleteBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px;");
                deleteBtn.setOnAction(event -> {
                    PayrollEscrow.EscrowEntry entry = getTableView().getItems().get(getIndex());
                    confirmAndDeleteEntry(entry);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                    setAlignment(Pos.CENTER);
                }
            }
        });
        actionsCol.setPrefWidth(80);
        
        table.getColumns().addAll(dateCol, weekCol, driverCol, typeCol, amountCol, balanceCol, remainingCol, notesCol, actionsCol);
        
        // Placeholder for empty table
        Label placeholder = new Label("No escrow entries found");
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        table.setPlaceholder(placeholder);
        
        return table;
    }
    
    private void setupListeners() {
        driverComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateEscrowTable();
            updateSummaryDisplay();
        });
        
        weekStartPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateEscrowTable();
        });
    }
    
    public void updateEscrowTable() {
        Employee selectedDriver = driverComboBox.getValue();
        
        escrowData.clear();
        
        if (selectedDriver != null) {
            List<PayrollEscrow.EscrowEntry> entries = payrollEscrow.getEntriesForDriver(selectedDriver.getId());
            escrowData.addAll(entries);
        } else {
            List<PayrollEscrow.EscrowEntry> allEntries = payrollEscrow.getAllEntries();
            escrowData.addAll(allEntries);
        }
        
        updateSummaryDisplay();
    }
    
    private void showAddDepositDialog() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            showAlert(Alert.AlertType.WARNING, "No Driver Selected", "Please select a driver first.");
            return;
        }
        
        LocalDate weekStart = weekStartPicker.getValue();
        if (weekStart == null) {
            weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }
        
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Add Escrow Deposit");
        dialog.setHeaderText("Add deposit for " + selectedDriver.getName());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount");
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Optional notes");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Amount:"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Notes:"), 0, 1);
        grid.add(notesArea, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Platform.runLater(amountField::requestFocus);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    return new BigDecimal(amountField.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
        
        Optional<BigDecimal> result = dialog.showAndWait();
        LocalDate finalWeekStart = weekStart;
        
        result.ifPresent(amount -> {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                payrollEscrow.addDeposit(selectedDriver, finalWeekStart, amount, notesArea.getText());
                updateEscrowTable();
                showAlert(Alert.AlertType.INFORMATION, "Deposit Added", 
                    String.format("Added $%,.2f deposit for %s", amount, selectedDriver.getName()));
            } else {
                showAlert(Alert.AlertType.ERROR, "Invalid Amount", "Amount must be greater than zero.");
            }
        });
    }
    
    private void showWithdrawDialog() {
        Employee selectedDriver = driverComboBox.getValue();
        if (selectedDriver == null) {
            showAlert(Alert.AlertType.WARNING, "No Driver Selected", "Please select a driver first.");
            return;
        }
        
        BigDecimal currentBalance = payrollEscrow.getCurrentBalance(selectedDriver);
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            showAlert(Alert.AlertType.WARNING, "Insufficient Balance", "No funds available to withdraw.");
            return;
        }
        
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Withdraw from Escrow");
        dialog.setHeaderText(String.format("Withdraw from %s's escrow (Balance: $%,.2f)", 
            selectedDriver.getName(), currentBalance));
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount");
        
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Reason for withdrawal");
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Amount:"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Reason:"), 0, 1);
        grid.add(notesArea, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Platform.runLater(amountField::requestFocus);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    return new BigDecimal(amountField.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
        
        Optional<BigDecimal> result = dialog.showAndWait();
        
        result.ifPresent(amount -> {
            if (amount.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(currentBalance) <= 0) {
                payrollEscrow.addWithdrawal(selectedDriver, LocalDate.now(), amount, notesArea.getText());
                updateEscrowTable();
                showAlert(Alert.AlertType.INFORMATION, "Withdrawal Completed", 
                    String.format("Withdrew $%,.2f from %s's escrow", amount, selectedDriver.getName()));
            } else {
                showAlert(Alert.AlertType.ERROR, "Invalid Amount", 
                    "Amount must be greater than zero and not exceed the current balance.");
            }
        });
    }
    
    private void confirmAndDeleteEntry(PayrollEscrow.EscrowEntry entry) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Escrow Entry");
        confirm.setContentText(String.format("Delete %s of $%,.2f for %s on %s?",
            entry.getType(), entry.getAmount(), entry.getDriverName(),
            entry.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            payrollEscrow.deleteEntry(entry.getId());
            updateEscrowTable();
            showAlert(Alert.AlertType.INFORMATION, "Entry Deleted", "The escrow entry has been deleted.");
        }
    }
    
    private Employee getEmployeeByName(String name) {
        for (int i = 0; i < driverComboBox.getItems().size(); i++) {
            Employee emp = driverComboBox.getItems().get(i);
            if (emp != null && emp.getName().equals(name)) {
                return emp;
            }
        }
        return null;
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}