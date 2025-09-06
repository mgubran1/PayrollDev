package com.company.payroll.employees;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.util.converter.LocalDateStringConverter;
import javafx.geometry.Pos;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import java.util.Stack;
import java.util.UUID;

public class PercentageConfigurationDialog extends Dialog<List<EmployeePercentageHistory>> {
    private static final Logger logger = LoggerFactory.getLogger(PercentageConfigurationDialog.class);
    
    private final TableView<EmployeePercentageRow> employeeTable;
    private final DatePicker effectiveDatePicker;
    private final TextField bulkDriverPercentField;
    private final TextField bulkCompanyPercentField;
    private final TextField bulkServiceFeePercentField;
    private final TextArea notesArea;
    private final CheckBox selectAllCheckBox;
    private final Label previewLabel;
    private final Label statusLabel;
    
    private final EmployeeDAO employeeDAO;
    private final EmployeePercentageHistoryDAO percentageHistoryDAO;
    private final PercentageAuditLogDAO auditLogDAO;
    private final ObservableList<EmployeePercentageRow> employeeRows = FXCollections.observableArrayList();
    private final ObservableList<EmployeePercentageRow> selectedRows = FXCollections.observableArrayList();
    
    // Audit tracking
    private final String sessionId = UUID.randomUUID().toString();
    private final String currentUser = System.getProperty("user.name", "Unknown");
    private final List<PercentageAuditLog> sessionAuditLogs = new ArrayList<>();
    
    public PercentageConfigurationDialog(Connection connection) {
        this.employeeDAO = new EmployeeDAO(connection);
        this.percentageHistoryDAO = new EmployeePercentageHistoryDAO(connection);
        
        // Initialize audit logging - allow graceful degradation if it fails
        PercentageAuditLogDAO tempAuditDAO = null;
        try {
            tempAuditDAO = new PercentageAuditLogDAO(connection);
        } catch (Exception e) {
            logger.warn("Failed to initialize audit logging, continuing without it", e);
        }
        this.auditLogDAO = tempAuditDAO;
        
        setTitle("Configure Driver Percentages");
        setHeaderText("Update percentage rates for drivers (Session: " + sessionId.substring(0, 8) + ")");
        setResizable(true);
        
        // Initialize controls
        effectiveDatePicker = new DatePicker(LocalDate.now().plusDays(1));
        bulkDriverPercentField = new TextField();
        bulkDriverPercentField.getStyleClass().add("percentage-bulk-field");
        bulkCompanyPercentField = new TextField();
        bulkCompanyPercentField.getStyleClass().add("percentage-bulk-field");
        bulkServiceFeePercentField = new TextField();
        bulkServiceFeePercentField.getStyleClass().add("percentage-bulk-field");
        notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        selectAllCheckBox = new CheckBox("Select All");
        selectAllCheckBox.getStyleClass().add("percentage-select-all");
        previewLabel = new Label();
        previewLabel.getStyleClass().add("percentage-preview");
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("percentage-config-status");
        
        // Setup table with CSS styling
        employeeTable = new TableView<>();
        employeeTable.getStyleClass().add("percentage-config-table");
        setupTable();
        loadEmployees();
        
        // Create layout
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefWidth(900);
        content.setPrefHeight(600);
        
        // Effective date section
        HBox dateBox = new HBox(10);
        dateBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        dateBox.getChildren().addAll(
            new Label("Effective Date:"),
            effectiveDatePicker,
            new Label("(Changes will apply from this date forward)")
        );
        
        // Status bar with CSS styling
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.getStyleClass().add("percentage-config-status-bar");
        
        Label userLabel = new Label("User: " + currentUser);
        userLabel.getStyleClass().add("user-label");
        
        Label sessionLabel = new Label("Session: " + sessionId.substring(0, 8));
        sessionLabel.getStyleClass().add("session-id-label");
        
        statusLabel.getStyleClass().add("percentage-config-status");
        
        statusBar.getChildren().addAll(
            userLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            sessionLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            statusLabel
        );
        
        // Bulk update section
        TitledPane bulkUpdatePane = createBulkUpdateSection();
        
        // Employee table section
        VBox tableSection = new VBox(5);
        tableSection.getChildren().addAll(
            createTableHeader(),
            employeeTable,
            previewLabel
        );
        VBox.setVgrow(employeeTable, Priority.ALWAYS);
        
        // Notes section
        VBox notesSection = new VBox(5);
        notesSection.getChildren().addAll(
            new Label("Notes (optional):"),
            notesArea
        );
        
        content.getChildren().addAll(
            statusBar,
            dateBox,
            new Separator(),
            bulkUpdatePane,
            new Separator(),
            tableSection,
            new Separator(),
            notesSection
        );
        
        getDialogPane().setContent(content);
        
        // CRITICAL: Load the stylesheet for visual enhancements
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles.css").toExternalForm()
        );
        
        // Add buttons
        ButtonType applyButtonType = new ButtonType("Apply Changes", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);
        
        // Enable/disable apply button based on selection
        Button applyButton = (Button) getDialogPane().lookupButton(applyButtonType);
        applyButton.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(selectedRows));
        
        // Add tooltip to help users
        Tooltip applyTooltip = new Tooltip("Select drivers and modify their percentages before applying changes");
        Tooltip.install(applyButton, applyTooltip);
        
        // Convert result
        setResultConverter(dialogButton -> {
            System.out.println("Result converter called with button: " + dialogButton); // Debug
            if (dialogButton == applyButtonType) {
                List<EmployeePercentageHistory> result = createPercentageHistoryEntries();
                System.out.println("Created " + result.size() + " percentage history entries"); // Debug
                return result;
            }
            return null;
        });
        
        // Update preview when selection changes
        employeeTable.setOnMouseClicked(e -> updatePreview());
        effectiveDatePicker.valueProperty().addListener((obs, old, newVal) -> {
            updatePreview();
            // Reload employees to check for existing configurations on the new date
            loadEmployees();
        });
    }
    
    private TitledPane createBulkUpdateSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        grid.add(new Label("Driver %:"), 0, 0);
        grid.add(bulkDriverPercentField, 1, 0);
        grid.add(new Label("Company %:"), 2, 0);
        grid.add(bulkCompanyPercentField, 3, 0);
        grid.add(new Label("Service Fee %:"), 4, 0);
        grid.add(bulkServiceFeePercentField, 5, 0);
        
        Button applyBulkButton = new Button("Apply to Selected");
        applyBulkButton.getStyleClass().add("percentage-apply-button");
        applyBulkButton.setOnAction(e -> applyBulkUpdate());
        
        // Enable/disable based on having values and selection
        applyBulkButton.disableProperty().bind(
            javafx.beans.binding.Bindings.createBooleanBinding(
                () -> {
                    boolean hasValues = !bulkDriverPercentField.getText().trim().isEmpty() ||
                                      !bulkCompanyPercentField.getText().trim().isEmpty() ||
                                      !bulkServiceFeePercentField.getText().trim().isEmpty();
                    boolean hasSelection = selectedRows.size() > 0;
                    return !hasValues || !hasSelection;
                },
                bulkDriverPercentField.textProperty(),
                bulkCompanyPercentField.textProperty(),
                bulkServiceFeePercentField.textProperty(),
                selectedRows
            )
        );
        
        grid.add(applyBulkButton, 6, 0);
        
        // Set column constraints
        for (int i = 0; i < 7; i++) {
            if (i % 2 == 1) {
                ColumnConstraints col = new ColumnConstraints();
                col.setPrefWidth(80);
                grid.getColumnConstraints().add(col);
            }
        }
        
        TitledPane pane = new TitledPane("Bulk Update - Enter New Percentages", grid);
        pane.setCollapsible(true);
        pane.setExpanded(true); // Expanded by default since it's the only way to update
        
        return pane;
    }
    
    private HBox createTableHeader() {
        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            selectedRows.clear();
            if (selected) {
                employeeRows.forEach(row -> {
                    row.setSelected(true);
                    selectedRows.add(row);
                });
            } else {
                employeeRows.forEach(row -> row.setSelected(false));
            }
            updatePreview();
        });
        
        Label instructionLabel = new Label("1. Check the box to select drivers  2. Use Bulk Update section to set new percentages  3. Click Apply Changes");
        instructionLabel.getStyleClass().add("percentage-instructions");
        
        header.getChildren().addAll(selectAllCheckBox, instructionLabel);
        return header;
    }
    
    private void setupTable() {
        // Selection column
        TableColumn<EmployeePercentageRow, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(column -> {
            CheckBoxTableCell<EmployeePercentageRow, Boolean> cell = new CheckBoxTableCell<>();
            cell.setSelectedStateCallback(index -> {
                if (index >= 0 && index < employeeRows.size()) {
                    return employeeRows.get(index).selectedProperty();
                }
                return null;
            });
            return cell;
        });
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        // Employee info columns
        TableColumn<EmployeePercentageRow, String> nameCol = new TableColumn<>("Driver Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(150);
        
        TableColumn<EmployeePercentageRow, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(cellData -> cellData.getValue().unitProperty());
        unitCol.setPrefWidth(80);
        
        // Current percentage columns
        TableColumn<EmployeePercentageRow, Double> currentDriverCol = new TableColumn<>("Current Driver %");
        currentDriverCol.setCellValueFactory(cellData -> cellData.getValue().currentDriverPercentProperty().asObject());
        currentDriverCol.setPrefWidth(110);
        
        TableColumn<EmployeePercentageRow, Double> currentCompanyCol = new TableColumn<>("Current Company %");
        currentCompanyCol.setCellValueFactory(cellData -> cellData.getValue().currentCompanyPercentProperty().asObject());
        currentCompanyCol.setPrefWidth(120);
        
        TableColumn<EmployeePercentageRow, Double> currentServiceCol = new TableColumn<>("Current Service Fee %");
        currentServiceCol.setCellValueFactory(cellData -> cellData.getValue().currentServiceFeePercentProperty().asObject());
        currentServiceCol.setPrefWidth(120);
        
        // New percentage columns (updated via bulk update only) - with styling
        TableColumn<EmployeePercentageRow, Double> newDriverCol = new TableColumn<>("New Driver %");
        newDriverCol.setCellValueFactory(cellData -> cellData.getValue().newDriverPercentProperty().asObject());
        newDriverCol.setPrefWidth(100);
        newDriverCol.setCellFactory(column -> createPercentageCell(true));
        newDriverCol.getStyleClass().add("new-percentage-column");
        newDriverCol.setStyle("-fx-background-color: #FFFDE7;");
        
        TableColumn<EmployeePercentageRow, Double> newCompanyCol = new TableColumn<>("New Company %");
        newCompanyCol.setCellValueFactory(cellData -> cellData.getValue().newCompanyPercentProperty().asObject());
        newCompanyCol.setPrefWidth(110);
        newCompanyCol.setCellFactory(column -> createPercentageCell(true));
        newCompanyCol.getStyleClass().add("new-percentage-column");
        newCompanyCol.setStyle("-fx-background-color: #FFFDE7;");
        
        TableColumn<EmployeePercentageRow, Double> newServiceCol = new TableColumn<>("New Service Fee %");
        newServiceCol.setCellValueFactory(cellData -> cellData.getValue().newServiceFeePercentProperty().asObject());
        newServiceCol.setPrefWidth(110);
        newServiceCol.setCellFactory(column -> createPercentageCell(true));
        newServiceCol.getStyleClass().add("new-percentage-column");
        newServiceCol.setStyle("-fx-background-color: #FFFDE7;");
        
        @SuppressWarnings("unchecked")
        TableColumn<EmployeePercentageRow, ?>[] columns = new TableColumn[] {
            selectCol, nameCol, unitCol,
            currentDriverCol, currentCompanyCol, currentServiceCol,
            newDriverCol, newCompanyCol, newServiceCol
        };
        employeeTable.getColumns().addAll(columns);
        
        employeeTable.setItems(employeeRows);
        employeeTable.setEditable(true); // Required for checkbox column to work
    }
    
    private void loadEmployees() {
        try {
            List<Employee> employees = employeeDAO.getAll();
            employeeRows.clear();
            
            LocalDate checkDate = effectiveDatePicker.getValue();
            if (checkDate == null) {
                checkDate = LocalDate.now();
            }
            
            for (Employee emp : employees) {
                if (emp.isActive() && emp.isDriver()) {
                    EmployeePercentageRow row = new EmployeePercentageRow(emp);
                    
                    // Check if there's a pending configuration for this employee
                    try {
                        EmployeePercentageHistory pendingConfig = percentageHistoryDAO.getEffectivePercentages(
                            emp.getId(), checkDate.plusDays(1));
                        
                        // If there's a configuration that starts after today, it's a pending configuration
                        if (pendingConfig != null && pendingConfig.getEffectiveDate().isAfter(LocalDate.now())) {
                            row.setNewDriverPercent(pendingConfig.getDriverPercent());
                            row.setNewCompanyPercent(pendingConfig.getCompanyPercent());
                            row.setNewServiceFeePercent(pendingConfig.getServiceFeePercent());
                        }
                    } catch (Exception e) {
                        // Ignore errors when checking for pending configurations
                    }
                    
                    row.selectedProperty().addListener((obs, old, newVal) -> {
                        updatePreview();
                        if (newVal) {
                            selectedRows.add(row);
                        } else {
                            selectedRows.remove(row);
                        }
                    });
                    employeeRows.add(row);
                }
            }
        } catch (DataAccessException e) {
            showError("Failed to load employees", e.getMessage());
        }
    }
    
    private void applyBulkUpdate() {
        try {
            Double driverPct = parsePercentage(bulkDriverPercentField.getText());
            Double companyPct = parsePercentage(bulkCompanyPercentField.getText());
            Double servicePct = parsePercentage(bulkServiceFeePercentField.getText());
            
            // Check if at least one percentage is provided
            if (driverPct == null && companyPct == null && servicePct == null) {
                showError("No Values", "Please enter at least one percentage value to update");
                return;
            }
            
            // Validate percentages sum to 100 if both driver and company are provided
            if (driverPct != null && companyPct != null) {
                double sum = driverPct + companyPct;
                if (Math.abs(sum - 100.0) > 0.01) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Percentage Validation");
                    confirm.setHeaderText("Driver + Company = " + String.format("%.1f%%", sum));
                    confirm.setContentText("The percentages don't sum to 100%. Continue anyway?");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        return;
                    }
                }
            }
            
            // Count updated rows and track changes for audit
            int updatedCount = 0;
            List<PercentageAuditLog> bulkAuditLogs = new ArrayList<>();
            
            // Update selected rows
            for (EmployeePercentageRow row : employeeRows) {
                if (row.isSelected()) {
                    boolean updated = false;
                    
                    // Track and apply driver percent change
                    if (driverPct != null && Math.abs(row.getNewDriverPercent() - driverPct) > 0.01) {
                        double oldValue = row.getNewDriverPercent();
                        row.setNewDriverPercent(driverPct);
                        updated = true;
                        
                        // Create audit log (if audit logging is available)
                        if (auditLogDAO != null) {
                            bulkAuditLogs.add(new PercentageAuditLog(
                                row.getEmployee().getId(),
                                row.getEmployee().getName(),
                                "BULK_UPDATE",
                                "driver_percent",
                                oldValue,
                                driverPct,
                                currentUser,
                                "Bulk update from dialog",
                                sessionId
                            ));
                        }
                    }
                    
                    // Track and apply company percent change
                    if (companyPct != null && Math.abs(row.getNewCompanyPercent() - companyPct) > 0.01) {
                        double oldValue = row.getNewCompanyPercent();
                        row.setNewCompanyPercent(companyPct);
                        updated = true;
                        
                        if (auditLogDAO != null) {
                            bulkAuditLogs.add(new PercentageAuditLog(
                                row.getEmployee().getId(),
                                row.getEmployee().getName(),
                                "BULK_UPDATE",
                                "company_percent",
                                oldValue,
                                companyPct,
                                currentUser,
                                "Bulk update from dialog",
                                sessionId
                            ));
                        }
                    }
                    
                    // Track and apply service fee percent change
                    if (servicePct != null && Math.abs(row.getNewServiceFeePercent() - servicePct) > 0.01) {
                        double oldValue = row.getNewServiceFeePercent();
                        row.setNewServiceFeePercent(servicePct);
                        updated = true;
                        
                        if (auditLogDAO != null) {
                            bulkAuditLogs.add(new PercentageAuditLog(
                                row.getEmployee().getId(),
                                row.getEmployee().getName(),
                                "BULK_UPDATE",
                                "service_fee_percent",
                                oldValue,
                                servicePct,
                                currentUser,
                                "Bulk update from dialog",
                                sessionId
                            ));
                        }
                    }
                    
                    if (updated) {
                        updatedCount++;
                        // Trigger property change listeners
                        row.setNewDriverPercent(row.getNewDriverPercent());
                        row.setNewCompanyPercent(row.getNewCompanyPercent());
                        row.setNewServiceFeePercent(row.getNewServiceFeePercent());
                    }
                }
            }
            
            // Save audit logs to session (if audit logging is available)
            if (auditLogDAO != null) {
                sessionAuditLogs.addAll(bulkAuditLogs);
            }
            
            // Force table refresh
            employeeTable.refresh();
            
            // Clear the bulk update fields and update status
            if (updatedCount > 0) {
                bulkDriverPercentField.clear();
                bulkCompanyPercentField.clear();
                bulkServiceFeePercentField.clear();
                
                // Update status with animation using CSS classes
                statusLabel.setText("✅ Updated " + updatedCount + " driver(s)");
                statusLabel.getStyleClass().clear();
                statusLabel.getStyleClass().addAll("percentage-config-status", "percentage-config-status-success");
                
                Timeline timeline = new Timeline(new KeyFrame(
                    Duration.seconds(3),
                    e -> {
                        statusLabel.setText("Ready");
                        statusLabel.getStyleClass().clear();
                        statusLabel.getStyleClass().add("percentage-config-status");
                    }
                ));
                timeline.play();
                
                updatePreview();
                logger.info("Bulk update applied to {} drivers", updatedCount);
            }
            
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter valid percentage values");
            logger.error("Invalid percentage input", e);
        }
    }
    
    private Double parsePercentage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return Double.parseDouble(text.trim());
    }
    
    private void updatePreview() {
        long selectedCount = employeeRows.stream().filter(EmployeePercentageRow::isSelected).count();
        LocalDate effectiveDate = effectiveDatePicker.getValue();
        
        if (selectedCount == 0) {
            previewLabel.setText("No drivers selected");
            previewLabel.setStyle("-fx-text-fill: #757575;");
        } else {
            previewLabel.setText(String.format(
                "Preview: %d driver(s) selected for percentage update effective %s",
                selectedCount,
                effectiveDate != null ? effectiveDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : "immediately"
            ));
            previewLabel.setStyle("-fx-text-fill: #1976d2;");
        }
    }
    
    private List<EmployeePercentageHistory> createPercentageHistoryEntries() {
        List<EmployeePercentageHistory> histories = new ArrayList<>();
        LocalDate effectiveDate = effectiveDatePicker.getValue();
        String notes = notesArea.getText().trim();
        String createdBy = System.getProperty("user.name", "SYSTEM");
        
        // Create final audit logs for all changes
        List<PercentageAuditLog> finalAuditLogs = new ArrayList<>();
        
        for (EmployeePercentageRow row : employeeRows) {
            if (row.isSelected() && row.hasChanges()) {
                // Create history entry with the new values
                EmployeePercentageHistory history = new EmployeePercentageHistory(
                    row.getEmployee().getId(),
                    row.getNewDriverPercent(),
                    row.getNewCompanyPercent(),
                    row.getNewServiceFeePercent(),
                    effectiveDate
                );
                history.setCreatedBy(createdBy);
                history.setNotes(notes);
                histories.add(history);
                
                // Create final audit logs for each changed field (if audit logging is available)
                if (auditLogDAO != null) {
                    if (Math.abs(row.getCurrentDriverPercent() - row.getNewDriverPercent()) > 0.01) {
                        finalAuditLogs.add(new PercentageAuditLog(
                            row.getEmployee().getId(),
                            row.getEmployee().getName(),
                            "FINAL_UPDATE",
                            "driver_percent",
                            row.getCurrentDriverPercent(),
                            row.getNewDriverPercent(),
                            currentUser,
                            "Final configuration: " + notes,
                            sessionId
                        ));
                    }
                    
                    if (Math.abs(row.getCurrentCompanyPercent() - row.getNewCompanyPercent()) > 0.01) {
                        finalAuditLogs.add(new PercentageAuditLog(
                            row.getEmployee().getId(),
                            row.getEmployee().getName(),
                            "FINAL_UPDATE",
                            "company_percent",
                            row.getCurrentCompanyPercent(),
                            row.getNewCompanyPercent(),
                            currentUser,
                            "Final configuration: " + notes,
                            sessionId
                        ));
                    }
                    
                    if (Math.abs(row.getCurrentServiceFeePercent() - row.getNewServiceFeePercent()) > 0.01) {
                        finalAuditLogs.add(new PercentageAuditLog(
                            row.getEmployee().getId(),
                            row.getEmployee().getName(),
                            "FINAL_UPDATE",
                            "service_fee_percent",
                            row.getCurrentServiceFeePercent(),
                            row.getNewServiceFeePercent(),
                            currentUser,
                            "Final configuration: " + notes,
                            sessionId
                        ));
                    }
                }
            }
        }
        
        // Save all audit logs to database (if audit logging is available)
        if (auditLogDAO != null && (!sessionAuditLogs.isEmpty() || !finalAuditLogs.isEmpty())) {
            try {
                List<PercentageAuditLog> allLogs = new ArrayList<>(sessionAuditLogs);
                allLogs.addAll(finalAuditLogs);
                auditLogDAO.logChanges(allLogs);
                logger.info("Saved {} audit log entries for session {}", allLogs.size(), sessionId);
            } catch (Exception e) {
                logger.error("Failed to save audit logs", e);
                // Don't fail the operation if audit logging fails
            }
        }
        
        return histories;
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private TableCell<EmployeePercentageRow, Double> createPercentageCell(boolean isNewColumn) {
        return new TableCell<EmployeePercentageRow, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(String.format("%.1f%%", value));
                    
                    if (isNewColumn) {
                        // Check if this is different from current value
                        EmployeePercentageRow row = getTableView().getItems().get(getIndex());
                        boolean hasChanged = false;
                        
                        if (getTableColumn().getText().contains("Driver")) {
                            hasChanged = Math.abs(value - row.currentDriverPercent.get()) > 0.01;
                        } else if (getTableColumn().getText().contains("Company")) {
                            hasChanged = Math.abs(value - row.currentCompanyPercent.get()) > 0.01;
                        } else if (getTableColumn().getText().contains("Service")) {
                            hasChanged = Math.abs(value - row.currentServiceFeePercent.get()) > 0.01;
                        }
                        
                        if (hasChanged) {
                            getStyleClass().clear();
                            getStyleClass().add("percentage-cell-modified");
                            Tooltip tooltip = new Tooltip("Percentage will be updated");
                            tooltip.getStyleClass().add("percentage-tooltip");
                            setTooltip(tooltip);
                        } else {
                            getStyleClass().clear();
                            getStyleClass().add("percentage-cell-readonly");
                            setTooltip(null);
                        }
                    }
                }
                
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        };
    }
    
    // Inner class for table rows
    public static class EmployeePercentageRow {
        private final Employee employee;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleStringProperty name;
        private final SimpleStringProperty unit;
        private final SimpleDoubleProperty currentDriverPercent;
        private final SimpleDoubleProperty currentCompanyPercent;
        private final SimpleDoubleProperty currentServiceFeePercent;
        private final SimpleDoubleProperty newDriverPercent;
        private final SimpleDoubleProperty newCompanyPercent;
        private final SimpleDoubleProperty newServiceFeePercent;
        
        public EmployeePercentageRow(Employee employee) {
            this.employee = employee;
            this.name = new SimpleStringProperty(employee.getName());
            this.unit = new SimpleStringProperty(employee.getTruckUnit());
            this.currentDriverPercent = new SimpleDoubleProperty(employee.getDriverPercent());
            this.currentCompanyPercent = new SimpleDoubleProperty(employee.getCompanyPercent());
            this.currentServiceFeePercent = new SimpleDoubleProperty(employee.getServiceFeePercent());
            this.newDriverPercent = new SimpleDoubleProperty(employee.getDriverPercent());
            this.newCompanyPercent = new SimpleDoubleProperty(employee.getCompanyPercent());
            this.newServiceFeePercent = new SimpleDoubleProperty(employee.getServiceFeePercent());
        }
        
        public boolean hasChanges() {
            return currentDriverPercent.get() != newDriverPercent.get() ||
                   currentCompanyPercent.get() != newCompanyPercent.get() ||
                   currentServiceFeePercent.get() != newServiceFeePercent.get();
        }
        
        // Getters and setters
        public Employee getEmployee() { return employee; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }
        public SimpleBooleanProperty selectedProperty() { return selected; }
        
        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty unitProperty() { return unit; }
        
        public SimpleDoubleProperty currentDriverPercentProperty() { return currentDriverPercent; }
        public SimpleDoubleProperty currentCompanyPercentProperty() { return currentCompanyPercent; }
        public SimpleDoubleProperty currentServiceFeePercentProperty() { return currentServiceFeePercent; }
        
        public double getCurrentDriverPercent() { return currentDriverPercent.get(); }
        public double getCurrentCompanyPercent() { return currentCompanyPercent.get(); }
        public double getCurrentServiceFeePercent() { return currentServiceFeePercent.get(); }
        
        public double getNewDriverPercent() { return newDriverPercent.get(); }
        public void setNewDriverPercent(double value) { newDriverPercent.set(value); }
        public SimpleDoubleProperty newDriverPercentProperty() { return newDriverPercent; }
        
        public double getNewCompanyPercent() { return newCompanyPercent.get(); }
        public void setNewCompanyPercent(double value) { newCompanyPercent.set(value); }
        public SimpleDoubleProperty newCompanyPercentProperty() { return newCompanyPercent; }
        
        public double getNewServiceFeePercent() { return newServiceFeePercent.get(); }
        public void setNewServiceFeePercent(double value) { newServiceFeePercent.set(value); }
        public SimpleDoubleProperty newServiceFeePercentProperty() { return newServiceFeePercent; }
        
        public void resetToOriginal() {
            newDriverPercent.set(currentDriverPercent.get());
            newCompanyPercent.set(currentCompanyPercent.get());
            newServiceFeePercent.set(currentServiceFeePercent.get());
        }
    }
}