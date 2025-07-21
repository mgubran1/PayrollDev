package com.company.payroll.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Document Manager Configuration - Handles storage path configuration and folder organization
 * for both Maintenance and Company Expenses document management systems.
 */
public class DocumentManagerConfig {
    private static final Logger logger = LoggerFactory.getLogger(DocumentManagerConfig.class);
    
    // Configuration file
    private static final String CONFIG_FILE = "document_manager_config.properties";
    
    // Default storage paths
    private static final String DEFAULT_MAINTENANCE_PATH = "maintenance_documents";
    private static final String DEFAULT_EXPENSE_PATH = "expense_documents";
    private static final String DEFAULT_LOADS_PATH = "load_documents";
    private static final String DEFAULT_MERGED_LOADS_PATH = "MergedLoadDocuments";
    
    // Configuration keys
    private static final String MAINTENANCE_STORAGE_PATH = "maintenance.storage.path";
    private static final String EXPENSE_STORAGE_PATH = "expense.storage.path";
    private static final String LOADS_STORAGE_PATH = "loads.storage.path";
    private static final String MERGED_LOADS_PATH = "merged.loads.path";
    private static final String AUTO_CREATE_FOLDERS = "auto.create.folders";
    private static final String SANITIZE_FILENAMES = "sanitize.filenames";
    
    // Properties instance
    private static Properties config;
    
    static {
        loadConfiguration();
    }
    
    /**
     * Load configuration from file
     */
    private static void loadConfiguration() {
        config = new Properties();
        
        // Set default values
        config.setProperty(MAINTENANCE_STORAGE_PATH, DEFAULT_MAINTENANCE_PATH);
        config.setProperty(EXPENSE_STORAGE_PATH, DEFAULT_EXPENSE_PATH);
        config.setProperty(LOADS_STORAGE_PATH, DEFAULT_LOADS_PATH);
        config.setProperty(MERGED_LOADS_PATH, DEFAULT_MERGED_LOADS_PATH);
        config.setProperty(AUTO_CREATE_FOLDERS, "true");
        config.setProperty(SANITIZE_FILENAMES, "true");
        
        // Load from file if exists
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                logger.info("Document manager configuration loaded from file");
            } catch (Exception e) {
                logger.warn("Failed to load document manager config, using defaults: " + e.getMessage());
            }
        } else {
            // Create default config file
            saveConfiguration();
        }
    }
    
    /**
     * Save configuration to file
     */
    public static void saveConfiguration() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "Document Manager Configuration");
            logger.info("Document manager configuration saved");
        } catch (Exception e) {
            logger.error("Failed to save document manager configuration", e);
        }
    }
    
    /**
     * Get maintenance storage path
     */
    public static String getMaintenanceStoragePath() {
        return config.getProperty(MAINTENANCE_STORAGE_PATH, DEFAULT_MAINTENANCE_PATH);
    }
    
    /**
     * Set maintenance storage path
     */
    public static void setMaintenanceStoragePath(String path) {
        config.setProperty(MAINTENANCE_STORAGE_PATH, path);
        saveConfiguration();
    }
    
    /**
     * Get expense storage path
     */
    public static String getExpenseStoragePath() {
        return config.getProperty(EXPENSE_STORAGE_PATH, DEFAULT_EXPENSE_PATH);
    }
    
    /**
     * Set expense storage path
     */
    public static void setExpenseStoragePath(String path) {
        config.setProperty(EXPENSE_STORAGE_PATH, path);
        saveConfiguration();
    }
    
    /**
     * Get loads storage path
     */
    public static String getLoadsStoragePath() {
        return config.getProperty(LOADS_STORAGE_PATH, DEFAULT_LOADS_PATH);
    }
    
    /**
     * Set loads storage path
     */
    public static void setLoadsStoragePath(String path) {
        config.setProperty(LOADS_STORAGE_PATH, path);
        saveConfiguration();
    }
    
    /**
     * Get merged loads storage path
     */
    public static String getMergedLoadsPath() {
        return config.getProperty(MERGED_LOADS_PATH, DEFAULT_MERGED_LOADS_PATH);
    }
    
    /**
     * Set merged loads storage path
     */
    public static void setMergedLoadsPath(String path) {
        config.setProperty(MERGED_LOADS_PATH, path);
        saveConfiguration();
    }
    
    /**
     * Check if auto-create folders is enabled
     */
    public static boolean isAutoCreateFolders() {
        return Boolean.parseBoolean(config.getProperty(AUTO_CREATE_FOLDERS, "true"));
    }
    
    /**
     * Set auto-create folders setting
     */
    public static void setAutoCreateFolders(boolean enabled) {
        config.setProperty(AUTO_CREATE_FOLDERS, String.valueOf(enabled));
        saveConfiguration();
    }
    
    /**
     * Check if filename sanitization is enabled
     */
    public static boolean isSanitizeFilenames() {
        return Boolean.parseBoolean(config.getProperty(SANITIZE_FILENAMES, "true"));
    }
    
    /**
     * Set filename sanitization setting
     */
    public static void setSanitizeFilenames(boolean enabled) {
        config.setProperty(SANITIZE_FILENAMES, String.valueOf(enabled));
        saveConfiguration();
    }
    
    /**
     * Sanitize filename for safe storage
     */
    public static String sanitizeFilename(String filename) {
        if (!isSanitizeFilenames()) {
            return filename;
        }
        
        // Remove or replace invalid characters
        String sanitized = filename.replaceAll("[<>:\"/\\\\|?*]", "_");
        
        // Limit length
        if (sanitized.length() > 200) {
            String extension = "";
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, lastDot);
            }
            sanitized = sanitized.substring(0, 200 - extension.length()) + extension;
        }
        
        return sanitized.trim();
    }
    
    /**
     * Create maintenance document path structure
     * Format: {storage_path}/{unit_type}/{unit_number}/{invoice#}_{unit_type}_{service_type}.{extension}
     */
    public static Path createMaintenanceDocumentPath(String unitType, String unitNumber, 
                                                   String invoiceNumber, String serviceType, 
                                                   String originalFilename) {
        try {
            // Sanitize inputs
            String sanitizedUnitType = sanitizeFilename(unitType);
            String sanitizedUnitNumber = sanitizeFilename(unitNumber);
            String sanitizedInvoice = sanitizeFilename(invoiceNumber);
            String sanitizedServiceType = sanitizeFilename(serviceType);
            
            // Get file extension
            String extension = "";
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot > 0) {
                extension = originalFilename.substring(lastDot);
            }
            
            // Create filename: invoice#_unit_type_service_type.extension
            String filename = String.format("%s_%s_%s%s", 
                sanitizedInvoice, sanitizedUnitType, sanitizedServiceType, extension);
            
            // Create path structure
            Path basePath = Paths.get(getMaintenanceStoragePath());
            Path unitTypePath = basePath.resolve(sanitizedUnitType);
            Path unitNumberPath = unitTypePath.resolve(sanitizedUnitNumber);
            Path documentPath = unitNumberPath.resolve(filename);
            
            // Create directories if auto-create is enabled
            if (isAutoCreateFolders()) {
                Files.createDirectories(unitNumberPath);
            }
            
            return documentPath;
            
        } catch (Exception e) {
            logger.error("Failed to create maintenance document path", e);
            throw new RuntimeException("Failed to create document path", e);
        }
    }
    
    /**
     * Create expense document path structure
     * Format: {storage_path}/{category}/{department}/{receipt#}_{category}_{department}.{extension}
     */
    public static Path createExpenseDocumentPath(String category, String department, 
                                               String receiptNumber, String originalFilename) {
        try {
            // Sanitize inputs
            String sanitizedCategory = sanitizeFilename(category);
            String sanitizedDepartment = sanitizeFilename(department);
            String sanitizedReceipt = sanitizeFilename(receiptNumber);
            
            // Get file extension
            String extension = "";
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot > 0) {
                extension = originalFilename.substring(lastDot);
            }
            
            // Create filename: receipt#_category_department.extension
            String filename = String.format("%s_%s_%s%s", 
                sanitizedReceipt, sanitizedCategory, sanitizedDepartment, extension);
            
            // Create path structure
            Path basePath = Paths.get(getExpenseStoragePath());
            Path categoryPath = basePath.resolve(sanitizedCategory);
            Path departmentPath = categoryPath.resolve(sanitizedDepartment);
            Path documentPath = departmentPath.resolve(filename);
            
            // Create directories if auto-create is enabled
            if (isAutoCreateFolders()) {
                Files.createDirectories(departmentPath);
            }
            
            return documentPath;
            
        } catch (Exception e) {
            logger.error("Failed to create expense document path", e);
            throw new RuntimeException("Failed to create document path", e);
        }
    }
    
    /**
     * Get maintenance folder path for a unit
     */
    public static Path getMaintenanceFolderPath(String unitType, String unitNumber) {
        String sanitizedUnitType = sanitizeFilename(unitType);
        String sanitizedUnitNumber = sanitizeFilename(unitNumber);
        
        return Paths.get(getMaintenanceStoragePath())
                   .resolve(sanitizedUnitType)
                   .resolve(sanitizedUnitNumber);
    }
    
    /**
     * Get expense folder path for a category and department
     */
    public static Path getExpenseFolderPath(String category, String department) {
        String sanitizedCategory = sanitizeFilename(category);
        String sanitizedDepartment = sanitizeFilename(department);
        
        return Paths.get(getExpenseStoragePath())
                   .resolve(sanitizedCategory)
                   .resolve(sanitizedDepartment);
    }
    
    /**
     * Create loads document path structure
     * Format: {storage_path}/{driver_name}/Week_{week_number}/{doc_type}_{load_number}_{timestamp}.{extension}
     */
    public static Path createLoadsDocumentPath(String driverName, int weekNumber, String loadNumber, 
                                              String documentType, String originalFilename) {
        try {
            // Sanitize inputs
            String sanitizedDriverName = sanitizeFilename(driverName);
            String sanitizedLoadNumber = sanitizeFilename(loadNumber);
            String sanitizedDocType = sanitizeFilename(documentType);
            
            // Get file extension
            String extension = "";
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot > 0) {
                extension = originalFilename.substring(lastDot);
            }
            
            // Create filename: doc_type_load_number_timestamp.extension
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = String.format("%s_%s_%s%s", 
                sanitizedDocType, sanitizedLoadNumber, timestamp, extension);
            
            // Create path structure: Loads/Driver_Name/Week_#/filename
            Path basePath = Paths.get(getLoadsStoragePath());
            Path driverPath = basePath.resolve(sanitizedDriverName);
            Path weekPath = driverPath.resolve("Week_" + weekNumber);
            Path documentPath = weekPath.resolve(filename);
            
            // Create directories if auto-create is enabled
            if (isAutoCreateFolders()) {
                Files.createDirectories(weekPath);
            }
            
            return documentPath;
            
        } catch (Exception e) {
            logger.error("Failed to create loads document path", e);
            throw new RuntimeException("Failed to create document path", e);
        }
    }
    
    /**
     * Get loads folder path for a driver and week number
     */
    public static Path getLoadsFolderPath(String driverName, int weekNumber) {
        String sanitizedDriverName = sanitizeFilename(driverName);
        
        return Paths.get(getLoadsStoragePath())
                   .resolve(sanitizedDriverName)
                   .resolve("Week_" + weekNumber);
    }
    
    /**
     * Create merged loads document path
     * Format: {merged_path}/All_Loads/Week_{week_number}/{filename}
     */
    public static Path createMergedLoadsPath(int weekNumber, String filename) {
        try {
            String sanitizedFilename = sanitizeFilename(filename);
            
            // Create path structure: MergedLoadDocuments/All_Loads/Week_#/filename
            Path basePath = Paths.get(getMergedLoadsPath());
            Path allLoadsPath = basePath.resolve("All_Loads");
            Path weekPath = allLoadsPath.resolve("Week_" + weekNumber);
            Path documentPath = weekPath.resolve(sanitizedFilename);
            
            // Create directories if auto-create is enabled
            if (isAutoCreateFolders()) {
                Files.createDirectories(weekPath);
            }
            
            return documentPath;
            
        } catch (Exception e) {
            logger.error("Failed to create merged loads path", e);
            throw new RuntimeException("Failed to create merged loads path", e);
        }
    }
    
    /**
     * List all documents in a maintenance folder
     */
    public static java.util.List<String> listMaintenanceDocuments(String unitType, String unitNumber) {
        try {
            Path folderPath = getMaintenanceFolderPath(unitType, unitNumber);
            if (Files.exists(folderPath)) {
                return Files.list(folderPath)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Failed to list maintenance documents", e);
        }
        return java.util.Collections.emptyList();
    }
    
    /**
     * List all documents in an expense folder
     */
    public static java.util.List<String> listExpenseDocuments(String category, String department) {
        try {
            Path folderPath = getExpenseFolderPath(category, department);
            if (Files.exists(folderPath)) {
                return Files.list(folderPath)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Failed to list expense documents", e);
        }
        return java.util.Collections.emptyList();
    }
    
    /**
     * List all documents in a loads folder
     */
    public static java.util.List<String> listLoadsDocuments(String driverName, int weekNumber) {
        try {
            Path folderPath = getLoadsFolderPath(driverName, weekNumber);
            if (Files.exists(folderPath)) {
                return Files.list(folderPath)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Failed to list loads documents", e);
        }
        return java.util.Collections.emptyList();
    }
    
    /**
     * Get current week number (1-52)
     */
    public static int getCurrentWeekNumber() {
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault());
        return now.get(weekFields.weekOfWeekBasedYear());
    }
    
    /**
     * Open folder in system file explorer
     */
    public static void openFolder(Path folderPath) {
        try {
            if (Files.exists(folderPath)) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(folderPath.toFile());
                } else {
                    logger.warn("Desktop not supported for opening folders");
                }
            } else {
                logger.warn("Folder does not exist: " + folderPath);
            }
        } catch (Exception e) {
            logger.error("Failed to open folder", e);
        }
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
        config.clear();
        config.setProperty(MAINTENANCE_STORAGE_PATH, DEFAULT_MAINTENANCE_PATH);
        config.setProperty(EXPENSE_STORAGE_PATH, DEFAULT_EXPENSE_PATH);
        config.setProperty(LOADS_STORAGE_PATH, DEFAULT_LOADS_PATH);
        config.setProperty(MERGED_LOADS_PATH, DEFAULT_MERGED_LOADS_PATH);
        config.setProperty(AUTO_CREATE_FOLDERS, "true");
        config.setProperty(SANITIZE_FILENAMES, "true");
        saveConfiguration();
    }
} 