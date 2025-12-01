-- Create Metabase application database
CREATE DATABASE metabase_app_db;

-- Create read-only user for Metabase to query business data
CREATE USER accounting_ro WITH PASSWORD 'accounting_ro_pass';

-- Grant CONNECT privilege
GRANT CONNECT ON DATABASE accounting_dev TO accounting_ro;

-- Connect to accounting_dev to grant table permissions
\c accounting_dev

-- Grant USAGE on schema
GRANT USAGE ON SCHEMA public TO accounting_ro;

-- Grant SELECT on all existing tables
GRANT SELECT ON ALL TABLES IN SCHEMA public TO accounting_ro;

-- Grant SELECT on future tables (for tables created by migrations)
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO accounting_ro;

-- Grant USAGE on sequences (needed for some queries)
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO accounting_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE ON SEQUENCES TO accounting_ro;

-- Explicitly deny INSERT, UPDATE, DELETE to ensure read-only access
-- (This is redundant but makes the intent explicit)
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM accounting_ro;
