package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Modern dialog for assigning loads to drivers
 */
public class AssignLoadDialog extends Dialog<Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(AssignLoadDialog.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    
    private final DispatcherController controller;
    private ComboBox<Load> loadCombo;
    private ComboBox<DispatcherDriverStatus> driverCombo;
    private TextArea notesArea;
    
    public AssignLoadDialog(DispatcherController controller) {
        this.controller = controller;
        
        setTitle("Assign Load to Driver");
        initModality(Modality.APPLICATION_MODAL);
        
        // Style the dialog
        getDialogPane().setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 20, 0, 0, 5);"
        );
        
        initializeModernUI();
        
        // Modern styled buttons
        ButtonType assignButtonType = new ButtonType("Assign Load", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);
        
        // Style the buttons
        Button assignBtn = (Button) getDialogPane().lookupButton(assignButtonType);
        assignBtn.setStyle(
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
        
        setResultConverter(dialogButton -> {
            if (dialogButton == assignButtonType) {
                return performAssignment();
            }
            return false;
        });
    }
    
    private void initializeModernUI() {
        VBox mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setStyle("-fx-background-color: white;");
        
        // Header with icon
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label("🚚");
        iconLabel.setStyle("-fx-font-size: 36px;");
        
        VBox headerText = new VBox(5);
        Label titleLabel = new Label("Assign Load to Driver");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        Label subtitleLabel = new Label("Match available loads with the right driver");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #757575;");
        
        headerText.getChildren().addAll(titleLabel, subtitleLabel);
        header.getChildren().addAll(iconLabel, headerText);
        
        // Load Section
        VBox loadSection = createModernSection(
            "📦 Select Load",
            "Choose from unassigned loads",
            createLoadSelector()
        );
        
        // Driver Section
        VBox driverSection = createModernSection(
            "👤 Select Driver",
            "Choose from available drivers",
            createDriverSelector()
        );
        
        // Notes Section
        VBox notesSection = createModernSection(
            "📝 Assignment Notes",
            "Add any special instructions",
            createNotesArea()
        );
        
        mainContainer.getChildren().addAll(header, loadSection, driverSection, notesSection);
        
        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white; -fx-background: white;");
        
        getDialogPane().setContent(scrollPane);
        getDialogPane().setPrefSize(600, 700);
    }
    
    private VBox createModernSection(String title, String subtitle, javafx.scene.Node content) {
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
    
    private VBox createLoadSelector() {
        VBox container = new VBox(10);
        
        loadCombo = new ComboBox<>();
        loadCombo.setPrefWidth(Double.MAX_VALUE);
        loadCombo.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 8px;"
        );
        
        List<Load> unassignedLoads = controller.getUnassignedLoads();
        loadCombo.getItems().addAll(unassignedLoads);
        
        loadCombo.setCellFactory(lv -> new ModernLoadCell());
        loadCombo.setButtonCell(new ModernLoadCell());
        
        // Load details card
        VBox loadDetailsCard = new VBox(10);
        loadDetailsCard.setPadding(new Insets(15));
        loadDetailsCard.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 5px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );
        
        Label loadDetailsContent = new Label("Select a load to view details");
        loadDetailsContent.setWrapText(true);
        loadDetailsContent.setStyle("-fx-text-fill: #757575;");
        loadDetailsCard.getChildren().add(loadDetailsContent);
        
        // Update details on selection
        loadCombo.setOnAction(e -> {
            Load selected = loadCombo.getValue();
            if (selected != null) {
                loadDetailsCard.getChildren().clear();
                loadDetailsCard.getChildren().addAll(createLoadDetailsContent(selected));
            }
        });
        
        container.getChildren().addAll(loadCombo, loadDetailsCard);
        
        return container;
    }
    
    private VBox createDriverSelector() {
        VBox container = new VBox(10);
        
        driverCombo = new ComboBox<>();
        driverCombo.setPrefWidth(Double.MAX_VALUE);
        driverCombo.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #E0E0E0; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-padding: 8px;"
        );
        
        List<DispatcherDriverStatus> availableDrivers = controller.getAvailableDrivers();
        driverCombo.getItems().addAll(availableDrivers);
        
        driverCombo.setCellFactory(lv -> new ModernDriverCell());
        driverCombo.setButtonCell(new ModernDriverCell());
        
        // Driver details card
        VBox driverDetailsCard = new VBox(10);
        driverDetailsCard.setPadding(new Insets(15));
        driverDetailsCard.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 5px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );
        
        Label driverDetailsContent = new Label("Select a driver to view details");
        driverDetailsContent.setWrapText(true);
        driverDetailsContent.setStyle("-fx-text-fill: #757575;");
        driverDetailsCard.getChildren().add(driverDetailsContent);
        
        // Update details on selection
        driverCombo.setOnAction(e -> {
            DispatcherDriverStatus selected = driverCombo.getValue();
            if (selected != null) {
                driverDetailsCard.getChildren().clear();
                driverDetailsCard.getChildren().addAll(createDriverDetailsContent(selected));
            }
        });
        
        container.getChildren().addAll(driverCombo, driverDetailsCard);
        
        return container;
    }
    
    private TextArea createNotesArea() {
        notesArea = new TextArea();
        notesArea.setPrefRowCount(4);
        notesArea.setPromptText("Enter any special instructions or notes for this assignment...");
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
    
    private List<javafx.scene.Node> createLoadDetailsContent(Load load) {
        List<javafx.scene.Node> content = new java.util.ArrayList<>();
        
        // Load number with badge
        HBox loadHeader = new HBox(10);
        loadHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label loadBadge = new Label(load.getLoadNumber());
        loadBadge.setStyle(
            "-fx-background-color: #E3F2FD; " +
            "-fx-text-fill: #2196F3; " +
            "-fx-padding: 5px 10px; " +
            "-fx-background-radius: 15px; " +
            "-fx-font-weight: 600;"
        );
        
        Label amountLabel = new Label(String.format("$%.2f", load.getGrossAmount()));
        amountLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #4CAF50;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        loadHeader.getChildren().addAll(loadBadge, spacer, amountLabel);
        content.add(loadHeader);
        
        // Customer
        content.add(createDetailRow("Customer", load.getCustomer(), "👤"));
        
        // Route
        content.add(createDetailRow("Route", 
            getCityName(load.getPickUpLocation()) + " → " + getCityName(load.getDropLocation()), "🛣"));
        
        // Pickup info
        String pickupInfo = load.getPickUpLocation();
        if (load.getPickUpDate() != null) {
            pickupInfo += "\n" + load.getPickUpDate();
            if (load.getPickUpTime() != null) {
                pickupInfo += " @ " + load.getPickUpTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        content.add(createDetailRow("Pickup", pickupInfo, "📍"));
        
        // Delivery info
        String deliveryInfo = load.getDropLocation();
        if (load.getDeliveryDate() != null) {
            deliveryInfo += "\n" + load.getDeliveryDate();
            if (load.getDeliveryTime() != null) {
                deliveryInfo += " @ " + load.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        content.add(createDetailRow("Delivery", deliveryInfo, "🎯"));
        
        return content;
    }
    
    private List<javafx.scene.Node> createDriverDetailsContent(DispatcherDriverStatus driver) {
        List<javafx.scene.Node> content = new java.util.ArrayList<>();
        
        // Driver header with status
        HBox driverHeader = new HBox(10);
        driverHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label nameBadge = new Label(driver.getDriverName());
        nameBadge.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox statusBadge = createStatusBadge(driver.getStatus());
        
        driverHeader.getChildren().addAll(nameBadge, spacer, statusBadge);
        content.add(driverHeader);
        
        // Truck info
        content.add(createDetailRow("Truck", driver.getTruckUnit(), "🚛"));
        
        // Trailer
        if (driver.getTrailerNumber() != null && !driver.getTrailerNumber().isEmpty()) {
            content.add(createDetailRow("Trailer", driver.getTrailerNumber(), "🚚"));
        }
        
        // Location
        content.add(createDetailRow("Location", driver.getCurrentLocation(), "📍"));
        
        // Hours worked with progress
        VBox hoursBox = new VBox(5);
        hoursBox.setStyle("-fx-padding: 10px 0;");
        
        Label hoursLabel = new Label("⏱ Hours Worked");
        hoursLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575; -fx-font-weight: 600;");
        
        HBox hoursInfo = new HBox(15);
        Label todayHours = new Label(String.format("Today: %.1fh", driver.getHoursWorkedToday()));
        todayHours.setStyle("-fx-font-size: 13px;");
        
        Label weekHours = new Label(String.format("This Week: %.1fh", driver.getHoursWorkedWeek()));
        weekHours.setStyle("-fx-font-size: 13px;");
        
        hoursInfo.getChildren().addAll(todayHours, weekHours);
        
        ProgressBar hoursProgress = new ProgressBar(driver.getHoursWorkedToday() / 11.0);
        hoursProgress.setPrefWidth(Double.MAX_VALUE);
        hoursProgress.setStyle("-fx-accent: #4CAF50;");
        
        hoursBox.getChildren().addAll(hoursLabel, hoursInfo, hoursProgress);
        content.add(hoursBox);
        
        // Next load if any
        if (driver.getNextLoad() != null) {
            content.add(createDetailRow("Next Load", driver.getNextLoad().getLoadNumber(), "📦"));
        }
        
        return content;
    }
    
    private HBox createDetailRow(String label, String value, String icon) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setStyle("-fx-padding: 10px 0;");
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16px;");
        
        VBox textBox = new VBox(2);
        
        Label labelText = new Label(label);
        labelText.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575; -fx-font-weight: 600;");
        
        Label valueText = new Label(value);
        valueText.setStyle("-fx-font-size: 13px; -fx-text-fill: #424242;");
        valueText.setWrapText(true);
        
        textBox.getChildren().addAll(labelText, valueText);
        
        row.getChildren().addAll(iconLabel, textBox);
        
        return row;
    }
    
    private HBox createStatusBadge(DispatcherDriverStatus.Status status) {
        HBox badge = new HBox();
        badge.setAlignment(Pos.CENTER);
        badge.setPadding(new Insets(4, 12, 4, 12));
        
        String bgColor = Color.web(status.getColor()).deriveColor(0, 0.3, 1.5, 0.2).toString().replace("0x", "#");
        badge.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12px;",
            bgColor
        ));
        
        Label label = new Label(status.getDisplayName());
        label.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: 600;",
            status.getColor()
        ));
        
        badge.getChildren().add(label);
        return badge;
    }
    
    // Custom cell for modern load display
    private class ModernLoadCell extends ListCell<Load> {
        @Override
        protected void updateItem(Load load, boolean empty) {
            super.updateItem(load, empty);
            if (empty || load == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox content = new HBox(10);
                content.setAlignment(Pos.CENTER_LEFT);
                content.setPadding(new Insets(8));
                
                VBox details = new VBox(2);
                
                HBox topLine = new HBox(10);
                topLine.setAlignment(Pos.CENTER_LEFT);
                
                Label loadNum = new Label(load.getLoadNumber());
                loadNum.setStyle("-fx-font-weight: 600; -fx-font-size: 13px;");
                
                Label amount = new Label(String.format("$%.2f", load.getGrossAmount()));
                amount.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: 600;");
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                topLine.getChildren().addAll(loadNum, spacer, amount);
                
                Label route = new Label(getCityName(load.getPickUpLocation()) + " → " + 
                                      getCityName(load.getDropLocation()));
                route.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
                
                details.getChildren().addAll(topLine, route);
                
                content.getChildren().addAll(new Label("📦"), details);
                
                setGraphic(content);
                setText(null);
            }
        }
    }
    
    // Custom cell for modern driver display
    private class ModernDriverCell extends ListCell<DispatcherDriverStatus> {
        @Override
        protected void updateItem(DispatcherDriverStatus driver, boolean empty) {
            super.updateItem(driver, empty);
            if (empty || driver == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox content = new HBox(10);
                content.setAlignment(Pos.CENTER_LEFT);
                content.setPadding(new Insets(8));
                
                VBox details = new VBox(2);
                
                HBox topLine = new HBox(10);
                topLine.setAlignment(Pos.CENTER_LEFT);
                
                Label name = new Label(driver.getDriverName());
                name.setStyle("-fx-font-weight: 600; -fx-font-size: 13px;");
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                HBox statusBadge = createStatusBadge(driver.getStatus());
                
                topLine.getChildren().addAll(name, spacer, statusBadge);
                
                Label info = new Label(driver.getTruckUnit() + " • " + driver.getCurrentLocation());
                info.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
                
                details.getChildren().addAll(topLine, info);
                
                content.getChildren().addAll(new Label("👤"), details);
                
                setGraphic(content);
                setText(null);
            }
        }
    }
    
    private String getCityName(String location) {
        if (location == null || location.isEmpty()) return "";
        String[] parts = location.split(",");
        return parts[0].trim();
    }
    
    private boolean performAssignment() {
        Load selectedLoad = loadCombo.getValue();
        DispatcherDriverStatus selectedDriver = driverCombo.getValue();
        
        if (selectedLoad == null || selectedDriver == null) {
            showModernError("Please select both a load and a driver.");
            return false;
        }
        
        try {
            boolean success = controller.assignLoadToDriver(
                selectedLoad,
                selectedDriver.getDriver(),
                notesArea.getText()
            );

            if (success) {
                showModernSuccess(String.format(
                    "Load %s has been successfully assigned to %s",
                    selectedLoad.getLoadNumber(),
                    selectedDriver.getDriverName()
                ));
            } else {
                showModernError("Failed to assign load. Please try again.");
            }

            return success;
        } catch (Exception e) {
            logger.error("Failed to assign load", e);
            showModernError("Failed to assign load: " + e.getMessage());
            return false;
        }
    }
    
    private void showModernError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Assignment Error");
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