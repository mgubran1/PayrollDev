package com.company.payroll.driver;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.collections.FXCollections;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.scene.chart.PieChart;
import javafx.scene.Node;
import java.text.NumberFormat; 
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog for showing detailed driver income information
 */
public class DriverDetailsDialog extends Dialog<Void> {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    // Theme colors
    private static final Color PRIMARY_COLOR = Color.web("#2196F3");
    private static final Color SECONDARY_COLOR = Color.web("#757575");
    private static final Color SUCCESS_COLOR = Color.web("#4CAF50");
    private static final Color ERROR_COLOR = Color.web("#F44336");
    private static final Color WARNING_COLOR = Color.web("#FF9800");
    
    public DriverDetailsDialog(DriverIncomeData data) {
        setTitle("Driver Income Details - " + data.getDriverName());
        setHeaderText(null);
        initModality(Modality.APPLICATION_MODAL);
        
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Summary Tab
        Tab summaryTab = new Tab("📊 Summary");
        summaryTab.setContent(createSummaryContent(data));
        
        // Income Tab
        Tab incomeTab = new Tab("💰 Income Breakdown");
        incomeTab.setContent(createIncomeContent(data));
        
        // Loads Tab
        Tab loadsTab = new Tab("🚚 Loads");
        loadsTab.setContent(createLoadsContent(data));
        
        // Deductions Tab
        Tab deductionsTab = new Tab("📉 Deductions");
        deductionsTab.setContent(createDeductionsContent(data));
        
        // Analytics Tab
        Tab analyticsTab = new Tab("📈 Analytics");
        analyticsTab.setContent(createAnalyticsContent(data));
        
        tabPane.getTabs().addAll(summaryTab, incomeTab, loadsTab, deductionsTab, analyticsTab);
        
        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefSize(900, 700);
        
        // Style the dialog
        getDialogPane().setStyle("-fx-font-size: 14px;");
        Button closeButton = (Button) getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
    }
    
    private ScrollPane createSummaryContent(DriverIncomeData data) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #f5f5f5;");
        
        // Header Card
        VBox headerCard = createCard();
        headerCard.setStyle(headerCard.getStyle() + "-fx-background-color: linear-gradient(to right, #2196F3, #1976D2);");
        
        Label nameLabel = new Label(data.getDriverName());
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        nameLabel.setTextFill(Color.WHITE);
        
        Label periodLabel = new Label("Period: " + 
            data.getStartDate().format(DATE_FORMAT) + " - " + 
            data.getEndDate().format(DATE_FORMAT));
        periodLabel.setFont(Font.font("Arial", 14));
        periodLabel.setTextFill(Color.WHITE);
        
        Label truckLabel = new Label("Truck/Unit: " + (data.getTruckUnit() != null ? data.getTruckUnit() : "N/A"));
        truckLabel.setFont(Font.font("Arial", 14));
        truckLabel.setTextFill(Color.WHITE);
        
        headerCard.getChildren().addAll(nameLabel, periodLabel, truckLabel);
        
        // Key Metrics Grid
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(20);
        
        // Create metric cards
        VBox grossCard = createMetricCard("Total Gross", CURRENCY_FORMAT.format(data.getTotalGross()), "💵", SUCCESS_COLOR);
        VBox netCard = createMetricCard("Net Pay", CURRENCY_FORMAT.format(data.getNetPay()), "💰", 
            data.getNetPay() >= 0 ? SUCCESS_COLOR : ERROR_COLOR);
        VBox loadsCard = createMetricCard("Total Loads", String.valueOf(data.getTotalLoads()), "🚚", PRIMARY_COLOR);
        VBox milesCard = createMetricCard("Total Miles", String.format("%.1f", data.getTotalMiles()), "🛣️", PRIMARY_COLOR);
        VBox avgMileCard = createMetricCard("Avg per Mile", String.format("$%.3f", data.getAveragePerMile()), "📊", WARNING_COLOR);
        VBox fuelEffCard = createMetricCard("Fuel Efficiency", 
            data.getFuelEfficiency() > 0 ? String.format("%.2f MPG", data.getFuelEfficiency()) : "N/A", "⛽", SUCCESS_COLOR);
        
        metricsGrid.add(grossCard, 0, 0);
        metricsGrid.add(netCard, 1, 0);
        metricsGrid.add(loadsCard, 2, 0);
        metricsGrid.add(milesCard, 0, 1);
        metricsGrid.add(avgMileCard, 1, 1);
        metricsGrid.add(fuelEffCard, 2, 1);
        
        // Performance Indicators
        VBox performanceCard = createCard();
        Label perfTitle = new Label("Performance Indicators");
        perfTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        perfTitle.setTextFill(PRIMARY_COLOR);
        
        VBox indicators = new VBox(10);
        indicators.setPadding(new Insets(10, 0, 0, 0));
        
        indicators.getChildren().addAll(
            createPerformanceIndicator("Deduction Rate", data.getDeductionPercentage(), "%"),
            createPerformanceIndicator("Net Pay Rate", data.getNetPayPercentage(), "%"),
            createPerformanceIndicator("Fuel Cost Rate", 
                data.getTotalGross() > 0 ? (data.getTotalFuelAmount() / data.getTotalGross()) * 100 : 0, "%")
        );
        
        performanceCard.getChildren().addAll(perfTitle, new Separator(), indicators);
        
        content.getChildren().addAll(headerCard, metricsGrid, performanceCard);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        return scrollPane;
    }
    
    private VBox createIncomeContent(DriverIncomeData data) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #f5f5f5;");
        
        // Income Breakdown Card
        VBox incomeCard = createCard();
        Label incomeTitle = new Label("Income Breakdown");
        incomeTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        incomeTitle.setTextFill(PRIMARY_COLOR);
        
        GridPane incomeGrid = new GridPane();
        incomeGrid.setHgap(30);
        incomeGrid.setVgap(10);
        incomeGrid.setPadding(new Insets(20, 0, 0, 0));
        
        int row = 0;
        addDetailRow(incomeGrid, row++, "Gross Revenue:", CURRENCY_FORMAT.format(data.getTotalGross()), SUCCESS_COLOR, true);
        addDetailRow(incomeGrid, row++, "Service Fee:", CURRENCY_FORMAT.format(-data.getServiceFee()), ERROR_COLOR, false);
        
        // Separator
        Separator sep1 = new Separator();
        GridPane.setColumnSpan(sep1, 2);
        incomeGrid.add(sep1, 0, row++);
        
        addDetailRow(incomeGrid, row++, "Gross After Service:", CURRENCY_FORMAT.format(data.getGrossAfterServiceFee()), null, true);
        addDetailRow(incomeGrid, row++, "Company Pay:", CURRENCY_FORMAT.format(data.getCompanyPay()), PRIMARY_COLOR, false);
        addDetailRow(incomeGrid, row++, "Driver Pay:", CURRENCY_FORMAT.format(data.getDriverPay()), SUCCESS_COLOR, false);
        
        incomeCard.getChildren().addAll(incomeTitle, new Separator(), incomeGrid);
        
        // Additional Income Card
        VBox additionalCard = createCard();
        Label additionalTitle = new Label("Additional Income");
        additionalTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        additionalTitle.setTextFill(PRIMARY_COLOR);
        
        GridPane additionalGrid = new GridPane();
        additionalGrid.setHgap(30);
        additionalGrid.setVgap(10);
        additionalGrid.setPadding(new Insets(20, 0, 0, 0));
        
        row = 0;
        addDetailRow(additionalGrid, row++, "Reimbursements:", CURRENCY_FORMAT.format(data.getReimbursements()), SUCCESS_COLOR, false);
        addDetailRow(additionalGrid, row++, "Advances Given:", CURRENCY_FORMAT.format(data.getAdvancesGiven()), WARNING_COLOR, false);
        
        additionalCard.getChildren().addAll(additionalTitle, new Separator(), additionalGrid);
        
        content.getChildren().addAll(incomeCard, additionalCard);
        
        return content;
    }
    
    private ScrollPane createLoadsContent(DriverIncomeData data) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        
        // Summary Card
        VBox summaryCard = createCard();
        Label summaryTitle = new Label("Loads Summary");
        summaryTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        summaryTitle.setTextFill(PRIMARY_COLOR);
        
        HBox summaryBox = new HBox(40);
        summaryBox.setPadding(new Insets(20, 0, 0, 0));
        
        VBox loadCountBox = new VBox(5);
        Label loadCountLabel = new Label("Total Loads");
        loadCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        Label loadCountValue = new Label(String.valueOf(data.getTotalLoads()));
        loadCountValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        loadCountValue.setTextFill(PRIMARY_COLOR);
        loadCountBox.getChildren().addAll(loadCountLabel, loadCountValue);
        
        VBox avgGrossBox = new VBox(5);
        Label avgGrossLabel = new Label("Average Gross");
        avgGrossLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        Label avgGrossValue = new Label(CURRENCY_FORMAT.format(
            data.getTotalLoads() > 0 ? data.getTotalGross() / data.getTotalLoads() : 0));
        avgGrossValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        avgGrossValue.setTextFill(SUCCESS_COLOR);
        avgGrossBox.getChildren().addAll(avgGrossLabel, avgGrossValue);
        
        VBox avgMilesBox = new VBox(5);
        Label avgMilesLabel = new Label("Average Miles");
        avgMilesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        Label avgMilesValue = new Label(String.format("%.1f", 
            data.getTotalLoads() > 0 ? data.getTotalMiles() / data.getTotalLoads() : 0));
        avgMilesValue.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        avgMilesValue.setTextFill(WARNING_COLOR);
        avgMilesBox.getChildren().addAll(avgMilesLabel, avgMilesValue);
        
        summaryBox.getChildren().addAll(loadCountBox, avgGrossBox, avgMilesBox);
        summaryCard.getChildren().addAll(summaryTitle, new Separator(), summaryBox);
        
        // Loads Table
        TableView<DriverIncomeData.LoadDetail> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(data.getLoads()));
        table.setPrefHeight(400);
        
        TableColumn<DriverIncomeData.LoadDetail, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().loadNumber));
        loadNumCol.setPrefWidth(100);
        
        TableColumn<DriverIncomeData.LoadDetail, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().customer));
        customerCol.setPrefWidth(150);
        
        TableColumn<DriverIncomeData.LoadDetail, String> pickupCol = new TableColumn<>("Pickup");
        pickupCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().pickupLocation));
        pickupCol.setPrefWidth(150);
        
        TableColumn<DriverIncomeData.LoadDetail, String> dropCol = new TableColumn<>("Drop");
        dropCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().dropLocation));
        dropCol.setPrefWidth(150);
        
        TableColumn<DriverIncomeData.LoadDetail, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().deliveryDate != null ? 
                cell.getValue().deliveryDate.format(DATE_FORMAT) : ""));
        dateCol.setPrefWidth(100);
        
        TableColumn<DriverIncomeData.LoadDetail, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                CURRENCY_FORMAT.format(cell.getValue().grossAmount)));
        amountCol.setPrefWidth(100);
        amountCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        TableColumn<DriverIncomeData.LoadDetail, String> milesCol = new TableColumn<>("Miles");
        milesCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f", cell.getValue().miles)));
        milesCol.setPrefWidth(80);
        milesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        table.getColumns().addAll(loadNumCol, customerCol, pickupCol, dropCol, 
                                 dateCol, amountCol, milesCol);
        
        content.getChildren().addAll(summaryCard, table);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    private VBox createDeductionsContent(DriverIncomeData data) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #f5f5f5;");
        
        // Deductions Detail Card
        VBox deductionsCard = createCard();
        Label deductionsTitle = new Label("Deductions Breakdown");
        deductionsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        deductionsTitle.setTextFill(PRIMARY_COLOR);
        
        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 0, 0, 0));
        
        int row = 0;
        
        // Service Fee
        addDeductionRow(grid, row++, "Service Fee:", data.getServiceFee(), 
            "Company service fee deduction", "💼");
        
        // Fuel Section
        Label fuelSection = new Label("Fuel Costs");
        fuelSection.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        fuelSection.setTextFill(SECONDARY_COLOR);
        GridPane.setColumnSpan(fuelSection, 3);
        grid.add(fuelSection, 0, row++);
        
        addDeductionRow(grid, row++, "  Fuel Cost:", data.getTotalFuelCost(), 
            "Total fuel purchases", "⛽");
        addDeductionRow(grid, row++, "  Fuel Fees:", data.getTotalFuelFees(), 
            "Transaction and service fees", "💳");
        addDeductionRow(grid, row++, "  Total Fuel:", data.getTotalFuelAmount(), 
            "Total fuel expenses", "⛽", true);
        
        // Separator
        Separator sep1 = new Separator();
        sep1.setPadding(new Insets(5, 0, 5, 0));
        GridPane.setColumnSpan(sep1, 3);
        grid.add(sep1, 0, row++);
        
        // Other Deductions
        addDeductionRow(grid, row++, "Recurring Fees:", data.getRecurringFees(), 
            "Weekly recurring charges (ELD, IFTA, etc.)", "🔄");
        addDeductionRow(grid, row++, "Advance Repayments:", data.getAdvanceRepayments(), 
            "Cash advance repayments", "💵");
        addDeductionRow(grid, row++, "Escrow Deposits:", data.getEscrowDeposits(), 
            "Escrow account deposits", "🏦");
        addDeductionRow(grid, row++, "Other Deductions:", data.getOtherDeductions(), 
            "Miscellaneous deductions", "📉");
        
        // Total Deductions
        Separator sep2 = new Separator();
        sep2.setPadding(new Insets(5, 0, 5, 0));
        GridPane.setColumnSpan(sep2, 3);
        grid.add(sep2, 0, row++);
        
        double totalDeductions = data.getTotalDeductions();
        Label totalLabel = new Label("Total Deductions:");
        totalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        Label totalValue = new Label(CURRENCY_FORMAT.format(totalDeductions));
        totalValue.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        totalValue.setTextFill(ERROR_COLOR);
        
        grid.add(totalLabel, 0, row);
        grid.add(totalValue, 1, row);
        
        deductionsCard.getChildren().addAll(deductionsTitle, new Separator(), grid);
        
        // Reimbursements Card
        VBox reimbCard = createCard();
        Label reimbTitle = new Label("Reimbursements & Credits");
        reimbTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        reimbTitle.setTextFill(PRIMARY_COLOR);
        
        GridPane reimbGrid = new GridPane();
        reimbGrid.setHgap(30);
        reimbGrid.setVgap(10);
        reimbGrid.setPadding(new Insets(20, 0, 0, 0));
        
        addDetailRow(reimbGrid, 0, "Total Reimbursements:", 
            CURRENCY_FORMAT.format(data.getReimbursements()), SUCCESS_COLOR, true);
        
        reimbCard.getChildren().addAll(reimbTitle, new Separator(), reimbGrid);
        
        content.getChildren().addAll(deductionsCard, reimbCard);
        
        return content;
    }
    
    private VBox createAnalyticsContent(DriverIncomeData data) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #f5f5f5;");
        
        // Pie Chart Card
        VBox chartCard = createCard();
        Label chartTitle = new Label("Expense Distribution");
        chartTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        chartTitle.setTextFill(PRIMARY_COLOR);
        
        PieChart pieChart = new PieChart();
        pieChart.getData().addAll(
            new PieChart.Data("Service Fee", data.getServiceFee()),
            new PieChart.Data("Fuel", data.getTotalFuelAmount()),
            new PieChart.Data("Recurring Fees", data.getRecurringFees()),
            new PieChart.Data("Advance Repayments", data.getAdvanceRepayments()),
            new PieChart.Data("Escrow", data.getEscrowDeposits()),
            new PieChart.Data("Other", data.getOtherDeductions())
        );
        pieChart.setPrefHeight(300);
        
        chartCard.getChildren().addAll(chartTitle, new Separator(), pieChart);
        
        // Key Ratios Card
        VBox ratiosCard = createCard();
        Label ratiosTitle = new Label("Key Performance Ratios");
        ratiosTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        ratiosTitle.setTextFill(PRIMARY_COLOR);
        
        GridPane ratiosGrid = new GridPane();
        ratiosGrid.setHgap(30);
        ratiosGrid.setVgap(15);
        ratiosGrid.setPadding(new Insets(20, 0, 0, 0));
        
        int row = 0;
        addRatioRow(ratiosGrid, row++, "Operating Margin:", 
            data.getTotalGross() > 0 ? (data.getNetPay() / data.getTotalGross()) * 100 : 0);
        addRatioRow(ratiosGrid, row++, "Fuel Efficiency:", 
            data.getFuelEfficiency() > 0 ? data.getFuelEfficiency() : 0);
        addRatioRow(ratiosGrid, row++, "Cost per Mile:", 
            data.getTotalMiles() > 0 ? data.getTotalDeductions() / data.getTotalMiles() : 0);
        addRatioRow(ratiosGrid, row++, "Revenue per Mile:", 
            data.getTotalMiles() > 0 ? data.getTotalGross() / data.getTotalMiles() : 0);
        
        ratiosCard.getChildren().addAll(ratiosTitle, new Separator(), ratiosGrid);
        
        content.getChildren().addAll(chartCard, ratiosCard);
        
        return content;
    }
    
    private VBox createCard() {
        VBox card = new VBox();
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        
        DropShadow shadow = new DropShadow();
        shadow.setRadius(5.0);
        shadow.setOffsetY(2.0);
        shadow.setColor(Color.color(0.4, 0.4, 0.4, 0.2));
        card.setEffect(shadow);
        
        return card;
    }
    
    private VBox createMetricCard(String title, String value, String emoji, Color color) {
        VBox card = createCard();
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(150);
        
        Label emojiLabel = new Label(emoji);
        emojiLabel.setFont(Font.font(24));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", 12));
        titleLabel.setTextFill(SECONDARY_COLOR);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        valueLabel.setTextFill(color);
        
        card.getChildren().addAll(emojiLabel, titleLabel, valueLabel);
        
        return card;
    }
    
    private HBox createPerformanceIndicator(String label, double value, String suffix) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        
        Label nameLabel = new Label(label + ":");
        nameLabel.setPrefWidth(150);
        nameLabel.setFont(Font.font("Arial", 14));
        
        ProgressBar progressBar = new ProgressBar(value / 100.0);
        progressBar.setPrefWidth(200);
        progressBar.setPrefHeight(20);
        
        // Style based on value
        String barStyle;
        if (value < 30) {
            barStyle = "-fx-accent: #4CAF50;";
        } else if (value < 60) {
            barStyle = "-fx-accent: #FF9800;";
        } else {
            barStyle = "-fx-accent: #F44336;";
        }
        progressBar.setStyle(barStyle);
        
        Label valueLabel = new Label(String.format("%.1f%s", value, suffix));
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        box.getChildren().addAll(nameLabel, progressBar, valueLabel);
        
        return box;
    }
    
    private void addDetailRow(GridPane grid, int row, String label, String value, Color valueColor, boolean bold) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 14));
        
        Label valueNode = new Label(value != null ? value : "N/A");
        valueNode.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 14));
        if (valueColor != null) {
            valueNode.setTextFill(valueColor);
        }
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
    
    private void addDeductionRow(GridPane grid, int row, String label, double value, 
                                String tooltip, String emoji) {
        addDeductionRow(grid, row, label, value, tooltip, emoji, false);
    }
    
    private void addDeductionRow(GridPane grid, int row, String label, double value, 
                                String tooltip, String emoji, boolean isTotal) {
        Label emojiLabel = new Label(emoji);
        emojiLabel.setFont(Font.font(14));
        
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", isTotal ? FontWeight.BOLD : FontWeight.NORMAL, 14));
        if (tooltip != null) {
            labelNode.setTooltip(new Tooltip(tooltip));
        }
        
        Label valueNode = new Label(CURRENCY_FORMAT.format(value));
        valueNode.setFont(Font.font("Arial", isTotal ? FontWeight.BOLD : FontWeight.NORMAL, 14));
        if (value > 0) {
            valueNode.setTextFill(ERROR_COLOR);
        } else {
            valueNode.setTextFill(SECONDARY_COLOR);
        }
        
        grid.add(emojiLabel, 0, row);
        grid.add(labelNode, 1, row);
        grid.add(valueNode, 2, row);
    }
    
    private void addRatioRow(GridPane grid, int row, String label, double value) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        String displayValue;
        Color valueColor;
        
        if (label.contains("Margin")) {
            displayValue = String.format("%.1f%%", value);
            valueColor = value > 20 ? SUCCESS_COLOR : (value > 10 ? WARNING_COLOR : ERROR_COLOR);
        } else if (label.contains("Efficiency")) {
            displayValue = String.format("%.2f MPG", value);
            valueColor = value > 6 ? SUCCESS_COLOR : (value > 4 ? WARNING_COLOR : ERROR_COLOR);
        } else if (label.contains("Cost")) {
            displayValue = String.format("$%.3f", value);
            valueColor = value < 1.5 ? SUCCESS_COLOR : (value < 2.0 ? WARNING_COLOR : ERROR_COLOR);
        } else {
            displayValue = String.format("$%.3f", value);
            valueColor = value > 2.0 ? SUCCESS_COLOR : (value > 1.5 ? WARNING_COLOR : ERROR_COLOR);
        }
        
        Label valueNode = new Label(displayValue);
        valueNode.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        valueNode.setTextFill(valueColor);
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
}