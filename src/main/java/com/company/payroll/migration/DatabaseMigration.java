package com.company.payroll.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class DatabaseMigration {
    
    private final Connection connection;
    
    public DatabaseMigration(Connection connection) {
        this.connection = connection;
    }
    
    public void migrate() throws SQLException {
        createMigrationTable();
        
        if (!isMigrationApplied("add_percentage_history")) {
            addPercentageHistoryTable();
            markMigrationApplied("add_percentage_history");
        }
        
        if (!isMigrationApplied("update_payroll_history")) {
            updatePayrollHistoryTable();
            markMigrationApplied("update_payroll_history");
        }
        
        if (!isMigrationApplied("add_lumper_amount_column")) {
            addLumperAmountColumn();
            markMigrationApplied("add_lumper_amount_column");
        }
    }
    
    private void createMigrationTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS migrations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                migration_name TEXT UNIQUE NOT NULL,
                applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private boolean isMigrationApplied(String migrationName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM migrations WHERE migration_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, migrationName);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }
    
    private void markMigrationApplied(String migrationName) throws SQLException {
        String sql = "INSERT INTO migrations (migration_name) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, migrationName);
            pstmt.executeUpdate();
        }
    }
    
    private void addPercentageHistoryTable() throws SQLException {
        String sql = """
            CREATE TABLE employee_percentage_history (
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
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            
            // Create index for performance
            stmt.execute("CREATE INDEX idx_emp_percentage_history_dates ON employee_percentage_history(employee_id, effective_date, end_date)");
            
            // Migrate existing percentages to history table
            String migrateSql = """
                INSERT INTO employee_percentage_history (employee_id, driver_percent, company_percent, service_fee_percent, effective_date, created_by)
                SELECT id, driver_percent, company_percent, service_fee_percent, '2024-01-01', 'MIGRATION'
                FROM employees
                WHERE driver_percent IS NOT NULL OR company_percent IS NOT NULL OR service_fee_percent IS NOT NULL
            """;
            stmt.execute(migrateSql);
        }
    }
    
    private void updatePayrollHistoryTable() throws SQLException {
        // Check if payroll_history table exists
        String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='payroll_history'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkTableSql)) {
            
            if (!rs.next()) {
                // Create payroll_history table if it doesn't exist
                String createTableSql = """
                    CREATE TABLE payroll_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        employee_id INTEGER NOT NULL,
                        payroll_date DATE NOT NULL,
                        driver_name TEXT NOT NULL,
                        truck_unit TEXT,
                        load_count INTEGER,
                        gross REAL,
                        total_deductions REAL,
                        net_pay REAL,
                        driver_percent_used REAL,
                        company_percent_used REAL,
                        service_fee_percent_used REAL,
                        locked BOOLEAN DEFAULT 0,
                        created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (employee_id) REFERENCES employees(id)
                    )
                """;
                stmt.execute(createTableSql);
                stmt.execute("CREATE INDEX idx_payroll_history_date ON payroll_history(payroll_date)");
            } else {
                // Add percentage columns if they don't exist
                try {
                    stmt.execute("ALTER TABLE payroll_history ADD COLUMN driver_percent_used REAL");
                } catch (SQLException e) {
                    // Column might already exist
                }
                
                try {
                    stmt.execute("ALTER TABLE payroll_history ADD COLUMN company_percent_used REAL");
                } catch (SQLException e) {
                    // Column might already exist
                }
                
                try {
                    stmt.execute("ALTER TABLE payroll_history ADD COLUMN service_fee_percent_used REAL");
                } catch (SQLException e) {
                    // Column might already exist
                }
            }
        }
    }
    
    private void addLumperAmountColumn() throws SQLException {
        String sql = "ALTER TABLE loads ADD COLUMN lumper_amount REAL DEFAULT 0.0";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
}