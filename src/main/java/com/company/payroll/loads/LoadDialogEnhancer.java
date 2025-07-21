package com.company.payroll.loads;

import com.company.payroll.employees.Employee;
import com.company.payroll.trailers.Trailer;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhances the Load Dialog with professional autocomplete functionality
 * for all fields including:
 * - Load Number (with validation and suggestions)
 * - PO Number (with history)
 * - Customer fields (with smart matching)
 * - Driver/Truck search (unified search)
 * - Trailer search
 * - Location autocomplete
 */
public class LoadDialogEnhancer {
    private static final Logger logger = LoggerFactory.getLogger(LoadDialogEnhancer.class);
    
    private final LoadDAO loadDAO;
    private final Map<String, List<String>> fieldHistory = new HashMap<>();
    private final Map<String, EnhancedAutocompleteField<?>> enhancedFields = new HashMap<>();
    
    public LoadDialogEnhancer(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
        loadFieldHistory();
    }
    
    /**
     * Enhance load number field with validation and smart suggestions
     */
    public EnhancedAutocompleteField<String> enhanceLoadNumberField(TextField originalField) {
        EnhancedAutocompleteField<String> enhanced = new EnhancedAutocompleteField<>();
        enhanced.setPromptText("Enter load number (e.g., LD-2024-001)");
        enhanced.setMinSearchLength(1);
        
        enhanced.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            List<EnhancedAutocompleteField.SearchResult<String>> results = new ArrayList<>();
            
            try {
                // Get recent load numbers
                List<String> recentLoads = loadDAO.getRecentLoadNumbers(20);
                
                // Smart suggestions based on pattern
                if (query.matches("\\d+")) {
                    // Just numbers - suggest formatted versions
                    String formatted = "LD-" + query;
                    results.add(new EnhancedAutocompleteField.SearchResult<>(
                        formatted, formatted, 100
                    ));
                    
                    // Suggest with current year
                    String withYear = "LD-2024-" + query;
                    results.add(new EnhancedAutocompleteField.SearchResult<>(
                        withYear, withYear, 90
                    ));
                }
                
                // Find similar load numbers
                for (String loadNum : recentLoads) {
                    double score = calculateSimilarity(query, loadNum);
                    if (score > 0) {
                        results.add(new EnhancedAutocompleteField.SearchResult<>(
                            loadNum, loadNum + " (existing)", score
                        ));
                    }
                }
                
                // Sort by score
                results.sort((a, b) -> Double.compare(b.score, a.score));
                
            } catch (Exception e) {
                logger.error("Error searching load numbers", e);
            }
            
            return results;
        }));
        
        enhanced.setDisplayFunction(s -> s);
        
        // Sync with original field
        enhanced.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                originalField.setText(newVal);
            }
        });
        
        return enhanced;
    }
    
    /**
     * Enhance PO field with history and suggestions
     */
    public EnhancedAutocompleteField<String> enhancePOField(TextField originalField) {
        EnhancedAutocompleteField<String> enhanced = new EnhancedAutocompleteField<>();
        enhanced.setPromptText("Enter PO number");
        enhanced.setMinSearchLength(1);
        
        enhanced.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            List<EnhancedAutocompleteField.SearchResult<String>> results = new ArrayList<>();
            
            try {
                // Get PO history
                List<String> poHistory = fieldHistory.getOrDefault("po_numbers", new ArrayList<>());
                
                // Search in history
                for (String po : poHistory) {
                    if (po.toLowerCase().contains(query.toLowerCase())) {
                        double score = calculateSimilarity(query, po);
                        results.add(new EnhancedAutocompleteField.SearchResult<>(
                            po, po, score
                        ));
                    }
                }
                
                // Get recent POs from database
                List<String> recentPOs = loadDAO.getRecentPONumbers(10);
                for (String po : recentPOs) {
                    if (po.toLowerCase().contains(query.toLowerCase())) {
                        double score = calculateSimilarity(query, po);
                        results.add(new EnhancedAutocompleteField.SearchResult<>(
                            po, po + " (recent)", score
                        ));
                    }
                }
                
                results.sort((a, b) -> Double.compare(b.score, a.score));
                
            } catch (Exception e) {
                logger.error("Error searching PO numbers", e);
            }
            
            return results;
        }));
        
        enhanced.setDisplayFunction(s -> s);
        
        // Sync with original field
        enhanced.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                originalField.setText(newVal);
                addToFieldHistory("po_numbers", newVal);
            }
        });
        
        return enhanced;
    }
    
    /**
     * Enhance customer combo box with intelligent search
     */
    public void enhanceCustomerComboBox(ComboBox<String> comboBox, ObservableList<String> allCustomers, boolean isPickup) {
        // Create enhanced autocomplete for the combo box editor
        TextField editor = comboBox.getEditor();
        if (editor == null) {
            comboBox.setEditable(true);
            editor = comboBox.getEditor();
        }
        
        EnhancedAutocompleteField<String> enhanced = new EnhancedAutocompleteField<>();
        enhanced.setPromptText(isPickup ? "Search pickup customers..." : "Search drop customers...");
        enhanced.setMinSearchLength(1);
        enhanced.setShowCategories(true);
        
        enhanced.setCategoryFunction(customer -> {
            // Categorize by first letter or frequency
            if (isFrequentCustomer(customer)) {
                return "Frequent Customers";
            }
            return "All Customers";
        });
        
        enhanced.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            List<EnhancedAutocompleteField.SearchResult<String>> results = new ArrayList<>();
            
            try {
                String searchLower = query.toLowerCase();
                
                // Search all customers
                for (String customer : allCustomers) {
                    if (customer == null) continue;
                    
                    String customerLower = customer.toLowerCase();
                    double score = 0;
                    
                    // Exact match
                    if (customerLower.equals(searchLower)) {
                        score = 100;
                    }
                    // Starts with
                    else if (customerLower.startsWith(searchLower)) {
                        score = 90;
                    }
                    // Word starts with
                    else if (Arrays.stream(customerLower.split("\\s+"))
                            .anyMatch(word -> word.startsWith(searchLower))) {
                        score = 80;
                    }
                    // Contains
                    else if (customerLower.contains(searchLower)) {
                        score = 70;
                    }
                    // Fuzzy match
                    else {
                        score = fuzzyMatch(searchLower, customerLower);
                    }
                    
                    if (score > 30) {
                        // Boost frequent customers
                        if (isFrequentCustomer(customer)) {
                            score += 10;
                        }
                        
                        results.add(new EnhancedAutocompleteField.SearchResult<>(
                            customer, customer, score
                        ));
                    }
                }
                
                // Sort by score
                results.sort((a, b) -> Double.compare(b.score, a.score));
                
            } catch (Exception e) {
                logger.error("Error searching customers", e);
            }
            
            return results.stream().limit(20).collect(Collectors.toList());
        }));
        
        enhanced.setDisplayFunction(s -> s);
        
        // Replace combo box editor with enhanced field
        HBox container = new HBox();
        container.getChildren().add(enhanced);
        HBox.setHgrow(enhanced, Priority.ALWAYS);
        
        // Sync selection
        enhanced.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                comboBox.setValue(newVal);
            }
        });
    }
    
    /**
     * Create unified driver/truck search field
     */
    public EnhancedAutocompleteField<Employee> createDriverTruckSearchField(
            ComboBox<Employee> driverBox, 
            TextField truckSearchField,
            ObservableList<Employee> drivers) {
        
        EnhancedAutocompleteField<Employee> enhanced = new EnhancedAutocompleteField<>();
        enhanced.setPromptText("Search by driver name or truck number...");
        enhanced.setMinSearchLength(1);
        enhanced.setShowCategories(true);
        
        enhanced.setCategoryFunction(emp -> {
            if (emp.getStatus() == Employee.Status.ACTIVE) {
                return "Active Drivers";
            } else {
                return "Inactive Drivers";
            }
        });
        
        enhanced.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            List<EnhancedAutocompleteField.SearchResult<Employee>> results = new ArrayList<>();
            
            try {
                String searchLower = query.toLowerCase();
                
                for (Employee driver : drivers) {
                    double score = 0;
                    String displayText = "";
                    
                    // Search by name
                    String nameLower = driver.getName().toLowerCase();
                    if (nameLower.contains(searchLower)) {
                        score = calculateSimilarity(searchLower, nameLower);
                        displayText = String.format("%s (Truck %s)", 
                            driver.getName(), 
                            driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A");
                    }
                    
                    // Search by truck unit
                    if (driver.getTruckUnit() != null) {
                        String truckLower = driver.getTruckUnit().toLowerCase();
                        if (truckLower.contains(searchLower)) {
                            double truckScore = calculateSimilarity(searchLower, truckLower) + 20;
                            if (truckScore > score) {
                                score = truckScore;
                                displayText = String.format("Truck %s - %s", 
                                    driver.getTruckUnit(), driver.getName());
                            }
                        }
                    }
                    
                    // Boost active drivers
                    if (driver.getStatus() == Employee.Status.ACTIVE) {
                        score += 15;
                    }
                    
                    if (score > 30) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("hasTrailer", driver.getTrailerNumber() != null);
                        metadata.put("status", driver.getStatus());
                        
                        results.add(new EnhancedAutocompleteField.SearchResult<>(
                            driver, displayText, score, false, metadata
                        ));
                    }
                }
                
                results.sort((a, b) -> Double.compare(b.score, a.score));
                
            } catch (Exception e) {
                logger.error("Error searching drivers", e);
            }
            
            return results.stream().limit(15).collect(Collectors.toList());
        }));
        
        enhanced.setDisplayFunction(emp -> 
            String.format("%s - Truck %s", 
                emp.getName(), 
                emp.getTruckUnit() != null ? emp.getTruckUnit() : "N/A")
        );
        
        // Sync with original fields
        enhanced.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                driverBox.setValue(newVal);
                if (newVal.getTruckUnit() != null) {
                    truckSearchField.setText(newVal.getTruckUnit());
                }
            }
        });
        
        return enhanced;
    }
    
    /**
     * Enhance trailer search with smart matching
     */
    public EnhancedAutocompleteField<Trailer> enhanceTrailerSearch(
            ComboBox<Trailer> trailerBox,
            TextField trailerSearchField,
            ObservableList<Trailer> trailers) {
        
        EnhancedAutocompleteField<Trailer> enhanced = new EnhancedAutocompleteField<>();
        enhanced.setPromptText("Search trailer by number or type...");
        enhanced.setMinSearchLength(1);
        
        enhanced.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            List<EnhancedAutocompleteField.SearchResult<Trailer>> results = new ArrayList<>();
            
            try {
                String searchLower = query.toLowerCase();
                
                for (Trailer trailer : trailers) {
                    double score = 0;
                    
                    // Search by trailer number
                    if (trailer.getTrailerNumber() != null) {
                        String numberLower = trailer.getTrailerNumber().toLowerCase();
                        if (numberLower.contains(searchLower)) {
                            score = calculateSimilarity(searchLower, numberLower) + 10;
                        }
                    }
                    
                    // Search by type
                    if (trailer.getType() != null) {
                        String typeLower = trailer.getType().toLowerCase();
                        if (typeLower.contains(searchLower)) {
                            double typeScore = calculateSimilarity(searchLower, typeLower);
                            score = Math.max(score, typeScore);
                        }
                    }
                    
                    if (score > 30) {
                        String displayText = String.format("%s - %s", 
                            trailer.getTrailerNumber(), trailer.getType());
                        
                        results.add(new EnhancedAutocompleteField.SearchResult<>(
                            trailer, displayText, score
                        ));
                    }
                }
                
                results.sort((a, b) -> Double.compare(b.score, a.score));
                
            } catch (Exception e) {
                logger.error("Error searching trailers", e);
            }
            
            return results.stream().limit(10).collect(Collectors.toList());
        }));
        
        enhanced.setDisplayFunction(t -> 
            String.format("%s - %s", t.getTrailerNumber(), t.getType())
        );
        
        // Sync with original fields
        enhanced.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                trailerBox.setValue(newVal);
                trailerSearchField.setText(newVal.getTrailerNumber());
            }
        });
        
        return enhanced;
    }
    
    // Helper methods
    private double calculateSimilarity(String query, String target) {
        if (query == null || target == null) return 0;
        
        query = query.toLowerCase();
        target = target.toLowerCase();
        
        // Exact match
        if (query.equals(target)) return 100;
        
        // Starts with
        if (target.startsWith(query)) return 90 - (target.length() - query.length());
        
        // Contains
        if (target.contains(query)) return 70 - (target.length() - query.length()) / 2;
        
        // Levenshtein distance
        int distance = levenshteinDistance(query, target);
        int maxLen = Math.max(query.length(), target.length());
        if (maxLen == 0) return 100;
        
        return Math.max(0, 100 - (distance * 100.0 / maxLen));
    }
    
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    private double fuzzyMatch(String query, String target) {
        // Simple fuzzy matching based on character overlap
        Set<Character> queryChars = query.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        Set<Character> targetChars = target.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        Set<Character> intersection = new HashSet<>(queryChars);
        intersection.retainAll(targetChars);
        
        if (queryChars.isEmpty()) return 0;
        
        return (intersection.size() * 100.0) / queryChars.size();
    }
    
    private boolean isFrequentCustomer(String customer) {
        // Check if customer appears frequently in recent loads
        try {
            int count = loadDAO.getCustomerLoadCount(customer, 30); // Last 30 days
            return count > 5;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void loadFieldHistory() {
        // Load field history from preferences or database
        // This would be implemented based on your persistence strategy
    }
    
    private void addToFieldHistory(String fieldName, String value) {
        List<String> history = fieldHistory.computeIfAbsent(fieldName, k -> new ArrayList<>());
        history.remove(value); // Remove if exists
        history.add(0, value); // Add to beginning
        
        // Keep only last 20 entries
        while (history.size() > 20) {
            history.remove(history.size() - 1);
        }
        
        // Save to preferences or database
        saveFieldHistory();
    }
    
    private void saveFieldHistory() {
        // Save field history to preferences or database
        // This would be implemented based on your persistence strategy
    }
}