package com.company.payroll.driver;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

/**
 * Data model for driver income information
 */
public class DriverIncomeData {
    private final int driverId;
    private final String driverName;
    private final String truckUnit;
    private final LocalDate startDate;
    private final LocalDate endDate;
    
    // Load data
    private int totalLoads;
    private double totalGross;
    private double totalMiles;
    private List<LoadDetail> loads = new ArrayList<>();
    
    // Fuel data
    private double totalFuelCost;
    private double totalFuelFees;
    private double totalFuelAmount;
    
    // Deductions
    private double serviceFee;
    private double recurringFees;
    private double advanceRepayments;
    private double escrowDeposits;
    private double otherDeductions;
    private double reimbursements;
    
    // Calculated fields
    private double netPay;
    private double averagePerMile;
    private double fuelEfficiency;
    
    public static class LoadDetail {
        public final String loadNumber;
        public final String customer;
        public final String pickupLocation;
        public final String dropLocation;
        public final LocalDate deliveryDate;
        public final double grossAmount;
        public final double miles;
        
        public LoadDetail(String loadNumber, String customer, String pickupLocation, 
                         String dropLocation, LocalDate deliveryDate, double grossAmount, double miles) {
            this.loadNumber = loadNumber;
            this.customer = customer;
            this.pickupLocation = pickupLocation;
            this.dropLocation = dropLocation;
            this.deliveryDate = deliveryDate;
            this.grossAmount = grossAmount;
            this.miles = miles;
        }
    }
    
    public DriverIncomeData(int driverId, String driverName, String truckUnit, 
                           LocalDate startDate, LocalDate endDate) {
        this.driverId = driverId;
        this.driverName = driverName;
        this.truckUnit = truckUnit;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    // Getters and setters
    public int getDriverId() { return driverId; }
    public String getDriverName() { return driverName; }
    public String getTruckUnit() { return truckUnit; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    
    public int getTotalLoads() { return totalLoads; }
    public void setTotalLoads(int totalLoads) { this.totalLoads = totalLoads; }
    
    public double getTotalGross() { return totalGross; }
    public void setTotalGross(double totalGross) { 
        this.totalGross = totalGross;
        calculateAveragePerMile();
    }
    
    public double getTotalMiles() { return totalMiles; }
    public void setTotalMiles(double totalMiles) { 
        this.totalMiles = totalMiles;
        calculateAveragePerMile();
    }
    
    public List<LoadDetail> getLoads() { return loads; }
    public void setLoads(List<LoadDetail> loads) { this.loads = loads; }
    
    public double getTotalFuelCost() { return totalFuelCost; }
    public void setTotalFuelCost(double totalFuelCost) { this.totalFuelCost = totalFuelCost; }
    
    public double getTotalFuelFees() { return totalFuelFees; }
    public void setTotalFuelFees(double totalFuelFees) { this.totalFuelFees = totalFuelFees; }
    
    public double getTotalFuelAmount() { return totalFuelAmount; }
    public void setTotalFuelAmount(double totalFuelAmount) { this.totalFuelAmount = totalFuelAmount; }
    
    public double getServiceFee() { return serviceFee; }
    public void setServiceFee(double serviceFee) { this.serviceFee = serviceFee; }
    
    public double getRecurringFees() { return recurringFees; }
    public void setRecurringFees(double recurringFees) { this.recurringFees = recurringFees; }
    
    public double getAdvanceRepayments() { return advanceRepayments; }
    public void setAdvanceRepayments(double advanceRepayments) { this.advanceRepayments = advanceRepayments; }
    
    public double getEscrowDeposits() { return escrowDeposits; }
    public void setEscrowDeposits(double escrowDeposits) { this.escrowDeposits = escrowDeposits; }
    
    public double getOtherDeductions() { return otherDeductions; }
    public void setOtherDeductions(double otherDeductions) { this.otherDeductions = otherDeductions; }
    
    public double getReimbursements() { return reimbursements; }
    public void setReimbursements(double reimbursements) { this.reimbursements = reimbursements; }
    
    public double getNetPay() { return netPay; }
    public void setNetPay(double netPay) { this.netPay = netPay; }
    
    public double getAveragePerMile() { return averagePerMile; }
    
    public double getFuelEfficiency() { return fuelEfficiency; }
    public void setFuelEfficiency(double fuelEfficiency) { this.fuelEfficiency = fuelEfficiency; }
    
    private void calculateAveragePerMile() {
        if (totalMiles > 0) {
            averagePerMile = netPay / totalMiles;
        } else {
            averagePerMile = 0;
        }
    }
    
    public void calculateNetPay() {
        double totalDeductions = serviceFee + totalFuelAmount + recurringFees + 
                               advanceRepayments + escrowDeposits + otherDeductions;
        netPay = totalGross - totalDeductions + reimbursements;
        calculateAveragePerMile();
    }
}