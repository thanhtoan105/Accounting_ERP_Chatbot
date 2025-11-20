-- Create ap_payments table
CREATE TABLE IF NOT EXISTS ap_payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  payment_number VARCHAR(50) NOT NULL,
  payment_date DATE NOT NULL,
  due_date DATE,
  cash_account_id BIGINT,
  bank_account_id BIGINT,
  payee VARCHAR(255) NOT NULL,
  amount NUMERIC(19, 2) NOT NULL,
  reference VARCHAR(500),
  payment_method VARCHAR(20) NOT NULL,
  payment_proof_url VARCHAR(1000),
  is_standalone BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_by_id BIGINT NOT NULL,
  approved_by_id BIGINT,
  linked_voucher_id UUID,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  posted_at TIMESTAMP WITH TIME ZONE
);

-- Foreign key constraints
ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id);

ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_cash_account FOREIGN KEY (cash_account_id) REFERENCES bank_accounts(id);

ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);

ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_created_by FOREIGN KEY (created_by_id) REFERENCES users(id);

ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_approved_by FOREIGN KEY (approved_by_id) REFERENCES users(id);

ALTER TABLE ap_payments
  ADD CONSTRAINT fk_ap_payments_linked_voucher FOREIGN KEY (linked_voucher_id) REFERENCES vouchers(id);

-- Unique constraint: company_id + payment_number + year(payment_date)
-- This ensures payment number uniqueness per company per year
CREATE UNIQUE INDEX IF NOT EXISTS ux_ap_payments_company_payment_year 
  ON ap_payments(company_id, payment_number, EXTRACT(YEAR FROM payment_date));

-- CHECK constraint for status values
ALTER TABLE ap_payments
  ADD CONSTRAINT ck_ap_payments_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'POSTED', 'CANCELLED'));

-- CHECK constraint for payment method values
ALTER TABLE ap_payments
  ADD CONSTRAINT ck_ap_payments_payment_method CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'CHECK', 'OTHER'));

-- CHECK constraints for positive amounts
ALTER TABLE ap_payments
  ADD CONSTRAINT ck_ap_payments_amount_positive CHECK (amount > 0);

-- CHECK constraint: either cash_account_id or bank_account_id must be set
ALTER TABLE ap_payments
  ADD CONSTRAINT ck_ap_payments_account_required CHECK (
    (cash_account_id IS NOT NULL AND bank_account_id IS NULL) OR
    (cash_account_id IS NULL AND bank_account_id IS NOT NULL)
  );

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_ap_payments_company_id ON ap_payments(company_id);
CREATE INDEX IF NOT EXISTS idx_ap_payments_supplier_id ON ap_payments(supplier_id);
CREATE INDEX IF NOT EXISTS idx_ap_payments_payment_date ON ap_payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_ap_payments_status ON ap_payments(status);
CREATE INDEX IF NOT EXISTS idx_ap_payments_cash_account_id ON ap_payments(cash_account_id);
CREATE INDEX IF NOT EXISTS idx_ap_payments_bank_account_id ON ap_payments(bank_account_id);
CREATE INDEX IF NOT EXISTS idx_ap_payments_payment_number ON ap_payments(payment_number);
CREATE INDEX IF NOT EXISTS idx_ap_payments_created_by_id ON ap_payments(created_by_id);

-- Create payment_allocations table
CREATE TABLE IF NOT EXISTS payment_allocations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL,
  payment_id UUID NOT NULL,
  purchase_bill_id UUID NOT NULL,
  allocated_amount NUMERIC(19, 2) NOT NULL,
  allocation_order INTEGER NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key constraints
ALTER TABLE payment_allocations
  ADD CONSTRAINT fk_payment_allocations_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE payment_allocations
  ADD CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES ap_payments(id) ON DELETE CASCADE;

ALTER TABLE payment_allocations
  ADD CONSTRAINT fk_payment_allocations_purchase_bill FOREIGN KEY (purchase_bill_id) REFERENCES purchase_bills(id);

-- CHECK constraint for positive allocated amount
ALTER TABLE payment_allocations
  ADD CONSTRAINT ck_payment_allocations_amount_positive CHECK (allocated_amount > 0);

-- CHECK constraint: allocated_amount cannot exceed bill remaining balance
-- Note: This is enforced at application level, and a database trigger is added in
-- V20251209__add_overpayment_prevention_trigger.sql for additional safety.
-- The remaining_balance is calculated as total_amount - sum of allocated payments.

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_payment_allocations_company_id ON payment_allocations(company_id);
CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment_id ON payment_allocations(payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_allocations_purchase_bill_id ON payment_allocations(purchase_bill_id);
CREATE INDEX IF NOT EXISTS idx_payment_allocations_allocation_order ON payment_allocations(allocation_order);

-- Composite index for FIFO queries
CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment_order ON payment_allocations(payment_id, allocation_order);

