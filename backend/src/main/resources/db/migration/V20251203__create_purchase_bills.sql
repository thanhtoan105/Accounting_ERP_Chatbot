-- Create purchase_bills table
CREATE TABLE IF NOT EXISTS purchase_bills (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  bill_number VARCHAR(50) NOT NULL,
  bill_date DATE NOT NULL,
  due_date DATE NOT NULL,
  reference VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  total_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
  vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
  created_by_id BIGINT NOT NULL,
  approved_by_id BIGINT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE purchase_bills
  ADD CONSTRAINT fk_purchase_bills_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE purchase_bills
  ADD CONSTRAINT fk_purchase_bills_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id);

ALTER TABLE purchase_bills
  ADD CONSTRAINT fk_purchase_bills_created_by FOREIGN KEY (created_by_id) REFERENCES users(id);

ALTER TABLE purchase_bills
  ADD CONSTRAINT fk_purchase_bills_approved_by FOREIGN KEY (approved_by_id) REFERENCES users(id);

-- Unique constraint: company_id + supplier_id + bill_number + year(bill_date)
-- This ensures bill number uniqueness per supplier per year
CREATE UNIQUE INDEX IF NOT EXISTS ux_purchase_bills_supplier_bill_year 
  ON purchase_bills(company_id, supplier_id, bill_number, EXTRACT(YEAR FROM bill_date));

-- CHECK constraint for status values
ALTER TABLE purchase_bills
  ADD CONSTRAINT ck_purchase_bills_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'POSTED', 'REJECTED', 'PAID', 'PARTIALLY_PAID'));

-- CHECK constraints for positive amounts
ALTER TABLE purchase_bills
  ADD CONSTRAINT ck_purchase_bills_total_amount_positive CHECK (total_amount >= 0);

ALTER TABLE purchase_bills
  ADD CONSTRAINT ck_purchase_bills_vat_amount_non_negative CHECK (vat_amount >= 0);

-- CHECK constraint for valid date ranges
ALTER TABLE purchase_bills
  ADD CONSTRAINT ck_purchase_bills_due_date_after_bill_date CHECK (due_date >= bill_date);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_purchase_bills_company_id ON purchase_bills(company_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bills_supplier_id ON purchase_bills(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bills_bill_date ON purchase_bills(bill_date);
CREATE INDEX IF NOT EXISTS idx_purchase_bills_status ON purchase_bills(status);
CREATE INDEX IF NOT EXISTS idx_purchase_bills_created_by_id ON purchase_bills(created_by_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bills_bill_number ON purchase_bills(bill_number);

-- Index for search on bill_number and reference (for Vietnamese unaccented search)
CREATE INDEX IF NOT EXISTS idx_purchase_bills_reference ON purchase_bills(reference);

-- Create purchase_bill_lines table
CREATE TABLE IF NOT EXISTS purchase_bill_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_bill_id UUID NOT NULL,
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
ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT fk_purchase_bill_lines_bill FOREIGN KEY (purchase_bill_id) REFERENCES purchase_bills(id) ON DELETE CASCADE;

ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT fk_purchase_bill_lines_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id);

ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT fk_purchase_bill_lines_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- UNIQUE constraint: purchase_bill_id + line_number
CREATE UNIQUE INDEX IF NOT EXISTS ux_purchase_bill_lines_bill_line_number 
  ON purchase_bill_lines(purchase_bill_id, line_number);

-- CHECK constraint for VAT rate values
ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT ck_purchase_bill_lines_vat_rate CHECK (vat_rate IN ('ZERO', 'FIVE', 'TEN', 'EXEMPT'));

-- CHECK constraints for positive amounts and quantities
ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT ck_purchase_bill_lines_quantity_non_negative CHECK (quantity >= 0);

ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT ck_purchase_bill_lines_unit_price_positive CHECK (unit_price > 0);

ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT ck_purchase_bill_lines_amount_positive CHECK (amount > 0);

ALTER TABLE purchase_bill_lines
  ADD CONSTRAINT ck_purchase_bill_lines_vat_amount_non_negative CHECK (vat_amount >= 0);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_purchase_bill_lines_bill ON purchase_bill_lines(purchase_bill_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bill_lines_account ON purchase_bill_lines(account_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bill_lines_company_id ON purchase_bill_lines(company_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bill_lines_cost_center_id ON purchase_bill_lines(cost_center_id);
CREATE INDEX IF NOT EXISTS idx_purchase_bill_lines_item_id ON purchase_bill_lines(item_id);

