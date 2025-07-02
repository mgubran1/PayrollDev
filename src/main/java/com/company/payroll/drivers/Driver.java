package com.company.payroll.drivers;

import com.company.payroll.loads.Load;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Minimal driver model used for compilation. */
public class Driver {
    private String driverId;
    private String name;
    private String phoneNumber;
    private String email;
    private String licenseNumber;
    private String licenseState;
    private String truckUnit;
    private String trailerNumber;
    private String location;
    private String notes;
    private double currentSpeed;
    private double heading;
    private Load currentLoad;
    private LocalDateTime eta;
    private double hoursWorkedToday;
    private double hoursWorkedWeek;
    private String homeTerminal;
    private final List<Load> assignedLoads = new ArrayList<>();

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getName() { return name; }
    public String getDriverName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getLicenseNumber() { return licenseNumber; }
    public String getLicenseState() { return licenseState; }

    public String getTruckUnit() { return truckUnit; }
    public String getTruckNumber() { return truckUnit; }
    public void setTruckUnit(String truckUnit) { this.truckUnit = truckUnit; }

    public String getTrailerNumber() { return trailerNumber; }
    public void setTrailerNumber(String trailerNumber) { this.trailerNumber = trailerNumber; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Load getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(Load load) { this.currentLoad = load; }

    public List<Load> getAssignedLoads() { return assignedLoads; }

    public double getCurrentSpeed() { return currentSpeed; }
    public double getHeading() { return heading; }

    public LocalDateTime getETA() { return eta; }

    public double getHoursWorkedToday() { return hoursWorkedToday; }
    public double getHoursWorkedWeek() { return hoursWorkedWeek; }

    public boolean isOnDuty() { return true; }
    public boolean isAvailableForDispatch() { return true; }
    public boolean isInSleeper() { return false; }

    public String getHomeTerminal() { return homeTerminal; }
    public String getPhotoUrl() { return null; }
}
