-- ============================================================================
-- Fix: Re-add period lock columns to MVs after COA mapping refactor
-- ============================================================================
-- V20251216003 added as_of_period_id and period_locked columns
-- V20251216004 recreated MVs for COA mapping but lost those columns
-- This migration restores the period lock columns while keeping COA mapping
-- ============================================================================

-- ============================================================================
-- 1. MV: Daily Revenue & Expense Summary (with COA mapping + period lock)
-- ============================================================================
DROP MATERIALIZED VIEW IF EXISTS mv_daily_revenue_expense CASCADE;

CREATE MATERIALIZED VIEW mv_daily_revenue_expense AS
SELECT
    je.company_id,
    v.voucher_date AS transaction_date,
    v.period_id AS as_of_period_id,
    (ap.status = 'CLOSED') AS period_locked,
    -- Revenue: Credit side of REVENUE category accounts
    COALESCE(SUM(
        CASE WHEN acm.category = 'REVENUE'
        THEN je.credit_amount - je.debit_amount
        ELSE 0 END
    ), 0) AS revenue,
    -- Expense: Debit side of expense category accounts
    COALESCE(SUM(
        CASE WHEN acm.category IN ('COGS', 'OPERATING_EXPENSE', 'OTHER_EXPENSE')
        THEN je.debit_amount - je.credit_amount
        ELSE 0 END
    ), 0) AS expense,
    -- Net = Revenue - Expense
    COALESCE(SUM(
        CASE WHEN acm.category = 'REVENUE'
        THEN je.credit_amount - je.debit_amount
        WHEN acm.category IN ('COGS', 'OPERATING_EXPENSE', 'OTHER_EXPENSE')
        THEN -(je.debit_amount - je.credit_amount)
        ELSE 0 END
    ), 0) AS net_income,
    COUNT(DISTINCT v.id) AS voucher_count,
    NOW() AS refreshed_at
FROM journal_entries je
INNER JOIN vouchers v ON je.voucher_id = v.id
INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
INNER JOIN accounting_periods ap ON ap.id = v.period_id AND ap.company_id = je.company_id
LEFT JOIN LATERAL (
    SELECT acm_inner.category FROM account_category_mapping acm_inner
    WHERE coa.code LIKE acm_inner.account_code_prefix || '%'
    AND (acm_inner.company_id IS NULL OR acm_inner.company_id = je.company_id)
    AND acm_inner.active = TRUE
    ORDER BY length(acm_inner.account_code_prefix) DESC, acm_inner.company_id NULLS LAST
    LIMIT 1
) acm ON TRUE
WHERE v.status = 'posted'
  AND v.reversal_of IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vouchers rv 
      WHERE rv.reversal_of = v.id
  )
GROUP BY je.company_id, v.voucher_date, v.period_id, ap.status
ORDER BY je.company_id, v.voucher_date;

CREATE UNIQUE INDEX idx_mv_daily_rev_exp_pk 
    ON mv_daily_revenue_expense(company_id, transaction_date, as_of_period_id);
CREATE INDEX idx_mv_daily_rev_exp_company 
    ON mv_daily_revenue_expense(company_id);
CREATE INDEX idx_mv_daily_rev_exp_date 
    ON mv_daily_revenue_expense(transaction_date);
CREATE INDEX idx_mv_daily_rev_exp_period 
    ON mv_daily_revenue_expense(company_id, as_of_period_id);
CREATE INDEX idx_mv_daily_rev_exp_locked 
    ON mv_daily_revenue_expense(company_id, period_locked);

COMMENT ON MATERIALIZED VIEW mv_daily_revenue_expense IS 
    'Daily revenue/expense with COA mapping + period lock. Refresh via ETL every 5 minutes.';

-- ============================================================================
-- 2. MV: AR/AP Aging Summary (with COA mapping + period lock)
-- ============================================================================
DROP MATERIALIZED VIEW IF EXISTS mv_ar_ap_aging CASCADE;

CREATE MATERIALIZED VIEW mv_ar_ap_aging AS
WITH outstanding_balances AS (
    SELECT
        je.company_id,
        je.customer_id,
        je.supplier_id,
        coa.code AS account_code,
        acm.category AS balance_type,
        v.voucher_date,
        v.period_id AS as_of_period_id,
        (ap.status = 'CLOSED') AS period_locked,
        CASE 
            WHEN acm.category = 'AR' THEN je.debit_amount - je.credit_amount
            WHEN acm.category = 'AP' THEN je.credit_amount - je.debit_amount
            ELSE 0
        END AS amount,
        CURRENT_DATE - v.voucher_date AS days_outstanding
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    INNER JOIN accounting_periods ap ON ap.id = v.period_id AND ap.company_id = je.company_id
    LEFT JOIN LATERAL (
        SELECT acm_inner.category FROM account_category_mapping acm_inner
        WHERE coa.code LIKE acm_inner.account_code_prefix || '%'
        AND (acm_inner.company_id IS NULL OR acm_inner.company_id = je.company_id)
        AND acm_inner.active = TRUE
        ORDER BY length(acm_inner.account_code_prefix) DESC, acm_inner.company_id NULLS LAST
        LIMIT 1
    ) acm ON TRUE
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND acm.category IN ('AR', 'AP')
)
SELECT
    company_id,
    balance_type::TEXT AS balance_type,
    customer_id,
    supplier_id,
    as_of_period_id,
    period_locked,
    SUM(CASE WHEN days_outstanding <= 0 THEN amount ELSE 0 END) AS bucket_current,
    SUM(CASE WHEN days_outstanding BETWEEN 1 AND 30 THEN amount ELSE 0 END) AS bucket_1_30,
    SUM(CASE WHEN days_outstanding BETWEEN 31 AND 60 THEN amount ELSE 0 END) AS bucket_31_60,
    SUM(CASE WHEN days_outstanding BETWEEN 61 AND 90 THEN amount ELSE 0 END) AS bucket_61_90,
    SUM(CASE WHEN days_outstanding > 90 THEN amount ELSE 0 END) AS bucket_over_90,
    SUM(amount) AS total_outstanding,
    COUNT(*) AS transaction_count,
    NOW() AS refreshed_at
FROM outstanding_balances
WHERE amount <> 0
GROUP BY company_id, balance_type, customer_id, supplier_id, as_of_period_id, period_locked;

CREATE INDEX idx_mv_ar_ap_aging_company 
    ON mv_ar_ap_aging(company_id);
CREATE INDEX idx_mv_ar_ap_aging_type 
    ON mv_ar_ap_aging(company_id, balance_type);
CREATE INDEX idx_mv_ar_ap_aging_customer 
    ON mv_ar_ap_aging(company_id, customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX idx_mv_ar_ap_aging_supplier 
    ON mv_ar_ap_aging(company_id, supplier_id) WHERE supplier_id IS NOT NULL;
CREATE INDEX idx_mv_ar_ap_aging_period 
    ON mv_ar_ap_aging(company_id, as_of_period_id);
CREATE INDEX idx_mv_ar_ap_aging_locked 
    ON mv_ar_ap_aging(company_id, period_locked);

COMMENT ON MATERIALIZED VIEW mv_ar_ap_aging IS 
    'AR/AP aging with COA mapping + period lock. Buckets: Current, 1-30, 31-60, 61-90, >90 days.';

-- ============================================================================
-- 3. MV: Cash Flow Summary (with COA mapping + period lock)
-- ============================================================================
DROP MATERIALIZED VIEW IF EXISTS mv_cash_flow_summary CASCADE;

CREATE MATERIALIZED VIEW mv_cash_flow_summary AS
SELECT
    je.company_id,
    v.voucher_date AS transaction_date,
    coa.id AS account_id,
    coa.code AS account_code,
    coa.name AS account_name,
    v.period_id AS as_of_period_id,
    (ap.status = 'CLOSED') AS period_locked,
    COALESCE(SUM(je.debit_amount), 0) AS cash_in,
    COALESCE(SUM(je.credit_amount), 0) AS cash_out,
    COALESCE(SUM(je.debit_amount - je.credit_amount), 0) AS net_flow,
    COUNT(DISTINCT v.id) AS transaction_count,
    NOW() AS refreshed_at
FROM journal_entries je
INNER JOIN vouchers v ON je.voucher_id = v.id
INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
INNER JOIN accounting_periods ap ON ap.id = v.period_id AND ap.company_id = je.company_id
LEFT JOIN LATERAL (
    SELECT acm_inner.category FROM account_category_mapping acm_inner
    WHERE coa.code LIKE acm_inner.account_code_prefix || '%'
    AND (acm_inner.company_id IS NULL OR acm_inner.company_id = je.company_id)
    AND acm_inner.active = TRUE
    ORDER BY length(acm_inner.account_code_prefix) DESC, acm_inner.company_id NULLS LAST
    LIMIT 1
) acm ON TRUE
WHERE v.status = 'posted'
  AND v.reversal_of IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vouchers rv 
      WHERE rv.reversal_of = v.id
  )
  AND acm.category IN ('CASH', 'BANK')
GROUP BY je.company_id, v.voucher_date, coa.id, coa.code, coa.name, v.period_id, ap.status
ORDER BY je.company_id, v.voucher_date, coa.code;

CREATE UNIQUE INDEX idx_mv_cash_flow_pk 
    ON mv_cash_flow_summary(company_id, transaction_date, account_id, as_of_period_id);
CREATE INDEX idx_mv_cash_flow_company 
    ON mv_cash_flow_summary(company_id);
CREATE INDEX idx_mv_cash_flow_date 
    ON mv_cash_flow_summary(transaction_date);
CREATE INDEX idx_mv_cash_flow_account 
    ON mv_cash_flow_summary(company_id, account_code);
CREATE INDEX idx_mv_cash_flow_period 
    ON mv_cash_flow_summary(company_id, as_of_period_id);
CREATE INDEX idx_mv_cash_flow_locked 
    ON mv_cash_flow_summary(company_id, period_locked);

COMMENT ON MATERIALIZED VIEW mv_cash_flow_summary IS 
    'Daily cash flow with COA mapping (CASH/BANK) + period lock. Refresh via ETL.';

-- ============================================================================
-- 4. MV: Period Summary KPIs (with COA mapping + period lock)
-- ============================================================================
DROP MATERIALIZED VIEW IF EXISTS mv_period_summary CASCADE;

CREATE MATERIALIZED VIEW mv_period_summary AS
SELECT
    je.company_id,
    v.period_id,
    (ap.status = 'CLOSED') AS period_locked,
    COALESCE(SUM(
        CASE WHEN acm.category = 'REVENUE'
        THEN je.credit_amount - je.debit_amount
        ELSE 0 END
    ), 0) AS total_revenue,
    COALESCE(SUM(
        CASE WHEN acm.category IN ('COGS', 'OPERATING_EXPENSE', 'OTHER_EXPENSE')
        THEN je.debit_amount - je.credit_amount
        ELSE 0 END
    ), 0) AS total_expense,
    COALESCE(SUM(
        CASE WHEN acm.category = 'AR'
        THEN je.debit_amount - je.credit_amount
        ELSE 0 END
    ), 0) AS ar_balance,
    COALESCE(SUM(
        CASE WHEN acm.category = 'AP'
        THEN je.credit_amount - je.debit_amount
        ELSE 0 END
    ), 0) AS ap_balance,
    COALESCE(SUM(
        CASE WHEN acm.category IN ('CASH', 'BANK')
        THEN je.debit_amount - je.credit_amount
        ELSE 0 END
    ), 0) AS cash_balance,
    COUNT(DISTINCT v.id) AS voucher_count,
    MIN(v.voucher_date) AS period_start,
    MAX(v.voucher_date) AS period_end,
    NOW() AS refreshed_at
FROM journal_entries je
INNER JOIN vouchers v ON je.voucher_id = v.id
INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
INNER JOIN accounting_periods ap ON ap.id = v.period_id AND ap.company_id = je.company_id
LEFT JOIN LATERAL (
    SELECT acm_inner.category FROM account_category_mapping acm_inner
    WHERE coa.code LIKE acm_inner.account_code_prefix || '%'
    AND (acm_inner.company_id IS NULL OR acm_inner.company_id = je.company_id)
    AND acm_inner.active = TRUE
    ORDER BY length(acm_inner.account_code_prefix) DESC, acm_inner.company_id NULLS LAST
    LIMIT 1
) acm ON TRUE
WHERE v.status = 'posted'
  AND v.reversal_of IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vouchers rv 
      WHERE rv.reversal_of = v.id
  )
GROUP BY je.company_id, v.period_id, ap.status;

CREATE UNIQUE INDEX idx_mv_period_summary_pk 
    ON mv_period_summary(company_id, period_id);
CREATE INDEX idx_mv_period_summary_company 
    ON mv_period_summary(company_id);
CREATE INDEX idx_mv_period_summary_locked 
    ON mv_period_summary(company_id, period_locked);

COMMENT ON MATERIALIZED VIEW mv_period_summary IS 
    'Period-level KPIs with COA mapping + period lock status. Refresh via ETL.';

-- ============================================================================
-- 5. MV: Top Debtors/Creditors (with COA mapping + period lock)
-- ============================================================================
DROP MATERIALIZED VIEW IF EXISTS mv_top_debtors_creditors CASCADE;

CREATE MATERIALIZED VIEW mv_top_debtors_creditors AS
WITH customer_balances AS (
    SELECT
        je.company_id,
        je.customer_id,
        c.name AS customer_name,
        c.code AS customer_code,
        v.period_id AS as_of_period_id,
        (ap.status = 'CLOSED') AS period_locked,
        SUM(je.debit_amount - je.credit_amount) AS balance
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    INNER JOIN customers c ON je.customer_id = c.id
    INNER JOIN accounting_periods ap ON ap.id = v.period_id AND ap.company_id = je.company_id
    LEFT JOIN LATERAL (
        SELECT acm_inner.category FROM account_category_mapping acm_inner
        WHERE coa.code LIKE acm_inner.account_code_prefix || '%'
        AND (acm_inner.company_id IS NULL OR acm_inner.company_id = je.company_id)
        AND acm_inner.active = TRUE
        ORDER BY length(acm_inner.account_code_prefix) DESC, acm_inner.company_id NULLS LAST
        LIMIT 1
    ) acm ON TRUE
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND acm.category = 'AR'
      AND je.customer_id IS NOT NULL
    GROUP BY je.company_id, je.customer_id, c.name, c.code, v.period_id, ap.status
    HAVING SUM(je.debit_amount - je.credit_amount) > 0
),
supplier_balances AS (
    SELECT
        je.company_id,
        je.supplier_id,
        s.name AS supplier_name,
        s.code AS supplier_code,
        v.period_id AS as_of_period_id,
        (ap.status = 'CLOSED') AS period_locked,
        SUM(je.credit_amount - je.debit_amount) AS balance
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    INNER JOIN suppliers s ON je.supplier_id = s.id
    INNER JOIN accounting_periods ap ON ap.id = v.period_id AND ap.company_id = je.company_id
    LEFT JOIN LATERAL (
        SELECT acm_inner.category FROM account_category_mapping acm_inner
        WHERE coa.code LIKE acm_inner.account_code_prefix || '%'
        AND (acm_inner.company_id IS NULL OR acm_inner.company_id = je.company_id)
        AND acm_inner.active = TRUE
        ORDER BY length(acm_inner.account_code_prefix) DESC, acm_inner.company_id NULLS LAST
        LIMIT 1
    ) acm ON TRUE
    WHERE v.status = 'posted'
      AND v.reversal_of IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM vouchers rv 
          WHERE rv.reversal_of = v.id
      )
      AND acm.category = 'AP'
      AND je.supplier_id IS NOT NULL
    GROUP BY je.company_id, je.supplier_id, s.name, s.code, v.period_id, ap.status
    HAVING SUM(je.credit_amount - je.debit_amount) > 0
),
ranked_debtors AS (
    SELECT
        company_id,
        'DEBTOR' AS entity_type,
        customer_id AS entity_id,
        customer_name AS entity_name,
        customer_code AS entity_code,
        as_of_period_id,
        period_locked,
        balance,
        ROW_NUMBER() OVER (PARTITION BY company_id ORDER BY balance DESC) AS rank
    FROM customer_balances
),
ranked_creditors AS (
    SELECT
        company_id,
        'CREDITOR' AS entity_type,
        supplier_id AS entity_id,
        supplier_name AS entity_name,
        supplier_code AS entity_code,
        as_of_period_id,
        period_locked,
        balance,
        ROW_NUMBER() OVER (PARTITION BY company_id ORDER BY balance DESC) AS rank
    FROM supplier_balances
)
SELECT company_id, entity_type, entity_id, entity_name, entity_code, as_of_period_id, period_locked, balance, rank, NOW() AS refreshed_at
FROM ranked_debtors WHERE rank <= 10
UNION ALL
SELECT company_id, entity_type, entity_id, entity_name, entity_code, as_of_period_id, period_locked, balance, rank, NOW() AS refreshed_at
FROM ranked_creditors WHERE rank <= 10;

CREATE INDEX idx_mv_top_dc_company 
    ON mv_top_debtors_creditors(company_id);
CREATE INDEX idx_mv_top_dc_type 
    ON mv_top_debtors_creditors(company_id, entity_type);
CREATE INDEX idx_mv_top_dc_rank 
    ON mv_top_debtors_creditors(company_id, entity_type, rank);
CREATE INDEX idx_mv_top_dc_period 
    ON mv_top_debtors_creditors(company_id, as_of_period_id);
CREATE INDEX idx_mv_top_dc_locked 
    ON mv_top_debtors_creditors(company_id, period_locked);

COMMENT ON MATERIALIZED VIEW mv_top_debtors_creditors IS 
    'Top 10 debtors/creditors with COA mapping + period lock. Use rank <= 5 for widgets.';

-- ============================================================================
-- Recreate refresh function
-- ============================================================================
CREATE OR REPLACE FUNCTION refresh_dashboard_materialized_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_revenue_expense;
    REFRESH MATERIALIZED VIEW mv_ar_ap_aging;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cash_flow_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_period_summary;
    REFRESH MATERIALIZED VIEW mv_top_debtors_creditors;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_dashboard_materialized_views IS 
    'Refreshes all dashboard MVs with COA mapping + period lock. Called by ETL every 5 minutes.';
