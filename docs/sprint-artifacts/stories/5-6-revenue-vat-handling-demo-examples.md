# Story 5.6: Revenue & VAT Handling - Demo Examples

File này chứa các ví dụ demo cụ thể với dữ liệu thực tế để minh họa các workflow của Story 5.6.

---

## Example 1: Tạo Hóa Đơn Bán Hàng với VAT Calculation

### Input Data

**Company Settings:**
- Default VAT Rate: 10%
- Company: ABC Trading Co., Ltd.
- Company Tax Code: 0123456789

**Customer:**
- Customer Code: CUST-001
- Customer Name: XYZ Manufacturing Co., Ltd.
- Customer Tax Code: 9876543210
- Address: 123 Main Street, Ho Chi Minh City

**Invoice Details:**
- Invoice Date: 2025-01-15
- Due Date: 2025-02-14 (Net 30)
- Reference: PO-2025-001

**Line Items:**

| Line | Description | Qty | Unit Price | VAT Rate | Revenue Account |
|------|-------------|-----|------------|----------|-----------------|
| 1 | Laptop Dell XPS 15 | 5 | 25,000,000 | 10% | 5111 (Sales - IT Equipment) |
| 2 | Software License (Annual) | 1 | 50,000,000 | 0% | 5112 (Sales - Software) |
| 3 | Installation Service | 1 | 5,000,000 | 10% | 5113 (Sales - Services) |

### Calculation Process

**Line 1:**
- Line Total = 5 × 25,000,000 = 125,000,000 VND
- VAT = 125,000,000 × 10% = 12,500,000 VND
- Rounded VAT (nearest 100) = 12,500,000 VND
- Line Grand Total = 125,000,000 + 12,500,000 = 137,500,000 VND

**Line 2:**
- Line Total = 1 × 50,000,000 = 50,000,000 VND
- VAT = 50,000,000 × 0% = 0 VND
- Line Grand Total = 50,000,000 VND

**Line 3:**
- Line Total = 1 × 5,000,000 = 5,000,000 VND
- VAT = 5,000,000 × 10% = 500,000 VND
- Rounded VAT (nearest 100) = 500,000 VND
- Line Grand Total = 5,000,000 + 500,000 = 5,500,000 VND

**Invoice Totals:**
- Total Amount (before VAT) = 125,000,000 + 50,000,000 + 5,000,000 = 180,000,000 VND
- Total VAT = 12,500,000 + 0 + 500,000 = 13,000,000 VND
- Grand Total = 180,000,000 + 13,000,000 = 193,000,000 VND

### VAT Validation

- Header VAT = 13,000,000 VND
- Sum of Line VAT = 12,500,000 + 0 + 500,000 = 13,000,000 VND
- Variance = 13,000,000 - 13,000,000 = 0 VND
- **Status: ✅ PASS** (variance < 1000 VND)

### GL Split on Post

**Voucher Entry:**

| Account | Description | Debit | Credit |
|---------|-------------|-------|--------|
| 131 | Accounts Receivable | 193,000,000 | |
| 5111 | Sales - IT Equipment | | 125,000,000 |
| 5112 | Sales - Software | | 50,000,000 |
| 5113 | Sales - Services | | 5,000,000 |
| 3331 | Output VAT | | 13,000,000 |
| **Total** | | **193,000,000** | **193,000,000** |

✅ **Balanced:** Debit = Credit

---

## Example 2: VAT Rate Override Warning

### Scenario

**Company Default VAT:** 10%

**Invoice Line:**
- Description: Export Goods (VAT Exempt)
- Qty: 10
- Unit Price: 10,000,000
- **User selects VAT Rate: EXEMPT** (overriding default 10%)

### System Behavior

**Warning Dialog Displayed:**
```
⚠️ VAT Rate Override Warning

You are overriding the default VAT rate from 10% to EXEMPT.

Ensure this is correct per customer agreement.

Original: 10% (Company Default)
Selected: EXEMPT

[Cancel] [Confirm Override]
```

**If User Confirms:**
- Line saved with VAT = EXEMPT
- Audit log entry created:
  ```json
  {
    "action": "VAT_RATE_OVERRIDE",
    "invoiceId": "inv-2025-001",
    "lineItemId": "line-001",
    "oldRate": "10%",
    "newRate": "EXEMPT",
    "userId": "user-123",
    "timestamp": "2025-01-15T10:30:00Z"
  }
  ```

---

## Example 3: VAT Sum Validation - Warning Case

### Invoice Data

**Line Items:**

| Line | Description | Qty | Unit Price | VAT Rate | Line Total | Line VAT |
|------|-------------|-----|------------|----------|------------|----------|
| 1 | Product A | 3 | 1,000,000 | 10% | 3,000,000 | 300,000 |
| 2 | Product B | 2 | 1,500,000 | 10% | 3,000,000 | 300,000 |
| 3 | Product C | 1 | 1,000,000 | 10% | 1,000,000 | 100,000 |

**Calculation:**
- Sum of Line VAT = 300,000 + 300,000 + 100,000 = 700,000 VND
- Header VAT (manually entered) = 700,500 VND
- Variance = 700,500 - 700,000 = 500 VND

**System Response:**
```
⚠️ VAT Rounding Variance: 500 VND

Header VAT: 700,500 VND
Sum of Line VAT: 700,000 VND
Variance: 500 VND (< 1000 VND threshold)

You can proceed with posting, but please verify the calculation.

[Cancel] [Post with Warning]
```

✅ **Status: ALLOW POST** (variance < 1000 VND)

---

## Example 4: VAT Sum Validation - Block Case

### Invoice Data

**Line Items:**

| Line | Description | Qty | Unit Price | VAT Rate | Line Total | Line VAT |
|------|-------------|-----|------------|----------|------------|----------|
| 1 | Product A | 10 | 5,000,000 | 10% | 50,000,000 | 5,000,000 |
| 2 | Product B | 5 | 10,000,000 | 10% | 50,000,000 | 5,000,000 |

**Calculation:**
- Sum of Line VAT = 5,000,000 + 5,000,000 = 10,000,000 VND
- Header VAT (manually entered) = 10,500,000 VND
- Variance = 10,500,000 - 10,000,000 = 500,000 VND

**System Response:**
```
❌ VAT Validation Failed

Header VAT: 10,500,000 VND
Sum of Line VAT: 10,000,000 VND
Variance: 500,000 VND (≥ 1000 VND threshold)

Posting is blocked. Please correct the VAT amounts.

[OK]
```

❌ **Status: BLOCK POST** (variance ≥ 1000 VND)

---

## Example 5: VAT Correction Workflow

### Original Invoice

**Invoice:** INV-2025-001
**Status:** POSTED
**Original VAT Amount:** 13,000,000 VND

### Correction Request

**User:** Admin (admin-001)
**Action:** Create VAT Correction

**Correction Details:**
- Old VAT Amount: 13,000,000 VND
- New VAT Amount: 12,500,000 VND
- Variance: -500,000 VND
- Reason: "Customer provided tax exemption certificate retroactively. Line 2 should be VAT exempt instead of 0%."

### System Check

**Variance = |12,500,000 - 13,000,000| = 500,000 VND**

**Threshold Check:**
- Default Threshold: 10,000,000 VND
- Variance (500,000) < Threshold (10,000,000)
- **Status: AUTO APPROVE** ✅

### Auto-Approval Process

1. System applies correction immediately
2. Updates invoice VAT amount: 13,000,000 → 12,500,000
3. Regenerates voucher with corrected amounts
4. Logs audit entry:
   ```json
   {
     "action": "VAT_CORRECTION_APPLIED",
     "correctionId": "corr-001",
     "invoiceId": "inv-2025-001",
     "oldVATAmount": 13000000,
     "newVATAmount": 12500000,
     "variance": -500000,
     "reason": "Customer provided tax exemption certificate...",
     "status": "APPROVED",
     "approvedBy": "system-auto",
     "approvedAt": "2025-01-20T14:30:00Z"
   }
   ```

---

## Example 6: VAT Correction - Requires Approval

### Original Invoice

**Invoice:** INV-2025-002
**Status:** POSTED
**Original VAT Amount:** 50,000,000 VND

### Correction Request

**User:** Admin (admin-001)
**Action:** Create VAT Correction

**Correction Details:**
- Old VAT Amount: 50,000,000 VND
- New VAT Amount: 45,000,000 VND
- Variance: -5,000,000 VND
- Reason: "Customer contract specifies 5% VAT rate instead of 10% for this invoice."

### System Check

**Variance = |45,000,000 - 50,000,000| = 5,000,000 VND**

**Threshold Check:**
- Default Threshold: 10,000,000 VND
- Variance (5,000,000) < Threshold (10,000,000)
- **Wait, recalculate...**

Actually, let me correct: If threshold is 10M and variance is 5M, it should still be auto-approved. Let me use a better example:

**Correction Details (Revised):**
- Old VAT Amount: 50,000,000 VND
- New VAT Amount: 35,000,000 VND
- Variance: -15,000,000 VND
- Reason: "Customer contract specifies 0% VAT rate (export) instead of 10%."

**Variance = |35,000,000 - 50,000,000| = 15,000,000 VND**

**Threshold Check:**
- Default Threshold: 10,000,000 VND
- Variance (15,000,000) > Threshold (10,000,000)
- **Status: PENDING_APPROVAL** ⏳

### Approval Workflow

1. **Correction Created:**
   - Status: PENDING_APPROVAL
   - Notification sent to Chief Accountant

2. **Chief Accountant Reviews:**
   - Views correction details
   - Views original invoice
   - Views impact on voucher
   - Decision: APPROVE

3. **System Applies Correction:**
   - Updates invoice VAT: 50,000,000 → 35,000,000
   - Regenerates voucher
   - Logs approval audit

---

## Example 7: Credit Note Creation

### Original Invoice

**Invoice:** INV-2025-003
**Invoice Date:** 2025-01-10
**Status:** POSTED
**Customer:** XYZ Manufacturing Co., Ltd.

**Line Items:**

| Line | Description | Qty | Unit Price | VAT Rate | Line Total | Line VAT |
|------|-------------|-----|------------|----------|------------|----------|
| 1 | Product A (Defective) | 10 | 2,000,000 | 10% | 20,000,000 | 2,000,000 |
| 2 | Product B | 5 | 3,000,000 | 10% | 15,000,000 | 1,500,000 |

**Totals:**
- Total Amount: 35,000,000 VND
- Total VAT: 3,500,000 VND
- Grand Total: 38,500,000 VND

### Credit Note Creation

**Reason:** Customer returned 10 units of Product A (defective)

**Credit Note:** CN-2025-001
**Credit Note Date:** 2025-01-25
**Original Invoice:** INV-2025-003

**Line Items (Auto-populated, then edited):**

| Line | Description | Qty | Unit Price | VAT Rate | Line Total | Line VAT |
|------|-------------|-----|------------|----------|------------|----------|
| 1 | Product A (Returned) | 10 | -2,000,000 | 10% | -20,000,000 | -2,000,000 |

**Totals:**
- Total Amount: -20,000,000 VND
- Total VAT: -2,000,000 VND
- Grand Total: -22,000,000 VND

### Inverted GL Split

**Voucher Entry:**

| Account | Description | Debit | Credit |
|---------|-------------|-------|--------|
| 131 | Accounts Receivable | | 22,000,000 |
| 5111 | Sales - Product A | 20,000,000 | |
| 3331 | Output VAT | 2,000,000 | |
| **Total** | | **22,000,000** | **22,000,000** |

✅ **Balanced:** Debit = Credit (inverted from original)

**Audit Trail Link:**
```json
{
  "creditNoteId": "cn-2025-001",
  "originalInvoiceId": "inv-2025-003",
  "action": "CREDIT_NOTE_CREATED",
  "crossReference": {
    "originalVoucherId": "voucher-003",
    "creditNoteVoucherId": "voucher-010"
  }
}
```

---

## Example 8: Output VAT Report (ND123 Format)

### Report Parameters

- **Period:** 2025-01
- **Customer:** All (no filter)
- **VAT Class:** All

### Sample Data

**Invoices Posted in January 2025:**

| Invoice # | Date | Customer | Tax Code | Revenue (0%) | Revenue (5%) | Revenue (10%) | Revenue (Exempt) | VAT Collected |
|----------|------|----------|----------|--------------|--------------|---------------|------------------|---------------|
| INV-2025-001 | 2025-01-15 | XYZ Manufacturing | 9876543210 | 50,000,000 | 0 | 130,000,000 | 0 | 13,000,000 |
| INV-2025-002 | 2025-01-20 | ABC Trading | 1234567890 | 0 | 0 | 100,000,000 | 0 | 10,000,000 |
| INV-2025-003 | 2025-01-25 | Export Co. | 5555555555 | 0 | 0 | 0 | 80,000,000 | 0 |
| INV-2025-004 | 2025-01-28 | Local Retailer | 1111111111 | 0 | 25,000,000 | 0 | 0 | 1,250,000 |

### Aggregated Report

**Totals:**
- Revenue (0%): 50,000,000 VND
- Revenue (5%): 25,000,000 VND
- Revenue (10%): 230,000,000 VND
- Revenue (Exempt): 80,000,000 VND
- **Total Revenue:** 385,000,000 VND
- **Total VAT Collected:** 24,250,000 VND

### ND123 Excel Export Format

```
BÁO CÁO VAT ĐẦU RA - THÁNG 01/2025
Công ty: ABC Trading Co., Ltd.
Mã số thuế: 0123456789
Kỳ báo cáo: 01/2025

┌─────────────┬──────────────┬──────────────────┬──────────────┬──────────────┬──────────────┬──────────────┬─────────────────┐
│ Số HĐ       │ Ngày HĐ      │ Tên KH          │ Mã số thuế KH │ Doanh thu 0% │ Doanh thu 5% │ Doanh thu 10%│ Doanh thu Exempt│ VAT Thu được    │
├─────────────┼──────────────┼──────────────────┼──────────────┼──────────────┼──────────────┼──────────────┼─────────────────┤
│ INV-2025-001│ 15/01/2025   │ XYZ Manufacturing│ 9876543210   │ 50,000,000  │ 0            │ 130,000,000 │ 0               │ 13,000,000     │
│ INV-2025-002│ 20/01/2025   │ ABC Trading      │ 1234567890   │ 0            │ 0            │ 100,000,000 │ 0               │ 10,000,000     │
│ INV-2025-003│ 25/01/2025   │ Export Co.       │ 5555555555   │ 0            │ 0            │ 0            │ 80,000,000      │ 0               │
│ INV-2025-004│ 28/01/2025   │ Local Retailer  │ 1111111111   │ 0            │ 25,000,000   │ 0            │ 0               │ 1,250,000      │
├─────────────┼──────────────┼──────────────────┼──────────────┼──────────────┼──────────────┼──────────────┼─────────────────┤
│ TỔNG CỘNG   │              │                  │              │ 50,000,000   │ 25,000,000   │ 230,000,000 │ 80,000,000      │ 24,250,000      │
└─────────────┴──────────────┴──────────────────┴──────────────┴──────────────┴──────────────┴──────────────┴─────────────────┘

Ngày xuất báo cáo: 31/01/2025 15:30:00
Người xuất: Nguyễn Văn A (admin-001)
Hash: SHA256(...) - For audit verification
```

---

## Example 9: Rounding Rules (Circular 200)

### Scenario: VAT Rounding to Nearest 100 VND

**Line Item:**
- Line Total: 1,234,567 VND
- VAT Rate: 10%

**Calculation:**
1. **Exact VAT:** 1,234,567 × 10% = 123,456.7 VND
2. **Round to nearest 100:** 123,500 VND ✅

**Examples:**

| Line Total | VAT Rate | Exact VAT | Rounded VAT |
|------------|----------|-----------|-------------|
| 1,234,567 | 10% | 123,456.7 | 123,500 |
| 1,234,432 | 10% | 123,443.2 | 123,400 |
| 1,234,550 | 10% | 123,455.0 | 123,500 |
| 1,234,449 | 10% | 123,444.9 | 123,400 |

**Rule:** Round to nearest 100 VND (0, 100, 200, ..., 900)

---

## Example 10: Idempotent Posting

### Scenario: Network Retry

**Invoice:** INV-2025-005
**Status:** DRAFT
**Action:** User clicks "Post Invoice"

**First Request:**
```
POST /api/v1/sales-invoices/inv-2025-005/post
→ Success: Voucher created (voucher-015)
→ Invoice status: POSTED
```

**Network Issue - User Retries:**
```
POST /api/v1/sales-invoices/inv-2025-005/post (retry)
→ System checks: Invoice already POSTED
→ System checks: Voucher already exists (voucher-015)
→ Response: 200 OK (idempotent - no duplicate created)
```

**Database Constraint:**
```sql
UNIQUE CONSTRAINT (invoice_id, voucher_id)
-- Prevents duplicate voucher creation
```

✅ **Result:** No duplicate vouchers created, system returns existing voucher ID

---

## Summary of Key Workflows

1. **Invoice Posting:** Validate VAT → Generate GL Split → Create Voucher → Update Status
2. **VAT Correction:** Create Request → Check Threshold → Auto Approve or Require Approval → Apply Correction
3. **Credit Note:** Select Original Invoice → Auto-populate → Generate Inverted GL Split → Create Voucher
4. **VAT Report:** Filter by Period/Customer → Aggregate by VAT Rate → Export ND123 Format

Tất cả các workflow đều có audit trail đầy đủ và tuân thủ TT200/Circular 200.

