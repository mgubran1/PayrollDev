package com.company.payroll.trucks;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simplified Trucks tab showing only a fleet overview table.
 */
public class TrucksTab extends Tab {
    private final TruckDAO truckDAO = new TruckDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TableView<Truck> table = new TableView<>();

    public TrucksTab() {
        setText("Trucks");
        setClosable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Truck, String> unitCol = new TableColumn<>("Truck/Unit");
        unitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumber()));

        TableColumn<Truck, String> typeCol = new TableColumn<>("Type/Model");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getModel() != null ? c.getValue().getModel() : c.getValue().getType()));

        TableColumn<Truck, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));

        TableColumn<Truck, String> driverCol = new TableColumn<>("Driver Name");
        driverCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDriver()));

        TableColumn<Truck, String> regCol = new TableColumn<>("Registration Expiry");
        regCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRegistrationExpiryDate() != null ?
                        c.getValue().getRegistrationExpiryDate().toString() : ""));

        TableColumn<Truck, String> irpCol = new TableColumn<>("IRP");
        irpCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPermitNumbers()));

        TableColumn<Truck, String> inspCol = new TableColumn<>("Inspection Expiry");
        inspCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNextInspectionDue() != null ?
                        c.getValue().getNextInspectionDue().toString() : ""));

        TableColumn<Truck, String> insCol = new TableColumn<>("Insurance Expiry");
        insCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getInsuranceExpiryDate() != null ?
                        c.getValue().getInsuranceExpiryDate().toString() : ""));

        TableColumn<Truck, String> vinCol = new TableColumn<>("VIN#");
        vinCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVin()));

        table.getColumns().addAll(unitCol, typeCol, statusCol, driverCol, regCol,
                irpCol, inspCol, insCol, vinCol);

        BorderPane pane = new BorderPane(table);
        setContent(pane);
        loadData();
    }

    private void loadData() {
        List<Truck> trucks = truckDAO.findAll();
        Map<String, String> driverMap = employeeDAO.getAll().stream()
                .filter(e -> e.getTruckUnit() != null)
                .collect(Collectors.toMap(Employee::getTruckUnit, Employee::getName, (a, b) -> a));

        trucks.forEach(t -> {
            if (driverMap.containsKey(t.getNumber())) {
                t.setDriver(driverMap.get(t.getNumber()));
            }
        });
        ObservableList<Truck> items = FXCollections.observableArrayList(trucks);
        table.setItems(items);
    }
}
