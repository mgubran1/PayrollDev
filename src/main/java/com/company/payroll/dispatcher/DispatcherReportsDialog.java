package com.company.payroll.dispatcher;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Placeholder dialog for dispatcher reports.
 */
public class DispatcherReportsDialog extends Dialog<Void> {
    public DispatcherReportsDialog(DispatcherController controller) {
        setTitle("Dispatcher Reports");
        getDialogPane().setContent(new VBox(new Label("Reports not implemented")));
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
    }
}
