package com.company.payroll.revenue;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.stream.DoubleStream;

public class RevenuePDFExporter {
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_LEFT = 50f;
    private static final float LINE_HEIGHT = 18f;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private final String companyName;
    private final String logoPath;

    public RevenuePDFExporter(String companyName) {
        this(companyName, null);
    }

    public RevenuePDFExporter(String companyName, String logoPath) {
        this.companyName = companyName;
        this.logoPath = logoPath;
    }

    public void exportRevenueSummaryPDF(File file, String reportTitle, String dateRange, List<RevenueTab.RevenueEntry> data) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            final PDPageContentStream[] contentStreamArr = new PDPageContentStream[]{new PDPageContentStream(document, page)};
            float y = page.getMediaBox().getHeight() - MARGIN_TOP;

            // Draw logo if available
            if (logoPath != null && !logoPath.isBlank()) {
                try {
                    PDImageXObject logo = PDImageXObject.createFromFile(logoPath, document);
                    float logoWidth = 80;
                    float logoHeight = 40;
                    contentStreamArr[0].drawImage(logo, MARGIN_LEFT, y - logoHeight, logoWidth, logoHeight);
                    y -= (logoHeight + 10);
                } catch (Exception e) {
                    // Ignore logo errors
                }
            }

            // Company name
            contentStreamArr[0].beginText();
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 22);
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText(companyName);
            contentStreamArr[0].endText();
            y -= 30;

            // Report title
            contentStreamArr[0].beginText();
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText(reportTitle);
            contentStreamArr[0].endText();
            y -= 20;

            // Date range
            contentStreamArr[0].beginText();
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText(dateRange);
            contentStreamArr[0].endText();
            y -= 30;

            // Table header
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
            float[] colWidths = {70, 70, 110, 60, 80, 70};
            String[] headers = {"Invoice #", "Date", "Customer", "Load ID", "Amount", "Status"};
            float x = MARGIN_LEFT;
            for (int i = 0; i < headers.length; i++) {
                contentStreamArr[0].beginText();
                contentStreamArr[0].newLineAtOffset(x, y);
                contentStreamArr[0].showText(headers[i]);
                contentStreamArr[0].endText();
                x += colWidths[i];
            }
            y -= LINE_HEIGHT;

            // Convert float[] to double[] for summing
            double[] colWidthsD = new double[colWidths.length];
            for (int i = 0; i < colWidths.length; i++) colWidthsD[i] = colWidths[i];
            contentStreamArr[0].moveTo(MARGIN_LEFT, y + 6);
            contentStreamArr[0].lineTo((float)(MARGIN_LEFT + DoubleStream.of(colWidthsD).sum()), y + 6);
            contentStreamArr[0].setLineWidth(1f);
            contentStreamArr[0].stroke();

            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            double total = 0.0;
            int totalLoads = 0;
            double outstanding = 0.0;
            List<RevenueTab.RevenueEntry> filtered = new java.util.ArrayList<>();
            for (RevenueTab.RevenueEntry entry : data) {
                // Exclude cancelled loads
                if (entry.getStatus() != null && entry.getStatus().toUpperCase().contains("CANCELLED")) continue;
                filtered.add(entry);
                x = MARGIN_LEFT;
                String[] row = {
                    Objects.toString(entry.getInvoiceNumber(), ""),
                    entry.getDate() != null ? entry.getDate().toString() : "",
                    Objects.toString(entry.getCustomer(), ""),
                    Objects.toString(entry.getLoadId(), ""),
                    CURRENCY_FORMAT.format(entry.getAmount()),
                    Objects.toString(entry.getStatus(), "")
                };
                for (int i = 0; i < row.length; i++) {
                    contentStreamArr[0].beginText();
                    contentStreamArr[0].newLineAtOffset(x, y);
                    // Status coloring
                    if (i == 5) {
                        String status = row[5];
                        if ("Paid".equalsIgnoreCase(status)) {
                            contentStreamArr[0].setNonStrokingColor(0f, 150f/255f, 0f); // Green
                        } else if ("Pending".equalsIgnoreCase(status)) {
                            contentStreamArr[0].setNonStrokingColor(1f, 140f/255f, 0f); // Orange
                        } else if ("Overdue".equalsIgnoreCase(status)) {
                            contentStreamArr[0].setNonStrokingColor(200f/255f, 0f, 0f); // Red
                        } else {
                            contentStreamArr[0].setNonStrokingColor(0f, 0f, 0f); // Black
                        }
                    }
                    contentStreamArr[0].showText(row[i]);
                    contentStreamArr[0].endText();
                    if (i == 5) {
                        contentStreamArr[0].setNonStrokingColor(0f, 0f, 0f); // Reset to black
                    }
                    x += colWidths[i];
                }
                y -= LINE_HEIGHT;
                total += entry.getAmount();
                totalLoads++;
                if (!"Paid".equalsIgnoreCase(entry.getStatus())) {
                    outstanding += entry.getAmount();
                }
                if (y < 100) {
                    contentStreamArr[0].close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStreamArr[0] = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - MARGIN_TOP;
                }
            }

            // Draw total row
            y -= 10;
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText("Total Revenue:");
            contentStreamArr[0].endText();
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], y);
            contentStreamArr[0].showText(CURRENCY_FORMAT.format(total));
            contentStreamArr[0].endText();
            y -= LINE_HEIGHT;

            // Analytics summary
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText("Total Loads: " + totalLoads);
            contentStreamArr[0].endText();
            y -= LINE_HEIGHT;
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText("Avg Revenue/Load: " + (totalLoads > 0 ? CURRENCY_FORMAT.format(total / totalLoads) : "$0.00"));
            contentStreamArr[0].endText();
            y -= LINE_HEIGHT;
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, y);
            contentStreamArr[0].showText("Outstanding: " + CURRENCY_FORMAT.format(outstanding));
            contentStreamArr[0].endText();
            y -= LINE_HEIGHT;
            // Top 5 customers
            java.util.Map<String, Double> revenueByCustomer = new java.util.HashMap<>();
            for (RevenueTab.RevenueEntry entry : filtered) {
                String cust = entry.getCustomer();
                revenueByCustomer.put(cust, revenueByCustomer.getOrDefault(cust, 0.0) + entry.getAmount());
            }
            final float[] yArr = new float[]{y};
            if (!revenueByCustomer.isEmpty()) {
                contentStreamArr[0].beginText();
                contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, yArr[0]);
                contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                contentStreamArr[0].showText("Top Customers:");
                contentStreamArr[0].endText();
                yArr[0] -= LINE_HEIGHT;
                contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                revenueByCustomer.entrySet().stream()
                    .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> {
                        try {
                            contentStreamArr[0].beginText();
                            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, yArr[0]);
                            contentStreamArr[0].showText(e.getKey() + ": " + CURRENCY_FORMAT.format(e.getValue()));
                            contentStreamArr[0].endText();
                            yArr[0] -= LINE_HEIGHT;
                        } catch (IOException ex) {}
                    });
            }
            // Revenue by month
            java.util.Map<String, Double> revenueByMonth = new java.util.HashMap<>();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
            for (RevenueTab.RevenueEntry entry : filtered) {
                if (entry.getDate() != null) {
                    String month = entry.getDate().format(fmt);
                    revenueByMonth.put(month, revenueByMonth.getOrDefault(month, 0.0) + entry.getAmount());
                }
            }
            if (!revenueByMonth.isEmpty()) {
                contentStreamArr[0].beginText();
                contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, yArr[0]);
                contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                contentStreamArr[0].showText("Revenue by Month:");
                contentStreamArr[0].endText();
                yArr[0] -= LINE_HEIGHT;
                contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                revenueByMonth.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> {
                        try {
                            contentStreamArr[0].beginText();
                            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, yArr[0]);
                            contentStreamArr[0].showText(e.getKey() + ": " + CURRENCY_FORMAT.format(e.getValue()));
                            contentStreamArr[0].endText();
                            yArr[0] -= LINE_HEIGHT;
                        } catch (IOException ex) {}
                    });
            }

            // Footer: report date and page number
            float footerY = 40;
            contentStreamArr[0].setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 9);
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT, footerY);
            contentStreamArr[0].showText("Generated: " + java.time.LocalDate.now());
            contentStreamArr[0].endText();
            contentStreamArr[0].beginText();
            contentStreamArr[0].newLineAtOffset(MARGIN_LEFT + 400, footerY);
            contentStreamArr[0].showText("Page 1"); // For now, single page; for multi-page, increment as needed
            contentStreamArr[0].endText();

            contentStreamArr[0].close();
            document.save(file);
        }
    }
} 