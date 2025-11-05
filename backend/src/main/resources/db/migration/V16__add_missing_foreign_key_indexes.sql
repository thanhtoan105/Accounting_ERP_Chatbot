-- Migration V16: Add missing indexes on foreign key columns
-- Purpose: Improve query performance for JOIN operations on foreign key columns

-- Index on invitations.created_by (foreign key to users.id)
CREATE INDEX IF NOT EXISTS idx_invitations_created_by ON invitations(created_by);

-- Index on users.company_id (foreign key to companies.id)
CREATE INDEX IF NOT EXISTS idx_users_company_id ON users(company_id);

-- Index on vouchers.reversed_by (foreign key to users.id)
CREATE INDEX IF NOT EXISTS idx_vouchers_reversed_by ON vouchers(reversed_by);

