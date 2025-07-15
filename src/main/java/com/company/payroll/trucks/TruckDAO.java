package com.company.payroll.trucks;

import com.company.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal DAO used by {@link TrucksTab} for basic CRUD operations.
 */
public class TruckDAO {
    private static final Logger logger = LoggerFactory.getLogger(TruckDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public TruckDAO() {
        initDatabase();
    }

    private void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS trucks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                truck_number TEXT NOT NULL UNIQUE,
                vin TEXT,
                make TEXT,
                model TEXT,
                year INTEGER,
                type TEXT,
                status TEXT,
                license_plate TEXT,
                registration_expiry_date DATE,
                insurance_expiry_date DATE,
                next_inspection_due DATE,
                permit_numbers TEXT,
                driver TEXT,
                assigned BOOLEAN DEFAULT 0
            )
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize trucks table", e);
            throw new DataAccessException("Failed to initialize trucks table", e);
        }
    }

    // -- CRUD ---------------------------------------------------------------

    public Truck save(Truck truck) {
        if (truck.getId() > 0) {
            return update(truck);
        }
        return insert(truck);
    }

    private Truck insert(Truck truck) {
        String sql = """
            INSERT INTO trucks (
                truck_number, vin, make, model, year, type, status,
                license_plate, registration_expiry_date, insurance_expiry_date,
                next_inspection_due, permit_numbers, driver, assigned
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setParams(ps, truck);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    truck.setId(keys.getInt(1));
                }
            }
            return truck;
        } catch (SQLException e) {
            logger.error("Failed to insert truck", e);
            throw new DataAccessException("Failed to insert truck", e);
        }
    }

    private Truck update(Truck truck) {
        String sql = """
            UPDATE trucks SET
                truck_number = ?, vin = ?, make = ?, model = ?, year = ?,
                type = ?, status = ?, license_plate = ?,
                registration_expiry_date = ?, insurance_expiry_date = ?,
                next_inspection_due = ?, permit_numbers = ?, driver = ?, assigned = ?
            WHERE id = ?
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, truck);
            ps.setInt(15, truck.getId());
            ps.executeUpdate();
            return truck;
        } catch (SQLException e) {
            logger.error("Failed to update truck", e);
            throw new DataAccessException("Failed to update truck", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM trucks WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete truck", e);
            throw new DataAccessException("Failed to delete truck", e);
        }
    }

    // -- Queries ------------------------------------------------------------

    public List<Truck> findAll() {
        String sql = "SELECT * FROM trucks ORDER BY truck_number";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Truck> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            logger.error("Failed to retrieve trucks", e);
            throw new DataAccessException("Failed to retrieve trucks", e);
        }
    }

    // -- Helpers ------------------------------------------------------------

    private void setParams(PreparedStatement ps, Truck t) throws SQLException {
        ps.setString(1, t.getNumber());
        ps.setString(2, t.getVin());
        ps.setString(3, t.getMake());
        ps.setString(4, t.getModel());
        if (t.getYear() > 0) ps.setInt(5, t.getYear()); else ps.setNull(5, Types.INTEGER);
        ps.setString(6, t.getType());
        ps.setString(7, t.getStatus());
        ps.setString(8, t.getLicensePlate());
        if (t.getRegistrationExpiryDate() != null) ps.setDate(9, Date.valueOf(t.getRegistrationExpiryDate())); else ps.setNull(9, Types.DATE);
        if (t.getInsuranceExpiryDate() != null) ps.setDate(10, Date.valueOf(t.getInsuranceExpiryDate())); else ps.setNull(10, Types.DATE);
        if (t.getNextInspectionDue() != null) ps.setDate(11, Date.valueOf(t.getNextInspectionDue())); else ps.setNull(11, Types.DATE);
        ps.setString(12, t.getPermitNumbers());
        ps.setString(13, t.getDriver());
        ps.setBoolean(14, t.isAssigned());
    }

    private Truck map(ResultSet rs) throws SQLException {
        Truck t = new Truck();
        t.setId(rs.getInt("id"));
        t.setNumber(rs.getString("truck_number"));
        t.setVin(rs.getString("vin"));
        t.setMake(rs.getString("make"));
        t.setModel(rs.getString("model"));
        t.setYear(rs.getInt("year"));
        t.setType(rs.getString("type"));
        t.setStatus(rs.getString("status"));
        t.setLicensePlate(rs.getString("license_plate"));
        Date reg = rs.getDate("registration_expiry_date");
        if (reg != null) t.setRegistrationExpiryDate(reg.toLocalDate());
        Date ins = rs.getDate("insurance_expiry_date");
        if (ins != null) t.setInsuranceExpiryDate(ins.toLocalDate());
        Date insp = rs.getDate("next_inspection_due");
        if (insp != null) t.setNextInspectionDue(insp.toLocalDate());
        t.setPermitNumbers(rs.getString("permit_numbers"));
        t.setDriver(rs.getString("driver"));
        t.setAssigned(rs.getBoolean("assigned"));
        return t;
    }
}
