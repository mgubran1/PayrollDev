package com.company.payroll.maintenance;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MaintenanceDAO {
    private static final Logger logger = LoggerFactory.getLogger(MaintenanceDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public MaintenanceDAO() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS maintenance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    vehicle_type TEXT,
                    vehicle_id INTEGER,
                    service_date DATE,
                    description TEXT,
                    cost REAL,
                    next_due DATE
                );
            """;
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize MaintenanceDAO", e);
            throw new DataAccessException("Failed to initialize MaintenanceDAO", e);
        }
    }

    public List<MaintenanceRecord> getAll() {
        List<MaintenanceRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM maintenance ORDER BY service_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error getting maintenance records", e);
            throw new DataAccessException("Error getting maintenance records", e);
        }
        return list;
    }

    public int add(MaintenanceRecord r) {
        String sql = """
            INSERT INTO maintenance (vehicle_type, vehicle_id, service_date, description, cost, next_due)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, r);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error adding maintenance record", e);
            throw new DataAccessException("Error adding maintenance record", e);
        }
        return -1;
    }

    public void update(MaintenanceRecord r) {
        String sql = """
            UPDATE maintenance SET vehicle_type=?, vehicle_id=?, service_date=?, description=?, cost=?, next_due=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, r);
            ps.setInt(7, r.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating maintenance record", e);
            throw new DataAccessException("Error updating maintenance record", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM maintenance WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting maintenance record", e);
            throw new DataAccessException("Error deleting maintenance record", e);
        }
    }

    private MaintenanceRecord mapRow(ResultSet rs) throws SQLException {
        return new MaintenanceRecord(
                rs.getInt("id"),
                rs.getString("vehicle_type") != null ? MaintenanceRecord.VehicleType.valueOf(rs.getString("vehicle_type")) : null,
                rs.getInt("vehicle_id"),
                getDate(rs,"service_date"),
                rs.getString("description"),
                rs.getDouble("cost"),
                getDate(rs,"next_due")
        );
    }

    private LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col) != null ? rs.getDate(col).toLocalDate() : null;
    }

    private void setParams(PreparedStatement ps, MaintenanceRecord r) throws SQLException {
        ps.setString(1, r.getVehicleType() != null ? r.getVehicleType().name() : null);
        ps.setInt(2, r.getVehicleId());
        if (r.getServiceDate() != null) ps.setDate(3, Date.valueOf(r.getServiceDate())); else ps.setNull(3, Types.DATE);
        ps.setString(4, r.getDescription());
        ps.setDouble(5, r.getCost());
        if (r.getNextDue() != null) ps.setDate(6, Date.valueOf(r.getNextDue())); else ps.setNull(6, Types.DATE);
    }
}
