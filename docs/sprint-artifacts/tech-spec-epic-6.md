# Epic Technical Specification: Cash & Bank Management

Date: 2025-11-26
Author: thanhtoan
Epic ID: epic-6
Status: Draft

---

## Overview

Epic 6 delivers a comprehensive Cash & Bank Management module for the enterprise accounting ERP system. This module enables robust management of cash and bank accounts, receipt/payment entry with GL posting, cash/bank book views with running balances, bank statement reconciliation, and complete audit compliance. Building on Epic 5 (AR Module) and reusing patterns from Epic 4 (AP Module), this module consolidates all cash-related transactions into a unified, auditable system aligned with Circular 200/2014/TT-BTC (Vietnamese accounting standard).

The module addresses critical business requirements for cash flow visibility, controlled disbursements, and bank reconciliation—ensuring cash movements are accurate, timely, and fully traceable. Primary users are accountants managing day-to-day cash operations, and chief accountants/auditors requiring comprehensive audit trails and reconciliation tools.

---

## Objectives and Scope

### In-Scope

- **Story 6.1 (Cash/Bank Account Management):** Full CRUD for cash/bank accounts with validations (account number uniqueness, type enforcement), inactivation workflow, RBAC enforcement, import capability, and opening balance lock after first period close.
- **Story 6.2 (Cash Receipt Entry & Posting):** Receipt form with payer selection, AR allocation (invoice linkage), standalone receipts, posting to GL (Dr Cash/Bank, Cr AR 131 or Other Income 711), reversal with cross-linking, batch import, and audit logging.
- **Story 6.3 (Cash Payment Entry & Posting):** Payment form with payee selection, AP allocation (bill linkage), standalone expense payments, posting to GL (Cr Cash/Bank, Dr AP 331 or Expense 6xx/8xx), balance guard, maker-checker workflow, and audit logging.
- **Story 6.4 (Bank/Cash Book View):** Per-account transaction ledger with running balances, drill-down to voucher details, multi-account aggregation, export to Excel/PDF with branding, pagination/virtual scrolling, and RBAC-enforced queries.
- **Story 6.5 (Manual Bank Reconciliation):** Statement import with column mapping wizard, duplicate detection, auto-match suggestions (date/amount/reference), manual match/unmatch, adjustment voucher creation, reconciliation status tracking, and audit logging.
- **Story 6.6 (Cash & Bank Audit and Compliance):** Closed period protection, scheduled backup/export, integrity checks (Dr/Cr parity, anomaly detection), export controls with hashing/watermarking, audit explorer, and notification center integration.

### Out-of-Scope

- **Automated Bank Feeds:** Direct API integration with banks for automatic statement download (deferred to post-MVP).
- **Multi-Currency Support:** Single VND currency per Circular 200; multi-currency is post-MVP.
- **Payment Gateway Integration:** Stripe/PayPal/VNPay integrations for automated payment processing (deferred).
- **Advanced Treasury:** Cash pooling, inter-company transfers, investment management (deferred).
- **Predictive Cash Flow:** AI-powered cash flow forecasting (addressed in Epic 8 – BI Dashboard).

---

## System Architecture Alignment

Epic 6 aligns with the established Spring Boot 3.5.7 + PostgreSQL + React/TypeScript Vite stack:

1. **Multi-Tenant Row-Level Security (RLS):** All Cash/Bank entities (`cash_receipts`, `cash_payments`, `bank_reconciliations`) inherit `CompanyScopedEntity` with automatic `company_id` filtering at JPA repository and Supabase RLS policy levels.

2. **JWT Authentication & RBAC:** Spring Security filters enforce role-based permissions:
   - **Admin/Chief Accountant:** Full CRUD on accounts, approvals, reversals, reconciliation, and audit log export.
   - **Accountant:** Create/view receipts/payments, post below threshold, cannot approve above threshold.
   - **CFO:** View-only cash books, reconciliation status, and reports.

3. **Existing Entity Leverage:** The `BankAccount` entity already exists (Epic 2, Story 2.4) with fields: `id`, `companyId`, `accountNumber`, `bankName`, `branch`, `type` (CASH/BANK), `openingBalance`, `active`, `createdAt`, `updatedAt`. Epic 6 extends this with balance tracking and reconciliation status.

4. **Voucher Engine Integration:** Cash receipts and payments automatically post journal entries via the existing `VoucherService` from Epic 3, generating double-entry vouchers with dimension validation, leaf-only account enforcement, and period closure checks.

5. **API Contract Compliance:** RESTful `/api/v1/cash-receipts`, `/api/v1/cash-payments`, `/api/v1/cash-book`, `/api/v1/bank-reconciliation` endpoints following the standard response wrapper (`{ data, meta, error }`) with pagination, filtering, and error codes.

6. **Audit Trail Integration:** Every Cash/Bank action logs to the centralized `AuditLog` table with before/after diffs, actor metadata, device/IP capture, and HMAC-SHA256 event hashing for integrity verification.

7. **Report Cache Strategy:** Cash/Bank book queries are optimized with indexed queries on `(company_id, account_id, transaction_date)` and optional Redis caching for frequently accessed balance summaries.

8. **UI Component Leverage:** Frontend uses existing shadcn/ui foundations and custom components (AccountPicker, MoneyInput, DataTablePro) with additions for reconciliation matching UI.

---

## Detailed Design

### Services and Modules

#### Existing Services (Reused/Enhanced)

| Service | Status | Epic 6 Enhancements |
|---------|--------|---------------------|
| **BankAccountServiceImpl** | ✅ Exists (Epic 2) | Add balance calculation, opening balance lock, reconciliation status tracking |
| **ReceiptService** | ✅ Exists (Epic 5) | Reuse for Story 6.2 cash receipt entry; API: `/api/v1/ar/receipts` |
| **APPaymentService** | ✅ Exists (Epic 4) | Reuse for Story 6.3 cash payment entry; API: `/api/v1/ap/payments` |
| **ReceiptImportService** | ✅ Exists (Epic 5) | Reuse for batch import |
| **AuditService** | ✅ Exists (Core) | Reuse for all Cash/Bank audit logging |

#### New Services (Epic 6 Specific)

| Service / Module | Responsibility | Key Classes | Dependencies |
|------------------|----------------|-------------|--------------|
| **CashBookService** | Query transaction ledger, compute running balances, export cash/bank books | `CashBookService`, `CashBookQueryBuilder`, `CashBookExportService` | VoucherRepository, JournalEntryRepository, BankAccountService |
| **BankReconciliationService** | Import statements, auto-match, manual match/unmatch, create adjustments, track reconciliation status | `BankReconciliationService`, `StatementImportService`, `ReconciliationMatcher` | BankAccountService, VoucherService, AuditService |
| **CashAuditService** | Cash-specific audit queries, integrity checks, export controls, anomaly detection | `CashAuditService`, `IntegrityCheckService` | AuditService |

**Architecture Pattern:** Service layer + Repository (Spring Data JPA) + Validator pattern; aggregates manage their own state transitions (e.g., prevent posting an already-reversed receipt).

---

### Data Models and Contracts

> **Database Reuse Summary:**
> - ✅ **Reuse existing:** `ar_payments`, `ap_payments`, `transaction_allocations`, `bank_accounts`
> - 🔧 **Enhance:** `bank_accounts` (add 4 new columns)
> - ➕ **Create new:** `bank_reconciliations`, `bank_statement_lines`, `reconciliation_adjustments`

#### Enhanced Existing Entity

**BankAccount** (enhanced from Epic 2)
```
PK: id (Long)
Fields:
  - companyId: Long (FK → Company, inherited from CompanyScopedEntity)
  - accountNumber: String (required, unique per company, max 50)
  - bankName: String (required, max 255)
  - branch: String (optional, max 255)
  - type: enum (CASH, BANK)
  - openingBalance: BigDecimal (required, default 0, precision 19/2)
  - active: Boolean (default true)
  - currency: String (default 'VND', fixed for MVP)
  - glAccountCode: String (FK → ChartOfAccounts, e.g., '1111' for cash, '1121' for bank) ← NEW
  - openingBalanceLocked: Boolean (default false) ← NEW
  - lastReconciledDate: LocalDate (nullable) ← NEW
  - lastReconciledBalance: BigDecimal (nullable) ← NEW
  - createdAt: Instant
  - updatedAt: Instant

Constraints:
  - UNIQUE(companyId, accountNumber)
  - CHECK(openingBalance >= 0)
  - glAccountCode must map to leaf account in COA
```

#### Existing Entities (Reused from Epic 4 & 5)

> **Note:** Epic 6 reuses existing tables from previous epics. No new receipt/payment tables are needed.

**ar_payments** (Existing - Used for Cash Receipts)
```
Table: accounting.ar_payments (Created in Epic 5)
Entity: ARPayment

Key Fields (already implemented):
  - id: UUID (PK)
  - company_id, customer_id
  - receipt_number (auto-generated)
  - receipt_date
  - cash_account_id, bank_account_id ← Links to bank_accounts
  - payee, amount, reference, payment_method
  - is_standalone ← Supports standalone receipts (Other Income)
  - status: DRAFT | POSTED | REVERSED
  - linked_voucher_id ← Links to GL voucher
  - original_receipt_id, reversing_receipt_id ← Reversal cross-linking

Epic 6 Usage:
  - Story 6.2 uses this table for cash receipt entry
  - No schema changes needed; existing functionality sufficient
  - API: /api/v1/ar/receipts (already implemented)
```

**ap_payments** (Existing - Used for Cash Payments)
```
Table: accounting.ap_payments (Created in Epic 4)
Entity: APPayment

Key Fields (already implemented):
  - id: UUID (PK)
  - company_id, supplier_id
  - payment_number (auto-generated)
  - payment_date, due_date
  - cash_account_id, bank_account_id ← Links to bank_accounts
  - payee, amount, reference, payment_method
  - is_standalone ← Supports standalone expense payments
  - status: DRAFT | PENDING_APPROVAL | POSTED | REJECTED | REVERSED
  - approved_by_id ← Maker-checker workflow support
  - linked_voucher_id ← Links to GL voucher

Epic 6 Usage:
  - Story 6.3 uses this table for cash payment entry
  - No schema changes needed; existing functionality sufficient
  - API: /api/v1/ap/payments (already implemented)
```

**transaction_allocations** (Existing - Unified Allocation Table)
```
Table: accounting.transaction_allocations (Consolidated in V20251126005)
Entity: ReceiptAllocation / PaymentAllocation (polymorphic via transaction_type)

Key Fields:
  - id: UUID (PK)
  - company_id
  - transaction_type: 'RECEIPT' | 'PAYMENT'
  - transaction_id: UUID (FK → ar_payments or ap_payments)
  - document_type: 'SALES_INVOICE' | 'PURCHASE_BILL'
  - document_id: UUID (FK → sales_invoices or purchase_bills)
  - allocated_amount
  - allocation_order

Epic 6 Usage:
  - Receipt allocations to invoices (transaction_type='RECEIPT')
  - Payment allocations to bills (transaction_type='PAYMENT')
  - No schema changes needed
```

---

#### New Entities (Epic 6 Specific)

**BankReconciliation**
```
PK: id (UUID)
Fields:
  - companyId: Long (FK → Company)
  - bankAccountId: Long (FK → BankAccount)
  - statementPeriodStart: LocalDate
  - statementPeriodEnd: LocalDate
  - statementBalance: BigDecimal
  - ledgerBalance: BigDecimal
  - reconciledBalance: BigDecimal
  - status: enum (NOT_STARTED, IN_PROGRESS, COMPLETED)
  - statementFileUrl: String (nullable)
  - statementFileHash: String (nullable, SHA256 of uploaded file)
  - notes: String (optional)
  - completedAt: Instant (nullable)
  - completedById: Long (FK → User, nullable)
  - createdAt: Instant
  - updatedAt: Instant

Constraints:
  - UNIQUE(companyId, bankAccountId, statementPeriodStart, statementPeriodEnd)
```

**BankStatementLine**
```
PK: id (UUID)
FK: reconciliationId (UUID → BankReconciliation, ON DELETE CASCADE)
Fields:
  - lineNumber: Integer
  - transactionDate: LocalDate
  - description: String
  - reference: String (nullable)
  - debitAmount: BigDecimal (nullable)
  - creditAmount: BigDecimal (nullable)
  - balance: BigDecimal (nullable, running balance from statement)
  - matchStatus: enum (UNMATCHED, MATCHED, ADJUSTMENT_REQUIRED)
  - matchedVoucherId: UUID (FK → Voucher, nullable)
  - matchedAt: Instant (nullable)
  - matchedById: Long (FK → User, nullable)
  - matchConfidence: BigDecimal (nullable, 0-1 for auto-match suggestions)
  - matchReason: String (nullable, explains why auto-matched)
  - notes: String (nullable)

Constraints:
  - CHECK(debitAmount IS NOT NULL OR creditAmount IS NOT NULL)
```

**ReconciliationAdjustment**
```
PK: id (UUID)
FK1: reconciliationId (UUID → BankReconciliation)
FK2: statementLineId (UUID → BankStatementLine, nullable)
Fields:
  - adjustmentType: enum (BANK_FEE, INTEREST_INCOME, INTEREST_EXPENSE, OTHER)
  - amount: BigDecimal
  - description: String
  - accountCode: String (FK → ChartOfAccounts)
  - voucherId: UUID (FK → Voucher, created after posting)
  - status: enum (PENDING, APPROVED, POSTED)
  - createdById: Long (FK → User)
  - createdAt: Instant

Constraints:
  - CHECK(amount > 0)
```

---

### APIs and Interfaces

#### Bank Account Management

```
GET    /api/v1/bank-accounts
       Query: type=CASH|BANK&status=active|inactive&search=...&sort=balance|lastActivity
       Response: { data: { content: [BankAccountDTO], totalElements, totalPages }, meta: {...} }

GET    /api/v1/bank-accounts/:id
       Response: { data: BankAccountDTO, meta: {...} }

GET    /api/v1/bank-accounts/:id/balance-tooltip
       Response: { data: { currentBalance, lastTxDate, lastReconciledDate }, meta: {...} }

POST   /api/v1/bank-accounts
       Request: { accountNumber, bankName, branch, type, openingBalance, glAccountCode }
       Authorization: Admin, Chief Accountant only
       Validation: accountNumber unique per company, glAccountCode is leaf account

PUT    /api/v1/bank-accounts/:id
       Request: { bankName, branch, openingBalance }
       Validation: openingBalance editable only if openingBalanceLocked=false

PATCH  /api/v1/bank-accounts/:id/deactivate
       Authorization: Admin, Chief Accountant only
       Validation: Cannot deactivate if referenced in unposted transactions
       Side Effects: Create audit log

PATCH  /api/v1/bank-accounts/:id/activate
       Authorization: Admin, Chief Accountant only
       Side Effects: Create audit log with reason

GET    /api/v1/bank-accounts/export
       Query: format=CSV|EXCEL&type=CASH|BANK&status=...
       Response: Binary file
```

#### Cash Receipts (Existing API - Epic 5)

> **Note:** Uses existing `/api/v1/ar/receipts` endpoint from Epic 5. No new endpoints needed.

```
# EXISTING ENDPOINTS (from Epic 5 - ReceiptController)

GET    /api/v1/ar/receipts
       Query: page, size, status, dateFrom, dateTo, customerId, bankAccountId, search
       Response: { data: { content: [ARPaymentDTO], totalElements, totalPages }, meta: {...} }

GET    /api/v1/ar/receipts/:id
       Response: { data: ARPaymentDTO with allocations }, meta: {...} }

POST   /api/v1/ar/receipts
       Request: { receiptDate, customerId, bankAccountId/cashAccountId, amount, reference, paymentMethod, 
                  isStandalone, allocations: [{ salesInvoiceId, allocatedAmount }] }
       Response: { data: { id, receiptNumber, status: 'DRAFT' }, meta: {...} }

PUT    /api/v1/ar/receipts/:id
       Request: { amount, reference, allocations }
       Validation: status must be DRAFT

POST   /api/v1/ar/receipts/:id/post
       Side Effects: Create voucher Dr 1111/1121 / Cr 131 or 711

POST   /api/v1/ar/receipts/:id/reverse
       Request: { reason }

POST   /api/v1/ar/receipts/import
       Content-Type: multipart/form-data
```

#### Cash Payments (Existing API - Epic 4)

> **Note:** Uses existing `/api/v1/ap/payments` endpoint from Epic 4. No new endpoints needed.

```
# EXISTING ENDPOINTS (from Epic 4 - APPaymentController)

GET    /api/v1/ap/payments
       Query: page, size, status, dateFrom, dateTo, supplierId, bankAccountId, search
       Response: { data: { content: [APPaymentDTO], totalElements, totalPages }, meta: {...} }

GET    /api/v1/ap/payments/:id
       Response: { data: APPaymentDTO with allocations }, meta: {...} }

POST   /api/v1/ap/payments
       Request: { paymentDate, supplierId, bankAccountId/cashAccountId, amount, reference, paymentMethod,
                  isStandalone, allocations: [{ purchaseBillId, allocatedAmount }] }

PUT    /api/v1/ap/payments/:id

POST   /api/v1/ap/payments/:id/post
       Side Effects: Create voucher Cr 1111/1121 / Dr 331 or 6xx

POST   /api/v1/ap/payments/:id/approve
       Authorization: Chief Accountant, approver ≠ creator

POST   /api/v1/ap/payments/:id/reject
       Request: { rejectionReason }

POST   /api/v1/ap/payments/:id/reverse

POST   /api/v1/ap/payments/import
       Content-Type: multipart/form-data
```

#### Cash/Bank Book (NEW API - Epic 6)

```
GET    /api/v1/cash-book/:bankAccountId
       Query: dateFrom, dateTo, type=receipt|payment|all, reference, page, size
       Response: { 
         data: { 
           account: BankAccountDTO,
           openingBalance: BigDecimal,
           transactions: [{ date, voucherNumber, description, reference, debit, credit, runningBalance }],
           closingBalance: BigDecimal,
           totalInflow: BigDecimal,
           totalOutflow: BigDecimal
         }, 
         meta: {...} 
       }

GET    /api/v1/cash-book/:bankAccountId/transactions/:voucherId
       Response: { data: VoucherDetailDTO with lines and attachments }, meta: {...} }

GET    /api/v1/cash-book/summary
       Query: dateFrom, dateTo, accountIds (comma-separated)
       Response: { 
         data: { 
           accounts: [{ accountId, accountName, type, openingBalance, totalInflow, totalOutflow, closingBalance }],
           grandTotals: { totalInflow, totalOutflow }
         }, 
         meta: {...} 
       }

GET    /api/v1/cash-book/:bankAccountId/export
       Query: format=EXCEL|PDF, dateFrom, dateTo
       Response: Binary file with company branding, filter snapshot, timestamp, generated-by, document hash
       Headers: Content-Disposition: attachment; filename="CashBook_{AccountNumber}_{DateRange}.xlsx"
       Side Effects: Create audit log for export action
```

#### Bank Reconciliation

```
GET    /api/v1/bank-reconciliations
       Query: bankAccountId, status, dateFrom, dateTo, page, size
       Response: { data: { content: [BankReconciliationListDTO], totalElements, totalPages }, meta: {...} }

GET    /api/v1/bank-reconciliations/:id
       Response: { data: BankReconciliationDTO with statementLines and adjustments }, meta: {...} }

POST   /api/v1/bank-reconciliations
       Request: { bankAccountId, statementPeriodStart, statementPeriodEnd, statementBalance }
       Validation: No overlapping reconciliation period for account
       Response: { data: { id, status: 'IN_PROGRESS' }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/import-statement
       Content-Type: multipart/form-data
       Request: FormData with CSV/Excel file + columnMapping: { dateColumn, descriptionColumn, referenceColumn, debitColumn, creditColumn, balanceColumn }
       Validation: Duplicate file detection via hash, date range overlap check
       Side Effects: Parse file, create BankStatementLine records, trigger auto-match
       Response: { data: { lineCount, autoMatchedCount, unmatchedCount }, meta: {...} }

GET    /api/v1/bank-reconciliations/:id/column-mapping-suggestions
       Query: fileUrl (uploaded file reference)
       Response: { data: { suggestedMapping, sampleRows: [first 5 rows] }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/auto-match
       Request: { dateToleranceDays: 3, amountTolerancePercent: 0 }
       Response: { data: { matchedCount, suggestions: [{ lineId, voucherId, confidence, reason }] }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/match
       Request: { matches: [{ statementLineId, voucherId }] }
       Validation: voucherId is unmatched, amounts compatible
       Response: { data: { matchedCount }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/unmatch
       Request: { statementLineIds: [...] }
       Response: { data: { unmatchedCount }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/adjustments
       Request: { statementLineId?, adjustmentType, amount, description, accountCode }
       Response: { data: { adjustmentId, status: 'PENDING' }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/adjustments/:adjustmentId/approve
       Authorization: Chief Accountant
       Side Effects: Create adjustment voucher, update adjustment status to POSTED
       Response: { data: { voucherId, status: 'POSTED' }, meta: {...} }

POST   /api/v1/bank-reconciliations/:id/complete
       Validation: All lines matched or have adjustments, balances reconcile
       Side Effects: Update status to COMPLETED, update account.lastReconciledDate and lastReconciledBalance
       Response: { data: { status: 'COMPLETED', reconciledBalance }, meta: {...} }

GET    /api/v1/bank-reconciliations/:id/export
       Query: format=EXCEL|PDF
       Response: Binary file (matched items, unmatched items, adjustments, summary)
```

---

### Workflows and Sequencing

#### Workflow 1: Cash Receipt Entry → AR Allocation → Posting

```
flowchart TD
  A["Accountant: New Cash Receipt Form"] -->|Select payer (Customer/Other)| B["System: Load open invoices if Customer"]
  B -->|Enter amount, bank/cash account, method| C["System: Validate amount > 0, account active"]
  C -->|Optional: Allocate to invoices| D["System: Validate allocations <= amount, per-invoice <= remaining"]
  D -->|Save as Draft| E["System: Store receipt + allocations"]
  E -->|Review complete| F["Accountant: Click 'Post'"]
  F -->|Trigger POST endpoint| G["System: Final validation (period open, allocations valid)"]
  G -->|Success| H["System: Create voucher Dr 1111/1121 / Cr 131 or 711"]
  H -->|Update status| I["System: receipt.status = POSTED"]
  I -->|Update invoices| J["System: invoice.amountPaid += allocation, status update"]
  J -->|Audit log| K["System: Log POST action with diffs"]
  K -->|Success response| L["UI: Show success, updated balances"]
```

#### Workflow 2: Cash Payment Entry → AP Allocation → Maker-Checker (if threshold)

```
flowchart TD
  A["Accountant: New Cash Payment Form"] -->|Select payee (Supplier/Other)| B["System: Load unpaid bills if Supplier"]
  B -->|Enter amount, bank/cash account, method| C["System: Validate amount > 0, account active"]
  C -->|Balance Guard| D{{"Sufficient balance?"}}
  D -->|No| E["System: Show warning/block per config"]
  D -->|Yes| F["Allocate to bills or expense accounts"]
  F -->|Save as Draft| G["System: Store payment + allocations"]
  G -->|Review complete| H["Accountant: Click 'Post'"]
  H -->|Trigger POST endpoint| I{{"Amount > threshold?"}}
  I -->|No| J["System: Create voucher Cr 1111/1121 / Dr 331 or 6xx"]
  I -->|Yes| K["System: status = PENDING_APPROVAL"]
  K -->|Notify Chief Accountant| L["Chief Accountant: Review payment"]
  L -->|Approve| M["System: Create voucher, status = POSTED"]
  L -->|Reject + reason| N["System: status = REJECTED, notify creator"]
  J -->|Update bills| O["System: bill.amountPaid += allocation"]
  M -->|Update bills| O
  O -->|Audit log| P["System: Log action with diffs"]
```

#### Workflow 3: Cash/Bank Book View & Export

```
flowchart TD
  A["User: Open Cash/Bank Book"] -->|Select account, date range| B["System: Query transactions with filters"]
  B -->|Compute running balance| C["System: openingBalance + cumulative (debit - credit)"]
  C -->|Display ledger| D["UI: Show transaction grid with running balance column"]
  D -->|Click row| E["System: Fetch voucher detail"]
  E -->|Display modal| F["UI: Show voucher with lines, attachments"]
  D -->|Click Export| G["System: Generate Excel/PDF with branding"]
  G -->|Include hash| H["System: Add document hash in footer"]
  H -->|Audit log| I["System: Log export action"]
  I -->|Download| J["Browser: Download file"]
```

#### Workflow 4: Bank Reconciliation

```
flowchart TD
  A["Accountant: Start Reconciliation"] -->|Select bank account, period| B["System: Create reconciliation record"]
  B -->|Upload bank statement| C["System: Show column mapping wizard"]
  C -->|Map columns| D["System: Parse file, create statement lines"]
  D -->|Check duplicate| E{{"File hash exists?"}}
  E -->|Yes| F["System: Warn duplicate, block"]
  E -->|No| G["System: Run auto-match"]
  G -->|Suggest matches| H["UI: Show matched/unmatched split view"]
  H -->|Review suggestions| I["Accountant: Accept/reject auto-matches"]
  I -->|Manual match| J["System: Link statement line to voucher"]
  J -->|Unmatched items| K{{"Adjustment needed?"}}
  K -->|Yes, bank fee| L["Accountant: Create adjustment (bank fee)"]
  L -->|Approve adjustment| M["Chief Accountant: Review adjustment"]
  M -->|Approve| N["System: Create adjustment voucher"]
  K -->|No, timing| O["Accountant: Add note, leave unmatched"]
  N -->|All reconciled?| P{{"Balances match?"}}
  O -->|All reconciled?| P
  P -->|Yes| Q["Accountant: Click Complete"]
  Q -->|Update status| R["System: reconciliation.status = COMPLETED"]
  R -->|Update account| S["System: account.lastReconciledDate, lastReconciledBalance"]
  S -->|Audit log| T["System: Log completion"]
  P -->|No| U["System: Show discrepancy, require resolution"]
```

---

## Non-Functional Requirements

### Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| Cash book query (1 month, <5k TX) | ≤ 2 seconds | P95 latency |
| Receipt/payment post | ≤ 3 seconds | End-to-end including voucher creation |
| Reconciliation auto-match (1000 lines) | ≤ 10 seconds | P95 latency |
| Export to Excel (10k rows) | ≤ 30 seconds | Time to download completion |

**Implementation:**
- Indexed queries on `(company_id, bank_account_id, transaction_date)`
- Running balance computed via window functions or application-level aggregation
- Batch processing for auto-match with parallel execution
- Async export for large datasets with progress notification

### Security

| Requirement | Implementation |
|-------------|----------------|
| Authentication | JWT tokens (15-30 min expiry), refresh tokens (7-30 days) |
| Authorization | RBAC enforced at API level; Admin/Chief Accountant for approvals, reversals, reconciliation completion |
| Data Isolation | Row-level security via `company_id` filtering in all queries |
| Balance Guard | Configurable block/warn for overdraft attempts; logged to audit |
| Sensitive Data | Attachment URLs signed with time-limited tokens; no direct storage access |

### Reliability/Availability

| Requirement | Target |
|-------------|--------|
| Uptime | 99.5% availability |
| Data Durability | PostgreSQL WAL + daily backups |
| Transaction Integrity | All posts atomic; voucher + receipt/payment in single transaction |
| Reconciliation Recovery | Auto-save every 30 seconds; resume from last state |

### Observability

| Signal | Implementation |
|--------|----------------|
| Logging | Structured JSON logs with correlation ID, user ID, company ID |
| Metrics | Cash flow totals per company per day, reconciliation completion rate |
| Tracing | OpenTelemetry spans for post/reconciliation workflows |
| Alerts | Overdraft attempts, failed reconciliations, anomaly detection triggers |

---

## Dependencies and Integrations

### Backend Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.5.7 | Core framework |
| Spring Data JPA | 3.5.x | Data access |
| Spring Security | 6.x | Authentication/authorization |
| PostgreSQL | 42.7.4 | Database driver |
| Flyway | 11.10.0 | Database migrations |
| Apache POI | 5.3.0 | Excel export |
| Apache Commons CSV | 1.11.0 | CSV parsing |
| JasperReports | 6.21.3 | PDF generation |
| Redis (Spring Data) | 3.x | Caching (optional) |

### Frontend Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| React | 19.1.x | UI framework |
| TypeScript | 5.9.x | Type safety |
| shadcn/ui | latest | UI components |
| TanStack Table | 8.21.x | Data tables |
| React Hook Form | 7.66.x | Form handling |
| Zod | 4.1.x | Validation |
| date-fns | 4.1.x | Date formatting |
| Lucide React | 0.552.x | Icons |

### Internal Integrations

| Module | Integration Point |
|--------|-------------------|
| Voucher Engine (Epic 3) | `VoucherService.createVoucher()` for GL posting |
| AR Module (Epic 5) | `SalesInvoiceService` for receipt allocation |
| AP Module (Epic 4) | `PurchaseBillService` for payment allocation |
| Audit Service (Core) | `AuditService.log()` for all mutations |
| Bank Account (Epic 2) | Existing `BankAccount` entity and repository |

---

## Acceptance Criteria (Authoritative)

### Story 6.1: Cash/Bank Account Management

| ID | Acceptance Criterion |
|----|---------------------|
| AC6.1-01 | Account form captures: account name, bank name, account number (required, unique per company), branch (optional), type (CASH/BANK), opening balance (default 0), GL account code (required, must be leaf). |
| AC6.1-02 | Account number uniqueness enforced at DB constraint and API validation; duplicate returns 409 with conflicting account details. |
| AC6.1-03 | Inactivation removes account from pickers but preserves history; inactivation blocked if unposted transactions reference account. |
| AC6.1-04 | Delete blocked if account referenced in any posted voucher/reconciliation; error shows reference count and example transactions. |
| AC6.1-05 | Account list supports filter by type/status, search by name/number/bank, sort by balance/last activity; export to CSV/Excel. |
| AC6.1-06 | Account picker displays only active accounts with tooltip showing current balance, last TX date, last reconciled date. |
| AC6.1-07 | RBAC: Only Admin/Chief Accountant can create/inactivate; Accountant can view and select; all enforced server-side. |
| AC6.1-08 | Every create/edit/inactivate logged to audit trail with who/when/old/new/IP; blocked attempts logged with reason. |
| AC6.1-09 | Import accounts via CSV with template validation; atomic batch; duplicates reported with line numbers. |
| AC6.1-10 | Opening balance locked (uneditable) after first period closes; edit requires admin override with reason and audit log. |

### Story 6.2: Cash Receipt Entry & Posting

| ID | Acceptance Criterion |
|----|---------------------|
| AC6.2-01 | Receipt form captures: date, auto-number (CR-YYYY-seq), payer (customer/other), reference, amount (>0), cash/bank account (active), method, attachment (optional). |
| AC6.2-02 | AR allocation: shows customer's open invoices with remaining balances; supports partial allocation; prevents over-collection. |
| AC6.2-03 | Validations: period must be open, account must be active, amount > 0, customer required if posting to 131. |
| AC6.2-04 | Post creates voucher: Dr Cash/Bank (1111/1121), Cr AR (131) or Other Income (711) per allocation/selection. |
| AC6.2-05 | Reversal creates linked reversing receipt; both receipts cross-linked; reversal badge displayed; reason required and logged. |
| AC6.2-06 | Batch import: CSV/Excel template with headers; atomic; error report includes row number, field, message. |
| AC6.2-07 | Performance: create and post simple receipt in ≤10 seconds; latency logged to telemetry. |
| AC6.2-08 | Attachments: up to 10 files/20MB total; preview images/PDF; blocked file types rejected with message. |
| AC6.2-09 | RBAC: Accountants can create/post; all actions audit-logged. |
| AC6.2-10 | All actions (create/edit/post/reverse/import) generate audit entries with payload diffs and event hash. |

### Story 6.3: Cash Payment Entry & Posting

| ID | Acceptance Criterion |
|----|---------------------|
| AC6.3-01 | Payment form captures: date, auto-number (CP-YYYY-seq), payee (supplier/other), reference, amount (>0), cash/bank account (active), method, attachment (required above threshold). |
| AC6.3-02 | AP linkage: select unpaid/part-paid bills; default FIFO allocation; manual override allowed; shows running remaining per bill. |
| AC6.3-03 | Prevent overpayment; blocking error lists exceeded bills; require adjustment to proceed. |
| AC6.3-04 | Standalone payments to expense accounts (6xx/8xx) permitted; ad-hoc payee requires reason and audit tag. |
| AC6.3-05 | Post creates voucher: Cr Cash/Bank (1111/1121), Dr AP (331) or Expense (6xx/8xx) per allocations. |
| AC6.3-06 | Balance guard: check sufficient balance before posting; overdraft attempt logged; configurable block/warn. |
| AC6.3-07 | Batch import: CSV/Excel template; atomic; row-level error map; attachments mapped by filename. |
| AC6.3-08 | Maker-checker: payments above threshold route to approval; approver ≠ maker; notifications sent; audit of state transitions. |
| AC6.3-09 | Performance: post simple payment in ≤10 seconds; quick keyboard entry supported. |
| AC6.3-10 | All actions fully audit-logged; reversal creates linked reversing payment with mandatory reason. |

### Story 6.4: Bank/Cash Book View & Running Balances

| ID | Acceptance Criterion |
|----|---------------------|
| AC6.4-01 | Per-account view: filters by date/type/reference; columns show opening balance, inflow, outflow, closing; running balance after each TX. |
| AC6.4-02 | Drill into row to voucher detail with attachments; show posting user and timestamps; highlight negative balances. |
| AC6.4-03 | Multi-account view shows aggregated totals; toggle grouping by account; quick switcher between bank/cash. |
| AC6.4-04 | Export to Excel/PDF includes company branding, filter snapshot, timestamp, generated-by, document hash in footer. |
| AC6.4-05 | Pagination/virtual list for >5k rows; async download for full dataset with notification when ready. |
| AC6.4-06 | RBAC enforced for all queries/exports; unauthorized attempts fail and are audit-logged. |
| AC6.4-07 | Performance: initial load ≤2s for typical month (≤5k TX); slow queries logged. |
| AC6.4-08 | All views/exports logged in audit with filters, user, IP, hash of snapshot data. |

### Story 6.5: Manual Bank Reconciliation

| ID | Acceptance Criterion |
|----|---------------------|
| AC6.5-01 | Import statement (CSV/Excel) with column mapping wizard; persistent format profiles per bank. |
| AC6.5-02 | Duplicate import detection via file hash and date range overlap; warning and block if duplicate. |
| AC6.5-03 | Auto-suggest matches by date±N days (configurable), amount tolerance, reference similarity; explain match reason in UI. |
| AC6.5-04 | Manual match/unmatch with notes; retain unmatched list with reasons (timing, missing voucher, bank fee). |
| AC6.5-05 | Adjustment suggestions: create bank fee/interest vouchers directly from reconciliation UI with pre-filled values. |
| AC6.5-06 | Summary shows matched count/value, unmatched count/value, delta; export matched/unmatched reports. |
| AC6.5-07 | Error handling: import errors list row numbers and reasons; partial import disallowed. |
| AC6.5-08 | Audit: every file upload, parse, match/unmatch, adjustment, export logged with user/time/IP and hash. |
| AC6.5-09 | Reconciliation status per account/month stored and displayed (Not started/In progress/Completed) with last updated timestamp. |

### Story 6.6: Cash & Bank Audit and Compliance

| ID | Acceptance Criterion |
|----|---------------------|
| AC6.6-01 | Closed period protection: edits/deletes blocked; attempts show error and produce audit log with reason. |
| AC6.6-02 | Scheduled backup/export: weekly compressed export of accounts, books, reconciliations, audit logs to external storage. |
| AC6.6-03 | Integrity checks: daily and period-close checks verify Dr/Cr parity; alerts to admin on anomalies. |
| AC6.6-04 | Export controls: PDF/Excel carry hash and signature block; watermarked DRAFT when period open. |
| AC6.6-05 | Audit explorer: filter by account/date/action/user; export JSON/CSV/PDF; 10-year retention enforced. |
| AC6.6-06 | Notification center integration: repeated blocked attempts or suspicious patterns trigger alerts. |
| AC6.6-07 | Compliance logs include version of policies/rules active at time of action. |

---

## Traceability Mapping

| AC ID | Spec Section | Component/API | Test Idea |
|-------|--------------|---------------|-----------|
| AC6.1-01 | Data Models - BankAccount | `POST /api/v1/bank-accounts` | Unit: validate required fields |
| AC6.1-02 | APIs - Bank Account | `BankAccountService.create()` | Integration: duplicate detection |
| AC6.1-03 | APIs - Bank Account | `PATCH /bank-accounts/:id/deactivate` | Integration: picker exclusion |
| AC6.1-04 | Services - BankAccountService | `BankAccountService.delete()` | Integration: reference check |
| AC6.2-01 | Existing - ar_payments | `POST /api/v1/ar/receipts` (existing) | Unit: form validation |
| AC6.2-02 | Existing - ReceiptService | `ReceiptService.create()` (existing) | Integration: allocation validation |
| AC6.2-04 | Workflows - Workflow 1 | `ReceiptService.post()` (existing) | Integration: voucher creation |
| AC6.2-05 | Existing - ReceiptController | `POST /ar/receipts/:id/reverse` (existing) | Integration: cross-linking |
| AC6.3-06 | Existing - APPaymentService | `APPaymentService.checkBalance()` | Unit: balance guard |
| AC6.3-08 | Workflows - Workflow 2 | `POST /ap/payments/:id/approve` (existing) | Integration: maker-checker |
| AC6.4-01 | APIs - Cash Book (NEW) | `GET /api/v1/cash-book/:id` | Integration: running balance |
| AC6.4-04 | Services - CashBookExportService (NEW) | `GET /cash-book/:id/export` | Integration: hash generation |
| AC6.5-01 | APIs - Reconciliation (NEW) | `POST /bank-reconciliations/:id/import-statement` | Integration: column mapping |
| AC6.5-03 | Services - ReconciliationMatcher (NEW) | `POST /bank-reconciliations/:id/auto-match` | Unit: match algorithm |
| AC6.6-01 | Services - CashAuditService (NEW) | Period validation aspect | Integration: closed period block |
| AC6.6-03 | Services - IntegrityCheckService (NEW) | Scheduled job | Integration: anomaly detection |

---

## Risks, Assumptions, Open Questions

### Risks

| ID | Risk | Mitigation |
|----|------|------------|
| R1 | Balance calculation performance degrades with high transaction volume | Implement materialized balance views or periodic balance snapshots |
| R2 | Reconciliation auto-match produces false positives | Require human review of all auto-matches; display confidence score |
| R3 | Opening balance lock bypass attempts | Database trigger enforcement; dual audit logging |

### Assumptions

| ID | Assumption |
|----|------------|
| A1 | Single currency (VND) for all cash/bank transactions |
| A2 | Bank statements available in structured CSV/Excel format (no PDF parsing required) |
| A3 | Existing `BankAccount` entity from Epic 2 can be extended without migration issues |
| A4 | Approval threshold is company-wide, not per-account |
| A5 | Existing `ar_payments` table (Epic 5) is sufficient for cash receipt functionality |
| A6 | Existing `ap_payments` table (Epic 4) is sufficient for cash payment functionality |
| A7 | Existing `transaction_allocations` table handles all receipt/payment allocations |

### Open Questions

| ID | Question | Owner | Status |
|----|----------|-------|--------|
| Q1 | Should overdraft be a hard block or warning with override? | Product | Pending |
| Q2 | Default auto-match tolerance (days/amount)? | Product | Pending |
| Q3 | Retention period for reconciliation files (original statements)? | Compliance | Pending |

---

## Test Strategy Summary

### Test Levels

| Level | Coverage |
|-------|----------|
| Unit Tests | Service validation logic, balance calculations, match algorithms |
| Integration Tests | API endpoints with TestContainers PostgreSQL, voucher creation flows |
| E2E Tests | Full receipt/payment workflows, reconciliation completion |

### Test Frameworks

- **Backend:** JUnit 5 + Mockito + TestContainers
- **Frontend:** Vitest + Testing Library + Playwright (E2E)

### Critical Test Scenarios

1. **Receipt Posting:** Verify voucher creation with correct debits/credits
2. **Payment Maker-Checker:** Verify approval workflow enforces maker ≠ checker
3. **Balance Guard:** Verify overdraft block/warning behavior
4. **Running Balance:** Verify accuracy across multiple transactions
5. **Auto-Match:** Verify match suggestions with various tolerance settings
6. **Reconciliation Completion:** Verify balance reconciliation and status update
7. **Audit Trail:** Verify all actions logged with correct diffs and hashes
8. **Period Lock:** Verify closed period blocks edits/posts

### Coverage Target

- Backend: ≥80% line coverage
- Frontend: ≥70% component coverage
- E2E: 100% of acceptance criteria
