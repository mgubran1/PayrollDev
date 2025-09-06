package com.company.payroll.calculators;

import com.company.payroll.employees.PaymentType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for payment method calculations.
 * Encapsulates all settings needed for payment calculations.
 */
public class PaymentMethodConfiguration {
    private PaymentType paymentType;
    private LocalDate effectiveDate;
    
    // Percentage configuration
    private double driverPercent;
    private double companyPercent;
    private double serviceFeePercent;
    
    // Flat rate configuration
    private double flatRateAmount;
    
    // Per mile configuration
    private double perMileRate;
    
    // Additional settings
    private boolean requireZipCodes;
    private boolean requireGrossAmount;
    private String notes;
    
    // Validation state
    private boolean validated = false;
    private String validationError;
    
    public PaymentMethodConfiguration() {
        this.effectiveDate = LocalDate.now();
    }
    
    public PaymentMethodConfiguration(PaymentType paymentType) {
        this();
        this.paymentType = paymentType;
        updateRequirements();
    }
    
    /**
     * Create configuration for percentage payment.
     */
    public static PaymentMethodConfiguration createPercentageConfiguration(
            double driverPercent, double companyPercent, double serviceFeePercent) {
        PaymentMethodConfiguration config = new PaymentMethodConfiguration(PaymentType.PERCENTAGE);
        config.setDriverPercent(driverPercent);
        config.setCompanyPercent(companyPercent);
        config.setServiceFeePercent(serviceFeePercent);
        return config;
    }
    
    /**
     * Create configuration for flat rate payment.
     */
    public static PaymentMethodConfiguration createFlatRateConfiguration(double flatRateAmount) {
        PaymentMethodConfiguration config = new PaymentMethodConfiguration(PaymentType.FLAT_RATE);
        config.setFlatRateAmount(flatRateAmount);
        return config;
    }
    
    /**
     * Create configuration for per mile payment.
     */
    public static PaymentMethodConfiguration createPerMileConfiguration(double perMileRate) {
        PaymentMethodConfiguration config = new PaymentMethodConfiguration(PaymentType.PER_MILE);
        config.setPerMileRate(perMileRate);
        return config;
    }
    
    /**
     * Validate this configuration.
     * @return true if valid
     */
    public boolean validate() {
        validated = true;
        validationError = null;
        
        if (paymentType == null) {
            validationError = "Payment type is required";
            return false;
        }
        
        validationError = paymentType.getValidationError(
            driverPercent, companyPercent, serviceFeePercent, flatRateAmount, perMileRate);
        
        return validationError == null;
    }
    
    /**
     * Get validation error if configuration is invalid.
     * @return Error message or null if valid
     */
    public String getValidationError() {
        if (!validated) {
            validate();
        }
        return validationError;
    }
    
    /**
     * Check if configuration is valid.
     * @return true if valid
     */
    public boolean isValid() {
        if (!validated) {
            validate();
        }
        return validationError == null;
    }
    
    /**
     * Update requirements based on payment type.
     */
    private void updateRequirements() {
        if (paymentType != null) {
            requireZipCodes = paymentType.requiresZipCodes();
            requireGrossAmount = paymentType.requiresGrossAmount();
        }
    }
    
    /**
     * Get a description of this configuration.
     * @return Human-readable description
     */
    public String getDescription() {
        if (paymentType == null) {
            return "No payment method configured";
        }
        
        switch (paymentType) {
            case PERCENTAGE:
                return String.format("%s: Driver %.2f%%, Company %.2f%%, Service %.2f%%",
                                   paymentType.getDisplayName(), 
                                   driverPercent, companyPercent, serviceFeePercent);
            case FLAT_RATE:
                return String.format("%s: $%.2f per load", 
                                   paymentType.getDisplayName(), flatRateAmount);
            case PER_MILE:
                return String.format("%s: $%.2f per mile", 
                                   paymentType.getDisplayName(), perMileRate);
            default:
                return paymentType.getDisplayName();
        }
    }
    
    /**
     * Get a summary of this configuration.
     * @return Summary including effective date
     */
    public String getSummary() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        return String.format("%s (Effective: %s)", 
                           getDescription(), 
                           effectiveDate != null ? effectiveDate.format(formatter) : "Not set");
    }
    
    /**
     * Convert to a map for display or storage.
     * @return Map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        
        map.put("paymentType", paymentType != null ? paymentType.name() : null);
        map.put("effectiveDate", effectiveDate != null ? effectiveDate.toString() : null);
        
        if (paymentType == PaymentType.PERCENTAGE) {
            map.put("driverPercent", driverPercent);
            map.put("companyPercent", companyPercent);
            map.put("serviceFeePercent", serviceFeePercent);
        } else if (paymentType == PaymentType.FLAT_RATE) {
            map.put("flatRateAmount", flatRateAmount);
        } else if (paymentType == PaymentType.PER_MILE) {
            map.put("perMileRate", perMileRate);
        }
        
        map.put("requireZipCodes", requireZipCodes);
        map.put("requireGrossAmount", requireGrossAmount);
        map.put("notes", notes);
        
        return map;
    }
    
    /**
     * Calculate payment for given inputs.
     * @param grossAmount Gross amount of load
     * @param miles Distance in miles
     * @return Calculated payment amount
     */
    public double calculatePayment(double grossAmount, double miles) {
        if (paymentType == null) {
            return 0.0;
        }
        
        return paymentType.calculatePayment(grossAmount, miles, driverPercent, 
                                          flatRateAmount, perMileRate);
    }
    
    /**
     * Get formatted rate for display.
     * @return Formatted rate string
     */
    public String getFormattedRate() {
        if (paymentType == null) {
            return "Not configured";
        }
        
        switch (paymentType) {
            case PERCENTAGE:
                return String.format("%.2f%%", driverPercent);
            case FLAT_RATE:
                return String.format("$%.2f", flatRateAmount);
            case PER_MILE:
                return String.format("$%.2f/mile", perMileRate);
            default:
                return "Unknown";
        }
    }
    
    /**
     * Clone this configuration.
     * @return New instance with same values
     */
    public PaymentMethodConfiguration clone() {
        PaymentMethodConfiguration clone = new PaymentMethodConfiguration();
        clone.paymentType = this.paymentType;
        clone.effectiveDate = this.effectiveDate;
        clone.driverPercent = this.driverPercent;
        clone.companyPercent = this.companyPercent;
        clone.serviceFeePercent = this.serviceFeePercent;
        clone.flatRateAmount = this.flatRateAmount;
        clone.perMileRate = this.perMileRate;
        clone.requireZipCodes = this.requireZipCodes;
        clone.requireGrossAmount = this.requireGrossAmount;
        clone.notes = this.notes;
        clone.updateRequirements();
        return clone;
    }
    
    // Getters and setters
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) { 
        this.paymentType = paymentType;
        updateRequirements();
        validated = false;
    }
    
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { 
        this.effectiveDate = effectiveDate;
    }
    
    public double getDriverPercent() { return driverPercent; }
    public void setDriverPercent(double driverPercent) { 
        this.driverPercent = driverPercent;
        validated = false;
    }
    
    public double getCompanyPercent() { return companyPercent; }
    public void setCompanyPercent(double companyPercent) { 
        this.companyPercent = companyPercent;
        validated = false;
    }
    
    public double getServiceFeePercent() { return serviceFeePercent; }
    public void setServiceFeePercent(double serviceFeePercent) { 
        this.serviceFeePercent = serviceFeePercent;
        validated = false;
    }
    
    public double getFlatRateAmount() { return flatRateAmount; }
    public void setFlatRateAmount(double flatRateAmount) { 
        this.flatRateAmount = flatRateAmount;
        validated = false;
    }
    
    public double getPerMileRate() { return perMileRate; }
    public void setPerMileRate(double perMileRate) { 
        this.perMileRate = perMileRate;
        validated = false;
    }
    
    public boolean isRequireZipCodes() { return requireZipCodes; }
    public boolean isRequireGrossAmount() { return requireGrossAmount; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    @Override
    public String toString() {
        return getSummary();
    }
}
