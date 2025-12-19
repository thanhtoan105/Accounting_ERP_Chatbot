-- Migration V20251208002: Create view for purchase_bill_attachments compatibility
-- The purchase_bill_attachments table was dropped in V20251126003 (consolidate_attachment_tables)
-- but PurchaseBillAttachment entity still references it. Create view for backward compatibility.
-- This is similar to V20251208001 which created the voucher_attachments view.

-- Create view for PurchaseBillAttachment entity
CREATE OR REPLACE VIEW purchase_bill_attachments AS
SELECT 
    id,
    entity_id AS purchase_bill_id,
    company_id,
    file_name,
    storage_path,
    mime_type,
    file_size,
    uploaded_at,
    uploaded_by
FROM attachments
WHERE entity_type = 'PURCHASE_BILL';

-- Add comment
COMMENT ON VIEW purchase_bill_attachments IS 'Backward-compatible view for PurchaseBillAttachment entity. Data stored in unified attachments table.';
