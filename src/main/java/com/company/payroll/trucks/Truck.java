package com.company.payroll.trucks;

import java.time.LocalDate;

public class Truck {
    private int id;
    private String unit;
    private String make;
    private String model;
    private int year;
    private String vin;
    private String licensePlate;
    private LocalDate licenseExpiry;
    private LocalDate inspectionExpiry;
    private LocalDate iftaExpiry;
    private Status status;

    public enum Status { ACTIVE, IN_SHOP, SOLD, INACTIVE }

    public Truck(int id, String unit, String make, String model, int year,
                 String vin, String licensePlate, LocalDate licenseExpiry,
                 LocalDate inspectionExpiry, LocalDate iftaExpiry, Status status) {
        this.id = id;
        this.unit = unit;
        this.make = make;
        this.model = model;
        this.year = year;
        this.vin = vin;
        this.licensePlate = licensePlate;
        this.licenseExpiry = licenseExpiry;
        this.inspectionExpiry = inspectionExpiry;
        this.iftaExpiry = iftaExpiry;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public LocalDate getInspectionExpiry() { return inspectionExpiry; }
    public void setInspectionExpiry(LocalDate inspectionExpiry) { this.inspectionExpiry = inspectionExpiry; }
    public LocalDate getIftaExpiry() { return iftaExpiry; }
    public void setIftaExpiry(LocalDate iftaExpiry) { this.iftaExpiry = iftaExpiry; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
