package com.company.payroll.dispatcher;

import java.util.prefs.Preferences;

/**
 * Settings and configuration for the dispatcher module
 */
public class DispatcherSettings {
    private static final Preferences prefs = Preferences.userNodeForPackage(DispatcherSettings.class);
    
    // Keys for preferences
    private static final String DISPATCHER_NAME_KEY = "dispatcher_name";
    private static final String DISPATCHER_PHONE_KEY = "dispatcher_phone";
    private static final String DISPATCHER_FAX_KEY = "dispatcher_fax";
    private static final String DISPATCHER_EMAIL_KEY = "dispatcher_email";
    private static final String WO_NUMBER_KEY = "wo_number";
    private static final String COMPANY_NAME_KEY = "company_name";
    private static final String COMPANY_LOGO_PATH_KEY = "company_logo_path";
    private static final String PICKUP_DELIVERY_POLICY_KEY = "pickup_delivery_policy";
    
    // Default policy text
    private static final String DEFAULT_POLICY = """
        Pickup & Delivery Policy
        
        1. Timeliness:
        Deliveries must be completed on time. A fee of $250 will be charged for late pickups or deliveries, in addition to any broker-imposed late fees.
        
        2. Pre-cooling:
        Ensure that the cargo is precooled to the requested temperature before arriving at the shipper.
        
        3. Temperature Control:
        Set the requested temperature on a continuous cycle; do not allow it to start and stop. If set to start and stop there will be consequences and will be held reliable for the whole load.
        
        4. Tracking:
        Use the app specified by the broker to track the load throughout the entire transport process.
        
        5. Documentation:
        Take and send pictures of the product upon loading, including the Bill of Lading (BOL), temperature readings, and seal, to your dispatch group before leaving the shipper.
        
        6. Final Checks:
        Before arriving at the receiver, send pictures of the product, temperature, and seal. Ensure all pictures, including BOLs, are sent to your dispatch group after delivery. Do not cut the seal until instructed to do so by the receiver.
        
        7. Rejection Policy:
        Do not leave the receiver's location if there are any rejections, shortages, or excess items upon delivery until instructed by your dispatcher""";
    
    // Getters
    public static String getDispatcherName() {
        return prefs.get(DISPATCHER_NAME_KEY, "Hamood Yafai H");
    }
    
    public static String getDispatcherPhone() {
        return prefs.get(DISPATCHER_PHONE_KEY, "313-770-9187");
    }
    
    public static String getDispatcherFax() {
        return prefs.get(DISPATCHER_FAX_KEY, "");
    }
    
    public static String getDispatcherEmail() {
        return prefs.get(DISPATCHER_EMAIL_KEY, "dispatch@windycitytrans.com");
    }
    
    public static String getWONumber() {
        return prefs.get(WO_NUMBER_KEY, "A-541014");
    }
    
    public static String getCompanyName() {
        return prefs.get(COMPANY_NAME_KEY, "Windy City Transportation");
    }
    
    public static String getCompanyLogoPath() {
        return prefs.get(COMPANY_LOGO_PATH_KEY, "");
    }
    
    public static String getPickupDeliveryPolicy() {
        return prefs.get(PICKUP_DELIVERY_POLICY_KEY, DEFAULT_POLICY);
    }
    
    // Setters
    public static void setDispatcherName(String name) {
        prefs.put(DISPATCHER_NAME_KEY, name);
    }
    
    public static void setDispatcherPhone(String phone) {
        prefs.put(DISPATCHER_PHONE_KEY, phone);
    }
    
    public static void setDispatcherFax(String fax) {
        prefs.put(DISPATCHER_FAX_KEY, fax);
    }
    
    public static void setDispatcherEmail(String email) {
        prefs.put(DISPATCHER_EMAIL_KEY, email);
    }
    
    public static void setWONumber(String woNumber) {
        prefs.put(WO_NUMBER_KEY, woNumber);
    }
    
    public static void setCompanyName(String companyName) {
        prefs.put(COMPANY_NAME_KEY, companyName);
    }
    
    public static void setCompanyLogoPath(String path) {
        prefs.put(COMPANY_LOGO_PATH_KEY, path);
    }
    
    public static void setPickupDeliveryPolicy(String policy) {
        prefs.put(PICKUP_DELIVERY_POLICY_KEY, policy);
    }
}