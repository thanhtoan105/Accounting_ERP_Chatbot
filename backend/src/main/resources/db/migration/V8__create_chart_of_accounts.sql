-- Enable unaccent extension for Vietnamese search support
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Create chart_of_accounts table
CREATE TABLE IF NOT EXISTS chart_of_accounts (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  normal_side VARCHAR(10) NOT NULL,
  postable BOOLEAN NOT NULL DEFAULT false,
  parent_id BIGINT,
  ordering_position INTEGER NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE chart_of_accounts
  ADD CONSTRAINT fk_chart_of_accounts_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE chart_of_accounts
  ADD CONSTRAINT fk_chart_of_accounts_parent FOREIGN KEY (parent_id) REFERENCES chart_of_accounts(id);

-- Unique constraint: company_id + code
CREATE UNIQUE INDEX IF NOT EXISTS ux_chart_of_accounts_company_code ON chart_of_accounts(company_id, code);

-- Index for parent_id for efficient tree queries
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_parent_id ON chart_of_accounts(parent_id);

-- Index for company_id for efficient filtering
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_company_id ON chart_of_accounts(company_id);

-- Index for code for efficient code prefix searches
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_code ON chart_of_accounts(code);

-- Index for postable flag
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_postable ON chart_of_accounts(postable);

-- Index for type
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_type ON chart_of_accounts(type);

-- Create function for unaccented search (for Vietnamese with accents)
CREATE OR REPLACE FUNCTION unaccent_search(text_value TEXT)
RETURNS TEXT AS $$
BEGIN
  RETURN unaccent('unaccent', text_value);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Add comment for documentation
COMMENT ON TABLE chart_of_accounts IS 'Chart of Accounts following TT200 standards. Supports hierarchical account structure with company scoping.';
COMMENT ON COLUMN chart_of_accounts.postable IS 'TRUE only for leaf accounts (accounts with no children). Parent accounts are non-postable.';
COMMENT ON COLUMN chart_of_accounts.normal_side IS 'Debit or Credit - auto-determined from account category per TT200 standards.';
