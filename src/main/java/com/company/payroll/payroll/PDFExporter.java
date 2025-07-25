package com.company.payroll.payroll;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Professional PDF exporter for payroll data with enhanced formatting and error handling.
 * Supports single and batch pay stub generation with consistent styling.
 */
public class PDFExporter {
    private static final Logger logger = LoggerFactory.getLogger(PDFExporter.class);
    
    // Page layout constants
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 50f;
    private static final float MARGIN_LEFT = 50f;
    private static final float MARGIN_RIGHT = 50f;
    private static final float LINE_HEIGHT = 18f;
    private static final float SECTION_SPACING = 25f;
    
    // Font sizes
    private static final float FONT_SIZE_TITLE = 24f;
    private static final float FONT_SIZE_SUBTITLE = 18f;
    private static final float FONT_SIZE_HEADER = 14f;
    private static final float FONT_SIZE_NORMAL = 11f;
    private static final float FONT_SIZE_SMALL = 9f;
    
    // Colors (RGB values 0-1)
    private static final float[] COLOR_PRIMARY = {25f/255f, 118f/255f, 210f/255f}; // Light blue
    private static final float[] COLOR_SECONDARY = {66f/255f, 66f/255f, 66f/255f}; // Dark gray
    private static final float[] COLOR_SUCCESS = {46f/255f, 125f/255f, 50f/255f}; // Green
    private static final float[] COLOR_DANGER = {211f/255f, 47f/255f, 47f/255f}; // Red
    private static final float[] COLOR_WARNING = {255f/255f, 152f/255f, 0f/255f}; // Orange
    private static final float[] COLOR_LIGHT_GRAY = {245f/255f, 245f/255f, 245f/255f};
    
    private final String companyName;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
    
    // Fonts
    private PDType1Font fontBold;
    private PDType1Font fontNormal;
    private PDType1Font fontItalic;
    
    public PDFExporter(String companyName) {
        this.companyName = companyName;
        logger.info("PDFExporter initialized for company: {}", companyName);
    }
    
    /**
     * Generate a pay stub PDF for a single employee
     */
    public void generatePayStub(File outputFile, PayrollCalculator.PayrollRow payrollData, 
                               com.company.payroll.employees.Employee employee, 
                               LocalDate weekStart) throws IOException {
        logger.info("Generating pay stub for employee: {} week: {}", employee.getName(), weekStart);
        
        try (PDDocument document = new PDDocument()) {
            initializeFonts();
            
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                float pageWidth = page.getMediaBox().getWidth();
                float contentWidth = pageWidth - MARGIN_LEFT - MARGIN_RIGHT;
                
                // Draw header
                yPosition = drawHeader(contentStream, yPosition, contentWidth);
                
                // Draw employee info section
                yPosition = drawEmployeeInfo(contentStream, yPosition, contentWidth, employee, weekStart);
                
                // Draw earnings section
                yPosition = drawEarningsSection(contentStream, yPosition, contentWidth, payrollData);
                
                // Draw deductions section
                yPosition = drawDeductionsSection(contentStream, yPosition, contentWidth, payrollData);
                
                // Draw reimbursements if any
                if (payrollData.reimbursements > 0) {
                    yPosition = drawReimbursementsSection(contentStream, yPosition, contentWidth, payrollData);
                }
                
                // Draw advances given if any
                if (payrollData.advancesGiven > 0) {
                    yPosition = drawAdvancesSection(contentStream, yPosition, contentWidth, payrollData);
                }
                
                // Draw net pay
                yPosition = drawNetPaySection(contentStream, yPosition, contentWidth, payrollData);
                
                // Draw footer
                drawFooter(contentStream, contentWidth);
            }
            
            document.save(outputFile);
            logger.info("Pay stub saved successfully to: {}", outputFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Failed to generate pay stub for employee: {}", employee.getName(), e);
            throw new IOException("Failed to generate pay stub: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate pay stubs for multiple employees in a single PDF
     */
    public void generateBatchPayStubs(File outputFile, List<PayrollCalculator.PayrollRow> payrollRows,
                                    Map<String, com.company.payroll.employees.Employee> employeeMap,
                                    LocalDate weekStart) throws IOException {
        logger.info("Generating batch pay stubs for {} employees", payrollRows.size());
        
        try (PDDocument document = new PDDocument()) {
            initializeFonts();
            
            int processed = 0;
            for (PayrollCalculator.PayrollRow row : payrollRows) {
                com.company.payroll.employees.Employee employee = employeeMap.get(row.driverName);
                if (employee == null) {
                    logger.warn("Employee not found for driver: {}", row.driverName);
                    continue;
                }
                
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    float yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                    float pageWidth = page.getMediaBox().getWidth();
                    float contentWidth = pageWidth - MARGIN_LEFT - MARGIN_RIGHT;
                    
                    // Draw complete pay stub
                    yPosition = drawHeader(contentStream, yPosition, contentWidth);
                    yPosition = drawEmployeeInfo(contentStream, yPosition, contentWidth, employee, weekStart);
                    yPosition = drawEarningsSection(contentStream, yPosition, contentWidth, row);
                    yPosition = drawDeductionsSection(contentStream, yPosition, contentWidth, row);
                    
                    if (row.reimbursements > 0) {
                        yPosition = drawReimbursementsSection(contentStream, yPosition, contentWidth, row);
                    }
                    
                    if (row.advancesGiven > 0) {
                        yPosition = drawAdvancesSection(contentStream, yPosition, contentWidth, row);
                    }
                    
                    yPosition = drawNetPaySection(contentStream, yPosition, contentWidth, row);
                    drawFooter(contentStream, contentWidth);
                }
                
                processed++;
            }
            
            document.save(outputFile);
            logger.info("Batch pay stubs saved successfully. Processed {} of {} employees", 
                       processed, payrollRows.size());
            
        } catch (IOException e) {
            logger.error("Failed to generate batch pay stubs", e);
            throw new IOException("Failed to generate batch pay stubs: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate a summary report PDF
     */
    public void generateSummaryReport(File outputFile, List<PayrollCalculator.PayrollRow> payrollRows,
                                    Map<String, Double> totals, LocalDate weekStart) throws IOException {
        logger.info("Generating payroll summary report for week: {}", weekStart);
        
        try (PDDocument document = new PDDocument()) {
            initializeFonts();
            
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                float pageWidth = page.getMediaBox().getWidth();
                float contentWidth = pageWidth - MARGIN_LEFT - MARGIN_RIGHT;
                
                // Draw report header
                yPosition = drawReportHeader(contentStream, yPosition, contentWidth, weekStart);
                
                // Draw summary statistics
                yPosition = drawSummaryStatistics(contentStream, yPosition, contentWidth, totals, payrollRows.size());
                
                // Draw employee details table header
                yPosition -= SECTION_SPACING;
                contentStream.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
                contentStream.beginText();
                contentStream.setFont(fontBold, FONT_SIZE_HEADER);
                contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
                contentStream.showText("EMPLOYEE DETAILS");
                contentStream.endText();
                
                yPosition -= LINE_HEIGHT;
                
                // Draw table headers
                yPosition = drawTableHeaders(contentStream, yPosition, contentWidth);
                
                // Draw employee rows
                contentStream.setNonStrokingColor(0, 0, 0);
                contentStream.setFont(fontNormal, FONT_SIZE_SMALL);
                
                for (PayrollCalculator.PayrollRow row : payrollRows) {
                    if (yPosition < MARGIN_BOTTOM + 50) {
                        // Start new page
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream.close();
                        PDPageContentStream newStream = new PDPageContentStream(document, page);
                        yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;
                        yPosition = drawTableHeaders(newStream, yPosition, contentWidth);
                        newStream.setFont(fontNormal, FONT_SIZE_SMALL);
                    }
                    
                    yPosition = drawEmployeeRow(contentStream, yPosition, contentWidth, row);
                }
                
                // Draw footer
                drawReportFooter(contentStream, contentWidth);
            }
            
            document.save(outputFile);
            logger.info("Summary report saved successfully to: {}", outputFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Failed to generate summary report", e);
            throw new IOException("Failed to generate summary report: " + e.getMessage(), e);
        }
    }
    
    private void initializeFonts() {
        fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    }
    
    private float drawHeader(PDPageContentStream stream, float yPosition, float contentWidth) throws IOException {
        // Company name
        stream.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_TITLE);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText(companyName);
        stream.endText();
        
        yPosition -= 30;
        
        // Subtitle
        stream.setNonStrokingColor(COLOR_SECONDARY[0], COLOR_SECONDARY[1], COLOR_SECONDARY[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_SUBTITLE);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("EARNINGS STATEMENT");
        stream.endText();
        
        yPosition -= 25;
        
        // Draw separator line
        stream.setStrokingColor(0.8f, 0.8f, 0.8f);
        stream.setLineWidth(1f);
        stream.moveTo(MARGIN_LEFT, yPosition);
        stream.lineTo(MARGIN_LEFT + contentWidth, yPosition);
        stream.stroke();
        
        return yPosition - SECTION_SPACING;
    }
    
    private float drawEmployeeInfo(PDPageContentStream stream, float yPosition, float contentWidth,
                                 com.company.payroll.employees.Employee employee, LocalDate weekStart) throws IOException {
        // Section background
        stream.setNonStrokingColor(COLOR_LIGHT_GRAY[0], COLOR_LIGHT_GRAY[1], COLOR_LIGHT_GRAY[2]);
        stream.addRect(MARGIN_LEFT, yPosition - 80, contentWidth, 80);
        stream.fill();
        
        // Section title
        stream.setNonStrokingColor(0, 0, 0);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT + 10, yPosition - 20);
        stream.showText("Employee Information");
        stream.endText();
        
        yPosition -= 35;
        
        // Employee details in two columns
        LocalDate weekEnd = weekStart.plusDays(6);
        String[][] info = {
            {"Employee:", employee.getName(), "Pay Period:", weekStart.format(dateFormatter) + " - " + weekEnd.format(dateFormatter)},
            {"Employee ID:", String.valueOf(employee.getId()), "Pay Date:", LocalDate.now().format(dateFormatter)},
            {"Truck/Unit:", employee.getTruckUnit() != null ? employee.getTruckUnit() : "N/A", "", ""}
        };
        
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
        for (String[] row : info) {
            drawInfoRow(stream, yPosition, row);
            yPosition -= LINE_HEIGHT;
        }
        
        return yPosition - SECTION_SPACING;
    }
    
    private float drawEarningsSection(PDPageContentStream stream, float yPosition, float contentWidth,
                                    PayrollCalculator.PayrollRow data) throws IOException {
        // Section title
        stream.setNonStrokingColor(COLOR_SUCCESS[0], COLOR_SUCCESS[1], COLOR_SUCCESS[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("EARNINGS");
        stream.endText();
        
        yPosition -= 20;
        
        // Earnings details
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
        
        String[][] earnings = {
            {"Gross Pay:", String.format("$%,.2f", data.gross)},
            {"Load Count:", String.valueOf(data.loadCount)},
            {"Service Fee:", String.format("($%,.2f)", Math.abs(data.serviceFee))},
            {"Company Pay:", String.format("$%,.2f", data.companyPay)},
            {"Driver Pay:", String.format("$%,.2f", data.driverPay)}
        };
        
        for (String[] row : earnings) {
            drawAmountRow(stream, yPosition, row[0], row[1]);
            yPosition -= LINE_HEIGHT;
        }
        
        return yPosition - SECTION_SPACING;
    }
    
    private float drawDeductionsSection(PDPageContentStream stream, float yPosition, float contentWidth,
                                      PayrollCalculator.PayrollRow data) throws IOException {
        // Section title
        stream.setNonStrokingColor(COLOR_DANGER[0], COLOR_DANGER[1], COLOR_DANGER[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("DEDUCTIONS");
        stream.endText();
        
        yPosition -= 20;
        
        // Deduction details
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
        
        String[][] deductions = {
            {"Fuel:", String.format("$%,.2f", Math.abs(data.fuel))},
            {"Recurring Fees:", String.format("$%,.2f", Math.abs(data.recurringFees))},
            {"Advance Repayments:", String.format("$%,.2f", Math.abs(data.advanceRepayments))},
            {"Escrow Deposits:", String.format("$%,.2f", Math.abs(data.escrowDeposits))},
            {"Other Deductions:", String.format("$%,.2f", Math.abs(data.otherDeductions))}
        };
        
        for (String[] row : deductions) {
            drawAmountRow(stream, yPosition, row[0], row[1]);
            yPosition -= LINE_HEIGHT;
        }
        
        // Total deductions
        double totalDeductions = Math.abs(data.fuel) + Math.abs(data.recurringFees) +
                               Math.abs(data.advanceRepayments) +
                               Math.abs(data.escrowDeposits) + Math.abs(data.otherDeductions);
        
        yPosition -= 5;
        stream.setStrokingColor(0.8f, 0.8f, 0.8f);
        stream.moveTo(MARGIN_LEFT + 250, yPosition);
        stream.lineTo(MARGIN_LEFT + contentWidth, yPosition);
        stream.stroke();
        
        yPosition -= LINE_HEIGHT;
        stream.setFont(fontBold, FONT_SIZE_NORMAL);
        drawAmountRow(stream, yPosition, "Total Deductions:", String.format("$%,.2f", totalDeductions));
        
        return yPosition - SECTION_SPACING;
    }
    
    private float drawReimbursementsSection(PDPageContentStream stream, float yPosition, float contentWidth,
                                          PayrollCalculator.PayrollRow data) throws IOException {
        stream.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("REIMBURSEMENTS");
        stream.endText();
        
        yPosition -= 20;
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
        drawAmountRow(stream, yPosition, "Total Reimbursements:", String.format("$%,.2f", data.reimbursements));
        
        return yPosition - SECTION_SPACING;
    }
    
    private float drawAdvancesSection(PDPageContentStream stream, float yPosition, float contentWidth,
                                    PayrollCalculator.PayrollRow data) throws IOException {
        stream.setNonStrokingColor(COLOR_WARNING[0], COLOR_WARNING[1], COLOR_WARNING[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("ADVANCES");
        stream.endText();
        
        yPosition -= 20;
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
        drawAmountRow(stream, yPosition, "Advances Given This Week:", String.format("$%,.2f", data.advancesGiven));

        yPosition -= LINE_HEIGHT;
        if (data.escrowWithdrawals > 0) {
            drawAmountRow(stream, yPosition, "Escrow Withdrawals:", String.format("$%,.2f", data.escrowWithdrawals));
            yPosition -= LINE_HEIGHT;
        }

        return yPosition - SECTION_SPACING;
    }
    
    private float drawNetPaySection(PDPageContentStream stream, float yPosition, float contentWidth,
                                  PayrollCalculator.PayrollRow data) throws IOException {
        // Draw highlight box
        float boxHeight = 35;
        if (data.netPay >= 0) {
            stream.setNonStrokingColor(0.9f, 0.97f, 0.9f); // Light green
        } else {
            stream.setNonStrokingColor(1f, 0.9f, 0.9f); // Light red
        }
        stream.addRect(MARGIN_LEFT, yPosition - boxHeight, contentWidth, boxHeight);
        stream.fill();
        
        // Draw net pay
        yPosition -= 25;
        if (data.netPay >= 0) {
            stream.setNonStrokingColor(COLOR_SUCCESS[0], COLOR_SUCCESS[1], COLOR_SUCCESS[2]);
        } else {
            stream.setNonStrokingColor(COLOR_DANGER[0], COLOR_DANGER[1], COLOR_DANGER[2]);
        }
        
        stream.beginText();
        stream.setFont(fontBold, 20);
        stream.newLineAtOffset(MARGIN_LEFT + 10, yPosition);
        stream.showText("NET PAY:");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(MARGIN_LEFT + contentWidth - 150, yPosition);
        stream.showText(String.format("$%,.2f", data.netPay));
        stream.endText();
        
        return yPosition - SECTION_SPACING;
    }
    
    private void drawFooter(PDPageContentStream stream, float contentWidth) throws IOException {
        float yPosition = MARGIN_BOTTOM + 30;
        
        stream.setNonStrokingColor(0.5f, 0.5f, 0.5f);
        stream.beginText();
        stream.setFont(fontItalic, FONT_SIZE_SMALL);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("This is an electronic pay stub. Please retain for your records.");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(MARGIN_LEFT, yPosition - 15);
        stream.showText("Generated on: " + LocalDateTime.now().format(dateTimeFormatter));
        stream.endText();
    }
    
    private float drawReportHeader(PDPageContentStream stream, float yPosition, float contentWidth,
                                 LocalDate weekStart) throws IOException {
        // Company name
        stream.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_TITLE);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText(companyName);
        stream.endText();
        
        yPosition -= 30;
        
        // Report title
        stream.setNonStrokingColor(COLOR_SECONDARY[0], COLOR_SECONDARY[1], COLOR_SECONDARY[2]);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_SUBTITLE);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("PAYROLL SUMMARY REPORT");
        stream.endText();
        
        // Week info
        LocalDate weekEnd = weekStart.plusDays(6);
        stream.beginText();
        stream.setFont(fontNormal, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition - 25);
        stream.showText("Week: " + weekStart.format(dateFormatter) + " - " + weekEnd.format(dateFormatter));
        stream.endText();
        
        yPosition -= 50;
        
        // Draw separator
        stream.setStrokingColor(0.8f, 0.8f, 0.8f);
        stream.setLineWidth(1f);
        stream.moveTo(MARGIN_LEFT, yPosition);
        stream.lineTo(MARGIN_LEFT + contentWidth, yPosition);
        stream.stroke();
        
        return yPosition - SECTION_SPACING;
    }
    
    private float drawSummaryStatistics(PDPageContentStream stream, float yPosition, float contentWidth,
                                      Map<String, Double> totals, int driverCount) throws IOException {
        // Summary box
        float boxHeight = 100;
        stream.setNonStrokingColor(COLOR_LIGHT_GRAY[0], COLOR_LIGHT_GRAY[1], COLOR_LIGHT_GRAY[2]);
        stream.addRect(MARGIN_LEFT, yPosition - boxHeight, contentWidth, boxHeight);
        stream.fill();
        
        // Title
        stream.setNonStrokingColor(0, 0, 0);
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_HEADER);
        stream.newLineAtOffset(MARGIN_LEFT + 10, yPosition - 20);
        stream.showText("SUMMARY STATISTICS");
        stream.endText();
        
        yPosition -= 40;
        
        // Statistics in columns
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
        float col1X = MARGIN_LEFT + 10;
        float col2X = MARGIN_LEFT + contentWidth/2;
        
        // Column 1
        drawStatistic(stream, col1X, yPosition, "Total Drivers:", String.valueOf(driverCount));
        yPosition -= LINE_HEIGHT;
        drawStatistic(stream, col1X, yPosition, "Total Gross Pay:", 
                     String.format("$%,.2f", totals.getOrDefault("gross", 0.0)));
        yPosition -= LINE_HEIGHT;
        drawStatistic(stream, col1X, yPosition, "Total Deductions:", 
                     String.format("$%,.2f", calculateTotalDeductions(totals)));
        
        // Column 2
        yPosition += 2 * LINE_HEIGHT;
        drawStatistic(stream, col2X, yPosition, "Total Reimbursements:", 
                     String.format("$%,.2f", totals.getOrDefault("reimbursements", 0.0)));
        yPosition -= LINE_HEIGHT;
        drawStatistic(stream, col2X, yPosition, "Total Net Pay:", 
                     String.format("$%,.2f", totals.getOrDefault("netPay", 0.0)));
        yPosition -= LINE_HEIGHT;
        drawStatistic(stream, col2X, yPosition, "Average Net Pay:", 
                     String.format("$%,.2f", driverCount > 0 ? totals.getOrDefault("netPay", 0.0) / driverCount : 0));
        
        return yPosition - boxHeight + 40;
    }
    
    private float drawTableHeaders(PDPageContentStream stream, float yPosition, float contentWidth) throws IOException {
        // Header background
        stream.setNonStrokingColor(0.9f, 0.9f, 0.9f);
        stream.addRect(MARGIN_LEFT, yPosition - LINE_HEIGHT, contentWidth, LINE_HEIGHT);
        stream.fill();
        
        // Headers
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(fontBold, FONT_SIZE_SMALL);
        
        float[] columnX = {
            MARGIN_LEFT + 5,
            MARGIN_LEFT + 120,
            MARGIN_LEFT + 180,
            MARGIN_LEFT + 230,
            MARGIN_LEFT + 300,
            MARGIN_LEFT + 370,
            MARGIN_LEFT + 440
        };
        
        String[] headers = {"Driver", "Truck", "Loads", "Gross", "Deductions", "Reimb.", "Net Pay"};
        
        stream.beginText();
        for (int i = 0; i < headers.length; i++) {
            stream.newLineAtOffset(i == 0 ? columnX[i] : columnX[i] - columnX[i-1], 0);
            stream.showText(headers[i]);
        }
        stream.endText();
        
        return yPosition - LINE_HEIGHT - 5;
    }
    
    private float drawEmployeeRow(PDPageContentStream stream, float yPosition, float contentWidth,
                                PayrollCalculator.PayrollRow row) throws IOException {
        float[] columnX = {
            MARGIN_LEFT + 5,
            MARGIN_LEFT + 120,
            MARGIN_LEFT + 180,
            MARGIN_LEFT + 230,
            MARGIN_LEFT + 300,
            MARGIN_LEFT + 370,
            MARGIN_LEFT + 440
        };
        
        // Alternate row coloring
        if ((int)(yPosition / LINE_HEIGHT) % 2 == 0) {
            stream.setNonStrokingColor(0.97f, 0.97f, 0.97f);
            stream.addRect(MARGIN_LEFT, yPosition - LINE_HEIGHT + 3, contentWidth, LINE_HEIGHT);
            stream.fill();
        }
        
        stream.setNonStrokingColor(0, 0, 0);
        
        // Truncate long names
        String driverName = row.driverName;
        if (driverName.length() > 15) {
            driverName = driverName.substring(0, 14) + "...";
        }
        
        double totalDeductions = Math.abs(row.fuel) + Math.abs(row.recurringFees) + 
                               Math.abs(row.advanceRepayments) + Math.abs(row.escrowDeposits) + 
                               Math.abs(row.otherDeductions);
        
        String[] values = {
            driverName,
            row.truckUnit,
            String.valueOf(row.loadCount),
            String.format("$%.2f", row.gross),
            String.format("$%.2f", totalDeductions),
            String.format("$%.2f", row.reimbursements),
            String.format("$%.2f", row.netPay)
        };
        
        stream.beginText();
        for (int i = 0; i < values.length; i++) {
            stream.newLineAtOffset(i == 0 ? columnX[i] : columnX[i] - columnX[i-1], 0);
            stream.showText(values[i]);
        }
        stream.endText();
        
        return yPosition - LINE_HEIGHT;
    }
    
    private void drawReportFooter(PDPageContentStream stream, float contentWidth) throws IOException {
        float yPosition = MARGIN_BOTTOM;
        
        // Draw separator
        stream.setStrokingColor(0.8f, 0.8f, 0.8f);
        stream.setLineWidth(0.5f);
        stream.moveTo(MARGIN_LEFT, yPosition + 20);
        stream.lineTo(MARGIN_LEFT + contentWidth, yPosition + 20);
        stream.stroke();
        
        stream.setNonStrokingColor(0.5f, 0.5f, 0.5f);
        stream.beginText();
        stream.setFont(fontItalic, FONT_SIZE_SMALL);
        stream.newLineAtOffset(MARGIN_LEFT, yPosition);
        stream.showText("Generated on: " + LocalDateTime.now().format(dateTimeFormatter));
        stream.endText();
        
        // Page number would go here in multi-page documents
    }
    
    private void drawInfoRow(PDPageContentStream stream, float y, String[] data) throws IOException {
        stream.beginText();
        stream.newLineAtOffset(MARGIN_LEFT + 15, y);
        stream.showText(data[0]);
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(MARGIN_LEFT + 120, y);
        stream.showText(data[1]);
        stream.endText();
        
        if (!data[2].isEmpty()) {
            stream.beginText();
            stream.newLineAtOffset(MARGIN_LEFT + 300, y);
            stream.showText(data[2]);
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(MARGIN_LEFT + 400, y);
            stream.showText(data[3]);
            stream.endText();
        }
    }
    
    private void drawAmountRow(PDPageContentStream stream, float y, String label, String amount) throws IOException {
        stream.beginText();
        stream.newLineAtOffset(MARGIN_LEFT + 20, y);
        stream.showText(label);
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(MARGIN_LEFT + 400, y);
        stream.showText(amount);
        stream.endText();
    }
    
    private void drawStatistic(PDPageContentStream stream, float x, float y, String label, String value) throws IOException {
        stream.beginText();
        stream.newLineAtOffset(x, y);
        stream.showText(label);
        stream.endText();
        
        stream.beginText();
        stream.setFont(fontBold, FONT_SIZE_NORMAL);
        stream.newLineAtOffset(x + 120, y);
        stream.showText(value);
        stream.endText();
        stream.setFont(fontNormal, FONT_SIZE_NORMAL);
    }
    
    private double calculateTotalDeductions(Map<String, Double> totals) {
        return Math.abs(totals.getOrDefault("fuel", 0.0)) +
               Math.abs(totals.getOrDefault("recurringFees", 0.0)) +
               Math.abs(totals.getOrDefault("advanceRepayments", 0.0)) +
               Math.abs(totals.getOrDefault("escrowDeposits", 0.0)) +
               Math.abs(totals.getOrDefault("otherDeductions", 0.0));
    }
}