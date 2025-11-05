-- Add contact info and fiscal year start to companies
ALTER TABLE companies
  ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255),
  ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32),
  ADD COLUMN IF NOT EXISTS fiscal_year_start DATE;

-- Optional: basic check constraints can be added later if needed


