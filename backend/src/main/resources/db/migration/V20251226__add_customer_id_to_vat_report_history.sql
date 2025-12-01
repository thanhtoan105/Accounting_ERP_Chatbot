-- Add customer_id column to vat_report_history for OUTPUT_VAT reports (AR module)
ALTER TABLE vat_report_history
    ADD COLUMN IF NOT EXISTS customer_id BIGINT;

-- Add foreign key constraint for customer_id
ALTER TABLE vat_report_history
    ADD CONSTRAINT fk_vat_report_history_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL;

-- Add index for customer_id queries
CREATE INDEX IF NOT EXISTS idx_vat_report_history_customer
    ON vat_report_history(customer_id);

-- Add comment explaining usage
COMMENT ON COLUMN vat_report_history.supplier_id IS 'For INPUT_VAT reports: supplier ID (null = all suppliers)';
COMMENT ON COLUMN vat_report_history.customer_id IS 'For OUTPUT_VAT reports: customer ID (null = all customers)';

