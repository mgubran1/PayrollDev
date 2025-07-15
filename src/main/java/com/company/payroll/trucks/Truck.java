package com.company.payroll.trucks;

import java.time.LocalDate;

/**
 * Simplified Truck model matching the fields used by {@link TrucksTab}.
 */
public class Truck {
    private int id;
    private String number;
    private String vin;
    private String make;
    private String model;
    private int year;
    private String type;
    private String status;
    private String licensePlate;
    private LocalDate registrationExpiryDate;
    private LocalDate insuranceExpiryDate;
    private LocalDate nextInspectionDue;
    private String permitNumbers;
    private String driver;
    private boolean assigned;

    public Truck() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public LocalDate getRegistrationExpiryDate() {
        return registrationExpiryDate;
    }

    public void setRegistrationExpiryDate(LocalDate registrationExpiryDate) {
        this.registrationExpiryDate = registrationExpiryDate;
    }

    public LocalDate getInsuranceExpiryDate() {
        return insuranceExpiryDate;
    }

    public void setInsuranceExpiryDate(LocalDate insuranceExpiryDate) {
        this.insuranceExpiryDate = insuranceExpiryDate;
    }

    public LocalDate getNextInspectionDue() {
        return nextInspectionDue;
    }

    public void setNextInspectionDue(LocalDate nextInspectionDue) {
        this.nextInspectionDue = nextInspectionDue;
    }

    public String getPermitNumbers() {
        return permitNumbers;
    }

    public void setPermitNumbers(String permitNumbers) {
        this.permitNumbers = permitNumbers;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }

    @Override
    public String toString() {
        return number != null ? number : "";
    }
}
