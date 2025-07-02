package com.company.payroll.dispatcher;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Dialog for batch updating driver statuses.
 */
public class UpdateStatusDialog extends Dialog<Void> {
    public UpdateStatusDialog(DispatcherController controller) {
        setTitle("Update Status");

        ObservableList<DispatcherDriverStatus> drivers = controller.getDriverStatuses();
        ListView<DispatcherDriverStatus> driverList = new ListView<>(drivers);
        driverList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        driverList.setPrefHeight(150);

        Label currentStatusLabel = new Label();
        driverList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && driverList.getSelectionModel().getSelectedItems().size() == 1) {
                currentStatusLabel.setText(n.getStatus().getDisplayName());
            } else {
                currentStatusLabel.setText("");
            }
        });

        ComboBox<DispatcherDriverStatus.Status> statusBox = new ComboBox<>();
        statusBox.getItems().setAll(DispatcherDriverStatus.Status.values());

        TextField locationField = new TextField();
        DatePicker etaDate = new DatePicker();
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 8);
        hourSpinner.setPrefWidth(60);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 0);
        minuteSpinner.setPrefWidth(60);
        HBox etaBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);

        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        int row = 0;
        grid.add(new Label("Drivers:"), 0, row);
        grid.add(driverList, 1, row++);
        grid.add(new Label("Current Status:"), 0, row);
        grid.add(currentStatusLabel, 1, row++);
        grid.add(new Label("New Status:"), 0, row);
        grid.add(statusBox, 1, row++);
        grid.add(new Label("Location:"), 0, row);
        grid.add(locationField, 1, row++);
        grid.add(new Label("ETA:"), 0, row);
        grid.add(new VBox(etaDate, etaBox), 1, row++);
        grid.add(new Label("Notes:"), 0, row);
        grid.add(notesArea, 1, row);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okBtn = getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(statusBox.valueProperty().isNull().or(driverList.getSelectionModel().selectedItemProperty().isNull()));

        setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                DispatcherDriverStatus.Status newStatus = statusBox.getValue();
                LocalDate date = etaDate.getValue();
                LocalTime time = LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue());
                LocalDateTime eta = date != null ? LocalDateTime.of(date, time) : null;
                for (DispatcherDriverStatus d : driverList.getSelectionModel().getSelectedItems()) {
                    controller.updateDriverStatus(d, newStatus, locationField.getText(), eta, notesArea.getText());
                }
            }
            return null;
        });
    }
}
