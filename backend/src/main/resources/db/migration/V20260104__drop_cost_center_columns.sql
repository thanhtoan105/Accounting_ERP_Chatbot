-- Drop cost_center related columns from all tables
-- This removes the cost center feature that is no longer needed

-- Drop cost_center_id from voucher_lines
ALTER TABLE voucher_lines DROP COLUMN IF EXISTS cost_center_id;

-- Drop cost_center_id from journal_entries
ALTER TABLE journal_entries DROP COLUMN IF EXISTS cost_center_id;

-- Drop cost_center_id from sales_invoice_lines
ALTER TABLE sales_invoice_lines DROP COLUMN IF EXISTS cost_center_id;

-- Drop cost_center_id from purchase_bill_lines
ALTER TABLE purchase_bill_lines DROP COLUMN IF EXISTS cost_center_id;

-- Drop requires_cost_center from account_controls
ALTER TABLE account_controls DROP COLUMN IF EXISTS requires_cost_center;

-- Drop requires_cost_center from voucher_template_lines
ALTER TABLE voucher_template_lines DROP COLUMN IF EXISTS requires_cost_center;
