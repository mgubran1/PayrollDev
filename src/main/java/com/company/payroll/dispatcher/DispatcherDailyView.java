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
 * Daily schedule view showing timeline of activities
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
        // Header
        VBox header = createHeader();
        setTop(header);
        
        // Main content - split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.7);
        
        // Left side - Event table
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        
        Label tableLabel = new Label("Daily Schedule");
        tableLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        setupEventTable();
        VBox.setVgrow(eventTable, Priority.ALWAYS);
        
        leftPanel.getChildren().addAll(tableLabel, eventTable);
        
        // Right side - Summary and quick stats
        VBox rightPanel = createSummaryPanel();
        
        splitPane.getItems().addAll(leftPanel, rightPanel);
        setCenter(splitPane);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        // Date selection
        HBox dateControls = new HBox(10);
        dateControls.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("Date:");
        datePicker.setOnAction(e -> {
            selectedDate = datePicker.getValue();
            refresh();
        });
        
        Button prevBtn = new Button("◀ Previous");
        prevBtn.setOnAction(e -> {
            selectedDate = selectedDate.minusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button todayBtn = new Button("Today");
        todayBtn.setOnAction(e -> {
            selectedDate = LocalDate.now();
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button nextBtn = new Button("Next ▶");
        nextBtn.setOnAction(e -> {
            selectedDate = selectedDate.plusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> refresh());
        
        Label selectedDateLabel = new Label();
        selectedDateLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        updateDateLabel(selectedDateLabel);
        
        dateControls.getChildren().addAll(
            dateLabel, datePicker, prevBtn, todayBtn, nextBtn,
            new Separator(), selectedDateLabel,
            new Separator(), refreshBtn
        );
        
        header.getChildren().add(dateControls);
        
        return header;
    }
    
    private void updateDateLabel(Label label) {
        label.setText(selectedDate.format(DATE_FORMAT));
    }
    
    private void setupEventTable() {
        // Time column
        TableColumn<DailyEvent, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getTime().format(TIME_FORMAT)));
        timeCol.setPrefWidth(80);
        timeCol.setStyle("-fx-alignment: CENTER;");
        
        // Type column
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
                } else {
                    setText(item);
                    DailyEvent event = getTableRow().getItem();
                    if (event != null) {
                        setStyle("-fx-background-color: " + event.getEventType().getColor() + 
                                "; -fx-font-weight: bold;");
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
        
        // Row factory for styling
        eventTable.setRowFactory(tv -> {
            TableRow<DailyEvent> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showEventDetails(row.getItem());
                }
            });
            return row;
        });
    }
    
    private VBox createSummaryPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f9f9f9;");
        
        Label titleLabel = new Label("Daily Summary");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        // Statistics grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(10);
        statsGrid.setVgap(5);
        
        int row = 0;
        addStatRow(statsGrid, row++, "Total Events:", String.valueOf(dailyEvents.size()));
        addStatRow(statsGrid, row++, "Active Drivers:", getActiveDriverCount());
        addStatRow(statsGrid, row++, "Pickups:", getEventTypeCount(EventType.PICKUP));
        addStatRow(statsGrid, row++, "Deliveries:", getEventTypeCount(EventType.DELIVERY));
        addStatRow(statsGrid, row++, "In Transit:", getInTransitCount());
        
        Separator separator = new Separator();
        
        // Driver availability summary
        Label availLabel = new Label("Driver Availability");
        availLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        VBox availBox = new VBox(5);
        updateAvailabilitySummary(availBox);
        
        panel.getChildren().addAll(titleLabel, statsGrid, separator, availLabel, availBox);
        
        return panel;
    }
    
    private void addStatRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold;");
        
        Label valueNode = new Label(value);
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
    
    private void refresh() {
        logger.info("Refreshing daily view for date: {}", selectedDate);
        controller.refreshAll();
        loadDailyEvents();
        updateDateLabel((Label) ((HBox) ((VBox) getTop()).getChildren().get(0)).getChildren().get(6));
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
            createAvailabilityLabel("Available", available, Color.GREEN),
            createAvailabilityLabel("On Road", onRoad, Color.BLUE),
            createAvailabilityLabel("Off Duty", offDuty, Color.RED)
        );
    }
    
    private Label createAvailabilityLabel(String text, int count, Color color) {
        Label label = new Label(String.format("%s: %d", text, count));
        label.setTextFill(color);
        label.setStyle("-fx-font-size: 11;");
        return label;
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
        PICKUP("Pick Up", "#90EE90"),
        DELIVERY("Delivery", "#FFB6C1"),
        IN_TRANSIT("In Transit", "#87CEEB"),
        BREAK("Break", "#DDA0DD"),
        MAINTENANCE("Maintenance", "#F0E68C");
        
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