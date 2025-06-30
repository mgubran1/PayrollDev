package com.company.payroll.loads;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class LoadDAO {
    private static final Logger logger = LoggerFactory.getLogger(LoadDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

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
        } catch (SQLException ignore) {}
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS loads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    load_number TEXT NOT NULL UNIQUE,
                    po_number TEXT,
                    customer TEXT,
                    pick_up_location TEXT,
                    drop_location TEXT,
                    driver_id INTEGER,
                    truck_unit_snapshot TEXT,
                    status TEXT,
                    gross_amount REAL,
                    notes TEXT,
                    delivery_date DATE,
                    reminder TEXT,
                    has_lumper INTEGER DEFAULT 0,
                    has_revised_rate_confirmation INTEGER DEFAULT 0,
                    FOREIGN KEY(driver_id) REFERENCES employees(id)
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
        logger.info("Adding new load - Number: {}, Customer: {}, Driver: {}, Amount: ${}", 
            load.getLoadNumber(), load.getCustomer(), 
            load.getDriver() != null ? load.getDriver().getName() : "None", 
            load.getGrossAmount());
        String sql = """
            INSERT INTO loads (load_number, po_number, customer, pick_up_location, drop_location, 
            driver_id, truck_unit_snapshot, status, gross_amount, notes, delivery_date, 
            reminder, has_lumper, has_revised_rate_confirmation) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, load.getLoadNumber());
            ps.setString(2, load.getPONumber());
            ps.setString(3, load.getCustomer());
            ps.setString(4, load.getPickUpLocation());
            ps.setString(5, load.getDropLocation());
            ps.setObject(6, load.getDriver() != null ? load.getDriver().getId() : null);
            ps.setString(7, load.getTruckUnitSnapshot());
            ps.setString(8, load.getStatus().name());
            ps.setDouble(9, load.getGrossAmount());
            ps.setString(10, load.getNotes());
            if (load.getDeliveryDate() != null)
                ps.setDate(11, java.sql.Date.valueOf(load.getDeliveryDate()));
            else
                ps.setNull(11, java.sql.Types.DATE);
            ps.setString(12, load.getReminder());
            ps.setInt(13, load.isHasLumper() ? 1 : 0);
            ps.setInt(14, load.isHasRevisedRateConfirmation() ? 1 : 0);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                logger.info("Load added successfully with ID: {}", id);
                // Save customer if not exists
                if (load.getCustomer() != null && !load.getCustomer().trim().isEmpty()) {
                    addCustomerIfNotExists(load.getCustomer().trim());
                }
                return id;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: loads.load_number")) {
                logger.error("Duplicate Load # not allowed: {}", load.getLoadNumber());
                throw new DataAccessException("Duplicate Load # not allowed.", e);
            }
            logger.error("Error adding load: {}", e.getMessage(), e);
            throw new DataAccessException("Error adding load", e);
        }
        return -1;
    }

    public void update(Load load) {
        logger.info("Updating load - ID: {}, Number: {}, Status: {}", 
            load.getId(), load.getLoadNumber(), load.getStatus());
        String sql = """
            UPDATE loads SET load_number=?, po_number=?, customer=?, pick_up_location=?, 
            drop_location=?, driver_id=?, truck_unit_snapshot=?, status=?, gross_amount=?, 
            notes=?, delivery_date=?, reminder=?, has_lumper=?, has_revised_rate_confirmation=? 
            WHERE id=?
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, load.getLoadNumber());
            ps.setString(2, load.getPONumber());
            ps.setString(3, load.getCustomer());
            ps.setString(4, load.getPickUpLocation());
            ps.setString(5, load.getDropLocation());
            ps.setObject(6, load.getDriver() != null ? load.getDriver().getId() : null);
            ps.setString(7, load.getTruckUnitSnapshot());
            ps.setString(8, load.getStatus().name());
            ps.setDouble(9, load.getGrossAmount());
            ps.setString(10, load.getNotes());
            if (load.getDeliveryDate() != null)
                ps.setDate(11, java.sql.Date.valueOf(load.getDeliveryDate()));
            else
                ps.setNull(11, java.sql.Types.DATE);
            ps.setString(12, load.getReminder());
            ps.setInt(13, load.isHasLumper() ? 1 : 0);
            ps.setInt(14, load.isHasRevisedRateConfirmation() ? 1 : 0);
            ps.setInt(15, load.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Load updated successfully");
                // Save customer if not exists
                if (load.getCustomer() != null && !load.getCustomer().trim().isEmpty()) {
                    addCustomerIfNotExists(load.getCustomer().trim());
                }
            } else {
                logger.warn("No load found with ID: {}", load.getId());
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
                list.add(load);
            }
            logger.info("Retrieved {} loads for driver ID {}", list.size(), driverId);
        } catch (SQLException e) {
            logger.error("Error getting loads by driver: {}", e.getMessage(), e);
            throw new DataAccessException("Error getting loads by driver", e);
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

    public List<Load> search(String loadNum, String customer, Integer driverId, Load.Status status, String truckUnit, LocalDate startDate, LocalDate endDate) {
        logger.debug("Searching loads - LoadNum: {}, Customer: {}, DriverId: {}, Status: {}, TruckUnit: {}, StartDate: {}, EndDate: {}", 
            loadNum, customer, driverId, status, truckUnit, startDate, endDate);
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
        String pickUp = rs.getString("pick_up_location");
        String drop = rs.getString("drop_location");
        int driverId = rs.getInt("driver_id");
        Employee driver = employeeDAO.getById(driverId);
        
        String truckUnitSnapshot = "";
        try { truckUnitSnapshot = rs.getString("truck_unit_snapshot"); } catch (SQLException ex) { 
            truckUnitSnapshot = driver != null ? driver.getTruckUnit() : ""; 
        }
        
        Load.Status status = Load.Status.valueOf(rs.getString("status"));
        double gross = rs.getDouble("gross_amount");
        String notes = rs.getString("notes");
        LocalDate deliveryDate = null;
        java.sql.Date sqlDate = rs.getDate("delivery_date");
        if (sqlDate != null) deliveryDate = sqlDate.toLocalDate();
        
        String reminder = "";
        try { reminder = rs.getString("reminder"); } catch (SQLException ex) { reminder = ""; }
        
        boolean hasLumper = false;
        try { hasLumper = rs.getInt("has_lumper") == 1; } catch (SQLException ex) { hasLumper = false; }
        
        boolean hasRevisedRateConfirmation = false;
        try { hasRevisedRateConfirmation = rs.getInt("has_revised_rate_confirmation") == 1; } catch (SQLException ex) { hasRevisedRateConfirmation = false; }
        
        return new Load(id, loadNumber, poNumber, customer, pickUp, drop, driver, truckUnitSnapshot, status, gross, notes, deliveryDate, reminder, hasLumper, hasRevisedRateConfirmation);
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
}