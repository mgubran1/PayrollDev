package com.company.payroll.employees;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for percentage audit logging
 */
public class PercentageAuditLogDAO {
    private static final Logger logger = LoggerFactory.getLogger(PercentageAuditLogDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    private final Connection connection;
    
    public PercentageAuditLogDAO() {
        this(null);
    }
    
    public PercentageAuditLogDAO(Connection connection) {
        this.connection = connection;
        initializeTable();
    }
    
    private void initializeTable() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS percentage_audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employee_id INTEGER NOT NULL,
                employee_name TEXT NOT NULL,
                action TEXT NOT NULL,
                field_changed TEXT NOT NULL,
                old_value REAL NOT NULL,
                new_value REAL NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                performed_by TEXT,
                notes TEXT,
                session_id TEXT,
                FOREIGN KEY (employee_id) REFERENCES employees(id)
            )
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            
            // Create index for faster queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_employee_id ON percentage_audit_log(employee_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON percentage_audit_log(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_session ON percentage_audit_log(session_id)");
            
            logger.info("Percentage audit log table initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize percentage audit log table", e);
            // Don't throw exception here - allow the system to work without audit logging
            logger.warn("Continuing without audit logging functionality");
        }
    }
    
    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        return DatabaseConfig.getConnection();
    }
    
    /**
     * Log a single percentage change
     */
    public void logChange(PercentageAuditLog log) throws DataAccessException {
        String sql = """
            INSERT INTO percentage_audit_log 
            (employee_id, employee_name, action, field_changed, old_value, new_value, 
             timestamp, performed_by, notes, session_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, log.getEmployeeId());
            pstmt.setString(2, log.getEmployeeName());
            pstmt.setString(3, log.getAction());
            pstmt.setString(4, log.getFieldChanged());
            pstmt.setDouble(5, log.getOldValue());
            pstmt.setDouble(6, log.getNewValue());
            pstmt.setTimestamp(7, Timestamp.valueOf(log.getTimestamp()));
            pstmt.setString(8, log.getPerformedBy());
            pstmt.setString(9, log.getNotes());
            pstmt.setString(10, log.getSessionId());
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setId(rs.getInt(1));
                }
            }
            
            logger.debug("Logged percentage change: {}", log);
        } catch (SQLException e) {
            logger.error("Failed to log percentage change", e);
            throw new DataAccessException("Failed to log percentage change", e);
        }
    }
    
    /**
     * Log multiple percentage changes in a batch
     */
    public void logChanges(List<PercentageAuditLog> logs) throws DataAccessException {
        String sql = """
            INSERT INTO percentage_audit_log 
            (employee_id, employee_name, action, field_changed, old_value, new_value, 
             timestamp, performed_by, notes, session_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            
            for (PercentageAuditLog log : logs) {
                pstmt.setInt(1, log.getEmployeeId());
                pstmt.setString(2, log.getEmployeeName());
                pstmt.setString(3, log.getAction());
                pstmt.setString(4, log.getFieldChanged());
                pstmt.setDouble(5, log.getOldValue());
                pstmt.setDouble(6, log.getNewValue());
                pstmt.setTimestamp(7, Timestamp.valueOf(log.getTimestamp()));
                pstmt.setString(8, log.getPerformedBy());
                pstmt.setString(9, log.getNotes());
                pstmt.setString(10, log.getSessionId());
                
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
            conn.commit();
            
            logger.info("Logged {} percentage changes in batch", logs.size());
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Failed to rollback transaction", ex);
                }
            }
            logger.error("Failed to log percentage changes in batch", e);
            throw new DataAccessException("Failed to log percentage changes", e);
        } finally {
            if (pstmt != null) {
                try { pstmt.close(); } catch (SQLException e) { /* ignore */ }
            }
            if (conn != null && connection == null) {
                try { 
                    conn.setAutoCommit(true);
                    conn.close(); 
                } catch (SQLException e) { /* ignore */ }
            }
        }
    }
    
    /**
     * Get audit logs for a specific employee
     */
    public List<PercentageAuditLog> getLogsForEmployee(int employeeId) throws DataAccessException {
        String sql = """
            SELECT * FROM percentage_audit_log 
            WHERE employee_id = ? 
            ORDER BY timestamp DESC
        """;
        
        List<PercentageAuditLog> logs = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, employeeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get logs for employee {}", employeeId, e);
            throw new DataAccessException("Failed to get audit logs", e);
        }
        
        return logs;
    }
    
    /**
     * Get audit logs for a specific session
     */
    public List<PercentageAuditLog> getLogsForSession(String sessionId) throws DataAccessException {
        String sql = """
            SELECT * FROM percentage_audit_log 
            WHERE session_id = ? 
            ORDER BY timestamp DESC
        """;
        
        List<PercentageAuditLog> logs = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, sessionId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get logs for session {}", sessionId, e);
            throw new DataAccessException("Failed to get audit logs", e);
        }
        
        return logs;
    }
    
    /**
     * Get audit logs within a date range
     */
    public List<PercentageAuditLog> getLogsByDateRange(LocalDateTime from, LocalDateTime to) throws DataAccessException {
        String sql = """
            SELECT * FROM percentage_audit_log 
            WHERE timestamp BETWEEN ? AND ? 
            ORDER BY timestamp DESC
        """;
        
        List<PercentageAuditLog> logs = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setTimestamp(1, Timestamp.valueOf(from));
            pstmt.setTimestamp(2, Timestamp.valueOf(to));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get logs by date range", e);
            throw new DataAccessException("Failed to get audit logs", e);
        }
        
        return logs;
    }
    
    /**
     * Get the most recent logs
     */
    public List<PercentageAuditLog> getRecentLogs(int limit) throws DataAccessException {
        String sql = """
            SELECT * FROM percentage_audit_log 
            ORDER BY timestamp DESC 
            LIMIT ?
        """;
        
        List<PercentageAuditLog> logs = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get recent logs", e);
            throw new DataAccessException("Failed to get audit logs", e);
        }
        
        return logs;
    }
    
    /**
     * Delete old audit logs
     */
    public int deleteOldLogs(LocalDateTime before) throws DataAccessException {
        String sql = "DELETE FROM percentage_audit_log WHERE timestamp < ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setTimestamp(1, Timestamp.valueOf(before));
            int deleted = pstmt.executeUpdate();
            
            logger.info("Deleted {} old audit log entries before {}", deleted, before);
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to delete old logs", e);
            throw new DataAccessException("Failed to delete old logs", e);
        }
    }
    
    private PercentageAuditLog mapResultSetToLog(ResultSet rs) throws SQLException {
        PercentageAuditLog log = new PercentageAuditLog();
        log.setId(rs.getInt("id"));
        log.setEmployeeId(rs.getInt("employee_id"));
        log.setEmployeeName(rs.getString("employee_name"));
        log.setAction(rs.getString("action"));
        log.setFieldChanged(rs.getString("field_changed"));
        log.setOldValue(rs.getDouble("old_value"));
        log.setNewValue(rs.getDouble("new_value"));
        log.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        log.setPerformedBy(rs.getString("performed_by"));
        log.setNotes(rs.getString("notes"));
        log.setSessionId(rs.getString("session_id"));
        return log;
    }
}
