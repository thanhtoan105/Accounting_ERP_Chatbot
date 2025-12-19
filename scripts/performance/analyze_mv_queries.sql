-- ============================================================================
-- Materialized View Query Performance Analysis
-- ============================================================================
-- Runs EXPLAIN ANALYZE on all dashboard widget queries
-- Target: All queries complete in <2s P95 (AC 8.0.4)
--
-- Usage: psql -d accounting -f analyze_mv_queries.sql -v company_id=1
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

-- Set analysis parameters
\set ANALYSIS_COMPANY_ID 1
\set START_DATE '''2024-01-01'''
\set END_DATE '''2024-12-31'''

\echo ''
\echo '============================================================================'
\echo 'MATERIALIZED VIEW PERFORMANCE ANALYSIS'
\echo '============================================================================'
\echo 'Company ID:' :ANALYSIS_COMPANY_ID
\echo 'Date Range:' :START_DATE 'to' :END_DATE
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Query 1: Revenue vs Expenses Widget (Daily Trend)
-- ============================================================================
\echo '>>> Query 1: Revenue vs Expenses Widget (mv_daily_revenue_expense)'
\echo ''

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT 
    transaction_date,
    SUM(revenue) AS total_revenue,
    SUM(expense) AS total_expense,
    SUM(net_income) AS net_income,
    SUM(voucher_count) AS voucher_count
FROM mv_daily_revenue_expense
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND transaction_date BETWEEN :START_DATE::DATE AND :END_DATE::DATE
GROUP BY transaction_date
ORDER BY transaction_date;

\echo ''
\echo '--- Actual Query Result Sample ---'
SELECT 
    transaction_date,
    SUM(revenue) AS total_revenue,
    SUM(expense) AS total_expense,
    SUM(net_income) AS net_income
FROM mv_daily_revenue_expense
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND transaction_date BETWEEN :START_DATE::DATE AND :END_DATE::DATE
GROUP BY transaction_date
ORDER BY transaction_date
LIMIT 5;

\echo ''
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Query 2: AR/AP Aging Widget
-- ============================================================================
\echo '>>> Query 2: AR/AP Aging Widget (mv_ar_ap_aging)'
\echo ''

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT 
    balance_type,
    SUM(bucket_current) AS current_amount,
    SUM(bucket_1_30) AS days_1_30,
    SUM(bucket_31_60) AS days_31_60,
    SUM(bucket_61_90) AS days_61_90,
    SUM(bucket_over_90) AS over_90_days,
    SUM(total_outstanding) AS total
FROM mv_ar_ap_aging
WHERE company_id = :ANALYSIS_COMPANY_ID
GROUP BY balance_type;

\echo ''
\echo '--- Actual Query Result ---'
SELECT 
    balance_type,
    SUM(bucket_current) AS current_amount,
    SUM(bucket_1_30) AS days_1_30,
    SUM(bucket_31_60) AS days_31_60,
    SUM(bucket_61_90) AS days_61_90,
    SUM(bucket_over_90) AS over_90_days,
    SUM(total_outstanding) AS total
FROM mv_ar_ap_aging
WHERE company_id = :ANALYSIS_COMPANY_ID
GROUP BY balance_type;

\echo ''
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Query 3: Cash Position Widget (Cash Flow Summary)
-- ============================================================================
\echo '>>> Query 3: Cash Position Widget (mv_cash_flow_summary)'
\echo ''

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT 
    transaction_date,
    SUM(cash_in) AS total_cash_in,
    SUM(cash_out) AS total_cash_out,
    SUM(net_flow) AS net_cash_flow
FROM mv_cash_flow_summary
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND transaction_date BETWEEN :START_DATE::DATE AND :END_DATE::DATE
GROUP BY transaction_date
ORDER BY transaction_date;

\echo ''
\echo '--- Actual Query Result Sample ---'
SELECT 
    transaction_date,
    SUM(cash_in) AS total_cash_in,
    SUM(cash_out) AS total_cash_out,
    SUM(net_flow) AS net_cash_flow
FROM mv_cash_flow_summary
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND transaction_date BETWEEN :START_DATE::DATE AND :END_DATE::DATE
GROUP BY transaction_date
ORDER BY transaction_date
LIMIT 5;

\echo ''
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Query 4: Top 5 Debtors Widget
-- ============================================================================
\echo '>>> Query 4: Top 5 Debtors Widget (mv_top_debtors_creditors)'
\echo ''

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT 
    entity_name,
    entity_code,
    balance,
    rank
FROM mv_top_debtors_creditors
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND entity_type = 'DEBTOR'
  AND rank <= 5
ORDER BY rank;

\echo ''
\echo '--- Actual Query Result ---'
SELECT 
    entity_name,
    entity_code,
    balance,
    rank
FROM mv_top_debtors_creditors
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND entity_type = 'DEBTOR'
  AND rank <= 5
ORDER BY rank;

\echo ''
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Query 5: Top 5 Creditors Widget
-- ============================================================================
\echo '>>> Query 5: Top 5 Creditors Widget (mv_top_debtors_creditors)'
\echo ''

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT 
    entity_name,
    entity_code,
    balance,
    rank
FROM mv_top_debtors_creditors
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND entity_type = 'CREDITOR'
  AND rank <= 5
ORDER BY rank;

\echo ''
\echo '--- Actual Query Result ---'
SELECT 
    entity_name,
    entity_code,
    balance,
    rank
FROM mv_top_debtors_creditors
WHERE company_id = :ANALYSIS_COMPANY_ID
  AND entity_type = 'CREDITOR'
  AND rank <= 5
ORDER BY rank;

\echo ''
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Query 6: Period Summary Widget (KPIs)
-- ============================================================================
\echo '>>> Query 6: Period Summary Widget (mv_period_summary)'
\echo ''

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT 
    period_id,
    total_revenue,
    total_expense,
    (total_revenue - total_expense) AS net_income,
    ar_balance,
    ap_balance,
    cash_balance,
    voucher_count,
    period_locked
FROM mv_period_summary
WHERE company_id = :ANALYSIS_COMPANY_ID
ORDER BY period_start DESC
LIMIT 12;

\echo ''
\echo '--- Actual Query Result ---'
SELECT 
    period_id,
    total_revenue,
    total_expense,
    (total_revenue - total_expense) AS net_income,
    ar_balance,
    ap_balance,
    cash_balance,
    voucher_count
FROM mv_period_summary
WHERE company_id = :ANALYSIS_COMPANY_ID
ORDER BY period_start DESC
LIMIT 3;

\echo ''
\echo '============================================================================'
\echo ''

-- ============================================================================
-- Aggregate Statistics
-- ============================================================================
\echo '>>> Materialized View Statistics'
\echo ''

SELECT 
    'mv_daily_revenue_expense' AS view_name,
    COUNT(*) AS row_count,
    pg_size_pretty(pg_relation_size('mv_daily_revenue_expense')) AS size
UNION ALL
SELECT 
    'mv_ar_ap_aging',
    COUNT(*),
    pg_size_pretty(pg_relation_size('mv_ar_ap_aging'))
FROM mv_ar_ap_aging
UNION ALL
SELECT 
    'mv_cash_flow_summary',
    COUNT(*),
    pg_size_pretty(pg_relation_size('mv_cash_flow_summary'))
FROM mv_cash_flow_summary
UNION ALL
SELECT 
    'mv_period_summary',
    COUNT(*),
    pg_size_pretty(pg_relation_size('mv_period_summary'))
FROM mv_period_summary
UNION ALL
SELECT 
    'mv_top_debtors_creditors',
    COUNT(*),
    pg_size_pretty(pg_relation_size('mv_top_debtors_creditors'))
FROM mv_top_debtors_creditors;

\echo ''
\echo '>>> Index Usage Statistics'
\echo ''

SELECT 
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
WHERE relname LIKE 'mv_%'
ORDER BY relname, indexrelname;

\echo ''
\echo '============================================================================'
\echo 'PERFORMANCE ANALYSIS COMPLETE'
\echo '============================================================================'
\echo ''
\echo 'Key Metrics to Check:'
\echo '  1. Execution Time: Should be <100ms for all queries'
\echo '  2. Rows Returned: Verify expected row counts'
\echo '  3. Index Usage: Ensure indexes are being used (Index Scan vs Seq Scan)'
\echo '  4. Buffers: Lower buffer hits = better performance'
\echo ''
\echo 'P95 Target: <2000ms (2 seconds)'
\echo '============================================================================'
