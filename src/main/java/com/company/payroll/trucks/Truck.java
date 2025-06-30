package com.company.payroll.trucks;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Model class representing a truck in the fleet management system.
 * Provides all necessary properties for truck management and tracking.
 */
public class Truck {
    // Primary identification properties
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty number = new SimpleStringProperty();
    private final StringProperty vin = new SimpleStringProperty();
    private final StringProperty make = new SimpleStringProperty();
    private final StringProperty model = new SimpleStringProperty();
    private final IntegerProperty year = new SimpleIntegerProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty("Available");
    private final StringProperty licensePlate = new SimpleStringProperty();
    
    // Assignment and location
    private final StringProperty driver = new SimpleStringProperty();
    private final StringProperty location = new SimpleStringProperty("Main Depot");
    private final BooleanProperty assigned = new SimpleBooleanProperty(false);
    private final StringProperty currentJobId = new SimpleStringProperty();
    private final StringProperty currentRoute = new SimpleStringProperty();
    private final DoubleProperty currentLatitude = new SimpleDoubleProperty();
    private final DoubleProperty currentLongitude = new SimpleDoubleProperty();
    private final DoubleProperty currentSpeed = new SimpleDoubleProperty();
    private final StringProperty currentDirection = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> lastLocationUpdate = new SimpleObjectProperty<>();
    
    // Technical specifications
    private final DoubleProperty grossWeight = new SimpleDoubleProperty();
    private final IntegerProperty horsepower = new SimpleIntegerProperty();
    private final StringProperty transmissionType = new SimpleStringProperty();
    private final IntegerProperty axleCount = new SimpleIntegerProperty(2);
    private final DoubleProperty fuelTankCapacity = new SimpleDoubleProperty();
    private final StringProperty engineType = new SimpleStringProperty();
    private final StringProperty sleeper = new SimpleStringProperty("No"); // Yes/No/Size
    private final StringProperty emissions = new SimpleStringProperty();
    
    // Performance metrics
    private final IntegerProperty mileage = new SimpleIntegerProperty();
    private final DoubleProperty fuelLevel = new SimpleDoubleProperty(100.0);
    private final DoubleProperty mpg = new SimpleDoubleProperty();
    private final IntegerProperty idleTime = new SimpleIntegerProperty();
    private final DoubleProperty fuelUsedTotal = new SimpleDoubleProperty();
    private final IntegerProperty totalMilesDriven = new SimpleIntegerProperty();
    private final DoubleProperty averageSpeed = new SimpleDoubleProperty();
    private final DoubleProperty performanceScore = new SimpleDoubleProperty(100.0);
    private final IntegerProperty hardBrakeCount = new SimpleIntegerProperty();
    private final IntegerProperty hardAccelerationCount = new SimpleIntegerProperty();
    
    // Maintenance related
    private final ObjectProperty<LocalDate> lastService = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> nextServiceDue = new SimpleObjectProperty<>();
    private final StringProperty currentCondition = new SimpleStringProperty("Good");
    private final StringProperty maintenanceNotes = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> lastInspectionDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> nextInspectionDue = new SimpleObjectProperty<>();
    private final IntegerProperty mileageSinceService = new SimpleIntegerProperty();
    private final StringProperty lastServiceType = new SimpleStringProperty();
    private final BooleanProperty inMaintenance = new SimpleBooleanProperty(false);
    private final IntegerProperty maintenanceCount = new SimpleIntegerProperty();
    
    // Financial and ownership
    private final StringProperty ownershipType = new SimpleStringProperty("Company"); // Company, Leased
    private final DoubleProperty purchasePrice = new SimpleDoubleProperty();
    private final ObjectProperty<LocalDate> purchaseDate = new SimpleObjectProperty<>();
    private final DoubleProperty currentValue = new SimpleDoubleProperty();
    private final DoubleProperty monthlyLeaseCost = new SimpleDoubleProperty();
    private final StringProperty leaseDetails = new SimpleStringProperty();
    private final StringProperty insurancePolicyNumber = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> insuranceExpiryDate = new SimpleObjectProperty<>();
    private final DoubleProperty totalRevenue = new SimpleDoubleProperty();
    private final DoubleProperty totalExpenses = new SimpleDoubleProperty();
    private final DoubleProperty costPerMile = new SimpleDoubleProperty();
    private final StringProperty depreciationSchedule = new SimpleStringProperty();
    
    // Documentation and compliance
    private final ObjectProperty<LocalDate> registrationExpiryDate = new SimpleObjectProperty<>();
    private final StringProperty permitNumbers = new SimpleStringProperty();
    private final StringProperty dotNumber = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> lastStateInspection = new SimpleObjectProperty<>();
    private final BooleanProperty iftaCompliant = new SimpleBooleanProperty(true);
    private final BooleanProperty eldCompliant = new SimpleBooleanProperty(true);
    
    // Telematics and systems
    private final StringProperty gpsDeviceId = new SimpleStringProperty();
    private final StringProperty eldDeviceId = new SimpleStringProperty();
    private final StringProperty dashcamId = new SimpleStringProperty();
    private final StringProperty telematicsProvider = new SimpleStringProperty();
    private final BooleanProperty hasActiveDiagnosticCodes = new SimpleBooleanProperty(false);
    private final StringProperty activeDiagnosticCodes = new SimpleStringProperty();
    private final BooleanProperty temperatureMonitoring = new SimpleBooleanProperty(false);
    
    // Audit and tracking
    private final ObjectProperty<LocalDateTime> createdDate = new SimpleObjectProperty<>(LocalDateTime.now());
    private final StringProperty createdBy = new SimpleStringProperty("mgubran1");
    private final ObjectProperty<LocalDateTime> modifiedDate = new SimpleObjectProperty<>(LocalDateTime.now());
    private final StringProperty modifiedBy = new SimpleStringProperty("mgubran1");
    private final StringProperty notes = new SimpleStringProperty();
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    
    // Constructors
    public Truck() {
        // Default constructor
    }
    
    public Truck(String number) {
        this.number.set(number);
    }
    
    public Truck(String number, String type, String status) {
        this.number.set(number);
        this.type.set(type);
        this.status.set(status);
    }
    
    public Truck(String number, String vin, String make, String model, int year, String type) {
        this.number.set(number);
        this.vin.set(vin);
        this.make.set(make);
        this.model.set(model);
        this.year.set(year);
        this.type.set(type);
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
    
    public boolean isServiceDue() {
        LocalDate nextDue = nextServiceDue.get();
        return nextDue != null && (nextDue.isBefore(LocalDate.now()) || nextDue.isEqual(LocalDate.now()));
    }
    
    public boolean isInspectionDue() {
        LocalDate nextDue = nextInspectionDue.get();
        return nextDue != null && (nextDue.isBefore(LocalDate.now()) || nextDue.isEqual(LocalDate.now()));
    }
    
    public double getUtilizationRate() {
        // Placeholder for actual calculation
        return Math.min(100.0, performanceScore.get());
    }
    
    public double getFuelLevelGallons() {
        return fuelTankCapacity.get() * (fuelLevel.get() / 100.0);
    }
    
    public double getEstimatedRange() {
        return getFuelLevelGallons() * (mpg.get() > 0 ? mpg.get() : 6.0);
    }
    
    public double getNetValue() {
        return currentValue.get() - totalExpenses.get();
    }
    
    public double getProfit() {
        return totalRevenue.get() - totalExpenses.get();
    }
    
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        sb.append(number.get());
        
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
    
    public StringProperty numberProperty() {
        return number;
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
    
    public StringProperty statusProperty() {
        return status;
    }
    
    public StringProperty licensePlateProperty() {
        return licensePlate;
    }
    
    public StringProperty driverProperty() {
        return driver;
    }
    
    public StringProperty locationProperty() {
        return location;
    }
    
    public BooleanProperty assignedProperty() {
        return assigned;
    }
    
    public StringProperty currentJobIdProperty() {
        return currentJobId;
    }
    
    public StringProperty currentRouteProperty() {
        return currentRoute;
    }
    
    public DoubleProperty currentLatitudeProperty() {
        return currentLatitude;
    }
    
    public DoubleProperty currentLongitudeProperty() {
        return currentLongitude;
    }
    
    public DoubleProperty currentSpeedProperty() {
        return currentSpeed;
    }
    
    public StringProperty currentDirectionProperty() {
        return currentDirection;
    }
    
    public ObjectProperty<LocalDateTime> lastLocationUpdateProperty() {
        return lastLocationUpdate;
    }
    
    public DoubleProperty grossWeightProperty() {
        return grossWeight;
    }
    
    public IntegerProperty horsepowerProperty() {
        return horsepower;
    }
    
    public StringProperty transmissionTypeProperty() {
        return transmissionType;
    }
    
    public IntegerProperty axleCountProperty() {
        return axleCount;
    }
    
    public DoubleProperty fuelTankCapacityProperty() {
        return fuelTankCapacity;
    }
    
    public StringProperty engineTypeProperty() {
        return engineType;
    }
    
    public StringProperty sleeperProperty() {
        return sleeper;
    }
    
    public StringProperty emissionsProperty() {
        return emissions;
    }
    
    public IntegerProperty mileageProperty() {
        return mileage;
    }
    
    public DoubleProperty fuelLevelProperty() {
        return fuelLevel;
    }
    
    public DoubleProperty mpgProperty() {
        return mpg;
    }
    
    public IntegerProperty idleTimeProperty() {
        return idleTime;
    }
    
    public DoubleProperty fuelUsedTotalProperty() {
        return fuelUsedTotal;
    }
    
    public IntegerProperty totalMilesDrivenProperty() {
        return totalMilesDriven;
    }
    
    public DoubleProperty averageSpeedProperty() {
        return averageSpeed;
    }
    
    public DoubleProperty performanceScoreProperty() {
        return performanceScore;
    }
    
    public IntegerProperty hardBrakeCountProperty() {
        return hardBrakeCount;
    }
    
    public IntegerProperty hardAccelerationCountProperty() {
        return hardAccelerationCount;
    }
    
    public ObjectProperty<LocalDate> lastServiceProperty() {
        return lastService;
    }
    
    public ObjectProperty<LocalDate> nextServiceDueProperty() {
        return nextServiceDue;
    }
    
    public StringProperty currentConditionProperty() {
        return currentCondition;
    }
    
    public StringProperty maintenanceNotesProperty() {
        return maintenanceNotes;
    }
    
    public ObjectProperty<LocalDate> lastInspectionDateProperty() {
        return lastInspectionDate;
    }
    
    public ObjectProperty<LocalDate> nextInspectionDueProperty() {
        return nextInspectionDue;
    }
    
    public IntegerProperty mileageSinceServiceProperty() {
        return mileageSinceService;
    }
    
    public StringProperty lastServiceTypeProperty() {
        return lastServiceType;
    }
    
    public BooleanProperty inMaintenanceProperty() {
        return inMaintenance;
    }
    
    public IntegerProperty maintenanceCountProperty() {
        return maintenanceCount;
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
    
    public DoubleProperty totalRevenueProperty() {
        return totalRevenue;
    }
    
    public DoubleProperty totalExpensesProperty() {
        return totalExpenses;
    }
    
    public DoubleProperty costPerMileProperty() {
        return costPerMile;
    }
    
    public StringProperty depreciationScheduleProperty() {
        return depreciationSchedule;
    }
    
    public ObjectProperty<LocalDate> registrationExpiryDateProperty() {
        return registrationExpiryDate;
    }
    
    public StringProperty permitNumbersProperty() {
        return permitNumbers;
    }
    
    public StringProperty dotNumberProperty() {
        return dotNumber;
    }
    
    public ObjectProperty<LocalDate> lastStateInspectionProperty() {
        return lastStateInspection;
    }
    
    public BooleanProperty iftaCompliantProperty() {
        return iftaCompliant;
    }
    
    public BooleanProperty eldCompliantProperty() {
        return eldCompliant;
    }
    
    public StringProperty gpsDeviceIdProperty() {
        return gpsDeviceId;
    }
    
    public StringProperty eldDeviceIdProperty() {
        return eldDeviceId;
    }
    
    public StringProperty dashcamIdProperty() {
        return dashcamId;
    }
    
    public StringProperty telematicsProviderProperty() {
        return telematicsProvider;
    }
    
    public BooleanProperty hasActiveDiagnosticCodesProperty() {
        return hasActiveDiagnosticCodes;
    }
    
    public StringProperty activeDiagnosticCodesProperty() {
        return activeDiagnosticCodes;
    }
    
    public BooleanProperty temperatureMonitoringProperty() {
        return temperatureMonitoring;
    }
    
    public ObjectProperty<LocalDateTime> createdDateProperty() {
        return createdDate;
    }
    
    public StringProperty createdByProperty() {
        return createdBy;
    }
    
    public ObjectProperty<LocalDateTime> modifiedDateProperty() {
        return modifiedDate;
    }
    
    public StringProperty modifiedByProperty() {
        return modifiedBy;
    }
    
    public StringProperty notesProperty() {
        return notes;
    }
    
    public BooleanProperty selectedProperty() {
        return selected;
    }
    
    // Regular getters and setters
    public int getId() {
        return id.get();
    }
    
    public void setId(int id) {
        this.id.set(id);
        updateLastModified();
    }
    
    public String getNumber() {
        return number.get();
    }
    
    public void setNumber(String number) {
        this.number.set(number);
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
    
    public String getStatus() {
        return status.get();
    }
    
    public void setStatus(String status) {
        this.status.set(status);
        if (status.equals("Maintenance")) {
            this.inMaintenance.set(true);
        } else if (status.equals("Active") || status.equals("In Transit")) {
            this.inMaintenance.set(false);
        }
        updateLastModified();
    }
    
    public String getLicensePlate() {
        return licensePlate.get();
    }
    
    public void setLicensePlate(String licensePlate) {
        this.licensePlate.set(licensePlate);
        updateLastModified();
    }
    
    public String getDriver() {
        return driver.get();
    }
    
    public void setDriver(String driver) {
        this.driver.set(driver);
        this.assigned.set(driver != null && !driver.isEmpty());
        updateLastModified();
    }
    
    public String getLocation() {
        return location.get();
    }
    
    public void setLocation(String location) {
        this.location.set(location);
        updateLastModified();
    }
    
    public boolean isAssigned() {
        return assigned.get();
    }
    
    public void setAssigned(boolean assigned) {
        this.assigned.set(assigned);
        updateLastModified();
    }
    
    public String getCurrentJobId() {
        return currentJobId.get();
    }
    
    public void setCurrentJobId(String currentJobId) {
        this.currentJobId.set(currentJobId);
        updateLastModified();
    }
    
    public String getCurrentRoute() {
        return currentRoute.get();
    }
    
    public void setCurrentRoute(String currentRoute) {
        this.currentRoute.set(currentRoute);
        updateLastModified();
    }
    
    public double getCurrentLatitude() {
        return currentLatitude.get();
    }
    
    public void setCurrentLatitude(double currentLatitude) {
        this.currentLatitude.set(currentLatitude);
        updateLastModified();
    }
    
    public double getCurrentLongitude() {
        return currentLongitude.get();
    }
    
    public void setCurrentLongitude(double currentLongitude) {
        this.currentLongitude.set(currentLongitude);
        updateLastModified();
    }
    
    public double getCurrentSpeed() {
        return currentSpeed.get();
    }
    
    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed.set(currentSpeed);
        updateLastModified();
    }
    
    public String getCurrentDirection() {
        return currentDirection.get();
    }
    
    public void setCurrentDirection(String currentDirection) {
        this.currentDirection.set(currentDirection);
        updateLastModified();
    }
    
    public LocalDateTime getLastLocationUpdate() {
        return lastLocationUpdate.get();
    }
    
    public void setLastLocationUpdate(LocalDateTime lastLocationUpdate) {
        this.lastLocationUpdate.set(lastLocationUpdate);
        updateLastModified();
    }
    
    public double getGrossWeight() {
        return grossWeight.get();
    }
    
    public void setGrossWeight(double grossWeight) {
        this.grossWeight.set(grossWeight);
        updateLastModified();
    }
    
    public int getHorsepower() {
        return horsepower.get();
    }
    
    public void setHorsepower(int horsepower) {
        this.horsepower.set(horsepower);
        updateLastModified();
    }
    
    public String getTransmissionType() {
        return transmissionType.get();
    }
    
    public void setTransmissionType(String transmissionType) {
        this.transmissionType.set(transmissionType);
        updateLastModified();
    }
    
    public int getAxleCount() {
        return axleCount.get();
    }
    
    public void setAxleCount(int axleCount) {
        this.axleCount.set(axleCount);
        updateLastModified();
    }
    
    public double getFuelTankCapacity() {
        return fuelTankCapacity.get();
    }
    
    public void setFuelTankCapacity(double fuelTankCapacity) {
        this.fuelTankCapacity.set(fuelTankCapacity);
        updateLastModified();
    }
    
    public String getEngineType() {
        return engineType.get();
    }
    
    public void setEngineType(String engineType) {
        this.engineType.set(engineType);
        updateLastModified();
    }
    
    public String getSleeper() {
        return sleeper.get();
    }
    
    public void setSleeper(String sleeper) {
        this.sleeper.set(sleeper);
        updateLastModified();
    }
    
    public String getEmissions() {
        return emissions.get();
    }
    
    public void setEmissions(String emissions) {
        this.emissions.set(emissions);
        updateLastModified();
    }
    
    public int getMileage() {
        return mileage.get();
    }
    
    public void setMileage(int mileage) {
        this.mileage.set(mileage);
        // Update mileage since service
        if (lastService.get() != null) {
            int lastServiceMileage = mileageSinceService.get();
            mileageSinceService.set(mileage - lastServiceMileage);
        }
        updateLastModified();
    }
    
    public double getFuelLevel() {
        return fuelLevel.get();
    }
    
    public void setFuelLevel(double fuelLevel) {
        this.fuelLevel.set(fuelLevel);
        updateLastModified();
    }
    
    public double getMpg() {
        return mpg.get();
    }
    
    public void setMpg(double mpg) {
        this.mpg.set(mpg);
        updateLastModified();
    }
    
    public int getIdleTime() {
        return idleTime.get();
    }
    
    public void setIdleTime(int idleTime) {
        this.idleTime.set(idleTime);
        updateLastModified();
    }
    
    public double getFuelUsedTotal() {
        return fuelUsedTotal.get();
    }
    
    public void setFuelUsedTotal(double fuelUsedTotal) {
        this.fuelUsedTotal.set(fuelUsedTotal);
        updateLastModified();
    }
    
    public int getTotalMilesDriven() {
        return totalMilesDriven.get();
    }
    
    public void setTotalMilesDriven(int totalMilesDriven) {
        this.totalMilesDriven.set(totalMilesDriven);
        updateLastModified();
    }
    
    public double getAverageSpeed() {
        return averageSpeed.get();
    }
    
    public void setAverageSpeed(double averageSpeed) {
        this.averageSpeed.set(averageSpeed);
        updateLastModified();
    }
    
    public double getPerformanceScore() {
        return performanceScore.get();
    }
    
    public void setPerformanceScore(double performanceScore) {
        this.performanceScore.set(performanceScore);
        updateLastModified();
    }
    
    public int getHardBrakeCount() {
        return hardBrakeCount.get();
    }
    
    public void setHardBrakeCount(int hardBrakeCount) {
        this.hardBrakeCount.set(hardBrakeCount);
        updateLastModified();
    }
    
    public int getHardAccelerationCount() {
        return hardAccelerationCount.get();
    }
    
    public void setHardAccelerationCount(int hardAccelerationCount) {
        this.hardAccelerationCount.set(hardAccelerationCount);
        updateLastModified();
    }
    
    public LocalDate getLastService() {
        return lastService.get();
    }
    
    public void setLastService(LocalDate lastService) {
        this.lastService.set(lastService);
        // If setting last service, update next service due date (e.g., +90 days)
        if (lastService != null) {
            this.nextServiceDue.set(lastService.plusDays(90));
            this.mileageSinceService.set(0);
        }
        updateLastModified();
    }
    
    public LocalDate getNextServiceDue() {
        return nextServiceDue.get();
    }
    
    public void setNextServiceDue(LocalDate nextServiceDue) {
        this.nextServiceDue.set(nextServiceDue);
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
    
    public LocalDate getLastInspectionDate() {
        return lastInspectionDate.get();
    }
    
    public void setLastInspectionDate(LocalDate lastInspectionDate) {
        this.lastInspectionDate.set(lastInspectionDate);
        // Update next inspection date (e.g., +12 months)
        if (lastInspectionDate != null) {
            this.nextInspectionDue.set(lastInspectionDate.plusMonths(12));
        }
        updateLastModified();
    }
    
    public LocalDate getNextInspectionDue() {
        return nextInspectionDue.get();
    }
    
    public void setNextInspectionDue(LocalDate nextInspectionDue) {
        this.nextInspectionDue.set(nextInspectionDue);
        updateLastModified();
    }
    
    public int getMileageSinceService() {
        return mileageSinceService.get();
    }
    
    public void setMileageSinceService(int mileageSinceService) {
        this.mileageSinceService.set(mileageSinceService);
        updateLastModified();
    }
    
    public String getLastServiceType() {
        return lastServiceType.get();
    }
    
    public void setLastServiceType(String lastServiceType) {
        this.lastServiceType.set(lastServiceType);
        updateLastModified();
    }
    
    public boolean isInMaintenance() {
        return inMaintenance.get();
    }
    
    public void setInMaintenance(boolean inMaintenance) {
        this.inMaintenance.set(inMaintenance);
        if (inMaintenance) {
            this.status.set("Maintenance");
            this.maintenanceCount.set(this.maintenanceCount.get() + 1);
        }
        updateLastModified();
    }
    
    public int getMaintenanceCount() {
        return maintenanceCount.get();
    }
    
    public void setMaintenanceCount(int maintenanceCount) {
        this.maintenanceCount.set(maintenanceCount);
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
    
    public double getTotalRevenue() {
        return totalRevenue.get();
    }
    
    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue.set(totalRevenue);
        updateLastModified();
    }
    
    public double getTotalExpenses() {
        return totalExpenses.get();
    }
    
    public void setTotalExpenses(double totalExpenses) {
        this.totalExpenses.set(totalExpenses);
        // Update cost per mile if we have miles
        if (totalMilesDriven.get() > 0) {
            costPerMile.set(totalExpenses / totalMilesDriven.get());
        }
        updateLastModified();
    }
    
    public double getCostPerMile() {
        return costPerMile.get();
    }
    
    public void setCostPerMile(double costPerMile) {
        this.costPerMile.set(costPerMile);
        updateLastModified();
    }
    
    public String getDepreciationSchedule() {
        return depreciationSchedule.get();
    }
    
    public void setDepreciationSchedule(String depreciationSchedule) {
        this.depreciationSchedule.set(depreciationSchedule);
        updateLastModified();
    }
    
    public LocalDate getRegistrationExpiryDate() {
        return registrationExpiryDate.get();
    }
    
    public void setRegistrationExpiryDate(LocalDate registrationExpiryDate) {
        this.registrationExpiryDate.set(registrationExpiryDate);
        updateLastModified();
    }
    
    public String getPermitNumbers() {
        return permitNumbers.get();
    }
    
    public void setPermitNumbers(String permitNumbers) {
        this.permitNumbers.set(permitNumbers);
        updateLastModified();
    }
    
    public String getDotNumber() {
        return dotNumber.get();
    }
    
    public void setDotNumber(String dotNumber) {
        this.dotNumber.set(dotNumber);
        updateLastModified();
    }
    
    public LocalDate getLastStateInspection() {
        return lastStateInspection.get();
    }
    
    public void setLastStateInspection(LocalDate lastStateInspection) {
        this.lastStateInspection.set(lastStateInspection);
        updateLastModified();
    }
    
    public boolean isIftaCompliant() {
        return iftaCompliant.get();
    }
    
    public void setIftaCompliant(boolean iftaCompliant) {
        this.iftaCompliant.set(iftaCompliant);
        updateLastModified();
    }
    
    public boolean isEldCompliant() {
        return eldCompliant.get();
    }
    
    public void setEldCompliant(boolean eldCompliant) {
        this.eldCompliant.set(eldCompliant);
        updateLastModified();
    }
    
    public String getGpsDeviceId() {
        return gpsDeviceId.get();
    }
    
    public void setGpsDeviceId(String gpsDeviceId) {
        this.gpsDeviceId.set(gpsDeviceId);
        updateLastModified();
    }
    
    public String getEldDeviceId() {
        return eldDeviceId.get();
    }
    
    public void setEldDeviceId(String eldDeviceId) {
        this.eldDeviceId.set(eldDeviceId);
        updateLastModified();
    }
    
    public String getDashcamId() {
        return dashcamId.get();
    }
    
    public void setDashcamId(String dashcamId) {
        this.dashcamId.set(dashcamId);
        updateLastModified();
    }
    
    public String getTelematicsProvider() {
        return telematicsProvider.get();
    }
    
    public void setTelematicsProvider(String telematicsProvider) {
        this.telematicsProvider.set(telematicsProvider);
        updateLastModified();
    }
    
    public boolean isHasActiveDiagnosticCodes() {
        return hasActiveDiagnosticCodes.get();
    }
    
    public void setHasActiveDiagnosticCodes(boolean hasActiveDiagnosticCodes) {
        this.hasActiveDiagnosticCodes.set(hasActiveDiagnosticCodes);
        updateLastModified();
    }
    
    public String getActiveDiagnosticCodes() {
        return activeDiagnosticCodes.get();
    }
    
    public void setActiveDiagnosticCodes(String activeDiagnosticCodes) {
        this.activeDiagnosticCodes.set(activeDiagnosticCodes);
        updateLastModified();
    }
    
    public boolean isTemperatureMonitoring() {
        return temperatureMonitoring.get();
    }
    
    public void setTemperatureMonitoring(boolean temperatureMonitoring) {
        this.temperatureMonitoring.set(temperatureMonitoring);
        updateLastModified();
    }
    
    public LocalDateTime getCreatedDate() {
        return createdDate.get();
    }
    
    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate.set(createdDate);
    }
    
    public String getCreatedBy() {
        return createdBy.get();
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy.set(createdBy);
    }
    
    public LocalDateTime getModifiedDate() {
        return modifiedDate.get();
    }
    
    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate.set(modifiedDate);
    }
    
    public String getModifiedBy() {
        return modifiedBy.get();
    }
    
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy.set(modifiedBy);
    }
    
    public String getNotes() {
        return notes.get();
    }
    
    public void setNotes(String notes) {
        this.notes.set(notes);
        updateLastModified();
    }
    
    public boolean isSelected() {
        return selected.get();
    }
    
    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
    
    // Helper method to update modification tracking
    private void updateLastModified() {
        modifiedDate.set(LocalDateTime.now());
        modifiedBy.set("mgubran1");
    }
    
    @Override
    public String toString() {
        return String.format("%s (%d %s %s)", 
            getNumber(), 
            getYear(), 
            getMake(), 
            getModel());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Truck truck = (Truck) o;
        return Objects.equals(getNumber(), truck.getNumber()) ||
               (getId() > 0 && getId() == truck.getId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getNumber(), getId());
    }
}