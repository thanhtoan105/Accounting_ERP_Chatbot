-- Create vouchers table
CREATE TABLE IF NOT EXISTS vouchers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  voucher_number VARCHAR(50) NOT NULL,
  voucher_date DATE NOT NULL,
  period_id BIGINT,
  description VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  total_debit NUMERIC(19, 2) NOT NULL DEFAULT 0,
  total_credit NUMERIC(19, 2) NOT NULL DEFAULT 0,
  entered_by BIGINT NOT NULL,
  posted_by BIGINT,
  posted_at TIMESTAMP WITH TIME ZONE,
  reversal_of UUID,
  reversed_by BIGINT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE vouchers
  ADD CONSTRAINT fk_vouchers_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE vouchers
  ADD CONSTRAINT fk_vouchers_entered_by FOREIGN KEY (entered_by) REFERENCES users(id);

ALTER TABLE vouchers
  ADD CONSTRAINT fk_vouchers_posted_by FOREIGN KEY (posted_by) REFERENCES users(id);

ALTER TABLE vouchers
  ADD CONSTRAINT fk_vouchers_reversed_by FOREIGN KEY (reversed_by) REFERENCES users(id);

ALTER TABLE vouchers
  ADD CONSTRAINT fk_vouchers_reversal_of FOREIGN KEY (reversal_of) REFERENCES vouchers(id);

-- Unique constraint: company_id + voucher_number
CREATE UNIQUE INDEX IF NOT EXISTS ux_vouchers_company_voucher_number ON vouchers(company_id, voucher_number);

-- CHECK constraint for status values
ALTER TABLE vouchers
  ADD CONSTRAINT ck_vouchers_status CHECK (status IN ('draft', 'posted', 'unposted'));

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_vouchers_company_id ON vouchers(company_id);
CREATE INDEX IF NOT EXISTS idx_vouchers_voucher_date ON vouchers(voucher_date);
CREATE INDEX IF NOT EXISTS idx_vouchers_status ON vouchers(status);
CREATE INDEX IF NOT EXISTS idx_vouchers_period_id ON vouchers(period_id);
CREATE INDEX IF NOT EXISTS idx_vouchers_entered_by ON vouchers(entered_by);
CREATE INDEX IF NOT EXISTS idx_vouchers_posted_by ON vouchers(posted_by);
CREATE INDEX IF NOT EXISTS idx_vouchers_reversal_of ON vouchers(reversal_of);

-- Index for search on voucher_number and description (for Vietnamese unaccented search)
CREATE INDEX IF NOT EXISTS idx_vouchers_voucher_number ON vouchers(voucher_number);
CREATE INDEX IF NOT EXISTS idx_vouchers_description ON vouchers(description);

