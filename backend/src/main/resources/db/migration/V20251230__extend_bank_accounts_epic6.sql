-- Epic 6 Story 6.1: Extend bank_accounts table for Cash/Bank Account Management
-- Adds fields: gl_account_code, opening_balance_locked, last_reconciled_date, last_reconciled_balance

-- Add GL account code column (references chart_of_accounts.code, validated in service layer)
ALTER TABLE bank_accounts
    ADD COLUMN IF NOT EXISTS gl_account_code VARCHAR(20);

-- Add opening balance lock flag (prevents modification after period close)
ALTER TABLE bank_accounts
    ADD COLUMN IF NOT EXISTS opening_balance_locked BOOLEAN NOT NULL DEFAULT FALSE;

-- Add last reconciled date for reconciliation tracking
ALTER TABLE bank_accounts
    ADD COLUMN IF NOT EXISTS last_reconciled_date DATE;

-- Add last reconciled balance for reconciliation tracking
ALTER TABLE bank_accounts
    ADD COLUMN IF NOT EXISTS last_reconciled_balance DECIMAL(19, 2);

-- Create index on gl_account_code for query performance
CREATE INDEX IF NOT EXISTS idx_bank_accounts_gl_account_code 
    ON bank_accounts(company_id, gl_account_code);

-- Create index for reconciliation queries
CREATE INDEX IF NOT EXISTS idx_bank_accounts_last_reconciled 
    ON bank_accounts(company_id, last_reconciled_date);

-- Add comments for documentation
COMMENT ON COLUMN bank_accounts.gl_account_code IS 'GL account code from chart_of_accounts. Must be a postable (leaf) cash/bank account (1111, 1112 for CASH; 1121, 1122 for BANK).';
COMMENT ON COLUMN bank_accounts.opening_balance_locked IS 'When TRUE, opening balance cannot be modified (set after first period close). Admin override requires explicit reason.';
COMMENT ON COLUMN bank_accounts.last_reconciled_date IS 'Date of the last bank reconciliation for this account.';
COMMENT ON COLUMN bank_accounts.last_reconciled_balance IS 'Bank statement balance as of the last reconciliation date.';
