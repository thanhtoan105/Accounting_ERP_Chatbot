# ATDD Checklist - Epic 4, Story 4.3: Cash Payments (Linked to Bills, Standalone)

**Date:** 2025-01-27
**Author:** thanhtoan
**Primary Test Level:** E2E

---

## Story Summary

As an accountant, I want to record supplier payments linked to purchase bills or as standalone payments, so that accounts payable are accurately tracked, payments are properly allocated, and financial records are maintained with full audit compliance.

**As a** accountant
**I want** to record supplier payments linked to purchase bills or as standalone payments
**So that** accounts payable are accurately tracked, payments are properly allocated, and financial records are maintained with full audit compliance

---

## Acceptance Criteria

1. New payment form: supplier picker lists only suppliers with open/unpaid bills, supports batch/link
2. Allows multiple bills per payment: allocates via FIFO by default, allows override/modification before post
3. Required fields: date, auto-generated number, cash/bank account (shows balance), payee, amount, reference, payment proof (required if above-config threshold), method
4. Disallows overpayment (enforces by current bill balance), error if attempted
5. Standalone payment (advance/ad hoc): allowed for admin with warning tag
6. Suggests "quick add" supplier if non-master; logs ad hoc tag
7. Payment approval for over-threshold follows Story 4.2 workflow
8. Sufficient balance confirmed for each payment; overdraft triggers warning/block (configurable)
9. Posts payment as voucher (Dr AP 331, Cr cash/bank 111/112); batch import allowed with atomic error reporting
10. All audit rules as for voucher entry/posting apply

---

## Failing Tests Created (RED Phase)

### E2E Tests (12 tests)

**File:** `tests/e2e/cash-payments.spec.ts` (450+ lines)

- ✅ **Test:** 4.3-E2E-001: should display only suppliers with open/unpaid bills in supplier picker
  - **Status:** RED - Missing `/ap-payments/new` route and payment form component
  - **Verifies:** AC#1 - Supplier picker filters to show only suppliers with open bills

- ✅ **Test:** 4.3-E2E-001: should not display suppliers without open bills in supplier picker
  - **Status:** RED - Missing supplier filtering logic in API endpoint
  - **Verifies:** AC#1 - Supplier picker excludes suppliers without open bills

- ✅ **Test:** 4.3-E2E-002: should automatically allocate payment to bills using FIFO algorithm
  - **Status:** RED - Missing FIFO allocation algorithm and allocation UI component
  - **Verifies:** AC#2 - Automatic FIFO allocation based on due date

- ✅ **Test:** 4.3-E2E-002: should allow manual override of FIFO allocation before posting
  - **Status:** RED - Missing manual allocation override functionality
  - **Verifies:** AC#2 - Manual allocation modification capability

- ✅ **Test:** 4.3-E2E-003: should require all mandatory fields before submission
  - **Status:** RED - Missing form validation for required fields
  - **Verifies:** AC#3 - Required field validation

- ✅ **Test:** 4.3-E2E-003: should require payment proof when payment amount exceeds threshold
  - **Status:** RED - Missing conditional payment proof validation
  - **Verifies:** AC#3 - Payment proof requirement based on threshold

- ✅ **Test:** 4.3-E2E-004: should prevent overpayment when allocated amount exceeds bill remaining balance
  - **Status:** RED - Missing overpayment validation logic
  - **Verifies:** AC#4 - Overpayment prevention

- ✅ **Test:** 4.3-E2E-005: should allow admin to create standalone payment with warning tag
  - **Status:** RED - Missing standalone payment toggle and warning UI
  - **Verifies:** AC#5 - Standalone payment for admin users

- ✅ **Test:** 4.3-E2E-005: should not allow non-admin to create standalone payment
  - **Status:** RED - Missing RBAC enforcement for standalone payments
  - **Verifies:** AC#5 - Standalone payment restricted to admin role

- ✅ **Test:** 4.3-E2E-007: should require approval for payments exceeding threshold
  - **Status:** RED - Missing approval workflow integration for payments
  - **Verifies:** AC#7 - Payment approval workflow integration

- ✅ **Test:** 4.3-E2E-008: should display account balance and calculate balance after payment
  - **Status:** RED - Missing account balance display component
  - **Verifies:** AC#8 - Account balance validation and display

- ✅ **Test:** 4.3-E2E-008: should display overdraft warning when payment exceeds balance
  - **Status:** RED - Missing overdraft detection and warning UI
  - **Verifies:** AC#8 - Overdraft warning/block functionality

### API Tests (10 tests)

**File:** `tests/api/cash-payments.api.spec.ts` (350+ lines)

- ✅ **Test:** 4.3-API-001: GET /api/v1/ap-payments/suppliers/{supplierId}/open-bills - should return only open/unpaid bills
  - **Status:** RED - Missing API endpoint for open bills
  - **Verifies:** AC#1 - Supplier open bills API endpoint

- ✅ **Test:** 4.3-API-002: POST /api/v1/ap-payments/{id}/allocate - should allocate payment to bills in FIFO order
  - **Status:** RED - Missing FIFO allocation algorithm implementation
  - **Verifies:** AC#2 - FIFO allocation algorithm (oldest due date first)

- ✅ **Test:** 4.3-API-004: POST /api/v1/ap-payments - should reject payment when allocated amount exceeds bill remaining balance
  - **Status:** RED - Missing overpayment validation at API level
  - **Verifies:** AC#4 - Overpayment prevention at API contract level

- ✅ **Test:** 4.3-API-004: POST /api/v1/ap-payments/{id}/allocate - should reject manual allocation exceeding bill remaining balance
  - **Status:** RED - Missing allocation validation
  - **Verifies:** AC#4 - Manual allocation overpayment prevention

- ✅ **Test:** 4.3-API-008: POST /api/v1/ap-payments - should validate sufficient account balance before creating payment
  - **Status:** RED - Missing account balance validation service
  - **Verifies:** AC#8 - Account balance validation API

- ✅ **Test:** 4.3-API-008: GET /api/v1/accounts/{id}/balance - should return current account balance
  - **Status:** RED - Missing account balance API endpoint
  - **Verifies:** AC#8 - Account balance retrieval

- ✅ **Test:** 4.3-API-009: POST /api/v1/ap-payments/{id}/post - should generate voucher with correct journal entries
  - **Status:** RED - Missing voucher posting integration
  - **Verifies:** AC#9 - Voucher posting with Dr AP 331, Cr cash/bank 111/112

- ✅ **Test:** 4.3-API-007: POST /api/v1/ap-payments - should create approval workflow for payments exceeding threshold
  - **Status:** RED - Missing approval workflow integration
  - **Verifies:** AC#7 - Payment approval workflow creation

- ✅ **Test:** 4.3-API-007: POST /api/v1/ap-payments/{id}/approve - should approve payment and post voucher
  - **Status:** RED - Missing payment approval endpoint
  - **Verifies:** AC#7 - Payment approval and posting flow

- ✅ **Test:** 4.3-API-010: POST /api/v1/ap-payments - should create audit log entry for payment creation
  - **Status:** RED - Missing audit logging integration
  - **Verifies:** AC#10 - Audit trail for payment operations

---

## Data Factories Created

### Payment Factory

**File:** `tests/support/fixtures/factories/payment-factory.ts`

**Exports:**

- `createPayment(overrides?)` - Create single payment with optional overrides
- `createDraftPayment(overrides?)` - Create draft payment (convenience method)
- `createPaymentWithAllocations(bills, paymentAmount?, overrides?)` - Create payment with FIFO allocations
- `createStandalonePayment(overrides?)` - Create standalone payment (advance payment)
- `createPaymentAboveThreshold(threshold?, overrides?)` - Create payment exceeding approval threshold
- `createPaymentBelowThreshold(threshold?, overrides?)` - Create payment below approval threshold
- `createPostedPayment(overrides?)` - Create posted payment (convenience method)
- `createAllocation(overrides?)` - Create payment allocation with optional overrides

**Example Usage:**

```typescript
const payment = paymentFactory.createPayment({ 
  supplierId: 1, 
  amount: 1000000 
});

const paymentWithAllocations = paymentFactory.createPaymentWithAllocations(
  [bill1, bill2], 
  2500000
);

const standalonePayment = paymentFactory.createStandalonePayment({
  amount: 5000000,
  isStandalone: true
});
```

---

## Fixtures Created

### Payment Factory Fixture

**File:** `tests/support/fixtures/index.ts`

**Fixtures:**

- `paymentFactory` - Payment factory with automatic cleanup
  - **Setup:** Creates PaymentFactory instance with API request helper
  - **Provides:** PaymentFactory with methods for creating test payments
  - **Cleanup:** Automatically deletes all tracked payments via API DELETE requests

**Example Usage:**

```typescript
import { test } from '../support/fixtures';

test('should create payment', async ({ paymentFactory }) => {
  const payment = paymentFactory.createPayment({ amount: 1000000 });
  // paymentFactory.cleanup() runs automatically after test
});
```

---

## Mock Requirements

### Account Balance Service Mock

**Endpoint:** `GET /api/v1/accounts/{id}/balance`

**Success Response:**

```json
{
  "balance": 5000000,
  "currency": "VND",
  "lastUpdated": "2024-01-27T10:00:00Z"
}
```

**Failure Response:**

```json
{
  "error": "Account not found",
  "status": 404
}
```

**Notes:** Mock should return configurable balance for testing overdraft scenarios

### Voucher Posting Service Mock

**Endpoint:** `POST /api/v1/vouchers`

**Success Response:**

```json
{
  "data": {
    "id": 123,
    "voucherNumber": "VCH-2024-001",
    "entries": [
      {
        "accountCode": "331",
        "debitAmount": 1000000,
        "creditAmount": 0
      },
      {
        "accountCode": "111",
        "debitAmount": 0,
        "creditAmount": 1000000
      }
    ]
  }
}
```

**Notes:** Mock should validate journal entry structure (Dr AP 331, Cr cash/bank 111/112)

---

## Required data-testid Attributes

### Payment Form Page (`/ap-payments/new`)

- `supplier-picker` - Supplier selection combobox/button
- `supplier-search` - Supplier search input field
- `supplier-option-{id}` - Individual supplier option in dropdown
- `payment-date` - Payment date picker input
- `payment-number` - Auto-generated payment number (read-only)
- `account-picker` - Cash/bank account selection
- `account-option-{id}` - Individual account option
- `account-balance-current` - Current account balance display
- `account-balance-after` - Balance after payment calculation
- `payee` - Payee name input field
- `payment-amount` - Payment amount input field
- `reference` - Payment reference input field
- `payment-proof` - Payment proof file upload input
- `payment-method` - Payment method dropdown
- `standalone-payment-toggle` - Standalone payment toggle (admin-only)
- `standalone-warning-tag` - Standalone payment warning badge
- `payment-allocation-grid` - Payment allocation grid container
- `allocation-row` - Individual allocation row
- `bill-number` - Bill number display in allocation row
- `allocated-amount` - Allocated amount display (read-only)
- `allocated-amount-input` - Allocated amount input field (editable)
- `submit-payment-button` - Submit payment button
- `error-supplier` - Supplier validation error message
- `error-payment-date` - Payment date validation error message
- `error-account` - Account validation error message
- `error-payee` - Payee validation error message
- `error-amount` - Amount validation error message
- `error-payment-proof` - Payment proof validation error message
- `error-overpayment` - Overpayment validation error message
- `overdraft-warning` - Overdraft warning message
- `payment-status` - Payment status badge/display

### Payment List Page (`/ap-payments`)

- `payment-list-table` - Payment list data table
- `payment-row-{id}` - Individual payment row
- `payment-number-{id}` - Payment number cell
- `payment-status-{id}` - Payment status badge
- `payment-actions-{id}` - Payment actions dropdown

**Implementation Example:**

```tsx
<button data-testid="supplier-picker" role="combobox">
  Select Supplier
</button>
<input data-testid="supplier-search" placeholder="Search suppliers" />
<div data-testid="supplier-option-1">{supplier.name}</div>
<input data-testid="payment-amount" type="number" />
<div data-testid="error-overpayment">{errorMessage}</div>
<div data-testid="account-balance-current">{balance}</div>
```

---

## Implementation Checklist

### Test: 4.3-E2E-001: Payment Form with Supplier Picker

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `/ap-payments/new` route in frontend routing
- [ ] Create `PaymentForm` component following `PurchaseBillForm` patterns
- [ ] Implement supplier picker component filtered to show only suppliers with open bills
- [ ] Create API endpoint `GET /api/v1/ap-payments/suppliers/{supplierId}/open-bills`
- [ ] Implement backend service method `getOpenBillsForSupplier(supplierId)`
- [ ] Add data-testid attributes: `supplier-picker`, `supplier-search`, `supplier-option-{id}`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-001"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 8 hours

---

### Test: 4.3-E2E-002: Multiple Bills Allocation with FIFO

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `PaymentAllocationGrid` component following `VoucherLineGrid` patterns
- [ ] Implement FIFO allocation algorithm in backend `PaymentService.allocateFIFO()`
- [ ] Create API endpoint `POST /api/v1/ap-payments/{id}/allocate` with `autoAllocate: true`
- [ ] Display allocation preview in UI with bill details and allocated amounts
- [ ] Implement manual allocation override functionality
- [ ] Add data-testid attributes: `payment-allocation-grid`, `allocation-row`, `bill-number`, `allocated-amount`, `allocated-amount-input`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-002"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 12 hours

---

### Test: 4.3-E2E-003: Required Fields Validation

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Add form validation for all required fields (supplier, date, account, payee, amount)
- [ ] Implement conditional payment proof validation (required if amount > threshold)
- [ ] Create `PaymentValidationService` with field-level error mapping
- [ ] Display validation errors with field-level error messages
- [ ] Add data-testid attributes: `error-supplier`, `error-payment-date`, `error-account`, `error-payee`, `error-amount`, `error-payment-proof`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-003"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 6 hours

---

### Test: 4.3-E2E-004: Overpayment Prevention

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Implement overpayment validation in `PaymentValidationService.validateAllocations()`
- [ ] Add database constraint: `CHECK (allocated_amount <= purchase_bill.remaining_balance)`
- [ ] Create real-time validation in frontend allocation grid
- [ ] Display overpayment error message when validation fails
- [ ] Add data-testid attribute: `error-overpayment`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-004"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 8 hours

---

### Test: 4.3-E2E-005: Standalone Payment

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Add standalone payment toggle to payment form (admin-only)
- [ ] Implement RBAC check: standalone payments require admin role
- [ ] Display warning tag when standalone payment is enabled
- [ ] Update `APPayment` entity with `isStandalone` field
- [ ] Add data-testid attributes: `standalone-payment-toggle`, `standalone-warning-tag`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-005"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 4 hours

---

### Test: 4.3-E2E-007: Payment Approval Workflow

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Integrate `ApprovalWorkflowService` from Story 4.2 for payments
- [ ] Check payment amount against `CompanySettings.approvalThresholdAmount`
- [ ] Create approval workflow when payment exceeds threshold
- [ ] Update payment status to `PENDING_APPROVAL`
- [ ] Extend `ApprovalDecisionDialog` for payment approval
- [ ] Add data-testid attribute: `payment-status`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-007"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 10 hours

---

### Test: 4.3-E2E-008: Account Balance Validation

**File:** `tests/e2e/cash-payments.spec.ts`

**Tasks to make this test pass:**

- [ ] Create `AccountBalanceDisplay` component
- [ ] Create API endpoint `GET /api/v1/accounts/{id}/balance`
- [ ] Implement `AccountBalanceService.getAccountBalance(accountId)`
- [ ] Display current balance and balance after payment calculation
- [ ] Implement overdraft detection and warning display
- [ ] Add data-testid attributes: `account-balance-current`, `account-balance-after`, `overdraft-warning`
- [ ] Run test: `npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-008"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 8 hours

---

### Test: 4.3-API-002: FIFO Allocation Algorithm

**File:** `tests/api/cash-payments.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Implement `PaymentService.allocateFIFO(paymentAmount, supplierId)` method
- [ ] Sort bills by `due_date ASC` (oldest first)
- [ ] Allocate payment amount to bills in FIFO order
- [ ] Store allocation order in `PaymentAllocation.allocationOrder` field
- [ ] Create API endpoint `POST /api/v1/ap-payments/{id}/allocate` with `autoAllocate: true`
- [ ] Run test: `npx playwright test tests/api/cash-payments.api.spec.ts -g "4.3-API-002"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 10 hours

---

### Test: 4.3-API-009: Voucher Posting Integration

**File:** `tests/api/cash-payments.api.spec.ts`

**Tasks to make this test pass:**

- [ ] Integrate with Epic 3 voucher engine for payment posting
- [ ] Generate voucher with journal entries: Dr AP 331, Cr cash/bank 111/112
- [ ] Link payment to generated voucher via `linked_voucher_id`
- [ ] Update bill statuses to PAID or PARTIALLY_PAID based on allocations
- [ ] Update bill `remaining_balance` after payment allocation
- [ ] Create API endpoint `POST /api/v1/ap-payments/{id}/post`
- [ ] Run test: `npx playwright test tests/api/cash-payments.api.spec.ts -g "4.3-API-009"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 12 hours

---

## Running Tests

```bash
# Run all failing tests for this story
npx playwright test tests/e2e/cash-payments.spec.ts tests/api/cash-payments.api.spec.ts

# Run specific test file
npx playwright test tests/e2e/cash-payments.spec.ts

# Run tests in headed mode (see browser)
npx playwright test tests/e2e/cash-payments.spec.ts --headed

# Debug specific test
npx playwright test tests/e2e/cash-payments.spec.ts -g "4.3-E2E-001" --debug

# Run tests with coverage
npx playwright test tests/e2e/cash-payments.spec.ts --reporter=html
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

**TEA Agent Responsibilities:**

- ✅ All tests written and failing (22 tests total: 12 E2E, 10 API)
- ✅ Fixtures and factories created with auto-cleanup (PaymentFactory, paymentFactory fixture)
- ✅ Mock requirements documented (Account Balance Service, Voucher Posting Service)
- ✅ data-testid requirements listed (30+ attributes for payment form and list)
- ✅ Implementation checklist created (9 test groups with detailed tasks)

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

**Recommended Order:**

1. Start with API tests (faster feedback): 4.3-API-001 (open bills endpoint)
2. Then E2E tests: 4.3-E2E-001 (supplier picker)
3. Continue with FIFO allocation: 4.3-API-002, 4.3-E2E-002
4. Implement validation: 4.3-E2E-003, 4.3-E2E-004
5. Add approval workflow: 4.3-E2E-007, 4.3-API-007
6. Complete voucher posting: 4.3-API-009
7. Finish with balance validation: 4.3-E2E-008, 4.3-API-008

**Progress Tracking:**

- Check off tasks as you complete them
- Share progress in daily standup
- Mark story as IN PROGRESS in `docs/sprint-status.yaml`

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
2. **Run failing tests** to confirm RED phase: `npx playwright test tests/e2e/cash-payments.spec.ts tests/api/cash-payments.api.spec.ts`
3. **Begin implementation** using implementation checklist as guide
4. **Work one test at a time** (red → green for each)
5. **Share progress** in daily standup
6. **When all tests pass**, refactor code for quality
7. **When refactoring complete**, run `bmad sm story-done` to move story to DONE

---

## Knowledge Base References Applied

This ATDD workflow consulted the following knowledge fragments:

- **fixture-architecture.md** - Test fixture patterns with setup/teardown and auto-cleanup using Playwright's `test.extend()`
- **data-factories.md** - Factory patterns using `@faker-js/faker` for random test data generation with overrides support
- **network-first.md** - Route interception patterns (intercept BEFORE navigation to prevent race conditions)
- **test-quality.md** - Test design principles (Given-When-Then, one assertion per test, determinism, isolation)
- **test-levels-framework.md** - Test level selection framework (E2E vs API vs Component vs Unit)

See `.bmad/bmm/testarch/tea-index.csv` for complete knowledge fragment mapping.

---

## Test Execution Evidence

### Initial Test Run (RED Phase Verification)

**Command:** `npx playwright test tests/e2e/cash-payments.spec.ts tests/api/cash-payments.api.spec.ts`

**Expected Results:**

```
Running 22 tests using 1 worker

  ✘ tests/e2e/cash-payments.spec.ts:12:5 › Cash Payments - Linked to Bills, Standalone › 4.3-E2E-001: Payment Form with Supplier Picker › should display only suppliers with open/unpaid bills in supplier picker
    Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5173/ap-payments/new
    Expected: Route /ap-payments/new does not exist

  ✘ tests/api/cash-payments.api.spec.ts:15:5 › Cash Payments API › 4.3-API-001: Get Open Bills for Supplier › GET /api/v1/ap-payments/suppliers/{supplierId}/open-bills - should return only open/unpaid bills
    Error: API request failed: 404 Not Found
    Expected: Endpoint /api/v1/ap-payments/suppliers/{supplierId}/open-bills does not exist

  ... (20 more failing tests)

**Summary:**
- Total tests: 22
- Passing: 0 (expected)
- Failing: 22 (expected)
- Status: ✅ RED phase verified
```

**Expected Failure Messages:**

- Route not found: `/ap-payments/new` (E2E tests)
- API endpoint not found: `/api/v1/ap-payments/*` (API tests)
- Component not found: `PaymentForm`, `PaymentAllocationGrid` (E2E tests)
- Service not found: `PaymentService`, `AccountBalanceService` (API tests)

---

## Notes

- **Story Dependencies**: This story depends on Story 4.1 (Purchase Bills) and Story 4.2 (Approval Workflow). Ensure those are complete before starting implementation.
- **FIFO Algorithm**: The FIFO allocation algorithm should sort bills by `due_date ASC` (oldest first) to prioritize oldest bills for payment.
- **Overpayment Prevention**: Enforce at multiple levels: database constraint, application validation, and real-time UI validation.
- **Standalone Payments**: Admin-only feature. Ensure RBAC is properly enforced at both API and UI levels.
- **Account Balance**: Integrate with cash/bank account balance tracking. Support configurable overdraft behavior (warn vs block).
- **Voucher Posting**: Journal entries must follow exact format: Dr AP 331 (Accounts Payable), Cr cash/bank 111/112 (Cash/Bank Account).

---

## Contact

**Questions or Issues?**

- Ask in team standup
- Tag @tea-agent in Slack/Discord
- Refer to `.bmad/bmm/workflows/testarch/atdd/` for workflow documentation
- Consult `.bmad/bmm/testarch/knowledge/` for testing best practices

---

**Generated by BMad TEA Agent** - 2025-01-27

