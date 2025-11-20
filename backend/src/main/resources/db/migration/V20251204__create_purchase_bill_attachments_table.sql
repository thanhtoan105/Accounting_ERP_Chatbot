-- Create purchase_bill_attachments table
CREATE TABLE IF NOT EXISTS purchase_bill_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_bill_id UUID NOT NULL,
  company_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  uploaded_by BIGINT NOT NULL
);

-- Foreign key constraints
ALTER TABLE purchase_bill_attachments
  ADD CONSTRAINT fk_purchase_bill_attachments_purchase_bill FOREIGN KEY (purchase_bill_id) REFERENCES purchase_bills(id) ON DELETE CASCADE;

ALTER TABLE purchase_bill_attachments
  ADD CONSTRAINT fk_purchase_bill_attachments_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE purchase_bill_attachments
  ADD CONSTRAINT fk_purchase_bill_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_purchase_bill_attachments_purchase_bill_company ON purchase_bill_attachments(purchase_bill_id, company_id);

-- Index for company scoping
CREATE INDEX IF NOT EXISTS idx_purchase_bill_attachments_company_id ON purchase_bill_attachments(company_id);

-- Index for uploaded_at (for sorting)
CREATE INDEX IF NOT EXISTS idx_purchase_bill_attachments_uploaded_at ON purchase_bill_attachments(uploaded_at);

