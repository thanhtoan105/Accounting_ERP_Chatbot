-- Add extended fields to chart_of_accounts table
-- V21: Add name_english, description, and active columns

ALTER TABLE chart_of_accounts
    ADD COLUMN IF NOT EXISTS name_english VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS description TEXT NULL,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

-- Update existing records to set active = true (already default, but explicit for clarity)
UPDATE chart_of_accounts SET active = true WHERE active IS NULL;

-- Add comment for documentation
COMMENT ON COLUMN chart_of_accounts.name_english IS 'Account name in English';
COMMENT ON COLUMN chart_of_accounts.description IS 'Account description';
COMMENT ON COLUMN chart_of_accounts.active IS 'Account status: true = In Use, false = Out of Use';

