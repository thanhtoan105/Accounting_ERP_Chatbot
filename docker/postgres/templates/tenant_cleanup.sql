-- ============================================================================
-- Tenant Analytics Cleanup SQL Template
-- ============================================================================
-- Removes all database-level isolation for a tenant when deleting/deactivating.
-- Parameters (to be replaced by application):
--   :company_id - The company ID (BIGINT)
-- ============================================================================

-- ============================================================================
-- 1. Drop tenant schema (CASCADE drops all views)
-- ============================================================================
DO $$
DECLARE
    v_schema_name TEXT := 'mb_company_:company_id';
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = v_schema_name) THEN
        EXECUTE format('DROP SCHEMA %I CASCADE', v_schema_name);
        RAISE NOTICE 'Dropped schema: %', v_schema_name;
    ELSE
        RAISE NOTICE 'Schema % does not exist, skipping', v_schema_name;
    END IF;
END $$;

-- ============================================================================
-- 2. Drop tenant role
-- ============================================================================
DO $$
DECLARE
    v_role_name TEXT := 'mb_company_:company_id_ro';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_role_name) THEN
        -- Revoke all privileges first
        EXECUTE format('REVOKE ALL PRIVILEGES ON DATABASE %I FROM %I', current_database(), v_role_name);
        
        -- Drop the role
        EXECUTE format('DROP ROLE %I', v_role_name);
        RAISE NOTICE 'Dropped role: %', v_role_name;
    ELSE
        RAISE NOTICE 'Role % does not exist, skipping', v_role_name;
    END IF;
END $$;
