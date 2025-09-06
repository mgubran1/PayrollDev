package com.company.payroll.validation;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.PaymentType;
import com.company.payroll.loads.Load;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Business rules engine for payment method operations.
 * Enforces company policies and regulations.
 */
public class PaymentMethodBusinessRules {
    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodBusinessRules.class);
    
    // Business rule constants
    private static final int MIN_DAYS_NOTICE_FOR_CHANGE = 7;
    private static final int MAX_PAYMENT_METHOD_CHANGES_PER_YEAR = 4;
    private static final double MIN_LOAD_GROSS_FOR_PERCENTAGE = 100.0;
    private static final double MAX_MILES_PER_DAY = 700.0;
    private static final double MIN_MILES_FOR_LOAD = 10.0;
    
    /**
     * Check if an employee can change payment method
     */
    public RuleResult canChangePaymentMethod(Employee employee, LocalDate proposedDate, 
                                           int changesThisYear) {
        RuleResult result = new RuleResult();
        
        // Check notice period
        LocalDate minAllowedDate = LocalDate.now().plusDays(MIN_DAYS_NOTICE_FOR_CHANGE);
        if (proposedDate.isBefore(minAllowedDate)) {
            result.addViolation(String.format("Payment method changes require %d days notice. " +
                                            "Earliest allowed date is %s", 
                                            MIN_DAYS_NOTICE_FOR_CHANGE, minAllowedDate));
        }
        
        // Check frequency limit
        if (changesThisYear >= MAX_PAYMENT_METHOD_CHANGES_PER_YEAR) {
            result.addViolation(String.format("Maximum %d payment method changes allowed per year. " +
                                            "Employee has already made %d changes.", 
                                            MAX_PAYMENT_METHOD_CHANGES_PER_YEAR, changesThisYear));
        }
        
        // Check employee status
        if (employee.getStatus() != Employee.Status.ACTIVE) {
            result.addViolation("Only active employees can change payment methods");
        }
        
        return result;
    }
    
    /**
     * Check if a load is eligible for payment calculation
     */
    public RuleResult isLoadEligibleForPayment(Load load, Employee driver) {
        RuleResult result = new RuleResult();
        
        // Check load status
        if (load.getStatus() != Load.Status.DELIVERED && 
            load.getStatus() != Load.Status.PAID) {
            result.addViolation("Load must be delivered or paid for payment calculation");
        }
        
        // Check driver assignment
        if (load.getDriver() == null || !load.getDriver().equals(driver)) {
            result.addViolation("Load must be assigned to the driver for payment calculation");
        }
        
        // Check dates
        if (load.getDeliveryDate() == null) {
            result.addViolation("Load must have a delivery date for payment calculation");
        }
        
        // Payment type specific rules
        if (driver.getPaymentType() == PaymentType.PERCENTAGE) {
            if (load.getGrossAmount() < MIN_LOAD_GROSS_FOR_PERCENTAGE) {
                result.addWarning(String.format("Load gross amount ($%.2f) is below minimum " +
                                              "threshold ($%.2f) for percentage payment", 
                                              load.getGrossAmount(), MIN_LOAD_GROSS_FOR_PERCENTAGE));
            }
        } else if (driver.getPaymentType() == PaymentType.PER_MILE) {
            // Check zip codes
            if (!Load.isValidZipCode(load.getPickupZipCode()) || 
                !Load.isValidZipCode(load.getDeliveryZipCode())) {
                result.addViolation("Valid pickup and delivery zip codes required for per-mile payment");
            }
            
            // Check calculated miles
            if (load.getCalculatedMiles() <= 0) {
                result.addViolation("Miles must be calculated before payment can be processed");
            } else if (load.getCalculatedMiles() < MIN_MILES_FOR_LOAD) {
                result.addWarning(String.format("Calculated miles (%.1f) below minimum threshold (%.1f)", 
                                              load.getCalculatedMiles(), MIN_MILES_FOR_LOAD));
            }
            
            // Check for reasonable mileage
            if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
                long daysBetween = load.getDeliveryDate().toEpochDay() - load.getPickUpDate().toEpochDay();
                if (daysBetween > 0) {
                    double milesPerDay = load.getCalculatedMiles() / daysBetween;
                    if (milesPerDay > MAX_MILES_PER_DAY) {
                        result.addWarning(String.format("Calculated miles per day (%.1f) exceeds " +
                                                      "maximum reasonable threshold (%.1f)", 
                                                      milesPerDay, MAX_MILES_PER_DAY));
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Validate payment calculation result
     */
    public RuleResult validatePaymentCalculation(double calculatedAmount, PaymentType paymentType,
                                               double grossAmount, double miles, double rate) {
        RuleResult result = new RuleResult();
        
        // Check for negative payment
        if (calculatedAmount < 0) {
            result.addViolation("Calculated payment amount cannot be negative");
        }
        
        // Payment type specific validation
        switch (paymentType) {
            case PERCENTAGE:
                double maxPossible = grossAmount;
                if (calculatedAmount > maxPossible) {
                    result.addViolation(String.format("Calculated amount ($%.2f) exceeds " +
                                                    "gross amount ($%.2f)", 
                                                    calculatedAmount, maxPossible));
                }
                break;
                
            case FLAT_RATE:
                // Flat rate should match the configured rate
                if (Math.abs(calculatedAmount - rate) > 0.01) {
                    result.addViolation(String.format("Calculated amount ($%.2f) does not match " +
                                                    "configured flat rate ($%.2f)", 
                                                    calculatedAmount, rate));
                }
                break;
                
            case PER_MILE:
                double expectedAmount = miles * rate;
                if (Math.abs(calculatedAmount - expectedAmount) > 0.01) {
                    result.addViolation(String.format("Calculated amount ($%.2f) does not match " +
                                                    "expected amount (%.1f miles × $%.2f = $%.2f)", 
                                                    calculatedAmount, miles, rate, expectedAmount));
                }
                break;
        }
        
        return result;
    }
    
    /**
     * Check if payment method configuration is allowed
     */
    public RuleResult isPaymentConfigurationAllowed(PaymentType paymentType, 
                                                  Employee.DriverType driverType,
                                                  Map<String, Object> configuration) {
        RuleResult result = new RuleResult();
        
        // Owner operators typically can't use company driver rates
        if (driverType == Employee.DriverType.OWNER_OPERATOR) {
            if (paymentType == PaymentType.PERCENTAGE) {
                Double companyPercent = (Double) configuration.get("companyPercent");
                if (companyPercent != null && companyPercent > 20.0) {
                    result.addWarning("Owner operators typically have lower company percentage");
                }
            }
        }
        
        // Company drivers typically use percentage or lower per-mile rates
        if (driverType == Employee.DriverType.COMPANY_DRIVER) {
            if (paymentType == PaymentType.PER_MILE) {
                Double perMileRate = (Double) configuration.get("perMileRate");
                if (perMileRate != null && perMileRate > 1.00) {
                    result.addWarning("Company drivers typically have per-mile rates under $1.00");
                }
            }
        }
        
        return result;
    }
    
    /**
     * Check retroactive payment method changes
     */
    public RuleResult canApplyRetroactively(LocalDate effectiveDate, LocalDate earliestLoadDate) {
        RuleResult result = new RuleResult();
        
        // Check if trying to apply retroactively
        if (effectiveDate.isBefore(LocalDate.now())) {
            result.addWarning("Applying payment method changes retroactively will affect " +
                            "historical payroll calculations");
            
            // Check how far back
            if (effectiveDate.isBefore(LocalDate.now().minusMonths(3))) {
                result.addViolation("Payment method changes cannot be applied more than 3 months " +
                                  "retroactively without special approval");
            }
            
            // Check if there are loads that would be affected
            if (earliestLoadDate != null && effectiveDate.isBefore(earliestLoadDate)) {
                result.addWarning(String.format("This change will affect loads dating back to %s", 
                                              earliestLoadDate));
            }
        }
        
        return result;
    }
    
    /**
     * Rule result class
     */
    public static class RuleResult {
        private final List<String> violations = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addViolation(String violation) {
            violations.add(violation);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isAllowed() {
            return violations.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getViolations() {
            return Collections.unmodifiableList(violations);
        }
        
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }
        
        public String getViolationMessage() {
            return String.join("\n", violations);
        }
        
        public String getWarningMessage() {
            return String.join("\n", warnings);
        }
    }
}
