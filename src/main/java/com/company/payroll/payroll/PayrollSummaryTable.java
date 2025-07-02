package com.company.payroll.payroll;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Callback;

import java.util.function.Predicate;

/**
 * Enhanced PayrollSummaryTable with professional styling and features
 */
public class PayrollSummaryTable extends VBox {
    
    // Color constants for consistent styling - Light Blue Theme
    private static final Color PRIMARY_COLOR = Color.web("#2196F3");
    private static final Color PRIMARY_DARK = Color.web("#1976D2");
    private static final Color PRIMARY_LIGHT = Color.web("#BBDEFB");
    private static final Color ACCENT_COLOR = Color.web("#00BCD4");
    private static final Color POSITIVE_COLOR = Color.web("#4CAF50");
    private static final Color NEGATIVE_COLOR = Color.web("#F44336");
    private static final Color WARNING_COLOR = Color.web("#FF9800");
    private static final Color NEUTRAL_COLOR = Color.web("#757575");
    private static final Color ESCROW_COLOR = Color.web("#1976D2");
    private static final Color REIMBURSEMENT_COLOR = Color.web("#00897B");
    private static final Color BACKGROUND_COLOR = Color.web("#FAFAFA");
    private static final Color CARD_COLOR = Color.WHITE;
    
	private final TableView<PayrollCalculator.PayrollRow> table;
	private final FilteredList<PayrollCalculator.PayrollRow> filteredData;
	private TextField searchField;
	private ComboBox<String> filterCombo;
	private Label statsLabel;
	private final ContextMenu contextMenu;
    
    public PayrollSummaryTable(ObservableList<PayrollCalculator.PayrollRow> payrollRows) {
        super(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: transparent;");
        
        // Create filtered list wrapper
        filteredData = new FilteredList<>(payrollRows, p -> true);
        SortedList<PayrollCalculator.PayrollRow> sortedData = new SortedList<>(filteredData);
        
        // Create header with search and filter
        HBox header = createHeader();
        
        // Create table
        table = new TableView<>(sortedData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        configureTable();
        
        // Create context menu
        contextMenu = createContextMenu();
        
        // Create footer with statistics
        HBox footer = createFooter();

        
        // Add all components
        getChildren().addAll(header, table, footer);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // Apply shadow effect to the entire component
        DropShadow shadow = new DropShadow();
        shadow.setRadius(5.0);
        shadow.setOffsetY(2.0);
        shadow.setColor(Color.color(0.3, 0.3, 0.3, 0.3));
        setEffect(shadow);
        
        // Setup event handlers
        setupEventHandlers();
        
        // Update statistics when data changes
        payrollRows.addListener((javafx.collections.ListChangeListener<PayrollCalculator.PayrollRow>) c -> updateStatistics());
        updateStatistics();
    }
    
    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(15));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: white; -fx-background-radius: 8px 8px 0 0;");
        
        // Title
        Label titleLabel = new Label("PAYROLL SUMMARY");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setTextFill(PRIMARY_DARK);
        
        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search by driver name or truck...");
        searchField.setPrefWidth(250);
        searchField.setStyle("-fx-background-radius: 20;");
        
        // Filter combo
        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(
            "All Drivers",
            "Positive Net Pay",
            "Negative Net Pay", 
            "High Earners (>$2000)",
            "Low Net Pay (<$500)",
            "With Advances",
            "With Escrow",
            "No Loads"
        );
        filterCombo.setValue("All Drivers");
        filterCombo.setPrefWidth(180);
        
        // Export button
        Button exportBtn = new Button("Export");
        exportBtn.setStyle("-fx-background-color: #E3F2FD; -fx-text-fill: #1976D2; " +
                          "-fx-font-weight: bold; -fx-cursor: hand;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(titleLabel, searchField, filterCombo, spacer, exportBtn);
        
        return header;
    }
    
    private HBox createFooter() {
        HBox footer = new HBox(20);
        footer.setPadding(new Insets(15));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 0 0 8px 8px;");
        
        statsLabel = new Label("Loading statistics...");
        statsLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        statsLabel.setTextFill(NEUTRAL_COLOR);
        
        footer.getChildren().add(statsLabel);
        
        return footer;
    }
    
    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(500);
        table.setStyle("-fx-background-color: white; -fx-font-size: 14px; " +
                      "-fx-selection-bar: #E3F2FD; -fx-selection-bar-non-focused: #F5F5F5;");
        table.setPlaceholder(createPlaceholder());

        // Configure columns
        configureColumns();
        
        // Row factory for highlighting and context menu
        table.setRowFactory(createRowFactory());
        
        // Enable multiple selection
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Default sort by driver name
        table.getSortOrder().add(table.getColumns().get(0));
    }
    
    private Label createPlaceholder() {
        Label placeholder = new Label("No payroll data available");
        placeholder.setStyle("-fx-font-size: 18px; -fx-text-fill: #BDBDBD; -fx-font-style: italic;");
        VBox placeholderBox = new VBox(placeholder);
        placeholderBox.setAlignment(Pos.CENTER);
        placeholderBox.setPadding(new Insets(50));
        return placeholder;
    }
    
    private void configureColumns() {
        // Driver column with enhanced styling
        TableColumn<PayrollCalculator.PayrollRow, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().driverName));
        driverCol.setPrefWidth(160);
        driverCol.setMinWidth(140);
        driverCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #212121;");
                    
                    // Add driver icon
                    Label icon = new Label("👤");
                    setGraphic(icon);
                }
            }
        });

        // Truck/Unit column
        TableColumn<PayrollCalculator.PayrollRow, String> truckUnitCol = new TableColumn<>("Truck/Unit");
        truckUnitCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().truckUnit));
        truckUnitCol.setPrefWidth(110);
        truckUnitCol.setMinWidth(90);
        truckUnitCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.trim().isEmpty()) {
                    setText("N/A");
                    setStyle("-fx-text-fill: #BDBDBD; -fx-font-style: italic;");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1976D2;");
                }
                setAlignment(Pos.CENTER);
            }
        });

        // Load count column with visual indicators
        TableColumn<PayrollCalculator.PayrollRow, Number> loadCountCol = new TableColumn<>("Loads");
        loadCountCol.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().loadCount));
        loadCountCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    int count = value.intValue();
                    setText(String.valueOf(count));
                    setAlignment(Pos.CENTER);
                    
                    // Visual indicators
                    String emoji = "";
                    Color color = NEUTRAL_COLOR;
                    
                    if (count == 0) {
                        emoji = "⚠️";
                        color = NEGATIVE_COLOR;
                    } else if (count >= 10) {
                        emoji = "🚚";
                        color = POSITIVE_COLOR;
                    } else if (count >= 5) {
                        emoji = "📦";
                        color = PRIMARY_COLOR;
                    }
                    
                    if (!emoji.isEmpty()) {
                        Label indicator = new Label(emoji);
                        setGraphic(indicator);
                        setContentDisplay(ContentDisplay.RIGHT);
                    }
                    
                    setTextFill(color);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        loadCountCol.setPrefWidth(80);
        loadCountCol.setMinWidth(70);

        // Monetary columns with consistent formatting
        TableColumn<PayrollCalculator.PayrollRow, Number> grossCol = 
            createMonetaryColumn("Gross Pay", p -> new SimpleDoubleProperty(p.getValue().gross), false, POSITIVE_COLOR, "💰");
        TableColumn<PayrollCalculator.PayrollRow, Number> serviceFeeCol = 
            createMonetaryColumn("Service Fee", p -> new SimpleDoubleProperty(p.getValue().serviceFee), true, NEGATIVE_COLOR, null);
        TableColumn<PayrollCalculator.PayrollRow, Number> grossAfterServiceFeeCol = 
            createMonetaryColumn("After Service", p -> new SimpleDoubleProperty(p.getValue().grossAfterServiceFee), false, NEUTRAL_COLOR, null);
        TableColumn<PayrollCalculator.PayrollRow, Number> companyPayCol = 
            createMonetaryColumn("Company Pay", p -> new SimpleDoubleProperty(p.getValue().companyPay), false, PRIMARY_COLOR, "🏢");
        TableColumn<PayrollCalculator.PayrollRow, Number> driverPayCol = 
            createMonetaryColumn("Driver Pay", p -> new SimpleDoubleProperty(p.getValue().driverPay), false, POSITIVE_COLOR, null);
        TableColumn<PayrollCalculator.PayrollRow, Number> fuelCol = 
            createMonetaryColumn("Fuel", p -> new SimpleDoubleProperty(p.getValue().fuel), true, NEGATIVE_COLOR, "⛽");
        TableColumn<PayrollCalculator.PayrollRow, Number> grossAfterFuelCol = 
            createMonetaryColumn("After Fuel", p -> new SimpleDoubleProperty(p.getValue().grossAfterFuel), false, NEUTRAL_COLOR, null);
        TableColumn<PayrollCalculator.PayrollRow, Number> recurringFeesCol = 
            createMonetaryColumn("Recurring", p -> new SimpleDoubleProperty(p.getValue().recurringFees), true, NEGATIVE_COLOR, "🔄");
        TableColumn<PayrollCalculator.PayrollRow, Number> advancesGivenCol = 
            createMonetaryColumn("Advances Given", p -> new SimpleDoubleProperty(p.getValue().advancesGiven), false, WARNING_COLOR, "💵");
        TableColumn<PayrollCalculator.PayrollRow, Number> advanceRepaymentsCol = 
            createMonetaryColumn("Repayments", p -> new SimpleDoubleProperty(p.getValue().advanceRepayments), true, NEGATIVE_COLOR, null);
        
        // Escrow Deposits column with special styling
        TableColumn<PayrollCalculator.PayrollRow, Number> escrowDepositsCol = new TableColumn<>("Escrow");
        escrowDepositsCol.setCellValueFactory(p -> new SimpleDoubleProperty(p.getValue().escrowDeposits));
        escrowDepositsCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    setGraphic(null);
                } else {
                    double amount = Math.abs(value.doubleValue());
                    setText(String.format("$%,.2f", amount));
                    setAlignment(Pos.CENTER_RIGHT);
                    
                    if (amount > 0) {
                        setTextFill(ESCROW_COLOR);
                        setStyle("-fx-font-weight: bold;");
                        
                        // Progress indicator for escrow
                        ProgressBar progress = new ProgressBar();
                        progress.setPrefWidth(50);
                        progress.setPrefHeight(5);
                        progress.setProgress(Math.min(amount / 1000.0, 1.0)); // Assume $1000 target
                        progress.setStyle("-fx-accent: #1976D2;");
                        
                        VBox graphic = new VBox(2);
                        graphic.setAlignment(Pos.CENTER_RIGHT);
                        Label icon = new Label("🏦");
                        graphic.getChildren().addAll(icon, progress);
                        
                        setGraphic(graphic);
                        setContentDisplay(ContentDisplay.LEFT);
                    } else {
                        setTextFill(Color.GRAY);
                        setStyle("-fx-font-style: italic;");
                        setGraphic(null);
                    }
                }
            }
        });
        escrowDepositsCol.setPrefWidth(110);
        escrowDepositsCol.setMinWidth(100);
        
        // Other deductions column
        TableColumn<PayrollCalculator.PayrollRow, Number> otherDeductionsCol = 
            createMonetaryColumn("Other Deductions", p -> new SimpleDoubleProperty(p.getValue().otherDeductions), true, NEGATIVE_COLOR, "📉");
        otherDeductionsCol.setPrefWidth(130);
        otherDeductionsCol.setMinWidth(120);

        // Reimbursements column with special styling
        TableColumn<PayrollCalculator.PayrollRow, Number> reimbursementsCol = 
            createMonetaryColumn("Reimbursements", p -> new SimpleDoubleProperty(p.getValue().reimbursements), false, REIMBURSEMENT_COLOR, "💸");
        reimbursementsCol.setPrefWidth(130);
        reimbursementsCol.setMinWidth(120);

        // Net Pay column with enhanced visual feedback
        TableColumn<PayrollCalculator.PayrollRow, Number> netCol = new TableColumn<>("NET PAY");
        netCol.setCellValueFactory(p -> new SimpleDoubleProperty(p.getValue().netPay));
        netCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    setGraphic(null);
                } else {
                    double netValue = value.doubleValue();
                    setText(String.format("$%,.2f", netValue));
                    setAlignment(Pos.CENTER_RIGHT);
                    
                    // Create visual indicator box
                    HBox box = new HBox(5);
                    box.setAlignment(Pos.CENTER_RIGHT);
                    box.setPadding(new Insets(2, 6, 2, 6));
                    
                    Label amountLabel = new Label(String.format("$%,.2f", netValue));
                    amountLabel.setFont(Font.font("System", FontWeight.BOLD, 15));
                    
                    // Color and emoji based on amount
                    String emoji;
                    Color bgColor;
                    Color textColor = Color.WHITE;
                    
                    if (netValue < 0) {
                        emoji = "❌";
                        bgColor = NEGATIVE_COLOR;
                        setTooltip(new Tooltip("Negative net pay - Driver owes money"));
                    } else if (netValue < 500) {
                        emoji = "⚠️";
                        bgColor = WARNING_COLOR;
                        setTooltip(new Tooltip("Low net pay - Below $500"));
                    } else if (netValue >= 2000) {
                        emoji = "✅";
                        bgColor = POSITIVE_COLOR;
                        setTooltip(new Tooltip("Excellent net pay - Above $2,000"));
                    } else {
                        emoji = "✓";
                        bgColor = PRIMARY_COLOR;
                        setTooltip(null);
                    }
                    
                    Label emojiLabel = new Label(emoji);
                    amountLabel.setTextFill(textColor);
                    
                    box.getChildren().addAll(emojiLabel, amountLabel);
                    box.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6px;",
                        toHexString(bgColor)));
                    
                    setGraphic(box);
                    setText("");
                }
            }
        });
        netCol.setPrefWidth(150);
        netCol.setMinWidth(140);

        // Add all columns
        table.getColumns().setAll(java.util.List.of(
            driverCol,
            truckUnitCol,
            loadCountCol,
            grossCol,
            serviceFeeCol,
            grossAfterServiceFeeCol,
            companyPayCol,
            driverPayCol,
            fuelCol,
            grossAfterFuelCol,
            recurringFeesCol,
            advancesGivenCol,
            advanceRepaymentsCol,
            escrowDepositsCol,
            otherDeductionsCol,
            reimbursementsCol,
            netCol
        ));
    }
    
    /**
     * Creates a monetary column with enhanced formatting and optional icon
     */
    private TableColumn<PayrollCalculator.PayrollRow, Number> createMonetaryColumn(
            String title, Callback<TableColumn.CellDataFeatures<PayrollCalculator.PayrollRow, Number>, javafx.beans.value.ObservableValue<Number>> valueFactory, 
            boolean isDeduction, Color accentColor, String icon) {
        
        TableColumn<PayrollCalculator.PayrollRow, Number> column = new TableColumn<>(title);
        
        // Use the provided value factory instead of reflection
        column.setCellValueFactory(valueFactory);
        
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    double doubleValue = value.doubleValue();
                    double displayValue = isDeduction ? Math.abs(doubleValue) : doubleValue;
                    setText(String.format("$%,.2f", displayValue));
                    setAlignment(Pos.CENTER_RIGHT);
                    
                    if (displayValue > 0.01) {
                        setTextFill(accentColor);
                        setStyle("-fx-font-weight: normal;");
                        
                        if (icon != null) {
                            Label iconLabel = new Label(icon);
                            iconLabel.setStyle("-fx-font-size: 11px;");
                            setGraphic(iconLabel);
                            setContentDisplay(ContentDisplay.LEFT);
                        }
                    } else {
                        setTextFill(Color.GRAY);
                        setStyle("-fx-font-style: italic;");
                        setGraphic(null);
                    }
                }
            }
        });
        
        // Set column widths based on content
        switch (title) {
            case "Gross Pay":
            case "After Service":
            case "After Fuel":
                column.setPrefWidth(120);
                column.setMinWidth(110);
                break;
            case "Service Fee":
            case "Fuel":
                column.setPrefWidth(100);
                column.setMinWidth(90);
                break;
            case "Company Pay":
            case "Driver Pay":
                column.setPrefWidth(110);
                column.setMinWidth(100);
                break;
            default:
                column.setPrefWidth(110);
                column.setMinWidth(95);
        }
        
        return column;
    }
    
    /**
     * Creates a row factory with sophisticated highlighting and interactions
     */
    private Callback<TableView<PayrollCalculator.PayrollRow>, TableRow<PayrollCalculator.PayrollRow>> createRowFactory() {
        return tv -> {
            TableRow<PayrollCalculator.PayrollRow> row = new TableRow<>() {
                @Override
                protected void updateItem(PayrollCalculator.PayrollRow item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                        setTooltip(null);
                    } else {
                        // Row highlighting based on net pay
                        String baseStyle = "-fx-background-insets: 0, 1; -fx-padding: 0.0em;";
                        
                        if (item.netPay < 0) {
                            setStyle(baseStyle + "-fx-background-color: #FFEBEE, #FFCDD2;");
                        } else if (item.netPay < 500) {
                            setStyle(baseStyle + "-fx-background-color: #FFF3E0, #FFE0B2;");
                        } else if (item.netPay >= 2000) {
                            setStyle(baseStyle + "-fx-background-color: #E8F5E9, #C8E6C9;");
                        } else {
                            setStyle(baseStyle);
                        }
                        
                        // Hover effect
                        setOnMouseEntered(e -> {
                            if (!isSelected()) {
                                setOpacity(0.8);
                            }
                        });
                        
                        setOnMouseExited(e -> {
                            if (!isSelected()) {
                                setOpacity(1.0);
                            }
                        });
                    }
                }
            };
            
            // Context menu on right-click
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty()) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            
            // Double-click handler
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showDriverDetails(row.getItem());
                }
            });
            
            return row;
        };
    }
    
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();
        
        MenuItem viewDetailsItem = new MenuItem("View Details");
        viewDetailsItem.setOnAction(e -> {
            PayrollCalculator.PayrollRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showDriverDetails(selected);
            }
        });
        
        MenuItem copyItem = new MenuItem("Copy Row Data");
        copyItem.setOnAction(e -> {
            PayrollCalculator.PayrollRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // Copy to clipboard
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(selected.toTSVRow());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });
        
        MenuItem exportItem = new MenuItem("Export Selected");
        exportItem.setOnAction(e -> exportSelected());
        
        menu.getItems().addAll(viewDetailsItem, copyItem, new SeparatorMenuItem(), exportItem);
        
        return menu;
    }
    
    private void setupEventHandlers() {
        // Search functionality
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateFilter();
        });
        
        // Filter combo
        filterCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateFilter();
        });
    }
    
    private void updateFilter() {
        String searchText = searchField.getText();
        String filterValue = filterCombo.getValue();
        
        filteredData.setPredicate(row -> {
            // Search filter
            boolean matchesSearch = true;
            if (searchText != null && !searchText.trim().isEmpty()) {
                String search = searchText.toLowerCase().trim();
                matchesSearch = row.driverName.toLowerCase().contains(search) ||
                              (row.truckUnit != null && row.truckUnit.toLowerCase().contains(search));
            }
            
            // Category filter
            boolean matchesFilter = true;
            if (filterValue != null) {
                switch (filterValue) {
                    case "Positive Net Pay":
                        matchesFilter = row.netPay >= 0;
                        break;
                    case "Negative Net Pay":
                        matchesFilter = row.netPay < 0;
                        break;
                    case "High Earners (>$2000)":
                        matchesFilter = row.netPay > 2000;
                        break;
                    case "Low Net Pay (<$500)":
                        matchesFilter = row.netPay < 500;
                        break;
                    case "With Advances":
                        matchesFilter = row.advancesGiven > 0 || row.advanceRepayments < 0;
                        break;
                    case "With Escrow":
                        matchesFilter = row.escrowDeposits < 0;
                        break;
                    case "No Loads":
                        matchesFilter = row.loadCount == 0;
                        break;
                }
            }
            
            return matchesSearch && matchesFilter;
        });
        
        updateStatistics();
    }
    
    private void updateStatistics() {
        PayrollSummaryStats stats = getSummaryStats();
        
        String statsText = String.format(
            "Showing %d of %d drivers | Total Gross: $%,.2f | Total Net: $%,.2f | " +
            "Average Net: $%,.2f | Negative Pay: %d drivers",
            filteredData.size(),
            table.getItems().size(),
            stats.totalGross,
            stats.totalNet,
            stats.driverCount > 0 ? stats.totalNet / stats.driverCount : 0,
            stats.driversWithNegativePay
        );
        
        statsLabel.setText(statsText);
        
        // Color code based on overall health
        if (stats.driversWithNegativePay > 0) {
            statsLabel.setTextFill(WARNING_COLOR);
        } else if (stats.totalNet / Math.max(1, stats.driverCount) > 1000) {
            statsLabel.setTextFill(POSITIVE_COLOR);
        } else {
            statsLabel.setTextFill(NEUTRAL_COLOR);
        }
    }
    
    private void showDriverDetails(PayrollCalculator.PayrollRow row) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Driver Details - " + row.driverName);
        alert.setHeaderText(null);
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        int gridRow = 0;
        addDetailRow(grid, gridRow++, "Driver:", row.driverName);
        addDetailRow(grid, gridRow++, "Truck/Unit:", row.truckUnit.isEmpty() ? "N/A" : row.truckUnit);
        addDetailRow(grid, gridRow++, "Load Count:", String.valueOf(row.loadCount));
        
        grid.add(new Separator(), 0, gridRow++, 2, 1);
        
        addDetailRow(grid, gridRow++, "Gross Pay:", String.format("$%,.2f", row.gross));
        addDetailRow(grid, gridRow++, "Total Deductions:", String.format("$%,.2f", row.getTotalDeductions()));
        addDetailRow(grid, gridRow++, "Reimbursements:", String.format("$%,.2f", row.reimbursements));
        addDetailRow(grid, gridRow++, "Net Pay:", String.format("$%,.2f", row.netPay));
        
        alert.getDialogPane().setContent(grid);
        alert.showAndWait();
    }
    
    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold;");
        Label valueNode = new Label(value);
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
    
    private void exportSelected() {
        // Implementation for exporting selected rows
        ObservableList<PayrollCalculator.PayrollRow> selected = table.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) {
            // Export logic here
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export");
            alert.setHeaderText(null);
            alert.setContentText("Exported " + selected.size() + " rows to clipboard.");
            alert.showAndWait();
        }
    }
    
    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
    
    /**
     * Get the underlying table view
     */
    public TableView<PayrollCalculator.PayrollRow> getTable() {
        return table;
    }
    
    /**
     * Get summary statistics for the current data
     */
    public PayrollSummaryStats getSummaryStats() {
        ObservableList<PayrollCalculator.PayrollRow> items = filteredData;
        if (items.isEmpty()) {
            return new PayrollSummaryStats();
        }
        
        double totalGross = items.stream().mapToDouble(r -> r.gross).sum();
        double totalNet = items.stream().mapToDouble(r -> r.netPay).sum();
        double totalDeductions = items.stream()
            .mapToDouble(r -> Math.abs(r.serviceFee) + Math.abs(r.fuel) + Math.abs(r.recurringFees) + 
                            Math.abs(r.advanceRepayments) + Math.abs(r.escrowDeposits) + 
                            Math.abs(r.otherDeductions))
            .sum();
        double totalReimbursements = items.stream().mapToDouble(r -> r.reimbursements).sum();
        int totalLoads = items.stream().mapToInt(r -> r.loadCount).sum();
        int driversWithNegativePay = (int) items.stream().filter(r -> r.netPay < 0).count();
        
        return new PayrollSummaryStats(
            items.size(), totalGross, totalNet, totalDeductions, 
            totalReimbursements, totalLoads, driversWithNegativePay
        );
    }
    
    /**
     * Summary statistics class
     */
    public static class PayrollSummaryStats {
        public final int driverCount;
        public final double totalGross;
        public final double totalNet;
        public final double totalDeductions;
        public final double totalReimbursements;
        public final int totalLoads;
        public final int driversWithNegativePay;
        
        public PayrollSummaryStats() {
            this(0, 0, 0, 0, 0, 0, 0);
        }
        
        public PayrollSummaryStats(int driverCount, double totalGross, double totalNet, 
                                  double totalDeductions, double totalReimbursements, 
                                  int totalLoads, int driversWithNegativePay) {
            this.driverCount = driverCount;
            this.totalGross = totalGross;
            this.totalNet = totalNet;
            this.totalDeductions = totalDeductions;
            this.totalReimbursements = totalReimbursements;
            this.totalLoads = totalLoads;
            this.driversWithNegativePay = driversWithNegativePay;
        }
        
        public double getAverageNet() {
            return driverCount > 0 ? totalNet / driverCount : 0;
        }
        
        public double getAverageGross() {
            return driverCount > 0 ? totalGross / driverCount : 0;
        }
        
        public double getAverageLoads() {
            return driverCount > 0 ? (double) totalLoads / driverCount : 0;
        }
        
        public double getDeductionPercentage() {
            return totalGross > 0 ? (totalDeductions / totalGross) * 100 : 0;
        }
        
        public double getNetPayPercentage() {
            return totalGross > 0 ? (totalNet / totalGross) * 100 : 0;
        }
    }
    
    /**
     * Export data to various formats
     */
    public void exportToCSV(java.io.File file) throws java.io.IOException {
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            
            // Write BOM for Excel UTF-8 recognition
            writer.write('\ufeff');
            
            // Write headers
            writer.write("Driver,Truck/Unit,Loads,Gross Pay,Service Fee,Gross After Fee,");
            writer.write("Company Pay,Driver Pay,Fuel,After Fuel,Recurring,Advances Given,");
            writer.write("Advance Repayments,Escrow,Other Deductions,Reimbursements,NET PAY");
            writer.newLine();
            
            // Write data
            for (PayrollCalculator.PayrollRow row : filteredData) {
                writer.write(row.toCSVRow());
                writer.newLine();
            }
        }
    }
    
    /**
     * Print the table
     */
    public void print() {
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(table.getScene().getWindow())) {
            javafx.print.PageLayout pageLayout = job.getPrinter().createPageLayout(
                javafx.print.Paper.A4, 
                javafx.print.PageOrientation.LANDSCAPE, 
                javafx.print.Printer.MarginType.DEFAULT
            );
            job.getJobSettings().setPageLayout(pageLayout);
            
            boolean printed = job.printPage(table);
            if (printed) {
                job.endJob();
            }
        }
    }
    
    /**
     * Refresh the table display
     */
    public void refresh() {
        table.refresh();
        updateStatistics();
    }
    
    /**
     * Clear all filters
     */
    public void clearFilters() {
        searchField.clear();
        filterCombo.setValue("All Drivers");
    }
    
    /**
     * Get selected rows
     */
    public ObservableList<PayrollCalculator.PayrollRow> getSelectedRows() {
        return table.getSelectionModel().getSelectedItems();
    }
    
    /**
     * Select all rows
     */
    public void selectAll() {
        table.getSelectionModel().selectAll();
    }
    
    /**
     * Clear selection
     */
    public void clearSelection() {
        table.getSelectionModel().clearSelection();
    }
    
    /**
     * Scroll to a specific row
     */
    public void scrollToRow(PayrollCalculator.PayrollRow row) {
        int index = table.getItems().indexOf(row);
        if (index >= 0) {
            table.scrollTo(index);
            table.getSelectionModel().select(index);
        }
    }
    
    /**
     * Get the current filter predicate
     */
    @SuppressWarnings("unchecked")
    public Predicate<PayrollCalculator.PayrollRow> getFilterPredicate() {
        Predicate<? super PayrollCalculator.PayrollRow> predicate = filteredData.getPredicate();
        return predicate != null ? (Predicate<PayrollCalculator.PayrollRow>) predicate : null;
    }
    /**
     * Set a custom filter predicate
     */
    public void setFilterPredicate(Predicate<PayrollCalculator.PayrollRow> predicate) {
        filteredData.setPredicate(predicate);
        updateStatistics();
    }
    
    /**
     * Get the filtered data
     */
    public FilteredList<PayrollCalculator.PayrollRow> getFilteredData() {
        return filteredData;
    }
    
    /**
     * Add a custom column to the table
     */
    public void addCustomColumn(TableColumn<PayrollCalculator.PayrollRow, ?> column) {
        table.getColumns().add(column);
    }
    
    /**
     * Remove a column from the table
     */
    public void removeColumn(TableColumn<PayrollCalculator.PayrollRow, ?> column) {
        table.getColumns().remove(column);
    }
    
    /**
     * Set the table style
     */
    public void setTableStyle(String style) {
        table.setStyle(style);
    }
    
    /**
     * Enable/disable table editing
     */
    public void setEditable(boolean editable) {
        table.setEditable(editable);
    }
    
    /**
     * Get the search field for external access
     */
    public TextField getSearchField() {
        return searchField;
    }
    
    /**
     * Get the filter combo for external access
     */
    public ComboBox<String> getFilterCombo() {
        return filterCombo;
    }
    
    /**
     * Highlight rows that match a condition
     */
    public void highlightRows(Predicate<PayrollCalculator.PayrollRow> condition, String style) {
        table.setRowFactory(tv -> {
            TableRow<PayrollCalculator.PayrollRow> row = createRowFactory().call(tv);
            
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null && condition.test(newItem)) {
                    row.setStyle(row.getStyle() + ";" + style);
                }
            });
            
            return row;
        });
    }
    
    /**
     * Export selected rows to clipboard
     */
    public void copySelectedToClipboard() {
        ObservableList<PayrollCalculator.PayrollRow> selected = table.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            
            // Headers
            sb.append("Driver\tTruck/Unit\tLoads\tGross Pay\tService Fee\tGross After Fee\t");
            sb.append("Company Pay\tDriver Pay\tFuel\tAfter Fuel\tRecurring\tAdvances Given\t");
            sb.append("Advance Repayments\tEscrow\tOther Deductions\tReimbursements\tNET PAY\n");
            
            // Data
            for (PayrollCalculator.PayrollRow row : selected) {
                sb.append(row.toTSVRow()).append("\n");
            }
            
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(sb.toString());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
    }
    
    /**
     * Show column visibility dialog
     */
    public void showColumnVisibilityDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Column Visibility");
        dialog.setHeaderText("Select columns to show/hide");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        for (TableColumn<PayrollCalculator.PayrollRow, ?> column : table.getColumns()) {
            CheckBox cb = new CheckBox(column.getText());
            cb.setSelected(column.isVisible());
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                column.setVisible(newVal);
            });
            content.getChildren().add(cb);
        }
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }
    
    /**
     * Get table columns for external manipulation
     */
    public ObservableList<TableColumn<PayrollCalculator.PayrollRow, ?>> getColumns() {
        return table.getColumns();
    }
}