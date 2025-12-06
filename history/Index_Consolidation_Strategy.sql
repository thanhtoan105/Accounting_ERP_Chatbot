-- ============================================================================
-- OPTIONAL: INDEX CONSOLIDATION & CLEANUP STRATEGY
-- Identifying and removing unused/redundant indexes to reduce overhead
-- 
-- Current state: 17 new indexes added in this session
-- Next step: Monitor usage and consolidate over time
-- ============================================================================

-- ============================================================================
-- PHASE 1: IDENTIFY UNUSED INDEXES
-- ============================================================================
-- Indexes that have never been used for scans will bloat the database
-- Solution: Drop unused indexes, consolidate redundant ones

-- Query 1: Find indexes that have never been used
SELECT 
  schemaname,
  tablename,
  indexname,
  idx_scan as index_scans,
  idx_tup_read as tuples_read,
  idx_tup_fetch as tuples_fetched,
  pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'accounting'
  AND idx_scan = 0  -- Never used
  AND indexrelname NOT LIKE '%_pkey'  -- Exclude primary keys
  AND indexrelname NOT LIKE 'ux_%'  -- Exclude unique indexes
ORDER BY pg_relation_size(indexrelid) DESC;

-- Query 2: Find indexes used very rarely (< 10 scans in lifetime)
SELECT 
  schemaname,
  tablename,
  indexname,
  idx_scan as index_scans,
  idx_tup_read as tuples_read,
  ROUND(100.0 * idx_tup_fetch / NULLIF(idx_tup_read, 0), 2) as fetch_ratio,
  pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'accounting'
  AND idx_scan < 10
  AND indexrelname NOT LIKE '%_pkey'
  AND indexrelname NOT LIKE 'ux_%'
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;

-- ============================================================================
-- PHASE 2: IDENTIFY REDUNDANT INDEXES
-- ============================================================================
-- Multiple indexes on same columns waste space and slow down writes

-- Query 3: Find duplicate indexes
SELECT 
  a.schemaname,
  a.tablename,
  a.indexname as index1,
  b.indexname as index2,
  pg_size_pretty(pg_relation_size(a.indexrelid)) as index1_size,
  pg_size_pretty(pg_relation_size(b.indexrelid)) as index2_size
FROM pg_stat_user_indexes a
JOIN pg_stat_user_indexes b ON a.tablename = b.tablename 
  AND a.indexrelname < b.indexrelname
WHERE a.schemaname = 'accounting'
  AND a.indexdef = b.indexdef
ORDER BY a.tablename, index1;

-- Query 4: Find redundant indexes (subset of another index)
-- Example: index on (a, b, c) makes index on (a, b) redundant
-- This query requires manual review - use query results to understand patterns

SELECT 
  schemaname,
  tablename,
  indexname,
  indexdef,
  pg_size_pretty(pg_relation_size(indexrelid)) as index_size,
  idx_scan as scans
FROM pg_stat_user_indexes
WHERE schemaname = 'accounting'
  AND indexname NOT LIKE '%_pkey'
  AND indexname NOT LIKE 'ux_%'
ORDER BY tablename, indexname;

-- ============================================================================
-- PHASE 3: CONSOLIDATION RECOMMENDATIONS
-- ============================================================================

-- Consolidation Strategy 1: Combine similar indexes
-- If you have:
--   - idx_table_col1
--   - idx_table_col1_col2
-- Consider: Keep only idx_table_col1_col2 (covers both use cases)

-- Example (Review before implementing):
-- -- Drop redundant index (col1 only covered by composite)
-- -- DROP INDEX IF EXISTS idx_sales_invoices_company_id;
-- -- Keep: idx_sales_invoices_company_status_due covers the query

-- Consolidation Strategy 2: Merge columns into fewer indexes
-- If filtering on (company_id, status, created_at DESC):
--   Move to single composite index instead of separate indexes

-- Consolidation Strategy 3: Use partial indexes more aggressively
-- For soft-delete or status-filtered tables:
--   - Only index active records
--   - Save 40-50% of index space

-- ============================================================================
-- PHASE 4: INDEX MONITORING (ONGOING)
-- ============================================================================

-- Create a monitoring query to track index health quarterly

-- Script: Index health dashboard
SELECT 
  schemaname,
  tablename,
  COUNT(*) as total_indexes,
  SUM(CASE WHEN idx_scan = 0 THEN 1 ELSE 0 END) as unused_indexes,
  SUM(CASE WHEN idx_scan < 10 THEN 1 ELSE 0 END) as rarely_used_indexes,
  pg_size_pretty(SUM(pg_relation_size(indexrelid))) as total_index_size,
  ROUND(AVG(idx_scan), 2) as avg_scans_per_index
FROM pg_stat_user_indexes
WHERE schemaname = 'accounting'
  AND indexname NOT LIKE '%_pkey'
  AND indexname NOT LIKE 'ux_%'
GROUP BY schemaname, tablename
ORDER BY SUM(pg_relation_size(indexrelid)) DESC;

-- ============================================================================
-- PHASE 5: INDEX SIZE ANALYSIS
-- ============================================================================

-- Find the largest indexes (candidates for consolidation)
SELECT 
  schemaname,
  tablename,
  indexname,
  pg_size_pretty(pg_relation_size(indexrelid)) as index_size,
  idx_scan,
  CASE 
    WHEN idx_scan = 0 THEN 'UNUSED - DROP'
    WHEN idx_scan < 10 THEN 'RARELY USED - CONSIDER DROPPING'
    WHEN pg_relation_size(indexrelid) > 10 * 1024 * 1024 THEN 'LARGE - CONSOLIDATE'
    ELSE 'OK'
  END as recommendation
FROM pg_stat_user_indexes
WHERE schemaname = 'accounting'
  AND indexname NOT LIKE '%_pkey'
  AND indexname NOT LIKE 'ux_%'
ORDER BY pg_relation_size(indexrelid) DESC
LIMIT 20;

-- ============================================================================
-- SAMPLE CONSOLIDATION ACTIONS (REVIEW BEFORE EXECUTING)
-- ============================================================================

-- Action 1: Drop unused indexes (if recommended from Phase 1)
-- Example:
-- DROP INDEX IF EXISTS idx_unused_column_name;

-- Action 2: Drop redundant indexes
-- Example:
-- DROP INDEX IF EXISTS idx_table_col1;  -- Covered by idx_table_col1_col2

-- Action 3: Consolidate indexes on same table
-- Example:
-- -- Drop separate indexes
-- DROP INDEX IF EXISTS idx_sales_invoices_company_id;
-- DROP INDEX IF EXISTS idx_sales_invoices_status;
-- 
-- -- Create consolidated index
-- CREATE INDEX idx_sales_invoices_company_status 
--   ON accounting.sales_invoices(company_id, status);

-- ============================================================================
-- IMPLEMENTATION CHECKLIST
-- ============================================================================
-- 
-- [ ] Run Phase 1 queries quarterly
-- [ ] Document unused indexes > 1MB
-- [ ] Wait 1-2 weeks before dropping (verify no hidden usage)
-- [ ] Create consolidated indexes before dropping old ones
-- [ ] Test application thoroughly after consolidation
-- [ ] Monitor query plans to verify new indexes are used
-- [ ] Document consolidation in comments for future reference
-- 

-- ============================================================================
-- MAINTENANCE: Drop action template
-- ============================================================================
-- When ready to drop an unused index:
-- 
-- BEGIN;
-- 
-- -- Verify index is not used
-- SELECT * FROM pg_stat_user_indexes 
-- WHERE indexname = 'index_to_drop' 
--   AND idx_scan = 0;
-- 
-- -- Drop it (very fast, just metadata)
-- DROP INDEX IF EXISTS accounting.index_to_drop;
-- 
-- -- Verify app still works fine
-- -- (Run your app's test suite)
-- 
-- COMMIT;
-- 

-- ============================================================================
-- ESTIMATED IMPACT
-- ============================================================================
-- 
-- Current situation (after V999 + V1000):
--   - Total indexes: ~150-200 (across all tables)
--   - New indexes added: 17
--   - Estimated unused indexes: 5-10% of total
--   - Storage savings opportunity: 50-100MB
-- 
-- After consolidation (estimated):
--   - Total indexes: 130-170 (20-30 removed)
--   - Storage savings: 50-100MB
--   - Write performance: +5-10% (fewer indexes to update)
--   - Query performance: -0% to +5% (most queries still covered)
-- 

