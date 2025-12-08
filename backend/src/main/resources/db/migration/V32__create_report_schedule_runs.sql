-- Report Schedule Runs table for tracking execution history
-- Part of Story 7.3: Report Scheduling feature

CREATE TABLE report_schedule_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    schedule_id UUID NOT NULL REFERENCES report_schedules(id),
    period_id UUID NOT NULL REFERENCES accounting_periods(id),
    period_label VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    triggered_by_id BIGINT REFERENCES users(id),
    snapshot_id UUID REFERENCES report_snapshots(id),
    rerun_of_run_id UUID REFERENCES report_schedule_runs(id),
    attempt INTEGER NOT NULL DEFAULT 1,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_code VARCHAR(100),
    error_message TEXT,
    partial_failure_details JSONB,
    sla_deadline TIMESTAMPTZ,
    exceeded_sla BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_run_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED', 'SKIPPED_DUPLICATE')),
    CONSTRAINT chk_trigger_type CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'RETRY'))
);

COMMENT ON TABLE report_schedule_runs IS 'Execution history for scheduled reports';
COMMENT ON COLUMN report_schedule_runs.status IS 'Run status: PENDING, RUNNING, SUCCESS, PARTIAL_SUCCESS, FAILED, CANCELLED, SKIPPED_DUPLICATE';
COMMENT ON COLUMN report_schedule_runs.trigger_type IS 'How the run was triggered: SCHEDULED, MANUAL, RETRY';
COMMENT ON COLUMN report_schedule_runs.rerun_of_run_id IS 'Reference to original run if this is a retry';
COMMENT ON COLUMN report_schedule_runs.partial_failure_details IS 'Details of partial failures (e.g., which exports/recipients failed)';
COMMENT ON COLUMN report_schedule_runs.exceeded_sla IS 'Whether the run exceeded its SLA deadline';

CREATE INDEX idx_report_schedule_runs_company_schedule ON report_schedule_runs(company_id, schedule_id);
CREATE INDEX idx_report_schedule_runs_company_schedule_period ON report_schedule_runs(company_id, schedule_id, period_id);

-- Unique partial index for idempotency: prevent duplicate successful runs for the same schedule and period
CREATE UNIQUE INDEX idx_report_schedule_runs_idempotency 
    ON report_schedule_runs(company_id, schedule_id, period_id) 
    WHERE status = 'SUCCESS';
