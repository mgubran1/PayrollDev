package com.company.payroll.util;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.layout.Region;

/**
 * Interface for views that need to respond to window size or state changes.
 */
public interface WindowAware {
    /**
     * Called when the application window is resized.
     *
     * @param width  the new window width
     * @param height the new window height
     */
    default void updateWindowSize(double width, double height) {
        // Attempt to update preferred size on common JavaFX containers
        if (this instanceof Region) {
            Region r = (Region) this;
            r.setPrefWidth(width);
            r.setPrefHeight(height);
        } else if (this instanceof Tab) {
            Node content = ((Tab) this).getContent();
            if (content instanceof Region) {
                ((Region) content).setPrefWidth(width);
                ((Region) content).setPrefHeight(height);
            }
        }
    }

    /**
     * Called when the application window is maximized, restored or minimized.
     *
     * @param maximized true if the window is maximized
     * @param minimized true if the window is minimized/iconified
     */
    default void onWindowStateChanged(boolean maximized, boolean minimized) {
        // Default implementation does nothing
    }
}
