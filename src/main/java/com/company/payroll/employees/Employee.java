package com.company.payroll.employees;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Enhanced Employee model that maintains backward compatibility while adding new features
 */
public class Employee {
    private int id;
    private String name;
    private String truckUnit;
    private String trailerNumber;
    private double driverPercent;
    private double companyPercent;
    private double serviceFeePercent;
    private LocalDate dob;
    private String licenseNumber;
    private DriverType driverType;
    private String employeeLLC;
    private LocalDate cdlExpiry;
    private LocalDate medicalExpiry;
    private Status status;
    
    // New enhanced fields
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private LocalDate hireDate;
    private String emergencyContact;
    private String emergencyPhone;
    private String cdlClass; // Class A, Class B, etc.
    private boolean hazmatEndorsement;
    private double totalMilesDriven;
    private double safetyScore;
    private int totalLoadsCompleted;
    private double fuelEfficiencyRating;
    private LocalDate lastDrugTest;
    private LocalDate lastPhysical;
    private String notes;
    
    // Performance metrics
    private double onTimeDeliveryRate = 100.0;
    private int accidentCount = 0;
    private int violationCount = 0;
    private double customerRating = 5.0;
    
    // Financial tracking
    private double totalEarningsYTD;
    private double totalDeductionsYTD;
    private double advanceBalance;
    private double escrowBalance;
    private double weeklyPayRate; // For company drivers
    
    // Payment method fields
    private PaymentType paymentType = PaymentType.PERCENTAGE;
    private double flatRateAmount = 0.0;
    private double perMileRate = 0.0;
    private LocalDate paymentEffectiveDate = LocalDate.now();
    private String paymentNotes;
    
    // Audit fields
    private LocalDate createdDate;
    private LocalDate modifiedDate;
    private String modifiedBy;

    public enum DriverType { OWNER_OPERATOR, COMPANY_DRIVER, OTHER }
    public enum Status { ACTIVE, ON_LEAVE, TERMINATED }

    // Original Constructor
    public Employee(int id, String name, String truckUnit, String trailerNumber, double driverPercent, 
                    double companyPercent, double serviceFeePercent, LocalDate dob, String licenseNumber, 
                    DriverType driverType, String employeeLLC, LocalDate cdlExpiry, LocalDate medicalExpiry, 
                    Status status) {
        this.id = id;
        this.name = name;
        this.truckUnit = truckUnit;
        this.trailerNumber = trailerNumber;
        this.driverPercent = driverPercent;
        this.companyPercent = companyPercent;
        this.serviceFeePercent = serviceFeePercent;
        this.dob = dob;
        this.licenseNumber = licenseNumber;
        this.driverType = driverType;
        this.employeeLLC = employeeLLC;
        this.cdlExpiry = cdlExpiry;
        this.medicalExpiry = medicalExpiry;
        this.status = status;
        
        // Initialize new fields with defaults
        this.safetyScore = 100.0;
        this.customerRating = 5.0;
        this.onTimeDeliveryRate = 100.0;
        this.createdDate = LocalDate.now();
        this.modifiedDate = LocalDate.now();
        this.modifiedBy = "mgubran1";
        this.hireDate = LocalDate.now();
    }

    // Backward compatibility constructor
    public Employee(int id, String name, String truckUnit, double driverPercent, double companyPercent, 
                    double serviceFeePercent, LocalDate dob, String licenseNumber, DriverType driverType, 
                    String employeeLLC, LocalDate cdlExpiry, LocalDate medicalExpiry, Status status) {
        this(id, name, truckUnit, "", driverPercent, companyPercent, serviceFeePercent,
             dob, licenseNumber, driverType, employeeLLC, cdlExpiry, medicalExpiry, status);
    }
    
    // New enhanced constructor with all fields
    public Employee(int id, String name, String truckUnit, String trailerNumber, double driverPercent,
                    double companyPercent, double serviceFeePercent, LocalDate dob, String licenseNumber,
                    DriverType driverType, String employeeLLC, LocalDate cdlExpiry, LocalDate medicalExpiry,
                    Status status, String email, String phone, String address, String city, String state,
                    String zipCode, LocalDate hireDate) {
        this(id, name, truckUnit, trailerNumber, driverPercent, companyPercent, serviceFeePercent,
             dob, licenseNumber, driverType, employeeLLC, cdlExpiry, medicalExpiry, status);
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.hireDate = hireDate != null ? hireDate : LocalDate.now();
    }
    
    // Constructor with payment method support
    public Employee(int id, String name, String truckUnit, String trailerNumber, double driverPercent,
                    double companyPercent, double serviceFeePercent, LocalDate dob, String licenseNumber,
                    DriverType driverType, String employeeLLC, LocalDate cdlExpiry, LocalDate medicalExpiry,
                    Status status, PaymentType paymentType, double flatRateAmount, double perMileRate,
                    LocalDate paymentEffectiveDate, String paymentNotes) {
        this(id, name, truckUnit, trailerNumber, driverPercent, companyPercent, serviceFeePercent,
             dob, licenseNumber, driverType, employeeLLC, cdlExpiry, medicalExpiry, status);
        this.paymentType = paymentType != null ? paymentType : PaymentType.PERCENTAGE;
        this.flatRateAmount = flatRateAmount;
        this.perMileRate = perMileRate;
        this.paymentEffectiveDate = paymentEffectiveDate != null ? paymentEffectiveDate : LocalDate.now();
        this.paymentNotes = paymentNotes;
    }

    // Computed properties
    public boolean isDriver() {
        return driverType == DriverType.OWNER_OPERATOR || driverType == DriverType.COMPANY_DRIVER;
    }
    
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
    
    public int getAge() {
        if (dob == null) return 0;
        return (int) ChronoUnit.YEARS.between(dob, LocalDate.now());
    }
    
    public int getYearsOfService() {
        if (hireDate == null) return 0;
        return (int) ChronoUnit.YEARS.between(hireDate, LocalDate.now());
    }
    
    public boolean isCdlExpired() {
        if (cdlExpiry == null) return false;
        return cdlExpiry.isBefore(LocalDate.now());
    }
    
    public boolean isMedicalExpired() {
        if (medicalExpiry == null) return false;
        return medicalExpiry.isBefore(LocalDate.now());
    }
    
    public int getDaysUntilCdlExpiry() {
        if (cdlExpiry == null) return -1;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), cdlExpiry);
    }
    
    public int getDaysUntilMedicalExpiry() {
        if (medicalExpiry == null) return -1;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), medicalExpiry);
    }
    
    public boolean needsDrugTest() {
        if (lastDrugTest == null) return true;
        return lastDrugTest.plusMonths(6).isBefore(LocalDate.now());
    }
    
    public boolean needsPhysical() {
        if (lastPhysical == null) return true;
        return lastPhysical.plusYears(2).isBefore(LocalDate.now());
    }
    
    public String getPerformanceRating() {
        if (safetyScore >= 90 && onTimeDeliveryRate >= 95 && customerRating >= 4.5) {
            return "Excellent";
        } else if (safetyScore >= 80 && onTimeDeliveryRate >= 90 && customerRating >= 4.0) {
            return "Good";
        } else if (safetyScore >= 70 && onTimeDeliveryRate >= 85 && customerRating >= 3.5) {
            return "Average";
        } else {
            return "Needs Improvement";
        }
    }
    
    public double getNetEarningsYTD() {
        return totalEarningsYTD - totalDeductionsYTD;
    }
    
    public String getFullAddress() {
        if (address == null || city == null || state == null || zipCode == null) {
            return "";
        }
        return address + ", " + city + ", " + state + " " + zipCode;
    }
    
    // Payment method validation and calculation
    public boolean isValidPaymentConfiguration() {
        if (paymentType == null) {
            return false;
        }
        return paymentType.isValidConfiguration(driverPercent, companyPercent, serviceFeePercent, 
                                               flatRateAmount, perMileRate);
    }
    
    public String getPaymentConfigurationError() {
        if (paymentType == null) {
            return "Payment type is not set";
        }
        return paymentType.getValidationError(driverPercent, companyPercent, serviceFeePercent,
                                            flatRateAmount, perMileRate);
    }
    
    public double calculateLoadPayment(double grossAmount, double miles) {
        if (paymentType == null) {
            return calculatePercentagePayment(grossAmount);
        }
        return paymentType.calculatePayment(grossAmount, miles, driverPercent, flatRateAmount, perMileRate);
    }
    
    public double calculatePercentagePayment(double grossAmount) {
        return grossAmount * (driverPercent / 100.0);
    }
    
    public String getPaymentMethodDescription() {
        if (paymentType == null) {
            return "Not configured";
        }
        
        switch (paymentType) {
            case PERCENTAGE:
                return String.format("%s (%.2f%%)", paymentType.getDisplayName(), driverPercent);
            case FLAT_RATE:
                return String.format("%s ($%.2f/load)", paymentType.getDisplayName(), flatRateAmount);
            case PER_MILE:
                return String.format("%s ($%.2f/mile)", paymentType.getDisplayName(), perMileRate);
            default:
                return paymentType.getDisplayName();
        }
    }
    
    public boolean requiresZipCodesForPayment() {
        return paymentType != null && paymentType.requiresZipCodes();
    }
    
    public boolean requiresGrossAmountForPayment() {
        return paymentType != null && paymentType.requiresGrossAmount();
    }
    
    public String getPaymentWarning(double amount, double miles) {
        if (paymentType == null) {
            return null;
        }
        return paymentType.getPaymentWarning(amount, miles);
    }

    // Original getters and setters
    public int getId() { return id; }
    public void setId(int id) { 
        this.id = id; 
        updateModified();
    }
    
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name;
        updateModified();
    }
    
    public String getTruckUnit() { return truckUnit; }
    public void setTruckUnit(String truckUnit) { 
        this.truckUnit = truckUnit;
        updateModified();
    }
    
    public String getTrailerNumber() { return trailerNumber; }
    public void setTrailerNumber(String trailerNumber) { 
        this.trailerNumber = trailerNumber;
        updateModified();
    }
    
    public double getDriverPercent() { return driverPercent; }
    public void setDriverPercent(double driverPercent) { 
        this.driverPercent = driverPercent;
        updateModified();
    }
    
    public double getCompanyPercent() { return companyPercent; }
    public void setCompanyPercent(double companyPercent) { 
        this.companyPercent = companyPercent;
        updateModified();
    }
    
    public double getServiceFeePercent() { return serviceFeePercent; }
    public void setServiceFeePercent(double serviceFeePercent) { 
        this.serviceFeePercent = serviceFeePercent;
        updateModified();
    }
    
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { 
        this.dob = dob;
        updateModified();
    }
    
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { 
        this.licenseNumber = licenseNumber;
        updateModified();
    }
    
    public DriverType getDriverType() { return driverType; }
    public void setDriverType(DriverType driverType) { 
        this.driverType = driverType;
        updateModified();
    }
    
    public String getEmployeeLLC() { return employeeLLC; }
    public void setEmployeeLLC(String employeeLLC) { 
        this.employeeLLC = employeeLLC;
        updateModified();
    }
    
    public LocalDate getCdlExpiry() { return cdlExpiry; }
    public void setCdlExpiry(LocalDate cdlExpiry) { 
        this.cdlExpiry = cdlExpiry;
        updateModified();
    }
    
    public LocalDate getMedicalExpiry() { return medicalExpiry; }
    public void setMedicalExpiry(LocalDate medicalExpiry) { 
        this.medicalExpiry = medicalExpiry;
        updateModified();
    }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { 
        this.status = status;
        updateModified();
    }

    // Alias for compatibility with getTruckId()
    public String getTruckId() {
        return truckUnit;
    }

    // New enhanced getters and setters
    public String getEmail() { return email; }
    public void setEmail(String email) { 
        this.email = email;
        updateModified();
    }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { 
        this.phone = phone;
        updateModified();
    }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { 
        this.address = address;
        updateModified();
    }
    
    public String getCity() { return city; }
    public void setCity(String city) { 
        this.city = city;
        updateModified();
    }
    
    public String getState() { return state; }
    public void setState(String state) { 
        this.state = state;
        updateModified();
    }
    
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { 
        this.zipCode = zipCode;
        updateModified();
    }
    
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { 
        this.hireDate = hireDate;
        updateModified();
    }
    
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { 
        this.emergencyContact = emergencyContact;
        updateModified();
    }
    
    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { 
        this.emergencyPhone = emergencyPhone;
        updateModified();
    }
    
    public String getCdlClass() { return cdlClass; }
    public void setCdlClass(String cdlClass) { 
        this.cdlClass = cdlClass;
        updateModified();
    }
    
    public boolean isHazmatEndorsement() { return hazmatEndorsement; }
    public void setHazmatEndorsement(boolean hazmatEndorsement) { 
        this.hazmatEndorsement = hazmatEndorsement;
        updateModified();
    }
    
    public double getTotalMilesDriven() { return totalMilesDriven; }
    public void setTotalMilesDriven(double totalMilesDriven) { 
        this.totalMilesDriven = totalMilesDriven;
        updateModified();
    }
    
    public double getSafetyScore() { return safetyScore; }
    public void setSafetyScore(double safetyScore) { 
        this.safetyScore = safetyScore;
        updateModified();
    }
    
    public int getTotalLoadsCompleted() { return totalLoadsCompleted; }
    public void setTotalLoadsCompleted(int totalLoadsCompleted) { 
        this.totalLoadsCompleted = totalLoadsCompleted;
        updateModified();
    }
    
    public double getFuelEfficiencyRating() { return fuelEfficiencyRating; }
    public void setFuelEfficiencyRating(double fuelEfficiencyRating) { 
        this.fuelEfficiencyRating = fuelEfficiencyRating;
        updateModified();
    }
    
    public LocalDate getLastDrugTest() { return lastDrugTest; }
    public void setLastDrugTest(LocalDate lastDrugTest) { 
        this.lastDrugTest = lastDrugTest;
        updateModified();
    }
    
    public LocalDate getLastPhysical() { return lastPhysical; }
    public void setLastPhysical(LocalDate lastPhysical) { 
        this.lastPhysical = lastPhysical;
        updateModified();
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { 
        this.notes = notes;
        updateModified();
    }
    
    public double getOnTimeDeliveryRate() { return onTimeDeliveryRate; }
    public void setOnTimeDeliveryRate(double onTimeDeliveryRate) { 
        this.onTimeDeliveryRate = onTimeDeliveryRate;
        updateModified();
    }
    
    public int getAccidentCount() { return accidentCount; }
    public void setAccidentCount(int accidentCount) { 
        this.accidentCount = accidentCount;
        updateModified();
    }
    
    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { 
        this.violationCount = violationCount;
        updateModified();
    }
    
    public double getCustomerRating() { return customerRating; }
    public void setCustomerRating(double customerRating) { 
        this.customerRating = customerRating;
        updateModified();
    }
    
    public double getTotalEarningsYTD() { return totalEarningsYTD; }
    public void setTotalEarningsYTD(double totalEarningsYTD) { 
        this.totalEarningsYTD = totalEarningsYTD;
        updateModified();
    }
    
    public double getTotalDeductionsYTD() { return totalDeductionsYTD; }
    public void setTotalDeductionsYTD(double totalDeductionsYTD) { 
        this.totalDeductionsYTD = totalDeductionsYTD;
        updateModified();
    }
    
    public double getAdvanceBalance() { return advanceBalance; }
    public void setAdvanceBalance(double advanceBalance) { 
        this.advanceBalance = advanceBalance;
        updateModified();
    }
    
    public double getEscrowBalance() { return escrowBalance; }
    public void setEscrowBalance(double escrowBalance) { 
        this.escrowBalance = escrowBalance;
        updateModified();
    }
    
    public double getWeeklyPayRate() { return weeklyPayRate; }
    public void setWeeklyPayRate(double weeklyPayRate) { 
        this.weeklyPayRate = weeklyPayRate;
        updateModified();
    }
    
    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }
    
    public LocalDate getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(LocalDate modifiedDate) { this.modifiedDate = modifiedDate; }
    
    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }
    
    // Payment method getters and setters
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
        updateModified();
    }
    
    public double getFlatRateAmount() { return flatRateAmount; }
    public void setFlatRateAmount(double flatRateAmount) {
        this.flatRateAmount = flatRateAmount;
        updateModified();
    }
    
    public double getPerMileRate() { return perMileRate; }
    public void setPerMileRate(double perMileRate) {
        this.perMileRate = perMileRate;
        updateModified();
    }
    
    public LocalDate getPaymentEffectiveDate() { return paymentEffectiveDate; }
    public void setPaymentEffectiveDate(LocalDate paymentEffectiveDate) {
        this.paymentEffectiveDate = paymentEffectiveDate;
        updateModified();
    }
    
    public String getPaymentNotes() { return paymentNotes; }
    public void setPaymentNotes(String paymentNotes) {
        this.paymentNotes = paymentNotes;
        updateModified();
    }
    
    // Helper method to update modification tracking
    private void updateModified() {
        this.modifiedDate = LocalDate.now();
        this.modifiedBy = "mgubran1";
    }
    
    @Override
    public String toString() {
        return name + " (" + driverType + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Employee employee = (Employee) o;
        return id == employee.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}