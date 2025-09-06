package com.company.payroll.calculators;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.PaymentMethodHistory;
import com.company.payroll.employees.PaymentMethodHistoryDAO;
import com.company.payroll.employees.PaymentType;
import com.company.payroll.loads.Load;
import com.company.payroll.services.DistanceCalculationService;
import com.company.payroll.validation.DistanceValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculator for determining payment amounts based on payment methods.
 * Handles percentage, flat rate, and per-mile payment calculations.
 */
public class PaymentMethodCalculator {
    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodCalculator.class);
    
    private final PaymentMethodHistoryDAO historyDAO;
    private final DistanceCalculationService distanceService;
    private final DistanceValidationService validationService;
    
    // Cache for payment method lookups
    private final Map<String, CachedMethod> methodCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 300000; // 5 minutes
    
    public PaymentMethodCalculator(Connection connection) {
        this.historyDAO = new PaymentMethodHistoryDAO(connection);
        this.distanceService = new DistanceCalculationService();
        this.validationService = new DistanceValidationService();
    }
    
    /**
     * Get effective payment method for an employee on a specific date.
     * @param employee The employee
     * @param effectiveDate The date to check
     * @return PaymentMethodHistory or null if not found
     */
    public PaymentMethodHistory getEffectivePaymentMethod(Employee employee, LocalDate effectiveDate) {
        if (employee == null || effectiveDate == null) {
            return null;
        }
        
        // Check cache first
        String cacheKey = employee.getId() + "-" + effectiveDate.toString();
        PaymentMethodHistory cached = getCachedMethod(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Look up from database
        PaymentMethodHistory history = historyDAO.getEffectivePaymentMethod(employee.getId(), effectiveDate);
        
        // If not found, create default from employee current settings
        if (history == null) {
            history = createDefaultHistory(employee, effectiveDate);
        }
        
        // Cache the result
        if (history != null) {
            cacheMethod(cacheKey, history);
        }
        
        return history;
    }
    
    /**
     * Calculate payment for a single load.
     * @param load The load to calculate payment for
     * @return PaymentCalculationResult with details
     */
    public PaymentCalculationResult calculateLoadPayment(Load load) {
        PaymentCalculationResult result = new PaymentCalculationResult();
        
        if (load == null) {
            result.addError("Load is null");
            return result;
        }
        
        if (load.getDriver() == null) {
            result.addError("Load has no driver assigned");
            return result;
        }
        
        Employee driver = load.getDriver();
        LocalDate effectiveDate = load.getDeliveryDate() != null ? 
                                 load.getDeliveryDate() : LocalDate.now();
        
        // Get effective payment method
        PaymentMethodHistory paymentMethod = getEffectivePaymentMethod(driver, effectiveDate);
        if (paymentMethod == null) {
            result.addError("No payment method found for driver");
            return result;
        }
        
        result.setPaymentMethod(paymentMethod);
        result.setPaymentType(paymentMethod.getPaymentType());
        
        // Calculate based on payment type
        switch (paymentMethod.getPaymentType()) {
            case PERCENTAGE:
                calculatePercentagePayment(load, paymentMethod, result);
                break;
                
            case FLAT_RATE:
                calculateFlatRatePayment(load, paymentMethod, result);
                break;
                
            case PER_MILE:
                calculatePerMilePayment(load, paymentMethod, result);
                break;
                
            default:
                result.addError("Unknown payment type: " + paymentMethod.getPaymentType());
        }
        
        // Update load with calculation results if successful
        if (result.isValid()) {
            load.setPaymentMethodUsed(paymentMethod.getPaymentType());
            load.setCalculatedDriverPay(result.getDriverPayment());
            load.setPaymentRateUsed(result.getRate());
        }
        
        return result;
    }
    
    /**
     * Calculate percentage-based payment.
     */
    private void calculatePercentagePayment(Load load, PaymentMethodHistory paymentMethod, 
                                          PaymentCalculationResult result) {
        double grossAmount = load.getGrossAmount();
        
        if (grossAmount <= 0) {
            result.addError("Gross amount must be greater than zero for percentage payment");
            return;
        }
        
        double driverPercent = paymentMethod.getDriverPercent();
        double companyPercent = paymentMethod.getCompanyPercent();
        double serviceFeePercent = paymentMethod.getServiceFeePercent();
        
        // Validate percentages
        double total = driverPercent + companyPercent + serviceFeePercent;
        if (Math.abs(total - 100.0) > 0.01) {
            result.addWarning(String.format("Percentages sum to %.2f%% instead of 100%%", total));
        }
        
        // Calculate payments
        double driverPayment = grossAmount * (driverPercent / 100.0);
        double companyPayment = grossAmount * (companyPercent / 100.0);
        double serviceFeePayment = grossAmount * (serviceFeePercent / 100.0);
        
        result.setDriverPayment(Math.round(driverPayment * 100.0) / 100.0);
        result.setCompanyPayment(Math.round(companyPayment * 100.0) / 100.0);
        result.setServiceFeePayment(Math.round(serviceFeePayment * 100.0) / 100.0);
        result.setRate(driverPercent);
        result.setRateDescription(String.format("%.2f%%", driverPercent));
        result.setCalculationDetails(String.format("$%.2f × %.2f%% = $%.2f", 
                                                 grossAmount, driverPercent, driverPayment));
        result.setValid(true);
        
        logger.info("Calculated percentage payment for load {}: ${}",
                   load.getLoadNumber(), result.getDriverPayment());
    }
    
    /**
     * Calculate flat rate payment.
     */
    private void calculateFlatRatePayment(Load load, PaymentMethodHistory paymentMethod, 
                                        PaymentCalculationResult result) {
        // Use load-specific flat rate if available, otherwise use driver's default
        double flatRate = load.getFlatRateAmount() > 0 ? 
                         load.getFlatRateAmount() : 
                         paymentMethod.getFlatRateAmount();
        
        if (flatRate <= 0) {
            result.addError("Flat rate must be greater than zero");
            return;
        }
        
        // Log which rate we're using
        if (load.getFlatRateAmount() > 0) {
            logger.debug("Using load-specific flat rate: ${} for load {}", 
                        flatRate, load.getLoadNumber());
        } else {
            logger.debug("Using driver default flat rate: ${} for load {}", 
                        flatRate, load.getLoadNumber());
        }
        
        // For flat rate, driver gets the flat amount
        result.setDriverPayment(flatRate);
        result.setCompanyPayment(0); // Company payment determined separately
        result.setServiceFeePayment(0); // Service fee determined separately
        result.setRate(flatRate);
        result.setRateDescription(String.format("$%.2f per load", flatRate));
        result.setCalculationDetails(String.format("Flat rate: $%.2f", flatRate));
        result.setValid(true);
        
        // Add warning if gross amount seems mismatched
        if (load.getGrossAmount() > 0) {
            double ratio = flatRate / load.getGrossAmount();
            if (ratio < 0.50) {
                result.addWarning(String.format("Flat rate is only %.1f%% of gross amount", ratio * 100));
            } else if (ratio > 1.50) {
                result.addWarning(String.format("Flat rate is %.1f%% of gross amount", ratio * 100));
            }
        }
        
        logger.info("Calculated flat rate payment for load {}: ${}",
                   load.getLoadNumber(), result.getDriverPayment());
    }
    
    /**
     * Calculate per-mile payment.
     */
    private void calculatePerMilePayment(Load load, PaymentMethodHistory paymentMethod, 
                                       PaymentCalculationResult result) {
        // Validate zip codes
        if (!load.hasValidZipCodes()) {
            result.addError("Valid pickup and delivery zip codes are required for per-mile payment");
            return;
        }
        
        double perMileRate = paymentMethod.getPerMileRate();
        if (perMileRate <= 0) {
            result.addError("Per-mile rate must be greater than zero");
            return;
        }
        
        // Get or calculate distance
        double distance = load.getCalculatedMiles();
        if (distance <= 0) {
            // Calculate distance
            distance = distanceService.calculateDistance(load.getPickupZipCode(), 
                                                       load.getDeliveryZipCode());
            if (distance < 0) {
                result.addError("Unable to calculate distance between zip codes");
                return;
            }
            
            // Update load with calculated distance
            load.setCalculatedMiles(distance);
        }
        
        // Validate distance
        DistanceValidationService.ValidationResult validation = 
            validationService.validateDistanceCalculation(load.getPickupZipCode(), 
                                                        load.getDeliveryZipCode(), 
                                                        distance);
        
        if (!validation.isValid()) {
            result.addError("Distance validation failed: " + validation.getFirstError());
            return;
        }
        
        // Add any validation warnings
        for (String warning : validation.getWarnings()) {
            result.addWarning(warning);
        }
        
        // Calculate payment
        double driverPayment = distance * perMileRate;
        
        result.setDriverPayment(Math.round(driverPayment * 100.0) / 100.0);
        result.setCompanyPayment(0); // Company payment determined separately
        result.setServiceFeePayment(0); // Service fee determined separately
        result.setRate(perMileRate);
        result.setRateDescription(String.format("$%.2f per mile", perMileRate));
        result.setCalculationDetails(String.format("%.1f miles × $%.2f/mile = $%.2f", 
                                                 distance, perMileRate, driverPayment));
        result.setDistance(distance);
        result.setValid(true);
        
        logger.info("Calculated per-mile payment for load {}: {} miles × ${}/mile = ${}",
                   load.getLoadNumber(), distance, perMileRate, result.getDriverPayment());
    }
    
    /**
     * Calculate driver payroll for a date range.
     * @param driver The driver
     * @param startDate Start date
     * @param endDate End date
     * @param loads List of loads in the period
     * @return PayrollCalculationResult with totals
     */
    public PayrollCalculationResult calculateDriverPayroll(Employee driver, LocalDate startDate, 
                                                         LocalDate endDate, List<Load> loads) {
        PayrollCalculationResult result = new PayrollCalculationResult();
        result.setDriver(driver);
        result.setStartDate(startDate);
        result.setEndDate(endDate);
        
        if (loads == null || loads.isEmpty()) {
            result.setValid(true);
            return result;
        }
        
        // Group loads by payment method
        Map<PaymentType, List<Load>> loadsByMethod = new HashMap<>();
        Map<PaymentType, Double> totalsByMethod = new HashMap<>();
        
        for (Load load : loads) {
            PaymentCalculationResult loadResult = calculateLoadPayment(load);
            
            if (loadResult.isValid()) {
                PaymentType type = loadResult.getPaymentType();
                loadsByMethod.computeIfAbsent(type, k -> new ArrayList<>()).add(load);
                totalsByMethod.merge(type, loadResult.getDriverPayment(), Double::sum);
                
                result.addLoadCalculation(load, loadResult);
                result.setTotalDriverPayment(result.getTotalDriverPayment() + loadResult.getDriverPayment());
                
                // Track additional metrics
                if (type == PaymentType.PER_MILE) {
                    result.setTotalMiles(result.getTotalMiles() + loadResult.getDistance());
                }
            } else {
                result.addError(String.format("Failed to calculate payment for load %s: %s",
                                            load.getLoadNumber(), loadResult.getFirstError()));
            }
        }
        
        // Set summary information
        result.setLoadsByPaymentMethod(loadsByMethod);
        result.setTotalsByPaymentMethod(totalsByMethod);
        result.setTotalLoads(loads.size());
        result.setValid(result.getErrors().isEmpty());
        
        logger.info("Calculated payroll for driver {}: {} loads, total payment ${}",
                   driver.getName(), loads.size(), result.getTotalDriverPayment());
        
        return result;
    }
    
    /**
     * Validate payment method configuration.
     * @param paymentMethod The payment method to validate
     * @return true if valid
     */
    public boolean validatePaymentMethodConfiguration(PaymentMethodHistory paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        
        return paymentMethod.isValid();
    }
    
    /**
     * Create default payment method history from employee.
     */
    private PaymentMethodHistory createDefaultHistory(Employee employee, LocalDate effectiveDate) {
        PaymentMethodHistory history = new PaymentMethodHistory();
        history.copyFromEmployee(employee);
        history.setEffectiveDate(effectiveDate);
        return history;
    }
    
    /**
     * Get cached payment method.
     */
    private PaymentMethodHistory getCachedMethod(String key) {
        CachedMethod cached = methodCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.method;
        }
        return null;
    }
    
    /**
     * Cache a payment method.
     */
    private void cacheMethod(String key, PaymentMethodHistory method) {
        methodCache.put(key, new CachedMethod(method));
    }
    
    /**
     * Clear the cache.
     */
    public void clearCache() {
        methodCache.clear();
        logger.info("Payment method cache cleared");
    }
    
    /**
     * Cached payment method with expiration.
     */
    private static class CachedMethod {
        final PaymentMethodHistory method;
        final long timestamp;
        
        CachedMethod(PaymentMethodHistory method) {
            this.method = method;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }
    
    /**
     * Result of a single load payment calculation.
     */
    public static class PaymentCalculationResult {
        private boolean valid = false;
        private PaymentType paymentType;
        private PaymentMethodHistory paymentMethod;
        private double driverPayment = 0.0;
        private double companyPayment = 0.0;
        private double serviceFeePayment = 0.0;
        private double rate = 0.0;
        private String rateDescription = "";
        private String calculationDetails = "";
        private double distance = 0.0;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public PaymentType getPaymentType() { return paymentType; }
        public void setPaymentType(PaymentType paymentType) { this.paymentType = paymentType; }
        
        public PaymentMethodHistory getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(PaymentMethodHistory paymentMethod) { this.paymentMethod = paymentMethod; }
        
        public double getDriverPayment() { return driverPayment; }
        public void setDriverPayment(double driverPayment) { this.driverPayment = driverPayment; }
        
        public double getCompanyPayment() { return companyPayment; }
        public void setCompanyPayment(double companyPayment) { this.companyPayment = companyPayment; }
        
        public double getServiceFeePayment() { return serviceFeePayment; }
        public void setServiceFeePayment(double serviceFeePayment) { this.serviceFeePayment = serviceFeePayment; }
        
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
        
        public String getRateDescription() { return rateDescription; }
        public void setRateDescription(String rateDescription) { this.rateDescription = rateDescription; }
        
        public String getCalculationDetails() { return calculationDetails; }
        public void setCalculationDetails(String calculationDetails) { this.calculationDetails = calculationDetails; }
        
        public double getDistance() { return distance; }
        public void setDistance(double distance) { this.distance = distance; }
        
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
    
    /**
     * Result of payroll calculation for a driver.
     */
    public static class PayrollCalculationResult {
        private boolean valid = false;
        private Employee driver;
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalLoads = 0;
        private double totalDriverPayment = 0.0;
        private double totalMiles = 0.0;
        private Map<PaymentType, List<Load>> loadsByPaymentMethod = new HashMap<>();
        private Map<PaymentType, Double> totalsByPaymentMethod = new HashMap<>();
        private Map<Load, PaymentCalculationResult> loadCalculations = new HashMap<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public Employee getDriver() { return driver; }
        public void setDriver(Employee driver) { this.driver = driver; }
        
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        
        public int getTotalLoads() { return totalLoads; }
        public void setTotalLoads(int totalLoads) { this.totalLoads = totalLoads; }
        
        public double getTotalDriverPayment() { return totalDriverPayment; }
        public void setTotalDriverPayment(double totalDriverPayment) { this.totalDriverPayment = totalDriverPayment; }
        
        public double getTotalMiles() { return totalMiles; }
        public void setTotalMiles(double totalMiles) { this.totalMiles = totalMiles; }
        
        public Map<PaymentType, List<Load>> getLoadsByPaymentMethod() { return loadsByPaymentMethod; }
        public void setLoadsByPaymentMethod(Map<PaymentType, List<Load>> loadsByPaymentMethod) { 
            this.loadsByPaymentMethod = loadsByPaymentMethod; 
        }
        
        public Map<PaymentType, Double> getTotalsByPaymentMethod() { return totalsByPaymentMethod; }
        public void setTotalsByPaymentMethod(Map<PaymentType, Double> totalsByPaymentMethod) { 
            this.totalsByPaymentMethod = totalsByPaymentMethod; 
        }
        
        public Map<Load, PaymentCalculationResult> getLoadCalculations() { return loadCalculations; }
        
        public void addLoadCalculation(Load load, PaymentCalculationResult result) {
            loadCalculations.put(load, result);
        }
        
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }
}
