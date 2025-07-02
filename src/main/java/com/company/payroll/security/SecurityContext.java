package com.company.payroll.security;

/** Simple security context stub. */
public class SecurityContext {
    private static String currentUser;

    public static String getCurrentUser() { return currentUser; }
    public static void setCurrentUser(String user) { currentUser = user; }
}
