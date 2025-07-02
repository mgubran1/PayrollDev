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
 * Weekly grid view for dispatcher
 */
public class DispatcherWeeklyView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherWeeklyView.class);
    
    private final DispatcherController controller;
    private final GridPane weekGrid;
    private final DatePicker weekPicker;
    private LocalDate weekStart;
    
    private static final int DAY_WIDTH = 180;
    private static final int DRIVER_ROW_HEIGHT = 100;
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
        // Top toolbar
        HBox toolbar = createToolbar();
        setTop(toolbar);
        
        // Main week grid
        ScrollPane scrollPane = new ScrollPane(weekGrid);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        weekGrid.setGridLinesVisible(true);
        weekGrid.setStyle("-fx-background-color: white;");
        
        setCenter(scrollPane);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        Label weekLabel = new Label("Week of:");
        weekPicker.setOnAction(e -> {
            weekStart = weekPicker.getValue().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            refresh();
        });
        
        Button prevWeekBtn = new Button("◀ Previous Week");
        prevWeekBtn.setOnAction(e -> {
            weekStart = weekStart.minusWeeks(1);
            weekPicker.setValue(weekStart);
            refresh();
        });
        
        Button nextWeekBtn = new Button("Next Week ▶");
        nextWeekBtn.setOnAction(e -> {
            weekStart = weekStart.plusWeeks(1);
            weekPicker.setValue(weekStart);
            refresh();
        });
        
        Button currentWeekBtn = new Button("Current Week");
        currentWeekBtn.setOnAction(e -> {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            weekPicker.setValue(weekStart);
            refresh();
        });
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> refresh());
        
        Label summaryLabel = new Label();
        updateSummaryLabel(summaryLabel);
        
        toolbar.getChildren().addAll(
            weekLabel, weekPicker, prevWeekBtn, currentWeekBtn, nextWeekBtn,
            new Separator(), summaryLabel,
            new Separator(), refreshBtn
        );
        
        return toolbar;
    }
    
    private void updateSummaryLabel(Label label) {
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
        
        label.setText(String.format("Week Summary: %d Active Drivers, %d Total Loads", activeDrivers, totalLoads));
        label.setStyle("-fx-font-weight: bold;");
    }
    
    private void refresh() {
        logger.info("Refreshing weekly view for week starting: {}", weekStart);
        weekGrid.getChildren().clear();
        weekGrid.getColumnConstraints().clear();
        weekGrid.getRowConstraints().clear();
        
        buildWeekGrid();
    }
    
    private void buildWeekGrid() {
        // Add column constraints
        // First column for driver names
        ColumnConstraints driverCol = new ColumnConstraints(150);
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
        cornerLabel.setStyle("-fx-font-weight: bold; -fx-background-color: #e0e0e0; -fx-padding: 10;");
        cornerLabel.setPrefSize(150, 40);
        cornerLabel.setAlignment(Pos.CENTER);
        weekGrid.add(cornerLabel, 0, 0);
        
        // Day headers
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            VBox dayHeader = new VBox(2);
            dayHeader.setAlignment(Pos.CENTER);
            dayHeader.setPadding(new Insets(5));
            dayHeader.setStyle("-fx-background-color: #e0e0e0;");
            
            Label dayLabel = new Label(date.format(DAY_FORMAT));
            dayLabel.setStyle("-fx-font-weight: bold;");
            
            // Highlight today
            if (date.equals(LocalDate.now())) {
                dayHeader.setStyle("-fx-background-color: #fff3cd;");
            }
            
            dayHeader.getChildren().add(dayLabel);
            weekGrid.add(dayHeader, i + 1, 0);
        }
    }
    
    private void buildDriverWeekRow(DispatcherDriverStatus driver, int rowIndex) {
        // Driver info cell
        VBox driverCell = new VBox(3);
        driverCell.setPadding(new Insets(5));
        driverCell.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc;");
        
        Label nameLabel = new Label(driver.getDriverName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #000000;");
        
        Label truckLabel = new Label(driver.getTruckUnit());
        truckLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #000000;");
        
        Label statusLabel = new Label(driver.getStatus().getDisplayName());
        statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #000000;");
        
        // Week summary for driver
        int weekLoads = (int) driver.getAssignedLoads().stream()
            .filter(this::isLoadInWeek)
            .count();
        Label summaryLabel = new Label(weekLoads + " loads");
        summaryLabel.setStyle("-fx-font-size: 10; -fx-font-style: italic; -fx-text-fill: #000000;");
        
        driverCell.getChildren().addAll(nameLabel, truckLabel, statusLabel, summaryLabel);
        driverCell.setPrefHeight(DRIVER_ROW_HEIGHT);
        
        weekGrid.add(driverCell, 0, rowIndex);
        
        // Day cells
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            VBox dayCell = createDayCell(driver, date);
            weekGrid.add(dayCell, dayOffset + 1, rowIndex);
        }
    }
    
    private VBox createDayCell(DispatcherDriverStatus driver, LocalDate date) {
        VBox cell = new VBox(3);
        cell.setPadding(new Insets(3));
        cell.setPrefHeight(DRIVER_ROW_HEIGHT);
        cell.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        
        // Get loads for this day
        List<Load> dayLoads = driver.getAssignedLoads().stream()
            .filter(load -> isLoadOnDate(load, date))
            .collect(Collectors.toList());
        
        // Add load entries
        for (Load load : dayLoads) {
            HBox loadEntry = createLoadEntry(load, date);
            cell.getChildren().add(loadEntry);
        }
        
        // Highlight if driver is unavailable
        if (driver.getStatus() == DispatcherDriverStatus.Status.OFF_DUTY && date.equals(LocalDate.now())) {
            cell.setStyle(cell.getStyle() + "-fx-background-color: #ffe0e0;");
        }
        
        return cell;
    }
    
    private HBox createLoadEntry(Load load, LocalDate date) {
        HBox entry = new HBox(3);
        entry.setPadding(new Insets(2));
        entry.setStyle("-fx-background-color: " + getLoadStatusColor(load.getStatus()) + 
                      "; -fx-border-radius: 3; -fx-background-radius: 3;");
        
        // Icon based on whether it's pickup or delivery
        String icon = "";
        if (load.getPickUpDate() != null && load.getPickUpDate().equals(date)) {
            icon = "▲"; // Pickup
        } else if (load.getDeliveryDate() != null && load.getDeliveryDate().equals(date)) {
            icon = "▼"; // Delivery
        } else {
            icon = "↔"; // In transit
        }
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #000000;");
        
        Label loadLabel = new Label(load.getLoadNumber());
        loadLabel.setStyle("-fx-font-size: 9; -fx-font-weight: bold; -fx-text-fill: #000000;");
        
        entry.getChildren().addAll(iconLabel, loadLabel);
        
        // Tooltip
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
        
        Tooltip tooltip = new Tooltip(tooltipText.toString());
        Tooltip.install(entry, tooltip);
        
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
        summaryLabel.setStyle("-fx-font-weight: bold; -fx-background-color: #e0e0e0; -fx-padding: 10;");
        summaryLabel.setPrefHeight(40);
        summaryLabel.setAlignment(Pos.CENTER);
        weekGrid.add(summaryLabel, 0, rowIndex);
        
        // Daily summaries
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            VBox summaryCell = createDailySummary(date);
            weekGrid.add(summaryCell, dayOffset + 1, rowIndex);
        }
    }
    
    private VBox createDailySummary(LocalDate date) {
        VBox summary = new VBox(2);
        summary.setPadding(new Insets(5));
        summary.setAlignment(Pos.CENTER);
        summary.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1;");
        summary.setPrefHeight(40);
        
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
        
        Label countsLabel = new Label(String.format("▲%d ▼%d", pickups, deliveries));
        countsLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #000000;");
        
        if (inTransit > 0) {
            Label transitLabel = new Label("↔" + inTransit + " in transit");
            transitLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #000000;");
            summary.getChildren().addAll(countsLabel, transitLabel);
        } else {
            summary.getChildren().add(countsLabel);
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
            case BOOKED: return "#b6d4fe";
            case ASSIGNED: return "#ffe59b";
            case IN_TRANSIT: return "#90EE90";
            case DELIVERED: return "#98FB98";
            case PAID: return "#c2c2d6";
            default: return "#f0f0f0";
        }
    }
    
    private void showLoadDetails(Load load) {
        logger.info("Showing details for load: {}", load.getLoadNumber());
        new LoadDetailsDialog(load).showAndWait();
    }
}