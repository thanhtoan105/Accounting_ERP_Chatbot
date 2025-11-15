-- Add is_locked column to vouchers table
-- This column is set to true when the period containing the voucher is closed
-- Locked vouchers cannot be edited to maintain period integrity

ALTER TABLE vouchers
    ADD COLUMN IF NOT EXISTS is_locked BOOLEAN NOT NULL DEFAULT false;

-- Create index for efficient queries on locked vouchers
CREATE INDEX IF NOT EXISTS idx_vouchers_is_locked ON vouchers(is_locked);

-- Add comment for documentation
COMMENT ON COLUMN vouchers.is_locked IS 'Lock flag set when period is closed. Prevents edits to maintain period integrity.';

