-- Migration V20251127004: Fix receipt trigger - use inline logic instead of calling trigger function
-- Previous migration had bug: trigger functions cannot be called directly

-- Recreate the trigger function with inline logic
CREATE OR REPLACE FUNCTION update_invoice_on_receipt_status_change()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
  alloc RECORD;
  total_paid NUMERIC(19,2);
BEGIN
  -- If receipt is posted or reversed, update all allocated invoices
  IF (TG_OP = 'UPDATE' AND OLD.status IS DISTINCT FROM NEW.status AND NEW.status IN ('POSTED', 'REVERSED')) OR
     (TG_OP = 'INSERT' AND NEW.status = 'POSTED') THEN
    
    -- Loop through all invoices linked to this receipt
    FOR alloc IN 
      SELECT DISTINCT ta.document_id as invoice_id
      FROM transaction_allocations ta
      WHERE ta.transaction_type = 'RECEIPT' 
        AND ta.document_type = 'SALES_INVOICE'
        AND ta.transaction_id = NEW.id
    LOOP
      -- Calculate total paid from all POSTED receipts for this invoice
      SELECT COALESCE(SUM(ta.allocated_amount), 0) INTO total_paid
      FROM transaction_allocations ta
      JOIN ar_payments ap ON ta.transaction_id = ap.id
      WHERE ta.document_type = 'SALES_INVOICE'
        AND ta.document_id = alloc.invoice_id
        AND ta.transaction_type = 'RECEIPT'
        AND ap.status = 'POSTED';

      -- Update invoice payment status
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
      WHERE id = alloc.invoice_id;
    END LOOP;
  END IF;

  RETURN NEW;
END;
$function$;

-- Fix the update_invoice_payment_status function for transaction_allocations triggers
CREATE OR REPLACE FUNCTION update_invoice_payment_status()
RETURNS TRIGGER AS $$
DECLARE
  target_invoice_id UUID;
  total_paid NUMERIC(19,2);
BEGIN
  -- Determine which invoice to update based on operation
  IF TG_OP = 'DELETE' THEN
    target_invoice_id := OLD.document_id;
    -- Only process RECEIPT allocations to SALES_INVOICE
    IF OLD.transaction_type != 'RECEIPT' OR OLD.document_type != 'SALES_INVOICE' THEN
      RETURN OLD;
    END IF;
  ELSE
    target_invoice_id := NEW.document_id;
    -- Only process RECEIPT allocations to SALES_INVOICE  
    IF NEW.transaction_type != 'RECEIPT' OR NEW.document_type != 'SALES_INVOICE' THEN
      RETURN NEW;
    END IF;
  END IF;

  -- Calculate total paid amount for this invoice from POSTED receipts
  SELECT COALESCE(SUM(ta.allocated_amount), 0) INTO total_paid
  FROM transaction_allocations ta
  JOIN ar_payments ap ON ta.transaction_id = ap.id
  WHERE ta.document_type = 'SALES_INVOICE'
    AND ta.document_id = target_invoice_id
    AND ta.transaction_type = 'RECEIPT'
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
  WHERE id = target_invoice_id;

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
