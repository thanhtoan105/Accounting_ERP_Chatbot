-- Add approval threshold amount to company_settings table
-- Controls when purchase bills require approval (default 20,000,000 VND)

ALTER TABLE company_settings
ADD COLUMN approval_threshold_amount DECIMAL(19, 2) DEFAULT 20000000.00;

COMMENT ON COLUMN company_settings.approval_threshold_amount IS 'Purchase bill approval threshold in company currency (default 20M VND). Bills exceeding this amount require maker-checker approval.';

-- Update existing company settings with default threshold
UPDATE company_settings
SET approval_threshold_amount = 20000000.00
WHERE approval_threshold_amount IS NULL;
