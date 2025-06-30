package com.company.payroll.export;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.company.payroll.employees.Employee;
import com.company.payroll.payroll.PayrollCalculator;

/**
 * Utility class for exporting data to various formats
 */
public class ExportUtils {
    
    /**
     * Exports payroll data to PDF
     */
    public static void exportPayrollToPdf(File file, List<PayrollCalculator.PayrollRow> rows, 
                                         Map<String, Employee> employeeMap, LocalDate weekStart) 
                                         throws IOException {
        // Delegate to PDFExporter
        new com.company.payroll.payroll.PDFExporter(getCompanyName(employeeMap))
            .generateBatchPayStubs(file, rows, employeeMap, weekStart);
    }
    
    /**
     * Exports payroll data to Excel
     */
    public static void exportPayrollToExcel(File file, List<PayrollCalculator.PayrollRow> rows, 
                                          Map<String, Employee> employeeMap, 
                                          Map<String, Double> totals, LocalDate weekStart) 
                                          throws IOException {
        // Delegate to ExcelExporter
        new com.company.payroll.payroll.ExcelExporter(getCompanyName(employeeMap))
            .exportPayrollWorkbook(file, rows, employeeMap, totals, weekStart);
    }
    
    private static String getCompanyName(Map<String, Employee> employeeMap) {
        // In a real implementation, this would get the company name from configuration
        return "TRUCKING COMPANY PAYROLL";
    }
}