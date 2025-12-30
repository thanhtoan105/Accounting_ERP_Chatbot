-- Function to handle Journal Entry creation/deletion on Voucher status change
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
    -- We proceed if the new status is 'posted' AND (it's a new record OR the old status was not posted)
    IF NEW.status = 'posted' THEN
        IF (TG_OP = 'INSERT') OR (TG_OP = 'UPDATE' AND (OLD.status IS NULL OR OLD.status != 'posted')) THEN
            -- Idempotency: Remove existing entries to prevent duplicates before inserting
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
                NEW.period_id,           -- Take period from Voucher header
                vl.debit,
                vl.credit,
                vl.customer_id,
                vl.supplier_id,          -- supplier_id from voucher_lines
                vl.cost_center_id,
                NEW.company_id,          -- Take company from Voucher header
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

-- Trigger definition
DROP TRIGGER IF EXISTS trg_vouchers_journal_entries ON vouchers;

CREATE TRIGGER trg_vouchers_journal_entries
AFTER INSERT OR UPDATE OF status ON vouchers
FOR EACH ROW
EXECUTE FUNCTION trg_process_journal_entries();
