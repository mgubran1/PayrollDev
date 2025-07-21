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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Professional Invoice PDF Generator for Trucking Management System
 */
public class InvoicePDFGenerator {
    private static final Logger logger = LoggerFactory.getLogger(InvoicePDFGenerator.class);
    
    // Page dimensions and margins
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float MARGIN = 50;
    private static final float TOP_MARGIN = 50;
    private static final float BOTTOM_MARGIN = 50;
    
    // Colors
    private static final Color HEADER_COLOR = new Color(41, 128, 185); // Professional blue
    private static final Color ACCENT_COLOR = new Color(52, 152, 219); // Lighter blue
    private static final Color TEXT_COLOR = new Color(44, 62, 80); // Dark gray
    private static final Color LIGHT_GRAY = new Color(236, 240, 241);
    
    // Fonts
    private PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDType1Font normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
    
    /**
     * Generate invoice PDF for a load
     */
    public File generateInvoice(Load load, File outputFile) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = PAGE_HEIGHT - TOP_MARGIN;
                
                // Draw header
                yPosition = drawHeader(contentStream, yPosition);
                
                // Draw invoice info section
                yPosition = drawInvoiceInfo(contentStream, load, yPosition - 30);
                
                // Draw bill to section
                yPosition = drawBillToSection(contentStream, load, yPosition - 30);
                
                // Draw load details table
                yPosition = drawLoadDetailsTable(contentStream, load, yPosition - 30);
                
                // Draw rate and charges
                yPosition = drawRateSection(contentStream, load, yPosition - 30);
                
                // Draw footer
                drawFooter(contentStream, load);
            }
            
            document.save(outputFile);
            logger.info("Invoice generated successfully: {}", outputFile.getAbsolutePath());
            return outputFile;
            
        } catch (Exception e) {
            logger.error("Failed to generate invoice PDF", e);
            throw new IOException("Failed to generate invoice: " + e.getMessage(), e);
        }
    }
    
    /**
     * Draw professional header with company info
     */
    private float drawHeader(PDPageContentStream contentStream, float yPosition) throws IOException {
        // Draw header background
        contentStream.setNonStrokingColor(HEADER_COLOR);
        contentStream.addRect(0, yPosition - 100, PAGE_WIDTH, 100);
        contentStream.fill();
        
        // Company name in white
        contentStream.setNonStrokingColor(Color.WHITE);
        contentStream.beginText();
        contentStream.setFont(titleFont, 24);
        contentStream.newLineAtOffset(MARGIN, yPosition - 40);
        contentStream.showText(InvoiceConfig.getCompanyName().toUpperCase());
        contentStream.endText();
        
        // Company details
        contentStream.setFont(normalFont, 10);
        float detailsY = yPosition - 60;
        
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailsY);
        contentStream.showText(InvoiceConfig.getCompanyStreet());
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailsY - 12);
        contentStream.showText(InvoiceConfig.getCompanyCity() + ", " + 
                            InvoiceConfig.getCompanyState() + " " + InvoiceConfig.getCompanyZip());
        contentStream.endText();
        
        // Contact info on the right
        float rightX = PAGE_WIDTH - MARGIN - 200;
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, detailsY);
        contentStream.showText("Phone: " + InvoiceConfig.getCompanyPhone());
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, detailsY - 12);
        contentStream.showText("Email: " + InvoiceConfig.getCompanyEmail());
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, detailsY - 24);
        contentStream.showText("MC: " + InvoiceConfig.getCompanyMC());
        contentStream.endText();
        
        // Draw "INVOICE" text
        contentStream.setNonStrokingColor(ACCENT_COLOR);
        contentStream.beginText();
        contentStream.setFont(titleFont, 36);
        contentStream.newLineAtOffset(PAGE_WIDTH - MARGIN - 150, yPosition - 95);
        contentStream.showText("INVOICE");
        contentStream.endText();
        
        return yPosition - 100;
    }
    
    /**
     * Draw invoice information section
     */
    private float drawInvoiceInfo(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        String invoiceNumber = InvoiceConfig.getNextInvoiceNumber();
        
        // Invoice details box
        float boxX = PAGE_WIDTH - MARGIN - 250;
        float boxY = yPosition - 60;
        
        contentStream.setNonStrokingColor(LIGHT_GRAY);
        contentStream.addRect(boxX, boxY, 250, 60);
        contentStream.fill();
        
        contentStream.setNonStrokingColor(TEXT_COLOR);
        contentStream.setFont(boldFont, 10);
        
        // Invoice number
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, boxY + 40);
        contentStream.showText("Invoice #: ");
        contentStream.setFont(normalFont, 10);
        contentStream.showText(invoiceNumber);
        contentStream.endText();
        
        // Date
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, boxY + 25);
        contentStream.showText("Date: ");
        contentStream.setFont(normalFont, 10);
        contentStream.showText(LocalDate.now().format(DATE_FORMAT));
        contentStream.endText();
        
        // Terms
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, boxY + 10);
        contentStream.showText("Terms: ");
        contentStream.setFont(normalFont, 10);
        contentStream.showText(InvoiceConfig.getInvoiceTerms());
        contentStream.endText();
        
        return yPosition;
    }
    
    /**
     * Draw bill to section
     */
    private float drawBillToSection(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Section title
        contentStream.setNonStrokingColor(HEADER_COLOR);
        contentStream.beginText();
        contentStream.setFont(headerFont, 12);
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("BILL TO:");
        contentStream.endText();
        
        // Bill to details
        contentStream.setNonStrokingColor(TEXT_COLOR);
        contentStream.setFont(normalFont, 11);
        
        float detailY = yPosition - 20;
        
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailY);
        contentStream.showText(load.getBillTo() != null ? load.getBillTo() : load.getCustomer());
        contentStream.endText();
        
        // Add additional billing address if available
        if (load.getDropLocation() != null) {
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, detailY - 15);
            contentStream.showText(load.getDropLocation());
            contentStream.endText();
        }
        
        return yPosition - 80;
    }
    
    /**
     * Draw load details table
     */
    private float drawLoadDetailsTable(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Table header
        contentStream.setNonStrokingColor(HEADER_COLOR);
        contentStream.addRect(MARGIN, yPosition - 25, PAGE_WIDTH - 2 * MARGIN, 25);
        contentStream.fill();
        
        // Header text
        contentStream.setNonStrokingColor(Color.WHITE);
        contentStream.setFont(boldFont, 10);
        
        float[] columnX = {MARGIN + 10, MARGIN + 100, MARGIN + 200, MARGIN + 350, MARGIN + 450};
        
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[0], yPosition - 17);
        contentStream.showText("LOAD #");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[1], yPosition - 17);
        contentStream.showText("PO #");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[2], yPosition - 17);
        contentStream.showText("PICKUP");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[3], yPosition - 17);
        contentStream.showText("DELIVERY");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[4], yPosition - 17);
        contentStream.showText("STATUS");
        contentStream.endText();
        
        // Table content
        float rowY = yPosition - 45;
        
        // Alternate row background
        contentStream.setNonStrokingColor(new Color(248, 249, 250));
        contentStream.addRect(MARGIN, rowY - 20, PAGE_WIDTH - 2 * MARGIN, 25);
        contentStream.fill();
        
        contentStream.setNonStrokingColor(TEXT_COLOR);
        contentStream.setFont(normalFont, 10);
        
        // Load number
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[0], rowY - 15);
        contentStream.showText(load.getLoadNumber());
        contentStream.endText();
        
        // PO number
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[1], rowY - 15);
        contentStream.showText(load.getPONumber() != null ? load.getPONumber() : "");
        contentStream.endText();
        
        // Pickup info
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[2], rowY - 15);
        String pickup = load.getPickUpLocation() != null ? 
            load.getPickUpLocation().substring(0, Math.min(load.getPickUpLocation().length(), 25)) : "";
        contentStream.showText(pickup);
        contentStream.endText();
        
        // Delivery info
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[3], rowY - 15);
        String delivery = load.getDropLocation() != null ? 
            load.getDropLocation().substring(0, Math.min(load.getDropLocation().length(), 25)) : "";
        contentStream.showText(delivery);
        contentStream.endText();
        
        // Status
        contentStream.beginText();
        contentStream.newLineAtOffset(columnX[4], rowY - 15);
        contentStream.showText("DELIVERED");
        contentStream.endText();
        
        // Additional details section
        float detailsY = rowY - 60;
        
        // Dates
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, detailsY);
        contentStream.showText("Pickup Date: ");
        contentStream.setFont(normalFont, 10);
        contentStream.showText(load.getPickUpDate() != null ? 
            load.getPickUpDate().format(DATETIME_FORMAT) : "");
        contentStream.endText();
        
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN + 250, detailsY);
        contentStream.showText("Delivery Date: ");
        contentStream.setFont(normalFont, 10);
        contentStream.showText(load.getDeliveryDate() != null ? 
            load.getDeliveryDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) : "");
        contentStream.endText();
        
        // Driver info
        if (load.getDriver() != null) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, detailsY - 20);
            contentStream.showText("Driver: ");
            contentStream.setFont(normalFont, 10);
            contentStream.showText(load.getDriver().getName());
            contentStream.endText();
        }
        
        // Truck/Trailer info
        if (load.getTruckUnitSnapshot() != null && !load.getTruckUnitSnapshot().isEmpty()) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + 250, detailsY - 20);
            contentStream.showText("Equipment: ");
            contentStream.setFont(normalFont, 10);
            contentStream.showText(load.getTruckUnitSnapshot() + 
                (load.getTrailer() != null ? " / " + load.getTrailer().getTrailerNumber() : ""));
            contentStream.endText();
        }
        
        return detailsY - 40;
    }
    
    /**
     * Draw rate and charges section
     */
    private float drawRateSection(PDPageContentStream contentStream, Load load, float yPosition) throws IOException {
        // Calculate amounts
        BigDecimal rate = BigDecimal.valueOf(load.getGrossAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = rate;
        BigDecimal tax = BigDecimal.ZERO; // Can be calculated if needed
        BigDecimal total = subtotal.add(tax);
        
        // Rate box
        float boxX = PAGE_WIDTH - MARGIN - 250;
        float boxY = yPosition - 120;
        
        // Box background
        contentStream.setNonStrokingColor(new Color(248, 249, 250));
        contentStream.addRect(boxX, boxY, 250, 120);
        contentStream.fill();
        
        // Box border
        contentStream.setStrokingColor(new Color(220, 220, 220));
        contentStream.setLineWidth(1);
        contentStream.addRect(boxX, boxY, 250, 120);
        contentStream.stroke();
        
        contentStream.setNonStrokingColor(TEXT_COLOR);
        
        // Line items
        float lineY = boxY + 90;
        
        // Freight charges
        contentStream.setFont(normalFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("Freight Charges:");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 180, lineY);
        contentStream.showText("$" + rate.toString());
        contentStream.endText();
        
        // Subtotal
        lineY -= 20;
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("Subtotal:");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 180, lineY);
        contentStream.showText("$" + subtotal.toString());
        contentStream.endText();
        
        // Tax
        lineY -= 20;
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("Tax:");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 180, lineY);
        contentStream.showText("$" + tax.toString());
        contentStream.endText();
        
        // Separator line
        lineY -= 10;
        contentStream.setLineWidth(1);
        contentStream.moveTo(boxX + 10, lineY);
        contentStream.lineTo(boxX + 240, lineY);
        contentStream.stroke();
        
        // Total
        lineY -= 20;
        contentStream.setFont(boldFont, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 10, lineY);
        contentStream.showText("TOTAL DUE:");
        contentStream.endText();
        
        contentStream.setNonStrokingColor(HEADER_COLOR);
        contentStream.beginText();
        contentStream.newLineAtOffset(boxX + 170, lineY);
        contentStream.showText("$" + total.toString());
        contentStream.endText();
        
        // Payment terms reminder
        contentStream.setNonStrokingColor(TEXT_COLOR);
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, boxY + 30);
        contentStream.showText("Payment Terms: " + InvoiceConfig.getInvoiceTerms());
        contentStream.endText();
        
        // Notes
        if (InvoiceConfig.getInvoiceNotes() != null && !InvoiceConfig.getInvoiceNotes().isEmpty()) {
            contentStream.setFont(normalFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, boxY - 20);
            contentStream.showText("Notes: " + InvoiceConfig.getInvoiceNotes());
            contentStream.endText();
        }
        
        return boxY - 40;
    }
    
    /**
     * Draw footer
     */
    private void drawFooter(PDPageContentStream contentStream, Load load) throws IOException {
        // Footer separator
        contentStream.setStrokingColor(new Color(220, 220, 220));
        contentStream.setLineWidth(1);
        contentStream.moveTo(MARGIN, BOTTOM_MARGIN + 40);
        contentStream.lineTo(PAGE_WIDTH - MARGIN, BOTTOM_MARGIN + 40);
        contentStream.stroke();
        
        // Thank you message
        contentStream.setNonStrokingColor(ACCENT_COLOR);
        contentStream.setFont(boldFont, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_WIDTH / 2 - 60, BOTTOM_MARGIN + 20);
        contentStream.showText("Thank you for your business!");
        contentStream.endText();
        
        // Footer text
        contentStream.setNonStrokingColor(new Color(150, 150, 150));
        contentStream.setFont(normalFont, 8);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, BOTTOM_MARGIN);
        contentStream.showText("This is a computer-generated invoice. No signature required.");
        contentStream.endText();
        
        // Page number
        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_WIDTH - MARGIN - 50, BOTTOM_MARGIN);
        contentStream.showText("Page 1 of 1");
        contentStream.endText();
    }
}