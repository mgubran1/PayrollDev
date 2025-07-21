package com.company.payroll.trailers;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Importer for trailer data from CSV and Excel files.
 * Supports the following columns: Trailer Number, Year, License Plate, Make, VIN, Inspection
 */
public class TrailerCSVImporter {
    private static final Logger logger = LoggerFactory.getLogger(TrailerCSVImporter.class);
    
    // Expected column headers
    private static final String[] EXPECTED_HEADERS = {
        "Trailer Number", "Year", "License Plate", "Make", "VIN", "Inspection", "Lease Agreement Expiry"
    };
    
    // Date formatters for parsing inspection dates
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("M/d/yyyy"),  // 3/10/2024
        DateTimeFormatter.ofPattern("MM/dd/yyyy"), // 03/10/2024
        DateTimeFormatter.ofPattern("yyyy-MM-dd"), // 2024-03-10
        DateTimeFormatter.ofPattern("dd/MM/yyyy"), // 10/03/2024
        DateTimeFormatter.ofPattern("yyyy/MM/dd")  // 2024/03/10
    };
    
    /**
     * Import trailers from a file (CSV or Excel)
     */
    public static List<Trailer> importTrailers(Path filePath) throws IOException {
        String fileName = filePath.toString().toLowerCase();
        
        if (fileName.endsWith(".csv")) {
            return importFromCSV(filePath);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return importFromExcel(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Please use CSV or Excel files.");
        }
    }
    
    /**
     * Import from CSV file
     */
    private static List<Trailer> importFromCSV(Path filePath) throws IOException {
        logger.info("Importing trailers from CSV file: {}", filePath);
        List<Trailer> trailers = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Empty file");
            }
            
            // Parse headers
            String[] headers = parseCSVLine(line);
            validateHeaders(headers);
            
            // Read data rows
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                try {
                    Trailer trailer = parseTrailerFromCSV(line, headers);
                    if (trailer != null) {
                        trailers.add(trailer);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing row {}: {}", rowNumber, e.getMessage());
                }
            }
        }
        
        logger.info("Successfully imported {} trailers from CSV", trailers.size());
        return trailers;
    }
    
    /**
     * Import from Excel file
     */
    private static List<Trailer> importFromExcel(Path filePath) throws IOException {
        logger.info("Importing trailers from Excel file: {}", filePath);
        List<Trailer> trailers = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new IOException("Empty Excel file");
            }
            
            // Parse headers from first row
            Row headerRow = sheet.getRow(0);
            String[] headers = new String[headerRow.getLastCellNum()];
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers[i] = cell != null ? cell.toString().trim() : "";
            }
            validateHeaders(headers);
            
            // Read data rows
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                
                try {
                    Trailer trailer = parseTrailerFromExcel(row, headers);
                    if (trailer != null) {
                        trailers.add(trailer);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing Excel row {}: {}", rowNum + 1, e.getMessage());
                }
            }
        }
        
        logger.info("Successfully imported {} trailers from Excel", trailers.size());
        return trailers;
    }
    
    /**
     * Validate that the file contains the expected headers
     */
    private static void validateHeaders(String[] headers) throws IOException {
        if (headers.length < EXPECTED_HEADERS.length) {
            throw new IOException("File must contain at least " + EXPECTED_HEADERS.length + " columns");
        }
        
        // Check if all expected headers are present (case-insensitive)
        for (String expectedHeader : EXPECTED_HEADERS) {
            boolean found = false;
            for (String header : headers) {
                if (header != null && header.trim().equalsIgnoreCase(expectedHeader)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IOException("Missing required column: " + expectedHeader);
            }
        }
        
        logger.info("Headers validated successfully");
    }
    
    /**
     * Parse trailer from CSV line
     */
    private static Trailer parseTrailerFromCSV(String line, String[] headers) {
        String[] values = parseCSVLine(line);
        return parseTrailerFromValues(values, headers);
    }
    
    /**
     * Parse trailer from Excel row
     */
    private static Trailer parseTrailerFromExcel(Row row, String[] headers) {
        String[] values = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.getCell(i);
            values[i] = getCellValueAsString(cell);
        }
        return parseTrailerFromValues(values, headers);
    }
    
    /**
     * Parse trailer from values array
     */
    private static Trailer parseTrailerFromValues(String[] values, String[] headers) {
        Trailer trailer = new Trailer();
        
        // Find column indices
        int trailerNumberIndex = findColumnIndex(headers, "Trailer Number");
        int yearIndex = findColumnIndex(headers, "Year");
        int licensePlateIndex = findColumnIndex(headers, "License Plate");
        int makeIndex = findColumnIndex(headers, "Make");
        int vinIndex = findColumnIndex(headers, "VIN");
        int inspectionIndex = findColumnIndex(headers, "Inspection");
        int leaseExpiryIndex = findColumnIndex(headers, "Lease Agreement Expiry");
        
        // Parse trailer number (required)
        String trailerNumber = getValue(values, trailerNumberIndex);
        if (trailerNumber == null || trailerNumber.trim().isEmpty()) {
            logger.warn("Skipping row with empty trailer number");
            return null;
        }
        trailer.setTrailerNumber(trailerNumber.trim());
        
        // Parse year
        String yearStr = getValue(values, yearIndex);
        if (yearStr != null && !yearStr.trim().isEmpty()) {
            try {
                int year = Integer.parseInt(yearStr.trim());
                if (year > 1900 && year <= LocalDate.now().getYear() + 1) {
                    trailer.setYear(year);
                } else {
                    logger.warn("Invalid year value: {}", yearStr);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid year format: {}", yearStr);
            }
        }
        
        // Parse license plate
        String licensePlate = getValue(values, licensePlateIndex);
        if (licensePlate != null && !licensePlate.trim().isEmpty()) {
            trailer.setLicensePlate(licensePlate.trim());
        }
        
        // Parse make
        String make = getValue(values, makeIndex);
        if (make != null && !make.trim().isEmpty()) {
            trailer.setMake(make.trim());
        }
        
        // Parse VIN
        String vin = getValue(values, vinIndex);
        if (vin != null && !vin.trim().isEmpty()) {
            trailer.setVin(vin.trim());
        }
        
        // Parse inspection date
        String inspectionDateStr = getValue(values, inspectionIndex);
        if (inspectionDateStr != null && !inspectionDateStr.trim().isEmpty()) {
            LocalDate inspectionDate = parseDate(inspectionDateStr.trim());
            if (inspectionDate != null) {
                trailer.setLastInspectionDate(inspectionDate);
                // Calculate inspection expiry (365 days after inspection)
                trailer.setNextInspectionDueDate(inspectionDate.plusDays(365));
            }
        }
        
        // Parse lease agreement expiry date
        String leaseExpiryStr = getValue(values, leaseExpiryIndex);
        if (leaseExpiryStr != null && !leaseExpiryStr.trim().isEmpty()) {
            LocalDate leaseExpiryDate = parseDate(leaseExpiryStr.trim());
            if (leaseExpiryDate != null) {
                trailer.setLeaseAgreementExpiryDate(leaseExpiryDate);
            }
        }
        
        // Set default values
        trailer.setStatus(TrailerStatus.ACTIVE);
        trailer.setType("Unknown");
        
        return trailer;
    }
    
    /**
     * Find column index by header name (case-insensitive)
     */
    private static int findColumnIndex(String[] headers, String headerName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null && headers[i].trim().equalsIgnoreCase(headerName)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Get value from array with bounds checking
     */
    private static String getValue(String[] values, int index) {
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        return null;
    }
    
    /**
     * Parse CSV line, handling quoted values
     */
    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }
    
    /**
     * Get cell value as string
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * Parse date string using multiple formatters
     */
    private static LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        
        logger.warn("Could not parse date: {}", dateStr);
        return null;
    }
} 