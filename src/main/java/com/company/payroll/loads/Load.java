package com.company.payroll.loads;

import com.company.payroll.employees.Employee;
import com.company.payroll.trailers.Trailer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Load {
    /**
     * Represents the lifecycle status of a load.
     * ASSIGNED indicates a load has been matched with a driver
     * but has not yet started transit.
     */
    public enum Status { BOOKED, ASSIGNED, IN_TRANSIT, DELIVERED, PAID, CANCELLED }

    private int id;
    private String loadNumber;
    private String poNumber;
    private String customer;
    private String pickUpLocation;
    private String dropLocation;
    private Employee driver;
    private String truckUnitSnapshot; // Snapshot of truck unit at time of load creation
    
    // Trailer relationship
    private int trailerId;
    private String trailerNumber;
    private Trailer trailer;
    
    private Status status;
    private double grossAmount;
    private String notes;
    private LocalDate pickUpDate;  // NEW FIELD
    private LocalDate deliveryDate;
    private String reminder;
    private boolean hasLumper;
    private boolean hasRevisedRateConfirmation;
    private List<LoadDocument> documents;
    
    // Additional fields for the missing methods
    private String pickupCity;
    private String pickupState;
    private String deliveryCity;
    private String deliveryState;
    private double driverRate;

    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, Status status, double grossAmount, String notes, 
                LocalDate pickUpDate, LocalDate deliveryDate, String reminder, boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this.id = id;
        this.loadNumber = loadNumber;
        this.poNumber = poNumber;
        this.customer = customer;
        this.pickUpLocation = pickUpLocation;
        this.dropLocation = dropLocation;
        this.driver = driver;
        this.truckUnitSnapshot = truckUnitSnapshot;
        this.status = status;
        this.grossAmount = grossAmount;
        this.notes = notes;
        this.pickUpDate = pickUpDate;
        this.deliveryDate = deliveryDate;
        this.reminder = reminder;
        this.hasLumper = hasLumper;
        this.hasRevisedRateConfirmation = hasRevisedRateConfirmation;
        this.documents = new ArrayList<>();
        
        // Initialize trailer fields
        this.trailerId = 0;
        this.trailerNumber = "";
        this.trailer = null;
        
        // Parse locations if possible
        parseLocations();
    }

    // Constructor with trailer parameters
    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, int trailerId, String trailerNumber, Status status, 
                double grossAmount, String notes, LocalDate pickUpDate, LocalDate deliveryDate, String reminder, 
                boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, truckUnitSnapshot, 
            status, grossAmount, notes, pickUpDate, deliveryDate, reminder, hasLumper, hasRevisedRateConfirmation);
        this.trailerId = trailerId;
        this.trailerNumber = trailerNumber;
    }

    // Backwards compatible constructors
    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, Status status, double grossAmount, String notes, LocalDate deliveryDate,
                String reminder, boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, truckUnitSnapshot, 
             status, grossAmount, notes, null, deliveryDate, reminder, hasLumper, hasRevisedRateConfirmation);
    }

    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, Status status, double grossAmount, String notes, LocalDate deliveryDate) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, 
             driver != null ? driver.getTruckUnit() : "", status, grossAmount, notes, null, deliveryDate, "", false, false);
    }

    public Load(int id, String loadNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, Status status, double grossAmount, String notes, LocalDate deliveryDate) {
        this(id, loadNumber, "", customer, pickUpLocation, dropLocation, driver, 
             driver != null ? driver.getTruckUnit() : "", status, grossAmount, notes, null, deliveryDate, "", false, false);
    }

    public Load(int id, String loadNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, Status status, double grossAmount, String notes) {
        this(id, loadNumber, "", customer, pickUpLocation, dropLocation, driver, 
             driver != null ? driver.getTruckUnit() : "", status, grossAmount, notes, null, null, "", false, false);
    }

    // Helper method to parse locations
    private void parseLocations() {
        if (pickUpLocation != null && pickUpLocation.contains(",")) {
            String[] parts = pickUpLocation.split(",", 2);
            if (parts.length == 2) {
                this.pickupCity = parts[0].trim();
                this.pickupState = parts[1].trim();
            } else {
                this.pickupCity = pickUpLocation;
                this.pickupState = "";
            }
        } else {
            this.pickupCity = pickUpLocation != null ? pickUpLocation : "";
            this.pickupState = "";
        }
        
        if (dropLocation != null && dropLocation.contains(",")) {
            String[] parts = dropLocation.split(",", 2);
            if (parts.length == 2) {
                this.deliveryCity = parts[0].trim();
                this.deliveryState = parts[1].trim();
            } else {
                this.deliveryCity = dropLocation;
                this.deliveryState = "";
            }
        } else {
            this.deliveryCity = dropLocation != null ? dropLocation : "";
            this.deliveryState = "";
        }
        
        // Calculate driver rate as a percentage of gross amount (default 75%)
        this.driverRate = this.grossAmount * 0.75;
    }

    // Getters and setters for existing fields
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getLoadNumber() { return loadNumber; }
    public void setLoadNumber(String loadNumber) { this.loadNumber = loadNumber; }
    public String getPONumber() { return poNumber; }
    public void setPONumber(String poNumber) { this.poNumber = poNumber; }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    public String getPickUpLocation() { return pickUpLocation; }
    public void setPickUpLocation(String pickUpLocation) { 
        this.pickUpLocation = pickUpLocation; 
        parseLocations();
    }
    public String getDropLocation() { return dropLocation; }
    public void setDropLocation(String dropLocation) { 
        this.dropLocation = dropLocation; 
        parseLocations();
    }
    public Employee getDriver() { return driver; }
    public void setDriver(Employee driver) { this.driver = driver; }
    public String getTruckUnitSnapshot() { return truckUnitSnapshot; }
    public void setTruckUnitSnapshot(String truckUnitSnapshot) { this.truckUnitSnapshot = truckUnitSnapshot; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getGrossAmount() { return grossAmount; }
    public void setGrossAmount(double grossAmount) { 
        this.grossAmount = grossAmount; 
        // Update driver rate when gross amount changes
        this.driverRate = grossAmount * 0.75;
    }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getPickUpDate() { return pickUpDate; }  // NEW GETTER
    public void setPickUpDate(LocalDate pickUpDate) { this.pickUpDate = pickUpDate; }  // NEW SETTER
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public String getReminder() { return reminder; }
    public void setReminder(String reminder) { this.reminder = reminder; }
    public boolean isHasLumper() { return hasLumper; }
    public void setHasLumper(boolean hasLumper) { this.hasLumper = hasLumper; }
    public boolean isHasRevisedRateConfirmation() { return hasRevisedRateConfirmation; }
    public void setHasRevisedRateConfirmation(boolean hasRevisedRateConfirmation) { this.hasRevisedRateConfirmation = hasRevisedRateConfirmation; }
    public List<LoadDocument> getDocuments() { return documents; }
    public void setDocuments(List<LoadDocument> documents) { this.documents = documents; }

    // Getters and setters for trailer fields
    public int getTrailerId() { return trailerId; }
    public void setTrailerId(int trailerId) { this.trailerId = trailerId; }
    public String getTrailerNumber() { return trailerNumber; }
    public void setTrailerNumber(String trailerNumber) { this.trailerNumber = trailerNumber; }
    public Trailer getTrailer() { return trailer; }
    public void setTrailer(Trailer trailer) { 
        this.trailer = trailer; 
        if (trailer != null) {
            this.trailerId = trailer.getId();
            this.trailerNumber = trailer.getTrailerNumber();
        }
    }

    // NEW GETTERS AND SETTERS FOR MISSING METHODS
    public String getPickupCity() { 
        return pickupCity != null ? pickupCity : "";
    }
    
    public void setPickupCity(String pickupCity) {
        this.pickupCity = pickupCity;
        updatePickUpLocation();
    }
    
    public String getPickupState() { 
        return pickupState != null ? pickupState : "";
    }
    
    public void setPickupState(String pickupState) {
        this.pickupState = pickupState;
        updatePickUpLocation();
    }
    
    public String getDeliveryCity() { 
        return deliveryCity != null ? deliveryCity : "";
    }
    
    public void setDeliveryCity(String deliveryCity) {
        this.deliveryCity = deliveryCity;
        updateDropLocation();
    }
    
    public String getDeliveryState() { 
        return deliveryState != null ? deliveryState : "";
    }
    
    public void setDeliveryState(String deliveryState) {
        this.deliveryState = deliveryState;
        updateDropLocation();
    }
    
    public double getDriverRate() { 
        return driverRate;
    }
    
    public void setDriverRate(double driverRate) {
        this.driverRate = driverRate;
    }
    
    // Helper methods to update location strings when city/state are set individually
    private void updatePickUpLocation() {
        if (pickupCity != null && !pickupCity.isEmpty()) {
            if (pickupState != null && !pickupState.isEmpty()) {
                this.pickUpLocation = pickupCity + ", " + pickupState;
            } else {
                this.pickUpLocation = pickupCity;
            }
        }
    }
    
    private void updateDropLocation() {
        if (deliveryCity != null && !deliveryCity.isEmpty()) {
            if (deliveryState != null && !deliveryState.isEmpty()) {
                this.dropLocation = deliveryCity + ", " + deliveryState;
            } else {
                this.dropLocation = deliveryCity;
            }
        }
    }

    // Convenience methods
    public LocalDate getLoadDate() { return deliveryDate; }
    public double getAmount() { return getGrossAmount(); }

    @Override
    public String toString() {
        return loadNumber + " (PO: " + poNumber + ") - " + customer + " (" + status + ")";
    }

    // Inner class for documents
    public static class LoadDocument {
        public enum DocumentType { RATE_CONFIRMATION, BOL, POD, LUMPER, OTHER }
        
        private int id;
        private int loadId;
        private String fileName;
        private String filePath;
        private DocumentType type;
        private LocalDate uploadDate;
        
        public LoadDocument(int id, int loadId, String fileName, String filePath, DocumentType type, LocalDate uploadDate) {
            this.id = id;
            this.loadId = loadId;
            this.fileName = fileName;
            this.filePath = filePath;
            this.type = type;
            this.uploadDate = uploadDate;
        }
        
        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getLoadId() { return loadId; }
        public void setLoadId(int loadId) { this.loadId = loadId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public DocumentType getType() { return type; }
        public void setType(DocumentType type) { this.type = type; }
        public LocalDate getUploadDate() { return uploadDate; }
        public void setUploadDate(LocalDate uploadDate) { this.uploadDate = uploadDate; }
    }
}