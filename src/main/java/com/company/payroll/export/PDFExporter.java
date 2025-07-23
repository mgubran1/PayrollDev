package com.company.payroll.export;

import com.company.payroll.driver.DriverIncomeData;
import com.company.payroll.employees.Employee;
import com.company.payroll.payroll.PayrollCalculator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import javafx.collections.ObservableList;
import com.company.payroll.payroll.CompanyFinancialsTab.BreakdownRow;
import com.company.payroll.payroll.CompanyFinancialsTab.FinancialRow;
import com.company.payroll.expenses.CompanyExpense;
import com.company.payroll.maintenance.MaintenanceRecord;
import java.awt.Color;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.TreeMap;

/**
 * PDF exporter with support for payroll and driver income reports
 */
public class PDFExporter {
    private static final Logger logger = LoggerFactory.getLogger(PDFExporter.class);
    
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 50f;
    private static final float MARGIN_LEFT = 50f;
    private static final float MARGIN_RIGHT = 50f;
    private static final float LINE_HEIGHT = 18f;
    
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    
    private final String companyName;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
    
    private PDType1Font fontBold;
    private PDType1Font fontNormal;
    
    // Constructor with company name
    public PDFExporter(String companyName) {
        this.companyName = companyName;
        initializeFonts();
    }
    
    // Default constructor
    public PDFExporter() {
        this.companyName = "Company";
        initializeFonts();
    }
    
    private void initializeFonts() {
        fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }
    
    /**
     * Generate batch PDF pay stubs
     */
    public void generateBatchPayStubs(File file, List<PayrollCalculator.PayrollRow> payrollRows, 
                                     Map<String, Employee> employeeMap, LocalDate weekStart) 
                                     throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (PayrollCalculator.PayrollRow row : payrollRows) {
                Employee driver = employeeMap.get(row.driverName);
                if (driver != null) {
                    addPayStubPage(document, driver, row, weekStart);
                }
            }
            
            document.save(file);
        }
    }
    
    /**
     * Generate a single pay stub PDF
     */
    public void generatePayStub(File file, Employee driver, PayrollCalculator.PayrollRow payrollRow,
                              LocalDate weekStart) throws IOException {
        try (PDDocument document = new PDDocument()) {
            addPayStubPage(document, driver, payrollRow, weekStart);
            document.save(file);
        }
    }
    
    private void addPayStubPage(PDDocument document, Employee driver, PayrollCalculator.PayrollRow row, 
                              LocalDate weekStart) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            float margin = 50;
            float yPosition = page.getMediaBox().getHeight() - margin;
            float width = page.getMediaBox().getWidth() - 2 * margin;
            
            // Company header
            contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
            contentStream.beginText();
            contentStream.setFont(fontBold, 24);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText(companyName);
            contentStream.endText();
            
            // Pay stub title
            yPosition -= 30;
            contentStream.setNonStrokingColor(66f/255f, 66f/255f, 66f/255f);
            contentStream.beginText();
            contentStream.setFont(fontBold, 18);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("EARNINGS STATEMENT");
            contentStream.endText();
            
            // Employee info
            yPosition -= 30;
            contentStream.setNonStrokingColor(0, 0, 0);
            contentStream.beginText();
            contentStream.setFont(fontBold, 12);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("Employee: " + driver.getName());
            contentStream.endText();
            
            // Pay period
            yPosition -= 20;
            LocalDate weekEnd = weekStart.plusDays(6);
            contentStream.beginText();
            contentStream.setFont(fontNormal, 12);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("Pay Period: " + weekStart.format(dateFormatter) + 
                                  " - " + weekEnd.format(dateFormatter));
            contentStream.endText();
            
            // Net pay
            yPosition -= 40;
            contentStream.beginText();
            contentStream.setFont(fontBold, 14);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("NET PAY: $" + String.format("%,.2f", row.netPay));
            contentStream.endText();
        }
    }
    
    /**
     * Export driver income history to PDF
     */
    public void exportDriverIncomeHistory(File file, List<PayrollCalculator.PayrollRow> rows, 
                                        Employee driver) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                
                // Title
                contentStream.setFont(fontBold, 20);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
                contentStream.showText("Income History - " + driver.getName());
                contentStream.endText();
                
                // Additional implementation would go here
            }
            
            document.save(file);
        }
    }
    
    /**
     * Export driver income data to PDF
     */
    public void exportDriverIncome(List<DriverIncomeData> data, File file) throws IOException {
        logger.info("Exporting driver income report to PDF: {}", file.getAbsolutePath());
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            try {
                float yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                float pageWidth = page.getMediaBox().getWidth();
                float contentWidth = pageWidth - MARGIN_LEFT - MARGIN_RIGHT;
                
                // Title
                contentStream.setFont(fontBold, 20);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
                contentStream.showText("Driver Income Report");
                contentStream.endText();
                
                yPosition -= 40;
                
                // Table headers
                yPosition = drawTableHeaders(contentStream, yPosition, contentWidth);
                
                // Table data
                contentStream.setFont(fontNormal, 10);
                
                for (DriverIncomeData income : data) {
                    if (yPosition < MARGIN_BOTTOM + 50) {
                        // Create new page if needed
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                        yPosition = drawTableHeaders(contentStream, yPosition, contentWidth);
                        contentStream.setFont(fontNormal, 10);
                    }
                    
                    yPosition = drawIncomeRow(contentStream, yPosition, income);
                }
            } finally {
                contentStream.close();
            }
            
            document.save(file);
            logger.info("Driver income report saved successfully");
            
        } catch (IOException e) {
            logger.error("Failed to export driver income to PDF", e);
            throw new IOException("Failed to export to PDF: " + e.getMessage(), e);
        }
    }
    
    private float drawTableHeaders(PDPageContentStream stream, float yPosition, float contentWidth) throws IOException {
        stream.setFont(fontBold, 10);
        
        float[] columnWidths = {100, 60, 50, 70, 60, 70, 60, 70};
        String[] headers = {"Driver", "Truck/Unit", "Loads", "Gross", "Miles", "Fuel", "Avg/Mile", "Net Pay"};
        
        // Draw header background
        stream.setNonStrokingColor(0.9f, 0.9f, 0.9f);
        stream.addRect(MARGIN_LEFT, yPosition - LINE_HEIGHT, contentWidth, LINE_HEIGHT);
        stream.fill();
        
        // Draw headers
        stream.setNonStrokingColor(0, 0, 0);
        float xPosition = MARGIN_LEFT + 5;
        
        for (int i = 0; i < headers.length; i++) {
            stream.beginText();
            stream.newLineAtOffset(xPosition, yPosition - 15);
            stream.showText(headers[i]);
            stream.endText();
            xPosition += columnWidths[i];
        }
        
        // Draw line under headers
        stream.setStrokingColor(0, 0, 0);
        stream.setLineWidth(0.5f);
        stream.moveTo(MARGIN_LEFT, yPosition - LINE_HEIGHT);
        stream.lineTo(MARGIN_LEFT + contentWidth, yPosition - LINE_HEIGHT);
        stream.stroke();
        
        return yPosition - LINE_HEIGHT - 5;
    }
    
    private float drawIncomeRow(PDPageContentStream stream, float yPosition, DriverIncomeData income) throws IOException {
        float[] columnWidths = {100, 60, 50, 70, 60, 70, 60, 70};
        float xPosition = MARGIN_LEFT + 5;
        
        String[] values = {
            truncateString(income.getDriverName(), 15),
            truncateString(income.getTruckUnit(), 8),
            String.valueOf(income.getTotalLoads()),
            CURRENCY_FORMAT.format(income.getTotalGross()),
            String.format("%.1f", income.getTotalMiles()),
            CURRENCY_FORMAT.format(income.getTotalFuelAmount()),
            String.format("$%.3f", income.getAveragePerMile()),
            CURRENCY_FORMAT.format(income.getNetPay())
        };
        
        // Alternate row coloring
        if ((int)(yPosition / LINE_HEIGHT) % 2 == 0) {
            stream.setNonStrokingColor(0.97f, 0.97f, 0.97f);
            stream.addRect(MARGIN_LEFT, yPosition - LINE_HEIGHT + 3, 
                          MARGIN_LEFT + MARGIN_RIGHT + 400, LINE_HEIGHT);
            stream.fill();
        }
        
        stream.setNonStrokingColor(0, 0, 0);
        
        for (int i = 0; i < values.length; i++) {
            stream.beginText();
            stream.newLineAtOffset(xPosition, yPosition - 15);
            stream.showText(values[i]);
            stream.endText();
            xPosition += columnWidths[i];
        }
        
        return yPosition - LINE_HEIGHT;
    }
    
    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Generate a company financials PDF report
     */
    public void generateCompanyFinancialsPDF(File file, String gross, String grossPeriod, String companyPercent, String serviceFee, ObservableList<BreakdownRow> breakdownRows) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float margin = 50;
                float yPosition = page.getMediaBox().getHeight() - margin;
                float width = page.getMediaBox().getWidth() - 2 * margin;

                // Header
                contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
                contentStream.beginText();
                contentStream.setFont(fontBold, 24);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(companyName);
                contentStream.endText();

                yPosition -= 30;
                contentStream.setNonStrokingColor(66f/255f, 66f/255f, 66f/255f);
                contentStream.beginText();
                contentStream.setFont(fontBold, 18);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Company Financials Summary");
                contentStream.endText();

                yPosition -= 30;
                contentStream.setNonStrokingColor(0, 0, 0);
                contentStream.setFont(fontNormal, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(gross);
                contentStream.endText();
                yPosition -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(grossPeriod);
                contentStream.endText();
                yPosition -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(companyPercent);
                contentStream.endText();
                yPosition -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(serviceFee);
                contentStream.endText();

                yPosition -= 30;
                // Table header
                contentStream.setFont(fontBold, 12);
                contentStream.setNonStrokingColor(0.9f, 0.9f, 0.9f);
                contentStream.addRect(margin, yPosition - 16, width, 16);
                contentStream.fill();
                contentStream.setNonStrokingColor(0, 0, 0);
                float x = margin + 5;
                String[] headers = {"Category", "Gross", "Service Fee", "Company Pay", "Company Net"};
                float[] colWidths = {120, 90, 90, 90, 100};
                for (int i = 0; i < headers.length; i++) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(x, yPosition - 4);
                    contentStream.showText(headers[i]);
                    contentStream.endText();
                    x += colWidths[i];
                }
                yPosition -= 16;
                // Table rows
                contentStream.setFont(fontNormal, 11);
                for (BreakdownRow row : breakdownRows) {
                    x = margin + 5;
                    String[] vals = {
                        row.categoryProperty().get(),
                        row.grossProperty().get(),
                        row.serviceFeeProperty().get(),
                        row.companyPayProperty().get(),
                        row.companyNetProperty().get()
                    };
                    for (int i = 0; i < vals.length; i++) {
                        contentStream.beginText();
                        contentStream.newLineAtOffset(x, yPosition - 4);
                        contentStream.showText(vals[i]);
                        contentStream.endText();
                        x += colWidths[i];
                    }
                    yPosition -= 16;
                    if (yPosition < margin + 50) break; // Avoid overflow for now
                }

                // Footer
                yPosition -= 30;
                contentStream.setFont(fontNormal, 10);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Generated on: " + java.time.LocalDateTime.now().format(dateTimeFormatter));
                contentStream.endText();
            }
            document.save(file);
        }
    }
    
    /**
     * Generate comprehensive financial report with multiple pages
     */
    public void generateComprehensiveFinancialReport(
        File file,
        LocalDate startDate,
        LocalDate endDate,
        String totalGross,
        String totalServiceFee,
        String totalCompanyPay,
        String totalCompanyNet,
        String totalExpenses,
        String finalNet,
        List<FinancialRow> revenueData,
        List<CompanyExpense> expenses,
        List<MaintenanceRecord> maintenance,
        ObservableList<BreakdownRow> breakdownRows
    ) throws IOException {
        
        try (PDDocument document = new PDDocument()) {
            float pageHeight = PDRectangle.A4.getHeight();
            float pageWidth = PDRectangle.A4.getWidth();
            float margin = 50;
            float contentWidth = pageWidth - 2 * margin;
            float yPosition;
            
            // Page 1: Executive Summary
            PDPage page1 = new PDPage(PDRectangle.A4);
            document.addPage(page1);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page1)) {
                yPosition = pageHeight - margin;
                
                // Company Header with gradient effect
                drawCompanyHeader(contentStream, margin, yPosition, contentWidth);
                yPosition -= 80;
                
                // Report Title
                contentStream.setNonStrokingColor(33f/255f, 33f/255f, 33f/255f);
                contentStream.beginText();
                contentStream.setFont(fontBold, 26);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Comprehensive Financial Report");
                contentStream.endText();
                
                yPosition -= 35;
                contentStream.beginText();
                contentStream.setFont(fontNormal, 14);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Reporting Period: " + startDate.format(dateFormatter) + " to " + endDate.format(dateFormatter));
                contentStream.endText();
                
                // Executive Summary Box
                yPosition -= 50;
                drawSummaryBox(contentStream, margin, yPosition, contentWidth, "EXECUTIVE SUMMARY", new Color(25, 118, 210));
                
                yPosition -= 40;
                String[][] summaryData = {
                    {"Total Gross Revenue:", totalGross},
                    {"Total Service Fee:", totalServiceFee},
                    {"Total Company Pay:", totalCompanyPay},
                    {"Total Company Revenue:", totalCompanyNet},
                    {"Total Expenses:", totalExpenses},
                    {"Net Profit:", finalNet}
                };
                
                for (String[] row : summaryData) {
                    drawSummaryRow(contentStream, margin + 20, yPosition, contentWidth - 40, row[0], row[1], 
                        row[0].equals("Net Profit:") ? new Color(27, 94, 32) : Color.BLACK);
                    yPosition -= 35;
                }
                
                // Key Metrics Section
                yPosition -= 30;
                drawSummaryBox(contentStream, margin, yPosition, contentWidth, "KEY PERFORMANCE INDICATORS", new Color(255, 111, 0));
                
                yPosition -= 40;
                // Calculate metrics
                double grossValue = parseMoneyValue(totalGross);
                double expensesValue = parseMoneyValue(totalExpenses);
                double netValue = parseMoneyValue(finalNet);
                double profitMargin = grossValue > 0 ? (netValue / grossValue) * 100 : 0;
                double expenseRatio = grossValue > 0 ? (expensesValue / grossValue) * 100 : 0;
                
                String[][] metricsData = {
                    {"Profit Margin:", String.format("%.2f%%", profitMargin)},
                    {"Expense Ratio:", String.format("%.2f%%", expenseRatio)},
                    {"Total Drivers:", String.valueOf(revenueData.stream().map(r -> r.driverProperty().get()).distinct().count())},
                    {"Total Expense Transactions:", String.valueOf(expenses.size() + maintenance.size())}
                };
                
                for (String[] row : metricsData) {
                    drawMetricRow(contentStream, margin + 20, yPosition, contentWidth - 40, row[0], row[1]);
                    yPosition -= 30;
                }
                
                // Add page footer
                drawPageFooter(contentStream, margin, 1, document.getNumberOfPages());
            }
            
            // Page 2: Revenue Details
            PDPage page2 = new PDPage(PDRectangle.A4);
            document.addPage(page2);
            
            PDPageContentStream contentStream = new PDPageContentStream(document, page2);
            try {
                yPosition = pageHeight - margin;
                
                // Page header
                drawPageHeader(contentStream, margin, yPosition, contentWidth, "Revenue Details");
                yPosition -= 60;
                
                // Revenue table headers
                String[] revenueHeaders = {"Driver", "Gross", "Service Fee", "Company Pay", "Company Net"};
                // Dynamically size columns to fit within the printable area to avoid
                // overlapping text. Percentages must sum to 1.0f
                float[] revenueColWidths = {
                    contentWidth * DRIVER_COLUMN_WIDTH_PERCENTAGE,  // Driver
                    contentWidth * OTHER_COLUMN_WIDTH_PERCENTAGE, // Gross
                    contentWidth * OTHER_COLUMN_WIDTH_PERCENTAGE, // Service Fee
                    contentWidth * OTHER_COLUMN_WIDTH_PERCENTAGE, // Company Pay
                    contentWidth * OTHER_COLUMN_WIDTH_PERCENTAGE  // Company Net
                };
                
                drawTableHeader(contentStream, margin, yPosition, revenueHeaders, revenueColWidths);
                yPosition -= 25;
                
                // Revenue table rows
                int rowCount = 0;
                boolean isEvenRow = false;
                
                for (FinancialRow row : revenueData) {
                    if (yPosition < margin + 50) {
                        // Add page footer
                        drawPageFooter(contentStream, margin, 2, document.getNumberOfPages());
                        
                        // Create new page
                        PDPage newPage = new PDPage(PDRectangle.A4);
                        document.addPage(newPage);
                        contentStream.close();
                        
                        contentStream = new PDPageContentStream(document, newPage);
                        yPosition = pageHeight - margin;
                        drawPageHeader(contentStream, margin, yPosition, contentWidth, "Revenue Details (Continued)");
                        yPosition -= 60;
                        drawTableHeader(contentStream, margin, yPosition, revenueHeaders, revenueColWidths);
                        yPosition -= 25;
                        continue;
                    }
                    
                    String[] values = {
                        row.driverProperty().get(),
                        row.grossProperty().get(),
                        row.serviceFeeProperty().get(),
                        row.companyPayProperty().get(),
                        row.companyNetProperty().get()
                    };
                    
                    drawTableRow(contentStream, margin, yPosition, values, revenueColWidths, isEvenRow);
                    yPosition -= 20;
                    isEvenRow = !isEvenRow;
                    rowCount++;
                }
                
                // Revenue summary
                yPosition -= 20;
                contentStream.setFont(fontBold, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Total Revenue Entries: " + rowCount);
                contentStream.endText();
                
                drawPageFooter(contentStream, margin, 2, document.getNumberOfPages());
            } finally {
                contentStream.close();
            }
            
            // Page 3: Expense Details
            PDPage page3 = new PDPage(PDRectangle.A4);
            document.addPage(page3);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page3)) {
                yPosition = pageHeight - margin;
                
                // Page header
                drawPageHeader(contentStream, margin, yPosition, contentWidth, "Company Expense Details");
                yPosition -= 60;
                
                // Company expenses section
                contentStream.setNonStrokingColor(211f/255f, 47f/255f, 47f/255f);
                contentStream.beginText();
                contentStream.setFont(fontBold, 16);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Company Expenses");
                contentStream.endText();
                contentStream.setNonStrokingColor(0, 0, 0);
                yPosition -= 30;
                
                // Expense table headers
                String[] expenseHeaders = {"Date", "Vendor", "Category", "Description", "Amount"};
                float[] expenseColWidths = {70, 100, 80, 180, 70};
                
                drawTableHeader(contentStream, margin, yPosition, expenseHeaders, expenseColWidths);
                yPosition -= 25;
                
                // Expense rows
                boolean isEvenRow = false;
                double totalExpenseAmount = 0;
                
                for (CompanyExpense expense : expenses) {
                    if (yPosition < margin + 50) {
                        drawPageFooter(contentStream, margin, 3, document.getNumberOfPages());
                        // Would need to handle page break here
                        break;
                    }
                    
                    String[] values = {
                        expense.getExpenseDate().format(dateFormatter),
                        truncateString(expense.getVendor(), 20),
                        expense.getCategory(),
                        truncateString(expense.getDescription(), 35),
                        CURRENCY_FORMAT.format(expense.getAmount())
                    };
                    
                    drawTableRow(contentStream, margin, yPosition, values, expenseColWidths, isEvenRow);
                    yPosition -= 20;
                    isEvenRow = !isEvenRow;
                    totalExpenseAmount += expense.getAmount();
                }
                
                // Expense total
                yPosition -= 10;
                contentStream.setFont(fontBold, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin + 350, yPosition);
                contentStream.showText("Total: " + CURRENCY_FORMAT.format(totalExpenseAmount));
                contentStream.endText();
                
                drawPageFooter(contentStream, margin, 3, document.getNumberOfPages());
            }
            
            // Page 4: Maintenance Details
            PDPage page4 = new PDPage(PDRectangle.A4);
            document.addPage(page4);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page4)) {
                yPosition = pageHeight - margin;
                
                // Page header
                drawPageHeader(contentStream, margin, yPosition, contentWidth, "Maintenance Expense Details");
                yPosition -= 60;
                
                // Maintenance section
                contentStream.setNonStrokingColor(255f/255f, 111f/255f, 0);
                contentStream.beginText();
                contentStream.setFont(fontBold, 16);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Maintenance Records");
                contentStream.endText();
                contentStream.setNonStrokingColor(0, 0, 0);
                yPosition -= 30;
                
                // Maintenance table headers
                String[] maintenanceHeaders = {"Date", "Vehicle", "Service", "Provider", "Cost"};
                float[] maintenanceColWidths = {70, 80, 120, 150, 80};
                
                drawTableHeader(contentStream, margin, yPosition, maintenanceHeaders, maintenanceColWidths);
                yPosition -= 25;
                
                // Maintenance rows
                boolean isEvenRow = false;
                double totalMaintenanceCost = 0;
                
                for (MaintenanceRecord record : maintenance) {
                    if (yPosition < margin + 50) {
                        drawPageFooter(contentStream, margin, 4, document.getNumberOfPages());
                        break;
                    }
                    
                    String provider = record.getServiceProvider() != null ? record.getServiceProvider() : record.getTechnician();
                    String[] values = {
                        record.getServiceDate().format(dateFormatter),
                        record.getVehicle(),
                        truncateString(record.getServiceType(), 25),
                        truncateString(provider, 30),
                        CURRENCY_FORMAT.format(record.getCost())
                    };
                    
                    drawTableRow(contentStream, margin, yPosition, values, maintenanceColWidths, isEvenRow);
                    yPosition -= 20;
                    isEvenRow = !isEvenRow;
                    totalMaintenanceCost += record.getCost();
                }
                
                // Maintenance total
                yPosition -= 10;
                contentStream.setFont(fontBold, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin + 350, yPosition);
                contentStream.showText("Total: " + CURRENCY_FORMAT.format(totalMaintenanceCost));
                contentStream.endText();
                
                // Final summary section
                yPosition -= 50;
                drawSummaryBox(contentStream, margin, yPosition, contentWidth, "REPORT SUMMARY", new Color(76, 175, 80));
                
                yPosition -= 40;
                contentStream.setFont(fontNormal, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin + 20, yPosition);
                contentStream.showText("This comprehensive financial report covers all revenue and expense activities");
                contentStream.endText();
                
                yPosition -= 20;
                contentStream.beginText();
                contentStream.newLineAtOffset(margin + 20, yPosition);
                contentStream.showText("for the period from " + startDate.format(dateFormatter) + " to " + endDate.format(dateFormatter) + ".");
                contentStream.endText();
                
                yPosition -= 30;
                contentStream.setFont(fontBold, 14);
                contentStream.setNonStrokingColor(27f/255f, 94f/255f, 32f/255f);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin + 20, yPosition);
                contentStream.showText("Final Net Profit: " + finalNet);
                contentStream.endText();
                
                drawPageFooter(contentStream, margin, 4, document.getNumberOfPages());
            }
            
            document.save(file);
        }
    }
    
    // Helper methods for PDF generation
    private void drawCompanyHeader(PDPageContentStream contentStream, float x, float y, float width) throws IOException {
        // Draw gradient background
        contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
        contentStream.addRect(x, y - 60, width, 60);
        contentStream.fill();
        
        // Company name
        contentStream.setNonStrokingColor(1, 1, 1);
        contentStream.beginText();
        contentStream.setFont(fontBold, 32);
        contentStream.newLineAtOffset(x + 20, y - 40);
        contentStream.showText(companyName);
        contentStream.endText();
    }
    
    private void drawPageHeader(PDPageContentStream contentStream, float x, float y, float width, String title) throws IOException {
        // Draw header line
        contentStream.setStrokingColor(25f/255f, 118f/255f, 210f/255f);
        contentStream.setLineWidth(2);
        contentStream.moveTo(x, y - 30);
        contentStream.lineTo(x + width, y - 30);
        contentStream.stroke();
        
        // Page title
        contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
        contentStream.beginText();
        contentStream.setFont(fontBold, 20);
        contentStream.newLineAtOffset(x, y - 20);
        contentStream.showText(title);
        contentStream.endText();
        contentStream.setNonStrokingColor(0, 0, 0);
    }
    
    private void drawPageFooter(PDPageContentStream contentStream, float x, int currentPage, int totalPages) throws IOException {
        float y = 40;
        
        // Draw footer line
        contentStream.setStrokingColor(0.8f, 0.8f, 0.8f);
        contentStream.setLineWidth(1);
        contentStream.moveTo(x, y + 10);
        contentStream.lineTo(PDRectangle.A4.getWidth() - x, y + 10);
        contentStream.stroke();
        
        // Footer text
        contentStream.setNonStrokingColor(0.5f, 0.5f, 0.5f);
        contentStream.beginText();
        contentStream.setFont(fontNormal, 10);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText("Generated on: " + LocalDateTime.now().format(dateTimeFormatter));
        contentStream.endText();
        
        // Page number
        contentStream.beginText();
        contentStream.newLineAtOffset(PDRectangle.A4.getWidth() - x - 100, y);
        contentStream.showText("Page " + currentPage + " of " + totalPages);
        contentStream.endText();
        
        contentStream.setNonStrokingColor(0, 0, 0);
    }
    
    private void drawSummaryBox(PDPageContentStream contentStream, float x, float y, float width, String title, Color color) throws IOException {
        // Draw box background
        contentStream.setNonStrokingColor(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f);
        contentStream.addRect(x, y - 30, width, 30);
        contentStream.fill();
        
        // Draw title
        contentStream.setNonStrokingColor(1, 1, 1);
        contentStream.beginText();
        contentStream.setFont(fontBold, 14);
        contentStream.newLineAtOffset(x + 10, y - 20);
        contentStream.showText(title);
        contentStream.endText();
        contentStream.setNonStrokingColor(0, 0, 0);
    }
    
    private void drawSummaryRow(PDPageContentStream contentStream, float x, float y, float width, String label, String value, Color valueColor) throws IOException {
        // Draw label
        contentStream.beginText();
        contentStream.setFont(fontNormal, 14);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(label);
        contentStream.endText();
        
        // Draw value
        contentStream.setNonStrokingColor(valueColor.getRed()/255f, valueColor.getGreen()/255f, valueColor.getBlue()/255f);
        contentStream.beginText();
        contentStream.setFont(fontBold, 14);
        contentStream.newLineAtOffset(x + width - 150, y);
        contentStream.showText(value);
        contentStream.endText();
        contentStream.setNonStrokingColor(0, 0, 0);
    }
    
    private void drawMetricRow(PDPageContentStream contentStream, float x, float y, float width, String label, String value) throws IOException {
        // Draw background
        contentStream.setNonStrokingColor(0.97f, 0.97f, 0.97f);
        contentStream.addRect(x - 10, y - 5, width + 20, 25);
        contentStream.fill();
        contentStream.setNonStrokingColor(0, 0, 0);
        
        // Draw label
        contentStream.beginText();
        contentStream.setFont(fontNormal, 12);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(label);
        contentStream.endText();
        
        // Draw value
        contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
        contentStream.beginText();
        contentStream.setFont(fontBold, 12);
        contentStream.newLineAtOffset(x + width - 100, y);
        contentStream.showText(value);
        contentStream.endText();
        contentStream.setNonStrokingColor(0, 0, 0);
    }
    
    private void drawTableHeader(PDPageContentStream contentStream, float x, float y, String[] headers, float[] colWidths) throws IOException {
        // Draw header background
        contentStream.setNonStrokingColor(0.9f, 0.9f, 0.9f);
        float totalWidth = 0;
        for (float w : colWidths) totalWidth += w;
        contentStream.addRect(x, y - 20, totalWidth, 20);
        contentStream.fill();
        
        // Draw header text
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(fontBold, 11);
        float currentX = x + 5;
        
        for (int i = 0; i < headers.length; i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(currentX, y - 15);
            contentStream.showText(headers[i]);
            contentStream.endText();
            currentX += colWidths[i];
        }
    }
    
    private void drawTableRow(PDPageContentStream contentStream, float x, float y, String[] values, float[] colWidths, boolean isEven) throws IOException {
        // Draw row background
        if (isEven) {
            contentStream.setNonStrokingColor(0.97f, 0.97f, 0.97f);
            float totalWidth = 0;
            for (float w : colWidths) totalWidth += w;
            contentStream.addRect(x, y - 15, totalWidth, 20);
            contentStream.fill();
        }
        
        // Draw row text
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(fontNormal, 10);
        float currentX = x + 5;
        
        for (int i = 0; i < values.length; i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(currentX, y - 10);
            contentStream.showText(values[i]);
            contentStream.endText();
            currentX += colWidths[i];
        }
    }
    
    private double parseMoneyValue(String moneyString) {
        try {
            return Double.parseDouble(moneyString.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Generate comprehensive revenue report with multiple pages
     */
    public void generateComprehensiveRevenueReport(
            File file,
            LocalDate startDate,
            LocalDate endDate,
            List<com.company.payroll.revenue.RevenueTab.RevenueEntry> revenueData,
            String totalRevenue,
            String totalLoads,
            String avgPerLoad,
            String collected,
            String pending,
            String outstanding) throws IOException {
        
        try (PDDocument document = new PDDocument()) {
            // Page 1: Executive Summary
            PDPage page1 = new PDPage(PDRectangle.A4);
            document.addPage(page1);
            PDPageContentStream contentStream = new PDPageContentStream(document, page1);
            
            float y = page1.getMediaBox().getHeight() - MARGIN_TOP;
            
            // Header
            contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
            contentStream.addRect(0, y - 40, page1.getMediaBox().getWidth(), 60);
            contentStream.fill();
            
            contentStream.setNonStrokingColor(1, 1, 1);
            contentStream.beginText();
            contentStream.setFont(fontBold, 24);
            contentStream.newLineAtOffset(MARGIN_LEFT, y - 25);
            contentStream.showText(companyName + " - Revenue Report");
            contentStream.endText();
            
            contentStream.beginText();
            contentStream.setFont(fontNormal, 12);
            contentStream.newLineAtOffset(MARGIN_LEFT, y - 45);
            contentStream.showText("Period: " + startDate.format(dateFormatter) + " to " + endDate.format(dateFormatter));
            contentStream.endText();
            
            y -= 80;
            contentStream.setNonStrokingColor(0, 0, 0);
            
            // Executive Summary Title
            contentStream.beginText();
            contentStream.setFont(fontBold, 18);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Executive Summary");
            contentStream.endText();
            y -= 40;
            
            // Summary Cards
            float cardWidth = 160;
            float cardHeight = 80;
            float cardSpacing = 20;
            float startX = MARGIN_LEFT;
            
            // Total Revenue Card
            drawRevenueCard(contentStream, startX, y, cardWidth, cardHeight, "Total Revenue", totalRevenue, new Color(70, 130, 180));
            startX += cardWidth + cardSpacing;
            
            // Total Loads Card
            drawRevenueCard(contentStream, startX, y, cardWidth, cardHeight, "Total Loads", totalLoads, new Color(60, 179, 113));
            startX += cardWidth + cardSpacing;
            
            // Avg Per Load Card
            drawRevenueCard(contentStream, startX, y, cardWidth, cardHeight, "Avg per Load", avgPerLoad, new Color(255, 140, 0));
            
            y -= cardHeight + 40;
            startX = MARGIN_LEFT;
            
            // Collected Card
            drawRevenueCard(contentStream, startX, y, cardWidth, cardHeight, "Collected", collected, new Color(46, 125, 50));
            startX += cardWidth + cardSpacing;
            
            // Pending Card
            drawRevenueCard(contentStream, startX, y, cardWidth, cardHeight, "Pending", pending, new Color(255, 152, 0));
            startX += cardWidth + cardSpacing;
            
            // Outstanding Card
            drawRevenueCard(contentStream, startX, y, cardWidth, cardHeight, "Outstanding", outstanding, new Color(211, 47, 47));
            
            y -= cardHeight + 40;
            
            // Revenue by Status
            contentStream.beginText();
            contentStream.setFont(fontBold, 16);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Revenue Status Breakdown");
            contentStream.endText();
            y -= 30;
            
            Map<String, Double> statusBreakdown = new HashMap<>();
            Map<String, Integer> statusCount = new HashMap<>();
            for (com.company.payroll.revenue.RevenueTab.RevenueEntry entry : revenueData) {
                String status = entry.getStatus() != null ? entry.getStatus() : "Unknown";
                statusBreakdown.put(status, statusBreakdown.getOrDefault(status, 0.0) + entry.getAmount());
                statusCount.put(status, statusCount.getOrDefault(status, 0) + 1);
            }
            
            for (Map.Entry<String, Double> entry : statusBreakdown.entrySet()) {
                contentStream.beginText();
                contentStream.setFont(fontNormal, 12);
                contentStream.newLineAtOffset(MARGIN_LEFT + 20, y);
                contentStream.showText(entry.getKey() + ": " + CURRENCY_FORMAT.format(entry.getValue()) + 
                                   " (" + statusCount.get(entry.getKey()) + " loads)");
                contentStream.endText();
                y -= 20;
            }
            
            // Footer
            drawFooter(contentStream, page1, 1);
            contentStream.close();
            
            // Page 2: Revenue Details Table
            PDPage page2 = new PDPage(PDRectangle.A4);
            document.addPage(page2);
            contentStream = new PDPageContentStream(document, page2);
            
            y = page2.getMediaBox().getHeight() - MARGIN_TOP;
            
            // Header
            contentStream.beginText();
            contentStream.setFont(fontBold, 18);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Revenue Details");
            contentStream.endText();
            y -= 40;
            
            // Table
            String[] headers = {"Invoice #", "Date", "Customer", "Load ID", "Amount", "Status"};
            float[] colWidths = {70, 70, 140, 60, 80, 80};
            
            drawTableHeader(contentStream, MARGIN_LEFT, y, headers, colWidths);
            y -= 25;
            
            boolean isEven = false;
            int pageNum = 2;
            
            for (com.company.payroll.revenue.RevenueTab.RevenueEntry entry : revenueData) {
                if (y < 100) {
                    drawFooter(contentStream, page2, pageNum);
                    contentStream.close();
                    
                    PDPage newPage = new PDPage(PDRectangle.A4);
                    document.addPage(newPage);
                    contentStream = new PDPageContentStream(document, newPage);
                    y = newPage.getMediaBox().getHeight() - MARGIN_TOP;
                    pageNum++;
                    
                    drawTableHeader(contentStream, MARGIN_LEFT, y, headers, colWidths);
                    y -= 25;
                }
                
                String[] values = {
                    entry.getInvoiceNumber() != null ? entry.getInvoiceNumber() : "",
                    entry.getDate() != null ? entry.getDate().format(dateFormatter) : "",
                    entry.getCustomer() != null ? entry.getCustomer() : "",
                    entry.getLoadId() != null ? entry.getLoadId() : "",
                    CURRENCY_FORMAT.format(entry.getAmount()),
                    entry.getStatus() != null ? entry.getStatus() : ""
                };
                
                drawTableRow(contentStream, MARGIN_LEFT, y, values, colWidths, isEven);
                y -= 20;
                isEven = !isEven;
            }
            
            drawFooter(contentStream, document.getPage(document.getNumberOfPages() - 1), pageNum);
            contentStream.close();
            
            // Page 3: Customer Analysis
            PDPage page3 = new PDPage(PDRectangle.A4);
            document.addPage(page3);
            contentStream = new PDPageContentStream(document, page3);
            pageNum++;
            
            y = page3.getMediaBox().getHeight() - MARGIN_TOP;
            
            // Header
            contentStream.beginText();
            contentStream.setFont(fontBold, 18);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Customer Analysis");
            contentStream.endText();
            y -= 40;
            
            // Calculate customer metrics
            Map<String, Double> customerRevenue = new HashMap<>();
            Map<String, Integer> customerLoads = new HashMap<>();
            
            for (com.company.payroll.revenue.RevenueTab.RevenueEntry entry : revenueData) {
                String customer = entry.getCustomer() != null ? entry.getCustomer() : "Unknown";
                customerRevenue.put(customer, customerRevenue.getOrDefault(customer, 0.0) + entry.getAmount());
                customerLoads.put(customer, customerLoads.getOrDefault(customer, 0) + 1);
            }
            
            // Sort by revenue
            List<Map.Entry<String, Double>> sortedCustomers = new ArrayList<>(customerRevenue.entrySet());
            sortedCustomers.sort(Map.Entry.<String, Double>comparingByValue().reversed());
            
            // Top Customers Table
            contentStream.beginText();
            contentStream.setFont(fontBold, 14);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Top Customers by Revenue");
            contentStream.endText();
            y -= 30;
            
            String[] custHeaders = {"Customer", "Total Revenue", "Total Loads", "Avg per Load"};
            float[] custColWidths = {200, 100, 80, 100};
            
            drawTableHeader(contentStream, MARGIN_LEFT, y, custHeaders, custColWidths);
            y -= 25;
            
            isEven = false;
            int count = 0;
            
            for (Map.Entry<String, Double> entry : sortedCustomers) {
                if (count >= 15 || y < 100) break;
                
                String customer = entry.getKey();
                double revenue = entry.getValue();
                int loads = customerLoads.get(customer);
                double avgPerLoadValue = loads > 0 ? revenue / loads : 0;
                
                String[] values = {
                    customer,
                    CURRENCY_FORMAT.format(revenue),
                    String.valueOf(loads),
                    CURRENCY_FORMAT.format(avgPerLoadValue)
                };
                
                drawTableRow(contentStream, MARGIN_LEFT, y, values, custColWidths, isEven);
                y -= 20;
                isEven = !isEven;
                count++;
            }
            
            drawFooter(contentStream, page3, pageNum);
            contentStream.close();
            
            // Page 4: Period Analysis
            PDPage page4 = new PDPage(PDRectangle.A4);
            document.addPage(page4);
            contentStream = new PDPageContentStream(document, page4);
            pageNum++;
            
            y = page4.getMediaBox().getHeight() - MARGIN_TOP;
            
            // Header
            contentStream.beginText();
            contentStream.setFont(fontBold, 18);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Period Analysis");
            contentStream.endText();
            y -= 40;
            
            // Weekly breakdown
            Map<LocalDate, Double> weeklyRevenue = new TreeMap<>();
            Map<LocalDate, Integer> weeklyLoads = new TreeMap<>();
            
            for (com.company.payroll.revenue.RevenueTab.RevenueEntry entry : revenueData) {
                if (entry.getDate() != null) {
                    LocalDate weekStart = entry.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    weeklyRevenue.put(weekStart, weeklyRevenue.getOrDefault(weekStart, 0.0) + entry.getAmount());
                    weeklyLoads.put(weekStart, weeklyLoads.getOrDefault(weekStart, 0) + 1);
                }
            }
            
            contentStream.beginText();
            contentStream.setFont(fontBold, 14);
            contentStream.newLineAtOffset(MARGIN_LEFT, y);
            contentStream.showText("Weekly Performance");
            contentStream.endText();
            y -= 30;
            
            String[] weekHeaders = {"Week Starting", "Revenue", "Loads", "Avg per Load"};
            float[] weekColWidths = {120, 100, 80, 100};
            
            drawTableHeader(contentStream, MARGIN_LEFT, y, weekHeaders, weekColWidths);
            y -= 25;
            
            isEven = false;
            for (Map.Entry<LocalDate, Double> entry : weeklyRevenue.entrySet()) {
                if (y < 100) break;
                
                LocalDate weekStart = entry.getKey();
                double revenue = entry.getValue();
                int loads = weeklyLoads.getOrDefault(weekStart, 0);
                double avgPerLoadWeekly = loads > 0 ? revenue / loads : 0;
                
                String[] values = {
                    weekStart.format(dateFormatter),
                    CURRENCY_FORMAT.format(revenue),
                    String.valueOf(loads),
                    CURRENCY_FORMAT.format(avgPerLoadWeekly)
                };
                
                drawTableRow(contentStream, MARGIN_LEFT, y, values, weekColWidths, isEven);
                y -= 20;
                isEven = !isEven;
            }
            
            // Summary Statistics
            y -= 40;
            if (y > 200) {
                contentStream.beginText();
                contentStream.setFont(fontBold, 14);
                contentStream.newLineAtOffset(MARGIN_LEFT, y);
                contentStream.showText("Summary Statistics");
                contentStream.endText();
                y -= 30;
                
                double totalRev = parseMoneyValue(totalRevenue);
                int totalLoadCount = Integer.parseInt(totalLoads.replaceAll("[^0-9]", ""));
                
                contentStream.beginText();
                contentStream.setFont(fontNormal, 12);
                contentStream.newLineAtOffset(MARGIN_LEFT + 20, y);
                contentStream.showText("Average Weekly Revenue: " + 
                    CURRENCY_FORMAT.format(weeklyRevenue.isEmpty() ? 0 : totalRev / weeklyRevenue.size()));
                contentStream.endText();
                y -= 20;
                
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN_LEFT + 20, y);
                contentStream.showText("Average Weekly Loads: " + 
                    (weeklyRevenue.isEmpty() ? "0" : String.format("%.1f", totalLoadCount / (double)weeklyRevenue.size())));
                contentStream.endText();
                y -= 20;
                
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN_LEFT + 20, y);
                contentStream.showText("Total Customers: " + customerRevenue.size());
                contentStream.endText();
            }
            
            drawFooter(contentStream, page4, pageNum);
            contentStream.close();
            
            document.save(file);
            logger.info("Generated comprehensive revenue report: {}", file.getAbsolutePath());
        }
    }
    
    private void drawRevenueCard(PDPageContentStream contentStream, float x, float y, float width, float height, 
                                String title, String value, Color color) throws IOException {
        // Draw card background
        contentStream.setNonStrokingColor(0.95f, 0.95f, 0.95f);
        contentStream.addRect(x, y - height, width, height);
        contentStream.fill();
        
        // Draw colored accent bar
        contentStream.setNonStrokingColor(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f);
        contentStream.addRect(x, y - 5, width, 5);
        contentStream.fill();
        
        // Draw title
        contentStream.setNonStrokingColor(0.4f, 0.4f, 0.4f);
        contentStream.beginText();
        contentStream.setFont(fontNormal, 11);
        contentStream.newLineAtOffset(x + 10, y - 25);
        contentStream.showText(title);
        contentStream.endText();
        
        // Draw value
        contentStream.setNonStrokingColor(0.2f, 0.2f, 0.2f);
        contentStream.beginText();
        contentStream.setFont(fontBold, 18);
        contentStream.newLineAtOffset(x + 10, y - 50);
        contentStream.showText(value);
        contentStream.endText();
        
        contentStream.setNonStrokingColor(0, 0, 0);
    }
    
    private void drawFooter(PDPageContentStream contentStream, PDPage page, int pageNumber) throws IOException {
        float y = MARGIN_BOTTOM - 20;
        
        contentStream.setNonStrokingColor(0.6f, 0.6f, 0.6f);
        contentStream.beginText();
        contentStream.setFont(fontNormal, 9);
        contentStream.newLineAtOffset(MARGIN_LEFT, y);
        contentStream.showText("Generated on " + LocalDate.now().format(dateFormatter) + " at " + 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(page.getMediaBox().getWidth() - MARGIN_RIGHT - 50, y);
        contentStream.showText("Page " + pageNumber);
        contentStream.endText();
        
        contentStream.setNonStrokingColor(0, 0, 0);
    }
}