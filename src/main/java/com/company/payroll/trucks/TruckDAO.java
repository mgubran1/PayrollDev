package com.company.payroll.trucks;

import com.company.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data Access Object for Truck operations.
 * Handles database interactions for truck management.
 */
public class TruckDAO {
    private static final Logger logger = LoggerFactory.getLogger(TruckDAO.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    
    public TruckDAO() {
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        logger.info("Initializing Truck database");
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
             
            // Create trucks table
            String createTable = """
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
                    driver TEXT,
                    location TEXT,
                    assigned BOOLEAN DEFAULT 0,
                    current_job_id TEXT,
                    current_route TEXT,
                    current_latitude REAL,
                    current_longitude REAL,
                    current_speed REAL,
                    current_direction TEXT,
                    last_location_update TIMESTAMP,
                    gross_weight REAL,
                    horsepower INTEGER,
                    transmission_type TEXT,
                    axle_count INTEGER,
                    fuel_tank_capacity REAL,
                    engine_type TEXT,
                    sleeper TEXT,
                    emissions TEXT,
                    mileage INTEGER,
                    fuel_level REAL,
                    mpg REAL,
                    idle_time INTEGER,
                    fuel_used_total REAL,
                    total_miles_driven INTEGER,
                    average_speed REAL,
                    performance_score REAL,
                    hard_brake_count INTEGER,
                    hard_acceleration_count INTEGER,
                    last_service DATE,
                    next_service_due DATE,
                    current_condition TEXT,
                    maintenance_notes TEXT,
                    last_inspection_date DATE,
                    next_inspection_due DATE,
                    mileage_since_service INTEGER,
                    last_service_type TEXT,
                    in_maintenance BOOLEAN DEFAULT 0,
                    maintenance_count INTEGER DEFAULT 0,
                    ownership_type TEXT,
                    purchase_price REAL,
                    purchase_date DATE,
                    current_value REAL,
                    monthly_lease_cost REAL,
                    lease_details TEXT,
                    insurance_policy_number TEXT,
                    insurance_expiry_date DATE,
                    total_revenue REAL DEFAULT 0,
                    total_expenses REAL DEFAULT 0,
                    cost_per_mile REAL,
                    depreciation_schedule TEXT,
                    registration_expiry_date DATE,
                    permit_numbers TEXT,
                    dot_number TEXT,
                    last_state_inspection DATE,
                    ifta_compliant BOOLEAN DEFAULT 1,
                    eld_compliant BOOLEAN DEFAULT 1,
                    gps_device_id TEXT,
                    eld_device_id TEXT,
                    dashcam_id TEXT,
                    telematics_provider TEXT,
                    has_active_diagnostic_codes BOOLEAN DEFAULT 0,
                    active_diagnostic_codes TEXT,
                    temperature_monitoring BOOLEAN DEFAULT 0,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT DEFAULT 'mgubran1',
                    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    modified_by TEXT DEFAULT 'mgubran1',
                    notes TEXT
                )
            """;
            stmt.execute(createTable);
            
            // Create truck_performance table for historical performance records
            String createPerformanceTable = """
                CREATE TABLE IF NOT EXISTS truck_performance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    truck_id INTEGER,
                    truck_number TEXT NOT NULL,
                    driver_name TEXT,
                    period_start_date DATE,
                    period_end_date DATE,
                    miles_driven INTEGER,
                    fuel_used REAL,
                    mpg REAL,
                    idle_time INTEGER,
                    revenue REAL,
                    performance_score REAL,
                    maintenance_events INTEGER,
                    hard_brakes INTEGER,
                    hard_accelerations INTEGER,
                    average_speed REAL,
                    notes TEXT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (truck_id) REFERENCES trucks(id)
                )
            """;
            stmt.execute(createPerformanceTable);
            
            // Create maintenance_history table
            String createMaintenanceTable = """
                CREATE TABLE IF NOT EXISTS truck_maintenance_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    truck_id INTEGER,
                    truck_number TEXT NOT NULL,
                    service_date DATE,
                    service_type TEXT,
                    description TEXT,
                    mileage INTEGER,
                    cost REAL,
                    technician TEXT,
                    location TEXT,
                    parts_cost REAL,
                    labor_cost REAL,
                    notes TEXT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT DEFAULT 'mgubran1',
                    FOREIGN KEY (truck_id) REFERENCES trucks(id)
                )
            """;
            stmt.execute(createMaintenanceTable);
            
            // Create indexes for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_truck_number ON trucks(truck_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_truck_status ON trucks(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_truck_driver ON trucks(driver)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_truck_maintenance ON trucks(in_maintenance)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_truck_next_service ON trucks(next_service_due)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_truck_next_inspection ON trucks(next_inspection_due)");
            
            logger.info("Truck database tables initialized successfully");
            
        } catch (SQLException e) {
            logger.error("Failed to initialize truck database", e);
            throw new DataAccessException("Failed to initialize database", e);
        }
    }
    
    // CRUD Operations
    
    public Truck save(Truck truck) {
        if (truck.getId() > 0) {
            return update(truck);
        } else {
            return insert(truck);
        }
    }
    
    private Truck insert(Truck truck) {
        String sql = """
            INSERT INTO trucks (
                truck_number, vin, make, model, year, type, status, license_plate,
                driver, location, assigned, current_job_id, current_route,
                current_latitude, current_longitude, current_speed, current_direction,
                last_location_update, gross_weight, horsepower, transmission_type,
                axle_count, fuel_tank_capacity, engine_type, sleeper, emissions,
                mileage, fuel_level, mpg, idle_time, fuel_used_total, total_miles_driven,
                average_speed, performance_score, hard_brake_count, hard_acceleration_count,
                last_service, next_service_due, current_condition, maintenance_notes,
                last_inspection_date, next_inspection_due, mileage_since_service,
                last_service_type, in_maintenance, maintenance_count, ownership_type,
                purchase_price, purchase_date, current_value, monthly_lease_cost,
                lease_details, insurance_policy_number, insurance_expiry_date,
                total_revenue, total_expenses, cost_per_mile, depreciation_schedule,
                registration_expiry_date, permit_numbers, dot_number, last_state_inspection,
                ifta_compliant, eld_compliant, gps_device_id, eld_device_id, dashcam_id,
                telematics_provider, has_active_diagnostic_codes, active_diagnostic_codes,
                temperature_monitoring, created_by, modified_by, notes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                     ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                     ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
             
            setTruckParameters(pstmt, truck);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Creating truck failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    truck.setId(id);
                    logger.info("Created truck with ID: {}", id);
                }
            }
            
            return truck;
            
        } catch (SQLException e) {
            logger.error("Failed to insert truck: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to insert truck", e);
        }
    }
    
    private Truck update(Truck truck) {
        String sql = """
            UPDATE trucks SET
                truck_number = ?, vin = ?, make = ?, model = ?, year = ?, type = ?,
                status = ?, license_plate = ?, driver = ?, location = ?, assigned = ?,
                current_job_id = ?, current_route = ?, current_latitude = ?,
                current_longitude = ?, current_speed = ?, current_direction = ?,
                last_location_update = ?, gross_weight = ?, horsepower = ?,
                transmission_type = ?, axle_count = ?, fuel_tank_capacity = ?,
                engine_type = ?, sleeper = ?, emissions = ?, mileage = ?,
                fuel_level = ?, mpg = ?, idle_time = ?, fuel_used_total = ?,
                total_miles_driven = ?, average_speed = ?, performance_score = ?,
                hard_brake_count = ?, hard_acceleration_count = ?, last_service = ?,
                next_service_due = ?, current_condition = ?, maintenance_notes = ?,
                last_inspection_date = ?, next_inspection_due = ?, mileage_since_service = ?,
                last_service_type = ?, in_maintenance = ?, maintenance_count = ?,
                ownership_type = ?, purchase_price = ?, purchase_date = ?,
                current_value = ?, monthly_lease_cost = ?, lease_details = ?,
                insurance_policy_number = ?, insurance_expiry_date = ?, total_revenue = ?,
                total_expenses = ?, cost_per_mile = ?, depreciation_schedule = ?,
                registration_expiry_date = ?, permit_numbers = ?, dot_number = ?,
                last_state_inspection = ?, ifta_compliant = ?, eld_compliant = ?,
                gps_device_id = ?, eld_device_id = ?, dashcam_id = ?, telematics_provider = ?,
                has_active_diagnostic_codes = ?, active_diagnostic_codes = ?,
                temperature_monitoring = ?, modified_date = CURRENT_TIMESTAMP,
                modified_by = ?, notes = ?
            WHERE id = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            setTruckParameters(pstmt, truck);
            pstmt.setInt(76, truck.getId());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Updating truck failed, no rows affected.");
            }
            
            logger.info("Updated truck with ID: {}", truck.getId());
            return truck;
            
        } catch (SQLException e) {
            logger.error("Failed to update truck: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to update truck", e);
        }
    }
    
    public void delete(int id) {
        String sql = "DELETE FROM trucks WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new DataAccessException("Deleting truck failed, no truck with ID: " + id);
            }
            
            logger.info("Deleted truck with ID: {}", id);
            
        } catch (SQLException e) {
            logger.error("Failed to delete truck: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to delete truck", e);
        }
    }
    
    // Query Operations
    
    public Truck findById(int id) {
        String sql = "SELECT * FROM trucks WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTruck(rs);
                }
            }
            
            return null;
            
        } catch (SQLException e) {
            logger.error("Failed to find truck by ID: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find truck", e);
        }
    }
    
    public Truck findByTruckNumber(String truckNumber) {
        String sql = "SELECT * FROM trucks WHERE truck_number = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, truckNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTruck(rs);
                }
            }
            
            return null;
            
        } catch (SQLException e) {
            logger.error("Failed to find truck by truck number: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find truck", e);
        }
    }
    
    public List<Truck> findAll() {
        String sql = "SELECT * FROM trucks ORDER BY truck_number";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            List<Truck> trucks = new ArrayList<>();
            while (rs.next()) {
                trucks.add(mapResultSetToTruck(rs));
            }
            
            return trucks;
            
        } catch (SQLException e) {
            logger.error("Failed to find all trucks: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findByStatus(String status) {
        String sql = "SELECT * FROM trucks WHERE status = ? ORDER BY truck_number";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, status);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks by status: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findByDriver(String driverName) {
        String sql = "SELECT * FROM trucks WHERE driver = ? ORDER BY truck_number";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, driverName);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks by driver: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findByLocation(String location) {
        String sql = "SELECT * FROM trucks WHERE location LIKE ? ORDER BY truck_number";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, "%" + location + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks by location: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findByType(String type) {
        String sql = "SELECT * FROM trucks WHERE type = ? ORDER BY truck_number";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, type);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks by type: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findAvailable() {
        String sql = "SELECT * FROM trucks WHERE assigned = 0 AND status NOT IN ('Maintenance', 'Out of Service') ORDER BY truck_number";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            List<Truck> trucks = new ArrayList<>();
            while (rs.next()) {
                trucks.add(mapResultSetToTruck(rs));
            }
            return trucks;
            
        } catch (SQLException e) {
            logger.error("Failed to find available trucks: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find available trucks", e);
        }
    }
    
    public List<Truck> findDueForService(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trucks 
            WHERE next_service_due IS NOT NULL 
            AND next_service_due <= ? 
            ORDER BY next_service_due
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDate(1, Date.valueOf(cutoffDate));
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks due for service: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
        public List<Truck> findDueForInspection(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trucks 
            WHERE next_inspection_due IS NOT NULL 
            AND next_inspection_due <= ? 
            ORDER BY next_inspection_due
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDate(1, Date.valueOf(cutoffDate));
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks due for inspection: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findByExpiringRegistration(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trucks 
            WHERE registration_expiry_date IS NOT NULL 
            AND registration_expiry_date <= ? 
            ORDER BY registration_expiry_date
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDate(1, Date.valueOf(cutoffDate));
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks with expiring registration: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findByExpiringInsurance(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM trucks 
            WHERE insurance_expiry_date IS NOT NULL 
            AND insurance_expiry_date <= ? 
            ORDER BY insurance_expiry_date
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDate(1, Date.valueOf(cutoffDate));
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks with expiring insurance: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> search(String searchTerm) {
        String sql = """
            SELECT * FROM trucks 
            WHERE truck_number LIKE ? 
               OR vin LIKE ? 
               OR make LIKE ? 
               OR model LIKE ? 
               OR driver LIKE ?
               OR location LIKE ?
               OR license_plate LIKE ? 
               OR notes LIKE ?
            ORDER BY truck_number
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            String pattern = "%" + searchTerm + "%";
            for (int i = 1; i <= 8; i++) {
                pstmt.setString(i, pattern);
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Truck> trucks = new ArrayList<>();
                while (rs.next()) {
                    trucks.add(mapResultSetToTruck(rs));
                }
                return trucks;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to search trucks: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to search trucks", e);
        }
    }
    
    public List<Truck> findWithActiveDiagnosticCodes() {
        String sql = "SELECT * FROM trucks WHERE has_active_diagnostic_codes = 1";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            List<Truck> trucks = new ArrayList<>();
            while (rs.next()) {
                trucks.add(mapResultSetToTruck(rs));
            }
            return trucks;
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks with diagnostic codes: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public List<Truck> findInMaintenance() {
        String sql = "SELECT * FROM trucks WHERE in_maintenance = 1 OR status = 'Maintenance'";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            List<Truck> trucks = new ArrayList<>();
            while (rs.next()) {
                trucks.add(mapResultSetToTruck(rs));
            }
            return trucks;
            
        } catch (SQLException e) {
            logger.error("Failed to find trucks in maintenance: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to find trucks", e);
        }
    }
    
    public void recordMaintenanceEvent(int truckId, String truckNumber, LocalDate serviceDate, 
                                      String serviceType, String description, int mileage, 
                                      double cost, String technician) {
        String sql = """
            INSERT INTO truck_maintenance_history (
                truck_id, truck_number, service_date, service_type, description, 
                mileage, cost, technician, location
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, truckId);
            pstmt.setString(2, truckNumber);
            pstmt.setDate(3, Date.valueOf(serviceDate));
            pstmt.setString(4, serviceType);
            pstmt.setString(5, description);
            pstmt.setInt(6, mileage);
            pstmt.setDouble(7, cost);
            pstmt.setString(8, technician);
            pstmt.setString(9, "Service Center");
            
            pstmt.executeUpdate();
            
            // Update the truck's last service date and related fields
            String updateSql = """
                UPDATE trucks SET 
                    last_service = ?, 
                    next_service_due = ?, 
                    last_service_type = ?,
                    mileage_since_service = 0,
                    maintenance_count = maintenance_count + 1,
                    in_maintenance = 0,
                    status = 'Active'
                WHERE id = ?
            """;
            
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setDate(1, Date.valueOf(serviceDate));
                updateStmt.setDate(2, Date.valueOf(serviceDate.plusDays(90)));
                updateStmt.setString(3, serviceType);
                updateStmt.setInt(4, truckId);
                updateStmt.executeUpdate();
            }
            
            logger.info("Recorded maintenance for truck ID: {}, Type: {}", truckId, serviceType);
            
        } catch (SQLException e) {
            logger.error("Failed to record maintenance event: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to record maintenance", e);
        }
    }
    
    public void updateTruckLocation(String truckNumber, double latitude, double longitude, 
                                    double speed, String direction, String location) {
        String sql = """
            UPDATE trucks SET 
                current_latitude = ?, 
                current_longitude = ?, 
                current_speed = ?,
                current_direction = ?,
                location = ?,
                last_location_update = ?
            WHERE truck_number = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDouble(1, latitude);
            pstmt.setDouble(2, longitude);
            pstmt.setDouble(3, speed);
            pstmt.setString(4, direction);
            pstmt.setString(5, location);
            pstmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setString(7, truckNumber);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Updating truck location failed, no truck found with number: " + truckNumber);
            }
            
            logger.info("Updated location for truck: {}", truckNumber);
            
        } catch (SQLException e) {
            logger.error("Failed to update truck location: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to update truck location", e);
        }
    }
    
    public void assignDriver(String truckNumber, String driverName) {
        String sql = """
            UPDATE trucks SET 
                driver = ?, 
                assigned = ?,
                status = ?,
                modified_date = CURRENT_TIMESTAMP
            WHERE truck_number = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            boolean assigned = driverName != null && !driverName.isEmpty();
            String status = assigned ? "Active" : "Available";
            
            pstmt.setString(1, driverName);
            pstmt.setBoolean(2, assigned);
            pstmt.setString(3, status);
            pstmt.setString(4, truckNumber);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Assigning driver failed, no truck found with number: " + truckNumber);
            }
            
            logger.info("Assigned driver: {} to truck: {}", driverName, truckNumber);
            
        } catch (SQLException e) {
            logger.error("Failed to assign driver: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to assign driver", e);
        }
    }
    
    public void updateMileage(String truckNumber, int newMileage) {
        String sql = """
            UPDATE trucks SET 
                mileage = ?, 
                mileage_since_service = mileage_since_service + (? - mileage),
                total_miles_driven = ?,
                modified_date = CURRENT_TIMESTAMP
            WHERE truck_number = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            // Get current mileage and total miles first
            Truck truck = findByTruckNumber(truckNumber);
            if (truck == null) {
                throw new DataAccessException("No truck found with number: " + truckNumber);
            }
            
            int mileageDifference = newMileage - truck.getMileage();
            if (mileageDifference < 0) {
                logger.warn("New mileage is less than current mileage for truck: {}", truckNumber);
                return;
            }
            
            int newTotalMiles = truck.getTotalMilesDriven() + mileageDifference;
            
            pstmt.setInt(1, newMileage);
            pstmt.setInt(2, newMileage);
            pstmt.setInt(3, newTotalMiles);
            pstmt.setString(4, truckNumber);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Updating mileage failed, no truck found with number: " + truckNumber);
            }
            
            logger.info("Updated mileage for truck: {} to {}", truckNumber, newMileage);
            
        } catch (SQLException e) {
            logger.error("Failed to update mileage: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to update mileage", e);
        }
    }
    
    public void updateFuelLevel(String truckNumber, double fuelLevel) {
        String sql = """
            UPDATE trucks SET 
                fuel_level = ?, 
                modified_date = CURRENT_TIMESTAMP
            WHERE truck_number = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDouble(1, fuelLevel);
            pstmt.setString(2, truckNumber);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Updating fuel level failed, no truck found with number: " + truckNumber);
            }
            
            logger.info("Updated fuel level for truck: {} to {}", truckNumber, fuelLevel);
            
        } catch (SQLException e) {
            logger.error("Failed to update fuel level: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to update fuel level", e);
        }
    }
    
    public void setMaintenanceStatus(String truckNumber, boolean inMaintenance, String notes) {
        String sql = """
            UPDATE trucks SET 
                in_maintenance = ?, 
                status = ?,
                maintenance_notes = ?,
                modified_date = CURRENT_TIMESTAMP
            WHERE truck_number = ?
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            String status = inMaintenance ? "Maintenance" : "Active";
            
            pstmt.setBoolean(1, inMaintenance);
            pstmt.setString(2, status);
            pstmt.setString(3, notes);
            pstmt.setString(4, truckNumber);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DataAccessException("Setting maintenance status failed, no truck found with number: " + truckNumber);
            }
            
            logger.info("Set maintenance status for truck: {} to {}", truckNumber, inMaintenance);
            
        } catch (SQLException e) {
            logger.error("Failed to set maintenance status: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to set maintenance status", e);
        }
    }
    
    public void recordPerformanceData(TrucksTab.TruckPerformance performance) {
        String sql = """
            INSERT INTO truck_performance (
                truck_number, driver_name, period_start_date, period_end_date,
                miles_driven, fuel_used, mpg, idle_time, revenue, performance_score
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30); // Default to last 30 days
            
            pstmt.setString(1, performance.getTruckNumber());
            pstmt.setString(2, performance.getDriverName());
            pstmt.setDate(3, Date.valueOf(startDate));
            pstmt.setDate(4, Date.valueOf(endDate));
            pstmt.setInt(5, performance.getMilesDriven());
            pstmt.setDouble(6, performance.getFuelUsed());
            pstmt.setDouble(7, performance.getMpg());
            pstmt.setInt(8, performance.getIdleTime());
            pstmt.setDouble(9, performance.getRevenue());
            pstmt.setDouble(10, performance.getPerformanceScore());
            
            pstmt.executeUpdate();
            
            // Update truck's performance metrics
            String updateSql = """
                UPDATE trucks SET 
                    mpg = ?,
                    performance_score = ?,
                    idle_time = ?,
                    total_revenue = total_revenue + ?
                WHERE truck_number = ?
            """;
            
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setDouble(1, performance.getMpg());
                updateStmt.setDouble(2, performance.getPerformanceScore());
                updateStmt.setInt(3, performance.getIdleTime());
                updateStmt.setDouble(4, performance.getRevenue());
                updateStmt.setString(5, performance.getTruckNumber());
                updateStmt.executeUpdate();
            }
            
            logger.info("Recorded performance data for truck: {}", performance.getTruckNumber());
            
        } catch (SQLException e) {
            logger.error("Failed to record performance data: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to record performance data", e);
        }
    }
    
    public List<TrucksTab.TruckPerformance> getPerformanceData(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                t.truck_number,
                t.driver,
                SUM(tp.miles_driven) as total_miles_driven,
                SUM(tp.fuel_used) as total_fuel_used,
                AVG(tp.mpg) as avg_mpg,
                SUM(tp.idle_time) as total_idle_time,
                SUM(tp.revenue) as total_revenue,
                AVG(tp.performance_score) as avg_performance_score
            FROM trucks t
            LEFT JOIN truck_performance tp ON t.truck_number = tp.truck_number
            WHERE tp.period_start_date >= ? AND tp.period_end_date <= ?
            GROUP BY t.truck_number, t.driver
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TrucksTab.TruckPerformance> performances = new ArrayList<>();
                while (rs.next()) {
                    TrucksTab.TruckPerformance perf = new TrucksTab.TruckPerformance();
                    perf.setTruckNumber(rs.getString("truck_number"));
                    perf.setDriverName(rs.getString("driver"));
                    perf.setMilesDriven(rs.getInt("total_miles_driven"));
                    perf.setFuelUsed(rs.getDouble("total_fuel_used"));
                    perf.setMpg(rs.getDouble("avg_mpg"));
                    perf.setIdleTime(rs.getInt("total_idle_time"));
                    perf.setRevenue(rs.getDouble("total_revenue"));
                    perf.setPerformanceScore(rs.getDouble("avg_performance_score"));
                    performances.add(perf);
                }
                return performances;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get performance data: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get performance data", e);
        }
    }
    
    // Utility methods
    
    public int getTrucksCount() {
        String sql = "SELECT COUNT(*) FROM trucks";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get trucks count: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get trucks count", e);
        }
    }
    
    public int getActiveTrucksCount() {
        String sql = "SELECT COUNT(*) FROM trucks WHERE status = 'Active' OR status = 'In Transit'";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get active trucks count: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get active trucks count", e);
        }
    }
    
    public int getMaintenanceTrucksCount() {
        String sql = "SELECT COUNT(*) FROM trucks WHERE status = 'Maintenance' OR in_maintenance = 1";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get maintenance trucks count: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get maintenance trucks count", e);
        }
    }
    
    public int getIdleTrucksCount() {
        String sql = "SELECT COUNT(*) FROM trucks WHERE status = 'Idle' OR status = 'At Rest' OR status = 'Available'";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get idle trucks count: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get idle trucks count", e);
        }
    }
    
    public double getAverageMileage() {
        String sql = "SELECT AVG(mileage) FROM trucks";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getDouble(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get average mileage: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get average mileage", e);
        }
    }
    
    public double getAverageFuelEfficiency() {
        String sql = "SELECT AVG(mpg) FROM trucks WHERE mpg > 0";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            if (rs.next()) {
                return rs.getDouble(1);
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to get average fuel efficiency: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to get average fuel efficiency", e);
        }
    }
    
    // Helper methods
    
    private void setTruckParameters(PreparedStatement pstmt, Truck truck) throws SQLException {
        pstmt.setString(1, truck.getNumber());
        pstmt.setString(2, truck.getVin());
        pstmt.setString(3, truck.getMake());
        pstmt.setString(4, truck.getModel());
        pstmt.setInt(5, truck.getYear());
        pstmt.setString(6, truck.getType());
        pstmt.setString(7, truck.getStatus());
        pstmt.setString(8, truck.getLicensePlate());
        pstmt.setString(9, truck.getDriver());
        pstmt.setString(10, truck.getLocation());
        pstmt.setBoolean(11, truck.isAssigned());
        pstmt.setString(12, truck.getCurrentJobId());
        pstmt.setString(13, truck.getCurrentRoute());
        pstmt.setDouble(14, truck.getCurrentLatitude());
        pstmt.setDouble(15, truck.getCurrentLongitude());
        pstmt.setDouble(16, truck.getCurrentSpeed());
        pstmt.setString(17, truck.getCurrentDirection());
        
        LocalDateTime lastUpdate = truck.getLastLocationUpdate();
        pstmt.setTimestamp(18, lastUpdate != null ? Timestamp.valueOf(lastUpdate) : null);
        
        pstmt.setDouble(19, truck.getGrossWeight());
        pstmt.setInt(20, truck.getHorsepower());
        pstmt.setString(21, truck.getTransmissionType());
        pstmt.setInt(22, truck.getAxleCount());
        pstmt.setDouble(23, truck.getFuelTankCapacity());
        pstmt.setString(24, truck.getEngineType());
        pstmt.setString(25, truck.getSleeper());
        pstmt.setString(26, truck.getEmissions());
        pstmt.setInt(27, truck.getMileage());
        pstmt.setDouble(28, truck.getFuelLevel());
        pstmt.setDouble(29, truck.getMpg());
        pstmt.setInt(30, truck.getIdleTime());
        pstmt.setDouble(31, truck.getFuelUsedTotal());
        pstmt.setInt(32, truck.getTotalMilesDriven());
        pstmt.setDouble(33, truck.getAverageSpeed());
        pstmt.setDouble(34, truck.getPerformanceScore());
        pstmt.setInt(35, truck.getHardBrakeCount());
        pstmt.setInt(36, truck.getHardAccelerationCount());
        
        LocalDate lastService = truck.getLastService();
        pstmt.setDate(37, lastService != null ? Date.valueOf(lastService) : null);
        
        LocalDate nextService = truck.getNextServiceDue();
        pstmt.setDate(38, nextService != null ? Date.valueOf(nextService) : null);
        
        pstmt.setString(39, truck.getCurrentCondition());
        pstmt.setString(40, truck.getMaintenanceNotes());
        
        LocalDate lastInspection = truck.getLastInspectionDate();
        pstmt.setDate(41, lastInspection != null ? Date.valueOf(lastInspection) : null);
        
        LocalDate nextInspection = truck.getNextInspectionDue();
        pstmt.setDate(42, nextInspection != null ? Date.valueOf(nextInspection) : null);
        
        pstmt.setInt(43, truck.getMileageSinceService());
        pstmt.setString(44, truck.getLastServiceType());
        pstmt.setBoolean(45, truck.isInMaintenance());
        pstmt.setInt(46, truck.getMaintenanceCount());
        pstmt.setString(47, truck.getOwnershipType());
        pstmt.setDouble(48, truck.getPurchasePrice());
        
        LocalDate purchaseDate = truck.getPurchaseDate();
        pstmt.setDate(49, purchaseDate != null ? Date.valueOf(purchaseDate) : null);
        
        pstmt.setDouble(50, truck.getCurrentValue());
        pstmt.setDouble(51, truck.getMonthlyLeaseCost());
        pstmt.setString(52, truck.getLeaseDetails());
        pstmt.setString(53, truck.getInsurancePolicyNumber());
        
        LocalDate insuranceExpiry = truck.getInsuranceExpiryDate();
        pstmt.setDate(54, insuranceExpiry != null ? Date.valueOf(insuranceExpiry) : null);
        
        pstmt.setDouble(55, truck.getTotalRevenue());
        pstmt.setDouble(56, truck.getTotalExpenses());
        pstmt.setDouble(57, truck.getCostPerMile());
        pstmt.setString(58, truck.getDepreciationSchedule());
        
        LocalDate regExpiry = truck.getRegistrationExpiryDate();
        pstmt.setDate(59, regExpiry != null ? Date.valueOf(regExpiry) : null);
        
        pstmt.setString(60, truck.getPermitNumbers());
        pstmt.setString(61, truck.getDotNumber());
        
        LocalDate stateInspection = truck.getLastStateInspection();
        pstmt.setDate(62, stateInspection != null ? Date.valueOf(stateInspection) : null);
        
        pstmt.setBoolean(63, truck.isIftaCompliant());
        pstmt.setBoolean(64, truck.isEldCompliant());
        pstmt.setString(65, truck.getGpsDeviceId());
        pstmt.setString(66, truck.getEldDeviceId());
        pstmt.setString(67, truck.getDashcamId());
        pstmt.setString(68, truck.getTelematicsProvider());
        pstmt.setBoolean(69, truck.isHasActiveDiagnosticCodes());
        pstmt.setString(70, truck.getActiveDiagnosticCodes());
        pstmt.setBoolean(71, truck.isTemperatureMonitoring());
        pstmt.setString(72, truck.getCreatedBy());
        pstmt.setString(73, truck.getModifiedBy());
        pstmt.setString(74, truck.getNotes());
    }
    
    private Truck mapResultSetToTruck(ResultSet rs) throws SQLException {
        Truck truck = new Truck();
        
        truck.setId(rs.getInt("id"));
        truck.setNumber(rs.getString("truck_number"));
        truck.setVin(rs.getString("vin"));
        truck.setMake(rs.getString("make"));
        truck.setModel(rs.getString("model"));
        truck.setYear(rs.getInt("year"));
        truck.setType(rs.getString("type"));
        truck.setStatus(rs.getString("status"));
        truck.setLicensePlate(rs.getString("license_plate"));
        truck.setDriver(rs.getString("driver"));
        truck.setLocation(rs.getString("location"));
        truck.setAssigned(rs.getBoolean("assigned"));
        truck.setCurrentJobId(rs.getString("current_job_id"));
        truck.setCurrentRoute(rs.getString("current_route"));
        truck.setCurrentLatitude(rs.getDouble("current_latitude"));
        truck.setCurrentLongitude(rs.getDouble("current_longitude"));
        truck.setCurrentSpeed(rs.getDouble("current_speed"));
        truck.setCurrentDirection(rs.getString("current_direction"));
        
        Timestamp lastLocationTs = rs.getTimestamp("last_location_update");
        if (lastLocationTs != null) {
            truck.setLastLocationUpdate(lastLocationTs.toLocalDateTime());
        }
        
        truck.setGrossWeight(rs.getDouble("gross_weight"));
        truck.setHorsepower(rs.getInt("horsepower"));
        truck.setTransmissionType(rs.getString("transmission_type"));
        truck.setAxleCount(rs.getInt("axle_count"));
        truck.setFuelTankCapacity(rs.getDouble("fuel_tank_capacity"));
        truck.setEngineType(rs.getString("engine_type"));
        truck.setSleeper(rs.getString("sleeper"));
        truck.setEmissions(rs.getString("emissions"));
        truck.setMileage(rs.getInt("mileage"));
        truck.setFuelLevel(rs.getDouble("fuel_level"));
        truck.setMpg(rs.getDouble("mpg"));
        truck.setIdleTime(rs.getInt("idle_time"));
        truck.setFuelUsedTotal(rs.getDouble("fuel_used_total"));
        truck.setTotalMilesDriven(rs.getInt("total_miles_driven"));
        truck.setAverageSpeed(rs.getDouble("average_speed"));
        truck.setPerformanceScore(rs.getDouble("performance_score"));
        truck.setHardBrakeCount(rs.getInt("hard_brake_count"));
        truck.setHardAccelerationCount(rs.getInt("hard_acceleration_count"));
        
        Date lastService = rs.getDate("last_service");
        if (lastService != null) {
            truck.setLastService(lastService.toLocalDate());
        }
        
        Date nextService = rs.getDate("next_service_due");
        if (nextService != null) {
            truck.setNextServiceDue(nextService.toLocalDate());
        }
        
        truck.setCurrentCondition(rs.getString("current_condition"));
        truck.setMaintenanceNotes(rs.getString("maintenance_notes"));
        
        Date lastInspection = rs.getDate("last_inspection_date");
        if (lastInspection != null) {
            truck.setLastInspectionDate(lastInspection.toLocalDate());
        }
        
        Date nextInspection = rs.getDate("next_inspection_due");
        if (nextInspection != null) {
            truck.setNextInspectionDue(nextInspection.toLocalDate());
        }
        
        truck.setMileageSinceService(rs.getInt("mileage_since_service"));
        truck.setLastServiceType(rs.getString("last_service_type"));
        truck.setInMaintenance(rs.getBoolean("in_maintenance"));
        truck.setMaintenanceCount(rs.getInt("maintenance_count"));
        truck.setOwnershipType(rs.getString("ownership_type"));
        truck.setPurchasePrice(rs.getDouble("purchase_price"));
        
        Date purchaseDate = rs.getDate("purchase_date");
        if (purchaseDate != null) {
            truck.setPurchaseDate(purchaseDate.toLocalDate());
        }
        
        truck.setCurrentValue(rs.getDouble("current_value"));
        truck.setMonthlyLeaseCost(rs.getDouble("monthly_lease_cost"));
        truck.setLeaseDetails(rs.getString("lease_details"));
        truck.setInsurancePolicyNumber(rs.getString("insurance_policy_number"));
        
        Date insuranceExpiry = rs.getDate("insurance_expiry_date");
        if (insuranceExpiry != null) {
            truck.setInsuranceExpiryDate(insuranceExpiry.toLocalDate());
        }
        
        truck.setTotalRevenue(rs.getDouble("total_revenue"));
        truck.setTotalExpenses(rs.getDouble("total_expenses"));
        truck.setCostPerMile(rs.getDouble("cost_per_mile"));
        truck.setDepreciationSchedule(rs.getString("depreciation_schedule"));
        
        Date regExpiry = rs.getDate("registration_expiry_date");
        if (regExpiry != null) {
            truck.setRegistrationExpiryDate(regExpiry.toLocalDate());
        }
        
        truck.setPermitNumbers(rs.getString("permit_numbers"));
        truck.setDotNumber(rs.getString("dot_number"));
        
        Date stateInspection = rs.getDate("last_state_inspection");
        if (stateInspection != null) {
            truck.setLastStateInspection(stateInspection.toLocalDate());
        }
        
        truck.setIftaCompliant(rs.getBoolean("ifta_compliant"));
        truck.setEldCompliant(rs.getBoolean("eld_compliant"));
        truck.setGpsDeviceId(rs.getString("gps_device_id"));
        truck.setEldDeviceId(rs.getString("eld_device_id"));
        truck.setDashcamId(rs.getString("dashcam_id"));
        truck.setTelematicsProvider(rs.getString("telematics_provider"));
        truck.setHasActiveDiagnosticCodes(rs.getBoolean("has_active_diagnostic_codes"));
        truck.setActiveDiagnosticCodes(rs.getString("active_diagnostic_codes"));
        truck.setTemperatureMonitoring(rs.getBoolean("temperature_monitoring"));
        
        Timestamp createdTs = rs.getTimestamp("created_date");
        if (createdTs != null) {
            truck.setCreatedDate(createdTs.toLocalDateTime());
        }
        
        truck.setCreatedBy(rs.getString("created_by"));
        
        Timestamp modifiedTs = rs.getTimestamp("modified_date");
        if (modifiedTs != null) {
            truck.setModifiedDate(modifiedTs.toLocalDateTime());
        }
        
        truck.setModifiedBy(rs.getString("modified_by"));
        truck.setNotes(rs.getString("notes"));
        
        return truck;
    }
    
    // Generate sample data for testing
    public void generateSampleData() {
        if (getTrucksCount() > 0) {
            logger.info("Sample truck data already exists, skipping generation");
            return;
        }
        
        logger.info("Generating sample truck data");
        
        try {
            String[] makes = {"Freightliner", "Peterbilt", "Kenworth", "Volvo", "International"};
            String[] models = {"Cascadia", "579", "T680", "VNL", "LT"};
            String[] types = {"Freightliner Cascadia", "Peterbilt 579", "Kenworth T680", 
                           "Volvo VNL", "International LT"};
            String[] drivers = {"John Smith", "Jane Doe", "Mike Johnson", "Sarah Williams", 
                              "Robert Brown", "", ""};
            String[] locations = {"Highway I-95 North", "Warehouse A", "Customer Site - NYC",
                                "Rest Area Mile 45", "Downtown Terminal", "Main Depot"};
            String[] statuses = {"Active", "Idle", "In Transit", "Maintenance", "At Rest"};
            
            for (int i = 1; i <= 30; i++) {
                int yearOffset = (int)(Math.random() * 5);
                int makeIndex = (int)(Math.random() * makes.length);
                
                Truck truck = new Truck();
                truck.setNumber("T" + String.format("%04d", 1000 + i));
                truck.setVin("1FUJA6CV" + i + "LM" + (10000 + i));
                truck.setMake(makes[makeIndex]);
                truck.setModel(models[makeIndex]);
                truck.setYear(2020 + yearOffset);
                truck.setType(types[makeIndex]);
                
                // Set status - bias toward active status
                String status = statuses[(int)(Math.random() * (Math.random() < 0.7 ? 1 : statuses.length))];
                truck.setStatus(status);
                
                truck.setLicensePlate("TR" + (1000 + i));
                
                // Assign drivers to some trucks
                if (i % 3 != 0) {
                    truck.setDriver(drivers[(int)(Math.random() * 5)]);  // Higher chance of having driver
                    truck.setAssigned(true);
                }
                
                truck.setLocation(locations[(int)(Math.random() * locations.length)]);
                truck.setMileage(50000 + (int)(Math.random() * 200000));
                truck.setFuelLevel(20 + Math.random() * 80);
                
                // Last service date
                int serviceOffset = (int)(Math.random() * 120);
                truck.setLastService(LocalDate.now().minusDays(serviceOffset));
                
                // Set next service due based on last service
                truck.setNextServiceDue(LocalDate.now().minusDays(serviceOffset).plusDays(90));
                
                // Current condition
                truck.setCurrentCondition(Math.random() > 0.8 ? "Needs Attention" : "Good");
                
                // More specific truck details
                truck.setGrossWeight(33000 + Math.random() * 12000);
                truck.setHorsepower(400 + (int)(Math.random() * 200));
                truck.setTransmissionType(Math.random() > 0.5 ? "Automatic" : "Manual");
                truck.setAxleCount(Math.random() > 0.7 ? 3 : 2);
                truck.setFuelTankCapacity(150 + Math.random() * 100);
                truck.setEngineType("Diesel");
                truck.setSleeper(Math.random() > 0.3 ? "Yes" : "No");
                
                // Performance metrics
                truck.setMpg(5.5 + Math.random() * 3);
                truck.setIdleTime(10 + (int)(Math.random() * 50));
                truck.setFuelUsedTotal(1000 + Math.random() * 5000);
                truck.setTotalMilesDriven(truck.getMileage());
                truck.setAverageSpeed(55 + Math.random() * 10);
                truck.setPerformanceScore(70 + Math.random() * 30);
                
                // Ownership info
                truck.setOwnershipType(Math.random() > 0.2 ? "Company" : "Leased");
                truck.setPurchasePrice(120000 + Math.random() * 30000);
                truck.setPurchaseDate(LocalDate.now().minusYears(1 + (int)(Math.random() * 4)));
                truck.setCurrentValue(100000 - yearOffset * 10000 - (int)(Math.random() * 15000));
                
                if ("Leased".equals(truck.getOwnershipType())) {
                    truck.setMonthlyLeaseCost(1800 + Math.random() * 800);
                    truck.setLeaseDetails("36 month lease from TransLease Inc.");
                }
                
                // Insurance and registration
                truck.setInsurancePolicyNumber("INS-" + (10000 + i));
                truck.setInsuranceExpiryDate(LocalDate.now().plusMonths(1 + (int)(Math.random() * 11)));
                truck.setRegistrationExpiryDate(LocalDate.now().plusMonths(2 + (int)(Math.random() * 10)));
                
                // Safety and compliance
                truck.setDotNumber("DOT" + (100000 + i));
                truck.setLastInspectionDate(LocalDate.now().minusMonths((int)(Math.random() * 11)));
                truck.setNextInspectionDue(LocalDate.now().plusMonths(1 + (int)(Math.random() * 11)));
                truck.setIftaCompliant(Math.random() > 0.1);
                truck.setEldCompliant(Math.random() > 0.05);
                
                // Maintenance related
                truck.setMileageSinceService((int)(Math.random() * 10000));
                truck.setLastServiceType(Math.random() > 0.5 ? "Full Service" : "Oil Change");
                
                if (status.equals("Maintenance")) {
                    truck.setInMaintenance(true);
                    truck.setMaintenanceNotes("In shop for " + 
                        (Math.random() > 0.5 ? "routine maintenance" : "brake service"));
                }
                
                truck.setMaintenanceCount((int)(Math.random() * 10));
                
                // Save the truck
                save(truck);
                
                // Create some performance records for each truck
                for (int j = 0; j < 3; j++) {
                    TrucksTab.TruckPerformance perf = new TrucksTab.TruckPerformance();
                    perf.setTruckNumber(truck.getNumber());
                    perf.setDriverName(truck.getDriver());
                    perf.setMilesDriven(1000 + (int)(Math.random() * 5000));
                    perf.setFuelUsed(perf.getMilesDriven() / (5.5 + Math.random() * 3));
                    perf.setMpg(perf.getMilesDriven() / perf.getFuelUsed());
                    perf.setIdleTime(10 + (int)(Math.random() * 50));
                    perf.setRevenue(perf.getMilesDriven() * (1.5 + Math.random()));
                    perf.setPerformanceScore(60 + Math.random() * 40);
                    
                    recordPerformanceData(perf);
                }
            }
            
            logger.info("Generated sample data for {} trucks", 30);
            
        } catch (Exception e) {
            logger.error("Failed to generate sample truck data: {}", e.getMessage(), e);
            throw new DataAccessException("Failed to generate sample data", e);
        }
    }
}