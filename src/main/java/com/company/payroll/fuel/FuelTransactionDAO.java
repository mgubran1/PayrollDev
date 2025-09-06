package com.company.payroll.fuel;

import com.company.payroll.exception.DataAccessException;
import com.company.payroll.database.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FuelTransactionDAO {
    private static final Logger logger = LoggerFactory.getLogger(FuelTransactionDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public FuelTransactionDAO() {
        logger.debug("Initializing FuelTransactionDAO");
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS fuel_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    card_number TEXT,
                    tran_date TEXT,
                    tran_time TEXT,
                    invoice TEXT,
                    unit TEXT,
                    driver_name TEXT,
                    odometer TEXT,
                    location_name TEXT,
                    city TEXT,
                    state_prov TEXT,
                    fees REAL,
                    item TEXT,
                    unit_price REAL,
                    disc_ppu REAL,
                    disc_cost REAL,
                    qty REAL,
                    disc_amt REAL,
                    disc_type TEXT,
                    amt REAL,
                    db TEXT,
                    currency TEXT,
                    employee_id INTEGER,
                    UNIQUE(invoice, tran_date, location_name, amt)
                );
            """;
            conn.createStatement().execute(sql);
            logger.info("Fuel transactions table initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize FuelTransactionDAO: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to initialize FuelTransactionDAO", e);
        }
    }

    public List<FuelTransaction> getAll() {
        logger.debug("Fetching all fuel transactions");
        List<FuelTransaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql = "SELECT * FROM fuel_transactions";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                FuelTransaction t = mapRow(rs);
                list.add(t);
            }
            logger.info("Retrieved {} fuel transactions", list.size());
        } catch (SQLException e) {
            logger.error("Error getting all fuel transactions: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting all fuel transactions", e);
        }
        return list;
    }

    public int add(FuelTransaction t) {
        logger.info("Adding fuel transaction - Invoice: {}, Driver: {}, Amount: ${}", 
            t.getInvoice(), t.getDriverName(), t.getAmt());
        String sql = """
        INSERT INTO fuel_transactions (
            card_number, tran_date, tran_time, invoice, unit, driver_name, odometer, location_name, city,
            state_prov, fees, item, unit_price, disc_ppu, disc_cost, qty, disc_amt, disc_type, amt, db, currency, employee_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, t.getCardNumber());
            ps.setString(2, t.getTranDate());
            ps.setString(3, t.getTranTime());
            ps.setString(4, t.getInvoice());
            ps.setString(5, t.getUnit());
            ps.setString(6, t.getDriverName());
            ps.setString(7, t.getOdometer());
            ps.setString(8, t.getLocationName());
            ps.setString(9, t.getCity());
            ps.setString(10, t.getStateProv());
            ps.setDouble(11, t.getFees());
            ps.setString(12, t.getItem());
            ps.setDouble(13, t.getUnitPrice());
            ps.setDouble(14, t.getDiscPPU());
            ps.setDouble(15, t.getDiscCost());
            ps.setDouble(16, t.getQty());
            ps.setDouble(17, t.getDiscAmt());
            ps.setString(18, t.getDiscType());
            ps.setDouble(19, t.getAmt());
            ps.setString(20, t.getDb());
            ps.setString(21, t.getCurrency());
            ps.setObject(22, t.getEmployeeId());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                logger.info("Fuel transaction added successfully with ID: {}", id);
                return id;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                logger.warn("Duplicate fuel transaction detected - Invoice: {}, Date: {}, Location: {}", 
                    t.getInvoice(), t.getTranDate(), t.getLocationName());
                return -1; // Duplicate!
            }
            logger.error("Error adding fuel transaction: {}", e.getMessage(), e);
            throw new DataAccessException("Error adding fuel transaction", e);
        }
        return -1;
    }

    public boolean exists(String invoice, String tranDate, String locationName, double amt) {
        logger.debug("Checking existence - Invoice: {}, Date: {}, Location: {}, Amount: ${}", 
            invoice, tranDate, locationName, amt);
        String sql = """
            SELECT COUNT(*) FROM fuel_transactions
            WHERE LOWER(TRIM(invoice)) = ? AND
                  LOWER(TRIM(tran_date)) = ? AND
                  LOWER(TRIM(location_name)) = ? AND
                  ROUND(amt, 2) = ROUND(?, 2)
        """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, invoice.trim().toLowerCase());
            ps.setString(2, tranDate.trim().toLowerCase());
            ps.setString(3, locationName.trim().toLowerCase());
            ps.setDouble(4, amt);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next() && rs.getInt(1) > 0;
            logger.debug("Transaction exists: {}", exists);
            return exists;
        } catch (SQLException e) {
            logger.error("Error checking fuel transaction existence: {}", e.getMessage(), e);
            throw new DataAccessException("Error checking fuel transaction existence", e);
        }
    }

    public void update(FuelTransaction t) {
        logger.info("Updating fuel transaction - ID: {}, Invoice: {}", t.getId(), t.getInvoice());
        String sql = """
            UPDATE fuel_transactions SET
                card_number=?, tran_date=?, tran_time=?, invoice=?, unit=?, driver_name=?, odometer=?, location_name=?,
                city=?, state_prov=?, fees=?, item=?, unit_price=?, disc_ppu=?, disc_cost=?, qty=?, disc_amt=?,
                disc_type=?, amt=?, db=?, currency=?, employee_id=?
            WHERE id=?
        """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, t.getCardNumber());
            ps.setString(2, t.getTranDate());
            ps.setString(3, t.getTranTime());
            ps.setString(4, t.getInvoice());
            ps.setString(5, t.getUnit());
            ps.setString(6, t.getDriverName());
            ps.setString(7, t.getOdometer());
            ps.setString(8, t.getLocationName());
            ps.setString(9, t.getCity());
            ps.setString(10, t.getStateProv());
            ps.setDouble(11, t.getFees());
            ps.setString(12, t.getItem());
            ps.setDouble(13, t.getUnitPrice());
            ps.setDouble(14, t.getDiscPPU());
            ps.setDouble(15, t.getDiscCost());
            ps.setDouble(16, t.getQty());
            ps.setDouble(17, t.getDiscAmt());
            ps.setString(18, t.getDiscType());
            ps.setDouble(19, t.getAmt());
            ps.setString(20, t.getDb());
            ps.setString(21, t.getCurrency());
            ps.setObject(22, t.getEmployeeId());
            ps.setInt(23, t.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Fuel transaction updated successfully");
            } else {
                logger.warn("No fuel transaction found with ID: {}", t.getId());
            }
        } catch (SQLException e) {
            logger.error("Error updating fuel transaction: {}", e.getMessage(), e);
            throw new DataAccessException("Error updating fuel transaction", e);
        }
    }

    public void delete(int id) {
        logger.info("Deleting fuel transaction with ID: {}", id);
        String sql = "DELETE FROM fuel_transactions WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Fuel transaction deleted successfully");
            } else {
                logger.warn("No fuel transaction found with ID: {}", id);
            }
        } catch (SQLException e) {
            logger.error("Error deleting fuel transaction: {}", e.getMessage(), e);
            throw new DataAccessException("Error deleting fuel transaction", e);
        }
    }

    public List<FuelTransaction> getByDateRange(LocalDate start, LocalDate end) {
        logger.debug("Getting fuel transactions by date range - Start: {}, End: {}", start, end);
        List<FuelTransaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM fuel_transactions WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (start != null) {
            sql.append(" AND tran_date >= ?");
            params.add(start.toString());
        }
        if (end != null) {
            sql.append(" AND tran_date <= ?");
            params.add(end.toString());
        }
        
        sql.append(" ORDER BY tran_date DESC, tran_time DESC");
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FuelTransaction t = mapRow(rs);
                list.add(t);
            }
            logger.info("Retrieved {} fuel transactions between {} and {}", 
                list.size(), start, end);
        } catch (SQLException e) {
            logger.error("Error getting fuel transactions by date range: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting fuel transactions by date range", e);
        }
        return list;
    }

    public List<FuelTransaction> getByDriverAndDateRange(String driverName, LocalDate start, LocalDate end) {
        logger.debug("Getting fuel transactions - Driver: {}, Start: {}, End: {}", driverName, start, end);
        List<FuelTransaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM fuel_transactions WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (driverName != null && !driverName.isBlank()) {
            sql.append(" AND LOWER(TRIM(driver_name)) = ?");
            params.add(driverName.trim().toLowerCase());
        }
        if (start != null) {
            sql.append(" AND tran_date >= ?");
            params.add(start.toString());
        }
        if (end != null) {
            sql.append(" AND tran_date <= ?");
            params.add(end.toString());
        }
        sql.append(" ORDER BY tran_date ASC");

        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); ++i)
                ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FuelTransaction t = mapRow(rs);
                list.add(t);
            }
            logger.info("Retrieved {} fuel transactions for driver {} between {} and {}", 
                list.size(), driverName, start, end);
        } catch (SQLException e) {
            logger.error("Error getting fuel transactions by driver and date range: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting fuel transactions by driver and date range", e);
        }
        return list;
    }

    private FuelTransaction mapRow(ResultSet rs) throws SQLException {
        return new FuelTransaction(
            rs.getInt("id"),
            rs.getString("card_number"),
            rs.getString("tran_date"),
            rs.getString("tran_time"),
            rs.getString("invoice"),
            rs.getString("unit"),
            rs.getString("driver_name"),
            rs.getString("odometer"),
            rs.getString("location_name"),
            rs.getString("city"),
            rs.getString("state_prov"),
            rs.getDouble("fees"),
            rs.getString("item"),
            rs.getDouble("unit_price"),
            rs.getDouble("disc_ppu"),
            rs.getDouble("disc_cost"),
            rs.getDouble("qty"),
            rs.getDouble("disc_amt"),
            rs.getString("disc_type"),
            rs.getDouble("amt"),
            rs.getString("db"),
            rs.getString("currency"),
            rs.getInt("employee_id")
        );
    }
}