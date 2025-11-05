-- Create voucher_lines table
CREATE TABLE IF NOT EXISTS voucher_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  voucher_id UUID NOT NULL,
  line_number INTEGER NOT NULL,
  account_id BIGINT NOT NULL,
  debit NUMERIC(19, 2) NOT NULL DEFAULT 0,
  credit NUMERIC(19, 2) NOT NULL DEFAULT 0,
  description VARCHAR(500),
  customer_id BIGINT,
  vendor_id BIGINT,
  cost_center_id BIGINT,
  item_id BIGINT,
  company_id BIGINT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE voucher_lines
  ADD CONSTRAINT fk_voucher_lines_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE CASCADE;

ALTER TABLE voucher_lines
  ADD CONSTRAINT fk_voucher_lines_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id);

ALTER TABLE voucher_lines
  ADD CONSTRAINT fk_voucher_lines_customer FOREIGN KEY (customer_id) REFERENCES customers(id);

ALTER TABLE voucher_lines
  ADD CONSTRAINT fk_voucher_lines_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- UNIQUE constraint: voucher_id + line_number
CREATE UNIQUE INDEX IF NOT EXISTS ux_voucher_lines_voucher_line_number ON voucher_lines(voucher_id, line_number);

-- CHECK constraints for debit/credit business rules
-- Rule 1: Either debit OR credit must be 0 (mutual exclusivity)
ALTER TABLE voucher_lines
  ADD CONSTRAINT ck_voucher_lines_debit_credit_exclusive CHECK ((debit = 0 OR credit = 0));

-- Rule 2: At least one of debit or credit must be > 0 (not both zero)
ALTER TABLE voucher_lines
  ADD CONSTRAINT ck_voucher_lines_not_both_zero CHECK ((debit > 0 OR credit > 0));

-- Rule 3: Both debit and credit must be >= 0 (non-negative)
ALTER TABLE voucher_lines
  ADD CONSTRAINT ck_voucher_lines_non_negative CHECK ((debit >= 0 AND credit >= 0));

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_voucher_lines_voucher ON voucher_lines(voucher_id);
CREATE INDEX IF NOT EXISTS idx_voucher_lines_account ON voucher_lines(account_id);
CREATE INDEX IF NOT EXISTS idx_voucher_lines_company_id ON voucher_lines(company_id);
CREATE INDEX IF NOT EXISTS idx_voucher_lines_customer_id ON voucher_lines(customer_id);
CREATE INDEX IF NOT EXISTS idx_voucher_lines_vendor_id ON voucher_lines(vendor_id);
CREATE INDEX IF NOT EXISTS idx_voucher_lines_cost_center_id ON voucher_lines(cost_center_id);

