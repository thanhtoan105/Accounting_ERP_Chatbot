CREATE TABLE IF NOT EXISTS data_integrity_jobs (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    triggered_by_user_id BIGINT NOT NULL,
    triggered_by_email VARCHAR(255),
    triggered_by_role VARCHAR(50),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    findings_count INTEGER DEFAULT 0,
    warnings JSONB,
    summary TEXT,
    audit_log_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS data_integrity_findings (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES data_integrity_jobs(id) ON DELETE CASCADE,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(64),
    issue_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) DEFAULT 'MEDIUM',
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_data_integrity_jobs_company ON data_integrity_jobs(company_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_integrity_findings_job ON data_integrity_findings(job_id);
CREATE INDEX IF NOT EXISTS idx_data_integrity_findings_entity ON data_integrity_findings(entity_type, entity_id);



