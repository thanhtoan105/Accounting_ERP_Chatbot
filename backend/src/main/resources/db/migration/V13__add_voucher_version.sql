-- Add version column for optimistic locking
ALTER TABLE vouchers
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Create index for version column (for optimistic locking queries)
CREATE INDEX IF NOT EXISTS idx_vouchers_version ON vouchers(version);

