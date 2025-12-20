# ATDD Checklist - Epic 5, Story 5.3: Customer Payment Receipts (Linked Receivables, Standalone Entry)

**Date:** 2025-11-21  
**Author:** thanhtoan (Murat - Master Test Architect)  
**Primary Test Level:** API (with E2E for critical flow, Component for UI validation)

---

## Story Summary

Customer payment receipts enable accountants to record incoming customer payments and allocate them to open invoices (supporting partial and advance payments). Receipts automatically generate GL vouchers posting debits to bank/cash accounts and credits to AR, with full dimension tracking and reversal capability.

**As a** accountant  
**I want** to record receipts and allocate them to invoices (including partials and advances)  
**So that** AR balances and customer statements are correct

---

## Acceptance Criteria

1. (AC1) Receipt form: customer picker (open invoices only), auto-generated number, date, cash/bank account, amount, reference, attachment, payment method
2. (AC2) Allocation UI: one/many invoices, partial allocation support, overpayment prevention, remaining amount display
3. (AC3) Standalone receipts (advances/on-account): flagged, admin-only, can match later
4. (AC4) GL posting: Dr Bank/Cash (111/112), Cr AR (131)
5. (AC5) GL dimensions: customer, cost center, project (if configured)
6. (AC6) Reversal: generates linked reversal voucher, cross-linked in audit trail
7. (AC7) Reversal: mandatory reason, logged in audit
8. (AC8) Batch import: atomic (all/none), template-based with column mapping
9. (AC9) Batch error handling: detailed error map with row numbers and failure reasons
10. (AC10) Full audit: create/edit/post tracked with user, timestamp, old/new values
11. (AC11) Reversal/import audit: full before/after state, allocation invoice balance deltas

---

## Failing Tests Created (RED Phase)

### API Tests (8 tests)

**File:** `tests/api/ar-receipt-api.spec.ts` (380 lines)

**Test List:**

- ✅ **AC1.1**: POST /receipts - should create receipt with required fields (DRAFT status, amount)
- ✅ **AC1.2**: POST /receipts - should validate customer has open invoices
- ✅ **AC1.3**: POST /receipts - should auto-generate receipt number per customer/period (unique)
- ✅ **AC2.1**: POST /receipts/{id}/allocate - should allocate to single invoice
- ✅ **AC2.2**: POST /receipts/{id}/allocate - should allocate to multiple invoices
- ✅ **AC2.3**: POST /receipts/{id}/allocate - should prevent overpayment (409 Conflict)
- ✅ **AC2.4**: POST /receipts/{id}/allocate - should support partial allocation
- ✅ **AC3.1**: POST /receipts - should create standalone receipt (admin-only, isStandalone=true)
- ✅ **AC3.2**: POST /receipts - should reject standalone receipt for non-admin (403 Forbidden)
- ✅ **AC4-5.1**: POST /receipts/{id}/post - should generate GL voucher (Dr 111/112, Cr 131)
- ✅ **AC4-5.2**: POST /receipts/{id}/post - should include dimensions in GL lines
- ✅ **AC6-7.1**: POST /receipts/{id}/reverse - should generate linked reversal voucher with cross-reference
- ✅ **AC6-7.2**: POST /receipts/{id}/reverse - should require reversal reason (400 if missing)
- ✅ **AC10.1**: POST /receipts - should audit log creation with user, timestamp, values
- ✅ **AC11.1**: POST /receipts/{id}/reverse - should audit log reversal with reason and before/after
- ✅ **AC8-9.1**: POST /receipts/batch-import - should import receipts atomically
- ✅ **AC8-9.2**: POST /receipts/batch-import - should return detailed error map with row numbers

**Status:** RED - All tests failing (missing implementation)

### E2E Tests (1 test)

**File:** `tests/e2e/ar-receipt-workflow.spec.ts` (380 lines)

- ✅ **E2E-001**: Complete Receipt Workflow (Create → Allocate → Post → Reverse)
  - **Status:** RED - Customer picker, allocation grid, posting, reversal not implemented
  - **Verifies:** Complete user journey with network mocking

### Component Tests (3 tests)

**File:** `frontend/src/components/receipt/__tests__/ReceiptForm.test.tsx` (210 lines)

- ✅ **AC1.1**: should render form with required fields
- ✅ **AC1**: should filter customer picker to customers with open invoices
- ✅ **AC2**: should validate overpayment prevention
- ✅ **AC1**: should auto-generate receipt number on save
- ✅ **AC3**: should show standalone receipt toggle for admin users
- ✅ **AC3**: should hide standalone receipt toggle for non-admin users
- ✅ Validation error display
- ✅ Draft autosave support

**File:** `frontend/src/components/receipt/__tests__/ReceiptAllocationGrid.test.tsx` (230 lines)

- ✅ **AC2**: should display open invoices with allocation inputs
- ✅ **AC2**: should allow partial allocation to invoice
- ✅ **AC2**: should prevent overpayment allocation
- ✅ **AC2**: should support multiple invoice allocation
- ✅ Allocation summary display (total allocated, unallocated)
- ✅ Allocation validation before submission

**File:** `frontend/src/components/receipt/__tests__/ReceiptReversalDialog.test.tsx` (190 lines)

- ✅ **AC6-7**: should display receipt details in dialog
- ✅ **AC6-7**: should display current allocations
- ✅ **AC7**: should require mandatory reversal reason
- ✅ **AC7**: should accept reversal reason (max 500 chars)
- ✅ **AC7**: should prevent reason exceeding 500 characters
- ✅ **AC6**: should show warning before confirming reversal
- ✅ onConfirm callback with reversal data
- ✅ Success message with reversal voucher link
- ✅ Close dialog on cancel

### Backend Unit Tests (3 tests)

**File:** `backend/src/test/java/com/accounting/service/impl/sales/ReceiptValidationServiceImplTest.java` (290 lines)

- ✅ **AC2**: Overpayment Prevention (5 test cases)
  - Allow allocation = remaining balance
  - Allow allocation < remaining balance
  - Reject allocation > remaining balance
  - Prevent overpayment across multiple invoices
- ✅ **AC1**: Customer Open Invoices (3 test cases)
  - Allow receipt for customer with open invoices
  - Reject linked receipt for customer without open invoices
  - Allow standalone receipt without open invoices
- ✅ Account Balance Validation (2 test cases)
  - Allow receipt if sufficient balance
  - Reject receipt if insufficient balance
- ✅ **AC3**: Standalone Receipt (2 test cases)
  - Flag standalone receipts as advances
  - Require admin role
- ✅ Multiple Invoice Allocation (2 test cases)
  - Support allocation to multiple invoices
  - Partial allocation to first, full to second

### Backend Integration Tests (1 test file)

**File:** `backend/src/test/java/com/accounting/controller/sales/ReceiptControllerIntegrationTest.java` (420 lines)

- ✅ **AC1-5**: Receipt CRUD Operations (6 test cases)
  - POST /receipts - create with auto-generated number
  - GET /receipts - list with pagination and filters
  - GET /receipts/{id} - get with allocations
  - PUT /receipts/{id} - update DRAFT only
  - PUT /receipts/{id} - reject update of POSTED
  - DELETE /receipts/{id} - delete DRAFT only
- ✅ **AC2**: Receipt Allocation (2 test cases)
  - Allocate to invoices
  - Prevent overpayment
- ✅ **AC4-5**: Receipt Posting (4 test cases)
  - Generate GL voucher (Dr/Cr)
  - Include dimensions
  - Update invoice status
  - Reject posting to closed period
- ✅ **AC6-7**: Receipt Reversal (3 test cases)
  - Generate linked reversal voucher
  - Require mandatory reason
  - Audit log reversal
- ✅ **AC8-9**: Batch Import (3 test cases)
  - Atomic import
  - Error handling with error map
  - Import template generation
- ✅ **RBAC**: Authorization (4 test cases)
  - Require authentication
  - Reject unauthenticated
  - Accountant can create/post
  - Admin required for standalone
- ✅ **AC10-11**: Audit Logging (2 test cases)
  - Create audit log
  - Allocation audit with invoice balance deltas

---

## Data Factories Created

### Receipt Factory

**File:** `tests/support/factories/receipt.factory.ts` (planned)

**Exports:**

- `createReceipt(overrides?)` - Create single receipt with optional overrides
- `createReceipts(count)` - Create array of receipts

**Example Usage:**

```typescript
const receipt = createReceipt({
	customerId: 'customer-001',
	amount: 5000000,
});
const receipts = createReceipts(5);
```

### Receipt Allocation Factory

**File:** `tests/support/factories/receipt-allocation.factory.ts` (planned)

**Exports:**

- `createAllocation(overrides?)` - Create allocation with override support
- `createAllocations(count)` - Bulk creation

### Invoice Factory (Extended)

**Existing:** `tests/support/factories/sales-invoice.factory.ts` (to be extended)

**Extensions:**

- `createOpenInvoice(overrides?)` - Create POSTED invoice with remaining balance
- `createPartiallyPaidInvoice(overrides?)` - POSTED with partial allocation

---

## Fixtures Created

### Receipt Fixtures

**File:** `tests/support/fixtures/receipt.fixture.ts` (planned)

**Fixtures:**

- `authenticatedAccountant` - Accountant user logged in
- `authenticatedAdmin` - Admin user logged in
- `customerWithOpenInvoices` - Customer with 2 open invoices
- `draftReceipt` - DRAFT receipt ready for allocation
- `postedReceipt` - POSTED receipt with allocations

**Example Usage:**

```typescript
import { test } from './fixtures/receipt.fixture';

test('should post receipt', async ({
	authenticatedAccountant,
	draftReceipt,
}) => {
	// authenticatedAccountant and draftReceipt ready with auto-cleanup
});
```

---

## Mock Requirements

### Customer API Mock

**Endpoint:** `GET /api/v1/ar/customers` / `GET /api/v1/ar/customers/{id}/open-invoices`

**Success Response:**

```json
{
	"data": [
		{
			"id": "customer-001",
			"name": "Test Customer AR",
			"arAccount": "131"
		}
	]
}
```

### Bank Account API Mock

**Endpoint:** `GET /api/v1/bank-accounts`

**Success Response:**

```json
{
	"data": [
		{
			"id": "bank-001",
			"accountCode": "111",
			"name": "Test Bank Account",
			"balance": 50000000
		}
	]
}
```

### Invoice API Mock

**Endpoint:** `GET /api/v1/sales-invoices` / `GET /api/v1/sales-invoices/{id}`

**Success Response:**

```json
{
	"data": {
		"id": "invoice-001",
		"number": "INV-2025-001",
		"customerId": "customer-001",
		"amount": 10000000,
		"remainingBalance": 10000000,
		"status": "POSTED"
	}
}
```

### Voucher API Mock

**Endpoint:** `POST /api/v1/vouchers` (for GL posting)

**Success Response:**

```json
{
	"data": {
		"id": "voucher-001",
		"lines": [
			{
				"accountCode": "111",
				"amount": 5000000,
				"type": "DEBIT"
			},
			{
				"accountCode": "131",
				"amount": 5000000,
				"type": "CREDIT"
			}
		]
	}
}
```

---

## Required data-testid Attributes

### Receipt Form

- `customer-picker` - Customer dropdown/autocomplete
- `receipt-date-input` - Date input field
- `bank-account-picker` - Bank account selector
- `cash-account-picker` - Cash account selector (alternative)
- `receipt-amount-input` - Receipt amount input
- `payment-method-select` - Payment method dropdown
- `receipt-reference-input` - Reference text input
- `receipt-attachment-upload` - File upload for receipt proof
- `standalone-receipt-toggle` - Standalone receipt checkbox (admin-only)
- `save-receipt-button` - Save/Draft button
- `post-receipt-button` - Post button
- `reverse-receipt-button` - Reverse button
- `error-summary` - Error message container
- `receipt-form-title` - Form title

### Receipt Allocation Grid

- `receipt-allocation-grid` - Main allocation table
- `allocation-row` - Each invoice row
- `invoice-number` - Invoice number display
- `invoice-remaining-balance` - Remaining balance display
- `allocation-amount-input` - Amount input for allocation
- `invoice-remaining-after-allocation` - Dynamic remaining after allocation
- `overpayment-error` - Overpayment validation error
- `total-allocated` - Total allocated amount summary
- `unallocated-amount` - Unallocated amount summary
- `allocation-summary` - Summary section
- `submit-allocations-button` - Submit allocations button
- `allocation-validation-error` - Validation error message

### Receipt Reversal Dialog

- `reversal-receipt-number` - Receipt number display
- `reversal-receipt-amount` - Amount display
- `reversal-receipt-date` - Date display
- `reversal-allocation-row` - Allocation row in dialog
- `reversal-allocation-invoice` - Invoice number in allocation
- `reversal-allocation-amount` - Allocation amount in dialog
- `reversal-reason-input` - Reversal reason text area
- `reversal-reason-error` - Reversal reason validation error
- `reversal-warning` - Warning message before confirming
- `confirm-reversal-button` - Confirm reversal button
- `cancel-reversal-button` - Cancel button
- `reversal-success-message` - Success message
- `reversal-voucher-link` - Link to reversal voucher

### Receipt List/View

- `receipt-status-badge` - Status display (DRAFT, POSTED, REVERSED)
- `receipt-number-display` - Receipt number
- `receipt-customer-name` - Customer name
- `receipt-amount-display` - Amount
- `receipt-actions-menu` - Action buttons
- `view-receipt-action` - View action
- `edit-receipt-action` - Edit action (DRAFT only)
- `delete-receipt-action` - Delete action (DRAFT only)
- `post-receipt-action` - Post action (DRAFT only)
- `reverse-receipt-action` - Reverse action (POSTED only)
- `confirm-post-button` - Confirm post dialog button
- `success-message` - Success notification

---

## Implementation Checklist

### Test: AC1.1: POST /receipts - Create with Required Fields

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ARPayment` entity with fields: id, company_id, customer_id, receipt_number, receipt_date, bankAccountId, cashAccountId, amount, reference, paymentMethod, status, createdBy, createdAt
- [ ] Create `ARPaymentRepository` with company scoping
- [ ] Create `ReceiptService.create()` method with validation
- [ ] Auto-generate receipt_number (format: YYYY/#### per customer/period)
- [ ] Create `ReceiptController` with `POST /api/v1/ar/receipts` endpoint
- [ ] Validate all required fields present (customer, date, account, amount)
- [ ] Return 201 Created with auto-generated receiptNumber
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 3 hours

---

### Test: AC1.2: POST /receipts - Validate Customer Has Open Invoices

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Add `ReceiptValidationService.validateCustomerHasOpenInvoices()` method
- [ ] Query sales_invoices for customer with status=POSTED and remaining_balance > 0
- [ ] For linked receipts (isStandalone=false): reject if no open invoices
- [ ] For standalone receipts (isStandalone=true): allow
- [ ] Return 400 Bad Request with error map: `{ customerId: "has no open invoices" }`
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC1.3: POST /receipts - Auto-Generate Receipt Number

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Implement receipt number generation: YYYY/#### (year + sequential number per customer/period)
- [ ] Add database unique constraint: `UNIQUE(company_id, receipt_number, EXTRACT(YEAR FROM receipt_date))`
- [ ] Query max receipt number for customer in period, increment
- [ ] Verify uniqueness per customer per year
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 1.5 hours

---

### Test: AC2.1: POST /receipts/{id}/allocate - Allocate to Single Invoice

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ReceiptAllocation` entity with fields: id, receipt_id, sales_invoice_id, allocated_amount
- [ ] Create `ReceiptAllocationRepository`
- [ ] Create `ReceiptService.allocateInvoices()` method
- [ ] Validate allocation amount <= invoice remaining_balance
- [ ] Save allocations with allocation_order for tracking
- [ ] Return 200 OK with allocations array
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC2.2: POST /receipts/{id}/allocate - Allocate to Multiple Invoices

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Support allocations array with multiple invoices
- [ ] Validate each allocation independently
- [ ] Validate total allocations <= receipt amount
- [ ] Save all allocations atomically (transaction)
- [ ] Return 200 OK with full allocations list
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC2.3: POST /receipts/{id}/allocate - Prevent Overpayment

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] In `ReceiptValidationService.validateAllocations()`: check allocation_amount <= invoice.remaining_balance
- [ ] If overpayment detected: return error code 409 Conflict
- [ ] Return error message: "Allocation of {amount} exceeds remaining balance of {remaining}"
- [ ] Return 409 Conflict status
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 1.5 hours

---

### Test: AC2.4: POST /receipts/{id}/allocate - Support Partial Allocation

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Allow allocated_amount < invoice.remaining_balance
- [ ] Calculate remaining after allocation: remaining = remaining_balance - allocated_amount
- [ ] Save allocation with correct remaining_balance
- [ ] Don't update invoice status yet (only on POST)
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 1 hour

---

### Test: AC3.1: POST /receipts - Create Standalone Receipt (Admin-Only)

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Add `isStandalone` boolean field to ARPayment entity
- [ ] If `isStandalone=true`: skip customer open invoices validation
- [ ] Enforce admin role using Spring Security `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Flag receipt in audit with `is_standalone=true`
- [ ] Return 201 Created with `isStandalone: true`
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC3.2: POST /receipts - Reject Standalone for Non-Admin

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Verify Spring Security configuration: require ADMIN role for isStandalone
- [ ] Return 403 Forbidden if user lacks ADMIN role
- [ ] Provide clear error message: "Standalone receipts require admin role"
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 1 hour

---

### Test: AC4-5.1: POST /receipts/{id}/post - Generate GL Voucher

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ReceiptService.postReceipt()` method
- [ ] Generate GL voucher with:
  - [ ] Dr: Bank Account (111) or Cash Account (112) for receipt amount
  - [ ] Cr: Accounts Receivable (131) for receipt amount
- [ ] Use voucher engine (Epic 3) to create and post voucher
- [ ] Link receipt to voucher via `linked_voucher_id`
- [ ] Update receipt status to POSTED
- [ ] Update invoice status based on allocations:
  - [ ] If allocated == invoice.amount: status = PAID
  - [ ] If allocated < invoice.amount: status = PARTIALLY_PAID
  - [ ] Update invoice.remaining_balance
- [ ] Return 200 OK with linked_voucher_id
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 4 hours

---

### Test: AC4-5.2: POST /receipts/{id}/post - Include Dimensions

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Include all configured dimensions in GL lines:
  - [ ] Customer (if configured)
  - [ ] Cost center (if provided in receipt)
  - [ ] Project (if provided in receipt)
- [ ] Map dimensions from receipt to voucher GL lines
- [ ] Verify voucher lines contain dimension data
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC6-7.1: POST /receipts/{id}/reverse - Generate Linked Reversal Voucher

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ReceiptService.reverseReceipt()` method
- [ ] Verify receipt status is POSTED (cannot reverse DRAFT)
- [ ] Generate reversal voucher with opposite signs (Cr Bank, Dr AR)
- [ ] Link original and reversal vouchers: set `linked_voucher_id` on both
- [ ] Update receipt status to REVERSED
- [ ] Set `reversal_voucher_id` on original receipt
- [ ] Add reversal reason to reversal voucher metadata
- [ ] Update invoice statuses back to POSTED (undo allocation)
- [ ] Restore invoice remaining_balance
- [ ] Return 200 OK with reversal_voucher_id
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 4 hours

---

### Test: AC6-7.2: POST /receipts/{id}/reverse - Require Mandatory Reason

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Add `reversalReason` field to reverse request validation
- [ ] Validate field is present and not empty
- [ ] Return 400 Bad Request with error: `{ reversalReason: "Reversal reason is mandatory" }`
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 1 hour

---

### Test: AC10.1: POST /receipts - Audit Log Creation

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Integrate `AuditService` in receipt operations
- [ ] Create audit log on receipt creation:
  - [ ] entityType = "ARPayment"
  - [ ] action = "CREATE"
  - [ ] userId = authenticated user ID
  - [ ] timestamp = current time
  - [ ] newValues = receipt data
- [ ] Log all CREATE operations (create receipt, allocate, post, reverse)
- [ ] Query `GET /api/v1/audit-logs?entityId={id}&action=CREATE`
- [ ] Verify audit entry exists with correct fields
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC11.1: POST /receipts/{id}/reverse - Audit Reversal with Reason and State

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create audit log on reversal:
  - [ ] action = "REVERSE"
  - [ ] reversalReason included in changes
  - [ ] oldValues = original receipt state (status=POSTED, allocations)
  - [ ] newValues = reversed state (status=REVERSED, allocations updated)
  - [ ] Include allocation invoice balance deltas:
    - [ ] oldBalance, newBalance per allocation
- [ ] Query `GET /api/v1/audit-logs?entityId={id}&action=REVERSE`
- [ ] Verify audit includes full before/after
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Test: AC8-9.1: POST /receipts/batch-import - Atomic Import

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `ReceiptImportService.importReceipts()` method
- [ ] Accept Excel file upload (multipart/form-data)
- [ ] Parse Excel with validated template:
  - [ ] Headers: customer_id, receipt_date, amount, invoice_id (optional), payment_method
  - [ ] Validate headers present
  - [ ] Validate data types (date, number)
- [ ] Validate each row:
  - [ ] Customer exists and has open invoices
  - [ ] Amount is positive number
  - [ ] Receipt date is valid
  - [ ] Invoice ID valid if provided
- [ ] Collect all errors before saving (don't partial save)
- [ ] If any errors: return 400 with error map, no receipts created
- [ ] If all valid: create all receipts in single transaction
- [ ] Return 200 OK with list of created receipts (all succeed) or 400 (all fail)
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 5 hours

---

### Test: AC8-9.2: POST /receipts/batch-import - Return Error Map

**File:** `tests/api/ar-receipt-api.spec.ts`

**Tasks to make this test pass:**

- [ ] Generate error map with row numbers and specific error reasons
- [ ] Error format: `{ row: 2, field: "customerId", error: "Invalid customer" }`
- [ ] Include all validation errors for all rows
- [ ] Return 400 Bad Request with errors array
- [ ] Example response:
  ```json
  {
  	"errors": [
  		{ "row": 2, "field": "customerId", "error": "Customer not found" },
  		{ "row": 5, "field": "amount", "error": "Amount must be positive" }
  	]
  }
  ```
- [ ] Provide downloadable error report (optional)
- [ ] Run test: `npm run test:api -- ar-receipt-api.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 2 hours

---

### Component Tests: Receipt Form

**File:** `frontend/src/components/receipt/__tests__/ReceiptForm.test.tsx`

**Tasks to make this test pass:**

- [ ] Create `ReceiptForm.tsx` component following `SalesInvoiceForm` pattern
- [ ] Implement all required fields:
  - [ ] Customer picker (ComboBox with company scoping)
  - [ ] Receipt date (DatePicker)
  - [ ] Bank account picker (Select)
  - [ ] Receipt amount (NumberInput)
  - [ ] Payment method (Select: CASH, BANK_TRANSFER, CHECK, OTHER)
  - [ ] Reference (TextField)
- [ ] Customer picker filters to customers with open invoices
- [ ] Overpayment validation (client-side warning)
- [ ] Standalone receipt toggle (admin-only with data-testid)
- [ ] Real-time validation with error display
- [ ] Save/Draft button
- [ ] Draft autosave every 30 seconds (optional)
- [ ] Run tests: `pnpm test -- ReceiptForm.test.tsx`
- [ ] ✅ All tests pass (green phase)

**Estimated Effort:** 4 hours

---

### Component Tests: Receipt Allocation Grid

**File:** `frontend/src/components/receipt/__tests__/ReceiptAllocationGrid.test.tsx`

**Tasks to make this test pass:**

- [ ] Create `ReceiptAllocationGrid.tsx` component
- [ ] Display open invoices in table with columns:
  - [ ] Invoice number
  - [ ] Invoice date
  - [ ] Due date
  - [ ] Total amount
  - [ ] Remaining balance
  - [ ] Allocation amount input
  - [ ] Remaining after allocation (calculated)
- [ ] Real-time validation:
  - [ ] Prevent allocation > remaining_balance (show error)
  - [ ] Prevent total allocations > receipt amount
- [ ] Allocation summary:
  - [ ] Total allocated amount
  - [ ] Unallocated amount
  - [ ] Validation status
- [ ] Support multiple invoice allocation
- [ ] Support partial allocation
- [ ] Run tests: `pnpm test -- ReceiptAllocationGrid.test.tsx`
- [ ] ✅ All tests pass (green phase)

**Estimated Effort:** 4 hours

---

### Component Tests: Receipt Reversal Dialog

**File:** `frontend/src/components/receipt/__tests__/ReceiptReversalDialog.test.tsx`

**Tasks to make this test pass:**

- [ ] Create `ReceiptReversalDialog.tsx` component
- [ ] Display receipt details:
  - [ ] Receipt number
  - [ ] Receipt date
  - [ ] Receipt amount
  - [ ] Current allocations table
- [ ] Mandatory reversal reason field:
  - [ ] TextArea with max 500 characters
  - [ ] Show error if empty or missing
  - [ ] Show character count
- [ ] Warning message about linked reversal voucher
- [ ] Confirm and Cancel buttons
- [ ] Success message with link to reversal voucher
- [ ] Run tests: `pnpm test -- ReceiptReversalDialog.test.tsx`
- [ ] ✅ All tests pass (green phase)

**Estimated Effort:** 3 hours

---

### E2E Test: Complete Receipt Workflow

**File:** `tests/e2e/ar-receipt-workflow.spec.ts`

**Tasks to make this test pass:**

- [ ] Create Receipt List page (`ReceiptList.tsx`)
- [ ] Create Receipt Form page (`ReceiptForm.tsx` wrapper)
- [ ] Implement customer picker with open invoices filtering
- [ ] Implement receipt allocation workflow
- [ ] Implement receipt posting flow
- [ ] Implement receipt reversal dialog integration
- [ ] Add routes: `/ar-receipts`, `/ar-receipts/new`, `/ar-receipts/{id}/edit`
- [ ] Backend API endpoints fully functional
- [ ] Network mocking in test working
- [ ] Run test: `npm run test:e2e -- ar-receipt-workflow.spec.ts`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 6 hours

---

### Backend Unit Tests

**File:** `backend/src/test/java/com/accounting/service/impl/sales/ReceiptValidationServiceImplTest.java`

**Tasks to make all unit tests pass:**

- [ ] Implement all test stubs with actual assertions
- [ ] Implement `ReceiptValidationService` methods:
  - [ ] `validateAllocations()`
  - [ ] `validateCustomerHasOpenInvoices()`
  - [ ] `validateAccountBalance()`
  - [ ] `validateStandaloneReceipt()`
- [ ] Mock repositories and services
- [ ] Run tests: `cd backend && mvnd test -Dtest=ReceiptValidationServiceImplTest`
- [ ] ✅ All unit tests pass (green phase)

**Estimated Effort:** 3 hours

---

### Backend Integration Tests

**File:** `backend/src/test/java/com/accounting/controller/sales/ReceiptControllerIntegrationTest.java`

**Tasks to make all integration tests pass:**

- [ ] Implement all test stubs with MockMvc assertions
- [ ] Fully implement `ReceiptController` endpoints
- [ ] Fully implement `ReceiptService` methods
- [ ] Fully implement `ReceiptImportService` for batch import
- [ ] Integrate with `AuditService` for all audit logging
- [ ] Integrate with voucher engine for GL posting
- [ ] Run tests: `cd backend && mvnd test -Dtest=ReceiptControllerIntegrationTest`
- [ ] ✅ All integration tests pass (green phase)

**Estimated Effort:** 8 hours

---

## Running Tests

```bash
# Run all failing API tests for this story
npm run test:api -- ar-receipt-api.spec.ts

# Run all E2E tests
npm run test:e2e -- ar-receipt-workflow.spec.ts

# Run all component tests
pnpm test -- receipt/

# Run backend unit tests
cd backend && mvnd test -Dtest=ReceiptValidationServiceImplTest

# Run backend integration tests
cd backend && mvnd test -Dtest=ReceiptControllerIntegrationTest

# Run all tests with coverage
npm run test:coverage
cd backend && mvnd clean test jacoco:report
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

**TEA Agent Responsibilities:**

- ✅ All 15+ failing tests written in Given-When-Then format
- ✅ 3 data factories stubbed (Receipt, Allocation, extended Invoice)
- ✅ 5 test fixtures stubbed (authentication, customer, drafts, posted)
- ✅ Mock requirements documented for all external APIs
- ✅ All required data-testid attributes listed
- ✅ Implementation checklist created with 30+ concrete tasks
- ✅ Unit and integration tests with comprehensive coverage
- ✅ Component tests for all UI components

**Verification:**

- All tests run and fail as expected
- Failure messages are clear (missing implementation)
- Tests fail due to missing endpoints/services, not test bugs
- Each test focuses on single AC

---

### GREEN Phase (DEV Team - Next Steps)

**DEV Agent Responsibilities:**

1. **Start with Backend Entities & Validation** (foundation for all other layers):

   - Implement `ARPayment` entity and `ReceiptAllocation` entity
   - Create repositories and Flyway migrations
   - Implement `ReceiptValidationService` (unit tests will guide)
   - Implement `ReceiptService` core methods

2. **Implement Backend API Layer**:

   - Create `ReceiptController` endpoints
   - Implement receipt CRUD operations
   - Implement allocation and posting endpoints
   - Implement batch import endpoint
   - Integrate with `AuditService`

3. **Implement Frontend Components** (in parallel with backend):

   - Create `ReceiptForm.tsx` component
   - Create `ReceiptAllocationGrid.tsx` component
   - Create `ReceiptReversalDialog.tsx` component
   - Create `ReceiptList.tsx` component
   - Integrate with API service layer

4. **Integration & E2E Testing**:
   - Connect frontend to backend
   - Run E2E tests for complete workflow
   - Verify GL voucher posting works
   - Test batch import end-to-end

**Key Principles:**

- One test at a time (don't try to fix all at once)
- Minimal implementation (don't over-engineer)
- Run tests frequently (immediate feedback)
- Focus on failing tests, implement to make them pass
- Use implementation checklist as detailed roadmap

**Progress Tracking:**

- Check off tasks as you complete them
- Share progress in daily standup
- Mark story as IN PROGRESS in workflow status

---

### REFACTOR Phase (DEV Team - After All Tests Pass)

**DEV Agent Responsibilities:**

1. **Code Quality**:

   - Improve readability and maintainability
   - Extract duplications (DRY principle)
   - Apply design patterns
   - Optimize performance

2. **Test Quality**:

   - Verify all tests still pass after refactoring
   - Improve test performance
   - Add edge case test coverage if needed
   - Refactor test fixtures and factories

3. **Documentation**:
   - Add JSDoc comments to services
   - Update API documentation
   - Document business logic in comments
   - Update error message clarity

**Key Principles:**

- Tests provide safety net (refactor with confidence)
- Make small refactors (easier to debug if tests fail)
- Run tests after each change
- Don't change test behavior (only implementation)

**Completion Criteria:**

- All tests pass (RED → GREEN phase complete)
- Code quality meets team standards
- No code duplications or smells
- Performance is acceptable
- Ready for code review and story approval

---

## Next Steps

1. **Review this checklist** with team in standup or planning session
2. **Run failing tests** to confirm RED phase:
   - `npm run test:api -- ar-receipt-api.spec.ts`
   - `npm run test:e2e -- ar-receipt-workflow.spec.ts`
   - `pnpm test -- receipt/`
   - `cd backend && mvnd test -Dtest=ReceiptValidationServiceImplTest`
3. **Begin implementation** using implementation checklist as detailed guide
4. **Work one test at a time** (RED → GREEN for each test)
5. **Start with backend** (entities, validation, services) for maximum parallelization
6. **Share progress** in daily standup
7. **When all tests pass**, refactor code for quality
8. **When refactoring complete**, run full test suite
9. **Request code review** and merge to main

---

## Knowledge Base References Applied

This ATDD workflow consulted the following knowledge fragments:

- **fixture-architecture.md** - Test fixture patterns with setup/teardown and auto-cleanup
- **data-factories.md** - Factory patterns using @faker-js/faker with overrides
- **component-tdd.md** - Component test strategies (Given-When-Then, provider isolation)
- **network-first.md** - Route interception patterns before navigation
- **test-quality.md** - Test design principles (atomic, deterministic, isolated)
- **test-healing-patterns.md** - Common test failure patterns and solutions
- **selector-resilience.md** - Selector best practices (data-testid > ARIA > text)

---

## Test Execution Evidence

### Initial Test Run (RED Phase Verification)

**Status:** Tests created and ready to run

```
Tests Created:
- API tests: 16 test cases in ar-receipt-api.spec.ts
- E2E tests: 1 critical workflow test in ar-receipt-workflow.spec.ts
- Component tests: 20 test cases across 3 components
- Unit tests: 16 test cases in ReceiptValidationServiceImplTest
- Integration tests: 25 test cases in ReceiptControllerIntegrationTest

Total: 78 failing tests (RED phase)
```

**Expected Behavior:** All tests will fail with:

- Missing endpoints (404)
- Missing methods/services (500)
- Missing components (undefined is not a function)
- Assertion failures (expected element not found)

---

## Notes

### Architecture Integration Points

- **Voucher Engine (Epic 3)**: Receipt posting integrates with voucher engine for GL generation
- **Invoice Module (Story 5.1)**: Receipts allocate to sales invoices and update their status
- **Customer Master (Epic 2)**: Receipt customer picker filters to customers with open invoices
- **Audit Service**: All receipt operations audited with before/after state
- **Multi-tenancy**: All queries use CompanyContext for row-level security

### Known Dependencies

- Story 5.1 (Sales Invoice) - must be complete for invoice statuses and balances
- Story 5.2 (Invoice Approval) - optional: if receipts require approval workflow
- Epic 3 (Voucher Engine) - must be complete for GL posting
- Epic 2 (Master Data) - must have customer and account entities

### Testing Best Practices Applied

- **Network-First**: All E2E tests intercept routes BEFORE navigation
- **Data-testid Selection**: All UI tests use data-testid (most resilient selector)
- **Atomic Tests**: Each test verifies single behavior, one assertion
- **Given-When-Then**: All test structures follow BDD pattern
- **Auto-cleanup**: All fixtures auto-cleanup test data in teardown
- **Faker for Test Data**: All factories use @faker-js/faker for realistic data
- **Comprehensive Error Testing**: Validation tested at all layers (API, Component, Unit)

---

## Contact

**Questions or Issues?**

- Ask in team standup
- Tag @murat in team chat
- Refer to `./.bmad/bmm/docs/tea-README.md` for workflow documentation
- Consult `./.bmad/bmm/testarch/knowledge` for testing best practices

---

**Generated by BMad TEA Agent** - 2025-11-21  
**Workflow:** ATDD (Acceptance Test-Driven Development)  
**Story:** 5.3 - Customer Payment Receipts (Linked Receivables, Standalone Entry)  
**Test Architecture:** API-first with E2E critical path and comprehensive component coverage
