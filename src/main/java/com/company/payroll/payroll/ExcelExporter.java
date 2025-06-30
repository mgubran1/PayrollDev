package com.company.payroll.payroll;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Professional Excel exporter for payroll data with advanced formatting and multiple sheet support.
 * Generates comprehensive Excel workbooks with summary, detail, and analysis sheets.
 */
public class ExcelExporter {
    private static final Logger logger = LoggerFactory.getLogger(ExcelExporter.class);
    
    private final String companyName;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
    
    // Cell styles
    private CellStyle headerStyle;
    private CellStyle titleStyle;
    private CellStyle subtitleStyle;
    private CellStyle currencyStyle;
    private CellStyle percentStyle;
    private CellStyle dateStyle;
    private CellStyle numberStyle;
    private CellStyle totalStyle;
    private CellStyle highlightStyle;
    private CellStyle warningStyle;
    private CellStyle successStyle;
    
    public ExcelExporter(String companyName) {
        this.companyName = companyName;
        logger.info("ExcelExporter initialized for company: {}", companyName);
    }
    
    /**
     * Export comprehensive payroll data to Excel with multiple sheets
     */
    public void exportPayrollWorkbook(File outputFile, List<PayrollCalculator.PayrollRow> payrollRows,
                                    Map<String, com.company.payroll.employees.Employee> employeeMap,
                                    Map<String, Double> totals, LocalDate weekStart) throws IOException {
        logger.info("Exporting payroll workbook for {} employees, week: {}", payrollRows.size(), weekStart);
        
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Initialize styles
            initializeStyles(workbook);
            
            // Create sheets
            createSummarySheet(workbook, payrollRows, totals, weekStart);
            createDetailSheet(workbook, payrollRows, employeeMap);
            createDeductionsSheet(workbook, payrollRows);
            createAnalysisSheet(workbook, payrollRows, totals);
            createPayStubsSheet(workbook, payrollRows, employeeMap, weekStart);
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
            
            logger.info("Payroll workbook exported successfully to: {}", outputFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Failed to export payroll workbook", e);
            throw new IOException("Failed to export payroll workbook: " + e.getMessage(), e);
        }
    }
    
    /**
     * Export simple payroll summary to Excel
     */
    public void exportPayrollSummary(File outputFile, List<PayrollCalculator.PayrollRow> payrollRows,
                                   Map<String, Double> totals, LocalDate weekStart) throws IOException {
        logger.info("Exporting payroll summary for week: {}", weekStart);
        
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            initializeStyles(workbook);
            createSummarySheet(workbook, payrollRows, totals, weekStart);
            
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
            
            logger.info("Payroll summary exported successfully");
            
        } catch (IOException e) {
            logger.error("Failed to export payroll summary", e);
            throw new IOException("Failed to export payroll summary: " + e.getMessage(), e);
        }
    }
    
    private void initializeStyles(XSSFWorkbook workbook) {
        // Header style
        headerStyle = workbook.createCellStyle();
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{33, 115, 189}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Title style
        titleStyle = workbook.createCellStyle();
        XSSFFont titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 18);
        titleFont.setColor(new XSSFColor(new byte[]{33, 115, 189}, null));
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        
        // Subtitle style
        subtitleStyle = workbook.createCellStyle();
        XSSFFont subtitleFont = workbook.createFont();
        subtitleFont.setBold(true);
        subtitleFont.setFontHeightInPoints((short) 14);
        subtitleStyle.setFont(subtitleFont);
        subtitleStyle.setAlignment(HorizontalAlignment.LEFT);
        
        // Currency style
        currencyStyle = workbook.createCellStyle();
        currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
        currencyStyle.setBorderBottom(BorderStyle.THIN);
        currencyStyle.setBorderTop(BorderStyle.THIN);
        currencyStyle.setBorderRight(BorderStyle.THIN);
        currencyStyle.setBorderLeft(BorderStyle.THIN);
        
        // Percent style
        percentStyle = workbook.createCellStyle();
        percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
        percentStyle.setBorderBottom(BorderStyle.THIN);
        percentStyle.setBorderTop(BorderStyle.THIN);
        percentStyle.setBorderRight(BorderStyle.THIN);
        percentStyle.setBorderLeft(BorderStyle.THIN);
        
        // Date style
        dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("MM/dd/yyyy"));
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setBorderTop(BorderStyle.THIN);
        dateStyle.setBorderRight(BorderStyle.THIN);
        dateStyle.setBorderLeft(BorderStyle.THIN);
        
        // Number style
        numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        numberStyle.setBorderBottom(BorderStyle.THIN);
        numberStyle.setBorderTop(BorderStyle.THIN);
        numberStyle.setBorderRight(BorderStyle.THIN);
        numberStyle.setBorderLeft(BorderStyle.THIN);
        numberStyle.setAlignment(HorizontalAlignment.CENTER);
        
        // Total style
        totalStyle = workbook.createCellStyle();
        totalStyle.cloneStyleFrom(currencyStyle);
        XSSFFont totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalStyle.setFont(totalFont);
        totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)240, (byte)240, (byte)240}, null));
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Highlight style
        highlightStyle = workbook.createCellStyle();
        highlightStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255, (byte)255, (byte)204}, null));
        highlightStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Warning style
        warningStyle = workbook.createCellStyle();
        warningStyle.cloneStyleFrom(currencyStyle);
        XSSFFont warningFont = workbook.createFont();
        warningFont.setColor(new XSSFColor(new byte[]{(byte)255, 0, 0}, null));
        warningStyle.setFont(warningFont);
        
        // Success style
        successStyle = workbook.createCellStyle();
        successStyle.cloneStyleFrom(currencyStyle);
        XSSFFont successFont = workbook.createFont();
        successFont.setColor(new XSSFColor(new byte[]{0, (byte)128, 0}, null));
        successStyle.setFont(successFont);
    }
    
    private void createSummarySheet(XSSFWorkbook workbook, List<PayrollCalculator.PayrollRow> payrollRows,
                                  Map<String, Double> totals, LocalDate weekStart) {
        XSSFSheet sheet = workbook.createSheet("Summary");
        int rowNum = 0;
        
        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(companyName + " - Payroll Summary");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));
        
        // Week info
        Row weekRow = sheet.createRow(rowNum++);
        Cell weekCell = weekRow.createCell(0);
        LocalDate weekEnd = weekStart.plusDays(6);
        weekCell.setCellValue("Week: " + weekStart.format(dateFormatter) + " - " + weekEnd.format(dateFormatter));
        weekCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 10));
        
        rowNum++; // Empty row
        
        // Summary statistics section
        Row statsHeaderRow = sheet.createRow(rowNum++);
        Cell statsHeaderCell = statsHeaderRow.createCell(0);
        statsHeaderCell.setCellValue("Summary Statistics");
        statsHeaderCell.setCellStyle(subtitleStyle);
        
        // Statistics
        createStatisticRow(sheet, rowNum++, "Total Drivers:", payrollRows.size(), numberStyle);
        createStatisticRow(sheet, rowNum++, "Total Gross Pay:", totals.getOrDefault("gross", 0.0), currencyStyle);
        createStatisticRow(sheet, rowNum++, "Total Service Fees:", totals.getOrDefault("serviceFee", 0.0), currencyStyle);
        createStatisticRow(sheet, rowNum++, "Total Fuel Costs:", totals.getOrDefault("fuel", 0.0), currencyStyle);
        createStatisticRow(sheet, rowNum++, "Total Deductions:", calculateTotalDeductions(totals), currencyStyle);
        createStatisticRow(sheet, rowNum++, "Total Reimbursements:", totals.getOrDefault("reimbursements", 0.0), currencyStyle);
        createStatisticRow(sheet, rowNum++, "Total Net Pay:", totals.getOrDefault("netPay", 0.0), 
                         totals.getOrDefault("netPay", 0.0) >= 0 ? successStyle : warningStyle);
        
        rowNum += 2; // Empty rows
        
        // Driver summary table
        Row driverHeaderRow = sheet.createRow(rowNum++);
        Cell driverHeaderCell = driverHeaderRow.createCell(0);
        driverHeaderCell.setCellValue("Driver Summary");
        driverHeaderCell.setCellStyle(subtitleStyle);
        
        // Table headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Driver", "Truck/Unit", "Loads", "Gross Pay", "Service Fee", 
                          "Fuel", "Deductions", "Reimbursements", "Net Pay", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Data rows
        for (PayrollCalculator.PayrollRow row : payrollRows) {
            Row dataRow = sheet.createRow(rowNum++);
            
            dataRow.createCell(0).setCellValue(row.driverName);
            dataRow.createCell(1).setCellValue(row.truckUnit);
            
            Cell loadCell = dataRow.createCell(2);
            loadCell.setCellValue(row.loadCount);
            loadCell.setCellStyle(numberStyle);
            
            createCurrencyCell(dataRow, 3, row.gross, currencyStyle);
            createCurrencyCell(dataRow, 4, row.serviceFee, currencyStyle);
            createCurrencyCell(dataRow, 5, row.fuel, currencyStyle);
            
            double totalDeductions = Math.abs(row.recurringFees) + Math.abs(row.advanceRepayments) + 
                                   Math.abs(row.escrowDeposits) + Math.abs(row.otherDeductions);
            createCurrencyCell(dataRow, 6, -totalDeductions, currencyStyle);
            createCurrencyCell(dataRow, 7, row.reimbursements, currencyStyle);
            createCurrencyCell(dataRow, 8, row.netPay, row.netPay >= 0 ? currencyStyle : warningStyle);
            
            Cell statusCell = dataRow.createCell(9);
            if (row.netPay < 0) {
                statusCell.setCellValue("Negative");
                statusCell.setCellStyle(warningStyle);
            } else if (row.netPay < 500) {
                statusCell.setCellValue("Low");
                statusCell.setCellStyle(highlightStyle);
            } else {
                statusCell.setCellValue("Normal");
            }
        }
        
        // Totals row
        Row totalRow = sheet.createRow(rowNum++);
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTALS");
        totalLabelCell.setCellStyle(totalStyle);
        
        createCurrencyCell(totalRow, 3, totals.getOrDefault("gross", 0.0), totalStyle);
        createCurrencyCell(totalRow, 4, totals.getOrDefault("serviceFee", 0.0), totalStyle);
        createCurrencyCell(totalRow, 5, totals.getOrDefault("fuel", 0.0), totalStyle);
        createCurrencyCell(totalRow, 6, -calculateTotalDeductions(totals), totalStyle);
        createCurrencyCell(totalRow, 7, totals.getOrDefault("reimbursements", 0.0), totalStyle);
        createCurrencyCell(totalRow, 8, totals.getOrDefault("netPay", 0.0), totalStyle);
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Set print settings
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }
    
    private void createDetailSheet(XSSFWorkbook workbook, List<PayrollCalculator.PayrollRow> payrollRows,
                                 Map<String, com.company.payroll.employees.Employee> employeeMap) {
        XSSFSheet sheet = workbook.createSheet("Detailed Breakdown");
        int rowNum = 0;
        
        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Detailed Payroll Breakdown");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 16));
        
        rowNum++; // Empty row
        
        // Headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Driver", "Employee ID", "Truck/Unit", "Loads", "Gross Pay", 
                          "Service Fee", "Gross After Fee", "Company Pay", "Driver Pay",
                          "Fuel", "Gross After Fuel", "Recurring Fees", "Advances Given",
                          "Advance Repayments", "Escrow", "Other Deductions", "Reimbursements", "NET PAY"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Data rows
        for (PayrollCalculator.PayrollRow row : payrollRows) {
            Row dataRow = sheet.createRow(rowNum++);
            int col = 0;
            
            dataRow.createCell(col++).setCellValue(row.driverName);
            
            com.company.payroll.employees.Employee emp = employeeMap.get(row.driverName);
            if (emp != null) {
                dataRow.createCell(col++).setCellValue(emp.getId());
            } else {
                dataRow.createCell(col++).setCellValue("");
            }
            
            dataRow.createCell(col++).setCellValue(row.truckUnit);
            
            Cell loadCell = dataRow.createCell(col++);
            loadCell.setCellValue(row.loadCount);
            loadCell.setCellStyle(numberStyle);
            
            // Financial columns
            createCurrencyCell(dataRow, col++, row.gross, currencyStyle);
            createCurrencyCell(dataRow, col++, row.serviceFee, currencyStyle);
            createCurrencyCell(dataRow, col++, row.grossAfterServiceFee, currencyStyle);
            createCurrencyCell(dataRow, col++, row.companyPay, currencyStyle);
            createCurrencyCell(dataRow, col++, row.driverPay, currencyStyle);
            createCurrencyCell(dataRow, col++, row.fuel, currencyStyle);
            createCurrencyCell(dataRow, col++, row.grossAfterFuel, currencyStyle);
            createCurrencyCell(dataRow, col++, row.recurringFees, currencyStyle);
            createCurrencyCell(dataRow, col++, row.advancesGiven, row.advancesGiven > 0 ? highlightStyle : currencyStyle);
            createCurrencyCell(dataRow, col++, row.advanceRepayments, currencyStyle);
            createCurrencyCell(dataRow, col++, row.escrowDeposits, currencyStyle);
            createCurrencyCell(dataRow, col++, row.otherDeductions, currencyStyle);
            createCurrencyCell(dataRow, col++, row.reimbursements, row.reimbursements > 0 ? successStyle : currencyStyle);
            createCurrencyCell(dataRow, col++, row.netPay, row.netPay >= 0 ? successStyle : warningStyle);
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            // Ensure minimum width
            if (sheet.getColumnWidth(i) < 2500) {
                sheet.setColumnWidth(i, 2500);
            }
        }
        
        // Freeze panes (header row)
        sheet.createFreezePane(0, 3);
    }
    
    private void createDeductionsSheet(XSSFWorkbook workbook, List<PayrollCalculator.PayrollRow> payrollRows) {
        XSSFSheet sheet = workbook.createSheet("Deductions Analysis");
        int rowNum = 0;
        
        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Deductions Analysis");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
        
        rowNum++; // Empty row
        
        // Headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Driver", "Fuel", "Recurring Fees", "Advance Repayments", 
                          "Escrow Deposits", "Other Deductions", "Total Deductions", 
                          "% of Gross", "Impact"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Data rows
        for (PayrollCalculator.PayrollRow row : payrollRows) {
            Row dataRow = sheet.createRow(rowNum++);
            
            dataRow.createCell(0).setCellValue(row.driverName);
            
            createCurrencyCell(dataRow, 1, Math.abs(row.fuel), currencyStyle);
            createCurrencyCell(dataRow, 2, Math.abs(row.recurringFees), currencyStyle);
            createCurrencyCell(dataRow, 3, Math.abs(row.advanceRepayments), currencyStyle);
            createCurrencyCell(dataRow, 4, Math.abs(row.escrowDeposits), currencyStyle);
            createCurrencyCell(dataRow, 5, Math.abs(row.otherDeductions), currencyStyle);
            
            double totalDeductions = Math.abs(row.fuel) + Math.abs(row.recurringFees) + 
                                   Math.abs(row.advanceRepayments) + Math.abs(row.escrowDeposits) + 
                                   Math.abs(row.otherDeductions);
            createCurrencyCell(dataRow, 6, totalDeductions, totalStyle);
            
            // Percentage of gross
            Cell percentCell = dataRow.createCell(7);
            if (row.gross > 0) {
                percentCell.setCellValue(totalDeductions / row.gross);
                percentCell.setCellStyle(percentStyle);
            } else {
                percentCell.setCellValue("N/A");
            }
            
            // Impact assessment
            Cell impactCell = dataRow.createCell(8);
            double deductionPercent = row.gross > 0 ? (totalDeductions / row.gross) * 100 : 0;
            if (deductionPercent > 50) {
                impactCell.setCellValue("High");
                impactCell.setCellStyle(warningStyle);
            } else if (deductionPercent > 30) {
                impactCell.setCellValue("Medium");
                impactCell.setCellStyle(highlightStyle);
            } else {
                impactCell.setCellValue("Low");
                impactCell.setCellStyle(successStyle);
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Freeze panes
        sheet.createFreezePane(1, 3);
    }
    
	private void createAnalysisSheet(XSSFWorkbook workbook, List<PayrollCalculator.PayrollRow> payrollRows,
								   Map<String, Double> totals) {
		XSSFSheet sheet = workbook.createSheet("Analysis");
		int rowNum = 0;
		
		// Title
		Row titleRow = sheet.createRow(rowNum++);
		Cell titleCell = titleRow.createCell(0);
		titleCell.setCellValue("Payroll Analysis");
		titleCell.setCellStyle(titleStyle);
		sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
		
		rowNum++; // Empty row
		
		// Key metrics
		Row metricsHeaderRow = sheet.createRow(rowNum++);
		Cell metricsHeaderCell = metricsHeaderRow.createCell(0);
		metricsHeaderCell.setCellValue("Key Metrics");
		metricsHeaderCell.setCellStyle(subtitleStyle);
		
		double avgGross = payrollRows.isEmpty() ? 0 : totals.getOrDefault("gross", 0.0) / payrollRows.size();
		double avgNet = payrollRows.isEmpty() ? 0 : totals.getOrDefault("netPay", 0.0) / payrollRows.size();
		double avgLoads = payrollRows.isEmpty() ? 0 : payrollRows.stream().mapToInt(r -> r.loadCount).sum() / (double) payrollRows.size();
		
		createStatisticRow(sheet, rowNum++, "Average Gross Pay:", avgGross, currencyStyle);
		createStatisticRow(sheet, rowNum++, "Average Net Pay:", avgNet, currencyStyle);
		createStatisticRow(sheet, rowNum++, "Average Loads per Driver:", avgLoads, numberStyle);
		
		rowNum++; // Empty row
		
		// Performance rankings
		Row rankingsHeaderRow = sheet.createRow(rowNum++);
		Cell rankingsHeaderCell = rankingsHeaderRow.createCell(0);
		rankingsHeaderCell.setCellValue("Performance Rankings");
		rankingsHeaderCell.setCellStyle(subtitleStyle);
		
		// Top performers by net pay
		Row topPerformersRow = sheet.createRow(rowNum++);
		Cell topPerformersCell = topPerformersRow.createCell(0);
		topPerformersCell.setCellValue("Top 5 Performers (by Net Pay)");
		topPerformersCell.setCellStyle(headerStyle);
		sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));
		
		List<PayrollCalculator.PayrollRow> sortedByNet = payrollRows.stream()
			.sorted((a, b) -> Double.compare(b.netPay, a.netPay))
			.limit(5)
			.collect(java.util.stream.Collectors.toList());
		
		// Headers for top performers
		Row performerHeaderRow = sheet.createRow(rowNum++);
		performerHeaderRow.createCell(0).setCellValue("Rank");
		performerHeaderRow.createCell(1).setCellValue("Driver");
		performerHeaderRow.createCell(2).setCellValue("Net Pay");
		for (int i = 0; i < 3; i++) {
			performerHeaderRow.getCell(i).setCellStyle(headerStyle);
		}
		
		// Top performers data
		int rank = 1;
		for (PayrollCalculator.PayrollRow row : sortedByNet) {
			Row dataRow = sheet.createRow(rowNum++);
			dataRow.createCell(0).setCellValue(rank++);
			dataRow.createCell(1).setCellValue(row.driverName);
			createCurrencyCell(dataRow, 2, row.netPay, successStyle);
		}
		
		rowNum++; // Empty row
		
		// Bottom performers
		Row bottomPerformersRow = sheet.createRow(rowNum++);
		Cell bottomPerformersCell = bottomPerformersRow.createCell(0);
		bottomPerformersCell.setCellValue("Bottom 5 Performers (by Net Pay)");
		bottomPerformersCell.setCellStyle(headerStyle);
		sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));
		
		List<PayrollCalculator.PayrollRow> sortedByNetAsc = payrollRows.stream()
			.sorted((a, b) -> Double.compare(a.netPay, b.netPay))
			.limit(5)
			.collect(java.util.stream.Collectors.toList());
		
		// Headers for bottom performers
		Row bottomHeaderRow = sheet.createRow(rowNum++);
		bottomHeaderRow.createCell(0).setCellValue("Rank");
		bottomHeaderRow.createCell(1).setCellValue("Driver");
		bottomHeaderRow.createCell(2).setCellValue("Net Pay");
		for (int i = 0; i < 3; i++) {
			bottomHeaderRow.getCell(i).setCellStyle(headerStyle);
		}
		
		// Bottom performers data
		rank = 1;
		for (PayrollCalculator.PayrollRow row : sortedByNetAsc) {
			Row dataRow = sheet.createRow(rowNum++);
			dataRow.createCell(0).setCellValue(rank++);
			dataRow.createCell(1).setCellValue(row.driverName);
			createCurrencyCell(dataRow, 2, row.netPay, row.netPay < 0 ? warningStyle : currencyStyle);
		}
		
		// Auto-size columns
		for (int i = 0; i < 5; i++) {
			sheet.autoSizeColumn(i);
		}
	}

	private void createPayStubsSheet(XSSFWorkbook workbook, List<PayrollCalculator.PayrollRow> payrollRows,
								   Map<String, com.company.payroll.employees.Employee> employeeMap,
								   LocalDate weekStart) {
		XSSFSheet sheet = workbook.createSheet("Pay Stubs");
		int rowNum = 0;
		
		LocalDate weekEnd = weekStart.plusDays(6);
		
		for (PayrollCalculator.PayrollRow row : payrollRows) {
			// Driver header
			Row driverRow = sheet.createRow(rowNum++);
			Cell driverCell = driverRow.createCell(0);
			driverCell.setCellValue("PAY STUB - " + row.driverName.toUpperCase());
			driverCell.setCellStyle(titleStyle);
			sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 6));
			
			// Pay period
			Row periodRow = sheet.createRow(rowNum++);
			periodRow.createCell(0).setCellValue("Pay Period:");
			periodRow.createCell(1).setCellValue(weekStart.format(dateFormatter) + " - " + weekEnd.format(dateFormatter));
			
			// Employee info
			com.company.payroll.employees.Employee emp = employeeMap.get(row.driverName);
			if (emp != null) {
				Row empIdRow = sheet.createRow(rowNum++);
				empIdRow.createCell(0).setCellValue("Employee ID:");
				empIdRow.createCell(1).setCellValue(emp.getId());
			}
			
			Row unitRow = sheet.createRow(rowNum++);
			unitRow.createCell(0).setCellValue("Truck/Unit:");
			unitRow.createCell(1).setCellValue(row.truckUnit);
			
			rowNum++; // Empty row
			
			// Earnings
			Row earningsHeaderRow = sheet.createRow(rowNum++);
			Cell earningsHeaderCell = earningsHeaderRow.createCell(0);
			earningsHeaderCell.setCellValue("EARNINGS");
			earningsHeaderCell.setCellStyle(headerStyle);
			sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));
			
			createPayStubLine(sheet, rowNum++, "Gross Pay:", row.gross, currencyStyle);
			createPayStubLine(sheet, rowNum++, "Service Fee:", -row.serviceFee, currencyStyle);
			createPayStubLine(sheet, rowNum++, "Driver Pay:", row.driverPay, currencyStyle);
			
			rowNum++; // Empty row
			
			// Deductions
			Row deductionsHeaderRow = sheet.createRow(rowNum++);
			Cell deductionsHeaderCell = deductionsHeaderRow.createCell(0);
			deductionsHeaderCell.setCellValue("DEDUCTIONS");
			deductionsHeaderCell.setCellStyle(headerStyle);
			sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));
			
			createPayStubLine(sheet, rowNum++, "Fuel:", -Math.abs(row.fuel), currencyStyle);
			createPayStubLine(sheet, rowNum++, "Recurring Fees:", -Math.abs(row.recurringFees), currencyStyle);
			createPayStubLine(sheet, rowNum++, "Advance Repayments:", -Math.abs(row.advanceRepayments), currencyStyle);
			createPayStubLine(sheet, rowNum++, "Escrow Deposits:", -Math.abs(row.escrowDeposits), currencyStyle);
			createPayStubLine(sheet, rowNum++, "Other Deductions:", -Math.abs(row.otherDeductions), currencyStyle);
			
			// Reimbursements
			if (row.reimbursements > 0) {
				rowNum++; // Empty row
				Row reimbursementsHeaderRow = sheet.createRow(rowNum++);
				Cell reimbursementsHeaderCell = reimbursementsHeaderRow.createCell(0);
				reimbursementsHeaderCell.setCellValue("REIMBURSEMENTS");
				reimbursementsHeaderCell.setCellStyle(headerStyle);
				sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));
				
				createPayStubLine(sheet, rowNum++, "Reimbursements:", row.reimbursements, successStyle);
			}
			
			rowNum++; // Empty row
			
			// Net pay
			Row netPayRow = sheet.createRow(rowNum++);
			Cell netPayLabelCell = netPayRow.createCell(0);
			netPayLabelCell.setCellValue("NET PAY:");
			netPayLabelCell.setCellStyle(totalStyle);
			
			Cell netPayValueCell = netPayRow.createCell(2);
			netPayValueCell.setCellValue(row.netPay);
			netPayValueCell.setCellStyle(row.netPay >= 0 ? totalStyle : warningStyle);
			
			// Add separator between pay stubs
			rowNum += 3; // Empty rows
		}
		
		// Auto-size columns
		for (int i = 0; i < 7; i++) {
			sheet.autoSizeColumn(i);
		}
	}

	// Helper methods
	private void createStatisticRow(Sheet sheet, int rowNum, String label, double value, CellStyle style) {
		Row row = sheet.createRow(rowNum);
		row.createCell(0).setCellValue(label);
		Cell valueCell = row.createCell(1);
		valueCell.setCellValue(value);
		valueCell.setCellStyle(style);
	}

	private void createStatisticRow(Sheet sheet, int rowNum, String label, int value, CellStyle style) {
		Row row = sheet.createRow(rowNum);
		row.createCell(0).setCellValue(label);
		Cell valueCell = row.createCell(1);
		valueCell.setCellValue(value);
		valueCell.setCellStyle(style);
	}

	private void createCurrencyCell(Row row, int column, double value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value);
		cell.setCellStyle(style);
	}

	private void createPayStubLine(Sheet sheet, int rowNum, String label, double value, CellStyle style) {
		Row row = sheet.createRow(rowNum);
		row.createCell(0).setCellValue(label);
		Cell valueCell = row.createCell(2);
		valueCell.setCellValue(value);
		valueCell.setCellStyle(style);
	}

	private double calculateTotalDeductions(Map<String, Double> totals) {
		return Math.abs(totals.getOrDefault("fuel", 0.0)) +
			   Math.abs(totals.getOrDefault("recurringFees", 0.0)) +
			   Math.abs(totals.getOrDefault("advanceRepayments", 0.0)) +
			   Math.abs(totals.getOrDefault("escrowDeposits", 0.0)) +
			   Math.abs(totals.getOrDefault("otherDeductions", 0.0));
	}