package com.company.payroll.database;

import java.sql.Connection;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to create all payment method related tables
 */
public class CreateAllPaymentTables {
    private static final Logger logger = LoggerFactory.getLogger(CreateAllPaymentTables.class);
    
    public static void main(String[] args) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            createPaymentMethodHistoryTable(conn);
            addFlatRateToLoadsTable(conn);
            logger.info("All payment tables created/updated successfully!");
        } catch (Exception e) {
            logger.error("Failed to create payment tables", e);
        }
    }
    
    private static void createPaymentMethodHistoryTable(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Check if table exists
            boolean tableExists = false;
            try {
                stmt.executeQuery("SELECT 1 FROM employee_payment_method_history LIMIT 1");
                tableExists = true;
                logger.info("employee_payment_method_history table already exists");
            } catch (Exception e) {
                // Table doesn't exist, proceed with creation
            }
            
            if (!tableExists) {
                logger.info("Creating employee_payment_method_history table...");
                String createTableSql = """
                    CREATE TABLE employee_payment_method_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        employee_id INTEGER NOT NULL,
                        payment_type TEXT NOT NULL,
                        driver_percent REAL DEFAULT 0.0,
                        company_percent REAL DEFAULT 0.0,
                        service_fee_percent REAL DEFAULT 0.0,
                        flat_rate_amount REAL DEFAULT 0.0,
                        per_mile_rate REAL DEFAULT 0.0,
                        effective_date DATE NOT NULL,
                        end_date DATE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        created_by TEXT,
                        notes TEXT,
                        FOREIGN KEY(employee_id) REFERENCES employees(id)
                    )
                """;
                stmt.execute(createTableSql);
                
                // Create indexes
                stmt.execute("CREATE INDEX idx_payment_history_employee ON employee_payment_method_history(employee_id)");
                stmt.execute("CREATE INDEX idx_payment_history_dates ON employee_payment_method_history(effective_date, end_date)");
                stmt.execute("CREATE INDEX idx_payment_history_type ON employee_payment_method_history(payment_type)");
                
                logger.info("employee_payment_method_history table created successfully");
            }
        }
    }
    
    private static void addFlatRateToLoadsTable(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Check if column already exists
            boolean columnExists = false;
            try {
                stmt.executeQuery("SELECT flat_rate_amount FROM loads LIMIT 1");
                columnExists = true;
                logger.info("flat_rate_amount column already exists in loads table");
            } catch (Exception e) {
                // Column doesn't exist, proceed with addition
            }
            
            if (!columnExists) {
                logger.info("Adding flat_rate_amount column to loads table...");
                stmt.execute("ALTER TABLE loads ADD COLUMN flat_rate_amount DOUBLE DEFAULT 0.0");
                
                // Update existing loads with flat rate payment method to use the driver's default flat rate
                String updateSql = """
                    UPDATE loads l
                    SET flat_rate_amount = (
                        SELECT e.flat_rate_amount 
                        FROM employees e 
                        WHERE e.id = l.driver_id
                    )
                    WHERE l.payment_method_used = 'FLAT_RATE' 
                      AND l.flat_rate_amount = 0.0
                """;
                int updated = stmt.executeUpdate(updateSql);
                logger.info("Updated {} loads with driver default flat rates", updated);
                
                // Add index for performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_loads_flat_rate ON loads(flat_rate_amount)");
                logger.info("Added index on flat_rate_amount");
            }
        }
    }
}
