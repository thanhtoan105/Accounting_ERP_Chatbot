-- Add missing fields to customers table
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS email VARCHAR(255),
  ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
  ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Drop the old unique index on code (it was global, should be per company)
DROP INDEX IF EXISTS ux_customers_company_code;

-- Create unique index on (company_id, code) for per-company code uniqueness
CREATE UNIQUE INDEX IF NOT EXISTS ux_customers_company_code ON customers(company_id, code);

-- Create unique index on (company_id, tax_code) for duplicate detection
-- Note: tax_code can be NULL, so we use a partial index
CREATE UNIQUE INDEX IF NOT EXISTS ux_customers_company_tax_code 
  ON customers(company_id, tax_code) 
  WHERE tax_code IS NOT NULL;

-- Create index for search performance
CREATE INDEX IF NOT EXISTS idx_customers_company_active ON customers(company_id, active);
CREATE INDEX IF NOT EXISTS idx_customers_company_name ON customers(company_id, name);

-- Create table to track customer code sequences per company and year
-- Format: CUST-YYYY-NNNN (e.g., CUST-2025-0001)
CREATE TABLE IF NOT EXISTS customer_code_sequences (
  company_id BIGINT NOT NULL,
  year INTEGER NOT NULL,
  last_sequence INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (company_id, year),
  FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Create index for efficient lookups
CREATE INDEX IF NOT EXISTS idx_customer_code_sequences_company_year 
  ON customer_code_sequences(company_id, year);

-- Create function to get next customer code for a company and year
-- Thread-safe using SELECT FOR UPDATE
CREATE OR REPLACE FUNCTION get_next_customer_code(company_id_value BIGINT, year_value INTEGER)
RETURNS TEXT AS $$
DECLARE
    next_seq INTEGER;
    formatted_code TEXT;
BEGIN
    -- Lock the row for this company and year (or create if it doesn't exist)
    INSERT INTO customer_code_sequences (company_id, year, last_sequence)
    VALUES (company_id_value, year_value, 0)
    ON CONFLICT (company_id, year) DO NOTHING;
    
    -- Update with locked row to ensure thread-safety
    UPDATE customer_code_sequences
    SET last_sequence = last_sequence + 1
    WHERE company_id = company_id_value AND year = year_value
    RETURNING last_sequence INTO next_seq;
    
    -- Format as CUST-YYYY-NNNN
    formatted_code := 'CUST-' || year_value || '-' || LPAD(next_seq::TEXT, 4, '0');
    
    RETURN formatted_code;
END;
$$ LANGUAGE plpgsql;

