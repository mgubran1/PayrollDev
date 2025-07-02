package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.fuel.FuelTransaction;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PayrollCalculator handles all payroll calculations for drivers.
 * This class is thread-safe and uses BigDecimal for precise financial calculations.
 */
public class PayrollCalculator {
    private static final Logger logger = LoggerFactory.getLogger(PayrollCalculator.class);
    
    // Constants for calculations
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final BigDecimal MIN_NET_PAY_THRESHOLD = new BigDecimal("300");
    private static final BigDecimal ESCROW_MIN_NET_PAY = new BigDecimal("500");
    private static final BigDecimal MAX_AUTO_REPAYMENT = new BigDecimal("200");
    private static final BigDecimal MAX_ESCROW_DEPOSIT = new BigDecimal("500");
    private static final BigDecimal MIN_ESCROW_DEPOSIT = new BigDecimal("50");
    private static final int ESCROW_WEEKS_TARGET = 6;
    
    private final EmployeeDAO employeeDAO;
    private final LoadDAO loadDAO;
    private final FuelTransactionDAO fuelDAO;
    private final PayrollRecurring payrollRecurring;
    private final PayrollAdvances payrollAdvances;
    private final PayrollOtherAdjustments payrollOtherAdjustments;
    private final PayrollEscrow payrollEscrow;
    
    // Cache for calculation results
    private final Map<String, PayrollCalculationCache> calculationCache = new ConcurrentHashMap<>();

    public PayrollCalculator(EmployeeDAO employeeDAO, LoadDAO loadDAO, FuelTransactionDAO fuelDAO) {
        this.employeeDAO = employeeDAO;
        this.loadDAO = loadDAO;
        this.fuelDAO = fuelDAO;
        this.payrollRecurring = new PayrollRecurring();
        this.payrollAdvances = PayrollAdvances.getInstance();
        this.payrollOtherAdjustments = PayrollOtherAdjustments.getInstance();
        this.payrollEscrow = PayrollEscrow.getInstance();
        logger.info("PayrollCalculator initialized with all components");
    }

    /**
     * Calculate payroll rows for given drivers and date range
     */
    public List<PayrollRow> calculatePayrollRows(List<Employee> drivers, LocalDate start, LocalDate end) {
        logger.info("Starting payroll calculation for {} drivers from {} to {}", 
            drivers.size(), start, end);
        
        List<PayrollRow> rows = new ArrayList<>();
        
        for (Employee driver : drivers) {
            try {
                PayrollRow row = calculateDriverPayroll(driver, start, end);
                rows.add(row);
            } catch (Exception e) {
                logger.error("Error calculating payroll for driver {} (ID: {})", 
                    driver.getName(), driver.getId(), e);
                // Add error row to maintain consistency
                rows.add(createErrorRow(driver, e.getMessage()));
            }
        }
        
        logger.info("Payroll calculation completed. Generated {} rows", rows.size());
        return rows;
    }
    
    /**
     * Calculate payroll for a single driver
     */
    private PayrollRow calculateDriverPayroll(Employee driver, LocalDate start, LocalDate end) {
        logger.debug("Calculating payroll for driver: {} (ID: {}, Truck: {})", 
            driver.getName(), driver.getId(), driver.getTruckUnit());
        
        // Get loads and fuel transactions
        List<Load> loads = loadDAO.getByDriverAndDateRange(driver.getId(), start, end);
        List<FuelTransaction> fuels = fuelDAO.getByDriverAndDateRange(driver.getName(), start, end);

        // Calculate gross pay using BigDecimal
        BigDecimal grossBD = loads.stream()
            .map(load -> BigDecimal.valueOf(load.getGrossAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        double gross = grossBD.doubleValue();
        logger.debug("Driver {} - Gross from {} loads: ${}", driver.getName(), loads.size(), gross);

        // Calculate fuel costs
        BigDecimal fuelBD = fuels.stream()
            .map(f -> BigDecimal.valueOf(f.getAmt() + f.getFees()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        double fuel = fuelBD.doubleValue();
        logger.debug("Driver {} - Fuel from {} transactions: ${}", driver.getName(), fuels.size(), fuel);

        // Calculate service fee with bonus adjustments
        BigDecimal serviceFeePct = BigDecimal.valueOf(driver.getServiceFeePercent());
        BigDecimal totalServiceFeeBD = BigDecimal.ZERO;
        BigDecimal totalBonusBD = BigDecimal.ZERO;

        for (Load load : loads) {
            String loadNumber = load.getLoadNumber();
            BigDecimal loadGross = BigDecimal.valueOf(load.getGrossAmount());
            BigDecimal loadServiceFee = loadGross.multiply(serviceFeePct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal bonusForThisLoad = BigDecimal.valueOf(
                payrollOtherAdjustments.getBonusForLoad(driver.getId(), start, loadNumber));
            
            totalServiceFeeBD = totalServiceFeeBD.add(loadServiceFee);
            totalBonusBD = totalBonusBD.add(bonusForThisLoad);
            
            if (bonusForThisLoad.compareTo(BigDecimal.ZERO) > 0) {
                logger.info("Bonus applied for driver {} on load {}: ${}", 
                    driver.getName(), loadNumber, bonusForThisLoad);
            }
        }
        
        double serviceFeeAmt = totalServiceFeeBD.doubleValue();
        double totalBonus = totalBonusBD.doubleValue();
        double grossAfterSF = gross - serviceFeeAmt;

        // Calculate company and driver pay
        BigDecimal grossAfterSFBD = grossBD.subtract(totalServiceFeeBD);
        BigDecimal companyPctBD = BigDecimal.valueOf(driver.getCompanyPercent());
        BigDecimal companyPayBD = grossAfterSFBD.multiply(companyPctBD).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        double companyPayAmt = companyPayBD.doubleValue();

        BigDecimal driverPctBD = BigDecimal.valueOf(driver.getDriverPercent());
        BigDecimal driverPayBD = grossAfterSFBD.multiply(driverPctBD).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        double driverPayAmt = driverPayBD.doubleValue();

        double grossAfterFuel = grossAfterSF - fuel;

        // Calculate recurring fees
        double recurringFees = payrollRecurring.totalDeductionsForDriverWeek(driver.getId(), start);
        logger.debug("Driver {} - Recurring fees: ${}", driver.getName(), recurringFees);
        
        // Cash Advances - Updated to use new PayrollAdvances system
        BigDecimal advancesGivenBD = BigDecimal.ZERO;
        BigDecimal advanceRepaymentsBD = BigDecimal.ZERO;
        
        // Get advances given this week
        List<PayrollAdvances.AdvanceEntry> weeklyAdvances = payrollAdvances.getEntriesForEmployee(driver.getId()).stream()
            .filter(e -> e.getAdvanceType() == PayrollAdvances.AdvanceType.ADVANCE)
            .filter(e -> !e.getDate().isBefore(start) && !e.getDate().isAfter(end))
            .collect(Collectors.toList());
        
        advancesGivenBD = weeklyAdvances.stream()
            .map(PayrollAdvances.AdvanceEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        double advancesGiven = advancesGivenBD.doubleValue();
        logger.debug("Driver {} - Advances given this week: ${}", driver.getName(), advancesGiven);
        
        // Calculate advance repayments
        BigDecimal scheduledRepaymentBD = payrollAdvances.getScheduledRepaymentForWeek(driver, start);
        
        if (scheduledRepaymentBD.compareTo(BigDecimal.ZERO) > 0) {
            advanceRepaymentsBD = scheduledRepaymentBD;
            logger.info("Scheduled advance repayment for driver {}: ${}", driver.getName(), advanceRepaymentsBD);
        } else {
            // Check if auto-repayment should be calculated
            BigDecimal outstandingBalance = payrollAdvances.getCurrentBalance(driver);
            
            if (outstandingBalance.compareTo(BigDecimal.ZERO) > 0 && grossAfterFuel > 500) {
                advanceRepaymentsBD = calculateAutoRepayment(
                    BigDecimal.valueOf(gross), 
                    BigDecimal.valueOf(grossAfterFuel), 
                    BigDecimal.valueOf(recurringFees), 
                    outstandingBalance
                );
                
                if (advanceRepaymentsBD.compareTo(BigDecimal.ZERO) > 0) {
                    logger.info("Auto-calculated advance repayment for driver {}: ${} (Outstanding: ${})", 
                        driver.getName(), advanceRepaymentsBD, outstandingBalance);
                }
            }
        }
        
        double advanceRepayments = advanceRepaymentsBD.doubleValue();

        // Other deductions and reimbursements
        double otherDeductions = payrollOtherAdjustments.getTotalDeductions(driver.getId(), start);
        double reimbursements = payrollOtherAdjustments.getTotalReimbursements(driver.getId(), start);
        double totalReimbursements = Math.abs(reimbursements) + Math.abs(totalBonus);

        // Escrow deposits calculation
        BigDecimal escrowDepositBD = calculateEscrowDeposit(
            driver, start, 
            BigDecimal.valueOf(gross),
            BigDecimal.valueOf(grossAfterFuel), 
            BigDecimal.valueOf(recurringFees),
            advanceRepaymentsBD, 
            BigDecimal.valueOf(otherDeductions), 
            BigDecimal.valueOf(totalReimbursements)
        );
        
        double escrowDeposits = escrowDepositBD.doubleValue();

        // Calculate final net pay
        double totalDeductions = Math.abs(fuel) + Math.abs(recurringFees) + Math.abs(advanceRepayments) 
                              + Math.abs(escrowDeposits) + Math.abs(otherDeductions);
        double net = grossAfterFuel - Math.abs(recurringFees) - Math.abs(advanceRepayments) 
                   - Math.abs(escrowDeposits) - Math.abs(otherDeductions) + totalReimbursements;

        logger.info("Driver {} - Summary: Gross=${}, Deductions=${}, Reimbursements=${}, Net=${}", 
            driver.getName(), gross, totalDeductions, totalReimbursements, net);

        // Create payroll row - UPDATED to include driver ID
        return new PayrollRow(
            driver.getId(),  // Added driver ID
            driver.getName(), 
            driver.getTruckUnit() != null ? driver.getTruckUnit() : "",
            loads.size(), 
            gross, 
            -Math.abs(serviceFeeAmt), 
            grossAfterSF, 
            companyPayAmt, 
            driverPayAmt,
            -Math.abs(fuel), 
            grossAfterFuel, 
            -Math.abs(recurringFees), 
            advancesGiven, 
            -Math.abs(advanceRepayments),
            -Math.abs(escrowDeposits), 
            -Math.abs(otherDeductions), 
            totalReimbursements, 
            net,
            loads, 
            fuels
        );
    }
    
    /**
     * Calculate automatic advance repayment
     */
    private BigDecimal calculateAutoRepayment(BigDecimal gross, BigDecimal grossAfterFuel, 
                                            BigDecimal recurringFees, BigDecimal outstandingBalance) {
        // 10% of gross or $200, whichever is less
        BigDecimal tenPercentOfGross = gross.multiply(TEN_PERCENT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggestedRepayment = tenPercentOfGross.min(MAX_AUTO_REPAYMENT);
        
        // Don't exceed outstanding balance
        suggestedRepayment = suggestedRepayment.min(outstandingBalance);
        
        // Ensure driver keeps at least $300 net pay
        BigDecimal projectedNet = grossAfterFuel.subtract(recurringFees).subtract(suggestedRepayment);
        if (projectedNet.compareTo(MIN_NET_PAY_THRESHOLD) < 0) {
            return BigDecimal.ZERO;
        }
        
        return suggestedRepayment;
    }
    
    /**
     * Calculate escrow deposit amount
     */
    private BigDecimal calculateEscrowDeposit(Employee driver, LocalDate weekStart, BigDecimal gross,
                                            BigDecimal grossAfterFuel, BigDecimal recurringFees,
                                            BigDecimal advanceRepayments, BigDecimal otherDeductions,
                                            BigDecimal reimbursements) {
        BigDecimal remainingToTarget = payrollEscrow.getRemainingToTarget(driver);
        
        // Check if escrow is already fully funded
        if (remainingToTarget.compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Driver {} - Escrow already fully funded", driver.getName());
            return BigDecimal.ZERO;
        }
        
        // Check for manual weekly deposit first
        BigDecimal manualEscrowDeposit = payrollEscrow.getWeeklyAmount(driver, weekStart);
        
        // If manual deposit exists, use it
        if (manualEscrowDeposit.compareTo(BigDecimal.ZERO) > 0) {
            logger.info("Manual escrow deposit for driver {}: ${} (Remaining to target: ${})", 
                driver.getName(), manualEscrowDeposit, remainingToTarget);
            return manualEscrowDeposit;
        }
        
        // Calculate suggested amount for informational purposes only (not applied)
        if (gross.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate potential net before escrow
            BigDecimal potentialNetBeforeEscrow = grossAfterFuel
                .subtract(recurringFees.abs())
                .subtract(advanceRepayments.abs())
                .subtract(otherDeductions.abs())
                .add(reimbursements.abs());
            
            if (potentialNetBeforeEscrow.compareTo(ESCROW_MIN_NET_PAY) > 0) {
                // Calculate suggested amount (complete in ~6 weeks)
                BigDecimal weeklyTarget = remainingToTarget.divide(
                    new BigDecimal(ESCROW_WEEKS_TARGET), 2, RoundingMode.CEILING);
                BigDecimal maxEscrow = weeklyTarget.min(MAX_ESCROW_DEPOSIT);
                
                // Ensure driver keeps at least $500
                BigDecimal affordableEscrow = potentialNetBeforeEscrow.subtract(ESCROW_MIN_NET_PAY);
                affordableEscrow = affordableEscrow.max(BigDecimal.ZERO);
                
                BigDecimal suggestedEscrow = maxEscrow.min(affordableEscrow);
                
                if (suggestedEscrow.compareTo(MIN_ESCROW_DEPOSIT) >= 0) {
                    suggestedEscrow = suggestedEscrow.setScale(2, RoundingMode.HALF_UP);
                    logger.info("SUGGESTED escrow deposit for driver {} would be: ${} (NOT APPLIED - manual entry required)", 
                        driver.getName(), suggestedEscrow);
                    
                    // Optionally, you could store this suggestion for display in the UI
                    // For example, add a method to PayrollEscrow to store suggestions:
                    // payrollEscrow.storeSuggestedAmount(driver, weekStart, suggestedEscrow);
                }
            }
        }
        
        // Return zero - no automatic escrow deduction without manual entry
        logger.debug("No manual escrow deposit found for driver {} - returning $0.00", driver.getName());
        return BigDecimal.ZERO;
    }
    
    /**
     * Create an error row for failed calculations
     */
    private PayrollRow createErrorRow(Employee driver, String errorMessage) {
        return new PayrollRow(
            driver.getId(),  // Added driver ID
            driver.getName() + " (ERROR: " + errorMessage + ")",
            driver.getTruckUnit() != null ? driver.getTruckUnit() : "",
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    /**
     * Calculate totals from payroll rows
     */
    public Map<String, Double> calculateTotals(List<PayrollRow> rows) {
        Map<String, Double> totals = new HashMap<>();
        
        double totalGross = rows.stream().mapToDouble(r -> r.gross).sum();
        double totalServiceFee = rows.stream().mapToDouble(r -> r.serviceFee).sum();
        double totalGrossAfterServiceFee = rows.stream().mapToDouble(r -> r.grossAfterServiceFee).sum();
        double totalCompanyPay = rows.stream().mapToDouble(r -> r.companyPay).sum();
        double totalDriverPay = rows.stream().mapToDouble(r -> r.driverPay).sum();
        double totalFuel = rows.stream().mapToDouble(r -> r.fuel).sum();
        double totalGrossAfterFuel = rows.stream().mapToDouble(r -> r.grossAfterFuel).sum();
        double totalRecurringFees = rows.stream().mapToDouble(r -> r.recurringFees).sum();
        double totalAdvancesGiven = rows.stream().mapToDouble(r -> r.advancesGiven).sum();
        double totalAdvanceRepayments = rows.stream().mapToDouble(r -> r.advanceRepayments).sum();
        double totalEscrowDeposits = rows.stream().mapToDouble(r -> r.escrowDeposits).sum();
        double totalOtherDeductions = rows.stream().mapToDouble(r -> r.otherDeductions).sum();
        double totalReimbursements = rows.stream().mapToDouble(r -> r.reimbursements).sum();
        double totalNetPay = rows.stream().mapToDouble(r -> r.netPay).sum();
        
        totals.put("gross", totalGross);
        totals.put("serviceFee", totalServiceFee);
        totals.put("grossAfterServiceFee", totalGrossAfterServiceFee);
        totals.put("companyPay", totalCompanyPay);
        totals.put("driverPay", totalDriverPay);
        totals.put("fuel", totalFuel);
        totals.put("grossAfterFuel", totalGrossAfterFuel);
        totals.put("recurringFees", totalRecurringFees);
        totals.put("advancesGiven", totalAdvancesGiven);
        totals.put("advanceRepayments", totalAdvanceRepayments);
        totals.put("escrowDeposits", totalEscrowDeposits);
        totals.put("otherDeductions", totalOtherDeductions);
        totals.put("reimbursements", totalReimbursements);
        totals.put("netPay", totalNetPay);
        
        logger.debug("Calculated totals for {} rows: Gross=${}, Net=${}", rows.size(), totalGross, totalNetPay);
        
        return totals;
    }
    
    /**
     * Clear calculation cache
     */
    public void clearCache() {
        calculationCache.clear();
        logger.debug("Calculation cache cleared");
    }

    /**
     * PayrollRow represents a single driver's payroll calculation
     */
    public static class PayrollRow {
        public final int driverId;  // ADDED driver ID field
        public final String driverName;
        public final String truckUnit;
        public final int loadCount;  // KEEPING ORIGINAL NAME
        public final double gross;
        public final double serviceFee;
        public final double grossAfterServiceFee;
        public final double companyPay;
        public final double driverPay;
        public final double fuel;
        public final double grossAfterFuel;
        public final double recurringFees;
        public final double advancesGiven;
        public final double advanceRepayments;
        public final double escrowDeposits;
        public final double otherDeductions;
        public final double reimbursements;
        public final double netPay;
        public final List<Load> loads;
        public final List<FuelTransaction> fuels;
        
        // Also add numLoads as an alias for backward compatibility
        public int getNumLoads() {
            return loadCount;
        }

        public PayrollRow(int driverId, String driverName, String truckUnit, int loadCount, double gross, double serviceFee, 
                          double grossAfterServiceFee, double companyPay, double driverPay, double fuel, 
                          double grossAfterFuel, double recurringFees, double advancesGiven, 
                          double advanceRepayments, double escrowDeposits, double otherDeductions, 
                          double reimbursements, double netPay, List<Load> loads, List<FuelTransaction> fuels) {
            this.driverId = driverId;  // Store driver ID
            this.driverName = driverName;
            this.truckUnit = truckUnit;
            this.loadCount = loadCount;  // Keep original name
            this.gross = gross;
            this.serviceFee = serviceFee;
            this.grossAfterServiceFee = grossAfterServiceFee;
            this.companyPay = companyPay;
            this.driverPay = driverPay;
            this.fuel = fuel;
            this.grossAfterFuel = grossAfterFuel;
            this.recurringFees = recurringFees;
            this.advancesGiven = advancesGiven;
            this.advanceRepayments = advanceRepayments;
            this.escrowDeposits = escrowDeposits;
            this.otherDeductions = otherDeductions;
            this.reimbursements = reimbursements;
            this.netPay = netPay;
            this.loads = Collections.unmodifiableList(new ArrayList<>(loads));
            this.fuels = Collections.unmodifiableList(new ArrayList<>(fuels));
        }
        
        /**
         * Export to CSV format with proper escaping
         */
        public String toCSVRow() {
            return String.format("%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                    escapeCSV(driverName), 
                    escapeCSV(truckUnit), 
                    loadCount, 
                    gross, 
                    serviceFee, 
                    grossAfterServiceFee,
                    companyPay, 
                    driverPay, 
                    fuel, 
                    grossAfterFuel,
                    recurringFees, 
                    advancesGiven, 
                    advanceRepayments,
                    escrowDeposits, 
                    otherDeductions, 
                    reimbursements, 
                    netPay);
        }
        
        /**
         * Export to TSV format
         */
        public String toTSVRow() {
            return String.format("%s\t%s\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f",
                    driverName, 
                    truckUnit, 
                    loadCount, 
                    gross, 
                    serviceFee, 
                    grossAfterServiceFee,
                    companyPay, 
                    driverPay, 
                    fuel, 
                    grossAfterFuel,
                    recurringFees, 
                    advancesGiven, 
                    advanceRepayments,
                    escrowDeposits, 
                    otherDeductions, 
                    reimbursements, 
                    netPay);
        }
        
        /**
         * Escape CSV values properly
         */
        private String escapeCSV(String value) {
            if (value == null) return "";
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
        
        /**
         * Get a detailed summary of deductions
         */
        public double getTotalDeductions() {
            return Math.abs(serviceFee) + Math.abs(fuel) + Math.abs(recurringFees) + 
                   Math.abs(advanceRepayments) + Math.abs(escrowDeposits) + Math.abs(otherDeductions);
        }
        
        /**
         * Get total additions (gross + reimbursements)
         */
        public double getTotalAdditions() {
            return gross + reimbursements;
        }
        
        /**
         * Check if driver has outstanding balance issues
         */
        public boolean hasPaymentIssues() {
            return netPay < 0;
        }
        
        /**
         * Get deduction breakdown as percentages
         */
        public Map<String, Double> getDeductionBreakdown() {
            double total = getTotalDeductions();
            if (total == 0) return Collections.emptyMap();
            
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("serviceFee", (Math.abs(serviceFee) / total) * 100);
            breakdown.put("fuel", (Math.abs(fuel) / total) * 100);
            breakdown.put("recurringFees", (Math.abs(recurringFees) / total) * 100);
            breakdown.put("advanceRepayments", (Math.abs(advanceRepayments) / total) * 100);
            breakdown.put("escrowDeposits", (Math.abs(escrowDeposits) / total) * 100);
            breakdown.put("otherDeductions", (Math.abs(otherDeductions) / total) * 100);
            
            return breakdown;
        }
        
        /**
         * Get load summary statistics
         */
        public Map<String, Object> getLoadStatistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("count", loadCount);
            stats.put("averageGross", loadCount > 0 ? gross / loadCount : 0);
            stats.put("totalMiles", 0.0);
            stats.put("averageMiles", 0.0); // Load class doesn't have getMiles() method
            
            return stats;
        }
        
        @Override
        public String toString() {
            return String.format("PayrollRow[driverId=%d, driver=%s, truck=%s, loads=%d, gross=%.2f, net=%.2f]",
                    driverId, driverName, truckUnit, loadCount, gross, netPay);
        }
    }
    
    /**
     * Cache for calculation results
     */
    private static class PayrollCalculationCache {
        private final String key;
        private final LocalDate calculationDate;
        private final List<PayrollRow> rows;
        private final Map<String, Double> totals;
        
        public PayrollCalculationCache(String key, List<PayrollRow> rows, Map<String, Double> totals) {
            this.key = key;
            this.calculationDate = LocalDate.now();
            this.rows = new ArrayList<>(rows);
            this.totals = new HashMap<>(totals);
        }
        
        public boolean isExpired() {
            return LocalDate.now().isAfter(calculationDate);
        }
    }
}