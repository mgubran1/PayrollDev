package com.company.payroll.services;

/** Minimal notification service placeholder. */
public class NotificationService {
    private static final NotificationService INSTANCE = new NotificationService();
    public static NotificationService getInstance() { return INSTANCE; }

    public void sendNotification(String subject, String body) {
        // no-op
    }
}
