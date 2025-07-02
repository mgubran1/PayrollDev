package com.company.payroll.triumph;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MyTriumphExcelImporter {
    private static final Logger logger = LoggerFactory.getLogger(MyTriumphExcelImporter.class);
    
    public static List<MyTriumphRecord> importFromXlsx(File file) throws Exception {
        logger.info("Starting import from file: {}", file.getName());
        List<MyTriumphRecord> result = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Header row
            if (!rowIterator.hasNext()) {
                logger.error("Empty sheet in file: {}", file.getName());
                throw new Exception("Empty sheet!");
            }
            Row headerRow = rowIterator.next();
            int colDTR = -1, colINV = -1, colDATE = -1, colPO = -1, colAMT = -1;
            for (Cell cell : headerRow) {
                String val = getCellString(cell).toUpperCase();
                if (val.equals("DTR_NAME") || val.equals("DTRNAME") || val.equals("DTR NAME")) colDTR = cell.getColumnIndex();
                else if (val.equals("INVOICE#") || val.equals("INVOICE") || val.equals("INV#")) colINV = cell.getColumnIndex();
                else if (val.equals("INV_DATE") || val.equals("INVDATE") || val.equals("INV DATE") || val.equals("INVOICE DATE")) colDATE = cell.getColumnIndex();
                else if (val.equals("PO") || val.equals("P.O.")) colPO = cell.getColumnIndex();
                else if (val.equals("INVAMT") || val.equals("INV AMT") || val.equals("INVOICE AMT") || val.equals("AMOUNT")) colAMT = cell.getColumnIndex();
            }
            
            logger.debug("Column indices - DTR: {}, INV: {}, DATE: {}, PO: {}, AMT: {}", 
                colDTR, colINV, colDATE, colPO, colAMT);
            
            if (colDTR == -1 || colINV == -1 || colDATE == -1 || colPO == -1 || colAMT == -1) {
                logger.error("Required columns missing in file: {}", file.getName());
                throw new Exception("Required columns missing! Expected: DTR_NAME, INVOICE#, INV_DATE, PO, INVAMT");
            }

            DateTimeFormatter[] fmts = new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("M/d/yy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
            };

            int rowNum = 1;
            int validRows = 0;
            int skippedRows = 0;
            
            while (rowIterator.hasNext()) {
                rowNum++;
                Row row = rowIterator.next();
                
                String dtr = getCellString(row.getCell(colDTR));
                String inv = getCellString(row.getCell(colINV));
                String po = getCellString(row.getCell(colPO));
                Double invAmt = getCellDouble(row.getCell(colAMT));
                
                LocalDate date = null;
                Cell dateCell = row.getCell(colDATE);
                if (dateCell != null) {
                    if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                        date = dateCell.getDateCellValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    } else {
                        String dateStr = getCellString(dateCell);
                        for (DateTimeFormatter fmt : fmts) {
                            try { 
                                date = LocalDate.parse(dateStr, fmt); 
                                break; 
                            } catch (Exception ignore) { }
                        }
                    }
                }
                
                // Validate row
                if (isValidDataRow(dtr, inv, po, invAmt)) {
                    result.add(new MyTriumphRecord(0, dtr, inv, date, po, invAmt));
                    validRows++;
                    logger.debug("Row {} - Valid record: PO={}, Invoice={}, Amount={}", rowNum, po, inv, invAmt);
                } else {
                    skippedRows++;
                    logger.debug("Row {} - Skipped: DTR={}, INV={}, PO={}, AMT={}", rowNum, dtr, inv, po, invAmt);
                }
            }
            
            logger.info("Import complete - Total rows: {}, Valid: {}, Skipped: {}", 
                rowNum-1, validRows, skippedRows);
        }
        return result;
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double val = cell.getNumericCellValue();
                if (Math.floor(val) == val) {
                    return String.valueOf((long)val);
                }
                return String.valueOf(val);
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
    }

    private static Double getCellDouble(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                String str = cell.getStringCellValue()
                    .replace("$", "")
                    .replace(",", "")
                    .replace(" ", "")
                    .trim();
                try { 
                    return Double.parseDouble(str); 
                } catch (Exception e) { 
                    return null; 
                }
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private static boolean isValidDataRow(String dtr, String inv, String po, Double amt) {
        // Basic validation
        if (dtr == null || dtr.trim().isEmpty()) return false;
        if (inv == null || inv.trim().isEmpty()) return false;
        if (po == null || po.trim().isEmpty()) return false;
        if (amt == null || amt <= 0) return false;
        
        // Ignore footer/header rows
        String dtrUpper = dtr.toUpperCase();
        String invUpper = inv.toUpperCase();
        String poUpper = po.toUpperCase();
        
        // Common footer text to ignore
        if (dtrUpper.contains("FOR VALUABLE") || dtrUpper.contains("AUTHORIZATION") || 
            dtrUpper.contains("TOTAL") || dtrUpper.contains("SUBTOTAL") ||
            dtrUpper.contains("GRAND TOTAL") || dtrUpper.equals("DTR_NAME")) return false;
            
        if (invUpper.contains("SCHEDULE") || invUpper.equals("INVOICE#") || 
            invUpper.contains("PAGE") || invUpper.contains("TOTAL")) return false;
            
        if (poUpper.contains("DATE") || poUpper.equals("PO") || 
            poUpper.contains("TOTAL") || poUpper.contains("PAGE")) return false;
        
        return true;
    }
}