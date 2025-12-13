-- Phase 1: Schema Enhancement for TT200 Compliance
-- Add support for "Lưỡng tính" (Both) normal_side and enforce data integrity

-- 1.1: First, we need to widen normal_side to support 'Both' if not already done
-- V20 already widened it, but let's ensure it can hold 'Both'
ALTER TABLE chart_of_accounts
    ALTER COLUMN normal_side TYPE VARCHAR(10);

-- 1.2: Add CHECK constraint for normal_side values
-- Drop existing constraint if any (idempotent)
ALTER TABLE chart_of_accounts
    DROP CONSTRAINT IF EXISTS chk_chart_of_accounts_normal_side;

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chk_chart_of_accounts_normal_side
    CHECK (normal_side IN ('Debit', 'Credit', 'Both'));

-- 1.3: Create trigger to enforce parent_id belongs to same company_id
CREATE OR REPLACE FUNCTION coa_parent_same_company()
RETURNS trigger AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL THEN
        PERFORM 1
        FROM chart_of_accounts p
        WHERE p.id = NEW.parent_id
          AND p.company_id = NEW.company_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Parent account % must belong to same company %',
                NEW.parent_id, NEW.company_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_coa_parent_same_company ON chart_of_accounts;

CREATE TRIGGER trg_coa_parent_same_company
    BEFORE INSERT OR UPDATE ON chart_of_accounts
    FOR EACH ROW EXECUTE FUNCTION coa_parent_same_company();

-- 1.4: Update column comments for TT200 compliance
COMMENT ON COLUMN chart_of_accounts.normal_side IS 
    'Account normal balance: Debit (Dư Nợ), Credit (Dư Có), or Both (Lưỡng tính) as per TT200/2014/TT-BTC';

COMMENT ON TABLE chart_of_accounts IS 
    'Chart of Accounts following TT200/2014/TT-BTC Vietnamese Accounting Standards. Supports hierarchical account structure with company scoping.';
