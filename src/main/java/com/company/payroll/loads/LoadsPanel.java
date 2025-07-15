package com.company.payroll.loads;

// Java standard library imports (grouped alphabetically)
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Third-party imports
import javax.imageio.ImageIO;

// JavaFX imports
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

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

// Application-specific imports
import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;

// JavaFX concurrency imports
import javafx.concurrent.Task;
import javafx.application.Platform;

// JavaFX animation imports for scheduled tasks
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

public class LoadsPanel extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(LoadsPanel.class);
    
    private final LoadDAO loadDAO = new LoadDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TrailerDAO trailerDAO = new TrailerDAO();
    private final ObservableList<Load> allLoads = FXCollections.observableArrayList();
    private final ObservableList<Employee> allDrivers = FXCollections.observableArrayList();
    private final FilteredList<Employee> activeDrivers = new FilteredList<>(allDrivers, 
        driver -> driver.getStatus() == Employee.Status.ACTIVE);
    private final ObservableList<Trailer> allTrailers = FXCollections.observableArrayList();
    private final ObservableList<String> allCustomers = FXCollections.observableArrayList();

    private final List<StatusTab> statusTabs = new ArrayList<>();
    private Consumer<List<Load>> syncToTriumphCallback;
    
    // Add listener interface and list
    public interface LoadDataChangeListener {
        void onLoadDataChanged();
    }
    
    private final List<LoadDataChangeListener> loadDataChangeListeners = new ArrayList<>();

    // Document storage directory
    private static final String DOCUMENT_STORAGE_PATH = "load_documents";
    
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
    
    // Modern button styling method
    private Button createStyledButton(String text, String bgColor, String textColor) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 8 16 8 16; " +
            "-fx-background-radius: 5; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 1);",
            bgColor, textColor
        ));
        
        // Add hover effect
        button.setOnMouseEntered(e -> 
            button.setStyle(String.format(
                "-fx-background-color: derive(%s, -10%%); " +
                "-fx-text-fill: %s; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 16 8 16; " +
                "-fx-background-radius: 5; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);",
                bgColor, textColor
            ))
        );
        
        button.setOnMouseExited(e -> 
            button.setStyle(String.format(
                "-fx-background-color: %s; " +
                "-fx-text-fill: %s; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 16 8 16; " +
                "-fx-background-radius: 5; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 1);",
                bgColor, textColor
            ))
        );
        
        return button;
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

    public LoadsPanel() {
        logger.info("Initializing LoadsPanel");
        
        // Create document storage directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(DOCUMENT_STORAGE_PATH));
        } catch (IOException e) {
            logger.error("Failed to create document storage directory", e);
        }
        
        reloadAll();
        
        // Initialize automatic status update timeline
        initializeStatusUpdateScheduler();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        statusTabs.add(makeActiveTab());
        statusTabs.add(makeStatusTab("Cancelled", Load.Status.CANCELLED));
        statusTabs.add(makeStatusTab("All Loads", null));
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
            boolean statusFilter = l.getStatus() != Load.Status.CANCELLED && 
                                 l.getStatus() != Load.Status.PICKUP_LATE && 
                                 l.getStatus() != Load.Status.DELIVERY_LATE;
            
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
        
        ComboBox<String> customerBox = new ComboBox<>(allCustomers);
        customerBox.setPromptText("Customer");
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
        HBox searchBox2 = new HBox(8, customerBox, new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker, clearSearchBtn);
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
            Predicate<Load> pred = l -> l.getStatus() != Load.Status.CANCELLED && 
                                      l.getStatus() != Load.Status.PICKUP_LATE && 
                                      l.getStatus() != Load.Status.DELIVERY_LATE;

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
                pred = pred.and(l -> customer.equalsIgnoreCase(l.getCustomer()));
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
                logger.info("Syncing {} loads to MyTriumph audit", toSync.size());
                syncToTriumphCallback.accept(toSync);
                showInfo("Syncing " + toSync.size() + " loads to MyTriumph audit.");
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
        
        // Main content split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.3);
        
        // Left side - Customer list with refresh button
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));
        
        // Header with title and refresh button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label customerLabel = new Label("Customer List:");
        customerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        Button refreshAllBtn = createStyledButton("🔄 Refresh", "#4CAF50", "white");
        refreshAllBtn.setTooltip(new Tooltip("Refresh all customer data and locations"));
        
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
        
        splitPane.getItems().addAll(leftPane, rightPane);
        
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
            logger.info("Refreshing all customer data");
            try {
                // Reload customers
                reloadAll();
                
                // Refresh customer list display
                customerList.refresh();
                
                // Refresh selected customer's address book
                refreshCustomerAddresses.run();
                
                showInfo("All customer data refreshed successfully!");
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

        statusTab.tab = new Tab("Customer Settings", splitPane);
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
        columns.addAll(Arrays.asList(loadNumCol, poCol, customerCol, pickUpCol, customer2Col, dropCol,
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
        dialog.setTitle("Document Management - Load " + load.getLoadNumber());
        dialog.setHeaderText("Upload and manage documents");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Document list
        ListView<Load.LoadDocument> docList = new ListView<>();
        docList.setPrefHeight(200);
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
        
        // Upload section
        ComboBox<Load.LoadDocument.DocumentType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Load.LoadDocument.DocumentType.values());
        typeCombo.setValue(Load.LoadDocument.DocumentType.RATE_CONFIRMATION);
        
        Button uploadBtn = createStyledButton("📁 Upload Document", "#28a745", "white");
        Button deleteBtn = createStyledButton("🗑️ Delete Selected", "#dc3545", "white");
        Button mergeBtn = createStyledButton("🖼️ Merge & Print", "#6f42c1", "white");
        
        HBox uploadBox = new HBox(10, new Label("Type:"), typeCombo, uploadBtn, deleteBtn);
        uploadBox.setAlignment(Pos.CENTER_LEFT);
        
        uploadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Document");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png")
            );
            
            List<File> files = fileChooser.showOpenMultipleDialog(dialog.getOwner());
            if (files != null && !files.isEmpty()) {
                for (File file : files) {
                    try {
                        // Copy file to document storage
                        String fileName = load.getLoadNumber() + "_" + System.currentTimeMillis() + "_" + file.getName();
                        Path targetPath = Paths.get(DOCUMENT_STORAGE_PATH, fileName);
                        Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        
                        // Save document record
                        Load.LoadDocument doc = new Load.LoadDocument(
                            0,
                            load.getId(),
                            file.getName(),
                            targetPath.toString(),
                            typeCombo.getValue(),
                            LocalDate.now()
                        );
                        
                        int docId = loadDAO.addDocument(doc);
                        doc.setId(docId);
                        documents.add(doc);
                        load.getDocuments().add(doc);
                        
                        logger.info("Document uploaded: {}", file.getName());
                    } catch (IOException ex) {
                        logger.error("Failed to upload document: {}", file.getName(), ex);
                        showError("Failed to upload " + file.getName() + ": " + ex.getMessage());
                    }
                }
            }
        });
        
        deleteBtn.setOnAction(e -> {
            Load.LoadDocument selected = docList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                loadDAO.deleteDocument(selected.getId());
                documents.remove(selected);
                load.getDocuments().remove(selected);
                
                // Delete physical file
                try {
                    Files.deleteIfExists(Paths.get(selected.getFilePath()));
                } catch (IOException ex) {
                    logger.error("Failed to delete file: {}", selected.getFilePath(), ex);
                }
            }
        });
        
        mergeBtn.setOnAction(e -> handleMergeDocuments(load, documents));
        
        content.getChildren().addAll(
            new Label("Documents:"),
            docList,
            uploadBox,
            new Separator(),
            mergeBtn
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(500, 400);
        
        dialog.showAndWait();
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
				
				DirectoryChooser dirChooser = new DirectoryChooser();
				dirChooser.setTitle("Select Output Directory");
				File outputDir = dirChooser.showDialog(dialog.getOwner());
				if (outputDir == null) return null;
				
				try {
					File outputFile = new File(outputDir, fileName + ".pdf");
					boolean includeCoverPage = includeCoverPageCheckBox.isSelected();
					mergePDFs(load, selectedDocs, outputFile, includeCoverPage);
					
					// Ask if user wants to print
					Alert printAlert = new Alert(Alert.AlertType.CONFIRMATION,
							"PDF merged successfully. Do you want to print it now?",
							ButtonType.YES, ButtonType.NO);
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

    /**
     * Public method to show the load dialog from external components.
     * 
     * @param load The load to edit, or null to create a new load
     * @param isAdd True to add a new load, false to edit existing
     */
    public void showLoadDialogPublic(Load load, boolean isAdd) {
        showLoadDialog(load, isAdd);
    }
    
    private void showLoadDialog(Load load, boolean isAdd) {
        logger.debug("Showing load dialog - isAdd: {}", isAdd);
        Dialog<Load> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add Load" : "Edit Load");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField loadNumField = new TextField();
        TextField poField = new TextField();
		//Please continue typing the code from here
        ComboBox<String> customerBox = new ComboBox<>(allCustomers);
        customerBox.setEditable(true);
        customerBox.setPromptText("Select/Enter pickup customer");
        
        ComboBox<String> customer2Box = new ComboBox<>(allCustomers);
        customer2Box.setEditable(true);
        customer2Box.setPromptText("Select/Enter drop customer");
        
        // Enhanced location fields with manual entry capability
        EnhancedLocationField pickUpField = new EnhancedLocationField("PICKUP", loadDAO);
        EnhancedLocationField dropField = new EnhancedLocationField("DROP", loadDAO);
        
        // Update pickup location list when pickup customer is selected
        customerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                // Save customer immediately when entered/selected
                loadDAO.addCustomerIfNotExists(newVal.trim());
                
                // Reload customers to ensure it's in the list
                allCustomers.setAll(loadDAO.getAllCustomers());
                
                // Update pickup location field
                pickUpField.setCustomer(newVal);
            } else {
                pickUpField.setCustomer(null);
            }
        });
        
        // Update drop location list when drop customer is selected
        customer2Box.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                // Save customer immediately when entered/selected
                loadDAO.addCustomerIfNotExists(newVal.trim());
                
                // Reload customers to ensure it's in the list
                allCustomers.setAll(loadDAO.getAllCustomers());
                
                // Update drop location field
                dropField.setCustomer(newVal);
            } else {
                dropField.setCustomer(null);
            }
        });
        
        // Also save customer when focus is lost from the customer field
        customerBox.getEditor().focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Lost focus
                String customerName = customerBox.getValue();
                if (customerName != null && !customerName.trim().isEmpty()) {
                    loadDAO.addCustomerIfNotExists(customerName.trim());
                    allCustomers.setAll(loadDAO.getAllCustomers());
                }
            }
        });
        
        // Also save customer2 when focus is lost from the customer2 field
        customer2Box.getEditor().focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Lost focus
                String customerName = customer2Box.getValue();
                if (customerName != null && !customerName.trim().isEmpty()) {
                    loadDAO.addCustomerIfNotExists(customerName.trim());
                    allCustomers.setAll(loadDAO.getAllCustomers());
                }
            }
        });
        
        // Enhanced driver/truck selection
        ComboBox<Employee> driverBox = new ComboBox<>(activeDrivers);
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
        
        TextField truckUnitSearchField = new TextField();
        truckUnitSearchField.setPromptText("Search by Truck/Unit");
        
        // Link truck unit search to driver selection
        truckUnitSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                Employee found = employeeDAO.getByTruckUnit(newVal.trim());
                if (found != null) {
                    driverBox.setValue(found);
                }
            }
        });
        
        // Update truck search field when driver is selected
        driverBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getTruckUnit() != null) {
                truckUnitSearchField.setText(newVal.getTruckUnit());
            }
        });
        
        // Trailer selection - now automatically populated from driver's trailer
        ComboBox<Trailer> trailerBox = new ComboBox<>(allTrailers);
        trailerBox.setPromptText("Select Trailer");
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
        
        TextField trailerSearchField = new TextField();
        trailerSearchField.setPromptText("Search by Trailer #");
        
        // Link trailer search to selection
        trailerSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                Trailer found = trailerDAO.findByTrailerNumber(newVal.trim());
                if (found != null) {
                    trailerBox.setValue(found);
                }
            }
        });
        
        // Update trailer search field when trailer is selected
        trailerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                trailerSearchField.setText(newVal.getTrailerNumber());
            }
        });
        
        // Auto-populate trailer from driver's trailer number
        driverBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getTrailerNumber() != null && !newVal.getTrailerNumber().isEmpty()) {
                trailerSearchField.setText(newVal.getTrailerNumber());
                // Try to find and select the trailer
                Trailer driverTrailer = trailerDAO.findByTrailerNumber(newVal.getTrailerNumber());
                if (driverTrailer != null) {
                    trailerBox.setValue(driverTrailer);
                }
            }
        });
        
        ComboBox<Load.Status> statusBox = new ComboBox<>(FXCollections.observableArrayList(Load.Status.values()));
        TextField grossField = new TextField();
        TextArea notesField = new TextArea();
        DatePicker pickUpDatePicker = new DatePicker();
        Spinner<LocalTime> pickUpTimeSpinner = createTimeSpinner();  // NEW
        DatePicker deliveryDatePicker = new DatePicker();
        Spinner<LocalTime> deliveryTimeSpinner = createTimeSpinner();  // NEW
        TextField reminderField = new TextField();
        CheckBox hasLumperCheck = new CheckBox("Has Lumper");
        CheckBox hasRevisedRateCheck = new CheckBox("Has Revised Rate Confirmation");
        
        notesField.setPrefRowCount(2);
        reminderField.setPromptText("Reminder notes...");

        if (load != null) {
            loadNumField.setText(load.getLoadNumber());
            poField.setText(load.getPONumber());
            customerBox.setValue(load.getCustomer());
            customer2Box.setValue(load.getCustomer2());
            // Set location values using the enhanced fields
            if (load.getPickUpLocation() != null && !load.getPickUpLocation().isEmpty()) {
                pickUpField.setLocationString(load.getPickUpLocation());
            }
            
            if (load.getDropLocation() != null && !load.getDropLocation().isEmpty()) {
                dropField.setLocationString(load.getDropLocation());
            }
            
            // Set driver
            Employee loadedDriver = load.getDriver();
            if (loadedDriver != null) {
                // Find from allDrivers (not activeDrivers) so we can display loads 
                // assigned to drivers who are now TERMINATED or ON_LEAVE
                Employee matching = allDrivers.stream()
                        .filter(emp -> emp.getId() == loadedDriver.getId())
                        .findFirst()
                        .orElse(null);
                driverBox.setValue(matching);
                
                // Show warning if driver is not active but don't disable
                // (user might want to change to an active driver)
                if (matching != null && matching.getStatus() != Employee.Status.ACTIVE) {
                    driverBox.setTooltip(new Tooltip("⚠ Warning: Driver is " + matching.getStatus() + 
                        "\nYou may want to reassign this load to an active driver"));
                }
            } else {
                driverBox.setValue(null);
            }
            
            // Set trailer
            Trailer loadedTrailer = load.getTrailer();
            if (loadedTrailer != null) {
                Trailer matching = allTrailers.stream()
                        .filter(t -> t.getId() == loadedTrailer.getId())
                        .findFirst()
                        .orElse(null);
                trailerBox.setValue(matching);
            } else if (load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty()) {
                trailerSearchField.setText(load.getTrailerNumber());
            }
            
            statusBox.setValue(load.getStatus());
            grossField.setText(String.valueOf(load.getGrossAmount()));
            notesField.setText(load.getNotes());
            if (load.getPickUpDate() != null) {
                pickUpDatePicker.setValue(load.getPickUpDate());
            }
            if (load.getPickUpTime() != null) {
                pickUpTimeSpinner.getValueFactory().setValue(load.getPickUpTime());
            }
            if (load.getDeliveryDate() != null) {
                deliveryDatePicker.setValue(load.getDeliveryDate());
            }
            if (load.getDeliveryTime() != null) {
                deliveryTimeSpinner.getValueFactory().setValue(load.getDeliveryTime());
            }
            reminderField.setText(load.getReminder());
            hasLumperCheck.setSelected(load.isHasLumper());
            hasRevisedRateCheck.setSelected(load.isHasRevisedRateConfirmation());
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        errorLabel.setVisible(false);

        GridPane grid = new GridPane();
        grid.setVgap(7);
        grid.setHgap(12);
        grid.setPadding(new Insets(15, 20, 10, 10));
        int r = 0;
        grid.add(new Label("Load #*:"), 0, r);      grid.add(loadNumField, 1, r++);
        grid.add(new Label("PO:"), 0, r);           grid.add(poField, 1, r++);
        grid.add(new Label("Pickup Customer:"), 0, r);     grid.add(customerBox, 1, r++);
        grid.add(new Label("Pick Up:"), 0, r);      grid.add(pickUpField, 1, r++);
        grid.add(new Label("Drop Customer:"), 0, r);grid.add(customer2Box, 1, r++);
        grid.add(new Label("Drop Location:"), 0, r);grid.add(dropField, 1, r++);
        grid.add(new Label("Driver:"), 0, r);       grid.add(driverBox, 1, r++);
        grid.add(new Label("Find by Truck:"), 0, r);grid.add(truckUnitSearchField, 1, r++);
        grid.add(new Label("Trailer:"), 0, r);      grid.add(trailerBox, 1, r++);
        grid.add(new Label("Find by Trailer #:"), 0, r);grid.add(trailerSearchField, 1, r++);
        grid.add(new Label("Status:"), 0, r);       grid.add(statusBox, 1, r++);
        grid.add(new Label("Gross Amount:"), 0, r); grid.add(grossField, 1, r++);
        grid.add(new Label("Notes:"), 0, r);        grid.add(notesField, 1, r++);
        
        // Date and time fields
        HBox pickUpBox = new HBox(10, pickUpDatePicker, new Label("Time:"), pickUpTimeSpinner);
        pickUpBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(new Label("Pick Up Date:"), 0, r); grid.add(pickUpBox, 1, r++);
        
        HBox deliveryBox = new HBox(10, deliveryDatePicker, new Label("Time:"), deliveryTimeSpinner);
        deliveryBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(new Label("Delivery Date:"), 0, r);grid.add(deliveryBox, 1, r++);
        
        grid.add(new Label("Reminder:"), 0, r);     grid.add(reminderField, 1, r++);
        grid.add(hasLumperCheck, 1, r++);
        grid.add(hasRevisedRateCheck, 1, r++);
        
        // Add Manage Locations button (only for existing loads)
        if (!isAdd && load != null) {
            Button manageLocationsBtn = createStyledButton("🗺️ Manage Multiple Locations", "#6c757d", "white");
            manageLocationsBtn.setOnAction(e -> showLocationsDialog(load));
            grid.add(manageLocationsBtn, 1, r++);
        }
        
        grid.add(errorLabel, 1, r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean loadNumValid = !loadNumField.getText().trim().isEmpty();
            boolean grossValid = grossField.getText().trim().isEmpty() || isDouble(grossField.getText());
            boolean duplicate = checkDuplicateLoadNumber(loadNumField.getText().trim(), isAdd ? -1 : (load != null ? load.getId() : -1));
            boolean deliveryRequired = false;
            Load.Status statusValue = statusBox.getValue();
            if (statusValue == Load.Status.DELIVERED || statusValue == Load.Status.PAID) {
                deliveryRequired = true;
            }
            boolean deliveryValid = !deliveryRequired || deliveryDatePicker.getValue() != null;
            if (duplicate && loadNumValid) {
                errorLabel.setText("Load # already exists.");
                errorLabel.setVisible(true);
            } else if (!deliveryValid) {
                errorLabel.setText("Delivery date required for DELIVERED or PAID status.");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
            okBtn.setDisable(!(loadNumValid && grossValid && deliveryValid) || duplicate);
        };
        loadNumField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        grossField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        statusBox.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        deliveryDatePicker.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    String loadNum = loadNumField.getText().trim();
                    String poNum = poField.getText().trim();
                    String customer = customerBox.getValue() != null ? customerBox.getValue().trim() : "";
                    String customer2 = customer2Box.getValue() != null ? customer2Box.getValue().trim() : "";
                    String pickUp = pickUpField.getLocationString();
                    String drop = dropField.getLocationString();
                    Employee driver = driverBox.getValue();
                    Trailer trailer = trailerBox.getValue();
                    Load.Status status = statusBox.getValue() != null ? statusBox.getValue() : Load.Status.BOOKED;
                    double gross = grossField.getText().isEmpty() ? 0 : Double.parseDouble(grossField.getText());
                    String notes = notesField.getText().trim();
                    LocalDate pickUpDate = pickUpDatePicker.getValue();
                    LocalTime pickUpTime = pickUpTimeSpinner.getValue();  // NEW
                    LocalDate deliveryDate = deliveryDatePicker.getValue();
                    LocalTime deliveryTime = deliveryTimeSpinner.getValue();  // NEW
                    String reminder = reminderField.getText().trim();
                    boolean hasLumper = hasLumperCheck.isSelected();
                    boolean hasRevisedRate = hasRevisedRateCheck.isSelected();
                    
                    // Capture truck unit at time of load creation/update
                    String truckUnitSnapshot = driver != null ? driver.getTruckUnit() : "";
                    
                    // Get trailer info
                    int trailerId = 0;
                    String trailerNumber = "";
                    if (trailer != null) {
                        trailerId = trailer.getId();
                        trailerNumber = trailer.getTrailerNumber();
                    }

                    if (isAdd) {
                        logger.info("Adding new load: {}", loadNum);
                        
                        // Ensure customers exist before creating the load
                        if (customer != null && !customer.isEmpty()) {
                            loadDAO.addCustomerIfNotExists(customer);
                        }
                        if (customer2 != null && !customer2.isEmpty()) {
                            loadDAO.addCustomerIfNotExists(customer2);
                        }
                        
                        Load newLoad = new Load(0, loadNum, poNum, customer, customer2, pickUp, drop, driver, 
                                             truckUnitSnapshot, status, gross, notes, pickUpDate, pickUpTime,
                                             deliveryDate, deliveryTime, reminder, hasLumper, hasRevisedRate);
                        
                        // Set trailer information
                        newLoad.setTrailerId(trailerId);
                        newLoad.setTrailerNumber(trailerNumber);
                        newLoad.setTrailer(trailer);
                        
                        int newId = loadDAO.add(newLoad);
                        newLoad.setId(newId);
                        
                        // Save any manually entered addresses from EnhancedLocationFields
                        if (customer != null && !customer.isEmpty()) {
                            CustomerLocation pickupLoc = pickUpField.getValue();
                            if (pickupLoc != null && pickupLoc.getId() == -1) { // Manually entered
                                loadDAO.addCustomerLocation(pickupLoc, customer);
                            }
                        }
                        
                        if (customer2 != null && !customer2.isEmpty()) {
                            CustomerLocation dropLoc = dropField.getValue();
                            if (dropLoc != null && dropLoc.getId() == -1) { // Manually entered
                                loadDAO.addCustomerLocation(dropLoc, customer2);
                            }
                        }
                        
                        logger.info("Load added successfully: {} (ID: {})", loadNum, newId);
                        return newLoad;
                    } else {
                        logger.info("Updating load: {} (ID: {})", loadNum, load.getId());
                        load.setLoadNumber(loadNum);
                        load.setPONumber(poNum);
                        load.setCustomer(customer);
                        load.setCustomer2(customer2);
                        load.setPickUpLocation(pickUp);
                        load.setDropLocation(drop);
                        load.setDriver(driver);
                        
                        // Don't update truck unit snapshot on edit - preserve historical data
                        if (load.getTruckUnitSnapshot() == null || load.getTruckUnitSnapshot().isEmpty()) {
                            load.setTruckUnitSnapshot(truckUnitSnapshot);
                        }
                        
                        // Update trailer information
                        load.setTrailerId(trailerId);
                        load.setTrailerNumber(trailerNumber);
                        load.setTrailer(trailer);
                        
                        load.setStatus(status);
                        load.setGrossAmount(gross);
                        load.setNotes(notes);
                        load.setPickUpDate(pickUpDate);
                        load.setPickUpTime(pickUpTime);  // NEW
                        load.setDeliveryDate(deliveryDate);
                        load.setDeliveryTime(deliveryTime);  // NEW
                        load.setReminder(reminder);
                        load.setHasLumper(hasLumper);
                        load.setHasRevisedRateConfirmation(hasRevisedRate);
                        
                        // Save any manually entered addresses from EnhancedLocationFields during edit
                        if (customer != null && !customer.isEmpty()) {
                            CustomerLocation pickupLoc = pickUpField.getValue();
                            if (pickupLoc != null && pickupLoc.getId() == -1) { // Manually entered
                                loadDAO.addCustomerLocation(pickupLoc, customer);
                            }
                        }
                        
                        if (customer2 != null && !customer2.isEmpty()) {
                            CustomerLocation dropLoc = dropField.getValue();
                            if (dropLoc != null && dropLoc.getId() == -1) { // Manually entered
                                loadDAO.addCustomerLocation(dropLoc, customer2);
                            }
                        }
                        
                        loadDAO.update(load);
                        logger.info("Load updated successfully: {}", loadNum);
                        return load;
                    }
                } catch (Exception ex) {
                    logger.error("Error in load dialog: {}", ex.getMessage(), ex);
                    ex.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            reloadAll();
            notifyLoadDataChanged(); // Notify listeners after add/edit
        });
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

    private void reloadAll() {
        logger.debug("Reloading all data");
        allLoads.setAll(loadDAO.getAll());
        allDrivers.setAll(employeeDAO.getAll());
        allTrailers.setAll(trailerDAO.findAll());
        allCustomers.setAll(loadDAO.getAllCustomers());
        for (StatusTab tab : statusTabs) {
            if (tab.filteredList != null)
                tab.filteredList.setPredicate(tab.filteredList.getPredicate());
        }
        logger.info("Data reload complete - Loads: {}, Drivers: {}, Trailers: {}, Customers: {}", 
            allLoads.size(), allDrivers.size(), allTrailers.size(), allCustomers.size());
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
}
		