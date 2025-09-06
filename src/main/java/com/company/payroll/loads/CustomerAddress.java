package com.company.payroll.loads;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

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
    private String customerName; // Customer name for display purposes
    
    // Zip code and geocoding fields
    private String zipCode;
    private double latitude;
    private double longitude;
    private LocalDateTime geocodedDate;
    private GeocodingStatus geocodingStatus = GeocodingStatus.PENDING;
    
    public enum GeocodingStatus {
        PENDING("Pending", "Geocoding not yet attempted"),
        GEOCODED("Geocoded", "Successfully geocoded"),
        FAILED("Failed", "Geocoding failed"),
        MANUAL("Manual", "Coordinates entered manually"),
        ESTIMATED("Estimated", "Coordinates estimated from region");
        
        private final String displayName;
        private final String description;
        
        GeocodingStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
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
    
    public String getZipCode() {
        return zipCode;
    }
    
    public void setZipCode(String zipCode) {
        this.zipCode = normalizeZipCode(zipCode);
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public LocalDateTime getGeocodedDate() {
        return geocodedDate;
    }
    
    public void setGeocodedDate(LocalDateTime geocodedDate) {
        this.geocodedDate = geocodedDate;
    }
    
    public GeocodingStatus getGeocodingStatus() {
        return geocodingStatus;
    }
    
    public void setGeocodingStatus(GeocodingStatus geocodingStatus) {
        this.geocodingStatus = geocodingStatus;
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
    
    public String getCustomerName() {
        return customerName;
    }
    
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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
        if (zipCode != null && !zipCode.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(zipCode);
        }
        return sb.toString();
    }
    
    // Get full address without zip for geocoding
    public String getFullAddressWithoutZip() {
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
    
    // Get full address with zip
    public String getFullAddressWithZip() {
        return getFullAddress();
    }
    
    // Zip code validation and utility methods
    public boolean isGeocodingRequired() {
        return geocodingStatus == GeocodingStatus.PENDING || 
               (geocodingStatus == GeocodingStatus.FAILED && zipCode != null && !zipCode.isEmpty());
    }
    
    public boolean isValidZipCode() {
        return isValidZipCode(zipCode);
    }
    
    public static boolean isValidZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            return false;
        }
        
        // Pattern for 5-digit or 5+4 digit zip codes
        Pattern pattern = Pattern.compile("^\\d{5}(-\\d{4})?$");
        return pattern.matcher(zipCode.trim()).matches();
    }
    
    private String normalizeZipCode(String zipCode) {
        if (zipCode == null) {
            return null;
        }
        return zipCode.trim().replaceAll("[^0-9-]", "");
    }
    
    public String getFormattedZipCode() {
        if (zipCode == null || zipCode.isEmpty()) {
            return "";
        }
        
        // Format as 5 digits or 5+4 format
        if (zipCode.length() == 9 && !zipCode.contains("-")) {
            return zipCode.substring(0, 5) + "-" + zipCode.substring(5);
        }
        
        return zipCode;
    }
    
    public boolean hasCoordinates() {
        return latitude != 0.0 && longitude != 0.0;
    }
    
    public String getGeocodingStatusDescription() {
        if (geocodingStatus == null) {
            return "Unknown";
        }
        return geocodingStatus.getDescription();
    }
    
    public void setCoordinates(double latitude, double longitude, GeocodingStatus status) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.geocodingStatus = status;
        this.geocodedDate = LocalDateTime.now();
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