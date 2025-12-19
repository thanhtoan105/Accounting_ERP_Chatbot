-- ============================================================================
-- Dashboard Widget Queries for Metabase
-- ============================================================================
-- All queries use materialized views for <2s response time
-- Required parameters: {{company_id}}, {{start_date}}, {{end_date}}
-- ============================================================================

-- ============================================================================
-- Widget 1: Revenue vs Expenses (Time Series)
-- ============================================================================
-- Chart Type: Line/Area chart with dual series
-- X-axis: transaction_date
-- Y-axis: revenue, expense, net_income

SELECT
    transaction_date,
    revenue,
    expense,
    net_income,
    SUM(net_income) OVER (ORDER BY transaction_date) AS cumulative_net_income
FROM mv_daily_revenue_expense
WHERE company_id = {{company_id}}
  AND transaction_date BETWEEN {{start_date}} AND {{end_date}}
ORDER BY transaction_date;


-- ============================================================================
-- Widget 2: AR/AP Balances with Aging
-- ============================================================================
-- Chart Type: Stacked bar chart (grouped by balance_type)
-- Shows aging buckets for both AR and AP

SELECT
    balance_type,
    SUM(bucket_current) AS current_0_days,
    SUM(bucket_1_30) AS aging_1_30_days,
    SUM(bucket_31_60) AS aging_31_60_days,
    SUM(bucket_61_90) AS aging_61_90_days,
    SUM(bucket_over_90) AS aging_over_90_days,
    SUM(total_outstanding) AS total_outstanding
FROM mv_ar_ap_aging
WHERE company_id = {{company_id}}
GROUP BY balance_type
ORDER BY balance_type;


-- ============================================================================
-- Widget 3: Cash Position with Trend
-- ============================================================================
-- Chart Type: Line chart with running balance
-- Shows daily cash movements and cumulative balance

WITH daily_cash AS (
    SELECT
        transaction_date,
        SUM(cash_in) AS total_cash_in,
        SUM(cash_out) AS total_cash_out,
        SUM(net_flow) AS daily_net_flow
    FROM mv_cash_flow_summary
    WHERE company_id = {{company_id}}
      AND transaction_date BETWEEN {{start_date}} AND {{end_date}}
    GROUP BY transaction_date
)
SELECT
    transaction_date,
    total_cash_in,
    total_cash_out,
    daily_net_flow,
    SUM(daily_net_flow) OVER (ORDER BY transaction_date) AS running_balance
FROM daily_cash
ORDER BY transaction_date;


-- ============================================================================
-- Widget 4: Top 5 Debtors
-- ============================================================================
-- Chart Type: Horizontal bar chart
-- Shows top 5 customers with highest AR balance

SELECT
    entity_code,
    entity_name,
    balance AS outstanding_balance,
    rank
FROM mv_top_debtors_creditors
WHERE company_id = {{company_id}}
  AND entity_type = 'DEBTOR'
  AND rank <= 5
ORDER BY rank;


-- ============================================================================
-- Widget 5: Top 5 Creditors
-- ============================================================================
-- Chart Type: Horizontal bar chart
-- Shows top 5 suppliers with highest AP balance

SELECT
    entity_code,
    entity_name,
    balance AS outstanding_balance,
    rank
FROM mv_top_debtors_creditors
WHERE company_id = {{company_id}}
  AND entity_type = 'CREDITOR'
  AND rank <= 5
ORDER BY rank;


-- ============================================================================
-- Widget 6: Period Summary KPIs
-- ============================================================================
-- Chart Type: Scalar/Number cards
-- Returns key metrics for dashboard header

SELECT
    SUM(total_revenue) AS total_revenue,
    SUM(total_expense) AS total_expense,
    SUM(total_revenue) - SUM(total_expense) AS net_profit,
    CASE 
        WHEN SUM(total_revenue) > 0 
        THEN ROUND(((SUM(total_revenue) - SUM(total_expense)) / SUM(total_revenue)) * 100, 2)
        ELSE 0 
    END AS profit_margin_pct,
    SUM(ar_balance) AS total_ar,
    SUM(ap_balance) AS total_ap,
    SUM(ar_balance) - SUM(ap_balance) AS net_working_capital,
    SUM(cash_balance) AS total_cash,
    SUM(voucher_count) AS total_transactions
FROM mv_period_summary
WHERE company_id = {{company_id}};


-- ============================================================================
-- Optional Widget: Cash Position by Account
-- ============================================================================
-- Chart Type: Pie/Donut chart
-- Breakdown of cash balance by account (111 vs 112)

SELECT
    account_code,
    account_name,
    SUM(net_flow) AS account_balance
FROM mv_cash_flow_summary
WHERE company_id = {{company_id}}
  AND transaction_date <= {{end_date}}
GROUP BY account_code, account_name
HAVING SUM(net_flow) <> 0
ORDER BY account_code;


-- ============================================================================
-- Optional Widget: Monthly Revenue Trend
-- ============================================================================
-- Chart Type: Bar chart
-- Monthly aggregation for longer-term trends

SELECT
    DATE_TRUNC('month', transaction_date) AS month,
    SUM(revenue) AS monthly_revenue,
    SUM(expense) AS monthly_expense,
    SUM(net_income) AS monthly_net_income
FROM mv_daily_revenue_expense
WHERE company_id = {{company_id}}
  AND transaction_date BETWEEN {{start_date}} AND {{end_date}}
GROUP BY DATE_TRUNC('month', transaction_date)
ORDER BY month;


-- ============================================================================
-- Optional Widget: AR Aging Detail by Customer
-- ============================================================================
-- Chart Type: Table
-- Detailed AR aging for drill-down

SELECT
    c.code AS customer_code,
    c.name AS customer_name,
    a.bucket_current AS current_0_days,
    a.bucket_1_30 AS aging_1_30_days,
    a.bucket_31_60 AS aging_31_60_days,
    a.bucket_61_90 AS aging_61_90_days,
    a.bucket_over_90 AS aging_over_90_days,
    a.total_outstanding
FROM mv_ar_ap_aging a
INNER JOIN customers c ON a.customer_id = c.id
WHERE a.company_id = {{company_id}}
  AND a.balance_type = 'AR'
  AND a.total_outstanding > 0
ORDER BY a.total_outstanding DESC
LIMIT 20;
