-- ============================================================================
-- Refactor Dashboard MVs to use account_category_mapping table
-- ============================================================================
-- Replaces hardcoded LIKE patterns with JOINs to account_category_mapping
-- for TT200 compliance and multi-tenant category customization support.
-- ============================================================================

-- ============================================================================
-- 1. Drop existing MVs and recreate with JOIN logic
-- ============================================================================

DROP MATERIALIZED VIEW IF EXISTS mv_top_debtors_creditors CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_period_summary CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_cash_flow_summary CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_ar_ap_aging CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_daily_revenue_expense CASCADE;

-- ============================================================================
-- 1. MV: Daily Revenue & Expense Summary (using account_category_mapping)
-- ============================================================================
CREATE MATERIALIZED VIEW mv_daily_revenue_expense AS
SELECT
    je.company_id,
    v.voucher_date AS transaction_date,
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
GROUP BY je.company_id, v.voucher_date
ORDER BY je.company_id, v.voucher_date;

-- Indexes for mv_daily_revenue_expense
CREATE UNIQUE INDEX idx_mv_daily_rev_exp_pk 
    ON mv_daily_revenue_expense(company_id, transaction_date);
CREATE INDEX idx_mv_daily_rev_exp_company 
    ON mv_daily_revenue_expense(company_id);
CREATE INDEX idx_mv_daily_rev_exp_date 
    ON mv_daily_revenue_expense(transaction_date);

COMMENT ON MATERIALIZED VIEW mv_daily_revenue_expense IS 
    'Daily aggregated revenue and expense using account_category_mapping. Refresh via ETL every 5 minutes.';

-- ============================================================================
-- 2. MV: AR/AP Aging Summary (using account_category_mapping)
-- ============================================================================
CREATE MATERIALIZED VIEW mv_ar_ap_aging AS
WITH outstanding_balances AS (
    SELECT
        je.company_id,
        je.customer_id,
        je.supplier_id,
        coa.code AS account_code,
        acm.category AS balance_type,
        v.voucher_date,
        -- AR: Debit increases receivable, Credit decreases
        -- AP: Credit increases payable, Debit decreases
        CASE 
            WHEN acm.category = 'AR' THEN je.debit_amount - je.credit_amount
            WHEN acm.category = 'AP' THEN je.credit_amount - je.debit_amount
            ELSE 0
        END AS amount,
        CURRENT_DATE - v.voucher_date AS days_outstanding
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
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
    -- Aging buckets
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
GROUP BY company_id, balance_type, customer_id, supplier_id;

-- Indexes for mv_ar_ap_aging
CREATE INDEX idx_mv_ar_ap_aging_company 
    ON mv_ar_ap_aging(company_id);
CREATE INDEX idx_mv_ar_ap_aging_type 
    ON mv_ar_ap_aging(company_id, balance_type);
CREATE INDEX idx_mv_ar_ap_aging_customer 
    ON mv_ar_ap_aging(company_id, customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX idx_mv_ar_ap_aging_supplier 
    ON mv_ar_ap_aging(company_id, supplier_id) WHERE supplier_id IS NOT NULL;

COMMENT ON MATERIALIZED VIEW mv_ar_ap_aging IS 
    'AR/AP aging analysis using account_category_mapping. Buckets: Current, 1-30, 31-60, 61-90, >90 days.';

-- ============================================================================
-- 3. MV: Cash Flow Summary (using account_category_mapping)
-- ============================================================================
CREATE MATERIALIZED VIEW mv_cash_flow_summary AS
SELECT
    je.company_id,
    v.voucher_date AS transaction_date,
    coa.id AS account_id,
    coa.code AS account_code,
    coa.name AS account_name,
    -- Cash flow: Debit = inflow, Credit = outflow
    COALESCE(SUM(je.debit_amount), 0) AS cash_in,
    COALESCE(SUM(je.credit_amount), 0) AS cash_out,
    COALESCE(SUM(je.debit_amount - je.credit_amount), 0) AS net_flow,
    COUNT(DISTINCT v.id) AS transaction_count,
    NOW() AS refreshed_at
FROM journal_entries je
INNER JOIN vouchers v ON je.voucher_id = v.id
INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
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
GROUP BY je.company_id, v.voucher_date, coa.id, coa.code, coa.name
ORDER BY je.company_id, v.voucher_date, coa.code;

-- Indexes for mv_cash_flow_summary
CREATE UNIQUE INDEX idx_mv_cash_flow_pk 
    ON mv_cash_flow_summary(company_id, transaction_date, account_id);
CREATE INDEX idx_mv_cash_flow_company 
    ON mv_cash_flow_summary(company_id);
CREATE INDEX idx_mv_cash_flow_date 
    ON mv_cash_flow_summary(transaction_date);
CREATE INDEX idx_mv_cash_flow_account 
    ON mv_cash_flow_summary(company_id, account_code);

COMMENT ON MATERIALIZED VIEW mv_cash_flow_summary IS 
    'Daily cash flow using account_category_mapping (CASH/BANK categories). Shows inflow, outflow, and net position.';

-- ============================================================================
-- 4. MV: Period Summary KPIs (using account_category_mapping)
-- ============================================================================
CREATE MATERIALIZED VIEW mv_period_summary AS
SELECT
    je.company_id,
    v.period_id,
    -- Revenue KPI
    COALESCE(SUM(
        CASE WHEN acm.category = 'REVENUE'
        THEN je.credit_amount - je.debit_amount
        ELSE 0 END
    ), 0) AS total_revenue,
    -- Expense KPI
    COALESCE(SUM(
        CASE WHEN acm.category IN ('COGS', 'OPERATING_EXPENSE', 'OTHER_EXPENSE')
        THEN je.debit_amount - je.credit_amount
        ELSE 0 END
    ), 0) AS total_expense,
    -- AR Balance
    COALESCE(SUM(
        CASE WHEN acm.category = 'AR'
        THEN je.debit_amount - je.credit_amount
        ELSE 0 END
    ), 0) AS ar_balance,
    -- AP Balance
    COALESCE(SUM(
        CASE WHEN acm.category = 'AP'
        THEN je.credit_amount - je.debit_amount
        ELSE 0 END
    ), 0) AS ap_balance,
    -- Cash Balance (CASH + BANK)
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
GROUP BY je.company_id, v.period_id;

-- Indexes for mv_period_summary
CREATE UNIQUE INDEX idx_mv_period_summary_pk 
    ON mv_period_summary(company_id, period_id);
CREATE INDEX idx_mv_period_summary_company 
    ON mv_period_summary(company_id);

COMMENT ON MATERIALIZED VIEW mv_period_summary IS 
    'Period-level KPIs using account_category_mapping: Revenue, Expense, AR, AP, Cash.';

-- ============================================================================
-- 5. MV: Top Debtors/Creditors (using account_category_mapping)
-- ============================================================================
CREATE MATERIALIZED VIEW mv_top_debtors_creditors AS
WITH customer_balances AS (
    SELECT
        je.company_id,
        je.customer_id,
        c.name AS customer_name,
        c.code AS customer_code,
        SUM(je.debit_amount - je.credit_amount) AS balance
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    INNER JOIN customers c ON je.customer_id = c.id
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
    GROUP BY je.company_id, je.customer_id, c.name, c.code
    HAVING SUM(je.debit_amount - je.credit_amount) > 0
),
supplier_balances AS (
    SELECT
        je.company_id,
        je.supplier_id,
        s.name AS supplier_name,
        s.code AS supplier_code,
        SUM(je.credit_amount - je.debit_amount) AS balance
    FROM journal_entries je
    INNER JOIN vouchers v ON je.voucher_id = v.id
    INNER JOIN chart_of_accounts coa ON je.account_id = coa.id
    INNER JOIN suppliers s ON je.supplier_id = s.id
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
    GROUP BY je.company_id, je.supplier_id, s.name, s.code
    HAVING SUM(je.credit_amount - je.debit_amount) > 0
),
ranked_debtors AS (
    SELECT
        company_id,
        'DEBTOR' AS entity_type,
        customer_id AS entity_id,
        customer_name AS entity_name,
        customer_code AS entity_code,
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
        balance,
        ROW_NUMBER() OVER (PARTITION BY company_id ORDER BY balance DESC) AS rank
    FROM supplier_balances
)
SELECT company_id, entity_type, entity_id, entity_name, entity_code, balance, rank, NOW() AS refreshed_at
FROM ranked_debtors WHERE rank <= 10
UNION ALL
SELECT company_id, entity_type, entity_id, entity_name, entity_code, balance, rank, NOW() AS refreshed_at
FROM ranked_creditors WHERE rank <= 10;

-- Indexes for mv_top_debtors_creditors
CREATE INDEX idx_mv_top_dc_company 
    ON mv_top_debtors_creditors(company_id);
CREATE INDEX idx_mv_top_dc_type 
    ON mv_top_debtors_creditors(company_id, entity_type);
CREATE INDEX idx_mv_top_dc_rank 
    ON mv_top_debtors_creditors(company_id, entity_type, rank);

COMMENT ON MATERIALIZED VIEW mv_top_debtors_creditors IS 
    'Top 10 debtors and creditors using account_category_mapping. Use rank <= 5 for widgets.';

-- ============================================================================
-- Recreate refresh function for all dashboard MVs
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
    'Refreshes all dashboard MVs (using account_category_mapping). Called by ETL service every 5 minutes.';
