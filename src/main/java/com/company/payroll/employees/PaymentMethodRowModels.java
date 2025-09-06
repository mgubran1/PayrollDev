package com.company.payroll.employees;

import javafx.beans.property.*;

/**
 * Row models for payment method configuration tables.
 * These models are used in the PaymentMethodConfigurationDialog.
 */
public class PaymentMethodRowModels {
    
    /**
     * Row model for percentage-based payment configuration.
     */
    public static class EmployeePercentageRow {
        private final Employee employee;
        private final BooleanProperty selected;
        private final StringProperty name;
        private final StringProperty truckUnit;
        private final DoubleProperty driverPercent;
        private final DoubleProperty companyPercent;
        private final DoubleProperty serviceFeePercent;
        private final StringProperty total;
        private final StringProperty status;
        
        public EmployeePercentageRow(Employee employee) {
            this.employee = employee;
            this.selected = new SimpleBooleanProperty(false);
            this.name = new SimpleStringProperty(employee.getName());
            this.truckUnit = new SimpleStringProperty(employee.getTruckUnit());
            this.driverPercent = new SimpleDoubleProperty(employee.getDriverPercent());
            this.companyPercent = new SimpleDoubleProperty(employee.getCompanyPercent());
            this.serviceFeePercent = new SimpleDoubleProperty(employee.getServiceFeePercent());
            this.total = new SimpleStringProperty(calculateTotal());
            this.status = new SimpleStringProperty(validatePercentages());
            
            // Add listeners to update total and status
            driverPercent.addListener((obs, old, val) -> {
                total.set(calculateTotal());
                status.set(validatePercentages());
            });
            companyPercent.addListener((obs, old, val) -> {
                total.set(calculateTotal());
                status.set(validatePercentages());
            });
            serviceFeePercent.addListener((obs, old, val) -> {
                total.set(calculateTotal());
                status.set(validatePercentages());
            });
        }
        
        private String calculateTotal() {
            double sum = driverPercent.get() + companyPercent.get() + serviceFeePercent.get();
            return String.format("%.2f%%", sum);
        }
        
        private String validatePercentages() {
            double sum = driverPercent.get() + companyPercent.get() + serviceFeePercent.get();
            
            if (Math.abs(sum - 100.0) > 0.01) {
                return String.format("Error: Total is %.2f%%, must equal 100%%", sum);
            }
            
            if (driverPercent.get() < 0 || companyPercent.get() < 0 || serviceFeePercent.get() < 0) {
                return "Error: Percentages cannot be negative";
            }
            
            if (driverPercent.get() > 100 || companyPercent.get() > 100 || serviceFeePercent.get() > 100) {
                return "Error: Percentages cannot exceed 100%";
            }
            
            // Warnings for unusual values
            if (driverPercent.get() < 50) {
                return "Warning: Driver percentage is unusually low";
            }
            
            if (driverPercent.get() > 90) {
                return "Warning: Driver percentage is unusually high";
            }
            
            return "Valid";
        }
        
        public boolean isValid() {
            return status.get().equals("Valid");
        }
        
        public boolean hasChanges() {
            return Math.abs(driverPercent.get() - employee.getDriverPercent()) > 0.01 ||
                   Math.abs(companyPercent.get() - employee.getCompanyPercent()) > 0.01 ||
                   Math.abs(serviceFeePercent.get() - employee.getServiceFeePercent()) > 0.01;
        }
        
        // Getters and setters
        public Employee getEmployee() { return employee; }
        
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        
        public StringProperty nameProperty() { return name; }
        public String getName() { return name.get(); }
        
        public StringProperty truckUnitProperty() { return truckUnit; }
        public String getTruckUnit() { return truckUnit.get(); }
        
        public DoubleProperty driverPercentProperty() { return driverPercent; }
        public double getDriverPercent() { return driverPercent.get(); }
        public void setDriverPercent(double value) { driverPercent.set(value); }
        
        public DoubleProperty companyPercentProperty() { return companyPercent; }
        public double getCompanyPercent() { return companyPercent.get(); }
        public void setCompanyPercent(double value) { companyPercent.set(value); }
        
        public DoubleProperty serviceFeePercentProperty() { return serviceFeePercent; }
        public double getServiceFeePercent() { return serviceFeePercent.get(); }
        public void setServiceFeePercent(double value) { serviceFeePercent.set(value); }
        
        public StringProperty totalProperty() { return total; }
        public String getTotal() { return total.get(); }
        
        public StringProperty statusProperty() { return status; }
        public String getStatus() { return status.get(); }
    }
    
    /**
     * Row model for flat rate payment configuration.
     */
    public static class EmployeeFlatRateRow {
        private final Employee employee;
        private final BooleanProperty selected;
        private final StringProperty name;
        private final StringProperty truckUnit;
        private final StringProperty currentPaymentMethod;
        private final DoubleProperty flatRateAmount;
        private final StringProperty status;
        private final BooleanProperty hasChanges;
        
        public EmployeeFlatRateRow(Employee employee) {
            this.employee = employee;
            this.selected = new SimpleBooleanProperty(false);
            this.name = new SimpleStringProperty(employee.getName());
            this.truckUnit = new SimpleStringProperty(employee.getTruckUnit());
            this.currentPaymentMethod = new SimpleStringProperty(employee.getPaymentMethodDescription());
            this.flatRateAmount = new SimpleDoubleProperty(employee.getFlatRateAmount());
            this.status = new SimpleStringProperty(validateFlatRate());
            this.hasChanges = new SimpleBooleanProperty(false);
            
            // Add listener to update status and changes
            flatRateAmount.addListener((obs, old, val) -> {
                status.set(validateFlatRate());
                hasChanges.set(Math.abs(val.doubleValue() - employee.getFlatRateAmount()) > 0.01);
            });
        }
        
        private String validateFlatRate() {
            double rate = flatRateAmount.get();
            
            if (rate <= 0) {
                return "Error: Flat rate must be greater than $0";
            }
            
            if (rate > 10000) {
                return "Error: Flat rate cannot exceed $10,000";
            }
            
            // Warnings for unusual values
            if (rate < 100) {
                return "Warning: Flat rate below $100 is unusually low";
            }
            
            if (rate > 5000) {
                return "Warning: Flat rate above $5,000 is unusually high";
            }
            
            return "Valid";
        }
        
        public boolean isValid() {
            return !status.get().startsWith("Error");
        }
        
        // Getters and setters
        public Employee getEmployee() { return employee; }
        
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        
        public StringProperty nameProperty() { return name; }
        public String getName() { return name.get(); }
        
        public StringProperty truckUnitProperty() { return truckUnit; }
        public String getTruckUnit() { return truckUnit.get(); }
        
        public StringProperty currentPaymentMethodProperty() { return currentPaymentMethod; }
        public String getCurrentPaymentMethod() { return currentPaymentMethod.get(); }
        
        public DoubleProperty flatRateAmountProperty() { return flatRateAmount; }
        public double getFlatRateAmount() { return flatRateAmount.get(); }
        public void setFlatRateAmount(double value) { flatRateAmount.set(value); }
        
        public StringProperty statusProperty() { return status; }
        public String getStatus() { return status.get(); }
        
        public BooleanProperty hasChangesProperty() { return hasChanges; }
        public boolean hasChanges() { return hasChanges.get(); }
    }
    
    /**
     * Row model for per-mile payment configuration.
     */
    public static class EmployeePerMileRow {
        private final Employee employee;
        private final BooleanProperty selected;
        private final StringProperty name;
        private final StringProperty truckUnit;
        private final StringProperty currentPaymentMethod;
        private final DoubleProperty perMileRate;
        private final StringProperty estimatedWeeklyMiles;
        private final StringProperty estimatedWeeklyPay;
        private final StringProperty status;
        private final BooleanProperty hasChanges;
        
        // Average miles per week (used for estimation)
        private static final double AVG_MILES_PER_WEEK = 2500;
        
        public EmployeePerMileRow(Employee employee) {
            this.employee = employee;
            this.selected = new SimpleBooleanProperty(false);
            this.name = new SimpleStringProperty(employee.getName());
            this.truckUnit = new SimpleStringProperty(employee.getTruckUnit());
            this.currentPaymentMethod = new SimpleStringProperty(employee.getPaymentMethodDescription());
            this.perMileRate = new SimpleDoubleProperty(employee.getPerMileRate());
            this.estimatedWeeklyMiles = new SimpleStringProperty(String.format("%.0f miles", AVG_MILES_PER_WEEK));
            this.estimatedWeeklyPay = new SimpleStringProperty(calculateEstimatedPay());
            this.status = new SimpleStringProperty(validatePerMileRate());
            this.hasChanges = new SimpleBooleanProperty(false);
            
            // Add listener to update status, estimated pay, and changes
            perMileRate.addListener((obs, old, val) -> {
                status.set(validatePerMileRate());
                estimatedWeeklyPay.set(calculateEstimatedPay());
                hasChanges.set(Math.abs(val.doubleValue() - employee.getPerMileRate()) > 0.001);
            });
        }
        
        private String calculateEstimatedPay() {
            double weeklyPay = perMileRate.get() * AVG_MILES_PER_WEEK;
            return String.format("$%.2f/week", weeklyPay);
        }
        
        private String validatePerMileRate() {
            double rate = perMileRate.get();
            
            if (rate <= 0) {
                return "Error: Per-mile rate must be greater than $0";
            }
            
            if (rate > 10) {
                return "Error: Per-mile rate cannot exceed $10/mile";
            }
            
            // Warnings for unusual values
            if (rate < 0.50) {
                return "Warning: Rate below $0.50/mile is unusually low";
            }
            
            if (rate > 5.00) {
                return "Warning: Rate above $5.00/mile is unusually high";
            }
            
            // Check based on driver type
            Employee.DriverType driverType = employee.getDriverType();
            if (driverType == Employee.DriverType.COMPANY_DRIVER) {
                if (rate > 1.00) {
                    return "Warning: Company driver rate above $1.00/mile is high";
                }
            } else if (driverType == Employee.DriverType.OWNER_OPERATOR) {
                if (rate < 1.00) {
                    return "Warning: Owner operator rate below $1.00/mile is low";
                }
            }
            
            return "Valid";
        }
        
        public boolean isValid() {
            return !status.get().startsWith("Error");
        }
        
        public boolean requiresZipCodes() {
            return true;
        }
        
        // Getters and setters
        public Employee getEmployee() { return employee; }
        
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        
        public StringProperty nameProperty() { return name; }
        public String getName() { return name.get(); }
        
        public StringProperty truckUnitProperty() { return truckUnit; }
        public String getTruckUnit() { return truckUnit.get(); }
        
        public StringProperty currentPaymentMethodProperty() { return currentPaymentMethod; }
        public String getCurrentPaymentMethod() { return currentPaymentMethod.get(); }
        
        public DoubleProperty perMileRateProperty() { return perMileRate; }
        public double getPerMileRate() { return perMileRate.get(); }
        public void setPerMileRate(double value) { perMileRate.set(value); }
        
        public StringProperty estimatedWeeklyMilesProperty() { return estimatedWeeklyMiles; }
        public String getEstimatedWeeklyMiles() { return estimatedWeeklyMiles.get(); }
        
        public StringProperty estimatedWeeklyPayProperty() { return estimatedWeeklyPay; }
        public String getEstimatedWeeklyPay() { return estimatedWeeklyPay.get(); }
        
        public StringProperty statusProperty() { return status; }
        public String getStatus() { return status.get(); }
        
        public BooleanProperty hasChangesProperty() { return hasChanges; }
        public boolean hasChanges() { return hasChanges.get(); }
    }
}
