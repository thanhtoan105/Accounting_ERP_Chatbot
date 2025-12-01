-- Update approval_workflows table to support Sales Invoices (AR)

ALTER TABLE approval_workflows
ADD COLUMN sales_invoice_id UUID;

CREATE INDEX idx_approval_workflows_sales_invoice_id ON approval_workflows(sales_invoice_id);

ALTER TABLE approval_workflows
ADD CONSTRAINT fk_approval_workflows_sales_invoice
FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id);

COMMENT ON COLUMN approval_workflows.sales_invoice_id IS 'Reference to sales invoice for AR workflows';
