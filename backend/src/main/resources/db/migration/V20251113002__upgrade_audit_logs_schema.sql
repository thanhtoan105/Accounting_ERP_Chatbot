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

ALTER TABLE audit_logs
    ALTER COLUMN created_at SET DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_audit_logs_company_id ON audit_logs(company_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_success ON audit_logs(success);



