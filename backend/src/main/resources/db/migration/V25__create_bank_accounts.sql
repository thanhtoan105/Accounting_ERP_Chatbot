-- Create bank_accounts table
CREATE TABLE IF NOT EXISTS bank_accounts (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL,
  account_number VARCHAR(50) NOT NULL,
  bank_name VARCHAR(255) NOT NULL,
  branch VARCHAR(255),
  type VARCHAR(10) NOT NULL CHECK (type IN ('CASH', 'BANK')),
  opening_balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraint
ALTER TABLE bank_accounts
  ADD CONSTRAINT fk_bank_accounts_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- Unique index on (company_id, account_number) for per-company account number uniqueness
CREATE UNIQUE INDEX IF NOT EXISTS ux_bank_accounts_company_account_number 
  ON bank_accounts(company_id, account_number);

-- Create indexes for search performance
CREATE INDEX IF NOT EXISTS idx_bank_accounts_company_active ON bank_accounts(company_id, active);
CREATE INDEX IF NOT EXISTS idx_bank_accounts_company_type ON bank_accounts(company_id, type);
CREATE INDEX IF NOT EXISTS idx_bank_accounts_company_bank_name ON bank_accounts(company_id, bank_name);

