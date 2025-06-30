package com.company.payroll.triumph;

import java.time.LocalDate;

public class MyTriumphRecord {
    private int id;
    private String dtrName;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String po;
    private double invAmt;
    
    // Add source tracking fields
    private String source; // "LOAD" or "IMPORT"
    private boolean matched; // true when LOAD record is matched with IMPORT

    // Cross-referenced fields (not stored, but for UI)
    private String matchStatus; // "BILLED", "UNBILLED"
    private String driverName;  // from Loads

    public MyTriumphRecord(int id, String dtrName, String invoiceNumber, LocalDate invoiceDate, String po, double invAmt) {
        this.id = id;
        this.dtrName = dtrName;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.po = po;
        this.invAmt = invAmt;
        this.source = "IMPORT"; // default
        this.matched = false;
    }

    // Constructor for creating from loads (no invoice number yet)
    public MyTriumphRecord(String dtrName, String po, LocalDate deliveryDate, double invAmt) {
        this.id = 0;
        this.dtrName = dtrName;
        this.invoiceNumber = "PENDING";
        this.invoiceDate = deliveryDate;
        this.po = po;
        this.invAmt = invAmt;
        this.source = "LOAD";
        this.matched = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getDtrName() { return dtrName; }
    public void setDtrName(String dtrName) { this.dtrName = dtrName; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public String getPo() { return po; }
    public void setPo(String po) { this.po = po; }
    public double getInvAmt() { return invAmt; }
    public void setInvAmt(double invAmt) { this.invAmt = invAmt; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
}