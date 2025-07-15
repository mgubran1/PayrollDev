package com.company.payroll.maintenance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.text.NumberFormat;
import java.util.stream.Collectors;

public class MaintenancePDFExporter {
    private static final Logger logger = LoggerFactory.getLogger(MaintenancePDFExporter.class);
    
    // Formatting
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    
    // PDF Layout Constants
    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    
    // Font sizes
    private static final float TITLE_FONT_SIZE = 24;
    private static final float HEADER_FONT_SIZE = 18;
    private static final float SUBHEADER_FONT_SIZE = 14;
    private static final float NORMAL_FONT_SIZE = 11;
    private static final float SMALL_FONT_SIZE = 9;
    
    // Colors
    private static final Color HEADER_COLOR = new Color(25, 118, 210); // #1976D2
    private static final Color ACCENT_COLOR = new Color(33, 150, 243); // #2196F3
    private static final Color LIGHT_GRAY = new Color(245, 245, 245);
    private static final Color DARK_GRAY = new Color(66, 66, 66);
    
    private final String companyName;
    private final List<MaintenanceRecord> records;
    private final LocalDate startDate;
    private final LocalDate endDate;
    
    public MaintenancePDFExporter(String companyName, List<MaintenanceRecord> records, 
                                  LocalDate startDate, LocalDate endDate) {
        this.companyName = companyName;
        this.records = new ArrayList<>(records);
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    public void exportToPDF(File file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // First page - Title and Summary
            PDPage firstPage = new PDPage(PDRectangle.LETTER);
            document.addPage(firstPage);
            float currentY = writeTitlePage(document, firstPage);
            
            // Detail pages
            currentY = writeDetailedRecords(document, firstPage, currentY);
            
            // Analytics page
            writeAnalyticsPage(document);
            
            // Save document
            document.save(file);
            logger.info("PDF exported successfully to: {}", file.getAbsolutePath());
        }
    }
    
    private float writeTitlePage(PDDocument document, PDPage page) throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            float y = PAGE_HEIGHT - MARGIN;
            
            // Company Name
            stream.setNonStrokingColor(HEADER_COLOR);
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_FONT_SIZE);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(companyName);
            stream.endText();
            y -= 35;
            
            // Report Title
            stream.setNonStrokingColor(DARK_GRAY);
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), HEADER_FONT_SIZE);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText("Maintenance Report");
            stream.endText();
            y -= 25;
            
            // Date Range
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
            stream.newLineAtOffset(MARGIN, y);
            String dateRange = String.format("%s to %s", 
                startDate.format(DATE_FORMAT), 
                endDate.format(DATE_FORMAT));
            stream.showText(dateRange);
            stream.endText();
            y -= 30;
            
            // Summary Section
            y = drawSummarySection(stream, y);
            
            return y;
        }
    }
    
    private float drawSummarySection(PDPageContentStream stream, float startY) throws IOException {
        float y = startY;
        
        // Calculate summary statistics
        double totalCost = records.stream().mapToDouble(MaintenanceRecord::getCost).sum();
        long totalRecords = records.size();
        double avgCost = totalRecords > 0 ? totalCost / totalRecords : 0;
        
        Map<MaintenanceRecord.VehicleType, Double> costByType = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getVehicleType,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        // Draw summary box
        stream.setNonStrokingColor(LIGHT_GRAY);
        stream.addRect(MARGIN, y - 120, CONTENT_WIDTH, 100);
        stream.fill();
        
        // Summary title
        y -= 20;
        stream.setNonStrokingColor(HEADER_COLOR);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SUBHEADER_FONT_SIZE);
        stream.newLineAtOffset(MARGIN + 10, y);
        stream.showText("Summary");
        stream.endText();
        y -= 25;
        
        // Summary data in columns
        float col1X = MARGIN + 10;
        float col2X = MARGIN + CONTENT_WIDTH/2;
        
        stream.setNonStrokingColor(DARK_GRAY);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
        
        // Total expenses
        stream.beginText();
        stream.newLineAtOffset(col1X, y);
        stream.showText("Total Expenses: " + CURRENCY_FORMAT.format(totalCost));
        stream.endText();
        
        // Total records
        stream.beginText();
        stream.newLineAtOffset(col2X, y);
        stream.showText("Total Records: " + totalRecords);
        stream.endText();
        y -= 20;
        
        // Average cost
        stream.beginText();
        stream.newLineAtOffset(col1X, y);
        stream.showText("Average Cost: " + CURRENCY_FORMAT.format(avgCost));
        stream.endText();
        
        // Truck expenses
        double truckCost = costByType.getOrDefault(MaintenanceRecord.VehicleType.TRUCK, 0.0);
        stream.beginText();
        stream.newLineAtOffset(col2X, y);
        stream.showText("Truck Expenses: " + CURRENCY_FORMAT.format(truckCost));
        stream.endText();
        y -= 20;
        
        // Trailer expenses
        double trailerCost = costByType.getOrDefault(MaintenanceRecord.VehicleType.TRAILER, 0.0);
        stream.beginText();
        stream.newLineAtOffset(col1X, y);
        stream.showText("Trailer Expenses: " + CURRENCY_FORMAT.format(trailerCost));
        stream.endText();
        
        return y - 40;
    }
    
    private float writeDetailedRecords(PDDocument document, PDPage currentPage, float startY) throws IOException {
        PDPageContentStream stream = new PDPageContentStream(document, currentPage, 
            PDPageContentStream.AppendMode.APPEND, true);
        
        float y = startY;
        
        // Section header
        stream.setNonStrokingColor(HEADER_COLOR);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SUBHEADER_FONT_SIZE);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Detailed Maintenance Records");
        stream.endText();
        y -= 25;
        
        // Table headers
        float[] columnWidths = {80, 60, 80, 150, 80, 100};
        String[] headers = {"Date", "Type", "Unit #", "Service", "Cost", "Notes"};
        
        // Draw header row
        stream.setNonStrokingColor(ACCENT_COLOR);
        stream.addRect(MARGIN, y - 20, CONTENT_WIDTH, 20);
        stream.fill();
        
        stream.setNonStrokingColor(Color.WHITE);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SMALL_FONT_SIZE);
        float xPos = MARGIN + 5;
        for (int i = 0; i < headers.length; i++) {
            stream.beginText();
            stream.newLineAtOffset(xPos, y - 15);
            stream.showText(headers[i]);
            stream.endText();
            xPos += columnWidths[i];
        }
        y -= 25;
        
        // Table rows
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), SMALL_FONT_SIZE);
        boolean alternateRow = false;
        
        for (MaintenanceRecord record : records) {
            // Check if we need a new page
            if (y < 100) {
                stream.close();
                PDPage newPage = new PDPage(PDRectangle.LETTER);
                document.addPage(newPage);
                stream = new PDPageContentStream(document, newPage);
                y = PAGE_HEIGHT - MARGIN;
                
                // Repeat headers on new page
                stream.setNonStrokingColor(ACCENT_COLOR);
                stream.addRect(MARGIN, y - 20, CONTENT_WIDTH, 20);
                stream.fill();
                
                stream.setNonStrokingColor(Color.WHITE);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SMALL_FONT_SIZE);
                xPos = MARGIN + 5;
                for (int i = 0; i < headers.length; i++) {
                    stream.beginText();
                    stream.newLineAtOffset(xPos, y - 15);
                    stream.showText(headers[i]);
                    stream.endText();
                    xPos += columnWidths[i];
                }
                y -= 25;
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), SMALL_FONT_SIZE);
            }
            
            // Alternate row background
            if (alternateRow) {
                stream.setNonStrokingColor(new Color(250, 250, 250));
                stream.addRect(MARGIN, y - 15, CONTENT_WIDTH, 15);
                stream.fill();
            }
            alternateRow = !alternateRow;
            
            // Row data
            stream.setNonStrokingColor(DARK_GRAY);
            xPos = MARGIN + 5;
            
            String[] rowData = {
                record.getDate().format(DATE_FORMAT),
                record.getVehicleType().toString(),
                record.getVehicle(),
                truncateText(record.getServiceType(), 20),
                CURRENCY_FORMAT.format(record.getCost()),
                truncateText(record.getNotes() != null ? record.getNotes() : "-", 15)
            };
            
            for (int i = 0; i < rowData.length; i++) {
                stream.beginText();
                stream.newLineAtOffset(xPos, y - 12);
                stream.showText(rowData[i]);
                stream.endText();
                xPos += columnWidths[i];
            }
            y -= 15;
        }
        
        stream.close();
        return y;
    }
    
    private void writeAnalyticsPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            float y = PAGE_HEIGHT - MARGIN;
            
            // Page title
            stream.setNonStrokingColor(HEADER_COLOR);
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADER_FONT_SIZE);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText("Maintenance Analytics");
            stream.endText();
            y -= 40;
            
            // Service Type Analysis
            y = drawServiceTypeAnalysis(stream, y);
            
            // Top Units by Cost
            y = drawTopUnitsAnalysis(stream, y);
            
            // Monthly Trend
            drawMonthlyTrend(stream, y);
        }
    }
    
    private float drawServiceTypeAnalysis(PDPageContentStream stream, float startY) throws IOException {
        float y = startY;
        
        // Calculate service type distribution
        Map<String, Double> serviceTypeCosts = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getServiceType,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        // Sort by cost descending
        List<Map.Entry<String, Double>> sortedServices = serviceTypeCosts.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        // Section title
        stream.setNonStrokingColor(ACCENT_COLOR);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SUBHEADER_FONT_SIZE);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Top Service Types by Cost");
        stream.endText();
        y -= 25;
        
        // Draw bars
        float maxCost = sortedServices.isEmpty() ? 0 : sortedServices.get(0).getValue().floatValue();
        float barMaxWidth = CONTENT_WIDTH * 0.6f;
        
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), SMALL_FONT_SIZE);
        
        for (Map.Entry<String, Double> entry : sortedServices) {
            // Service type name
            stream.setNonStrokingColor(DARK_GRAY);
            stream.beginText();
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(truncateText(entry.getKey(), 30));
            stream.endText();
            
            // Bar
            float barWidth = maxCost > 0 ? (float)(entry.getValue() / maxCost * barMaxWidth) : 0;
            stream.setNonStrokingColor(ACCENT_COLOR);
            stream.addRect(MARGIN + 200, y - 2, barWidth, 12);
            stream.fill();
            
            // Value
            stream.setNonStrokingColor(DARK_GRAY);
            stream.beginText();
            stream.newLineAtOffset(MARGIN + 210 + barWidth, y);
            stream.showText(CURRENCY_FORMAT.format(entry.getValue()));
            stream.endText();
            
            y -= 18;
        }
        
        return y - 20;
    }
    
    private float drawTopUnitsAnalysis(PDPageContentStream stream, float startY) throws IOException {
        float y = startY;
        
        // Calculate top units by maintenance cost
        Map<String, Double> unitCosts = records.stream()
            .collect(Collectors.groupingBy(
                MaintenanceRecord::getVehicle,
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        List<Map.Entry<String, Double>> topUnits = unitCosts.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());
        
        // Section title
        stream.setNonStrokingColor(ACCENT_COLOR);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SUBHEADER_FONT_SIZE);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Top 5 Units by Maintenance Cost");
        stream.endText();
        y -= 25;
        
        // Draw table
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);
        int rank = 1;
        
        for (Map.Entry<String, Double> entry : topUnits) {
            stream.setNonStrokingColor(DARK_GRAY);
            stream.beginText();
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(rank + ". " + entry.getKey() + " - " + CURRENCY_FORMAT.format(entry.getValue()));
            stream.endText();
            y -= 20;
            rank++;
        }
        
        return y - 20;
    }
    
    private void drawMonthlyTrend(PDPageContentStream stream, float startY) throws IOException {
        float y = startY;
        
        // Section title
        stream.setNonStrokingColor(ACCENT_COLOR);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SUBHEADER_FONT_SIZE);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Monthly Maintenance Trend");
        stream.endText();
        y -= 30;
        
        // Calculate monthly totals
        Map<String, Double> monthlyTotals = records.stream()
            .collect(Collectors.groupingBy(
                record -> record.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.summingDouble(MaintenanceRecord::getCost)
            ));
        
        // Sort by month
        List<Map.Entry<String, Double>> sortedMonths = monthlyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());
        
        if (!sortedMonths.isEmpty()) {
            // Find max for scaling
            double maxMonthly = sortedMonths.stream()
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0);
            
            // Draw simple bar chart
            float barWidth = 40;
            float maxBarHeight = 100;
            float chartX = MARGIN;
            
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), SMALL_FONT_SIZE);
            
            for (Map.Entry<String, Double> entry : sortedMonths) {
                // Bar
                float barHeight = maxMonthly > 0 ? (float)(entry.getValue() / maxMonthly * maxBarHeight) : 0;
                stream.setNonStrokingColor(ACCENT_COLOR);
                stream.addRect(chartX, y - barHeight, barWidth - 5, barHeight);
                stream.fill();
                
                // Month label
                stream.setNonStrokingColor(DARK_GRAY);
                stream.beginText();
                stream.newLineAtOffset(chartX, y - barHeight - 15);
                String monthLabel = entry.getKey().substring(5); // Just MM
                stream.showText(monthLabel);
                stream.endText();
                
                // Value on top
                stream.beginText();
                stream.newLineAtOffset(chartX - 5, y - barHeight + 5);
                stream.showText(formatCurrency(entry.getValue()));
                stream.endText();
                
                chartX += barWidth;
            }
        }
    }
    
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
    
    private String formatCurrency(double amount) {
        if (amount >= 1000) {
            return String.format("$%.1fK", amount / 1000);
        }
        return CURRENCY_FORMAT.format(amount);
    }
}