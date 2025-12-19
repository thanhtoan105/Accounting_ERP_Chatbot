-- TT200 COA Category Mapping for BI Analytics
-- Maps account code prefixes to standardized categories for financial reporting

-- Create enum type for account categories
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'account_category') THEN
        CREATE TYPE account_category AS ENUM (
            'REVENUE',
            'COGS',
            'OPERATING_EXPENSE',
            'OTHER_EXPENSE',
            'FINANCIAL_INCOME',
            'FINANCIAL_EXPENSE',
            'AR',
            'AP',
            'CASH',
            'BANK'
        );
    END IF;
END$$;

-- Create account_category_mapping table
CREATE TABLE IF NOT EXISTS account_category_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT REFERENCES companies(id) ON DELETE CASCADE,
    account_code_prefix VARCHAR(10) NOT NULL,
    category account_category NOT NULL,
    description VARCHAR(255),
    is_global BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uk_coa_mapping_company_prefix UNIQUE (company_id, account_code_prefix)
);

-- Create indexes for efficient lookup
CREATE INDEX IF NOT EXISTS idx_coa_mapping_company_id ON account_category_mapping(company_id);
CREATE INDEX IF NOT EXISTS idx_coa_mapping_prefix ON account_category_mapping(account_code_prefix);
CREATE INDEX IF NOT EXISTS idx_coa_mapping_category ON account_category_mapping(category);
CREATE INDEX IF NOT EXISTS idx_coa_mapping_global ON account_category_mapping(is_global) WHERE is_global = TRUE;

-- Seed TT200 standard mappings (global defaults with company_id = NULL)
INSERT INTO account_category_mapping (company_id, account_code_prefix, category, description, is_global, active)
VALUES
    -- Revenue accounts
    (NULL, '511', 'REVENUE', 'Doanh thu bán hàng hóa - Revenue from sale of goods', TRUE, TRUE),
    (NULL, '512', 'REVENUE', 'Doanh thu bán thành phẩm - Revenue from sale of finished products', TRUE, TRUE),
    (NULL, '515', 'REVENUE', 'Doanh thu hoạt động tài chính - Financial income', TRUE, TRUE),
    (NULL, '521', 'REVENUE', 'Các khoản giảm trừ doanh thu - Revenue deductions (contra-revenue)', TRUE, TRUE),
    
    -- Cost of Goods Sold
    (NULL, '632', 'COGS', 'Giá vốn hàng bán - Cost of goods sold', TRUE, TRUE),
    
    -- Operating Expenses (Production costs)
    (NULL, '621', 'OPERATING_EXPENSE', 'Chi phí nguyên vật liệu trực tiếp - Direct material costs', TRUE, TRUE),
    (NULL, '622', 'OPERATING_EXPENSE', 'Chi phí nhân công trực tiếp - Direct labor costs', TRUE, TRUE),
    (NULL, '623', 'OPERATING_EXPENSE', 'Chi phí sử dụng máy thi công - Construction machinery costs', TRUE, TRUE),
    (NULL, '627', 'OPERATING_EXPENSE', 'Chi phí sản xuất chung - Manufacturing overhead', TRUE, TRUE),
    
    -- Operating Expenses (General)
    (NULL, '631', 'OPERATING_EXPENSE', 'Giá thành sản xuất - Production cost', TRUE, TRUE),
    (NULL, '635', 'OPERATING_EXPENSE', 'Chi phí tài chính - Financial expenses', TRUE, TRUE),
    (NULL, '641', 'OPERATING_EXPENSE', 'Chi phí bán hàng - Selling expenses', TRUE, TRUE),
    (NULL, '642', 'OPERATING_EXPENSE', 'Chi phí quản lý doanh nghiệp - General & administrative expenses', TRUE, TRUE),
    
    -- Other Expenses
    (NULL, '811', 'OTHER_EXPENSE', 'Chi phí khác - Other expenses', TRUE, TRUE),
    
    -- Accounts Receivable
    (NULL, '131', 'AR', 'Phải thu của khách hàng - Trade receivables', TRUE, TRUE),
    
    -- Accounts Payable
    (NULL, '331', 'AP', 'Phải trả cho người bán - Trade payables', TRUE, TRUE),
    
    -- Cash
    (NULL, '111', 'CASH', 'Tiền mặt - Cash on hand', TRUE, TRUE),
    
    -- Bank
    (NULL, '112', 'BANK', 'Tiền gửi ngân hàng - Bank deposits', TRUE, TRUE)
ON CONFLICT (company_id, account_code_prefix) DO NOTHING;

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_coa_mapping_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_coa_mapping_updated_at ON account_category_mapping;
CREATE TRIGGER trigger_coa_mapping_updated_at
    BEFORE UPDATE ON account_category_mapping
    FOR EACH ROW
    EXECUTE FUNCTION update_coa_mapping_timestamp();

COMMENT ON TABLE account_category_mapping IS 'Maps TT200 account code prefixes to standardized categories for BI analytics';
COMMENT ON COLUMN account_category_mapping.company_id IS 'NULL for global TT200 defaults, company ID for company-specific overrides';
COMMENT ON COLUMN account_category_mapping.account_code_prefix IS 'Account code prefix (e.g., 511, 632) to match against';
COMMENT ON COLUMN account_category_mapping.is_global IS 'TRUE for TT200 standard mappings, FALSE for company-specific';
