package com.company.payroll.loads;

import java.util.Objects;

/**
 * Represents a unified customer address that can be used for both pickup and drop locations
 */
public class CustomerAddress {
    private int id;
    private int customerId;
    private String locationName; // Optional name for the location
    private String address;
    private String city;
    private String state;
    private boolean isDefaultPickup; // Flag to mark default pickup location
    private boolean isDefaultDrop; // Flag to mark default drop location
    
    public CustomerAddress() {
    }
    
    public CustomerAddress(int id, int customerId, String locationName, 
                          String address, String city, String state, 
                          boolean isDefaultPickup, boolean isDefaultDrop) {
        this.id = id;
        this.customerId = customerId;
        this.locationName = locationName;
        this.address = address;
        this.city = city;
        this.state = state;
        this.isDefaultPickup = isDefaultPickup;
        this.isDefaultDrop = isDefaultDrop;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    
    public boolean isDefaultPickup() {
        return isDefaultPickup;
    }
    
    public void setDefaultPickup(boolean isDefaultPickup) {
        this.isDefaultPickup = isDefaultPickup;
    }
    
    public boolean isDefaultDrop() {
        return isDefaultDrop;
    }
    
    public void setDefaultDrop(boolean isDefaultDrop) {
        this.isDefaultDrop = isDefaultDrop;
    }
    
    // Get full address as single line
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.trim().isEmpty()) {
            sb.append(address);
        }
        if (city != null && !city.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        return sb.toString();
    }
    
    // Display text for UI
    public String getDisplayText() {
        String fullAddr = getFullAddress();
        if (locationName != null && !locationName.trim().isEmpty()) {
            return locationName + " - " + fullAddr;
        }
        return fullAddr;
    }
    
    // Get default status indicators for UI
    public String getDefaultStatusText() {
        if (isDefaultPickup && isDefaultDrop) {
            return " (Default for Pickup & Drop)";
        } else if (isDefaultPickup) {
            return " (Default Pickup)";
        } else if (isDefaultDrop) {
            return " (Default Drop)";
        }
        return "";
    }
    
    @Override
    public String toString() {
        return getDisplayText();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerAddress that = (CustomerAddress) o;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}