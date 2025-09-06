package com.company.payroll.validation;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.PaymentType;
import com.company.payroll.employees.PaymentMethodHistory;
import com.company.payroll.loads.Load;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Comprehensive validation service for payment method configurations and calculations.
 * Ensures data integrity and business rule compliance across the payment system.
 */
public class PaymentMethodValidationService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodValidationService.class);
    
    // Validation constants
    private static final double MIN_PERCENTAGE = 0.0;
    private static final double MAX_PERCENTAGE = 100.0;
    private static final double MIN_FLAT_RATE = 0.0;
    private static final double MAX_FLAT_RATE = 10000.0;
    private static final double MIN_PER_MILE_RATE = 0.0;
    private static final double MAX_PER_MILE_RATE = 10.0;
    private static final double PERCENTAGE_TOTAL_TOLERANCE = 0.01;
    
    // Warning thresholds
    private static final double LOW_DRIVER_PERCENTAGE_THRESHOLD = 50.0;
    private static final double HIGH_DRIVER_PERCENTAGE_THRESHOLD = 90.0;
    private static final double LOW_FLAT_RATE_THRESHOLD = 100.0;
    private static final double HIGH_FLAT_RATE_THRESHOLD = 5000.0;
    private static final double LOW_PER_MILE_THRESHOLD = 0.50;
    private static final double HIGH_PER_MILE_THRESHOLD = 5.00;
    
    /**
     * Validates a payment method configuration for an employee
     */
    public ValidationResult validatePaymentMethodConfiguration(PaymentType paymentType, 
                                                              Double driverPercent,
                                                              Double companyPercent,
                                                              Double serviceFeePercent,
                                                              Double flatRate,
                                                              Double perMileRate,
                                                              Employee.DriverType driverType) {
        ValidationResult result = new ValidationResult();
        
        switch (paymentType) {
            case PERCENTAGE:
                validatePercentageConfiguration(driverPercent, companyPercent, serviceFeePercent, result);
                break;
            case FLAT_RATE:
                validateFlatRateConfiguration(flatRate, result);
                break;
            case PER_MILE:
                validatePerMileConfiguration(perMileRate, driverType, result);
                break;
        }
        
        return result;
    }
    
    /**
     * Validates percentage-based payment configuration
     */
    private void validatePercentageConfiguration(Double driverPercent, Double companyPercent, 
                                               Double serviceFeePercent, ValidationResult result) {
        // Check nulls
        if (driverPercent == null || companyPercent == null || serviceFeePercent == null) {
            result.addError("All percentage values are required for percentage-based payment");
            return;
        }
        
        // Check ranges
        if (driverPercent < MIN_PERCENTAGE || driverPercent > MAX_PERCENTAGE) {
            result.addError(String.format("Driver percentage must be between %.2f%% and %.2f%%", 
                          MIN_PERCENTAGE, MAX_PERCENTAGE));
        }
        
        if (companyPercent < MIN_PERCENTAGE || companyPercent > MAX_PERCENTAGE) {
            result.addError(String.format("Company percentage must be between %.2f%% and %.2f%%", 
                          MIN_PERCENTAGE, MAX_PERCENTAGE));
        }
        
        if (serviceFeePercent < MIN_PERCENTAGE || serviceFeePercent > MAX_PERCENTAGE) {
            result.addError(String.format("Service fee percentage must be between %.2f%% and %.2f%%", 
                          MIN_PERCENTAGE, MAX_PERCENTAGE));
        }
        
        // Check total equals 100%
        double total = driverPercent + companyPercent + serviceFeePercent;
        if (Math.abs(total - 100.0) > PERCENTAGE_TOTAL_TOLERANCE) {
            result.addError(String.format("Percentages must total 100%%. Current total: %.2f%%", total));
        }
        
        // Add warnings
        if (driverPercent < LOW_DRIVER_PERCENTAGE_THRESHOLD) {
            result.addWarning(String.format("Driver percentage (%.2f%%) is unusually low", driverPercent));
        }
        
        if (driverPercent > HIGH_DRIVER_PERCENTAGE_THRESHOLD) {
            result.addWarning(String.format("Driver percentage (%.2f%%) is unusually high", driverPercent));
        }
    }
    
    /**
     * Validates flat rate payment configuration
     */
    private void validateFlatRateConfiguration(Double flatRate, ValidationResult result) {
        if (flatRate == null) {
            result.addError("Flat rate amount is required for flat rate payment");
            return;
        }
        
        if (flatRate < MIN_FLAT_RATE) {
            result.addError(String.format("Flat rate must be at least $%.2f", MIN_FLAT_RATE));
        }
        
        if (flatRate > MAX_FLAT_RATE) {
            result.addError(String.format("Flat rate cannot exceed $%.2f", MAX_FLAT_RATE));
        }
        
        // Add warnings
        if (flatRate < LOW_FLAT_RATE_THRESHOLD) {
            result.addWarning(String.format("Flat rate ($%.2f) is unusually low", flatRate));
        }
        
        if (flatRate > HIGH_FLAT_RATE_THRESHOLD) {
            result.addWarning(String.format("Flat rate ($%.2f) is unusually high", flatRate));
        }
    }
    
    /**
     * Validates per-mile payment configuration
     */
    private void validatePerMileConfiguration(Double perMileRate, Employee.DriverType driverType, 
                                            ValidationResult result) {
        if (perMileRate == null) {
            result.addError("Per-mile rate is required for per-mile payment");
            return;
        }
        
        if (perMileRate < MIN_PER_MILE_RATE) {
            result.addError(String.format("Per-mile rate must be at least $%.2f", MIN_PER_MILE_RATE));
        }
        
        if (perMileRate > MAX_PER_MILE_RATE) {
            result.addError(String.format("Per-mile rate cannot exceed $%.2f", MAX_PER_MILE_RATE));
        }
        
        // Add warnings based on driver type
        if (perMileRate < LOW_PER_MILE_THRESHOLD) {
            result.addWarning(String.format("Per-mile rate ($%.2f) is unusually low", perMileRate));
        }
        
        if (perMileRate > HIGH_PER_MILE_THRESHOLD) {
            result.addWarning(String.format("Per-mile rate ($%.2f) is unusually high", perMileRate));
        }
        
        // Driver type specific warnings
        if (driverType == Employee.DriverType.COMPANY_DRIVER && perMileRate > 1.00) {
            result.addWarning("Company driver rate above $1.00/mile is high for industry standards");
        }
        
        if (driverType == Employee.DriverType.OWNER_OPERATOR && perMileRate < 1.00) {
            result.addWarning("Owner operator rate below $1.00/mile is low for industry standards");
        }
    }
    
    /**
     * Validates a load for payment calculation
     */
    public ValidationResult validateLoadForPayment(Load load, Employee driver) {
        ValidationResult result = new ValidationResult();
        
        // Basic validation
        if (load == null) {
            result.addError("Load cannot be null");
            return result;
        }
        
        if (driver == null) {
            result.addError("Driver cannot be null");
            return result;
        }
        
        // Check load status
        if (load.getStatus() != Load.Status.DELIVERED && load.getStatus() != Load.Status.PAID) {
            result.addWarning("Load is not marked as delivered or paid");
        }
        
        // Check payment type specific requirements
        if (driver.getPaymentType() == PaymentType.PER_MILE) {
            if (!Load.isValidZipCode(load.getPickupZipCode())) {
                result.addError("Valid pickup zip code is required for per-mile payment");
            }
            
            if (!Load.isValidZipCode(load.getDeliveryZipCode())) {
                result.addError("Valid delivery zip code is required for per-mile payment");
            }
        }
        
        // Check gross amount
        if (driver.getPaymentType() == PaymentType.PERCENTAGE && load.getGrossAmount() <= 0) {
            result.addError("Gross amount must be greater than 0 for percentage-based payment");
        }
        
        return result;
    }
    
    /**
     * Validates payment method history for conflicts
     */
    public ValidationResult validatePaymentMethodHistory(List<PaymentMethodHistory> history, 
                                                       PaymentMethodHistory newEntry) {
        ValidationResult result = new ValidationResult();
        
        if (newEntry.getEffectiveDate() == null) {
            result.addError("Effective date is required");
            return result;
        }
        
        // Check for overlapping date ranges
        for (PaymentMethodHistory existing : history) {
            if (existing.getEmployeeId() == newEntry.getEmployeeId() && 
                existing.getId() != newEntry.getId()) {
                
                // Check if dates overlap
                LocalDate existingStart = existing.getEffectiveDate();
                LocalDate existingEnd = existing.getEndDate();
                LocalDate newStart = newEntry.getEffectiveDate();
                LocalDate newEnd = newEntry.getEndDate();
                
                boolean overlaps = false;
                
                if (existingEnd == null && newEnd == null) {
                    // Both are open-ended
                    overlaps = true;
                } else if (existingEnd == null) {
                    // Existing is open-ended
                    overlaps = newEnd == null || !newEnd.isBefore(existingStart);
                } else if (newEnd == null) {
                    // New is open-ended
                    overlaps = !newStart.isAfter(existingEnd);
                } else {
                    // Both have end dates
                    overlaps = !newStart.isAfter(existingEnd) && !newEnd.isBefore(existingStart);
                }
                
                if (overlaps) {
                    result.addError(String.format("Date range overlaps with existing payment method " +
                                                "configuration effective from %s", existingStart));
                }
            }
        }
        
        return result;
    }
    
    /**
     * Validates bulk payment method changes
     */
    public ValidationResult validateBulkPaymentMethodChange(List<Employee> employees, 
                                                          PaymentType newPaymentType,
                                                          LocalDate effectiveDate) {
        ValidationResult result = new ValidationResult();
        
        if (employees == null || employees.isEmpty()) {
            result.addError("No employees selected for payment method change");
            return result;
        }
        
        if (newPaymentType == null) {
            result.addError("Payment type is required");
            return result;
        }
        
        if (effectiveDate == null) {
            result.addError("Effective date is required");
            return result;
        }
        
        // Check if effective date is in the past
        if (effectiveDate.isBefore(LocalDate.now())) {
            result.addWarning("Effective date is in the past. This will affect historical calculations.");
        }
        
        // Check for per-mile requirements
        if (newPaymentType == PaymentType.PER_MILE) {
            result.addWarning("Per-mile payment requires valid zip codes for all loads. " +
                            "Ensure all customer addresses have zip codes before proceeding.");
        }
        
        return result;
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }
        
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }
        
        public String getErrorMessage() {
            return String.join("\n", errors);
        }
        
        public String getWarningMessage() {
            return String.join("\n", warnings);
        }
    }
}
