package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import com.company.payroll.dispatcher.LoadDetailsDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Weekly grid view for dispatcher with modern UI
 */
public class DispatcherWeeklyView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherWeeklyView.class);
    
    private final DispatcherController controller;
    private final GridPane weekGrid;
    private final DatePicker weekPicker;
    private LocalDate weekStart;
    
    private static final int DAY_WIDTH = 180;
    private static final int DRIVER_ROW_HEIGHT = 120;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE MM/dd");
    
    public DispatcherWeeklyView(DispatcherController controller) {
        this.controller = controller;
        this.weekGrid = new GridPane();
        this.weekPicker = new DatePicker(LocalDate.now());
        this.weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        
        initializeUI();
        refresh();
    }
    
    private void initializeUI() {
        getStyleClass().add("dispatcher-container");
        
        // Top toolbar with modern design
        HBox toolbar = createToolbar();
        setTop(toolbar);
        
        // Main week grid with enhanced styling
        ScrollPane scrollPane = new ScrollPane(weekGrid);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("week-scroll-pane");
        
        weekGrid.setGridLinesVisible(false);
        weekGrid.setStyle("-fx-background-color: #FAFAFA;");
        weekGrid.setHgap(1);
        weekGrid.setVgap(1);
        
        setCenter(scrollPane);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(15);
        toolbar.setPadding(new Insets(15));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #FFFFFF; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label weekLabel = new Label("Week of:");
        weekLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #757575;");
        
        weekPicker.setStyle("-fx-background-radius: 5px;");
        weekPicker.setOnAction(e -> {
            weekStart = weekPicker.getValue().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            refresh();
        });
        
        Button prevWeekBtn = createModernButton("◀ Previous Week", "#2196F3", false);
        prevWeekBtn.setOnAction(e -> {
            weekStart = weekStart.minusWeeks(1);
            weekPicker.setValue(weekStart);
            refresh();
        });
        
        Button nextWeekBtn = createModernButton("Next Week ▶", "#2196F3", false);
        nextWeekBtn.setOnAction(e -> {
            weekStart = weekStart.plusWeeks(1);
            weekPicker.setValue(weekStart);
            refresh();
        });
        
        Button currentWeekBtn = createModernButton("Current Week", "#2196F3", true);
        currentWeekBtn.setOnAction(e -> {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            weekPicker.setValue(weekStart);
            refresh();
        });
        
        Button refreshBtn = createModernButton("🔄 Refresh", "#4CAF50", false);
        refreshBtn.setOnAction(e -> refresh());
        
        // Summary card
        HBox summaryCard = createSummaryCard();
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        toolbar.getChildren().addAll(
            weekLabel, weekPicker, prevWeekBtn, currentWeekBtn, nextWeekBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            summaryCard,
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
    
    private HBox createSummaryCard() {
        HBox card = new HBox(20);
        card.setPadding(new Insets(10, 15, 10, 15));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color: #F5F5F5; -fx-background-radius: 8px;"
        );
        
        VBox driversBox = createStatBox("Active Drivers", "0", "#2196F3");
        VBox loadsBox = createStatBox("Total Loads", "0", "#4CAF50");
        
        card.getChildren().addAll(driversBox, loadsBox);
        updateSummaryCard(card);
        
        return card;
    }
    
    private VBox createStatBox(String label, String value, String color) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: %s;", color));
        valueLabel.getStyleClass().add("stat-value");
        
        Label labelText = new Label(label);
        labelText.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575;");
        
        box.getChildren().addAll(valueLabel, labelText);
        return box;
    }
    
    private void updateSummaryCard(HBox card) {
        int totalLoads = 0;
        int activeDrivers = 0;
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            boolean hasLoadsThisWeek = driver.getAssignedLoads().stream()
                .anyMatch(load -> isLoadInWeek(load));
            
            if (hasLoadsThisWeek) {
                activeDrivers++;
                totalLoads += driver.getAssignedLoads().stream()
                    .filter(this::isLoadInWeek)
                    .count();
            }
        }
        
        VBox driversBox = (VBox) card.getChildren().get(0);
        VBox loadsBox = (VBox) card.getChildren().get(1);
        
        Label driversValue = (Label) driversBox.getChildren().get(0);
        Label loadsValue = (Label) loadsBox.getChildren().get(0);
        
        driversValue.setText(String.valueOf(activeDrivers));
        loadsValue.setText(String.valueOf(totalLoads));
    }
    
    private void refresh() {
        logger.info("Refreshing weekly view for week starting: {}", weekStart);
        weekGrid.getChildren().clear();
        weekGrid.getColumnConstraints().clear();
        weekGrid.getRowConstraints().clear();
        
        buildWeekGrid();
        
        // Update summary after building grid
        HBox toolbar = (HBox) getTop();
        for (javafx.scene.Node node : toolbar.getChildren()) {
            if (node instanceof HBox && node.getStyle().contains("F5F5F5")) {
                updateSummaryCard((HBox) node);
                break;
            }
        }
    }
    
    private void buildWeekGrid() {
        // Add column constraints
        // First column for driver names
        ColumnConstraints driverCol = new ColumnConstraints(160);
        driverCol.setHgrow(Priority.NEVER);
        weekGrid.getColumnConstraints().add(driverCol);
        
        // Day columns
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints(DAY_WIDTH);
            dayCol.setHgrow(Priority.ALWAYS);
            weekGrid.getColumnConstraints().add(dayCol);
        }
        
        // Add header row
        buildHeaderRow();
        
        // Add driver rows
        List<DispatcherDriverStatus> drivers = controller.getDriverStatuses();
        int rowIndex = 1;
        
        for (DispatcherDriverStatus driver : drivers) {
            // Only show drivers with loads this week
            if (hasLoadsInWeek(driver)) {
                buildDriverWeekRow(driver, rowIndex++);
            }
        }
        
        // Add summary row
        buildSummaryRow(rowIndex);
    }
    
    private boolean hasLoadsInWeek(DispatcherDriverStatus driver) {
        return driver.getAssignedLoads().stream()
            .anyMatch(this::isLoadInWeek);
    }
    
    private boolean isLoadInWeek(Load load) {
        LocalDate weekEnd = weekStart.plusDays(6);
        
        if (load.getPickUpDate() != null) {
            if (!load.getPickUpDate().isBefore(weekStart) && !load.getPickUpDate().isAfter(weekEnd)) {
                return true;
            }
        }
        
        if (load.getDeliveryDate() != null) {
            if (!load.getDeliveryDate().isBefore(weekStart) && !load.getDeliveryDate().isAfter(weekEnd)) {
                return true;
            }
        }
        
        // Check if load spans the week
        if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
            return !load.getDeliveryDate().isBefore(weekStart) && !load.getPickUpDate().isAfter(weekEnd);
        }
        
        return false;
    }
    
    private void buildHeaderRow() {
        // Corner cell
        Label cornerLabel = new Label("Driver / Day");
        cornerLabel.setStyle(
            "-fx-font-weight: 600; -fx-font-size: 13px; -fx-text-fill: #757575;"
        );
        cornerLabel.setPrefSize(160, 50);
        cornerLabel.setAlignment(Pos.CENTER);
        
        VBox cornerBox = new VBox(cornerLabel);
        cornerBox.setAlignment(Pos.CENTER);
        cornerBox.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        weekGrid.add(cornerBox, 0, 0);
        
        // Day headers
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            VBox dayHeader = new VBox(2);
            dayHeader.setAlignment(Pos.CENTER);
            dayHeader.setPadding(new Insets(10));
            dayHeader.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
            
            Label dayLabel = new Label(date.format(DAY_FORMAT));
            dayLabel.setStyle("-fx-font-weight: 600; -fx-font-size: 14px; -fx-text-fill: #424242;");
            
            // Highlight today with modern style
            if (date.equals(LocalDate.now())) {
                dayHeader.setStyle("-fx-background-color: #E3F2FD; -fx-border-color: #2196F3; -fx-border-width: 0 1 2 0;");
                dayLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #1976D2;");
            }
            
            dayHeader.getChildren().add(dayLabel);
            weekGrid.add(dayHeader, i + 1, 0);
        }
    }
    
    private void buildDriverWeekRow(DispatcherDriverStatus driver, int rowIndex) {
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
        
        // Week summary for driver
        int weekLoads = (int) driver.getAssignedLoads().stream()
            .filter(this::isLoadInWeek)
            .count();
        Label summaryLabel = new Label(weekLoads + " loads this week");
        summaryLabel.setStyle("-fx-font-size: 11px; -fx-font-style: italic; -fx-text-fill: #9E9E9E;");
        
        driverCell.getChildren().addAll(nameLabel, truckLabel, statusBadge, summaryLabel);
        driverCell.setPrefHeight(DRIVER_ROW_HEIGHT);
        
        weekGrid.add(driverCell, 0, rowIndex);
        
        // Day cells
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            VBox dayCell = createModernDayCell(driver, date);
            weekGrid.add(dayCell, dayOffset + 1, rowIndex);
        }
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
    
    private VBox createModernDayCell(DispatcherDriverStatus driver, LocalDate date) {
        VBox cell = new VBox(5);
        cell.setPadding(new Insets(8));
        cell.setPrefHeight(DRIVER_ROW_HEIGHT);
        cell.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        
        // Get loads for this day
        List<Load> dayLoads = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, date))
            .collect(Collectors.toList());
        
        // Add load entries with modern design
        for (Load load : dayLoads) {
            HBox loadEntry = createModernLoadEntry(load, date);
            cell.getChildren().add(loadEntry);
        }
        
        // Highlight if driver is unavailable
        if (driver.getStatus() == DispatcherDriverStatus.Status.OFF_DUTY && date.equals(LocalDate.now())) {
            cell.setStyle(cell.getStyle() + "-fx-background-color: #FFEBEE;");
        }
        
        // Add hover effect
        cell.setOnMouseEntered(e -> {
            if (!cell.getStyle().contains("#FFEBEE")) {
                cell.setStyle(cell.getStyle() + "-fx-background-color: #F5F5F5;");
            }
        });
        
        cell.setOnMouseExited(e -> {
            if (!cell.getStyle().contains("#FFEBEE")) {
                cell.setStyle(cell.getStyle().replace("-fx-background-color: #F5F5F5;", "-fx-background-color: #FFFFFF;"));
            }
        });
        
        return cell;
    }
    
    private HBox createModernLoadEntry(Load load, LocalDate date) {
        HBox entry = new HBox(5);
        entry.setPadding(new Insets(4, 8, 4, 8));
        entry.setAlignment(Pos.CENTER_LEFT);
        
        String bgColor = getLoadStatusColor(load.getStatus());
        String borderColor = getBorderColor(load.getStatus());
        
        entry.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; " +
            "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-cursor: hand;",
            bgColor, borderColor
        ));
        
        // Icon based on whether it's pickup or delivery
        String icon = "";
        String iconColor = "";
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
            icon = "▲"; // Pickup
            iconColor = "#4CAF50";
        } else if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
            icon = "▼"; // Delivery
            iconColor = "#F44336";
        } else {
            icon = "↔"; // In transit
            iconColor = "#2196F3";
        }
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle(String.format("-fx-font-size: 12px; -fx-text-fill: %s; -fx-font-weight: bold;", iconColor));
        
        Label loadLabel = new Label(load.getLoadNumber());
        loadLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #212121;");
        
        entry.getChildren().addAll(iconLabel, loadLabel);
        
        // Enhanced tooltip
        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append("Load: ").append(load.getLoadNumber()).append("\n");
        tooltipText.append("Customer: ").append(load.getCustomer()).append("\n");
        
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
            tooltipText.append("PICKUP: ").append(load.getPickUpLocation());
            if (load.getPickUpTime() != null) {
                tooltipText.append(" @ ").append(load.getPickUpTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        } else if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
            tooltipText.append("DELIVERY: ").append(load.getDropLocation());
            if (load.getDeliveryTime() != null) {
                tooltipText.append(" @ ").append(load.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        } else {
            tooltipText.append("IN TRANSIT");
        }
        
        tooltipText.append("\nAmount: $").append(String.format("%.2f", load.getGrossAmount()));
        
        Tooltip tooltip = new Tooltip(tooltipText.toString());
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(entry, tooltip);
        
        // Hover effect
        entry.setOnMouseEntered(e -> {
            entry.setScaleX(1.05);
            entry.setScaleY(1.05);
            entry.setStyle(entry.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);");
        });
        
        entry.setOnMouseExited(e -> {
            entry.setScaleX(1.0);
            entry.setScaleY(1.0);
            entry.setStyle(entry.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);", ""));
        });
        
        // Click handler
        entry.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showLoadDetails(load);
            }
        });
        
        return entry;
    }
    
    private void buildSummaryRow(int rowIndex) {
        // Summary label
        Label summaryLabel = new Label("Daily Summary");
        summaryLabel.setStyle(
            "-fx-font-weight: 600; -fx-font-size: 13px; -fx-text-fill: #757575;"
        );
        summaryLabel.setPrefHeight(60);
        summaryLabel.setAlignment(Pos.CENTER);
        
        VBox summaryBox = new VBox(summaryLabel);
        summaryBox.setAlignment(Pos.CENTER);
        summaryBox.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        weekGrid.add(summaryBox, 0, rowIndex);
        
        // Daily summaries
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            VBox summaryCell = createDailySummary(date);
            weekGrid.add(summaryCell, dayOffset + 1, rowIndex);
        }
    }
    
    private VBox createDailySummary(LocalDate date) {
        VBox summary = new VBox(5);
        summary.setPadding(new Insets(8));
        summary.setAlignment(Pos.CENTER);
        summary.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 1 0;");
        summary.setPrefHeight(60);
        
        // Count loads by type
        int pickups = 0;
        int deliveries = 0;
        int inTransit = 0;
        
        for (DispatcherDriverStatus driver : controller.getDriverStatuses()) {
            for (Load load : driver.getAssignedLoads()) {
                if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
                    pickups++;
                }
                if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
                    deliveries++;
                }
                if (isLoadInTransit(load, date)) {
                    inTransit++;
                }
            }
        }
        
        HBox countsBox = new HBox(10);
        countsBox.setAlignment(Pos.CENTER);
        
        Label pickupLabel = new Label("▲" + pickups);
        pickupLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        
        Label deliveryLabel = new Label("▼" + deliveries);
        deliveryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #F44336;");
        
        countsBox.getChildren().addAll(pickupLabel, deliveryLabel);
        
        if (inTransit > 0) {
            Label transitLabel = new Label("↔" + inTransit);
            transitLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2196F3;");
            summary.getChildren().addAll(countsBox, transitLabel);
        } else {
            summary.getChildren().add(countsBox);
        }
        
        return summary;
    }
    
    private boolean isLoadOnDate(Load load, LocalDate date) {
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
            return true;
        }
        if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
            return true;
        }
        return isLoadInTransit(load, date);
    }
    
    private boolean isLoadInTransit(Load load, LocalDate date) {
        if (load.getPickUpDate() != null && load.getDeliveryDate() != null) {
            return date.isAfter(load.getPickUpDate()) && date.isBefore(load.getDeliveryDate());
        }
        return false;
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
    
    private void showLoadDetails(Load load) {
        logger.info("Showing details for load: {}", load.getLoadNumber());
        new LoadDetailsDialog(load).showAndWait();
    }
}