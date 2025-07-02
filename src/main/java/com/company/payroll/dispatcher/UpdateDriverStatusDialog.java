package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Dialog for updating a single driver's status.
 */
public class UpdateDriverStatusDialog extends Dialog<Boolean> {
    public UpdateDriverStatusDialog(DispatcherDriverStatus driver, DispatcherController controller) {
        setTitle("Update Driver Status");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.setStyle("-fx-font-weight: bold;");

        ToggleGroup statusGroup = new ToggleGroup();
        VBox statusBox = new VBox(5);
        for (DispatcherDriverStatus.Status s : DispatcherDriverStatus.Status.values()) {
            RadioButton rb = new RadioButton(s.getDisplayName());
            rb.setUserData(s);
            rb.setToggleGroup(statusGroup);
            if (s == driver.getStatus()) {
                rb.setSelected(true);
            }
            statusBox.getChildren().add(rb);
        }

        TextField locationField = new TextField(driver.getCurrentLocation());

        DatePicker etaDate = new DatePicker();
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 8);
        hourSpinner.setPrefWidth(60);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 0);
        minuteSpinner.setPrefWidth(60);
        HBox etaBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);

        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);

        int row = 0;
        grid.add(new Label("Driver:"), 0, row);
        grid.add(nameLabel, 1, row++);
        grid.add(new Label("Status:"), 0, row);
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
        okBtn.disableProperty().bind(statusGroup.selectedToggleProperty().isNull());

        setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                DispatcherDriverStatus.Status newStatus = (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData();
                LocalDate date = etaDate.getValue();
                LocalTime time = LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue());
                LocalDateTime eta = date != null ? LocalDateTime.of(date, time) : null;
                controller.updateDriverStatus(driver, newStatus, locationField.getText(), eta, notesArea.getText());
                return true;
            }
            return false;
        });
    }
}
