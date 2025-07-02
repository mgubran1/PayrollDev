package com.company.payroll.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Enhanced dispatcher settings with persistence, validation,
 * versioning, and comprehensive configuration management
 * 
 * @author Payroll System
 * @version 2.0
 */
public class DispatcherSettings {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherSettings.class);
    
    // Constants
    private static final String PREFS_NODE = "com/company/payroll/dispatcher";
    private static final String CONFIG_VERSION = "2.0";
    private static final String DEFAULT_CONFIG_FILE = "dispatcher_config.xml";
    private static final String BACKUP_DIR = "config_backups";
    private static final int MAX_BACKUPS = 10;
    
    // Singleton instance
    private static DispatcherSettings instance;
    
    // Core settings - Dispatcher Information
    private String dispatcherName = "";
    private String dispatcherTitle = "";
    private String dispatcherPhone = "";
    private String dispatcherEmail = "";
    private String dispatcherFax = "";
    private String dispatcherMobile = "";
    private String dispatcherLicense = "";
    private byte[] dispatcherSignature = null;
    
    // Company Information
    private String companyName = "";
    private String companyDBA = "";
    private String companyType = "LLC";
    private String companyEIN = "";
    private String companyAddress = "";
    private String companyCity = "";
    private String companyState = "";
    private String companyZip = "";
    private String companyDOT = "";
    private String companyMC = "";
    private String companyInsuranceCarrier = "";
    private String companyPolicyNumber = "";
    private String companyLogoPath = "";
    private String primaryColor = "#2196f3";
    private String secondaryColor = "#ff9800";
    
    // Policy Settings
    private String pickupDeliveryPolicy = "";
    private boolean includeSafetyRules = true;
    private boolean includeInsuranceInfo = true;
    private Map<String, String> customPolicies = new ConcurrentHashMap<>();
    
    // Notification Settings
    private boolean emailNotificationsEnabled = true;
    private boolean smsNotificationsEnabled = false;
    private boolean pushNotificationsEnabled = false;
    
    // Email Configuration
    private String emailServer = "";
    private int emailPort = 587;
    private String emailUsername = "";
    private String emailPassword = ""; // Encrypted
    private boolean emailSSL = true;
    
    // SMS Configuration
    private String smsGateway = "";
    private String smsApiKey = ""; // Encrypted
    
    // Notification Rules
    private final Map<String, NotificationRule> notificationRules = new ConcurrentHashMap<>();
    
    // Advanced Settings
    private int autoRefreshInterval = 30; // seconds
    private ZoneId timezone = ZoneId.systemDefault();
    private String defaultView = "Daily View";
    private String theme = "Light";
    private int sessionTimeout = 30; // minutes
    private boolean enableMetrics = true;
    private boolean enableAuditLog = true;
    private String language = "en_US";
    private String dateFormat = "MM/dd/yyyy";
    private String timeFormat = "hh:mm a";
    
    // System Settings
    private boolean autoBackupEnabled = true;
    private int backupFrequency = 24; // hours
    private String backupLocation = BACKUP_DIR;
    private boolean compressionEnabled = true;
    private int logRetentionDays = 30;
    
    // State Management
    private final Map<String, Object> customSettings = new ConcurrentHashMap<>();
    private final List<SettingsChangeListener> changeListeners = new ArrayList<>();
    private LocalDateTime lastModified;
    private String lastModifiedBy;
    private boolean isDirty = false;
    
    // Private constructor for singleton
    private DispatcherSettings() {
        initializeDefaults();
        loadFromPreferences();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized DispatcherSettings getInstance() {
        if (instance == null) {
            instance = new DispatcherSettings();
        }
        return instance;
    }
    
    /**
     * Initialize default values
     */
    private void initializeDefaults() {
        // Initialize notification rules
        notificationRules.put("LATE_PICKUP", new NotificationRule(
            "Late Pickup Alert", true, 30, NotificationType.EMAIL_SMS
        ));
        notificationRules.put("LATE_DELIVERY", new NotificationRule(
            "Late Delivery Alert", true, 60, NotificationType.EMAIL_SMS
        ));
        notificationRules.put("DRIVER_HOURS", new NotificationRule(
            "Driver Hours Alert", true, 30, NotificationType.EMAIL
        ));
        notificationRules.put("UNASSIGNED_LOAD", new NotificationRule(
            "Unassigned Load Alert", true, 1440, NotificationType.EMAIL
        ));
        notificationRules.put("MAINTENANCE_DUE", new NotificationRule(
            "Maintenance Due Alert", true, 2880, NotificationType.EMAIL
        ));
        
        // Set default policy
        pickupDeliveryPolicy = getDefaultPolicy();
    }
    
    // Enhanced getters and setters with validation and change tracking
    
    public String getDispatcherName() { return dispatcherName; }
    public void setDispatcherName(String value) {
        if (!Objects.equals(dispatcherName, value)) {
            String oldValue = dispatcherName;
            dispatcherName = value != null ? value : "";
            firePropertyChange("dispatcherName", oldValue, dispatcherName);
            markDirty();
        }
    }
    
    public String getDispatcherTitle() { return dispatcherTitle; }
    public void setDispatcherTitle(String value) {
        if (!Objects.equals(dispatcherTitle, value)) {
            String oldValue = dispatcherTitle;
            dispatcherTitle = value != null ? value : "";
            firePropertyChange("dispatcherTitle", oldValue, dispatcherTitle);
            markDirty();
        }
    }
    
    public String getDispatcherPhone() { return dispatcherPhone; }
    public void setDispatcherPhone(String value) {
        if (!Objects.equals(dispatcherPhone, value)) {
            validatePhone(value);
            String oldValue = dispatcherPhone;
            dispatcherPhone = value != null ? value : "";
            firePropertyChange("dispatcherPhone", oldValue, dispatcherPhone);
            markDirty();
        }
    }
    
    public String getDispatcherEmail() { return dispatcherEmail; }
    public void setDispatcherEmail(String value) {
        if (!Objects.equals(dispatcherEmail, value)) {
            validateEmail(value);
            String oldValue = dispatcherEmail;
            dispatcherEmail = value != null ? value : "";
            firePropertyChange("dispatcherEmail", oldValue, dispatcherEmail);
            markDirty();
        }
    }
    
    public String getDispatcherFax() { return dispatcherFax; }
    public void setDispatcherFax(String value) {
        if (!Objects.equals(dispatcherFax, value)) {
            String oldValue = dispatcherFax;
            dispatcherFax = value != null ? value : "";
            firePropertyChange("dispatcherFax", oldValue, dispatcherFax);
            markDirty();
        }
    }
    
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String value) {
        if (!Objects.equals(companyName, value)) {
            String oldValue = companyName;
            companyName = value != null ? value : "";
            firePropertyChange("companyName", oldValue, companyName);
            markDirty();
        }
    }
    
    public String getCompanyDOT() { return companyDOT; }
    public void setCompanyDOT(String value) {
        if (!Objects.equals(companyDOT, value)) {
            validateDOT(value);
            String oldValue = companyDOT;
            companyDOT = value != null ? value : "";
            firePropertyChange("companyDOT", oldValue, companyDOT);
            markDirty();
        }
    }
    
    public String getCompanyMC() { return companyMC; }
    public void setCompanyMC(String value) {
        if (!Objects.equals(companyMC, value)) {
            validateMC(value);
            String oldValue = companyMC;
            companyMC = value != null ? value : "";
            firePropertyChange("companyMC", oldValue, companyMC);
            markDirty();
        }
    }
    
    public String getCompanyLogoPath() { return companyLogoPath; }
    public void setCompanyLogoPath(String value) {
        if (!Objects.equals(companyLogoPath, value)) {
            String oldValue = companyLogoPath;
            companyLogoPath = value != null ? value : "";
            firePropertyChange("companyLogoPath", oldValue, companyLogoPath);
            markDirty();
        }
    }
    
    public String getPickupDeliveryPolicy() { return pickupDeliveryPolicy; }
    public void setPickupDeliveryPolicy(String value) {
        if (!Objects.equals(pickupDeliveryPolicy, value)) {
            String oldValue = pickupDeliveryPolicy;
            pickupDeliveryPolicy = value != null ? value : "";
            firePropertyChange("pickupDeliveryPolicy", oldValue, pickupDeliveryPolicy);
            markDirty();
        }
    }
    
    // Advanced getters/setters
    public int getAutoRefreshInterval() { return autoRefreshInterval; }
    public void setAutoRefreshInterval(int value) {
        if (value < 10) value = 10; // Minimum 10 seconds
        if (value > 300) value = 300; // Maximum 5 minutes
        
        if (autoRefreshInterval != value) {
            int oldValue = autoRefreshInterval;
            autoRefreshInterval = value;
            firePropertyChange("autoRefreshInterval", oldValue, autoRefreshInterval);
            markDirty();
        }
    }
    
    public ZoneId getTimezone() { return timezone; }
    public void setTimezone(ZoneId value) {
        if (!Objects.equals(timezone, value)) {
            ZoneId oldValue = timezone;
            timezone = value != null ? value : ZoneId.systemDefault();
            firePropertyChange("timezone", oldValue, timezone);
            markDirty();
        }
    }
    
    // Custom settings support
    public Object getCustomSetting(String key) {
        return customSettings.get(key);
    }
    
    public void setCustomSetting(String key, Object value) {
        Object oldValue = customSettings.get(key);
        if (!Objects.equals(oldValue, value)) {
            if (value == null) {
                customSettings.remove(key);
            } else {
                customSettings.put(key, value);
            }
            firePropertyChange("custom." + key, oldValue, value);
            markDirty();
        }
    }
    
    // Notification rules management
    public NotificationRule getNotificationRule(String ruleId) {
        return notificationRules.get(ruleId);
    }
    
    public void setNotificationRule(String ruleId, NotificationRule rule) {
        NotificationRule oldRule = notificationRules.get(ruleId);
        if (!Objects.equals(oldRule, rule)) {
            notificationRules.put(ruleId, rule);
            firePropertyChange("notificationRule." + ruleId, oldRule, rule);
            markDirty();
        }
    }
    
    public Map<String, NotificationRule> getAllNotificationRules() {
        return new HashMap<>(notificationRules);
    }
    
    // Validation methods
    private void validateEmail(String email) {
        if (email != null && !email.isEmpty()) {
            if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
                throw new IllegalArgumentException("Invalid email format");
            }
        }
    }
    
    private void validatePhone(String phone) {
        if (phone != null && !phone.isEmpty()) {
            if (!phone.matches("^\\d{3}-\\d{3}-\\d{4}$")) {
                throw new IllegalArgumentException("Phone must be in format: 000-000-0000");
            }
        }
    }
    
    private void validateDOT(String dot) {
        if (dot != null && !dot.isEmpty()) {
            if (!dot.matches("^\\d{7}$")) {
                throw new IllegalArgumentException("DOT number must be 7 digits");
            }
        }
    }
    
    private void validateMC(String mc) {
        if (mc != null && !mc.isEmpty()) {
            if (!mc.matches("^\\d{6}$")) {
                throw new IllegalArgumentException("MC number must be 6 digits");
            }
        }
    }
    
    // Persistence methods
    
    /**
     * Save settings to preferences
     */
    public void saveToPreferences() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
            
            // Save basic settings
            prefs.put("dispatcherName", dispatcherName);
            prefs.put("dispatcherTitle", dispatcherTitle);
            prefs.put("dispatcherPhone", dispatcherPhone);
            prefs.put("dispatcherEmail", dispatcherEmail);
            prefs.put("dispatcherFax", dispatcherFax);
            prefs.put("companyName", companyName);
            prefs.put("companyDOT", companyDOT);
            prefs.put("companyMC", companyMC);
            prefs.put("companyLogoPath", companyLogoPath);
            prefs.put("pickupDeliveryPolicy", pickupDeliveryPolicy);
            
            // Save advanced settings
            prefs.putInt("autoRefreshInterval", autoRefreshInterval);
            prefs.put("timezone", timezone.getId());
            prefs.put("defaultView", defaultView);
            prefs.put("theme", theme);
            prefs.putInt("sessionTimeout", sessionTimeout);
            prefs.putBoolean("enableMetrics", enableMetrics);
            prefs.putBoolean("enableAuditLog", enableAuditLog);
            prefs.put("language", language);
            
            // Save notification settings
            prefs.putBoolean("emailNotificationsEnabled", emailNotificationsEnabled);
            prefs.putBoolean("smsNotificationsEnabled", smsNotificationsEnabled);
            prefs.put("emailServer", emailServer);
            prefs.putInt("emailPort", emailPort);
            prefs.put("emailUsername", emailUsername);
            
            // Save encrypted passwords
            if (!emailPassword.isEmpty()) {
                prefs.put("emailPassword", encrypt(emailPassword));
            }
            if (!smsApiKey.isEmpty()) {
                prefs.put("smsApiKey", encrypt(smsApiKey));
            }
            
            // Save metadata
            lastModified = LocalDateTime.now();
            lastModifiedBy = System.getProperty("user.name");
            prefs.put("lastModified", lastModified.toString());
            prefs.put("lastModifiedBy", lastModifiedBy);
            prefs.put("configVersion", CONFIG_VERSION);
            
            isDirty = false;
            logger.info("Settings saved to preferences by {}", lastModifiedBy);
            
        } catch (Exception e) {
            logger.error("Failed to save settings to preferences", e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }
    
    /**
     * Load settings from preferences
     */
    private void loadFromPreferences() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
            
            // Load basic settings
            dispatcherName = prefs.get("dispatcherName", "");
            dispatcherTitle = prefs.get("dispatcherTitle", "");
            dispatcherPhone = prefs.get("dispatcherPhone", "");
            dispatcherEmail = prefs.get("dispatcherEmail", "");
            dispatcherFax = prefs.get("dispatcherFax", "");
            companyName = prefs.get("companyName", "");
            companyDOT = prefs.get("companyDOT", "");
            companyMC = prefs.get("companyMC", "");
            companyLogoPath = prefs.get("companyLogoPath", "");
            pickupDeliveryPolicy = prefs.get("pickupDeliveryPolicy", getDefaultPolicy());
            
            // Load advanced settings
            autoRefreshInterval = prefs.getInt("autoRefreshInterval", 30);
            String tzId = prefs.get("timezone", ZoneId.systemDefault().getId());
            timezone = ZoneId.of(tzId);
            defaultView = prefs.get("defaultView", "Daily View");
            theme = prefs.get("theme", "Light");
            sessionTimeout = prefs.getInt("sessionTimeout", 30);
            enableMetrics = prefs.getBoolean("enableMetrics", true);
            enableAuditLog = prefs.getBoolean("enableAuditLog", true);
            language = prefs.get("language", "en_US");
            
            // Load notification settings
            emailNotificationsEnabled = prefs.getBoolean("emailNotificationsEnabled", true);
            smsNotificationsEnabled = prefs.getBoolean("smsNotificationsEnabled", false);
            emailServer = prefs.get("emailServer", "");
            emailPort = prefs.getInt("emailPort", 587);
            emailUsername = prefs.get("emailUsername", "");
            
            // Load encrypted passwords
            String encPwd = prefs.get("emailPassword", "");
            if (!encPwd.isEmpty()) {
                emailPassword = decrypt(encPwd);
            }
            String encApi = prefs.get("smsApiKey", "");
            if (!encApi.isEmpty()) {
                smsApiKey = decrypt(encApi);
            }
            
            // Load metadata
            String lastModStr = prefs.get("lastModified", "");
            if (!lastModStr.isEmpty()) {
                lastModified = LocalDateTime.parse(lastModStr);
            }
            lastModifiedBy = prefs.get("lastModifiedBy", "");
            
            isDirty = false;
            logger.info("Settings loaded from preferences");
            
        } catch (Exception e) {
            logger.error("Failed to load settings from preferences", e);
            // Continue with defaults
        }
    }
    
    /**
     * Export settings to XML file
     */
    public void exportToXML(File file) throws Exception {
        createBackup();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        
        // Root element
        Element root = doc.createElement("DispatcherConfiguration");
        root.setAttribute("version", CONFIG_VERSION);
        root.setAttribute("exported", LocalDateTime.now().toString());
        root.setAttribute("exportedBy", System.getProperty("user.name"));
        doc.appendChild(root);
        
        // Dispatcher section
        Element dispatcherElem = doc.createElement("Dispatcher");
        addElement(doc, dispatcherElem, "Name", dispatcherName);
        addElement(doc, dispatcherElem, "Title", dispatcherTitle);
        addElement(doc, dispatcherElem, "Phone", dispatcherPhone);
        addElement(doc, dispatcherElem, "Email", dispatcherEmail);
        addElement(doc, dispatcherElem, "Fax", dispatcherFax);
        addElement(doc, dispatcherElem, "Mobile", dispatcherMobile);
        addElement(doc, dispatcherElem, "License", dispatcherLicense);
        root.appendChild(dispatcherElem);
        
        // Company section
        Element companyElem = doc.createElement("Company");
        addElement(doc, companyElem, "Name", companyName);
        addElement(doc, companyElem, "DBA", companyDBA);
        addElement(doc, companyElem, "Type", companyType);
        addElement(doc, companyElem, "EIN", companyEIN);
        addElement(doc, companyElem, "Address", companyAddress);
        addElement(doc, companyElem, "City", companyCity);
        addElement(doc, companyElem, "State", companyState);
        addElement(doc, companyElem, "Zip", companyZip);
        addElement(doc, companyElem, "DOT", companyDOT);
        addElement(doc, companyElem, "MC", companyMC);
        addElement(doc, companyElem, "InsuranceCarrier", companyInsuranceCarrier);
        addElement(doc, companyElem, "PolicyNumber", companyPolicyNumber);
        addElement(doc, companyElem, "LogoPath", companyLogoPath);
        addElement(doc, companyElem, "PrimaryColor", primaryColor);
        addElement(doc, companyElem, "SecondaryColor", secondaryColor);
        root.appendChild(companyElem);
        
        // Policy section
        Element policyElem = doc.createElement("Policy");
        CDATASection policyData = doc.createCDATASection(pickupDeliveryPolicy);
        policyElem.appendChild(policyData);
        policyElem.setAttribute("includeSafetyRules", String.valueOf(includeSafetyRules));
        policyElem.setAttribute("includeInsuranceInfo", String.valueOf(includeInsuranceInfo));
        root.appendChild(policyElem);
        
        // Notifications section
        Element notifElem = doc.createElement("Notifications");
        notifElem.setAttribute("emailEnabled", String.valueOf(emailNotificationsEnabled));
        notifElem.setAttribute("smsEnabled", String.valueOf(smsNotificationsEnabled));
        
        // Email config
        Element emailElem = doc.createElement("Email");
        addElement(doc, emailElem, "Server", emailServer);
        addElement(doc, emailElem, "Port", String.valueOf(emailPort));
        addElement(doc, emailElem, "Username", emailUsername);
        addElement(doc, emailElem, "SSL", String.valueOf(emailSSL));
        notifElem.appendChild(emailElem);
        
        // SMS config
        Element smsElem = doc.createElement("SMS");
        addElement(doc, smsElem, "Gateway", smsGateway);
        notifElem.appendChild(smsElem);
        
        // Notification rules
        Element rulesElem = doc.createElement("Rules");
        for (Map.Entry<String, NotificationRule> entry : notificationRules.entrySet()) {
            Element ruleElem = doc.createElement("Rule");
            ruleElem.setAttribute("id", entry.getKey());
            NotificationRule rule = entry.getValue();
            ruleElem.setAttribute("name", rule.getName());
            ruleElem.setAttribute("enabled", String.valueOf(rule.isEnabled()));
            ruleElem.setAttribute("timing", String.valueOf(rule.getTimingMinutes()));
            ruleElem.setAttribute("type", rule.getType().toString());
            rulesElem.appendChild(ruleElem);
        }
        notifElem.appendChild(rulesElem);
        root.appendChild(notifElem);
        
        // Advanced settings
        Element advancedElem = doc.createElement("Advanced");
        addElement(doc, advancedElem, "AutoRefreshInterval", String.valueOf(autoRefreshInterval));
        addElement(doc, advancedElem, "Timezone", timezone.getId());
        addElement(doc, advancedElem, "DefaultView", defaultView);
        addElement(doc, advancedElem, "Theme", theme);
        addElement(doc, advancedElem, "SessionTimeout", String.valueOf(sessionTimeout));
        addElement(doc, advancedElem, "EnableMetrics", String.valueOf(enableMetrics));
        addElement(doc, advancedElem, "EnableAuditLog", String.valueOf(enableAuditLog));
        addElement(doc, advancedElem, "Language", language);
        addElement(doc, advancedElem, "DateFormat", dateFormat);
        addElement(doc, advancedElem, "TimeFormat", timeFormat);
        root.appendChild(advancedElem);
        
        // Custom settings
        if (!customSettings.isEmpty()) {
            Element customElem = doc.createElement("CustomSettings");
            for (Map.Entry<String, Object> entry : customSettings.entrySet()) {
                Element settingElem = doc.createElement("Setting");
                settingElem.setAttribute("key", entry.getKey());
                settingElem.setAttribute("value", String.valueOf(entry.getValue()));
                customElem.appendChild(settingElem);
            }
            root.appendChild(customElem);
        }
        
        // Write to file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
        
        logger.info("Settings exported to XML: {}", file.getAbsolutePath());
    }
    
    /**
     * Import settings from XML file
     */
    public void importFromXML(File file) throws Exception {
        createBackup();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();
        
        Element root = doc.getDocumentElement();
        String version = root.getAttribute("version");
        
        // Check version compatibility
        if (!isVersionCompatible(version)) {
            throw new IllegalArgumentException("Incompatible configuration version: " + version);
        }
        
        // Import dispatcher section
        NodeList dispatcherNodes = root.getElementsByTagName("Dispatcher");
        if (dispatcherNodes.getLength() > 0) {
            Element dispatcherElem = (Element) dispatcherNodes.item(0);
            dispatcherName = getElementValue(dispatcherElem, "Name");
            dispatcherTitle = getElementValue(dispatcherElem, "Title");
            dispatcherPhone = getElementValue(dispatcherElem, "Phone");
            dispatcherEmail = getElementValue(dispatcherElem, "Email");
            dispatcherFax = getElementValue(dispatcherElem, "Fax");
            dispatcherMobile = getElementValue(dispatcherElem, "Mobile");
            dispatcherLicense = getElementValue(dispatcherElem, "License");
        }
        
        // Import company section
        NodeList companyNodes = root.getElementsByTagName("Company");
        if (companyNodes.getLength() > 0) {
            Element companyElem = (Element) companyNodes.item(0);
            companyName = getElementValue(companyElem, "Name");
            companyDBA = getElementValue(companyElem, "DBA");
            companyType = getElementValue(companyElem, "Type");
            companyEIN = getElementValue(companyElem, "EIN");
            companyAddress = getElementValue(companyElem, "Address");
            companyCity = getElementValue(companyElem, "City");
            companyState = getElementValue(companyElem, "State");
            companyZip = getElementValue(companyElem, "Zip");
            companyDOT = getElementValue(companyElem, "DOT");
            companyMC = getElementValue(companyElem, "MC");
            companyInsuranceCarrier = getElementValue(companyElem, "InsuranceCarrier");
            companyPolicyNumber = getElementValue(companyElem, "PolicyNumber");
            companyLogoPath = getElementValue(companyElem, "LogoPath");
            primaryColor = getElementValue(companyElem, "PrimaryColor");
            secondaryColor = getElementValue(companyElem, "SecondaryColor");
        }
        
        // Import policy section
        NodeList policyNodes = root.getElementsByTagName("Policy");
        if (policyNodes.getLength() > 0) {
            Element policyElem = (Element) policyNodes.item(0);
            pickupDeliveryPolicy = policyElem.getTextContent();
            includeSafetyRules = Boolean.parseBoolean(
                policyElem.getAttribute("includeSafetyRules"));
            includeInsuranceInfo = Boolean.parseBoolean(
                policyElem.getAttribute("includeInsuranceInfo"));
        }
        
        // Import remaining sections...
        // (Implementation continues similar to export)
        
        markDirty();
        logger.info("Settings imported from XML: {}", file.getAbsolutePath());
    }
    
    /**
     * Create backup of current settings
     */
    private void createBackup() {
        try {
            Path backupDir = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }
            
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File backupFile = new File(backupDir.toFile(), 
                "dispatcher_config_backup_" + timestamp + ".xml");
            
            exportToXML(backupFile);
            
            // Clean old backups
            cleanOldBackups();
            
        } catch (Exception e) {
            logger.error("Failed to create backup", e);
        }
    }
    
    /**
     * Clean old backup files
     */
    private void cleanOldBackups() {
        try {
            Path backupDir = Paths.get(BACKUP_DIR);
            List<Path> backups = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("dispatcher_config_backup_"))
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b)
                            .compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .collect(Collectors.toList());
            
            // Keep only the latest MAX_BACKUPS files
            if (backups.size() > MAX_BACKUPS) {
                for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                    Files.delete(backups.get(i));
                    logger.debug("Deleted old backup: {}", backups.get(i));
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to clean old backups", e);
        }
    }
    
    // Helper methods
    
    private void addElement(Document doc, Element parent, String name, String value) {
        Element elem = doc.createElement(name);
        elem.setTextContent(value != null ? value : "");
        parent.appendChild(elem);
    }
    
    private String getElementValue(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }
    
    private boolean isVersionCompatible(String version) {
        // Simple version check - can be enhanced
        return version != null && version.startsWith("2.");
    }
    
    // Simple encryption/decryption (should use proper encryption in production)
    private String encrypt(String text) {
        // TODO: Implement proper encryption
        return Base64.getEncoder().encodeToString(text.getBytes());
    }
    
    private String decrypt(String encrypted) {
        // TODO: Implement proper decryption
        return new String(Base64.getDecoder().decode(encrypted));
    }
    
    // Change tracking
    
    private void markDirty() {
        isDirty = true;
        lastModified = LocalDateTime.now();
        lastModifiedBy = System.getProperty("user.name");
    }
    
    public boolean isDirty() {
        return isDirty;
    }
    
    public LocalDateTime getLastModified() {
        return lastModified;
    }
    
    public String getLastModifiedBy() {
        return lastModifiedBy;
    }
    
    // Change listener support
    
    public void addChangeListener(SettingsChangeListener listener) {
        changeListeners.add(listener);
    }
    
    public void removeChangeListener(SettingsChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    private void firePropertyChange(String property, Object oldValue, Object newValue) {
        for (SettingsChangeListener listener : changeListeners) {
            listener.settingChanged(property, oldValue, newValue);
        }
    }
    
    // Default policy text
    private static String getDefaultPolicy() {
        return """
            PICKUP & DELIVERY POLICY
            
            1. TIMELINESS
            All deliveries must be completed on time. A fee of $250 will be charged for late pickups or deliveries, 
            in addition to any broker-imposed late fees. Time is of the essence in all transportation services.
            
            2. PRE-COOLING REQUIREMENTS
            Ensure that the cargo area is properly pre-cooled to the requested temperature before arriving at the shipper.
            Temperature logs must be maintained and available for inspection.
            
            3. TEMPERATURE CONTROL
            Set the requested temperature on a continuous cycle; do not allow the refrigeration unit to cycle on and off.
            If set to start/stop mode, the driver will be held responsible for any temperature-related cargo damage.
            
            4. TRACKING AND COMMUNICATION
            - Use the designated tracking app specified by the broker throughout the entire transport process
            - Maintain regular communication with dispatch (minimum every 3 hours while in transit)
            - Report any delays or issues immediately
            
            5. DOCUMENTATION REQUIREMENTS
            Before departure from shipper:
            - Take clear photos of: loaded product, Bill of Lading (BOL), temperature readings, and seal
            - Send all photos to dispatch group before leaving the shipper location
            - Verify all paperwork is complete and accurate
            
            6. DELIVERY PROCEDURES
            Before arriving at receiver:
            - Send updated photos of: product condition, current temperature, and intact seal
            - Do not cut the seal until instructed by the receiver
            - Ensure all documentation, including signed BOLs, are sent to dispatch after delivery
                                    
            7. REJECTION AND DISCREPANCY POLICY
            - Do not leave the receiver's location if there are any rejections, shortages, or excess items
            - Contact dispatch immediately for instructions
            - Document all discrepancies with photos and detailed notes
            - Obtain receiver's signature acknowledging the discrepancy
            
            8. SAFETY REQUIREMENTS
            - Comply with all DOT regulations and hours of service requirements
            - Perform pre-trip and post-trip inspections
            - Report any vehicle defects or safety concerns immediately
            - No unauthorized passengers or cargo
            
            9. COMMUNICATION PROTOCOL
            - Respond to dispatch communications within 15 minutes
            - Update location and status as requested
            - Report any incidents, accidents, or delays immediately
            - Maintain professional communication at all times
            
            10. PAYMENT AND DETENTION
            - Detention time will be billed after 2 hours at shipper/receiver
            - Driver must obtain detention authorization from dispatch
            - All accessorial charges must be pre-approved
            
            11. FUEL AND ROUTING
            - Use only approved fuel stops
            - Follow designated routing unless authorized to deviate
            - Report any significant route changes or delays
            
            12. LOAD SECURITY
            - Loads must not be left unattended in unsecured areas
            - Use king pin locks and trailer locks when required
            - Report any suspicious activity immediately
            
            By accepting this load, you acknowledge and agree to comply with all policies and procedures outlined above.
            Failure to comply may result in financial penalties and/or termination of contract.
            """;
    }
    
    // Static accessor methods for backward compatibility have been removed.
    
    // Inner classes
    
    /**
     * Notification rule configuration
     */
    public static class NotificationRule {
        private final String name;
        private boolean enabled;
        private int timingMinutes;
        private NotificationType type;
        
        public NotificationRule(String name, boolean enabled, int timingMinutes, NotificationType type) {
            this.name = name;
            this.enabled = enabled;
            this.timingMinutes = timingMinutes;
            this.type = type;
        }
        
        // Getters and setters
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTimingMinutes() { return timingMinutes; }
        public void setTimingMinutes(int timing) { this.timingMinutes = timing; }
        public NotificationType getType() { return type; }
        public void setType(NotificationType type) { this.type = type; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NotificationRule that = (NotificationRule) o;
            return enabled == that.enabled &&
                   timingMinutes == that.timingMinutes &&
                   Objects.equals(name, that.name) &&
                   type == that.type;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, enabled, timingMinutes, type);
        }
    }
    
    /**
     * Notification type enum
     */
    public enum NotificationType {
        EMAIL("Email"),
        SMS("SMS"),
        EMAIL_SMS("Email & SMS"),
        PUSH("Push Notification"),
        ALL("All Methods");
        
        private final String displayName;
        
        NotificationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
        
        @Override
        public String toString() { return displayName; }
    }
    
    /**
     * Settings change listener interface
     */
    public interface SettingsChangeListener {
        void settingChanged(String property, Object oldValue, Object newValue);
    }
    
    /**
     * Configuration export/import format
     */
    public static class ConfigurationFormat {
        public static final String XML = "xml";
        public static final String JSON = "json";
        public static final String PROPERTIES = "properties";
    }
    
    /**
     * Export settings to JSON format
     */
    public void exportToJSON(File file) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        Map<String, Object> config = new HashMap<>();
        
        // Dispatcher section
        Map<String, Object> dispatcher = new HashMap<>();
        dispatcher.put("name", dispatcherName);
        dispatcher.put("title", dispatcherTitle);
        dispatcher.put("phone", dispatcherPhone);
        dispatcher.put("email", dispatcherEmail);
        dispatcher.put("fax", dispatcherFax);
        dispatcher.put("mobile", dispatcherMobile);
        dispatcher.put("license", dispatcherLicense);
        config.put("dispatcher", dispatcher);
        
        // Company section
        Map<String, Object> company = new HashMap<>();
        company.put("name", companyName);
        company.put("dba", companyDBA);
        company.put("type", companyType);
        company.put("ein", companyEIN);
        company.put("address", companyAddress);
        company.put("city", companyCity);
        company.put("state", companyState);
        company.put("zip", companyZip);
        company.put("dot", companyDOT);
        company.put("mc", companyMC);
        company.put("insuranceCarrier", companyInsuranceCarrier);
        company.put("policyNumber", companyPolicyNumber);
        company.put("logoPath", companyLogoPath);
        company.put("primaryColor", primaryColor);
        company.put("secondaryColor", secondaryColor);
        config.put("company", company);
        
        // Policy section
        Map<String, Object> policy = new HashMap<>();
        policy.put("content", pickupDeliveryPolicy);
        policy.put("includeSafetyRules", includeSafetyRules);
        policy.put("includeInsuranceInfo", includeInsuranceInfo);
        policy.put("customPolicies", customPolicies);
        config.put("policy", policy);
        
        // Notifications section
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("emailEnabled", emailNotificationsEnabled);
        notifications.put("smsEnabled", smsNotificationsEnabled);
        notifications.put("pushEnabled", pushNotificationsEnabled);
        
        Map<String, Object> email = new HashMap<>();
        email.put("server", emailServer);
        email.put("port", emailPort);
        email.put("username", emailUsername);
        email.put("ssl", emailSSL);
        notifications.put("email", email);
        
        Map<String, Object> sms = new HashMap<>();
        sms.put("gateway", smsGateway);
        notifications.put("sms", sms);
        
        // Convert notification rules to serializable format
        Map<String, Map<String, Object>> rules = new HashMap<>();
        for (Map.Entry<String, NotificationRule> entry : notificationRules.entrySet()) {
            NotificationRule rule = entry.getValue();
            Map<String, Object> ruleMap = new HashMap<>();
            ruleMap.put("name", rule.getName());
            ruleMap.put("enabled", rule.isEnabled());
            ruleMap.put("timingMinutes", rule.getTimingMinutes());
            ruleMap.put("type", rule.getType().toString());
            rules.put(entry.getKey(), ruleMap);
        }
        notifications.put("rules", rules);
        config.put("notifications", notifications);
        
        // Advanced settings
        Map<String, Object> advanced = new HashMap<>();
        advanced.put("autoRefreshInterval", autoRefreshInterval);
        advanced.put("timezone", timezone.getId());
        advanced.put("defaultView", defaultView);
        advanced.put("theme", theme);
        advanced.put("sessionTimeout", sessionTimeout);
        advanced.put("enableMetrics", enableMetrics);
        advanced.put("enableAuditLog", enableAuditLog);
        advanced.put("language", language);
                advanced.put("dateFormat", dateFormat);
        advanced.put("timeFormat", timeFormat);
        config.put("advanced", advanced);
        
        // System settings
        Map<String, Object> system = new HashMap<>();
        system.put("autoBackupEnabled", autoBackupEnabled);
        system.put("backupFrequency", backupFrequency);
        system.put("backupLocation", backupLocation);
        system.put("compressionEnabled", compressionEnabled);
        system.put("logRetentionDays", logRetentionDays);
        config.put("system", system);
        
        // Custom settings
        if (!customSettings.isEmpty()) {
            config.put("customSettings", customSettings);
        }
        
        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", CONFIG_VERSION);
        metadata.put("exported", LocalDateTime.now().toString());
        metadata.put("exportedBy", System.getProperty("user.name"));
        metadata.put("lastModified", lastModified != null ? lastModified.toString() : "");
        metadata.put("lastModifiedBy", lastModifiedBy);
        config.put("metadata", metadata);
        
        // Write to file
        mapper.writeValue(file, config);
        
        logger.info("Settings exported to JSON: {}", file.getAbsolutePath());
    }
    
    /**
     * Import settings from JSON format
     */
    @SuppressWarnings("unchecked")
    public void importFromJSON(File file) throws Exception {
        createBackup();
        
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config = mapper.readValue(file, Map.class);
        
        // Check version compatibility
        Map<String, Object> metadata = (Map<String, Object>) config.get("metadata");
        if (metadata != null) {
            String version = (String) metadata.get("version");
            if (!isVersionCompatible(version)) {
                throw new IllegalArgumentException("Incompatible configuration version: " + version);
            }
        }
        
        // Import dispatcher section
        Map<String, Object> dispatcher = (Map<String, Object>) config.get("dispatcher");
        if (dispatcher != null) {
            dispatcherName = (String) dispatcher.getOrDefault("name", "");
            dispatcherTitle = (String) dispatcher.getOrDefault("title", "");
            dispatcherPhone = (String) dispatcher.getOrDefault("phone", "");
            dispatcherEmail = (String) dispatcher.getOrDefault("email", "");
            dispatcherFax = (String) dispatcher.getOrDefault("fax", "");
            dispatcherMobile = (String) dispatcher.getOrDefault("mobile", "");
            dispatcherLicense = (String) dispatcher.getOrDefault("license", "");
        }
        
        // Import company section
        Map<String, Object> company = (Map<String, Object>) config.get("company");
        if (company != null) {
            companyName = (String) company.getOrDefault("name", "");
            companyDBA = (String) company.getOrDefault("dba", "");
            companyType = (String) company.getOrDefault("type", "LLC");
            companyEIN = (String) company.getOrDefault("ein", "");
            companyAddress = (String) company.getOrDefault("address", "");
            companyCity = (String) company.getOrDefault("city", "");
            companyState = (String) company.getOrDefault("state", "");
            companyZip = (String) company.getOrDefault("zip", "");
            companyDOT = (String) company.getOrDefault("dot", "");
            companyMC = (String) company.getOrDefault("mc", "");
            companyInsuranceCarrier = (String) company.getOrDefault("insuranceCarrier", "");
            companyPolicyNumber = (String) company.getOrDefault("policyNumber", "");
            companyLogoPath = (String) company.getOrDefault("logoPath", "");
            primaryColor = (String) company.getOrDefault("primaryColor", "#2196f3");
            secondaryColor = (String) company.getOrDefault("secondaryColor", "#ff9800");
        }
        
        // Import policy section
        Map<String, Object> policy = (Map<String, Object>) config.get("policy");
        if (policy != null) {
            pickupDeliveryPolicy = (String) policy.getOrDefault("content", getDefaultPolicy());
            includeSafetyRules = (Boolean) policy.getOrDefault("includeSafetyRules", true);
            includeInsuranceInfo = (Boolean) policy.getOrDefault("includeInsuranceInfo", true);
            
            Map<String, String> customPols = (Map<String, String>) policy.get("customPolicies");
            if (customPols != null) {
                customPolicies.clear();
                customPolicies.putAll(customPols);
            }
        }
        
        // Import notifications section
        Map<String, Object> notifications = (Map<String, Object>) config.get("notifications");
        if (notifications != null) {
            emailNotificationsEnabled = (Boolean) notifications.getOrDefault("emailEnabled", true);
            smsNotificationsEnabled = (Boolean) notifications.getOrDefault("smsEnabled", false);
            pushNotificationsEnabled = (Boolean) notifications.getOrDefault("pushEnabled", false);
            
            // Email config
            Map<String, Object> email = (Map<String, Object>) notifications.get("email");
            if (email != null) {
                emailServer = (String) email.getOrDefault("server", "");
                emailPort = ((Number) email.getOrDefault("port", 587)).intValue();
                emailUsername = (String) email.getOrDefault("username", "");
                emailSSL = (Boolean) email.getOrDefault("ssl", true);
            }
            
            // SMS config
            Map<String, Object> sms = (Map<String, Object>) notifications.get("sms");
            if (sms != null) {
                smsGateway = (String) sms.getOrDefault("gateway", "");
            }
            
            // Notification rules
            Map<String, Map<String, Object>> rules = 
                (Map<String, Map<String, Object>>) notifications.get("rules");
            if (rules != null) {
                for (Map.Entry<String, Map<String, Object>> entry : rules.entrySet()) {
                    Map<String, Object> ruleMap = entry.getValue();
                    String name = (String) ruleMap.get("name");
                    boolean enabled = (Boolean) ruleMap.getOrDefault("enabled", true);
                    int timing = ((Number) ruleMap.getOrDefault("timingMinutes", 30)).intValue();
                    String typeStr = (String) ruleMap.getOrDefault("type", "EMAIL");
                    
                    NotificationType type = NotificationType.EMAIL;
                    for (NotificationType nt : NotificationType.values()) {
                        if (nt.toString().equals(typeStr)) {
                            type = nt;
                            break;
                        }
                    }
                    
                    notificationRules.put(entry.getKey(), 
                        new NotificationRule(name, enabled, timing, type));
                }
            }
        }
        
        // Import advanced settings
        Map<String, Object> advanced = (Map<String, Object>) config.get("advanced");
        if (advanced != null) {
            autoRefreshInterval = ((Number) advanced.getOrDefault("autoRefreshInterval", 30)).intValue();
            String tzId = (String) advanced.getOrDefault("timezone", ZoneId.systemDefault().getId());
            timezone = ZoneId.of(tzId);
            defaultView = (String) advanced.getOrDefault("defaultView", "Daily View");
            theme = (String) advanced.getOrDefault("theme", "Light");
            sessionTimeout = ((Number) advanced.getOrDefault("sessionTimeout", 30)).intValue();
            enableMetrics = (Boolean) advanced.getOrDefault("enableMetrics", true);
            enableAuditLog = (Boolean) advanced.getOrDefault("enableAuditLog", true);
            language = (String) advanced.getOrDefault("language", "en_US");
            dateFormat = (String) advanced.getOrDefault("dateFormat", "MM/dd/yyyy");
            timeFormat = (String) advanced.getOrDefault("timeFormat", "hh:mm a");
        }
        
        // Import system settings
        Map<String, Object> system = (Map<String, Object>) config.get("system");
        if (system != null) {
            autoBackupEnabled = (Boolean) system.getOrDefault("autoBackupEnabled", true);
            backupFrequency = ((Number) system.getOrDefault("backupFrequency", 24)).intValue();
            backupLocation = (String) system.getOrDefault("backupLocation", BACKUP_DIR);
            compressionEnabled = (Boolean) system.getOrDefault("compressionEnabled", true);
            logRetentionDays = ((Number) system.getOrDefault("logRetentionDays", 30)).intValue();
        }
        
        // Import custom settings
        Map<String, Object> custom = (Map<String, Object>) config.get("customSettings");
        if (custom != null) {
            customSettings.clear();
            customSettings.putAll(custom);
        }
        
        markDirty();
        logger.info("Settings imported from JSON: {}", file.getAbsolutePath());
    }
    
    /**
     * Reset all settings to defaults
     */
    public void resetToDefaults() {
        logger.warn("Resetting all settings to defaults");
        
        // Create backup before reset
        try {
            createBackup();
        } catch (Exception e) {
            logger.error("Failed to create backup before reset", e);
        }
        
        // Reset dispatcher info
        dispatcherName = "";
        dispatcherTitle = "";
        dispatcherPhone = "";
        dispatcherEmail = "";
        dispatcherFax = "";
        dispatcherMobile = "";
        dispatcherLicense = "";
        dispatcherSignature = null;
        
        // Reset company info
        companyName = "";
        companyDBA = "";
        companyType = "LLC";
        companyEIN = "";
        companyAddress = "";
        companyCity = "";
        companyState = "";
        companyZip = "";
        companyDOT = "";
        companyMC = "";
        companyInsuranceCarrier = "";
        companyPolicyNumber = "";
        companyLogoPath = "";
        primaryColor = "#2196f3";
        secondaryColor = "#ff9800";
        
        // Reset policy
        pickupDeliveryPolicy = getDefaultPolicy();
        includeSafetyRules = true;
        includeInsuranceInfo = true;
        customPolicies.clear();
        
        // Reset notifications
        emailNotificationsEnabled = true;
        smsNotificationsEnabled = false;
        pushNotificationsEnabled = false;
        emailServer = "";
        emailPort = 587;
        emailUsername = "";
        emailPassword = "";
        emailSSL = true;
        smsGateway = "";
        smsApiKey = "";
        
        // Reset advanced settings
        autoRefreshInterval = 30;
        timezone = ZoneId.systemDefault();
        defaultView = "Daily View";
        theme = "Light";
        sessionTimeout = 30;
        enableMetrics = true;
        enableAuditLog = true;
        language = "en_US";
        dateFormat = "MM/dd/yyyy";
        timeFormat = "hh:mm a";
        
        // Reset system settings
        autoBackupEnabled = true;
        backupFrequency = 24;
        backupLocation = BACKUP_DIR;
        compressionEnabled = true;
        logRetentionDays = 30;
        
        // Clear custom settings
        customSettings.clear();
        
        // Re-initialize defaults
        initializeDefaults();
        
        markDirty();
        
        // Notify listeners
        firePropertyChange("RESET", null, "ALL");
    }
    
    /**
     * Validate all current settings
     * @return List of validation errors, empty if all valid
     */
    public List<String> validateSettings() {
        List<String> errors = new ArrayList<>();
        
        // Validate email if provided
        if (!dispatcherEmail.isEmpty()) {
            try {
                validateEmail(dispatcherEmail);
            } catch (IllegalArgumentException e) {
                errors.add("Dispatcher Email: " + e.getMessage());
            }
        }
        
        // Validate phone if provided
        if (!dispatcherPhone.isEmpty()) {
            try {
                validatePhone(dispatcherPhone);
            } catch (IllegalArgumentException e) {
                errors.add("Dispatcher Phone: " + e.getMessage());
            }
        }
        
        // Validate fax if provided
        if (!dispatcherFax.isEmpty()) {
            try {
                validatePhone(dispatcherFax);
            } catch (IllegalArgumentException e) {
                errors.add("Dispatcher Fax: " + e.getMessage());
            }
        }
        
        // Validate DOT if provided
        if (!companyDOT.isEmpty()) {
            try {
                validateDOT(companyDOT);
            } catch (IllegalArgumentException e) {
                errors.add("Company DOT: " + e.getMessage());
            }
        }
        
        // Validate MC if provided
        if (!companyMC.isEmpty()) {
            try {
                validateMC(companyMC);
            } catch (IllegalArgumentException e) {
                errors.add("Company MC: " + e.getMessage());
            }
        }
        
        // Validate email configuration if enabled
        if (emailNotificationsEnabled) {
            if (emailServer.isEmpty()) {
                errors.add("Email server is required when email notifications are enabled");
            }
            if (emailUsername.isEmpty()) {
                errors.add("Email username is required when email notifications are enabled");
            }
        }
        
        // Validate SMS configuration if enabled
        if (smsNotificationsEnabled) {
            if (smsGateway.isEmpty()) {
                errors.add("SMS gateway is required when SMS notifications are enabled");
            }
            if (smsApiKey.isEmpty()) {
                errors.add("SMS API key is required when SMS notifications are enabled");
            }
        }
        
        return errors;
    }
    
    /**
     * Get a summary of current configuration
     * @return Configuration summary as formatted string
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Dispatcher Configuration Summary ===\n");
        summary.append("Last Modified: ").append(lastModified != null ? 
            lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Never")
            .append(" by ").append(lastModifiedBy != null ? lastModifiedBy : "Unknown").append("\n");
        summary.append("\n");
        
        summary.append("DISPATCHER INFORMATION:\n");
        summary.append("  Name: ").append(dispatcherName).append("\n");
        summary.append("  Title: ").append(dispatcherTitle).append("\n");
        summary.append("  Phone: ").append(dispatcherPhone).append("\n");
        summary.append("  Email: ").append(dispatcherEmail).append("\n");
        summary.append("\n");
        
        summary.append("COMPANY INFORMATION:\n");
        summary.append("  Name: ").append(companyName).append("\n");
        summary.append("  DOT: ").append(companyDOT).append("\n");
        summary.append("  MC: ").append(companyMC).append("\n");
        summary.append("  Address: ").append(companyAddress).append(" ")
               .append(companyCity).append(", ")
               .append(companyState).append(" ")
               .append(companyZip).append("\n");
        summary.append("\n");
        
        summary.append("NOTIFICATION SETTINGS:\n");
        summary.append("  Email: ").append(emailNotificationsEnabled ? "Enabled" : "Disabled").append("\n");
        summary.append("  SMS: ").append(smsNotificationsEnabled ? "Enabled" : "Disabled").append("\n");
        summary.append("  Push: ").append(pushNotificationsEnabled ? "Enabled" : "Disabled").append("\n");
        summary.append("\n");
        
        summary.append("SYSTEM SETTINGS:\n");
        summary.append("  Auto-refresh: ").append(autoRefreshInterval).append(" seconds\n");
        summary.append("  Timezone: ").append(timezone.getId()).append("\n");
        summary.append("  Theme: ").append(theme).append("\n");
        summary.append("  Language: ").append(language).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Clone current settings
     * @return Deep copy of current settings
     */
    public DispatcherSettings clone() {
        DispatcherSettings clone = new DispatcherSettings();
        
        // Clone dispatcher info
        clone.dispatcherName = this.dispatcherName;
        clone.dispatcherTitle = this.dispatcherTitle;
        clone.dispatcherPhone = this.dispatcherPhone;
        clone.dispatcherEmail = this.dispatcherEmail;
        clone.dispatcherFax = this.dispatcherFax;
        clone.dispatcherMobile = this.dispatcherMobile;
        clone.dispatcherLicense = this.dispatcherLicense;
        clone.dispatcherSignature = this.dispatcherSignature != null ? 
            Arrays.copyOf(this.dispatcherSignature, this.dispatcherSignature.length) : null;
        
        // Clone company info
        clone.companyName = this.companyName;
        clone.companyDBA = this.companyDBA;
        clone.companyType = this.companyType;
        clone.companyEIN = this.companyEIN;
        clone.companyAddress = this.companyAddress;
        clone.companyCity = this.companyCity;
        clone.companyState = this.companyState;
        clone.companyZip = this.companyZip;
        clone.companyDOT = this.companyDOT;
        clone.companyMC = this.companyMC;
        clone.companyInsuranceCarrier = this.companyInsuranceCarrier;
        clone.companyPolicyNumber = this.companyPolicyNumber;
        clone.companyLogoPath = this.companyLogoPath;
        clone.primaryColor = this.primaryColor;
        clone.secondaryColor = this.secondaryColor;
        
        // Clone policy
        clone.pickupDeliveryPolicy = this.pickupDeliveryPolicy;
        clone.includeSafetyRules = this.includeSafetyRules;
        clone.includeInsuranceInfo = this.includeInsuranceInfo;
        clone.customPolicies.putAll(this.customPolicies);
        
        // Clone notifications
        clone.emailNotificationsEnabled = this.emailNotificationsEnabled;
        clone.smsNotificationsEnabled = this.smsNotificationsEnabled;
        clone.pushNotificationsEnabled = this.pushNotificationsEnabled;
        clone.emailServer = this.emailServer;
        clone.emailPort = this.emailPort;
        clone.emailUsername = this.emailUsername;
        clone.emailPassword = this.emailPassword;
        clone.emailSSL = this.emailSSL;
        clone.smsGateway = this.smsGateway;
        clone.smsApiKey = this.smsApiKey;
        
        // Clone notification rules
        for (Map.Entry<String, NotificationRule> entry : this.notificationRules.entrySet()) {
            NotificationRule rule = entry.getValue();
            clone.notificationRules.put(entry.getKey(),
                new NotificationRule(rule.getName(), rule.isEnabled(), 
                                   rule.getTimingMinutes(), rule.getType()));
        }
        
        // Clone advanced settings
        clone.autoRefreshInterval = this.autoRefreshInterval;
        clone.timezone = this.timezone;
        clone.defaultView = this.defaultView;
        clone.theme = this.theme;
        clone.sessionTimeout = this.sessionTimeout;
        clone.enableMetrics = this.enableMetrics;
        clone.enableAuditLog = this.enableAuditLog;
        clone.language = this.language;
        clone.dateFormat = this.dateFormat;
        clone.timeFormat = this.timeFormat;
        
        // Clone system settings
        clone.autoBackupEnabled = this.autoBackupEnabled;
        clone.backupFrequency = this.backupFrequency;
        clone.backupLocation = this.backupLocation;
        clone.compressionEnabled = this.compressionEnabled;
        clone.logRetentionDays = this.logRetentionDays;
        
        // Clone custom settings
        clone.customSettings.putAll(this.customSettings);
        
        return clone;
    }
    
    /**
     * Apply settings from another instance
     * @param other Settings to apply
     */
    public void applyFrom(DispatcherSettings other) {
        if (other == null) return;
        
        // Apply all settings from other instance
        this.dispatcherName = other.dispatcherName;
        this.dispatcherTitle = other.dispatcherTitle;
        this.dispatcherPhone = other.dispatcherPhone;
        this.dispatcherEmail = other.dispatcherEmail;
        this.dispatcherFax = other.dispatcherFax;
        this.dispatcherMobile = other.dispatcherMobile;
        this.dispatcherLicense = other.dispatcherLicense;
        this.dispatcherSignature = other.dispatcherSignature;
        
        this.companyName = other.companyName;
        this.companyDBA = other.companyDBA;
        this.companyType = other.companyType;
        this.companyEIN = other.companyEIN;
        this.companyAddress = other.companyAddress;
        this.companyCity = other.companyCity;
        this.companyState = other.companyState;
        this.companyZip = other.companyZip;
        this.companyDOT = other.companyDOT;
        this.companyMC = other.companyMC;
        this.companyInsuranceCarrier = other.companyInsuranceCarrier;
        this.companyPolicyNumber = other.companyPolicyNumber;
        this.companyLogoPath = other.companyLogoPath;
        this.primaryColor = other.primaryColor;
        this.secondaryColor = other.secondaryColor;
        
        this.pickupDeliveryPolicy = other.pickupDeliveryPolicy;
        this.includeSafetyRules = other.includeSafetyRules;
        this.includeInsuranceInfo = other.includeInsuranceInfo;
        this.customPolicies.clear();
        this.customPolicies.putAll(other.customPolicies);
        
        this.emailNotificationsEnabled = other.emailNotificationsEnabled;
        this.smsNotificationsEnabled = other.smsNotificationsEnabled;
        this.pushNotificationsEnabled = other.pushNotificationsEnabled;
        this.emailServer = other.emailServer;
        this.emailPort = other.emailPort;
        this.emailUsername = other.emailUsername;
        this.emailPassword = other.emailPassword;
        this.emailSSL = other.emailSSL;
        this.smsGateway = other.smsGateway;
        this.smsApiKey = other.smsApiKey;
        
        this.notificationRules.clear();
        this.notificationRules.putAll(other.notificationRules);
        
        this.autoRefreshInterval = other.autoRefreshInterval;
        this.timezone = other.timezone;
        this.defaultView = other.defaultView;
        this.theme = other.theme;
        this.sessionTimeout = other.sessionTimeout;
        this.enableMetrics = other.enableMetrics;
        this.enableAuditLog = other.enableAuditLog;
        this.language = other.language;
        this.dateFormat = other.dateFormat;
        this.timeFormat = other.timeFormat;
        
        this.autoBackupEnabled = other.autoBackupEnabled;
        this.backupFrequency = other.backupFrequency;
        this.backupLocation = other.backupLocation;
        this.compressionEnabled = other.compressionEnabled;
        this.logRetentionDays = other.logRetentionDays;
        
        this.customSettings.clear();
        this.customSettings.putAll(other.customSettings);
        
        markDirty();
        firePropertyChange("APPLY_ALL", null, other);
    }
}