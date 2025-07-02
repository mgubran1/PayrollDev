package com.company.payroll.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal system monitor placeholder.
 */
public class SystemMonitor {
    public static class Alert {
        private final String message;
        private final boolean critical;
        public Alert(String message, boolean critical) {
            this.message = message;
            this.critical = critical;
        }
        public String getMessage() { return message; }
        public boolean isCritical() { return critical; }
    }

    private final List<Consumer<Alert>> listeners = new ArrayList<>();

    public void startMonitoring() {
        // no-op
    }

    public void addAlertListener(Consumer<Alert> listener) {
        listeners.add(listener);
    }
}
