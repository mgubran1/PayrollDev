package com.company.payroll.util;

import java.sql.*;

public class DatabaseChecker {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:payroll.db";
        
        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected to database successfully!");
            
            // Get all tables
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "%", new String[] {"TABLE"});
            
            System.out.println("\nExisting tables:");
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                System.out.println("- " + tableName);
                
                // Check columns for important tables
                if (tableName.equals("employee_percentage_history") || 
                    tableName.equals("payroll_history") ||
                    tableName.equals("migrations")) {
                    System.out.println("  Columns:");
                    ResultSet columns = meta.getColumns(null, null, tableName, null);
                    while (columns.next()) {
                        System.out.println("    - " + columns.getString("COLUMN_NAME") + 
                                         " (" + columns.getString("TYPE_NAME") + ")");
                    }
                }
            }
            
            // Check if migrations table has entries
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT * FROM migrations");
                System.out.println("\nMigrations applied:");
                while (rs.next()) {
                    System.out.println("- " + rs.getString("migration_name") + 
                                     " at " + rs.getString("applied_at"));
                }
            } catch (SQLException e) {
                System.out.println("No migrations table found");
            }
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}