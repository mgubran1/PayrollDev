package com.company.payroll.dispatcher;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for dispatcher operations
 */
public class DispatcherController {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
    
    private final EmployeeDAO employeeDAO;
    private final LoadDAO loadDAO;
    private final ObservableList<DispatcherDriverStatus> driverStatuses;
    private final ObservableList<Load> activeLoads;
    private final Map<Integer, DispatcherDriverStatus> driverStatusMap;
    
    private Timeline clockTimeline;
    
    public DispatcherController() {
        this.employeeDAO = new EmployeeDAO();
        this.loadDAO = new LoadDAO();
        this.driverStatuses = FXCollections.observableArrayList();
        this.activeLoads = FXCollections.observableArrayList();
        this.driverStatusMap = new HashMap<>();
        
        initializeData();
        startAutoRefresh();
    }
    
    private void initializeData() {
        logger.info("Initializing dispatcher data");
        refreshAll();
    }
    
    private void startAutoRefresh() {
        // Auto-refresh every 30 seconds
        Timeline refreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(30), e -> refreshAll())
        );
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }
    
    public void bindTimeLabel(Label timeLabel) {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        
        clockTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                timeLabel.setText(LocalDateTime.now().format(DATE_TIME_FORMATTER));
            })
        );
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }
    
    public void refreshAll() {
        logger.info("Refreshing all dispatcher data");
        Platform.runLater(() -> {
            refreshDriverStatuses();
            refreshActiveLoads();
            calculateDriverAvailability();
        });
    }
    
    private void refreshDriverStatuses() {
        driverStatuses.clear();
        driverStatusMap.clear();
        
        List<Employee> activeDrivers = employeeDAO.getActive().stream()
            .filter(Employee::isDriver)
            .collect(Collectors.toList());
        
        for (Employee driver : activeDrivers) {
            DispatcherDriverStatus status = new DispatcherDriverStatus(driver);
            
            // Get current and upcoming loads for this driver
            List<Load> driverLoads = loadDAO.getByDriver(driver.getId()).stream()
                .filter(l -> l.getStatus() != Load.Status.CANCELLED)
                .sorted(Comparator.comparing(Load::getPickUpDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Load::getPickUpTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
            
            status.setAssignedLoads(driverLoads);
            
            // Determine current status
            determineDriverStatus(status, driverLoads);
            
            driverStatuses.add(status);
            driverStatusMap.put(driver.getId(), status);
        }
        
        logger.info("Refreshed {} driver statuses", driverStatuses.size());
    }
    
    private void determineDriverStatus(DispatcherDriverStatus driverStatus, List<Load> loads) {
        LocalDateTime now = LocalDateTime.now();
        
        // Find current load (in transit or being loaded/unloaded)
        Optional<Load> currentLoad = loads.stream()
            .filter(l -> l.getStatus() == Load.Status.IN_TRANSIT || l.getStatus() == Load.Status.ASSIGNED)
            .findFirst();
        
        if (currentLoad.isPresent()) {
            Load load = currentLoad.get();
            driverStatus.setCurrentLoad(load);
            
            if (load.getStatus() == Load.Status.IN_TRANSIT) {
                driverStatus.setStatus(DispatcherDriverStatus.Status.ON_ROAD);
                driverStatus.setCurrentLocation(
                    String.format("En route to %s", load.getDropLocation())
                );
                
                // Calculate ETA based on delivery date/time
                if (load.getDeliveryDate() != null) {
                    LocalDateTime eta = LocalDateTime.of(
                        load.getDeliveryDate(),
                        load.getDeliveryTime() != null ? load.getDeliveryTime() : LocalTime.of(12, 0)
                    );
                    driverStatus.setEstimatedAvailableTime(eta);
                }
            } else {
                driverStatus.setStatus(DispatcherDriverStatus.Status.LOADING);
                driverStatus.setCurrentLocation(load.getPickUpLocation());
            }
        } else {
            // Check for upcoming loads
            Optional<Load> nextLoad = loads.stream()
                .filter(l -> l.getStatus() == Load.Status.BOOKED)
                .filter(l -> l.getPickUpDate() != null)
                .findFirst();
            
            if (nextLoad.isPresent()) {
                Load load = nextLoad.get();
                LocalDateTime pickupTime = LocalDateTime.of(
                    load.getPickUpDate(),
                    load.getPickUpTime() != null ? load.getPickUpTime() : LocalTime.of(8, 0)
                );
                
                // If pickup is within next 2 hours, mark as preparing
                if (pickupTime.isBefore(now.plusHours(2))) {
                    driverStatus.setStatus(DispatcherDriverStatus.Status.PREPARING);
                    driverStatus.setNextLoad(load);
                } else {
                    driverStatus.setStatus(DispatcherDriverStatus.Status.AVAILABLE);
                    driverStatus.setNextLoad(load);
                }
            } else {
                // Check last completed load to determine if returning
                Optional<Load> lastDelivered = loads.stream()
                    .filter(l -> l.getStatus() == Load.Status.DELIVERED || l.getStatus() == Load.Status.PAID)
                    .max(Comparator.comparing(Load::getDeliveryDate, Comparator.nullsFirst(Comparator.naturalOrder())));
                
                if (lastDelivered.isPresent()) {
                    Load load = lastDelivered.get();
                    LocalDateTime deliveryTime = LocalDateTime.of(
                        load.getDeliveryDate(),
                        load.getDeliveryTime() != null ? load.getDeliveryTime() : LocalTime.of(17, 0)
                    );
                    
                    // If delivered within last 24 hours, mark as returning
                    if (deliveryTime.isAfter(now.minusHours(24))) {
                        driverStatus.setStatus(DispatcherDriverStatus.Status.RETURNING);
                        driverStatus.setCurrentLocation(load.getDropLocation());
                        driverStatus.setEstimatedAvailableTime(deliveryTime.plusHours(12)); // Estimate 12 hours to return
                    } else {
                        driverStatus.setStatus(DispatcherDriverStatus.Status.AVAILABLE);
                        driverStatus.setCurrentLocation("Home Base");
                    }
                } else {
                    driverStatus.setStatus(DispatcherDriverStatus.Status.AVAILABLE);
                    driverStatus.setCurrentLocation("Home Base");
                }
            }
        }
    }
    
    private void refreshActiveLoads() {
        activeLoads.clear();
        
        List<Load> loads = loadDAO.getAll().stream()
            .filter(l -> l.getStatus() != Load.Status.CANCELLED && l.getStatus() != Load.Status.PAID)
            .sorted(Comparator.comparing(Load::getPickUpDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Load::getPickUpTime, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        activeLoads.addAll(loads);
        logger.info("Refreshed {} active loads", activeLoads.size());
    }
    
    private void calculateDriverAvailability() {
        // Calculate availability windows for each driver
        for (DispatcherDriverStatus status : driverStatuses) {
            calculateAvailabilityWindows(status);
        }
    }
    
    private void calculateAvailabilityWindows(DispatcherDriverStatus driverStatus) {
        List<TimeSlot> availability = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfWeek = now.plusDays(7).withHour(23).withMinute(59);
        
        // Get all assigned loads sorted by pickup time
        List<Load> assignedLoads = driverStatus.getAssignedLoads().stream()
            .filter(l -> l.getPickUpDate() != null)
            .sorted(Comparator.comparing(Load::getPickUpDate)
                .thenComparing(Load::getPickUpTime, Comparator.nullsFirst(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        LocalDateTime currentTime = now;
        
        // If driver has current load, start from estimated completion
        if (driverStatus.getCurrentLoad() != null && driverStatus.getEstimatedAvailableTime() != null) {
            currentTime = driverStatus.getEstimatedAvailableTime();
        }
        
        // Calculate availability between loads
        for (Load load : assignedLoads) {
            LocalDateTime pickupTime = LocalDateTime.of(
                load.getPickUpDate(),
                load.getPickUpTime() != null ? load.getPickUpTime() : LocalTime.of(8, 0)
            );
            
            // Add buffer time before pickup (2 hours)
            LocalDateTime bufferStart = pickupTime.minusHours(2);
            
            if (currentTime.isBefore(bufferStart)) {
                availability.add(new TimeSlot(currentTime, bufferStart));
            }
            
            // Update current time to after delivery
            if (load.getDeliveryDate() != null) {
                LocalDateTime deliveryTime = LocalDateTime.of(
                    load.getDeliveryDate(),
                    load.getDeliveryTime() != null ? load.getDeliveryTime() : LocalTime.of(17, 0)
                );
                
                // Add rest time after delivery (10 hours minimum)
                currentTime = deliveryTime.plusHours(10);
            } else {
                // Estimate 24 hours for load completion if no delivery date
                currentTime = pickupTime.plusHours(24);
            }
        }
        
        // Add remaining availability until end of week
        if (currentTime.isBefore(endOfWeek)) {
            availability.add(new TimeSlot(currentTime, endOfWeek));
        }
        
        driverStatus.setAvailabilityWindows(availability);
    }
    
    // Getters
    public ObservableList<DispatcherDriverStatus> getDriverStatuses() {
        return driverStatuses;
    }
    
    public ObservableList<Load> getActiveLoads() {
        return activeLoads;
    }
    
    public List<DispatcherDriverStatus> getAvailableDrivers() {
        return driverStatuses.stream()
            .filter(d -> d.getStatus() == DispatcherDriverStatus.Status.AVAILABLE)
            .collect(Collectors.toList());
    }
    
    public List<Load> getUnassignedLoads() {
        return activeLoads.stream()
            .filter(l -> l.getDriver() == null)
            .filter(l -> l.getStatus() == Load.Status.BOOKED)
            .collect(Collectors.toList());
    }

    /**
     * Assign a load to a driver and persist the change. This updates the load
     * record with the selected driver and refreshes dispatcher data.
     *
     * @param load   the load to assign
     * @param driver the driver who will take the load
     * @param notes  optional assignment notes
     * @return true if the assignment succeeded
     */
    public boolean assignLoadToDriver(Load load, Employee driver, String notes) {
        if (load == null || driver == null) {
            return false;
        }

        try {
            load.setDriver(driver);
            load.setTruckUnitSnapshot(driver.getTruckUnit());

            if (notes != null && !notes.isBlank()) {
                String existing = load.getNotes();
                if (existing == null || existing.isBlank()) {
                    load.setNotes(notes.trim());
                } else {
                    load.setNotes(existing + "\n" + notes.trim());
                }
            }

            if (load.getStatus() == Load.Status.BOOKED) {
                load.setStatus(Load.Status.ASSIGNED);
            }

            loadDAO.update(load);
            refreshAll();
            logger.info("Assigned load {} to driver {}", load.getLoadNumber(), driver.getName());
            return true;
        } catch (Exception e) {
            logger.error("Failed to assign load {} to driver {}", load.getLoadNumber(), driver.getName(), e);
            return false;
        }
    }
    
    // Dialog methods
    public void showAssignLoadDialog() {
        logger.info("Showing assign load dialog");
        new AssignLoadDialog(this).showAndWait();
    }
    
    public void showUpdateStatusDialog() {
        logger.info("Showing update status dialog");
        new UpdateStatusDialog(this).showAndWait();
    }
    
    public void showFleetTracking() {
        logger.info("Showing fleet tracking");
        new FleetTrackingDialog(this).show();
    }
    
    public void showReports() {
        logger.info("Showing dispatcher reports");
        new DispatcherReportsDialog(this).show();
    }
    
    // Inner class for time slots
    public static class TimeSlot {
        private final LocalDateTime start;
        private final LocalDateTime end;
        
        public TimeSlot(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
        
        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
        
        public boolean contains(LocalDateTime time) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        
        public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
            return !otherEnd.isBefore(start) && !otherStart.isAfter(end);
        }
    }
}