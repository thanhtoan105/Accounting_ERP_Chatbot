# Epic Technical Specification: Reporting Engine – Core Financials

Date: 2025-11-29
Author: thanhtoan
Epic ID: 7
Status: Draft

---

## Overview

Epic 7 delivers the core financial reporting engine for the accounting system, enabling generation of TT200-compliant statutory reports including Trial Balance (S06-DN), Balance Sheet (B01-DN), Income Statement (B02-DN), Cash Flow Statement (B03-DN), and the Detailed Ledger Report (F01/Sổ kế toán chi tiết). This epic builds upon the General Ledger foundation from Epics 3-6 and transforms raw GL data into actionable financial insights with drill-down capabilities, export functionality, and audit compliance.

The reporting engine must serve three primary personas: Chief Accountants requiring operational reconciliation (Trial Balance with drill-down), CFOs needing strategic insights (statutory reports with variance analysis), and external auditors demanding tamper-evident audit trails and snapshot reproducibility.

## Objectives and Scope

### In-Scope

- **Trial Balance (S06-DN)**: Period-based generation with opening/period/closing balances, drill-down to vouchers, PDF/Excel export with TT200 layout
- **Statutory Reports (B01-DN, B02-DN, B03-DN, F01)**: Balance Sheet, Income Statement, Cash Flow (direct method), Detailed Ledger per TT200 templates
- **TT200 Mapping Engine**: Configurable account-to-line mappings with versioning, audit trails, and rollback capability
- **Report Scheduling & Distribution**: Cron-based scheduling, email distribution with signed links, access control
- **Multi-Period Comparison**: 3-12 period comparison with variance analysis (absolute and percentage)
- **Drill-Down Navigation**: From report lines → accounts → vouchers → attachments with breadcrumb navigation
- **Export Engine**: PDF (TT200 layout), Excel (with header sheet), CSV with data hash and manifest
- **Report Audit Trail**: View/export/schedule actions logged with user, IP, timestamp, snapshot hash
- **Snapshot Immutability**: Closed-period snapshots with WORM-like retention, legal holds, integrity verification

### Out-of-Scope

- Real-time dashboard (Epic 8)
- AI-powered insights and forecasting (Epic 9)
- Consolidated multi-company reporting
- Custom report builder/designer
- Third-party ERP integrations

## System Architecture Alignment

The Reporting Engine aligns with the existing architecture as follows:

| Architecture Component | Report Engine Integration |
|------------------------|---------------------------|
| **Multi-tenancy** | All report queries filtered by `company_id` via `CompanyContext`; RBAC enforced at service layer |
| **Data Layer** | Read-only access to `vouchers`, `voucher_lines`, `chart_of_accounts`, `accounting_periods` via JPA repositories |
| **Caching (Redis)** | Report snapshots cached with TTL; invalidation on GL post, period close, mapping edit |
| **API Pattern** | REST endpoints under `/api/v1/reports/*` following existing patterns |
| **Security** | Spring Security with JWT; role-based access (CFO, Chief Accountant, Auditor roles) |
| **File Storage** | Export files stored in Supabase Storage with signed URLs (≤7 days expiry) |
| **Audit Trail** | All report access/export logged to `audit_logs` table |

**Key Constraints:**
- Reports must pull only from **posted** GL entries (status = 'POSTED')
- All monetary calculations use `BigDecimal` with `RoundingMode.HALF_UP`
- PDF generation uses existing Vietnamese font support (UTF-8)
- Date/number formatting per Vietnamese locale (`dd/MM/yyyy`, `#.###,##`)

## Detailed Design

### Services and Modules

| Service | Responsibility | Key Methods |
|---------|----------------|-------------|
| `TrialBalanceService` | Generate S06-DN Trial Balance with period filtering | `generate(periodId)`, `drillDown(accountId, periodId)`, `export(format)` |
| `StatutoryReportService` | Generate B01/B02/B03/F01 reports with TT200 mappings | `generateBalanceSheet(periodId)`, `generateIncomeStatement(periodId)`, `generateCashFlow(periodId)`, `generateDetailedLedger(accountId, periodId)` |
| `ReportMappingService` | Manage TT200 account-to-line mappings with versioning | `getMappings(reportType)`, `updateMapping(lineId, accounts)`, `getVersion(reportType)`, `rollback(versionId)` |
| `ReportSchedulerService` | Manage scheduled report jobs and distribution | `createSchedule(config)`, `executeJob(scheduleId)`, `cancelSchedule(scheduleId)` |
| `ReportExportService` | Generate PDF/Excel/CSV exports with hash and manifest | `exportPdf(reportData)`, `exportExcel(reportData)`, `generateManifest(files)` |
| `ReportSnapshotService` | Store and retrieve immutable report snapshots | `createSnapshot(reportId, data)`, `getSnapshot(snapshotId)`, `verifyIntegrity(snapshotId)` |
| `ReportAuditService` | Log all report access and export activities | `logAccess(reportId, action, userId)`, `getAuditTrail(reportId)` |

**Module Dependencies:**
```
ReportController
    └── TrialBalanceService
    └── StatutoryReportService
            └── ReportMappingService
            └── ReportExportService
                    └── ReportSnapshotService
    └── ReportSchedulerService
    └── ReportAuditService (cross-cutting)
```

### Data Models and Contracts

**New Entities:**

```sql
-- TT200 Report Line Mappings
CREATE TABLE report_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL, -- 'B01', 'B02', 'B03', 'F01', 'S06'
    line_code VARCHAR(20) NOT NULL,   -- TT200 line code (e.g., '100', '110')
    line_name VARCHAR(255) NOT NULL,  -- Vietnamese line name
    account_pattern TEXT NOT NULL,    -- Account codes/ranges (e.g., '111,112,113' or '1*')
    operator VARCHAR(10) DEFAULT 'SUM', -- 'SUM', 'DIFF', 'ABS'
    sign_modifier INTEGER DEFAULT 1,  -- 1 or -1 for balance direction
    display_order INTEGER NOT NULL,
    parent_line_code VARCHAR(20),     -- For hierarchical lines
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(company_id, report_type, line_code, version)
);

-- Report Snapshots (Immutable)
CREATE TABLE report_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL,
    period_id UUID NOT NULL REFERENCES accounting_periods(id),
    parameters JSONB NOT NULL,        -- Filter params used
    mapping_version INTEGER NOT NULL,
    data_hash VARCHAR(64) NOT NULL,   -- SHA-256 of report data
    snapshot_data JSONB NOT NULL,     -- Full report data
    generated_by UUID NOT NULL REFERENCES users(id),
    generated_at TIMESTAMP DEFAULT NOW(),
    is_final BOOLEAN DEFAULT FALSE,   -- TRUE when period is closed
    legal_hold BOOLEAN DEFAULT FALSE,
    legal_hold_reason TEXT,
    legal_hold_by UUID REFERENCES users(id),
    legal_hold_at TIMESTAMP
);

-- Report Schedules
CREATE TABLE report_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    report_type VARCHAR(20) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    period_rule VARCHAR(50) NOT NULL, -- 'LAST_CLOSED', 'CURRENT', 'SPECIFIC'
    export_formats TEXT[] NOT NULL,   -- ['PDF', 'EXCEL']
    recipients TEXT[] NOT NULL,       -- Email addresses
    owner_id UUID NOT NULL REFERENCES users(id),
    is_active BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Report Audit Log (extension of audit_logs)
CREATE TABLE report_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL,
    snapshot_id UUID REFERENCES report_snapshots(id),
    action VARCHAR(50) NOT NULL,      -- 'VIEW', 'EXPORT', 'SCHEDULE', 'DRILL_DOWN'
    user_id UUID NOT NULL REFERENCES users(id),
    ip_address INET,
    user_agent TEXT,
    parameters JSONB,                 -- Filters, export format, etc.
    result VARCHAR(20) NOT NULL,      -- 'SUCCESS', 'BLOCKED', 'ERROR'
    created_at TIMESTAMP DEFAULT NOW()
);
```

**DTOs:**

```java
// Trial Balance Response
public record TrialBalanceDTO(
    UUID periodId,
    String periodName,
    List<TrialBalanceLineDTO> lines,
    BigDecimal totalOpeningDebit,
    BigDecimal totalOpeningCredit,
    BigDecimal totalPeriodDebit,
    BigDecimal totalPeriodCredit,
    BigDecimal totalClosingDebit,
    BigDecimal totalClosingCredit,
    boolean isBalanced,
    String snapshotHash,
    LocalDateTime generatedAt
) {}

public record TrialBalanceLineDTO(
    String accountCode,
    String accountName,
    BigDecimal openingDebit,
    BigDecimal openingCredit,
    BigDecimal periodDebit,
    BigDecimal periodCredit,
    BigDecimal closingDebit,
    BigDecimal closingCredit,
    boolean isDrillable
) {}

// Statutory Report Response
public record StatutoryReportDTO(
    String reportType,
    UUID periodId,
    String periodName,
    UUID comparisonPeriodId,
    List<ReportLineDTO> lines,
    Integer mappingVersion,
    String snapshotHash,
    boolean isDraft,
    LocalDateTime generatedAt
) {}

public record ReportLineDTO(
    String lineCode,
    String lineName,
    Integer level,
    BigDecimal currentValue,
    BigDecimal priorValue,
    BigDecimal variance,
    BigDecimal variancePercent,
    boolean isDrillable,
    List<String> contributingAccounts
) {}
```

### APIs and Interfaces

**Trial Balance API:**
```
GET  /api/v1/reports/trial-balance
     Query: periodId, includeDrafts (default: false)
     Response: TrialBalanceDTO
     Roles: CHIEF_ACCOUNTANT, CFO, AUDITOR

GET  /api/v1/reports/trial-balance/drill-down/{accountCode}
     Query: periodId, page, size
     Response: Page<VoucherSummaryDTO>
     Roles: CHIEF_ACCOUNTANT, CFO, AUDITOR

POST /api/v1/reports/trial-balance/export
     Body: { periodId, format: 'PDF'|'EXCEL'|'CSV' }
     Response: { downloadUrl, expiresAt, hash }
     Roles: CHIEF_ACCOUNTANT, CFO
```

**Statutory Reports API:**
```
GET  /api/v1/reports/balance-sheet
     Query: periodId, comparisonPeriodId (optional)
     Response: StatutoryReportDTO
     Roles: CFO, CHIEF_ACCOUNTANT, AUDITOR

GET  /api/v1/reports/income-statement
     Query: periodId, comparisonPeriodId (optional)
     Response: StatutoryReportDTO
     Roles: CFO, CHIEF_ACCOUNTANT, AUDITOR

GET  /api/v1/reports/cash-flow
     Query: periodId
     Response: StatutoryReportDTO
     Roles: CFO, CHIEF_ACCOUNTANT

GET  /api/v1/reports/detailed-ledger
     Query: accountCodes[], periodId, subsidiaryType, subsidiaryId
     Response: DetailedLedgerDTO
     Roles: CHIEF_ACCOUNTANT, AUDITOR

POST /api/v1/reports/{reportType}/export
     Body: { periodId, format, comparisonPeriodId }
     Response: { downloadUrl, expiresAt, hash, manifestUrl }
     Roles: CFO, CHIEF_ACCOUNTANT
```

**Drill-Down API:**
```
GET  /api/v1/reports/drill-down/line/{reportType}/{lineCode}
     Query: periodId, page, size
     Response: { accounts: Page<AccountContributionDTO> }

GET  /api/v1/reports/drill-down/account/{accountCode}
     Query: periodId, page, size
     Response: { vouchers: Page<VoucherSummaryDTO> }

GET  /api/v1/reports/drill-down/voucher/{voucherId}
     Response: VoucherDetailDTO (with attachments)
```

**Mapping Management API:**
```
GET  /api/v1/reports/mappings/{reportType}
     Response: List<ReportMappingDTO>
     Roles: ADMIN, CHIEF_ACCOUNTANT

PUT  /api/v1/reports/mappings/{reportType}/{lineCode}
     Body: { accountPattern, operator, reason }
     Response: ReportMappingDTO (new version created)
     Roles: ADMIN

GET  /api/v1/reports/mappings/{reportType}/history
     Response: List<MappingVersionDTO>
     Roles: ADMIN, AUDITOR

POST /api/v1/reports/mappings/{reportType}/rollback/{versionId}
     Roles: ADMIN
```

**Scheduling API:**
```
GET    /api/v1/reports/schedules
POST   /api/v1/reports/schedules
PUT    /api/v1/reports/schedules/{id}
DELETE /api/v1/reports/schedules/{id}
POST   /api/v1/reports/schedules/{id}/run-now
GET    /api/v1/reports/schedules/{id}/history
```

**Error Responses:**
```json
{
  "error": {
    "code": "REPORT_VALIDATION_FAILED",
    "message": "Cannot export: GL out of balance",
    "details": {
      "imbalanceAmount": 1000.00,
      "affectedAccounts": ["111", "331"]
    }
  }
}
```

### Workflows and Sequencing

**Trial Balance Generation Flow:**
```
User Request → Validate Period (open/closed)
    → Check GL Balance (Σ Dr = Σ Cr)
    → Query Posted Voucher Lines by Period
    → Group by Account, Calculate:
        - Opening = Prior Period Closing
        - Period = SUM(Dr) - SUM(Cr) for period
        - Closing = Opening + Period
    → Apply Account Hierarchy (roll-up parents)
    → Generate Snapshot Hash
    → Cache Result (Redis, TTL=1hr)
    → Log Audit Event
    → Return TrialBalanceDTO
```

**Statutory Report Generation Flow:**
```
User Request → Load Mapping Configuration (versioned)
    → Validate Period Status
    → For Each Mapping Line:
        - Parse Account Pattern (ranges, wildcards)
        - Query GL Balances for Matching Accounts
        - Apply Operator (SUM/DIFF/ABS) and Sign
        - Calculate Line Value
    → Build Hierarchical Structure
    → If Comparison Period:
        - Repeat for Prior Period
        - Calculate Variance (abs & %)
    → Validate: No NULL Required Lines
    → Generate Snapshot + Hash
    → Return StatutoryReportDTO
```

**Export Flow:**
```
Export Request → Load Cached/Generate Report
    → Validate Export Permission (RBAC)
    → Generate File:
        - PDF: Apply TT200 Template, Vietnamese Fonts
        - Excel: Header Sheet + Data + Formulas
        - CSV: UTF-8 BOM, Vietnamese headers
    → Calculate File Hash (SHA-256)
    → Upload to Supabase Storage
    → Generate Signed URL (7-day expiry)
    → Create Manifest (files, hashes, params)
    → Log Export Audit Event
    → Return Download URLs
```

**Drill-Down Flow:**
```
Drill Request (Line Code) → Parse Mapping → Get Account List
    → For Each Account:
        - Query Voucher Lines (period, account)
        - Group by Voucher
        - Calculate Contribution Amount
    → Return Paginated Account List
    
Drill Request (Account) → Query Voucher Lines
    → Return Paginated Voucher List with:
        - Voucher Number, Date, Description
        - Dr/Cr Amount for this Account
        - Link to Full Voucher Detail
        
Drill Request (Voucher) → Load Full Voucher
    → Include: Lines, Attachments (signed URLs), Audit History
    → Return VoucherDetailDTO
```

**Schedule Execution Flow:**
```
Scheduler Trigger (Cron) → Load Schedule Config
    → Resolve Period (LAST_CLOSED rule)
    → Generate Report
    → Export to All Formats
    → For Each Recipient:
        - Validate Access Permission
        - Generate Email with Signed Links
        - Apply Password Protection (if configured)
    → Send Emails (with retry/backoff)
    → Log Execution Result
    → Update next_run_at
```

## Non-Functional Requirements

### Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Trial Balance render** | ≤2s for ≤50k GL rows | P95 response time |
| **Statutory report render** | ≤3s for typical COA (~500 accounts) | P95 response time |
| **Drill-down query** | ≤500ms per level | P95 response time |
| **PDF export generation** | ≤5s for ≤100 pages | Time to download URL |
| **Excel export generation** | ≤3s for ≤10k rows | Time to download URL |
| **Large report threshold** | >50k GL rows triggers streaming | Background job with notification |
| **Cache hit ratio** | ≥80% for repeated queries | Redis metrics |
| **Concurrent report generation** | 10 simultaneous users | Load test baseline |

**Optimization Strategies:**
- Redis caching for generated reports (TTL: 1 hour, invalidate on GL post)
- Database indexes on `voucher_lines(account_id, period_id, company_id)`
- Materialized views for period balances (refresh on period close)
- Streaming export for large datasets (>50k rows)
- Connection pooling via HikariCP (max 20 connections per instance)

### Security

| Requirement | Implementation |
|-------------|----------------|
| **RBAC enforcement** | Spring Security `@PreAuthorize` on all report endpoints; CFO/Chief Accountant/Auditor roles |
| **Multi-tenancy** | All queries filtered by `company_id` via `CompanyContext`; no cross-tenant data leakage |
| **Export URL security** | Signed URLs with 7-day expiry; SHA-256 HMAC signature validation |
| **Attachment access** | Signed URLs for drill-down attachments; audit logged with IP/user-agent |
| **Unauthorized access** | 403 response + security alert + audit log entry |
| **Data masking** | Sensitive fields (tax codes) masked for non-authorized roles in exports |
| **SQL injection** | Parameterized queries only; no dynamic SQL construction |
| **Audit immutability** | `report_audit_logs` table with no UPDATE/DELETE permissions |

**Access Matrix:**

| Report | CFO | Chief Accountant | Accountant | Auditor |
|--------|-----|------------------|------------|--------|
| Trial Balance (S06-DN) | View/Export | View/Export/Drill | View | View/Export |
| Balance Sheet (B01-DN) | View/Export | View/Export | - | View/Export |
| Income Statement (B02-DN) | View/Export | View/Export | - | View/Export |
| Cash Flow (B03-DN) | View/Export | View/Export | - | View |
| Detailed Ledger (F01) | - | View/Export | - | View/Export |
| Mapping Management | - | View | - | View History |
| Schedule Management | Create/Edit | Create/Edit | - | View |

### Reliability/Availability

| Requirement | Implementation |
|-------------|----------------|
| **Report generation idempotency** | Same params + period → same snapshot hash; deduplication key: `{reportType}_{periodId}_{mappingVersion}` |
| **Export retry** | 3 retries with exponential backoff (1s, 2s, 4s) for storage uploads |
| **Schedule failure handling** | Retry policy (3 attempts); failure notification to schedule owner; status logged |
| **Email delivery** | Bounce/failure handling with retry; fallback notification in-app |
| **Snapshot durability** | Final snapshots (closed periods) backed up to secondary storage |
| **Graceful degradation** | If Redis unavailable, generate fresh (slower); log warning |
| **Data consistency** | Reports generated from consistent snapshot; no mid-generation GL changes affect result |

**Recovery Procedures:**
- **Corrupted snapshot**: Regenerate from GL data + mapping version; verify hash matches original
- **Failed schedule**: Manual rerun via API; audit trail preserved
- **Storage failure**: Queue export for retry; notify user of delay

### Observability

| Signal | Implementation |
|--------|----------------|
| **Structured logging** | JSON format with `requestId`, `userId`, `companyId`, `reportType`, `duration` |
| **Slow query logging** | Queries >1s logged with full SQL and params (masked) |
| **Report generation metrics** | `report_generation_duration_seconds` histogram by report type |
| **Cache metrics** | Hit/miss ratio, eviction count per report type |
| **Export metrics** | `report_export_count` counter by format; `report_export_size_bytes` histogram |
| **Schedule metrics** | Success/failure count; average execution duration |
| **Audit trail** | All view/export/drill-down actions logged with IP, user-agent, result |

**Alerting Thresholds:**
- Report generation >10s: Warning
- Report generation failure: Error + notification
- Cache miss ratio >50%: Warning
- Schedule failure: Error + owner notification
- Unauthorized access attempt: Security alert

**Dashboard Panels (Future):**
- Report usage by type/period
- Export volume and formats
- Schedule execution timeline
- Drill-down depth analysis

## Dependencies and Integrations

### Internal Dependencies (Prerequisites)

| Dependency | Source | Required For |
|------------|--------|--------------|
| **Voucher Engine** | Epic 3 | GL data source for all reports |
| **Chart of Accounts** | Epic 2 | Account hierarchy, TT200 codes |
| **Accounting Periods** | Epic 1 | Period selection, open/closed status |
| **User/RBAC System** | Epic 1 | Authentication, role-based access |
| **Audit Trail** | Epic 1 | Logging infrastructure |
| **Cash & Bank Module** | Epic 6 | Cash flow report data |

### External Dependencies (Backend)

| Library | Version | Purpose |
|---------|---------|---------|
| **Spring Boot** | 3.5.7 | Core framework |
| **Spring Data JPA** | 3.5.x | Database access |
| **Spring Data Redis** | 3.5.x | Report caching |
| **Spring Security** | 6.x | RBAC enforcement |
| **PostgreSQL Driver** | 42.7.4 | Database connectivity |
| **Flyway** | 11.10.0 | Schema migrations |
| **Apache POI** | 5.2.x | Excel export (NEW) |
| **iText/OpenPDF** | 2.0.x | PDF generation (NEW) |
| **Commons CSV** | 1.11.0 | CSV export (existing) |

### External Dependencies (Frontend)

| Library | Version | Purpose |
|---------|---------|---------|
| **React** | 19.1.x | UI framework |
| **TanStack Table** | 8.21.x | Report data grids |
| **TanStack Query** | 5.62.x | Data fetching, caching |
| **react-pdf** | 10.2.x | PDF preview (existing) |
| **date-fns** | 4.1.x | Date formatting |
| **Lucide React** | 0.552.x | Icons |
| **shadcn/ui** | latest | UI components |

### Integration Points

| System | Integration Type | Purpose |
|--------|------------------|---------|
| **Supabase Storage** | REST API | Export file storage, signed URLs |
| **Redis** | Spring Cache | Report snapshot caching |
| **Email Service** | SMTP/Maildev | Schedule distribution |
| **n8n** | Webhook | Cache invalidation trigger on GL post |

### New Libraries to Add

**Backend (pom.xml):**
```xml
<!-- PDF Generation -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>

<!-- Excel Generation -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- Scheduling -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

**Frontend (package.json):**
```json
{
  "xlsx": "^0.18.5",           // Excel client-side preview
  "recharts": "^2.12.x"        // Report charts (optional)
}
```

## Acceptance Criteria (Authoritative)

### Story 7.1: Trial Balance (S06-DN)

| AC# | Criterion | Testable Statement |
|-----|-----------|--------------------|
| AC7.1.1 | Period selector persists last-used period per user | Given a user selects period "T10/2025", when they return to Trial Balance, then "T10/2025" is pre-selected |
| AC7.1.2 | Columns per S06-DN format | Report displays: Account Code, Account Name, Opening Dr/Cr, Period Dr/Cr, Closing Dr/Cr |
| AC7.1.3 | Balance validation | Total Dr = Total Cr at report level; validation error blocks export if imbalanced |
| AC7.1.4 | Data from posted GL only | Only vouchers with status='POSTED' included; draft toggle (admin only) adds banner |
| AC7.1.5 | Drill-down from amount | Clicking any amount opens paginated voucher list filtered by account/period |
| AC7.1.6 | PDF export TT200 layout | PDF includes logo, footer, timestamp, hash; matches TT200 template |
| AC7.1.7 | Excel export with metadata | Excel has header sheet with filters, user, timestamp, data hash |
| AC7.1.8 | Performance ≤2s | Render time ≤2s for ≤50k GL rows (P95) |
| AC7.1.9 | RBAC enforced | Unauthorized users receive 403; access logged |
| AC7.1.10 | Snapshot reproducibility | Same params regenerate identical hash; mapping version stored |

### Story 7.2: Statutory Reports (B01-DN, B02-DN, B03-DN, F01)

| AC# | Criterion | Testable Statement |
|-----|-----------|--------------------|
| AC7.2.1 | TT200 template compliance | Reports match official TT200 formats; headers include company legal details |
| AC7.2.2 | Mapping tables versioned | Each mapping edit creates new version; audit record with diff and reason |
| AC7.2.3 | Line tooltips/explainers | Each line shows contributing accounts on hover; drill-down available |
| AC7.2.4 | Period comparison mode | Current vs prior period with absolute and % variance columns |
| AC7.2.5 | Validation blocks export | Export blocked if mapping yields NULL or GL imbalanced; error panel shows fixes |
| AC7.2.6 | DRAFT watermark | Open periods show DRAFT watermark on PDF/screen |
| AC7.2.7 | Mapping rollback | Admin can rollback to prior mapping version; affected snapshots rerunnable |
| AC7.2.8 | F01 subsidiary detail | F01 supports customer/supplier/item subsidiary selection per account |

### Story 7.3: Scheduling & Distribution

| AC# | Criterion | Testable Statement |
|-----|-----------|--------------------|
| AC7.3.1 | Schedule CRUD | Users can create/edit/cancel schedules with cron expression and recipients |
| AC7.3.2 | Access control | Only users with report permission can create schedules; recipients validated |
| AC7.3.3 | Signed email links | Emails contain signed URLs expiring in ≤7 days |
| AC7.3.4 | Report Center | Lists upcoming/historical runs with status, duration, error logs |
| AC7.3.5 | Idempotency | Same schedule+period produces single snapshot (no duplicates) |
| AC7.3.6 | Failure notification | Failed schedules notify owner with error details |

### Story 7.4: Multi-Period Comparison

| AC# | Criterion | Testable Statement |
|-----|-----------|--------------------|
| AC7.4.1 | 3-12 period comparison | User can select 3-12 periods for comparison view |
| AC7.4.2 | Variance with thresholds | Variance columns show abs/%; configurable materiality threshold |
| AC7.4.3 | Drill from comparison | Clicking variance drills to base report filtered by period |
| AC7.4.4 | Top drivers | System identifies and lists top N variance contributors |

### Story 7.5: Audit & Compliance

| AC# | Criterion | Testable Statement |
|-----|-----------|--------------------|
| AC7.5.1 | Audit timeline | All view/export/drill actions logged with user, IP, timestamp, hash |
| AC7.5.2 | File integrity | Exports include footer hash; manifest JSON with file hashes |
| AC7.5.3 | Legal hold | Snapshots can be marked on-hold; hold prevents purge/alteration |
| AC7.5.4 | Unauthorized alerts | Blocked access attempts trigger security alert |

### Story 7.6: F01 Detailed Ledger

| AC# | Criterion | Testable Statement |
|-----|-----------|--------------------|
| AC7.6.1 | TT200/BTC format | F01 matches official template with headers, footers, signature fields |
| AC7.6.2 | Multi-account selection | User can select multiple accounts for batch export |
| AC7.6.3 | Running balance | Each transaction line shows running balance |
| AC7.6.4 | Drill to voucher | Transaction lines link to full voucher with audit history |
| AC7.6.5 | RBAC per account | Only authorized roles can view specific account ledgers |

## Traceability Mapping

| AC# | PRD Requirement | Spec Section | Component/API | Test Approach |
|-----|-----------------|--------------|---------------|---------------|
| AC7.1.1 | FR16 | Detailed Design > Workflows | `TrialBalanceService`, User Preferences | Unit test: preference persistence |
| AC7.1.2 | FR16 | Data Models > TrialBalanceDTO | `TrialBalanceLineDTO` | Integration test: column validation |
| AC7.1.3 | NFR11 | Workflows > Validation | `TrialBalanceService.validate()` | Unit test: Dr=Cr assertion |
| AC7.1.4 | FR16 | Data Models | `VoucherRepository` query | Integration test: status filter |
| AC7.1.5 | FR36 | APIs > Drill-Down | `/api/v1/reports/trial-balance/drill-down` | E2E test: drill navigation |
| AC7.1.6 | FR16 | Dependencies > OpenPDF | `ReportExportService.exportPdf()` | Visual regression test |
| AC7.1.7 | FR16 | Dependencies > Apache POI | `ReportExportService.exportExcel()` | Integration test: header sheet |
| AC7.1.8 | NFR1 | NFR > Performance | Redis cache, DB indexes | Load test: 50k rows |
| AC7.1.9 | NFR5, FR47 | NFR > Security | Spring Security `@PreAuthorize` | Security test: 403 response |
| AC7.1.10 | FR16 | Data Models > report_snapshots | `ReportSnapshotService` | Unit test: hash determinism |
| AC7.2.1 | FR32-35 | Detailed Design > Services | `StatutoryReportService` | Visual regression test |
| AC7.2.2 | FR32-35 | Data Models > report_mappings | `ReportMappingService` | Integration test: versioning |
| AC7.2.3 | FR36 | APIs > Drill-Down | `ReportLineDTO.contributingAccounts` | E2E test: tooltip render |
| AC7.2.4 | FR32-35 | APIs > Statutory | `comparisonPeriodId` param | Integration test: variance calc |
| AC7.2.5 | FR32-35 | Workflows > Validation | `StatutoryReportService.validate()` | Unit test: NULL check |
| AC7.3.1 | FR37 (implied) | APIs > Scheduling | `ReportSchedulerService` | Integration test: CRUD |
| AC7.3.3 | NFR7 | NFR > Security | Supabase signed URLs | Security test: URL expiry |
| AC7.5.1 | FR48 | Data Models > report_audit_logs | `ReportAuditService` | Integration test: log entries |
| AC7.6.1 | FR35 | Detailed Design | `DetailedLedgerDTO` | Visual regression test |

## Risks, Assumptions, Open Questions

### Risks

| ID | Risk | Impact | Probability | Mitigation |
|----|------|--------|-------------|------------|
| R1 | **TT200 mapping complexity** - Account-to-line mappings may not cover all edge cases | High | Medium | Start with standard TT200 template; allow admin customization; validate against sample data |
| R2 | **Performance degradation** - Large GL datasets (>100k rows) may exceed 2s target | Medium | Medium | Implement materialized views; streaming exports; progressive loading |
| R3 | **PDF layout inconsistency** - Vietnamese fonts may render differently across systems | Medium | Low | Use embedded fonts (OpenPDF); visual regression tests; browser-based preview |
| R4 | **Cache invalidation race conditions** - Concurrent GL posts may cause stale reports | Medium | Low | Use Redis distributed locks; timestamp-based cache keys; explicit invalidation on post |
| R5 | **Email delivery failures** - Scheduled reports may not reach recipients | Low | Medium | Implement retry with backoff; fallback to in-app notification; bounce handling |
| R6 | **Snapshot storage growth** - Historical snapshots may consume significant storage | Low | High | Implement retention policies; archive closed periods to cold storage; compress JSONB |

### Assumptions

| ID | Assumption | Validation |
|----|------------|------------|
| A1 | GL data is balanced (Σ Dr = Σ Cr) for all posted vouchers | Enforced by Epic 3 posting validation |
| A2 | Chart of Accounts follows TT200 structure with standard codes | Preloaded TT200 COA in Epic 2 |
| A3 | Accounting periods exist and have clear open/closed status | Epic 1 period management |
| A4 | Users have appropriate roles assigned for report access | RBAC system from Epic 1 |
| A5 | Redis is available and configured for caching | Infrastructure requirement |
| A6 | Supabase Storage is accessible for export file storage | Infrastructure requirement |
| A7 | Email service (SMTP) is configured for schedule distribution | Can use Maildev for dev/test |

### Open Questions

| ID | Question | Owner | Due Date | Resolution |
|----|----------|-------|----------|------------|
| Q1 | Should B03 Cash Flow use direct or indirect method? | Product | Before Story 7.2 | PRD specifies direct method |
| Q2 | What is the retention period for report snapshots? | Compliance | Before Story 7.5 | Default 10 years per TT200 |
| Q3 | Should mapping edits require dual approval (maker-checker)? | Product | Before Story 7.2 | TBD - start with single admin |
| Q4 | What email provider for production schedule distribution? | DevOps | Before Story 7.3 | TBD - evaluate SendGrid vs SES |
| Q5 | Should F01 support custom date ranges or only full periods? | Product | Before Story 7.6 | TBD - MVP: full periods only |

## Test Strategy Summary

### Test Levels

| Level | Scope | Tools | Coverage Target |
|-------|-------|-------|----------------|
| **Unit Tests** | Services, DTOs, Validators | JUnit 5, Mockito | 80% for business logic |
| **Integration Tests** | Repository queries, API endpoints | TestContainers, Spring Test | 70% for data layer |
| **E2E Tests** | Critical user flows (generate, export, drill) | Playwright | Top 5 scenarios |
| **Visual Regression** | PDF/Excel layout compliance | Percy or manual comparison | All report types |
| **Performance Tests** | Response time, throughput | JMeter, Gatling | P95 targets |
| **Security Tests** | RBAC, unauthorized access | Spring Security Test | All endpoints |

### Test Scenarios by Story

**Story 7.1 (Trial Balance):**
- Generate TB for period with 1k, 10k, 50k GL rows
- Verify Dr=Cr totals match
- Drill-down navigation: TB → Account → Voucher → Attachment
- Export PDF/Excel with hash verification
- Period persistence across sessions
- Unauthorized access returns 403

**Story 7.2 (Statutory Reports):**
- Generate B01/B02/B03 for sample period
- Verify line values against manual calculation
- Comparison mode with variance calculation
- Mapping edit creates new version
- DRAFT watermark for open periods
- NULL mapping line blocks export

**Story 7.3 (Scheduling):**
- Create/edit/delete schedule
- Execute scheduled job
- Email delivery with signed links
- Retry on failure
- Report Center displays history

**Story 7.4 (Multi-Period):**
- 3-period and 12-period comparisons
- Variance calculation accuracy
- Drill from variance to base report

**Story 7.5 (Audit):**
- All actions logged with required fields
- Export manifest hash verification
- Legal hold prevents modification

**Story 7.6 (F01):**
- Generate F01 for single and multiple accounts
- Running balance calculation
- Subsidiary filtering (customer, supplier)
- TT200 format compliance

### Test Data Requirements

- **Seed data**: 500 accounts, 10k vouchers, 50k voucher lines across 12 periods
- **Edge cases**: Zero-balance accounts, negative balances, closed periods
- **Multi-tenant**: 2+ companies to verify isolation

### Automation Priority

1. **P0 (Must)**: Balance validation, RBAC, export hash
2. **P1 (Should)**: Drill-down flows, scheduling, caching
3. **P2 (Could)**: Visual regression, performance benchmarks
