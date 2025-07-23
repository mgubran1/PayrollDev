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
import java.util.Map;
import java.util.HashMap;

/**
 * Importer for trailer data from CSV and Excel files.
 * Supports the following columns: Trailer Number, Year, License Plate, Make, Model, VIN,
 * Type, Status, Registration Expiry, Insurance Expiry, Inspection Date, Inspection Expiry, Current Location
 */
public class TrailerCSVImporter {
    private static final Logger logger = LoggerFactory.getLogger(TrailerCSVImporter.class);
    
    // Expected column headers
    private static final String[] EXPECTED_HEADERS = {
        "Trailer Number", "Year", "License Plate", "Make", "Model", "VIN", "Type", "Status",
        "Registration Expiry", "Insurance Expiry", "Inspection Date", "Inspection Expiry"
    };
    
    // Date formatters for parsing inspection dates
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("M/d/yyyy"),  // 3/10/2024
        DateTimeFormatter.ofPattern("MM/dd/yyyy"), // 03/10/2024
        DateTimeFormatter.ofPattern("yyyy-MM-dd"), // 2024-03-10
        DateTimeFormatter.ofPattern("dd/MM/yyyy"), // 10/03/2024
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),  // 2024/03/10
        DateTimeFormatter.ofPattern("M-d-yyyy"),  // 3-10-2024
        DateTimeFormatter.ofPattern("MM-dd-yyyy") // 03-10-2024
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
            Map<String, Integer> columnIndices = getColumnIndices(headers);
            
            // Read data rows
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                try {
                    if (line.trim().isEmpty()) {
                        continue; // Skip empty lines
                    }
                    Trailer trailer = parseTrailerFromCSV(line, columnIndices);
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
            Map<String, Integer> columnIndices = getColumnIndices(headers);
            
            // Read data rows
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                
                try {
                    Trailer trailer = parseTrailerFromExcel(row, columnIndices);
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
     * Get column indices from headers array
     */
    private static Map<String, Integer> getColumnIndices(String[] headers) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            
            // Trailer Number
            if (header.contains("trailer") && header.contains("number")) {
                indices.put("Trailer Number", i);
            } else if (header.equals("trailer") || header.equals("trailer #")) {
                indices.put("Trailer Number", i);
            }
            // Year
            else if (header.contains("year")) {
                indices.put("Year", i);
            }
            // License Plate
            else if (header.contains("license") && header.contains("plate")) {
                indices.put("License Plate", i);
            } else if (header.equals("license") || header.equals("plate")) {
                indices.put("License Plate", i);
            }
            // Make
            else if (header.equals("make")) {
                indices.put("Make", i);
            }
            // Model
            else if (header.equals("model")) {
                indices.put("Model", i);
            }
            // VIN
            else if (header.contains("vin")) {
                indices.put("VIN", i);
            }
            // Type
            else if (header.equals("type")) {
                indices.put("Type", i);
            }
            // Status
            else if (header.equals("status")) {
                indices.put("Status", i);
            }
            // Registration Expiry
            else if (header.contains("registration") && header.contains("expiry")) {
                indices.put("Registration Expiry", i);
            } else if (header.equals("registration expiry") || header.equals("reg expiry")) {
                indices.put("Registration Expiry", i);
            }
            // Insurance Expiry
            else if (header.contains("insurance") && header.contains("expiry")) {
                indices.put("Insurance Expiry", i);
            } else if (header.equals("insurance expiry") || header.equals("ins expiry")) {
                indices.put("Insurance Expiry", i);
            }
            // Inspection Date
            else if (header.contains("inspection") && !header.contains("expiry")) {
                indices.put("Inspection Date", i);
            }
            // Inspection Expiry
            else if (header.contains("inspection") && header.contains("expiry")) {
                indices.put("Inspection Expiry", i);
            } else if (header.equals("inspection expiry") || header.equals("insp expiry")) {
                indices.put("Inspection Expiry", i);
            }
            // Current Location
            else if (header.contains("current") && header.contains("location")) {
                indices.put("Current Location", i);
            } else if (header.equals("location")) {
                indices.put("Current Location", i);
            }
        }
        
        return indices;
    }
    
    /**
     * Parse trailer from CSV line
     */
    private static Trailer parseTrailerFromCSV(String line, Map<String, Integer> columnIndices) {
        String[] values = parseCSVLine(line);
        return parseTrailerFromValues(columnIndices, i -> {
            if (i >= 0 && i < values.length) {
                return values[i].trim();
            }
            return "";
        });
    }
    
    /**
     * Parse trailer from Excel row
     */
    private static Trailer parseTrailerFromExcel(Row row, Map<String, Integer> columnIndices) {
        return parseTrailerFromValues(columnIndices, i -> {
            if (i >= 0) {
                Cell cell = row.getCell(i);
                return getCellValueAsString(cell);
            }
            return "";
        });
    }
    
    /**
     * Parse trailer using a value provider function
     */
    private static Trailer parseTrailerFromValues(Map<String, Integer> columnIndices, ValueProvider valueProvider) {
        // Extract trailer number (required)
        Integer trailerNumberIndex = columnIndices.get("Trailer Number");
        if (trailerNumberIndex == null) {
            logger.warn("Trailer Number column not found");
            return null;
        }
        
        String trailerNumber = valueProvider.getValue(trailerNumberIndex);
        if (trailerNumber == null || trailerNumber.trim().isEmpty()) {
            logger.warn("Skipping row with empty trailer number");
            return null;
        }
        
        Trailer trailer = new Trailer(trailerNumber.trim());
        
        // Parse year
        Integer yearIndex = columnIndices.get("Year");
        if (yearIndex != null) {
            String yearStr = valueProvider.getValue(yearIndex);
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
        }
        
        // Parse license plate
        Integer licensePlateIndex = columnIndices.get("License Plate");
        if (licensePlateIndex != null) {
            String licensePlate = valueProvider.getValue(licensePlateIndex);
            if (licensePlate != null && !licensePlate.trim().isEmpty()) {
                trailer.setLicensePlate(licensePlate.trim());
            }
        }
        
        // Parse make
        Integer makeIndex = columnIndices.get("Make");
        if (makeIndex != null) {
            String make = valueProvider.getValue(makeIndex);
            if (make != null && !make.trim().isEmpty()) {
                trailer.setMake(make.trim());
            }
        }
        
        // Parse model
        Integer modelIndex = columnIndices.get("Model");
        if (modelIndex != null) {
            String model = valueProvider.getValue(modelIndex);
            if (model != null && !model.trim().isEmpty()) {
                trailer.setModel(model.trim());
            }
        }
        
        // Parse VIN
        Integer vinIndex = columnIndices.get("VIN");
        if (vinIndex != null) {
            String vin = valueProvider.getValue(vinIndex);
            if (vin != null && !vin.trim().isEmpty()) {
                trailer.setVin(vin.trim());
            }
        }
        
        // Parse type
        Integer typeIndex = columnIndices.get("Type");
        if (typeIndex != null) {
            String type = valueProvider.getValue(typeIndex);
            if (type != null && !type.trim().isEmpty()) {
                trailer.setType(type.trim());
            }
        }
        
        // Parse status
        Integer statusIndex = columnIndices.get("Status");
        if (statusIndex != null) {
            String status = valueProvider.getValue(statusIndex);
            if (status != null && !status.trim().isEmpty()) {
                try {
                    TrailerStatus trailerStatus = TrailerStatus.valueOf(status.trim().toUpperCase());
                    trailer.setStatus(trailerStatus);
                } catch (IllegalArgumentException e) {
                    // Default to ACTIVE if status is not recognized
                    trailer.setStatus(TrailerStatus.ACTIVE);
                }
            }
        }
        
        // Parse registration expiry date
        Integer regExpiryIndex = columnIndices.get("Registration Expiry");
        if (regExpiryIndex != null) {
            String regExpiryStr = valueProvider.getValue(regExpiryIndex);
            if (regExpiryStr != null && !regExpiryStr.trim().isEmpty()) {
                LocalDate regExpiryDate = parseDate(regExpiryStr.trim());
                if (regExpiryDate != null) {
                    trailer.setRegistrationExpiryDate(regExpiryDate);
                }
            }
        }
        
        // Parse insurance expiry date
        Integer insExpiryIndex = columnIndices.get("Insurance Expiry");
        if (insExpiryIndex != null) {
            String insExpiryStr = valueProvider.getValue(insExpiryIndex);
            if (insExpiryStr != null && !insExpiryStr.trim().isEmpty()) {
                LocalDate insExpiryDate = parseDate(insExpiryStr.trim());
                if (insExpiryDate != null) {
                    trailer.setInsuranceExpiryDate(insExpiryDate);
                }
            }
        }
        
        // Parse inspection date
        Integer inspectionIndex = columnIndices.get("Inspection Date");
        if (inspectionIndex != null) {
            String inspectionDateStr = valueProvider.getValue(inspectionIndex);
            if (inspectionDateStr != null && !inspectionDateStr.trim().isEmpty()) {
                LocalDate inspectionDate = parseDate(inspectionDateStr.trim());
                if (inspectionDate != null) {
                    trailer.setLastInspectionDate(inspectionDate);
                }
            }
        }
        
        // Parse inspection expiry date
        Integer inspExpiryIndex = columnIndices.get("Inspection Expiry");
        if (inspExpiryIndex != null) {
            String inspExpiryStr = valueProvider.getValue(inspExpiryIndex);
            if (inspExpiryStr != null && !inspExpiryStr.trim().isEmpty()) {
                LocalDate inspExpiryDate = parseDate(inspExpiryStr.trim());
                if (inspExpiryDate != null) {
                    trailer.setNextInspectionDueDate(inspExpiryDate);
                } else if (trailer.getLastInspectionDate() != null) {
                    // Auto-calculate if not provided
                    trailer.setNextInspectionDueDate(trailer.getLastInspectionDate().plusDays(365));
                }
            } else if (trailer.getLastInspectionDate() != null) {
                // Auto-calculate if not provided
                trailer.setNextInspectionDueDate(trailer.getLastInspectionDate().plusDays(365));
            }
        }
        
        // Parse current location
        Integer locationIndex = columnIndices.get("Current Location");
        if (locationIndex != null) {
            String location = valueProvider.getValue(locationIndex);
            if (location != null && !location.trim().isEmpty()) {
                trailer.setCurrentLocation(location.trim());
            }
        }
        
        return trailer;
    }
    
    /**
     * Value provider interface for flexible data extraction
     */
    private interface ValueProvider {
        String getValue(int index);
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
                    try {
                        return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } catch (Exception e) {
                        return "";
                    }
                } else {
                    double value = cell.getNumericCellValue();
                    if (Math.floor(value) == value) {
                        return String.valueOf((int) value);
                    }
                    return String.valueOf(value);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
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