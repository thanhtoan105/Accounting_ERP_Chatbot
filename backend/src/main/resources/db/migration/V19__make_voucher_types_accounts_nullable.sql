-- Make debit_account_id and credit_account_id nullable
-- First drop the foreign key constraints
ALTER TABLE voucher_types
  DROP CONSTRAINT IF EXISTS fk_voucher_types_debit_account;

ALTER TABLE voucher_types
  DROP CONSTRAINT IF EXISTS fk_voucher_types_credit_account;

-- Alter columns to be nullable
ALTER TABLE voucher_types
  ALTER COLUMN debit_account_id DROP NOT NULL;

ALTER TABLE voucher_types
  ALTER COLUMN credit_account_id DROP NOT NULL;

-- Recreate foreign key constraints (now allowing NULL)
ALTER TABLE voucher_types
  ADD CONSTRAINT fk_voucher_types_debit_account 
  FOREIGN KEY (debit_account_id) REFERENCES chart_of_accounts(id);

ALTER TABLE voucher_types
  ADD CONSTRAINT fk_voucher_types_credit_account 
  FOREIGN KEY (credit_account_id) REFERENCES chart_of_accounts(id);



