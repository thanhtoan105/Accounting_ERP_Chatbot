CREATE TABLE IF NOT EXISTS companies (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(16) NOT NULL,
  name VARCHAR(255) NOT NULL,
  tax_code VARCHAR(10) NOT NULL,
  address VARCHAR(512) NOT NULL,
  logo_url VARCHAR(512),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_companies_code ON companies(code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_companies_tax_code ON companies(tax_code);

ALTER TABLE companies DROP CONSTRAINT IF EXISTS ck_tax_code_digits;
ALTER TABLE companies ADD CONSTRAINT ck_tax_code_digits CHECK (tax_code ~ '^[0-9]{10}$');


