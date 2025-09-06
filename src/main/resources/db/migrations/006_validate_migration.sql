-- Migration: Validate payment method migration
-- Date: 2024
-- Description: Validate data consistency after payment method migration

-- Create validation results table
CREATE TABLE IF NOT EXISTS migration_validation_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    validation_name TEXT NOT NULL,
    validation_query TEXT,
    result_count INTEGER,
    status TEXT CHECK (status IN ('PASS', 'FAIL', 'WARNING')),
    message TEXT,
    validation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Validation 1: Check all active employees have payment method history
INSERT INTO migration_validation_results (validation_name, validation_query, result_count, status, message)
SELECT 
    'Active employees without payment history' as validation_name,
    'SELECT COUNT(*) FROM employees WHERE active = 1 AND NOT EXISTS (SELECT 1 FROM employee_payment_method_history WHERE employee_id = employees.id)' as validation_query,
    COUNT(*) as result_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END as status,
    CASE WHEN COUNT(*) = 0 
        THEN 'All active employees have payment method history' 
        ELSE 'Found ' || COUNT(*) || ' active employees without payment method history' 
    END as message
FROM employees e
WHERE e.active = 1
AND NOT EXISTS (
    SELECT 1 FROM employee_payment_method_history pmh
    WHERE pmh.employee_id = e.id
);

-- Validation 2: Check for overlapping payment history date ranges
INSERT INTO migration_validation_results (validation_name, validation_query, result_count, status, message)
SELECT 
    'Overlapping payment history date ranges' as validation_name,
    'Complex date overlap query' as validation_query,
    COUNT(*) as result_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END as status,
    CASE WHEN COUNT(*) = 0 
        THEN 'No overlapping payment history date ranges found' 
        ELSE 'Found ' || COUNT(*) || ' overlapping date ranges' 
    END as message
FROM employee_payment_method_history h1
JOIN employee_payment_method_history h2 ON 
    h1.employee_id = h2.employee_id AND 
    h1.id != h2.id
WHERE (
    (h1.effective_date >= h2.effective_date AND 
     (h1.effective_date <= h2.end_date OR h2.end_date IS NULL))
    OR
    (h1.end_date IS NOT NULL AND 
     h1.end_date >= h2.effective_date AND 
     (h1.end_date <= h2.end_date OR h2.end_date IS NULL))
);

-- Validation 3: Check percentage totals equal 100 for PERCENTAGE payment types
INSERT INTO migration_validation_results (validation_name, validation_query, result_count, status, message)
SELECT 
    'Invalid percentage totals' as validation_name,
    'SELECT COUNT(*) FROM employee_payment_method_history WHERE payment_type = "PERCENTAGE" AND ABS((driver_percent + company_percent + service_fee_percent) - 100) > 0.01' as validation_query,
    COUNT(*) as result_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END as status,
    CASE WHEN COUNT(*) = 0 
        THEN 'All percentage payment types have valid totals' 
        ELSE 'Found ' || COUNT(*) || ' percentage configurations with invalid totals' 
    END as message
FROM employee_payment_method_history
WHERE payment_type = 'PERCENTAGE'
AND ABS((driver_percent + company_percent + service_fee_percent) - 100) > 0.01;

-- Validation 4: Check for multiple active payment methods per employee
INSERT INTO migration_validation_results (validation_name, validation_query, result_count, status, message)
SELECT 
    'Multiple active payment methods' as validation_name,
    'SELECT employee_id, COUNT(*) FROM employee_payment_method_history WHERE end_date IS NULL GROUP BY employee_id HAVING COUNT(*) > 1' as validation_query,
    COUNT(DISTINCT employee_id) as result_count,
    CASE WHEN COUNT(DISTINCT employee_id) = 0 THEN 'PASS' ELSE 'FAIL' END as status,
    CASE WHEN COUNT(DISTINCT employee_id) = 0 
        THEN 'No employees have multiple active payment methods' 
        ELSE 'Found ' || COUNT(DISTINCT employee_id) || ' employees with multiple active payment methods' 
    END as message
FROM (
    SELECT employee_id, COUNT(*) as active_count
    FROM employee_payment_method_history
    WHERE end_date IS NULL
    GROUP BY employee_id
    HAVING COUNT(*) > 1
);

-- Validation 5: Check payment_type consistency between employees table and active history
INSERT INTO migration_validation_results (validation_name, validation_query, result_count, status, message)
SELECT 
    'Payment type mismatches' as validation_name,
    'SELECT COUNT(*) FROM payment_method_integrity_check WHERE status = "MISMATCH"' as validation_query,
    COUNT(*) as result_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'WARNING' END as status,
    CASE WHEN COUNT(*) = 0 
        THEN 'All payment types are consistent between employees table and history' 
        ELSE 'Found ' || COUNT(*) || ' payment type mismatches (may need sync)' 
    END as message
FROM payment_method_integrity_check
WHERE status = 'MISMATCH';

-- Validation 6: Check for negative payment values
INSERT INTO migration_validation_results (validation_name, validation_query, result_count, status, message)
SELECT 
    'Negative payment values' as validation_name,
    'SELECT COUNT(*) FROM employee_payment_method_history WHERE driver_percent < 0 OR company_percent < 0 OR service_fee_percent < 0 OR flat_rate_amount < 0 OR per_mile_rate < 0' as validation_query,
    COUNT(*) as result_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END as status,
    CASE WHEN COUNT(*) = 0 
        THEN 'No negative payment values found' 
        ELSE 'Found ' || COUNT(*) || ' records with negative payment values' 
    END as message
FROM employee_payment_method_history
WHERE driver_percent < 0 
   OR company_percent < 0 
   OR service_fee_percent < 0 
   OR flat_rate_amount < 0 
   OR per_mile_rate < 0;

-- Create summary view of validation results
CREATE VIEW IF NOT EXISTS migration_validation_summary AS
SELECT 
    validation_date,
    SUM(CASE WHEN status = 'PASS' THEN 1 ELSE 0 END) as passed,
    SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) as failed,
    SUM(CASE WHEN status = 'WARNING' THEN 1 ELSE 0 END) as warnings,
    COUNT(*) as total_validations
FROM migration_validation_results
GROUP BY validation_date
ORDER BY validation_date DESC;

-- Log migration completion
INSERT INTO schema_migrations (version, description, applied_date) 
VALUES ('006', 'Validate payment method migration', datetime('now'))
ON CONFLICT(version) DO NOTHING;
