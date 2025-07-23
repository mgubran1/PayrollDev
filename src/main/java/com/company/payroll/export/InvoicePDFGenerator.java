package com.company.payroll.export;

import com.company.payroll.loads.Load;
import com.company.payroll.config.InvoiceConfig;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Professional Invoice PDF Generator for Trucking Management System
 * Features modern design, comprehensive load details, and professional styling
 */
public class InvoicePDFGenerator {
    private static final Logger logger = LoggerFactory.getLogger(InvoicePDFGenerator.class);
    
    // Page dimensions and margins
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float MARGIN = 40;
    private static final float TOP_MARGIN = 40;
    private static final float BOTTOM_MARGIN = 40;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    
    // Enhanced color scheme - Modern professional palette
    private static final Color PRIMARY_COLOR = new Color(41, 98, 255);     // Modern blue
    private static final Color SECONDARY_COLOR = new Color(99, 102, 241);   // Purple accent
    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);     // Green
    private static final Color WARNING_COLOR = new Color(245, 158, 11);     // Amber
    private static final Color DANGER_COLOR = new Color(239, 68, 68);       // Red
    private static final Color GRAY_900 = new Color(17, 24, 39);            // Dark text
    private static final Color GRAY_700 = new Color(55, 65, 81);            // Medium text
    private static final Color GRAY_500 = new Color(107, 114, 128);         // Light text
    private static final Color GRAY_200 = new Color(229, 231, 235);         // Light border
    private static final Color GRAY_50 = new Color(249, 250, 251);          // Light background
    private static final Color WHITE = new Color(255, 255, 255);
    
    // Enhanced typography
    private PDType1Font displayFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDType1Font normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDType1Font italicFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    
    // Date formatters
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy 'at' hh:mm a");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    
    // Layout constants
    private static final float LINE_HEIGHT = 12f;
    private static final float SECTION_SPACING = 18f;
    private static final float TABLE_ROW_HEIGHT = 16f;
    
    /**
     * Generate enhanced professional invoice PDF for a load
     */
    public File generateInvoice(Load load, File outputFile) throws IOException {
        logger.info("Generating enhanced invoice for Load: {} to file: {}", 
            load.getLoadNumber(), outputFile.getAbsolutePath());
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = PAGE_HEIGHT - TOP_MARGIN;
                
                // Draw modern header with gradient effect simulation
                yPosition = drawEnhancedHeader(contentStream, yPosition);
                
                // Draw invoice metadata section
                yPosition = drawInvoiceMetadata(contentStream, load, yPosition - 20);
                
                // Draw customer information section
                yPosition = drawCustomerSection(contentStream, load, yPosition - 20);
                
                // Draw comprehensive load details
                yPosition = drawEnhancedLoadDetails(contentStream, load, yPosition - 20);
                
                // Draw pickup and delivery timeline
                yPosition = drawTimelineSection(contentStream, load, yPosition - 20);
                
                // Draw financial summary with modern styling
                yPosition = drawEnhancedFinancialSection(contentStream, load, yPosition - 20);
                
                // Draw professional footer
                drawEnhancedFooter(contentStream, load);
            }
            
            document.save(outputFile);
            logger.info("Enhanced invoice generated successfully: {}", outputFile.getAbsolutePath());
            return outputFile;
            
        } catch (Exception e) {
            logger.error("Failed to generate enhanced invoice PDF for load: {}", load.getLoadNumber(), e);
            throw new IOException("Failed to generate invoice: " + e.getMessage(), e);
        }
    }
    
    /**
     * Draw enhanced modern header with company branding
     */
    private float drawEnhancedHeader(PDPageContentStream contentStream, float yPosition) throws IOException {
        float headerHeight = 80f;
        
        // Main header background
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.addRect(0, yPosition - headerHeight, PAGE_WIDTH, headerHeight);
        contentStream.fill();
        
        // Company name - large and prominent
        contentStream.setNonStrokingColor(WHITE);
        contentStream.beginText();
        contentStream.setFont(titleFont, 20);
        contentStream.newLineAtOffset(MARGIN, yPosition - 28);
        contentStream.showText(InvoiceConfig.getCompanyName().toUpperCase());
        contentStream.endText();
        
        // Company address - left side
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition - 45);
        contentStream.showText(InvoiceConfig.getCompanyStreet());
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition - 56);
        contentStream.showText(String.format("%s, %s %s", 
            InvoiceConfig.getCompanyCity(), 
            InvoiceConfig.getCompanyState(), 
            InvoiceConfig.getCompanyZip()));
        contentStream.endText();
        
        // Contact information - right side
        float rightX = PAGE_WIDTH - MARGIN - 180;
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, yPosition - 45);
        contentStream.showText("Phone: " + InvoiceConfig.getCompanyPhone());
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, yPosition - 56);
        contentStream.showText("Email: " + InvoiceConfig.getCompanyEmail());
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, yPosition - 67);
        contentStream.showText("MC: " + InvoiceConfig.getCompanyMC());
        contentStream.endText();
        

        
        return yPosition - headerHeight;
    }
    
    /**
     * Draw invoice metadata section with modern card design
     */
    private float drawInvoiceMetadata(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // INVOICE title in the correct position
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.setFont(titleFont, 28);
        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_WIDTH - MARGIN - 120, yPosition);
        contentStream.showText("INVOICE");
        contentStream.endText();
        
        float boxHeight = 65f;
        float boxX = PAGE_WIDTH - MARGIN - 220;
        float boxY = yPosition - 35;
        
        // Background box
        contentStream.setNonStrokingColor(GRAY_50);
        contentStream.addRect(boxX, boxY - boxHeight, 220, boxHeight);
        contentStream.fill();
        
        // Generate invoice number
        String invoiceNumber = InvoiceConfig.getNextInvoiceNumber();
        
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(boldFont, 9);
        
        float labelX = boxX + 8;
        float valueX = boxX + 85;
        float currentY = boxY - 12;
        
        // Invoice Number
        contentStream.beginText();
        contentStream.newLineAtOffset(labelX, currentY);
        contentStream.showText("Invoice Number:");
        contentStream.endText();
        
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(valueX, currentY);
        contentStream.showText(invoiceNumber);
        contentStream.endText();
        
        // Invoice Date
        currentY -= 12;
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(labelX, currentY);
        contentStream.showText("Invoice Date:");
        contentStream.endText();
        
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(valueX, currentY);
        contentStream.showText(LocalDate.now().format(DATE_FORMAT));
        contentStream.endText();
        
        // Payment Terms
        currentY -= 12;
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(labelX, currentY);
        contentStream.showText("Payment Terms:");
        contentStream.endText();
        
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(valueX, currentY);
        contentStream.showText(InvoiceConfig.getInvoiceTerms());
        contentStream.endText();
        
        // Due Date
        currentY -= 12;
        LocalDate dueDate = calculateDueDate(InvoiceConfig.getInvoiceTerms());
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(labelX, currentY);
        contentStream.showText("Due Date:");
        contentStream.endText();
        
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(valueX, currentY);
        contentStream.showText(dueDate.format(DATE_FORMAT));
        contentStream.endText();
        
        return boxY - boxHeight - 10;
    }
    
    /**
     * Draw customer information section
     */
    private float drawCustomerSection(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Section header
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("BILL TO:");
        contentStream.endText();
        
        // Customer details
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition - 15);
        contentStream.showText(load.getBillTo() != null ? load.getBillTo() : load.getCustomer());
        contentStream.endText();
        
        float detailY = yPosition - 28;
        
        // PO Number if available
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            contentStream.setFont(boldFont, 9);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, detailY);
            contentStream.showText("PO#: " + load.getPONumber());
            contentStream.endText();
            detailY -= 12;
        }
        
        return detailY - 10;
    }
    
    /**
     * Draw simplified load details
     */
    private float drawEnhancedLoadDetails(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Section header
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("LOAD DETAILS");
        contentStream.endText();
        
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(normalFont, 9);
        
        float detailY = yPosition - 15;
        
        // Load Number
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailY);
        contentStream.showText("Load Number: " + (load.getLoadNumber() != null ? load.getLoadNumber() : ""));
        contentStream.endText();
        
        // Equipment (only show if not empty)
        String equipment = (load.getTruckUnitSnapshot() != null ? load.getTruckUnitSnapshot() : "") +
                          (load.getTrailer() != null ? " / " + load.getTrailer().getTrailerNumber() : "");
        equipment = equipment.trim();
        if (!equipment.isEmpty() && !equipment.equals(" /")) {
            detailY -= 12;
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, detailY);
            contentStream.showText("Equipment: " + equipment);
            contentStream.endText();
        }
        
        // Pickup Location
        detailY -= 12;
        String pickup = load.getPickUpLocation() != null ? load.getPickUpLocation() : "";
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailY);
        contentStream.showText("Pickup: " + (pickup.length() > 60 ? pickup.substring(0, 60) + "..." : pickup));
        contentStream.endText();
        
        // Delivery Location
        detailY -= 12;
        String delivery = load.getDropLocation() != null ? load.getDropLocation() : "";
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailY);
        contentStream.showText("Delivery: " + (delivery.length() > 60 ? delivery.substring(0, 60) + "..." : delivery));
        contentStream.endText();
        
        // Driver
        if (load.getDriver() != null) {
            detailY -= 12;
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, detailY);
            contentStream.showText("Driver: " + load.getDriver().getName());
            contentStream.endText();
        }
        
        return detailY - 15;
    }
    
    /**
     * Draw pickup and delivery dates
     */
    private float drawTimelineSection(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Section header
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("SHIPMENT DATES");
        contentStream.endText();
        
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(normalFont, 9);
        
        float dateY = yPosition - 15;
        
        // Pickup date
        if (load.getPickUpDate() != null) {
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, dateY);
            contentStream.showText("Pickup Date: " + load.getPickUpDate().format(DATE_FORMAT));
            if (load.getPickUpTime() != null) {
                contentStream.showText(" at " + load.getPickUpTime().format(TIME_FORMAT));
            }
            contentStream.endText();
            dateY -= 12;
        }
        
        // Delivery date
        if (load.getDeliveryDate() != null) {
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, dateY);
            contentStream.showText("Delivery Date: " + load.getDeliveryDate().format(DATE_FORMAT));
            if (load.getDeliveryTime() != null) {
                contentStream.showText(" at " + load.getDeliveryTime().format(TIME_FORMAT));
            }
            contentStream.endText();
            dateY -= 12;
        }
        
        return dateY - 10;
    }
    
    /**
     * Draw simplified financial section
     */
    private float drawEnhancedFinancialSection(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Section header
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("BILLING SUMMARY");
        contentStream.endText();
        
        // Financial details background
        float boxX = PAGE_WIDTH - MARGIN - 180;
        float boxY = yPosition - 15;
        float boxHeight = load.isHasLumper() && load.getLumperAmount() > 0 ? 75 : 60;
        
        contentStream.setNonStrokingColor(GRAY_50);
        contentStream.addRect(boxX, boxY - boxHeight, 180, boxHeight);
        contentStream.fill();
        
        // Calculate amounts
        BigDecimal grossAmount = BigDecimal.valueOf(load.getGrossAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lumperAmount = BigDecimal.valueOf(load.getLumperAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = grossAmount.add(lumperAmount).add(tax);
        
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(normalFont, 9);
        
        float lineY = boxY - 12;
        
        // Freight charges
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("Freight Charges:");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 120, lineY);
        contentStream.showText("$" + grossAmount.toString());
        contentStream.endText();
        
        // Lumper charges (if applicable)
        if (load.isHasLumper() && load.getLumperAmount() > 0) {
            lineY -= 10;
            contentStream.beginText();
            contentStream.newLineAtOffset(boxX + 10, lineY);
            contentStream.showText("Lumper Fees:");
            contentStream.endText();
            
            contentStream.beginText();
            contentStream.newLineAtOffset(boxX + 120, lineY);
            contentStream.showText("$" + lumperAmount.toString());
            contentStream.endText();
        }
        
        // Tax
        lineY -= 10;
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("Tax:");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 120, lineY);
        contentStream.showText("$" + tax.toString());
        contentStream.endText();
        
        // Total
        lineY -= 15;
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("TOTAL DUE:");
        contentStream.endText();
        
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 120, lineY);
        contentStream.showText("$" + total.toString());
        contentStream.endText();
        
        // Payment terms
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, boxY - boxHeight - 15);
        contentStream.showText("Payment Terms: " + InvoiceConfig.getInvoiceTerms());
        contentStream.endText();
        
        return boxY - boxHeight - 20;
    }
    
    /**
     * Draw enhanced professional footer
     */
    private void drawEnhancedFooter(PDPageContentStream contentStream, Load load) throws IOException {
        float footerY = BOTTOM_MARGIN + 35;
        
        // Payment terms and notes section
        if (InvoiceConfig.getInvoiceNotes() != null && !InvoiceConfig.getInvoiceNotes().isEmpty()) {
            contentStream.setNonStrokingColor(GRAY_50);
            contentStream.addRect(MARGIN, footerY - 30, CONTENT_WIDTH, 25);
            contentStream.fill();
            
            contentStream.setNonStrokingColor(GRAY_700);
            contentStream.setFont(italicFont, 8);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + 10, footerY - 18);
            contentStream.showText("Note: " + InvoiceConfig.getInvoiceNotes());
            contentStream.endText();
        }
        
        // Footer separator
        contentStream.setStrokingColor(GRAY_200);
        contentStream.setLineWidth(1);
        contentStream.moveTo(MARGIN, BOTTOM_MARGIN + 20);
        contentStream.lineTo(PAGE_WIDTH - MARGIN, BOTTOM_MARGIN + 20);
        contentStream.stroke();
        
        // Thank you message
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_WIDTH / 2 - 60, BOTTOM_MARGIN + 5);
        contentStream.showText("Thank you for your business!");
        contentStream.endText();
        
        // Footer details
        contentStream.setNonStrokingColor(GRAY_500);
        contentStream.setFont(normalFont, 7);
        
        // Left side - generated timestamp
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, BOTTOM_MARGIN - 8);
        contentStream.showText("Generated on " + LocalDateTime.now().format(DATETIME_FORMAT));
        contentStream.endText();
        
        // Right side - page number
        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_WIDTH - MARGIN - 50, BOTTOM_MARGIN - 8);
        contentStream.showText("Page 1 of 1");
        contentStream.endText();
        
        // Center - professional disclaimer
        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_WIDTH / 2 - 90, BOTTOM_MARGIN - 8);
        contentStream.showText("This invoice was electronically generated and is valid without signature.");
        contentStream.endText();
    }
    

    
    /**
     * Calculate due date based on payment terms
     */
    private LocalDate calculateDueDate(String terms) {
        LocalDate today = LocalDate.now();
        if (terms == null || terms.isEmpty()) {
            return today.plusDays(30); // Default to 30 days
        }
        
        String upperTerms = terms.toUpperCase();
        if (upperTerms.contains("NET 15")) {
            return today.plusDays(15);
        } else if (upperTerms.contains("NET 30")) {
            return today.plusDays(30);
        } else if (upperTerms.contains("NET 45")) {
            return today.plusDays(45);
        } else if (upperTerms.contains("NET 60")) {
            return today.plusDays(60);
        } else if (upperTerms.contains("DUE ON RECEIPT") || upperTerms.contains("IMMEDIATE")) {
            return today;
        } else {
            // Try to extract number from terms
            try {
                String[] parts = terms.split("\\s+");
                for (String part : parts) {
                    if (part.matches("\\d+")) {
                        int days = Integer.parseInt(part);
                        return today.plusDays(days);
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not parse payment terms: {}", terms);
            }
            return today.plusDays(30); // Default fallback
        }
    }
}