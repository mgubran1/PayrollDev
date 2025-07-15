package com.company.payroll.loads;

import java.util.Objects;

public class CustomerLocation {
    private int id;
    private int customerId;
    private String locationType; // PICKUP or DROP
    private String locationName; // Optional name for the location
    private String address;
    private String city;
    private String state;
    private boolean isDefault; // Flag to mark default locations
    
    public CustomerLocation() {
    }
    
    public CustomerLocation(int id, int customerId, String locationType, String locationName, 
                          String address, String city, String state, boolean isDefault) {
        this.id = id;
        this.customerId = customerId;
        this.locationType = locationType;
        this.locationName = locationName;
        this.address = address;
        this.city = city;
        this.state = state;
        this.isDefault = isDefault;
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
    
    public String getLocationType() {
        return locationType;
    }
    
    public void setLocationType(String locationType) {
        this.locationType = locationType;
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
    
    
    public boolean isDefault() {
        return isDefault;
    }
    
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
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
    
    @Override
    public String toString() {
        return getDisplayText();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerLocation that = (CustomerLocation) o;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}