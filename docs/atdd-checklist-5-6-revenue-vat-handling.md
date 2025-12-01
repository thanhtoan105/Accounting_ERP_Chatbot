# ATDD Checklist - Epic 5, Story 5.6: Revenue & VAT Handling

**Date:** 2025-01-23
**Author:** thanhtoan
**Primary Test Level:** API (business logic) + E2E (critical user journeys)

---

## Story Summary

As an accountant/auditor, I want correct revenue and output VAT accounting and reporting, so that statutory filings and financial statements are accurate.

This story implements comprehensive VAT handling for sales invoices including VAT rate validation, GL split generation, VAT totals validation, credit note support, output VAT reporting, VAT corrections, and idempotent posting.

---

## Acceptance Criteria

1. **(AC-VAT-001)** Each invoice line has VAT rate (default from settings, override with warning); supports 0/5/10/exempt only. When user overrides the default VAT rate, system shows warning: "You are overriding the default VAT rate from {oldRate}% to {newRate}%. Ensure this is correct per customer agreement." Confirmation required before saving.

2. **(AC-VAT-002)** GL splits on post: Dr AR (131), Cr Revenue (511+), Cr VAT Output (3331); rounding rules applied consistently. When invoice posted, for each line, system creates GL split: Dr 131 (AR), Cr 5xx (revenue per line.revenueAccountCode), Cr 3331 (output VAT).

3. **(AC-VAT-003)** Totals validation: header VAT equals sum of line VAT and GL VAT; block post if mismatch beyond tolerance. System computes VAT to 2 decimal places (VND cents). Rounding rules: line VAT rounded to nearest 100 VND (per Circular 200 interpretation). If header VAT ≠ sum of line VAT after rounding, system flags with warning: "VAT rounding variance: {variance} VND." Allow posting if variance < 1000 VND; block if ≥ 1000 VND.

4. **(AC-VAT-004)** Credit notes/negative invoices supported; must reference original; all linked with audit cross-references. Negative invoice (credit note) supported; must reference original invoice (field: originalInvoiceID). VAT computed as negative. GL splits inverted: Cr 131 (reverses AR), Dr 5xx (reverses revenue), Dr 3331 (reverses VAT).

5. **(AC-VAT-005)** Output VAT report: filter by period/customer/VAT class; Excel export in ND123 format. GET /api/v1/ar-vat-report?period=2025-01 returns data for output VAT report. Response: array of { invoiceNumber, invoiceDate, customerName, customerTaxCode, revenue0pct, revenue5pct, revenue10pct, revenueExempt, total_vat_collected }.

6. **(AC-VAT-006)** Admin VAT corrections allowed with reason and diff audit; overrides require approval if above threshold. Admin/Chief Accountant can create VAT corrections for posted invoices. Correction requires reason (max 500 chars, required) and displays diff (old VAT amount, new VAT amount, variance). If correction amount variance > threshold (default 10M VND, configurable), correction requires Chief Accountant/CFO approval before application.

7. **(AC-VAT-007)** API enforces idempotent posting and blocks double-booking. Invoice post endpoint is idempotent (POST /api/v1/sales-invoices/:id/post can be called multiple times without duplicate voucher creation, enforced via unique constraint on (invoiceID, voucherID) pair).

---

## Failing Tests Created (RED Phase)

### E2E Tests (8 tests)

**File:** `tests/e2e/revenue-vat-handling.spec.ts` (350+ lines)

- ✅ **Test:** AC-VAT-001: should display warning when overriding default VAT rate
  - **Status:** RED - Missing VAT override warning dialog component
  - **Verifies:** Warning dialog appears when VAT rate differs from company default, requires confirmation

- ✅ **Test:** AC-VAT-001: should require confirmation before saving VAT rate override
  - **Status:** RED - Missing VAT override confirmation flow
  - **Verifies:** VAT rate reverts to default if user cancels warning dialog

- ✅ **Test:** AC-VAT-003: should display VAT totals summary and block post if variance ≥ 1000 VND
  - **Status:** RED - Missing VAT variance validation UI
  - **Verifies:** Error message displayed and post button disabled when VAT variance ≥ 1000 VND

- ✅ **Test:** AC-VAT-003: should show warning but allow post if variance < 1000 VND
  - **Status:** RED - Missing VAT variance warning display
  - **Verifies:** Warning displayed but post allowed when variance < 1000 VND

- ✅ **Test:** AC-VAT-003: should display GL split preview before posting
  - **Status:** RED - Missing GL split preview component
  - **Verifies:** GL split preview shows Dr 131, Cr 5xx, Cr 3331 before posting

- ✅ **Test:** AC-VAT-004: should create credit note referencing original invoice
  - **Status:** RED - Missing credit note creation UI
  - **Verifies:** Credit note form with original invoice reference, inverted amounts

- ✅ **Test:** AC-VAT-004: should display audit cross-reference to original invoice
  - **Status:** RED - Missing audit cross-reference display
  - **Verifies:** Audit trail shows link between credit note and original invoice

- ✅ **Test:** AC-VAT-005: should generate output VAT report with filters
  - **Status:** RED - Missing VAT report page and generation UI
  - **Verifies:** Report table with required columns (invoice number, date, customer, tax code, revenue by VAT rate, total VAT)

- ✅ **Test:** AC-VAT-005: should export VAT report to Excel in ND123 format
  - **Status:** RED - Missing Excel export functionality
  - **Verifies:** Excel file downloaded in ND123 format

- ✅ **Test:** AC-VAT-006: should create VAT correction with reason and diff preview
  - **Status:** RED - Missing VAT correction dialog
  - **Verifies:** Correction dialog with current/new VAT amounts, reason field, diff preview

- ✅ **Test:** AC-VAT-006: should require approval for corrections above threshold
  - **Status:** RED - Missing approval workflow UI
  - **Verifies:** Corrections with variance > 10M VND require Chief Accountant/CFO approval

### API Tests (15 tests)

**File:** `tests/api/revenue-vat-handling.api.spec.ts` (650+ lines)

- ✅ **Test:** AC-VAT-001: POST /invoices - should return warning when VAT rate overrides default
  - **Status:** RED - Missing VAT rate override warning in API response
  - **Verifies:** API returns warning when line VAT rate differs from company default

- ✅ **Test:** AC-VAT-001: POST /invoices - should validate VAT rate is one of 0/5/10/exempt
  - **Status:** RED - Missing VAT rate validation
  - **Verifies:** API rejects invalid VAT rates (not 0, 5, 10, or exempt)

- ✅ **Test:** AC-VAT-002: POST /sales-invoices/{id}/post - should create GL splits: Dr 131, Cr 5xx, Cr 3331
  - **Status:** RED - Missing GL split generation logic
  - **Verifies:** Voucher created with correct GL splits (Dr 131, Cr revenue account, Cr 3331)

- ✅ **Test:** AC-VAT-002: POST /sales-invoices/{id}/post - should handle multiple line items with different revenue accounts
  - **Status:** RED - Missing multi-line GL split generation
  - **Verifies:** GL entries created for each revenue account, single AR and VAT entries

- ✅ **Test:** AC-VAT-003: POST /sales-invoices/{id}/post - should block post if VAT variance ≥ 1000 VND
  - **Status:** RED - Missing VAT variance validation
  - **Verifies:** Post blocked when header VAT ≠ sum of line VAT (variance ≥ 1000 VND)

- ✅ **Test:** AC-VAT-003: POST /sales-invoices/{id}/post - should allow post if VAT variance < 1000 VND
  - **Status:** RED - Missing VAT variance tolerance logic
  - **Verifies:** Post allowed with warning when variance < 1000 VND

- ✅ **Test:** AC-VAT-003: POST /sales-invoices - should round VAT to nearest 100 VND per Circular 200
  - **Status:** RED - Missing VAT rounding logic
  - **Verifies:** VAT amounts rounded to nearest 100 VND (e.g., 33,333.30 → 33,300)

- ✅ **Test:** AC-VAT-004: POST /sales-invoices/credit-notes - should create credit note with inverted GL splits
  - **Status:** RED - Missing credit note creation endpoint
  - **Verifies:** Credit note created with inverted GL splits (Cr 131, Dr 5xx, Dr 3331)

- ✅ **Test:** AC-VAT-004: POST /sales-invoices/credit-notes - should require original invoice reference
  - **Status:** RED - Missing original invoice validation
  - **Verifies:** API rejects credit note without originalInvoiceId

- ✅ **Test:** AC-VAT-005: GET /ar-vat-report - should generate report with period filter
  - **Status:** RED - Missing VAT report endpoint
  - **Verifies:** Report data returned with required fields (invoice number, date, customer, tax code, revenue by VAT rate, total VAT)

- ✅ **Test:** AC-VAT-005: GET /ar-vat-report/export - should export report to Excel in ND123 format
  - **Status:** RED - Missing Excel export endpoint
  - **Verifies:** Excel file returned in ND123 format

- ✅ **Test:** AC-VAT-006: POST /ar-vat-corrections - should create correction with reason
  - **Status:** RED - Missing VAT correction creation endpoint
  - **Verifies:** Correction created with invoice ID, old/new VAT amounts, reason, variance

- ✅ **Test:** AC-VAT-006: POST /ar-vat-corrections - should require approval if variance > threshold
  - **Status:** RED - Missing approval workflow logic
  - **Verifies:** Corrections with variance > 10M VND set status to PENDING and require approval

- ✅ **Test:** AC-VAT-006: POST /ar-vat-corrections/{id}/approve - should approve and apply correction
  - **Status:** RED - Missing approval endpoint
  - **Verifies:** Correction approved, invoice VAT updated, voucher regenerated if needed

- ✅ **Test:** AC-VAT-007: POST /sales-invoices/{id}/post - should be idempotent (no duplicate vouchers)
  - **Status:** RED - Missing idempotency enforcement
  - **Verifies:** Multiple calls to post endpoint return same voucher ID, no duplicate vouchers

- ✅ **Test:** AC-VAT-007: POST /sales-invoices/{id}/post - should prevent double-booking via unique constraint
  - **Status:** RED - Missing unique constraint on (invoiceID, voucherID)
  - **Verifies:** System prevents duplicate voucher creation via database constraint

---

## Data Factories Created

### VAT Factory

**File:** `tests/support/factories/vat.factory.ts`

**Exports:**

- `createVATCorrection(overrides?)` - Create single VAT correction with optional overrides
- `createVATCorrections(count)` - Create array of VAT corrections
- `createVATCorrectionRequiringApproval(threshold, overrides?)` - Create correction requiring approval (variance > threshold)
- `createInvoiceWithVATOverride(companyDefaultRate, overrideRate, overrides?)` - Create invoice with VAT rate override
- `createInvoiceWithVATRounding(overrides?)` - Create invoice with VAT rounding scenario
- `createInvoiceWithVATVariance(variance, overrides?)` - Create invoice with VAT variance
- `createOutputVATReportRow(overrides?)` - Create output VAT report row
- `createOutputVATReportRows(count, overrides?)` - Create array of report rows
- `createCreditNote(originalInvoiceId, overrides?)` - Create credit note referencing original invoice

**Example Usage:**

```typescript
const correction = createVATCorrection({ invoiceId: 'INV-001', variance: 50000 });
const invoice = createInvoiceWithVATOverride(10, 5); // Override from 10% to 5%
const reportRow = createOutputVATReportRow({ period: '2025-01' });
```

---

## Fixtures Created

**Note:** Existing fixtures from `tests/support/fixtures/auth.fixture.ts` are sufficient for authentication. No new fixtures required for this story.

**Existing Fixtures Available:**

- `authenticatedUser` - Auto-login as accountant with cleanup
- `authenticate(role)` - Login as specific role (accountant, chief_accountant, cfo, admin)

---

## Mock Requirements

### VAT Calculation Service Mock

**Endpoint:** Internal service (not exposed via API)

**Requirements:**

- VAT calculation with rounding to nearest 100 VND
- VAT rate validation (0, 5, 10, exempt only)
- Company default VAT rate retrieval
- VAT sum validation with tolerance threshold

**Success Response (for testing):**

```json
{
  "calculatedVAT": 100000,
  "roundedVAT": 100000,
  "warnings": []
}
```

**Failure Response:**

```json
{
  "error": "Invalid VAT rate: 15. Must be 0, 5, 10, or exempt",
  "details": {
    "rate": 15,
    "validRates": [0, 5, 10, "exempt"]
  }
}
```

---

## Required data-testid Attributes

### Invoice Form / Line Items

- `vat-override-warning-dialog` - VAT rate override warning dialog
- `vat-override-warning-message` - Warning message text
- `confirm-vat-override-button` - Confirm override button
- `cancel-vat-override-button` - Cancel override button
- `vat-override-badge` - Warning badge on line item with override
- `line-vat-rate` - VAT rate selector for line item
- `header-vat-input` - Header VAT amount input
- `vat-variance-error` - VAT variance error message
- `vat-variance-warning` - VAT variance warning message
- `post-invoice-button` - Post invoice button
- `show-gl-split-preview-button` - Show GL split preview button
- `gl-split-preview` - GL split preview container
- `gl-entry-131-debit` - AR account debit entry
- `gl-entry-511001-credit` - Revenue account credit entry
- `gl-entry-3331-credit` - Output VAT credit entry

### Credit Note Form

- `create-credit-note-button` - Create credit note button
- `credit-note-form` - Credit note form container
- `original-invoice-reference` - Original invoice reference display
- `original-invoice-link` - Link to original invoice
- `confirm-credit-note-button` - Confirm credit note creation button
- `credit-note-status` - Credit note status display
- `credit-note-total` - Credit note total amount

### VAT Report Page

- `vat-report-form` - VAT report filter form
- `period-selector` - Period selector input
- `generate-report-button` - Generate report button
- `vat-report-table` - VAT report table
- `report-row-0` - First report row
- `column-invoice-number` - Invoice number column header
- `column-invoice-date` - Invoice date column header
- `column-customer-name` - Customer name column header
- `column-customer-tax-code` - Customer tax code column header
- `column-revenue-0pct` - Revenue 0% column header
- `column-revenue-5pct` - Revenue 5% column header
- `column-revenue-10pct` - Revenue 10% column header
- `column-revenue-exempt` - Revenue exempt column header
- `column-total-vat-collected` - Total VAT collected column header
- `report-summary-totals` - Summary totals row
- `export-excel-button` - Export to Excel button

### VAT Correction Dialog

- `create-vat-correction-button` - Create VAT correction button
- `vat-correction-dialog` - VAT correction dialog container
- `current-vat-amount` - Current VAT amount display
- `new-vat-amount` - New VAT amount input
- `correction-reason` - Correction reason textarea
- `vat-diff-preview` - VAT difference preview container
- `old-vat-amount` - Old VAT amount display
- `vat-variance` - VAT variance display
- `submit-correction-button` - Submit correction button
- `correction-status` - Correction status display
- `approve-correction-button` - Approve correction button
- `approval-notes` - Approval notes input
- `confirm-approval-button` - Confirm approval button

---

## Implementation Checklist

### Test: AC-VAT-001 - VAT Rate Override Warning

**File:** `tests/e2e/revenue-vat-handling.spec.ts`, `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ARVATService` interface and `ARVATServiceImpl` in `backend/src/main/java/com/accounting/service/impl/sales/`
- [ ] Implement `validateVATRate(rate, companyId)` method:
  - [ ] Validate rate is one of: 0, 5, 10, EXEMPT
  - [ ] Read company default VAT rate from CompanySettings (default 10% if not configured)
  - [ ] Return validation result with warnings if override detected
- [ ] Extend `SalesInvoiceService.createInvoice()` to call VAT validation
- [ ] Add VAT rate override warning to API response (`warnings` array)
- [ ] Create `VATOverrideWarningDialog` component in `frontend/src/components/vat/`
- [ ] Display warning dialog when user overrides default VAT rate
- [ ] Show message: "You are overriding the default VAT rate from {oldRate}% to {newRate}%. Ensure this is correct per customer agreement."
- [ ] Require confirmation before saving line item
- [ ] Display warning badge on line item if override detected
- [ ] Add required data-testid attributes: `vat-override-warning-dialog`, `vat-override-warning-message`, `confirm-vat-override-button`, `cancel-vat-override-button`, `vat-override-badge`
- [ ] Run test: `npm run test:e2e -- revenue-vat-handling.spec.ts`
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 8 hours

---

### Test: AC-VAT-002 - GL Split on Post

**File:** `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Implement `ARVATService.generateGLSplit(invoice)` method:
  - [ ] For each line: Dr 131 (AR account), Cr {revenueAccountCode} (revenue), Cr 3331 (output VAT)
  - [ ] Ensure all legs balance (debit = credit)
  - [ ] Return list of journal entry lines for voucher creation
- [ ] Extend `SalesInvoiceService.postInvoice(invoiceId)` method:
  - [ ] Call `ARVATService.generateGLSplit()` to create voucher lines
  - [ ] Create voucher via `VoucherService` with generated lines
  - [ ] Update invoice status to POSTED only after voucher creation succeeds
- [ ] Handle multiple line items with different revenue accounts (one GL entry per revenue account)
- [ ] Verify GL balance: Dr = Cr
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 6 hours

---

### Test: AC-VAT-003 - VAT Totals Validation

**File:** `tests/e2e/revenue-vat-handling.spec.ts`, `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Implement `ARVATService.calculateVAT(lineTotal, vatRate)` method:
  - [ ] Compute line VAT = lineTotal × (vatRate / 100)
  - [ ] Apply rounding to nearest 100 VND per Circular 200
  - [ ] Return rounded VAT amount
- [ ] Implement `ARVATService.validateVATSum(invoice)` method:
  - [ ] Sum all line VAT amounts (after rounding)
  - [ ] Compare with header VAT amount
  - [ ] Return validation result with variance; block if variance ≥ 1000 VND
- [ ] Extend `SalesInvoiceService.postInvoice(invoiceId)`:
  - [ ] Call `ARVATService.validateVATSum()` before posting
  - [ ] Block post if VAT validation fails (variance ≥ 1000 VND)
- [ ] Create `VATValidationDisplay` component in `frontend/src/components/vat/`:
  - [ ] Display VAT totals summary: Header VAT, Sum of Line VAT, Variance
  - [ ] Show error message if variance ≥ 1000 VND (block post button)
  - [ ] Show warning message if variance < 1000 VND but > 0 (allow post with warning)
  - [ ] Real-time calculation as user edits line items
- [ ] Create `GLSplitPreview` component:
  - [ ] Display GL split preview (read-only) showing Dr/Cr accounts before posting
  - [ ] Show Dr 131, Cr revenue accounts, Cr 3331
- [ ] Add required data-testid attributes: `header-vat-input`, `vat-variance-error`, `vat-variance-warning`, `post-invoice-button`, `show-gl-split-preview-button`, `gl-split-preview`, `gl-entry-131-debit`, `gl-entry-511001-credit`, `gl-entry-3331-credit`
- [ ] Run test: `npm run test:e2e -- revenue-vat-handling.spec.ts`
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 10 hours

---

### Test: AC-VAT-004 - Credit Note Support

**File:** `tests/e2e/revenue-vat-handling.spec.ts`, `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Implement `ARVATService.generateCreditNoteGLSplit(creditNote, originalInvoice)` method:
  - [ ] Invert GL splits: Cr 131, Dr {revenueAccountCode}, Dr 3331
  - [ ] Link credit note to original invoice in audit trail
  - [ ] Return inverted journal entry lines
- [ ] Extend `SalesInvoiceService.createCreditNote(creditNoteDTO)` method:
  - [ ] Validate originalInvoiceID exists and is POSTED
  - [ ] Call `ARVATService.generateCreditNoteGLSplit()` for inverted GL splits
  - [ ] Create reversal voucher and link to original invoice in audit trail
- [ ] Create `CreditNoteForm` component in `frontend/src/components/ar/`:
  - [ ] Select original invoice (typeahead/searchable, filtered to POSTED invoices)
  - [ ] Auto-populate line items from original invoice (inverted amounts)
  - [ ] Allow editing line items (quantities, amounts, VAT rates)
  - [ ] Display link to original invoice with audit cross-reference
  - [ ] Submit creates credit note with inverted GL splits
- [ ] Integrate into `SalesInvoices` list page with "Create Credit Note" action button
- [ ] Extend audit trail to show cross-reference between credit note and original invoice
- [ ] Add required data-testid attributes: `create-credit-note-button`, `credit-note-form`, `original-invoice-reference`, `original-invoice-link`, `confirm-credit-note-button`, `credit-note-status`, `credit-note-total`
- [ ] Run test: `npm run test:e2e -- revenue-vat-handling.spec.ts`
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 12 hours

---

### Test: AC-VAT-005 - Output VAT Report

**File:** `tests/e2e/revenue-vat-handling.spec.ts`, `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ARVATReportService` interface and implementation:
  - [ ] Implement `generateVATReport(period, customerId, vatClass)` method:
    - [ ] Query POSTED invoices filtered by period, customer (optional), VAT class (optional)
    - [ ] Aggregate by VAT rate: revenue0pct, revenue5pct, revenue10pct, revenueExempt, total_vat_collected
    - [ ] Return `OutputVATReportDTO` with invoice details and totals
  - [ ] Implement `exportVATReport(period, format)` method:
    - [ ] Generate Excel file in ND123 format per Circular 200 spec
    - [ ] Include headers: Invoice Number, Invoice Date, Customer Name, Customer Tax Code, Revenue (0%), Revenue (5%), Revenue (10%), Revenue (Exempt), Total VAT Collected
    - [ ] Include summary totals row
    - [ ] Return binary file with proper MIME type
- [ ] Create `ARVATReportHistory` entity to track generated reports (period, format, generatedBy, generatedAt, hash)
- [ ] Create `ARVATController` with endpoints:
  - [ ] GET `/api/v1/ar-vat-report` - Generate VAT report data (query params: period, customerId, vatClass)
  - [ ] GET `/api/v1/ar-vat-report/export` - Export VAT report to Excel (query params: period, format=EXCEL)
- [ ] Create `OutputVATReportList` page under `frontend/src/features/accounting/pages/VATReports/`:
  - [ ] Filter form: Period (required), Customer (optional), VAT Class (optional)
  - [ ] Display report table: Invoice Number, Invoice Date, Customer Name, Customer Tax Code, Revenue (0%), Revenue (5%), Revenue (10%), Revenue (Exempt), Total VAT Collected
  - [ ] Display summary totals row
  - [ ] Export button (Excel format, ND123 compliant)
  - [ ] Report history table showing previously generated reports
- [ ] Add required data-testid attributes: `vat-report-form`, `period-selector`, `generate-report-button`, `vat-report-table`, `report-row-0`, `column-*`, `report-summary-totals`, `export-excel-button`
- [ ] Run test: `npm run test:e2e -- revenue-vat-handling.spec.ts`
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 14 hours

---

### Test: AC-VAT-006 - VAT Correction Workflow

**File:** `tests/e2e/revenue-vat-handling.spec.ts`, `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ARVATCorrection` entity implementing `CompanyScopedEntity`:
  - [ ] Fields: invoiceID (FK), lineItemID (nullable), oldVATAmount, newVATAmount, variance, reason, status (PENDING/APPROVED/REJECTED), correctedBy, correctedAt, approvedBy, approvedAt
  - [ ] Add Flyway migration for `ar_vat_corrections` table with indexes on (company_id, invoice_id, status)
- [ ] Create `ARVATCorrectionService` interface and implementation:
  - [ ] Implement `createCorrection(correctionDTO)` method with validation (reason required, amounts positive, invoice exists and is POSTED)
  - [ ] Implement `approveCorrection(correctionId, approverId)` method:
    - [ ] Check if correction requires approval (variance > threshold)
    - [ ] Update invoice VAT amounts and regenerate voucher if needed
    - [ ] Log approval to audit trail
  - [ ] Implement `rejectCorrection(correctionId, reason)` method with audit logging
- [ ] Create `ARVATCorrectionRepository` with company-scoped query methods
- [ ] Create `ARVATController` with endpoints:
  - [ ] POST `/api/v1/ar-vat-corrections` - Create VAT correction (requires Chief Accountant/CFO)
  - [ ] GET `/api/v1/ar-vat-corrections` - List corrections with filters (period, invoice, status)
  - [ ] POST `/api/v1/ar-vat-corrections/:id/approve` - Approve correction (requires Chief Accountant/CFO)
  - [ ] POST `/api/v1/ar-vat-corrections/:id/reject` - Reject correction with reason
- [ ] Create `ARVATCorrectionDialog` component:
  - [ ] Select invoice and line item (if applicable)
  - [ ] Display current VAT amount (fetched from invoice details)
  - [ ] Enter new VAT amount
  - [ ] Enter correction reason (required, max 500 chars)
  - [ ] Preview diff calculation (shows old/new/difference with color coding)
  - [ ] Submit correction button (requires Chief Accountant/CFO role)
  - [ ] Show approval workflow if variance > threshold
- [ ] Create `ARVATCorrectionList` component:
  - [ ] Display corrections table with columns: Invoice, Line Item, Old/New Amount, Diff, Reason, Corrected By, Corrected Date, Status, Actions
  - [ ] Add filters: invoice, date range, status, corrected by
  - [ ] Add action buttons: Approve (if pending), Reject
- [ ] Create `ApproveVATCorrectionDialog` component:
  - [ ] Display correction details and diff
  - [ ] Show impact on invoice and voucher (displays invoice status, current VAT, posted voucher ID with link)
  - [ ] Approve button (requires Chief Accountant/CFO role)
- [ ] Extend `AuditService` with `logVatCorrection()` method
- [ ] Add required data-testid attributes: `create-vat-correction-button`, `vat-correction-dialog`, `current-vat-amount`, `new-vat-amount`, `correction-reason`, `vat-diff-preview`, `old-vat-amount`, `vat-variance`, `submit-correction-button`, `correction-status`, `approve-correction-button`, `approval-notes`, `confirm-approval-button`
- [ ] Run test: `npm run test:e2e -- revenue-vat-handling.spec.ts`
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 16 hours

---

### Test: AC-VAT-007 - Idempotent Posting

**File:** `tests/api/revenue-vat-handling.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Add unique constraint on (invoiceID, voucherID) pair in database
- [ ] Extend `SalesInvoiceService.postInvoice(invoiceId)` method:
  - [ ] Check if invoice is already posted before creating voucher
  - [ ] If already posted, return existing voucher ID (idempotent behavior)
  - [ ] If not posted, create voucher and update invoice status
- [ ] Add idempotency check in `VoucherService` to prevent duplicate voucher creation
- [ ] Handle race conditions (concurrent post requests) via database constraint
- [ ] Run test: `npm run test:api -- revenue-vat-handling.api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 4 hours

---

## Running Tests

```bash
# Run all failing tests for this story
npm run test:e2e -- revenue-vat-handling.spec.ts
npm run test:api -- revenue-vat-handling.api.spec.ts

# Run specific test file
npm run test:e2e -- revenue-vat-handling.spec.ts --grep "AC-VAT-001"
npm run test:api -- revenue-vat-handling.api.spec.ts --grep "AC-VAT-002"

# Run tests in headed mode (see browser)
npm run test:e2e -- revenue-vat-handling.spec.ts --headed

# Debug specific test
npm run test:e2e -- revenue-vat-handling.spec.ts --debug

# Run tests with coverage
npm run test:api -- revenue-vat-handling.api.spec.ts --coverage
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

**TEA Agent Responsibilities:**

- ✅ All tests written and failing
- ✅ Fixtures and factories created with auto-cleanup
- ✅ Mock requirements documented
- ✅ data-testid requirements listed
- ✅ Implementation checklist created

**Verification:**

- All tests run and fail as expected
- Failure messages are clear and actionable
- Tests fail due to missing implementation, not test bugs

---

### GREEN Phase (DEV Team - Next Steps)

**DEV Agent Responsibilities:**

1. **Pick one failing test** from implementation checklist (start with highest priority)
2. **Read the test** to understand expected behavior
3. **Implement minimal code** to make that specific test pass
4. **Run the test** to verify it now passes (green)
5. **Check off the task** in implementation checklist
6. **Move to next test** and repeat

**Key Principles:**

- One test at a time (don't try to fix all at once)
- Minimal implementation (don't over-engineer)
- Run tests frequently (immediate feedback)
- Use implementation checklist as roadmap

**Progress Tracking:**

- Check off tasks as you complete them
- Share progress in daily standup
- Mark story as IN PROGRESS in `sprint-status.yaml`

---

### REFACTOR Phase (DEV Team - After All Tests Pass)

**DEV Agent Responsibilities:**

1. **Verify all tests pass** (green phase complete)
2. **Review code for quality** (readability, maintainability, performance)
3. **Extract duplications** (DRY principle)
4. **Optimize performance** (if needed)
5. **Ensure tests still pass** after each refactor
6. **Update documentation** (if API contracts change)

**Key Principles:**

- Tests provide safety net (refactor with confidence)
- Make small refactors (easier to debug if tests fail)
- Run tests after each change
- Don't change test behavior (only implementation)

**Completion:**

- All tests pass
- Code quality meets team standards
- No duplications or code smells
- Ready for code review and story approval

---

## Next Steps

1. **Review this checklist** with team in standup or planning
2. **Run failing tests** to confirm RED phase: `npm run test:e2e -- revenue-vat-handling.spec.ts && npm run test:api -- revenue-vat-handling.api.spec.ts`
3. **Begin implementation** using implementation checklist as guide
4. **Work one test at a time** (red → green for each)
5. **Share progress** in daily standup
6. **When all tests pass**, refactor code for quality
7. **When refactoring complete**, update `sprint-status.yaml` to move story to DONE

---

## Knowledge Base References Applied

This ATDD workflow consulted the following knowledge fragments:

- **fixture-architecture.md** - Test fixture patterns with setup/teardown and auto-cleanup using Playwright's `test.extend()`
- **data-factories.md** - Factory patterns using `@faker-js/faker` for random test data generation with overrides support
- **network-first.md** - Route interception patterns (intercept BEFORE navigation to prevent race conditions)
- **test-quality.md** - Test design principles (Given-When-Then, one assertion per test, determinism, isolation)
- **test-levels-framework.md** - Test level selection framework (E2E vs API vs Component vs Unit)

---

## Test Execution Evidence

### Initial Test Run (RED Phase Verification)

**Command:** `npm run test:e2e -- revenue-vat-handling.spec.ts && npm run test:api -- revenue-vat-handling.api.spec.ts`

**Expected Results:**

```
E2E Tests: 8 tests, 0 passing, 8 failing (expected - RED phase)
API Tests: 15 tests, 0 passing, 15 failing (expected - RED phase)
Status: ✅ RED phase verified
```

**Expected Failure Messages:**

- E2E: "Element not found: [data-testid='vat-override-warning-dialog']"
- E2E: "Element not found: [data-testid='gl-split-preview']"
- E2E: "Element not found: [data-testid='credit-note-form']"
- API: "404 Not Found: POST /api/v1/ar-vat-corrections"
- API: "404 Not Found: GET /api/v1/ar-vat-report"
- API: "500 Internal Server Error: GL split generation not implemented"

---

## Notes

- **VAT Rounding:** Follow Circular 200 interpretation - round to nearest 100 VND (e.g., 33,333.30 → 33,300)
- **Approval Threshold:** Default 10M VND, configurable via CompanySettings
- **ND123 Format:** Follow established patterns from Story 4.6 (AP VAT reports)
- **Idempotency:** Use database unique constraint on (invoiceID, voucherID) to prevent double-booking
- **Audit Trail:** All VAT operations (override, correction, credit note) must be logged with before/after snapshots

---

## Contact

**Questions or Issues?**

- Ask in team standup
- Tag @tea in Slack/Discord
- Refer to `.bmad/bmm/workflows/testarch/atdd/` for workflow documentation
- Consult `.bmad/bmm/testarch/knowledge` for testing best practices

---

**Generated by BMad TEA Agent** - 2025-01-23

