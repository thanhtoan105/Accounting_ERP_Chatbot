-- Create ar_payments table
CREATE TABLE IF NOT EXISTS ar_payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  receipt_number VARCHAR(50) NOT NULL,
  receipt_date DATE NOT NULL,
  cash_account_id BIGINT,
  bank_account_id BIGINT,
  payee VARCHAR(255) NOT NULL,
  amount NUMERIC(19, 2) NOT NULL,
  reference VARCHAR(500),
  payment_method VARCHAR(20) NOT NULL,
  receipt_proof_url VARCHAR(1000),
  is_standalone BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_by_id BIGINT NOT NULL,
  posted_by_id BIGINT,
  linked_voucher_id UUID,
  reversal_reason VARCHAR(500),
  original_receipt_id UUID,
  reversing_receipt_id UUID,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  posted_at TIMESTAMP WITH TIME ZONE
);

-- Foreign key constraints
ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_customer FOREIGN KEY (customer_id) REFERENCES customers(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_cash_account FOREIGN KEY (cash_account_id) REFERENCES bank_accounts(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_created_by FOREIGN KEY (created_by_id) REFERENCES users(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_posted_by FOREIGN KEY (posted_by_id) REFERENCES users(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_linked_voucher FOREIGN KEY (linked_voucher_id) REFERENCES vouchers(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_original_receipt FOREIGN KEY (original_receipt_id) REFERENCES ar_payments(id);

ALTER TABLE ar_payments
  ADD CONSTRAINT fk_ar_payments_reversing_receipt FOREIGN KEY (reversing_receipt_id) REFERENCES ar_payments(id);

-- Unique constraint: company_id + receipt_number + year(receipt_date)
-- This ensures receipt number uniqueness per company per year
CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_payments_company_receipt_year 
  ON ar_payments(company_id, receipt_number, EXTRACT(YEAR FROM receipt_date));

-- CHECK constraint for status values
ALTER TABLE ar_payments
  ADD CONSTRAINT ck_ar_payments_status CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED'));

-- CHECK constraint for payment method values
ALTER TABLE ar_payments
  ADD CONSTRAINT ck_ar_payments_payment_method CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'CHECK', 'OTHER'));

-- CHECK constraints for positive amounts
ALTER TABLE ar_payments
  ADD CONSTRAINT ck_ar_payments_amount_positive CHECK (amount > 0);

-- CHECK constraint: either cash_account_id or bank_account_id must be set
ALTER TABLE ar_payments
  ADD CONSTRAINT ck_ar_payments_account_required CHECK (
    (cash_account_id IS NOT NULL AND bank_account_id IS NULL) OR
    (cash_account_id IS NULL AND bank_account_id IS NOT NULL)
  );

-- CHECK constraint: reversal_reason required when status is REVERSED
ALTER TABLE ar_payments
  ADD CONSTRAINT ck_ar_payments_reversal_reason CHECK (
    (status = 'REVERSED' AND reversal_reason IS NOT NULL) OR
    (status != 'REVERSED')
  );

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_ar_payments_company_id ON ar_payments(company_id);
CREATE INDEX IF NOT EXISTS idx_ar_payments_customer_id ON ar_payments(customer_id);
CREATE INDEX IF NOT EXISTS idx_ar_payments_receipt_date ON ar_payments(receipt_date);
CREATE INDEX IF NOT EXISTS idx_ar_payments_status ON ar_payments(status);
CREATE INDEX IF NOT EXISTS idx_ar_payments_cash_account_id ON ar_payments(cash_account_id);
CREATE INDEX IF NOT EXISTS idx_ar_payments_bank_account_id ON ar_payments(bank_account_id);
CREATE INDEX IF NOT EXISTS idx_ar_payments_receipt_number ON ar_payments(receipt_number);
CREATE INDEX IF NOT EXISTS idx_ar_payments_created_by_id ON ar_payments(created_by_id);
CREATE INDEX IF NOT EXISTS idx_ar_payments_is_standalone ON ar_payments(is_standalone);

-- Create receipt_allocations table
CREATE TABLE IF NOT EXISTS receipt_allocations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  receipt_id UUID NOT NULL,
  sales_invoice_id UUID NOT NULL,
  allocated_amount NUMERIC(19, 2) NOT NULL,
  allocation_order INTEGER NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE receipt_allocations
  ADD CONSTRAINT fk_receipt_allocations_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE receipt_allocations
  ADD CONSTRAINT fk_receipt_allocations_receipt FOREIGN KEY (receipt_id) REFERENCES ar_payments(id) ON DELETE CASCADE;

ALTER TABLE receipt_allocations
  ADD CONSTRAINT fk_receipt_allocations_sales_invoice FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices(id);

-- CHECK constraint for positive allocated amount
ALTER TABLE receipt_allocations
  ADD CONSTRAINT ck_receipt_allocations_amount_positive CHECK (allocated_amount > 0);

-- Unique constraint: one allocation per receipt-invoice pair
CREATE UNIQUE INDEX IF NOT EXISTS ux_receipt_allocations_receipt_invoice 
  ON receipt_allocations(receipt_id, sales_invoice_id);

-- CHECK constraint: allocated_amount cannot exceed invoice remaining balance
-- Note: This is enforced at application level and via database trigger.
-- The remaining_balance is calculated as total_amount - sum of allocated receipts.

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_company_id ON receipt_allocations(company_id);
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_receipt_id ON receipt_allocations(receipt_id);
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_sales_invoice_id ON receipt_allocations(sales_invoice_id);
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_allocation_order ON receipt_allocations(allocation_order);

-- Composite index for allocation order queries
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_receipt_order ON receipt_allocations(receipt_id, allocation_order);

-- Add remaining_balance and amount_paid columns to sales_invoices table
ALTER TABLE sales_invoices
  ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(19, 2) DEFAULT 0 NOT NULL;

ALTER TABLE sales_invoices
  ADD COLUMN IF NOT EXISTS remaining_balance NUMERIC(19, 2);

-- Update remaining_balance for existing invoices (total_amount - amount_paid)
UPDATE sales_invoices
SET remaining_balance = total_amount - amount_paid
WHERE remaining_balance IS NULL;

-- Make remaining_balance non-nullable after initial update
ALTER TABLE sales_invoices
  ALTER COLUMN remaining_balance SET NOT NULL;

-- Add CHECK constraint: remaining_balance >= 0
ALTER TABLE sales_invoices
  ADD CONSTRAINT ck_sales_invoices_remaining_balance_positive CHECK (remaining_balance >= 0);

-- Add CHECK constraint: amount_paid <= total_amount
ALTER TABLE sales_invoices
  ADD CONSTRAINT ck_sales_invoices_amount_paid_valid CHECK (amount_paid <= total_amount);

-- Create function to prevent over-allocation of receipts to invoices
CREATE OR REPLACE FUNCTION check_receipt_allocation_limit()
RETURNS TRIGGER AS $$
DECLARE
  invoice_total NUMERIC(19, 2);
  total_allocated NUMERIC(19, 2);
BEGIN
  -- Get the invoice's total amount
  SELECT total_amount INTO invoice_total
  FROM sales_invoices
  WHERE id = NEW.sales_invoice_id;

  -- Calculate total allocated amount for this invoice (including new allocation)
  SELECT COALESCE(SUM(allocated_amount), 0) INTO total_allocated
  FROM receipt_allocations
  WHERE sales_invoice_id = NEW.sales_invoice_id
    AND id != COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::UUID);

  -- Check if new allocation would exceed total amount
  IF (total_allocated + NEW.allocated_amount) > invoice_total THEN
    RAISE EXCEPTION 'Allocation amount exceeds invoice total amount. Total: %, Attempting to allocate total: %',
      invoice_total, (total_allocated + NEW.allocated_amount);
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to check receipt allocation limits
CREATE TRIGGER trg_check_receipt_allocation_limit
  BEFORE INSERT OR UPDATE ON receipt_allocations
  FOR EACH ROW
  EXECUTE FUNCTION check_receipt_allocation_limit();

-- Create function to update invoice status based on payments
CREATE OR REPLACE FUNCTION update_invoice_payment_status()
RETURNS TRIGGER AS $$
DECLARE
  invoice_total NUMERIC(19, 2);
  total_paid NUMERIC(19, 2);
  invoice_status VARCHAR(20);
BEGIN
  -- Get invoice total and current status
  SELECT total_amount, status INTO invoice_total, invoice_status
  FROM sales_invoices
  WHERE id = COALESCE(NEW.sales_invoice_id, OLD.sales_invoice_id);

  -- Calculate total paid amount for this invoice
  SELECT COALESCE(SUM(ra.allocated_amount), 0) INTO total_paid
  FROM receipt_allocations ra
  JOIN ar_payments ap ON ra.receipt_id = ap.id
  WHERE ra.sales_invoice_id = COALESCE(NEW.sales_invoice_id, OLD.sales_invoice_id)
    AND ap.status = 'POSTED';

  -- Update invoice amount_paid and remaining_balance
  UPDATE sales_invoices
  SET 
    amount_paid = total_paid,
    remaining_balance = total_amount - total_paid,
    status = CASE
      WHEN total_paid = 0 THEN 
        CASE WHEN status IN ('PAID', 'PARTIALLY_PAID') THEN 'POSTED' ELSE status END
      WHEN total_paid >= total_amount THEN 'PAID'
      WHEN total_paid > 0 AND total_paid < total_amount THEN 'PARTIALLY_PAID'
      ELSE status
    END
  WHERE id = COALESCE(NEW.sales_invoice_id, OLD.sales_invoice_id);

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Create trigger to update invoice status on allocation changes
CREATE TRIGGER trg_update_invoice_payment_status_insert
  AFTER INSERT ON receipt_allocations
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_payment_status();

CREATE TRIGGER trg_update_invoice_payment_status_update
  AFTER UPDATE ON receipt_allocations
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_payment_status();

CREATE TRIGGER trg_update_invoice_payment_status_delete
  AFTER DELETE ON receipt_allocations
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_payment_status();

-- Create trigger to update invoice status when receipt status changes
CREATE OR REPLACE FUNCTION update_invoice_on_receipt_status_change()
RETURNS TRIGGER AS $$
BEGIN
  -- If receipt is posted or reversed, trigger invoice status update for all allocated invoices
  IF (TG_OP = 'UPDATE' AND OLD.status != NEW.status AND NEW.status IN ('POSTED', 'REVERSED')) OR
     (TG_OP = 'INSERT' AND NEW.status = 'POSTED') THEN
    
    -- Update all invoices linked to this receipt
    PERFORM update_invoice_payment_status()
    FROM receipt_allocations
    WHERE receipt_id = NEW.id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_invoice_on_receipt_status
  AFTER INSERT OR UPDATE ON ar_payments
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_on_receipt_status_change();
