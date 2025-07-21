package com.company.payroll.loads;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.exception.DataAccessException;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;

public class LoadDAO {
    private static final Logger logger = LoggerFactory.getLogger(LoadDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TrailerDAO trailerDAO = new TrailerDAO();

    public LoadDAO() {
        logger.debug("Initializing LoadDAO");
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Add new columns if they don't exist (backwards compatible)
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN po_number TEXT");
                logger.info("Added po_number column to loads table");
            } catch (SQLException ignore) {
                logger.debug("po_number column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN customer2 TEXT");
                logger.info("Added customer2 column to loads table");
            } catch (SQLException ignore) {
                logger.debug("customer2 column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN bill_to TEXT");
                logger.info("Added bill_to column to loads table");
            } catch (SQLException ignore) {
                logger.debug("bill_to column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN truck_unit_snapshot TEXT");
                logger.info("Added truck_unit_snapshot column to loads table");
            } catch (SQLException ignore) {
                logger.debug("truck_unit_snapshot column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN reminder TEXT");
                logger.info("Added reminder column to loads table");
            } catch (SQLException ignore) {
                logger.debug("reminder column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN has_lumper INTEGER DEFAULT 0");
                logger.info("Added has_lumper column to loads table");
            } catch (SQLException ignore) {
                logger.debug("has_lumper column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN has_revised_rate_confirmation INTEGER DEFAULT 0");
                logger.info("Added has_revised_rate_confirmation column to loads table");
            } catch (SQLException ignore) {
                logger.debug("has_revised_rate_confirmation column already exists");
            }
            
            // Add trailer-related columns
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN trailer_id INTEGER");
                logger.info("Added trailer_id column to loads table");
            } catch (SQLException ignore) {
                logger.debug("trailer_id column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN trailer_number TEXT");
                logger.info("Added trailer_number column to loads table");
            } catch (SQLException ignore) {
                logger.debug("trailer_number column already exists");
            }
            
            // Add pickup_date column
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN pickup_date DATE");
                logger.info("Added pickup_date column to loads table");
            } catch (SQLException ignore) {
                logger.debug("pickup_date column already exists");
            }
            
            // Add pickup_time column
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN pickup_time TIME");
                logger.info("Added pickup_time column to loads table");
            } catch (SQLException ignore) {
                logger.debug("pickup_time column already exists");
            }
            
            // Add delivery_time column
            try {
                conn.createStatement().execute("ALTER TABLE loads ADD COLUMN delivery_time TIME");
                logger.info("Added delivery_time column to loads table");
            } catch (SQLException ignore) {
                logger.debug("delivery_time column already exists");
            }
            
            // Add new columns to customer_locations if they don't exist
            try {
                conn.createStatement().execute("ALTER TABLE customer_locations ADD COLUMN location_name TEXT");
                logger.info("Added location_name column to customer_locations table");
            } catch (SQLException ignore) {
                logger.debug("location_name column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE customer_locations ADD COLUMN city TEXT");
                logger.info("Added city column to customer_locations table");
            } catch (SQLException ignore) {
                logger.debug("city column already exists");
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE customer_locations ADD COLUMN state TEXT");
                logger.info("Added state column to customer_locations table");
            } catch (SQLException ignore) {
                logger.debug("state column already exists");
            }
            
            
            try {
                conn.createStatement().execute("ALTER TABLE customer_locations ADD COLUMN is_default INTEGER DEFAULT 0");
                logger.info("Added is_default column to customer_locations table");
            } catch (SQLException ignore) {
                logger.debug("is_default column already exists");
            }
            
            // Add customer column to load_locations table if it doesn't exist
            try {
                conn.createStatement().execute("ALTER TABLE load_locations ADD COLUMN customer TEXT");
                logger.info("Added customer column to load_locations table");
            } catch (SQLException ignore) {
                logger.debug("customer column already exists in load_locations");
            }
        } catch (SQLException ignore) {}
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS loads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    load_number TEXT NOT NULL UNIQUE,
                    po_number TEXT,
                    customer TEXT,
                    customer2 TEXT,
                    bill_to TEXT,
                    pick_up_location TEXT,
                    drop_location TEXT,
                    driver_id INTEGER,
                    truck_unit_snapshot TEXT,
                    trailer_id INTEGER,
                    trailer_number TEXT,
                    status TEXT,
                    gross_amount REAL,
                    notes TEXT,
                    pickup_date DATE,
                    pickup_time TIME,
                    delivery_date DATE,
                    delivery_time TIME,
                    reminder TEXT,
                    has_lumper INTEGER DEFAULT 0,
                    has_revised_rate_confirmation INTEGER DEFAULT 0,
                    FOREIGN KEY(driver_id) REFERENCES employees(id),
                    FOREIGN KEY(trailer_id) REFERENCES trailers(id)
                );
            """;
            conn.createStatement().execute(sql);
            logger.info("Loads table initialized successfully");

            String sqlCustomer = """
                CREATE TABLE IF NOT EXISTS customers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                );
            """;
            conn.createStatement().execute(sqlCustomer);
            logger.info("Customers table initialized successfully");
            
            String sqlBillingEntities = """
                CREATE TABLE IF NOT EXISTS billing_entities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                );
            """;
            conn.createStatement().execute(sqlBillingEntities);
            logger.info("Billing entities table initialized successfully");
            
            String sqlLocations = """
                CREATE TABLE IF NOT EXISTS customer_locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id INTEGER,
                    location_type TEXT,
                    location_name TEXT,
                    address TEXT NOT NULL,
                    city TEXT,
                    state TEXT,
                    is_default INTEGER DEFAULT 0,
                    FOREIGN KEY(customer_id) REFERENCES customers(id) ON DELETE CASCADE,
                    UNIQUE(customer_id, location_type, address, city, state)
                );
            """;
            conn.createStatement().execute(sqlLocations);
            
            // Create new unified address book table
            String sqlAddressBook = """
                CREATE TABLE IF NOT EXISTS customer_address_book (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id INTEGER,
                    location_name TEXT,
                    address TEXT NOT NULL,
                    city TEXT,
                    state TEXT,
                    is_default_pickup INTEGER DEFAULT 0,
                    is_default_drop INTEGER DEFAULT 0,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(customer_id) REFERENCES customers(id) ON DELETE CASCADE,
                    UNIQUE(customer_id, address, city, state)
                );
            """;
            conn.createStatement().execute(sqlAddressBook);
            logger.info("Customer address book table initialized successfully");
            
            // Migrate existing data from customer_locations to unified address book
            migrateToUnifiedAddressBook();
            
            String sqlDocuments = """
                CREATE TABLE IF NOT EXISTS load_documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    load_id INTEGER NOT NULL,
                    file_name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    document_type TEXT NOT NULL,
                    upload_date DATE,
                    FOREIGN KEY(load_id) REFERENCES loads(id) ON DELETE CASCADE
                );
            """;
            conn.createStatement().execute(sqlDocuments);
            logger.info("Load documents table initialized successfully");
            
            String sqlLoadLocations = """
                CREATE TABLE IF NOT EXISTS load_locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    load_id INTEGER NOT NULL,
                    location_type TEXT NOT NULL,
                    customer TEXT,
                    address TEXT,
                    city TEXT,
                    state TEXT,
                    date DATE,
                    time TIME,
                    notes TEXT,
                    sequence INTEGER NOT NULL,
                    FOREIGN KEY(load_id) REFERENCES loads(id) ON DELETE CASCADE
                );
            """;
            conn.createStatement().execute(sqlLoadLocations);
            logger.info("Load locations table initialized successfully");
            
            // Create search indexes for optimized performance
            createSearchIndexes();
            
        } catch (SQLException e) {
            logger.error("Failed to initialize LoadDAO: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to initialize LoadDAO", e);
        }
    }

    public List<Load> getAll() {
        logger.debug("Fetching all loads");
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                load.setLocations(getLoadLocations(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads", list.size());
        } catch (SQLException e) {
            logger.error("Error getting all loads: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting all loads", e);
        }
        return list;
    }

    public int add(Load load) {
        logger.info("Adding new load - Number: {}, Customer: {}, Customer2: {}, Driver: {}, Amount: ${}", 
            load.getLoadNumber(), load.getCustomer(), load.getCustomer2(),
            load.getDriver() != null ? load.getDriver().getName() : "None", 
            load.getGrossAmount());
        
        // Validate required fields
        if (load.getLoadNumber() == null || load.getLoadNumber().trim().isEmpty()) {
            throw new DataAccessException("Load number is required");
        }
        
        String sql = """
            INSERT INTO loads (load_number, po_number, customer, customer2, bill_to, pick_up_location, drop_location, 
            driver_id, truck_unit_snapshot, trailer_id, trailer_number, status, gross_amount, notes, 
            pickup_date, pickup_time, delivery_date, delivery_time, reminder, has_lumper, has_revised_rate_confirmation) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, load.getLoadNumber());
            ps.setString(2, load.getPONumber());
            ps.setString(3, load.getCustomer());
            ps.setString(4, load.getCustomer2());
            ps.setString(5, load.getBillTo());
            ps.setString(6, load.getPickUpLocation());
            ps.setString(7, load.getDropLocation());
            ps.setObject(8, load.getDriver() != null ? load.getDriver().getId() : null);
            ps.setString(9, load.getTruckUnitSnapshot());
            ps.setInt(10, load.getTrailerId());
            ps.setString(11, load.getTrailerNumber());
            ps.setString(12, load.getStatus().name());
            ps.setDouble(13, load.getGrossAmount());
            ps.setString(14, load.getNotes());
            if (load.getPickUpDate() != null)
                ps.setDate(15, java.sql.Date.valueOf(load.getPickUpDate()));
            else
                ps.setNull(15, java.sql.Types.DATE);
            if (load.getPickUpTime() != null)
                ps.setTime(16, java.sql.Time.valueOf(load.getPickUpTime()));
            else
                ps.setNull(16, java.sql.Types.TIME);
            if (load.getDeliveryDate() != null)
                ps.setDate(17, java.sql.Date.valueOf(load.getDeliveryDate()));
            else
                ps.setNull(17, java.sql.Types.DATE);
            if (load.getDeliveryTime() != null)
                ps.setTime(18, java.sql.Time.valueOf(load.getDeliveryTime()));
            else
                ps.setNull(18, java.sql.Types.TIME);
            ps.setString(19, load.getReminder());
            ps.setInt(20, load.isHasLumper() ? 1 : 0);
            ps.setInt(21, load.isHasRevisedRateConfirmation() ? 1 : 0);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                logger.info("Load added successfully with ID: {}", id);
                
                // Save customers if not exists with error handling
                try {
                    if (load.getCustomer() != null && !load.getCustomer().trim().isEmpty()) {
                        addCustomerIfNotExists(load.getCustomer().trim());
                        // Auto-add pickup address to unified address book
                        autoSaveAddressToCustomerBook(load.getCustomer().trim(), load.getPickUpLocation(), true);
                    }
                    if (load.getCustomer2() != null && !load.getCustomer2().trim().isEmpty()) {
                        addCustomerIfNotExists(load.getCustomer2().trim());
                        // Auto-add drop address to unified address book
                        autoSaveAddressToCustomerBook(load.getCustomer2().trim(), load.getDropLocation(), false);
                    }
                } catch (Exception e) {
                    logger.warn("Error saving customer data for load {}: {}", load.getLoadNumber(), e.getMessage());
                    // Don't fail the load creation if customer saving fails
                }
                
                // Set the load ID and save additional locations
                load.setId(id);
                saveAdditionalLocations(load);
                
                return id;
            } else {
                logger.error("No generated key returned for load: {}", load.getLoadNumber());
                throw new DataAccessException("Failed to get generated key for new load");
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: loads.load_number")) {
                logger.error("Duplicate Load # not allowed: {}", load.getLoadNumber());
                throw new DataAccessException("Duplicate Load # not allowed.", e);
            }
            logger.error("Error adding load: {}", e.getMessage(), e);
            throw new DataAccessException("Error adding load", e);
        }
    }

    public void update(Load load) {
        logger.info("Updating load - ID: {}, Number: {}, Status: {}", 
            load.getId(), load.getLoadNumber(), load.getStatus());
        
        // Validate required fields
        if (load.getId() <= 0) {
            throw new DataAccessException("Invalid load ID for update");
        }
        if (load.getLoadNumber() == null || load.getLoadNumber().trim().isEmpty()) {
            throw new DataAccessException("Load number is required");
        }
        
        String sql = """
            UPDATE loads SET load_number=?, po_number=?, customer=?, customer2=?, bill_to=?, pick_up_location=?, 
            drop_location=?, driver_id=?, truck_unit_snapshot=?, trailer_id=?, trailer_number=?, status=?, gross_amount=?, 
            notes=?, pickup_date=?, pickup_time=?, delivery_date=?, delivery_time=?, reminder=?, has_lumper=?, has_revised_rate_confirmation=? 
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, load.getLoadNumber());
            ps.setString(2, load.getPONumber());
            ps.setString(3, load.getCustomer());
            ps.setString(4, load.getCustomer2());
            ps.setString(5, load.getBillTo());
            ps.setString(6, load.getPickUpLocation());
            ps.setString(7, load.getDropLocation());
            ps.setObject(8, load.getDriver() != null ? load.getDriver().getId() : null);
            ps.setString(9, load.getTruckUnitSnapshot());
            ps.setInt(10, load.getTrailerId());
            ps.setString(11, load.getTrailerNumber());
            ps.setString(12, load.getStatus().name());
            ps.setDouble(13, load.getGrossAmount());
            ps.setString(14, load.getNotes());
            if (load.getPickUpDate() != null)
                ps.setDate(15, java.sql.Date.valueOf(load.getPickUpDate()));
            else
                ps.setNull(15, java.sql.Types.DATE);
            if (load.getPickUpTime() != null)
                ps.setTime(16, java.sql.Time.valueOf(load.getPickUpTime()));
            else
                ps.setNull(16, java.sql.Types.TIME);
            if (load.getDeliveryDate() != null)
                ps.setDate(17, java.sql.Date.valueOf(load.getDeliveryDate()));
            else
                ps.setNull(17, java.sql.Types.DATE);
            if (load.getDeliveryTime() != null)
                ps.setTime(18, java.sql.Time.valueOf(load.getDeliveryTime()));
            else
                ps.setNull(18, java.sql.Types.TIME);
            ps.setString(19, load.getReminder());
            ps.setInt(20, load.isHasLumper() ? 1 : 0);
            ps.setInt(21, load.isHasRevisedRateConfirmation() ? 1 : 0);
            ps.setInt(22, load.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Load updated successfully");
                
                // Save customer if not exists with error handling
                try {
                    if (load.getCustomer() != null && !load.getCustomer().trim().isEmpty()) {
                        addCustomerIfNotExists(load.getCustomer().trim());
                        // Auto-add pickup address to customer's address book
                        autoSaveAddressToCustomerBook(load.getCustomer().trim(), load.getPickUpLocation(), true);
                    }
                    // Save customer2 if not exists
                    if (load.getCustomer2() != null && !load.getCustomer2().trim().isEmpty()) {
                        addCustomerIfNotExists(load.getCustomer2().trim());
                        // Auto-add drop address to customer2's address book
                        autoSaveAddressToCustomerBook(load.getCustomer2().trim(), load.getDropLocation(), false);
                    }
                } catch (Exception e) {
                    logger.warn("Error saving customer data for load {}: {}", load.getLoadNumber(), e.getMessage());
                    // Don't fail the load update if customer saving fails
                }
                
                // Update additional locations
                saveAdditionalLocations(load);
            } else {
                logger.warn("No load found with ID: {}", load.getId());
                throw new DataAccessException("No load found with ID: " + load.getId());
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: loads.load_number")) {
                logger.error("Duplicate Load # not allowed: {}", load.getLoadNumber());
                throw new DataAccessException("Duplicate Load # not allowed.", e);
            }
            logger.error("Error updating load: {}", e.getMessage(), e);
            throw new DataAccessException("Error updating load", e);
        }
    }

    public void delete(int id) {
        logger.info("Deleting load with ID: {}", id);
        String sql = "DELETE FROM loads WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Load deleted successfully");
            } else {
                logger.warn("No load found with ID: {}", id);
            }
        } catch (SQLException e) {
            logger.error("Error deleting load: {}", e.getMessage(), e);
            throw new DataAccessException("Error deleting load", e);
        }
    }

    public Load getById(int id) {
        logger.debug("Getting load by ID: {}", id);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                load.setLocations(getLoadLocations(load.getId()));
                logger.debug("Found load: {} (ID: {})", load.getLoadNumber(), id);
                return load;
            }
            logger.debug("No load found with ID: {}", id);
        } catch (SQLException e) {
            logger.error("Error getting load by id: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting load by id", e);
        }
        return null;
    }

    public List<Load> getByDateRange(LocalDate start, LocalDate end) {
        logger.debug("Getting loads by date range - Start: {}, End: {}", start, end);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE delivery_date IS NOT NULL AND delivery_date >= ? AND delivery_date <= ? AND (status = ? OR status = ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDate(1, java.sql.Date.valueOf(start));
            ps.setDate(2, java.sql.Date.valueOf(end)); 
            ps.setString(3, Load.Status.DELIVERED.name());
            ps.setString(4, Load.Status.PAID.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads between {} and {}", list.size(), start, end);
        } catch (SQLException e) {
            logger.error("Error getting loads by date range: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by date range", e);
        }
        return list;
    }

    /**
     * Get all loads for financial calculations within a date range with more inclusive logic.
     * This method includes:
     * - Loads with status DELIVERED or PAID
     * - Loads with status IN_TRANSIT if they have a gross amount > 0
     * - Uses multiple date fields for filtering (delivery_date, pickup_date)
     * - Handles cases where delivery_date might be null
     * 
     * @param start the start date of the range
     * @param end the end date of the range
     * @return list of loads for financial calculations
     */
    public List<Load> getByDateRangeForFinancials(LocalDate start, LocalDate end) {
        logger.debug("Getting loads for financials - Start: {}, End: {}", start, end);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // More inclusive query that considers multiple date fields and statuses
            String sql = """
                SELECT * FROM loads 
                WHERE gross_amount > 0
                AND status NOT IN (?, ?, ?)
                AND (
                    (delivery_date IS NOT NULL AND delivery_date >= ? AND delivery_date <= ?)
                    OR (delivery_date IS NULL AND pickup_date IS NOT NULL AND pickup_date >= ? AND pickup_date <= ?)
                    OR (delivery_date IS NULL AND pickup_date IS NULL AND status IN (?, ?, ?))
                )
                ORDER BY COALESCE(delivery_date, pickup_date) DESC
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, Load.Status.BOOKED.name());
            ps.setString(2, Load.Status.CANCELLED.name());
            ps.setString(3, Load.Status.ASSIGNED.name());
            ps.setDate(4, java.sql.Date.valueOf(start));
            ps.setDate(5, java.sql.Date.valueOf(end));
            ps.setDate(6, java.sql.Date.valueOf(start));
            ps.setDate(7, java.sql.Date.valueOf(end));
            ps.setString(8, Load.Status.DELIVERED.name());
            ps.setString(9, Load.Status.PAID.name());
            ps.setString(10, Load.Status.IN_TRANSIT.name());
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            
            logger.info("Retrieved {} loads for financials between {} and {}", 
                list.size(), start, end);
                
            // Log details about included loads for debugging
            if (logger.isDebugEnabled()) {
                Map<Load.Status, Long> statusCounts = list.stream()
                    .collect(Collectors.groupingBy(Load::getStatus, Collectors.counting()));
                logger.debug("Load status breakdown: {}", statusCounts);
                
                long loadsWithoutDeliveryDate = list.stream()
                    .filter(load -> load.getDeliveryDate() == null)
                    .count();
                if (loadsWithoutDeliveryDate > 0) {
                    logger.debug("Found {} loads without delivery date", loadsWithoutDeliveryDate);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting loads for financials: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads for financials", e);
        }
        return list;
    }

    public List<Load> getByStatus(Load.Status status) {
        logger.debug("Getting loads by status: {}", status);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE status = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads with status {}", list.size(), status);
        } catch (SQLException e) {
            logger.error("Error getting loads by status: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by status", e);
        }
        return list;
    }

    public List<Load> getByDriver(int driverId) {
        logger.debug("Getting loads by driver ID: {}", driverId);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE driver_id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, driverId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                load.setLocations(getLoadLocations(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads for driver ID {}", list.size(), driverId);
        } catch (SQLException e) {
            logger.error("Error getting loads by driver: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by driver", e);
        }
        return list;
    }

    public List<Load> getByTrailer(int trailerId) {
        logger.debug("Getting loads by trailer ID: {}", trailerId);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE trailer_id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, trailerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads for trailer ID {}", list.size(), trailerId);
        } catch (SQLException e) {
            logger.error("Error getting loads by trailer: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by trailer", e);
        }
        return list;
    }

    public List<Load> getByTrailerNumber(String trailerNumber) {
        logger.debug("Getting loads by trailer number: {}", trailerNumber);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE trailer_number = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, trailerNumber);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads for trailer number {}", list.size(), trailerNumber);
        } catch (SQLException e) {
            logger.error("Error getting loads by trailer number: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by trailer number", e);
        }
        return list;
    }

    public List<Load> getByGrossAmountRange(double min, double max) {
        logger.debug("Getting loads by gross amount range - Min: ${}, Max: ${}", min, max);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE gross_amount >= ? AND gross_amount <= ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDouble(1, min);
            ps.setDouble(2, max);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads with amount between ${} and ${}", list.size(), min, max);
        } catch (SQLException e) {
            logger.error("Error getting loads by gross amount range: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by gross amount range", e);
        }
        return list;
    }

    public List<Load> getByTruckUnit(String truckUnit) {
        logger.debug("Getting loads by truck unit: {}", truckUnit);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE truck_unit_snapshot = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, truckUnit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads for truck unit {}", list.size(), truckUnit);
        } catch (SQLException e) {
            logger.error("Error getting loads by truck unit: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by truck unit", e);
        }
        return list;
    }

    public List<Load> search(String loadNum, String customer, Integer driverId, Integer trailerId, Load.Status status, String truckUnit, LocalDate startDate, LocalDate endDate) {
        logger.debug("Searching loads - LoadNum: {}, Customer: {}, DriverId: {}, TrailerId: {}, Status: {}, TruckUnit: {}, StartDate: {}, EndDate: {}", 
            loadNum, customer, driverId, trailerId, status, truckUnit, startDate, endDate);
        List<Load> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM loads WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (loadNum != null && !loadNum.isBlank()) {
            sql.append(" AND lower(load_number) LIKE ?");
            params.add("%" + loadNum.toLowerCase() + "%");
        }
        if (customer != null && !customer.isBlank()) {
            sql.append(" AND lower(customer) LIKE ?");
            params.add("%" + customer.toLowerCase() + "%");
        }
        if (driverId != null) {
            sql.append(" AND driver_id = ?");
            params.add(driverId);
        }
        if (trailerId != null) {
            sql.append(" AND trailer_id = ?");
            params.add(trailerId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (truckUnit != null && !truckUnit.isBlank()) {
            sql.append(" AND lower(truck_unit_snapshot) LIKE ?");
            params.add("%" + truckUnit.toLowerCase() + "%");
        }
        if (startDate != null) {
            sql.append(" AND delivery_date >= ?");
            params.add(java.sql.Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND delivery_date <= ?");
            params.add(java.sql.Date.valueOf(endDate));
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); ++i)
                ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Search returned {} loads", list.size());
        } catch (SQLException e) {
            logger.error("Error searching loads: {}", e.getMessage(), e);
            throw new DataAccessException("Error searching loads", e);
        }
        return list;
    }

    /**
     * Retrieve active loads for a specific driver. Active loads are those
     * that are not cancelled and not yet delivered/paid.
     */
    public List<Load> getActiveLoadsByDriver(int driverId) {
        logger.debug("Getting active loads for driver {}", driverId);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE driver_id = ? AND status IN (?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, driverId);
            ps.setString(2, Load.Status.BOOKED.name());
            ps.setString(3, Load.Status.ASSIGNED.name());
            ps.setString(4, Load.Status.IN_TRANSIT.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Found {} active loads for driver {}", list.size(), driverId);
        } catch (SQLException e) {
            logger.error("Error getting active loads for driver {}: {}", driverId, e.getMessage());
            throw new DataAccessException("Error getting active loads for driver", e);
        }
        return list;
    }

    public List<Load> getByDriverAndDateRange(int driverId, LocalDate start, LocalDate end) {
        logger.debug("Getting loads - DriverId: {}, Start: {}, End: {}", driverId, start, end);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM loads WHERE driver_id = ? AND delivery_date IS NOT NULL AND delivery_date >= ? AND delivery_date <= ? AND (status = ? OR status = ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, driverId);
            ps.setDate(2, java.sql.Date.valueOf(start));
            ps.setDate(3, java.sql.Date.valueOf(end));
            ps.setString(4, Load.Status.DELIVERED.name());
            ps.setString(5, Load.Status.PAID.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            logger.info("Retrieved {} loads for driver {} between {} and {}", 
                list.size(), driverId, start, end);
        } catch (SQLException e) {
            logger.error("Error getting loads by driver and date range: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by driver and date range", e);
        }
        return list;
    }

    /**
     * Get loads for financial calculations with more inclusive logic.
     * This method includes:
     * - Loads with status DELIVERED or PAID
     * - Loads with status IN_TRANSIT if they have a gross amount > 0
     * - Uses multiple date fields for filtering (delivery_date, pickup_date, or load creation date)
     * - Handles cases where delivery_date might be null
     * 
     * @param driverId the driver ID
     * @param start the start date of the range
     * @param end the end date of the range
     * @return list of loads for financial calculations
     */
    public List<Load> getByDriverAndDateRangeForFinancials(int driverId, LocalDate start, LocalDate end) {
        logger.debug("Getting loads for financials - DriverId: {}, Start: {}, End: {}", driverId, start, end);
        List<Load> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Simplified query that avoids complex nested subqueries
            String sql = """
                SELECT * FROM loads 
                WHERE driver_id = ? 
                AND gross_amount > 0
                AND status IN (?, ?, ?)
                AND (
                    (delivery_date IS NOT NULL AND delivery_date >= ? AND delivery_date <= ?)
                    OR (delivery_date IS NULL AND pickup_date IS NOT NULL AND pickup_date >= ? AND pickup_date <= ?)
                    OR (delivery_date IS NULL AND pickup_date IS NULL)
                )
                ORDER BY COALESCE(delivery_date, pickup_date) DESC
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, driverId);
            ps.setString(2, Load.Status.DELIVERED.name());
            ps.setString(3, Load.Status.PAID.name());
            ps.setString(4, Load.Status.IN_TRANSIT.name());
            ps.setDate(5, java.sql.Date.valueOf(start));
            ps.setDate(6, java.sql.Date.valueOf(end));
            ps.setDate(7, java.sql.Date.valueOf(start));
            ps.setDate(8, java.sql.Date.valueOf(end));
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load load = extractLoad(rs);
                load.setDocuments(getDocumentsByLoadId(load.getId()));
                list.add(load);
            }
            
            logger.info("Retrieved {} loads for financials for driver {} between {} and {}", 
                list.size(), driverId, start, end);
                
            // Log details about included loads for debugging
            if (logger.isDebugEnabled()) {
                Map<Load.Status, Long> statusCounts = list.stream()
                    .collect(Collectors.groupingBy(Load::getStatus, Collectors.counting()));
                logger.debug("Load status breakdown: {}", statusCounts);
                
                long loadsWithoutDeliveryDate = list.stream()
                    .filter(load -> load.getDeliveryDate() == null)
                    .count();
                if (loadsWithoutDeliveryDate > 0) {
                    logger.debug("Found {} loads without delivery date", loadsWithoutDeliveryDate);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting loads for financials: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads for financials", e);
        }
        return list;
    }

    // Document management methods
    public int addDocument(Load.LoadDocument doc) {
        logger.info("Adding document: {} for load ID: {}", doc.getFileName(), doc.getLoadId());
        String sql = "INSERT INTO load_documents (load_id, file_name, file_path, document_type, upload_date) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, doc.getLoadId());
            ps.setString(2, doc.getFileName());
            ps.setString(3, doc.getFilePath());
            ps.setString(4, doc.getType().name());
            ps.setDate(5, java.sql.Date.valueOf(doc.getUploadDate()));
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                logger.info("Document added successfully with ID: {}", id);
                return id;
            }
        } catch (SQLException e) {
            logger.error("Error adding document: {}", e.getMessage(), e);
            throw new DataAccessException("Error adding document", e);
        }
        return -1;
    }

    public void deleteDocument(int docId) {
        logger.info("Deleting document with ID: {}", docId);
        String sql = "DELETE FROM load_documents WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, docId);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Document deleted successfully");
            } else {
                logger.warn("No document found with ID: {}", docId);
            }
        } catch (SQLException e) {
            logger.error("Error deleting document: {}", e.getMessage(), e);
            throw new DataAccessException("Error deleting document", e);
        }
    }

    public List<Load.LoadDocument> getDocumentsByLoadId(int loadId) {
        logger.debug("Getting documents for load ID: {}", loadId);
        List<Load.LoadDocument> docs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM load_documents WHERE load_id = ? ORDER BY upload_date DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, loadId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load.LoadDocument doc = new Load.LoadDocument(
                    rs.getInt("id"),
                    rs.getInt("load_id"),
                    rs.getString("file_name"),
                    rs.getString("file_path"),
                    Load.LoadDocument.DocumentType.valueOf(rs.getString("document_type")),
                    rs.getDate("upload_date").toLocalDate()
                );
                docs.add(doc);
            }
            logger.debug("Retrieved {} documents for load ID {}", docs.size(), loadId);
        } catch (SQLException e) {
            logger.error("Error getting documents for load: {}", e.getMessage(), e);
        }
        return docs;
    }
    
    /**
     * Get all documents for multiple loads - useful for payroll PDF merging
     * @param loadIds List of load IDs
     * @return Map of load ID to list of documents
     */
    public Map<Integer, List<Load.LoadDocument>> getDocumentsByLoadIds(List<Integer> loadIds) {
        logger.debug("Getting documents for {} loads", loadIds.size());
        Map<Integer, List<Load.LoadDocument>> documentsMap = new HashMap<>();
        
        if (loadIds.isEmpty()) {
            return documentsMap;
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Build IN clause
            String placeholders = loadIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
            
            String sql = "SELECT * FROM load_documents WHERE load_id IN (" + placeholders + ") ORDER BY load_id, upload_date DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            
            // Set parameters
            for (int i = 0; i < loadIds.size(); i++) {
                ps.setInt(i + 1, loadIds.get(i));
            }
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int loadId = rs.getInt("load_id");
                Load.LoadDocument doc = new Load.LoadDocument(
                    rs.getInt("id"),
                    loadId,
                    rs.getString("file_name"),
                    rs.getString("file_path"),
                    Load.LoadDocument.DocumentType.valueOf(rs.getString("document_type")),
                    rs.getDate("upload_date").toLocalDate()
                );
                
                documentsMap.computeIfAbsent(loadId, k -> new ArrayList<>()).add(doc);
            }
            
            logger.info("Retrieved documents for {} loads", documentsMap.size());
        } catch (SQLException e) {
            logger.error("Error getting documents for multiple loads: {}", e.getMessage(), e);
        }
        
        return documentsMap;
    }
    
    /**
     * Get all PDF documents for a driver's loads within a date range
     * Useful for payroll document merging
     */
    public List<Load.LoadDocument> getPDFDocumentsByDriverAndDateRange(int driverId, LocalDate start, LocalDate end) {
        logger.debug("Getting PDF documents for driver {} between {} and {}", driverId, start, end);
        List<Load.LoadDocument> documents = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT ld.* FROM load_documents ld
                INNER JOIN loads l ON ld.load_id = l.id
                WHERE l.driver_id = ? 
                AND l.delivery_date >= ? 
                AND l.delivery_date <= ?
                AND (l.status = ? OR l.status = ?)
                AND ld.file_path LIKE '%.pdf'
                ORDER BY l.delivery_date, ld.upload_date DESC
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, driverId);
            ps.setDate(2, java.sql.Date.valueOf(start));
            ps.setDate(3, java.sql.Date.valueOf(end));
            ps.setString(4, Load.Status.DELIVERED.name());
            ps.setString(5, Load.Status.PAID.name());
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Load.LoadDocument doc = new Load.LoadDocument(
                    rs.getInt("id"),
                    rs.getInt("load_id"),
                    rs.getString("file_name"),
                    rs.getString("file_path"),
                    Load.LoadDocument.DocumentType.valueOf(rs.getString("document_type")),
                    rs.getDate("upload_date").toLocalDate()
                );
                documents.add(doc);
            }
            
            logger.info("Retrieved {} PDF documents for driver {}", documents.size(), driverId);
        } catch (SQLException e) {
            logger.error("Error getting PDF documents for driver: {}", e.getMessage(), e);
        }
        
        return documents;
    }

    private Load extractLoad(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String loadNumber = rs.getString("load_number");
        String poNumber = "";
        try { poNumber = rs.getString("po_number"); } catch (SQLException ex) { poNumber = ""; }
        String customer = rs.getString("customer");
        String customer2 = null;
        try { customer2 = rs.getString("customer2"); } catch (SQLException ex) { customer2 = null; }
        String billTo = null;
        try { billTo = rs.getString("bill_to"); } catch (SQLException ex) { billTo = null; }
        String pickUp = rs.getString("pick_up_location");
        String drop = rs.getString("drop_location");
        int driverId = rs.getInt("driver_id");
        Employee driver = employeeDAO.getById(driverId);
        
        String truckUnitSnapshot = "";
        try { truckUnitSnapshot = rs.getString("truck_unit_snapshot"); } catch (SQLException ex) { 
            truckUnitSnapshot = driver != null ? driver.getTruckUnit() : ""; 
        }
        
        // Extract trailer information
        int trailerId = 0;
        String trailerNumber = "";
        try { trailerId = rs.getInt("trailer_id"); } catch (SQLException ex) { trailerId = 0; }
        try { trailerNumber = rs.getString("trailer_number"); } catch (SQLException ex) { trailerNumber = ""; }
        
        // Get trailer object if trailerId exists
        Trailer trailer = null;
        if (trailerId > 0) {
            trailer = trailerDAO.findById(trailerId);
        }
        
        Load.Status status = Load.Status.valueOf(rs.getString("status"));
        double gross = rs.getDouble("gross_amount");
        String notes = rs.getString("notes");
        
        LocalDate pickUpDate = null;
        try {
            java.sql.Date sqlPickupDate = rs.getDate("pickup_date");
            if (sqlPickupDate != null) pickUpDate = sqlPickupDate.toLocalDate();
        } catch (SQLException ex) {
            pickUpDate = null;
        }
        
        LocalTime pickUpTime = null;
        try {
            java.sql.Time sqlPickupTime = rs.getTime("pickup_time");
            if (sqlPickupTime != null) pickUpTime = sqlPickupTime.toLocalTime();
        } catch (SQLException ex) {
            pickUpTime = null;
        }
        
        LocalDate deliveryDate = null;
        java.sql.Date sqlDate = rs.getDate("delivery_date");
        if (sqlDate != null) deliveryDate = sqlDate.toLocalDate();
        
        LocalTime deliveryTime = null;
        try {
            java.sql.Time sqlDeliveryTime = rs.getTime("delivery_time");
            if (sqlDeliveryTime != null) deliveryTime = sqlDeliveryTime.toLocalTime();
        } catch (SQLException ex) {
            deliveryTime = null;
        }
        
        String reminder = "";
        try { reminder = rs.getString("reminder"); } catch (SQLException ex) { reminder = ""; }
        
        boolean hasLumper = false;
        try { hasLumper = rs.getInt("has_lumper") == 1; } catch (SQLException ex) { hasLumper = false; }
        
        boolean hasRevisedRateConfirmation = false;
        try { hasRevisedRateConfirmation = rs.getInt("has_revised_rate_confirmation") == 1; } catch (SQLException ex) { hasRevisedRateConfirmation = false; }
        
        Load load = new Load(id, loadNumber, poNumber, customer, customer2, billTo, pickUp, drop, driver, truckUnitSnapshot, 
                          status, gross, notes, pickUpDate, pickUpTime, deliveryDate, deliveryTime, 
                          reminder, hasLumper, hasRevisedRateConfirmation);
        
        // Set trailer info
        load.setTrailerId(trailerId);
        load.setTrailerNumber(trailerNumber);
        load.setTrailer(trailer);
        
        return load;
    }

    public void addCustomerIfNotExists(String customer) {
        if (customer == null || customer.trim().isEmpty()) return;
        logger.debug("Adding customer if not exists: {}", customer);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR IGNORE INTO customers (name) VALUES (?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customer.trim());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Added new customer: {}", customer);
            }
        } catch (SQLException e) {
            logger.error("Error saving customer: {}", e.getMessage(), e);
        }
    }

    public List<String> getAllCustomers() {
        logger.debug("Fetching all customers");
        List<String> customers = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT name FROM customers ORDER BY name COLLATE NOCASE";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                customers.add(rs.getString("name"));
            }
            logger.info("Retrieved {} customers", customers.size());
        } catch (SQLException e) {
            logger.error("Error getting customers: {}", e.getMessage(), e);
        }
        return customers;
    }

    public void deleteCustomer(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) return;
        logger.info("Deleting customer: {}", customerName);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM customers WHERE name = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Customer deleted successfully");
            } else {
                logger.warn("Customer not found: {}", customerName);
            }
        } catch (SQLException e) {
            logger.error("Error deleting customer: {}", e.getMessage(), e);
        }
    }
    
    // BILLING LIST METHODS
    public void addBillingEntityIfNotExists(String billingEntity) {
        if (billingEntity == null || billingEntity.trim().isEmpty()) return;
        logger.debug("Adding billing entity if not exists: {}", billingEntity);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR IGNORE INTO billing_entities (name) VALUES (?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, billingEntity.trim());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Added new billing entity: {}", billingEntity);
            }
        } catch (SQLException e) {
            logger.error("Error saving billing entity: {}", e.getMessage(), e);
        }
    }

    public List<String> getAllBillingEntities() {
        logger.debug("Fetching all billing entities");
        List<String> billingEntities = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT name FROM billing_entities ORDER BY name COLLATE NOCASE";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                billingEntities.add(rs.getString("name"));
            }
            logger.info("Retrieved {} billing entities", billingEntities.size());
        } catch (SQLException e) {
            logger.error("Error getting billing entities: {}", e.getMessage(), e);
        }
        return billingEntities;
    }

    public void deleteBillingEntity(String billingEntityName) {
        if (billingEntityName == null || billingEntityName.trim().isEmpty()) return;
        logger.info("Deleting billing entity: {}", billingEntityName);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM billing_entities WHERE name = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, billingEntityName.trim());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Billing entity deleted successfully");
            } else {
                logger.warn("Billing entity not found: {}", billingEntityName);
            }
        } catch (SQLException e) {
            logger.error("Error deleting billing entity: {}", e.getMessage(), e);
        }
    }
    
    // NEW METHODS FOR CUSTOMER LOCATIONS
    // Enhanced customer location methods
    public int addCustomerLocation(CustomerLocation location, String customerName) {
        logger.debug("Adding enhanced customer location: {} - {}", customerName, location.getLocationType());
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Get customer ID
            String getCustomerSql = "SELECT id FROM customers WHERE name = ?";
            PreparedStatement ps = conn.prepareStatement(getCustomerSql);
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                int customerId = rs.getInt("id");
                location.setCustomerId(customerId);
                
                // Insert location with all fields
                String sql = """
                    INSERT INTO customer_locations 
                    (customer_id, location_type, location_name, address, city, state, is_default)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
                
                ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, customerId);
                ps.setString(2, location.getLocationType());
                ps.setString(3, location.getLocationName());
                ps.setString(4, location.getAddress());
                ps.setString(5, location.getCity());
                ps.setString(6, location.getState());
                ps.setInt(7, location.isDefault() ? 1 : 0);
                
                int affected = ps.executeUpdate();
                if (affected > 0) {
                    rs = ps.getGeneratedKeys();
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        location.setId(id);
                        logger.info("Added customer location with ID: {}", id);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding customer location: {}", e.getMessage(), e);
        }
        return -1;
    }
    
    public void updateCustomerLocation(CustomerLocation location) {
        logger.debug("Updating customer location ID: {}", location.getId());
        
        String sql = """
            UPDATE customer_locations 
            SET location_name = ?, address = ?, city = ?, state = ?, is_default = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, location.getLocationName());
            ps.setString(2, location.getAddress());
            ps.setString(3, location.getCity());
            ps.setString(4, location.getState());
            ps.setInt(5, location.isDefault() ? 1 : 0);
            ps.setInt(6, location.getId());
            
            int affected = ps.executeUpdate();
            if (affected > 0) {
                logger.info("Updated customer location ID: {}", location.getId());
            }
        } catch (SQLException e) {
            logger.error("Error updating customer location: {}", e.getMessage(), e);
        }
    }
    
    public List<CustomerLocation> getCustomerLocationsFull(String customerName, String locationType) {
        logger.debug("Fetching full customer locations for: {} - {}", customerName, locationType);
        List<CustomerLocation> locations = new ArrayList<>();
        if (customerName == null || customerName.trim().isEmpty()) return locations;
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT cl.* FROM customer_locations cl
                INNER JOIN customers c ON cl.customer_id = c.id
                WHERE c.name = ? AND cl.location_type = ?
                ORDER BY cl.is_default DESC, cl.location_name, cl.address COLLATE NOCASE
            """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            ps.setString(2, locationType);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                CustomerLocation loc = new CustomerLocation(
                    rs.getInt("id"),
                    rs.getInt("customer_id"),
                    rs.getString("location_type"),
                    rs.getString("location_name"),
                    rs.getString("address"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getInt("is_default") == 1
                );
                locations.add(loc);
            }
            logger.info("Retrieved {} full locations for customer {} type {}", locations.size(), customerName, locationType);
        } catch (SQLException e) {
            logger.error("Error getting customer locations: {}", e.getMessage(), e);
        }
        return locations;
    }
    
    public Map<String, List<CustomerLocation>> getAllCustomerLocationsFull(String customerName) {
        logger.debug("Fetching all full locations for customer: {}", customerName);
        Map<String, List<CustomerLocation>> locationMap = new HashMap<>();
        if (customerName == null || customerName.trim().isEmpty()) return locationMap;
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT cl.* FROM customer_locations cl
                INNER JOIN customers c ON cl.customer_id = c.id
                WHERE c.name = ?
                ORDER BY cl.location_type, cl.is_default DESC, cl.location_name, cl.address COLLATE NOCASE
            """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String locationType = rs.getString("location_type");
                CustomerLocation loc = new CustomerLocation(
                    rs.getInt("id"),
                    rs.getInt("customer_id"),
                    locationType,
                    rs.getString("location_name"),
                    rs.getString("address"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getInt("is_default") == 1
                );
                locationMap.computeIfAbsent(locationType, k -> new ArrayList<>()).add(loc);
            }
            
            logger.info("Retrieved full locations for customer {}: pickup={}, drop={}", 
                customerName, 
                locationMap.getOrDefault("PICKUP", new ArrayList<>()).size(),
                locationMap.getOrDefault("DROP", new ArrayList<>()).size());
        } catch (SQLException e) {
            logger.error("Error getting all customer locations: {}", e.getMessage(), e);
        }
        return locationMap;
    }
    
    public void deleteCustomerLocationById(int locationId) {
        logger.info("Deleting customer location by ID: {}", locationId);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM customer_locations WHERE id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, locationId);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Customer location deleted successfully");
            } else {
                logger.warn("Customer location not found");
            }
        } catch (SQLException e) {
            logger.error("Error deleting customer location: {}", e.getMessage(), e);
        }
    }
    
    // Legacy methods for backward compatibility
    public void addCustomerLocationIfNotExists(String customerName, String locationType, String address) {
        if (customerName == null || customerName.trim().isEmpty() || address == null || address.trim().isEmpty()) return;
        logger.debug("Adding customer location if not exists: {} - {} - {}", customerName, locationType, address);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Get customer ID
            String getCustomerSql = "SELECT id FROM customers WHERE name = ?";
            PreparedStatement ps = conn.prepareStatement(getCustomerSql);
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int customerId = rs.getInt("id");
                
                // Parse the address to extract street, city, and state
                String street = "";
                String city = "";
                String state = "";
                
                // Expected format: "Street, City, State"
                String[] parts = address.trim().split(",");
                if (parts.length >= 1) {
                    street = parts[0].trim();
                }
                if (parts.length >= 2) {
                    city = parts[1].trim();
                }
                if (parts.length >= 3) {
                    state = parts[2].trim();
                }
                
                // If parsing fails, store entire address in street field
                if (street.isEmpty() && !address.trim().isEmpty()) {
                    street = address.trim();
                }
                
                // Insert location with parsed components
                String sql = "INSERT OR IGNORE INTO customer_locations (customer_id, location_type, address, city, state) VALUES (?, ?, ?, ?, ?)";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, customerId);
                ps.setString(2, locationType);
                ps.setString(3, street);
                ps.setString(4, city);
                ps.setString(5, state);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("Added new customer location: {} - {} - {}, {}, {}", customerName, locationType, street, city, state);
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving customer location: {}", e.getMessage(), e);
        }
    }
    
    public List<String> getCustomerLocations(String customerName, String locationType) {
        logger.debug("Fetching customer locations for: {} - {}", customerName, locationType);
        List<String> locations = new ArrayList<>();
        if (customerName == null || customerName.trim().isEmpty()) return locations;
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT cl.address, cl.city, cl.state FROM customer_locations cl
                INNER JOIN customers c ON cl.customer_id = c.id
                WHERE c.name = ? AND cl.location_type = ?
                ORDER BY cl.address COLLATE NOCASE
            """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            ps.setString(2, locationType);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                
                // Build full address format
                StringBuilder fullAddress = new StringBuilder();
                if (address != null && !address.trim().isEmpty()) {
                    fullAddress.append(address.trim());
                }
                if (city != null && !city.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(city.trim());
                }
                if (state != null && !state.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(state.trim());
                }
                
                // If we still have an empty address, use the original address field
                String finalAddress = fullAddress.length() > 0 ? fullAddress.toString() : address;
                locations.add(finalAddress);
            }
            logger.info("Retrieved {} locations for customer {} type {}", locations.size(), customerName, locationType);
        } catch (SQLException e) {
            logger.error("Error getting customer locations: {}", e.getMessage(), e);
        }
        return locations;
    }
    
    // Method to get all locations from all customers with full address format
    public List<String> getAllLocations() {
        logger.debug("Fetching all locations from all customers");
        List<String> locations = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT DISTINCT cl.address, cl.city, cl.state 
                FROM customer_locations cl
                ORDER BY cl.address COLLATE NOCASE, cl.city COLLATE NOCASE, cl.state COLLATE NOCASE
            """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                
                // Build full address format
                StringBuilder fullAddress = new StringBuilder();
                if (address != null && !address.trim().isEmpty()) {
                    fullAddress.append(address.trim());
                }
                if (city != null && !city.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(city.trim());
                }
                if (state != null && !state.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(state.trim());
                }
                
                // If we have a valid address, add it
                if (fullAddress.length() > 0) {
                    locations.add(fullAddress.toString());
                }
            }
            
            logger.info("Retrieved {} unique locations", locations.size());
        } catch (SQLException e) {
            logger.error("Error getting all locations: {}", e.getMessage(), e);
        }
        return locations;
    }
    
    public void deleteCustomerLocation(String customerName, String locationType, String address) {
        if (customerName == null || customerName.trim().isEmpty() || address == null || address.trim().isEmpty()) return;
        logger.info("Deleting customer location: {} - {} - {}", customerName, locationType, address);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                DELETE FROM customer_locations 
                WHERE customer_id = (SELECT id FROM customers WHERE name = ?)
                AND location_type = ?
                AND address = ?
            """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            ps.setString(2, locationType);
            ps.setString(3, address.trim());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Customer location deleted successfully");
            } else {
                logger.warn("Customer location not found");
            }
        } catch (SQLException e) {
            logger.error("Error deleting customer location: {}", e.getMessage(), e);
        }
    }
    
    public Map<String, List<String>> getAllCustomerLocations(String customerName) {
        logger.debug("Fetching all locations for customer: {}", customerName);
        Map<String, List<String>> locationMap = new HashMap<>();
        if (customerName == null || customerName.trim().isEmpty()) return locationMap;
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT cl.location_type, cl.address, cl.city, cl.state FROM customer_locations cl
                INNER JOIN customers c ON cl.customer_id = c.id
                WHERE c.name = ?
                ORDER BY cl.location_type, cl.address COLLATE NOCASE
            """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String locationType = rs.getString("location_type");
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                
                // Build full address format
                StringBuilder fullAddress = new StringBuilder();
                if (address != null && !address.trim().isEmpty()) {
                    fullAddress.append(address.trim());
                }
                if (city != null && !city.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(city.trim());
                }
                if (state != null && !state.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(state.trim());
                }
                
                // If we still have an empty address, use the original address field
                String finalAddress = fullAddress.length() > 0 ? fullAddress.toString() : address;
                
                locationMap.computeIfAbsent(locationType, k -> new ArrayList<>()).add(finalAddress);
            }
            logger.info("Retrieved locations for customer {}: pickup={}, drop={}", 
                customerName, 
                locationMap.getOrDefault("PICKUP", new ArrayList<>()).size(),
                locationMap.getOrDefault("DROP", new ArrayList<>()).size());
        } catch (SQLException e) {
            logger.error("Error getting all customer locations: {}", e.getMessage(), e);
        }
        return locationMap;
    }
    
    // Load Locations Methods
    public int addLoadLocation(LoadLocation location) {
        logger.debug("Adding load location for load ID: {}, type: {}, sequence: {}", 
            location.getLoadId(), location.getType(), location.getSequence());
        
        String sql = """
            INSERT INTO load_locations (load_id, location_type, customer, address, city, state, date, time, notes, sequence)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, location.getLoadId());
            stmt.setString(2, location.getType().name());
            stmt.setString(3, location.getCustomer());
            stmt.setString(4, location.getAddress());
            stmt.setString(5, location.getCity());
            stmt.setString(6, location.getState());
            stmt.setDate(7, location.getDate() != null ? java.sql.Date.valueOf(location.getDate()) : null);
            stmt.setTime(8, location.getTime() != null ? java.sql.Time.valueOf(location.getTime()) : null);
            stmt.setString(9, location.getNotes());
            stmt.setInt(10, location.getSequence());
            
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("Added load location with ID: {}", id);
                    
                    // Auto-save to Customer Address Book if customer is specified
                    if (location.getCustomer() != null && !location.getCustomer().trim().isEmpty()) {
                        String fullAddress = buildFullAddressFromLocation(location);
                        boolean isPickup = (location.getType() == LoadLocation.LocationType.PICKUP);
                        autoSaveAddressToCustomerBook(location.getCustomer().trim(), fullAddress, isPickup);
                    }
                    
                    return id;
                }
            }
            throw new DataAccessException("Failed to add load location");
        } catch (SQLException e) {
            logger.error("Error adding load location: {}", e.getMessage(), e);
            throw new DataAccessException("Error adding load location", e);
        }
    }
    
    /**
     * Builds a full address string from a LoadLocation for auto-saving
     */
    private String buildFullAddressFromLocation(LoadLocation location) {
        StringBuilder fullAddr = new StringBuilder();
        if (location.getAddress() != null && !location.getAddress().trim().isEmpty()) {
            fullAddr.append(location.getAddress().trim());
        }
        if (location.getCity() != null && !location.getCity().trim().isEmpty()) {
            if (fullAddr.length() > 0) fullAddr.append(", ");
            fullAddr.append(location.getCity().trim());
        }
        if (location.getState() != null && !location.getState().trim().isEmpty()) {
            if (fullAddr.length() > 0) fullAddr.append(", ");
            fullAddr.append(location.getState().trim());
        }
        return fullAddr.toString();
    }
    
    public void updateLoadLocation(LoadLocation location) {
        logger.debug("Updating load location ID: {}", location.getId());
        
        String sql = """
            UPDATE load_locations 
            SET location_type = ?, address = ?, city = ?, state = ?, date = ?, time = ?, notes = ?, sequence = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, location.getType().name());
            stmt.setString(2, location.getAddress());
            stmt.setString(3, location.getCity());
            stmt.setString(4, location.getState());
            stmt.setDate(5, location.getDate() != null ? java.sql.Date.valueOf(location.getDate()) : null);
            stmt.setTime(6, location.getTime() != null ? java.sql.Time.valueOf(location.getTime()) : null);
            stmt.setString(7, location.getNotes());
            stmt.setInt(8, location.getSequence());
            stmt.setInt(9, location.getId());
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Load location not found for update");
            }
            logger.info("Updated load location ID: {}", location.getId());
        } catch (SQLException e) {
            logger.error("Error updating load location: {}", e.getMessage(), e);
            throw new DataAccessException("Error updating load location", e);
        }
    }
    
    public void deleteLoadLocation(int locationId) {
        logger.debug("Deleting load location ID: {}", locationId);
        
        String sql = "DELETE FROM load_locations WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, locationId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                logger.warn("Load location not found for deletion: {}", locationId);
            } else {
                logger.info("Deleted load location ID: {}", locationId);
            }
        } catch (SQLException e) {
            logger.error("Error deleting load location: {}", e.getMessage(), e);
            throw new DataAccessException("Error deleting load location", e);
        }
    }
    
    public List<LoadLocation> getLoadLocations(int loadId) {
        logger.debug("Getting locations for load ID: {}", loadId);
        List<LoadLocation> locations = new ArrayList<>();
        
        String sql = """
            SELECT id, load_id, location_type, customer, address, city, state, date, time, notes, sequence
            FROM load_locations 
            WHERE load_id = ? 
            ORDER BY location_type, sequence
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, loadId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                LoadLocation location = extractLoadLocation(rs);
                locations.add(location);
            }
        } catch (SQLException e) {
            logger.error("Error getting load locations: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting load locations", e);
        }
        
        return locations;
    }
    
    public void deleteLoadLocations(int loadId) {
        logger.debug("Deleting all locations for load ID: {}", loadId);
        
        String sql = "DELETE FROM load_locations WHERE load_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, loadId);
            int affected = stmt.executeUpdate();
            logger.info("Deleted {} locations for load ID: {}", affected, loadId);
        } catch (SQLException e) {
            logger.error("Error deleting load locations: {}", e.getMessage(), e);
            throw new DataAccessException("Error deleting load locations", e);
        }
    }
    
    private LoadLocation extractLoadLocation(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int loadId = rs.getInt("load_id");
        LoadLocation.LocationType type = LoadLocation.LocationType.valueOf(rs.getString("location_type"));
        String customer = "";
        try {
            customer = rs.getString("customer");
            if (customer == null) customer = "";
        } catch (SQLException e) {
            // Column might not exist in older databases
            customer = "";
        }
        String address = rs.getString("address");
        String city = rs.getString("city");
        String state = rs.getString("state");
        LocalDate date = rs.getDate("date") != null ? rs.getDate("date").toLocalDate() : null;
        LocalTime time = rs.getTime("time") != null ? rs.getTime("time").toLocalTime() : null;
        String notes = rs.getString("notes");
        int sequence = rs.getInt("sequence");
        
        return new LoadLocation(id, loadId, type, customer, address, city, state, date, time, notes, sequence);
    }
    
    /**
     * Saves all additional locations for a load to the database
     * This method handles both insert and update scenarios by first deleting existing locations
     * and then inserting the current set of locations
     */
    private void saveAdditionalLocations(Load load) {
        if (load.getLocations() == null || load.getLocations().isEmpty()) {
            // Delete any existing locations if the load has no additional locations
            deleteLoadLocations(load.getId());
            return;
        }
        
        logger.debug("Saving {} additional locations for load {}", load.getLocations().size(), load.getLoadNumber());
        
        try {
            // First delete existing locations to avoid duplicates
            deleteLoadLocations(load.getId());
            
            // Then add all current locations
            for (LoadLocation location : load.getLocations()) {
                location.setLoadId(load.getId());
                int locationId = addLoadLocation(location);
                location.setId(locationId);
                logger.debug("Saved location {} with ID {}", location.getAddress(), locationId);
            }
            
            logger.debug("Successfully saved all additional locations for load {}", load.getLoadNumber());
        } catch (Exception e) {
            logger.error("Error saving additional locations for load {}: {}", load.getLoadNumber(), e.getMessage(), e);
            // Don't throw exception to avoid failing the entire load save operation
        }
    }
    
    // UNIFIED ADDRESS BOOK METHODS
    
    /**
     * Migrates existing customer_locations data to the new unified address book
     */
    private void migrateToUnifiedAddressBook() {
        logger.info("Starting migration to unified address book");
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Check if migration is needed
            String checkSql = "SELECT COUNT(*) FROM customer_address_book";
            PreparedStatement checkPs = conn.prepareStatement(checkSql);
            ResultSet checkRs = checkPs.executeQuery();
            if (checkRs.next() && checkRs.getInt(1) > 0) {
                logger.info("Address book already contains data, skipping migration");
                return;
            }
            
            // Get all existing locations grouped by customer and address
            String selectSql = """
                SELECT cl.customer_id, cl.location_name, cl.address, cl.city, cl.state,
                       GROUP_CONCAT(cl.location_type) as types,
                       MAX(CASE WHEN cl.location_type = 'PICKUP' AND cl.is_default = 1 THEN 1 ELSE 0 END) as is_default_pickup,
                       MAX(CASE WHEN cl.location_type = 'DROP' AND cl.is_default = 1 THEN 1 ELSE 0 END) as is_default_drop
                FROM customer_locations cl
                GROUP BY cl.customer_id, cl.address, cl.city, cl.state
            """;
            
            PreparedStatement selectPs = conn.prepareStatement(selectSql);
            ResultSet rs = selectPs.executeQuery();
            
            String insertSql = """
                INSERT OR IGNORE INTO customer_address_book 
                (customer_id, location_name, address, city, state, is_default_pickup, is_default_drop)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            PreparedStatement insertPs = conn.prepareStatement(insertSql);
            
            int migratedCount = 0;
            while (rs.next()) {
                insertPs.setInt(1, rs.getInt("customer_id"));
                insertPs.setString(2, rs.getString("location_name"));
                insertPs.setString(3, rs.getString("address"));
                insertPs.setString(4, rs.getString("city"));
                insertPs.setString(5, rs.getString("state"));
                insertPs.setInt(6, rs.getInt("is_default_pickup"));
                insertPs.setInt(7, rs.getInt("is_default_drop"));
                
                int rowsAffected = insertPs.executeUpdate();
                if (rowsAffected > 0) {
                    migratedCount++;
                }
            }
            
            logger.info("Migration completed: {} addresses migrated to unified address book", migratedCount);
            
            // Also sync all addresses to customer_locations
            syncAllAddressesToCustomerLocations();
        } catch (SQLException e) {
            logger.error("Error during address book migration", e);
        }
    }
    
    /**
     * Syncs addresses between customer_address_book and customer_locations tables with progress callback
     * This ensures both tables are in sync by syncing in both directions
     */
    public void syncAllAddressesToCustomerLocations(Consumer<SyncProgress> progressCallback) {
        logger.info("Starting optimized address synchronization");
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Count addresses in both tables
            String countBookSql = "SELECT COUNT(*) FROM customer_address_book";
            PreparedStatement countBookPs = conn.prepareStatement(countBookSql);
            ResultSet countBookRs = countBookPs.executeQuery();
            int addressBookCount = countBookRs.next() ? countBookRs.getInt(1) : 0;
            
            String countLocSql = "SELECT COUNT(*) FROM (SELECT DISTINCT customer_id, address, city, state FROM customer_locations)";
            PreparedStatement countLocPs = conn.prepareStatement(countLocSql);
            ResultSet countLocRs = countLocPs.executeQuery();
            int locationCount = countLocRs.next() ? countLocRs.getInt(1) : 0;
            
            logger.info("Found {} addresses in customer_address_book and {} in customer_locations", addressBookCount, locationCount);
            
            int totalOperations = Math.max(addressBookCount, locationCount);
            
            if (progressCallback != null) {
                progressCallback.accept(new SyncProgress(0, totalOperations, "Initializing bidirectional sync...", false));
            }
            
            if (totalOperations == 0) {
                logger.warn("No addresses found in either table");
                if (progressCallback != null) {
                    progressCallback.accept(new SyncProgress(0, 0, "No addresses found to sync", true));
                }
                return;
            }
            
            // Disable auto-commit for batch processing
            conn.setAutoCommit(false);
            
            try {
                int processed = 0;
                
                // Step 1: Sync from customer_locations to customer_address_book (missing addresses)
                if (progressCallback != null) {
                    progressCallback.accept(new SyncProgress(processed, totalOperations, "Syncing locations to address book...", false));
                }
                
                String syncToBookSql = """
                    INSERT OR IGNORE INTO customer_address_book 
                    (customer_id, location_name, address, city, state, is_default_pickup, is_default_drop)
                    SELECT DISTINCT 
                        customer_id, 
                        location_name, 
                        address, 
                        city, 
                        state,
                        CASE WHEN location_type = 'PICKUP' AND is_default = 1 THEN 1 ELSE 0 END,
                        CASE WHEN location_type = 'DROP' AND is_default = 1 THEN 1 ELSE 0 END
                    FROM customer_locations 
                    WHERE NOT EXISTS (
                        SELECT 1 FROM customer_address_book 
                        WHERE customer_address_book.customer_id = customer_locations.customer_id
                        AND customer_address_book.address = customer_locations.address
                        AND customer_address_book.city = customer_locations.city
                        AND customer_address_book.state = customer_locations.state
                    )
                """;
                
                int syncedToBook = conn.createStatement().executeUpdate(syncToBookSql);
                processed += syncedToBook;
                logger.info("Synced {} new addresses from customer_locations to customer_address_book", syncedToBook);
                
                if (progressCallback != null) {
                    progressCallback.accept(new SyncProgress(processed, totalOperations, "Syncing address book to locations...", false));
                }
                
                // Step 2: Sync from customer_address_book to customer_locations (missing locations)
                String syncToLocationsSql = """
                    INSERT OR IGNORE INTO customer_locations 
                    (customer_id, location_type, location_name, address, city, state, is_default)
                    SELECT 
                        customer_id, 
                        'PICKUP' as location_type,
                        location_name, 
                        address, 
                        city, 
                        state,
                        is_default_pickup
                    FROM customer_address_book 
                    WHERE NOT EXISTS (
                        SELECT 1 FROM customer_locations 
                        WHERE customer_locations.customer_id = customer_address_book.customer_id
                        AND customer_locations.address = customer_address_book.address
                        AND customer_locations.city = customer_address_book.city
                        AND customer_locations.state = customer_address_book.state
                        AND customer_locations.location_type = 'PICKUP'
                    )
                    UNION ALL
                    SELECT 
                        customer_id, 
                        'DROP' as location_type,
                        location_name, 
                        address, 
                        city, 
                        state,
                        is_default_drop
                    FROM customer_address_book 
                    WHERE NOT EXISTS (
                        SELECT 1 FROM customer_locations 
                        WHERE customer_locations.customer_id = customer_address_book.customer_id
                        AND customer_locations.address = customer_address_book.address
                        AND customer_locations.city = customer_address_book.city
                        AND customer_locations.state = customer_address_book.state
                        AND customer_locations.location_type = 'DROP'
                    )
                """;
                
                int syncedToLocations = conn.createStatement().executeUpdate(syncToLocationsSql);
                processed += syncedToLocations;
                logger.info("Synced {} new locations from customer_address_book to customer_locations", syncedToLocations);
                
                // Commit transaction
                conn.commit();
                
                logger.info("Successfully completed bidirectional sync: {} operations", processed);
                
                if (progressCallback != null) {
                    progressCallback.accept(new SyncProgress(totalOperations, totalOperations, 
                        "Bidirectional sync complete! Synced " + processed + " items.", true));
                }
                
            } catch (SQLException e) {
                // Rollback on error
                conn.rollback();
                logger.error("Error during batch sync, rolling back", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Error syncing addresses to customer_locations", e);
            if (progressCallback != null) {
                progressCallback.accept(new SyncProgress(0, 0, "Error: " + e.getMessage(), true));
            }
            throw new DataAccessException("Failed to sync addresses", e);
        }
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    public void syncAllAddressesToCustomerLocations() {
        syncAllAddressesToCustomerLocations(null);
    }
    
    /**
     * Updates load statuses based on pickup and delivery times
     * - If BOOKED/ASSIGNED and past pickup time -> PICKUP_LATE
     * - If not DELIVERED and past delivery time -> DELIVERY_LATE
     * @return number of loads updated
     */
    public int updateLateStatuses() {
        logger.info("Checking and updating late load statuses");
        
        String sql = """
            SELECT id, load_number, status, pickup_date, pickup_time, delivery_date, delivery_time
            FROM loads 
            WHERE status IN ('BOOKED', 'ASSIGNED', 'IN_TRANSIT', 'PICKUP_LATE', 'DELIVERY_LATE')
            AND status != 'CANCELLED'
        """;
        
        int updatedCount = 0;
        
        // Add retry logic for database locking
        int retries = 3;
        SQLException lastException = null;
        
        while (retries > 0) {
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                // Use a single connection for all operations to avoid locking
                conn.setAutoCommit(false);
                
                // Set busy timeout to wait up to 5 seconds if database is locked
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 5000");
                }
            
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
            
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            
            while (rs.next()) {
                int loadId = rs.getInt("id");
                String loadNumber = rs.getString("load_number");
                String currentStatus = rs.getString("status");
                
                // Get dates and times
                LocalDate pickupDate = null;
                LocalTime pickupTime = null;
                LocalDate deliveryDate = null; 
                LocalTime deliveryTime = null;
                
                java.sql.Date sqlPickupDate = rs.getDate("pickup_date");
                if (sqlPickupDate != null) {
                    pickupDate = sqlPickupDate.toLocalDate();
                }
                
                Time sqlPickupTime = rs.getTime("pickup_time");
                if (sqlPickupTime != null) {
                    pickupTime = sqlPickupTime.toLocalTime();
                }
                
                java.sql.Date sqlDeliveryDate = rs.getDate("delivery_date");
                if (sqlDeliveryDate != null) {
                    deliveryDate = sqlDeliveryDate.toLocalDate();
                }
                
                Time sqlDeliveryTime = rs.getTime("delivery_time");
                if (sqlDeliveryTime != null) {
                    deliveryTime = sqlDeliveryTime.toLocalTime();
                }
                
                String newStatus = null;
                
                // Check for pickup late
                if ((currentStatus.equals("BOOKED") || currentStatus.equals("ASSIGNED")) && 
                    pickupDate != null) {
                    
                    boolean isPickupLate = false;
                    if (pickupDate.isBefore(today)) {
                        isPickupLate = true;
                    } else if (pickupDate.equals(today) && pickupTime != null && pickupTime.isBefore(now)) {
                        isPickupLate = true;
                    }
                    
                    if (isPickupLate) {
                        newStatus = "PICKUP_LATE";
                        logger.info("Load {} is past pickup time, marking as PICKUP_LATE", loadNumber);
                    }
                }
                
                // Check for delivery late
                if (!currentStatus.equals("DELIVERED") && !currentStatus.equals("PAID") && 
                    deliveryDate != null && newStatus == null) {
                    
                    boolean isDeliveryLate = false;
                    if (deliveryDate.isBefore(today)) {
                        isDeliveryLate = true;
                    } else if (deliveryDate.equals(today) && deliveryTime != null && deliveryTime.isBefore(now)) {
                        isDeliveryLate = true;
                    }
                    
                    if (isDeliveryLate) {
                        newStatus = "DELIVERY_LATE";
                        logger.info("Load {} is past delivery time, marking as DELIVERY_LATE", loadNumber);
                    }
                }
                
                // Update status if changed
                if (newStatus != null && !newStatus.equals(currentStatus)) {
                    updateLoadStatus(conn, loadId, newStatus);
                    updatedCount++;
                }
            }
            
            // Commit all updates
            conn.commit();
            
            if (updatedCount > 0) {
                logger.info("Updated {} loads with late status", updatedCount);
            }
            
            // If we get here, operation was successful
            return updatedCount;
                
            } catch (SQLException e) {
                // Rollback on error
                conn.rollback();
                logger.error("Error updating late load statuses", e);
                lastException = e;
                
                // If it's a database locked error, retry
                if (e.getMessage().contains("database is locked") && retries > 1) {
                    logger.warn("Database is locked, retrying... ({} retries left)", retries - 1);
                    retries--;
                    
                    // Wait a bit before retrying
                    try {
                        Thread.sleep(1000); // Wait 1 second
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DataAccessException("Interrupted while waiting to retry", e);
                    }
                } else {
                    // Not a lock error or no more retries
                    throw new DataAccessException("Failed to update late statuses", e);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error connecting to database", e);
            lastException = e;
            retries--;
            
            if (retries > 0) {
                logger.warn("Failed to connect, retrying... ({} retries left)", retries);
                try {
                    Thread.sleep(500); // Wait 500ms before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DataAccessException("Interrupted while waiting to retry", e);
                }
            }
        }
        }
        
        // If we exhausted all retries
        throw new DataAccessException("Failed to update late statuses after " + 3 + " attempts", lastException);
    }
    
    /**
     * Updates the status of a single load using provided connection
     */
    private void updateLoadStatus(Connection conn, int loadId, String status) throws SQLException {
        String sql = "UPDATE loads SET status = ? WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, loadId);
            ps.executeUpdate();
            
            logger.debug("Updated load {} to status {}", loadId, status);
        }
    }
    
    /**
     * Progress tracking class for sync operations
     */
    public static class SyncProgress {
        public final int current;
        public final int total;
        public final String message;
        public final boolean isComplete;
        
        public SyncProgress(int current, int total, String message, boolean isComplete) {
            this.current = current;
            this.total = total;
            this.message = message;
            this.isComplete = isComplete;
        }
    }
    
    /**
     * Adds a new address to the customer's address book
     */
    public int addCustomerAddress(String customerName, String locationName, String address, 
                                 String city, String state) {
        if (customerName == null || customerName.trim().isEmpty() || 
            address == null || address.trim().isEmpty()) {
            return 0;
        }
        
        logger.debug("Adding address to customer address book: {}", customerName);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Get customer ID
            String getCustomerSql = "SELECT id FROM customers WHERE name = ?";
            PreparedStatement ps = conn.prepareStatement(getCustomerSql);
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                int customerId = rs.getInt("id");
                
                // Insert address
                String insertSql = """
                    INSERT OR IGNORE INTO customer_address_book 
                    (customer_id, location_name, address, city, state)
                    VALUES (?, ?, ?, ?, ?)
                """;
                ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, customerId);
                ps.setString(2, locationName != null ? locationName.trim() : "");
                ps.setString(3, address.trim());
                ps.setString(4, city != null ? city.trim() : "");
                ps.setString(5, state != null ? state.trim() : "");
                
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    ResultSet generatedKeys = ps.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        logger.info("Added new address to address book: {} - {}", customerName, address);
                        
                        // Also sync to customer_locations for both PICKUP and DROP
                        syncAddressToCustomerLocations(customerId, locationName, address, city, state);
                        
                        return id;
                    }
                } else {
                    logger.debug("Address already exists in address book: {} - {}", customerName, address);
                    // Get existing ID
                    String selectIdSql = """
                        SELECT id FROM customer_address_book 
                        WHERE customer_id = ? AND address = ? AND city = ? AND state = ?
                    """;
                    ps = conn.prepareStatement(selectIdSql);
                    ps.setInt(1, customerId);
                    ps.setString(2, address.trim());
                    ps.setString(3, city != null ? city.trim() : "");
                    ps.setString(4, state != null ? state.trim() : "");
                    rs = ps.executeQuery();
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding customer address", e);
        }
        return 0;
    }
    
    /**
     * Gets all addresses from customer's address book
     */
    public List<CustomerAddress> getCustomerAddressBook(String customerName) {
        List<CustomerAddress> addresses = new ArrayList<>();
        if (customerName == null || customerName.trim().isEmpty()) {
            return addresses;
        }
        
        logger.debug("Fetching address book for customer: {}", customerName);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT cab.id, cab.customer_id, cab.location_name, cab.address, cab.city, cab.state,
                       cab.is_default_pickup, cab.is_default_drop, cab.created_date
                FROM customer_address_book cab
                JOIN customers c ON cab.customer_id = c.id
                WHERE c.name = ?
                ORDER BY cab.is_default_pickup DESC, cab.is_default_drop DESC, cab.location_name, cab.address
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                CustomerAddress address = new CustomerAddress();
                address.setId(rs.getInt("id"));
                address.setCustomerId(rs.getInt("customer_id"));
                address.setLocationName(rs.getString("location_name"));
                address.setAddress(rs.getString("address"));
                address.setCity(rs.getString("city"));
                address.setState(rs.getString("state"));
                address.setDefaultPickup(rs.getInt("is_default_pickup") == 1);
                address.setDefaultDrop(rs.getInt("is_default_drop") == 1);
                addresses.add(address);
            }
            
            logger.debug("Loaded {} addresses for customer {}", addresses.size(), customerName);
        } catch (SQLException e) {
            logger.error("Error getting customer address book", e);
        }
        
        return addresses;
    }
    
    /**
     * Updates an address in the customer's address book
     */
    public void updateCustomerAddress(CustomerAddress address) {
        if (address == null || address.getId() <= 0) return;
        
        logger.debug("Updating customer address: {}", address.getId());
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                UPDATE customer_address_book 
                SET location_name = ?, address = ?, city = ?, state = ?,
                    is_default_pickup = ?, is_default_drop = ?
                WHERE id = ?
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, address.getLocationName() != null ? address.getLocationName().trim() : "");
            ps.setString(2, address.getAddress() != null ? address.getAddress().trim() : "");
            ps.setString(3, address.getCity() != null ? address.getCity().trim() : "");
            ps.setString(4, address.getState() != null ? address.getState().trim() : "");
            ps.setInt(5, address.isDefaultPickup() ? 1 : 0);
            ps.setInt(6, address.isDefaultDrop() ? 1 : 0);
            ps.setInt(7, address.getId());
            
            ps.executeUpdate();
            logger.info("Updated customer address: {}", address.getId());
        } catch (SQLException e) {
            logger.error("Error updating customer address", e);
        }
    }
    
    /**
     * Deletes an address from the customer's address book and syncs with customer_locations
     */
    public void deleteCustomerAddress(int addressId) {
        logger.debug("Deleting customer address: {}", addressId);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // First, get the address details before deleting
            String getAddressSql = "SELECT customer_id, address, city, state FROM customer_address_book WHERE id = ?";
            PreparedStatement ps = conn.prepareStatement(getAddressSql);
            ps.setInt(1, addressId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                int customerId = rs.getInt("customer_id");
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                
                // Delete from customer_address_book
                String deleteAddressBookSql = "DELETE FROM customer_address_book WHERE id = ?";
                ps = conn.prepareStatement(deleteAddressBookSql);
                ps.setInt(1, addressId);
                ps.executeUpdate();
                logger.info("Deleted from customer_address_book: {}", addressId);
                
                // Also delete matching entries from customer_locations
                String deleteLocationsSql = "DELETE FROM customer_locations WHERE customer_id = ? AND address = ? AND city = ? AND state = ?";
                ps = conn.prepareStatement(deleteLocationsSql);
                ps.setInt(1, customerId);
                ps.setString(2, address != null ? address : "");
                ps.setString(3, city != null ? city : "");
                ps.setString(4, state != null ? state : "");
                int locationsDeleted = ps.executeUpdate();
                
                if (locationsDeleted > 0) {
                    logger.info("Also deleted {} matching entries from customer_locations", locationsDeleted);
                }
            }
        } catch (SQLException e) {
            logger.error("Error deleting customer address", e);
            throw new DataAccessException("Error deleting customer address", e);
        }
    }
    
    /**
     * Syncs an address from customer_address_book to customer_locations for both PICKUP and DROP
     */
    private void syncAddressToCustomerLocations(int customerId, String locationName, String address, 
                                               String city, String state) {
        logger.debug("Syncing address to customer_locations for customer ID: {}", customerId);
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Add for both PICKUP and DROP location types
            String[] locationTypes = {"PICKUP", "DROP"};
            
            for (String locationType : locationTypes) {
                String insertSql = """
                    INSERT OR IGNORE INTO customer_locations 
                    (customer_id, location_type, location_name, address, city, state, is_default)
                    VALUES (?, ?, ?, ?, ?, ?, 0)
                """;
                
                PreparedStatement ps = conn.prepareStatement(insertSql);
                ps.setInt(1, customerId);
                ps.setString(2, locationType);
                ps.setString(3, locationName != null ? locationName : "");
                ps.setString(4, address != null ? address : "");
                ps.setString(5, city != null ? city : "");
                ps.setString(6, state != null ? state : "");
                
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.debug("Added {} location to customer_locations", locationType);
                }
            }
        } catch (SQLException e) {
            logger.error("Error syncing to customer_locations", e);
        }
    }
    
    /**
     * Enhanced auto-save method that prevents duplicates and handles pickup/drop locations intelligently
     */
    public void autoSaveAddressToCustomerBook(String customerName, String addressString, boolean isPickup) {
        if (customerName == null || customerName.trim().isEmpty() || 
            addressString == null || addressString.trim().isEmpty()) {
            return;
        }
        
        logger.debug("Auto-saving {} address for customer {}: {}", 
            isPickup ? "pickup" : "drop", customerName, addressString);
        
        // Parse the address string (format: "Address, City, State" or "City, State")
        String[] parts = addressString.trim().split(",");
        final String address;
        final String city;
        final String state;
        
        if (parts.length >= 3) {
            address = parts[0].trim();
            city = parts[1].trim();
            state = parts[2].trim();
        } else if (parts.length == 2) {
            address = "";
            city = parts[0].trim();
            state = parts[1].trim();
        } else {
            address = addressString.trim();
            city = "";
            state = "";
        }
        
        // Check if this address already exists to prevent duplicates
        List<CustomerAddress> existingAddresses = getCustomerAddressBook(customerName);
        boolean exists = existingAddresses.stream().anyMatch(addr -> {
            boolean addressMatch = Objects.equals(normalizeString(addr.getAddress()), normalizeString(address));
            boolean cityMatch = Objects.equals(normalizeString(addr.getCity()), normalizeString(city));
            boolean stateMatch = Objects.equals(normalizeString(addr.getState()), normalizeString(state));
            return addressMatch && cityMatch && stateMatch;
        });
        
        if (!exists) {
            // Create a meaningful location name based on the address
            String locationName = generateLocationName(address, city, state, isPickup);
            
            int addressId = addCustomerAddress(customerName, locationName, address, city, state);
            if (addressId > 0) {
                logger.info("Auto-saved {} address for customer {}: {} (ID: {})", 
                    isPickup ? "pickup" : "drop", customerName, addressString, addressId);
            }
        } else {
            logger.debug("Address already exists in customer address book: {} - {}", customerName, addressString);
        }
    }
    
    /**
     * Get address book statistics for the manager
     */
    public Map<String, Object> getAddressBookStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Total customers
            String customerSql = "SELECT COUNT(*) FROM customers";
            PreparedStatement ps = conn.prepareStatement(customerSql);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                stats.put("totalCustomers", rs.getInt(1));
            }
            
            // Total addresses
            String addressSql = "SELECT COUNT(*) FROM customer_address_book";
            ps = conn.prepareStatement(addressSql);
            rs = ps.executeQuery();
            if (rs.next()) {
                stats.put("totalAddresses", rs.getInt(1));
            }
            
            // Average addresses per customer
            String avgSql = """
                SELECT AVG(address_count) as avg_addresses
                FROM (
                    SELECT customer_id, COUNT(*) as address_count
                    FROM customer_address_book
                    GROUP BY customer_id
                )
            """;
            ps = conn.prepareStatement(avgSql);
            rs = ps.executeQuery();
            if (rs.next()) {
                double avg = rs.getDouble(1);
                stats.put("avgAddressesPerCustomer", String.format("%.1f", avg));
            }
            
        } catch (SQLException e) {
            logger.error("Error getting address book statistics", e);
        }
        
        return stats;
    }
    
    /**
     * Search addresses with enhanced matching for autocomplete
     */
    public List<String> searchAddresses(String query) {
        List<String> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        
        String normalizedQuery = normalizeString(query);
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT DISTINCT 
                    c.name as customer_name,
                    cab.address,
                    cab.city,
                    cab.state,
                    cab.location_name
                FROM customer_address_book cab
                JOIN customers c ON cab.customer_id = c.id
                WHERE LOWER(c.name) LIKE ? 
                   OR LOWER(cab.address) LIKE ? 
                   OR LOWER(cab.city) LIKE ? 
                   OR LOWER(cab.state) LIKE ?
                ORDER BY 
                    CASE 
                        WHEN LOWER(c.name) LIKE ? THEN 1
                        WHEN LOWER(cab.address) LIKE ? THEN 2
                        WHEN LOWER(cab.city) LIKE ? THEN 3
                        ELSE 4
                    END,
                    c.name, cab.address
                LIMIT 50
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            String likePattern = "%" + normalizedQuery + "%";
            ps.setString(1, likePattern);
            ps.setString(2, likePattern);
            ps.setString(3, likePattern);
            ps.setString(4, likePattern);
            ps.setString(5, likePattern);
            ps.setString(6, likePattern);
            ps.setString(7, likePattern);
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String customerName = rs.getString("customer_name");
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                String locationName = rs.getString("location_name");
                
                // Build display string
                StringBuilder display = new StringBuilder();
                if (locationName != null && !locationName.trim().isEmpty()) {
                    display.append(locationName).append(" - ");
                }
                display.append(customerName).append(": ");
                
                if (address != null && !address.trim().isEmpty()) {
                    display.append(address);
                }
                if (city != null && !city.trim().isEmpty()) {
                    if (display.length() > 0) display.append(", ");
                    display.append(city);
                }
                if (state != null && !state.trim().isEmpty()) {
                    if (display.length() > 0) display.append(", ");
                    display.append(state);
                }
                
                results.add(display.toString());
            }
            
        } catch (SQLException e) {
            logger.error("Error searching addresses", e);
        }
        
        return results;
    }
    
    /**
     * Get all unique addresses for autocomplete
     */
    public List<String> getAllUniqueAddresses() {
        List<String> addresses = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                SELECT DISTINCT 
                    cab.address,
                    cab.city,
                    cab.state
                FROM customer_address_book cab
                WHERE cab.address IS NOT NULL AND cab.address != ''
                ORDER BY cab.address, cab.city, cab.state
            """;
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                
                StringBuilder fullAddress = new StringBuilder();
                if (address != null && !address.trim().isEmpty()) {
                    fullAddress.append(address);
                }
                if (city != null && !city.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(city);
                }
                if (state != null && !state.trim().isEmpty()) {
                    if (fullAddress.length() > 0) fullAddress.append(", ");
                    fullAddress.append(state);
                }
                
                addresses.add(fullAddress.toString());
            }
            
        } catch (SQLException e) {
            logger.error("Error getting all unique addresses", e);
        }
        
        return addresses;
    }
    
    /**
     * Normalizes strings for comparison (removes extra spaces, converts to lowercase)
     */
    private String normalizeString(String str) {
        if (str == null) return "";
        return str.trim().toLowerCase().replaceAll("\\s+", " ");
    }
    
    /**
     * Generates a meaningful location name for auto-saved addresses
     */
    private String generateLocationName(String address, String city, String state, boolean isPickup) {
        StringBuilder name = new StringBuilder();
        
        if (city != null && !city.trim().isEmpty()) {
            name.append(city.trim());
        }
        if (state != null && !state.trim().isEmpty()) {
            if (name.length() > 0) name.append(", ");
            name.append(state.trim());
        }
        if (name.length() == 0 && address != null && !address.trim().isEmpty()) {
            // If no city/state, use first part of address
            String[] addressParts = address.trim().split(" ");
            name.append(addressParts[0]);
        }
        
        // Add pickup/drop indicator if name is not unique
        String baseName = name.toString();
        if (baseName.isEmpty()) {
            baseName = isPickup ? "Pickup Location" : "Drop Location";
        }
        
        return baseName;
    }

    /**
     * Batch insert addresses for efficient large imports
     * @param addresses List of addresses to insert
     */
    public void batchInsert(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            logger.warn("No addresses provided for batch insert");
            return;
        }
        
        logger.info("Starting batch insert of {} addresses", addresses.size());
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Disable auto-commit for batch processing
            conn.setAutoCommit(false);
            
            try {
                // Prepare statements for batch processing
                String customerInsertSql = "INSERT OR IGNORE INTO customers (name) VALUES (?)";
                String addressInsertSql = """
                    INSERT OR IGNORE INTO customer_address_book 
                    (customer_id, location_name, address, city, state)
                    VALUES (?, ?, ?, ?, ?)
                """;
                
                PreparedStatement customerPs = conn.prepareStatement(customerInsertSql);
                PreparedStatement addressPs = conn.prepareStatement(addressInsertSql);
                
                int batchSize = 100; // Process in batches of 100
                int totalInserted = 0;
                
                for (int i = 0; i < addresses.size(); i++) {
                    Address address = addresses.get(i);
                    
                    // First, ensure customer exists
                    customerPs.setString(1, address.getCustomerName());
                    customerPs.executeUpdate();
                    
                    // Get customer ID
                    String getCustomerSql = "SELECT id FROM customers WHERE name = ?";
                    PreparedStatement getCustomerPs = conn.prepareStatement(getCustomerSql);
                    getCustomerPs.setString(1, address.getCustomerName());
                    ResultSet rs = getCustomerPs.executeQuery();
                    
                    if (rs.next()) {
                        int customerId = rs.getInt("id");
                        
                        // Prepare address insert
                        addressPs.setInt(1, customerId);
                        addressPs.setString(2, address.getLocationName() != null ? address.getLocationName() : "");
                        addressPs.setString(3, address.getAddress());
                        addressPs.setString(4, address.getCity() != null ? address.getCity() : "");
                        addressPs.setString(5, address.getState() != null ? address.getState() : "");
                        
                        int rowsAffected = addressPs.executeUpdate();
                        if (rowsAffected > 0) {
                            totalInserted++;
                        }
                    }
                    
                    // Execute batch every batchSize records
                    if ((i + 1) % batchSize == 0 || i == addresses.size() - 1) {
                        logger.debug("Processed batch: {}/{} addresses", i + 1, addresses.size());
                    }
                }
                
                // Commit transaction
                conn.commit();
                logger.info("Batch insert completed: {} addresses inserted", totalInserted);
                
            } catch (SQLException e) {
                // Rollback on error
                conn.rollback();
                logger.error("Error during batch insert, rolling back", e);
                throw new DataAccessException("Failed to batch insert addresses", e);
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            logger.error("Error in batch insert", e);
            throw new DataAccessException("Error in batch insert", e);
        }
    }
    
    /**
     * Get recent load numbers for autocomplete suggestions
     */
    public List<String> getRecentLoadNumbers(int limit) throws DataAccessException {
        String sql = "SELECT DISTINCT load_number FROM loads ORDER BY id DESC LIMIT ?";
        List<String> loadNumbers = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                loadNumbers.add(rs.getString("load_number"));
            }
            
            return loadNumbers;
        } catch (SQLException e) {
            logger.error("Error getting recent load numbers", e);
            throw new DataAccessException("Error getting recent load numbers", e);
        }
    }
    
    /**
     * Get recent PO numbers for autocomplete suggestions
     */
    public List<String> getRecentPONumbers(int limit) throws DataAccessException {
        String sql = "SELECT DISTINCT po_number FROM loads WHERE po_number IS NOT NULL AND po_number != '' ORDER BY id DESC LIMIT ?";
        List<String> poNumbers = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                poNumbers.add(rs.getString("po_number"));
            }
            
            return poNumbers;
        } catch (SQLException e) {
            logger.error("Error getting recent PO numbers", e);
            throw new DataAccessException("Error getting recent PO numbers", e);
        }
    }
    
    /**
     * Get customer load count for the last N days
     */
    public int getCustomerLoadCount(String customer, int days) throws DataAccessException {
        String sql = "SELECT COUNT(*) FROM loads WHERE (customer = ? OR customer2 = ?) AND pickup_date >= date('now', '-' || ? || ' days')";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, customer);
            pstmt.setString(2, customer);
            pstmt.setInt(3, days);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Error getting customer load count", e);
            throw new DataAccessException("Error getting customer load count", e);
        }
    }
    
    /**
     * Search loads by multiple criteria
     */
    public List<Load> searchLoads(String query, int limit) throws DataAccessException {
        String sql = """
            SELECT * FROM loads 
            WHERE load_number LIKE ? 
                OR po_number LIKE ? 
                OR customer LIKE ? 
                OR customer2 LIKE ? 
                OR pickup_location LIKE ? 
                OR drop_location LIKE ?
            ORDER BY id DESC 
            LIMIT ?
            """;
        
        List<Load> loads = new ArrayList<>();
        String searchPattern = "%" + query + "%";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (int i = 1; i <= 6; i++) {
                pstmt.setString(i, searchPattern);
            }
            pstmt.setInt(7, limit);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Extract data from ResultSet
                int id = rs.getInt("id");
                String loadNumber = rs.getString("load_number");
                String poNumber = rs.getString("po_number");
                String customer = rs.getString("customer");
                String customer2 = rs.getString("customer2");
                String billTo = rs.getString("bill_to");
                String pickUp = rs.getString("pickup_location");
                String drop = rs.getString("drop_location");
                
                // Get driver
                int driverId = rs.getInt("driver_id");
                Employee driver = null;
                if (driverId > 0) {
                    driver = employeeDAO.getById(driverId);
                }
                
                String truckUnitSnapshot = rs.getString("truck_unit_snapshot");
                Load.Status status = Load.Status.valueOf(rs.getString("status"));
                double gross = rs.getDouble("gross_amount");
                String notes = rs.getString("notes");
                
                // Get dates
                LocalDate pickUpDate = null;
                java.sql.Date sqlPickUpDate = rs.getDate("pickup_date");
                if (sqlPickUpDate != null) {
                    pickUpDate = sqlPickUpDate.toLocalDate();
                }
                
                LocalTime pickUpTime = null;
                Time sqlPickUpTime = rs.getTime("pickup_time");
                if (sqlPickUpTime != null) {
                    pickUpTime = sqlPickUpTime.toLocalTime();
                }
                
                LocalDate deliveryDate = null;
                java.sql.Date sqlDeliveryDate = rs.getDate("delivery_date");
                if (sqlDeliveryDate != null) {
                    deliveryDate = sqlDeliveryDate.toLocalDate();
                }
                
                LocalTime deliveryTime = null;
                Time sqlDeliveryTime = rs.getTime("delivery_time");
                if (sqlDeliveryTime != null) {
                    deliveryTime = sqlDeliveryTime.toLocalTime();
                }
                
                String reminder = rs.getString("reminder");
                boolean hasLumper = rs.getBoolean("has_lumper");
                boolean hasRevisedRateConfirmation = rs.getBoolean("has_revised_rate_confirmation");
                
                // Get trailer info
                int trailerId = rs.getInt("trailer_id");
                String trailerNumber = rs.getString("trailer_number");
                Trailer trailer = null;
                if (trailerId > 0) {
                    trailer = trailerDAO.findById(trailerId);
                }
                
                // Create Load object
                Load load = new Load(id, loadNumber, poNumber, customer, customer2, billTo, pickUp, drop, driver, 
                                  truckUnitSnapshot, status, gross, notes, pickUpDate, pickUpTime, 
                                  deliveryDate, deliveryTime, reminder, hasLumper, hasRevisedRateConfirmation);
                
                // Set trailer info
                load.setTrailerId(trailerId);
                load.setTrailerNumber(trailerNumber);
                load.setTrailer(trailer);
                
                loads.add(load);
            }
            
            return loads;
        } catch (SQLException e) {
            logger.error("Error searching loads", e);
            throw new DataAccessException("Error searching loads", e);
        }
    }
    
    /**
     * Gets the last load number from the database
     */
    public String getLastLoadNumber() throws DataAccessException {
        String sql = "SELECT load_number FROM loads ORDER BY id DESC LIMIT 1";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getString("load_number");
            }
            return null;
        } catch (SQLException e) {
            logger.error("Error getting last load number: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting last load number", e);
        }
    }
    
    /**
     * Search customers by name with pattern matching - optimized for on-demand loading
     * @param searchPattern The pattern to search for (supports partial matching)
     * @param limit Maximum number of results to return
     * @return List of matching customer names
     */
    public List<String> searchCustomersByName(String searchPattern, int limit) {
        List<String> customers = new ArrayList<>();
        if (searchPattern == null || searchPattern.trim().isEmpty()) {
            return customers;
        }
        
        String pattern = searchPattern.trim();
        String sql = """
            SELECT DISTINCT name 
            FROM customers 
            WHERE UPPER(name) LIKE UPPER(?)
            ORDER BY 
                CASE 
                    WHEN UPPER(name) = UPPER(?) THEN 1
                    WHEN UPPER(name) LIKE UPPER(?) THEN 2
                    ELSE 3
                END,
                name
            LIMIT ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, "%" + pattern + "%");
            ps.setString(2, pattern);
            ps.setString(3, pattern + "%");
            ps.setInt(4, limit);
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                customers.add(rs.getString("name"));
            }
            
        } catch (SQLException e) {
            logger.error("Error searching customers by name", e);
        }
        
        return customers;
    }
    
    /**
     * Search addresses with pattern matching - optimized for on-demand loading
     * @param searchPattern The pattern to search for
     * @param customerName Optional customer name to filter results
     * @param limit Maximum number of results to return
     * @return List of matching addresses with customer info
     */
    public List<CustomerAddress> searchAddresses(String searchPattern, String customerName, int limit) {
        List<CustomerAddress> addresses = new ArrayList<>();
        if (searchPattern == null || searchPattern.trim().isEmpty()) {
            return addresses;
        }
        
        String pattern = searchPattern.trim();
        StringBuilder sql = new StringBuilder("""
            SELECT cab.id, cab.customer_id, cab.location_name, cab.address, cab.city, cab.state,
                   cab.is_default_pickup, cab.is_default_drop, c.name as customer_name
            FROM customer_address_book cab
            JOIN customers c ON cab.customer_id = c.id
            WHERE (UPPER(cab.address) LIKE UPPER(?) 
                   OR UPPER(cab.city) LIKE UPPER(?)
                   OR UPPER(cab.location_name) LIKE UPPER(?)
                   OR UPPER(cab.address || ', ' || cab.city || ', ' || cab.state) LIKE UPPER(?))
        """);
        
        if (customerName != null && !customerName.trim().isEmpty()) {
            sql.append(" AND c.name = ?");
        }
        
        sql.append("""
            ORDER BY 
                c.name,
                cab.is_default_pickup DESC,
                cab.is_default_drop DESC,
                CASE 
                    WHEN UPPER(cab.address) = UPPER(?) THEN 1
                    WHEN UPPER(cab.address) LIKE UPPER(?) THEN 2
                    ELSE 3
                END,
                cab.address
            LIMIT ?
        """);
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            ps.setString(paramIndex++, "%" + pattern + "%");
            ps.setString(paramIndex++, "%" + pattern + "%");
            ps.setString(paramIndex++, "%" + pattern + "%");
            ps.setString(paramIndex++, "%" + pattern + "%");
            
            if (customerName != null && !customerName.trim().isEmpty()) {
                ps.setString(paramIndex++, customerName.trim());
            }
            
            ps.setString(paramIndex++, pattern);
            ps.setString(paramIndex++, pattern + "%");
            ps.setInt(paramIndex++, limit);
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CustomerAddress address = new CustomerAddress();
                address.setId(rs.getInt("id"));
                address.setCustomerId(rs.getInt("customer_id"));
                address.setLocationName(rs.getString("location_name"));
                address.setAddress(rs.getString("address"));
                address.setCity(rs.getString("city"));
                address.setState(rs.getString("state"));
                address.setDefaultPickup(rs.getInt("is_default_pickup") == 1);
                address.setDefaultDrop(rs.getInt("is_default_drop") == 1);
                address.setCustomerName(rs.getString("customer_name"));
                addresses.add(address);
            }
            
        } catch (SQLException e) {
            logger.error("Error searching addresses", e);
        }
        
        return addresses;
    }
    
    /**
     * Get customer by exact name match - used for validation
     * @param customerName The exact customer name
     * @return Customer ID if found, -1 otherwise
     */
    public int getCustomerIdByName(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            return -1;
        }
        
        String sql = "SELECT id FROM customers WHERE name = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, customerName.trim());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
            
        } catch (SQLException e) {
            logger.error("Error getting customer ID by name", e);
        }
        
        return -1;
    }
    
    /**
     * Create database indexes for optimized searching
     * Should be called during initialization
     */
    public void createSearchIndexes() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Index on customer name for faster searches
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name COLLATE NOCASE)"
            );
            
            // Index on addresses for faster searches
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_customer_address_book_address ON customer_address_book(address COLLATE NOCASE)"
            );
            
            // Index on city for location searches
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_customer_address_book_city ON customer_address_book(city COLLATE NOCASE)"
            );
            
            // Composite index for customer + address lookups
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_customer_address_book_customer_address ON customer_address_book(customer_id, address)"
            );
            
            logger.info("Database search indexes created successfully");
            
        } catch (SQLException e) {
            logger.error("Error creating search indexes", e);
        }
    }
}