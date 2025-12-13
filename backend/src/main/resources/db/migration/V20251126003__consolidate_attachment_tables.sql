-- Migration V20251126003: Consolidate attachment tables
-- Merges voucher_attachments, purchase_bill_attachments, sales_invoice_attachments into single attachments table

-- Step 1: Create new unified attachments table
CREATE TABLE IF NOT EXISTS attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    entity_type VARCHAR(50) NOT NULL,  -- 'VOUCHER', 'PURCHASE_BILL', 'SALES_INVOICE'
    entity_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_attachments_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT ck_attachments_entity_type CHECK (entity_type IN ('VOUCHER', 'PURCHASE_BILL', 'SALES_INVOICE'))
);

-- Step 2: Create indexes for the new table
CREATE INDEX IF NOT EXISTS idx_attachments_company_entity 
    ON attachments (company_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_attachments_entity_lookup 
    ON attachments (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_attachments_uploaded_at 
    ON attachments (uploaded_at DESC);

-- Step 3: Migrate data from voucher_attachments
INSERT INTO attachments (id, company_id, entity_type, entity_id, file_name, storage_path, mime_type, file_size, uploaded_at, uploaded_by)
SELECT id, company_id, 'VOUCHER', voucher_id, file_name, storage_path, mime_type, file_size, uploaded_at, uploaded_by
FROM voucher_attachments
ON CONFLICT (id) DO NOTHING;

-- Step 4: Migrate data from purchase_bill_attachments
INSERT INTO attachments (id, company_id, entity_type, entity_id, file_name, storage_path, mime_type, file_size, uploaded_at, uploaded_by)
SELECT id, company_id, 'PURCHASE_BILL', purchase_bill_id, file_name, storage_path, mime_type, file_size, uploaded_at, uploaded_by
FROM purchase_bill_attachments
ON CONFLICT (id) DO NOTHING;

-- Step 5: Migrate data from sales_invoice_attachments
INSERT INTO attachments (id, company_id, entity_type, entity_id, file_name, storage_path, mime_type, file_size, uploaded_at, uploaded_by)
SELECT id, company_id, 'SALES_INVOICE', sales_invoice_id, file_name, storage_path, mime_type, file_size, uploaded_at, uploaded_by
FROM sales_invoice_attachments
ON CONFLICT (id) DO NOTHING;

-- Step 6: Drop old tables (after data migration is verified)
DROP TABLE IF EXISTS voucher_attachments CASCADE;
DROP TABLE IF EXISTS purchase_bill_attachments CASCADE;
DROP TABLE IF EXISTS sales_invoice_attachments CASCADE;

-- Add comment for documentation
COMMENT ON TABLE attachments IS 'Unified attachment storage for all entity types (vouchers, purchase bills, sales invoices). Consolidates former voucher_attachments, purchase_bill_attachments, and sales_invoice_attachments tables.';
COMMENT ON COLUMN attachments.entity_type IS 'Type of parent entity: VOUCHER, PURCHASE_BILL, or SALES_INVOICE';
COMMENT ON COLUMN attachments.entity_id IS 'UUID of the parent entity (voucher_id, purchase_bill_id, or sales_invoice_id)';
