package mgubran1.PayrollDev.trailers;

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
 * TrailersTab provides basic trailer management.
 */
public class TrailersTab extends BorderPane {
    private final ObservableList<Trailer> trailers = FXCollections.observableArrayList();
    private final TrailerDAO dao = new TrailerDAO();

    public TrailersTab() {
        trailers.setAll(dao.getAll());

        TableView<Trailer> table = new TableView<>(trailers);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Trailer, String> numCol = new TableColumn<>("Trailer #");
        numCol.setCellValueFactory(new PropertyValueFactory<>("trailerNumber"));
        TableColumn<Trailer, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        TableColumn<Trailer, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> javafx.beans.property.SimpleStringProperty.stringExpression(c.getValue().getStatus().name()));

        table.getColumns().addAll(numCol, typeCol, statusCol);

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");

        addBtn.setOnAction(e -> showDialog(null));
        editBtn.setOnAction(e -> {
            Trailer sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showDialog(sel);
        });
        deleteBtn.setOnAction(e -> {
            Trailer sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                dao.delete(sel.getId());
                trailers.setAll(dao.getAll());
            }
        });
        refreshBtn.setOnAction(e -> trailers.setAll(dao.getAll()));

        HBox buttons = new HBox(10, addBtn, editBtn, deleteBtn, refreshBtn);
        buttons.setPadding(new Insets(10));
        buttons.setAlignment(Pos.CENTER_LEFT);

        setCenter(table);
        setBottom(buttons);
    }

    private void showDialog(Trailer trailer) {
        Dialog<Trailer> dialog = new Dialog<>();
        dialog.setTitle(trailer == null ? "Add Trailer" : "Edit Trailer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField numField = new TextField(trailer == null ? "" : trailer.getTrailerNumber());
        TextField typeField = new TextField(trailer == null ? "" : trailer.getType());
        ComboBox<Trailer.Status> statusBox = new ComboBox<>(FXCollections.observableArrayList(Trailer.Status.values()));
        statusBox.setValue(trailer == null ? Trailer.Status.AVAILABLE : trailer.getStatus());

        VBox form = new VBox(8,
            new HBox(8, new Label("Number"), numField),
            new HBox(8, new Label("Type"), typeField),
            new HBox(8, new Label("Status"), statusBox)
        );
        form.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Trailer result = new Trailer(
                    trailer == null ? 0 : trailer.getId(),
                    numField.getText(),
                    typeField.getText(),
                    statusBox.getValue(),
                    trailer == null ? "" : trailer.getNotes()
                );
                return result;
            }
            return null;
        });

        Optional<Trailer> res = dialog.showAndWait();
        res.ifPresent(t -> {
            if (trailer == null) {
                dao.add(t);
            } else {
                t.setId(trailer.getId());
                dao.update(t);
            }
            trailers.setAll(dao.getAll());
        });
    }
}
