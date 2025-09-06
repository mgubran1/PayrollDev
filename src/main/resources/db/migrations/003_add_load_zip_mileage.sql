-- Migration: Add zip code and mileage support to loads table
-- Date: 2024
-- Description: Add zip code tracking and mileage calculation support for per-mile payments

-- Add zip code and mileage fields to loads table
ALTER TABLE loads ADD COLUMN pickup_zip_code TEXT;

ALTER TABLE loads ADD COLUMN delivery_zip_code TEXT;

ALTER TABLE loads ADD COLUMN calculated_miles REAL DEFAULT 0.0;

ALTER TABLE loads ADD COLUMN miles_calculation_date TIMESTAMP;

ALTER TABLE loads ADD COLUMN payment_method_used TEXT
    CHECK (payment_method_used IN ('PERCENTAGE', 'FLAT_RATE', 'PER_MILE', NULL));

ALTER TABLE loads ADD COLUMN calculated_driver_pay REAL DEFAULT 0.0;

ALTER TABLE loads ADD COLUMN payment_rate_used REAL DEFAULT 0.0;

-- Create indexes for performance
CREATE INDEX idx_loads_pickup_zip_code ON loads(pickup_zip_code);
CREATE INDEX idx_loads_delivery_zip_code ON loads(delivery_zip_code);
CREATE INDEX idx_loads_payment_method_used ON loads(payment_method_used);

-- Create composite indexes for mileage queries
CREATE INDEX idx_loads_mileage_calculation 
    ON loads(pickup_zip_code, delivery_zip_code, calculated_miles);

CREATE INDEX idx_loads_payment_calculation 
    ON loads(payment_method_used, payment_rate_used, calculated_driver_pay);

-- Add validation trigger for zip code format on pickup
CREATE TRIGGER validate_pickup_zip_format
BEFORE INSERT ON loads
FOR EACH ROW
WHEN NEW.pickup_zip_code IS NOT NULL AND 
     NEW.pickup_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]' AND 
     NEW.pickup_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'
BEGIN
    SELECT RAISE(ABORT, 'Invalid pickup zip code format. Must be 5 digits or 5+4 format');
END;

-- Add validation trigger for zip code format on delivery
CREATE TRIGGER validate_delivery_zip_format
BEFORE INSERT ON loads
FOR EACH ROW
WHEN NEW.delivery_zip_code IS NOT NULL AND 
     NEW.delivery_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]' AND 
     NEW.delivery_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'
BEGIN
    SELECT RAISE(ABORT, 'Invalid delivery zip code format. Must be 5 digits or 5+4 format');
END;

-- Add update triggers for zip code validation
CREATE TRIGGER validate_pickup_zip_format_update
BEFORE UPDATE ON loads
FOR EACH ROW
WHEN NEW.pickup_zip_code IS NOT NULL AND 
     NEW.pickup_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]' AND 
     NEW.pickup_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'
BEGIN
    SELECT RAISE(ABORT, 'Invalid pickup zip code format. Must be 5 digits or 5+4 format');
END;

CREATE TRIGGER validate_delivery_zip_format_update
BEFORE UPDATE ON loads
FOR EACH ROW
WHEN NEW.delivery_zip_code IS NOT NULL AND 
     NEW.delivery_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]' AND 
     NEW.delivery_zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'
BEGIN
    SELECT RAISE(ABORT, 'Invalid delivery zip code format. Must be 5 digits or 5+4 format');
END;

-- Ensure calculated values are non-negative
CREATE TRIGGER validate_calculated_values
BEFORE INSERT ON loads
FOR EACH ROW
WHEN NEW.calculated_miles < 0 OR NEW.calculated_driver_pay < 0 OR NEW.payment_rate_used < 0
BEGIN
    SELECT RAISE(ABORT, 'Calculated values must be non-negative');
END;

CREATE TRIGGER validate_calculated_values_update
BEFORE UPDATE ON loads
FOR EACH ROW
WHEN NEW.calculated_miles < 0 OR NEW.calculated_driver_pay < 0 OR NEW.payment_rate_used < 0
BEGIN
    SELECT RAISE(ABORT, 'Calculated values must be non-negative');
END;

-- Log migration completion
INSERT INTO schema_migrations (version, description, applied_date) 
VALUES ('003', 'Add zip code and mileage support to loads table', datetime('now'))
ON CONFLICT(version) DO NOTHING;
