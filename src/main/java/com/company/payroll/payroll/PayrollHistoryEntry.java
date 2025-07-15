package com.company.payroll.payroll;

import java.time.LocalDate;

/**
 * Represents a single entry in the payroll history table.
 * Includes the percentages used at the time of payroll calculation.
 */
public class PayrollHistoryEntry {
    public final LocalDate date;
    public final String driverName;
    public final String truckUnit;
    public final int loadCount;
    public final double gross;
    public final double totalDeductions;
    public final double netPay;
    public final boolean locked;
    public final double driverPercentUsed;
    public final double companyPercentUsed;
    public final double serviceFeePercentUsed;

    // Legacy constructor for backward compatibility
    public PayrollHistoryEntry(LocalDate date,
                               String driverName,
                               String truckUnit,
                               int loadCount,
                               double gross,
                               double totalDeductions,
                               double netPay,
                               boolean locked) {
        this(date, driverName, truckUnit, loadCount, gross, totalDeductions, netPay, locked, 0.0, 0.0, 0.0);
    }
    
    // New constructor with percentage tracking
    public PayrollHistoryEntry(LocalDate date,
                               String driverName,
                               String truckUnit,
                               int loadCount,
                               double gross,
                               double totalDeductions,
                               double netPay,
                               boolean locked,
                               double driverPercentUsed,
                               double companyPercentUsed,
                               double serviceFeePercentUsed) {
        this.date = date;
        this.driverName = driverName;
        this.truckUnit = truckUnit;
        this.loadCount = loadCount;
        this.gross = gross;
        this.totalDeductions = totalDeductions;
        this.netPay = netPay;
        this.locked = locked;
        this.driverPercentUsed = driverPercentUsed;
        this.companyPercentUsed = companyPercentUsed;
        this.serviceFeePercentUsed = serviceFeePercentUsed;
    }
}