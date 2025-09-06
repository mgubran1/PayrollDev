package com.company.payroll.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for calculating distances between zip codes using geocoded coordinates.
 * Uses the Haversine formula for great-circle distance calculations.
 */
public class DistanceCalculationService {
    private static final Logger logger = LoggerFactory.getLogger(DistanceCalculationService.class);
    
    // Earth's radius in miles
    private static final double EARTH_RADIUS_MILES = 3959.0;
    
    // Cache for distance calculations
    private static final Map<String, Double> distanceCache = new ConcurrentHashMap<>();
    
    // Maximum cache size
    private static final int MAX_CACHE_SIZE = 10000;
    
    private final ZipCodeGeocodingService geocodingService;
    
    public DistanceCalculationService() {
        this.geocodingService = new ZipCodeGeocodingService();
    }
    
    public DistanceCalculationService(ZipCodeGeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }
    
    /**
     * Calculate distance between two zip codes.
     * @param fromZip Origin zip code
     * @param toZip Destination zip code
     * @return Distance in miles, or -1 if calculation failed
     */
    public double calculateDistance(String fromZip, String toZip) {
        if (fromZip == null || toZip == null) {
            logger.warn("Cannot calculate distance with null zip codes");
            return -1;
        }
        
        // Normalize zip codes
        fromZip = normalizeZipCode(fromZip);
        toZip = normalizeZipCode(toZip);
        
        if (fromZip.isEmpty() || toZip.isEmpty()) {
            logger.warn("Cannot calculate distance with empty zip codes");
            return -1;
        }
        
        // If same zip code, distance is 0
        if (fromZip.equals(toZip)) {
            return 0.0;
        }
        
        // Check cache
        String cacheKey = createCacheKey(fromZip, toZip);
        Double cachedDistance = distanceCache.get(cacheKey);
        if (cachedDistance != null) {
            logger.debug("Returning cached distance for {} to {}: {} miles", 
                        fromZip, toZip, cachedDistance);
            return cachedDistance;
        }
        
        // Geocode both zip codes
        ZipCodeGeocodingService.GeocodingResult fromResult = geocodingService.geocodeZipCode(fromZip);
        ZipCodeGeocodingService.GeocodingResult toResult = geocodingService.geocodeZipCode(toZip);
        
        if (fromResult == null || !fromResult.isValid()) {
            logger.warn("Failed to geocode origin zip code: {}", fromZip);
            return -1;
        }
        
        if (toResult == null || !toResult.isValid()) {
            logger.warn("Failed to geocode destination zip code: {}", toZip);
            return -1;
        }
        
        // Calculate distance
        double distance = calculateHaversineDistance(
            fromResult.getLatitude(), fromResult.getLongitude(),
            toResult.getLatitude(), toResult.getLongitude()
        );
        
        // Add reasonable road factor (typically 15-20% more than straight line)
        distance = distance * 1.15;
        
        // Round to 1 decimal place
        distance = Math.round(distance * 10.0) / 10.0;
        
        logger.info("Calculated distance from {} to {}: {} miles", fromZip, toZip, distance);
        
        // Cache the result
        cacheDistance(cacheKey, distance);
        
        return distance;
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula.
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in miles
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // Convert to radians
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        double distance = EARTH_RADIUS_MILES * c;
        
        return distance;
    }
    
    /**
     * Calculate distances for multiple origin-destination pairs.
     * @param pairs Array of zip code pairs [fromZip, toZip]
     * @return Array of distances in same order as input
     */
    public double[] calculateBatchDistances(String[][] pairs) {
        if (pairs == null || pairs.length == 0) {
            return new double[0];
        }
        
        double[] distances = new double[pairs.length];
        
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i] != null && pairs[i].length >= 2) {
                distances[i] = calculateDistance(pairs[i][0], pairs[i][1]);
            } else {
                distances[i] = -1;
            }
        }
        
        return distances;
    }
    
    /**
     * Validate if a distance seems reasonable for the given zip codes.
     * @param fromZip Origin zip code
     * @param toZip Destination zip code
     * @param distance The distance to validate
     * @return true if distance seems reasonable
     */
    public boolean isReasonableDistance(String fromZip, String toZip, double distance) {
        if (distance < 0) {
            return false;
        }
        
        // Same zip code should have minimal distance
        if (normalizeZipCode(fromZip).equals(normalizeZipCode(toZip))) {
            return distance <= 10; // Allow up to 10 miles for same zip
        }
        
        // Check if states are different
        String fromState = getStateFromZip(fromZip);
        String toState = getStateFromZip(toZip);
        
        if (fromState != null && toState != null) {
            // Same state: typically under 500 miles
            if (fromState.equals(toState)) {
                return distance <= 500;
            }
            
            // Adjacent states: typically under 1000 miles
            if (areStatesAdjacent(fromState, toState)) {
                return distance <= 1000;
            }
            
            // Cross-country: up to 3000 miles
            return distance <= 3000;
        }
        
        // Default: any distance under 3500 miles is reasonable for US
        return distance <= 3500;
    }
    
    /**
     * Get estimated time for the distance (assuming average speed).
     * @param distance Distance in miles
     * @return Estimated time in hours
     */
    public double estimateDrivingTime(double distance) {
        if (distance <= 0) {
            return 0;
        }
        
        // Assume average speed based on distance
        double avgSpeed;
        if (distance < 50) {
            avgSpeed = 35; // City/local driving
        } else if (distance < 200) {
            avgSpeed = 50; // Mixed driving
        } else {
            avgSpeed = 60; // Highway driving
        }
        
        // Add time for stops/breaks (10 minutes per 100 miles)
        double drivingTime = distance / avgSpeed;
        double breakTime = (distance / 100) * (10.0 / 60.0);
        
        return Math.round((drivingTime + breakTime) * 10.0) / 10.0;
    }
    
    /**
     * Get a description of the distance.
     * @param distance Distance in miles
     * @return Human-readable description
     */
    public String getDistanceDescription(double distance) {
        if (distance < 0) {
            return "Unable to calculate";
        } else if (distance == 0) {
            return "Same location";
        } else if (distance < 50) {
            return String.format("%.1f miles (Local)", distance);
        } else if (distance < 200) {
            return String.format("%.1f miles (Regional)", distance);
        } else if (distance < 500) {
            return String.format("%.1f miles (Long distance)", distance);
        } else {
            return String.format("%.1f miles (Cross-country)", distance);
        }
    }
    
    /**
     * Normalize zip code for consistency.
     */
    private String normalizeZipCode(String zipCode) {
        if (zipCode == null) {
            return "";
        }
        
        // Remove all non-digits
        String normalized = zipCode.trim().replaceAll("[^0-9]", "");
        
        // Take only first 5 digits
        if (normalized.length() >= 5) {
            return normalized.substring(0, 5);
        }
        
        return normalized;
    }
    
    /**
     * Create cache key for distance lookup.
     */
    private String createCacheKey(String fromZip, String toZip) {
        // Order doesn't matter for distance, so normalize the key
        if (fromZip.compareTo(toZip) <= 0) {
            return fromZip + "-" + toZip;
        } else {
            return toZip + "-" + fromZip;
        }
    }
    
    /**
     * Cache a distance calculation.
     */
    private void cacheDistance(String key, double distance) {
        // Implement simple cache size limit
        if (distanceCache.size() >= MAX_CACHE_SIZE) {
            // Clear oldest entries (simple strategy - clear half the cache)
            distanceCache.clear();
            logger.info("Distance cache cleared due to size limit");
        }
        
        distanceCache.put(key, distance);
    }
    
    /**
     * Get state from zip code (simplified).
     */
    private String getStateFromZip(String zipCode) {
        ZipCodeGeocodingService.GeocodingResult result = geocodingService.geocodeZipCode(zipCode);
        if (result != null) {
            return result.getState();
        }
        return null;
    }
    
    /**
     * Check if two states are adjacent (simplified).
     */
    private boolean areStatesAdjacent(String state1, String state2) {
        // This is a simplified check - a real implementation would have
        // a complete adjacency matrix
        
        // Some example adjacent states
        if (state1.equals("NY")) {
            return "NJ,PA,CT,MA,VT".contains(state2);
        }
        if (state1.equals("CA")) {
            return "OR,NV,AZ".contains(state2);
        }
        if (state1.equals("TX")) {
            return "NM,OK,AR,LA".contains(state2);
        }
        
        // Default to false for simplicity
        return false;
    }
    
    /**
     * Clear the distance cache.
     */
    public void clearCache() {
        distanceCache.clear();
        logger.info("Distance cache cleared");
    }
    
    /**
     * Get cache size.
     */
    public int getCacheSize() {
        return distanceCache.size();
    }
    
    /**
     * Get statistics about distance calculations.
     */
    public DistanceStatistics getStatistics() {
        if (distanceCache.isEmpty()) {
            return new DistanceStatistics(0, 0, 0, 0);
        }
        
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        
        for (double distance : distanceCache.values()) {
            sum += distance;
            min = Math.min(min, distance);
            max = Math.max(max, distance);
        }
        
        double avg = sum / distanceCache.size();
        
        return new DistanceStatistics(distanceCache.size(), avg, min, max);
    }
    
    /**
     * Statistics about distance calculations.
     */
    public static class DistanceStatistics {
        private final int count;
        private final double average;
        private final double minimum;
        private final double maximum;
        
        public DistanceStatistics(int count, double average, double minimum, double maximum) {
            this.count = count;
            this.average = average;
            this.minimum = minimum;
            this.maximum = maximum;
        }
        
        public int getCount() { return count; }
        public double getAverage() { return average; }
        public double getMinimum() { return minimum; }
        public double getMaximum() { return maximum; }
        
        @Override
        public String toString() {
            return String.format("DistanceStatistics[count=%d, avg=%.1f, min=%.1f, max=%.1f]",
                               count, average, minimum, maximum);
        }
    }
}
