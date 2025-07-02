package com.company.payroll.dispatcher;

import com.company.payroll.drivers.Driver;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadStatus;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;

/**
 * Enhanced dialog for assigning loads to drivers with validation,
 * search functionality, and detailed information display
 * 
 * @author Payroll System
 * @version 2.0
 */
public class AssignLoadDialog extends Dialog<Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(AssignLoadDialog.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
    
    // Core components
    private final DispatcherController controller;
    private final SimpleBooleanProperty isValid = new SimpleBooleanProperty(false);
    
    // UI Components - Load Selection
    private ComboBox<Load> loadCombo;
    private FilteredList<Load> filteredLoads;
    private TextField loadSearchField;
    private Label loadCountLabel;
    private TitledPane loadDetailsPane;
    private VBox loadDetailsBox;
    
    // UI Components - Driver Selection
    private ComboBox<DispatcherDriverStatus> driverCombo;
    private FilteredList<DispatcherDriverStatus> filteredDrivers;
    private TextField driverSearchField;
    private Label driverCountLabel;
    private TitledPane driverDetailsPane;
    private VBox driverDetailsBox;
    
    // UI Components - Options
    private TextArea notesArea;
    private CheckBox notifyDriverCheck;
    private CheckBox updateStatusCheck;
    private ComboBox<String> assignmentTypeCombo;
    private DatePicker notifyDatePicker;
    private Spinner<Integer> notifyHourSpinner;
    private Spinner<Integer> notifyMinuteSpinner;
    
    // Compatibility mapping from driver to estimated hours
    private final Map<Driver, Double> driverCompatibilityMap = new HashMap<>();
    
    /**
     * Constructor - initializes dialog with controller and parent window
     */
    public AssignLoadDialog(DispatcherController controller, Window owner) {
        this.controller = controller;
        
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        
        setTitle("Assign Load to Driver");
        setHeaderText("Select an unassigned load and available driver");
        
        // Apply styling
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/dispatcher-dialogs.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("assign-load-dialog");
        
        initializeUI();
        setupBindings();
        setupKeyboardShortcuts();
        
        // Dialog buttons
        ButtonType assignButton = new ButtonType("Assign", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        getDialogPane().getButtonTypes().addAll(assignButton, cancelButton);
        
        // Disable Assign button until a valid selection is made
        Button assignBtn = (Button) getDialogPane().lookupButton(assignButton);
        assignBtn.disableProperty().bind(isValid.not());
        
        setResultConverter(dialogButton -> {
            if (dialogButton == assignButton) {
                return performAssignment();
            }
            return false;
        });
        
        logger.info("AssignLoadDialog initialized by {} at {}", 
            System.getProperty("user.name", "mgubran1"), 
            LocalDateTime.now().format(DATETIME_FORMAT));
    }
    
    /**
     * Initialize the UI components
     */
    private void initializeUI() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));
        
        // Create load selection section
        TitledPane loadSelectionPane = createLoadSelectionPane();
        
        // Create driver selection section
        TitledPane driverSelectionPane = createDriverSelectionPane();
        
        // Create options section
        TitledPane optionsPane = createOptionsPane();
        
        // Add sections to accordion to save space
        Accordion accordion = new Accordion();
        accordion.getPanes().addAll(loadSelectionPane, driverSelectionPane, optionsPane);
        accordion.setExpandedPane(loadSelectionPane);
        
        // Create summary section (always visible)
        VBox summaryBox = createSummaryBox();
        
        // Layout
        VBox centerBox = new VBox(10, accordion);
        mainLayout.setCenter(centerBox);
        mainLayout.setBottom(summaryBox);
        
        getDialogPane().setContent(mainLayout);
        getDialogPane().setPrefSize(650, 650);
    }
    
    /**
     * Create load selection section
     */
    private TitledPane createLoadSelectionPane() {
        VBox loadSelectionBox = new VBox(10);
        loadSelectionBox.setPadding(new Insets(10));
        
        // Load search and selection
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchIcon = new Label("🔍");
        searchIcon.setFont(Font.font(14));
        
        loadSearchField = new TextField();
        loadSearchField.setPromptText("Search loads by number, customer, or location...");
        loadSearchField.setPrefWidth(300);
        HBox.setHgrow(loadSearchField, Priority.ALWAYS);
        
        loadCountLabel = new Label("0 loads available");
        loadCountLabel.getStyleClass().add("count-label");
        
        searchBox.getChildren().addAll(searchIcon, loadSearchField, loadCountLabel);
        
        // Load combo
        HBox loadBox = new HBox(10);
        loadBox.setAlignment(Pos.CENTER_LEFT);
        
        Label loadLabel = new Label("Select Load:");
        loadLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        ObservableList<Load> unassignedLoads = FXCollections.observableArrayList(controller.getActiveLoads());
        filteredLoads = new FilteredList<>(unassignedLoads, load -> 
            load.getDriver() == null && load.getStatus() == LoadStatus.UNASSIGNED);
            
        loadCombo = new ComboBox<>(filteredLoads);
        loadCombo.setPrefWidth(400);
        loadCombo.setVisibleRowCount(8);
        HBox.setHgrow(loadCombo, Priority.ALWAYS);
        
        loadCombo.setCellFactory(lv -> new ListCell<Load>() {
            @Override
            protected void updateItem(Load load, boolean empty) {
                super.updateItem(load, empty);
                if (empty || load == null) {
                    setText(null);
                } else {
                    setText(formatLoadDisplay(load));
                }
            }
        });
        
        loadCombo.setButtonCell(new ListCell<Load>() {
            @Override
            protected void updateItem(Load load, boolean empty) {
                super.updateItem(load, empty);
                if (empty || load == null) {
                    setText(null);
                } else {
                    setText(formatLoadDisplay(load));
                }
            }
        });
        
        loadBox.getChildren().addAll(loadLabel, loadCombo);
        
        // Load details section
        loadDetailsBox = new VBox(5);
        loadDetailsBox.setPadding(new Insets(10));
        loadDetailsBox.getStyleClass().add("details-box");
        loadDetailsBox.setMinHeight(200);
        
        Label placeholderLabel = new Label("Select a load to view details");
        placeholderLabel.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
        loadDetailsBox.getChildren().add(placeholderLabel);
        
        loadDetailsPane = new TitledPane("Load Details", loadDetailsBox);
        loadDetailsPane.setCollapsible(false);
        loadDetailsPane.setExpanded(true);
        
        loadSelectionBox.getChildren().addAll(searchBox, loadBox, loadDetailsPane);
        
        // Set load count
        loadCountLabel.setText(filteredLoads.size() + " loads available");
        
        // Setup load search filter
        loadSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                filteredLoads.setPredicate(load -> load.getDriver() == null && 
                                                  load.getStatus() == LoadStatus.UNASSIGNED);
            } else {
                String searchText = newVal.toLowerCase();
                filteredLoads.setPredicate(load -> 
                    (load.getDriver() == null && load.getStatus() == LoadStatus.UNASSIGNED) && 
                    (load.getLoadNumber().toLowerCase().contains(searchText) ||
                     load.getCustomer().toLowerCase().contains(searchText) ||
                     load.getOriginCity().toLowerCase().contains(searchText) ||
                     load.getDestCity().toLowerCase().contains(searchText))
                );
            }
            loadCountLabel.setText(filteredLoads.size() + " loads available");
        });
        
        // Update details when load selected
        loadCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateLoadDetails(newVal);
            validateSelections();
            
            // Update compatibility for available drivers
            if (newVal != null) {
                calculateDriverCompatibility(newVal);
            }
        });
        
        TitledPane loadSelectionPane = new TitledPane("Step 1: Select Load", loadSelectionBox);
        loadSelectionPane.setExpanded(true);
        return loadSelectionPane;
    }
    
    /**
     * Create driver selection section
     */
    private TitledPane createDriverSelectionPane() {
        VBox driverSelectionBox = new VBox(10);
        driverSelectionBox.setPadding(new Insets(10));
        
        // Driver search and selection
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchIcon = new Label("🔍");
        searchIcon.setFont(Font.font(14));
        
        driverSearchField = new TextField();
        driverSearchField.setPromptText("Search drivers by name or location...");
        driverSearchField.setPrefWidth(300);
        HBox.setHgrow(driverSearchField, Priority.ALWAYS);
        
        driverCountLabel = new Label("0 drivers available");
        driverCountLabel.getStyleClass().add("count-label");
        
        searchBox.getChildren().addAll(searchIcon, driverSearchField, driverCountLabel);
        
        // Driver combo
        HBox driverBox = new HBox(10);
        driverBox.setAlignment(Pos.CENTER_LEFT);
        
        Label driverLabel = new Label("Select Driver:");
        driverLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        ObservableList<DispatcherDriverStatus> allDrivers = controller.getDriverStatuses();
        filteredDrivers = new FilteredList<>(allDrivers, 
            driver -> driver.getStatus() == DispatcherDriverStatus.Status.AVAILABLE);
            
        driverCombo = new ComboBox<>(filteredDrivers);
        driverCombo.setPrefWidth(400);
        driverCombo.setVisibleRowCount(8);
        HBox.setHgrow(driverCombo, Priority.ALWAYS);
        
        driverCombo.setCellFactory(lv -> new ListCell<DispatcherDriverStatus>() {
            @Override
            protected void updateItem(DispatcherDriverStatus driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty || driver == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox cellBox = new HBox(10);
                    cellBox.setAlignment(Pos.CENTER_LEFT);
                    
                    Label nameLabel = new Label(driver.getDriverName());
                    
                    // Show compatibility indicator if a load is selected
                    if (loadCombo.getValue() != null && driverCompatibilityMap.containsKey(driver.getDriver())) {
                        double compatibility = driverCompatibilityMap.get(driver.getDriver());
                        String compatText = String.format("%.0f%%", compatibility * 100);
                        Label compatLabel = new Label(compatText);
                        
                        if (compatibility >= 0.8) {
                            compatLabel.setTextFill(Color.GREEN);
                        } else if (compatibility >= 0.5) {
                            compatLabel.setTextFill(Color.ORANGE);
                        } else {
                            compatLabel.setTextFill(Color.RED);
                        }
                        
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);
                        
                        cellBox.getChildren().addAll(nameLabel, spacer, compatLabel);
                    } else {
                        cellBox.getChildren().add(nameLabel);
                    }
                    
                    setText(null);
                    setGraphic(cellBox);
                }
            }
        });
        
        driverCombo.setButtonCell(new ListCell<DispatcherDriverStatus>() {
            @Override
            protected void updateItem(DispatcherDriverStatus driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty || driver == null) {
                    setText(null);
                } else {
                    setText(driver.getDriverName());
                }
            }
        });
        
        CheckBox showAllDriversCheck = new CheckBox("Show all drivers");
        showAllDriversCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                filteredDrivers.setPredicate(driver -> true);
            } else {
                applyDriverFilter(driverSearchField.getText());
            }
            driverCountLabel.setText(filteredDrivers.size() + " drivers available");
        });
        
        driverBox.getChildren().addAll(driverLabel, driverCombo);
        
        // Driver details section
        driverDetailsBox = new VBox(5);
        driverDetailsBox.setPadding(new Insets(10));
        driverDetailsBox.getStyleClass().add("details-box");
        driverDetailsBox.setMinHeight(200);
        
        Label placeholderLabel = new Label("Select a driver to view details");
        placeholderLabel.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
        driverDetailsBox.getChildren().add(placeholderLabel);
        
        driverDetailsPane = new TitledPane("Driver Details", driverDetailsBox);
        driverDetailsPane.setCollapsible(false);
        driverDetailsPane.setExpanded(true);
        
        driverSelectionBox.getChildren().addAll(searchBox, driverBox, showAllDriversCheck, driverDetailsPane);
        
        // Set driver count
        driverCountLabel.setText(filteredDrivers.size() + " drivers available");
        
        // Setup driver search filter
        driverSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            applyDriverFilter(newVal);
            driverCountLabel.setText(filteredDrivers.size() + " drivers available");
        });
        
        // Update details when driver selected
        driverCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDriverDetails(newVal);
            validateSelections();
        });
        
        TitledPane driverSelectionPane = new TitledPane("Step 2: Select Driver", driverSelectionBox);
        driverSelectionPane.setExpanded(false);
        return driverSelectionPane;
    }
    
    /**
     * Create options section
     */
    private TitledPane createOptionsPane() {
        VBox optionsBox = new VBox(10);
        optionsBox.setPadding(new Insets(10));
        
        // Assignment notes
        Label notesLabel = new Label("Assignment Notes:");
        notesLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        notesArea = new TextArea();
        notesArea.setPrefRowCount(4);
        notesArea.setPromptText("Enter any special instructions or notes for this assignment...");
        notesArea.setWrapText(true);
        
        // Assignment options
        Label optionsLabel = new Label("Assignment Options:");
        optionsLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
        
        notifyDriverCheck = new CheckBox("Notify Driver");
        notifyDriverCheck.setSelected(true);
        
        updateStatusCheck = new CheckBox("Update Driver Status");
        updateStatusCheck.setSelected(true);
        
        HBox typeBox = new HBox(10);
        typeBox.setAlignment(Pos.CENTER_LEFT);
        
        Label typeLabel = new Label("Assignment Type:");
        
        assignmentTypeCombo = new ComboBox<>();
        assignmentTypeCombo.getItems().addAll(
            "Standard", "Priority", "Team", "Relay", "Emergency"
        );
        assignmentTypeCombo.setValue("Standard");
        
        typeBox.getChildren().addAll(typeLabel, assignmentTypeCombo);
        
        // Notification scheduling
        HBox notifyBox = new HBox(10);
        notifyBox.setAlignment(Pos.CENTER_LEFT);
        
        Label notifyLabel = new Label("Send Notification:");
        
        notifyDatePicker = new DatePicker(LocalDate.now());
        
        notifyHourSpinner = new Spinner<>(0, 23, LocalDateTime.now().getHour());
        notifyHourSpinner.setPrefWidth(70);
        notifyHourSpinner.setEditable(true);
        
        notifyMinuteSpinner = new Spinner<>(0, 59, LocalDateTime.now().getMinute());
        notifyMinuteSpinner.setPrefWidth(70);
        notifyMinuteSpinner.setEditable(true);
        
        Label colonLabel = new Label(":");
        
        Button nowButton = new Button("Now");
        nowButton.setOnAction(e -> {
            LocalDateTime now = LocalDateTime.now();
            notifyDatePicker.setValue(now.toLocalDate());
            notifyHourSpinner.getValueFactory().setValue(now.getHour());
            notifyMinuteSpinner.getValueFactory().setValue(now.getMinute());
        });
        
        HBox timeBox = new HBox(5, notifyHourSpinner, colonLabel, notifyMinuteSpinner, nowButton);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        
        notifyBox.getChildren().addAll(notifyLabel, notifyDatePicker, timeBox);
        notifyBox.disableProperty().bind(notifyDriverCheck.selectedProperty().not());
        
        optionsBox.getChildren().addAll(
            notesLabel, notesArea, 
            optionsLabel, notifyDriverCheck, updateStatusCheck, typeBox, notifyBox
        );
        
        TitledPane optionsPane = new TitledPane("Step 3: Assignment Options", optionsBox);
        optionsPane.setExpanded(false);
        return optionsPane;
    }
    
    /**
     * Create summary box (always visible)
     */
    private VBox createSummaryBox() {
        VBox summaryBox = new VBox(10);
        summaryBox.setPadding(new Insets(20, 10, 10, 10));
        summaryBox.getStyleClass().add("summary-box");
        
        Label summaryLabel = new Label("Assignment Summary");
        summaryLabel.setFont(Font.font(null, FontWeight.BOLD, 14));
        
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(10);
        summaryGrid.setVgap(5);
        
        Label loadLabel = new Label("Load:");
        Label loadValueLabel = new Label("No load selected");
        summaryGrid.add(loadLabel, 0, 0);
        summaryGrid.add(loadValueLabel, 1, 0);
        
        Label driverLabel = new Label("Driver:");
        Label driverValueLabel = new Label("No driver selected");
        summaryGrid.add(driverLabel, 0, 1);
        summaryGrid.add(driverValueLabel, 1, 1);
        
        Label routeLabel = new Label("Route:");
        Label routeValueLabel = new Label("");
        summaryGrid.add(routeLabel, 0, 2);
        summaryGrid.add(routeValueLabel, 1, 2);
        
        Label datesLabel = new Label("Dates:");
        Label datesValueLabel = new Label("");
        summaryGrid.add(datesLabel, 0, 3);
        summaryGrid.add(datesValueLabel, 1, 3);
        
        Label compatibilityLabel = new Label("Compatibility:");
        ProgressBar compatibilityBar = new ProgressBar(0);
        compatibilityBar.setPrefWidth(200);
        summaryGrid.add(compatibilityLabel, 0, 4);
        summaryGrid.add(compatibilityBar, 1, 4);
        
        summaryBox.getChildren().addAll(summaryLabel, new Separator(), summaryGrid);
        
        // Update summary when selections change
        loadCombo.valueProperty().addListener((obs, old, newLoad) -> {
            if (newLoad != null) {
                loadValueLabel.setText(newLoad.getLoadNumber() + " - " + newLoad.getCustomer());
                routeValueLabel.setText(newLoad.getOriginCity() + " → " + newLoad.getDestCity());
                
                LocalDate pickupDate = newLoad.getPickupDate();
                LocalDate deliveryDate = newLoad.getDeliveryDate();
                if (pickupDate != null && deliveryDate != null) {
                    datesValueLabel.setText(pickupDate.format(DATE_FORMAT) + " to " + 
                                         deliveryDate.format(DATE_FORMAT));
                }
            } else {
                loadValueLabel.setText("No load selected");
                routeValueLabel.setText("");
                datesValueLabel.setText("");
            }
            
            updateCompatibilityIndicator(compatibilityBar);
        });
        
        driverCombo.valueProperty().addListener((obs, old, newDriver) -> {
            if (newDriver != null) {
                driverValueLabel.setText(newDriver.getDriverName());
            } else {
                driverValueLabel.setText("No driver selected");
            }
            
            updateCompatibilityIndicator(compatibilityBar);
        });
        
        return summaryBox;
    }
    
    /**
     * Set up data bindings
     */
    private void setupBindings() {
        // Enable "Assign" button when both load and driver are selected
        isValid.bind(Bindings.and(
            loadCombo.valueProperty().isNotNull(),
            driverCombo.valueProperty().isNotNull()
        ));
    }
    
    /**
     * Set up keyboard shortcuts
     */
    private void setupKeyboardShortcuts() {
        getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                refreshData();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            }
        });
    }
    
    /**
     * Format load display for combo box
     */
    private String formatLoadDisplay(Load load) {
        return String.format("#%s - %s (%s → %s) - %s",
            load.getLoadNumber(),
            load.getCustomer(),
            load.getOriginCity(),
            load.getDestCity(),
            load.getPickupDate() != null ? load.getPickupDate().format(DATE_FORMAT) : "TBD"
        );
    }
    
    /**
     * Format detailed load information
     */
    private void updateLoadDetails(Load load) {
        loadDetailsBox.getChildren().clear();
        
        if (load == null) {
            Label placeholderLabel = new Label("Select a load to view details");
            placeholderLabel.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
            loadDetailsBox.getChildren().add(placeholderLabel);
            return;
        }
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(5);
        
        int row = 0;
        addDetailRow(grid, "Load Number:", load.getLoadNumber(), row++);
        addDetailRow(grid, "Customer:", load.getCustomer(), row++);
        addDetailRow(grid, "PO Number:", load.getPONumber(), row++);
        addDetailRow(grid, "Commodity:", load.getCommodity(), row++);
        addDetailRow(grid, "Weight:", load.getWeight() + " lbs", row++);
        
        row = 0;
        addDetailRow(grid, "Pickup:", load.getOriginName(), row++, 2);
        addDetailRow(grid, "Pickup Address:", formatAddress(load.getOriginAddress(), load.getOriginCity(), 
                                                       load.getOriginState(), load.getOriginZip()), row++, 2);
        
        LocalDate pickupDate = load.getPickupDate();
        String pickupTimeStr = "";
        if (pickupDate != null) {
            pickupTimeStr = pickupDate.format(DATE_FORMAT);
            if (load.getPickupTime() != null) {
                pickupTimeStr += " @ " + load.getPickupTime().format(TIME_FORMAT);
            }
        }
        addDetailRow(grid, "Pickup Date/Time:", pickupTimeStr, row++, 2);
        
        row = 0;
        addDetailRow(grid, "Delivery:", load.getDestName(), row++, 4);
        addDetailRow(grid, "Delivery Address:", formatAddress(load.getDestAddress(), load.getDestCity(), 
                                                        load.getDestState(), load.getDestZip()), row++, 4);
        
        LocalDate deliveryDate = load.getDeliveryDate();
        String deliveryTimeStr = "";
        if (deliveryDate != null) {
            deliveryTimeStr = deliveryDate.format(DATE_FORMAT);
            if (load.getDeliveryTime() != null) {
                deliveryTimeStr += " @ " + load.getDeliveryTime().format(TIME_FORMAT);
            }
        }
        addDetailRow(grid, "Delivery Date/Time:", deliveryTimeStr, row++, 4);
        
        addDetailRow(grid, "Status:", load.getStatus().toString(), 6, 0);
        addDetailRow(grid, "Miles:", String.format("%.0f", load.getMiles()), 7, 0);
        addDetailRow(grid, "Rate:", String.format("$%.2f", load.getRate()), 8, 0);
        
        loadDetailsBox.getChildren().add(grid);
        
        // Add notes if available
        if (load.getNotes() != null && !load.getNotes().isEmpty()) {
            Label notesLabel = new Label("Notes:");
            notesLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
            TextArea notesArea = new TextArea(load.getNotes());
            notesArea.setEditable(false);
            notesArea.setPrefRowCount(3);
            notesArea.setWrapText(true);
            
            VBox notesBox = new VBox(5, notesLabel, notesArea);
            notesBox.setPadding(new Insets(10, 0, 0, 0));
            loadDetailsBox.getChildren().add(notesBox);
        }
    }
    
    /**
     * Format driver details display
     */
    private void updateDriverDetails(DispatcherDriverStatus driverStatus) {
        driverDetailsBox.getChildren().clear();
        
        if (driverStatus == null) {
            Label placeholderLabel = new Label("Select a driver to view details");
            placeholderLabel.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
            driverDetailsBox.getChildren().add(placeholderLabel);
            return;
        }
        
        Driver driver = driverStatus.getDriver();
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(5);
        
        int row = 0;
        addDetailRow(grid, "Name:", driver.getName(), row++);
        addDetailRow(grid, "ID:", driver.getDriverId(), row++);
        addDetailRow(grid, "Phone:", driver.getPhoneNumber(), row++);
        addDetailRow(grid, "Email:", driver.getEmail(), row++);
        addDetailRow(grid, "License:", driver.getLicenseNumber() + " (" + driver.getLicenseState() + ")", row++);
        
        row = 0;
        addDetailRow(grid, "Status:", driverStatus.getStatus().getDisplayName(), row++, 2);
        addDetailRow(grid, "Location:", driverStatus.getLocation(), row++, 2);
        
        // Equipment
        addDetailRow(grid, "Truck:", driverStatus.getTruckNumber(), row++, 2);
        addDetailRow(grid, "Trailer:", driverStatus.getTrailerNumber(), row++, 2);
        
        // HOS info
        addDetailRow(grid, "Hours Today:", String.format("%.1f", calculateHoursToday(driver)), row++, 2);
        addDetailRow(grid, "Hours This Week:", String.format("%.1f", calculateHoursThisWeek(driver)), row++, 2);
        addDetailRow(grid, "Days on Road:", String.valueOf(calculateDaysOnRoad(driver)), row++, 2);
        
        // If driver has a current load
        Load currentLoad = driver.getCurrentLoad();
        if (currentLoad != null) {
            addDetailRow(grid, "Current Load:", currentLoad.getLoadNumber(), row, 0);
            addDetailRow(grid, "ETA:", driverStatus.getETA() != null ? 
                driverStatus.getETA().format(DATETIME_FORMAT) : "N/A", row, 2);
            row++;
        }
        
        driverDetailsBox.getChildren().add(grid);
        
        // Add compatibility indicator if a load is selected
        if (loadCombo.getValue() != null && driverCompatibilityMap.containsKey(driver)) {
            double compatibility = driverCompatibilityMap.get(driver);
            
            HBox compatBox = new HBox(10);
            compatBox.setAlignment(Pos.CENTER_LEFT);
            compatBox.setPadding(new Insets(15, 0, 0, 0));
            
            Label compatLabel = new Label("Compatibility Score: ");
            compatLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
            
            ProgressBar compatBar = new ProgressBar(compatibility);
            compatBar.setPrefWidth(150);
            
            Label compatPercentLabel = new Label(String.format("%.0f%%", compatibility * 100));
            if (compatibility >= 0.8) {
                compatPercentLabel.setTextFill(Color.GREEN);
            } else if (compatibility >= 0.5) {
                compatPercentLabel.setTextFill(Color.ORANGE);
            } else {
                compatPercentLabel.setTextFill(Color.RED);
            }
            compatPercentLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
            
            compatBox.getChildren().addAll(compatLabel, compatBar, compatPercentLabel);
            driverDetailsBox.getChildren().add(compatBox);
            
            // Add compatibility factors
            VBox factorsBox = new VBox(5);
            factorsBox.setPadding(new Insets(10, 0, 0, 0));
            
            Label factorsLabel = new Label("Compatibility Factors:");
            factorsLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
            factorsBox.getChildren().add(factorsLabel);
            
            // Add individual factors (these would be calculated in a real system)
            addCompatibilityFactor(factorsBox, "Location", calculateLocationFactor(driverStatus, loadCombo.getValue()));
            addCompatibilityFactor(factorsBox, "Hours Available", calculateHoursAvailableFactor(driver));
            addCompatibilityFactor(factorsBox, "Equipment", calculateEquipmentFactor(driverStatus, loadCombo.getValue()));
            addCompatibilityFactor(factorsBox, "Experience", calculateExperienceFactor(driver, loadCombo.getValue()));
            
            driverDetailsBox.getChildren().add(factorsBox);
        }
        
        // Add notes if available
        if (driver.getNotes() != null && !driver.getNotes().isEmpty()) {
            Label notesLabel = new Label("Driver Notes:");
            notesLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
            TextArea driverNotesArea = new TextArea(driver.getNotes());
            driverNotesArea.setEditable(false);
            driverNotesArea.setPrefRowCount(3);
            driverNotesArea.setWrapText(true);
            
            VBox notesBox = new VBox(5, notesLabel, driverNotesArea);
            notesBox.setPadding(new Insets(10, 0, 0, 0));
            driverDetailsBox.getChildren().add(notesBox);
        }
    }
    
    /**
     * Add a detail row to the grid
     */
    private void addDetailRow(GridPane grid, String label, String value, int row) {
        addDetailRow(grid, label, value, row, 0);
    }
    
    /**
     * Add a detail row to the grid with column offset
     */
    private void addDetailRow(GridPane grid, String label, String value, int row, int colOffset) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font(null, FontWeight.BOLD, 12));
        Label valueNode = new Label(value != null ? value : "");
        
        grid.add(labelNode, colOffset, row);
        grid.add(valueNode, colOffset + 1, row);
    }
    
    /**
     * Add a compatibility factor to the factors box
     */
    private void addCompatibilityFactor(VBox factorsBox, String factor, double value) {
        HBox factorBox = new HBox(10);
        factorBox.setAlignment(Pos.CENTER_LEFT);
        
        Label factorLabel = new Label(factor + ":");
        factorLabel.setPrefWidth(120);
        
        ProgressBar factorBar = new ProgressBar(value);
        factorBar.setPrefWidth(100);
        
        Label percentLabel = new Label(String.format("%.0f%%", value * 100));
        if (value >= 0.8) {
            percentLabel.setTextFill(Color.GREEN);
        } else if (value >= 0.5) {
            percentLabel.setTextFill(Color.ORANGE);
        } else {
            percentLabel.setTextFill(Color.RED);
        }
        
        factorBox.getChildren().addAll(factorLabel, factorBar, percentLabel);
        factorsBox.getChildren().add(factorBox);
    }
    
    /**
     * Format address for display
     */
    private String formatAddress(String address, String city, String state, String zip) {
        StringBuilder sb = new StringBuilder();
        
        if (address != null && !address.isEmpty()) {
            sb.append(address);
        }
        
        if (city != null && !city.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        
        if (state != null && !state.isEmpty()) {
            if (city != null && !city.isEmpty()) {
                sb.append(", ");
            } else if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(state);
        }
        
        if (zip != null && !zip.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(zip);
        }
        
        return sb.toString();
    }
    
    /**
     * Apply filter to driver list
     */
    private void applyDriverFilter(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            filteredDrivers.setPredicate(driver -> 
                driver.getStatus() == DispatcherDriverStatus.Status.AVAILABLE);
        } else {
            String lowerSearchText = searchText.toLowerCase();
            filteredDrivers.setPredicate(driver -> 
                driver.getStatus() == DispatcherDriverStatus.Status.AVAILABLE &&
                (driver.getDriverName().toLowerCase().contains(lowerSearchText) ||
                 (driver.getLocation() != null && 
                  driver.getLocation().toLowerCase().contains(lowerSearchText)))
            );
        }
    }
    
    /**
     * Calculate driver compatibility for a given load
     */
    private void calculateDriverCompatibility(Load load) {
        driverCompatibilityMap.clear();
        
        for (DispatcherDriverStatus driverStatus : controller.getDriverStatuses()) {
            Driver driver = driverStatus.getDriver();
            
            // Calculate individual factors
            double locationFactor = calculateLocationFactor(driverStatus, load);
            double hoursAvailableFactor = calculateHoursAvailableFactor(driver);
            double equipmentFactor = calculateEquipmentFactor(driverStatus, load);
            double experienceFactor = calculateExperienceFactor(driver, load);
            
            // Weighted average of factors
            double compatibility = locationFactor * 0.4 + 
                                  hoursAvailableFactor * 0.3 + 
                                  equipmentFactor * 0.2 + 
                                  experienceFactor * 0.1;
            
            driverCompatibilityMap.put(driver, compatibility);
        }
        
        // Refresh driver list to update compatibility indicators
    }
    
    /**
     * Calculate location compatibility factor
     */
    private double calculateLocationFactor(DispatcherDriverStatus driverStatus, Load load) {
        // In a real system, this would use actual distance calculations
        // For demo purposes, use a random value between 0 and 1
        return 0.3 + Math.random() * 0.7;
    }
    
    /**
     * Calculate hours available compatibility factor
     */
    private double calculateHoursAvailableFactor(Driver driver) {
        // In a real system, this would calculate available driving hours
        // For demo purposes, use a random value between 0 and 1
        return 0.5 + Math.random() * 0.5;
    }
    
    /**
     * Calculate equipment compatibility factor
     */
    private double calculateEquipmentFactor(DispatcherDriverStatus driverStatus, Load load) {
        // In a real system, this would check if driver has appropriate equipment
        // For demo purposes, use a random value between 0 and 1
        return 0.7 + Math.random() * 0.3;
    }
    
    /**
     * Calculate driver experience compatibility factor
     */
    private double calculateExperienceFactor(Driver driver, Load load) {
        // In a real system, this would check driver's experience with this type of load
        // For demo purposes, use a random value between 0 and 1
        return 0.4 + Math.random() * 0.6;
    }
    
    /**
     * Update compatibility indicator in summary
     */
    private void updateCompatibilityIndicator(ProgressBar compatibilityBar) {
        Load load = loadCombo.getValue();
        DispatcherDriverStatus driverStatus = driverCombo.getValue();
        
        if (load != null && driverStatus != null && driverCompatibilityMap.containsKey(driverStatus.getDriver())) {
            double compatibility = driverCompatibilityMap.get(driverStatus.getDriver());
            compatibilityBar.setProgress(compatibility);
            
            // Set color based on compatibility
            if (compatibility >= 0.8) {
                compatibilityBar.setStyle("-fx-accent: green;");
            } else if (compatibility >= 0.5) {
                compatibilityBar.setStyle("-fx-accent: orange;");
            } else {
                compatibilityBar.setStyle("-fx-accent: red;");
            }
        } else {
            compatibilityBar.setProgress(0);
            compatibilityBar.setStyle("");
        }
    }
    
    /**
     * Simulated calculation of hours worked today
     */
    private double calculateHoursToday(Driver driver) {
        // In a real system, this would calculate actual hours worked
        return 4.0 + Math.random() * 6.0;
    }
    
    /**
     * Simulated calculation of hours worked this week
     */
    private double calculateHoursThisWeek(Driver driver) {
        // In a real system, this would calculate actual hours worked
        return 20.0 + Math.random() * 40.0;
    }
    
    /**
     * Simulated calculation of days on road
     */
    private int calculateDaysOnRoad(Driver driver) {
        // In a real system, this would calculate actual days
        return (int) (1 + Math.random() * 5);
    }
    
    /**
     * Validate current selections
     */
    private void validateSelections() {
        Load load = loadCombo.getValue();
        DispatcherDriverStatus driverStatus = driverCombo.getValue();
        
        if (load != null && driverStatus != null) {
            // Check for compatibility issues
            Driver driver = driverStatus.getDriver();
            
            if (driverStatus.getStatus() != DispatcherDriverStatus.Status.AVAILABLE) {
                // Driver not available - warn but don't prevent assignment
                showWarning("Driver Status Warning", 
                    "Selected driver is not currently available (Status: " + 
                    driverStatus.getStatus().getDisplayName() + ").\n\n" + 
                    "You can still assign the load, but the driver's status will be updated."
                );
            }
        }
    }
    
    /**
     * Perform the load assignment
     */
    private boolean performAssignment() {
        Load selectedLoad = loadCombo.getValue();
        DispatcherDriverStatus selectedDriver = driverCombo.getValue();
        
        if (selectedLoad == null || selectedDriver == null) {
            showError("Assignment Error", "Please select both a load and a driver.");
            return false;
        }
        
        try {
            logger.info("Assigning load {} to driver {}", 
                selectedLoad.getLoadNumber(), selectedDriver.getDriverName());
            
            controller.assignLoad(selectedDriver.getDriver(), selectedLoad);
            
            // Update driver status if requested
            if (updateStatusCheck.isSelected()) {
                controller.updateDriverStatus(
                    selectedDriver, 
                    DispatcherDriverStatus.Status.ON_ROAD, 
                    "En route to " + selectedLoad.getOriginCity(), 
                    selectedLoad.getPickupDate().atTime(8, 0), 
                    notesArea.getText()
                );
            }
            
            // Send notification if requested
            if (notifyDriverCheck.isSelected()) {
                LocalDateTime notifyTime = LocalDateTime.of(
                    notifyDatePicker.getValue(),
                    LocalTime.of(notifyHourSpinner.getValue(), notifyMinuteSpinner.getValue())
                );
                
                controller.sendStatusNotification(selectedDriver, 
                    "Load assignment: " + selectedLoad.getLoadNumber());
            }

            showSuccess("Assignment Successful", String.format(
                "Load %s has been assigned to %s",
                selectedLoad.getLoadNumber(),
                selectedDriver.getDriverName()
            ));

            return true;
        } catch (Exception e) {
            logger.error("Failed to assign load", e);
            showError("Assignment Error", "Failed to assign load: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Refresh data from controller
     */
    private void refreshData() {
        logger.info("Refreshing assignment data");
        
        controller.refreshData();
        
        // Clear and reload data
        Load selectedLoad = loadCombo.getValue();
        DispatcherDriverStatus selectedDriver = driverCombo.getValue();
        
        // Reset filters and reapply
        String loadSearchText = loadSearchField.getText();
        String driverSearchText = driverSearchField.getText();
        
        loadSearchField.clear();
        driverSearchField.clear();
        
        // Reset and reapply filters
        Platform.runLater(() -> {
            loadSearchField.setText(loadSearchText);
            driverSearchField.setText(driverSearchText);
            
            // Try to restore selections
            if (selectedLoad != null) {
                for (Load load : filteredLoads) {
                    if (load.getLoadId().equals(selectedLoad.getLoadId())) {
                        loadCombo.setValue(load);
                        break;
                    }
                }
            }
            
            if (selectedDriver != null) {
                for (DispatcherDriverStatus driver : filteredDrivers) {
                    if (driver.getDriver().getDriverId().equals(selectedDriver.getDriver().getDriverId())) {
                        driverCombo.setValue(driver);
                        break;
                    }
                }
            }
            
            // Update load count
            loadCountLabel.setText(filteredLoads.size() + " loads available");
            
            // Update driver count
            driverCountLabel.setText(filteredDrivers.size() + " drivers available");
            
            // Recalculate compatibility if load is selected
            if (loadCombo.getValue() != null) {
                calculateDriverCompatibility(loadCombo.getValue());
            }
        });
    }
    
    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Show warning dialog
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Show success dialog
     */
    private void showSuccess(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}