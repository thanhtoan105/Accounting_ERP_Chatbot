-- Fix: Create customer code function in accounting schema
-- Migration V22 may have created it in wrong schema or it was missing

-- Create table to track customer code sequences per company and year (if not exists)
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

-- Drop function if exists in wrong schema/location
DROP FUNCTION IF EXISTS get_next_customer_code(BIGINT, INTEGER);
DROP FUNCTION IF EXISTS public.get_next_customer_code(BIGINT, INTEGER);
DROP FUNCTION IF EXISTS get_next_customer_code(BIGINT, INTEGER);

-- Create function in accounting schema
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

-- Grant execute permission
GRANT EXECUTE ON FUNCTION get_next_customer_code(BIGINT, INTEGER) TO PUBLIC;

