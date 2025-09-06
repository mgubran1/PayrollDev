-- Migration: Create payment method history table
-- Date: 2024
-- Description: Comprehensive payment method history tracking similar to percentage history

-- Create payment method history table
CREATE TABLE IF NOT EXISTS employee_payment_method_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id INTEGER NOT NULL,
    payment_type TEXT NOT NULL CHECK (payment_type IN ('PERCENTAGE', 'FLAT_RATE', 'PER_MILE')),
    
    -- Percentage payment fields
    driver_percent REAL DEFAULT 0.0,
    company_percent REAL DEFAULT 0.0,
    service_fee_percent REAL DEFAULT 0.0,
    
    -- Flat rate payment fields
    flat_rate_amount REAL DEFAULT 0.0,
    
    -- Per mile payment fields
    per_mile_rate REAL DEFAULT 0.0,
    
    -- Date range fields
    effective_date DATE NOT NULL,
    end_date DATE,
    
    -- Audit fields
    created_by TEXT DEFAULT 'SYSTEM',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    
    -- Foreign key constraint
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    
    -- Ensure end_date is after effective_date
    CHECK (end_date IS NULL OR end_date >= effective_date),
    
    -- Ensure percentage values are between 0 and 100
    CHECK (driver_percent >= 0 AND driver_percent <= 100),
    CHECK (company_percent >= 0 AND company_percent <= 100),
    CHECK (service_fee_percent >= 0 AND service_fee_percent <= 100),
    
    -- Ensure flat rate and per mile rates are non-negative
    CHECK (flat_rate_amount >= 0),
    CHECK (per_mile_rate >= 0)
);

-- Create indexes for performance
CREATE INDEX idx_payment_history_employee_id ON employee_payment_method_history(employee_id);
CREATE INDEX idx_payment_history_effective_date ON employee_payment_method_history(effective_date);
CREATE INDEX idx_payment_history_end_date ON employee_payment_method_history(end_date);
CREATE INDEX idx_payment_history_payment_type ON employee_payment_method_history(payment_type);

-- Create composite indexes for common queries
CREATE INDEX idx_payment_history_employee_dates 
    ON employee_payment_method_history(employee_id, effective_date, end_date);

CREATE INDEX idx_payment_history_active 
    ON employee_payment_method_history(employee_id, effective_date) 
    WHERE end_date IS NULL;

-- Create trigger to update modified_date on changes
CREATE TRIGGER update_payment_history_modified_date
AFTER UPDATE ON employee_payment_method_history
FOR EACH ROW
BEGIN
    UPDATE employee_payment_method_history 
    SET modified_date = CURRENT_TIMESTAMP 
    WHERE id = NEW.id;
END;

-- Create trigger to prevent overlapping date ranges for same employee
CREATE TRIGGER prevent_overlapping_payment_history
BEFORE INSERT ON employee_payment_method_history
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN EXISTS (
            SELECT 1 FROM employee_payment_method_history
            WHERE employee_id = NEW.employee_id
            AND id != NEW.id
            AND (
                (NEW.effective_date >= effective_date AND 
                 (NEW.effective_date <= end_date OR end_date IS NULL))
                OR
                (NEW.end_date IS NOT NULL AND 
                 NEW.end_date >= effective_date AND 
                 (NEW.end_date <= end_date OR end_date IS NULL))
                OR
                (NEW.effective_date <= effective_date AND 
                 (NEW.end_date >= end_date OR NEW.end_date IS NULL OR end_date IS NULL))
            )
        )
        THEN RAISE(ABORT, 'Payment history date ranges cannot overlap for the same employee')
    END;
END;

-- Create trigger for update to prevent overlapping date ranges
CREATE TRIGGER prevent_overlapping_payment_history_update
BEFORE UPDATE ON employee_payment_method_history
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN EXISTS (
            SELECT 1 FROM employee_payment_method_history
            WHERE employee_id = NEW.employee_id
            AND id != NEW.id
            AND (
                (NEW.effective_date >= effective_date AND 
                 (NEW.effective_date <= end_date OR end_date IS NULL))
                OR
                (NEW.end_date IS NOT NULL AND 
                 NEW.end_date >= effective_date AND 
                 (NEW.end_date <= end_date OR end_date IS NULL))
                OR
                (NEW.effective_date <= effective_date AND 
                 (NEW.end_date >= end_date OR NEW.end_date IS NULL OR end_date IS NULL))
            )
        )
        THEN RAISE(ABORT, 'Payment history date ranges cannot overlap for the same employee')
    END;
END;

-- Create trigger to validate percentage totals for PERCENTAGE payment type
CREATE TRIGGER validate_percentage_totals
BEFORE INSERT ON employee_payment_method_history
FOR EACH ROW
WHEN NEW.payment_type = 'PERCENTAGE' AND 
     (NEW.driver_percent + NEW.company_percent + NEW.service_fee_percent) != 100
BEGIN
    SELECT RAISE(ABORT, 'For PERCENTAGE payment type, percentages must sum to 100%');
END;

CREATE TRIGGER validate_percentage_totals_update
BEFORE UPDATE ON employee_payment_method_history
FOR EACH ROW
WHEN NEW.payment_type = 'PERCENTAGE' AND 
     (NEW.driver_percent + NEW.company_percent + NEW.service_fee_percent) != 100
BEGIN
    SELECT RAISE(ABORT, 'For PERCENTAGE payment type, percentages must sum to 100%');
END;

-- Log migration completion
INSERT INTO schema_migrations (version, description, applied_date) 
VALUES ('004', 'Create payment method history table', datetime('now'))
ON CONFLICT(version) DO NOTHING;
