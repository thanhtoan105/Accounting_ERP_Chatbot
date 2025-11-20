-- Add database-level overpayment prevention constraint via trigger
-- This enforces that allocated_amount cannot exceed bill remaining_balance
-- remaining_balance = purchase_bills.total_amount - sum of allocated payments

-- Create function to validate overpayment prevention
CREATE OR REPLACE FUNCTION validate_payment_allocation_overpayment()
RETURNS TRIGGER AS $$
DECLARE
  bill_total_amount NUMERIC(19, 2);
  existing_allocated_amount NUMERIC(19, 2);
  remaining_balance NUMERIC(19, 2);
BEGIN
  -- Get bill total amount
  SELECT total_amount INTO bill_total_amount
  FROM purchase_bills
  WHERE id = NEW.purchase_bill_id;

  IF bill_total_amount IS NULL THEN
    RAISE EXCEPTION 'Purchase bill not found: %', NEW.purchase_bill_id;
  END IF;

  -- Calculate existing allocated amount (excluding current row for UPDATE)
  SELECT COALESCE(SUM(allocated_amount), 0) INTO existing_allocated_amount
  FROM payment_allocations
  WHERE purchase_bill_id = NEW.purchase_bill_id
    AND (TG_OP = 'INSERT' OR id != NEW.id);

  -- Calculate remaining balance
  remaining_balance := bill_total_amount - existing_allocated_amount;

  -- Validate: allocated_amount cannot exceed remaining_balance
  IF NEW.allocated_amount > remaining_balance THEN
    RAISE EXCEPTION 'Overpayment prevention: Allocated amount (%) exceeds bill remaining balance (%). Bill ID: %',
      NEW.allocated_amount, remaining_balance, NEW.purchase_bill_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on payment_allocations table
CREATE TRIGGER trg_validate_payment_allocation_overpayment
  BEFORE INSERT OR UPDATE ON payment_allocations
  FOR EACH ROW
  EXECUTE FUNCTION validate_payment_allocation_overpayment();

-- Add comment to document the constraint
COMMENT ON TRIGGER trg_validate_payment_allocation_overpayment ON payment_allocations IS
  'Enforces overpayment prevention: allocated_amount cannot exceed bill remaining_balance (total_amount - sum of allocated payments). This provides database-level safety net in addition to application-level validation.';

