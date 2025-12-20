-- ============================================================================
-- Tenant Analytics Provisioning SQL Template
-- ============================================================================
-- This template creates database-level isolation for Metabase analytics.
-- Each tenant gets:
--   1. Read-only role: mb_company_{id}_ro
--   2. Dedicated schema: mb_company_{id}
--   3. Security views filtering MVs by company_id
--   4. Grants for the role to access only their views
-- ============================================================================
-- Parameters (to be replaced by application):
--   :company_id - The company ID (BIGINT)
--   :role_password - Generated secure password for the role
-- ============================================================================

-- ============================================================================
-- 1. Create tenant-specific read-only role
-- ============================================================================
DO $$
DECLARE
    v_role_name TEXT := 'mb_company_:company_id_ro';
    v_schema_name TEXT := 'mb_company_:company_id';
BEGIN
    -- Create role if not exists
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_role_name) THEN
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE',
                       v_role_name, ':role_password');
        RAISE NOTICE 'Created role: %', v_role_name;
    ELSE
        -- Update password if role exists
        EXECUTE format('ALTER ROLE %I WITH PASSWORD %L', v_role_name, ':role_password');
        RAISE NOTICE 'Updated password for role: %', v_role_name;
    END IF;

    -- Grant CONNECT to database
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), v_role_name);
END $$;

-- ============================================================================
-- 2. Create tenant-specific schema
-- ============================================================================
DO $$
DECLARE
    v_schema_name TEXT := 'mb_company_:company_id';
    v_role_name TEXT := 'mb_company_:company_id_ro';
BEGIN
    -- Create schema if not exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = v_schema_name) THEN
        EXECUTE format('CREATE SCHEMA %I AUTHORIZATION %I', v_schema_name, v_role_name);
        RAISE NOTICE 'Created schema: %', v_schema_name;
    END IF;

    -- Grant usage on schema
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', v_schema_name, v_role_name);
END $$;

-- ============================================================================
-- 3. Create security views (filtering by company_id)
-- ============================================================================
-- These views wrap the materialized views and filter by the tenant's company_id

-- View: Daily Revenue & Expense
CREATE OR REPLACE VIEW mb_company_:company_id.v_daily_revenue_expense AS
SELECT
    transaction_date,
    revenue,
    expense,
    net_income,
    voucher_count,
    refreshed_at
FROM public.mv_daily_revenue_expense
WHERE company_id = :company_id;

-- View: AR/AP Aging
CREATE OR REPLACE VIEW mb_company_:company_id.v_ar_ap_aging AS
SELECT
    balance_type,
    customer_id,
    supplier_id,
    bucket_current,
    bucket_1_30,
    bucket_31_60,
    bucket_61_90,
    bucket_over_90,
    total_outstanding,
    transaction_count,
    refreshed_at
FROM public.mv_ar_ap_aging
WHERE company_id = :company_id;

-- View: Cash Flow Summary
CREATE OR REPLACE VIEW mb_company_:company_id.v_cash_flow_summary AS
SELECT
    transaction_date,
    account_id,
    account_code,
    account_name,
    cash_in,
    cash_out,
    net_flow,
    transaction_count,
    refreshed_at
FROM public.mv_cash_flow_summary
WHERE company_id = :company_id;

-- View: Period Summary
CREATE OR REPLACE VIEW mb_company_:company_id.v_period_summary AS
SELECT
    period_id,
    total_revenue,
    total_expense,
    (total_revenue - total_expense) AS net_profit,
    ar_balance,
    ap_balance,
    cash_balance,
    voucher_count,
    period_start,
    period_end,
    refreshed_at
FROM public.mv_period_summary
WHERE company_id = :company_id;

-- View: Top Debtors/Creditors
CREATE OR REPLACE VIEW mb_company_:company_id.v_top_debtors_creditors AS
SELECT
    entity_type,
    entity_id,
    entity_name,
    entity_code,
    balance,
    rank,
    refreshed_at
FROM public.mv_top_debtors_creditors
WHERE company_id = :company_id;

-- ============================================================================
-- 4. Grant SELECT on all views to the tenant role
-- ============================================================================
DO $$
DECLARE
    v_schema_name TEXT := 'mb_company_:company_id';
    v_role_name TEXT := 'mb_company_:company_id_ro';
BEGIN
    -- Grant SELECT on all views in the tenant schema
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO %I', v_schema_name, v_role_name);
    
    -- Set default privileges for future views
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT ON TABLES TO %I', 
                   v_schema_name, v_role_name);
    
    RAISE NOTICE 'Granted SELECT permissions to % on schema %', v_role_name, v_schema_name;
END $$;

-- ============================================================================
-- 5. Revoke access to public schema (tenant can ONLY see their schema)
-- ============================================================================
DO $$
DECLARE
    v_role_name TEXT := 'mb_company_:company_id_ro';
BEGIN
    -- Revoke all on public schema tables
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', v_role_name);
    
    -- Revoke usage on public schema (prevents browsing other tables)
    EXECUTE format('REVOKE USAGE ON SCHEMA public FROM %I', v_role_name);
    
    RAISE NOTICE 'Revoked public schema access from %', v_role_name;
END $$;

-- ============================================================================
-- 6. Set search_path to only tenant schema
-- ============================================================================
DO $$
DECLARE
    v_role_name TEXT := 'mb_company_:company_id_ro';
    v_schema_name TEXT := 'mb_company_:company_id';
BEGIN
    EXECUTE format('ALTER ROLE %I SET search_path TO %I', v_role_name, v_schema_name);
    RAISE NOTICE 'Set search_path for % to %', v_role_name, v_schema_name;
END $$;
