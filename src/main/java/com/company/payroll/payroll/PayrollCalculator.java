package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.employees.EmployeePercentageHistory;
import com.company.payroll.employees.EmployeePercentageHistoryDAO;
import com.company.payroll.fuel.FuelTransaction;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
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
    private final EmployeePercentageHistoryDAO percentageHistoryDAO;
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
        // Initialize percentage history DAO with same connection as employeeDAO
        this.percentageHistoryDAO = new EmployeePercentageHistoryDAO(employeeDAO.getConnection());
        this.payrollRecurring = new PayrollRecurring();
        this.payrollAdvances = PayrollAdvances.getInstance();
        this.payrollOtherAdjustments = PayrollOtherAdjustments.getInstance();
        this.payrollEscrow = PayrollEscrow.getInstance();
        logger.info("PayrollCalculator initialized with all components including percentage history");
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
        List<Load> loads = loadDAO.getByDriverAndDateRangeForFinancials(driver.getId(), start, end);
        List<FuelTransaction> fuels = fuelDAO.getByDriverAndDateRange(driver.getName(), start, end);
        
        // Log load details for debugging
        if (logger.isDebugEnabled()) {
            logger.debug("Driver {} - Found {} loads for payroll calculation", driver.getName(), loads.size());
            Map<Load.Status, Long> statusCounts = loads.stream()
                .collect(Collectors.groupingBy(Load::getStatus, Collectors.counting()));
            logger.debug("Driver {} - Load status breakdown: {}", driver.getName(), statusCounts);
        }
        
        // Get effective percentages for this payroll period
        EmployeePercentageHistory effectivePercentages = null;
        double driverPercent = driver.getDriverPercent();
        double companyPercent = driver.getCompanyPercent();
        double serviceFeePercent = driver.getServiceFeePercent();
        
        try {
            effectivePercentages = percentageHistoryDAO.getEffectivePercentages(driver.getId(), end);
            if (effectivePercentages != null) {
                driverPercent = effectivePercentages.getDriverPercent();
                companyPercent = effectivePercentages.getCompanyPercent();
                serviceFeePercent = effectivePercentages.getServiceFeePercent();
                logger.info("Using date-effective percentages for driver {} on {}: Driver={}%, Company={}%, ServiceFee={}%",
                    driver.getName(), end, driverPercent, companyPercent, serviceFeePercent);
            }
        } catch (Exception e) {
            logger.warn("Failed to get effective percentages for driver {}, using current values: {}",
                driver.getName(), e.getMessage());
        }

        // Calculate gross pay using BigDecimal
        BigDecimal grossBD = loads.stream()
            .map(load -> BigDecimal.valueOf(load.getGrossAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        double gross = grossBD.doubleValue();
        logger.debug("Driver {} - Gross from {} loads: ${}", driver.getName(), loads.size(), gross);

        // Calculate fuel costs from fuel transactions
        BigDecimal fuelTransactionsBD = fuels.stream()
            .map(f -> BigDecimal.valueOf(f.getAmt() + f.getFees()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Get fuel deductions from adjustments panel
        BigDecimal fuelAdjustmentsBD = payrollOtherAdjustments.getFuelDeductionsBD(driver.getId(), start);
        
        // Total fuel is sum of both sources
        BigDecimal totalFuelBD = fuelTransactionsBD.add(fuelAdjustmentsBD);
        double fuel = totalFuelBD.doubleValue();
        
        if (fuelAdjustmentsBD.compareTo(BigDecimal.ZERO) > 0) {
            logger.info("Driver {} - Fuel deductions: ${} from transactions, ${} from adjustments, total: ${}", 
                driver.getName(), fuelTransactionsBD, fuelAdjustmentsBD, totalFuelBD);
        } else {
            logger.debug("Driver {} - Fuel from {} transactions: ${}", driver.getName(), fuels.size(), fuel);
        }

        // Calculate service fee with bonus adjustments
        BigDecimal serviceFeePct = BigDecimal.valueOf(serviceFeePercent);
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
        BigDecimal companyPctBD = BigDecimal.valueOf(companyPercent);
        BigDecimal companyPayBD = grossAfterSFBD.multiply(companyPctBD).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        double companyPayAmt = companyPayBD.doubleValue();

        BigDecimal driverPctBD = BigDecimal.valueOf(driverPercent);
        BigDecimal driverPayBD = grossAfterSFBD.multiply(driverPctBD).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        double driverPayAmt = driverPayBD.doubleValue();

        double grossAfterFuel = grossAfterSF - fuel;

        // Calculate recurring fees
        double recurringFees = payrollRecurring.totalDeductionsForDriverWeek(driver.getId(), start);
        logger.debug("Driver {} - Recurring fees: ${}", driver.getName(), recurringFees);
        
        // Cash Advances - Only deduct repayments that are manually scheduled/recorded for the week
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
        
        // Only deduct repayments that are manually scheduled/recorded for the week
        // IMPORTANT: Repayments are stored as negative values, so we need to handle this correctly
        List<PayrollAdvances.AdvanceEntry> weeklyRepayments = payrollAdvances.getEntriesForEmployee(driver.getId()).stream()
            .filter(e -> e.getAdvanceType() == PayrollAdvances.AdvanceType.REPAYMENT)
            .filter(e -> !e.getDate().isBefore(start) && !e.getDate().isAfter(end))
            .collect(Collectors.toList());
        
        // Sum the repayments (they're already negative, so we get the absolute value for display)
        advanceRepaymentsBD = weeklyRepayments.stream()
            .map(PayrollAdvances.AdvanceEntry::getAmount)
            .map(BigDecimal::abs)  // Convert to positive for display/calculation
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        double advanceRepayments = advanceRepaymentsBD.doubleValue();
        logger.debug("Driver {} - Advance repayments this week: ${}", driver.getName(), advanceRepayments);

        // Other deductions and reimbursements (excluding fuel which is handled separately)
        BigDecimal otherDeductionsBD = payrollOtherAdjustments.getTotalDeductionsBD(driver.getId(), start)
            .subtract(fuelAdjustmentsBD); // Subtract fuel to avoid double counting
        double otherDeductions = otherDeductionsBD.doubleValue();
        
        double reimbursements = payrollOtherAdjustments.getTotalReimbursements(driver.getId(), start);
        double totalReimbursements = Math.abs(reimbursements) + Math.abs(totalBonus);

        // Escrow deposits calculation
        BigDecimal escrowDepositBD = calculateEscrowDeposit(
            driver, start, 
            BigDecimal.valueOf(gross),
            BigDecimal.valueOf(grossAfterFuel), 
            BigDecimal.valueOf(recurringFees),
            advanceRepaymentsBD, 
            otherDeductionsBD, 
            BigDecimal.valueOf(totalReimbursements)
        );
        
        double escrowDeposits = escrowDepositBD.doubleValue();

        // Calculate final net pay - CORRECTED LOGIC
        // Start with driver's share of gross after service fee and fuel
        double driverGrossAfterFuel = driverPayAmt - Math.abs(fuel);
        
        // Calculate total deductions (excluding fuel since it's already subtracted from driver's share)
        double totalDeductions = Math.abs(recurringFees) + Math.abs(advanceRepayments) 
                              + Math.abs(escrowDeposits) + Math.abs(otherDeductions);
        
        // Final NET PAY = Driver's gross after fuel - all other deductions + reimbursements
        double net = driverGrossAfterFuel - totalDeductions + totalReimbursements;
        
        // Update the driverPayAmt to represent the final take-home pay (NET PAY)
        double finalDriverPay = net;

        logger.info("Driver {} - Summary: Gross=${}, Driver Share=${}, Fuel=${}, Other Deductions=${}, Reimbursements=${}, Final Driver Pay=${}", 
            driver.getName(), gross, driverPayAmt, fuel, totalDeductions, totalReimbursements, finalDriverPay);

        // Create payroll row - CORRECTED: Driver Pay now represents final take-home pay
        return new PayrollRow(
            driver.getId(),
            driver.getName(),
            driver.getTruckUnit() != null ? driver.getTruckUnit() : "",
            loads.size(),
            gross,
            Math.abs(serviceFeeAmt),
            grossAfterSF,
            companyPayAmt,
            finalDriverPay,  // Driver Pay = final take-home pay (NET PAY)
            driverPayAmt,    // Driver Gross Share = original percentage-based share
            Math.abs(fuel),
            grossAfterFuel,
            Math.abs(recurringFees),
            advancesGiven,
            Math.abs(advanceRepayments),
            Math.abs(escrowDeposits),
            Math.abs(otherDeductions),
            totalReimbursements,
            finalDriverPay,  // NET PAY = same as Driver Pay (final take-home amount)
            loads,
            fuels,
            companyPercent,
            driverPercent,
            serviceFeePercent
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
     * Calculate escrow deposit amount - UPDATED to use isEscrowFullyFunded()
     */
    private BigDecimal calculateEscrowDeposit(Employee driver, LocalDate weekStart, BigDecimal gross,
                                            BigDecimal grossAfterFuel, BigDecimal recurringFees,
                                            BigDecimal advanceRepayments, BigDecimal otherDeductions,
                                            BigDecimal reimbursements) {
        
        // Use the new isEscrowFullyFunded method which only returns true when balance EXCEEDS target
        if (payrollEscrow.isEscrowFullyFunded(driver)) {
            logger.debug("Driver {} - Escrow balance exceeds target, no deduction needed", driver.getName());
            return BigDecimal.ZERO;
        }
        
        BigDecimal remainingToTarget = payrollEscrow.getRemainingToTarget(driver);
        BigDecimal currentBalance = payrollEscrow.getCurrentBalance(driver);
        BigDecimal targetAmount = payrollEscrow.getTargetAmount(driver);
        
        logger.debug("Driver {} - Escrow status: Current=${}, Target=${}, Remaining=${}", 
            driver.getName(), currentBalance, targetAmount, remainingToTarget);
        
        // Check for manual weekly deposit first
        BigDecimal manualEscrowDeposit = payrollEscrow.getWeeklyAmount(driver, weekStart);
        
        // If manual deposit exists, use it (even if at target amount)
        if (manualEscrowDeposit.compareTo(BigDecimal.ZERO) > 0) {
            logger.info("Manual escrow deposit for driver {}: ${} (Current balance: ${}, Target: ${})", 
                driver.getName(), manualEscrowDeposit, currentBalance, targetAmount);
            return manualEscrowDeposit;
        }
        
        // Calculate suggested amount for informational purposes only (not applied)
        if (gross.compareTo(BigDecimal.ZERO) > 0 && remainingToTarget.compareTo(BigDecimal.ZERO) > 0) {
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
            0, // loadCount
            0.0, // gross
            0.0, // serviceFee
            0.0, // grossAfterServiceFee
            0.0, // companyPay
            0.0, // driverPay (final)
            0.0, // driverGrossShare
            0.0, // fuel
            0.0, // grossAfterFuel
            0.0, // recurringFees
            0.0, // advancesGiven
            0.0, // advanceRepayments
            0.0, // escrowDeposits
            0.0, // otherDeductions
            0.0, // reimbursements
            0.0, // netPay
            Collections.emptyList(), // loads
            Collections.emptyList(), // fuels
            0.0, // companyPercent
            0.0, // driverPercent
            0.0  // serviceFeePercent
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
     * Get the database connection for additional queries
     */
    public Connection getConnection() {
        return employeeDAO.getConnection();
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
        public final double driverPay;  // NOW represents final take-home pay (NET PAY)
        public final double driverGrossShare;  // NEW: Driver's share before final deductions
        public final double fuel;
        public final double grossAfterFuel;
        public final double recurringFees;
        public final double advancesGiven;
        public final double advanceRepayments;
        public final double escrowDeposits;
        public final double otherDeductions;
        public final double reimbursements;
        public final double netPay;  // Same as driverPay (final take-home amount)
        public final List<Load> loads;
        public final List<FuelTransaction> fuels;
        public final double companyPercent;
        public final double driverPercent;
        public final double serviceFeePercent;
        
        // Also add numLoads as an alias for backward compatibility
        public int getNumLoads() {
            return loadCount;
        }

        public PayrollRow(int driverId, String driverName, String truckUnit, int loadCount, double gross, double serviceFee, 
                          double grossAfterServiceFee, double companyPay, double driverPay, double driverGrossShare, double fuel, 
                          double grossAfterFuel, double recurringFees, double advancesGiven, 
                          double advanceRepayments, double escrowDeposits, double otherDeductions, 
                          double reimbursements, double netPay, List<Load> loads, List<FuelTransaction> fuels,
                          double companyPercent, double driverPercent, double serviceFeePercent) {
            this.driverId = driverId;  // Store driver ID
            this.driverName = driverName;
            this.truckUnit = truckUnit;
            this.loadCount = loadCount;  // Keep original name
            this.gross = gross;
            this.serviceFee = serviceFee;
            this.grossAfterServiceFee = grossAfterServiceFee;
            this.companyPay = companyPay;
            this.driverPay = driverPay;  // Final take-home pay
            this.driverGrossShare = driverGrossShare;  // Driver's share before final deductions
            this.fuel = fuel;
            this.grossAfterFuel = grossAfterFuel;
            this.recurringFees = recurringFees;
            this.advancesGiven = advancesGiven;
            this.advanceRepayments = advanceRepayments;
            this.escrowDeposits = escrowDeposits;
            this.otherDeductions = otherDeductions;
            this.reimbursements = reimbursements;
            this.netPay = netPay;  // Same as driverPay
            this.loads = Collections.unmodifiableList(new ArrayList<>(loads));
            this.fuels = Collections.unmodifiableList(new ArrayList<>(fuels));
            this.companyPercent = companyPercent;
            this.driverPercent = driverPercent;
            this.serviceFeePercent = serviceFeePercent;
        }
        
        /**
         * Export to CSV format with proper escaping
         */
        public String toCSVRow() {
            return String.format("%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                    escapeCSV(driverName), 
                    escapeCSV(truckUnit), 
                    loadCount, 
                    gross, 
                    serviceFee, 
                    grossAfterServiceFee,
                    companyPay, 
                    driverPay,  // Final take-home pay
                    driverGrossShare,  // Driver's gross share before final deductions
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
            return String.format("%s\t%s\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f",
                    driverName, 
                    truckUnit, 
                    loadCount, 
                    gross, 
                    serviceFee, 
                    grossAfterServiceFee,
                    companyPay, 
                    driverPay,  // Final take-home pay
                    driverGrossShare,  // Driver's gross share before final deductions
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
            return String.format("PayrollRow[driverId=%d, driver=%s, truck=%s, loads=%d, gross=%.2f, driverPay=%.2f, net=%.2f]",
                    driverId, driverName, truckUnit, loadCount, gross, driverPay, netPay);
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

    /**
     * Calculate the company's net profit for a set of payroll rows, using robust logic:
     * 1. Company receives Service Fee from gross.
     * 2. Remaining gross after service fee and fuel is split by Company % and Driver %.
     * 3. Company share from split is added to company profit.
     * 4. Expenses (company and maintenance) are subtracted for true net.
     *
     * @param rows List of PayrollRow (one per driver)
     * @param totalCompanyExpenses Total company expenses for the period
     * @param totalMaintenanceExpenses Total maintenance expenses for the period
     * @return The company's true net profit for the period
     */
    public static double calculateCompanyNet(List<PayrollRow> rows, double totalCompanyExpenses, double totalMaintenanceExpenses) {
        double companyNet = 0.0;
        for (PayrollRow row : rows) {
            companyNet += calculateCompanyNetForRow(row);
        }
        companyNet -= totalCompanyExpenses;
        companyNet -= totalMaintenanceExpenses;
        return companyNet;
    }

    /**
     * Calculate the company's net profit for a single payroll row (driver):
     * 1. Company receives Service Fee from gross.
     * 2. Remaining gross after service fee and fuel is split by Company % and Driver %.
     * 3. Company share from split is added to company profit.
     *
     * @param row PayrollRow for a driver
     * @return The company's net profit from this driver (before expenses)
     */
    public static double calculateCompanyNetForRow(PayrollRow row) {
        double serviceFee = Math.abs(row.serviceFee);
        double fuel = Math.abs(row.fuel);
        double grossAfterServiceFee = row.gross - serviceFee;
        double grossAfterFuel = grossAfterServiceFee - fuel;
        double companySplit = grossAfterFuel * (row.companyPercent / 100.0);
        return serviceFee + companySplit;
    }
}