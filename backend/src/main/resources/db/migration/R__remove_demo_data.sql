-- Rollback script: remove demo data seeded for the DEMO company
DO $$
DECLARE
  v_company_id BIGINT;
BEGIN
  SELECT id INTO v_company_id FROM companies WHERE code = 'DEMO' OR name LIKE '%[DEMO]';

  IF v_company_id IS NULL THEN
    RAISE NOTICE 'No DEMO company found. Nothing to rollback.';
    RETURN;
  END IF;

  -- Delete users of demo company
  DELETE FROM users WHERE company_id = v_company_id;

  -- Delete chart of accounts entries for demo company
  DELETE FROM chart_of_accounts WHERE company_id = v_company_id;

  -- Finally delete the demo company
  DELETE FROM companies WHERE id = v_company_id;

  RAISE NOTICE 'Demo data removed for company id %', v_company_id;
END $$;


