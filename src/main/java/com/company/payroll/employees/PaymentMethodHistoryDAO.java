package com.company.payroll.employees;

import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for payment method history operations.
 * Manages payment method configurations over time with full audit trail.
 */
public class PaymentMethodHistoryDAO {
    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodHistoryDAO.class);
    private final Connection connection;
    
    public PaymentMethodHistoryDAO(Connection connection) {
        this.connection = connection;
    }
    
    /**
     * Get the effective payment method for an employee on a specific date.
     * @param employeeId The employee ID
     * @param effectiveDate The date to check
     * @return The payment method history entry or null if none found
     */
    public PaymentMethodHistory getEffectivePaymentMethod(int employeeId, LocalDate effectiveDate) {
        String sql = "SELECT * FROM employee_payment_method_history " +
                    "WHERE employee_id = ? " +
                    "AND effective_date <= ? " +
                    "AND (end_date IS NULL OR end_date >= ?) " +
                    "ORDER BY effective_date DESC " +
                    "LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            stmt.setDate(2, Date.valueOf(effectiveDate));
            stmt.setDate(3, Date.valueOf(effectiveDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPaymentMethodHistory(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting effective payment method for employee {} on date {}", 
                        employeeId, effectiveDate, e);
        }
        
        return null;
    }
    
    /**
     * Create a new payment method history entry.
     * Automatically closes any existing active configuration.
     * @param history The payment method history to create
     * @return true if successful
     */
    public boolean createPaymentMethodHistory(PaymentMethodHistory history) {
        if (!history.isValid()) {
            logger.error("Invalid payment method history: {}", history.getValidationError());
            return false;
        }
        
        Connection conn = null;
        boolean originalAutoCommit = false;
        
        try {
            conn = connection;
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            // First, close any existing active configuration
            if (!closeCurrentPaymentMethod(history.getEmployeeId(), 
                                          history.getEffectiveDate().minusDays(1), conn)) {
                conn.rollback();
                return false;
            }
            
            // Then insert the new configuration
            String sql = "INSERT INTO employee_payment_method_history " +
                        "(employee_id, payment_type, driver_percent, company_percent, service_fee_percent, " +
                        "flat_rate_amount, per_mile_rate, effective_date, end_date, " +
                        "created_by, created_date, modified_date, notes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, history.getEmployeeId());
                stmt.setString(2, history.getPaymentType().name());
                stmt.setDouble(3, history.getDriverPercent());
                stmt.setDouble(4, history.getCompanyPercent());
                stmt.setDouble(5, history.getServiceFeePercent());
                stmt.setDouble(6, history.getFlatRateAmount());
                stmt.setDouble(7, history.getPerMileRate());
                stmt.setDate(8, Date.valueOf(history.getEffectiveDate()));
                stmt.setDate(9, history.getEndDate() != null ? Date.valueOf(history.getEndDate()) : null);
                stmt.setString(10, history.getCreatedBy() != null ? history.getCreatedBy() : "SYSTEM");
                stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setString(13, history.getNotes());
                
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            history.setId(generatedKeys.getInt(1));
                        }
                    }
                    
                    // Update employee's current payment method
                    if (!updateEmployeePaymentMethod(history.getEmployeeId(), history, conn)) {
                        conn.rollback();
                        return false;
                    }
                    
                    conn.commit();
                    logger.info("Created payment method history for employee {}: {}", 
                               history.getEmployeeId(), history.getDescription());
                    return true;
                }
            }
            
            conn.rollback();
            return false;
            
        } catch (SQLException e) {
            logger.error("Error creating payment method history", e);
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                logger.error("Error rolling back transaction", ex);
            }
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                logger.error("Error resetting auto-commit", e);
            }
        }
    }
    
    /**
     * Close the current active payment method configuration.
     * @param employeeId The employee ID
     * @param endDate The end date for the configuration
     * @return true if successful
     */
    public boolean closeCurrentPaymentMethod(int employeeId, LocalDate endDate) {
        return closeCurrentPaymentMethod(employeeId, endDate, connection);
    }
    
    private boolean closeCurrentPaymentMethod(int employeeId, LocalDate endDate, Connection conn) {
        String sql = "UPDATE employee_payment_method_history " +
                    "SET end_date = ?, modified_date = ? " +
                    "WHERE employee_id = ? AND end_date IS NULL";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(endDate));
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(3, employeeId);
            
            int rowsAffected = stmt.executeUpdate();
            logger.info("Closed {} active payment method configuration(s) for employee {}", 
                       rowsAffected, employeeId);
            return true;
        } catch (SQLException e) {
            logger.error("Error closing current payment method for employee {}", employeeId, e);
            return false;
        }
    }
    
    /**
     * Update employee's current payment method based on history.
     */
    private boolean updateEmployeePaymentMethod(int employeeId, PaymentMethodHistory history, Connection conn) {
        String sql = "UPDATE employees SET " +
                    "payment_type = ?, flat_rate_amount = ?, per_mile_rate = ?, " +
                    "payment_effective_date = ?, payment_notes = ? " +
                    "WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, history.getPaymentType().name());
            stmt.setDouble(2, history.getFlatRateAmount());
            stmt.setDouble(3, history.getPerMileRate());
            stmt.setDate(4, Date.valueOf(history.getEffectiveDate()));
            stmt.setString(5, history.getNotes());
            stmt.setInt(6, employeeId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.error("Error updating employee payment method", e);
            return false;
        }
    }
    
    /**
     * Get all payment method history for an employee.
     * @param employeeId The employee ID
     * @return List of payment method history entries
     */
    public List<PaymentMethodHistory> getHistoryForEmployee(int employeeId) {
        List<PaymentMethodHistory> history = new ArrayList<>();
        String sql = "SELECT * FROM employee_payment_method_history " +
                    "WHERE employee_id = ? " +
                    "ORDER BY effective_date DESC";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapResultSetToPaymentMethodHistory(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting payment method history for employee {}", employeeId, e);
        }
        
        return history;
    }
    
    /**
     * Get active payment methods for all employees on a specific date.
     * @param effectiveDate The date to check
     * @return List of active payment method configurations
     */
    public List<PaymentMethodHistory> getActivePaymentMethods(LocalDate effectiveDate) {
        List<PaymentMethodHistory> methods = new ArrayList<>();
        String sql = "SELECT h.*, e.first_name || ' ' || e.last_name as employee_name " +
                    "FROM employee_payment_method_history h " +
                    "JOIN employees e ON h.employee_id = e.id " +
                    "WHERE h.effective_date <= ? " +
                    "AND (h.end_date IS NULL OR h.end_date >= ?) " +
                    "AND e.active = 1 " +
                    "ORDER BY e.first_name, e.last_name";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(effectiveDate));
            stmt.setDate(2, Date.valueOf(effectiveDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PaymentMethodHistory history = mapResultSetToPaymentMethodHistory(rs);
                    history.setEmployeeName(rs.getString("employee_name"));
                    methods.add(history);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting active payment methods for date {}", effectiveDate, e);
        }
        
        return methods;
    }
    
    /**
     * Update an existing payment method history entry.
     * @param history The payment method history to update
     * @return true if successful
     */
    public boolean updatePaymentMethodHistory(PaymentMethodHistory history) {
        if (!history.isValid()) {
            logger.error("Invalid payment method history: {}", history.getValidationError());
            return false;
        }
        
        String sql = "UPDATE employee_payment_method_history SET " +
                    "payment_type = ?, driver_percent = ?, company_percent = ?, " +
                    "service_fee_percent = ?, flat_rate_amount = ?, per_mile_rate = ?, " +
                    "effective_date = ?, end_date = ?, modified_date = ?, notes = ? " +
                    "WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, history.getPaymentType().name());
            stmt.setDouble(2, history.getDriverPercent());
            stmt.setDouble(3, history.getCompanyPercent());
            stmt.setDouble(4, history.getServiceFeePercent());
            stmt.setDouble(5, history.getFlatRateAmount());
            stmt.setDouble(6, history.getPerMileRate());
            stmt.setDate(7, Date.valueOf(history.getEffectiveDate()));
            stmt.setDate(8, history.getEndDate() != null ? Date.valueOf(history.getEndDate()) : null);
            stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(10, history.getNotes());
            stmt.setInt(11, history.getId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Updated payment method history {}: {}", 
                           history.getId(), history.getDescription());
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error updating payment method history", e);
        }
        
        return false;
    }
    
    /**
     * Delete a payment method history entry.
     * @param historyId The history ID to delete
     * @return true if successful
     */
    public boolean deletePaymentMethodHistory(int historyId) {
        String sql = "DELETE FROM employee_payment_method_history WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, historyId);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Deleted payment method history {}", historyId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error deleting payment method history {}", historyId, e);
        }
        
        return false;
    }
    
    /**
     * Check if there are any overlapping date ranges for an employee.
     * @param employeeId The employee ID
     * @param effectiveDate The effective date to check
     * @param endDate The end date to check (can be null)
     * @param excludeId The history ID to exclude from check (for updates)
     * @return true if there are overlaps
     */
    public boolean hasOverlappingDateRanges(int employeeId, LocalDate effectiveDate, 
                                          LocalDate endDate, Integer excludeId) {
        String sql = "SELECT COUNT(*) FROM employee_payment_method_history " +
                    "WHERE employee_id = ? " +
                    "AND id != ? " +
                    "AND ((? >= effective_date AND (? <= end_date OR end_date IS NULL)) " +
                    "OR (? IS NOT NULL AND ? >= effective_date AND (? <= end_date OR end_date IS NULL)) " +
                    "OR (? <= effective_date AND (? >= end_date OR ? IS NULL OR end_date IS NULL)))";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            stmt.setInt(2, excludeId != null ? excludeId : -1);
            stmt.setDate(3, Date.valueOf(effectiveDate));
            stmt.setDate(4, Date.valueOf(effectiveDate));
            stmt.setDate(5, endDate != null ? Date.valueOf(endDate) : null);
            stmt.setDate(6, endDate != null ? Date.valueOf(endDate) : null);
            stmt.setDate(7, endDate != null ? Date.valueOf(endDate) : null);
            stmt.setDate(8, Date.valueOf(effectiveDate));
            stmt.setDate(9, endDate != null ? Date.valueOf(endDate) : null);
            stmt.setDate(10, endDate != null ? Date.valueOf(endDate) : null);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking for overlapping date ranges", e);
        }
        
        return false;
    }
    
    /**
     * Map ResultSet to PaymentMethodHistory object.
     */
    private PaymentMethodHistory mapResultSetToPaymentMethodHistory(ResultSet rs) throws SQLException {
        PaymentMethodHistory history = new PaymentMethodHistory();
        
        history.setId(rs.getInt("id"));
        history.setEmployeeId(rs.getInt("employee_id"));
        
        String paymentTypeStr = rs.getString("payment_type");
        if (paymentTypeStr != null) {
            history.setPaymentType(PaymentType.valueOf(paymentTypeStr));
        }
        
        history.setDriverPercent(rs.getDouble("driver_percent"));
        history.setCompanyPercent(rs.getDouble("company_percent"));
        history.setServiceFeePercent(rs.getDouble("service_fee_percent"));
        history.setFlatRateAmount(rs.getDouble("flat_rate_amount"));
        history.setPerMileRate(rs.getDouble("per_mile_rate"));
        
        Date effectiveDate = rs.getDate("effective_date");
        if (effectiveDate != null) {
            history.setEffectiveDate(effectiveDate.toLocalDate());
        }
        
        Date endDate = rs.getDate("end_date");
        if (endDate != null) {
            history.setEndDate(endDate.toLocalDate());
        }
        
        history.setCreatedBy(rs.getString("created_by"));
        
        Timestamp createdDate = rs.getTimestamp("created_date");
        if (createdDate != null) {
            history.setCreatedDate(createdDate.toLocalDateTime());
        }
        
        Timestamp modifiedDate = rs.getTimestamp("modified_date");
        if (modifiedDate != null) {
            history.setModifiedDate(modifiedDate.toLocalDateTime());
        }
        
        history.setNotes(rs.getString("notes"));
        
        return history;
    }
}
