package com.company.payroll.loads;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Enterprise-grade customer field with address integration, clear buttons, 
 * double-click select all, and robust caching
 */
public class EnhancedCustomerFieldWithClear extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedCustomerFieldWithClear.class);
    
    // UI Components
    private TextField customerField;
    private Button clearCustomerButton;
    private TextField addressField;
    private Button clearAddressButton;
    private Button addCustomerButton;
    private Button viewAddressBookButton;
    private Popup suggestionPopup;
    private ListView<CustomerSuggestion> suggestionList;
    private VBox suggestionContainer;
    private Label previewLabel;
    private ProgressIndicator loadingIndicator;
    
    // Data and state
    private final ObservableList<CustomerSuggestion> suggestions = FXCollections.observableArrayList();
    private final StringProperty selectedCustomer = new SimpleStringProperty();
    private final ObjectProperty<CustomerAddress> selectedAddress = new SimpleObjectProperty<>();
    private final EnterpriseDataCacheManager cacheManager;
    
    private boolean isPickupCustomer;
    private Consumer<String> onCustomerSelected;
    private Consumer<CustomerAddress> onAddressSelected;
    private Timer debounceTimer;
    private boolean isShowingPopup = false;
    
    // Configuration
    private static final int MAX_SUGGESTIONS = 15;
    private static final long DEBOUNCE_DELAY = 200;
    
    // Modern enterprise styling
    private static final String FIELD_STYLE = 
        "-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
        "-fx-border-color: #d1d5db; -fx-border-radius: 6px; -fx-background-radius: 6px; " +
        "-fx-padding: 10px 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 1, 0, 0, 1);";
    
    private static final String FIELD_FOCUSED_STYLE = 
        "-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
        "-fx-border-color: #3b82f6; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px; " +
        "-fx-padding: 9px 11px; -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.15), 3, 0, 0, 1);";
    
    private static final String CLEAR_BUTTON_STYLE = 
        "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; " +
        "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 8px; " +
        "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 11px;";
    
    private static final String ACTION_BUTTON_STYLE = 
        "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; " +
        "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; " +
        "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 12px;";
    
    private static final String VIEW_BUTTON_STYLE = 
        "-fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-weight: bold; " +
        "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; " +
        "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 12px;";
    
    public EnhancedCustomerFieldWithClear(boolean isPickupCustomer) {
        this.isPickupCustomer = isPickupCustomer;
        this.cacheManager = EnterpriseDataCacheManager.getInstance();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupKeyboardNavigation();
        setupSuggestionPopup();
        
        logger.debug("Created enhanced customer field for {}", isPickupCustomer ? "pickup" : "drop");
    }
    
    private void initializeComponents() {
        // Customer field with modern styling
        customerField = new TextField();
        customerField.setPromptText(isPickupCustomer ? "Enter pickup customer..." : "Enter drop customer...");
        customerField.setStyle(FIELD_STYLE);
        customerField.setPrefWidth(300);
        
        // Clear customer button
        clearCustomerButton = new Button("×");
        clearCustomerButton.setTooltip(new Tooltip("Clear customer"));
        clearCustomerButton.setStyle(CLEAR_BUTTON_STYLE);
        clearCustomerButton.setVisible(false);
        clearCustomerButton.setManaged(false);
        
        // Address field with modern styling
        addressField = new TextField();
        addressField.setPromptText(isPickupCustomer ? "Select pickup address..." : "Select drop address...");
        addressField.setStyle(FIELD_STYLE);
        addressField.setPrefWidth(350);
        addressField.setEditable(false);
        
        // Clear address button
        clearAddressButton = new Button("×");
        clearAddressButton.setTooltip(new Tooltip("Clear address"));
        clearAddressButton.setStyle(CLEAR_BUTTON_STYLE);
        clearAddressButton.setVisible(false);
        clearAddressButton.setManaged(false);
        
        // Action buttons
        addCustomerButton = new Button("+");
        addCustomerButton.setTooltip(new Tooltip("Add new customer"));
        addCustomerButton.setStyle(ACTION_BUTTON_STYLE);
        
        viewAddressBookButton = new Button("📍");
        viewAddressBookButton.setTooltip(new Tooltip("View address book"));
        viewAddressBookButton.setStyle(VIEW_BUTTON_STYLE);
        viewAddressBookButton.setDisable(true);
        
        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(16, 16);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
    }
    
    private void setupLayout() {
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(5));
        
        // Customer field container
        HBox customerContainer = new HBox(3);
        customerContainer.setAlignment(Pos.CENTER_LEFT);
        customerContainer.getChildren().addAll(customerField, clearCustomerButton);
        
        // Address field container
        HBox addressContainer = new HBox(3);
        addressContainer.setAlignment(Pos.CENTER_LEFT);
        addressContainer.getChildren().addAll(addressField, clearAddressButton);
        
        // Button container
        HBox buttonContainer = new HBox(5);
        buttonContainer.setAlignment(Pos.CENTER_LEFT);
        buttonContainer.getChildren().addAll(addCustomerButton, viewAddressBookButton, loadingIndicator);
        
        getChildren().addAll(customerContainer, addressContainer, buttonContainer);
        
        // Set growth priorities
        HBox.setHgrow(customerContainer, Priority.NEVER);
        HBox.setHgrow(addressContainer, Priority.ALWAYS);
        HBox.setHgrow(buttonContainer, Priority.NEVER);
    }
    
    private void setupEventHandlers() {
        // Customer field events
        customerField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            
            // Show/hide clear button
            boolean hasText = newVal != null && !newVal.trim().isEmpty();
            clearCustomerButton.setVisible(hasText);
            clearCustomerButton.setManaged(hasText);
            
            if (!hasText) {
                hidePopup();
                clearAddress();
                viewAddressBookButton.setDisable(true);
                selectedCustomer.set(null);
                return;
            }
            
            // Debounced search
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> searchCustomers(newVal.trim()));
                }
            }, DEBOUNCE_DELAY);
        });
        
        // Customer field focus styling
        customerField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                customerField.setStyle(FIELD_FOCUSED_STYLE);
            } else {
                customerField.setStyle(FIELD_STYLE);
                if (!suggestionList.isFocused()) {
                    hidePopup();
                }
            }
        });
        
        // Double-click select all functionality
        customerField.setOnMouseClicked(this::handleMouseClick);
        addressField.setOnMouseClicked(this::handleMouseClick);
        
        // Address field focus styling
        addressField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                addressField.setStyle(FIELD_FOCUSED_STYLE);
            } else {
                addressField.setStyle(FIELD_STYLE);
            }
        });
        
        // Address field text change (show/hide clear button)
        addressField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasText = newVal != null && !newVal.trim().isEmpty();
            clearAddressButton.setVisible(hasText);
            clearAddressButton.setManaged(hasText);
        });
        
        // Clear button events
        clearCustomerButton.setOnAction(e -> clearCustomer());
        clearAddressButton.setOnAction(e -> clearAddress());
        
        // Action button events
        addCustomerButton.setOnAction(e -> addNewCustomer());
        viewAddressBookButton.setOnAction(e -> viewAddressBook());
        
        // Button hover effects
        setupButtonHoverEffects();
    }
    
    private void setupButtonHoverEffects() {
        // Clear buttons
        setupHoverEffect(clearCustomerButton, CLEAR_BUTTON_STYLE, "#dc2626");
        setupHoverEffect(clearAddressButton, CLEAR_BUTTON_STYLE, "#dc2626");
        
        // Action buttons
        setupHoverEffect(addCustomerButton, ACTION_BUTTON_STYLE, "#059669");
        setupHoverEffect(viewAddressBookButton, VIEW_BUTTON_STYLE, "#4f46e5");
    }
    
    private void setupHoverEffect(Button button, String baseStyle, String hoverColor) {
        button.setOnMouseEntered(e -> 
            button.setStyle(baseStyle.replace(baseStyle.split(";")[0].split(":")[1].trim(), hoverColor)));
        button.setOnMouseExited(e -> 
            button.setStyle(baseStyle));
    }
    
    private void handleMouseClick(MouseEvent event) {
        if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
            TextField field = (TextField) event.getSource();
            Platform.runLater(() -> field.selectAll());
            logger.debug("Double-click select all triggered for field");
        }
    }
    
    private void setupKeyboardNavigation() {
        customerField.setOnKeyPressed(event -> {
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
                    selectCurrentSuggestion();
                    break;
                case ESCAPE:
                    event.consume();
                    hidePopup();
                    break;
                case TAB:
                    if (suggestionList.getSelectionModel().getSelectedItem() != null) {
                        event.consume();
                        selectCurrentSuggestion();
                    }
                    break;
            }
        });
    }
    
    private void setupSuggestionPopup() {
        suggestionPopup = new Popup();
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        
        suggestionList = new ListView<>(suggestions);
        suggestionList.setPrefHeight(250);
        suggestionList.setMaxHeight(300);
        suggestionList.setPrefWidth(500);
        suggestionList.setCellFactory(listView -> new CustomerSuggestionCell());
        
        // Preview label
        previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setPrefWidth(500);
        previewLabel.setPadding(new Insets(12));
        previewLabel.setStyle(
            "-fx-background-color: #f8fafc; -fx-border-color: #e5e7eb; " +
            "-fx-border-width: 0 0 1 0; -fx-font-size: 12px; -fx-text-fill: #374151;"
        );
        
        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1 0;");
        
        Label headerLabel = new Label("🔍 Customer Search");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f2937; -fx-font-size: 13px;");
        
        header.getChildren().addAll(headerLabel);
        
        suggestionContainer = new VBox();
        suggestionContainer.setStyle(
            "-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 1px; " +
            "-fx-border-radius: 8px; -fx-background-radius: 8px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 4);"
        );
        
        suggestionContainer.getChildren().addAll(header, previewLabel, suggestionList);
        suggestionPopup.getContent().add(suggestionContainer);
        
        // Selection handling
        suggestionList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updatePreview(newVal);
            }
        });
        
        suggestionList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                selectCurrentSuggestion();
            }
        });
    }
    
    private void searchCustomers(String query) {
        if (query.isEmpty()) {
            hidePopup();
            return;
        }
        
        loadingIndicator.setVisible(true);
        loadingIndicator.setManaged(true);
        
        // Use cache manager for fast search
        List<String> results = cacheManager.searchCustomers(query, MAX_SUGGESTIONS);
        
        List<CustomerSuggestion> customerSuggestions = results.stream()
            .map(customer -> new CustomerSuggestion(customer, calculateScore(customer, query)))
            .sorted(Comparator.comparingDouble((CustomerSuggestion s) -> s.score).reversed())
            .collect(Collectors.toList());
        
        Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);
            
            if (!customerSuggestions.isEmpty()) {
                showSuggestions(customerSuggestions);
            } else {
                hidePopup();
            }
        });
    }
    
    private double calculateScore(String customer, String query) {
        String customerLower = customer.toLowerCase();
        String queryLower = query.toLowerCase();
        
        if (customerLower.equals(queryLower)) return 100.0;
        if (customerLower.startsWith(queryLower)) return 80.0;
        if (customerLower.contains(queryLower)) return 60.0;
        
        // Word matching
        String[] queryWords = queryLower.split("\\s+");
        String[] customerWords = customerLower.split("\\s+");
        
        double score = 0;
        for (String queryWord : queryWords) {
            for (String customerWord : customerWords) {
                if (customerWord.startsWith(queryWord)) {
                    score += 30.0;
                } else if (customerWord.contains(queryWord)) {
                    score += 15.0;
                }
            }
        }
        
        return score;
    }
    
    private void showSuggestions(List<CustomerSuggestion> customerSuggestions) {
        suggestions.setAll(customerSuggestions);
        
        if (!suggestions.isEmpty()) {
            if (!isShowingPopup) {
                showPopup();
            }
            suggestionList.getSelectionModel().select(0);
            updatePreview(suggestions.get(0));
        }
    }
    
    private void updatePreview(CustomerSuggestion suggestion) {
        if (suggestion == null) {
            previewLabel.setText("");
            return;
        }
        
        String preview = "👤 " + suggestion.customerName + 
                        " (Score: " + String.format("%.0f", suggestion.score) + ")";
        previewLabel.setText(preview);
    }
    
    private void showPopup() {
        try {
            var bounds = customerField.localToScreen(customerField.getBoundsInLocal());
            if (bounds != null) {
                suggestionPopup.show(customerField, bounds.getMinX(), bounds.getMaxY() + 5);
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
    
    private void selectCurrentSuggestion() {
        CustomerSuggestion selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectCustomer(selected.customerName);
        }
    }
    
    private void selectCustomer(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) return;
        
        String normalizedCustomer = customerName.trim().toUpperCase();
        customerField.setText(normalizedCustomer);
        selectedCustomer.set(normalizedCustomer);
        hidePopup();
        
        // Save customer and load addresses
        cacheManager.addCustomerAsync(normalizedCustomer)
            .thenCompose(v -> cacheManager.getCustomerAddressesAsync(normalizedCustomer))
            .thenAccept(addresses -> Platform.runLater(() -> {
                viewAddressBookButton.setDisable(false);
                
                // Auto-select default address if available
                CustomerAddress defaultAddress = addresses.stream()
                    .filter(addr -> isPickupCustomer ? addr.isDefaultPickup() : addr.isDefaultDrop())
                    .findFirst()
                    .orElse(null);
                
                if (defaultAddress != null) {
                    setSelectedAddress(defaultAddress);
                }
                
                if (onCustomerSelected != null) {
                    onCustomerSelected.accept(normalizedCustomer);
                }
                
                logger.debug("Selected customer: {} with {} addresses", normalizedCustomer, addresses.size());
            }))
            .exceptionally(throwable -> {
                logger.error("Error selecting customer: " + normalizedCustomer, throwable);
                return null;
            });
    }
    
    private void clearCustomer() {
        customerField.clear();
        selectedCustomer.set(null);
        clearAddress();
        viewAddressBookButton.setDisable(true);
        hidePopup();
        logger.debug("Cleared customer field");
    }
    
    private void clearAddress() {
        addressField.clear();
        selectedAddress.set(null);
        logger.debug("Cleared address field");
    }
    
    private void addNewCustomer() {
        String customerText = customerField.getText();
        if (customerText == null || customerText.trim().isEmpty()) {
            showCustomerInputDialog();
        } else {
            selectCustomer(customerText.trim());
        }
    }
    
    private void showCustomerInputDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Customer");
        dialog.setHeaderText("Enter new customer name:");
        dialog.setContentText("Customer:");
        
        // Style the dialog
        dialog.getDialogPane().setStyle("-fx-background-color: white; -fx-text-fill: black;");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::selectCustomer);
    }
    
    private void viewAddressBook() {
        String customer = selectedCustomer.get();
        if (customer == null || customer.trim().isEmpty()) return;
        
        // Show address selection dialog
        showAddressSelectionDialog(customer);
    }
    
    private void showAddressSelectionDialog(String customerName) {
        Dialog<CustomerAddress> dialog = new Dialog<>();
        dialog.setTitle("Select Address");
        dialog.setHeaderText("Select address for " + customerName);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: white; -fx-text-fill: black;");
        
        ListView<CustomerAddress> addressList = new ListView<>();
        addressList.setPrefHeight(300);
        addressList.setPrefWidth(500);
        
        // Load addresses
        cacheManager.getCustomerAddressesAsync(customerName)
            .thenAccept(addresses -> Platform.runLater(() -> {
                addressList.getItems().setAll(addresses);
                if (selectedAddress.get() != null) {
                    addressList.getSelectionModel().select(selectedAddress.get());
                }
            }));
        
        dialog.getDialogPane().setContent(addressList);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return addressList.getSelectionModel().getSelectedItem();
            }
            return null;
        });
        
        Optional<CustomerAddress> result = dialog.showAndWait();
        result.ifPresent(this::setSelectedAddress);
    }
    
    private void setSelectedAddress(CustomerAddress address) {
        if (address == null) {
            clearAddress();
            return;
        }
        
        selectedAddress.set(address);
        
        // Format address display
        StringBuilder display = new StringBuilder();
        if (address.getLocationName() != null && !address.getLocationName().trim().isEmpty()) {
            display.append(address.getLocationName()).append(": ");
        }
        
        if (address.getAddress() != null && !address.getAddress().trim().isEmpty()) {
            display.append(address.getAddress());
        }
        
        if (address.getCity() != null && !address.getCity().trim().isEmpty()) {
            if (display.length() > 0) display.append(", ");
            display.append(address.getCity());
        }
        
        if (address.getState() != null && !address.getState().trim().isEmpty()) {
            if (display.length() > 0) display.append(", ");
            display.append(address.getState());
        }
        
        addressField.setText(display.toString());
        
        if (onAddressSelected != null) {
            onAddressSelected.accept(address);
        }
        
        logger.debug("Selected address: {}", display.toString());
    }
    
    // Public API methods
    
    public String getSelectedCustomer() {
        return selectedCustomer.get();
    }
    
    public CustomerAddress getSelectedAddress() {
        return selectedAddress.get();
    }
    
    public void setCustomer(String customer) {
        if (customer != null && !customer.trim().isEmpty()) {
            selectCustomer(customer.trim());
        } else {
            clearCustomer();
        }
    }
    
    public void setAddress(CustomerAddress address) {
        setSelectedAddress(address);
    }
    
    public void setOnCustomerSelected(Consumer<String> handler) {
        this.onCustomerSelected = handler;
    }
    
    public void setOnAddressSelected(Consumer<CustomerAddress> handler) {
        this.onAddressSelected = handler;
    }
    
    public StringProperty selectedCustomerProperty() {
        return selectedCustomer;
    }
    
    public ObjectProperty<CustomerAddress> selectedAddressProperty() {
        return selectedAddress;
    }
    
    // Inner classes
    
    private static class CustomerSuggestion {
        final String customerName;
        final double score;
        
        CustomerSuggestion(String customerName, double score) {
            this.customerName = customerName;
            this.score = score;
        }
    }
    
    private class CustomerSuggestionCell extends ListCell<CustomerSuggestion> {
        private final HBox container;
        private final Label nameLabel;
        private final Label scoreLabel;
        
        public CustomerSuggestionCell() {
            container = new HBox(10);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(8, 12, 8, 12));
            
            nameLabel = new Label();
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
            nameLabel.setTextFill(Color.BLACK);
            
            scoreLabel = new Label();
            scoreLabel.setFont(Font.font("System", 10));
            scoreLabel.setTextFill(Color.GRAY);
            
            container.getChildren().addAll(nameLabel, scoreLabel);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
        }
        
        @Override
        protected void updateItem(CustomerSuggestion item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
            } else {
                nameLabel.setText("👤 " + item.customerName);
                scoreLabel.setText(String.format("%.0f", item.score));
                
                // Highlight high-score matches
                if (item.score >= 80) {
                    container.setStyle("-fx-background-color: #ecfdf5; -fx-border-color: #10b981; -fx-border-width: 0 0 0 3;");
                } else {
                    container.setStyle("-fx-background-color: transparent;");
                }
                
                setGraphic(container);
            }
        }
    }
}