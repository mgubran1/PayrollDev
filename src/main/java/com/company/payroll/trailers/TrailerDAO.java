package com.company.payroll.trailers;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TrailerDAO {
    private static final Logger logger = LoggerFactory.getLogger(TrailerDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public TrailerDAO() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS trailers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    number TEXT UNIQUE,
                    type TEXT,
                    year INTEGER,
                    vin TEXT,
                    license_plate TEXT,
                    license_expiry DATE,
                    inspection_expiry DATE,
                    status TEXT
                );
            """;
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize TrailerDAO", e);
            throw new DataAccessException("Failed to initialize TrailerDAO", e);
        }
    }

    public List<Trailer> getAll() {
        List<Trailer> list = new ArrayList<>();
        String sql = "SELECT * FROM trailers ORDER BY number COLLATE NOCASE";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error getting trailers", e);
            throw new DataAccessException("Error getting trailers", e);
        }
        return list;
    }

    public int add(Trailer t) {
        String sql = """
            INSERT INTO trailers (number, type, year, vin, license_plate, license_expiry, inspection_expiry, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
            logger.error("Error adding trailer", e);
            throw new DataAccessException("Error adding trailer", e);
        }
        return -1;
    }

    public void update(Trailer t) {
        String sql = """
            UPDATE trailers SET number=?, type=?, year=?, vin=?, license_plate=?, license_expiry=?, inspection_expiry=?, status=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, t);
            ps.setInt(9, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating trailer", e);
            throw new DataAccessException("Error updating trailer", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM trailers WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting trailer", e);
            throw new DataAccessException("Error deleting trailer", e);
        }
    }

    private Trailer mapRow(ResultSet rs) throws SQLException {
        return new Trailer(
                rs.getInt("id"),
                rs.getString("number"),
                rs.getString("type"),
                rs.getInt("year"),
                rs.getString("vin"),
                rs.getString("license_plate"),
                getDate(rs, "license_expiry"),
                getDate(rs, "inspection_expiry"),
                rs.getString("status") != null ? Trailer.Status.valueOf(rs.getString("status")) : null
        );
    }

    private LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col) != null ? rs.getDate(col).toLocalDate() : null;
    }

    private void setParams(PreparedStatement ps, Trailer t) throws SQLException {
        ps.setString(1, t.getNumber());
        ps.setString(2, t.getType());
        if (t.getYear() > 0) ps.setInt(3, t.getYear()); else ps.setNull(3, Types.INTEGER);
        ps.setString(4, t.getVin());
        ps.setString(5, t.getLicensePlate());
        if (t.getLicenseExpiry() != null) ps.setDate(6, Date.valueOf(t.getLicenseExpiry())); else ps.setNull(6, Types.DATE);
        if (t.getInspectionExpiry() != null) ps.setDate(7, Date.valueOf(t.getInspectionExpiry())); else ps.setNull(7, Types.DATE);
        ps.setString(8, t.getStatus() != null ? t.getStatus().name() : null);
    }
}
