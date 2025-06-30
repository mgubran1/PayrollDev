package com.company.payroll.employees;

import java.time.LocalDate;

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

    public enum DriverType { OWNER_OPERATOR, COMPANY_DRIVER, OTHER }
    public enum Status { ACTIVE, ON_LEAVE, TERMINATED }

    // Constructor
    public Employee(int id, String name, String truckUnit, String trailerNumber, double driverPercent, double companyPercent, double serviceFeePercent,
                    LocalDate dob, String licenseNumber, DriverType driverType, String employeeLLC,
                    LocalDate cdlExpiry, LocalDate medicalExpiry, Status status) {
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
    }

    // Backward compatibility constructor
    public Employee(int id, String name, String truckUnit, double driverPercent, double companyPercent, double serviceFeePercent,
                    LocalDate dob, String licenseNumber, DriverType driverType, String employeeLLC,
                    LocalDate cdlExpiry, LocalDate medicalExpiry, Status status) {
        this(id, name, truckUnit, "", driverPercent, companyPercent, serviceFeePercent,
             dob, licenseNumber, driverType, employeeLLC, cdlExpiry, medicalExpiry, status);
    }

    // Getters and setters...
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTruckUnit() { return truckUnit; }
    public void setTruckUnit(String truckUnit) { this.truckUnit = truckUnit; }
    public String getTrailerNumber() { return trailerNumber; }
    public void setTrailerNumber(String trailerNumber) { this.trailerNumber = trailerNumber; }
    public double getDriverPercent() { return driverPercent; }
    public void setDriverPercent(double driverPercent) { this.driverPercent = driverPercent; }
    public double getCompanyPercent() { return companyPercent; }
    public void setCompanyPercent(double companyPercent) { this.companyPercent = companyPercent; }
    public double getServiceFeePercent() { return serviceFeePercent; }
    public void setServiceFeePercent(double serviceFeePercent) { this.serviceFeePercent = serviceFeePercent; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public DriverType getDriverType() { return driverType; }
    public void setDriverType(DriverType driverType) { this.driverType = driverType; }
    public String getEmployeeLLC() { return employeeLLC; }
    public void setEmployeeLLC(String employeeLLC) { this.employeeLLC = employeeLLC; }
    public LocalDate getCdlExpiry() { return cdlExpiry; }
    public void setCdlExpiry(LocalDate cdlExpiry) { this.cdlExpiry = cdlExpiry; }
    public LocalDate getMedicalExpiry() { return medicalExpiry; }
    public void setMedicalExpiry(LocalDate medicalExpiry) { this.medicalExpiry = medicalExpiry; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    // Alias for compatibility with getTruckId()
    public String getTruckId() {
        return truckUnit;
    }
}