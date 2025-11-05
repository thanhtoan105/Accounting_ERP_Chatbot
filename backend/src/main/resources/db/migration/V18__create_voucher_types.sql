-- Create voucher_types table
CREATE TABLE IF NOT EXISTS voucher_types (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL,
  type_code VARCHAR(50) NOT NULL,
  type_name VARCHAR(255) NOT NULL,
  debit_account_id BIGINT NOT NULL,
  credit_account_id BIGINT NOT NULL,
  description VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE voucher_types
  ADD CONSTRAINT fk_voucher_types_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE voucher_types
  ADD CONSTRAINT fk_voucher_types_debit_account FOREIGN KEY (debit_account_id) REFERENCES chart_of_accounts(id);

ALTER TABLE voucher_types
  ADD CONSTRAINT fk_voucher_types_credit_account FOREIGN KEY (credit_account_id) REFERENCES chart_of_accounts(id);

-- Unique constraint: company_id + type_code
CREATE UNIQUE INDEX IF NOT EXISTS ux_voucher_types_company_type_code ON voucher_types(company_id, type_code);

-- CHECK constraint for status values
ALTER TABLE voucher_types
  ADD CONSTRAINT ck_voucher_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_voucher_types_company_id ON voucher_types(company_id);
CREATE INDEX IF NOT EXISTS idx_voucher_types_status ON voucher_types(status);
CREATE INDEX IF NOT EXISTS idx_voucher_types_type_code ON voucher_types(type_code);
CREATE INDEX IF NOT EXISTS idx_voucher_types_type_name ON voucher_types(type_name);
CREATE INDEX IF NOT EXISTS idx_voucher_types_debit_account_id ON voucher_types(debit_account_id);
CREATE INDEX IF NOT EXISTS idx_voucher_types_credit_account_id ON voucher_types(credit_account_id);




