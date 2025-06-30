package com.company.payroll.expenses;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Enhanced CompanyExpense model that maintains backward compatibility while adding new features
 * for better expense tracking and categorization.
 */
public class CompanyExpense {
    private int id;
    private LocalDate expenseDate;
    private String description;
    private double amount;
    private String receiptNumber;
    private String receiptPath;
    
    // New enhanced fields
    private String category; // Fuel, Maintenance, Insurance, Payroll, Office, Other
    private String subcategory; // More specific categorization
    private String vendor;
    private String paymentMethod; // Cash, Credit Card, Check, ACH, Other
    private String paymentReference; // Check number, transaction ID, etc.
    private String expenseType; // Fixed, Variable, One-time
    private String department; // Operations, Admin, Maintenance, etc.
    private String approvedBy;
    private LocalDate approvalDate;
    private String status; // Pending, Approved, Rejected, Paid
    private boolean isRecurring;
    private String recurringFrequency; // Monthly, Quarterly, Annually
    private LocalDate nextDueDate;
    private String notes;
    private String attachedDocuments; // Comma-separated list of additional document paths
    private double taxAmount;
    private String taxCategory; // Deductible, Non-deductible, Partial
    private String accountingCode; // For integration with accounting software
    private String costCenter;
    private String project; // If expense is project-specific
    private String truckUnit; // If expense is truck-specific
    private String trailerNumber; // If expense is trailer-specific
    private String employeeId; // If expense is employee-specific
    
    // Budget tracking
    private String budgetCategory;
    private double budgetedAmount;
    private String budgetPeriod; // Monthly, Quarterly, Annually
    
    // Audit fields
    private LocalDateTime createdDate;
    private String createdBy;
    private LocalDateTime modifiedDate;
    private String modifiedBy;
    
    // Original constructor for backward compatibility
    public CompanyExpense(int id, LocalDate expenseDate, String description,
                          double amount, String receiptNumber, String receiptPath) {
        this.id = id;
        this.expenseDate = expenseDate;
        this.description = description;
        this.amount = amount;
        this.receiptNumber = receiptNumber;
        this.receiptPath = receiptPath;
        
        // Initialize new fields with defaults
        this.status = "Pending";
        this.isRecurring = false;
        this.taxAmount = 0.0;
        this.createdDate = LocalDateTime.now();
        this.createdBy = "mgubran1";
        this.modifiedDate = LocalDateTime.now();
        this.modifiedBy = "mgubran1";
    }
    
    // Enhanced constructor with category
    public CompanyExpense(int id, LocalDate expenseDate, String category, String description,
                          double amount, String receiptNumber, String receiptPath) {
        this(id, expenseDate, description, amount, receiptNumber, receiptPath);
        this.category = category;
    }
    
    // Full enhanced constructor
    public CompanyExpense(int id, LocalDate expenseDate, String category, String subcategory,
                          String description, double amount, String vendor, String paymentMethod,
                          String receiptNumber, String receiptPath, String status) {
        this(id, expenseDate, description, amount, receiptNumber, receiptPath);
        this.category = category;
        this.subcategory = subcategory;
        this.vendor = vendor;
        this.paymentMethod = paymentMethod;
        this.status = status;
    }
    
    // Computed properties
    public double getTotalAmount() {
        return amount + taxAmount;
    }
    
    public boolean isOverdue() {
        if (!"Pending".equals(status) || expenseDate == null) {
            return false;
        }
        return expenseDate.plusDays(30).isBefore(LocalDate.now());
    }
    
    public boolean isPaid() {
        return "Paid".equals(status);
    }
    
    public boolean isApproved() {
        return "Approved".equals(status) || "Paid".equals(status);
    }
    
    public boolean needsApproval() {
        return "Pending".equals(status) && amount > 1000; // Example threshold
    }
    
    public String getCategoryDisplay() {
        if (subcategory != null && !subcategory.isEmpty()) {
            return category + " - " + subcategory;
        }
        return category != null ? category : "Uncategorized";
    }
    
    public boolean isDueForRecurring() {
        if (!isRecurring || nextDueDate == null) {
            return false;
        }
        return !nextDueDate.isAfter(LocalDate.now());
    }
    
    public double getVarianceFromBudget() {
        if (budgetedAmount <= 0) {
            return 0;
        }
        return ((amount - budgetedAmount) / budgetedAmount) * 100;
    }
    
    public boolean isOverBudget() {
        return budgetedAmount > 0 && amount > budgetedAmount;
    }
    
    // Original getters and setters
    public int getId() { return id; }
    public void setId(int id) { 
        this.id = id;
        updateModified();
    }
    
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { 
        this.expenseDate = expenseDate;
        updateModified();
    }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        updateModified();
    }
    
    public double getAmount() { return amount; }
    public void setAmount(double amount) { 
        this.amount = amount;
        updateModified();
    }
    
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { 
        this.receiptNumber = receiptNumber;
        updateModified();
    }
    
    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { 
        this.receiptPath = receiptPath;
        updateModified();
    }
    
    // New enhanced getters and setters
    public String getCategory() { return category; }
    public void setCategory(String category) { 
        this.category = category;
        updateModified();
    }
    
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { 
        this.subcategory = subcategory;
        updateModified();
    }
    
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { 
        this.vendor = vendor;
        updateModified();
    }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { 
        this.paymentMethod = paymentMethod;
        updateModified();
    }
    
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { 
        this.paymentReference = paymentReference;
        updateModified();
    }
    
    public String getExpenseType() { return expenseType; }
    public void setExpenseType(String expenseType) { 
        this.expenseType = expenseType;
        updateModified();
    }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { 
        this.department = department;
        updateModified();
    }
    
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { 
        this.approvedBy = approvedBy;
        updateModified();
    }
    
    public LocalDate getApprovalDate() { return approvalDate; }
    public void setApprovalDate(LocalDate approvalDate) { 
        this.approvalDate = approvalDate;
        updateModified();
    }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status;
        if ("Approved".equals(status)) {
            this.approvalDate = LocalDate.now();
            this.approvedBy = "mgubran1";
        }
        updateModified();
    }
    
    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { 
        isRecurring = recurring;
        updateModified();
    }
    
    public String getRecurringFrequency() { return recurringFrequency; }
    public void setRecurringFrequency(String recurringFrequency) { 
        this.recurringFrequency = recurringFrequency;
        updateModified();
    }
    
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { 
        this.nextDueDate = nextDueDate;
        updateModified();
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { 
        this.notes = notes;
        updateModified();
    }
    
    public String getAttachedDocuments() { return attachedDocuments; }
    public void setAttachedDocuments(String attachedDocuments) { 
        this.attachedDocuments = attachedDocuments;
        updateModified();
    }
    
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { 
        this.taxAmount = taxAmount;
        updateModified();
    }
    
    public String getTaxCategory() { return taxCategory; }
    public void setTaxCategory(String taxCategory) { 
        this.taxCategory = taxCategory;
        updateModified();
    }
    
    public String getAccountingCode() { return accountingCode; }
    public void setAccountingCode(String accountingCode) { 
        this.accountingCode = accountingCode;
        updateModified();
    }
    
    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { 
        this.costCenter = costCenter;
        updateModified();
    }
    
    public String getProject() { return project; }
    public void setProject(String project) { 
        this.project = project;
        updateModified();
    }
    
    public String getTruckUnit() { return truckUnit; }
    public void setTruckUnit(String truckUnit) { 
        this.truckUnit = truckUnit;
        updateModified();
    }
    
    public String getTrailerNumber() { return trailerNumber; }
    public void setTrailerNumber(String trailerNumber) { 
        this.trailerNumber = trailerNumber;
        updateModified();
    }
    
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { 
        this.employeeId = employeeId;
        updateModified();
    }
    
    public String getBudgetCategory() { return budgetCategory; }
    public void setBudgetCategory(String budgetCategory) { 
        this.budgetCategory = budgetCategory;
        updateModified();
    }
    
    public double getBudgetedAmount() { return budgetedAmount; }
    public void setBudgetedAmount(double budgetedAmount) { 
        this.budgetedAmount = budgetedAmount;
        updateModified();
    }
    
    public String getBudgetPeriod() { return budgetPeriod; }
    public void setBudgetPeriod(String budgetPeriod) { 
        this.budgetPeriod = budgetPeriod;
        updateModified();
    }
    
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(LocalDateTime modifiedDate) { this.modifiedDate = modifiedDate; }
    
    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }
    
    // Helper method to update modification tracking
    private void updateModified() {
        this.modifiedDate = LocalDateTime.now();
        this.modifiedBy = "mgubran1";
    }
    
    @Override
    public String toString() {
        return String.format("%s - %s: $%.2f (%s)", 
            expenseDate, description, amount, status);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompanyExpense that = (CompanyExpense) o;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}