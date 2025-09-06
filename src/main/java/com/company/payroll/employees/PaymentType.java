package com.company.payroll.employees;

/**
 * Enumeration representing the different payment methods available for employees.
 * This enum provides validation and utility methods for each payment type.
 */
public enum PaymentType {
    
    /**
     * Percentage-based payment where driver receives a percentage of load gross amount
     */
    PERCENTAGE("Percentage of Load", 
               "Driver receives a percentage of the load's gross amount") {
        @Override
        public boolean requiresZipCodes() {
            return false;
        }
        
        @Override
        public boolean requiresGrossAmount() {
            return true;
        }
        
        @Override
        public boolean isValidConfiguration(double driverPercent, double companyPercent, 
                                          double serviceFeePercent, double flatRate, double perMileRate) {
            // Percentages must sum to 100
            double total = driverPercent + companyPercent + serviceFeePercent;
            return Math.abs(total - 100.0) < 0.01 && 
                   driverPercent >= 0 && driverPercent <= 100 &&
                   companyPercent >= 0 && companyPercent <= 100 &&
                   serviceFeePercent >= 0 && serviceFeePercent <= 100;
        }
        
        @Override
        public String getValidationError(double driverPercent, double companyPercent, 
                                       double serviceFeePercent, double flatRate, double perMileRate) {
            double total = driverPercent + companyPercent + serviceFeePercent;
            if (Math.abs(total - 100.0) >= 0.01) {
                return String.format("Percentages must sum to 100%%. Current total: %.2f%%", total);
            }
            if (driverPercent < 0 || driverPercent > 100) {
                return "Driver percentage must be between 0 and 100";
            }
            if (companyPercent < 0 || companyPercent > 100) {
                return "Company percentage must be between 0 and 100";
            }
            if (serviceFeePercent < 0 || serviceFeePercent > 100) {
                return "Service fee percentage must be between 0 and 100";
            }
            return null;
        }
    },
    
    /**
     * Flat rate payment where driver receives a fixed amount per completed load
     */
    FLAT_RATE("Flat Rate per Load", 
              "Driver receives a fixed amount for each completed load") {
        @Override
        public boolean requiresZipCodes() {
            return false;
        }
        
        @Override
        public boolean requiresGrossAmount() {
            return false;
        }
        
        @Override
        public boolean isValidConfiguration(double driverPercent, double companyPercent, 
                                          double serviceFeePercent, double flatRate, double perMileRate) {
            return flatRate > 0 && flatRate <= 10000; // Reasonable max of $10,000 per load
        }
        
        @Override
        public String getValidationError(double driverPercent, double companyPercent, 
                                       double serviceFeePercent, double flatRate, double perMileRate) {
            if (flatRate <= 0) {
                return "Flat rate must be greater than $0";
            }
            if (flatRate > 10000) {
                return "Flat rate cannot exceed $10,000 per load";
            }
            return null;
        }
    },
    
    /**
     * Per-mile payment where driver receives payment based on distance traveled
     */
    PER_MILE("Per Mile Rate", 
             "Driver receives payment based on miles from pickup to delivery") {
        @Override
        public boolean requiresZipCodes() {
            return true;
        }
        
        @Override
        public boolean requiresGrossAmount() {
            return false;
        }
        
        @Override
        public boolean isValidConfiguration(double driverPercent, double companyPercent, 
                                          double serviceFeePercent, double flatRate, double perMileRate) {
            return perMileRate > 0 && perMileRate <= 10; // Reasonable max of $10 per mile
        }
        
        @Override
        public String getValidationError(double driverPercent, double companyPercent, 
                                       double serviceFeePercent, double flatRate, double perMileRate) {
            if (perMileRate <= 0) {
                return "Per mile rate must be greater than $0";
            }
            if (perMileRate > 10) {
                return "Per mile rate cannot exceed $10 per mile";
            }
            return null;
        }
    };
    
    private final String displayName;
    private final String description;
    
    PaymentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * @return The display name for this payment type
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return The description for this payment type
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * @return true if this payment type requires zip codes for calculation
     */
    public abstract boolean requiresZipCodes();
    
    /**
     * @return true if this payment type requires gross amount for calculation
     */
    public abstract boolean requiresGrossAmount();
    
    /**
     * Validates the configuration for this payment type
     * @param driverPercent Driver percentage (used for PERCENTAGE type)
     * @param companyPercent Company percentage (used for PERCENTAGE type)
     * @param serviceFeePercent Service fee percentage (used for PERCENTAGE type)
     * @param flatRate Flat rate amount (used for FLAT_RATE type)
     * @param perMileRate Per mile rate (used for PER_MILE type)
     * @return true if configuration is valid
     */
    public abstract boolean isValidConfiguration(double driverPercent, double companyPercent, 
                                               double serviceFeePercent, double flatRate, double perMileRate);
    
    /**
     * Gets validation error message for the configuration
     * @param driverPercent Driver percentage (used for PERCENTAGE type)
     * @param companyPercent Company percentage (used for PERCENTAGE type)
     * @param serviceFeePercent Service fee percentage (used for PERCENTAGE type)
     * @param flatRate Flat rate amount (used for FLAT_RATE type)
     * @param perMileRate Per mile rate (used for PER_MILE type)
     * @return Error message or null if valid
     */
    public abstract String getValidationError(double driverPercent, double companyPercent, 
                                            double serviceFeePercent, double flatRate, double perMileRate);
    
    /**
     * Calculate payment for a load based on this payment type
     * @param grossAmount The gross amount of the load
     * @param miles The distance in miles (for PER_MILE type)
     * @param driverPercent Driver percentage (for PERCENTAGE type)
     * @param flatRate Flat rate amount (for FLAT_RATE type)
     * @param perMileRate Per mile rate (for PER_MILE type)
     * @return The calculated payment amount
     */
    public double calculatePayment(double grossAmount, double miles, double driverPercent, 
                                 double flatRate, double perMileRate) {
        switch (this) {
            case PERCENTAGE:
                return grossAmount * (driverPercent / 100.0);
            case FLAT_RATE:
                return flatRate;
            case PER_MILE:
                return miles * perMileRate;
            default:
                throw new IllegalStateException("Unknown payment type: " + this);
        }
    }
    
    /**
     * Get payment type from string value
     * @param value The string value
     * @return The payment type or null if not found
     */
    public static PaymentType fromString(String value) {
        if (value == null) {
            return null;
        }
        
        for (PaymentType type : PaymentType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Check if a payment amount is reasonable for this payment type
     * @param amount The payment amount to check
     * @param miles The distance in miles (for validation of PER_MILE payments)
     * @return true if amount seems reasonable
     */
    public boolean isReasonablePayment(double amount, double miles) {
        switch (this) {
            case PERCENTAGE:
                // Percentage payments typically range from $0 to $50,000
                return amount >= 0 && amount <= 50000;
            case FLAT_RATE:
                // Flat rate payments typically range from $50 to $10,000
                return amount >= 50 && amount <= 10000;
            case PER_MILE:
                // Per mile payments should match the calculation
                // Allow some variance for rounding
                return miles > 0 && amount >= 0 && amount <= (miles * 10);
            default:
                return false;
        }
    }
    
    /**
     * Get warning message if payment seems unusual
     * @param amount The payment amount
     * @param miles The distance in miles
     * @return Warning message or null if payment seems reasonable
     */
    public String getPaymentWarning(double amount, double miles) {
        switch (this) {
            case PERCENTAGE:
                if (amount > 25000) {
                    return String.format("Unusually high percentage payment: $%.2f", amount);
                } else if (amount < 50 && amount > 0) {
                    return String.format("Unusually low percentage payment: $%.2f", amount);
                }
                break;
            case FLAT_RATE:
                if (amount > 5000) {
                    return String.format("Unusually high flat rate payment: $%.2f", amount);
                } else if (amount < 100) {
                    return String.format("Unusually low flat rate payment: $%.2f", amount);
                }
                break;
            case PER_MILE:
                if (miles > 0) {
                    double effectiveRate = amount / miles;
                    if (effectiveRate > 5) {
                        return String.format("Unusually high per-mile rate: $%.2f/mile", effectiveRate);
                    } else if (effectiveRate < 0.50) {
                        return String.format("Unusually low per-mile rate: $%.2f/mile", effectiveRate);
                    }
                }
                break;
        }
        return null;
    }
}
