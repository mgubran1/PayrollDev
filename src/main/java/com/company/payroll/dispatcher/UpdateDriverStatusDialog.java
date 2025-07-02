package com.company.payroll.dispatcher;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.util.Duration;
import javafx.util.StringConverter;
import com.company.payroll.loads.Load;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Enhanced dialog for updating driver status with validation, real-time preview,
 * and comprehensive status management features
 * 
 * @author Payroll System
 * @version 2.0
 */
public class UpdateDriverStatusDialog extends Dialog<Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(UpdateDriverStatusDialog.class);
    
    // UI Constants
    private static final int DIALOG_WIDTH = 600;
    private static final int DIALOG_HEIGHT = 700;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("^[\\w\\s,.-]{3,100}$");
    
    // Core Components
    private final DispatcherDriverStatus driver;
    private final DispatcherController controller;
    
    // Form Controls
    private final ToggleGroup statusGroup;
    private final TextField locationField;
    private final DatePicker etaDatePicker;
    private final Spinner<Integer> hourSpinner;
    private final Spinner<Integer> minuteSpinner;
    private final TextArea notesArea;
    private final ComboBox<String> locationPresets;
    private final CheckBox notifyDispatchCheck;
    private final CheckBox updateLoadStatusCheck;
    
    // Validation and Preview
    private final Label validationLabel;
    private final VBox previewBox;
    private final SimpleBooleanProperty formValid;
    private final Map<TextField, Label> fieldValidationLabels;
    
    // Additional Features
    private final ListView<String> statusHistoryList;
    private final Label estimatedDurationLabel;
    private final ProgressBar completionProgress;
    
    public UpdateDriverStatusDialog(DispatcherDriverStatus driver, DispatcherController controller) {
        this.driver = driver;
        this.controller = controller;
        this.statusGroup = new ToggleGroup();
        this.locationField = new TextField();
        this.etaDatePicker = new DatePicker();
        this.hourSpinner = new Spinner<>(0, 23, 12);
        this.minuteSpinner = new Spinner<>(0, 59, 0, 5);
        this.notesArea = new TextArea();
        this.locationPresets = new ComboBox<>();
        this.notifyDispatchCheck = new CheckBox("Notify dispatch team");
        this.updateLoadStatusCheck = new CheckBox("Update associated load status");
        this.validationLabel = new Label();
        this.previewBox = new VBox(10);
        this.formValid = new SimpleBooleanProperty(false);
        this.fieldValidationLabels = new HashMap<>();
        this.statusHistoryList = new ListView<>();
        this.estimatedDurationLabel = new Label();
        this.completionProgress = new ProgressBar();
        
        initializeDialog();
        setupValidation();
        loadDriverData();
        updatePreview();
    }
    
    private void initializeDialog() {
        setTitle("Update Driver Status - " + driver.getDriverName());
        setHeaderText("Update status and location information");
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        
        // Apply styling
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/driver-status-dialog.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("driver-status-dialog");
        
        // Configure dialog size
        getDialogPane().setPrefSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        
        // Create main content
        TabPane tabPane = createTabPane();
        getDialogPane().setContent(tabPane);
        
        // Add buttons
        ButtonType updateButtonType = new ButtonType("Update Status", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);
        
        // Configure update button
        Node updateButton = getDialogPane().lookupButton(updateButtonType);
        updateButton.disableProperty().bind(formValid.not());
        
        // Set result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == updateButtonType) {
                return performUpdate();
            }
            return false;
        });
        
        // Add keyboard shortcuts
        setupKeyboardShortcuts();
    }
    
    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Status Update Tab
        Tab statusTab = new Tab("Status Update");
        statusTab.setContent(createStatusUpdateContent());
        
        // History Tab
        Tab historyTab = new Tab("Status History");
        historyTab.setContent(createHistoryContent());
        
        // Preview Tab
        Tab previewTab = new Tab("Preview");
        previewTab.setContent(createPreviewContent());
        
        tabPane.getTabs().addAll(statusTab, historyTab, previewTab);
        
        return tabPane;
    }
    
    private ScrollPane createStatusUpdateContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Current status display
        HBox currentStatusBox = createCurrentStatusDisplay();
        
        // Status selection
        TitledPane statusPane = createStatusSelectionPane();
        
        // Location update
        TitledPane locationPane = createLocationUpdatePane();
        
        // ETA configuration
        TitledPane etaPane = createETAConfigurationPane();
        
        // Additional options
        TitledPane optionsPane = createAdditionalOptionsPane();
        
        // Notes section
        TitledPane notesPane = createNotesPane();
        
        // Validation feedback
        validationLabel.setTextFill(Color.RED);
        validationLabel.setWrapText(true);
        
        content.getChildren().addAll(
            currentStatusBox,
            statusPane,
            locationPane,
            etaPane,
            optionsPane,
            notesPane,
            validationLabel
        );
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        return scrollPane;
    }
    
    private HBox createCurrentStatusDisplay() {
        HBox statusBox = new HBox(15);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(10));
        statusBox.getStyleClass().add("current-status-box");
        
        // Driver info
        VBox driverInfo = new VBox(5);
        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        Label truckLabel = new Label("Truck: " + driver.getTruckUnit() + 
            (driver.getTrailerNumber() != null ? " / Trailer: " + driver.getTrailerNumber() : ""));
        truckLabel.setStyle("-fx-text-fill: #666;");
        
        driverInfo.getChildren().addAll(nameLabel, truckLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Current status indicator
        VBox currentStatus = new VBox(5);
        currentStatus.setAlignment(Pos.CENTER);
        
        Circle statusIndicator = new Circle(15);
        statusIndicator.setFill(Color.web(driver.getStatus().getColor()));
        statusIndicator.setEffect(new DropShadow(5, Color.gray(0.3)));
        
        Label statusLabel = new Label(driver.getStatus().getDisplayName());
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        Label locationLabel = new Label(driver.getCurrentLocation());
        locationLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        
        currentStatus.getChildren().addAll(statusIndicator, statusLabel, locationLabel);
        
        statusBox.getChildren().addAll(driverInfo, spacer, currentStatus);
        
        return statusBox;
    }
    
    private TitledPane createStatusSelectionPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label instructionLabel = new Label("Select new status:");
        instructionLabel.setStyle("-fx-font-weight: bold;");
        
        FlowPane statusButtons = new FlowPane(10, 10);
        statusButtons.setPrefWrapLength(500);
        
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            RadioButton radioButton = createStatusRadioButton(status);
            statusButtons.getChildren().add(radioButton);
        }
        
        // Estimated duration for status
        HBox durationBox = new HBox(10);
        durationBox.setAlignment(Pos.CENTER_LEFT);
        Label durationLabel = new Label("Estimated duration:");
        estimatedDurationLabel.setStyle("-fx-font-weight: bold;");
        durationBox.getChildren().addAll(durationLabel, estimatedDurationLabel);
        
        content.getChildren().addAll(instructionLabel, statusButtons, durationBox);
        
        TitledPane pane = new TitledPane("Status Selection", content);
        pane.setExpanded(true);
        
        return pane;
    }
    
    private RadioButton createStatusRadioButton(DispatcherDriverStatus.Status status) {
        RadioButton rb = new RadioButton();
        rb.setToggleGroup(statusGroup);
        rb.setUserData(status);
        
        // Custom graphic for radio button
        HBox graphic = new HBox(5);
        graphic.setAlignment(Pos.CENTER_LEFT);
        
        Circle indicator = new Circle(6);
        indicator.setFill(Color.web(status.getColor()));
        
        Label label = new Label(status.getDisplayName());
        
        graphic.getChildren().addAll(indicator, label);
        rb.setGraphic(graphic);
        
        // Pre-select current status
        if (status == driver.getStatus()) {
            rb.setSelected(true);
        }
        
        // Update duration estimate on selection
        rb.setOnAction(e -> updateEstimatedDuration(status));
        
        return rb;
    }
    
    private TitledPane createLocationUpdatePane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        // Current location
        Label currentLabel = new Label("Current Location:");
        Label currentLocationLabel = new Label(driver.getCurrentLocation());
        currentLocationLabel.setStyle("-fx-font-style: italic;");
        
        // New location input
        Label newLabel = new Label("New Location:");
        locationField.setPrefWidth(300);
        locationField.setPromptText("Enter location or select from presets");
        
        // Location presets
        Label presetLabel = new Label("Quick Select:");
        setupLocationPresets();
        locationPresets.setPrefWidth(200);
        
        // Location validation
        Label locationValidation = new Label();
        locationValidation.setTextFill(Color.RED);
        locationValidation.setStyle("-fx-font-size: 10;");
        fieldValidationLabels.put(locationField, locationValidation);
        
        // GPS coordinates (if available)
        Button mapButton = new Button("📍 View on Map");
        mapButton.setOnAction(e -> showLocationOnMap());
        
        // Layout
        grid.add(currentLabel, 0, 0);
        grid.add(currentLocationLabel, 1, 0, 2, 1);
        
        grid.add(newLabel, 0, 1);
        grid.add(locationField, 1, 1);
        grid.add(mapButton, 2, 1);
        
        grid.add(presetLabel, 0, 2);
        grid.add(locationPresets, 1, 2);
        
        grid.add(locationValidation, 1, 3, 2, 1);
        
        TitledPane pane = new TitledPane("Location Update", grid);
        pane.setExpanded(true);
        
        return pane;
    }
    
    private TitledPane createETAConfigurationPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        // ETA date
        Label dateLabel = new Label("ETA Date:");
        etaDatePicker.setPrefWidth(150);
        etaDatePicker.setPromptText("Select date");
        
        // Custom date converter
        etaDatePicker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(DATE_FORMAT) : "";
            }
            
            @Override
            public LocalDate fromString(String string) {
                try {
                    return LocalDate.parse(string, DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        
        // ETA time
        Label timeLabel = new Label("ETA Time:");
        HBox timeBox = createTimeSelectionBox();
        
        // Quick time options
        Label quickLabel = new Label("Quick Set:");
        HBox quickTimeBox = createQuickTimeButtons();
        
        // Calculate from current location
        Button calculateButton = new Button("Calculate ETA");
        calculateButton.setOnAction(e -> calculateETA());
        
        // ETA preview
        Label etaPreviewLabel = new Label("Selected ETA:");
        Label etaPreview = new Label("Not set");
        etaPreview.setStyle("-fx-font-weight: bold;");
        
        // Update preview on changes
        Runnable updateETA = () -> {
            if (etaDatePicker.getValue() != null) {
                LocalDateTime eta = LocalDateTime.of(
                    etaDatePicker.getValue(),
                    LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                );
                etaPreview.setText(eta.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
                
                // Show time from now
                long hours = LocalDateTime.now().until(eta, java.time.temporal.ChronoUnit.HOURS);
                if (hours > 0) {
                    etaPreview.setText(etaPreview.getText() + 
                        String.format(" (in %d hours)", hours));
                }
            }
        };
        
        etaDatePicker.setOnAction(e -> updateETA.run());
        hourSpinner.valueProperty().addListener((obs, old, val) -> updateETA.run());
        minuteSpinner.valueProperty().addListener((obs, old, val) -> updateETA.run());
        
        // Layout
        grid.add(dateLabel, 0, 0);
        grid.add(etaDatePicker, 1, 0);
        grid.add(calculateButton, 2, 0);
        
        grid.add(timeLabel, 0, 1);
        grid.add(timeBox, 1, 1, 2, 1);
        
        grid.add(quickLabel, 0, 2);
        grid.add(quickTimeBox, 1, 2, 2, 1);
        
        grid.add(etaPreviewLabel, 0, 3);
        grid.add(etaPreview, 1, 3, 2, 1);
        
        TitledPane pane = new TitledPane("Estimated Time of Availability", grid);
        pane.setExpanded(false);
        
        return pane;
    }
    
    private HBox createTimeSelectionBox() {
        HBox timeBox = new HBox(5);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        
        // Configure spinners
        hourSpinner.setPrefWidth(60);
        hourSpinner.setEditable(true);
        hourSpinner.getValueFactory().setWrapAround(true);
        
        minuteSpinner.setPrefWidth(60);
        minuteSpinner.setEditable(true);
        minuteSpinner.getValueFactory().setWrapAround(true);
        
        // Add formatter to ensure two digits
        UnaryOperator<TextFormatter.Change> timeFilter = change -> {
            String text = change.getText();
            if (text.matches("[0-9]*")) {
                return change;
            }
            return null;
        };
        
        hourSpinner.getEditor().setTextFormatter(new TextFormatter<>(timeFilter));
        minuteSpinner.getEditor().setTextFormatter(new TextFormatter<>(timeFilter));
        
        Label colonLabel = new Label(":");
        colonLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        
        // Current time button
        Button nowButton = new Button("Now");
        nowButton.setOnAction(e -> {
            LocalTime now = LocalTime.now();
            hourSpinner.getValueFactory().setValue(now.getHour());
            minuteSpinner.getValueFactory().setValue(now.getMinute());
        });
        
        timeBox.getChildren().addAll(
            hourSpinner, colonLabel, minuteSpinner, nowButton
        );
        
        return timeBox;
    }
    
    // Continued in next message...
	    // ... continuing from previous section ...
    
    private HBox createQuickTimeButtons() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        
        Button in30MinBtn = new Button("+30 min");
        in30MinBtn.setOnAction(e -> setETAFromNow(30));
        
        Button in1HourBtn = new Button("+1 hour");
        in1HourBtn.setOnAction(e -> setETAFromNow(60));
        
        Button in2HoursBtn = new Button("+2 hours");
        in2HoursBtn.setOnAction(e -> setETAFromNow(120));
        
        Button in4HoursBtn = new Button("+4 hours");
        in4HoursBtn.setOnAction(e -> setETAFromNow(240));
        
        Button tomorrowBtn = new Button("Tomorrow");
        tomorrowBtn.setOnAction(e -> {
            etaDatePicker.setValue(LocalDate.now().plusDays(1));
            hourSpinner.getValueFactory().setValue(8);
            minuteSpinner.getValueFactory().setValue(0);
        });
        
        box.getChildren().addAll(in30MinBtn, in1HourBtn, in2HoursBtn, in4HoursBtn, tomorrowBtn);
        
        return box;
    }
    
    private void setETAFromNow(int minutes) {
        LocalDateTime eta = LocalDateTime.now().plusMinutes(minutes);
        etaDatePicker.setValue(eta.toLocalDate());
        hourSpinner.getValueFactory().setValue(eta.getHour());
        minuteSpinner.getValueFactory().setValue(eta.getMinute());
    }
    
    private TitledPane createAdditionalOptionsPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Notification options
        notifyDispatchCheck.setSelected(true);
        
        // Load status update
        updateLoadStatusCheck.setSelected(driver.getCurrentLoad() != null);
        updateLoadStatusCheck.setDisable(driver.getCurrentLoad() == null);
        
        if (driver.getCurrentLoad() != null) {
            Label loadLabel = new Label("Current load: " + driver.getCurrentLoad().getLoadNumber() + 
                " (" + driver.getCurrentLoad().getCustomer() + ")");
            loadLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");
            content.getChildren().add(loadLabel);
        }
        
        // Priority flag
        CheckBox priorityCheck = new CheckBox("Mark as priority update");
        
        // Reason for status change
        Label reasonLabel = new Label("Reason for change:");
        ComboBox<String> reasonCombo = new ComboBox<>();
        reasonCombo.getItems().addAll(
            "Scheduled stop",
            "Unexpected delay",
            "Mechanical issue",
            "Weather conditions",
            "Customer request",
            "Driver request",
            "Load completed",
            "Other"
        );
        reasonCombo.setPrefWidth(200);
        
        content.getChildren().addAll(
            notifyDispatchCheck,
            updateLoadStatusCheck,
            new Separator(),
            priorityCheck,
            new Separator(),
            reasonLabel,
            reasonCombo
        );
        
        TitledPane pane = new TitledPane("Additional Options", content);
        pane.setExpanded(false);
        
        return pane;
    }
    
    private TitledPane createNotesPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label instructionLabel = new Label("Additional notes or comments:");
        
        notesArea.setPrefRowCount(4);
        notesArea.setWrapText(true);
        notesArea.setPromptText("Enter any additional information about this status update...");
        
        // Character counter
        Label charCountLabel = new Label("0 / 500");
        charCountLabel.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
        
        notesArea.textProperty().addListener((obs, old, text) -> {
            int length = text.length();
            charCountLabel.setText(length + " / 500");
            if (length > 500) {
                charCountLabel.setTextFill(Color.RED);
                notesArea.setText(text.substring(0, 500));
            } else {
                charCountLabel.setTextFill(Color.GRAY);
            }
        });
        
        // Quick notes buttons
        HBox quickNotes = new HBox(5);
        quickNotes.setAlignment(Pos.CENTER_LEFT);
        
        Button trafficBtn = new Button("Traffic");
        trafficBtn.setOnAction(e -> appendNote("Heavy traffic conditions. "));
        
        Button weatherBtn = new Button("Weather");
        weatherBtn.setOnAction(e -> appendNote("Adverse weather conditions. "));
        
        Button mechanicalBtn = new Button("Mechanical");
        mechanicalBtn.setOnAction(e -> appendNote("Minor mechanical issue resolved. "));
        
        Button customerBtn = new Button("Customer");
        customerBtn.setOnAction(e -> appendNote("Customer requested schedule change. "));
        
        quickNotes.getChildren().addAll(trafficBtn, weatherBtn, mechanicalBtn, customerBtn);
        
        content.getChildren().addAll(instructionLabel, notesArea, charCountLabel, quickNotes);
        
        TitledPane pane = new TitledPane("Notes", content);
        pane.setExpanded(false);
        
        return pane;
    }
    
    private void appendNote(String text) {
        String current = notesArea.getText();
        if (!current.isEmpty() && !current.endsWith(" ")) {
            current += " ";
        }
        notesArea.setText(current + text);
        notesArea.positionCaret(notesArea.getText().length());
    }
    
    private ScrollPane createHistoryContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Status History");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // History list
        statusHistoryList.setPrefHeight(400);
        statusHistoryList.getStyleClass().add("history-list");
        
        // Load history data
        loadStatusHistory();
        
        // Summary stats
        HBox statsBox = new HBox(20);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        VBox availableStats = createHistoryStatBox("Available", 
            calculateStatusHours(DispatcherDriverStatus.Status.AVAILABLE), Color.GREEN);
        VBox onRoadStats = createHistoryStatBox("On Road", 
            calculateStatusHours(DispatcherDriverStatus.Status.ON_ROAD), Color.BLUE);
        VBox loadingStats = createHistoryStatBox("Loading", 
            calculateStatusHours(DispatcherDriverStatus.Status.LOADING), Color.ORANGE);
        
        statsBox.getChildren().addAll(availableStats, onRoadStats, loadingStats);
        
        content.getChildren().addAll(titleLabel, statusHistoryList, statsBox);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    private VBox createHistoryStatBox(String title, double hours, Color color) {
        VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER);
        
        Circle indicator = new Circle(5, color);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold;");
        Label hoursLabel = new Label(String.format("%.1f hrs", hours));
        
        box.getChildren().addAll(indicator, titleLabel, hoursLabel);
        
        return box;
    }
    
    private ScrollPane createPreviewContent() {
        previewBox.setPadding(new Insets(20));
        previewBox.getStyleClass().add("preview-box");
        
        ScrollPane scrollPane = new ScrollPane(previewBox);
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    private void updatePreview() {
        previewBox.getChildren().clear();
        
        Label titleLabel = new Label("Status Update Preview");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        Separator separator = new Separator();
        
        // Current state
        VBox currentBox = new VBox(5);
        currentBox.setPadding(new Insets(10));
        currentBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");
        
        Label currentTitle = new Label("Current State:");
        currentTitle.setStyle("-fx-font-weight: bold;");
        
        Label currentStatus = new Label("Status: " + driver.getStatus().getDisplayName());
        Label currentLocation = new Label("Location: " + driver.getCurrentLocation());
        
        currentBox.getChildren().addAll(currentTitle, currentStatus, currentLocation);
        
        // Arrow
        Label arrowLabel = new Label("⬇");
        arrowLabel.setStyle("-fx-font-size: 24; -fx-text-fill: #007bff;");
        arrowLabel.setPadding(new Insets(10));
        
        // New state
        VBox newBox = new VBox(5);
        newBox.setPadding(new Insets(10));
        newBox.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 5;");
        
        Label newTitle = new Label("New State:");
        newTitle.setStyle("-fx-font-weight: bold;");
        
        DispatcherDriverStatus.Status selectedStatus = statusGroup.getSelectedToggle() != null ?
            (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData() :
            driver.getStatus();
        
        Label newStatus = new Label("Status: " + selectedStatus.getDisplayName());
        Label newLocation = new Label("Location: " + 
            (!locationField.getText().isEmpty() ? locationField.getText() : driver.getCurrentLocation()));
        
        String etaText = "ETA: ";
        if (etaDatePicker.getValue() != null) {
            LocalDateTime eta = LocalDateTime.of(
                etaDatePicker.getValue(),
                LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
            );
            etaText += eta.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
        } else {
            etaText += "Not set";
        }
        Label newETA = new Label(etaText);
        
        newBox.getChildren().addAll(newTitle, newStatus, newLocation, newETA);
        
        // Impact summary
        VBox impactBox = new VBox(5);
        impactBox.setPadding(new Insets(10));
        impactBox.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 5;");
        
        Label impactTitle = new Label("Impact:");
        impactTitle.setStyle("-fx-font-weight: bold;");
        
        List<String> impacts = calculateUpdateImpact();
        VBox impactsList = new VBox(3);
        for (String impact : impacts) {
            Label impactLabel = new Label("• " + impact);
            impactLabel.setWrapText(true);
            impactsList.getChildren().add(impactLabel);
        }
        
        impactBox.getChildren().addAll(impactTitle, impactsList);
        
        previewBox.getChildren().addAll(
            titleLabel, separator, currentBox, arrowLabel, newBox, impactBox
        );
    }
    
    private List<String> calculateUpdateImpact() {
        List<String> impacts = new ArrayList<>();
        
        if (driver.getCurrentLoad() != null && updateLoadStatusCheck.isSelected()) {
            impacts.add("Current load " + driver.getCurrentLoad().getLoadNumber() + 
                       " will be updated");
        }
        
        if (notifyDispatchCheck.isSelected()) {
            impacts.add("Dispatch team will be notified");
        }
        
        DispatcherDriverStatus.Status newStatus = statusGroup.getSelectedToggle() != null ?
            (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData() :
            driver.getStatus();
        
        if (newStatus == DispatcherDriverStatus.Status.OFF_DUTY) {
            impacts.add("Driver will be removed from active dispatch");
        } else if (newStatus == DispatcherDriverStatus.Status.AVAILABLE) {
            impacts.add("Driver will be available for new load assignments");
        }
        
        return impacts;
    }
    
    private void setupValidation() {
        // Status selection validation
        statusGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            validateForm();
            updatePreview();
            
            // Enable/disable ETA based on status
            boolean requiresETA = selected != null && 
                ((DispatcherDriverStatus.Status) selected.getUserData()) != DispatcherDriverStatus.Status.AVAILABLE;
            etaDatePicker.setDisable(!requiresETA);
            hourSpinner.setDisable(!requiresETA);
            minuteSpinner.setDisable(!requiresETA);
        });
        
        // Location validation
        locationField.textProperty().addListener((obs, old, text) -> {
            validateLocation();
            validateForm();
            updatePreview();
        });
        
        // ETA validation
        etaDatePicker.valueProperty().addListener((obs, old, date) -> {
            validateForm();
            updatePreview();
        });
        
        hourSpinner.valueProperty().addListener((obs, old, val) -> {
            validateForm();
            updatePreview();
        });
        
        minuteSpinner.valueProperty().addListener((obs, old, val) -> {
            validateForm();
            updatePreview();
        });
    }
    
    private void validateLocation() {
        Label validationLabel = fieldValidationLabels.get(locationField);
        if (validationLabel != null) {
            String location = locationField.getText().trim();
            if (location.isEmpty()) {
                validationLabel.setText("");
            } else if (!LOCATION_PATTERN.matcher(location).matches()) {
                validationLabel.setText("Invalid location format");
                validationLabel.setTextFill(Color.RED);
            } else {
                validationLabel.setText("✓ Valid location");
                validationLabel.setTextFill(Color.GREEN);
            }
        }
    }
    
    private void validateForm() {
        List<String> errors = new ArrayList<>();
        
        // Status is required
        if (statusGroup.getSelectedToggle() == null) {
            errors.add("Please select a status");
        }
        
        // Location validation if changed
        String newLocation = locationField.getText().trim();
        if (!newLocation.isEmpty() && !LOCATION_PATTERN.matcher(newLocation).matches()) {
            errors.add("Invalid location format");
        }
        
        // ETA validation if required
        DispatcherDriverStatus.Status selectedStatus = statusGroup.getSelectedToggle() != null ?
            (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData() : null;
        
        if (selectedStatus != null && 
            selectedStatus != DispatcherDriverStatus.Status.AVAILABLE &&
            selectedStatus != DispatcherDriverStatus.Status.OFF_DUTY) {
            
            if (etaDatePicker.getValue() == null) {
                errors.add("ETA date is required for this status");
            } else {
                // Check if ETA is in the past
                LocalDateTime eta = LocalDateTime.of(
                    etaDatePicker.getValue(),
                    LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                );
                if (eta.isBefore(LocalDateTime.now())) {
                    errors.add("ETA cannot be in the past");
                }
            }
        }
        
        // Update validation display
        if (errors.isEmpty()) {
            validationLabel.setText("");
            formValid.set(true);
        } else {
            validationLabel.setText(String.join("\n", errors));
            formValid.set(false);
        }
    }
    
    private void loadDriverData() {
        // Pre-populate current location
        locationField.setText(driver.getCurrentLocation());
        
        // Pre-populate ETA if exists
        if (driver.getEstimatedAvailableTime() != null) {
            LocalDateTime eta = driver.getEstimatedAvailableTime();
            etaDatePicker.setValue(eta.toLocalDate());
            hourSpinner.getValueFactory().setValue(eta.getHour());
            minuteSpinner.getValueFactory().setValue(eta.getMinute());
        }
    }
    
    private void setupLocationPresets() {
        locationPresets.getItems().addAll(
            "Home Base",
            "Distribution Center - Main",
            "Distribution Center - North",
            "Distribution Center - South",
            "Truck Stop - I-95",
            "Truck Stop - I-80",
            "Customer Site",
            "Maintenance Shop",
            "Weigh Station",
            "Rest Area"
        );
        
        // Add commonly used locations from history
        List<String> recentLocations = getRecentLocations();
        if (!recentLocations.isEmpty()) {
            locationPresets.getItems().add(null); // Separator
            locationPresets.getItems().addAll(recentLocations);
        }
        
        locationPresets.setOnAction(e -> {
            String selected = locationPresets.getValue();
            if (selected != null) {
                locationField.setText(selected);
            }
        });
    }
    
    private List<String> getRecentLocations() {
        // TODO: Load from driver history
        return new ArrayList<>();
    }
    
    private void setupKeyboardShortcuts() {
        getDialogPane().getScene().getAccelerators().put(
            javafx.scene.input.KeyCombination.keyCombination("Ctrl+S"),
            () -> {
                if (formValid.get()) {
                    setResult(true);
                    close();
                }
            }
        );
    }
    
    private void updateEstimatedDuration(DispatcherDriverStatus.Status status) {
        String duration = switch (status) {
            case LOADING -> "30-60 minutes";
            case ON_ROAD -> "Variable (based on route)";
            case RETURNING -> "2-4 hours";
            case OFF_DUTY -> "8+ hours";
            case AVAILABLE -> "Immediate";
            case PREPARING -> "15-30 minutes";
            default -> "Unknown";
        };
        
        estimatedDurationLabel.setText(duration);
    }
    
    private void calculateETA() {
        // Simple ETA calculation based on current location and status
        DispatcherDriverStatus.Status selectedStatus = statusGroup.getSelectedToggle() != null ?
            (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData() :
            driver.getStatus();
        
        LocalDateTime estimatedETA = LocalDateTime.now();
        
        switch (selectedStatus) {
            case LOADING:
                estimatedETA = estimatedETA.plusMinutes(45);
                break;
            case ON_ROAD:
                // Would need route information for accurate calculation
                estimatedETA = estimatedETA.plusHours(4);
                break;
            case RETURNING:
                estimatedETA = estimatedETA.plusHours(3);
                break;
            case OFF_DUTY:
                estimatedETA = estimatedETA.plusHours(10);
                break;
            case PREPARING:
                estimatedETA = estimatedETA.plusMinutes(20);
                break;
        }
        
        etaDatePicker.setValue(estimatedETA.toLocalDate());
        hourSpinner.getValueFactory().setValue(estimatedETA.getHour());
        minuteSpinner.getValueFactory().setValue(estimatedETA.getMinute());
    }
    
    private void loadStatusHistory() {
        // TODO: Load actual history from database
        ObservableList<String> historyItems = FXCollections.observableArrayList();
        
        // Sample history entries
        historyItems.add("2025-07-02 08:30 - Status: Available → On Road");
        historyItems.add("2025-07-02 07:15 - Status: Off Duty → Available");
        historyItems.add("2025-07-01 18:45 - Status: Returning → Off Duty");
        historyItems.add("2025-07-01 16:20 - Status: On Road → Returning");
        historyItems.add("2025-07-01 14:00 - Status: Loading → On Road");
        
        statusHistoryList.setItems(historyItems);
    }
    
    private double calculateStatusHours(DispatcherDriverStatus.Status status) {
        // TODO: Calculate actual hours from history
        return Math.random() * 40; // Placeholder
    }
    
    private void showLocationOnMap() {
        // TODO: Integrate with mapping service
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Map View");
        alert.setHeaderText(null);
        alert.setContentText("Map integration coming soon!");
        alert.showAndWait();
    }
    
    private boolean performUpdate() {
        try {
            DispatcherDriverStatus.Status newStatus = 
                (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData();
            
            String newLocation = locationField.getText().trim();
            if (newLocation.isEmpty()) {
                newLocation = driver.getCurrentLocation();
            }
            
            LocalDateTime eta = null;
            if (etaDatePicker.getValue() != null) {
                eta = LocalDateTime.of(
                    etaDatePicker.getValue(),
                    LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
                );
            }
            
            String notes = notesArea.getText().trim();
            
            // Add metadata to notes
            if (!notes.isEmpty()) {
                notes = String.format("[%s - Updated by: %s] %s",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
                    System.getProperty("user.name", "Unknown"),
                    notes
                );
            }
            
            // Perform the update
            controller.updateDriverStatus(driver, newStatus, newLocation, eta, notes);
            
            // Update load if requested
            if (updateLoadStatusCheck.isSelected() && driver.getCurrentLoad() != null) {
                updateAssociatedLoadStatus(driver.getCurrentLoad(), newStatus);
            }
            
            // Send notifications if requested
            if (notifyDispatchCheck.isSelected()) {
                sendDispatchNotification(driver, newStatus, newLocation);
            }
            
            logger.info("Successfully updated status for driver: {} to {}", 
                driver.getDriverName(), newStatus);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to update driver status", e);
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Update Failed");
            alert.setHeaderText("Failed to update driver status");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            
            return false;
        }
    }
    
    private void updateAssociatedLoadStatus(Load load, DispatcherDriverStatus.Status driverStatus) {
        // Update load status based on driver status
        switch (driverStatus) {
            case ON_ROAD:
                if (load.getStatus() == Load.Status.ASSIGNED) {
                    load.setStatus(Load.Status.IN_TRANSIT);
                }
                break;
            case AVAILABLE:
                if (load.getStatus() == Load.Status.IN_TRANSIT) {
                    load.setStatus(Load.Status.DELIVERED);
                }
                break;
        }
        
        // TODO: Persist load status update
    }
    
    private void sendDispatchNotification(DispatcherDriverStatus driver, 
                                        DispatcherDriverStatus.Status newStatus, 
                                        String newLocation) {
        // TODO: Implement actual notification system
        logger.info("Dispatch notification sent for driver {} status change to {}", 
            driver.getDriverName(), newStatus);
    }
}
	