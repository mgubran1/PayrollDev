package com.company.payroll.trucks;

import javafx.beans.property.*;

/**
 * Represents performance metrics for a truck. This class was previously
 * declared inside {@link TrucksTab} but is now a standalone model so DAO
 * classes can reference it without depending on UI code.
 */
public class TruckPerformance {
    private final StringProperty truckNumber = new SimpleStringProperty();
    private final StringProperty driverName = new SimpleStringProperty();
    private final IntegerProperty milesDriven = new SimpleIntegerProperty();
    private final DoubleProperty fuelUsed = new SimpleDoubleProperty();
    private final DoubleProperty mpg = new SimpleDoubleProperty();
    private final IntegerProperty idleTime = new SimpleIntegerProperty();
    private final DoubleProperty revenue = new SimpleDoubleProperty();
    private final DoubleProperty performanceScore = new SimpleDoubleProperty();

    public String getTruckNumber() { return truckNumber.get(); }
    public void setTruckNumber(String value) { truckNumber.set(value); }
    public StringProperty truckNumberProperty() { return truckNumber; }

    public String getDriverName() { return driverName.get(); }
    public void setDriverName(String value) { driverName.set(value); }
    public StringProperty driverNameProperty() { return driverName; }

    public int getMilesDriven() { return milesDriven.get(); }
    public void setMilesDriven(int value) { milesDriven.set(value); }
    public IntegerProperty milesDrivenProperty() { return milesDriven; }

    public double getFuelUsed() { return fuelUsed.get(); }
    public void setFuelUsed(double value) { fuelUsed.set(value); }
    public DoubleProperty fuelUsedProperty() { return fuelUsed; }

    public double getMpg() { return mpg.get(); }
    public void setMpg(double value) { mpg.set(value); }
    public DoubleProperty mpgProperty() { return mpg; }

    public int getIdleTime() { return idleTime.get(); }
    public void setIdleTime(int value) { idleTime.set(value); }
    public IntegerProperty idleTimeProperty() { return idleTime; }

    public double getRevenue() { return revenue.get(); }
    public void setRevenue(double value) { revenue.set(value); }
    public DoubleProperty revenueProperty() { return revenue; }

    public double getPerformanceScore() { return performanceScore.get(); }
    public void setPerformanceScore(double value) { performanceScore.set(value); }
    public DoubleProperty performanceScoreProperty() { return performanceScore; }
}
