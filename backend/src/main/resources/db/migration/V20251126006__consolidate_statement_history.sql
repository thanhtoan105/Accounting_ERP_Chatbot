-- =====================================================
-- Phase 4: Consolidate Statement History Tables
-- Merges: ar_statement_history, supplier_statement_history → statement_history
-- =====================================================

-- Create unified statement_history table
CREATE TABLE IF NOT EXISTS statement_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    
    -- Polymorphic party reference
    party_type VARCHAR(20) NOT NULL CHECK (party_type IN ('CUSTOMER', 'SUPPLIER')),
    party_id BIGINT NOT NULL,  -- customer_id or supplier_id
    
    -- Common fields
    statement_number VARCHAR(50),
    statement_type VARCHAR(20) NOT NULL,  -- SUMMARY, DETAILED
    format VARCHAR(20) NOT NULL,  -- PDF, EXCEL, SUMMARY, DETAILED
    
    -- Generation metadata
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    generated_by_id BIGINT NOT NULL REFERENCES users(id),
    
    -- Date range
    as_of_date DATE,
    start_date DATE,
    end_date DATE,
    
    -- Content tracking
    statement_hash VARCHAR(64) NOT NULL,
    file_path VARCHAR(500),
    filters_applied JSONB,
    
    -- Delivery tracking
    sent_at TIMESTAMP WITH TIME ZONE,
    sent_to TEXT,
    
    -- Counters
    export_count INTEGER NOT NULL DEFAULT 0,
    sent_count INTEGER NOT NULL DEFAULT 0,
    view_count INTEGER NOT NULL DEFAULT 0,
    download_count INTEGER NOT NULL DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Migrate ar_statement_history data
INSERT INTO statement_history (
    id, company_id, party_type, party_id, statement_number, statement_type, format,
    generated_at, generated_by_id, as_of_date, statement_hash, filters_applied,
    export_count, sent_count, created_at
)
SELECT 
    id, company_id, 'CUSTOMER', customer_id, statement_number, 
    format::VARCHAR, format::VARCHAR,
    generated_at, generated_by_id, as_of_date, statement_hash, filters_applied::JSONB,
    export_count, sent_count, created_at
FROM ar_statement_history
ON CONFLICT (id) DO NOTHING;

-- Migrate supplier_statement_history data
INSERT INTO statement_history (
    id, company_id, party_type, party_id, statement_number, statement_type, format,
    generated_at, generated_by_id, start_date, end_date, statement_hash, file_path,
    sent_at, sent_to, view_count, download_count, created_at
)
SELECT 
    id, company_id, 'SUPPLIER', supplier_id, NULL, 
    statement_type::VARCHAR, format::VARCHAR,
    generation_date, generated_by, start_date, end_date, hash, file_path,
    sent_date, sent_to, view_count, download_count, generation_date
FROM supplier_statement_history
ON CONFLICT (id) DO NOTHING;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_statement_history_company ON statement_history(company_id);
CREATE INDEX IF NOT EXISTS idx_statement_history_party ON statement_history(party_type, party_id);
CREATE INDEX IF NOT EXISTS idx_statement_history_generated ON statement_history(generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_statement_history_hash ON statement_history(statement_hash);

-- Create unique constraint for statement numbers per company
CREATE UNIQUE INDEX IF NOT EXISTS idx_statement_history_number 
    ON statement_history(company_id, statement_number) WHERE statement_number IS NOT NULL;

-- Create view for AR Statement History (backward compatibility)
CREATE OR REPLACE VIEW ar_statement_history_view AS
SELECT 
    id, company_id, party_id as customer_id, statement_number,
    format as format, generated_at, generated_by_id, as_of_date,
    filters_applied::TEXT, export_count, sent_count, statement_hash, created_at
FROM statement_history
WHERE party_type = 'CUSTOMER';

-- Create view for Supplier Statement History (backward compatibility)
CREATE OR REPLACE VIEW supplier_statement_history_view AS
SELECT 
    id, company_id, party_id as supplier_id, statement_type,
    generated_at as generation_date, generated_by_id as generated_by,
    format, file_path, statement_hash as hash, sent_at as sent_date,
    sent_to, view_count, download_count, start_date, end_date
FROM statement_history
WHERE party_type = 'SUPPLIER';

-- Drop old tables
DROP TABLE IF EXISTS ar_statement_history CASCADE;
DROP TABLE IF EXISTS supplier_statement_history CASCADE;

-- Comment
COMMENT ON TABLE statement_history IS 'Consolidated statement history for both customers (AR) and suppliers (AP)';
COMMENT ON COLUMN statement_history.party_type IS 'CUSTOMER for AR statements, SUPPLIER for AP statements';
