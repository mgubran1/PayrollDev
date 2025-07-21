package com.company.payroll.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Configuration class for DOT compliance requirements
 * Allows flexible configuration of required documents for trucks, trailers, and employees
 */
public class DOTComplianceConfig {
    private static final Logger logger = LoggerFactory.getLogger(DOTComplianceConfig.class);
    private static final String CONFIG_FILE = "dot_compliance_config.dat";
    
    // Document categories and their default requirements
    private Map<String, Boolean> truckRequirements;
    private Map<String, Boolean> trailerRequirements;
    private Map<String, Boolean> employeeRequirements;
    
    // Document storage paths
    private String truckDocumentPath;
    private String trailerDocumentPath;
    private String employeeDocumentPath;
    
    // Singleton instance
    private static DOTComplianceConfig instance;
    
    private DOTComplianceConfig() {
        initializeDefaults();
        load();
    }
    
    public static DOTComplianceConfig getInstance() {
        if (instance == null) {
            instance = new DOTComplianceConfig();
        }
        return instance;
    }
    
    /**
     * Initialize default compliance requirements
     */
    private void initializeDefaults() {
        truckRequirements = new LinkedHashMap<>();
        trailerRequirements = new LinkedHashMap<>();
        employeeRequirements = new LinkedHashMap<>();
        
        // Default truck requirements (DOT critical)
        truckRequirements.put("Annual DOT Inspection", true);
        truckRequirements.put("Truck Registration", true);
        truckRequirements.put("IRP Registration", true);
        truckRequirements.put("IFTA Documentation", true);
        truckRequirements.put("DOT Cab Card", true);
        truckRequirements.put("Insurance Documents", true);
        truckRequirements.put("Title or Lease Agreements", true);
        truckRequirements.put("Preventive Maintenance Records", false);
        truckRequirements.put("Brake System Inspection", false);
        truckRequirements.put("Tire Inspection Logs", false);
        truckRequirements.put("Emissions Compliance", false);
        truckRequirements.put("Truck Repair Receipts", false);
        truckRequirements.put("DVIRs", false);
        truckRequirements.put("Other Document", false);
        
        // Default trailer requirements
        trailerRequirements.put("Annual DOT Inspection", true);
        trailerRequirements.put("Registration Document", true);
        trailerRequirements.put("Insurance Documents", true);
        trailerRequirements.put("Trailer DOT Inspection", false);
        trailerRequirements.put("Lease Agreement Expiry", false);
        trailerRequirements.put("Trailer Lease Expiry", false);
        trailerRequirements.put("Tire Inspection or Replacement Records", false);
        trailerRequirements.put("Other Document", false);
        
        // Default employee requirements - DOT mandatory and recommended
        employeeRequirements.put("CDL License", true);
        employeeRequirements.put("Medical Certificate", true);
        employeeRequirements.put("Drug Test Results", true);
        employeeRequirements.put("Physical Examination", true);
        employeeRequirements.put("Background Check", true);
        employeeRequirements.put("Training Certificates", false);
        employeeRequirements.put("Safety Training", false);
        employeeRequirements.put("Hazmat Endorsement", false);
        employeeRequirements.put("Employment Application", false);
        employeeRequirements.put("Resume", false);
        employeeRequirements.put("Company Policies Acknowledgement", false);
        employeeRequirements.put("Driver's License Verification", false);
        employeeRequirements.put("PSP Report", false);
        employeeRequirements.put("MVR Report", false);
        employeeRequirements.put("Hours of Service Training", false);
        employeeRequirements.put("Defensive Driving Certificate", false);
        employeeRequirements.put("Pre-employment Drug Test", true);
        employeeRequirements.put("Random Drug Test", false);
        employeeRequirements.put("Post-accident Drug Test", false);
        employeeRequirements.put("Driver Onboarding Checklist", false);
        employeeRequirements.put("Other Document", false);
        
        // Default document paths
        truckDocumentPath = "truck_documents";
        trailerDocumentPath = "trailer_documents";
        employeeDocumentPath = "employee_documents";
    }
    
    /**
     * Load configuration from file
     */
    public void load() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(configFile))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) ois.readObject();
                
                truckRequirements = (Map<String, Boolean>) data.get("truckRequirements");
                trailerRequirements = (Map<String, Boolean>) data.get("trailerRequirements");
                employeeRequirements = (Map<String, Boolean>) data.get("employeeRequirements");
                truckDocumentPath = (String) data.get("truckDocumentPath");
                trailerDocumentPath = (String) data.get("trailerDocumentPath");
                employeeDocumentPath = (String) data.get("employeeDocumentPath");
                
                logger.info("DOT compliance configuration loaded successfully");
            } catch (Exception e) {
                logger.error("Error loading DOT compliance configuration: {}", e.getMessage(), e);
                // Keep defaults if loading fails
            }
        } else {
            logger.info("DOT compliance configuration file not found, using defaults");
        }
    }
    
    /**
     * Save configuration to file
     */
    public void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CONFIG_FILE))) {
            Map<String, Object> data = new HashMap<>();
            data.put("truckRequirements", truckRequirements);
            data.put("trailerRequirements", trailerRequirements);
            data.put("employeeRequirements", employeeRequirements);
            data.put("truckDocumentPath", truckDocumentPath);
            data.put("trailerDocumentPath", trailerDocumentPath);
            data.put("employeeDocumentPath", employeeDocumentPath);
            
            oos.writeObject(data);
            logger.info("DOT compliance configuration saved successfully");
        } catch (IOException e) {
            logger.error("Error saving DOT compliance configuration: {}", e.getMessage(), e);
        }
    }
    
    // Getters and setters for truck requirements
    public Map<String, Boolean> getTruckRequirements() {
        return new LinkedHashMap<>(truckRequirements);
    }
    
    public void setTruckRequirements(Map<String, Boolean> requirements) {
        this.truckRequirements = new LinkedHashMap<>(requirements);
    }
    
    public boolean isTruckDocumentRequired(String documentType) {
        return truckRequirements.getOrDefault(documentType, false);
    }
    
    public void setTruckDocumentRequired(String documentType, boolean required) {
        truckRequirements.put(documentType, required);
    }
    
    // Getters and setters for trailer requirements
    public Map<String, Boolean> getTrailerRequirements() {
        return new LinkedHashMap<>(trailerRequirements);
    }
    
    public void setTrailerRequirements(Map<String, Boolean> requirements) {
        this.trailerRequirements = new LinkedHashMap<>(requirements);
    }
    
    public boolean isTrailerDocumentRequired(String documentType) {
        return trailerRequirements.getOrDefault(documentType, false);
    }
    
    public void setTrailerDocumentRequired(String documentType, boolean required) {
        trailerRequirements.put(documentType, required);
    }
    
    // Getters and setters for employee requirements
    public Map<String, Boolean> getEmployeeRequirements() {
        return new LinkedHashMap<>(employeeRequirements);
    }
    
    public void setEmployeeRequirements(Map<String, Boolean> requirements) {
        this.employeeRequirements = new LinkedHashMap<>(requirements);
    }
    
    public boolean isEmployeeDocumentRequired(String documentType) {
        return employeeRequirements.getOrDefault(documentType, false);
    }
    
    public void setEmployeeDocumentRequired(String documentType, boolean required) {
        employeeRequirements.put(documentType, required);
    }
    
    // Document path getters and setters
    public String getTruckDocumentPath() {
        return truckDocumentPath;
    }
    
    public void setTruckDocumentPath(String path) {
        this.truckDocumentPath = path;
    }
    
    public String getTrailerDocumentPath() {
        return trailerDocumentPath;
    }
    
    public void setTrailerDocumentPath(String path) {
        this.trailerDocumentPath = path;
    }
    
    public String getEmployeeDocumentPath() {
        return employeeDocumentPath;
    }
    
    public void setEmployeeDocumentPath(String path) {
        this.employeeDocumentPath = path;
    }
    
    /**
     * Get all required document types for a vehicle type
     */
    public List<String> getRequiredDocuments(String vehicleType) {
        Map<String, Boolean> requirements;
        switch (vehicleType.toLowerCase()) {
            case "truck":
                requirements = truckRequirements;
                break;
            case "trailer":
                requirements = trailerRequirements;
                break;
            case "employee":
                requirements = employeeRequirements;
                break;
            default:
                return new ArrayList<>();
        }
        
        return requirements.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }
    
    /**
     * Check if a vehicle is compliant based on available documents
     */
    public boolean isCompliant(String vehicleType, Set<String> availableDocuments) {
        List<String> requiredDocs = getRequiredDocuments(vehicleType);
        return requiredDocs.stream().allMatch(availableDocuments::contains);
    }
    
    /**
     * Get missing required documents for a vehicle
     */
    public List<String> getMissingDocuments(String vehicleType, Set<String> availableDocuments) {
        List<String> requiredDocs = getRequiredDocuments(vehicleType);
        return requiredDocs.stream()
                .filter(doc -> !availableDocuments.contains(doc))
                .toList();
    }
    
    /**
     * Notify all tabs that configuration has changed
     */
    public void notifyConfigurationChanged() {
        // This method can be extended to notify all tabs when configuration changes
        logger.info("DOT compliance configuration changed - all tabs should refresh");
    }
} 