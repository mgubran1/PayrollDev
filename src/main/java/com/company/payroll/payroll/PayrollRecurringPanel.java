package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// FIX: Add missing imports
import java.util.Map;
import java.util.LinkedHashMap;

public class PayrollRecurringPanel extends VBox {
    private final PayrollRecurring payrollRecurring;
    private final ComboBox<Employee> driverBox;
    private final DatePicker weekStartPicker;
    private final ObservableList<PayrollRecurring.RecurringDeduction> recurringRows = FXCollections.observableArrayList();
    private final TableView<PayrollRecurring.RecurringDeduction> recurringTable = new TableView<>(recurringRows);
    private final Button addFeeBtn = new Button("Add Deduction");
    private final Button removeFeeBtn = new Button("Remove");
    private final Button saveFeesBtn = new Button("Save This Week's Deductions");
    private final Button chargeAllBtn = new Button("Charge All Recurring Fees");
    private final Label reminderLabel = new Label();

    // Optionally: If you want summary labels, you can define them here:
    private final Map<String, Label> summaryLabels = new LinkedHashMap<>();
    private final Label netPayLabel = new Label("Net Pay: $0.00");

    public PayrollRecurringPanel(PayrollRecurring payrollRecurring, ComboBox<Employee> driverBox, DatePicker weekStartPicker) {
        this.payrollRecurring = payrollRecurring;
        this.driverBox = driverBox;
        this.weekStartPicker = weekStartPicker;
        setSpacing(8);
        setPadding(new Insets(8));
        buildRecurringTable();

        reminderLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
        reminderLabel.setVisible(false);

        HBox feeBtnBox = new HBox(8, addFeeBtn, removeFeeBtn, saveFeesBtn, chargeAllBtn);
        feeBtnBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(reminderLabel, feeBtnBox, recurringTable);

        addFeeBtn.setOnAction(e -> addDeduction());
        removeFeeBtn.setOnAction(e -> removeDeduction());
        saveFeesBtn.setOnAction(e -> saveDeductions());
        chargeAllBtn.setOnAction(e -> chargeAllRecurringFees());

        recurringTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            removeFeeBtn.setDisable(newSel == null);
        });
        removeFeeBtn.setDisable(true);

        driverBox.valueProperty().addListener((obs, o, n) -> updateRecurringTable());
        weekStartPicker.valueProperty().addListener((obs, o, n) -> updateRecurringTable());

        updateRecurringTable();
    }

    private void buildRecurringTable() {
        recurringTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recurringTable.setPrefHeight(300);
        recurringTable.setStyle("-fx-background-color: #fff; -fx-font-size: 15px;");
        recurringTable.setPlaceholder(new Label("No recurring deductions for this period/driver."));

        TableColumn<PayrollRecurring.RecurringDeduction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        TableColumn<PayrollRecurring.RecurringDeduction, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().amount()).asObject());
        TableColumn<PayrollRecurring.RecurringDeduction, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().description() == null ? "" : cell.getValue().description()));
        recurringTable.getColumns().setAll(typeCol, amountCol, descCol);
    }

    public void updateRecurringTable() {
        recurringRows.clear();
        Employee selectedDriver = driverBox.getValue();
        LocalDate start = weekStartPicker.getValue();
        if (selectedDriver != null && start != null) {
            List<PayrollRecurring.RecurringDeduction> list = payrollRecurring.getDeductionsForDriverWeek(selectedDriver.getId(), start);
            recurringRows.setAll(list);
        }
    }

    private void addDeduction() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to add a deduction.");
            return;
        }
        PayrollRecurring.RecurringDeduction newDeduction = showDeductionDialog(null);
        if (newDeduction != null) {
            for (PayrollRecurring.RecurringDeduction d : recurringRows) {
                if (d.type().equalsIgnoreCase(newDeduction.type())) {
                    showError("This fee type is already entered for this week.");
                    return;
                }
            }
            List<PayrollRecurring.RecurringDeduction> current = new ArrayList<>(recurringRows);
            current.add(new PayrollRecurring.RecurringDeduction(
                    0, selectedDriver.getId(), newDeduction.type(), newDeduction.amount(), newDeduction.description(), weekStart
            ));
            recurringRows.setAll(current);
            payrollRecurring.saveDeductionsForDriverWeek(selectedDriver.getId(), weekStart, current);
            updateRecurringTable();
        }
    }

    private void removeDeduction() {
        PayrollRecurring.RecurringDeduction selected = recurringTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a deduction to remove.");
            return;
        }
        if (selected.id() != 0) {
            payrollRecurring.removeDeductionById(selected.id());
        }
        List<PayrollRecurring.RecurringDeduction> current = new ArrayList<>(recurringRows);
        current.remove(selected);
        recurringRows.setAll(current);
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver != null && weekStart != null) {
            payrollRecurring.saveDeductionsForDriverWeek(selectedDriver.getId(), weekStart, current);
            updateRecurringTable();
        }
    }

    private void saveDeductions() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to save deductions.");
            return;
        }
        List<PayrollRecurring.RecurringDeduction> toSave = new ArrayList<>(recurringRows);
        boolean ok = payrollRecurring.saveDeductionsForDriverWeek(selectedDriver.getId(), weekStart, toSave);
        if (ok) {
            showInfo("Recurring deductions saved for this week and driver.");
            updateRecurringTable();
        } else {
            showError("Error saving deductions.");
        }
    }

    private PayrollRecurring.RecurringDeduction showDeductionDialog(PayrollRecurring.RecurringDeduction deduction) {
        Dialog<PayrollRecurring.RecurringDeduction> dialog = new Dialog<>();
        dialog.setTitle(deduction == null ? "Add Recurring Deduction" : "Edit Recurring Deduction");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(PayrollRecurring.RECURRING_TYPES));
        typeBox.setEditable(true);
        TextField amountField = new TextField();
        TextField descField = new TextField();

        if (deduction != null) {
            typeBox.setValue(deduction.type());
            amountField.setText(String.valueOf(deduction.amount()));
            descField.setText(deduction.description());
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        GridPane grid = new GridPane();
        grid.setVgap(7);
        grid.setHgap(10);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.add(new Label("Type:"), 0, row);   grid.add(typeBox, 1, row++);
        grid.add(new Label("Amount:"), 0, row); grid.add(amountField, 1, row++);
        grid.add(new Label("Description:"), 0, row); grid.add(descField, 1, row++);
        grid.add(errorLabel, 1, row++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean valid = typeBox.getValue() != null && !typeBox.getValue().trim().isEmpty() && isDouble(amountField.getText());
            errorLabel.setVisible(!valid);
            okBtn.setDisable(!valid);
            errorLabel.setText(valid ? "" : "Type & valid Amount required.");
        };
        typeBox.valueProperty().addListener((obs, o, n) -> validate.run());
        amountField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                String type = typeBox.getValue().trim();
                double amt = Double.parseDouble(amountField.getText());
                String desc = descField.getText().trim();
                return new PayrollRecurring.RecurringDeduction(0, 0, type, amt, desc, null);
            }
            return null;
        });

        Optional<PayrollRecurring.RecurringDeduction> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void chargeAllRecurringFees() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to charge recurring fees.");
            return;
        }
        Dialog<List<PayrollRecurring.RecurringDeduction>> dialog = new Dialog<>();
        dialog.setTitle("Charge All Recurring Fees");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(8);
        Map<String, TextField> amountFields = new LinkedHashMap<>();
        Map<String, CheckBox> checkboxes = new LinkedHashMap<>();
        for (String type : PayrollRecurring.RECURRING_TYPES) {
            HBox row = new HBox(8);
            CheckBox cb = new CheckBox();
            TextField amtField = new TextField();
            amtField.setPromptText("Amount");
            Label lbl = new Label(type);
            checkboxes.put(type, cb);
            amountFields.put(type, amtField);
            row.getChildren().addAll(cb, lbl, amtField);
            box.getChildren().add(row);
        }
        Label descLabel = new Label("Description (optional):");
        TextField descField = new TextField();
        box.getChildren().addAll(descLabel, descField);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        box.getChildren().add(errorLabel);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean hasCharge = false;
            boolean valid = true;
            for (String type : PayrollRecurring.RECURRING_TYPES) {
                if (checkboxes.get(type).isSelected()) {
                    hasCharge = true;
                    String txt = amountFields.get(type).getText();
                    if (txt == null || txt.trim().isEmpty() || !isDouble(txt)) {
                        valid = false;
                        break;
                    }
                }
            }
            errorLabel.setVisible(!valid || !hasCharge);
            errorLabel.setText(!hasCharge ? "Select at least one fee type to charge." :
                    (valid ? "" : "All selected types must have valid Amounts."));
            okBtn.setDisable(!valid || !hasCharge);
        };
        for (CheckBox cb : checkboxes.values()) cb.selectedProperty().addListener((obs, o, n) -> validate.run());
        for (TextField tf : amountFields.values()) tf.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                List<PayrollRecurring.RecurringDeduction> charges = new ArrayList<>();
                for (String type : PayrollRecurring.RECURRING_TYPES) {
                    if (checkboxes.get(type).isSelected()) {
                        double amt = Double.parseDouble(amountFields.get(type).getText());
                        String desc = descField.getText().trim();
                        charges.add(new PayrollRecurring.RecurringDeduction(0, selectedDriver.getId(), type, amt, desc, weekStart));
                    }
                }
                return charges;
            }
            return null;
        });

        Optional<List<PayrollRecurring.RecurringDeduction>> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isEmpty()) return;

        boolean ok = payrollRecurring.chargeAllRecurringFees(selectedDriver.getId(), weekStart, result.get());
        if (ok) {
            showInfo("All recurring fees charged for this week and driver.");
            updateRecurringTable();
        } else {
            showError("Duplicate charge detected. One or more fee types already charged for this week.");
        }
    }

    private boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; }
        catch (Exception e) { return false; }
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
}