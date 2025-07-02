package com.company.payroll.services;

/** Minimal notification service placeholder. */
public class NotificationService {
    private static final NotificationService INSTANCE = new NotificationService();
    public static NotificationService getInstance() { return INSTANCE; }

    public void sendNotification(String subject, String body) {
        // no-op
    }

    // Overloaded variant used in DispatcherController
    public void sendNotification(String subject, String body,
                                 java.util.Map<String, String> params,
                                 java.util.List<String> recipients) {
        // no-op
    }
}
