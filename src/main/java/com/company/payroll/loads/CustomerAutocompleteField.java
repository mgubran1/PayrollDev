package com.company.payroll.loads;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Professional customer autocomplete field with features similar to AddressAutocompleteField
 * - Real-time customer suggestions
 * - Intelligent fuzzy matching
 * - Recent customers highlighting
 * - Address preview for selected customer
 * - Keyboard navigation
 * - Performance optimized for large customer lists
 */
public class CustomerAutocompleteField extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(CustomerAutocompleteField.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // UI Components
    private final TextField customerField;
    private final Button addCustomerButton;
    private final Button viewAddressesButton;
    private final Popup suggestionPopup;
    private final VBox suggestionContainer;
    private final ListView<CustomerSuggestion> suggestionList;
    private final Label previewLabel;
    private final ProgressIndicator loadingIndicator;
    
    // Data
    private final ObservableList<CustomerSuggestion> suggestions = FXCollections.observableArrayList();
    private final ObjectProperty<String> selectedCustomer = new SimpleObjectProperty<>();
    private final StringProperty placeholderText = new SimpleStringProperty();
    private BiFunction<String, Boolean, CompletableFuture<List<CustomerInfo>>> customerProvider;
    private Consumer<String> onCustomerSelected;
    private LoadDAO loadDAO;
    private boolean isPickupCustomer;
    
    // Configuration
    private int maxSuggestions = 10;
    private int minCharsToShow = 1;
    private long debounceDelay = 200; // milliseconds
    private Timer debounceTimer;
    private boolean isShowingPopup = false;
    
    // Professional styles with black text
    private static final String FIELD_STYLE = "-fx-font-size: 14px; -fx-pref-width: 350px; -fx-text-fill: black; " +
                                              "-fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;";
    private static final String BUTTON_STYLE = "-fx-font-size: 12px; -fx-min-width: 30px; -fx-pref-width: 30px; " +
                                               "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-cursor: hand;";
    private static final String POPUP_STYLE = "-fx-background-color: white; -fx-border-color: #cccccc; " +
                                              "-fx-border-width: 1px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 2); -fx-text-fill: black;";
    
    public CustomerAutocompleteField(boolean isPickupCustomer, LoadDAO loadDAO) {
        this.isPickupCustomer = isPickupCustomer;
        this.loadDAO = loadDAO;
        
        // Initialize components
        customerField = new TextField();
        customerField.setPromptText(isPickupCustomer ? "Enter pickup customer..." : "Enter drop customer...");
        customerField.setStyle(FIELD_STYLE);
        
        addCustomerButton = new Button("➕");
        addCustomerButton.setTooltip(new Tooltip("Add new customer"));
        addCustomerButton.setStyle(BUTTON_STYLE + " -fx-background-color: #28a745; -fx-text-fill: white;");
        
        viewAddressesButton = new Button("📍");
        viewAddressesButton.setTooltip(new Tooltip("View customer addresses"));
        viewAddressesButton.setStyle(BUTTON_STYLE + " -fx-background-color: #17a2b8; -fx-text-fill: white;");
        viewAddressesButton.setDisable(true);
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(16, 16);
        loadingIndicator.setVisible(false);
        
        // Setup suggestion popup
        suggestionPopup = new Popup();
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        
        suggestionList = new ListView<>(suggestions);
        suggestionList.setPrefHeight(250);
        suggestionList.setMaxHeight(300);
        suggestionList.setPrefWidth(500);
        suggestionList.setCellFactory(listView -> new CustomerSuggestionCell());
        
        previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setPrefWidth(500);
        previewLabel.setPadding(new Insets(10));
        previewLabel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #e0e0e0; " +
                             "-fx-border-width: 0 0 1 0; -fx-font-size: 12px; -fx-text-fill: black;");
        
        suggestionContainer = new VBox();
        suggestionContainer.setStyle(POPUP_STYLE);
        
        // Header with loading indicator
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 10, 5, 10));
        Label headerLabel = new Label("Customer Search");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
        header.getChildren().addAll(headerLabel, loadingIndicator);
        
        suggestionContainer.getChildren().addAll(header, previewLabel, suggestionList);
        
        suggestionPopup.getContent().add(suggestionContainer);
        
        // Layout
        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(customerField, addCustomerButton, viewAddressesButton);
        HBox.setHgrow(customerField, Priority.ALWAYS);
        
        // Setup event handlers
        setupEventHandlers();
        setupKeyboardNavigation();
    }
    
    private void setupEventHandlers() {
        // Text change handler with debouncing
        customerField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            
            if (newVal == null || newVal.trim().isEmpty()) {
                hidePopup();
                selectedCustomer.set(null);
                viewAddressesButton.setDisable(true);
                return;
            }
            
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> searchCustomers(newVal));
                }
            }, debounceDelay);
        });
        
        // Focus handling
        customerField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && !suggestionList.isFocused()) {
                hidePopup();
            }
        });
        
        // Selection handling
        suggestionList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updatePreview(newVal);
            }
        });
        
        // Mouse click selection
        suggestionList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                CustomerSuggestion selected = suggestionList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectCustomer(selected);
                }
            }
        });
        
        // Button handlers
        addCustomerButton.setOnAction(e -> {
            String customerName = customerField.getText().trim();
            if (!customerName.isEmpty()) {
                addNewCustomer(customerName);
            }
        });
        
        viewAddressesButton.setOnAction(e -> {
            if (selectedCustomer.get() != null) {
                showCustomerAddresses(selectedCustomer.get());
            }
        });
    }
    
    private void setupKeyboardNavigation() {
        customerField.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (!isShowingPopup) return;
            
            switch (event.getCode()) {
                case DOWN:
                    event.consume();
                    selectNextSuggestion();
                    break;
                case UP:
                    event.consume();
                    selectPreviousSuggestion();
                    break;
                case ENTER:
                    event.consume();
                    if (suggestionList.getSelectionModel().getSelectedItem() != null) {
                        selectCustomer(suggestionList.getSelectionModel().getSelectedItem());
                    } else if (!customerField.getText().trim().isEmpty()) {
                        // Add new customer if no selection
                        addNewCustomer(customerField.getText().trim());
                    }
                    break;
                case ESCAPE:
                    event.consume();
                    hidePopup();
                    break;
                case TAB:
                    if (suggestionList.getSelectionModel().getSelectedItem() != null) {
                        selectCustomer(suggestionList.getSelectionModel().getSelectedItem());
                    }
                    break;
            }
        });
    }
    
    private void searchCustomers(String query) {
        if (customerProvider == null) {
            logger.warn("No customer provider set");
            return;
        }
        
        loadingIndicator.setVisible(true);
        
        CompletableFuture<List<CustomerInfo>> future = customerProvider.apply(query.trim(), isPickupCustomer);
        
        future.thenAccept(new Consumer<List<CustomerInfo>>() {
            @Override
            public void accept(List<CustomerInfo> customers) {
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    if (customers != null && !customers.isEmpty()) {
                        showSuggestions(rankCustomers(customers, query));
                    } else {
                        showNoResults(query);
                    }
                });
            }
        });
        
        future.exceptionally(throwable -> {
            logger.error("Error searching customers", throwable);
            Platform.runLater(() -> {
                loadingIndicator.setVisible(false);
                hidePopup();
            });
            return new ArrayList<>();
        });
    }
    
    private List<CustomerSuggestion> rankCustomers(List<CustomerInfo> customers, String query) {
        String normalizedQuery = normalizeString(query);
        
        return customers.stream()
            .map(customer -> {
                double score = calculateScore(customer, normalizedQuery);
                return new CustomerSuggestion(customer, score);
            })
            .filter(suggestion -> suggestion.score > 0)
            .sorted(Comparator.comparingDouble((CustomerSuggestion s) -> s.score).reversed())
            .limit(maxSuggestions)
            .collect(Collectors.toList());
    }
    
    private double calculateScore(CustomerInfo customer, String query) {
        double score = 0;
        String normalizedName = normalizeString(customer.name);
        
        // Exact match bonus
        if (normalizedName.equals(query)) {
            score += 100;
        }
        
        // Starts with bonus
        if (normalizedName.startsWith(query)) {
            score += 80;
        }
        
        // Contains match
        if (normalizedName.contains(query)) {
            score += 50;
        }
        
        // Word-by-word matching
        String[] queryWords = query.split("\\s+");
        String[] nameWords = normalizedName.split("\\s+");
        
        for (String queryWord : queryWords) {
            for (String nameWord : nameWords) {
                if (nameWord.startsWith(queryWord)) {
                    score += 30;
                } else if (nameWord.contains(queryWord)) {
                    score += 20;
                }
            }
        }
        
        // Recent customer bonus
        if (customer.isRecent) {
            score += 25;
        }
        
        // Has addresses bonus
        if (customer.addressCount > 0) {
            score += 10 + Math.min(customer.addressCount, 5) * 2;
        }
        
        // Default customer bonus
        if (isPickupCustomer && customer.hasDefaultPickup) {
            score += 20;
        } else if (!isPickupCustomer && customer.hasDefaultDrop) {
            score += 20;
        }
        
        return score;
    }
    
    private String normalizeString(String str) {
        if (str == null) return "";
        return str.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }
    
    private void showSuggestions(List<CustomerSuggestion> customerSuggestions) {
        suggestions.setAll(customerSuggestions);
        
        if (!suggestions.isEmpty()) {
            if (!isShowingPopup) {
                showPopup();
            }
            suggestionList.getSelectionModel().select(0);
            updatePreview(suggestions.get(0));
        } else {
            hidePopup();
        }
    }
    
    private void showNoResults(String query) {
        suggestions.clear();
        previewLabel.setText("No customers found for \"" + query + "\". Press Enter to add as new customer.");
        
        if (!isShowingPopup) {
            showPopup();
        }
    }
    
    private void updatePreview(CustomerSuggestion suggestion) {
        if (suggestion == null || suggestion.customer == null) {
            previewLabel.setText("");
            return;
        }
        
        CustomerInfo customer = suggestion.customer;
        StringBuilder preview = new StringBuilder();
        
        preview.append("👤 ").append(customer.name);
        
        if (customer.isRecent) {
            preview.append(" ⭐ Recent Customer");
        }
        
        if (customer.addressCount > 0) {
            preview.append("\n📍 ").append(customer.addressCount).append(" saved address");
            if (customer.addressCount > 1) preview.append("es");
        }
        
        if (isPickupCustomer && customer.hasDefaultPickup) {
            preview.append("\n✓ Has default pickup location");
        } else if (!isPickupCustomer && customer.hasDefaultDrop) {
            preview.append("\n✓ Has default drop location");
        }
        
        if (customer.lastLoadDate != null) {
            preview.append("\n📅 Last load: ").append(customer.lastLoadDate);
        }
        
        previewLabel.setText(preview.toString());
    }
    
    private void showPopup() {
        try {
            Bounds bounds = customerField.localToScreen(customerField.getBoundsInLocal());
            if (bounds != null) {
                suggestionPopup.show(customerField, bounds.getMinX(), bounds.getMaxY() + 2);
                isShowingPopup = true;
            }
        } catch (Exception e) {
            logger.error("Error showing popup", e);
        }
    }
    
    private void hidePopup() {
        if (isShowingPopup) {
            suggestionPopup.hide();
            isShowingPopup = false;
            suggestions.clear();
            previewLabel.setText("");
        }
    }
    
    private void selectNextSuggestion() {
        int current = suggestionList.getSelectionModel().getSelectedIndex();
        int next = (current + 1) % suggestions.size();
        suggestionList.getSelectionModel().select(next);
        suggestionList.scrollTo(next);
    }
    
    private void selectPreviousSuggestion() {
        int current = suggestionList.getSelectionModel().getSelectedIndex();
        int prev = current <= 0 ? suggestions.size() - 1 : current - 1;
        suggestionList.getSelectionModel().select(prev);
        suggestionList.scrollTo(prev);
    }
    
    private void selectCustomer(CustomerSuggestion suggestion) {
        if (suggestion != null && suggestion.customer != null) {
            customerField.setText(suggestion.customer.name);
            selectedCustomer.set(suggestion.customer.name);
            viewAddressesButton.setDisable(false);
            hidePopup();
            
            if (onCustomerSelected != null) {
                onCustomerSelected.accept(suggestion.customer.name);
            }
        }
    }
    
    private void addNewCustomer(String customerName) {
        try {
            loadDAO.addCustomerIfNotExists(customerName);
            customerField.setText(customerName);
            selectedCustomer.set(customerName);
            viewAddressesButton.setDisable(false);
            hidePopup();
            
            if (onCustomerSelected != null) {
                onCustomerSelected.accept(customerName);
            }
            
            // Show success notification
            showNotification("Customer \"" + customerName + "\" added successfully!");
        } catch (Exception e) {
            logger.error("Error adding customer", e);
            showNotification("Error adding customer: " + e.getMessage());
        }
    }
    
    private void showCustomerAddresses(String customerName) {
        // This would open a dialog showing all addresses for the customer
        logger.info("Show addresses for customer: {}", customerName);
    }
    
    private void showNotification(String message) {
        // Simple notification - in production, use a proper notification system
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Customer Management");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Public API
    public void setCustomerProvider(BiFunction<String, Boolean, CompletableFuture<List<CustomerInfo>>> provider) {
        this.customerProvider = provider;
    }
    
    public void setOnCustomerSelected(Consumer<String> handler) {
        this.onCustomerSelected = handler;
    }
    
    public String getSelectedCustomer() {
        return selectedCustomer.get();
    }
    
    public ObjectProperty<String> selectedCustomerProperty() {
        return selectedCustomer;
    }
    
    public void setCustomer(String customer) {
        if (customer != null && !customer.isEmpty()) {
            customerField.setText(customer);
            selectedCustomer.set(customer);
            viewAddressesButton.setDisable(false);
        } else {
            customerField.clear();
            selectedCustomer.set(null);
            viewAddressesButton.setDisable(true);
        }
    }
    
    public TextField getCustomerField() {
        return customerField;
    }
    
    public void setPromptText(String text) {
        customerField.setPromptText(text);
    }
    
    public void setMaxSuggestions(int max) {
        this.maxSuggestions = max;
    }
    
    public void setMinCharsToShow(int min) {
        this.minCharsToShow = min;
    }
    
    public void setDebounceDelay(long delay) {
        this.debounceDelay = delay;
    }
    
    // Inner classes
    public static class CustomerInfo {
        public final String name;
        public final int addressCount;
        public final boolean hasDefaultPickup;
        public final boolean hasDefaultDrop;
        public final boolean isRecent;
        public final String lastLoadDate;
        
        public CustomerInfo(String name, int addressCount, boolean hasDefaultPickup, 
                          boolean hasDefaultDrop, boolean isRecent, String lastLoadDate) {
            this.name = name;
            this.addressCount = addressCount;
            this.hasDefaultPickup = hasDefaultPickup;
            this.hasDefaultDrop = hasDefaultDrop;
            this.isRecent = isRecent;
            this.lastLoadDate = lastLoadDate;
        }
    }
    
    private static class CustomerSuggestion {
        final CustomerInfo customer;
        final double score;
        
        CustomerSuggestion(CustomerInfo customer, double score) {
            this.customer = customer;
            this.score = score;
        }
    }
    
    private class CustomerSuggestionCell extends ListCell<CustomerSuggestion> {
        private final HBox container;
        private final VBox textContainer;
        private final Label nameLabel;
        private final Label detailsLabel;
        private final Label scoreLabel;
        
        public CustomerSuggestionCell() {
            container = new HBox(10);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(5, 10, 5, 10));
            
            textContainer = new VBox(2);
            
            nameLabel = new Label();
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
            nameLabel.setTextFill(Color.BLACK);
            
            detailsLabel = new Label();
            detailsLabel.setFont(Font.font("System", 11));
            detailsLabel.setTextFill(Color.DARKGRAY);
            
            scoreLabel = new Label();
            scoreLabel.setFont(Font.font("System", 10));
            scoreLabel.setTextFill(Color.LIGHTGRAY);
            scoreLabel.setVisible(false); // Hide in production
            
            textContainer.getChildren().addAll(nameLabel, detailsLabel);
            container.getChildren().addAll(textContainer, scoreLabel);
            HBox.setHgrow(textContainer, Priority.ALWAYS);
        }
        
        @Override
        protected void updateItem(CustomerSuggestion item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.customer == null) {
                setGraphic(null);
            } else {
                CustomerInfo customer = item.customer;
                
                // Name with indicators
                StringBuilder nameText = new StringBuilder(customer.name);
                if (customer.isRecent) {
                    nameText.append(" ⭐");
                }
                nameLabel.setText(nameText.toString());
                
                // Details
                List<String> details = new ArrayList<>();
                if (customer.addressCount > 0) {
                    details.add(customer.addressCount + " address" + (customer.addressCount > 1 ? "es" : ""));
                }
                if (isPickupCustomer && customer.hasDefaultPickup) {
                    details.add("Default pickup");
                } else if (!isPickupCustomer && customer.hasDefaultDrop) {
                    details.add("Default drop");
                }
                detailsLabel.setText(String.join(" • ", details));
                
                // Highlight recent customers with professional styling
                if (customer.isRecent) {
                    container.setStyle("-fx-background-color: #FFF9E6; -fx-border-color: #FFD700; -fx-border-width: 0 0 0 3; -fx-text-fill: black;");
                } else {
                    container.setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
                }
                
                // Score for debugging
                scoreLabel.setText(String.format("%.1f", item.score));
                
                setGraphic(container);
            }
        }
    }
    
    public static void shutdown() {
        executorService.shutdown();
    }
}