package com.company.payroll.export;

import com.company.payroll.driver.DriverIncomeData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class ExcelExporter {
    
    public void exportDriverIncome(List<DriverIncomeData> data, File file) {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fileOut = new FileOutputStream(file)) {
            
            Sheet sheet = workbook.createSheet("Driver Income");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Driver", "Truck/Unit", "Total Loads", "Total Gross", 
                              "Total Miles", "Fuel Cost", "Avg/Mile", "Net Pay"};
            
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            for (DriverIncomeData income : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(income.getDriverName());
                row.createCell(1).setCellValue(income.getTruckUnit());
                row.createCell(2).setCellValue(income.getTotalLoads());
                row.createCell(3).setCellValue(income.getTotalGross());
                row.createCell(4).setCellValue(income.getTotalMiles());
                row.createCell(5).setCellValue(income.getTotalFuelAmount());
                row.createCell(6).setCellValue(income.getAveragePerMile());
                row.createCell(7).setCellValue(income.getNetPay());
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(fileOut);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export to Excel", e);
        }
    }
}