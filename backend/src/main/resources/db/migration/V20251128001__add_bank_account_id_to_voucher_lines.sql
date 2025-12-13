-- Migration: Add bank_account_id to voucher_lines for detailed bank account tracking
-- This follows MISA accounting pattern where transactions on TK 1121/1122 are tracked by specific bank account

-- Add bank_account_id column (nullable - only used for bank/cash accounts)
ALTER TABLE voucher_lines 
ADD COLUMN IF NOT EXISTS bank_account_id BIGINT;

-- Add foreign key constraint
ALTER TABLE voucher_lines
ADD CONSTRAINT fk_voucher_lines_bank_account 
FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);

-- Add index for performance (partial index for non-null values only)
CREATE INDEX IF NOT EXISTS idx_voucher_lines_bank_account 
ON voucher_lines(bank_account_id) WHERE bank_account_id IS NOT NULL;

-- Composite index for cash book queries
CREATE INDEX IF NOT EXISTS idx_voucher_lines_company_bank_account 
ON voucher_lines(company_id, bank_account_id) WHERE bank_account_id IS NOT NULL;

COMMENT ON COLUMN voucher_lines.bank_account_id IS 
'Bank account ID for detailed tracking. Used when account is 1121, 1122, etc. (Theo dõi chi tiết theo tài khoản ngân hàng - giống MISA)';
