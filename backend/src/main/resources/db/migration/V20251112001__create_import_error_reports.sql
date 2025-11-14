CREATE TABLE IF NOT EXISTS import_error_reports (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    import_type VARCHAR(64) NOT NULL,
    filename VARCHAR(255),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content BYTEA NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_import_error_reports_company
    ON import_error_reports (company_id);

