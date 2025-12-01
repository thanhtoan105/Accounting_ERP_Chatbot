-- Migration V20251126005: Consolidate allocation tables
-- Merges payment_allocations (AP) and receipt_allocations (AR) into single transaction_allocations table

-- Step 1: Create new unified transaction_allocations table
CREATE TABLE IF NOT EXISTS accounting.transaction_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,  -- 'PAYMENT' or 'RECEIPT'
    transaction_id UUID NOT NULL,           -- payment_id or receipt_id
    document_type VARCHAR(20) NOT NULL,     -- 'PURCHASE_BILL' or 'SALES_INVOICE'
    document_id UUID NOT NULL,              -- purchase_bill_id or sales_invoice_id
    allocated_amount NUMERIC(19, 2) NOT NULL,
    allocation_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_transaction_allocations_company FOREIGN KEY (company_id) REFERENCES accounting.companies(id),
    CONSTRAINT ck_transaction_allocations_type CHECK (transaction_type IN ('PAYMENT', 'RECEIPT')),
    CONSTRAINT ck_transaction_allocations_doc_type CHECK (document_type IN ('PURCHASE_BILL', 'SALES_INVOICE')),
    CONSTRAINT ck_transaction_allocations_amount_positive CHECK (allocated_amount > 0)
);

-- Step 2: Create indexes
CREATE INDEX IF NOT EXISTS idx_transaction_allocations_company_transaction 
    ON accounting.transaction_allocations (company_id, transaction_type, transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_allocations_document 
    ON accounting.transaction_allocations (document_type, document_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_transaction_allocations_transaction_document 
    ON accounting.transaction_allocations (transaction_id, document_id);

-- Step 3: Migrate data from payment_allocations (AP payments to purchase bills)
INSERT INTO accounting.transaction_allocations (
    id, company_id, transaction_type, transaction_id, document_type, document_id, 
    allocated_amount, allocation_order, created_at
)
SELECT 
    id, company_id, 'PAYMENT', payment_id, 'PURCHASE_BILL', purchase_bill_id,
    allocated_amount, allocation_order, created_at
FROM accounting.payment_allocations
ON CONFLICT (id) DO NOTHING;

-- Step 4: Migrate data from receipt_allocations (AR receipts to sales invoices)
INSERT INTO accounting.transaction_allocations (
    id, company_id, transaction_type, transaction_id, document_type, document_id, 
    allocated_amount, allocation_order, created_at
)
SELECT 
    id, company_id, 'RECEIPT', receipt_id, 'SALES_INVOICE', sales_invoice_id,
    allocated_amount, allocation_order, created_at
FROM accounting.receipt_allocations
ON CONFLICT (id) DO NOTHING;

-- Step 5: Drop old tables
DROP TABLE IF EXISTS accounting.payment_allocations CASCADE;
DROP TABLE IF EXISTS accounting.receipt_allocations CASCADE;

-- Step 6: Add comments
COMMENT ON TABLE accounting.transaction_allocations IS 'Unified allocation table for both AP payments and AR receipts. Links transactions to documents with allocated amounts.';
COMMENT ON COLUMN accounting.transaction_allocations.transaction_type IS 'Type of transaction: PAYMENT (AP) or RECEIPT (AR)';
COMMENT ON COLUMN accounting.transaction_allocations.transaction_id IS 'UUID of the payment or receipt';
COMMENT ON COLUMN accounting.transaction_allocations.document_type IS 'Type of document: PURCHASE_BILL or SALES_INVOICE';
COMMENT ON COLUMN accounting.transaction_allocations.document_id IS 'UUID of the purchase bill or sales invoice';
