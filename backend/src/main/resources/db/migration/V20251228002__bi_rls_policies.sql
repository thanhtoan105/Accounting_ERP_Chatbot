-- ============================================================================
-- BI RLS Policies for Multi-Tenant Company Isolation
-- ============================================================================
-- This migration creates secure views on top of BI materialized views
-- with Row-Level Security enforced via app.company_id session setting.
-- Materialized views don't support RLS directly, so we create wrapper views.
-- ============================================================================

-- ============================================================================
-- 1. Create dedicated BI readonly role
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'bi_readonly') THEN
        CREATE ROLE bi_readonly NOLOGIN;
        COMMENT ON ROLE bi_readonly IS 'Read-only role for BI/Metabase access with RLS enforcement';
    END IF;
END
$$;

-- ============================================================================
-- 2. Create function to set company context for BI queries (BIGINT version)
-- ============================================================================
CREATE OR REPLACE FUNCTION set_bi_company_context(p_company_id BIGINT)
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.company_id', p_company_id::text, false);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION set_bi_company_context(BIGINT) IS 
    'Sets the company_id context for RLS. Call at connection start in Metabase.';

-- Grant execute to bi_readonly so Metabase can set context
GRANT EXECUTE ON FUNCTION set_bi_company_context(BIGINT) TO bi_readonly;

-- ============================================================================
-- 3. Helper function to get current company context safely (returns BIGINT)
-- ============================================================================
CREATE OR REPLACE FUNCTION get_bi_company_id()
RETURNS BIGINT AS $$
DECLARE
    v_company_id TEXT;
BEGIN
    v_company_id := current_setting('app.company_id', true);
    IF v_company_id IS NULL OR v_company_id = '' THEN
        RETURN NULL;
    END IF;
    RETURN v_company_id::BIGINT;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

COMMENT ON FUNCTION get_bi_company_id() IS 
    'Returns current company_id from session or NULL if not set.';

-- ============================================================================
-- 4. Create RLS-enabled secure views on top of materialized views
-- ============================================================================

-- 4.1 Secure view for bi_cash_position_daily
CREATE OR REPLACE VIEW bi_cash_position_daily_secure AS
SELECT 
    company_id,
    account_id,
    account_code,
    account_name,
    balance_date,
    opening_balance,
    closing_balance,
    net_change,
    refreshed_at
FROM bi_cash_position_daily
WHERE company_id = get_bi_company_id();

COMMENT ON VIEW bi_cash_position_daily_secure IS 
    'RLS-secured view of bi_cash_position_daily. Filters by app.company_id.';

-- 4.2 Secure view for bi_ar_aging
CREATE OR REPLACE VIEW bi_ar_aging_secure AS
SELECT 
    company_id,
    customer_id,
    customer_name,
    current_amount,
    days_1_30,
    days_31_60,
    days_61_90,
    days_over_90,
    total_ar,
    refreshed_at
FROM bi_ar_aging
WHERE company_id = get_bi_company_id();

COMMENT ON VIEW bi_ar_aging_secure IS 
    'RLS-secured view of bi_ar_aging. Filters by app.company_id.';

-- 4.3 Secure view for bi_ap_aging
CREATE OR REPLACE VIEW bi_ap_aging_secure AS
SELECT 
    company_id,
    supplier_id,
    supplier_name,
    current_amount,
    days_1_30,
    days_31_60,
    days_61_90,
    days_over_90,
    total_ap,
    refreshed_at
FROM bi_ap_aging
WHERE company_id = get_bi_company_id();

COMMENT ON VIEW bi_ap_aging_secure IS 
    'RLS-secured view of bi_ap_aging. Filters by app.company_id.';

-- 4.4 Secure view for bi_exec_kpis_daily
CREATE OR REPLACE VIEW bi_exec_kpis_daily_secure AS
SELECT 
    company_id,
    kpi_date,
    total_revenue,
    total_expenses,
    net_income,
    total_ar,
    total_ap,
    cash_balance,
    dso,
    dpo,
    refreshed_at
FROM bi_exec_kpis_daily
WHERE company_id = get_bi_company_id();

COMMENT ON VIEW bi_exec_kpis_daily_secure IS 
    'RLS-secured view of bi_exec_kpis_daily. Filters by app.company_id.';

-- 4.5 Secure view for bi_revenue_vs_prior
CREATE OR REPLACE VIEW bi_revenue_vs_prior_secure AS
SELECT 
    company_id,
    period_month,
    current_revenue,
    prior_revenue,
    revenue_change,
    change_percent,
    refreshed_at
FROM bi_revenue_vs_prior
WHERE company_id = get_bi_company_id();

COMMENT ON VIEW bi_revenue_vs_prior_secure IS 
    'RLS-secured view of bi_revenue_vs_prior. Filters by app.company_id.';

-- ============================================================================
-- 5. Grant SELECT permissions on secure views to bi_readonly
-- ============================================================================
GRANT SELECT ON bi_cash_position_daily_secure TO bi_readonly;
GRANT SELECT ON bi_ar_aging_secure TO bi_readonly;
GRANT SELECT ON bi_ap_aging_secure TO bi_readonly;
GRANT SELECT ON bi_exec_kpis_daily_secure TO bi_readonly;
GRANT SELECT ON bi_revenue_vs_prior_secure TO bi_readonly;

-- ============================================================================
-- 6. Create audit log table for BI access tracking
-- ============================================================================
CREATE TABLE IF NOT EXISTS bi_access_log (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    accessed_view TEXT NOT NULL,
    accessed_at TIMESTAMPTZ DEFAULT NOW(),
    db_user TEXT DEFAULT CURRENT_USER
);

CREATE INDEX IF NOT EXISTS idx_bi_access_log_company ON bi_access_log(company_id);
CREATE INDEX IF NOT EXISTS idx_bi_access_log_time ON bi_access_log(accessed_at);

COMMENT ON TABLE bi_access_log IS 
    'Audit trail for BI view access. Populated by application.';

-- Grant insert to bi_readonly for logging
GRANT INSERT ON bi_access_log TO bi_readonly;
GRANT USAGE, SELECT ON SEQUENCE bi_access_log_id_seq TO bi_readonly;
