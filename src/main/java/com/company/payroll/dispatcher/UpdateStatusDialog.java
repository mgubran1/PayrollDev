package com.company.payroll.dispatcher;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Placeholder dialog for updating dispatcher status entries.
 */
public class UpdateStatusDialog extends Dialog<Void> {
    public UpdateStatusDialog(DispatcherController controller) {
        setTitle("Update Status");
        getDialogPane().setContent(new VBox(new Label("Update load or driver status")));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK);
    }
}
