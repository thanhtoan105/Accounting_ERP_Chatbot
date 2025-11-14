-- Create company_settings table for advanced company configuration
CREATE TABLE IF NOT EXISTS company_settings (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL UNIQUE,
  -- General section
  legal_name VARCHAR(255),
  short_name VARCHAR(100),
  registration_number VARCHAR(50),
  default_fiscal_year_start_month INTEGER CHECK (
    default_fiscal_year_start_month >= 1
    AND default_fiscal_year_start_month <= 12
  ),
  timezone VARCHAR(50),
  -- Localization section
  default_currency VARCHAR(3),
  currency_format VARCHAR(20),
  thousand_separator VARCHAR(1),
  decimal_separator VARCHAR(1),
  date_format VARCHAR(20),
  language VARCHAR(10),
  -- Tax & Compliance section
  vat_registration_number VARCHAR(20),
  vat_rate_presets JSONB,
  invoice_rounding_mode VARCHAR(20),
  tax_rounding_mode VARCHAR(20),
  e_invoice_enabled BOOLEAN NOT NULL DEFAULT false,
  audit_retention_period_days INTEGER CHECK (audit_retention_period_days >= 0),
  -- Numbering section (JSON config for per-document prefix/sequence)
  numbering_config JSONB,
  -- Integrations section
  bank_reconciliation_enabled BOOLEAN NOT NULL DEFAULT false,
  export_format_default VARCHAR(10),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_company_settings_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);
-- Create unique index on company_id (already enforced by UNIQUE constraint, but explicit index for performance)
CREATE UNIQUE INDEX IF NOT EXISTS ux_company_settings_company_id ON company_settings(company_id);
-- Create index on updated_at for optimistic locking queries
CREATE INDEX IF NOT EXISTS ix_company_settings_updated_at ON company_settings(updated_at);
-- Add constraint for VAT registration number format (10 digits)
ALTER TABLE company_settings
ADD CONSTRAINT ck_vat_registration_number_format CHECK (
    vat_registration_number IS NULL
    OR vat_registration_number ~ '^[0-9]{10}$'
  );
-- Add constraint for currency code format (3 uppercase letters)
ALTER TABLE company_settings
ADD CONSTRAINT ck_default_currency_format CHECK (
    default_currency IS NULL
    OR default_currency ~ '^[A-Z]{3}$'
  );