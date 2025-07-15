package com.company.payroll.expenses;

import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for CompanyExpense records.
 * Provides CRUD operations and query methods for company expense management.
 */
public class CompanyExpenseDAO {
    private static final Logger logger = LoggerFactory.getLogger(CompanyExpenseDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public CompanyExpenseDAO() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS company_expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_date DATE NOT NULL,
                    vendor TEXT NOT NULL,
                    category TEXT,
                    department TEXT,
                    description TEXT,
                    amount REAL NOT NULL,
                    payment_method TEXT,
                    receipt_number TEXT,
                    recurring INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'Pending',
                    notes TEXT,
                    employee_id TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    approved_by TEXT,
                    approval_date DATE
                )
            """;
            stmt.execute(sql);
            
            // Create indexes for better performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_expense_date ON company_expenses(expense_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vendor ON company_expenses(vendor)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_category ON company_expenses(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_status ON company_expenses(status)");
            
            logger.info("CompanyExpenseDAO database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize CompanyExpenseDAO database", e);
            throw new DataAccessException("Failed to initialize CompanyExpenseDAO database", e);
        }
    }

    public CompanyExpense save(CompanyExpense expense) {
        if (expense.getId() > 0) {
            return update(expense);
        } else {
            return insert(expense);
        }
    }

    private CompanyExpense insert(CompanyExpense expense) {
        String sql = """
            INSERT INTO company_expenses (
                expense_date, vendor, category, department, description, amount,
                payment_method, receipt_number, recurring, status, notes, employee_id,
                created_at, updated_at, approved_by, approval_date
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setParameters(ps, expense);
            ps.executeUpdate();
            
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    expense.setId(keys.getInt(1));
                }
            }
            
            logger.info("Inserted company expense with ID {}", expense.getId());
            return expense;
        } catch (SQLException e) {
            logger.error("Error inserting company expense", e);
            throw new DataAccessException("Error inserting company expense", e);
        }
    }

    private CompanyExpense update(CompanyExpense expense) {
        String sql = """
            UPDATE company_expenses SET
                expense_date=?, vendor=?, category=?, department=?, description=?, amount=?,
                payment_method=?, receipt_number=?, recurring=?, status=?, notes=?, employee_id=?,
                updated_at=?, approved_by=?, approval_date=?
            WHERE id=?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            // Set all parameters except created_at
            ps.setDate(1, expense.getExpenseDate() != null ? Date.valueOf(expense.getExpenseDate()) : null);
            ps.setString(2, expense.getVendor());
            ps.setString(3, expense.getCategory());
            ps.setString(4, expense.getDepartment());
            ps.setString(5, expense.getDescription());
            ps.setDouble(6, expense.getAmount());
            ps.setString(7, expense.getPaymentMethod());
            ps.setString(8, expense.getReceiptNumber());
            ps.setInt(9, expense.isRecurring() ? 1 : 0);
            ps.setString(10, expense.getStatus());
            ps.setString(11, expense.getNotes());
            ps.setString(12, expense.getEmployeeId());
            ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(14, expense.getApprovedBy());
            ps.setDate(15, expense.getApprovalDate() != null ? Date.valueOf(expense.getApprovalDate()) : null);
            ps.setInt(16, expense.getId());
            
            ps.executeUpdate();
            logger.info("Updated company expense with ID {}", expense.getId());
            return expense;
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
            int affected = ps.executeUpdate();
            if (affected > 0) {
                logger.info("Deleted company expense with ID {}", id);
            } else {
                logger.warn("No company expense found with ID {}", id);
            }
        } catch (SQLException e) {
            logger.error("Error deleting company expense", e);
            throw new DataAccessException("Error deleting company expense", e);
        }
    }

    public CompanyExpense findById(int id) {
        String sql = "SELECT * FROM company_expenses WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
            return null;
        } catch (SQLException e) {
            logger.error("Error finding company expense by ID", e);
            throw new DataAccessException("Error finding company expense by ID", e);
        }
    }

    public List<CompanyExpense> getAll() {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            logger.debug("Retrieved {} company expenses", list.size());
        } catch (SQLException e) {
            logger.error("Error querying all company expenses", e);
            throw new DataAccessException("Error querying all company expenses", e);
        }
        return list;
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
            logger.debug("Retrieved {} company expenses for date range {} to {}", list.size(), start, end);
        } catch (SQLException e) {
            logger.error("Error querying company expenses by date range", e);
            throw new DataAccessException("Error querying company expenses by date range", e);
        }
        return list;
    }

    public List<CompanyExpense> findByVendor(String vendor) {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses WHERE vendor LIKE ? ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + vendor + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error querying company expenses by vendor", e);
            throw new DataAccessException("Error querying company expenses by vendor", e);
        }
        return list;
    }

    public List<CompanyExpense> findByCategory(String category) {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses WHERE category = ? ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error querying company expenses by category", e);
            throw new DataAccessException("Error querying company expenses by category", e);
        }
        return list;
    }

    public List<CompanyExpense> findByStatus(String status) {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses WHERE status = ? ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error querying company expenses by status", e);
            throw new DataAccessException("Error querying company expenses by status", e);
        }
        return list;
    }

    public List<CompanyExpense> findRecurringExpenses() {
        List<CompanyExpense> list = new ArrayList<>();
        String sql = "SELECT * FROM company_expenses WHERE recurring = 1 ORDER BY expense_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error querying recurring expenses", e);
            throw new DataAccessException("Error querying recurring expenses", e);
        }
        return list;
    }

    public double getTotalExpensesByDateRange(LocalDate start, LocalDate end) {
        String sql = "SELECT SUM(amount) FROM company_expenses WHERE expense_date >= ? AND expense_date <= ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(start));
            ps.setDate(2, Date.valueOf(end));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
            return 0.0;
        } catch (SQLException e) {
            logger.error("Error calculating total expenses", e);
            throw new DataAccessException("Error calculating total expenses", e);
        }
    }

    private CompanyExpense mapRow(ResultSet rs) throws SQLException {
        CompanyExpense expense = new CompanyExpense();
        expense.setId(rs.getInt("id"));
        
        Date expenseDate = rs.getDate("expense_date");
        expense.setExpenseDate(expenseDate != null ? expenseDate.toLocalDate() : null);
        
        String vendor = rs.getString("vendor");
        expense.setVendor(vendor != null ? vendor : "Unknown Vendor");
        
        String category = rs.getString("category");
        expense.setCategory(category != null ? category : "Other");
        
        String department = rs.getString("department");
        expense.setDepartment(department != null ? department : "Operations");
        
        expense.setDescription(rs.getString("description"));
        expense.setAmount(rs.getDouble("amount"));
        
        String paymentMethod = rs.getString("payment_method");
        expense.setPaymentMethod(paymentMethod != null ? paymentMethod : "Other");
        expense.setReceiptNumber(rs.getString("receipt_number"));
        expense.setRecurring(rs.getInt("recurring") == 1);
        expense.setStatus(rs.getString("status"));
        expense.setNotes(rs.getString("notes"));
        expense.setEmployeeId(rs.getString("employee_id"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            expense.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            expense.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        expense.setApprovedBy(rs.getString("approved_by"));
        
        Date approvalDate = rs.getDate("approval_date");
        if (approvalDate != null) {
            expense.setApprovalDate(approvalDate.toLocalDate());
        }
        
        return expense;
    }

    private void setParameters(PreparedStatement ps, CompanyExpense e) throws SQLException {
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
        ps.setTimestamp(13, e.getCreatedAt() != null ? Timestamp.valueOf(e.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
        ps.setTimestamp(14, e.getUpdatedAt() != null ? Timestamp.valueOf(e.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
        ps.setString(15, e.getApprovedBy());
        ps.setDate(16, e.getApprovalDate() != null ? Date.valueOf(e.getApprovalDate()) : null);
    }
}