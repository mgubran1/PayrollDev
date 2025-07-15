package com.company.payroll.fuel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Configuration class for fuel import column mapping
 * Allows flexible column mapping for CSV/XLSX imports
 */
public class FuelImportConfig {
    private static final Logger logger = LoggerFactory.getLogger(FuelImportConfig.class);
    private static final String CONFIG_FILE = "fuel_import_config.dat";
    
    private Map<String, String> columnMappings;
    
    public FuelImportConfig() {
        this.columnMappings = new LinkedHashMap<>();
    }
    
    /**
     * Load default configuration (matches current hardcoded logic)
     */
    public static FuelImportConfig loadDefault() {
        FuelImportConfig config = new FuelImportConfig();
        
        // Default column mappings (matches current hardcoded logic)
        config.columnMappings.put("Card Number", "card #");
        config.columnMappings.put("Transaction Date", "tran date");
        config.columnMappings.put("Transaction Time", "tran time");
        config.columnMappings.put("Invoice", "invoice");
        config.columnMappings.put("Unit", "unit");
        config.columnMappings.put("Driver Name", "driver name");
        config.columnMappings.put("Odometer", "odometer");
        config.columnMappings.put("Location Name", "location name");
        config.columnMappings.put("City", "city");
        config.columnMappings.put("State/Province", "state/ prov");
        config.columnMappings.put("Fees", "fees");
        config.columnMappings.put("Item", "item");
        config.columnMappings.put("Unit Price", "unit price");
        config.columnMappings.put("Discount PPU", "disc ppu");
        config.columnMappings.put("Discount Cost", "disc cost");
        config.columnMappings.put("Quantity", "qty");
        config.columnMappings.put("Discount Amount", "disc amt");
        config.columnMappings.put("Discount Type", "disc type");
        config.columnMappings.put("Amount", "amt");
        config.columnMappings.put("DB", "db");
        config.columnMappings.put("Currency", "currency");
        
        logger.info("Loaded default fuel import configuration");
        return config;
    }
    
    /**
     * Load configuration from file
     */
    public static FuelImportConfig load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                FuelImportConfig config = (FuelImportConfig) ois.readObject();
                logger.info("Loaded fuel import configuration from {}", CONFIG_FILE);
                return config;
            } catch (Exception e) {
                logger.warn("Failed to load fuel import configuration, using defaults: {}", e.getMessage());
            }
        }
        
        // Return default if file doesn't exist or load fails
        return loadDefault();
    }
    
    /**
     * Save configuration to file
     */
    public void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CONFIG_FILE))) {
            oos.writeObject(this);
            logger.info("Saved fuel import configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save fuel import configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get column mapping for a field
     */
    public String getColumnMapping(String fieldName) {
        return columnMappings.getOrDefault(fieldName, "");
    }
    
    /**
     * Set column mapping for a field
     */
    public void setColumnMapping(String fieldName, String columnHeader) {
        columnMappings.put(fieldName, columnHeader);
    }
    
    /**
     * Get all column mappings
     */
    public Map<String, String> getColumnMappings() {
        return new LinkedHashMap<>(columnMappings);
    }
    
    /**
     * Get column index from header row using configured mappings
     */
    public Map<String, Integer> getColumnIndices(String[] headers) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            
            // Find which field this header maps to
            for (Map.Entry<String, String> entry : columnMappings.entrySet()) {
                String fieldName = entry.getKey();
                String expectedHeader = entry.getValue().toLowerCase();
                
                if (header.equals(expectedHeader)) {
                    indices.put(fieldName, i);
                    break;
                }
            }
        }
        
        logger.debug("Column indices mapping: {}", indices);
        return indices;
    }
    
    /**
     * Get column index from header row using configured mappings (for XLSX)
     */
    public Map<String, Integer> getColumnIndices(Map<String, Integer> headerMap) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (Map.Entry<String, String> entry : columnMappings.entrySet()) {
            String fieldName = entry.getKey();
            String expectedHeader = entry.getValue().toLowerCase();
            
            Integer index = headerMap.get(expectedHeader);
            if (index != null) {
                indices.put(fieldName, index);
            }
        }
        
        logger.debug("Column indices mapping: {}", indices);
        return indices;
    }
} 