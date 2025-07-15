package com.company.payroll.payroll;

import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

/**
 * Simple dialog that shows the progress of a background task.
 */
public class ProgressDialog<T> extends Dialog<T> {
    private final ProgressBar progressBar;
    private final Label messageLabel;

    public ProgressDialog(Task<T> task) {
        setTitle("Processing");
        setHeaderText("Please wait...");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.progressProperty().bind(task.progressProperty());

        messageLabel = new Label();
        messageLabel.textProperty().bind(task.messageProperty());

        content.getChildren().addAll(messageLabel, progressBar);
        getDialogPane().setContent(content);

        task.stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED ||
                newState == Worker.State.FAILED ||
                newState == Worker.State.CANCELLED) {
                close();
            }
        });

        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node cancelButton = getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.disableProperty().bind(task.runningProperty().not());

        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.CANCEL) {
                task.cancel();
            }
            return null;
        });
    }
}
