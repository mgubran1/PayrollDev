package com.company.payroll.payroll;

import com.company.payroll.fuel.FuelTransaction;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.*;

public class PayrollFuelTable extends TableView<FuelTransaction> {
    public PayrollFuelTable(ObservableList<FuelTransaction> fuelRows) {
        super(fuelRows);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPrefHeight(300);
        setStyle("-fx-background-color: #fff; -fx-font-size: 15px;");
        setPlaceholder(new Label("No content in table."));

        TableColumn<FuelTransaction, String> dateCol = new TableColumn<>("Tran Date");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTranDate()));
        TableColumn<FuelTransaction, String> invCol = new TableColumn<>("Invoice");
        invCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getInvoice()));
        TableColumn<FuelTransaction, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUnit()));
        TableColumn<FuelTransaction, String> driverCol = new TableColumn<>("Driver Name");
        driverCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDriverName()));
        TableColumn<FuelTransaction, String> locCol = new TableColumn<>("Location Name");
        locCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLocationName()));
        TableColumn<FuelTransaction, Number> amtCol = new TableColumn<>("Amt");
        amtCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getAmt()));
        TableColumn<FuelTransaction, Number> feesCol = new TableColumn<>("Fees");
        feesCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getFees()));
        TableColumn<FuelTransaction, Number> totalCol = new TableColumn<>("Amt+Fees");
        totalCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getAmt() + cell.getValue().getFees()));
        TableColumn<FuelTransaction, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getItem()));
        TableColumn<FuelTransaction, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getQty()));
        getColumns().addAll(dateCol, invCol, unitCol, driverCol, locCol, amtCol, feesCol, totalCol, itemCol, qtyCol);
    }
}