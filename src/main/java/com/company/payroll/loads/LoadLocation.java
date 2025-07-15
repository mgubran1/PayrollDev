package com.company.payroll.loads;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a single pickup or drop location within a load
 */
public class LoadLocation {
    public enum LocationType { PICKUP, DROP }
    
    private int id;
    private int loadId;
    private LocationType type;
    private String customer;
    private String address;
    private String city;
    private String state;
    private LocalDate date;
    private LocalTime time;
    private String notes;
    private int sequence; // Order of locations (1, 2, 3, etc.)
    
    public LoadLocation(int id, int loadId, LocationType type, String customer, String address, String city, String state, 
                       LocalDate date, LocalTime time, String notes, int sequence) {
        this.id = id;
        this.loadId = loadId;
        this.type = type;
        this.customer = customer;
        this.address = address;
        this.city = city;
        this.state = state;
        this.date = date;
        this.time = time;
        this.notes = notes;
        this.sequence = sequence;
    }
    
    public LoadLocation(LocationType type, String customer, String address, String city, String state, 
                       LocalDate date, LocalTime time, String notes, int sequence) {
        this(0, 0, type, customer, address, city, state, date, time, notes, sequence);
    }
    
    // Legacy constructor for backward compatibility
    public LoadLocation(int id, int loadId, LocationType type, String address, String city, String state, 
                       LocalDate date, LocalTime time, String notes, int sequence) {
        this(id, loadId, type, "", address, city, state, date, time, notes, sequence);
    }
    
    // Legacy constructor for backward compatibility
    public LoadLocation(LocationType type, String address, String city, String state, 
                       LocalDate date, LocalTime time, String notes, int sequence) {
        this(0, 0, type, "", address, city, state, date, time, notes, sequence);
    }
    
    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getLoadId() { return loadId; }
    public void setLoadId(int loadId) { this.loadId = loadId; }
    
    public LocationType getType() { return type; }
    public void setType(LocationType type) { this.type = type; }
    
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    
    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    
    /**
     * Get the full location string (city, state)
     */
    public String getFullLocation() {
        if (city != null && state != null && !city.isEmpty() && !state.isEmpty()) {
            return city + ", " + state;
        } else if (city != null && !city.isEmpty()) {
            return city;
        } else if (state != null && !state.isEmpty()) {
            return state;
        } else {
            return address != null ? address : "";
        }
    }
    
    /**
     * Get the complete address string
     */
    public String getCompleteAddress() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isEmpty()) {
            sb.append(address);
        }
        if (city != null && !city.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("%s #%d: %s", type, sequence, getCompleteAddress());
    }
} 