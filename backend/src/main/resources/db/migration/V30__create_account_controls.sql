-- Create account_controls table for company-specific required dimension configuration
CREATE TABLE IF NOT EXISTS account_controls (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  requires_customer BOOLEAN NOT NULL DEFAULT false,
  requires_supplier BOOLEAN NOT NULL DEFAULT false,
  requires_cost_center BOOLEAN NOT NULL DEFAULT false,
  requires_item BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_account_controls_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id) ON DELETE CASCADE,
  CONSTRAINT fk_account_controls_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT uk_account_controls_account_company UNIQUE (account_id, company_id)
);

-- Index for query performance
CREATE INDEX IF NOT EXISTS idx_account_controls_company_account ON account_controls(company_id, account_id);
CREATE INDEX IF NOT EXISTS idx_account_controls_company ON account_controls(company_id);

