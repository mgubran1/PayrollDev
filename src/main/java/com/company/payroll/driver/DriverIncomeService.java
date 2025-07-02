package com.company.payroll.driver;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import com.company.payroll.fuel.FuelTransaction;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.payroll.PayrollCalculator;
import com.company.payroll.payroll.PayrollRecurring;
import com.company.payroll.payroll.PayrollAdvances;
import com.company.payroll.payroll.PayrollOtherAdjustments;
import com.company.payroll.payroll.PayrollEscrow;
import com.company.payroll.driver.GeocodingService;
import com.company.payroll.driver.MileageCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service class for calculating driver income data
 */
public class DriverIncomeService {
    private static final Logger logger = LoggerFactory.getLogger(DriverIncomeService.class);
    
    private final EmployeeDAO employeeDAO;
    private final LoadDAO loadDAO;
    private final FuelTransactionDAO fuelDAO;
    private final PayrollCalculator payrollCalculator;
    private final GeocodingService geocodingService;
    private final MileageCalculator mileageCalculator;
    
    // Additional services for complete data integration
    private final PayrollRecurring payrollRecurring;
    private final PayrollAdvances payrollAdvances;
    private final PayrollOtherAdjustments payrollOtherAdjustments;
    private final PayrollEscrow payrollEscrow;
    
    // Cache for mileage calculations
    private final Map<String, Double> mileageCache = new ConcurrentHashMap<>();
    
    public DriverIncomeService(EmployeeDAO employeeDAO, LoadDAO loadDAO, 
                              FuelTransactionDAO fuelDAO, PayrollCalculator payrollCalculator) {
        this.employeeDAO = employeeDAO;
        this.loadDAO = loadDAO;
        this.fuelDAO = fuelDAO;
        this.payrollCalculator = payrollCalculator;
        this.geocodingService = new GeocodingService();
        this.mileageCalculator = new MileageCalculator(geocodingService);
        
        // Initialize additional services
        this.payrollRecurring = new PayrollRecurring();
        this.payrollAdvances = PayrollAdvances.getInstance();
        this.payrollOtherAdjustments = PayrollOtherAdjustments.getInstance();
        this.payrollEscrow = PayrollEscrow.getInstance();
    }
    
    /**
     * Get income data for a specific driver
     */
    public CompletableFuture<DriverIncomeData> getDriverIncomeData(Employee driver, 
                                                                   LocalDate startDate, 
                                                                   LocalDate endDate) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Fetching income data for driver: {} from {} to {}", 
                       driver.getName(), startDate, endDate);
            
            DriverIncomeData data = new DriverIncomeData(
                driver.getId(),
                driver.getName(),
                driver.getTruckUnit(),
                startDate,
                endDate
            );
            
            // Fetch loads
            List<Load> loads = loadDAO.getByDriverAndDateRange(driver.getId(), startDate, endDate);
            processLoads(data, loads);
            
            // Fetch fuel transactions
            List<FuelTransaction> fuelTransactions = fuelDAO.getByDriverAndDateRange(
                driver.getName(), startDate, endDate);
            processFuelTransactions(data, fuelTransactions);
            
            // Fetch recurring fees directly
            double recurringTotal = 0.0;
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                recurringTotal += payrollRecurring.totalDeductionsForDriverWeek(driver.getId(), currentDate);
                currentDate = currentDate.plusWeeks(1);
            }
            data.setRecurringFees(recurringTotal);
            
            // Fetch advances data
            BigDecimal totalAdvancesGiven = BigDecimal.ZERO;
            BigDecimal totalAdvanceRepayments = BigDecimal.ZERO;
            
            currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                // Get advances given in this period
                List<PayrollAdvances.AdvanceEntry> advances = payrollAdvances.getAdvancesForEmployee(driver);
                for (PayrollAdvances.AdvanceEntry advance : advances) {
                    if (advance.getAdvanceType() == PayrollAdvances.AdvanceType.ADVANCE &&
                        !advance.getDate().isBefore(startDate) && !advance.getDate().isAfter(endDate)) {
                        totalAdvancesGiven = totalAdvancesGiven.add(advance.getAmount());
                    }
                }
                
                // Get scheduled repayments for this week
                BigDecimal weeklyRepayment = payrollAdvances.getScheduledRepaymentForWeek(driver, currentDate);
                totalAdvanceRepayments = totalAdvanceRepayments.add(weeklyRepayment);
                
                currentDate = currentDate.plusWeeks(1);
            }
            
            data.setAdvancesGiven(totalAdvancesGiven.doubleValue());
            data.setAdvanceRepayments(totalAdvanceRepayments.doubleValue());
            
            // Fetch escrow deposits
            double totalEscrowDeposits = 0.0;
            currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                double weeklyDeposit = payrollEscrow.getTotalDeposits(driver.getId(), currentDate);
                totalEscrowDeposits += weeklyDeposit;
                currentDate = currentDate.plusWeeks(1);
            }
            data.setEscrowDeposits(totalEscrowDeposits);
            
            // Fetch other adjustments
            double totalOtherDeductions = 0.0;
            double totalReimbursements = 0.0;
            currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                BigDecimal deductions = payrollOtherAdjustments.getTotalDeductionsBD(driver.getId(), currentDate);
                BigDecimal reimbursements = payrollOtherAdjustments.getTotalReimbursementsBD(driver.getId(), currentDate);
                
                totalOtherDeductions += deductions.doubleValue();
                totalReimbursements += reimbursements.doubleValue();
                
                currentDate = currentDate.plusWeeks(1);
            }
            data.setOtherDeductions(totalOtherDeductions);
            data.setReimbursements(totalReimbursements);
            
            // Calculate payroll data for complete financial picture
            List<PayrollCalculator.PayrollRow> payrollRows = payrollCalculator.calculatePayrollRows(
                Collections.singletonList(driver), startDate, endDate);
            
            if (!payrollRows.isEmpty()) {
                PayrollCalculator.PayrollRow row = payrollRows.get(0);
                
                // Set all financial data from payroll calculation
                data.setServiceFee(Math.abs(row.serviceFee));
                data.setGrossAfterServiceFee(row.grossAfterServiceFee);
                data.setCompanyPay(row.companyPay);
                data.setDriverPay(row.driverPay);
                data.setGrossAfterFuel(row.grossAfterFuel);
                
                // Use the directly fetched values instead of payroll row values
                // This ensures we get the actual detailed data
                if (recurringTotal > 0) {
                    data.setRecurringFees(recurringTotal);
                } else {
                    data.setRecurringFees(Math.abs(row.recurringFees));
                }
                
                if (totalAdvancesGiven.doubleValue() > 0) {
                    data.setAdvancesGiven(totalAdvancesGiven.doubleValue());
                } else {
                    data.setAdvancesGiven(row.advancesGiven);
                }
                
                if (totalAdvanceRepayments.doubleValue() > 0) {
                    data.setAdvanceRepayments(totalAdvanceRepayments.doubleValue());
                } else {
                    data.setAdvanceRepayments(Math.abs(row.advanceRepayments));
                }
                
                if (totalEscrowDeposits > 0) {
                    data.setEscrowDeposits(totalEscrowDeposits);
                } else {
                    data.setEscrowDeposits(Math.abs(row.escrowDeposits));
                }
                
                if (totalOtherDeductions > 0) {
                    data.setOtherDeductions(totalOtherDeductions);
                } else {
                    data.setOtherDeductions(Math.abs(row.otherDeductions));
                }
                
                if (totalReimbursements > 0) {
                    data.setReimbursements(totalReimbursements);
                } else {
                    data.setReimbursements(row.reimbursements);
                }
                
                data.setNetPay(row.netPay);
            } else {
                // Calculate net pay if no payroll row found
                data.calculateNetPay();
            }
            
            logger.info("Income data fetched successfully for driver: {}", driver.getName());
            return data;
        });
    }
    
    /**
     * Get income data for all drivers
     */
    public CompletableFuture<List<DriverIncomeData>> getAllDriversIncomeData(LocalDate startDate, 
                                                                             LocalDate endDate) {
        List<Employee> activeDrivers = employeeDAO.getActive().stream()
            .filter(emp -> emp.isDriver())
            .collect(Collectors.toList());
        
        List<CompletableFuture<DriverIncomeData>> futures = activeDrivers.stream()
            .map(driver -> getDriverIncomeData(driver, startDate, endDate))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }
    
    private void processLoads(DriverIncomeData data, List<Load> loads) {
        double totalGross = 0;
        double totalMiles = 0;
        List<DriverIncomeData.LoadDetail> loadDetails = new ArrayList<>();
        
        for (Load load : loads) {
            double miles = calculateMiles(load.getPickUpLocation(), load.getDropLocation());
            
            DriverIncomeData.LoadDetail detail = new DriverIncomeData.LoadDetail(
                load.getLoadNumber(),
                load.getCustomer(),
                load.getPickUpLocation(),
                load.getDropLocation(),
                load.getDeliveryDate(),
                load.getGrossAmount(),
                miles
            );
            
            loadDetails.add(detail);
            totalGross += load.getGrossAmount();
            totalMiles += miles;
        }
        
        data.setTotalLoads(loads.size());
        data.setTotalGross(totalGross);
        data.setTotalMiles(totalMiles);
        data.setLoads(loadDetails);
    }
    
    private void processFuelTransactions(DriverIncomeData data, List<FuelTransaction> transactions) {
        double totalFuelCost = 0;
        double totalFees = 0;
        
        for (FuelTransaction transaction : transactions) {
            totalFuelCost += transaction.getAmt();
            totalFees += transaction.getFees();
        }
        
        data.setTotalFuelCost(totalFuelCost);
        data.setTotalFuelFees(totalFees);
        data.setTotalFuelAmount(totalFuelCost + totalFees);
        
        // Calculate fuel efficiency if we have miles and fuel quantity
        double totalGallons = transactions.stream()
            .mapToDouble(FuelTransaction::getQty)
            .sum();
        
        if (totalGallons > 0 && data.getTotalMiles() > 0) {
            data.setFuelEfficiency(data.getTotalMiles() / totalGallons);
        }
    }
    
    private double calculateMiles(String origin, String destination) {
        if (origin == null || destination == null || 
            origin.trim().isEmpty() || destination.trim().isEmpty()) {
            return 0;
        }
        
        String cacheKey = origin + "->" + destination;
        
        return mileageCache.computeIfAbsent(cacheKey, k -> {
            try {
                return mileageCalculator.calculateDistance(origin, destination);
            } catch (Exception e) {
                logger.error("Failed to calculate miles from {} to {}", origin, destination, e);
                // Fallback to estimated calculation
                return estimateMiles(origin, destination);
            }
        });
    }
    
    private double estimateMiles(String origin, String destination) {
        // Simple estimation based on average miles
        // This is a fallback when actual calculation fails
        return 250.0; // Default average miles
    }
    
    /**
     * Clear the mileage cache
     */
    public void clearMileageCache() {
        mileageCache.clear();
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", mileageCache.size());
        stats.put("cachedRoutes", new ArrayList<>(mileageCache.keySet()));
        return stats;
    }
}