package com.company.payroll.loads;

// Java standard library imports (grouped alphabetically)
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Third-party imports
import javax.imageio.ImageIO;

// JavaFX imports
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

// Apache PDFBox imports
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Removed ControlsFX imports - using SimpleAutocompleteHandler instead

// Application-specific imports
import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;
import com.company.payroll.trucks.Truck;
import com.company.payroll.trucks.TruckDAO;
import com.company.payroll.exception.DataAccessException;
import com.company.payroll.config.DocumentManagerConfig;
import com.company.payroll.config.DocumentManagerSettingsDialog;
import javafx.application.Platform;

// JavaFX concurrency imports for scheduled tasks
import javafx.concurrent.Task;

// JavaFX animation imports for scheduled tasks
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

public class LoadsPanel extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(LoadsPanel.class);
    
    private final LoadDAO loadDAO = new LoadDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TrailerDAO trailerDAO = new TrailerDAO();
    private final TruckDAO truckDAO = new TruckDAO();
    private final EnterpriseDataCacheManager cacheManager = EnterpriseDataCacheManager.getInstance();
    private final AddressBookManager addressBookManager = new AddressBookManager(loadDAO);
    private final ObservableList<Load> allLoads = FXCollections.observableArrayList();
    private final ObservableList<Employee> allDrivers = FXCollections.observableArrayList();
    private final FilteredList<Employee> activeDrivers = new FilteredList<>(allDrivers, 
        driver -> driver.getStatus() == Employee.Status.ACTIVE);
    private final ObservableList<Trailer> allTrailers = FXCollections.observableArrayList();
    private final ObservableList<Truck> allTrucks = FXCollections.observableArrayList();
    private final ObservableList<String> allCustomers = FXCollections.observableArrayList();
    private final ObservableList<String> allBillingEntities = FXCollections.observableArrayList();

    private final List<StatusTab> statusTabs = new ArrayList<>();
    private Consumer<List<Load>> syncToTriumphCallback;
    
    // Customer-Location synchronization manager - using optimized version
    private final CustomerLocationSyncManagerOptimized syncManager = new CustomerLocationSyncManagerOptimized(loadDAO);
    
    // Add listener interface and list
    public interface LoadDataChangeListener {
        void onLoadDataChanged();
    }
    
    private final List<LoadDataChangeListener> loadDataChangeListeners = new ArrayList<>();

    // Document storage directory
    private String DOCUMENT_STORAGE_PATH = DocumentManagerConfig.getLoadsStoragePath();
    
    // Timeline for automatic status updates
    private Timeline statusUpdateTimeline;
    
    // Helper method to format driver display text
    private String formatDriverDisplay(Employee e) {
        if (e == null) return "";
        String text = e.getName() + (e.getTruckUnit() != null ? " (" + e.getTruckUnit() + ")" : "");
        if (e.getStatus() != Employee.Status.ACTIVE) {
            text += " [" + e.getStatus() + "]";
        }
        return text;
    }
    
    // Enhanced modern button styling method
    private Button createStyledButton(String text, String bgColor, String textColor) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 10 20 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-border-radius: 6; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 3, 0, 0, 2); " +
            "-fx-font-size: 13px;",
            bgColor, textColor
        ));
        
        // Enhanced hover effect with smooth transitions
        button.setOnMouseEntered(e -> 
            button.setStyle(String.format(
                "-fx-background-color: derive(%s, -15%%); " +
                "-fx-text-fill: %s; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 10 20 10 20; " +
                "-fx-background-radius: 6; " +
                "-fx-border-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3); " +
                "-fx-font-size: 13px; -fx-scale-x: 1.02; -fx-scale-y: 1.02;",
                bgColor, textColor
            ))
        );
        
        button.setOnMouseExited(e -> 
            button.setStyle(String.format(
                "-fx-background-color: %s; " +
                "-fx-text-fill: %s; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 10 20 10 20; " +
                "-fx-background-radius: 6; " +
                "-fx-border-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 3, 0, 0, 2); " +
                "-fx-font-size: 13px; -fx-scale-x: 1.0; -fx-scale-y: 1.0;",
                bgColor, textColor
            ))
        );
        
        return button;
    }
    
    /**
     * Format a location address from its components
     */
    private String formatLocationAddress(String address, String city, String state) {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.trim().isEmpty()) {
            sb.append(address.trim());
        }
        if (city != null && !city.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city.trim());
        }
        if (state != null && !state.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state.trim());
        }
        return sb.toString();
    }
    
    /**
     * Create modern section with enhanced styling
     */
    private VBox createModernSection(String title, String description) {
        VBox section = new VBox(12);
        section.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #e5e7eb; " +
            "-fx-border-radius: 12px; " +
            "-fx-background-radius: 12px; " +
            "-fx-padding: 20px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);"
        );
        
        // Modern header with icon and description
        VBox header = new VBox(5);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-font-size: 16px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #111827;"
        );
        
        Label descLabel = new Label(description);
        descLabel.setStyle(
            "-fx-font-size: 12px; " +
            "-fx-text-fill: #6b7280; " +
            "-fx-wrap-text: true;"
        );
        descLabel.setWrapText(true);
        
        header.getChildren().addAll(titleLabel, descLabel);
        section.getChildren().add(header);
        
        return section;
    }
    
    // Inline button styling for table cells
    private Button createInlineButton(String text, String bgColor, String textColor) {
        Button button = new Button(text);
        String baseStyle = String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 10px; " +
            "-fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 3; " +
            "-fx-cursor: hand;",
            bgColor, textColor
        );
        
        button.setStyle(baseStyle);
        button.setCursor(Cursor.HAND);
        
        // Simpler hover effect for inline buttons
        button.setOnMouseEntered(e -> 
            button.setStyle(String.format(
                "-fx-background-color: derive(%s, -10%%); " +
                "-fx-text-fill: %s; " +
                "-fx-font-size: 10px; " +
                "-fx-padding: 3 8 3 8; " +
                "-fx-background-radius: 3; " +
                "-fx-cursor: hand;",
                bgColor, textColor
            ))
        );
        
        button.setOnMouseExited(e -> button.setStyle(baseStyle));
        
        return button;
    }
    
    /**
     * Creates an additional location row for pickup or drop locations
     * @param labelText The label text for the row
     * @param customerValue The current customer value to use - null means independent (no default)
     * @param isPickup True if this is a pickup location, false for drop location
     * @param parentContainer The parent container for removing the row if needed
     * @return HBox containing the location row
     */
    private HBox createAdditionalLocationRow(String labelText, String customerValue, boolean isPickup, VBox parentContainer) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("modern-additional-location-row");
        
        Label rowLabel = new Label(labelText);
        rowLabel.getStyleClass().add("modern-form-label");
        rowLabel.setPrefWidth(120);
        
        // Customer combo box for additional stops - INDEPENDENT from main customer
        ComboBox<String> customerCombo = new ComboBox<>();
        customerCombo.setPromptText("Select customer");
        customerCombo.setEditable(true);
        customerCombo.setPrefWidth(180);
        customerCombo.getStyleClass().add("modern-combo-box");
        
        // Load customers asynchronously - NO DEFAULT TO MAIN CUSTOMER
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> {
                loadCustomersIntoComboBox(customerCombo);
                // Only set customer value if explicitly provided (when editing existing load)
                if (customerValue != null && !customerValue.trim().isEmpty()) {
                    customerCombo.setValue(customerValue);
                }
                // Do NOT default to main customer - keep independent
            });
        });
        
        // Add button for new customer
        Button addCustomerBtn = new Button("+");
        addCustomerBtn.getStyleClass().add("inline-add-button");
        addCustomerBtn.setStyle("-fx-background-color: #28a745;");
        addCustomerBtn.setTooltip(new Tooltip("Add new customer"));
        addCustomerBtn.setOnAction(e -> showAddCustomerDialog(customerCombo));
        
        // Location combo box
        ComboBox<String> locationCombo = new ComboBox<>();
        locationCombo.setPromptText("Select/Enter location");
        locationCombo.setEditable(true);
        locationCombo.setPrefWidth(200);
        locationCombo.getStyleClass().add("modern-combo-box");
        
        // Initialize dropdown with all locations first
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> {
                enableLocationFiltering(locationCombo);
                updateLocationDropdown(locationCombo, null); // Load all locations initially
            });
        });
        
        // Update locations when customer changes
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLocationDropdown(locationCombo, newVal);
        });
        
        // Add button for new location
        Button addLocationBtn = new Button("+");
        addLocationBtn.getStyleClass().add("inline-add-button");
        addLocationBtn.setStyle("-fx-background-color: #28a745;");
        addLocationBtn.setTooltip(new Tooltip("Add new location"));
        addLocationBtn.setOnAction(e -> {
            String customer = customerCombo.getValue();
            if (customer == null || customer.trim().isEmpty()) {
                showInfo("Please select a customer first to add a location");
                return;
            }
            showAddLocationDialogForCombo(locationCombo, customer);
        });

        DatePicker datePicker = new DatePicker();
        datePicker.setPrefWidth(120);
        Spinner<LocalTime> timeSpinner = createTimeSpinner24Hour();
        timeSpinner.setPrefWidth(90);
        
        // Remove button
        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("modern-remove-button");
        removeBtn.setTooltip(new Tooltip("Remove this stop"));
        removeBtn.setOnAction(e -> {
            // Clean up data associations before removing
            locationCombo.setUserData(null);
            locationCombo.getProperties().clear();
            
            // Remove from UI
            parentContainer.getChildren().remove(row);
            // Remove from the appropriate list in dialog fields
            if (isPickup) {
                int idx = dialogFields.additionalPickupLocations.indexOf(locationCombo);
                dialogFields.additionalPickupLocations.remove(locationCombo);
                if (idx >= 0) {
                    dialogFields.additionalPickupDates.remove(idx);
                    dialogFields.additionalPickupTimes.remove(idx);
                }
            } else {
                int idx = dialogFields.additionalDropLocations.indexOf(locationCombo);
                dialogFields.additionalDropLocations.remove(locationCombo);
                if (idx >= 0) {
                    dialogFields.additionalDropDates.remove(idx);
                    dialogFields.additionalDropTimes.remove(idx);
                }
            }
            
            logger.debug("Removed additional {} location. Remaining locations: {}", 
                isPickup ? "pickup" : "drop", 
                isPickup ? dialogFields.additionalPickupLocations.size() : dialogFields.additionalDropLocations.size());
        });
        
        row.getChildren().addAll(rowLabel, customerCombo, addCustomerBtn,
                locationCombo, addLocationBtn, datePicker, timeSpinner, removeBtn);
        
        // Add to the appropriate list in dialog fields
        if (isPickup) {
            dialogFields.additionalPickupLocations.add(locationCombo);
            dialogFields.additionalPickupDates.add(datePicker);
            dialogFields.additionalPickupTimes.add(timeSpinner);
        } else {
            dialogFields.additionalDropLocations.add(locationCombo);
            dialogFields.additionalDropDates.add(datePicker);
            dialogFields.additionalDropTimes.add(timeSpinner);
        }
        
        // IMPORTANT: Store the customer combo reference in a way that won't get lost
        // This ensures customer data persistence
        customerCombo.setId("customer-" + System.currentTimeMillis());
        locationCombo.getProperties().put("customerCombo", customerCombo);
        
        // Store customer combo reference as user data for later retrieval
        locationCombo.setUserData(customerCombo);
        
        // Set up bi-directional sync between customer and location
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                updateLocationDropdown(locationCombo, newVal);
            }
        });
        
        // Auto-suggest customer when location is selected
        locationCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty() && 
                (customerCombo.getValue() == null || customerCombo.getValue().trim().isEmpty())) {
                updateCustomerSuggestions(customerCombo, locationCombo, newVal);
            }
        });
        
        return row;
    }

    /**
     * Calculates the next sequence number for additional locations based on existing rows
     * This ensures proper numbering that continues from the highest existing sequence
     */
    private int calculateNextSequenceNumber(VBox container, String labelPrefix) {
        int maxSequence = 1; // Start from 1 since main location is sequence 1
        
        // Examine existing rows in the container
        for (Node child : container.getChildren()) {
            if (child instanceof HBox) {
                HBox row = (HBox) child;
                if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label) {
                    Label label = (Label) row.getChildren().get(0);
                    String labelText = label.getText();
                    
                    // Extract sequence number from label (e.g., "Additional Pickup 3:")
                    if (labelText.startsWith(labelPrefix)) {
                        try {
                            String[] parts = labelText.split(" ");
                            if (parts.length >= 3) {
                                String numberPart = parts[2].replace(":", "");
                                int sequence = Integer.parseInt(numberPart);
                                maxSequence = Math.max(maxSequence, sequence);
                            }
                        } catch (NumberFormatException e) {
                            // Ignore parsing errors and continue
                        }
                    }
                }
            }
        }
        
        // Return the next sequence number
        return maxSequence + 1;
    }

    public LoadsPanel() {
        logger.info("Initializing Enterprise LoadsPanel");
        
        // Initialize cache manager and load essential data
        initializeCacheManager();
        
        // Document storage directory is handled by DocumentManagerConfig
        
        // Load trucks data for dropdown
        loadTrucksData();
        
        reloadAll();
        
        // Initialize automatic status update timeline
        initializeStatusUpdateScheduler();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        statusTabs.add(makeActiveTab());
        statusTabs.add(makeStatusTab("Cancelled", Load.Status.CANCELLED));
        statusTabs.add(makeAllLoadsTab());
        statusTabs.add(makeCustomerSettingsTab());

        for (StatusTab sTab : statusTabs) {
            tabs.getTabs().add(sTab.tab);
        }

        setCenter(tabs);
        logger.info("LoadsPanel initialization complete");
    }
    
    /**
     * Initializes the automatic status update scheduler
     * Runs every 5 minutes to check for late loads
     */
    private void initializeStatusUpdateScheduler() {
        logger.info("Initializing automatic status update scheduler");
        
        // Create timeline that runs every 5 minutes
        statusUpdateTimeline = new Timeline(
            new KeyFrame(Duration.minutes(5), e -> {
                checkAndUpdateLateStatuses();
            })
        );
        statusUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        statusUpdateTimeline.play();
        
        // Also run immediately on startup
        Platform.runLater(() -> {
            checkAndUpdateLateStatuses();
        });
        
        logger.info("Status update scheduler started - will check every 5 minutes");
    }
    
    /**
     * Checks and updates late load statuses
     */
    private void checkAndUpdateLateStatuses() {
        logger.debug("Running automatic status update check");
        
        Task<Integer> updateTask = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                return loadDAO.updateLateStatuses();
            }
            
            @Override
            protected void succeeded() {
                Integer updatedCount = getValue();
                if (updatedCount != null && updatedCount > 0) {
                    logger.info("Updated {} loads with late status", updatedCount);
                    // Refresh the table to show updated statuses
                    Platform.runLater(() -> {
                        reloadAll();
                        // Notify listeners
                        notifyLoadDataChangeListeners();
                    });
                }
            }
            
            @Override
            protected void failed() {
                Throwable exception = getException();
                logger.error("Failed to update late statuses", exception);
            }
        };
        
        Thread updateThread = new Thread(updateTask);
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    /**
     * Stops the automatic status update scheduler
     * Should be called when the panel is being disposed
     */
    public void stopStatusUpdateScheduler() {
        if (statusUpdateTimeline != null) {
            statusUpdateTimeline.stop();
            logger.info("Status update scheduler stopped");
        }
    }
    
    public void addLoadDataChangeListener(LoadDataChangeListener listener) {
        loadDataChangeListeners.add(listener);
    }

    public void removeLoadDataChangeListener(LoadDataChangeListener listener) {
        loadDataChangeListeners.remove(listener);
    }
    
    private void notifyLoadDataChangeListeners() {
        for (LoadDataChangeListener listener : loadDataChangeListeners) {
            listener.onLoadDataChanged();
        }
    }

    private void notifyLoadDataChanged() {
        for (LoadDataChangeListener listener : loadDataChangeListeners) {
            listener.onLoadDataChanged();
        }
    }

    public void setSyncToTriumphCallback(Consumer<List<Load>> callback) {
        logger.debug("Setting sync to Triumph callback");
        this.syncToTriumphCallback = callback;
    }

    private static class StatusTab {
        Tab tab;
        TableView<Load> table;
        FilteredList<Load> filteredList;
    }

    private StatusTab makeActiveTab() {
        logger.debug("Creating Active Loads tab");
        StatusTab tab = new StatusTab();
        
        // Default filter: last 10 days of active loads to improve performance
        LocalDate tenDaysAgo = LocalDate.now().minusDays(10);
        tab.filteredList = new FilteredList<>(allLoads, l -> {
            boolean statusFilter = l.getStatus() != Load.Status.CANCELLED;
            
            // Check both pickup and delivery dates for recent loads
            boolean dateFilter = false;
            if (l.getPickUpDate() != null && !l.getPickUpDate().isBefore(tenDaysAgo)) {
                dateFilter = true;
            } else if (l.getDeliveryDate() != null && !l.getDeliveryDate().isBefore(tenDaysAgo)) {
                dateFilter = true;
            }
            
            return statusFilter && dateFilter;
        });

        TableView<Load> table = makeTableView(tab.filteredList, true);

        // Enhanced search controls
        TextField loadNumField = new TextField();
        loadNumField.setPromptText("Load #");
        loadNumField.setPrefWidth(100);
        
        TextField truckUnitField = new TextField();
        truckUnitField.setPromptText("Truck/Unit");
        truckUnitField.setPrefWidth(100);
        
        TextField trailerNumberField = new TextField();
        trailerNumberField.setPromptText("Trailer #");
        trailerNumberField.setPrefWidth(100);
        
        ComboBox<Employee> driverBox = new ComboBox<>(activeDrivers);
        driverBox.setPromptText("Driver");
        driverBox.setPrefWidth(150);
        driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(formatDriverDisplay(e));
                    if (e.getStatus() != Employee.Status.ACTIVE) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        driverBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(formatDriverDisplay(e));
                    if (e.getStatus() != Employee.Status.ACTIVE) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        ComboBox<Trailer> trailerBox = new ComboBox<>(allTrailers);
        trailerBox.setPromptText("Trailer");
        trailerBox.setPrefWidth(150);
        trailerBox.setCellFactory(cb -> new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        trailerBox.setButtonCell(new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        
        ComboBox<String> customerBox = new ComboBox<>(allBillingEntities);
        customerBox.setPromptText("Bill To");
        customerBox.setPrefWidth(150);
        
        // Set default date range to last 10 days
        DatePicker startDatePicker = new DatePicker(LocalDate.now().minusDays(10));
        startDatePicker.setPromptText("Start Date");
        startDatePicker.setPrefWidth(120);
        
        DatePicker endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPromptText("End Date");
        endDatePicker.setPrefWidth(120);
        
        Button clearSearchBtn = createStyledButton("🔄 Clear Search", "#6c757d", "white");

        // Add info label about default date range
        Label dateRangeInfo = new Label("📅 Showing loads from last 10 days by default. Adjust date range to see more.");
        dateRangeInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 11px; -fx-font-style: italic;");
        
        HBox searchBox1 = new HBox(8, new Label("Search:"), loadNumField, truckUnitField, trailerNumberField, driverBox);
        HBox searchBox2 = new HBox(8, new Label("Bill To:"), customerBox, new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker, clearSearchBtn);
        VBox searchContainer = new VBox(5, searchBox1, searchBox2, dateRangeInfo);
        searchContainer.setPadding(new Insets(10));
        searchContainer.setStyle("-fx-background-color:#f7f9ff; -fx-border-color: #e0e0e0; -fx-border-radius: 5; -fx-background-radius: 5;");

        Button addBtn = createStyledButton("➕ Add", "#28a745", "white");
        Button editBtn = createStyledButton("✏️ Edit", "#ffc107", "black");
        Button deleteBtn = createStyledButton("🗑️ Delete", "#dc3545", "white");
        Button exportBtn = createStyledButton("📊 Export CSV", "#17a2b8", "white");
        Button refreshBtn = createStyledButton("🔄 Refresh", "#6c757d", "white");
        Button syncToTriumphBtn = createStyledButton("☁️ Sync to MyTriumph", "#007bff", "white");
        Button confirmationBtn = createStyledButton("📄 Load Confirmation", "#6f42c1", "white");

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, confirmationBtn, syncToTriumphBtn, exportBtn, refreshBtn);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(4, 10, 8, 10));

        table.setRowFactory(tv -> {
            TableRow<Load> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for load: {}", row.getItem().getLoadNumber());
                    showLoadDialog(row.getItem(), false);
                }
            });
            return row;
        });

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Disable buttons that require selection
        editBtn.setDisable(true);
        deleteBtn.setDisable(true);
        confirmationBtn.setDisable(true);
        
        // Enable/disable buttons based on selection
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            editBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
            confirmationBtn.setDisable(!hasSelection);
        });

        // Enhanced filtering with date range
        Runnable refilter = () -> {
            logger.debug("Applying filters to Active Loads");
            
            // Base predicate for active loads
            Predicate<Load> pred = l -> l.getStatus() != Load.Status.CANCELLED;

            String loadNum = loadNumField.getText().trim().toLowerCase();
            if (!loadNum.isEmpty()) {
                pred = pred.and(l -> l.getLoadNumber() != null && l.getLoadNumber().toLowerCase().contains(loadNum));
            }
            
            String truckUnit = truckUnitField.getText().trim().toLowerCase();
            if (!truckUnit.isEmpty()) {
                pred = pred.and(l -> l.getTruckUnitSnapshot() != null && l.getTruckUnitSnapshot().toLowerCase().contains(truckUnit));
            }
            
            String trailerNumber = trailerNumberField.getText().trim().toLowerCase();
            if (!trailerNumber.isEmpty()) {
                pred = pred.and(l -> l.getTrailerNumber() != null && l.getTrailerNumber().toLowerCase().contains(trailerNumber));
            }
            
            Employee driver = driverBox.getValue();
            if (driver != null) {
                pred = pred.and(l -> l.getDriver() != null && l.getDriver().getId() == driver.getId());
            }
            
            Trailer trailer = trailerBox.getValue();
            if (trailer != null) {
                pred = pred.and(l -> l.getTrailer() != null && l.getTrailer().getId() == trailer.getId());
            }
            
            String customer = customerBox.getValue();
            if (customer != null && !customer.trim().isEmpty()) {
                pred = pred.and(l -> customer.equalsIgnoreCase(l.getBillTo()));
            }
            
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null || endDate != null) {
                pred = pred.and(l -> {
                    // Check both pickup and delivery dates
                    boolean inRange = false;
                    
                    if (l.getPickUpDate() != null) {
                        boolean pickupInRange = true;
                        if (startDate != null) pickupInRange = !l.getPickUpDate().isBefore(startDate);
                        if (endDate != null) pickupInRange = pickupInRange && !l.getPickUpDate().isAfter(endDate);
                        if (pickupInRange) inRange = true;
                    }
                    
                    if (l.getDeliveryDate() != null) {
                        boolean deliveryInRange = true;
                        if (startDate != null) deliveryInRange = !l.getDeliveryDate().isBefore(startDate);
                        if (endDate != null) deliveryInRange = deliveryInRange && !l.getDeliveryDate().isAfter(endDate);
                        if (deliveryInRange) inRange = true;
                    }
                    
                    return inRange;
                });
            }
            
            tab.filteredList.setPredicate(pred);
        };

        // Add listeners
        loadNumField.textProperty().addListener((obs, o, n) -> refilter.run());
        truckUnitField.textProperty().addListener((obs, o, n) -> refilter.run());
        trailerNumberField.textProperty().addListener((obs, o, n) -> refilter.run());
        driverBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        trailerBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        customerBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        startDatePicker.valueProperty().addListener((obs, o, n) -> refilter.run());
        endDatePicker.valueProperty().addListener((obs, o, n) -> refilter.run());
        
        clearSearchBtn.setOnAction(e -> {
            logger.info("Clearing search filters");
            loadNumField.clear();
            truckUnitField.clear();
            trailerNumberField.clear();
            driverBox.setValue(null);
            trailerBox.setValue(null);
            customerBox.setValue(null);
            // Reset to default date range (last 10 days)
            startDatePicker.setValue(LocalDate.now().minusDays(10));
            endDatePicker.setValue(LocalDate.now());
            refilter.run();
        });

        // Button actions
        addBtn.setOnAction(e -> {
            logger.info("Add load button clicked");
            showLoadDialog(null, true);
        });
        editBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit load button clicked for: {}", selected.getLoadNumber());
                showLoadDialog(selected, false);
            }
        });
        deleteBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete load button clicked for: {}", selected.getLoadNumber());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete load \"" + selected.getLoadNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of load: {}", selected.getLoadNumber());
                        loadDAO.delete(selected.getId());
                        reloadAll();
                        notifyLoadDataChanged(); // Notify listeners
                    }
                });
            }
        });
        confirmationBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Load Confirmation button clicked for: {}", selected.getLoadNumber());
                LoadConfirmationPreviewDialog previewDialog = new LoadConfirmationPreviewDialog(selected);
                previewDialog.show();
            } else {
                showError("Please select a load first.");
            }
        });
        exportBtn.setOnAction(e -> {
            logger.info("Export CSV button clicked");
            exportCSV(table);
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            reloadAll();
            notifyLoadDataChanged(); // Notify listeners
        });

        syncToTriumphBtn.setOnAction(e -> {
            logger.info("Sync to MyTriumph button clicked");
            List<Load> toSync = allLoads.stream()
                .filter(l -> (l.getStatus() == Load.Status.DELIVERED || l.getStatus() == Load.Status.PAID))
                .filter(l -> l.getPONumber() != null && !l.getPONumber().isEmpty())
                .collect(Collectors.toList());
            
            if (toSync.isEmpty()) {
                logger.info("No delivered/paid loads with PO numbers to sync");
                showInfo("No delivered/paid loads with PO numbers to sync.");
                return;
            }
            
            if (syncToTriumphCallback != null) {
                logger.info("Syncing {} loads to Invoice audit", toSync.size());
                syncToTriumphCallback.accept(toSync);
                showInfo("Syncing " + toSync.size() + " loads to Invoice audit.");
            } else {
                logger.warn("MyTriumph sync callback not configured");
                showInfo("MyTriumph sync not configured.");
            }
        });
        
        // Add Check Late button after creating button box
        Button checkLateBtn = createStyledButton("⏰ Check Late", "#ff9900", "white");
        checkLateBtn.setTooltip(new Tooltip("Check for loads that are past pickup or delivery times"));
        checkLateBtn.setOnAction(e -> {
            logger.info("Check Late button clicked - manually checking for late loads");
            checkAndUpdateLateStatuses();
            showInfo("Checking for late loads...");
        });
        buttonBox.getChildren().add(5, checkLateBtn); // Add after syncToTriumphBtn

        // Wrap table in ScrollPane for horizontal scrolling
        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setFitToWidth(false);  // Allow horizontal scrolling
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setPrefViewportHeight(600);
        
        VBox vbox = new VBox(searchContainer, tableScrollPane, buttonBox);
        vbox.setSpacing(10);
        tab.tab = new Tab("Active Loads", vbox);
        tab.table = table;
        refilter.run();
        return tab;
    }

    private StatusTab makeAllLoadsTab() {
        logger.debug("Creating All Loads tab with advanced filtering");
        StatusTab tab = new StatusTab();
        
        // Default filter: last 45 days of all loads
        LocalDate fortyFiveDaysAgo = LocalDate.now().minusDays(45);
        tab.filteredList = new FilteredList<>(allLoads, l -> {
            // Check both pickup and delivery dates for recent loads
            boolean dateFilter = false;
            if (l.getPickUpDate() != null && !l.getPickUpDate().isBefore(fortyFiveDaysAgo)) {
                dateFilter = true;
            } else if (l.getDeliveryDate() != null && !l.getDeliveryDate().isBefore(fortyFiveDaysAgo)) {
                dateFilter = true;
            }
            
            return dateFilter;
        });

        TableView<Load> table = makeTableView(tab.filteredList, true);

        // Enhanced search controls (same as Active Loads)
        TextField loadNumField = new TextField();
        loadNumField.setPromptText("Load #");
        loadNumField.setPrefWidth(100);
        
        TextField truckUnitField = new TextField();
        truckUnitField.setPromptText("Truck/Unit");
        truckUnitField.setPrefWidth(100);
        
        TextField trailerNumberField = new TextField();
        trailerNumberField.setPromptText("Trailer #");
        trailerNumberField.setPrefWidth(100);
        
        ComboBox<Employee> driverBox = new ComboBox<>(activeDrivers);
        driverBox.setPromptText("Driver");
        driverBox.setPrefWidth(150);
        driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(formatDriverDisplay(e));
                    if (e.getStatus() != Employee.Status.ACTIVE) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        driverBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(formatDriverDisplay(e));
                    if (e.getStatus() != Employee.Status.ACTIVE) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        ComboBox<Trailer> trailerBox = new ComboBox<>(allTrailers);
        trailerBox.setPromptText("Trailer");
        trailerBox.setPrefWidth(150);
        trailerBox.setCellFactory(cb -> new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        trailerBox.setButtonCell(new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        
        ComboBox<String> customerBox = new ComboBox<>(allBillingEntities);
        customerBox.setPromptText("Bill To");
        customerBox.setPrefWidth(150);
        
        // Set default date range to last 45 days
        DatePicker startDatePicker = new DatePicker(LocalDate.now().minusDays(45));
        startDatePicker.setPromptText("Start Date");
        startDatePicker.setPrefWidth(120);
        
        DatePicker endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPromptText("End Date");
        endDatePicker.setPrefWidth(120);
        
        Button clearSearchBtn = createStyledButton("🔄 Clear Search", "#6c757d", "white");

        // Add info label about default date range
        Label dateRangeInfo = new Label("📅 Showing loads from last 45 days by default. Adjust date range to see more.");
        dateRangeInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 11px; -fx-font-style: italic;");
        
        HBox searchBox1 = new HBox(8, new Label("Search:"), loadNumField, truckUnitField, trailerNumberField, driverBox);
        HBox searchBox2 = new HBox(8, new Label("Bill To:"), customerBox, new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker, clearSearchBtn);
        VBox searchContainer = new VBox(5, searchBox1, searchBox2, dateRangeInfo);
        searchContainer.setPadding(new Insets(10));
        searchContainer.setStyle("-fx-background-color:#f7f9ff; -fx-border-color: #e0e0e0; -fx-border-radius: 5; -fx-background-radius: 5;");

        Button addBtn = createStyledButton("➕ Add", "#28a745", "white");
        Button editBtn = createStyledButton("✏️ Edit", "#ffc107", "black");
        Button deleteBtn = createStyledButton("🗑️ Delete", "#dc3545", "white");
        Button exportBtn = createStyledButton("📊 Export CSV", "#17a2b8", "white");
        Button refreshBtn = createStyledButton("🔄 Refresh", "#6c757d", "white");

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, exportBtn, refreshBtn);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(4, 10, 8, 10));

        table.setRowFactory(tv -> {
            TableRow<Load> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for load: {}", row.getItem().getLoadNumber());
                    showLoadDialog(row.getItem(), false);
                }
            });
            return row;
        });

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Disable buttons that require selection
        editBtn.setDisable(true);
        deleteBtn.setDisable(true);
        
        // Enable/disable buttons based on selection
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            editBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        // Enhanced filtering with date range
        Runnable refilter = () -> {
            logger.debug("Applying filters to All Loads");
            
            // Base predicate for all loads (no status filter)
            Predicate<Load> pred = l -> true;

            String loadNum = loadNumField.getText().trim().toLowerCase();
            if (!loadNum.isEmpty()) {
                pred = pred.and(l -> l.getLoadNumber() != null && l.getLoadNumber().toLowerCase().contains(loadNum));
            }
            
            String truckUnit = truckUnitField.getText().trim().toLowerCase();
            if (!truckUnit.isEmpty()) {
                pred = pred.and(l -> l.getTruckUnitSnapshot() != null && l.getTruckUnitSnapshot().toLowerCase().contains(truckUnit));
            }
            
            String trailerNumber = trailerNumberField.getText().trim().toLowerCase();
            if (!trailerNumber.isEmpty()) {
                pred = pred.and(l -> l.getTrailerNumber() != null && l.getTrailerNumber().toLowerCase().contains(trailerNumber));
            }
            
            Employee driver = driverBox.getValue();
            if (driver != null) {
                pred = pred.and(l -> l.getDriver() != null && l.getDriver().getId() == driver.getId());
            }
            
            Trailer trailer = trailerBox.getValue();
            if (trailer != null) {
                pred = pred.and(l -> l.getTrailer() != null && l.getTrailer().getId() == trailer.getId());
            }
            
            String customer = customerBox.getValue();
            if (customer != null && !customer.trim().isEmpty()) {
                pred = pred.and(l -> customer.equalsIgnoreCase(l.getBillTo()));
            }
            
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null || endDate != null) {
                pred = pred.and(l -> {
                    // Check both pickup and delivery dates
                    boolean inRange = false;
                    
                    if (l.getPickUpDate() != null) {
                        boolean pickupInRange = true;
                        if (startDate != null) pickupInRange = !l.getPickUpDate().isBefore(startDate);
                        if (endDate != null) pickupInRange = pickupInRange && !l.getPickUpDate().isAfter(endDate);
                        if (pickupInRange) inRange = true;
                    }
                    
                    if (l.getDeliveryDate() != null) {
                        boolean deliveryInRange = true;
                        if (startDate != null) deliveryInRange = !l.getDeliveryDate().isBefore(startDate);
                        if (endDate != null) deliveryInRange = deliveryInRange && !l.getDeliveryDate().isAfter(endDate);
                        if (deliveryInRange) inRange = true;
                    }
                    
                    return inRange;
                });
            }
            
            tab.filteredList.setPredicate(pred);
        };

        // Add listeners
        loadNumField.textProperty().addListener((obs, o, n) -> refilter.run());
        truckUnitField.textProperty().addListener((obs, o, n) -> refilter.run());
        trailerNumberField.textProperty().addListener((obs, o, n) -> refilter.run());
        driverBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        trailerBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        customerBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        startDatePicker.valueProperty().addListener((obs, o, n) -> refilter.run());
        endDatePicker.valueProperty().addListener((obs, o, n) -> refilter.run());

        // Button actions
        addBtn.setOnAction(e -> {
            logger.info("Add load button clicked in All Loads tab");
            showLoadDialog(null, true);
        });
        editBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit load button clicked for: {} in All Loads tab", selected.getLoadNumber());
                showLoadDialog(selected, false);
            }
        });
        deleteBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete load button clicked for: {} in All Loads tab", selected.getLoadNumber());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete load \"" + selected.getLoadNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of load: {}", selected.getLoadNumber());
                        loadDAO.delete(selected.getId());
                        reloadAll();
                        notifyLoadDataChanged();
                    }
                });
            }
        });
        exportBtn.setOnAction(e -> {
            logger.info("Export CSV button clicked in All Loads tab");
            exportCSV(table);
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked in All Loads tab");
            reloadAll();
            notifyLoadDataChanged();
        });
        clearSearchBtn.setOnAction(e -> {
            logger.info("Clear search button clicked in All Loads tab");
            loadNumField.clear();
            truckUnitField.clear();
            trailerNumberField.clear();
            driverBox.setValue(null);
            trailerBox.setValue(null);
            customerBox.setValue(null);
            startDatePicker.setValue(LocalDate.now().minusDays(45));
            endDatePicker.setValue(LocalDate.now());
            refilter.run();
        });

        // Wrap table in ScrollPane for horizontal scrolling
        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setFitToWidth(false);  // Allow horizontal scrolling
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setPrefViewportHeight(600);
        
        VBox vbox = new VBox(searchContainer, tableScrollPane, buttonBox);
        vbox.setSpacing(10);
        tab.tab = new Tab("All Loads", vbox);
        tab.table = table;
        refilter.run();
        return tab;
    }

    private StatusTab makeStatusTab(String title, Load.Status filterStatus) {
        logger.debug("Creating {} tab", title);
        StatusTab statusTab = new StatusTab();
        statusTab.filteredList = new FilteredList<>(allLoads, l -> filterStatus == null || l.getStatus() == filterStatus);
        TableView<Load> table = makeTableView(statusTab.filteredList, filterStatus == null);

        Button addBtn = createStyledButton("➕ Add", "#28a745", "white");
        Button editBtn = createStyledButton("✏️ Edit", "#ffc107", "black");
        Button deleteBtn = createStyledButton("🗑️ Delete", "#dc3545", "white");
        Button exportBtn = createStyledButton("📊 Export CSV", "#17a2b8", "white");
        Button refreshBtn = createStyledButton("🔄 Refresh", "#6c757d", "white");

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, exportBtn, refreshBtn);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 10, 5, 10));

        addBtn.setOnAction(e -> {
            logger.info("Add load button clicked in {} tab", title);
            showLoadDialog(null, true);
        });
        editBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit load button clicked for: {} in {} tab", selected.getLoadNumber(), title);
                showLoadDialog(selected, false);
            }
        });
        deleteBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete load button clicked for: {} in {} tab", selected.getLoadNumber(), title);
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete load \"" + selected.getLoadNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of load: {}", selected.getLoadNumber());
                        loadDAO.delete(selected.getId());
                        reloadAll();
                        notifyLoadDataChanged(); // Notify listeners
                    }
                });
            }
        });
        exportBtn.setOnAction(e -> {
            logger.info("Export CSV button clicked in {} tab", title);
            exportCSV(table);
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked in {} tab", title);
            reloadAll();
            notifyLoadDataChanged(); // Notify listeners
        });

        table.setRowFactory(tv -> {
            TableRow<Load> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for load: {} in {} tab", row.getItem().getLoadNumber(), title);
                    showLoadDialog(row.getItem(), false);
                }
            });
            return row;
        });

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Wrap table in ScrollPane for horizontal scrolling
        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setFitToWidth(false);  // Allow horizontal scrolling
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setPrefViewportHeight(600);
        
        VBox vbox = new VBox(tableScrollPane, buttonBox);
        vbox.setSpacing(10);
        statusTab.tab = new Tab(title, vbox);
        statusTab.table = table;
        return statusTab;
    }

    private StatusTab makeCustomerSettingsTab() {
        logger.debug("Creating Enhanced Customer Settings tab");
        StatusTab statusTab = new StatusTab();
        
        // Main content split pane with three panes
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setDividerPositions(0.25, 0.5);
        
        // Left side - Customer list with refresh button
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));
        
        // Header with title and refresh button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label customerLabel = new Label("Customer List:");
        customerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        Button refreshAllBtn = createStyledButton("🔄 Refresh", "#4CAF50", "white");
        refreshAllBtn.setTooltip(new Tooltip("Refresh all customer data and clear all caches"));
        
        Button syncBtn = createStyledButton("🔄 Sync Addresses", "#2196F3", "white");
        syncBtn.setTooltip(new Tooltip("Sync address book with location dropdowns"));
        syncBtn.setOnAction(e -> {
            logger.info("Manual address sync requested");
            showAddressSyncDialog();
        });
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerBox.getChildren().addAll(customerLabel, spacer, syncBtn, refreshAllBtn);
        
        ListView<String> customerList = new ListView<>(allCustomers);
        customerList.setPrefHeight(300);
        customerList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String customer, boolean empty) {
                super.updateItem(customer, empty);
                if (empty || customer == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(customer);
                    // Count addresses for this customer
                    List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(customer);
                    int addressCount = addresses.size();
                    if (addressCount > 0) {
                        setStyle("-fx-font-weight: bold;");
                        setTooltip(new Tooltip(addressCount + " address(es) in address book"));
                    } else {
                        setStyle("");
                        setTooltip(new Tooltip("No addresses configured"));
                    }
                }
            }
        });
        
        TextField newCustomerField = new TextField();
        newCustomerField.setPromptText("Add new customer...");
        Button addCustomerBtn = createStyledButton("Add", "#2196F3", "white");
        Button deleteCustomerBtn = createStyledButton("Delete Selected", "#F44336", "white");
        
        HBox customerInputBox = new HBox(10, newCustomerField, addCustomerBtn, deleteCustomerBtn);
        customerInputBox.setAlignment(Pos.CENTER_LEFT);
        
        leftPane.getChildren().addAll(headerBox, customerList, customerInputBox);
        
        // Address Book Manager button (moved from rightPane to leftPane)
        Button addressBookManagerBtn = new Button("\uD83D\uDCDA Address Book Manager");
        addressBookManagerBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        addressBookManagerBtn.setOnAction(e -> {
            try {
                addressBookManager.showAddressBookManager((Stage) getScene().getWindow());
            } catch (Exception ex) {
                logger.error("Error opening Address Book Manager", ex);
                showError("Error opening Address Book Manager: " + ex.getMessage());
            }
        });
        leftPane.getChildren().add(addressBookManagerBtn);
        
        // Right side - Unified address book management
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));
        
        Label addressBookLabel = new Label("Customer Address Book:");
        addressBookLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        // Help text explaining unified approach
        Label helpLabel = new Label("📍 All addresses can be used for both pickup and delivery locations");
        helpLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
        
        // Unified address list
        ListView<CustomerAddress> addressList = new ListView<>();
        addressList.setPrefHeight(400);
        addressList.setCellFactory(lv -> new ListCell<CustomerAddress>() {
            @Override
            protected void updateItem(CustomerAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                } else {
                    // Enhanced display with default indicators
                    String displayText = item.getDisplayText();
                    String defaultStatus = item.getDefaultStatusText();
                    
                    if (!defaultStatus.isEmpty()) {
                        displayText += defaultStatus;
                        setStyle("-fx-font-weight: bold; -fx-background-color: #E8F5E8;");
                    } else {
                        setStyle("");
                    }
                    setText(displayText);
                    
                    // Tooltip with full details
                    String tooltipText = String.format("Address: %s\\nCity: %s\\nState: %s\\nDefault Pickup: %s\\nDefault Drop: %s",
                        item.getAddress() != null ? item.getAddress() : "N/A",
                        item.getCity() != null ? item.getCity() : "N/A", 
                        item.getState() != null ? item.getState() : "N/A",
                        item.isDefaultPickup() ? "Yes" : "No",
                        item.isDefaultDrop() ? "Yes" : "No");
                    setTooltip(new Tooltip(tooltipText));
                }
            }
        });
        
        // Simplified button layout - removed duplicate button
        Button addAddressBtn = createStyledButton("➕ Add Address", "#4CAF50", "white");
        
        Button editAddressBtn = createStyledButton("✏️ Edit", "#2196F3", "white");
        editAddressBtn.setDisable(true);
        
        Button deleteAddressBtn = createStyledButton("🗑️ Delete", "#F44336", "white");
        deleteAddressBtn.setDisable(true);
        
        HBox addressButtonsBox = new HBox(10, addAddressBtn, editAddressBtn, deleteAddressBtn);
        addressButtonsBox.setAlignment(Pos.CENTER_LEFT);
        
        rightPane.getChildren().addAll(addressBookLabel, helpLabel, addressList, addressButtonsBox);
        rightPane.setDisable(true); // Initially disabled until customer selected
        
        // Middle pane - Billing List
        VBox middlePane = new VBox(10);
        middlePane.setPadding(new Insets(10));
        
        // Header for billing list
        HBox billingHeaderBox = new HBox(10);
        billingHeaderBox.setAlignment(Pos.CENTER_LEFT);
        Label billingLabel = new Label("Billing List:");
        billingLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        Region billingSpacer = new Region();
        HBox.setHgrow(billingSpacer, Priority.ALWAYS);
        billingHeaderBox.getChildren().addAll(billingLabel, billingSpacer);
        
        // Billing entities list
        ListView<String> billingList = new ListView<>(allBillingEntities);
        billingList.setPrefHeight(300);
        billingList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String billingEntity, boolean empty) {
                super.updateItem(billingEntity, empty);
                if (empty || billingEntity == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(billingEntity);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #2E7D32;");
                    setTooltip(new Tooltip("Billing entity for 'Bill To' field"));
                }
            }
        });
        
        // Billing entity input controls
        TextField newBillingField = new TextField();
        newBillingField.setPromptText("Add new billing entity...");
        Button addBillingBtn = createStyledButton("Add", "#2196F3", "white");
        Button deleteBillingBtn = createStyledButton("Delete Selected", "#F44336", "white");
        
        HBox billingInputBox = new HBox(10, newBillingField, addBillingBtn, deleteBillingBtn);
        billingInputBox.setAlignment(Pos.CENTER_LEFT);
        
        middlePane.getChildren().addAll(billingHeaderBox, billingList, billingInputBox);
        
        // Billing entity management handlers
        addBillingBtn.setOnAction(e -> {
            String name = newBillingField.getText().trim();
            if (name.isEmpty()) {
                logger.warn("Attempted to add empty billing entity name");
                showError("Billing entity name cannot be empty.");
                return;
            }
            boolean isDuplicate = allBillingEntities.stream().anyMatch(b -> b.equalsIgnoreCase(name));
            if (isDuplicate) {
                logger.warn("Attempted to add duplicate billing entity: {}", name);
                showError("Billing entity already exists.");
                return;
            }
            logger.info("Adding new billing entity with real-time sync: {}", name);
            
            try {
                // Add to database
                loadDAO.addBillingEntityIfNotExists(name);
                
                // Update local collections immediately for instant UI update
                Platform.runLater(() -> {
                    allBillingEntities.add(name);
                    
                    // Find and select the new item
                    for (String b : allBillingEntities) {
                        if (b.equalsIgnoreCase(name)) {
                            billingList.getSelectionModel().select(b);
                            billingList.scrollTo(b);
                            break;
                        }
                    }
                    
                    newBillingField.clear();
                    showInfo("Billing entity '" + name + "' added successfully!");
                });
                
                // Refresh cache in background - this will update cache automatically
                CompletableFuture.runAsync(() -> {
                    // The cache manager will refresh on next background refresh cycle
                    logger.debug("Billing entity added, cache will refresh automatically: {}", name);
                });
                
                // Notify load data changed for UI sync
                notifyLoadDataChanged();
                
            } catch (Exception ex) {
                logger.error("Error adding billing entity: {}", ex.getMessage(), ex);
                showError("Failed to add billing entity: " + ex.getMessage());
            }
        });

        deleteBillingBtn.setOnAction(e -> {
            String selected = billingList.getSelectionModel().getSelectedItem();
            if (selected == null || selected.trim().isEmpty()) {
                logger.warn("No billing entity selected for deletion");
                showError("No billing entity selected.");
                return;
            }
            logger.info("Delete billing entity button clicked for: {}", selected);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Delete billing entity \"" + selected + "\"?", 
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Confirm Delete");
            confirm.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.YES) {
                    logger.info("User confirmed deletion of billing entity: {}", selected);
                    loadDAO.deleteBillingEntity(selected);
                    reloadAll();
                }
            });
        });
        
        mainSplitPane.getItems().addAll(leftPane, middlePane, rightPane);
        
        // Robust refresh functionality for unified address book
        Runnable refreshCustomerAddresses = () -> {
            String selectedCustomer = customerList.getSelectionModel().getSelectedItem();
            if (selectedCustomer != null) {
                logger.debug("Refreshing address book for customer: {}", selectedCustomer);
                try {
                    List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(selectedCustomer);
                    addressList.setItems(FXCollections.observableArrayList(addresses));
                    logger.info("Loaded {} addresses for customer: {}", addresses.size(), selectedCustomer);
                } catch (Exception e) {
                    logger.error("Error refreshing customer address book", e);
                    showError("Error refreshing address book: " + e.getMessage());
                }
            }
        };
        
        // Global refresh functionality
        refreshAllBtn.setOnAction(e -> {
            logger.info("Refreshing all customer data and invalidating caches");
            try {
                // Invalidate all caches first to force fresh data load
                cacheManager.invalidateAllCaches().thenRun(() -> {
                    Platform.runLater(() -> {
                        // Reload customers
                        reloadAll();
                        
                        // Refresh customer list display
                        customerList.refresh();
                        
                        // Refresh selected customer's address book
                        refreshCustomerAddresses.run();
                        
                        showInfo("All customer data and caches refreshed successfully!");
                    });
                });
            } catch (Exception ex) {
                logger.error("Error during global refresh", ex);
                showError("Error refreshing data: " + ex.getMessage());
            }
        });
        
        // Customer selection handler with robust error handling
        customerList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                rightPane.setDisable(false);
                refreshCustomerAddresses.run();
            } else {
                rightPane.setDisable(true);
                addressList.setItems(FXCollections.emptyObservableList());
            }
        });
        
        // Address selection listeners for button state management
        addressList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editAddressBtn.setDisable(!hasSelection);
            deleteAddressBtn.setDisable(!hasSelection);
        });
        
        // Customer management handlers with enhanced error handling
        addCustomerBtn.setOnAction(e -> {
            String name = newCustomerField.getText().trim();
            if (name.isEmpty()) {
                logger.warn("Attempted to add empty customer name");
                showError("Customer name cannot be empty.");
                return;
            }
            boolean isDuplicate = allCustomers.stream().anyMatch(c -> c.equalsIgnoreCase(name));
            if (isDuplicate) {
                logger.warn("Attempted to add duplicate customer: {}", name);
                showError("Customer already exists.");
                return;
            }
            logger.info("Adding new customer: {}", name);
            loadDAO.addCustomerIfNotExists(name);
            reloadAll();
            newCustomerField.clear();
            for (String c : allCustomers) {
                if (c.equalsIgnoreCase(name)) {
                    customerList.getSelectionModel().select(c);
                    customerList.scrollTo(c);
                    break;
                }
            }
        });

        deleteCustomerBtn.setOnAction(e -> {
            String selected = customerList.getSelectionModel().getSelectedItem();
            if (selected == null || selected.trim().isEmpty()) {
                logger.warn("No customer selected for deletion");
                showError("No customer selected.");
                return;
            }
            logger.info("Delete customer button clicked for: {}", selected);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Delete customer \"" + selected + "\"?\nThis will also delete all associated locations.", 
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Confirm Delete");
            confirm.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.YES) {
                    logger.info("User confirmed deletion of customer: {}", selected);
                    loadDAO.deleteCustomer(selected);
                    reloadAll();
                }
            });
        });
        
        // Unified Address management handlers
        addAddressBtn.setOnAction(e -> {
            String customer = customerList.getSelectionModel().getSelectedItem();
            if (customer == null) {
                showError("Please select a customer first.");
                return;
            }
            
            CustomerAddressDialog dialog = new CustomerAddressDialog(null);
            dialog.showAndWait().ifPresent(address -> {
                try {
                    // Check for duplicates to prevent duplicate addresses
                    List<CustomerAddress> existingAddresses = loadDAO.getCustomerAddressBook(customer);
                    boolean isDuplicate = existingAddresses.stream().anyMatch(existing -> 
                        Objects.equals(existing.getAddress(), address.getAddress()) &&
                        Objects.equals(existing.getCity(), address.getCity()) &&
                        Objects.equals(existing.getState(), address.getState())
                    );
                    
                    if (isDuplicate) {
                        showError("A similar address already exists for this customer.");
                        return;
                    }
                    
                    // Clear other defaults if this address is set as default
                    if (address.isDefaultPickup() || address.isDefaultDrop()) {
                        for (CustomerAddress existing : existingAddresses) {
                            boolean needsUpdate = false;
                            if (address.isDefaultPickup() && existing.isDefaultPickup()) {
                                existing.setDefaultPickup(false);
                                needsUpdate = true;
                            }
                            if (address.isDefaultDrop() && existing.isDefaultDrop()) {
                                existing.setDefaultDrop(false);
                                needsUpdate = true;
                            }
                            if (needsUpdate) {
                                loadDAO.updateCustomerAddress(existing);
                            }
                        }
                    }
                    
                    int id = loadDAO.addCustomerAddress(customer, 
                        address.getLocationName(), address.getAddress(), 
                        address.getCity(), address.getState());
                    
                    if (id > 0) {
                        // Update default settings
                        address.setId(id);
                        if (address.isDefaultPickup() || address.isDefaultDrop()) {
                            loadDAO.updateCustomerAddress(address);
                        }
                        refreshCustomerAddresses.run();
                        showInfo("Address added successfully!");
                    } else {
                        showError("Failed to add address.");
                    }
                } catch (Exception ex) {
                    logger.error("Error adding address", ex);
                    showError("Error adding address: " + ex.getMessage());
                }
            });
        });
        
        editAddressBtn.setOnAction(e -> {
            CustomerAddress selected = addressList.getSelectionModel().getSelectedItem();
            String customer = customerList.getSelectionModel().getSelectedItem();
            if (selected != null && customer != null) {
                CustomerAddressDialog dialog = new CustomerAddressDialog(selected);
                dialog.showAndWait().ifPresent(address -> {
                    try {
                        // Clear other defaults if this address is set as default
                        if (address.isDefaultPickup() || address.isDefaultDrop()) {
                            List<CustomerAddress> existingAddresses = loadDAO.getCustomerAddressBook(customer);
                            for (CustomerAddress existing : existingAddresses) {
                                if (existing.getId() != address.getId()) {
                                    boolean needsUpdate = false;
                                    if (address.isDefaultPickup() && existing.isDefaultPickup()) {
                                        existing.setDefaultPickup(false);
                                        needsUpdate = true;
                                    }
                                    if (address.isDefaultDrop() && existing.isDefaultDrop()) {
                                        existing.setDefaultDrop(false);
                                        needsUpdate = true;
                                    }
                                    if (needsUpdate) {
                                        loadDAO.updateCustomerAddress(existing);
                                    }
                                }
                            }
                        }
                        
                        loadDAO.updateCustomerAddress(address);
                        refreshCustomerAddresses.run();
                        showInfo("Address updated successfully!");
                    } catch (Exception ex) {
                        logger.error("Error updating address", ex);
                        showError("Error updating address: " + ex.getMessage());
                    }
                });
            }
        });
        
        deleteAddressBtn.setOnAction(e -> {
            CustomerAddress selected = addressList.getSelectionModel().getSelectedItem();
            String customer = customerList.getSelectionModel().getSelectedItem();
            if (selected != null && customer != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                    "Delete address \"" + selected.getDisplayText() + "\"?", 
                    ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        try {
                            loadDAO.deleteCustomerAddress(selected.getId());
                            refreshCustomerAddresses.run();
                            showInfo("Address deleted successfully!");
                        } catch (Exception ex) {
                            logger.error("Error deleting address", ex);
                            showError("Error deleting address: " + ex.getMessage());
                        }
                    }
                });
            }
        });
        
        statusTab.tab = new Tab("Customer Settings", mainSplitPane);
        return statusTab;
    }

    private TableView<Load> makeTableView(ObservableList<Load> list, boolean includeActionColumns) {
        TableView<Load> table = new TableView<>();
        
        // Enhanced table configuration for better UI
        table.setItems(list);
        table.setStyle("-fx-font-size: 12px;");
        
        // Set row factory for enhanced styling
        table.setRowFactory(tv -> {
            TableRow<Load> row = new TableRow<>();
            
            // Set row height
            row.setPrefHeight(35);
            row.setMinHeight(35);
            
            // Add alternating row colors
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #ffffff;");
                    } else {
                        row.setStyle("-fx-background-color: #f9f9f9;");
                    }
                }
            });
            
            // Add hover effect
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    row.setStyle(row.getStyle() + "-fx-background-color: #e8f4f8;");
                }
            });
            
            row.setOnMouseExited(e -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    if (row.getIndex() % 2 == 0) {
                        row.setStyle("-fx-background-color: #ffffff;");
                    } else {
                        row.setStyle("-fx-background-color: #f9f9f9;");
                    }
                }
            });
            
            return row;
        });
        
        // Enable horizontal scrolling with unconstrained resize policy
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        // Set table dimensions to accommodate all columns
        table.setPrefHeight(600);
        table.setMinWidth(1800);  // Increased to fit all columns comfortably
        table.setPrefWidth(2000);
        
        // Add padding to cells
        table.setStyle(table.getStyle() + "-fx-padding: 5px;");

        TableColumn<Load, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getLoadNumber()));
        loadNumCol.setPrefWidth(90);
        loadNumCol.setMinWidth(80);
        loadNumCol.setCellFactory(col -> new TableCell<Load, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                if (!empty && item != null) {
                    Load load = getTableRow().getItem();
                    if (load != null && load.getLocations() != null && load.getLocations().size() > 2) {
                        setStyle("-fx-background-color: #FFFACD; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                } else {
                    setStyle("");
                }
            }
        });

        TableColumn<Load, String> poCol = new TableColumn<>("PO");
        poCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getPONumber()));
        poCol.setPrefWidth(90);
        poCol.setMinWidth(80);

        TableColumn<Load, String> customerCol = new TableColumn<>("Pickup Customer");
        customerCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getCustomer()));
        customerCol.setPrefWidth(150);
        customerCol.setMinWidth(120);

        TableColumn<Load, String> pickUpCol = new TableColumn<>("Pick Up Location");
        pickUpCol.setCellValueFactory(e -> new SimpleStringProperty(extractCityState(e.getValue().getPickUpLocation())));
        pickUpCol.setPrefWidth(180);
        pickUpCol.setMinWidth(150);
        
        TableColumn<Load, String> customer2Col = new TableColumn<>("Drop Customer");
        customer2Col.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getCustomer2() != null ? e.getValue().getCustomer2() : ""));
        customer2Col.setPrefWidth(150);
        customer2Col.setMinWidth(120);

        TableColumn<Load, String> billToCol = new TableColumn<>("Bill To");
        billToCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getBillTo() != null ? e.getValue().getBillTo() : ""));
        billToCol.setPrefWidth(150);
        billToCol.setMinWidth(120);

        TableColumn<Load, String> dropCol = new TableColumn<>("Drop Location");
        dropCol.setCellValueFactory(e -> new SimpleStringProperty(extractCityState(e.getValue().getDropLocation())));
        dropCol.setPrefWidth(180);
        dropCol.setMinWidth(150);

        TableColumn<Load, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDriver() != null ? e.getValue().getDriver().getName() : ""
        ));
        driverCol.setPrefWidth(120);
        driverCol.setMinWidth(100);

        TableColumn<Load, String> truckUnitCol = new TableColumn<>("Truck/Unit");
        truckUnitCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTruckUnitSnapshot()));
        truckUnitCol.setPrefWidth(90);
        truckUnitCol.setMinWidth(80);
        
        TableColumn<Load, String> trailerCol = new TableColumn<>("Trailer");
        trailerCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTrailerNumber()));
        trailerCol.setPrefWidth(90);
        trailerCol.setMinWidth(80);

        TableColumn<Load, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getStatus().toString()));
        statusCol.setCellFactory(col -> new TableCell<Load, String>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(s);
                if (!empty && s != null) {
                    setStyle("-fx-background-color: " + getStatusColor(s) + "; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });
        statusCol.setPrefWidth(100);
        statusCol.setMinWidth(80);

        TableColumn<Load, Number> grossCol = new TableColumn<>("Gross Amount");
        grossCol.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getGrossAmount()));
        grossCol.setCellFactory(col -> new TableCell<Load, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", item.doubleValue()));
                }
            }
        });
        grossCol.setPrefWidth(120);
        grossCol.setMinWidth(100);
        grossCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Documents column (moved here to be next to Gross Amount)
        TableColumn<Load, Void> docsCol = null;
        if (includeActionColumns) {
            docsCol = new TableColumn<>("Documents");
            docsCol.setPrefWidth(100);
            docsCol.setCellFactory(col -> new TableCell<Load, Void>() {
                private final Button uploadBtn = createInlineButton("Upload Docs", "#007bff", "white");
                
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        Load load = getTableRow().getItem();
                        if (load != null) {
                            uploadBtn.setOnAction(e -> showDocumentDialog(load));
                            setGraphic(uploadBtn);
                        }
                    }
                }
            });
        }

        // Lumper column (moved here to be next to Documents)
        TableColumn<Load, Void> lumperCol = null;
        if (includeActionColumns) {
            lumperCol = new TableColumn<>("Lumper");
            lumperCol.setPrefWidth(150);
            lumperCol.setCellFactory(col -> new TableCell<Load, Void>() {
                private final Button addLumperBtn = createInlineButton("Add", "#28a745", "white");
                private final Button editLumperBtn = createInlineButton("Edit", "#ffc107", "black");
                private final Button removeLumperBtn = createInlineButton("Remove", "#dc3545", "white");
                private final HBox buttonBox = new HBox(3, addLumperBtn, editLumperBtn, removeLumperBtn);
                
                {
                    buttonBox.setAlignment(Pos.CENTER);
                }
                
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        Load load = getTableRow().getItem();
                        if (load != null) {
                            addLumperBtn.setDisable(load.isHasLumper());
                            editLumperBtn.setDisable(!load.isHasLumper());
                            removeLumperBtn.setDisable(!load.isHasLumper());
                            
                            addLumperBtn.setOnAction(e -> handleLumperAction(load, "add"));
                            editLumperBtn.setOnAction(e -> handleLumperAction(load, "edit"));
                            removeLumperBtn.setOnAction(e -> handleLumperAction(load, "remove"));
                            
                            setGraphic(buttonBox);
                        }
                    }
                }
            });
        }

        TableColumn<Load, String> reminderCol = new TableColumn<>("Reminder");
        reminderCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getReminder()));
        reminderCol.setPrefWidth(220);
        reminderCol.setMinWidth(180);
        reminderCol.setCellFactory(col -> new TableCell<Load, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    Load load = getTableRow().getItem();
                    if (load != null && load.isHasLumper()) {
                        if (load.isHasRevisedRateConfirmation()) {
                            setStyle("-fx-background-color: #b7f9b7; -fx-font-weight: bold;"); // Green
                        } else {
                            setStyle("-fx-background-color: #ffcccc; -fx-font-weight: bold;"); // Red
                        }
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        // Width already set above

        TableColumn<Load, String> pickupDateCol = new TableColumn<>("Pick Up Date");
        pickupDateCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getPickUpDate() != null ? e.getValue().getPickUpDate().toString() : ""
        ));
        pickupDateCol.setPrefWidth(110);
        pickupDateCol.setMinWidth(100);
        pickupDateCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Load, String> pickupTimeCol = new TableColumn<>("Pick Up Time");
        pickupTimeCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getPickUpTime() != null ? e.getValue().getPickUpTime().format(DateTimeFormatter.ofPattern("h:mm a")) : ""
        ));
        pickupTimeCol.setPrefWidth(100);
        pickupTimeCol.setMinWidth(90);
        pickupTimeCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Load, String> deliveryDateCol = new TableColumn<>("Delivery Date");
        deliveryDateCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDeliveryDate() != null ? e.getValue().getDeliveryDate().toString() : ""
        ));
        deliveryDateCol.setPrefWidth(110);
        deliveryDateCol.setMinWidth(100);
        deliveryDateCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Load, String> deliveryTimeCol = new TableColumn<>("Delivery Time");
        deliveryTimeCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDeliveryTime() != null ? e.getValue().getDeliveryTime().format(DateTimeFormatter.ofPattern("h:mm a")) : ""
        ));
        deliveryTimeCol.setPrefWidth(100);
        deliveryTimeCol.setMinWidth(90);
        deliveryTimeCol.setStyle("-fx-alignment: CENTER;");

        // Create list of columns
        List<TableColumn<Load, ?>> columns = new ArrayList<>();
        columns.addAll(Arrays.asList(loadNumCol, poCol, billToCol, customerCol, pickUpCol, customer2Col, dropCol,
                driverCol, truckUnitCol, trailerCol, statusCol, grossCol));
        
        // Add documents and lumper columns after gross amount if includeActionColumns is true
        if (includeActionColumns && docsCol != null) {
            columns.add(docsCol);
        }
        if (includeActionColumns && lumperCol != null) {
            columns.add(lumperCol);
        }
        
        // Add remaining columns
        columns.addAll(Arrays.asList(reminderCol, pickupDateCol, pickupTimeCol, deliveryDateCol, deliveryTimeCol));
        
        table.getColumns().addAll(columns);

        return table;
    }

    private void handleLumperAction(Load load, String action) {
        logger.info("Lumper {} action for load: {}", action, load.getLoadNumber());
        
        if ("add".equals(action) || "edit".equals(action)) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle(action.equals("add") ? "Add Lumper" : "Edit Lumper");
            dialog.setHeaderText("Enter lumper information for load " + load.getLoadNumber());
            
            TextArea lumperInfo = new TextArea();
            lumperInfo.setPromptText("Enter lumper details...");
            lumperInfo.setPrefRowCount(3);
            if ("edit".equals(action) && load.getReminder() != null) {
                lumperInfo.setText(load.getReminder());
            }
            
            CheckBox hasRevisedConfirmation = new CheckBox("Revised Rate Confirmation Available");
            hasRevisedConfirmation.setSelected(load.isHasRevisedRateConfirmation());
            
            VBox content = new VBox(10);
            content.getChildren().addAll(
                new Label("Lumper Information:"),
                lumperInfo,
                hasRevisedConfirmation,
                new Label("Note: If revised rate confirmation is not available,\nreminder will show: EMAIL BROKER / CHECK EMAIL for Revised Rate Confirmation")
            );
            
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return lumperInfo.getText();
                }
                return null;
            });
            
            dialog.showAndWait().ifPresent(lumperText -> {
                load.setHasLumper(true);
                load.setHasRevisedRateConfirmation(hasRevisedConfirmation.isSelected());
                if (!hasRevisedConfirmation.isSelected()) {
                    load.setReminder("EMAIL BROKER / CHECK EMAIL for Revised Rate Confirmation - " + lumperText);
                } else {
                    load.setReminder("Lumper: " + lumperText);
                }
                loadDAO.update(load);
                reloadAll();
                notifyLoadDataChanged(); // Notify listeners
            });
        } else if ("remove".equals(action)) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove lumper from load " + load.getLoadNumber() + "?",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    load.setHasLumper(false);
                    load.setHasRevisedRateConfirmation(false);
                    load.setReminder("");
                    loadDAO.update(load);
                    reloadAll();
                    notifyLoadDataChanged(); // Notify listeners
                }
            });
        }
    }

    private void showDocumentDialog(Load load) {
        logger.info("Showing document dialog for load: {}", load.getLoadNumber());
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Load Document Manager - " + load.getLoadNumber() + " - " + load.getCustomer());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Create main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(800);
        content.setPrefHeight(600);
        
        // Header with load info
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Documents for Load " + load.getLoadNumber());
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label customerLabel = new Label("Customer: " + load.getCustomer());
        customerLabel.setStyle("-fx-text-fill: #666;");
        
        headerBox.getChildren().addAll(titleLabel, new Separator(Orientation.VERTICAL), customerLabel);
        
        // Document list section
        VBox documentSection = createDocumentListSection(load);
        
        // Upload section with document type
        ComboBox<Load.LoadDocument.DocumentType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Load.LoadDocument.DocumentType.values());
        typeCombo.setValue(Load.LoadDocument.DocumentType.RATE_CONFIRMATION);
        
        HBox typeBox = new HBox(10, new Label("Document Type:"), typeCombo);
        typeBox.setAlignment(Pos.CENTER_LEFT);
        
        // Action buttons section
        HBox actionButtons = createDocumentActionButtons(load, documentSection, typeCombo);
        
        // Settings button
        Button settingsButton = createStyledButton("⚙️ Settings", "#6c757d", "white");
        settingsButton.setOnAction(e -> {
            DocumentManagerSettingsDialog settingsDialog = 
                new DocumentManagerSettingsDialog((Stage) dialog.getOwner());
            settingsDialog.showAndWait();
            // Refresh storage path after settings change
            DOCUMENT_STORAGE_PATH = DocumentManagerConfig.getLoadsStoragePath();
        });
        
        // Merge & Print button
        Button mergeBtn = createStyledButton("🖼️ Merge & Print", "#6f42c1", "white");
        mergeBtn.setOnAction(e -> {
            ObservableList<Load.LoadDocument> documents = FXCollections.observableArrayList(load.getDocuments());
            handleMergeDocuments(load, documents);
        });
        
        HBox topButtons = new HBox(10, mergeBtn, settingsButton);
        topButtons.setAlignment(Pos.CENTER_RIGHT);
        
        content.getChildren().addAll(headerBox, typeBox, documentSection, actionButtons, topButtons);
        VBox.setVgrow(documentSection, Priority.ALWAYS);
        
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
    
    /**
     * Create document list section
     */
    private VBox createDocumentListSection(Load load) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("📄 Documents");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");
        
        ListView<Load.LoadDocument> docList = new ListView<>();
        docList.setPrefHeight(300);
        docList.setCellFactory(lv -> new ListCell<Load.LoadDocument>() {
            @Override
            protected void updateItem(Load.LoadDocument doc, boolean empty) {
                super.updateItem(doc, empty);
                if (empty || doc == null) {
                    setText(null);
                } else {
                    setText(doc.getFileName() + " (" + doc.getType() + ") - " + doc.getUploadDate());
                }
            }
        });
        
        ObservableList<Load.LoadDocument> documents = FXCollections.observableArrayList(load.getDocuments());
        docList.setItems(documents);
        
        // Folder info
        String driverName = load.getDriver() != null ? 
            load.getDriver().getName().replace(" ", "_") : "Unassigned";
        int weekNumber = DocumentManagerConfig.getCurrentWeekNumber();
        Path folderPath = DocumentManagerConfig.getLoadsFolderPath(driverName, weekNumber);
        
        Label folderInfo = new Label("Storage: " + folderPath.toString() + " (Week " + weekNumber + ")");
        folderInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        
        section.getChildren().addAll(sectionTitle, docList, folderInfo);
        
        return section;
    }
    
    /**
     * Create document action buttons
     */
    private HBox createDocumentActionButtons(Load load, VBox documentSection, ComboBox<Load.LoadDocument.DocumentType> typeCombo) {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button uploadBtn = createStyledButton("📤 Upload", "#007bff", "white");
        Button viewBtn = createStyledButton("👁️ View", "#17a2b8", "white");
        Button printBtn = createStyledButton("🖨️ Print", "#6c757d", "white");
        Button deleteBtn = createStyledButton("🗑️ Delete", "#dc3545", "white");
        Button openFolderBtn = createStyledButton("📁 Open Folder", "#28a745", "white");
        
        // Get document list view from the section
        ListView<Load.LoadDocument> docList = (ListView<Load.LoadDocument>) documentSection.getChildren().get(1);
        ObservableList<Load.LoadDocument> documents = docList.getItems();
        
        uploadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Upload Document for Load " + load.getLoadNumber());
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Document Files", "*.doc", "*.docx", "*.txt")
            );
            
            List<File> files = fileChooser.showOpenMultipleDialog(buttonBox.getScene().getWindow());
            if (files != null && !files.isEmpty()) {
                for (File file : files) {
                    try {
                        // Create organized path structure
                        String driverName = load.getDriver() != null ? 
                            load.getDriver().getName().replace(" ", "_") : "Unassigned";
                        int weekNumber = DocumentManagerConfig.getCurrentWeekNumber();
                        Path documentPath = DocumentManagerConfig.createLoadsDocumentPath(
                            driverName,
                            weekNumber,
                            load.getLoadNumber(),
                            typeCombo.getValue().toString(),
                            file.getName()
                        );
                        
                        // Copy file to organized location
                        Files.copy(file.toPath(), documentPath, StandardCopyOption.REPLACE_EXISTING);
                        
                        // Save document record
                        Load.LoadDocument doc = new Load.LoadDocument(
                            0,
                            load.getId(),
                            file.getName(),
                            documentPath.toString(),
                            typeCombo.getValue(),
                            LocalDate.now()
                        );
                        
                        int docId = loadDAO.addDocument(doc);
                        doc.setId(docId);
                        documents.add(doc);
                        load.getDocuments().add(doc);
                        
                        logger.info("Uploaded document to: {}", documentPath);
                    } catch (IOException ex) {
                        logger.error("Failed to upload document: {}", file.getName(), ex);
                        showError("Failed to upload " + file.getName() + ": " + ex.getMessage());
                    }
                }
            }
        });
        
        viewBtn.setOnAction(e -> {
            Load.LoadDocument selectedDoc = docList.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                try {
                    File file = new File(selectedDoc.getFilePath());
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file);
                    } else {
                        showError("Cannot open file. File is saved at: " + selectedDoc.getFilePath());
                    }
                } catch (Exception ex) {
                    logger.error("Failed to open document", ex);
                    showError("Failed to open document: " + ex.getMessage());
                }
            } else {
                showError("Please select a document to view");
            }
        });
        
        printBtn.setOnAction(e -> {
            Load.LoadDocument selectedDoc = docList.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                try {
                    File file = new File(selectedDoc.getFilePath());
                    if (Desktop.isDesktopSupported() && 
                        Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
                        Desktop.getDesktop().print(file);
                        logger.info("Printing document: {}", selectedDoc.getFilePath());
                    } else {
                        showError("Printing is not supported. Please open the file first.");
                    }
                } catch (Exception ex) {
                    logger.error("Failed to print document", ex);
                    showError("Failed to print document: " + ex.getMessage());
                }
            } else {
                showError("Please select a document to print");
            }
        });
        
        deleteBtn.setOnAction(e -> {
            Load.LoadDocument selectedDoc = docList.getSelectionModel().getSelectedItem();
            if (selectedDoc != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to delete this document?",
                    ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Deletion");
                
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        loadDAO.deleteDocument(selectedDoc.getId());
                        documents.remove(selectedDoc);
                        load.getDocuments().remove(selectedDoc);
                        
                        // Delete physical file
                        try {
                            Files.deleteIfExists(Paths.get(selectedDoc.getFilePath()));
                            logger.info("Deleted document: {}", selectedDoc.getFilePath());
                        } catch (IOException ex) {
                            logger.error("Failed to delete file: {}", selectedDoc.getFilePath(), ex);
                        }
                    }
                });
            } else {
                showError("Please select a document to delete");
            }
        });
        
        openFolderBtn.setOnAction(e -> {
            String driverName = load.getDriver() != null ? 
                load.getDriver().getName().replace(" ", "_") : "Unassigned";
            int weekNumber = DocumentManagerConfig.getCurrentWeekNumber();
            Path folderPath = DocumentManagerConfig.getLoadsFolderPath(driverName, weekNumber);
            DocumentManagerConfig.openFolder(folderPath);
        });
        
        buttonBox.getChildren().addAll(uploadBtn, viewBtn, printBtn, deleteBtn, openFolderBtn);
        
        return buttonBox;
    }

    private void handleMergeDocuments(Load load, List<Load.LoadDocument> documents) {
		if (documents.isEmpty()) {
			showError("No documents to merge.");
			return;
		}
		
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Merge Documents");
		dialog.setHeaderText("Select documents to merge and output filename");
		
		VBox content = new VBox(10);
		content.setPadding(new Insets(10));
		
		// Document selection with checkboxes
		VBox docSelectionBox = new VBox(5);
		List<CheckBox> checkBoxes = new ArrayList<>();
		for (Load.LoadDocument doc : documents) {
			CheckBox cb = new CheckBox(doc.getFileName() + " (" + doc.getType() + ")");
			cb.setUserData(doc);
			checkBoxes.add(cb);
			docSelectionBox.getChildren().add(cb);
		}
		ScrollPane scrollPane = new ScrollPane(docSelectionBox);
		scrollPane.setPrefHeight(150);
		
		// Output filename
		ComboBox<String> nameTypeCombo = new ComboBox<>();
		nameTypeCombo.getItems().addAll("Load Number", "PO Number", "Custom");
		nameTypeCombo.setValue("Load Number");
		
		TextField customNameField = new TextField();
		customNameField.setPromptText("Custom filename");
		customNameField.setDisable(true);
		
		nameTypeCombo.setOnAction(e -> {
			customNameField.setDisable(!"Custom".equals(nameTypeCombo.getValue()));
		});
		
		// Cover page option
		CheckBox includeCoverPageCheckBox = new CheckBox("Include cover page (recommended)");
		includeCoverPageCheckBox.setSelected(true); // Default to true
		
		content.getChildren().addAll(
			new Label("Select documents to merge:"),
			scrollPane,
			new Separator(),
			new Label("Output filename:"),
			new HBox(10, nameTypeCombo, customNameField),
			new Separator(),
			includeCoverPageCheckBox
		);
		
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				List<Load.LoadDocument> selectedDocs = new ArrayList<>();
				for (CheckBox cb : checkBoxes) {
					if (cb.isSelected()) {
						selectedDocs.add((Load.LoadDocument) cb.getUserData());
					}
				}
				
				if (selectedDocs.isEmpty()) {
					showError("Please select at least one document.");
					return null;
				}
				
				String fileName;
				switch (nameTypeCombo.getValue()) {
					case "Load Number":
						fileName = load.getLoadNumber();
						break;
					case "PO Number":
						fileName = load.getPONumber();
						break;
					default:
						fileName = customNameField.getText().trim();
						if (fileName.isEmpty()) {
							showError("Please enter a custom filename.");
							return null;
						}
				}
				
				try {
					// Create output file in merged documents folder
					int weekNumber = DocumentManagerConfig.getCurrentWeekNumber();
					Path mergedPath = DocumentManagerConfig.createMergedLoadsPath(weekNumber, fileName + ".pdf");
					File outputFile = mergedPath.toFile();
					boolean includeCoverPage = includeCoverPageCheckBox.isSelected();
					mergePDFs(load, selectedDocs, outputFile, includeCoverPage);
					
					// Ask if user wants to print
					Alert printAlert = new Alert(Alert.AlertType.CONFIRMATION,
							"PDF merged successfully to:\n" + outputFile.getAbsolutePath() + "\n\nDo you want to print it now?",
							ButtonType.YES, ButtonType.NO);
					printAlert.setHeaderText("Merge Successful");
					printAlert.showAndWait().ifPresent(response -> {
						if (response == ButtonType.YES) {
							printPDF(outputFile);
						}
					});
					
					return fileName;
				} catch (Exception ex) {
					logger.error("Failed to merge documents", ex);
					showError("Failed to merge documents: " + ex.getMessage());
					return null;
				}
			}
			return null;
		});
		
		dialog.showAndWait();
	}

    private void mergePDFs(Load load, List<Load.LoadDocument> documents, File outputFile, boolean includeCoverPage) throws IOException {
		PDFMergerUtility merger = new PDFMergerUtility();
		File tempCover = null;
		
		// Add cover page first if requested
		if (includeCoverPage) {
			PDDocument coverPage = createCoverPage(load);
			
			// Save cover page to temp file
			tempCover = File.createTempFile("cover", ".pdf");
			coverPage.save(tempCover);
			coverPage.close();
			
			// Add cover page first
			merger.addSource(tempCover);
		}
		
		// Add all selected documents
		for (Load.LoadDocument doc : documents) {
			File docFile = new File(doc.getFilePath());
			if (docFile.exists()) {
				if (doc.getFilePath().toLowerCase().endsWith(".pdf")) {
					merger.addSource(docFile);
				} else {
					// Convert image to PDF
					File tempPdf = convertImageToPDF(docFile);
					merger.addSource(tempPdf);
				}
			}
		}
		
		merger.setDestinationFileName(outputFile.getAbsolutePath());
		// Remove the MemoryUsageSetting parameter:
		merger.mergeDocuments(null);
		
		// Clean up temp files
		if (tempCover != null) {
			tempCover.delete();
		}
		
		logger.info("Documents merged successfully to: {} (cover page: {})", outputFile.getAbsolutePath(), includeCoverPage ? "included" : "excluded");
	}

    private PDDocument createCoverPage(Load load) throws IOException {
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.LETTER);
		document.addPage(page);
		
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
			contentStream.beginText();
			// Correct way to use Standard14Fonts in PDFBox 3.0.2:
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
			contentStream.setLeading(30f);
			contentStream.newLineAtOffset(100, 700);
			
			contentStream.showText("LOAD DOCUMENTS");
			contentStream.newLine();
			contentStream.newLine();
			
			// Correct way to use Standard14Fonts in PDFBox 3.0.2:
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
			contentStream.showText("Driver: " + (load.getDriver() != null ? load.getDriver().getName() : "N/A"));
			contentStream.newLine();
			
			contentStream.showText("Truck/Unit: " + load.getTruckUnitSnapshot());
			contentStream.newLine();
			
			// Add trailer information to cover page
			String trailerInfo = load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty() ? 
				load.getTrailerNumber() : 
				(load.getDriver() != null ? load.getDriver().getTrailerNumber() : "N/A");
				
			contentStream.showText("Trailer #: " + trailerInfo);
			contentStream.newLine();
			
			// Format dates with times
			String pickupDateTime = "N/A";
			if (load.getPickUpDate() != null) {
				pickupDateTime = load.getPickUpDate().toString();
				if (load.getPickUpTime() != null) {
					pickupDateTime += " " + load.getPickUpTime().format(DateTimeFormatter.ofPattern("h:mm a"));
				}
			}
			contentStream.showText("Pick Up Date/Time: " + pickupDateTime);
			contentStream.newLine();
			
			String deliveryDateTime = "N/A";
			if (load.getDeliveryDate() != null) {
				deliveryDateTime = load.getDeliveryDate().toString();
				if (load.getDeliveryTime() != null) {
					deliveryDateTime += " " + load.getDeliveryTime().format(DateTimeFormatter.ofPattern("h:mm a"));
				}
			}
			contentStream.showText("Delivery Date/Time: " + deliveryDateTime);
			contentStream.newLine();
			
			contentStream.showText("PO: " + load.getPONumber());
			contentStream.newLine();
			
			contentStream.endText();
		}
		
		return document;
	}

    private File convertImageToPDF(File imageFile) throws IOException {
        PDDocument document = new PDDocument();
        
        BufferedImage image = ImageIO.read(imageFile);
        float width = image.getWidth();
        float height = image.getHeight();
        
        PDPage page = new PDPage(new PDRectangle(width, height));
        document.addPage(page);
        
        PDImageXObject pdImage = PDImageXObject.createFromFileByContent(imageFile, document);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(pdImage, 0, 0);
        }
        
        File pdfFile = File.createTempFile("image", ".pdf");
        document.save(pdfFile);
        document.close();
        
        return pdfFile;
    }

    private void printPDF(File pdfFile) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().print(pdfFile);
            } else {
                showError("Printing is not supported on this system.");
            }
        } catch (IOException e) {
            logger.error("Failed to print PDF", e);
            showError("Failed to print PDF: " + e.getMessage());
        }
    }

    // CheckListView implementation (simplified version)
    private static class CheckListView<T> extends ListView<T> {
        private final MultipleSelectionModel<T> checkModel;
        
        public CheckListView() {
            this.checkModel = new MultipleSelectionModel<T>() {
                private final ObservableList<Integer> selectedIndices = FXCollections.observableArrayList();
                private final ObservableList<T> selectedItems = FXCollections.observableArrayList();
                
                @Override
                public ObservableList<Integer> getSelectedIndices() { return selectedIndices; }
                @Override
                public ObservableList<T> getSelectedItems() { return selectedItems; }
                @Override
                public void selectIndices(int index, int... indices) {
                    select(index);
                    for (int i : indices) select(i);
                }
                @Override
                public void selectAll() {
                    for (int i = 0; i < getItems().size(); i++) select(i);
                }
                @Override
                public void selectFirst() { if (!getItems().isEmpty()) select(0); }
                @Override
                public void selectLast() { if (!getItems().isEmpty()) select(getItems().size() - 1); }
                @Override
                public void clearAndSelect(int index) { clearSelection(); select(index); }
                @Override
                public void select(int index) {
                    if (index >= 0 && index < getItems().size()) {
                        if (!selectedIndices.contains(index)) {
                            selectedIndices.add(index);
                            selectedItems.add(getItems().get(index));
                        }
                    }
                }
                @Override
                public void select(T obj) {
                    int index = getItems().indexOf(obj);
                    if (index >= 0) select(index);
                }
                @Override
                public void clearSelection(int index) {
                    selectedIndices.remove(Integer.valueOf(index));
                    selectedItems.remove(getItems().get(index));
                }
                @Override
                public void clearSelection() {
                    selectedIndices.clear();
                    selectedItems.clear();
                }
                @Override
                public boolean isSelected(int index) {
                    return selectedIndices.contains(index);
                }
                @Override
                public boolean isEmpty() { return selectedIndices.isEmpty(); }
                @Override
                public void selectPrevious() {}
                @Override
                public void selectNext() {}
            };
            
            setCellFactory(lv -> new CheckBoxListCell<>(checkModel::isSelected, (index, selected) -> {
                if (selected) {
                    checkModel.select(index);
                } else {
                    checkModel.clearSelection(index);
                }
            }));
        }
        
        public MultipleSelectionModel<T> getCheckModel() { return checkModel; }
    }

    private static class CheckBoxListCell<T> extends ListCell<T> {
        private final CheckBox checkBox = new CheckBox();
        private final java.util.function.Function<Integer, Boolean> getSelectedProperty;
        private final java.util.function.BiConsumer<Integer, Boolean> onSelectedChanged;
        
        public CheckBoxListCell(java.util.function.Function<Integer, Boolean> getSelectedProperty,
                                java.util.function.BiConsumer<Integer, Boolean> onSelectedChanged) {
            this.getSelectedProperty = getSelectedProperty;
            this.onSelectedChanged = onSelectedChanged;
        }
        
        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                checkBox.setSelected(getSelectedProperty.apply(getIndex()));
                checkBox.setOnAction(e -> onSelectedChanged.accept(getIndex(), checkBox.isSelected()));
                setGraphic(checkBox);
                setText(item.toString());
            }
        }
    }

    private String getStatusColor(String s) {
        switch (s) {
            case "BOOKED":        return "#b6d4fe";  // Light blue
            case "ASSIGNED":      return "#b6d4fe";  // Light blue
            case "IN_TRANSIT":    return "#ffe59b";  // Light yellow
            case "DELIVERED":     return "#b7f9b7";  // Light green
            case "PAID":          return "#c2c2d6";  // Light purple
            case "CANCELLED":     return "#ffc8c8";  // Light red
            case "PICKUP_LATE":   return "#ff9999";  // Medium red
            case "DELIVERY_LATE": return "#ff6666";  // Darker red
            default:              return "#f7f7f7";  // Light gray
        }
    }

    // Helper method to create time spinner
    private Spinner<LocalTime> createTimeSpinner() {
        SpinnerValueFactory<LocalTime> factory = new SpinnerValueFactory<LocalTime>() {
            {
                setValue(null);
            }
            
            @Override
            public void decrement(int steps) {
                LocalTime current = getValue();
                if (current == null) {
                    setValue(LocalTime.of(0, 0));
                } else {
                    setValue(current.minusMinutes(15L * steps));
                }
            }
            
            @Override
            public void increment(int steps) {
                LocalTime current = getValue();
                if (current == null) {
                    setValue(LocalTime.of(0, 0));
                } else {
                    setValue(current.plusMinutes(15L * steps));
                }
            }
        };
        
        Spinner<LocalTime> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        
        // Custom converter for time display
        spinner.getValueFactory().setConverter(new StringConverter<LocalTime>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
            
            @Override
            public String toString(LocalTime time) {
                return time != null ? time.format(formatter) : "";
            }
            
            @Override
            public LocalTime fromString(String string) {
                if (string == null || string.trim().isEmpty()) {
                    return null;
                }
                try {
                    return LocalTime.parse(string.trim().toUpperCase(), formatter);
                } catch (Exception e) {
                    // Try parsing with just hour and AM/PM
                    try {
                        String trimmed = string.trim().toUpperCase();
                        if (trimmed.matches("\\d{1,2}\\s*(AM|PM)")) {
                            String[] parts = trimmed.split("\\s*(?=AM|PM)");
                            int hour = Integer.parseInt(parts[0]);
                            boolean isPM = parts[1].equals("PM");
                            if (isPM && hour != 12) hour += 12;
                            else if (!isPM && hour == 12) hour = 0;
                            return LocalTime.of(hour, 0);
                        }
                        return null;
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
        });
        
        return spinner;
    }
    
    // Helper method to create 24-hour time spinner
    private Spinner<LocalTime> createTimeSpinner24Hour() {
        SpinnerValueFactory<LocalTime> factory = new SpinnerValueFactory<LocalTime>() {
            {
                setValue(null);
            }
            
            @Override
            public void decrement(int steps) {
                LocalTime current = getValue();
                if (current == null) {
                    setValue(LocalTime.of(0, 0));
                } else {
                    setValue(current.minusMinutes(15L * steps));
                }
            }
            
            @Override
            public void increment(int steps) {
                LocalTime current = getValue();
                if (current == null) {
                    setValue(LocalTime.of(0, 0));
                } else {
                    setValue(current.plusMinutes(15L * steps));
                }
            }
        };
        
        Spinner<LocalTime> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        
        // Custom string converter for 24-hour format
        StringConverter<LocalTime> converter = new StringConverter<LocalTime>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            
            @Override
            public String toString(LocalTime time) {
                return time != null ? time.format(formatter) : "";
            }
            
            @Override
            public LocalTime fromString(String string) {
                if (string == null || string.trim().isEmpty()) {
                    return null;
                }
                try {
                    return LocalTime.parse(string, formatter);
                } catch (Exception e) {
                    return null;
                }
            }
        };
        
        spinner.getEditor().setTextFormatter(new TextFormatter<>(converter, null, change -> {
            String newText = change.getControlNewText();
            if (newText.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]") || newText.matches("([01]?[0-9]|2[0-3]):?") || newText.isEmpty()) {
                return change;
            }
            return null;
        }));
        
        spinner.getValueFactory().setConverter(converter);
        return spinner;
    }
    
    // Helper method to format LocalTime to 12-hour format
    private String format12HourTime(LocalTime time) {
        if (time == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
        return time.format(formatter);
    }
    
    // Dialog to add new customer
    private void showAddCustomerDialog(ComboBox<String> targetComboBox) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Customer");
        dialog.setHeaderText("Enter new customer name");
        dialog.setContentText("Customer name:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(customerName -> {
            if (!customerName.trim().isEmpty()) {
                // Add to database
                try {
                    loadDAO.addCustomerIfNotExists(customerName.trim());
                    // Add to combo box and select it
                    Platform.runLater(() -> {
                        if (!targetComboBox.getItems().contains(customerName.trim())) {
                            targetComboBox.getItems().add(customerName.trim());
                        }
                        targetComboBox.setValue(customerName.trim());
                    });
                    showInfo("Customer added successfully!");
                } catch (Exception e) {
                    logger.error("Error adding customer", e);
                    showError("Failed to add customer: " + e.getMessage());
                }
            }
        });
    }
    
    // Helper method to load customers into a ComboBox with filtering
    // Enhanced billing integration with Customer tab cache
    private void loadBillingEntitiesIntoComboBox(ComboBox<String> comboBox) {
        try {
            logger.debug("Loading billing entities from cache for enhanced integration");
            
            // Get cached billing entities for instant responsiveness
            CompletableFuture.supplyAsync(() -> cacheManager.getCachedBillingEntities())
                .thenAccept(cachedBillingEntities -> {
                    Platform.runLater(() -> {
                        try {
                            // Use cached data for instant loading
                            ObservableList<String> billingList = FXCollections.observableArrayList(cachedBillingEntities);
                            
                            // Create filtered list for enterprise-grade search functionality
                            FilteredList<String> filteredBilling = new FilteredList<>(billingList, p -> true);
                            
                            // Enhanced search with debouncing for better performance
                            if (comboBox.getEditor() != null) {
                                comboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
                                    final TextField editor = comboBox.getEditor();
                                    final String selected = comboBox.getSelectionModel().getSelectedItem();
                                    
                                    // Debounced filtering with 150ms delay
                                    Platform.runLater(() -> {
                                        if (selected == null || !selected.equals(editor.getText())) {
                                            filteredBilling.setPredicate(entity -> {
                                                if (newValue == null || newValue.isEmpty()) {
                                                    return true;
                                                }
                                                String lowerCaseFilter = newValue.toLowerCase();
                                                return entity.toLowerCase().contains(lowerCaseFilter);
                                            });
                                            
                                            // Real-time sync with customer settings
                                            comboBox.setItems(filteredBilling);
                                            
                                            // Smart dropdown behavior
                                            if (!comboBox.isShowing() && !newValue.isEmpty()) {
                                                comboBox.show();
                                            }
                                        }
                                    });
                                });
                            }
                            
                            // Set initial items from cache
                            comboBox.setItems(filteredBilling);
                            
                            logger.debug("Loaded {} billing entities from cache", cachedBillingEntities.size());
                            
                        } catch (Exception e) {
                            logger.error("Error setting up billing entities UI", e);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("Error loading cached billing entities", throwable);
                    Platform.runLater(() -> {
                        // Fallback to direct database query
                        loadCustomersIntoComboBox(comboBox);
                    });
                    return null;
                });
            
        } catch (Exception e) {
            logger.error("Error loading billing entities from cache", e);
            // Fallback to legacy method
            loadCustomersIntoComboBox(comboBox);
        }
    }
    
    // Enhanced customer loading with cache integration
    private void loadCustomersIntoComboBox(ComboBox<String> comboBox) {
        try {
            // Use cached customers for instant loading
            CompletableFuture.supplyAsync(() -> cacheManager.getCachedCustomers())
                .thenAccept(cachedCustomers -> {
                    Platform.runLater(() -> {
                        try {
                            ObservableList<String> customerList = FXCollections.observableArrayList(cachedCustomers);
                            
                            // Create filtered list for search functionality
                            FilteredList<String> filteredCustomers = new FilteredList<>(customerList, p -> true);
                            
                            // Add listener to filter items based on text input
                            if (comboBox.getEditor() != null) {
                                comboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
                                    final TextField editor = comboBox.getEditor();
                                    final String selected = comboBox.getSelectionModel().getSelectedItem();
                                    
                                    // This flag prevents filtering when selecting an item from the dropdown
                                    Platform.runLater(() -> {
                                        if (selected == null || !selected.equals(editor.getText())) {
                                            filteredCustomers.setPredicate(customer -> {
                                                if (newValue == null || newValue.isEmpty()) {
                                                    return true;
                                                }
                                                String lowerCaseFilter = newValue.toLowerCase();
                                                return customer.toLowerCase().contains(lowerCaseFilter);
                                            });
                                            
                                            // Update the items in the ComboBox
                                            comboBox.setItems(filteredCustomers);
                                            
                                            // Keep the dropdown open while typing
                                            if (!comboBox.isShowing() && !newValue.isEmpty()) {
                                                comboBox.show();
                                            }
                                        }
                                    });
                                });
                            }
                            
                            // Initially set all items from cache
                            comboBox.setItems(filteredCustomers);
                            
                        } catch (Exception e) {
                            logger.error("Error setting up customers UI from cache", e);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("Error loading cached customers", throwable);
                    Platform.runLater(() -> {
                        // Fallback to direct database query
                        loadCustomersFromDatabase(comboBox);
                    });
                    return null;
                });
            
        } catch (Exception e) {
            logger.error("Error loading customers from cache", e);
            // Fallback to legacy database method
            loadCustomersFromDatabase(comboBox);
        }
    }
    
    // Legacy database loading method (fallback)
    private void loadCustomersFromDatabase(ComboBox<String> comboBox) {
        try {
            List<String> customers = loadDAO.getAllCustomers();
            ObservableList<String> customerList = FXCollections.observableArrayList(customers);
            
            // Create filtered list for search functionality
            FilteredList<String> filteredCustomers = new FilteredList<>(customerList, p -> true);
            
            // Add listener to filter items based on text input
            if (comboBox.getEditor() != null) {
                comboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
                    final TextField editor = comboBox.getEditor();
                    final String selected = comboBox.getSelectionModel().getSelectedItem();
                    
                    // This flag prevents filtering when selecting an item from the dropdown
                    Platform.runLater(() -> {
                        if (selected == null || !selected.equals(editor.getText())) {
                            filteredCustomers.setPredicate(customer -> {
                                if (newValue == null || newValue.isEmpty()) {
                                    return true;
                                }
                                String lowerCaseFilter = newValue.toLowerCase();
                                return customer.toLowerCase().contains(lowerCaseFilter);
                            });
                            
                            // Update the items in the ComboBox
                            comboBox.setItems(filteredCustomers);
                            
                            // Keep the dropdown open while typing
                            if (!comboBox.isShowing() && !newValue.isEmpty()) {
                                comboBox.show();
                            }
                        }
                    });
                });
            }
            
            // Initially set all items
            comboBox.setItems(filteredCustomers);
            
        } catch (Exception e) {
            logger.error("Error loading customers from database", e);
        }
    }
    
    // Cached location data for faster dialog loading
    private volatile Set<String> cachedAllLocations = new HashSet<>();
    private volatile Map<String, Set<String>> cachedLocationToCustomersMap = new HashMap<>();
    private volatile long lastLocationCacheTime = 0;
    private static final long LOCATION_CACHE_VALIDITY = 30000; // 30 seconds
    
    // Helper method to enable filtering on location ComboBox with bidirectional search
    private void enableLocationFiltering(ComboBox<String> locationCombo) {
        enableLocationFiltering(locationCombo, false);
    }
    
    // Overloaded method with force refresh option
    private void enableLocationFiltering(ComboBox<String> locationCombo, boolean forceRefresh) {
        // Use cached data if available and not force refresh
        if (!forceRefresh && isCacheValid() && !cachedAllLocations.isEmpty()) {
            setupLocationComboWithCachedData(locationCombo);
            return;
        }
        
        // Load locations in background for better performance
        CompletableFuture.supplyAsync(() -> {
            try {
                List<String> allCustomers = loadDAO.getAllCustomers();
                Set<String> allLocations = new HashSet<>();
                Map<String, Set<String>> locationToCustomersMap = new HashMap<>();
                
                // Build a map of locations to customers
                for (String customer : allCustomers) {
                    Map<String, List<String>> customerLocations = loadDAO.getAllCustomerLocations(customer);
                    for (List<String> locations : customerLocations.values()) {
                        for (String location : locations) {
                            allLocations.add(location);
                            locationToCustomersMap.computeIfAbsent(location, k -> new HashSet<>()).add(customer);
                        }
                    }
                }
                
                // Update cache
                cachedAllLocations = allLocations;
                cachedLocationToCustomersMap = locationToCustomersMap;
                lastLocationCacheTime = System.currentTimeMillis();
                
                return new LocationData(allLocations, locationToCustomersMap);
                
            } catch (Exception e) {
                logger.error("Error loading all locations", e);
                return new LocationData(new HashSet<>(), new HashMap<>());
            }
        }).thenAccept(locationData -> {
            Platform.runLater(() -> {
                setupLocationComboWithData(locationCombo, locationData);
            });
        });
        
        // Set up with empty data immediately for responsiveness
        if (cachedAllLocations.isEmpty()) {
            setupLocationComboWithData(locationCombo, new LocationData(new HashSet<>(), new HashMap<>()));
        } else {
            setupLocationComboWithCachedData(locationCombo);
        }
    }
    
    private boolean isCacheValid() {
        return (System.currentTimeMillis() - lastLocationCacheTime) < LOCATION_CACHE_VALIDITY;
    }
    
    private void setupLocationComboWithCachedData(ComboBox<String> locationCombo) {
        setupLocationComboWithData(locationCombo, new LocationData(cachedAllLocations, cachedLocationToCustomersMap));
    }
    
    private void setupLocationComboWithData(ComboBox<String> locationCombo, LocationData locationData) {
        ObservableList<String> locationList = FXCollections.observableArrayList(new ArrayList<>(locationData.allLocations));
        locationList.sort(String::compareToIgnoreCase);
        FilteredList<String> filteredList = new FilteredList<>(locationList, p -> true);
        
        // Store the location-to-customers map in the ComboBox properties
        locationCombo.getProperties().put("locationToCustomersMap", locationData.locationToCustomersMap);
        
        // Add filtering listener to the location ComboBox
        if (locationCombo.isEditable() && locationCombo.getEditor() != null) {
            ChangeListener<String> filterListener = (obs, oldValue, newValue) -> {
                final TextField editor = locationCombo.getEditor();
                final String selected = locationCombo.getSelectionModel().getSelectedItem();
                
                Platform.runLater(() -> {
                    if (selected == null || !selected.equals(editor.getText())) {
                        filteredList.setPredicate(location -> {
                            if (newValue == null || newValue.isEmpty()) {
                                return true;
                            }
                            String lowerCaseFilter = newValue.toLowerCase();
                            // Case-insensitive search that matches any part of the address
                            String lowerLocation = location.toLowerCase();
                            return lowerLocation.contains(lowerCaseFilter);
                        });
                        
                        // Keep dropdown open while typing
                        if (!locationCombo.isShowing() && !newValue.isEmpty()) {
                            locationCombo.show();
                        }
                    }
                });
            };
            
            locationCombo.getEditor().textProperty().addListener(filterListener);
            locationCombo.setUserData(filterListener); // Store listener reference
        }
        
        locationCombo.setItems(filteredList);
    }
    
    // Helper class to hold location data
    private static class LocationData {
        final Set<String> allLocations;
        final Map<String, Set<String>> locationToCustomersMap;
        
        LocationData(Set<String> allLocations, Map<String, Set<String>> locationToCustomersMap) {
            this.allLocations = allLocations;
            this.locationToCustomersMap = locationToCustomersMap;
        }
    }
    
    // Helper method to auto-select best matching customer for a location
    private void autoSelectBestMatchingCustomer(ComboBox<String> customerCombo, String selectedLocation) {
        try {
            if (selectedLocation == null || selectedLocation.trim().isEmpty()) {
                return;
            }
            
            // Get all customers that have this location
            List<String> allCustomers = loadDAO.getAllCustomers();
            final String[] bestMatch = {null};
            
            for (String customer : allCustomers) {
                Map<String, List<String>> customerLocations = loadDAO.getAllCustomerLocations(customer);
                
                // Check all location types for this customer
                for (List<String> locations : customerLocations.values()) {
                    for (String location : locations) {
                        if (location.equalsIgnoreCase(selectedLocation.trim())) {
                            bestMatch[0] = customer;
                            break;
                        }
                    }
                    if (bestMatch[0] != null) break;
                }
                if (bestMatch[0] != null) break;
            }
            
            // Auto-select the best matching customer if found
            if (bestMatch[0] != null && customerCombo.getItems().contains(bestMatch[0])) {
                Platform.runLater(() -> {
                    customerCombo.setValue(bestMatch[0]);
                });
            }
            
        } catch (Exception e) {
            logger.error("Error auto-selecting customer for location: " + selectedLocation, e);
        }
    }

    // Helper method to update customer suggestions based on selected location
    @SuppressWarnings("unchecked")
    private void updateCustomerSuggestions(ComboBox<String> customerCombo, ComboBox<String> locationCombo, String selectedLocation) {
        try {
            // Get the location-to-customers map from the location ComboBox
            Map<String, Set<String>> locationToCustomersMap = 
                (Map<String, Set<String>>) locationCombo.getProperties().get("locationToCustomersMap");
            
            if (locationToCustomersMap != null && locationToCustomersMap.containsKey(selectedLocation)) {
                Set<String> matchingCustomers = locationToCustomersMap.get(selectedLocation);
                
                // If only one customer has this location, auto-select it
                if (matchingCustomers.size() == 1) {
                    String customer = matchingCustomers.iterator().next();
                    Platform.runLater(() -> {
                        customerCombo.setValue(customer);
                        showInfo("Auto-selected customer: " + customer);
                    });
                } else if (matchingCustomers.size() > 1) {
                    // Multiple customers have this location - show them at the top of the list
                    Platform.runLater(() -> {
                        ObservableList<String> allCustomers = FXCollections.observableArrayList(loadDAO.getAllCustomers());
                        
                        // Create a sorted list with matching customers first
                        List<String> sortedCustomers = new ArrayList<>();
                        sortedCustomers.addAll(matchingCustomers);
                        sortedCustomers.sort(String::compareToIgnoreCase);
                        
                        // Add remaining customers
                        for (String customer : allCustomers) {
                            if (!matchingCustomers.contains(customer)) {
                                sortedCustomers.add(customer);
                            }
                        }
                        
                        // Update the customer ComboBox with prioritized list
                        customerCombo.setItems(FXCollections.observableArrayList(sortedCustomers));
                        
                        // Show tooltip indicating matching customers
                        String matchList = String.join(", ", matchingCustomers);
                        Tooltip tooltip = new Tooltip("Customers with this location: " + matchList);
                        tooltip.setAutoHide(false);
                        tooltip.show(customerCombo, 
                            customerCombo.localToScreen(customerCombo.getBoundsInLocal()).getMinX(),
                            customerCombo.localToScreen(customerCombo.getBoundsInLocal()).getMaxY());
                        
                        // Auto-hide tooltip after 5 seconds
                        Timeline timeline = new Timeline(new KeyFrame(
                            Duration.seconds(5),
                            ae -> tooltip.hide()
                        ));
                        timeline.play();
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error updating customer suggestions for location: " + selectedLocation, e);
        }
    }
    
    // Helper method to update location dropdown based on selected customer
    private void updateLocationDropdown(ComboBox<String> locationCombo, String customerName) {
        try {
            // Ensure ComboBox is properly initialized
            if (locationCombo == null) {
                return;
            }
            
            Set<String> allLocations = new HashSet<>();
            
            if (customerName != null && !customerName.trim().isEmpty()) {
                // Load locations for specific customer
                Map<String, List<String>> customerLocations = loadDAO.getAllCustomerLocations(customerName);
                
                // Add all locations from all types (PICKUP, DROP, BOTH)
                customerLocations.values().forEach(allLocations::addAll);
            } else {
                // When no customer is selected, load ALL available locations from ALL customers
                // This ensures the user can still select locations even when customer field is empty
                try {
                    List<String> allCustomers = loadDAO.getAllCustomers();
                    for (String customer : allCustomers) {
                        Map<String, List<String>> customerLocations = loadDAO.getAllCustomerLocations(customer);
                        customerLocations.values().forEach(allLocations::addAll);
                    }
                } catch (Exception e) {
                    logger.warn("Could not load all locations: " + e.getMessage());
                }
            }
            
            // If we still have no locations, at least keep the current selection if any
            String currentSelection = locationCombo.getValue();
            if (currentSelection != null && !currentSelection.trim().isEmpty()) {
                allLocations.add(currentSelection);
            }
            
            ObservableList<String> locationList = FXCollections.observableArrayList(new ArrayList<>(allLocations));
            locationList.sort(String::compareToIgnoreCase);
            
            // Create filtered list for location search
            FilteredList<String> filteredLocations = new FilteredList<>(locationList, p -> true);
            
            // Remove any existing listeners to avoid duplicates
            if (locationCombo.getUserData() instanceof ChangeListener && locationCombo.getEditor() != null) {
                try {
                    locationCombo.getEditor().textProperty().removeListener((ChangeListener<String>) locationCombo.getUserData());
                    locationCombo.setUserData(null);
                } catch (Exception ex) {
                    // Ignore if listener was already removed
                    logger.debug("Could not remove previous listener: " + ex.getMessage());
                }
            }
            
            // Add new listener for filtering only if editor is available
            if (locationCombo.isEditable() && locationCombo.getEditor() != null) {
                ChangeListener<String> filterListener = (obs, oldValue, newValue) -> {
                    final TextField editor = locationCombo.getEditor();
                    final String selected = locationCombo.getSelectionModel().getSelectedItem();
                    
                    Platform.runLater(() -> {
                        if (selected == null || !selected.equals(editor.getText())) {
                            filteredLocations.setPredicate(location -> {
                                if (newValue == null || newValue.isEmpty()) {
                                    return true;
                                }
                                String lowerCaseFilter = newValue.toLowerCase();
                                // Case-insensitive search that matches street, city, or state
                                String lowerLocation = location.toLowerCase();
                                return lowerLocation.contains(lowerCaseFilter);
                            });
                            
                            // Keep dropdown open while typing
                            if (!locationCombo.isShowing() && !newValue.isEmpty()) {
                                locationCombo.show();
                            }
                        }
                    });
                };
                
                locationCombo.getEditor().textProperty().addListener(filterListener);
                locationCombo.setUserData(filterListener); // Store listener reference
            }
            
            locationCombo.setItems(filteredLocations);
            
            // Restore the previous selection if it exists in the new list
            if (currentSelection != null && locationList.contains(currentSelection)) {
                locationCombo.setValue(currentSelection);
            }
        } catch (Exception e) {
            logger.error("Error loading locations for customer: " + customerName, e);
        }
    }
    
    // Dialog to add new location for ComboBox
    private void showAddLocationDialogForCombo(ComboBox<String> locationCombo, String customerName) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add New Location");
        dialog.setHeaderText("Enter new location details");
        
        // Create form matching customer settings format
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField addressField = new TextField();
        addressField.setPromptText("123 Main St");
        addressField.setPrefWidth(300);
        
        TextField cityField = new TextField();
        cityField.setPromptText("City");
        cityField.setPrefWidth(200);
        
        TextField stateField = new TextField();
        stateField.setPromptText("State (e.g., NY, CA)");
        stateField.setPrefWidth(100);
        
        // Style the labels
        Label addressLabel = new Label("Street Address:");
        Label cityLabel = new Label("City:");
        Label stateLabel = new Label("State:");
        
        Stream.of(addressLabel, cityLabel, stateLabel).forEach(label -> 
            label.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;"));
        
        grid.add(addressLabel, 0, 0);
        grid.add(addressField, 1, 0);
        grid.add(cityLabel, 0, 1);
        grid.add(cityField, 1, 1);
        grid.add(stateLabel, 0, 2);
        grid.add(stateField, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Focus on address field when dialog opens
        Platform.runLater(() -> addressField.requestFocus());
        
        // Convert result - Format: "Street, City, State" (no ZIP)
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String address = addressField.getText().trim();
                String city = cityField.getText().trim();
                String state = stateField.getText().trim().toUpperCase();
                
                if (!address.isEmpty() && !city.isEmpty() && !state.isEmpty()) {
                    return String.format("%s, %s, %s", address, city, state);
                }
            }
            return null;
        });
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(location -> {
            try {
                // Save to database if customer is provided
                if (customerName != null && !customerName.trim().isEmpty()) {
                    // Save to customer settings with BOTH type (for both pickup and drop)
                    loadDAO.addCustomerLocationIfNotExists(customerName, "BOTH", location);
                    logger.info("Added new location for customer {}: {}", customerName, location);
                    
                    // Refresh the location dropdown to include the new location
                    updateLocationDropdown(locationCombo, customerName);
                    
                    // Select the newly added location
                    Platform.runLater(() -> {
                        locationCombo.setValue(location);
                    });
                    
                    showInfo("Location added successfully to customer settings!");
                } else {
                    // If no customer selected, just add to current dropdown
                    Platform.runLater(() -> {
                        ObservableList<String> items = FXCollections.observableArrayList(locationCombo.getItems());
                        if (!items.contains(location)) {
                            items.add(location);
                            items.sort(String::compareToIgnoreCase);
                            locationCombo.setItems(items);
                        }
                        locationCombo.setValue(location);
                    });
                    showInfo("Location added to current session. Select a customer to save permanently.");
                }
            } catch (Exception e) {
                logger.error("Error adding location", e);
                showError("Failed to add location: " + e.getMessage());
            }
        });
    }
    
    // Dialog to add new location (keeping for EnhancedLocationFieldOptimized compatibility)
    private void showAddLocationDialog(EnhancedLocationFieldOptimized locationField, String customerName) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add New Location");
        dialog.setHeaderText("Enter new location details");
        
        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField addressField = new TextField();
        addressField.setPromptText("123 Main St");
        TextField cityField = new TextField();
        cityField.setPromptText("City");
        TextField stateField = new TextField();
        stateField.setPromptText("State");
        TextField zipField = new TextField();
        zipField.setPromptText("ZIP Code");
        
        grid.add(new Label("Address:"), 0, 0);
        grid.add(addressField, 1, 0);
        grid.add(new Label("City:"), 0, 1);
        grid.add(cityField, 1, 1);
        grid.add(new Label("State:"), 0, 2);
        grid.add(stateField, 1, 2);
        grid.add(new Label("ZIP:"), 0, 3);
        grid.add(zipField, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String address = addressField.getText().trim();
                String city = cityField.getText().trim();
                String state = stateField.getText().trim();
                String zip = zipField.getText().trim();
                
                if (!address.isEmpty() && !city.isEmpty() && !state.isEmpty()) {
                    return String.format("%s, %s, %s %s", address, city, state, zip).trim();
                }
            }
            return null;
        });
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(location -> {
            try {
                // Save to database if customer is provided
                if (customerName != null && !customerName.trim().isEmpty()) {
                    loadDAO.addCustomerLocationIfNotExists(customerName, "BOTH", location);
                }
                // Set location in field
                locationField.setLocationString(location);
                showInfo("Location added successfully!");
            } catch (Exception e) {
                logger.error("Error adding location", e);
                showError("Failed to add location: " + e.getMessage());
            }
        });
    }

    // ENHANCEMENT #4: ENHANCED CUSTOMER FIELDS WITH CLEAR BUTTONS
    private static class EnhancedCustomerFieldWithClear extends HBox {
        private final ComboBox<String> customerCombo;
        private final Button clearButton;
        private final Button addButton;
        private final LoadDAO loadDAO;
        
        public EnhancedCustomerFieldWithClear(LoadDAO loadDAO) {
            this.loadDAO = loadDAO;
            this.customerCombo = new ComboBox<>();
            this.clearButton = new Button("×");
            this.addButton = new Button("+");
            
            setupComponents();
            setupStyling();
            setupEventHandlers();
            
            this.getChildren().addAll(customerCombo, clearButton, addButton);
            this.setSpacing(2);
            this.setAlignment(Pos.CENTER_LEFT);
        }
        
        private void setupComponents() {
            customerCombo.setEditable(true);
            customerCombo.setPrefWidth(180);
            customerCombo.setPromptText("Select or type customer...");
            
            clearButton.setPrefSize(25, 25);
            clearButton.setTooltip(new Tooltip("Clear selection"));
            
            addButton.setPrefSize(25, 25);
            addButton.setTooltip(new Tooltip("Add new customer"));
        }
        
        private void setupStyling() {
            // Clear button styling
            clearButton.setStyle(
                "-fx-background-color: #dc3545; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 3; " +
                "-fx-border-radius: 3; " +
                "-fx-font-size: 12px;"
            );
            
            // Add button styling
            addButton.setStyle(
                "-fx-background-color: #28a745; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 3; " +
                "-fx-border-radius: 3; " +
                "-fx-font-size: 12px;"
            );
            
            // Hover effects
            clearButton.setOnMouseEntered(e -> clearButton.setStyle(clearButton.getStyle() + "-fx-background-color: #c82333;"));
            clearButton.setOnMouseExited(e -> clearButton.setStyle(clearButton.getStyle().replace("-fx-background-color: #c82333;", "-fx-background-color: #dc3545;")));
            
            addButton.setOnMouseEntered(e -> addButton.setStyle(addButton.getStyle() + "-fx-background-color: #218838;"));
            addButton.setOnMouseExited(e -> addButton.setStyle(addButton.getStyle().replace("-fx-background-color: #218838;", "-fx-background-color: #28a745;")));
        }
        
        private void setupEventHandlers() {
            clearButton.setOnAction(e -> {
                customerCombo.setValue(null);
                customerCombo.getEditor().clear();
            });
            
            addButton.setOnAction(e -> showAddCustomerDialog());
        }
        
        private void showAddCustomerDialog() {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add New Customer");
            dialog.setHeaderText("Enter new customer name");
            dialog.setContentText("Customer name:");
            
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(customerName -> {
                if (!customerName.trim().isEmpty()) {
                    try {
                        loadDAO.addCustomerIfNotExists(customerName.trim());
                        refreshCustomers();
                        customerCombo.setValue(customerName.trim());
                        // Show success message
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText(null);
                        alert.setContentText("Customer added successfully!");
                        alert.showAndWait();
                    } catch (Exception ex) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText(null);
                        alert.setContentText("Failed to add customer: " + ex.getMessage());
                        alert.showAndWait();
                    }
                }
            });
        }
        
        public void refreshCustomers() {
            try {
                List<String> customers = loadDAO.getAllCustomers();
                customerCombo.setItems(FXCollections.observableArrayList(customers));
            } catch (Exception e) {
                // Log error but don't show to user
            }
        }
        
        public ComboBox<String> getComboBox() {
            return customerCombo;
        }
        
        public String getValue() {
            return customerCombo.getValue();
        }
        
        public void setValue(String value) {
            customerCombo.setValue(value);
        }
    }

    // ENHANCEMENT #5: TRUCK DROPDOWN INTEGRATION
    private static class EnhancedTruckDropdown extends ComboBox<String> {
        private final LoadDAO loadDAO;
        private final List<Employee> allDrivers;
        
        public EnhancedTruckDropdown(LoadDAO loadDAO, List<Employee> drivers) {
            this.loadDAO = loadDAO;
            this.allDrivers = drivers;
            
            setupComponent();
            loadTruckOptions();
            setupFiltering();
        }
        
        private void setupComponent() {
            setEditable(true);
            setPrefWidth(120);
            setPromptText("Select truck/unit...");
            
            // Enhanced styling
            setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 4;");
        }
        
        private void loadTruckOptions() {
            Set<String> truckUnits = new HashSet<>();
            
            // Get trucks from drivers
            for (Employee driver : allDrivers) {
                if (driver.getTruckUnit() != null && !driver.getTruckUnit().trim().isEmpty()) {
                    truckUnits.add(driver.getTruckUnit().trim());
                }
            }
            
            // Get trucks from existing loads - use available methods only
            try {
                // Get truck units from existing loads by querying all loads
                List<Load> allLoads = loadDAO.getAll();
                for (Load load : allLoads) {
                    if (load.getTruckUnitSnapshot() != null && !load.getTruckUnitSnapshot().trim().isEmpty()) {
                        truckUnits.add(load.getTruckUnitSnapshot().trim());
                    }
                }
            } catch (Exception e) {
                // Continue with driver trucks only
            }
            
            // Sort and set items
            List<String> sortedTrucks = new ArrayList<>(truckUnits);
            sortedTrucks.sort((a, b) -> {
                // Natural sorting for numbers
                try {
                    Integer numA = Integer.parseInt(a);
                    Integer numB = Integer.parseInt(b);
                    return numA.compareTo(numB);
                } catch (NumberFormatException e) {
                    return a.compareToIgnoreCase(b);
                }
            });
            
            setItems(FXCollections.observableArrayList(sortedTrucks));
        }
        
        private void setupFiltering() {
            // Create filtered list for search functionality
            FilteredList<String> filteredList = new FilteredList<>(getItems(), p -> true);
            
            // Add listener to filter items based on text input
            if (getEditor() != null) {
                getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
                    final String selected = getSelectionModel().getSelectedItem();
                    
                    Platform.runLater(() -> {
                        if (selected == null || !selected.equals(getEditor().getText())) {
                            filteredList.setPredicate(truck -> {
                                if (newValue == null || newValue.isEmpty()) {
                                    return true;
                                }
                                return truck.toLowerCase().contains(newValue.toLowerCase());
                            });
                            
                            if (!isShowing() && !newValue.isEmpty()) {
                                show();
                            }
                        }
                    });
                });
            }
            
            setItems(filteredList);
        }
        
        public void refreshTrucks() {
            loadTruckOptions();
        }
    }

    // ENHANCEMENT #6: MULTIPLE LOADS FUNCTIONALITY
    // Method removed to fix compilation issues
    private void showMultipleLoadsDialog() {
        // Implementation removed to fix compilation issues
    }
    // ENHANCEMENT #7: ADVANCED SEARCH AND FILTERING
    private class AdvancedSearchPanel extends VBox {
        private final TableView<Load> targetTable;
        private final FilteredList<Load> filteredList;
        private final TextField quickSearchField;
        private final DatePicker startDatePicker;
        private final DatePicker endDatePicker;
        private final ComboBox<Load.Status> statusFilter;
        private final ComboBox<String> billToFilter;
        private final CheckBox showOnlyLateCheckBox;
        private final Slider grossAmountSlider;
        private final Label grossAmountLabel;
        
        public AdvancedSearchPanel(TableView<Load> table, FilteredList<Load> filtered) {
            this.targetTable = table;
            this.filteredList = filtered;
            
            // Initialize components
            this.quickSearchField = new TextField();
            this.startDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
            this.endDatePicker = new DatePicker(LocalDate.now());
            this.statusFilter = new ComboBox<>();
            this.billToFilter = new ComboBox<>();
            this.showOnlyLateCheckBox = new CheckBox("Show only late loads");
            this.grossAmountSlider = new Slider(0, 10000, 0);
            this.grossAmountLabel = new Label("Min Gross: $0");
            
            setupComponents();
            setupLayout();
            setupEventHandlers();
        }
        
        private void setupComponents() {
            quickSearchField.setPromptText("🔍 Quick search (load#, PO, customer, location...)");
            quickSearchField.setPrefWidth(300);
            
            statusFilter.getItems().add(null); // All statuses
            statusFilter.getItems().addAll(Load.Status.values());
            statusFilter.setPromptText("All Statuses");
            
            billToFilter.setPromptText("All Bill To");
            
            grossAmountSlider.setShowTickLabels(true);
            grossAmountSlider.setShowTickMarks(true);
            grossAmountSlider.setMajorTickUnit(2000);
            grossAmountSlider.setBlockIncrement(500);
        }
        
        private void setupLayout() {
            setSpacing(10);
            setPadding(new Insets(10));
            setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
            
            // First row - Quick search and date range
            HBox row1 = new HBox(10);
            row1.getChildren().addAll(
                quickSearchField,
                new Label("From:"), startDatePicker,
                new Label("To:"), endDatePicker
            );
            
            // Second row - Filters
            HBox row2 = new HBox(10);
            row2.getChildren().addAll(
                new Label("Status:"), statusFilter,
                new Label("Bill To:"), billToFilter,
                showOnlyLateCheckBox
            );
            
            // Third row - Gross amount filter
            HBox row3 = new HBox(10);
            row3.getChildren().addAll(grossAmountLabel, grossAmountSlider);
            
            // Buttons
            HBox buttonRow = new HBox(10);
            Button clearBtn = new Button("🔄 Clear All Filters");
            Button saveSearchBtn = new Button("💾 Save Search");
            Button loadSearchBtn = new Button("📂 Load Search");
            
            clearBtn.setOnAction(e -> clearAllFilters());
            buttonRow.getChildren().addAll(clearBtn, saveSearchBtn, loadSearchBtn);
            
            getChildren().addAll(row1, row2, row3, buttonRow);
        }
        
        private void setupEventHandlers() {
            // Real-time filtering
            quickSearchField.textProperty().addListener((obs, old, newVal) -> applyFilters());
            startDatePicker.valueProperty().addListener((obs, old, newVal) -> applyFilters());
            endDatePicker.valueProperty().addListener((obs, old, newVal) -> applyFilters());
            statusFilter.valueProperty().addListener((obs, old, newVal) -> applyFilters());
            billToFilter.valueProperty().addListener((obs, old, newVal) -> applyFilters());
            showOnlyLateCheckBox.selectedProperty().addListener((obs, old, newVal) -> applyFilters());
            
            grossAmountSlider.valueProperty().addListener((obs, old, newVal) -> {
                grossAmountLabel.setText(String.format("Min Gross: $%.0f", newVal.doubleValue()));
                applyFilters();
            });
        }
        
        private void applyFilters() {
            filteredList.setPredicate(load -> {
                // Quick search filter
                String searchText = quickSearchField.getText();
                if (searchText != null && !searchText.trim().isEmpty()) {
                    String search = searchText.toLowerCase();
                    boolean matches = 
                        (load.getLoadNumber() != null && load.getLoadNumber().toLowerCase().contains(search)) ||
                        (load.getPONumber() != null && load.getPONumber().toLowerCase().contains(search)) ||
                        (load.getCustomer() != null && load.getCustomer().toLowerCase().contains(search)) ||
                        (load.getCustomer2() != null && load.getCustomer2().toLowerCase().contains(search)) ||
                        (load.getBillTo() != null && load.getBillTo().toLowerCase().contains(search)) ||
                        (load.getPickUpLocation() != null && load.getPickUpLocation().toLowerCase().contains(search)) ||
                        (load.getDropLocation() != null && load.getDropLocation().toLowerCase().contains(search)) ||
                        (load.getDriver() != null && load.getDriver().getName().toLowerCase().contains(search));
                    
                    if (!matches) return false;
                }
                
                // Date range filter
                if (startDatePicker.getValue() != null && load.getPickUpDate() != null) {
                    if (load.getPickUpDate().isBefore(startDatePicker.getValue())) return false;
                }
                if (endDatePicker.getValue() != null && load.getPickUpDate() != null) {
                    if (load.getPickUpDate().isAfter(endDatePicker.getValue())) return false;
                }
                
                // Status filter
                if (statusFilter.getValue() != null && !statusFilter.getValue().equals(load.getStatus())) {
                    return false;
                }
                
                // Bill To filter
                if (billToFilter.getValue() != null && !billToFilter.getValue().equals(load.getBillTo())) {
                    return false;
                }
                
                // Late loads filter
                if (showOnlyLateCheckBox.isSelected()) {
                    if (load.getStatus() != Load.Status.PICKUP_LATE && load.getStatus() != Load.Status.DELIVERY_LATE) {
                        return false;
                    }
                }
                
                // Gross amount filter
                if (load.getGrossAmount() < grossAmountSlider.getValue()) {
                    return false;
                }
                
                return true;
            });
        }
        
        private void clearAllFilters() {
            quickSearchField.clear();
            startDatePicker.setValue(LocalDate.now().minusMonths(1));
            endDatePicker.setValue(LocalDate.now());
            statusFilter.setValue(null);
            billToFilter.setValue(null);
            showOnlyLateCheckBox.setSelected(false);
            grossAmountSlider.setValue(0);
        }
        
        public void refreshBillToFilter(List<String> billToEntities) {
            String currentValue = billToFilter.getValue();
            billToFilter.getItems().clear();
            billToFilter.getItems().add(null); // All bill to
            billToFilter.getItems().addAll(billToEntities);
            if (billToEntities.contains(currentValue)) {
                billToFilter.setValue(currentValue);
            }
        }
    }

    // ENHANCEMENT #8: EXPORT FUNCTIONALITY
    private void showAdvancedExportDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Advanced Export Options");
        dialog.setHeaderText("Export loads data with custom options");
        dialog.getDialogPane().setPrefSize(600, 500);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Export format selection
        Label formatLabel = new Label("Export Format:");
        formatLabel.setStyle("-fx-font-weight: bold;");
        
        ToggleGroup formatGroup = new ToggleGroup();
        RadioButton csvRadio = new RadioButton("CSV (Comma Separated Values)");
        RadioButton excelRadio = new RadioButton("Excel (.xlsx)");
        RadioButton pdfRadio = new RadioButton("PDF Report");
        
        csvRadio.setToggleGroup(formatGroup);
        excelRadio.setToggleGroup(formatGroup);
        pdfRadio.setToggleGroup(formatGroup);
        csvRadio.setSelected(true);
        
        VBox formatBox = new VBox(5);
        formatBox.getChildren().addAll(csvRadio, excelRadio, pdfRadio);
        
        // Column selection
        Label columnsLabel = new Label("Columns to Export:");
        columnsLabel.setStyle("-fx-font-weight: bold;");
        
        CheckListView<String> columnsList = new CheckListView<>();
        String[] availableColumns = {
            "Load Number", "PO Number", "Customer", "Bill To", "Pickup Location", 
            "Delivery Location", "Driver", "Truck/Unit", "Trailer", "Status", 
            "Gross Amount", "Pickup Date", "Delivery Date", "Created Date", "Reminder"
        };
        columnsList.getItems().addAll(availableColumns);
        
        // Select all by default
        for (int i = 0; i < availableColumns.length; i++) {
            columnsList.getCheckModel().select(i);
        }
        
        columnsList.setPrefHeight(200);
        
        // Filter options
        Label filtersLabel = new Label("Filter Options:");
        filtersLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox currentFilterCheckBox = new CheckBox("Apply current table filters");
        CheckBox selectedOnlyCheckBox = new CheckBox("Export selected rows only");
        DatePicker exportStartDate = new DatePicker(LocalDate.now().minusMonths(1));
        DatePicker exportEndDate = new DatePicker(LocalDate.now());
        
        currentFilterCheckBox.setSelected(true);
        
        GridPane filterGrid = new GridPane();
        filterGrid.setHgap(10);
        filterGrid.setVgap(5);
        filterGrid.add(currentFilterCheckBox, 0, 0, 2, 1);
        filterGrid.add(selectedOnlyCheckBox, 0, 1, 2, 1);
        filterGrid.add(new Label("Date Range:"), 0, 2);
        filterGrid.add(exportStartDate, 1, 2);
        filterGrid.add(new Label("to"), 2, 2);
        filterGrid.add(exportEndDate, 3, 2);
        
        content.getChildren().addAll(
            formatLabel, formatBox,
            new Separator(),
            columnsLabel, columnsList,
            new Separator(),
            filtersLabel, filterGrid
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                RadioButton selectedFormat = (RadioButton) formatGroup.getSelectedToggle();
                List<String> selectedColumns = columnsList.getCheckModel().getSelectedItems();
                
                handleAdvancedExport(
                    selectedFormat.getText(),
                    selectedColumns,
                    currentFilterCheckBox.isSelected(),
                    selectedOnlyCheckBox.isSelected(),
                    exportStartDate.getValue(),
                    exportEndDate.getValue()
                );
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void handleAdvancedExport(String format, List<String> columns, boolean useCurrentFilters,
                                    boolean selectedOnly, LocalDate startDate, LocalDate endDate) {
        try {
            // Get data to export
            List<Load> dataToExport;
            if (selectedOnly) {
                // Get current tab selection - default to first tab if no selection
                StatusTab currentTab = statusTabs.get(0); // Default to active loads tab
                dataToExport = new ArrayList<>(currentTab.table.getSelectionModel().getSelectedItems());
            } else if (useCurrentFilters) {
                // Get current tab's filtered list - default to first tab if no selection  
                StatusTab currentTab = statusTabs.get(0); // Default to active loads tab
                dataToExport = new ArrayList<>(currentTab.filteredList);
            } else {
                dataToExport = loadDAO.getAll();
            }
            
            // Apply date filter if needed
            if (startDate != null || endDate != null) {
                dataToExport = dataToExport.stream()
                    .filter(load -> {
                        LocalDate loadDate = load.getPickUpDate(); // Use pickup date instead of created date
                        if (loadDate == null) loadDate = load.getDeliveryDate(); // Fallback to delivery date
                        if (loadDate == null) return false;
                        if (startDate != null && loadDate.isBefore(startDate)) return false;
                        if (endDate != null && loadDate.isAfter(endDate)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
            }
            
            // Choose export method based on format
            if (format.contains("CSV")) {
                exportToCSVAdvanced(dataToExport, columns);
            } else if (format.contains("Excel")) {
                exportToExcel(dataToExport, columns);
            } else if (format.contains("PDF")) {
                exportToPDF(dataToExport, columns);
            }
            
        } catch (Exception e) {
            logger.error("Error during advanced export", e);
            showError("Export failed: " + e.getMessage());
        }
    }
    
    private void exportToCSVAdvanced(List<Load> loads, List<String> columns) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV Export");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("loads_export_" + LocalDate.now() + ".csv");
        
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Write header
                writer.println(String.join(",", columns));
                
                // Write data
                for (Load load : loads) {
                    List<String> row = new ArrayList<>();
                    for (String column : columns) {
                        row.add(getColumnValue(load, column));
                    }
                    writer.println(String.join(",", row));
                }
                
                showInfo("Export completed successfully to: " + file.getAbsolutePath());
            } catch (IOException e) {
                showError("Failed to export CSV: " + e.getMessage());
            }
        }
    }
    
    private void exportToExcel(List<Load> loads, List<String> columns) {
        showInfo("Excel export feature coming soon!");
    }
    
    private void exportToPDF(List<Load> loads, List<String> columns) {
        showInfo("PDF export feature coming soon!");
    }
    
    private String getColumnValue(Load load, String column) {
        switch (column) {
            case "Load Number": return escapeCSV(load.getLoadNumber());
            case "PO Number": return escapeCSV(load.getPONumber());
            case "Customer": return escapeCSV(load.getCustomer());
            case "Bill To": return escapeCSV(load.getBillTo());
            case "Pickup Location": return escapeCSV(load.getPickUpLocation());
            case "Delivery Location": return escapeCSV(load.getDropLocation());
            case "Driver": return escapeCSV(load.getDriver() != null ? load.getDriver().getName() : "");
            case "Truck/Unit": return escapeCSV(load.getTruckUnitSnapshot());
            case "Trailer": return escapeCSV(load.getTrailerNumber());
            case "Status": return escapeCSV(load.getStatus().toString());
            case "Gross Amount": return String.valueOf(load.getGrossAmount());
            case "Pickup Date": return load.getPickUpDate() != null ? load.getPickUpDate().toString() : "";
            case "Delivery Date": return load.getDeliveryDate() != null ? load.getDeliveryDate().toString() : "";
            case "Created Date": return load.getPickUpDate() != null ? load.getPickUpDate().toString() : ""; // Use pickup date as created date
            case "Reminder": return escapeCSV(load.getReminder());
            default: return "";
        }
    }
    
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ENHANCEMENT #9: NOTIFICATION SYSTEM
    private static class NotificationManager {
        private static final ObservableList<Notification> notifications = FXCollections.observableArrayList();
        private static VBox notificationArea;
        
        public static void initialize(VBox area) {
            notificationArea = area;
        }
        
        public static void showNotification(String title, String message, NotificationType type) {
            Notification notification = new Notification(title, message, type);
            notifications.add(notification);
            
            Platform.runLater(() -> {
                if (notificationArea != null) {
                    HBox notificationBox = createNotificationBox(notification);
                    notificationArea.getChildren().add(0, notificationBox);
                    
                    // Auto-remove after 5 seconds
                    Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
                        notificationArea.getChildren().remove(notificationBox);
                        notifications.remove(notification);
                    }));
                    timeline.play();
                }
            });
        }
        
        private static HBox createNotificationBox(Notification notification) {
            HBox box = new HBox(10);
            box.setPadding(new Insets(10));
            box.setAlignment(Pos.CENTER_LEFT);
            
            // Style based on type
            String backgroundColor;
            String textColor = "white";
            switch (notification.getType()) {
                case SUCCESS: backgroundColor = "#28a745"; break;
                case WARNING: backgroundColor = "#ffc107"; textColor = "black"; break;
                case ERROR: backgroundColor = "#dc3545"; break;
                case INFO: 
                default: backgroundColor = "#17a2b8"; break;
            }
            
            box.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 5; -fx-border-radius: 5;",
                backgroundColor
            ));
            
            Label titleLabel = new Label(notification.getTitle());
            titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
            
            Label messageLabel = new Label(notification.getMessage());
            messageLabel.setStyle("-fx-text-fill: " + textColor + ";");
            
            Button closeBtn = new Button("×");
            closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; " +
                "-fx-font-weight: bold; -fx-border-color: transparent;"
            );
            closeBtn.setOnAction(e -> {
                notificationArea.getChildren().remove(box);
                notifications.remove(notification);
            });
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            box.getChildren().addAll(titleLabel, messageLabel, spacer, closeBtn);
            return box;
        }
        
        public static void showSuccess(String message) {
            showNotification("Success", message, NotificationType.SUCCESS);
        }
        
        public static void showWarning(String message) {
            showNotification("Warning", message, NotificationType.WARNING);
        }
        
        public static void showError(String message) {
            showNotification("Error", message, NotificationType.ERROR);
        }
        
        public static void showInfo(String message) {
            showNotification("Info", message, NotificationType.INFO);
        }
    }
    
    private static class Notification {
        private final String title;
        private final String message;
        private final NotificationType type;
        private final LocalDateTime timestamp;
        
        public Notification(String title, String message, NotificationType type) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.timestamp = LocalDateTime.now();
        }
        
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public NotificationType getType() { return type; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    private enum NotificationType {
        SUCCESS, WARNING, ERROR, INFO
    }

    // ENHANCEMENT #10: DASHBOARD ANALYTICS
    private Tab createDashboardTab() {
        VBox dashboardContent = new VBox(20);
        dashboardContent.setPadding(new Insets(20));
        dashboardContent.setStyle("-fx-background-color: #f8f9fa;");
        
        // Header
        Label titleLabel = new Label("📊 Fleet Management Dashboard");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        // Metrics cards row
        HBox metricsRow = new HBox(20);
        metricsRow.setAlignment(Pos.CENTER);
        
        try {
            List<Load> loads = loadDAO.getAll();
            
            // Total loads card
            VBox totalLoadsCard = createMetricCard("Total Loads", String.valueOf(loads.size()), "#3498db", "📦");
            
            // Active loads card
            long activeLoads = loads.stream().filter(l -> 
                l.getStatus() == Load.Status.BOOKED || 
                l.getStatus() == Load.Status.ASSIGNED || 
                l.getStatus() == Load.Status.IN_TRANSIT
            ).count();
            VBox activeLoadsCard = createMetricCard("Active Loads", String.valueOf(activeLoads), "#e74c3c", "🚛");
            
            // Revenue card
            double totalRevenue = loads.stream().mapToDouble(Load::getGrossAmount).sum();
            VBox revenueCard = createMetricCard("Total Revenue", String.format("$%.2f", totalRevenue), "#27ae60", "💰");
            
            // Late loads card
            long lateLoads = loads.stream().filter(l -> 
                l.getStatus() == Load.Status.PICKUP_LATE || 
                l.getStatus() == Load.Status.DELIVERY_LATE
            ).count();
            VBox lateLoadsCard = createMetricCard("Late Loads", String.valueOf(lateLoads), "#f39c12", "⚠️");
            
            metricsRow.getChildren().addAll(totalLoadsCard, activeLoadsCard, revenueCard, lateLoadsCard);
            
            // Charts row
            HBox chartsRow = new HBox(20);
            
            // Status distribution pie chart
            VBox statusChartBox = createStatusChart(loads);
            
            // Monthly revenue bar chart
            VBox revenueChartBox = createMonthlyRevenueChart(loads);
            
            chartsRow.getChildren().addAll(statusChartBox, revenueChartBox);
            
            // Recent activity
            VBox recentActivityBox = createRecentActivityBox(loads);
            
            dashboardContent.getChildren().addAll(titleLabel, metricsRow, chartsRow, recentActivityBox);
            
        } catch (Exception e) {
            logger.error("Error creating dashboard", e);
            Label errorLabel = new Label("Error loading dashboard data");
            dashboardContent.getChildren().addAll(titleLabel, errorLabel);
        }
        
        ScrollPane scrollPane = new ScrollPane(dashboardContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return new Tab("📊 Dashboard", scrollPane);
    }
    
    private VBox createMetricCard(String title, String value, String color, String icon) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200);
        card.setPrefHeight(120);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 10; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);",
            color
        ));
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 32px;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        
        card.getChildren().addAll(iconLabel, valueLabel, titleLabel);
        return card;
    }
    
    private VBox createStatusChart(List<Load> loads) {
        VBox chartBox = new VBox(10);
        chartBox.setPadding(new Insets(20));
        chartBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #dee2e6; -fx-border-radius: 10;");
        
        Label chartTitle = new Label("Load Status Distribution");
        chartTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Simple text-based chart for now
        VBox statusList = new VBox(5);
        Map<Load.Status, Long> statusCounts = loads.stream()
            .collect(Collectors.groupingBy(Load::getStatus, Collectors.counting()));
        
        for (Map.Entry<Load.Status, Long> entry : statusCounts.entrySet()) {
            HBox statusRow = new HBox(10);
            statusRow.setAlignment(Pos.CENTER_LEFT);
            
            Label statusLabel = new Label(entry.getKey().toString());
            statusLabel.setPrefWidth(100);
            
            Label countLabel = new Label(entry.getValue().toString());
            countLabel.setStyle("-fx-font-weight: bold;");
            
            statusRow.getChildren().addAll(statusLabel, countLabel);
            statusList.getChildren().add(statusRow);
        }
        
        chartBox.getChildren().addAll(chartTitle, statusList);
        return chartBox;
    }
    
    private VBox createMonthlyRevenueChart(List<Load> loads) {
        VBox chartBox = new VBox(10);
        chartBox.setPadding(new Insets(20));
        chartBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #dee2e6; -fx-border-radius: 10;");
        
        Label chartTitle = new Label("Monthly Revenue");
        chartTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Group loads by month and calculate revenue
        Map<String, Double> monthlyRevenue = loads.stream()
            .filter(load -> load.getPickUpDate() != null)
            .collect(Collectors.groupingBy(
                load -> load.getPickUpDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.summingDouble(Load::getGrossAmount)
            ));
        
        VBox revenueList = new VBox(5);
        monthlyRevenue.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                HBox monthRow = new HBox(10);
                monthRow.setAlignment(Pos.CENTER_LEFT);
                
                Label monthLabel = new Label(entry.getKey());
                monthLabel.setPrefWidth(80);
                
                Label revenueLabel = new Label(String.format("$%.2f", entry.getValue()));
                revenueLabel.setStyle("-fx-font-weight: bold;");
                
                monthRow.getChildren().addAll(monthLabel, revenueLabel);
                revenueList.getChildren().add(monthRow);
            });
        
        chartBox.getChildren().addAll(chartTitle, revenueList);
        return chartBox;
    }
    
    private VBox createRecentActivityBox(List<Load> loads) {
        VBox activityBox = new VBox(10);
        activityBox.setPadding(new Insets(20));
        activityBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #dee2e6; -fx-border-radius: 10;");
        
        Label activityTitle = new Label("Recent Activity");
        activityTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Show recent loads (last 5)
        List<Load> recentLoads = loads.stream()
            .filter(load -> load.getPickUpDate() != null)
            .sorted((a, b) -> {
                LocalDate dateA = a.getPickUpDate();
                LocalDate dateB = b.getPickUpDate();
                if (dateA == null && dateB == null) return 0;
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA);
            })
            .limit(5)
            .collect(Collectors.toList());
        
        VBox activityList = new VBox(5);
        for (Load load : recentLoads) {
            HBox activityRow = new HBox(10);
            activityRow.setAlignment(Pos.CENTER_LEFT);
            
            Label loadLabel = new Label(load.getLoadNumber());
            loadLabel.setStyle("-fx-font-weight: bold;");
            loadLabel.setPrefWidth(80);
            
            Label customerLabel = new Label(load.getCustomer());
            customerLabel.setPrefWidth(100);
            
            Label statusLabel = new Label(load.getStatus().toString());
            statusLabel.setStyle("-fx-text-fill: " + getStatusTextColor(load.getStatus()) + ";");
            
            activityRow.getChildren().addAll(loadLabel, customerLabel, statusLabel);
            activityList.getChildren().add(activityRow);
        }
        
        activityBox.getChildren().addAll(activityTitle, activityList);
        return activityBox;
    }
    
    private String getStatusTextColor(Load.Status status) {
        switch (status) {
            case DELIVERED: return "#27ae60";
            case PAID: return "#2c3e50";
            case CANCELLED: return "#e74c3c";
            case PICKUP_LATE:
            case DELIVERY_LATE: return "#f39c12";
            default: return "#3498db";
        }
    }

    /**
     * Public method to show the load dialog from external components.
     * 
     * @param load The load to edit, or null to create a new load
     * @param isAdd True to add a new load, false to edit existing
     */
    public void showLoadDialogPublic(Load load, boolean isAdd) {
        showLoadDialog(load, isAdd);
    }
    
    /**
     * Helper class to hold dialog field references with enhanced enterprise fields
     */
    private static class LoadDialogFields {
        // Basic Information
        TextField loadNumField;
        TextField poField;
        ComboBox<String> billToBox;
        
        // Enhanced Customer & Location Fields
        EnhancedCustomerFieldWithClear pickupCustomerField;
        EnhancedCustomerFieldWithClear dropCustomerField;
        
        // Legacy fields for backward compatibility (may be removed later)
        ComboBox<String> pickupCustomerBox;
        ComboBox<String> dropCustomerBox;
        ComboBox<String> pickupLocationBox;
        ComboBox<String> dropLocationBox;
        
        // Multi-location support
        List<ComboBox<String>> additionalPickupLocations = new ArrayList<>();
        List<DatePicker> additionalPickupDates = new ArrayList<>();
        List<Spinner<LocalTime>> additionalPickupTimes = new ArrayList<>();
        List<ComboBox<String>> additionalDropLocations = new ArrayList<>();
        List<DatePicker> additionalDropDates = new ArrayList<>();
        List<Spinner<LocalTime>> additionalDropTimes = new ArrayList<>();
        
        // Enhanced Driver & Equipment
        ComboBox<Employee> driverBox;
        ComboBox<Truck> truckBox;  // New truck dropdown
        ComboBox<Trailer> trailerBox;
        TextField truckUnitSearchField;
        TextField trailerSearchField;
        
        // Schedule & Financial
        DatePicker pickUpDatePicker;
        Spinner<LocalTime> pickUpTimeSpinner;
        DatePicker deliveryDatePicker;
        Spinner<LocalTime> deliveryTimeSpinner;
        TextField grossField;
        ComboBox<Load.Status> statusBox;
        
        // Additional Details
        TextArea notesField;
        TextField reminderField;
        CheckBox hasLumperCheck;
        TextField lumperAmountField;
        CheckBox hasRevisedRateCheck;
    }
    
    private LoadDialogFields dialogFields;
    
    private void showLoadDialog(Load load, boolean isAdd) {
        // Initialize dialog fields
        dialogFields = new LoadDialogFields();
        logger.debug("Showing load dialog - isAdd: {}", isAdd);
        
        // If editing, load the locations for the load
        if (!isAdd && load != null && load.getId() > 0) {
            List<LoadLocation> locations = loadDAO.getLoadLocations(load.getId());
            load.setLocations(locations);
        }
        
        // Create main dialog with modern styling
        Dialog<Load> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add New Load" : "Edit Load");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Set dialog properties
        dialog.setResizable(true);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        // Modern dialog sizing
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setPrefWidth(900);
        dialogPane.setPrefHeight(700);
        dialogPane.setMinWidth(800);
        dialogPane.setMinHeight(600);
        dialogPane.setMaxWidth(1000);
        dialogPane.setMaxHeight(800);
        
        // Apply modern dialog styling
        dialog.getDialogPane().getStyleClass().add("modern-load-dialog");
        
        // Enhanced window setup
        dialog.setOnShown(event -> {
            javafx.stage.Window window = dialog.getDialogPane().getScene().getWindow();
            if (window instanceof javafx.stage.Stage) {
                javafx.stage.Stage stage = (javafx.stage.Stage) window;
                stage.setMinWidth(800);
                stage.setMinHeight(600);
                stage.setWidth(900);
                stage.setHeight(700);
                stage.centerOnScreen();
            }
        });
        
        // Create modern header
        VBox header = new VBox();
        header.getStyleClass().add("modern-dialog-header");
        Label headerLabel = new Label(isAdd ? "Create New Load" : "Edit Load " + (load != null ? load.getLoadNumber() : ""));
        headerLabel.getStyleClass().add("modern-dialog-title");
        header.getChildren().add(headerLabel);
        
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        
        // Create main content container
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("modern-form-container");
        mainContainer.setFillWidth(true);
        
        // Add header to main container
        mainContainer.getChildren().add(header);
        
        // Create scrollable content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("modern-scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        VBox content = new VBox(20);
        content.getStyleClass().add("modern-form-container");
        content.setFillWidth(true);
        
        // Load Information Section
        VBox loadInfoSection = createSection("Load Information", "");
        
        GridPane loadInfoGrid = new GridPane();
        loadInfoGrid.getStyleClass().add("modern-form-grid");
        
        // Load Number
        Label loadNumLabel = new Label("Load Number*");
        loadNumLabel.getStyleClass().add("modern-form-label");
        dialogFields.loadNumField = new TextField();
        dialogFields.loadNumField.setPromptText("e.g., L-2024-001");
        dialogFields.loadNumField.getStyleClass().add("modern-text-field");
        dialogFields.loadNumField.setPrefWidth(250);
        
        // PO Number
        Label poLabel = new Label("PO Number");
        poLabel.getStyleClass().add("modern-form-label");
        dialogFields.poField = new TextField();
        dialogFields.poField.setPromptText("Optional");
        dialogFields.poField.getStyleClass().add("modern-text-field");
        dialogFields.poField.setPrefWidth(250);
        
        // Bill To
        Label billToLabel = new Label("Bill To");
        billToLabel.getStyleClass().add("modern-form-label");
        dialogFields.billToBox = new ComboBox<>();
        dialogFields.billToBox.setPromptText("Select billing entity");
        dialogFields.billToBox.setEditable(true);
        dialogFields.billToBox.getStyleClass().add("modern-combo-box");
        dialogFields.billToBox.setPrefWidth(250);
        
        // Load billing entities
        loadBillingEntitiesIntoComboBox(dialogFields.billToBox);
        
        // Pre-fill if editing
        if (load != null) {
            dialogFields.loadNumField.setText(load.getLoadNumber());
            dialogFields.poField.setText(load.getPONumber());
            dialogFields.billToBox.setValue(load.getBillTo());
        }
        
        // Add to grid with professional spacing - move PO Number further right
        loadInfoGrid.add(loadNumLabel, 0, 0);
        loadInfoGrid.add(dialogFields.loadNumField, 1, 0);
        loadInfoGrid.add(poLabel, 4, 0);  // Move further right for better organization
        loadInfoGrid.add(dialogFields.poField, 5, 0);
        
        loadInfoGrid.add(billToLabel, 0, 1);
        loadInfoGrid.add(dialogFields.billToBox, 1, 1, 5, 1);
        
        loadInfoSection.getChildren().add(loadInfoGrid);
        
        // Customer & Location Section
        VBox customerLocationSection = createSection("Customer & Location Details", "");
        
        // Refresh button removed as requested
        
        GridPane customerLocGrid = new GridPane();
        customerLocGrid.getStyleClass().add("modern-form-grid");
        
        // Pickup Customer
        Label pickupCustomerLabel = new Label("Pickup Customer");
        pickupCustomerLabel.getStyleClass().add("modern-form-label");
        dialogFields.pickupCustomerBox = new ComboBox<>();
        dialogFields.pickupCustomerBox.setPromptText("Select/Enter");
        dialogFields.pickupCustomerBox.setEditable(true);
        dialogFields.pickupCustomerBox.getStyleClass().add("modern-combo-box");
        dialogFields.pickupCustomerBox.setPrefWidth(200);
        
        // Load customers asynchronously
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> loadCustomersIntoComboBox(dialogFields.pickupCustomerBox));
        });
        
        // Add button for new pickup customer
        Button addPickupCustomerBtn = new Button("+");
        addPickupCustomerBtn.getStyleClass().add("inline-add-button");
        addPickupCustomerBtn.setStyle("-fx-background-color: #28a745;");
        addPickupCustomerBtn.setTooltip(new Tooltip("Add new customer"));
        addPickupCustomerBtn.setOnAction(e -> showAddCustomerDialog(dialogFields.pickupCustomerBox));
        
        HBox pickupCustomerBox = new HBox(5);
        pickupCustomerBox.getChildren().addAll(dialogFields.pickupCustomerBox, addPickupCustomerBtn);
        
        // Pickup Location
        Label pickupLocationLabel = new Label("Pickup Location");
        pickupLocationLabel.getStyleClass().add("modern-form-label");
        ComboBox<String> pickupLocationCombo = new ComboBox<>();
        pickupLocationCombo.setPromptText("Select/Enter");
        pickupLocationCombo.setEditable(true);
        pickupLocationCombo.getStyleClass().add("modern-combo-box");
        pickupLocationCombo.setPrefWidth(200);
        
        // Initialize location dropdown asynchronously
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> enableLocationFiltering(pickupLocationCombo));
        });
        
        // Add button for new pickup location
        Button addPickupLocationBtn = new Button("+");
        addPickupLocationBtn.getStyleClass().add("inline-add-button");
        addPickupLocationBtn.setStyle("-fx-background-color: #28a745;");
        addPickupLocationBtn.setTooltip(new Tooltip("Add new location"));
        addPickupLocationBtn.setOnAction(e -> showAddLocationDialogForCombo(pickupLocationCombo, dialogFields.pickupCustomerBox.getValue()));
        
        HBox pickupLocationBox = new HBox(5);
        pickupLocationBox.getChildren().addAll(pickupLocationCombo, addPickupLocationBtn);
        
        // Store in dialog fields
        dialogFields.pickupLocationBox = pickupLocationCombo;
        
        // Now add the customer change listener
        dialogFields.pickupCustomerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLocationDropdown(pickupLocationCombo, newVal);
        });
        
        // Enhanced bidirectional listener - auto-select customer when location is selected
        pickupLocationCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                updateCustomerSuggestions(dialogFields.pickupCustomerBox, pickupLocationCombo, newVal);
                // Auto-select the best matching customer
                autoSelectBestMatchingCustomer(dialogFields.pickupCustomerBox, newVal);
            }
        });
        
        // Drop Customer
        Label dropCustomerLabel = new Label("Drop Customer");
        dropCustomerLabel.getStyleClass().add("modern-form-label");
        dialogFields.dropCustomerBox = new ComboBox<>();
        dialogFields.dropCustomerBox.setPromptText("Select/Enter");
        dialogFields.dropCustomerBox.setEditable(true);
        dialogFields.dropCustomerBox.getStyleClass().add("modern-combo-box");
        dialogFields.dropCustomerBox.setPrefWidth(200);
        
        // Load customers asynchronously
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> loadCustomersIntoComboBox(dialogFields.dropCustomerBox));
        });
        
        // Add button for new drop customer
        Button addDropCustomerBtn = new Button("+");
        addDropCustomerBtn.getStyleClass().add("inline-add-button");
        addDropCustomerBtn.setStyle("-fx-background-color: #28a745;");
        addDropCustomerBtn.setTooltip(new Tooltip("Add new customer"));
        addDropCustomerBtn.setOnAction(e -> showAddCustomerDialog(dialogFields.dropCustomerBox));
        
        HBox dropCustomerBox = new HBox(5);
        dropCustomerBox.getChildren().addAll(dialogFields.dropCustomerBox, addDropCustomerBtn);
        
        // Drop Location
        Label dropLocationLabel = new Label("Drop Location");
        dropLocationLabel.getStyleClass().add("modern-form-label");
        ComboBox<String> dropLocationCombo = new ComboBox<>();
        dropLocationCombo.setPromptText("Select/Enter");
        dropLocationCombo.setEditable(true);
        dropLocationCombo.getStyleClass().add("modern-combo-box");
        dropLocationCombo.setPrefWidth(200);
        
        // Initialize drop location dropdown asynchronously
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> enableLocationFiltering(dropLocationCombo));
        });
        
        // Add button for new drop location
        Button addDropLocationBtn = new Button("+");
        addDropLocationBtn.getStyleClass().add("inline-add-button");
        addDropLocationBtn.setStyle("-fx-background-color: #28a745;");
        addDropLocationBtn.setTooltip(new Tooltip("Add new location"));
        addDropLocationBtn.setOnAction(e -> showAddLocationDialogForCombo(dropLocationCombo, dialogFields.dropCustomerBox.getValue()));
        
        HBox dropLocationBox = new HBox(5);
        dropLocationBox.getChildren().addAll(dropLocationCombo, addDropLocationBtn);
        
        // Store in dialog fields
        dialogFields.dropLocationBox = dropLocationCombo;
        
        // Now add the customer change listener
        dialogFields.dropCustomerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLocationDropdown(dropLocationCombo, newVal);
        });
        
        // Enhanced bidirectional listener - auto-select customer when location is selected
        dropLocationCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                updateCustomerSuggestions(dialogFields.dropCustomerBox, dropLocationCombo, newVal);
                // Auto-select the best matching customer
                autoSelectBestMatchingCustomer(dialogFields.dropCustomerBox, newVal);
            }
        });
        
        // Build the grid layout with professional spacing
        // Row 0: Pickup - move location further right for better organization
        customerLocGrid.add(pickupCustomerLabel, 0, 0);
        customerLocGrid.add(pickupCustomerBox, 1, 0);
        customerLocGrid.add(pickupLocationLabel, 4, 0);  // Move further right for professional look
        customerLocGrid.add(pickupLocationBox, 5, 0);
        
        // Row 1: Drop - move location further right for better organization
        customerLocGrid.add(dropCustomerLabel, 0, 1);
        customerLocGrid.add(dropCustomerBox, 1, 1);
        customerLocGrid.add(dropLocationLabel, 4, 1);    // Move further right for professional look
        customerLocGrid.add(dropLocationBox, 5, 1);
        
        // Add buttons for additional locations
        Button addPickupLocationRowBtn = new Button("+ Add Pickup");
        addPickupLocationRowBtn.getStyleClass().add("modern-add-button");
        addPickupLocationRowBtn.setTooltip(new Tooltip("Add another pickup location"));
        
        Button addDropLocationRowBtn = new Button("+ Add Drop");
        addDropLocationRowBtn.getStyleClass().add("modern-add-button");
        addDropLocationRowBtn.setTooltip(new Tooltip("Add another drop location"));
        
        // VBox containers for additional location rows
        VBox additionalPickupsContainer = new VBox(8);
        additionalPickupsContainer.setStyle("-fx-padding: 8px 0;");
        VBox additionalDropsContainer = new VBox(8);
        additionalDropsContainer.setStyle("-fx-padding: 8px 0;");
        
        // Row 2-3: Containers for additional pickup/drop locations
        customerLocGrid.add(additionalPickupsContainer, 0, 2, 6, 1);
        customerLocGrid.add(additionalDropsContainer, 0, 3, 6, 1);
        
        // Setup action for adding pickup locations with proper sequence tracking
        final int[] pickupRowCount = {0};
        final int[] maxPickupSequence = {1}; // Track the maximum sequence number used
        addPickupLocationRowBtn.setOnAction(e -> {
            // Calculate next sequence number properly
            int nextSequence = calculateNextSequenceNumber(additionalPickupsContainer, "Additional Pickup");
            maxPickupSequence[0] = Math.max(maxPickupSequence[0], nextSequence);
            pickupRowCount[0]++;
            HBox row = createAdditionalLocationRow("Additional Pickup " + nextSequence + ":", 
                                                 null, // Don't default to main customer - INDEPENDENT
                                                 true,
                                                 additionalPickupsContainer);
            additionalPickupsContainer.getChildren().add(row);
        });
        
        // Setup action for adding drop locations with proper sequence tracking
        final int[] dropRowCount = {0};
        final int[] maxDropSequence = {1}; // Track the maximum sequence number used
        addDropLocationRowBtn.setOnAction(e -> {
            // Calculate next sequence number properly
            int nextSequence = calculateNextSequenceNumber(additionalDropsContainer, "Additional Drop");
            maxDropSequence[0] = Math.max(maxDropSequence[0], nextSequence);
            dropRowCount[0]++;
            HBox row = createAdditionalLocationRow("Additional Drop " + nextSequence + ":", 
                                                 null, // Don't default to main customer - INDEPENDENT
                                                 false,
                                                 additionalDropsContainer);
            additionalDropsContainer.getChildren().add(row);
        });
        
        // Add buttons row with improved spacing
        HBox buttonsRow = new HBox();
        buttonsRow.getStyleClass().add("modern-button-row");
        buttonsRow.getChildren().addAll(addPickupLocationRowBtn, addDropLocationRowBtn);
        customerLocGrid.add(buttonsRow, 0, 4, 6, 1);
        
        customerLocationSection.getChildren().add(customerLocGrid);
        
        // Pre-fill customer and location data if editing
        if (load != null) {
            // Pre-fill pickup customer and location
            if (load.getCustomer() != null) {
                dialogFields.pickupCustomerBox.setValue(load.getCustomer());
                // Trigger location dropdown update
                updateLocationDropdown(pickupLocationCombo, load.getCustomer());
            }
            if (load.getPickUpLocation() != null) {
                Platform.runLater(() -> {
                    pickupLocationCombo.setValue(load.getPickUpLocation());
                });
            }
            
            // Pre-fill drop customer and location
            if (load.getCustomer2() != null) {
                dialogFields.dropCustomerBox.setValue(load.getCustomer2());
                // Trigger location dropdown update
                updateLocationDropdown(dropLocationCombo, load.getCustomer2());
            }
            if (load.getDropLocation() != null) {
                Platform.runLater(() -> {
                    dropLocationCombo.setValue(load.getDropLocation());
                });
            }
            
            // Clear existing additional location UI before loading new ones (prevent duplicates)
            additionalPickupsContainer.getChildren().clear();
            additionalDropsContainer.getChildren().clear();
            
            // Reset sequence tracking to prevent duplicate sequence numbers
            maxPickupSequence[0] = 1;
            maxDropSequence[0] = 1;
            pickupRowCount[0] = 0;
            dropRowCount[0] = 0;
            
            // Clear the additional location lists to prevent stale references
            dialogFields.additionalPickupLocations.clear();
            dialogFields.additionalPickupDates.clear();
            dialogFields.additionalPickupTimes.clear();
            dialogFields.additionalDropLocations.clear();
            dialogFields.additionalDropDates.clear();
            dialogFields.additionalDropTimes.clear();
            
            // Load additional locations if they exist
            if (load.getLocations() != null && !load.getLocations().isEmpty()) {
                // Calculate the highest pickup sequence number from all existing locations
                // to ensure proper continuation even if some were deleted
                int highestPickupSequence = load.getLocations().stream()
                    .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
                    .mapToInt(LoadLocation::getSequence)
                    .max()
                    .orElse(1); // Default to 1 if no additional pickups
                maxPickupSequence[0] = Math.max(maxPickupSequence[0], highestPickupSequence);
                
                // Load additional pickup locations with deduplication
                List<LoadLocation> additionalPickups = load.getLocations().stream()
                    .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP && loc.getSequence() > 1)
                    .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                    .collect(Collectors.toList());
                
                // Create a set to track sequences we've already processed
                Set<Integer> processedPickupSequences = new HashSet<>();
                
                for (LoadLocation loc : additionalPickups) {
                    // Skip if we've already processed this sequence number (prevent duplicates)
                    if (processedPickupSequences.contains(loc.getSequence())) {
                        logger.warn("Skipping duplicate pickup sequence: {}", loc.getSequence());
                        continue;
                    }
                    processedPickupSequences.add(loc.getSequence());
                    
                    pickupRowCount[0]++;
                    HBox row = createAdditionalLocationRow("Additional Pickup " + loc.getSequence() + ":",
                                                         loc.getCustomer(), // Use the location's independent customer
                                                         true,
                                                         additionalPickupsContainer);
                    additionalPickupsContainer.getChildren().add(row);

                    // Ensure customer-location relationship is properly established
                    ComboBox<String> locationCombo = (ComboBox<String>) row.getChildren().get(3);
                    DatePicker datePicker = (DatePicker) row.getChildren().get(5);
                    Spinner<LocalTime> timeSpinner = (Spinner<LocalTime>) row.getChildren().get(6);
                    ComboBox<String> customerCombo = (ComboBox<String>) row.getChildren().get(1);
                    
                    // Set location value
                    String locationString = formatLocationAddress(loc.getAddress(), loc.getCity(), loc.getState());
                    Platform.runLater(() -> {
                        locationCombo.setValue(locationString);
                        datePicker.setValue(loc.getDate());
                        timeSpinner.getValueFactory().setValue(loc.getTime());
                        // Double-check customer value is set correctly
                        if (loc.getCustomer() != null && !loc.getCustomer().trim().isEmpty()) {
                            customerCombo.setValue(loc.getCustomer());
                        }
                    });
                }
                
                // Calculate the highest drop sequence number from all existing locations
                // to ensure proper continuation even if some were deleted
                int highestDropSequence = load.getLocations().stream()
                    .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
                    .mapToInt(LoadLocation::getSequence)
                    .max()
                    .orElse(1); // Default to 1 if no additional drops
                maxDropSequence[0] = Math.max(maxDropSequence[0], highestDropSequence);
                
                // Load additional drop locations with deduplication
                List<LoadLocation> additionalDrops = load.getLocations().stream()
                    .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP && loc.getSequence() > 1)
                    .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                    .collect(Collectors.toList());
                
                // Create a set to track sequences we've already processed
                Set<Integer> processedDropSequences = new HashSet<>();
                
                for (LoadLocation loc : additionalDrops) {
                    // Skip if we've already processed this sequence number (prevent duplicates)
                    if (processedDropSequences.contains(loc.getSequence())) {
                        logger.warn("Skipping duplicate drop sequence: {}", loc.getSequence());
                        continue;
                    }
                    processedDropSequences.add(loc.getSequence());
                    
                    dropRowCount[0]++;
                    HBox row = createAdditionalLocationRow("Additional Drop " + loc.getSequence() + ":",
                                                         loc.getCustomer(), // Use the location's independent customer
                                                         false,
                                                         additionalDropsContainer);
                    additionalDropsContainer.getChildren().add(row);

                    // Ensure customer-location relationship is properly established
                    ComboBox<String> locationCombo = (ComboBox<String>) row.getChildren().get(3);
                    DatePicker datePicker = (DatePicker) row.getChildren().get(5);
                    Spinner<LocalTime> timeSpinner = (Spinner<LocalTime>) row.getChildren().get(6);
                    ComboBox<String> customerCombo = (ComboBox<String>) row.getChildren().get(1);
                    
                    // Set location value
                    String locationString = formatLocationAddress(loc.getAddress(), loc.getCity(), loc.getState());
                    Platform.runLater(() -> {
                        locationCombo.setValue(locationString);
                        datePicker.setValue(loc.getDate());
                        timeSpinner.getValueFactory().setValue(loc.getTime());
                        // Double-check customer value is set correctly
                        if (loc.getCustomer() != null && !loc.getCustomer().trim().isEmpty()) {
                            customerCombo.setValue(loc.getCustomer());
                        }
                    });
                }
            }
        }
        
        // Driver & Equipment Section
        VBox driverEquipmentSection = createSection("Driver & Equipment", "");
        
        GridPane driverEquipmentGrid = new GridPane();
        driverEquipmentGrid.getStyleClass().add("modern-form-grid");
        
        // Driver
        Label driverLabel = new Label("Driver");
        driverLabel.getStyleClass().add("modern-form-label");
        dialogFields.driverBox = new ComboBox<>(activeDrivers);
        dialogFields.driverBox.setPromptText("Select driver");
        dialogFields.driverBox.getStyleClass().add("modern-combo-box");
        dialogFields.driverBox.setPrefWidth(200);
        dialogFields.driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setGraphic(null);
                } else {
                    setText(formatDriverDisplay(e));
                }
            }
        });
        dialogFields.driverBox.setButtonCell(dialogFields.driverBox.getCellFactory().call(null));
        
        // Truck field (read-only, auto-populated from driver)
        Label truckLabel = new Label("Truck #");
        truckLabel.getStyleClass().add("modern-form-label");
        TextField truckField = new TextField();
        truckField.setPromptText("Auto-filled");
        truckField.getStyleClass().add("modern-text-field");
        truckField.setPrefWidth(150);
        truckField.setEditable(false);
        truckField.setStyle("-fx-background-color: #f8f9fa;");
        
        // Update truck field and trailer when driver is selected
        dialogFields.driverBox.valueProperty().addListener((obs, oldDriver, newDriver) -> {
            if (newDriver != null) {
                // Update truck field if driver has a truck
                if (newDriver.getTruckUnit() != null) {
                    truckField.setText(newDriver.getTruckUnit());
                } else {
                    truckField.clear();
                }
                
                // Auto-populate trailer based on driver's assigned trailer
                if (newDriver.getTrailerNumber() != null && !newDriver.getTrailerNumber().isEmpty()) {
                    try {
                        Trailer driverTrailer = trailerDAO.findByTrailerNumber(newDriver.getTrailerNumber());
                        if (driverTrailer != null) {
                            dialogFields.trailerBox.setValue(driverTrailer);
                            logger.debug("Auto-populated trailer {} for driver {}", 
                                driverTrailer.getTrailerNumber(), newDriver.getName());
                        }
                    } catch (Exception e) {
                        logger.error("Error finding driver's trailer: {}", e.getMessage(), e);
                    }
                }
            }
        });
        
        // Trailer
        Label trailerLabel = new Label("Trailer");
        trailerLabel.getStyleClass().add("modern-form-label");
        dialogFields.trailerBox = new ComboBox<>(allTrailers);
        dialogFields.trailerBox.setPromptText("Select trailer");
        dialogFields.trailerBox.getStyleClass().add("modern-combo-box");
        dialogFields.trailerBox.setPrefWidth(200);
        dialogFields.trailerBox.setCellFactory(cb -> new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setText("");
                } else {
                    setText(t.getTrailerNumber() + " - " + t.getType());
                }
            }
        });
        dialogFields.trailerBox.setButtonCell(dialogFields.trailerBox.getCellFactory().call(null));
        
        // Add to grid with professional spacing - move Truck # further right
        driverEquipmentGrid.add(driverLabel, 0, 0);
        driverEquipmentGrid.add(dialogFields.driverBox, 1, 0);
        driverEquipmentGrid.add(truckLabel, 4, 0);    // Move further right for professional look
        driverEquipmentGrid.add(truckField, 5, 0);
        
        driverEquipmentGrid.add(trailerLabel, 0, 1);
        driverEquipmentGrid.add(dialogFields.trailerBox, 1, 1, 5, 1);
        
        driverEquipmentSection.getChildren().add(driverEquipmentGrid);
        
        // Schedule & Financial Section
        VBox scheduleFinancialSection = createSection("Schedule & Financial", "");
        
        GridPane scheduleGrid = new GridPane();
        scheduleGrid.getStyleClass().add("modern-form-grid");
        
        // Pickup Date & Time
        Label pickupDateLabel = new Label("Pickup Date");
        pickupDateLabel.getStyleClass().add("modern-form-label");
        dialogFields.pickUpDatePicker = new DatePicker();
        dialogFields.pickUpDatePicker.getStyleClass().add("modern-date-picker");
        dialogFields.pickUpDatePicker.setPrefWidth(150);
        
        Label pickupTimeLabel = new Label("Pickup Time");
        pickupTimeLabel.getStyleClass().add("modern-form-label");
        dialogFields.pickUpTimeSpinner = createTimeSpinner24Hour();
        dialogFields.pickUpTimeSpinner.getStyleClass().add("modern-spinner");
        dialogFields.pickUpTimeSpinner.setPrefWidth(100);
        
        // 12-hour display for pickup time
        TextField pickupTime12Hour = new TextField();
        pickupTime12Hour.getStyleClass().add("time-display-12h");
        pickupTime12Hour.setPrefWidth(100);
        pickupTime12Hour.setEditable(false);
        pickupTime12Hour.setPromptText("12-hour");
        
        // Update 12-hour display when 24-hour time changes
        dialogFields.pickUpTimeSpinner.valueProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime != null) {
                pickupTime12Hour.setText(format12HourTime(newTime));
            }
        });
        
        // Delivery Date & Time
        Label deliveryDateLabel = new Label("Delivery Date");
        deliveryDateLabel.getStyleClass().add("modern-form-label");
        dialogFields.deliveryDatePicker = new DatePicker();
        dialogFields.deliveryDatePicker.getStyleClass().add("modern-date-picker");
        dialogFields.deliveryDatePicker.setPrefWidth(150);
        
        Label deliveryTimeLabel = new Label("Delivery Time");
        deliveryTimeLabel.getStyleClass().add("modern-form-label");
        dialogFields.deliveryTimeSpinner = createTimeSpinner24Hour();
        dialogFields.deliveryTimeSpinner.getStyleClass().add("modern-spinner");
        dialogFields.deliveryTimeSpinner.setPrefWidth(100);
        
        // 12-hour display for delivery time
        TextField deliveryTime12Hour = new TextField();
        deliveryTime12Hour.getStyleClass().add("time-display-12h");
        deliveryTime12Hour.setPrefWidth(100);
        deliveryTime12Hour.setEditable(false);
        deliveryTime12Hour.setPromptText("12-hour");
        
        // Update 12-hour display when 24-hour time changes
        dialogFields.deliveryTimeSpinner.valueProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime != null) {
                deliveryTime12Hour.setText(format12HourTime(newTime));
            }
        });
        
        // Financial Fields
        Label grossLabel = new Label("Gross Amount");
        grossLabel.getStyleClass().add("modern-form-label");
        dialogFields.grossField = new TextField();
        dialogFields.grossField.setPromptText("$0.00");
        dialogFields.grossField.getStyleClass().add("modern-text-field");
        dialogFields.grossField.setPrefWidth(120);
        
        Label statusLabel = new Label("Status");
        statusLabel.getStyleClass().add("modern-form-label");
        dialogFields.statusBox = new ComboBox<>();
        dialogFields.statusBox.getItems().addAll(Load.Status.values());
        dialogFields.statusBox.setValue(Load.Status.BOOKED);
        dialogFields.statusBox.getStyleClass().add("modern-combo-box");
        dialogFields.statusBox.setPrefWidth(150);
        
        // Build the grid layout with professional spacing and 12-hour displays
        // Row 0: Pickup - move time fields further right for better organization
        scheduleGrid.add(pickupDateLabel, 0, 0);
        scheduleGrid.add(dialogFields.pickUpDatePicker, 1, 0);
        scheduleGrid.add(pickupTimeLabel, 4, 0);    // Move further right for professional look
        scheduleGrid.add(dialogFields.pickUpTimeSpinner, 5, 0);
        Label pickup12hLabel = new Label("(12h):");
        pickup12hLabel.getStyleClass().add("modern-form-label");
        scheduleGrid.add(pickup12hLabel, 6, 0);
        scheduleGrid.add(pickupTime12Hour, 7, 0);
        
        // Row 1: Delivery - move time fields further right for better organization
        scheduleGrid.add(deliveryDateLabel, 0, 1);
        scheduleGrid.add(dialogFields.deliveryDatePicker, 1, 1);
        scheduleGrid.add(deliveryTimeLabel, 4, 1);  // Move further right for professional look
        scheduleGrid.add(dialogFields.deliveryTimeSpinner, 5, 1);
        Label delivery12hLabel = new Label("(12h):");
        delivery12hLabel.getStyleClass().add("modern-form-label");
        scheduleGrid.add(delivery12hLabel, 6, 1);
        scheduleGrid.add(deliveryTime12Hour, 7, 1);
        
        // Row 2: Financial - move status further right for better organization
        scheduleGrid.add(grossLabel, 0, 2);
        scheduleGrid.add(dialogFields.grossField, 1, 2);
        scheduleGrid.add(statusLabel, 4, 2);        // Move further right for professional look
        scheduleGrid.add(dialogFields.statusBox, 5, 2, 3, 1);
        
        scheduleFinancialSection.getChildren().add(scheduleGrid);
        
        // Additional Details Section
        VBox additionalDetailsSection = createSection("Additional Details", "");
        
        GridPane detailsGrid = new GridPane();
        detailsGrid.getStyleClass().add("modern-form-grid");
        
        Label notesLabel = new Label("Notes");
        notesLabel.getStyleClass().add("modern-form-label");
        dialogFields.notesField = new TextArea();
        dialogFields.notesField.setPromptText("Enter load notes");
        dialogFields.notesField.getStyleClass().add("modern-text-area");
        dialogFields.notesField.setPrefRowCount(2);
        dialogFields.notesField.setPrefWidth(400);
        dialogFields.notesField.setWrapText(true);
        
        Label reminderLabel = new Label("Reminder");
        reminderLabel.getStyleClass().add("modern-form-label");
        dialogFields.reminderField = new TextField();
        dialogFields.reminderField.setPromptText("Optional reminder");
        dialogFields.reminderField.getStyleClass().add("modern-text-field");
        dialogFields.reminderField.setPrefWidth(400);
        
        dialogFields.hasLumperCheck = new CheckBox("Has Lumper");
        dialogFields.hasLumperCheck.getStyleClass().add("modern-check-box");
        
        // Lumper amount field
        TextField lumperAmountField = new TextField();
        lumperAmountField.setPromptText("$0.00");
        lumperAmountField.getStyleClass().add("lumper-amount-field");
        lumperAmountField.setPrefWidth(120);
        lumperAmountField.setDisable(true); // Initially disabled
        
        // Enable/disable lumper amount field based on checkbox
        dialogFields.hasLumperCheck.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            lumperAmountField.setDisable(!isSelected);
            if (!isSelected) {
                lumperAmountField.clear();
            }
        });
        
        // Store lumper amount field for later access
        dialogFields.lumperAmountField = lumperAmountField;
        
        HBox lumperRow = new HBox();
        lumperRow.getStyleClass().add("checkbox-with-amount");
        lumperRow.getChildren().addAll(dialogFields.hasLumperCheck, lumperAmountField);
        
        dialogFields.hasRevisedRateCheck = new CheckBox("Has Revised Rate");
        dialogFields.hasRevisedRateCheck.getStyleClass().add("modern-check-box");
        
        HBox checkBoxRow = new HBox();
        checkBoxRow.getStyleClass().add("modern-button-row");
        checkBoxRow.getChildren().addAll(lumperRow, dialogFields.hasRevisedRateCheck);
        
        detailsGrid.add(notesLabel, 0, 0);
        detailsGrid.add(dialogFields.notesField, 1, 0, 3, 1);
        detailsGrid.add(reminderLabel, 0, 1);
        detailsGrid.add(dialogFields.reminderField, 1, 1, 3, 1);
        detailsGrid.add(checkBoxRow, 1, 2, 3, 1);
        
        additionalDetailsSection.getChildren().add(detailsGrid);
        
        // Pre-fill if editing
        if (load != null) {
            // Pre-fill basic fields
            dialogFields.loadNumField.setText(load.getLoadNumber());
            dialogFields.poField.setText(load.getPONumber());
            dialogFields.billToBox.setValue(load.getBillTo());
            
            // Pre-fill customer fields
            dialogFields.pickupCustomerBox.setValue(load.getCustomer());
            pickupLocationCombo.setValue(load.getPickUpLocation());
            
            dialogFields.dropCustomerBox.setValue(load.getCustomer2());
            dropLocationCombo.setValue(load.getDropLocation());
            
            // Pre-fill driver and equipment
            dialogFields.driverBox.setValue(load.getDriver());
            dialogFields.trailerBox.setValue(load.getTrailer());
            
            // Pre-fill dates and times
            dialogFields.pickUpDatePicker.setValue(load.getPickUpDate());
            if (load.getPickUpTime() != null) {
                dialogFields.pickUpTimeSpinner.getValueFactory().setValue(load.getPickUpTime());
            }
            dialogFields.deliveryDatePicker.setValue(load.getDeliveryDate());
            if (load.getDeliveryTime() != null) {
                dialogFields.deliveryTimeSpinner.getValueFactory().setValue(load.getDeliveryTime());
            }
            
            // Pre-fill financial and status
            dialogFields.grossField.setText(String.valueOf(load.getGrossAmount()));
            dialogFields.statusBox.setValue(load.getStatus());
            
            // Pre-fill additional details
            dialogFields.notesField.setText(load.getNotes());
            dialogFields.reminderField.setText(load.getReminder());
            dialogFields.hasLumperCheck.setSelected(load.isHasLumper());
            // Pre-fill lumper amount if load has lumper
            if (load.isHasLumper() && load.getLumperAmount() > 0) {
                dialogFields.lumperAmountField.setText(String.format("%.2f", load.getLumperAmount()));
            }
            dialogFields.hasRevisedRateCheck.setSelected(load.isHasRevisedRateConfirmation());
        }
        
        // Add all sections with subtle separators for better visual hierarchy
        content.getChildren().addAll(
            loadInfoSection,
            createSeparator(),
            customerLocationSection,
            createSeparator(),
            driverEquipmentSection,
            createSeparator(),
            scheduleFinancialSection,
            createSeparator(),
            additionalDetailsSection
        );
        
        scrollPane.setContent(content);
        mainContainer.getChildren().add(scrollPane);
        
        // Set dialog content
        dialog.getDialogPane().setContent(mainContainer);
        
        // Configure dialog buttons with better styling
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        
        okButton.setText(isAdd ? "Create Load" : "Save Changes");
        okButton.getStyleClass().clear();
        okButton.getStyleClass().add("modern-button-success");
        
        cancelButton.setText("Cancel");
        cancelButton.getStyleClass().clear();
        cancelButton.getStyleClass().add("modern-button-secondary");
        
        // Style the button bar
        dialog.getDialogPane().lookup(".button-bar").getStyleClass().add("modern-dialog-button-bar");
        
        // Set result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return buildLoadFromDialog(load, isAdd);
            }
            return null;
        });
        
        // Note: Sync manager is disabled for now since we converted from EnhancedLocationFieldOptimized to ComboBox
        // The location ComboBoxes now have their own autocomplete functionality
        /* Platform.runLater(() -> {
            syncManager.attachComponents(
                dialogFields.pickupCustomerBox, 
                pickupLocationCombo,
                dialogFields.dropCustomerBox, 
                dropLocationCombo
            );
        }); */
        
        // Show dialog and handle result
        Optional<Load> result = dialog.showAndWait();
        result.ifPresent(savedLoad -> {
            try {
                if (isAdd) {
                    // Add the load first to get the ID
                    int loadId = loadDAO.add(savedLoad);
                    savedLoad.setId(loadId);
                    
                    // Save additional locations
                    for (LoadLocation location : savedLoad.getLocations()) {
                        location.setLoadId(loadId);
                        loadDAO.addLoadLocation(location);
                    }
                    
                    showInfo("Load created successfully!");
                } else {
                    // Update the load
                    loadDAO.update(savedLoad);
                    
                    // Delete existing additional locations (not primary ones)
                    loadDAO.deleteLoadLocations(savedLoad.getId());
                    
                    // Save new additional locations
                    for (LoadLocation location : savedLoad.getLocations()) {
                        location.setLoadId(savedLoad.getId());
                        loadDAO.addLoadLocation(location);
                    }
                    
                    showInfo("Load updated successfully!");
                }
                reloadAll();
                notifyLoadDataChanged();
            } catch (Exception e) {
                logger.error("Error saving load: {}", e.getMessage(), e);
                showError("Error saving load: " + e.getMessage());
            }
        });
    }
    
    // Tab creation methods removed - using single window approach
    
    // Old tab methods removed - using single window approach
    
    /**
     * Creates the Driver & Equipment tab
     */
    private VBox createDriverEquipmentTab(Load load, boolean isAdd) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
        
        // Driver Section
        VBox driverSection = createSection("Driver Assignment", "Select driver and associated equipment");
        
        dialogFields.driverBox = new ComboBox<>(activeDrivers);
        dialogFields.driverBox.setPromptText("Select driver");
        dialogFields.driverBox.setPrefWidth(300);
        
        // Enhanced driver display
        dialogFields.driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(formatDriverDisplay(e));
                    if (e.getStatus() != Employee.Status.ACTIVE) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        dialogFields.driverBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(formatDriverDisplay(e));
                    if (e.getStatus() != Employee.Status.ACTIVE) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Truck Unit Search
        dialogFields.truckUnitSearchField = new TextField();
        dialogFields.truckUnitSearchField.setPromptText("Search by truck/unit number");
        dialogFields.truckUnitSearchField.setPrefWidth(300);
        
        // Trailer Section
        VBox trailerSection = createSection("Trailer Assignment", "Select trailer for this load");
        
        dialogFields.trailerBox = new ComboBox<>(allTrailers);
        dialogFields.trailerBox.setPromptText("Select trailer");
        dialogFields.trailerBox.setPrefWidth(300);
        
        dialogFields.trailerBox.setCellFactory(cb -> new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        
        dialogFields.trailerBox.setButtonCell(new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        
        dialogFields.trailerSearchField = new TextField();
        dialogFields.trailerSearchField.setPromptText("Search by trailer number");
        dialogFields.trailerSearchField.setPrefWidth(300);
        
        // Link truck search to driver selection
        dialogFields.truckUnitSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                try {
                    Employee found = employeeDAO.getByTruckUnit(newVal.trim());
                    if (found != null) {
                        dialogFields.driverBox.setValue(found);
                    }
                } catch (Exception e) {
                    logger.error("Error searching for truck unit: {}", e.getMessage(), e);
                }
            }
        });
        
        // Update truck search field when driver is selected
        dialogFields.driverBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getTruckUnit() != null) {
                dialogFields.truckUnitSearchField.setText(newVal.getTruckUnit());
            }
        });
        
        // Enhanced trailer search
        dialogFields.trailerSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                if (newVal == null || newVal.trim().isEmpty()) {
                    dialogFields.trailerBox.setItems(allTrailers);
                    return;
                }
                
                String searchText = newVal.toLowerCase().trim();
                ObservableList<Trailer> filteredTrailers = allTrailers.filtered(trailer -> {
                    if (trailer == null) return false;
                    String trailerNumber = trailer.getTrailerNumber() != null ? trailer.getTrailerNumber().toLowerCase() : "";
                    String trailerType = trailer.getType() != null ? trailer.getType().toLowerCase() : "";
                    return trailerNumber.contains(searchText) || trailerType.contains(searchText);
                });
                
                dialogFields.trailerBox.setItems(filteredTrailers);
                
                if (!filteredTrailers.isEmpty()) {
                    Trailer first = filteredTrailers.get(0);
                    if (first.getTrailerNumber() != null && 
                        first.getTrailerNumber().toLowerCase().startsWith(searchText)) {
                        dialogFields.trailerBox.setValue(first);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error in trailer search: {}", e.getMessage(), e);
                dialogFields.trailerBox.setItems(allTrailers);
            }
        });
        
        // Auto-populate trailer from driver's trailer
        dialogFields.driverBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getTrailerNumber() != null && !newVal.getTrailerNumber().isEmpty()) {
                dialogFields.trailerSearchField.setText(newVal.getTrailerNumber());
                try {
                    Trailer driverTrailer = trailerDAO.findByTrailerNumber(newVal.getTrailerNumber());
                    if (driverTrailer != null) {
                        dialogFields.trailerBox.setValue(driverTrailer);
                    }
                } catch (Exception e) {
                    logger.error("Error finding driver's trailer: {}", e.getMessage(), e);
                }
            }
        });
        
        // Pre-fill if editing
        if (load != null) {
            Employee loadedDriver = load.getDriver();
            if (loadedDriver != null) {
                Employee matching = allDrivers.stream()
                    .filter(emp -> emp.getId() == loadedDriver.getId())
                    .findFirst()
                    .orElse(null);
                dialogFields.driverBox.setValue(matching);
                
                if (matching != null && matching.getStatus() != Employee.Status.ACTIVE) {
                    dialogFields.driverBox.setTooltip(new Tooltip("⚠ Warning: Driver is " + matching.getStatus() + 
                        "\nYou may want to reassign this load to an active driver"));
                }
            }
            
            Trailer loadedTrailer = load.getTrailer();
            if (loadedTrailer != null) {
                Trailer matching = allTrailers.stream()
                    .filter(t -> t.getId() == loadedTrailer.getId())
                    .findFirst()
                    .orElse(null);
                dialogFields.trailerBox.setValue(matching);
            } else if (load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty()) {
                dialogFields.trailerSearchField.setText(load.getTrailerNumber());
            }
        }
        
        // Add sections to container
        driverSection.getChildren().addAll(
            createFieldRow("Driver:", dialogFields.driverBox),
            createFieldRow("Truck/Unit Search:", dialogFields.truckUnitSearchField)
        );
        
        trailerSection.getChildren().addAll(
            createFieldRow("Trailer:", dialogFields.trailerBox),
            createFieldRow("Trailer Search:", dialogFields.trailerSearchField)
        );
        
        container.getChildren().addAll(driverSection, trailerSection);
        
        return container;
    }
    
    /**
     * Creates the Schedule & Financial tab
     */
    private VBox createScheduleFinancialTab(Load load, boolean isAdd) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
        
        // Schedule Section
        VBox scheduleSection = createSection("Schedule Information", "Pickup and delivery schedule");
        
        dialogFields.pickUpDatePicker = new DatePicker();
        dialogFields.pickUpDatePicker.setPromptText("Pickup date");
        dialogFields.pickUpDatePicker.setPrefWidth(200);
        
        dialogFields.pickUpTimeSpinner = createTimeSpinner();
        dialogFields.pickUpTimeSpinner.setPrefWidth(150);
        
        dialogFields.deliveryDatePicker = new DatePicker();
        dialogFields.deliveryDatePicker.setPromptText("Delivery date");
        dialogFields.deliveryDatePicker.setPrefWidth(200);
        
        dialogFields.deliveryTimeSpinner = createTimeSpinner();
        dialogFields.deliveryTimeSpinner.setPrefWidth(150);
        
        // Financial Section
        VBox financialSection = createSection("Financial Information", "Load financial details");
        
        dialogFields.grossField = new TextField();
        dialogFields.grossField.setPromptText("Gross amount");
        dialogFields.grossField.setPrefWidth(200);
        
        dialogFields.statusBox = new ComboBox<>(FXCollections.observableArrayList(Load.Status.values()));
        dialogFields.statusBox.setPromptText("Select status");
        dialogFields.statusBox.setPrefWidth(200);
        
        // Pre-fill if editing
        if (load != null) {
            if (load.getPickUpDate() != null) {
                dialogFields.pickUpDatePicker.setValue(load.getPickUpDate());
            }
            if (load.getPickUpTime() != null) {
                dialogFields.pickUpTimeSpinner.getValueFactory().setValue(load.getPickUpTime());
            }
            if (load.getDeliveryDate() != null) {
                dialogFields.deliveryDatePicker.setValue(load.getDeliveryDate());
            }
            if (load.getDeliveryTime() != null) {
                dialogFields.deliveryTimeSpinner.getValueFactory().setValue(load.getDeliveryTime());
            }
            dialogFields.grossField.setText(String.valueOf(load.getGrossAmount()));
            dialogFields.statusBox.setValue(load.getStatus());
        }
        
        // Add sections to container
        scheduleSection.getChildren().addAll(
            createFieldRow("Pickup Date:", dialogFields.pickUpDatePicker),
            createFieldRow("Pickup Time:", dialogFields.pickUpTimeSpinner),
            createFieldRow("Delivery Date:", dialogFields.deliveryDatePicker),
            createFieldRow("Delivery Time:", dialogFields.deliveryTimeSpinner)
        );
        
        financialSection.getChildren().addAll(
            createFieldRow("Gross Amount:", dialogFields.grossField),
            createFieldRow("Status:", dialogFields.statusBox)
        );
        
        container.getChildren().addAll(scheduleSection, financialSection);
        
        return container;
    }
    
    /**
     * Creates the Additional Details tab
     */
    private VBox createAdditionalDetailsTab(Load load, boolean isAdd) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
        
        // Notes Section
        VBox notesSection = createSection("Notes & Reminders", "Additional information and reminders");
        
        dialogFields.notesField = new TextArea();
        dialogFields.notesField.setPromptText("Enter load notes...");
        dialogFields.notesField.setPrefRowCount(4);
        dialogFields.notesField.setPrefWidth(400);
        dialogFields.notesField.setWrapText(true);
        
        dialogFields.reminderField = new TextField();
        dialogFields.reminderField.setPromptText("Reminder notes...");
        dialogFields.reminderField.setPrefWidth(400);
        
        // Options Section
        VBox optionsSection = createSection("Load Options", "Additional load features");
        
        dialogFields.hasLumperCheck = new CheckBox("Has Lumper");
        dialogFields.hasRevisedRateCheck = new CheckBox("Has Revised Rate Confirmation");
        
        // Pre-fill if editing
        if (load != null) {
            dialogFields.notesField.setText(load.getNotes());
            dialogFields.reminderField.setText(load.getReminder());
            dialogFields.hasLumperCheck.setSelected(load.isHasLumper());
            dialogFields.hasRevisedRateCheck.setSelected(load.isHasRevisedRateConfirmation());
        }
        
        // Add sections to container
        notesSection.getChildren().addAll(
            createFieldRow("Notes:", dialogFields.notesField),
            createFieldRow("Reminder:", dialogFields.reminderField)
        );
        
        optionsSection.getChildren().addAll(
            createFieldRow("", dialogFields.hasLumperCheck),
            createFieldRow("", dialogFields.hasRevisedRateCheck)
        );
        
        container.getChildren().addAll(notesSection, optionsSection);
        
        return container;
    }
    
    /**
     * Creates a subtle separator for visual hierarchy
     */
    private Separator createSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("modern-separator");
        return separator;
    }
    
    /**
     * Creates a section with title and description
     */
    private VBox createSection(String title, String description) {
        VBox section = new VBox();
        section.getStyleClass().add("modern-section");
        section.setFillWidth(true);
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modern-section-title");
        
        Label descLabel = new Label(description);
        descLabel.setStyle(
            "-fx-font-size: 13px; " +
            "-fx-text-fill: #6b7280; " +
            "-fx-font-family: 'Segoe UI', sans-serif;"
        );
        
        // Modern separator
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #e8eaed; -fx-border-color: #e8eaed;");
        separator.setPadding(new Insets(5, 0, 5, 0));
        
        section.getChildren().addAll(titleLabel, descLabel, separator);
        return section;
    }
    
    /**
     * Creates a field row with label and control
     */
    private HBox createFieldRow(String labelText, Node control) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 8, 0));
        
        if (!labelText.isEmpty()) {
            Label label = new Label(labelText);
            label.setPrefWidth(150);
            label.setMinWidth(150);
            label.setMaxWidth(150);
            label.setStyle(
                "-fx-font-weight: 600; " +
                "-fx-text-fill: #1a1a1a; " +
                "-fx-font-size: 14px; " +
                "-fx-font-family: 'Segoe UI', sans-serif;"
            );
            row.getChildren().add(label);
        }
        
        // Ensure control has proper styling
        if (control instanceof ComboBox) {
            control.setStyle(control.getStyle() + 
                "; -fx-pref-width: 300px; -fx-min-width: 250px; -fx-max-width: 400px;");
        } else if (control instanceof TextField) {
            control.setStyle(control.getStyle() + 
                "; -fx-pref-width: 300px; -fx-min-width: 250px; -fx-max-width: 400px;");
        }
        
        row.getChildren().add(control);
        HBox.setHgrow(control, Priority.SOMETIMES);
        
        return row;
    }
    
    /**
     * Creates validation area
     */
    private VBox createValidationArea() {
        VBox validationArea = new VBox(10);
        validationArea.setPadding(new Insets(15));
        validationArea.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 5; -fx-border-color: #ffeaa7; -fx-border-radius: 5;");
        
        Label validationTitle = new Label("Validation Status");
        validationTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #856404;");
        
        Label validationText = new Label("Please complete all required fields marked with *");
        validationText.setStyle("-fx-text-fill: #856404;");
        
        validationArea.getChildren().addAll(validationTitle, validationText);
        validationArea.setVisible(false);
        validationArea.setManaged(false);
        
        return validationArea;
    }

    /**
     * Builds a Load object from the dialog fields
     */
    private Load buildLoadFromDialog(Load originalLoad, boolean isAdd) {
        try {
            // Enhanced validation for manual load number input
            String loadNumber = dialogFields.loadNumField.getText();
            if (loadNumber == null || loadNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Load number is required. Please enter a unique load number (e.g., L-2024-001).");
            }
            
            loadNumber = loadNumber.trim();
            
            // Validate load number format (optional but recommended)
            if (!loadNumber.matches("^[A-Z]-\\d{4}-\\d{3}$") && !loadNumber.matches("^[A-Z]\\d+$")) {
                // Show warning but don't prevent saving - just log it
                logger.warn("Load number '{}' doesn't follow recommended format (L-YYYY-NNN)", loadNumber);
            }
            
            // Check for duplicate load number
            if (isAdd && checkDuplicateLoadNumber(loadNumber, -1)) {
                throw new IllegalArgumentException("Load number '" + loadNumber + "' already exists. Please choose a different load number.");
            }
            
            // Create or update load
            Load load = isAdd ? new Load(0, "", "", "", "", "", "", "", null, "", Load.Status.BOOKED, 0.0, "", null, null, null, null, "", false, 0.0, false) : originalLoad;
            
            // Set basic information
            load.setLoadNumber(loadNumber);
            load.setPONumber(dialogFields.poField.getText() != null ? dialogFields.poField.getText().trim() : "");
            load.setBillTo(dialogFields.billToBox.getValue());
            
            // Set customer information
            load.setCustomer(dialogFields.pickupCustomerBox.getValue());
            load.setCustomer2(dialogFields.dropCustomerBox.getValue());
            
            // Set legacy location information (for backward compatibility)
            if (dialogFields.pickupLocationBox != null) {
                load.setPickUpLocation(dialogFields.pickupLocationBox.getValue());
            }
            if (dialogFields.dropLocationBox != null) {
                load.setDropLocation(dialogFields.dropLocationBox.getValue());
            }
            
            // Set enhanced multiple location information
            load.clearLocations(); // Clear existing locations
            
            // NOTE: Primary pickup and drop locations are stored in the Load object's 
            // pickUpLocation and dropLocation fields. We should NOT create LoadLocation 
            // objects for them to avoid duplication in the PDF.
            
            // Add ONLY additional pickup locations (sequence starts at 2)
            int pickupSequence = 2;
            for (int i = 0; i < dialogFields.additionalPickupLocations.size(); i++) {
                ComboBox<String> locationBox = dialogFields.additionalPickupLocations.get(i);
                DatePicker datePicker = dialogFields.additionalPickupDates.get(i);
                Spinner<LocalTime> timeSpinner = dialogFields.additionalPickupTimes.get(i);
                if (locationBox.getValue() != null && !locationBox.getValue().trim().isEmpty()) {
                    // Parse address components
                    String[] parts = parseAddressParts(locationBox.getValue());
                    
                    // Get the customer from the associated customer combo box - ROBUST APPROACH
                    String customerName = null; // Start with null, no default
                    ComboBox<String> customerCombo = null;
                    
                    // Try primary method (getUserData)
                    if (locationBox.getUserData() instanceof ComboBox) {
                        customerCombo = (ComboBox<String>) locationBox.getUserData();
                    }
                    // Try backup method (properties)
                    else if (locationBox.getProperties().containsKey("customerCombo")) {
                        Object customerComboObj = locationBox.getProperties().get("customerCombo");
                        if (customerComboObj instanceof ComboBox) {
                            customerCombo = (ComboBox<String>) customerComboObj;
                        }
                    }
                    
                    if (customerCombo != null && customerCombo.getValue() != null && !customerCombo.getValue().trim().isEmpty()) {
                        customerName = customerCombo.getValue();
                    }
                    
                    // Extract sequence number from parent row if possible
                    int sequence = pickupSequence++;
                    if (locationBox.getParent() instanceof HBox) {
                        HBox row = (HBox) locationBox.getParent();
                        if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label) {
                            Label label = (Label) row.getChildren().get(0);
                            String labelText = label.getText();
                            // Extract number from "Additional Pickup X:"
                            String[] labelParts = labelText.split(" ");
                            if (labelParts.length >= 3) {
                                try {
                                    int extractedSeq = Integer.parseInt(labelParts[2].replace(":", ""));
                                    sequence = extractedSeq;
                                } catch (NumberFormatException e) {
                                    // Use default sequence if extraction fails
                                }
                            }
                        }
                    }
                    
                    // Create and add the additional pickup location
                    LoadLocation additionalPickup = new LoadLocation(
                        0, load.getId(), LoadLocation.LocationType.PICKUP,
                        customerName,
                        parts[0], parts[1], parts[2],
                        datePicker.getValue(),
                        timeSpinner.getValue(),
                        "", sequence
                    );
                    load.addLocation(additionalPickup);
                }
            }
            
            // Add ONLY additional drop locations (sequence starts at 2)
            int dropSequence = 2;
            for (int i = 0; i < dialogFields.additionalDropLocations.size(); i++) {
                ComboBox<String> locationBox = dialogFields.additionalDropLocations.get(i);
                DatePicker datePicker = dialogFields.additionalDropDates.get(i);
                Spinner<LocalTime> timeSpinner = dialogFields.additionalDropTimes.get(i);
                if (locationBox.getValue() != null && !locationBox.getValue().trim().isEmpty()) {
                    // Parse address components
                    String[] parts = parseAddressParts(locationBox.getValue());
                    
                    // Get the customer from the associated customer combo box - ROBUST APPROACH
                    String customerName = null; // Start with null, no default
                    ComboBox<String> customerCombo = null;
                    
                    // Try primary method (getUserData)
                    if (locationBox.getUserData() instanceof ComboBox) {
                        customerCombo = (ComboBox<String>) locationBox.getUserData();
                    }
                    // Try backup method (properties)
                    else if (locationBox.getProperties().containsKey("customerCombo")) {
                        Object customerComboObj = locationBox.getProperties().get("customerCombo");
                        if (customerComboObj instanceof ComboBox) {
                            customerCombo = (ComboBox<String>) customerComboObj;
                        }
                    }
                    
                    if (customerCombo != null && customerCombo.getValue() != null && !customerCombo.getValue().trim().isEmpty()) {
                        customerName = customerCombo.getValue();
                    }
                    
                    // Extract sequence number from parent row if possible
                    int sequence = dropSequence++;
                    if (locationBox.getParent() instanceof HBox) {
                        HBox row = (HBox) locationBox.getParent();
                        if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label) {
                            Label label = (Label) row.getChildren().get(0);
                            String labelText = label.getText();
                            // Extract number from "Additional Drop X:"
                            String[] labelParts = labelText.split(" ");
                            if (labelParts.length >= 3) {
                                try {
                                    int extractedSeq = Integer.parseInt(labelParts[2].replace(":", ""));
                                    sequence = extractedSeq;
                                } catch (NumberFormatException e) {
                                    // Use default sequence if extraction fails
                                }
                            }
                        }
                    }
                    
                    // Create and add the additional drop location
                    LoadLocation additionalDrop = new LoadLocation(
                        0, load.getId(), LoadLocation.LocationType.DROP,
                        customerName,
                        parts[0], parts[1], parts[2],
                        datePicker.getValue(),
                        timeSpinner.getValue(),
                        "", sequence
                    );
                    load.addLocation(additionalDrop);
                }
            }
            
            // Set driver and trailer
            load.setDriver(dialogFields.driverBox.getValue());
            load.setTrailer(dialogFields.trailerBox.getValue());
            
            // Set schedule
            load.setPickUpDate(dialogFields.pickUpDatePicker.getValue());
            load.setPickUpTime(dialogFields.pickUpTimeSpinner.getValue());
            load.setDeliveryDate(dialogFields.deliveryDatePicker.getValue());
            load.setDeliveryTime(dialogFields.deliveryTimeSpinner.getValue());
            
            // Set financial information
            if (dialogFields.grossField.getText() != null && !dialogFields.grossField.getText().trim().isEmpty()) {
                try {
                    load.setGrossAmount(Double.parseDouble(dialogFields.grossField.getText().trim()));
                } catch (NumberFormatException e) {
                    load.setGrossAmount(0.0);
                }
            } else {
                load.setGrossAmount(0.0);
            }
            
            load.setStatus(dialogFields.statusBox.getValue() != null ? dialogFields.statusBox.getValue() : Load.Status.BOOKED);
            
            // Set additional details
            load.setNotes(dialogFields.notesField.getText() != null ? dialogFields.notesField.getText().trim() : "");
            load.setReminder(dialogFields.reminderField.getText() != null ? dialogFields.reminderField.getText().trim() : "");
            load.setHasLumper(dialogFields.hasLumperCheck.isSelected());
            
            // Process lumper amount
            if (dialogFields.hasLumperCheck.isSelected()) {
                String lumperAmountText = dialogFields.lumperAmountField.getText();
                if (lumperAmountText != null && !lumperAmountText.trim().isEmpty()) {
                    try {
                        // Remove any currency symbols and parse
                        String cleanAmount = lumperAmountText.replaceAll("[^0-9.]", "");
                        if (!cleanAmount.isEmpty()) {
                            load.setLumperAmount(Double.parseDouble(cleanAmount));
                        } else {
                            load.setLumperAmount(0.0);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid lumper amount format: '{}', setting to 0.0", lumperAmountText);
                        load.setLumperAmount(0.0);
                    }
                } else {
                    load.setLumperAmount(0.0);
                }
            } else {
                load.setLumperAmount(0.0);
            }
            
            load.setHasRevisedRateConfirmation(dialogFields.hasRevisedRateCheck.isSelected());
            
            // Save any manually entered addresses
            // With the optimized implementation, addresses are saved on-demand
            // No need to pre-save them here as they'll be created when used
            
            return load;
            
        } catch (Exception e) {
            logger.error("Error building load from dialog: {}", e.getMessage(), e);
            throw new RuntimeException("Error building load: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to find nodes by ID
     */
    private Node findNodeById(String id) {
        // This is a simplified implementation - in a real application,
        // you would maintain references to the dialog fields
        return null; // Placeholder - would need to be implemented with proper field tracking
    }

    private boolean checkDuplicateLoadNumber(String loadNum, int excludeId) {
        String norm = loadNum.trim().toLowerCase(Locale.ROOT);
        for (Load l : allLoads) {
            if (l.getId() != excludeId && l.getLoadNumber() != null &&
                l.getLoadNumber().trim().toLowerCase(Locale.ROOT).equals(norm)) {
                logger.debug("Duplicate load number found: {}", loadNum);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse address into components (street, city, state)
     * Expects format like "123 Main St, Chicago, IL" or just "123 Main St, Chicago"
     * @param address Full address string
     * @return Array with [street, city, state]
     */
    private String[] parseAddressParts(String address) {
        String[] result = new String[] {"", "", ""};
        
        if (address == null || address.trim().isEmpty()) {
            return result;
        }
        
        // Try to parse the address
        try {
            String cleaned = address.trim();
            
            // Split by commas
            String[] parts = cleaned.split(",");
            
            // Street is always the first part
            if (parts.length > 0) {
                result[0] = parts[0].trim();
            }
            
            // City is the second part if available
            if (parts.length > 1) {
                result[1] = parts[1].trim();
            }
            
            // State is the third part if available, or we try to extract from city
            if (parts.length > 2) {
                result[2] = parts[2].trim();
            } else if (parts.length == 2) {
                // Try to extract state from city (e.g. "Chicago IL")
                String[] cityParts = parts[1].trim().split("\\s+");
                if (cityParts.length > 1) {
                    String lastPart = cityParts[cityParts.length - 1];
                    // If the last part looks like a state code (2 letters)
                    if (lastPart.length() == 2 && lastPart.equals(lastPart.toUpperCase())) {
                        result[2] = lastPart;
                        
                        // Rebuild city without the state part
                        StringBuilder cityBuilder = new StringBuilder();
                        for (int i = 0; i < cityParts.length - 1; i++) {
                            if (i > 0) cityBuilder.append(" ");
                            cityBuilder.append(cityParts[i]);
                        }
                        result[1] = cityBuilder.toString().trim();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error parsing address: {}", address, e);
        }
        
        return result;
    }

    private void reloadAll() {
        logger.debug("Reloading all data");
        allLoads.setAll(loadDAO.getAll());
        allDrivers.setAll(employeeDAO.getAll());
        allTrailers.setAll(trailerDAO.findAll());
        allCustomers.setAll(loadDAO.getAllCustomers());
        allBillingEntities.setAll(loadDAO.getAllBillingEntities());
        
        // No cache refresh needed with optimized on-demand implementation
        
        for (StatusTab tab : statusTabs) {
            if (tab.filteredList != null)
                tab.filteredList.setPredicate(tab.filteredList.getPredicate());
        }
        logger.info("Data reload complete - Loads: {}, Drivers: {}, Trailers: {}, Customers: {}, Billing Entities: {}", 
            allLoads.size(), allDrivers.size(), allTrailers.size(), allCustomers.size(), allBillingEntities.size());
    }

    /**
     * Call this method from LoadsTab when employee list changes.
     */
    public void onEmployeeDataChanged(List<Employee> currentList) {
        logger.debug("Employee data changed, updating drivers list with {} employees", currentList.size());
        allDrivers.setAll(currentList);
    }
    
    /**
     * Call this method when trailer list changes.
     */
    public void onTrailerDataChanged(List<Trailer> currentList) {
        logger.debug("Trailer data changed, updating trailers list with {} trailers", currentList.size());
        allTrailers.setAll(currentList);
    }

    private boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; }
        catch (Exception e) { return false; }
    }
    
    /**
     * Extracts city and state from a full address string.
     * Assumes format like "City, State" or "Street Address, City, State, ZIP"
     * Returns only "City, State" portion.
     */
    private String extractCityState(String fullLocation) {
        if (fullLocation == null || fullLocation.trim().isEmpty()) {
            return "";
        }
        
        String location = fullLocation.trim();
        
        // Split by commas
        String[] parts = location.split(",");
        
        if (parts.length >= 2) {
            // Look for state pattern (2-3 letter abbreviation or full state name)
            // Common patterns:
            // "Street, City, State"
            // "Street, City, State ZIP"
            // "City, State"
            // "City, State ZIP"
            
            for (int i = parts.length - 1; i >= 1; i--) {
                String part = parts[i].trim();
                
                // Check if this looks like a state (2-3 letters, or common state names)
                if (isLikelyState(part)) {
                    String city = parts[i - 1].trim();
                    return city + ", " + part;
                }
            }
            
            // If no clear state found, return last two parts
            if (parts.length >= 2) {
                String city = parts[parts.length - 2].trim();
                String state = parts[parts.length - 1].trim();
                return city + ", " + state;
            }
        }
        
        // If only one part or can't parse, return as-is
        return location;
    }
    
    /**
     * Checks if a string looks like a US state abbreviation or name
     */
    private boolean isLikelyState(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = text.trim();
        
        // Remove ZIP code if present (5 digits or 5+4 format)
        if (trimmed.matches(".*\\b\\d{5}(-\\d{4})?\\b.*")) {
            trimmed = trimmed.replaceAll("\\b\\d{5}(-\\d{4})?\\b", "").trim();
        }
        
        // Check for 2-letter state abbreviations
        if (trimmed.length() == 2 && trimmed.matches("[A-Z]{2}")) {
            return true;
        }
        
        // Check for common state names (case insensitive)
        String lowerTrimmed = trimmed.toLowerCase();
        String[] commonStates = {
            "alabama", "alaska", "arizona", "arkansas", "california", "colorado", "connecticut",
            "delaware", "florida", "georgia", "hawaii", "idaho", "illinois", "indiana", "iowa",
            "kansas", "kentucky", "louisiana", "maine", "maryland", "massachusetts", "michigan",
            "minnesota", "mississippi", "missouri", "montana", "nebraska", "nevada", "new hampshire",
            "new jersey", "new mexico", "new york", "north carolina", "north dakota", "ohio",
            "oklahoma", "oregon", "pennsylvania", "rhode island", "south carolina", "south dakota",
            "tennessee", "texas", "utah", "vermont", "virginia", "washington", "west virginia",
            "wisconsin", "wyoming"
        };
        
        for (String state : commonStates) {
            if (lowerTrimmed.equals(state)) {
                return true;
            }
        }
        
        return false;
    }

    private void exportCSV(TableView<Load> table) {
        logger.info("Exporting loads to CSV");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Loads to CSV");
        fileChooser.setInitialFileName("loads-export.csv");
        File file = fileChooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) {
            logger.info("CSV export cancelled");
            return;
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            String header = table.getColumns().stream().map(TableColumn::getText).collect(Collectors.joining(","));
            bw.write(header); bw.newLine();
            int count = 0;
            for (Load l : table.getItems()) {
                String row = String.join(",",
                        safe(l.getLoadNumber()),
                        safe(l.getPONumber()),
                        safe(l.getCustomer()),
                        safe(l.getPickUpLocation()),
                        safe(l.getDropLocation()),
                        safe(l.getDriver() != null ? l.getDriver().getName() : ""),
                        safe(l.getTruckUnitSnapshot()),
                        safe(l.getTrailerNumber()),
                        safe(l.getStatus().toString()),
                        String.valueOf(l.getGrossAmount()),
                        safe(l.getReminder()),
                        safe(l.getPickUpDate() != null ? l.getPickUpDate().toString() : ""),
                        safe(l.getPickUpTime() != null ? l.getPickUpTime().format(DateTimeFormatter.ofPattern("h:mm a")) : ""),
                        safe(l.getDeliveryDate() != null ? l.getDeliveryDate().toString() : ""),
                        safe(l.getDeliveryTime() != null ? l.getDeliveryTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "")
                );
                bw.write(row); bw.newLine();
                count++;
            }
            logger.info("Successfully exported {} loads to CSV: {}", count, file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to export CSV: {}", e.getMessage(), e);
            new Alert(Alert.AlertType.ERROR, "Failed to write file: " + e.getMessage()).showAndWait();
        }
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace(",", " ");
    }

    private void showError(String msg) {
        logger.error("Showing error dialog: {}", msg);
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        // Don't show dialogs during jpackage testing
        if ("true".equals(System.getProperty("jpackage.testing"))) {
            logger.info("Suppressing dialog during jpackage testing: {}", msg);
            return;
        }
        
        logger.info("Showing info dialog: {}", msg);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText("Info");
        alert.showAndWait();
    }
    
    private void showAddressSyncDialog() {
        logger.info("Starting address sync with progress dialog");
        
        // Create progress dialog
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Syncing Addresses");
        progressDialog.setHeaderText("Synchronizing customer addresses with location dropdowns...");
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        
        // Progress components
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        Label statusLabel = new Label("Preparing sync...");
        Label countLabel = new Label("");
        
        VBox content = new VBox(10);
        content.getChildren().addAll(statusLabel, progressBar, countLabel);
        content.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(content);
        
        // Create background task
        Task<Void> syncTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                loadDAO.syncAllAddressesToCustomerLocations(progress -> {
                    Platform.runLater(() -> {
                        double percentComplete = progress.total > 0 ? (double) progress.current / progress.total : 0.0;
                        progressBar.setProgress(percentComplete);
                        statusLabel.setText(progress.message);
                        countLabel.setText(String.format("Processed %d of %d addresses", 
                            progress.current, progress.total));
                    });
                });
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    progressDialog.close();
                    try {
                        // Refresh the customer locations in dropdowns
                        reloadAll();
                        
                        // Show success dialog
                        Dialog<Void> successDialog = new Dialog<>();
                        successDialog.setTitle("Sync Complete");
                        successDialog.setHeaderText("Address Synchronization Successful");
                        
                        // Get the final sync message from the last progress update
                        String finalMessage = statusLabel.getText();
                        Label messageLabel = new Label(finalMessage);
                        messageLabel.setWrapText(true);
                        
                        VBox content = new VBox(10);
                        content.setPadding(new Insets(20));
                        content.getChildren().add(messageLabel);
                        
                        successDialog.getDialogPane().setContent(content);
                        successDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                        successDialog.showAndWait();
                        
                    } catch (Exception e) {
                        logger.error("Error refreshing after sync", e);
                        showError("Sync completed but failed to refresh: " + e.getMessage());
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressDialog.close();
                    Throwable exception = getException();
                    logger.error("Address sync failed", exception);
                    showError("Address sync failed: " + (exception != null ? exception.getMessage() : "Unknown error"));
                });
            }
            
            @Override
            protected void cancelled() {
                Platform.runLater(() -> {
                    progressDialog.close();
                    showInfo("Address sync was cancelled.");
                });
            }
        };
        
        // Handle cancel button
        Button cancelButton = (Button) progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setOnAction(e -> {
            syncTask.cancel();
            progressDialog.close();
        });
        
        // Start the task in background thread
        Thread syncThread = new Thread(syncTask);
        syncThread.setDaemon(true);
        syncThread.start();
        
        // Show dialog
        progressDialog.showAndWait();
    }
    
    private void showLocationsDialog(Load load) {
        logger.debug("Showing locations dialog for load: {}", load.getLoadNumber());
        
        LoadLocationsDialog locationsDialog = new LoadLocationsDialog(load, loadDAO);
        locationsDialog.showAndWait().ifPresent(locations -> {
            // Update the load with new locations
            load.clearLocations();
            for (LoadLocation location : locations) {
                load.addLocation(location);
            }
            
            // Save locations to database
            try {
                // Delete existing locations
                loadDAO.deleteLoadLocations(load.getId());
                
                // Add new locations
                for (LoadLocation location : locations) {
                    location.setLoadId(load.getId());
                    loadDAO.addLoadLocation(location);
                }
                
                // Update the load in database
                loadDAO.update(load);
                
                showInfo("Locations updated successfully!");
                reloadAll();
                // Force refresh on all tables to update cell styles
                for (StatusTab tab : statusTabs) {
                    if (tab.table != null) tab.table.refresh();
                }
                notifyLoadDataChanged();
            } catch (Exception e) {
                logger.error("Error saving locations: {}", e.getMessage(), e);
                showError("Error saving locations: " + e.getMessage());
            }
        });
    }

    /**
     * Professional autocomplete for ComboBox fields with full sync to customer settings logic
     */
    private void setupSimpleAutocomplete(ComboBox<String> comboBox, ObservableList<String> allItems) {
        // Apply professional styling with black text
        comboBox.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
                         "-fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;");
        
        // Enhanced autocomplete with debouncing for better performance and UX
        final Timer[] debounceTimer = {null};
        
        comboBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                // Cancel previous timer for debouncing
                if (debounceTimer[0] != null) {
                    debounceTimer[0].cancel();
                }
                
                if (newVal == null || newVal.trim().isEmpty()) {
                    comboBox.setItems(allItems);
                    comboBox.hide();
                    return;
                }
                
                // Debounce to improve performance and UX
                debounceTimer[0] = new Timer();
                debounceTimer[0].schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            try {
                                String searchText = newVal.toLowerCase().trim();
                                
                                // Enhanced filtering logic to match customer settings behavior exactly
                                ObservableList<String> filteredItems = allItems.filtered(item -> {
                                    if (item == null || item.trim().isEmpty()) return false;
                                    
                                    String itemLower = item.toLowerCase().trim();
                                    
                                    // Multi-criteria matching for robust search - same as customer settings
                                    return itemLower.equals(searchText) ||         // Exact match
                                           itemLower.startsWith(searchText) ||     // Prefix match
                                           itemLower.contains(searchText) ||       // Contains match
                                           itemLower.replace(" ", "").contains(searchText.replace(" ", "")); // No-space match
                                });
                                
                                // Advanced sorting for professional user experience
                                List<String> sortedItems = new ArrayList<>(filteredItems);
                                sortedItems.sort((a, b) -> {
                                    String aLower = a.toLowerCase().trim();
                                    String bLower = b.toLowerCase().trim();
                                    
                                    // Priority 1: Exact match (highest)
                                    boolean aExact = aLower.equals(searchText);
                                    boolean bExact = bLower.equals(searchText);
                                    if (aExact && !bExact) return -1;
                                    if (!aExact && bExact) return 1;
                                    
                                    // Priority 2: Starts with match
                                    boolean aStarts = aLower.startsWith(searchText);
                                    boolean bStarts = bLower.startsWith(searchText);
                                    if (aStarts && !bStarts) return -1;
                                    if (!aStarts && bStarts) return 1;
                                    
                                    // Priority 3: Shorter names for same match type
                                    if (aStarts && bStarts) {
                                        return Integer.compare(aLower.length(), bLower.length());
                                    }
                                    
                                    // Priority 4: Contains match
                                    boolean aContains = aLower.contains(searchText);
                                    boolean bContains = bLower.contains(searchText);
                                    if (aContains && !bContains) return -1;
                                    if (!aContains && bContains) return 1;
                                    
                                    // Final: Alphabetical and length-based sorting
                                    int lengthCompare = Integer.compare(aLower.length(), bLower.length());
                                    return lengthCompare != 0 ? lengthCompare : aLower.compareTo(bLower);
                                });
                                
                                // Limit results for better performance
                                if (sortedItems.size() > 25) {
                                    sortedItems = sortedItems.subList(0, 25);
                                }
                                
                                comboBox.setItems(FXCollections.observableArrayList(sortedItems));
                                
                                // Show dropdown if we have results and the field is focused
                                if (!sortedItems.isEmpty() && comboBox.isFocused()) {
                                    if (!comboBox.isShowing()) {
                                        comboBox.show();
                                    }
                                } else {
                                    comboBox.hide();
                                }
                                
                            } catch (Exception e) {
                                logger.error("Error in autocomplete filtering: {}", e.getMessage(), e);
                                comboBox.setItems(allItems);
                            }
                        });
                    }
                }, 150); // 150ms debounce for optimal UX
                
            } catch (Exception e) {
                logger.error("Error setting up autocomplete timer: {}", e.getMessage(), e);
                comboBox.setItems(allItems);
            }
        });
        
        // Enhanced customer selection handler with normalization and async processing
        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                try {
                    // Normalize customer name for consistency across the application
                    String normalizedCustomer = newVal.trim().toUpperCase();
                    
                    // Process customer save asynchronously to avoid UI blocking
                    CompletableFuture.runAsync(() -> {
                        try {
                            loadDAO.addCustomerIfNotExists(normalizedCustomer);
                            
                            // Refresh customer list to maintain full sync with customer settings
                            List<String> updatedCustomers = loadDAO.getAllCustomers();
                            Platform.runLater(() -> {
                                allCustomers.setAll(updatedCustomers);
                                // Ensure the new customer is in the list
                                if (!updatedCustomers.contains(normalizedCustomer)) {
                                    allCustomers.add(normalizedCustomer);
                                }
                                logger.debug("Customer saved and list refreshed: {}", normalizedCustomer);
                            });
                            
                        } catch (Exception e) {
                            logger.error("Error saving customer: {}", normalizedCustomer, e);
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Customer Save Warning");
                                alert.setHeaderText(null);
                                alert.setContentText("Warning: Could not save customer '" + normalizedCustomer + "': " + e.getMessage());
                                alert.getDialogPane().setStyle("-fx-background-color: white; -fx-text-fill: black;");
                                alert.showAndWait();
                            });
                        }
                    });
                    
                } catch (Exception e) {
                    logger.error("Error in customer selection: {}", e.getMessage(), e);
                }
            }
        });
    }
    
    /**
     * Creates an enhanced customer field with robust address dropdown integration
     */
    private HBox createEnhancedCustomerField(String promptText, boolean isPickupCustomer) {
        HBox container = new HBox(5);
        container.setAlignment(Pos.CENTER_LEFT);
        
        // Customer ComboBox with enhanced autocomplete and professional styling
        ComboBox<String> customerBox = new ComboBox<>(allCustomers);
        customerBox.setEditable(true);
        customerBox.setPromptText(promptText);
        customerBox.setPrefWidth(280);
        customerBox.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
                           "-fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;");
        
        // Enhanced autocomplete for customer
        setupSimpleAutocomplete(customerBox, allCustomers);
        
        // Create the new enhanced address autocomplete field
        AddressAutocompleteField addressAutocomplete = new AddressAutocompleteField(isPickupCustomer);
        addressAutocomplete.setVisible(false);
        addressAutocomplete.setManaged(false);
        
        // Set up the address provider for the autocomplete field
        addressAutocomplete.setAddressProvider((customer, query) -> 
            CompletableFuture.supplyAsync(() -> {
                try {
                    List<CustomerAddress> allAddresses = loadDAO.getCustomerAddressBook(customer);
                    // Return all addresses - the autocomplete field will handle filtering and ranking
                    return allAddresses;
                } catch (Exception e) {
                    logger.error("Error getting customer addresses", e);
                    return new ArrayList<>();
                }
            })
        );
        
        // For backward compatibility, create a hidden ComboBox that mirrors the autocomplete selection
        ComboBox<CustomerAddress> addressBox = new ComboBox<>();
        addressBox.setPromptText("Select address");
        addressBox.setPrefWidth(300);
        addressBox.setVisible(false);
        addressBox.setManaged(false);
        
        // Custom cell factory for address display with enhanced styling
        addressBox.setCellFactory(cb -> new ListCell<CustomerAddress>() {
            @Override
            protected void updateItem(CustomerAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    StringBuilder displayText = new StringBuilder();
                    
                    // Add location name if available
                    if (item.getLocationName() != null && !item.getLocationName().trim().isEmpty()) {
                        displayText.append(item.getLocationName()).append(": ");
                    }
                    
                    // Add address components
                    if (item.getAddress() != null && !item.getAddress().trim().isEmpty()) {
                        displayText.append(item.getAddress());
                    }
                    
                    if (item.getCity() != null && !item.getCity().trim().isEmpty()) {
                        if (displayText.length() > 0) displayText.append(", ");
                        displayText.append(item.getCity());
                    }
                    
                    if (item.getState() != null && !item.getState().trim().isEmpty()) {
                        if (displayText.length() > 0) displayText.append(", ");
                        displayText.append(item.getState());
                    }
                    
                    setText(displayText.toString());
                    
                    // Professional styling for default addresses with black text
                    if (isPickupCustomer && item.isDefaultPickup()) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: #E8F5E8; " +
                               "-fx-border-color: #28a745; -fx-border-width: 0 0 0 3; -fx-padding: 5px;");
                    } else if (!isPickupCustomer && item.isDefaultDrop()) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: #E8F5E8; " +
                               "-fx-border-color: #28a745; -fx-border-width: 0 0 0 3; -fx-padding: 5px;");
                    } else {
                        setStyle("-fx-text-fill: black; -fx-padding: 5px;");
                    }
                }
            }
        });
        
        addressBox.setButtonCell(new ListCell<CustomerAddress>() {
            @Override
            protected void updateItem(CustomerAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    StringBuilder displayText = new StringBuilder();
                    
                    if (item.getLocationName() != null && !item.getLocationName().trim().isEmpty()) {
                        displayText.append(item.getLocationName()).append(": ");
                    }
                    
                    if (item.getAddress() != null && !item.getAddress().trim().isEmpty()) {
                        displayText.append(item.getAddress());
                    }
                    
                    if (item.getCity() != null && !item.getCity().trim().isEmpty()) {
                        if (displayText.length() > 0) displayText.append(", ");
                        displayText.append(item.getCity());
                    }
                    
                    if (item.getState() != null && !item.getState().trim().isEmpty()) {
                        if (displayText.length() > 0) displayText.append(", ");
                        displayText.append(item.getState());
                    }
                    
                    setText(displayText.toString());
                }
            }
        });
        
        // Manual address entry button with professional styling
        Button manualAddressBtn = new Button("📍");
        manualAddressBtn.setTooltip(new Tooltip("Add new address for this customer"));
        manualAddressBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-font-weight: bold; " +
                                "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; " +
                                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 2, 0, 0, 1);");
        manualAddressBtn.setVisible(false);
        manualAddressBtn.setManaged(false);
        
        // Enhanced hover effects for better UX
        manualAddressBtn.setOnMouseEntered(e -> 
            manualAddressBtn.setStyle("-fx-background-color: #138496; -fx-text-fill: white; -fx-font-weight: bold; " +
                                    "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; " +
                                    "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 4, 0, 0, 2);"));
        
        manualAddressBtn.setOnMouseExited(e -> 
            manualAddressBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-font-weight: bold; " +
                                    "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 10px; " +
                                    "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 2, 0, 0, 1);"));
        
        // Enhanced customer selection listener with robust address management and sync with customer settings
        customerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                try {
                    // Save customer immediately with normalization
                    String normalizedCustomer = newVal.trim().toUpperCase();
                    loadDAO.addCustomerIfNotExists(normalizedCustomer);
                    
                    // Refresh customer list to ensure consistency with customer settings
                    CompletableFuture.supplyAsync(() -> loadDAO.getAllCustomers())
                        .thenAcceptAsync(customerList -> {
                            Platform.runLater(() -> {
                                allCustomers.setAll(customerList);
                                if (!customerList.contains(normalizedCustomer)) {
                                    allCustomers.add(normalizedCustomer);
                                }
                            });
                        });
                    
                    // Update the autocomplete field with the current customer
                    addressAutocomplete.setCurrentCustomer(normalizedCustomer);
                    
                    // Get customer addresses with enhanced error handling and caching
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return loadDAO.getCustomerAddressBook(normalizedCustomer);
                        } catch (Exception e) {
                            logger.error("Error loading addresses for customer: {}", normalizedCustomer, e);
                            return new ArrayList<CustomerAddress>();
                        }
                    }).thenAcceptAsync(addresses -> {
                        Platform.runLater(() -> {
                            if (!addresses.isEmpty()) {
                                // Show the enhanced autocomplete field
                                addressAutocomplete.setVisible(true);
                                addressAutocomplete.setManaged(true);
                                
                                // Auto-select default address if available with priority logic
                                CustomerAddress defaultAddress = addresses.stream()
                                    .filter(addr -> isPickupCustomer ? addr.isDefaultPickup() : addr.isDefaultDrop())
                                    .findFirst()
                                    .orElse(null);
                                
                                if (defaultAddress != null) {
                                    addressAutocomplete.setAddress(defaultAddress);
                                    addressBox.setValue(defaultAddress);
                                    logger.debug("Auto-selected default {} address for customer: {}", 
                                        isPickupCustomer ? "pickup" : "drop", normalizedCustomer);
                                }
                                
                                // Maintain compatibility with existing UI elements
                                addressBox.getItems().setAll(addresses);
                                addressBox.setVisible(false);
                                addressBox.setManaged(false);
                                
                            } else {
                                // No addresses - show autocomplete for manual entry
                                addressAutocomplete.setVisible(true);
                                addressAutocomplete.setManaged(true);
                                addressBox.setVisible(false);
                                addressBox.setManaged(false);
                                logger.debug("No addresses found for customer: {}, showing manual entry field", normalizedCustomer);
                            }
                            
                            manualAddressBtn.setVisible(true);
                            manualAddressBtn.setManaged(true);
                        });
                    });
                    
                } catch (Exception e) {
                    logger.error("Error in customer selection handler: {}", e.getMessage(), e);
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Customer Selection Error");
                        alert.setHeaderText(null);
                        alert.setContentText("Error loading customer data: " + e.getMessage());
                        alert.getDialogPane().setStyle("-fx-background-color: white; -fx-text-fill: black;");
                        alert.showAndWait();
                    });
                }
            } else {
                // No customer selected - hide address fields
                addressAutocomplete.setVisible(false);
                addressAutocomplete.setManaged(false);
                addressAutocomplete.setCurrentCustomer(null);
                addressBox.setVisible(false);
                addressBox.setManaged(false);
                manualAddressBtn.setVisible(false);
                manualAddressBtn.setManaged(false);
            }
        });
        
        // Enhanced manual address entry functionality
        manualAddressBtn.setOnAction(e -> {
            String customerName = customerBox.getValue();
            if (customerName == null || customerName.trim().isEmpty()) {
                showError("Please select a customer first");
                return;
            }
            
            showManualAddressDialog(customerName, isPickupCustomer, addressBox, addressAutocomplete);
        });
        
        // Layout with proper spacing and growth
        // Sync autocomplete selection with the hidden ComboBox
        addressAutocomplete.selectedAddressProperty().addListener((obs, oldAddr, newAddr) -> {
            if (newAddr != null) {
                addressBox.setValue(newAddr);
            }
        });
        
        // Add all components to container
        container.getChildren().addAll(customerBox, addressAutocomplete, addressBox, manualAddressBtn);
        HBox.setHgrow(customerBox, Priority.NEVER);
        HBox.setHgrow(addressAutocomplete, Priority.ALWAYS);
        HBox.setHgrow(addressBox, Priority.NEVER); // Hidden, so no growth needed
        HBox.setHgrow(manualAddressBtn, Priority.NEVER);
        
        return container;
    }
    
    /**
     * Gets the selected address string from an enhanced customer field
     */
    private String getSelectedAddressFromField(HBox customerField) {
        if (customerField.getChildren().size() > 1) {
            ComboBox<CustomerAddress> addressBox = (ComboBox<CustomerAddress>) customerField.getChildren().get(1);
            if (addressBox.isVisible() && addressBox.getValue() != null) {
                CustomerAddress address = addressBox.getValue();
                StringBuilder addressString = new StringBuilder();
                
                if (address.getAddress() != null && !address.getAddress().trim().isEmpty()) {
                    addressString.append(address.getAddress());
                }
                
                if (address.getCity() != null && !address.getCity().trim().isEmpty()) {
                    if (addressString.length() > 0) addressString.append(", ");
                    addressString.append(address.getCity());
                }
                
                if (address.getState() != null && !address.getState().trim().isEmpty()) {
                    if (addressString.length() > 0) addressString.append(", ");
                    addressString.append(address.getState());
                }
                
                return addressString.toString();
            }
        }
        return "";
    }
    
    /**
     * Shows professional dialog for manually adding a new address for a customer
     */
    private void showManualAddressDialog(String customerName, boolean isPickupCustomer, ComboBox<CustomerAddress> addressBox, AddressAutocompleteField addressAutocomplete) {
        Dialog<CustomerAddress> dialog = new Dialog<>();
        dialog.setTitle("Add New Address");
        dialog.setHeaderText("Add new address for " + customerName);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Professional dialog styling with black text
        dialog.getDialogPane().setStyle("-fx-background-color: white; -fx-text-fill: black;");
        
        // Form fields with professional styling
        TextField locationNameField = new TextField();
        locationNameField.setPromptText("Location name (optional)");
        locationNameField.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
                                  "-fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;");
        
        TextField addressField = new TextField();
        addressField.setPromptText("Street address");
        addressField.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
                             "-fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;");
        
        TextField cityField = new TextField();
        cityField.setPromptText("City");
        cityField.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
                           "-fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;");
        
        TextField stateField = new TextField();
        stateField.setPromptText("State");
        stateField.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
                            "-fx-border-color: #ced4da; -fx-border-radius: 4px; -fx-padding: 8px;");
        
        CheckBox defaultPickupBox = new CheckBox("Default pickup location");
        defaultPickupBox.setStyle("-fx-text-fill: black; -fx-font-size: 13px;");
        
        CheckBox defaultDropBox = new CheckBox("Default drop location");
        defaultDropBox.setStyle("-fx-text-fill: black; -fx-font-size: 13px;");
        
        // Set defaults based on customer type
        if (isPickupCustomer) {
            defaultPickupBox.setSelected(true);
        } else {
            defaultDropBox.setSelected(true);
        }
        
        // Professional layout with proper spacing
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(25));
        
        // Create styled labels
        Label locationLabel = new Label("Location Name:");
        locationLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-font-size: 14px;");
        
        Label addressLabel = new Label("Address:");
        addressLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-font-size: 14px;");
        
        Label cityLabel = new Label("City:");
        cityLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-font-size: 14px;");
        
        Label stateLabel = new Label("State:");
        stateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-font-size: 14px;");
        
        Label defaultsLabel = new Label("Defaults:");
        defaultsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-font-size: 14px;");
        
        grid.add(locationLabel, 0, 0);
        grid.add(locationNameField, 1, 0);
        grid.add(addressLabel, 0, 1);
        grid.add(addressField, 1, 1);
        grid.add(cityLabel, 0, 2);
        grid.add(cityField, 1, 2);
        grid.add(stateLabel, 0, 3);
        grid.add(stateField, 1, 3);
        grid.add(defaultsLabel, 0, 4);
        
        HBox defaultBoxes = new HBox(15, defaultPickupBox, defaultDropBox);
        grid.add(defaultBoxes, 1, 4);
        
        // Set column constraints for better layout
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(120);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPrefWidth(300);
        grid.getColumnConstraints().addAll(col1, col2);
        
        dialog.getDialogPane().setContent(grid);
        
        // Validation
        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        
        // Enable OK button when required fields are filled
        Runnable validateInput = () -> {
            boolean isValid = !addressField.getText().trim().isEmpty() &&
                            !cityField.getText().trim().isEmpty() &&
                            !stateField.getText().trim().isEmpty();
            okButton.setDisable(!isValid);
        };
        
        addressField.textProperty().addListener((obs, oldVal, newVal) -> validateInput.run());
        cityField.textProperty().addListener((obs, oldVal, newVal) -> validateInput.run());
        stateField.textProperty().addListener((obs, oldVal, newVal) -> validateInput.run());
        
        // Result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    CustomerAddress newAddress = new CustomerAddress();
                    newAddress.setLocationName(locationNameField.getText().trim());
                    newAddress.setAddress(addressField.getText().trim());
                    newAddress.setCity(cityField.getText().trim());
                    newAddress.setState(stateField.getText().trim());
                    newAddress.setDefaultPickup(defaultPickupBox.isSelected());
                    newAddress.setDefaultDrop(defaultDropBox.isSelected());
                    
                    // Save to database
                    int addressId = loadDAO.addCustomerAddress(
                        customerName,
                        newAddress.getLocationName(),
                        newAddress.getAddress(),
                        newAddress.getCity(),
                        newAddress.getState()
                    );
                    
                    if (addressId > 0) {
                        newAddress.setId(addressId);
                        
                        // Update address dropdown
                        List<CustomerAddress> addresses = loadDAO.getCustomerAddressBook(customerName);
                        addressBox.getItems().setAll(addresses);
                        addressBox.setValue(newAddress);
                        
                        // Also update the autocomplete field
                        if (addressAutocomplete != null) {
                            addressAutocomplete.setAddress(newAddress);
                        }
                        
                        showInfo("Address added successfully!");
                        return newAddress;
                    } else {
                        showError("Failed to add address");
                        return null;
                    }
                    
                } catch (Exception e) {
                    logger.error("Error adding address: {}", e.getMessage(), e);
                    showError("Error adding address: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }

    /**
     * Setup on-demand autocomplete for ComboBox
     */
    /* Removed setupOnDemandAutocomplete - using standard ComboBox dropdown instead */
    
    /**
     * Creates an enhanced address autocomplete field with customer integration
     */
    private HBox createEnhancedAddressField(String promptText, boolean isPickupLocation) {
        HBox container = new HBox(5);
        container.setAlignment(Pos.CENTER_LEFT);
        
        // Enhanced location field with customer integration - using optimized version
        EnhancedLocationFieldOptimized locationField = new EnhancedLocationFieldOptimized(loadDAO);
        locationField.setPrefWidth(350);
        
        // Layout - no additional button needed since EnhancedLocationField has its own
        container.getChildren().addAll(locationField);
        HBox.setHgrow(locationField, Priority.ALWAYS);
        
        return container;
    }
    
    /**
     * Shows manual location entry dialog with enhanced functionality
     * NOTE: This method is currently unused and commented out to avoid compilation errors
     */
    /*
    private void showManualLocationDialog(boolean isPickupLocation, EnhancedLocationFieldOptimized locationField) {
        Dialog<CustomerLocation> dialog = new Dialog<>();
        dialog.setTitle("Manual Location Entry");
        dialog.setHeaderText("Enter " + (isPickupLocation ? "pickup" : "drop") + " location details");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        // Dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        // Location name field
        TextField locationNameField = new TextField();
        locationNameField.setPromptText("Location name (optional)");
        grid.add(new Label("Location Name:"), 0, 0);
        grid.add(locationNameField, 1, 0);
        
        // Address field
        TextField addressField = new TextField();
        addressField.setPromptText("Street address");
        grid.add(new Label("Address:"), 0, 1);
        grid.add(addressField, 1, 1);
        
        // City field
        TextField cityField = new TextField();
        cityField.setPromptText("City");
        grid.add(new Label("City:"), 0, 2);
        grid.add(cityField, 1, 2);
        
        // State field
        TextField stateField = new TextField();
        stateField.setPromptText("State");
        grid.add(new Label("State:"), 0, 3);
        grid.add(stateField, 1, 3);
        
        // Set as default checkbox
        CheckBox defaultCheckBox = new CheckBox("Set as default " + 
            (isPickupLocation ? "pickup" : "drop") + " location");
        grid.add(defaultCheckBox, 0, 4, 2, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        // Buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Validation
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        
        // Enable save button only if at least city or address is provided
        javafx.beans.value.ChangeListener<String> validationListener = (obs, oldVal, newVal) -> {
            boolean hasContent = !cityField.getText().trim().isEmpty() || 
                               !addressField.getText().trim().isEmpty();
            saveButton.setDisable(!hasContent);
        };
        
        cityField.textProperty().addListener(validationListener);
        addressField.textProperty().addListener(validationListener);
        
        // Result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                CustomerLocation location = new CustomerLocation();
                location.setLocationName(locationNameField.getText().trim());
                location.setAddress(addressField.getText().trim());
                location.setCity(cityField.getText().trim());
                location.setState(stateField.getText().trim());
                location.setLocationType(isPickupLocation ? "PICKUP" : "DROP");
                location.setDefault(defaultCheckBox.isSelected());
                
                return location;
            }
            return null;
        });
        
        // Show dialog and handle result
        Optional<CustomerLocation> result = dialog.showAndWait();
        result.ifPresent(location -> {
            try {
                // Save to database if customer is set
                if (locationField.getCurrentCustomer() != null && 
                    !locationField.getCurrentCustomer().trim().isEmpty()) {
                    
                    int locationId = loadDAO.addCustomerLocation(location, locationField.getCurrentCustomer());
                    location.setId(locationId);
                    
                    // Auto-save to customer address book
                    String fullAddress = location.getFullAddress();
                    loadDAO.autoSaveAddressToCustomerBook(
                        locationField.getCurrentCustomer(), 
                        fullAddress, 
                        isPickupLocation);
                    
                    logger.info("Saved new location for customer {}: {}", 
                        locationField.getCurrentCustomer(), fullAddress);
                }
                
                // Set the location in the field
                locationField.setValue(location);
                
            } catch (Exception e) {
                logger.error("Error saving location: {}", e.getMessage(), e);
                showError("Error saving location: " + e.getMessage());
            }
        });
    }
    */
    
    // Remove the unused buildLoadFormLayout method since we're adding sections directly

    /**
     * Wraps a VBox section in a collapsible TitledPane.
     */
    private TitledPane createCollapsibleSection(String title, VBox content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(false);
        pane.setAnimated(true);
        return pane;
    }
    
    /**
     * Initialize cache manager and load essential data
     */
    private void initializeCacheManager() {
        logger.info("Initializing cache manager for enterprise performance");
        
        // The cache manager will load data in background
        // UI will be responsive and data will populate asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Force cache initialization if needed
                ObservableList<String> customers = cacheManager.getCachedCustomers();
                cacheManager.getCachedBillingEntities();
                cacheManager.getCachedTrucks();
                cacheManager.getCachedTrailers();
                
                // Pre-populate customer addresses for all customers
                logger.info("Pre-populating customer addresses cache for {} customers", customers.size());
                int processed = 0;
                for (String customer : customers) {
                    if (customer != null && !customer.trim().isEmpty()) {
                        cacheManager.getCustomerAddressesAsync(customer);
                        processed++;
                        // Log progress every 100 customers
                        if (processed % 100 == 0) {
                            logger.debug("Pre-populated addresses for {} customers", processed);
                        }
                    }
                }
                
                logger.info("Cache manager initialized successfully with {} customer addresses pre-loaded", processed);
            } catch (Exception e) {
                logger.error("Error initializing cache manager", e);
            }
        });
    }
    
    /**
     * Load trucks data for dropdown selection
     */
    private void loadTrucksData() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Truck> trucks = truckDAO.findAll();
                Platform.runLater(() -> {
                    allTrucks.setAll(trucks);
                    logger.debug("Loaded {} trucks for dropdown selection", trucks.size());
                });
            } catch (Exception e) {
                logger.error("Error loading trucks data", e);
            }
        });
    }
    
    /**
     * Setup billing entity autocomplete with cache integration
     */
    private void setupBillingEntityAutocomplete(ComboBox<String> billingBox) {
        billingBox.setEditable(true);
        billingBox.setStyle(
            "-fx-font-size: 14px; -fx-text-fill: black; -fx-background-color: white; " +
            "-fx-border-color: #d1d5db; -fx-border-radius: 6px; -fx-background-radius: 6px; " +
            "-fx-padding: 10px 12px;"
        );
        
        // Use cache manager for autocomplete
        setupAutocompleteWithCache(billingBox, "billing");
    }
    
    /**
     * Setup driver autocomplete with enhanced filtering
     */
    private void setupDriverAutocomplete(ComboBox<Employee> driverBox) {
        driverBox.setEditable(true);
        
        // Enhanced driver search
        TextField editor = driverBox.getEditor();
        editor.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                driverBox.setItems(cacheManager.getCachedDrivers());
                return;
            }
            
            String query = newVal.toLowerCase().trim();
            List<Employee> filteredDrivers = cacheManager.getCachedDrivers().stream()
                .filter(driver -> {
                    String driverName = driver.getName().toLowerCase();
                    String truckUnit = driver.getTruckUnit() != null ? driver.getTruckUnit().toLowerCase() : "";
                    return driverName.contains(query) || truckUnit.contains(query);
                })
                .sorted((a, b) -> {
                    String aName = a.getName().toLowerCase();
                    String bName = b.getName().toLowerCase();
                    
                    // Exact match first
                    if (aName.equals(query) && !bName.equals(query)) return -1;
                    if (bName.equals(query) && !aName.equals(query)) return 1;
                    
                    // Starts with match
                    if (aName.startsWith(query) && !bName.startsWith(query)) return -1;
                    if (bName.startsWith(query) && !aName.startsWith(query)) return 1;
                    
                    return aName.compareTo(bName);
                })
                .collect(Collectors.toList());
            
            driverBox.setItems(FXCollections.observableArrayList(filteredDrivers));
        });
    }
    
    /**
     * Setup truck autocomplete with enhanced filtering
     */
    private void setupTruckAutocomplete(ComboBox<Truck> truckBox) {
        truckBox.setEditable(true);
        
        TextField editor = truckBox.getEditor();
        editor.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                truckBox.setItems(cacheManager.getCachedTrucks());
                return;
            }
            
            String query = newVal.toLowerCase().trim();
            List<Truck> filteredTrucks = cacheManager.getCachedTrucks().stream()
                .filter(truck -> {
                    String number = truck.getNumber() != null ? truck.getNumber().toLowerCase() : "";
                    String make = truck.getMake() != null ? truck.getMake().toLowerCase() : "";
                    String model = truck.getModel() != null ? truck.getModel().toLowerCase() : "";
                    return number.contains(query) || make.contains(query) || model.contains(query);
                })
                .sorted((a, b) -> {
                    String aNumber = a.getNumber() != null ? a.getNumber().toLowerCase() : "";
                    String bNumber = b.getNumber() != null ? b.getNumber().toLowerCase() : "";
                    
                    // Exact match first
                    if (aNumber.equals(query) && !bNumber.equals(query)) return -1;
                    if (bNumber.equals(query) && !aNumber.equals(query)) return 1;
                    
                    // Starts with match
                    if (aNumber.startsWith(query) && !bNumber.startsWith(query)) return -1;
                    if (bNumber.startsWith(query) && !aNumber.startsWith(query)) return 1;
                    
                    return aNumber.compareTo(bNumber);
                })
                .collect(Collectors.toList());
            
            truckBox.setItems(FXCollections.observableArrayList(filteredTrucks));
        });
    }
    
    /**
     * Generic autocomplete setup with cache integration
     */
    private void setupAutocompleteWithCache(ComboBox<String> comboBox, String dataType) {
        TextField editor = comboBox.getEditor();
        final Timer[] debounceTimer = {null};
        
        editor.textProperty().addListener((obs, oldVal, newVal) -> {
            if (debounceTimer[0] != null) {
                debounceTimer[0].cancel();
            }
            
            debounceTimer[0] = new Timer();
            debounceTimer[0].schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        if (newVal == null || newVal.trim().isEmpty()) {
                            ObservableList<String> data = "billing".equals(dataType) ? 
                                cacheManager.getCachedBillingEntities() : cacheManager.getCachedCustomers();
                            comboBox.setItems(data);
                            return;
                        }
                        
                        List<String> results = "billing".equals(dataType) ?
                            cacheManager.searchCustomers(newVal.trim(), 25) :
                            cacheManager.searchCustomers(newVal.trim(), 25);
                        
                        comboBox.setItems(FXCollections.observableArrayList(results));
                    });
                }
            }, 150);
        });
    }
    

    
    /**
     * Shutdown method for proper cleanup
     */
    public void shutdown() {
        logger.info("Shutting down LoadsPanel");
        
        // Shutdown cache manager
        cacheManager.shutdown();
        
        logger.info("LoadsPanel shutdown completed");
    }

}
		