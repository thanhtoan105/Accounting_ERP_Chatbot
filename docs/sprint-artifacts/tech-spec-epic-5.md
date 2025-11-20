# Epic Technical Specification: Sales Invoice Entry & AR Management

Date: 2025-11-20
Author: thanhtoan
Epic ID: epic-5
Status: Draft

---

## Overview

Epic 5 delivers the complete Accounts Receivable (AR) module for an enterprise accounting ERP system, enabling sales invoice creation, approval workflows, customer payment receipts, AR aging analytics, and comprehensive audit compliance. This epic implements five core functional requirements (FR22–FR26) aligned with Circular 200/2014/TT-BTC (Vietnamese accounting standard) and builds on the foundation of Epic 3 (Voucher Engine) and Epic 2 (Master Data). The module integrates strict validation (leaf-only accounts, mandatory dimensions, double-entry equilibrium), maker-checker approval workflows, and immutable audit trails to ensure revenue recognition accuracy, cash flow visibility, and regulatory defensibility. Primary users are accountants and chief accountants managing customer relationships, invoice lifecycles, and AR reminders.

Epic 5 is strategically positioned as a parallel track to Epic 4 (AP Module) after Epic 3 completion, sharing identical patterns for document workflows, approval logic, and audit mechanisms. The AR module differentiates from AP through customer-centric reconciliation (statements, aging by customer), revenue VAT handling (output VAT vs. input VAT), and customer communication features (invoice delivery, statement distribution).

---

## Objectives and Scope

### In-Scope

- **FR23 (Sales Invoice Creation):** Full invoice entry form with customer selection, line items (qty, price, VAT rate), auto-calculated totals, draft/posted statuses, import (CSV/Excel), and duplicate prevention.
- **FR26 (Maker-Checker for AR):** Threshold-based approval workflow (default 100M VND) with separate maker/checker roles, approver UI with change history, rejection with reason routing, and audit shadow records.
- **FR24 (Customer Payment Receipts):** Receipt entry with customer/bank/cash account selection, invoice allocation (single/multi/partial), standalone advances, reversal with cross-linking, import, and audit.
- **FR25 (AR Aging Report):** Multi-bucket aging (Current, 1–30d, 31–60d, 61–90d, 91+d), drill-down to invoice lists, Excel/PDF export with snapshot metadata, overdue dashboard tiles, and automated reminders (configurable cadence).
- **AR Statement & Reconciliation:** Customer statement generation (summary/detailed views), PDF/Excel export, delivery tracking, customer-provided reconciliation import with dispute logging, and statement history.
- **Revenue & VAT Handling:** Per-line VAT rate selection (0/5/10/exempt), GL splits (Dr AR / Cr Revenue / Cr Output VAT 3331), validation rules (header VAT = sum of line VAT), credit notes/negative invoices, output VAT ND123 export.
- **Audit & Compliance:** Complete audit trail for all AR actions (create/edit/post/approve/reverse/import) with diffs, actor metadata, device/IP, and event hashing; 10-year retention; GDPR purge process.
- **Integration with Core Systems:** Multi-tenant row-level security (company_id filtering), RBAC enforcement (Admin/Accountant/Chief/CFO), API-level authorization, and idempotent posting via unique constraint enforcement.

### Out-of-Scope

- **Advanced AR features:** Collection management workflows, third-party payment gateway integration (Stripe/Paypal), automated reminders via SMS, credit limit enforcement, and discount approval.
- **Customer Portal:** Self-service invoice view, payment submission, and dispute handling via public web interface (deferred to post-MVP).
- **Multi-currency Support:** Single VND currency per Circular 200; multi-currency is post-MVP.
- **Analytics & Forecasting:** AR-specific predictive analytics and cash flow forecasting (addressed in Epic 8 – BI Dashboard).
- **Intercompany Transactions:** Consolidated AR across legal entities; single-company focus for MVP.

---

## System Architecture Alignment

Epic 5 aligns with the established Spring Boot 3.5.7 + PostgreSQL + React/TypeScript Vite stack as defined in the Architecture Document. Key alignment points:

1. **Multi-Tenant Row-Level Security (RLS):** All AR entities (`sales_invoices`, `ar_receipts`, `ar_aging_cache`) inherit `CompanyScopedEntity` base class with automatic `company_id` filtering at JPA repository and Supabase RLS policy levels.

2. **JWT Authentication & RBAC:** Spring Security filters enforce role-based permissions:
   - **Admin/Chief Accountant:** Full CRUD on invoices, approvals, reversals, and audit log export.
   - **Accountant:** Create/view invoices, view receipts, cannot approve above threshold.
   - **CFO:** View-only AR Aging, dashboards, and reports.

3. **Data Model Inheritance:** Entities follow the established pattern:
   - `SalesInvoice extends CompanyScopedEntity` with `InvoiceStatus` enum (DRAFT, PENDING_APPROVAL, POSTED, REJECTED, PAID, PARTIALLY_PAID).
   - `ARReceipt extends CompanyScopedEntity` with allocation relationship to `SalesInvoice`.
   - `ARAllocation` (join table) tracks partial/multi-invoice allocations with reversibility.

4. **API Contract Compliance:** RESTful `/api/v1/sales-invoices`, `/api/v1/ar-receipts`, `/api/v1/ar-aging` endpoints following the standard response wrapper (`{ data, meta, error }`) with pagination, filtering, and error codes (e.g., `VALIDATION_ERROR`, `PERIOD_CLOSED`, `INSUFFICIENT_PERMISSION`).

5. **Audit Trail Integration:** Every AR action logs to a centralized `AuditLog` table (append-only, no deletes) with before/after diffs, actor metadata, device/IP capture, and HMAC-SHA256 event hashing for integrity verification.

6. **Voucher Engine Integration:** Sales invoices automatically post journal entries via the `VoucherService`, generating double-entry vouchers (Dr Account 131 / Cr Account 5xx) with dimension validation, leaf-only account enforcement, and period closure checks inherited from Epic 3.

7. **Report Cache Strategy:** AR Aging is materialized to a `ARAgingCache` table refreshed via scheduled job (trigger on invoice post/receipt post) or manual invalidation, enabling sub-second dashboard queries compliant with NFR1 (page load <2s).

8. **UI Component Leverage:** Frontend uses existing shadcn/ui foundations (Button, Dialog, Table, Form) and custom components (VoucherLineGrid for line items, AccountPicker for GL accounts, MoneyInput for currency formatting per vi-VN locale, DataTablePro for list pagination/filtering).

---

## Detailed Design

### Services and Modules

| Service / Module | Responsibility | Key Classes | Dependencies |
|------------------|-----------------|-------------|--------------|
| **SalesInvoiceService** | Create, edit, post, approve, reject, reverse invoices; draft/posted state transitions; duplicate detection | `SalesInvoiceService`, `SalesInvoiceValidator`, `InvoiceApprovalService` | VoucherService, AuditService, CustomerService, AccountService |
| **ARReceiptService** | Record customer payments, allocate to invoices, handle partials/advances, post receipt vouchers, reverse | `ARReceiptService`, `ARAllocationService`, `ReceiptValidator` | VoucherService, AuditService, SalesInvoiceService |
| **ARAgingService** | Query invoices by aging bucket, compute aging cache, generate reports, handle drill-down | `ARAgingService`, `ARAgingQueryBuilder`, `ARAgingCacheRefresher` | ReportService, CacheManager (Redis) |
| **ARStatementService** | Generate customer statements (summary/detailed), handle PDF/Excel export, track distribution, parse customer reconciliation imports | `ARStatementService`, `StatementExportService`, `StatementImportService` | ARReceiptService, ReportService |
| **ARVATService** | VAT rate validation per line, GL split computation (Dr AR / Cr Revenue / Cr Output VAT), header/detail VAT reconciliation, ND123 export | `ARVATService`, `VATCalculator`, `VATNormalizationService` | AccountService, JournalEntryService |
| **AuditService** | Log all AR mutations (create/edit/post/approve), compute diffs, hash events, filter/export audit records | `AuditService`, `AuditLogRepository` | (Core shared service) |
| **ImportService** (AR-specific) | Parse CSV/Excel invoice/receipt templates, validate row-level, return error map with row numbers, atomic batch insert/reject | `ARImportService`, `InvoiceImportParser`, `ReceiptImportParser` | SalesInvoiceService, ARReceiptService |
| **NotificationService** (AR-specific) | Trigger approval notifications (in-app/email), overdue reminders, statement delivery tracking | `NotificationService`, `ARNotificationStrategy` | Core event bus |

**Architecture Pattern:** Service layer + Repository (Spring Data JPA) + Validator pattern; domain-driven design with Invoice/Receipt aggregates managing their own state transitions and invariants (e.g., prevent posting an already-paid invoice).

---

### Data Models and Contracts

#### Core Entities

**SalesInvoice**
```
PK: id (UUID)
Fields:
  - invoiceNumber: String (auto-generated, format INV-{YYYY}-{seq}, unique per customer+period)
  - customerID: UUID (FK → Customer, required)
  - companyID: UUID (FK → Company, inherited from CompanyScopedEntity)
  - invoiceDate: LocalDate (must be in open period)
  - dueDate: LocalDate (default = invoiceDate + 30 days)
  - referenceText: String (optional)
  - status: enum (DRAFT, PENDING_APPROVAL, POSTED, REJECTED, PAID, PARTIALLY_PAID)
  - totalAmount: BigDecimal (computed from lines)
  - vATAmount: BigDecimal (sum of line VAT)
  - amountPaid: BigDecimal (sum of allocated receipts, updated on receipt post)
  - createdBy: UUID (FK → User)
  - createdAt: Instant
  - approvedBy: UUID (FK → User, nullable until POSTED)
  - approvedAt: Instant (nullable)
  - rejectionReason: String (optional, populated if status=REJECTED)
  - voucherID: UUID (FK → Voucher, nullable until posted)
  - externalAttachmentUrls: List<String> (JSON array, soft-deleted on invoice delete)
  - isDeleted: boolean (soft delete flag)
  - deletedAt: Instant (soft delete timestamp)

Constraints:
  - UNIQUE(companyID, customerID, invoiceNumber, invoiceDate) — prevent duplicates
  - CHECK(invoiceDate <= dueDate)
  - CHECK(totalAmount > 0)
  - Composite index on (companyID, status, invoiceDate) for list queries
```

**ARInvoiceLine**
```
PK: id (UUID)
FK: invoiceID (UUID → SalesInvoice, cascading delete)
Fields:
  - lineNumber: Integer (1-based, unique per invoice)
  - description: String (required)
  - quantity: BigDecimal (required, > 0)
  - unitPrice: BigDecimal (required, > 0)
  - vATRate: enum (0, 5, 10, EXEMPT; default from company settings)
  - revenueAccountCode: String (FK → ChartOfAccounts, must be leaf, must be in 5xx range per TT200)
  - itemID: UUID (FK → Item, optional; required if account requires it per account_controls)
  - discountAmount: BigDecimal (optional, default 0)
  - lineTotal: BigDecimal (computed: qty * unitPrice - discount)
  - lineVAT: BigDecimal (computed: lineTotal * (vATRate / 100))

Computed Fields (not stored, calculated on render):
  - lineGrandTotal = lineTotal + lineVAT

Validation Rules:
  - quantity > 0, unitPrice > 0
  - revenueAccountCode must be leaf (postable)
  - discountAmount <= lineTotal
  - vATRate must be 0, 5, 10, or EXEMPT per company settings
```

**ARReceipt**
```
PK: id (UUID)
Fields:
  - receiptNumber: String (auto-generated, format RCP-{YYYY}-{seq})
  - customerID: UUID (FK → Customer)
  - companyID: UUID (inherited from CompanyScopedEntity)
  - receiptDate: LocalDate (must be in open period)
  - bankAccountID: UUID (FK → BankAccount, required; must be account 111 or 112)
  - amount: BigDecimal (required, > 0)
  - referenceText: String (optional)
  - method: enum (BANK_TRANSFER, CASH, CHECK, OTHER)
  - status: enum (DRAFT, POSTED, REVERSED)
  - allocationStatus: enum (UNALLOCATED, PARTIALLY_ALLOCATED, FULLY_ALLOCATED)
  - createdBy: UUID (FK → User)
  - createdAt: Instant
  - voucherID: UUID (FK → Voucher, nullable until posted)
  - reversalReceiptID: UUID (FK → ARReceipt, nullable; if set, this is a reversal of the original)
  - reversingReceiptID: UUID (FK → ARReceipt, nullable; if set, another receipt reverses this one)
  - externalAttachmentUrls: List<String>
  - isDeleted: boolean
  - deletedAt: Instant

Constraints:
  - UNIQUE(companyID, receiptNumber, receiptDate)
  - CHECK(amount > 0)
  - CHECK(bankAccountID → account.code IN ('111', '112')) — enforced at repo/service level
  - If reversalReceiptID is not NULL, original must exist and status=POSTED
```

**ARAllocation**
```
PK: id (UUID)
FK1: receiptID (UUID → ARReceipt, ON DELETE CASCADE)
FK2: invoiceID (UUID → SalesInvoice)
Fields:
  - allocatedAmount: BigDecimal (> 0, <= invoice.remainingBalance at time of creation)
  - allocationDate: LocalDate
  - notes: String (optional)
  - isReversed: boolean (if true, the allocation is superseded by a reversal receipt)
  - reversalAllocationID: UUID (FK → ARAllocation, nullable; points to the reversal counterpart)

Constraints:
  - UNIQUE(receiptID, invoiceID) — one allocation per receipt-invoice pair
  - CHECK(allocatedAmount > 0)
  - Sum(allocatedAmount) per receipt ≤ receipt.amount
  - Sum(allocatedAmount) per invoice ≤ invoice.totalAmount (including VAT)
```

**ARAgingCache** (materialized view / denormalized table)
```
PK: id (UUID)
Fields:
  - companyID: UUID
  - customerID: UUID
  - invoiceID: UUID (optional, for drill-down detail)
  - totalOutstanding: BigDecimal
  - currentAmount: BigDecimal (due within 0 days)
  - days1To30: BigDecimal
  - days31To60: BigDecimal
  - days61To90: BigDecimal
  - daysOver90: BigDecimal
  - lastRefreshedAt: Instant
  - snapshotDate: LocalDate (aging as-of date)

Refresh Trigger:
  - On invoice POST (increment bucket)
  - On receipt POST (decrement appropriate bucket)
  - Scheduled job nightly (full recompute) or on period close
  - Manual invalidation endpoint for admin
```

**AuditLog** (shared, inherited by all epics)
```
PK: id (UUID)
Fields:
  - entityType: String (e.g., 'SALES_INVOICE', 'AR_RECEIPT', 'AR_ALLOCATION')
  - entityID: UUID
  - action: enum (CREATE, UPDATE, DELETE, POST, APPROVE, REJECT, REVERSE, IMPORT, EXPORT)
  - actorID: UUID (FK → User)
  - actorRole: String (snapshot of role at time of action, for audit defensibility)
  - timestamp: Instant (append-only, never modified)
  - deviceIPAddress: String (captured from request header X-Forwarded-For or remote address)
  - userAgent: String (captured from request User-Agent header)
  - beforeSnapshot: JsonNode (entity state before the action)
  - afterSnapshot: JsonNode (entity state after the action)
  - diffSummary: String (human-readable change description)
  - eventHash: String (SHA256(entityType + entityID + action + timestamp + actorID + beforeSnapshot + afterSnapshot))
  - companyID: UUID (for multi-tenant filtering)

Constraints:
  - All fields immutable after insert (database triggers enforce)
  - Indexed on (entityType, entityID, timestamp) for efficient audit trail queries
  - Indexed on (companyID, timestamp) for bulk audit export
  - No deletes; GDPR right-to-be-forgotten handled via anonymization (actor details masked, not removed)
```

---

### APIs and Interfaces

#### REST Endpoints (RESTful design)

**Sales Invoices**

```
GET    /api/v1/sales-invoices
       Query: page=0&size=20&status=DRAFT&dateFrom=2025-01-01&dateTo=2025-01-31&customerID=...&search=...
       Response: { data: { content: [SalesInvoice], totalElements: 100, totalPages: 5 }, meta: { requestId, timestamp } }

POST   /api/v1/sales-invoices
       Request: { customerID, invoiceDate, dueDate, referenceText, lines: [{ description, qty, unitPrice, vATRate, revenueAccountCode, itemID }], attachments: [urls] }
       Response: { data: { id, invoiceNumber, status: 'DRAFT' }, meta: { ... } }
       Validation: customerID exists; invoiceDate in open period; all line validations (positive qty/price, leaf account, VAT rate in [0,5,10,EXEMPT])

GET    /api/v1/sales-invoices/:id
       Response: Full SalesInvoice with lines, customer details, and computed remaining balance

PUT    /api/v1/sales-invoices/:id
       Request: { dueDate, referenceText, lines: [...], attachments: [...] }
       Validation: status must be DRAFT; same line validations as POST

DELETE /api/v1/sales-invoices/:id
       Validation: status must be DRAFT; only creator or admin can delete; soft delete flagged

POST   /api/v1/sales-invoices/:id/post
       Request: {} (trigger posting)
       Response: { data: { status: 'PENDING_APPROVAL' or 'POSTED', voucherID }, meta: { ... } }
       Side Effects: 
         - Create journal voucher (Dr 131 / Cr 5xx)
         - Update status to PENDING_APPROVAL (if above threshold) or POSTED (if below threshold or auto-approved)
         - Create audit log
         - Invalidate AR aging cache
         - Emit notification event if approval required

POST   /api/v1/sales-invoices/:id/approve
       Request: {} (only available if status=PENDING_APPROVAL)
       Authorization: Must be Chief Accountant role, and different from creator
       Response: { data: { status: 'POSTED' }, meta: { ... } }
       Side Effects:
         - Update status to POSTED
         - Set approvedBy, approvedAt
         - Create audit log

POST   /api/v1/sales-invoices/:id/reject
       Request: { rejectionReason }
       Authorization: Chief Accountant role, different from creator
       Response: { data: { status: 'REJECTED' }, meta: { ... } }
       Side Effects:
         - Revert status to DRAFT
         - Set rejectionReason
         - Create audit log
         - Emit notification (rejection reason to creator)

POST   /api/v1/sales-invoices/:id/reverse
       Request: {} (only available if status=POSTED)
       Response: { data: { reversalVoucherID, reversalInvoiceID }, meta: { ... } }
       Side Effects:
         - Create reversal voucher (opposite debit/credit)
         - Update original invoice status to POSTED (marked as reversed in audit)
         - Create new linked invoice with REVERSED status
         - Create audit log with cross-reference
         - Invalidate AR aging cache

POST   /api/v1/sales-invoices/import
       Content-Type: multipart/form-data (file)
       Request: FormData with CSV/Excel file (expected columns: CustomerCode, InvoiceNumber, InvoiceDate, DueDate, [Lines])
       Response: { data: { importID, importedCount, errorCount, errorMap: [{ rowNumber, field, message }] }, meta: { ... } }
       Validation: Template validation, atomic batch (all succeed or all fail)
       Side Effects: Create audit log for import action
```

**AR Receipts**

```
GET    /api/v1/ar-receipts
       Query: page, size, status, dateFrom, dateTo, customerID, search
       Response: { data: { content: [ARReceipt], totalElements, totalPages }, meta: { ... } }

POST   /api/v1/ar-receipts
       Request: { customerID, receiptDate, bankAccountID, amount, referenceText, method, allocations: [{ invoiceID, allocatedAmount }] }
       Response: { data: { id, receiptNumber, allocationStatus: 'UNALLOCATED|PARTIALLY_ALLOCATED|FULLY_ALLOCATED' }, meta: { ... } }
       Validation: 
         - customerID exists, has open invoices
         - receiptDate in open period
         - bankAccountID must be 111 or 112
         - amount > 0
         - allocations.sum ≤ amount
         - allocations.sum per invoice ≤ invoice.remainingBalance

PUT    /api/v1/ar-receipts/:id
       Request: { amount, referenceText, allocations: [...] }
       Validation: status must be DRAFT

POST   /api/v1/ar-receipts/:id/post
       Request: {}
       Response: { data: { status: 'POSTED', voucherID, updatedInvoices: [{ id, status, amountPaid }] }, meta: { ... } }
       Side Effects:
         - Create journal voucher (Dr 111/112 / Cr 131)
         - Update receipt status to POSTED
         - Update allocated invoices: amountPaid += allocation.amount, status := PAID|PARTIALLY_PAID
         - Create audit log
         - Invalidate AR aging cache

POST   /api/v1/ar-receipts/:id/reverse
       Request: {}
       Response: { data: { reversalReceiptID }, meta: { ... } }
       Side Effects:
         - Create reversal receipt with opposite amount
         - Create reversal voucher
         - Update allocations to isReversed=true
         - Update invoices: amountPaid -= reversed allocations
         - Create audit log with cross-reference
         - Invalidate AR aging cache

POST   /api/v1/ar-receipts/import
       Content-Type: multipart/form-data
       Request: FormData with CSV/Excel file
       Response: { data: { importID, importedCount, errorMap }, meta: { ... } }
```

**AR Aging & Reports**

```
GET    /api/v1/ar-aging
       Query: asOfDate=2025-01-31&customerID=...&agingFormat=STANDARD
       Response: { data: { aging: [{ customerID, customerName, current, days1To30, days31To60, days61To90, daysOver90, total }], totals, meta: { cachedAt } } }
       Cache: Redis with 1-hour TTL, invalidated on invoice/receipt POST

GET    /api/v1/ar-aging/:customerId/detail
       Query: agingBucketKey (e.g., 'DAYS_31_60')
       Response: { data: { invoices: [{ invoiceNumber, invoiceDate, amount, amountPaid, daysOverdue, status, lastPaymentDate }] }, meta: { ... } }
       Drill-down enabling

GET    /api/v1/ar-aging/export
       Query: asOfDate, format=EXCEL|PDF, customerID
       Response: Binary file (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet or application/pdf)
       Headers: Content-Disposition: attachment; filename="AR_Aging_2025-01-31.xlsx"
       Side Effects: Create audit log for export action
```

**AR Statements**

```
GET    /api/v1/ar-statements/:customerId
       Query: fromDate, toDate, format=SUMMARY|DETAILED
       Response: { data: { customer: { id, name, address }, statements: [{ invoiceID, invoiceNumber, invoiceDate, amount, paidAmount, balance, paymentDetails: [{ receiptDate, paidAmount }] }], totals, computedAt } }

GET    /api/v1/ar-statements/:customerId/export
       Query: format=EXCEL|PDF, fromDate, toDate
       Response: Binary file
       Side Effects: Create audit log; update statement distribution tracking

POST   /api/v1/ar-statements/:customerId/send
       Request: { email, message }
       Response: { data: { deliveryID, sentAt, status: 'SENT' }, meta: { ... } }
       Side Effects: Queue email job, create delivery tracking record, audit log

POST   /api/v1/ar-statements/:customerId/import-reconciliation
       Content-Type: multipart/form-data
       Request: FormData with CSV file (expected columns: InvoiceNumber, CustomerAmount, CustomerPayment, [Notes])
       Response: { data: { reconciliationID, matchedCount, mismatchCount, mismatches: [{ invoiceNumber, systemAmount, customerAmount, notes }] }, meta: { ... } }
       Side Effects: Create dispute log entry, create audit log
```

**VAT Reporting (AR-specific)**

```
GET    /api/v1/ar-vat-report
       Query: period=2025-01&format=ND123
       Response: { data: { vATLines: [{ invoiceNumber, invoiceDate, customerName, revenue, vAT0, vAT5, vAT10 }], totals }, meta: { ... } }

GET    /api/v1/ar-vat-report/export
       Query: period, format=EXCEL|JSON
       Response: Binary file in ND123 format per Circular 200
```

**Audit Queries**

```
GET    /api/v1/audit-logs?entityType=SALES_INVOICE&entityID=...&startDate=...&endDate=...
       Authorization: Chief Accountant, CFO, Admin only
       Response: { data: { logs: [{ action, actorID, timestamp, deviceIP, diffSummary, eventHash }] }, meta: { ... } }

POST   /api/v1/audit-logs/export
       Query: entityType, startDate, endDate, format=PDF|JSON
       Response: Binary file with hashes for integrity verification
       Side Effects: Create audit log for export action (meta-audit)
```

---

### Workflows and Sequencing

#### Workflow 1: Sales Invoice Entry → Posting → Approval (if threshold exceeded)

```
Mermaid diagram:
flowchart TD
  A["Accountant: New Invoice Form"] -->|Fill customer, lines, VAT rates| B["System: Validate inline"]
  B -->|Required fields OK, amounts positive, accounts leaf| C["Accountant: Save as Draft"]
  C -->|status=DRAFT| D["System: Store invoice + lines"]
  D -->|Calculate totals| E["System: Display totals, remaining balance"]
  E -->|Review complete, no errors| F["Accountant: Click 'Post'"]
  F -->|Trigger POST endpoint| G["System: Final validation (period, amounts, doubleentry)"]
  G -->|amount > threshold 100M| H["System: status=PENDING_APPROVAL"]
  G -->|amount <= threshold| I["System: status=POSTED (auto-approve)"]
  H -->|Notify Chief Accountant| J["Chief Accountant: Approve/Reject UI"]
  J -->|Approve| K["System: status=POSTED, create journal voucher Dr131/Cr5xx"]
  J -->|Reject + reason| L["System: status=DRAFT, notify creator with reason"]
  K -->|Invalidate cache| M["System: Refresh AR Aging cache"]
  I -->|Invalidate cache| M
  M -->|Update dashboard| N["Dashboard: AR tiles updated within 5s"]
```

#### Workflow 2: Customer Payment Receipt → Invoice Allocation → Reversal (if needed)

```
flowchart TD
  A["Accountant: New Receipt Form"] -->|Select customer, bank/cash, amount| B["System: Auto-load open invoices"]
  B -->|Suggest full allocations or allow manual| C["Accountant: Allocate to 1+ invoices (partial OK)"]
  C -->|Validate: sum ≤ receipt.amount, per-invoice ≤ remaining| D["System: Show allocation preview"]
  D -->|OK| E["Accountant: Save as Draft + Post"]
  E -->|status=POSTED| F["System: Create journal voucher Dr111/112/Cr131"]
  F -->|Post voucher| G["System: Update invoice.amountPaid, status→PAID|PARTIALLY_PAID"]
  G -->|Invalidate cache| H["System: Refresh AR Aging"]
  H -->|Update dashboard| I["Dashboard: AR tiles updated"]
  I -->|Later, reversal needed| J["Accountant: Click 'Reverse' on receipt"]
  J -->|Confirm| K["System: Create reversal receipt (negative amount)"]
  K -->|Create reversal voucher| L["System: Create reversal voucher Dr131/Cr111-112"]
  L -->|Mark allocations reversed| M["System: Revert invoice.amountPaid, status→UNPAID|PARTIALLY_PAID"]
  M -->|Cross-link receipts| N["System: Set reversalReceiptID, reversingReceiptID"]
  N -->|Invalidate cache, audit| O["System: Create audit log with cross-reference"]
```

#### Workflow 3: AR Aging Report & Drill-Down

```
flowchart TD
  A["User: Open AR Aging Dashboard"] -->|Query /ar-aging with asOfDate| B["System: Check cache (Redis)"]
  B -->|Hit: return cached result| C["System: Display aging grid"]
  B -->|Miss: Compute from scratch| D["System: Query invoices with allocation joins"]
  D -->|Filter by company, period, status=POSTED| E["System: Compute aging buckets per customer"]
  E -->|Cache result (TTL 1h)| F["System: Display aging grid with customer totals"]
  F -->|Accountant: Click cell in 'Days 31-60' bucket| G["System: Query drill-down with agingBucketKey"]
  G -->|Return invoices in that bucket| H["System: Display invoice detail grid"]
  H -->|Click invoice| I["System: Open invoice detail (read-only) with payment history"]
```

#### Workflow 4: Import Sales Invoices (CSV/Excel)

```
flowchart TD
  A["Accountant: Upload invoice CSV/Excel"] -->|Validate file format| B["System: Parse template (CustomerCode, InvoiceNumber, InvoiceDate, Lines)"]
  B -->|Validate headers + row count| C["System: Validate each row"]
  C -->|For each row: check customer exists, date in period, line amounts positive| D["System: Accumulate errors per row number"]
  D -->|Errors found| E["System: Return error map"]
  E -->|Accountant: Download error map, fix, re-upload| F["Retry"]
  D -->|No errors| G["System: Atomic batch insert (all succeed or all fail)"]
  G -->|Compute totals per invoice| H["System: Create invoice records in DRAFT status"]
  H -->|Create audit log (action=IMPORT)| I["System: Return importID + count"]
  I -->|Success toast| J["Dashboard: Import summary shown"]
```

#### Workflow 5: AR Statement Reconciliation

```
flowchart TD
  A["Accountant: Open customer statement view"] -->|Query /ar-statements/:customerId| B["System: Compute summary (invoices, payments, running balance)"]
  B -->|Export PDF/Excel| C["System: Generate formatted statement"]
  C -->|Attachment optional| D["Accountant: Send to customer via email"]
  D -->|POST /ar-statements/:customerId/send| E["System: Queue email job, track delivery"]
  E -->|Later: Customer provides reconciliation| F["Accountant: Upload reconciliation CSV"]
  F -->|POST /ar-statements/:customerId/import-reconciliation| G["System: Parse and compare"]
  G -->|For each row: match invoice, compare amounts| H["System: Flag mismatches"]
  H -->|Discrepancies found| I["System: Create dispute log entry"]
  I -->|Display mismatch grid, allow notes| J["Accountant: Resolve disputes"]
  J -->|Create adjustment voucher or contact customer| K["System: Link resolution to dispute entry"]
```

---

## Acceptance Criteria (Atomic & Testable)

### FR23: Sales Invoice Creation

| ID | Acceptance Criterion | Testable | Story |
|---|---|---|---|
| AC23-001 | **Customer Selection:** Invoice form provides typeahead/searchable dropdown to select customer; "Add New Customer" option unavailable (deferred to Master Data module). When customer selected, fetch and display customer tax code and address in read-only fields. | test_invoiceForm_customerTypeahead_displaysMatches | 5.1 |
| AC23-002 | **Invoice Number Generation:** On save, system auto-generates invoice number in format `INV-{YYYY}-{seq}` where {seq} increments per customer per calendar year. Duplicate prevention: system prevents saving if same (customerID, invoiceNumber, invoiceDate) exists. | test_invoiceCreate_generates_uniqueInvoiceNumber_perCustomer_perYear | 5.1 |
| AC23-003 | **Line Items:** Form supports adding ≥1 line item with fields: description (required), quantity (required, > 0), unitPrice (required, > 0), VAT% (dropdown: 0, 5, 10, EXEMPT, default from company settings), revenue account (leaf-only typeahead), optional item/service lookup. System auto-calculates: lineTotal = qty × unitPrice - discount; lineVAT = lineTotal × (VAT% / 100); lineGrandTotal = lineTotal + lineVAT. | test_invoiceLine_autoCalculates_totals_forAllVATRates | 5.1 |
| AC23-004 | **Leaf-Only Account Enforcement:** On line creation, system queries account master; rejects accounts with is_leaf = false; prevents summary/parent accounts from being used for posting. Error returned: "Account {code} is not postable; select a detail account." | test_invoiceLine_rejects_parentAccount | 5.1 |
| AC23-005 | **Header Total Validation:** System computes invoice totals in real-time: totalAmount = Σ(lineTotal), vATAmount = Σ(lineVAT). Inline validation ensures VAT header matches sum of line VATs within rounding tolerance (±1 VND). Display error if mismatch. | test_invoice_headerVATvalidation_matchesSumOfLines | 5.1 |
| AC23-006 | **Draft Save & Autosave:** User can save form at any time with incomplete fields (draft mode). System implements autosave every 30s while form is open (no save button click required). Undo/Redo available for last 10 edits. | test_invoiceForm_autosave_every30s | 5.1 |
| AC23-007 | **Access Control:** Only invoice creator or Admin can edit/delete drafts. Attempt by other user returns 403 "Insufficient permissions." Soft delete: set isDeleted=true, deletedAt=NOW(), do not physically remove. | test_invoiceDelete_forbidden_forNonCreator | 5.1 |
| AC23-008 | **Attachments:** Form allows drag/drop or file picker for attachments (images, PDFs, XLS). File type validation: only {pdf, xlsx, xls, jpg, png, jpeg} allowed. Max 5MB per file, 20MB total per invoice. Preview thumbnail for images. Delete allowed only on drafts. Attachment changes logged to audit trail. | test_invoiceAttachment_typeValidation_rejectsExE | 5.1 |
| AC23-009 | **Import (CSV/Excel):** API endpoint `/api/v1/sales-invoices/import` accepts multipart file (CSV or Excel). Template validation: headers must include CustomerCode, InvoiceNumber, InvoiceDate, {LineDescription, LineQty, LinePrice, LineVATRate, LineRevenueAccount}. Atomic batch: validate all rows; if any row error, reject all and return errorMap with rowNumber + field + message. Support up to 1000 rows per file. | test_invoiceImport_atomicBatch_rejectsAllOnError | 5.1 |
| AC23-010 | **Duplicate Prevention:** System prevents creating invoice if (customerID, invoiceNumber, invoiceDate) combination exists and isDeleted=false. System returns 409 "Invoice INV-2025-001 already exists for this customer on 2025-01-15." | test_invoiceCreate_duplicate_returns409 | 5.1 |
| AC23-011 | **Inline Validation:** As user types in each field, system provides real-time feedback. Example: if unitPrice < 0, display red error "Price must be positive" below field. Save button disabled if any required field empty or error present. | test_invoiceForm_realtimeValidation_disablesSaveOnError | 5.1 |
| AC23-012 | **Audit Trail:** Every invoice create/edit/delete attempt logged to AuditLog: action=CREATE|UPDATE|DELETE, before/after snapshots in JSON, actor=userId, timestamp, device IP, user agent, eventHash=SHA256(...). | test_invoiceCreate_generates_auditLogEntry_withEventHash | 5.1 |

### FR26: Maker-Checker Approval Workflow

| ID | Acceptance Criterion | Testable | Story |
|---|---|---|---|
| AC26-001 | **Threshold Configuration:** System reads approval threshold from company settings (default 100M VND per company). Admin can update via settings API. Threshold is company-wide; no per-customer overrides in MVP. | test_approvalThreshold_readFromCompanySettings | 5.2 |
| AC26-002 | **Above-Threshold Routing:** When invoice posted with amount > threshold, system sets status=PENDING_APPROVAL (not POSTED). User sees "Awaiting Approval" badge. Approver (Chief Accountant) receives in-app notification + email (if configured). | test_invoicePost_amountOver100M_status_PENDING_APPROVAL | 5.2 |
| AC26-003 | **Below-Threshold Auto-Approval:** When invoice posted with amount ≤ threshold, system sets status=POSTED immediately (no approval required). System creates shadow audit log entry: action=AUTO_APPROVE, with note "Auto-approved: amount ≤ threshold." | test_invoicePost_amountUnder100M_autoApproves | 5.2 |
| AC26-004 | **Approver Identity Validation:** When Chief Accountant approves invoice, system verifies approver ≠ creator. If same person attempts to approve own invoice, request rejected with 403 "Cannot approve your own invoice." Attempt logged to audit trail. | test_invoiceApprove_rejects_creatorApprovesOwn | 5.2 |
| AC26-005 | **Approval UI - Change History:** Approver UI displays invoice summary, all line items, attachments, and "Change History" panel showing all previous edits with timestamps, editor names, and diffs (before/after values). | test_approvalUI_displayChangeHistory | 5.2 |
| AC26-006 | **Approval Action - Approve:** Chief Accountant clicks "Approve" button. System: (1) transitions status to POSTED, (2) sets approvedBy=userId, approvedAt=NOW(), (3) creates journal voucher (Dr 131 / Cr 5xx per line revenue accounts), (4) logs AuditLog entry action=APPROVE, (5) invalidates AR Aging cache, (6) sends confirmation notification to creator. Response includes voucherID. | test_invoiceApprove_createsVoucher_logsAudit | 5.2 |
| AC26-007 | **Approval Action - Reject:** Chief Accountant clicks "Reject" button and enters rejection reason (max 500 chars, required). System: (1) reverts status to DRAFT, (2) sets rejectionReason, (3) logs AuditLog entry action=REJECT with reason, (4) sends email to creator with reason text. Creator can then re-edit and resubmit. | test_invoiceReject_reverts_toDraft_notifiesCreator | 5.2 |
| AC26-008 | **Period Closure Block:** If invoice invoiceDate falls in a closed period (checked via chart_of_accounts_period.period_closed=true), approval attempt is blocked with error "Period 2024-12 is closed; cannot approve." Same block applies to posting of new invoices in closed periods. | test_invoiceApprove_blockedIfPeriodClosed | 5.2 |
| AC26-009 | **Notification Dispatch:** Approval notifications sent via: (1) in-app notification (always), (2) email to user's configured email (if email service available; no error if unavailable). Notification includes invoice number, customer name, amount, approver name/role, and action link. | test_approvalNotification_sentInApp_andEmail | 5.2 |
| AC26-010 | **Audit Trail Completeness:** Every approval/rejection action creates immutable AuditLog entry with: entityType=SALES_INVOICE, action=APPROVE|REJECT, beforeSnapshot (status=PENDING_APPROVAL, approvedBy=null), afterSnapshot (status=POSTED, approvedBy=userId), diffSummary (human-readable), eventHash (SHA256). | test_approvalAudit_immutable_withEventHash | 5.2 |

### FR24: Customer Payment Receipts & Allocation

| ID | Acceptance Criterion | Testable | Story |
|---|---|---|---|
| AC24-001 | **Receipt Form - Customer Selection:** Form provides customer typeahead. When customer selected, system loads all POSTED invoices with status ≠ PAID; displays list of open invoices (with invoice number, date, outstanding amount) for allocation. | test_receiptForm_loadsOpenInvoices_forCustomer | 5.3 |
| AC24-002 | **Receipt Form - Payment Method & Account:** Form provides dropdown for method (BANK_TRANSFER, CASH, CHECK, OTHER). For each method, system filters bank account dropdown to accounts with code IN ('111', '112'). User selects specific bank/cash account. | test_receiptForm_accountFiltering_byMethod | 5.3 |
| AC24-003 | **Receipt Amount Validation:** Form requires amount > 0. System validates: amount ≤ sum of invoice outstanding balances (or allows unallocated advance, marked UNALLOCATED status). On save, system checks: sum(allocations) ≤ receipt.amount. | test_receiptCreate_validatesAmount_positive | 5.3 |
| AC24-004 | **Allocation - Single Invoice:** User selects one invoice and enters allocation amount (defaults to full outstanding balance). System shows "Allocating {amount} to {invoiceNumber}; remaining on invoice: {balance}." Allocation amount must be ≤ invoice outstanding balance. | test_receiptAllocate_single_invoice | 5.3 |
| AC24-005 | **Allocation - Multiple Invoices (Split):** User can add multiple allocations to the same receipt. System validates: sum(allocations) ≤ receipt.amount; per-invoice allocation ≤ outstanding. UI shows allocation grid with invoice number, amount, remaining balance per invoice, and "Remove" per row. | test_receiptAllocate_multiple_invoices_split | 5.3 |
| AC24-006 | **Partial Allocation:** Receipt can be allocated to less than receipt.amount (e.g., receipt 50M, allocate 30M to invoice, leaving 20M unallocated). System sets allocationStatus=PARTIALLY_ALLOCATED. Later, user can add more allocations to same receipt or create new receipt. | test_receiptPartialAllocation_remaining | 5.3 |
| AC24-007 | **Standalone Advance (Unallocated):** User can post receipt without allocations (UNALLOCATED status). System stores receipt with bankAccountID, amount, receiptDate. Later, accountant can create new receipt/allocation or match to future invoices. | test_receiptCreate_unallocated_advance | 5.3 |
| AC24-008 | **Receipt Posting - Voucher Creation:** When receipt posted, system creates journal voucher: Dr {bankAccountID} / Cr 131 (AR account) with amount=receipt.amount. Voucher includes configurable dimensions (cost center, project) from company settings. Voucher.companyID=receipt.companyID. | test_receiptPost_createsVoucher_dr_bank_cr_ar | 5.3 |
| AC24-009 | **Receipt Posting - Invoice Status Update:** When receipt posted, for each allocation, system updates invoice: (1) amountPaid += allocation.amount, (2) status transitions: if amountPaid = totalAmount then PAID, else PARTIALLY_PAID. | test_receiptPost_updatesInvoiceStatus_paid_or_partial | 5.3 |
| AC24-010 | **Receipt Reversal:** When Chief Accountant clicks "Reverse" on posted receipt (status=POSTED), system: (1) creates reversal receipt with negative amount, (2) creates reversal voucher (opposite debit/credit), (3) marks original allocations isReversed=true, (4) updates invoices: amountPaid -= reversed allocations (status reverts to DRAFT or UNPAID), (5) cross-links receipts: original.reversingReceiptID = reversal.id, reversal.reversalReceiptID = original.id, (6) logs AuditLog with cross-reference. | test_receiptReverse_reverts_invoiceStatus_crossLinked | 5.3 |
| AC24-011 | **Receipt Import:** API endpoint `/api/v1/ar-receipts/import` accepts CSV/Excel. Template: CustomerCode, ReceiptDate, Amount, BankAccountCode, Method, [AllocateToInvoiceNumber, AllocateAmount]. Atomic batch validation; return errorMap with row numbers on failure. Support 1000+ rows. | test_receiptImport_atomicBatch | 5.3 |
| AC24-012 | **Receipt Audit Trail:** Every receipt create/edit/post/reverse/import logs AuditLog entry with action, before/after snapshot (e.g., status DRAFT→POSTED, amountPaid 0→allocation.amount), eventHash. Allocation changes also logged separately. | test_receiptCreate_logsAudit_withAllocationChanges | 5.3 |

### FR25: AR Aging Report & Analysis

| ID | Acceptance Criterion | Testable | Story |
|---|---|---|---|
| AC25-001 | **Aging Buckets:** System computes aging based on invoice dueDate vs. asOfDate: (1) Current (0 days overdue, dueDate ≥ asOfDate), (2) 1–30d overdue, (3) 31–60d overdue, (4) 61–90d overdue, (5) 91+ days overdue. For each bucket per customer, sum outstanding balance (totalAmount - amountPaid for POSTED invoices, excluding PAID/REVERSED). | test_arAging_computesBuckets_correctly | 5.4 |
| AC25-002 | **AR Aging by Customer:** Report displays grid: Customer Name, Current, 1–30d, 31–60d, 61–90d, 91+d, Total Outstanding. Row total = sum of buckets; column totals shown. Excludes reversed invoices, drafts, and invoices in closed periods. | test_arAging_customerGrid_totals_correct | 5.4 |
| AC25-003 | **Cache Performance:** GET /api/v1/ar-aging returns results in <100ms (cache hit) or <1s (cache miss + recompute). System uses Redis cache with 1-hour TTL. Cache key includes asOfDate + companyID. Cache invalidated on every invoice POST or receipt POST. | test_arAging_cacheHit_under100ms | 5.4 |
| AC25-004 | **Drill-Down to Invoice List:** When user clicks on aging cell (e.g., "Days 31–60d" for customer X), system queries GET /api/v1/ar-aging/:customerId/detail?agingBucketKey=DAYS_31_60. Response: list of invoices in that bucket with invoiceNumber, invoiceDate, dueDate, outstanding amount, amountPaid, days overdue, status, last payment date. | test_arAging_drillDown_showsInvoices | 5.4 |
| AC25-005 | **Invoice Detail from Aging:** User can click invoice number in drill-down list to open read-only invoice detail view with full lines, customer details, payment history (list of receipts applied). | test_arAging_invoiceDetail_fromDrillDown | 5.4 |
| AC25-006 | **Export to Excel/PDF:** GET /api/v1/ar-aging/export?format=EXCEL&asOfDate=2025-01-31 returns binary file. Excel includes: (1) header with company name, as-of date, report generated timestamp, (2) aging grid with customer totals, (3) summary sheet with total outstanding per bucket, (4) active filters displayed (e.g., "Filtered by Company: ABC Corp, Customer: XYZ Ltd"). PDF similar format with footer containing hash for audit defensibility. | test_arAging_export_excelFormat_includesMetadata | 5.4 |
| AC25-007 | **Overdue Dashboard Tiles:** Dashboard displays: (1) Total Overdue (sum of 1–30d, 31–60d, 61–90d, 91+d buckets), (2) Overdue Count (number of invoices), (3) Top 5 Overdue Customers (with amount). Tiles refresh every 5s or on manual refresh; pull from AR Aging cache. | test_dashboard_overduesTiles_refreshUnder5s | 5.4 |
| AC25-008 | **Automated Reminders (Configuration):** Admin can configure reminder schedule via settings: (1) Pre-due reminder (e.g., 3 days before due date), (2) Due-date reminder, (3) Post-due cadence (e.g., every 7 days after due date). Accountant can trigger manual reminder batch via API (queues email jobs). Automated daily nightly job is post-MVP. | test_reminderConfig_readFromSettings | 5.4 |
| AC25-009 | **RBAC for AR Aging:** API enforces role-based access: (1) CFO: view-only all invoices, (2) Chief Accountant: view-only all invoices + access to drill-downs, (3) Accountant: view only own company's invoices (filtered by companyID), (4) CFO cannot post/approve/reverse. @PreAuthorize on /ar-aging endpoints. | test_arAging_rbac_forbidsAccountantApprove | 5.4 |
| AC25-010 | **Aging Snapshot Consistency:** asOfDate parameter is consistent across all queries; if report generated at 2025-01-31 15:30, all invoices aged as of that moment (no mid-report date changes). Snapshot metadata (computedAt, snapshotDate) included in response. | test_arAging_snapshotConsistency_asOfDate | 5.4 |

### Custom Stories: Statements, VAT, Audit Trail

| ID | Acceptance Criterion | Testable | Story |
|---|---|---|---|
| AC-STMT-001 | **Customer Statement - Summary View:** GET /api/v1/ar-statements/:customerId?format=SUMMARY displays: (1) customer header (name, address, tax code), (2) statement rows: invoice number, invoice date, invoice amount, amount paid, balance (outstanding), (3) totals row: total invoices, total paid, total outstanding. Running balance column shows cumulative outstanding. | test_statement_summary_format_correct | 5.5 |
| AC-STMT-002 | **Customer Statement - Detailed View:** GET /api/v1/ar-statements/:customerId?format=DETAILED displays: for each invoice, include sub-rows: receipts applied, credit notes, adjustments, with receipt date, amount, reference. Running balance updates after each transaction. | test_statement_detailed_expandsReceipts | 5.5 |
| AC-STMT-003 | **Statement Export:** GET /api/v1/ar-statements/:customerId/export?format=PDF|EXCEL. PDF includes: legal footer with company address/tax code, report hash (SHA256 of content for audit defensibility), statement date. Excel includes all columns + formulas for balance calculations. Filename: "Statement_{CustomerCode}_{Date}.pdf". | test_statement_export_pdfWithFooterHash | 5.5 |
| AC-STMT-004 | **Send to Customer:** POST /api/v1/ar-statements/:customerId/send?email=customer@xyz.com queues email job. Email body includes statement attachment (PDF) + message (configurable). System creates delivery tracking record (sentAt, status=SENT, recipientEmail). Notification sent to accountant confirming dispatch. | test_statement_sendEmail_queued_tracked | 5.5 |
| AC-STMT-005 | **Customer Reconciliation Import:** POST /api/v1/ar-statements/:customerId/import-reconciliation uploads CSV from customer. Template: InvoiceNumber, CustomerAmount (what customer claims), CustomerPayment (what they paid), Notes. System parses, matches to system invoices, flags mismatches: if system.amount ≠ customer.amount, mark MISMATCH with colors (red=significant variance, yellow=rounding). Response includes reconciliationID, matchedCount, mismatchCount, list of mismatches. | test_statement_reconciliation_detectsMismatches | 5.5 |
| AC-STMT-006 | **Dispute Logging:** On reconciliation import, system creates DisputeLog entry for each mismatch: reconciliationID, invoiceID, systemAmount, customerAmount, variance, notes, status=OPEN. Accountant can view dispute grid, add resolution notes, mark RESOLVED. Resolution audit-logged. | test_dispute_log_tracked_andResolved | 5.5 |
| AC-VAT-001 | **VAT Rate Override with Warning:** Invoice line default VAT rate from company settings. User can override by selecting different rate. System shows warning: "You are overriding the default VAT rate from {oldRate}% to {newRate}%. Ensure this is correct per customer agreement." Confirmation required. | test_vat_override_showsWarning | 5.6 |
| AC-VAT-002 | **GL Split on Invoice Post:** When invoice posted, for each line, system creates GL split: Dr 131 (AR), Cr 5xx (revenue per line.revenueAccountCode), Cr 3331 (output VAT). Example: line with lineTotal=100, VAT=10, creates: Dr 131 100, Cr 511 90, Cr 3331 10 (balanced). | test_vat_glSplit_dr131_cr5xx_cr3331 | 5.6 |
| AC-VAT-003 | **VAT Rounding Tolerance:** System computes VAT to 2 decimal places (VND cents). Rounding rules: line VAT rounded to nearest 100 VND (per Circular 200 interpretation). If header VAT ≠ sum of line VAT after rounding, system flags with warning: "VAT rounding variance: {variance} VND." Allow posting if variance < 1000 VND; block if ≥ 1000 VND. | test_vat_roundingTolerance_blocks_if_over1000 | 5.6 |
| AC-VAT-004 | **Credit Note Support:** Negative invoice (credit note) supported; must reference original invoice (field: originalInvoiceID). VAT computed as negative. GL splits inverted: Cr 131 (reverses AR), Dr 5xx (reverses revenue), Dr 3331 (reverses VAT). Linked via audit trail: credit note AuditLog references original invoice ID. | test_creditNote_glSplitInverted_linkedInAudit | 5.6 |
| AC-VAT-005 | **ND123 VAT Report:** GET /api/v1/ar-vat-report?period=2025-01 returns data for output VAT report. Response: array of { invoiceNumber, invoiceDate, customerName, customerTaxCode, revenue0pct, revenue5pct, revenue10pct, revenueExempt, total_vat_collected }. GET /api/v1/ar-vat-report/export?format=EXCEL exports in standard Excel format per Circular 200 spec. | test_vat_nd123_export_formatCorrect | 5.6 |
| AC-AUDIT-001 | **Audit Log Entry on Create:** Every invoice/receipt/allocation CREATE action logs to AuditLog: entityType={SALES_INVOICE|AR_RECEIPT|AR_ALLOCATION}, action=CREATE, beforeSnapshot={}, afterSnapshot={full new entity}, diffSummary="Created invoice INV-2025-001", actorID, actorRole (snapshot), timestamp, deviceIPAddress, userAgent, eventHash. | test_create_logsAudit_withSnapshot | 5.7 |
| AC-AUDIT-002 | **Audit Log Entry on Post/Approve:** POST/APPROVE actions log: action={POST|APPROVE}, beforeSnapshot={old status, before approver}, afterSnapshot={new status, approver set}, diffSummary="Posted invoice; status DRAFT→PENDING_APPROVAL", eventHash. Hash computed deterministically so same input = same hash. | test_post_logsAudit_withBeforeAfterDiff | 5.7 |
| AC-AUDIT-003 | **Audit Trail History View:** UI displays "Audit History" panel for invoice/receipt showing chronological log: action, actor name, timestamp, device/IP (masked for privacy), diffSummary. User can click to expand and see before/after snapshots in JSON. | test_auditHistory_displayChronological | 5.7 |
| AC-AUDIT-004 | **Event Hash Integrity:** Every AuditLog entry includes eventHash = SHA256(entityType + entityID + action + timestamp + actorID + beforeSnapshot + afterSnapshot). On export, system can validate hash to detect tampering. Export includes hash column for external verification. | test_auditHash_computed_deterministically | 5.7 |
| AC-AUDIT-005 | **Immutable Audit Log:** AuditLog entries are immutable after insert (enforced by database trigger; UPDATE/DELETE forbidden). Export includes note: "Audit logs are immutable and retained for 10 years per Vietnamese law." | test_auditLog_immutable_blockUpdate | 5.7 |
| AC-AUDIT-006 | **GDPR Anonymization Process:** On request to purge user data, system anonymizes AuditLog entries (set actorID to ANONYMIZED_UUID, actorRole to 'ANONYMIZED'). Actor details no longer appear in future exports. Anonymization itself logged to audit trail (action=ANONYMIZE). | test_gdpr_purge_anonymizes_auditLog | 5.7 |
| AC-AUDIT-007 | **Audit Export (PDF/JSON):** POST /api/v1/audit-logs/export?entityType=SALES_INVOICE&startDate=2025-01-01&endDate=2025-01-31&format=PDF exports chronological audit trail for period. PDF includes: (1) summary (action counts, users involved), (2) detailed log (one row per action with before/after, hash), (3) footer with hash verification note. JSON includes structured data for downstream analysis. | test_auditExport_pdf_withHashSignature | 5.7 |

---

## Non-Functional Requirements

### Performance

- **Response Times:** 
  - Invoice list (GET /api/v1/sales-invoices) with pagination: <500ms (P95) via indexed queries on (companyID, status, invoiceDate).
  - Invoice create (POST) with validation: <800ms (P95).
  - AR Aging dashboard: <1s (P95) via Redis cache with 1-hour TTL.
  - Invoice post (state transition + journal voucher creation): <2s (P95).
  - Report generation (Excel export, AR Aging): <3s (P95) via batch query + in-memory aggregation.

- **Scalability:** Support 100+ concurrent invoice entries; materialized AR Aging Cache prevents N+1 queries; batch import supports 1000+ invoices per file (processed asynchronously if needed).

- **Database Optimization:**
  - Indexes: (companyID, status, invoiceDate), (companyID, customerID, invoiceNumber), (companyID, receiptDate), (invoiceID) on ARAllocation for receipt allocation lookups.
  - Query Optimization: Avoid N+1 by eagerly loading customer, company, and line items; use JPA projections for list queries to reduce payload.
  - Cache: Redis for AR Aging Cache (refresh on invoice/receipt POST), Report Cache (invalidate on period close).

---

### Security

- **Authorization:**
  - Accountant: Create/edit/view own invoices/receipts; cannot approve above threshold.
  - Chief Accountant: Full CRUD; approve/reject; reverse; export audit logs.
  - CFO: View-only AR Aging, reports.
  - Admin: User/company management.
  - Enforcement: Spring Security @PreAuthorize on every endpoint; repository-level company_id filtering for multi-tenancy.

- **Data Protection:**
  - Data in Transit: HTTPS/TLS mandatory; JWT tokens in Authorization header (not cookies in MVP, but consider HttpOnly cookies for refresh tokens).
  - Data at Rest: Database passwords via environment variables; no hardcoded secrets.
  - Masking: Audit logs mask sensitive details (e.g., customer PII) in diffs for GDPR compliance.

- **Audit & Compliance:**
  - Every AR action (create/post/approve/reverse) logged to immutable AuditLog table.
  - Event hashing (SHA256) for integrity verification.
  - 10-year retention; GDPR right-to-be-forgotten via anonymization (actor details masked).
  - Audit export (PDF/JSON) with hash signatures for external audit defensibility.

---

### Reliability / Availability

- **Idempotency:** Invoice post endpoint is idempotent (POST /api/v1/sales-invoices/:id/post can be called multiple times without duplicate voucher creation, enforced via unique constraint on (invoiceID, voucherID) pair).

- **Error Handling:**
  - Validation errors: Return 400 with detailed field-level messages (e.g., `{ error: { code: 'VALIDATION_ERROR', details: { lines: [{ lineNumber: 1, field: 'vATRate', message: 'Invalid VAT rate' }] } } }`).
  - Authorization errors: Return 403 with message "Insufficient permissions."
  - Period closed: Return 409 with message "Period is closed; cannot create/post documents."
  - Idempotency: Return 409 if invoice already posted (to aid client retries).

- **Transaction Management:** All mutations use database transactions with rollback on error. Journal voucher creation rolls back invoice status update if voucher fails.

- **Graceful Degradation:** If AR Aging cache refresh fails, endpoint still returns pre-computed cache (stale, but available). Scheduled job retries with exponential backoff.

---

### Observability

- **Structured Logging:**
  - Log level DEBUG: Line-item validation details, allocation computation.
  - Log level INFO: Invoice posted, receipt allocated, approval action, import completed.
  - Log level ERROR: Validation failures, authorization denials, transaction rollbacks, cache refresh failures.
  - Format: JSON with `{ timestamp, requestId, userId, companyId, action, entity, entityId, duration, status }` for correlation.

- **Metrics:**
  - Counter: Invoices created/posted/approved/rejected per hour.
  - Counter: Receipts created/posted/reversed per hour.
  - Gauge: Current AR Aging Cache freshness (seconds since last refresh).
  - Histogram: Invoice post duration, receipt allocation duration.

- **Tracing:** Request ID propagated from API gateway through service layer to database (via `REQUEST_ID` context variable in logs).

- **Health Checks:** /api/v1/health includes database connectivity and Redis cache availability.

---

## Dependencies and Integrations

### Internal Dependencies

- **Epic 1 (Project Foundation):** Authentication (JWT), RBAC, multi-tenancy setup.
- **Epic 2 (Master Data Management):** Customer master data, bank account setup, company settings (VAT rates, approval thresholds).
- **Epic 3 (Voucher Engine):** Journal voucher creation, double-entry validation, period closure checks, account leaf-only enforcement, leaf account rules.
- **Epic 4 (AP Module):** Shared approval workflow pattern, shared import/export utilities, shared receipt/allocation logic (mirrored for AR).

### External Integrations

- **Database (PostgreSQL via Supabase):** Storage for invoices, receipts, allocations, audit logs.
- **Redis Cache:** AR Aging materialized view, report cache invalidation.
- **Email Service (configured):** Invoice delivery, approval notifications, overdue reminders (if configured).
- **n8n (optional, post-MVP):** Nightly n8n workflow to trigger AR Aging refresh and send automated reminders.

### Package Dependencies

**Backend (Maven POM.xml)**
- spring-boot-starter-data-jpa (query/persistence)
- spring-boot-starter-web (REST endpoints)
- spring-security-core (RBAC)
- postgresql (JDBC driver)
- redis-spring-data-cache (cache)
- jackson-databind (JSON serialization for audit diffs)
- commons-csv (CSV import parsing)
- apache-poi (Excel import/export)

**Frontend (package.json)**
- axios (HTTP client)
- @tanstack/react-query (caching, deduplication)
- @tanstack/react-table (DataTablePro foundation)
- react-hook-form (form validation)
- zod (schema validation)
- date-fns (date formatting, aging bucket computation)
- shadcn/ui (Button, Dialog, Form, Table, Combobox)

---

## Acceptance Criteria (Authoritative)

Extracted from PRD FR22–FR26 and Epic 5 story definitions:

| ID | Criteria | Story | Status |
|---|---|---|---|
| AC-AR-001 | Invoice form allows customer selection (typeahead/add), auto-generates invoice number (INV-{YYYY}-{seq}, unique per customer+period), supports date/due date, reference text, VND currency | 5.1 | Backlog |
| AC-AR-002 | Invoice lines support qty, unit price (both positive), VAT% (0/5/10/exempt), revenue account (leaf), item/service (if required by account rules), auto-calculated totals | 5.1 | Backlog |
| AC-AR-003 | Inline validation on all required fields; leaf-only account enforcement; save-as-draft at any time with autosave/undo/redo | 5.1 | Backlog |
| AC-AR-004 | Only creator/admin can edit/delete drafts; soft delete with timestamp; duplicate prevention (same customer + invoice number/date blocked) | 5.1 | Backlog |
| AC-AR-005 | Attachments: drag/drop, preview, delete allowed only on drafts; size/type checks; audit trail records attachment changes | 5.1 | Backlog |
| AC-AR-006 | Import (CSV/Excel): atomic, template-validated, row-level error map available for download; supports 1000+ rows per file | 5.1 | Backlog |
| AC-AR-007 | Audit log records all create/edit/post/import/delete attempts with before/after diff, actor, device/IP, and event hash | 5.1 | Backlog |
| AC-AR-008 | Approval threshold configurable by admin (default 100M VND); rule-based sensitivity flag supported | 5.2 | Backlog |
| AC-AR-009 | Above-threshold or sensitive invoices route to "Pending Approval" with in-app/email notifications to Chief Accountant | 5.2 | Backlog |
| AC-AR-010 | Approver must differ from creator; violation attempts blocked and logged; approver UI shows invoice, attachments, change history | 5.2 | Backlog |
| AC-AR-011 | Approver can approve (posts invoice) or reject (returns to draft with reason, notifies creator); approval after period close is disabled | 5.2 | Backlog |
| AC-AR-012 | If workflow not triggered (below threshold): auto-approve with shadow "auto-approved" audit record | 5.2 | Backlog |
| AC-AR-013 | Receipt form: customer picker filters to customers with open invoices; date/number auto; cash/bank account; amount; reference; attachment; method | 5.3 | Backlog |
| AC-AR-014 | Allocation UI: select one/many invoices; supports partial/prorated allocations; prevents overpayments; shows remaining per invoice | 5.3 | Backlog |
| AC-AR-015 | Standalone receipts (advances/on-account) allowed; can later match to invoices | 5.3 | Backlog |
| AC-AR-016 | Posting entries: Dr Bank/Cash (111/112), Cr AR (131) with configured dimensions; reversal generates linked reversal voucher | 5.3 | Backlog |
| AC-AR-017 | Reversal path: generates linked reversal voucher; keeps both vouchers cross-linked and audit-tagged | 5.3 | Backlog |
| AC-AR-018 | Import receipts: atomic, template-based; returns detailed error map with row numbers | 5.3 | Backlog |
| AC-AR-019 | Full audit on create/edit/post/reverse/import, including allocation changes with before/after diffs | 5.3 | Backlog |
| AC-AR-020 | AR Aging: buckets (Current, 1–30d, 31–60d, 61–90d, 91+d); by customer; totals and running balances shown | 5.4 | Backlog |
| AC-AR-021 | Drill-down from any aging cell to invoice list with paid/remaining and last-payment details | 5.4 | Backlog |
| AC-AR-022 | Export to Excel/PDF with snapshot timestamp and active filters displayed on export; excludes reversed/voided invoices | 5.4 | Backlog |
| AC-AR-023 | Overdue badges on dashboard with counts and top overdue customers list | 5.4 | Backlog |
| AC-AR-024 | Reminder actions: trigger in-app/email reminders (single/batch); configurable schedule (pre-due, due, +7d cadence) | 5.4 | Backlog |
| AC-AR-025 | RBAC: CFO/Chief see all; AR clerk sees assigned scope; API filters enforce permissions | 5.4 | Backlog |
| AC-AR-026 | Statement view: summary (one row per invoice) and detailed (including receipts/credits) with running balance | 5.5 | Backlog |
| AC-AR-027 | Export: PDF/Excel; includes legal footer and hash for audit; per-customer and batch ZIP exports | 5.5 | Backlog |
| AC-AR-028 | "Send to customer" emails statement and records delivery/view events | 5.5 | Backlog |
| AC-AR-029 | Import customer-provided reconciliation: parse/compare, flag mismatches/unapplied items, suggest adjustments; color-coded dispute tracking | 5.5 | Backlog |
| AC-AR-030 | Maintain statement history and dispute log with reasons/actions; include notes in subsequent exports | 5.5 | Backlog |
| AC-AR-031 | Invoice line VAT rate (default from settings, override with warning); supports 0/5/10/exempt only | 5.6 | Backlog |
| AC-AR-032 | GL splits on post: Dr AR (131), Cr Revenue (511+), Cr VAT Output (3331); rounding rules applied consistently | 5.6 | Backlog |
| AC-AR-033 | Totals validation: header VAT = sum of line VAT and GL VAT; block post if mismatch beyond tolerance | 5.6 | Backlog |
| AC-AR-034 | Credit notes/negative invoices supported; must reference original; linked with audit cross-references | 5.6 | Backlog |
| AC-AR-035 | Output VAT report: filter by period/customer/VAT class; Excel export in ND123 format | 5.6 | Backlog |
| AC-AR-036 | Admin VAT corrections allowed with reason and diff audit; overrides require approval if above threshold | 5.6 | Backlog |
| AC-AR-037 | Every create/edit/post/approve/reverse/import action produces audit entry with diff, actor, device/IP, event hash | 5.7 | Backlog |
| AC-AR-038 | Voucher/invoice/receipt histories show chronological diffs and human-readable summaries | 5.7 | Backlog |
| AC-AR-039 | Export of AR audits to PDF/JSON with hash signature; blocked/unauthorized actions flagged and alerted | 5.7 | Backlog |
| AC-AR-040 | Scheduled backups include AR audit logs; external copy available for DR/review; retention 10y with GDPR purge process | 5.7 | Backlog |

---

## Traceability Mapping

| PRD Requirement | Epic 5 Story | Acceptance Criteria | Implementation Component |
|---|---|---|---|
| FR22 (Customer Master Data Management) | (Inherited from Epic 2, Story 2.2) | Customer CRUD, linked to account 131 | CustomerService, CustomerController |
| FR23 (Create Sales Invoices) | Story 5.1 | AC-AR-001 to AC-AR-007 | SalesInvoiceService, SalesInvoiceController, InvoiceLineGrid (FE) |
| FR26 (Maker-Checker for AR) | Story 5.2 | AC-AR-008 to AC-AR-012 | InvoiceApprovalService, ApprovalController |
| FR24 (Customer Payments) | Story 5.3 | AC-AR-013 to AC-AR-019 | ARReceiptService, ARReceiptController, AllocationService |
| FR25 (AR Aging Report) | Story 5.4 | AC-AR-020 to AC-AR-025 | ARAgingService, ARAgingController, ARAgingDashboard (FE) |
| (Custom: Statements) | Story 5.5 | AC-AR-026 to AC-AR-030 | ARStatementService, StatementController |
| (Custom: VAT Handling) | Story 5.6 | AC-AR-031 to AC-AR-036 | ARVATService, VATCalculator |
| (Custom: Audit Trail) | Story 5.7 | AC-AR-037 to AC-AR-040 | AuditService, AuditLogController |
| NFR5 (RBAC) | (Distributed across all stories) | RBAC enforcement at API layer | Spring Security, @PreAuthorize |
| NFR8 (Audit Trail) | Story 5.7 | AC-AR-037 to AC-AR-040 | AuditLogRepository, AuditLog entity |
| NFR10 (Circular 200 Compliance) | (Distributed across all stories) | CoA structure, GL splits, double-entry | ARVATService, JournalEntryService |
| NFR15 (Vietnamese UI) | (Distributed across all stories) | Vietnamese labels, number/date formatting | Frontend i18n, MoneyInput component |

---

## Risks, Assumptions, Open Questions

### Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| **Performance degradation with large AR Aging queries** | Medium | High | Materialized AR Aging Cache with 1-hour refresh; Redis cache layer; consider read replica for reporting queries in post-MVP |
| **VAT calculation errors leading to compliance violations** | Medium | High | Comprehensive test suite for VAT scenarios (0%, 5%, 10%, EXEMPT); audit trail captures all VAT changes; manual audit review before period close |
| **Approval workflow complexity introducing latency** | Low | Medium | Async notification dispatch; threshold-based triggering to minimize approval overhead; clear UI UX for approvers |
| **Multi-invoice allocation complexity during receipt posting** | Low | High | Explicit allocation table (ARAllocation) with atomic batch; validation before post; reversal testing via E2E tests |
| **GDPR compliance gaps in audit log retention/purge** | Low | High | Legal review of audit anonymization process; scheduled purge job with audit trail; document 10-year retention policy |
| **Import file validation errors causing data corruption** | Low | High | Atomic batch insert (all succeed or all fail); pre-import CSV schema validation; error map returned to user for correction; no partial imports allowed |

### Assumptions

1. **Payment methods are recorded but not integrated with external payment gateways** (MVP assumption); bank reconciliation is manual via CSV upload.
2. **AR Aging aging is computed as of invoice due date, not invoice date** (standard accounting practice).
3. **Reversal is the only way to undo a posted invoice**; no direct edit after posting (maintains audit defensibility).
4. **Approval threshold is company-wide**; no per-customer or per-account thresholds in MVP.
5. **Single currency (VND)** per Vietnam Circular 200; multi-currency is post-MVP.
6. **Email service is configured and available** for approval notifications and statement delivery (fallback to in-app notifications if unavailable).
7. **Archive/restore of AR data follows company-wide archival policy** (defined in Epic 1); no special AR archival logic.

### Open Questions

1. **Approval Threshold Configuration:** Should approval threshold be configurable per company, or is it a global system setting? Assume per-company in this spec.
2. **VAT Rounding Rules:** What rounding rules apply when VAT is calculated? (e.g., round to nearest 100 VND, or exact?) → Defer to accounting policy document; implement as configurable tolerance in NFR.
3. **Overdue Reminder Frequency:** Should overdue reminders be sent automatically daily, or only on-demand? Assume on-demand + configurable cadence in settings; automated nightly job is post-MVP.
4. **Credit Note Workflow:** Are credit notes a separate entity or a negative-amount invoice? Assume negative-amount invoice referencing original; clarify business logic with Product Manager.
5. **Multi-Invoice Reverse:** If a payment is split across two invoices and we need to reverse one, do we reverse the entire receipt or partially? Assume partial reversal by creating a new reversal receipt with subset of allocations.
6. **Batch Email Delivery:** For "Send Statement to All Customers," is there a rate limit or batch sizing? Assume background job with queue; max 100 emails per minute to avoid spam filter issues.

---

## Comprehensive Test Strategy

### Test Pyramid & Coverage Goals

```
                  /\
                 /  \         E2E (Playwright) — 5%
                /----\        - User journeys, complex workflows
               /      \       - Invoice entry → Approval → Payment → Statement
              /--------\      - Dashboard interactions, export verification
             /          \
            /            \    Integration (Spring Boot Test) — 20%
           /              \   - API endpoints + Database transactions
          /                \  - Service layer workflows
         /                  \ - Cache invalidation, audit logging
        /                    \- Event persistence (Vouchers, Allocations)
       /______________________ \
      /                        \
     /                          \ Unit (JUnit 5 + Mockito) — 75%
    /____________________________\ - Service validators
                                  - Business logic (VAT, allocation, aging)
                                  - Entity constraints
                                  - Error scenarios
```

**Coverage Targets:**
- Line coverage: 90% of service + validator code
- Critical path coverage: 100% (invoice post, approval, receipt allocation, reversal)
- API endpoint coverage: 100% (happy path + main error cases)
- Acceptance Criteria coverage: 100% (every AC maps to ≥1 test)

---

### Unit Tests (Service Layer & Business Logic)

**Framework:** JUnit 5 + Mockito + AssertJ

#### SalesInvoiceValidator Tests

```java
// Test cases:
- test_validateInvoice_allFieldsRequired_throwsOnMissing()
- test_validateInvoice_amountPositive_throwsOnNegative()
- test_validateLine_leafAccountRequired_rejectsParentAccount()
- test_validateLine_vATRateValidation_acceptsOnly0510EXEMPT()
- test_validateLine_duplicateInvoiceDetection_preventsCreation()
- test_validateInvoice_periodClosed_blocksPosting()
- test_validateInvoice_customerExists_throwsOnUnknown()
- test_validateInvoice_lineQuantityPositive_throwsOnZero()
```

#### ARVATCalculator Tests

```java
// Test cases for all VAT rates:
- test_computeVAT_0Percent_zeroVATOnLine()
- test_computeVAT_5Percent_correctVATAmount()
- test_computeVAT_10Percent_correctVATAmount()
- test_computeVAT_EXEMPT_zeroVATOnLine()
- test_computeVAT_roundingTo100VND_appliesCorrectly()
- test_computeVAT_multipleLines_headerVATMatchesSumOfLines()
- test_computeVAT_discountReduction_appliesBeforeVAT()
- test_validateHeaderVAT_mismatch_throwsOnVarianceOver1000VND()
- test_glSplit_dr131Cr5xxCr3331_generatesCorrectSplit()
```

#### ARAllocationService Tests

```java
// Test cases:
- test_allocate_singleInvoice_full()
- test_allocate_singleInvoice_partial()
- test_allocate_multipleInvoices_split()
- test_allocate_overpaymentPrevention_throwsOnExcess()
- test_allocate_remainingBalance_computed_correctly()
- test_reverse_allocation_reverts_invoiceAmountPaid()
- test_allocate_unallocatedAdvance_allowed()
- test_allocate_constraint_sumPerReceiptLessThanOrEqualAmount()
```

#### ARAgingService Tests

```java
// Test cases:
- test_computeAgingBuckets_current_daysZero()
- test_computeAgingBuckets_1to30days_computed()
- test_computeAgingBuckets_31to60days_computed()
- test_computeAgingBuckets_61to90days_computed()
- test_computeAgingBuckets_over90days_computed()
- test_computeAgingBuckets_excludesReversedInvoices()
- test_computeAgingBuckets_excludesDrafts()
- test_computeAgingBuckets_excludesClosedPeriodInvoices()
- test_computeAgingBuckets_totalOutstanding_sumOfBuckets()
```

#### ImportParserTests (CSV/Excel)

```java
// Test cases:
- test_parseInvoiceCSV_validTemplate_parsesRows()
- test_parseInvoiceCSV_invalidHeader_throwsError()
- test_parseInvoiceCSV_rowError_accumulatesIntoErrorMap()
- test_parseInvoiceCSV_atomicValidation_rejectsAllOnAnyError()
- test_parseInvoiceCSV_1000rows_performanceUnder10s()
```

---

### Integration Tests (API Endpoints + Database Transactions)

**Framework:** Spring Boot Test + TestContainers (PostgreSQL, Redis)

#### Invoice Lifecycle Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
public class InvoiceLifecycleIT {
  
  // Scenario 1: Below-threshold invoice (auto-approve)
  test_invoiceLifecycle_belowThreshold_autoApproves() {
    // Given: Company approval threshold = 100M VND
    // When: Create invoice with totalAmount = 50M VND, post
    // Then: Status = POSTED (no approval required)
    //       Voucher created (Dr 131 / Cr 5xx)
    //       AuditLog entry: action = POST
    //       AR Aging cache invalidated
  }
  
  // Scenario 2: Above-threshold invoice (requires approval)
  test_invoiceLifecycle_aboveThreshold_requiresApproval() {
    // Given: Company approval threshold = 100M VND
    // When: Create invoice with totalAmount = 150M VND, post
    // Then: Status = PENDING_APPROVAL
    //       Notification sent to Chief Accountant
    //       Approver can approve/reject
  }
  
  // Scenario 3: Invoice approval by Chief Accountant
  test_invoiceApproval_validApprover_approvesSuccessfully() {
    // Given: Invoice status = PENDING_APPROVAL
    // When: Chief Accountant (different from creator) clicks Approve
    // Then: Status → POSTED
    //       Voucher created
    //       approvedBy + approvedAt set
    //       Creator notified
    //       AuditLog: action = APPROVE, eventHash verified
  }
  
  // Scenario 4: Invoice rejection
  test_invoiceRejection_validReason_revertsToDraft() {
    // Given: Invoice status = PENDING_APPROVAL
    // When: Chief Accountant clicks Reject with reason
    // Then: Status → DRAFT
    //       rejectionReason populated
    //       Creator notified with reason
    //       AuditLog: action = REJECT
  }
}
```

#### Receipt & Allocation Tests

```java
@SpringBootTest
public class ReceiptAllocationIT {
  
  // Scenario 1: Single invoice allocation (full payment)
  test_receipt_allocateSingleInvoice_fullPayment() {
    // Given: Posted invoice with amount 100M VND, no payments
    // When: Create receipt 100M VND, allocate full to invoice
    // Then: Status → FULLY_ALLOCATED
    //       Invoice.amountPaid = 100M, status → PAID
    //       Voucher created: Dr 111/112 / Cr 131
    //       AuditLog: allocation created
  }
  
  // Scenario 2: Multiple invoices allocation (split)
  test_receipt_allocateMultipleInvoices_split() {
    // Given: Two posted invoices (100M + 80M), unallocated receipt 150M
    // When: Allocate 100M to invoice 1, 50M to invoice 2
    // Then: Invoice 1 status → PAID
    //       Invoice 2 status → PARTIALLY_PAID (30M remaining)
    //       Receipt status → PARTIALLY_ALLOCATED (no allocation for 50M)
    //       Allocation table shows 2 rows
  }
  
  // Scenario 3: Receipt reversal
  test_receipt_reversal_crossLinked() {
    // Given: Posted receipt with allocations to 2 invoices
    // When: Click Reverse
    // Then: Reversal receipt created (negative amount)
    //       Original allocations marked isReversed = true
    //       Invoice amountPaid reverted
    //       Cross-links set: original.reversingReceiptID, reversal.reversalReceiptID
    //       Reversal voucher created (opposite debit/credit)
    //       AuditLog: cross-reference logged
  }
}
```

#### Import Workflows Tests

```java
@SpringBootTest
public class ImportWorkflowIT {
  
  // Scenario 1: Valid invoice import (atomic success)
  test_invoiceImport_validCSV_atomicInsert() {
    // Given: CSV file with 10 valid invoices
    // When: POST /api/v1/sales-invoices/import
    // Then: All 10 invoices created in DRAFT status
    //       AuditLog entries: action = IMPORT
    //       Response: importID, importedCount = 10, errorMap = {}
  }
  
  // Scenario 2: Invalid import (atomic rejection)
  test_invoiceImport_invalidCSV_rejectsAll() {
    // Given: CSV file with 10 rows, row 5 has invalid customer code
    // When: POST /api/v1/sales-invoices/import
    // Then: All rows rejected (atomic failure)
    //       No invoices created
    //       Response: errorMap includes row 5 with message
    //       Accountant downloads error map, fixes, retries
  }
}
```

#### Approval Workflow Tests (RBAC)

```java
@SpringBootTest
public class ApprovalRBACIT {
  
  // Test that approver must differ from creator
  test_invoice_approval_selfApprovalBlocked() {
    // Given: Creator = Accountant A, invoice above threshold
    // When: Accountant A attempts to approve own invoice
    // Then: 403 Forbidden "Cannot approve your own invoice"
    //       AuditLog: failed approval attempt logged
  }
  
  // Test that only Chief Accountant can approve
  test_invoice_approval_wrongRoleBlocked() {
    // Given: Invoice PENDING_APPROVAL, user is Accountant (not Chief)
    // When: Accountant attempts to approve
    // Then: 403 Forbidden "Only Chief Accountant can approve"
  }
  
  // Test that period closure blocks approval
  test_invoice_approval_closedPeriodBlocked() {
    // Given: Invoice invoiceDate in closed period (period_closed = true)
    // When: Chief Accountant attempts to approve
    // Then: 409 Conflict "Period is closed"
  }
}
```

#### Cache Invalidation Tests

```java
@SpringBootTest
public class CacheInvalidationIT {
  
  test_arAgingCache_invalidatedOnInvoicePost() {
    // Given: AR Aging cache populated and valid (TTL 1h)
    // When: New invoice posted
    // Then: Cache key (asOfDate + companyID) invalidated
    //       Next GET /ar-aging recomputes from database
  }
  
  test_arAgingCache_invalidatedOnReceiptPost() {
    // Given: AR Aging cache populated
    // When: Receipt posted with allocations
    // Then: Cache invalidated
    //       Subsequent query reflects new allocations
  }
}
```

---

### E2E Tests (UI + API)

**Framework:** Playwright + TypeScript

#### Invoice Form Workflow

```gherkin
Scenario: Create and post invoice with multiple line items
  Given: User logged in as Accountant
  When: Navigate to "New Invoice"
       Select customer "ABC Corp" via typeahead
       Add line 1: desc="Service A", qty=5, price=1000, VAT=10%
       Add line 2: desc="Service B", qty=3, price=2000, VAT=5%
       Verify totals: invoiceAmount=13000, VAT=850
       Click "Save as Draft"
  Then: Invoice saved, invoiceNumber auto-generated (INV-2025-001)
       Status = DRAFT
       Can click "Edit" to modify
       
  When: Click "Post"
       System final validation passes
  Then: Status = POSTED (auto-approved, below threshold)
       Notification: "Invoice posted successfully"
       Voucher created (verified in audit trail)
       AR Aging dashboard updated within 5s
```

#### Receipt Allocation Workflow

```gherkin
Scenario: Allocate receipt to multiple invoices
  Given: User logged in as Accountant
         Customer has 3 open invoices (100M, 80M, 50M)
  When: Navigate to "New Receipt"
       Select customer
       Enter amount 200M
       Click "Auto-allocate"
  Then: System suggests allocation: 100M to invoice 1, 80M to invoice 2, 20M to invoice 3
       User reviews allocation grid
       
  When: Click "Post"
  Then: Voucher created: Dr 111/112 (200M) / Cr 131 (200M)
       Invoice 1 status = PAID
       Invoice 2 status = PAID
       Invoice 3 status = PARTIALLY_PAID (30M unpaid)
       Receipt allocationStatus = FULLY_ALLOCATED
```

#### AR Aging Dashboard Drill-Down

```gherkin
Scenario: View AR aging and drill down to invoices
  Given: User logged in as CFO
  When: Open "AR Aging Dashboard"
  Then: Grid displays: Customer Name | Current | 1-30d | 31-60d | 61-90d | 91+d | Total
       Data loads in <100ms (cache hit)
       
  When: Click cell "61-90d" for customer "XYZ Ltd"
  Then: Drill-down shows invoices in 61-90 days bucket:
        InvoiceNumber | Date | Outstanding | DaysOverdue | Status
        
  When: Click invoice "INV-2024-0045"
  Then: Detail view shows: lines, customer, payment history (receipts applied)
```

#### AR Statement & Reconciliation

```gherkin
Scenario: Generate statement and process customer reconciliation
  Given: User logged in as Accountant, customer selected
  When: Navigate to "Customer Statements"
       Click "Generate Statement"
       Select format "Detailed"
  Then: Statement displayed with invoices + receipts + running balance
       
  When: Click "Export PDF"
  Then: PDF generated with legal footer + hash
       Filename: "Statement_ABC_Corp_2025-01-31.pdf"
       
  When: Customer uploads reconciliation CSV
       System parses and compares
  Then: Grid shows matched rows (green) and mismatched rows (red)
       Mismatch example: system 100M, customer claims 95M (variance 5M)
       Dispute log created; accountant notes reason and marks RESOLVED
```

---

### Performance Tests

**Framework:** JMeter or Gatling

#### Query Performance

```java
// Goal: Response times P95
- test_invoiceListQuery_1000invoices_under500ms()
  - Query: GET /api/v1/sales-invoices with pagination
  - Expected: <500ms P95
  - Index validation: (companyID, status, invoiceDate)

- test_arAgingQuery_cacheHit_under100ms()
  - Query: GET /api/v1/ar-aging (cache hit)
  - Expected: <100ms
  - Setup: Warm cache via initial query

- test_arAgingQuery_cacheMiss_under1s()
  - Query: GET /api/v1/ar-aging (cache miss)
  - Expected: <1s (recompute from scratch)

- test_reportExportExcel_1000invoices_under3s()
  - Query: GET /api/v1/ar-aging/export?format=EXCEL
  - Expected: <3s
```

#### Concurrent Load

```java
- test_concurrentInvoicePosts_20concurrent_noLocks()
  - Setup: 20 simultaneous invoice POST requests
  - Expected: 0% error rate, P95 latency <2s
  - Validation: No transaction deadlocks

- test_concurrentReceiptAllocations_multiInvoice()
  - Setup: 10 receipts, each allocating to 5 different invoices
  - Expected: All succeed atomically
```

---

### Security Tests

**Framework:** Spring Security Test

#### Authorization (RBAC) Tests

```java
@SpringBootTest
public class RBACSecurityIT {
  
  // Accountant cannot approve
  test_approveEndpoint_forbiddenForAccountant() {
    // Given: User role = ACCOUNTANT
    // When: POST /api/v1/sales-invoices/:id/approve
    // Then: 403 Forbidden "Insufficient permissions"
  }
  
  // CFO cannot create invoices
  test_createInvoiceEndpoint_forbiddenForCFO() {
    // Given: User role = CFO
    // When: POST /api/v1/sales-invoices
    // Then: 403 Forbidden
  }
  
  // Chief Accountant cannot view other company's invoices
  test_getInvoiceEndpoint_otherCompanyBlocked() {
    // Given: User companyID = A, invoice companyID = B
    // When: GET /api/v1/sales-invoices/:id
    // Then: 404 Not Found (RLS enforced)
  }
}
```

#### Audit Trail Integrity Tests

```java
@SpringBootTest
public class AuditTrailSecurityIT {
  
  // Audit log cannot be modified
  test_auditLog_immutable_blockUpdate() {
    // Given: Existing AuditLog entry
    // When: Attempt UPDATE or DELETE
    // Then: Database constraint violation (immutability enforced)
  }
  
  // Event hash tamper detection
  test_auditLog_eventHash_detectsTamper() {
    // Given: AuditLog with eventHash
    // When: Someone modifies beforeSnapshot (if mutable, which it shouldn't be)
    // Then: Hash mismatch detected on validation
  }
  
  // Every mutation logged
  test_invoiceCreate_alwaysLogged() {
    // Given: Invoice created
    // When: Query AuditLog for this invoice
    // Then: Entry exists with action=CREATE, timestamp, actor
  }
}
```

---

### Compliance Tests

**Framework:** Custom assertions, CPA sign-off

#### Circular 200 Compliance

```java
@SpringBootTest
public class Circular200ComplianceIT {
  
  // GL structure verification
  test_chartOfAccounts_structurePerCircular200() {
    // Verify accounts exist: 111 (Cash), 131 (AR), 511-519 (Revenue), 3331 (VAT Output)
  }
  
  // Leaf-only posting enforcement
  test_invoice_postingLeafAccountsOnly() {
    // Given: Invoice with line using parent account (e.g., "5" instead of "511")
    // When: Post invoice
    // Then: Validation error "Account must be leaf"
  }
  
  // GL split accuracy
  test_invoicePost_glSplitPerCircular200() {
    // Example: 100M VND revenue, 10% VAT
    // Expected GL: Dr 131 (100), Cr 511 (90), Cr 3331 (10)
    // Verify: Sum(Dr) = Sum(Cr) = 100
  }
}
```

#### Double-Entry Validation

```java
@SpringBootTest
public class DoubleEntryIT {
  
  // Every posted invoice has balanced voucher
  test_invoicePost_voucherBalanced() {
    // Given: Posted invoice
    // When: Query Voucher by invoiceID
    // Then: Σ(debit) = Σ(credit) = invoice.totalAmount
  }
  
  // Every posted receipt has balanced voucher
  test_receiptPost_voucherBalanced() {
    // Given: Posted receipt
    // When: Query Voucher by receiptID
    // Then: Σ(debit) = Σ(credit) = receipt.amount
  }
}
```

#### Period Closure Enforcement

```java
@SpringBootTest
public class PeriodClosureIT {
  
  // Cannot post invoice in closed period
  test_invoicePost_blockedInClosedPeriod() {
    // Given: Period 2024-12 is closed (period_closed = true)
    // When: POST invoice with invoiceDate = 2024-12-15
    // Then: 409 Conflict "Period is closed"
  }
}
```

---

### Test Data & Fixtures

**Setup Strategy:** Spring Boot @DataSet or custom fixtures

```java
@SpringBootTest
public class BaseIT {
  
  @BeforeEach
  void setupTestData() {
    // Create company: "Test Company", approval threshold = 100M VND
    // Create customers: "ABC Corp", "XYZ Ltd"
    // Create GL accounts: 111 (Cash), 131 (AR), 511 (Revenue), 3331 (VAT Output)
    // Create users: Accountant, Chief Accountant, CFO, Admin
    // Create open period: 2025-01 (period_closed = false)
  }
}
```

---

### Test Execution & CI/CD

**Running Tests:**

```bash
# Unit tests (fast, <2min)
mvn test -Dtest="*Test"

# Integration tests (with containers, <5min)
mvn test -Dtest="*IT"

# E2E tests (Playwright, <10min)
npx playwright test --project=chromium

# Full suite (sequential, <20min)
mvn verify && npx playwright test

# Performance tests (separate, on-demand)
jmeter -n -t performance-suite.jmx -l results.jtl
```

**CI/CD Gate:**
- All unit tests must pass
- Coverage >90% on services/validators
- All integration tests must pass
- E2E tests on critical workflows (invoice post, approval, statement)
- Performance tests baseline established, regressions flagged

---

### Test Coverage by Acceptance Criteria

| AC ID | Unit Test | Integration Test | E2E Test | Performance Test |
|---|---|---|---|---|
| AC23-001 | — | test_invoiceCreate_customerTypeahead | — | — |
| AC23-002 | test_invoiceNumber_uniquePerCustomerYear | test_invoiceCreate_duplicate_prevented | — | — |
| AC23-003 | test_invoiceLine_autoCalculates | test_invoiceCreate_multiLine | Create invoice form | — |
| AC23-004 | test_leafAccountValidation | test_invoiceCreate_rejectsParent | — | — |
| AC23-005 | test_headerVATvalidation | test_invoiceCreate_vatValidation | — | — |
| AC26-002 | test_approvalThreshold | test_invoicePost_aboveThreshold | — | — |
| AC26-003 | test_autoApproveBelow | test_invoicePost_autoApprove | — | — |
| AC26-006 | test_approvalVoucherCreation | test_invoiceApprove_voucherCreated | Approval workflow | — |
| AC25-001 | test_agingBucketComputation | test_arAging_bucketsCorrect | — | test_arAging_cacheHit_under100ms |
| AC24-004 | test_singleAllocation | test_receiptAllocate_single | — | — |
| AC24-005 | test_multiAllocation | test_receiptAllocate_multi | Receipt allocation form | — |
| AC-AUDIT-001 | test_auditLogCreated | test_create_logsAudit | — | — |

---

---

## Appendices

### A. Terminology

- **Accounts Receivable (AR):** Money owed to the company by customers for goods/services delivered.
- **Sales Invoice:** Document issued to customer detailing goods/services provided, amount owed, and due date.
- **Receipt:** Payment received from customer, allocated to one or more invoices.
- **Allocation:** Matching of receipt to invoice(s), recording which invoice the payment applies to.
- **Aging Bracket:** Time period category (Current, 1–30 days, etc.) for aging analysis.
- **Reversal:** Opposite journal entry to undo a posted invoice/receipt, maintaining audit trail.
- **Maker-Checker:** Approval workflow where one user (Maker) creates document, another (Checker) approves before posting.
- **VAT (Value Added Tax):** Output VAT on sales, input VAT on purchases, per Vietnamese tax law.
- **Circular 200:** Vietnamese accounting standard defining CoA structure, reporting format, and compliance rules.

### B. Circular 200 Account Codes Referenced

- **131:** Accounts Receivable (Công nợ khách hàng)
- **511–519:** Revenue/Sales accounts (Doanh thu bán hàng)
- **3331:** VAT Output (VAT đầu ra)
- **111:** Cash (Tiền mặt)
- **112:** Bank accounts (Tiền gửi ngân hàng)

### C. ND123 VAT Report Format

Output VAT report per Circular 200, exported in standard Excel format with columns:
- Invoice Number
- Invoice Date
- Customer Name / Tax Code
- Revenue (0% VAT, 5% VAT, 10% VAT, Exempt)
- Output VAT (total)

---

**Document Status:** ✅ Draft (Ready for Review)  
**Last Updated:** 2025-11-20  
**Next Steps:** 1. Review by Product Manager & Tech Lead; 2. Story grooming for Sprint 1; 3. Development kickoff after Epic 4 completion
