package com.company.payroll.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Simple notification hub used for demo purposes. */
public class NotificationCenter {
    public enum NotificationType { INFO, WARNING, ERROR }

    public static class Notification {
        private final String title;
        private final String message;
        private final NotificationType type;
        public Notification(String title, String message, NotificationType type) {
            this.title = title;
            this.message = message;
            this.type = type;
        }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public NotificationType getType() { return type; }
    }

    private static final NotificationCenter INSTANCE = new NotificationCenter();
    private final List<Consumer<Notification>> listeners = new ArrayList<>();

    public static NotificationCenter getInstance() { return INSTANCE; }

    public void addNotification(String title, String message, NotificationType type) {
        Notification notification = new Notification(title, message, type);
        for (Consumer<Notification> l : listeners) {
            l.accept(notification);
        }
    }

    public void addNotificationListener(Consumer<Notification> listener) {
        listeners.add(listener);
    }
}
