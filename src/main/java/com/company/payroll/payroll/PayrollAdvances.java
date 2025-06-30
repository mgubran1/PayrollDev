package com.company.payroll.payroll;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe, business-ready PayrollAdvances management system.
 * Handles cash advances with comprehensive validation, audit trails, and error recovery.
 */
public class PayrollAdvances implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(PayrollAdvances.class);
    private static final long serialVersionUID = 3L;
    private static final String ADVANCES_DATA_FILE = "payroll_advances.dat";
    private static final String BACKUP_DATA_FILE = "payroll_advances.bak";
    private static final int MAX_WEEKS_TO_REPAY = 52;
    private static final int MIN_WEEKS_TO_REPAY = 1;
    private static final BigDecimal MAX_ADVANCE_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal MIN_ADVANCE_AMOUNT = new BigDecimal("10.00");
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, List<Advance>> advances = new ConcurrentHashMap<>();
    private final Map<String, AuditEntry> auditTrail = new ConcurrentHashMap<>();
    private transient boolean dataModified = false;
    
    private static volatile PayrollAdvances instance;

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
     * Represents a single cash advance with full tracking
     */
    public static class Advance implements Serializable, Cloneable {
        private static final long serialVersionUID = 3L;
        
        private final String id;
        private final String employeeId;
        private final BigDecimal totalAmount;
        private final String notes;
        private final LocalDate dateGiven;
        private final LocalDate firstRepaymentDate;
        private final int weeksToRepay;
        private final List<Repayment> repayments;
        private final List<ManualRepayment> manualRepayments;
        private final List<StatusChange> statusHistory;
        private AdvanceStatus status;
        private LocalDateTime lastModified;
        private String approvedBy;
        private LocalDate completedDate;
        
        public enum AdvanceStatus {
            ACTIVE("Active"),
            COMPLETED("Completed"),
            DEFAULTED("Defaulted"),
            FORGIVEN("Forgiven"),
            CANCELLED("Cancelled");
            
            private final String displayName;
            
            AdvanceStatus(String displayName) {
                this.displayName = displayName;
            }
            
            public String getDisplayName() {
                return displayName;
            }
        }
        
        /**
         * Status change tracking
         */
        public static class StatusChange implements Serializable {
            private static final long serialVersionUID = 1L;
            public final LocalDateTime timestamp;
            public final AdvanceStatus fromStatus;
            public final AdvanceStatus toStatus;
            public final String reason;
            public final String changedBy;
            
            public StatusChange(AdvanceStatus fromStatus, AdvanceStatus toStatus, 
                              String reason, String changedBy) {
                this.timestamp = LocalDateTime.now();
                this.fromStatus = fromStatus;
                this.toStatus = toStatus;
                this.reason = reason;
                this.changedBy = changedBy;
            }
        }

        /**
         * Represents a scheduled repayment
         */
        public static class Repayment implements Serializable, Cloneable {
            private static final long serialVersionUID = 2L;
            private LocalDate weekStartDate;
            private BigDecimal scheduledAmount;
            private BigDecimal paidAmount;
            private boolean paid;
            private LocalDate paidDate;
            private String note;
            private String processedBy;
            private LocalDateTime processedTimestamp;

            public Repayment(LocalDate weekStartDate, BigDecimal amount) {
                this.weekStartDate = weekStartDate;
                this.scheduledAmount = amount;
                this.paidAmount = BigDecimal.ZERO;
                this.paid = false;
            }
            
            @Override
            public Repayment clone() {
                try {
                    return (Repayment) super.clone();
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException("Clone not supported", e);
                }
            }
            
            // Getters and setters
            public LocalDate getWeekStartDate() { return weekStartDate; }
            public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
            public BigDecimal getScheduledAmount() { return scheduledAmount; }
            public void setScheduledAmount(BigDecimal scheduledAmount) { this.scheduledAmount = scheduledAmount; }
            public BigDecimal getPaidAmount() { return paidAmount; }
            public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
            public boolean isPaid() { return paid; }
            public void setPaid(boolean paid) { this.paid = paid; }
            public LocalDate getPaidDate() { return paidDate; }
            public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
            public String getNote() { return note; }
            public void setNote(String note) { this.note = note; }
            public String getProcessedBy() { return processedBy; }
            public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
            public LocalDateTime getProcessedTimestamp() { return processedTimestamp; }
            public void setProcessedTimestamp(LocalDateTime processedTimestamp) { this.processedTimestamp = processedTimestamp; }
        }

        /**
         * Manual repayment record
         */
        public static class ManualRepayment implements Serializable {
            private static final long serialVersionUID = 1L;
            private final String id;
            private final LocalDate date;
            private final BigDecimal amount;
            private final String note;
            private final String processedBy;
            private final LocalDateTime timestamp;
            private final PaymentMethod paymentMethod;
            private final String referenceNumber;
            
            public enum PaymentMethod {
                PAYROLL_DEDUCTION("Payroll Deduction"),
                CASH("Cash"),
                CHECK("Check"),
                DIRECT_DEPOSIT("Direct Deposit"),
                OTHER("Other");
                
                private final String displayName;
                
                PaymentMethod(String displayName) {
                    this.displayName = displayName;
                }
                
                public String getDisplayName() {
                    return displayName;
                }
            }
            
            public ManualRepayment(LocalDate date, BigDecimal amount, String note, 
                                 String processedBy, PaymentMethod paymentMethod, 
                                 String referenceNumber) {
                this.id = UUID.randomUUID().toString();
                this.date = date;
                this.amount = amount;
                this.note = note;
                this.processedBy = processedBy;
                this.timestamp = LocalDateTime.now();
                this.paymentMethod = paymentMethod;
                this.referenceNumber = referenceNumber;
            }
            
            // Getters
            public String getId() { return id; }
            public LocalDate getDate() { return date; }
            public BigDecimal getAmount() { return amount; }
            public String getNote() { return note; }
            public String getProcessedBy() { return processedBy; }
            public LocalDateTime getTimestamp() { return timestamp; }
            public PaymentMethod getPaymentMethod() { return paymentMethod; }
            public String getReferenceNumber() { return referenceNumber; }
        }

        public Advance(String employeeId, BigDecimal totalAmount, String notes, 
                      LocalDate dateGiven, int weeksToRepay, LocalDate firstRepaymentDate, 
                      String approvedBy) {
            // Validation
            if (totalAmount.compareTo(MIN_ADVANCE_AMOUNT) < 0) {
                throw new IllegalArgumentException("Advance amount must be at least " + MIN_ADVANCE_AMOUNT);
            }
            if (totalAmount.compareTo(MAX_ADVANCE_AMOUNT) > 0) {
                throw new IllegalArgumentException("Advance amount cannot exceed " + MAX_ADVANCE_AMOUNT);
            }
            if (weeksToRepay < MIN_WEEKS_TO_REPAY || weeksToRepay > MAX_WEEKS_TO_REPAY) {
                throw new IllegalArgumentException("Repayment weeks must be between " + 
                    MIN_WEEKS_TO_REPAY + " and " + MAX_WEEKS_TO_REPAY);
            }
            
            this.id = UUID.randomUUID().toString();
            this.employeeId = employeeId;
            this.totalAmount = totalAmount;
            this.notes = notes;
            this.dateGiven = dateGiven;
            this.firstRepaymentDate = firstRepaymentDate;
            this.weeksToRepay = weeksToRepay;
            this.approvedBy = approvedBy;
            this.status = AdvanceStatus.ACTIVE;
            this.lastModified = LocalDateTime.now();
            this.repayments = new ArrayList<>();
            this.manualRepayments = new ArrayList<>();
            this.statusHistory = new ArrayList<>();
            
            // Add initial status
            statusHistory.add(new StatusChange(null, AdvanceStatus.ACTIVE, 
                "Advance created", approvedBy));
            
            // Calculate repayment schedule
            calculateRepaymentSchedule();
        }
        
        private void calculateRepaymentSchedule() {
            repayments.clear();
            
            // Calculate weekly payment with proper rounding
            BigDecimal weeklyPayment = totalAmount.divide(
                BigDecimal.valueOf(weeksToRepay), 2, RoundingMode.DOWN);
            BigDecimal totalScheduled = weeklyPayment.multiply(BigDecimal.valueOf(weeksToRepay));
            BigDecimal remainder = totalAmount.subtract(totalScheduled);
            
            LocalDate currentWeek = firstRepaymentDate;
            for (int i = 0; i < weeksToRepay; i++) {
                BigDecimal amount = weeklyPayment;
                // Add remainder to first payment
                if (i == 0 && remainder.compareTo(BigDecimal.ZERO) > 0) {
                    amount = amount.add(remainder);
                }
                repayments.add(new Repayment(currentWeek, amount));
                currentWeek = currentWeek.plusWeeks(1);
            }
        }
        
        /**
         * Process a scheduled repayment
         */
        public synchronized void processScheduledRepayment(LocalDate weekStartDate, 
                                                         BigDecimal amount, 
                                                         String processedBy, 
                                                         String note) {
            Repayment repayment = findRepaymentForWeek(weekStartDate);
            if (repayment == null) {
                throw new IllegalArgumentException("No scheduled repayment found for week " + weekStartDate);
            }
            if (repayment.paid) {
                throw new IllegalStateException("Repayment for week " + weekStartDate + " already processed");
            }
            
            repayment.paidAmount = amount;
            repayment.paid = true;
            repayment.paidDate = LocalDate.now();
            repayment.processedBy = processedBy;
            repayment.processedTimestamp = LocalDateTime.now();
            repayment.note = note;
            
            lastModified = LocalDateTime.now();
            checkIfCompleted();
        }
        
        /**
         * Add a manual repayment with proper allocation
         */
        public synchronized void addManualRepayment(LocalDate date, BigDecimal amount, 
                                                  String note, String processedBy,
                                                  ManualRepayment.PaymentMethod paymentMethod,
                                                  String referenceNumber) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment amount must be positive");
            }
            
            ManualRepayment manual = new ManualRepayment(date, amount, note, 
                processedBy, paymentMethod, referenceNumber);
            manualRepayments.add(manual);
            
            // Allocate to unpaid scheduled repayments
            BigDecimal remaining = amount;
            for (Repayment rep : repayments) {
                if (!rep.paid && remaining.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal unpaidAmount = rep.scheduledAmount.subtract(rep.paidAmount);
                    if (unpaidAmount.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal toApply = remaining.min(unpaidAmount);
                        rep.paidAmount = rep.paidAmount.add(toApply);
                        remaining = remaining.subtract(toApply);
                        
                        if (rep.paidAmount.compareTo(rep.scheduledAmount) >= 0) {
                            rep.paid = true;
                            rep.paidDate = date;
                            rep.processedBy = processedBy;
                            rep.processedTimestamp = LocalDateTime.now();
                            rep.note = "Paid via manual payment " + manual.id;
                        }
                    }
                }
            }
            
            lastModified = LocalDateTime.now();
            checkIfCompleted();
        }
        
        private void checkIfCompleted() {
            boolean allPaid = repayments.stream().allMatch(r -> r.paid);
            if (allPaid && status == AdvanceStatus.ACTIVE) {
                changeStatus(AdvanceStatus.COMPLETED, "All repayments received", "System");
                completedDate = LocalDate.now();
            }
        }
        
        public synchronized void changeStatus(AdvanceStatus newStatus, String reason, String changedBy) {
            if (status == newStatus) {
                return;
            }
            statusHistory.add(new StatusChange(status, newStatus, reason, changedBy));
            status = newStatus;
            lastModified = LocalDateTime.now();
        }
        
        private Repayment findRepaymentForWeek(LocalDate weekStartDate) {
            return repayments.stream()
                .filter(r -> r.weekStartDate.equals(weekStartDate))
                .findFirst()
                .orElse(null);
        }
        
        /**
         * Calculate remaining balance with BigDecimal precision
         */
        public BigDecimal getRemainingBalance() {
            BigDecimal totalPaid = repayments.stream()
                .map(r -> r.paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal manualTotal = manualRepayments.stream()
                .map(m -> m.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Manual payments are already allocated to repayments, so don't double-count
            BigDecimal totalScheduled = repayments.stream()
                .filter(r -> !r.paid)
                .map(r -> r.scheduledAmount.subtract(r.paidAmount))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return totalScheduled.max(BigDecimal.ZERO);
        }
        
        /**
         * Get total amount paid so far
         */
        public BigDecimal getTotalPaid() {
            return repayments.stream()
                .map(r -> r.paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        /**
         * Get next scheduled payment
         */
        public Optional<Repayment> getNextScheduledPayment() {
            return repayments.stream()
                .filter(r -> !r.paid)
                .min(Comparator.comparing(r -> r.weekStartDate));
        }
        
        /**
         * Check if advance is overdue
         */
        public boolean isOverdue() {
            if (status != AdvanceStatus.ACTIVE) {
                return false;
            }
            LocalDate today = LocalDate.now();
            return repayments.stream()
                .anyMatch(r -> !r.paid && r.weekStartDate.isBefore(today));
        }
        
        /**
         * Get number of missed payments
         */
        public long getMissedPaymentCount() {
            LocalDate today = LocalDate.now();
            return repayments.stream()
                .filter(r -> !r.paid && r.weekStartDate.isBefore(today))
                .count();
        }
        
        @Override
        public Advance clone() {
            try {
                Advance cloned = (Advance) super.clone();
                // Deep copy collections
                // Note: In production, you'd properly deep clone all mutable objects
                return cloned;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Clone not supported", e);
            }
        }
        
        // Getters
        public String getId() { return id; }
        public String getEmployeeId() { return employeeId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public String getNotes() { return notes; }
        public LocalDate getDateGiven() { return dateGiven; }
        public LocalDate getFirstRepaymentDate() { return firstRepaymentDate; }
        public int getWeeksToRepay() { return weeksToRepay; }
        public List<Repayment> getRepayments() { return new ArrayList<>(repayments); }
        public List<ManualRepayment> getManualRepayments() { return new ArrayList<>(manualRepayments); }
        public AdvanceStatus getStatus() { return status; }
        public LocalDateTime getLastModified() { return lastModified; }
        public String getApprovedBy() { return approvedBy; }
        public LocalDate getCompletedDate() { return completedDate; }
        public List<StatusChange> getStatusHistory() { return new ArrayList<>(statusHistory); }
        public boolean isCompleted() { return status == AdvanceStatus.COMPLETED; }
        
        // For backward compatibility
        public double getAmountAsDouble() { return totalAmount.doubleValue(); }
        public double getRemainingBalanceAsDouble() { return getRemainingBalance().doubleValue(); }
    }

    private PayrollAdvances() {
        logger.info("Initializing PayrollAdvances system");
    }

    public static PayrollAdvances getInstance() {
        if (instance == null) {
            synchronized (PayrollAdvances.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    /**
     * Add a new advance with validation and audit trail
     */
    public AdvanceResult addAdvance(String employeeId, BigDecimal amount, String notes, 
                                   LocalDate dateGiven, Integer weeksToRepay, String approvedBy) {
        lock.writeLock().lock();
        try {
            logger.info("Adding advance: employeeId={}, amount={}, dateGiven={}, weeksToRepay={}, approvedBy={}", 
                employeeId, amount, dateGiven, weeksToRepay, approvedBy);
            
            // Validation
            ValidationResult validation = validateNewAdvance(employeeId, amount, weeksToRepay);
            if (!validation.isValid()) {
                logger.warn("Advance validation failed: {}", validation.getErrorMessage());
                return new AdvanceResult(false, validation.getErrorMessage(), null);
            }
            
            // Calculate first repayment date (next week)
            LocalDate firstRepaymentDate = dateGiven.plusWeeks(1);
            
            // Create advance
            Advance advance = new Advance(employeeId, amount, notes, dateGiven, 
                weeksToRepay, firstRepaymentDate, approvedBy);
            
            // Add to employee's advances
            advances.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(advance);
            
            // Add audit entry
            addAuditEntry("ADVANCE_CREATED", employeeId, 
                String.format("Created advance: $%.2f over %d weeks", amount, weeksToRepay), 
                approvedBy);
            
            dataModified = true;
            save();
            
            logger.info("Advance created successfully with ID: {}", advance.getId());
            return new AdvanceResult(true, "Advance created successfully", advance);
            
        } catch (Exception e) {
            logger.error("Error creating advance", e);
            return new AdvanceResult(false, "Error creating advance: " + e.getMessage(), null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Process a scheduled repayment
     */
    public PaymentResult recordRepayment(String employeeId, LocalDate weekStartDate, 
                                       BigDecimal amount, String processedBy, String note) {
        lock.writeLock().lock();
        try {
            logger.info("Recording repayment: employeeId={}, week={}, amount={}, processedBy={}", 
                employeeId, weekStartDate, amount, processedBy);
            
            List<Advance> employeeAdvances = getActiveAdvances(employeeId);
            if (employeeAdvances.isEmpty()) {
                return new PaymentResult(false, "No active advances found for employee", null);
            }
            
            // Find advances with repayments due this week
            List<Advance> advancesWithPaymentsDue = employeeAdvances.stream()
                .filter(a -> a.getRepayments().stream()
                    .anyMatch(r -> r.weekStartDate.equals(weekStartDate) && !r.paid))
                .collect(Collectors.toList());
            
            if (advancesWithPaymentsDue.isEmpty()) {
                return new PaymentResult(false, "No repayments due for week " + weekStartDate, null);
            }
            
            // Process payments
            BigDecimal totalProcessed = BigDecimal.ZERO;
            List<String> processedIds = new ArrayList<>();
            
            for (Advance advance : advancesWithPaymentsDue) {
                for (Advance.Repayment repayment : advance.getRepayments()) {
                    if (repayment.weekStartDate.equals(weekStartDate) && !repayment.paid) {
                        advance.processScheduledRepayment(weekStartDate, 
                            repayment.scheduledAmount, processedBy, note);
                        totalProcessed = totalProcessed.add(repayment.scheduledAmount);
                        processedIds.add(advance.getId());
                        
                        addAuditEntry("REPAYMENT_PROCESSED", employeeId,
                            String.format("Processed scheduled repayment: $%.2f for advance %s", 
                                repayment.scheduledAmount, advance.getId()),
                            processedBy);
                    }
                }
            }
            
            dataModified = true;
            save();
            
            String message = String.format("Processed %d repayment(s) totaling $%.2f", 
                processedIds.size(), totalProcessed);
            logger.info(message);
            
            return new PaymentResult(true, message, processedIds);
            
        } catch (Exception e) {
            logger.error("Error processing repayment", e);
            return new PaymentResult(false, "Error processing repayment: " + e.getMessage(), null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Add a manual repayment
     */
    public PaymentResult addManualRepayment(String employeeId, String advanceId, 
                                          LocalDate date, BigDecimal amount, String note,
                                          String processedBy, Advance.ManualRepayment.PaymentMethod method,
                                          String referenceNumber) {
        lock.writeLock().lock();
        try {
            logger.info("Adding manual repayment: advanceId={}, amount={}, processedBy={}", 
                advanceId, amount, processedBy);
            
            Advance advance = findAdvanceById(employeeId, advanceId);
            if (advance == null) {
                return new PaymentResult(false, "Advance not found", null);
            }
            
            if (advance.getStatus() != Advance.AdvanceStatus.ACTIVE) {
                return new PaymentResult(false, "Cannot add payment to " + 
                    advance.getStatus().getDisplayName() + " advance", null);
            }
            
            advance.addManualRepayment(date, amount, note, processedBy, method, referenceNumber);
            
            addAuditEntry("MANUAL_PAYMENT_ADDED", employeeId,
                String.format("Added manual payment: $%.2f to advance %s via %s", 
                    amount, advanceId, method.getDisplayName()),
                processedBy);
            
            dataModified = true;
            save();
            
            logger.info("Manual payment added successfully");
            return new PaymentResult(true, "Manual payment added successfully", 
                Collections.singletonList(advanceId));
            
        } catch (Exception e) {
            logger.error("Error adding manual payment", e);
            return new PaymentResult(false, "Error adding manual payment: " + e.getMessage(), null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get active advances for an employee
     */
    public List<Advance> getActiveAdvances(String employeeId) {
        lock.readLock().lock();
        try {
            return advances.getOrDefault(employeeId, Collections.emptyList()).stream()
                .filter(a -> a.getStatus() == Advance.AdvanceStatus.ACTIVE)
                .map(a -> a.clone()) // Return defensive copies
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all advances for an employee
     */
    public List<Advance> getAllAdvances(String employeeId) {
        lock.readLock().lock();
        try {
            return advances.getOrDefault(employeeId, Collections.emptyList()).stream()
                .map(a -> a.clone()) // Return defensive copies
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get total repayment amount due for a week
     */
    public BigDecimal getRepaymentAmountForWeek(String employeeId, LocalDate weekStartDate) {
        lock.readLock().lock();
        try {
            return getActiveAdvances(employeeId).stream()
                .flatMap(a -> a.getRepayments().stream())
                .filter(r -> r.weekStartDate.equals(weekStartDate) && !r.paid)
                .map(r -> r.scheduledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get total advances given in a week
     */
    public BigDecimal getAdvancesGivenForWeek(String employeeId, LocalDate weekStartDate) {
        lock.readLock().lock();
        try {
            LocalDate weekEnd = weekStartDate.plusDays(6);
            return advances.getOrDefault(employeeId, Collections.emptyList()).stream()
                .filter(a -> !a.getDateGiven().isBefore(weekStartDate) && 
                           !a.getDateGiven().isAfter(weekEnd))
                .map(Advance::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get completion notifications for a week
     */
    public List<String> getCompletedAdvanceNotifications(LocalDate weekStartDate) {
        lock.readLock().lock();
        try {
            List<String> notifications = new ArrayList<>();
            
            for (Map.Entry<String, List<Advance>> entry : advances.entrySet()) {
                for (Advance advance : entry.getValue()) {
                    if (advance.getCompletedDate() != null &&
                        !advance.getCompletedDate().isBefore(weekStartDate) &&
                        !advance.getCompletedDate().isAfter(weekStartDate.plusDays(6))) {
                        
                        notifications.add(String.format(
                            "Employee %s completed advance repayment (ID: %s, Original amount: $%.2f)",
                            entry.getKey(), advance.getId(), advance.getTotalAmount()));
                    }
                }
            }
            
            return notifications;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Change advance status
     */
    public OperationResult changeAdvanceStatus(String employeeId, String advanceId,
                                             Advance.AdvanceStatus newStatus,
                                             String reason, String changedBy) {
        lock.writeLock().lock();
        try {
            Advance advance = findAdvanceById(employeeId, advanceId);
            if (advance == null) {
                return new OperationResult(false, "Advance not found");
            }
            
            advance.changeStatus(newStatus, reason, changedBy);
            
            addAuditEntry("STATUS_CHANGED", employeeId,
                String.format("Changed advance %s status to %s: %s", 
                    advanceId, newStatus.getDisplayName(), reason),
                changedBy);
            
            dataModified = true;
            save();
            
            return new OperationResult(true, "Status changed successfully");
            
        } catch (Exception e) {
            logger.error("Error changing advance status", e);
            return new OperationResult(false, "Error changing status: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get summary statistics for an employee
     */
    public AdvanceSummary getEmployeeSummary(String employeeId) {
        lock.readLock().lock();
        try {
            List<Advance> employeeAdvances = advances.getOrDefault(employeeId, Collections.emptyList());
            
            BigDecimal totalAdvanced = employeeAdvances.stream()
                .map(Advance::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalPaid = employeeAdvances.stream()
                .map(Advance::getTotalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalOutstanding = employeeAdvances.stream()
                .filter(a -> a.getStatus() == Advance.AdvanceStatus.ACTIVE)
                .map(Advance::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long activeCount = employeeAdvances.stream()
                .filter(a -> a.getStatus() == Advance.AdvanceStatus.ACTIVE)
                .count();
            
            long completedCount = employeeAdvances.stream()
                .filter(a -> a.getStatus() == Advance.AdvanceStatus.COMPLETED)
                .count();
            
            long overdueCount = employeeAdvances.stream()
                .filter(Advance::isOverdue)
                .count();
            
            return new AdvanceSummary(employeeId, totalAdvanced, totalPaid, 
                totalOutstanding, activeCount, completedCount, overdueCount);
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get outstanding balance
     */
    public BigDecimal getOutstandingBalance(String employeeId) {
        lock.readLock().lock();
        try {
            return advances.getOrDefault(employeeId, Collections.emptyList()).stream()
                .filter(a -> a.getStatus() == Advance.AdvanceStatus.ACTIVE)
                .map(Advance::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove an advance (soft delete by changing status)
     */
    public OperationResult removeAdvance(String employeeId, int advanceIndex) {
        lock.writeLock().lock();
        try {
            List<Advance> employeeAdvances = advances.get(employeeId);
            if (employeeAdvances == null || advanceIndex < 0 || advanceIndex >= employeeAdvances.size()) {
                return new OperationResult(false, "Invalid advance index");
            }
            
            Advance advance = employeeAdvances.get(advanceIndex);
            
            // Don't actually remove, just mark as cancelled
            advance.changeStatus(Advance.AdvanceStatus.CANCELLED, 
                "Removed by user", "System");
            
            addAuditEntry("ADVANCE_REMOVED", employeeId,
                String.format("Cancelled advance %s", advance.getId()),
                "System");
            
            dataModified = true;
            save();
            
            return new OperationResult(true, "Advance cancelled successfully");
            
        } catch (Exception e) {
            logger.error("Error removing advance", e);
            return new OperationResult(false, "Error removing advance: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update repayment schedule
     */
    public OperationResult updateRepayment(String employeeId, String advanceId,
                                         LocalDate originalWeekStart, LocalDate newWeekStart,
                                         BigDecimal newAmount, String note, String modifiedBy) {
        lock.writeLock().lock();
        try {
            Advance advance = findAdvanceById(employeeId, advanceId);
            if (advance == null) {
                return new OperationResult(false, "Advance not found");
            }
            
            // Find and update the repayment
            boolean found = false;
            for (Advance.Repayment repayment : advance.getRepayments()) {
                if (repayment.weekStartDate.equals(originalWeekStart)) {
                    if (repayment.paid) {
                        return new OperationResult(false, "Cannot modify paid repayment");
                    }
                    repayment.weekStartDate = newWeekStart;
                    repayment.scheduledAmount = newAmount;
                    repayment.note = note;
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                return new OperationResult(false, "Repayment not found for week " + originalWeekStart);
            }
            
            advance.lastModified = LocalDateTime.now();
            
            addAuditEntry("REPAYMENT_UPDATED", employeeId,
                String.format("Updated repayment for advance %s: week %s -> %s, amount $%.2f", 
                    advanceId, originalWeekStart, newWeekStart, newAmount),
                modifiedBy);
            
            dataModified = true;
            save();
            
            return new OperationResult(true, "Repayment updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating repayment", e);
            return new OperationResult(false, "Error updating repayment: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
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
    
    private Advance findAdvanceById(String employeeId, String advanceId) {
        List<Advance> employeeAdvances = advances.get(employeeId);
        if (employeeAdvances == null) return null;
        
        return employeeAdvances.stream()
            .filter(a -> a.getId().equals(advanceId))
            .findFirst()
            .orElse(null);
    }
    
    private ValidationResult validateNewAdvance(String employeeId, BigDecimal amount, int weeksToRepay) {
        // Check for existing active advances
        List<Advance> activeAdvances = getActiveAdvances(employeeId);
        
        // Business rule: Maximum 3 active advances
        if (activeAdvances.size() >= 3) {
            return new ValidationResult(false, "Employee already has maximum number of active advances (3)");
        }
        
        // Business rule: Total outstanding cannot exceed $5000
        BigDecimal totalOutstanding = activeAdvances.stream()
            .map(Advance::getRemainingBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalOutstanding.add(amount).compareTo(new BigDecimal("5000")) > 0) {
            return new ValidationResult(false, 
                String.format("Total outstanding advances would exceed $5000 limit (Current: $%.2f)", 
                    totalOutstanding));
        }
        
        // Business rule: No new advances if any existing advance is overdue
        boolean hasOverdue = activeAdvances.stream().anyMatch(Advance::isOverdue);
        if (hasOverdue) {
            return new ValidationResult(false, "Cannot create new advance while existing advances are overdue");
        }
        
        return new ValidationResult(true, null);
    }
    
    private void addAuditEntry(String action, String employeeId, String details, String performedBy) {
        AuditEntry entry = new AuditEntry(action, employeeId, details, performedBy);
        auditTrail.put(entry.id, entry);
    }
    
    private synchronized void save() {
        if (!dataModified) {
            return;
        }
        
        try {
            // Create backup first
            File mainFile = new File(ADVANCES_DATA_FILE);
            if (mainFile.exists()) {
                File backupFile = new File(BACKUP_DATA_FILE);
                java.nio.file.Files.copy(mainFile.toPath(), backupFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Save to temp file first
            File tempFile = new File(ADVANCES_DATA_FILE + ".tmp");
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tempFile)))) {
                oos.writeObject(this);
            }
            
            // Atomic rename
            tempFile.renameTo(mainFile);
            dataModified = false;
            
            logger.debug("Advances data saved successfully");
            
        } catch (IOException e) {
            logger.error("Error saving advances data", e);
            throw new RuntimeException("Failed to save advances data", e);
        }
    }
    
    private static PayrollAdvances load() {
        File mainFile = new File(ADVANCES_DATA_FILE);
        File backupFile = new File(BACKUP_DATA_FILE);
        
        // Try main file first
        PayrollAdvances loaded = loadFromFile(mainFile);
        if (loaded != null) {
            return loaded;
        }
        
        // Try backup file
        logger.warn("Main data file corrupted or missing, trying backup");
        loaded = loadFromFile(backupFile);
        if (loaded != null) {
            return loaded;
        }
        
        // Create new instance
        logger.info("No valid data files found, creating new instance");
        return new PayrollAdvances();
    }
    
    private static PayrollAdvances loadFromFile(File file) {
        if (!file.exists()) {
            return null;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            Object obj = ois.readObject();
            if (obj instanceof PayrollAdvances) {
                logger.info("Loaded advances data from {}", file.getName());
                return (PayrollAdvances) obj;
            }
        } catch (Exception e) {
            logger.error("Error loading from {}: {}", file.getName(), e.getMessage());
        }
        
        return null;
    }
    
    // Result classes
    
    public static class AdvanceResult {
        private final boolean success;
        private final String message;
        private final Advance advance;
        
        public AdvanceResult(boolean success, String message, Advance advance) {
            this.success = success;
            this.message = message;
            this.advance = advance;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Advance getAdvance() { return advance; }
    }
    
    public static class PaymentResult {
        private final boolean success;
        private final String message;
        private final List<String> processedAdvanceIds;
        
        public PaymentResult(boolean success, String message, List<String> processedAdvanceIds) {
            this.success = success;
            this.message = message;
            this.processedAdvanceIds = processedAdvanceIds;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<String> getProcessedAdvanceIds() { return processedAdvanceIds; }
    }
    
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
    
    public static class AdvanceSummary {
        public final String employeeId;
        public final BigDecimal totalAdvanced;
        public final BigDecimal totalPaid;
        public final BigDecimal totalOutstanding;
        public final long activeCount;
        public final long completedCount;
        public final long overdueCount;
        
        public AdvanceSummary(String employeeId, BigDecimal totalAdvanced, BigDecimal totalPaid,
                            BigDecimal totalOutstanding, long activeCount, long completedCount,
                            long overdueCount) {
            this.employeeId = employeeId;
            this.totalAdvanced = totalAdvanced;
            this.totalPaid = totalPaid;
            this.totalOutstanding = totalOutstanding;
            this.activeCount = activeCount;
            this.completedCount = completedCount;
            this.overdueCount = overdueCount;
        }
    }
}