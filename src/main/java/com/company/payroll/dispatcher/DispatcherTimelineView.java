package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Timeline view showing 12 or 24 hour grid
 */
public class DispatcherTimelineView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherTimelineView.class);
    
    public enum TimelineMode {
        TWELVE_HOUR(12, 7, 19),  // 7 AM to 7 PM
        TWENTY_FOUR_HOUR(24, 0, 24);  // Full day
        
        private final int hours;
        private final int startHour;
        private final int endHour;
        
        TimelineMode(int hours, int startHour, int endHour) {
            this.hours = hours;
            this.startHour = startHour;
            this.endHour = endHour;
        }
    }
    
    private final DispatcherController controller;
    private final TimelineMode mode;
    private final ScrollPane scrollPane;
    private final GridPane timelineGrid;
    private final DatePicker datePicker;
    private LocalDate selectedDate;
    
    private static final int HOUR_WIDTH = 120;
    private static final int DRIVER_ROW_HEIGHT = 80;
    private static final int HEADER_HEIGHT = 40;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("ha");
    
    public DispatcherTimelineView(DispatcherController controller, TimelineMode mode) {
        this.controller = controller;
        this.mode = mode;
        this.scrollPane = new ScrollPane();
        this.timelineGrid = new GridPane();
        this.datePicker = new DatePicker(LocalDate.now());
        this.selectedDate = LocalDate.now();
        
        initializeUI();
        refresh();
    }
    
    private void initializeUI() {
        // Top toolbar
        HBox toolbar = createToolbar();
        setTop(toolbar);
        
        // Main timeline grid
        scrollPane.setContent(timelineGrid);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        timelineGrid.setGridLinesVisible(true);
        timelineGrid.setStyle("-fx-background-color: white;");
        
        setCenter(scrollPane);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        Label dateLabel = new Label("Date:");
        datePicker.setOnAction(e -> {
            selectedDate = datePicker.getValue();
            refresh();
        });
        
        Button todayBtn = new Button("Today");
        todayBtn.setOnAction(e -> {
            selectedDate = LocalDate.now();
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button prevBtn = new Button("◀");
        prevBtn.setOnAction(e -> {
            selectedDate = selectedDate.minusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button nextBtn = new Button("▶");
        nextBtn.setOnAction(e -> {
            selectedDate = selectedDate.plusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> refresh());
        
        Label modeLabel = new Label(mode == TimelineMode.TWELVE_HOUR ? "12 Hour View" : "24 Hour View");
        modeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #000000;");
        
        toolbar.getChildren().addAll(
            dateLabel, datePicker, prevBtn, todayBtn, nextBtn,
            new Separator(), modeLabel,
            new Separator(), refreshBtn
        );
        
        return toolbar;
    }
    
    private void refresh() {
        logger.info("Refreshing timeline view for date: {}", selectedDate);
        timelineGrid.getChildren().clear();
        timelineGrid.getColumnConstraints().clear();
        timelineGrid.getRowConstraints().clear();
        
        buildTimelineGrid();
    }
    
    private void buildTimelineGrid() {
        // Add column constraints
        // First column for driver names
        ColumnConstraints driverCol = new ColumnConstraints(150);
        driverCol.setHgrow(Priority.NEVER);
        timelineGrid.getColumnConstraints().add(driverCol);
        
        // Hour columns
        for (int hour = mode.startHour; hour < mode.endHour; hour++) {
            ColumnConstraints hourCol = new ColumnConstraints(HOUR_WIDTH);
            hourCol.setHgrow(Priority.NEVER);
            timelineGrid.getColumnConstraints().add(hourCol);
        }
        
        // Add header row
        buildHeaderRow();
        
        // Add driver rows
        List<DispatcherDriverStatus> drivers = controller.getDriverStatuses();
        int rowIndex = 1;
        
        for (DispatcherDriverStatus driver : drivers) {
            buildDriverRow(driver, rowIndex++);
        }
    }
    
    private void buildHeaderRow() {
        // Empty cell for driver column
        Label cornerLabel = new Label("Driver / Time");
        cornerLabel.setStyle("-fx-font-weight: bold; -fx-background-color: #e0e0e0; -fx-padding: 10;");
        cornerLabel.setPrefSize(150, HEADER_HEIGHT);
        cornerLabel.setAlignment(Pos.CENTER);
        timelineGrid.add(cornerLabel, 0, 0);
        
        // Hour headers
        int colIndex = 1;
        for (int hour = mode.startHour; hour < mode.endHour; hour++) {
            LocalTime time = LocalTime.of(hour, 0);
            Label hourLabel = new Label(time.format(HOUR_FORMAT));
            hourLabel.setStyle("-fx-font-weight: bold; -fx-background-color: #e0e0e0; -fx-padding: 10;");
            hourLabel.setPrefSize(HOUR_WIDTH, HEADER_HEIGHT);
            hourLabel.setAlignment(Pos.CENTER);
            
            timelineGrid.add(hourLabel, colIndex++, 0);
        }
    }
    
    private void buildDriverRow(DispatcherDriverStatus driver, int rowIndex) {
        // Driver info cell
        VBox driverCell = new VBox(2);
        driverCell.setPadding(new Insets(5));
        driverCell.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc;");
        
        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #000000;");
        
        Label truckLabel = new Label(driver.getTruckUnit());
        truckLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #000000;");
        
        Label statusLabel = new Label(driver.getStatus().getDisplayName());
        statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #000000;");
        
        driverCell.getChildren().addAll(nameLabel, truckLabel, statusLabel);
        driverCell.setPrefHeight(DRIVER_ROW_HEIGHT);
        
        timelineGrid.add(driverCell, 0, rowIndex);
        
        // Timeline cells
        HBox timelineCells = new HBox();
        timelineCells.setPrefHeight(DRIVER_ROW_HEIGHT);
        
        // Create hour cells
        for (int hour = mode.startHour; hour < mode.endHour; hour++) {
            Pane hourCell = createHourCell(driver, hour);
            timelineGrid.add(hourCell, hour - mode.startHour + 1, rowIndex);
        }
        
        // Overlay load blocks
        overlayLoadBlocks(driver, rowIndex);
    }
    
    private Pane createHourCell(DispatcherDriverStatus driver, int hour) {
        Pane cell = new Pane();
        cell.setPrefSize(HOUR_WIDTH, DRIVER_ROW_HEIGHT);
        cell.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        
        // Highlight current hour
        LocalTime now = LocalTime.now();
        if (selectedDate.equals(LocalDate.now()) && now.getHour() == hour) {
            cell.setStyle(cell.getStyle() + "-fx-background-color: #fff3cd;");
        }
        
        return cell;
    }
    
    private void overlayLoadBlocks(DispatcherDriverStatus driver, int rowIndex) {
        // Get loads for this driver on selected date
        List<Load> driverLoads = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, selectedDate))
            .toList();
        
        for (Load load : driverLoads) {
            Pane loadBlock = createLoadBlock(load);
            
            // Calculate position
            LocalDateTime startTime = getLoadStartTime(load);
            LocalDateTime endTime = getLoadEndTime(load);
            
            if (startTime != null && endTime != null) {
                double startX = calculateTimePosition(startTime);
                double endX = calculateTimePosition(endTime);
                double width = Math.max(endX - startX, 50); // Minimum width
                
                loadBlock.setLayoutX(startX);
                loadBlock.setPrefWidth(width);
                
                // Add to appropriate cell
                int startHour = startTime.getHour();
                if (startHour >= mode.startHour && startHour < mode.endHour) {
                    int colIndex = startHour - mode.startHour + 1;
                    
                    // Get the cell and add load block
                    Pane cell = (Pane) getNodeFromGridPane(timelineGrid, colIndex, rowIndex);
                    if (cell != null) {
                        cell.getChildren().add(loadBlock);
                    }
                }
            }
        }
    }
    
    private Pane createLoadBlock(Load load) {
        VBox block = new VBox(2);
        block.setPadding(new Insets(2));
        block.setPrefHeight(DRIVER_ROW_HEIGHT - 10);
        block.setLayoutY(5);
        
        // Style based on load status
        String bgColor = getLoadStatusColor(load.getStatus());
        block.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: #333; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;",
            bgColor
        ));
        
        // Load info
        Label loadNumLabel = new Label(load.getLoadNumber());
        loadNumLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #000000;");
        
        Label customerLabel = new Label(load.getCustomer());
        customerLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #000000;");
        
        Label routeLabel = new Label(String.format("%s → %s", 
            getCityAbbreviation(load.getPickUpLocation()),
            getCityAbbreviation(load.getDropLocation())
        ));
        routeLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #000000;");
        
        block.getChildren().addAll(loadNumLabel, customerLabel, routeLabel);
        
        // Tooltip with full details
        Tooltip tooltip = new Tooltip(String.format(
            "Load: %s\nPO: %s\nCustomer: %s\nPickup: %s @ %s\nDelivery: %s @ %s\nStatus: %s",
            load.getLoadNumber(),
            load.getPONumber(),
            load.getCustomer(),
            load.getPickUpLocation(),
            load.getPickUpTime() != null ? load.getPickUpTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A",
            load.getDropLocation(),
            load.getDeliveryTime() != null ? load.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A",
            load.getStatus()
        ));
        Tooltip.install(block, tooltip);
        
        // Click handler
        block.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showLoadDetails(load);
            }
        });
        
        return block;
    }
    
    private boolean isLoadOnDate(Load load, LocalDate date) {
        // Check if pickup or delivery is on this date
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
            return true;
        }
        if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
            return true;
        }
        
        // Check if load spans this date
        if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
            return !date.isBefore(load.getPickUpDate()) && !date.isAfter(load.getDeliveryDate());
        }
        
        return false;
    }
    
    private LocalDateTime getLoadStartTime(Load load) {
        if (load.getPickUpDate() != null) {
            LocalTime time = load.getPickUpTime() != null ? load.getPickUpTime() : LocalTime.of(8, 0);
            return LocalDateTime.of(load.getPickUpDate(), time);
        }
        return null;
    }
    
    private LocalDateTime getLoadEndTime(Load load) {
        if (load.getDeliveryDate() != null) {
            LocalTime time = load.getDeliveryTime() != null ? load.getDeliveryTime() : LocalTime.of(17, 0);
            return LocalDateTime.of(load.getDeliveryDate(), time);
        }
        return null;
    }
    
    private double calculateTimePosition(LocalDateTime time) {
        if (!time.toLocalDate().equals(selectedDate)) {
            // Handle multi-day loads
            if (time.toLocalDate().isBefore(selectedDate)) {
                return 150; // Start of timeline (after driver column)
            } else {
                return 150 + (mode.endHour - mode.startHour) * HOUR_WIDTH; // End of timeline
            }
        }
        
        double hourFraction = time.getHour() + (time.getMinute() / 60.0);
        double position = 150 + (hourFraction - mode.startHour) * HOUR_WIDTH;
        
        return Math.max(150, Math.min(position, 150 + (mode.endHour - mode.startHour) * HOUR_WIDTH));
    }
    
    private String getLoadStatusColor(Load.Status status) {
        switch (status) {
            case BOOKED: return "#b6d4fe";
            case ASSIGNED: return "#ffe59b";
            case IN_TRANSIT: return "#90EE90";
            case DELIVERED: return "#98FB98";
            case PAID: return "#c2c2d6";
            default: return "#f0f0f0";
        }
    }
    
    private String getStatusColor(DispatcherDriverStatus.Status status) {
        return status.getColor();
    }
    
    private String getCityAbbreviation(String location) {
        if (location == null || location.isEmpty()) return "";
        
        // Take first 3 letters of city
        String[] parts = location.split(",");
        String city = parts[0].trim();
        
        if (city.length() <= 3) return city;
        return city.substring(0, 3).toUpperCase();
    }
    
    private javafx.scene.Node getNodeFromGridPane(GridPane gridPane, int col, int row) {
        for (javafx.scene.Node node : gridPane.getChildren()) {
            Integer columnIndex = GridPane.getColumnIndex(node);
            Integer rowIndex = GridPane.getRowIndex(node);
            
            if (columnIndex != null && columnIndex == col && 
                rowIndex != null && rowIndex == row) {
                return node;
            }
        }
        return null;
    }
    
    private void showLoadDetails(Load load) {
        logger.info("Showing details for load: {}", load.getLoadNumber());
        // TODO: Implement load details dialog
    }
}