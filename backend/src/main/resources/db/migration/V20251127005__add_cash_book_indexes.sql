-- Migration V20251127005: Add indexes for Cash Book / Bank Book queries (AC6.4-07)
-- Optimizes per-account ledger view, running balance calculation, and multi-account summary

-- ============================================================================
-- VOUCHER_LINES: Optimize for cash book account queries
-- ============================================================================

-- For finding all voucher lines for a specific GL account (cash book per-account view)
-- Covers: SELECT from voucher_lines WHERE company_id = ? AND account_id IN (SELECT id FROM chart_of_accounts WHERE code = ?)
CREATE INDEX IF NOT EXISTS idx_voucher_lines_company_account 
ON accounting.voucher_lines (company_id, account_id);

-- ============================================================================
-- VOUCHERS: Additional indexes for cash book date range queries
-- ============================================================================

-- For cash book date range filtering with status (most common cash book query)
-- Covers: SELECT from vouchers WHERE company_id = ? AND voucher_date BETWEEN ? AND ? AND status = 'posted'
-- Note: idx_vouchers_company_date_status already exists but we ensure it's optimal
CREATE INDEX IF NOT EXISTS idx_vouchers_company_status_date 
ON accounting.vouchers (company_id, status, voucher_date);

-- ============================================================================
-- CHART_OF_ACCOUNTS: Optimize for GL code lookup (cash book needs to map bank account to GL code)
-- ============================================================================

-- For finding account by GL code (bank account -> GL account mapping)
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_company_code 
ON accounting.chart_of_accounts (company_id, code);

-- ============================================================================
-- BANK_ACCOUNTS: Optimize for cash book queries
-- ============================================================================

-- For listing active bank accounts for cash book summary
CREATE INDEX IF NOT EXISTS idx_bank_accounts_company_active 
ON accounting.bank_accounts (company_id, active) WHERE active = true;

-- For finding bank account by GL code (cash book summary view)
CREATE INDEX IF NOT EXISTS idx_bank_accounts_company_gl_code 
ON accounting.bank_accounts (company_id, gl_account_code) WHERE gl_account_code IS NOT NULL;

-- ============================================================================
-- COMPOSITE INDEX for optimal cash book query path
-- ============================================================================

-- For the main cash book query that joins vouchers with voucher_lines
-- This covers: voucher_lines JOIN vouchers ON voucher_id WHERE company_id AND account_id AND voucher_date range
CREATE INDEX IF NOT EXISTS idx_voucher_lines_company_account_voucher 
ON accounting.voucher_lines (company_id, account_id, voucher_id);

-- ============================================================================
-- ANALYZE tables to update statistics for query planner
-- ============================================================================
ANALYZE accounting.voucher_lines;
ANALYZE accounting.vouchers;
ANALYZE accounting.chart_of_accounts;
ANALYZE accounting.bank_accounts;
