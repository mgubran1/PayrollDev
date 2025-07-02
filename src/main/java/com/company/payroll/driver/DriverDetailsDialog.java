package com.company.payroll.driver;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.stage.Modality;
import javafx.collections.FXCollections;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.text.NumberFormat; 
import java.time.format.DateTimeFormatter;

/**
 * Dialog for showing detailed driver income information
 */
public class DriverDetailsDialog extends Dialog<Void> {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    public DriverDetailsDialog(DriverIncomeData data) {
        setTitle("Driver Income Details - " + data.getDriverName());
        setHeaderText(null);
        initModality(Modality.APPLICATION_MODAL);
        
        TabPane tabPane = new TabPane();
        
        // Summary Tab
        Tab summaryTab = new Tab("Summary");
        summaryTab.setContent(createSummaryContent(data));
        summaryTab.setClosable(false);
        
        // Loads Tab
        Tab loadsTab = new Tab("Loads");
        loadsTab.setContent(createLoadsContent(data));
        loadsTab.setClosable(false);
        
        // Deductions Tab
        Tab deductionsTab = new Tab("Deductions");
        deductionsTab.setContent(createDeductionsContent(data));
        deductionsTab.setClosable(false);
        
        tabPane.getTabs().addAll(summaryTab, loadsTab, deductionsTab);
        
        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefSize(800, 600);
    }
    
    private VBox createSummaryContent(DriverIncomeData data) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Header
        Label header = new Label(data.getDriverName());
        header.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        
        Label subHeader = new Label("Period: " + 
            data.getStartDate().format(DATE_FORMAT) + " - " + 
            data.getEndDate().format(DATE_FORMAT));
        subHeader.setFont(Font.font("Arial", 14));
        
        // Summary Grid
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 0, 0, 0));
        
        int row = 0;
        addSummaryRow(grid, row++, "Truck/Unit:", data.getTruckUnit());
        addSummaryRow(grid, row++, "Total Loads:", String.valueOf(data.getTotalLoads()));
        addSummaryRow(grid, row++, "Total Miles:", String.format("%.1f", data.getTotalMiles()));
        addSummaryRow(grid, row++, "Total Gross:", CURRENCY_FORMAT.format(data.getTotalGross()));
        addSummaryRow(grid, row++, "Total Fuel Cost:", CURRENCY_FORMAT.format(data.getTotalFuelAmount()));
        addSummaryRow(grid, row++, "Average per Mile:", String.format("$%.3f", data.getAveragePerMile()));
        addSummaryRow(grid, row++, "Fuel Efficiency:", 
            data.getFuelEfficiency() > 0 ? String.format("%.2f MPG", data.getFuelEfficiency()) : "N/A");
        
        Separator separator = new Separator();
        separator.setPadding(new Insets(10, 0, 10, 0));
        
        Label netPayLabel = new Label("Net Pay: " + CURRENCY_FORMAT.format(data.getNetPay()));
        netPayLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        content.getChildren().addAll(header, subHeader, grid, separator, netPayLabel);
        
        return content;
    }
    
    private ScrollPane createLoadsContent(DriverIncomeData data) {
        TableView<DriverIncomeData.LoadDetail> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(data.getLoads()));
        
        TableColumn<DriverIncomeData.LoadDetail, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().loadNumber));
        
        TableColumn<DriverIncomeData.LoadDetail, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().customer));
        
        TableColumn<DriverIncomeData.LoadDetail, String> pickupCol = new TableColumn<>("Pickup");
        pickupCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().pickupLocation));
        
        TableColumn<DriverIncomeData.LoadDetail, String> dropCol = new TableColumn<>("Drop");
        dropCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(cell.getValue().dropLocation));
        
        TableColumn<DriverIncomeData.LoadDetail, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().deliveryDate != null ? 
                cell.getValue().deliveryDate.format(DATE_FORMAT) : ""));
        
        TableColumn<DriverIncomeData.LoadDetail, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                CURRENCY_FORMAT.format(cell.getValue().grossAmount)));
        
        TableColumn<DriverIncomeData.LoadDetail, String> milesCol = new TableColumn<>("Miles");
        milesCol.setCellValueFactory(cell -> 
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f", cell.getValue().miles)));
        
        table.getColumns().addAll(loadNumCol, customerCol, pickupCol, dropCol, 
                                 dateCol, amountCol, milesCol);
        
        ScrollPane scrollPane = new ScrollPane(table);
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    private VBox createDeductionsContent(DriverIncomeData data) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        
        int row = 0;
        addDeductionRow(grid, row++, "Service Fee:", data.getServiceFee());
        addDeductionRow(grid, row++, "Fuel Cost:", data.getTotalFuelCost());
        addDeductionRow(grid, row++, "Fuel Fees:", data.getTotalFuelFees());
        addDeductionRow(grid, row++, "Recurring Fees:", data.getRecurringFees());
        addDeductionRow(grid, row++, "Advance Repayments:", data.getAdvanceRepayments());
        addDeductionRow(grid, row++, "Escrow Deposits:", data.getEscrowDeposits());
        addDeductionRow(grid, row++, "Other Deductions:", data.getOtherDeductions());
        
        Separator separator = new Separator();
        separator.setPadding(new Insets(10, 0, 10, 0));
        
        double totalDeductions = data.getServiceFee() + data.getTotalFuelAmount() + 
            data.getRecurringFees() + data.getAdvanceRepayments() + 
            data.getEscrowDeposits() + data.getOtherDeductions();
        
        Label totalLabel = new Label("Total Deductions: " + CURRENCY_FORMAT.format(totalDeductions));
        totalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        Label reimbursementsLabel = new Label("Reimbursements: " + CURRENCY_FORMAT.format(data.getReimbursements()));
        reimbursementsLabel.setFont(Font.font("Arial", 14));
        
        content.getChildren().addAll(grid, separator, totalLabel, reimbursementsLabel);
        
        return content;
    }
    
    private void addSummaryRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        Label valueNode = new Label(value != null ? value : "N/A");
        valueNode.setFont(Font.font("Arial", 12));
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
    
    private void addDeductionRow(GridPane grid, int row, String label, double value) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        Label valueNode = new Label(CURRENCY_FORMAT.format(value));
        valueNode.setFont(Font.font("Arial", 12));
        if (value > 0) {
            valueNode.setStyle("-fx-text-fill: #e74c3c;"); // Red for deductions
        }
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
}