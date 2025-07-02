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
import java.util.Map;

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
}