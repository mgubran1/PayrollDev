-- Migration: Add zip code support to customer address book
-- Date: 2024
-- Description: Add zip codes and geocoding support for distance calculations

-- Add zip code and geocoding fields to customer_addresses table
ALTER TABLE customer_addresses ADD COLUMN zip_code TEXT;

ALTER TABLE customer_addresses ADD COLUMN latitude REAL DEFAULT 0.0;

ALTER TABLE customer_addresses ADD COLUMN longitude REAL DEFAULT 0.0;

ALTER TABLE customer_addresses ADD COLUMN geocoded_date TIMESTAMP;

ALTER TABLE customer_addresses ADD COLUMN geocoding_status TEXT DEFAULT 'PENDING'
    CHECK (geocoding_status IN ('PENDING', 'GEOCODED', 'FAILED', 'MANUAL', 'ESTIMATED'));

-- Create indexes for performance
CREATE INDEX idx_customer_addresses_zip_code ON customer_addresses(zip_code);
CREATE INDEX idx_customer_addresses_geocoding_status ON customer_addresses(geocoding_status);

-- Create composite index for geocoding queries
CREATE INDEX idx_customer_addresses_geocoding 
    ON customer_addresses(geocoding_status, geocoded_date);

-- Add validation trigger for zip code format (5 or 5+4 digits)
CREATE TRIGGER validate_zip_code_format
BEFORE INSERT ON customer_addresses
FOR EACH ROW
WHEN NEW.zip_code IS NOT NULL AND 
     NEW.zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]' AND 
     NEW.zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'
BEGIN
    SELECT RAISE(ABORT, 'Invalid zip code format. Must be 5 digits or 5+4 format');
END;

-- Add update trigger for zip code format validation
CREATE TRIGGER validate_zip_code_format_update
BEFORE UPDATE ON customer_addresses
FOR EACH ROW
WHEN NEW.zip_code IS NOT NULL AND 
     NEW.zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]' AND 
     NEW.zip_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'
BEGIN
    SELECT RAISE(ABORT, 'Invalid zip code format. Must be 5 digits or 5+4 format');
END;

-- Log migration completion
INSERT INTO schema_migrations (version, description, applied_date) 
VALUES ('002', 'Add zip code support to customer address book', datetime('now'))
ON CONFLICT(version) DO NOTHING;
