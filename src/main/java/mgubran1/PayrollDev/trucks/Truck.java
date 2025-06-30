package mgubran1.PayrollDev.trucks;

public class Truck {
    private int id;
    private String unitNumber;
    private String make;
    private String model;
    private int year;
    private String vin;
    private String licensePlate;
    private double mileage;
    private Status status;
    private String notes;

    public enum Status { ACTIVE, IN_SHOP, SOLD, OUT_OF_SERVICE }

    public Truck(int id, String unitNumber, String make, String model, int year, String vin,
                 String licensePlate, double mileage, Status status, String notes) {
        this.id = id;
        this.unitNumber = unitNumber;
        this.make = make;
        this.model = model;
        this.year = year;
        this.vin = vin;
        this.licensePlate = licensePlate;
        this.mileage = mileage;
        this.status = status;
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public double getMileage() { return mileage; }
    public void setMileage(double mileage) { this.mileage = mileage; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
