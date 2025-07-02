package com.company.payroll.dispatcher;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Placeholder dialog for fleet tracking view.
 */
public class FleetTrackingDialog extends Dialog<Void> {
    public FleetTrackingDialog(DispatcherController controller) {
        setTitle("Fleet Tracking");
        getDialogPane().setContent(new VBox(new Label("Fleet tracking view")));
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
    }
}
