-- Phase 2.2-2.3: Seed TT200 Template from Official CSV
-- Source: Danh_sach_he_thong_tai_khoan_4.csv (235 accounts)
-- Mapping: Dư Nợ → Debit, Dư Có → Credit, Lưỡng tính → Both
-- Type derived from first digit: 1,2→Asset, 3→Liability, 4→Equity, 5→Revenue, 6,7,8,9→Expense

-- Insert parent accounts first (no parent_code), then children
-- Using multiple batches to respect FK constraint on parent_code

-- ============================================
-- BATCH 1: Level 1 accounts (3-digit codes, no parent)
-- ============================================
INSERT INTO tt200_chart_of_accounts_template (code, name_vi, name_english, normal_side, type, parent_code, ordering) VALUES
-- Class 1: Assets (Tài sản)
('111', 'Tiền mặt', 'Cash in hand', 'Debit', 'Asset', NULL, 1),
('112', 'Tiền gửi Ngân hàng', 'Cash in bank', 'Debit', 'Asset', NULL, 5),
('113', 'Tiền đang chuyển', 'Cash in transit', 'Debit', 'Asset', NULL, 9),
('121', 'Chứng khoán kinh doanh', 'Trading securities', 'Debit', 'Asset', NULL, 12),
('128', 'Đầu tư nắm giữ đến ngày đáo hạn', 'Held to maturity investment', 'Debit', 'Asset', NULL, 16),
('131', 'Phải thu của khách hàng', 'Receivables from customers', 'Both', 'Asset', NULL, 21),
('133', 'Thuế GTGT được khấu trừ', 'VAT receivable', 'Debit', 'Asset', NULL, 22),
('136', 'Phải thu nội bộ', 'Internal receivables', 'Debit', 'Asset', NULL, 25),
('138', 'Phải thu khác', 'Other receivable', 'Both', 'Asset', NULL, 30),
('141', 'Tạm ứng', 'Advances', 'Debit', 'Asset', NULL, 34),
('151', 'Hàng mua đang đi đường', 'Goods in transit', 'Debit', 'Asset', NULL, 35),
('152', 'Nguyên liệu, vật liệu', 'Raw materials', 'Debit', 'Asset', NULL, 36),
('153', 'Công cụ, dụng cụ', 'Tools and equipments', 'Debit', 'Asset', NULL, 37),
('154', 'Chi phí sản xuất, kinh doanh dở dang', 'Work in progress', 'Debit', 'Asset', NULL, 42),
('155', 'Thành phẩm', 'Finished goods', 'Debit', 'Asset', NULL, 43),
('156', 'Hàng hóa', 'Goods', 'Debit', 'Asset', NULL, 46),
('157', 'Hàng gửi đi bán', 'Goods on consignment', 'Debit', 'Asset', NULL, 50),
('158', 'Hàng hóa kho bảo thuế', 'Goods in bonded factory', 'Debit', 'Asset', NULL, 51),
('161', 'Chi sự nghiệp', 'Expenditures from subsidies of state budget', 'Debit', 'Asset', NULL, 52),
('171', 'Giao dịch mua bán lại trái phiếu chính phủ', 'Treasury bonds purchased for resale', 'Both', 'Asset', NULL, 55),
-- Class 2: Fixed Assets (Tài sản cố định)
('211', 'Tài sản cố định hữu hình', 'Tangible fixed assets', 'Debit', 'Asset', NULL, 56),
('212', 'Tài sản cố định thuê tài chính', 'Financial leasing fixed assets', 'Debit', 'Asset', NULL, 63),
('213', 'Tài sản cố định vô hình', 'Intangible fixed assets', 'Debit', 'Asset', NULL, 66),
('214', 'Hao mòn tài sản cố định', 'Accumulated depreciation - fixed assets', 'Credit', 'Asset', NULL, 74),
('217', 'Bất động sản đầu tư', 'Investmet property', 'Debit', 'Asset', NULL, 79),
('221', 'Đầu tư vào công ty con', 'Equity investments in subsidiaries', 'Debit', 'Asset', NULL, 80),
('222', 'Đầu tư vào công ty liên doanh, liên kết', 'Investments in joint ventures and associates', 'Debit', 'Asset', NULL, 81),
('228', 'Đầu tư khác', 'Other investments', 'Debit', 'Asset', NULL, 82),
('229', 'Dự phòng tổn thất tài sản', 'Provisions for impairment of assets', 'Credit', 'Asset', NULL, 85),
('241', 'Xây dựng cơ bản dở dang', 'Construction in process', 'Debit', 'Asset', NULL, 90),
('242', 'Chi phí trả trước', 'Prepaid expenses', 'Debit', 'Asset', NULL, 94),
('243', 'Tài sản thuế thu nhập hoãn lại', 'Deferred income tax assets', 'Debit', 'Asset', NULL, 95),
('244', 'Cầm cố, thế chấp, ký quỹ, ký cược', 'Mortgages, collateral, deposits', 'Debit', 'Asset', NULL, 96),
-- Class 3: Liabilities (Nợ phải trả)
('331', 'Phải trả cho người bán', 'Payable to suppliers', 'Both', 'Liability', NULL, 97),
('333', 'Thuế và các khoản phải nộp Nhà nước', 'Taxes and payable to state budget', 'Both', 'Liability', NULL, 98),
('334', 'Phải trả người lao động', 'Payable to employees', 'Credit', 'Liability', NULL, 112),
('335', 'Chi phí phải trả', 'Payable expenses', 'Credit', 'Liability', NULL, 115),
('336', 'Phải trả nội bộ', 'Internal payables', 'Credit', 'Liability', NULL, 116),
('337', 'Thanh toán theo tiến độ kế hoạch hợp đồng xây dựng', 'Construction contract''s progress payment due to customers', 'Both', 'Liability', NULL, 121),
('338', 'Phải trả, phải nộp khác', 'Other payable', 'Both', 'Liability', NULL, 122),
('341', 'Vay và nợ thuê tài chính', 'Borrowings and financial lease liabilities', 'Credit', 'Liability', NULL, 131),
('343', 'Trái phiếu phát hành', 'Issued bond', 'Credit', 'Liability', NULL, 134),
('344', 'Nhận ký quỹ, ký cược', 'Collaterals, deposits received', 'Credit', 'Liability', NULL, 140),
('347', 'Thuế thu nhập hoãn lại phải trả', 'Deferred income tax', 'Credit', 'Liability', NULL, 141),
('352', 'Dự phòng phải trả', 'Provisions for payables', 'Credit', 'Liability', NULL, 142),
('353', 'Quỹ khen thưởng, phúc lợi', 'Bonus & welfare funds', 'Credit', 'Liability', NULL, 147),
('356', 'Quỹ phát triển khoa học và công nghệ', 'Science and technology development fund', 'Credit', 'Liability', NULL, 152),
('357', 'Quỹ bình ổn giá', 'Price stabilisation fund', 'Credit', 'Liability', NULL, 155),
-- Class 4: Equity (Vốn chủ sở hữu)
('411', 'Vốn đầu tư của chủ sở hữu', 'Contributed legal capital', 'Credit', 'Equity', NULL, 156),
('412', 'Chênh lệch đánh giá lại tài sản', 'Differences upon asset revaluation', 'Both', 'Equity', NULL, 163),
('413', 'Chênh lệch tỷ giá hối đoái', 'Foreign exchange differences', 'Both', 'Equity', NULL, 164),
('414', 'Quỹ đầu tư phát triển', 'Investment & development funds', 'Credit', 'Equity', NULL, 167),
('417', 'Quỹ hỗ trợ sắp xếp doanh nghiệp', 'Enterprise reorganization assistance fund', 'Credit', 'Equity', NULL, 168),
('418', 'Các quỹ khác thuộc vốn chủ sở hữu', 'Other funds', 'Credit', 'Equity', NULL, 169),
('419', 'Cổ phiếu quỹ', 'Treasury share', 'Debit', 'Equity', NULL, 170),
('421', 'Lợi nhuận sau thuế chưa phân phối', 'Undistributed earnings', 'Both', 'Equity', NULL, 171),
('441', 'Nguồn vốn đầu tư xây dựng cơ bản', 'Construction investment fund', 'Credit', 'Equity', NULL, 174),
('461', 'Nguồn kinh phí sự nghiệp', 'Budget resources', 'Credit', 'Equity', NULL, 175),
('466', 'Nguồn kinh phí đã hình thành TSCĐ', 'Budget resources used to acquire fixed assets', 'Credit', 'Equity', NULL, 178),
-- Class 5: Revenue (Doanh thu)
('511', 'Doanh thu bán hàng và cung cấp dịch vụ', 'Revenue from sales of goods and provision of services', 'Both', 'Revenue', NULL, 179),
('515', 'Doanh thu hoạt động tài chính', 'Revenue from financial operations', 'Both', 'Revenue', NULL, 186),
('521', 'Các khoản giảm trừ doanh thu', 'Revenue deductions', 'Both', 'Revenue', NULL, 187),
-- Class 6: Expenses (Chi phí)
('611', 'Mua hàng', 'Cost of purchases', 'Both', 'Expense', NULL, 191),
('621', 'Chi phí nguyên liệu, vật liệu trực tiếp', 'Direct raw materials expense', 'Both', 'Expense', NULL, 194),
('622', 'Chi phí nhân công trực tiếp', 'Direct labor expense', 'Both', 'Expense', NULL, 195),
('623', 'Chi phí sử dụng máy thi công', 'Machinery expense', 'Both', 'Expense', NULL, 196),
('627', 'Chi phí sản xuất chung', 'General operation expense', 'Both', 'Expense', NULL, 203),
('631', 'Giá thành sản xuất', 'Cost of production', 'Both', 'Expense', NULL, 210),
('632', 'Giá vốn hàng bán', 'Cost of goods sold', 'Both', 'Expense', NULL, 211),
('635', 'Chi phí tài chính', 'Financial activities expenses', 'Both', 'Expense', NULL, 212),
('641', 'Chi phí bán hàng', 'Selling expenses', 'Both', 'Expense', NULL, 213),
('642', 'Chi phí quản lý doanh nghiệp', 'General & administration expenses', 'Both', 'Expense', NULL, 221),
-- Class 7: Other Income (Thu nhập khác)
('711', 'Thu nhập khác', 'Other income', 'Both', 'Revenue', NULL, 230),
-- Class 8: Other Expenses (Chi phí khác)
('811', 'Chi phí khác', 'Other expenses', 'Both', 'Expense', NULL, 231),
('821', 'Chi phí thuế thu nhập doanh nghiệp', 'Business Income tax charge', 'Both', 'Expense', NULL, 232),
-- Class 9: Profit/Loss Determination
('911', 'Xác định kết quả kinh doanh', 'Evaluation of business results', 'Both', 'Equity', NULL, 235);

-- ============================================
-- BATCH 2: Level 2 accounts (4-digit codes)
-- ============================================
INSERT INTO tt200_chart_of_accounts_template (code, name_vi, name_english, normal_side, type, parent_code, ordering) VALUES
-- 111x: Cash in hand
('1111', 'Tiền Việt Nam', 'Vietnam dong', 'Debit', 'Asset', '111', 2),
('1112', 'Ngoại tệ', 'Foreign currency', 'Debit', 'Asset', '111', 3),
('1113', 'Vàng tiền tệ', 'Monetary gold', 'Debit', 'Asset', '111', 4),
-- 112x: Cash in bank
('1121', 'Tiền Việt Nam', 'Vietnam dong', 'Debit', 'Asset', '112', 6),
('1122', 'Ngoại tệ', 'Foreign currency', 'Debit', 'Asset', '112', 7),
('1123', 'Vàng tiền tệ', 'Monetary gold', 'Debit', 'Asset', '112', 8),
-- 113x: Cash in transit
('1131', 'Tiền Việt Nam', 'Vietnam dong', 'Debit', 'Asset', '113', 10),
('1132', 'Ngoại tệ', 'Foreign currency', 'Debit', 'Asset', '113', 11),
-- 121x: Trading securities
('1211', 'Cổ phiếu', 'Shares', 'Debit', 'Asset', '121', 13),
('1212', 'Trái phiếu', 'Bond', 'Debit', 'Asset', '121', 14),
('1218', 'Chứng khoán và công cụ tài chính khác', 'Other securities and financial deprivations', 'Debit', 'Asset', '121', 15),
-- 128x: Held to maturity investment
('1281', 'Tiền gửi có kỳ hạn', 'Term deposits', 'Debit', 'Asset', '128', 17),
('1282', 'Trái phiếu', 'Bond', 'Debit', 'Asset', '128', 18),
('1283', 'Cho vay', 'Lending loans', 'Debit', 'Asset', '128', 19),
('1288', 'Các khoản đầu tư khác nắm giữ đến ngày đáo hạn', 'Other held to maturity investments', 'Debit', 'Asset', '128', 20),
-- 133x: VAT receivable
('1331', 'Thuế GTGT được khấu trừ của hàng hóa, dịch vụ', 'VAT receivable - goods and services', 'Debit', 'Asset', '133', 23),
('1332', 'Thuế GTGT được khấu trừ của TSCĐ', 'VAT receivable - fixed assets', 'Debit', 'Asset', '133', 24),
-- 136x: Internal receivables
('1361', 'Vốn kinh doanh ở các đơn vị trực thuộc', 'Investment in equity of subsidiaries', 'Debit', 'Asset', '136', 26),
('1362', 'Phải thu nội bộ về chênh lệch tỷ giá', 'Internal receivables on foreign exchange differences', 'Debit', 'Asset', '136', 27),
('1363', 'Phải thu nội bộ về chi phí đi vay đủ điều kiện được vốn hóa', 'Internal receivables on borrowing expenses eligible to be capitalised', 'Debit', 'Asset', '136', 28),
('1368', 'Phải thu nội bộ khác', 'Other internal receivables', 'Debit', 'Asset', '136', 29),
-- 138x: Other receivables
('1381', 'Tài sản thiếu chờ xử lý', 'Shortage of assets awaiting resolution', 'Both', 'Asset', '138', 31),
('1385', 'Phải thu về cổ phần hóa', 'Equitisation receivable', 'Both', 'Asset', '138', 32),
('1388', 'Phải thu khác', 'Other receivable', 'Both', 'Asset', '138', 33),
-- 153x: Tools and equipments
('1531', 'Công cụ, dụng cụ', 'Tools and equipments', 'Debit', 'Asset', '153', 38),
('1532', 'Bao bì luân chuyển', 'Reusable packaging materials', 'Debit', 'Asset', '153', 39),
('1533', 'Đồ dùng cho thuê', 'Instruments for renting', 'Debit', 'Asset', '153', 40),
('1534', 'Thiết bị, phụ tùng thay thế', 'Equipment, spare parts for replacement', 'Debit', 'Asset', '153', 41),
-- 155x: Finished goods
('1551', 'Thành phẩm nhập kho', 'Finished products', 'Debit', 'Asset', '155', 44),
('1557', 'Thành phẩm bất động sản', 'Finished real-estate', 'Debit', 'Asset', '155', 45),
-- 156x: Goods
('1561', 'Giá mua hàng hóa', 'Cost of purchases', 'Debit', 'Asset', '156', 47),
('1562', 'Chi phí thu mua hàng hóa', 'Purchasing expense', 'Debit', 'Asset', '156', 48),
('1567', 'Hàng hóa bất động sản', 'Real Estate', 'Debit', 'Asset', '156', 49),
-- 161x: Chi sự nghiệp
('1611', 'Chi sự nghiệp năm trước', 'Last year', 'Debit', 'Asset', '161', 53),
('1612', 'Chi sự nghiệp năm nay', 'This year', 'Debit', 'Asset', '161', 54),
-- 211x: Tangible fixed assets
('2111', 'Nhà cửa, vật kiến trúc', 'House, building', 'Debit', 'Asset', '211', 57),
('2112', 'Máy móc, thiết bị', 'Machinery, equipments', 'Debit', 'Asset', '211', 58),
('2113', 'Phương tiện vận tải, truyền dẫn', 'Means of transport, conveyance equipment', 'Debit', 'Asset', '211', 59),
('2114', 'Thiết bị, dụng cụ quản lý', 'Managerial equipment and instruments', 'Debit', 'Asset', '211', 60),
('2115', 'Cây lâu năm, súc vật làm việc và cho sản phẩm', 'Plants and livestocks', 'Debit', 'Asset', '211', 61),
('2118', 'TSCĐ khác', 'Other tangible fixed assets', 'Debit', 'Asset', '211', 62),
-- 212x: Financial leasing fixed assets
('2121', 'TSCĐ hữu hình thuê tài chính', 'Financial leasing tangible fixed assets ', 'Debit', 'Asset', '212', 64),
('2122', 'TSCĐ vô hình thuê tài chính', 'Financial leasing Intangible fixed assets', 'Debit', 'Asset', '212', 65),
-- 213x: Intangible fixed assets
('2131', 'Quyền sử dụng đất', 'Right of land use', 'Debit', 'Asset', '213', 67),
('2132', 'Quyền phát hành', 'Publishing rights', 'Debit', 'Asset', '213', 68),
('2133', 'Bản quyền, bằng sáng chế', 'Copyright, patents', 'Debit', 'Asset', '213', 69),
('2134', 'Nhãn hiệu, tên thương mại', 'Product labels and trade marks', 'Debit', 'Asset', '213', 70),
('2135', 'Chương trình phần mềm', 'Computer software', 'Debit', 'Asset', '213', 71),
('2136', 'Giấy phép và giấy phép nhượng quyền', 'License and Concession Agreement', 'Debit', 'Asset', '213', 72),
('2138', 'TSCĐ vô hình khác', 'Other intangible fixed assets', 'Debit', 'Asset', '213', 73),
-- 214x: Accumulated depreciation
('2141', 'Hao mòn TSCĐ hữu hình', 'Accumulated depreciation - tangible fixed assets', 'Credit', 'Asset', '214', 75),
('2142', 'Hao mòn TSCĐ thuê tài chính', 'Accumulated depreciation - financial leasing fixed assets', 'Credit', 'Asset', '214', 76),
('2143', 'Hao mòn TSCĐ vô hình', 'Accumulated depreciation - intangible fixed assets', 'Credit', 'Asset', '214', 77),
('2147', 'Hao mòn bất động sản đầu tư', 'Accumulated depreciation - investment property', 'Credit', 'Asset', '214', 78),
-- 228x: Other investments
('2281', 'Đầu tư góp vốn vào đơn vị khác', 'Equity investments in other entities', 'Debit', 'Asset', '228', 83),
('2288', 'Đầu tư khác', 'Other investments', 'Debit', 'Asset', '228', 84),
-- 229x: Provisions for impairment
('2291', 'Dự phòng giảm giá chứng khoán kinh doanh', 'Provision for diminution in the value of trading securities ', 'Credit', 'Asset', '229', 86),
('2292', 'Dự phòng tổn thất đầu tư vào đơn vị khác', 'Provisions for impairment of investments in other entities', 'Credit', 'Asset', '229', 87),
('2293', 'Dự phòng phải thu khó đòi', 'Provisions for doubtful debts', 'Credit', 'Asset', '229', 88),
('2294', 'Dự phòng giảm giá hàng tồn kho', 'Provisions for inventories', 'Credit', 'Asset', '229', 89),
-- 241x: Construction in process
('2411', 'Mua sắm TSCĐ', 'Fixed assets purchases', 'Debit', 'Asset', '241', 91),
('2412', 'Xây dựng cơ bản', 'Construction in process', 'Debit', 'Asset', '241', 92),
('2413', 'Sửa chữa lớn TSCĐ', 'Major repair of fixed assets', 'Debit', 'Asset', '241', 93),
-- 333x: Taxes payable
('3331', 'Thuế giá trị gia tăng phải nộp', 'Value Added Tax payables', 'Both', 'Liability', '333', 99),
('3332', 'Thuế tiêu thụ đặc biệt', 'Special consumption tax', 'Both', 'Liability', '333', 102),
('3333', 'Thuế xuất, nhập khẩu', 'Import & export duties', 'Both', 'Liability', '333', 103),
('3334', 'Thuế thu nhập doanh nghiệp', 'Company income tax', 'Both', 'Liability', '333', 104),
('3335', 'Thuế thu nhập cá nhân', 'Personal income tax', 'Both', 'Liability', '333', 105),
('3336', 'Thuế tài nguyên', 'Natural resource tax', 'Both', 'Liability', '333', 106),
('3337', 'Thuế nhà đất, tiền thuê đất', 'Land & housing tax, land rental charges', 'Both', 'Liability', '333', 107),
('3338', 'Thuế bảo vệ môi trường và các loại thuế khác', 'Environment protection tax and other taxes', 'Both', 'Liability', '333', 108),
('3339', 'Phí, lệ phí và các khoản phải nộp khác', 'Fee & charge & other payables', 'Both', 'Liability', '333', 111),
-- 334x: Payable to employees
('3341', 'Phải trả công nhân viên', 'Payable to employees', 'Credit', 'Liability', '334', 113),
('3348', 'Phải trả người lao động khác', 'Payable to other employees', 'Credit', 'Liability', '334', 114),
-- 336x: Internal payables
('3361', 'Phải trả nội bộ về vốn kinh doanh', 'Internal payables for operating capital received', 'Credit', 'Liability', '336', 117),
('3362', 'Phải trả nội bộ về chênh lệch tỷ giá', 'Internal payables for foreign exchange differences', 'Credit', 'Liability', '336', 118),
('3363', 'Phải trả nội bộ về chi phí đi vay đủ điều kiện được vốn hóa', 'Internal payables for borrowing expenses eligible to be capitalized', 'Credit', 'Liability', '336', 119),
('3368', 'Phải trả nội bộ khác', 'Other Internal payables', 'Credit', 'Liability', '336', 120),
-- 338x: Other payables
('3381', 'Tài sản thừa chờ giải quyết', 'Surplus assets awaiting for resolution', 'Both', 'Liability', '338', 123),
('3382', 'Kinh phí công đoàn', 'Trade Union fees', 'Both', 'Liability', '338', 124),
('3383', 'Bảo hiểm xã hội', 'Social insurance', 'Both', 'Liability', '338', 125),
('3384', 'Bảo hiểm y tế', 'Health insurance', 'Both', 'Liability', '338', 126),
('3385', 'Phải trả về cổ phần hóa', 'Privatisation payable', 'Both', 'Liability', '338', 127),
('3386', 'Bảo hiểm thất nghiệp', 'Unemployment insurance', 'Both', 'Liability', '338', 128),
('3387', 'Doanh thu chưa thực hiện', 'Unrealised revenue', 'Both', 'Liability', '338', 129),
('3388', 'Phải trả, phải nộp khác', 'Other payable', 'Both', 'Liability', '338', 130),
-- 341x: Borrowings
('3411', 'Các khoản đi vay', 'Borrowing', 'Credit', 'Liability', '341', 132),
('3412', 'Nợ thuê tài chính', 'Financial lease liabilities', 'Credit', 'Liability', '341', 133),
-- 343x: Issued bonds
('3431', 'Trái phiếu thường', 'Ordinary bonds', 'Credit', 'Liability', '343', 135),
('3432', 'Trái phiếu chuyển đổi', 'Convertible bonds', 'Credit', 'Liability', '343', 139),
-- 352x: Provisions for payables
('3521', 'Dự phòng bảo hành sản phẩm hàng hóa', 'Product warranty provisions', 'Credit', 'Liability', '352', 143),
('3522', 'Dự phòng bảo hành công trình xây dựng', 'Construction warranty provisions', 'Credit', 'Liability', '352', 144),
('3523', 'Dự phòng tái cơ cấu doanh nghiệp', 'Enterprise restructuring provisions', 'Credit', 'Liability', '352', 145),
('3524', 'Dự phòng phải trả khác', 'Other provisions', 'Credit', 'Liability', '352', 146),
-- 353x: Bonus & welfare funds
('3531', 'Quỹ khen thưởng', 'Bonus fund', 'Credit', 'Liability', '353', 148),
('3532', 'Quỹ phúc lợi', 'Welfare fund', 'Credit', 'Liability', '353', 149),
('3533', 'Quỹ phúc lợi đã hình thành TSCĐ', 'Welfare fund used to acquire fixed assets', 'Credit', 'Liability', '353', 150),
('3534', 'Quỹ thưởng ban quản lý điều hành công ty', 'Management bonus fund', 'Credit', 'Liability', '353', 151),
-- 356x: Science and technology fund
('3561', 'Quỹ phát triển khoa học và công nghệ', 'Science and technology development fund', 'Credit', 'Liability', '356', 153),
('3562', 'Quỹ phát triển khoa học và công nghệ đã hình thành TSCĐ', 'Science and technology development fund used for fixed asset acquisition', 'Credit', 'Liability', '356', 154),
-- 411x: Contributed capital
('4111', 'Vốn góp của chủ sở hữu', 'Contributed capital ', 'Credit', 'Equity', '411', 157),
('4112', 'Thặng dư vốn cổ phần', 'Share capital surplus', 'Credit', 'Equity', '411', 160),
('4113', 'Quyền chọn chuyển đổi trái phiếu', 'Conversion options on convertible bonds', 'Credit', 'Equity', '411', 161),
('4118', 'Vốn khác', 'Other capital', 'Credit', 'Equity', '411', 162),
-- 413x: Foreign exchange differences
('4131', 'Chênh lệch tỷ giá do đánh giá lại các khoản mục tiền tệ có gốc ngoại tệ', 'Exchange rate differneces upon revaluation of monetary items denominated in foreign currency', 'Both', 'Equity', '413', 165),
('4132', 'Chênh lệch tỷ giá hối đoái trong giai đoạn trước hoạt động', 'Exchange rate differneces in pre-operating period', 'Both', 'Equity', '413', 166),
-- 421x: Undistributed earnings
('4211', 'Lợi nhuận sau thuế chưa phân phối năm trước', 'Previous year undistributed earnings', 'Both', 'Equity', '421', 172),
('4212', 'Lợi nhuận sau thuế chưa phân phối năm nay', 'This year undistributed earnings', 'Both', 'Equity', '421', 173),
-- 461x: Budget resources
('4611', 'Nguồn kinh phí sự nghiệp năm trước', 'Precious year budget resources', 'Credit', 'Equity', '461', 176),
('4612', 'Nguồn kinh phí sự nghiệp năm nay', 'This year budget resources', 'Credit', 'Equity', '461', 177),
-- 511x: Revenue from sales
('5111', 'Doanh thu bán hàng hóa', 'Revenue from sales of goods', 'Both', 'Revenue', '511', 180),
('5112', 'Doanh thu bán các thành phẩm', 'Revenue from sales of finished goods', 'Both', 'Revenue', '511', 181),
('5113', 'Doanh thu cung cấp dịch vụ', 'Revenue from provision of services', 'Both', 'Revenue', '511', 182),
('5114', 'Doanh thu trợ cấp, trợ giá', 'Revenue from sales of Subsidies', 'Both', 'Revenue', '511', 183),
('5117', 'Doanh thu kinh doanh bất động sản đầu tư', 'Revenue from sales of Investment property', 'Both', 'Revenue', '511', 184),
('5118', 'Doanh thu khác', 'Other revenues', 'Both', 'Revenue', '511', 185),
-- 521x: Revenue deductions
('5211', 'Chiết khấu thương mại', 'Sales discount', 'Both', 'Revenue', '521', 188),
('5212', 'Hàng bán bị trả lại', 'Sales returns', 'Both', 'Revenue', '521', 189),
('5213', 'Giảm giá hàng bán', 'Devaluation of sales price', 'Both', 'Revenue', '521', 190),
-- 611x: Cost of purchases
('6111', 'Mua nguyên liệu, vật liệu', 'Raw materials purchase', 'Both', 'Expense', '611', 192),
('6112', 'Mua hàng hóa', 'Cost of purchases', 'Both', 'Expense', '611', 193),
-- 623x: Machinery expense
('6231', 'Chi phí nhân công', 'Labour expense', 'Both', 'Expense', '623', 197),
('6232', 'Chi phí vật liệu', 'Material expense', 'Both', 'Expense', '623', 198),
('6233', 'Chi phí dụng cụ sản xuất', 'Production tool expense', 'Both', 'Expense', '623', 199),
('6234', 'Chi phí khấu hao máy thi công', 'Machinery expense', 'Both', 'Expense', '623', 200),
('6237', 'Chi phí dịch vụ mua ngoài', 'Outsource expense', 'Both', 'Expense', '623', 201),
('6238', 'Chi phí bằng tiền khác', 'Other Cost in cash', 'Both', 'Expense', '623', 202),
-- 627x: General operation expense
('6271', 'Chi phí nhân viên phân xưởng', 'Factory worker expense', 'Both', 'Expense', '627', 204),
('6272', 'Chi phí vật liệu', 'Material expense', 'Both', 'Expense', '627', 205),
('6273', 'Chi phí dụng cụ sản xuất', 'Production tool expense', 'Both', 'Expense', '627', 206),
('6274', 'Chi phí khấu hao TSCĐ', 'Fixed asset depreciation', 'Both', 'Expense', '627', 207),
('6277', 'Chi phí dịch vụ mua ngoài', 'Outsource expense', 'Both', 'Expense', '627', 208),
('6278', 'Chi phí bằng tiền khác', 'Other Cost in cash', 'Both', 'Expense', '627', 209),
-- 641x: Selling expenses
('6411', 'Chi phí nhân viên', 'Labor expense', 'Both', 'Expense', '641', 214),
('6412', 'Chi phí vật liệu, bao bì', 'Material, packing expense', 'Both', 'Expense', '641', 215),
('6413', 'Chi phí dụng cụ, đồ dùng', 'Tools and equipments expense', 'Both', 'Expense', '641', 216),
('6414', 'Chi phí khấu hao TSCĐ', 'Fixed asset depreciation', 'Both', 'Expense', '641', 217),
('6415', 'Chi phí bảo hành', 'Warranty expense', 'Both', 'Expense', '641', 218),
('6417', 'Chi phí dịch vụ mua ngoài', 'Outsource expense', 'Both', 'Expense', '641', 219),
('6418', 'Chi phí bằng tiền khác', 'Other Cost in cash', 'Both', 'Expense', '641', 220),
-- 642x: G&A expenses
('6421', 'Chi phí nhân viên quản lý', 'Employees expense', 'Both', 'Expense', '642', 222),
('6422', 'Chi phí vật liệu quản lý', 'Tools expense', 'Both', 'Expense', '642', 223),
('6423', 'Chi phí đồ dùng văn phòng', 'Stationery expense', 'Both', 'Expense', '642', 224),
('6424', 'Chi phí khấu hao TSCĐ', 'Fixed asset depreciation', 'Both', 'Expense', '642', 225),
('6425', 'Thuế, phí và lệ phí', 'Taxes, fees, charges', 'Both', 'Expense', '642', 226),
('6426', 'Chi phí dự phòng', 'Provision expense', 'Both', 'Expense', '642', 227),
('6427', 'Chi phí dịch vụ mua ngoài', 'Outsource expense', 'Both', 'Expense', '642', 228),
('6428', 'Chi phí bằng tiền khác', 'Other Cost in cash', 'Both', 'Expense', '642', 229),
-- 821x: Business income tax
('8211', 'Chi phí thuế TNDN hiện hành', 'Current business income tax charge', 'Both', 'Expense', '821', 233),
('8212', 'Chi phí thuế TNDN hoãn lại', 'Deferred business income tax charge', 'Both', 'Expense', '821', 234);

-- ============================================
-- BATCH 3: Level 3 accounts (5-digit codes)
-- ============================================
INSERT INTO tt200_chart_of_accounts_template (code, name_vi, name_english, normal_side, type, parent_code, ordering) VALUES
-- 3331x: VAT payables sub-accounts
('33311', 'Thuế GTGT đầu ra', 'VAT output', 'Both', 'Liability', '3331', 100),
('33312', 'Thuế GTGT hàng nhập khẩu', 'VAT for imported goods', 'Both', 'Liability', '3331', 101),
-- 3338x: Environment tax sub-accounts
('33381', 'Thuế bảo vệ môi trường', 'Environment protection tax', 'Both', 'Liability', '3338', 109),
('33382', 'Các loại thuế khác', 'Other taxes', 'Both', 'Liability', '3338', 110),
-- 3431x: Ordinary bonds sub-accounts
('34311', 'Mệnh giá trái phiếu', 'Bond face value', 'Credit', 'Liability', '3431', 136),
('34312', 'Chiết khấu trái phiếu', 'Bond discount', 'Credit', 'Liability', '3431', 137),
('34313', 'Phụ trội trái phiếu', 'Additional bond', 'Credit', 'Liability', '3431', 138),
-- 4111x: Contributed capital sub-accounts
('41111', 'Cổ phiếu phổ thông có quyền biểu quyết', 'Ordinary shares with voting rights', 'Credit', 'Equity', '4111', 158),
('41112', 'Cổ phiếu ưu đãi', 'Preferred shares', 'Credit', 'Equity', '4111', 159);

-- ============================================
-- Validate: Should have exactly 235 accounts
-- ============================================
DO $$
DECLARE
    account_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO account_count FROM tt200_chart_of_accounts_template;
    IF account_count <> 235 THEN
        RAISE EXCEPTION 'Expected 235 accounts in TT200 template, but found %', account_count;
    END IF;
    RAISE NOTICE 'TT200 template seeded successfully with % accounts', account_count;
END $$;
