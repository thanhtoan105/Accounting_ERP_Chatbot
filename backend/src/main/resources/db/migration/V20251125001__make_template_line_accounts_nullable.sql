-- Allow voucher template lines to have optional debit or credit account
-- At least one account must be provided (enforced by CHECK constraint)

-- Drop NOT NULL constraint from debit_account_id
ALTER TABLE voucher_template_lines
    ALTER COLUMN debit_account_id DROP NOT NULL;

-- Drop NOT NULL constraint from credit_account_id
ALTER TABLE voucher_template_lines
    ALTER COLUMN credit_account_id DROP NOT NULL;

-- Add CHECK constraint to ensure at least one account is provided
ALTER TABLE voucher_template_lines
    ADD CONSTRAINT chk_template_line_at_least_one_account
    CHECK (debit_account_id IS NOT NULL OR credit_account_id IS NOT NULL);

COMMENT ON CONSTRAINT chk_template_line_at_least_one_account ON voucher_template_lines
    IS 'Ensures each template line has at least one account (debit or credit)';
