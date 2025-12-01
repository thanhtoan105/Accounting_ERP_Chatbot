-- Add original_invoice_id column to sales_invoices table for credit notes
-- This column references the original invoice that this credit note is reversing

ALTER TABLE sales_invoices
ADD COLUMN original_invoice_id UUID;

-- Add foreign key constraint
ALTER TABLE sales_invoices
ADD CONSTRAINT fk_sales_invoices_original_invoice
FOREIGN KEY (original_invoice_id)
REFERENCES sales_invoices(id)
ON DELETE RESTRICT;

-- Add index for performance
CREATE INDEX idx_sales_invoices_original_invoice_id
ON sales_invoices(original_invoice_id);

-- Add comment
COMMENT ON COLUMN sales_invoices.original_invoice_id IS 'References the original invoice ID for credit notes. NULL for regular invoices.';

