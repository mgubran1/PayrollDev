package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PayrollAdvances management system following the PayrollEscrow pattern.
 * Handles cash advances with full tracking, repayment scheduling, and validation.
 */
public class PayrollAdvances implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(PayrollAdvances.class);
    private static final long serialVersionUID = 1L;
    private static final String DATA_FILE = "payroll_advances.dat";
    private static final String SETTINGS_FILE = "payroll_advances_settings.dat";
    
    // Default business rules
    public static final BigDecimal DEFAULT_MAX_ADVANCE = new BigDecimal("5000.00");
    public static final BigDecimal DEFAULT_MIN_ADVANCE = new BigDecimal("50.00");
    public static final int DEFAULT_MAX_WEEKS = 26;
    public static final int DEFAULT_MIN_WEEKS = 1;
    public static final BigDecimal DEFAULT_WEEKLY_LIMIT = new BigDecimal("500.00");
    
    private final List<AdvanceEntry> entries = new ArrayList<>();
    private final Map<Integer, AdvanceSettings> employeeSettings = new HashMap<>();
    private static PayrollAdvances instance;
    
    public enum AdvanceType {
        ADVANCE,
        REPAYMENT,
        ADJUSTMENT,
        FORGIVENESS
    }
    
    public enum AdvanceStatus {
        ACTIVE,
        COMPLETED,
        DEFAULTED,
        FORGIVEN,
        CANCELLED
    }
    
    public enum PaymentMethod {
        PAYROLL_DEDUCTION,
        CASH,
        CHECK,
        BANK_TRANSFER,
        OTHER
    }
    
    /**
     * Settings for individual employees
     */
    public static class AdvanceSettings implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private BigDecimal maxAdvanceAmount;
        private BigDecimal weeklyRepaymentLimit;
        private int maxRepaymentWeeks;
        private boolean allowMultipleAdvances;
        private LocalDate lastModified;
        
        public AdvanceSettings() {
            this.maxAdvanceAmount = DEFAULT_MAX_ADVANCE;
            this.weeklyRepaymentLimit = DEFAULT_WEEKLY_LIMIT;
            this.maxRepaymentWeeks = DEFAULT_MAX_WEEKS;
            this.allowMultipleAdvances = false;
            this.lastModified = LocalDate.now();
        }
        
        // Getters and setters
        public BigDecimal getMaxAdvanceAmount() { return maxAdvanceAmount; }
        public void setMaxAdvanceAmount(BigDecimal maxAdvanceAmount) { 
            this.maxAdvanceAmount = maxAdvanceAmount;
            this.lastModified = LocalDate.now();
        }
        
        public BigDecimal getWeeklyRepaymentLimit() { return weeklyRepaymentLimit; }
        public void setWeeklyRepaymentLimit(BigDecimal weeklyRepaymentLimit) { 
            this.weeklyRepaymentLimit = weeklyRepaymentLimit;
            this.lastModified = LocalDate.now();
        }
        
        public int getMaxRepaymentWeeks() { return maxRepaymentWeeks; }
        public void setMaxRepaymentWeeks(int maxRepaymentWeeks) { 
            this.maxRepaymentWeeks = maxRepaymentWeeks;
            this.lastModified = LocalDate.now();
        }
        
        public boolean isAllowMultipleAdvances() { return allowMultipleAdvances; }
        public void setAllowMultipleAdvances(boolean allowMultipleAdvances) { 
            this.allowMultipleAdvances = allowMultipleAdvances;
            this.lastModified = LocalDate.now();
        }
    }
    
    /**
     * Represents a cash advance entry
     */
    public static class AdvanceEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String id;
        private final LocalDate date;
        private final LocalDate weekStart;
        private final int employeeId;
        private final String employeeName;
        private final AdvanceType type;
        private final BigDecimal amount;
        private String notes;
        private transient BigDecimal balance; // Running balance
        
        // For advances
        private String advanceId; // Unique ID for the advance
        private AdvanceStatus status;
        private int weeksToRepay;
        private BigDecimal weeklyRepaymentAmount;
        private LocalDate firstRepaymentDate;
        private LocalDate lastRepaymentDate;
        private String approvedBy;
        
        // For repayments
        private String parentAdvanceId; // Links repayment to advance
        private PaymentMethod paymentMethod;
        private String referenceNumber;
        private String processedBy;
        
        // Constructor for new advance
        public AdvanceEntry(LocalDate date, LocalDate weekStart, Employee employee, 
                          BigDecimal amount, int weeksToRepay, String notes, String approvedBy) {
            this.id = UUID.randomUUID().toString();
            this.advanceId = "ADV-" + System.currentTimeMillis();
            this.date = date;
            this.weekStart = weekStart;
            this.employeeId = employee.getId();
            this.employeeName = employee.getName();
            this.type = AdvanceType.ADVANCE;
            this.amount = amount;
            this.notes = notes;
            this.status = AdvanceStatus.ACTIVE;
            this.weeksToRepay = weeksToRepay;
            this.weeklyRepaymentAmount = calculateWeeklyPayment(amount, weeksToRepay);
            this.firstRepaymentDate = weekStart.plusWeeks(1);
            this.lastRepaymentDate = firstRepaymentDate.plusWeeks(weeksToRepay - 1);
            this.approvedBy = approvedBy;
        }
        
        // Constructor for repayment
        public AdvanceEntry(LocalDate date, LocalDate weekStart, Employee employee,
                          BigDecimal amount, String parentAdvanceId, PaymentMethod method,
                          String referenceNumber, String notes, String processedBy) {
            this.id = UUID.randomUUID().toString();
            this.date = date;
            this.weekStart = weekStart;
            this.employeeId = employee.getId();
            this.employeeName = employee.getName();
            this.type = AdvanceType.REPAYMENT;
            this.amount = amount.negate(); // Repayments are negative
            this.notes = notes;
            this.parentAdvanceId = parentAdvanceId;
            this.paymentMethod = method;
            this.referenceNumber = referenceNumber;
            this.processedBy = processedBy;
            this.status = AdvanceStatus.ACTIVE;
        }
        
        // Constructor for adjustment/forgiveness
        public AdvanceEntry(LocalDate date, Employee employee, AdvanceType type,
                          BigDecimal amount, String parentAdvanceId, String notes, String processedBy) {
            this.id = UUID.randomUUID().toString();
            this.date = date;
            this.weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            this.employeeId = employee.getId();
            this.employeeName = employee.getName();
            this.type = type;
            this.amount = type == AdvanceType.FORGIVENESS ? amount.negate() : amount;
            this.notes = notes;
            this.parentAdvanceId = parentAdvanceId;
            this.processedBy = processedBy;
            this.status = AdvanceStatus.ACTIVE;
        }
        
        private BigDecimal calculateWeeklyPayment(BigDecimal totalAmount, int weeks) {
            return totalAmount.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.UP);
        }
        
        // Getters
        public String getId() { return id; }
        public String getAdvanceId() { return advanceId; }
        public LocalDate getDate() { return date; }
        public LocalDate getWeekStart() { return weekStart; }
        public int getEmployeeId() { return employeeId; }
        public String getEmployeeName() { return employeeName; }
        public String getType() { return type.toString(); }
        public AdvanceType getAdvanceType() { return type; }
        public BigDecimal getAmount() { return amount; }
        public String getNotes() { return notes; }
        public BigDecimal getBalance() { return balance; }
        public AdvanceStatus getStatus() { return status; }
        public String getStatusDisplay() { return status != null ? status.toString() : ""; }
        public int getWeeksToRepay() { return weeksToRepay; }
        public BigDecimal getWeeklyRepaymentAmount() { return weeklyRepaymentAmount; }
        public LocalDate getFirstRepaymentDate() { return firstRepaymentDate; }
        public LocalDate getLastRepaymentDate() { return lastRepaymentDate; }
        public String getApprovedBy() { return approvedBy; }
        public String getParentAdvanceId() { return parentAdvanceId; }
        public PaymentMethod getPaymentMethod() { return paymentMethod; }
        public String getReferenceNumber() { return referenceNumber; }
        public String getProcessedBy() { return processedBy; }
        
        // Setters
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public void setNotes(String notes) { this.notes = notes; }
        public void setStatus(AdvanceStatus status) { this.status = status; }
    }
    
    private PayrollAdvances() {
        loadData();
        loadSettings();
    }
    
    public static PayrollAdvances getInstance() {
        if (instance == null) {
            instance = new PayrollAdvances();
        }
        return instance;
    }
    
    // Employee settings management
    public void updateEmployeeSettings(Employee employee, AdvanceSettings settings) {
        if (employee != null && settings != null) {
            employeeSettings.put(employee.getId(), settings);
            saveSettings();
            logger.info("Updated advance settings for employee {}", employee.getName());
        }
    }
    
    public AdvanceSettings getEmployeeSettings(Employee employee) {
        if (employee == null) return new AdvanceSettings();
        return employeeSettings.getOrDefault(employee.getId(), new AdvanceSettings());
    }
    
    // Advance management
    public AdvanceEntry createAdvance(Employee employee, LocalDate weekStart, BigDecimal amount, 
                                    int weeksToRepay, String notes, String approvedBy) {
        // Validation
        AdvanceSettings settings = getEmployeeSettings(employee);
        
        if (amount.compareTo(DEFAULT_MIN_ADVANCE) < 0) {
            logger.warn("Advance amount ${} is below minimum ${}", amount, DEFAULT_MIN_ADVANCE);
            return null;
        }
        
        if (amount.compareTo(settings.getMaxAdvanceAmount()) > 0) {
            logger.warn("Advance amount ${} exceeds maximum ${} for employee {}", 
                amount, settings.getMaxAdvanceAmount(), employee.getName());
            return null;
        }
        
        if (weeksToRepay < DEFAULT_MIN_WEEKS || weeksToRepay > settings.getMaxRepaymentWeeks()) {
            logger.warn("Invalid repayment period: {} weeks", weeksToRepay);
            return null;
        }
        
        // Check for existing active advances
        if (!settings.isAllowMultipleAdvances() && hasActiveAdvance(employee)) {
            logger.warn("Employee {} already has an active advance", employee.getName());
            return null;
        }
        
        // Check total outstanding
        BigDecimal currentBalance = getCurrentBalance(employee);
        if (currentBalance.add(amount).compareTo(settings.getMaxAdvanceAmount()) > 0) {
            logger.warn("Total advances would exceed maximum for employee {}", employee.getName());
            return null;
        }
        
        AdvanceEntry entry = new AdvanceEntry(LocalDate.now(), weekStart, employee, 
            amount, weeksToRepay, notes, approvedBy);
        entries.add(entry);
        saveData();
        logger.info("Created advance {} for employee {} amount ${}", 
            entry.getAdvanceId(), employee.getName(), amount);
        return entry;
    }
    
    public AdvanceEntry recordRepayment(Employee employee, LocalDate weekStart, BigDecimal amount,
                                      String advanceId, PaymentMethod method, String referenceNumber,
                                      String notes, String processedBy) {
        // Find the parent advance
        AdvanceEntry parentAdvance = findAdvanceById(advanceId);
        if (parentAdvance == null) {
            logger.error("Parent advance {} not found", advanceId);
            return null;
        }
        
        if (parentAdvance.getStatus() != AdvanceStatus.ACTIVE) {
            logger.warn("Cannot add repayment to {} advance", parentAdvance.getStatus());
            return null;
        }
        
        AdvanceEntry entry = new AdvanceEntry(LocalDate.now(), weekStart, employee,
            amount, advanceId, method, referenceNumber, notes, processedBy);
        entries.add(entry);
        
        // Check if advance is fully repaid
        updateAdvanceStatus(advanceId);
        
        saveData();
        logger.info("Recorded repayment of ${} for advance {}", amount, advanceId);
        return entry;
    }
    
    public AdvanceEntry createAdjustment(Employee employee, AdvanceType type, BigDecimal amount,
                                       String advanceId, String notes, String processedBy) {
        if (type != AdvanceType.ADJUSTMENT && type != AdvanceType.FORGIVENESS) {
            logger.error("Invalid adjustment type: {}", type);
            return null;
        }
        
        AdvanceEntry entry = new AdvanceEntry(LocalDate.now(), employee, type,
            amount, advanceId, notes, processedBy);
        entries.add(entry);
        
        // Update advance status if forgiven
        if (type == AdvanceType.FORGIVENESS) {
            AdvanceEntry advance = findAdvanceById(advanceId);
            if (advance != null) {
                advance.setStatus(AdvanceStatus.FORGIVEN);
            }
        }
        
        saveData();
        logger.info("Created {} of ${} for advance {}", type, amount, advanceId);
        return entry;
    }
    
    public void deleteEntry(String entryId) {
        entries.removeIf(e -> e.getId().equals(entryId));
        saveData();
        logger.info("Deleted advance entry {}", entryId);
    }
    
    // Query methods
    public List<AdvanceEntry> getEntriesForEmployee(int employeeId) {
        List<AdvanceEntry> employeeEntries = entries.stream()
            .filter(e -> e.getEmployeeId() == employeeId)
            .sorted(Comparator.comparing(AdvanceEntry::getDate))
            .collect(Collectors.toList());
            
        // Calculate running balance
        BigDecimal runningBalance = BigDecimal.ZERO;
        for (AdvanceEntry entry : employeeEntries) {
            runningBalance = runningBalance.add(entry.getAmount());
            entry.setBalance(runningBalance);
        }
        
        // Return in reverse order (newest first)
        Collections.reverse(employeeEntries);
        return employeeEntries;
    }
    
    public List<AdvanceEntry> getAllEntries() {
        // Create a list with all entries and calculate balances
        Map<Integer, BigDecimal> balances = new HashMap<>();
        List<AdvanceEntry> allEntriesSorted = new ArrayList<>(entries);
        allEntriesSorted.sort(Comparator.comparing(AdvanceEntry::getDate));
        
        for (AdvanceEntry entry : allEntriesSorted) {
            BigDecimal currentBalance = balances.getOrDefault(entry.getEmployeeId(), BigDecimal.ZERO);
            currentBalance = currentBalance.add(entry.getAmount());
            balances.put(entry.getEmployeeId(), currentBalance);
            entry.setBalance(currentBalance);
        }
        
        // Return in reverse order (newest first)
        Collections.reverse(allEntriesSorted);
        return allEntriesSorted;
    }
    
    public List<AdvanceEntry> getActiveAdvances() {
        return entries.stream()
            .filter(e -> e.getAdvanceType() == AdvanceType.ADVANCE)
            .filter(e -> e.getStatus() == AdvanceStatus.ACTIVE)
            .sorted(Comparator.comparing(AdvanceEntry::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    public List<AdvanceEntry> getAdvancesForEmployee(Employee employee) {
        if (employee == null) return new ArrayList<>();
        
        return entries.stream()
            .filter(e -> e.getEmployeeId() == employee.getId())
            .filter(e -> e.getAdvanceType() == AdvanceType.ADVANCE)
            .sorted(Comparator.comparing(AdvanceEntry::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    public List<AdvanceEntry> getRepaymentsForAdvance(String advanceId) {
        return entries.stream()
            .filter(e -> advanceId.equals(e.getParentAdvanceId()))
            .filter(e -> e.getAdvanceType() == AdvanceType.REPAYMENT)
            .sorted(Comparator.comparing(AdvanceEntry::getDate))
            .collect(Collectors.toList());
    }
    
    // Balance calculations
    public BigDecimal getCurrentBalance(Employee employee) {
        if (employee == null) return BigDecimal.ZERO;
        
        return entries.stream()
            .filter(e -> e.getEmployeeId() == employee.getId())
            .map(AdvanceEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getTotalAdvanced(Employee employee) {
        if (employee == null) return BigDecimal.ZERO;
        
        return entries.stream()
            .filter(e -> e.getEmployeeId() == employee.getId())
            .filter(e -> e.getAdvanceType() == AdvanceType.ADVANCE)
            .map(AdvanceEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getTotalRepaid(Employee employee) {
        if (employee == null) return BigDecimal.ZERO;

        return entries.stream()
            .filter(e -> e.getEmployeeId() == employee.getId())
            .filter(e -> e.getAdvanceType() == AdvanceType.REPAYMENT)
            .map(e -> e.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Convenience overload to get the total repaid for an employee by ID.
     * This avoids having to fetch the {@link Employee} object when only the
     * identifier is known.
     */
    public BigDecimal getTotalRepaid(int employeeId) {
        return entries.stream()
            .filter(e -> e.getEmployeeId() == employeeId)
            .filter(e -> e.getAdvanceType() == AdvanceType.REPAYMENT)
            .map(e -> e.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getAdvanceBalance(String advanceId) {
        AdvanceEntry advance = findAdvanceById(advanceId);
        if (advance == null) return BigDecimal.ZERO;
        
        BigDecimal totalRepaid = entries.stream()
            .filter(e -> advanceId.equals(e.getParentAdvanceId()))
            .filter(e -> e.getAdvanceType() == AdvanceType.REPAYMENT || 
                        e.getAdvanceType() == AdvanceType.FORGIVENESS)
            .map(e -> e.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        return advance.getAmount().subtract(totalRepaid);
    }
    
    public BigDecimal getScheduledRepaymentForWeek(Employee employee, LocalDate weekStart) {
        if (employee == null || weekStart == null) return BigDecimal.ZERO;
        
        return getActiveAdvances().stream()
            .filter(a -> a.getEmployeeId() == employee.getId())
            .filter(a -> !weekStart.isBefore(a.getFirstRepaymentDate()))
            .filter(a -> !weekStart.isAfter(a.getLastRepaymentDate()))
            .map(AdvanceEntry::getWeeklyRepaymentAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public boolean hasActiveAdvance(Employee employee) {
        if (employee == null) return false;
        
        return entries.stream()
            .filter(e -> e.getEmployeeId() == employee.getId())
            .filter(e -> e.getAdvanceType() == AdvanceType.ADVANCE)
            .anyMatch(e -> e.getStatus() == AdvanceStatus.ACTIVE);
    }
    
    public int getActiveAdvanceCount(Employee employee) {
        if (employee == null) return 0;
        
        return (int) entries.stream()
            .filter(e -> e.getEmployeeId() == employee.getId())
            .filter(e -> e.getAdvanceType() == AdvanceType.ADVANCE)
            .filter(e -> e.getStatus() == AdvanceStatus.ACTIVE)
            .count();
    }
    
    // Status management
    private void updateAdvanceStatus(String advanceId) {
        AdvanceEntry advance = findAdvanceById(advanceId);
        if (advance == null || advance.getStatus() != AdvanceStatus.ACTIVE) return;
        
        BigDecimal balance = getAdvanceBalance(advanceId);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            advance.setStatus(AdvanceStatus.COMPLETED);
            logger.info("Advance {} marked as completed", advanceId);
        }
    }
    
    public void markAdvanceAsDefaulted(String advanceId, String reason) {
        AdvanceEntry advance = findAdvanceById(advanceId);
        if (advance != null && advance.getStatus() == AdvanceStatus.ACTIVE) {
            advance.setStatus(AdvanceStatus.DEFAULTED);
            advance.setNotes(advance.getNotes() + " | DEFAULTED: " + reason);
            saveData();
            logger.info("Advance {} marked as defaulted: {}", advanceId, reason);
        }
    }
    
    public void cancelAdvance(String advanceId, String reason) {
        AdvanceEntry advance = findAdvanceById(advanceId);
        if (advance != null && advance.getStatus() == AdvanceStatus.ACTIVE) {
            // Check if there are any repayments
            BigDecimal totalRepaid = getTotalRepaid(advance.getEmployeeId());
            if (totalRepaid.compareTo(BigDecimal.ZERO) > 0) {
                logger.warn("Cannot cancel advance {} with existing repayments", advanceId);
                return;
            }
            
            advance.setStatus(AdvanceStatus.CANCELLED);
            advance.setNotes(advance.getNotes() + " | CANCELLED: " + reason);
            saveData();
            logger.info("Advance {} cancelled: {}", advanceId, reason);
        }
    }
    
    // Helper methods
    private AdvanceEntry findAdvanceById(String advanceId) {
        return entries.stream()
            .filter(e -> advanceId.equals(e.getAdvanceId()) || advanceId.equals(e.getId()))
            .findFirst()
            .orElse(null);
    }
    
    // Persistence methods
    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(entries);
            logger.debug("Advance entries saved to {}", DATA_FILE);
        } catch (IOException e) {
            logger.error("Failed to save advance data", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                List<AdvanceEntry> loaded = (List<AdvanceEntry>) ois.readObject();
                entries.clear();
                entries.addAll(loaded);
                logger.info("Loaded {} advance entries from {}", entries.size(), DATA_FILE);
            } catch (Exception e) {
                logger.error("Failed to load advance data", e);
            }
        }
    }
    
    private void saveSettings() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SETTINGS_FILE))) {
            oos.writeObject(employeeSettings);
            logger.debug("Employee settings saved to {}", SETTINGS_FILE);
        } catch (IOException e) {
            logger.error("Failed to save employee settings", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Map<Integer, AdvanceSettings> loaded = (Map<Integer, AdvanceSettings>) ois.readObject();
                employeeSettings.clear();
                employeeSettings.putAll(loaded);
                logger.info("Loaded {} employee settings from {}", employeeSettings.size(), SETTINGS_FILE);
            } catch (Exception e) {
                logger.error("Failed to load employee settings", e);
            }
        }
    }
}