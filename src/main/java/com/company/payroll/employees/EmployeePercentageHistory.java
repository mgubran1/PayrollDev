package com.company.payroll.employees;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeePercentageHistory {
    private int id;
    private int employeeId;
    private double driverPercent;
    private double companyPercent;
    private double serviceFeePercent;
    private LocalDate effectiveDate;
    private LocalDate endDate;
    private String createdBy;
    private LocalDateTime createdDate;
    private String notes;
    
    public EmployeePercentageHistory() {}
    
    public EmployeePercentageHistory(int employeeId, double driverPercent, double companyPercent, 
                                   double serviceFeePercent, LocalDate effectiveDate) {
        this.employeeId = employeeId;
        this.driverPercent = driverPercent;
        this.companyPercent = companyPercent;
        this.serviceFeePercent = serviceFeePercent;
        this.effectiveDate = effectiveDate;
        this.createdDate = LocalDateTime.now();
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getEmployeeId() {
        return employeeId;
    }
    
    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }
    
    public double getDriverPercent() {
        return driverPercent;
    }
    
    public void setDriverPercent(double driverPercent) {
        this.driverPercent = driverPercent;
    }
    
    public double getCompanyPercent() {
        return companyPercent;
    }
    
    public void setCompanyPercent(double companyPercent) {
        this.companyPercent = companyPercent;
    }
    
    public double getServiceFeePercent() {
        return serviceFeePercent;
    }
    
    public void setServiceFeePercent(double serviceFeePercent) {
        this.serviceFeePercent = serviceFeePercent;
    }
    
    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }
    
    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
    
    public LocalDate getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public boolean isEffectiveOn(LocalDate date) {
        if (date == null || effectiveDate == null) {
            return false;
        }
        
        boolean afterOrOnEffective = !date.isBefore(effectiveDate);
        boolean beforeEnd = endDate == null || !date.isAfter(endDate);
        
        return afterOrOnEffective && beforeEnd;
    }
    
    @Override
    public String toString() {
        return String.format("PercentageHistory[driver=%.2f%%, company=%.2f%%, serviceFee=%.2f%%, effective=%s, end=%s]",
                driverPercent, companyPercent, serviceFeePercent, effectiveDate, endDate);
    }
}