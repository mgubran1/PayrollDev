package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Daily schedule view showing timeline of activities with modern UI
 */
public class DispatcherDailyView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherDailyView.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    
    private final DispatcherController controller;
    private final TableView<DailyEvent> eventTable;
    private final ObservableList<DailyEvent> dailyEvents;
    private final DatePicker datePicker;
    private LocalDate selectedDate;
    
    public DispatcherDailyView(DispatcherController controller) {
        this.controller = controller;
        this.eventTable = new TableView<>();
        this.dailyEvents = FXCollections.observableArrayList();
        this.datePicker = new DatePicker(LocalDate.now());
        this.selectedDate = LocalDate.now();
        
        initializeUI();
        refresh();
    }
    
    private void initializeUI() {
        getStyleClass().add("dispatcher-container");
        
        // Header with modern design
        VBox header = createHeader();
        setTop(header);
        
        // Main content - split pane with modern styling
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.7);
        splitPane.setStyle("-fx-background-color: #FAFAFA;");
        
        // Left side - Event table with modern design
        VBox leftPanel = new VBox(15);
        leftPanel.setPadding(new Insets(20));
        leftPanel.setStyle("-fx-background-color: #FFFFFF;");
        
        Label tableLabel = new Label("Daily Schedule");
        tableLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        setupModernEventTable();
        VBox.setVgrow(eventTable, Priority.ALWAYS);
        
        leftPanel.getChildren().addAll(tableLabel, eventTable);
        
        // Right side - Summary and quick stats with cards
        VBox rightPanel = createModernSummaryPanel();
        
        splitPane.getItems().addAll(leftPanel, rightPanel);
        setCenter(splitPane);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #FFFFFF; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        // Date selection with modern controls
        HBox dateControls = new HBox(15);
        dateControls.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("Date:");
        dateLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #757575;");
        
        datePicker.setStyle("-fx-background-radius: 5px;");
        datePicker.setOnAction(e -> {
            selectedDate = datePicker.getValue();
            refresh();
        });
        
        Button prevBtn = createModernButton("◀ Previous", "#2196F3", false);
        prevBtn.setOnAction(e -> {
            selectedDate = selectedDate.minusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button todayBtn = createModernButton("Today", "#2196F3", true);
        todayBtn.setOnAction(e -> {
            selectedDate = LocalDate.now();
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button nextBtn = createModernButton("Next ▶", "#2196F3", false);
        nextBtn.setOnAction(e -> {
            selectedDate = selectedDate.plusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button refreshBtn = createModernButton("🔄 Refresh", "#4CAF50", false);
        refreshBtn.setOnAction(e -> refresh());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label selectedDateLabel = new Label();
        selectedDateLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        updateDateLabel(selectedDateLabel);
        
        dateControls.getChildren().addAll(
            dateLabel, datePicker, prevBtn, todayBtn, nextBtn,
            spacer, selectedDateLabel,
            spacer, refreshBtn
        );
        
        header.getChildren().add(dateControls);
        
        return header;
    }
    
    private Button createModernButton(String text, String color, boolean isPrimary) {
        Button button = new Button(text);
        if (isPrimary) {
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: 600; " +
                "-fx-background-radius: 5px; -fx-padding: 8px 16px; -fx-cursor: hand;",
                color
            ));
        } else {
            button.setStyle(String.format(
                "-fx-background-color: white; -fx-text-fill: %s; -fx-font-weight: 600; " +
                "-fx-background-radius: 5px; -fx-padding: 8px 16px; -fx-cursor: hand; " +
                "-fx-border-color: %s; -fx-border-width: 1px;",
                color, color
            ));
        }
        
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);");
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);", ""));
        });
        
        return button;
    }
    
    private void updateDateLabel(Label label) {
        label.setText(selectedDate.format(DATE_FORMAT));
    }
    
    private void setupModernEventTable() {
        eventTable.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        eventTable.setRowFactory(tv -> {
            TableRow<DailyEvent> row = new TableRow<>();
            row.setStyle("-fx-background-color: white; -fx-border-width: 0;");
            
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-background-color: #F5F5F5; -fx-cursor: hand;");
                }
            });
            
            row.setOnMouseExited(e -> {
                row.setStyle("-fx-background-color: white;");
            });
            
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showEventDetails(row.getItem());
                }
            });
            
            return row;
        });
        
        // Time column
        TableColumn<DailyEvent, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getTime().format(TIME_FORMAT)));
        timeCol.setPrefWidth(80);
        timeCol.setStyle("-fx-alignment: CENTER;");
        
        // Type column with color coding
        TableColumn<DailyEvent, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getEventType().getDisplayName()));
        typeCol.setPrefWidth(100);
        typeCol.setCellFactory(column -> new TableCell<DailyEvent, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setGraphic(null);
                } else {
                    DailyEvent event = getTableRow().getItem();
                    if (event != null) {
                        HBox badge = createEventTypeBadge(event.getEventType());
                        setGraphic(badge);
                        setText(null);
                    }
                }
            }
        });
        
        // Driver column
        TableColumn<DailyEvent, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getDriverName()));
        driverCol.setPrefWidth(150);
        
        // Load column
        TableColumn<DailyEvent, String> loadCol = new TableColumn<>("Load #");
        loadCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getLoadNumber()));
        loadCol.setPrefWidth(100);
        
        // Customer column
        TableColumn<DailyEvent, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getCustomer()));
        customerCol.setPrefWidth(150);
        
        // Location column
        TableColumn<DailyEvent, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getLocation()));
        locationCol.setPrefWidth(200);
        
        // Status column
        TableColumn<DailyEvent, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getStatus()));
        statusCol.setPrefWidth(100);
        
        eventTable.getColumns().setAll(java.util.List.of(
                timeCol, typeCol, driverCol, loadCol,
                customerCol, locationCol, statusCol));
        eventTable.setItems(dailyEvents);
    }
    
    private HBox createEventTypeBadge(EventType type) {
        HBox badge = new HBox();
        badge.setAlignment(Pos.CENTER);
        badge.setPadding(new Insets(4, 12, 4, 12));
        badge.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12px;",
            type.getColor()
        ));
        
        Label label = new Label(type.getDisplayName());
        label.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 600;");
        
        badge.getChildren().add(label);
        return badge;
    }
    
    private VBox createModernSummaryPanel() {
        VBox panel = new VBox(20);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: #FAFAFA;");
        
        Label titleLabel = new Label("Daily Overview");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        // Statistics cards
        VBox statsContainer = new VBox(15);
        
        // Total Events Card
        VBox totalEventsCard = createStatCard("Total Events", String.valueOf(dailyEvents.size()), "#2196F3", "📅");
        
        // Active Drivers Card
        VBox activeDriversCard = createStatCard("Active Drivers", getActiveDriverCount(), "#4CAF50", "🚚");
        
        // Pickups Card
        VBox pickupsCard = createStatCard("Pickups", getEventTypeCount(EventType.PICKUP), "#FF9800", "▲");
        
        // Deliveries Card
        VBox deliveriesCard = createStatCard("Deliveries", getEventTypeCount(EventType.DELIVERY), "#F44336", "▼");
        
        // In Transit Card
        VBox inTransitCard = createStatCard("In Transit", getInTransitCount(), "#00BCD4", "↔");
        
        statsContainer.getChildren().addAll(totalEventsCard, activeDriversCard, pickupsCard, deliveriesCard, inTransitCard);
        
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #E0E0E0;");
        
        // Driver availability section
        Label availLabel = new Label("Driver Availability");
        availLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: #424242;");
        
        VBox availBox = new VBox(10);
        updateAvailabilitySummary(availBox);
        
        panel.getChildren().addAll(titleLabel, statsContainer, separator, availLabel, availBox);
        
        return panel;
    }
    
    private VBox createStatCard(String title, String value, String color, String icon) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setStyle(String.format(
            "-fx-background-color: white; -fx-background-radius: 8px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        ));
        
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle(String.format("-fx-font-size: 20px; -fx-text-fill: %s;", color));
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format("-fx-font-size: 28px; -fx-font-weight: 700; -fx-text-fill: %s;", color));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(iconLabel, spacer, valueLabel);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        
        card.getChildren().addAll(header, titleLabel);
        
        // Hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle(card.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 3);");
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle(card.getStyle().replace(
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 3);",
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
            ));
        });
        
        return card;
    }
    
    private void refresh() {
        logger.info("Refreshing daily view for date: {}", selectedDate);
        controller.refreshAll();
        loadDailyEvents();
        updateDateLabel((Label) ((HBox) ((VBox) getTop()).getChildren().get(0)).getChildren().get(6));
        
        // Update summary panel
        SplitPane splitPane = (SplitPane) getCenter();
        VBox rightPanel = (VBox) splitPane.getItems().get(1);
        updateSummaryPanel(rightPanel);
    }
    
    private void updateSummaryPanel(VBox panel) {
        VBox statsContainer = (VBox) panel.getChildren().get(1);
        
        // Update each stat card
        VBox totalEventsCard = (VBox) statsContainer.getChildren().get(0);
        updateStatCard(totalEventsCard, String.valueOf(dailyEvents.size()));
        
        VBox activeDriversCard = (VBox) statsContainer.getChildren().get(1);
        updateStatCard(activeDriversCard, getActiveDriverCount());
        
        VBox pickupsCard = (VBox) statsContainer.getChildren().get(2);
        updateStatCard(pickupsCard, getEventTypeCount(EventType.PICKUP));
        
        VBox deliveriesCard = (VBox) statsContainer.getChildren().get(3);
        updateStatCard(deliveriesCard, getEventTypeCount(EventType.DELIVERY));
        
        VBox inTransitCard = (VBox) statsContainer.getChildren().get(4);
        updateStatCard(inTransitCard, getInTransitCount());
        
        // Update availability
        VBox availBox = (VBox) panel.getChildren().get(4);
        updateAvailabilitySummary(availBox);
    }
    
    private void updateStatCard(VBox card, String newValue) {
        HBox header = (HBox) card.getChildren().get(0);
        Label valueLabel = (Label) header.getChildren().get(2);
        valueLabel.setText(newValue);
    }
    
    private void loadDailyEvents() {
        dailyEvents.clear();
        
        // Process all drivers and their loads
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            for (Load load : driver.getAssignedLoads()) {
                // Check for pickup on selected date
                if (load.getPickUpDate() != null && load.getPickUpDate().equals(selectedDate)) {
                    LocalTime time = load.getPickUpTime() != null ? load.getPickUpTime() : LocalTime.of(8, 0);
                    dailyEvents.add(new DailyEvent(
                        LocalDateTime.of(selectedDate, time),
                        EventType.PICKUP,
                        driver.getDriverName(),
                        driver.getTruckUnit(),
                        load.getLoadNumber(),
                        load.getCustomer(),
                        load.getPickUpLocation(),
                        load.getStatus().toString()
                    ));
                }
                
                // Check for delivery on selected date
                if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(selectedDate)) {
                    LocalTime time = load.getDeliveryTime() != null ? load.getDeliveryTime() : LocalTime.of(17, 0);
                    dailyEvents.add(new DailyEvent(
                        LocalDateTime.of(selectedDate, time),
                        EventType.DELIVERY,
                        driver.getDriverName(),
                        driver.getTruckUnit(),
                        load.getLoadNumber(),
                        load.getCustomer(),
                        load.getDropLocation(),
                        load.getStatus().toString()
                    ));
                }
                
                // Check for in-transit loads
                if (isLoadInTransitOnDate(load, selectedDate)) {
                    dailyEvents.add(new DailyEvent(
                        LocalDateTime.of(selectedDate, LocalTime.NOON),
                        EventType.IN_TRANSIT,
                        driver.getDriverName(),
                        driver.getTruckUnit(),
                        load.getLoadNumber(),
                        load.getCustomer(),
                        "En route: " + load.getPickUpLocation() + " → " + load.getDropLocation(),
                        "IN TRANSIT"
                    ));
                }
            }
        }
        
        // Sort events by time
        dailyEvents.sort(Comparator.comparing(DailyEvent::getTime));
    }
    
    private boolean isLoadInTransitOnDate(Load load, LocalDate date) {
        if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
            return date.isAfter(load.getPickUpDate()) && date.isBefore(load.getDeliveryDate()) &&
                   load.getStatus() == Load.Status.IN_TRANSIT;
        }
        return false;
    }
    
    private String getActiveDriverCount() {
        long count = controller.getDriverStatuses().stream()
            .filter(d -> d.getAssignedLoads().stream()
                .anyMatch(load -> isLoadActiveOnDate(load, selectedDate)))
            .count();
        return String.valueOf(count);
    }
    
    private boolean isLoadActiveOnDate(Load load, LocalDate date) {
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) return true;
        if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) return true;
        return isLoadInTransitOnDate(load, date);
    }
    
    private String getEventTypeCount(EventType type) {
        long count = dailyEvents.stream()
            .filter(e -> e.getEventType() == type)
            .count();
        return String.valueOf(count);
    }
    
    private String getInTransitCount() {
        return getEventTypeCount(EventType.IN_TRANSIT);
    }
    
    private void updateAvailabilitySummary(VBox container) {
        container.getChildren().clear();
        
        int available = 0;
        int onRoad = 0;
        int offDuty = 0;
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            switch (driver.getStatus()) {
                case AVAILABLE:
                    available++;
                    break;
                case ON_ROAD:
                case LOADING:
                case UNLOADING:
                    onRoad++;
                    break;
                case OFF_DUTY:
                case ON_BREAK:
                    offDuty++;
                    break;
            }
        }
        
        container.getChildren().addAll(
            createAvailabilityItem("Available", available, "#4CAF50"),
            createAvailabilityItem("On Road", onRoad, "#2196F3"),
            createAvailabilityItem("Off Duty", offDuty, "#F44336")
        );
    }
    
    private HBox createAvailabilityItem(String text, int count, String color) {
        HBox item = new HBox(10);
        item.setPadding(new Insets(8));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setStyle("-fx-background-color: white; -fx-background-radius: 5px;");
        
        Label dot = new Label("●");
        dot.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 16px;", color));
        
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #424242;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label countLabel = new Label(String.valueOf(count));
        countLabel.setStyle(String.format("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: %s;", color));
        
        item.getChildren().addAll(dot, label, spacer, countLabel);
        
        return item;
    }
    
    private void showEventDetails(DailyEvent event) {
        logger.info("Showing details for event: {} - {}", event.getEventType(), event.getLoadNumber());
        // TODO: Implement event details dialog
    }
    
    // Inner class for daily events
    public static class DailyEvent {
        private final LocalDateTime time;
        private final EventType eventType;
        private final String driverName;
        private final String truckUnit;
        private final String loadNumber;
        private final String customer;
        private final String location;
        private final String status;
        
        public DailyEvent(LocalDateTime time, EventType eventType, String driverName,
                         String truckUnit, String loadNumber, String customer,
                         String location, String status) {
            this.time = time;
            this.eventType = eventType;
            this.driverName = driverName;
            this.truckUnit = truckUnit;
            this.loadNumber = loadNumber;
            this.customer = customer;
            this.location = location;
            this.status = status;
        }
        
        // Getters
        public LocalDateTime getTime() { return time; }
        public EventType getEventType() { return eventType; }
        public String getDriverName() { return driverName; }
        public String getTruckUnit() { return truckUnit; }
        public String getLoadNumber() { return loadNumber; }
        public String getCustomer() { return customer; }
        public String getLocation() { return location; }
        public String getStatus() { return status; }
    }
    
    public enum EventType {
        PICKUP("Pick Up", "#4CAF50"),
        DELIVERY("Delivery", "#F44336"),
        IN_TRANSIT("In Transit", "#2196F3"),
        BREAK("Break", "#9C27B0"),
        MAINTENANCE("Maintenance", "#FF9800");
        
        private final String displayName;
        private final String color;
        
        EventType(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
}