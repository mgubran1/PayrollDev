-- Add flat rate amount to loads table for enterprise-ready flat rate handling
-- Each load can have its own flat rate amount

ALTER TABLE loads ADD COLUMN flat_rate_amount DOUBLE DEFAULT 0.0;

-- Update existing loads with flat rate payment method to use the driver's default flat rate
UPDATE loads l
SET flat_rate_amount = (
    SELECT e.flat_rate_amount 
    FROM employees e 
    WHERE e.id = l.driver_id
)
WHERE l.payment_method_used = 'FLAT_RATE' 
  AND l.flat_rate_amount = 0.0;

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_loads_flat_rate ON loads(flat_rate_amount);
