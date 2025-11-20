-- Create approval_workflows table for maker-checker workflow
-- Tracks approval lifecycle with approver ≠ creator constraint

CREATE TABLE approval_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    purchase_bill_id UUID NOT NULL,
    created_by_id BIGINT NOT NULL,
    approved_by_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    threshold_amount DECIMAL(19, 2) NOT NULL,
    bill_amount DECIMAL(19, 2) NOT NULL,
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    approval_reason VARCHAR(1000),
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMP,
    rejected_at TIMESTAMP,

    -- Foreign keys
    CONSTRAINT fk_approval_workflows_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_workflows_purchase_bill FOREIGN KEY (purchase_bill_id) REFERENCES purchase_bills(id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_workflows_created_by FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_workflows_approved_by FOREIGN KEY (approved_by_id) REFERENCES users(id) ON DELETE RESTRICT,

    -- Business constraint: approver must be different from creator (maker-checker)
    CONSTRAINT chk_approval_workflows_approver_not_creator CHECK (approved_by_id IS NULL OR approved_by_id != created_by_id),

    -- Status constraint
    CONSTRAINT chk_approval_workflows_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED'))
);

-- Indexes for performance
CREATE INDEX idx_approval_workflows_company_id ON approval_workflows(company_id);
CREATE INDEX idx_approval_workflows_purchase_bill_id ON approval_workflows(purchase_bill_id);
CREATE INDEX idx_approval_workflows_status ON approval_workflows(status);
CREATE INDEX idx_approval_workflows_company_status ON approval_workflows(company_id, status);
CREATE INDEX idx_approval_workflows_created_at ON approval_workflows(created_at);

-- Comments
COMMENT ON TABLE approval_workflows IS 'Tracks approval workflows for purchase bills implementing maker-checker pattern';
COMMENT ON COLUMN approval_workflows.threshold_amount IS 'Approval threshold amount at the time of workflow creation';
COMMENT ON COLUMN approval_workflows.bill_amount IS 'Total bill amount at the time of workflow creation';
COMMENT ON COLUMN approval_workflows.is_sensitive IS 'Whether bill was marked as sensitive (triggers approval regardless of amount)';
COMMENT ON CONSTRAINT chk_approval_workflows_approver_not_creator ON approval_workflows IS 'Enforces maker-checker: approver must be different from creator';
