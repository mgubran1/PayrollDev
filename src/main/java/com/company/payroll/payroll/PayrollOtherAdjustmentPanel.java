package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class PayrollOtherAdjustmentPanel extends VBox {
    public static final String[] DEDUCTION_TYPES = {
        "Damages", "Tolls (Deduction)", "Tickets & Violations", "Advances", "Unreturned Equipment", "Shortages",
        "Uniforms", "Personal Phone Use", "Drug/Alcohol Testing Fees", "Non-Compliance Fines", "Unauthorized Purchases",
        "Lease Payments", "Chargebacks", "Insurance Deductibles", "Fuel Card Fees", "Other Policy Violations", "Other (Custom Deduction)"
    };
    public static final String[] REIMBURSEMENT_TYPES = {
        "Lumper Fees", "Tolls (Reimbursement)", "Scale Tickets", "Truck Wash", "Permits", "Medical DOT Exam",
        "Hotel/Lodging", "Layover/Detention Pay", "Miscellaneous Supplies", "Parking Fees", "Personal Advances (Reimbursed)",
        "Training/Orientation Expenses", "Safety Bonus", "Referral Bonus", "Other (Custom Reimbursement)"
    };

    private final PayrollOtherAdjustments adjustmentsLogic;
    private final ComboBox<Employee> driverBox;
    private final DatePicker weekStartPicker;
    private final ObservableList<PayrollOtherAdjustments.OtherAdjustment> adjustmentRows = FXCollections.observableArrayList();
    private final TableView<PayrollOtherAdjustments.OtherAdjustment> adjustmentTable = new TableView<>(adjustmentRows);
    private final Button addBtn = new Button("Add Adjustment");
    private final Button editBtn = new Button("Edit");
    private final Button removeBtn = new Button("Remove");
    private final Button saveBtn = new Button("Save Adjustments");
    private final Button bonusOnLoadBtn = new Button("Bonus on Load");

    private LoadDAO loadDAO;

    public PayrollOtherAdjustmentPanel(PayrollOtherAdjustments adjustmentsLogic, ComboBox<Employee> driverBox, DatePicker weekStartPicker) {
        this.adjustmentsLogic = adjustmentsLogic;
        this.driverBox = driverBox;
        this.weekStartPicker = weekStartPicker;

        setSpacing(8);
        setPadding(new Insets(8));
        buildAdjustmentTable();

        HBox btnBox = new HBox(8, addBtn, editBtn, removeBtn, saveBtn, bonusOnLoadBtn);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(btnBox, adjustmentTable);

        addBtn.setOnAction(e -> addAdjustmentDialog());
        editBtn.setOnAction(e -> editAdjustmentDialog());
        removeBtn.setOnAction(e -> removeAdjustment());
        saveBtn.setOnAction(e -> saveAdjustments());
        bonusOnLoadBtn.setOnAction(e -> bonusOnLoadDialog());

        adjustmentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            editBtn.setDisable(newSel == null);
            removeBtn.setDisable(newSel == null);
        });
        editBtn.setDisable(true);
        removeBtn.setDisable(true);

        driverBox.valueProperty().addListener((obs, o, n) -> updateAdjustmentTable());
        weekStartPicker.valueProperty().addListener((obs, o, n) -> updateAdjustmentTable());

        updateAdjustmentTable();
    }

    private void buildAdjustmentTable() {
        adjustmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        adjustmentTable.setPrefHeight(300);
        adjustmentTable.setStyle("-fx-background-color: #fff; -fx-font-size: 15px;");
        adjustmentTable.setPlaceholder(new Label("No other adjustments for this period/driver."));

        TableColumn<PayrollOtherAdjustments.OtherAdjustment, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type));
        
        TableColumn<PayrollOtherAdjustments.OtherAdjustment, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().category));
        
        TableColumn<PayrollOtherAdjustments.OtherAdjustment, Double> amtCol = new TableColumn<>("Amount");
        // Updated to handle BigDecimal amount
        amtCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().amount.doubleValue()).asObject());
        amtCol.setCellFactory(col -> new TableCell<PayrollOtherAdjustments.OtherAdjustment, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("$%,.2f", value));
                    PayrollOtherAdjustments.OtherAdjustment row = getTableView().getItems().get(getIndex());
                    if ("Reimbursement".equals(row.category)) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        TableColumn<PayrollOtherAdjustments.OtherAdjustment, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().description == null ? "" : cell.getValue().description));
        
        adjustmentTable.getColumns().setAll(java.util.List.of(typeCol, catCol, amtCol, descCol));
    }

    public void updateAdjustmentTable() {
        adjustmentRows.clear();
        Employee selectedDriver = driverBox.getValue();
        LocalDate start = weekStartPicker.getValue();
        if (selectedDriver != null && start != null) {
            List<PayrollOtherAdjustments.OtherAdjustment> list = adjustmentsLogic.getAdjustmentsForDriverWeek(selectedDriver.getId(), start);
            adjustmentRows.setAll(list);
        }
    }

    private void addAdjustmentDialog() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to add an adjustment.");
            return;
        }
        OtherAdjustmentDialogResult result = showAdjustmentDialog(null);
        if (result != null) {
            List<PayrollOtherAdjustments.OtherAdjustment> current = new ArrayList<>(adjustmentRows);
            // Create new adjustment with BigDecimal amount and enhanced constructor
            current.add(new PayrollOtherAdjustments.OtherAdjustment(
                    0, selectedDriver.getId(), result.category, result.type, 
                    BigDecimal.valueOf(result.amount), result.description, weekStart, 
                    null, "User", null, PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.ACTIVE
            ));
            adjustmentRows.setAll(current);
            adjustmentsLogic.saveAdjustmentsForDriverWeek(selectedDriver.getId(), weekStart, current);
            updateAdjustmentTable();
        }
    }

    private void editAdjustmentDialog() {
        PayrollOtherAdjustments.OtherAdjustment selected = adjustmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select an adjustment to edit.");
            return;
        }
        OtherAdjustmentDialogResult result = showAdjustmentDialog(selected);
        if (result != null) {
            List<PayrollOtherAdjustments.OtherAdjustment> current = new ArrayList<>(adjustmentRows);
            int idx = current.indexOf(selected);
            // Create updated adjustment with BigDecimal amount
            current.set(idx, new PayrollOtherAdjustments.OtherAdjustment(
                    selected.id, selected.driverId, result.category, result.type, 
                    BigDecimal.valueOf(result.amount), result.description, selected.weekStart,
                    selected.loadNumber, selected.createdBy, selected.referenceNumber, selected.status
            ));
            adjustmentRows.setAll(current);
            adjustmentsLogic.saveAdjustmentsForDriverWeek(selected.driverId, selected.weekStart, current);
            updateAdjustmentTable();
        }
    }

    private void removeAdjustment() {
        PayrollOtherAdjustments.OtherAdjustment selected = adjustmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select an adjustment to remove.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete this adjustment?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Confirm Remove Adjustment");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) return;
        
        // Use the enhanced removal method
        PayrollOtherAdjustments.OperationResult opResult = adjustmentsLogic.removeAdjustmentById(selected.id, "User", "Removed by user");
        if (opResult.isSuccess()) {
            updateAdjustmentTable();
            showInfo("Adjustment removed.");
        } else {
            showError("Error removing adjustment: " + opResult.getMessage());
        }
    }

    private void saveAdjustments() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to save adjustments.");
            return;
        }
        List<PayrollOtherAdjustments.OtherAdjustment> toSave = new ArrayList<>(adjustmentRows);
        boolean ok = adjustmentsLogic.saveAdjustmentsForDriverWeek(selectedDriver.getId(), weekStart, toSave);
        if (ok) {
            showInfo("Other adjustments saved for this week and driver.");
            updateAdjustmentTable();
        } else {
            showError("Error saving adjustments.");
        }
    }

    private static class OtherAdjustmentDialogResult {
        final String category;
        final String type;
        final double amount;
        final String description;
        OtherAdjustmentDialogResult(String category, String type, double amount, String description) {
            this.category = category;
            this.type = type;
            this.amount = amount;
            this.description = description;
        }
    }

    private OtherAdjustmentDialogResult showAdjustmentDialog(PayrollOtherAdjustments.OtherAdjustment adjustment) {
        Dialog<OtherAdjustmentDialogResult> dialog = new Dialog<>();
        dialog.setTitle(adjustment == null ? "Add Adjustment" : "Edit Adjustment");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList("Deduction", "Reimbursement"));
        ComboBox<String> typeBox = new ComboBox<>();
        TextField customTypeField = new TextField();
        customTypeField.setPromptText("Type name...");
        customTypeField.setVisible(false);
        TextField amountField = new TextField();
        TextField descField = new TextField();

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        if (adjustment != null) {
            categoryBox.setValue(adjustment.category);
            if ("Deduction".equals(adjustment.category)) {
                typeBox.setItems(FXCollections.observableArrayList(DEDUCTION_TYPES));
            } else {
                typeBox.setItems(FXCollections.observableArrayList(REIMBURSEMENT_TYPES));
            }
            typeBox.setValue(adjustment.type);
            if (isCustomType(adjustment.type)) {
                customTypeField.setVisible(true);
                customTypeField.setText(adjustment.type);
            }
            // Convert BigDecimal to String for display
            amountField.setText(adjustment.amount.toPlainString());
            descField.setText(adjustment.description == null ? "" : adjustment.description);
        } else {
            categoryBox.setValue("Deduction");
            typeBox.setItems(FXCollections.observableArrayList(DEDUCTION_TYPES));
            typeBox.setValue(DEDUCTION_TYPES[0]);
        }

        categoryBox.valueProperty().addListener((obs, o, n) -> {
            if ("Deduction".equals(n)) {
                typeBox.setItems(FXCollections.observableArrayList(DEDUCTION_TYPES));
                typeBox.setValue(DEDUCTION_TYPES[0]);
            } else {
                typeBox.setItems(FXCollections.observableArrayList(REIMBURSEMENT_TYPES));
                typeBox.setValue(REIMBURSEMENT_TYPES[0]);
            }
        });
        typeBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null && isCustomType(n)) {
                customTypeField.setVisible(true);
            } else {
                customTypeField.setVisible(false);
            }
        });

        GridPane grid = new GridPane();
        grid.setVgap(7);
        grid.setHgap(10);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.add(new Label("Category:"), 0, row);   grid.add(categoryBox, 1, row++);
        grid.add(new Label("Type:"), 0, row);       grid.add(typeBox, 1, row++);
        grid.add(new Label("Custom Type:"), 0, row);grid.add(customTypeField, 1, row++);
        grid.add(new Label("Amount:"), 0, row);     grid.add(amountField, 1, row++);
        grid.add(new Label("Description:"), 0, row);grid.add(descField, 1, row++);
        grid.add(errorLabel, 1, row++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean valid = categoryBox.getValue() != null && typeBox.getValue() != null && isDouble(amountField.getText());
            if (typeBox.getValue() != null && isCustomType(typeBox.getValue())) {
                valid &= !customTypeField.getText().trim().isEmpty();
            }
            errorLabel.setVisible(!valid);
            okBtn.setDisable(!valid);
            errorLabel.setText(valid ? "" : "All fields required and Amount must be a number.");
        };
        categoryBox.valueProperty().addListener((obs, o, n) -> validate.run());
        typeBox.valueProperty().addListener((obs, o, n) -> validate.run());
        amountField.textProperty().addListener((obs, o, n) -> validate.run());
        customTypeField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                String category = categoryBox.getValue();
                String type = typeBox.getValue();
                if (isCustomType(type)) {
                    type = customTypeField.getText().trim();
                }
                double amount = Double.parseDouble(amountField.getText());
                String desc = descField.getText().trim();
                return new OtherAdjustmentDialogResult(category, type, amount, desc);
            }
            return null;
        });

        Optional<OtherAdjustmentDialogResult> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private static boolean isCustomType(String type) {
        return type != null && type.toLowerCase().contains("custom");
    }

    private boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; }
        catch (Exception e) { return false; }
    }

    public void setLoadDAO(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
    }

    private void bonusOnLoadDialog() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate start = weekStartPicker.getValue();
        if (selectedDriver == null || start == null) {
            showError("Select a driver and week to add a bonus.");
            return;
        }
        if (loadDAO == null) {
            showError("Internal error: LoadDAO is not set.");
            return;
        }
        List<Load> loads = loadDAO.getByDriverAndDateRange(selectedDriver.getId(), start, start.plusDays(6));
        if (loads.isEmpty()) {
            showError("No loads for this driver in the selected week.");
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Bonus on Load");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<Load> loadBox = new ComboBox<>(FXCollections.observableArrayList(loads));
        loadBox.setConverter(new StringConverter<Load>() {
            @Override
            public String toString(Load l) { return l == null ? "" : String.format("Load #%s  $%.2f", l.getLoadNumber(), l.getGrossAmount()); }
            @Override
            public Load fromString(String s) { return null; }
        });
        loadBox.setValue(loads.get(0));
        TextField pctField = new TextField("50");
        Label feeLabel = new Label();
        Label bonusLabel = new Label();
        updateBonusLabels(loadBox.getValue(), pctField.getText(), feeLabel, bonusLabel, selectedDriver);

        loadBox.valueProperty().addListener((obs, o, n) -> updateBonusLabels(n, pctField.getText(), feeLabel, bonusLabel, selectedDriver));
        pctField.textProperty().addListener((obs, o, n) -> updateBonusLabels(loadBox.getValue(), n, feeLabel, bonusLabel, selectedDriver));

        GridPane grid = new GridPane();
        grid.setVgap(7);
        grid.setHgap(10);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.add(new Label("Select Load:"), 0, row);  grid.add(loadBox, 1, row++);
        grid.add(new Label("Bonus Percentage (of service fee):"), 0, row); grid.add(pctField, 1, row++);
        grid.add(new Label("Original Service Fee:"), 0, row); grid.add(feeLabel, 1, row++);
        grid.add(new Label("Bonus Amount:"), 0, row); grid.add(bonusLabel, 1, row++);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean valid = loadBox.getValue() != null && isDouble(pctField.getText()) && Double.parseDouble(pctField.getText()) > 0 && Double.parseDouble(pctField.getText()) <= 100;
            okBtn.setDisable(!valid);
        };
        loadBox.valueProperty().addListener((obs, o, n) -> validate.run());
        pctField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                Load load = loadBox.getValue();
                double pct = Double.parseDouble(pctField.getText());
                double fee = load.getGrossAmount() * (selectedDriver.getServiceFeePercent() / 100.0);
                double bonus = fee * (pct / 100.0);
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        String.format("Are you sure you want to give bonus for %s on Load #%s?\nBonus: $%.2f",
                                selectedDriver.getName(), load.getLoadNumber(), bonus), ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Load Bonus");
                Optional<ButtonType> res = confirm.showAndWait();
                if (res.isEmpty() || res.get() != ButtonType.YES) return null;
                String type = "Load Bonus: " + load.getLoadNumber();
                String desc = String.format("Bonus %.2f%% of service fee (original $%.2f) for Load #%s", pct, fee, load.getLoadNumber());
                List<PayrollOtherAdjustments.OtherAdjustment> current = new ArrayList<>(adjustmentRows);
                // Create bonus adjustment with enhanced constructor
                current.add(new PayrollOtherAdjustments.OtherAdjustment(
                        0, selectedDriver.getId(), "Reimbursement", type, 
                        BigDecimal.valueOf(bonus), desc, start, load.getLoadNumber(),
                        "User", null, PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.ACTIVE
                ));
                adjustmentRows.setAll(current);
                adjustmentsLogic.saveAdjustmentsForDriverWeek(selectedDriver.getId(), start, current);
                updateAdjustmentTable();
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void updateBonusLabels(Load load, String pctText, Label feeLabel, Label bonusLabel, Employee driver) {
        if (load == null || !isDouble(pctText)) {
            feeLabel.setText("$0.00");
            bonusLabel.setText("$0.00");
            return;
        }
        double fee = load.getGrossAmount() * (driver.getServiceFeePercent() / 100.0);
        double pct = Double.parseDouble(pctText);
        double bonus = fee * (pct / 100.0);
        feeLabel.setText(String.format("$%.2f", fee));
        bonusLabel.setText(String.format("$%.2f", bonus));
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