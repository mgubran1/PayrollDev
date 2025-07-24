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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class PayrollOtherAdjustmentPanel extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(PayrollOtherAdjustmentPanel.class);
    
    public static final String[] DEDUCTION_TYPES = {
        "Fuel", "Damages", "Tolls (Deduction)", "Tickets & Violations", "Advances", "Unreturned Equipment", "Shortages",
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
    private final Button addBtn = ModernButtonStyles.createSuccessButton("Add Adjustment");
    private final Button editBtn = ModernButtonStyles.createInfoButton("Edit");
    private final Button removeBtn = ModernButtonStyles.createDangerButton("Remove");
    private final Button saveBtn = ModernButtonStyles.createPrimaryButton("Save Adjustments");
    private final Button bonusOnLoadBtn = ModernButtonStyles.createWarningButton("Bonus on Load");
    private final Button refreshBtn = ModernButtonStyles.createSecondaryButton("🔄 Refresh");
    private final TableView<PayrollOtherAdjustments.AuditEntry> auditTable = new TableView<>();
    
    // Summary labels
    private final Label totalDeductionsLabel = new Label("$0.00");
    private final Label totalReimbursementsLabel = new Label("$0.00");
    private final Label netAdjustmentLabel = new Label("$0.00");

    private LoadDAO loadDAO;

    public PayrollOtherAdjustmentPanel(PayrollOtherAdjustments adjustmentsLogic, ComboBox<Employee> driverBox, DatePicker weekStartPicker) {
        this.adjustmentsLogic = adjustmentsLogic;
        this.driverBox = driverBox;
        this.weekStartPicker = weekStartPicker;

        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f5f5f5;");
        
        // Header
        VBox headerBox = createHeaderSection();
        
        // Summary section
        HBox summaryBox = createSummarySection();
        
        // Table setup
        buildAdjustmentTable();
        buildAuditTable();

        // Button box
        HBox btnBox = new HBox(8, addBtn, editBtn, removeBtn, saveBtn, bonusOnLoadBtn, refreshBtn);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        btnBox.setPadding(new Insets(5, 0, 5, 0));

        // Audit section
        VBox auditSection = new VBox(5);
        Label auditLabel = new Label("Audit Trail");
        auditLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        auditSection.getChildren().addAll(auditLabel, auditTable);

        getChildren().addAll(headerBox, summaryBox, btnBox, adjustmentTable, auditSection);

        // Event handlers
        addBtn.setOnAction(e -> addAdjustmentDialog());
        editBtn.setOnAction(e -> editAdjustmentDialog());
        removeBtn.setOnAction(e -> removeAdjustment());
        saveBtn.setOnAction(e -> saveAdjustments());
        bonusOnLoadBtn.setOnAction(e -> bonusOnLoadDialog());
        refreshBtn.setOnAction(e -> updateAdjustmentTable());

        adjustmentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            boolean hasSelection = newSel != null;
            boolean canEdit = hasSelection && canEdit(newSel);
            editBtn.setDisable(!canEdit);
            removeBtn.setDisable(!hasSelection);
        });
        
        editBtn.setDisable(true);
        removeBtn.setDisable(true);

        driverBox.valueProperty().addListener((obs, o, n) -> updateAdjustmentTable());
        weekStartPicker.valueProperty().addListener((obs, o, n) -> updateAdjustmentTable());

        updateAdjustmentTable();
    }
    
    private VBox createHeaderSection() {
        VBox headerBox = new VBox(5);
        Label titleLabel = new Label("💰 Other Adjustments");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#1976d2"));
        
        Label subtitleLabel = new Label("Manage deductions and reimbursements");
        subtitleLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        subtitleLabel.setTextFill(Color.GRAY);
        
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);
        headerBox.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5;");
        return headerBox;
    }
    
    private HBox createSummarySection() {
        HBox summaryBox = new HBox(30);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setAlignment(Pos.CENTER);
        summaryBox.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
        
        VBox deductionsBox = createSummaryItem("Total Deductions", totalDeductionsLabel, Color.RED);
        VBox reimbursementsBox = createSummaryItem("Total Reimbursements", totalReimbursementsLabel, Color.GREEN);
        VBox netBox = createSummaryItem("Net Adjustment", netAdjustmentLabel, Color.BLUE);
        
        summaryBox.getChildren().addAll(deductionsBox, reimbursementsBox, netBox);
        return summaryBox;
    }
    
    private VBox createSummaryItem(String title, Label valueLabel, Color color) {
        VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        valueLabel.setTextFill(color);
        
        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    private void buildAdjustmentTable() {
        adjustmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        adjustmentTable.setPrefHeight(300);
        adjustmentTable.setStyle("-fx-background-color: #fff; -fx-font-size: 14px;");
        adjustmentTable.setPlaceholder(new Label("No adjustments for this period/driver."));

        TableColumn<PayrollOtherAdjustments.OtherAdjustment, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type));
        typeCol.setPrefWidth(150);
        
        TableColumn<PayrollOtherAdjustments.OtherAdjustment, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().category));
        catCol.setPrefWidth(100);
        catCol.setCellFactory(col -> new TableCell<PayrollOtherAdjustments.OtherAdjustment, String>() {
            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(category);
                    if ("Deduction".equals(category)) {
                        setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #388e3c; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        TableColumn<PayrollOtherAdjustments.OtherAdjustment, Double> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().amount.doubleValue()).asObject());
        amtCol.setPrefWidth(100);
        amtCol.setCellFactory(col -> new TableCell<PayrollOtherAdjustments.OtherAdjustment, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("$%,.2f", value));
                    setAlignment(Pos.CENTER_RIGHT);
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
        
        TableColumn<PayrollOtherAdjustments.OtherAdjustment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status.getDisplayName()));
        statusCol.setPrefWidth(80);
        statusCol.setCellFactory(col -> new TableCell<PayrollOtherAdjustments.OtherAdjustment, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "Active":
                            setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
                            break;
                        case "Reversed":
                            setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
                            break;
                        case "Pending Approval":
                            setStyle("-fx-text-fill: #ff9800; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
        
        adjustmentTable.getColumns().setAll(Arrays.asList(typeCol, catCol, amtCol, statusCol, descCol));
    }

    private void buildAuditTable() {
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auditTable.setPrefHeight(150);
        auditTable.setStyle("-fx-background-color: #f9f9f9; -fx-font-size: 12px;");
        auditTable.setPlaceholder(new Label("No audit entries for this period/driver."));
        
        TableColumn<PayrollOtherAdjustments.AuditEntry, String> tsCol = new TableColumn<>("Timestamp");
        tsCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().timestamp.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))));
        tsCol.setPrefWidth(120);
        
        TableColumn<PayrollOtherAdjustments.AuditEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().action));
        actionCol.setPrefWidth(150);
        
        TableColumn<PayrollOtherAdjustments.AuditEntry, String> detailsCol = new TableColumn<>("Details");
        detailsCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().details));
        
        TableColumn<PayrollOtherAdjustments.AuditEntry, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().performedBy));
        byCol.setPrefWidth(80);
        
        auditTable.getColumns().setAll(Arrays.asList(tsCol, actionCol, detailsCol, byCol));
    }

    public void updateAdjustmentTable() {
        adjustmentRows.clear();
        Employee selectedDriver = driverBox.getValue();
        LocalDate start = weekStartPicker.getValue();
        
        if (selectedDriver != null && start != null) {
            logger.debug("Updating adjustment table for driver {} week {}", selectedDriver.getName(), start);
            
            List<PayrollOtherAdjustments.OtherAdjustment> list = adjustmentsLogic.getAdjustmentsForDriverWeek(selectedDriver.getId(), start);
            adjustmentRows.setAll(list);
            
            // Update summary
            updateSummary(list);
            
            // Update audit table
            List<PayrollOtherAdjustments.AuditEntry> audit = adjustmentsLogic.getAuditTrail(String.valueOf(selectedDriver.getId()), start, start.plusDays(6));
            auditTable.getItems().setAll(audit);
            
            logger.info("Loaded {} adjustments and {} audit entries", list.size(), audit.size());
        } else {
            auditTable.getItems().clear();
            updateSummary(Collections.emptyList());
        }
    }
    
    private void updateSummary(List<PayrollOtherAdjustments.OtherAdjustment> adjustments) {
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalReimbursements = BigDecimal.ZERO;
        
        for (PayrollOtherAdjustments.OtherAdjustment adj : adjustments) {
            if (adj.status == PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.ACTIVE ||
                adj.status == PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.APPROVED) {
                if ("Deduction".equals(adj.category)) {
                    totalDeductions = totalDeductions.add(adj.amount);
                } else if ("Reimbursement".equals(adj.category)) {
                    totalReimbursements = totalReimbursements.add(adj.amount);
                }
            }
        }
        
        BigDecimal net = totalReimbursements.subtract(totalDeductions);
        
        totalDeductionsLabel.setText(String.format("$%,.2f", totalDeductions));
        totalReimbursementsLabel.setText(String.format("$%,.2f", totalReimbursements));
        netAdjustmentLabel.setText(String.format("$%,.2f", net));
        netAdjustmentLabel.setTextFill(net.compareTo(BigDecimal.ZERO) >= 0 ? Color.GREEN : Color.RED);
    }

    private boolean canEdit(PayrollOtherAdjustments.OtherAdjustment adj) {
        return adj.status == PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.ACTIVE ||
               adj.status == PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.PENDING;
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
            try {
                // Validation
                if (result.type.startsWith("Load Bonus:") && adjustmentRows.stream().anyMatch(a -> a.type.equals(result.type))) {
                    showError("A bonus for this load already exists for this week.");
                    return;
                }
                
                if (result.amount <= 0) {
                    showError("Amount must be positive.");
                    return;
                }
                
                // Create new adjustment with ID 0 (will be assigned by logic)
                PayrollOtherAdjustments.OtherAdjustment newAdjustment = new PayrollOtherAdjustments.OtherAdjustment(
                    0, selectedDriver.getId(), result.category, result.type,
                    BigDecimal.valueOf(result.amount), result.description, weekStart,
                    null, System.getProperty("user.name", "User"), null,
                    PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.ACTIVE
                );
                
                List<PayrollOtherAdjustments.OtherAdjustment> current = new ArrayList<>(adjustmentRows);
                current.add(newAdjustment);
                
                boolean saved = adjustmentsLogic.saveAdjustmentsForDriverWeek(selectedDriver.getId(), weekStart, current);
                
                if (saved) {
                    updateAdjustmentTable();
                    showInfo("Adjustment added successfully.");
                } else {
                    showError("Failed to save adjustment.");
                }
            } catch (Exception e) {
                logger.error("Error adding adjustment", e);
                showError("Error adding adjustment: " + e.getMessage());
            }
        }
    }

    private void editAdjustmentDialog() {
        PayrollOtherAdjustments.OtherAdjustment selected = adjustmentTable.getSelectionModel().getSelectedItem();
        if (selected == null || !canEdit(selected)) {
            showError("Select an editable adjustment to edit.");
            return;
        }
        
        OtherAdjustmentDialogResult result = showAdjustmentDialog(selected);
        if (result != null) {
            try {
                // Validation
                if (result.type.startsWith("Load Bonus:") && 
                    adjustmentRows.stream().anyMatch(a -> a.type.equals(result.type) && a.id != selected.id)) {
                    showError("A bonus for this load already exists for this week.");
                    return;
                }
                
                if (result.amount <= 0) {
                    showError("Amount must be positive.");
                    return;
                }
                
                // Create updated adjustment
                PayrollOtherAdjustments.OtherAdjustment updatedAdjustment = new PayrollOtherAdjustments.OtherAdjustment(
                    selected.id, selected.driverId, result.category, result.type,
                    BigDecimal.valueOf(result.amount), result.description, selected.weekStart,
                    selected.loadNumber, selected.createdBy, selected.referenceNumber, selected.status
                );
                
                List<PayrollOtherAdjustments.OtherAdjustment> current = new ArrayList<>(adjustmentRows);
                int idx = current.indexOf(selected);
                if (idx >= 0) {
                    current.set(idx, updatedAdjustment);
                    
                    boolean saved = adjustmentsLogic.saveAdjustmentsForDriverWeek(selected.driverId, selected.weekStart, current);
                    
                    if (saved) {
                        updateAdjustmentTable();
                        showInfo("Adjustment updated successfully.");
                    } else {
                        showError("Failed to update adjustment.");
                    }
                } else {
                    showError("Selected adjustment not found in current list.");
                }
            } catch (Exception e) {
                logger.error("Error editing adjustment", e);
                showError("Error editing adjustment: " + e.getMessage());
            }
        }
    }

    private void removeAdjustment() {
        PayrollOtherAdjustments.OtherAdjustment selected = adjustmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select an adjustment to remove.");
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Remove");
        alert.setHeaderText("Remove Adjustment");
        alert.setContentText(String.format("Are you sure you want to remove this %s adjustment?\nType: %s\nAmount: $%,.2f",
            selected.category, selected.type, selected.amount));
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                String user = System.getProperty("user.name", "User");
                PayrollOtherAdjustments.OperationResult opResult = adjustmentsLogic.removeAdjustmentById(
                    selected.id, user, "Removed by user");
                
                if (opResult.isSuccess()) {
                    updateAdjustmentTable();
                    showInfo("Adjustment removed successfully.");
                } else {
                    showError("Failed to remove adjustment: " + opResult.getMessage());
                }
            } catch (Exception e) {
                logger.error("Error removing adjustment", e);
                showError("Error removing adjustment: " + e.getMessage());
            }
        }
    }

    private void saveAdjustments() {
        Employee selectedDriver = driverBox.getValue();
        LocalDate weekStart = weekStartPicker.getValue();
        if (selectedDriver == null || weekStart == null) {
            showError("Select a driver and week to save adjustments.");
            return;
        }
        
        try {
            List<PayrollOtherAdjustments.OtherAdjustment> toSave = new ArrayList<>(adjustmentRows);
            boolean saved = adjustmentsLogic.saveAdjustmentsForDriverWeek(selectedDriver.getId(), weekStart, toSave);
            
            if (saved) {
                showInfo("Adjustments saved successfully.");
                updateAdjustmentTable();
            } else {
                showError("Failed to save adjustments.");
            }
        } catch (Exception e) {
            logger.error("Error saving adjustments", e);
            showError("Error saving adjustments: " + e.getMessage());
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
        customTypeField.setPromptText("Enter custom type...");
        customTypeField.setVisible(false);
        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        TextArea descField = new TextArea();
        descField.setPrefRowCount(3);
        descField.setPromptText("Enter description or notes...");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setVisible(false);

        if (adjustment != null) {
            categoryBox.setValue(adjustment.category);
            if ("Deduction".equals(adjustment.category)) {
                typeBox.setItems(FXCollections.observableArrayList(DEDUCTION_TYPES));
            } else {
                typeBox.setItems(FXCollections.observableArrayList(REIMBURSEMENT_TYPES));
            }
            
            // Check if type exists in list
            if (Arrays.asList(DEDUCTION_TYPES).contains(adjustment.type) || 
                Arrays.asList(REIMBURSEMENT_TYPES).contains(adjustment.type)) {
                typeBox.setValue(adjustment.type);
            } else {
                // It's a custom type
                if ("Deduction".equals(adjustment.category)) {
                    typeBox.setValue("Other (Custom Deduction)");
                } else {
                    typeBox.setValue("Other (Custom Reimbursement)");
                }
                customTypeField.setVisible(true);
                customTypeField.setText(adjustment.type);
            }
            
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
            customTypeField.setVisible(false);
            customTypeField.setText("");
        });
        
        typeBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null && isCustomType(n)) {
                customTypeField.setVisible(true);
            } else {
                customTypeField.setVisible(false);
                customTypeField.setText("");
            }
        });

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(15));
        
        int row = 0;
        grid.add(new Label("Category:"), 0, row);
        grid.add(categoryBox, 1, row++);
        
        grid.add(new Label("Type:"), 0, row);
        grid.add(typeBox, 1, row++);
        
        grid.add(new Label("Custom Type:"), 0, row);
        grid.add(customTypeField, 1, row++);
        
        grid.add(new Label("Amount:"), 0, row);
        grid.add(amountField, 1, row++);
        
        grid.add(new Label("Description:"), 0, row);
        grid.add(descField, 1, row++);
        
        grid.add(errorLabel, 0, row, 2, 1);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean valid = true;
            String errorMsg = "";
            
            if (categoryBox.getValue() == null) {
                valid = false;
                errorMsg = "Category is required";
            } else if (typeBox.getValue() == null) {
                valid = false;
                errorMsg = "Type is required";
            } else if (isCustomType(typeBox.getValue()) && customTypeField.getText().trim().isEmpty()) {
                valid = false;
                errorMsg = "Custom type name is required";
            } else if (!isValidAmount(amountField.getText())) {
                valid = false;
                errorMsg = "Amount must be a positive number";
            }
            
            errorLabel.setText(errorMsg);
            errorLabel.setVisible(!valid);
            okBtn.setDisable(!valid);
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
        return type != null && (type.contains("Custom") || type.contains("Other"));
    }

    private boolean isValidAmount(String s) {
        try {
            double d = Double.parseDouble(s);
            return d > 0;
        } catch (Exception e) {
            return false;
        }
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
            public String toString(Load l) {
                return l == null ? "" : String.format("Load #%s  $%.2f", l.getLoadNumber(), l.getGrossAmount());
            }
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
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(15));
        
        int row = 0;
        grid.add(new Label("Select Load:"), 0, row);
        grid.add(loadBox, 1, row++);
        grid.add(new Label("Bonus Percentage (of service fee):"), 0, row);
        grid.add(pctField, 1, row++);
        grid.add(new Label("Original Service Fee:"), 0, row);
        grid.add(feeLabel, 1, row++);
        grid.add(new Label("Bonus Amount:"), 0, row);
        grid.add(bonusLabel, 1, row++);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean valid = loadBox.getValue() != null && isValidPercentage(pctField.getText());
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
                    String.format("Are you sure you want to give a bonus for %s on Load #%s?\nBonus: $%.2f",
                        selectedDriver.getName(), load.getLoadNumber(), bonus), ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Load Bonus");
                Optional<ButtonType> res = confirm.showAndWait();
                
                if (res.isPresent() && res.get() == ButtonType.YES) {
                    try {
                        String type = "Load Bonus: " + load.getLoadNumber();
                        String desc = String.format("Bonus %.2f%% of service fee (original $%.2f) for Load #%s", 
                            pct, fee, load.getLoadNumber());
                        
                        PayrollOtherAdjustments.OtherAdjustment bonusAdj = new PayrollOtherAdjustments.OtherAdjustment(
                            0, selectedDriver.getId(), "Reimbursement", type,
                            BigDecimal.valueOf(bonus), desc, start, load.getLoadNumber(),
                            System.getProperty("user.name", "User"), null,
                            PayrollOtherAdjustments.OtherAdjustment.AdjustmentStatus.ACTIVE
                        );
                        
                        List<PayrollOtherAdjustments.OtherAdjustment> current = new ArrayList<>(adjustmentRows);
                        current.add(bonusAdj);
                        
                        boolean saved = adjustmentsLogic.saveAdjustmentsForDriverWeek(selectedDriver.getId(), start, current);
                        
                        if (saved) {
                            updateAdjustmentTable();
                            showInfo("Load bonus added successfully.");
                        } else {
                            showError("Failed to save load bonus.");
                        }
                    } catch (Exception e) {
                        logger.error("Error adding load bonus", e);
                        showError("Error adding load bonus: " + e.getMessage());
                    }
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private boolean isValidPercentage(String s) {
        try {
            double d = Double.parseDouble(s);
            return d > 0 && d <= 100;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateBonusLabels(Load load, String pctText, Label feeLabel, Label bonusLabel, Employee driver) {
        if (load == null || !isValidPercentage(pctText)) {
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