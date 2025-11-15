-- Create voucher_attachments table
CREATE TABLE IF NOT EXISTS voucher_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  voucher_id UUID NOT NULL,
  company_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  uploaded_by BIGINT NOT NULL
);

-- Foreign key constraints
ALTER TABLE voucher_attachments
  ADD CONSTRAINT fk_voucher_attachments_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE CASCADE;

ALTER TABLE voucher_attachments
  ADD CONSTRAINT fk_voucher_attachments_company FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE voucher_attachments
  ADD CONSTRAINT fk_voucher_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id);

-- Composite index on (voucher_id, company_id) for efficient queries
CREATE INDEX IF NOT EXISTS idx_voucher_attachments_voucher_company ON voucher_attachments(voucher_id, company_id);

-- Index on company_id for company-scoped queries
CREATE INDEX IF NOT EXISTS idx_voucher_attachments_company_id ON voucher_attachments(company_id);

-- Index on uploaded_at for sorting
CREATE INDEX IF NOT EXISTS idx_voucher_attachments_uploaded_at ON voucher_attachments(uploaded_at);

