package com.company.payroll.loads;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Simple autocomplete handler that doesn't require ControlsFX
 */
public class SimpleAutocompleteHandler {
    private final TextField textField;
    private final Function<String, List<String>> searchFunction;
    private final Consumer<String> onSelection;
    
    private Popup popup;
    private ListView<String> listView;
    private Timer searchTimer;
    private CompletableFuture<Void> currentSearch;
    
    private static final int SEARCH_DELAY_MS = 300;
    private static final int MAX_VISIBLE_ROWS = 10;
    
    public SimpleAutocompleteHandler(TextField textField, 
                                   Function<String, List<String>> searchFunction,
                                   Consumer<String> onSelection) {
        this.textField = textField;
        this.searchFunction = searchFunction;
        this.onSelection = onSelection;
        
        setupAutocomplete();
    }
    
    private void setupAutocomplete() {
        // Create popup with list view
        popup = new Popup();
        popup.setAutoHide(true);
        
        listView = new ListView<>();
        listView.setPrefWidth(textField.getPrefWidth());
        listView.setMaxHeight(200);
        listView.getStyleClass().add("autocomplete-popup");
        
        // Style the popup
        listView.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #ccc;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);
            """);
        
        popup.getContent().add(listView);
        
        // Handle text changes
        textField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.trim().isEmpty() || newText.length() < 1) {
                hidePopup();
                return;
            }
            
            // Cancel previous search
            if (searchTimer != null) {
                searchTimer.cancel();
            }
            
            // Schedule new search
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    performSearch(newText.trim());
                }
            }, SEARCH_DELAY_MS);
        });
        
        // Handle keyboard navigation
        textField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (popup.isShowing()) {
                if (event.getCode() == KeyCode.DOWN) {
                    listView.requestFocus();
                    listView.getSelectionModel().selectFirst();
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    hidePopup();
                    event.consume();
                }
            }
        });
        
        // Handle list selection
        listView.setOnMouseClicked(event -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectItem(selected);
            }
        });
        
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectItem(selected);
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hidePopup();
                textField.requestFocus();
            }
        });
        
        // Hide popup when field loses focus (with delay to allow list selection)
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                Platform.runLater(() -> {
                    if (!listView.isFocused()) {
                        hidePopup();
                    }
                });
            }
        });
    }
    
    private void performSearch(String searchText) {
        if (currentSearch != null && !currentSearch.isDone()) {
            currentSearch.cancel(true);
        }
        
        currentSearch = CompletableFuture.supplyAsync(() -> searchFunction.apply(searchText))
            .thenAcceptAsync(results -> {
                if (results != null && !results.isEmpty()) {
                    showResults(results);
                } else {
                    hidePopup();
                }
            }, Platform::runLater);
    }
    
    private void showResults(List<String> results) {
        ObservableList<String> items = FXCollections.observableArrayList(results);
        listView.setItems(items);
        
        // Adjust height based on items
        int visibleRows = Math.min(items.size(), MAX_VISIBLE_ROWS);
        listView.setPrefHeight(visibleRows * 24 + 2); // Approximate row height
        
        if (!popup.isShowing()) {
            Window window = textField.getScene().getWindow();
            Bounds bounds = textField.localToScreen(textField.getBoundsInLocal());
            popup.show(window, bounds.getMinX(), bounds.getMaxY());
        }
    }
    
    private void selectItem(String item) {
        textField.setText(item);
        if (onSelection != null) {
            onSelection.accept(item);
        }
        hidePopup();
        textField.requestFocus();
        textField.positionCaret(textField.getText().length());
    }
    
    private void hidePopup() {
        if (popup != null && popup.isShowing()) {
            popup.hide();
        }
    }
    
    public void dispose() {
        if (searchTimer != null) {
            searchTimer.cancel();
        }
        if (currentSearch != null && !currentSearch.isDone()) {
            currentSearch.cancel(true);
        }
        hidePopup();
    }
}