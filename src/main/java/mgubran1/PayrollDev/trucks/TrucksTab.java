package mgubran1.PayrollDev.trucks;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * TrucksTab provides basic fleet management for the application.
 */
public class TrucksTab extends BorderPane {
    private final ObservableList<Truck> trucks = FXCollections.observableArrayList();
    private final TruckDAO dao = new TruckDAO();

    public TrucksTab() {
        trucks.setAll(dao.getAll());

        TableView<Truck> table = new TableView<>(trucks);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Truck, String> unitCol = new TableColumn<>("Unit #");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unitNumber"));
        TableColumn<Truck, String> makeCol = new TableColumn<>("Make");
        makeCol.setCellValueFactory(new PropertyValueFactory<>("make"));
        TableColumn<Truck, String> modelCol = new TableColumn<>("Model");
        modelCol.setCellValueFactory(new PropertyValueFactory<>("model"));
        TableColumn<Truck, Integer> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(new PropertyValueFactory<>("year"));
        TableColumn<Truck, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(c.getValue().getStatus().name()));

        table.getColumns().addAll(unitCol, makeCol, modelCol, yearCol, statusCol);

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");

        addBtn.setOnAction(e -> showDialog(null));
        editBtn.setOnAction(e -> {
            Truck selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showDialog(selected);
            }
        });
        deleteBtn.setOnAction(e -> {
            Truck selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                dao.delete(selected.getId());
                trucks.setAll(dao.getAll());
            }
        });
        refreshBtn.setOnAction(e -> trucks.setAll(dao.getAll()));

        HBox buttons = new HBox(10, addBtn, editBtn, deleteBtn, refreshBtn);
        buttons.setPadding(new Insets(10));
        buttons.setAlignment(Pos.CENTER_LEFT);

        setCenter(table);
        setBottom(buttons);
    }

    private void showDialog(Truck truck) {
        Dialog<Truck> dialog = new Dialog<>();
        dialog.setTitle(truck == null ? "Add Truck" : "Edit Truck");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField unitField = new TextField(truck == null ? "" : truck.getUnitNumber());
        TextField makeField = new TextField(truck == null ? "" : truck.getMake());
        TextField modelField = new TextField(truck == null ? "" : truck.getModel());
        TextField yearField = new TextField(truck == null ? "" : String.valueOf(truck.getYear()));
        ComboBox<Truck.Status> statusBox = new ComboBox<>(FXCollections.observableArrayList(Truck.Status.values()));
        statusBox.setValue(truck == null ? Truck.Status.ACTIVE : truck.getStatus());

        VBox form = new VBox(8,
            new HBox(8, new Label("Unit"), unitField),
            new HBox(8, new Label("Make"), makeField),
            new HBox(8, new Label("Model"), modelField),
            new HBox(8, new Label("Year"), yearField),
            new HBox(8, new Label("Status"), statusBox)
        );
        form.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                int year = yearField.getText().isEmpty() ? 0 : Integer.parseInt(yearField.getText());
                Truck result = new Truck(
                    truck == null ? 0 : truck.getId(),
                    unitField.getText(), makeField.getText(), modelField.getText(), year,
                    truck == null ? "" : truck.getVin(),
                    truck == null ? "" : truck.getLicensePlate(),
                    truck == null ? 0.0 : truck.getMileage(),
                    statusBox.getValue(),
                    truck == null ? "" : truck.getNotes()
                );
                return result;
            }
            return null;
        });

        Optional<Truck> result = dialog.showAndWait();
        result.ifPresent(t -> {
            if (truck == null) {
                dao.add(t);
            } else {
                t.setId(truck.getId());
                dao.update(t);
            }
            trucks.setAll(dao.getAll());
        });
    }
}
