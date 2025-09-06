package com.company.payroll.employees;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents an audit log entry for percentage configuration changes
 */
public class PercentageAuditLog {
    private int id;
    private int employeeId;
    private String employeeName;
    private String action; // CREATE, UPDATE, DELETE, BULK_UPDATE
    private String fieldChanged; // driver_percent, company_percent, service_fee_percent, all
    private double oldValue;
    private double newValue;
    private LocalDateTime timestamp;
    private String performedBy;
    private String notes;
    private String sessionId; // To group related changes
    
    public PercentageAuditLog() {
        this.timestamp = LocalDateTime.now();
    }
    
    public PercentageAuditLog(int employeeId, String employeeName, String action, 
                              String fieldChanged, double oldValue, double newValue,
                              String performedBy, String notes, String sessionId) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.action = action;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = LocalDateTime.now();
        this.performedBy = performedBy;
        this.notes = notes;
        this.sessionId = sessionId;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getEmployeeId() { return employeeId; }
    public void setEmployeeId(int employeeId) { this.employeeId = employeeId; }
    
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getFieldChanged() { return fieldChanged; }
    public void setFieldChanged(String fieldChanged) { this.fieldChanged = fieldChanged; }
    
    public double getOldValue() { return oldValue; }
    public void setOldValue(double oldValue) { this.oldValue = oldValue; }
    
    public double getNewValue() { return newValue; }
    public void setNewValue(double newValue) { this.newValue = newValue; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format("[%s] %s: %s changed %s from %.2f%% to %.2f%% for %s (Employee #%d)",
            timestamp.format(formatter),
            performedBy,
            action,
            fieldChanged,
            oldValue,
            newValue,
            employeeName,
            employeeId
        );
    }
    
    public String toDetailedString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("=== PERCENTAGE AUDIT LOG ===\n");
        sb.append("Timestamp: ").append(timestamp.format(formatter)).append("\n");
        sb.append("Session ID: ").append(sessionId).append("\n");
        sb.append("Performed By: ").append(performedBy).append("\n");
        sb.append("Action: ").append(action).append("\n");
        sb.append("Employee: ").append(employeeName).append(" (ID: ").append(employeeId).append(")\n");
        sb.append("Field: ").append(fieldChanged).append("\n");
        sb.append("Old Value: ").append(String.format("%.2f%%", oldValue)).append("\n");
        sb.append("New Value: ").append(String.format("%.2f%%", newValue)).append("\n");
        if (notes != null && !notes.isEmpty()) {
            sb.append("Notes: ").append(notes).append("\n");
        }
        sb.append("============================");
        return sb.toString();
    }
}

