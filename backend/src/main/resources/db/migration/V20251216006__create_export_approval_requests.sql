-- Large Export Approval Workflow (AC 8.0.17)
-- Exports >10k rows require manager approval

CREATE TABLE export_approval_requests (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    requester_id BIGINT NOT NULL REFERENCES users(id),
    export_type VARCHAR(50) NOT NULL,
    query_parameters JSONB,
    row_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approver_id BIGINT REFERENCES users(id),
    rejection_reason TEXT,
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    decided_at TIMESTAMP WITH TIME ZONE,
    download_token VARCHAR(255) UNIQUE,
    download_expiry TIMESTAMP WITH TIME ZONE,
    download_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT chk_export_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'DOWNLOADED'))
);

CREATE INDEX idx_export_approval_company ON export_approval_requests(company_id);
CREATE INDEX idx_export_approval_requester ON export_approval_requests(requester_id);
CREATE INDEX idx_export_approval_status ON export_approval_requests(company_id, status);
CREATE INDEX idx_export_approval_token ON export_approval_requests(download_token) WHERE download_token IS NOT NULL;

COMMENT ON TABLE export_approval_requests IS 'Tracks large export requests requiring manager approval (>10k rows)';
COMMENT ON COLUMN export_approval_requests.status IS 'PENDING, APPROVED, REJECTED, EXPIRED, or DOWNLOADED';
COMMENT ON COLUMN export_approval_requests.download_token IS 'Secure token for downloading approved exports';
COMMENT ON COLUMN export_approval_requests.download_expiry IS 'Token expires 24h after approval';
