package com.company.payroll.revenue;

import com.company.payroll.expenses.CompanyExpensesTab;
import com.company.payroll.maintenance.MaintenanceTab;
import com.company.payroll.payroll.PayrollTab;
import com.company.payroll.payroll.PayrollSummaryTable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

public class RevenueTab extends BorderPane {
    private final PayrollTab payrollTab;
    private final MaintenanceTab maintenanceTab;
    private final CompanyExpensesTab expensesTab;

    private final Label payrollGrossLbl = new Label();
    private final Label maintenanceLbl = new Label();
    private final Label expensesLbl = new Label();
    private final Label netLbl = new Label();

    public RevenueTab(PayrollTab payrollTab, MaintenanceTab maintenanceTab, CompanyExpensesTab expensesTab){
        this.payrollTab = payrollTab;
        this.maintenanceTab = maintenanceTab;
        this.expensesTab = expensesTab;
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(25));
        int r=0;
        grid.add(new Label("Payroll Gross:"),0,r); grid.add(payrollGrossLbl,1,r++);
        grid.add(new Label("Maintenance Cost:"),0,r); grid.add(maintenanceLbl,1,r++);
        grid.add(new Label("Company Expenses:"),0,r); grid.add(expensesLbl,1,r++);
        grid.add(new Label("Net Revenue:"),0,r); grid.add(netLbl,1,r++);
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e->refresh());
        grid.add(refreshBtn,0,r);
        setCenter(grid);
        refresh();
    }

    private void refresh(){
        PayrollSummaryTable.PayrollSummaryStats stats = payrollTab.getSummaryStats();
        double payrollGross = stats.totalGross;
        double maintenanceCost = maintenanceTab.getCurrentRecords().stream().mapToDouble(r->r.getCost()).sum();
        double expensesCost = expensesTab.getCurrentExpenses().stream().mapToDouble(c->c.getAmount()).sum();
        double net = payrollGross - maintenanceCost - expensesCost;
        payrollGrossLbl.setText(String.format("$%.2f", payrollGross));
        maintenanceLbl.setText(String.format("$%.2f", maintenanceCost));
        expensesLbl.setText(String.format("$%.2f", expensesCost));
        netLbl.setText(String.format("$%.2f", net));
    }
}
