-- ============================================================================
-- OPTIONAL: VARCHAR → TEXT MIGRATION STRATEGY
-- Gradual conversion of 208 VARCHAR columns to TEXT with CHECK constraints
-- 
-- Approach: Incremental migration, table by table
-- Strategy: Dual-column migration to maintain compatibility during rollout
-- Timeline: 3-5 minutes per table (includes app code changes)
-- ============================================================================

-- ============================================================================
-- PHASE 1: IDENTIFY TARGET COLUMNS
-- ============================================================================
-- Current state: 208 VARCHAR columns across 30+ tables
-- 
-- Migration priority:
-- 1. HIGH:   code/name columns (frequently indexed & filtered)
-- 2. MEDIUM: status/type columns (enum-like, used in queries)
-- 3. LOW:    description/reference columns (less critical)
-- ============================================================================

-- Query to identify all VARCHAR columns
SELECT 
  table_name,
  column_name,
  character_maximum_length,
  COUNT(*) OVER (PARTITION BY table_name) as varchar_count_in_table
FROM information_schema.columns
WHERE table_schema = 'accounting'
  AND data_type = 'character varying'
ORDER BY table_name, column_name;

-- ============================================================================
-- PHASE 2: DUAL-COLUMN MIGRATION TEMPLATE
-- 
-- Pattern: Migrate one table at a time without downtime
-- This allows:
--   - Old application code still works (uses original column)
--   - New application code can use new column
--   - Gradual rollout with ability to rollback
-- ============================================================================

-- ============================================================================
-- EXAMPLE 1: Migrate companies.code (VARCHAR(50) → TEXT)
-- 
-- Timeline:
--   Day 1: Run this script in dev/test
--   Day 2: Update app code to READ from code_new (still WRITE to code)
--   Day 3: Update app code to READ+WRITE from code_new
--   Day 4: Run DROP OLD COLUMN script, rename code_new → code
-- ============================================================================

-- Step 1: Create new TEXT column (day 1)
BEGIN;

ALTER TABLE accounting.companies
  ADD COLUMN code_new TEXT;

-- Copy all data from old VARCHAR column
UPDATE accounting.companies 
  SET code_new = code 
  WHERE code_new IS NULL;

-- Add constraints to match original column
ALTER TABLE accounting.companies
  ALTER COLUMN code_new SET NOT NULL;

ALTER TABLE accounting.companies
  ADD CONSTRAINT ck_companies_code_new_length 
    CHECK (LENGTH(code_new) <= 50);

-- Create index matching original if exists
CREATE INDEX IF NOT EXISTS idx_companies_code_new 
  ON accounting.companies(code_new);

-- Keep unique constraint if exists
CREATE UNIQUE INDEX IF NOT EXISTS ux_companies_code_new 
  ON accounting.companies(code_new);

COMMIT;

-- Step 2: Migrate application code (day 2-3)
-- In your Java/SQL code:
-- Old: SELECT code FROM companies WHERE ...
-- New: SELECT code_new FROM companies WHERE ...
-- 
-- Keep both reads/writes working for one day to ensure no edge cases missed

-- Step 3: Verify data consistency (day 3-4)
-- SELECT * FROM accounting.companies
-- WHERE code != code_new OR (code IS NULL AND code_new IS NOT NULL);
-- Result should be empty

-- Step 4: Drop old column and rename (day 4)
-- Run only after app update is confirmed deployed
-- BEGIN;
-- 
-- -- Drop old constraints
-- ALTER TABLE accounting.companies DROP CONSTRAINT ux_companies_code;
-- 
-- -- Drop old column
-- ALTER TABLE accounting.companies DROP COLUMN code;
-- 
-- -- Rename new column
-- ALTER TABLE accounting.companies RENAME COLUMN code_new TO code;
-- 
-- -- Rename constraint back
-- ALTER TABLE accounting.companies RENAME CONSTRAINT ck_companies_code_new_length TO ck_companies_code_length;
-- ALTER INDEX idx_companies_code_new RENAME TO idx_companies_code;
-- ALTER INDEX ux_companies_code_new RENAME TO ux_companies_code;
-- 
-- COMMIT;

-- ============================================================================
-- EXAMPLE 2: Batch migration template (for less critical columns)
-- ============================================================================

-- For multiple columns in same table without breaking changes:
-- Migrate all in one transaction if they're not independently indexed
-- 
-- BEGIN;
-- 
-- -- Add new TEXT columns
-- ALTER TABLE accounting.chart_of_accounts
--   ADD COLUMN type_new TEXT,
--   ADD COLUMN normal_side_new TEXT,
--   ADD COLUMN description_new TEXT;
-- 
-- -- Copy data
-- UPDATE accounting.chart_of_accounts 
--   SET type_new = type,
--       normal_side_new = normal_side,
--       description_new = description;
-- 
-- -- Add constraints
-- ALTER TABLE accounting.chart_of_accounts
--   ALTER COLUMN type_new SET NOT NULL,
--   ALTER COLUMN normal_side_new SET NOT NULL;
-- 
-- -- Create indexes for searchable columns
-- CREATE INDEX idx_chart_of_accounts_type_new 
--   ON accounting.chart_of_accounts(type_new);
-- 
-- COMMIT;

-- ============================================================================
-- RECOMMENDED MIGRATION ORDER
-- ============================================================================
-- 
-- BATCH 1 (HIGH priority - frequently queried):
-- - companies.code, name
-- - chart_of_accounts.code, type, normal_side
-- - vouchers.status
-- - sales_invoices.status
-- - purchase_bills.status
-- 
-- BATCH 2 (MEDIUM priority):
-- - customers.code, name
-- - suppliers.code, name
-- - bank_accounts.code
-- - All status/type columns used in filters
-- 
-- BATCH 3 (LOW priority):
-- - Description/reference/reference columns
-- - Rarely-indexed text fields
-- 

-- ============================================================================
-- VERIFICATION SCRIPT
-- ============================================================================

-- After all migrations complete, verify consistency:
SELECT 
  table_name,
  COUNT(*) as varchar_columns_remaining,
  COUNT(CASE WHEN data_type = 'text' THEN 1 END) as text_columns
FROM information_schema.columns
WHERE table_schema = 'accounting'
  AND (data_type = 'character varying' OR data_type = 'text')
GROUP BY table_name
HAVING COUNT(*) > 0
ORDER BY table_name;

-- Expected: No VARCHAR columns remaining, all migrated to TEXT

-- ============================================================================
-- PERFORMANCE IMPACT
-- ============================================================================
-- 
-- Storage impact: None (TEXT uses same storage as VARCHAR for small strings)
-- Query impact: None (CHECK constraints as fast as VARCHAR limits)
-- Migration time: 
--   - Per table: 3-5 minutes (includes data copy + index creation)
--   - Full migration: 2-4 hours spread over multiple days
-- 
-- Risk: Low (can rollback by dropping new column)
-- Downtime: Zero (dual-column approach)
-- 

-- ============================================================================
-- MAINTENANCE: Monitor migration progress
-- ============================================================================

-- Track which tables have been migrated
SELECT 
  table_name,
  CASE 
    WHEN COUNT(CASE WHEN data_type = 'character varying' THEN 1 END) = 0 THEN 'MIGRATED'
    WHEN COUNT(CASE WHEN data_type = 'text' THEN 1 END) > 0 THEN 'IN_PROGRESS'
    ELSE 'PENDING'
  END as migration_status,
  COUNT(CASE WHEN data_type = 'character varying' THEN 1 END) as varchar_columns_left,
  COUNT(CASE WHEN data_type = 'text' THEN 1 END) as text_columns
FROM information_schema.columns
WHERE table_schema = 'accounting'
  AND (data_type = 'character varying' OR data_type = 'text')
GROUP BY table_name
ORDER BY migration_status, table_name;

-- ============================================================================
-- ROLLBACK: If migration needs to be reverted
-- ============================================================================
-- 
-- Simple rollback (if new column not yet dropped):
-- 
-- -- Drop new column (all migrated data is lost, but safe)
-- ALTER TABLE <table_name> DROP COLUMN <column_name>_new CASCADE;
-- 
-- -- Application continues using original VARCHAR column
-- 
-- This is safe even in production. Very fast (just metadata change).
-- 

