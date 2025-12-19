-- Add amount_paid and remaining_balance columns to purchase_bills table
ALTER TABLE purchase_bills
  ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(19, 2) DEFAULT 0 NOT NULL;

ALTER TABLE purchase_bills
  ADD COLUMN IF NOT EXISTS remaining_balance NUMERIC(19, 2);

-- Update remaining_balance for existing bills (total_amount - amount_paid)
UPDATE purchase_bills
SET remaining_balance = total_amount - amount_paid
WHERE remaining_balance IS NULL;

-- Make remaining_balance non-nullable after initial update
ALTER TABLE purchase_bills
  ALTER COLUMN remaining_balance SET NOT NULL;

-- Add CHECK constraint: remaining_balance >= 0 (use DROP IF EXISTS pattern for idempotency)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ck_purchase_bills_remaining_balance_positive'
  ) THEN
    ALTER TABLE purchase_bills
      ADD CONSTRAINT ck_purchase_bills_remaining_balance_positive CHECK (remaining_balance >= 0);
  END IF;
END $$;

-- Add CHECK constraint: amount_paid <= total_amount
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'ck_purchase_bills_amount_paid_valid'
  ) THEN
    ALTER TABLE purchase_bills
      ADD CONSTRAINT ck_purchase_bills_amount_paid_valid CHECK (amount_paid <= total_amount);
  END IF;
END $$;
