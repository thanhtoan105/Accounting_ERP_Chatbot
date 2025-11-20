-- Ensure all columns from V20251113002 exist (in case it was skipped/failed)
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS company_id BIGINT,
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS entity_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS entity_display VARCHAR(255),
    ADD COLUMN IF NOT EXISTS actor_role VARCHAR(50),
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS success BOOLEAN,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS changes JSONB,
    ADD COLUMN IF NOT EXISTS metadata JSONB,
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

-- Add new columns to audit_logs
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS chain_hash VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS retention_until TIMESTAMP WITH TIME ZONE;

-- Create indexes for efficient audit filtering
-- idx_audit_logs_company_action_date already exists from V20251115001 (company_id, action, created_at DESC)
-- so we skip creating idx_audit_logs_company_action_created to avoid redundancy and potential conflicts

CREATE INDEX IF NOT EXISTS idx_audit_logs_company_user_created ON audit_logs(company_id, user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_company_entity ON audit_logs(company_id, entity_id, entity_type);

-- Create ap_audit_backups table
CREATE TABLE IF NOT EXISTS ap_audit_backups (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    backup_date TIMESTAMP WITH TIME ZONE NOT NULL,
    archive_path VARCHAR(512) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    record_count INTEGER,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_ap_audit_backups_company_id ON ap_audit_backups(company_id);
