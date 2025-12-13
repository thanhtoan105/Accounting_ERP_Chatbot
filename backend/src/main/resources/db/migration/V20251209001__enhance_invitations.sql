-- Enhance invitations table with additional audit and security columns

-- Add new columns
ALTER TABLE invitations
    ADD COLUMN revoked_at TIMESTAMP,
    ADD COLUMN accepted_at TIMESTAMP,
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN invited_by BIGINT REFERENCES users(id),
    ADD COLUMN token_hash VARCHAR(64);

-- Add index on token column if not exists
CREATE INDEX IF NOT EXISTS idx_invitations_token ON invitations(token);

-- Add index on token_hash for secure lookups
CREATE INDEX idx_invitations_token_hash ON invitations(token_hash);

-- Add composite index on email and company_id for lookup
CREATE INDEX idx_invitations_email_company ON invitations(email, company_id);
