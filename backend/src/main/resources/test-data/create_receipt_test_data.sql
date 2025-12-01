-- Test Data for Receipt Module Testing
-- This script creates test customers and sales invoices for receipt allocation testing
-- Run this MANUALLY via psql or pgAdmin, NOT as a Flyway migration
--
-- Usage:
--   psql -U your_user -d accounting_db -f backend/src/main/resources/test-data/create_receipt_test_data.sql
--   OR run in pgAdmin Query Tool

-- Step 1: Create a test customer (if not exists)
DO $$
DECLARE
    test_company_id BIGINT;
    test_customer_id BIGINT;
    test_invoice_id UUID;
    test_user_id BIGINT;
BEGIN
    -- Get the first company ID (adjust as needed)
    SELECT id INTO test_company_id FROM accounting.companies LIMIT 1;
    
    IF test_company_id IS NULL THEN
        RAISE EXCEPTION 'No company found. Please create a company first.';
    END IF;
    
    -- Get the first user ID (for created_by fields)
    SELECT id INTO test_user_id FROM accounting.users WHERE company_id = test_company_id LIMIT 1;
    
    IF test_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for company. Please create a user first.';
    END IF;
    
    -- Create test customer "Test Customer for Receipts" if not exists
    INSERT INTO accounting.customers (company_id, code, name, tax_code, email, phone, address, active, created_at, updated_at)
    VALUES (
        test_company_id,
        'CUST-RECEIPT-TEST',
        'Test Customer for Receipts',
        '1234567890',
        'test-customer@example.com',
        '0123456789',
        '123 Test Street, Test City',
        true,  -- active = true (boolean, not 'ACTIVE' string)
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (company_id, code) DO NOTHING
    RETURNING id INTO test_customer_id;
    
    -- If customer already exists, get its ID
    IF test_customer_id IS NULL THEN
        SELECT id INTO test_customer_id FROM accounting.customers 
        WHERE company_id = test_company_id AND code = 'CUST-RECEIPT-TEST';
    END IF;
    
    -- Create test sales invoice 1: 10,000,000 VND (for partial allocation test)
    INSERT INTO accounting.sales_invoices (
        company_id,
        customer_id,
        invoice_number,
        invoice_date,
        due_date,
        total_amount,
        tax_amount,
        amount_paid,
        remaining_balance,
        status,
        created_by,
        created_at,
        updated_at
    )
    VALUES (
        test_company_id,
        test_customer_id,
        'INV-TEST-001',
        CURRENT_DATE - INTERVAL '30 days',
        CURRENT_DATE + INTERVAL '30 days',
        10000000.00,  -- 10M VND
        0.00,
        0.00,
        10000000.00,  -- Full amount remaining
        'POSTED',     -- Must be POSTED to have open invoice
        test_user_id,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT DO NOTHING
    RETURNING id INTO test_invoice_id;
    
    -- Create test sales invoice 2: 5,000,000 VND (for multiple allocation test)
    INSERT INTO accounting.sales_invoices (
        company_id,
        customer_id,
        invoice_number,
        invoice_date,
        due_date,
        total_amount,
        tax_amount,
        amount_paid,
        remaining_balance,
        status,
        created_by,
        created_at,
        updated_at
    )
    VALUES (
        test_company_id,
        test_customer_id,
        'INV-TEST-002',
        CURRENT_DATE - INTERVAL '20 days',
        CURRENT_DATE + INTERVAL '40 days',
        5000000.00,  -- 5M VND
        0.00,
        0.00,
        5000000.00,  -- Full amount remaining
        'POSTED',    -- Must be POSTED to have open invoice
        test_user_id,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT DO NOTHING;
    
    -- Create test sales invoice 3: 15,000,000 VND (for high-value test)
    INSERT INTO accounting.sales_invoices (
        company_id,
        customer_id,
        invoice_number,
        invoice_date,
        due_date,
        total_amount,
        tax_amount,
        amount_paid,
        remaining_balance,
        status,
        created_by,
        created_at,
        updated_at
    )
    VALUES (
        test_company_id,
        test_customer_id,
        'INV-TEST-003',
        CURRENT_DATE - INTERVAL '10 days',
        CURRENT_DATE + INTERVAL '50 days',
        15000000.00,  -- 15M VND
        0.00,
        0.00,
        15000000.00,  -- Full amount remaining
        'POSTED',     -- Must be POSTED to have open invoice
        test_user_id,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT DO NOTHING;
    
    RAISE NOTICE 'Test data created successfully!';
    RAISE NOTICE 'Customer ID: %', test_customer_id;
    RAISE NOTICE 'Company ID: %', test_company_id;
    RAISE NOTICE 'You can now use customer code "CUST-RECEIPT-TEST" for receipt testing';
    
END $$;

-- Verify test data was created
SELECT 
    c.code AS customer_code,
    c.name AS customer_name,
    i.invoice_number,
    i.total_amount,
    i.remaining_balance,
    i.status
FROM accounting.customers c
JOIN accounting.sales_invoices i ON c.id = i.customer_id
WHERE c.code = 'CUST-RECEIPT-TEST'
ORDER BY i.invoice_number;

