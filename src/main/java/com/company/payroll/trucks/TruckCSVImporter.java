package com.company.payroll.trucks;

import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Importer for truck data from CSV and XLSX files.
 * Supports importing truck information with the following fields:
 * - Truck/Unit
 * - Year
 * - Make/Model
 * - VIN
 * - License Plate
 * - Assigned Driver
 * - Registration Expiry
 * - Inspection
 */
public class TruckCSVImporter {
    private static final Logger logger = LoggerFactory.getLogger(TruckCSVImporter.class);
    private static final EmployeeDAO employeeDAO = new EmployeeDAO();
    
    /**
     * Import trucks from a file (CSV or XLSX)
     */
    public static List<Truck> importTrucks(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        logger.info("Importing trucks from file: {}", fileName);
        
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
    private static List<Truck> parseCSV(Path filePath) throws IOException {
        List<Truck> trucks = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            if (line == null) {
                logger.warn("Empty CSV file");
                return trucks;
            }
            
            // Parse header to find column indices
            String[] headers = line.split(",");
            Map<String, Integer> columnIndices = getColumnIndices(headers);
            logger.debug("CSV column indices: {}", columnIndices);
            
            // Validate that we have at least the truck unit column
            if (!columnIndices.containsKey("Truck/Unit")) {
                throw new IllegalArgumentException("Required column 'Truck/Unit' not found in CSV file. Available columns: " + Arrays.toString(headers));
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
                    
                    Truck truck = parseCSVRow(line, columnIndices, lineNumber);
                    if (truck != null) {
                        trucks.add(truck);
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
        
        logger.info("Parsed {} trucks from CSV", trucks.size());
        return trucks;
    }
    
    /**
     * Parse XLSX file with improved error handling
     */
    private static List<Truck> parseXLSX(Path filePath) throws IOException {
        List<Truck> trucks = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            
            if (!rowIterator.hasNext()) {
                logger.warn("Empty XLSX file");
                return trucks;
            }
            
            // Parse header row
            Row headerRow = rowIterator.next();
            Map<String, Integer> columnIndices = getColumnIndicesFromRow(headerRow);
            logger.debug("XLSX column indices: {}", columnIndices);
            
            // Validate that we have at least the truck unit column
            if (!columnIndices.containsKey("Truck/Unit")) {
                throw new IllegalArgumentException("Required column 'Truck/Unit' not found in XLSX file.");
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
                    
                    Truck truck = parseXLSXRow(row, columnIndices, rowNumber);
                    if (truck != null) {
                        trucks.add(truck);
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
        
        logger.info("Parsed {} trucks from XLSX", trucks.size());
        return trucks;
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
            
            // More flexible matching for truck unit
            if (header.contains("truck") && header.contains("unit")) {
                indices.put("Truck/Unit", i);
            } else if (header.equals("unit") || header.equals("truck")) {
                indices.put("Truck/Unit", i);
            }
            // More flexible matching for year
            else if (header.contains("year")) {
                indices.put("Year", i);
            }
            // More flexible matching for make/model
            else if (header.contains("make") && header.contains("model")) {
                indices.put("Make/Model", i);
            } else if (header.equals("make") || header.equals("model")) {
                indices.put("Make/Model", i);
            }
            // More flexible matching for VIN
            else if (header.contains("vin")) {
                indices.put("VIN", i);
            }
            // More flexible matching for license plate
            else if (header.contains("license") && header.contains("plate")) {
                indices.put("License Plate", i);
            } else if (header.equals("license plate") || header.equals("plate")) {
                indices.put("License Plate", i);
            }
            // More flexible matching for registration expiry
            else if (header.contains("registration") && header.contains("expiry")) {
                indices.put("Registration Expiry", i);
            } else if (header.equals("registration expiry") || header.equals("reg expiry")) {
                indices.put("Registration Expiry", i);
            }
            // More flexible matching for inspection
            else if (header.contains("inspection")) {
                indices.put("Inspection", i);
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
                
                // More flexible matching for truck unit
                if (header.contains("truck") && header.contains("unit")) {
                    indices.put("Truck/Unit", i);
                } else if (header.equals("unit") || header.equals("truck")) {
                    indices.put("Truck/Unit", i);
                }
                // More flexible matching for year
                else if (header.contains("year")) {
                    indices.put("Year", i);
                }
                // More flexible matching for make/model
                else if (header.contains("make") && header.contains("model")) {
                    indices.put("Make/Model", i);
                } else if (header.equals("make") || header.equals("model")) {
                    indices.put("Make/Model", i);
                }
                // More flexible matching for VIN
                else if (header.contains("vin")) {
                    indices.put("VIN", i);
                }
                // More flexible matching for license plate
                else if (header.contains("license") && header.contains("plate")) {
                    indices.put("License Plate", i);
                } else if (header.equals("license plate") || header.equals("plate")) {
                    indices.put("License Plate", i);
                }
                // More flexible matching for registration expiry
                else if (header.contains("registration") && header.contains("expiry")) {
                    indices.put("Registration Expiry", i);
                } else if (header.equals("registration expiry") || header.equals("reg expiry")) {
                    indices.put("Registration Expiry", i);
                }
                // More flexible matching for inspection
                else if (header.contains("inspection")) {
                    indices.put("Inspection", i);
                }
            }
        }
        
        return indices;
    }
    
    /**
     * Parse a CSV row into a Truck object with improved validation
     */
    private static Truck parseCSVRow(String line, Map<String, Integer> columnIndices, int lineNumber) {
        String[] values = line.split(",");
        
        // Extract values with better error handling
        String truckUnit = getValue(values, columnIndices, "Truck/Unit");
        int year = parseInt(getValue(values, columnIndices, "Year"));
        String makeModel = getValue(values, columnIndices, "Make/Model");
        String vin = getValue(values, columnIndices, "VIN");
        String licensePlate = getValue(values, columnIndices, "License Plate");
        LocalDate registrationExpiry = parseDate(getValue(values, columnIndices, "Registration Expiry"));
        LocalDate inspection = parseDate(getValue(values, columnIndices, "Inspection"));
        
        // Validate required fields
        if (truckUnit == null || truckUnit.trim().isEmpty()) {
            logger.debug("Skipping line {}: missing truck unit", lineNumber);
            return null;
        }
        
        // Validate year
        if (year < 1900 || year > 2030) {
            logger.warn("Line {}: Invalid year: {} (should be 1900-2030)", lineNumber, year);
            year = Math.max(1900, Math.min(2030, year)); // Clamp to valid range
        }
        
        // Split make/model if combined
        String make = "";
        String model = "";
        if (makeModel != null && !makeModel.trim().isEmpty()) {
            String[] parts = makeModel.trim().split("\\s+", 2);
            make = parts[0];
            if (parts.length > 1) {
                model = parts[1];
            }
        }
        
        // Create truck with default values for missing fields
        Truck truck = new Truck();
        truck.setNumber(truckUnit.trim());
        truck.setYear(year);
        truck.setMake(make);
        truck.setModel(model);
        truck.setVin(vin != null ? vin.trim() : "");
        truck.setLicensePlate(licensePlate != null ? licensePlate.trim() : "");
        truck.setRegistrationExpiryDate(registrationExpiry);
        truck.setInspection(inspection);
        
        // Auto-calculate Inspection Expiry as 365 days after Inspection date
        if (inspection != null) {
            LocalDate inspectionExpiry = inspection.plusDays(365);
            truck.setNextInspectionDue(inspectionExpiry);
        }
        
        truck.setStatus("Active"); // default status
        truck.setType("Semi Truck (Tractor)"); // default type
        truck.setAssigned(false); // Assigned Driver column removed, so always false
        
        logger.debug("Parsed truck from line {}: {}", lineNumber, truckUnit);
        return truck;
    }
    
    /**
     * Parse an XLSX row into a Truck object with improved validation
     */
    private static Truck parseXLSXRow(Row row, Map<String, Integer> columnIndices, int rowNumber) {
        // Extract values with better error handling
        String truckUnit = getCellValue(row, columnIndices, "Truck/Unit");
        int year = parseInt(getCellValue(row, columnIndices, "Year"));
        String makeModel = getCellValue(row, columnIndices, "Make/Model");
        String vin = getCellValue(row, columnIndices, "VIN");
        String licensePlate = getCellValue(row, columnIndices, "License Plate");
        LocalDate registrationExpiry = parseDate(getCellValue(row, columnIndices, "Registration Expiry"));
        LocalDate inspection = parseDate(getCellValue(row, columnIndices, "Inspection"));
        
        // Validate required fields
        if (truckUnit == null || truckUnit.trim().isEmpty()) {
            logger.debug("Skipping row {}: missing truck unit", rowNumber);
            return null;
        }
        
        // Validate year
        if (year < 1900 || year > 2030) {
            logger.warn("Row {}: Invalid year: {} (should be 1900-2030)", rowNumber, year);
            year = Math.max(1900, Math.min(2030, year)); // Clamp to valid range
        }
        
        // Split make/model if combined
        String make = "";
        String model = "";
        if (makeModel != null && !makeModel.trim().isEmpty()) {
            String[] parts = makeModel.trim().split("\\s+", 2);
            make = parts[0];
            if (parts.length > 1) {
                model = parts[1];
            }
        }
        
        // Create truck with default values for missing fields
        Truck truck = new Truck();
        truck.setNumber(truckUnit.trim());
        truck.setYear(year);
        truck.setMake(make);
        truck.setModel(model);
        truck.setVin(vin != null ? vin.trim() : "");
        truck.setLicensePlate(licensePlate != null ? licensePlate.trim() : "");
        truck.setRegistrationExpiryDate(registrationExpiry);
        truck.setInspection(inspection);
        
        // Auto-calculate Inspection Expiry as 365 days after Inspection date
        if (inspection != null) {
            LocalDate inspectionExpiry = inspection.plusDays(365);
            truck.setNextInspectionDue(inspectionExpiry);
        }
        
        truck.setStatus("Active"); // default status
        truck.setType("Semi Truck (Tractor)"); // default type
        truck.setAssigned(false); // Assigned Driver column removed, so always false
        
        logger.debug("Parsed truck from row {}: {}", rowNumber, truckUnit);
        return truck;
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
     * Parse integer value safely
     */
    private static int parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            // Remove any non-numeric characters except minus
            String cleaned = value.replaceAll("[^0-9-]", "");
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            logger.debug("Could not parse integer value: {}", value);
            return 0;
        }
    }
    
    /**
     * Parse date value safely with multiple formats
     */
    private static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String dateStr = value.trim();
        
        // Try multiple date formats
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception e) {
                // Continue to next format
            }
        }
        
        logger.debug("Could not parse date value: {}", value);
        return null;
    }
} 