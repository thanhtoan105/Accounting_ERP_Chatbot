-- ============================================================================
-- BI Schema Materialized Views for CFO Dashboard (Epic: CFO BI Dashboard)
-- ============================================================================
-- All views follow bi_* naming convention for BI-specific use
-- Include company_id for RLS compatibility
-- Use UNIQUE INDEX for CONCURRENTLY refresh support
-- ============================================================================

-- ============================================================================
-- 1. bi_cash_position_daily - Daily cash balances by bank account
-- ============================================================================
-- Purpose: Track daily cash position across all cash/bank accounts
-- Cash accounts: 111 (Tiền mặt), 112 (Tiền gửi ngân hàng)

CREATE MATERIALIZED VIEW IF NOT EXISTS bi_cash_position_daily AS
WITH daily_movements AS (
    SELECT
        je.company_id,
        coa.id AS account_id,
        coa.code AS account_code,
        coa.name AS account_name,
        v.voucher_date AS balance_date,
        SUM(je.debit_amount) AS total_debit,
        SUM(je.credit_amount) AS total_credit,
        SUM(je.debit_amount - je.credit_amount) AS net_change
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND (coa.code LIKE '111%' OR coa.code LIKE '112%')
    GROUP BY je.company_id, coa.id, coa.code, coa.name, v.voucher_date
),
running_balances AS (
    SELECT
        company_id,
        account_id,
        account_code,
        account_name,
        balance_date,
        net_change,
        SUM(net_change) OVER (
            PARTITION BY company_id, account_id 
            ORDER BY balance_date
            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
        ) AS opening_balance,
        SUM(net_change) OVER (
            PARTITION BY company_id, account_id 
            ORDER BY balance_date
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS closing_balance
    FROM daily_movements
)
SELECT
    company_id,
    account_id,
    account_code,
    account_name,
    balance_date,
    COALESCE(opening_balance, 0) AS opening_balance,
    closing_balance,
    net_change,
    NOW() AS refreshed_at
FROM running_balances
ORDER BY company_id, account_code, balance_date;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bi_cash_position_daily_pk 
    ON bi_cash_position_daily(company_id, account_id, balance_date);
CREATE INDEX IF NOT EXISTS idx_bi_cash_position_daily_company 
    ON bi_cash_position_daily(company_id);
CREATE INDEX IF NOT EXISTS idx_bi_cash_position_daily_date 
    ON bi_cash_position_daily(balance_date);
CREATE INDEX IF NOT EXISTS idx_bi_cash_position_daily_account 
    ON bi_cash_position_daily(company_id, account_code);

COMMENT ON MATERIALIZED VIEW bi_cash_position_daily IS 
    'Daily cash balances by bank/cash account. Shows opening, closing, and net change per day.';

-- ============================================================================
-- 2. bi_ar_aging - AR aging buckets by customer
-- ============================================================================
-- Purpose: Accounts Receivable aging analysis for CFO dashboard
-- AR accounts: 131 (Phải thu khách hàng)

CREATE MATERIALIZED VIEW IF NOT EXISTS bi_ar_aging AS
WITH ar_transactions AS (
    SELECT
        je.company_id,
        je.customer_id,
        c.name AS customer_name,
        c.code AS customer_code,
        v.voucher_date,
        je.debit_amount - je.credit_amount AS amount,
        CURRENT_DATE - v.voucher_date AS days_outstanding
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    LEFT JOIN customers c ON je.customer_id = c.id
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND coa.code LIKE '131%'
)
SELECT
    company_id,
    customer_id,
    COALESCE(customer_name, 'Unassigned') AS customer_name,
    COALESCE(customer_code, 'N/A') AS customer_code,
    SUM(CASE WHEN days_outstanding <= 0 THEN amount ELSE 0 END) AS current_amount,
    SUM(CASE WHEN days_outstanding BETWEEN 1 AND 30 THEN amount ELSE 0 END) AS days_1_30,
    SUM(CASE WHEN days_outstanding BETWEEN 31 AND 60 THEN amount ELSE 0 END) AS days_31_60,
    SUM(CASE WHEN days_outstanding BETWEEN 61 AND 90 THEN amount ELSE 0 END) AS days_61_90,
    SUM(CASE WHEN days_outstanding > 90 THEN amount ELSE 0 END) AS days_over_90,
    SUM(amount) AS total_ar,
    COUNT(*) AS transaction_count,
    NOW() AS refreshed_at
FROM ar_transactions
GROUP BY company_id, customer_id, customer_name, customer_code
HAVING SUM(amount) <> 0
ORDER BY company_id, total_ar DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bi_ar_aging_pk 
    ON bi_ar_aging(company_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_bi_ar_aging_company 
    ON bi_ar_aging(company_id);
CREATE INDEX IF NOT EXISTS idx_bi_ar_aging_customer 
    ON bi_ar_aging(customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_bi_ar_aging_total 
    ON bi_ar_aging(company_id, total_ar DESC);

COMMENT ON MATERIALIZED VIEW bi_ar_aging IS 
    'AR aging buckets by customer: Current, 1-30, 31-60, 61-90, >90 days.';

-- ============================================================================
-- 3. bi_ap_aging - AP aging buckets by supplier
-- ============================================================================
-- Purpose: Accounts Payable aging analysis for CFO dashboard
-- AP accounts: 331 (Phải trả người bán)

CREATE MATERIALIZED VIEW IF NOT EXISTS bi_ap_aging AS
WITH ap_transactions AS (
    SELECT
        je.company_id,
        je.supplier_id,
        s.name AS supplier_name,
        s.code AS supplier_code,
        v.voucher_date,
        je.credit_amount - je.debit_amount AS amount,
        CURRENT_DATE - v.voucher_date AS days_outstanding
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    LEFT JOIN suppliers s ON je.supplier_id = s.id
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND coa.code LIKE '331%'
)
SELECT
    company_id,
    supplier_id,
    COALESCE(supplier_name, 'Unassigned') AS supplier_name,
    COALESCE(supplier_code, 'N/A') AS supplier_code,
    SUM(CASE WHEN days_outstanding <= 0 THEN amount ELSE 0 END) AS current_amount,
    SUM(CASE WHEN days_outstanding BETWEEN 1 AND 30 THEN amount ELSE 0 END) AS days_1_30,
    SUM(CASE WHEN days_outstanding BETWEEN 31 AND 60 THEN amount ELSE 0 END) AS days_31_60,
    SUM(CASE WHEN days_outstanding BETWEEN 61 AND 90 THEN amount ELSE 0 END) AS days_61_90,
    SUM(CASE WHEN days_outstanding > 90 THEN amount ELSE 0 END) AS days_over_90,
    SUM(amount) AS total_ap,
    COUNT(*) AS transaction_count,
    NOW() AS refreshed_at
FROM ap_transactions
GROUP BY company_id, supplier_id, supplier_name, supplier_code
HAVING SUM(amount) <> 0
ORDER BY company_id, total_ap DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bi_ap_aging_pk 
    ON bi_ap_aging(company_id, supplier_id);
CREATE INDEX IF NOT EXISTS idx_bi_ap_aging_company 
    ON bi_ap_aging(company_id);
CREATE INDEX IF NOT EXISTS idx_bi_ap_aging_supplier 
    ON bi_ap_aging(supplier_id) WHERE supplier_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_bi_ap_aging_total 
    ON bi_ap_aging(company_id, total_ap DESC);

COMMENT ON MATERIALIZED VIEW bi_ap_aging IS 
    'AP aging buckets by supplier: Current, 1-30, 31-60, 61-90, >90 days.';

-- ============================================================================
-- 4. bi_revenue_vs_prior - Revenue comparison with prior period
-- ============================================================================
-- Purpose: Month-over-month revenue comparison for trend analysis
-- Revenue accounts: 511, 512 (TT200 standard)

CREATE MATERIALIZED VIEW IF NOT EXISTS bi_revenue_vs_prior AS
WITH monthly_revenue AS (
    SELECT
        je.company_id,
        DATE_TRUNC('month', v.voucher_date) AS period_month,
        SUM(je.credit_amount - je.debit_amount) AS revenue
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND (coa.code LIKE '511%' OR coa.code LIKE '512%')
    GROUP BY je.company_id, DATE_TRUNC('month', v.voucher_date)
)
SELECT
    mr.company_id,
    mr.period_month,
    mr.revenue AS current_revenue,
    COALESCE(pr.revenue, 0) AS prior_revenue,
    mr.revenue - COALESCE(pr.revenue, 0) AS revenue_change,
    CASE 
        WHEN COALESCE(pr.revenue, 0) = 0 THEN NULL
        ELSE ROUND(((mr.revenue - pr.revenue) / pr.revenue * 100)::NUMERIC, 2)
    END AS change_percent,
    NOW() AS refreshed_at
FROM monthly_revenue mr
LEFT JOIN monthly_revenue pr 
    ON mr.company_id = pr.company_id 
    AND mr.period_month = pr.period_month + INTERVAL '1 month'
ORDER BY mr.company_id, mr.period_month DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bi_revenue_vs_prior_pk 
    ON bi_revenue_vs_prior(company_id, period_month);
CREATE INDEX IF NOT EXISTS idx_bi_revenue_vs_prior_company 
    ON bi_revenue_vs_prior(company_id);
CREATE INDEX IF NOT EXISTS idx_bi_revenue_vs_prior_month 
    ON bi_revenue_vs_prior(period_month DESC);

COMMENT ON MATERIALIZED VIEW bi_revenue_vs_prior IS 
    'Monthly revenue comparison with prior period. Shows current, prior, change amount and percent.';

-- ============================================================================
-- 5. bi_exec_kpis_daily - Executive KPIs aggregated daily
-- ============================================================================
-- Purpose: High-level executive KPIs for CFO dashboard header
-- Includes: Revenue, Expenses, Net Income, AR, AP, Cash, DSO, DPO

CREATE MATERIALIZED VIEW IF NOT EXISTS bi_exec_kpis_daily AS
WITH daily_kpis AS (
    SELECT
        je.company_id,
        v.voucher_date AS kpi_date,
        -- Revenue (511, 512, 515)
        SUM(CASE 
            WHEN coa.code LIKE '511%' OR coa.code LIKE '512%' OR coa.code LIKE '515%'
            THEN je.credit_amount - je.debit_amount
            ELSE 0
        END) AS total_revenue,
        -- Expenses (621-642, 811)
        SUM(CASE 
            WHEN coa.code LIKE '621%' OR coa.code LIKE '622%' OR coa.code LIKE '623%'
                 OR coa.code LIKE '627%' OR coa.code LIKE '631%' OR coa.code LIKE '632%'
                 OR coa.code LIKE '635%' OR coa.code LIKE '641%' OR coa.code LIKE '642%'
                 OR coa.code LIKE '811%'
            THEN je.debit_amount - je.credit_amount
            ELSE 0
        END) AS total_expenses,
        -- AR Balance (131)
        SUM(CASE 
            WHEN coa.code LIKE '131%'
            THEN je.debit_amount - je.credit_amount
            ELSE 0
        END) AS ar_movement,
        -- AP Balance (331)
        SUM(CASE 
            WHEN coa.code LIKE '331%'
            THEN je.credit_amount - je.debit_amount
            ELSE 0
        END) AS ap_movement,
        -- Cash Balance (111, 112)
        SUM(CASE 
            WHEN coa.code LIKE '111%' OR coa.code LIKE '112%'
            THEN je.debit_amount - je.credit_amount
            ELSE 0
        END) AS cash_movement
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
    GROUP BY je.company_id, v.voucher_date
),
running_totals AS (
    SELECT
        company_id,
        kpi_date,
        total_revenue,
        total_expenses,
        total_revenue - total_expenses AS net_income,
        SUM(ar_movement) OVER (PARTITION BY company_id ORDER BY kpi_date) AS total_ar,
        SUM(ap_movement) OVER (PARTITION BY company_id ORDER BY kpi_date) AS total_ap,
        SUM(cash_movement) OVER (PARTITION BY company_id ORDER BY kpi_date) AS cash_balance,
        SUM(total_revenue) OVER (PARTITION BY company_id ORDER BY kpi_date) AS cumulative_revenue
    FROM daily_kpis
)
SELECT
    company_id,
    kpi_date,
    total_revenue,
    total_expenses,
    net_income,
    total_ar,
    total_ap,
    cash_balance,
    -- DSO = (AR / Revenue) * 365 (simplified, based on cumulative)
    CASE 
        WHEN cumulative_revenue > 0 THEN ROUND((total_ar / cumulative_revenue * 365)::NUMERIC, 1)
        ELSE NULL
    END AS dso,
    -- DPO = (AP / Expenses) * 365 (simplified placeholder)
    CASE 
        WHEN total_expenses > 0 THEN ROUND((total_ap / total_expenses * 30)::NUMERIC, 1)
        ELSE NULL
    END AS dpo,
    NOW() AS refreshed_at
FROM running_totals
ORDER BY company_id, kpi_date DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bi_exec_kpis_daily_pk 
    ON bi_exec_kpis_daily(company_id, kpi_date);
CREATE INDEX IF NOT EXISTS idx_bi_exec_kpis_daily_company 
    ON bi_exec_kpis_daily(company_id);
CREATE INDEX IF NOT EXISTS idx_bi_exec_kpis_daily_date 
    ON bi_exec_kpis_daily(kpi_date DESC);

COMMENT ON MATERIALIZED VIEW bi_exec_kpis_daily IS 
    'Daily executive KPIs: Revenue, Expenses, Net Income, AR, AP, Cash, DSO, DPO.';

-- ============================================================================
-- Refresh function for all BI materialized views
-- ============================================================================
CREATE OR REPLACE FUNCTION refresh_bi_materialized_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY bi_cash_position_daily;
    REFRESH MATERIALIZED VIEW CONCURRENTLY bi_ar_aging;
    REFRESH MATERIALIZED VIEW CONCURRENTLY bi_ap_aging;
    REFRESH MATERIALIZED VIEW CONCURRENTLY bi_revenue_vs_prior;
    REFRESH MATERIALIZED VIEW CONCURRENTLY bi_exec_kpis_daily;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_bi_materialized_views IS 
    'Refreshes all bi_* materialized views. Call daily at 2 AM for CFO dashboard.';

-- ============================================================================
-- Grant SELECT on all BI views to future BI user (for Metabase)
-- ============================================================================
-- Note: The actual BI user creation and grants will be done in RLS migration
-- This documents the intended permissions pattern
COMMENT ON SCHEMA public IS 
    'BI views (bi_*) are designed for Metabase access with RLS via app.company_id setting.';
