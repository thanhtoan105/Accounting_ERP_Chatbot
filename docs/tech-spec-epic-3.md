# Epic Technical Specification: Voucher Engine & General Ledger Core

Date: 2025-01-31
Author: thanhtoan
Epic ID: 3
Status: Draft

---

## Overview

This epic delivers the core voucher engine and general ledger foundation for the accounting system. It enables accountants to create, edit, and post journal vouchers with full double-entry validation, strict leaf-only posting enforcement, comprehensive audit trails, and period-based controls. The implementation aligns with TT200 (Circular 200/2014/TT-BTC) compliance requirements and establishes the transactional foundation that subsequent AP/AR and reporting modules depend upon. The system ensures data integrity through real-time validation, immutable audit logs, and prevents posting to closed periods while supporting draft workflows for accurate data entry.

## Objectives and Scope

**In-Scope:**
- Voucher list and search functionality with filtering, sorting, and pagination
- Voucher form with inline line item editing, keyboard navigation, and auto-validation
- Posting and unposting workflows with atomic status transitions
- Reversal voucher generation with bi-directional linking
- Double-entry validation engine (Total Debit = Total Credit) with BigDecimal precision
- Leaf-only posting enforcement (blocks parent account postings)
- Mandatory dimension validation (customer_id for AR accounts, vendor_id for AP accounts, cost_center_id for expense accounts)
- Period selector and voucher-period mapping with closed period prevention
- Comprehensive audit trail for all voucher lifecycle events (create, edit, post, unpost, reverse)
- Voucher attachments management with secure storage and access controls
- Draft state management with auto-save and recovery
- Voucher number auto-generation (format: VC{YYYY}-{seq})

**Out-of-Scope:**
- Purchase bills and sales invoices (deferred to Epic 4 and Epic 5)
- Cash receipts and payments (deferred to Epic 6)
- Financial report generation (deferred to Epic 7)
- Maker-checker approval workflow for general journals (optional feature, deferred to post-MVP)
- Multi-currency support (VND only in MVP)
- Advanced workflow automation or business rules engine
- Real-time collaboration/co-editing features

## System Architecture Alignment

This epic aligns with the Spring Boot backend architecture, implementing RESTful controllers in `controller/voucher/`, business logic in `service/gl/`, and JPA repositories for data persistence. The data model leverages PostgreSQL tables `vouchers`, `voucher_lines`, and `journal_entries` as specified in the architecture document. The frontend implements React components in `features/accounting/pages/Vouchers/VoucherList.tsx` and `features/accounting/pages/Vouchers/VoucherForm.tsx` (feature-first, shadcn layout), while still using MUI where appropriate for dense tables. The implementation follows established naming patterns (snake_case for DB, PascalCase for entities, REST endpoints at `/api/v1/vouchers`), multi-tenancy via `company_id` row-level filtering, and Spring Security integration for RBAC enforcement. The system integrates with Epic 2's Chart of Accounts (COA) for account validation and references the period management foundation from Epic 1. File attachments utilize Supabase Storage as per architecture decision, and audit logs write to the `audit_logs` table with immutable append-only semantics.

## Detailed Design

### Services and Modules

**Backend Services:**

1. **VoucherService** (`service/impl/voucher/VoucherService`)
   - Responsibilities: Create, update, retrieve, delete vouchers; enforce business rules (double-entry, leaf-only, dimensions)
   - Inputs: VoucherDTO, VoucherLineDTO, validation context
   - Outputs: Voucher entity, validation errors, status transitions
   - Owner: Backend team

2. **VoucherPostingService** (`service/impl/gl/VoucherPostingService`)
   - Responsibilities: Post vouchers to GL (create journal entries), unpost (remove entries), validate period status
   - Inputs: Voucher ID, posting context (user, period)
   - Outputs: Posted voucher, journal entries created, audit events
   - Owner: Backend team

3. **VoucherReversalService** (`service/impl/voucher/VoucherReversalService`)
   - Responsibilities: Generate reversal vouchers, link bi-directionally, prevent double-reversal
   - Inputs: Original voucher ID, reversal reason
   - Outputs: Reversal voucher entity, linkage metadata
   - Owner: Backend team

4. **VoucherValidationService** (`service/impl/voucher/VoucherValidationService`)
   - Responsibilities: Validate double-entry balance, leaf-only accounts, required dimensions, period status
   - Inputs: Voucher lines, account metadata, period context
   - Outputs: Validation result (success/errors), detailed error map
   - Owner: Backend team

5. **PeriodService** (`service/impl/admin/PeriodService`) - **Reference from Epic 1**
   - Responsibilities: Check period status (open/closed), validate voucher date against period
   - Inputs: Date, company ID
   - Outputs: Period entity, status, validation result
   - Owner: Backend team (shared dependency)

6. **AttachmentService** (`service/impl/voucher/AttachmentService`)
   - Responsibilities: Upload to Supabase Storage, generate signed URLs, manage file metadata
   - Inputs: File stream, voucher ID, user context
   - Outputs: Attachment metadata, signed download URLs
   - Owner: Backend team

7. **AuditLogService** (`service/impl/audit/AuditLogService`) - **Reference from Epic 1**
   - Responsibilities: Log all voucher lifecycle events, generate hash digests, provide audit history
   - Inputs: Action type, entity snapshot, user context
   - Outputs: Audit log entry with hash
   - Owner: Backend team (shared dependency)

**Frontend Modules:**

1. **VoucherList Component** (`features/accounting/pages/Vouchers/VoucherList.tsx`)
   - Responsibilities: Display voucher table with filters, search, pagination; handle row actions
   - Inputs: Filter state, sort order, page number
   - Outputs: Rendered table, filter UI, action buttons
   - Owner: Frontend team

2. **VoucherForm Component** (`features/accounting/pages/Vouchers/VoucherForm.tsx`)
   - Responsibilities: Render voucher form with line item grid, handle inline editing, validation feedback
   - Inputs: Voucher ID (for edit), draft state
   - Outputs: Form state, validation errors, save actions
   - Owner: Frontend team

3. **VoucherLineItemGrid Component** (`components/voucher/VoucherLineItemGrid.tsx`)
   - Responsibilities: Editable grid for line items with account picker, debit/credit inputs, dimension fields
   - Inputs: Line items array, account list, validation rules
   - Outputs: Updated line items, field-level errors
   - Owner: Frontend team

4. **PeriodSelector Component** (`components/common/PeriodSelector.tsx`) - **Shared component**
   - Responsibilities: Period dropdown with open periods only, current period highlighting
   - Inputs: Company ID, current selection
   - Outputs: Selected period, validation state
   - Owner: Frontend team (shared)

### Data Models and Contracts

**Database Tables:**

1. **vouchers** (snake_case, PostgreSQL)
   ```sql
   CREATE TABLE vouchers (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     company_id UUID NOT NULL REFERENCES companies(id),
     voucher_number VARCHAR(50) NOT NULL, -- Format: VC{YYYY}-{seq}
     voucher_date DATE NOT NULL,
     period_id UUID REFERENCES accounting_periods(id),
     description TEXT,
     status VARCHAR(20) NOT NULL, -- 'draft', 'posted', 'unposted'
     currency VARCHAR(3) DEFAULT 'VND',
     total_debit DECIMAL(18,2) DEFAULT 0,
     total_credit DECIMAL(18,2) DEFAULT 0,
     entered_by UUID REFERENCES users(id),
     posted_by UUID REFERENCES users(id),
     posted_at TIMESTAMP,
     reversal_of UUID REFERENCES vouchers(id), -- For reversal linkage
     reversed_by UUID REFERENCES vouchers(id), -- Bi-directional link
     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
     updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
     UNIQUE(company_id, voucher_number),
     CHECK (status IN ('draft', 'posted', 'unposted'))
   );
   CREATE INDEX idx_vouchers_company ON vouchers(company_id);
   CREATE INDEX idx_vouchers_date ON vouchers(voucher_date);
   CREATE INDEX idx_vouchers_status ON vouchers(status);
   CREATE INDEX idx_vouchers_period ON vouchers(period_id);
   ```

2. **voucher_lines** (snake_case, PostgreSQL)
   ```sql
   CREATE TABLE voucher_lines (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     voucher_id UUID NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
     line_number INTEGER NOT NULL,
     account_id UUID NOT NULL REFERENCES chart_of_accounts(id),
     debit DECIMAL(18,2) DEFAULT 0 CHECK (debit >= 0),
     credit DECIMAL(18,2) DEFAULT 0 CHECK (credit >= 0),
     description TEXT,
     customer_id UUID REFERENCES customers(id), -- Required if account 131
     vendor_id UUID REFERENCES suppliers(id), -- Required if account 331
     cost_center_id UUID REFERENCES cost_centers(id), -- Required for 154/621
     item_id UUID REFERENCES items(id), -- Optional dimension
     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
     updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
     CHECK (debit = 0 OR credit = 0), -- Not both, not neither
     CHECK (debit > 0 OR credit > 0), -- At least one must be > 0
     UNIQUE(voucher_id, line_number)
   );
   CREATE INDEX idx_voucher_lines_voucher ON voucher_lines(voucher_id);
   CREATE INDEX idx_voucher_lines_account ON voucher_lines(account_id);
   ```

3. **journal_entries** (snake_case, PostgreSQL) - **Created during posting**
   ```sql
   CREATE TABLE journal_entries (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     company_id UUID NOT NULL REFERENCES companies(id),
     voucher_id UUID NOT NULL REFERENCES vouchers(id),
     voucher_line_id UUID NOT NULL REFERENCES voucher_lines(id),
     account_id UUID NOT NULL REFERENCES chart_of_accounts(id),
     period_id UUID NOT NULL REFERENCES accounting_periods(id),
     entry_date DATE NOT NULL,
     debit DECIMAL(18,2) DEFAULT 0,
     credit DECIMAL(18,2) DEFAULT 0,
     description TEXT,
     customer_id UUID REFERENCES customers(id),
     vendor_id UUID REFERENCES suppliers(id),
     cost_center_id UUID REFERENCES cost_centers(id),
     item_id UUID REFERENCES items(id),
     posted_by UUID NOT NULL REFERENCES users(id),
     posted_at TIMESTAMP NOT NULL DEFAULT NOW(),
     created_at TIMESTAMP NOT NULL DEFAULT NOW()
   );
   CREATE INDEX idx_journal_entries_voucher ON journal_entries(voucher_id);
   CREATE INDEX idx_journal_entries_account ON journal_entries(account_id);
   CREATE INDEX idx_journal_entries_period ON journal_entries(period_id);
   CREATE INDEX idx_journal_entries_date ON journal_entries(entry_date);
   ```

4. **voucher_attachments** (snake_case, PostgreSQL)
   ```sql
   CREATE TABLE voucher_attachments (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     voucher_id UUID NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
     company_id UUID NOT NULL REFERENCES companies(id),
     file_name VARCHAR(255) NOT NULL,
     file_size BIGINT NOT NULL,
     file_type VARCHAR(100),
     storage_path VARCHAR(500) NOT NULL, -- Supabase Storage path
     uploaded_by UUID NOT NULL REFERENCES users(id),
     uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
     deleted_at TIMESTAMP,
     deleted_by UUID REFERENCES users(id)
   );
   CREATE INDEX idx_attachments_voucher ON voucher_attachments(voucher_id);
   ```

**JPA Entity Classes:**

1. **Voucher** (`entity/Voucher.java`)
   - Fields: id, companyId, voucherNumber, voucherDate, periodId, description, status, currency, totalDebit, totalCredit, enteredBy, postedBy, postedAt, reversalOf, reversedBy
   - Relationships: @ManyToOne Company, @OneToMany VoucherLine, @OneToMany Attachment
   - Implements: CompanyScopedEntity interface

2. **VoucherLine** (`entity/VoucherLine.java`)
   - Fields: id, voucherId, lineNumber, accountId, debit, credit, description, customerId, vendorId, costCenterId, itemId
   - Relationships: @ManyToOne Voucher, @ManyToOne Account, optional @ManyToOne Customer/Supplier/CostCenter/Item

3. **JournalEntry** (`entity/JournalEntry.java`)
   - Fields: id, companyId, voucherId, voucherLineId, accountId, periodId, entryDate, debit, credit, description, dimensions, postedBy, postedAt
   - Relationships: @ManyToOne Voucher, @ManyToOne Account, @ManyToOne Period

**DTOs:**

1. **VoucherDTO** (`dto/VoucherDTO.java`)
   - Fields matching Voucher entity + lines array (VoucherLineDTO[])
   - Validation: @NotNull, @Size, @Valid annotations

2. **VoucherLineDTO** (`dto/VoucherLineDTO.java`)
   - Fields: accountId, debit, credit, description, customerId, vendorId, costCenterId, itemId
   - Validation: Custom validators for debit/credit mutual exclusivity

3. **VoucherCreateRequest** (`dto/VoucherCreateRequest.java`)
   - Fields: date, description, lines (array), periodId (optional, derived if missing)

4. **VoucherPostRequest** (`dto/VoucherPostRequest.java`)
   - Fields: voucherId, confirmBalance (boolean flag)

### APIs and Interfaces

**REST API Endpoints** (Base: `/api/v1/vouchers`):

1. **GET /api/v1/vouchers**
   - Query params: `page`, `size`, `status` (draft|posted|unposted), `dateFrom`, `dateTo`, `search` (text), `sort` (date|status|number)
   - Response: `Page<VoucherDTO>` with pagination metadata
   - Auth: Required (Accountant+)
   - RBAC: Filtered by company_id automatically

2. **GET /api/v1/vouchers/{voucherId}**
   - Response: `VoucherDTO` with full line items
   - Auth: Required
   - RBAC: Company-scoped access only

3. **POST /api/v1/vouchers**
   - Request body: `VoucherCreateRequest`
   - Response: `VoucherDTO` (created voucher)
   - Status codes: 201 Created, 400 Validation Error, 403 Forbidden
   - Auth: Required (Accountant+)
   - Validation: Double-entry, leaf-only, dimensions, period status

4. **PUT /api/v1/vouchers/{voucherId}**
   - Request body: `VoucherCreateRequest`
   - Response: `VoucherDTO` (updated)
   - Status codes: 200 OK, 400 Validation Error, 404 Not Found, 409 Conflict (if posted)
   - Auth: Required (Accountant+)
   - Business rule: Only editable if status='draft'

5. **DELETE /api/v1/vouchers/{voucherId}**
   - Query params: `reason` (required for audit)
   - Response: 204 No Content
   - Status codes: 204 Success, 400 If posted/referenced, 404 Not Found
   - Auth: Required (Accountant+)
   - Business rule: Only deletable if status='draft' and unreferenced

6. **POST /api/v1/vouchers/{voucherId}/post**
   - Request body: `VoucherPostRequest` (optional confirmation)
   - Response: `VoucherDTO` (posted), `JournalEntry[]` (created entries)
   - Status codes: 200 OK, 400 Validation Error, 409 Conflict (period closed)
   - Auth: Required (Accountant+)
   - Side effects: Creates journal_entries, updates voucher status, triggers audit log

7. **POST /api/v1/vouchers/{voucherId}/unpost**
   - Request body: `{ "reason": "string" }` (required)
   - Response: `VoucherDTO` (unposted)
   - Status codes: 200 OK, 400 If referenced, 404 Not Found
   - Auth: Required (Chief Accountant+)
   - Side effects: Removes journal_entries, updates status, validates no dependencies

8. **POST /api/v1/vouchers/{voucherId}/reverse**
   - Request body: `{ "reason": "string" }` (required)
   - Response: `VoucherDTO` (reversal voucher, status='posted')
   - Status codes: 201 Created, 400 If already reversed, 409 Conflict
   - Auth: Required (Accountant+)
   - Side effects: Creates reversal voucher, links bi-directionally, posts automatically

9. **GET /api/v1/vouchers/{voucherId}/attachments**
   - Response: `AttachmentDTO[]`
   - Auth: Required

10. **POST /api/v1/vouchers/{voucherId}/attachments**
    - Request: Multipart form data (file)
    - Response: `AttachmentDTO`
    - Status codes: 201 Created, 400 File validation error
    - Auth: Required (Accountant+)

11. **DELETE /api/v1/vouchers/{voucherId}/attachments/{attachmentId}**
    - Query params: `reason` (required)
    - Response: 204 No Content
    - Auth: Required (Creator or Admin only)

12. **GET /api/v1/vouchers/{voucherId}/audit-history**
    - Response: `AuditLogDTO[]` (voucher-specific history)
    - Auth: Required (Auditor+)

**Error Response Format:**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Tổng Nợ phải bằng Tổng Có",
    "details": {
      "debitTotal": 1000000,
      "creditTotal": 900000,
      "lines": [
        {"lineNumber": 2, "field": "debit", "error": "Amount must be positive"}
      ]
    }
  }
}
```

### Workflows and Sequencing

**1. Voucher Creation Workflow:**
```
1. User opens VoucherForm (empty state)
2. System loads current period (or latest open period) via PeriodService
3. User enters date, description
4. User adds line items via VoucherLineItemGrid:
   a. Selects account (typeahead filtered to leaf/postable accounts only)
   b. Enters debit OR credit (mutually exclusive)
   c. System validates dimension requirements (if account 131 → requires customer_id)
   d. Auto-calculates totals, shows balance check
5. User clicks "Save Draft"
6. Frontend validates client-side (double-entry, required fields)
7. POST /api/v1/vouchers with draft status
8. Backend: VoucherValidationService validates all rules
9. VoucherService creates voucher + lines (transactional)
10. AuditLogService logs "VOUCHER_CREATED" event
11. Returns VoucherDTO with generated voucher_number
12. Frontend shows success, updates list view
```

**2. Voucher Posting Workflow:**
```
1. User selects posted voucher from list
2. Clicks "Post" button
3. Frontend: Re-validates double-entry balance client-side
4. POST /api/v1/vouchers/{id}/post
5. Backend: VoucherPostingService:
   a. Validates period is open (via PeriodService)
   b. Validates voucher status = 'draft'
   c. Re-validates double-entry (debitTotal == creditTotal)
   d. Validates leaf-only accounts (via COA metadata)
   e. Validates required dimensions (customer_id, vendor_id, cost_center_id)
   f. If all valid: Creates journal_entries (one per voucher_line)
   g. Updates voucher: status='posted', posted_by, posted_at
   h. Updates total_debit, total_credit
6. AuditLogService logs "VOUCHER_POSTED" with snapshot
7. Returns posted voucher + journal entries created
8. Frontend: Updates UI, shows success toast
9. Invalidates React Query cache for voucher list
```

**3. Voucher Reversal Workflow:**
```
1. User selects posted voucher
2. Clicks "Reverse" button, enters reason (required)
3. POST /api/v1/vouchers/{id}/reverse with reason
4. Backend: VoucherReversalService:
   a. Validates voucher status = 'posted'
   b. Validates not already reversed (reversed_by IS NULL)
   c. Creates new voucher:
      - voucher_number: "REV-{original_number}"
      - voucher_date: current date
      - description: "Reversal of {original_number}: {reason}"
      - reversal_of: original voucher ID
   d. Creates reversal lines (swaps debit/credit for each line)
   e. Automatically posts reversal voucher (status='posted')
   f. Links bi-directionally: original.reversed_by = reversal.id
5. AuditLogService logs "VOUCHER_REVERSED" on both vouchers
6. Returns reversal voucher
7. Frontend: Shows reversal voucher in new tab, updates original with "Reversed" badge
```

**4. Unposting Workflow:**
```
1. User (Chief Accountant+) selects posted voucher
2. Clicks "Unpost" button, enters reason
3. POST /api/v1/vouchers/{id}/unpost
4. Backend: VoucherPostingService:
   a. Validates period is still open (if period closed, block unpost)
   b. Validates no dependencies (e.g., referenced in payment, invoice)
   c. If blocked: Returns 400 with dependency list
   d. If allowed: Deletes journal_entries (WHERE voucher_id = id)
   e. Updates voucher: status='unposted', clears posted_by/posted_at
5. AuditLogService logs "VOUCHER_UNPOSTED" with reason
6. Returns unposted voucher
7. Frontend: Updates status badge, refreshes list
```

**5. Period Validation Sequence:**
```
On voucher create/edit:
1. User selects date or period via PeriodSelector
2. Frontend: Fetches open periods via GET /api/v1/periods?status=open
3. Date picker disabled for closed/future periods
4. On save: Backend validates date falls within open period
5. If invalid: Returns 400 "Cannot create voucher in closed period"

On posting:
1. VoucherPostingService checks voucher_date against PeriodService
2. If period closed: Blocks with 409 "Period {period} is closed"
3. If period not found: Blocks with 404
```

## Non-Functional Requirements

### Performance

**Performance Targets:**
- Voucher list page load: < 2 seconds for initial render (NFR1 alignment)
- Voucher form save (draft): < 1 second response time (NFR1)
- Voucher posting operation: < 2 seconds end-to-end (includes journal entry creation)
- Voucher search with filters: < 500ms response time for typical datasets (<1000 vouchers)
- Voucher line item grid rendering: Support 20+ lines with smooth scrolling (< 60s total entry time per acceptance criteria)

**Optimization Strategies:**
- Database indexes on `vouchers(company_id, voucher_date, status)` for list queries (NFR4)
- Database indexes on `voucher_lines(voucher_id)`, `journal_entries(voucher_id, account_id, period_id)` for posting/queries
- Pagination with page size 20-50 items to limit data transfer
- Lazy loading of voucher attachments (metadata only in list, full download on demand)
- React Query caching for voucher list (5-minute stale time, background refetch)
- Avoid N+1 queries: Use `@EntityGraph` or JOIN FETCH for voucher + lines in single query
- BigDecimal precision for monetary calculations (no rounding errors, exact decimal arithmetic)

**Scalability Considerations:**
- Support 20+ concurrent users (NFR3) creating/posting vouchers simultaneously
- Voucher number sequence generation must be thread-safe (database-level sequence or UUID-based)
- Attachment uploads use Supabase Storage (external, scales independently)

**Performance Monitoring:**
- Log queries > 1 second execution time (NFR4)
- Monitor voucher posting transaction duration
- Track React Query cache hit rates

### Security

**Authentication and Authorization:**
- All voucher endpoints require JWT authentication (NFR5, NFR6)
- RBAC enforcement at API level: Accountant+ can create/edit, Chief Accountant+ can unpost (NFR5)
- Company-level data isolation: All queries automatically filtered by `company_id` (via CompanyScopeAspect)
- Frontend UI hiding is not a security boundary; backend must reject unauthorized access with 403 (NFR5)

**Data Protection:**
- Voucher attachments stored in Supabase Storage with company-scoped paths and signed URLs (10-minute expiry)
- Audit logs are append-only and immutable (NFR8)
- Password/authentication handled by Epic 1 foundation (JWT, Bcrypt hashing)
- Input validation: All DTOs use Bean Validation (@NotNull, @Size, custom validators for business rules)

**Security Validations:**
- Negative debit/credit amounts blocked (possible fraud indicator, logged to audit)
- Period closed validation prevents unauthorized postings to locked periods
- Attempted deletion of posted vouchers logged as "blocked action" with user/IP context
- Dimension validation prevents orphaned or invalid account combinations

**HTTPS/TLS:**
- Production requires HTTPS for all API calls (NFR7)
- HttpOnly, Secure cookies for JWT tokens (handled by Epic 1)

### Reliability/Availability

**Transaction Integrity:**
- All voucher operations use database transactions (@Transactional) to ensure atomicity
- Double-entry validation enforced at both frontend and backend (NFR11)
- Posting operation: Creates voucher + journal_entries atomically; rollback on any failure
- Unposting operation: Deletes journal_entries + updates voucher status atomically

**Error Recovery:**
- Optimistic UI updates with rollback on API error (React Query error handling)
- Draft auto-save every 30 seconds or on blur to prevent data loss on browser crash
- Database connection pooling (HikariCP, Spring Boot default) for reliable database access
- Retry logic: 3 retries with exponential backoff for transient network errors (frontend)

**Period Lock Integrity:**
- Period closing locks all vouchers in that period (immutable, NFR12)
- System prevents modifications to vouchers in closed periods (API returns 409 Conflict)
- Period reopen requires Chief Accountant approval and audit trail (Epic 1 dependency)

**Data Consistency:**
- Referential integrity: Foreign key constraints enforce voucher → lines, voucher → journal_entries relationships (NFR13)
- Double-entry invariant: Every voucher must balance (Total Debit = Total Credit) enforced by CHECK constraints and application logic (NFR11)
- Leaf-only posting: Database and application layers both validate `postable=true` on accounts (NFR14)

**Availability:**
- Health check endpoint `/api/health` returns 200 OK (Epic 1, NFR25)
- Graceful degradation: If Supabase Storage unavailable, attachment uploads fail gracefully with user-visible error
- No single point of failure: Database connection pooling and transaction management handled by Spring Boot

### Observability

**Logging:**
- Structured logging with JSON format (NFR24)
- Log all voucher lifecycle events: CREATE, UPDATE, POST, UNPOST, REVERSE, DELETE_ATTEMPT
- Include context: user_id, company_id, voucher_id, timestamp, IP address (if available)
- Mask sensitive data: Do not log full voucher details in logs, only IDs and action types
- Request ID tracking: Include correlation ID in all log entries for traceability

**Audit Trail:**
- Every voucher action generates immutable audit log entry with:
  - Action type (CREATE, UPDATE, POST, etc.)
  - Entity snapshot (JSON before/after state)
  - Cryptographic hash digest for tamper detection
  - User/role, timestamp, device/IP (if available)
- Audit log exportable as PDF/JSON with hash watermark for legal compliance
- Voucher history view shows field-by-field diff with colored changes

**Monitoring:**
- Health check endpoint for infrastructure monitoring (Epic 1, NFR25)
- Database connection pool metrics available via Spring Boot Actuator
- Slow query logging: Track queries > 1 second execution time (NFR4, NFR25)
- Alert on suspicious patterns: Multiple failed posting attempts, negative amount attempts logged as "possible fraud"

**Metrics (Post-MVP Enhancement):**
- Voucher creation rate (vouchers/minute)
- Posting success/failure rate
- Average posting duration
- Draft-to-posted conversion rate
- Audit log size growth

**Debugging Support:**
- API error responses include request ID for support troubleshooting
- Frontend error boundaries catch and display user-friendly messages
- Detailed validation error maps show field-level issues (not just generic 400)

## Dependencies and Integrations

**Backend Dependencies (Spring Boot 3.5.7):**

- **Spring Boot Starter Web** (via Spring Boot 3.5.7): REST API, MVC framework
- **Spring Boot Starter Data JPA** (via Spring Boot 3.5.7): JPA/Hibernate ORM for database access
- **Spring Boot Starter Security** (via Spring Boot 3.5.7): Authentication and authorization
- **Spring Boot Starter Validation** (via Spring Boot 3.5.7): Bean Validation API for DTO validation
- **PostgreSQL Driver** (latest): Database connectivity to PostgreSQL/Supabase
- **Flyway** (latest): Database migration management for schema versioning
- **Lombok** (latest): Reduces boilerplate code (getters, setters, constructors)
- **SpringDoc OpenAPI** (2.3.x): Swagger/OpenAPI documentation generation

**External Service Integrations:**

1. **Supabase (PostgreSQL Database)**
   - Purpose: Primary database for vouchers, voucher_lines, journal_entries, voucher_attachments
   - Integration: JDBC connection string in `application.yml`
   - Version/Commit: PostgreSQL 15+ (managed by Supabase)
   - Constraints: Multi-tenant via `company_id`, connection pooling via HikariCP

2. **Supabase Storage**
   - Purpose: File storage for voucher attachments (PDFs, images, documents)
   - Integration: Supabase Storage REST API or Java SDK
   - API Endpoint: Supabase project storage bucket `/vouchers/{voucherId}/attachments/{filename}`
   - Constraints: Company-scoped paths, signed URLs with 10-minute expiry, file size limits (20MB per file, 200MB total per voucher)

**Frontend Dependencies:**

- **React 18+** (latest stable): UI framework
- **TypeScript 5.x**: Type safety
- **MUI 6.x**: Material-UI component library for form inputs, buttons, dialogs
- **MUI X Data Grid 8.x**: Advanced data grid for voucher list with pagination, filtering, sorting
- **TanStack Query 5.x**: Data fetching, caching, and state management for voucher list/form
- **Axios** (latest stable): HTTP client for API calls
- **date-fns** (latest): Date formatting/parsing (Vietnamese locale: `dd/MM/yyyy`)
- **@supabase/supabase-js** (optional, latest): Direct Supabase Storage client if needed for frontend uploads

**Internal Module Dependencies (Epic Dependencies):**

1. **Epic 1 Dependencies:**
   - **PeriodService** (`service/impl/admin/PeriodService`): Period status validation, open period checks
   - **AccountingPeriod Entity**: Period metadata (id, period_name, status, company_id)
   - **AuditLogService** (`service/impl/audit/AuditLogService`): Immutable audit trail logging
   - **User Entity**: User context for `entered_by`, `posted_by` fields
   - **Company Entity**: Multi-tenancy context

2. **Epic 2 Dependencies:**
   - **ChartOfAccounts Entity/Repository**: Account validation, leaf-only checking, postable flag
   - **Account Metadata**: Account type, normal balance side, parent-child relationships
   - **account_controls Table** (if exists): Mandatory dimension rules (account 131 → customer_id required)
   - **Customer Entity/Repository**: For AR account dimension validation (account 131)
   - **Supplier Entity/Repository**: For AP account dimension validation (account 331) - Note: May not exist yet, deferred validation if supplier not created

**Shared Components:**

- **CompanyScopeAspect** (Epic 1): Automatic company_id filtering on all queries
- **CompanyContext** (Epic 1): ThreadLocal company context management
- **SecurityConfig** (Epic 1): JWT authentication, RBAC enforcement
- **ExceptionHandler** (Epic 1): Global error handling, standardized error responses

**Version Constraints:**

- Spring Boot: Must use 3.5.7 (matches project initialization)
- Java: Must use Java 21 (LTS, matches architecture decision)
- PostgreSQL: 15+ (Supabase managed)
- React: Latest stable 18+ (no specific version pin required for MVP)
- MUI: 6.x for Material-UI, 8.x for MUI X (matches architecture decision)

**Integration Points:**

1. **Voucher → Chart of Accounts**: Foreign key `account_id` references `chart_of_accounts(id)`, validates account exists and is postable
2. **Voucher → Period**: Foreign key `period_id` references `accounting_periods(id)`, validates period is open
3. **Voucher → Users**: Foreign keys `entered_by`, `posted_by` reference `users(id)`
4. **Voucher → Journal Entries**: One-to-many relationship, journal entries created on posting, deleted on unposting
5. **Voucher → Attachments**: One-to-many relationship, stored in Supabase Storage with metadata in `voucher_attachments` table
6. **Voucher Reversal Linkage**: Self-referential foreign keys `reversal_of`, `reversed_by` for bi-directional linking

**Future Integration Points (Post-MVP):**

- **Epic 4 (AP)**: Purchase bills may reference vouchers for manual journal entries
- **Epic 5 (AR)**: Sales invoices may reference vouchers for manual journal entries
- **Epic 6 (Cash/Bank)**: Cash receipts/payments may reference vouchers
- **Epic 7 (Reporting)**: Trial Balance, Financial Statements query journal_entries aggregated from vouchers

## Acceptance Criteria (Authoritative)

**AC1: Voucher List and Search**
- Voucher list displays columns: Voucher #, Date, Type, Amount (total_debit/total_credit), Status (draft/posted/unposted), Entered by, Posted by, AR/AP entity (if applicable), Reversal badge, Attachment count
- Filters, query text, and sort order persist per user/company session
- Live badges show count of draft vs posted vouchers
- Fuzzy/Unicode text search on voucher number, description, supports Vietnamese terms
- Multi-column sorting (e.g., date DESC + status)
- Delete allowed only for unreferenced drafts, with mandatory reason captured in audit log
- Pagination with lazy loading (20-50 items per page)
- "No vouchers" state with UI guidance to create or adjust filters
- API error recovery with retry button, error details in toast/modal
- RBAC: Non-admins see only their company's vouchers

**AC2: Voucher Form (Create/Edit) - Line Item Engine**
- Tab/keyboard navigation through header/line grid; auto-add line on last field tab-out
- Inline error indicators and tooltips for required fields/dimensions
- Date picker disables closed/future periods, jumps to latest open period
- Currency set to VND, read-only/hidden on form
- Row reordering (drag/drop); hotkeys for duplicate/delete/move
- Invalid/incomplete lines saved as draft, clearly marked; cannot post until fully valid
- List supports 20+ lines with smooth rendering (target: 20 lines < 60s entry time)
- Attachments allowed before and after draft save; inline error for type/size/virus scan
- "Save draft" is optimistic, tolerant of browser close/crash; clears edit lock if session lost >5 min
- Undo supports row/cell revert, persists on draft save
- API returns precise error map for all field validation failures (not generic 400)

**AC3: Posting, Unposting & Reversal Workflows**
- Posting changes status atomically, returns updated voucher and generated GL entries
- Unposting checks all dependencies; blocks with error if referenced (e.g., "cannot unpost - referenced in payment #P123")
- Reversal auto-creates voucher (REV-{linked}), status "posted", links bi-directionally; badge on both
- "Reversed by" badge is clickable on original and links to reversal voucher
- Export voucher and reversal trail as PDF with barcode/QR
- Double reversal not allowed (UI/API blocks and logs); 409 error on attempt
- Deletion of posted voucher forbidden; all attempted deletions logged as blocked in audit
- All failed posting (period closed, Dr≠Cr, dims missing) blocks and all errors shown at once
- Batch posting/import: any error aborts batch and logs all issues/results

**AC4: Leaf-Only and Double-Entry Validation Engine**
- UI disables and API blocks non-postable (parent) accounts, with audit log for blocked attempt
- Dr/Cr must always sum using BigDecimal; rounding logic documented
- Negative Dr/Cr values blocked, attempt logs "possible fraud" and admin alert
- Required-dimension engine is company/config-driven and all errors shown at once
- Bulk validation for all failed lines; QA test cases include field-level errors for all bulk/single paths

**AC5: Audit Trail for Voucher Lifecycle**
- Every voucher event (create/edit/post/reverse/unpost/import) generates JSON snapshot, diff hash, user/role, device/IP
- Mass/batch actions log voucher IDs, stats, start/end time, details
- "Voucher history" view shows colored field-by-field diff and plain English summary of changes
- Full audit logs exportable (PDF/JSON), with hash watermark
- Admin/audit dashboard for mass/blocked/suspicious actions; alerts sent on apparent fraud or abuse

**AC6: Period Selector & Voucher-Period Mapping**
- Always-visible period selector on voucher screens (shows current, 3 prior/next open if allowed)
- No create/post in closed/future period; API 400 and UI error with log if attempted
- On period close, batch-lock all vouchers, audit event for who/when/hash/note
- Period reopen: reason/approval, audit all attempts (even if not approved)
- System automates required reversal of prior-period adjustments by creating offsetting entry in next open period
- Period summary dashboard badge shows closing/posting flows and any pending actions

**AC7: Attachments and Voucher Documentation**
- Drag-and-drop uploader, inline image/PDF preview, download for all types; unsupported filetypes blocked and logged
- Attachments stored externally with randomized file names and metadata in DB; access limited by company
- Each download/view/delete logged with user, time, IP
- Delete allowed only for draft and by creator/admin; requires confirm modal and reason
- Download links signed/expiring (token, 10 min expiry)
- Trigger simulated virus scan; block type if fails
- Voucher icon always shows current attachment count; click for manage modal
- Manage uploads for size/multi-part upload, robust to network error

## Traceability Mapping

| AC ID | Acceptance Criteria | PRD Section | Epic Story | Spec Section(s) | Component(s)/API(s) | Test Idea |
|-------|-------------------|-------------|-----------|-----------------|---------------------|-----------|
| AC1 | Voucher list with search/filter | FR13 | 3.1 | Detailed Design: Services/Modules (VoucherList), APIs (GET /api/v1/vouchers) | VoucherList.tsx, VoucherController.getVouchers(), VoucherRepository.findAll() | Test voucher list pagination, search with Vietnamese text, filter by status/date |
| AC2 | Voucher form with line items | FR13 | 3.2 | Detailed Design: Services (VoucherService), Data Models (VoucherLine), APIs (POST/PUT /api/v1/vouchers) | features/accounting/pages/Vouchers/VoucherForm.tsx, components/voucher/VoucherLineItemGrid.tsx, VoucherController.create/update() | Test line item editing, keyboard navigation, auto-add line, draft save |
| AC3 | Posting/unposting/reversal | FR13, FR14 | 3.3 | Detailed Design: Services (VoucherPostingService, VoucherReversalService), Workflows (Posting, Reversal) | VoucherPostingService.post(), VoucherReversalService.reverse(), POST /api/v1/vouchers/{id}/post | Test posting creates journal entries, reversal links bi-directionally, unposting validates dependencies |
| AC4 | Double-entry & leaf-only validation | FR10, FR11, FR12 | 3.4 | Detailed Design: Services (VoucherValidationService), Data Models (CHECK constraints) | VoucherValidationService.validate(), COA postable flag check | Test double-entry balance (Dr=Cr), parent account blocking, dimension requirements |
| AC5 | Audit trail | FR07 | 3.5 | Non-Functional: Observability (Audit Trail), Detailed Design: Services (AuditLogService) | AuditLogService.log(), GET /api/v1/vouchers/{id}/audit-history | Test audit log creation on all actions, hash digest generation, export functionality |
| AC6 | Period selector & validation | FR04, FR05, FR06 | 3.6 | Detailed Design: Services (PeriodService), Workflows (Period Validation) | PeriodSelector.tsx, PeriodService.validate(), VoucherPostingService (period check) | Test period selector shows only open periods, blocks posting to closed period |
| AC7 | Attachments | N/A (Epic story) | 3.7 | Detailed Design: Services (AttachmentService), APIs (POST /api/v1/vouchers/{id}/attachments) | AttachmentService.upload(), Supabase Storage integration | Test file upload, signed URL generation, download/view logging, deletion restrictions |
| AC-DE | Double-entry invariant | FR13, NFR11 | 3.2, 3.4 | Detailed Design: Data Models (CHECK constraints), Services (VoucherValidationService) | Database CHECK constraint, VoucherValidationService.validateBalance() | Test Dr=Cr validation at save and post, negative amounts blocked |
| AC-LEAF | Leaf-only posting | FR10 | 3.4 | Detailed Design: Data Models (COA postable flag), Services (VoucherValidationService) | ChartOfAccounts.postable check, VoucherValidationService.validateAccount() | Test parent account blocked, only leaf accounts selectable in UI |
| AC-DIM | Mandatory dimensions | FR11 | 3.4 | Detailed Design: Data Models (voucher_lines dimensions), Services (VoucherValidationService) | account_controls table, dimension validation logic | Test account 131 requires customer_id, account 331 requires vendor_id, validation errors shown |

## Risks, Assumptions, Open Questions

**Risks:**

1. **Risk: Voucher number sequence generation race condition**
   - **Description:** Concurrent voucher creation could generate duplicate voucher numbers if sequence generation is not thread-safe
   - **Mitigation:** Use database-level sequence (PostgreSQL SERIAL/BIGSERIAL) or UUID-based voucher numbers, or implement optimistic locking with retry logic
   - **Owner:** Backend team
   - **Status:** Open - requires design decision on sequence strategy

2. **Risk: Supabase Storage availability impacts attachment uploads**
   - **Description:** If Supabase Storage is down or slow, attachment uploads fail, blocking voucher creation workflow
   - **Mitigation:** Graceful degradation: allow voucher creation without attachments, queue uploads for retry, show user-friendly error message
   - **Owner:** Backend team
   - **Status:** Mitigated - error handling specified in requirements

3. **Risk: Period validation dependency on Epic 1 completion**
   - **Description:** Voucher posting requires PeriodService/AccountingPeriod entity which may not be fully implemented in Epic 1
   - **Mitigation:** Coordinate with Epic 1 team, define interface contract early, use mock/stub if needed for parallel development
   - **Owner:** Backend team, SM coordination
   - **Status:** Open - depends on Epic 1 status

4. **Risk: Dimension validation complexity for accounts requiring customer_id/vendor_id**
   - **Description:** Accounts 131 (AR) require customer_id, but Customer entity may not exist yet (Epic 2 Story 2.2). Validation logic needs to handle missing dependencies gracefully
   - **Mitigation:** Conditional validation: if account requires dimension but entity not created, show warning but allow draft save; block posting until dimension provided
   - **Owner:** Backend team
   - **Status:** Mitigated - conditional validation approach documented

5. **Risk: Performance degradation with large voucher line counts (>50 lines)**
   - **Description:** Voucher forms with 50+ lines may cause UI lag and slow save/validation operations
   - **Mitigation:** Optimize React rendering (useMemo, virtualization), paginate line items in UI, backend batch validation, performance testing with 50+ lines
   - **Owner:** Frontend team
   - **Status:** Open - requires performance testing

6. **Risk: Unposting dependency validation complexity**
   - **Description:** Validating "no dependencies" for unposting requires checking Epic 4 (payments), Epic 5 (receipts), Epic 6 (cash transactions) which don't exist yet
   - **Mitigation:** Implement dependency check framework with extensible interface, placeholder validation for future epics, document integration points
   - **Owner:** Backend team
   - **Status:** Mitigated - extensible dependency framework specified

**Assumptions:**

1. **Assumption: Epic 1 foundation is complete**
   - Period management (PeriodService, AccountingPeriod entity) is available and functional
   - AuditLogService is implemented and ready for use
   - Company-scoped multi-tenancy (CompanyScopeAspect) is working
   - JWT authentication and RBAC enforcement is operational
   - **Validation:** Verify Epic 1 completion status before starting Epic 3 development

2. **Assumption: Epic 2 Chart of Accounts is complete**
   - ChartOfAccounts entity/repository exists with `postable` flag
   - COA hierarchy (parent-child relationships) is queryable
   - Account validation APIs are available
   - **Validation:** Confirm Epic 2 Story 2.1 is done before starting voucher validation work

3. **Assumption: Supabase Storage is configured and accessible**
   - Supabase project has storage bucket created
   - Backend has Supabase Storage credentials (environment variables)
   - Storage API is accessible from backend service
   - **Validation:** Test Supabase Storage connection during Epic 3 setup

4. **Assumption: Customer entity may not exist during Epic 3 development**
   - Account 131 dimension validation will need conditional logic
   - Supplier entity (account 331) also may not exist (Epic 2 Story 2.3)
   - **Validation:** Implement conditional dimension validation, test with/without customer/supplier entities

5. **Assumption: BigDecimal precision is sufficient for VND amounts**
   - VND amounts (no decimal places in practice) but BigDecimal handles edge cases
   - Rounding logic (if needed) will be documented
   - **Validation:** Test with large amounts (billions of VND), verify no precision loss

**Open Questions:**

1. **Question: Voucher number format - sequential vs UUID?**
   - Current spec: "VC{YYYY}-{seq}" format suggests sequential numbering
   - Consideration: Sequential numbers are user-friendly but require sequence management
   - **Decision needed:** Confirm sequence strategy (database sequence, application-level counter, or UUID)
   - **Owner:** Architect/SM
   - **Due:** Before Story 3.2 implementation

2. **Question: Reversal voucher numbering format**
   - Current spec: "REV-{original_number}"
   - Consideration: Should reversal vouchers follow same sequence or use separate prefix?
   - **Decision needed:** Finalize reversal numbering convention
   - **Owner:** Backend team
   - **Due:** Before Story 3.3 implementation

3. **Question: Draft auto-save interval (30 seconds) vs user preference**
   - Current spec: Auto-save every 30 seconds or on blur
   - Consideration: Should interval be configurable? Is 30 seconds optimal?
   - **Decision needed:** Confirm auto-save strategy (fixed interval, user-configurable, or adaptive based on edit activity)
   - **Owner:** UX Designer/Frontend team
   - **Due:** Before Story 3.2 implementation

4. **Question: Attachment file size limits (20MB per file, 200MB total)**
   - Current spec: 20MB per file, 200MB total per voucher
   - Consideration: Are these limits sufficient for typical accounting documents (invoices, receipts, PDFs)?
   - **Decision needed:** Validate file size limits with business stakeholders
   - **Owner:** Product Manager
   - **Due:** Before Story 3.7 implementation

5. **Question: Period close batch-lock mechanism**
   - Current spec: "On period close, batch-lock all vouchers"
   - Consideration: How is batch-lock implemented? Database-level constraint, application-level flag, or both?
   - **Decision needed:** Define period close locking strategy with Epic 1 team
   - **Owner:** Backend team (coordination with Epic 1)
   - **Due:** Before Story 3.6 implementation

## Test Strategy Summary

**Test Levels:**

1. **Unit Tests (Target: 60% coverage, NFR20)**
   - **VoucherService:** Test create, update, delete operations with mock repositories
   - **VoucherValidationService:** Test double-entry validation, leaf-only checking, dimension requirements
   - **VoucherPostingService:** Test posting/unposting logic, journal entry creation/deletion
   - **VoucherReversalService:** Test reversal voucher generation, bi-directional linking
   - **PeriodService integration:** Test period validation with mock period repository
   - **DTO Validation:** Test Bean Validation annotations (debit/credit mutual exclusivity, required fields)
   - **Test Framework:** JUnit 5, Mockito, AssertJ

2. **Integration Tests**
   - **Database Integration:** Test voucher CRUD with TestContainers PostgreSQL
   - **Transaction Integrity:** Test posting creates journal entries atomically (rollback on failure)
   - **Period Validation:** Test voucher creation/posting blocked for closed periods
   - **Audit Logging:** Test audit log creation for all voucher lifecycle events
   - **Company Scoping:** Test multi-tenant isolation (vouchers filtered by company_id)
   - **Test Framework:** Spring Boot Test, TestContainers, @Transactional test rollback

3. **API Integration Tests**
   - **REST Endpoints:** Test all `/api/v1/vouchers/*` endpoints with Spring MockMvc
   - **Authentication/RBAC:** Test JWT authentication, role-based access (403 for unauthorized)
   - **Error Handling:** Test validation errors (400), not found (404), conflicts (409)
   - **Pagination:** Test voucher list pagination, sorting, filtering
   - **Test Framework:** Spring Boot Test, MockMvc, @WebMvcTest

4. **Frontend Component Tests**
   - **VoucherList:** Test rendering, pagination, search, filter UI
   - **VoucherForm:** Test form state, line item editing, validation feedback
   - **VoucherLineItemGrid:** Test inline editing, auto-add line, keyboard navigation
   - **PeriodSelector:** Test period dropdown, disabled states for closed periods
   - **Test Framework:** Vitest, React Testing Library, jsdom

5. **End-to-End Tests (Deferred, Post-MVP)**
   - Full voucher creation → posting → reversal workflow
   - Multi-user concurrent voucher creation
   - Period close and voucher locking scenarios

**Test Coverage Targets:**

- **Business Logic Layer:** 60% minimum (NFR20)
- **Critical Flows:** 80% coverage (posting, validation, reversal)
- **Controllers:** 70% coverage (all endpoints, error cases)
- **Frontend Components:** 50% coverage (MVP acceptable)

**Test Data Strategy:**

- Use TestContainers for database integration tests (isolated PostgreSQL instance)
- Factory pattern for test data (VoucherTestFactory, VoucherLineTestFactory)
- Seed test data: demo company, open periods, chart of accounts, sample users
- Cleanup: @Transactional rollback for unit tests, TestContainers cleanup for integration tests

**Critical Test Cases:**

1. **Double-Entry Validation:**
   - Test Dr=Cr validation at save and post
   - Test negative amounts blocked
   - Test decimal precision with BigDecimal

2. **Leaf-Only Posting:**
   - Test parent account blocked in UI and API
   - Test only leaf/postable accounts selectable

3. **Period Validation:**
   - Test voucher creation blocked for closed period
   - Test posting blocked for closed period
   - Test period selector shows only open periods

4. **Reversal Workflow:**
   - Test reversal voucher auto-created and posted
   - Test bi-directional linking (reversal_of, reversed_by)
   - Test double reversal blocked

5. **Audit Trail:**
   - Test audit log created for all actions (create, update, post, reverse, unpost)
   - Test hash digest generation
   - Test audit log export

6. **Dimension Validation:**
   - Test account 131 requires customer_id
   - Test account 331 requires vendor_id (if supplier entity exists)
   - Test dimension validation errors shown at once

**Performance Testing:**

- Voucher list load time < 2 seconds (NFR1)
- Voucher form save < 1 second (NFR1)
- Voucher posting < 2 seconds end-to-end
- Test with 20+ concurrent users (NFR3)
- Test with 50+ line items per voucher
- Database query optimization (avoid N+1, use indexes)

**Security Testing:**

- Test JWT authentication required for all endpoints
- Test RBAC: Accountant can create, Chief Accountant can unpost
- Test company-scoped isolation (user from Company A cannot access Company B vouchers)
- Test input validation: SQL injection attempts blocked, XSS protection

**Edge Cases:**

- Voucher with 0 lines (should be invalid)
- Voucher with 1 line (Dr only or Cr only - invalid, needs balancing)
- Very large amounts (billions of VND) - test BigDecimal precision
- Voucher date in future period (should be blocked)
- Unposting voucher that has been referenced (dependency check)
- Attachment upload failure scenarios (network error, file too large, unsupported type)

