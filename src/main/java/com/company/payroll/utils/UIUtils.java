package com.company.payroll.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/** Simple UI helper utilities. */
public class UIUtils {
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
