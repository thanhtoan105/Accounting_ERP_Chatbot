-- Migration V6: Create invitations table
-- Purpose: Support user invitation system with tokens, expiration, and status tracking

CREATE TABLE IF NOT EXISTS invitations (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  token VARCHAR(64) NOT NULL UNIQUE,
  company_id BIGINT,
  created_by BIGINT,
  role VARCHAR(50) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_invitations_role_valid CHECK (role IN ('admin', 'accountant', 'chief_accountant', 'cfo')),
  CONSTRAINT ck_invitations_status_valid CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'CANCELLED'))
);

-- Foreign keys
ALTER TABLE invitations
  ADD CONSTRAINT fk_invitations_company FOREIGN KEY (company_id) REFERENCES companies(id),
  ADD CONSTRAINT fk_invitations_created_by FOREIGN KEY (created_by) REFERENCES users(id);

-- Indexes
CREATE UNIQUE INDEX IF NOT EXISTS ux_invitations_token ON invitations(token);
CREATE INDEX IF NOT EXISTS ix_invitations_email_company ON invitations(email, company_id);
CREATE INDEX IF NOT EXISTS ix_invitations_status ON invitations(status);
CREATE INDEX IF NOT EXISTS ix_invitations_expires_at ON invitations(expires_at);

