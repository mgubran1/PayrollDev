package com.company.payroll.dispatcher;

import java.time.LocalDate;
import com.company.payroll.loads.Load;

/**
 * Represents driver availability status
 */
public class DriverAvailability {
    public enum AvailabilityStatus { 
        AVAILABLE("Available", "#90EE90"),
        ON_ROAD("On Road", "#FFD700"),
        RETURNING("Returning", "#87CEEB"),
        OFF_DUTY("Off Duty", "#FFB6C1"),
        ON_LEAVE("On Leave", "#DDA0DD");
        
        private final String displayName;
        private final String color;
        
        AvailabilityStatus(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
    
    private final int driverId;
    private final String driverName;
    private final String truckUnit;
    private final String trailerNumber;
    private AvailabilityStatus status;
    private Load currentLoad;
    private LocalDate expectedReturnDate;
    private String currentLocation;
    private String notes;
    
    public DriverAvailability(int driverId, String driverName, String truckUnit, 
                            String trailerNumber, AvailabilityStatus status) {
        this.driverId = driverId;
        this.driverName = driverName;
        this.truckUnit = truckUnit;
        this.trailerNumber = trailerNumber;
        this.status = status;
    }
    
    // Getters
    public int getDriverId() { return driverId; }
    public String getDriverName() { return driverName; }
    public String getTruckUnit() { return truckUnit; }
    public String getTrailerNumber() { return trailerNumber; }
    public AvailabilityStatus getStatus() { return status; }
    public Load getCurrentLoad() { return currentLoad; }
    public LocalDate getExpectedReturnDate() { return expectedReturnDate; }
    public String getCurrentLocation() { return currentLocation; }
    public String getNotes() { return notes; }
    
    // Setters
    public void setStatus(AvailabilityStatus status) { this.status = status; }
    public void setCurrentLoad(Load currentLoad) { this.currentLoad = currentLoad; }
    public void setExpectedReturnDate(LocalDate expectedReturnDate) { 
        this.expectedReturnDate = expectedReturnDate; 
    }
    public void setCurrentLocation(String currentLocation) { 
        this.currentLocation = currentLocation; 
    }
    public void setNotes(String notes) { this.notes = notes; }
    
    public boolean isAvailable() {
        return status == AvailabilityStatus.AVAILABLE;
    }
    
    public boolean isOnRoad() {
        return status == AvailabilityStatus.ON_ROAD || status == AvailabilityStatus.RETURNING;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s", driverName, truckUnit, status.getDisplayName());
    }
}