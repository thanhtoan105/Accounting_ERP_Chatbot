-- Make purchase_bill_id nullable in approval_workflows to support Sales Invoices
-- The table now supports both Purchase Bills (AP) and Sales Invoices (AR)
-- One of purchase_bill_id or sales_invoice_id must be set, but not both

-- Step 1: Drop the NOT NULL constraint on purchase_bill_id
ALTER TABLE approval_workflows
ALTER COLUMN purchase_bill_id DROP NOT NULL;

-- Step 2: Drop the foreign key constraint (we'll recreate it with different options)
ALTER TABLE approval_workflows
DROP CONSTRAINT IF EXISTS fk_approval_workflows_purchase_bill;

-- Step 3: Recreate the foreign key constraint with ON DELETE CASCADE
-- This allows NULL values for purchase_bill_id
ALTER TABLE approval_workflows
ADD CONSTRAINT fk_approval_workflows_purchase_bill
FOREIGN KEY (purchase_bill_id) REFERENCES purchase_bills(id) ON DELETE CASCADE;

-- Step 4: Add check constraint to ensure exactly one of purchase_bill_id or sales_invoice_id is set
ALTER TABLE approval_workflows
ADD CONSTRAINT chk_approval_workflows_entity_reference
CHECK (
    (purchase_bill_id IS NOT NULL AND sales_invoice_id IS NULL) OR
    (purchase_bill_id IS NULL AND sales_invoice_id IS NOT NULL)
);

-- Update comment to reflect dual support
COMMENT ON TABLE approval_workflows IS 'Tracks approval workflows for purchase bills (AP) and sales invoices (AR) implementing maker-checker pattern';
COMMENT ON COLUMN approval_workflows.purchase_bill_id IS 'Reference to purchase bill for AP workflows (nullable for AR workflows)';
COMMENT ON COLUMN approval_workflows.sales_invoice_id IS 'Reference to sales invoice for AR workflows (nullable for AP workflows)';
COMMENT ON CONSTRAINT chk_approval_workflows_entity_reference ON approval_workflows IS 'Ensures exactly one of purchase_bill_id or sales_invoice_id is set';

