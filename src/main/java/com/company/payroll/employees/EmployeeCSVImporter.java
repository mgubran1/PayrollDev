package com.company.payroll.employees;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Importer for employee data from CSV and XLSX files.
 * Supports importing driver information with the following fields:
 * - Driver Name
 * - Driver %
 * - Company %
 * - Service Fee %
 * - Mobile #
 */
public class EmployeeCSVImporter {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeCSVImporter.class);
    
    /**
     * Import employees from a file (CSV or XLSX)
     */
    public static List<Employee> importEmployees(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        logger.info("Importing employees from file: {}", fileName);
        
        if (fileName.endsWith(".csv")) {
            return parseCSV(filePath);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return parseXLSX(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file type. Please use CSV or XLSX files.");
        }
    }
    
    /**
     * Parse CSV file with improved error handling
     */
    private static List<Employee> parseCSV(Path filePath) throws IOException {
        List<Employee> employees = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            if (line == null) {
                logger.warn("Empty CSV file");
                return employees;
            }
            
            // Parse header to find column indices
            String[] headers = line.split(",");
            Map<String, Integer> columnIndices = getColumnIndices(headers);
            logger.debug("CSV column indices: {}", columnIndices);
            
            // Validate that we have at least the driver name column
            if (!columnIndices.containsKey("Driver Name")) {
                throw new IllegalArgumentException("Required column 'Driver Name' not found in CSV file. Available columns: " + Arrays.toString(headers));
            }
            
            int lineNumber = 1;
            int validRows = 0;
            int skippedRows = 0;
            
            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                        logger.debug("Skipping empty line {}", lineNumber);
                        skippedRows++;
                        continue;
                    }
                    
                    Employee employee = parseCSVRow(line, columnIndices, lineNumber);
                    if (employee != null) {
                        employees.add(employee);
                        validRows++;
                    } else {
                        skippedRows++;
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing line {}: {}", lineNumber, e.getMessage());
                    skippedRows++;
                }
            }
            
            logger.info("CSV parsing complete - Valid: {}, Skipped: {}, Total: {}", validRows, skippedRows, lineNumber - 1);
        }
        
        logger.info("Parsed {} employees from CSV", employees.size());
        return employees;
    }
    
    /**
     * Parse XLSX file with improved error handling
     */
    private static List<Employee> parseXLSX(Path filePath) throws IOException {
        List<Employee> employees = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            
            if (!rowIterator.hasNext()) {
                logger.warn("Empty XLSX file");
                return employees;
            }
            
            // Parse header row
            Row headerRow = rowIterator.next();
            Map<String, Integer> columnIndices = getColumnIndicesFromRow(headerRow);
            logger.debug("XLSX column indices: {}", columnIndices);
            
            // Validate that we have at least the driver name column
            if (!columnIndices.containsKey("Driver Name")) {
                throw new IllegalArgumentException("Required column 'Driver Name' not found in XLSX file.");
            }
            
            int rowNumber = 1;
            int validRows = 0;
            int skippedRows = 0;
            
            while (rowIterator.hasNext()) {
                rowNumber++;
                Row row = rowIterator.next();
                try {
                    // Check if row is empty
                    if (isRowEmpty(row)) {
                        logger.debug("Skipping empty row {}", rowNumber);
                        skippedRows++;
                        continue;
                    }
                    
                    Employee employee = parseXLSXRow(row, columnIndices, rowNumber);
                    if (employee != null) {
                        employees.add(employee);
                        validRows++;
                    } else {
                        skippedRows++;
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing row {}: {}", rowNumber, e.getMessage());
                    skippedRows++;
                }
            }
            
            logger.info("XLSX parsing complete - Valid: {}, Skipped: {}, Total: {}", validRows, skippedRows, rowNumber - 1);
        }
        
        logger.info("Parsed {} employees from XLSX", employees.size());
        return employees;
    }
    
    /**
     * Check if a row is empty
     */
    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellString(cell);
                if (!value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Get column indices from CSV header with improved matching
     */
    private static Map<String, Integer> getColumnIndices(String[] headers) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            
            // More flexible matching for driver name
            if (header.contains("driver") && header.contains("name")) {
                indices.put("Driver Name", i);
            } else if (header.equals("name") || header.equals("driver")) {
                indices.put("Driver Name", i);
            }
            // More flexible matching for driver percentage
            else if (header.contains("driver") && (header.contains("%") || header.contains("percent"))) {
                indices.put("Driver %", i);
            } else if (header.equals("driver %") || header.equals("driver percent")) {
                indices.put("Driver %", i);
            }
            // More flexible matching for company percentage
            else if (header.contains("company") && (header.contains("%") || header.contains("percent"))) {
                indices.put("Company %", i);
            } else if (header.equals("company %") || header.equals("company percent")) {
                indices.put("Company %", i);
            }
            // More flexible matching for service fee percentage
            else if (header.contains("service") && header.contains("fee") && (header.contains("%") || header.contains("percent"))) {
                indices.put("Service Fee %", i);
            } else if (header.equals("service fee %") || header.equals("service fee percent")) {
                indices.put("Service Fee %", i);
            }
            // More flexible matching for mobile/phone
            else if (header.contains("mobile") || header.contains("phone")) {
                indices.put("Mobile #", i);
            } else if (header.equals("mobile #") || header.equals("phone #")) {
                indices.put("Mobile #", i);
            }
        }
        
        return indices;
    }
    
    /**
     * Get column indices from XLSX header row with improved matching
     */
    private static Map<String, Integer> getColumnIndicesFromRow(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String header = cell.getStringCellValue().trim().toLowerCase();
                
                // More flexible matching for driver name
                if (header.contains("driver") && header.contains("name")) {
                    indices.put("Driver Name", i);
                } else if (header.equals("name") || header.equals("driver")) {
                    indices.put("Driver Name", i);
                }
                // More flexible matching for driver percentage
                else if (header.contains("driver") && (header.contains("%") || header.contains("percent"))) {
                    indices.put("Driver %", i);
                } else if (header.equals("driver %") || header.equals("driver percent")) {
                    indices.put("Driver %", i);
                }
                // More flexible matching for company percentage
                else if (header.contains("company") && (header.contains("%") || header.contains("percent"))) {
                    indices.put("Company %", i);
                } else if (header.equals("company %") || header.equals("company percent")) {
                    indices.put("Company %", i);
                }
                // More flexible matching for service fee percentage
                else if (header.contains("service") && header.contains("fee") && (header.contains("%") || header.contains("percent"))) {
                    indices.put("Service Fee %", i);
                } else if (header.equals("service fee %") || header.equals("service fee percent")) {
                    indices.put("Service Fee %", i);
                }
                // More flexible matching for mobile/phone
                else if (header.contains("mobile") || header.contains("phone")) {
                    indices.put("Mobile #", i);
                } else if (header.equals("mobile #") || header.equals("phone #")) {
                    indices.put("Mobile #", i);
                }
            }
        }
        
        return indices;
    }
    
    /**
     * Parse a CSV row into an Employee object with improved validation
     */
    private static Employee parseCSVRow(String line, Map<String, Integer> columnIndices, int lineNumber) {
        String[] values = line.split(",");
        
        // Extract values with better error handling
        String driverName = getValue(values, columnIndices, "Driver Name");
        double driverPercent = parseDouble(getValue(values, columnIndices, "Driver %"));
        double companyPercent = parseDouble(getValue(values, columnIndices, "Company %"));
        double serviceFeePercent = parseDouble(getValue(values, columnIndices, "Service Fee %"));
        String mobileNumber = getValue(values, columnIndices, "Mobile #");
        
        // Validate required fields
        if (driverName == null || driverName.trim().isEmpty()) {
            logger.debug("Skipping line {}: missing driver name", lineNumber);
            return null;
        }
        
        // Validate percentage values
        if (driverPercent < 0 || driverPercent > 100) {
            logger.warn("Line {}: Invalid driver percentage: {} (should be 0-100)", lineNumber, driverPercent);
            driverPercent = Math.max(0, Math.min(100, driverPercent)); // Clamp to valid range
        }
        
        if (companyPercent < 0 || companyPercent > 100) {
            logger.warn("Line {}: Invalid company percentage: {} (should be 0-100)", lineNumber, companyPercent);
            companyPercent = Math.max(0, Math.min(100, companyPercent)); // Clamp to valid range
        }
        
        if (serviceFeePercent < 0 || serviceFeePercent > 100) {
            logger.warn("Line {}: Invalid service fee percentage: {} (should be 0-100)", lineNumber, serviceFeePercent);
            serviceFeePercent = Math.max(0, Math.min(100, serviceFeePercent)); // Clamp to valid range
        }
        
        // Clean and validate phone number
        if (mobileNumber != null && !mobileNumber.trim().isEmpty()) {
            mobileNumber = cleanPhoneNumber(mobileNumber.trim());
        }
        
        // Create employee with default values for missing fields
        Employee employee = new Employee(
            0, // ID will be set by database
            driverName.trim(),
            "", // truck unit - empty by default
            "", // trailer number - empty by default
            driverPercent,
            companyPercent,
            serviceFeePercent,
            null, // DOB - not provided in import
            "", // license number - not provided in import
            Employee.DriverType.OWNER_OPERATOR, // default driver type
            "", // employee LLC - not provided in import
            null, // CDL expiry - not provided in import
            null, // medical expiry - not provided in import
            Employee.Status.ACTIVE // default to active
        );
        
        employee.setPhone(mobileNumber != null ? mobileNumber : "");
        employee.setEmail(""); // not provided in import
        
        logger.debug("Parsed employee from line {}: {}", lineNumber, driverName);
        return employee;
    }
    
    /**
     * Parse an XLSX row into an Employee object with improved validation
     */
    private static Employee parseXLSXRow(Row row, Map<String, Integer> columnIndices, int rowNumber) {
        // Extract values with better error handling
        String driverName = getCellValue(row, columnIndices, "Driver Name");
        double driverPercent = parseDouble(getCellValue(row, columnIndices, "Driver %"));
        double companyPercent = parseDouble(getCellValue(row, columnIndices, "Company %"));
        double serviceFeePercent = parseDouble(getCellValue(row, columnIndices, "Service Fee %"));
        String mobileNumber = getCellValue(row, columnIndices, "Mobile #");
        
        // Validate required fields
        if (driverName == null || driverName.trim().isEmpty()) {
            logger.debug("Skipping row {}: missing driver name", rowNumber);
            return null;
        }
        
        // Validate percentage values
        if (driverPercent < 0 || driverPercent > 100) {
            logger.warn("Row {}: Invalid driver percentage: {} (should be 0-100)", rowNumber, driverPercent);
            driverPercent = Math.max(0, Math.min(100, driverPercent)); // Clamp to valid range
        }
        
        if (companyPercent < 0 || companyPercent > 100) {
            logger.warn("Row {}: Invalid company percentage: {} (should be 0-100)", rowNumber, companyPercent);
            companyPercent = Math.max(0, Math.min(100, companyPercent)); // Clamp to valid range
        }
        
        if (serviceFeePercent < 0 || serviceFeePercent > 100) {
            logger.warn("Row {}: Invalid service fee percentage: {} (should be 0-100)", rowNumber, serviceFeePercent);
            serviceFeePercent = Math.max(0, Math.min(100, serviceFeePercent)); // Clamp to valid range
        }
        
        // Clean and validate phone number
        if (mobileNumber != null && !mobileNumber.trim().isEmpty()) {
            mobileNumber = cleanPhoneNumber(mobileNumber.trim());
        }
        
        // Create employee with default values for missing fields
        Employee employee = new Employee(
            0, // ID will be set by database
            driverName.trim(),
            "", // truck unit - empty by default
            "", // trailer number - empty by default
            driverPercent,
            companyPercent,
            serviceFeePercent,
            null, // DOB - not provided in import
            "", // license number - not provided in import
            Employee.DriverType.OWNER_OPERATOR, // default driver type
            "", // employee LLC - not provided in import
            null, // CDL expiry - not provided in import
            null, // medical expiry - not provided in import
            Employee.Status.ACTIVE // default to active
        );
        
        employee.setPhone(mobileNumber != null ? mobileNumber : "");
        employee.setEmail(""); // not provided in import
        
        logger.debug("Parsed employee from row {}: {}", rowNumber, driverName);
        return employee;
    }
    
    /**
     * Clean phone number format
     */
    private static String cleanPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }
        
        // Remove all non-digit characters
        String cleaned = phone.replaceAll("[^0-9]", "");
        
        // Format as XXX-XXX-XXXX if it's 10 digits
        if (cleaned.length() == 10) {
            return cleaned.substring(0, 3) + "-" + cleaned.substring(3, 6) + "-" + cleaned.substring(6);
        }
        // Format as XXX-XXX-XXXX if it's 11 digits and starts with 1
        else if (cleaned.length() == 11 && cleaned.startsWith("1")) {
            return cleaned.substring(1, 4) + "-" + cleaned.substring(4, 7) + "-" + cleaned.substring(7);
        }
        
        // Return original if it doesn't match expected patterns
        return phone.trim();
    }
    
    /**
     * Get value from CSV array with improved error handling
     */
    private static String getValue(String[] values, Map<String, Integer> columnIndices, String fieldName) {
        Integer index = columnIndices.get(fieldName);
        if (index != null && index >= 0 && index < values.length) {
            String value = values[index].trim();
            // Handle quoted values
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return "";
    }
    
    /**
     * Get value from XLSX row with improved error handling
     */
    private static String getCellValue(Row row, Map<String, Integer> columnIndices, String fieldName) {
        Integer index = columnIndices.get(fieldName);
        if (index != null && index >= 0) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            return getCellString(cell);
        }
        return "";
    }
    
    /**
     * Get string value from cell with improved error handling
     */
    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    double value = cell.getNumericCellValue();
                    if (Math.floor(value) == value) {
                        return String.valueOf((long) value);
                    }
                    return String.valueOf(value);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return cell.getStringCellValue().trim();
                    } catch (Exception e) {
                        try {
                            return String.valueOf(cell.getNumericCellValue());
                        } catch (Exception e2) {
                            return "";
                        }
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            logger.debug("Error reading cell value: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Parse double value safely with improved error handling
     */
    private static double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            // Remove any non-numeric characters except decimal point and minus
            String cleaned = value.replaceAll("[^0-9.-]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            logger.debug("Could not parse double value: {}", value);
            return 0.0;
        }
    }
} 