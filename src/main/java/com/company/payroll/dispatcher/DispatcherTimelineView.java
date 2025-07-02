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
import javafx.scene.effect.DropShadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Timeline view showing 12 or 24 hour grid with modern UI
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
    private static final int DRIVER_ROW_HEIGHT = 90;
    private static final int HEADER_HEIGHT = 50;
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
        getStyleClass().add("dispatcher-container");
        
        // Top toolbar with modern styling
        HBox toolbar = createToolbar();
        setTop(toolbar);
        
        // Main timeline grid with enhanced styling
        scrollPane.setContent(timelineGrid);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("timeline-scroll-pane");
        
        timelineGrid.setGridLinesVisible(false);
        timelineGrid.setStyle("-fx-background-color: #FAFAFA;");
        timelineGrid.setHgap(1);
        timelineGrid.setVgap(1);
        
        setCenter(scrollPane);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(15);
        toolbar.setPadding(new Insets(15));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #FFFFFF; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label dateLabel = new Label("Date:");
        dateLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #757575;");
        
        datePicker.setStyle("-fx-background-radius: 5px;");
        datePicker.setOnAction(e -> {
            selectedDate = datePicker.getValue();
            refresh();
        });
        
        Button todayBtn = createModernButton("Today", "#2196F3", true);
        todayBtn.setOnAction(e -> {
            selectedDate = LocalDate.now();
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button prevBtn = createIconButton("◀", "Previous Day");
        prevBtn.setOnAction(e -> {
            selectedDate = selectedDate.minusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button nextBtn = createIconButton("▶", "Next Day");
        nextBtn.setOnAction(e -> {
            selectedDate = selectedDate.plusDays(1);
            datePicker.setValue(selectedDate);
            refresh();
        });
        
        Button refreshBtn = createModernButton("🔄 Refresh", "#4CAF50", false);
        refreshBtn.setOnAction(e -> refresh());
        
        Label modeLabel = new Label(mode == TimelineMode.TWELVE_HOUR ? "12 Hour View" : "24 Hour View");
        modeLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #212121; -fx-font-size: 16px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        toolbar.getChildren().addAll(
            modeLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            dateLabel, datePicker, prevBtn, todayBtn, nextBtn,
            spacer, refreshBtn
        );
        
        return toolbar;
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
    
    private Button createIconButton(String icon, String tooltip) {
        Button button = new Button(icon);
        button.setStyle(
            "-fx-background-color: transparent; -fx-font-size: 18px; " +
            "-fx-padding: 8px; -fx-cursor: hand; -fx-background-radius: 20px;"
        );
        
        if (tooltip != null) {
            button.setTooltip(new Tooltip(tooltip));
        }
        
        button.setOnMouseEntered(e -> button.setStyle(button.getStyle() + "-fx-background-color: #F5F5F5;"));
        button.setOnMouseExited(e -> button.setStyle(button.getStyle().replace("-fx-background-color: #F5F5F5;", "-fx-background-color: transparent;")));
        
        return button;
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
        ColumnConstraints driverCol = new ColumnConstraints(160);
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
        cornerLabel.setStyle(
            "-fx-font-weight: 600; -fx-background-color: #F5F5F5; -fx-padding: 15px; " +
            "-fx-font-size: 13px; -fx-text-fill: #757575;"
        );
        cornerLabel.setPrefSize(160, HEADER_HEIGHT);
        cornerLabel.setAlignment(Pos.CENTER);
        
        VBox cornerBox = new VBox(cornerLabel);
        cornerBox.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        timelineGrid.add(cornerBox, 0, 0);
        
        // Hour headers
        int colIndex = 1;
        for (int hour = mode.startHour; hour < mode.endHour; hour++) {
            LocalTime time = LocalTime.of(hour, 0);
            Label hourLabel = new Label(time.format(HOUR_FORMAT));
            hourLabel.setStyle(
                "-fx-font-weight: 600; -fx-font-size: 14px; -fx-text-fill: #424242;"
            );
            hourLabel.setPrefSize(HOUR_WIDTH, HEADER_HEIGHT);
            hourLabel.setAlignment(Pos.CENTER);
            
            VBox headerBox = new VBox(hourLabel);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
            
            timelineGrid.add(headerBox, colIndex++, 0);
        }
    }
    
    private void buildDriverRow(DispatcherDriverStatus driver, int rowIndex) {
        // Driver info cell with modern design
        VBox driverCell = new VBox(5);
        driverCell.setPadding(new Insets(10));
        driverCell.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        
        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.setStyle("-fx-font-weight: 600; -fx-font-size: 14px; -fx-text-fill: #212121;");
        
        Label truckLabel = new Label(driver.getTruckUnit());
        truckLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        
        // Status badge
        HBox statusBadge = createStatusBadge(driver.getStatus());
        
        driverCell.getChildren().addAll(nameLabel, truckLabel, statusBadge);
        driverCell.setPrefHeight(DRIVER_ROW_HEIGHT);
        
        timelineGrid.add(driverCell, 0, rowIndex);
        
        // Timeline cells
        for (int hour = mode.startHour; hour < mode.endHour; hour++) {
            Pane hourCell = createHourCell(driver, hour);
            timelineGrid.add(hourCell, hour - mode.startHour + 1, rowIndex);
        }
        
        // Overlay load blocks
        overlayLoadBlocks(driver, rowIndex);
    }
    
    private HBox createStatusBadge(DispatcherDriverStatus.Status status) {
        HBox badge = new HBox();
        badge.setAlignment(Pos.CENTER);
        badge.setPadding(new Insets(4, 12, 4, 12));
        badge.setMaxWidth(Region.USE_PREF_SIZE);
        
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
    
    private Pane createHourCell(DispatcherDriverStatus driver, int hour) {
        Pane cell = new Pane();
        cell.setPrefSize(HOUR_WIDTH, DRIVER_ROW_HEIGHT);
        cell.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        
        // Highlight current hour with modern style
        LocalTime now = LocalTime.now();
        if (selectedDate.equals(LocalDate.now()) && now.getHour() == hour) {
            cell.setStyle(cell.getStyle() + "-fx-background-color: #FFF9C4;");
        }
        
        // Add hover effect
        cell.setOnMouseEntered(e -> {
            if (!cell.getStyle().contains("#FFF9C4")) {
                cell.setStyle(cell.getStyle() + "-fx-background-color: #F5F5F5;");
            }
        });
        
        cell.setOnMouseExited(e -> {
            if (!cell.getStyle().contains("#FFF9C4")) {
                cell.setStyle(cell.getStyle().replace("-fx-background-color: #F5F5F5;", "-fx-background-color: #FFFFFF;"));
            }
        });
        
        return cell;
    }
    
    private void overlayLoadBlocks(DispatcherDriverStatus driver, int rowIndex) {
        // Get loads for this driver on selected date
        List<Load> driverLoads = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, selectedDate))
            .toList();
        
        for (Load load : driverLoads) {
            Pane loadBlock = createModernLoadBlock(load);
            
            // Calculate position
            LocalDateTime startTime = getLoadStartTime(load);
            LocalDateTime endTime = getLoadEndTime(load);
            
            if (startTime != null && endTime != null) {
                double startX = calculateTimePosition(startTime);
                double endX = calculateTimePosition(endTime);
                double width = Math.max(endX - startX, 60); // Minimum width
                
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
    
    private Pane createModernLoadBlock(Load load) {
        VBox block = new VBox(3);
        block.setPadding(new Insets(8));
        block.setPrefHeight(DRIVER_ROW_HEIGHT - 20);
        block.setLayoutY(10);
        
        // Modern style based on load status
        String bgColor = getLoadStatusColor(load.getStatus());
        String borderColor = getBorderColor(load.getStatus());
        
        block.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
            "-fx-border-radius: 6px; -fx-background-radius: 6px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);",
            bgColor, borderColor
        ));
        
        // Load info with modern typography
        Label loadNumLabel = new Label(load.getLoadNumber());
        loadNumLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #212121;");
        
        Label customerLabel = new Label(load.getCustomer());
        customerLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #424242;");
        customerLabel.setMaxWidth(block.getPrefWidth() - 16);
        customerLabel.setEllipsisString("...");
        
        Label routeLabel = new Label(String.format("%s → %s", 
            getCityAbbreviation(load.getPickUpLocation()),
            getCityAbbreviation(load.getDropLocation())
        ));
        routeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #757575; -fx-font-weight: 500;");
        
        block.getChildren().addAll(loadNumLabel, customerLabel, routeLabel);
        
        // Enhanced tooltip
        Tooltip tooltip = new Tooltip(String.format(
            "Load: %s\nPO: %s\nCustomer: %s\nPickup: %s @ %s\nDelivery: %s @ %s\nStatus: %s\nAmount: $%.2f",
            load.getLoadNumber(),
            load.getPONumber(),
            load.getCustomer(),
            load.getPickUpLocation(),
            load.getPickUpTime() != null ? load.getPickUpTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A",
            load.getDropLocation(),
            load.getDeliveryTime() != null ? load.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A",
            load.getStatus(),
            load.getGrossAmount()
        ));
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(block, tooltip);
        
        // Hover effect
        block.setOnMouseEntered(e -> {
            block.setStyle(block.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 3);");
            block.setScaleX(1.02);
            block.setScaleY(1.02);
        });
        
        block.setOnMouseExited(e -> {
            block.setStyle(block.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 3);", 
                                                   "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);"));
            block.setScaleX(1.0);
            block.setScaleY(1.0);
        });
        
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
                return 160; // Start of timeline (after driver column)
            } else {
                return 160 + (mode.endHour - mode.startHour) * HOUR_WIDTH; // End of timeline
            }
        }
        
        double hourFraction = time.getHour() + (time.getMinute() / 60.0);
        double position = 160 + (hourFraction - mode.startHour) * HOUR_WIDTH;
        
        return Math.max(160, Math.min(position, 160 + (mode.endHour - mode.startHour) * HOUR_WIDTH));
    }
    
    private String getLoadStatusColor(Load.Status status) {
        switch (status) {
            case BOOKED: return "#E3F2FD";
            case ASSIGNED: return "#FFF3E0";
            case IN_TRANSIT: return "#E8F5E9";
            case DELIVERED: return "#F3E5F5";
            case PAID: return "#E8EAF6";
            default: return "#FAFAFA";
        }
    }
    
    private String getBorderColor(Load.Status status) {
        switch (status) {
            case BOOKED: return "#2196F3";
            case ASSIGNED: return "#FF9800";
            case IN_TRANSIT: return "#4CAF50";
            case DELIVERED: return "#9C27B0";
            case PAID: return "#3F51B5";
            default: return "#E0E0E0";
        }
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
        new LoadDetailsDialog(load).showAndWait();
    }
}