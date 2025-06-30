package com.company.payroll.maintenance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Enhanced MaintenanceRecord model that maintains backward compatibility while adding
 * comprehensive maintenance tracking features. Matches the inner class structure
 * used in MaintenanceTab.
 */
public class MaintenanceRecord {
    public enum VehicleType { TRUCK, TRAILER }
    
    // New enums for enhanced functionality
    public enum ServiceType { 
        PREVENTIVE, REPAIR, INSPECTION, EMERGENCY, WARRANTY, RECALL, OTHER 
    }
    
    public enum Priority { 
        LOW, MEDIUM, HIGH, CRITICAL 
    }
    
    public enum Status {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, OVERDUE
    }

    // Original fields
    private int id;
    private VehicleType vehicleType;
    private int vehicleId;
    private LocalDate serviceDate;
    private String description;
    private double cost;
    private LocalDate nextDue;
    private String receiptNumber;
    private String receiptPath;
    
    // New enhanced fields matching MaintenanceTab requirements
    private LocalDate date; // Alias for serviceDate for compatibility
    private String vehicle; // Vehicle number/name (e.g., "Truck #101")
    private String serviceType; // Service type string (e.g., "Oil Change", "Tire Rotation")
    private int mileage; // Current mileage at service
    private String technician; // Technician/mechanic name
    private String status; // Status string (e.g., "Completed", "Scheduled")
    private String notes; // Service notes
    
    // Additional enhanced fields
    private String vehicleNumber; // Truck/Trailer number for display
    private Priority priority;
    private String serviceProvider;
    private String providerLocation;
    private String providerPhone;
    private String workOrderNumber;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime completionTime;
    private double laborHours;
    private double laborCost;
    private double partsCost;
    private double taxAmount;
    private String performedBy; // Additional technician/helper
    private String authorizedBy;
    private int hoursAtService; // Engine hours
    private String partsUsed; // JSON or comma-separated list
    private String laborDescription;
    private String additionalNotes;
    private String warrantyInfo;
    private LocalDate warrantyExpiry;
    private boolean isWarrantyClaim;
    private String defectFound;
    private String correctiveAction;
    private String preventiveAction;
    private int downtimeHours;
    private double downtimeCost;
    private String attachedDocuments; // Additional document paths
    
    // Scheduled maintenance fields
    private LocalDate scheduledDate;
    private int scheduledMileage;
    private boolean isScheduledMaintenance;
    private String scheduleNotes;
    
    // Audit fields
    private LocalDateTime createdDate;
    private String createdBy;
    private LocalDateTime modifiedDate;
    private String modifiedBy;
    
    // Original constructor for backward compatibility
    public MaintenanceRecord(int id, VehicleType vehicleType, int vehicleId,
                             LocalDate serviceDate, String description, double cost,
                             LocalDate nextDue, String receiptNumber, String receiptPath) {
        this.id = id;
        this.vehicleType = vehicleType;
        this.vehicleId = vehicleId;
        this.serviceDate = serviceDate;
        this.date = serviceDate; // Set alias
        this.description = description;
        this.cost = cost;
        this.nextDue = nextDue;
        this.receiptNumber = receiptNumber;
        this.receiptPath = receiptPath;
        
        // Initialize new fields with defaults
        this.status = "Completed";
        this.priority = Priority.MEDIUM;
        this.mileage = 0;
        this.createdDate = LocalDateTime.now();
        this.createdBy = "mgubran1";
        this.modifiedDate = LocalDateTime.now();
        this.modifiedBy = "mgubran1";
        
        // Set vehicle name based on type and ID
        this.vehicle = (vehicleType == VehicleType.TRUCK ? "Truck #" : "Trailer #") + vehicleId;
        this.vehicleNumber = this.vehicle;
    }
    
    // Default constructor for MaintenanceTab compatibility
    public MaintenanceRecord() {
        this.vehicleType = VehicleType.TRUCK;
        this.status = "Completed";
        this.priority = Priority.MEDIUM;
        this.createdDate = LocalDateTime.now();
        this.createdBy = "mgubran1";
        this.modifiedDate = LocalDateTime.now();
        this.modifiedBy = "mgubran1";
    }
    
    // Constructor matching MaintenanceTab usage
    public MaintenanceRecord(LocalDate date, String vehicle, String serviceType,
                           int mileage, double cost, String technician, 
                           String status, String notes) {
        this();
        this.date = date;
        this.serviceDate = date;
        this.vehicle = vehicle;
        this.vehicleNumber = vehicle;
        this.serviceType = serviceType;
        this.description = serviceType;
        this.mileage = mileage;
        this.cost = cost;
        this.technician = technician;
        this.performedBy = technician;
        this.status = status;
        this.notes = notes;
        
        // Parse vehicle type from vehicle name
        if (vehicle != null && vehicle.toLowerCase().contains("truck")) {
            this.vehicleType = VehicleType.TRUCK;
        } else if (vehicle != null && vehicle.toLowerCase().contains("trailer")) {
            this.vehicleType = VehicleType.TRAILER;
        }
    }
    
    // Computed properties
    public boolean isOverdue() {
        if (nextDue == null || "Completed".equals(status)) {
            return false;
        }
        return nextDue.isBefore(LocalDate.now());
    }
    
    public long getDaysUntilDue() {
        if (nextDue == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(LocalDate.now(), nextDue);
    }
    
    public boolean isDueSoon() {
        long daysUntil = getDaysUntilDue();
        return daysUntil >= 0 && daysUntil <= 7;
    }
    
    public double getTotalCost() {
        return cost + laborCost + partsCost + taxAmount;
    }
    
    public boolean isCompleted() {
        return "Completed".equalsIgnoreCase(status);
    }
    
    public boolean isScheduled() {
        return "Scheduled".equalsIgnoreCase(status);
    }
    
    public boolean isInProgress() {
        return "In Progress".equalsIgnoreCase(status);
    }
    
    public String getPriorityDisplay() {
        if (priority == null) return "Medium";
        switch (priority) {
            case CRITICAL: return "Critical";
            case HIGH: return "High";
            case MEDIUM: return "Medium";
            case LOW: return "Low";
            default: return "Medium";
        }
    }
    
    // Original getters and setters
    public int getId() { return id; }
    public void setId(int id) { 
        this.id = id;
        updateModified();
    }
    
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { 
        this.vehicleType = vehicleType;
        updateModified();
    }
    
    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { 
        this.vehicleId = vehicleId;
        updateModified();
    }
    
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { 
        this.serviceDate = serviceDate;
        this.date = serviceDate; // Keep alias in sync
        updateModified();
    }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        updateModified();
    }
    
    public double getCost() { return cost; }
    public void setCost(double cost) { 
        this.cost = cost;
        updateModified();
    }
    
    public LocalDate getNextDue() { return nextDue; }
    public void setNextDue(LocalDate nextDue) { 
        this.nextDue = nextDue;
        updateModified();
    }
    
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { 
        this.receiptNumber = receiptNumber;
        updateModified();
    }
    
    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { 
        this.receiptPath = receiptPath;
        updateModified();
    }
    
    // MaintenanceTab compatibility getters/setters
    public LocalDate getDate() { return date != null ? date : serviceDate; }
    public void setDate(LocalDate date) { 
        this.date = date;
        this.serviceDate = date;
        updateModified();
    }
    
    public String getVehicle() { return vehicle; }
    public void setVehicle(String vehicle) { 
        this.vehicle = vehicle;
        this.vehicleNumber = vehicle;
        
        // Parse vehicle type
        if (vehicle != null) {
            if (vehicle.toLowerCase().contains("truck")) {
                this.vehicleType = VehicleType.TRUCK;
            } else if (vehicle.toLowerCase().contains("trailer")) {
                this.vehicleType = VehicleType.TRAILER;
            }
            
            // Try to extract ID
            String[] parts = vehicle.split("#");
            if (parts.length > 1) {
                try {
                    this.vehicleId = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        updateModified();
    }
    
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { 
        this.serviceType = serviceType;
        this.description = serviceType;
        updateModified();
    }
    
    public int getMileage() { return mileage; }
    public void setMileage(int mileage) { 
        this.mileage = mileage;
        updateModified();
    }
    
    public String getTechnician() { return technician; }
    public void setTechnician(String technician) { 
        this.technician = technician;
        this.performedBy = technician;
        updateModified();
    }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status;
        updateModified();
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { 
        this.notes = notes;
        updateModified();
    }
    
    // Additional enhanced getters/setters
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { 
        this.vehicleNumber = vehicleNumber;
        updateModified();
    }
    
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { 
        this.priority = priority;
        updateModified();
    }
    
    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { 
        this.serviceProvider = serviceProvider;
        updateModified();
    }
    
    public String getProviderLocation() { return providerLocation; }
    public void setProviderLocation(String providerLocation) { 
        this.providerLocation = providerLocation;
        updateModified();
    }
    
    public String getProviderPhone() { return providerPhone; }
    public void setProviderPhone(String providerPhone) { 
        this.providerPhone = providerPhone;
        updateModified();
    }
    
    public String getWorkOrderNumber() { return workOrderNumber; }
    public void setWorkOrderNumber(String workOrderNumber) { 
        this.workOrderNumber = workOrderNumber;
        updateModified();
    }
    
    public LocalDateTime getScheduledStartTime() { return scheduledStartTime; }
    public void setScheduledStartTime(LocalDateTime scheduledStartTime) { 
        this.scheduledStartTime = scheduledStartTime;
        updateModified();
    }
    
    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { 
        this.actualStartTime = actualStartTime;
        updateModified();
    }
    
    public LocalDateTime getCompletionTime() { return completionTime; }
    public void setCompletionTime(LocalDateTime completionTime) { 
        this.completionTime = completionTime;
        updateModified();
    }
    
    public double getLaborHours() { return laborHours; }
    public void setLaborHours(double laborHours) { 
        this.laborHours = laborHours;
        updateModified();
    }
    
    public double getLaborCost() { return laborCost; }
    public void setLaborCost(double laborCost) { 
        this.laborCost = laborCost;
        updateModified();
    }
    
    public double getPartsCost() { return partsCost; }
    public void setPartsCost(double partsCost) { 
        this.partsCost = partsCost;
        updateModified();
    }
    
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { 
        this.taxAmount = taxAmount;
        updateModified();
    }
    
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { 
        this.performedBy = performedBy;
        updateModified();
    }
    
    public String getAuthorizedBy() { return authorizedBy; }
    public void setAuthorizedBy(String authorizedBy) { 
        this.authorizedBy = authorizedBy;
        updateModified();
    }
    
    public int getHoursAtService() { return hoursAtService; }
    public void setHoursAtService(int hoursAtService) { 
        this.hoursAtService = hoursAtService;
        updateModified();
    }
    
    public String getPartsUsed() { return partsUsed; }
    public void setPartsUsed(String partsUsed) { 
        this.partsUsed = partsUsed;
        updateModified();
    }
    
    public String getLaborDescription() { return laborDescription; }
    public void setLaborDescription(String laborDescription) { 
        this.laborDescription = laborDescription;
        updateModified();
    }
    
    public String getAdditionalNotes() { return additionalNotes; }
    public void setAdditionalNotes(String additionalNotes) { 
        this.additionalNotes = additionalNotes;
        updateModified();
    }
    
    public String getWarrantyInfo() { return warrantyInfo; }
    public void setWarrantyInfo(String warrantyInfo) { 
        this.warrantyInfo = warrantyInfo;
        updateModified();
    }
    
    public LocalDate getWarrantyExpiry() { return warrantyExpiry; }
    public void setWarrantyExpiry(LocalDate warrantyExpiry) { 
        this.warrantyExpiry = warrantyExpiry;
        updateModified();
    }
    
    public boolean isWarrantyClaim() { return isWarrantyClaim; }
    public void setWarrantyClaim(boolean warrantyClaim) { 
        isWarrantyClaim = warrantyClaim;
        updateModified();
    }
    
    public String getDefectFound() { return defectFound; }
    public void setDefectFound(String defectFound) { 
        this.defectFound = defectFound;
        updateModified();
    }
    
    public String getCorrectiveAction() { return correctiveAction; }
    public void setCorrectiveAction(String correctiveAction) { 
        this.correctiveAction = correctiveAction;
        updateModified();
    }
    
    public String getPreventiveAction() { return preventiveAction; }
    public void setPreventiveAction(String preventiveAction) { 
        this.preventiveAction = preventiveAction;
        updateModified();
    }
    
    public int getDowntimeHours() { return downtimeHours; }
    public void setDowntimeHours(int downtimeHours) { 
        this.downtimeHours = downtimeHours;
        updateModified();
    }
    
    public double getDowntimeCost() { return downtimeCost; }
    public void setDowntimeCost(double downtimeCost) { 
        this.downtimeCost = downtimeCost;
        updateModified();
    }
    
    public String getAttachedDocuments() { return attachedDocuments; }
    public void setAttachedDocuments(String attachedDocuments) { 
        this.attachedDocuments = attachedDocuments;
        updateModified();
    }
    
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { 
        this.scheduledDate = scheduledDate;
        updateModified();
    }
    
    public int getScheduledMileage() { return scheduledMileage; }
    public void setScheduledMileage(int scheduledMileage) { 
        this.scheduledMileage = scheduledMileage;
        updateModified();
    }
    
    public boolean isScheduledMaintenance() { return isScheduledMaintenance; }
    public void setScheduledMaintenance(boolean scheduledMaintenance) { 
        isScheduledMaintenance = scheduledMaintenance;
        updateModified();
    }
    
    public String getScheduleNotes() { return scheduleNotes; }
    public void setScheduleNotes(String scheduleNotes) { 
        this.scheduleNotes = scheduleNotes;
        updateModified();
    }
    
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(LocalDateTime modifiedDate) { this.modifiedDate = modifiedDate; }
    
    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }
    
    // Helper method to update modification tracking
    private void updateModified() {
        this.modifiedDate = LocalDateTime.now();
        this.modifiedBy = "mgubran1";
    }
    
    @Override
    public String toString() {
        return String.format("%s - %s: %s ($%.2f) [%s]", 
            getDate(), vehicle, serviceType, cost, status);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaintenanceRecord that = (MaintenanceRecord) o;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}