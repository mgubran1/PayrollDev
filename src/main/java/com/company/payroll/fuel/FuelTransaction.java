package com.company.payroll.fuel;

public class FuelTransaction {
    private int id;
    private String cardNumber;
    private String tranDate;
    private String tranTime;
    private String invoice;
    private String unit;
    private String driverName;
    private String odometer;
    private String locationName;
    private String city;
    private String stateProv;
    private double fees;
    private String item;
    private double unitPrice;
    private double discPPU; 
    private double discCost;
    private double qty;
    private double discAmt;
    private String discType;
    private double amt;
    private String db;
    private String currency;
    private int employeeId;

    public FuelTransaction(
        int id,
        String cardNumber,
        String tranDate,
        String tranTime,
        String invoice,
        String unit,
        String driverName,
        String odometer,
        String locationName,
        String city,
        String stateProv,
        double fees,
        String item,
        double unitPrice,
        double discPPU,
        double discCost,
        double qty,
        double discAmt,
        String discType,
        double amt,
        String db,
        String currency,
        int employeeId
    ) {
        this.id = id;
        this.cardNumber = cardNumber;
        this.tranDate = tranDate;
        this.tranTime = tranTime;
        this.invoice = invoice;
        this.unit = unit;
        this.driverName = driverName;
        this.odometer = odometer;
        this.locationName = locationName;
        this.city = city;
        this.stateProv = stateProv;
        this.fees = fees;
        this.item = item;
        this.unitPrice = unitPrice;
        this.discPPU = discPPU;
        this.discCost = discCost;
        this.qty = qty;
        this.discAmt = discAmt;
        this.discType = discType;
        this.amt = amt;
        this.db = db; 
        this.currency = currency;
        this.employeeId = employeeId;
    }

    // --- Getters for each field ---
    public int getId() { return id; }
    public String getCardNumber() { return cardNumber; }
    public String getTranDate() { return tranDate; }
    public String getTranTime() { return tranTime; }
    public String getInvoice() { return invoice; }
    public String getUnit() { return unit; }
    public String getDriverName() { return driverName; }
    public String getOdometer() { return odometer; }
    public String getLocationName() { return locationName; }
    public String getCity() { return city; }
    public String getStateProv() { return stateProv; }
    public double getFees() { return fees; }
    public String getItem() { return item; }
    public double getUnitPrice() { return unitPrice; }
    public double getDiscPPU() { return discPPU; }
    public double getDiscCost() { return discCost; }
    public double getQty() { return qty; }
    public double getDiscAmt() { return discAmt; }
    public String getDiscType() { return discType; }
    public double getAmt() { return amt; }
    public String getDb() { return db; }
    public String getCurrency() { return currency; }
    public int getEmployeeId() { return employeeId; }
}