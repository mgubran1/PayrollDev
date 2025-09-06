package com.company.payroll.trailers;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.payroll.trailers.TrailerStatus;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Trailer operations.
 * Handles database interactions for trailer management.
 */
public class TrailerDAO {
    private static final Logger logger = LoggerFactory.getLogger(TrailerDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    
    public TrailerDAO() {
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        logger.info("Initializing Trailer database");
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
             
            // Create trailers table with correct column order
            String createTable = """
                CREATE TABLE IF NOT EXISTS trailers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trailer_number TEXT NOT NULL UNIQUE,
                    vin TEXT,
                    make TEXT,
                    model TEXT,
                    type TEXT,
                    status TEXT,
                    assigned_to TEXT,
                    registration_expiry_date TEXT,
                    insurance_expiry_date TEXT,
                    inspection_expiry TEXT,
                    license_plate TEXT,
                    year INTEGER,
                    length REAL,
                    width REAL,
                    height REAL,
                    capacity REAL,
                    max_weight REAL,
                    empty_weight REAL,
                    axle_count INTEGER,
                    suspension_type TEXT,
                    has_thermal_unit BOOLEAN,
                    thermal_unit_details TEXT,
                    ownership_type TEXT,
                    purchase_price REAL,
                    purchase_date TEXT,
                    current_value REAL,
                    current_location TEXT,
                    monthly_lease_cost REAL,
                    lease_details TEXT,
                    lease_agreement_expiry_date TEXT,
                    insurance_policy_number TEXT,
                    last_inspection_date TEXT,
                    next_inspection_due_date TEXT,
                    last_service_date TEXT,
                    next_service_due_date TEXT,
                    current_condition TEXT,
                    maintenance_notes TEXT,
                    assigned_driver TEXT,
                    assigned_truck TEXT,
                    is_assigned BOOLEAN,
                    current_job_id TEXT,
                    last_updated TIMESTAMP,
                    updated_by TEXT,
                    notes TEXT,
                    odometer_reading INTEGER
                )
            """;
            stmt.execute(createTable);
            
            // Create indexes for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trailer_number ON trailers(trailer_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trailer_status ON trailers(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trailer_assigned_driver ON trailers(assigned_driver)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trailer_assigned_truck ON trailers(assigned_truck)");
            
            logger.info("Trailer database initialized successfully");
            
            // Add lease_agreement_expiry_date column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE trailers ADD COLUMN lease_agreement_expiry_date TEXT");
                logger.info("Added lease_agreement_expiry_date column to trailers table");
            } catch (SQLException e) {
                // Column might already exist, which is fine
                logger.debug("lease_agreement_expiry_date column already exists or could not be added: {}", e.getMessage());
            }
            
        } catch (SQLException e) {
            logger.error("Failed to initialize trailer database", e);
            throw new DataAccessException("Failed to initialize database", e);
        }
    }
    
    // CRUD Operations
    
    public Trailer save(Trailer trailer) {
        if (trailer.getId() > 0) {
            return update(trailer);
        } else {
            return insert(trailer);
        }
    }
    
    private Trailer insert(Trailer trailer) {
        String sql = """
            INSERT INTO trailers (
                trailer_number, vin, make, model, type, status, assigned_to,
                registration_expiry_date, insurance_expiry_date, inspection_expiry,
                license_plate, year, length, width, height, capacity,
                max_weight, empty_weight, axle_count, suspension_type,
                has_thermal_unit, thermal_unit_details, ownership_type,
                purchase_price, purchase_date, current_value, current_location,
                monthly_lease_cost, lease_details, lease_agreement_expiry_date, insurance_policy_number,
                last_inspection_date, next_inspection_due_date, last_service_date,
                next_service_due_date, current_condition, maintenance_notes,
                assigned_driver, assigned_truck, is_assigned, current_job_id,
                last_updated, updated_by, notes, odometer_reading
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                     ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
             
            setTrailerParameters(pstmt, trailer);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Creating trailer failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    trailer.setId(id);
                    logger.info("Created trailer with ID: {}", id);
                }
            }
            
            return trailer;
            
        } catch (SQLException e) {
            logger.error("Failed to insert trailer: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to insert trailer", e);
        }
    }
    
    private Trailer update(Trailer trailer) {
        String sql = """
            UPDATE trailers SET
                trailer_number = ?, vin = ?, make = ?, model = ?, type = ?,
                status = ?, assigned_to = ?, registration_expiry_date = ?,
                insurance_expiry_date = ?, inspection_expiry = ?, license_plate = ?,
                year = ?, length = ?, width = ?, height = ?, capacity = ?,
                max_weight = ?, empty_weight = ?, axle_count = ?, suspension_type = ?,
                has_thermal_unit = ?, thermal_unit_details = ?, ownership_type = ?,
                purchase_price = ?, purchase_date = ?, current_value = ?,
                current_location = ?, monthly_lease_cost = ?, lease_details = ?,
                lease_agreement_expiry_date = ?, insurance_policy_number = ?, last_inspection_date = ?,
                next_inspection_due_date = ?, last_service_date = ?,
                next_service_due_date = ?, current_condition = ?, maintenance_notes = ?,
                assigned_driver = ?, assigned_truck = ?, is_assigned = ?,
                current_job_id = ?, last_updated = ?, updated_by = ?, notes = ?,
                odometer_reading = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            setTrailerParameters(pstmt, trailer);
            pstmt.setInt(46, trailer.getId());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Updating trailer failed, no rows affected.");
            }
            
            logger.info("Updated trailer with ID: {}", trailer.getId());
            return trailer;
            
        } catch (SQLException e) {
            logger.error("Failed to update trailer: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to update trailer", e);
        }
    }
    
    public void delete(int id) {
        String sql = "DELETE FROM trailers WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new DataAccessException("Deleting trailer failed, no trailer with ID: " + id);
            }
            
            logger.info("Deleted trailer with ID: {}", id);
            
        } catch (SQLException e) {
            logger.error("Failed to delete trailer: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to delete trailer", e);
        }
    }
    
    // Query Operations
    
    public Trailer findById(int id) {
        String sql = "SELECT * FROM trailers WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTrailer(rs);
                }
            }
            
            return null;
            
        } catch (SQLException e) {
            logger.error("Failed to find trailer by ID: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailer", e);
        }
    }
    
    public Trailer findByTrailerNumber(String trailerNumber) {
        String sql = "SELECT * FROM trailers WHERE trailer_number = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, trailerNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTrailer(rs);
                }
            }
            
            return null;
            
        } catch (SQLException e) {
            logger.error("Failed to find trailer by trailer number: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailer", e);
        }
    }
    
    public List<Trailer> findAll() {
        String sql = "SELECT * FROM trailers ORDER BY trailer_number";
        
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            List<Trailer> trailers = new ArrayList<>();
            while (rs.next()) {
                trailers.add(mapResultSetToTrailer(rs));
            }
            
            return trailers;
            
        } catch (SQLException e) {
            logger.error("Failed to find all trailers: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> findByStatus(TrailerStatus status) {
        String sql = "SELECT * FROM trailers WHERE status = ? ORDER BY trailer_number";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, status.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trailers by status: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> findByAssignedDriver(String driverName) {
        String sql = "SELECT * FROM trailers WHERE assigned_driver = ? ORDER BY trailer_number";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, driverName);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trailers by assigned driver: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> findByAssignedTruck(String truckNumber) {
        String sql = "SELECT * FROM trailers WHERE assigned_truck = ? ORDER BY trailer_number";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, truckNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trailers by assigned truck: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> findAvailable() {
        String sql = "SELECT * FROM trailers WHERE is_assigned = 0 AND status = ? ORDER BY trailer_number";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, TrailerStatus.ACTIVE.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find available trailers: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find available trailers", e);
        }
    }
    
    public List<Trailer> findDueForInspection(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trailers 
            WHERE next_inspection_due_date IS NOT NULL 
            AND date(next_inspection_due_date) <= date(?) 
            ORDER BY next_inspection_due_date
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, cutoffDate.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trailers due for inspection: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> findDueForService(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trailers 
            WHERE next_service_due_date IS NOT NULL 
            AND date(next_service_due_date) <= date(?) 
            ORDER BY next_service_due_date
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, cutoffDate.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trailers due for service: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> findByExpiringRegistration(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trailers 
            WHERE registration_expiry_date IS NOT NULL 
            AND date(registration_expiry_date) <= date(?) 
            ORDER BY registration_expiry_date
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, cutoffDate.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trailers with expiring registration: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trailers", e);
        }
    }
    
    public List<Trailer> search(String searchTerm) {
        String sql = """
            SELECT * FROM trailers 
            WHERE trailer_number LIKE ? 
               OR vin LIKE ? 
               OR make LIKE ? 
               OR model LIKE ? 
               OR license_plate LIKE ? 
               OR current_location LIKE ? 
               OR notes LIKE ?
            ORDER BY trailer_number
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            String pattern = "%" + searchTerm + "%";
            for (int i = 1; i <= 7; i++) {
                pstmt.setString(i, pattern);
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Trailer> trailers = new ArrayList<>();
                while (rs.next()) {
                    trailers.add(mapResultSetToTrailer(rs));
                }
                return trailers;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to search trailers: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to search trailers", e);
        }
    }
    
    // Utility methods
    
    public int getTrailersCount() {
        String sql = "SELECT COUNT(*) FROM trailers";
        
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get trailers count: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get trailers count", e);
        }
    }
    
    public int getAvailableTrailersCount() {
        String sql = "SELECT COUNT(*) FROM trailers WHERE is_assigned = 0 AND status = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, TrailerStatus.ACTIVE.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get available trailers count: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get available trailers count", e);
        }
    }
    
    /**
     * Add or update multiple trailers from import
     * Checks for existing trailers by trailer number and updates them, or adds new ones
     * Returns the list of trailers with proper IDs assigned
     */
    public List<Trailer> addOrUpdateAll(List<Trailer> trailers) {
        logger.info("Processing {} trailers for import", trailers.size());
        List<Trailer> resultTrailers = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                int added = 0;
                int updated = 0;
                
                for (Trailer importedTrailer : trailers) {
                    // Check if trailer exists by trailer number
                    Trailer existing = findByTrailerNumber(importedTrailer.getTrailerNumber());
                    
                    if (existing != null) {
                        // Merge imported data with existing data
                        mergeTrailerData(existing, importedTrailer);
                        update(existing);
                        resultTrailers.add(existing); // Use existing trailer with proper ID
                        updated++;
                        logger.debug("Updated existing trailer: {} (ID: {})", existing.getTrailerNumber(), existing.getId());
                    } else {
                        // Add new trailer
                        Trailer insertedTrailer = insert(importedTrailer);
                        resultTrailers.add(insertedTrailer); // Use inserted trailer with proper ID
                        added++;
                        logger.debug("Added new trailer: {} (ID: {})", insertedTrailer.getTrailerNumber(), insertedTrailer.getId());
                    }
                }
                
                conn.commit();
                logger.info("Import completed - Added: {}, Updated: {}", added, updated);
                
                // Validate that all result trailers have proper IDs
                for (Trailer trailer : resultTrailers) {
                    if (trailer.getId() <= 0) {
                        logger.error("CRITICAL: Trailer {} has invalid ID: {}", trailer.getTrailerNumber(), trailer.getId());
                    } else {
                        logger.debug("Trailer {} has valid ID: {}", trailer.getTrailerNumber(), trailer.getId());
                    }
                }
                
            } catch (Exception e) {
                conn.rollback();
                logger.error("Import failed, rolling back transaction", e);
                throw new DataAccessException("Import failed", e);
            }
        } catch (SQLException e) {
            logger.error("Database connection error during import", e);
            throw new DataAccessException("Database connection error", e);
        }
        
        return resultTrailers;
    }
    
    /**
     * Merge imported trailer data with existing trailer data
     * Only updates fields that are provided in the import, preserves existing data
     */
    private void mergeTrailerData(Trailer existing, Trailer imported) {
        // Only update fields that are provided in the import
        if (imported.getYear() > 0) {
            existing.setYear(imported.getYear());
        }
        
        if (imported.getLicensePlate() != null && !imported.getLicensePlate().isEmpty()) {
            existing.setLicensePlate(imported.getLicensePlate());
        }
        
        if (imported.getMake() != null && !imported.getMake().isEmpty()) {
            existing.setMake(imported.getMake());
        }
        
        if (imported.getVin() != null && !imported.getVin().isEmpty()) {
            existing.setVin(imported.getVin());
        }
        
        if (imported.getLastInspectionDate() != null) {
            existing.setLastInspectionDate(imported.getLastInspectionDate());
        }
        
        if (imported.getNextInspectionDueDate() != null) {
            existing.setNextInspectionDueDate(imported.getNextInspectionDueDate());
        }
        
        // Update last modified timestamp
        existing.setLastUpdated(LocalDateTime.now());
        existing.setUpdatedBy("IMPORT");
    }
    
    // Helper methods
    
    private void setTrailerParameters(PreparedStatement pstmt, Trailer trailer) throws SQLException {
        // Match the exact column order from the INSERT/UPDATE statements
        pstmt.setString(1, trailer.getTrailerNumber());
        pstmt.setString(2, trailer.getVin());
        pstmt.setString(3, trailer.getMake());
        pstmt.setString(4, trailer.getModel());
        pstmt.setString(5, trailer.getType());
        pstmt.setString(6, trailer.getStatus() != null ? trailer.getStatus().name() : TrailerStatus.ACTIVE.name());
        pstmt.setString(7, trailer.getAssignedDriver()); // assigned_to
        
        // Date fields as strings for SQLite
        pstmt.setString(8, trailer.getRegistrationExpiryDate() != null ? trailer.getRegistrationExpiryDate().toString() : null);
        pstmt.setString(9, trailer.getInsuranceExpiryDate() != null ? trailer.getInsuranceExpiryDate().toString() : null);
        pstmt.setString(10, trailer.getNextInspectionDueDate() != null ? trailer.getNextInspectionDueDate().toString() : null); // inspection_expiry
        
        pstmt.setString(11, trailer.getLicensePlate());
        pstmt.setInt(12, trailer.getYear());
        pstmt.setDouble(13, trailer.getLength());
        pstmt.setDouble(14, trailer.getWidth());
        pstmt.setDouble(15, trailer.getHeight());
        pstmt.setDouble(16, trailer.getCapacity());
        pstmt.setDouble(17, trailer.getMaxWeight());
        pstmt.setDouble(18, trailer.getEmptyWeight());
        pstmt.setInt(19, trailer.getAxleCount());
        pstmt.setString(20, trailer.getSuspensionType());
        pstmt.setBoolean(21, trailer.isHasThermalUnit());
        pstmt.setString(22, trailer.getThermalUnitDetails());
        pstmt.setString(23, trailer.getOwnershipType());
        pstmt.setDouble(24, trailer.getPurchasePrice());
        
        pstmt.setString(25, trailer.getPurchaseDate() != null ? trailer.getPurchaseDate().toString() : null);
        pstmt.setDouble(26, trailer.getCurrentValue());
        pstmt.setString(27, trailer.getCurrentLocation());
        pstmt.setDouble(28, trailer.getMonthlyLeaseCost());
        pstmt.setString(29, trailer.getLeaseDetails());
        pstmt.setString(30, trailer.getLeaseAgreementExpiryDate() != null ? trailer.getLeaseAgreementExpiryDate().toString() : null);
        pstmt.setString(31, trailer.getInsurancePolicyNumber());
        
        pstmt.setString(32, trailer.getLastInspectionDate() != null ? trailer.getLastInspectionDate().toString() : null);
        pstmt.setString(33, trailer.getNextInspectionDueDate() != null ? trailer.getNextInspectionDueDate().toString() : null);
        pstmt.setString(34, trailer.getLastServiceDate() != null ? trailer.getLastServiceDate().toString() : null);
        pstmt.setString(35, trailer.getNextServiceDueDate() != null ? trailer.getNextServiceDueDate().toString() : null);
        
        pstmt.setString(36, trailer.getCurrentCondition());
        pstmt.setString(37, trailer.getMaintenanceNotes());
        pstmt.setString(38, trailer.getAssignedDriver());
        pstmt.setString(39, trailer.getAssignedTruck());
        pstmt.setBoolean(40, trailer.isAssigned());
        pstmt.setString(41, trailer.getCurrentJobId());
        
        pstmt.setTimestamp(42, trailer.getLastUpdated() != null ? Timestamp.valueOf(trailer.getLastUpdated()) : Timestamp.valueOf(LocalDateTime.now()));
        pstmt.setString(43, trailer.getUpdatedBy() != null ? trailer.getUpdatedBy() : "mgubran1");
        pstmt.setString(44, trailer.getNotes());
        pstmt.setInt(45, trailer.getOdometerReading());
    }
    
    private Trailer mapResultSetToTrailer(ResultSet rs) throws SQLException {
        Trailer trailer = new Trailer();
        
        trailer.setId(rs.getInt("id"));
        trailer.setTrailerNumber(rs.getString("trailer_number"));
        trailer.setVin(rs.getString("vin"));
        trailer.setMake(rs.getString("make"));
        trailer.setModel(rs.getString("model"));
        trailer.setType(rs.getString("type"));
        
        String statusStr = rs.getString("status");
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                trailer.setStatus(TrailerStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                trailer.setStatus(TrailerStatus.ACTIVE);
                logger.warn("Invalid status value in database: {}", statusStr);
            }
        } else {
            trailer.setStatus(TrailerStatus.ACTIVE);
        }
        
        // Handle assigned_to column which maps to assignedDriver
        String assignedTo = rs.getString("assigned_to");
        if (assignedTo != null && !assignedTo.isEmpty()) {
            trailer.setAssignedDriver(assignedTo);
        }
        
        trailer.setLicensePlate(rs.getString("license_plate"));
        trailer.setYear(rs.getInt("year"));
        
        // Date fields
        String regExpiry = rs.getString("registration_expiry_date");
        if (regExpiry != null && !regExpiry.isEmpty()) {
            try {
                trailer.setRegistrationExpiryDate(LocalDate.parse(regExpiry));
            } catch (Exception e) {
                logger.warn("Invalid registration expiry date: {}", regExpiry);
            }
        }
        
        String insExpiry = rs.getString("insurance_expiry_date");
        if (insExpiry != null && !insExpiry.isEmpty()) {
            try {
                trailer.setInsuranceExpiryDate(LocalDate.parse(insExpiry));
            } catch (Exception e) {
                logger.warn("Invalid insurance expiry date: {}", insExpiry);
            }
        }
        
        String inspExpiry = rs.getString("inspection_expiry");
        if (inspExpiry != null && !inspExpiry.isEmpty()) {
            try {
                trailer.setNextInspectionDueDate(LocalDate.parse(inspExpiry));
            } catch (Exception e) {
                logger.warn("Invalid inspection expiry date: {}", inspExpiry);
            }
        }
        
        trailer.setLength(rs.getDouble("length"));
        trailer.setWidth(rs.getDouble("width"));
        trailer.setHeight(rs.getDouble("height"));
        trailer.setCapacity(rs.getDouble("capacity"));
        trailer.setMaxWeight(rs.getDouble("max_weight"));
        trailer.setEmptyWeight(rs.getDouble("empty_weight"));
        trailer.setAxleCount(rs.getInt("axle_count"));
        trailer.setSuspensionType(rs.getString("suspension_type"));
        trailer.setHasThermalUnit(rs.getBoolean("has_thermal_unit"));
        trailer.setThermalUnitDetails(rs.getString("thermal_unit_details"));
        trailer.setOwnershipType(rs.getString("ownership_type"));
        trailer.setPurchasePrice(rs.getDouble("purchase_price"));
        
        String purchaseDate = rs.getString("purchase_date");
        if (purchaseDate != null && !purchaseDate.isEmpty()) {
            try {
                trailer.setPurchaseDate(LocalDate.parse(purchaseDate));
            } catch (Exception e) {
                logger.warn("Invalid purchase date: {}", purchaseDate);
            }
        }
        
        trailer.setCurrentValue(rs.getDouble("current_value"));
        trailer.setCurrentLocation(rs.getString("current_location"));
        trailer.setMonthlyLeaseCost(rs.getDouble("monthly_lease_cost"));
        trailer.setLeaseDetails(rs.getString("lease_details"));
        
        String leaseExpiry = rs.getString("lease_agreement_expiry_date");
        if (leaseExpiry != null && !leaseExpiry.isEmpty()) {
            try {
                trailer.setLeaseAgreementExpiryDate(LocalDate.parse(leaseExpiry));
            } catch (Exception e) {
                logger.warn("Invalid lease agreement expiry date: {}", leaseExpiry);
            }
        }
        
        trailer.setInsurancePolicyNumber(rs.getString("insurance_policy_number"));
        
        String lastInspection = rs.getString("last_inspection_date");
        if (lastInspection != null && !lastInspection.isEmpty()) {
            try {
                trailer.setLastInspectionDate(LocalDate.parse(lastInspection));
            } catch (Exception e) {
                logger.warn("Invalid last inspection date: {}", lastInspection);
            }
        }
        
        String nextInspection = rs.getString("next_inspection_due_date");
        if (nextInspection != null && !nextInspection.isEmpty()) {
            try {
                trailer.setNextInspectionDueDate(LocalDate.parse(nextInspection));
            } catch (Exception e) {
                logger.warn("Invalid next inspection date: {}", nextInspection);
            }
        }
        
        String lastService = rs.getString("last_service_date");
        if (lastService != null && !lastService.isEmpty()) {
            try {
                trailer.setLastServiceDate(LocalDate.parse(lastService));
            } catch (Exception e) {
                logger.warn("Invalid last service date: {}", lastService);
            }
        }
        
        String nextService = rs.getString("next_service_due_date");
        if (nextService != null && !nextService.isEmpty()) {
            try {
                trailer.setNextServiceDueDate(LocalDate.parse(nextService));
            } catch (Exception e) {
                logger.warn("Invalid next service date: {}", nextService);
            }
        }
        
        trailer.setCurrentCondition(rs.getString("current_condition"));
        trailer.setMaintenanceNotes(rs.getString("maintenance_notes"));
        trailer.setAssignedDriver(rs.getString("assigned_driver"));
        trailer.setAssignedTruck(rs.getString("assigned_truck"));
        trailer.setAssigned(rs.getBoolean("is_assigned"));
        trailer.setCurrentJobId(rs.getString("current_job_id"));
        
        Timestamp lastUpdated = rs.getTimestamp("last_updated");
        if (lastUpdated != null) {
            trailer.setLastUpdated(lastUpdated.toLocalDateTime());
        }
        
        trailer.setUpdatedBy(rs.getString("updated_by"));
        trailer.setNotes(rs.getString("notes"));
        
        try {
            trailer.setOdometerReading(rs.getInt("odometer_reading"));
        } catch (SQLException e) {
            // Column might not exist in older databases
            logger.debug("Odometer reading column not found");
        }
        
        return trailer;
    }
    
    // Generate sample data for testing
    public void generateSampleData() {
        if (getTrailersCount() > 0) {
            logger.info("Sample trailer data already exists, skipping generation");
            return;
        }
        
        logger.info("Generating sample trailer data");
        
        try {
            // Sample dry van trailers
            for (int i = 1; i <= 5; i++) {
                Trailer trailer = new Trailer();
                trailer.setTrailerNumber("DVT-" + (1000 + i));
                trailer.setVin("1DRY" + i + "00000" + i);
                trailer.setMake("Great Dane");
                trailer.setModel("Champion");
                trailer.setYear(2020 + (i % 3));
                trailer.setType("Dry Van");
                trailer.setStatus(TrailerStatus.ACTIVE);
                trailer.setLicensePlate("TR" + (7000 + i));
                trailer.setRegistrationExpiryDate(LocalDate.now().plusMonths(6 + i));
                trailer.setLength(53.0);
                trailer.setWidth(8.5);
                trailer.setHeight(13.5);
                trailer.setCapacity(4000 + (i * 50));
                trailer.setMaxWeight(45000 + (i * 500));
                trailer.setEmptyWeight(14000 + (i * 100));
                trailer.setAxleCount(2);
                trailer.setSuspensionType("Air Ride");
                trailer.setOwnershipType("Company");
                trailer.setPurchasePrice(32000 + (i * 500));
                trailer.setPurchaseDate(LocalDate.now().minusYears(2).minusMonths(i));
                trailer.setCurrentValue(28000 - (i * 1000));
                trailer.setInsurancePolicyNumber("INS-TR-" + (2000 + i));
                trailer.setInsuranceExpiryDate(LocalDate.now().plusMonths(3 + i));
                trailer.setLastInspectionDate(LocalDate.now().minusMonths(3));
                trailer.setNextInspectionDueDate(LocalDate.now().plusMonths(3));
                trailer.setLastServiceDate(LocalDate.now().minusMonths(2));
                trailer.setNextServiceDueDate(LocalDate.now().plusMonths(4));
                trailer.setCurrentCondition("Good");
                
                if (i % 3 == 0) {
                    trailer.setAssignedDriver("John Smith");
                    trailer.setAssignedTruck("TRK-8001");
                    trailer.setAssigned(true);
                    trailer.setCurrentLocation("On Route I-95");
                } else if (i % 3 == 1) {
                    trailer.setAssignedDriver("Mike Johnson");
                    trailer.setAssignedTruck("TRK-8002");
                    trailer.setAssigned(true);
                    trailer.setCurrentLocation("Warehouse #3");
                } else {
                    trailer.setCurrentLocation("Main Yard");
                }
                
                save(trailer);
            }
            
            // Sample refrigerated trailers
            for (int i = 1; i <= 3; i++) {
                Trailer trailer = new Trailer();
                trailer.setTrailerNumber("RFT-" + (2000 + i));
                trailer.setVin("1REF" + i + "00000" + i);
                trailer.setMake("Utility");
                trailer.setModel("3000R");
                trailer.setYear(2021 + (i % 2));
                trailer.setType("Refrigerated");
                trailer.setStatus(TrailerStatus.ACTIVE);
                trailer.setLicensePlate("TR" + (8000 + i));
                trailer.setRegistrationExpiryDate(LocalDate.now().plusMonths(8 + i));
                trailer.setLength(53.0);
                trailer.setWidth(8.5);
                trailer.setHeight(13.6);
                trailer.setCapacity(4200 + (i * 50));
                trailer.setMaxWeight(44000 + (i * 400));
                trailer.setEmptyWeight(16000 + (i * 200));
                trailer.setAxleCount(2);
                trailer.setSuspensionType("Air Ride");
                trailer.setHasThermalUnit(true);
                trailer.setThermalUnitDetails("Carrier Transicold Model X4 7300");
                trailer.setOwnershipType("Company");
                trailer.setPurchasePrice(65000 + (i * 1000));
                trailer.setPurchaseDate(LocalDate.now().minusYears(1).minusMonths(i));
                trailer.setCurrentValue(60000 - (i * 2000));
                trailer.setInsurancePolicyNumber("INS-TR-" + (3000 + i));
                trailer.setInsuranceExpiryDate(LocalDate.now().plusMonths(5 + i));
                trailer.setLastInspectionDate(LocalDate.now().minusMonths(2));
                trailer.setNextInspectionDueDate(LocalDate.now().plusMonths(4));
                trailer.setLastServiceDate(LocalDate.now().minusMonths(1));
                trailer.setNextServiceDueDate(LocalDate.now().plusMonths(5));
                trailer.setCurrentCondition("Excellent");
                
                if (i % 2 == 0) {
                    trailer.setAssignedDriver("Alex Rodriguez");
                    trailer.setAssignedTruck("TRK-8003");
                    trailer.setAssigned(true);
                    trailer.setCurrentLocation("Cold Storage Facility #2");
                } else {
                    trailer.setCurrentLocation("Main Yard");
                }
                
                save(trailer);
            }
            
            // Sample flatbed trailer
            Trailer flatbed = new Trailer();
            flatbed.setTrailerNumber("FBT-3001");
            flatbed.setVin("1FLT10000001");
            flatbed.setMake("Fontaine");
            flatbed.setModel("Revolution");
            flatbed.setYear(2022);
            flatbed.setType("Flatbed");
            flatbed.setStatus(TrailerStatus.ACTIVE);
            flatbed.setLicensePlate("TR9001");
            flatbed.setRegistrationExpiryDate(LocalDate.now().plusMonths(7));
            flatbed.setLength(48.0);
            flatbed.setWidth(8.5);
            flatbed.setHeight(5.0);
            flatbed.setCapacity(3000);
            flatbed.setMaxWeight(48000);
            flatbed.setEmptyWeight(12000);
            flatbed.setAxleCount(2);
            flatbed.setSuspensionType("Spring");
            flatbed.setOwnershipType("Leased");
            flatbed.setMonthlyLeaseCost(950.0);
            flatbed.setLeaseDetails("36 month lease from TransLease Inc.");
            flatbed.setCurrentValue(40000);
            flatbed.setInsurancePolicyNumber("INS-TR-4001");
            flatbed.setInsuranceExpiryDate(LocalDate.now().plusMonths(9));
            flatbed.setLastInspectionDate(LocalDate.now().minusMonths(1));
            flatbed.setNextInspectionDueDate(LocalDate.now().plusMonths(5));
            flatbed.setLastServiceDate(LocalDate.now().minusMonths(2));
            flatbed.setNextServiceDueDate(LocalDate.now().plusMonths(4));
            flatbed.setCurrentCondition("Good");
            flatbed.setCurrentLocation("Main Yard");
            save(flatbed);
            
            // One maintenance trailer
            Trailer maintenance = new Trailer();
            maintenance.setTrailerNumber("DVT-1006");
            maintenance.setVin("1DRY600000006");
            maintenance.setMake("Great Dane");
            maintenance.setModel("Champion");
            maintenance.setYear(2019);
            maintenance.setType("Dry Van");
            maintenance.setStatus(TrailerStatus.MAINTENANCE);
            maintenance.setLicensePlate("TR7006");
            maintenance.setRegistrationExpiryDate(LocalDate.now().plusMonths(4));
            maintenance.setLength(53.0);
            maintenance.setWidth(8.5);
            maintenance.setHeight(13.5);
            maintenance.setCapacity(4000);
            maintenance.setMaxWeight(45000);
            maintenance.setEmptyWeight(14000);
            maintenance.setAxleCount(2);
            maintenance.setSuspensionType("Air Ride");
            maintenance.setOwnershipType("Company");
            maintenance.setPurchasePrice(28000);
            maintenance.setPurchaseDate(LocalDate.now().minusYears(4));
            maintenance.setCurrentValue(18000);
            maintenance.setInsurancePolicyNumber("INS-TR-2006");
            maintenance.setInsuranceExpiryDate(LocalDate.now().plusMonths(2));
            maintenance.setLastInspectionDate(LocalDate.now().minusMonths(6));
            maintenance.setNextInspectionDueDate(LocalDate.now());
            maintenance.setLastServiceDate(LocalDate.now().minusMonths(5));
            maintenance.setNextServiceDueDate(LocalDate.now().plusMonths(1));
            maintenance.setCurrentCondition("Needs Repair");
            maintenance.setMaintenanceNotes("In shop for brake repair and annual inspection");
            maintenance.setCurrentLocation("Service Center");
            save(maintenance);
            
            logger.info("Generated sample data for trailers");
            
        } catch (Exception e) {
            logger.error("Failed to generate sample trailer data: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to generate sample data", e);
        }
    }
}