# Mô tả Chi tiết Cơ sở Dữ liệu - Hệ thống Kế toán

## Tổng quan

Hệ thống sử dụng **PostgreSQL** với kiến trúc **Multi-tenant** (đa công ty). Tất cả các bảng chính đều có trường `company_id` để phân tách dữ liệu giữa các công ty.

**Tổng số bảng trình bày:** 18 bảng  
**Schema:** `accounting`

---

## 1. Bảng `companies` - Công ty

**Mô tả:** Lưu trữ thông tin các công ty/tenant trong hệ thống. Đây là bảng trung tâm, tất cả các bảng khác đều tham chiếu đến.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh công ty | BIGINT | PK | Không |
| 2 | code | Mã công ty | VARCHAR(16) | | Không |
| 3 | name | Tên công ty | VARCHAR(255) | | Không |
| 4 | tax_code | Mã số thuế | VARCHAR(10) | | Không |
| 5 | address | Địa chỉ | VARCHAR(512) | | Không |
| 6 | logo_url | Đường dẫn logo | VARCHAR(512) | | Có |
| 7 | contact_email | Email liên hệ | VARCHAR(255) | | Có |
| 8 | contact_phone | Số điện thoại | VARCHAR(32) | | Có |
| 9 | fiscal_year_start | Ngày bắt đầu năm tài chính | DATE | | Có |
| 10 | created_at | Thời gian tạo | TIMESTAMP | | Không |
| 11 | updated_at | Thời gian cập nhật | TIMESTAMP | | Không |

---

## 2. Bảng `users` - Người dùng

**Mô tả:** Lưu trữ thông tin tài khoản người dùng, bao gồm thông tin xác thực và phân quyền.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh người dùng | BIGINT | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Có |
| 3 | email | Địa chỉ email | VARCHAR(255) | | Không |
| 4 | password_hash | Mật khẩu đã mã hoá (BCrypt) | VARCHAR(255) | | Không |
| 5 | full_name | Họ và tên | VARCHAR(255) | | Không |
| 6 | role | Vai trò (ADMIN, ACCOUNTANT, VIEWER) | VARCHAR(50) | | Không |
| 7 | status | Trạng thái (ACTIVE, INACTIVE, LOCKED) | VARCHAR(20) | | Không |
| 8 | failed_login_count | Số lần đăng nhập thất bại | INTEGER | | Không |
| 9 | locked_until | Thời gian khoá tài khoản | TIMESTAMP | | Có |
| 10 | reset_token | Token đặt lại mật khẩu | VARCHAR(255) | | Có |
| 11 | reset_token_expiry | Thời hạn token | TIMESTAMP | | Có |
| 12 | created_at | Thời gian tạo | TIMESTAMP | | Không |
| 13 | updated_at | Thời gian cập nhật | TIMESTAMP | | Không |

---

## 3. Bảng `customers` - Khách hàng

**Mô tả:** Lưu trữ thông tin khách hàng (đối tượng công nợ phải thu).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | BIGINT | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | code | Mã khách hàng | VARCHAR(32) | | Không |
| 4 | name | Tên khách hàng | VARCHAR(255) | | Không |
| 5 | tax_code | Mã số thuế | VARCHAR(20) | | Có |
| 6 | address | Địa chỉ | VARCHAR(512) | | Có |
| 7 | email | Email | VARCHAR(255) | | Có |
| 8 | phone | Số điện thoại | VARCHAR(20) | | Có |
| 9 | active | Trạng thái hoạt động | BOOLEAN | | Không |
| 10 | created_at | Thời gian tạo | TIMESTAMP | | Không |
| 11 | updated_at | Thời gian cập nhật | TIMESTAMP | | Không |

---

## 4. Bảng `suppliers` - Nhà cung cấp

**Mô tả:** Lưu trữ thông tin nhà cung cấp (đối tượng công nợ phải trả).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | BIGINT | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | code | Mã nhà cung cấp | VARCHAR(32) | | Không |
| 4 | name | Tên nhà cung cấp | VARCHAR(255) | | Không |
| 5 | tax_code | Mã số thuế | VARCHAR(20) | | Có |
| 6 | address | Địa chỉ | VARCHAR(512) | | Có |
| 7 | email | Email | VARCHAR(255) | | Có |
| 8 | phone | Số điện thoại | VARCHAR(20) | | Có |
| 9 | active | Trạng thái hoạt động | BOOLEAN | | Không |
| 10 | created_at | Thời gian tạo | TIMESTAMP | | Không |
| 11 | updated_at | Thời gian cập nhật | TIMESTAMP | | Không |

---

## 5. Bảng `chart_of_accounts` - Hệ thống tài khoản

**Mô tả:** Lưu trữ danh mục hệ thống tài khoản kế toán theo Thông tư 200/2014/TT-BTC. Hỗ trợ cấu trúc cây (tài khoản cha-con).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | BIGINT | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | parent_id | Mã tài khoản cha | BIGINT | FK → chart_of_accounts | Có |
| 4 | code | Số hiệu tài khoản | VARCHAR(20) | | Không |
| 5 | name | Tên tài khoản | VARCHAR(255) | | Không |
| 6 | name_english | Tên tiếng Anh | VARCHAR(255) | | Có |
| 7 | type | Loại tài khoản (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE) | VARCHAR(50) | | Không |
| 8 | normal_side | Bên số dư (DEBIT, CREDIT) | VARCHAR(50) | | Không |
| 9 | postable | Cho phép ghi sổ | BOOLEAN | | Không |
| 10 | active | Trạng thái hoạt động | BOOLEAN | | Không |
| 11 | ordering_position | Thứ tự sắp xếp | INTEGER | | Không |
| 12 | description | Mô tả | TEXT | | Có |
| 13 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 14 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 6. Bảng `bank_accounts` - Tài khoản ngân hàng

**Mô tả:** Lưu trữ thông tin tài khoản ngân hàng và quỹ tiền mặt của công ty.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | BIGINT | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | account_number | Số tài khoản | VARCHAR(50) | | Không |
| 4 | bank_name | Tên ngân hàng | VARCHAR(255) | | Không |
| 5 | branch | Chi nhánh | VARCHAR(255) | | Có |
| 6 | type | Loại (BANK, CASH) | VARCHAR(10) | | Không |
| 7 | gl_account_code | Mã tài khoản kế toán | VARCHAR(20) | | Có |
| 8 | opening_balance | Số dư đầu kỳ | NUMERIC(19,4) | | Không |
| 9 | opening_balance_locked | Khoá số dư đầu kỳ | BOOLEAN | | Không |
| 10 | last_reconciled_date | Ngày đối chiếu cuối | DATE | | Có |
| 11 | last_reconciled_balance | Số dư đối chiếu cuối | NUMERIC(19,4) | | Có |
| 12 | active | Trạng thái hoạt động | BOOLEAN | | Không |
| 13 | created_at | Thời gian tạo | TIMESTAMP | | Không |
| 14 | updated_at | Thời gian cập nhật | TIMESTAMP | | Không |

---

## 7. Bảng `accounting_periods` - Kỳ kế toán

**Mô tả:** Quản lý các kỳ kế toán (tháng/quý/năm), kiểm soát việc đóng/mở sổ.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | fiscal_year | Năm tài chính | INTEGER | | Không |
| 4 | period_number | Số kỳ (1-12) | INTEGER | | Không |
| 5 | period_name | Tên kỳ (VD: "Tháng 01/2025") | VARCHAR(100) | | Không |
| 6 | start_date | Ngày bắt đầu | DATE | | Không |
| 7 | end_date | Ngày kết thúc | DATE | | Không |
| 8 | status | Trạng thái (OPEN, CLOSED, LOCKED) | VARCHAR(20) | | Không |
| 9 | closed_by | Người đóng kỳ | BIGINT | | Có |
| 10 | closed_at | Thời gian đóng kỳ | TIMESTAMPTZ | | Có |
| 11 | close_reason | Lý do đóng kỳ | TEXT | | Có |
| 12 | version | Phiên bản (Optimistic Locking) | BIGINT | | Không |
| 13 | created_at | Thời gian tạo | TIMESTAMPTZ | | Không |
| 14 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Không |

---

## 8. Bảng `vouchers` - Chứng từ kế toán

**Mô tả:** Lưu trữ các chứng từ kế toán (phiếu thu, phiếu chi, phiếu kế toán). Đây là bảng trung tâm của nghiệp vụ kế toán.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | period_id | Mã kỳ kế toán | UUID | FK → accounting_periods | Có |
| 4 | voucher_number | Số chứng từ | VARCHAR(50) | | Không |
| 5 | voucher_date | Ngày chứng từ | DATE | | Không |
| 6 | description | Diễn giải | VARCHAR(500) | | Không |
| 7 | currency | Loại tiền | VARCHAR(3) | | Không |
| 8 | status | Trạng thái (DRAFT, POSTED, REVERSED) | VARCHAR(20) | | Không |
| 9 | total_debit | Tổng nợ | NUMERIC(19,4) | | Không |
| 10 | total_credit | Tổng có | NUMERIC(19,4) | | Không |
| 11 | entered_by | Người lập | BIGINT | FK → users | Không |
| 12 | posted_by | Người ghi sổ | BIGINT | FK → users | Có |
| 13 | posted_at | Thời gian ghi sổ | TIMESTAMPTZ | | Có |
| 14 | reversal_of | Chứng từ gốc (nếu là CT đảo) | UUID | FK → vouchers | Có |
| 15 | reversed_by | Người đảo | BIGINT | FK → users | Có |
| 16 | reversed_by_voucher_id | CT đảo của CT này | UUID | FK → vouchers | Có |
| 17 | is_locked | Đã khoá | BOOLEAN | | Không |
| 18 | version | Phiên bản (Optimistic Locking) | BIGINT | | Không |
| 19 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 20 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 9. Bảng `voucher_lines` - Dòng chứng từ

**Mô tả:** Chi tiết các dòng định khoản Nợ/Có của chứng từ.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | voucher_id | Mã chứng từ | UUID | FK → vouchers | Không |
| 3 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 4 | line_number | Số thứ tự dòng | INTEGER | | Không |
| 5 | account_id | Mã tài khoản | BIGINT | FK → chart_of_accounts | Không |
| 6 | debit | Số tiền Nợ | NUMERIC(19,4) | | Không |
| 7 | credit | Số tiền Có | NUMERIC(19,4) | | Không |
| 8 | description | Diễn giải dòng | VARCHAR(500) | | Có |
| 9 | customer_id | Mã khách hàng | BIGINT | FK → customers | Có |
| 10 | vendor_id | Mã nhà cung cấp | BIGINT | | Có |
| 11 | bank_account_id | Mã tài khoản ngân hàng | BIGINT | FK → bank_accounts | Có |
| 12 | cost_center_id | Mã trung tâm chi phí | BIGINT | | Có |
| 13 | item_id | Mã hàng hoá | BIGINT | | Có |
| 14 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 15 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 10. Bảng `journal_entries` - Bút toán nhật ký

**Mô tả:** Sổ nhật ký chung, được sinh tự động khi chứng từ được ghi sổ (posted).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | voucher_id | Mã chứng từ | UUID | FK → vouchers | Không |
| 3 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 4 | period_id | Mã kỳ kế toán | UUID | FK → accounting_periods | Có |
| 5 | account_id | Mã tài khoản | BIGINT | FK → chart_of_accounts | Không |
| 6 | debit_amount | Số tiền Nợ | NUMERIC(19,4) | | Không |
| 7 | credit_amount | Số tiền Có | NUMERIC(19,4) | | Không |
| 8 | customer_id | Mã khách hàng | BIGINT | FK → customers | Có |
| 9 | supplier_id | Mã nhà cung cấp | BIGINT | FK → suppliers | Có |
| 10 | cost_center_id | Mã trung tâm chi phí | BIGINT | | Có |
| 11 | posted_at | Thời gian ghi sổ | TIMESTAMPTZ | | Không |
| 12 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 13 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 11. Bảng `purchase_bills` - Hoá đơn mua hàng

**Mô tả:** Lưu trữ hoá đơn mua hàng từ nhà cung cấp (Accounts Payable).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | supplier_id | Mã nhà cung cấp | BIGINT | FK → suppliers | Không |
| 4 | bill_number | Số hoá đơn | VARCHAR(50) | | Không |
| 5 | bill_date | Ngày hoá đơn | DATE | | Không |
| 6 | due_date | Ngày đến hạn | DATE | | Không |
| 7 | reference | Tham chiếu | VARCHAR(100) | | Không |
| 8 | description | Mô tả | VARCHAR(500) | | Có |
| 9 | status | Trạng thái (DRAFT, PENDING_APPROVAL, APPROVED, POSTED, PAID) | VARCHAR(20) | | Không |
| 10 | total_amount | Tổng tiền (bao gồm VAT) | NUMERIC(19,4) | | Không |
| 11 | vat_amount | Tiền thuế GTGT | NUMERIC(19,4) | | Không |
| 12 | is_sensitive | Hoá đơn nhạy cảm | BOOLEAN | | Không |
| 13 | created_by_id | Người tạo | BIGINT | FK → users | Không |
| 14 | approved_by_id | Người phê duyệt | BIGINT | FK → users | Có |
| 15 | posted_voucher_id | Chứng từ ghi sổ | UUID | FK → vouchers | Có |
| 16 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 17 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 12. Bảng `purchase_bill_lines` - Chi tiết hoá đơn mua

**Mô tả:** Chi tiết từng dòng hàng hoá/dịch vụ trong hoá đơn mua.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | purchase_bill_id | Mã hoá đơn | UUID | FK → purchase_bills | Không |
| 3 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 4 | line_number | Số thứ tự dòng | INTEGER | | Không |
| 5 | account_id | Mã tài khoản | BIGINT | FK → chart_of_accounts | Không |
| 6 | description | Mô tả hàng hoá/dịch vụ | VARCHAR(500) | | Không |
| 7 | quantity | Số lượng | NUMERIC(19,4) | | Không |
| 8 | unit_price | Đơn giá | NUMERIC(19,4) | | Không |
| 9 | amount | Thành tiền | NUMERIC(19,4) | | Không |
| 10 | vat_rate | Thuế suất (VAT_0, VAT_5, VAT_8, VAT_10) | VARCHAR(10) | | Không |
| 11 | vat_amount | Tiền thuế | NUMERIC(19,4) | | Không |
| 12 | cost_center_id | Mã trung tâm chi phí | BIGINT | | Có |
| 13 | item_id | Mã hàng hoá | BIGINT | | Có |
| 14 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 15 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 13. Bảng `ap_payments` - Thanh toán cho NCC

**Mô tả:** Lưu trữ các khoản thanh toán cho nhà cung cấp (chi tiền).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | supplier_id | Mã nhà cung cấp | BIGINT | FK → suppliers | Không |
| 4 | payment_number | Số phiếu chi | VARCHAR(50) | | Không |
| 5 | payment_date | Ngày thanh toán | DATE | | Không |
| 6 | due_date | Ngày đến hạn | DATE | | Có |
| 7 | payee | Người nhận | VARCHAR(255) | | Không |
| 8 | amount | Số tiền | NUMERIC(19,4) | | Không |
| 9 | payment_method | Phương thức (CASH, BANK_TRANSFER, CHECK) | VARCHAR(20) | | Không |
| 10 | cash_account_id | TK tiền mặt | BIGINT | FK → bank_accounts | Có |
| 11 | bank_account_id | TK ngân hàng | BIGINT | FK → bank_accounts | Có |
| 12 | reference | Tham chiếu | VARCHAR(500) | | Có |
| 13 | payment_proof_url | URL chứng từ thanh toán | VARCHAR(1000) | | Có |
| 14 | is_standalone | Thanh toán độc lập | BOOLEAN | | Không |
| 15 | status | Trạng thái (DRAFT, PENDING, APPROVED, POSTED) | VARCHAR(20) | | Không |
| 16 | created_by_id | Người tạo | BIGINT | FK → users | Không |
| 17 | approved_by_id | Người phê duyệt | BIGINT | FK → users | Có |
| 18 | linked_voucher_id | Chứng từ liên kết | UUID | FK → vouchers | Có |
| 19 | posted_at | Thời gian ghi sổ | TIMESTAMPTZ | | Có |
| 20 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 21 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 14. Bảng `sales_invoices` - Hoá đơn bán hàng

**Mô tả:** Lưu trữ hoá đơn bán hàng cho khách hàng (Accounts Receivable).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | customer_id | Mã khách hàng | BIGINT | FK → customers | Không |
| 4 | invoice_number | Số hoá đơn | VARCHAR(50) | | Không |
| 5 | invoice_date | Ngày hoá đơn | DATE | | Không |
| 6 | due_date | Ngày đến hạn | DATE | | Không |
| 7 | reference | Tham chiếu | VARCHAR(100) | | Không |
| 8 | description | Mô tả | VARCHAR(500) | | Có |
| 9 | status | Trạng thái (DRAFT, PENDING_APPROVAL, APPROVED, POSTED, PAID, PARTIALLY_PAID) | VARCHAR(20) | | Không |
| 10 | total_amount | Tổng tiền | NUMERIC(19,4) | | Không |
| 11 | vat_amount | Tiền thuế GTGT | NUMERIC(19,4) | | Không |
| 12 | amount_paid | Đã thanh toán | NUMERIC(19,4) | | Không |
| 13 | remaining_balance | Còn nợ | NUMERIC(19,4) | | Không |
| 14 | is_sensitive | Hoá đơn nhạy cảm | BOOLEAN | | Không |
| 15 | is_deleted | Đã xoá (soft delete) | BOOLEAN | | Không |
| 16 | deleted_at | Thời gian xoá | TIMESTAMPTZ | | Có |
| 17 | original_invoice_id | HĐ gốc (nếu là HĐ điều chỉnh) | UUID | FK → sales_invoices | Có |
| 18 | posted_voucher_id | Chứng từ ghi sổ | UUID | | Có |
| 19 | created_by_id | Người tạo | BIGINT | FK → users | Không |
| 20 | approved_by_id | Người phê duyệt | BIGINT | FK → users | Có |
| 21 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 22 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 15. Bảng `sales_invoice_lines` - Chi tiết hoá đơn bán

**Mô tả:** Chi tiết từng dòng hàng hoá/dịch vụ trong hoá đơn bán.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | sales_invoice_id | Mã hoá đơn | UUID | FK → sales_invoices | Không |
| 3 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 4 | line_number | Số thứ tự dòng | INTEGER | | Không |
| 5 | account_id | Mã tài khoản | BIGINT | FK → chart_of_accounts | Không |
| 6 | description | Mô tả hàng hoá/dịch vụ | VARCHAR(500) | | Không |
| 7 | quantity | Số lượng | NUMERIC(19,4) | | Không |
| 8 | unit_price | Đơn giá | NUMERIC(19,4) | | Không |
| 9 | amount | Thành tiền | NUMERIC(19,4) | | Không |
| 10 | vat_rate | Thuế suất | VARCHAR(10) | | Không |
| 11 | vat_amount | Tiền thuế | NUMERIC(19,4) | | Không |
| 12 | cost_center_id | Mã trung tâm chi phí | BIGINT | | Có |
| 13 | item_id | Mã hàng hoá | BIGINT | | Có |
| 14 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 15 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 16. Bảng `ar_payments` - Thu tiền từ khách hàng

**Mô tả:** Lưu trữ các khoản thu tiền từ khách hàng (phiếu thu).

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | customer_id | Mã khách hàng | BIGINT | FK → customers | Không |
| 4 | receipt_number | Số phiếu thu | VARCHAR(50) | | Không |
| 5 | receipt_date | Ngày thu | DATE | | Không |
| 6 | payee | Người nộp tiền | VARCHAR(255) | | Không |
| 7 | amount | Số tiền | NUMERIC(19,4) | | Không |
| 8 | payment_method | Phương thức | VARCHAR(20) | | Không |
| 9 | cash_account_id | TK tiền mặt | BIGINT | FK → bank_accounts | Có |
| 10 | bank_account_id | TK ngân hàng | BIGINT | FK → bank_accounts | Có |
| 11 | reference | Tham chiếu | VARCHAR(500) | | Có |
| 12 | receipt_proof_url | URL chứng từ | VARCHAR(1000) | | Có |
| 13 | is_standalone | Thu tiền độc lập | BOOLEAN | | Không |
| 14 | status | Trạng thái (DRAFT, POSTED, REVERSED) | VARCHAR(20) | | Không |
| 15 | created_by_id | Người tạo | BIGINT | FK → users | Không |
| 16 | posted_by_id | Người ghi sổ | BIGINT | FK → users | Có |
| 17 | linked_voucher_id | Chứng từ liên kết | UUID | FK → vouchers | Có |
| 18 | reversal_reason | Lý do huỷ | VARCHAR(500) | | Có |
| 19 | original_receipt_id | Phiếu thu gốc (nếu huỷ) | UUID | FK → ar_payments | Có |
| 20 | reversing_receipt_id | Phiếu huỷ | UUID | FK → ar_payments | Có |
| 21 | posted_at | Thời gian ghi sổ | TIMESTAMPTZ | | Có |
| 22 | created_at | Thời gian tạo | TIMESTAMPTZ | | Có |
| 23 | updated_at | Thời gian cập nhật | TIMESTAMPTZ | | Có |

---

## 17. Bảng `approval_workflows` - Luồng phê duyệt

**Mô tả:** Quản lý luồng phê duyệt cho hoá đơn mua/bán khi vượt ngưỡng.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | UUID | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | FK → companies | Không |
| 3 | purchase_bill_id | Mã hoá đơn mua | UUID | FK → purchase_bills | Có |
| 4 | sales_invoice_id | Mã hoá đơn bán | UUID | FK → sales_invoices | Có |
| 5 | status | Trạng thái (PENDING, APPROVED, REJECTED) | VARCHAR(20) | | Không |
| 6 | threshold_amount | Ngưỡng phê duyệt | NUMERIC(19,4) | | Không |
| 7 | bill_amount | Giá trị hoá đơn | NUMERIC(19,4) | | Không |
| 8 | is_sensitive | Hoá đơn nhạy cảm | BOOLEAN | | Không |
| 9 | approval_reason | Lý do phê duyệt | VARCHAR(1000) | | Có |
| 10 | rejection_reason | Lý do từ chối | VARCHAR(1000) | | Có |
| 11 | created_by_id | Người tạo yêu cầu | BIGINT | FK → users | Không |
| 12 | approved_by_id | Người phê duyệt | BIGINT | FK → users | Có |
| 13 | approved_at | Thời gian phê duyệt | TIMESTAMP | | Có |
| 14 | rejected_at | Thời gian từ chối | TIMESTAMP | | Có |
| 15 | created_at | Thời gian tạo | TIMESTAMP | | Không |
| 16 | updated_at | Thời gian cập nhật | TIMESTAMP | | Không |

---

## 18. Bảng `audit_logs` - Nhật ký thay đổi

**Mô tả:** Ghi lại tất cả các thao tác của người dùng để phục vụ kiểm toán và truy vết.

| STT | Trường | Mô tả | Kiểu dữ liệu | Khoá | Cho phép NULL |
|-----|--------|-------|--------------|------|---------------|
| 1 | id | Mã định danh | BIGINT | PK | Không |
| 2 | company_id | Mã công ty | BIGINT | | Có |
| 3 | user_id | Mã người dùng | BIGINT | | Có |
| 4 | email | Email người dùng | VARCHAR(255) | | Có |
| 5 | action | Hành động (CREATE, UPDATE, DELETE, LOGIN, LOGOUT) | VARCHAR(50) | | Không |
| 6 | entity_type | Loại đối tượng | VARCHAR(100) | | Có |
| 7 | entity_id | Mã đối tượng | VARCHAR(64) | | Có |
| 8 | entity_display | Tên hiển thị đối tượng | VARCHAR(255) | | Có |
| 9 | event_type | Loại sự kiện | VARCHAR(50) | | Có |
| 10 | actor_role | Vai trò người thực hiện | VARCHAR(50) | | Có |
| 11 | success | Thành công | BOOLEAN | | Có |
| 12 | failure_reason | Lý do thất bại | VARCHAR(255) | | Có |
| 13 | reason | Lý do thực hiện | VARCHAR(50) | | Có |
| 14 | changes | Chi tiết thay đổi | JSONB | | Có |
| 15 | metadata | Metadata bổ sung | JSONB | | Có |
| 16 | ip_address | Địa chỉ IP | VARCHAR(45) | | Có |
| 17 | user_agent | Trình duyệt | VARCHAR(512) | | Có |
| 18 | trace_id | Mã trace | VARCHAR(64) | | Có |
| 19 | chain_hash | Hash chuỗi (chống giả mạo) | VARCHAR(64) | | Có |
| 20 | retention_until | Thời hạn lưu trữ | TIMESTAMPTZ | | Có |
| 21 | created_at | Thời gian tạo | TIMESTAMP | | Không |

---

## Sơ đồ quan hệ (ERD)

```
┌─────────────┐       ┌─────────────┐       ┌─────────────────────┐
│  companies  │───────│    users    │       │  chart_of_accounts  │
└─────────────┘       └─────────────┘       └─────────────────────┘
       │                                              │
       ├──────────────────────────────────────────────┤
       │                     │                        │
       ▼                     ▼                        ▼
┌─────────────┐       ┌─────────────┐       ┌─────────────────┐
│  customers  │       │  suppliers  │       │  bank_accounts  │
└─────────────┘       └─────────────┘       └─────────────────┘
       │                     │                        │
       │                     │                        │
       ▼                     ▼                        ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│ sales_invoices  │   │ purchase_bills  │   │    vouchers     │
└─────────────────┘   └─────────────────┘   └─────────────────┘
       │                     │                        │
       ▼                     ▼                        ▼
┌───────────────────┐ ┌───────────────────┐ ┌─────────────────┐
│sales_invoice_lines│ │purchase_bill_lines│ │  voucher_lines  │
└───────────────────┘ └───────────────────┘ └─────────────────┘
       │                     │                        │
       ▼                     ▼                        ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│   ar_payments   │   │   ap_payments   │   │ journal_entries │
└─────────────────┘   └─────────────────┘   └─────────────────┘
                             │
                             ▼
                    ┌─────────────────────┐
                    │ approval_workflows  │
                    └─────────────────────┘
```

---

## Ghi chú kỹ thuật

### 1. Multi-tenancy
Tất cả các bảng chính đều có trường `company_id` để phân tách dữ liệu giữa các công ty. Hệ thống sử dụng Row-Level Security thông qua `CompanyScopeAspect`.

### 2. Audit Trail
Bảng `audit_logs` ghi lại mọi thay đổi với:
- Field `changes` (JSONB): Lưu giá trị cũ/mới
- Field `chain_hash`: Đảm bảo tính toàn vẹn dữ liệu

### 3. Optimistic Locking
Các bảng `vouchers`, `accounting_periods` sử dụng trường `version` để xử lý đồng thời.

### 4. Soft Delete
Bảng `sales_invoices` hỗ trợ soft delete với các trường `is_deleted`, `deleted_at`.

### 5. Self-referencing
- `chart_of_accounts.parent_id` → Cấu trúc cây tài khoản
- `vouchers.reversal_of` → Chứng từ đảo
- `ar_payments.original_receipt_id` → Phiếu thu gốc
