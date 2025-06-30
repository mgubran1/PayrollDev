package com.company.payroll.expenses;

import java.time.LocalDate;

public class CompanyExpense {
    private int id;
    private LocalDate expenseDate;
    private String description;
    private double amount;
    private String receiptNumber;
    private String receiptPath;

    public CompanyExpense(int id, LocalDate expenseDate, String description,
                          double amount, String receiptNumber, String receiptPath) {
        this.id = id;
        this.expenseDate = expenseDate;
        this.description = description;
        this.amount = amount;
        this.receiptNumber = receiptNumber;
        this.receiptPath = receiptPath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }
}
