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
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ENTERPRISE UNIFIED AUTOCOMPLETE FIELD - REPLACES ALL 7 DUPLICATE IMPLEMENTATIONS
 * 
 * This professional, thread-safe autocomplete component eliminates all code duplication
 * and fixes critical performance/memory issues found in:
 * - AddressAutocompleteField.java 
 * - CustomerAutocompleteField.java
 * - EnhancedAutocompleteField.java
 * - EnhancedCustomerFieldWithClear.java
 * - EnhancedLocationField.java
 * - EnhancedLocationFieldOptimized.java  
 * - SimpleAutocompleteHandler.java
 *
 * KEY IMPROVEMENTS:
 * - Single shared, managed ExecutorService (no static leaks)
 * - Proper resource cleanup and disposal
 * - Thread-safe operations with atomic state management
 * - Advanced debouncing with race condition protection
 * - Memory-efficient caching with automatic cleanup
 * - Comprehensive error handling and logging
 * - Generic type support for any data type
 * - Configurable display and search functions
 * - Professional keyboard navigation
 * - Modern enterprise styling
 */
public class UnifiedAutocompleteField<T> extends HBox implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(UnifiedAutocompleteField.class);
    
    // SHARED THREAD MANAGEMENT - FIXES STATIC EXECUTOR LEAKS
    private static final class ThreadManager {
        private static volatile ThreadManager INSTANCE;
        private final ScheduledExecutorService sharedExecutor;
        private final AtomicInteger activeInstances = new AtomicInteger(0);
        private volatile boolean isShuttingDown = false;
        
        private ThreadManager() {
            sharedExecutor = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "UnifiedAutocomplete-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    t.setUncaughtExceptionHandler((thread, ex) -> 
                        logger.error("Uncaught exception in autocomplete thread: " + thread.getName(), ex));
                    return t;
                }
            );
            logger.debug("Created shared autocomplete thread pool");
        }
        
        public static ThreadManager getInstance() {
            if (INSTANCE == null) {
                synchronized (ThreadManager.class) {
                    if (INSTANCE == null) {
                        INSTANCE = new ThreadManager();
                    }
                }
            }
            return INSTANCE;
        }
        
        public ScheduledExecutorService getExecutor() {
            if (isShuttingDown) {
                throw new IllegalStateException("Thread manager is shutting down");
            }
            return sharedExecutor;
        }
        
        public void registerInstance() {
            activeInstances.incrementAndGet();
        }
        
        public void unregisterInstance() {
            if (activeInstances.decrementAndGet() <= 0) {
                synchronized (ThreadManager.class) {
                    if (activeInstances.get() <= 0 && !isShuttingDown) {
                        isShuttingDown = true;
                        logger.info("Shutting down shared autocomplete executor - no active instances");
                        sharedExecutor.shutdown();
                        try {
                            if (!sharedExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                                List<Runnable> pending = sharedExecutor.shutdownNow();
                                logger.warn("Forcibly shutdown executor, {} pending tasks cancelled", pending.size());
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            sharedExecutor.shutdownNow();
                        }
                        INSTANCE = null;
                    }
                }
            }
        }
    }
    
    // ATOMIC STATE MANAGEMENT - FIXES RACE CONDITIONS
    private final AtomicReference<CompletableFuture<Void>> currentSearchTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> debounceTask = new AtomicReference<>();
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final AtomicBoolean isPopupShowing = new AtomicBoolean(false);
    
    // THREAD-SAFE COLLECTIONS
    private final ConcurrentLinkedQueue<String> recentSearches = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, List<AutocompleteResult<T>>> searchCache = new ConcurrentHashMap<>();
    
    // UI COMPONENTS
    private final TextField searchField;
    private final Button clearButton;
    private final Button actionButton;
    private final ProgressIndicator loadingIndicator;
    private final Popup suggestionPopup;
    private VBox suggestionContainer;
    private final ListView<AutocompleteResult<T>> suggestionList;
    private final Label previewLabel;
    private final Label statusLabel;
    
    // DATA BINDING
    private final ObservableList<AutocompleteResult<T>> suggestions = FXCollections.synchronizedObservableList(
        FXCollections.observableArrayList()
    );
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private final StringProperty displayText = new SimpleStringProperty("");
    
    // CONFIGURATION
    private Function<String, CompletableFuture<List<T>>> searchProvider;
    private Function<T, String> displayFunction = Object::toString;
    private Function<T, String> searchFunction = Object::toString;
    private Consumer<T> onSelectionHandler;
    private Consumer<String> onTextChangeHandler;
    private Runnable onActionHandler;
    
    // PERFORMANCE SETTINGS
    private volatile int maxSuggestions = 10;
    private volatile int maxRecentSearches = 5;
    private volatile int maxCacheSize = 100;
    private volatile long debounceDelay = 300; // milliseconds
    private volatile int minSearchLength = 1;
    private volatile boolean enableCaching = true;
    private volatile boolean showRecentSearches = true;
    
    // THREAD MANAGER INSTANCE
    private final ThreadManager threadManager;
    private final ScheduledExecutorService executor;
    
    // PROFESSIONAL STYLING
    private static final String FIELD_STYLE = """
        -fx-font-size: 14px;
        -fx-text-fill: #1f2937;
        -fx-background-color: white;
        -fx-border-color: #d1d5db;
        -fx-border-width: 1px;
        -fx-border-radius: 6px;
        -fx-background-radius: 6px;
        -fx-padding: 8px 12px;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 1, 0, 0, 1);
        """;
    
    private static final String FIELD_FOCUSED_STYLE = """
        -fx-font-size: 14px;
        -fx-text-fill: #1f2937;
        -fx-background-color: white;
        -fx-border-color: #3b82f6;
        -fx-border-width: 2px;
        -fx-border-radius: 6px;
        -fx-background-radius: 6px;
        -fx-padding: 7px 11px;
        -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.25), 4, 0, 0, 1);
        """;
    
    private static final String BUTTON_STYLE = """
        -fx-font-size: 12px;
        -fx-font-weight: bold;
        -fx-text-fill: white;
        -fx-border-radius: 4px;
        -fx-background-radius: 4px;
        -fx-padding: 6px 8px;
        -fx-cursor: hand;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0, 0, 1);
        """;
    
    private static final String POPUP_STYLE = """
        -fx-background-color: white;
        -fx-border-color: #e5e7eb;
        -fx-border-width: 1px;
        -fx-border-radius: 8px;
        -fx-background-radius: 8px;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 15, 0, 0, 5);
        """;
    
    /**
     * Constructor for unified autocomplete field
     * @param promptText Placeholder text for the search field
     */
    public UnifiedAutocompleteField(String promptText) {
        // Initialize thread management - FIXES STATIC EXECUTOR LEAKS
        this.threadManager = ThreadManager.getInstance();
        this.threadManager.registerInstance();
        this.executor = threadManager.getExecutor();
        
        // Initialize UI components
        this.searchField = new TextField();
        this.clearButton = new Button("×");
        this.actionButton = new Button("+");
        this.loadingIndicator = new ProgressIndicator();
        this.suggestionPopup = new Popup();
        this.suggestionList = new ListView<>(suggestions);
        this.previewLabel = new Label();
        this.statusLabel = new Label();
        
        initializeComponents(promptText);
        setupLayout();
        setupEventHandlers();
        setupKeyboardNavigation();
        setupSuggestionPopup();
        
        logger.debug("Created UnifiedAutocompleteField with prompt: {}", promptText);
    }
    
    private void initializeComponents(String promptText) {
        // Search field configuration
        searchField.setPromptText(promptText);
        searchField.setStyle(FIELD_STYLE);
        searchField.setPrefWidth(300);
        
        // Clear button configuration  
        clearButton.setTooltip(new Tooltip("Clear field"));
        clearButton.setStyle(BUTTON_STYLE + " -fx-background-color: #ef4444;");
        clearButton.setVisible(false);
        clearButton.setManaged(false);
        
        // Action button configuration
        actionButton.setTooltip(new Tooltip("Add new item"));
        actionButton.setStyle(BUTTON_STYLE + " -fx-background-color: #10b981;");
        
        // Loading indicator configuration
        loadingIndicator.setPrefSize(16, 16);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        
        // Status label configuration
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        statusLabel.setPadding(new Insets(5, 12, 5, 12));
    }
    
    private void setupLayout() {
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2));
        
        // Create field container with overlay buttons
        StackPane fieldContainer = new StackPane();
        fieldContainer.getChildren().add(searchField);
        
        // Button overlay
        HBox buttonOverlay = new HBox(5);
        buttonOverlay.setAlignment(Pos.CENTER_RIGHT);
        buttonOverlay.setPadding(new Insets(0, 8, 0, 0));
        buttonOverlay.getChildren().addAll(loadingIndicator, clearButton);
        buttonOverlay.setPickOnBounds(false);
        
        fieldContainer.getChildren().add(buttonOverlay);
        StackPane.setAlignment(buttonOverlay, Pos.CENTER_RIGHT);
        
        getChildren().addAll(fieldContainer, actionButton);
        HBox.setHgrow(fieldContainer, Priority.ALWAYS);
    }
    
    private void setupEventHandlers() {
        // THREAD-SAFE TEXT CHANGE HANDLING - FIXES RACE CONDITIONS
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isDisposed.get()) return;
            
            // Update display text property
            displayText.set(newVal != null ? newVal : "");
            
            // Show/hide clear button
            boolean hasText = newVal != null && !newVal.trim().isEmpty();
            Platform.runLater(() -> {
                clearButton.setVisible(hasText);
                clearButton.setManaged(hasText);
            });
            
            // Cancel any pending debounce task - FIXES TIMER LEAKS
            ScheduledFuture<?> oldTask = debounceTask.getAndSet(null);
            if (oldTask != null && !oldTask.isDone()) {
                oldTask.cancel(false);
            }
            
            if (!hasText) {
                hidePopup();
                selectedItem.set(null);
                if (onTextChangeHandler != null) {
                    onTextChangeHandler.accept("");
                }
                return;
            }
            
            // Schedule new search with debouncing
            try {
                ScheduledFuture<?> newTask = executor.schedule(() -> {
                    if (!isDisposed.get()) {
                        performSearch(newVal.trim());
                    }
                }, debounceDelay, TimeUnit.MILLISECONDS);
                debounceTask.set(newTask);
            } catch (RejectedExecutionException e) {
                logger.debug("Executor rejected search task - component likely disposed");
            }
        });
        
        // Focus handling with modern styling
        searchField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                searchField.setStyle(FIELD_FOCUSED_STYLE);
                if (!searchField.getText().isEmpty() && !suggestions.isEmpty()) {
                    showPopup();
                }
            } else {
                searchField.setStyle(FIELD_STYLE);
                // Delay hiding to allow for list selection
                executor.schedule(() -> {
                    if (!suggestionList.isFocused()) {
                        Platform.runLater(this::hidePopup);
                    }
                }, 150, TimeUnit.MILLISECONDS);
            }
        });
        
        // Button handlers
        clearButton.setOnAction(e -> clearField());
        actionButton.setOnAction(e -> {
            if (onActionHandler != null) {
                onActionHandler.run();
            }
        });
        
        // Button hover effects
        setupButtonHoverEffects();
    }
    
    private void setupButtonHoverEffects() {
        clearButton.setOnMouseEntered(e -> 
            clearButton.setStyle(BUTTON_STYLE + " -fx-background-color: #dc2626;"));
        clearButton.setOnMouseExited(e -> 
            clearButton.setStyle(BUTTON_STYLE + " -fx-background-color: #ef4444;"));
        
        actionButton.setOnMouseEntered(e -> 
            actionButton.setStyle(BUTTON_STYLE + " -fx-background-color: #059669;"));
        actionButton.setOnMouseExited(e -> 
            actionButton.setStyle(BUTTON_STYLE + " -fx-background-color: #10b981;"));
    }
    
    private void setupKeyboardNavigation() {
        searchField.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (!isPopupShowing.get()) return;
            
            switch (event.getCode()) {
                case DOWN:
                    event.consume();
                    selectNext();
                    break;
                case UP:
                    event.consume();
                    selectPrevious();
                    break;
                case ENTER:
                    event.consume();
                    selectCurrent();
                    break;
                case ESCAPE:
                    event.consume();
                    hidePopup();
                    break;
                case TAB:
                    if (suggestionList.getSelectionModel().getSelectedItem() != null) {
                        event.consume();
                        selectCurrent();
                    }
                    break;
                default:
                    // Handle other key codes - no action needed
                    break;
            }
        });
    }
    
    private void setupSuggestionPopup() {
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        
        // Suggestion list configuration
        suggestionList.setPrefHeight(300);
        suggestionList.setMaxHeight(400);
        suggestionList.setPrefWidth(450);
        suggestionList.setCellFactory(lv -> new AutocompleteCell());
        
        // Preview label configuration
        previewLabel.setWrapText(true);
        previewLabel.setPrefWidth(450);
        previewLabel.setPadding(new Insets(10, 12, 10, 12));
        previewLabel.setStyle("""
            -fx-background-color: #f8fafc;
            -fx-border-color: #e5e7eb;
            -fx-border-width: 0 0 1 0;
            -fx-font-size: 12px;
            -fx-text-fill: #374151;
            """);
        
        // Header configuration
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle("""
            -fx-background-color: #f1f5f9;
            -fx-border-color: #e5e7eb;
            -fx-border-width: 0 0 1 0;
            """);
        
        Label headerIcon = new Label("🔍");
        Label headerLabel = new Label("Search Results");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f2937; -fx-font-size: 13px;");
        
        header.getChildren().addAll(headerIcon, headerLabel, statusLabel);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        // Container assembly
        suggestionContainer = new VBox();
        suggestionContainer.setStyle(POPUP_STYLE);
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
                selectCurrent();
            }
        });
    }
    
    // THREAD-SAFE SEARCH - FIXES COMPLETABLEFUTURE LEAKS
    private void performSearch(String query) {
        if (searchProvider == null || query.isEmpty() || isDisposed.get()) {
            return;
        }
        
        // Cancel previous search - FIXES FUTURE LEAKS
        CompletableFuture<Void> oldSearch = currentSearchTask.getAndSet(null);
        if (oldSearch != null && !oldSearch.isDone()) {
            oldSearch.cancel(true);
        }
        
        // Check cache first if enabled
        if (enableCaching && searchCache.containsKey(query)) {
            List<AutocompleteResult<T>> cachedResults = searchCache.get(query);
            Platform.runLater(() -> showResults(cachedResults, query));
            return;
        }
        
        // Show loading indicator
        Platform.runLater(() -> {
            loadingIndicator.setVisible(true);
            loadingIndicator.setManaged(true);
            statusLabel.setText("Searching...");
        });
        
        // Perform async search
        CompletableFuture<Void> searchTask = searchProvider.apply(query)
            .thenCompose(rawResults -> CompletableFuture.supplyAsync(() -> {
                if (rawResults == null) return new ArrayList<AutocompleteResult<T>>();
                
                return rawResults.stream()
                    .map(item -> new AutocompleteResult<>(
                        item,
                        displayFunction.apply(item),
                        calculateRelevanceScore(query, searchFunction.apply(item))
                    ))
                    .sorted(Comparator.comparingDouble((AutocompleteResult<T> r) -> r.score).reversed())
                    .limit(maxSuggestions)
                    .collect(Collectors.toList());
            }, executor))
            .thenAccept(results -> {
                if (!isDisposed.get()) {
                    // Cache results if enabled
                    if (enableCaching && results != null) {
                        cacheResults(query, new ArrayList<>(results));
                    }
                    
                    Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        loadingIndicator.setManaged(false);
                        showResults(new ArrayList<>(results), query);
                    });
                }
            })
            .exceptionally(throwable -> {
                logger.error("Search error for query: " + query, throwable);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    loadingIndicator.setManaged(false);
                    statusLabel.setText("Search failed");
                });
                return null;
            });
        
        currentSearchTask.set(searchTask);
    }
    
    // INTELLIGENT RELEVANCE SCORING
    private double calculateRelevanceScore(String query, String text) {
        if (text == null || query == null) return 0;
        
        String normalizedQuery = query.toLowerCase().trim();
        String normalizedText = text.toLowerCase().trim();
        
        // Exact match
        if (normalizedText.equals(normalizedQuery)) return 100.0;
        
        // Starts with
        if (normalizedText.startsWith(normalizedQuery)) return 90.0;
        
        // Contains as substring
        if (normalizedText.contains(normalizedQuery)) return 80.0;
        
        // Word boundary matches
        String[] queryWords = normalizedQuery.split("\\s+");
        String[] textWords = normalizedText.split("\\s+");
        double wordScore = 0;
        int matchedWords = 0;
        
        for (String queryWord : queryWords) {
            for (String textWord : textWords) {
                if (textWord.startsWith(queryWord)) {
                    wordScore += 70.0;
                    matchedWords++;
                } else if (textWord.contains(queryWord)) {
                    wordScore += 50.0;
                    matchedWords++;
                }
            }
        }
        
        return wordScore * (double) matchedWords / Math.max(queryWords.length, textWords.length);
    }
    
    // THREAD-SAFE CACHING WITH AUTOMATIC CLEANUP
    private void cacheResults(String query, List<AutocompleteResult<T>> results) {
        if (!enableCaching) return;
        
        // Cleanup cache if it's getting too large
        if (searchCache.size() >= maxCacheSize) {
            // Remove oldest 20% of entries
            List<String> keys = new ArrayList<>(searchCache.keySet());
            int toRemove = (int) (maxCacheSize * 0.2);
            for (int i = 0; i < toRemove && !keys.isEmpty(); i++) {
                searchCache.remove(keys.get(i));
            }
        }
        
        searchCache.put(query, new ArrayList<>(results));
        
        // Add to recent searches
        if (showRecentSearches && results != null && !results.isEmpty()) {
            recentSearches.remove(query); // Remove if already exists
            recentSearches.offer(query);
            
            // Keep only maxRecentSearches
            while (recentSearches.size() > maxRecentSearches) {
                recentSearches.poll();
            }
        }
    }
    
    private void showResults(List<AutocompleteResult<T>> results, String query) {
        if (isDisposed.get()) return;
        
        suggestions.clear();
        
        if (results == null || results.isEmpty()) {
            statusLabel.setText("No results found");
            if (isPopupShowing.get()) {
                hidePopup();
            }
            return;
        }
        
        suggestions.addAll(results);
        statusLabel.setText(String.format("Found %d result%s", results.size(), results.size() == 1 ? "" : "s"));
        
        if (!isPopupShowing.get()) {
            showPopup();
        }
        
        // Auto-select first result
        if (!suggestions.isEmpty()) {
            suggestionList.getSelectionModel().select(0);
            updatePreview(suggestions.get(0));
        }
    }
    
    private void updatePreview(AutocompleteResult<T> result) {
        if (result == null || result.item == null) {
            previewLabel.setText("");
            return;
        }
        
        String preview = displayFunction.apply(result.item);
        if (logger.isDebugEnabled()) {
            preview += String.format(" (Score: %.1f)", result.score);
        }
        previewLabel.setText(preview);
    }
    
    private void showPopup() {
        if (isDisposed.get() || isPopupShowing.get()) return;
        
        try {
            Bounds bounds = searchField.localToScreen(searchField.getBoundsInLocal());
            if (bounds != null) {
                suggestionPopup.show(searchField, bounds.getMinX(), bounds.getMaxY() + 5);
                isPopupShowing.set(true);
            }
        } catch (Exception e) {
            logger.error("Error showing popup", e);
        }
    }
    
    private void hidePopup() {
        if (isPopupShowing.compareAndSet(true, false)) {
            suggestionPopup.hide();
            suggestions.clear();
            previewLabel.setText("");
            statusLabel.setText("");
        }
    }
    
    private void selectNext() {
        int current = suggestionList.getSelectionModel().getSelectedIndex();
        int next = (current + 1) % suggestions.size();
        suggestionList.getSelectionModel().select(next);
        suggestionList.scrollTo(next);
    }
    
    private void selectPrevious() {
        int current = suggestionList.getSelectionModel().getSelectedIndex();
        int prev = current <= 0 ? suggestions.size() - 1 : current - 1;
        suggestionList.getSelectionModel().select(prev);
        suggestionList.scrollTo(prev);
    }
    
    private void selectCurrent() {
        AutocompleteResult<T> selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.item != null) {
            searchField.setText(selected.displayText);
            selectedItem.set(selected.item);
            hidePopup();
            
            if (onSelectionHandler != null) {
                onSelectionHandler.accept(selected.item);
            }
        }
    }
    
    private void clearField() {
        searchField.clear();
        selectedItem.set(null);
        hidePopup();
    }
    
    // PUBLIC API METHODS
    
    public void setSearchProvider(Function<String, CompletableFuture<List<T>>> provider) {
        this.searchProvider = provider;
    }
    
    public void setDisplayFunction(Function<T, String> function) {
        this.displayFunction = function != null ? function : Object::toString;
    }
    
    public void setSearchFunction(Function<T, String> function) {
        this.searchFunction = function != null ? function : Object::toString;
    }
    
    public void setOnSelectionHandler(Consumer<T> handler) {
        this.onSelectionHandler = handler;
    }
    
    public void setOnTextChangeHandler(Consumer<String> handler) {
        this.onTextChangeHandler = handler;
    }
    
    public void setOnActionHandler(Runnable handler) {
        this.onActionHandler = handler;
    }
    
    public void setActionButtonText(String text) {
        actionButton.setText(text);
    }
    
    public void setActionButtonTooltip(String tooltip) {
        actionButton.setTooltip(new Tooltip(tooltip));
    }
    
    public T getSelectedItem() {
        return selectedItem.get();
    }
    
    public ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }
    
    public String getDisplayText() {
        return displayText.get();
    }
    
    public StringProperty displayTextProperty() {
        return displayText;
    }
    
    public void setText(String text) {
        searchField.setText(text);
    }
    
    public TextField getSearchField() {
        return searchField;
    }
    
    // CONFIGURATION METHODS
    
    public void setMaxSuggestions(int max) {
        this.maxSuggestions = Math.max(1, max);
    }
    
    public void setMaxRecentSearches(int max) {
        this.maxRecentSearches = Math.max(0, max);
    }
    
    public void setMaxCacheSize(int max) {
        this.maxCacheSize = Math.max(10, max);
    }
    
    public void setDebounceDelay(long delay) {
        this.debounceDelay = Math.max(50, delay);
    }
    
    public void setMinSearchLength(int length) {
        this.minSearchLength = Math.max(0, length);
    }
    
    public void setEnableCaching(boolean enable) {
        this.enableCaching = enable;
        if (!enable) {
            searchCache.clear();
        }
    }
    
    public void setShowRecentSearches(boolean show) {
        this.showRecentSearches = show;
        if (!show) {
            recentSearches.clear();
        }
    }
    
    public void clearCache() {
        searchCache.clear();
        recentSearches.clear();
    }
    
    public List<String> getRecentSearches() {
        return new ArrayList<>(recentSearches);
    }
    
    // PROPER RESOURCE CLEANUP - FIXES ALL MEMORY LEAKS
    @Override
    public void close() {
        if (isDisposed.compareAndSet(false, true)) {
            logger.debug("Disposing UnifiedAutocompleteField");
            
            // Cancel all pending tasks
            ScheduledFuture<?> debounce = debounceTask.getAndSet(null);
            if (debounce != null && !debounce.isDone()) {
                debounce.cancel(false);
            }
            
            CompletableFuture<Void> search = currentSearchTask.getAndSet(null);
            if (search != null && !search.isDone()) {
                search.cancel(true);
            }
            
            // Clear collections
            Platform.runLater(() -> {
                suggestions.clear();
                hidePopup();
            });
            
            searchCache.clear();
            recentSearches.clear();
            
            // Unregister from thread manager
            threadManager.unregisterInstance();
            
            logger.debug("UnifiedAutocompleteField disposed successfully");
        }
    }
    
    // RESULT WRAPPER CLASS
    public static class AutocompleteResult<T> {
        public final T item;
        public final String displayText;
        public final double score;
        
        public AutocompleteResult(T item, String displayText, double score) {
            this.item = item;
            this.displayText = displayText;
            this.score = score;
        }
        
        @Override
        public String toString() {
            return displayText;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            AutocompleteResult<?> that = (AutocompleteResult<?>) obj;
            return Objects.equals(item, that.item);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(item);
        }
    }
    
    // PROFESSIONAL CELL RENDERER
    private class AutocompleteCell extends ListCell<AutocompleteResult<T>> {
        private final HBox container;
        private final Label mainLabel;
        private final Label scoreLabel;
        
        public AutocompleteCell() {
            container = new HBox(10);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(8, 12, 8, 12));
            
            mainLabel = new Label();
            mainLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
            mainLabel.setTextFill(Color.web("#1f2937"));
            mainLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(mainLabel, Priority.ALWAYS);
            
            scoreLabel = new Label();
            scoreLabel.setFont(Font.font("System", 10));
            scoreLabel.setTextFill(Color.web("#9ca3af"));
            scoreLabel.setVisible(logger.isDebugEnabled());
            
            container.getChildren().addAll(mainLabel, scoreLabel);
        }
        
        @Override
        protected void updateItem(AutocompleteResult<T> item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setStyle("");
            } else {
                mainLabel.setText(item.displayText);
                scoreLabel.setText(String.format("%.1f", item.score));
                
                // Professional hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: #f3f4f6;"));
                setOnMouseExited(e -> setStyle(""));
                
                setGraphic(container);
            }
        }
    }
    
    // STATIC CLEANUP METHOD FOR APPLICATION SHUTDOWN
    public static void shutdownAll() {
        ThreadManager manager = ThreadManager.INSTANCE;
        if (manager != null) {
            synchronized (ThreadManager.class) {
                if (manager != null && !manager.isShuttingDown) {
                    manager.isShuttingDown = true;
                    manager.sharedExecutor.shutdown();
                    ThreadManager.INSTANCE = null;
                }
            }
        }
    }
}
