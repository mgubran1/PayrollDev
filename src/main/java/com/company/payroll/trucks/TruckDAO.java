package com.company.payroll.trucks;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TruckDAO {
    private static final Logger logger = LoggerFactory.getLogger(TruckDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public TruckDAO() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS trucks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    unit TEXT UNIQUE,
                    make TEXT,
                    model TEXT,
                    year INTEGER,
                    vin TEXT,
                    license_plate TEXT,
                    license_expiry DATE,
                    inspection_expiry DATE,
                    ifta_expiry DATE,
                    status TEXT
                );
            """;
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize TruckDAO", e);
            throw new DataAccessException("Failed to initialize TruckDAO", e);
        }
    }

    public List<Truck> getAll() {
        List<Truck> list = new ArrayList<>();
        String sql = "SELECT * FROM trucks ORDER BY unit COLLATE NOCASE";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error getting trucks", e);
            throw new DataAccessException("Error getting trucks", e);
        }
        return list;
    }

    public int add(Truck t) {
        String sql = """
            INSERT INTO trucks (unit, make, model, year, vin, license_plate, license_expiry, inspection_expiry, ifta_expiry, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, t);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error adding truck", e);
            throw new DataAccessException("Error adding truck", e);
        }
        return -1;
    }

    public void update(Truck t) {
        String sql = """
            UPDATE trucks SET unit=?, make=?, model=?, year=?, vin=?, license_plate=?, license_expiry=?, inspection_expiry=?, ifta_expiry=?, status=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, t);
            ps.setInt(11, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating truck", e);
            throw new DataAccessException("Error updating truck", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM trucks WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting truck", e);
            throw new DataAccessException("Error deleting truck", e);
        }
    }

    private Truck mapRow(ResultSet rs) throws SQLException {
        return new Truck(
                rs.getInt("id"),
                rs.getString("unit"),
                rs.getString("make"),
                rs.getString("model"),
                rs.getInt("year"),
                rs.getString("vin"),
                rs.getString("license_plate"),
                getDate(rs, "license_expiry"),
                getDate(rs, "inspection_expiry"),
                getDate(rs, "ifta_expiry"),
                rs.getString("status") != null ? Truck.Status.valueOf(rs.getString("status")) : null
        );
    }

    private LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col) != null ? rs.getDate(col).toLocalDate() : null;
    }

    private void setParams(PreparedStatement ps, Truck t) throws SQLException {
        ps.setString(1, t.getUnit());
        ps.setString(2, t.getMake());
        ps.setString(3, t.getModel());
        if (t.getYear() > 0) ps.setInt(4, t.getYear()); else ps.setNull(4, Types.INTEGER);
        ps.setString(5, t.getVin());
        ps.setString(6, t.getLicensePlate());
        if (t.getLicenseExpiry() != null) ps.setDate(7, Date.valueOf(t.getLicenseExpiry())); else ps.setNull(7, Types.DATE);
        if (t.getInspectionExpiry() != null) ps.setDate(8, Date.valueOf(t.getInspectionExpiry())); else ps.setNull(8, Types.DATE);
        if (t.getIftaExpiry() != null) ps.setDate(9, Date.valueOf(t.getIftaExpiry())); else ps.setNull(9, Types.DATE);
        ps.setString(10, t.getStatus() != null ? t.getStatus().name() : null);
    }
}
