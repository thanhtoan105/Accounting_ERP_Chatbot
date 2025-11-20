-- Add is_sensitive flag to purchase_bills table for manual sensitivity marking
-- This triggers approval workflow regardless of amount threshold

ALTER TABLE purchase_bills
ADD COLUMN is_sensitive BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN purchase_bills.is_sensitive IS 'Flag to manually mark a bill as sensitive, triggering approval workflow regardless of amount threshold';
