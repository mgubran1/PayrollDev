package com.company.payroll.dispatcher;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced dialog for batch updating driver statuses with
 * validation, templates, and real-time preview
 * 
 * @author Payroll System
 * @version 2.0
 */
public class UpdateStatusDialog extends Dialog<Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(UpdateStatusDialog.class);
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
    
    // Core components
    private final DispatcherController controller;
    private final SimpleBooleanProperty hasChanges = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty isValid = new SimpleBooleanProperty(false);
    
    // UI Components - Driver Selection
    private TextField searchField;
    private ListView<DispatcherDriverStatus> driverListView;
    private FilteredList<DispatcherDriverStatus> filteredDrivers;
    private Label selectionCountLabel;
    private CheckBox selectAllCheck;
    
    // UI Components - Status Update
    private ToggleGroup statusGroup;
    private Map<DispatcherDriverStatus.Status, RadioButton> statusRadios;
    private ComboBox<StatusTemplate> templateCombo;
    private TextField locationField;
    private ComboBox<String> locationPresetCombo;
    private CheckBox updateLocationCheck;
    
    // UI Components - ETA
    private CheckBox updateETACheck;
    private DatePicker etaDatePicker;
    private Spinner<Integer> hourSpinner;
    private Spinner<Integer> minuteSpinner;
    private HBox quickTimeButtons;
    
    // UI Components - Notes
    private TextArea notesArea;
    private Label charCountLabel;
    private CheckBox appendNotesCheck;
    
    // UI Components - Options
    private CheckBox notifyDispatchCheck;
    private CheckBox updateLoadStatusCheck;
    private CheckBox priorityUpdateCheck;
    private ComboBox<String> reasonCombo;
    
    // UI Components - Preview
    private VBox previewContainer;
    private Label affectedCountLabel;
    private TableView<UpdatePreview> previewTable;
    
    // Data
    private final List<StatusUpdate> pendingUpdates = new ArrayList<>();
    private Timeline validationTimeline;
    
    // Status templates
    private static class StatusTemplate {
        private final String name;
        private final DispatcherDriverStatus.Status status;
        private final String locationTemplate;
        private final Integer etaMinutes;
        private final String noteTemplate;
        
        public StatusTemplate(String name, DispatcherDriverStatus.Status status, 
                            String locationTemplate, Integer etaMinutes, String noteTemplate) {
            this.name = name;
            this.status = status;
            this.locationTemplate = locationTemplate;
            this.etaMinutes = etaMinutes;
            this.noteTemplate = noteTemplate;
        }
        
        @Override
        public String toString() { return name; }
    }
    
    public UpdateStatusDialog(DispatcherController controller, Window owner) {
        this.controller = controller;
        
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Batch Update Driver Status");
        setResizable(true);
        
        // Apply styling
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/update-status.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("update-status-dialog");
        
        initializeUI();
        setupBindings();
        setupValidation();
        setupKeyboardShortcuts();
        loadTemplates();
    }
    
    private void initializeUI() {
        // Create main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPrefSize(900, 700);
        
        // Left panel - Driver selection
        VBox leftPanel = createDriverSelectionPanel();
        leftPanel.setPrefWidth(350);
        
        // Center panel - Update configuration
        VBox centerPanel = createUpdateConfigurationPanel();
        
        // Right panel - Preview
        VBox rightPanel = createPreviewPanel();
        rightPanel.setPrefWidth(350);
        
        // Add panels to main layout
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(centerPanel);
        mainLayout.setRight(rightPanel);
        
        getDialogPane().setContent(mainLayout);
        
        // Dialog buttons
        ButtonType updateButton = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        getDialogPane().getButtonTypes().addAll(updateButton, cancelButton);
        
        // Configure update button
        Button updateBtn = (Button) getDialogPane().lookupButton(updateButton);
        updateBtn.setDefaultButton(true);
        updateBtn.disableProperty().bind(isValid.not());
        
        // Result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == updateButton) {
                return performUpdate();
            }
            return false;
        });
    }
    
    private VBox createDriverSelectionPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.getStyleClass().add("selection-panel");
        
        // Header
        Label headerLabel = new Label("Select Drivers");
        headerLabel.getStyleClass().add("panel-header");
        
        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search drivers...");
        searchField.getStyleClass().add("search-field");
        
        Label searchIcon = new Label("🔍");
        searchIcon.getStyleClass().add("search-icon");
        
        HBox searchBox = new HBox(10, searchIcon, searchField);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        // Driver list
        ObservableList<DispatcherDriverStatus> allDrivers = controller.getDriverStatuses();
        filteredDrivers = new FilteredList<>(allDrivers);
        
        driverListView = new ListView<>(filteredDrivers);
        driverListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        driverListView.setPrefHeight(400);
        driverListView.setCellFactory(createDriverCellFactory());
        
        // Selection controls
        HBox selectionControls = new HBox(15);
        selectionControls.setAlignment(Pos.CENTER_LEFT);
        
        selectAllCheck = new CheckBox("Select All");
        selectAllCheck.setOnAction(e -> {
            if (selectAllCheck.isSelected()) {
                driverListView.getSelectionModel().selectAll();
            } else {
                driverListView.getSelectionModel().clearSelection();
            }
        });
        
        selectionCountLabel = new Label("0 selected");
        selectionCountLabel.getStyleClass().add("selection-count");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        selectionControls.getChildren().addAll(selectAllCheck, spacer, selectionCountLabel);
        
        // Quick filters
        HBox filterBox = createQuickFilters();
        
        panel.getChildren().addAll(
            headerLabel, searchBox, filterBox, 
            driverListView, selectionControls
        );
        
        return panel;
    }
    
    private HBox createQuickFilters() {
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        Label filterLabel = new Label("Filter:");
        
        ToggleButton allBtn = new ToggleButton("All");
        ToggleButton availableBtn = new ToggleButton("Available");
        ToggleButton onRoadBtn = new ToggleButton("On Road");
        ToggleButton offDutyBtn = new ToggleButton("Off Duty");
        
        ToggleGroup filterGroup = new ToggleGroup();
        allBtn.setToggleGroup(filterGroup);
        availableBtn.setToggleGroup(filterGroup);
        onRoadBtn.setToggleGroup(filterGroup);
        offDutyBtn.setToggleGroup(filterGroup);
        allBtn.setSelected(true);
        
        // Apply filters
        filterGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == allBtn) {
                filteredDrivers.setPredicate(null);
            } else if (selected == availableBtn) {
                filteredDrivers.setPredicate(d -> 
                    d.getStatus() == DispatcherDriverStatus.Status.AVAILABLE);
            } else if (selected == onRoadBtn) {
                filteredDrivers.setPredicate(d -> 
                                        d.getStatus() == DispatcherDriverStatus.Status.ON_ROAD ||
                    d.getStatus() == DispatcherDriverStatus.Status.LOADING ||
                    d.getStatus() == DispatcherDriverStatus.Status.UNLOADING);
            } else if (selected == offDutyBtn) {
                filteredDrivers.setPredicate(d -> 
                    d.getStatus() == DispatcherDriverStatus.Status.OFF_DUTY ||
                    d.getStatus() == DispatcherDriverStatus.Status.SLEEPER);
            }
        });
        
        filterBox.getChildren().addAll(filterLabel, allBtn, availableBtn, onRoadBtn, offDutyBtn);
        
        return filterBox;
    }
    
    private Callback<ListView<DispatcherDriverStatus>, ListCell<DispatcherDriverStatus>> createDriverCellFactory() {
        return listView -> new ListCell<DispatcherDriverStatus>() {
            @Override
            protected void updateItem(DispatcherDriverStatus item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox cellBox = new HBox(10);
                    cellBox.setAlignment(Pos.CENTER_LEFT);
                    
                    // Status indicator
                    Circle statusCircle = new Circle(6);
                    statusCircle.setFill(getStatusColor(item.getStatus()));
                    
                    // Driver info
                    VBox infoBox = new VBox(2);
                    Label nameLabel = new Label(item.getDriverName());
                    nameLabel.getStyleClass().add("driver-name");
                    
                    Label statusLabel = new Label(item.getStatus().getDisplayName());
                    statusLabel.getStyleClass().add("driver-status");
                    
                    String location = item.getLocation();
                    if (location != null && !location.isEmpty()) {
                        Label locationLabel = new Label("📍 " + location);
                        locationLabel.getStyleClass().add("driver-location");
                        infoBox.getChildren().addAll(nameLabel, statusLabel, locationLabel);
                    } else {
                        infoBox.getChildren().addAll(nameLabel, statusLabel);
                    }
                    
                    cellBox.getChildren().addAll(statusCircle, infoBox);
                    setGraphic(cellBox);
                }
            }
        };
    }
    
    private VBox createUpdateConfigurationPanel() {
        VBox panel = new VBox(20);
        panel.setPadding(new Insets(20));
        panel.getStyleClass().add("config-panel");
        
        // Header
        Label headerLabel = new Label("Update Configuration");
        headerLabel.getStyleClass().add("panel-header");
        
        // Template selection
        VBox templateBox = createTemplateSection();
        
        // Status selection
        VBox statusBox = createStatusSection();
        
        // Location update
        VBox locationBox = createLocationSection();
        
        // ETA update
        VBox etaBox = createETASection();
        
        // Notes
        VBox notesBox = createNotesSection();
        
        // Options
        VBox optionsBox = createOptionsSection();
        
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        VBox scrollContent = new VBox(15);
        scrollContent.getChildren().addAll(
            templateBox, statusBox, locationBox, etaBox, notesBox, optionsBox
        );
        
        scrollPane.setContent(scrollContent);
        
        panel.getChildren().addAll(headerLabel, scrollPane);
        
        return panel;
    }
    
    private VBox createTemplateSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("config-section");
        
        Label sectionLabel = new Label("Quick Templates");
        sectionLabel.getStyleClass().add("section-label");
        
        templateCombo = new ComboBox<>();
        templateCombo.setPromptText("Select a template...");
        templateCombo.setPrefWidth(250);
        templateCombo.setConverter(new StringConverter<StatusTemplate>() {
            @Override
            public String toString(StatusTemplate template) {
                return template != null ? template.name : "";
            }
            
            @Override
            public StatusTemplate fromString(String string) {
                return null;
            }
        });
        
        templateCombo.setOnAction(e -> applyTemplate());
        
        section.getChildren().addAll(sectionLabel, templateCombo);
        
        return section;
    }
    
    private VBox createStatusSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("config-section");
        
        Label sectionLabel = new Label("New Status *");
        sectionLabel.getStyleClass().add("section-label");
        
        statusGroup = new ToggleGroup();
        statusRadios = new HashMap<>();
        
        GridPane statusGrid = new GridPane();
        statusGrid.setHgap(15);
        statusGrid.setVgap(10);
        
        int col = 0, row = 0;
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            RadioButton radio = new RadioButton(status.getDisplayName());
            radio.setToggleGroup(statusGroup);
            radio.setUserData(status);
            statusRadios.put(status, radio);
            
            statusGrid.add(radio, col, row);
            
            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }
        
        section.getChildren().addAll(sectionLabel, statusGrid);
        
        return section;
    }
    
    private VBox createLocationSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("config-section");
        
        updateLocationCheck = new CheckBox("Update Location");
        updateLocationCheck.getStyleClass().add("section-label");
        
        HBox locationBox = new HBox(10);
        locationBox.setAlignment(Pos.CENTER_LEFT);
        
        locationField = new TextField();
        locationField.setPromptText("Enter location...");
        locationField.setPrefWidth(200);
        locationField.setDisable(true);
        
        locationPresetCombo = new ComboBox<>();
        locationPresetCombo.getItems().addAll(
            "En Route", "At Shipper", "At Receiver", "Rest Area",
            "Truck Stop", "Home Terminal", "Breakdown"
        );
        locationPresetCombo.setPrefWidth(150);
        locationPresetCombo.setDisable(true);
        locationPresetCombo.setOnAction(e -> {
            String selected = locationPresetCombo.getValue();
            if (selected != null) {
                locationField.setText(selected);
            }
        });
        
        Button gpsBtn = new Button("📍 Current GPS");
        gpsBtn.setDisable(true);
        gpsBtn.setOnAction(e -> useCurrentGPS());
        
        locationBox.getChildren().addAll(locationField, locationPresetCombo, gpsBtn);
        
        // Bind enable state
        locationField.disableProperty().bind(updateLocationCheck.selectedProperty().not());
        locationPresetCombo.disableProperty().bind(updateLocationCheck.selectedProperty().not());
        gpsBtn.disableProperty().bind(updateLocationCheck.selectedProperty().not());
        
        section.getChildren().addAll(updateLocationCheck, locationBox);
        
        return section;
    }
    
    private VBox createETASection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("config-section");
        
        updateETACheck = new CheckBox("Update ETA");
        updateETACheck.getStyleClass().add("section-label");
        
        GridPane etaGrid = new GridPane();
        etaGrid.setHgap(10);
        etaGrid.setVgap(10);
        
        // Date picker
        Label dateLabel = new Label("Date:");
        etaDatePicker = new DatePicker(LocalDate.now());
        etaDatePicker.setPrefWidth(150);
        etaDatePicker.setDisable(true);
        
        // Time spinners
        Label timeLabel = new Label("Time:");
        hourSpinner = new Spinner<>(0, 23, LocalTime.now().getHour());
        hourSpinner.setPrefWidth(70);
        hourSpinner.setDisable(true);
        hourSpinner.setEditable(true);
        
        minuteSpinner = new Spinner<>(0, 59, LocalTime.now().getMinute());
        minuteSpinner.setPrefWidth(70);
        minuteSpinner.setDisable(true);
        minuteSpinner.setEditable(true);
        
        Label colonLabel = new Label(":");
        colonLabel.setStyle("-fx-font-weight: bold;");
        
        HBox timeBox = new HBox(5, hourSpinner, colonLabel, minuteSpinner);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        
        etaGrid.add(dateLabel, 0, 0);
        etaGrid.add(etaDatePicker, 1, 0);
        etaGrid.add(timeLabel, 0, 1);
        etaGrid.add(timeBox, 1, 1);
        
        // Quick time buttons
        quickTimeButtons = new HBox(10);
        quickTimeButtons.setAlignment(Pos.CENTER_LEFT);
        
        Button nowBtn = new Button("Now");
        nowBtn.setOnAction(e -> setETAToNow());
        
        Button in30Btn = new Button("+30 min");
        in30Btn.setOnAction(e -> addMinutesToETA(30));
        
        Button in1HrBtn = new Button("+1 hr");
        in1HrBtn.setOnAction(e -> addMinutesToETA(60));
        
        Button in2HrBtn = new Button("+2 hrs");
        in2HrBtn.setOnAction(e -> addMinutesToETA(120));
        
        quickTimeButtons.getChildren().addAll(nowBtn, in30Btn, in1HrBtn, in2HrBtn);
        quickTimeButtons.setDisable(true);
        
        // Bind enable state
        etaDatePicker.disableProperty().bind(updateETACheck.selectedProperty().not());
        hourSpinner.disableProperty().bind(updateETACheck.selectedProperty().not());
        minuteSpinner.disableProperty().bind(updateETACheck.selectedProperty().not());
        quickTimeButtons.disableProperty().bind(updateETACheck.selectedProperty().not());
        
        section.getChildren().addAll(updateETACheck, etaGrid, quickTimeButtons);
        
        return section;
    }
    
    private VBox createNotesSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("config-section");
        
        Label sectionLabel = new Label("Notes");
        sectionLabel.getStyleClass().add("section-label");
        
        notesArea = new TextArea();
        notesArea.setPrefRowCount(4);
        notesArea.setPromptText("Enter update notes...");
        notesArea.setWrapText(true);
        
        HBox notesOptions = new HBox(15);
        notesOptions.setAlignment(Pos.CENTER_LEFT);
        
        appendNotesCheck = new CheckBox("Append to existing notes");
        appendNotesCheck.setSelected(true);
        
        charCountLabel = new Label("0 / 500 characters");
        charCountLabel.getStyleClass().add("char-count");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        notesOptions.getChildren().addAll(appendNotesCheck, spacer, charCountLabel);
        
        // Character count binding
        notesArea.textProperty().addListener((obs, old, text) -> {
            int length = text != null ? text.length() : 0;
            charCountLabel.setText(length + " / 500 characters");
            if (length > 500) {
                charCountLabel.setTextFill(Color.RED);
                notesArea.setText(text.substring(0, 500));
            } else {
                charCountLabel.setTextFill(Color.GRAY);
            }
        });
        
        section.getChildren().addAll(sectionLabel, notesArea, notesOptions);
        
        return section;
    }
    
    private VBox createOptionsSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("config-section");
        
        Label sectionLabel = new Label("Additional Options");
        sectionLabel.getStyleClass().add("section-label");
        
        notifyDispatchCheck = new CheckBox("Send notification to dispatch");
        notifyDispatchCheck.setSelected(true);
        
        updateLoadStatusCheck = new CheckBox("Update associated load status");
        
        priorityUpdateCheck = new CheckBox("Mark as priority update");
        
        HBox reasonBox = new HBox(10);
        reasonBox.setAlignment(Pos.CENTER_LEFT);
        
        Label reasonLabel = new Label("Reason:");
        reasonCombo = new ComboBox<>();
        reasonCombo.getItems().addAll(
            "Scheduled update", "Traffic delay", "Weather delay",
            "Mechanical issue", "Customer request", "Route change",
            "Rest break", "Fuel stop", "Other"
        );
        reasonCombo.setPrefWidth(200);
        reasonCombo.setValue("Scheduled update");
        
        reasonBox.getChildren().addAll(reasonLabel, reasonCombo);
        
        section.getChildren().addAll(
            sectionLabel, notifyDispatchCheck, updateLoadStatusCheck,
            priorityUpdateCheck, reasonBox
        );
        
        return section;
    }
    
    private VBox createPreviewPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.getStyleClass().add("preview-panel");
        
        // Header
        Label headerLabel = new Label("Update Preview");
        headerLabel.getStyleClass().add("panel-header");
        
        // Summary
        affectedCountLabel = new Label("0 drivers will be updated");
        affectedCountLabel.getStyleClass().add("affected-count");
        
        // Preview table
        previewTable = new TableView<>();
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        previewTable.setPrefHeight(400);
        
        TableColumn<UpdatePreview, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(data -> data.getValue().driverNameProperty());
        
        TableColumn<UpdatePreview, String> currentCol = new TableColumn<>("Current Status");
        currentCol.setCellValueFactory(data -> data.getValue().currentStatusProperty());
        
        TableColumn<UpdatePreview, String> newCol = new TableColumn<>("New Status");
        newCol.setCellValueFactory(data -> data.getValue().newStatusProperty());
        
        TableColumn<UpdatePreview, String> changesCol = new TableColumn<>("Changes");
        changesCol.setCellValueFactory(data -> data.getValue().changesProperty());
        
        previewTable.getColumns().addAll(driverCol, currentCol, newCol, changesCol);
        
        // Warnings
        VBox warningsBox = new VBox(5);
        warningsBox.getStyleClass().add("warnings-box");
        
        Label warningsLabel = new Label("⚠ Warnings:");
        warningsLabel.getStyleClass().add("warnings-label");
        
        VBox warningsList = new VBox(3);
        warningsList.getStyleClass().add("warnings-list");
        
        warningsBox.getChildren().addAll(warningsLabel, warningsList);
        
        panel.getChildren().addAll(headerLabel, affectedCountLabel, previewTable, warningsBox);
        
        return panel;
    }
    
    private void setupBindings() {
        // Update selection count
        driverListView.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<DispatcherDriverStatus>) c -> {
                int count = driverListView.getSelectionModel().getSelectedItems().size();
                selectionCountLabel.setText(count + " selected");
                affectedCountLabel.setText(count + " driver" + (count != 1 ? "s" : "") + " will be updated");
                updatePreview();
            }
        );
        
        // Search filter
        searchField.textProperty().addListener((obs, old, text) -> {
            if (text != null && !text.isEmpty()) {
                filteredDrivers.setPredicate(driver -> 
                    driver.getDriverName().toLowerCase().contains(text.toLowerCase()) ||
                    (driver.getLocation() != null && 
                     driver.getLocation().toLowerCase().contains(text.toLowerCase()))
                );
            } else {
                filteredDrivers.setPredicate(null);
            }
        });
        
        // Update preview on configuration changes
        statusGroup.selectedToggleProperty().addListener((obs, old, toggle) -> {
            updatePreview();
            validateForm();
        });
        
        updateLocationCheck.selectedProperty().addListener((obs, old, selected) -> updatePreview());
        locationField.textProperty().addListener((obs, old, text) -> updatePreview());
        
        updateETACheck.selectedProperty().addListener((obs, old, selected) -> updatePreview());
        etaDatePicker.valueProperty().addListener((obs, old, date) -> updatePreview());
        hourSpinner.valueProperty().addListener((obs, old, hour) -> updatePreview());
        minuteSpinner.valueProperty().addListener((obs, old, minute) -> updatePreview());
        
        notesArea.textProperty().addListener((obs, old, text) -> {
            hasChanges.set(true);
            updatePreview();
        });
    }
    
    private void setupValidation() {
        validationTimeline = new Timeline(new KeyFrame(Duration.millis(300), e -> validateForm()));
        validationTimeline.setCycleCount(1);
        
        // Trigger validation on relevant changes
        statusGroup.selectedToggleProperty().addListener((obs, old, toggle) -> 
            validationTimeline.playFromStart());
        driverListView.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<DispatcherDriverStatus>) c -> 
                validationTimeline.playFromStart()
        );
    }
    
    private void setupKeyboardShortcuts() {
        // Ctrl+A - Select all drivers
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN),
            () -> {
                if (driverListView.isFocused()) {
                    driverListView.getSelectionModel().selectAll();
                }
            }
        );
        
        // Ctrl+U - Focus update button
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.U, KeyCombination.CONTROL_DOWN),
            () -> {
                Button updateBtn = (Button) getDialogPane().lookupButton(
                    getDialogPane().getButtonTypes().get(0)
                );
                updateBtn.requestFocus();
            }
        );
        
        // Escape - Clear selection
        getDialogPane().getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.ESCAPE),
            () -> driverListView.getSelectionModel().clearSelection()
        );
    }
    
    private void loadTemplates() {
        ObservableList<StatusTemplate> templates = FXCollections.observableArrayList(
            new StatusTemplate("Start Shift", 
                DispatcherDriverStatus.Status.AVAILABLE, 
                "Home Terminal", null, 
                "Starting shift"),
                
            new StatusTemplate("Begin Route", 
                DispatcherDriverStatus.Status.ON_ROAD, 
                "En Route", null, 
                "Departing for pickup"),
                
            new StatusTemplate("At Pickup", 
                DispatcherDriverStatus.Status.LOADING, 
                "At Shipper", 30, 
                "Arrived at shipper for loading"),
                
            new StatusTemplate("At Delivery", 
                DispatcherDriverStatus.Status.UNLOADING, 
                "At Receiver", 45, 
                "Arrived at receiver for unloading"),
                
            new StatusTemplate("Rest Break", 
                DispatcherDriverStatus.Status.BREAK, 
                "Rest Area", 30, 
                "Taking required rest break"),
                
            new StatusTemplate("End of Day", 
                DispatcherDriverStatus.Status.OFF_DUTY, 
                "Truck Stop", null, 
                "End of duty hours"),
                
            new StatusTemplate("Home Time", 
                DispatcherDriverStatus.Status.OFF_DUTY, 
                "Home Terminal", null, 
                "Returning home - off duty")
        );
        
        templateCombo.setItems(templates);
    }
    
    private void applyTemplate() {
        StatusTemplate template = templateCombo.getValue();
        if (template == null) return;
        
        // Apply status
        RadioButton statusRadio = statusRadios.get(template.status);
        if (statusRadio != null) {
            statusRadio.setSelected(true);
        }
        
        // Apply location
        if (template.locationTemplate != null) {
            updateLocationCheck.setSelected(true);
            locationField.setText(template.locationTemplate);
        }
        
        // Apply ETA
        if (template.etaMinutes != null) {
            updateETACheck.setSelected(true);
            addMinutesToETA(template.etaMinutes);
        }
        
        // Apply notes
        if (template.noteTemplate != null) {
            notesArea.setText(template.noteTemplate);
        }
    }
    
    private void validateForm() {
        List<String> errors = new ArrayList<>();
        
        // Check driver selection
        if (driverListView.getSelectionModel().getSelectedItems().isEmpty()) {
            errors.add("No drivers selected");
        }
        
        // Check status selection
        if (statusGroup.getSelectedToggle() == null) {
            errors.add("No status selected");
        }
        
        // Check location if enabled
        if (updateLocationCheck.isSelected() && 
            (locationField.getText() == null || locationField.getText().trim().isEmpty())) {
            errors.add("Location is required when update location is checked");
        }
        
        isValid.set(errors.isEmpty());
    }
    
    private void updatePreview() {
        ObservableList<UpdatePreview> previews = FXCollections.observableArrayList();
        List<DispatcherDriverStatus> selectedDrivers = 
            driverListView.getSelectionModel().getSelectedItems();
        
        Toggle selectedToggle = statusGroup.getSelectedToggle();
        if (selectedToggle == null || selectedDrivers.isEmpty()) {
            previewTable.setItems(previews);
            return;
        }
        
        DispatcherDriverStatus.Status newStatus = 
            (DispatcherDriverStatus.Status) selectedToggle.getUserData();
        
        for (DispatcherDriverStatus driver : selectedDrivers) {
            UpdatePreview preview = new UpdatePreview();
            preview.setDriverName(driver.getDriverName());
            preview.setCurrentStatus(driver.getStatus().getDisplayName());
            preview.setNewStatus(newStatus.getDisplayName());
            
            List<String> changes = new ArrayList<>();
            changes.add("Status: " + driver.getStatus().getDisplayName() + 
                       " → " + newStatus.getDisplayName());
            
            if (updateLocationCheck.isSelected()) {
                String newLocation = locationField.getText();
                changes.add("Location: " + (driver.getLocation() != null ? 
                    driver.getLocation() : "N/A") + " → " + newLocation);
            }
            
            if (updateETACheck.isSelected()) {
                LocalDateTime eta = LocalDateTime.of(
                    etaDatePicker.getValue(),
                    LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                );
                changes.add("ETA: " + eta.format(DATETIME_FORMAT));
            }
            
            if (!notesArea.getText().isEmpty()) {
                changes.add("Notes: " + (appendNotesCheck.isSelected() ? "Appended" : "Replaced"));
            }
            
            preview.setChanges(String.join(", ", changes));
            previews.add(preview);
        }
        
        previewTable.setItems(previews);
    }
    
    private boolean performUpdate() {
        List<DispatcherDriverStatus> selectedDrivers = 
            new ArrayList<>(driverListView.getSelectionModel().getSelectedItems());
        
        if (selectedDrivers.isEmpty() || statusGroup.getSelectedToggle() == null) {
            return false;
        }
        
        DispatcherDriverStatus.Status newStatus = 
            (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData();
        String location = updateLocationCheck.isSelected() ? locationField.getText() : null;
        LocalDateTime eta = null;
        
        if (updateETACheck.isSelected()) {
            eta = LocalDateTime.of(
                etaDatePicker.getValue(),
                LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
            );
        }
        
        String notes = notesArea.getText();
        boolean append = appendNotesCheck.isSelected();
        
        // Create progress dialog
        ProgressDialog progressDialog = new ProgressDialog(
            "Updating Drivers", 
            "Updating " + selectedDrivers.size() + " driver(s)..."
        );
        progressDialog.show();
        
        // Perform updates in background
        Task<Boolean> updateTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                int total = selectedDrivers.size();
                int current = 0;
                
                for (DispatcherDriverStatus driver : selectedDrivers) {
                    updateMessage("Updating " + driver.getDriverName() + "...");
                    updateProgress(current, total);
                    
                    try {
                        // Build complete notes
                        String finalNotes = notes;
                        if (append && driver.getNotes() != null && !driver.getNotes().isEmpty()) {
                            finalNotes = driver.getNotes() + "\n" + notes;
                        }
                        
                        // Add timestamp and user
                        if (!finalNotes.isEmpty()) {
                            finalNotes += String.format("\n[%s - %s]", 
                                LocalDateTime.now().format(DATETIME_FORMAT),
                                System.getProperty("user.name", "Unknown")
                            );
                        }
                        
                        // Perform update
                        controller.updateDriverStatus(
                            driver, newStatus, location, eta, finalNotes
                        );
                        
                        // Log update
                        logger.info("Updated driver {} status to {}", 
                            driver.getDriverName(), newStatus);
                        
                        // Send notifications if enabled
                        if (notifyDispatchCheck.isSelected()) {
                            sendNotification(driver, newStatus, reasonCombo.getValue());
                        }
                        
                        // Update load status if enabled
                        if (updateLoadStatusCheck.isSelected()) {
                            updateAssociatedLoadStatus(driver, newStatus);
                        }
                        
                        current++;
                        
                        // Small delay to prevent overwhelming the system
                        Thread.sleep(100);
                        
                    } catch (Exception e) {
                        logger.error("Failed to update driver: " + driver.getDriverName(), e);
                        throw e;
                    }
                }
                
                updateProgress(total, total);
                return true;
            }
        };
        
        progressDialog.progressProperty().bind(updateTask.progressProperty());
        progressDialog.messageProperty().bind(updateTask.messageProperty());
        
        updateTask.setOnSucceeded(e -> {
            progressDialog.close();
            showSuccessAlert(selectedDrivers.size());
        });
        
        updateTask.setOnFailed(e -> {
            progressDialog.close();
            showErrorAlert(updateTask.getException());
        });
        
        new Thread(updateTask).start();
        
        return true;
    }
    
    // Helper methods
    
    private Color getStatusColor(DispatcherDriverStatus.Status status) {
        switch (status) {
            case AVAILABLE: return Color.GREEN;
            case ON_ROAD: return Color.BLUE;
            case LOADING: return Color.ORANGE;
            case UNLOADING: return Color.ORANGE;
            case BREAK: return Color.YELLOW;
            case OFF_DUTY: return Color.GRAY;
            case SLEEPER: return Color.DARKGRAY;
            default: return Color.BLACK;
        }
    }
    
    private void useCurrentGPS() {
        // Simulate GPS location
        locationField.setText("GPS: 41.8781° N, 87.6298° W");
    }
    
    private void setETAToNow() {
        LocalDateTime now = LocalDateTime.now();
        etaDatePicker.setValue(now.toLocalDate());
        hourSpinner.getValueFactory().setValue(now.getHour());
        minuteSpinner.getValueFactory().setValue(now.getMinute());
    }
    
    private void addMinutesToETA(int minutes) {
        LocalDateTime eta = LocalDateTime.now().plusMinutes(minutes);
        etaDatePicker.setValue(eta.toLocalDate());
        hourSpinner.getValueFactory().setValue(eta.getHour());
        minuteSpinner.getValueFactory().setValue(eta.getMinute());
    }
    
    private void sendNotification(DispatcherDriverStatus driver, 
                                 DispatcherDriverStatus.Status newStatus, 
                                 String reason) {
        // Implementation for sending notifications
        logger.info("Notification sent for driver {} status change to {}", 
            driver.getDriverName(), newStatus);
    }
    
    private void updateAssociatedLoadStatus(DispatcherDriverStatus driver,
                                           DispatcherDriverStatus.Status newStatus) {
        // Implementation for updating associated load status
        logger.info("Updated associated load status for driver {}", driver.getDriverName());
    }
    
    private void showSuccessAlert(int count) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Update Successful");
        alert.setHeaderText(null);
        alert.setContentText(String.format("Successfully updated %d driver%s", 
            count, count != 1 ? "s" : ""));
        alert.showAndWait();
    }
    
    private void showErrorAlert(Throwable error) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Update Failed");
        alert.setHeaderText("Failed to update driver status");
        alert.setContentText(error.getMessage());
        alert.showAndWait();
    }
    
    // Inner classes
    
    private static class UpdatePreview {
        private final SimpleStringProperty driverName = new SimpleStringProperty();
        private final SimpleStringProperty currentStatus = new SimpleStringProperty();
        private final SimpleStringProperty newStatus = new SimpleStringProperty();
        private final SimpleStringProperty changes = new SimpleStringProperty();
        
        public SimpleStringProperty driverNameProperty() { return driverName; }
        public void setDriverName(String value) { driverName.set(value); }
        
        public SimpleStringProperty currentStatusProperty() { return currentStatus; }
        public void setCurrentStatus(String value) { currentStatus.set(value); }
        
        public SimpleStringProperty newStatusProperty() { return newStatus; }
        public void setNewStatus(String value) { newStatus.set(value); }
        
        public SimpleStringProperty changesProperty() { return changes; }
        public void setChanges(String value) { changes.set(value); }
    }
    
    private static class StatusUpdate {
        private final DispatcherDriverStatus driver;
        private final DispatcherDriverStatus.Status newStatus;
        private final String location;
        private final LocalDateTime eta;
        private final String notes;
        
        public StatusUpdate(DispatcherDriverStatus driver, 
                          DispatcherDriverStatus.Status newStatus,
                          String location, LocalDateTime eta, String notes) {
            this.driver = driver;
            this.newStatus = newStatus;
            this.location = location;
            this.eta = eta;
            this.notes = notes;
        }
    }
    
    private static class ProgressDialog extends Dialog<Void> {
        private final ProgressBar progressBar;
        private final Label messageLabel;
        
        public ProgressDialog(String title, String message) {
            setTitle(title);
            setHeaderText(null);
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            
            messageLabel = new Label(message);
            progressBar = new ProgressBar();
            progressBar.setPrefWidth(300);
            
            content.getChildren().addAll(messageLabel, progressBar);
            
            getDialogPane().setContent(content);
            getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        }
        
        public ProgressBar getProgressBar() { return progressBar; }
        public Label getMessageLabel() { return messageLabel; }
        
        public javafx.beans.property.DoubleProperty progressProperty() {
            return progressBar.progressProperty();
        }
        
        public javafx.beans.property.StringProperty messageProperty() {
            return messageLabel.textProperty();
        }
    }
}