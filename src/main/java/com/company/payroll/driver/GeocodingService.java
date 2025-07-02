package com.company.payroll.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service for geocoding addresses to coordinates
 */
public class GeocodingService {
    private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);
    private static final String NOMINATIM_API_URL = "https://nominatim.openstreetmap.org/search";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public GeocodingService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public static class Coordinates {
        public final double latitude;
        public final double longitude;
        
        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
    
    public CompletableFuture<Coordinates> geocode(String address) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
                String url = NOMINATIM_API_URL + "?q=" + encodedAddress + "&format=json&limit=1";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PayrollSystem/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    if (root.isArray() && root.size() > 0) {
                        JsonNode location = root.get(0);
                        double lat = location.get("lat").asDouble();
                        double lon = location.get("lon").asDouble();
                        return new Coordinates(lat, lon);
                    }
                }
                
                throw new RuntimeException("Failed to geocode address: " + address);
            } catch (Exception e) {
                logger.error("Geocoding failed for address: {}", address, e);
                throw new RuntimeException("Geocoding failed", e);
            }
        });
    }
}