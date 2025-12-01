# ATDD Checklist: Epic 5 - Story 5.1

**Story Title:** Sales Invoice Entry, Edit, and Draft Management  
**Epic:** 5 – Accounts Receivable (AR) Module  
**Date Generated:** 2025-11-21  
**Status:** ✅ RED PHASE - All tests failing, ready for development

---

## Story Summary

As an accountant, I want to create and edit sales invoices with strict validations and attachments, so that revenue recognition and receivables are accurate and auditable.

**Acceptance Criteria:**
1. Invoice form: Customer picker, auto-generated number, date (open period), due date, reference text, VND currency
2. Line items: description, quantity/unit price (positive), VAT%, revenue account (leaf), validation
3. Totals auto-calculated; inline validation
4. Save as draft anytime; autosave with undo/redo; creator/admin only can edit/delete
5. Attachments: drag/drop, preview, delete (drafts only)
6. Duplicate prevention: same customer + invoice number + date
7. Import (CSV/Excel): atomic, template-validated, error mapping
8. Audit log: all create/edit/post/import/delete with diff, actor, device/IP

---

## Test Files Created

### E2E Tests
- **File:** `tests/e2e/sales-invoice-entry.spec.ts`
- **Framework:** Playwright
- **Test Count:** 18 tests (9 P0 critical, 5 P1 high, 4 P2 medium)
- **Status:** ✅ All tests in RED phase (failing)

**P0 Tests:**
1. ✅ Create invoice with all required fields (happy path)
2. ✅ Auto-generate invoice number per customer/period
3. ✅ Prevent duplicate invoice (same customer + inv# + date)
4. ✅ Reject date in closed period
5. ✅ Validate required fields on save
6. ✅ Support VAT rate selection (0%, 5%, 10%, exempt)
7. ✅ Handle file attachments (drag/drop, preview, delete)
8. ✅ Support autosave with undo/redo
9. ✅ Support CSV/Excel import with atomicity

**P1 Tests:**
10. ✅ Restrict draft editing to creator/admin only
11. ✅ Support draft deletion with confirmation
12. ✅ Validate revenue account is leaf account only
13. ✅ Enforce mandatory dimension validation
14. ✅ Auto-calculate totals and validate

**P2 Tests:**
15. ✅ Search invoices by number, customer, date range
16. ✅ Export invoice to PDF
17. ✅ Show audit log with all changes
18. ✅ Validate attachment file type/size

### API Tests
*To be created in next phase - API-level testing of GL posting, VAT calculations, audit trails*

### Component Tests
*To be created in next phase - Form validation, dropdown filtering, calculation logic*

---

## Data Factories Created

### Invoice Factory
- **File:** `tests/support/factories/invoice.factory.ts`
- **Exports:**
  - `createInvoice(overrides)` - Single invoice with optional overrides
  - `createInvoices(count, overrides)` - Multiple invoices
  - `createInvoiceWithVATRates(rates, overrides)` - Invoices with specific VAT rates (for VAT testing)
  - `createInvoiceForCustomer(customerId, customerName, overrides)` - Customer-specific invoices
  - `createInvoicesBulk(count, options)` - Bulk invoices for load testing (10K+ invoices)

**Example Usage:**
```typescript
import { createInvoice, createInvoiceWithVATRates } from './factories/invoice.factory';

const invoice = createInvoice({ customerId: 'CUST-001' });
const invoicesWithVAT = createInvoiceWithVATRates([0, 5, 10, 'exempt']);
```

### Customer Factory
*To be created - For seeding test customers*

### Period Factory
*To be created - For setting up open/closed accounting periods*

---

## Fixtures Created

### Authentication Fixture
- **File:** `tests/support/fixtures/auth.fixture.ts`
- **Features:**
  - `authenticatedUser` - Auto-logs in as accountant before test
  - `authenticate(role)` - Manually authenticate as specific role (accountant, chief_accountant, cfo, admin)
  - Auto-cleanup: Logs out after test
  - Token verification in localStorage

**Example Usage:**
```typescript
import { test } from '../support/fixtures/auth.fixture';

test('should create invoice as accountant', async ({ authenticatedUser }) => {
  // User is already logged in
  await page.goto('/invoices/new');
});

test('should reject edit as non-creator', async ({ authenticate, page }) => {
  await authenticate('chief_accountant');
  // Now logged in as chief accountant
});
```

### Test Data Fixture
*To be created - Seeding customers, periods, GL accounts for each test*

### Network Mocking Fixture
*To be created - Route interception for API responses*

---

## Mock Requirements for DEV Team

### Invoice API Endpoints

**POST /api/v1/invoices** - Create invoice
```json
Request: {
  "customerId": "uuid",
  "date": "2025-01-15",
  "dueDate": "2025-02-15",
  "lineItems": [
    {
      "description": "Service",
      "quantity": 1,
      "unitPrice": 100000,
      "vatRate": 10,
      "revenueAccount": "511001"
    }
  ]
}

Response (201 Created): {
  "id": "uuid",
  "invoiceNumber": "INV-2025-001",
  "status": "Draft",
  "customerId": "uuid",
  "date": "2025-01-15",
  "totalAmount": 110000,
  "createdAt": "2025-01-15T10:00:00Z"
}
```

**GET /api/v1/invoices/{id}** - Fetch invoice
```json
Response (200 OK): {
  "id": "uuid",
  "invoiceNumber": "INV-2025-001",
  "customerId": "uuid",
  "customerName": "Test Customer",
  "date": "2025-01-15",
  "dueDate": "2025-02-15",
  "status": "Draft",
  "lineItems": [...],
  "subtotal": 100000,
  "totalVAT": 10000,
  "grandTotal": 110000
}
```

**PUT /api/v1/invoices/{id}** - Update invoice draft
```json
Response (200 OK): { /* Same as GET */ }
Error (403 Forbidden): {
  "error": "You do not have permission to edit this invoice"
}
Error (409 Conflict): {
  "error": "Duplicate invoice: customer + number + date already exists"
}
```

**DELETE /api/v1/invoices/{id}** - Delete draft invoice
```json
Response (204 No Content)
Error (400 Bad Request): {
  "error": "Cannot delete posted invoice"
}
```

**POST /api/v1/invoices/import** - Bulk import CSV
```json
Request: FormData with CSV file
Response (202 Accepted): {
  "jobId": "uuid",
  "status": "processing",
  "estimatedCompletion": 5000 // milliseconds
}

GET /api/v1/invoices/import/{jobId}
Response: {
  "status": "completed",
  "successCount": 10,
  "failureCount": 0,
  "errors": []
}
```

### Validation Errors

**Period Closed (400 Bad Request)**
```json
{
  "error": "Period closed",
  "details": {
    "period": "2024-12",
    "status": "Closed",
    "closedAt": "2025-01-01T00:00:00Z"
  }
}
```

**Duplicate Invoice (409 Conflict)**
```json
{
  "error": "Duplicate invoice",
  "existingId": "uuid",
  "details": {
    "customerId": "uuid",
    "invoiceNumber": "INV-2025-001",
    "date": "2025-01-15"
  }
}
```

**VAT Mismatch (400 Bad Request)**
```json
{
  "error": "VAT calculation mismatch",
  "details": {
    "headerVAT": 15000,
    "calculatedVAT": 10000,
    "difference": 5000
  }
}
```

---

## Required data-testid Attributes

### Invoice Form Page

**Customer Section:**
- `customer-picker` - Customer selection dropdown
- `customer-search` - Customer search input
- `customer-option-0`, `customer-option-1` - Customer options

**Date Section:**
- `invoice-date` - Invoice date input
- `due-date` - Due date input (default +30 days)
- `date-2024-12-15` - Calendar date button (for closed period testing)

**Line Items Section:**
- `add-line-item` - Add line item button
- `line-description` - Line description input
- `line-description-1`, `line-description-2` - Multiple line descriptions
- `line-quantity` - Line quantity input
- `line-quantity-1` - Multiple line quantities
- `line-unit-price` - Line unit price input
- `line-unit-price-1` - Multiple line unit prices
- `line-vat-rate` - VAT rate select
- `line-vat-rate-1` - Multiple line VAT rates
- `line-revenue-account` - Revenue account picker
- `account-search` - Account search input
- `account-option-parent` - Parent account option

**Totals Section:**
- `subtotal` - Subtotal display
- `total-vat` - Total VAT display
- `grand-total` - Grand total display

**Action Buttons:**
- `save-draft-button` - Save as draft button
- `delete-invoice-button` - Delete invoice button
- `undo-button` - Undo changes button
- `redo-button` - Redo changes button

**Attachment Section:**
- `attachment-drop-zone` - Drag/drop zone for files
- `file-input` - File input element
- `attachment-item` - Attachment list item
- `attachment-preview-button` - Preview attachment button
- `attachment-delete-button` - Delete attachment button

**Feedback Elements:**
- `success-message` - Success notification
- `error-message` - Error message container
- `error-customer-required` - Customer validation error
- `error-date-required` - Date validation error
- `error-line-items-required` - Line items validation error
- `error-cost-center-required` - Cost center validation error
- `error-file-size` - File size validation error
- `error-file-type` - File type validation error
- `account-warning` - Account warning message
- `autosave-status` - Autosave status indicator

**Form Container:**
- `invoice-form` - Main form container
- `invoice-status` - Status badge
- `invoice-id` - Invoice ID display
- `invoice-number` - Invoice number display

### Invoice List Page
- `invoice-row` - Invoice table row
- `invoice-row-0`, `invoice-row-1` - Specific row indices
- `search-input` - Search/filter input
- `search-button` - Search button
- `export-pdf-button` - Export to PDF button
- `audit-log-button` - Open audit log button

### Audit Log
- `audit-entry` - Audit entry row
- `audit-entry-0` - Specific audit entry

### Import Page
- `import-file-input` - File input for import
- `map-columns-button` - Map CSV columns button
- `confirm-mapping-button` - Confirm mapping button
- `import-complete-message` - Import completion message

### Delete Confirmation Dialog
- `delete-confirmation-dialog` - Confirmation modal
- `delete-confirmation-message` - Confirmation message
- `confirm-delete-button` - Confirm delete button

### User Menu
- `user-menu-button` - User menu button
- `logout-button` - Logout button

---

## Implementation Checklist

### Story 5.1: Sales Invoice Entry

#### Test AC1.1: Create invoice with all required fields
- [ ] Create POST `/api/v1/invoices` endpoint
- [ ] Implement `/invoices/new` page with form component
- [ ] Add customer picker with typeahead search
- [ ] Add date picker (restrict to open periods)
- [ ] Add due date picker (default +30 days)
- [ ] Add reference text input
- [ ] Implement line items section with add/remove
- [ ] Implement VAT rate selection
- [ ] Auto-calculate subtotal, VAT, grand total
- [ ] Add save draft button and API integration
- [ ] Display success message on save
- [ ] Show invoice status badge
- [ ] Generate and display invoice ID/number
- [ ] Add all required `data-testid` attributes
- [ ] Run test: `npm run test:e2e -- sales-invoice-entry.spec.ts`
- [ ] ✅ Test passes (green phase)

#### Test AC1.2: Auto-generate invoice number
- [ ] Implement invoice number generation logic (pattern: CUST-{id}-{year}-{seq})
- [ ] Store generation state in database (sequence per customer per year)
- [ ] Verify number format in response
- [ ] Add number display in form
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.3: Prevent duplicate invoice
- [ ] Add unique database constraint: (customer_id, invoice_number, period_id)
- [ ] Implement duplicate check on POST endpoint
- [ ] Return 409 Conflict with "already exists" message
- [ ] Display error in UI
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.4: Reject date in closed period
- [ ] Fetch open/closed period list in form
- [ ] Disable closed period dates in date picker
- [ ] Validate date in open period on API
- [ ] Return 400 Bad Request if closed
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.5: Validate required fields
- [ ] Implement form validation for required fields
- [ ] Show inline error messages
- [ ] Disable submit button when validation fails
- [ ] Add validation indicators with `data-testid`
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.6: Support VAT rate selection
- [ ] Implement VAT rate dropdown (0%, 5%, 10%, exempt)
- [ ] Add default VAT rate setting
- [ ] Calculate line VAT on amount change
- [ ] Sum line VATs for total VAT
- [ ] Validate VAT calculations
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.7: Handle file attachments
- [ ] Implement drag-and-drop file upload zone
- [ ] Add file preview capability
- [ ] Implement file delete for drafts
- [ ] Add file size validation (max 10MB)
- [ ] Add file type validation (PDF, images, Office)
- [ ] Store attachments in draft invoice
- [ ] Clean up attachments on draft deletion
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.8: Support autosave with undo/redo
- [ ] Implement autosave on 2s timeout after change
- [ ] Add autosave status indicator
- [ ] Implement undo/redo using state management
- [ ] Show undo/redo buttons
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC1.9: Support CSV/Excel import
- [ ] Create import endpoint: POST `/api/v1/invoices/import`
- [ ] Implement CSV/Excel parser
- [ ] Validate template headers
- [ ] Implement atomic import (all or nothing)
- [ ] Generate error mapping for failed rows
- [ ] Add import file input and mapping UI
- [ ] Show import completion with counts
- [ ] Run test
- [ ] ✅ Test passes

#### Test AC2.1-AC2.6: P1 tests (High Priority)
- [ ] Implement creator/admin permission check
- [ ] Implement draft delete with confirmation modal
- [ ] Validate revenue account is leaf (not parent)
- [ ] Enforce mandatory dimensions per account
- [ ] Auto-calculate grand total
- [ ] Validate file upload constraints
- [ ] Run all P1 tests
- [ ] ✅ All P1 tests pass

#### Test AC2.7-AC2.10: P2 tests (Medium Priority)
- [ ] Implement invoice search/filter by number, customer, date range
- [ ] Implement PDF export
- [ ] Implement audit log display
- [ ] Run all P2 tests
- [ ] ✅ All P2 tests pass

---

## Red-Green-Refactor Workflow

### RED Phase ✅ COMPLETE
- ✅ All 18 tests written and failing
- ✅ Data factories created
- ✅ Fixtures created
- ✅ Mock requirements documented
- ✅ data-testid attributes listed
- ✅ Tests run locally and fail with correct messages

**Failing Tests Output Example:**
```
FAIL tests/e2e/sales-invoice-entry.spec.ts (9 failed)
  Story 5.1: Sales Invoice Entry
    P0: Critical Tests
      1) Create invoice with all required fields - 404 GET /invoices/new
      2) Auto-generate invoice number - timeout waiting for invoice-form
      3) Prevent duplicate invoice - 400 POST /api/v1/invoices
      ...
```

### GREEN Phase (DEV Team Responsibility)
1. ✅ Start with test AC1.1 (Create invoice happy path)
2. ✅ Implement minimal code to pass test
   - Create `/invoices/new` route
   - Build invoice form component
   - Add customer picker
   - Implement POST `/api/v1/invoices` endpoint
3. ✅ Run test locally: `npm run test:e2e -- sales-invoice-entry.spec.ts`
4. ✅ Confirm test passes (green)
5. ✅ Move to next test (AC1.2 auto-generation)
6. ✅ Repeat until all tests pass

**Estimated Timeline:**
- AC1.1-1.5 (core form): ~16 hours
- AC1.6-1.8 (advanced features): ~12 hours
- AC1.9 (import): ~8 hours
- AC2.1-2.6 (P1 features): ~12 hours
- AC2.7-2.10 (P2 features): ~8 hours
- **Total: ~56 hours (~7 days)**

### REFACTOR Phase (After All Tests Green)
1. Code quality review
   - Extract form components
   - Remove duplication in validation logic
   - Optimize API queries
2. Performance optimization
   - Lazy-load customer list
   - Batch import optimization
3. Refactor test code if needed
   - Extract common test setup
   - Create reusable test helpers
4. Ensure all tests still pass during refactoring

---

## Running the Tests

### Install Dependencies
```bash
cd /home/thanhtoan/code/accounting
pnpm install
```

### Run All ATDD Tests (RED phase)
```bash
npm run test:e2e -- sales-invoice-entry.spec.ts
```

### Run Specific Test
```bash
npm run test:e2e -- sales-invoice-entry.spec.ts -g "Create invoice with all required fields"
```

### Run in Headed Mode (See Browser)
```bash
npm run test:e2e -- sales-invoice-entry.spec.ts --headed
```

### Debug Specific Test
```bash
npm run test:e2e -- sales-invoice-entry.spec.ts --debug
```

### Generate HTML Report
```bash
npm run test:e2e -- sales-invoice-entry.spec.ts
npx playwright show-report
```

---

## Knowledge Base References Applied

### Fixture Architecture
- Pure function factories (`invoice.factory.ts`)
- Fixture composition with auto-cleanup (`auth.fixture.ts`)
- User context management
- Reference: `fixture-architecture.md`

### Data Factories
- Faker.js for random test data
- Override pattern for specific scenarios
- Factory composition (VAT rates, bulk creation)
- Reference: `data-factories.md`

### Network-First Testing
- Route interception before navigation (planned for API test phase)
- HAR capture for reproducible scenarios
- Reference: `network-first.md`

### Component TDD
- Given-When-Then structure for all tests
- Atomic tests with single assertion
- form validation testing
- Reference: `component-tdd.md`

### Test Quality
- Deterministic tests (no hardcoded waits)
- Explicit selectors (data-testid)
- Cleanup on fixture teardown
- Reference: `test-quality.md`

---

## Next Steps for Implementation

### Immediate (This Sprint)
1. ✅ ATDD tests created (RED phase)
2. ✅ Factories and fixtures ready
3. ✅ DEV team: Implement AC1.1-1.5 (core form)
4. ✅ Run smoke tests daily

### This Week
- Implement AC1.6-1.9 (advanced features + import)
- All P0 tests passing (green phase)

### Next Week
- Implement AC2.1-2.10 (P1 + P2 features)
- All tests passing (green phase)
- Begin refactor phase

### Before Release
- Code review and refactoring
- Performance optimization for 10K invoice scenarios
- Integration testing with other AR workflows (Payment Receipts, Aging)

---

## Quality Metrics

| Metric | Target | Status |
|--------|--------|--------|
| Tests passing | 100% | 🔴 0/18 (RED phase) |
| Code coverage | ≥80% | 🟡 Pending |
| Performance (form load) | <2s | 🟡 Pending |
| Accessibility | WCAG 2.1 AA | 🟡 Pending |
| i18n (Vietnamese) | All labels/messages | 🟡 Pending |

---

## Document Status

✅ **RED Phase Complete**
- All 18 tests written in Given-When-Then format
- Tests fail due to missing implementation (not test bugs)
- Factories and fixtures ready for use
- Mock requirements documented
- data-testid attributes specified
- Implementation checklist created
- Ready for development team to begin GREEN phase

**Last Updated:** 2025-11-21  
**Next Review:** After GREEN phase (all tests passing)
