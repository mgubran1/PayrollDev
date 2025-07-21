package com.company.payroll.loads;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import java.util.stream.Collectors;

/**
 * Robust address autocomplete field that integrates with customer address book
 * Features:
 * - Real-time address suggestions from customer address book
 * - Intelligent fuzzy matching with ranking
 * - Address preview with all details
 * - Quick selection with keyboard navigation
 * - Support for both pickup and drop addresses
 * - Integration with default address preferences
 * - Performance optimized for large address datasets
 */
public class AddressAutocompleteField extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(AddressAutocompleteField.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // UI Components
    private final TextField addressField;
    private final Button addressBookButton;
    private final Button manualEntryButton;
    private final Popup suggestionPopup;
    private final VBox suggestionContainer;
    private final ListView<AddressSuggestion> suggestionList;
    private final Label previewLabel;
    
    // Data
    private final ObservableList<AddressSuggestion> suggestions = FXCollections.observableArrayList();
    private final ObjectProperty<CustomerAddress> selectedAddress = new SimpleObjectProperty<>();
    private BiFunction<String, String, CompletableFuture<List<CustomerAddress>>> addressProvider;
    private Consumer<CustomerAddress> onAddressSelected;
    private String currentCustomer;
    private boolean isPickupAddress;
    
    // Configuration
    private int maxSuggestions = 8;
    private int minCharsToShow = 2;
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
    
    public AddressAutocompleteField(boolean isPickupAddress) {
        this.isPickupAddress = isPickupAddress;
        
        // Initialize components
        addressField = new TextField();
        addressField.setPromptText(isPickupAddress ? "Enter pickup address..." : "Enter drop address...");
        addressField.setStyle(FIELD_STYLE);
        
        addressBookButton = new Button("📖");
        addressBookButton.setTooltip(new Tooltip("Browse address book"));
        addressBookButton.setStyle(BUTTON_STYLE + " -fx-background-color: #4CAF50; -fx-text-fill: white;");
        
        manualEntryButton = new Button("✏");
        manualEntryButton.setTooltip(new Tooltip("Add new address"));
        manualEntryButton.setStyle(BUTTON_STYLE + " -fx-background-color: #FF9800; -fx-text-fill: white;");
        
        // Setup suggestion popup
        suggestionPopup = new Popup();
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        
        suggestionList = new ListView<>(suggestions);
        suggestionList.setPrefHeight(250);
        suggestionList.setMaxHeight(250);
        suggestionList.setPrefWidth(500);
        suggestionList.setCellFactory(listView -> new AddressSuggestionCell());
        
        previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setPrefWidth(500);
        previewLabel.setPadding(new Insets(10));
        previewLabel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #e0e0e0; " +
                             "-fx-border-width: 0 0 1 0; -fx-font-size: 12px;");
        
        suggestionContainer = new VBox();
        suggestionContainer.setStyle(POPUP_STYLE);
        suggestionContainer.getChildren().addAll(previewLabel, suggestionList);
        
        suggestionPopup.getContent().add(suggestionContainer);
        
        // Layout
        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(addressField, addressBookButton, manualEntryButton);
        
        // Setup event handlers
        setupEventHandlers();
        setupKeyboardNavigation();
    }
    
    private void setupEventHandlers() {
        // Text change handler with debouncing
        addressField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            
            if (newVal == null || newVal.trim().isEmpty()) {
                hidePopup();
                selectedAddress.set(null);
                return;
            }
            
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> searchAddresses(newVal));
                }
            }, debounceDelay);
        });
        
        // Focus handling
        addressField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !suggestionList.isFocused()) {
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
                AddressSuggestion selected = suggestionList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectAddress(selected);
                }
            }
        });
        
        // Button handlers
        addressBookButton.setOnAction(e -> showAddressBookDialog());
        manualEntryButton.setOnAction(e -> showManualEntryDialog());
    }
    
    private void setupKeyboardNavigation() {
        addressField.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
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
                        selectAddress(suggestionList.getSelectionModel().getSelectedItem());
                    }
                    break;
                case ESCAPE:
                    event.consume();
                    hidePopup();
                    break;
                case TAB:
                    if (suggestionList.getSelectionModel().getSelectedItem() != null) {
                        event.consume();
                        selectAddress(suggestionList.getSelectionModel().getSelectedItem());
                    }
                    break;
            }
        });
    }
    
    private void searchAddresses(String query) {
        if (addressProvider == null || currentCustomer == null || query.trim().isEmpty()) {
            hidePopup();
            return;
        }
        
        addressProvider.apply(currentCustomer, query.trim())
            .thenAcceptAsync(addresses -> {
                Platform.runLater(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        showSuggestions(rankAddresses(addresses, query));
                    } else {
                        hidePopup();
                    }
                });
            }, executorService)
            .exceptionally(throwable -> {
                logger.error("Error searching addresses", throwable);
                Platform.runLater(this::hidePopup);
                return null;
            });
    }
    
    private List<AddressSuggestion> rankAddresses(List<CustomerAddress> addresses, String query) {
        String normalizedQuery = normalizeString(query);
        
        return addresses.stream()
            .map(addr -> new AddressSuggestion(addr, calculateScore(addr, normalizedQuery)))
            .filter(suggestion -> suggestion.score > 0)
            .sorted(Comparator.comparingDouble((AddressSuggestion s) -> s.score).reversed())
            .limit(maxSuggestions)
            .collect(Collectors.toList());
    }
    
    private double calculateScore(CustomerAddress address, String query) {
        double score = 0;
        
        // Build searchable text
        String searchText = buildSearchableText(address);
        String normalizedText = normalizeString(searchText);
        
        // Exact match bonus
        if (normalizedText.equals(query)) {
            score += 100;
        }
        
        // Starts with bonus
        if (normalizedText.startsWith(query)) {
            score += 50;
        }
        
        // Contains match
        if (normalizedText.contains(query)) {
            score += 30;
        }
        
        // Word-by-word matching
        String[] queryWords = query.split("\\s+");
        String[] textWords = normalizedText.split("\\s+");
        
        for (String queryWord : queryWords) {
            for (String textWord : textWords) {
                if (textWord.startsWith(queryWord)) {
                    score += 20;
                } else if (textWord.contains(queryWord)) {
                    score += 10;
                }
            }
        }
        
        // Default address bonus
        if (isPickupAddress && address.isDefaultPickup()) {
            score += 25;
        } else if (!isPickupAddress && address.isDefaultDrop()) {
            score += 25;
        }
        
        // Location name bonus
        if (address.getLocationName() != null && !address.getLocationName().isEmpty()) {
            score += 15;
        }
        
        // Penalize based on string length difference
        int lengthDiff = Math.abs(normalizedText.length() - query.length());
        score -= lengthDiff * 0.5;
        
        return Math.max(0, score);
    }
    
    private String buildSearchableText(CustomerAddress address) {
        StringBuilder sb = new StringBuilder();
        
        if (address.getLocationName() != null) {
            sb.append(address.getLocationName()).append(" ");
        }
        if (address.getAddress() != null) {
            sb.append(address.getAddress()).append(" ");
        }
        if (address.getCity() != null) {
            sb.append(address.getCity()).append(" ");
        }
        if (address.getState() != null) {
            sb.append(address.getState()).append(" ");
        }
        
        return sb.toString().trim();
    }
    
    private String normalizeString(String str) {
        if (str == null) return "";
        return str.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }
    
    private void showSuggestions(List<AddressSuggestion> addressSuggestions) {
        suggestions.setAll(addressSuggestions);
        
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
    
    private void updatePreview(AddressSuggestion suggestion) {
        if (suggestion == null || suggestion.address == null) {
            previewLabel.setText("");
            return;
        }
        
        CustomerAddress addr = suggestion.address;
        StringBuilder preview = new StringBuilder();
        
        if (addr.getLocationName() != null && !addr.getLocationName().isEmpty()) {
            preview.append("📍 ").append(addr.getLocationName()).append("\n");
        }
        
        preview.append(formatAddress(addr));
        
        if (isPickupAddress && addr.isDefaultPickup()) {
            preview.append("\n⭐ Default Pickup Location");
        } else if (!isPickupAddress && addr.isDefaultDrop()) {
            preview.append("\n⭐ Default Drop Location");
        }
        
        previewLabel.setText(preview.toString());
    }
    
    private String formatAddress(CustomerAddress addr) {
        StringBuilder formatted = new StringBuilder();
        
        if (addr.getAddress() != null && !addr.getAddress().isEmpty()) {
            formatted.append(addr.getAddress());
        }
        
        if (addr.getCity() != null && !addr.getCity().isEmpty()) {
            if (formatted.length() > 0) formatted.append(", ");
            formatted.append(addr.getCity());
        }
        
        if (addr.getState() != null && !addr.getState().isEmpty()) {
            if (formatted.length() > 0) formatted.append(", ");
            formatted.append(addr.getState());
        }
        
        return formatted.toString();
    }
    
    private void showPopup() {
        try {
            Bounds bounds = addressField.localToScreen(addressField.getBoundsInLocal());
            if (bounds != null) {
                suggestionPopup.show(addressField, bounds.getMinX(), bounds.getMaxY() + 2);
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
    
    private void selectAddress(AddressSuggestion suggestion) {
        if (suggestion != null && suggestion.address != null) {
            CustomerAddress addr = suggestion.address;
            addressField.setText(formatAddress(addr));
            selectedAddress.set(addr);
            hidePopup();
            
            if (onAddressSelected != null) {
                onAddressSelected.accept(addr);
            }
        }
    }
    
    private void showAddressBookDialog() {
        // Implementation for address book dialog
        // This would show a dialog with all addresses for the current customer
        logger.info("Address book dialog requested for customer: {}", currentCustomer);
    }
    
    private void showManualEntryDialog() {
        // Implementation for manual address entry dialog
        // This would show a dialog to enter a new address
        logger.info("Manual address entry dialog requested");
    }
    
    // Public API
    public void setAddressProvider(BiFunction<String, String, CompletableFuture<List<CustomerAddress>>> provider) {
        this.addressProvider = provider;
    }
    
    public void setOnAddressSelected(Consumer<CustomerAddress> handler) {
        this.onAddressSelected = handler;
    }
    
    public void setCurrentCustomer(String customer) {
        this.currentCustomer = customer;
        addressField.clear();
        selectedAddress.set(null);
    }
    
    public CustomerAddress getSelectedAddress() {
        return selectedAddress.get();
    }
    
    public ObjectProperty<CustomerAddress> selectedAddressProperty() {
        return selectedAddress;
    }
    
    public void setAddress(CustomerAddress address) {
        if (address != null) {
            addressField.setText(formatAddress(address));
            selectedAddress.set(address);
        } else {
            addressField.clear();
            selectedAddress.set(null);
        }
    }
    
    public TextField getAddressField() {
        return addressField;
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
    private static class AddressSuggestion {
        final CustomerAddress address;
        final double score;
        
        AddressSuggestion(CustomerAddress address, double score) {
            this.address = address;
            this.score = score;
        }
    }
    
    private class AddressSuggestionCell extends ListCell<AddressSuggestion> {
        private final VBox container;
        private final Label nameLabel;
        private final Label addressLabel;
        private final Label scoreLabel;
        
        public AddressSuggestionCell() {
            container = new VBox(2);
            container.setPadding(new Insets(5, 10, 5, 10));
            
            nameLabel = new Label();
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
            nameLabel.setTextFill(Color.BLACK);
            
            addressLabel = new Label();
            addressLabel.setFont(Font.font("System", 12));
            addressLabel.setTextFill(Color.DARKGRAY);
            
            scoreLabel = new Label();
            scoreLabel.setFont(Font.font("System", 10));
            scoreLabel.setTextFill(Color.GRAY);
            scoreLabel.setVisible(false); // Hide score in production
            
            container.getChildren().addAll(nameLabel, addressLabel);
        }
        
        @Override
        protected void updateItem(AddressSuggestion item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.address == null) {
                setGraphic(null);
            } else {
                CustomerAddress addr = item.address;
                
                // Name or primary text
                if (addr.getLocationName() != null && !addr.getLocationName().isEmpty()) {
                    nameLabel.setText("📍 " + addr.getLocationName());
                } else if (addr.getAddress() != null) {
                    nameLabel.setText(addr.getAddress());
                } else {
                    nameLabel.setText("Address");
                }
                
                // Full address
                addressLabel.setText(formatAddress(addr));
                
                // Highlight default addresses with professional styling
                if (isPickupAddress && addr.isDefaultPickup()) {
                    container.setStyle("-fx-background-color: #E8F5E9; -fx-border-color: #4CAF50; -fx-border-width: 0 0 0 3; -fx-text-fill: black;");
                } else if (!isPickupAddress && addr.isDefaultDrop()) {
                    container.setStyle("-fx-background-color: #E8F5E9; -fx-border-color: #4CAF50; -fx-border-width: 0 0 0 3; -fx-text-fill: black;");
                } else {
                    container.setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
                }
                
                // Score for debugging
                scoreLabel.setText(String.format("Score: %.1f", item.score));
                
                setGraphic(container);
            }
        }
    }
    
    public static void shutdown() {
        executorService.shutdown();
    }
}