package com.company.payroll.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.Properties;

/**
 * Invoice Configuration - Manages company information for invoice generation
 */
public class InvoiceConfig {
    private static final Logger logger = LoggerFactory.getLogger(InvoiceConfig.class);
    
    // Configuration file
    private static final String CONFIG_FILE = "invoice_config.properties";
    
    // Configuration keys
    private static final String COMPANY_NAME = "company.name";
    private static final String COMPANY_STREET = "company.street";
    private static final String COMPANY_CITY = "company.city";
    private static final String COMPANY_STATE = "company.state";
    private static final String COMPANY_ZIP = "company.zip";
    private static final String COMPANY_EMAIL = "company.email";
    private static final String COMPANY_PHONE = "company.phone";
    private static final String COMPANY_FAX = "company.fax";
    private static final String COMPANY_MC = "company.mc";
    private static final String INVOICE_PREFIX = "invoice.prefix";
    private static final String INVOICE_START_NUMBER = "invoice.start.number";
    private static final String INVOICE_TERMS = "invoice.terms";
    private static final String INVOICE_NOTES = "invoice.notes";
    
    // Properties instance
    private static Properties config;
    private static int currentInvoiceNumber;
    
    static {
        loadConfiguration();
    }
    
    /**
     * Load configuration from file
     */
    private static void loadConfiguration() {
        config = new Properties();
        
        // Set default values
        config.setProperty(COMPANY_NAME, "Your Company Name");
        config.setProperty(COMPANY_STREET, "123 Main Street");
        config.setProperty(COMPANY_CITY, "City");
        config.setProperty(COMPANY_STATE, "State");
        config.setProperty(COMPANY_ZIP, "12345");
        config.setProperty(COMPANY_EMAIL, "info@company.com");
        config.setProperty(COMPANY_PHONE, "(555) 123-4567");
        config.setProperty(COMPANY_FAX, "(555) 123-4568");
        config.setProperty(COMPANY_MC, "MC-123456");
        config.setProperty(INVOICE_PREFIX, "INV");
        config.setProperty(INVOICE_START_NUMBER, "1000");
        config.setProperty(INVOICE_TERMS, "Net 30");
        config.setProperty(INVOICE_NOTES, "Thank you for your business!");
        
        // Load from file if exists
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                logger.info("Invoice configuration loaded from file");
            } catch (Exception e) {
                logger.warn("Failed to load invoice config, using defaults: " + e.getMessage());
            }
        } else {
            // Create default config file
            saveConfiguration();
        }
        
        // Initialize invoice number
        currentInvoiceNumber = Integer.parseInt(config.getProperty(INVOICE_START_NUMBER, "1000"));
    }
    
    /**
     * Save configuration to file
     */
    public static void saveConfiguration() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "Invoice Configuration");
            logger.info("Invoice configuration saved");
        } catch (Exception e) {
            logger.error("Failed to save invoice configuration", e);
        }
    }
    
    // Getters and setters for all properties
    public static String getCompanyName() {
        return config.getProperty(COMPANY_NAME, "");
    }
    
    public static void setCompanyName(String name) {
        config.setProperty(COMPANY_NAME, name);
        saveConfiguration();
    }
    
    public static String getCompanyStreet() {
        return config.getProperty(COMPANY_STREET, "");
    }
    
    public static void setCompanyStreet(String street) {
        config.setProperty(COMPANY_STREET, street);
        saveConfiguration();
    }
    
    public static String getCompanyCity() {
        return config.getProperty(COMPANY_CITY, "");
    }
    
    public static void setCompanyCity(String city) {
        config.setProperty(COMPANY_CITY, city);
        saveConfiguration();
    }
    
    public static String getCompanyState() {
        return config.getProperty(COMPANY_STATE, "");
    }
    
    public static void setCompanyState(String state) {
        config.setProperty(COMPANY_STATE, state);
        saveConfiguration();
    }
    
    public static String getCompanyZip() {
        return config.getProperty(COMPANY_ZIP, "");
    }
    
    public static void setCompanyZip(String zip) {
        config.setProperty(COMPANY_ZIP, zip);
        saveConfiguration();
    }
    
    public static String getCompanyEmail() {
        return config.getProperty(COMPANY_EMAIL, "");
    }
    
    public static void setCompanyEmail(String email) {
        config.setProperty(COMPANY_EMAIL, email);
        saveConfiguration();
    }
    
    public static String getCompanyPhone() {
        return config.getProperty(COMPANY_PHONE, "");
    }
    
    public static void setCompanyPhone(String phone) {
        config.setProperty(COMPANY_PHONE, phone);
        saveConfiguration();
    }
    
    public static String getCompanyFax() {
        return config.getProperty(COMPANY_FAX, "");
    }
    
    public static void setCompanyFax(String fax) {
        config.setProperty(COMPANY_FAX, fax);
        saveConfiguration();
    }
    
    public static String getCompanyMC() {
        return config.getProperty(COMPANY_MC, "");
    }
    
    public static void setCompanyMC(String mc) {
        config.setProperty(COMPANY_MC, mc);
        saveConfiguration();
    }
    
    public static String getInvoicePrefix() {
        return config.getProperty(INVOICE_PREFIX, "INV");
    }
    
    public static void setInvoicePrefix(String prefix) {
        config.setProperty(INVOICE_PREFIX, prefix);
        saveConfiguration();
    }
    
    public static String getInvoiceTerms() {
        return config.getProperty(INVOICE_TERMS, "Net 30");
    }
    
    public static void setInvoiceTerms(String terms) {
        config.setProperty(INVOICE_TERMS, terms);
        saveConfiguration();
    }
    
    public static String getInvoiceNotes() {
        return config.getProperty(INVOICE_NOTES, "");
    }
    
    public static void setInvoiceNotes(String notes) {
        config.setProperty(INVOICE_NOTES, notes);
        saveConfiguration();
    }
    
    /**
     * Get full company address as single string
     */
    public static String getFullAddress() {
        return String.format("%s\n%s, %s %s",
            getCompanyStreet(),
            getCompanyCity(),
            getCompanyState(),
            getCompanyZip()
        );
    }
    
    /**
     * Get next invoice number
     */
    public static synchronized String getNextInvoiceNumber() {
        currentInvoiceNumber++;
        config.setProperty(INVOICE_START_NUMBER, String.valueOf(currentInvoiceNumber));
        saveConfiguration();
        return getInvoicePrefix() + "-" + String.format("%06d", currentInvoiceNumber);
    }
    
    /**
     * Get all configuration properties
     */
    public static Properties getAllProperties() {
        return new Properties(config);
    }
    
    /**
     * Reset to default configuration
     */
    public static void resetToDefaults() {
        loadConfiguration();
    }
}