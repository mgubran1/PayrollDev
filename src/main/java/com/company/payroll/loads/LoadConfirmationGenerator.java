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
    
    private static final float MARGIN = 40;
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
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            float y = height - MARGIN;
            
            // Add company logo if available
            y = addCompanyLogo(document, contentStream, page, y);
            
            // Add header with Load # and dates, and driver info on right
            y = addHeader(contentStream, load, y, width);
            
            // Add horizontal line
            y = addHorizontalLine(contentStream, y, width);
            
            // Add pickup and drop information side by side
            y = addPickupAndDropSideBySide(contentStream, load, y, width);
            
            // Add notes if present
            if (load.getNotes() != null && !load.getNotes().trim().isEmpty()) {
                y = addNotes(contentStream, load, y, width);
            }
            
            // Add pickup and delivery policy
            y = addPickupDeliveryPolicy(contentStream, y, width);
            
            // Add dispatcher information at the bottom
            addDispatcherInformation(contentStream, 80);
        }
        
        return document;
    }
    
    private float addCompanyLogo(PDDocument document, PDPageContentStream contentStream, PDPage page, float y) {
        String logoPath = config.getCompanyLogoPath();
        if (logoPath != null && !logoPath.isEmpty()) {
            File logoFile = new File(logoPath);
            if (logoFile.exists()) {
                try {
                    PDImageXObject logo = PDImageXObject.createFromFile(logoPath, document);
                    float logoHeight = 60;
                    float logoWidth = logo.getWidth() * (logoHeight / logo.getHeight());
                    
                    // Center the logo
                    float x = (page.getMediaBox().getWidth() - logoWidth) / 2;
                    contentStream.drawImage(logo, x, y - logoHeight, logoWidth, logoHeight);
                    
                    return y - logoHeight - 20;
                } catch (IOException e) {
                    logger.error("Error loading company logo: {}", e.getMessage());
                }
            }
        }
        return y;
    }
    
    private float addHeader(PDPageContentStream contentStream, Load load, float y, float width) throws IOException {
        // Company name
        String companyName = getCompanyNameFromConfig();
        contentStream.setFont(boldFont, 20);
        float companyWidth = boldFont.getStringWidth(companyName) / 1000 * 20;
        contentStream.beginText();
        contentStream.newLineAtOffset((width - companyWidth) / 2, y);
        contentStream.showText(companyName);
        contentStream.endText();
        y -= 25;
        
        // Title
        contentStream.setFont(boldFont, 16);
        String title = "LOAD CONFIRMATION";
        float titleWidth = boldFont.getStringWidth(title) / 1000 * 16;
        contentStream.beginText();
        contentStream.newLineAtOffset((width - titleWidth) / 2, y);
        contentStream.showText(title);
        contentStream.endText();
        y -= 25;
        
        // Left side: Load number and dates
        float leftX = MARGIN;
        float rightX = width / 2 + 20;
        
        // Load number
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("Load #: " + load.getLoadNumber());
        contentStream.endText();
        
        // Driver info on the right
        Employee driver = load.getDriver();
        if (driver != null) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Driver: ");
            contentStream.endText();
            
            contentStream.setFont(normalFont, 10);
            float driverLabelWidth = boldFont.getStringWidth("Driver: ") / 1000 * 10;
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + driverLabelWidth, y);
            contentStream.showText(driver.getName());
            contentStream.endText();
        }
        y -= LINE_HEIGHT;
        
        // Pickup date
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        contentStream.setFont(normalFont, 10);
        String pickupDateStr = load.getPickUpDate() != null ? 
            "Pickup: " + load.getPickUpDate().format(dateFormatter) : "Pickup: TBD";
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText(pickupDateStr);
        contentStream.endText();
        
        // Truck/Unit on the right
        if (driver != null) {
            String truckUnit = load.getTruckUnitSnapshot() != null ? load.getTruckUnitSnapshot() : 
                             (driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A");
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Truck: ");
            contentStream.endText();
            
            contentStream.setFont(normalFont, 10);
            float truckLabelWidth = boldFont.getStringWidth("Truck: ") / 1000 * 10;
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + truckLabelWidth, y);
            contentStream.showText(truckUnit);
            contentStream.endText();
        }
        y -= LINE_HEIGHT;
        
        // Delivery date
        String deliveryDateStr = load.getDeliveryDate() != null ? 
            "Delivery: " + load.getDeliveryDate().format(dateFormatter) : "Delivery: TBD";
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText(deliveryDateStr);
        contentStream.endText();
        
        // Trailer on the right
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
            
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX, y);
            contentStream.showText("Trailer: ");
            contentStream.endText();
            
            contentStream.setFont(normalFont, 10);
            float trailerLabelWidth = boldFont.getStringWidth("Trailer: ") / 1000 * 10;
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + trailerLabelWidth, y);
            contentStream.showText(trailerInfo);
            contentStream.endText();
        }
        y -= LINE_HEIGHT;
        
        // Add driver mobile and email
        if (driver != null) {
            // Mobile number on the left
            if (driver.getPhone() != null && !driver.getPhone().isEmpty()) {
                contentStream.setFont(boldFont, 10);
                contentStream.beginText();
                contentStream.newLineAtOffset(leftX, y);
                contentStream.showText("Mobile #: ");
                contentStream.endText();
                
                contentStream.setFont(normalFont, 10);
                float mobileLabelWidth = boldFont.getStringWidth("Mobile #: ") / 1000 * 10;
                contentStream.beginText();
                contentStream.newLineAtOffset(leftX + mobileLabelWidth, y);
                contentStream.showText(driver.getPhone());
                contentStream.endText();
            }
            
            // Email on the right
            if (driver.getEmail() != null && !driver.getEmail().isEmpty()) {
                contentStream.setFont(boldFont, 10);
                contentStream.beginText();
                contentStream.newLineAtOffset(rightX, y);
                contentStream.showText("Email: ");
                contentStream.endText();
                
                contentStream.setFont(normalFont, 10);
                float emailLabelWidth = boldFont.getStringWidth("Email: ") / 1000 * 10;
                contentStream.beginText();
                contentStream.newLineAtOffset(rightX + emailLabelWidth, y);
                contentStream.showText(driver.getEmail());
                contentStream.endText();
            }
        }
        
        return y - 20;
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
                .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
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
                .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
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
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText("NOTES");
        contentStream.endText();
        y -= 15;
        
        // Set red color for notes
        contentStream.setNonStrokingColor(Color.RED);
        contentStream.setFont(boldFont, 10);
        
        // Wrap and display notes
        List<String> lines = wrapText(load.getNotes(), width - (2 * MARGIN) - 40, boldFont, 10);
        for (String line : lines) {
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN + 20, y);
            contentStream.showText(line);
            contentStream.endText();
            y -= LINE_HEIGHT;
        }
        
        // Reset to black color
        contentStream.setNonStrokingColor(Color.BLACK);
        
        return y - SECTION_SPACING;
    }
    
    private float addPickupDeliveryPolicy(PDPageContentStream contentStream, float y, float width) throws IOException {
        String policy = config.getPickupDeliveryPolicy();
        if (policy != null && !policy.isEmpty()) {
            contentStream.setFont(boldFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN, y);
            contentStream.showText("PICKUP AND DELIVERY POLICY");
            contentStream.endText();
            y -= 12;
            
            contentStream.setFont(normalFont, 8);
            String[] policyLines = policy.split("\n");
            for (String line : policyLines) {
                if (!line.trim().isEmpty()) {
                    List<String> wrappedLines = wrapText(line, width - (2 * MARGIN) - 40, normalFont, 10);
                    for (String wrappedLine : wrappedLines) {
                        contentStream.beginText();
                        contentStream.newLineAtOffset(MARGIN + 20, y);
                        contentStream.showText(wrappedLine);
                        contentStream.endText();
                        y -= 10;
                    }
                }
            }
        }
        
        return y;
    }
    
    private void addDispatcherInformation(PDPageContentStream contentStream, float y) throws IOException {
        contentStream.setFont(boldFont, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText("DISPATCHER INFORMATION");
        contentStream.endText();
        y -= 15;
        
        contentStream.setFont(normalFont, 9);
        
        List<String> dispatcherInfo = new ArrayList<>();
        if (!config.getDispatcherName().isEmpty()) {
            dispatcherInfo.add("Name: " + config.getDispatcherName());
        }
        if (!config.getDispatcherPhone().isEmpty()) {
            dispatcherInfo.add("Phone: " + config.getDispatcherPhone());
        }
        if (!config.getDispatcherEmail().isEmpty()) {
            dispatcherInfo.add("Email: " + config.getDispatcherEmail());
        }
        if (!config.getDispatcherFax().isEmpty()) {
            dispatcherInfo.add("Fax: " + config.getDispatcherFax());
        }
        
        // Display dispatcher info in a single line if it fits, otherwise wrap
        String infoLine = String.join(" | ", dispatcherInfo);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText(infoLine);
        contentStream.endText();
    }
    
    private float addLabelValue(PDPageContentStream contentStream, String label, String value, float x, float y) throws IOException {
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(label);
        contentStream.endText();
        
        contentStream.setFont(normalFont, 10);
        float labelWidth = boldFont.getStringWidth(label) / 1000 * 11;
        contentStream.beginText();
        contentStream.newLineAtOffset(x + labelWidth + 10, y);
        contentStream.showText(value != null ? value : "");
        contentStream.endText();
        
        return y - LINE_HEIGHT;
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
        float columnWidth = (width - 2 * MARGIN - 20) / 2; // 20 for middle spacing
        float leftX = MARGIN;
        float rightX = MARGIN + columnWidth + 20;
        float startY = y;
        
        // PICKUP INFORMATION on the left
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX, y);
        contentStream.showText("PICKUP INFORMATION");
        contentStream.endText();
        y -= SMALL_LINE_HEIGHT;
        
        contentStream.setFont(normalFont, 9);
        float pickupY = y;
        
        // Customer
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 10, pickupY);
        contentStream.showText("Customer:");
        contentStream.endText();
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 65, pickupY);
        contentStream.showText(load.getCustomer() != null ? load.getCustomer() : "");
        contentStream.endText();
        pickupY -= SMALL_LINE_HEIGHT;
        
        // Address
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 10, pickupY);
        contentStream.showText("Address:");
        contentStream.endText();
        contentStream.setFont(normalFont, 9);
        String pickupAddr = load.getPickUpLocation() != null ? load.getPickUpLocation() : "";
        // Wrap address if too long
        List<String> pickupLines = wrapText(pickupAddr, columnWidth - 70, normalFont, 9);
        for (int i = 0; i < pickupLines.size(); i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX + 65, pickupY);
            contentStream.showText(pickupLines.get(i));
            contentStream.endText();
            if (i < pickupLines.size() - 1) {
                pickupY -= SMALL_LINE_HEIGHT;
            }
        }
        pickupY -= SMALL_LINE_HEIGHT;
        
        // Date/Time
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 10, pickupY);
        contentStream.showText("Date/Time:");
        contentStream.endText();
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(leftX + 65, pickupY);
        contentStream.showText(pickupDateTime);
        contentStream.endText();
        pickupY -= SMALL_LINE_HEIGHT;
        
        // PO Number if present
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            contentStream.setFont(boldFont, 9);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX + 10, pickupY);
            contentStream.showText("PO #:");
            contentStream.endText();
            contentStream.setFont(normalFont, 9);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftX + 65, pickupY);
            contentStream.showText(load.getPONumber());
            contentStream.endText();
            pickupY -= SMALL_LINE_HEIGHT;
        }
        
        // DROP INFORMATION on the right
        y = startY;
        contentStream.setFont(boldFont, 11);
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX, y);
        contentStream.showText("DROP INFORMATION");
        contentStream.endText();
        y -= SMALL_LINE_HEIGHT;
        
        contentStream.setFont(normalFont, 9);
        float dropY = y;
        
        // Customer
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX + 10, dropY);
        contentStream.showText("Customer:");
        contentStream.endText();
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX + 65, dropY);
        contentStream.showText(load.getCustomer2() != null ? load.getCustomer2() : (load.getCustomer() != null ? load.getCustomer() : ""));
        contentStream.endText();
        dropY -= SMALL_LINE_HEIGHT;
        
        // Address
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX + 10, dropY);
        contentStream.showText("Address:");
        contentStream.endText();
        contentStream.setFont(normalFont, 9);
        String dropAddr = load.getDropLocation() != null ? load.getDropLocation() : "";
        // Wrap address if too long
        List<String> dropLines = wrapText(dropAddr, columnWidth - 70, normalFont, 9);
        for (int i = 0; i < dropLines.size(); i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(rightX + 65, dropY);
            contentStream.showText(dropLines.get(i));
            contentStream.endText();
            if (i < dropLines.size() - 1) {
                dropY -= SMALL_LINE_HEIGHT;
            }
        }
        dropY -= SMALL_LINE_HEIGHT;
        
        // Date/Time
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        contentStream.setFont(boldFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX + 10, dropY);
        contentStream.showText("Date/Time:");
        contentStream.endText();
        contentStream.setFont(normalFont, 9);
        contentStream.beginText();
        contentStream.newLineAtOffset(rightX + 65, dropY);
        contentStream.showText(dropDateTime);
        contentStream.endText();
        dropY -= SMALL_LINE_HEIGHT;
        
        // Check for additional locations
        List<LoadLocation> additionalPickups = load.getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
            
        List<LoadLocation> additionalDrops = load.getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
        
        float lowestY = Math.min(pickupY, dropY);
        
        // Add additional locations if any
        if (!additionalPickups.isEmpty() || !additionalDrops.isEmpty()) {
            lowestY -= 10; // Small spacing before additional locations
            
            // Additional pickup locations on the left
            if (!additionalPickups.isEmpty()) {
                contentStream.setFont(boldFont, 9);
                contentStream.beginText();
                contentStream.newLineAtOffset(leftX + 10, lowestY);
                contentStream.showText("Additional Pickup Locations:");
                contentStream.endText();
                lowestY -= SMALL_LINE_HEIGHT;
                
                for (int i = 0; i < additionalPickups.size(); i++) {
                    LoadLocation loc = additionalPickups.get(i);
                    
                    // Format location info
                    String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                        loc.getCustomer() : "";
                    String address = formatLocationAddress(loc);
                    String time = loc.getTime() != null ? 
                        loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                    
                    String locationLine = String.format("%d. %s - %s - %s", 
                        i + 1, customer, address, time);
                    
                    // Wrap text if too long
                    List<String> lines = wrapText(locationLine, columnWidth - 20, normalFont, 8);
                    contentStream.setFont(normalFont, 8);
                    for (String line : lines) {
                        contentStream.beginText();
                        contentStream.newLineAtOffset(leftX + 20, lowestY);
                        contentStream.showText(line);
                        contentStream.endText();
                        lowestY -= SMALL_LINE_HEIGHT;
                    }
                    
                    // Add notes if present
                    if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                        contentStream.setFont(normalFont, 7);
                        List<String> noteLines = wrapText("Notes: " + loc.getNotes(), 
                            columnWidth - 30, normalFont, 7);
                        for (String noteLine : noteLines) {
                            contentStream.beginText();
                            contentStream.newLineAtOffset(leftX + 30, lowestY);
                            contentStream.showText(noteLine);
                            contentStream.endText();
                            lowestY -= SMALL_LINE_HEIGHT;
                        }
                    }
                }
            }
            
            // Additional drop locations on the right
            float dropAdditionalY = Math.min(pickupY, dropY) - 10;
            if (!additionalDrops.isEmpty()) {
                contentStream.setFont(boldFont, 9);
                contentStream.beginText();
                contentStream.newLineAtOffset(rightX + 10, dropAdditionalY);
                contentStream.showText("Additional Drop Locations:");
                contentStream.endText();
                dropAdditionalY -= SMALL_LINE_HEIGHT;
                
                for (int i = 0; i < additionalDrops.size(); i++) {
                    LoadLocation loc = additionalDrops.get(i);
                    
                    // Format location info
                    String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                        loc.getCustomer() : "";
                    String address = formatLocationAddress(loc);
                    String time = loc.getTime() != null ? 
                        loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                    
                    String locationLine = String.format("%d. %s - %s - %s", 
                        i + 1, customer, address, time);
                    
                    // Wrap text if too long
                    List<String> lines = wrapText(locationLine, columnWidth - 20, normalFont, 8);
                    contentStream.setFont(normalFont, 8);
                    for (String line : lines) {
                        contentStream.beginText();
                        contentStream.newLineAtOffset(rightX + 20, dropAdditionalY);
                        contentStream.showText(line);
                        contentStream.endText();
                        dropAdditionalY -= SMALL_LINE_HEIGHT;
                    }
                    
                    // Add notes if present
                    if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                        contentStream.setFont(normalFont, 7);
                        List<String> noteLines = wrapText("Notes: " + loc.getNotes(), 
                            columnWidth - 30, normalFont, 7);
                        for (String noteLine : noteLines) {
                            contentStream.beginText();
                            contentStream.newLineAtOffset(rightX + 30, dropAdditionalY);
                            contentStream.showText(noteLine);
                            contentStream.endText();
                            dropAdditionalY -= SMALL_LINE_HEIGHT;
                        }
                    }
                }
                
                // Update lowestY to be the minimum of both columns
                lowestY = Math.min(lowestY, dropAdditionalY);
            }
        }
        
        // Return the lower y position
        return lowestY - SECTION_SPACING;
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