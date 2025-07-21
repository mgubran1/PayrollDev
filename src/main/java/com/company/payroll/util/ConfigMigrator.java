package com.company.payroll.util;

import java.io.*;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to migrate configuration files and remove deprecated properties
 */
public class ConfigMigrator {
    private static final Logger logger = LoggerFactory.getLogger(ConfigMigrator.class);
    private static final String CONFIG_FILE = "load_confirmation_config.properties";
    
    public static void migrateLoadConfirmationConfig() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }
        
        try {
            Properties props = new Properties();
            
            // Load existing properties
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }
            
            // Remove deprecated landscape.orientation property
            if (props.containsKey("landscape.orientation")) {
                props.remove("landscape.orientation");
                logger.info("Removed deprecated landscape.orientation property");
            }
            
            // Add fit.to.page if not present
            if (!props.containsKey("fit.to.page")) {
                props.setProperty("fit.to.page", "true");
                logger.info("Added fit.to.page property with default value true");
            }
            
            // Save updated properties
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "Load Confirmation Configuration");
                logger.info("Configuration migration completed successfully");
            }
            
        } catch (IOException e) {
            logger.error("Error during configuration migration: {}", e.getMessage(), e);
        }
    }
}