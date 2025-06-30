package com.company.payroll.trailers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import javafx.beans.property.*;

/**
 * Model class representing a trailer in the fleet management system.
 */
public class Trailer {
    // Primary properties
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty trailerNumber = new SimpleStringProperty();
    private final StringProperty vin = new SimpleStringProperty();
    private final StringProperty make = new SimpleStringProperty();
    private final StringProperty model = new SimpleStringProperty();
    private final IntegerProperty year = new SimpleIntegerProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.ACTIVE);
    private final StringProperty licensePlate = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> registrationExpiryDate = new SimpleObjectProperty<>();
    private final StringProperty currentLocation = new SimpleStringProperty();
    
    // Technical specifications
    private final DoubleProperty length = new SimpleDoubleProperty();
    private final DoubleProperty width = new SimpleDoubleProperty();
    private final DoubleProperty height = new SimpleDoubleProperty();
    private final DoubleProperty maxWeight = new SimpleDoubleProperty();
    private final DoubleProperty emptyWeight = new SimpleDoubleProperty();
    private final IntegerProperty axleCount = new SimpleIntegerProperty(2);
    private final StringProperty suspensionType = new SimpleStringProperty();
    private final BooleanProperty hasThermalUnit = new SimpleBooleanProperty(false);
    private final StringProperty thermalUnitDetails = new SimpleStringProperty();
    
    // Financial and ownership
    private final StringProperty ownershipType = new SimpleStringProperty("Company"); // Company, Leased, Owner-Operator
    private final DoubleProperty purchasePrice = new SimpleDoubleProperty();
    private final ObjectProperty<LocalDate> purchaseDate = new SimpleObjectProperty<>();
    private final DoubleProperty currentValue = new SimpleDoubleProperty();
    private final DoubleProperty monthlyLeaseCost = new SimpleDoubleProperty();
    private final StringProperty leaseDetails = new SimpleStringProperty();
    private final StringProperty insurancePolicyNumber = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> insuranceExpiryDate = new SimpleObjectProperty<>();
    
    // Maintenance related
    private final IntegerProperty odometerReading = new SimpleIntegerProperty();
    private final ObjectProperty<LocalDate> lastInspectionDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> nextInspectionDueDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> lastServiceDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> nextServiceDueDate = new SimpleObjectProperty<>();
    private final StringProperty currentCondition = new SimpleStringProperty("Good");
    private final StringProperty maintenanceNotes = new SimpleStringProperty();
    
    // Usage and assignment
    private final StringProperty assignedDriver = new SimpleStringProperty();
    private final StringProperty assignedTruck = new SimpleStringProperty();
    private final BooleanProperty isAssigned = new SimpleBooleanProperty(false);
    private final StringProperty currentJobId = new SimpleStringProperty();
    
    // Tracking
    private final ObjectProperty<LocalDateTime> lastUpdated = new SimpleObjectProperty<>(LocalDateTime.now());
    private final StringProperty updatedBy = new SimpleStringProperty("mgubran1");
    private final StringProperty notes = new SimpleStringProperty();
    
    // Enums
    public enum Status {
        ACTIVE, 
        IN_MAINTENANCE, 
        OUT_OF_SERVICE, 
        IN_TRANSIT, 
        AVAILABLE, 
        RESERVED,
        DECOMMISSIONED
    }
    
    // Constructors
    public Trailer() {
        // Default constructor
    }
    
    public Trailer(String trailerNumber) {
        this.trailerNumber.set(trailerNumber);
    }
    
    public Trailer(String trailerNumber, String vin, String make, String model, int year) {
        this.trailerNumber.set(trailerNumber);
        this.vin.set(vin);
        this.make.set(make);
        this.model.set(model);
        this.year.set(year);
    }
    
    // Computed properties
    
    public int getAge() {
        return (year.get() > 0) ? LocalDate.now().getYear() - year.get() : 0;
    }
    
    public boolean isRegistrationExpired() {
        LocalDate expiry = registrationExpiryDate.get();
        return expiry != null && expiry.isBefore(LocalDate.now());
    }
    
    public boolean isInsuranceExpired() {
        LocalDate expiry = insuranceExpiryDate.get();
        return expiry != null && expiry.isBefore(LocalDate.now());
    }
    
    public long getDaysUntilRegistrationExpiry() {
        LocalDate expiry = registrationExpiryDate.get();
        return expiry != null ? ChronoUnit.DAYS.between(LocalDate.now(), expiry) : 0;
    }
    
    public long getDaysUntilInsuranceExpiry() {
        LocalDate expiry = insuranceExpiryDate.get();
        return expiry != null ? ChronoUnit.DAYS.between(LocalDate.now(), expiry) : 0;
    }
    
    public boolean isInspectionDue() {
        LocalDate nextDue = nextInspectionDueDate.get();
        return nextDue != null && (nextDue.isBefore(LocalDate.now()) || nextDue.isEqual(LocalDate.now()));
    }
    
    public boolean isServiceDue() {
        LocalDate nextDue = nextServiceDueDate.get();
        return nextDue != null && (nextDue.isBefore(LocalDate.now()) || nextDue.isEqual(LocalDate.now()));
    }
    
    public double getPayload() {
        return maxWeight.get() - emptyWeight.get();
    }
    
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        sb.append(trailerNumber.get());
        
        if (make.get() != null && !make.get().isEmpty()) {
            sb.append(" - ").append(make.get());
        }
        
        if (model.get() != null && !model.get().isEmpty()) {
            sb.append(" ").append(model.get());
        }
        
        if (year.get() > 0) {
            sb.append(" (").append(year.get()).append(")");
        }
        
        return sb.toString();
    }
    
    // Property getters
    public IntegerProperty idProperty() {
        return id;
    }
    
    public StringProperty trailerNumberProperty() {
        return trailerNumber;
    }
    
    public StringProperty vinProperty() {
        return vin;
    }
    
    public StringProperty makeProperty() {
        return make;
    }
    
    public StringProperty modelProperty() {
        return model;
    }
    
    public IntegerProperty yearProperty() {
        return year;
    }
    
    public StringProperty typeProperty() {
        return type;
    }
    
    public ObjectProperty<Status> statusProperty() {
        return status;
    }
    
    public StringProperty licensePlateProperty() {
        return licensePlate;
    }
    
    public ObjectProperty<LocalDate> registrationExpiryDateProperty() {
        return registrationExpiryDate;
    }
    
    public StringProperty currentLocationProperty() {
        return currentLocation;
    }
    
    public DoubleProperty lengthProperty() {
        return length;
    }
    
    public DoubleProperty widthProperty() {
        return width;
    }
    
    public DoubleProperty heightProperty() {
        return height;
    }
    
    public DoubleProperty maxWeightProperty() {
        return maxWeight;
    }
    
    public DoubleProperty emptyWeightProperty() {
        return emptyWeight;
    }
    
    public IntegerProperty axleCountProperty() {
        return axleCount;
    }
    
    public StringProperty suspensionTypeProperty() {
        return suspensionType;
    }
    
    public BooleanProperty hasThermalUnitProperty() {
        return hasThermalUnit;
    }
    
    public StringProperty thermalUnitDetailsProperty() {
        return thermalUnitDetails;
    }
    
    public StringProperty ownershipTypeProperty() {
        return ownershipType;
    }
    
    public DoubleProperty purchasePriceProperty() {
        return purchasePrice;
    }
    
    public ObjectProperty<LocalDate> purchaseDateProperty() {
        return purchaseDate;
    }
    
    public DoubleProperty currentValueProperty() {
        return currentValue;
    }
    
    public DoubleProperty monthlyLeaseCostProperty() {
        return monthlyLeaseCost;
    }
    
    public StringProperty leaseDetailsProperty() {
        return leaseDetails;
    }
    
    public StringProperty insurancePolicyNumberProperty() {
        return insurancePolicyNumber;
    }
    
    public ObjectProperty<LocalDate> insuranceExpiryDateProperty() {
        return insuranceExpiryDate;
    }
    
    public IntegerProperty odometerReadingProperty() {
        return odometerReading;
    }
    
    public ObjectProperty<LocalDate> lastInspectionDateProperty() {
        return lastInspectionDate;
    }
    
    public ObjectProperty<LocalDate> nextInspectionDueDateProperty() {
        return nextInspectionDueDate;
    }
    
    public ObjectProperty<LocalDate> lastServiceDateProperty() {
        return lastServiceDate;
    }
    
    public ObjectProperty<LocalDate> nextServiceDueDateProperty() {
        return nextServiceDueDate;
    }
    
    public StringProperty currentConditionProperty() {
        return currentCondition;
    }
    
    public StringProperty maintenanceNotesProperty() {
        return maintenanceNotes;
    }
    
    public StringProperty assignedDriverProperty() {
        return assignedDriver;
    }
    
    public StringProperty assignedTruckProperty() {
        return assignedTruck;
    }
    
    public BooleanProperty isAssignedProperty() {
        return isAssigned;
    }
    
    public StringProperty currentJobIdProperty() {
        return currentJobId;
    }
    
    public ObjectProperty<LocalDateTime> lastUpdatedProperty() {
        return lastUpdated;
    }
    
    public StringProperty updatedByProperty() {
        return updatedBy;
    }
    
    public StringProperty notesProperty() {
        return notes;
    }
    
    // Regular getters and setters
    
    public int getId() {
        return id.get();
    }
    
    public void setId(int id) {
        this.id.set(id);
        updateLastModified();
    }
    
    public String getTrailerNumber() {
        return trailerNumber.get();
    }
    
    public void setTrailerNumber(String trailerNumber) {
        this.trailerNumber.set(trailerNumber);
        updateLastModified();
    }
    
    public String getVin() {
        return vin.get();
    }
    
    public void setVin(String vin) {
        this.vin.set(vin);
        updateLastModified();
    }
    
    public String getMake() {
        return make.get();
    }
    
    public void setMake(String make) {
        this.make.set(make);
        updateLastModified();
    }
    
    public String getModel() {
        return model.get();
    }
    
    public void setModel(String model) {
        this.model.set(model);
        updateLastModified();
    }
    
    public int getYear() {
        return year.get();
    }
    
    public void setYear(int year) {
        this.year.set(year);
        updateLastModified();
    }
    
    public String getType() {
        return type.get();
    }
    
    public void setType(String type) {
        this.type.set(type);
        updateLastModified();
    }
    
    public Status getStatus() {
        return status.get();
    }
    
    public void setStatus(Status status) {
        this.status.set(status);
        updateLastModified();
    }
    
    public String getLicensePlate() {
        return licensePlate.get();
    }
    
    public void setLicensePlate(String licensePlate) {
        this.licensePlate.set(licensePlate);
        updateLastModified();
    }
    
    public LocalDate getRegistrationExpiryDate() {
        return registrationExpiryDate.get();
    }
    
    public void setRegistrationExpiryDate(LocalDate registrationExpiryDate) {
        this.registrationExpiryDate.set(registrationExpiryDate);
        updateLastModified();
    }
    
    public String getCurrentLocation() {
        return currentLocation.get();
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation.set(currentLocation);
        updateLastModified();
    }
    
    public double getLength() {
        return length.get();
    }
    
    public void setLength(double length) {
        this.length.set(length);
        updateLastModified();
    }
    
    public double getWidth() {
        return width.get();
    }
    
    public void setWidth(double width) {
        this.width.set(width);
        updateLastModified();
    }
    
    public double getHeight() {
        return height.get();
    }
    
    public void setHeight(double height) {
        this.height.set(height);
        updateLastModified();
    }
    
    public double getMaxWeight() {
        return maxWeight.get();
    }
    
    public void setMaxWeight(double maxWeight) {
        this.maxWeight.set(maxWeight);
        updateLastModified();
    }
    
    public double getEmptyWeight() {
        return emptyWeight.get();
    }
    
    public void setEmptyWeight(double emptyWeight) {
        this.emptyWeight.set(emptyWeight);
        updateLastModified();
    }
    
    public int getAxleCount() {
        return axleCount.get();
    }
    
    public void setAxleCount(int axleCount) {
        this.axleCount.set(axleCount);
        updateLastModified();
    }
    
    public String getSuspensionType() {
        return suspensionType.get();
    }
    
    public void setSuspensionType(String suspensionType) {
        this.suspensionType.set(suspensionType);
        updateLastModified();
    }
    
    public boolean isHasThermalUnit() {
        return hasThermalUnit.get();
    }
    
    public void setHasThermalUnit(boolean hasThermalUnit) {
        this.hasThermalUnit.set(hasThermalUnit);
        updateLastModified();
    }
    
    public String getThermalUnitDetails() {
        return thermalUnitDetails.get();
    }
    
    public void setThermalUnitDetails(String thermalUnitDetails) {
        this.thermalUnitDetails.set(thermalUnitDetails);
        updateLastModified();
    }
    
    public String getOwnershipType() {
        return ownershipType.get();
    }
    
    public void setOwnershipType(String ownershipType) {
        this.ownershipType.set(ownershipType);
        updateLastModified();
    }
    
    public double getPurchasePrice() {
        return purchasePrice.get();
    }
    
    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice.set(purchasePrice);
        updateLastModified();
    }
    
    public LocalDate getPurchaseDate() {
        return purchaseDate.get();
    }
    
    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate.set(purchaseDate);
        updateLastModified();
    }
    
    public double getCurrentValue() {
        return currentValue.get();
    }
    
    public void setCurrentValue(double currentValue) {
        this.currentValue.set(currentValue);
        updateLastModified();
    }
    
    public double getMonthlyLeaseCost() {
        return monthlyLeaseCost.get();
    }
    
    public void setMonthlyLeaseCost(double monthlyLeaseCost) {
        this.monthlyLeaseCost.set(monthlyLeaseCost);
        updateLastModified();
    }
    
    public String getLeaseDetails() {
        return leaseDetails.get();
    }
    
    public void setLeaseDetails(String leaseDetails) {
        this.leaseDetails.set(leaseDetails);
        updateLastModified();
    }
    
    public String getInsurancePolicyNumber() {
        return insurancePolicyNumber.get();
    }
    
    public void setInsurancePolicyNumber(String insurancePolicyNumber) {
        this.insurancePolicyNumber.set(insurancePolicyNumber);
        updateLastModified();
    }
    
    public LocalDate getInsuranceExpiryDate() {
        return insuranceExpiryDate.get();
    }
    
    public void setInsuranceExpiryDate(LocalDate insuranceExpiryDate) {
        this.insuranceExpiryDate.set(insuranceExpiryDate);
        updateLastModified();
    }
    
    public int getOdometerReading() {
        return odometerReading.get();
    }
    
    public void setOdometerReading(int odometerReading) {
        this.odometerReading.set(odometerReading);
        updateLastModified();
    }
    
    public LocalDate getLastInspectionDate() {
        return lastInspectionDate.get();
    }
    
    public void setLastInspectionDate(LocalDate lastInspectionDate) {
        this.lastInspectionDate.set(lastInspectionDate);
        updateLastModified();
    }
    
    public LocalDate getNextInspectionDueDate() {
        return nextInspectionDueDate.get();
    }
    
    public void setNextInspectionDueDate(LocalDate nextInspectionDueDate) {
        this.nextInspectionDueDate.set(nextInspectionDueDate);
        updateLastModified();
    }
    
    public LocalDate getLastServiceDate() {
        return lastServiceDate.get();
    }
    
    public void setLastServiceDate(LocalDate lastServiceDate) {
        this.lastServiceDate.set(lastServiceDate);
        updateLastModified();
    }
    
    public LocalDate getNextServiceDueDate() {
        return nextServiceDueDate.get();
    }
    
    public void setNextServiceDueDate(LocalDate nextServiceDueDate) {
        this.nextServiceDueDate.set(nextServiceDueDate);
        updateLastModified();
    }
    
    public String getCurrentCondition() {
        return currentCondition.get();
    }
    
    public void setCurrentCondition(String currentCondition) {
        this.currentCondition.set(currentCondition);
        updateLastModified();
    }
    
    public String getMaintenanceNotes() {
        return maintenanceNotes.get();
    }
    
    public void setMaintenanceNotes(String maintenanceNotes) {
        this.maintenanceNotes.set(maintenanceNotes);
        updateLastModified();
    }
    
    public String getAssignedDriver() {
        return assignedDriver.get();
    }
    
    public void setAssignedDriver(String assignedDriver) {
        this.assignedDriver.set(assignedDriver);
        this.isAssigned.set(assignedDriver != null && !assignedDriver.isEmpty());
        updateLastModified();
    }
    
    public String getAssignedTruck() {
        return assignedTruck.get();
    }
    
    public void setAssignedTruck(String assignedTruck) {
        this.assignedTruck.set(assignedTruck);
        updateLastModified();
    }
    
    public boolean isAssigned() {
        return isAssigned.get();
    }
    
    public void setAssigned(boolean assigned) {
        isAssigned.set(assigned);
        updateLastModified();
    }
    
    public String getCurrentJobId() {
        return currentJobId.get();
    }
    
    public void setCurrentJobId(String currentJobId) {
        this.currentJobId.set(currentJobId);
        updateLastModified();
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated.get();
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated.set(lastUpdated);
    }
    
    public String getUpdatedBy() {
        return updatedBy.get();
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy.set(updatedBy);
    }
    
    public String getNotes() {
        return notes.get();
    }
    
    public void setNotes(String notes) {
        this.notes.set(notes);
        updateLastModified();
    }
    
    // Helper methods
    private void updateLastModified() {
        lastUpdated.set(LocalDateTime.now());
        updatedBy.set("mgubran1"); // Current user
    }
    
    @Override
    public String toString() {
        return String.format("Trailer #%s (%d %s %s)", 
            getTrailerNumber(), 
            getYear(), 
            getMake(), 
            getModel());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trailer trailer = (Trailer) o;
        return Objects.equals(getTrailerNumber(), trailer.getTrailerNumber()) ||
               (getId() > 0 && getId() == trailer.getId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getTrailerNumber(), getId());
    }
}