-- Create widget_role_permissions table for refined RBAC analytics widget access
-- This table stores role-based permissions for analytics widgets

CREATE TABLE IF NOT EXISTS widget_role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    widget_key VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_widget_role UNIQUE (widget_key, role)
);

CREATE INDEX idx_widget_role_permissions_widget_key ON widget_role_permissions(widget_key);
CREATE INDEX idx_widget_role_permissions_role ON widget_role_permissions(role);

COMMENT ON TABLE widget_role_permissions IS 'Role-based permissions for analytics widgets';
COMMENT ON COLUMN widget_role_permissions.widget_key IS 'Unique identifier for the widget';
COMMENT ON COLUMN widget_role_permissions.role IS 'User role (e.g., admin, accountant_ar, cashier)';
COMMENT ON COLUMN widget_role_permissions.can_view IS 'Whether the role can view this widget';

-- Insert default permissions for Vietnamese accounting roles
-- ALL scope roles (can view all widgets)
INSERT INTO widget_role_permissions (widget_key, role, can_view) VALUES
    ('revenue-vs-expenses', 'admin', true),
    ('revenue-vs-expenses', 'cfo', true),
    ('revenue-vs-expenses', 'chief_accountant', true),
    ('ar-ap-balances', 'admin', true),
    ('ar-ap-balances', 'cfo', true),
    ('ar-ap-balances', 'chief_accountant', true),
    ('cash-position', 'admin', true),
    ('cash-position', 'cfo', true),
    ('cash-position', 'chief_accountant', true),
    ('top-5-debtors', 'admin', true),
    ('top-5-debtors', 'cfo', true),
    ('top-5-debtors', 'chief_accountant', true),
    ('top-5-creditors', 'admin', true),
    ('top-5-creditors', 'cfo', true),
    ('top-5-creditors', 'chief_accountant', true),
    ('period-summary', 'admin', true),
    ('period-summary', 'cfo', true),
    ('period-summary', 'chief_accountant', true);

-- SUMMARY scope (accountant_general, accountant, finance)
INSERT INTO widget_role_permissions (widget_key, role, can_view) VALUES
    ('period-summary', 'accountant_general', true),
    ('period-summary', 'accountant', true),
    ('period-summary', 'finance', true);

-- AR_ONLY scope (accountant_ar)
INSERT INTO widget_role_permissions (widget_key, role, can_view) VALUES
    ('ar-ap-balances', 'accountant_ar', true),
    ('top-5-debtors', 'accountant_ar', true);

-- AP_ONLY scope (accountant_ap)
INSERT INTO widget_role_permissions (widget_key, role, can_view) VALUES
    ('ar-ap-balances', 'accountant_ap', true),
    ('top-5-creditors', 'accountant_ap', true);

-- CASH_ONLY scope (cashier)
INSERT INTO widget_role_permissions (widget_key, role, can_view) VALUES
    ('cash-position', 'cashier', true);
