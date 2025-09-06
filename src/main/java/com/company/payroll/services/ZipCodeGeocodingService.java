package com.company.payroll.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for geocoding zip codes to latitude/longitude coordinates.
 * Uses multiple fallback mechanisms for reliability.
 */
public class ZipCodeGeocodingService {
    private static final Logger logger = LoggerFactory.getLogger(ZipCodeGeocodingService.class);
    
    // Cache for geocoding results
    private static final Map<String, GeocodingResult> geocodeCache = new ConcurrentHashMap<>();
    
    // Rate limiting
    private static long lastApiCall = 0;
    private static final long MIN_TIME_BETWEEN_CALLS = 1000; // 1 second between API calls
    
    // API endpoints
    private static final String ZIPPOPOTAM_API = "http://api.zippopotam.us/us/";
    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search";
    
    // Timeout settings
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 10000; // 10 seconds
    
    // US State center coordinates for fallback
    private static final Map<String, double[]> STATE_CENTERS = new HashMap<>();
    
    static {
        // Initialize state center coordinates for fallback
        STATE_CENTERS.put("AL", new double[]{32.806671, -86.791130});
        STATE_CENTERS.put("AK", new double[]{61.370716, -152.404419});
        STATE_CENTERS.put("AZ", new double[]{33.729759, -111.431221});
        STATE_CENTERS.put("AR", new double[]{34.969704, -92.373123});
        STATE_CENTERS.put("CA", new double[]{36.116203, -119.681564});
        STATE_CENTERS.put("CO", new double[]{39.059811, -105.311104});
        STATE_CENTERS.put("CT", new double[]{41.597782, -72.755371});
        STATE_CENTERS.put("DE", new double[]{39.318523, -75.507141});
        STATE_CENTERS.put("FL", new double[]{27.766279, -81.686783});
        STATE_CENTERS.put("GA", new double[]{33.040619, -83.643074});
        STATE_CENTERS.put("HI", new double[]{21.094318, -157.498337});
        STATE_CENTERS.put("ID", new double[]{44.240459, -114.478828});
        STATE_CENTERS.put("IL", new double[]{40.349457, -88.986137});
        STATE_CENTERS.put("IN", new double[]{39.849426, -86.258278});
        STATE_CENTERS.put("IA", new double[]{42.011539, -93.210526});
        STATE_CENTERS.put("KS", new double[]{38.526600, -96.726486});
        STATE_CENTERS.put("KY", new double[]{37.668140, -84.670067});
        STATE_CENTERS.put("LA", new double[]{31.169546, -91.867805});
        STATE_CENTERS.put("ME", new double[]{44.693947, -69.381927});
        STATE_CENTERS.put("MD", new double[]{39.063946, -76.802101});
        STATE_CENTERS.put("MA", new double[]{42.230171, -71.530106});
        STATE_CENTERS.put("MI", new double[]{43.326618, -84.536095});
        STATE_CENTERS.put("MN", new double[]{45.694454, -93.900192});
        STATE_CENTERS.put("MS", new double[]{32.741646, -89.678696});
        STATE_CENTERS.put("MO", new double[]{38.456085, -92.288368});
        STATE_CENTERS.put("MT", new double[]{46.921925, -110.454353});
        STATE_CENTERS.put("NE", new double[]{41.492537, -99.901813});
        STATE_CENTERS.put("NV", new double[]{38.313515, -117.055374});
        STATE_CENTERS.put("NH", new double[]{43.452492, -71.563896});
        STATE_CENTERS.put("NJ", new double[]{40.298904, -74.521011});
        STATE_CENTERS.put("NM", new double[]{34.840515, -106.248482});
        STATE_CENTERS.put("NY", new double[]{42.165726, -74.948051});
        STATE_CENTERS.put("NC", new double[]{35.630066, -79.806419});
        STATE_CENTERS.put("ND", new double[]{47.528912, -99.784012});
        STATE_CENTERS.put("OH", new double[]{40.388783, -82.764915});
        STATE_CENTERS.put("OK", new double[]{35.565342, -96.928917});
        STATE_CENTERS.put("OR", new double[]{44.572021, -122.070938});
        STATE_CENTERS.put("PA", new double[]{40.590752, -77.209755});
        STATE_CENTERS.put("RI", new double[]{41.680893, -71.511780});
        STATE_CENTERS.put("SC", new double[]{33.856892, -80.945007});
        STATE_CENTERS.put("SD", new double[]{44.299782, -99.438828});
        STATE_CENTERS.put("TN", new double[]{35.747845, -86.692345});
        STATE_CENTERS.put("TX", new double[]{31.054487, -97.563461});
        STATE_CENTERS.put("UT", new double[]{40.150032, -111.862434});
        STATE_CENTERS.put("VT", new double[]{44.045876, -72.710686});
        STATE_CENTERS.put("VA", new double[]{37.769337, -78.169968});
        STATE_CENTERS.put("WA", new double[]{47.400902, -121.490494});
        STATE_CENTERS.put("WV", new double[]{38.491226, -80.954456});
        STATE_CENTERS.put("WI", new double[]{44.268543, -89.616508});
        STATE_CENTERS.put("WY", new double[]{42.755966, -107.302490});
    }
    
    /**
     * Geocode a zip code to get latitude and longitude.
     * @param zipCode The zip code to geocode
     * @return GeocodingResult with coordinates or null if failed
     */
    public GeocodingResult geocodeZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            return null;
        }
        
        // Normalize zip code
        String normalizedZip = zipCode.trim().replaceAll("[^0-9]", "");
        if (normalizedZip.length() < 5) {
            return null;
        }
        
        // Take only first 5 digits
        normalizedZip = normalizedZip.substring(0, 5);
        
        // Check cache first
        GeocodingResult cached = geocodeCache.get(normalizedZip);
        if (cached != null && cached.isValid()) {
            logger.debug("Returning cached geocoding result for zip {}", normalizedZip);
            return cached;
        }
        
        // Try primary geocoding service
        GeocodingResult result = geocodeWithZippopotam(normalizedZip);
        
        // If primary fails, try fallback
        if (result == null || !result.isValid()) {
            result = geocodeWithNominatim(normalizedZip);
        }
        
        // If both fail, use state center estimation
        if (result == null || !result.isValid()) {
            result = estimateFromState(normalizedZip);
        }
        
        // Cache the result
        if (result != null && result.isValid()) {
            geocodeCache.put(normalizedZip, result);
        }
        
        return result;
    }
    
    /**
     * Geocode using Zippopotam.us API.
     */
    private GeocodingResult geocodeWithZippopotam(String zipCode) {
        try {
            // Rate limiting
            enforceRateLimit();
            
            String apiUrl = ZIPPOPOTAM_API + zipCode;
            logger.debug("Geocoding zip {} with Zippopotam API", zipCode);
            
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "PayrollDesktop/1.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse JSON response
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.toString());
                
                if (root.has("places") && root.get("places").size() > 0) {
                    JsonNode place = root.get("places").get(0);
                    double lat = place.get("latitude").asDouble();
                    double lon = place.get("longitude").asDouble();
                    String city = place.get("place name").asText();
                    String state = place.get("state abbreviation").asText();
                    
                    logger.info("Successfully geocoded zip {} to {}, {} ({}, {})", 
                               zipCode, city, state, lat, lon);
                    
                    return new GeocodingResult(zipCode, lat, lon, city, state, "Zippopotam");
                }
            } else {
                logger.warn("Zippopotam API returned status {} for zip {}", responseCode, zipCode);
            }
            
        } catch (Exception e) {
            logger.error("Error geocoding zip {} with Zippopotam", zipCode, e);
        }
        
        return null;
    }
    
    /**
     * Geocode using Nominatim (OpenStreetMap) API as fallback.
     */
    private GeocodingResult geocodeWithNominatim(String zipCode) {
        try {
            // Rate limiting (Nominatim requires 1 second between requests)
            enforceRateLimit();
            
            String query = URLEncoder.encode(zipCode + ", USA", StandardCharsets.UTF_8.toString());
            String apiUrl = NOMINATIM_API + "?q=" + query + "&format=json&limit=1&countrycodes=us";
            logger.debug("Geocoding zip {} with Nominatim API", zipCode);
            
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "PayrollDesktop/1.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse JSON response
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.toString());
                
                if (root.isArray() && root.size() > 0) {
                    JsonNode result = root.get(0);
                    double lat = result.get("lat").asDouble();
                    double lon = result.get("lon").asDouble();
                    String displayName = result.get("display_name").asText();
                    
                    logger.info("Successfully geocoded zip {} with Nominatim to ({}, {})", 
                               zipCode, lat, lon);
                    
                    return new GeocodingResult(zipCode, lat, lon, displayName, "", "Nominatim");
                }
            } else {
                logger.warn("Nominatim API returned status {} for zip {}", responseCode, zipCode);
            }
            
        } catch (Exception e) {
            logger.error("Error geocoding zip {} with Nominatim", zipCode, e);
        }
        
        return null;
    }
    
    /**
     * Estimate coordinates based on state (zip code prefix).
     */
    private GeocodingResult estimateFromState(String zipCode) {
        try {
            // Use zip code prefix to determine state
            int prefix = Integer.parseInt(zipCode.substring(0, 3));
            String state = getStateFromZipPrefix(prefix);
            
            if (state != null && STATE_CENTERS.containsKey(state)) {
                double[] coords = STATE_CENTERS.get(state);
                logger.info("Estimating coordinates for zip {} based on state {}", zipCode, state);
                return new GeocodingResult(zipCode, coords[0], coords[1], 
                                         "Estimated", state, "State Center Estimate");
            }
        } catch (Exception e) {
            logger.error("Error estimating coordinates for zip {}", zipCode, e);
        }
        
        return null;
    }
    
    /**
     * Get state from zip code prefix.
     * This is a simplified mapping - real implementation would be more comprehensive.
     */
    private String getStateFromZipPrefix(int prefix) {
        // Simplified state mapping based on zip code prefixes
        if (prefix >= 10 && prefix <= 27) return "MA";
        if (prefix >= 28 && prefix <= 29) return "RI";
        if (prefix >= 30 && prefix <= 38) return "NH";
        if (prefix >= 39 && prefix <= 49) return "ME";
        if (prefix >= 50 && prefix <= 54) return "VT";
        if (prefix >= 55 && prefix <= 59) return "MA";
        if (prefix >= 60 && prefix <= 69) return "CT";
        if (prefix >= 70 && prefix <= 89) return "NJ";
        if (prefix >= 100 && prefix <= 149) return "NY";
        if (prefix >= 150 && prefix <= 196) return "PA";
        if (prefix >= 197 && prefix <= 199) return "DE";
        if (prefix >= 200 && prefix <= 212) return "DC";
        if (prefix >= 206 && prefix <= 219) return "MD";
        if (prefix >= 220 && prefix <= 246) return "VA";
        if (prefix >= 247 && prefix <= 268) return "WV";
        if (prefix >= 270 && prefix <= 289) return "NC";
        if (prefix >= 290 && prefix <= 299) return "SC";
        if (prefix >= 300 && prefix <= 319) return "GA";
        if (prefix >= 320 && prefix <= 339) return "FL";
        if (prefix >= 340 && prefix <= 347) return "FL";
        if (prefix >= 350 && prefix <= 369) return "AL";
        if (prefix >= 370 && prefix <= 385) return "TN";
        if (prefix >= 386 && prefix <= 397) return "MS";
        if (prefix >= 400 && prefix <= 427) return "KY";
        if (prefix >= 430 && prefix <= 458) return "OH";
        if (prefix >= 460 && prefix <= 479) return "IN";
        if (prefix >= 480 && prefix <= 499) return "MI";
        if (prefix >= 500 && prefix <= 528) return "IA";
        if (prefix >= 530 && prefix <= 549) return "WI";
        if (prefix >= 550 && prefix <= 567) return "MN";
        if (prefix >= 570 && prefix <= 577) return "SD";
        if (prefix >= 580 && prefix <= 588) return "ND";
        if (prefix >= 590 && prefix <= 599) return "MT";
        if (prefix >= 600 && prefix <= 629) return "IL";
        if (prefix >= 630 && prefix <= 658) return "MO";
        if (prefix >= 660 && prefix <= 679) return "KS";
        if (prefix >= 680 && prefix <= 693) return "NE";
        if (prefix >= 700 && prefix <= 714) return "LA";
        if (prefix >= 716 && prefix <= 729) return "AR";
        if (prefix >= 730 && prefix <= 749) return "OK";
        if (prefix >= 750 && prefix <= 799) return "TX";
        if (prefix >= 800 && prefix <= 816) return "CO";
        if (prefix >= 820 && prefix <= 831) return "WY";
        if (prefix >= 832 && prefix <= 838) return "ID";
        if (prefix >= 840 && prefix <= 847) return "UT";
        if (prefix >= 850 && prefix <= 865) return "AZ";
        if (prefix >= 870 && prefix <= 884) return "NM";
        if (prefix >= 889 && prefix <= 898) return "NV";
        if (prefix >= 900 && prefix <= 961) return "CA";
        if (prefix >= 967 && prefix <= 968) return "HI";
        if (prefix >= 970 && prefix <= 979) return "OR";
        if (prefix >= 980 && prefix <= 994) return "WA";
        if (prefix >= 995 && prefix <= 999) return "AK";
        
        return null;
    }
    
    /**
     * Enforce rate limiting between API calls.
     */
    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastCall = now - lastApiCall;
        
        if (timeSinceLastCall < MIN_TIME_BETWEEN_CALLS) {
            try {
                Thread.sleep(MIN_TIME_BETWEEN_CALLS - timeSinceLastCall);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        lastApiCall = System.currentTimeMillis();
    }
    
    /**
     * Clear the geocoding cache.
     */
    public void clearCache() {
        geocodeCache.clear();
        logger.info("Geocoding cache cleared");
    }
    
    /**
     * Get cache size.
     */
    public int getCacheSize() {
        return geocodeCache.size();
    }
    
    /**
     * Result of geocoding operation.
     */
    public static class GeocodingResult {
        private final String zipCode;
        private final double latitude;
        private final double longitude;
        private final String city;
        private final String state;
        private final String source;
        private final long timestamp;
        
        public GeocodingResult(String zipCode, double latitude, double longitude, 
                             String city, String state, String source) {
            this.zipCode = zipCode;
            this.latitude = latitude;
            this.longitude = longitude;
            this.city = city;
            this.state = state;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isValid() {
            return latitude != 0.0 && longitude != 0.0 && 
                   latitude >= -90 && latitude <= 90 && 
                   longitude >= -180 && longitude <= 180;
        }
        
        public boolean isExpired() {
            // Cache for 30 days
            return System.currentTimeMillis() - timestamp > TimeUnit.DAYS.toMillis(30);
        }
        
        // Getters
        public String getZipCode() { return zipCode; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public String getSource() { return source; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("GeocodingResult[%s: %.6f, %.6f (%s, %s) via %s]",
                               zipCode, latitude, longitude, city, state, source);
        }
    }
}
