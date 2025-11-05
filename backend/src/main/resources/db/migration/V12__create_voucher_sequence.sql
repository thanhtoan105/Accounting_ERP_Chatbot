-- Create table to track voucher number sequences per year
-- This approach allows sequence to reset each year (starts at 1 for each new year)
CREATE TABLE IF NOT EXISTS voucher_number_sequences (
  year INTEGER NOT NULL,
  last_sequence INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (year)
);

-- Create index for efficient lookups
CREATE INDEX IF NOT EXISTS idx_voucher_number_sequences_year ON voucher_number_sequences(year);

-- Create function to get next voucher number for a given year
-- This function handles year-based sequence generation with automatic reset per year
-- Uses SELECT FOR UPDATE for thread-safe concurrent access
CREATE OR REPLACE FUNCTION get_next_voucher_number(year_value INTEGER)
RETURNS TEXT AS $$
DECLARE
    next_seq INTEGER;
    formatted_number TEXT;
BEGIN
    -- Lock the row for this year (or create if it doesn't exist)
    INSERT INTO voucher_number_sequences (year, last_sequence)
    VALUES (year_value, 0)
    ON CONFLICT (year) DO NOTHING;
    
    -- Update with locked row to ensure thread-safety
    UPDATE voucher_number_sequences
    SET last_sequence = last_sequence + 1
    WHERE year = year_value
    RETURNING last_sequence INTO next_seq;
    
    -- Format as VC{YYYY}-{seq}
    formatted_number := 'VC' || year_value || '-' || LPAD(next_seq::TEXT, 6, '0');
    
    RETURN formatted_number;
END;
$$ LANGUAGE plpgsql;

