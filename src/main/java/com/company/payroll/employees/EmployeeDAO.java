package com.company.payroll.employees;

import com.company.payroll.exception.DataAccessException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDAO {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";

    public EmployeeDAO() {
        logger.debug("Initializing EmployeeDAO");
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Add trailer_number column if it doesn't exist
            try {
                conn.createStatement().execute("ALTER TABLE employees ADD COLUMN trailer_number TEXT");
                logger.info("Added trailer_number column to employees table");
            } catch (SQLException ignore) {
                logger.debug("trailer_number column already exists");
            }
            
            String sql = """
                CREATE TABLE IF NOT EXISTS employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    truck_unit TEXT,
                    trailer_number TEXT,
                    driver_percent REAL,
                    company_percent REAL,
                    service_fee_percent REAL,
                    dob DATE,
                    license_number TEXT,
                    driver_type TEXT,
                    employee_llc TEXT,
                    cdl_expiry DATE,
                    medical_expiry DATE,
                    status TEXT
                );
            """;
            conn.createStatement().execute(sql);
            logger.info("Employees table initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize EmployeeDAO: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to initialize EmployeeDAO", e);
        }
    }

    public List<Employee> getAll() {
        logger.debug("Fetching all employees");
        List<Employee> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM employees ORDER BY name COLLATE NOCASE";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            logger.info("Retrieved {} employees", list.size());
        } catch (SQLException e) {
            logger.error("Error getting all employees: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting all employees", e);
        }
        return list;
    }

    public ObservableList<Employee> getObservableAll() {
        logger.debug("Getting observable list of all employees");
        return FXCollections.observableArrayList(getAll());
    }

    public List<Employee> getActive() {
        logger.debug("Fetching active employees");
        List<Employee> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM employees WHERE status = ? ORDER BY name COLLATE NOCASE";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, Employee.Status.ACTIVE.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            logger.info("Retrieved {} active employees", list.size());
        } catch (SQLException e) {
            logger.error("Error getting active employees: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting active employees", e);
        }
        return list;
    }

    public int add(Employee emp) {
        logger.info("Adding new employee: {}", emp.getName());
        String sql = """
            INSERT INTO employees 
            (name, truck_unit, trailer_number, driver_percent, company_percent, service_fee_percent, dob, license_number, driver_type, employee_llc, cdl_expiry, medical_expiry, status) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, emp);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                logger.info("Employee added successfully with ID: {}", id);
                return id;
            }
        } catch (SQLException e) {
            logger.error("Error adding employee {}: {}", emp.getName(), e.getMessage(), e);
            throw new DataAccessException("Error adding employee", e);
        }
        return -1;
    }

    public void update(Employee emp) {
        logger.info("Updating employee: {} (ID: {})", emp.getName(), emp.getId());
        String sql = """
            UPDATE employees SET 
                name=?, truck_unit=?, trailer_number=?, driver_percent=?, company_percent=?, service_fee_percent=?, dob=?, license_number=?, driver_type=?, employee_llc=?, cdl_expiry=?, medical_expiry=?, status=?
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, emp);
            ps.setInt(14, emp.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Employee {} updated successfully", emp.getName());
            } else {
                logger.warn("No employee found with ID {} to update", emp.getId());
            }
        } catch (SQLException e) {
            logger.error("Error updating employee {} (ID: {}): {}", emp.getName(), emp.getId(), e.getMessage(), e);
            throw new DataAccessException("Error updating employee", e);
        }
    }

    public void delete(int id) {
        logger.info("Deleting employee with ID: {}", id);
        String sql = "DELETE FROM employees WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Employee with ID {} deleted successfully", id);
            } else {
                logger.warn("No employee found with ID {} to delete", id);
            }
        } catch (SQLException e) {
            logger.error("Error deleting employee with ID {}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Error deleting employee", e);
        }
    }

    public Employee getById(int id) {
        logger.debug("Getting employee by ID: {}", id);
        String sql = "SELECT * FROM employees WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Employee emp = mapRow(rs);
                logger.debug("Found employee: {} (ID: {})", emp.getName(), id);
                return emp;
            }
            logger.debug("No employee found with ID: {}", id);
        } catch (SQLException e) {
            logger.error("Error getting employee by ID {}: {}", id, e.getMessage(), e);
            throw new DataAccessException("Error getting employee by id", e);
        }
        return null;
    }

    public Employee getByTruckUnit(String truckUnit) {
        logger.debug("Getting employee by truck unit: {}", truckUnit);
        if (truckUnit == null || truckUnit.trim().isEmpty()) return null;
        
        String sql = "SELECT * FROM employees WHERE truck_unit = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, truckUnit.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Employee emp = mapRow(rs);
                logger.debug("Found employee: {} for truck unit: {}", emp.getName(), truckUnit);
                return emp;
            }
            logger.debug("No employee found with truck unit: {}", truckUnit);
        } catch (SQLException e) {
            logger.error("Error getting employee by truck unit {}: {}", truckUnit, e.getMessage(), e);
            throw new DataAccessException("Error getting employee by truck unit", e);
        }
        return null;
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        String trailerNumber = "";
        try {
            trailerNumber = rs.getString("trailer_number");
        } catch (SQLException e) {
            // Column might not exist in older databases
            trailerNumber = "";
        }
        
        return new Employee(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("truck_unit"),
            trailerNumber != null ? trailerNumber : "",
            rs.getDouble("driver_percent"),
            rs.getDouble("company_percent"),
            rs.getDouble("service_fee_percent"),
            rs.getObject("dob") != null ? rs.getDate("dob").toLocalDate() : null,
            rs.getString("license_number"),
            rs.getString("driver_type") != null ? Employee.DriverType.valueOf(rs.getString("driver_type")) : null,
            rs.getString("employee_llc"),
            rs.getObject("cdl_expiry") != null ? rs.getDate("cdl_expiry").toLocalDate() : null,
            rs.getObject("medical_expiry") != null ? rs.getDate("medical_expiry").toLocalDate() : null,
            rs.getString("status") != null ? Employee.Status.valueOf(rs.getString("status")) : null
        );
    }

    private void setParams(PreparedStatement ps, Employee emp) throws SQLException {
        ps.setString(1, emp.getName());
        ps.setString(2, emp.getTruckUnit());
        ps.setString(3, emp.getTrailerNumber());
        ps.setDouble(4, emp.getDriverPercent());
        ps.setDouble(5, emp.getCompanyPercent());
        ps.setDouble(6, emp.getServiceFeePercent());
        if (emp.getDob() != null)
            ps.setDate(7, Date.valueOf(emp.getDob()));
        else
            ps.setNull(7, Types.DATE);
        ps.setString(8, emp.getLicenseNumber());
        ps.setString(9, emp.getDriverType() != null ? emp.getDriverType().name() : null);
        ps.setString(10, emp.getEmployeeLLC());
        if (emp.getCdlExpiry() != null)
            ps.setDate(11, Date.valueOf(emp.getCdlExpiry()));
        else
            ps.setNull(11, Types.DATE);
        if (emp.getMedicalExpiry() != null)
            ps.setDate(12, Date.valueOf(emp.getMedicalExpiry()));
        else
            ps.setNull(12, Types.DATE);
        ps.setString(13, emp.getStatus() != null ? emp.getStatus().name() : null);
    }
}