-- Migration: Migrate existing payment data to new payment method history
-- Date: 2024
-- Description: Migrate existing percentage data and ensure data consistency

-- First, ensure schema_migrations table exists
CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    description TEXT,
    applied_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migrate existing percentage history to payment method history
INSERT INTO employee_payment_method_history (
    employee_id,
    payment_type,
    driver_percent,
    company_percent,
    service_fee_percent,
    flat_rate_amount,
    per_mile_rate,
    effective_date,
    end_date,
    created_by,
    created_date,
    notes
)
SELECT 
    employee_id,
    'PERCENTAGE' as payment_type,
    driver_percentage,
    company_percentage,
    service_fee_percentage,
    0.0 as flat_rate_amount,
    0.0 as per_mile_rate,
    effective_date,
    end_date,
    'MIGRATION' as created_by,
    CURRENT_TIMESTAMP as created_date,
    'Migrated from employee_percentage_history table' as notes
FROM employee_percentage_history
WHERE NOT EXISTS (
    SELECT 1 FROM employee_payment_method_history pmh
    WHERE pmh.employee_id = employee_percentage_history.employee_id
    AND pmh.effective_date = employee_percentage_history.effective_date
);

-- For employees without any history, create initial payment method history from current values
INSERT INTO employee_payment_method_history (
    employee_id,
    payment_type,
    driver_percent,
    company_percent,
    service_fee_percent,
    flat_rate_amount,
    per_mile_rate,
    effective_date,
    end_date,
    created_by,
    created_date,
    notes
)
SELECT 
    id as employee_id,
    COALESCE(payment_type, 'PERCENTAGE') as payment_type,
    COALESCE(driver_percentage, 0.0) as driver_percent,
    COALESCE(company_percentage, 0.0) as company_percent,
    COALESCE(service_fee_percentage, 0.0) as service_fee_percent,
    COALESCE(flat_rate_amount, 0.0) as flat_rate_amount,
    COALESCE(per_mile_rate, 0.0) as per_mile_rate,
    COALESCE(payment_effective_date, date('2024-01-01')) as effective_date,
    NULL as end_date,
    'MIGRATION' as created_by,
    CURRENT_TIMESTAMP as created_date,
    'Initial payment method configuration from employees table' as notes
FROM employees
WHERE active = 1
AND NOT EXISTS (
    SELECT 1 FROM employee_payment_method_history pmh
    WHERE pmh.employee_id = employees.id
    AND pmh.end_date IS NULL
);

-- Update employees table payment_type for consistency
UPDATE employees 
SET payment_type = 'PERCENTAGE' 
WHERE payment_type IS NULL;

-- Ensure all active employees have at least one payment method history entry
INSERT INTO employee_payment_method_history (
    employee_id,
    payment_type,
    driver_percent,
    company_percent,
    service_fee_percent,
    flat_rate_amount,
    per_mile_rate,
    effective_date,
    end_date,
    created_by,
    created_date,
    notes
)
SELECT 
    e.id as employee_id,
    'PERCENTAGE' as payment_type,
    0.0 as driver_percent,
    0.0 as company_percent,
    0.0 as service_fee_percent,
    0.0 as flat_rate_amount,
    0.0 as per_mile_rate,
    date('2024-01-01') as effective_date,
    NULL as end_date,
    'MIGRATION' as created_by,
    CURRENT_TIMESTAMP as created_date,
    'Default payment method for employee without history' as notes
FROM employees e
WHERE e.active = 1
AND NOT EXISTS (
    SELECT 1 FROM employee_payment_method_history pmh
    WHERE pmh.employee_id = e.id
);

-- Create data integrity check view
CREATE VIEW IF NOT EXISTS payment_method_integrity_check AS
SELECT 
    e.id as employee_id,
    e.first_name || ' ' || e.last_name as employee_name,
    e.payment_type as current_payment_type,
    pmh.payment_type as history_payment_type,
    pmh.effective_date,
    pmh.end_date,
    CASE 
        WHEN pmh.id IS NULL THEN 'NO_HISTORY'
        WHEN pmh.end_date IS NOT NULL THEN 'INACTIVE'
        WHEN e.payment_type != pmh.payment_type THEN 'MISMATCH'
        ELSE 'OK'
    END as status
FROM employees e
LEFT JOIN employee_payment_method_history pmh ON 
    e.id = pmh.employee_id AND pmh.end_date IS NULL
WHERE e.active = 1;

-- Log migration completion
INSERT INTO schema_migrations (version, description, applied_date) 
VALUES ('005', 'Migrate existing payment data to payment method history', datetime('now'))
ON CONFLICT(version) DO NOTHING;
