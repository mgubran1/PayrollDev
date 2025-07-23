package com.company.payroll.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Configuration manager for document storage settings
 */
public class DocumentConfig {
    private static final Logger logger = LoggerFactory.getLogger(DocumentConfig.class);
    private static final String CONFIG_FILE = "document-config.json";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    // Configuration fields
    private String invoiceBasePath = "invoices";
    private boolean autoSaveEnabled = true;
    private FolderStructure folderStructure = FolderStructure.DRIVER_WEEK;
    private String fileNameTemplate = "{PO}_INVOICE_{DRIVER}";
    
    // Enum for folder structure options
    public enum FolderStructure {
        DRIVER_WEEK("Driver Name/Week #"),
        WEEK_DRIVER("Week #/Driver Name"),
        YEAR_MONTH_DRIVER("Year/Month/Driver Name"),
        DRIVER_YEAR_MONTH("Driver Name/Year/Month"),
        FLAT("No folders");
        
        private final String description;
        
        FolderStructure(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Static instance
    private static DocumentConfig instance;
    
    private DocumentConfig() {
        load();
    }
    
    public static synchronized DocumentConfig getInstance() {
        if (instance == null) {
            instance = new DocumentConfig();
        }
        return instance;
    }
    
    /**
     * Generate the full file path for an invoice
     */
    public File generateInvoicePath(String poNumber, String driverName, LocalDate date) {
        File baseDir = new File(invoiceBasePath);
        
        if (!autoSaveEnabled) {
            return null; // Let user choose manually
        }
        
        // Create folder structure
        File targetDir = baseDir;
        
        switch (folderStructure) {
            case DRIVER_WEEK:
                targetDir = new File(baseDir, sanitizeFileName(driverName));
                targetDir = new File(targetDir, "Week " + getWeekNumber(date));
                break;
                
            case WEEK_DRIVER:
                targetDir = new File(baseDir, "Week " + getWeekNumber(date));
                targetDir = new File(targetDir, sanitizeFileName(driverName));
                break;
                
            case YEAR_MONTH_DRIVER:
                targetDir = new File(baseDir, String.valueOf(date.getYear()));
                targetDir = new File(targetDir, date.getMonth().toString());
                targetDir = new File(targetDir, sanitizeFileName(driverName));
                break;
                
            case DRIVER_YEAR_MONTH:
                targetDir = new File(baseDir, sanitizeFileName(driverName));
                targetDir = new File(targetDir, String.valueOf(date.getYear()));
                targetDir = new File(targetDir, date.getMonth().toString());
                break;
                
            case FLAT:
                // Use base directory directly
                break;
        }
        
        // Create directories if they don't exist
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        // Generate filename
        String fileName = fileNameTemplate
            .replace("{PO}", sanitizeFileName(poNumber))
            .replace("{DRIVER}", sanitizeFileName(driverName))
            .replace("{DATE}", date.toString())
            .replace("{WEEK}", String.valueOf(getWeekNumber(date)))
            .replace("{YEAR}", String.valueOf(date.getYear()))
            .replace("{MONTH}", date.getMonth().toString());
            
        if (!fileName.endsWith(".pdf")) {
            fileName += ".pdf";
        }
        
        return new File(targetDir, fileName);
    }
    
    /**
     * Get week number for a date
     */
    private int getWeekNumber(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return date.get(weekFields.weekOfYear());
    }
    
    /**
     * Sanitize filename to remove invalid characters
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
    
    /**
     * Save configuration to file
     */
    public void save() {
        try {
            objectMapper.writeValue(new File(CONFIG_FILE), this);
            logger.info("Document configuration saved");
        } catch (IOException e) {
            logger.error("Failed to save document configuration", e);
        }
    }
    
    /**
     * Load configuration from file
     */
    private void load() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try {
                DocumentConfig loaded = objectMapper.readValue(configFile, DocumentConfig.class);
                if (loaded != null) {
                    this.invoiceBasePath = loaded.invoiceBasePath;
                    this.autoSaveEnabled = loaded.autoSaveEnabled;
                    this.folderStructure = loaded.folderStructure;
                    this.fileNameTemplate = loaded.fileNameTemplate;
                    logger.info("Document configuration loaded");
                }
            } catch (IOException e) {
                logger.error("Failed to load document configuration", e);
            }
        }
    }
    
    // Getters and setters
    public String getInvoiceBasePath() {
        return invoiceBasePath;
    }
    
    public void setInvoiceBasePath(String invoiceBasePath) {
        this.invoiceBasePath = invoiceBasePath;
    }
    
    public boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }
    
    public void setAutoSaveEnabled(boolean autoSaveEnabled) {
        this.autoSaveEnabled = autoSaveEnabled;
    }
    
    public FolderStructure getFolderStructure() {
        return folderStructure;
    }
    
    public void setFolderStructure(FolderStructure folderStructure) {
        this.folderStructure = folderStructure;
    }
    
    public String getFileNameTemplate() {
        return fileNameTemplate;
    }
    
    public void setFileNameTemplate(String fileNameTemplate) {
        this.fileNameTemplate = fileNameTemplate;
    }
} 