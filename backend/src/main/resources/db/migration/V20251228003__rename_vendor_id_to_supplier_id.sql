-- Rename vendor_id to supplier_id in voucher_lines for consistency
-- All other tables use supplier_id (journal_entries, purchase_bills, ap_payments, etc.)

-- Drop the old index
DROP INDEX IF EXISTS accounting.idx_voucher_lines_vendor_id;

-- Rename the column
ALTER TABLE accounting.voucher_lines 
    RENAME COLUMN vendor_id TO supplier_id;

-- Create new index with correct name
CREATE INDEX IF NOT EXISTS idx_voucher_lines_supplier_id 
    ON accounting.voucher_lines(supplier_id);

-- Add comment for documentation
COMMENT ON COLUMN accounting.voucher_lines.supplier_id IS 'Reference to suppliers table - supplier associated with this voucher line';
