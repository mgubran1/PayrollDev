package com.company.payroll.dispatcher;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Simple dialog placeholder for updating a driver's status.
 */
public class UpdateDriverStatusDialog extends Dialog<Boolean> {
    public UpdateDriverStatusDialog(DispatcherDriverStatus status, DispatcherController controller) {
        setTitle("Update Driver Status");
        getDialogPane().setContent(new VBox(new Label("Update status for " + status.getDriverName())));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(btn -> btn == ButtonType.OK);
    }
}
