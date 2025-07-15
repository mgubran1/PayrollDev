package com.company.payroll.payroll;

import com.company.payroll.loads.Load;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import java.time.format.DateTimeFormatter;

public class PayrollLoadsTable extends TableView<Load> {
    public PayrollLoadsTable(ObservableList<Load> loadRows) {
        super(loadRows);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPrefHeight(300);
        setStyle("-fx-background-color: #fff; -fx-font-size: 15px;");
        setPlaceholder(new Label("No content in table."));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        TableColumn<Load, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLoadNumber()));
        TableColumn<Load, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCustomer()));
        TableColumn<Load, String> pickUpCol = new TableColumn<>("Pick Up Location");
        pickUpCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPickUpLocation()));
        TableColumn<Load, String> dropCol = new TableColumn<>("Drop Location");
        dropCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDropLocation()));
        TableColumn<Load, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getDriver() != null ? cell.getValue().getDriver().getName() : ""));
        
        // Add trailer column
        TableColumn<Load, String> trailerCol = new TableColumn<>("Trailer");
        trailerCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getTrailerNumber() != null ? cell.getValue().getTrailerNumber() : ""));
        
        TableColumn<Load, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getStatus() != null ? cell.getValue().getStatus().name() : ""));
        TableColumn<Load, Number> grossCol = new TableColumn<>("Gross Amount");
        grossCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getGrossAmount()));
        TableColumn<Load, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNotes()));
        TableColumn<Load, String> deliveryDateCol = new TableColumn<>("Delivery Date");
        deliveryDateCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getDeliveryDate() != null ? cell.getValue().getDeliveryDate().format(dtf) : ""));
        getColumns().setAll(java.util.List.of(loadNumCol, customerCol, pickUpCol, dropCol,
                driverCol, trailerCol, statusCol, grossCol, notesCol, deliveryDateCol));
    }
}