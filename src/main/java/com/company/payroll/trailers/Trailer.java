package com.company.payroll.trailers;

import java.time.LocalDate;

public class Trailer {
    private int id;
    private String number;
    private String type;
    private int year;
    private String vin;
    private String licensePlate;
    private LocalDate licenseExpiry;
    private LocalDate inspectionExpiry;
    private Status status;

    public enum Status { ACTIVE, IN_SHOP, SOLD, INACTIVE }

    public Trailer(int id, String number, String type, int year, String vin,
                   String licensePlate, LocalDate licenseExpiry,
                   LocalDate inspectionExpiry, Status status) {
        this.id = id;
        this.number = number;
        this.type = type;
        this.year = year;
        this.vin = vin;
        this.licensePlate = licensePlate;
        this.licenseExpiry = licenseExpiry;
        this.inspectionExpiry = inspectionExpiry;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
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
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
