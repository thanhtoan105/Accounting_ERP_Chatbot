-- Story 7.2: Create report_snapshots table for immutable report storage
-- Stores generated report data for reproducibility and legal compliance

CREATE TABLE report_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL,           -- 'B01', 'B02', 'B03', 'F01'
    period_id UUID NOT NULL REFERENCES accounting_periods(id),
    comparison_period_id UUID REFERENCES accounting_periods(id),

    -- Metadata for reproducibility
    mapping_version INTEGER NOT NULL,           -- Which mapping version was used
    parameters JSONB NOT NULL DEFAULT '{}',     -- Additional parameters used

    -- Data integrity
    data_hash VARCHAR(64) NOT NULL,             -- SHA-256 hash of snapshot_data
    snapshot_data JSONB NOT NULL,               -- Full report data (lines, totals, etc.)

    -- Generation info
    generated_by BIGINT NOT NULL REFERENCES users(id),
    generated_at TIMESTAMPTZ DEFAULT NOW(),

    -- Status flags
    is_draft BOOLEAN DEFAULT TRUE,              -- TRUE if period was open at generation
    is_final BOOLEAN DEFAULT FALSE,             -- TRUE = cannot be regenerated

    -- Legal hold (for audit/investigation)
    legal_hold BOOLEAN DEFAULT FALSE,
    legal_hold_reason TEXT,
    legal_hold_by BIGINT REFERENCES users(id),
    legal_hold_at TIMESTAMPTZ,

    -- Soft delete
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id)
);

-- Primary lookup index
CREATE INDEX idx_report_snapshots_lookup
    ON report_snapshots(company_id, report_type, period_id, generated_at DESC);

-- Hash lookup for verification
CREATE INDEX idx_report_snapshots_hash
    ON report_snapshots(data_hash);

-- Legal hold queries
CREATE INDEX idx_report_snapshots_legal_hold
    ON report_snapshots(company_id, legal_hold)
    WHERE legal_hold = TRUE;

-- Active snapshots (not deleted)
CREATE INDEX idx_report_snapshots_active
    ON report_snapshots(company_id, report_type, period_id)
    WHERE deleted_at IS NULL;

-- Draft snapshots for open periods
CREATE INDEX idx_report_snapshots_draft
    ON report_snapshots(company_id, is_draft)
    WHERE is_draft = TRUE AND deleted_at IS NULL;

COMMENT ON TABLE report_snapshots IS 'Immutable snapshots of generated reports for audit and legal compliance';
COMMENT ON COLUMN report_snapshots.data_hash IS 'SHA-256 hash for integrity verification';
COMMENT ON COLUMN report_snapshots.is_final IS 'Once TRUE, snapshot cannot be regenerated or deleted';
COMMENT ON COLUMN report_snapshots.is_draft IS 'TRUE if period was open when snapshot was created';
COMMENT ON COLUMN report_snapshots.mapping_version IS 'Version of report_mappings used for reproducibility';
