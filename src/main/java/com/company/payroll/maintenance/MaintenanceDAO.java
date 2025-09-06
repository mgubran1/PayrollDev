package com.company.payroll.maintenance;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for MaintenanceRecord operations.
 * Handles all database interactions for maintenance records and schedules.
 */
public class MaintenanceDAO {
    private static final Logger logger = LoggerFactory.getLogger(MaintenanceDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    
    public MaintenanceDAO() {
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create maintenance_records table
            String createMaintenanceTable = """
                CREATE TABLE IF NOT EXISTS maintenance_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    vehicle_type TEXT NOT NULL,
                    vehicle_id INTEGER NOT NULL,
                    vehicle TEXT NOT NULL,
                    service_date DATE NOT NULL,
                    service_type TEXT NOT NULL,
                    description TEXT,
                    mileage INTEGER,
                    cost REAL NOT NULL,
                    labor_cost REAL DEFAULT 0,
                    parts_cost REAL DEFAULT 0,
                    tax_amount REAL DEFAULT 0,
                    technician TEXT,
                    status TEXT NOT NULL,
                    priority TEXT DEFAULT 'MEDIUM',
                    notes TEXT,
                    next_due DATE,
                    receipt_number TEXT,
                    receipt_path TEXT,
                    service_provider TEXT,
                    provider_location TEXT,
                    provider_phone TEXT,
                    work_order_number TEXT,
                    scheduled_start_time TIMESTAMP,
                    actual_start_time TIMESTAMP,
                    completion_time TIMESTAMP,
                    labor_hours REAL DEFAULT 0,
                    performed_by TEXT,
                    authorized_by TEXT,
                    hours_at_service INTEGER,
                    parts_used TEXT,
                    labor_description TEXT,
                    additional_notes TEXT,
                    warranty_info TEXT,
                    warranty_expiry DATE,
                    is_warranty_claim BOOLEAN DEFAULT 0,
                    defect_found TEXT,
                    corrective_action TEXT,
                    preventive_action TEXT,
                    downtime_hours INTEGER DEFAULT 0,
                    downtime_cost REAL DEFAULT 0,
                    attached_documents TEXT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT DEFAULT 'mgubran1',
                    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    modified_by TEXT DEFAULT 'mgubran1'
                )
            """;
            stmt.execute(createMaintenanceTable);
            
            // Create maintenance_schedules table
            String createScheduleTable = """
                CREATE TABLE IF NOT EXISTS maintenance_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    vehicle TEXT NOT NULL,
                    service TEXT NOT NULL,
                    due_date DATE NOT NULL,
                    due_mileage INTEGER,
                    priority TEXT DEFAULT 'MEDIUM',
                    notes TEXT,
                    is_recurring BOOLEAN DEFAULT 0,
                    recurrence_interval_days INTEGER,
                    recurrence_interval_miles INTEGER,
                    last_completed_date DATE,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT DEFAULT 'mgubran1',
                    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    modified_by TEXT DEFAULT 'mgubran1'
                )
            """;
            stmt.execute(createScheduleTable);
            
            // Create index for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_maintenance_vehicle ON maintenance_records(vehicle)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_maintenance_date ON maintenance_records(service_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_maintenance_status ON maintenance_records(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_schedule_due_date ON maintenance_schedules(due_date)");
            
            logger.info("Maintenance database tables initialized successfully");
            
        } catch (SQLException e) {
            logger.error("Failed to initialize maintenance database", e);
            throw new DataAccessException("Failed to initialize database", e);
        }
    }
    
    // CRUD Operations for MaintenanceRecord
    
    public MaintenanceRecord save(MaintenanceRecord record) throws DataAccessException {
        if (record.getId() > 0) {
            return update(record);
        } else {
            return insert(record);
        }
    }
    
    private MaintenanceRecord insert(MaintenanceRecord record) throws DataAccessException {
        String sql = """
            INSERT INTO maintenance_records (
                vehicle_type, vehicle_id, vehicle, service_date, service_type, 
                description, mileage, cost, labor_cost, parts_cost, tax_amount,
                technician, status, priority, notes, next_due, receipt_number, 
                receipt_path, service_provider, provider_location, provider_phone,
                work_order_number, scheduled_start_time, actual_start_time, 
                completion_time, labor_hours, performed_by, authorized_by,
                hours_at_service, parts_used, labor_description, additional_notes,
                warranty_info, warranty_expiry, is_warranty_claim, defect_found,
                corrective_action, preventive_action, downtime_hours, downtime_cost,
                attached_documents, created_by, modified_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                     ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                     ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setParameters(pstmt, record);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Creating maintenance record failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    record.setId(generatedKeys.getInt(1));
                    logger.info("Created maintenance record with ID: {}", record.getId());
                }
            }
            
            return record;
            
        } catch (SQLException e) {
            logger.error("Failed to insert maintenance record", e);
            throw new DataAccessException("Failed to insert maintenance record", e);
        }
    }
    
    private MaintenanceRecord update(MaintenanceRecord record) throws DataAccessException {
        String sql = """
            UPDATE maintenance_records SET
                vehicle_type = ?, vehicle_id = ?, vehicle = ?, service_date = ?, 
                service_type = ?, description = ?, mileage = ?, cost = ?, 
                labor_cost = ?, parts_cost = ?, tax_amount = ?, technician = ?, 
                status = ?, priority = ?, notes = ?, next_due = ?, receipt_number = ?, 
                receipt_path = ?, service_provider = ?, provider_location = ?, 
                provider_phone = ?, work_order_number = ?, scheduled_start_time = ?, 
                actual_start_time = ?, completion_time = ?, labor_hours = ?, 
                performed_by = ?, authorized_by = ?, hours_at_service = ?, 
                parts_used = ?, labor_description = ?, additional_notes = ?, 
                warranty_info = ?, warranty_expiry = ?, is_warranty_claim = ?, 
                defect_found = ?, corrective_action = ?, preventive_action = ?, 
                downtime_hours = ?, downtime_cost = ?, attached_documents = ?, 
                modified_date = CURRENT_TIMESTAMP, modified_by = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setUpdateParameters(pstmt, record);
            pstmt.setInt(43, record.getId());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Updating maintenance record failed, no rows affected.");
            }
            
            logger.info("Updated maintenance record with ID: {}", record.getId());
            return record;
            
        } catch (SQLException e) {
            logger.error("Failed to update maintenance record", e);
            throw new DataAccessException("Failed to update maintenance record", e);
        }
    }
    
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM maintenance_records WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new DataAccessException("Deleting maintenance record failed, no rows affected.");
            }
            
            logger.info("Deleted maintenance record with ID: {}", id);
            
        } catch (SQLException e) {
            logger.error("Failed to delete maintenance record", e);
            throw new DataAccessException("Failed to delete maintenance record", e);
        }
    }
    
    public MaintenanceRecord findById(int id) throws DataAccessException {
        String sql = "SELECT * FROM maintenance_records WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMaintenanceRecord(rs);
                }
            }
            
            return null;
            
        } catch (SQLException e) {
            logger.error("Failed to find maintenance record by ID", e);
            throw new DataAccessException("Failed to find maintenance record", e);
        }
    }
    
    public List<MaintenanceRecord> findAll() throws DataAccessException {
        String sql = "SELECT * FROM maintenance_records ORDER BY service_date DESC";
        return executeQuery(sql);
    }
    
    public List<MaintenanceRecord> findByVehicle(String vehicle) throws DataAccessException {
        String sql = "SELECT * FROM maintenance_records WHERE vehicle = ? ORDER BY service_date DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, vehicle);
            return executeQuery(pstmt);
            
        } catch (SQLException e) {
            logger.error("Failed to find maintenance records by vehicle", e);
            throw new DataAccessException("Failed to find maintenance records", e);
        }
    }
    
    public List<MaintenanceRecord> findByDateRange(LocalDate startDate, LocalDate endDate) throws DataAccessException {
        String sql = "SELECT * FROM maintenance_records WHERE service_date BETWEEN ? AND ? ORDER BY service_date DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            return executeQuery(pstmt);
            
        } catch (SQLException e) {
            logger.error("Failed to find maintenance records by date range", e);
            throw new DataAccessException("Failed to find maintenance records", e);
        }
    }
    
    public List<MaintenanceRecord> findByStatus(String status) throws DataAccessException {
        String sql = "SELECT * FROM maintenance_records WHERE status = ? ORDER BY service_date DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, status);
            return executeQuery(pstmt);
            
        } catch (SQLException e) {
            logger.error("Failed to find maintenance records by status", e);
            throw new DataAccessException("Failed to find maintenance records", e);
        }
    }
    
    public List<MaintenanceRecord> search(String searchTerm) throws DataAccessException {
        String sql = """
            SELECT * FROM maintenance_records 
            WHERE vehicle LIKE ? OR service_type LIKE ? OR technician LIKE ? 
            OR description LIKE ? OR notes LIKE ?
            ORDER BY service_date DESC
        """;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String searchPattern = "%" + searchTerm + "%";
            for (int i = 1; i <= 5; i++) {
                pstmt.setString(i, searchPattern);
            }
            
            return executeQuery(pstmt);
            
        } catch (SQLException e) {
            logger.error("Failed to search maintenance records", e);
            throw new DataAccessException("Failed to search maintenance records", e);
        }
    }
    
    
    public double getAverageCostByServiceType(String serviceType) throws DataAccessException {
        String sql = "SELECT AVG(cost) as avg_cost FROM maintenance_records WHERE service_type = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, serviceType);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("avg_cost");
                }
            }
            
            return 0.0;
            
        } catch (SQLException e) {
            logger.error("Failed to get average cost by service type", e);
            throw new DataAccessException("Failed to get average cost", e);
        }
    }
    
    public int getMaintenanceCountByVehicle(String vehicle) throws DataAccessException {
        String sql = "SELECT COUNT(*) as count FROM maintenance_records WHERE vehicle = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, vehicle);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
            
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get maintenance count by vehicle", e);
            throw new DataAccessException("Failed to get maintenance count", e);
        }
    }
    
    // Helper methods
    
    private void setParameters(PreparedStatement pstmt, MaintenanceRecord record) throws SQLException {
        pstmt.setString(1, record.getVehicleType() != null ? record.getVehicleType().name() : "TRUCK");
        pstmt.setInt(2, record.getVehicleId());
        pstmt.setString(3, record.getVehicle());
        pstmt.setDate(4, Date.valueOf(record.getDate()));
        pstmt.setString(5, record.getServiceType());
        pstmt.setString(6, record.getDescription());
        pstmt.setInt(7, record.getMileage());
        pstmt.setDouble(8, record.getCost());
        pstmt.setDouble(9, record.getLaborCost());
        pstmt.setDouble(10, record.getPartsCost());
        pstmt.setDouble(11, record.getTaxAmount());
        pstmt.setString(12, record.getTechnician());
        pstmt.setString(13, record.getStatus());
        pstmt.setString(14, record.getPriority() != null ? record.getPriority().name() : "MEDIUM");
        pstmt.setString(15, record.getNotes());
        pstmt.setDate(16, record.getNextDue() != null ? Date.valueOf(record.getNextDue()) : null);
        pstmt.setString(17, record.getReceiptNumber());
        pstmt.setString(18, record.getReceiptPath());
        pstmt.setString(19, record.getServiceProvider());
        pstmt.setString(20, record.getProviderLocation());
        pstmt.setString(21, record.getProviderPhone());
        pstmt.setString(22, record.getWorkOrderNumber());
        pstmt.setTimestamp(23, record.getScheduledStartTime() != null ? 
                         Timestamp.valueOf(record.getScheduledStartTime()) : null);
        pstmt.setTimestamp(24, record.getActualStartTime() != null ? 
                         Timestamp.valueOf(record.getActualStartTime()) : null);
        pstmt.setTimestamp(25, record.getCompletionTime() != null ? 
                         Timestamp.valueOf(record.getCompletionTime()) : null);
        pstmt.setDouble(26, record.getLaborHours());
        pstmt.setString(27, record.getPerformedBy());
        pstmt.setString(28, record.getAuthorizedBy());
        pstmt.setInt(29, record.getHoursAtService());
        pstmt.setString(30, record.getPartsUsed());
        pstmt.setString(31, record.getLaborDescription());
        pstmt.setString(32, record.getAdditionalNotes());
        pstmt.setString(33, record.getWarrantyInfo());
        pstmt.setDate(34, record.getWarrantyExpiry() != null ? Date.valueOf(record.getWarrantyExpiry()) : null);
        pstmt.setBoolean(35, record.isWarrantyClaim());
        pstmt.setString(36, record.getDefectFound());
        pstmt.setString(37, record.getCorrectiveAction());
        pstmt.setString(38, record.getPreventiveAction());
        pstmt.setInt(39, record.getDowntimeHours());
        pstmt.setDouble(40, record.getDowntimeCost());
        pstmt.setString(41, record.getAttachedDocuments());
        pstmt.setString(42, record.getCreatedBy() != null ? record.getCreatedBy() : "mgubran1");
        pstmt.setString(43, record.getModifiedBy() != null ? record.getModifiedBy() : "mgubran1");
    }

    /**
     * Set parameters for UPDATE statements. Excludes the created_by column to
     * keep the parameter count in sync with the SQL statement.
     */
    private void setUpdateParameters(PreparedStatement pstmt, MaintenanceRecord record) throws SQLException {
        pstmt.setString(1, record.getVehicleType() != null ? record.getVehicleType().name() : "TRUCK");
        pstmt.setInt(2, record.getVehicleId());
        pstmt.setString(3, record.getVehicle());
        pstmt.setDate(4, Date.valueOf(record.getDate()));
        pstmt.setString(5, record.getServiceType());
        pstmt.setString(6, record.getDescription());
        pstmt.setInt(7, record.getMileage());
        pstmt.setDouble(8, record.getCost());
        pstmt.setDouble(9, record.getLaborCost());
        pstmt.setDouble(10, record.getPartsCost());
        pstmt.setDouble(11, record.getTaxAmount());
        pstmt.setString(12, record.getTechnician());
        pstmt.setString(13, record.getStatus());
        pstmt.setString(14, record.getPriority() != null ? record.getPriority().name() : "MEDIUM");
        pstmt.setString(15, record.getNotes());
        pstmt.setDate(16, record.getNextDue() != null ? Date.valueOf(record.getNextDue()) : null);
        pstmt.setString(17, record.getReceiptNumber());
        pstmt.setString(18, record.getReceiptPath());
        pstmt.setString(19, record.getServiceProvider());
        pstmt.setString(20, record.getProviderLocation());
        pstmt.setString(21, record.getProviderPhone());
        pstmt.setString(22, record.getWorkOrderNumber());
        pstmt.setTimestamp(23, record.getScheduledStartTime() != null ? Timestamp.valueOf(record.getScheduledStartTime()) : null);
        pstmt.setTimestamp(24, record.getActualStartTime() != null ? Timestamp.valueOf(record.getActualStartTime()) : null);
        pstmt.setTimestamp(25, record.getCompletionTime() != null ? Timestamp.valueOf(record.getCompletionTime()) : null);
        pstmt.setDouble(26, record.getLaborHours());
        pstmt.setString(27, record.getPerformedBy());
        pstmt.setString(28, record.getAuthorizedBy());
        pstmt.setInt(29, record.getHoursAtService());
        pstmt.setString(30, record.getPartsUsed());
        pstmt.setString(31, record.getLaborDescription());
        pstmt.setString(32, record.getAdditionalNotes());
        pstmt.setString(33, record.getWarrantyInfo());
        pstmt.setDate(34, record.getWarrantyExpiry() != null ? Date.valueOf(record.getWarrantyExpiry()) : null);
        pstmt.setBoolean(35, record.isWarrantyClaim());
        pstmt.setString(36, record.getDefectFound());
        pstmt.setString(37, record.getCorrectiveAction());
        pstmt.setString(38, record.getPreventiveAction());
        pstmt.setInt(39, record.getDowntimeHours());
        pstmt.setDouble(40, record.getDowntimeCost());
        pstmt.setString(41, record.getAttachedDocuments());
        pstmt.setString(42, record.getModifiedBy() != null ? record.getModifiedBy() : "mgubran1");
    }
    
    private MaintenanceRecord mapResultSetToMaintenanceRecord(ResultSet rs) throws SQLException {
        MaintenanceRecord record = new MaintenanceRecord();
        
        record.setId(rs.getInt("id"));
        
        String vehicleTypeStr = rs.getString("vehicle_type");
        if (vehicleTypeStr != null) {
            record.setVehicleType(MaintenanceRecord.VehicleType.valueOf(vehicleTypeStr));
        }
        
        record.setVehicleId(rs.getInt("vehicle_id"));
        record.setVehicle(rs.getString("vehicle"));
        record.setDate(rs.getDate("service_date").toLocalDate());
        record.setServiceType(rs.getString("service_type"));
        record.setDescription(rs.getString("description"));
        record.setMileage(rs.getInt("mileage"));
        record.setCost(rs.getDouble("cost"));
        record.setLaborCost(rs.getDouble("labor_cost"));
        record.setPartsCost(rs.getDouble("parts_cost"));
        record.setTaxAmount(rs.getDouble("tax_amount"));
        record.setTechnician(rs.getString("technician"));
        record.setStatus(rs.getString("status"));
        
        String priorityStr = rs.getString("priority");
        if (priorityStr != null) {
            try {
                record.setPriority(MaintenanceRecord.Priority.valueOf(priorityStr));
            } catch (IllegalArgumentException e) {
                record.setPriority(MaintenanceRecord.Priority.MEDIUM);
            }
        }
        
        record.setNotes(rs.getString("notes"));
        
        Date nextDue = rs.getDate("next_due");
        if (nextDue != null) {
            record.setNextDue(nextDue.toLocalDate());
        }
        
        record.setReceiptNumber(rs.getString("receipt_number"));
        record.setReceiptPath(rs.getString("receipt_path"));
        record.setServiceProvider(rs.getString("service_provider"));
        record.setProviderLocation(rs.getString("provider_location"));
        record.setProviderPhone(rs.getString("provider_phone"));
        record.setWorkOrderNumber(rs.getString("work_order_number"));
        
        Timestamp scheduledStart = rs.getTimestamp("scheduled_start_time");
        if (scheduledStart != null) {
            record.setScheduledStartTime(scheduledStart.toLocalDateTime());
        }
        
        Timestamp actualStart = rs.getTimestamp("actual_start_time");
        if (actualStart != null) {
            record.setActualStartTime(actualStart.toLocalDateTime());
        }
        
        Timestamp completion = rs.getTimestamp("completion_time");
        if (completion != null) {
            record.setCompletionTime(completion.toLocalDateTime());
        }
        
        record.setLaborHours(rs.getDouble("labor_hours"));
        record.setPerformedBy(rs.getString("performed_by"));
        record.setAuthorizedBy(rs.getString("authorized_by"));
        record.setHoursAtService(rs.getInt("hours_at_service"));
        record.setPartsUsed(rs.getString("parts_used"));
        record.setLaborDescription(rs.getString("labor_description"));
        record.setAdditionalNotes(rs.getString("additional_notes"));
        record.setWarrantyInfo(rs.getString("warranty_info"));
        
        Date warrantyExpiry = rs.getDate("warranty_expiry");
        if (warrantyExpiry != null) {
            record.setWarrantyExpiry(warrantyExpiry.toLocalDate());
        }
        
        record.setWarrantyClaim(rs.getBoolean("is_warranty_claim"));
        record.setDefectFound(rs.getString("defect_found"));
        record.setCorrectiveAction(rs.getString("corrective_action"));
        record.setPreventiveAction(rs.getString("preventive_action"));
        record.setDowntimeHours(rs.getInt("downtime_hours"));
        record.setDowntimeCost(rs.getDouble("downtime_cost"));
        record.setAttachedDocuments(rs.getString("attached_documents"));
        
        Timestamp createdDate = rs.getTimestamp("created_date");
        if (createdDate != null) {
            record.setCreatedDate(createdDate.toLocalDateTime());
        }
        
        record.setCreatedBy(rs.getString("created_by"));
        
        Timestamp modifiedDate = rs.getTimestamp("modified_date");
        if (modifiedDate != null) {
            record.setModifiedDate(modifiedDate.toLocalDateTime());
        }
        
        record.setModifiedBy(rs.getString("modified_by"));
        
        return record;
    }
    
    private List<MaintenanceRecord> executeQuery(String sql) throws DataAccessException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            return mapResultSet(rs);
            
        } catch (SQLException e) {
            logger.error("Failed to execute query", e);
            throw new DataAccessException("Failed to execute query", e);
        }
    }
    
    private List<MaintenanceRecord> executeQuery(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            return mapResultSet(rs);
        }
    }
    
    private List<MaintenanceRecord> mapResultSet(ResultSet rs) throws SQLException {
        List<MaintenanceRecord> records = new ArrayList<>();
        while (rs.next()) {
            records.add(mapResultSetToMaintenanceRecord(rs));
        }
        return records;
    }
}