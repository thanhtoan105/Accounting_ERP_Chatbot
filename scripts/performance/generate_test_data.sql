-- ============================================================================
-- Performance Test Data Generator
-- ============================================================================
-- Generates 50,000+ transactions for performance testing AC 8.0.4
-- Target: All widgets load in <2s P95 for datasets ≤50k transactions
--
-- Usage: psql -d accounting -f generate_test_data.sql -v company_id=1
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

-- Configuration
\set TARGET_VOUCHERS 50000
\set NUM_CUSTOMERS 1000
\set NUM_SUPPLIERS 500
\set NUM_PERIODS 12
\set START_DATE '2024-01-01'
\set END_DATE '2024-12-31'

DO $$
DECLARE
    v_company_id BIGINT;
    v_user_id BIGINT;
    v_start_time TIMESTAMPTZ;
    v_end_time TIMESTAMPTZ;
    v_customer_ids BIGINT[];
    v_supplier_ids BIGINT[];
    v_period_ids UUID[];
    v_cash_account_id BIGINT;
    v_bank_account_id BIGINT;
    v_ar_account_id BIGINT;
    v_ap_account_id BIGINT;
    v_revenue_account_id BIGINT;
    v_expense_account_id BIGINT;
    v_cogs_account_id BIGINT;
    v_i INTEGER;
    v_voucher_id UUID;
    v_period_id UUID;
    v_voucher_date DATE;
    v_amount NUMERIC(19,2);
    v_customer_id BIGINT;
    v_supplier_id BIGINT;
    v_voucher_type INTEGER;
    v_voucher_number VARCHAR(50);
    v_batch_size INTEGER := 1000;
    v_progress INTEGER := 0;
BEGIN
    v_start_time := clock_timestamp();
    
    -- Get or create test company
    SELECT id INTO v_company_id FROM companies LIMIT 1;
    IF v_company_id IS NULL THEN
        INSERT INTO companies (name, tax_code, address)
        VALUES ('Performance Test Company', '0123456789', '123 Test Street')
        RETURNING id INTO v_company_id;
        RAISE NOTICE 'Created test company with id: %', v_company_id;
    ELSE
        RAISE NOTICE 'Using existing company with id: %', v_company_id;
    END IF;
    
    -- Get or create test user
    SELECT id INTO v_user_id FROM users WHERE company_id = v_company_id LIMIT 1;
    IF v_user_id IS NULL THEN
        INSERT INTO users (company_id, email, password_hash, full_name, role, status)
        VALUES (v_company_id, 'perf-test@example.com', 'hashed_password', 'Performance Test User', 'ADMIN', 'active')
        RETURNING id INTO v_user_id;
        RAISE NOTICE 'Created test user with id: %', v_user_id;
    END IF;
    
    -- ========================================================================
    -- Step 1: Generate Customers
    -- ========================================================================
    RAISE NOTICE 'Step 1: Generating % customers...', 1000;
    
    FOR v_i IN 1..1000 LOOP
        INSERT INTO customers (company_id, code, name, tax_code, address)
        VALUES (
            v_company_id,
            'CUST-PERF-' || LPAD(v_i::TEXT, 5, '0'),
            'Test Customer ' || v_i,
            '0' || LPAD(v_i::TEXT, 9, '0'),
            'Address ' || v_i || ', Test City'
        )
        ON CONFLICT (company_id, code) DO NOTHING;
    END LOOP;
    
    SELECT ARRAY_AGG(id) INTO v_customer_ids 
    FROM customers 
    WHERE company_id = v_company_id AND code LIKE 'CUST-PERF-%';
    
    RAISE NOTICE 'Created/Found % customers', array_length(v_customer_ids, 1);
    
    -- ========================================================================
    -- Step 2: Generate Suppliers
    -- ========================================================================
    RAISE NOTICE 'Step 2: Generating % suppliers...', 500;
    
    FOR v_i IN 1..500 LOOP
        INSERT INTO suppliers (company_id, code, name, tax_code, address, email, active)
        VALUES (
            v_company_id,
            'SUP-PERF-' || LPAD(v_i::TEXT, 5, '0'),
            'Test Supplier ' || v_i,
            '1' || LPAD(v_i::TEXT, 9, '0'),
            'Supplier Address ' || v_i,
            'supplier' || v_i || '@test.com',
            TRUE
        )
        ON CONFLICT (company_id, code) DO NOTHING;
    END LOOP;
    
    SELECT ARRAY_AGG(id) INTO v_supplier_ids 
    FROM suppliers 
    WHERE company_id = v_company_id AND code LIKE 'SUP-PERF-%';
    
    RAISE NOTICE 'Created/Found % suppliers', array_length(v_supplier_ids, 1);
    
    -- ========================================================================
    -- Step 3: Generate Accounting Periods (12 months)
    -- ========================================================================
    RAISE NOTICE 'Step 3: Generating 12 accounting periods...';
    
    FOR v_i IN 1..12 LOOP
        INSERT INTO accounting_periods (
            company_id, fiscal_year, period_number, period_name,
            start_date, end_date, status
        )
        VALUES (
            v_company_id,
            2024,
            v_i,
            'Period ' || v_i || ' 2024',
            ('2024-' || LPAD(v_i::TEXT, 2, '0') || '-01')::DATE,
            (('2024-' || LPAD(v_i::TEXT, 2, '0') || '-01')::DATE + INTERVAL '1 month' - INTERVAL '1 day')::DATE,
            CASE WHEN v_i <= 11 THEN 'CLOSED' ELSE 'OPEN' END
        )
        ON CONFLICT (company_id, fiscal_year, period_number) DO NOTHING;
    END LOOP;
    
    SELECT ARRAY_AGG(id ORDER BY period_number) INTO v_period_ids
    FROM accounting_periods
    WHERE company_id = v_company_id AND fiscal_year = 2024;
    
    RAISE NOTICE 'Created/Found % periods', array_length(v_period_ids, 1);
    
    -- ========================================================================
    -- Step 4: Get Account IDs for Journal Entries
    -- ========================================================================
    RAISE NOTICE 'Step 4: Looking up chart of accounts...';
    
    -- Cash account (111)
    SELECT id INTO v_cash_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '1111' AND postable = TRUE LIMIT 1;
    IF v_cash_account_id IS NULL THEN
        SELECT id INTO v_cash_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '111%' AND postable = TRUE LIMIT 1;
    END IF;
    
    -- Bank account (112)
    SELECT id INTO v_bank_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '1121' AND postable = TRUE LIMIT 1;
    IF v_bank_account_id IS NULL THEN
        SELECT id INTO v_bank_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '112%' AND postable = TRUE LIMIT 1;
    END IF;
    
    -- AR account (131)
    SELECT id INTO v_ar_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '131' AND postable = TRUE LIMIT 1;
    IF v_ar_account_id IS NULL THEN
        SELECT id INTO v_ar_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '131%' AND postable = TRUE LIMIT 1;
    END IF;
    
    -- AP account (331)
    SELECT id INTO v_ap_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '331' AND postable = TRUE LIMIT 1;
    IF v_ap_account_id IS NULL THEN
        SELECT id INTO v_ap_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '331%' AND postable = TRUE LIMIT 1;
    END IF;
    
    -- Revenue account (511)
    SELECT id INTO v_revenue_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '5111' AND postable = TRUE LIMIT 1;
    IF v_revenue_account_id IS NULL THEN
        SELECT id INTO v_revenue_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '511%' AND postable = TRUE LIMIT 1;
    END IF;
    
    -- COGS account (632)
    SELECT id INTO v_cogs_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '632' AND postable = TRUE LIMIT 1;
    IF v_cogs_account_id IS NULL THEN
        SELECT id INTO v_cogs_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '632%' AND postable = TRUE LIMIT 1;
    END IF;
    
    -- Expense account (642)
    SELECT id INTO v_expense_account_id FROM chart_of_accounts 
    WHERE company_id = v_company_id AND code = '6421' AND postable = TRUE LIMIT 1;
    IF v_expense_account_id IS NULL THEN
        SELECT id INTO v_expense_account_id FROM chart_of_accounts 
        WHERE company_id = v_company_id AND code LIKE '642%' AND postable = TRUE LIMIT 1;
    END IF;
    
    RAISE NOTICE 'Account IDs - Cash: %, Bank: %, AR: %, AP: %, Revenue: %, COGS: %, Expense: %',
        v_cash_account_id, v_bank_account_id, v_ar_account_id, v_ap_account_id, 
        v_revenue_account_id, v_cogs_account_id, v_expense_account_id;
    
    IF v_cash_account_id IS NULL OR v_ar_account_id IS NULL OR v_revenue_account_id IS NULL THEN
        RAISE EXCEPTION 'Required accounts not found. Please ensure chart of accounts is seeded.';
    END IF;
    
    -- ========================================================================
    -- Step 5: Generate 50,000 Vouchers with Journal Entries
    -- ========================================================================
    RAISE NOTICE 'Step 5: Generating 50000 vouchers with journal entries...';
    RAISE NOTICE 'This may take several minutes. Progress updates every 5000 vouchers.';
    
    FOR v_i IN 1..50000 LOOP
        -- Random date within 2024
        v_voucher_date := '2024-01-01'::DATE + (random() * 364)::INTEGER;
        
        -- Determine period based on date
        v_period_id := v_period_ids[EXTRACT(MONTH FROM v_voucher_date)::INTEGER];
        
        -- Random amount between 100,000 and 50,000,000 VND
        v_amount := (100000 + random() * 49900000)::NUMERIC(19,2);
        
        -- Random voucher type (1-4): Sales, Purchase, Receipt, Payment
        v_voucher_type := 1 + (random() * 3)::INTEGER;
        
        -- Random customer/supplier
        v_customer_id := v_customer_ids[1 + (random() * (array_length(v_customer_ids, 1) - 1))::INTEGER];
        v_supplier_id := v_supplier_ids[1 + (random() * (array_length(v_supplier_ids, 1) - 1))::INTEGER];
        
        -- Generate voucher number
        v_voucher_number := 'PERF-' || v_voucher_type || '-' || LPAD(v_i::TEXT, 7, '0');
        
        -- Create voucher
        INSERT INTO vouchers (
            company_id, voucher_number, voucher_date, period_id,
            description, status, currency, total_debit, total_credit,
            entered_by, posted_by, posted_at
        )
        VALUES (
            v_company_id,
            v_voucher_number,
            v_voucher_date,
            v_period_id,
            CASE v_voucher_type
                WHEN 1 THEN 'Sales Invoice to ' || v_customer_id
                WHEN 2 THEN 'Purchase from ' || v_supplier_id
                WHEN 3 THEN 'Receipt from ' || v_customer_id
                WHEN 4 THEN 'Payment to ' || v_supplier_id
            END,
            'posted',
            'VND',
            v_amount,
            v_amount,
            v_user_id,
            v_user_id,
            v_voucher_date::TIMESTAMPTZ
        )
        ON CONFLICT (company_id, voucher_number) DO NOTHING
        RETURNING id INTO v_voucher_id;
        
        IF v_voucher_id IS NOT NULL THEN
            -- Create journal entries based on voucher type
            CASE v_voucher_type
                WHEN 1 THEN
                    -- Sales: Debit AR, Credit Revenue
                    INSERT INTO journal_entries (voucher_id, account_id, period_id, debit_amount, credit_amount, customer_id, company_id)
                    VALUES 
                        (v_voucher_id, v_ar_account_id, v_period_id, v_amount, 0, v_customer_id, v_company_id),
                        (v_voucher_id, v_revenue_account_id, v_period_id, 0, v_amount, v_customer_id, v_company_id);
                    
                WHEN 2 THEN
                    -- Purchase: Debit Expense/COGS, Credit AP
                    INSERT INTO journal_entries (voucher_id, account_id, period_id, debit_amount, credit_amount, supplier_id, company_id)
                    VALUES 
                        (v_voucher_id, COALESCE(v_cogs_account_id, v_expense_account_id), v_period_id, v_amount, 0, v_supplier_id, v_company_id),
                        (v_voucher_id, v_ap_account_id, v_period_id, 0, v_amount, v_supplier_id, v_company_id);
                    
                WHEN 3 THEN
                    -- Receipt: Debit Cash/Bank, Credit AR
                    INSERT INTO journal_entries (voucher_id, account_id, period_id, debit_amount, credit_amount, customer_id, company_id)
                    VALUES 
                        (v_voucher_id, COALESCE(v_bank_account_id, v_cash_account_id), v_period_id, v_amount, 0, v_customer_id, v_company_id),
                        (v_voucher_id, v_ar_account_id, v_period_id, 0, v_amount, v_customer_id, v_company_id);
                    
                WHEN 4 THEN
                    -- Payment: Debit AP, Credit Cash/Bank
                    INSERT INTO journal_entries (voucher_id, account_id, period_id, debit_amount, credit_amount, supplier_id, company_id)
                    VALUES 
                        (v_voucher_id, v_ap_account_id, v_period_id, v_amount, 0, v_supplier_id, v_company_id),
                        (v_voucher_id, COALESCE(v_bank_account_id, v_cash_account_id), v_period_id, 0, v_amount, v_supplier_id, v_company_id);
            END CASE;
        END IF;
        
        -- Progress update
        IF v_i % 5000 = 0 THEN
            v_progress := (v_i * 100 / 50000);
            RAISE NOTICE 'Progress: %% (% vouchers)', v_progress, v_i;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Voucher generation complete!';
    
    -- ========================================================================
    -- Step 6: Refresh Materialized Views
    -- ========================================================================
    RAISE NOTICE 'Step 6: Refreshing materialized views...';
    
    REFRESH MATERIALIZED VIEW mv_daily_revenue_expense;
    RAISE NOTICE '  - mv_daily_revenue_expense refreshed';
    
    REFRESH MATERIALIZED VIEW mv_ar_ap_aging;
    RAISE NOTICE '  - mv_ar_ap_aging refreshed';
    
    REFRESH MATERIALIZED VIEW mv_cash_flow_summary;
    RAISE NOTICE '  - mv_cash_flow_summary refreshed';
    
    REFRESH MATERIALIZED VIEW mv_period_summary;
    RAISE NOTICE '  - mv_period_summary refreshed';
    
    REFRESH MATERIALIZED VIEW mv_top_debtors_creditors;
    RAISE NOTICE '  - mv_top_debtors_creditors refreshed';
    
    -- ========================================================================
    -- Summary
    -- ========================================================================
    v_end_time := clock_timestamp();
    
    RAISE NOTICE '';
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'PERFORMANCE TEST DATA GENERATION COMPLETE';
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Company ID: %', v_company_id;
    RAISE NOTICE 'Customers created: %', array_length(v_customer_ids, 1);
    RAISE NOTICE 'Suppliers created: %', array_length(v_supplier_ids, 1);
    RAISE NOTICE 'Periods created: %', array_length(v_period_ids, 1);
    RAISE NOTICE 'Vouchers target: 50000';
    RAISE NOTICE 'Journal entries: ~100000 (2 per voucher)';
    RAISE NOTICE 'Total time: % seconds', EXTRACT(EPOCH FROM (v_end_time - v_start_time));
    RAISE NOTICE '============================================================';
    
END $$;

-- Final statistics
SELECT 'vouchers' as table_name, COUNT(*) as count FROM vouchers WHERE voucher_number LIKE 'PERF-%'
UNION ALL
SELECT 'journal_entries', COUNT(*) FROM journal_entries je 
    JOIN vouchers v ON je.voucher_id = v.id WHERE v.voucher_number LIKE 'PERF-%'
UNION ALL
SELECT 'customers', COUNT(*) FROM customers WHERE code LIKE 'CUST-PERF-%'
UNION ALL
SELECT 'suppliers', COUNT(*) FROM suppliers WHERE code LIKE 'SUP-PERF-%'
UNION ALL
SELECT 'mv_daily_revenue_expense', COUNT(*) FROM mv_daily_revenue_expense
UNION ALL
SELECT 'mv_ar_ap_aging', COUNT(*) FROM mv_ar_ap_aging
UNION ALL
SELECT 'mv_cash_flow_summary', COUNT(*) FROM mv_cash_flow_summary
UNION ALL
SELECT 'mv_period_summary', COUNT(*) FROM mv_period_summary
UNION ALL
SELECT 'mv_top_debtors_creditors', COUNT(*) FROM mv_top_debtors_creditors;
