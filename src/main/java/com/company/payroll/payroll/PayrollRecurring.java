package com.company.payroll.payroll;

import com.company.payroll.employees.Employee;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple recurring fee deduction logic for PayrollTab.
 * All fees are per-driver, per-week (week_start date) records.
 */
public class PayrollRecurring {
    private static final Logger logger = LoggerFactory.getLogger(PayrollRecurring.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public static final String[] RECURRING_TYPES = {"ELD", "IFTA", "TVC", "PARKING", "PRE-PASS", "OTHER"};

    public PayrollRecurring() {
        ensureTable();
    }

    private void ensureTable() {
        logger.debug("Ensuring recurring_deductions table exists");
        String sql = """
            CREATE TABLE IF NOT EXISTS recurring_deductions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                driver_id INTEGER NOT NULL,
                week_start DATE NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                description TEXT
            );
        """;
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.debug("Recurring deductions table ready");
        } catch (SQLException e) {
            logger.error("Error ensuring recurring_deductions table: {}", e.getMessage(), e);
            System.err.println("[PayrollRecurring] Error ensuring recurring_deductions table: " + e.getMessage());
        }
    }

    public List<RecurringDeduction> getDeductionsForDriverWeek(int driverId, LocalDate weekStart) {
        logger.debug("Loading recurring deductions for driver {} week {}", driverId, weekStart);
        ensureTable();
        List<RecurringDeduction> list = new ArrayList<>();
        String sql = "SELECT * FROM recurring_deductions WHERE driver_id=? AND week_start=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            ps.setDate(2, java.sql.Date.valueOf(weekStart));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new RecurringDeduction(
                        rs.getInt("id"),
                        rs.getInt("driver_id"),
                        rs.getString("type"),
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        rs.getDate("week_start").toLocalDate()
                ));
            }
            logger.debug("Found {} recurring deductions", list.size());
        } catch (SQLException e) {
            logger.error("Error loading deductions for driver {} week {}: {}", driverId, weekStart, e.getMessage(), e);
            System.err.println("[PayrollRecurring] Error loading deductions: " + e.getMessage());
        }
        return list;
    }

    public boolean saveDeductionsForDriverWeek(int driverId, LocalDate weekStart, List<RecurringDeduction> deductions) {
        logger.info("Saving {} recurring deductions for driver {} week {}", deductions.size(), driverId, weekStart);
        ensureTable();
        // Delete old for this driver+week, then insert new
        String deleteSql = "DELETE FROM recurring_deductions WHERE driver_id=? AND week_start=?";
        String insertSql = "INSERT INTO recurring_deductions (driver_id, week_start, type, amount, description) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setInt(1, driverId);
                del.setDate(2, java.sql.Date.valueOf(weekStart));
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                for (RecurringDeduction d : deductions) {
                    ins.setInt(1, driverId);
                    ins.setDate(2, java.sql.Date.valueOf(weekStart));
                    ins.setString(3, d.type());
                    ins.setDouble(4, d.amount());
                    ins.setString(5, d.description());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
            logger.info("Successfully saved recurring deductions");
            return true;
        } catch (SQLException e) {
            logger.error("Failed to save recurring deductions for driver {} week {}: {}", driverId, weekStart, e.getMessage(), e);
            System.err.println("[PayrollRecurring] Error saving deductions: " + e.getMessage());
            return false;
        }
    }

    // Remove a recurring deduction by its unique ID (removes from DB)
    public boolean removeDeductionById(int id) {
        logger.info("Removing recurring deduction with id {}", id);
        ensureTable();
        String sql = "DELETE FROM recurring_deductions WHERE id=?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Successfully removed recurring deduction id {}", id);
                return true;
            } else {
                logger.warn("No recurring deduction found with id {}", id);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Error deleting recurring deduction (id={}): {}", id, e.getMessage(), e);
            System.err.println("[PayrollRecurring] Error deleting recurring deduction (id=" + id + "): " + e.getMessage());
            return false;
        }
    }

    public double totalDeductionsForDriverWeek(int driverId, LocalDate weekStart) {
        logger.debug("Calculating total deductions for driver {} week {}", driverId, weekStart);
        ensureTable();
        String sql = "SELECT SUM(amount) FROM recurring_deductions WHERE driver_id=? AND week_start=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            ps.setDate(2, java.sql.Date.valueOf(weekStart));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double total = rs.getDouble(1);
                logger.debug("Total recurring deductions for driver {} week {}: ${}", driverId, weekStart, total);
                return total;
            }
        } catch (SQLException e) {
            logger.error("Error summing deductions for driver {} week {}: {}", driverId, weekStart, e.getMessage(), e);
            System.err.println("[PayrollRecurring] Error summing deductions: " + e.getMessage());
        }
        return 0.0;
    }

    // Charge all recurring fees for a driver+week, but don't allow duplicate charges
    public boolean chargeAllRecurringFees(int driverId, LocalDate weekStart, List<RecurringDeduction> toCharge) {
        logger.info("Charging {} recurring fees for driver {} week {}", toCharge.size(), driverId, weekStart);
        ensureTable();
        // Check for duplicates
        List<RecurringDeduction> existing = getDeductionsForDriverWeek(driverId, weekStart);
        for (RecurringDeduction charge : toCharge) {
            for (RecurringDeduction ex : existing) {
                if (ex.type().equalsIgnoreCase(charge.type())) {
                    // already charged this type for this driver+week
                    logger.warn("Duplicate charge detected: {} already charged for driver {} week {}", charge.type(), driverId, weekStart);
                    return false;
                }
            }
        }
        List<RecurringDeduction> merged = new ArrayList<>(existing);
        merged.addAll(toCharge);
        boolean success = saveDeductionsForDriverWeek(driverId, weekStart, merged);
        if (success) {
            logger.info("Successfully charged all recurring fees");
        }
        return success;
    }

    public static record RecurringDeduction(int id, int driverId, String type, double amount, String description, LocalDate weekStart) { }
}