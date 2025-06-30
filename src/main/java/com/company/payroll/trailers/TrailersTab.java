package com.company.payroll.trailers;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simplified trailers tab with fleet overview and assignments.
 */
public class TrailersTab extends Tab {
    private final TrailerDAO trailerDAO = new TrailerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    private final TableView<Trailer> trailersTable = new TableView<>();
    private final TableView<TrailerAssignment> assignmentsTable = new TableView<>();

    public TrailersTab() {
        setText("Trailers");
        setClosable(false);
        TabPane panes = new TabPane();
        panes.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        panes.getTabs().add(createFleetTab());
        panes.getTabs().add(createAssignmentsTab());
        setContent(panes);
        loadData();
    }

    private Tab createFleetTab() {
        Tab t = new Tab("Fleet Overview");
        trailersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Trailer, String> numCol = new TableColumn<>("Trailer #");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTrailerNumber()));

        TableColumn<Trailer, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType()));

        TableColumn<Trailer, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));

        TableColumn<Trailer, String> regCol = new TableColumn<>("Registration Expiry");
        regCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRegistrationExpiryDate() != null ?
                        c.getValue().getRegistrationExpiryDate().toString() : ""));

        TableColumn<Trailer, String> inspCol = new TableColumn<>("Inspection Expiry");
        inspCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNextInspectionDueDate() != null ?
                        c.getValue().getNextInspectionDueDate().toString() : ""));

        TableColumn<Trailer, String> insCol = new TableColumn<>("Insurance Expiry");
        insCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getInsuranceExpiryDate() != null ?
                        c.getValue().getInsuranceExpiryDate().toString() : ""));

        TableColumn<Trailer, String> vinCol = new TableColumn<>("VIN#");
        vinCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVin()));

        trailersTable.getColumns().addAll(numCol, typeCol, statusCol, regCol, inspCol, insCol, vinCol);
        t.setContent(new BorderPane(trailersTable));
        return t;
    }

    private Tab createAssignmentsTab() {
        Tab t = new Tab("Assignments");
        assignmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<TrailerAssignment, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> c.getValue().assignmentIdProperty());

        TableColumn<TrailerAssignment, String> trailerCol = new TableColumn<>("Trailer");
        trailerCol.setCellValueFactory(c -> c.getValue().trailerNumberProperty());

        TableColumn<TrailerAssignment, String> truckCol = new TableColumn<>("Truck/Unit");
        truckCol.setCellValueFactory(c -> c.getValue().truckUnitProperty());

        TableColumn<TrailerAssignment, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(c -> c.getValue().driverNameProperty());

        TableColumn<TrailerAssignment, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartDate() != null ? c.getValue().getStartDate().toString() : ""));

        TableColumn<TrailerAssignment, String> endCol = new TableColumn<>("End");
        endCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndDate() != null ? c.getValue().getEndDate().toString() : ""));

        assignmentsTable.getColumns().addAll(idCol, trailerCol, truckCol, driverCol, startCol, endCol);
        t.setContent(new VBox(assignmentsTable));
        return t;
    }

    private void loadData() {
        List<Trailer> trailers = trailerDAO.findAll();
        ObservableList<Trailer> trailerItems = FXCollections.observableArrayList(trailers);
        trailersTable.setItems(trailerItems);

        // empty assignments list, to be filled with real data elsewhere
        assignmentsTable.setItems(FXCollections.observableArrayList());

        // Map assignments from employees if truck_unit or trailer_number set
        Map<String, String> driverMap = employeeDAO.getAll().stream()
                .filter(e -> e.getTrailerNumber() != null)
                .collect(Collectors.toMap(Employee::getTrailerNumber, Employee::getName, (a,b)->a));

        trailerItems.forEach(t -> {
            if (driverMap.containsKey(t.getTrailerNumber())) {
                TrailerAssignment a = new TrailerAssignment();
                a.setAssignmentId("AUTO-" + t.getTrailerNumber());
                a.setTrailerNumber(t.getTrailerNumber());
                a.setDriverName(driverMap.get(t.getTrailerNumber()));
                assignmentsTable.getItems().add(a);
            }
        });
    }
}
