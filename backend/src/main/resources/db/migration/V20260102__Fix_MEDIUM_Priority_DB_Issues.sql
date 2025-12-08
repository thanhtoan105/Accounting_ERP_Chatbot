-- ============================================================================
-- MEDIUM PRIORITY DATABASE FIXES
-- Issue 1: Audit_logs partitioning + retention policy (TimescaleDB)
-- Issue 2: VARCHAR → TEXT migration with CHECK constraints
-- Issue 3: Soft-delete index optimization
-- ============================================================================
-- IMPORTANT: Run in TEST environment first!
-- ============================================================================

-- ============================================================================
-- NOTE ON TIMESCALEDB: 
-- TimescaleDB extension is not installed in current PostgreSQL instance.
-- To enable time-series features in future:
-- 1. Install TimescaleDB package: apt-get install postgresql-16-timescaledb
-- 2. Run: timescaledb-tune --pg-config=/usr/lib/postgresql/16/bin/pg_config
-- 3. Restart PostgreSQL
-- 4. Run separate migration to enable hypertables + compression
-- 
-- For now, using standard PostgreSQL partitioning + manual retention cleanup
-- ============================================================================

-- ============================================================================
-- FIX 1: Add manual retention cleanup for audit_logs
-- Why: Keeps table size manageable without TimescaleDB
-- Schedule: Run this monthly or use cron job
-- ============================================================================
BEGIN;

-- Add index on retention_until for efficient filtering
CREATE INDEX IF NOT EXISTS idx_audit_logs_retention_until
  ON accounting.audit_logs(retention_until)
  WHERE retention_until IS NOT NULL;

-- Procedure to cleanup expired audit logs (call manually monthly)
-- This can be used in a cron job or background task
-- DELETE FROM accounting.audit_logs 
-- WHERE retention_until IS NOT NULL 
--   AND retention_until < CURRENT_TIMESTAMP;

COMMIT;

-- ============================================================================
-- FIX 5: VARCHAR → TEXT migration pattern
-- Converting character varying(n) to TEXT + CHECK constraints
-- Why: More flexible, PostgreSQL best practice, same performance
-- 
-- Strategy: Use generated columns for backward compatibility
-- 
-- NOTE: This is an EXAMPLE pattern. Apply to each table INDIVIDUALLY
-- and ONLY when ready to migrate application code.
-- ============================================================================

-- MIGRATION PATTERN (applied to each table incrementally):
-- 1. Add new TEXT column
-- 2. Copy data from VARCHAR column
-- 3. Add CHECK constraint on new TEXT column
-- 4. Create index if frequently searched
-- 5. Update application code to use new column
-- 6. Drop old VARCHAR column (after app update)

-- Example: companies.code (VARCHAR(50) → TEXT)
-- COMMENTED OUT - Use as template for actual migrations
--
-- BEGIN;
-- 
-- -- Step 1: Add new TEXT column (temporary name)
-- ALTER TABLE accounting.companies
--   ADD COLUMN code_new TEXT;
-- 
-- -- Step 2: Copy data from VARCHAR to TEXT
-- UPDATE accounting.companies SET code_new = code;
-- 
-- -- Step 3: Add NOT NULL and CHECK constraint
-- ALTER TABLE accounting.companies
--   ALTER COLUMN code_new SET NOT NULL;
-- 
-- ALTER TABLE accounting.companies
--   ADD CONSTRAINT ck_companies_code_length CHECK (LENGTH(code_new) <= 50);
-- 
-- -- Step 4: Create index if needed (for lookups)
-- CREATE INDEX IF NOT EXISTS idx_companies_code_new ON accounting.companies(code_new);
-- 
-- -- Step 5: Swap columns (old app uses code, new app uses code_new)
-- -- DO NOT RUN YET - Wait until app is updated:
-- -- ALTER TABLE accounting.companies RENAME COLUMN code TO code_old;
-- -- ALTER TABLE accounting.companies RENAME COLUMN code_new TO code;
-- -- ALTER TABLE accounting.companies DROP COLUMN code_old;
-- 
-- COMMIT;

-- ============================================================================
-- FIX 6: Soft-delete index optimization
-- Adding partial indexes for frequently filtered queries
-- Why: Index only active records, saves space, faster scans
-- NOTE: Only sales_invoices has is_deleted column in this schema
-- ============================================================================
BEGIN;

-- sales_invoices: Add more partial indexes for soft-deleted queries
CREATE INDEX IF NOT EXISTS idx_sales_invoices_company_active_status_created
  ON accounting.sales_invoices(company_id, status, created_at DESC)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sales_invoices_company_active_date_range
  ON accounting.sales_invoices(company_id, invoice_date)
  WHERE is_deleted = false AND status != 'DRAFT';

COMMIT;

-- ============================================================================
-- FIX 7: Add indexes on retention_until for audit purge operations
-- Why: Speeds up purge queries that filter by retention_until
-- ============================================================================
BEGIN;

CREATE INDEX IF NOT EXISTS idx_audit_logs_retention_until
  ON accounting.audit_logs(retention_until)
  WHERE retention_until IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_logs_retention_company
  ON accounting.audit_logs(company_id, retention_until)
  WHERE retention_until IS NOT NULL;

COMMIT;

-- ============================================================================
-- VERIFICATION QUERIES
-- Run these to confirm all fixes were applied
-- ============================================================================

-- Query 1: Verify retention_until index exists
SELECT 
  schemaname,
  tablename,
  indexname,
  indexdef
FROM pg_indexes
WHERE schemaname = 'accounting'
  AND indexname LIKE '%retention%'
ORDER BY tablename, indexname;

-- Query 2: List all soft-delete indexes
SELECT 
  schemaname,
  tablename,
  indexname,
  indexdef
FROM pg_indexes
WHERE schemaname = 'accounting'
  AND indexdef LIKE '%is_deleted%'
ORDER BY tablename, indexname;

-- Query 3: Check VARCHAR columns still to be migrated
SELECT 
  table_name,
  column_name,
  data_type,
  character_maximum_length,
  is_nullable
FROM information_schema.columns
WHERE table_schema = 'accounting'
  AND data_type = 'character varying'
ORDER BY table_name, column_name;

-- Query 4: Count indexes on FK columns (verify HIGH priority fixes)
SELECT 
  COUNT(*) as total_fk_indexes,
  COUNT(CASE WHEN indexname LIKE '%_by_id' THEN 1 END) as user_fk_indexes,
  COUNT(CASE WHEN indexname LIKE '%_active%' THEN 1 END) as active_status_indexes,
  COUNT(CASE WHEN indexdef LIKE '%is_deleted%' THEN 1 END) as soft_delete_indexes
FROM pg_indexes
WHERE schemaname = 'accounting';

-- ============================================================================
-- MONITORING & MAINTENANCE
-- ============================================================================

-- Monitor audit_logs table size
SELECT 
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as total_size,
  pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) as table_size,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - 
                pg_relation_size(schemaname||'.'||tablename)) as indexes_size
FROM pg_tables
WHERE schemaname = 'accounting'
  AND tablename = 'audit_logs';

-- Count audit logs by age (to plan retention cleanup)
SELECT 
  CASE 
    WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '1 month' THEN '< 1 month'
    WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '3 months' THEN '1-3 months'
    WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '6 months' THEN '3-6 months'
    WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '1 year' THEN '6 months - 1 year'
    WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '2 years' THEN '1-2 years'
    ELSE '> 2 years (eligible for cleanup)'
  END as age_bucket,
  COUNT(*) as record_count,
  pg_size_pretty(SUM(pg_column_size(id) + pg_column_size(created_at))) as approx_size
FROM accounting.audit_logs
GROUP BY age_bucket
ORDER BY age_bucket;

-- Manual cleanup query (run monthly or via cron job)
-- DELETE FROM accounting.audit_logs 
-- WHERE retention_until IS NOT NULL 
--   AND retention_until < CURRENT_TIMESTAMP;

-- Alternative: Delete logs older than 2 years (if no retention_until)
-- DELETE FROM accounting.audit_logs 
-- WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '2 years'
--   AND retention_until IS NULL;

-- ============================================================================
-- ROLLBACK PROCEDURE (if needed)
-- ============================================================================
-- To revert these changes:
--
-- 1. Drop soft-delete optimization indexes:
--    DROP INDEX IF EXISTS idx_sales_invoices_company_active_status_created;
--    DROP INDEX IF EXISTS idx_sales_invoices_company_active_date_range;
--
-- 2. Drop retention index:
--    DROP INDEX IF EXISTS idx_audit_logs_retention_until;
--    DROP INDEX IF EXISTS idx_audit_logs_retention_company;
--
-- 3. Revert VARCHAR columns (if migrated):
--    DROP COLUMN code_new (after reverting application code);
--    Rename code_old back to code;
--
-- NOTE: Reverting these changes is safe and straightforward (just DROP INDEX)
-- ============================================================================
