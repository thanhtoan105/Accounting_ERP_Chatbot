-- Create audit_purge_requests table for dual-approval purge workflow (Story 6.6)
-- This table tracks purge requests requiring Chief Accountant request + Admin approval

CREATE TABLE IF NOT EXISTS audit_purge_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    requester_id BIGINT NOT NULL REFERENCES users(id),
    approver_id BIGINT REFERENCES users(id),
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    rejection_reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    estimated_records INTEGER,
    records_purged INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,

    -- Constraints
    CONSTRAINT chk_purge_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_date_range CHECK (date_from <= date_to),
    CONSTRAINT chk_dual_approval CHECK (
        -- If approved, approver must be different from requester
        status != 'APPROVED' OR (approver_id IS NOT NULL AND approver_id != requester_id)
    )
);

-- Index for finding pending requests by company
CREATE INDEX IF NOT EXISTS idx_purge_requests_company_status
    ON audit_purge_requests(company_id, status);

-- Index for finding requests by requester
CREATE INDEX IF NOT EXISTS idx_purge_requests_requester
    ON audit_purge_requests(requester_id);

-- Add comment for documentation
COMMENT ON TABLE audit_purge_requests IS
    'Tracks audit log purge requests requiring dual approval (Story 6.6 AC6.6-05)';
COMMENT ON COLUMN audit_purge_requests.requester_id IS
    'User who requested the purge (must be Chief Accountant)';
COMMENT ON COLUMN audit_purge_requests.approver_id IS
    'User who approved/rejected the purge (must be Admin and different from requester)';
