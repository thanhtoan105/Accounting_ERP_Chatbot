-- Create sales_invoices table
CREATE TABLE IF NOT EXISTS sales_invoices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  invoice_number VARCHAR(50) NOT NULL,
  invoice_date DATE NOT NULL,
  due_date DATE NOT NULL,
  reference VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  total_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
  vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
  created_by_id BIGINT NOT NULL,
  approved_by_id BIGINT,
  posted_voucher_id UUID,
  is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE sales_invoices
  ADD CONSTRAINT fk_sales_invoices_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE sales_invoices
  ADD CONSTRAINT fk_sales_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers(id);

ALTER TABLE sales_invoices
  ADD CONSTRAINT fk_sales_invoices_created_by FOREIGN KEY (created_by_id) REFERENCES users(id);

ALTER TABLE sales_invoices
  ADD CONSTRAINT fk_sales_invoices_approved_by FOREIGN KEY (approved_by_id) REFERENCES users(id);

-- Unique constraint: company_id + customer_id + invoice_number + year(invoice_date)
-- This ensures invoice number uniqueness per customer per year
CREATE UNIQUE INDEX IF NOT EXISTS ux_sales_invoices_customer_invoice_year 
  ON sales_invoices(company_id, customer_id, invoice_number, EXTRACT(YEAR FROM invoice_date))
  WHERE is_deleted = FALSE;

-- CHECK constraint for status values
ALTER TABLE sales_invoices
  ADD CONSTRAINT ck_sales_invoices_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'POSTED', 'REJECTED', 'PAID', 'PARTIALLY_PAID'));

-- CHECK constraints for positive amounts
ALTER TABLE sales_invoices
  ADD CONSTRAINT ck_sales_invoices_total_amount_positive CHECK (total_amount >= 0);

ALTER TABLE sales_invoices
  ADD CONSTRAINT ck_sales_invoices_vat_amount_non_negative CHECK (vat_amount >= 0);

-- CHECK constraint for valid date ranges
ALTER TABLE sales_invoices
  ADD CONSTRAINT ck_sales_invoices_due_date_after_invoice_date CHECK (due_date >= invoice_date);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_sales_invoices_company_id ON sales_invoices(company_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_customer_id ON sales_invoices(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_invoice_date ON sales_invoices(invoice_date);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_status ON sales_invoices(status);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_created_by_id ON sales_invoices(created_by_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_invoice_number ON sales_invoices(invoice_number);

-- Index for search on invoice_number and reference (for Vietnamese unaccented search)
CREATE INDEX IF NOT EXISTS idx_sales_invoices_reference ON sales_invoices(reference);

-- Create sales_invoice_lines table
CREATE TABLE IF NOT EXISTS sales_invoice_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sales_invoice_id UUID NOT NULL,
  line_number INTEGER NOT NULL,
  account_id BIGINT NOT NULL,
  description VARCHAR(500) NOT NULL,
  quantity NUMERIC(19, 4) NOT NULL DEFAULT 1,
  unit_price NUMERIC(19, 2) NOT NULL DEFAULT 0,
  amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
  vat_rate VARCHAR(10) NOT NULL DEFAULT 'ZERO',
  vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
  cost_center_id BIGINT,
  item_id BIGINT,
  company_id BIGINT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT fk_sales_invoice_lines_invoice FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE CASCADE;

ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT fk_sales_invoice_lines_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id);

ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT fk_sales_invoice_lines_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- UNIQUE constraint: sales_invoice_id + line_number
CREATE UNIQUE INDEX IF NOT EXISTS ux_sales_invoice_lines_invoice_line_number 
  ON sales_invoice_lines(sales_invoice_id, line_number);

-- CHECK constraint for VAT rate values
ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT ck_sales_invoice_lines_vat_rate CHECK (vat_rate IN ('ZERO', 'FIVE', 'TEN', 'EXEMPT'));

-- CHECK constraints for positive amounts and quantities
ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT ck_sales_invoice_lines_quantity_non_negative CHECK (quantity >= 0);

ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT ck_sales_invoice_lines_unit_price_positive CHECK (unit_price > 0);

ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT ck_sales_invoice_lines_amount_positive CHECK (amount > 0);

ALTER TABLE sales_invoice_lines
  ADD CONSTRAINT ck_sales_invoice_lines_vat_amount_non_negative CHECK (vat_amount >= 0);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_sales_invoice_lines_invoice ON sales_invoice_lines(sales_invoice_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_lines_account ON sales_invoice_lines(account_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_lines_company_id ON sales_invoice_lines(company_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_lines_cost_center_id ON sales_invoice_lines(cost_center_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_lines_item_id ON sales_invoice_lines(item_id);

-- Create sales_invoice_attachments table
CREATE TABLE IF NOT EXISTS sales_invoice_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sales_invoice_id UUID NOT NULL,
  company_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  uploaded_by BIGINT NOT NULL
);

-- Foreign key constraints
ALTER TABLE sales_invoice_attachments
  ADD CONSTRAINT fk_sales_invoice_attachments_sales_invoice FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id) ON DELETE CASCADE;

ALTER TABLE sales_invoice_attachments
  ADD CONSTRAINT fk_sales_invoice_attachments_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE sales_invoice_attachments
  ADD CONSTRAINT fk_sales_invoice_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_sales_invoice_attachments_invoice_company ON sales_invoice_attachments(sales_invoice_id, company_id);

-- Index for company scoping
CREATE INDEX IF NOT EXISTS idx_sales_invoice_attachments_company_id ON sales_invoice_attachments(company_id);

-- Index for uploaded_at (for sorting)
CREATE INDEX IF NOT EXISTS idx_sales_invoice_attachments_uploaded_at ON sales_invoice_attachments(uploaded_at);
