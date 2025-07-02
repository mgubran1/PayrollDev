package com.company.payroll.dispatcher;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic manager for dispatcher operations
 */
public class DispatcherManager {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherManager.class);
    
    private final DriverAvailabilityDAO availabilityDAO;
    private final EmployeeDAO employeeDAO;
    private final LoadDAO loadDAO;
    
    public DispatcherManager() {
        this.availabilityDAO = new DriverAvailabilityDAO();
        this.employeeDAO = new EmployeeDAO();
        this.loadDAO = new LoadDAO();
        logger.info("DispatcherManager initialized");
    }
    
    /**
     * Initialize or refresh driver availability data
     */
    public void initializeDriverAvailability() {
        logger.info("Initializing driver availability data");
        
        List<Employee> activeDrivers = employeeDAO.getActive().stream()
            .filter(Employee::isDriver)
            .collect(Collectors.toList());
        
        for (Employee driver : activeDrivers) {
            DriverAvailability existing = availabilityDAO.getByDriverId(driver.getId());
            
            if (existing == null) {
                // Create new availability record
                DriverAvailability availability = new DriverAvailability(
                    driver.getId(),
                    driver.getName(),
                    driver.getTruckUnit(),
                    driver.getTrailerNumber(),
                    DriverAvailability.AvailabilityStatus.AVAILABLE
                );
                availabilityDAO.save(availability);
                logger.debug("Created availability record for driver: {}", driver.getName());
            } else {
                // Update existing record with current driver info
                existing = new DriverAvailability(
                    driver.getId(),
                    driver.getName(),
                    driver.getTruckUnit(),
                    driver.getTrailerNumber(),
                    existing.getStatus()
                );
                existing.setCurrentLocation(existing.getCurrentLocation());
                existing.setNotes(existing.getNotes());
                existing.setExpectedReturnDate(existing.getExpectedReturnDate());
                availabilityDAO.save(existing);
                logger.debug("Updated availability record for driver: {}", driver.getName());
            }
        }
        
        logger.info("Driver availability initialization complete");
    }
    
    /**
     * Get all driver availabilities with current load information
     */
    public List<DriverAvailability> getAllDriverAvailabilities() {
        logger.debug("Getting all driver availabilities");
        List<DriverAvailability> availabilities = availabilityDAO.getAll();
        
        // Enrich with current load data
        for (DriverAvailability availability : availabilities) {
            if (availability.getStatus() == DriverAvailability.AvailabilityStatus.ON_ROAD ||
                availability.getStatus() == DriverAvailability.AvailabilityStatus.RETURNING) {
                
                List<Load> activeLoads = loadDAO.getActiveLoadsByDriver(availability.getDriverId());
                if (!activeLoads.isEmpty()) {
                    Load currentLoad = activeLoads.get(0); // Most recent active load
                    availability.setCurrentLoad(currentLoad);
                    
                    // Update location based on load status
                    if (currentLoad.getStatus() == Load.Status.IN_TRANSIT) {
                        availability.setCurrentLocation(
                            "En route to " + currentLoad.getDeliveryCity() + ", " + 
                            currentLoad.getDeliveryState()
                        );
                    } else if (currentLoad.getStatus() == Load.Status.DELIVERED) {
                        availability.setCurrentLocation(
                            currentLoad.getDeliveryCity() + ", " + currentLoad.getDeliveryState()
                        );
                    }
                }
            }
        }
        
        return availabilities;
    }
    
    /**
     * Find available drivers for a specific date range
     */
    public List<DriverAvailability> findAvailableDrivers(LocalDate startDate, LocalDate endDate) {
        logger.info("Finding available drivers for dates {} to {}", startDate, endDate);
        
        List<DriverAvailability> availableDrivers = availabilityDAO.getAvailableDrivers();
        List<DriverAvailability> returningDrivers = availabilityDAO.getOnRoadDrivers().stream()
            .filter(d -> d.getExpectedReturnDate() != null && 
                        !d.getExpectedReturnDate().isAfter(startDate))
            .collect(Collectors.toList());
        
        List<DriverAvailability> allAvailable = new ArrayList<>();
        allAvailable.addAll(availableDrivers);
        allAvailable.addAll(returningDrivers);
        
        logger.info("Found {} available drivers", allAvailable.size());
        return allAvailable;
    }
    
    /**
     * Assign a load to a driver
     */
    public void assignLoadToDriver(int driverId, Load load) {
        logger.info("Assigning load {} to driver {}", load.getId(), driverId);
        
        DriverAvailability availability = availabilityDAO.getByDriverId(driverId);
        if (availability == null) {
            throw new IllegalStateException("No availability record found for driver " + driverId);
        }
        
        if (!availability.isAvailable()) {
            throw new IllegalStateException("Driver " + availability.getDriverName() + " is not available");
        }
        
        // Update driver status
        availability.setStatus(DriverAvailability.AvailabilityStatus.ON_ROAD);
        availability.setCurrentLoad(load);
        availability.setExpectedReturnDate(load.getDeliveryDate());
        availability.setCurrentLocation(load.getPickupCity() + ", " + load.getPickupState());
        availability.setNotes("Assigned to load #" + load.getId());
        
        availabilityDAO.save(availability);
        
        // Update load with driver assignment
        // Attach the driver object and update load status
        load.setDriver(employeeDAO.getById(driverId));
        load.setStatus(Load.Status.ASSIGNED);
        loadDAO.update(load);
        
        logger.info("Load assignment complete");
    }
    
    /**
     * Update driver status when load is completed
     */
    public void completeLoadForDriver(int driverId, Load load) {
        logger.info("Completing load {} for driver {}", load.getId(), driverId);
        
        DriverAvailability availability = availabilityDAO.getByDriverId(driverId);
        if (availability == null) {
            logger.warn("No availability record found for driver {}", driverId);
            return;
        }
        
        // Update driver status
        availability.setStatus(DriverAvailability.AvailabilityStatus.RETURNING);
        availability.setCurrentLocation(load.getDeliveryCity() + ", " + load.getDeliveryState());
        availability.setNotes("Completed load #" + load.getId() + " - Returning");
        
        // Calculate expected return based on distance (simplified)
        LocalDate expectedReturn = LocalDate.now().plusDays(1); // Simple 1-day return
        availability.setExpectedReturnDate(expectedReturn);
        
        availabilityDAO.save(availability);
        logger.info("Driver status updated to RETURNING");
    }
    
    /**
     * Mark driver as available
     */
    public void markDriverAvailable(int driverId, String notes) {
        logger.info("Marking driver {} as available", driverId);
        
        DriverAvailability availability = availabilityDAO.getByDriverId(driverId);
        if (availability == null) {
            logger.warn("No availability record found for driver {}", driverId);
            return;
        }
        
        availability.setStatus(DriverAvailability.AvailabilityStatus.AVAILABLE);
        availability.setCurrentLoad(null);
        availability.setCurrentLocation("Home");
        availability.setExpectedReturnDate(null);
        availability.setNotes(notes != null ? notes : "Available for dispatch");
        
        availabilityDAO.save(availability);
        logger.info("Driver marked as available");
    }
    
    /**
     * Update driver status
     */
    public void updateDriverStatus(int driverId, DriverAvailability.AvailabilityStatus status, 
                                  String location, LocalDate returnDate, String notes) {
        logger.info("Updating driver {} status to {}", driverId, status);
        
        DriverAvailability availability = availabilityDAO.getByDriverId(driverId);
        if (availability == null) {
            Employee driver = employeeDAO.getById(driverId);
            if (driver == null) {
                throw new IllegalArgumentException("Driver not found: " + driverId);
            }
            availability = new DriverAvailability(
                driver.getId(),
                driver.getName(),
                driver.getTruckUnit(),
                driver.getTrailerNumber(),
                status
            );
        } else {
            availability.setStatus(status);
        }
        
        availability.setCurrentLocation(location);
        availability.setExpectedReturnDate(returnDate);
        availability.setNotes(notes);
        
        availabilityDAO.save(availability);
        logger.info("Driver status updated successfully");
    }
    
    /**
     * Get drivers returning soon (within next n days)
     */
    public List<DriverAvailability> getDriversReturningSoon(int days) {
        logger.debug("Getting drivers returning within {} days", days);
        
        LocalDate cutoffDate = LocalDate.now().plusDays(days);
        
        return availabilityDAO.getOnRoadDrivers().stream()
            .filter(d -> d.getExpectedReturnDate() != null &&
                        !d.getExpectedReturnDate().isAfter(cutoffDate))
            .sorted(Comparator.comparing(DriverAvailability::getExpectedReturnDate))
            .collect(Collectors.toList());
    }
    
    /**
     * Get availability statistics
     */
    public DispatcherStatistics getStatistics() {
        List<DriverAvailability> all = availabilityDAO.getAll();
        
        int total = all.size();
        int available = (int) all.stream()
            .filter(d -> d.getStatus() == DriverAvailability.AvailabilityStatus.AVAILABLE)
            .count();
        int onRoad = (int) all.stream()
            .filter(DriverAvailability::isOnRoad)
            .count();
        int offDuty = (int) all.stream()
            .filter(d -> d.getStatus() == DriverAvailability.AvailabilityStatus.OFF_DUTY ||
                        d.getStatus() == DriverAvailability.AvailabilityStatus.ON_LEAVE)
            .count();
        
        return new DispatcherStatistics(total, available, onRoad, offDuty);
    }
    
    /**
     * Statistics holder class
     */
    public static class DispatcherStatistics {
        private final int totalDrivers;
        private final int availableDrivers;
        private final int onRoadDrivers;
        private final int offDutyDrivers;
        
        public DispatcherStatistics(int total, int available, int onRoad, int offDuty) {
            this.totalDrivers = total;
            this.availableDrivers = available;
            this.onRoadDrivers = onRoad;
            this.offDutyDrivers = offDuty;
        }
        
        public int getTotalDrivers() { return totalDrivers; }
        public int getAvailableDrivers() { return availableDrivers; }
        public int getOnRoadDrivers() { return onRoadDrivers; }
        public int getOffDutyDrivers() { return offDutyDrivers; }
        
        public double getAvailabilityPercentage() {
            return totalDrivers > 0 ? (availableDrivers * 100.0 / totalDrivers) : 0;
        }
    }
}