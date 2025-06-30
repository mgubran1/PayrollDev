package mgubran1.PayrollDev.trucks;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
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
                    unit_number TEXT,
                    make TEXT,
                    model TEXT,
                    year INTEGER,
                    vin TEXT,
                    license_plate TEXT,
                    mileage REAL,
                    status TEXT,
                    notes TEXT
                );
            """;
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to init trucks table", e);
        }
    }

    private Truck mapRow(ResultSet rs) throws SQLException {
        return new Truck(
            rs.getInt("id"),
            rs.getString("unit_number"),
            rs.getString("make"),
            rs.getString("model"),
            rs.getInt("year"),
            rs.getString("vin"),
            rs.getString("license_plate"),
            rs.getDouble("mileage"),
            Truck.Status.valueOf(rs.getString("status")),
            rs.getString("notes")
        );
    }

    public List<Truck> getAll() {
        List<Truck> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM trucks");
            while (rs.next()) { list.add(mapRow(rs)); }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to fetch trucks", e);
        }
        return list;
    }

    public int add(Truck t) {
        String sql = """
            INSERT INTO trucks(unit_number, make, model, year, vin, license_plate, mileage, status, notes)
            VALUES(?,?,?,?,?,?,?,?,?)
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
            throw new DataAccessException("Failed to add truck", e);
        }
        return -1;
    }

    public void update(Truck t) {
        String sql = """
            UPDATE trucks SET unit_number=?, make=?, model=?, year=?, vin=?, license_plate=?, mileage=?, status=?, notes=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, t);
            ps.setInt(9, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update truck", e);
        }
    }

    public void delete(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM trucks WHERE id=?");
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete truck", e);
        }
    }

    private void setParams(PreparedStatement ps, Truck t) throws SQLException {
        ps.setString(1, t.getUnitNumber());
        ps.setString(2, t.getMake());
        ps.setString(3, t.getModel());
        ps.setInt(4, t.getYear());
        ps.setString(5, t.getVin());
        ps.setString(6, t.getLicensePlate());
        ps.setDouble(7, t.getMileage());
        ps.setString(8, t.getStatus().name());
        ps.setString(9, t.getNotes());
    }
}
