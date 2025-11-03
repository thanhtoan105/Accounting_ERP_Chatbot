-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS accounting;

-- Set default search_path at the database level (applies to new sessions)
ALTER DATABASE accounting_dev SET search_path = accounting, public;

-- Ensure role-level default also prefers accounting
ALTER ROLE accounting SET search_path = accounting, public;
