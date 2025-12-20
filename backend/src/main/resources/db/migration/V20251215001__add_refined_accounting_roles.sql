-- Migration V20251215001: Add refined accounting roles for AC 8.0.18
-- Purpose: Extend the users.role constraint to include new specialized accounting roles
-- Required for Vietnamese accounting role-based dashboard widget access

-- Drop the existing constraint
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role_valid;

-- Add the updated constraint with all valid roles
-- Note: This includes both legacy roles and new refined roles for backward compatibility
ALTER TABLE users
  ADD CONSTRAINT ck_users_role_valid
  CHECK (role IN (
    'super_admin',      -- System superuser (multi-tenant admin)
    'admin',            -- Company administrator
    'chief_accountant', -- Kế toán trưởng - Full analytics access
    'cfo',              -- Chief Financial Officer - View all, no manual refresh
    'accountant',       -- Legacy general accountant role (maps to ACCOUNTANT_GENERAL)
    'finance',          -- Legacy finance role (maps to ACCOUNTANT_GENERAL)
    'accountant_general', -- Kế toán tổng hợp - Summary view only
    'accountant_ar',    -- Kế toán công nợ phải thu - AR widgets only
    'accountant_ap',    -- Kế toán công nợ phải trả - AP widgets only
    'cashier'           -- Thủ quỹ - Cash widgets only
  ));

COMMENT ON CONSTRAINT ck_users_role_valid ON users IS
  'Valid roles for Vietnamese accounting system with specialized dashboard access per AC 8.0.18';
