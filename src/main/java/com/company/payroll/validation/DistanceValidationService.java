package com.company.payroll.validation;

import com.company.payroll.services.DistanceCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for validating zip codes and distance calculations.
 * Provides comprehensive validation for per-mile payment calculations.
 */
public class DistanceValidationService {
    private static final Logger logger = LoggerFactory.getLogger(DistanceValidationService.class);
    
    // Zip code validation pattern
    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$");
    
    // Distance validation thresholds
    private static final double MIN_VALID_DISTANCE = 0.0;
    private static final double MAX_VALID_DISTANCE = 3500.0; // Continental US max
    private static final double SAME_ZIP_MAX_DISTANCE = 10.0; // Max for same zip
    private static final double WARNING_DISTANCE_THRESHOLD = 2500.0; // Trigger warning
    
    private final DistanceCalculationService distanceService;
    
    public DistanceValidationService() {
        this.distanceService = new DistanceCalculationService();
    }
    
    public DistanceValidationService(DistanceCalculationService distanceService) {
        this.distanceService = distanceService;
    }
    
    /**
     * Validate a zip code format.
     * @param zipCode The zip code to validate
     * @return ValidationResult with details
     */
    public ValidationResult validateZipCode(String zipCode) {
        ValidationResult result = new ValidationResult();
        
        if (zipCode == null || zipCode.trim().isEmpty()) {
            result.addError("Zip code is required");
            return result;
        }
        
        String trimmed = zipCode.trim();
        
        // Check format
        if (!ZIP_PATTERN.matcher(trimmed).matches()) {
            result.addError("Invalid zip code format. Must be 5 digits or 5+4 format (e.g., 12345 or 12345-6789)");
            return result;
        }
        
        // Extract 5-digit portion
        String fiveDigit = trimmed.substring(0, 5);
        
        // Check for known invalid patterns
        if (fiveDigit.equals("00000")) {
            result.addError("Invalid zip code: 00000");
            return result;
        }
        
        // Check for military/special zip codes (might need special handling)
        if (fiveDigit.startsWith("090") || fiveDigit.startsWith("091") || 
            fiveDigit.startsWith("092") || fiveDigit.startsWith("093") ||
            fiveDigit.startsWith("094") || fiveDigit.startsWith("095") ||
            fiveDigit.startsWith("096") || fiveDigit.startsWith("097") ||
            fiveDigit.startsWith("098") || fiveDigit.startsWith("099")) {
            result.addWarning("Military/APO/FPO zip code detected - distance calculations may be estimates");
        }
        
        // Check for Puerto Rico, Virgin Islands, etc.
        if (fiveDigit.startsWith("006") || fiveDigit.startsWith("007") || 
            fiveDigit.startsWith("008") || fiveDigit.startsWith("009")) {
            result.addWarning("US territory zip code detected - ensure distance calculations are appropriate");
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * Validate a distance calculation result.
     * @param fromZip Origin zip code
     * @param toZip Destination zip code
     * @param calculatedDistance The calculated distance
     * @return ValidationResult with details
     */
    public ValidationResult validateDistanceCalculation(String fromZip, String toZip, double calculatedDistance) {
        ValidationResult result = new ValidationResult();
        
        // Validate zip codes first
        ValidationResult fromValidation = validateZipCode(fromZip);
        if (!fromValidation.isValid()) {
            result.addError("Invalid origin zip code: " + fromValidation.getFirstError());
            return result;
        }
        
        ValidationResult toValidation = validateZipCode(toZip);
        if (!toValidation.isValid()) {
            result.addError("Invalid destination zip code: " + toValidation.getFirstError());
            return result;
        }
        
        // Copy any warnings from zip validations
        result.getWarnings().addAll(fromValidation.getWarnings());
        result.getWarnings().addAll(toValidation.getWarnings());
        
        // Validate distance value
        if (calculatedDistance < MIN_VALID_DISTANCE) {
            result.addError("Invalid distance: cannot be negative");
            return result;
        }
        
        if (calculatedDistance > MAX_VALID_DISTANCE) {
            result.addError(String.format("Invalid distance: %.1f miles exceeds maximum continental US distance", 
                                        calculatedDistance));
            return result;
        }
        
        // Normalize zip codes for comparison
        String normalizedFrom = normalizeZipCode(fromZip);
        String normalizedTo = normalizeZipCode(toZip);
        
        // Same zip validation
        if (normalizedFrom.equals(normalizedTo)) {
            if (calculatedDistance > SAME_ZIP_MAX_DISTANCE) {
                result.addWarning(String.format("Distance of %.1f miles seems high for same zip code", 
                                              calculatedDistance));
            }
        }
        
        // Check if distance is reasonable
        if (!distanceService.isReasonableDistance(fromZip, toZip, calculatedDistance)) {
            result.addWarning("Distance seems unusually high for these zip codes");
        }
        
        // Warning for very long distances
        if (calculatedDistance > WARNING_DISTANCE_THRESHOLD) {
            result.addWarning(String.format("Very long distance (%.1f miles) - please verify", 
                                          calculatedDistance));
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * Check if distance is reasonable for given zip codes.
     * @param fromZip Origin zip code
     * @param toZip Destination zip code
     * @param distance The distance to check
     * @return true if reasonable
     */
    public boolean isReasonableDistance(String fromZip, String toZip, double distance) {
        return distanceService.isReasonableDistance(fromZip, toZip, distance);
    }
    
    /**
     * Validate batch of zip code pairs.
     * @param pairs Array of zip code pairs [fromZip, toZip]
     * @return List of validation results
     */
    public List<ValidationResult> validateBatch(String[][] pairs) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (pairs == null || pairs.length == 0) {
            ValidationResult result = new ValidationResult();
            result.addError("No zip code pairs provided");
            results.add(result);
            return results;
        }
        
        for (String[] pair : pairs) {
            ValidationResult result = new ValidationResult();
            
            if (pair == null || pair.length < 2) {
                result.addError("Invalid pair format");
            } else {
                ValidationResult fromValidation = validateZipCode(pair[0]);
                ValidationResult toValidation = validateZipCode(pair[1]);
                
                if (!fromValidation.isValid()) {
                    result.addError("Invalid origin: " + fromValidation.getFirstError());
                }
                if (!toValidation.isValid()) {
                    result.addError("Invalid destination: " + toValidation.getFirstError());
                }
                
                if (fromValidation.isValid() && toValidation.isValid()) {
                    result.setValid(true);
                    result.getWarnings().addAll(fromValidation.getWarnings());
                    result.getWarnings().addAll(toValidation.getWarnings());
                }
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Get sanity check message for distance.
     * @param distance The distance in miles
     * @return Sanity check message or null
     */
    public String getDistanceSanityCheck(double distance) {
        if (distance < 0) {
            return "Distance cannot be negative";
        } else if (distance == 0) {
            return "Same location - no distance";
        } else if (distance < 1) {
            return "Very short distance - within same area";
        } else if (distance > 3000) {
            return "Extremely long distance - verify zip codes";
        } else if (distance > 2000) {
            return "Cross-country distance - typical for coast-to-coast";
        } else if (distance > 1000) {
            return "Long distance - multiple states";
        } else if (distance > 500) {
            return "Regional distance - crossing state lines";
        } else if (distance > 200) {
            return "In-state long distance";
        } else if (distance > 50) {
            return "Regional delivery";
        } else {
            return "Local delivery";
        }
    }
    
    /**
     * Validate per-mile rate.
     * @param rate The per-mile rate
     * @return ValidationResult with details
     */
    public ValidationResult validatePerMileRate(double rate) {
        ValidationResult result = new ValidationResult();
        
        if (rate <= 0) {
            result.addError("Per-mile rate must be greater than $0");
            return result;
        }
        
        if (rate > 10.0) {
            result.addError("Per-mile rate cannot exceed $10 per mile");
            return result;
        }
        
        // Warnings for unusual rates
        if (rate < 0.50) {
            result.addWarning(String.format("Per-mile rate of $%.2f seems unusually low", rate));
        } else if (rate > 5.00) {
            result.addWarning(String.format("Per-mile rate of $%.2f seems unusually high", rate));
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * Normalize zip code for consistency.
     */
    private String normalizeZipCode(String zipCode) {
        if (zipCode == null) {
            return "";
        }
        
        // Remove all non-digits
        String normalized = zipCode.trim().replaceAll("[^0-9]", "");
        
        // Take only first 5 digits
        if (normalized.length() >= 5) {
            return normalized.substring(0, 5);
        }
        
        return normalized;
    }
    
    /**
     * Validation result container.
     */
    public static class ValidationResult {
        private boolean valid = false;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
        
        public String getFirstWarning() {
            return warnings.isEmpty() ? null : warnings.get(0);
        }
        
        public String getAllErrors() {
            return String.join(", ", errors);
        }
        
        public String getAllWarnings() {
            return String.join(", ", warnings);
        }
        
        @Override
        public String toString() {
            if (valid) {
                if (hasWarnings()) {
                    return "Valid with warnings: " + getAllWarnings();
                }
                return "Valid";
            } else {
                return "Invalid: " + getAllErrors();
            }
        }
    }
}
