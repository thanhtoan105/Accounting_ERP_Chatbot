-- Migration V20251126004: Consolidate VAT corrections tables
-- Merges vat_corrections (AP) and ar_vat_corrections (AR) into single vat_corrections table

-- Step 1: Add document_type column to existing vat_corrections table
ALTER TABLE accounting.vat_corrections 
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(20) DEFAULT 'PURCHASE_BILL',
    ADD COLUMN IF NOT EXISTS document_id UUID,
    ADD COLUMN IF NOT EXISTS line_item_id UUID;

-- Step 2: Update existing records to use new polymorphic columns
UPDATE accounting.vat_corrections 
SET document_type = 'PURCHASE_BILL',
    document_id = purchase_bill_id,
    line_item_id = purchase_bill_line_id
WHERE document_type IS NULL OR document_type = 'PURCHASE_BILL';

-- Step 3: Migrate data from ar_vat_corrections
INSERT INTO accounting.vat_corrections (
    id, company_id, document_type, document_id, line_item_id,
    old_vat_amount, new_vat_amount, reason, status,
    corrected_by_id, corrected_at, approved_by_id, approved_at,
    created_at, updated_at
)
SELECT 
    id, company_id, 'SALES_INVOICE', invoice_id, line_item_id,
    old_vat_amount, new_vat_amount, reason, status,
    corrected_by_id, corrected_at, approved_by_id, approved_at,
    created_at, updated_at
FROM accounting.ar_vat_corrections
ON CONFLICT (id) DO NOTHING;

-- Step 4: Make document_type NOT NULL and add constraint
ALTER TABLE accounting.vat_corrections 
    ALTER COLUMN document_type SET NOT NULL;

ALTER TABLE accounting.vat_corrections
    ADD CONSTRAINT ck_vat_corrections_document_type 
    CHECK (document_type IN ('PURCHASE_BILL', 'SALES_INVOICE'));

-- Step 5: Drop old columns that are now redundant
ALTER TABLE accounting.vat_corrections 
    DROP COLUMN IF EXISTS purchase_bill_id,
    DROP COLUMN IF EXISTS purchase_bill_line_id;

-- Step 6: Drop constraint that references old column name
ALTER TABLE accounting.vat_corrections 
    DROP CONSTRAINT IF EXISTS fk_vat_corrections_bill,
    DROP CONSTRAINT IF EXISTS fk_vat_corrections_bill_line;

-- Step 7: Create indexes for new columns
CREATE INDEX IF NOT EXISTS idx_vat_corrections_document 
    ON accounting.vat_corrections (document_type, document_id);
CREATE INDEX IF NOT EXISTS idx_vat_corrections_company_document 
    ON accounting.vat_corrections (company_id, document_type, document_id);

-- Step 8: Drop old ar_vat_corrections table
DROP TABLE IF EXISTS accounting.ar_vat_corrections CASCADE;

-- Step 9: Add comments for documentation
COMMENT ON TABLE accounting.vat_corrections IS 'Unified VAT corrections for both purchase bills (AP) and sales invoices (AR). Consolidated from former vat_corrections and ar_vat_corrections tables.';
COMMENT ON COLUMN accounting.vat_corrections.document_type IS 'Type of document: PURCHASE_BILL or SALES_INVOICE';
COMMENT ON COLUMN accounting.vat_corrections.document_id IS 'UUID of the parent document (purchase_bill_id or sales_invoice_id)';
