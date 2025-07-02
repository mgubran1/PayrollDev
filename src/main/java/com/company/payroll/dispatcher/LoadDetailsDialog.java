package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Simple dialog showing load details.
 */
public class LoadDetailsDialog extends Dialog<Void> {
    public LoadDetailsDialog(Load load) {
        setTitle("Load Details");
        String text = load != null ? "Load #" + load.getLoadNumber() : "No load";
        getDialogPane().setContent(new VBox(new Label(text)));
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
    }
}
