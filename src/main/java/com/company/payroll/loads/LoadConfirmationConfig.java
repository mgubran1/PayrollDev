package com.company.payroll.loads;

import java.io.*;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadConfirmationConfig {
    private static final Logger logger = LoggerFactory.getLogger(LoadConfirmationConfig.class);
    private static final String CONFIG_FILE = "load_confirmation_config.properties";
    
    // Configuration properties
    private String pickupDeliveryPolicy = "";
    private String dispatcherName = "";
    private String dispatcherPhone = "";
    private String dispatcherEmail = "";
    private String dispatcherFax = "";
    private String companyLogoPath = "";
    
    // Singleton instance
    private static LoadConfirmationConfig instance;
    
    private LoadConfirmationConfig() {
        load();
    }
    
    public static LoadConfirmationConfig getInstance() {
        if (instance == null) {
            instance = new LoadConfirmationConfig();
        }
        return instance;
    }
    
    public void load() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                
                pickupDeliveryPolicy = props.getProperty("pickup.delivery.policy", "");
                dispatcherName = props.getProperty("dispatcher.name", "");
                dispatcherPhone = props.getProperty("dispatcher.phone", "");
                dispatcherEmail = props.getProperty("dispatcher.email", "");
                dispatcherFax = props.getProperty("dispatcher.fax", "");
                companyLogoPath = props.getProperty("company.logo.path", "");
                
                logger.info("Load confirmation configuration loaded successfully");
            } catch (IOException e) {
                logger.error("Error loading configuration: {}", e.getMessage(), e);
            }
        } else {
            logger.info("Configuration file not found, using defaults");
            // Set some default policy text
            pickupDeliveryPolicy = "1. Driver must call upon arrival at pickup location.\n" +
                                 "2. All paperwork must be signed before departure.\n" +
                                 "3. Driver must secure load properly.\n" +
                                 "4. Delivery appointment times must be confirmed 24 hours in advance.\n" +
                                 "5. POD must be obtained and sent immediately after delivery.";
        }
    }
    
    public void save() {
        Properties props = new Properties();
        
        props.setProperty("pickup.delivery.policy", pickupDeliveryPolicy);
        props.setProperty("dispatcher.name", dispatcherName);
        props.setProperty("dispatcher.phone", dispatcherPhone);
        props.setProperty("dispatcher.email", dispatcherEmail);
        props.setProperty("dispatcher.fax", dispatcherFax);
        props.setProperty("company.logo.path", companyLogoPath);
        
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Load Confirmation Configuration");
            logger.info("Configuration saved successfully");
        } catch (IOException e) {
            logger.error("Error saving configuration: {}", e.getMessage(), e);
        }
    }
    
    // Getters and Setters
    public String getPickupDeliveryPolicy() {
        return pickupDeliveryPolicy;
    }
    
    public void setPickupDeliveryPolicy(String pickupDeliveryPolicy) {
        this.pickupDeliveryPolicy = pickupDeliveryPolicy;
    }
    
    public String getDispatcherName() {
        return dispatcherName;
    }
    
    public void setDispatcherName(String dispatcherName) {
        this.dispatcherName = dispatcherName;
    }
    
    public String getDispatcherPhone() {
        return dispatcherPhone;
    }
    
    public void setDispatcherPhone(String dispatcherPhone) {
        this.dispatcherPhone = dispatcherPhone;
    }
    
    public String getDispatcherEmail() {
        return dispatcherEmail;
    }
    
    public void setDispatcherEmail(String dispatcherEmail) {
        this.dispatcherEmail = dispatcherEmail;
    }
    
    public String getDispatcherFax() {
        return dispatcherFax;
    }
    
    public void setDispatcherFax(String dispatcherFax) {
        this.dispatcherFax = dispatcherFax;
    }
    
    public String getCompanyLogoPath() {
        return companyLogoPath;
    }
    
    public void setCompanyLogoPath(String companyLogoPath) {
        this.companyLogoPath = companyLogoPath;
    }
}