-- Migration V20251208001: Create view for voucher_attachments compatibility
-- The voucher_attachments table was dropped in V20251126003 (consolidate_attachment_tables)
-- but VoucherAttachment entity still references it. Create view for backward compatibility.
-- Note: purchase_bill_attachments was re-created in V20251204.

-- Create view for VoucherAttachment entity
CREATE OR REPLACE VIEW voucher_attachments AS
SELECT 
    id,
    entity_id AS voucher_id,
    company_id,
    file_name,
    storage_path,
    mime_type,
    file_size,
    uploaded_at,
    uploaded_by
FROM attachments
WHERE entity_type = 'VOUCHER';

-- Add comment
COMMENT ON VIEW voucher_attachments IS 'Backward-compatible view for VoucherAttachment entity. Data stored in unified attachments table.';
