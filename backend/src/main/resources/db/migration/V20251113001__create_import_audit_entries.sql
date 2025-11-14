CREATE TABLE import_audit_entries (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    import_type VARCHAR(64) NOT NULL,
    attempt_id UUID NOT NULL,
    source_filename VARCHAR(255),
    row_number INTEGER,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    before_payload JSONB,
    after_payload JSONB,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512)
);
CREATE INDEX idx_import_audit_entries_company ON import_audit_entries (company_id);
CREATE INDEX idx_import_audit_entries_type ON import_audit_entries (import_type);
CREATE INDEX idx_import_audit_entries_attempt ON import_audit_entries (attempt_id);
CREATE INDEX idx_import_audit_entries_created_at ON import_audit_entries (created_at);