package com.company.payroll.maintenance;

import java.time.LocalDate;

public class MaintenanceRecord {
    public enum VehicleType { TRUCK, TRAILER }

    private int id;
    private VehicleType vehicleType;
    private int vehicleId;
    private LocalDate serviceDate;
    private String description;
    private double cost;
    private LocalDate nextDue;
    private String receiptNumber;
    private String receiptPath;

    public MaintenanceRecord(int id, VehicleType vehicleType, int vehicleId,
                             LocalDate serviceDate, String description, double cost,
                             LocalDate nextDue, String receiptNumber, String receiptPath) {
        this.id = id;
        this.vehicleType = vehicleType;
        this.vehicleId = vehicleId;
        this.serviceDate = serviceDate;
        this.description = description;
        this.cost = cost;
        this.nextDue = nextDue;
        this.receiptNumber = receiptNumber;
        this.receiptPath = receiptPath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }
    public LocalDate getNextDue() { return nextDue; }
    public void setNextDue(LocalDate nextDue) { this.nextDue = nextDue; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }
}
