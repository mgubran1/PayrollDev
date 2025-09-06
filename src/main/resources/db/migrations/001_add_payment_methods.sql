-- Migration: Add payment method support to employees table
-- Date: 2024
-- Description: Add payment method tracking with backward compatibility

-- Add payment method fields to employees table
ALTER TABLE employees ADD COLUMN payment_type TEXT DEFAULT 'PERCENTAGE' 
    CHECK (payment_type IN ('PERCENTAGE', 'FLAT_RATE', 'PER_MILE'));

ALTER TABLE employees ADD COLUMN flat_rate_amount REAL DEFAULT 0.0;

ALTER TABLE employees ADD COLUMN per_mile_rate REAL DEFAULT 0.0;

ALTER TABLE employees ADD COLUMN payment_effective_date DATE DEFAULT CURRENT_DATE;

ALTER TABLE employees ADD COLUMN payment_notes TEXT;

-- Create indexes for performance
CREATE INDEX idx_employees_payment_type ON employees(payment_type);
CREATE INDEX idx_employees_payment_effective_date ON employees(payment_effective_date);

-- Update existing employees to ensure they have payment_type set
UPDATE employees SET payment_type = 'PERCENTAGE' WHERE payment_type IS NULL;

-- Add validation constraints
-- Ensure flat_rate_amount is non-negative
UPDATE employees SET flat_rate_amount = 0.0 WHERE flat_rate_amount < 0;

-- Ensure per_mile_rate is non-negative
UPDATE employees SET per_mile_rate = 0.0 WHERE per_mile_rate < 0;

-- Log migration completion
INSERT INTO schema_migrations (version, description, applied_date) 
VALUES ('001', 'Add payment method support to employees table', datetime('now'))
ON CONFLICT(version) DO NOTHING;
