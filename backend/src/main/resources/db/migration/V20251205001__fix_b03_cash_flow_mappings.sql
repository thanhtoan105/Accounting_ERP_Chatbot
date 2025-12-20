-- =====================================================
-- V20251205001: Fix B03 Cash Flow Statement Mappings
-- =====================================================
--
-- This migration fixes critical bugs in B03-DN Cash Flow Statement:
--
-- BUG: All B03 line items incorrectly map to cash accounts (111*,112*,113*)
--      which only represents cash holdings, not the sources/uses of cash.
--
-- FIX: Map each B03 line to the appropriate GL accounts that represent
--      the economic activity generating or consuming cash.
--
-- Per TT200 Circular 200/2014/TT-BTC, Direct Method Cash Flow Statement
-- classifies cash flows by activity type:
--   - Operating: Revenue, COGS, Operating Expenses
--   - Investing: Fixed Assets, Investments
--   - Financing: Capital, Loans, Dividends
--
-- Note: This is an MVP approximation. Full TT200 Direct Method compliance
-- would require transaction-level classification, which is a larger feature.
-- =====================================================

-- I. OPERATING ACTIVITIES
-- Line 01: Cash receipts from sales/services
-- Maps to revenue accounts (511*, 512*) as proxy for cash from customers
UPDATE report_mappings
SET account_pattern = '511*,512*'
WHERE report_type = 'B03' AND line_code = '01';

-- Line 02: Cash payments to suppliers
-- Maps to COGS (632*) as proxy for supplier payments (negative/expense account)
UPDATE report_mappings
SET account_pattern = '632*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '02';

-- Line 03: Cash payments to employees
-- Maps to payroll liability (334*) as proxy for employee payments
UPDATE report_mappings
SET account_pattern = '334*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '03';

-- Line 04: Interest paid
-- Maps to financial expense (635*) for interest payments
UPDATE report_mappings
SET account_pattern = '635*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '04';

-- Line 05: Corporate income tax paid
-- Maps to tax payable (3334) as proxy for CIT payments
UPDATE report_mappings
SET account_pattern = '3334*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '05';

-- Line 06: Other cash receipts from operating
-- Maps to other income (711*) for miscellaneous operating receipts
UPDATE report_mappings
SET account_pattern = '711*'
WHERE report_type = 'B03' AND line_code = '06';

-- Line 07: Other cash payments for operating
-- Maps to other expenses (811*) for miscellaneous operating payments
UPDATE report_mappings
SET account_pattern = '811*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '07';

-- II. INVESTING ACTIVITIES
-- Line 21: Cash payments for fixed asset acquisition
-- Maps to fixed asset accounts (211*, 212*, 213*) - debits represent purchases
UPDATE report_mappings
SET account_pattern = '211*,212*,213*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '21';

-- Line 22: Cash receipts from fixed asset disposal
-- Remains tied to 711* (other income) for disposal gains
UPDATE report_mappings
SET account_pattern = '711*'
WHERE report_type = 'B03' AND line_code = '22';

-- Line 23: Cash payments for loans/debt instruments to others
-- Maps to receivables/loans made (128*, 135*)
UPDATE report_mappings
SET account_pattern = '128*,135*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '23';

-- Line 24: Cash receipts from loan repayment/sale of debt instruments
-- Maps to receivables/loans received (128*, 135*)
UPDATE report_mappings
SET account_pattern = '128*,135*'
WHERE report_type = 'B03' AND line_code = '24';

-- Line 25: Cash payments for equity investments
-- Maps to investment accounts (221*, 222*, 228*)
UPDATE report_mappings
SET account_pattern = '221*,222*,228*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '25';

-- Line 26: Cash receipts from disposal of equity investments
-- Maps to investment accounts (221*, 222*, 228*)
UPDATE report_mappings
SET account_pattern = '221*,222*,228*'
WHERE report_type = 'B03' AND line_code = '26';

-- Line 27: Cash receipts from interest, dividends and profits
-- Maps to financial income (515*)
UPDATE report_mappings
SET account_pattern = '515*'
WHERE report_type = 'B03' AND line_code = '27';

-- III. FINANCING ACTIVITIES
-- Line 31: Cash from issuing shares/capital contribution
-- Maps to capital accounts (411*)
UPDATE report_mappings
SET account_pattern = '411*'
WHERE report_type = 'B03' AND line_code = '31';

-- Line 32: Cash payments for capital return/treasury shares
-- Maps to treasury shares/capital reduction (419*)
UPDATE report_mappings
SET account_pattern = '419*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '32';

-- Line 33: Cash receipts from borrowings
-- Maps to loan accounts (341*, 342*, 343*)
UPDATE report_mappings
SET account_pattern = '341*,342*,343*'
WHERE report_type = 'B03' AND line_code = '33';

-- Line 34: Cash payments for loan principal
-- Maps to loan accounts (341*, 342*)
UPDATE report_mappings
SET account_pattern = '341*,342*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '34';

-- Line 35: Cash payments for finance lease principal
-- Maps to finance lease liability (343*)
UPDATE report_mappings
SET account_pattern = '343*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '35';

-- Line 36: Dividends/profits paid to owners
-- Maps to profit distribution (421*) when paying out retained earnings
UPDATE report_mappings
SET account_pattern = '421*', sign_modifier = -1
WHERE report_type = 'B03' AND line_code = '36';

-- IV. SUMMARY LINES
-- Line 60: Cash and cash equivalents at beginning of period
-- This CORRECTLY uses cash accounts - keep 111*, 112* (exclude 113* for precision)
-- Note: Line 60 requires OPENING BALANCE calculation, handled specially in service
UPDATE report_mappings
SET account_pattern = '111*,112*'
WHERE report_type = 'B03' AND line_code = '60';

-- Line 70 is a formula (50+60+61), no account_pattern change needed
-- Its is_calculated = TRUE so it uses the formula

-- =====================================================
-- Update the seed function for new companies
-- =====================================================

CREATE OR REPLACE FUNCTION seed_b03_mappings(p_company_id BIGINT) RETURNS VOID AS $$
BEGIN
    -- Skip if mappings already exist for this company
    IF EXISTS (SELECT 1 FROM report_mappings WHERE company_id = p_company_id AND report_type = 'B03') THEN
        RETURN;
    END IF;

    INSERT INTO report_mappings (company_id, report_type, line_code, line_name, line_name_english, account_pattern, operator, sign_modifier, display_order, level, is_calculated, formula) VALUES
    -- I. OPERATING ACTIVITIES
    (p_company_id, 'B03', 'I', 'I. Lưu chuyển tiền từ hoạt động kinh doanh', 'I. Cash flows from operating activities', 'SUM(01,02,03,04,05,06,07)', 'SUM', 1, 1, 1, TRUE, '01+02+03+04+05+06+07'),
    (p_company_id, 'B03', '01', '1. Tiền thu từ bán hàng, cung cấp dịch vụ và doanh thu khác', '1. Cash receipts from sale of goods and services', '511*,512*', 'SUM', 1, 2, 2, FALSE, NULL),
    (p_company_id, 'B03', '02', '2. Tiền chi trả cho người cung cấp hàng hóa và dịch vụ', '2. Cash payments to suppliers', '632*', 'SUM', -1, 3, 2, FALSE, NULL),
    (p_company_id, 'B03', '03', '3. Tiền chi trả cho người lao động', '3. Cash payments to employees', '334*', 'SUM', -1, 4, 2, FALSE, NULL),
    (p_company_id, 'B03', '04', '4. Tiền lãi vay đã trả', '4. Interest paid', '635*', 'SUM', -1, 5, 2, FALSE, NULL),
    (p_company_id, 'B03', '05', '5. Thuế TNDN đã nộp', '5. Corporate income tax paid', '3334*', 'SUM', -1, 6, 2, FALSE, NULL),
    (p_company_id, 'B03', '06', '6. Tiền thu khác từ hoạt động kinh doanh', '6. Other cash receipts from operating activities', '711*', 'SUM', 1, 7, 2, FALSE, NULL),
    (p_company_id, 'B03', '07', '7. Tiền chi khác cho hoạt động kinh doanh', '7. Other cash payments for operating activities', '811*', 'SUM', -1, 8, 2, FALSE, NULL),
    (p_company_id, 'B03', '20', 'Lưu chuyển tiền thuần từ hoạt động kinh doanh', 'Net cash flows from operating activities', '01+02+03+04+05+06+07', 'SUM', 1, 9, 1, TRUE, '01+02+03+04+05+06+07'),

    -- II. INVESTING ACTIVITIES
    (p_company_id, 'B03', 'II', 'II. Lưu chuyển tiền từ hoạt động đầu tư', 'II. Cash flows from investing activities', 'SUM(21,22,23,24,25,26,27)', 'SUM', 1, 10, 1, TRUE, '21+22+23+24+25+26+27'),
    (p_company_id, 'B03', '21', '1. Tiền chi để mua sắm, xây dựng TSCĐ và các tài sản dài hạn khác', '1. Cash payments for acquisition of fixed assets', '211*,212*,213*', 'SUM', -1, 11, 2, FALSE, NULL),
    (p_company_id, 'B03', '22', '2. Tiền thu từ thanh lý, nhượng bán TSCĐ và các tài sản dài hạn khác', '2. Cash receipts from disposal of fixed assets', '711*', 'SUM', 1, 12, 2, FALSE, NULL),
    (p_company_id, 'B03', '23', '3. Tiền chi cho vay, mua các công cụ nợ của đơn vị khác', '3. Cash payments for loans and debt instruments', '128*,135*', 'SUM', -1, 13, 2, FALSE, NULL),
    (p_company_id, 'B03', '24', '4. Tiền thu hồi cho vay, bán lại các công cụ nợ của đơn vị khác', '4. Cash receipts from repayment of loans and debt instruments', '128*,135*', 'SUM', 1, 14, 2, FALSE, NULL),
    (p_company_id, 'B03', '25', '5. Tiền chi đầu tư góp vốn vào đơn vị khác', '5. Cash payments for equity investments', '221*,222*,228*', 'SUM', -1, 15, 2, FALSE, NULL),
    (p_company_id, 'B03', '26', '6. Tiền thu hồi đầu tư góp vốn vào đơn vị khác', '6. Cash receipts from disposal of equity investments', '221*,222*,228*', 'SUM', 1, 16, 2, FALSE, NULL),
    (p_company_id, 'B03', '27', '7. Tiền thu lãi cho vay, cổ tức và lợi nhuận được chia', '7. Cash receipts from interest, dividends and profits', '515*', 'SUM', 1, 17, 2, FALSE, NULL),
    (p_company_id, 'B03', '30', 'Lưu chuyển tiền thuần từ hoạt động đầu tư', 'Net cash flows from investing activities', '21+22+23+24+25+26+27', 'SUM', 1, 18, 1, TRUE, '21+22+23+24+25+26+27'),

    -- III. FINANCING ACTIVITIES
    (p_company_id, 'B03', 'III', 'III. Lưu chuyển tiền từ hoạt động tài chính', 'III. Cash flows from financing activities', 'SUM(31,32,33,34,35,36)', 'SUM', 1, 19, 1, TRUE, '31+32+33+34+35+36'),
    (p_company_id, 'B03', '31', '1. Tiền thu từ phát hành cổ phiếu, nhận vốn góp của chủ sở hữu', '1. Cash receipts from issuing shares and capital contribution', '411*', 'SUM', 1, 20, 2, FALSE, NULL),
    (p_company_id, 'B03', '32', '2. Tiền trả lại vốn góp cho các chủ sở hữu, mua lại cổ phiếu của doanh nghiệp đã phát hành', '2. Cash payments for capital return and treasury shares', '419*', 'SUM', -1, 21, 2, FALSE, NULL),
    (p_company_id, 'B03', '33', '3. Tiền thu từ đi vay', '3. Cash receipts from borrowings', '341*,342*,343*', 'SUM', 1, 22, 2, FALSE, NULL),
    (p_company_id, 'B03', '34', '4. Tiền trả nợ gốc vay', '4. Cash payments for loan principal', '341*,342*', 'SUM', -1, 23, 2, FALSE, NULL),
    (p_company_id, 'B03', '35', '5. Tiền trả nợ gốc thuê tài chính', '5. Cash payments for finance lease principal', '343*', 'SUM', -1, 24, 2, FALSE, NULL),
    (p_company_id, 'B03', '36', '6. Cổ tức, lợi nhuận đã trả cho chủ sở hữu', '6. Dividends and profits paid to owners', '421*', 'SUM', -1, 25, 2, FALSE, NULL),
    (p_company_id, 'B03', '40', 'Lưu chuyển tiền thuần từ hoạt động tài chính', 'Net cash flows from financing activities', '31+32+33+34+35+36', 'SUM', 1, 26, 1, TRUE, '31+32+33+34+35+36'),

    -- SUMMARY
    (p_company_id, 'B03', '50', 'Lưu chuyển tiền thuần trong kỳ (50 = 20 + 30 + 40)', 'Net cash flows for the period (50 = 20 + 30 + 40)', '20+30+40', 'SUM', 1, 27, 1, TRUE, '20+30+40'),
    (p_company_id, 'B03', '60', 'Tiền và tương đương tiền đầu kỳ', 'Cash and cash equivalents at beginning of period', '111*,112*', 'SUM', 1, 28, 1, FALSE, NULL),
    (p_company_id, 'B03', '61', 'Ảnh hưởng của thay đổi tỷ giá hối đoái quy đổi ngoại tệ', 'Effect of exchange rate changes', '413*', 'SUM', 1, 29, 1, FALSE, NULL),
    (p_company_id, 'B03', '70', 'Tiền và tương đương tiền cuối kỳ (70 = 50 + 60 + 61)', 'Cash and cash equivalents at end of period (70 = 50 + 60 + 61)', '50+60+61', 'SUM', 1, 30, 1, TRUE, '50+60+61');
END;
$$ LANGUAGE plpgsql;

-- Log this migration
COMMENT ON FUNCTION seed_b03_mappings IS 'Seeds B03 Cash Flow Statement mappings with corrected account patterns per TT200. Updated 2025-12-05.';
