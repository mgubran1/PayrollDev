package com.company.payroll.employees;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Importer for employee data from CSV and XLSX files.
 * Supports importing driver information with the following fields:
 * - Driver Name
 * - Truck/Unit
 * - Trailer #
 * - Email
 * - Driver %
 * - Company %
 * - Service Fee %
 * - DOB
 * - License #
 * - Driver Type
 * - Employee LLC
 * - CDL Expiry
 * - Medical Expiry
 * - Mobile #
 */
public class EmployeeCSVImporter {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeCSVImporter.class);
    private static final String[] DATE_FORMATS = {
        "MM/dd/yyyy", "yyyy-MM-dd", "M/d/yyyy", "MM-dd-yyyy", "dd/MM/yyyy", "yyyy/MM/dd"
    };
    
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
            String[] headers = line.split(",", -1); // -1 to include trailing empty strings
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
            
            // Driver name
            if (header.contains("driver") && header.contains("name")) {
                indices.put("Driver Name", i);
            } else if (header.equals("name") || header.equals("driver")) {
                indices.put("Driver Name", i);
            }
            // Truck/Unit
            else if (header.contains("truck") || (header.contains("unit") && !header.contains("trailer"))) {
                indices.put("Truck/Unit", i);
            }
            // Trailer number
            else if (header.contains("trailer") || header.equals("trailer #")) {
                indices.put("Trailer #", i);
            }
            // Email
            else if (header.contains("email")) {
                indices.put("Email", i);
            }
            // Driver percentage
            else if (header.contains("driver") && (header.contains("%") || header.contains("percent"))) {
                indices.put("Driver %", i);
            } else if (header.equals("driver %") || header.equals("driver percent")) {
                indices.put("Driver %", i);
            }
            // Company percentage
            else if (header.contains("company") && (header.contains("%") || header.contains("percent"))) {
                indices.put("Company %", i);
            } else if (header.equals("company %") || header.equals("company percent")) {
                indices.put("Company %", i);
            }
            // Service fee percentage
            else if (header.contains("service") && header.contains("fee") && (header.contains("%") || header.contains("percent"))) {
                indices.put("Service Fee %", i);
            } else if (header.equals("service fee %") || header.equals("service fee percent")) {
                indices.put("Service Fee %", i);
            }
            // DOB
            else if (header.contains("dob") || header.contains("birth") || header.contains("date of birth")) {
                indices.put("DOB", i);
            }
            // License number
            else if (header.contains("license") || header.equals("license #")) {
                indices.put("License #", i);
            }
            // Driver type
            else if (header.contains("driver type") || header.equals("type")) {
                indices.put("Driver Type", i);
            }
            // Employee LLC
            else if (header.contains("llc") || header.contains("employee llc")) {
                indices.put("Employee LLC", i);
            }
            // CDL expiry
            else if ((header.contains("cdl") && (header.contains("expiry") || header.contains("expiration"))) || header.equals("cdl expiry")) {
                indices.put("CDL Expiry", i);
            }
            // Medical expiry
            else if ((header.contains("medical") || header.contains("med")) && (header.contains("expiry") || header.contains("expiration"))) {
                indices.put("Medical Expiry", i);
            }
            // Phone/Mobile
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
                
                // Driver name
                if (header.contains("driver") && header.contains("name")) {
                    indices.put("Driver Name", i);
                } else if (header.equals("name") || header.equals("driver")) {
                    indices.put("Driver Name", i);
                }
                // Truck/Unit
                else if (header.contains("truck") || (header.contains("unit") && !header.contains("trailer"))) {
                    indices.put("Truck/Unit", i);
                }
                // Trailer number
                else if (header.contains("trailer") || header.equals("trailer #")) {
                    indices.put("Trailer #", i);
                }
                // Email
                else if (header.contains("email")) {
                    indices.put("Email", i);
                }
                // Driver percentage
                else if (header.contains("driver") && (header.contains("%") || header.contains("percent"))) {
                    indices.put("Driver %", i);
                } else if (header.equals("driver %") || header.equals("driver percent")) {
                    indices.put("Driver %", i);
                }
                // Company percentage
                else if (header.contains("company") && (header.contains("%") || header.contains("percent"))) {
                    indices.put("Company %", i);
                } else if (header.equals("company %") || header.equals("company percent")) {
                    indices.put("Company %", i);
                }
                // Service fee percentage
                else if (header.contains("service") && header.contains("fee") && (header.contains("%") || header.contains("percent"))) {
                    indices.put("Service Fee %", i);
                } else if (header.equals("service fee %") || header.equals("service fee percent")) {
                    indices.put("Service Fee %", i);
                }
                // DOB
                else if (header.contains("dob") || header.contains("birth") || header.contains("date of birth")) {
                    indices.put("DOB", i);
                }
                // License number
                else if (header.contains("license") || header.equals("license #")) {
                    indices.put("License #", i);
                }
                // Driver type
                else if (header.contains("driver type") || header.equals("type")) {
                    indices.put("Driver Type", i);
                }
                // Employee LLC
                else if (header.contains("llc") || header.contains("employee llc")) {
                    indices.put("Employee LLC", i);
                }
                // CDL expiry
                else if ((header.contains("cdl") && (header.contains("expiry") || header.contains("expiration"))) || header.equals("cdl expiry")) {
                    indices.put("CDL Expiry", i);
                }
                // Medical expiry
                else if ((header.contains("medical") || header.contains("med")) && (header.contains("expiry") || header.contains("expiration"))) {
                    indices.put("Medical Expiry", i);
                }
                // Phone/Mobile
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
        String[] values = line.split(",", -1); // -1 to include trailing empty strings
        
        // Extract values with better error handling
        String driverName = getValue(values, columnIndices, "Driver Name");
        String truckUnit = getValue(values, columnIndices, "Truck/Unit");
        String trailerNumber = getValue(values, columnIndices, "Trailer #");
        String email = getValue(values, columnIndices, "Email");
        double driverPercent = parseDouble(getValue(values, columnIndices, "Driver %"));
        double companyPercent = parseDouble(getValue(values, columnIndices, "Company %"));
        double serviceFeePercent = parseDouble(getValue(values, columnIndices, "Service Fee %"));
        LocalDate dob = parseDate(getValue(values, columnIndices, "DOB"));
        String licenseNumber = getValue(values, columnIndices, "License #");
        String driverTypeStr = getValue(values, columnIndices, "Driver Type");
        String employeeLLC = getValue(values, columnIndices, "Employee LLC");
        LocalDate cdlExpiry = parseDate(getValue(values, columnIndices, "CDL Expiry"));
        LocalDate medicalExpiry = parseDate(getValue(values, columnIndices, "Medical Expiry"));
        String mobileNumber = getValue(values, columnIndices, "Mobile #");
        
        // Validate required fields
        if (driverName == null || driverName.trim().isEmpty()) {
            logger.debug("Skipping line {}: missing driver name", lineNumber);
            return null;
        }
        
        // Parse driver type
        Employee.DriverType driverType = parseDriverType(driverTypeStr);
        
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
        
        // Create employee with all available fields
        Employee employee = new Employee(
            0, // ID will be set by database
            driverName.trim(),
            truckUnit != null ? truckUnit.trim() : "",
            trailerNumber != null ? trailerNumber.trim() : "",
            driverPercent,
            companyPercent,
            serviceFeePercent,
            dob,
            licenseNumber != null ? licenseNumber.trim() : "",
            driverType,
            employeeLLC != null ? employeeLLC.trim() : "",
            cdlExpiry,
            medicalExpiry,
            Employee.Status.ACTIVE // default to active
        );
        
        // Set additional fields
        employee.setPhone(mobileNumber != null ? mobileNumber : "");
        employee.setEmail(email != null ? email.trim() : "");
        
        logger.debug("Parsed employee from line {}: {}", lineNumber, driverName);
        return employee;
    }
    
    /**
     * Parse an XLSX row into an Employee object with improved validation
     */
    private static Employee parseXLSXRow(Row row, Map<String, Integer> columnIndices, int rowNumber) {
        // Extract values with better error handling
        String driverName = getCellValue(row, columnIndices, "Driver Name");
        String truckUnit = getCellValue(row, columnIndices, "Truck/Unit");
        String trailerNumber = getCellValue(row, columnIndices, "Trailer #");
        String email = getCellValue(row, columnIndices, "Email");
        double driverPercent = parseDouble(getCellValue(row, columnIndices, "Driver %"));
        double companyPercent = parseDouble(getCellValue(row, columnIndices, "Company %"));
        double serviceFeePercent = parseDouble(getCellValue(row, columnIndices, "Service Fee %"));
        LocalDate dob = parseDate(getCellValue(row, columnIndices, "DOB"));
        String licenseNumber = getCellValue(row, columnIndices, "License #");
        String driverTypeStr = getCellValue(row, columnIndices, "Driver Type");
        String employeeLLC = getCellValue(row, columnIndices, "Employee LLC");
        LocalDate cdlExpiry = parseDate(getCellValue(row, columnIndices, "CDL Expiry"));
        LocalDate medicalExpiry = parseDate(getCellValue(row, columnIndices, "Medical Expiry"));
        String mobileNumber = getCellValue(row, columnIndices, "Mobile #");
        
        // Validate required fields
        if (driverName == null || driverName.trim().isEmpty()) {
            logger.debug("Skipping row {}: missing driver name", rowNumber);
            return null;
        }
        
        // Parse driver type
        Employee.DriverType driverType = parseDriverType(driverTypeStr);
        
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
        
        // Create employee with all available fields
        Employee employee = new Employee(
            0, // ID will be set by database
            driverName.trim(),
            truckUnit != null ? truckUnit.trim() : "",
            trailerNumber != null ? trailerNumber.trim() : "",
            driverPercent,
            companyPercent,
            serviceFeePercent,
            dob,
            licenseNumber != null ? licenseNumber.trim() : "",
            driverType,
            employeeLLC != null ? employeeLLC.trim() : "",
            cdlExpiry,
            medicalExpiry,
            Employee.Status.ACTIVE // default to active
        );
        
        // Set additional fields
        employee.setPhone(mobileNumber != null ? mobileNumber : "");
        employee.setEmail(email != null ? email.trim() : "");
        
        logger.debug("Parsed employee from row {}: {}", rowNumber, driverName);
        return employee;
    }
    
    /**
     * Parse driver type from string
     */
    private static Employee.DriverType parseDriverType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Employee.DriverType.OWNER_OPERATOR; // Default
        }
        
        String cleanValue = value.trim().toUpperCase();
        
        if (cleanValue.contains("OWNER") || cleanValue.contains("OPERATOR")) {
            return Employee.DriverType.OWNER_OPERATOR;
        } else if (cleanValue.contains("COMPANY")) {
            return Employee.DriverType.COMPANY_DRIVER;
        } else {
            return Employee.DriverType.OTHER;
        }
    }
    
    /**
     * Parse date from string with multiple format support
     */
    private static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String cleanValue = value.trim();
        
        // Try parsing with each supported date format
        for (String format : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleanValue, DateTimeFormatter.ofPattern(format));
            } catch (DateTimeParseException e) {
                // Continue trying with next format
            }
        }
        
        // If we get here, no format matched
        logger.debug("Could not parse date: {}", value);
        return null;
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
                    // Check if this is a date cell
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Date date = cell.getDateCellValue();
                        if (date != null) {
                            return new java.text.SimpleDateFormat("MM/dd/yyyy").format(date);
                        }
                    }
                    
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