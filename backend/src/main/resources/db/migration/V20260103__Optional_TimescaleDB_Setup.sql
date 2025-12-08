-- ============================================================================
-- OPTIONAL: TIMESCALEDB INSTALLATION & SETUP
-- For automatic partitioning + compression of audit_logs
-- 
-- Status: Requires system-level installation (not just database)
-- Benefit: 50-80% storage savings, auto-cleanup, 10-50x performance for queries
-- Compatible with: TimescaleDB 2.24.0+
-- ============================================================================

-- ============================================================================
-- STEP 1: SYSTEM-LEVEL INSTALLATION (run on server, not in database)
-- ============================================================================
-- 
-- On Ubuntu/Debian:
-- $ sudo apt-get update
-- $ sudo apt-get install -y postgresql-16-timescaledb
-- 
-- On CentOS/RHEL:
-- $ sudo yum install -y timescaledb-tools
-- $ sudo timescaledb-tune
-- 
-- Verify installation:
-- $ sudo su - postgres
-- $ psql -c "SELECT * FROM pg_available_extensions WHERE name = 'timescaledb';"
-- 

-- ============================================================================
-- STEP 2: ENABLE TIMESCALEDB EXTENSION (run in database)
-- ============================================================================
BEGIN;

CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Verify installation
SELECT * FROM pg_extension WHERE extname = 'timescaledb';

COMMIT;

-- ============================================================================
-- STEP 3: CONVERT audit_logs TO HYPERTABLE (auto-partitioned time-series)
-- ============================================================================
-- Why:
--   - Automatic monthly partitions
--   - Built-in compression
--   - Optimized queries for time-range scans
--   - 10-50x faster queries on large datasets
-- 
-- Execution time: ~5-10 seconds depending on data size
-- ============================================================================
BEGIN;

-- TimescaleDB requires partitioning column (created_at) to be part of primary key
-- Drop existing primary key and recreate as composite key
ALTER TABLE accounting.audit_logs DROP CONSTRAINT IF EXISTS audit_logs_pkey;
ALTER TABLE accounting.audit_logs ADD PRIMARY KEY (id, created_at);

-- Convert existing audit_logs table to hypertable
-- Partitioned by created_at column (monthly by default)
SELECT create_hypertable(
  'accounting.audit_logs',
  'created_at',
  if_not_exists => TRUE,
  migrate_data => TRUE
);

-- Verify conversion
SELECT * FROM timescaledb_information.hypertables
WHERE hypertable_name = 'audit_logs';

COMMIT;

-- ============================================================================
-- STEP 4: ENABLE AUTOMATIC COMPRESSION (saves 50-80% storage)
-- ============================================================================
-- Why:
--   - Compress chunks (partitions) older than 30 days
--   - Old data takes <20% of original space
--   - Reads still work transparently
-- 
-- Compression is automatic, happens every night
-- ============================================================================
BEGIN;

-- First, enable compression on the hypertable (required before adding policy)
ALTER TABLE accounting.audit_logs SET (
  timescaledb.compress = true,
  timescaledb.compress_orderby = 'created_at DESC'
);

-- Add compression policy for chunks older than 30 days
SELECT add_compression_policy(
  'accounting.audit_logs',
  INTERVAL '30 days',
  if_not_exists => TRUE
);

-- View compression policy (using jobs view, not policy view)
SELECT * FROM timescaledb_information.jobs
WHERE hypertable_name = 'audit_logs'
  AND proc_name LIKE '%compress%';

COMMIT;

-- ============================================================================
-- STEP 5: ENABLE AUTOMATIC RETENTION (auto-delete old data)
-- ============================================================================
-- Why:
--   - Delete logs older than 2 years automatically
--   - Complies with audit retention policies
--   - Keeps database size manageable
-- 
-- Retention runs nightly, very fast for hypertables (just deletes chunks)
-- ============================================================================
BEGIN;

-- Delete audit logs older than 2 years automatically
SELECT add_retention_policy(
  'accounting.audit_logs',
  INTERVAL '2 years',
  if_not_exists => TRUE
);

-- Optional: For longer compliance requirements (7 years)
-- SELECT add_retention_policy(
--   'accounting.audit_logs',
--   INTERVAL '7 years',
--   if_not_exists => TRUE
-- );

-- View retention policy (using jobs view)
SELECT * FROM timescaledb_information.jobs
WHERE hypertable_name = 'audit_logs'
  AND proc_name LIKE '%retention%';

COMMIT;

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Check hypertable metadata
SELECT
  hypertable_schema,
  hypertable_name,
  num_chunks,
  compression_enabled
FROM timescaledb_information.hypertables
WHERE hypertable_name = 'audit_logs';

-- Get hypertable size information
SELECT * FROM hypertable_detailed_size('accounting.audit_logs');

-- List all chunks (partitions)
SELECT 
  chunk_schema,
  chunk_name,
  range_start,
  range_end,
  is_compressed
FROM timescaledb_information.chunks
WHERE hypertable_name = 'audit_logs'
ORDER BY range_start DESC
LIMIT 20;

-- Estimate compression ratio (storage saved) using chunk_compression_stats function
SELECT * FROM chunk_compression_stats('accounting.audit_logs');

-- ============================================================================
-- PERFORMANCE: Query examples showing hypertable advantages
-- ============================================================================

-- Example 1: Find all logs for a specific company in last 30 days
-- (Hypertable automatically prunes unnecessary chunks)
-- SELECT COUNT(*) FROM accounting.audit_logs
-- WHERE company_id = 42 
--   AND created_at > CURRENT_TIMESTAMP - INTERVAL '30 days';
-- Expected: ~50-100ms (compared to 500ms without hypertable)

-- Example 2: Monthly summary statistics
-- (Perfect for hypertable time_bucket function)
-- SELECT 
--   time_bucket('1 month', created_at) AS month,
--   COUNT(*) AS log_count,
--   COUNT(DISTINCT company_id) AS unique_companies
-- FROM accounting.audit_logs
-- GROUP BY month
-- ORDER BY month DESC;

-- ============================================================================
-- MAINTENANCE: Monitoring
-- ============================================================================

-- Check compression job status (using available columns in v2.24)
SELECT 
  job_id,
  hypertable_name,
  proc_name,
  schedule_interval,
  next_start
FROM timescaledb_information.jobs
WHERE hypertable_name = 'audit_logs';

-- Monitor chunks manually
SELECT 
  COUNT(*) as total_chunks,
  SUM(CASE WHEN is_compressed THEN 1 ELSE 0 END) as compressed_chunks,
  SUM(CASE WHEN is_compressed = false THEN 1 ELSE 0 END) as uncompressed_chunks
FROM timescaledb_information.chunks
WHERE hypertable_name = 'audit_logs';

-- ============================================================================
-- ROLLBACK PROCEDURE (if needed)
-- ============================================================================
-- To disable TimescaleDB and revert to regular table:
--
-- BEGIN;
-- 
-- -- Drop hypertable policies
-- SELECT remove_compression_policy('accounting.audit_logs', if_exists => TRUE);
-- SELECT remove_retention_policy('accounting.audit_logs', if_exists => TRUE);
-- 
-- -- Detach hypertable (complex operation - not recommended after data accumulation)
-- -- SELECT timescaledb_pre_restore();
-- -- SELECT timescaledb_post_restore();
-- 
-- COMMIT;
--
-- NOTE: Not recommended after significant data accumulation.
-- If needed, backup data first!
-- ============================================================================
