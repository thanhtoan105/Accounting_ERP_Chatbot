-- Create supplier_statement_history table for tracking all generated and sent statements
CREATE TABLE IF NOT EXISTS supplier_statement_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    statement_type VARCHAR(20) NOT NULL CHECK (statement_type IN ('SUMMARY', 'DETAILED')),
    generation_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_by BIGINT NOT NULL,
    format VARCHAR(10) NOT NULL CHECK (format IN ('PDF', 'EXCEL')),
    file_path VARCHAR(500),
    hash VARCHAR(64) NOT NULL, -- SHA-256 hash
    sent_date TIMESTAMP WITH TIME ZONE,
    sent_to TEXT, -- JSON array of recipient emails
    view_count INTEGER NOT NULL DEFAULT 0,
    download_count INTEGER NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    CONSTRAINT fk_supplier_statement_history_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_statement_history_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_statement_history_generated_by FOREIGN KEY (generated_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Create indexes for efficient queries
CREATE INDEX idx_supplier_statement_history_company_id ON supplier_statement_history(company_id);
CREATE INDEX idx_supplier_statement_history_supplier_id ON supplier_statement_history(supplier_id);
CREATE INDEX idx_supplier_statement_history_generation_date ON supplier_statement_history(generation_date DESC);
CREATE INDEX idx_supplier_statement_history_company_supplier ON supplier_statement_history(company_id, supplier_id);

-- Add comments
COMMENT ON TABLE supplier_statement_history IS 'Tracks all generated and sent supplier statements with metadata, hash, and delivery information';
COMMENT ON COLUMN supplier_statement_history.hash IS 'SHA-256 hash of the statement document for integrity verification';
COMMENT ON COLUMN supplier_statement_history.sent_to IS 'JSON array of recipient email addresses';

