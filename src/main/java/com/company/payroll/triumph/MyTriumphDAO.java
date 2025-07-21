package com.company.payroll.triumph;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MyTriumphDAO {
    private static final Logger logger = LoggerFactory.getLogger(MyTriumphDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public MyTriumphDAO() {
        logger.debug("Initializing MyTriumphDAO");
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // First, check if columns exist and add if needed
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "mytriumph_audit", "source");
            boolean hasSourceColumn = rs.next();
            rs.close();
            
            if (!hasSourceColumn) {
                logger.info("Adding source and matched columns to mytriumph_audit table");
                try {
                    conn.createStatement().execute("ALTER TABLE mytriumph_audit ADD COLUMN source TEXT DEFAULT 'IMPORT'");
                    conn.createStatement().execute("ALTER TABLE mytriumph_audit ADD COLUMN matched INTEGER DEFAULT 0");
                    logger.info("Columns added successfully");
                } catch (SQLException e) {
                    logger.debug("Columns may already exist or table doesn't exist yet");
                }
            }
            
            String sql = """
                CREATE TABLE IF NOT EXISTS mytriumph_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dtr_name TEXT,
                    invoice_number TEXT,
                    invoice_date DATE,
                    po TEXT UNIQUE,
                    inv_amt REAL,
                    source TEXT DEFAULT 'IMPORT',
                    matched INTEGER DEFAULT 0
                );
            """;
            conn.createStatement().execute(sql);
            logger.info("Invoice audit table initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize MyTriumphDAO: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to initialize MyTriumphDAO", e);
        }
    }

    public List<MyTriumphRecord> getAll() {
        logger.debug("Fetching all Invoice audit records");
        List<MyTriumphRecord> records = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM mytriumph_audit ORDER BY invoice_date DESC";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                records.add(mapRow(rs));
            }
            logger.info("Retrieved {} Invoice audit records", records.size());
        } catch (SQLException e) {
            logger.error("Error fetching Invoice audit records: {}", e.getMessage(), e);
            throw new DataAccessException("Error fetching records", e);
        }
        return records;
    }

    public int add(MyTriumphRecord rec) {
        logger.info("Adding MyTriumph record - PO: {}, Invoice: {}, Amount: ${}", 
            rec.getPo(), rec.getInvoiceNumber(), rec.getInvAmt());
        String sql = "INSERT OR IGNORE INTO mytriumph_audit (dtr_name, invoice_number, invoice_date, po, inv_amt, source, matched) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, rec.getDtrName());
            ps.setString(2, rec.getInvoiceNumber());
            if (rec.getInvoiceDate() != null)
                ps.setDate(3, Date.valueOf(rec.getInvoiceDate()));
            else
                ps.setNull(3, Types.DATE);
            ps.setString(4, rec.getPo());
            ps.setDouble(5, rec.getInvAmt());
            ps.setString(6, rec.getSource() != null ? rec.getSource() : "IMPORT");
            ps.setInt(7, rec.isMatched() ? 1 : 0);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("Record with PO {} was not added (may already exist)", rec.getPo());
                return -1;
            }
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                logger.info("MyTriumph record added successfully with ID: {}", id);
                return id;
            }
        } catch (SQLException e) {
            logger.error("Error adding MyTriumph record: {}", e.getMessage(), e);
            throw new DataAccessException("Error adding MyTriumph record", e);
        }
        return -1;
    }

    public void update(MyTriumphRecord rec) {
        logger.info("Updating MyTriumph record - ID: {}, PO: {}", rec.getId(), rec.getPo());
        String sql = "UPDATE mytriumph_audit SET dtr_name=?, invoice_number=?, invoice_date=?, po=?, inv_amt=?, source=?, matched=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, rec.getDtrName());
            ps.setString(2, rec.getInvoiceNumber());
            if (rec.getInvoiceDate() != null)
                ps.setDate(3, Date.valueOf(rec.getInvoiceDate()));
            else
                ps.setNull(3, Types.DATE);
            ps.setString(4, rec.getPo());
            ps.setDouble(5, rec.getInvAmt());
            ps.setString(6, rec.getSource() != null ? rec.getSource() : "IMPORT");
            ps.setInt(7, rec.isMatched() ? 1 : 0);
            ps.setInt(8, rec.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("MyTriumph record updated successfully");
            } else {
                logger.warn("No MyTriumph record found with ID: {}", rec.getId());
            }
        } catch (SQLException e) {
            logger.error("Error updating MyTriumph record: {}", e.getMessage(), e);
            throw new DataAccessException("Error updating MyTriumph record", e);
        }
    }

    public void delete(int id) {
        logger.info("Deleting MyTriumph record with ID: {}", id);
        String sql = "DELETE FROM mytriumph_audit WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("MyTriumph record deleted successfully");
            } else {
                logger.warn("No MyTriumph record found with ID: {}", id);
            }
        } catch (SQLException e) {
            logger.error("Error deleting MyTriumph record: {}", e.getMessage(), e);
            throw new DataAccessException("Error deleting MyTriumph record", e);
        }
    }

    public boolean existsByInvoiceAndPo(String invoiceNumber, String po) {
        logger.debug("Checking existence by invoice {} and PO {}", invoiceNumber, po);
        String sql = "SELECT 1 FROM mytriumph_audit WHERE invoice_number=? AND po=?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, invoiceNumber);
            ps.setString(2, po);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            logger.debug("Record exists: {}", exists);
            return exists;
        } catch (SQLException e) {
            logger.error("Error checking duplicate MyTriumph record: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean existsByPO(String po) {
        logger.debug("Checking existence by PO: {}", po);
        String sql = "SELECT 1 FROM mytriumph_audit WHERE po=?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, po);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            logger.debug("PO {} exists: {}", po, exists);
            return exists;
        } catch (SQLException e) {
            logger.error("Error checking PO existence: {}", e.getMessage(), e);
            return false;
        }
    }

    public MyTriumphRecord getByPO(String po) {
        logger.debug("Getting record by PO: {}", po);
        String sql = "SELECT * FROM mytriumph_audit WHERE po=?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, po);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                MyTriumphRecord rec = mapRow(rs);
                logger.debug("Found record for PO {}: Invoice {}", po, rec.getInvoiceNumber());
                return rec;
            }
            logger.debug("No record found for PO: {}", po);
        } catch (SQLException e) {
            logger.error("Error getting record by PO: {}", e.getMessage(), e);
        }
        return null;
    }

    public void updateByPO(String po, MyTriumphRecord updates) {
        logger.info("Updating record by PO: {} with invoice: {}", po, updates.getInvoiceNumber());
        String sql = "UPDATE mytriumph_audit SET dtr_name=?, invoice_number=?, invoice_date=?, inv_amt=?, source='IMPORT', matched=1 WHERE po=?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, updates.getDtrName());
            ps.setString(2, updates.getInvoiceNumber());
            if (updates.getInvoiceDate() != null)
                ps.setDate(3, Date.valueOf(updates.getInvoiceDate()));
            else
                ps.setNull(3, Types.DATE);
            ps.setDouble(4, updates.getInvAmt());
            ps.setString(5, po);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Successfully updated record for PO: {}", po);
            } else {
                logger.warn("No record found to update for PO: {}", po);
            }
        } catch (SQLException e) {
            logger.error("Error updating by PO: {}", e.getMessage(), e);
            throw new DataAccessException("Error updating by PO", e);
        }
    }

    public void markAsMatched(String po) {
        logger.info("Marking PO {} as matched", po);
        String sql = "UPDATE mytriumph_audit SET matched=1 WHERE po=? AND source='LOAD'";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, po);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Marked {} records as matched for PO: {}", rowsAffected, po);
            } else {
                logger.debug("No LOAD records found to mark as matched for PO: {}", po);
            }
        } catch (SQLException e) {
            logger.error("Error marking as matched: {}", e.getMessage(), e);
        }
    }

    private MyTriumphRecord mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String dtrName = rs.getString("dtr_name");
        String invoiceNumber = rs.getString("invoice_number");
        LocalDate invoiceDate = null;
        Date d = rs.getDate("invoice_date");
        if (d != null) invoiceDate = d.toLocalDate();
        String po = rs.getString("po");
        double invAmt = rs.getDouble("inv_amt");
        
        MyTriumphRecord rec = new MyTriumphRecord(id, dtrName, invoiceNumber, invoiceDate, po, invAmt);
        
        // Try to get source and matched fields (might not exist in older DBs)
        try {
            rec.setSource(rs.getString("source"));
            rec.setMatched(rs.getInt("matched") == 1);
        } catch (SQLException e) {
            // Columns might not exist yet, use defaults
            rec.setSource("IMPORT");
            rec.setMatched(false);
        }
        
        return rec;
    }
}