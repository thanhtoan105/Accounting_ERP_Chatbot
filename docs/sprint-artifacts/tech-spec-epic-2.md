# Epic Technical Specification: Master Data Management

Date: 2025-02-10
Author: thanhtoan
Epic ID: 2
Status: Draft

---

## Overview

Epic 2: Master Data Management establishes the foundational data governance layer for the accounting platform. This epic enables precise, compliant master data management for all legal entities, customers, suppliers, and bank/cash accounts, with robust import/export flows, auditability, and baseline data integrity. The epic ensures all subsequent transactional and reporting modules operate with clean, well-governed reference data that adheres to Vietnamese accounting standards (TT200) and enterprise compliance requirements.

The epic builds upon Epic 1's foundation (authentication, RBAC, company setup) and provides the master data infrastructure required for Epic 3 (Voucher Engine) and subsequent AP/AR modules. Key deliverables include the preconfigured TT200 Chart of Accounts, comprehensive CRUD operations for customers and suppliers, bank account management, enhanced company settings, data import/migration capabilities, and comprehensive audit trails.

## Objectives and Scope

### In Scope

- **Chart of Accounts (COA) Management**: Preload and manage TT200-compliant Chart of Accounts (≥154 accounts) with table-based UI supporting CRUD operations, search/filter capabilities, and extended fields (Account Name in English, Description, Status). Support for 3-level hierarchy (1xx, 11x, 111), postable flag enforcement, and leaf-only posting validation. CRUD operations available via Sheet modal form with RBAC protection (admin/chief_accountant roles).
- **Customer Master Data**: Full CRUD operations for customer records with auto-generated codes (CUST-YYYY-NNNN), duplicate detection by tax code, status management (active/inactive), AR summary integration, and import/export functionality.
- **Supplier Master Data**: Full CRUD operations for supplier records with auto-generated codes (SUP-YYYY-NNNN), duplicate detection, AP summary integration, and import/export functionality.
- **Bank Account Management**: CRUD for cash and bank accounts with unique account numbers, balance tracking, referential integrity protection, and company scoping.
- **Company Settings Expansion**: Advanced fiscal year configuration, VAT rate management, document sequence configuration, and report export branding settings.
- **Data Import & Migration**: Excel/CSV import wizards for customers, suppliers, and opening balances with validation, error reporting, and atomic transaction handling.
- **Audit Trail & Data Integrity**: Comprehensive audit logging for all master data changes, orphan detection, integrity checks, and audit log exploration APIs.

### Out of Scope

- Direct integration with external accounting systems (deferred to future epic)
- Multi-currency support (locked to VND per PRD)
- Advanced workflow automation for master data approval (basic RBAC only)
- Real-time synchronization with external data sources
- Advanced data quality scoring or automated data cleansing
- Master data versioning or historical snapshots (audit trail provides change history)

## System Architecture Alignment

This epic aligns with the established Spring Boot + React architecture:

- **Backend Components**: 
  - Controllers: `controller/customer/`, `controller/supplier/`, `controller/chartofaccounts/`, `controller/bankaccount/`
  - Services: Business logic for CRUD operations, validation, import/export processing
  - Repositories: JPA repositories with company-scoped filtering (extends Epic 1's multi-tenancy pattern)
  - Entities: `Customer`, `Supplier`, `ChartOfAccount`, `BankAccount` extending `CompanyScopedEntity`

- **Frontend Components**:
  - Feature modules: `features/accounting/pages/ChartOfAccounts.tsx` (table view with CRUD), `features/customers/`, `features/suppliers/`
  - Shared components: Master data tables using TanStack Table (data-table-09 pattern), Sheet modals for forms, searchable comboboxes, import wizards, audit log viewers
  - Services: API clients for master data operations with CRUD support
  - Components: `components/account/ChartOfAccountFormSheet.tsx` (add/edit form), `components/account/AccountCombobox.tsx` (parent account selection)

- **Database Schema**: 
  - Tables: `customers`, `suppliers`, `chart_of_accounts`, `bank_accounts` (all with `company_id` for multi-tenancy)
  - Audit tables: `audit_logs` for change tracking
  - Constraints: Foreign keys, unique constraints, referential integrity

- **Integration Points**:
  - Uses Epic 1's company context and RBAC enforcement
  - Prepares data structures for Epic 3's voucher engine (account validation, customer/supplier references)
  - Foundation for Epic 4 (AP) and Epic 5 (AR) modules

## Detailed Design

### Services and Modules

#### Backend Services

**ChartOfAccountService**
- **Responsibilities**: COA hierarchy management, TT200 seeding, postable validation, search/filter operations, CRUD operations (create, update, soft delete, activate, deactivate)
- **Inputs**: Account code, name, nameEnglish, description, active status, filters (postable, code prefix, account class, active), create/update requests
- **Outputs**: Hierarchical account tree, filtered account lists, account details, created/updated account DTOs
- **Owner**: Backend team

**CustomerService**
- **Responsibilities**: Customer CRUD, code generation (CUST-YYYY-NNNN), duplicate detection, AR summary aggregation
- **Inputs**: Customer data (name, tax code, contact info), filters, search terms
- **Outputs**: Customer records, paginated lists, AR summaries
- **Owner**: Backend team

**SupplierService**
- **Responsibilities**: Supplier CRUD, code generation (SUP-YYYY-NNNN), duplicate detection, AP summary aggregation
- **Inputs**: Supplier data (name, tax code, contact info), filters, search terms
- **Outputs**: Supplier records, paginated lists, AP summaries
- **Owner**: Backend team

**BankAccountService**
- **Responsibilities**: Bank/cash account CRUD, balance tracking, referential integrity checks
- **Inputs**: Account data (number, bank name, type, opening balance)
- **Outputs**: Account records, balance information
- **Owner**: Backend team

**CompanySettingsService**
- **Responsibilities**: Extended company configuration (fiscal year, VAT rates, document sequences, report branding)
- **Inputs**: Settings updates, validation rules
- **Outputs**: Updated company settings, validation results
- **Owner**: Backend team

**ImportExportService**
- **Responsibilities**: Excel/CSV parsing, validation, bulk import, export generation
- **Inputs**: Import files (Excel/CSV), import templates, export filters
- **Outputs**: Import results (success/error reports), exported files
- **Owner**: Backend team

**AuditLogService**
- **Responsibilities**: Audit log creation, querying, filtering, export
- **Inputs**: Audit events, filter criteria
- **Outputs**: Audit log entries, filtered audit reports
- **Owner**: Backend team

**DataIntegrityService**
- **Responsibilities**: Orphan detection, referential integrity checks, data validation
- **Inputs**: Entity types, validation rules
- **Outputs**: Integrity check results, orphan reports
- **Owner**: Backend team

#### Frontend Modules

**ChartOfAccounts Module** (`features/accounting/pages/ChartOfAccounts.tsx`)
- Tree view component with expand/collapse
- Search and filter controls
- Account detail modal
- Integration with voucher account pickers

**Customers Module** (`features/customers/`)
- Customer list page with TanStack Table
- Customer form (create/edit)
- Import wizard component
- AR summary panel

**Suppliers Module** (`features/suppliers/`)
- Supplier list page with TanStack Table
- Supplier form (create/edit)
- Import wizard component
- AP summary panel

**BankAccounts Module** (`features/bankaccounts/`)
- Bank account list page
- Bank account form (create/edit)
- Balance display components

**CompanySettings Module** (`features/company/pages/CompanySettings.tsx`)
- Extended settings form
- VAT rate management
- Document sequence configuration
- Report branding preview

**ImportExport Module** (shared components)
- Import wizard with file upload
- Validation error display
- Export button/functionality
- Template download

**AuditLog Module** (shared components)
- Audit log viewer with filters
- Change diff display
- Export functionality

### Data Models and Contracts

#### Chart of Accounts Entity

```java
@Entity
@Table(name = "chart_of_accounts")
public class ChartOfAccount implements CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String code; // e.g., "111", "131", "331"
    
    @Column(nullable = false, length = 255)
    private String name; // Vietnamese name with accents
    
    @Column(length = 255)
    private String nameEnglish; // Account name in English (optional)
    
    @Column(columnDefinition = "TEXT")
    private String description; // Account description (optional)
    
    @Column(nullable = false)
    private Boolean active = true; // Status: true = In Use, false = Out of Use
    
    @Enumerated(EnumType.STRING)
    private AccountType type; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    
    @Enumerated(EnumType.STRING)
    private NormalSide normalSide; // DEBIT, CREDIT, HERMAPHRODITE
    
    @Column(nullable = false)
    private Boolean postable = false; // Only leaf accounts are postable
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ChartOfAccount parent;
    
    @Column(nullable = false)
    private Integer orderingPosition;
    
    @Column(nullable = false)
    private UUID companyId; // Multi-tenancy
    
    // Getters, setters, equals, hashCode
}
```

#### Customer Entity

```java
@Entity
@Table(name = "customers", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "tax_code"}))
public class Customer implements CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50, unique = true)
    private String code; // CUST-YYYY-NNNN
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(length = 20)
    private String taxCode; // Unique per company
    
    @Column(length = 500)
    private String address;
    
    @Column(length = 50)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @Column(nullable = false)
    private UUID companyId;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    // Getters, setters
}
```

#### Supplier Entity

```java
@Entity
@Table(name = "suppliers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "tax_code"}))
public class Supplier implements CompanyScopedEntity {
    // Similar structure to Customer
    // Code format: SUP-YYYY-NNNN
}
```

#### Bank Account Entity

```java
@Entity
@Table(name = "bank_accounts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "account_number"}))
public class BankAccount implements CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String accountNumber; // Unique per company
    
    @Column(nullable = false, length = 255)
    private String bankName;
    
    @Column(length = 255)
    private String branch;
    
    @Enumerated(EnumType.STRING)
    private AccountType type; // CASH, BANK
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @Column(nullable = false)
    private UUID companyId;
    
    // Getters, setters
}
```

#### Audit Log Entity

```java
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String entityType; // "Customer", "Supplier", "ChartOfAccount", etc.
    
    @Column(nullable = false)
    private Long entityId;
    
    @Enumerated(EnumType.STRING)
    private AuditAction action; // CREATE, UPDATE, DELETE, ACTIVATE, DEACTIVATE
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private UUID companyId;
    
    @Column(columnDefinition = "JSONB")
    private JsonNode changes; // Before/after values
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(length = 45)
    private String ipAddress;
    
    @Column(length = 500)
    private String userAgent;
    
    // Getters, setters
}
```

### APIs and Interfaces

#### Chart of Accounts API

**GET /api/v1/chart-of-accounts**
- Query params: `postable` (boolean), `codePrefix` (string), `accountClass` (string), `parentId` (Long), `active` (boolean), `search` (string)
- Response: `{ "data": { "content": [ChartOfAccountDTO], "totalElements": number } }`
- Returns hierarchical tree structure or flat list based on filters

**GET /api/v1/chart-of-accounts/{id}**
- Response: `{ "data": ChartOfAccountDTO }`

**GET /api/v1/chart-of-accounts/search**
- Query params: `q` (string) - search by code or name (unaccented)
- Response: `{ "data": [ChartOfAccountDTO] }`

**POST /api/v1/chart-of-accounts**
- Request: `ChartOfAccountCreateRequest` (code, name, nameEnglish, description, type, normalSide, parentId, orderingPosition)
- Response: `{ "data": ChartOfAccountDTO }`
- Requires: admin or chief_accountant role

**PUT /api/v1/chart-of-accounts/{id}**
- Request: `ChartOfAccountUpdateRequest` (all fields optional except id)
- Response: `{ "data": ChartOfAccountDTO }`
- Requires: admin or chief_accountant role

**DELETE /api/v1/chart-of-accounts/{id}**
- Soft delete (sets active=false)
- Response: 204 No Content
- Requires: admin or chief_accountant role

**PATCH /api/v1/chart-of-accounts/{id}/activate**
- Sets active=true
- Response: 204 No Content
- Requires: admin or chief_accountant role

**PATCH /api/v1/chart-of-accounts/{id}/deactivate**
- Sets active=false
- Response: 204 No Content
- Requires: admin or chief_accountant role

#### Customer API

**GET /api/v1/customers**
- Query params: `page`, `size`, `status` (active/inactive), `search` (string), `sort`
- Response: Paginated customer list with AR summaries

**POST /api/v1/customers**
- Request: `{ "name": string, "taxCode": string, "address": string, ... }`
- Response: `{ "data": CustomerDTO }`
- Auto-generates code, validates duplicates

**PUT /api/v1/customers/{id}**
- Request: Customer update data
- Response: Updated customer

**DELETE /api/v1/customers/{id}**
- Validates no linked invoices/payments
- Returns 409 if referenced

**POST /api/v1/customers/import**
- Request: Multipart file (Excel/CSV)
- Response: `{ "data": { "successCount": number, "errorCount": number, "errors": [...] } }`

**GET /api/v1/customers/export**
- Query params: Filters
- Response: Excel/CSV file download

#### Supplier API

Similar structure to Customer API:
- `GET /api/v1/suppliers`
- `POST /api/v1/suppliers`
- `PUT /api/v1/suppliers/{id}`
- `DELETE /api/v1/suppliers/{id}`
- `POST /api/v1/suppliers/import`
- `GET /api/v1/suppliers/export`

#### Bank Account API

**GET /api/v1/bank-accounts**
- Query params: `type` (CASH/BANK), `status` (active/inactive)
- Response: List of bank accounts with balances

**POST /api/v1/bank-accounts**
- Request: Bank account data
- Validates unique account number per company

**PUT /api/v1/bank-accounts/{id}**
- Validates no references in vouchers/reconciliations

**DELETE /api/v1/bank-accounts/{id}**
- Returns 409 if referenced

#### Company Settings API

**GET /api/v1/company/settings**
- Response: Extended company settings (fiscal year, VAT rates, sequences, branding)

**PUT /api/v1/company/settings**
- Request: Settings updates
- Validates fiscal year overlaps, VAT rate uniqueness
- Full audit trail

#### Audit Log API

**GET /api/v1/audit-logs**
- Query params: `entityType`, `entityId`, `userId`, `dateFrom`, `dateTo`, `action`
- Response: Filtered audit log entries

**GET /api/v1/audit-logs/{entityType}/{entityId}**
- Response: Audit history for specific entity

#### Data Integrity API

**POST /api/v1/admin/data-integrity/check**
- Response: `{ "data": { "orphans": [...], "integrityIssues": [...] } }`
- Admin-only endpoint

### Workflows and Sequencing

#### COA Seeding Workflow

1. **Initial Migration**: Flyway migration script seeds TT200 COA (≥154 accounts) for new companies
2. **Company Creation**: On company creation, COA is copied from template or seeded via migration
3. **COA Access**: Users query COA via API, filtered by company context
4. **Account Selection**: Voucher forms query only postable leaf accounts
5. **Validation**: Posting validation checks account exists, is postable, and belongs to company

#### Customer CRUD Workflow

1. **Create**: User fills form → Frontend validates → POST /api/v1/customers → Service generates code → Validates tax code uniqueness → Saves → Returns customer with code → Audit log created
2. **Update**: User edits → PUT /api/v1/customers/{id} → Service validates → Updates → Audit log with before/after diff
3. **Deactivate**: User deactivates → Service sets active=false → Removes from active pickers → Audit log → Linked AR data preserved
4. **Delete Attempt**: User attempts delete → Service checks references → If referenced, returns 409 with details → If not, soft delete or hard delete with audit

#### Import Workflow

1. **Upload**: User uploads Excel/CSV → Frontend sends to POST /import endpoint
2. **Validation**: Service parses file → Validates headers → Validates each row (format, duplicates, required fields) → Collects errors
3. **Preview**: Service returns validation results → Frontend displays errors → User reviews
4. **Commit**: User confirms → Service starts transaction → Bulk inserts valid rows → If any error, rollback → Returns success/error counts → Audit log per row
5. **Error Report**: User downloads error report for failed rows

#### Audit Trail Workflow

1. **Event Capture**: Any CRUD operation triggers AuditLogService
2. **Log Creation**: Service captures entity type, ID, action, user, company, before/after values, timestamp, IP
3. **Storage**: Audit log saved to database (append-only)
4. **Query**: Users query audit logs via API with filters
5. **Display**: Frontend shows audit timeline with diffs
6. **Export**: Users export audit logs to CSV/PDF for compliance

#### Data Integrity Check Workflow

1. **Trigger**: Admin initiates integrity check via API
2. **Orphan Detection**: Service queries all master data entities → Checks for orphaned records (missing company, invalid references)
3. **Referential Check**: Validates foreign key integrity
4. **Report**: Returns list of issues → Admin reviews → Manual fixes required
5. **Audit**: Integrity check itself is logged

## Non-Functional Requirements

### Performance

- **Response Times**: Master data list views must load in < 2 seconds for typical datasets (≤1000 records). COA tree view must render in < 1 second. Search operations with unaccented Vietnamese matching must complete in < 500ms.
- **Import Performance**: Bulk import of 1000 customer/supplier records must complete validation and insertion in < 30 seconds. Large imports (10,000+ records) should support background processing with progress tracking.
- **Database Queries**: All master data queries must use proper indexes on `company_id`, `tax_code`, `code`, and foreign keys. Avoid N+1 query patterns using `@EntityGraph` or explicit joins. Slow queries (>1s) must be logged and monitored.
- **Caching Strategy**: COA hierarchy can be cached in Redis with 1-hour TTL, invalidated on company creation. Customer/Supplier lists can use short-term caching (5 minutes) for frequently accessed data.
- **Pagination**: All list endpoints must support pagination with default page size of 20, maximum 100. Frontend must implement virtual scrolling or pagination for large datasets.

### Security

- **RBAC Enforcement**: All master data APIs must enforce role-based permissions at the API level. Admin/Chief Accountant can create/edit/delete. Accountants can view and create. CFO has view-only access. Frontend UI hiding is not a security boundary.
- **Multi-Tenancy**: All queries must be filtered by `company_id` using Epic 1's company context. No cross-company data leakage. API returns 403 if user attempts to access another company's data.
- **Input Validation**: All user inputs must be validated server-side: tax code format, email format, phone number format, required fields. Prevent SQL injection via parameterized queries. Prevent XSS via proper output encoding.
- **Audit Trail**: All master data changes must be logged with user, timestamp, IP address, and before/after values. Audit logs are append-only and cannot be modified or deleted by users.
- **Data Protection**: Sensitive data (tax codes, contact information) must be handled according to data protection policies. Export functionality must respect RBAC and log all export activities.

### Reliability/Availability

- **Data Integrity**: Database constraints enforce referential integrity (foreign keys, unique constraints). Deletion of master data referenced by transactions must be blocked with clear error messages.
- **Transaction Safety**: Import operations must be atomic - either all valid rows are inserted or none. Failed imports must not leave partial data. Use database transactions for bulk operations.
- **Error Recovery**: Failed operations must provide clear, actionable error messages in Vietnamese. System must gracefully handle concurrent modifications (optimistic locking where appropriate).
- **Availability**: Master data services must be available during normal business hours. Degraded mode: If audit logging fails, operation should still proceed but log the audit failure separately.
- **Data Consistency**: COA seeding must be idempotent - running migration multiple times should not create duplicates. Code generation must be thread-safe to prevent duplicate codes.

### Observability

- **Structured Logging**: All master data operations must log structured JSON with: timestamp, level, logger, message, userId, companyId, entityType, entityId, action. Sensitive data must be masked in logs.
- **Metrics**: Track key metrics: CRUD operation counts by entity type, import success/failure rates, search query performance, audit log volume. Expose metrics via health check endpoint.
- **Error Tracking**: All exceptions must be logged with full stack trace and context. Failed imports must log row numbers and error details. Monitor for repeated failures or suspicious patterns.
- **Audit Logging**: All audit log entries must include request ID for traceability. Audit log queries must be logged themselves for compliance. Support filtering and export of audit logs.
- **Health Checks**: Master data services must expose health check endpoints indicating database connectivity, cache connectivity (if used), and overall service health.

## Dependencies and Integrations

### Backend Dependencies

**Core Framework:**
- Spring Boot 3.5.7 (parent POM)
- Spring Data JPA 6.x (via Spring Boot) - For entity management and repositories
- Spring Security 6.x (via Spring Boot) - For RBAC enforcement
- Spring Validation (via Spring Boot) - For input validation

**Database & Migration:**
- PostgreSQL Driver 42.7.4 - Database connectivity
- Flyway 11.10.0 - Database migration and COA seeding

**Utilities:**
- Lombok - Code generation for entities
- Jackson (via Spring Boot) - JSON serialization for audit logs (JSONB)

**Excel/CSV Processing:**
- Apache POI 5.3.0 - Excel file parsing for import/export operations
- (Note: JasperReports and DynamicReports are for reporting, not used in Epic 2)

**API Documentation:**
- SpringDoc OpenAPI 2.8.13 - API documentation (Swagger UI)

**Testing:**
- TestContainers 1.21.3 - Integration testing with PostgreSQL
- JUnit 5 (via Spring Boot Test) - Unit and integration tests

### Frontend Dependencies

**Core Framework:**
- React 19.1.1 - UI framework
- TypeScript 5.9.3 - Type safety
- Vite 7.1.7 - Build tool and dev server

**UI Components:**
- Shadcn UI (Radix UI primitives) - Accessible component library
  - @radix-ui/react-dialog, @radix-ui/react-select, @radix-ui/react-dropdown-menu, etc.
- Tailwind CSS 4.1.16 - Utility-first styling
- Lucide React 0.552.0 - Icon library

**Data Tables:**
- @tanstack/react-table 8.21.3 - Headless table component for master data lists

**Forms & Validation:**
- React Hook Form 7.66.0 - Form state management
- Zod 4.1.12 - Schema validation
- @hookform/resolvers 5.2.2 - Zod integration with React Hook Form

**HTTP Client:**
- Axios 1.7.9 - API communication

**Utilities:**
- date-fns 4.1.0 - Date formatting
- clsx 2.1.1, tailwind-merge 3.3.1 - Conditional class names
- class-variance-authority 0.7.1 - Component variant management

**Routing:**
- react-router-dom 7.9.5 - Client-side routing

### Integration Points

**Epic 1 Dependencies:**
- Company Context Management - All master data operations use Epic 1's `CompanyContext` for multi-tenancy
- RBAC System - Master data APIs integrate with Epic 1's role-based access control
- User Management - Audit logs reference Epic 1's User entity
- JWT Authentication - All API calls require valid JWT tokens from Epic 1

**Database Integration:**
- PostgreSQL (Supabase) - Primary data store for all master data entities
- Flyway Migrations - COA seeding via migration scripts (V2X__seed_chart_of_accounts.sql)
- Multi-tenancy - All tables use `company_id` column with Epic 1's company scoping

**Future Epic Dependencies (Epic 2 provides foundation for):**
- Epic 3 (Voucher Engine) - Requires Chart of Accounts for account validation, Customer/Supplier for voucher line items
- Epic 4 (AP Module) - Requires Supplier master data, Bank Account for payments
- Epic 5 (AR Module) - Requires Customer master data, Bank Account for receipts
- Epic 6 (Cash & Bank) - Requires Bank Account master data
- Epic 7 (Reporting) - Requires Chart of Accounts for report generation

**External Services (Not used in Epic 2, but prepared for):**
- Redis (future) - Caching for COA hierarchy (deferred to Epic 7/8)
- Supabase Storage (future) - File storage for import templates (deferred)

### Version Constraints

- Java 21+ (required by Spring Boot 3.5.7)
- Node.js 18+ (required by Vite and React 19)
- PostgreSQL 15+ (required for JSONB support in audit logs)
- Maven 3.6+ (for backend builds)
- pnpm 8+ (for frontend package management)

### Migration Dependencies

Epic 2 requires the following Flyway migrations to be executed in order:
1. Epic 1 migrations (users, companies, roles tables)
2. Epic 2 migrations:
   - `V2X__create_chart_of_accounts.sql` - COA table structure
   - `V2X__seed_chart_of_accounts.sql` - TT200 COA data (≥154 accounts)
   - `V2X__create_customers.sql` - Customer table
   - `V2X__create_suppliers.sql` - Supplier table
   - `V2X__create_bank_accounts.sql` - Bank account table
   - `V2X__create_audit_logs.sql` - Audit log table
   - `V2X__add_company_settings_fields.sql` - Extended company settings

## Acceptance Criteria (Authoritative)

### Story 2.1: Chart of Accounts (COA) – TT200 Preload & Read-Only Management

1. Full TT200 COA (≥154 accounts) seeded on initial migration; reusable as import for fresh companies.
2. Each account has: code, name (VN, accents allowed), type (Asset, Liability, Equity, Revenue, Expense), normal side (Debit/Credit), postable flag, parent (nullable), and ordering position.
3. Tree view: Expand/collapse to 3 levels minimum; shows full account hierarchy and allows navigation (keyboard and mouse).
4. UX: Select/search input with typeahead for account code or name; unaccented search must match accented field reliably.
5. Filtering by account class (e.g., asset, income), postable-only toggle, and code prefix filter (e.g., all "131").
6. "View Details" modal clearly displays all account fields; "Postable" visually distinguished with tag or icon.
7. COA CRUD operations: Admin and chief accountant can create, update, soft delete, activate, and deactivate accounts via table UI with Sheet modal form. Table displays 6 columns: Account Code, Account Name, Account Type (Debit/Credit/Hermaphrodite), Account Name in English, Description, Status (In Use/Out of Use). Form includes 5 fields: Account Number (numeric, required, 1-4 digits), Account Name (required), Primary Account (optional parent selection via searchable combobox), Account Type/Characteristic (required combobox with options: "Debit Balance", "Credit Balance", "Hermaphrodite", "No Balance"), Description (optional textarea). All operations require RBAC (admin/chief_accountant roles) and are logged in audit.
8. Only postable-leaf accounts appear in voucher account pickers. Root accounts cannot be added to vouchers (validation both client and server).
9. API endpoint for COA returns hierarchy, allows filter by postable, code prefix, and parent–child relationships for cascading selectors.
10. No two accounts can ever have the same code per company. Attempt to add/seed duplicate code must fail with detailed error message.
11. "Balance" column available in COA tree (optional for MVP), showing real-time balance for that account in the current period.

### Story 2.2: Customer Master Data Management (CRUD)

1. List view: Supports pagination (20 per page), sorting by name or code, filter on status (active/inactive), and typeahead search.
2. Add form: All required except for optional fields; real-time validation (e.g., phone number, email format, tax code checksum).
3. Autogenerates Customer Code (CUST-YYYY-NNNN) and ensures globally unique per company; number increments even if prior code deleted/deactivated.
4. Detect and reject duplicate customers by tax code (and optionally email/cell), displaying exactly where conflict occurred.
5. Inactive customers appear grayed-out and are moved to bottom or separate tab.
6. Users can deactivate/reactivate, with tooltip explaining that linked AR data will not be deleted.
7. CRUD actions update real-time (websockets or polling), so multiple users see live changes.
8. Show customer AR summary (open invoices, total owed, average payment days) in details panel.
9. Can export customer list to CSV/Excel.
10. Attempt to delete customer with existing invoices/payments blocked, with explanatory modal.
11. All field edits, activations/deactivations, and import actions logged to the audit trail with before/after data and responsible user.

### Story 2.3: Supplier Master Data Management (CRUD)

1. List view: Supports pagination (20 per page), sorting by name or code, filter on status (active/inactive), and typeahead search.
2. Add form: All required except for optional fields; real-time validation (e.g., phone number, email format, tax code checksum).
3. Autogenerates Supplier Code (SUP-YYYY-NNNN) and ensures globally unique per company; number increments even if prior code deleted/deactivated.
4. Detect and reject duplicate suppliers by tax code (and optionally email/cell), displaying exactly where conflict occurred.
5. Inactive suppliers appear grayed-out and are moved to bottom or separate tab.
6. Users can deactivate/reactivate, with tooltip explaining that linked AP data will not be deleted.
7. CRUD actions update real-time (websockets or polling), so multiple users see live changes.
8. Show supplier AP summary (open bills, total owed, average payment days) in details panel.
9. Can export supplier list to CSV/Excel.
10. Attempt to delete supplier with existing bills/payments blocked, with explanatory modal.
11. All field edits, activations/deactivations, and import actions logged to the audit trail with before/after data and responsible user.

### Story 2.4: Bank Account Management (CRUD)

1. List view shows all cash and bank accounts; enables filtering by type/status, sort by last balance update.
2. "Add" form: Account number (required, must be unique), bank name (validated from list, open entry allowed), opening balance (must match accounting entry at migration).
3. On edit, audit trail logs all changes with reason if field value changed.
4. Cannot delete bank/cash account if referenced in any voucher, period, or reconciliation.
5. Closing (inactivating) an account removes it from pickers, but does not break referential integrity with historical vouchers.
6. Tooltip on bank account selector shows current and prior period balances.
7. Export list of accounts and balances to Excel for audit.
8. Multi-company: Accounts scoped to company_id.

### Story 2.5: Company Settings Expansion (Advanced Fields)

1. Fiscal year is pre-selected on company creation but can be edited (future years only, no overlap allowed).
2. Currency is VND (locked/read-only in MVP).
3. Admin can set up VAT rates for invoices/bills; must validate unique % per company.
4. Document sequences: optionally editable; cannot change to a code that causes a collision with existing records.
5. Report export config: logo, footer lines—shows preview of financial statements with these values before saving.
6. All changes are transactional: field edit either succeeds fully with all validations or fails leaving old values untouched.
7. Full audit trail on any change.

### Story 2.6: Data Import & Migration

1. Import wizard accepts only template files (Excel/CSV); checks that required headers and data types are present.
2. Validator parses all rows before saving: highlights errors and summary after validation (e.g., "Row 12: Invalid tax code, Row 25: Duplicate supplier").
3. On commit, bulk creates master records within transaction; if any row fails, no partial import occurs.
4. Opening balance import only available to admin, only before first period close. Enforces Dr = Cr invariant and warning if negative or zero-value entries.
5. All import errors downloadable as report for offline review.
6. Migration script logs result: inserted count, skipped, errors, and reference to audit trail.
7. "Demo import" uses sample data compatible with dev demo company.

### Story 2.7: Audit Trail & Data Integrity for Master Data

1. UI "Audit Log" per entity: shows all changes for that customer/supplier/account, filterable by date/user/action type.
2. Exposes audit log API for download/filtering (respecting role-based access).
3. Log format includes: record type, affected fields (old/new), user/email/role, datetime, IP/user agent if present.
4. Detects and logs failed modification attempts (e.g., user tried to delete protected account).
5. Data integrity check endpoint for admin: runs background job to check for orphaned master records across all entities (should return zero in normal state).
6. Bulk actions are split-out in logs for transparency (each import row has separate audit entry).

## Traceability Mapping

| AC # | Acceptance Criteria | Spec Section(s) | Component(s)/API(s) | Test Idea |
|------|---------------------|-----------------|---------------------|-----------|
| 2.1.1 | Full TT200 COA (≥154 accounts) seeded | Data Models, Workflows | Flyway Migration, ChartOfAccountService | Verify migration seeds ≥154 accounts, test re-seeding idempotency |
| 2.1.2 | Account fields: code, name, type, normalSide, postable, parent, ordering | Data Models | ChartOfAccount Entity | Unit test entity validation, verify all fields persist correctly |
| 2.1.3 | Tree view with 3-level hierarchy | Detailed Design, APIs | ChartOfAccounts Component, GET /api/v1/chart-of-accounts | E2E test: expand/collapse tree, verify hierarchy display |
| 2.1.4 | Unaccented search matches accented names | APIs, NFR Performance | GET /api/v1/chart-of-accounts/search | Test Vietnamese search: "nha" matches "nhà", verify <500ms response |
| 2.1.8 | Only postable-leaf accounts in voucher pickers | APIs, Workflows | GET /api/v1/chart-of-accounts?postable=true | Integration test: verify only leaf accounts with postable=true returned |
| 2.1.10 | No duplicate account codes per company | Data Models, Workflows | ChartOfAccountService, Database constraint | Test duplicate code rejection, verify unique constraint |
| 2.2.3 | Auto-generate CUST-YYYY-NNNN code | Services, Workflows | CustomerService.generateCode() | Unit test code generation, verify uniqueness and increment |
| 2.2.4 | Reject duplicate customers by tax code | Services, APIs | CustomerService, POST /api/v1/customers | Integration test: create duplicate tax code, verify 409 error |
| 2.2.10 | Block delete if customer has invoices | Services, APIs | CustomerService, DELETE /api/v1/customers/{id} | Integration test: attempt delete with linked invoices, verify 409 |
| 2.2.11 | Audit log all customer changes | Services, Workflows | AuditLogService, CustomerService | Test audit log creation on create/update/delete, verify before/after values |
| 2.3.3 | Auto-generate SUP-YYYY-NNNN code | Services, Workflows | SupplierService.generateCode() | Unit test code generation, verify uniqueness |
| 2.4.2 | Unique account number per company | Data Models, APIs | BankAccount Entity, POST /api/v1/bank-accounts | Test duplicate account number rejection |
| 2.4.4 | Block delete if bank account referenced | Services, APIs | BankAccountService, DELETE /api/v1/bank-accounts/{id} | Integration test: verify 409 when referenced in vouchers |
| 2.5.1 | Fiscal year edit validation (future only, no overlap) | Services, APIs | CompanySettingsService, PUT /api/v1/company/settings | Test fiscal year validation rules |
| 2.6.2 | Import validation with error reporting | Services, APIs | ImportExportService, POST /api/v1/customers/import | Test import with invalid rows, verify error report format |
| 2.6.3 | Atomic import transaction | Services, Workflows | ImportExportService | Test import with mixed valid/invalid rows, verify rollback on failure |
| 2.7.1 | Audit log UI per entity | Frontend Modules, APIs | AuditLog Component, GET /api/v1/audit-logs/{entityType}/{entityId} | E2E test: view audit log for customer, verify filtering works |
| 2.7.5 | Data integrity check endpoint | Services, APIs | DataIntegrityService, POST /api/v1/admin/data-integrity/check | Test integrity check, verify orphan detection |
| NFR1 | Response times < 2s for lists | NFR Performance | All list APIs | Load test: 1000 records, verify < 2s response |
| NFR2 | RBAC enforcement at API level | NFR Security | All APIs, Spring Security | Test unauthorized access, verify 403 response |
| NFR3 | Multi-tenancy isolation | NFR Security, Data Models | All services, CompanyScopedEntity | Test cross-company data access, verify 403 |
| NFR4 | Structured audit logging | NFR Observability, Services | AuditLogService | Test audit log format, verify JSON structure |

## Risks, Assumptions, Open Questions

### Risks

**Risk 1: COA Seeding Complexity**
- **Description**: TT200 COA has ≥154 accounts with complex hierarchy. Seeding logic must be idempotent and handle edge cases.
- **Impact**: High - COA is foundational for all accounting operations
- **Mitigation**: Use Flyway migrations with proper versioning. Test idempotency thoroughly. Create seed data as SQL script with conflict resolution.
- **Owner**: Backend team

**Risk 2: Unaccented Vietnamese Search Performance**
- **Description**: PostgreSQL unaccented search may be slow for large COA datasets without proper indexing.
- **Impact**: Medium - Affects user experience
- **Mitigation**: Use PostgreSQL `unaccent` extension with GIN indexes. Consider materialized search columns. Monitor query performance.
- **Owner**: Backend team

**Risk 3: Concurrent Code Generation**
- **Description**: Auto-generated codes (CUST-YYYY-NNNN, SUP-YYYY-NNNN) may collide under high concurrency.
- **Impact**: Medium - Data integrity issue
- **Mitigation**: Use database sequences or optimistic locking. Implement retry logic. Consider UUID-based codes if sequences prove insufficient.
- **Owner**: Backend team

**Risk 4: Import Transaction Size**
- **Description**: Large imports (10,000+ rows) may cause transaction timeouts or memory issues.
- **Impact**: Medium - Affects bulk data migration
- **Mitigation**: Implement batch processing with configurable batch size. Use background jobs for large imports. Provide progress tracking.
- **Owner**: Backend team

**Risk 5: Audit Log Volume**
- **Description**: Comprehensive audit logging may generate large volumes of data over time, impacting database performance.
- **Impact**: Medium - Long-term performance and storage
- **Mitigation**: Implement audit log archival strategy. Use partitioned tables. Consider separate audit log database. Monitor growth rates.
- **Owner**: Backend team, DevOps

**Risk 6: Referential Integrity During Deletion**
- **Description**: Complex referential checks (e.g., customer with invoices, supplier with bills) may be slow or miss edge cases.
- **Impact**: High - Data integrity
- **Mitigation**: Use database foreign key constraints. Implement efficient query patterns. Cache reference counts where appropriate.
- **Owner**: Backend team

### Assumptions

**Assumption 1: Epic 1 Completion**
- Epic 1 (Foundation & Authentication) is complete and provides stable company context, RBAC, and user management.
- **Validation**: Verify Epic 1 APIs are stable before Epic 2 development.

**Assumption 2: TT200 COA Structure**
- TT200 COA structure is well-documented and stable. No changes expected during Epic 2 development.
- **Validation**: Confirm COA structure with domain experts before seeding.

**Assumption 3: Import File Formats**
- Users will provide Excel/CSV files in expected formats. Template files will be provided to guide users.
- **Validation**: Test with real-world import files from pilot users.

**Assumption 4: Performance Requirements**
- Current database and infrastructure can handle expected load (≤1000 master records per company initially).
- **Validation**: Load test with realistic data volumes.

**Assumption 5: Real-time Updates**
- Polling-based real-time updates (5-minute intervals) are acceptable for master data changes. WebSockets not required for MVP.
- **Validation**: Confirm with product owner and users.

### Open Questions

**Question 1: COA Balance Display**
- Should COA tree show real-time balances in MVP, or defer to Epic 3 (Voucher Engine)?
- **Decision Needed**: Product Owner
- **Impact**: Affects Story 2.1 AC #11 (optional)

**Question 2: AR/AP Summary Integration**
- Customer/Supplier AR/AP summaries (Stories 2.2.8, 2.3.8) require data from Epic 5 (AR) and Epic 4 (AP). Should these be stubbed in Epic 2?
- **Decision Needed**: Architect, Product Owner
- **Impact**: Affects Stories 2.2 and 2.3

**Question 3: Real-time Update Mechanism**
- Stories 2.2.7 and 2.3.7 mention "real-time updates (websockets or polling)". Which mechanism should be used?
- **Decision Needed**: Architect
- **Impact**: Affects implementation approach

**Question 4: Audit Log Retention**
- What is the audit log retention policy? 10 years per NFR10, but should there be archival strategy?
- **Decision Needed**: Compliance, Product Owner
- **Impact**: Affects Story 2.7 and database design

**Question 5: Import Template Location**
- Where should import templates be stored? Supabase Storage, static files, or generated on-demand?
- **Decision Needed**: Architect
- **Impact**: Affects Story 2.6 implementation

## Test Strategy Summary

### Test Levels

**Unit Tests (Target: 70% coverage for business logic)**
- Service layer: Code generation, validation logic, duplicate detection
- Entity validation: Field constraints, relationships
- Utility functions: Search normalization, import parsing
- **Framework**: JUnit 5, Mockito
- **Location**: `backend/src/test/java/com/accounting/service/`, `backend/src/test/java/com/accounting/util/`

**Integration Tests (Target: Critical paths)**
- API endpoints: CRUD operations, import/export, search
- Database operations: Multi-tenancy filtering, referential integrity
- Audit logging: Log creation and retrieval
- **Framework**: Spring Boot Test, TestContainers (PostgreSQL)
- **Location**: `backend/src/test/java/com/accounting/integration/`

**End-to-End Tests (Target: Key user journeys)**
- Customer CRUD flow: Create → Edit → Deactivate → View audit log
- Import workflow: Upload → Validate → Commit → Verify
- COA tree view: Load → Search → Filter → View details
- **Framework**: Frontend: Vitest + Testing Library, Backend: Spring Boot Test
- **Location**: `frontend/src/**/*.test.tsx`, `backend/src/test/java/com/accounting/e2e/`

### Test Coverage Areas

**Functional Coverage**
- All acceptance criteria from Stories 2.1-2.7
- API contract validation (request/response formats)
- Business rule validation (duplicate detection, referential integrity)
- Error handling and edge cases

**Non-Functional Coverage**
- Performance: Response times, import throughput, search latency
- Security: RBAC enforcement, multi-tenancy isolation, input validation
- Reliability: Transaction atomicity, error recovery, concurrent operations

**Data Integrity Coverage**
- Referential integrity constraints
- Unique constraint enforcement
- Audit log completeness and accuracy
- Data migration correctness

### Test Data Strategy

**Test Fixtures**
- Minimal TT200 COA subset (20-30 accounts) for unit/integration tests
- Full TT200 COA (154+ accounts) for E2E and performance tests
- Sample customers/suppliers with various data combinations
- Test companies with different configurations

**Test Isolation**
- Each test uses isolated test company (created and cleaned up)
- Database transactions rolled back after each test
- No shared state between tests

### Test Automation

**CI/CD Integration**
- All unit and integration tests run on every commit
- E2E tests run on pull requests and nightly builds
- Performance tests run weekly or on-demand
- **Pipeline**: GitHub Actions (or equivalent)

**Test Reporting**
- Code coverage reports (target: 70% for services)
- Test execution reports with pass/fail status
- Performance test results with trend analysis

### Manual Testing

**User Acceptance Testing (UAT)**
- Key user journeys validated by product owner
- Import/export workflows tested with real-world data
- UI/UX validation for master data management screens

**Exploratory Testing**
- Edge cases and error scenarios
- Concurrent user scenarios
- Large dataset handling

