-- TT200 Chart of Accounts Seed Migration
-- Follows Circular 200/2014/TT-BTC standards
-- Seeds accounts for all existing companies
-- Auto-determines normal_side and postable flags

-- Function to seed COA for a specific company
CREATE OR REPLACE FUNCTION seed_tt200_coa_for_company(p_company_id BIGINT)
RETURNS VOID AS $$
DECLARE
  v_parent_id BIGINT;
  v_child_id BIGINT;
BEGIN
  -- 1xx: ASSETS (Short-term and Long-term)
  -- Root: 1 - Tài sản (Assets)
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '1', 'Tài sản', 'Asset', 'Debit', false, NULL, 1)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  -- 11x: Short-term Assets
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '11', 'Tài sản ngắn hạn', 'Asset', 'Debit', false, v_parent_id, 11)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  -- 111: Cash and Cash Equivalents
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '111', 'Tiền mặt', 'Asset', 'Debit', false, v_child_id, 111)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1111', 'Tiền Việt Nam', 'Asset', 'Debit', true, v_parent_id, 1111),
  (p_company_id, '1112', 'Ngoại tệ', 'Asset', 'Debit', true, v_parent_id, 1112),
  (p_company_id, '1113', 'Vàng bạc, đá quý', 'Asset', 'Debit', true, v_parent_id, 1113);

  -- 112: Bank Deposits
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '112', 'Tiền gửi ngân hàng', 'Asset', 'Debit', false, v_child_id, 112)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1121', 'Tiền Việt Nam', 'Asset', 'Debit', true, v_parent_id, 1121),
  (p_company_id, '1122', 'Ngoại tệ', 'Asset', 'Debit', true, v_parent_id, 1122);

  -- 113: Short-term Investments
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '113', 'Đầu tư tài chính ngắn hạn', 'Asset', 'Debit', false, v_child_id, 113)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1131', 'Đầu tư chứng khoán ngắn hạn', 'Asset', 'Debit', true, v_parent_id, 1131),
  (p_company_id, '1132', 'Đầu tư ngắn hạn khác', 'Asset', 'Debit', true, v_parent_id, 1132);

  -- 131: Receivables from Customers
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '131', 'Phải thu của khách hàng', 'Asset', 'Debit', false, v_child_id, 131)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1311', 'Phải thu của khách hàng - Chi tiết theo đối tượng', 'Asset', 'Debit', true, v_parent_id, 1311),
  (p_company_id, '1312', 'Trả trước cho người bán', 'Asset', 'Debit', true, v_parent_id, 1312);

  -- 133: Other Receivables
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '133', 'Phải thu khác', 'Asset', 'Debit', false, v_child_id, 133)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1331', 'Phải thu khác - Chi tiết', 'Asset', 'Debit', true, v_parent_id, 1331),
  (p_company_id, '1332', 'Phải thu nội bộ', 'Asset', 'Debit', true, v_parent_id, 1332);

  -- 136: Prepaid Expenses
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '136', 'Tạm ứng', 'Asset', 'Debit', false, v_child_id, 136)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1361', 'Tạm ứng', 'Asset', 'Debit', true, v_parent_id, 1361),
  (p_company_id, '1362', 'Tạm ứng cho nhà cung cấp', 'Asset', 'Debit', true, v_parent_id, 1362);

  -- 141: Inventory
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '141', 'Hàng tồn kho', 'Asset', 'Debit', false, v_child_id, 141)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1411', 'Nguyên liệu, vật liệu', 'Asset', 'Debit', true, v_parent_id, 1411),
  (p_company_id, '1412', 'Công cụ, dụng cụ', 'Asset', 'Debit', true, v_parent_id, 1412),
  (p_company_id, '1413', 'Chi phí sản xuất, kinh doanh dở dang', 'Asset', 'Debit', true, v_parent_id, 1413),
  (p_company_id, '1414', 'Thành phẩm', 'Asset', 'Debit', true, v_parent_id, 1414),
  (p_company_id, '1415', 'Hàng hóa', 'Asset', 'Debit', true, v_parent_id, 1415),
  (p_company_id, '1416', 'Hàng gửi đi bán', 'Asset', 'Debit', true, v_parent_id, 1416);

  -- 15x: Other Short-term Assets
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '151', 'Tài sản ngắn hạn khác', 'Asset', 'Debit', false, v_child_id, 151)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1511', 'Chi phí trả trước ngắn hạn', 'Asset', 'Debit', true, v_parent_id, 1511),
  (p_company_id, '1512', 'Thuế GTGT được khấu trừ', 'Asset', 'Debit', true, v_parent_id, 1512),
  (p_company_id, '1513', 'Thuế và các khoản phải thu Nhà nước', 'Asset', 'Debit', true, v_parent_id, 1513);

  -- 16x: Long-term Assets
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '16', 'Tài sản dài hạn', 'Asset', 'Debit', false, (SELECT id FROM chart_of_accounts WHERE company_id = p_company_id AND code = '1'), 16)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '161', 'Tài sản cố định', 'Asset', 'Debit', false, v_child_id, 161)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1611', 'Tài sản cố định hữu hình', 'Asset', 'Debit', false, v_parent_id, 1611),
  (p_company_id, '1612', 'Tài sản cố định vô hình', 'Asset', 'Debit', false, v_parent_id, 1612);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '162', 'Hao mòn tài sản cố định', 'Asset', 'Credit', false, v_child_id, 162)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '1621', 'Hao mòn TSCĐ hữu hình', 'Asset', 'Credit', true, v_parent_id, 1621),
  (p_company_id, '1622', 'Hao mòn TSCĐ vô hình', 'Asset', 'Credit', true, v_parent_id, 1622);

  -- 2xx: FIXED ASSETS (Depreciation)
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '2', 'Tài sản cố định', 'Asset', 'Debit', false, NULL, 2)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '211', 'Tài sản cố định hữu hình', 'Asset', 'Debit', false, v_parent_id, 211)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '2111', 'Nhà cửa, vật kiến trúc', 'Asset', 'Debit', true, v_child_id, 2111),
  (p_company_id, '2112', 'Máy móc, thiết bị', 'Asset', 'Debit', true, v_child_id, 2112),
  (p_company_id, '2113', 'Phương tiện vận tải', 'Asset', 'Debit', true, v_child_id, 2113),
  (p_company_id, '2114', 'Thiết bị dụng cụ quản lý', 'Asset', 'Debit', true, v_child_id, 2114),
  (p_company_id, '2115', 'Cây lâu năm, súc vật làm việc và cho sản phẩm', 'Asset', 'Debit', true, v_child_id, 2115),
  (p_company_id, '2116', 'Tài sản cố định khác', 'Asset', 'Debit', true, v_child_id, 2116);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '213', 'Tài sản cố định vô hình', 'Asset', 'Debit', false, v_parent_id, 213)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '2131', 'Quyền sử dụng đất', 'Asset', 'Debit', true, v_child_id, 2131),
  (p_company_id, '2132', 'Quyền phát hành', 'Asset', 'Debit', true, v_child_id, 2132),
  (p_company_id, '2133', 'Bằng phát minh sáng chế', 'Asset', 'Debit', true, v_child_id, 2133),
  (p_company_id, '2134', 'Bản quyền, tác giả', 'Asset', 'Debit', true, v_child_id, 2134),
  (p_company_id, '2135', 'Phần mềm máy vi tính', 'Asset', 'Debit', true, v_child_id, 2135),
  (p_company_id, '2136', 'Giấy phép và giấy phép nhượng quyền', 'Asset', 'Debit', true, v_child_id, 2136),
  (p_company_id, '2137', 'Nhãn hiệu hàng hóa', 'Asset', 'Debit', true, v_child_id, 2137),
  (p_company_id, '2138', 'Tài sản cố định vô hình khác', 'Asset', 'Debit', true, v_child_id, 2138);

  -- 3xx: LIABILITIES
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '3', 'Nợ phải trả', 'Liability', 'Credit', false, NULL, 3)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '331', 'Phải trả người bán', 'Liability', 'Credit', false, v_parent_id, 331)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '3311', 'Phải trả người bán - Chi tiết theo đối tượng', 'Liability', 'Credit', true, v_child_id, 3311),
  (p_company_id, '3312', 'Phải trả người bán - Hàng mua trả lại', 'Liability', 'Credit', true, v_child_id, 3312);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '333', 'Thuế và các khoản phải nộp Nhà nước', 'Liability', 'Credit', false, v_parent_id, 333)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '3331', 'Thuế GTGT phải nộp', 'Liability', 'Credit', true, v_child_id, 3331),
  (p_company_id, '3332', 'Thuế tiêu thụ đặc biệt', 'Liability', 'Credit', true, v_child_id, 3332),
  (p_company_id, '3333', 'Thuế xuất, nhập khẩu', 'Liability', 'Credit', true, v_child_id, 3333),
  (p_company_id, '3334', 'Thuế thu nhập doanh nghiệp', 'Liability', 'Credit', true, v_child_id, 3334),
  (p_company_id, '3335', 'Thuế thu nhập cá nhân', 'Liability', 'Credit', true, v_child_id, 3335),
  (p_company_id, '3336', 'Thuế tài nguyên', 'Liability', 'Credit', true, v_child_id, 3336),
  (p_company_id, '3337', 'Thuế nhà đất, tiền thuê đất', 'Liability', 'Credit', true, v_child_id, 3337),
  (p_company_id, '3338', 'Các loại thuế khác', 'Liability', 'Credit', true, v_child_id, 3338),
  (p_company_id, '3339', 'Phí, lệ phí và các khoản phải nộp khác', 'Liability', 'Credit', true, v_child_id, 3339);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '334', 'Phải trả người lao động', 'Liability', 'Credit', false, v_parent_id, 334)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '3341', 'Phải trả công nhân viên', 'Liability', 'Credit', true, v_child_id, 3341),
  (p_company_id, '3342', 'Phải trả về các khoản khác', 'Liability', 'Credit', true, v_child_id, 3342),
  (p_company_id, '3348', 'Phải trả, phải nộp khác', 'Liability', 'Credit', true, v_child_id, 3348);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '338', 'Phải trả, phải nộp khác', 'Liability', 'Credit', false, v_parent_id, 338)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '3381', 'Tài sản thừa chờ xử lý', 'Liability', 'Credit', true, v_child_id, 3381),
  (p_company_id, '3382', 'Kinh phí công đoàn', 'Liability', 'Credit', true, v_child_id, 3382),
  (p_company_id, '3383', 'Bảo hiểm xã hội', 'Liability', 'Credit', true, v_child_id, 3383),
  (p_company_id, '3384', 'Bảo hiểm y tế', 'Liability', 'Credit', true, v_child_id, 3384),
  (p_company_id, '3385', 'Bảo hiểm thất nghiệp', 'Liability', 'Credit', true, v_child_id, 3385),
  (p_company_id, '3386', 'Bảo hiểm tai nạn lao động - bệnh nghề nghiệp', 'Liability', 'Credit', true, v_child_id, 3386),
  (p_company_id, '3387', 'Doanh thu chưa thực hiện', 'Liability', 'Credit', true, v_child_id, 3387),
  (p_company_id, '3388', 'Phải trả, phải nộp khác', 'Liability', 'Credit', true, v_child_id, 3388);

  -- 4xx: EQUITY
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '4', 'Vốn chủ sở hữu', 'Equity', 'Credit', false, NULL, 4)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '411', 'Vốn đầu tư của chủ sở hữu', 'Equity', 'Credit', false, v_parent_id, 411)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '4111', 'Vốn góp của chủ sở hữu', 'Equity', 'Credit', true, v_child_id, 4111),
  (p_company_id, '4112', 'Thặng dư vốn cổ phần', 'Equity', 'Credit', true, v_child_id, 4112),
  (p_company_id, '4118', 'Vốn khác của chủ sở hữu', 'Equity', 'Credit', true, v_child_id, 4118);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '421', 'Lợi nhuận sau thuế chưa phân phối', 'Equity', 'Credit', false, v_parent_id, 421)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '4211', 'Lợi nhuận sau thuế chưa phân phối năm trước', 'Equity', 'Credit', true, v_child_id, 4211),
  (p_company_id, '4212', 'Lợi nhuận sau thuế chưa phân phối năm nay', 'Equity', 'Credit', true, v_child_id, 4212);

  -- 5xx: REVENUE
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '5', 'Doanh thu', 'Revenue', 'Credit', false, NULL, 5)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '511', 'Doanh thu bán hàng và cung cấp dịch vụ', 'Revenue', 'Credit', false, v_parent_id, 511)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '5111', 'Doanh thu bán hàng hóa', 'Revenue', 'Credit', true, v_child_id, 5111),
  (p_company_id, '5112', 'Doanh thu bán thành phẩm', 'Revenue', 'Credit', true, v_child_id, 5112),
  (p_company_id, '5113', 'Doanh thu cung cấp dịch vụ', 'Revenue', 'Credit', true, v_child_id, 5113),
  (p_company_id, '5114', 'Doanh thu trợ cấp, trợ giá', 'Revenue', 'Credit', true, v_child_id, 5114),
  (p_company_id, '5117', 'Doanh thu kinh doanh bất động sản đầu tư', 'Revenue', 'Credit', true, v_child_id, 5117),
  (p_company_id, '5118', 'Doanh thu khác', 'Revenue', 'Credit', true, v_child_id, 5118);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '515', 'Doanh thu hoạt động tài chính', 'Revenue', 'Credit', false, v_parent_id, 515)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '5151', 'Thu nhập từ lãi cho vay', 'Revenue', 'Credit', true, v_child_id, 5151),
  (p_company_id, '5152', 'Thu nhập từ cổ tức, lợi nhuận được chia', 'Revenue', 'Credit', true, v_child_id, 5152),
  (p_company_id, '5153', 'Thu nhập từ hoạt động đầu tư khác', 'Revenue', 'Credit', true, v_child_id, 5153);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '521', 'Chiết khấu thương mại', 'Revenue', 'Debit', false, v_parent_id, 521)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '5211', 'Chiết khấu thương mại hàng hóa', 'Revenue', 'Debit', true, v_child_id, 5211),
  (p_company_id, '5212', 'Chiết khấu thương mại thành phẩm', 'Revenue', 'Debit', true, v_child_id, 5212),
  (p_company_id, '5213', 'Chiết khấu thương mại dịch vụ', 'Revenue', 'Debit', true, v_child_id, 5213);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '531', 'Hàng bán bị trả lại', 'Revenue', 'Debit', false, v_parent_id, 531)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '5311', 'Hàng bán bị trả lại', 'Revenue', 'Debit', true, v_child_id, 5311);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '532', 'Giảm giá hàng bán', 'Revenue', 'Debit', false, v_parent_id, 532)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '5321', 'Giảm giá hàng bán', 'Revenue', 'Debit', true, v_child_id, 5321);

  -- 6xx: PRODUCTION COSTS
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '6', 'Chi phí sản xuất, kinh doanh', 'Expense', 'Debit', false, NULL, 6)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '621', 'Chi phí nguyên vật liệu trực tiếp', 'Expense', 'Debit', false, v_parent_id, 621)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6211', 'Chi phí nguyên vật liệu trực tiếp', 'Expense', 'Debit', true, v_child_id, 6211);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '622', 'Chi phí nhân công trực tiếp', 'Expense', 'Debit', false, v_parent_id, 622)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6221', 'Chi phí nhân công trực tiếp', 'Expense', 'Debit', true, v_child_id, 6221);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '623', 'Chi phí sử dụng máy thi công', 'Expense', 'Debit', false, v_parent_id, 623)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6231', 'Chi phí khấu hao máy thi công', 'Expense', 'Debit', true, v_child_id, 6231),
  (p_company_id, '6232', 'Chi phí sửa chữa máy thi công', 'Expense', 'Debit', true, v_child_id, 6232),
  (p_company_id, '6233', 'Chi phí nhiên liệu cho máy thi công', 'Expense', 'Debit', true, v_child_id, 6233),
  (p_company_id, '6234', 'Chi phí khác cho máy thi công', 'Expense', 'Debit', true, v_child_id, 6234);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '627', 'Chi phí sản xuất chung', 'Expense', 'Debit', false, v_parent_id, 627)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6271', 'Chi phí nhân viên phân xưởng', 'Expense', 'Debit', true, v_child_id, 6271),
  (p_company_id, '6272', 'Chi phí vật liệu', 'Expense', 'Debit', true, v_child_id, 6272),
  (p_company_id, '6273', 'Chi phí dụng cụ sản xuất', 'Expense', 'Debit', true, v_child_id, 6273),
  (p_company_id, '6274', 'Chi phí khấu hao TSCĐ', 'Expense', 'Debit', true, v_child_id, 6274),
  (p_company_id, '6277', 'Chi phí dịch vụ mua ngoài', 'Expense', 'Debit', true, v_child_id, 6277),
  (p_company_id, '6278', 'Chi phí bằng tiền khác', 'Expense', 'Debit', true, v_child_id, 6278);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '632', 'Giá vốn hàng bán', 'Expense', 'Debit', false, v_parent_id, 632)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6321', 'Giá vốn hàng bán', 'Expense', 'Debit', true, v_child_id, 6321);

  -- 7xx: OPERATING EXPENSES
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '7', 'Chi phí hoạt động tài chính và chi phí khác', 'Expense', 'Debit', false, NULL, 7)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '641', 'Chi phí bán hàng', 'Expense', 'Debit', false, v_parent_id, 641)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6411', 'Chi phí nhân viên bán hàng', 'Expense', 'Debit', true, v_child_id, 6411),
  (p_company_id, '6412', 'Chi phí vật liệu, bao bì', 'Expense', 'Debit', true, v_child_id, 6412),
  (p_company_id, '6413', 'Chi phí dụng cụ, đồ dùng', 'Expense', 'Debit', true, v_child_id, 6413),
  (p_company_id, '6414', 'Chi phí khấu hao TSCĐ', 'Expense', 'Debit', true, v_child_id, 6414),
  (p_company_id, '6415', 'Chi phí bảo hành', 'Expense', 'Debit', true, v_child_id, 6415),
  (p_company_id, '6417', 'Chi phí dịch vụ mua ngoài', 'Expense', 'Debit', true, v_child_id, 6417),
  (p_company_id, '6418', 'Chi phí bằng tiền khác', 'Expense', 'Debit', true, v_child_id, 6418);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '642', 'Chi phí quản lý doanh nghiệp', 'Expense', 'Debit', false, v_parent_id, 642)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6421', 'Chi phí nhân viên quản lý', 'Expense', 'Debit', true, v_child_id, 6421),
  (p_company_id, '6422', 'Chi phí vật liệu quản lý', 'Expense', 'Debit', true, v_child_id, 6422),
  (p_company_id, '6423', 'Chi phí đồ dùng văn phòng', 'Expense', 'Debit', true, v_child_id, 6423),
  (p_company_id, '6424', 'Chi phí khấu hao TSCĐ', 'Expense', 'Debit', true, v_child_id, 6424),
  (p_company_id, '6425', 'Thuế, phí và lệ phí', 'Expense', 'Debit', true, v_child_id, 6425),
  (p_company_id, '6426', 'Chi phí dự phòng', 'Expense', 'Debit', true, v_child_id, 6426),
  (p_company_id, '6427', 'Chi phí dịch vụ mua ngoài', 'Expense', 'Debit', true, v_child_id, 6427),
  (p_company_id, '6428', 'Chi phí bằng tiền khác', 'Expense', 'Debit', true, v_child_id, 6428);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '635', 'Chi phí tài chính', 'Expense', 'Debit', false, v_parent_id, 635)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '6351', 'Chi phí lãi vay', 'Expense', 'Debit', true, v_child_id, 6351),
  (p_company_id, '6352', 'Chi phí bán ngoại tệ', 'Expense', 'Debit', true, v_child_id, 6352),
  (p_company_id, '6353', 'Chi phí đầu tư tài chính', 'Expense', 'Debit', true, v_child_id, 6353),
  (p_company_id, '6354', 'Chi phí giao dịch bán chứng khoán', 'Expense', 'Debit', true, v_child_id, 6354),
  (p_company_id, '6355', 'Chi phí khác', 'Expense', 'Debit', true, v_child_id, 6355);

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '811', 'Chi phí khác', 'Expense', 'Debit', false, v_parent_id, 811)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '8111', 'Chi phí khác', 'Expense', 'Debit', true, v_child_id, 8111);

  -- 8xx: OTHER INCOME/EXPENSES
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '8', 'Thu nhập khác và chi phí khác', 'Revenue', 'Credit', false, NULL, 8)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '711', 'Thu nhập khác', 'Revenue', 'Credit', false, v_parent_id, 711)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_child_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position) VALUES
  (p_company_id, '7111', 'Thu nhập khác', 'Revenue', 'Credit', true, v_child_id, 7111);

  -- 9xx: FINANCIAL STATEMENT CLOSING
  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '9', 'Xác định kết quả kinh doanh', 'Revenue', 'Credit', false, NULL, 9)
  ON CONFLICT (company_id, code) DO NOTHING
  RETURNING id INTO v_parent_id;

  INSERT INTO chart_of_accounts (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
  VALUES (p_company_id, '911', 'Xác định kết quả kinh doanh', 'Revenue', 'Credit', true, v_parent_id, 911)
  ON CONFLICT (company_id, code) DO NOTHING;

  -- Update postable flags: set postable=false for accounts that have children
  UPDATE chart_of_accounts ca1
  SET postable = false
  WHERE company_id = p_company_id
    AND EXISTS (
      SELECT 1 FROM chart_of_accounts ca2
      WHERE ca2.company_id = p_company_id
        AND ca2.parent_id = ca1.id
    );

END;
$$ LANGUAGE plpgsql;

-- Seed COA for all existing companies
DO $$
DECLARE
  company_rec RECORD;
BEGIN
  FOR company_rec IN SELECT id FROM companies LOOP
    PERFORM seed_tt200_coa_for_company(company_rec.id);
  END LOOP;
END $$;

-- Drop the function after use (optional, can keep for future use)
-- DROP FUNCTION IF EXISTS seed_tt200_coa_for_company(BIGINT);

-- Validate seed data
DO $$
DECLARE
  account_count INTEGER;
BEGIN
  SELECT COUNT(*) INTO account_count
  FROM chart_of_accounts;
  
  IF account_count < 154 THEN
    RAISE EXCEPTION 'Seed migration failed: Expected at least 154 accounts, found %', account_count;
  END IF;
END $$;
