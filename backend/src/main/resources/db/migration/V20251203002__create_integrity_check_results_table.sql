-- ===========================================================================
-- Story 6.6: Cash & Bank Audit and Compliance - Integrity Check Results Table
-- ===========================================================================
-- Stores integrity check execution results for daily and period-close checks

CREATE TABLE IF NOT EXISTS integrity_check_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      BIGINT NOT NULL,
    check_type      VARCHAR(20) NOT NULL CHECK (check_type IN ('DAILY', 'PERIOD_CLOSE', 'MANUAL', 'HOURLY_ANOMALY')),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PASSED', 'FAILED', 'ERROR')),
    period_id       UUID,
    executed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    duration_ms     BIGINT,
    records_checked INTEGER,
    issue_count     INTEGER DEFAULT 0,
    issues          JSONB,
    alerts_sent     BOOLEAN DEFAULT FALSE,

    CONSTRAINT fk_integrity_check_company
        FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Indexes for common queries
CREATE INDEX idx_integrity_check_company_executed
    ON integrity_check_results(company_id, executed_at DESC);

CREATE INDEX idx_integrity_check_type_status
    ON integrity_check_results(check_type, status);

CREATE INDEX idx_integrity_check_period
    ON integrity_check_results(period_id)
    WHERE period_id IS NOT NULL;

COMMENT ON TABLE integrity_check_results IS 'Stores integrity check execution history for Cash & Bank auditing';
COMMENT ON COLUMN integrity_check_results.check_type IS 'Type of check: DAILY (scheduled 2AM), PERIOD_CLOSE (on period close), MANUAL (admin triggered), HOURLY_ANOMALY (hourly anomaly scan)';
COMMENT ON COLUMN integrity_check_results.status IS 'Check result: PASSED (no issues), FAILED (issues found), ERROR (check failed to complete)';
COMMENT ON COLUMN integrity_check_results.issues IS 'JSON array of IntegrityIssueDTO with type, severity, description, and details';
COMMENT ON COLUMN integrity_check_results.alerts_sent IS 'Whether notification alerts were sent for failed checks';
