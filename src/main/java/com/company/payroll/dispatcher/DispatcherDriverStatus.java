package com.company.payroll.dispatcher;

import com.company.payroll.drivers.Driver;
import com.company.payroll.loads.Load;
import com.company.payroll.utils.GeoCoordinates;

import javafx.beans.property.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced model representing driver status for dispatcher with
 * comprehensive tracking and history features
 * 
 * @author Payroll System
 * @version 2.0
 * @since 2025-07-02
 */
public class DispatcherDriverStatus {
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_STATUS_HISTORY = 50;
    
    /**
     * Driver status enum with display names and color codes
     */
    public enum Status {
        AVAILABLE("Available", "#28a745"),      // Green
        ON_ROAD("On Road", "#007bff"),          // Blue
        LOADING("Loading", "#ffc107"),          // Amber
        UNLOADING("Unloading", "#ffc107"),      // Amber
        BREAK("Break", "#17a2b8"),              // Cyan
        SLEEPER("Sleeper", "#6c757d"),          // Gray
        OFF_DUTY("Off Duty", "#6c757d"),        // Gray
        BREAKDOWN("Breakdown", "#dc3545"),       // Red
        RESERVED("Reserved", "#fd7e14"),        // Orange
        RETURNING("Returning", "#007bff");      // Blue
        
        private final String displayName;
        private final String color;
        
        Status(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        
        /**
         * Get estimated hours available for driver with this status
         */
        public double getEstimatedHoursAvailable() {
            switch(this) {
                case AVAILABLE: return 11.0;
                case ON_ROAD: return 6.0;
                case LOADING: case UNLOADING: return 8.0;
                case BREAK: return 10.0;
                case SLEEPER: return 3.0;
                case OFF_DUTY: return 0.0;
                case BREAKDOWN: return 0.0;
                case RESERVED: return 2.0;
                case RETURNING: return 4.0;
                default: return 0.0;
            }
        }
    }
    
    /**
     * Record of a status change event
     */
    public static class StatusChangeEvent {
        private final Status status;
        private final LocalDateTime timestamp;
        private final String location;
        private final String notes;
        private final String changedBy;
        
        public StatusChangeEvent(Status status, String location, String notes, String changedBy) {
            this.status = status;
            this.timestamp = LocalDateTime.now();
            this.location = location;
            this.notes = notes;
            this.changedBy = changedBy;
        }
        
        public Status getStatus() { return status; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getLocation() { return location; }
        public String getNotes() { return notes; }
        public String getChangedBy() { return changedBy; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s - %s by %s", 
                timestamp.format(DATETIME_FORMAT), 
                status.getDisplayName(), 
                location != null ? location : "Unknown",
                changedBy);
        }
    }
    
    // Core driver data
    private Driver driver;
    private final StringProperty driverName;
    private final StringProperty truckNumber;
    private final StringProperty trailerNumber;
    
    // Current status properties
    private final ObjectProperty<Status> status;
    private final StringProperty location;
    private final StringProperty notes;
    private final ObjectProperty<LocalDateTime> lastUpdateTime;
    private final StringProperty lastUpdatedBy;
    private final ObjectProperty<LocalDateTime> eta;
    private final ObjectProperty<LocalDateTime> estimatedAvailableTime;
    
    // Load assignment properties
    private final ObjectProperty<Load> currentLoad;
    private final ObjectProperty<Load> nextLoad;
    
    // Hours of service tracking
    private final DoubleProperty hoursWorkedToday;
    private final DoubleProperty hoursWorkedWeek;
    private final IntegerProperty consecutiveDrivingDays;
    private final ObjectProperty<LocalDateTime> lastRestDateTime;
    
    // Tracking data
    private final ObjectProperty<GeoCoordinates> currentCoordinates;
    private final DoubleProperty currentSpeed;
    private final DoubleProperty heading;
    private final BooleanProperty moving;
    
    // Status history
    private final LinkedList<StatusChangeEvent> statusHistory;
    
    // Additional data
    private List<Load> assignedLoads;
    private List<TimeSlot> availabilityWindows;
    private Map<String, Object> extendedAttributes;
    
    /**
     * Constructor with driver
     */
    public DispatcherDriverStatus(Driver driver) {
        this(driver, Status.AVAILABLE, null, null, null);
    }
    
    /**
     * Constructor with driver and status
     */
    public DispatcherDriverStatus(Driver driver, Status status, String location, 
                                 LocalDateTime eta, String notes) {
        this.driver = driver;
        
        // Initialize properties
        this.driverName = new SimpleStringProperty(driver.getName());
        this.truckNumber = new SimpleStringProperty(driver.getTruckNumber());
        this.trailerNumber = new SimpleStringProperty(driver.getTrailerNumber());
        
        this.status = new SimpleObjectProperty<>(status != null ? status : Status.AVAILABLE);
        this.location = new SimpleStringProperty(location);
        this.notes = new SimpleStringProperty(notes);
        this.lastUpdateTime = new SimpleObjectProperty<>(LocalDateTime.now());
        this.lastUpdatedBy = new SimpleStringProperty("mgubran1"); // Use system username
        this.eta = new SimpleObjectProperty<>(eta);
        this.estimatedAvailableTime = new SimpleObjectProperty<>(eta);
        
        this.currentLoad = new SimpleObjectProperty<>(driver.getCurrentLoad());
        this.nextLoad = new SimpleObjectProperty<>();
        
        this.hoursWorkedToday = new SimpleDoubleProperty(0.0);
        this.hoursWorkedWeek = new SimpleDoubleProperty(0.0);
        this.consecutiveDrivingDays = new SimpleIntegerProperty(0);
        this.lastRestDateTime = new SimpleObjectProperty<>();
        
        this.currentCoordinates = new SimpleObjectProperty<>();
        this.currentSpeed = new SimpleDoubleProperty(0.0);
        this.heading = new SimpleDoubleProperty(0.0);
        this.moving = new SimpleBooleanProperty(false);
        
        this.statusHistory = new LinkedList<>();
        this.assignedLoads = new ArrayList<>();
        this.availabilityWindows = new ArrayList<>();
        this.extendedAttributes = new ConcurrentHashMap<>();
        
        // Add initial status change event
        if (status != null) {
            addStatusChangeEvent(status, location, notes, "mgubran1");
        }
    }
    
    // Driver info getters
    public Driver getDriver() { return driver; }
    
    public void setDriver(Driver d) { this.driver = d; driverName.set(d.getName()); truckNumber.set(d.getTruckNumber()); trailerNumber.set(d.getTrailerNumber()); }
    public String getDriverId() { return driver.getDriverId(); }
    
    public String getDriverName() { return driverName.get(); }
    public StringProperty driverNameProperty() { return driverName; }
    
    public String getTruckNumber() { return truckNumber.get(); }
    public StringProperty truckNumberProperty() { return truckNumber; }
    public String getTruckUnit() { return getTruckNumber(); }

    public void setTruckNumber(String value) { truckNumber.set(value); }
    
    public String getTrailerNumber() { return trailerNumber.get(); }
    public StringProperty trailerNumberProperty() { return trailerNumber; }
    public void setTrailerNumber(String value) { trailerNumber.set(value); }
    
    // Status getters and setters
    public Status getStatus() { return status.get(); }
    public ObjectProperty<Status> statusProperty() { return status; }
    public void setStatus(Status value) { 
        status.set(value);
    }
    
    public String getLocation() { return location.get(); }
    public StringProperty locationProperty() { return location; }
    public void setLocation(String value) { location.set(value); }
    
    public String getNotes() { return notes.get(); }
    public String getCurrentLocation() { return getLocation(); }
    public StringProperty currentLocationProperty() { return locationProperty(); }

    public StringProperty notesProperty() { return notes; }
    public void setNotes(String value) { notes.set(value); }
    
    public LocalDateTime getLastUpdateTime() { return lastUpdateTime.get(); }
    public ObjectProperty<LocalDateTime> lastUpdateTimeProperty() { return lastUpdateTime; }
    
    public String getLastUpdatedBy() { return lastUpdatedBy.get(); }
    public StringProperty lastUpdatedByProperty() { return lastUpdatedBy; }
    
    public LocalDateTime getETA() { return eta.get(); }
    public ObjectProperty<LocalDateTime> etaProperty() { return eta; }
    public void setETA(LocalDateTime value) { eta.set(value); }
    
    public LocalDateTime getEstimatedAvailableTime() { return estimatedAvailableTime.get(); }
    public ObjectProperty<LocalDateTime> estimatedAvailableTimeProperty() { return estimatedAvailableTime; }
    public void setEstimatedAvailableTime(LocalDateTime value) { estimatedAvailableTime.set(value); }

    // Load assignment getters and setters
    public Load getCurrentLoad() { return currentLoad.get(); }
    public ObjectProperty<Load> currentLoadProperty() { return currentLoad; }
    public void setCurrentLoad(Load value) { currentLoad.set(value); }
    
    public Load getNextLoad() { return nextLoad.get(); }
    public ObjectProperty<Load> nextLoadProperty() { return nextLoad; }
    public void setNextLoad(Load value) { nextLoad.set(value); }
    
    public List<Load> getAssignedLoads() { return assignedLoads; }
    public void setAssignedLoads(List<Load> loads) { this.assignedLoads = loads; }
    
    // Hours of service getters and setters
    public double getHoursWorkedToday() { return hoursWorkedToday.get(); }
    public DoubleProperty hoursWorkedTodayProperty() { return hoursWorkedToday; }
    public void setHoursWorkedToday(double value) { hoursWorkedToday.set(value); }
    
    public double getHoursWorkedWeek() { return hoursWorkedWeek.get(); }
    public DoubleProperty hoursWorkedWeekProperty() { return hoursWorkedWeek; }
    public void setHoursWorkedWeek(double value) { hoursWorkedWeek.set(value); }
    
    public int getConsecutiveDrivingDays() { return consecutiveDrivingDays.get(); }
    public IntegerProperty consecutiveDrivingDaysProperty() { return consecutiveDrivingDays; }
    public void setConsecutiveDrivingDays(int value) { consecutiveDrivingDays.set(value); }
    
    public LocalDateTime getLastRestDateTime() { return lastRestDateTime.get(); }
    public ObjectProperty<LocalDateTime> lastRestDateTimeProperty() { return lastRestDateTime; }
    public void setLastRestDateTime(LocalDateTime value) { lastRestDateTime.set(value); }
    
    // Tracking data getters and setters
    public GeoCoordinates getCurrentCoordinates() { return currentCoordinates.get(); }
    public ObjectProperty<GeoCoordinates> currentCoordinatesProperty() { return currentCoordinates; }
    public void setCurrentCoordinates(GeoCoordinates value) { currentCoordinates.set(value); }
    
    public double getCurrentSpeed() { return currentSpeed.get(); }
    public DoubleProperty currentSpeedProperty() { return currentSpeed; }
    public void setCurrentSpeed(double value) { currentSpeed.set(value); }
    
    public double getHeading() { return heading.get(); }
    public DoubleProperty headingProperty() { return heading; }
    public void setHeading(double value) { heading.set(value); }
    
    public boolean isMoving() { return moving.get(); }
    public BooleanProperty movingProperty() { return moving; }
    public void setMoving(boolean value) { moving.set(value); }
    
    // Status history methods
    public LinkedList<StatusChangeEvent> getStatusHistory() { return statusHistory; }
    
    /**
     * Add a status change event to history
     */
    public void addStatusChangeEvent(Status status, String location, String notes, String changedBy) {
        StatusChangeEvent event = new StatusChangeEvent(status, location, notes, changedBy);
        statusHistory.addFirst(event); // Add to front of list (newest first)
        
        // Trim history if needed
        while (statusHistory.size() > MAX_STATUS_HISTORY) {
            statusHistory.removeLast();
        }
        
        // Update current values
        this.status.set(status);
        if (location != null) this.location.set(location);
        if (notes != null) this.notes.set(notes);
        this.lastUpdateTime.set(LocalDateTime.now());
        this.lastUpdatedBy.set(changedBy);
    }
    
    /**
     * Get the most recent status change event
     */
    public StatusChangeEvent getLatestStatusChange() {
        return statusHistory.isEmpty() ? null : statusHistory.getFirst();
    }
    
    /**
     * Get time since last status change
     */
    public Duration getTimeSinceLastStatusChange() {
        StatusChangeEvent latest = getLatestStatusChange();
        return latest != null ? Duration.between(latest.getTimestamp(), LocalDateTime.now()) : Duration.ZERO;
    }
    
    // Availability methods
    public List<TimeSlot> getAvailabilityWindows() { return availabilityWindows; }
    public void setAvailabilityWindows(List<TimeSlot> windows) { this.availabilityWindows = windows; }
    
    public boolean isAvailable() {
        return status.get() == Status.AVAILABLE;
    }
    
    public boolean isOnDuty() {
        return status.get() != Status.OFF_DUTY && status.get() != Status.SLEEPER;
    }
    
    public boolean hasRoom(LocalDateTime start, LocalDateTime end) {
        // Check if the driver has available time for a new load
        if (!isAvailable() && currentLoad != null) {
            return false;
        }
        
        // Calculate hours needed for the load
        long hoursNeeded = Duration.between(start, end).toHours() + 2; // Add buffer
        
        // Check hours of service limits
        if (hoursWorkedToday.get() + hoursNeeded > 14) {
            return false;
        }
        
        if (hoursWorkedWeek.get() + hoursNeeded > 70) {
            return false;
        }
        
        // Check available windows if they exist
        if (!availabilityWindows.isEmpty()) {
            for (TimeSlot window : availabilityWindows) {
                if (window.contains(start, end)) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }
    
    // Extended attributes (for additional custom data)
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) extendedAttributes.get(key);
    }
    
    public void setAttribute(String key, Object value) {
        extendedAttributes.put(key, value);
    }
    
    public boolean hasAttribute(String key) {
        return extendedAttributes.containsKey(key);
    }
    
    public Map<String, Object> getAllAttributes() {
        return new ConcurrentHashMap<>(extendedAttributes);
    }
    
    /**
     * Time slot class for availability windows
     */
    public static class TimeSlot {
        private final LocalDateTime start;
        private final LocalDateTime end;
        
        public TimeSlot(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
        
        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
        
        public boolean contains(LocalDateTime dateTime) {
            return !dateTime.isBefore(start) && !dateTime.isAfter(end);
        }
        
        public boolean contains(LocalDateTime checkStart, LocalDateTime checkEnd) {
            return !checkStart.isBefore(start) && !checkEnd.isAfter(end);
        }
        
        public boolean overlaps(TimeSlot other) {
            return !(other.end.isBefore(start) || other.start.isAfter(end));
        }
        
        public Duration getDuration() {
            return Duration.between(start, end);
        }
        
        @Override
        public String toString() {
            return String.format("%s to %s", 
                start.format(DATETIME_FORMAT), 
                end.format(DATETIME_FORMAT));
        }
    }
    
    /**
     * Create a copy of this status
     */
    public DispatcherDriverStatus copy() {
        DispatcherDriverStatus copy = new DispatcherDriverStatus(
            this.driver, this.status.get(), this.location.get(), this.eta.get(), this.notes.get());
            
        // Copy current properties
        copy.setTruckNumber(this.getTruckNumber());
        copy.setTrailerNumber(this.getTrailerNumber());
        copy.setCurrentLoad(this.getCurrentLoad());
        copy.setNextLoad(this.getNextLoad());
        copy.setHoursWorkedToday(this.getHoursWorkedToday());
        copy.setHoursWorkedWeek(this.getHoursWorkedWeek());
        copy.setConsecutiveDrivingDays(this.getConsecutiveDrivingDays());
        copy.setLastRestDateTime(this.getLastRestDateTime());
        copy.setCurrentCoordinates(this.getCurrentCoordinates());
        copy.setCurrentSpeed(this.getCurrentSpeed());
        copy.setHeading(this.getHeading());
        copy.setMoving(this.isMoving());
        
        // Copy lists
        if (this.assignedLoads != null) {
            copy.assignedLoads = new ArrayList<>(this.assignedLoads);
        }
        
        if (this.availabilityWindows != null) {
            copy.availabilityWindows = new ArrayList<>(this.availabilityWindows);
        }
        
        // Copy extended attributes
        copy.extendedAttributes = new ConcurrentHashMap<>(this.extendedAttributes);
        
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s%s", 
            driverName.get(), 
            truckNumber.get(), 
            status.get().getDisplayName(),
            currentLoad.get() != null ? " - Load #" + currentLoad.get().getLoadNumber() : "");
    }
}