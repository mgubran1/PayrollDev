package com.company.payroll.employees;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a payment method change request.
 * Used for UI operations when changing payment methods for multiple employees.
 */
public class PaymentMethodChange {
    private PaymentType paymentType;
    private LocalDate effectiveDate;
    private String notes;
    
    // Percentage configuration
    private double driverPercent;
    private double companyPercent;
    private double serviceFeePercent;
    
    // Flat rate configuration
    private double flatRateAmount;
    
    // Per mile configuration
    private double perMileRate;
    
    // Employees affected by this change
    private List<Employee> affectedEmployees;
    
    // Validation results
    private boolean valid;
    private List<String> validationErrors;
    private List<String> warnings;
    
    public PaymentMethodChange() {
        this.effectiveDate = LocalDate.now();
        this.affectedEmployees = new ArrayList<>();
        this.validationErrors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.valid = true;
    }
    
    public PaymentMethodChange(PaymentType paymentType, LocalDate effectiveDate) {
        this();
        this.paymentType = paymentType;
        this.effectiveDate = effectiveDate;
    }
    
    /**
     * Validate the payment method change.
     * @return true if valid
     */
    public boolean validate() {
        validationErrors.clear();
        warnings.clear();
        valid = true;
        
        // Basic validation
        if (paymentType == null) {
            addError("Payment type is required");
        }
        
        if (effectiveDate == null) {
            addError("Effective date is required");
        } else if (effectiveDate.isBefore(LocalDate.now().minusDays(30))) {
            addWarning("Effective date is more than 30 days in the past");
        } else if (effectiveDate.isAfter(LocalDate.now().plusDays(90))) {
            addWarning("Effective date is more than 90 days in the future");
        }
        
        if (affectedEmployees == null || affectedEmployees.isEmpty()) {
            addError("No employees selected for payment method change");
        }
        
        // Payment type specific validation
        if (paymentType != null) {
            String error = paymentType.getValidationError(driverPercent, companyPercent, 
                                                        serviceFeePercent, flatRateAmount, perMileRate);
            if (error != null) {
                addError(error);
            }
            
            // Check for reasonable rates
            PaymentMethodHistory tempHistory = createTemporaryHistory();
            if (!tempHistory.hasReasonableRates()) {
                String warning = tempHistory.getRateWarning();
                if (warning != null) {
                    addWarning(warning);
                }
            }
        }
        
        return valid;
    }
    
    /**
     * Apply this change to create payment method history entries.
     * @return List of payment method history entries
     */
    public List<PaymentMethodHistory> createHistoryEntries() {
        List<PaymentMethodHistory> histories = new ArrayList<>();
        
        if (!validate()) {
            return histories;
        }
        
        for (Employee employee : affectedEmployees) {
            PaymentMethodHistory history = new PaymentMethodHistory();
            history.setEmployeeId(employee.getId());
            history.setPaymentType(paymentType);
            history.setEffectiveDate(effectiveDate);
            history.setNotes(notes);
            
            // Set payment type specific fields
            switch (paymentType) {
                case PERCENTAGE:
                    history.setDriverPercent(driverPercent);
                    history.setCompanyPercent(companyPercent);
                    history.setServiceFeePercent(serviceFeePercent);
                    break;
                case FLAT_RATE:
                    history.setFlatRateAmount(flatRateAmount);
                    break;
                case PER_MILE:
                    history.setPerMileRate(perMileRate);
                    break;
            }
            
            histories.add(history);
        }
        
        return histories;
    }
    
    /**
     * Create a temporary history entry for validation purposes.
     */
    private PaymentMethodHistory createTemporaryHistory() {
        PaymentMethodHistory history = new PaymentMethodHistory();
        history.setPaymentType(paymentType);
        history.setDriverPercent(driverPercent);
        history.setCompanyPercent(companyPercent);
        history.setServiceFeePercent(serviceFeePercent);
        history.setFlatRateAmount(flatRateAmount);
        history.setPerMileRate(perMileRate);
        history.setEffectiveDate(effectiveDate);
        return history;
    }
    
    /**
     * Get a summary of the change for display.
     */
    public String getSummary() {
        if (paymentType == null) {
            return "No payment method selected";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Change to ").append(paymentType.getDisplayName());
        
        switch (paymentType) {
            case PERCENTAGE:
                sb.append(String.format(" (Driver: %.2f%%, Company: %.2f%%, Service: %.2f%%)",
                                      driverPercent, companyPercent, serviceFeePercent));
                break;
            case FLAT_RATE:
                sb.append(String.format(" ($%.2f per load)", flatRateAmount));
                break;
            case PER_MILE:
                sb.append(String.format(" ($%.2f per mile)", perMileRate));
                break;
        }
        
        if (effectiveDate != null) {
            sb.append(" effective ").append(effectiveDate);
        }
        
        if (affectedEmployees != null && !affectedEmployees.isEmpty()) {
            sb.append(" for ").append(affectedEmployees.size()).append(" employee(s)");
        }
        
        return sb.toString();
    }
    
    /**
     * Get details of affected employees.
     */
    public String getAffectedEmployeesDetails() {
        if (affectedEmployees == null || affectedEmployees.isEmpty()) {
            return "No employees selected";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Affected Employees (").append(affectedEmployees.size()).append("):\n");
        
        for (Employee emp : affectedEmployees) {
            sb.append("  - ").append(emp.getName());
            if (emp.getPaymentType() != null) {
                sb.append(" (Current: ").append(emp.getPaymentMethodDescription()).append(")");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private void addError(String error) {
        validationErrors.add(error);
        valid = false;
    }
    
    private void addWarning(String warning) {
        warnings.add(warning);
    }
    
    // Getters and setters
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) { this.paymentType = paymentType; }
    
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public double getDriverPercent() { return driverPercent; }
    public void setDriverPercent(double driverPercent) { this.driverPercent = driverPercent; }
    
    public double getCompanyPercent() { return companyPercent; }
    public void setCompanyPercent(double companyPercent) { this.companyPercent = companyPercent; }
    
    public double getServiceFeePercent() { return serviceFeePercent; }
    public void setServiceFeePercent(double serviceFeePercent) { this.serviceFeePercent = serviceFeePercent; }
    
    public double getFlatRateAmount() { return flatRateAmount; }
    public void setFlatRateAmount(double flatRateAmount) { this.flatRateAmount = flatRateAmount; }
    
    public double getPerMileRate() { return perMileRate; }
    public void setPerMileRate(double perMileRate) { this.perMileRate = perMileRate; }
    
    public List<Employee> getAffectedEmployees() { return affectedEmployees; }
    public void setAffectedEmployees(List<Employee> affectedEmployees) { 
        this.affectedEmployees = affectedEmployees; 
    }
    
    public void addAffectedEmployee(Employee employee) {
        if (affectedEmployees == null) {
            affectedEmployees = new ArrayList<>();
        }
        if (!affectedEmployees.contains(employee)) {
            affectedEmployees.add(employee);
        }
    }
    
    public boolean isValid() { return valid; }
    
    public List<String> getValidationErrors() { return validationErrors; }
    
    public List<String> getWarnings() { return warnings; }
    
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    
    @Override
    public String toString() {
        return getSummary();
    }
}
