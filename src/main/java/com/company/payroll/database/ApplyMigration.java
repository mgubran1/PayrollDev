package com.company.payroll.database;

import java.sql.Connection;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to apply database migrations
 */
public class ApplyMigration {
    private static final Logger logger = LoggerFactory.getLogger(ApplyMigration.class);
    
    public static void main(String[] args) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Apply flat rate migration
            applyFlatRateMigration(conn);
            logger.info("Migration applied successfully!");
        } catch (Exception e) {
            logger.error("Failed to apply migration", e);
        }
    }
    
    private static void applyFlatRateMigration(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Check if column already exists
            boolean columnExists = false;
            try {
                stmt.executeQuery("SELECT flat_rate_amount FROM loads LIMIT 1");
                columnExists = true;
                logger.info("flat_rate_amount column already exists");
            } catch (Exception e) {
                // Column doesn't exist, proceed with migration
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
