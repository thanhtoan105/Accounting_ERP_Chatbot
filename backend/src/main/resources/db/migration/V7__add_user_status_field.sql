-- Add status field to users table
ALTER TABLE users
ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
-- Update existing users to have ACTIVE status (migration safety)
UPDATE users
SET status = 'ACTIVE'
WHERE status IS NULL;
-- Add check constraint for valid status values
ALTER TABLE users
ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'));