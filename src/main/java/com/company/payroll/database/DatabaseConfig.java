package com.company.payroll.database;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Professional database configuration and connection management for SQLite.
 * Implements connection pooling, proper timeouts, and retry logic to prevent database locking issues.
 */
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    private static HikariDataSource dataSource;
    private static final Object INIT_LOCK = new Object();
    
    // Configuration constants
    private static final int MAX_POOL_SIZE = 10; // Allow multiple connections for initialization
    private static final int MIN_IDLE = 2; // Keep some connections ready
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds - faster failure detection
    private static final int BUSY_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500; // 500ms - faster retries
    
    static {
        initializeDataSource();
    }
    
    private static void initializeDataSource() {
        synchronized (INIT_LOCK) {
            if (dataSource != null && !dataSource.isClosed()) {
                return;
            }
            
            try {
                // Configure SQLite
                SQLiteConfig config = new SQLiteConfig();
                config.setBusyTimeout(BUSY_TIMEOUT);
                config.setJournalMode(SQLiteConfig.JournalMode.WAL); // Write-Ahead Logging for better concurrency
                config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
                config.setTempStore(SQLiteConfig.TempStore.MEMORY);
                config.setCacheSize(10000);
                config.setLockingMode(SQLiteConfig.LockingMode.NORMAL);
                
                // Create SQLite data source
                SQLiteDataSource sqliteDS = new SQLiteDataSource();
                sqliteDS.setUrl(DB_URL);
                sqliteDS.setConfig(config);
                
                // Configure HikariCP
                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setDataSource(sqliteDS);
                hikariConfig.setMaximumPoolSize(MAX_POOL_SIZE);
                hikariConfig.setMinimumIdle(MIN_IDLE);
                hikariConfig.setConnectionTimeout(CONNECTION_TIMEOUT);
                hikariConfig.setIdleTimeout(300000); // 5 minutes
                hikariConfig.setMaxLifetime(600000); // 10 minutes - shorter for SQLite
                hikariConfig.setPoolName("PayrollSQLitePool");
                hikariConfig.setAutoCommit(true);
                hikariConfig.setConnectionTestQuery("SELECT 1");
                hikariConfig.setValidationTimeout(2000); // 2 seconds
                hikariConfig.setLeakDetectionThreshold(60000); // 1 minute - detect connection leaks
                
                // Create the pool
                dataSource = new HikariDataSource(hikariConfig);
                
                // Initialize database with WAL mode
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT);
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA temp_store=MEMORY");
                    stmt.execute("PRAGMA cache_size=10000");
                    logger.info("Database connection pool initialized successfully with WAL mode");
                }
                
            } catch (Exception e) {
                logger.error("Failed to initialize database connection pool", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }
    }
    
    /**
     * Get a database connection from the pool.
     * @return Connection object
     * @throws SQLException if connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initializeDataSource();
        }
        return dataSource.getConnection();
    }
    
    /**
     * Execute a database operation with retry logic for handling SQLITE_BUSY errors.
     * @param operation The database operation to execute
     * @param <T> The return type
     * @return The result of the operation
     * @throws SQLException if the operation fails after all retries
     */
    public static <T> T executeWithRetry(DatabaseOperation<T> operation) throws SQLException {
        SQLException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.execute();
            } catch (SQLException e) {
                lastException = e;
                
                // Check if it's a busy/locked error
                if (e.getMessage() != null && 
                    (e.getMessage().contains("SQLITE_BUSY") || 
                     e.getMessage().contains("database is locked"))) {
                    
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        logger.warn("Database locked (attempt {}/{}), retrying in {}ms...", 
                                  attempt, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS);
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Interrupted while waiting for retry", ie);
                        }
                    } else {
                        logger.error("Database locked after {} attempts", MAX_RETRY_ATTEMPTS);
                    }
                } else {
                    // Not a busy error, throw immediately
                    throw e;
                }
            }
        }
        
        throw new SQLException("Operation failed after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }
    
    /**
     * Functional interface for database operations.
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }
    
    /**
     * Shutdown the connection pool.
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Shutting down database connection pool");
            dataSource.close();
        }
    }
    
    /**
     * Get current pool statistics for monitoring.
     */
    public static String getPoolStats() {
        if (dataSource != null && !dataSource.isClosed()) {
            return String.format("Active: %d, Idle: %d, Total: %d, Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
        }
        return "Pool not initialized";
    }
}