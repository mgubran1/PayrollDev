package com.company.payroll.trucks;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal DAO used by {@link TrucksTab} for basic CRUD operations.
 */
public class TruckDAO {
    private static final Logger logger = LoggerFactory.getLogger(TruckDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public TruckDAO() {
        initDatabase();
    }

    private void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS trucks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                truck_number TEXT NOT NULL UNIQUE,
                vin TEXT,
                make TEXT,
                model TEXT,
                year INTEGER,
                type TEXT,
                status TEXT,
                license_plate TEXT,
                registration_expiry_date DATE,
                insurance_expiry_date DATE,
                next_inspection_due DATE,
                inspection DATE,
                permit_numbers TEXT,
                driver TEXT,
                assigned BOOLEAN DEFAULT 0
            )
        """;
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            
            // Add inspection column if it doesn't exist (for existing databases)
            try {
                stmt.execute("ALTER TABLE trucks ADD COLUMN inspection DATE");
                logger.info("Added inspection column to trucks table");
            } catch (SQLException e) {
                logger.debug("inspection column already exists");
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize trucks table", e);
            throw new DataAccessException("Failed to initialize trucks table", e);
        }
    }

    // -- CRUD ---------------------------------------------------------------

    public Truck save(Truck truck) {
        if (truck.getId() > 0) {
            return update(truck);
        }
        return insert(truck);
    }

    private Truck insert(Truck truck) {
        String sql = """
            INSERT INTO trucks (
                truck_number, vin, make, model, year, type, status,
                license_plate, registration_expiry_date, insurance_expiry_date,
                next_inspection_due, inspection, permit_numbers, driver, assigned
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setParams(ps, truck);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    truck.setId(keys.getInt(1));
                }
            }
            return truck;
        } catch (SQLException e) {
            logger.error("Failed to insert truck", e);
            throw new DataAccessException("Failed to insert truck", e);
        }
    }

    private Truck update(Truck truck) {
        String sql = """
            UPDATE trucks SET
                truck_number = ?, vin = ?, make = ?, model = ?, year = ?,
                type = ?, status = ?, license_plate = ?,
                registration_expiry_date = ?, insurance_expiry_date = ?,
                next_inspection_due = ?, inspection = ?, permit_numbers = ?, driver = ?, assigned = ?
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, truck);
            ps.setInt(16, truck.getId());
            ps.executeUpdate();
            return truck;
        } catch (SQLException e) {
            logger.error("Failed to update truck", e);
            throw new DataAccessException("Failed to update truck", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM trucks WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete truck", e);
            throw new DataAccessException("Failed to delete truck", e);
        }
    }

    // -- Queries ------------------------------------------------------------

    public List<Truck> findAll() {
        String sql = "SELECT * FROM trucks ORDER BY truck_number";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Truck> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to retrieve trucks", e);
            throw new DataAccessException("Failed to retrieve trucks", e);
        }
    }

    /**
     * Add or update multiple trucks from import
     * Checks for existing trucks by truck number and updates them, or adds new ones
     * Returns the list of trucks with proper IDs assigned
     */
    public List<Truck> addOrUpdateAll(List<Truck> trucks) {
        logger.info("Processing {} trucks for import", trucks.size());
        List<Truck> resultTrucks = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                int added = 0;
                int updated = 0;
                
                for (Truck importedTruck : trucks) {
                    // Check if truck exists by truck number
                    Truck existing = getByTruckNumber(importedTruck.getNumber());
                    
                    if (existing != null) {
                        // Merge imported data with existing data
                        mergeTruckData(existing, importedTruck);
                        update(existing);
                        resultTrucks.add(existing); // Use existing truck with proper ID
                        updated++;
                        logger.debug("Updated existing truck: {} (ID: {})", existing.getNumber(), existing.getId());
                    } else {
                        // Add new truck
                        Truck insertedTruck = insert(importedTruck);
                        resultTrucks.add(insertedTruck); // Use inserted truck with proper ID
                        added++;
                        logger.debug("Added new truck: {} (ID: {})", insertedTruck.getNumber(), insertedTruck.getId());
                    }
                }
                
                conn.commit();
                logger.info("Import completed - Added: {}, Updated: {}", added, updated);
                
                // Validate that all result trucks have proper IDs
                for (Truck truck : resultTrucks) {
                    if (truck.getId() <= 0) {
                        logger.error("CRITICAL: Truck {} has invalid ID: {}", truck.getNumber(), truck.getId());
                    } else {
                        logger.debug("Truck {} has valid ID: {}", truck.getNumber(), truck.getId());
                    }
                }
                
            } catch (Exception e) {
                conn.rollback();
                logger.error("Error during import, rolling back: {}", e.getMessage(), e);
                throw new DataAccessException("Error during truck import", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Database error during import: {}", e.getMessage(), e);
            throw new DataAccessException("Database error during import", e);
        }
        
        return resultTrucks;
    }
    
    /**
     * Merge imported truck data with existing truck data
     * Preserves existing fields that are not provided in the import
     */
    private void mergeTruckData(Truck existing, Truck imported) {
        // Only update fields that are provided in the import (not null/empty)
        if (imported.getYear() > 0) {
            existing.setYear(imported.getYear());
        }
        if (imported.getMake() != null && !imported.getMake().trim().isEmpty()) {
            existing.setMake(imported.getMake());
        }
        if (imported.getModel() != null && !imported.getModel().trim().isEmpty()) {
            existing.setModel(imported.getModel());
        }
        if (imported.getVin() != null && !imported.getVin().trim().isEmpty()) {
            existing.setVin(imported.getVin());
        }
        if (imported.getLicensePlate() != null && !imported.getLicensePlate().trim().isEmpty()) {
            existing.setLicensePlate(imported.getLicensePlate());
        }
        // Note: Driver assignment is handled separately in the UI, not during import
        if (imported.getRegistrationExpiryDate() != null) {
            existing.setRegistrationExpiryDate(imported.getRegistrationExpiryDate());
        }
        if (imported.getInspection() != null) {
            existing.setInspection(imported.getInspection());
        }
        
        // Set default values for new trucks if not already set
        if (existing.getType() == null || existing.getType().trim().isEmpty()) {
            existing.setType("Semi Truck (Tractor)");
        }
        if (existing.getStatus() == null || existing.getStatus().trim().isEmpty()) {
            existing.setStatus("Active");
        }
        
        // Auto-calculate Inspection Expiry if Inspection date is set
        if (imported.getInspection() != null) {
            LocalDate inspectionExpiry = imported.getInspection().plusDays(365);
            existing.setNextInspectionDue(inspectionExpiry);
        }
    }
    
    /**
     * Get truck by truck number (case-insensitive)
     */
    public Truck getByTruckNumber(String truckNumber) {
        logger.debug("Getting truck by number: {}", truckNumber);
        if (truckNumber == null || truckNumber.trim().isEmpty()) return null;
        
        String sql = "SELECT * FROM trucks WHERE LOWER(truck_number) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, truckNumber.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Truck truck = map(rs);
                logger.debug("Found truck: {} (ID: {})", truck.getNumber(), truck.getId());
                return truck;
            }
            logger.debug("No truck found with number: {}", truckNumber);
        } catch (SQLException e) {
            logger.error("Error getting truck by number {}: {}", truckNumber, e.getMessage(), e);
            throw new DataAccessException("Error getting truck by number", e);
        }
        return null;
    }

    // -- Helpers ------------------------------------------------------------

    private void setParams(PreparedStatement ps, Truck t) throws SQLException {
        ps.setString(1, t.getNumber());
        ps.setString(2, t.getVin());
        ps.setString(3, t.getMake());
        ps.setString(4, t.getModel());
        if (t.getYear() > 0) ps.setInt(5, t.getYear()); else ps.setNull(5, Types.INTEGER);
        ps.setString(6, t.getType());
        ps.setString(7, t.getStatus());
        ps.setString(8, t.getLicensePlate());
        if (t.getRegistrationExpiryDate() != null) ps.setDate(9, Date.valueOf(t.getRegistrationExpiryDate())); else ps.setNull(9, Types.DATE);
        if (t.getInsuranceExpiryDate() != null) ps.setDate(10, Date.valueOf(t.getInsuranceExpiryDate())); else ps.setNull(10, Types.DATE);
        if (t.getNextInspectionDue() != null) ps.setDate(11, Date.valueOf(t.getNextInspectionDue())); else ps.setNull(11, Types.DATE);
        if (t.getInspection() != null) ps.setDate(12, Date.valueOf(t.getInspection())); else ps.setNull(12, Types.DATE);
        ps.setString(13, t.getPermitNumbers());
        ps.setString(14, t.getDriver());
        ps.setBoolean(15, t.isAssigned());
    }

    private Truck map(ResultSet rs) throws SQLException {
        Truck t = new Truck();
        t.setId(rs.getInt("id"));
        t.setNumber(rs.getString("truck_number"));
        t.setVin(rs.getString("vin"));
        t.setMake(rs.getString("make"));
        t.setModel(rs.getString("model"));
        t.setYear(rs.getInt("year"));
        t.setType(rs.getString("type"));
        t.setStatus(rs.getString("status"));
        t.setLicensePlate(rs.getString("license_plate"));
        Date reg = rs.getDate("registration_expiry_date");
        if (reg != null) t.setRegistrationExpiryDate(reg.toLocalDate());
        Date ins = rs.getDate("insurance_expiry_date");
        if (ins != null) t.setInsuranceExpiryDate(ins.toLocalDate());
        Date insp = rs.getDate("next_inspection_due");
        if (insp != null) t.setNextInspectionDue(insp.toLocalDate());
        Date inspDate = rs.getDate("inspection");
        if (inspDate != null) t.setInspection(inspDate.toLocalDate());
        t.setPermitNumbers(rs.getString("permit_numbers"));
        t.setDriver(rs.getString("driver"));
        t.setAssigned(rs.getBoolean("assigned"));
        return t;
    }
}
