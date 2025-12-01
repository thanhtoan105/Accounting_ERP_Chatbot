-- =====================================================
-- Phase 5: Consolidate Statement Dispute Tables
-- Merges: ar_statement_dispute, supplier_statement_dispute → statement_disputes
-- =====================================================

-- Create unified statement_disputes table
CREATE TABLE IF NOT EXISTS statement_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    
    -- Polymorphic party reference
    party_type VARCHAR(20) NOT NULL CHECK (party_type IN ('CUSTOMER', 'SUPPLIER')),
    party_id BIGINT NOT NULL,  -- customer_id or supplier_id
    
    -- Document reference (invoice or bill)
    document_id UUID,
    document_number VARCHAR(100),
    
    -- Reconciliation reference (if applicable)
    reconciliation_id UUID,
    
    -- Amounts
    system_amount NUMERIC(19, 2),
    counterparty_amount NUMERIC(19, 2),  -- customer_amount or disputed_amount
    variance NUMERIC(19, 2),
    variance_type VARCHAR(20),  -- SIGNIFICANT, ROUNDING
    
    -- Dispute details
    dispute_reason TEXT,
    notes TEXT,
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_notes TEXT,
    
    -- Audit fields
    created_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_by_id BIGINT REFERENCES users(id),
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- Migrate ar_statement_dispute data
INSERT INTO statement_disputes (
    id, company_id, party_type, party_id, document_id, document_number,
    reconciliation_id, system_amount, counterparty_amount, variance, variance_type,
    notes, status, resolution_notes, created_at, updated_at, resolved_by_id, resolved_at
)
SELECT 
    id, company_id, 'CUSTOMER', 
    (SELECT customer_id FROM sales_invoices WHERE id = ar.invoice_id LIMIT 1),
    invoice_id, invoice_number,
    reconciliation_id, system_amount, customer_amount, variance, variance_type::VARCHAR,
    notes, status::VARCHAR, resolution_notes, created_at, updated_at, resolved_by_id, resolved_at
FROM ar_statement_dispute ar
ON CONFLICT (id) DO NOTHING;

-- Migrate supplier_statement_dispute data
INSERT INTO statement_disputes (
    id, company_id, party_type, party_id, document_id, document_number,
    system_amount, counterparty_amount, dispute_reason, status, resolution_notes,
    created_by_id, created_at, updated_at, resolved_by_id, resolved_at
)
SELECT 
    id, company_id, 'SUPPLIER', supplier_id,
    bill_id, bill_number,
    system_amount, disputed_amount, dispute_reason, status::VARCHAR, resolution_notes,
    created_by, created_at, created_at, resolved_by, resolved_at
FROM supplier_statement_dispute
ON CONFLICT (id) DO NOTHING;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_statement_disputes_company ON statement_disputes(company_id);
CREATE INDEX IF NOT EXISTS idx_statement_disputes_party ON statement_disputes(party_type, party_id);
CREATE INDEX IF NOT EXISTS idx_statement_disputes_document ON statement_disputes(document_id);
CREATE INDEX IF NOT EXISTS idx_statement_disputes_status ON statement_disputes(status);
CREATE INDEX IF NOT EXISTS idx_statement_disputes_created ON statement_disputes(created_at DESC);

-- Create view for AR Statement Disputes (backward compatibility)
CREATE OR REPLACE VIEW ar_statement_dispute_view AS
SELECT 
    id, company_id, reconciliation_id, document_id as invoice_id,
    document_number as invoice_number, system_amount, counterparty_amount as customer_amount,
    variance, variance_type, notes, status, resolved_at, resolved_by_id, resolution_notes,
    created_at, updated_at
FROM statement_disputes
WHERE party_type = 'CUSTOMER';

-- Create view for Supplier Statement Disputes (backward compatibility)
CREATE OR REPLACE VIEW supplier_statement_dispute_view AS
SELECT 
    id, company_id, party_id as supplier_id, document_id as bill_id,
    document_number as bill_number, dispute_reason, status, resolution_notes,
    created_by_id as created_by, created_at, resolved_by_id as resolved_by, resolved_at,
    counterparty_amount as disputed_amount, system_amount
FROM statement_disputes
WHERE party_type = 'SUPPLIER';

-- Drop old tables
DROP TABLE IF EXISTS ar_statement_dispute CASCADE;
DROP TABLE IF EXISTS supplier_statement_dispute CASCADE;

-- Comment
COMMENT ON TABLE statement_disputes IS 'Consolidated statement disputes for both customers (AR) and suppliers (AP)';
COMMENT ON COLUMN statement_disputes.party_type IS 'CUSTOMER for AR disputes, SUPPLIER for AP disputes';
COMMENT ON COLUMN statement_disputes.counterparty_amount IS 'customer_amount for AR, disputed_amount for AP';
