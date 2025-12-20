-- Add is_super_admin column and constraint to users table
-- Super admins can have null company_id, regular users must have a company

-- Step 1: Add is_super_admin column
ALTER TABLE users ADD COLUMN is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Step 2: Add CHECK constraint as NOT VALID (doesn't validate existing rows)
-- This allows migration to succeed even if orphan users exist
ALTER TABLE users ADD CONSTRAINT chk_user_company_or_super_admin 
    CHECK (company_id IS NOT NULL OR is_super_admin = TRUE) NOT VALID;

-- Step 3: Validate constraint (commented out - run manually after orphan users are handled)
-- After migrating or fixing orphan users, run:
-- ALTER TABLE users VALIDATE CONSTRAINT chk_user_company_or_super_admin;
