-- ============================================================================
-- Dashboard ETL Tracking Tables (Epic 8.0)
-- ============================================================================
-- Purpose: Track ETL job runs, cache status, and widget configurations
-- ============================================================================

-- ============================================================================
-- 1. Dashboard ETL Runs - Track each refresh job
-- ============================================================================
CREATE TABLE IF NOT EXISTS dashboard_etl_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    rows_processed INTEGER DEFAULT 0,
    duration_ms BIGINT,
    error_message TEXT,
    error_stack_trace TEXT,
    retry_count INTEGER DEFAULT 0,
    triggered_by VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    triggered_by_user_id BIGINT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_etl_runs_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_etl_runs_user FOREIGN KEY (triggered_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_etl_runs_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_etl_runs_triggered_by CHECK (triggered_by IN ('SCHEDULED', 'MANUAL', 'SYSTEM', 'STARTUP'))
);

-- Indexes for dashboard_etl_runs
CREATE INDEX IF NOT EXISTS idx_etl_runs_company_id ON dashboard_etl_runs(company_id);
CREATE INDEX IF NOT EXISTS idx_etl_runs_status ON dashboard_etl_runs(status);
CREATE INDEX IF NOT EXISTS idx_etl_runs_job_name ON dashboard_etl_runs(job_name);
CREATE INDEX IF NOT EXISTS idx_etl_runs_started_at ON dashboard_etl_runs(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_etl_runs_company_job ON dashboard_etl_runs(company_id, job_name, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_etl_runs_latest ON dashboard_etl_runs(company_id, status, completed_at DESC);

COMMENT ON TABLE dashboard_etl_runs IS 'Tracks ETL job executions for dashboard MV refreshes. Used for freshness monitoring.';
COMMENT ON COLUMN dashboard_etl_runs.triggered_by IS 'SCHEDULED=cron, MANUAL=user click, SYSTEM=auto, STARTUP=app init';
COMMENT ON COLUMN dashboard_etl_runs.metadata IS 'JSON with additional context: last_voucher_id, row_counts per MV, etc.';

-- ============================================================================
-- 2. Dashboard Freshness Status - Per-company freshness tracking
-- ============================================================================
CREATE TABLE IF NOT EXISTS dashboard_freshness (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL UNIQUE,
    last_successful_refresh TIMESTAMP WITH TIME ZONE,
    last_refresh_attempt TIMESTAMP WITH TIME ZONE,
    last_refresh_status VARCHAR(20),
    consecutive_failures INTEGER DEFAULT 0,
    freshness_level VARCHAR(10) DEFAULT 'RED',
    last_etl_run_id UUID,
    data_as_of_timestamp TIMESTAMP WITH TIME ZONE,
    last_posted_voucher_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_freshness_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_freshness_etl_run FOREIGN KEY (last_etl_run_id) REFERENCES dashboard_etl_runs(id),
    CONSTRAINT ck_freshness_level CHECK (freshness_level IN ('GREEN', 'YELLOW', 'RED'))
);

CREATE INDEX IF NOT EXISTS idx_freshness_company ON dashboard_freshness(company_id);
CREATE INDEX IF NOT EXISTS idx_freshness_level ON dashboard_freshness(freshness_level);

COMMENT ON TABLE dashboard_freshness IS 'Per-company freshness status. GREEN=<5min, YELLOW=5-30min, RED=>30min since last refresh.';
COMMENT ON COLUMN dashboard_freshness.data_as_of_timestamp IS 'Authoritative timestamp for data freshness (not browser time).';

-- ============================================================================
-- 3. Widget Configurations - Per-company widget settings
-- ============================================================================
CREATE TABLE IF NOT EXISTS widget_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    widget_code VARCHAR(50) NOT NULL,
    widget_name VARCHAR(100) NOT NULL,
    widget_type VARCHAR(30) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    required_roles TEXT[] DEFAULT '{}',
    config JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_widget_config_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT ck_widget_type CHECK (widget_type IN ('TIME_SERIES', 'KPI', 'TABLE', 'PIE', 'BAR', 'CARD')),
    CONSTRAINT uq_widget_company_code UNIQUE (company_id, widget_code)
);

CREATE INDEX IF NOT EXISTS idx_widget_config_company ON widget_configurations(company_id);
CREATE INDEX IF NOT EXISTS idx_widget_config_enabled ON widget_configurations(company_id, is_enabled);

COMMENT ON TABLE widget_configurations IS 'Per-company widget settings including RBAC, order, and custom config.';
COMMENT ON COLUMN widget_configurations.required_roles IS 'Array of roles allowed to view this widget. Empty = all roles.';
COMMENT ON COLUMN widget_configurations.config IS 'JSON with widget-specific settings: colors, thresholds, filters, etc.';

-- ============================================================================
-- 4. Dashboard Audit Log - Analytics access tracking (AC 8.0.10)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dashboard_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    response_status INTEGER,
    duration_ms BIGINT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_dashboard_audit_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_dashboard_audit_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for efficient audit queries
CREATE INDEX IF NOT EXISTS idx_dashboard_audit_company ON dashboard_audit_log(company_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_audit_user ON dashboard_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_audit_action ON dashboard_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_dashboard_audit_created ON dashboard_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dashboard_audit_company_action ON dashboard_audit_log(company_id, action, created_at DESC);

-- Partition by month for efficient retention management (optional - enable if needed)
-- ALTER TABLE dashboard_audit_log PARTITION BY RANGE (created_at);

COMMENT ON TABLE dashboard_audit_log IS 'Audit trail for dashboard/analytics access. Retention: 10 years per TT200.';
COMMENT ON COLUMN dashboard_audit_log.action IS 'Actions: EMBED_REQUEST, DASHBOARD_VIEW, REFRESH_ATTEMPT, EXPORT, TOKEN_GENERATED';
COMMENT ON COLUMN dashboard_audit_log.resource_type IS 'Types: DASHBOARD, WIDGET, ETL_JOB, EXPORT, TOKEN';

-- ============================================================================
-- 5. Seed default widget configurations (optional - per company on first access)
-- ============================================================================
-- Note: This function creates default widgets for a company on first analytics access

CREATE OR REPLACE FUNCTION seed_default_widget_configurations(p_company_id BIGINT)
RETURNS void AS $$
BEGIN
    -- Only seed if no widgets exist for this company
    IF NOT EXISTS (SELECT 1 FROM widget_configurations WHERE company_id = p_company_id) THEN
        INSERT INTO widget_configurations (company_id, widget_code, widget_name, widget_type, display_order, required_roles)
        VALUES
            (p_company_id, 'REVENUE_EXPENSE', 'Doanh thu vs Chi phí', 'TIME_SERIES', 1, '{}'),
            (p_company_id, 'AR_AP_AGING', 'Tuổi nợ Phải thu/Phải trả', 'BAR', 2, '{}'),
            (p_company_id, 'CASH_POSITION', 'Số dư tiền mặt', 'CARD', 3, '{ADMIN,CFO,CHIEF_ACCOUNTANT,CASHIER}'),
            (p_company_id, 'TOP_DEBTORS', 'Top 5 Khách nợ', 'TABLE', 4, '{ADMIN,CFO,CHIEF_ACCOUNTANT,ACCOUNTANT_AR}'),
            (p_company_id, 'TOP_CREDITORS', 'Top 5 Nhà cung cấp nợ', 'TABLE', 5, '{ADMIN,CFO,CHIEF_ACCOUNTANT,ACCOUNTANT_AP}'),
            (p_company_id, 'PERIOD_SUMMARY', 'Tổng hợp kỳ', 'KPI', 0, '{}');
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION seed_default_widget_configurations IS 'Seeds default TT200-compliant widgets for a company on first analytics access.';

-- ============================================================================
-- 6. ETL Distributed Lock Table (for preventing overlapping runs)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dashboard_etl_locks (
    lock_name VARCHAR(100) PRIMARY KEY,
    locked_by VARCHAR(255) NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_etl_locks_expires ON dashboard_etl_locks(expires_at);

COMMENT ON TABLE dashboard_etl_locks IS 'Distributed locks for ETL jobs. Prevents overlapping executions across instances.';

-- ============================================================================
-- 7. Helper function to acquire ETL lock
-- ============================================================================
CREATE OR REPLACE FUNCTION acquire_etl_lock(
    p_lock_name VARCHAR(100),
    p_locked_by VARCHAR(255),
    p_ttl_seconds INTEGER DEFAULT 300
)
RETURNS BOOLEAN AS $$
DECLARE
    v_acquired BOOLEAN := FALSE;
BEGIN
    -- Clean up expired locks
    DELETE FROM dashboard_etl_locks WHERE expires_at < CURRENT_TIMESTAMP;
    
    -- Try to acquire lock
    INSERT INTO dashboard_etl_locks (lock_name, locked_by, locked_at, expires_at)
    VALUES (p_lock_name, p_locked_by, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + (p_ttl_seconds || ' seconds')::INTERVAL)
    ON CONFLICT (lock_name) DO NOTHING;
    
    -- Check if we got the lock
    SELECT EXISTS (
        SELECT 1 FROM dashboard_etl_locks 
        WHERE lock_name = p_lock_name AND locked_by = p_locked_by
    ) INTO v_acquired;
    
    RETURN v_acquired;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION release_etl_lock(p_lock_name VARCHAR(100), p_locked_by VARCHAR(255))
RETURNS BOOLEAN AS $$
DECLARE
    v_deleted INTEGER;
BEGIN
    DELETE FROM dashboard_etl_locks 
    WHERE lock_name = p_lock_name AND locked_by = p_locked_by;
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted > 0;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION acquire_etl_lock IS 'Attempts to acquire a distributed lock. Returns TRUE if successful.';
COMMENT ON FUNCTION release_etl_lock IS 'Releases a distributed lock. Returns TRUE if lock was released.';
