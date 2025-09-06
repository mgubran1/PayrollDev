package com.company.payroll.loads;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.PaymentType;
import com.company.payroll.trailers.Trailer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Load {
    /**
     * Represents the lifecycle status of a load.
     * ASSIGNED indicates a load has been matched with a driver
     * but has not yet started transit.
     */
    public enum Status { BOOKED, ASSIGNED, IN_TRANSIT, DELIVERED, PAID, CANCELLED, PICKUP_LATE, DELIVERY_LATE }

    private int id;
    private String loadNumber;
    private String poNumber;
    private String customer;  // Customer for pickup location
    private String customer2; // Customer for drop location (new field)
    private String billTo;    // Customer to bill for the load
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
    private LocalDate pickUpDate;
    private LocalTime pickUpTime;  // NEW FIELD
    private LocalDate deliveryDate;
    private LocalTime deliveryTime;  // NEW FIELD
    private String reminder;
    private boolean hasLumper;
    private double lumperAmount;
    private boolean hasRevisedRateConfirmation;
    private List<LoadDocument> documents;
    private List<LoadLocation> locations; // NEW: Multiple locations support
    
    // Additional fields for the missing methods
    private String pickupCity;
    private String pickupState;
    private String deliveryCity;
    private String deliveryState;
    private double driverRate;
    
    // Zip code and mileage fields
    private String pickupZipCode;
    private String deliveryZipCode;
    private double calculatedMiles;
    private LocalDateTime milesCalculationDate;
    private PaymentType paymentMethodUsed;
    private double calculatedDriverPay;
    private double paymentRateUsed;
    
    // Flat rate amount specific to this load (enterprise-ready)
    private double flatRateAmount;

    public Load(int id, String loadNumber, String poNumber, String customer, String customer2, String billTo, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, Status status, double grossAmount, String notes, 
                LocalDate pickUpDate, LocalTime pickUpTime, LocalDate deliveryDate, LocalTime deliveryTime, 
                String reminder, boolean hasLumper, double lumperAmount, boolean hasRevisedRateConfirmation) {
        this.id = id;
        this.loadNumber = loadNumber;
        this.poNumber = poNumber;
        this.customer = customer;
        this.customer2 = customer2;
        this.billTo = billTo;
        this.pickUpLocation = pickUpLocation;
        this.dropLocation = dropLocation;
        this.driver = driver;
        this.truckUnitSnapshot = truckUnitSnapshot;
        this.status = status;
        this.grossAmount = grossAmount;
        this.notes = notes;
        this.pickUpDate = pickUpDate;
        this.pickUpTime = pickUpTime;
        this.deliveryDate = deliveryDate;
        this.deliveryTime = deliveryTime;
        this.reminder = reminder;
        this.hasLumper = hasLumper;
        this.lumperAmount = lumperAmount;
        this.hasRevisedRateConfirmation = hasRevisedRateConfirmation;
        this.documents = new ArrayList<>();
        
        // Initialize trailer fields
        this.trailerId = 0;
        this.trailerNumber = "";
        this.trailer = null;
        
        // Parse locations if possible
        parseLocations();
    }
    
    // Backward compatible constructor without customer2 and billTo
    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, Status status, double grossAmount, String notes, 
                LocalDate pickUpDate, LocalTime pickUpTime, LocalDate deliveryDate, LocalTime deliveryTime, 
                String reminder, boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this(id, loadNumber, poNumber, customer, null, null, pickUpLocation, dropLocation, driver, truckUnitSnapshot, 
             status, grossAmount, notes, pickUpDate, pickUpTime, deliveryDate, deliveryTime, 
             reminder, hasLumper, 0.0, hasRevisedRateConfirmation);
    }

    // Constructor with trailer parameters
    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, int trailerId, String trailerNumber, Status status, 
                double grossAmount, String notes, LocalDate pickUpDate, LocalTime pickUpTime, 
                LocalDate deliveryDate, LocalTime deliveryTime, String reminder, 
                boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, truckUnitSnapshot, 
            status, grossAmount, notes, pickUpDate, pickUpTime, deliveryDate, deliveryTime, 
            reminder, hasLumper, hasRevisedRateConfirmation);
        this.trailerId = trailerId;
        this.trailerNumber = trailerNumber;
    }

    // Backwards compatible constructors - updated to include null times
    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, Status status, double grossAmount, String notes, 
                LocalDate pickUpDate, LocalDate deliveryDate, String reminder, boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, truckUnitSnapshot, 
             status, grossAmount, notes, pickUpDate, null, deliveryDate, null, reminder, hasLumper, hasRevisedRateConfirmation);
    }

    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, String truckUnitSnapshot, Status status, double grossAmount, String notes, LocalDate deliveryDate,
                String reminder, boolean hasLumper, boolean hasRevisedRateConfirmation) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, truckUnitSnapshot, 
             status, grossAmount, notes, null, null, deliveryDate, null, reminder, hasLumper, hasRevisedRateConfirmation);
    }

    public Load(int id, String loadNumber, String poNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, Status status, double grossAmount, String notes, LocalDate deliveryDate) {
        this(id, loadNumber, poNumber, customer, pickUpLocation, dropLocation, driver, 
             driver != null ? driver.getTruckUnit() : "", status, grossAmount, notes, null, null, deliveryDate, null, "", false, false);
    }

    public Load(int id, String loadNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, Status status, double grossAmount, String notes, LocalDate deliveryDate) {
        this(id, loadNumber, "", customer, pickUpLocation, dropLocation, driver, 
             driver != null ? driver.getTruckUnit() : "", status, grossAmount, notes, null, null, deliveryDate, null, "", false, false);
    }

    public Load(int id, String loadNumber, String customer, String pickUpLocation, String dropLocation,
                Employee driver, Status status, double grossAmount, String notes) {
        this(id, loadNumber, "", customer, pickUpLocation, dropLocation, driver, 
             driver != null ? driver.getTruckUnit() : "", status, grossAmount, notes, null, null, null, null, "", false, false);
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
        
        // Initialize zip code fields
        this.pickupZipCode = "";
        this.deliveryZipCode = "";
        this.calculatedMiles = 0.0;
        this.milesCalculationDate = null;
        this.paymentMethodUsed = null;
        this.calculatedDriverPay = 0.0;
        this.paymentRateUsed = 0.0;
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
    public String getCustomer2() { return customer2; }
    public void setCustomer2(String customer2) { this.customer2 = customer2; }
    public String getBillTo() { return billTo; }
    public void setBillTo(String billTo) { this.billTo = billTo; }
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
    public LocalDate getPickUpDate() { return pickUpDate; }
    public void setPickUpDate(LocalDate pickUpDate) { this.pickUpDate = pickUpDate; }
    public LocalTime getPickUpTime() { return pickUpTime; }  // NEW GETTER
    public void setPickUpTime(LocalTime pickUpTime) { this.pickUpTime = pickUpTime; }  // NEW SETTER
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public LocalTime getDeliveryTime() { return deliveryTime; }  // NEW GETTER
    public void setDeliveryTime(LocalTime deliveryTime) { this.deliveryTime = deliveryTime; }  // NEW SETTER
    public String getReminder() { return reminder; }
    public void setReminder(String reminder) { this.reminder = reminder; }
    public boolean isHasLumper() { return hasLumper; }
    public void setHasLumper(boolean hasLumper) { this.hasLumper = hasLumper; }
    public double getLumperAmount() { return lumperAmount; }
    public void setLumperAmount(double lumperAmount) { this.lumperAmount = lumperAmount; }
    public boolean isHasRevisedRateConfirmation() { return hasRevisedRateConfirmation; }
    public void setHasRevisedRateConfirmation(boolean hasRevisedRateConfirmation) { this.hasRevisedRateConfirmation = hasRevisedRateConfirmation; }
    public List<LoadDocument> getDocuments() { return documents; }
    public void setDocuments(List<LoadDocument> documents) { this.documents = documents; }
    
    // Location management methods
    public List<LoadLocation> getLocations() { 
        if (locations == null) {
            locations = new ArrayList<>();
        }
        return locations; 
    }
    public void setLocations(List<LoadLocation> locations) { this.locations = locations; }
    
    public List<LoadLocation> getPickupLocations() {
        return getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(Collectors.toList());
    }
    
    public List<LoadLocation> getDropLocations() {
        return getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(Collectors.toList());
    }
    
    public void addLocation(LoadLocation location) {
        if (locations == null) {
            locations = new ArrayList<>();
        }
        location.setLoadId(this.id);
        locations.add(location);
    }
    
    public void removeLocation(LoadLocation location) {
        if (locations != null) {
            locations.remove(location);
        }
    }
    
    public void clearLocations() {
        if (locations != null) {
            locations.clear();
        }
    }
    
    // Backward compatibility methods
    public String getPrimaryPickupLocation() {
        List<LoadLocation> pickups = getPickupLocations();
        if (!pickups.isEmpty()) {
            return pickups.get(0).getCompleteAddress();
        }
        return pickUpLocation != null ? pickUpLocation : "";
    }
    
    public String getPrimaryDropLocation() {
        List<LoadLocation> drops = getDropLocations();
        if (!drops.isEmpty()) {
            return drops.get(0).getCompleteAddress();
        }
        return dropLocation != null ? dropLocation : "";
    }

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
    
    // Helper method to check if load has multiple stops
    public boolean hasMultipleStops() {
        return getLocations() != null && getLocations().size() > 0;
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

    // Zip code and mileage getters and setters
    public String getPickupZipCode() { return pickupZipCode; }
    public void setPickupZipCode(String pickupZipCode) { 
        this.pickupZipCode = normalizeZipCode(pickupZipCode);
    }
    
    public String getDeliveryZipCode() { return deliveryZipCode; }
    public void setDeliveryZipCode(String deliveryZipCode) { 
        this.deliveryZipCode = normalizeZipCode(deliveryZipCode);
    }
    
    public double getCalculatedMiles() { return calculatedMiles; }
    public void setCalculatedMiles(double calculatedMiles) { 
        this.calculatedMiles = calculatedMiles;
        this.milesCalculationDate = LocalDateTime.now();
    }
    
    public LocalDateTime getMilesCalculationDate() { return milesCalculationDate; }
    public void setMilesCalculationDate(LocalDateTime milesCalculationDate) { 
        this.milesCalculationDate = milesCalculationDate;
    }
    
    public PaymentType getPaymentMethodUsed() { return paymentMethodUsed; }
    public void setPaymentMethodUsed(PaymentType paymentMethodUsed) { 
        this.paymentMethodUsed = paymentMethodUsed;
    }
    
    public double getCalculatedDriverPay() { return calculatedDriverPay; }
    public void setCalculatedDriverPay(double calculatedDriverPay) { 
        this.calculatedDriverPay = calculatedDriverPay;
    }
    
    public double getPaymentRateUsed() { return paymentRateUsed; }
    public void setPaymentRateUsed(double paymentRateUsed) { 
        this.paymentRateUsed = paymentRateUsed;
    }
    
    public double getFlatRateAmount() { return flatRateAmount; }
    public void setFlatRateAmount(double flatRateAmount) { 
        this.flatRateAmount = flatRateAmount;
    }
    
    // Zip code validation and utility methods
    public boolean hasValidZipCodes() {
        return isValidZipCode(pickupZipCode) && isValidZipCode(deliveryZipCode);
    }
    
    public static boolean isValidZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            return false;
        }
        
        // Pattern for 5-digit or 5+4 digit zip codes
        Pattern pattern = Pattern.compile("^\\d{5}(-\\d{4})?$");
        Matcher matcher = pattern.matcher(zipCode.trim());
        return matcher.matches();
    }
    
    private String normalizeZipCode(String zipCode) {
        if (zipCode == null) {
            return "";
        }
        return zipCode.trim().replaceAll("[^0-9-]", "");
    }
    
    public boolean requiresMileageCalculation() {
        return driver != null && 
               driver.getPaymentType() == PaymentType.PER_MILE && 
               hasValidZipCodes() && 
               (calculatedMiles == 0 || milesCalculationDate == null);
    }
    
    public String getFormattedPickupZip() {
        return formatZipCode(pickupZipCode);
    }
    
    public String getFormattedDeliveryZip() {
        return formatZipCode(deliveryZipCode);
    }
    
    private String formatZipCode(String zipCode) {
        if (zipCode == null || zipCode.isEmpty()) {
            return "";
        }
        
        // Format as 5 digits or 5+4 format
        if (zipCode.length() == 9 && !zipCode.contains("-")) {
            return zipCode.substring(0, 5) + "-" + zipCode.substring(5);
        }
        
        return zipCode;
    }
    
    public String getDistanceDescription() {
        if (calculatedMiles <= 0) {
            return "Not calculated";
        }
        return String.format("%.1f miles", calculatedMiles);
    }
    
    public boolean hasMileageData() {
        return calculatedMiles > 0 && milesCalculationDate != null;
    }
    
    public String getPaymentMethodDescription() {
        if (paymentMethodUsed == null) {
            return "Not specified";
        }
        
        switch (paymentMethodUsed) {
            case PERCENTAGE:
                return String.format("Percentage (%.2f%%)", paymentRateUsed);
            case FLAT_RATE:
                return String.format("Flat Rate ($%.2f)", paymentRateUsed);
            case PER_MILE:
                return String.format("Per Mile ($%.2f/mile)", paymentRateUsed);
            default:
                return paymentMethodUsed.getDisplayName();
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