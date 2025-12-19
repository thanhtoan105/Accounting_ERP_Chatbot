-- ============================================================================
-- TT200 AC 8.0.13 Compliance: Immutable Audit Chain
-- ============================================================================
-- This migration creates an immutable audit log with cryptographic hash chain
-- to ensure data integrity and tamper-evidence as required by Vietnamese
-- accounting regulations (Thông tư 200/2014/TT-BTC).
--
-- Key features:
-- 1. Hash chain linking each record to previous (tamper detection)
-- 2. Merkle tree for efficient bulk verification
-- 3. Daily checkpoints for integrity verification
-- 4. Immutability enforced at database level (triggers + permissions)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Table: analytics_audit_log
-- Purpose: Immutable audit trail with cryptographic hash chain
-- ----------------------------------------------------------------------------
CREATE TABLE analytics_audit_log (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    
    -- Event timing
    event_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_date_utc DATE NOT NULL,
    
    -- Event classification
    event_type VARCHAR(64) NOT NULL,
    event_subtype VARCHAR(64),
    
    -- Principal (who performed the action)
    principal_id BIGINT,
    principal_type VARCHAR(32),
    
    -- Object (what was affected)
    object_type VARCHAR(64),
    object_id VARCHAR(255),
    
    -- Request context
    ip_address INET,
    user_agent TEXT,
    request_id UUID,
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    response_status INTEGER,
    
    -- Additional metadata as JSON
    metadata JSONB,
    
    -- Hash chain fields for tamper detection (TT200 compliance)
    sequence_in_company BIGINT NOT NULL,
    sequence_in_day BIGINT NOT NULL,
    prev_hash CHAR(64),
    record_hash CHAR(64) NOT NULL,
    merkle_leaf_hash CHAR(64) NOT NULL,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE analytics_audit_log IS 'TT200 8.0.13: Immutable audit log with cryptographic hash chain for tamper detection';
COMMENT ON COLUMN analytics_audit_log.sequence_in_company IS 'Monotonically increasing sequence per company for chain ordering';
COMMENT ON COLUMN analytics_audit_log.sequence_in_day IS 'Sequence within a single day for daily checkpoint verification';
COMMENT ON COLUMN analytics_audit_log.prev_hash IS 'SHA-256 hash of previous record in chain (NULL for first record)';
COMMENT ON COLUMN analytics_audit_log.record_hash IS 'SHA-256 hash of this record content for integrity verification';
COMMENT ON COLUMN analytics_audit_log.merkle_leaf_hash IS 'Merkle tree leaf hash for efficient bulk verification';

-- ----------------------------------------------------------------------------
-- Table: audit_chain_checkpoints
-- Purpose: Daily verification checkpoints for audit chain integrity
-- ----------------------------------------------------------------------------
CREATE TABLE audit_chain_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    event_date_utc DATE NOT NULL,
    
    -- Sequence range for this checkpoint
    first_sequence BIGINT NOT NULL,
    last_sequence BIGINT NOT NULL,
    record_count BIGINT NOT NULL,
    
    -- Cryptographic verification data
    merkle_root CHAR(64) NOT NULL,
    chain_head_hash CHAR(64) NOT NULL,
    chain_tail_hash CHAR(64) NOT NULL,
    
    -- Verification status
    status VARCHAR(16) NOT NULL,
    last_verified_at TIMESTAMPTZ,
    verification_error TEXT,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT chk_checkpoint_status CHECK (status IN ('PENDING', 'OK', 'MISMATCH', 'MISSING'))
);

COMMENT ON TABLE audit_chain_checkpoints IS 'TT200 8.0.13: Daily checkpoints for audit chain integrity verification';
COMMENT ON COLUMN audit_chain_checkpoints.merkle_root IS 'Root hash of Merkle tree for all records in this checkpoint period';
COMMENT ON COLUMN audit_chain_checkpoints.chain_head_hash IS 'Hash of first record in the chain segment';
COMMENT ON COLUMN audit_chain_checkpoints.chain_tail_hash IS 'Hash of last record in the chain segment';
COMMENT ON COLUMN audit_chain_checkpoints.status IS 'Verification status: PENDING (not yet verified), OK (valid), MISMATCH (tampering detected), MISSING (records missing)';

-- ----------------------------------------------------------------------------
-- Indexes for analytics_audit_log
-- ----------------------------------------------------------------------------
CREATE INDEX idx_analytics_audit_log_company_seq 
    ON analytics_audit_log (company_id, sequence_in_company);

CREATE INDEX idx_analytics_audit_log_company_date_seq 
    ON analytics_audit_log (company_id, event_date_utc, sequence_in_day);

CREATE INDEX idx_analytics_audit_log_company_date 
    ON analytics_audit_log (company_id, event_date_utc);

CREATE UNIQUE INDEX idx_analytics_audit_log_company_seq_unique 
    ON analytics_audit_log (company_id, sequence_in_company);

CREATE UNIQUE INDEX idx_analytics_audit_log_company_date_seq_unique 
    ON analytics_audit_log (company_id, event_date_utc, sequence_in_day);

-- ----------------------------------------------------------------------------
-- Indexes for audit_chain_checkpoints
-- ----------------------------------------------------------------------------
CREATE UNIQUE INDEX idx_audit_chain_checkpoints_company_date 
    ON audit_chain_checkpoints (company_id, event_date_utc);

-- ----------------------------------------------------------------------------
-- Immutability enforcement: Trigger function
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_immutable_analytics_audit_log()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'TT200 Compliance Violation: analytics_audit_log is immutable. UPDATE and DELETE operations are not permitted.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION enforce_immutable_analytics_audit_log() IS 'TT200 8.0.13: Prevents modification or deletion of audit records';

-- ----------------------------------------------------------------------------
-- Immutability enforcement: Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER trg_analytics_audit_log_no_update
    BEFORE UPDATE ON analytics_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION enforce_immutable_analytics_audit_log();

CREATE TRIGGER trg_analytics_audit_log_no_delete
    BEFORE DELETE ON analytics_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION enforce_immutable_analytics_audit_log();

-- ----------------------------------------------------------------------------
-- Permission enforcement
-- TT200 requires that audit logs cannot be modified even by privileged users
-- ----------------------------------------------------------------------------
REVOKE UPDATE, DELETE ON analytics_audit_log FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'accounting_app') THEN
        REVOKE UPDATE, DELETE ON analytics_audit_log FROM accounting_app;
        GRANT SELECT, INSERT ON analytics_audit_log TO accounting_app;
        GRANT SELECT, INSERT, UPDATE ON audit_chain_checkpoints TO accounting_app;
        GRANT USAGE, SELECT ON SEQUENCE audit_chain_checkpoints_id_seq TO accounting_app;
    END IF;
END $$;
