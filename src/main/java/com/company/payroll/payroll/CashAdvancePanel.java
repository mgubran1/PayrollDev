package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class CashAdvancePanel extends VBox {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private final PayrollAdvances payrollAdvances;
    private final ComboBox<Employee> driverBox;
    private final DatePicker weekStartPicker;
    private final ObservableList<PayrollAdvances.Advance> advancesRows = FXCollections.observableArrayList();
    private final TableView<PayrollAdvances.Advance> advancesTable = new TableView<>(advancesRows);
    private final ObservableList<PayrollAdvances.Advance> advancesHistoryRows = FXCollections.observableArrayList();
    private final TableView<PayrollAdvances.Advance> advancesHistoryTable = new TableView<>(advancesHistoryRows);
    
    // Buttons
    private final Button addAdvanceBtn = new Button("Add Cash Advance");
    private final Button viewDetailsBtn = new Button("View Details");
    private final Button changeStatusBtn = new Button("Change Status");
    private final Button refreshAdvanceBtn = new Button("Refresh");
    private final Button markPaidAdvanceBtn = new Button("Process This Week's Payment");
    private final Button viewScheduleBtn = new Button("View/Edit Schedule");
    private final Button addManualPaymentBtn = new Button("Add Manual Payment");
    private final Button viewAdvanceHistoryBtn = new Button("View History");
    private final Button viewSummaryBtn = new Button("View Summary");
    private final Button viewAuditBtn = new Button("Audit Trail");
    
    private final Label cashAdvanceRedNote = new Label();
    private final Label summaryLabel = new Label();

    public CashAdvancePanel(PayrollAdvances payrollAdvances, ComboBox<Employee> driverBox, DatePicker weekStartPicker) {
        this.payrollAdvances = payrollAdvances;
        this.driverBox = driverBox;
        this.weekStartPicker = weekStartPicker;

        setSpacing(8);
        setPadding(new Insets(8));

        buildAdvancesTable();
        buildAdvancesHistoryTable();

        cashAdvanceRedNote.setWrapText(true);
        cashAdvanceRedNote.setStyle("-fx-text-fill: red; -fx-font-size: 15px; -fx-font-weight: bold;");
        cashAdvanceRedNote.setVisible(false);
        
        summaryLabel.setStyle("-fx-font-size: 14px; -fx-padding: 5;");

        HBox advBtnBox1 = new HBox(8, addAdvanceBtn, viewDetailsBtn, changeStatusBtn, markPaidAdvanceBtn, viewScheduleBtn);
        HBox advBtnBox2 = new HBox(8, addManualPaymentBtn, viewAdvanceHistoryBtn, viewSummaryBtn, viewAuditBtn, refreshAdvanceBtn);
        advBtnBox1.setAlignment(Pos.CENTER_LEFT);
        advBtnBox2.setAlignment(Pos.CENTER_LEFT);

        VBox buttonContainer = new VBox(5, advBtnBox1, advBtnBox2);

        getChildren().addAll(buttonContainer, summaryLabel, cashAdvanceRedNote, advancesTable);

        // Wire up events
        addAdvanceBtn.setOnAction(e -> addAdvanceDialog());
        viewDetailsBtn.setOnAction(e -> viewAdvanceDetails());
        changeStatusBtn.setOnAction(e -> changeAdvanceStatus());
        markPaidAdvanceBtn.setOnAction(e -> markAdvanceRepaymentPaid());
        refreshAdvanceBtn.setOnAction(e -> updateAdvancesTable());
        viewScheduleBtn.setOnAction(e -> viewEditScheduleDialog());
        addManualPaymentBtn.setOnAction(e -> addManualPaymentDialog());
        viewAdvanceHistoryBtn.setOnAction(e -> showAdvanceHistory());
        viewSummaryBtn.setOnAction(e -> showEmployeeSummary());
        viewAuditBtn.setOnAction(e -> showAuditTrail());
        
        advancesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            boolean hasSelection = newSel != null;
            viewDetailsBtn.setDisable(!hasSelection);
            changeStatusBtn.setDisable(!hasSelection);
            viewScheduleBtn.setDisable(!hasSelection);
            addManualPaymentBtn.setDisable(!hasSelection || newSel.getStatus() != PayrollAdvances.Advance.AdvanceStatus.ACTIVE);
        });
        
        viewDetailsBtn.setDisable(true);
        changeStatusBtn.setDisable(true);
        viewScheduleBtn.setDisable(true);
        addManualPaymentBtn.setDisable(true);

        driverBox.valueProperty().addListener((obs, o, n) -> {
            updateAdvancesTable();
            updateSummary();
        });

        updateAdvancesTable();
        updateSummary();
    }

    private void buildAdvancesTable() {
        advancesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        advancesTable.setPrefHeight(300);
        advancesTable.setStyle("-fx-background-color: #fff; -fx-font-size: 15px;");
        advancesTable.setPlaceholder(new Label("No advances for this employee."));

        TableColumn<PayrollAdvances.Advance, String> dateCol = new TableColumn<>("Date Given");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getDateGiven().format(DATE_FORMAT)));
        
        TableColumn<PayrollAdvances.Advance, Number> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
            cell.getValue().getTotalAmount().doubleValue()));
        amountCol.setCellFactory(col -> new TableCell<PayrollAdvances.Advance, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", item.doubleValue()));
                }
            }
        });
        
        TableColumn<PayrollAdvances.Advance, String> notesCol = new TableColumn<>("Reason/Notes");
        notesCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getNotes() == null ? "" : cell.getValue().getNotes()));
        
        TableColumn<PayrollAdvances.Advance, Number> weeksCol = new TableColumn<>("Weeks");
        weeksCol.setCellValueFactory(cell -> new SimpleIntegerProperty(
            cell.getValue().getWeeksToRepay()));
        
        TableColumn<PayrollAdvances.Advance, Number> remainingCol = new TableColumn<>("Remaining");
        remainingCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
            cell.getValue().getRemainingBalance().doubleValue()));
        remainingCol.setCellFactory(col -> new TableCell<PayrollAdvances.Advance, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", item.doubleValue()));
                }
            }
        });
        
        TableColumn<PayrollAdvances.Advance, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getStatus().getDisplayName()));
        statusCol.setCellFactory(col -> new TableCell<PayrollAdvances.Advance, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    PayrollAdvances.Advance advance = getTableRow().getItem();
                    if (advance != null) {
                        switch (advance.getStatus()) {
                            case ACTIVE:
                                if (advance.isOverdue()) {
                                    setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                                } else {
                                    setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
                                }
                                break;
                            case COMPLETED:
                                setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                                break;
                            case DEFAULTED:
                                setStyle("-fx-text-fill: darkred; -fx-font-weight: bold;");
                                break;
                            case FORGIVEN:
                                setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                                break;
                            case CANCELLED:
                                setStyle("-fx-text-fill: gray; -fx-font-weight: bold;");
                                break;
                            default:
                                setStyle("");
                        }
                    }
                }
            }
        });
        
        TableColumn<PayrollAdvances.Advance, String> overdueCol = new TableColumn<>("Overdue");
        overdueCol.setCellValueFactory(cell -> {
            PayrollAdvances.Advance advance = cell.getValue();
            if (advance.isOverdue()) {
                long missedCount = advance.getMissedPaymentCount();
                return new SimpleStringProperty(missedCount + " payment(s)");
            }
            return new SimpleStringProperty("");
        });
        overdueCol.setCellFactory(col -> new TableCell<PayrollAdvances.Advance, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }
        });
        
        advancesTable.getColumns().addAll(dateCol, amountCol, notesCol, weeksCol, 
            remainingCol, statusCol, overdueCol);
        
        // Row styling
        advancesTable.setRowFactory(tv -> new TableRow<PayrollAdvances.Advance>() {
            @Override
            protected void updateItem(PayrollAdvances.Advance item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.isOverdue()) {
                    setStyle("-fx-background-color: #ffcccc;");
                } else if (item.getStatus() == PayrollAdvances.Advance.AdvanceStatus.COMPLETED) {
                    setStyle("-fx-background-color: #ccffcc;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void buildAdvancesHistoryTable() {
        advancesHistoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        advancesHistoryTable.setPrefHeight(400);
        advancesHistoryTable.setStyle("-fx-background-color: #fff; -fx-font-size: 15px;");
        advancesHistoryTable.setPlaceholder(new Label("No advance history for this employee."));

        // Similar columns as main table but for history view
        TableColumn<PayrollAdvances.Advance, String> dateCol = new TableColumn<>("Date Given");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getDateGiven().format(DATE_FORMAT)));
        
        TableColumn<PayrollAdvances.Advance, Number> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
            cell.getValue().getTotalAmount().doubleValue()));
        
        TableColumn<PayrollAdvances.Advance, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getStatus().getDisplayName()));
        
        TableColumn<PayrollAdvances.Advance, Number> paidCol = new TableColumn<>("Total Paid");
        paidCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
            cell.getValue().getTotalPaid().doubleValue()));
        
        TableColumn<PayrollAdvances.Advance, String> completedDateCol = new TableColumn<>("Completed Date");
        completedDateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getCompletedDate() != null ? 
            cell.getValue().getCompletedDate().format(DATE_FORMAT) : ""));
        
        advancesHistoryTable.getColumns().addAll(dateCol, amountCol, statusCol, paidCol, completedDateCol);
    }

	public void updateAdvancesTable() {
		advancesRows.clear();
		Employee selectedDriver = driverBox.getValue();
		cashAdvanceRedNote.setVisible(false);
		
		if (selectedDriver != null) {
			// Get only active advances for main view
			List<PayrollAdvances.Advance> activeAdvances = payrollAdvances.getActiveAdvances(
				String.valueOf(selectedDriver.getId()));
			advancesRows.setAll(activeAdvances);
			
			LocalDate weekStart = weekStartPicker.getValue();
			if (weekStart != null) {
				List<String> notifications = payrollAdvances.getCompletedAdvanceNotifications(weekStart);
				if (!notifications.isEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (String note : notifications) {
						sb.append(note).append("\n");
					}
					cashAdvanceRedNote.setText(sb.toString().trim());
					cashAdvanceRedNote.setVisible(true);
				}
			}
		}
	}

	public void updateAdvanceTable() {
		updateAdvancesTable();
	}

    private void updateSummary() {
        Employee selectedDriver = driverBox.getValue();
        if (selectedDriver != null) {
            PayrollAdvances.AdvanceSummary summary = payrollAdvances.getEmployeeSummary(
                String.valueOf(selectedDriver.getId()));
            
            summaryLabel.setText(String.format(
                "Summary - Total Advanced: $%,.2f | Total Paid: $%,.2f | Outstanding: $%,.2f | " +
                "Active: %d | Completed: %d | Overdue: %d",
                summary.totalAdvanced, summary.totalPaid, summary.totalOutstanding,
                summary.activeCount, summary.completedCount, summary.overdueCount));
        } else {
            summaryLabel.setText("");
        }
    }

    private void addAdvanceDialog() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to add an advance.");
            return;
        }
        
        Dialog<AdvanceDialogResult> dialog = new Dialog<>();
        dialog.setTitle("Add Cash Advance");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10); 
        grid.setHgap(10);

        TextField amtField = new TextField();
        amtField.setPromptText("Amount (10.00 - 10000.00)");
        
        ComboBox<Integer> weeksBox = new ComboBox<>(FXCollections.observableArrayList());
        for (int i = 1; i <= 52; i++) {
            weeksBox.getItems().add(i);
        }
        weeksBox.setValue(4); // Default 4 weeks
        
        TextArea notesField = new TextArea();
        notesField.setPromptText("Reason for advance / Notes");
        notesField.setWrapText(true);
        notesField.setPrefRowCount(3);
        
        TextField approvedByField = new TextField(System.getProperty("user.name", "Manager"));
        approvedByField.setPromptText("Approved By");
        
        DatePicker datePicker = new DatePicker(LocalDate.now());
        
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        int row = 0;
        grid.add(new Label("Advance Amount:"), 0, row); 
        grid.add(amtField, 1, row++);
        grid.add(new Label("Date Given:"), 0, row); 
        grid.add(datePicker, 1, row++);
        grid.add(new Label("Weeks to Repay:"), 0, row); 
        grid.add(weeksBox, 1, row++);
        grid.add(new Label("Approved By:"), 0, row); 
        grid.add(approvedByField, 1, row++);
        grid.add(new Label("Notes/Reason:"), 0, row); 
        grid.add(notesField, 1, row++);
        grid.add(errorLabel, 0, row, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        
        Runnable validate = () -> {
            try {
                if (amtField.getText().trim().isEmpty()) {
                    errorLabel.setText("Amount is required");
                    okBtn.setDisable(true);
                    return;
                }
                
                BigDecimal amount = new BigDecimal(amtField.getText().trim());
                if (amount.compareTo(new BigDecimal("10.00")) < 0 || 
                    amount.compareTo(new BigDecimal("10000.00")) > 0) {
                    errorLabel.setText("Amount must be between $10 and $10,000");
                    okBtn.setDisable(true);
                    return;
                }
                
                if (approvedByField.getText().trim().isEmpty()) {
                    errorLabel.setText("Approved By is required");
                    okBtn.setDisable(true);
                    return;
                }
                
                errorLabel.setText("");
                okBtn.setDisable(false);
                
            } catch (NumberFormatException e) {
                errorLabel.setText("Invalid amount format");
                okBtn.setDisable(true);
            }
        };
        
        amtField.textProperty().addListener((obs, o, n) -> validate.run());
        approvedByField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                BigDecimal amt = new BigDecimal(amtField.getText().trim());
                int weeks = weeksBox.getValue();
                String notes = notesField.getText().trim();
                String approvedBy = approvedByField.getText().trim();
                LocalDate dateGiven = datePicker.getValue();
                return new AdvanceDialogResult(amt, weeks, notes, approvedBy, dateGiven);
            }
            return null;
        });

        Optional<AdvanceDialogResult> result = dialog.showAndWait();
        if (result.isPresent()) {
            AdvanceDialogResult res = result.get();
            PayrollAdvances.AdvanceResult advResult = payrollAdvances.addAdvance(
                String.valueOf(selectedDriver.getId()), 
                res.amount, 
                res.notes, 
                res.dateGiven, 
                res.weeks,
                res.approvedBy
            );
            
            if (advResult.isSuccess()) {
                updateAdvancesTable();
                updateSummary();
                showInfo("Advance created successfully!");
            } else {
                showError(advResult.getMessage());
            }
        }
    }

    private void viewEditScheduleDialog() {
        PayrollAdvances.Advance selected = advancesTable.getSelectionModel().getSelectedItem();
        Employee selectedDriver = driverBox.getValue();
        if (selected == null || selectedDriver == null) {
            showError("Select a cash advance to view/edit schedule.");
            return;
        }
        
        Stage dialogStage = new Stage();
        dialogStage.setTitle("View/Edit Repayment Schedule - " + selected.getId());
        dialogStage.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        
        Label headerLabel = new Label(String.format(
            "Advance Date: %s | Amount: $%,.2f | Status: %s",
            selected.getDateGiven().format(DATE_FORMAT),
            selected.getTotalAmount(),
            selected.getStatus().getDisplayName()
        ));
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        TableView<PayrollAdvances.Advance.Repayment> scheduleTable = new TableView<>();
        ObservableList<PayrollAdvances.Advance.Repayment> schedRows = 
            FXCollections.observableArrayList(selected.getRepayments());
        scheduleTable.setItems(schedRows);

        TableColumn<PayrollAdvances.Advance.Repayment, String> weekCol = new TableColumn<>("Week Start");
        weekCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getWeekStartDate().format(DATE_FORMAT)));
        
        TableColumn<PayrollAdvances.Advance.Repayment, Number> scheduledCol = new TableColumn<>("Scheduled");
        scheduledCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
            cell.getValue().getScheduledAmount().doubleValue()));
        
        TableColumn<PayrollAdvances.Advance.Repayment, Number> paidAmtCol = new TableColumn<>("Paid");
        paidAmtCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
            cell.getValue().getPaidAmount().doubleValue()));
        
        TableColumn<PayrollAdvances.Advance.Repayment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().isPaid() ? "Paid" : "Pending"));
        
        TableColumn<PayrollAdvances.Advance.Repayment, String> paidDateCol = new TableColumn<>("Paid Date");
        paidDateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getPaidDate() != null ? 
            cell.getValue().getPaidDate().format(DATE_FORMAT) : ""));
        
        TableColumn<PayrollAdvances.Advance.Repayment, String> noteCol = new TableColumn<>("Note");
        noteCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getNote() == null ? "" : cell.getValue().getNote()));
        
        scheduleTable.getColumns().addAll(weekCol, scheduledCol, paidAmtCol, statusCol, paidDateCol, noteCol);

        Button editBtn = new Button("Edit Selected Repayment");
        Button closeBtn = new Button("Close");
        
        HBox btnBox = new HBox(8, editBtn, closeBtn);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        editBtn.setOnAction(e -> {
            PayrollAdvances.Advance.Repayment rep = scheduleTable.getSelectionModel().getSelectedItem();
            if (rep == null) {
                showError("Select a scheduled repayment to edit.");
                return;
            }
            
            if (rep.isPaid()) {
                showError("Cannot edit paid repayments.");
                return;
            }
            
            editRepaymentDialog(selected, rep, selectedDriver, dialogStage);
        });
        
        closeBtn.setOnAction(e -> dialogStage.close());

        root.getChildren().addAll(headerLabel, scheduleTable, btnBox);

        Scene scene = new Scene(root, 800, 400);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private void editRepaymentDialog(PayrollAdvances.Advance advance, 
                                   PayrollAdvances.Advance.Repayment repayment,
                                   Employee driver,
                                   Stage parentStage) {
        Dialog<EditRepaymentResult> dialog = new Dialog<>();
        dialog.setTitle("Edit Repayment");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        DatePicker weekDatePicker = new DatePicker(repayment.getWeekStartDate());
        TextField amtEdit = new TextField(repayment.getScheduledAmount().toString());
        TextField noteEdit = new TextField(repayment.getNote() == null ? "" : repayment.getNote());
        
        GridPane grid = new GridPane();
        grid.setVgap(8); 
        grid.setHgap(8); 
        grid.setPadding(new Insets(12));
        
        grid.add(new Label("Week Start:"), 0, 0); 
        grid.add(weekDatePicker, 1, 0);
        grid.add(new Label("Amount:"), 0, 1); 
        grid.add(amtEdit, 1, 1);
        grid.add(new Label("Note:"), 0, 2); 
        grid.add(noteEdit, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    LocalDate newDate = weekDatePicker.getValue();
                    BigDecimal newAmount = new BigDecimal(amtEdit.getText().trim());
                    String note = noteEdit.getText().trim();
                    return new EditRepaymentResult(newDate, newAmount, note);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });
        
        Optional<EditRepaymentResult> result = dialog.showAndWait();
        if (result.isPresent()) {
            EditRepaymentResult res = result.get();
            PayrollAdvances.OperationResult opResult = payrollAdvances.updateRepayment(
                String.valueOf(driver.getId()),
                advance.getId(),
                repayment.getWeekStartDate(),
                res.newWeekStartDate,
                res.newAmount,
                res.note,
                System.getProperty("user.name", "Manager")
            );
            
            if (opResult.isSuccess()) {
                updateAdvancesTable();
                parentStage.close();
                showInfo("Repayment updated successfully");
            } else {
                showError(opResult.getMessage());
            }
        }
    }

    private void addManualPaymentDialog() {
        PayrollAdvances.Advance selected = advancesTable.getSelectionModel().getSelectedItem();
        Employee selectedDriver = driverBox.getValue();
        if (selected == null || selectedDriver == null) {
            showError("Select a cash advance to add a manual payment.");
            return;
        }
        
        Dialog<ManualPaymentResult> dialog = new Dialog<>();
        dialog.setTitle("Add Manual Repayment");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField amtField = new TextField();
        amtField.setPromptText("Amount");
        
        ComboBox<PayrollAdvances.Advance.ManualRepayment.PaymentMethod> methodBox = 
            new ComboBox<>(FXCollections.observableArrayList(
                PayrollAdvances.Advance.ManualRepayment.PaymentMethod.values()));
        methodBox.setValue(PayrollAdvances.Advance.ManualRepayment.PaymentMethod.CASH);
        
        TextField refField = new TextField();
        refField.setPromptText("Reference Number (optional)");
        
        TextArea noteField = new TextArea();
        noteField.setPromptText("Note/Reason");
        noteField.setPrefRowCount(2);
        
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        
        GridPane grid = new GridPane();
        grid.setVgap(8); 
        grid.setHgap(8); 
        grid.setPadding(new Insets(12));
        
        int row = 0;
        grid.add(new Label("Date:"), 0, row); 
        grid.add(datePicker, 1, row++);
        grid.add(new Label("Amount:"), 0, row); 
        grid.add(amtField, 1, row++);
        grid.add(new Label("Payment Method:"), 0, row); 
        grid.add(methodBox, 1, row++);
        grid.add(new Label("Reference #:"), 0, row); 
        grid.add(refField, 1, row++);
        grid.add(new Label("Note:"), 0, row); 
        grid.add(noteField, 1, row++);
        grid.add(errorLabel, 0, row, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        
        Runnable validate = () -> {
            try {
                if (amtField.getText().trim().isEmpty()) {
                    errorLabel.setText("Amount is required");
                    okBtn.setDisable(true);
                    return;
                }
                
                BigDecimal amount = new BigDecimal(amtField.getText().trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    errorLabel.setText("Amount must be positive");
                    okBtn.setDisable(true);
                    return;
                }
                
                errorLabel.setText("");
                okBtn.setDisable(false);
                
            } catch (NumberFormatException e) {
                errorLabel.setText("Invalid amount format");
                okBtn.setDisable(true);
            }
        };
        
        amtField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                LocalDate date = datePicker.getValue();
                BigDecimal amount = new BigDecimal(amtField.getText().trim());
                String note = noteField.getText().trim();
                PayrollAdvances.Advance.ManualRepayment.PaymentMethod method = methodBox.getValue();
                String reference = refField.getText().trim();
                return new ManualPaymentResult(date, amount, note, method, reference);
            }
            return null;
        });

        Optional<ManualPaymentResult> result = dialog.showAndWait();
        if (result.isPresent()) {
            ManualPaymentResult p = result.get();
            PayrollAdvances.PaymentResult payResult = payrollAdvances.addManualRepayment(
                String.valueOf(selectedDriver.getId()),
                selected.getId(),
                p.date,
                p.amount,
                p.note,
                System.getProperty("user.name", "Manager"),
                p.method,
                p.reference
            );
            
            if (payResult.isSuccess()) {
                updateAdvancesTable();
                updateSummary();
                showInfo(payResult.getMessage());
            } else {
                showError(payResult.getMessage());
            }
        }
    }

    private void markAdvanceRepaymentPaid() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to process payments.");
            return;
        }
        
        BigDecimal totalDue = payrollAdvances.getRepaymentAmountForWeek(
            String.valueOf(selectedDriver.getId()), weekStart);
        
        if (totalDue.compareTo(BigDecimal.ZERO) == 0) {
            showInfo("No repayments due for this week.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Process Weekly Repayments");
        confirm.setHeaderText("Process Scheduled Repayments");
        confirm.setContentText(String.format(
            "Process repayments for %s\nWeek of %s\nTotal Amount: $%,.2f\n\nContinue?",
            selectedDriver.getName(),
            weekStart.format(DATE_FORMAT),
            totalDue
        ));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            PayrollAdvances.PaymentResult payResult = payrollAdvances.recordRepayment(
                String.valueOf(selectedDriver.getId()),
                weekStart,
                totalDue,
                System.getProperty("user.name", "Manager"),
                "Weekly payroll deduction"
            );
            
            if (payResult.isSuccess()) {
                updateAdvancesTable();
                updateSummary();
                showInfo(payResult.getMessage());
            } else {
                showError(payResult.getMessage());
            }
        }
    }

    private void viewAdvanceDetails() {
        PayrollAdvances.Advance selected = advancesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        Stage detailStage = new Stage();
        detailStage.setTitle("Advance Details - " + selected.getId());
        detailStage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        
        TextArea detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPrefRowCount(20);
        
        StringBuilder details = new StringBuilder();
        details.append("ADVANCE DETAILS\n");
        details.append("===============\n\n");
        details.append("ID: ").append(selected.getId()).append("\n");
        details.append("Status: ").append(selected.getStatus().getDisplayName()).append("\n");
        details.append("Date Given: ").append(selected.getDateGiven().format(DATE_FORMAT)).append("\n");
        details.append("Amount: $").append(String.format("%,.2f", selected.getTotalAmount())).append("\n");
        details.append("Approved By: ").append(selected.getApprovedBy()).append("\n");
        details.append("Notes: ").append(selected.getNotes()).append("\n");
        details.append("Weeks to Repay: ").append(selected.getWeeksToRepay()).append("\n");
        details.append("Total Paid: $").append(String.format("%,.2f", selected.getTotalPaid())).append("\n");
        details.append("Remaining Balance: $").append(String.format("%,.2f", selected.getRemainingBalance())).append("\n");
        
        if (selected.isOverdue()) {
            details.append("OVERDUE: ").append(selected.getMissedPaymentCount()).append(" missed payment(s)\n");
        }
        
        if (selected.getCompletedDate() != null) {
            details.append("Completed Date: ").append(selected.getCompletedDate().format(DATE_FORMAT)).append("\n");
        }
        
        details.append("\nSTATUS HISTORY\n");
        details.append("--------------\n");
        for (PayrollAdvances.Advance.StatusChange change : selected.getStatusHistory()) {
            details.append(change.timestamp.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")))
                   .append(" - ")
                   .append(change.fromStatus == null ? "Created" : change.fromStatus.getDisplayName())
                   .append(" -> ")
                   .append(change.toStatus.getDisplayName())
                   .append(" (").append(change.reason).append(")")
                   .append(" by ").append(change.changedBy)
                   .append("\n");
        }
        
        if (!selected.getManualRepayments().isEmpty()) {
            details.append("\nMANUAL PAYMENTS\n");
            details.append("---------------\n");
            for (PayrollAdvances.Advance.ManualRepayment manual : selected.getManualRepayments()) {
                details.append(manual.getDate().format(DATE_FORMAT))
                       .append(" - $").append(String.format("%,.2f", manual.getAmount()))
                       .append(" via ").append(manual.getPaymentMethod().getDisplayName())
                       .append(manual.getReferenceNumber().isEmpty() ? "" : " (Ref: " + manual.getReferenceNumber() + ")")
                       .append(" - ").append(manual.getNote())
                       .append("\n");
            }
        }
        
        detailsArea.setText(details.toString());
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> detailStage.close());
        
        root.getChildren().addAll(detailsArea, closeBtn);
        
        Scene scene = new Scene(root, 600, 500);
        detailStage.setScene(scene);
        detailStage.showAndWait();
    }

    private void changeAdvanceStatus() {
        PayrollAdvances.Advance selected = advancesTable.getSelectionModel().getSelectedItem();
        Employee selectedDriver = driverBox.getValue();
        if (selected == null || selectedDriver == null) return;
        
        Dialog<StatusChangeResult> dialog = new Dialog<>();
        dialog.setTitle("Change Advance Status");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        ComboBox<PayrollAdvances.Advance.AdvanceStatus> statusBox = new ComboBox<>(
            FXCollections.observableArrayList(PayrollAdvances.Advance.AdvanceStatus.values())
        );
        statusBox.setValue(selected.getStatus());
        
        TextArea reasonField = new TextArea();
        reasonField.setPromptText("Reason for status change");
        reasonField.setPrefRowCount(3);
        
        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(8);
        grid.setPadding(new Insets(12));
        
        grid.add(new Label("New Status:"), 0, 0);
        grid.add(statusBox, 1, 0);
        grid.add(new Label("Reason:"), 0, 1);
        grid.add(reasonField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        
        Runnable validate = () -> {
            boolean valid = statusBox.getValue() != selected.getStatus() && 
                          !reasonField.getText().trim().isEmpty();
            okBtn.setDisable(!valid);
        };
        
        statusBox.valueProperty().addListener((obs, o, n) -> validate.run());
        reasonField.textProperty().addListener((obs, o, n) -> validate.run());
        
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                return new StatusChangeResult(statusBox.getValue(), reasonField.getText().trim());
            }
            return null;
        });
        
        Optional<StatusChangeResult> result = dialog.showAndWait();
        if (result.isPresent()) {
            StatusChangeResult res = result.get();
            PayrollAdvances.OperationResult opResult = payrollAdvances.changeAdvanceStatus(
                String.valueOf(selectedDriver.getId()),
                selected.getId(),
                res.newStatus,
                res.reason,
                System.getProperty("user.name", "Manager")
            );
            
            if (opResult.isSuccess()) {
                updateAdvancesTable();
                updateSummary();
                showInfo("Status changed successfully");
            } else {
                showError(opResult.getMessage());
            }
        }
    }

    private void showAdvanceHistory() {
        Employee selectedDriver = driverBox.getValue();
        if (selectedDriver == null) {
            showError("Select a driver to view history.");
            return;
        }
        
        Stage historyStage = new Stage();
        historyStage.setTitle("Advance History - " + selectedDriver.getName());
        historyStage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        
        List<PayrollAdvances.Advance> allAdvances = payrollAdvances.getAllAdvances(
            String.valueOf(selectedDriver.getId()));
        advancesHistoryRows.setAll(allAdvances);
        
        Label summaryLabel = new Label(String.format(
            "Total Advances: %d | Total Amount: $%,.2f",
            allAdvances.size(),
            allAdvances.stream()
                .mapToDouble(a -> a.getTotalAmount().doubleValue())
                .sum()
        ));
        summaryLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> historyStage.close());
        
        root.getChildren().addAll(summaryLabel, advancesHistoryTable, closeBtn);
        
        Scene scene = new Scene(root, 800, 600);
        historyStage.setScene(scene);
        historyStage.showAndWait();
    }

    private void showEmployeeSummary() {
        Employee selectedDriver = driverBox.getValue();
        if (selectedDriver == null) {
            showError("Select a driver to view summary.");
            return;
        }
        
        PayrollAdvances.AdvanceSummary summary = payrollAdvances.getEmployeeSummary(
            String.valueOf(selectedDriver.getId()));
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Employee Advance Summary");
        alert.setHeaderText(selectedDriver.getName() + " - Cash Advance Summary");
        
        String content = String.format(
            "Total Advanced: $%,.2f\n" +
            "Total Paid: $%,.2f\n" +
            "Outstanding Balance: $%,.2f\n\n" +
            "Active Advances: %d\n" +
            "Completed Advances: %d\n" +
            "Overdue Advances: %d",
            summary.totalAdvanced,
            summary.totalPaid,
            summary.totalOutstanding,
            summary.activeCount,
            summary.completedCount,
            summary.overdueCount
        );
        
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showAuditTrail() {
        Employee selectedDriver = driverBox.getValue();
        
        Stage auditStage = new Stage();
        auditStage.setTitle("Audit Trail" + (selectedDriver != null ? " - " + selectedDriver.getName() : ""));
        auditStage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        
        TableView<PayrollAdvances.AuditEntry> auditTable = new TableView<>();
        
        TableColumn<PayrollAdvances.AuditEntry, String> timestampCol = new TableColumn<>("Timestamp");
        timestampCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().timestamp.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"))));
        
        TableColumn<PayrollAdvances.AuditEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().action));
        
        TableColumn<PayrollAdvances.AuditEntry, String> employeeCol = new TableColumn<>("Employee");
        employeeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().employeeId));
        
        TableColumn<PayrollAdvances.AuditEntry, String> detailsCol = new TableColumn<>("Details");
        detailsCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().details));
        
        TableColumn<PayrollAdvances.AuditEntry, String> performedByCol = new TableColumn<>("Performed By");
        performedByCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().performedBy));
        
        auditTable.getColumns().addAll(timestampCol, actionCol, employeeCol, detailsCol, performedByCol);
        
        List<PayrollAdvances.AuditEntry> auditEntries = payrollAdvances.getAuditTrail(
            selectedDriver != null ? String.valueOf(selectedDriver.getId()) : null,
            LocalDate.now().minusMonths(6),
            LocalDate.now()
        );
        
        auditTable.setItems(FXCollections.observableArrayList(auditEntries));
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> auditStage.close());
        
        root.getChildren().addAll(auditTable, closeBtn);
        
        Scene scene = new Scene(root, 900, 600);
        auditStage.setScene(scene);
        auditStage.showAndWait();
    }

    public void updateAdvancesHistoryTable() {
        // This method is called from outside but we handle history differently now
        updateAdvancesTable();
        updateSummary();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
    
    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Error");
        a.showAndWait();
    }

    // Dialog result classes
    private static class AdvanceDialogResult {
        final BigDecimal amount;
        final int weeks;
        final String notes;
        final String approvedBy;
        final LocalDate dateGiven;
        
        AdvanceDialogResult(BigDecimal amount, int weeks, String notes, String approvedBy, LocalDate dateGiven) {
            this.amount = amount;
            this.weeks = weeks;
            this.notes = notes;
            this.approvedBy = approvedBy;
            this.dateGiven = dateGiven;
        }
    }
    
    private static class EditRepaymentResult {
        final LocalDate newWeekStartDate;
        final BigDecimal newAmount;
        final String note;
        
        EditRepaymentResult(LocalDate newWeekStartDate, BigDecimal newAmount, String note) {
            this.newWeekStartDate = newWeekStartDate;
            this.newAmount = newAmount;
            this.note = note;
        }
    }
    
    private static class ManualPaymentResult {
        final LocalDate date;
        final BigDecimal amount;
        final String note;
        final PayrollAdvances.Advance.ManualRepayment.PaymentMethod method;
        final String reference;
        
        ManualPaymentResult(LocalDate date, BigDecimal amount, String note, 
                          PayrollAdvances.Advance.ManualRepayment.PaymentMethod method, 
                          String reference) {
            this.date = date;
            this.amount = amount;
            this.note = note;
            this.method = method;
            this.reference = reference;
        }
    }
    
    private static class StatusChangeResult {
        final PayrollAdvances.Advance.AdvanceStatus newStatus;
        final String reason;
        
        StatusChangeResult(PayrollAdvances.Advance.AdvanceStatus newStatus, String reason) {
            this.newStatus = newStatus;
            this.reason = reason;
        }
    }
}