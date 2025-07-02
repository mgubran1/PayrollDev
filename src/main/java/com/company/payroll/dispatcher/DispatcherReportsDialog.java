package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/**
 * Dialog for generating simple driver reports.
 */
public class DispatcherReportsDialog extends Dialog<Void> {
    public DispatcherReportsDialog(DispatcherController controller) {
        setTitle("Dispatcher Reports");

        ComboBox<DispatcherDriverStatus> driverBox = new ComboBox<>(controller.getDriverStatuses());
        driverBox.setPrefWidth(200);
        DatePicker startDate = new DatePicker(LocalDate.now().minusWeeks(1));
        DatePicker endDate = new DatePicker(LocalDate.now());
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Summary", "Detailed", "Load List");
        typeBox.setValue("Summary");

        TextArea reportArea = new TextArea();
        reportArea.setPrefRowCount(15);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Driver:"), 0, 0);
        grid.add(driverBox, 1, 0);
        grid.add(new Label("Start:"), 0, 1);
        grid.add(startDate, 1, 1);
        grid.add(new Label("End:"), 0, 2);
        grid.add(endDate, 1, 2);
        grid.add(new Label("Type:"), 0, 3);
        grid.add(typeBox, 1, 3);

        VBox root = new VBox(10, grid, reportArea);
        root.setPadding(new Insets(10));
        getDialogPane().setContent(root);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okBtn = getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(driverBox.valueProperty().isNull());
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            DispatcherDriverStatus status = driverBox.getValue();
            List<com.company.payroll.loads.Load> loads = controller.getLoadsForDriverAndRange(
                    status.getDriver(), startDate.getValue(), endDate.getValue());
            StringBuilder sb = new StringBuilder();
            sb.append("Driver: ").append(status.getDriverName()).append("\n");
            sb.append("Report from ").append(startDate.getValue()).append(" to ")
              .append(endDate.getValue()).append("\n\n");
            sb.append("Total Loads: ").append(loads.size()).append("\n");
            for (com.company.payroll.loads.Load l : loads) {
                sb.append(l.getLoadNumber()).append(" - ").append(l.getCustomer()).append("\n");
            }
            reportArea.setText(sb.toString());
            e.consume();
        });
    }
}
