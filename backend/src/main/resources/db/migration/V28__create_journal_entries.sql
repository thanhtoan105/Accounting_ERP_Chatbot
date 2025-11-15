-- Create journal_entries table
CREATE TABLE IF NOT EXISTS journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id UUID NOT NULL,
    account_id BIGINT NOT NULL,
    period_id BIGINT,
    debit_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    customer_id BIGINT,
    supplier_id BIGINT,
    cost_center_id BIGINT,
    company_id BIGINT NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
-- Foreign key constraints
ALTER TABLE journal_entries
ADD CONSTRAINT fk_journal_entries_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE CASCADE;
ALTER TABLE journal_entries
ADD CONSTRAINT fk_journal_entries_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id);
-- Period foreign key constraint (periods table may not exist yet in MVP)
-- ALTER TABLE journal_entries
--   ADD CONSTRAINT fk_journal_entries_period FOREIGN KEY (period_id) REFERENCES periods(id);
ALTER TABLE journal_entries
ADD CONSTRAINT fk_journal_entries_customer FOREIGN KEY (customer_id) REFERENCES customers(id);
-- Supplier foreign key constraint (suppliers table exists in V24)
ALTER TABLE journal_entries
ADD CONSTRAINT fk_journal_entries_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
ALTER TABLE journal_entries
ADD CONSTRAINT fk_journal_entries_company FOREIGN KEY (company_id) REFERENCES companies(id);
-- CHECK constraints for debit/credit business rules
-- Rule 1: Either debit OR credit must be 0 (mutual exclusivity)
ALTER TABLE journal_entries
ADD CONSTRAINT ck_journal_entries_debit_credit_exclusive CHECK (
        (
            debit_amount = 0
            OR credit_amount = 0
        )
    );
-- Rule 2: At least one of debit or credit must be > 0 (not both zero)
ALTER TABLE journal_entries
ADD CONSTRAINT ck_journal_entries_not_both_zero CHECK (
        (
            debit_amount > 0
            OR credit_amount > 0
        )
    );
-- Rule 3: Both debit and credit must be >= 0 (non-negative)
ALTER TABLE journal_entries
ADD CONSTRAINT ck_journal_entries_non_negative CHECK (
        (
            debit_amount >= 0
            AND credit_amount >= 0
        )
    );
-- Indexes for efficient queries (optimized for reporting)
CREATE INDEX IF NOT EXISTS idx_journal_entries_voucher ON journal_entries(voucher_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_period_account_company ON journal_entries(period_id, account_id, company_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_account ON journal_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_company_id ON journal_entries(company_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_period_id ON journal_entries(period_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_posted_at ON journal_entries(posted_at);