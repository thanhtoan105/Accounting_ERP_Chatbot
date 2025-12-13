-- Phase 2.1: Create TT200 Chart of Accounts Template Table
-- This is a tenant-agnostic template table containing the official TT200/2014/TT-BTC accounts

CREATE TABLE IF NOT EXISTS tt200_chart_of_accounts_template (
    code          VARCHAR(20) PRIMARY KEY,
    name_vi       VARCHAR(255) NOT NULL,
    name_english  VARCHAR(255),
    normal_side   VARCHAR(10) NOT NULL,
    type          VARCHAR(50) NOT NULL,
    parent_code   VARCHAR(20),
    ordering      INTEGER NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT true,
    
    CONSTRAINT chk_tt200_template_normal_side 
        CHECK (normal_side IN ('Debit', 'Credit', 'Both')),
    CONSTRAINT chk_tt200_template_type 
        CHECK (type IN ('Asset', 'Liability', 'Equity', 'Revenue', 'Expense')),
    CONSTRAINT fk_tt200_template_parent 
        FOREIGN KEY (parent_code) REFERENCES tt200_chart_of_accounts_template(code)
);

CREATE INDEX IF NOT EXISTS idx_tt200_template_parent_code ON tt200_chart_of_accounts_template(parent_code);
CREATE INDEX IF NOT EXISTS idx_tt200_template_type ON tt200_chart_of_accounts_template(type);

COMMENT ON TABLE tt200_chart_of_accounts_template IS 
    'Official TT200/2014/TT-BTC Chart of Accounts template. Used to seed chart_of_accounts for each company.';
COMMENT ON COLUMN tt200_chart_of_accounts_template.normal_side IS 
    'Debit (Dư Nợ), Credit (Dư Có), or Both (Lưỡng tính)';
