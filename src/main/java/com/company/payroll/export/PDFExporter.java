package com.company.payroll.export;

import com.company.payroll.employees.Employee;
import com.company.payroll.payroll.PayrollCalculator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class for exporting payroll data to PDF format
 */
public class PDFExporter {
    
    private final String companyName;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
    
    public PDFExporter(String companyName) {
        this.companyName = companyName;
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
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText(companyName);
            contentStream.endText();
            
            // Rest of the pay stub content...
            // (Simplified for brevity - would include all pay info sections)
            yPosition -= 30;
            contentStream.setNonStrokingColor(66f/255f, 66f/255f, 66f/255f);
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("EARNINGS STATEMENT");
            contentStream.endText();
            
            // Employee info
            yPosition -= 30;
            contentStream.setNonStrokingColor(0, 0, 0);
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("Employee: " + driver.getName());
            contentStream.endText();
            
            // Pay period
            yPosition -= 20;
            LocalDate weekEnd = weekStart.plusDays(6);
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText("Pay Period: " + weekStart.format(dateFormatter) + 
                                  " - " + weekEnd.format(dateFormatter));
            contentStream.endText();
            
            // Net pay
            yPosition -= 40;
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
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
                // Implementation similar to pay stub above but formatted as an income report
                // Omitted for brevity
            }
            
            document.save(file);
        }
    }
}