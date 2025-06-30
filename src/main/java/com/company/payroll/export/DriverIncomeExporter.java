package com.company.payroll.export;

import com.company.payroll.payroll.PayrollCalculator;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporter for driver income data
 */
public class DriverIncomeExporter {
    
    /**
     * Export driver income data to CSV
     */
    public static void exportToCSV(File file, List<PayrollCalculator.PayrollRow> rows) throws IOException {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            
            // Write BOM for Excel UTF-8 recognition
            writer.write('\ufeff');
            
            // Write headers
            writer.write("Date,Load ID,Miles,Gross Pay,Net Pay");
            writer.newLine();
            
            // Write data
            for (PayrollCalculator.PayrollRow row : rows) {
                if (row.date != null) {
                    String line = String.format("%s,%s,%d,%.2f,%.2f",
                        row.date.format(dateFormatter),
                        row.loadId != null ? escapeCSV(row.loadId) : "",
                        row.miles,
                        row.gross,
                        row.netPay
                    );
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }
    
    private static String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}