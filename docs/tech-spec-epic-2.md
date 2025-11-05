# Epic Technical Specification: Master Data Management

Date: 2025-01-31
Author: thanhtoan
Epic ID: 2
Status: Draft

---

## Overview

This epic delivers comprehensive master data management capabilities for the accounting ERP system, enabling precise, compliant management of all legal entities, customers, suppliers, and bank/cash accounts. The epic establishes robust CRUD operations, import/export flows, auditability, and baseline data integrity to ensure all transactional and reporting modules operate with clean, well-governed reference data.

Key outcomes include a preloaded TT200-compliant Chart of Accounts (COA) in a hierarchical tree view, complete customer and supplier master data management with duplicate detection and validation, bank account management with balance tracking, expanded company settings with fiscal configuration, data import/migration tools for bulk operations, and comprehensive audit trails for all master data changes.

The epic focuses on establishing the foundation for all downstream modules by ensuring master data is accurate, searchable, validated, and fully auditable before any transactional operations begin.

## Objectives and Scope

**In scope:**
- Chart of Accounts (COA) preload with TT200 structure (≥154 accounts), hierarchical tree view, search with unaccented Vietnamese support, read-only protection with admin override capability
- Customer master data CRUD with auto-generated codes (CUST-YYYY-NNNN), duplicate detection by tax code, import/export, AR summary integration
- Supplier master data CRUD with auto-generated codes (SUP-YYYY-NNNN), duplicate detection, import/export, AP summary integration
- Bank account management (cash/bank accounts) with opening balance tracking, inactivation without data loss
- Company settings expansion: fiscal year configuration, VAT rates, document sequences, report export configuration
- Data import/migration: Excel/CSV templates, validation before commit, opening balance import (pre-period-close only), error reporting
- Audit trail for all master data: field-level change tracking, user/IP/timestamp logging, exportable audit logs, blocked action detection

**Out of scope:**
- Voucher entry or posting (Epic 3)
- AP/AR transactional flows (Epics 4-5)
- Cash receipt/payment entry (Epic 6)
- Reporting generation (Epic 7)
- BI analytics (Epic 8)
- Real-time websocket updates (deferred to later epics; MVP uses polling)

## System Architecture Alignment

Aligns to Architecture decisions: Spring Boot backend with Spring Data JPA repositories, React frontend (feature-first) with shadcn/ui layout and MUI used where appropriate for dense table displays, PostgreSQL with unaccent extension for Vietnamese search, multi-tenancy via company_id filtering at DB and API layers.

Epic-to-architecture mapping: Epic 2 components reside under `controller/customer/`, `controller/supplier/`, `controller/chart-of-accounts/` on backend and `features/accounting/pages/ChartOfAccounts.tsx` (+ future customers/suppliers paths) on frontend. Database tables: `customers`, `suppliers`, `chart_of_accounts`, `bank_accounts` (extends Epic 1 `companies` entity). Search uses PostgreSQL FTS + unaccent extension as per Architecture ADR for Vietnamese search support (NFR23).

Company scoping enforced via `CompanyContext` filter established in Epic 1; all repositories automatically filter by current company context. Audit logging uses `AuditLog` entity pattern from Epic 1 foundation.

## Detailed Design

### Services and Modules

**Backend Services:**

| Service/Module | Location | Responsibilities |
|---------------|----------|------------------|
| `ChartOfAccountsService` | `service/chart/ChartOfAccountsService` | COA hierarchy retrieval, filtering (postable, code prefix), search with unaccented Vietnamese support, validation for leaf-only accounts |
| `CustomerService` | `service/customer/CustomerService` | CRUD operations, auto-generate codes (CUST-YYYY-NNNN), duplicate detection by tax code, import/export, AR summary aggregation |
| `SupplierService` | `service/supplier/SupplierService` | CRUD operations, auto-generate codes (SUP-YYYY-NNNN), duplicate detection, import/export, AP summary aggregation |
| `BankAccountService` | `service/bank/BankAccountService` | CRUD for cash/bank accounts, opening balance tracking, inactivation with referential integrity checks |
| `CompanySettingsService` | `service/company/CompanySettingsService` | Fiscal year configuration, VAT rates management, document sequence configuration, report export settings |
| `ImportService` | `service/import/ImportService` | Excel/CSV parsing, validation before commit, atomic bulk creation, error report generation, opening balance import |
| `AuditLogService` | `service/audit/AuditLogService` | Field-level change tracking, exportable audit logs, blocked action detection, data integrity checks |

**Backend Controllers:**

- `ChartOfAccountsController`: GET `/api/v1/chart-of-accounts` (hierarchy, filters), GET `/api/v1/chart-of-accounts/{id}`
- `CustomerController`: GET/POST/PUT/DELETE `/api/v1/customers`, GET `/api/v1/customers/{id}/audit`
- `SupplierController`: GET/POST/PUT/DELETE `/api/v1/suppliers`, GET `/api/v1/suppliers/{id}/audit`
- `BankAccountController`: GET/POST/PUT `/api/v1/bank-accounts` (DELETE blocked if referenced)
- `CompanySettingsController`: GET/PUT `/api/v1/company-settings`
- `ImportController`: POST `/api/v1/import/customers`, `/api/v1/import/suppliers`, `/api/v1/import/opening-balances`

**Frontend Components (feature-first):**

- `features/accounting/pages/ChartOfAccounts.tsx`: Tree view with expand/collapse, search, filters
- `features/company/pages/CompanySettings.tsx`: Fiscal year, VAT rates, document sequences, report config
- `components/import/ImportWizard.tsx`: Step-by-step import flow with validation preview
- `components/audit/AuditLogViewer.tsx`: Filterable audit log display per entity

### Data Models and Contracts

**Database Tables (PostgreSQL via Supabase):**

```sql
-- Chart of Accounts (extends company-scoped pattern)
chart_of_accounts (
  id UUID PRIMARY KEY,
  company_id UUID REFERENCES companies(id),
  code VARCHAR(20) NOT NULL,  -- e.g., "131", "1111"
  name VARCHAR(255) NOT NULL,  -- Vietnamese with accents
  type VARCHAR(50),  -- Asset, Liability, Equity, Revenue, Expense
  normal_side VARCHAR(10),  -- Debit or Credit
  postable BOOLEAN DEFAULT false,
  parent_id UUID REFERENCES chart_of_accounts(id),
  ordering_position INTEGER,
  UNIQUE(company_id, code)
);

-- Customers
customers (
  id UUID PRIMARY KEY,
  company_id UUID REFERENCES companies(id),
  code VARCHAR(50) NOT NULL,  -- CUST-YYYY-NNNN format
  name VARCHAR(255) NOT NULL,
  tax_code VARCHAR(20) UNIQUE,  -- Per company
  email VARCHAR(255),
  phone VARCHAR(20),
  address TEXT,
  status VARCHAR(20) DEFAULT 'active',  -- active/inactive
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  UNIQUE(company_id, code)
);

-- Suppliers (similar structure to customers)
suppliers (
  id UUID PRIMARY KEY,
  company_id UUID REFERENCES companies(id),
  code VARCHAR(50) NOT NULL,  -- SUP-YYYY-NNNN format
  name VARCHAR(255) NOT NULL,
  tax_code VARCHAR(20) UNIQUE,  -- Per company
  email VARCHAR(255),
  phone VARCHAR(20),
  address TEXT,
  status VARCHAR(20) DEFAULT 'active',
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  UNIQUE(company_id, code)
);

-- Bank Accounts
bank_accounts (
  id UUID PRIMARY KEY,
  company_id UUID REFERENCES companies(id),
  account_number VARCHAR(100) NOT NULL,
  bank_name VARCHAR(255),
  branch VARCHAR(255),
  type VARCHAR(20),  -- cash or bank
  opening_balance DECIMAL(18,2) DEFAULT 0,
  currency VARCHAR(3) DEFAULT 'VND',
  status VARCHAR(20) DEFAULT 'active',
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  UNIQUE(company_id, account_number)
);

-- Company Settings (extends companies table or separate table)
company_settings (
  company_id UUID PRIMARY KEY REFERENCES companies(id),
  fiscal_year_start DATE,
  currency VARCHAR(3) DEFAULT 'VND',
  vat_rates JSONB,  -- Array of {rate: 0|5|10|exempt, description}
  document_sequences JSONB,  -- Sequence configs per document type
  report_logo_url VARCHAR(500),
  report_footer_lines TEXT[],
  updated_at TIMESTAMP
);
```

**API Request/Response Contracts:**

- Customer Create: `POST /api/v1/customers` → `{ name, tax_code, email, phone, address }` → Returns `{ data: { id, code, ... }, meta }`
- Customer Search: `GET /api/v1/customers?q=search&status=active&page=0&size=20` → Returns paginated list
- COA Hierarchy: `GET /api/v1/chart-of-accounts?postable=true&codePrefix=131` → Returns tree structure
- Import Request: `POST /api/v1/import/customers` (multipart/form-data with Excel file) → Returns `{ data: { success: 10, errors: [...] }, meta }`

### Workflows and Sequencing

**COA Initialization:**
1. Flyway migration `V{X}__seed_tt200_chart_of_accounts.sql` seeds ≥154 accounts on company creation
2. Migration validates no duplicate codes per company
3. Frontend loads COA tree on page load, caches for session

**Customer Creation Flow:**
1. User opens Customer form → System auto-generates next code (CUST-2025-0001)
2. User enters name, tax code, etc. → Real-time validation (email format, tax code uniqueness check)
3. On submit → Backend validates duplicate by tax code → Creates record → Returns created customer
4. Audit log entry created automatically via `@Audited` annotation or service layer

**Import Workflow:**
1. User uploads Excel/CSV → Backend validates headers and data types
2. System parses all rows, collects validation errors → Returns error report if validation fails
3. User reviews errors, fixes file → Re-submits
4. On validation pass → Backend executes atomic bulk insert within transaction
5. Each row creates individual audit log entry
6. Returns summary: `{ inserted: 50, skipped: 2, errors: 0 }`

**Bank Account Inactivation:**
1. User attempts to inactivate account → System checks for references in vouchers
2. If referenced → Block with modal showing reference count
3. If not referenced → Inactivate → Remove from active pickers → Preserve historical links
4. Audit log records inactivation with reason

## Non-Functional Requirements

### Performance

**Targets (per PRD NFR1-NFR4):**
- List view pagination: Initial load < 2s for 20 records; search with Vietnamese unaccented matching < 1s
- COA tree render: < 1s for full hierarchy (154+ accounts)
- Import validation: < 5s for 1000-row Excel file parsing and validation
- Export to Excel/CSV: < 3s for typical master data list (500 records)

**Database Optimization:**
- Indexes on `customers(tax_code, company_id)`, `suppliers(tax_code, company_id)` for duplicate detection
- Index on `chart_of_accounts(code, company_id, parent_id)` for hierarchy queries
- Index on `bank_accounts(account_number, company_id)` for uniqueness checks
- Use `@EntityGraph` or JOIN FETCH to avoid N+1 queries in list views
- Connection pooling: HikariCP defaults (10 connections, configurable)

**Frontend Optimization:**
- MUI Data Grid virtualization for large lists (>100 records)
- Debounced search input (300ms) to reduce API calls
- Client-side caching of COA tree structure per session
- Lazy loading of AR/AP summaries in customer/supplier details

### Security

**Authentication & Authorization (per PRD NFR5-NFR9):**
- All endpoints require JWT authentication (from Epic 1)
- RBAC enforcement: Admin/Chief Accountant can edit COA settings; Accountants can create/edit customers/suppliers; all roles can view
- API-level permission checks: 403 on unauthorized access (e.g., accountant attempting COA edit)
- Company scoping: All queries automatically filtered by `company_id` via `CompanyContext`

**Data Protection:**
- Input validation: Tax code format validation, email format, phone number validation
- SQL injection prevention: Parameterized queries via JPA (Spring Data JPA)
- XSS protection: React escapes user input by default; sanitize import data
- Audit trail: All CRUD operations logged with user, IP, timestamp, field-level diffs

**Import Security:**
- File size limits: Max 10MB per import file
- File type validation: Only .xlsx, .xls, .csv allowed
- Virus scan simulation: Block suspicious file types (executables, scripts)
- Bulk operation rate limiting: Max 1000 rows per import; admin override available

### Reliability/Availability

**Data Integrity:**
- Unique constraints at DB level: `UNIQUE(company_id, code)` for customers, suppliers, COA codes
- Foreign key constraints: `company_id` references `companies(id)`, `parent_id` references `chart_of_accounts(id)`
- Transaction boundaries: Import operations use `@Transactional`; rollback on any validation failure
- Referential integrity: Cannot delete bank account if referenced in vouchers (soft delete via inactivation)

**Error Handling:**
- Graceful degradation: If COA seed fails, system continues with empty COA (warning logged)
- Import validation: All errors collected before commit; atomic rollback on failure
- User-friendly error messages: Vietnamese language (NFR15), clear actionable messages
- Retry logic: Import failures logged with retry button; exponential backoff for external dependencies

**Backup & Recovery:**
- Audit logs append-only; cannot be modified or deleted (per NFR8)
- Export functionality: All master data exportable for backup before migration
- Data retention: Audit logs retained for 10 years per NFR10 (Circular 200 compliance)

### Observability

**Logging (per PRD NFR24-NFR25):**
- Structured JSON logging: All master data operations log `{ entity, action, userId, companyId, changes, timestamp }`
- Request ID tracking: Correlation ID in all logs for request tracing
- Sensitive data masking: Password hashes, tax codes partially masked in logs (e.g., `TAX***1234`)
- Slow query logging: Log queries > 1s with query plan and execution time

**Monitoring:**
- Health check endpoint: `/health` includes DB connectivity, Redis availability (if used)
- Import job monitoring: Track import success rate, average processing time, error frequency
- Audit log metrics: Count of blocked actions, audit log export frequency

**Debugging Support:**
- Audit log explorer: Filterable UI for troubleshooting data issues
- Data integrity check endpoint: `/api/v1/admin/integrity-check` for orphan detection
- Export audit trails: CSV/JSON export for offline analysis

## Dependencies and Integrations

**New Backend Dependencies Required:**

| Dependency | Version | Purpose |
|------------|---------|---------|
| Apache POI | 5.2.5 | Excel file parsing for import/export (.xlsx, .xls) |
| Apache POI-OOXML | 5.2.5 | Office OpenXML format support |
| commons-csv | 1.10.0 | CSV parsing for import functionality |
| PostgreSQL `unaccent` extension | Built-in | Vietnamese unaccented search support (NFR23) |

**Existing Dependencies (from Epic 1):**
- Spring Boot 3.5.7, Spring Data JPA, Spring Validation (already in pom.xml)
- Flyway (for COA seed migration)
- Lombok (for entity boilerplate reduction)
- SpringDoc OpenAPI (for API documentation)

**Frontend Dependencies Required:**

| Dependency | Version | Purpose |
|------------|---------|---------|
| @tanstack/react-table | 8.x | Dense table display for customer/supplier lists (per Architecture ADR-005) |
| xlsx | Latest | Excel export functionality on frontend (alternative: use backend export) |
| @dnd-kit/core | Latest | Drag-and-drop for COA tree reordering (optional, modern alternative to react-beautiful-dnd) |
| lucide-react | Latest | Icons for UI components |

**Existing Frontend Dependencies:**
- Shadcn UI + Tailwind CSS (already in package.json per Architecture)
- React Router (for navigation)
- Axios (for API calls)
- Zod (for form validation)

**Database Extensions:**

- PostgreSQL `unaccent` extension must be enabled on Supabase instance for Vietnamese search:
  ```sql
  CREATE EXTENSION IF NOT EXISTS unaccent;
  ```
- Custom function for unaccented search (to be created in Flyway migration):
  ```sql
  CREATE OR REPLACE FUNCTION unaccent_search(text) RETURNS text AS $$
    SELECT unaccent($1);
  $$ LANGUAGE SQL IMMUTABLE;
  ```

**Integration Points:**

1. **Epic 1 Integration:**
   - Uses `CompanyContext` filter for multi-tenancy
   - Extends `AuditLog` entity pattern from Epic 1
   - Uses JWT authentication established in Epic 1

2. **Future Epic Integration:**
   - Customer/Supplier data referenced by Epic 5 (AR) and Epic 4 (AP)
   - Bank accounts referenced by Epic 6 (Cash & Bank Management)
   - COA referenced by Epic 3 (Voucher Engine) for account selection
   - Company settings used by Epic 7 (Reporting) for report configuration

3. **External Services:**
   - Supabase Storage (if needed for import file temporary storage)
   - No external API dependencies for MVP

**Migration Dependencies:**

- Flyway migration `V{X}__seed_tt200_chart_of_accounts.sql` must run after company creation
- Migration depends on `companies` table from Epic 1
- COA seed data source: TT200 standard accounts list (to be provided as SQL INSERT statements)

## Acceptance Criteria (Authoritative)

**Story 2.1: Chart of Accounts (COA) – TT200 Preload & Read-Only Management**

1. Full TT200 COA (≥154 accounts) seeded via Flyway migration on company initialization; migration reusable as import template for fresh companies.
2. Each account entity has: `code` (VARCHAR(20)), `name` (Vietnamese with accents), `type` (Asset/Liability/Equity/Revenue/Expense), `normal_side` (Debit/Credit), `postable` (BOOLEAN), `parent_id` (nullable FK), `ordering_position` (INTEGER).
3. Frontend tree view supports expand/collapse to 3+ levels, keyboard and mouse navigation, visual hierarchy display.
4. Search input with typeahead: supports account code (exact/prefix) and Vietnamese name with unaccented matching (e.g., "nha" matches "nhà").
5. Filtering capabilities: by account class (Asset/Income/etc.), `postable=true` toggle, code prefix filter (e.g., "131" returns all 131xx accounts).
6. "View Details" modal displays all account fields; "Postable" badge/icon visually distinguishes postable accounts.
7. COA edit protection: Admin edit attempts show warning modal ("preconfigured, only editable by superadmin"); UI routes for edit/delete blocked in MVP; all blocked attempts logged in audit.
8. Voucher account picker validation: Only postable-leaf accounts appear in pickers; root/parent accounts blocked (client and server validation).
9. API endpoint `GET /api/v1/chart-of-accounts` returns hierarchical structure; supports query params: `postable=true`, `codePrefix=131`, `parentId={id}` for cascading selectors.
10. Unique constraint enforced: `UNIQUE(company_id, code)`; duplicate code attempts fail with detailed error message showing conflicting account.
11. Balance column (optional MVP): Shows real-time balance for account in current period (query from `journal_entries` aggregated by account_id).

**Story 2.2: Customer Master Data Management (CRUD)**

1. List view: Pagination (20 per page default), sorting by name/code, filter by status (active/inactive), typeahead search with Vietnamese unaccented support.
2. Create/edit form: Required fields validated (name, tax_code); optional fields (email, phone, address) with format validation (email regex, phone format).
3. Auto-generated code format: `CUST-YYYY-NNNN` (e.g., CUST-2025-0001); sequence increments globally per company even if prior codes deleted/deactivated.
4. Duplicate detection: Reject by `tax_code` uniqueness per company; optionally check email/cell uniqueness; error message displays exactly which field conflicts.
5. Inactive customers: Display grayed-out or moved to separate tab/bottom of list; inactive status visually distinguished.
6. Deactivate/reactivate: Action available with tooltip explaining linked AR data (invoices, payments) remains intact.
7. Real-time updates: CRUD actions reflect changes via polling (5-minute interval) or websockets if implemented; multiple users see live changes.
8. AR summary integration: Customer details panel shows open invoices count, total owed amount, average payment days (calculated from linked AR data in future epics).
9. Export functionality: Customer list exportable to CSV/Excel with applied filters; export includes all visible columns.
10. Delete protection: Attempt to delete customer with existing invoices/payments blocked with explanatory modal showing reference count.
11. Audit trail: All field edits, activations/deactivations, import actions logged with before/after data, user, timestamp, IP address.

**Story 2.3: Supplier Master Data Management (CRUD)**

1-11. Same acceptance criteria as Story 2.2, with substitutions:
   - "Customer" → "Supplier"
   - "CUST-YYYY-NNNN" → "SUP-YYYY-NNNN"
   - "AR" → "AP"
   - "invoices/payments" → "bills/payments"
   - `/api/v1/customers` → `/api/v1/suppliers`

**Story 2.4: Bank Account Management (CRUD)**

1. List view: Shows all cash and bank accounts; filters by type (cash/bank) and status (active/inactive); sorting by last balance update date.
2. Create form: `account_number` (required, unique per company), `bank_name` (validated from predefined list, open entry allowed), `opening_balance` (DECIMAL, must match accounting entry at migration time).
3. Edit audit trail: All field changes logged with reason if value changed; before/after values stored in audit log.
4. Delete protection: Cannot delete bank/cash account if referenced in any voucher, period, or reconciliation; blocking modal shows reference details.
5. Inactivation: Closing (inactivating) account removes from active pickers; preserves referential integrity with historical vouchers; status change logged.
6. Balance tooltip: Bank account selector displays tooltip showing current balance and prior period balance on hover.
7. Export: Account list and balances exportable to Excel/CSV for audit purposes; export includes all visible fields.
8. Multi-company scoping: All accounts filtered by `company_id`; company context enforced at API and DB levels.

**Story 2.5: Company Settings Expansion (Advanced Fields)**

1. Fiscal year configuration: Pre-selected on company creation; editable for future years only; no overlap validation (e.g., cannot have 2025-01-01 to 2025-12-31 overlap with 2026-01-01 to 2026-12-31).
2. Currency setting: VND locked/read-only in MVP (per PRD scope).
3. VAT rates management: Admin can configure array of VAT rates (0%, 5%, 10%, exempt) with descriptions; uniqueness validation per company.
4. Document sequences: Optionally editable prefix/format; validation prevents collision with existing record codes (e.g., cannot change voucher prefix if vouchers already use old prefix).
5. Report export configuration: Logo upload (URL or file), footer lines (TEXT array); preview shows financial statement mockup with these values before saving.
6. Transactional updates: All field edits succeed fully with validations or fail atomically leaving old values untouched; rollback on any validation error.
7. Full audit trail: Every settings change logged with field name, old value, new value, user, timestamp, reason if provided.

**Story 2.6: Data Import & Migration**

1. Import wizard: Accepts template files (Excel .xlsx/.xls, CSV); validates required headers and data types before processing.
2. Pre-commit validation: Parser validates all rows before saving; returns error summary with row numbers and reasons (e.g., "Row 12: Invalid tax code format", "Row 25: Duplicate supplier tax_code").
3. Atomic bulk creation: On commit, all master records created within single transaction; if any row fails validation, entire import rolls back (no partial import).
4. Opening balance import: Only available to admin role; only before first period close; enforces double-entry invariant (Total Debit = Total Credit); warns if negative or zero-value entries detected.
5. Error reporting: All import errors downloadable as Excel/CSV report for offline review and correction.
6. Migration logging: Import script logs result summary: `{ inserted: 50, skipped: 2, errors: 3, reference_to_audit_log: "audit-123" }`.
7. Demo import: Sample data template compatible with dev demo company; supports quick setup for development/testing.

**Story 2.7: Audit Trail & Data Integrity for Master Data**

1. Per-entity audit log UI: Each customer/supplier/account has "Audit Log" tab showing all changes; filterable by date range, user, action type (create/edit/delete/activate).
2. Audit log API: `GET /api/v1/{entity}/{id}/audit` with filters (date, user, action); respects RBAC (users see only their company's audit logs).
3. Log format: `{ entity_type, entity_id, action, fields_changed: [{field, old_value, new_value}], user_id, user_email, user_role, timestamp, ip_address, user_agent }`.
4. Blocked action detection: Failed modification attempts (e.g., delete protected COA account) logged with reason and blocked action flag.
5. Data integrity check: Admin endpoint `/api/v1/admin/integrity-check` runs background job to detect orphaned master records; returns zero orphans in normal state.
6. Bulk action transparency: Import operations create separate audit entry per imported row for full traceability.

## Traceability Mapping

| AC | Spec Section(s) | Component(s)/API(s) | Test Idea |
|----|-----------------|---------------------|------------|
| 2.1.1 | PRD FR09, Epic 2.1 AC1 | Flyway migration `V{X}__seed_tt200_chart_of_accounts.sql`, `ChartOfAccountsService.seedCOA()` | Integration test: Verify ≥154 accounts seeded after company creation |
| 2.1.4 | PRD FR12, NFR23 | `ChartOfAccountsService.search()`, PostgreSQL `unaccent` function, `GET /api/v1/chart-of-accounts?q=nha` | E2E test: Search "nha" matches "nhà cung cấp" |
| 2.1.8 | PRD FR10 | `VoucherAccountValidator.validateLeafOnly()`, frontend account picker filter | Unit test: Non-leaf account selection rejected with 400 error |
| 2.2.3 | Epic 2.2 AC3 | `CustomerService.generateCode()`, `CustomerRepository.getNextSequence()` | Unit test: Code increments CUST-2025-0001 → CUST-2025-0002 |
| 2.2.4 | Epic 2.2 AC4 | `CustomerService.validateDuplicate()`, `UNIQUE(company_id, tax_code)` constraint | Integration test: Duplicate tax_code rejected with conflict message |
| 2.3.1-11 | PRD FR17, Epic 2.3 | `SupplierController`, `SupplierService`, `suppliers` table | Same test patterns as Story 2.2 with supplier substitutions |
| 2.4.4 | Epic 2.4 AC4 | `BankAccountService.validateReferences()`, foreign key check | Integration test: Delete attempt with voucher reference blocked |
| 2.5.1 | PRD FR04, Epic 2.5 AC1 | `CompanySettingsService.updateFiscalYear()`, overlap validation | Unit test: Overlapping fiscal years rejected |
| 2.6.2-3 | Epic 2.6 AC2-3 | `ImportService.validateAndImport()`, `@Transactional` annotation | Integration test: Invalid row causes full rollback, no partial import |
| 2.7.1 | PRD FR07, NFR8 | `AuditLogService.getAuditLog()`, `GET /api/v1/customers/{id}/audit` | Integration test: Audit log returns chronological change history |
| 2.7.5 | Epic 2.7 AC5 | `AdminController.integrityCheck()`, orphan detection query | Unit test: Integrity check returns zero orphans for clean database |

**Traceability to PRD Functional Requirements:**
- AC 2.1.x → PRD FR09 (View COA per TT200), FR10 (Leaf-only posting), FR12 (COA Search)
- AC 2.2.x, 2.3.x → PRD FR22 (Customer CRUD), FR17 (Supplier CRUD)
- AC 2.4.x → PRD FR27 (Manage Cash/Bank Accounts)
- AC 2.5.x → PRD FR03 (Company Profile Management), FR04 (Accounting Period Management)
- AC 2.6.x → PRD FR43 (Import Data from Excel)
- AC 2.7.x → PRD FR07 (Basic Audit Trail), NFR8 (Immutable Audit Trail)

## Risks, Assumptions, Open Questions

**Risks:**

1. **Risk: TT200 COA Seed Data Availability**
   - **Impact:** High - Epic cannot proceed without complete COA data
   - **Mitigation:** Obtain official TT200 account list (154+ accounts) before implementation; create fallback seed script with sample subset if official data delayed
   - **Owner:** Development team to source TT200 data; Architect to validate structure

2. **Risk: Vietnamese Unaccented Search Performance**
   - **Impact:** Medium - Poor performance could degrade user experience
   - **Mitigation:** Implement proper indexes on unaccented search columns; benchmark with realistic data volume (1000+ customers); consider materialized search columns if needed
   - **Owner:** Backend developer to optimize queries

3. **Risk: Import File Size Limits**
   - **Impact:** Low - Large imports may timeout or consume excessive memory
   - **Mitigation:** Enforce 10MB file size limit; implement streaming parsing for large files; add progress indicators for >100 row imports
   - **Owner:** Import service developer

4. **Risk: Referential Integrity on Deletion**
   - **Impact:** Medium - Incomplete validation could orphan transactions
   - **Mitigation:** Implement comprehensive foreign key checks before Epic 3 (Voucher Engine) depends on master data; use database foreign key constraints as safety net
   - **Owner:** Database architect

**Assumptions:**

1. **PostgreSQL unaccent extension available:** Assumed Supabase instance allows `CREATE EXTENSION unaccent`; if not, fallback to application-level normalization
2. **AR/AP summaries deferred:** Customer/Supplier AR/AP summaries (Story 2.2 AC8, 2.3 AC8) will return placeholder data until Epics 4-5 complete; UI designed to handle null/zero values gracefully
3. **Real-time updates via polling:** MVP uses 5-minute polling for multi-user updates; websockets deferred to later epic
4. **COA read-only in MVP:** Assumed COA edits blocked in MVP; admin override capability planned but not implemented until Post-MVP
5. **Excel import library stable:** Apache POI 5.2.5 assumed compatible with Spring Boot 3.5.7; tested before Epic start

**Open Questions:**

1. **Q: Should COA balance column query from journal_entries in real-time or use cached materialized view?**
   - **Decision needed:** Real-time (more accurate, slower) vs. Cached (faster, may be stale)
   - **Recommendation:** Cache with 5-minute TTL; invalidate on voucher post
   - **Owner:** Architect to decide before Story 2.1 implementation

2. **Q: What is the maximum expected customer/supplier count per company for MVP?**
   - **Decision needed:** To determine pagination strategy and search optimization
   - **Recommendation:** Assume 1000-5000 records per company; implement server-side pagination with 20/page
   - **Owner:** Product Owner to confirm with stakeholders

3. **Q: Should import errors block entire import or allow partial import with error report?**
   - **Decision:** Atomic rollback (current spec) vs. Partial import with errors logged
   - **Recommendation:** Atomic rollback per Story 2.6 AC3; user fixes file and re-imports
   - **Owner:** SM/PO decision (already in AC, confirmed)

4. **Q: VAT rates stored as JSONB or separate table?**
   - **Decision:** JSONB in company_settings (current spec) vs. Normalized `vat_rates` table
   - **Recommendation:** JSONB for MVP (simpler, sufficient for 4-5 rates); normalize in Post-MVP if needed
   - **Owner:** Database architect decision

## Test Strategy Summary

**Test Levels:**

1. **Unit Tests (Target: 70%+ coverage for service layer)**
   - `CustomerService.generateCode()`: Verify sequence increment logic
   - `CustomerService.validateDuplicate()`: Test tax_code uniqueness validation
   - `ImportService.validateRow()`: Test Excel/CSV parsing and validation rules
   - `ChartOfAccountsService.buildHierarchy()`: Test tree structure construction
   - Mock repositories and external dependencies; use JUnit 5 + Mockito

2. **Integration Tests (Target: Critical paths)**
   - COA seed migration: Verify ≥154 accounts seeded after company creation
   - Customer CRUD flow: Create → Read → Update → Deactivate → Reactivate
   - Import workflow: Upload Excel → Validate → Commit → Verify audit logs
   - Duplicate detection: Attempt duplicate tax_code → Verify rejection
   - Use TestContainers with PostgreSQL; test against real database schema

3. **API Tests (Target: All endpoints)**
   - REST endpoints: Test all CRUD operations via `@WebMvcTest` or `MockMvc`
   - Authorization: Verify RBAC enforcement (403 on unauthorized access)
   - Validation: Test 400 errors for invalid input
   - Pagination: Test page/size parameters, total count accuracy
   - Use Spring Boot Test Slice annotations for faster tests

4. **Frontend Component Tests (Target: Key interactions)**
   - Customer list: Test pagination, sorting, filtering, search
   - Import wizard: Test file upload, validation display, error handling
   - COA tree: Test expand/collapse, search, filter
   - Use Vitest + React Testing Library; mock API responses

**Test Coverage Targets:**

- Service layer: ≥70% line coverage
- Repository layer: ≥60% (test custom query methods)
- Controller layer: ≥80% (test all endpoints, error cases)
- Critical paths: 100% coverage (import, duplicate detection, audit logging)

**Test Data Strategy:**

- Use TestContainers for integration tests with isolated database per test class
- Seed test data via Flyway migrations or `@Sql` scripts
- Use factory pattern for creating test entities (Builder pattern with Lombok)
- Clean up test data after each test method (`@DirtiesContext` or manual cleanup)

**Performance Testing:**

- Import performance: Measure time for 1000-row Excel import (< 5s target)
- Search performance: Measure Vietnamese unaccented search with 5000 customers (< 1s target)
- COA tree render: Measure initial load time for 154 accounts (< 1s target)
- Use `@Timed` annotation or JMeter for load testing if needed

**Accessibility Testing:**

- WCAG AA compliance: Test keyboard navigation, screen reader support
- Focus management: Test tab order, focus indicators
- Color contrast: Verify text meets 4.5:1 ratio for normal text
- Use automated tools (axe-core) + manual testing

**Regression Testing:**

- Ensure Epic 1 functionality unaffected: Authentication, RBAC, company context
- Verify multi-tenancy isolation: Data from Company A not visible to Company B
- Test audit log integrity: Verify append-only behavior, no modification possible

