package com.company.payroll.payroll;

import java.time.LocalDate;

/**
 * Represents a single entry in the payroll history table.
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

    public PayrollHistoryEntry(LocalDate date,
                               String driverName,
                               String truckUnit,
                               int loadCount,
                               double gross,
                               double totalDeductions,
                               double netPay,
                               boolean locked) {
        this.date = date;
        this.driverName = driverName;
        this.truckUnit = truckUnit;
        this.loadCount = loadCount;
        this.gross = gross;
        this.totalDeductions = totalDeductions;
        this.netPay = netPay;
        this.locked = locked;
    }
}
