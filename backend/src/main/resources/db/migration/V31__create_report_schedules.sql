-- Report Schedules table for scheduling automated report generation
-- Part of Story 7.3: Report Scheduling feature

CREATE TABLE report_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    report_type VARCHAR(20) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    period_rule VARCHAR(30) NOT NULL,
    parameters JSONB,
    export_formats TEXT[] NOT NULL,
    recipients TEXT[] NOT NULL,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_report_type CHECK (report_type IN ('S06', 'B01', 'B02', 'B03', 'F01')),
    CONSTRAINT chk_period_rule CHECK (period_rule IN ('LAST_CLOSED', 'CURRENT', 'SPECIFIC'))
);

COMMENT ON TABLE report_schedules IS 'Scheduled report configurations for automated generation';
COMMENT ON COLUMN report_schedules.report_type IS 'Type of report: S06, B01, B02, B03, F01';
COMMENT ON COLUMN report_schedules.period_rule IS 'How to determine the period: LAST_CLOSED, CURRENT, SPECIFIC';
COMMENT ON COLUMN report_schedules.parameters IS 'Additional parameters, e.g., period_id for SPECIFIC rule';
COMMENT ON COLUMN report_schedules.export_formats IS 'Array of export formats: PDF, EXCEL';
COMMENT ON COLUMN report_schedules.recipients IS 'Array of email addresses to receive the report';
COMMENT ON COLUMN report_schedules.version IS 'Optimistic locking version';

CREATE INDEX idx_report_schedules_company_id ON report_schedules(company_id);
CREATE INDEX idx_report_schedules_company_active ON report_schedules(company_id, is_active);
CREATE INDEX idx_report_schedules_next_run ON report_schedules(next_run_at, is_active) WHERE is_active = TRUE;
