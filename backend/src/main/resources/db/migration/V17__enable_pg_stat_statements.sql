-- Migration V17: Enable pg_stat_statements extension
-- Purpose: Enable query performance monitoring and statistics collection

-- Note: This extension is already enabled via docker/postgres-init/01_schema.sql
-- during database initialization. This migration is kept for reference and
-- in case the extension needs to be enabled in other environments.
-- 
-- If needed manually, run as superuser:
-- CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Attempt to create extension (may fail if not superuser)
-- In that case, it should already exist from init script
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
EXCEPTION WHEN insufficient_privilege THEN
    -- Extension should already exist from init script
    RAISE NOTICE 'pg_stat_statements extension requires superuser privileges. Checking if already exists...';
END $$;

