package com.company.payroll.employees;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EmployeePercentageHistoryDAO {
    
    private final Connection connection;
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    
    public EmployeePercentageHistoryDAO(Connection connection) {
        this.connection = connection;
        initializeTable();
    }
    
    // Ensure we have a valid connection
    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        // Create a new connection if the provided one is closed
        return DatabaseConfig.getConnection();
    }
    
    private void initializeTable() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS employee_percentage_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employee_id INTEGER NOT NULL,
                driver_percent REAL NOT NULL,
                company_percent REAL NOT NULL,
                service_fee_percent REAL NOT NULL,
                effective_date DATE NOT NULL,
                end_date DATE,
                created_by TEXT,
                created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                notes TEXT,
                FOREIGN KEY (employee_id) REFERENCES employees(id)
            )
        """;
        
        Connection conn = null;
        boolean shouldClose = false;
        try {
            conn = getConnection();
            // Check if we created a new connection
            shouldClose = (conn != connection);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                
                // Create index for performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_emp_percentage_history_dates ON employee_percentage_history(employee_id, effective_date, end_date)");
            }
        } catch (SQLException e) {
            // Don't fail the operation - log and continue
            System.err.println("Warning: Failed to initialize employee_percentage_history table: " + e.getMessage());
        } finally {
            // Only close if we created a new connection
            if (shouldClose && conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }
    
    public void createPercentageHistory(EmployeePercentageHistory history) throws DataAccessException {
        // Ensure table exists first
        initializeTable();
        
        String sql = """
            INSERT INTO employee_percentage_history 
            (employee_id, driver_percent, company_percent, service_fee_percent, 
             effective_date, end_date, created_by, created_date, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        Connection conn = null;
        boolean shouldClose = false;
        
        try {
            conn = getConnection();
            shouldClose = (conn != connection);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, history.getEmployeeId());
                pstmt.setDouble(2, history.getDriverPercent());
                pstmt.setDouble(3, history.getCompanyPercent());
                pstmt.setDouble(4, history.getServiceFeePercent());
                pstmt.setDate(5, Date.valueOf(history.getEffectiveDate()));
                pstmt.setDate(6, history.getEndDate() != null ? Date.valueOf(history.getEndDate()) : null);
                pstmt.setString(7, history.getCreatedBy());
                pstmt.setTimestamp(8, Timestamp.valueOf(history.getCreatedDate() != null ? 
                    history.getCreatedDate() : LocalDateTime.now()));
                pstmt.setString(9, history.getNotes());
                
                pstmt.executeUpdate();
                
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        history.setId(rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error creating percentage history: " + e.getMessage());
            e.printStackTrace();
            throw new DataAccessException("Failed to create percentage history: " + e.getMessage(), e);
        } finally {
            if (shouldClose && conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }
    
    public void updatePercentageHistory(EmployeePercentageHistory history) throws DataAccessException {
        String sql = """
            UPDATE employee_percentage_history 
            SET driver_percent = ?, company_percent = ?, service_fee_percent = ?,
                effective_date = ?, end_date = ?, notes = ?
            WHERE id = ?
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, history.getDriverPercent());
            pstmt.setDouble(2, history.getCompanyPercent());
            pstmt.setDouble(3, history.getServiceFeePercent());
            pstmt.setDate(4, Date.valueOf(history.getEffectiveDate()));
            pstmt.setDate(5, history.getEndDate() != null ? Date.valueOf(history.getEndDate()) : null);
            pstmt.setString(6, history.getNotes());
            pstmt.setInt(7, history.getId());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update percentage history", e);
        }
    }
    
    public EmployeePercentageHistory getEffectivePercentages(int employeeId, LocalDate date) throws DataAccessException {
        String sql = """
            SELECT * FROM employee_percentage_history
            WHERE employee_id = ?
            AND effective_date <= ?
            AND (end_date IS NULL OR end_date >= ?)
            ORDER BY effective_date DESC
            LIMIT 1
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, employeeId);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setDate(3, Date.valueOf(date));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHistory(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get effective percentages", e);
        }
    }
    
    public List<EmployeePercentageHistory> getHistoryForEmployee(int employeeId) throws DataAccessException {
        String sql = """
            SELECT * FROM employee_percentage_history
            WHERE employee_id = ?
            ORDER BY effective_date DESC
        """;
        
        List<EmployeePercentageHistory> history = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, employeeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapResultSetToHistory(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get employee percentage history", e);
        }
        
        return history;
    }
    
    public void createBulkPercentageHistory(List<EmployeePercentageHistory> histories) throws DataAccessException {
        String sql = """
            INSERT INTO employee_percentage_history 
            (employee_id, driver_percent, company_percent, service_fee_percent, 
             effective_date, end_date, created_by, created_date, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            
            for (EmployeePercentageHistory history : histories) {
                pstmt.setInt(1, history.getEmployeeId());
                pstmt.setDouble(2, history.getDriverPercent());
                pstmt.setDouble(3, history.getCompanyPercent());
                pstmt.setDouble(4, history.getServiceFeePercent());
                pstmt.setDate(5, Date.valueOf(history.getEffectiveDate()));
                pstmt.setDate(6, history.getEndDate() != null ? Date.valueOf(history.getEndDate()) : null);
                pstmt.setString(7, history.getCreatedBy());
                pstmt.setTimestamp(8, Timestamp.valueOf(history.getCreatedDate() != null ? 
                    history.getCreatedDate() : LocalDateTime.now()));
                pstmt.setString(9, history.getNotes());
                
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackEx) {
                // Log rollback failure
            }
            throw new DataAccessException("Failed to create bulk percentage history", e);
        }
    }
    
    public void closeCurrentPercentages(int employeeId, LocalDate endDate) {
        // First ensure the table exists
        initializeTable();
        
        String sql = """
            UPDATE employee_percentage_history 
            SET end_date = ?
            WHERE employee_id = ? 
            AND end_date IS NULL
            AND effective_date < ?
        """;
        
        Connection conn = null;
        boolean shouldClose = false;
        
        try {
            conn = getConnection();
            shouldClose = (conn != connection);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDate(1, Date.valueOf(endDate.minusDays(1)));
                pstmt.setInt(2, employeeId);
                pstmt.setDate(3, Date.valueOf(endDate));
                
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            // Log but don't throw - allow the operation to continue
            System.err.println("Warning: Could not close current percentages for employee " + employeeId + ": " + e.getMessage());
            // This is likely because there are no existing records to close, which is fine
        } finally {
            if (shouldClose && conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }
    
    public List<EmployeePercentageHistory> getFuturePercentages(int employeeId, LocalDate fromDate) throws DataAccessException {
        String sql = """
            SELECT * FROM employee_percentage_history
            WHERE employee_id = ?
            AND effective_date > ?
            ORDER BY effective_date ASC
        """;
        
        List<EmployeePercentageHistory> history = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, employeeId);
            pstmt.setDate(2, Date.valueOf(fromDate));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapResultSetToHistory(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get future percentages", e);
        }
        
        return history;
    }
    
    private EmployeePercentageHistory mapResultSetToHistory(ResultSet rs) throws SQLException {
        EmployeePercentageHistory history = new EmployeePercentageHistory();
        history.setId(rs.getInt("id"));
        history.setEmployeeId(rs.getInt("employee_id"));
        history.setDriverPercent(rs.getDouble("driver_percent"));
        history.setCompanyPercent(rs.getDouble("company_percent"));
        history.setServiceFeePercent(rs.getDouble("service_fee_percent"));
        history.setEffectiveDate(rs.getDate("effective_date").toLocalDate());
        
        Date endDate = rs.getDate("end_date");
        if (endDate != null) {
            history.setEndDate(endDate.toLocalDate());
        }
        
        history.setCreatedBy(rs.getString("created_by"));
        Timestamp createdDate = rs.getTimestamp("created_date");
        if (createdDate != null) {
            history.setCreatedDate(createdDate.toLocalDateTime());
        }
        history.setNotes(rs.getString("notes"));
        
        return history;
    }
}