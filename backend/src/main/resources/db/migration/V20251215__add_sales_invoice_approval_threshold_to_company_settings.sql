-- Add sales invoice approval threshold amount to company_settings table
-- Controls when sales invoices require approval (default 100,000,000 VND)

ALTER TABLE company_settings
ADD COLUMN sales_invoice_approval_threshold_amount DECIMAL(19, 2) DEFAULT 100000000.00;

COMMENT ON COLUMN company_settings.sales_invoice_approval_threshold_amount IS 'Sales invoice approval threshold in company currency (default 100M VND). Invoices exceeding this amount require maker-checker approval.';

-- Update existing company settings with default threshold
UPDATE company_settings
SET sales_invoice_approval_threshold_amount = 100000000.00
WHERE sales_invoice_approval_threshold_amount IS NULL;
