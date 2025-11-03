-- Migration V5: Validate and set default roles
-- Purpose: Add CHECK constraint for valid roles and assign default 'accountant' role to existing users

-- First, set default role 'accountant' for any users with NULL or invalid role
UPDATE users
SET role = 'accountant'
WHERE role IS NULL
   OR role NOT IN ('admin', 'accountant', 'chief_accountant', 'cfo');

-- Add CHECK constraint to ensure role column only accepts valid values
ALTER TABLE users
  ADD CONSTRAINT ck_users_role_valid
  CHECK (role IN ('admin', 'accountant', 'chief_accountant', 'cfo'));

