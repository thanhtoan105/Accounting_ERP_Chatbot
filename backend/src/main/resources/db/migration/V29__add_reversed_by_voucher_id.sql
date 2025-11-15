-- Add reversed_by_voucher_id column to vouchers table for bi-directional reversal linking
ALTER TABLE vouchers
  ADD COLUMN IF NOT EXISTS reversed_by_voucher_id UUID;

-- Foreign key constraint
ALTER TABLE vouchers
  ADD CONSTRAINT fk_vouchers_reversed_by_voucher FOREIGN KEY (reversed_by_voucher_id) REFERENCES vouchers(id);

-- Index for efficient queries
CREATE INDEX IF NOT EXISTS idx_vouchers_reversed_by_voucher_id ON vouchers(reversed_by_voucher_id);

