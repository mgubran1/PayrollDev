package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class PayrollEscrow implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(PayrollEscrow.class);
    private static final long serialVersionUID = 1L;
    private static final String DATA_FILE = "payroll_escrow.dat";
    private static final String TARGET_AMOUNTS_FILE = "payroll_escrow_targets.dat";
    
    // Default target escrow amount
    public static final BigDecimal DEFAULT_TARGET_AMOUNT = new BigDecimal("3000.00");
    
    private final List<EscrowEntry> entries = new ArrayList<>();
    private final Map<Integer, BigDecimal> driverTargetAmounts = new HashMap<>();
    private static PayrollEscrow instance;
    
    public enum EscrowType {
        DEPOSIT,
        WITHDRAWAL
    }
    
    public static class EscrowEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String id;
        private final LocalDate date;
        private final LocalDate weekStart;
        private final int driverId;
        private final String driverName;
        private final EscrowType type;
        private final BigDecimal amount;
        private String notes;
        private transient BigDecimal balance; // Running balance
        
        public EscrowEntry(LocalDate date, LocalDate weekStart, Employee driver, 
                          EscrowType type, BigDecimal amount, String notes) {
            this.id = UUID.randomUUID().toString();
            this.date = date;
            this.weekStart = weekStart;
            this.driverId = driver.getId();
            this.driverName = driver.getName();
            this.type = type;
            this.amount = amount;
            this.notes = notes;
        }
        
        // Getters
        public String getId() { return id; }
        public LocalDate getDate() { return date; }
        public LocalDate getWeekStart() { return weekStart; }
        public int getDriverId() { return driverId; }
        public String getDriverName() { return driverName; }
        public String getType() { return type.toString(); }
        public BigDecimal getAmount() { return amount; }
        public String getNotes() { return notes; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public void setNotes(String notes) { this.notes = notes; }
    }
    
    private PayrollEscrow() {
        loadData();
        loadTargetAmounts();
    }
    
    public static PayrollEscrow getInstance() {
        if (instance == null) {
            instance = new PayrollEscrow();
        }
        return instance;
    }
    
    // Target amount management
    public void setTargetAmount(Employee driver, BigDecimal amount) {
        if (driver != null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            driverTargetAmounts.put(driver.getId(), amount);
            saveTargetAmounts();
            logger.info("Set target escrow amount for driver {} to ${}", driver.getName(), amount);
        }
    }
    
    public BigDecimal getTargetAmount(Employee driver) {
        if (driver == null) return DEFAULT_TARGET_AMOUNT;
        return driverTargetAmounts.getOrDefault(driver.getId(), DEFAULT_TARGET_AMOUNT);
    }
    
    // Entry management
    public EscrowEntry addDeposit(Employee driver, LocalDate transactionDate, LocalDate weekStart, BigDecimal amount, String notes) {
        EscrowEntry entry = new EscrowEntry(transactionDate, weekStart, driver, EscrowType.DEPOSIT, amount, notes);
        entries.add(entry);
        saveData();
        logger.info("Added escrow deposit for driver {} amount ${} on {}", driver.getName(), amount, transactionDate);
        return entry;
    }
    
    public EscrowEntry addWithdrawal(Employee driver, LocalDate transactionDate, LocalDate weekStart, BigDecimal amount, String notes) {
        BigDecimal currentBalance = getCurrentBalance(driver);
        if (amount.compareTo(currentBalance) > 0) {
            logger.warn("Withdrawal amount ${} exceeds current balance ${} for driver {}", 
                amount, currentBalance, driver.getName());
            return null;
        }
        
        EscrowEntry entry = new EscrowEntry(transactionDate, weekStart, driver, EscrowType.WITHDRAWAL, amount, notes);
        entries.add(entry);
        saveData();
        logger.info("Added escrow withdrawal for driver {} amount ${} on {}", driver.getName(), amount, transactionDate);
        return entry;
    }
    
    public void deleteEntry(String entryId) {
        entries.removeIf(e -> e.getId().equals(entryId));
        saveData();
        logger.info("Deleted escrow entry {}", entryId);
    }
    
    /**
     * Clear all escrow data (for testing/debugging)
     */
    public void clearAllData() {
        entries.clear();
        saveData();
        logger.info("Cleared all escrow data");
    }
    
    // Query methods
    public List<EscrowEntry> getEntriesForDriver(int driverId) {
        List<EscrowEntry> driverEntries = entries.stream()
            .filter(e -> e.getDriverId() == driverId)
            .sorted(Comparator.comparing(EscrowEntry::getDate))
            .collect(Collectors.toList());
            
        // Calculate running balance
        BigDecimal runningBalance = BigDecimal.ZERO;
        for (EscrowEntry entry : driverEntries) {
            if (entry.type == EscrowType.DEPOSIT) {
                runningBalance = runningBalance.add(entry.getAmount());
            } else {
                runningBalance = runningBalance.subtract(entry.getAmount());
            }
            entry.setBalance(runningBalance);
        }
        
        // Return in reverse order (newest first)
        Collections.reverse(driverEntries);
        return driverEntries;
    }
    
    public List<EscrowEntry> getAllEntries() {
        // Create a list with all entries and calculate balances
        Map<Integer, BigDecimal> balances = new HashMap<>();
        List<EscrowEntry> allEntriesSorted = new ArrayList<>(entries);
        allEntriesSorted.sort(Comparator.comparing(EscrowEntry::getDate));
        
        for (EscrowEntry entry : allEntriesSorted) {
            BigDecimal currentBalance = balances.getOrDefault(entry.getDriverId(), BigDecimal.ZERO);
            if (entry.type == EscrowType.DEPOSIT) {
                currentBalance = currentBalance.add(entry.getAmount());
            } else {
                currentBalance = currentBalance.subtract(entry.getAmount());
            }
            balances.put(entry.getDriverId(), currentBalance);
            entry.setBalance(currentBalance);
        }
        
        // Return in reverse order (newest first)
        Collections.reverse(allEntriesSorted);
        return allEntriesSorted;
    }
    
    // Balance calculations
    public BigDecimal getCurrentBalance(Employee driver) {
        if (driver == null) return BigDecimal.ZERO;
        
        return entries.stream()
            .filter(e -> e.getDriverId() == driver.getId())
            .map(e -> e.type == EscrowType.DEPOSIT ? e.getAmount() : e.getAmount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getTotalDeposits(Employee driver) {
        if (driver == null) return BigDecimal.ZERO;
        
        return entries.stream()
            .filter(e -> e.getDriverId() == driver.getId())
            .filter(e -> e.type == EscrowType.DEPOSIT)
            .map(EscrowEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getTotalWithdrawals(Employee driver) {
        if (driver == null) return BigDecimal.ZERO;
        
        return entries.stream()
            .filter(e -> e.getDriverId() == driver.getId())
            .filter(e -> e.type == EscrowType.WITHDRAWAL)
            .map(EscrowEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getRemainingToTarget(Employee driver) {
        if (driver == null) return DEFAULT_TARGET_AMOUNT;
        
        BigDecimal currentBalance = getCurrentBalance(driver);
        BigDecimal targetAmount = getTargetAmount(driver);
        BigDecimal remaining = targetAmount.subtract(currentBalance);
        
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }
    
    /**
     * Check if escrow is fully funded. Modified to only return true when
     * the balance EXCEEDS the target, not when it equals it.
     * This ensures deductions continue even when exactly at target.
     */
    public boolean isEscrowFullyFunded(Employee driver) {
        if (driver == null) return false;
        
        BigDecimal currentBalance = getCurrentBalance(driver);
        BigDecimal targetAmount = getTargetAmount(driver);
        // Changed from >= to > to allow deductions when exactly at target
        return currentBalance.compareTo(targetAmount) > 0;
    }
    
    /**
     * Alternative method to check if escrow has reached the target amount.
     * This can be used for display purposes to show "Fully Funded" status.
     */
    public boolean hasReachedTarget(Employee driver) {
        if (driver == null) return false;
        
        BigDecimal currentBalance = getCurrentBalance(driver);
        BigDecimal targetAmount = getTargetAmount(driver);
        return currentBalance.compareTo(targetAmount) >= 0;
    }
    
    public BigDecimal getWeeklyAmount(Employee driver, LocalDate weekStart) {
        if (driver == null || weekStart == null) return BigDecimal.ZERO;

        return entries.stream()
            .filter(e -> e.getDriverId() == driver.getId())
            .filter(e -> e.getWeekStart() != null && e.getWeekStart().equals(weekStart))
            .filter(e -> e.type == EscrowType.DEPOSIT)
            .map(EscrowEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get the total deposits for a specific week. Alias for backward
     * compatibility with existing calls to {@code getWeeklyAmount}.
     */
    public BigDecimal getWeeklyDeposits(Employee driver, LocalDate weekStart) {
        return getWeeklyAmount(driver, weekStart);
    }

    /**
     * Get the total withdrawals for a specific week.
     */
    public BigDecimal getWeeklyWithdrawals(Employee driver, LocalDate weekStart) {
        if (driver == null || weekStart == null) return BigDecimal.ZERO;

        return entries.stream()
            .filter(e -> e.getDriverId() == driver.getId())
            .filter(e -> e.getWeekStart() != null && e.getWeekStart().equals(weekStart))
            .filter(e -> e.type == EscrowType.WITHDRAWAL)
            .map(EscrowEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // Persistence methods
    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(entries);
            logger.debug("Escrow entries saved to {}", DATA_FILE);
        } catch (IOException e) {
            logger.error("Failed to save escrow data", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                List<EscrowEntry> loaded = (List<EscrowEntry>) ois.readObject();
                entries.clear();
                entries.addAll(loaded);
                logger.info("Loaded {} escrow entries from {}", entries.size(), DATA_FILE);
            } catch (Exception e) {
                logger.error("Failed to load escrow data", e);
            }
        }
    }
    
    private void saveTargetAmounts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(TARGET_AMOUNTS_FILE))) {
            oos.writeObject(driverTargetAmounts);
            logger.debug("Target amounts saved to {}", TARGET_AMOUNTS_FILE);
        } catch (IOException e) {
            logger.error("Failed to save target amounts", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadTargetAmounts() {
        File file = new File(TARGET_AMOUNTS_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Map<Integer, BigDecimal> loaded = (Map<Integer, BigDecimal>) ois.readObject();
                driverTargetAmounts.clear();
                driverTargetAmounts.putAll(loaded);
                logger.info("Loaded {} driver target amounts from {}", driverTargetAmounts.size(), TARGET_AMOUNTS_FILE);
            } catch (Exception e) {
                logger.error("Failed to load target amounts", e);
            }
        }
    }
}