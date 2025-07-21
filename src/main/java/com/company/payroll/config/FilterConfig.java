package com.company.payroll.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Filter Configuration Manager - Handles auto-saving and loading of date filters
 * for MaintenanceTab and CompanyExpenseTab to persist user preferences.
 */
public class FilterConfig {
    private static final Logger logger = LoggerFactory.getLogger(FilterConfig.class);
    private static final String CONFIG_FILE = "filter_config.properties";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    // Configuration keys
    private static final String MAINTENANCE_START_DATE = "maintenance.start_date";
    private static final String MAINTENANCE_END_DATE = "maintenance.end_date";
    private static final String EXPENSE_START_DATE = "expense.start_date";
    private static final String EXPENSE_END_DATE = "expense.end_date";
    
    private static Properties properties;
    
    static {
        loadProperties();
    }
    
    /**
     * Load properties from file
     */
    private static void loadProperties() {
        properties = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                logger.info("Loaded filter configuration from {}", CONFIG_FILE);
            } catch (IOException e) {
                logger.error("Failed to load filter configuration", e);
            }
        } else {
            logger.info("Filter configuration file not found, using defaults");
        }
    }
    
    /**
     * Save properties to file
     */
    private static void saveProperties() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Filter Configuration - Auto-saved date ranges");
            logger.debug("Saved filter configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save filter configuration", e);
        }
    }
    
    /**
     * Save maintenance tab date filters
     */
    public static void saveMaintenanceDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null) {
            properties.setProperty(MAINTENANCE_START_DATE, startDate.format(DATE_FORMATTER));
        } else {
            properties.remove(MAINTENANCE_START_DATE);
        }
        
        if (endDate != null) {
            properties.setProperty(MAINTENANCE_END_DATE, endDate.format(DATE_FORMATTER));
        } else {
            properties.remove(MAINTENANCE_END_DATE);
        }
        
        saveProperties();
        logger.debug("Saved maintenance date range: {} to {}", startDate, endDate);
    }
    
    /**
     * Load maintenance tab date filters
     */
    public static DateRange loadMaintenanceDateRange() {
        String startStr = properties.getProperty(MAINTENANCE_START_DATE);
        String endStr = properties.getProperty(MAINTENANCE_END_DATE);
        
        LocalDate startDate = null;
        LocalDate endDate = null;
        
        if (startStr != null) {
            try {
                startDate = LocalDate.parse(startStr, DATE_FORMATTER);
            } catch (Exception e) {
                logger.warn("Failed to parse maintenance start date: {}", startStr);
            }
        }
        
        if (endStr != null) {
            try {
                endDate = LocalDate.parse(endStr, DATE_FORMATTER);
            } catch (Exception e) {
                logger.warn("Failed to parse maintenance end date: {}", endStr);
            }
        }
        
        // Set defaults if no saved dates
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        logger.debug("Loaded maintenance date range: {} to {}", startDate, endDate);
        return new DateRange(startDate, endDate);
    }
    
    /**
     * Save expense tab date filters
     */
    public static void saveExpenseDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null) {
            properties.setProperty(EXPENSE_START_DATE, startDate.format(DATE_FORMATTER));
        } else {
            properties.remove(EXPENSE_START_DATE);
        }
        
        if (endDate != null) {
            properties.setProperty(EXPENSE_END_DATE, endDate.format(DATE_FORMATTER));
        } else {
            properties.remove(EXPENSE_END_DATE);
        }
        
        saveProperties();
        logger.debug("Saved expense date range: {} to {}", startDate, endDate);
    }
    
    /**
     * Load expense tab date filters
     */
    public static DateRange loadExpenseDateRange() {
        String startStr = properties.getProperty(EXPENSE_START_DATE);
        String endStr = properties.getProperty(EXPENSE_END_DATE);
        
        LocalDate startDate = null;
        LocalDate endDate = null;
        
        if (startStr != null) {
            try {
                startDate = LocalDate.parse(startStr, DATE_FORMATTER);
            } catch (Exception e) {
                logger.warn("Failed to parse expense start date: {}", startStr);
            }
        }
        
        if (endStr != null) {
            try {
                endDate = LocalDate.parse(endStr, DATE_FORMATTER);
            } catch (Exception e) {
                logger.warn("Failed to parse expense end date: {}", endStr);
            }
        }
        
        // Set defaults if no saved dates
        if (startDate == null) {
            startDate = LocalDate.now().withDayOfYear(1); // First day of current year
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        logger.debug("Loaded expense date range: {} to {}", startDate, endDate);
        return new DateRange(startDate, endDate);
    }
    
    /**
     * Clear all saved filter configurations
     */
    public static void clearAllFilters() {
        properties.clear();
        saveProperties();
        logger.info("Cleared all filter configurations");
    }
    
    /**
     * Date range container class
     */
    public static class DateRange {
        private final LocalDate startDate;
        private final LocalDate endDate;
        
        public DateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        public LocalDate getStartDate() {
            return startDate;
        }
        
        public LocalDate getEndDate() {
            return endDate;
        }
        
        @Override
        public String toString() {
            return "DateRange{startDate=" + startDate + ", endDate=" + endDate + "}";
        }
    }
} 