-- Create default_accounts table
CREATE TABLE IF NOT EXISTS default_accounts (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL,
  voucher_type VARCHAR(50) NOT NULL,
  entry_name VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE default_accounts
  ADD CONSTRAINT fk_default_accounts_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- CHECK constraint for status values
ALTER TABLE default_accounts
  ADD CONSTRAINT ck_default_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- CHECK constraint for voucher_type values
ALTER TABLE default_accounts
  ADD CONSTRAINT ck_default_accounts_voucher_type CHECK (voucher_type IN (
    'Cash Payment',
    'Bank Payment',
    'Cash Receipt',
    'Bank Receipt',
    'Other Business Voucher'
  ));

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_default_accounts_company_id ON default_accounts(company_id);
CREATE INDEX IF NOT EXISTS idx_default_accounts_voucher_type ON default_accounts(voucher_type);
CREATE INDEX IF NOT EXISTS idx_default_accounts_status ON default_accounts(status);
CREATE INDEX IF NOT EXISTS idx_default_accounts_entry_name ON default_accounts(entry_name);

-- Create default_account_entries table
CREATE TABLE IF NOT EXISTS default_account_entries (
  id BIGSERIAL PRIMARY KEY,
  default_account_id BIGINT NOT NULL,
  column_name VARCHAR(255) NOT NULL,
  account_id BIGINT,
  ordering_position INTEGER NOT NULL DEFAULT 0
);

-- Foreign key constraints
ALTER TABLE default_account_entries
  ADD CONSTRAINT fk_default_account_entries_default_account FOREIGN KEY (default_account_id) 
    REFERENCES default_accounts(id) ON DELETE CASCADE;

ALTER TABLE default_account_entries
  ADD CONSTRAINT fk_default_account_entries_account FOREIGN KEY (account_id) 
    REFERENCES chart_of_accounts(id);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_default_account_entries_default_account_id 
  ON default_account_entries(default_account_id);
CREATE INDEX IF NOT EXISTS idx_default_account_entries_account_id 
  ON default_account_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_default_account_entries_ordering_position 
  ON default_account_entries(default_account_id, ordering_position);

