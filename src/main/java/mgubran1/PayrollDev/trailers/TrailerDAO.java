package mgubran1.PayrollDev.trailers;

import com.company.payroll.exception.DataAccessException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TrailerDAO {
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public TrailerDAO() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS trailers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trailer_number TEXT,
                    type TEXT,
                    status TEXT,
                    notes TEXT
                );
            """;
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to init trailers table", e);
        }
    }

    private Trailer mapRow(ResultSet rs) throws SQLException {
        return new Trailer(
            rs.getInt("id"),
            rs.getString("trailer_number"),
            rs.getString("type"),
            Trailer.Status.valueOf(rs.getString("status")),
            rs.getString("notes")
        );
    }

    public List<Trailer> getAll() {
        List<Trailer> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM trailers");
            while (rs.next()) { list.add(mapRow(rs)); }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to fetch trailers", e);
        }
        return list;
    }

    public int add(Trailer t) {
        String sql = "INSERT INTO trailers(trailer_number, type, status, notes) VALUES(?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, t);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to add trailer", e);
        }
        return -1;
    }

    public void update(Trailer t) {
        String sql = "UPDATE trailers SET trailer_number=?, type=?, status=?, notes=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, t);
            ps.setInt(5, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update trailer", e);
        }
    }

    public void delete(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM trailers WHERE id=?");
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete trailer", e);
        }
    }

    private void setParams(PreparedStatement ps, Trailer t) throws SQLException {
        ps.setString(1, t.getTrailerNumber());
        ps.setString(2, t.getType());
        ps.setString(3, t.getStatus().name());
        ps.setString(4, t.getNotes());
    }
}
