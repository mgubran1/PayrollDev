package com.company.payroll.dispatcher;

import com.company.payroll.employees.Employee;
import com.company.payroll.loads.Load;
import javafx.beans.property.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing driver status for dispatcher
 */
public class DispatcherDriverStatus {
    public enum Status {
        AVAILABLE("Available", "#28a745"),      // Green
        PREPARING("Preparing", "#ffc107"),      // Amber
        LOADING("Loading", "#ffc107"),          // Amber
        ON_ROAD("On Road", "#007bff"),         // Blue
        UNLOADING("Unloading", "#ffc107"),     // Amber
        RETURNING("Returning", "#007bff"),      // Blue
        OFF_DUTY("Off Duty", "#6c757d"),       // Gray
        ON_BREAK("On Break", "#17a2b8");       // Cyan
        
        private final String displayName;
        private final String color;
        
        Status(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
    
    private final Employee driver;
    private final IntegerProperty driverId;
    private final StringProperty driverName;
    private final StringProperty truckUnit;
    private final StringProperty trailerNumber;
    private final ObjectProperty<Status> status;
    private final ObjectProperty<Load> currentLoad;
    private final ObjectProperty<Load> nextLoad;
    private final StringProperty currentLocation;
    private final ObjectProperty<LocalDateTime> estimatedAvailableTime;
    private final DoubleProperty hoursWorkedToday;
    private final DoubleProperty hoursWorkedWeek;
    
    private List<Load> assignedLoads = new ArrayList<>();
    private List<DispatcherController.TimeSlot> availabilityWindows = new ArrayList<>();
    
    public DispatcherDriverStatus(Employee driver) {
        this.driver = driver;
        this.driverId = new SimpleIntegerProperty(driver.getId());
        this.driverName = new SimpleStringProperty(driver.getName());
        this.truckUnit = new SimpleStringProperty(driver.getTruckUnit());
        this.trailerNumber = new SimpleStringProperty(driver.getTrailerNumber());
        this.status = new SimpleObjectProperty<>(Status.AVAILABLE);
        this.currentLoad = new SimpleObjectProperty<>();
        this.nextLoad = new SimpleObjectProperty<>();
        this.currentLocation = new SimpleStringProperty("Home Base");
        this.estimatedAvailableTime = new SimpleObjectProperty<>();
        this.hoursWorkedToday = new SimpleDoubleProperty(0.0);
        this.hoursWorkedWeek = new SimpleDoubleProperty(0.0);
    }
    
    // Getters and setters
    public Employee getDriver() { return driver; }
    
    public int getDriverId() { return driverId.get(); }
    public IntegerProperty driverIdProperty() { return driverId; }
    
    public String getDriverName() { return driverName.get(); }
    public StringProperty driverNameProperty() { return driverName; }
    
    public String getTruckUnit() { return truckUnit.get(); }
    public StringProperty truckUnitProperty() { return truckUnit; }
    
    public String getTrailerNumber() { return trailerNumber.get(); }
    public StringProperty trailerNumberProperty() { return trailerNumber; }
    public void setTrailerNumber(String value) { trailerNumber.set(value); }
    
    public Status getStatus() { return status.get(); }
    public ObjectProperty<Status> statusProperty() { return status; }
    public void setStatus(Status value) { status.set(value); }
    
    public Load getCurrentLoad() { return currentLoad.get(); }
    public ObjectProperty<Load> currentLoadProperty() { return currentLoad; }
    public void setCurrentLoad(Load value) { currentLoad.set(value); }
    
    public Load getNextLoad() { return nextLoad.get(); }
    public ObjectProperty<Load> nextLoadProperty() { return nextLoad; }
    public void setNextLoad(Load value) { nextLoad.set(value); }
    
    public String getCurrentLocation() { return currentLocation.get(); }
    public StringProperty currentLocationProperty() { return currentLocation; }
    public void setCurrentLocation(String value) { currentLocation.set(value); }
    
    public LocalDateTime getEstimatedAvailableTime() { return estimatedAvailableTime.get(); }
    public ObjectProperty<LocalDateTime> estimatedAvailableTimeProperty() { return estimatedAvailableTime; }
    public void setEstimatedAvailableTime(LocalDateTime value) { estimatedAvailableTime.set(value); }
    
    public double getHoursWorkedToday() { return hoursWorkedToday.get(); }
    public DoubleProperty hoursWorkedTodayProperty() { return hoursWorkedToday; }
    public void setHoursWorkedToday(double value) { hoursWorkedToday.set(value); }
    
    public double getHoursWorkedWeek() { return hoursWorkedWeek.get(); }
    public DoubleProperty hoursWorkedWeekProperty() { return hoursWorkedWeek; }
    public void setHoursWorkedWeek(double value) { hoursWorkedWeek.set(value); }
    
    public List<Load> getAssignedLoads() { return assignedLoads; }
    public void setAssignedLoads(List<Load> loads) { this.assignedLoads = loads; }
    
    public List<DispatcherController.TimeSlot> getAvailabilityWindows() { return availabilityWindows; }
    public void setAvailabilityWindows(List<DispatcherController.TimeSlot> windows) { this.availabilityWindows = windows; }
    
    public boolean isAvailable() {
        return status.get() == Status.AVAILABLE;
    }
    
    public boolean isOnDuty() {
        return status.get() != Status.OFF_DUTY && status.get() != Status.ON_BREAK;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s", driverName.get(), truckUnit.get(), status.get().getDisplayName());
    }
}