package com.company.payroll.expenses;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Model representing a company expense entry in the fleet management system.
 * Tracks all business expenses including fuel, insurance, permits, office supplies, etc.
 */
public class CompanyExpense {
    private int id;
    private LocalDate expenseDate;
    private String vendor;
    private String category;
    private String department;
    private String description;
    private double amount;
    private String paymentMethod;
    private String receiptNumber;
    private boolean recurring;
    private String status;
    private String notes;
    private String employeeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String approvedBy;
    private LocalDate approvalDate;

    // Default constructor
    public CompanyExpense() {
        this.status = "Pending";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Full constructor
    public CompanyExpense(int id,
                          LocalDate expenseDate,
                          String vendor,
                          String category,
                          String department,
                          String description,
                          double amount,
                          String paymentMethod,
                          String receiptNumber,
                          boolean recurring,
                          String status,
                          String notes,
                          String employeeId,
                          LocalDateTime createdAt,
                          LocalDateTime updatedAt,
                          String approvedBy,
                          LocalDate approvalDate) {
        this.id = id;
        this.expenseDate = expenseDate;
        this.vendor = vendor;
        this.category = category;
        this.department = department;
        this.description = description;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.receiptNumber = receiptNumber;
        this.recurring = recurring;
        this.status = status;
        this.notes = notes;
        this.employeeId = employeeId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.approvedBy = approvedBy;
        this.approvalDate = approvalDate;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
        this.updatedAt = LocalDateTime.now();
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
        this.updatedAt = LocalDateTime.now();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.updatedAt = LocalDateTime.now();
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
        this.updatedAt = LocalDateTime.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
        
        // If approved, set approval info
        if ("Approved".equals(status) && this.approvalDate == null) {
            this.approvalDate = LocalDate.now();
        }
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        this.updatedAt = LocalDateTime.now();
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDate getApprovalDate() {
        return approvalDate;
    }

    public void setApprovalDate(LocalDate approvalDate) {
        this.approvalDate = approvalDate;
    }

    @Override
    public String toString() {
        return String.format("CompanyExpense[id=%d, date=%s, vendor=%s, category=%s, amount=%.2f, status=%s]",
                id, expenseDate, vendor, category, amount, status);
    }
}