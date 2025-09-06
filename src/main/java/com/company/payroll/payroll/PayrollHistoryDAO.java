package com.company.payroll.payroll;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing payroll history records
 */
public class PayrollHistoryDAO {
    private static final Logger logger = LoggerFactory.getLogger(PayrollHistoryDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    
    private final Connection connection;
    
    public PayrollHistoryDAO() {
        this(null);
    }
    
    public PayrollHistoryDAO(Connection connection) {
        this.connection = connection;
    }
    
    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        return DatabaseConfig.getConnection();
    }
    
    /**
     * Save payroll history for a specific week
     */
    public void savePayrollHistory(List<PayrollCalculator.PayrollRow> payrollRows, LocalDate weekStart, boolean locked) throws DataAccessException {
        String sql = """
            INSERT INTO payroll_history 
            (employee_id, payroll_date, driver_name, truck_unit, load_count, gross, 
             total_deductions, net_pay, driver_percent_used, company_percent_used, 
             service_fee_percent_used, locked)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            for (PayrollCalculator.PayrollRow row : payrollRows) {
                pstmt.setInt(1, row.driverId);
                pstmt.setDate(2, Date.valueOf(weekStart));
                pstmt.setString(3, row.driverName);
                pstmt.setString(4, row.truckUnit);
                pstmt.setInt(5, row.loadCount);
                pstmt.setDouble(6, row.gross);
                pstmt.setDouble(7, row.getTotalDeductions());
                pstmt.setDouble(8, row.netPay);
                pstmt.setDouble(9, row.driverPercent);
                pstmt.setDouble(10, row.companyPercent);
                pstmt.setDouble(11, row.serviceFeePercent);
                pstmt.setBoolean(12, locked);
                
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
            
            logger.info("Saved payroll history for {} drivers for week starting {}", payrollRows.size(), weekStart);
            
        } catch (SQLException e) {
            logger.error("Failed to save payroll history", e);
            throw new DataAccessException("Failed to save payroll history", e);
        }
    }
    
    /**
     * Delete payroll history for a specific week
     */
    public void deletePayrollHistory(LocalDate weekStart) throws DataAccessException {
        String sql = "DELETE FROM payroll_history WHERE payroll_date = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(weekStart));
            int deleted = pstmt.executeUpdate();
            
            logger.info("Deleted {} payroll history records for week starting {}", deleted, weekStart);
            
        } catch (SQLException e) {
            logger.error("Failed to delete payroll history", e);
            throw new DataAccessException("Failed to delete payroll history", e);
        }
    }
    
    /**
     * Update lock status for a specific week
     */
    public void updateLockStatus(LocalDate weekStart, boolean locked) throws DataAccessException {
        String sql = "UPDATE payroll_history SET locked = ? WHERE payroll_date = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setBoolean(1, locked);
            pstmt.setDate(2, Date.valueOf(weekStart));
            int updated = pstmt.executeUpdate();
            
            logger.info("Updated lock status to {} for {} records for week starting {}", 
                locked, updated, weekStart);
            
        } catch (SQLException e) {
            logger.error("Failed to update lock status", e);
            throw new DataAccessException("Failed to update lock status", e);
        }
    }
    
    /**
     * Get payroll history for a specific week
     */
    public List<PayrollHistoryEntry> getPayrollHistory(LocalDate weekStart) throws DataAccessException {
        String sql = """
            SELECT * FROM payroll_history 
            WHERE payroll_date = ? 
            ORDER BY driver_name
        """;
        
        List<PayrollHistoryEntry> history = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(weekStart));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapResultSetToHistoryEntry(rs));
                }
            }
            
            logger.debug("Retrieved {} payroll history records for week starting {}", 
                history.size(), weekStart);
            
        } catch (SQLException e) {
            logger.error("Failed to get payroll history", e);
            throw new DataAccessException("Failed to get payroll history", e);
        }
        
        return history;
    }
    
    /**
     * Get payroll history for a date range
     */
    public List<PayrollHistoryEntry> getPayrollHistoryRange(LocalDate startDate, LocalDate endDate) throws DataAccessException {
        String sql = """
            SELECT * FROM payroll_history 
            WHERE payroll_date >= ? AND payroll_date <= ?
            ORDER BY payroll_date DESC, driver_name
        """;
        
        List<PayrollHistoryEntry> history = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapResultSetToHistoryEntry(rs));
                }
            }
            
            logger.debug("Retrieved {} payroll history records for range {} to {}", 
                history.size(), startDate, endDate);
            
        } catch (SQLException e) {
            logger.error("Failed to get payroll history range", e);
            throw new DataAccessException("Failed to get payroll history range", e);
        }
        
        return history;
    }
    
    /**
     * Check if a week is locked
     */
    public boolean isWeekLocked(LocalDate weekStart) throws DataAccessException {
        String sql = "SELECT locked FROM payroll_history WHERE payroll_date = ? LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(weekStart));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("locked");
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to check if week is locked", e);
            throw new DataAccessException("Failed to check if week is locked", e);
        }
        
        return false;
    }
    
    /**
     * Get all locked week dates
     */
    public List<LocalDate> getLockedWeeks() throws DataAccessException {
        String sql = "SELECT DISTINCT payroll_date FROM payroll_history WHERE locked = 1 ORDER BY payroll_date DESC";
        
        List<LocalDate> lockedWeeks = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                lockedWeeks.add(rs.getDate("payroll_date").toLocalDate());
            }
            
            logger.debug("Retrieved {} locked weeks", lockedWeeks.size());
            
        } catch (SQLException e) {
            logger.error("Failed to get locked weeks", e);
            throw new DataAccessException("Failed to get locked weeks", e);
        }
        
        return lockedWeeks;
    }
    
    private PayrollHistoryEntry mapResultSetToHistoryEntry(ResultSet rs) throws SQLException {
        return new PayrollHistoryEntry(
            rs.getDate("payroll_date").toLocalDate(),
            rs.getString("driver_name"),
            rs.getString("truck_unit"),
            rs.getInt("load_count"),
            rs.getDouble("gross"),
            rs.getDouble("total_deductions"),
            rs.getDouble("net_pay"),
            rs.getBoolean("locked"),
            rs.getDouble("driver_percent_used"),
            rs.getDouble("company_percent_used"),
            rs.getDouble("service_fee_percent_used")
        );
    }
}