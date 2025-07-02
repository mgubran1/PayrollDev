package com.company.payroll.dispatcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Modern dialog for updating a single driver's status
 */
public class UpdateDriverStatusDialog extends Dialog<Boolean> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    
    private final DispatcherDriverStatus driver;
    private final DispatcherController controller;
    private ToggleGroup statusGroup;
    private TextField locationField;
    private DatePicker etaDate;
    private Spinner<Integer> hourSpinner;
    private Spinner<Integer> minuteSpinner;
    private TextArea notesArea;
    
    public UpdateDriverStatusDialog(DispatcherDriverStatus driver, DispatcherController controller) {
        this.driver = driver;
        this.controller = controller;
        
        setTitle("Update Driver Status");
        initModality(Modality.APPLICATION_MODAL);
        
        // Style the dialog
        getDialogPane().setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 20, 0, 0, 5);"
        );
        
        initializeModernUI();
        
        // Modern styled buttons
        ButtonType updateButtonType = new ButtonType("Update Status", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);
        
        // Style the buttons
        Button updateBtn = (Button) getDialogPane().lookupButton(updateButtonType);
        updateBtn.setStyle(
            "-fx-background-color: #2196F3; " +
            "-fx-text-fill: white; " +
            "-fx-font-weight: 600; " +
            "-fx-padding: 10px 20px; " +
            "-fx-background-radius: 5px;"
        );
        
        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelBtn.setStyle(
            "-fx-background-color: white; " +
            "-fx-text-fill: #757575; " +
            "-fx-font-weight: 600; " +
            "-fx-padding: 10px 20px; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-width: 1px;"
        );
        
        // Disable OK button if no status selected
        Node okBtn = getDialogPane().lookupButton(updateButtonType);
        okBtn.disableProperty().bind(statusGroup.selectedToggleProperty().isNull());
        
        setResultConverter(btn -> {
            if (btn == updateButtonType) {
                return performUpdate();
            }
            return false;
        });
    }
    
    private void initializeModernUI() {
        VBox mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setStyle("-fx-background-color: white;");
        
        // Header with driver info
        VBox header = createHeader();
        
        // Status Section
        VBox statusSection = createModernSection(
            "📊 Status Update",
            "Change driver's current status",
            createStatusSelector()
        );
        
        // Location Section
        VBox locationSection = createModernSection(
            "📍 Current Location",
            "Update driver's current location",
            createLocationInput()
        );
        
        // ETA Section
        VBox etaSection = createModernSection(
            "⏰ Estimated Availability",
            "Set when driver will be available",
            createETAInput()
        );
        
        // Notes Section
        VBox notesSection = createModernSection(
            "📝 Update Notes",
            "Add any additional information",
            createNotesArea()
        );
        
        mainContainer.getChildren().addAll(header, statusSection, locationSection, etaSection, notesSection);
        
        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white; -fx-background: white;");
        
        getDialogPane().setContent(scrollPane);
        getDialogPane().setPrefSize(500, 650);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
            "-fx-background-radius: 8px; " +
            "-fx-padding: 20px;"
        );
        
        Label driverName = new Label(driver.getDriverName());
        driverName.setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: white;");
        
        HBox infoRow = new HBox(20);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        
        Label truckInfo = new Label("🚛 " + driver.getTruckUnit());
        truckInfo.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        
        Label trailerInfo = new Label("🚚 " + driver.getTrailerNumber());
        trailerInfo.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        
        HBox currentStatusBadge = createCurrentStatusBadge();
        
        infoRow.getChildren().addAll(truckInfo, trailerInfo, currentStatusBadge);
        
        header.getChildren().addAll(driverName, infoRow);
        
        return header;
    }
    
    private HBox createCurrentStatusBadge() {
        HBox badge = new HBox();
        badge.setAlignment(Pos.CENTER);
        badge.setPadding(new Insets(6, 15, 6, 15));
        badge.setStyle(
            "-fx-background-color: rgba(255,255,255,0.3); " +
            "-fx-background-radius: 15px;"
        );
        
        Label statusLabel = new Label("Current: " + driver.getStatus().getDisplayName());
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: 600;");
        
        badge.getChildren().add(statusLabel);
        return badge;
    }
    
    private VBox createModernSection(String title, String subtitle, Node content) {
        VBox section = new VBox(10);
        section.setStyle(
            "-fx-background-color: #FAFAFA; " +
            "-fx-background-radius: 8px; " +
            "-fx-padding: 15px;"
        );
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: #212121;");
        
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        
        section.getChildren().addAll(titleLabel, subtitleLabel, content);
        
        return section;
    }
    
    private VBox createStatusSelector() {
        VBox container = new VBox(10);
        statusGroup = new ToggleGroup();
        
        for (DispatcherDriverStatus.Status status : DispatcherDriverStatus.Status.values()) {
            HBox statusOption = createStatusOption(status);
            container.getChildren().add(statusOption);
        }
        
        return container;
    }
    
    private HBox createStatusOption(DispatcherDriverStatus.Status status) {
        HBox option = new HBox(10);
        option.setAlignment(Pos.CENTER_LEFT);
        option.setPadding(new Insets(10));
        option.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 8px; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 8px; " +
            "-fx-cursor: hand;"
        );
        
        RadioButton radioButton = new RadioButton();
        radioButton.setToggleGroup(statusGroup);
        radioButton.setUserData(status);
        if (status == driver.getStatus()) {
            radioButton.setSelected(true);
        }
        
        // Status indicator circle
        Label indicator = new Label("●");
        indicator.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 16px;", status.getColor()));
        
        Label nameLabel = new Label(status.getDisplayName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        
        option.getChildren().addAll(radioButton, indicator, nameLabel);
        
        // Hover effect
        option.setOnMouseEntered(e -> {
            option.setStyle(option.getStyle() + "-fx-background-color: #F5F5F5;");
        });
        
        option.setOnMouseExited(e -> {
            option.setStyle(option.getStyle().replace("-fx-background-color: #F5F5F5;", "-fx-background-color: white;"));
        });
        
        // Click entire row to select
        option.setOnMouseClicked(e -> radioButton.setSelected(true));
        
        return option;
    }
    
    private VBox createLocationInput() {
        VBox container = new VBox(10);
        
        locationField = new TextField(driver.getCurrentLocation());
        locationField.setPromptText("Enter current location (e.g., Chicago, IL)");
        locationField.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 10px; " +
            "-fx-font-size: 14px;"
        );
        
        // Recent locations (optional enhancement)
        Label recentLabel = new Label("Recent Locations:");
        recentLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575; -fx-font-weight: 600;");
        
        HBox recentLocations = new HBox(10);
        recentLocations.setAlignment(Pos.CENTER_LEFT);
        
        // Example recent location buttons
        for (String location : new String[]{"Chicago, IL", "Milwaukee, WI", "Detroit, MI"}) {
            Button locBtn = createQuickLocationButton(location);
            recentLocations.getChildren().add(locBtn);
        }
        
        container.getChildren().addAll(locationField, recentLabel, recentLocations);
        
        return container;
    }
    
    private Button createQuickLocationButton(String location) {
        Button button = new Button(location);
        button.setStyle(
            "-fx-background-color: #E3F2FD; " +
            "-fx-text-fill: #2196F3; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 5px 10px; " +
            "-fx-background-radius: 15px; " +
            "-fx-cursor: hand;"
        );
        
        button.setOnAction(e -> locationField.setText(location));
        
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "-fx-background-color: #BBDEFB;");
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("-fx-background-color: #BBDEFB;", "-fx-background-color: #E3F2FD;"));
        });
        
        return button;
    }
    
    private VBox createETAInput() {
        VBox container = new VBox(10);
        
        HBox dateTimeBox = new HBox(15);
        dateTimeBox.setAlignment(Pos.CENTER_LEFT);
        
        // Date picker
        VBox dateBox = new VBox(5);
        Label dateLabel = new Label("Date:");
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        
        etaDate = new DatePicker();
        etaDate.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px;"
        );
        etaDate.setPrefWidth(150);
        
        dateBox.getChildren().addAll(dateLabel, etaDate);
        
        // Time spinners
        VBox timeBox = new VBox(5);
        Label timeLabel = new Label("Time:");
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        
        HBox timeSpinners = new HBox(5);
        timeSpinners.setAlignment(Pos.CENTER_LEFT);
        
        hourSpinner = new Spinner<>(0, 23, 8);
        hourSpinner.setPrefWidth(70);
        hourSpinner.setStyle("-fx-background-color: white; -fx-border-color: #E0E0E0;");
        
        Label colonLabel = new Label(":");
        colonLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        minuteSpinner = new Spinner<>(0, 59, 0);
        minuteSpinner.setPrefWidth(70);
        minuteSpinner.setStyle("-fx-background-color: white; -fx-border-color: #E0E0E0;");
        
        timeSpinners.getChildren().addAll(hourSpinner, colonLabel, minuteSpinner);
        timeBox.getChildren().addAll(timeLabel, timeSpinners);
        
        dateTimeBox.getChildren().addAll(dateBox, timeBox);
        
        // Quick select buttons
        HBox quickButtons = new HBox(10);
        quickButtons.setAlignment(Pos.CENTER_LEFT);
        
        Button nowBtn = createQuickTimeButton("Now", 0);
        Button oneHourBtn = createQuickTimeButton("+1 Hour", 1);
        Button twoHoursBtn = createQuickTimeButton("+2 Hours", 2);
        Button tomorrowBtn = createQuickTimeButton("Tomorrow", 24);
        
        quickButtons.getChildren().addAll(nowBtn, oneHourBtn, twoHoursBtn, tomorrowBtn);
        
        container.getChildren().addAll(dateTimeBox, new Separator(), quickButtons);
        
        return container;
    }
    
    private Button createQuickTimeButton(String text, int hoursToAdd) {
        Button button = new Button(text);
        button.setStyle(
            "-fx-background-color: white; " +
            "-fx-text-fill: #2196F3; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 5px 15px; " +
            "-fx-background-radius: 15px; " +
            "-fx-border-color: #2196F3; " +
            "-fx-border-width: 1px; " +
            "-fx-cursor: hand;"
        );
        
        button.setOnAction(e -> {
            LocalDateTime eta = LocalDateTime.now().plusHours(hoursToAdd);
            etaDate.setValue(eta.toLocalDate());
            hourSpinner.getValueFactory().setValue(eta.getHour());
            minuteSpinner.getValueFactory().setValue(eta.getMinute());
        });
        
        button.setOnMouseEntered(e -> {
            button.setStyle(
                "-fx-background-color: #E3F2FD; " +
                "-fx-text-fill: #2196F3; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 5px 15px; " +
                "-fx-background-radius: 15px; " +
                "-fx-border-color: #2196F3; " +
                "-fx-border-width: 1px; " +
                "-fx-cursor: hand;"
            );
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(
                "-fx-background-color: white; " +
                "-fx-text-fill: #2196F3; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 5px 15px; " +
                "-fx-background-radius: 15px; " +
                "-fx-border-color: #2196F3; " +
                "-fx-border-width: 1px; " +
                "-fx-cursor: hand;"
            );
        });
        
        return button;
    }
    
    private TextArea createNotesArea() {
        notesArea = new TextArea();
        notesArea.setPrefRowCount(4);
        notesArea.setPromptText("Enter any notes about this status update...");
        notesArea.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 8px; " +
            "-fx-font-size: 13px;"
        );
        notesArea.setWrapText(true);
        
        return notesArea;
    }
    
    private boolean performUpdate() {
        try {
            DispatcherDriverStatus.Status newStatus = (DispatcherDriverStatus.Status) statusGroup.getSelectedToggle().getUserData();
            LocalDate date = etaDate.getValue();
            LocalTime time = LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue());
            LocalDateTime eta = date != null ? LocalDateTime.of(date, time) : null;
            
            controller.updateDriverStatus(driver, newStatus, locationField.getText(), eta, notesArea.getText());
            
            showModernSuccess("Driver status updated successfully");
            return true;
        } catch (Exception e) {
            showModernError("Failed to update driver status: " + e.getMessage());
            return false;
        }
    }
    
    private void showModernError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Update Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px;"
        );
        
        alert.showAndWait();
    }
    
    private void showModernSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px;"
        );
        
        alert.showAndWait();
    }
}