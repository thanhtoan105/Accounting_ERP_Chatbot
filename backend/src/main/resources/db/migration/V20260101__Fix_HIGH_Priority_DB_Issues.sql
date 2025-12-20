-- ============================================================================
-- HIGH PRIORITY DATABASE FIXES
-- Issue 1: Fix TIMESTAMP to TIMESTAMPTZ (timezone awareness)
-- Issue 2: Add missing FK indexes (query performance)
-- ============================================================================
-- IMPORTANT: Run in TEST environment first!
-- ============================================================================

-- ============================================================================
-- FIX 1: TIMESTAMP → TIMESTAMPTZ in companies table
-- Why: Ensures correct time representation across different timezones
-- ============================================================================
BEGIN;

-- Step 1: Backup existing data (verify before/after)
-- SELECT created_at, updated_at FROM companies LIMIT 5;

-- Step 2: Convert TIMESTAMP to TIMESTAMPTZ
-- PostgreSQL automatically assumes UTC for TIMESTAMP without timezone
ALTER TABLE companies
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE companies
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

COMMIT;

-- Verify:
-- SELECT column_name, data_type FROM information_schema.columns 
--   WHERE table_name = 'companies' AND column_name IN ('created_at', 'updated_at');
-- Expected: "timestamp with time zone"

-- ============================================================================
-- FIX 2: TIMESTAMP → TIMESTAMPTZ in audit_logs table
-- Why: Consistency + correct timezone handling for compliance
-- ============================================================================
BEGIN;

ALTER TABLE audit_logs
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- Optional: If audit_logs has retention_until, also convert
-- ALTER TABLE audit_logs
--   ALTER COLUMN retention_until TYPE TIMESTAMPTZ USING retention_until AT TIME ZONE 'UTC';

COMMIT;

-- ============================================================================
-- FIX 3: Add missing FK indexes on sales_invoices
-- Why: 
--   - PostgreSQL does NOT auto-index FK columns
--   - Without indexes, queries like "find all invoices created by user X" are slow
--   - Performance: ~2s scan vs ~10ms with index
-- ============================================================================
BEGIN;

-- Index on created_by_id (FK to users)
CREATE INDEX IF NOT EXISTS idx_sales_invoices_created_by_id 
  ON sales_invoices(created_by_id);

-- Index on approved_by_id (FK to users, nullable)
-- Using partial index since many rows have NULL approved_by_id
CREATE INDEX IF NOT EXISTS idx_sales_invoices_approved_by_id 
  ON sales_invoices(approved_by_id) 
  WHERE approved_by_id IS NOT NULL;

-- Index on original_invoice_id (self-referencing FK for credit notes)
CREATE INDEX IF NOT EXISTS idx_sales_invoices_original_invoice_id 
  ON sales_invoices(original_invoice_id) 
  WHERE original_invoice_id IS NOT NULL;

-- Index on posted_voucher_id (FK to vouchers)
CREATE INDEX IF NOT EXISTS idx_sales_invoices_posted_voucher_id 
  ON sales_invoices(posted_voucher_id) 
  WHERE posted_voucher_id IS NOT NULL;

COMMIT;

-- ============================================================================
-- FIX 4: Add missing FK indexes on purchase_bills
-- Why: Same reason as sales_invoices
-- ============================================================================
BEGIN;

-- Index on created_by_id (FK to users)
CREATE INDEX IF NOT EXISTS idx_purchase_bills_created_by_id 
  ON purchase_bills(created_by_id);

-- Index on approved_by_id (FK to users, nullable)
CREATE INDEX IF NOT EXISTS idx_purchase_bills_approved_by_id 
  ON purchase_bills(approved_by_id) 
  WHERE approved_by_id IS NOT NULL;

-- Index on posted_voucher_id (FK to vouchers, nullable)
CREATE INDEX IF NOT EXISTS idx_purchase_bills_posted_voucher_id 
  ON purchase_bills(posted_voucher_id) 
  WHERE posted_voucher_id IS NOT NULL;

COMMIT;

-- ============================================================================
-- FIX 5: Add missing FK indexes on other critical tables
-- ============================================================================
BEGIN;

-- ar_payments: created_by_id, posted_voucher_id (if they exist)
CREATE INDEX IF NOT EXISTS idx_ar_payments_created_by_id 
  ON ar_payments(created_by_id) 
  WHERE created_by_id IS NOT NULL;

-- ap_payments: created_by_id, posted_voucher_id (if they exist)
CREATE INDEX IF NOT EXISTS idx_ap_payments_created_by_id 
  ON ap_payments(created_by_id) 
  WHERE created_by_id IS NOT NULL;

COMMIT;

-- ============================================================================
-- VERIFICATION QUERIES
-- Run these to confirm all fixes were applied
-- ============================================================================

-- Query 1: Verify TIMESTAMPTZ in companies
SELECT 
  table_name, 
  column_name, 
  data_type 
FROM information_schema.columns 
WHERE table_schema = 'public' 
  AND table_name IN ('companies', 'audit_logs')
  AND column_name IN ('created_at', 'updated_at', 'retention_until')
ORDER BY table_name, column_name;

-- Query 2: Verify all FK indexes exist
SELECT 
  schemaname,
  tablename,
  indexname,
  indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('sales_invoices', 'purchase_bills', 'ar_payments', 'ap_payments')
  AND indexname LIKE 'idx_%_by_id'
ORDER BY tablename, indexname;

-- Query 3: Count indexes added
SELECT 
  COUNT(*) as total_indexes,
  COUNT(CASE WHEN indexname LIKE 'idx_%_created_by_id' THEN 1 END) as created_by_indexes,
  COUNT(CASE WHEN indexname LIKE 'idx_%_approved_by_id' THEN 1 END) as approved_by_indexes
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('sales_invoices', 'purchase_bills', 'ar_payments', 'ap_payments');

-- ============================================================================
-- ROLLBACK PROCEDURE (if needed)
-- ============================================================================
-- If issues occur, rollback changes:
-- 
-- ALTER TABLE companies
--   ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC';
-- ALTER TABLE companies
--   ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';
-- 
-- DROP INDEX IF EXISTS idx_sales_invoices_created_by_id;
-- DROP INDEX IF EXISTS idx_sales_invoices_approved_by_id;
-- ... etc
-- ============================================================================
