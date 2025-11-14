# Epic Technical Specification: Voucher Engine & General Ledger Core

Date: 2025-11-13
Author: thanhtoan
Epic ID: 3
Status: Draft

---

## Overview

Epic 3 delivers the core voucher engine and general ledger foundation for the accounting ERP system. This epic enables end-to-end voucher entry with double-entry journal logic, posting/unposting workflows, stringent validations (leaf-only posting, double-entry balancing, required dimensions), comprehensive audit trail, period management, and attachment handling. The system enforces compliance with Vietnamese Circular 200 (TT200) accounting standards, ensuring all journal entries are balanced (Total Debit = Total Credit), posted only to leaf accounts, and validated against mandatory dimensions (customer for AR accounts, vendor for AP accounts, cost center for expense accounts).

The epic builds on Epic 2's master data foundation (Chart of Accounts, Customers, Suppliers) and provides the transactional engine that subsequent epics (AP, AR, Cash/Bank, Reporting) will leverage for automated GL posting. Key outcomes include a fully functional voucher entry form with keyboard-first editing, server-side validation, period locking controls, and an immutable audit trail that captures every change with cryptographic hashing for legal defensibility.

## Objectives and Scope

**In Scope:**

- Voucher list and search with server-side pagination, filtering, and fuzzy search (Story 3.1)
- Voucher form with line item engine supporting keyboard-first editing, inline validation, and draft auto-save (Story 3.2)
- Voucher templates with pre-configured debit/credit accounts for common transactions (e.g., cash receipt from customer, cash payment to supplier) (Story 3.2)
- Posting, unposting, and reversal workflows with dependency checking and double-entry validation (Story 3.3)
- Leaf-only posting validation and double-entry balancing engine with required dimension enforcement (Story 3.4)
- Comprehensive audit trail with cryptographic hashing, field-level diffs, and exportable logs (Story 3.5)
- Period selector and voucher-period mapping with period close/reopen controls (Story 3.6)
- Attachment management with drag-and-drop upload, preview, and secure storage (Story 3.7)

**Out of Scope:**

- AP/AR-specific workflows (deferred to Epics 4-5)
- Financial reporting generation (deferred to Epic 7)
- BI dashboard analytics (deferred to Epic 8)
- Maker-checker approval workflows (optional for MVP, configurable for production per FR15)
- Multi-currency support (single-currency VND only per PRD scope)
- E-invoice integration (deferred to post-MVP)

## System Architecture Alignment

This epic aligns with the established Spring Boot 3.5.7 backend architecture and React TypeScript frontend. Backend components reside under `controller/voucher/` and `service/gl/` packages, following the modular structure defined in the architecture. The frontend implements voucher management in `features/accounting/pages/Vouchers/` using shadcn/ui components (DataTablePro, VoucherLineGrid) as specified in the UX design specification.

**Database Schema:**

- Core tables: `vouchers`, `voucher_lines`, `journal_entries` (per data architecture)
- Template tables: `voucher_templates`, `voucher_template_lines` for pre-configured voucher patterns
- Audit table: `audit_logs` with append-only, immutable structure
- Period management: `accounting_periods` table for fiscal year and period status tracking
- Attachment storage: Supabase Storage with metadata in `voucher_attachments` table

**API Contracts:**

- REST endpoints follow `/api/v1/vouchers` pattern with standard pagination, filtering, and error responses
- Posting endpoint returns updated voucher and generated GL entries atomically
- Validation errors return detailed field-level error maps (not generic 400)

**Security & Multi-Tenancy:**

- All voucher operations enforce company-level isolation via `company_id` filtering
- RBAC: Accountant role can create/edit drafts; Chief Accountant can post/unpost; Admin has full access
- Period close requires Chief Accountant or CFO role (per FR05)

**Performance:**

- Server-side pagination for voucher lists (target: <2s page load per NFR1)
- Redis caching for period status and COA metadata (invalidated on period close)
- Optimistic UI updates for draft saves with background persistence

## Detailed Design

### Services and Modules

#### Backend Services

**VoucherService**

- **Responsibilities**: Voucher CRUD operations, draft management, status transitions (draft → posted), voucher number generation (VC{YYYY}-{seq}), company-scoped filtering, search/filter operations with server-side pagination
- **Inputs**: Voucher DTOs (date, description, lines), filters (status, date range, account, search term), pagination params
- **Outputs**: Voucher records with lines, paginated lists, voucher details with audit history
- **Owner**: Backend team

**VoucherPostingService**

- **Responsibilities**: Posting workflow execution, double-entry validation (Dr=Cr), leaf-only account validation, required dimension enforcement, GL entry generation, atomic transaction management, dependency checking for unposting, reversal voucher creation
- **Inputs**: Voucher ID, posting request with validation flags
- **Outputs**: Posted voucher with generated journal entries, validation error maps, dependency conflict details
- **Owner**: Backend team

**VoucherValidationService**

- **Responsibilities**: Real-time validation for voucher lines (leaf-only accounts, positive amounts, required dimensions per account_controls), period validation (open/closed), double-entry balancing checks, bulk validation for all lines
- **Inputs**: Voucher lines, account codes, period ID, company context
- **Outputs**: Validation result with field-level error maps, aggregated error summaries
- **Owner**: Backend team

**JournalEntryService**

- **Responsibilities**: GL entry generation from posted vouchers, journal entry querying for reports, period-based filtering, account balance calculations
- **Inputs**: Posted voucher data, period filters, account filters
- **Outputs**: Journal entry records, account balances, trial balance data
- **Owner**: Backend team

**PeriodManagementService**

- **Responsibilities**: Period status management (open/closed), period close validation (no drafts, all balanced), period reopen with approval workflow, period locking enforcement, batch-lock vouchers on close
- **Inputs**: Period ID, close/reopen requests, approval metadata
- **Outputs**: Period status updates, validation results, audit events
- **Owner**: Backend team

**VoucherTemplateService**

- **Responsibilities**: Voucher template CRUD operations, template library management, template application to voucher forms, default account and dimension pre-filling
- **Inputs**: Template data (name, description, default debit/credit accounts, required dimensions), template selection for voucher creation
- **Outputs**: Template records, template list for selection, pre-filled voucher lines from template
- **Owner**: Backend team

**VoucherAttachmentService**

- **Responsibilities**: File upload to Supabase Storage, attachment metadata management, signed URL generation for downloads, file type/size validation, virus scan simulation, access logging
- **Inputs**: File uploads, voucher ID, file metadata
- **Outputs**: Attachment records with signed URLs, upload validation results
- **Owner**: Backend team

**AuditLogService** (extends Epic 2's audit service)

- **Responsibilities**: Voucher lifecycle event logging (create/edit/post/unpost/reverse), field-level change tracking with JSON snapshots, cryptographic hashing for integrity, exportable audit logs (PDF/JSON), mass action logging
- **Inputs**: Voucher events, change diffs, user context, device/IP metadata
- **Outputs**: Audit log entries, exportable audit reports
- **Owner**: Backend team

#### Frontend Modules

**VoucherListPage** (`features/accounting/pages/Vouchers/VoucherList.tsx`)

- **Responsibilities**: Server-side paginated voucher table, filters (date range, status, account, search), sort controls, bulk actions toolbar, row action menu (view/edit/delete), live badge counts (draft vs posted)
- **Components**: DataTablePro, DateRangeFilter, SearchInput, StatusBadge, ActionMenu
- **Owner**: Frontend team

**VoucherFormPage** (`features/accounting/pages/Vouchers/VoucherForm.tsx`)

- **Responsibilities**: Voucher header form (number, date, description, status), VoucherLineGrid integration, draft auto-save, posting workflow, validation error display, attachment management modal, voucher template selector and application
- **Components**: VoucherLineGrid, MoneyInput, AccountPicker, DatePicker, AttachmentDropzone, PostButton, VoucherTemplateSelector
- **Owner**: Frontend team

**VoucherTemplateSelector** (`components/voucher/VoucherTemplateSelector.tsx`)

- **Responsibilities**: Template library display (list/grid of available templates), template selection modal, template preview with default accounts, apply template to voucher form
- **Components**: Dialog/Sheet, TemplateCard, AccountBadge
- **Owner**: Frontend team

**VoucherLineGrid** (`components/voucher/VoucherLineItemGrid.tsx`)

- **Responsibilities**: Keyboard-first editable grid, inline validation, row operations (insert/duplicate/delete), sticky footer with balance totals, dimension pickers (customer/vendor/cost center), tab/arrow navigation, auto-add row on last field tab-out
- **Components**: Custom grid built on TanStack Table, AccountPicker, MoneyInput, DimensionCombobox
- **Owner**: Frontend team

**PeriodSelector** (`components/accounting/PeriodSelector.tsx`)

- **Responsibilities**: Period dropdown with current + 3 prior/next open periods, period status badges, period close/reopen actions (Chief Accountant only), period summary dashboard
- **Components**: Select, Badge, Button
- **Owner**: Frontend team

### Data Models and Contracts

#### Database Entities

**Voucher Entity**

```java
@Entity
@Table(name = "vouchers")
public class Voucher implements CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String voucherNumber; // Format: VC{YYYY}-{seq}

    @Column(nullable = false)
    private LocalDate voucherDate;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private VoucherStatus status; // DRAFT, POSTED, REVERSED

    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private AccountingPeriod period;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne
    @JoinColumn(name = "entered_by")
    private User enteredBy;

    @ManyToOne
    @JoinColumn(name = "posted_by")
    private User postedBy;

    private LocalDateTime postedAt;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL)
    private List<VoucherLine> lines;

    @OneToMany(mappedBy = "voucher")
    private List<JournalEntry> journalEntries;

    @OneToOne(mappedBy = "originalVoucher")
    private Voucher reversalVoucher;

    @OneToOne
    @JoinColumn(name = "reversal_voucher_id")
    private Voucher reversedBy;

    // Template reference (optional)
    @ManyToOne
    @JoinColumn(name = "template_id")
    private VoucherTemplate template;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**VoucherLine Entity**

```java
@Entity
@Table(name = "voucher_lines")
public class VoucherLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(nullable = false)
    private Integer lineNumber;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @Column(length = 500)
    private String description;

    @Column(precision = 19, scale = 2)
    private BigDecimal debitAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal creditAmount;

    // Optional dimensions
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**JournalEntry Entity**

```java
@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private AccountingPeriod period;

    @Column(precision = 19, scale = 2)
    private BigDecimal debitAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal creditAmount;

    // Dimension references (denormalized for reporting performance)
    private UUID customerId;
    private UUID supplierId;
    private UUID costCenterId;

    @Column(nullable = false)
    private UUID companyId;

    private LocalDateTime postedAt;
}
```

**AccountingPeriod Entity**

```java
@Entity
@Table(name = "accounting_periods")
public class AccountingPeriod implements CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "fiscal_year_id", nullable = false)
    private FiscalYear fiscalYear;

    @Column(nullable = false)
    private Integer periodNumber; // 1-12 for monthly

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private PeriodStatus status; // OPEN, CLOSED

    @ManyToOne
    @JoinColumn(name = "closed_by")
    private User closedBy;

    private LocalDateTime closedAt;

    private String closeReason;

    @Column(nullable = false)
    private UUID companyId;
}
```

**VoucherTemplate Entity**

```java
@Entity
@Table(name = "voucher_templates")
public class VoucherTemplate implements CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name; // e.g., "Thu tiền mặt khách hàng (không theo hóa đơn)"

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private UUID companyId;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL)
    private List<VoucherTemplateLine> lines;

    @Column(nullable = false)
    private Boolean isActive;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**VoucherTemplateLine Entity**

```java
@Entity
@Table(name = "voucher_template_lines")
public class VoucherTemplateLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    private VoucherTemplate template;

    @Column(nullable = false)
    private Integer lineNumber;

    @ManyToOne
    @JoinColumn(name = "debit_account_id")
    private ChartOfAccount debitAccount; // Optional: can be null if credit only

    @ManyToOne
    @JoinColumn(name = "credit_account_id")
    private ChartOfAccount creditAccount; // Optional: can be null if debit only

    @Column(length = 500)
    private String defaultDescription;

    // Optional default dimensions
    private Boolean requiresCustomer; // If true, customer dimension required (for AR accounts)
    private Boolean requiresSupplier; // If true, supplier dimension required (for AP accounts)
    private Boolean requiresCostCenter; // If true, cost center dimension required

    // Optional: lock accounts (read-only in voucher form)
    @Column(nullable = false)
    private Boolean lockAccounts; // If true, accounts cannot be changed after template applied
}
```

**VoucherAttachment Entity**

```java
@Entity
@Table(name = "voucher_attachments")
public class VoucherAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(nullable = false)
    private String fileName; // Original filename

    @Column(nullable = false)
    private String storagePath; // Supabase Storage path

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long fileSize; // Bytes

    @Column(nullable = false)
    private UUID companyId;

    private LocalDateTime uploadedAt;
    private UUID uploadedBy;
}
```

#### DTOs

**VoucherDTO**

```typescript
interface VoucherDTO {
  id: string;
  voucherNumber: string;
  voucherDate: string; // ISO 8601
  description: string;
  status: "DRAFT" | "POSTED" | "REVERSED";
  periodId: string;
  enteredBy: UserSummary;
  postedBy?: UserSummary;
  postedAt?: string;
  lines: VoucherLineDTO[];
  reversalVoucherId?: string;
  reversedByVoucherId?: string;
  attachmentCount: number;
  templateId?: string; // If created from template
  templateName?: string; // For display
  createdAt: string;
  updatedAt: string;
}
```

**VoucherTemplateDTO**

```typescript
interface VoucherTemplateDTO {
  id: string;
  name: string;
  description: string;
  lines: VoucherTemplateLineDTO[];
  isActive: boolean;
  createdBy: UserSummary;
  createdAt: string;
  updatedAt: string;
}
```

**VoucherTemplateLineDTO**

```typescript
interface VoucherTemplateLineDTO {
  lineNumber: number;
  debitAccountId?: string;
  debitAccountCode?: string;
  debitAccountName?: string;
  creditAccountId?: string;
  creditAccountCode?: string;
  creditAccountName?: string;
  defaultDescription?: string;
  requiresCustomer: boolean;
  requiresSupplier: boolean;
  requiresCostCenter: boolean;
  lockAccounts: boolean;
}
```

**ApplyTemplateRequest**

```typescript
interface ApplyTemplateRequest {
  templateId: string;
  voucherDate: string; // ISO 8601
  description?: string; // Optional override
}
```

**VoucherLineDTO**

```typescript
interface VoucherLineDTO {
  id?: string;
  lineNumber: number;
  accountId: string;
  accountCode: string;
  accountName: string;
  description: string;
  debitAmount: number;
  creditAmount: number;
  customerId?: string;
  supplierId?: string;
  costCenterId?: string;
  itemId?: string;
  errors?: FieldError[];
}
```

**PostVoucherRequest**

```typescript
interface PostVoucherRequest {
  voucherId: string;
  validateOnly?: boolean; // For dry-run validation
}
```

**PostVoucherResponse**

```typescript
interface PostVoucherResponse {
  voucher: VoucherDTO;
  journalEntries: JournalEntryDTO[];
  validationErrors?: ValidationErrorMap; // If validation fails
}
```

**ValidationErrorMap**

```typescript
interface ValidationErrorMap {
  global?: string[];
  lines?: {
    [lineNumber: number]: {
      [field: string]: string[];
    };
  };
}
```

### APIs and Interfaces

#### REST Endpoints

**GET /api/v1/vouchers**

- **Query Params**: `page`, `size`, `status`, `dateFrom`, `dateTo`, `accountId`, `search`, `sort`
- **Response**: `{ data: { content: VoucherDTO[], totalElements: number, totalPages: number }, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can view

**GET /api/v1/vouchers/{voucherId}**

- **Response**: `{ data: VoucherDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can view

**POST /api/v1/vouchers**

- **Request Body**: `{ voucherDate: string, description: string, lines: VoucherLineDTO[] }`
- **Response**: `{ data: VoucherDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can create
- **Validation**: Returns field-level error map on validation failure

**PUT /api/v1/vouchers/{voucherId}**

- **Request Body**: Same as POST
- **Response**: `{ data: VoucherDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can edit (drafts only)
- **Validation**: Blocks editing posted vouchers (409 Conflict)

**DELETE /api/v1/vouchers/{voucherId}**

- **Request Body**: `{ reason: string }` (required for audit)
- **Response**: `204 No Content`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can delete (drafts only, unreferenced)
- **Validation**: Blocks deletion of posted vouchers or vouchers with references

**POST /api/v1/vouchers/{voucherId}/post**

- **Request Body**: `PostVoucherRequest`
- **Response**: `PostVoucherResponse`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can post
- **Validation**: Double-entry (Dr=Cr), leaf-only accounts, required dimensions, open period
- **Atomic**: Transaction ensures voucher status update + journal entry creation

**POST /api/v1/vouchers/{voucherId}/unpost**

- **Request Body**: `{ reason: string }`
- **Response**: `{ data: VoucherDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can unpost
- **Validation**: Checks dependencies (referenced in payments/receipts), returns 409 if blocked

**POST /api/v1/vouchers/{voucherId}/reverse**

- **Request Body**: `{ description: string, reason: string }`
- **Response**: `{ data: { original: VoucherDTO, reversal: VoucherDTO }, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can reverse
- **Validation**: Blocks double reversal (409 Conflict), auto-posts reversal voucher

**GET /api/v1/vouchers/{voucherId}/history**

- **Response**: `{ data: AuditLogEntry[], meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can view audit history

**GET /api/v1/voucher-templates**

- **Query Params**: `isActive` (optional filter)
- **Response**: `{ data: VoucherTemplateDTO[], meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can view

**GET /api/v1/voucher-templates/{templateId}**

- **Response**: `{ data: VoucherTemplateDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can view

**POST /api/v1/voucher-templates**

- **Request Body**: `{ name: string, description: string, lines: VoucherTemplateLineDTO[], isActive: boolean }`
- **Response**: `{ data: VoucherTemplateDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can create (templates are company-wide)

**PUT /api/v1/voucher-templates/{templateId}**

- **Request Body**: Same as POST
- **Response**: `{ data: VoucherTemplateDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can update

**DELETE /api/v1/voucher-templates/{templateId}**

- **Response**: `204 No Content`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can delete

**POST /api/v1/vouchers/apply-template**

- **Request Body**: `ApplyTemplateRequest`
- **Response**: `{ data: { voucher: VoucherDTO, template: VoucherTemplateDTO }, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can apply templates
- **Behavior**: Creates new draft voucher with pre-filled lines from template

**POST /api/v1/vouchers/{voucherId}/attachments**

- **Request**: Multipart form data with file(s)
- **Response**: `{ data: VoucherAttachmentDTO[], meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Accountant+ can upload
- **Validation**: File type, size limits, virus scan simulation

**GET /api/v1/vouchers/{voucherId}/attachments/{attachmentId}/download**

- **Response**: Signed URL (302 redirect) or file stream
- **Auth**: JWT required, company-scoped, signed URL expires in 10 minutes
- **RBAC**: Accountant+ can download
- **Audit**: Logs download event

**GET /api/v1/periods/current**

- **Response**: `{ data: AccountingPeriodDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: All authenticated users

**POST /api/v1/periods/{periodId}/close**

- **Request Body**: `{ reason: string }`
- **Response**: `{ data: AccountingPeriodDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can close
- **Validation**: No drafts, all vouchers balanced, batch-locks vouchers

**POST /api/v1/periods/{periodId}/reopen**

- **Request Body**: `{ reason: string, approvedBy: string }`
- **Response**: `{ data: AccountingPeriodDTO, meta: {...} }`
- **Auth**: JWT required, company-scoped
- **RBAC**: Chief Accountant+ can reopen (with approval audit)

#### Standard Error Responses

**400 Bad Request** (Validation Error)

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Tổng Nợ phải bằng Tổng Có",
    "details": {
      "debitTotal": 1000000,
      "creditTotal": 900000,
      "lines": {
        "2": {
          "accountId": ["Account must be leaf/postable"],
          "customerId": ["Customer required for account 131"]
        }
      }
    }
  },
  "meta": {
    "timestamp": "2025-11-13T12:00:00Z",
    "requestId": "req-abc123"
  }
}
```

**409 Conflict** (Business Rule Violation)

```json
{
  "error": {
    "code": "BUSINESS_RULE_VIOLATION",
    "message": "Cannot unpost voucher: referenced in payment #P123",
    "details": {
      "voucherId": "vc-123",
      "conflictingReferences": [
        { "type": "payment", "id": "p-123", "number": "P123" }
      ]
    }
  }
}
```

### Workflows and Sequencing

#### Voucher Creation Workflow (with Template)

**Sequence Diagram:**

```
User → Frontend → Backend API → Database
  |        |            |            |
  |--[1] Click "New Voucher"--------|
  |        |--[2] Show template selector modal
  |        |--[3] User selects template (or "Blank")
  |        |--[4] POST /vouchers/apply-template---|
  |        |            |--[5] Load template       |
  |        |            |--[6] Create draft voucher|
  |        |            |--[7] Pre-fill lines from template
  |        |            |--[8] Set templateId reference
  |        |<--[9] Return voucher with pre-filled lines|
  |<--[10] Display form with template lines--------|
  |                                 |
  |--[11] User edits amounts/dimensions----------|
  |        |--[12] Auto-save (30s/blur)----------|
  |        |--[13] PUT /vouchers/{id}            |
  |        |            |--[14] Update draft      |
  |        |<--[15] Updated voucher---------------|
  |<--[16] Optimistic update----------------------|
```

**Steps:**

1. User clicks "New Voucher" button on Voucher List page
2. Frontend shows template selector modal with list of active templates (e.g., "Thu tiền mặt khách hàng", "Chi tiền mặt cho nhà cung cấp", "Blank" option)
3. User selects a template (or "Blank" for manual entry)
4. Frontend sends POST request to `/api/v1/vouchers/apply-template` with templateId and voucherDate
5. Backend loads template with lines
6. Backend creates new draft voucher with current date and period
7. Backend creates voucher lines from template lines:
   - Copy debit/credit accounts
   - Copy default descriptions
   - Set required dimension flags (customer/supplier/cost center)
   - Apply lockAccounts flag (if true, accounts become read-only in UI)
8. Backend sets voucher.templateId reference for audit tracking
9. Backend returns draft voucher with pre-filled lines
10. Frontend displays voucher form with template-applied lines:
    - Locked accounts (if lockAccounts=true) show as read-only with lock icon
    - Required dimensions highlighted until filled
    - User can add additional lines or modify amounts
11. User edits amounts, fills dimensions, adds additional lines as needed
12. On blur/auto-save: Frontend sends PUT request with current state
13. Backend validates server-side, saves draft, returns updated voucher
14. Frontend updates UI optimistically, shows success toast
15. User continues editing; auto-save repeats every 30s or on blur

#### Voucher Creation Workflow (Manual Entry)

**Sequence Diagram:**

```
User → Frontend → Backend API → Database
  |        |            |            |
  |--[1] Create Draft--|            |
  |        |--[2] POST /vouchers----|
  |        |            |--[3] Validate lines
  |        |            |--[4] Save voucher + lines
  |        |<--[5] Return voucher---|
  |<--[6] Display form-------------|
  |                                 |
  |--[7] Auto-save (30s/blur)------|
  |        |--[8] PUT /vouchers/{id}|
  |        |            |--[9] Update draft
  |        |<--[10] Updated voucher-|
  |<--[11] Optimistic update--------|
```

**Steps:**

1. User opens Voucher Form (selects "Blank" template or creates without template), enters header (date, description)
2. User adds lines via VoucherLineGrid (keyboard navigation, inline validation)
3. Frontend validates locally (leaf-only, positive amounts, required dimensions)
4. On blur/auto-save: Frontend sends PUT request with current state
5. Backend validates server-side, saves draft, returns updated voucher
6. Frontend updates UI optimistically, shows success toast
7. User continues editing; auto-save repeats every 30s or on blur

#### Voucher Posting Workflow

**Sequence Diagram:**

```
User → Frontend → VoucherPostingService → ValidationService → JournalEntryService → Database
  |        |              |                      |                    |                |
  |--[1] Click Post------|                     |                    |                |
  |        |--[2] POST /vouchers/{id}/post-----|                    |                |
  |        |              |--[3] Load voucher---|                    |                |
  |        |              |--[4] Validate-------|                    |                |
  |        |              |      |--[5] Check Dr=Cr                 |                |
  |        |              |      |--[6] Check leaf-only              |                |
  |        |              |      |--[7] Check required dimensions   |                |
  |        |              |      |--[8] Check period open            |                |
  |        |              |<--[9] Validation result-----------------|                |
  |        |              |--[10] If valid: Start transaction       |                |
  |        |              |      |--[11] Update status=POSTED         |                |
  |        |              |      |--[12] Generate journal entries----|                |
  |        |              |      |--[13] Commit transaction          |                |
  |        |<--[14] Return posted voucher + journal entries---------|                |
  |<--[15] Show success, refresh list-------------------------------|
```

**Steps:**

1. User clicks "Post" button on draft voucher
2. Frontend sends POST request to `/api/v1/vouchers/{id}/post`
3. Backend loads voucher with lines
4. VoucherValidationService validates:
   - Double-entry: Sum(Dr) == Sum(Cr) using BigDecimal
   - Leaf-only: All accounts have `postable=true` and no children
   - Required dimensions: Account 131 requires customer, 331 requires supplier, 154/621 require cost center (if configured)
   - Period: Voucher date falls within open period
     5-8. Validation checks return aggregated error map if any fail
5. If validation passes, start database transaction
6. Update voucher status to POSTED, set postedBy/postedAt
7. Generate JournalEntry records for each line (one entry per line with debit or credit)
8. Commit transaction atomically
9. Return posted voucher with generated journal entries
10. Frontend shows success toast, refreshes voucher list, updates status badge

#### Voucher Reversal Workflow

**Sequence Diagram:**

```
User → Frontend → VoucherPostingService → JournalEntryService → Database
  |        |              |                      |                |
  |--[1] Click Reverse---|                     |                |
  |        |--[2] POST /vouchers/{id}/reverse--|                |
  |        |              |--[3] Check not already reversed       |                |
  |        |              |--[4] Create reversal voucher          |                |
  |        |              |      |--[5] Copy lines, swap Dr/Cr    |                |
  |        |              |      |--[6] Set description="REV-{original}"|                |
  |        |              |      |--[7] Link bi-directionally    |                |
  |        |              |--[8] Auto-post reversal voucher-------|                |
  |        |              |--[9] Update original.reversedBy       |                |
  |        |<--[10] Return both vouchers-------------------------|                |
  |<--[11] Show both, link badges-------------------------------|
```

**Steps:**

1. User clicks "Reverse" on posted voucher
2. Frontend sends POST request with reason
3. Backend checks: original voucher not already reversed (409 if double reversal attempted)
4. Create new voucher with:
   - Voucher number: REV-{original_number}
   - Date: Current date (or next open period)
   - Description: "REV-{original_description}" + user reason
   - Lines: Copy all lines, swap debit/credit amounts
5. Link bi-directionally: reversal.reversedBy = original, original.reversalVoucher = reversal
6. Auto-post reversal voucher (same validation as posting workflow)
7. Update original voucher: set reversedBy reference
8. Return both vouchers
9. Frontend displays both with "Reversed by" / "Reversal of" badges, clickable links

#### Period Close Workflow

**Sequence Diagram:**

```
Chief Accountant → Frontend → PeriodManagementService → VoucherService → Database
        |              |                  |                  |                |
        |--[1] Click Close Period--------|                  |                |
        |              |--[2] POST /periods/{id}/close-----|                |
        |              |                  |--[3] Validate no drafts           |                |
        |              |                  |--[4] Validate all balanced       |                |
        |              |                  |--[5] Start transaction            |                |
        |              |                  |      |--[6] Update period.status=CLOSED|                |
        |              |                  |      |--[7] Batch-lock vouchers---|                |
        |              |                  |      |--[8] Create audit event     |                |
        |              |                  |      |--[9] Commit transaction    |                |
        |              |<--[10] Return closed period--------------------------|                |
        |<--[11] Show success, refresh period selector----------------------|
```

**Steps:**

1. Chief Accountant navigates to Period Management, selects period, clicks "Close Period"
2. Frontend sends POST request with reason
3. Backend validates:
   - No vouchers with status=DRAFT in period
   - All posted vouchers balanced (sum of journal entries Dr=Cr per account)
4. If validation fails, return 400 with details
5. Start database transaction
6. Update period: status=CLOSED, closedBy=currentUser, closedAt=now, closeReason
7. Batch update all vouchers in period: add lock flag (prevent future edits)
8. Create audit log entry: period close event with hash digest
9. Commit transaction
10. Return updated period
11. Frontend refreshes period selector, shows closed badge, disables period in date pickers

## Non-Functional Requirements

### Performance

**Response Time Targets (per NFR1):**

- Voucher list page load: < 2 seconds (with server-side pagination, 20 items per page)
- Voucher form submission (create/update draft): < 1 second
- Voucher posting operation: < 3 seconds (including validation and journal entry generation)
- Period close operation: < 5 seconds (including validation and batch locking)

**Database Optimization (per NFR4):**

- **Indexes Required:**
  - `vouchers(company_id, voucher_date, status)` - Composite index for list queries
  - `vouchers(voucher_number)` - Unique index for voucher number lookup
  - `voucher_lines(voucher_id, line_number)` - Composite index for line retrieval
  - `journal_entries(period_id, account_id, company_id)` - Composite index for reporting queries
  - `journal_entries(voucher_id)` - Foreign key index
  - `accounting_periods(company_id, status, start_date, end_date)` - Composite index for period queries
- **Query Optimization:**
  - Use `@EntityGraph` to avoid N+1 queries when loading vouchers with lines
  - Batch fetch journal entries for multiple vouchers
  - Use native queries with `LIMIT/OFFSET` for pagination (avoid in-memory pagination)
  - Monitor slow queries (>1s) and optimize as needed

**Caching Strategy (per NFR26):**

- **Redis Cache Keys:**
  - `period:current:{companyId}` - Current open period (TTL: 1 hour, invalidate on period close)
  - `coa:leaf:{companyId}:{accountCode}` - Leaf/postable flag per account (TTL: 1 hour, invalidate on COA update)
  - `coa:controls:{companyId}` - Account controls map (131→customer, 331→supplier, etc.) (TTL: 1 hour)
  - `voucher:count:{companyId}:{status}` - Draft/posted counts (TTL: 5 minutes, invalidate on create/post)
- **Cache Invalidation:**
  - On voucher post: Invalidate voucher counts, period status (if period closed)
  - On period close: Invalidate all period-related caches
  - On COA update: Invalidate account metadata caches
- **Fallback:** If Redis unavailable, fall back to database queries (slower but functional)

**Frontend Performance:**

- **VoucherLineGrid:** Virtual scrolling for large vouchers (20+ lines), debounced validation (300ms), optimistic UI updates
- **DataTablePro:** Server-side pagination, request cancellation on filter change, skeleton loaders during fetch
- **Draft Auto-Save:** Debounced to 30 seconds or on blur, background persistence with retry logic

**Known Limitations:**

- Search with unaccented Vietnamese support may load all results before pagination (acceptable for MVP with <1000 records per company)
- Large voucher lists (>1000 vouchers) may require additional optimization (deferred to post-MVP)

### Security

**Authentication & Authorization (per NFR5, NFR6, NFR7):**

- **JWT Authentication:** All endpoints require valid JWT access token (15-30 min expiry)
- **Role-Based Access Control (RBAC):**
  - **Accountant:** Create/edit/delete drafts, view vouchers, upload attachments, view audit history
  - **Chief Accountant:** All Accountant permissions + post/unpost/reverse vouchers, close/reopen periods
  - **Admin:** Full access including period reopen, system configuration
  - **CFO:** View-only access to vouchers and reports
- **API-Level Enforcement:** Permissions enforced at Spring Security method level (`@PreAuthorize`), not just UI hiding
- **Company Isolation:** All queries filtered by `company_id` via `CompanyScopeAspect` (extends Epic 1 pattern)
- **HTTPS/TLS:** Mandatory for production, HttpOnly/Secure cookies for refresh tokens

**Data Protection (per NFR8, NFR9):**

- **Audit Trail Integrity:**
  - Immutable append-only audit logs with cryptographic hashing (SHA-256)
  - Hash digest stored with each audit entry for tamper detection
  - Exportable audit logs with hash watermark for legal defensibility
- **Input Validation:**
  - Server-side validation for all inputs (amounts must be positive, dates within open period, account codes valid)
  - Parameterized queries to prevent SQL injection
  - XSS protection via React defaults (no `dangerouslySetInnerHTML` for user input)
  - File upload validation: File type whitelist (PDF, images), size limits (10MB max), virus scan simulation
- **File Storage Security:**
  - Randomized storage paths in Supabase Storage (prevent enumeration)
  - Signed URLs with 10-minute expiry for downloads
  - Access logging for all file downloads/views

**Security Best Practices:**

- **Period Locking:** Prevent posting to closed periods at API level (return 400 with clear message), not just UI disable
- **Reversal Protection:** Block double reversal attempts (409 Conflict), log all reversal attempts (even if blocked) for fraud detection
- **Negative Amount Detection:** Block negative debit/credit values, log as "possible fraud" and alert admin
- **Dependency Checking:** Prevent unposting vouchers referenced in payments/receipts (Epic 4-5 integration)

### Reliability/Availability

**Data Integrity (per NFR10, NFR11, NFR12, NFR13, NFR14):**

- **Double-Entry Enforcement:**
  - Atomic transaction ensures voucher posting + journal entry creation (all-or-nothing)
  - Validation: Sum(Dr) == Sum(Cr) using BigDecimal with proper rounding
  - Rollback on validation failure, return detailed error map
- **Leaf-Only Posting:**
  - Database constraint: `chart_of_accounts.postable = true` AND no children
  - Application-level validation before posting, UI disables non-postable accounts
- **Required Dimensions:**
  - Validation per `account_controls` table: 131→customer, 331→supplier, 154/621→cost center (if configured)
  - All validation errors shown at once (not one-by-one)
- **Period Integrity:**
  - Database constraint: Voucher date must fall within period range
  - Application-level validation: Prevent posting to closed periods (400 error)
  - Period close: Batch-lock all vouchers in period (prevent future edits)
- **Referential Integrity:**
  - Foreign key constraints: `voucher_lines.voucher_id`, `voucher_lines.account_id`, `journal_entries.voucher_id`, `journal_entries.account_id`
  - Cascade delete: Lines deleted when voucher deleted (drafts only)
  - Prevent deletion of posted vouchers or vouchers with references (409 Conflict)

**Error Recovery:**

- **Draft Auto-Save:**
  - Optimistic UI updates with background persistence
  - Retry on failure (3 retries with exponential backoff: 1s, 2s, 4s)
  - Clear error messages in Vietnamese, allow manual retry
  - Session lock cleared if lost >5 minutes
- **Posting Failures:**
  - Transaction rollback on validation error
  - Return detailed error map with field-level errors
  - Allow user to fix errors and retry posting
- **Network Failures:**
  - Frontend retry logic for API calls (3 retries with exponential backoff)
  - Show offline indicator, queue draft saves for retry when online
  - Request cancellation on component unmount

**Availability:**

- **Target:** 99% uptime for MVP (no strict SLA, monitor and optimize)
- **Degradation Strategy:**
  - If Redis unavailable: Fall back to database queries (slower but functional)
  - If Supabase Storage unavailable: Show error message, allow voucher creation without attachments
- **Database Failures:**
  - Connection pooling: HikariCP (default Spring Boot, 10-20 connections)
  - Retry logic for transient failures (connection timeout, deadlock)
  - Clear error messages in Vietnamese for user-facing errors

**Data Retention (per NFR10):**

- Audit logs: Retain for 10 years (per Circular 200 compliance)
- Vouchers: Soft delete for drafts, hard delete not allowed for posted vouchers
- Journal entries: Never deleted (immutable for compliance)

### Observability

**Logging (per NFR24):**

- **Structured Logs:** JSON format with fields:
  ```json
  {
    "timestamp": "2025-11-13T12:00:00Z",
    "level": "INFO",
    "logger": "com.accounting.service.VoucherService",
    "message": "Voucher posted successfully",
    "userId": "user-123",
    "companyId": "company-456",
    "voucherId": "voucher-789",
    "requestId": "req-abc123",
    "voucherNumber": "VC2025-001",
    "status": "POSTED"
  }
  ```
- **Log Levels:**
  - **INFO:** Normal operations (voucher created, posted, period closed)
  - **WARN:** Validation failures, blocked operations (posting to closed period, double reversal attempt)
  - **ERROR:** System errors, database failures, unexpected exceptions
  - **AUDIT:** All voucher lifecycle events (create/edit/post/unpost/reverse) with before/after snapshots
- **Sensitive Data Masking:** Mask password hashes, credit card numbers (if any) in logs

**Metrics (per NFR25):**

- **Key Metrics:**
  - Voucher creation rate (vouchers/hour)
  - Posting success rate (% of post attempts that succeed)
  - Validation error rate (% of vouchers with validation errors)
  - Average posting time (P50, P95, P99)
  - Period close frequency and duration
- **Database Metrics:**
  - Query execution time (track slow queries >1s)
  - Connection pool utilization (HikariCP metrics)
  - Transaction rollback rate
- **Application Metrics:**
  - Request count per endpoint
  - Error rate (4xx, 5xx responses)
  - Response time percentiles (P50, P95, P99)

**Tracing:**

- **Request ID:** Propagate `X-Request-ID` header through all service calls for correlation
- **Audit Trail:** Every voucher event logged with full context:
  - Before/after JSON snapshots
  - User ID, role, company ID
  - Timestamp, device/IP (if available)
  - Cryptographic hash for integrity verification

**Monitoring:**

- **Health Checks:** `/health` endpoint returns 200 OK if:
  - Database connection pool healthy
  - Redis connection available (optional, degrade gracefully if unavailable)
- **Alerts (deferred to post-MVP, but design for):**
  - High error rate (>5% of requests)
  - Slow queries (>1s execution time)
  - Database connection pool exhaustion
  - Period close failures
  - Unusual reversal patterns (possible fraud)

## Dependencies and Integrations

**External Dependencies:**

- **PostgreSQL (Supabase):** Primary database for vouchers, voucher_lines, journal_entries, accounting_periods, voucher_attachments, voucher_templates, voucher_template_lines tables
- **Redis:** Caching layer for period status, COA metadata, voucher counts (optional, degrades gracefully)
- **Supabase Storage:** File storage for voucher attachments (PDF, images), signed URL generation
- **Spring Boot 3.5.7:** Backend framework with Spring Data JPA, Spring Security, Spring Cache
- **React 18+ with TypeScript:** Frontend framework with shadcn/ui components, TanStack Table

**Internal Dependencies:**

- **Epic 1 (Foundation):** Company context, RBAC, JWT authentication, User entity, Company entity
- **Epic 2 (Master Data):** ChartOfAccount entity (for account validation and template default accounts), Customer entity (for AR dimension), Supplier entity (for AP dimension), AuditLogService (extends for voucher events)
- **Future Epics:**
  - **Epic 4 (AP):** Will reference vouchers for payment linking, dependency checking for unposting
  - **Epic 5 (AR):** Will reference vouchers for receipt linking, dependency checking for unposting
  - **Epic 7 (Reporting):** Will query journal_entries for Trial Balance, Financial Statements

**Voucher Templates Integration:**

- Templates reference ChartOfAccount entities for default debit/credit accounts
- Templates can specify required dimensions (customer for AR accounts, supplier for AP accounts)
- Templates are company-scoped (each company can have their own template library)
- Template management (CRUD) requires Chief Accountant+ role (templates are company-wide configurations)

**Integration Points:**

- **Voucher → Chart of Accounts:** Foreign key relationship, validates leaf-only posting, required dimensions
- **Voucher → Period:** Foreign key relationship, validates open period, prevents posting to closed periods
- **Voucher → Journal Entries:** One-to-many relationship, generated on posting, immutable after creation
- **Voucher → Attachments:** One-to-many relationship, stored in Supabase Storage, metadata in database
- **Voucher → Audit Logs:** Lifecycle events logged via AuditLogService, includes before/after snapshots
- **Voucher → Template:** Optional relationship, tracks which template was used to create voucher (for audit and reporting)

**Version Constraints:**

- Java 21 (required for Spring Boot 3.5.7)
- PostgreSQL 15+ (required for Supabase)
- Redis 7.x (optional, for caching)
- Node.js 18+ (for frontend build)
- pnpm (latest stable, for frontend package management)

**API Dependencies:**

- **Supabase Storage REST API:** For file upload/download, signed URL generation
- **Internal REST API:** `/api/v1/vouchers/*`, `/api/v1/periods/*`, `/api/v1/voucher-templates/*` endpoints

**No External Service Dependencies:**

- No third-party payment processors
- No external accounting system integrations (deferred to post-MVP)
- No email service (deferred to Epic 4-5 for invoice/bill notifications)

## Acceptance Criteria (Authoritative)

**Story 3.1: Voucher List and Search**

1. Voucher list table displays columns: Voucher Number, Date, Type, Amount (total), Status (DRAFT/POSTED/REVERSED), Entered By (user name), Posted By (user name, if posted), AR/AP Entity (customer/supplier name if applicable), Reversal Badge (if reversed), Attachment Count.
2. Filters (status, date range, account), search query text, and sort order persist in user session/localStorage per company.
3. Live badge counts display draft vs posted voucher totals, updated in real-time after create/post operations.
4. Fuzzy search supports unaccented Vietnamese text matching on voucher number, description fields; Unicode-aware search.
5. Multi-column sorting supported (e.g., date DESC + status ASC); sort state persists in session.
6. Delete action allowed only for vouchers with status=DRAFT and no references (no linked payments/receipts); requires mandatory reason field captured in audit log.
7. Server-side pagination with page size selector (10/20/30/50/100); infinite scroll deferred to post-MVP.
8. Empty state displays when no vouchers found: helpful message, "Create First Voucher" CTA, "Reset Filters" link.
9. API error handling: retry button on error toast, detailed error message in modal, "Copy Error Details" button exports JSON for support.
10. RBAC enforcement: non-admin users see only their company's vouchers; department/role scoping deferred to post-MVP.

**Story 3.2: Voucher Form (Create/Edit) – Line Item Engine**

1. Tab/Enter keyboard navigation through header fields and line grid cells; auto-add new line when tabbing out of last cell in last row.
2. Inline error indicators (red border, error icon) and tooltips display for required fields (account, debit/credit) and missing dimensions (customer for 131, supplier for 331).
3. Date picker disables dates outside open periods, auto-jumps to latest open period on open.
4. Currency field set to VND, read-only in MVP (hidden from form UI).
5. Row reordering via drag/drop; keyboard shortcuts: Ctrl+N (insert row), Ctrl+D (duplicate row), Ctrl+Backspace (delete row).
6. Invalid/incomplete lines saved as draft with clear visual markers (error badges); posting blocked until all lines valid.
7. VoucherLineGrid supports 20+ lines with smooth rendering (virtual scrolling), target: 20 lines entered in <60 seconds for QA testing.
8. Attachments allowed before and after draft save; inline error messages for unsupported file types, size limits, virus scan failures.
9. Draft auto-save is optimistic (UI updates immediately), persists every 30 seconds or on blur; edit lock cleared if session lost >5 minutes.
10. Undo functionality supports row/cell revert, persists undo state on draft save.
11. API returns detailed error map with field-level validation failures (not generic 400), format: `{ lines: { [lineNumber]: { [field]: [errors] } } }`.
12. **Voucher Templates:** Template selector available when creating new voucher; displays list of pre-configured templates (e.g., "Thu tiền mặt khách hàng", "Chi tiền mặt cho nhà cung cấp"); selecting template auto-fills debit/credit accounts and default descriptions; locked accounts (if template specifies) display as read-only with lock icon; user can add additional lines or modify template-applied lines.

**Story 3.3: Posting, Unposting & Reversal Workflows**

1. Posting operation changes voucher status atomically (DRAFT → POSTED), returns updated voucher and generated journal entries in single response.
2. Unposting checks all dependencies (referenced in payments/receipts from Epic 4-5); blocks with error popup showing conflicting references (e.g., "Cannot unpost – referenced in payment #P123").
3. Reversal creates new voucher with number format "REV-{original_number}", status "POSTED", links bi-directionally (original.reversedBy = reversal, reversal.reversalVoucher = original).
4. "Reversed by" badge on original voucher is clickable, navigates to reversal voucher detail page.
5. Export voucher and reversal trail as PDF with barcode/QR code (deferred to post-MVP, basic PDF export in MVP).
6. Double reversal blocked at UI and API level; 409 Conflict error returned, attempt logged in audit.
7. Deletion of posted voucher forbidden (UI disables delete button, API returns 409); all attempted deletions logged as blocked in audit.
8. Posting validation failures (period closed, Dr≠Cr, missing dimensions) block posting and display all errors at once (not one-by-one).
9. Batch posting/import: any validation error aborts entire batch, logs all issues/results in audit (batch import deferred to post-MVP).

**Story 3.4: Leaf-Only and Double-Entry Validation Engine**

1. UI disables non-postable (parent) accounts in AccountPicker; API blocks posting to non-leaf accounts, logs blocked attempt in audit.
2. Debit/Credit totals must always sum using BigDecimal with HALF_UP rounding; rounding logic documented in code comments.
3. Negative debit/credit values blocked at UI and API level; attempt logs "possible fraud" event and sends admin alert (email deferred, logged only).
4. Required dimension validation engine is company/config-driven via `account_controls` table; all validation errors shown at once (bulk validation).
5. Bulk validation runs for all lines before posting; QA test cases cover field-level errors for both single-line and multi-line validation paths.

**Story 3.5: Audit Trail for Voucher Lifecycle**

1. Every voucher event (create/edit/post/reverse/unpost/import) generates audit log entry with: JSON snapshot (before/after), SHA-256 diff hash, user ID/role, device/IP (if available).
2. Mass/batch actions log aggregated entry with: voucher IDs list, action stats (success/failure counts), start/end timestamp, details summary.
3. "Voucher history" view displays colored field-by-field diff (green=added, red=removed, yellow=changed) and plain English summary (e.g., "Voucher posted by John Doe on 2025-11-13").
4. Full audit logs exportable as PDF (with hash watermark) or JSON; export includes all events for voucher with cryptographic hashes.
5. Admin/audit dashboard displays mass actions, blocked operations, suspicious patterns (e.g., multiple reversal attempts); alerts deferred to post-MVP (logged only).

**Story 3.6: Period Selector & Voucher-Period Mapping**

1. Period selector always visible on voucher screens (header/toolbar), shows current period + 3 prior/next open periods (if available).
2. Voucher creation/posting blocked for closed/future periods; API returns 400 with clear error message, attempt logged.
3. Period close operation batch-locks all vouchers in period (adds lock flag), creates audit event with: closed_by (user), closed_at (timestamp), hash_digest, close_reason.
4. Period reopen requires reason and approval metadata; all reopen attempts (even if not approved) logged in audit.
5. System automates required reversal of prior-period adjustments by creating offsetting entry in next open period (deferred to post-MVP, manual reversal in MVP).
6. Period summary dashboard badge displays: closing status, posting flow status, pending actions count (drafts in period).

**Story 3.7: Attachments and Voucher Documentation**

1. Drag-and-drop file uploader with inline image/PDF preview, download button for all file types; unsupported file types blocked and attempt logged.
2. Attachments stored in Supabase Storage with randomized file names (UUID-based paths), metadata (filename, mimeType, fileSize) stored in database; access limited by company_id.
3. Each download/view/delete action logged in audit with: user ID, timestamp, IP address (if available).
4. Delete allowed only for vouchers with status=DRAFT and by creator or admin role; requires confirmation modal with mandatory reason field.
5. Download links are signed URLs with 10-minute expiry; URLs generated on-demand via Supabase Storage API.
6. Simulated virus scan triggered on upload (mock validation); blocks file type if scan fails, logs failure.
7. Voucher icon/badge always displays current attachment count; click opens attachment management modal.
8. File upload handles large files via multi-part upload, robust to network errors (retry logic, progress indicator).

## Traceability Mapping

| AC #   | Story | Acceptance Criteria                       | Spec Section                  | Component/API                                                                                | Test Idea                                                            |
| ------ | ----- | ----------------------------------------- | ----------------------------- | -------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| 3.1.1  | 3.1   | Voucher list columns                      | Data Models, APIs             | VoucherListPage, GET /api/v1/vouchers                                                        | Verify all columns render correctly, test API response structure     |
| 3.1.2  | 3.1   | Filter/sort persistence                   | Detailed Design               | VoucherListPage (localStorage)                                                               | Test filter state persists across page reloads                       |
| 3.1.3  | 3.1   | Live badge counts                         | Detailed Design               | VoucherListPage, GET /api/v1/vouchers/count                                                  | Test counts update after create/post operations                      |
| 3.1.4  | 3.1   | Fuzzy Vietnamese search                   | APIs, Performance             | GET /api/v1/vouchers?search=                                                                 | Test unaccented search, Unicode matching                             |
| 3.1.5  | 3.1   | Multi-column sorting                      | APIs                          | GET /api/v1/vouchers?sort=                                                                   | Test sort combinations, persistence                                  |
| 3.1.6  | 3.1   | Delete draft only                         | APIs, Security                | DELETE /api/v1/vouchers/{id}                                                                 | Test delete blocked for posted, requires reason                      |
| 3.1.7  | 3.1   | Server-side pagination                    | APIs, Performance             | GET /api/v1/vouchers?page=                                                                   | Test pagination, page size selector                                  |
| 3.1.8  | 3.1   | Empty state                               | Frontend Modules              | VoucherListPage                                                                              | Test empty state UI, CTAs                                            |
| 3.1.9  | 3.1   | Error recovery                            | Frontend Modules              | VoucherListPage (error handling)                                                             | Test retry button, error modal, JSON export                          |
| 3.1.10 | 3.1   | RBAC company scoping                      | Security, APIs                | CompanyScopeAspect, GET /api/v1/vouchers                                                     | Test company isolation, RBAC enforcement                             |
| 3.2.1  | 3.2   | Keyboard navigation                       | Frontend Modules              | VoucherLineGrid                                                                              | Test Tab/Enter navigation, auto-add row                              |
| 3.2.2  | 3.2   | Inline error indicators                   | Frontend Modules              | VoucherLineGrid                                                                              | Test error display, tooltips, required fields                        |
| 3.2.3  | 3.2   | Date picker period validation             | Frontend Modules, APIs        | DatePicker, PeriodManagementService                                                          | Test disabled dates, auto-jump to open period                        |
| 3.2.4  | 3.2   | Currency VND                              | Data Models                   | VoucherDTO                                                                                   | Test currency field (hidden, VND only)                               |
| 3.2.5  | 3.2   | Row operations                            | Frontend Modules              | VoucherLineGrid                                                                              | Test drag/drop, keyboard shortcuts                                   |
| 3.2.6  | 3.2   | Draft with invalid lines                  | Workflows                     | VoucherFormPage, POST /api/v1/vouchers                                                       | Test draft save with errors, posting blocked                         |
| 3.2.7  | 3.2   | 20+ lines performance                     | Performance, Frontend Modules | VoucherLineGrid (virtual scrolling)                                                          | Test rendering performance, 20 lines <60s entry                      |
| 3.2.8  | 3.2   | Attachments before/after save             | APIs                          | POST /api/v1/vouchers/{id}/attachments                                                       | Test upload before/after draft, error handling                       |
| 3.2.9  | 3.2   | Draft auto-save                           | Workflows, Reliability        | VoucherFormPage (auto-save)                                                                  | Test optimistic update, 30s auto-save, session lock                  |
| 3.2.10 | 3.2   | Undo functionality                        | Frontend Modules              | VoucherLineGrid                                                                              | Test undo/redo, persistence on save                                  |
| 3.2.11 | 3.2   | Field-level error map                     | APIs                          | POST /api/v1/vouchers, ValidationErrorMap                                                    | Test detailed error response structure                               |
| 3.2.12 | 3.2   | Voucher templates                         | Frontend Modules, APIs        | VoucherTemplateSelector, GET /api/v1/voucher-templates, POST /api/v1/vouchers/apply-template | Test template selection, auto-fill accounts, locked accounts display |
| 3.3.1  | 3.3   | Atomic posting                            | Workflows, Data Models        | VoucherPostingService, POST /api/v1/vouchers/{id}/post                                       | Test transaction atomicity, response structure                       |
| 3.3.2  | 3.3   | Unpost dependency check                   | Workflows, APIs               | VoucherPostingService, POST /api/v1/vouchers/{id}/unpost                                     | Test dependency validation, error message                            |
| 3.3.3  | 3.3   | Reversal creation                         | Workflows, APIs               | VoucherPostingService, POST /api/v1/vouchers/{id}/reverse                                    | Test reversal voucher creation, bi-directional links                 |
| 3.3.4  | 3.3   | Reversal badge navigation                 | Frontend Modules              | VoucherListPage, VoucherFormPage                                                             | Test badge click, navigation to reversal                             |
| 3.3.5  | 3.3   | PDF export with barcode                   | APIs (deferred)               | GET /api/v1/vouchers/{id}/export                                                             | Test PDF generation (deferred to post-MVP)                           |
| 3.3.6  | 3.3   | Double reversal block                     | Security, APIs                | POST /api/v1/vouchers/{id}/reverse                                                           | Test 409 error, audit logging                                        |
| 3.3.7  | 3.3   | Posted voucher deletion block             | Security, APIs                | DELETE /api/v1/vouchers/{id}                                                                 | Test 409 error, audit logging                                        |
| 3.3.8  | 3.3   | All errors shown at once                  | APIs, Workflows               | VoucherPostingService, ValidationErrorMap                                                    | Test bulk error display, not sequential                              |
| 3.3.9  | 3.3   | Batch posting error handling              | Workflows (deferred)          | BatchPostingService                                                                          | Test batch abort on error, audit logging (deferred)                  |
| 3.4.1  | 3.4   | Leaf-only validation                      | Security, APIs                | VoucherValidationService, AccountPicker                                                      | Test UI disable, API block, audit log                                |
| 3.4.2  | 3.4   | BigDecimal double-entry                   | Data Models, Workflows        | VoucherPostingService, BigDecimal                                                            | Test Dr=Cr validation, rounding logic                                |
| 3.4.3  | 3.4   | Negative amount block                     | Security, APIs                | VoucherValidationService                                                                     | Test UI/API block, fraud logging                                     |
| 3.4.4  | 3.4   | Required dimensions                       | Security, APIs                | VoucherValidationService, account_controls                                                   | Test config-driven validation, bulk errors                           |
| 3.4.5  | 3.4   | Bulk validation                           | APIs, Test Strategy           | VoucherValidationService                                                                     | Test all lines validated, field-level errors                         |
| 3.5.1  | 3.5   | Audit log generation                      | Security, APIs                | AuditLogService, voucher events                                                              | Test JSON snapshot, hash, user/device/IP                             |
| 3.5.2  | 3.5   | Mass action logging                       | Security, APIs                | AuditLogService                                                                              | Test aggregated audit entry, stats                                   |
| 3.5.3  | 3.5   | Voucher history view                      | Frontend Modules              | VoucherHistoryView                                                                           | Test colored diffs, plain English summary                            |
| 3.5.4  | 3.5   | Audit log export                          | APIs                          | GET /api/v1/vouchers/{id}/history/export                                                     | Test PDF/JSON export, hash watermark                                 |
| 3.5.5  | 3.5   | Admin audit dashboard                     | Frontend Modules (deferred)   | AdminAuditDashboard                                                                          | Test mass actions, suspicious patterns (deferred)                    |
| 3.6.1  | 3.6   | Period selector UI                        | Frontend Modules              | PeriodSelector                                                                               | Test always visible, current + 3 prior/next                          |
| 3.6.2  | 3.6   | Closed period block                       | Security, APIs                | PeriodManagementService, POST /api/v1/vouchers                                               | Test 400 error, UI disable, audit log                                |
| 3.6.3  | 3.6   | Period close batch-lock                   | Workflows, APIs               | PeriodManagementService, POST /api/v1/periods/{id}/close                                     | Test batch lock, audit event                                         |
| 3.6.4  | 3.6   | Period reopen audit                       | Security, APIs                | PeriodManagementService, POST /api/v1/periods/{id}/reopen                                    | Test reason/approval, audit logging                                  |
| 3.6.5  | 3.6   | Prior-period reversal automation          | Workflows (deferred)          | PeriodManagementService                                                                      | Test offsetting entry (deferred to post-MVP)                         |
| 3.6.6  | 3.6   | Period summary badge                      | Frontend Modules              | PeriodSelector                                                                               | Test closing status, posting flow, pending actions                   |
| 3.7.1  | 3.7   | Drag-drop uploader                        | Frontend Modules, APIs        | AttachmentDropzone, POST /api/v1/vouchers/{id}/attachments                                   | Test upload, preview, download, error handling                       |
| 3.7.2  | 3.7   | External storage with metadata            | APIs, Data Models             | VoucherAttachmentService, Supabase Storage                                                   | Test randomized paths, metadata storage, company isolation           |
| 3.7.3  | 3.7   | Download/view/delete logging              | Security, APIs                | VoucherAttachmentService, AuditLogService                                                    | Test audit logging for all access                                    |
| 3.7.4  | 3.7   | Delete restrictions                       | Security, APIs                | DELETE /api/v1/vouchers/{id}/attachments/{aid}                                               | Test draft-only, creator/admin only, confirmation                    |
| 3.7.5  | 3.7   | Signed URL expiry                         | Security, APIs                | GET /api/v1/vouchers/{id}/attachments/{aid}/download                                         | Test 10-minute expiry, URL regeneration                              |
| 3.7.6  | 3.7   | Virus scan simulation                     | Security, APIs                | VoucherAttachmentService                                                                     | Test mock scan, file type blocking                                   |
| 3.7.7  | 3.7   | Attachment count badge                    | Frontend Modules              | VoucherFormPage, VoucherListPage                                                             | Test count display, modal open                                       |
| 3.7.8  | 3.7   | Multi-part upload, network error handling | APIs, Reliability             | VoucherAttachmentService                                                                     | Test large file upload, retry logic, progress                        |

## Risks, Assumptions, Open Questions

### Risks

**Risk 1: Performance Degradation with Large Voucher Lists**

- **Description:** Server-side pagination with unaccented Vietnamese search may load all results before pagination, causing performance issues with >1000 vouchers per company.
- **Impact:** High - User experience degradation, potential timeout errors
- **Mitigation:** Acceptable for MVP (<1000 records), monitor query performance, optimize with database-level pagination in post-MVP
- **Owner:** Backend team

**Risk 2: Complex Validation Logic Bugs**

- **Description:** Leaf-only, double-entry, and required dimension validations are complex; bugs could allow invalid vouchers to be posted.
- **Impact:** Critical - Data integrity violation, compliance risk
- **Mitigation:** Comprehensive unit and integration tests, code review, QA testing with edge cases
- **Owner:** Backend team, QA team

**Risk 3: Period Close Race Condition**

- **Description:** Concurrent period close operations or voucher posting during period close could cause data inconsistency.
- **Impact:** High - Data integrity violation
- **Mitigation:** Database-level locking (SELECT FOR UPDATE), transaction isolation, clear error messages
- **Owner:** Backend team

**Risk 4: Draft Auto-Save Data Loss**

- **Description:** Browser crash or network failure during draft auto-save could result in data loss.
- **Impact:** Medium - User frustration, rework required
- **Mitigation:** Optimistic UI updates, retry logic with exponential backoff, session lock timeout, clear error messages
- **Owner:** Frontend team

**Risk 5: Supabase Storage Integration Complexity**

- **Description:** File upload/download, signed URL generation, and access control integration with Supabase Storage may have unexpected issues.
- **Impact:** Medium - Attachment feature may be delayed
- **Mitigation:** Early integration testing, fallback to basic file storage if needed, clear error handling
- **Owner:** Backend team

**Risk 6: Reversal Workflow Complexity**

- **Description:** Bi-directional linking, auto-posting, and dependency checking for reversals is complex and may have edge cases.
- **Impact:** Medium - Incorrect reversals could affect reporting
- **Mitigation:** Comprehensive test cases, code review, audit logging for all reversal attempts
- **Owner:** Backend team

### Assumptions

**Assumption 1: Epic 2 Master Data Complete**

- **Description:** Chart of Accounts, Customers, Suppliers from Epic 2 are fully implemented and available for voucher validation.
- **Validation:** Epic 2 is marked "done" in sprint-status.yaml
- **Owner:** Product Owner

**Assumption 2: Company Context and RBAC Working**

- **Description:** Epic 1's company context filtering and RBAC enforcement are working correctly and will be extended for vouchers.
- **Validation:** Epic 1 is marked "done", company scoping tested
- **Owner:** Backend team

**Assumption 3: Period Management Foundation**

- **Description:** Accounting periods and fiscal year management are implemented (may be part of Epic 1 or Epic 2).
- **Validation:** Period entity and basic CRUD available
- **Owner:** Product Owner

**Assumption 4: Single Currency (VND) Only**

- **Description:** Multi-currency support is deferred to post-MVP, simplifying voucher entry and validation.
- **Validation:** PRD confirms single-currency scope
- **Owner:** Product Owner

**Assumption 5: No Batch Import in MVP**

- **Description:** Batch voucher import/export is deferred to post-MVP, reducing complexity.
- **Validation:** Epic 3 scope confirmed
- **Owner:** Product Owner

### Open Questions

**Question 1: Period Reopen Approval Workflow**

- **Description:** What is the exact approval workflow for period reopen? Single approver or multi-level?
- **Status:** Deferred to implementation - MVP will require reason and approval metadata, exact workflow TBD
- **Owner:** Product Owner, Chief Accountant stakeholders

**Question 2: Prior-Period Adjustment Automation**

- **Description:** Should prior-period adjustments automatically create offsetting entries in next open period, or manual reversal only?
- **Status:** Deferred to post-MVP - MVP supports manual reversal only
- **Owner:** Product Owner

**Question 3: Attachment File Size Limits**

- **Description:** What is the maximum file size per attachment? Total size per voucher?
- **Status:** Assumed 10MB per file, total per voucher TBD - will be configurable
- **Owner:** Product Owner

**Question 4: Audit Log Retention Policy**

- **Description:** How long should audit logs be retained? 10 years per Circular 200, but storage strategy?
- **Status:** 10 years confirmed, storage/archival strategy TBD for post-MVP
- **Owner:** Product Owner, DevOps

**Question 5: Voucher Number Sequence Reset**

- **Description:** Should voucher number sequences reset annually or continue across years?
- **Status:** Assumed annual reset (VC{YYYY}-{seq}), to be confirmed
- **Owner:** Product Owner

## Test Strategy Summary

### Test Levels

**Unit Tests (Backend):**

- **Coverage Target:** 70% for business logic layer (per NFR20)
- **Focus Areas:**
  - VoucherValidationService: Leaf-only, double-entry, required dimensions
  - VoucherPostingService: Atomic posting, journal entry generation
  - PeriodManagementService: Period close validation, batch locking
  - BigDecimal rounding logic for double-entry validation
- **Framework:** JUnit 5, Mockito
- **Owner:** Backend team

**Integration Tests (Backend):**

- **Coverage Target:** Critical workflows (posting, reversal, period close)
- **Focus Areas:**
  - Voucher CRUD with company scoping
  - Posting workflow with transaction rollback on error
  - Reversal workflow with bi-directional linking
  - Period close with batch locking
  - Attachment upload/download with Supabase Storage
- **Framework:** Spring Boot Test, TestContainers for PostgreSQL
- **Owner:** Backend team

**API Tests:**

- **Coverage Target:** All REST endpoints, error scenarios
- **Focus Areas:**
  - Authentication and RBAC enforcement
  - Validation error responses (field-level error maps)
  - Business rule violations (409 Conflict)
  - Company isolation
- **Framework:** REST Assured or Spring MockMvc
- **Owner:** Backend team

**Frontend Unit Tests:**

- **Coverage Target:** 60% for components and hooks
- **Focus Areas:**
  - VoucherLineGrid: Keyboard navigation, validation, row operations
  - VoucherFormPage: Draft auto-save, error handling
  - PeriodSelector: Period validation, UI state
- **Framework:** Vitest, Testing Library
- **Owner:** Frontend team

**E2E Tests (Deferred to Post-MVP):**

- **Coverage Target:** Critical user journeys
- **Focus Areas:**
  - Create voucher → Post → Reverse workflow
  - Period close workflow
  - Attachment upload/download
- **Framework:** Playwright or Cypress (deferred)
- **Owner:** QA team

### Test Data Strategy

**Test Fixtures:**

- Pre-seeded Chart of Accounts (TT200-compliant)
- Test companies, users with different roles
- Test accounting periods (open/closed)
- Sample vouchers (draft, posted, reversed)

**Test Scenarios:**

- **Happy Path:** Create draft → Add lines → Post → View → Reverse
- **Validation Errors:** Leaf-only violation, Dr≠Cr, missing dimensions, closed period
- **Business Rules:** Double reversal, unpost with dependencies, delete posted voucher
- **Edge Cases:** Large vouchers (20+ lines), concurrent operations, network failures

### Performance Testing

**Load Testing (Deferred to Post-MVP):**

- Target: 20 concurrent users (per NFR3)
- Scenarios: Voucher list pagination, posting operations, period close
- Tools: JMeter or k6 (deferred)

**Performance Benchmarks:**

- Voucher list load: <2s (20 items)
- Voucher posting: <3s
- 20-line entry: <60s (QA target)

### Security Testing

**Focus Areas:**

- RBAC enforcement at API level
- Company isolation (cross-company data access)
- Input validation (SQL injection, XSS)
- File upload security (type validation, size limits)
- Signed URL expiry (10 minutes)

**Tools:** Manual testing, OWASP ZAP (deferred to post-MVP)

### Test Automation

**CI/CD Integration:**

- Unit and integration tests run on every commit
- API tests run on pull request
- Frontend tests run on pull request
- Coverage reports generated and tracked

**Test Maintenance:**

- Tests updated with code changes
- Flaky tests identified and fixed
- Test data refreshed regularly

### Acceptance Criteria Coverage

All 48 acceptance criteria (3.1.1 through 3.7.8) have corresponding test cases mapped in the Traceability Mapping table above. Test execution will verify:

- Functional correctness (all ACs)
- Performance targets (3.2.7, NFR1)
- Security requirements (3.1.10, 3.4.1, 3.4.3, etc.)
- Error handling (3.1.9, 3.2.11, 3.3.8)
- Audit trail integrity (3.5.1, 3.5.2, 3.5.4)
