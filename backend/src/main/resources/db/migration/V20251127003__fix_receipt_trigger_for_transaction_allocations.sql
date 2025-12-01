-- Migration V20251127001: Fix receipt trigger to use transaction_allocations table
-- The consolidation migration dropped receipt_allocations but triggers still reference it

-- Drop and recreate the trigger function to use transaction_allocations
CREATE OR REPLACE FUNCTION update_invoice_on_receipt_status_change()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
  -- If receipt is posted or reversed, trigger invoice status update for all allocated invoices
  IF (TG_OP = 'UPDATE' AND OLD.status != NEW.status AND NEW.status IN ('POSTED', 'REVERSED')) OR
     (TG_OP = 'INSERT' AND NEW.status = 'POSTED') THEN
    
    -- Update all invoices linked to this receipt (using new transaction_allocations table)
    PERFORM update_invoice_payment_status()
    FROM transaction_allocations
    WHERE transaction_type = 'RECEIPT' AND transaction_id = NEW.id;
  END IF;

  RETURN NEW;
END;
$function$;

-- Also update the update_invoice_payment_status function to use transaction_allocations
CREATE OR REPLACE FUNCTION update_invoice_payment_status()
RETURNS TRIGGER AS $$
DECLARE
  target_invoice_id UUID;
  total_paid NUMERIC(19,2);
  invoice_total NUMERIC(19,2);
BEGIN
  -- Determine which invoice to update based on operation
  IF TG_TABLE_NAME = 'transaction_allocations' THEN
    -- Direct allocation change
    IF TG_OP = 'DELETE' THEN
      target_invoice_id := OLD.document_id;
    ELSE
      target_invoice_id := NEW.document_id;
    END IF;
    
    -- Only process RECEIPT allocations to SALES_INVOICE
    IF TG_OP != 'DELETE' AND (NEW.transaction_type != 'RECEIPT' OR NEW.document_type != 'SALES_INVOICE') THEN
      RETURN COALESCE(NEW, OLD);
    END IF;
    IF TG_OP = 'DELETE' AND (OLD.transaction_type != 'RECEIPT' OR OLD.document_type != 'SALES_INVOICE') THEN
      RETURN COALESCE(NEW, OLD);
    END IF;
  ELSE
    -- Called from ar_payments trigger
    RETURN NEW;
  END IF;

  -- Calculate total paid amount for this invoice from POSTED receipts
  SELECT COALESCE(SUM(ta.allocated_amount), 0) INTO total_paid
  FROM transaction_allocations ta
  JOIN ar_payments ap ON ta.transaction_id = ap.id
  WHERE ta.document_type = 'SALES_INVOICE'
    AND ta.document_id = target_invoice_id
    AND ta.transaction_type = 'RECEIPT'
    AND ap.status = 'POSTED';

  -- Get invoice total
  SELECT total_amount INTO invoice_total
  FROM sales_invoices
  WHERE id = target_invoice_id;

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
  WHERE id = target_invoice_id;

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Recreate triggers on transaction_allocations for receipt allocations
DROP TRIGGER IF EXISTS trg_update_invoice_payment_status_insert ON transaction_allocations;
DROP TRIGGER IF EXISTS trg_update_invoice_payment_status_update ON transaction_allocations;
DROP TRIGGER IF EXISTS trg_update_invoice_payment_status_delete ON transaction_allocations;

CREATE TRIGGER trg_update_invoice_payment_status_insert
  AFTER INSERT ON transaction_allocations
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_payment_status();

CREATE TRIGGER trg_update_invoice_payment_status_update
  AFTER UPDATE ON transaction_allocations
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_payment_status();

CREATE TRIGGER trg_update_invoice_payment_status_delete
  AFTER DELETE ON transaction_allocations
  FOR EACH ROW
  EXECUTE FUNCTION update_invoice_payment_status();
