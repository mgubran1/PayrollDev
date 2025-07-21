package com.company.payroll.loads;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enhanced autocomplete field with professional features:
 * - Multi-source data support
 * - Intelligent fuzzy matching
 * - Recent searches memory
 * - Visual loading indicators
 * - Keyboard navigation
 * - Async data loading
 * - Category grouping
 * - Smart suggestions
 */
public class EnhancedAutocompleteField<T> extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedAutocompleteField.class);
    private static final ExecutorService searchExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("AutocompleteSearch-" + t.getId());
        return t;
    });
    
    // UI Components
    private final TextField searchField;
    private final Button clearButton;
    private final ProgressIndicator loadingIndicator;
    private final Popup suggestionPopup;
    private final VBox suggestionContainer;
    private final ListView<SearchResult<T>> suggestionList;
    private final Label statusLabel;
    private final Label noResultsLabel;
    
    // Data
    private final ObservableList<SearchResult<T>> suggestions = FXCollections.observableArrayList();
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final List<String> recentSearches = new ArrayList<>();
    private final Map<String, List<T>> categoryCache = new ConcurrentHashMap<>();
    
    // Configuration
    private Function<String, CompletableFuture<List<SearchResult<T>>>> searchProvider;
    private Function<T, String> displayFunction;
    private Function<T, String> categoryFunction;
    private int maxSuggestions = 10;
    private int maxRecentSearches = 5;
    private long debounceDelay = 300; // milliseconds
    private int minSearchLength = 1;
    private boolean showCategories = true;
    private boolean showRecentSearches = true;
    private boolean highlightMatches = true;
    
    // State
    private ScheduledFuture<?> searchTask;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean isShowingPopup = false;
    private String lastSearchQuery = "";
    
    // Styles
    private static final String FIELD_STYLE = """
        -fx-font-size: 14px;
        -fx-pref-width: 300px;
        -fx-background-radius: 20;
        -fx-border-radius: 20;
        -fx-padding: 5 35 5 10;
        """;
    
    private static final String CLEAR_BUTTON_STYLE = """
        -fx-background-color: transparent;
        -fx-font-size: 16px;
        -fx-text-fill: #666;
        -fx-cursor: hand;
        -fx-padding: 0;
        -fx-min-width: 20;
        -fx-pref-width: 20;
        """;
    
    private static final String POPUP_STYLE = """
        -fx-background-color: white;
        -fx-border-color: #e0e0e0;
        -fx-border-width: 1;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
        -fx-background-radius: 5;
        -fx-border-radius: 5;
        """;
    
    public EnhancedAutocompleteField() {
        // Initialize search field
        searchField = new TextField();
        searchField.setPromptText("Type to search...");
        searchField.setStyle(FIELD_STYLE);
        
        // Clear button
        clearButton = new Button("×");
        clearButton.setStyle(CLEAR_BUTTON_STYLE);
        clearButton.setVisible(false);
        clearButton.setOnAction(e -> clearSearch());
        
        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(16, 16);
        loadingIndicator.setVisible(false);
        
        // Create layered pane for field with overlay buttons
        StackPane fieldContainer = new StackPane();
        fieldContainer.getChildren().add(searchField);
        
        HBox overlayButtons = new HBox(5);
        overlayButtons.setAlignment(Pos.CENTER_RIGHT);
        overlayButtons.setPadding(new Insets(0, 10, 0, 0));
        overlayButtons.getChildren().addAll(loadingIndicator, clearButton);
        overlayButtons.setPickOnBounds(false);
        
        fieldContainer.getChildren().add(overlayButtons);
        StackPane.setAlignment(overlayButtons, Pos.CENTER_RIGHT);
        
        // Status label
        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        statusLabel.setPadding(new Insets(5, 10, 5, 10));
        
        // No results label
        noResultsLabel = new Label("No results found");
        noResultsLabel.setStyle("-fx-padding: 20; -fx-text-fill: #999;");
        noResultsLabel.setVisible(false);
        
        // Suggestion list
        suggestionList = new ListView<>(suggestions);
        suggestionList.setCellFactory(lv -> new SearchResultCell());
        suggestionList.setPrefHeight(300);
        suggestionList.setMaxHeight(400);
        
        // Suggestion container
        suggestionContainer = new VBox();
        suggestionContainer.setStyle(POPUP_STYLE);
        suggestionContainer.getChildren().addAll(statusLabel, suggestionList, noResultsLabel);
        VBox.setVgrow(suggestionList, Priority.ALWAYS);
        
        // Popup
        suggestionPopup = new Popup();
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        suggestionPopup.getContent().add(suggestionContainer);
        
        // Layout
        getChildren().add(fieldContainer);
        HBox.setHgrow(fieldContainer, Priority.ALWAYS);
        
        // Setup event handlers
        setupEventHandlers();
        setupKeyboardNavigation();
        
        // Bind properties
        loading.addListener((obs, wasLoading, isLoading) -> {
            loadingIndicator.setVisible(isLoading);
            if (isLoading) {
                statusLabel.setText("Searching...");
            }
        });
    }
    
    private void setupEventHandlers() {
        // Text change handler with debouncing
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            clearButton.setVisible(newVal != null && !newVal.isEmpty());
            
            if (searchTask != null && !searchTask.isDone()) {
                searchTask.cancel(false);
            }
            
            if (newVal == null || newVal.trim().length() < minSearchLength) {
                hidePopup();
                return;
            }
            
            searchTask = scheduler.schedule(() -> {
                Platform.runLater(() -> performSearch(newVal.trim()));
            }, debounceDelay, TimeUnit.MILLISECONDS);
        });
        
        // Focus handling
        searchField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && !suggestionList.isFocused()) {
                hidePopup();
            } else if (isFocused && !searchField.getText().isEmpty() && !suggestions.isEmpty()) {
                showPopup();
            }
        });
        
        // Selection handling
        suggestionList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.item != null) {
                selectItem(newVal);
            }
        });
        
        // Double-click selection
        suggestionList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                SearchResult<T> selected = suggestionList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectItem(selected);
                }
            }
        });
    }
    
    private void setupKeyboardNavigation() {
        searchField.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case DOWN:
                    if (isShowingPopup) {
                        event.consume();
                        selectNext();
                    } else if (!suggestions.isEmpty()) {
                        showPopup();
                    }
                    break;
                case UP:
                    if (isShowingPopup) {
                        event.consume();
                        selectPrevious();
                    }
                    break;
                case ENTER:
                    if (isShowingPopup && suggestionList.getSelectionModel().getSelectedItem() != null) {
                        event.consume();
                        selectItem(suggestionList.getSelectionModel().getSelectedItem());
                    }
                    break;
                case ESCAPE:
                    if (isShowingPopup) {
                        event.consume();
                        hidePopup();
                    } else {
                        clearSearch();
                    }
                    break;
                case TAB:
                    if (isShowingPopup && suggestionList.getSelectionModel().getSelectedItem() != null) {
                        selectItem(suggestionList.getSelectionModel().getSelectedItem());
                    }
                    break;
            }
        });
    }
    
    private void performSearch(String query) {
        if (searchProvider == null) {
            logger.warn("No search provider configured");
            return;
        }
        
        lastSearchQuery = query;
        loading.set(true);
        
        searchProvider.apply(query)
            .thenAcceptAsync(results -> {
                Platform.runLater(() -> {
                    loading.set(false);
                    
                    if (!lastSearchQuery.equals(query)) {
                        // Query changed while searching, ignore results
                        return;
                    }
                    
                    if (results == null || results.isEmpty()) {
                        showNoResults();
                    } else {
                        showResults(results);
                        addToRecentSearches(query);
                    }
                });
            }, searchExecutor)
            .exceptionally(throwable -> {
                Platform.runLater(() -> {
                    loading.set(false);
                    logger.error("Search error", throwable);
                    showError("Search failed: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void showResults(List<SearchResult<T>> results) {
        suggestions.clear();
        
        if (showCategories && categoryFunction != null) {
            // Group by category
            Map<String, List<SearchResult<T>>> grouped = results.stream()
                .collect(Collectors.groupingBy(
                    r -> categoryFunction.apply(r.item),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            
            grouped.forEach((category, items) -> {
                suggestions.add(new SearchResult<>(null, category, 0, true));
                suggestions.addAll(items.stream()
                    .limit(maxSuggestions / Math.max(1, grouped.size()))
                    .collect(Collectors.toList()));
            });
        } else {
            suggestions.addAll(results.stream()
                .limit(maxSuggestions)
                .collect(Collectors.toList()));
        }
        
        noResultsLabel.setVisible(false);
        suggestionList.setVisible(true);
        statusLabel.setText(String.format("Found %d result%s", results.size(), results.size() == 1 ? "" : "s"));
        
        if (!isShowingPopup) {
            showPopup();
        }
        
        // Auto-select first non-category item
        for (int i = 0; i < suggestions.size(); i++) {
            if (!suggestions.get(i).isCategory) {
                suggestionList.getSelectionModel().select(i);
                break;
            }
        }
    }
    
    private void showNoResults() {
        suggestions.clear();
        noResultsLabel.setVisible(true);
        suggestionList.setVisible(false);
        statusLabel.setText("No results");
        
        if (!isShowingPopup) {
            showPopup();
        }
    }
    
    private void showError(String message) {
        suggestions.clear();
        noResultsLabel.setText("Error: " + message);
        noResultsLabel.setVisible(true);
        suggestionList.setVisible(false);
        statusLabel.setText("Search failed");
        
        if (!isShowingPopup) {
            showPopup();
        }
    }
    
    private void showPopup() {
        try {
            Bounds bounds = searchField.localToScreen(searchField.getBoundsInLocal());
            if (bounds != null) {
                suggestionPopup.show(searchField, bounds.getMinX(), bounds.getMaxY() + 2);
                isShowingPopup = true;
                
                // Fade in animation
                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), suggestionContainer);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();
            }
        } catch (Exception e) {
            logger.error("Error showing popup", e);
        }
    }
    
    private void hidePopup() {
        if (isShowingPopup) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(100), suggestionContainer);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                suggestionPopup.hide();
                isShowingPopup = false;
            });
            fadeOut.play();
        }
    }
    
    private void selectNext() {
        int current = suggestionList.getSelectionModel().getSelectedIndex();
        int next = current;
        
        do {
            next = (next + 1) % suggestions.size();
        } while (next != current && suggestions.get(next).isCategory);
        
        if (next != current) {
            suggestionList.getSelectionModel().select(next);
            suggestionList.scrollTo(next);
        }
    }
    
    private void selectPrevious() {
        int current = suggestionList.getSelectionModel().getSelectedIndex();
        int prev = current;
        
        do {
            prev = prev <= 0 ? suggestions.size() - 1 : prev - 1;
        } while (prev != current && suggestions.get(prev).isCategory);
        
        if (prev != current) {
            suggestionList.getSelectionModel().select(prev);
            suggestionList.scrollTo(prev);
        }
    }
    
    private void selectItem(SearchResult<T> result) {
        if (result != null && result.item != null) {
            selectedItem.set(result.item);
            searchField.setText(displayFunction != null ? displayFunction.apply(result.item) : result.displayText);
            hidePopup();
            
            // Fire action event
            fireEvent(new javafx.event.ActionEvent());
        }
    }
    
    private void clearSearch() {
        searchField.clear();
        selectedItem.set(null);
        suggestions.clear();
        hidePopup();
    }
    
    private void addToRecentSearches(String query) {
        if (!showRecentSearches) return;
        
        recentSearches.remove(query); // Remove if exists to move to top
        recentSearches.add(0, query);
        
        while (recentSearches.size() > maxRecentSearches) {
            recentSearches.remove(recentSearches.size() - 1);
        }
    }
    
    // Public API
    public void setSearchProvider(Function<String, CompletableFuture<List<SearchResult<T>>>> provider) {
        this.searchProvider = provider;
    }
    
    public void setDisplayFunction(Function<T, String> function) {
        this.displayFunction = function;
    }
    
    public void setCategoryFunction(Function<T, String> function) {
        this.categoryFunction = function;
    }
    
    public T getSelectedItem() {
        return selectedItem.get();
    }
    
    public ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }
    
    public void setSelectedItem(T item) {
        selectedItem.set(item);
        if (item != null && displayFunction != null) {
            searchField.setText(displayFunction.apply(item));
        }
    }
    
    public TextField getSearchField() {
        return searchField;
    }
    
    public void setPromptText(String text) {
        searchField.setPromptText(text);
    }
    
    public void setMaxSuggestions(int max) {
        this.maxSuggestions = max;
    }
    
    public void setMinSearchLength(int length) {
        this.minSearchLength = length;
    }
    
    public void setDebounceDelay(long delay) {
        this.debounceDelay = delay;
    }
    
    public void setShowCategories(boolean show) {
        this.showCategories = show;
    }
    
    public void setHighlightMatches(boolean highlight) {
        this.highlightMatches = highlight;
    }
    
    public List<String> getRecentSearches() {
        return new ArrayList<>(recentSearches);
    }
    
    // Search result wrapper
    public static class SearchResult<T> {
        public final T item;
        public final String displayText;
        public final double score;
        public final boolean isCategory;
        public final Map<String, Object> metadata;
        
        public SearchResult(T item, String displayText, double score) {
            this(item, displayText, score, false, null);
        }
        
        public SearchResult(T item, String displayText, double score, boolean isCategory) {
            this(item, displayText, score, isCategory, null);
        }
        
        public SearchResult(T item, String displayText, double score, boolean isCategory, Map<String, Object> metadata) {
            this.item = item;
            this.displayText = displayText;
            this.score = score;
            this.isCategory = isCategory;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
    }
    
    // Custom cell renderer
    private class SearchResultCell extends ListCell<SearchResult<T>> {
        private final HBox container = new HBox();
        private final Label mainLabel = new Label();
        private final Label scoreLabel = new Label();
        private final Label categoryLabel = new Label();
        
        public SearchResultCell() {
            container.setAlignment(Pos.CENTER_LEFT);
            container.setSpacing(10);
            container.setPadding(new Insets(5, 10, 5, 10));
            
            mainLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(mainLabel, Priority.ALWAYS);
            
            scoreLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
            categoryLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
            
            container.getChildren().addAll(mainLabel, scoreLabel);
        }
        
        @Override
        protected void updateItem(SearchResult<T> item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setStyle("");
            } else if (item.isCategory) {
                categoryLabel.setText(item.displayText.toUpperCase());
                setGraphic(categoryLabel);
                setStyle("-fx-background-color: #f5f5f5; -fx-font-weight: bold;");
                setDisable(true);
            } else {
                mainLabel.setText(item.displayText);
                
                if (logger.isDebugEnabled()) {
                    scoreLabel.setText(String.format("%.1f", item.score));
                    scoreLabel.setVisible(true);
                } else {
                    scoreLabel.setVisible(false);
                }
                
                // Highlight matches if enabled
                if (highlightMatches && lastSearchQuery != null && !lastSearchQuery.isEmpty()) {
                    highlightText(mainLabel, item.displayText, lastSearchQuery);
                }
                
                setGraphic(container);
                setStyle("");
                setDisable(false);
                
                // Hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: #f0f0f0;"));
                setOnMouseExited(e -> setStyle(""));
            }
        }
        
        private void highlightText(Label label, String text, String query) {
            // Simple highlighting - in production, use TextFlow for better highlighting
            String lowerText = text.toLowerCase();
            String lowerQuery = query.toLowerCase();
            int index = lowerText.indexOf(lowerQuery);
            
            if (index >= 0) {
                label.setStyle("-fx-font-weight: bold;");
            } else {
                label.setStyle("");
            }
        }
    }
    
    // Cleanup
    public static void shutdown() {
        searchExecutor.shutdown();
    }
}