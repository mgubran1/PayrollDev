package com.company.payroll.utils;

/** Simple latitude/longitude record. */
public class GeoCoordinates {
    private final double latitude;
    private final double longitude;

    public GeoCoordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
