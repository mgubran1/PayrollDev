package com.company.payroll.driver;
import com.company.payroll.drivers.Driver;

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
     * Get income data for a specific driver - IMPROVED VERSION
     */
    public CompletableFuture<DriverIncomeData> getDriverIncomeData(Driver driver, 
                                                                   LocalDate startDate, 
                                                                   LocalDate endDate) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Fetching income data for driver: {} from {} to {}", 
                       driver.getName(), startDate, endDate);
            
            // Use PayrollCalculator to get the accurate data
            List<PayrollCalculator.PayrollRow> payrollRows = payrollCalculator.calculatePayrollRows(
                Collections.singletonList(driver), startDate, endDate);
            
            if (payrollRows.isEmpty()) {
                logger.warn("No payroll data found for driver: {}", driver.getName());
                return createEmptyDriverIncomeData(driver, startDate, endDate);
            }
            
            PayrollCalculator.PayrollRow payrollRow = payrollRows.get(0);
            
            // Create income data from payroll row
            DriverIncomeData data = new DriverIncomeData(
                driver.getId(),
                driver.getName(),
                driver.getTruckUnit(),
                startDate,
                endDate
            );
            
            // Set gross and loads data
            data.setTotalGross(payrollRow.gross);
            data.setTotalLoads(payrollRow.loadCount);  // Use loadCount instead of numLoads
            
            // Fetch and process loads for detailed information
            List<Load> loads = loadDAO.getByDriverAndDateRange(driver.getId(), startDate, endDate);
            processLoads(data, loads);
            
            // Set fuel data from payroll row
            data.setTotalFuelAmount(Math.abs(payrollRow.fuel));
            data.setTotalFuelCost(Math.abs(payrollRow.fuel)); // Assuming fuel cost includes fees
            data.setTotalFuelFees(0); // Already included in fuel cost
            
            // Fetch fuel transactions for detailed analysis
            List<FuelTransaction> fuelTransactions = fuelDAO.getByDriverAndDateRange(
                driver.getName(), startDate, endDate);
            processFuelTransactions(data, fuelTransactions);
            
            // Set all financial data from payroll row
            data.setServiceFee(Math.abs(payrollRow.serviceFee));
            data.setGrossAfterServiceFee(payrollRow.grossAfterServiceFee);
            data.setCompanyPay(payrollRow.companyPay);
            data.setDriverPay(payrollRow.driverPay);
            data.setGrossAfterFuel(payrollRow.grossAfterFuel);
            
            // Set deductions from payroll row
            data.setRecurringFees(Math.abs(payrollRow.recurringFees));
            data.setAdvancesGiven(payrollRow.advancesGiven);
            data.setAdvanceRepayments(Math.abs(payrollRow.advanceRepayments));
            data.setEscrowDeposits(Math.abs(payrollRow.escrowDeposits));
            data.setOtherDeductions(Math.abs(payrollRow.otherDeductions));
            data.setReimbursements(payrollRow.reimbursements);
            
            // Set net pay
            data.setNetPay(payrollRow.netPay);
            
            logger.info("Income data fetched successfully for driver: {} - Net Pay: ${}", 
                       driver.getName(), payrollRow.netPay);
            
            return data;
        });
    }
    
    /**
     * Get income data for all drivers
     */
    public CompletableFuture<List<DriverIncomeData>> getAllDriversIncomeData(LocalDate startDate, 
                                                                             LocalDate endDate) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Fetching income data for all drivers from {} to {}", startDate, endDate);
            
            // Get all active drivers
            List<Employee> activeDrivers = employeeDAO.getActive().stream()
                .filter(emp -> emp.isDriver())
                .collect(Collectors.toList());
            
            // Use PayrollCalculator to get data for all drivers at once
            List<PayrollCalculator.PayrollRow> allPayrollRows = 
                payrollCalculator.calculatePayrollRows(activeDrivers, startDate, endDate);
            
            // Create a map for quick lookup
            Map<Integer, PayrollCalculator.PayrollRow> payrollMap = allPayrollRows.stream()
                .collect(Collectors.toMap(
                    row -> row.driverId,
                    row -> row,
                    (existing, replacement) -> existing
                ));
            
            // Process each driver
            List<DriverIncomeData> allDriverData = new ArrayList<>();
            
            for (Employee driver : activeDrivers) {
                PayrollCalculator.PayrollRow payrollRow = payrollMap.get(driver.getId());
                
                if (payrollRow == null) {
                    logger.warn("No payroll data found for driver: {}", driver.getName());
                    allDriverData.add(createEmptyDriverIncomeData(driver, startDate, endDate));
                    continue;
                }
                
                // Create income data from payroll row
                DriverIncomeData data = new DriverIncomeData(
                    driver.getId(),
                    driver.getName(),
                    driver.getTruckUnit(),
                    startDate,
                    endDate
                );
                
                // Set all data from payroll row
                data.setTotalGross(payrollRow.gross);
                data.setTotalLoads(payrollRow.loadCount);  // Use loadCount instead of numLoads
                data.setTotalFuelAmount(Math.abs(payrollRow.fuel));
                data.setServiceFee(Math.abs(payrollRow.serviceFee));
                data.setGrossAfterServiceFee(payrollRow.grossAfterServiceFee);
                data.setCompanyPay(payrollRow.companyPay);
                data.setDriverPay(payrollRow.driverPay);
                data.setGrossAfterFuel(payrollRow.grossAfterFuel);
                data.setRecurringFees(Math.abs(payrollRow.recurringFees));
                data.setAdvancesGiven(payrollRow.advancesGiven);
                data.setAdvanceRepayments(Math.abs(payrollRow.advanceRepayments));
                data.setEscrowDeposits(Math.abs(payrollRow.escrowDeposits));
                data.setOtherDeductions(Math.abs(payrollRow.otherDeductions));
                data.setReimbursements(payrollRow.reimbursements);
                data.setNetPay(payrollRow.netPay);
                
                // Fetch loads for mileage calculation
                List<Load> loads = loadDAO.getByDriverAndDateRange(driver.getId(), startDate, endDate);
                double totalMiles = 0;
                for (Load load : loads) {
                    totalMiles += calculateMiles(load.getPickUpLocation(), load.getDropLocation());
                }
                data.setTotalMiles(totalMiles);
                
                allDriverData.add(data);
            }
            
            logger.info("Fetched income data for {} drivers", allDriverData.size());
            return allDriverData;
        });
    }
    
    private DriverIncomeData createEmptyDriverIncomeData(Employee driver, LocalDate startDate, LocalDate endDate) {
        DriverIncomeData data = new DriverIncomeData(
            driver.getId(),
            driver.getName(),
            driver.getTruckUnit(),
            startDate,
            endDate
        );
        
        // Initialize all values to 0
        data.setTotalLoads(0);
        data.setTotalGross(0);
        data.setTotalMiles(0);
        data.setTotalFuelCost(0);
        data.setTotalFuelFees(0);
        data.setTotalFuelAmount(0);
        data.setServiceFee(0);
        data.setRecurringFees(0);
        data.setAdvanceRepayments(0);
        data.setEscrowDeposits(0);
        data.setOtherDeductions(0);
        data.setReimbursements(0);
        data.setAdvancesGiven(0);
        data.setCompanyPay(0);
        data.setDriverPay(0);
        data.setGrossAfterServiceFee(0);
        data.setGrossAfterFuel(0);
        data.setNetPay(0);
        
        return data;
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
        
        // Only update miles, keep gross from payroll calculation
        data.setTotalMiles(totalMiles);
        data.setLoads(loadDetails);
    }
    
    private void processFuelTransactions(DriverIncomeData data, List<FuelTransaction> transactions) {
        // Calculate fuel efficiency if we have miles and fuel quantity
        double totalGallons = transactions.stream()
            .mapToDouble(FuelTransaction::getQty)
            .sum();
        
        if (totalGallons > 0 && data.getTotalMiles() > 0) {
            data.setFuelEfficiency(data.getTotalMiles() / totalGallons);
        }
        
        // Note: We're using fuel cost from PayrollCalculator for accuracy
        // This method now only calculates efficiency metrics
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