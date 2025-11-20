-- Create supplier_statement_dispute table for tracking discrepancies and disputes during reconciliation
CREATE TABLE IF NOT EXISTS supplier_statement_dispute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    bill_id UUID, -- Nullable: some disputes may not be linked to specific bills
    dispute_reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    resolution_notes TEXT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by BIGINT,
    resolved_at TIMESTAMP WITH TIME ZONE,
    bill_number VARCHAR(100), -- For disputes where bill doesn't exist in system
    disputed_amount DECIMAL(19, 2), -- Amount from supplier statement
    system_amount DECIMAL(19, 2), -- Amount in our system
    CONSTRAINT fk_supplier_statement_dispute_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_statement_dispute_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_statement_dispute_bill FOREIGN KEY (bill_id) REFERENCES purchase_bills(id) ON DELETE SET NULL,
    CONSTRAINT fk_supplier_statement_dispute_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_supplier_statement_dispute_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Create indexes for efficient queries
CREATE INDEX idx_supplier_statement_dispute_company_id ON supplier_statement_dispute(company_id);
CREATE INDEX idx_supplier_statement_dispute_supplier_id ON supplier_statement_dispute(supplier_id);
CREATE INDEX idx_supplier_statement_dispute_bill_id ON supplier_statement_dispute(bill_id);
CREATE INDEX idx_supplier_statement_dispute_status ON supplier_statement_dispute(status);
CREATE INDEX idx_supplier_statement_dispute_company_supplier ON supplier_statement_dispute(company_id, supplier_id);
CREATE INDEX idx_supplier_statement_dispute_created_at ON supplier_statement_dispute(created_at DESC);

-- Add comments
COMMENT ON TABLE supplier_statement_dispute IS 'Tracks discrepancies found during supplier statement reconciliation with resolution workflow';
COMMENT ON COLUMN supplier_statement_dispute.bill_id IS 'Nullable: dispute may reference bill not in system or general supplier-level dispute';
COMMENT ON COLUMN supplier_statement_dispute.bill_number IS 'Bill number from supplier statement, for disputes where bill does not exist in system';
COMMENT ON COLUMN supplier_statement_dispute.disputed_amount IS 'Amount shown on supplier statement';
COMMENT ON COLUMN supplier_statement_dispute.system_amount IS 'Amount in our system for comparison';

