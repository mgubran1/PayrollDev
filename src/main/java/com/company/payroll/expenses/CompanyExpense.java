package com.company.payroll.expenses;

import java.time.LocalDate;

/**
 * Simple model representing a single company expense entry.
 * This matches the fields used by {@link CompanyExpensesTab}.
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

    public CompanyExpense() {
    }

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
                          String employeeId) {
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
    }

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
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }
}
