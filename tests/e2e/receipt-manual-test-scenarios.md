# Receipt Module - Manual Test Scenarios

## Prerequisites

1. **Start Backend**: `cd backend && mvnd spring-boot:run`
2. **Start Frontend**: `cd frontend && pnpm dev`
3. **Login**: Đăng nhập với user có quyền Accountant
4. **Test Data**: Đảm bảo có:
   - Ít nhất 1 Customer với open invoices
   - Ít nhất 1 Bank Account active với GL Account Code
   - Accounting period đang mở

---

## Scenario 1: Tạo Receipt Mới (AC6.2-01)

### Mục tiêu

Kiểm tra form tạo receipt có đầy đủ fields và validation

### Steps

| Step | Action                              | Expected Result                                                                                                    |
| ---- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| 1    | Navigate to `/accounting/receipts`  | Hiển thị danh sách receipts                                                                                        |
| 2    | Click **"New Receipt"** button      | Chuyển đến form tạo receipt                                                                                        |
| 3    | Verify form fields                  | Có các fields: Date, Receipt Number (auto), Customer, Reference, Amount, Bank Account, Payment Method, Attachments |
| 4    | Receipt Number format               | Format: `CR-YYYY-XXX` (e.g., CR-2025-001)                                                                          |
| 5    | Submit form với Amount = 0          | ❌ Error: "Amount must be greater than 0"                                                                          |
| 6    | Submit form thiếu Customer          | ❌ Error: "Customer is required"                                                                                   |
| 7    | Submit form thiếu Bank Account      | ❌ Error: "Bank account is required"                                                                               |
| 8    | Fill đầy đủ fields hợp lệ và Submit | ✅ Receipt created với status DRAFT                                                                                |

---

## Scenario 2: Phân Bổ Receipt vào Invoices (AC6.2-02)

### Mục tiêu

Kiểm tra chức năng allocation receipt vào các invoices

### Steps

| Step | Action                                  | Expected Result                                                 |
| ---- | --------------------------------------- | --------------------------------------------------------------- |
| 1    | Tạo Receipt với Amount = 10,000,000 VND | Receipt tạo thành công                                          |
| 2    | Chọn Customer có 2 open invoices        | Hiển thị allocation grid với 2 invoices                         |
| 3    | Verify invoice info                     | Hiển thị: Invoice Number, Date, Total Amount, Remaining Balance |
| 4    | Nhập allocation > remaining balance     | ❌ Error: "Cannot allocate more than remaining balance"         |
| 5    | Nhập total allocations > receipt amount | ❌ Error: "Total allocations exceed receipt amount"             |
| 6    | Partial allocation: 5M vào invoice 1    | ✅ Remaining balance cập nhật                                   |
| 7    | Partial allocation: 5M vào invoice 2    | ✅ Total = Receipt Amount                                       |

---

## Scenario 3: Post Receipt với GL Validation (AC6.2-03, AC6.2-04)

### Mục tiêu

Kiểm tra posting logic và validation rules

### Steps

| Step | Action                                     | Expected Result                                                      |
| ---- | ------------------------------------------ | -------------------------------------------------------------------- |
| 1    | Tạo receipt với Bank Account inactive      | ❌ Error: "Cannot create receipt: account is inactive"               |
| 2    | Tạo receipt với Bank Account thiếu GL Code | ❌ Error: "Cannot post: account has no GL account code"              |
| 3    | Tạo receipt hợp lệ, click **Post**         | Hiển thị confirmation dialog                                         |
| 4    | Confirm Post                               | ✅ Status → POSTED                                                   |
| 5    | Verify GL Voucher created                  | Click "View Voucher" link → Voucher có entries: Dr 1111/1121, Cr 131 |
| 6    | Verify invoice status                      | Invoice đã được paid: PARTIALLY_PAID hoặc PAID                       |

---

## Scenario 4: Standalone Receipt (Other Income) (AC6.2-04)

### Mục tiêu

Kiểm tra standalone receipt credit account 711 (Other Income)

### Steps

| Step | Action                              | Expected Result                                   |
| ---- | ----------------------------------- | ------------------------------------------------- |
| 1    | Click "New Receipt"                 | Form tạo receipt                                  |
| 2    | Check "Standalone Receipt" checkbox | Allocation grid ẩn đi                             |
| 3    | Fill amount = 5,000,000 VND         | Amount accepted                                   |
| 4    | Post receipt                        | ✅ Voucher created: Dr 1111, **Cr 711** (not 131) |

---

## Scenario 5: Reversal Workflow (AC6.2-05)

### Mục tiêu

Kiểm tra chức năng reverse receipt

### Steps

| Step | Action                                 | Expected Result                         |
| ---- | -------------------------------------- | --------------------------------------- |
| 1    | Mở receipt có status POSTED            | Hiển thị button "Reverse"               |
| 2    | Click **Reverse**                      | Hiển thị dialog yêu cầu reversal reason |
| 3    | Submit với reason rỗng                 | ❌ Error: "Reversal reason is required" |
| 4    | Nhập reason: "Customer refund request" | ✅ Submit enabled                       |
| 5    | Confirm Reverse                        | Status → REVERSED                       |
| 6    | Verify reversal voucher                | Link đến reversal voucher created       |
| 7    | Verify invoice balance restored        | Remaining balance = original amount     |

---

## Scenario 6: Batch Import (AC6.2-06)

### Mục tiêu

Kiểm tra import receipts từ CSV/Excel

### Steps

| Step | Action                                   | Expected Result                                |
| ---- | ---------------------------------------- | ---------------------------------------------- |
| 1    | Click **Import** button                  | Hiển thị import dialog                         |
| 2    | Click **Download Template**              | Tải file template CSV/Excel                    |
| 3    | Fill template với 3 receipts (1 invalid) | File có 3 rows                                 |
| 4    | Upload file                              | Progress bar hiển thị                          |
| 5    | Verify error handling                    | ❌ Row 2 error: "Invalid customer code"        |
| 6    | Atomic rollback                          | Không có receipt nào được tạo (all-or-nothing) |
| 7    | Fix errors và re-upload                  | ✅ 3 receipts created                          |

---

## Scenario 7: Performance Telemetry (AC6.2-07)

### Mục tiêu

Kiểm tra performance ≤ 10 seconds

### Steps

| Step | Action                              | Expected Result     |
| ---- | ----------------------------------- | ------------------- |
| 1    | Open browser DevTools → Network tab |                     |
| 2    | Create simple receipt               | Create API < 2s     |
| 3    | Post receipt                        | Post API < 5s       |
| 4    | Total workflow                      | Create + Post < 10s |

---

## Scenario 8: Multi-File Attachments (AC6.2-08)

### Mục tiêu

Kiểm tra attachment upload với limits

### Steps

| Step | Action                   | Expected Result                               |
| ---- | ------------------------ | --------------------------------------------- |
| 1    | Mở form Receipt          | Hiển thị Attachments section                  |
| 2    | Drag & drop 1 image file | ✅ File uploaded, preview hiển thị            |
| 3    | Upload PDF file          | ✅ PDF icon hiển thị                          |
| 4    | Upload file > 10MB       | ❌ Error: "File exceeds maximum size of 10MB" |
| 5    | Upload 10 files          | ✅ All uploaded                               |
| 6    | Upload file thứ 11       | ❌ Error: "Maximum 10 files allowed"          |
| 7    | Delete 1 attachment      | ✅ File removed, count = 9                    |
| 8    | Upload .exe file         | ❌ Error: "Invalid file type"                 |
| 9    | Total size > 20MB        | ❌ Error: "Total size exceeds 20MB limit"     |

---

## Scenario 9: Threshold-Based Maker-Checker (AC6.2-09)

### Mục tiêu

Kiểm tra approval workflow cho high-value receipts

### Prerequisites

- Threshold setting: 100,000,000 VND
- 2 users: Accountant (creator) + Chief Accountant (approver)

### Steps

| Step | Action                                         | Expected Result                                |
| ---- | ---------------------------------------------- | ---------------------------------------------- |
| 1    | Login as Accountant                            |                                                |
| 2    | Create receipt với amount = 50M (< threshold)  | Status = DRAFT                                 |
| 3    | Post receipt                                   | ✅ Posted immediately (no approval needed)     |
| 4    | Create receipt với amount = 150M (> threshold) | Status = **PENDING_APPROVAL**                  |
| 5    | Try to Post                                    | ❌ Error: "Requires Chief Accountant approval" |
| 6    | Logout → Login as Chief Accountant             |                                                |
| 7    | Open receipt PENDING_APPROVAL                  | Hiển thị button "Approve & Post"               |
| 8    | Click Approve & Post                           | ✅ Status → POSTED                             |

### Maker-Checker Validation

| Step | Action                                 | Expected Result                                     |
| ---- | -------------------------------------- | --------------------------------------------------- |
| 1    | Accountant creates receipt > threshold | Status = PENDING_APPROVAL                           |
| 2    | Same Accountant tries to approve       | ❌ Error: "Approver must be different from creator" |
| 3    | Different Chief Accountant approves    | ✅ Success                                          |

---

## Scenario 10: Audit Trail (AC6.2-10)

### Mục tiêu

Kiểm tra audit log cho tất cả actions

### Steps

| Step | Action                     | Expected Result                                    |
| ---- | -------------------------- | -------------------------------------------------- |
| 1    | Create receipt             | Audit log: "RECEIPT_CREATED"                       |
| 2    | Update receipt             | Audit log: "RECEIPT_UPDATED" với payload diff      |
| 3    | Post receipt               | Audit log: "RECEIPT_POSTED"                        |
| 4    | Reverse receipt            | Audit log: "RECEIPT_REVERSED" với reason           |
| 5    | Import receipts            | Audit log: "RECEIPTS_IMPORTED" với count           |
| 6    | Navigate to Audit Log page | Filter by entity type = "RECEIPT"                  |
| 7    | Verify log entries         | Có đầy đủ: timestamp, user, action, old/new values |

---

## Quick Smoke Test Checklist

Chạy nhanh các test cases quan trọng nhất:

- [ ] Login as Accountant
- [ ] Navigate to Receipts list
- [ ] Create new receipt với customer allocation
- [ ] Verify allocation prevents over-collection
- [ ] Post receipt
- [ ] Verify voucher created with correct GL accounts
- [ ] Reverse receipt with reason
- [ ] Upload attachment (image + PDF)
- [ ] Download attachment
- [ ] Delete attachment
- [ ] Search/filter receipts list
- [ ] Export receipts list

---

## Test Data Requirements

```sql
-- Ensure test customer with open invoices
SELECT c.name, i.invoice_number, i.remaining_balance
FROM customers c
JOIN sales_invoices i ON c.id = i.customer_id
WHERE i.status = 'POSTED' AND i.remaining_balance > 0;

-- Ensure active bank account with GL code
SELECT ba.name, ba.gl_account_code, ba.active
FROM bank_accounts ba
WHERE ba.active = true AND ba.gl_account_code IS NOT NULL;

-- Check accounting period is open
SELECT * FROM accounting_periods WHERE is_closed = false ORDER BY end_date DESC LIMIT 1;
```

---

## Running Automated E2E Tests

```bash
# Run all receipt E2E tests
npx playwright test ar-receipt --headed

# Run specific scenario
npx playwright test ar-receipt-workflow.spec.ts --grep "E2E-001"

# Debug mode
npx playwright test ar-receipt --debug
```
