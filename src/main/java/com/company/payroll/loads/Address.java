package com.company.payroll.loads;

/**
 * Address class for batch import operations
 */
public class Address {
    private String customerName;
    private String locationName;
    private String address;
    private String city;
    private String state;
    
    public Address() {
        // Default constructor
    }
    
    public Address(String customerName, String address, String city, String state) {
        this.customerName = customerName;
        this.address = address;
        this.city = city;
        this.state = state;
    }
    
    // Getters and Setters
    public String getCustomerName() {
        return customerName;
    }
    
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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
    
    @Override
    public String toString() {
        return "Address{" +
                "customerName='" + customerName + '\'' +
                ", locationName='" + locationName + '\'' +
                ", address='" + address + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
} 