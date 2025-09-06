package com.company.payroll.payroll;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe, business-ready PayrollOtherAdjustments management system.
 * Handles payroll adjustments (deductions and reimbursements) with comprehensive validation, 
 * audit trails, and error recovery.
 */
public class PayrollOtherAdjustments implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(PayrollOtherAdjustments.class);
    private static final long serialVersionUID = 3L; // Incremented for compatibility
    private static final String ADJ_DATA_FILE = "payroll_other_adjustments.dat";
    private static final String BACKUP_DATA_FILE = "payroll_other_adjustments.bak";
    private static final BigDecimal MAX_ADJUSTMENT_AMOUNT = new BigDecimal("50000.00");
    private static final BigDecimal MIN_ADJUSTMENT_AMOUNT = new BigDecimal("0.01");
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, List<OtherAdjustment>> adjustments = new ConcurrentHashMap<>();
    private final Map<String, AuditEntry> auditTrail = new ConcurrentHashMap<>();
    private transient boolean dataModified = false;
    private transient int nextId = 1;
    
    private static volatile PayrollOtherAdjustments instance;

    /**
     * Audit entry for tracking all changes
     */
    public static class AuditEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String id;
        public final LocalDateTime timestamp;
        public final String action;
        public final String employeeId;
        public final String details;
        public final String performedBy;
        
        public AuditEntry(String action, String employeeId, String details, String performedBy) {
            this.id = UUID.randomUUID().toString();
            this.timestamp = LocalDateTime.now();
            this.action = action;
            this.employeeId = employeeId;
            this.details = details;
            this.performedBy = performedBy;
        }
    }

    /**
     * Enhanced OtherAdjustment with BigDecimal precision and better tracking
     */
    public static class OtherAdjustment implements Serializable, Cloneable {
        private static final long serialVersionUID = 2L;
        
        public final int id;
        public final int driverId;
        public final String category; // "Deduction" or "Reimbursement"
        public final String type;     // For bonus: "Load Bonus: {loadNumber}"
        public final BigDecimal amount;   // Always positive, sign handled in calculation
        public final String description;
        public final LocalDate weekStart;
        public final String loadNumber; // For bonus adjustments, otherwise null
        public final LocalDateTime createdDate;
        public final String createdBy;
        public final String referenceNumber; // For tracking external references
        public final AdjustmentStatus status;
        
        public enum AdjustmentStatus {
            ACTIVE("Active"),
            REVERSED("Reversed"),
            PENDING("Pending Approval"),
            APPROVED("Approved"),
            REJECTED("Rejected");
            
            private final String displayName;
            
            AdjustmentStatus(String displayName) {
                this.displayName = displayName;
            }
            
            public String getDisplayName() {
                return displayName;
            }
        }

        // Primary constructor with all fields
        public OtherAdjustment(int id, int driverId, String category, String type, BigDecimal amount, 
                             String description, LocalDate weekStart, String loadNumber, 
                             String createdBy, String referenceNumber, AdjustmentStatus status) {
            // Validation
            if (amount != null && (amount.compareTo(MIN_ADJUSTMENT_AMOUNT) < 0 || 
                                  amount.compareTo(MAX_ADJUSTMENT_AMOUNT) > 0)) {
                throw new IllegalArgumentException("Amount must be between " + MIN_ADJUSTMENT_AMOUNT + 
                                                 " and " + MAX_ADJUSTMENT_AMOUNT);
            }
            
            this.id = id;
            this.driverId = driverId;
            this.category = category;
            this.type = type;
            this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            this.description = description;
            this.weekStart = weekStart;
            this.loadNumber = loadNumber;
            this.createdDate = LocalDateTime.now();
            this.createdBy = createdBy;
            this.referenceNumber = referenceNumber;
            this.status = status != null ? status : AdjustmentStatus.ACTIVE;
        }

        // Backward compatibility constructors
        public OtherAdjustment(int id, int driverId, String category, String type, double amount, 
                             String description, LocalDate weekStart) {
            this(id, driverId, category, type, BigDecimal.valueOf(amount), description, weekStart, 
                 null, "System", null, AdjustmentStatus.ACTIVE);
        }
        
        public OtherAdjustment(int id, int driverId, String category, String type, double amount, 
                             String description, LocalDate weekStart, String loadNumber) {
            this(id, driverId, category, type, BigDecimal.valueOf(amount), description, weekStart, 
                 loadNumber, "System", null, AdjustmentStatus.ACTIVE);
        }

        @Override
        public OtherAdjustment clone() {
            try {
                return (OtherAdjustment) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Clone not supported", e);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof OtherAdjustment)) return false;
            OtherAdjustment o = (OtherAdjustment) obj;
            return id == o.id &&
                   driverId == o.driverId &&
                   Objects.equals(category, o.category) &&
                   Objects.equals(type, o.type) &&
                   Objects.equals(amount, o.amount) &&
                   Objects.equals(description, o.description) &&
                   Objects.equals(weekStart, o.weekStart) &&
                   Objects.equals(loadNumber, o.loadNumber) &&
                   Objects.equals(referenceNumber, o.referenceNumber) &&
                   Objects.equals(status, o.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, driverId, category, type, amount, description, weekStart, 
                              loadNumber, referenceNumber, status);
        }
        
        // Backward compatibility
        public double getAmountAsDouble() {
            return amount.doubleValue();
        }
    }

    private PayrollOtherAdjustments() {
        logger.info("Initializing PayrollOtherAdjustments system");
        loadData();
        initializeNextId();
    }

    public static PayrollOtherAdjustments getInstance() {
        if (instance == null) {
            synchronized (PayrollOtherAdjustments.class) {
                if (instance == null) {
                    instance = new PayrollOtherAdjustments();
                }
            }
        }
        return instance;
    }

    private static String key(int driverId, LocalDate weekStart) {
        return driverId + "_" + weekStart.toString();
    }

    /**
     * Get adjustments for a driver and week with defensive copying
     */
    public List<OtherAdjustment> getAdjustmentsForDriverWeek(int driverId, LocalDate weekStart) {
        lock.readLock().lock();
        try {
            logger.debug("Getting adjustments for driver {} week {}", driverId, weekStart);
            String keyStr = key(driverId, weekStart);
            List<OtherAdjustment> list = adjustments.getOrDefault(keyStr, Collections.emptyList())
                .stream()
                .filter(adj -> adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE || 
                              adj.status == OtherAdjustment.AdjustmentStatus.APPROVED)
                .map(OtherAdjustment::clone)
                .collect(Collectors.toList());
            logger.debug("Found {} active adjustments for key {}", list.size(), keyStr);
            return list;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Save adjustments with validation and audit trail
     */
    public boolean saveAdjustmentsForDriverWeek(int driverId, LocalDate weekStart, List<OtherAdjustment> list) {
        lock.writeLock().lock();
        try {
            logger.info("Saving {} adjustments for driver {} week {}", list.size(), driverId, weekStart);
            
            // Validate all adjustments
            ValidationResult validation = validateAdjustments(list);
            if (!validation.isValid()) {
                logger.warn("Adjustment validation failed: {}", validation.getErrorMessage());
                return false;
            }
            
            String keyStr = key(driverId, weekStart);
            List<OtherAdjustment> existingList = adjustments.getOrDefault(keyStr, new ArrayList<>());
            
            // Build a map of existing adjustments by ID for tracking changes
            Map<Integer, OtherAdjustment> existingMap = existingList.stream()
                .collect(Collectors.toMap(adj -> adj.id, adj -> adj));
            
            // Process the new list
            List<OtherAdjustment> processedList = new ArrayList<>();
            for (OtherAdjustment adj : list) {
                if (adj.id == 0) {
                    // New adjustment - assign ID
                    OtherAdjustment newAdj = new OtherAdjustment(
                        getNextId(), adj.driverId, adj.category, adj.type, adj.amount,
                        adj.description, adj.weekStart, adj.loadNumber, adj.createdBy,
                        adj.referenceNumber, adj.status
                    );
                    processedList.add(newAdj);
                    
                    addAuditEntry("ADJUSTMENT_CREATED", String.valueOf(driverId),
                        String.format("Created %s adjustment: %s - $%.2f", 
                            adj.category, adj.type, adj.amount), adj.createdBy);
                } else {
                    // Existing adjustment - check for changes
                    OtherAdjustment existing = existingMap.get(adj.id);
                    if (existing != null && !existing.equals(adj)) {
                        addAuditEntry("ADJUSTMENT_MODIFIED", String.valueOf(driverId),
                            String.format("Modified adjustment ID %d: %s", adj.id, adj.type), 
                            adj.createdBy);
                    }
                    processedList.add(adj);
                }
            }
            
            // Replace the entire list for this key
            adjustments.put(keyStr, new ArrayList<>(processedList));
            dataModified = true;
            save();
            
            logger.info("Adjustments saved successfully for key {}", keyStr);
            return true;
            
        } catch (Exception e) {
            logger.error("Error saving adjustments", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove adjustment by ID with soft delete
     */
    public OperationResult removeAdjustmentById(int id, String removedBy, String reason) {
        lock.writeLock().lock();
        try {
            logger.info("Removing adjustment with id {} by {}", id, removedBy);
            
            boolean found = false;
            String foundKey = null;
            OtherAdjustment toRemove = null;
            
            // Find the adjustment
            for (Map.Entry<String, List<OtherAdjustment>> entry : adjustments.entrySet()) {
                for (OtherAdjustment adj : entry.getValue()) {
                    if (adj.id == id && (adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE ||
                                        adj.status == OtherAdjustment.AdjustmentStatus.APPROVED)) {
                        found = true;
                        foundKey = entry.getKey();
                        toRemove = adj;
                        break;
                    }
                }
                if (found) break;
            }
            
            if (found && toRemove != null) {
                List<OtherAdjustment> list = adjustments.get(foundKey);
                
                // Create reversed entry
                OtherAdjustment reversed = new OtherAdjustment(
                    getNextId(), toRemove.driverId, toRemove.category, toRemove.type, toRemove.amount,
                    "REVERSED: " + toRemove.description + " - " + reason,
                    toRemove.weekStart, toRemove.loadNumber, removedBy, toRemove.referenceNumber,
                    OtherAdjustment.AdjustmentStatus.REVERSED
                );
                
                // Update original to reversed status
                List<OtherAdjustment> updatedList = new ArrayList<>();
                for (OtherAdjustment adj : list) {
                    if (adj.id == id) {
                        // Mark original as reversed
                        OtherAdjustment reversedOriginal = new OtherAdjustment(
                            adj.id, adj.driverId, adj.category, adj.type, adj.amount,
                            adj.description, adj.weekStart, adj.loadNumber, adj.createdBy,
                            adj.referenceNumber, OtherAdjustment.AdjustmentStatus.REVERSED
                        );
                        updatedList.add(reversedOriginal);
                    } else {
                        updatedList.add(adj);
                    }
                }
                
                // Add the reversal entry
                updatedList.add(reversed);
                
                adjustments.put(foundKey, updatedList);
                
                addAuditEntry("ADJUSTMENT_REVERSED", String.valueOf(toRemove.driverId),
                    String.format("Reversed adjustment ID %d: %s - %s", id, toRemove.type, reason), removedBy);
                
                dataModified = true;
                save();
                
                logger.info("Adjustment {} reversed successfully", id);
                return new OperationResult(true, "Adjustment reversed successfully");
            } else {
                logger.warn("No active adjustment found with id {}", id);
                return new OperationResult(false, "Adjustment not found or already reversed");
            }
            
        } catch (Exception e) {
            logger.error("Error reversing adjustment", e);
            return new OperationResult(false, "Error reversing adjustment: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Backward compatibility method
    public void removeAdjustmentById(int id) {
        removeAdjustmentById(id, "System", "Removed via legacy method");
    }

    /**
     * Get total deductions with BigDecimal precision
     */
    public BigDecimal getTotalDeductionsBD(int driverId, LocalDate weekStart) {
        lock.readLock().lock();
        try {
            logger.debug("Calculating total deductions for driver {} week {}", driverId, weekStart);
            List<OtherAdjustment> list = adjustments.getOrDefault(key(driverId, weekStart), Collections.emptyList());
            BigDecimal total = list.stream()
                .filter(adj -> "Deduction".equals(adj.category) && 
                              (adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE || 
                               adj.status == OtherAdjustment.AdjustmentStatus.APPROVED))
                .map(adj -> adj.amount.abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            logger.debug("Total deductions for driver {} week {}: ${}", driverId, weekStart, total);
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Backward compatibility
    public double getTotalDeductions(int driverId, LocalDate weekStart) {
        return getTotalDeductionsBD(driverId, weekStart).doubleValue();
    }
    
    /**
     * Get fuel deductions specifically
     */
    public BigDecimal getFuelDeductionsBD(int driverId, LocalDate weekStart) {
        lock.readLock().lock();
        try {
            logger.debug("Calculating fuel deductions for driver {} week {}", driverId, weekStart);
            List<OtherAdjustment> list = adjustments.getOrDefault(key(driverId, weekStart), Collections.emptyList());
            BigDecimal total = list.stream()
                .filter(adj -> "Deduction".equals(adj.category) && 
                              "Fuel".equals(adj.type) &&
                              (adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE || 
                               adj.status == OtherAdjustment.AdjustmentStatus.APPROVED))
                .map(adj -> adj.amount.abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            logger.debug("Fuel deductions for driver {} week {}: ${}", driverId, weekStart, total);
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public double getFuelDeductions(int driverId, LocalDate weekStart) {
        return getFuelDeductionsBD(driverId, weekStart).doubleValue();
    }

    /**
     * Get total reimbursements with BigDecimal precision
     */
    public BigDecimal getTotalReimbursementsBD(int driverId, LocalDate weekStart) {
        lock.readLock().lock();
        try {
            logger.debug("Calculating total reimbursements for driver {} week {}", driverId, weekStart);
            List<OtherAdjustment> list = adjustments.getOrDefault(key(driverId, weekStart), Collections.emptyList());
            BigDecimal total = list.stream()
                .filter(adj -> "Reimbursement".equals(adj.category) && 
                              (adj.type == null || !adj.type.startsWith("Load Bonus:")) &&
                              (adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE || 
                               adj.status == OtherAdjustment.AdjustmentStatus.APPROVED))
                .map(adj -> adj.amount.abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            logger.debug("Total reimbursements for driver {} week {}: ${}", driverId, weekStart, total);
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Backward compatibility
    public double getTotalReimbursements(int driverId, LocalDate weekStart) {
        return getTotalReimbursementsBD(driverId, weekStart).doubleValue();
    }

    /**
     * Get bonus for load with BigDecimal precision
     */
    public BigDecimal getBonusForLoadBD(int driverId, LocalDate weekStart, String loadNumber) {
        lock.readLock().lock();
        try {
            List<OtherAdjustment> list = adjustments.getOrDefault(key(driverId, weekStart), Collections.emptyList());
            BigDecimal bonus = list.stream()
                .filter(adj -> "Reimbursement".equals(adj.category) &&
                              adj.type != null &&
                              adj.type.startsWith("Load Bonus:") &&
                              loadNumber != null &&
                              loadNumber.equals(adj.loadNumber) &&
                              (adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE || 
                               adj.status == OtherAdjustment.AdjustmentStatus.APPROVED))
                .map(adj -> adj.amount.abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (bonus.compareTo(BigDecimal.ZERO) > 0) {
                logger.info("Bonus found for driver {} load {}: ${}", driverId, loadNumber, bonus);
            }
            return bonus;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Backward compatibility
    public double getBonusForLoad(int driverId, LocalDate weekStart, String loadNumber) {
        return getBonusForLoadBD(driverId, weekStart, loadNumber).doubleValue();
    }

    /**
     * Get summary statistics for an employee
     */
    public AdjustmentSummary getEmployeeSummary(int driverId, LocalDate startDate, LocalDate endDate) {
        lock.readLock().lock();
        try {
            BigDecimal totalDeductions = BigDecimal.ZERO;
            BigDecimal totalReimbursements = BigDecimal.ZERO;
            BigDecimal totalBonuses = BigDecimal.ZERO;
            BigDecimal totalFuelDeductions = BigDecimal.ZERO;
            long adjustmentCount = 0;
            
            for (Map.Entry<String, List<OtherAdjustment>> entry : adjustments.entrySet()) {
                if (entry.getKey().startsWith(driverId + "_")) {
                    for (OtherAdjustment adj : entry.getValue()) {
                        if ((adj.status == OtherAdjustment.AdjustmentStatus.ACTIVE || 
							 adj.status == OtherAdjustment.AdjustmentStatus.APPROVED) &&
                            !adj.weekStart.isBefore(startDate) && 
                            !adj.weekStart.isAfter(endDate)) {
                            
                            adjustmentCount++;
                            if ("Deduction".equals(adj.category)) {
                                totalDeductions = totalDeductions.add(adj.amount);
                                if ("Fuel".equals(adj.type)) {
                                    totalFuelDeductions = totalFuelDeductions.add(adj.amount);
                                }
                            } else if ("Reimbursement".equals(adj.category)) {
                                if (adj.type != null && adj.type.startsWith("Load Bonus:")) {
                                    totalBonuses = totalBonuses.add(adj.amount);
                                } else {
                                    totalReimbursements = totalReimbursements.add(adj.amount);
                                }
                            }
                        }
                    }
                }
            }
            
            return new AdjustmentSummary(driverId, totalDeductions, totalReimbursements, 
                                       totalBonuses, totalFuelDeductions, adjustmentCount);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get audit trail
     */
    public List<AuditEntry> getAuditTrail(String employeeId, LocalDate startDate, LocalDate endDate) {
        lock.readLock().lock();
        try {
            return auditTrail.values().stream()
                .filter(e -> (employeeId == null || e.employeeId.equals(employeeId)))
                .filter(e -> (startDate == null || !e.timestamp.toLocalDate().isBefore(startDate)))
                .filter(e -> (endDate == null || !e.timestamp.toLocalDate().isAfter(endDate)))
                .sorted(Comparator.comparing((AuditEntry e) -> e.timestamp).reversed())
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // Helper methods
    
    private synchronized int getNextId() {
        return nextId++;
    }
    
    private void initializeNextId() {
        int maxId = 0;
        for (List<OtherAdjustment> list : adjustments.values()) {
            for (OtherAdjustment adj : list) {
                if (adj.id > maxId) {
                    maxId = adj.id;
                }
            }
        }
        nextId = maxId + 1;
        logger.info("Initialized nextId to {}", nextId);
    }

    private ValidationResult validateAdjustments(List<OtherAdjustment> adjustments) {
        for (OtherAdjustment adj : adjustments) {
            if (adj.amount == null || adj.amount.compareTo(BigDecimal.ZERO) <= 0) {
                return new ValidationResult(false, "All adjustments must have positive amounts");
            }
            if (adj.category == null || (!adj.category.equals("Deduction") && !adj.category.equals("Reimbursement"))) {
                return new ValidationResult(false, "Category must be 'Deduction' or 'Reimbursement'");
            }
            if (adj.type == null || adj.type.trim().isEmpty()) {
                return new ValidationResult(false, "Adjustment type is required");
            }
        }
        return new ValidationResult(true, null);
    }

    private void addAuditEntry(String action, String employeeId, String details, String performedBy) {
        AuditEntry entry = new AuditEntry(action, employeeId, details, performedBy);
        auditTrail.put(entry.id, entry);
        // Keep audit trail size manageable
        if (auditTrail.size() > 10000) {
            // Remove oldest entries
            List<String> oldestKeys = auditTrail.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((a, b) -> a.timestamp.compareTo(b.timestamp)))
                .limit(1000)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            oldestKeys.forEach(auditTrail::remove);
        }
    }

    private void save() {
        if (!dataModified) {
            return;
        }
        
        try {
            // Create backup first
            File mainFile = new File(ADJ_DATA_FILE);
            if (mainFile.exists()) {
                File backupFile = new File(BACKUP_DATA_FILE);
                Files.copy(mainFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Save to temp file first
            File tempFile = new File(ADJ_DATA_FILE + ".tmp");
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tempFile)))) {
                oos.writeObject(this);
            }
            
            // Atomic rename with error checking
            if (!tempFile.renameTo(mainFile)) {
                // If rename fails, try copy and delete as fallback
                try {
                    Files.move(tempFile.toPath(), mainFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException moveException) {
                    // Final fallback: copy then delete
                    Files.copy(tempFile.toPath(), mainFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    tempFile.delete();
                }
            }
            dataModified = false;
            
            logger.debug("Adjustments data saved successfully");
            
        } catch (IOException e) {
            logger.error("Error saving adjustments data", e);
            throw new RuntimeException("Failed to save adjustments data", e);
        }
    }

    private void loadData() {
        File mainFile = new File(ADJ_DATA_FILE);
        File backupFile = new File(BACKUP_DATA_FILE);
        
        // Try main file first
        PayrollOtherAdjustments loaded = loadFromFile(mainFile);
        if (loaded != null) {
            copyFrom(loaded);
            return;
        }
        
        // Try backup file
        logger.warn("Main data file corrupted or missing, trying backup");
        loaded = loadFromFile(backupFile);
        if (loaded != null) {
            copyFrom(loaded);
            return;
        }
        
        // No data files found
        logger.info("No valid data files found, starting with empty data");
    }

    private PayrollOtherAdjustments loadFromFile(File file) {
        if (!file.exists()) {
            return null;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            Object obj = ois.readObject();
            if (obj instanceof PayrollOtherAdjustments) {
                logger.info("Loaded adjustments data from {}", file.getName());
                return (PayrollOtherAdjustments) obj;
            }
        } catch (Exception e) {
            logger.error("Error loading from {}: {}", file.getName(), e.getMessage());
        }
        
        return null;
    }
    
    private void copyFrom(PayrollOtherAdjustments other) {
        this.adjustments.clear();
        this.adjustments.putAll(other.adjustments);
        this.auditTrail.clear();
        this.auditTrail.putAll(other.auditTrail);
        this.dataModified = false;
    }

    // Result classes
    
    public static class OperationResult {
        private final boolean success;
        private final String message;
        
        public OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class AdjustmentSummary {
        public final int driverId;
        public final BigDecimal totalDeductions;
        public final BigDecimal totalReimbursements;
        public final BigDecimal totalBonuses;
        public final BigDecimal totalFuelDeductions;
        public final long adjustmentCount;
        
        public AdjustmentSummary(int driverId, BigDecimal totalDeductions, BigDecimal totalReimbursements,
                               BigDecimal totalBonuses, BigDecimal totalFuelDeductions, long adjustmentCount) {
            this.driverId = driverId;
            this.totalDeductions = totalDeductions;
            this.totalReimbursements = totalReimbursements;
            this.totalBonuses = totalBonuses;
            this.totalFuelDeductions = totalFuelDeductions;
            this.adjustmentCount = adjustmentCount;
        }
        
        public BigDecimal getNetAdjustment() {
            return totalReimbursements.add(totalBonuses).subtract(totalDeductions);
        }
    }
}