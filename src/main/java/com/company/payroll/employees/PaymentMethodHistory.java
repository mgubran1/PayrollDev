package com.company.payroll.employees;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing payment method history for employees.
 * Tracks all payment method configurations over time with complete audit trail.
 */
public class PaymentMethodHistory {
    private int id;
    private int employeeId;
    private PaymentType paymentType;
    
    // Percentage payment fields
    private double driverPercent;
    private double companyPercent;
    private double serviceFeePercent;
    
    // Flat rate payment fields
    private double flatRateAmount;
    
    // Per mile payment fields
    private double perMileRate;
    
    // Date range fields
    private LocalDate effectiveDate;
    private LocalDate endDate;
    
    // Audit fields
    private String createdBy;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String notes;
    
    // Transient fields for display
    private String employeeName;
    
    public PaymentMethodHistory() {
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
        this.createdBy = "SYSTEM";
    }
    
    public PaymentMethodHistory(int employeeId, PaymentType paymentType, LocalDate effectiveDate) {
        this();
        this.employeeId = employeeId;
        this.paymentType = paymentType;
        this.effectiveDate = effectiveDate;
    }
    
    // Full constructor
    public PaymentMethodHistory(int id, int employeeId, PaymentType paymentType,
                              double driverPercent, double companyPercent, double serviceFeePercent,
                              double flatRateAmount, double perMileRate,
                              LocalDate effectiveDate, LocalDate endDate,
                              String createdBy, LocalDateTime createdDate, LocalDateTime modifiedDate,
                              String notes) {
        this.id = id;
        this.employeeId = employeeId;
        this.paymentType = paymentType;
        this.driverPercent = driverPercent;
        this.companyPercent = companyPercent;
        this.serviceFeePercent = serviceFeePercent;
        this.flatRateAmount = flatRateAmount;
        this.perMileRate = perMileRate;
        this.effectiveDate = effectiveDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.modifiedDate = modifiedDate;
        this.notes = notes;
    }
    
    // Validation methods
    public boolean isValid() {
        if (paymentType == null || effectiveDate == null) {
            return false;
        }
        
        // Validate date range
        if (endDate != null && endDate.isBefore(effectiveDate)) {
            return false;
        }
        
        // Validate payment type specific fields
        return paymentType.isValidConfiguration(driverPercent, companyPercent, serviceFeePercent,
                                               flatRateAmount, perMileRate);
    }
    
    public String getValidationError() {
        if (paymentType == null) {
            return "Payment type is required";
        }
        
        if (effectiveDate == null) {
            return "Effective date is required";
        }
        
        if (endDate != null && endDate.isBefore(effectiveDate)) {
            return "End date cannot be before effective date";
        }
        
        String paymentError = paymentType.getValidationError(driverPercent, companyPercent, 
                                                           serviceFeePercent, flatRateAmount, perMileRate);
        if (paymentError != null) {
            return paymentError;
        }
        
        return null;
    }
    
    // Description methods for display
    public String getDescription() {
        if (paymentType == null) {
            return "No payment method configured";
        }
        
        switch (paymentType) {
            case PERCENTAGE:
                return String.format("%s: Driver %.2f%%, Company %.2f%%, Service Fee %.2f%%",
                                   paymentType.getDisplayName(), driverPercent, companyPercent, serviceFeePercent);
            case FLAT_RATE:
                return String.format("%s: $%.2f per load", paymentType.getDisplayName(), flatRateAmount);
            case PER_MILE:
                return String.format("%s: $%.2f per mile", paymentType.getDisplayName(), perMileRate);
            default:
                return paymentType.getDisplayName();
        }
    }
    
    public String getDateRangeDescription() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String start = effectiveDate != null ? effectiveDate.format(formatter) : "Unknown";
        
        if (endDate == null) {
            return start + " - Current";
        } else {
            return start + " - " + endDate.format(formatter);
        }
    }
    
    public boolean isActive() {
        return endDate == null || endDate.isAfter(LocalDate.now());
    }
    
    public boolean isActiveOn(LocalDate date) {
        if (date == null || effectiveDate == null) {
            return false;
        }
        
        if (date.isBefore(effectiveDate)) {
            return false;
        }
        
        return endDate == null || !date.isAfter(endDate);
    }
    
    // Business rule validation
    public boolean hasReasonableRates() {
        switch (paymentType) {
            case PERCENTAGE:
                return driverPercent >= 50 && driverPercent <= 90; // Typical range
            case FLAT_RATE:
                return flatRateAmount >= 100 && flatRateAmount <= 5000; // $100-$5000 per load
            case PER_MILE:
                return perMileRate >= 0.50 && perMileRate <= 5.00; // $0.50-$5.00 per mile
            default:
                return true;
        }
    }
    
    public String getRateWarning() {
        if (!hasReasonableRates()) {
            switch (paymentType) {
                case PERCENTAGE:
                    if (driverPercent < 50) {
                        return String.format("Driver percentage (%.2f%%) is unusually low", driverPercent);
                    } else if (driverPercent > 90) {
                        return String.format("Driver percentage (%.2f%%) is unusually high", driverPercent);
                    }
                    break;
                case FLAT_RATE:
                    if (flatRateAmount < 100) {
                        return String.format("Flat rate ($%.2f) is unusually low", flatRateAmount);
                    } else if (flatRateAmount > 5000) {
                        return String.format("Flat rate ($%.2f) is unusually high", flatRateAmount);
                    }
                    break;
                case PER_MILE:
                    if (perMileRate < 0.50) {
                        return String.format("Per mile rate ($%.2f) is unusually low", perMileRate);
                    } else if (perMileRate > 5.00) {
                        return String.format("Per mile rate ($%.2f) is unusually high", perMileRate);
                    }
                    break;
            }
        }
        return null;
    }
    
    // Conversion methods
    public void copyFromEmployee(Employee employee) {
        if (employee == null) {
            return;
        }
        
        this.employeeId = employee.getId();
        this.paymentType = employee.getPaymentType();
        this.driverPercent = employee.getDriverPercent();
        this.companyPercent = employee.getCompanyPercent();
        this.serviceFeePercent = employee.getServiceFeePercent();
        this.flatRateAmount = employee.getFlatRateAmount();
        this.perMileRate = employee.getPerMileRate();
        this.effectiveDate = employee.getPaymentEffectiveDate();
        this.notes = employee.getPaymentNotes();
    }
    
    public void applyToEmployee(Employee employee) {
        if (employee == null) {
            return;
        }
        
        employee.setPaymentType(this.paymentType);
        employee.setDriverPercent(this.driverPercent);
        employee.setCompanyPercent(this.companyPercent);
        employee.setServiceFeePercent(this.serviceFeePercent);
        employee.setFlatRateAmount(this.flatRateAmount);
        employee.setPerMileRate(this.perMileRate);
        employee.setPaymentEffectiveDate(this.effectiveDate);
        employee.setPaymentNotes(this.notes);
    }
    
    // Calculate payment for a load
    public double calculateLoadPayment(double grossAmount, double miles) {
        if (paymentType == null) {
            return 0.0;
        }
        
        return paymentType.calculatePayment(grossAmount, miles, driverPercent, flatRateAmount, perMileRate);
    }
    
    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getEmployeeId() { return employeeId; }
    public void setEmployeeId(int employeeId) { this.employeeId = employeeId; }
    
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) { 
        this.paymentType = paymentType;
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
    
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { 
        this.effectiveDate = effectiveDate;
        updateModified();
    }
    
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { 
        this.endDate = endDate;
        updateModified();
    }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    
    public LocalDateTime getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(LocalDateTime modifiedDate) { this.modifiedDate = modifiedDate; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { 
        this.notes = notes;
        updateModified();
    }
    
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    
    private void updateModified() {
        this.modifiedDate = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return String.format("PaymentMethodHistory[%s: %s, %s]", 
                           paymentType != null ? paymentType.name() : "NONE",
                           getDescription(), 
                           getDateRangeDescription());
    }
}
