package com.company.payroll.dispatcher;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for driver availability persistence
 */
public class DriverAvailabilityDAO {
    private static final Logger logger = LoggerFactory.getLogger(DriverAvailabilityDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    
    public DriverAvailabilityDAO() {
        logger.debug("Initializing DriverAvailabilityDAO");
        initializeTable();
    }
    
    private void initializeTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS driver_availability (
                    driver_id INTEGER PRIMARY KEY,
                    driver_name TEXT NOT NULL,
                    truck_unit TEXT,
                    trailer_number TEXT,
                    status TEXT NOT NULL,
                    current_load_id INTEGER,
                    expected_return_date DATE,
                    current_location TEXT,
                    notes TEXT,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by TEXT,
                    FOREIGN KEY (driver_id) REFERENCES employees(id),
                    FOREIGN KEY (current_load_id) REFERENCES loads(id)
                );
            """;
            conn.createStatement().execute(sql);
            
            // Create index for faster queries
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_availability_status ON driver_availability(status)");
            
            logger.info("Driver availability table initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize driver availability table: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to initialize driver availability table", e);
        }
    }
    
    public DriverAvailability getByDriverId(int driverId) {
        logger.debug("Getting availability for driver ID: {}", driverId);
        String sql = "SELECT * FROM driver_availability WHERE driver_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("Error getting availability for driver {}: {}", driverId, e.getMessage());
            throw new DataAccessException("Error getting driver availability", e);
        }
        return null;
    }
    
    public List<DriverAvailability> getAll() {
        logger.debug("Getting all driver availabilities");
        List<DriverAvailability> list = new ArrayList<>();
        String sql = "SELECT * FROM driver_availability ORDER BY driver_name";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            logger.info("Retrieved {} driver availability records", list.size());
        } catch (SQLException e) {
            logger.error("Error getting all driver availabilities: {}", e.getMessage());
            throw new DataAccessException("Error getting all driver availabilities", e);
        }
        return list;
    }
    
    public List<DriverAvailability> getByStatus(DriverAvailability.AvailabilityStatus status) {
        logger.debug("Getting drivers by status: {}", status);
        List<DriverAvailability> list = new ArrayList<>();
        String sql = "SELECT * FROM driver_availability WHERE status = ? ORDER BY driver_name";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            logger.info("Found {} drivers with status {}", list.size(), status);
        } catch (SQLException e) {
            logger.error("Error getting drivers by status {}: {}", status, e.getMessage());
            throw new DataAccessException("Error getting drivers by status", e);
        }
        return list;
    }
    
    public void save(DriverAvailability availability) {
        logger.info("Saving availability for driver: {} (ID: {})", 
                   availability.getDriverName(), availability.getDriverId());
        
        String sql = """
            INSERT OR REPLACE INTO driver_availability 
            (driver_id, driver_name, truck_unit, trailer_number, status, 
             current_load_id, expected_return_date, current_location, notes, 
             last_updated, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, availability.getDriverId());
            ps.setString(2, availability.getDriverName());
            ps.setString(3, availability.getTruckUnit());
            ps.setString(4, availability.getTrailerNumber());
            ps.setString(5, availability.getStatus().name());
            
            if (availability.getCurrentLoad() != null) {
                ps.setInt(6, availability.getCurrentLoad().getId());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            
            if (availability.getExpectedReturnDate() != null) {
                ps.setDate(7, Date.valueOf(availability.getExpectedReturnDate()));
            } else {
                ps.setNull(7, Types.DATE);
            }
            
            ps.setString(8, availability.getCurrentLocation());
            ps.setString(9, availability.getNotes());
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(11, "mgubran1");
            
            ps.executeUpdate();
            logger.info("Driver availability saved successfully");
        } catch (SQLException e) {
            logger.error("Error saving driver availability: {}", e.getMessage());
            throw new DataAccessException("Error saving driver availability", e);
        }
    }
    
    public void updateStatus(int driverId, DriverAvailability.AvailabilityStatus status, String notes) {
        logger.info("Updating status for driver {}: {}", driverId, status);
        String sql = """
            UPDATE driver_availability 
            SET status = ?, notes = ?, last_updated = ?, updated_by = ?
            WHERE driver_id = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, status.name());
            ps.setString(2, notes);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4, "mgubran1");
            ps.setInt(5, driverId);
            
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Driver status updated successfully");
            } else {
                logger.warn("No driver found with ID {} to update", driverId);
            }
        } catch (SQLException e) {
            logger.error("Error updating driver status: {}", e.getMessage());
            throw new DataAccessException("Error updating driver status", e);
        }
    }
    
    public void delete(int driverId) {
        logger.info("Deleting availability record for driver: {}", driverId);
        String sql = "DELETE FROM driver_availability WHERE driver_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, driverId);
            int rows = ps.executeUpdate();
            
            if (rows > 0) {
                logger.info("Driver availability record deleted");
            } else {
                logger.warn("No availability record found for driver {}", driverId);
            }
        } catch (SQLException e) {
            logger.error("Error deleting driver availability: {}", e.getMessage());
            throw new DataAccessException("Error deleting driver availability", e);
        }
    }
    
    public List<DriverAvailability> getAvailableDrivers() {
        return getByStatus(DriverAvailability.AvailabilityStatus.AVAILABLE);
    }
    
    public List<DriverAvailability> getOnRoadDrivers() {
        logger.debug("Getting on-road drivers");
        List<DriverAvailability> list = new ArrayList<>();
        String sql = """
            SELECT * FROM driver_availability 
            WHERE status IN (?, ?) 
            ORDER BY expected_return_date
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, DriverAvailability.AvailabilityStatus.ON_ROAD.name());
            ps.setString(2, DriverAvailability.AvailabilityStatus.RETURNING.name());
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            logger.info("Found {} on-road drivers", list.size());
        } catch (SQLException e) {
            logger.error("Error getting on-road drivers: {}", e.getMessage());
            throw new DataAccessException("Error getting on-road drivers", e);
        }
        return list;
    }
    
    private DriverAvailability mapRow(ResultSet rs) throws SQLException {
        DriverAvailability availability = new DriverAvailability(
            rs.getInt("driver_id"),
            rs.getString("driver_name"),
            rs.getString("truck_unit"),
            rs.getString("trailer_number"),
            DriverAvailability.AvailabilityStatus.valueOf(rs.getString("status"))
        );
        
        Date returnDate = rs.getDate("expected_return_date");
        if (returnDate != null) {
            availability.setExpectedReturnDate(returnDate.toLocalDate());
        }
        
        availability.setCurrentLocation(rs.getString("current_location"));
        availability.setNotes(rs.getString("notes"));
        
        // Note: CurrentLoad would need to be loaded separately to avoid circular dependencies
        // int loadId = rs.getInt("current_load_id");
        // if (!rs.wasNull()) {
        //     availability.setCurrentLoad(loadDAO.getById(loadId));
        // }
        
        return availability;
    }
}