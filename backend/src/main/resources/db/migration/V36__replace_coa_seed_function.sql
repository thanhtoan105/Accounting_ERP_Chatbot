-- Phase 3: Replace COA Seed Function with TT200 Template-based Seeding
-- This migration:
-- 1. Creates new seed function using tt200_chart_of_accounts_template
-- 2. Clears existing chart_of_accounts data (DB was reset)
-- 3. Seeds all companies with correct TT200 accounts
-- 4. Validates 235 accounts per company

-- ============================================
-- 3.1: Create new seed function from template
-- ============================================
CREATE OR REPLACE FUNCTION seed_tt200_coa_from_template(p_company_id BIGINT)
RETURNS VOID AS $$
DECLARE
    v_inserted_count INTEGER;
BEGIN
    -- Step 1: Insert all accounts from template for this company
    INSERT INTO chart_of_accounts (
        company_id,
        code,
        name,
        name_english,
        type,
        normal_side,
        postable,
        parent_id,
        ordering_position,
        active,
        created_at,
        updated_at
    )
    SELECT
        p_company_id,
        t.code,
        t.name_vi,
        t.name_english,
        t.type,
        t.normal_side,
        true,  -- Will be fixed in step 3
        NULL,  -- Will be set in step 2
        t.ordering,
        t.active,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    FROM tt200_chart_of_accounts_template t
    ON CONFLICT (company_id, code) DO UPDATE SET
        name = EXCLUDED.name,
        name_english = EXCLUDED.name_english,
        type = EXCLUDED.type,
        normal_side = EXCLUDED.normal_side,
        ordering_position = EXCLUDED.ordering_position,
        active = EXCLUDED.active,
        updated_at = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_inserted_count = ROW_COUNT;
    RAISE NOTICE 'Inserted/Updated % accounts for company %', v_inserted_count, p_company_id;

    -- Step 2: Set parent_id based on template's parent_code
    UPDATE chart_of_accounts ca
    SET parent_id = p.id
    FROM tt200_chart_of_accounts_template t
    JOIN chart_of_accounts p ON p.company_id = p_company_id AND p.code = t.parent_code
    WHERE ca.company_id = p_company_id
      AND ca.code = t.code
      AND t.parent_code IS NOT NULL;

    -- Step 3: Fix postable flags - accounts with children are NOT postable
    UPDATE chart_of_accounts ca1
    SET postable = false
    WHERE ca1.company_id = p_company_id
      AND EXISTS (
          SELECT 1 
          FROM chart_of_accounts ca2
          WHERE ca2.company_id = p_company_id
            AND ca2.parent_id = ca1.id
      );

    -- Step 4: Ensure leaf accounts (no children) are postable
    UPDATE chart_of_accounts ca1
    SET postable = true
    WHERE ca1.company_id = p_company_id
      AND NOT EXISTS (
          SELECT 1 
          FROM chart_of_accounts ca2
          WHERE ca2.company_id = p_company_id
            AND ca2.parent_id = ca1.id
      );

    RAISE NOTICE 'Parent IDs and postable flags updated for company %', p_company_id;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 3.2: Clear existing data and reseed
-- ============================================

-- First, we need to handle existing data properly
-- Since we have FK constraint from voucher_lines to chart_of_accounts,
-- we need to handle this carefully

-- Check if there are any voucher_lines referencing chart_of_accounts
DO $$
DECLARE
    has_voucher_lines BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM voucher_lines LIMIT 1) INTO has_voucher_lines;
    
    IF has_voucher_lines THEN
        RAISE NOTICE 'Voucher lines exist - will update existing accounts instead of deleting';
    ELSE
        -- Safe to delete all chart_of_accounts
        RAISE NOTICE 'No voucher lines found - clearing chart_of_accounts for fresh seed';
        DELETE FROM chart_of_accounts;
    END IF;
END $$;

-- ============================================
-- 3.3: Seed all existing companies
-- ============================================
DO $$
DECLARE
    company_rec RECORD;
    company_count INTEGER := 0;
BEGIN
    -- Ensure at least one company exists for dev
    IF NOT EXISTS (SELECT 1 FROM companies) THEN
        INSERT INTO companies (code, name, tax_code, address)
        VALUES ('DEFAULT', 'Default Company', '0000000000', 'Default Address');
        RAISE NOTICE 'Created default company for development';
    END IF;

    -- Seed COA for all companies
    FOR company_rec IN SELECT id, name FROM companies LOOP
        PERFORM seed_tt200_coa_from_template(company_rec.id);
        company_count := company_count + 1;
        RAISE NOTICE 'Seeded TT200 COA for company: % (ID: %)', company_rec.name, company_rec.id;
    END LOOP;

    RAISE NOTICE 'Total companies seeded: %', company_count;
END $$;

-- ============================================
-- 3.4: Validate seed data
-- ============================================
DO $$
DECLARE
    rec RECORD;
    expected_count INTEGER := 235;
BEGIN
    -- If there are no companies, skip validation
    IF NOT EXISTS (SELECT 1 FROM companies) THEN
        RAISE NOTICE 'No companies found; skipping TT200 COA validation.';
        RETURN;
    END IF;

    -- Check each company has correct number of accounts
    FOR rec IN
        SELECT c.id AS company_id, c.name AS company_name, COUNT(a.id) AS account_count
        FROM companies c
        LEFT JOIN chart_of_accounts a ON a.company_id = c.id
        GROUP BY c.id, c.name
        HAVING COUNT(a.id) < expected_count
    LOOP
        RAISE EXCEPTION 'Seed migration failed: Company "%" (ID: %) has only % accounts (expected %)', 
            rec.company_name, rec.company_id, rec.account_count, expected_count;
    END LOOP;

    -- Validate parent_id integrity
    FOR rec IN
        SELECT ca.id, ca.code, ca.company_id
        FROM chart_of_accounts ca
        WHERE ca.parent_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM chart_of_accounts p 
              WHERE p.id = ca.parent_id 
                AND p.company_id = ca.company_id
          )
    LOOP
        RAISE EXCEPTION 'Data integrity error: Account % (company %) has invalid parent_id', 
            rec.code, rec.company_id;
    END LOOP;

    RAISE NOTICE 'TT200 COA validation passed for all companies';
END $$;

-- ============================================
-- 3.5: Drop old seed function (cleanup)
-- ============================================
DROP FUNCTION IF EXISTS seed_tt200_coa_for_company(BIGINT);

-- ============================================
-- Summary statistics
-- ============================================
DO $$
DECLARE
    template_count INTEGER;
    total_accounts INTEGER;
    company_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO template_count FROM tt200_chart_of_accounts_template;
    SELECT COUNT(*) INTO total_accounts FROM chart_of_accounts;
    SELECT COUNT(*) INTO company_count FROM companies;

    RAISE NOTICE '==========================================';
    RAISE NOTICE 'TT200 Chart of Accounts Migration Summary';
    RAISE NOTICE '==========================================';
    RAISE NOTICE 'Template accounts: %', template_count;
    RAISE NOTICE 'Companies seeded: %', company_count;
    RAISE NOTICE 'Total COA records: %', total_accounts;
    RAISE NOTICE 'Expected total: % (% companies x % accounts)', 
        company_count * template_count, company_count, template_count;
    RAISE NOTICE '==========================================';
END $$;
