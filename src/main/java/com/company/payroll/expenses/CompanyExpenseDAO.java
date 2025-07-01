package com.company.payroll.expenses;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for {@link CompanyExpense} records.
 * Provides simple CRUD operations used by {@link CompanyExpensesTab}.
 */
public class CompanyExpenseDAO {
    private static final Logger logger = LoggerFactory.getLogger(CompanyExpenseDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public CompanyExpenseDAO() {
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS company_expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_date DATE NOT NULL,
                    vendor TEXT,
                    category TEXT,
                    department TEXT,
                    description TEXT,
                    amount REAL,
                    payment_method TEXT,
                    receipt_number TEXT,
                    recurring INTEGER DEFAULT 0,
                    status TEXT,
                    notes TEXT,
                    employee_id TEXT
                )
            """;
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize CompanyExpenseDAO", e);
            throw new DataAccessException("Failed to initialize CompanyExpenseDAO", e);
        }
    }

    public CompanyExpense save(CompanyExpense expense) {
        if (expense.getId() > 0) {
            update(expense);
        } else {
            insert(expense);
        }
        return expense;
    }

    private void insert(CompanyExpense expense) {
        String sql = """
            INSERT INTO company_expenses (
                expense_date, vendor, category, department, description, amount,
                payment_method, receipt_number, recurring, status, notes, employee_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, expense);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    expense.setId(keys.getInt(1));
                }
            }
            logger.info("Inserted company expense with ID {}", expense.getId());
        } catch (SQLException e) {
            logger.error("Error inserting company expense", e);
            throw new DataAccessException("Error inserting company expense", e);
        }
    }

    private void update(CompanyExpense expense) {
        String sql = """
            UPDATE company_expenses SET
                expense_date=?, vendor=?, category=?, department=?, description=?, amount=?,
                payment_method=?, receipt_number=?, recurring=?, status=?, notes=?, employee_id=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, expense);
            ps.setInt(13, expense.getId());
            ps.executeUpdate();
            logger.info("Updated company expense with ID {}", expense.getId());
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
            logger.info("Deleted company expense with ID {}", id);
        } catch (SQLException e) {
            logger.error("Error deleting company expense", e);
            throw new DataAccessException("Error deleting company expense", e);
        }
    }

    public List<CompanyExpense> findByDateRange(LocalDate start, LocalDate end) {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses WHERE expense_date >= ? AND expense_date <= ? ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(start));
            ps.setDate(2, Date.valueOf(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error querying company expenses", e);
            throw new DataAccessException("Error querying company expenses", e);
        }
        return list;
    }

    private CompanyExpense mapRow(ResultSet rs) throws SQLException {
        return new CompanyExpense(
            rs.getInt("id"),
            rs.getObject("expense_date") != null ? rs.getDate("expense_date").toLocalDate() : null,
            rs.getString("vendor"),
            rs.getString("category"),
            rs.getString("department"),
            rs.getString("description"),
            rs.getDouble("amount"),
            rs.getString("payment_method"),
            rs.getString("receipt_number"),
            rs.getInt("recurring") == 1,
            rs.getString("status"),
            rs.getString("notes"),
            rs.getString("employee_id")
        );
    }

    private void setParams(PreparedStatement ps, CompanyExpense e) throws SQLException {
        ps.setDate(1, e.getExpenseDate() != null ? Date.valueOf(e.getExpenseDate()) : null);
        ps.setString(2, e.getVendor());
        ps.setString(3, e.getCategory());
        ps.setString(4, e.getDepartment());
        ps.setString(5, e.getDescription());
        ps.setDouble(6, e.getAmount());
        ps.setString(7, e.getPaymentMethod());
        ps.setString(8, e.getReceiptNumber());
        ps.setInt(9, e.isRecurring() ? 1 : 0);
        ps.setString(10, e.getStatus());
        ps.setString(11, e.getNotes());
        ps.setString(12, e.getEmployeeId());
    }
}
