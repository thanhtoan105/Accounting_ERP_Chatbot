-- Story 7.2: Seed default TT200 mappings for B01-DN, B02-DN, B03-DN statutory reports
-- Based on Vietnamese Accounting Standard Circular 200/2014/TT-BTC
-- These mappings are inserted for ALL companies as default templates

-- =====================================================
-- B01-DN: BẢNG CÂN ĐỐI KẾ TOÁN (BALANCE SHEET)
-- =====================================================

-- Function to insert B01 mappings for a company
CREATE OR REPLACE FUNCTION seed_b01_mappings(p_company_id BIGINT) RETURNS VOID AS $$
BEGIN
    -- Skip if mappings already exist for this company
    IF EXISTS (SELECT 1 FROM report_mappings WHERE company_id = p_company_id AND report_type = 'B01') THEN
        RETURN;
    END IF;

    -- A - TÀI SẢN NGẮN HẠN (CURRENT ASSETS)
    INSERT INTO report_mappings (company_id, report_type, line_code, line_name, line_name_english, account_pattern, operator, display_order, level, is_calculated, formula) VALUES
    (p_company_id, 'B01', '100', 'A - TÀI SẢN NGẮN HẠN', 'A - CURRENT ASSETS', 'SUM(110,120,130,140,150)', 'SUM', 1, 1, TRUE, '110+120+130+140+150'),
    (p_company_id, 'B01', '110', 'I. Tiền và các khoản tương đương tiền', 'I. Cash and cash equivalents', '111+112', 'SUM', 2, 2, TRUE, '111+112'),
    (p_company_id, 'B01', '111', '1. Tiền', '1. Cash', '111*', 'SUM', 3, 3, FALSE, NULL),
    (p_company_id, 'B01', '112', '2. Các khoản tương đương tiền', '2. Cash equivalents', '112*,113*', 'SUM', 4, 3, FALSE, NULL),
    (p_company_id, 'B01', '120', 'II. Đầu tư tài chính ngắn hạn', 'II. Short-term financial investments', '121+122+123', 'SUM', 5, 2, TRUE, '121+122+123'),
    (p_company_id, 'B01', '121', '1. Chứng khoán kinh doanh', '1. Trading securities', '121*', 'SUM', 6, 3, FALSE, NULL),
    (p_company_id, 'B01', '122', '2. Dự phòng giảm giá chứng khoán kinh doanh', '2. Provision for impairment of trading securities', '1291*', 'SUM', 7, 3, FALSE, NULL),
    (p_company_id, 'B01', '123', '3. Đầu tư nắm giữ đến ngày đáo hạn', '3. Held-to-maturity investments', '1281*,1282*,1288*', 'SUM', 8, 3, FALSE, NULL),
    (p_company_id, 'B01', '130', 'III. Các khoản phải thu ngắn hạn', 'III. Short-term receivables', '131+132+133+134+135+136+137', 'SUM', 9, 2, TRUE, '131+132+133+134+135+136+137'),
    (p_company_id, 'B01', '131', '1. Phải thu ngắn hạn của khách hàng', '1. Short-term trade receivables', '131*', 'SUM', 10, 3, FALSE, NULL),
    (p_company_id, 'B01', '132', '2. Trả trước cho người bán ngắn hạn', '2. Short-term prepayments to suppliers', '1311*', 'SUM', 11, 3, FALSE, NULL),
    (p_company_id, 'B01', '133', '3. Phải thu nội bộ ngắn hạn', '3. Short-term intercompany receivables', '1361*,1362*,1363*,1368*', 'SUM', 12, 3, FALSE, NULL),
    (p_company_id, 'B01', '134', '4. Phải thu theo tiến độ kế hoạch hợp đồng xây dựng', '4. Construction contract receivables', '337*', 'SUM', 13, 3, FALSE, NULL),
    (p_company_id, 'B01', '135', '5. Phải thu về cho vay ngắn hạn', '5. Short-term loan receivables', '1283*', 'SUM', 14, 3, FALSE, NULL),
    (p_company_id, 'B01', '136', '6. Các khoản phải thu ngắn hạn khác', '6. Other short-term receivables', '1385*,1388*,334*,338*,141*,244*', 'SUM', 15, 3, FALSE, NULL),
    (p_company_id, 'B01', '137', '7. Dự phòng phải thu ngắn hạn khó đòi', '7. Provision for doubtful short-term receivables', '1391*', 'SUM', 16, 3, FALSE, NULL),
    (p_company_id, 'B01', '140', 'IV. Hàng tồn kho', 'IV. Inventories', '141+149', 'SUM', 17, 2, TRUE, '141+149'),
    (p_company_id, 'B01', '141', '1. Hàng tồn kho', '1. Inventories', '151*,152*,153*,154*,155*,156*,157*,158*', 'SUM', 18, 3, FALSE, NULL),
    (p_company_id, 'B01', '149', '2. Dự phòng giảm giá hàng tồn kho', '2. Provision for inventory devaluation', '2294*', 'SUM', 19, 3, FALSE, NULL),
    (p_company_id, 'B01', '150', 'V. Tài sản ngắn hạn khác', 'V. Other short-term assets', '151+152+153+154+155', 'SUM', 20, 2, TRUE, '151+152+153+154+155'),
    (p_company_id, 'B01', '151', '1. Chi phí trả trước ngắn hạn', '1. Short-term prepaid expenses', '242*', 'SUM', 21, 3, FALSE, NULL),
    (p_company_id, 'B01', '152', '2. Thuế GTGT được khấu trừ', '2. Deductible VAT', '133*', 'SUM', 22, 3, FALSE, NULL),
    (p_company_id, 'B01', '153', '3. Thuế và các khoản khác phải thu Nhà nước', '3. Taxes and other receivables from State', '333*', 'SUM', 23, 3, FALSE, NULL),
    (p_company_id, 'B01', '154', '4. Giao dịch mua bán lại trái phiếu Chính phủ', '4. Government bond repo transactions', '171*', 'SUM', 24, 3, FALSE, NULL),
    (p_company_id, 'B01', '155', '5. Tài sản ngắn hạn khác', '5. Other short-term assets', '228*,2288*', 'SUM', 25, 3, FALSE, NULL),

    -- B - TÀI SẢN DÀI HẠN (NON-CURRENT ASSETS)
    (p_company_id, 'B01', '200', 'B - TÀI SẢN DÀI HẠN', 'B - NON-CURRENT ASSETS', 'SUM(210,220,230,240,250,260)', 'SUM', 26, 1, TRUE, '210+220+230+240+250+260'),
    (p_company_id, 'B01', '210', 'I. Các khoản phải thu dài hạn', 'I. Long-term receivables', '211+212+213+214+215+216+219', 'SUM', 27, 2, TRUE, '211+212+213+214+215+216+219'),
    (p_company_id, 'B01', '211', '1. Phải thu dài hạn của khách hàng', '1. Long-term trade receivables', '131*', 'SUM', 28, 3, FALSE, NULL),
    (p_company_id, 'B01', '212', '2. Trả trước cho người bán dài hạn', '2. Long-term prepayments to suppliers', '1312*', 'SUM', 29, 3, FALSE, NULL),
    (p_company_id, 'B01', '213', '3. Vốn kinh doanh ở đơn vị trực thuộc', '3. Capital at dependent units', '1361*', 'SUM', 30, 3, FALSE, NULL),
    (p_company_id, 'B01', '214', '4. Phải thu nội bộ dài hạn', '4. Long-term intercompany receivables', '1362*,1363*,1368*', 'SUM', 31, 3, FALSE, NULL),
    (p_company_id, 'B01', '215', '5. Phải thu về cho vay dài hạn', '5. Long-term loan receivables', '1284*', 'SUM', 32, 3, FALSE, NULL),
    (p_company_id, 'B01', '216', '6. Các khoản phải thu dài hạn khác', '6. Other long-term receivables', '1385*,1386*,1388*,244*', 'SUM', 33, 3, FALSE, NULL),
    (p_company_id, 'B01', '219', '7. Dự phòng phải thu dài hạn khó đòi', '7. Provision for doubtful long-term receivables', '1392*', 'SUM', 34, 3, FALSE, NULL),
    (p_company_id, 'B01', '220', 'II. Tài sản cố định', 'II. Fixed assets', '221+224+227', 'SUM', 35, 2, TRUE, '221+224+227'),
    (p_company_id, 'B01', '221', '1. Tài sản cố định hữu hình', '1. Tangible fixed assets', '222-223', 'DIFF', 36, 3, TRUE, '222-223'),
    (p_company_id, 'B01', '222', '- Nguyên giá', '- Cost', '211*', 'SUM', 37, 4, FALSE, NULL),
    (p_company_id, 'B01', '223', '- Giá trị hao mòn lũy kế', '- Accumulated depreciation', '2141*', 'SUM', 38, 4, FALSE, NULL),
    (p_company_id, 'B01', '224', '2. Tài sản cố định thuê tài chính', '2. Finance lease assets', '225-226', 'DIFF', 39, 3, TRUE, '225-226'),
    (p_company_id, 'B01', '225', '- Nguyên giá', '- Cost', '212*', 'SUM', 40, 4, FALSE, NULL),
    (p_company_id, 'B01', '226', '- Giá trị hao mòn lũy kế', '- Accumulated depreciation', '2142*', 'SUM', 41, 4, FALSE, NULL),
    (p_company_id, 'B01', '227', '3. Tài sản cố định vô hình', '3. Intangible fixed assets', '228-229', 'DIFF', 42, 3, TRUE, '228-229'),
    (p_company_id, 'B01', '228', '- Nguyên giá', '- Cost', '213*', 'SUM', 43, 4, FALSE, NULL),
    (p_company_id, 'B01', '229', '- Giá trị hao mòn lũy kế', '- Accumulated amortization', '2143*', 'SUM', 44, 4, FALSE, NULL),
    (p_company_id, 'B01', '230', 'III. Bất động sản đầu tư', 'III. Investment property', '231-232', 'DIFF', 45, 2, TRUE, '231-232'),
    (p_company_id, 'B01', '231', '- Nguyên giá', '- Cost', '217*', 'SUM', 46, 3, FALSE, NULL),
    (p_company_id, 'B01', '232', '- Giá trị hao mòn lũy kế', '- Accumulated depreciation', '2147*', 'SUM', 47, 3, FALSE, NULL),
    (p_company_id, 'B01', '240', 'IV. Tài sản dở dang dài hạn', 'IV. Long-term assets in progress', '241+242', 'SUM', 48, 2, TRUE, '241+242'),
    (p_company_id, 'B01', '241', '1. Chi phí sản xuất, kinh doanh dở dang dài hạn', '1. Long-term work in progress', '241*', 'SUM', 49, 3, FALSE, NULL),
    (p_company_id, 'B01', '242', '2. Chi phí xây dựng cơ bản dở dang', '2. Construction in progress', '2412*', 'SUM', 50, 3, FALSE, NULL),
    (p_company_id, 'B01', '250', 'V. Đầu tư tài chính dài hạn', 'V. Long-term financial investments', '251+252+253+254+255', 'SUM', 51, 2, TRUE, '251+252+253+254+255'),
    (p_company_id, 'B01', '251', '1. Đầu tư vào công ty con', '1. Investment in subsidiaries', '221*', 'SUM', 52, 3, FALSE, NULL),
    (p_company_id, 'B01', '252', '2. Đầu tư vào công ty liên kết, liên doanh', '2. Investment in associates and joint ventures', '222*,223*', 'SUM', 53, 3, FALSE, NULL),
    (p_company_id, 'B01', '253', '3. Đầu tư góp vốn vào đơn vị khác', '3. Investment in other entities', '228*', 'SUM', 54, 3, FALSE, NULL),
    (p_company_id, 'B01', '254', '4. Dự phòng đầu tư tài chính dài hạn', '4. Provision for long-term investments', '2292*,2293*', 'SUM', 55, 3, FALSE, NULL),
    (p_company_id, 'B01', '255', '5. Đầu tư nắm giữ đến ngày đáo hạn', '5. Held-to-maturity investments', '1281*,1282*,1288*', 'SUM', 56, 3, FALSE, NULL),
    (p_company_id, 'B01', '260', 'VI. Tài sản dài hạn khác', 'VI. Other long-term assets', '261+262+268', 'SUM', 57, 2, TRUE, '261+262+268'),
    (p_company_id, 'B01', '261', '1. Chi phí trả trước dài hạn', '1. Long-term prepaid expenses', '242*', 'SUM', 58, 3, FALSE, NULL),
    (p_company_id, 'B01', '262', '2. Tài sản thuế thu nhập hoãn lại', '2. Deferred tax assets', '243*', 'SUM', 59, 3, FALSE, NULL),
    (p_company_id, 'B01', '268', '3. Tài sản dài hạn khác', '3. Other long-term assets', '244*', 'SUM', 60, 3, FALSE, NULL),

    -- TỔNG CỘNG TÀI SẢN (TOTAL ASSETS)
    (p_company_id, 'B01', '270', 'TỔNG CỘNG TÀI SẢN (270 = 100 + 200)', 'TOTAL ASSETS (270 = 100 + 200)', '100+200', 'SUM', 61, 1, TRUE, '100+200'),

    -- C - NỢ PHẢI TRẢ (LIABILITIES)
    (p_company_id, 'B01', '300', 'C - NỢ PHẢI TRẢ', 'C - LIABILITIES', 'SUM(310,330)', 'SUM', 62, 1, TRUE, '310+330'),
    (p_company_id, 'B01', '310', 'I. Nợ ngắn hạn', 'I. Short-term liabilities', '311+312+313+314+315+316+317+318+319+320+321+322+323+324', 'SUM', 63, 2, TRUE, '311+312+313+314+315+316+317+318+319+320+321+322+323+324'),
    (p_company_id, 'B01', '311', '1. Phải trả người bán ngắn hạn', '1. Short-term trade payables', '331*', 'SUM', 64, 3, FALSE, NULL),
    (p_company_id, 'B01', '312', '2. Người mua trả tiền trước ngắn hạn', '2. Short-term advances from customers', '131*', 'SUM', 65, 3, FALSE, NULL),
    (p_company_id, 'B01', '313', '3. Thuế và các khoản phải nộp Nhà nước', '3. Taxes and payables to State', '333*,334*', 'SUM', 66, 3, FALSE, NULL),
    (p_company_id, 'B01', '314', '4. Phải trả người lao động', '4. Payables to employees', '334*', 'SUM', 67, 3, FALSE, NULL),
    (p_company_id, 'B01', '315', '5. Chi phí phải trả ngắn hạn', '5. Short-term accrued expenses', '335*', 'SUM', 68, 3, FALSE, NULL),
    (p_company_id, 'B01', '316', '6. Phải trả nội bộ ngắn hạn', '6. Short-term intercompany payables', '336*', 'SUM', 69, 3, FALSE, NULL),
    (p_company_id, 'B01', '317', '7. Phải trả theo tiến độ kế hoạch hợp đồng xây dựng', '7. Construction contract payables', '337*', 'SUM', 70, 3, FALSE, NULL),
    (p_company_id, 'B01', '318', '8. Doanh thu chưa thực hiện ngắn hạn', '8. Short-term unearned revenue', '3387*', 'SUM', 71, 3, FALSE, NULL),
    (p_company_id, 'B01', '319', '9. Các khoản phải trả ngắn hạn khác', '9. Other short-term payables', '338*,344*,352*', 'SUM', 72, 3, FALSE, NULL),
    (p_company_id, 'B01', '320', '10. Vay và nợ thuê tài chính ngắn hạn', '10. Short-term borrowings and finance leases', '341*,342*,343*', 'SUM', 73, 3, FALSE, NULL),
    (p_company_id, 'B01', '321', '11. Dự phòng phải trả ngắn hạn', '11. Short-term provisions', '352*', 'SUM', 74, 3, FALSE, NULL),
    (p_company_id, 'B01', '322', '12. Quỹ khen thưởng, phúc lợi', '12. Bonus and welfare fund', '353*,431*', 'SUM', 75, 3, FALSE, NULL),
    (p_company_id, 'B01', '323', '13. Quỹ bình ổn giá', '13. Price stabilization fund', '356*', 'SUM', 76, 3, FALSE, NULL),
    (p_company_id, 'B01', '324', '14. Giao dịch mua bán lại trái phiếu Chính phủ', '14. Government bond repo transactions', '171*', 'SUM', 77, 3, FALSE, NULL),
    (p_company_id, 'B01', '330', 'II. Nợ dài hạn', 'II. Long-term liabilities', '331+332+333+334+335+336+337+338+339+340+341+342+343', 'SUM', 78, 2, TRUE, '331+332+333+334+335+336+337+338+339+340+341+342+343'),
    (p_company_id, 'B01', '331', '1. Phải trả người bán dài hạn', '1. Long-term trade payables', '331*', 'SUM', 79, 3, FALSE, NULL),
    (p_company_id, 'B01', '332', '2. Người mua trả tiền trước dài hạn', '2. Long-term advances from customers', '131*', 'SUM', 80, 3, FALSE, NULL),
    (p_company_id, 'B01', '333', '3. Chi phí phải trả dài hạn', '3. Long-term accrued expenses', '335*', 'SUM', 81, 3, FALSE, NULL),
    (p_company_id, 'B01', '334', '4. Phải trả nội bộ về vốn kinh doanh', '4. Intercompany payables - capital', '336*', 'SUM', 82, 3, FALSE, NULL),
    (p_company_id, 'B01', '335', '5. Phải trả nội bộ dài hạn', '5. Long-term intercompany payables', '336*', 'SUM', 83, 3, FALSE, NULL),
    (p_company_id, 'B01', '336', '6. Doanh thu chưa thực hiện dài hạn', '6. Long-term unearned revenue', '3387*', 'SUM', 84, 3, FALSE, NULL),
    (p_company_id, 'B01', '337', '7. Các khoản phải trả dài hạn khác', '7. Other long-term payables', '338*,344*', 'SUM', 85, 3, FALSE, NULL),
    (p_company_id, 'B01', '338', '8. Vay và nợ thuê tài chính dài hạn', '8. Long-term borrowings and finance leases', '341*,342*', 'SUM', 86, 3, FALSE, NULL),
    (p_company_id, 'B01', '339', '9. Trái phiếu chuyển đổi', '9. Convertible bonds', '3431*', 'SUM', 87, 3, FALSE, NULL),
    (p_company_id, 'B01', '340', '10. Cổ phiếu ưu đãi', '10. Preferred stock', '411*', 'SUM', 88, 3, FALSE, NULL),
    (p_company_id, 'B01', '341', '11. Thuế thu nhập hoãn lại phải trả', '11. Deferred tax liabilities', '347*', 'SUM', 89, 3, FALSE, NULL),
    (p_company_id, 'B01', '342', '12. Dự phòng phải trả dài hạn', '12. Long-term provisions', '352*', 'SUM', 90, 3, FALSE, NULL),
    (p_company_id, 'B01', '343', '13. Quỹ phát triển khoa học và công nghệ', '13. Science and technology development fund', '356*', 'SUM', 91, 3, FALSE, NULL),

    -- D - VỐN CHỦ SỞ HỮU (EQUITY)
    (p_company_id, 'B01', '400', 'D - VỐN CHỦ SỞ HỮU', 'D - EQUITY', 'SUM(410,430)', 'SUM', 92, 1, TRUE, '410+430'),
    (p_company_id, 'B01', '410', 'I. Vốn chủ sở hữu', 'I. Owner''s equity', '411+412+413+414+415+416+417+418+419+420+421', 'SUM', 93, 2, TRUE, '411+412+413+414+415+416+417+418+419+420+421'),
    (p_company_id, 'B01', '411', '1. Vốn góp của chủ sở hữu', '1. Contributed capital', '4111*', 'SUM', 94, 3, FALSE, NULL),
    (p_company_id, 'B01', '411a', '- Cổ phiếu phổ thông có quyền biểu quyết', '- Common shares with voting rights', '4111*', 'SUM', 95, 4, FALSE, NULL),
    (p_company_id, 'B01', '411b', '- Cổ phiếu ưu đãi', '- Preferred shares', '4112*', 'SUM', 96, 4, FALSE, NULL),
    (p_company_id, 'B01', '412', '2. Thặng dư vốn cổ phần', '2. Share premium', '4112*', 'SUM', 97, 3, FALSE, NULL),
    (p_company_id, 'B01', '413', '3. Quyền chọn chuyển đổi trái phiếu', '3. Convertible bond options', '4113*', 'SUM', 98, 3, FALSE, NULL),
    (p_company_id, 'B01', '414', '4. Vốn khác của chủ sở hữu', '4. Other owner''s capital', '4118*', 'SUM', 99, 3, FALSE, NULL),
    (p_company_id, 'B01', '415', '5. Cổ phiếu quỹ', '5. Treasury shares', '419*', 'SUM', 100, 3, FALSE, NULL),
    (p_company_id, 'B01', '416', '6. Chênh lệch đánh giá lại tài sản', '6. Asset revaluation difference', '412*', 'SUM', 101, 3, FALSE, NULL),
    (p_company_id, 'B01', '417', '7. Chênh lệch tỷ giá hối đoái', '7. Foreign exchange difference', '413*', 'SUM', 102, 3, FALSE, NULL),
    (p_company_id, 'B01', '418', '8. Quỹ đầu tư phát triển', '8. Investment and development fund', '414*', 'SUM', 103, 3, FALSE, NULL),
    (p_company_id, 'B01', '419', '9. Quỹ hỗ trợ sắp xếp doanh nghiệp', '9. Enterprise restructuring fund', '4171*', 'SUM', 104, 3, FALSE, NULL),
    (p_company_id, 'B01', '420', '10. Quỹ khác thuộc vốn chủ sở hữu', '10. Other owner''s equity funds', '4172*,4174*,4175*,4178*', 'SUM', 105, 3, FALSE, NULL),
    (p_company_id, 'B01', '421', '11. Lợi nhuận sau thuế chưa phân phối', '11. Undistributed post-tax profits', '421*', 'SUM', 106, 3, FALSE, NULL),
    (p_company_id, 'B01', '421a', '- LNST chưa phân phối lũy kế đến cuối kỳ trước', '- Accumulated prior periods undistributed profits', '4211*', 'SUM', 107, 4, FALSE, NULL),
    (p_company_id, 'B01', '421b', '- LNST chưa phân phối kỳ này', '- Current period undistributed profits', '4212*', 'SUM', 108, 4, FALSE, NULL),
    (p_company_id, 'B01', '430', 'II. Nguồn kinh phí và quỹ khác', 'II. Other funds and sources', '431+432', 'SUM', 109, 2, TRUE, '431+432'),
    (p_company_id, 'B01', '431', '1. Nguồn kinh phí', '1. Funding sources', '461*,466*', 'SUM', 110, 3, FALSE, NULL),
    (p_company_id, 'B01', '432', '2. Nguồn kinh phí đã hình thành TSCĐ', '2. Fixed assets formation fund', '466*', 'SUM', 111, 3, FALSE, NULL),

    -- TỔNG CỘNG NGUỒN VỐN (TOTAL EQUITY AND LIABILITIES)
    (p_company_id, 'B01', '440', 'TỔNG CỘNG NGUỒN VỐN (440 = 300 + 400)', 'TOTAL EQUITY AND LIABILITIES (440 = 300 + 400)', '300+400', 'SUM', 112, 1, TRUE, '300+400');
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- B02-DN: BÁO CÁO KẾT QUẢ HOẠT ĐỘNG KINH DOANH (INCOME STATEMENT)
-- =====================================================

CREATE OR REPLACE FUNCTION seed_b02_mappings(p_company_id BIGINT) RETURNS VOID AS $$
BEGIN
    -- Skip if mappings already exist for this company
    IF EXISTS (SELECT 1 FROM report_mappings WHERE company_id = p_company_id AND report_type = 'B02') THEN
        RETURN;
    END IF;

    INSERT INTO report_mappings (company_id, report_type, line_code, line_name, line_name_english, account_pattern, operator, display_order, level, is_calculated, formula) VALUES
    (p_company_id, 'B02', '01', '1. Doanh thu bán hàng và cung cấp dịch vụ', '1. Revenue from sale of goods and provision of services', '511*,512*', 'SUM', 1, 1, FALSE, NULL),
    (p_company_id, 'B02', '02', '2. Các khoản giảm trừ doanh thu', '2. Revenue deductions', '521*', 'SUM', 2, 1, FALSE, NULL),
    (p_company_id, 'B02', '10', '3. Doanh thu thuần về bán hàng và cung cấp dịch vụ (10 = 01 - 02)', '3. Net revenue from sale of goods and provision of services (10 = 01 - 02)', '01-02', 'DIFF', 3, 1, TRUE, '01-02'),
    (p_company_id, 'B02', '11', '4. Giá vốn hàng bán', '4. Cost of goods sold', '632*', 'SUM', 4, 1, FALSE, NULL),
    (p_company_id, 'B02', '20', '5. Lợi nhuận gộp về bán hàng và cung cấp dịch vụ (20 = 10 - 11)', '5. Gross profit from sale of goods and provision of services (20 = 10 - 11)', '10-11', 'DIFF', 5, 1, TRUE, '10-11'),
    (p_company_id, 'B02', '21', '6. Doanh thu hoạt động tài chính', '6. Financial income', '515*', 'SUM', 6, 1, FALSE, NULL),
    (p_company_id, 'B02', '22', '7. Chi phí tài chính', '7. Financial expenses', '635*', 'SUM', 7, 1, FALSE, NULL),
    (p_company_id, 'B02', '23', '- Trong đó: Chi phí lãi vay', '- Of which: Interest expense', '6351*', 'SUM', 8, 2, FALSE, NULL),
    (p_company_id, 'B02', '24', '8. Phần lãi/lỗ trong công ty liên kết, liên doanh', '8. Share of profit/loss of associates and joint ventures', '515*', 'SUM', 9, 1, FALSE, NULL),
    (p_company_id, 'B02', '25', '9. Chi phí bán hàng', '9. Selling expenses', '641*', 'SUM', 10, 1, FALSE, NULL),
    (p_company_id, 'B02', '26', '10. Chi phí quản lý doanh nghiệp', '10. General and administrative expenses', '642*', 'SUM', 11, 1, FALSE, NULL),
    (p_company_id, 'B02', '30', '11. Lợi nhuận thuần từ hoạt động kinh doanh (30 = 20 + (21 - 22) + 24 - 25 - 26)', '11. Net operating profit (30 = 20 + (21 - 22) + 24 - 25 - 26)', '20+21-22+24-25-26', 'CALC', 12, 1, TRUE, '20+21-22+24-25-26'),
    (p_company_id, 'B02', '31', '12. Thu nhập khác', '12. Other income', '711*', 'SUM', 13, 1, FALSE, NULL),
    (p_company_id, 'B02', '32', '13. Chi phí khác', '13. Other expenses', '811*', 'SUM', 14, 1, FALSE, NULL),
    (p_company_id, 'B02', '40', '14. Lợi nhuận khác (40 = 31 - 32)', '14. Other profit (40 = 31 - 32)', '31-32', 'DIFF', 15, 1, TRUE, '31-32'),
    (p_company_id, 'B02', '50', '15. Tổng lợi nhuận kế toán trước thuế (50 = 30 + 40)', '15. Total accounting profit before tax (50 = 30 + 40)', '30+40', 'SUM', 16, 1, TRUE, '30+40'),
    (p_company_id, 'B02', '51', '16. Chi phí thuế TNDN hiện hành', '16. Current corporate income tax expense', '8211*', 'SUM', 17, 1, FALSE, NULL),
    (p_company_id, 'B02', '52', '17. Chi phí thuế TNDN hoãn lại', '17. Deferred corporate income tax expense', '8212*', 'SUM', 18, 1, FALSE, NULL),
    (p_company_id, 'B02', '60', '18. Lợi nhuận sau thuế thu nhập doanh nghiệp (60 = 50 - 51 - 52)', '18. Net profit after corporate income tax (60 = 50 - 51 - 52)', '50-51-52', 'DIFF', 19, 1, TRUE, '50-51-52'),
    (p_company_id, 'B02', '61', '18.1. Lợi nhuận sau thuế của công ty mẹ', '18.1. Post-tax profit attributable to parent company', '60*', 'SUM', 20, 2, FALSE, NULL),
    (p_company_id, 'B02', '62', '18.2. Lợi nhuận sau thuế của cổ đông không kiểm soát', '18.2. Post-tax profit attributable to non-controlling interests', '60*', 'SUM', 21, 2, FALSE, NULL),
    (p_company_id, 'B02', '70', '19. Lãi cơ bản trên cổ phiếu', '19. Basic earnings per share', 'CALCULATED', 'CALC', 22, 1, TRUE, NULL),
    (p_company_id, 'B02', '71', '20. Lãi suy giảm trên cổ phiếu', '20. Diluted earnings per share', 'CALCULATED', 'CALC', 23, 1, TRUE, NULL);
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- B03-DN: BÁO CÁO LƯU CHUYỂN TIỀN TỆ (CASH FLOW STATEMENT - DIRECT METHOD)
-- =====================================================

CREATE OR REPLACE FUNCTION seed_b03_mappings(p_company_id BIGINT) RETURNS VOID AS $$
BEGIN
    -- Skip if mappings already exist for this company
    IF EXISTS (SELECT 1 FROM report_mappings WHERE company_id = p_company_id AND report_type = 'B03') THEN
        RETURN;
    END IF;

    INSERT INTO report_mappings (company_id, report_type, line_code, line_name, line_name_english, account_pattern, operator, display_order, level, is_calculated, formula) VALUES
    -- I. LƯU CHUYỂN TIỀN TỪ HOẠT ĐỘNG KINH DOANH (CASH FLOWS FROM OPERATING ACTIVITIES)
    (p_company_id, 'B03', 'I', 'I. Lưu chuyển tiền từ hoạt động kinh doanh', 'I. Cash flows from operating activities', 'SUM(01,02,03,04,05,06,07)', 'SUM', 1, 1, TRUE, '01+02+03+04+05+06+07'),
    (p_company_id, 'B03', '01', '1. Tiền thu từ bán hàng, cung cấp dịch vụ và doanh thu khác', '1. Cash receipts from sale of goods and services', '111*,112*,113*', 'SUM', 2, 2, FALSE, NULL),
    (p_company_id, 'B03', '02', '2. Tiền chi trả cho người cung cấp hàng hóa và dịch vụ', '2. Cash payments to suppliers', '111*,112*,113*', 'SUM', 3, 2, FALSE, NULL),
    (p_company_id, 'B03', '03', '3. Tiền chi trả cho người lao động', '3. Cash payments to employees', '111*,112*,113*', 'SUM', 4, 2, FALSE, NULL),
    (p_company_id, 'B03', '04', '4. Tiền lãi vay đã trả', '4. Interest paid', '111*,112*,113*', 'SUM', 5, 2, FALSE, NULL),
    (p_company_id, 'B03', '05', '5. Thuế TNDN đã nộp', '5. Corporate income tax paid', '111*,112*,113*', 'SUM', 6, 2, FALSE, NULL),
    (p_company_id, 'B03', '06', '6. Tiền thu khác từ hoạt động kinh doanh', '6. Other cash receipts from operating activities', '111*,112*,113*', 'SUM', 7, 2, FALSE, NULL),
    (p_company_id, 'B03', '07', '7. Tiền chi khác cho hoạt động kinh doanh', '7. Other cash payments for operating activities', '111*,112*,113*', 'SUM', 8, 2, FALSE, NULL),
    (p_company_id, 'B03', '20', 'Lưu chuyển tiền thuần từ hoạt động kinh doanh', 'Net cash flows from operating activities', '01+02+03+04+05+06+07', 'SUM', 9, 1, TRUE, '01+02+03+04+05+06+07'),

    -- II. LƯU CHUYỂN TIỀN TỪ HOẠT ĐỘNG ĐẦU TƯ (CASH FLOWS FROM INVESTING ACTIVITIES)
    (p_company_id, 'B03', 'II', 'II. Lưu chuyển tiền từ hoạt động đầu tư', 'II. Cash flows from investing activities', 'SUM(21,22,23,24,25,26,27)', 'SUM', 10, 1, TRUE, '21+22+23+24+25+26+27'),
    (p_company_id, 'B03', '21', '1. Tiền chi để mua sắm, xây dựng TSCĐ và các tài sản dài hạn khác', '1. Cash payments for acquisition of fixed assets', '111*,112*,113*', 'SUM', 11, 2, FALSE, NULL),
    (p_company_id, 'B03', '22', '2. Tiền thu từ thanh lý, nhượng bán TSCĐ và các tài sản dài hạn khác', '2. Cash receipts from disposal of fixed assets', '111*,112*,113*', 'SUM', 12, 2, FALSE, NULL),
    (p_company_id, 'B03', '23', '3. Tiền chi cho vay, mua các công cụ nợ của đơn vị khác', '3. Cash payments for loans and debt instruments', '111*,112*,113*', 'SUM', 13, 2, FALSE, NULL),
    (p_company_id, 'B03', '24', '4. Tiền thu hồi cho vay, bán lại các công cụ nợ của đơn vị khác', '4. Cash receipts from repayment of loans and debt instruments', '111*,112*,113*', 'SUM', 14, 2, FALSE, NULL),
    (p_company_id, 'B03', '25', '5. Tiền chi đầu tư góp vốn vào đơn vị khác', '5. Cash payments for equity investments', '111*,112*,113*', 'SUM', 15, 2, FALSE, NULL),
    (p_company_id, 'B03', '26', '6. Tiền thu hồi đầu tư góp vốn vào đơn vị khác', '6. Cash receipts from disposal of equity investments', '111*,112*,113*', 'SUM', 16, 2, FALSE, NULL),
    (p_company_id, 'B03', '27', '7. Tiền thu lãi cho vay, cổ tức và lợi nhuận được chia', '7. Cash receipts from interest, dividends and profits', '111*,112*,113*', 'SUM', 17, 2, FALSE, NULL),
    (p_company_id, 'B03', '30', 'Lưu chuyển tiền thuần từ hoạt động đầu tư', 'Net cash flows from investing activities', '21+22+23+24+25+26+27', 'SUM', 18, 1, TRUE, '21+22+23+24+25+26+27'),

    -- III. LƯU CHUYỂN TIỀN TỪ HOẠT ĐỘNG TÀI CHÍNH (CASH FLOWS FROM FINANCING ACTIVITIES)
    (p_company_id, 'B03', 'III', 'III. Lưu chuyển tiền từ hoạt động tài chính', 'III. Cash flows from financing activities', 'SUM(31,32,33,34,35,36)', 'SUM', 19, 1, TRUE, '31+32+33+34+35+36'),
    (p_company_id, 'B03', '31', '1. Tiền thu từ phát hành cổ phiếu, nhận vốn góp của chủ sở hữu', '1. Cash receipts from issuing shares and capital contribution', '111*,112*,113*', 'SUM', 20, 2, FALSE, NULL),
    (p_company_id, 'B03', '32', '2. Tiền trả lại vốn góp cho các chủ sở hữu, mua lại cổ phiếu của doanh nghiệp đã phát hành', '2. Cash payments for capital return and treasury shares', '111*,112*,113*', 'SUM', 21, 2, FALSE, NULL),
    (p_company_id, 'B03', '33', '3. Tiền thu từ đi vay', '3. Cash receipts from borrowings', '111*,112*,113*', 'SUM', 22, 2, FALSE, NULL),
    (p_company_id, 'B03', '34', '4. Tiền trả nợ gốc vay', '4. Cash payments for loan principal', '111*,112*,113*', 'SUM', 23, 2, FALSE, NULL),
    (p_company_id, 'B03', '35', '5. Tiền trả nợ gốc thuê tài chính', '5. Cash payments for finance lease principal', '111*,112*,113*', 'SUM', 24, 2, FALSE, NULL),
    (p_company_id, 'B03', '36', '6. Cổ tức, lợi nhuận đã trả cho chủ sở hữu', '6. Dividends and profits paid to owners', '111*,112*,113*', 'SUM', 25, 2, FALSE, NULL),
    (p_company_id, 'B03', '40', 'Lưu chuyển tiền thuần từ hoạt động tài chính', 'Net cash flows from financing activities', '31+32+33+34+35+36', 'SUM', 26, 1, TRUE, '31+32+33+34+35+36'),

    -- SUMMARY
    (p_company_id, 'B03', '50', 'Lưu chuyển tiền thuần trong kỳ (50 = 20 + 30 + 40)', 'Net cash flows for the period (50 = 20 + 30 + 40)', '20+30+40', 'SUM', 27, 1, TRUE, '20+30+40'),
    (p_company_id, 'B03', '60', 'Tiền và tương đương tiền đầu kỳ', 'Cash and cash equivalents at beginning of period', '111*,112*,113*', 'SUM', 28, 1, FALSE, NULL),
    (p_company_id, 'B03', '61', 'Ảnh hưởng của thay đổi tỷ giá hối đoái quy đổi ngoại tệ', 'Effect of exchange rate changes', '413*', 'SUM', 29, 1, FALSE, NULL),
    (p_company_id, 'B03', '70', 'Tiền và tương đương tiền cuối kỳ (70 = 50 + 60 + 61)', 'Cash and cash equivalents at end of period (70 = 50 + 60 + 61)', '50+60+61', 'SUM', 30, 1, TRUE, '50+60+61');
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- TRIGGER: Auto-seed mappings for new companies
-- =====================================================

CREATE OR REPLACE FUNCTION trigger_seed_report_mappings() RETURNS TRIGGER AS $$
BEGIN
    -- Seed all report type mappings for the new company
    PERFORM seed_b01_mappings(NEW.id);
    PERFORM seed_b02_mappings(NEW.id);
    PERFORM seed_b03_mappings(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on companies table
DROP TRIGGER IF EXISTS trg_seed_report_mappings ON companies;
CREATE TRIGGER trg_seed_report_mappings
    AFTER INSERT ON companies
    FOR EACH ROW
    EXECUTE FUNCTION trigger_seed_report_mappings();

-- =====================================================
-- SEED EXISTING COMPANIES
-- =====================================================

-- Seed mappings for all existing companies
DO $$
DECLARE
    company_record RECORD;
BEGIN
    FOR company_record IN SELECT id FROM companies LOOP
        PERFORM seed_b01_mappings(company_record.id);
        PERFORM seed_b02_mappings(company_record.id);
        PERFORM seed_b03_mappings(company_record.id);
    END LOOP;
END $$;

-- Add comments
COMMENT ON FUNCTION seed_b01_mappings(BIGINT) IS 'Seeds B01-DN Balance Sheet mappings for a company per TT200';
COMMENT ON FUNCTION seed_b02_mappings(BIGINT) IS 'Seeds B02-DN Income Statement mappings for a company per TT200';
COMMENT ON FUNCTION seed_b03_mappings(BIGINT) IS 'Seeds B03-DN Cash Flow Statement mappings for a company per TT200';
COMMENT ON FUNCTION trigger_seed_report_mappings() IS 'Trigger function to auto-seed report mappings for new companies';
