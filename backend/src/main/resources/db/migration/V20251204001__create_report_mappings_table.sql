-- Story 7.2: Create report_mappings table for TT200 statutory reports
-- This table stores account-to-line mappings with versioning for rollback capability

CREATE TABLE report_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL,          -- 'B01', 'B02', 'B03', 'F01'
    line_code VARCHAR(20) NOT NULL,            -- TT200 line code (e.g., '100', '110')
    line_name VARCHAR(255) NOT NULL,           -- Vietnamese line name
    line_name_english VARCHAR(255),            -- English translation for i18n
    account_pattern TEXT NOT NULL,             -- Account codes/ranges (e.g., '111*,112*' or 'SUM(110,120)')
    operator VARCHAR(10) DEFAULT 'SUM',        -- 'SUM', 'DIFF', 'ABS'
    sign_modifier INTEGER DEFAULT 1,           -- 1 or -1 for balance direction
    display_order INTEGER NOT NULL,            -- For rendering order
    parent_line_code VARCHAR(20),              -- For hierarchical structure
    level INTEGER DEFAULT 1,                   -- Indentation level (1=main, 2=sub, 3=detail)
    is_calculated BOOLEAN DEFAULT FALSE,       -- TRUE for lines like "Gross Profit = Revenue - COGS"
    formula TEXT,                              -- Formula for calculated lines (e.g., "01-11")
    version INTEGER DEFAULT 1,                 -- Version number for this line
    is_current BOOLEAN DEFAULT TRUE,           -- Quick lookup for current version
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    change_reason TEXT,                        -- Required for audit when updating

    CONSTRAINT uk_report_mappings_company_type_line_version
        UNIQUE(company_id, report_type, line_code, version)
);

-- Index for fast current version lookups
CREATE INDEX idx_report_mappings_current
    ON report_mappings(company_id, report_type, is_current)
    WHERE is_current = TRUE;

-- Index for version history queries
CREATE INDEX idx_report_mappings_history
    ON report_mappings(company_id, report_type, line_code, version DESC);

-- Index for hierarchical queries
CREATE INDEX idx_report_mappings_parent
    ON report_mappings(company_id, report_type, parent_line_code)
    WHERE parent_line_code IS NOT NULL;

-- Index for display order sorting
CREATE INDEX idx_report_mappings_display_order
    ON report_mappings(company_id, report_type, display_order);

COMMENT ON TABLE report_mappings IS 'TT200 account-to-line mappings with versioning for statutory reports';
COMMENT ON COLUMN report_mappings.is_current IS 'TRUE for the active version; FALSE for historical versions';
COMMENT ON COLUMN report_mappings.is_calculated IS 'TRUE for lines computed from other lines (not from GL accounts)';
COMMENT ON COLUMN report_mappings.account_pattern IS 'Account codes: wildcards (111*), ranges, or formulas like SUM(110,120)';
COMMENT ON COLUMN report_mappings.formula IS 'For calculated lines: references to other line codes (e.g., "01-11" means line 01 minus line 11)';
