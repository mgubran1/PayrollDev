package com.company.payroll.loads;

import com.company.payroll.employees.Employee;
import com.company.payroll.trailers.Trailer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class LoadConfirmationGenerator {
    private static final Logger logger = LoggerFactory.getLogger(LoadConfirmationGenerator.class);
    
    private static final float MARGIN = 72; // 1 inch = 72 points for better centering
    private static final float LINE_HEIGHT = 12;
    private static final float SECTION_SPACING = 15;
    private static final float SMALL_LINE_HEIGHT = 10;
    
    private final LoadConfirmationConfig config;
    private PDType1Font normalFont;
    private PDType1Font boldFont;
    
    public LoadConfirmationGenerator() {
        this.config = LoadConfirmationConfig.getInstance();
        this.normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        this.boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }
    
    public PDDocument generateLoadConfirmation(Load load) throws IOException {
        PDDocument document = new PDDocument();
        
        // Always use portrait orientation for consistency with print preview
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            float y = height - MARGIN;
            
            // Add header
            y = addHeader(contentStream, load, y, width);
            
            // Add horizontal line
            y = addHorizontalLine(contentStream, y, width);
            
            // Add main grid layout
            // Add basic info section
            y = addBasicInfo(contentStream, load, y, width);
            
            // Add separator
            y = addHorizontalLine(contentStream, y, width);
            
            // Add pickup section
            y = addPickupSection(contentStream, load, y, width);
            
            // Add separator
            y = addHorizontalLine(contentStream, y, width);
            
            // Add drop section
            y = addDropSection(contentStream, load, y, width);
            
            // Add notes if present
            if (load.getNotes() != null && !load.getNotes().isEmpty()) {
                y = addNotesSection(contentStream, load, y, width);
            }
            
            // Add pickup and delivery policy
            y = addPickupDeliveryPolicy(contentStream, y, width);
            
            // Add dispatcher information at the bottom
            if (y > 100) {
                addDispatcherInformation(contentStream, 60);
            } else {
                // Start new page if needed
                contentStream.close();
                PDPage newPage = new PDPage(PDRectangle.LETTER);
                document.addPage(newPage);
                PDPageContentStream newContentStream = new PDPageContentStream(document, newPage);
                addDispatcherInformation(newContentStream, height - MARGIN - 20);
                newContentStream.close();
            }
        }
        
        return document;
    }
    
    private float addHeader(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        // Company name
        String companyName = getCompanyNameFromConfig();
        contentStream.setFont(boldFont, 14);
        float companyWidth = boldFont.getStringWidth(companyName) / 1000 * 14;
        contentStream.beginText();
        contentStream.newLineAtOffset((width - companyWidth) / 2, y);
        contentStream.showText(companyName);
        contentStream.endText();
        y -= 12;
        
        // Title
        contentStream.setFont(boldFont, 12);
        String title = "LOAD CONFIRMATION";
        float titleWidth = boldFont.getStringWidth(title) / 1000 * 12;
        contentStream.beginText();
        contentStream.newLineAtOffset((width - titleWidth) / 2, y);
        contentStream.showText(title);
        contentStream.endText();
        y -= 10;
        
        return y - 5;
    }
    
    private float addBasicInfo(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        float contentWidth = width - 2 * MARGIN;
        float leftX = MARGIN;
        float rightX = MARGIN + (contentWidth * 0.6f); // 60% for left column
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        Employee driver = load.getDriver();
        
        // Left column
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Load #:");
        contentStream.endText();
        
        contentStream.setFont(boldFont, 10);
        contentStream.setNonStrokingColor(0.15f, 0.68f, 0.38f); // Green color
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 50, y);
        contentStream.showText(load.getLoadNumber());
        contentStream.endText();
        contentStream.setNonStrokingColor(0, 0, 0); // Back to black
        
        // Right column - Driver
        if (driver != null) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Driver:");
            contentStream.endText();
            
            contentStream.setFont(boldFont, 10);
            contentStream.setNonStrokingColor(0.15f, 0.68f, 0.38f); // Green color
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + 45, y);
            contentStream.showText(driver.getName());
            contentStream.endText();
            contentStream.setNonStrokingColor(0, 0, 0); // Back to black
        }
        y -= LINE_HEIGHT;
        
        // Pickup Date
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Pickup Date:");
        contentStream.endText();
        
        String pickupDate = load.getPickUpDate() != null ? load.getPickUpDate().format(dateFormatter) : "TBD";
        contentStream.setFont(normalFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 70, y);
        contentStream.showText(pickupDate);
        contentStream.endText();
        
        // Truck
        if (driver != null) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Truck:");
            contentStream.endText();
            
            String truckUnit = load.getTruckUnitSnapshot() != null ? load.getTruckUnitSnapshot() : 
                             (driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A");
            contentStream.setFont(normalFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + 45, y);
            contentStream.showText(truckUnit);
            contentStream.endText();
        }
        y -= LINE_HEIGHT;
        
        // Delivery Date
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Delivery Date:");
        contentStream.endText();
        
        String deliveryDate = load.getDeliveryDate() != null ? load.getDeliveryDate().format(dateFormatter) : "TBD";
        contentStream.setFont(normalFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 75, y);
        contentStream.showText(deliveryDate);
        contentStream.endText();
        
        // Trailer
        if (driver != null) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Trailer:");
            contentStream.endText();
            
            Trailer trailer = load.getTrailer();
            String trailerInfo = "N/A";
            if (trailer != null) {
                trailerInfo = trailer.getTrailerNumber();
                if (trailer.getType() != null && !trailer.getType().isEmpty()) {
                    trailerInfo += " (" + trailer.getType() + ")";
                }
            } else if (load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty()) {
                trailerInfo = load.getTrailerNumber();
            }
            
            contentStream.setFont(normalFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + 45, y);
            contentStream.showText(trailerInfo);
            contentStream.endText();
        }
        y -= LINE_HEIGHT;
        
        // Gross Amount and Mobile
        if (config.isShowGrossAmount() && load.getGrossAmount() > 0.0) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX, y);
            contentStream.showText("Gross Amount:");
            contentStream.endText();
            
            contentStream.setFont(boldFont, 10);
            contentStream.setNonStrokingColor(0.15f, 0.68f, 0.38f); // Green color
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX + 80, y);
            contentStream.showText("$" + String.format("%.2f", load.getGrossAmount()));
            contentStream.endText();
            contentStream.setNonStrokingColor(0, 0, 0); // Back to black
        }
        
        if (driver != null && driver.getPhone() != null && !driver.getPhone().isEmpty()) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Mobile:");
            contentStream.endText();
            
            contentStream.setFont(normalFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + 45, y);
            contentStream.showText(driver.getPhone());
            contentStream.endText();
        }
        
        return y - SECTION_SPACING;
    }
    
    private float addPickupSection(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        // Section header with gray background box
        contentStream.setNonStrokingColor(0.94f, 0.94f, 0.94f);
        contentStream.addRect(MARGIN, y - 12, width - 2 * MARGIN, 14);
        contentStream.fill();
        
        // Black text for section header
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN + 5, y - 10);
        contentStream.showText("PICKUP INFORMATION");
        contentStream.endText();
        y -= LINE_HEIGHT + 5;
        
        // Add double spacing after section header
        y -= LINE_HEIGHT;
        
        // Pickup details with indentation
        float indent = MARGIN + 20;
        
        // Customer
        y = addDetailRow(contentStream, "Customer:", load.getCustomer(), indent, y);
        
        // Address
        y = addDetailRow(contentStream, "Address:", load.getPickUpLocation(), indent, y);
        
        // Date/Time
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        y = addDetailRow(contentStream, "Date/Time:", pickupDateTime, indent, y);
        
        // PO Number
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            y = addDetailRow(contentStream, "PO #:", load.getPONumber(), indent, y);
        }
        
        // Additional pickup locations
        List<LoadLocation> additionalPickups = getAdditionalLocations(load, LoadLocation.LocationType.PICKUP);
        if (!additionalPickups.isEmpty()) {
            y -= 5;
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(indent, y);
            contentStream.showText("Additional Pickup Locations:");
            contentStream.endText();
            y -= LINE_HEIGHT;
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                String info = String.format("%d. %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc));
                
                if (loc.getTime() != null) {
                    info += " @ " + loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a"));
                }
                
                contentStream.setFont(normalFont, 9);
                contentStream.beginText();
                contentStream.newLineAtOffset(indent + 15, y);
                contentStream.showText(info);
                contentStream.endText();
                y -= SMALL_LINE_HEIGHT;
                
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    contentStream.setFont(normalFont, 8);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(indent + 25, y);
                    contentStream.showText("Notes: " + loc.getNotes());
                    contentStream.endText();
                    y -= SMALL_LINE_HEIGHT;
                }
            }
        }
        
        return y - SECTION_SPACING;
    }
    
    private float addDropSection(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        // Section header with gray background box
        contentStream.setNonStrokingColor(0.94f, 0.94f, 0.94f);
        contentStream.addRect(MARGIN, y - 12, width - 2 * MARGIN, 14);
        contentStream.fill();
        
        // Black text for section header
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN + 5, y - 10);
        contentStream.showText("DROP INFORMATION");
        contentStream.endText();
        y -= LINE_HEIGHT + 5;
        
        // Add double spacing after section header
        y -= LINE_HEIGHT;
        
        // Drop details with indentation
        float indent = MARGIN + 20;
        
        // Customer
        String dropCustomer = load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer();
        y = addDetailRow(contentStream, "Customer:", dropCustomer, indent, y);
        
        // Address
        y = addDetailRow(contentStream, "Address:", load.getDropLocation(), indent, y);
        
        // Date/Time
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        y = addDetailRow(contentStream, "Date/Time:", dropDateTime, indent, y);
        
        // Additional drop locations
        List<LoadLocation> additionalDrops = getAdditionalLocations(load, LoadLocation.LocationType.DROP);
        if (!additionalDrops.isEmpty()) {
            y -= 5;
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(indent, y);
            contentStream.showText("Additional Drop Locations:");
            contentStream.endText();
            y -= LINE_HEIGHT;
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                String info = String.format("%d. %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc));
                
                if (loc.getTime() != null) {
                    info += " @ " + loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a"));
                }
                
                contentStream.setFont(normalFont, 9);
                contentStream.beginText();
                contentStream.newLineAtOffset(indent + 15, y);
                contentStream.showText(info);
                contentStream.endText();
                y -= SMALL_LINE_HEIGHT;
                
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    contentStream.setFont(normalFont, 8);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(indent + 25, y);
                    contentStream.showText("Notes: " + loc.getNotes());
                    contentStream.endText();
                    y -= SMALL_LINE_HEIGHT;
                }
            }
        }
        
        return y - SECTION_SPACING;
    }
    
    private float addNotesSection(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        // Section header with gray background box
        contentStream.setNonStrokingColor(0.94f, 0.94f, 0.94f);
        contentStream.addRect(MARGIN, y - 12, width - 2 * MARGIN, 14);
        contentStream.fill();
        
        // Black text for section header
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN + 5, y - 10);
        contentStream.showText("NOTES");
        contentStream.endText();
        y -= LINE_HEIGHT + 5;
        
        // Notes text in red
        contentStream.setFont(boldFont, 10);
        contentStream.setNonStrokingColor(0.91f, 0.30f, 0.24f); // Red color
        
        // Wrap notes text if needed
        List<String> wrappedLines = wrapText(load.getNotes(), width - 2 * MARGIN - 40, boldFont, 10);
        for (String line : wrappedLines) {
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + 20, y);
            contentStream.showText(line);
            contentStream.endText();
            y -= LINE_HEIGHT;
        }
        
        contentStream.setNonStrokingColor(0, 0, 0); // Back to black
        return y - SECTION_SPACING;
    }
    
    private float addDetailRow(PDPageContentStream contentStream, String label, String value, float x, float y) throws IOException {
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(label);
        contentStream.endText();
        
        contentStream.setFont(normalFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(x + 80, y);
        contentStream.showText(value != null ? value : "");
        contentStream.endText();
        
        return y - LINE_HEIGHT;
    }
    
    private List<LoadLocation> getAdditionalLocations(Load load, LoadLocation.LocationType type) {
        if (load.getLocations() == null) {
            return new ArrayList<>();
        }
        
        // Only return locations with sequence > 1 (additional locations)
        // Primary locations (sequence 1) are already shown in the main pickup/drop sections
        return load.getLocations().stream()
            .filter(loc -> loc.getType() == type && loc.getSequence() > 1)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    private float addMainGrid(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        float contentWidth = width - 2 * MARGIN;
        float leftX = MARGIN;
        float rightX = MARGIN + (contentWidth * 0.6f); // 60% for left column
        
        // Row 1: Load #, Driver
        contentStream.setFont(boldFont, 8);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Load #: " + load.getLoadNumber());
        contentStream.endText();
        
        Employee driver = load.getDriver();
        if (driver != null) {
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Driver: " + driver.getName());
            contentStream.endText();
        }
        y -= 6;
        
        // Row 2: Pickup Date, Truck
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String pickupDate = load.getPickUpDate() != null ? 
            "Pickup Date: " + load.getPickUpDate().format(dateFormatter) : "Pickup Date: TBD";
        contentStream.setFont(normalFont, 8);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText(pickupDate);
        contentStream.endText();
        
        if (driver != null) {
            String truckUnit = load.getTruckUnitSnapshot() != null ? load.getTruckUnitSnapshot() : 
                             (driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A");
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Truck: " + truckUnit);
            contentStream.endText();
        }
        y -= 6;
        
        // Row 3: Delivery Date, Trailer
        String deliveryDate = load.getDeliveryDate() != null ? 
            "Delivery Date: " + load.getDeliveryDate().format(dateFormatter) : "Delivery Date: TBD";
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText(deliveryDate);
        contentStream.endText();
        
        if (driver != null) {
            Trailer trailer = load.getTrailer();
            String trailerInfo = "N/A";
            if (trailer != null) {
                trailerInfo = trailer.getTrailerNumber();
                if (trailer.getType() != null && !trailer.getType().isEmpty()) {
                    trailerInfo += " (" + trailer.getType() + ")";
                }
            } else if (load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty()) {
                trailerInfo = load.getTrailerNumber();
            }
            
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Trailer: " + trailerInfo);
            contentStream.endText();
        }
        y -= 6;
        
        // Row 4: Gross Amount, Mobile
        if (config.isShowGrossAmount() && load.getGrossAmount() > 0.0) {
            contentStream.setFont(boldFont, 8);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX, y);
            contentStream.showText("Gross Amount: $" + String.format("%.2f", load.getGrossAmount()));
            contentStream.endText();
        }
        
        if (driver != null && driver.getPhone() != null && !driver.getPhone().isEmpty()) {
            contentStream.setFont(normalFont, 8);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Mobile: " + driver.getPhone());
            contentStream.endText();
        }
        y -= 6;
        
        // Row 5: Pickup Customer, Pickup Address
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Pickup Customer: " + (load.getCustomer() != null ? load.getCustomer() : ""));
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, y);
        contentStream.showText("Pickup Address: " + (load.getPickUpLocation() != null ? load.getPickUpLocation() : ""));
        contentStream.endText();
        y -= 6;
        
        // Row 6: Pickup Date/Time, PO #
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Pickup Date/Time: " + pickupDateTime);
        contentStream.endText();
        
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("PO #: " + load.getPONumber());
            contentStream.endText();
        }
        y -= 6;
        
        // Row 7: Drop Customer, Drop Address
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Drop Customer: " + (load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer()));
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, y);
        contentStream.showText("Drop Address: " + (load.getDropLocation() != null ? load.getDropLocation() : ""));
        contentStream.endText();
        y -= 6;
        
        // Row 8: Drop Date/Time
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Drop Date/Time: " + dropDateTime);
        contentStream.endText();
        y -= 6;
        
        // Add additional locations if any
        List<LoadLocation> additionalPickups = new ArrayList<>();
        List<LoadLocation> additionalDrops = new ArrayList<>();
        if (load.getLocations() != null) {
            additionalPickups = load.getLocations().stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP && loc.getSequence() > 1)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
            
            additionalDrops = load.getLocations().stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP && loc.getSequence() > 1)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        // Add additional pickup locations
        if (!additionalPickups.isEmpty()) {
            contentStream.setFont(boldFont, 8);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX, y);
            contentStream.showText("Additional Pickups:");
            contentStream.endText();
            y -= 6;
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                String info = String.format("%d. %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc));
                
                contentStream.setFont(normalFont, 7);
                contentStream.beginText();
                contentStream.newLineAtOffset(leftX + 10, y);
                contentStream.showText(info);
                contentStream.endText();
                y -= 5;
            }
        }
        
        // Add additional drop locations
        if (!additionalDrops.isEmpty()) {
            contentStream.setFont(boldFont, 8);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Additional Drops:");
            contentStream.endText();
            y -= 6;
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                String info = String.format("%d. %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc));
                
                contentStream.setFont(normalFont, 7);
                contentStream.beginText();
                contentStream.newLineAtOffset(rightX + 10, y);
                contentStream.showText(info);
                contentStream.endText();
                y -= 5;
            }
        }
        
        return y - 5;
    }
    
    private float addHorizontalLine(PDPageContentStream contentStream, float y, float width) throws IOException {
        contentStream.setLineWidth(1f);
        contentStream.moveTo(MARGIN, y);
        contentStream.lineTo(width - MARGIN, y);
        contentStream.stroke();
        return y - 15;
    }
    
    
    private float addPickupInformation(PDPageContentStream contentStream, Load load, float y) throws IOException {
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText("PICKUP INFORMATION");
        contentStream.endText();
        y -= SMALL_LINE_HEIGHT;
        
        contentStream.setFont(normalFont, 10);
        
        // Always show the original pickup location first
        y = addLabelValue(contentStream, "Customer:", load.getCustomer(), MARGIN + 20, y);
        y = addLabelValue(contentStream, "Address:", load.getPickUpLocation(), MARGIN + 20, y);
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        y = addLabelValue(contentStream, "Date/Time:", pickupDateTime, MARGIN + 20, y);
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            y = addLabelValue(contentStream, "PO #:", load.getPONumber(), MARGIN + 20, y);
        }
        
        // Check if we have additional pickup locations from Manage Load Locations
        List<LoadLocation> additionalPickups = new ArrayList<>();
        if (load.getLocations() != null) {
            additionalPickups = load.getLocations().stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP && loc.getSequence() > 1)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        // If we have additional pickups, show them
        if (!additionalPickups.isEmpty()) {
            y -= 10; // Extra spacing before additional locations
            
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + 20, y);
            contentStream.showText("Additional Pickup Locations:");
            contentStream.endText();
            y -= SMALL_LINE_HEIGHT;
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                
                // Format: Customer - Address - Time
                String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                    loc.getCustomer() : "";
                String address = formatLocationAddress(loc);
                String time = loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                
                String locationLine = String.format("%d. %s - %s - %s", 
                    i + 1, customer, address, time);
                
                contentStream.setFont(normalFont, 10);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN + 30, y);
                contentStream.showText(locationLine);
                contentStream.endText();
                y -= LINE_HEIGHT;
                
                // Add notes if present
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    contentStream.setFont(normalFont, 10);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(MARGIN + 40, y);
                    contentStream.showText("Notes: " + loc.getNotes());
                    contentStream.endText();
                    y -= LINE_HEIGHT;
                }
            }
        }
        
        return y - SECTION_SPACING;
    }
    
    private float addDropInformation(PDPageContentStream contentStream, Load load, float y) throws IOException {
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText("DROP INFORMATION");
        contentStream.endText();
        y -= SMALL_LINE_HEIGHT;
        
        contentStream.setFont(normalFont, 10);
        
        // Always show the original drop location first
        y = addLabelValue(contentStream, "Customer:", load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer(), MARGIN + 20, y);
        y = addLabelValue(contentStream, "Address:", load.getDropLocation(), MARGIN + 20, y);
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        y = addLabelValue(contentStream, "Date/Time:", dropDateTime, MARGIN + 20, y);
        
        // Check if we have additional drop locations from Manage Load Locations
        List<LoadLocation> additionalDrops = new ArrayList<>();
        if (load.getLocations() != null) {
            additionalDrops = load.getLocations().stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP && loc.getSequence() > 1)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        // If we have additional drops, show them
        if (!additionalDrops.isEmpty()) {
            y -= 10; // Extra spacing before additional locations
            
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + 20, y);
            contentStream.showText("Additional Drop Locations:");
            contentStream.endText();
            y -= SMALL_LINE_HEIGHT;
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                
                // Format: Customer - Address - Time
                String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                    loc.getCustomer() : "";
                String address = formatLocationAddress(loc);
                String time = loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                
                String locationLine = String.format("%d. %s - %s - %s", 
                    i + 1, customer, address, time);
                
                contentStream.setFont(normalFont, 10);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN + 30, y);
                contentStream.showText(locationLine);
                contentStream.endText();
                y -= LINE_HEIGHT;
                
                // Add notes if present
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    contentStream.setFont(normalFont, 10);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(MARGIN + 40, y);
                    contentStream.showText("Notes: " + loc.getNotes());
                    contentStream.endText();
                    y -= LINE_HEIGHT;
                }
            }
        }
        
        return y - SECTION_SPACING;
    }
    
    private float addNotes(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        contentStream.setFont(boldFont, 9); // Reduced font size
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText("NOTES");
        contentStream.endText();
        y -= 8; // Reduced spacing
        
        contentStream.setFont(boldFont, 8); // Reduced font size
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN + 15, y);
        contentStream.showText(load.getNotes());
        contentStream.endText();
        
        return y - 15; // Reduced spacing
    }
    
    private float addPickupDeliveryPolicy(PDPageContentStream contentStream, float y, float width) throws IOException {
        String policy = config.getPickupDeliveryPolicy();
        if (policy == null || policy.isEmpty()) {
            return y;
        }
        
        // Section header with gray background box
        contentStream.setNonStrokingColor(0.94f, 0.94f, 0.94f);
        contentStream.addRect(MARGIN, y - 12, width - 2 * MARGIN, 14);
        contentStream.fill();
        
        // Black text for section header
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN + 5, y - 10);
        contentStream.showText("PICKUP AND DELIVERY POLICY");
        contentStream.endText();
        y -= LINE_HEIGHT + 5;
        
        // Add double spacing after section header
        y -= LINE_HEIGHT;
        
        // Split policy into lines and add them
        String[] policyLines = policy.split("\n");
        contentStream.setFont(normalFont, 8);
        
        for (String line : policyLines) {
            if (line.trim().isEmpty()) continue;
            
            // Wrap long lines
            List<String> wrappedLines = wrapText(line.trim(), width - 2 * MARGIN - 40, normalFont, 8);
            for (String wrappedLine : wrappedLines) {
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN + 20, y);
                contentStream.showText(wrappedLine);
                contentStream.endText();
                y -= SMALL_LINE_HEIGHT;
            }
        }
        
        return y - SECTION_SPACING;
    }
    
    private void addDispatcherInformation(PDPageContentStream contentStream, float y) throws IOException {
        // Center the dispatcher information
        String title = "DISPATCHER INFORMATION";
        contentStream.setFont(boldFont, 8);
        float titleWidth = boldFont.getStringWidth(title) / 1000 * 8;
        float pageWidth = PDRectangle.LETTER.getWidth();
        
        contentStream.beginText();
        contentStream.newLineAtOffset((pageWidth - titleWidth) / 2, y);
        contentStream.showText(title);
        contentStream.endText();
        
        StringBuilder info = new StringBuilder();
        if (!config.getDispatcherName().isEmpty()) {
            info.append("Name: ").append(config.getDispatcherName());
        }
        if (!config.getDispatcherPhone().isEmpty()) {
            if (info.length() > 0) info.append(" | ");
            info.append("Phone: ").append(config.getDispatcherPhone());
        }
        if (!config.getDispatcherEmail().isEmpty()) {
            if (info.length() > 0) info.append(" | ");
            info.append("Email: ").append(config.getDispatcherEmail());
        }
        if (!config.getDispatcherFax().isEmpty()) {
            if (info.length() > 0) info.append(" | ");
            info.append("Fax: ").append(config.getDispatcherFax());
        }
        
        String infoStr = info.toString();
        contentStream.setFont(normalFont, 7);
        float infoWidth = normalFont.getStringWidth(infoStr) / 1000 * 7;
        
        contentStream.beginText();
        contentStream.newLineAtOffset((pageWidth - infoWidth) / 2, y - 10);
        contentStream.showText(infoStr);
        contentStream.endText();
    }
    
    private float addLabelValue(PDPageContentStream contentStream, String label, String value, float x, float y) throws IOException {
        contentStream.setFont(boldFont, 8); // Reduced font size
        contentStream.beginText();
        contentStream.newLineAtOffset(x + 10, y);
        contentStream.showText(label);
        contentStream.endText();
        
        contentStream.setFont(normalFont, 8); // Reduced font size
        contentStream.beginText();
        contentStream.newLineAtOffset(x + 65, y);
        contentStream.showText(value != null ? value : "");
        contentStream.endText();
        
        return y - 6; // Reduced line height
    }
    
    private String formatDateTime(LocalDate date, LocalTime time) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
        
        String result = "";
        if (date != null) {
            result = date.format(dateFormatter);
            if (time != null) {
                result += " @ " + time.format(timeFormatter);
            }
        } else {
            result = "TBD";
        }
        
        return result;
    }
    
    private List<String> wrapText(String text, float maxWidth, PDType1Font font, int fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            float textWidth = font.getStringWidth(testLine) / 1000 * fontSize;
            
            if (textWidth > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    // Word is too long, force break
                    lines.add(word);
                }
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    private String formatLocationAddress(LoadLocation location) {
        StringBuilder sb = new StringBuilder();
        if (location.getAddress() != null && !location.getAddress().isEmpty()) {
            sb.append(location.getAddress());
        }
        if (location.getCity() != null && !location.getCity().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(location.getCity());
        }
        if (location.getState() != null && !location.getState().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(location.getState());
        }
        return sb.toString();
    }
    
    private float addPickupAndDropSideBySide(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        float leftX = MARGIN;
        float rightX = width / 2 + 10;
        float sectionWidth = (width - 2 * MARGIN - 10) / 2; // 10 is gap between sections
        float height = 792; // Letter page height in points
        
        // Pickup Information (Left Side)
        contentStream.setFont(boldFont, 9); // Reduced font size
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("PICKUP INFORMATION");
        contentStream.endText();
        y -= 8; // Reduced spacing
        
        // Pickup details
        contentStream.setFont(normalFont, 8); // Reduced font size
        y = addLabelValue(contentStream, "Customer:", load.getCustomer(), leftX, y);
        y = addLabelValue(contentStream, "Address:", load.getPickUpLocation(), leftX, y);
        
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        y = addLabelValue(contentStream, "Date/Time:", pickupDateTime, leftX, y);
        
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            y = addLabelValue(contentStream, "PO #:", load.getPONumber(), leftX, y);
        }
        
        // Additional pickup locations
        List<LoadLocation> additionalPickups = new ArrayList<>();
        if (load.getLocations() != null) {
            additionalPickups = load.getLocations().stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP && loc.getSequence() > 1)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        if (!additionalPickups.isEmpty()) {
            y -= 5; // Extra spacing
            contentStream.setFont(boldFont, 7); // Reduced font size
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX, y);
            contentStream.showText("Additional Pickup Locations:");
            contentStream.endText();
            y -= 6; // Reduced spacing
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                    loc.getCustomer() : "";
                String address = formatLocationAddress(loc);
                String time = loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                
                String locationLine = String.format("%d. %s - %s - %s", 
                    i + 1, customer, address, time);
                
                contentStream.setFont(normalFont, 7); // Reduced font size
                contentStream.beginText();
                contentStream.newLineAtOffset(leftX + 10, y);
                contentStream.showText(locationLine);
                contentStream.endText();
                y -= 5; // Reduced spacing
                
                // Add notes if present
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    contentStream.setFont(normalFont, 6); // Reduced font size
                    contentStream.beginText();
                    contentStream.newLineAtOffset(leftX + 15, y);
                    contentStream.showText("Notes: " + loc.getNotes());
                    contentStream.endText();
                    y -= 4; // Reduced spacing
                }
            }
        }
        
        // Reset Y position for drop section
        y = height - MARGIN - 80; // Reset to same level as pickup section
        
        // Drop Information (Right Side)
        contentStream.setFont(boldFont, 9); // Reduced font size
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, y);
        contentStream.showText("DROP INFORMATION");
        contentStream.endText();
        y -= 8; // Reduced spacing
        
        // Drop details
        contentStream.setFont(normalFont, 8); // Reduced font size
        y = addLabelValue(contentStream, "Customer:", load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer(), rightX, y);
        y = addLabelValue(contentStream, "Address:", load.getDropLocation(), rightX, y);
        
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        y = addLabelValue(contentStream, "Date/Time:", dropDateTime, rightX, y);
        
        // Additional drop locations
        List<LoadLocation> additionalDrops = new ArrayList<>();
        if (load.getLocations() != null) {
            additionalDrops = load.getLocations().stream()
                .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP && loc.getSequence() > 1)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        if (!additionalDrops.isEmpty()) {
            y -= 5; // Extra spacing
            contentStream.setFont(boldFont, 7); // Reduced font size
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Additional Drop Locations:");
            contentStream.endText();
            y -= 6; // Reduced spacing
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                    loc.getCustomer() : "";
                String address = formatLocationAddress(loc);
                String time = loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                
                String locationLine = String.format("%d. %s - %s - %s", 
                    i + 1, customer, address, time);
                
                contentStream.setFont(normalFont, 7); // Reduced font size
                contentStream.beginText();
                contentStream.newLineAtOffset(rightX + 10, y);
                contentStream.showText(locationLine);
                contentStream.endText();
                y -= 5; // Reduced spacing
                
                // Add notes if present
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    contentStream.setFont(normalFont, 6); // Reduced font size
                    contentStream.beginText();
                    contentStream.newLineAtOffset(rightX + 15, y);
                    contentStream.showText("Notes: " + loc.getNotes());
                    contentStream.endText();
                    y -= 4; // Reduced spacing
                }
            }
        }
        
        return y - 10; // Reduced spacing
    }
    
    private String getCompanyNameFromConfig() {
        Properties props = new Properties();
        File configFile = new File("payroll_config.properties");
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                String name = props.getProperty("company.name");
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (Exception e) {
                logger.warn("Could not load company name from config: " + e.getMessage());
            }
        }
        
        return "Your Company Name";
    }
}