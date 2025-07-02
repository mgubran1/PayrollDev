package com.company.payroll.geocoding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

/**
 * Calculate mileage between locations
 */
public class MileageCalculator {
    private static final Logger logger = LoggerFactory.getLogger(MileageCalculator.class);
    private static final double EARTH_RADIUS_MILES = 3959.0;
    private final GeocodingService geocodingService;
    
    public MileageCalculator(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }
    
    public double calculateDistance(String origin, String destination) {
        try {
            CompletableFuture<GeocodingService.Coordinates> originFuture = 
                geocodingService.geocode(origin);
            CompletableFuture<GeocodingService.Coordinates> destFuture = 
                geocodingService.geocode(destination);
            
            CompletableFuture<Double> distanceFuture = originFuture.thenCombine(destFuture, 
                (originCoords, destCoords) -> haversineDistance(originCoords, destCoords));
            
            return distanceFuture.get();
        } catch (Exception e) {
            logger.error("Failed to calculate distance between {} and {}", origin, destination, e);
            throw new RuntimeException("Distance calculation failed", e);
        }
    }
    
    private double haversineDistance(GeocodingService.Coordinates origin, 
                                   GeocodingService.Coordinates destination) {
        double lat1Rad = Math.toRadians(origin.latitude);
        double lat2Rad = Math.toRadians(destination.latitude);
        double deltaLat = Math.toRadians(destination.latitude - origin.latitude);
        double deltaLon = Math.toRadians(destination.longitude - origin.longitude);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_MILES * c * 1.2; // 1.2 factor for road distance
    }
}