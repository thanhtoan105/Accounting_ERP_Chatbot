-- Create suppliers table
CREATE TABLE IF NOT EXISTS suppliers (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(255) NOT NULL,
  tax_code VARCHAR(20),
  address VARCHAR(512),
  email VARCHAR(255),
  phone VARCHAR(20),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraint
ALTER TABLE suppliers
  ADD CONSTRAINT fk_suppliers_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- Unique index on (company_id, code) for per-company code uniqueness
CREATE UNIQUE INDEX IF NOT EXISTS ux_suppliers_company_code ON suppliers(company_id, code);

-- Create unique index on (company_id, tax_code) for duplicate detection
-- Note: tax_code can be NULL, so we use a partial index
CREATE UNIQUE INDEX IF NOT EXISTS ux_suppliers_company_tax_code 
  ON suppliers(company_id, tax_code) 
  WHERE tax_code IS NOT NULL;

-- Create indexes for search performance
CREATE INDEX IF NOT EXISTS idx_suppliers_company_active ON suppliers(company_id, active);
CREATE INDEX IF NOT EXISTS idx_suppliers_company_name ON suppliers(company_id, name);

-- Create table to track supplier code sequences per company and year
-- Format: SUP-YYYY-NNNN (e.g., SUP-2025-0001)
CREATE TABLE IF NOT EXISTS supplier_code_sequences (
  company_id BIGINT NOT NULL,
  year INTEGER NOT NULL,
  last_sequence INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (company_id, year),
  FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Create index for efficient lookups
CREATE INDEX IF NOT EXISTS idx_supplier_code_sequences_company_year 
  ON supplier_code_sequences(company_id, year);

-- Create function to get next supplier code for a company and year
-- Thread-safe using SELECT FOR UPDATE
CREATE OR REPLACE FUNCTION get_next_supplier_code(company_id_value BIGINT, year_value INTEGER)
RETURNS TEXT AS $$
DECLARE
    next_seq INTEGER;
    formatted_code TEXT;
BEGIN
    -- Lock the row for this company and year (or create if it doesn't exist)
    INSERT INTO supplier_code_sequences (company_id, year, last_sequence)
    VALUES (company_id_value, year_value, 0)
    ON CONFLICT (company_id, year) DO NOTHING;
    
    -- Update with locked row to ensure thread-safety
    UPDATE supplier_code_sequences
    SET last_sequence = last_sequence + 1
    WHERE company_id = company_id_value AND year = year_value
    RETURNING last_sequence INTO next_seq;
    
    -- Format as SUP-YYYY-NNNN
    formatted_code := 'SUP-' || year_value || '-' || LPAD(next_seq::TEXT, 4, '0');
    
    RETURN formatted_code;
END;
$$ LANGUAGE plpgsql;

