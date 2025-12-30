-- Update trigger function to use supplier_id instead of vendor_id
-- This is needed because vendor_id was renamed to supplier_id in V20251228003

CREATE OR REPLACE FUNCTION trg_process_journal_entries()
RETURNS TRIGGER AS $$
BEGIN
    -- CASE 1: Handle UNPOSTING (UPDATE only: Posted -> Not Posted)
    IF (TG_OP = 'UPDATE') THEN
        IF OLD.status = 'posted' AND NEW.status != 'posted' THEN
            DELETE FROM journal_entries WHERE voucher_id = NEW.id;
        END IF;
    END IF;

    -- CASE 2: Handle POSTING (INSERT or UPDATE: -> Posted)
    IF NEW.status = 'posted' THEN
        IF (TG_OP = 'INSERT') OR (TG_OP = 'UPDATE' AND (OLD.status IS NULL OR OLD.status != 'posted')) THEN
            -- Idempotency: Remove existing entries to prevent duplicates
            DELETE FROM journal_entries WHERE voucher_id = NEW.id;

            -- Insert new journal entries from voucher lines
            INSERT INTO journal_entries (
                voucher_id,
                account_id,
                period_id,
                debit_amount,
                credit_amount,
                customer_id,
                supplier_id,
                cost_center_id,
                company_id,
                posted_at,
                created_at,
                updated_at
            )
            SELECT
                vl.voucher_id,
                vl.account_id,
                NEW.period_id,
                vl.debit,
                vl.credit,
                vl.customer_id,
                vl.supplier_id,          -- Now using supplier_id (renamed from vendor_id)
                vl.cost_center_id,
                NEW.company_id,
                COALESCE(NEW.posted_at, CURRENT_TIMESTAMP),
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM voucher_lines vl
            WHERE vl.voucher_id = NEW.id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
