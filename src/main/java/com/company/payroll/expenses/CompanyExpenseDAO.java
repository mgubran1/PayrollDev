package com.company.payroll.expenses;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CompanyExpenseDAO {
    private static final Logger logger = LoggerFactory.getLogger(CompanyExpenseDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public CompanyExpenseDAO() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS company_expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_date DATE,
                    description TEXT,
                    amount REAL,
                    receipt_number TEXT UNIQUE,
                    receipt_path TEXT
                );
            """;
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize CompanyExpenseDAO", e);
            throw new DataAccessException("Failed to initialize CompanyExpenseDAO", e);
        }
    }

    public List<CompanyExpense> getAll() {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error getting company expenses", e);
            throw new DataAccessException("Error getting company expenses", e);
        }
        return list;
    }

    public int add(CompanyExpense exp) {
        String sql = """
            INSERT INTO company_expenses (expense_date, description, amount, receipt_number, receipt_path)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, exp);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error adding company expense", e);
            throw new DataAccessException("Error adding company expense", e);
        }
        return -1;
    }

    public void update(CompanyExpense exp) {
        String sql = """
            UPDATE company_expenses SET expense_date=?, description=?, amount=?, receipt_number=?, receipt_path=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, exp);
            ps.setInt(6, exp.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating company expense", e);
            throw new DataAccessException("Error updating company expense", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM company_expenses WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting company expense", e);
            throw new DataAccessException("Error deleting company expense", e);
        }
    }

    private CompanyExpense mapRow(ResultSet rs) throws SQLException {
        return new CompanyExpense(
                rs.getInt("id"),
                getDate(rs, "expense_date"),
                rs.getString("description"),
                rs.getDouble("amount"),
                rs.getString("receipt_number"),
                rs.getString("receipt_path")
        );
    }

    private LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col) != null ? rs.getDate(col).toLocalDate() : null;
    }

    private void setParams(PreparedStatement ps, CompanyExpense exp) throws SQLException {
        if (exp.getExpenseDate() != null) ps.setDate(1, Date.valueOf(exp.getExpenseDate())); else ps.setNull(1, Types.DATE);
        ps.setString(2, exp.getDescription());
        ps.setDouble(3, exp.getAmount());
        ps.setString(4, exp.getReceiptNumber());
        ps.setString(5, exp.getReceiptPath());
    }
}
