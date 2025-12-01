# ATDD API Test Checklist: Epic 5 - Story 5.1

**Story Title:** Sales Invoice Entry, Edit, and Draft Management  
**Test Type:** API (Integration) Tests  
**Date Generated:** 2025-11-21  
**Status:** ✅ RED PHASE - All API tests failing, ready for backend development

---

## Overview

This checklist covers API-level testing for Story 5.1, focusing on:
- Invoice CRUD operations (create, read, update, delete)
- GL (General Ledger) posting validation
- VAT calculations and rounding
- Duplicate prevention
- Period-based validation
- Authorization & RBAC
- Audit logging

**Test File:** `tests/api/sales-invoice-api.spec.ts`  
**Test Count:** 17 API tests (9 P0, 5 P1, 3 P2)  
**Status:** ✅ All failing (RED phase)

---

## API Endpoints Required

### Invoice Management

**POST /api/v1/invoices** - Create invoice
```
Request Headers: Authorization: Bearer {token}
Request Body:
{
  "customerId": "string",
  "date": "2025-01-15",
  "dueDate": "2025-02-15",
  "referenceText": "optional-string",
  "lineItems": [
    {
      "description": "string",
      "quantity": 1,
      "unitPrice": 1000000,
      "vatRate": 0 | 5 | 10 | "exempt",
      "revenueAccount": "511001"
    }
  ]
}

Response (201 Created):
{
  "id": "uuid",
  "invoiceNumber": "INV-2025-001",
  "customerId": "uuid",
  "status": "Draft",
  "date": "2025-01-15",
  "dueDate": "2025-02-15",
  "currency": "VND",
  "lineItems": [...],
  "subtotal": 1000000,
  "totalVAT": 100000,
  "grandTotal": 1100000,
  "createdAt": "2025-01-15T10:00:00Z",
  "createdBy": "uuid"
}

Error (400 Bad Request): Missing required field
{
  "error": "customerId is required",
  "field": "customerId"
}

Error (409 Conflict): Duplicate invoice
{
  "error": "Duplicate invoice: customer + number + date already exists",
  "existingId": "uuid"
}
```

**GET /api/v1/invoices** - List invoices
```
Query Parameters:
  - page: 1
  - pageSize: 10
  - status: Draft|PendingApproval|Posted
  - customerId: filter by customer
  - dateFrom: 2025-01-01
  - dateTo: 2025-01-31

Response (200 OK):
{
  "data": [
    { /* invoice objects */ }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 10,
    "total": 100,
    "totalPages": 10
  }
}
```

**GET /api/v1/invoices/{id}** - Fetch single invoice
```
Response (200 OK):
{
  "id": "uuid",
  "invoiceNumber": "INV-2025-001",
  "customerId": "uuid",
  "customerName": "Test Customer",
  "status": "Draft",
  "date": "2025-01-15",
  "dueDate": "2025-02-15",
  "referenceText": "ref",
  "currency": "VND",
  "lineItems": [...],
  "subtotal": 1000000,
  "totalVAT": 100000,
  "grandTotal": 1100000
}

Error (404 Not Found):
{
  "error": "Invoice not found",
  "id": "uuid"
}
```

**PUT /api/v1/invoices/{id}** - Update draft invoice
```
Request Body: (same as POST, partial updates allowed)
{
  "referenceText": "updated ref",
  "lineItems": [...]
}

Response (200 OK): Updated invoice object

Error (400 Bad Request): Cannot update posted invoice
{
  "error": "Cannot update posted invoice",
  "status": "Posted"
}

Error (403 Forbidden): Not authorized
{
  "error": "You do not have permission to edit this invoice",
  "reason": "Only creator or admin can edit draft"
}
```

**DELETE /api/v1/invoices/{id}** - Delete draft invoice
```
Response (204 No Content)

Error (400 Bad Request): Cannot delete posted invoice
{
  "error": "Cannot delete posted invoice",
  "status": "Posted"
}
```

**POST /api/v1/invoices/{id}/submit** - Post invoice (create GL entries)
```
Response (200 OK):
{
  "id": "uuid",
  "status": "Posted",
  "postedAt": "2025-01-15T10:05:00Z",
  "postedBy": "uuid",
  "glDocumentId": "uuid"
}

Error (400 Bad Request): Invalid period
{
  "error": "Period closed",
  "details": {
    "period": "2024-12",
    "status": "Closed",
    "closedAt": "2025-01-01T00:00:00Z"
  }
}

Error (400 Bad Request): Already posted
{
  "error": "Invoice already posted"
}
```

### Bulk Operations

**POST /api/v1/invoices/import** - Bulk import CSV
```
Request: FormData
  - file: CSV file

CSV Format:
customerId,date,dueDate,referenceText,description,quantity,unitPrice,vatRate,revenueAccount
customer-1,2025-01-15,2025-02-15,INV-001,Service,1,100000,10,511001

Response (202 Accepted):
{
  "jobId": "uuid",
  "status": "processing",
  "estimatedCompletion": 5000
}

GET /api/v1/invoices/import/{jobId}:
{
  "jobId": "uuid",
  "status": "completed|processing|failed",
  "successCount": 10,
  "failureCount": 0,
  "errors": [
    {
      "row": 5,
      "error": "Invalid account: 999999"
    }
  ]
}
```

### GL & Audit

**GET /api/v1/gl/entries** - Fetch GL entries for invoice
```
Query Parameters:
  - documentId: uuid of invoice

Response (200 OK):
[
  {
    "id": "uuid",
    "accountCode": "131",
    "accountName": "Accounts Receivable",
    "debit": 1100000,
    "credit": 0,
    "documentId": "uuid",
    "documentType": "Invoice",
    "lineNumber": 1
  },
  {
    "id": "uuid",
    "accountCode": "511001",
    "accountName": "Service Revenue",
    "debit": 0,
    "credit": 1000000,
    "documentId": "uuid",
    "documentType": "Invoice",
    "lineNumber": 2
  },
  {
    "id": "uuid",
    "accountCode": "3331",
    "accountName": "VAT Output",
    "debit": 0,
    "credit": 100000,
    "documentId": "uuid",
    "documentType": "Invoice",
    "lineNumber": 3
  }
]
```

**GET /api/v1/invoices/{id}/audit** - Fetch audit log
```
Response (200 OK):
[
  {
    "id": "uuid",
    "action": "CREATED|UPDATED|POSTED|DELETED",
    "documentId": "uuid",
    "actorId": "uuid",
    "actorEmail": "accountant@example.com",
    "timestamp": "2025-01-15T10:00:00Z",
    "before": { /* previous values */ },
    "after": { /* new values */ },
    "ipAddress": "192.168.1.1",
    "deviceInfo": "Mozilla/5.0..."
  }
]
```

---

## Test Execution Map

### P0 Critical Tests (9 tests, ~18 hours)

| Test | Endpoint | Scenario | Expected Result |
|------|----------|----------|-----------------|
| AC1.1 | POST /invoices | Create valid invoice | 201, invoice object |
| AC1.2 | POST /invoices (2x) | Auto-gen sequential numbers | CUST-1-2025-001, CUST-1-2025-002 |
| AC1.3 | POST /invoices (duplicate) | Prevent duplicate | 409 Conflict |
| AC1.4 | POST /invoices (closed period) | Reject closed period date | 400 Bad Request |
| AC1.5 | POST /invoices (missing fields) | Validate required fields | 400 Bad Request |
| AC1.6 | POST /invoices (multi-VAT) | Support all VAT rates | Correct VAT totals |
| AC1.7 | PUT /invoices/{id} (draft) | Update draft | 200, updated object |
| AC1.8 | DELETE /invoices/{id} (draft) | Delete draft | 204 No Content |
| AC1.9 | POST /invoices/{id}/submit | Post invoice & GL entries | 200, GL entries created |

### P1 High Priority Tests (5 tests, ~10 hours)

| Test | Endpoint | Scenario | Expected Result |
|------|----------|----------|-----------------|
| AC2.1 | POST /invoices (VAT mismatch) | VAT validation | 400 Bad Request |
| AC2.2 | PUT /invoices/{id} (different user) | Authorization check | 403 Forbidden |
| AC2.3 | POST /invoices (parent account) | Leaf account validation | 400 Bad Request |
| AC2.4 | POST /invoices/{id}/submit (2x) | Idempotent posting | 400 Bad Request (2nd post) |
| AC2.5 | GET /invoices/{id}/audit | Audit trail logging | Audit entries with diff |
| AC2.6 | POST /invoices/{id}/submit (closed period) | Period validation on post | 400 Bad Request |

### P2 Medium Priority Tests (3 tests, ~6 hours)

| Test | Endpoint | Scenario | Expected Result |
|------|----------|----------|-----------------|
| AC3.1 | GET /invoices (filters) | List with pagination | 200, paginated list |
| AC3.2 | POST /invoices/import | Bulk import CSV | 202, job created |
| AC3.3 | GET /invoices/{id} | Fetch single | 200, invoice details |

### Precision & Balance Tests (2 bonus tests)

| Test | Focus | Validation |
|------|-------|-----------|
| Rounding | VAT edge cases | Consistent rounding rules |
| GL Balance | Dr = Cr on post | GL entries balanced |

---

## Data Factories & Fixtures

### Invoice Factory
```typescript
import { createInvoice, createInvoiceWithVATRates, createInvoicesBulk } from '../factories/invoice.factory';

// Single invoice
const invoice = createInvoice({ customerId: 'cust-1' });

// Multiple VAT rates
const multiVAT = createInvoiceWithVATRates([0, 5, 10, 'exempt']);

// Bulk (10K for load testing)
const bulk = createInvoicesBulk(10000, {
  customerIds: ['cust-1', 'cust-2', 'cust-3'],
});
```

### Customer Factory
```typescript
import { createCustomer, createCustomers } from '../factories/customer.factory';

const customer = createCustomer({ creditLimit: 10000000 });
const customers = createCustomers(100);
```

### Period Factory
```typescript
import { createOpenPeriod, createClosedPeriod, createPeriodsWithClosedHistory } from '../factories/period.factory';

const openPeriod = createOpenPeriod({ year: 2025, month: 1 });
const closedPeriod = createClosedPeriod({ year: 2024, month: 12 });
const history = createPeriodsWithClosedHistory(); // Current + 3 previous closed
```

---

## Mock/Stub Requirements

### Authentication Mock
```typescript
// Mock login endpoint to return valid JWT token
POST /api/v1/auth/login
Response: { token: "eyJhbGc..." }
```

### Customer Master Mock
```typescript
// Ensure test customers exist before invoice creation
POST /api/v1/customers
Response: { id, code, name, arAccount: "131" }
```

### Period Master Mock
```typescript
// Ensure test periods exist (mix of open/closed)
GET /api/v1/periods
Response: [
  { id: "period-2025-01", status: "Open" },
  { id: "period-2024-12", status: "Closed" }
]
```

### GL Account Master Mock
```typescript
// Ensure chart of accounts available for validation
GET /api/v1/accounts/131 → { postable: true, type: "Debit" }
GET /api/v1/accounts/511 → { postable: false, type: "Credit" } // Parent
GET /api/v1/accounts/511001 → { postable: true, type: "Credit" } // Leaf
```

---

## Implementation Tasks

### Backend Endpoints (DEV Team)

#### POST /api/v1/invoices
- [ ] Create Invoice entity in database
- [ ] Implement InvoiceService.create()
- [ ] Validate required fields
- [ ] Check for duplicates (unique constraint)
- [ ] Validate date in open period
- [ ] Calculate totals (subtotal, VAT, grand total)
- [ ] Generate invoice number (CUST-{id}-{year}-{seq})
- [ ] Return 201 with invoice object
- [ ] Log audit entry (CREATED)
- [ ] Tests pass: AC1.1, AC1.2, AC1.3, AC1.4, AC1.5, AC1.6

#### PUT /api/v1/invoices/{id}
- [ ] Implement InvoiceService.update()
- [ ] Check invoice exists
- [ ] Verify creator or admin (403 if not)
- [ ] Prevent update if posted (400 if posted)
- [ ] Recalculate totals
- [ ] Persist changes
- [ ] Log audit entry (UPDATED) with before/after
- [ ] Return 200 with updated object
- [ ] Test passes: AC1.7, AC2.2

#### DELETE /api/v1/invoices/{id}
- [ ] Implement InvoiceService.delete()
- [ ] Check invoice exists
- [ ] Prevent delete if posted (400 if posted)
- [ ] Delete from database
- [ ] Log audit entry (DELETED)
- [ ] Return 204 No Content
- [ ] Test passes: AC1.8

#### POST /api/v1/invoices/{id}/submit
- [ ] Implement InvoiceService.submit()
- [ ] Verify invoice in Draft status
- [ ] Validate date in open period (400 if closed)
- [ ] Create GL entries:
  - Dr AR (131) = subtotal + VAT
  - Cr Revenue (account from line) = subtotal per revenue account
  - Cr VAT Output (3331) = total VAT
- [ ] Verify GL balanced (Dr = Cr)
- [ ] Update invoice status to Posted
- [ ] Log audit entry (POSTED)
- [ ] Return 200 with posted invoice
- [ ] Test passes: AC1.9, AC2.4, AC2.6

#### GET /api/v1/gl/entries
- [ ] Implement GLService.getEntriesByDocument()
- [ ] Accept documentId query parameter
- [ ] Return all GL entries for invoice
- [ ] Include account code, name, debit, credit
- [ ] Test passes: AC1.9 (verification)

#### GET /api/v1/invoices/{id}/audit
- [ ] Implement AuditService.getAuditTrail()
- [ ] Return all audit entries for invoice
- [ ] Include action, actor, timestamp, before/after
- [ ] Order by timestamp ascending
- [ ] Test passes: AC2.5

#### POST /api/v1/invoices/import
- [ ] Parse CSV file
- [ ] Validate template headers
- [ ] Create invoices atomically (all or nothing)
- [ ] Return 202 with jobId
- [ ] Implement async processing
- [ ] Test passes: AC3.2

---

## Running API Tests

### Install Dependencies
```bash
pnpm install
```

### Run All API Tests (RED phase)
```bash
npx playwright test tests/api/sales-invoice-api.spec.ts
```

### Run Specific Test
```bash
npx playwright test tests/api/sales-invoice-api.spec.ts -g "should create invoice"
```

### Run in Debug Mode
```bash
npx playwright test tests/api/sales-invoice-api.spec.ts --debug
```

### View HTML Report
```bash
npx playwright show-report
```

---

## API Test Examples

### Example 1: Create Invoice
```typescript
test('should create invoice', async ({ request }) => {
  const response = await request.post(`${API_BASE}/invoices`, {
    headers: { Authorization: `Bearer ${authToken}` },
    data: {
      customerId: 'cust-1',
      date: '2025-01-15',
      dueDate: '2025-02-15',
      lineItems: [
        {
          description: 'Service',
          quantity: 1,
          unitPrice: 100000,
          vatRate: 10,
          revenueAccount: '511001',
        },
      ],
    },
  });

  expect(response.status()).toBe(201);
  const body = await response.json();
  expect(body.id).toBeTruthy();
  expect(body.status).toBe('Draft');
});
```

### Example 2: Verify GL Entries
```typescript
test('should create GL entries on post', async ({ request }) => {
  // ... create and post invoice ...

  const glResponse = await request.get(`${API_BASE}/gl/entries?documentId=${invoice.id}`, {
    headers: { Authorization: `Bearer ${authToken}` },
  });

  const entries = await glResponse.json();
  const arEntry = entries.find(e => e.accountCode === '131');
  expect(arEntry.debit).toBe(1100000); // Subtotal + VAT
});
```

### Example 3: Test Authorization
```typescript
test('should reject unauthorized edit', async ({ request }) => {
  // ... create invoice as user A ...
  // ... login as user B ...

  const response = await request.put(`${API_BASE}/invoices/${invoice.id}`, {
    headers: { Authorization: `Bearer ${otherToken}` },
    data: { referenceText: 'Hacked!' },
  });

  expect(response.status()).toBe(403);
});
```

---

## Validation Checklist

After all API tests pass, verify:

- [ ] All 17 API tests passing (green phase)
- [ ] GL entries balanced (Dr = Cr) for every posted invoice
- [ ] Audit trail complete with actor, timestamp, before/after
- [ ] VAT calculations correct for all rates (0%, 5%, 10%, exempt)
- [ ] Duplicate prevention working (409 on duplicate)
- [ ] Period validation enforced (400 if closed)
- [ ] Authorization working (403 if not creator/admin)
- [ ] Idempotency working (400 on second POST)
- [ ] Error messages clear and actionable
- [ ] Response codes match HTTP standards (201 create, 200 ok, 400 bad request, 403 forbidden, 404 not found, 409 conflict)

---

## Quality Gates

| Metric | Target | Status |
|--------|--------|--------|
| Tests passing | 100% | 🔴 0/17 (RED phase) |
| API response time | <200ms | 🟡 Pending |
| Error handling | All cases covered | 🟡 Pending |
| Authorization | All checks enforced | 🟡 Pending |
| GL balance | Every post balanced | 🟡 Pending |

---

## Document Status

✅ **RED Phase Complete**
- 17 API tests written in Given-When-Then format
- Tests fail due to missing API implementation
- Data factories ready for use
- Mock requirements documented
- Implementation tasks mapped to endpoints
- Ready for backend development

**Last Updated:** 2025-11-21  
**Next Review:** After GREEN phase (all tests passing)
