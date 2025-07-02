package com.company.payroll.dispatcher;

/**
 * Simple in-memory settings holder for dispatcher configuration.
 */
public class DispatcherSettings {
    private static String dispatcherName = "";
    private static String dispatcherPhone = "";
    private static String dispatcherEmail = "";
    private static String dispatcherFax = "";
    private static String companyName = "";
    private static String companyLogoPath = "";
    private static String pickupDeliveryPolicy = "";

    public static String getDispatcherName() { return dispatcherName; }
    public static void setDispatcherName(String v) { dispatcherName = v; }
    public static String getDispatcherPhone() { return dispatcherPhone; }
    public static void setDispatcherPhone(String v) { dispatcherPhone = v; }
    public static String getDispatcherEmail() { return dispatcherEmail; }
    public static void setDispatcherEmail(String v) { dispatcherEmail = v; }
    public static String getDispatcherFax() { return dispatcherFax; }
    public static void setDispatcherFax(String v) { dispatcherFax = v; }
    public static String getCompanyName() { return companyName; }
    public static void setCompanyName(String v) { companyName = v; }
    public static String getCompanyLogoPath() { return companyLogoPath; }
    public static void setCompanyLogoPath(String v) { companyLogoPath = v; }
    public static String getPickupDeliveryPolicy() { return pickupDeliveryPolicy; }
    public static void setPickupDeliveryPolicy(String v) { pickupDeliveryPolicy = v; }
}
