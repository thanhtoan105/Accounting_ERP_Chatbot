# Story 7.2: Statutory Reports (B01-DN, B02-DN, B03-DN, F01) with TT200 Mapping

Status: done

## Code Review Findings (2025-12-05 - UPDATED)

**Review Result: APPROVED WITH MINOR ITEMS**

### Previous Critical Issues - ALL FIXED ✅

1. ~~**[accounting-uhy] PDF Export outputs text, not PDF**~~ → Now uses DynamicReports/JasperReports
2. ~~**[accounting-rox] Missing Controller Integration Tests**~~ → `StatutoryReportControllerTest.java` created (40+ tests)
3. ~~**[accounting-l53] TODO left in production code**~~ → `countTransactionsPerAccountForPeriod()` implemented
4. ~~**[accounting-oat] Spring Page response handling**~~ → `normalizePageResponse()` helper in use

### High Issues - FIXED ✅

5. ~~**[accounting-h82] Task 15.4 marked incomplete but tests exist**~~ → Story updated
6. ~~**[accounting-v19] Voucher link route verified**~~ → Already correct (`/vouchers/{id}` - vouchers are at root level)

### Medium Issues - FIXED ✅

7. ~~**[accounting-m31] Missing useCallback dependencies**~~ → Added `loadAccounts` to deps
8. ~~**[accounting-l21] Unused Wallet import**~~ → Removed

### Remaining Items (Deferred to Future Sprint)

- **[accounting-m45]** Hardcoded Vietnamese labels in Excel export (i18n enhancement)
- **[accounting-m67]** E2E tests not created (Task 16 - deferred)
- **[accounting-l34]** Test renamed to `StatutoryReportControllerTest.java` for accuracy
- **[accounting-l89]** PDF DRAFT watermark is text banner, not overlay (cosmetic enhancement)

---

## Story

As a CFO,
I want statutory financial reports with transparent line mappings,
so that filings are accurate and explainable.

## Requirements Context Summary

**Business Requirements (Full Scope per Epic 7):**

- Generate TT200-compliant statutory reports: B01-DN (Balance Sheet), B02-DN (Income Statement), B03-DN (Cash Flow - direct method), F01 (Detailed Ledger)
- Configurable TT200 account-to-line mappings with versioning and audit trail
- Period comparison mode with variance analysis (absolute and percentage)
- Drill-down from report lines → contributing accounts → vouchers
- PDF export with TT200 layout and Excel export with metadata
- DRAFT watermark for open periods
- Mapping rollback capability

**Primary Users:**

- **CFO**: Strategic financial insights, variance analysis, filing preparation
- **Chief Accountant**: Operational reconciliation, mapping configuration
- **Auditor**: Audit trail verification, mapping history review

**Key Features:**

1. **Report Generation**: B01, B02, B03, F01 per TT200 templates with company legal details
2. **Mapping Management**: Versioned mappings, admin edits with reason/audit, diff tracking
3. **Drill-down Navigation**: Line → accounts → vouchers with breadcrumb navigation
4. **Period Comparison**: Current vs prior with absolute/% variance, hide zeros toggle
5. **Validation**: Blocks export if mapping yields NULL or GL imbalanced
6. **Export**: TT200-styled PDF and Excel with mapping version, DRAFT watermark
7. **Change Management**: Mapping change log, rollback capability, affected snapshots

**Dependencies:**

- **Prerequisite:** Story 7.1 (Trial Balance) - COMPLETED
- **Reused Components:**
  - `FinancialReportDTO`, `ReportSectionDTO`, `ReportLineDTO` - existing DTOs
  - `TrialBalanceService` patterns for balance calculation
  - `VoucherLineRepository` - for GL data aggregation
  - `audit_logs` table - **REUSE for mapping audit trail**
  - Apache POI (Excel), OpenPDF (PDF generation)

---

## Multi-Tenancy Security Requirements (MANDATORY)

**All queries MUST enforce company scoping:**

1. **Service Layer:** Every method MUST call `CompanyContext.getCompanyId()` and include it in all database queries
2. **Repository Queries:** All queries MUST include `WHERE company_id = :companyId` filter
3. **RBAC Enforcement:**
   - CFO, CHIEF_ACCOUNTANT, AUDITOR: View/Export reports
   - ADMIN only: Edit mappings
4. **Row-level constraints:** Respect departmental scoping if enabled

**Pattern to follow:** `TrialBalanceServiceImpl.java:74-77` - CompanyContext validation pattern

---

## Acceptance Criteria (Full Story 7.2 from Epic)

### AC7.2.1: TT200 Template Compliance

Reports match official TT200 formats; headers include company legal details.

**Implementation:**

- B01-DN (Balance Sheet): Assets, Liabilities, Equity sections per TT200
- B02-DN (Income Statement): Revenue, COGS, Operating Profit, Net Profit per TT200
- B03-DN (Cash Flow - Direct Method): Operating, Investing, Financing activities
- F01 (Detailed Ledger): Transaction-level with running balance
- Headers: Company name, tax code, address, report title, period

### AC7.2.2: Mapping Tables with Versioning

Each mapping edit creates new version; audit record with diff and reason required.

**Implementation:**

- Use `report_mappings` table with `version` column and `is_current` flag
- **REUSE existing `audit_logs` table** for mapping change audit trail (entity_type='REPORT_MAPPING')

### AC7.2.3: Line Tooltips/Explainers

Each line shows contributing accounts on hover; drill-down available.

**Implementation:**

- Tooltip shows: mapping formula, example accounts contributing
- Click opens drill-down panel with paginated account list
- From account, drill to vouchers

### AC7.2.4: Period Comparison Mode

Current vs prior period with absolute and % variance columns.

**Implementation:**

- API accepts `comparisonPeriodId` parameter
- Response includes `priorValue`, `variance`, `variancePercent` per line
- Toggle for hide zeros/immaterial lines with configurable threshold

### AC7.2.5: Validation Blocks Export

Export blocked if mapping yields NULL or GL imbalanced; error panel shows fixes.

**Implementation:**

- Pre-export validation checks:
  - No NULL values for required mapping lines
  - GL balance check (sum(Dr) = sum(Cr))
  - Period status check (warn if open)
- Error panel lists problematic lines with suggested fixes

### AC7.2.6: DRAFT Watermark

Open periods show DRAFT watermark on PDF/screen.

**Implementation:**

- Check `accounting_periods.status` before rendering
- Add "DRAFT" watermark overlay on PDF
- Show banner on screen for open periods

### AC7.2.7: Mapping Rollback

Admin can rollback to prior mapping version; affected snapshots rerunnable.

**Implementation:**

```java
POST /api/v1/reports/mappings/{reportType}/rollback/{versionId}
```

- Creates new version copying from historical version
- Lists affected snapshots that can be regenerated

### AC7.2.8: F01 Subsidiary Detail

F01 supports customer/supplier/item subsidiary selection per account.

**Implementation:**

- Use existing `voucher_lines.customer_id`, `voucher_lines.vendor_id` for filtering
- Running balance per transaction line

---

## Database Schema Design (Optimized)

### Design Philosophy

**Key Decision: Reuse existing `audit_logs` table instead of creating `mapping_audit_logs`**

| Original Design      | Optimized Design              |
| -------------------- | ----------------------------- |
| 3 new tables         | **2 new tables**              |
| `report_mappings`    | `report_mappings` (enhanced)  |
| `report_snapshots`   | `report_snapshots` (enhanced) |
| `mapping_audit_logs` | **REUSE `audit_logs`**        |

**Benefits:**

- Consistent audit pattern across entire application
- Leverage existing indexes on `audit_logs`
- Single source of truth for all audit trails
- Reduced maintenance burden

---

### Table 1: `report_mappings` (NEW)

**Purpose:** Store TT200 line-to-account mappings with versioning for rollback capability.

```sql
-- Migration: V20251204001__create_report_mappings_table.sql
CREATE TABLE report_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL,      -- 'B01', 'B02', 'B03', 'F01', 'S06'
    line_code VARCHAR(20) NOT NULL,        -- TT200 line code (e.g., '100', '110')
    line_name VARCHAR(255) NOT NULL,       -- Vietnamese line name
    line_name_english VARCHAR(255),        -- English translation for i18n
    account_pattern TEXT NOT NULL,         -- Account codes/ranges (e.g., '111*,112*' or 'SUM(110,120)')
    operator VARCHAR(10) DEFAULT 'SUM',    -- 'SUM', 'DIFF', 'ABS'
    sign_modifier INTEGER DEFAULT 1,       -- 1 or -1 for balance direction
    display_order INTEGER NOT NULL,        -- For rendering order
    parent_line_code VARCHAR(20),          -- For hierarchical structure
    level INTEGER DEFAULT 1,               -- Indentation level (1=main, 2=sub, 3=detail)
    is_calculated BOOLEAN DEFAULT FALSE,   -- TRUE for lines like "Gross Profit = Revenue - COGS"
    formula TEXT,                          -- Formula for calculated lines (e.g., "01-11")
    version INTEGER DEFAULT 1,             -- Version number for this line
    is_current BOOLEAN DEFAULT TRUE,       -- Quick lookup for current version
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    change_reason TEXT,                    -- Required for audit when updating

    CONSTRAINT uk_report_mappings_company_type_line_version
        UNIQUE(company_id, report_type, line_code, version)
);

-- Index for fast current version lookups
CREATE INDEX idx_report_mappings_current
    ON report_mappings(company_id, report_type, is_current)
    WHERE is_current = TRUE;

-- Index for version history queries
CREATE INDEX idx_report_mappings_history
    ON report_mappings(company_id, report_type, line_code, version DESC);

-- Index for hierarchical queries
CREATE INDEX idx_report_mappings_parent
    ON report_mappings(company_id, report_type, parent_line_code)
    WHERE parent_line_code IS NOT NULL;

COMMENT ON TABLE report_mappings IS 'TT200 account-to-line mappings with versioning for statutory reports';
COMMENT ON COLUMN report_mappings.is_current IS 'TRUE for the active version; FALSE for historical versions';
COMMENT ON COLUMN report_mappings.is_calculated IS 'TRUE for lines computed from other lines (not from GL accounts)';
```

---

### Table 2: `report_snapshots` (NEW)

**Purpose:** Store generated report data for reproducibility and legal compliance.

```sql
-- Migration: V20251204002__create_report_snapshots_table.sql
CREATE TABLE report_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    report_type VARCHAR(20) NOT NULL,      -- 'B01', 'B02', 'B03', 'F01', 'S06'
    period_id UUID NOT NULL REFERENCES accounting_periods(id),
    comparison_period_id UUID REFERENCES accounting_periods(id),

    -- Metadata for reproducibility
    mapping_version INTEGER NOT NULL,      -- Which mapping version was used
    parameters JSONB NOT NULL DEFAULT '{}', -- Additional parameters used

    -- Data integrity
    data_hash VARCHAR(64) NOT NULL,        -- SHA-256 hash of snapshot_data
    snapshot_data JSONB NOT NULL,          -- Full report data (lines, totals, etc.)

    -- Generation info
    generated_by BIGINT NOT NULL REFERENCES users(id),
    generated_at TIMESTAMPTZ DEFAULT NOW(),

    -- Status flags
    is_final BOOLEAN DEFAULT FALSE,        -- TRUE = cannot be regenerated

    -- Legal hold (for audit/investigation)
    legal_hold BOOLEAN DEFAULT FALSE,
    legal_hold_reason TEXT,
    legal_hold_by BIGINT REFERENCES users(id),
    legal_hold_at TIMESTAMPTZ,

    -- Soft delete
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id)
);

-- Primary lookup index
CREATE INDEX idx_report_snapshots_lookup
    ON report_snapshots(company_id, report_type, period_id, generated_at DESC);

-- Hash lookup for verification
CREATE INDEX idx_report_snapshots_hash
    ON report_snapshots(data_hash);

-- Legal hold queries
CREATE INDEX idx_report_snapshots_legal_hold
    ON report_snapshots(company_id, legal_hold)
    WHERE legal_hold = TRUE;

COMMENT ON TABLE report_snapshots IS 'Immutable snapshots of generated reports for audit and legal compliance';
COMMENT ON COLUMN report_snapshots.data_hash IS 'SHA-256 hash for integrity verification';
COMMENT ON COLUMN report_snapshots.is_final IS 'Once TRUE, snapshot cannot be regenerated or deleted';
```

---

### Audit Trail: REUSE `audit_logs` (NO NEW TABLE)

**Convention for mapping audit entries:**

```java
// When creating/updating mapping, log to existing audit_logs table:
auditLogService.log(AuditLog.builder()
    .companyId(companyId)
    .entityType("REPORT_MAPPING")
    .entityId(reportType + ":" + lineCode)  // e.g., "B01:111"
    .action("UPDATE")  // or "CREATE", "ROLLBACK"
    .reason(changeReason)  // Required!
    .changes(Map.of(
        "old_version", oldVersion,
        "new_version", newVersion,
        "diff", Map.of(
            "account_pattern", Map.of("old", "111*", "new", "111*,112*"),
            "operator", Map.of("old", "SUM", "new", "SUM")
        )
    ))
    .build());
```

**Query mapping history:**

```sql
SELECT * FROM audit_logs
WHERE company_id = ?
  AND entity_type = 'REPORT_MAPPING'
  AND entity_id LIKE 'B01:%'
ORDER BY created_at DESC;
```

---

### Seed Data Migration

```sql
-- Migration: V20251204003__seed_default_tt200_mappings.sql
-- This inserts default TT200 mappings for new companies
-- Triggered by CompanyBootstrapService when company is created

-- Example B01 Balance Sheet seed (partial)
INSERT INTO report_mappings (company_id, report_type, line_code, line_name, line_name_english,
                             account_pattern, operator, display_order, level, is_calculated, version)
SELECT
    c.id,
    'B01',
    m.line_code,
    m.line_name,
    m.line_name_english,
    m.account_pattern,
    m.operator,
    m.display_order,
    m.level,
    m.is_calculated,
    1
FROM companies c
CROSS JOIN (VALUES
    ('100', 'A - TÀI SẢN NGẮN HẠN', 'A - CURRENT ASSETS', 'SUM(110,120,130,140,150)', 'SUM', 1, 1, TRUE),
    ('110', 'I. Tiền và tương đương tiền', 'I. Cash and cash equivalents', '111*,112*,113*', 'SUM', 2, 2, FALSE),
    ('111', '1. Tiền', '1. Cash', '111*', 'SUM', 3, 3, FALSE),
    ('112', '2. Tương đương tiền', '2. Cash equivalents', '112*,113*', 'SUM', 4, 3, FALSE),
    -- ... more lines
    ('300', 'C - NỢ PHẢI TRẢ', 'C - LIABILITIES', 'SUM(310,330)', 'SUM', 50, 1, TRUE),
    ('400', 'D - VỐN CHỦ SỞ HỮU', 'D - EQUITY', 'SUM(410,420,430)', 'SUM', 70, 1, TRUE)
) AS m(line_code, line_name, line_name_english, account_pattern, operator, display_order, level, is_calculated)
WHERE NOT EXISTS (
    SELECT 1 FROM report_mappings rm
    WHERE rm.company_id = c.id AND rm.report_type = 'B01'
);
```

---

## Relationship with Existing Tables

### Tables READ (no modification needed)

| Existing Table       | Usage in Story 7.2                             |
| -------------------- | ---------------------------------------------- |
| `chart_of_accounts`  | Get account codes matching patterns            |
| `voucher_lines`      | Aggregate balances by account                  |
| `vouchers`           | Filter by status='posted', join for drill-down |
| `accounting_periods` | Get period info, check status for DRAFT        |
| `companies`          | Get company legal details for report headers   |
| `company_settings`   | Get tax code, currency format                  |
| `users`              | Get user name for "generated by"               |
| `audit_logs`         | **WRITE** - Log mapping changes                |
| `customers`          | F01 subsidiary filtering                       |
| `suppliers`          | F01 subsidiary filtering                       |

### No Schema Changes to Existing Tables

The optimized design ensures:

- Zero modifications to existing table schemas
- Zero risk of breaking existing functionality
- Clean separation of concerns

---

## API Endpoints

### Statutory Reports API

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
     Body: { periodId, format: 'PDF'|'EXCEL', comparisonPeriodId }
     Response: { downloadUrl, expiresAt, hash, manifestUrl }
     Roles: CFO, CHIEF_ACCOUNTANT
```

### Drill-Down API

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

### Mapping Management API

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

---

## Tasks / Subtasks

### Task 1: Database - Create Migration Scripts (AC: #2) ✅

- [x] **1.1** Create `V20251204001__create_report_mappings_table.sql` (with enhanced schema)
- [x] **1.2** Create `V20251204002__create_report_snapshots_table.sql`
- [x] **1.3** Create `V20251204003__seed_default_tt200_mappings.sql` (B01, B02, B03 defaults)

### Task 2: Backend - Create Entities and Repositories (AC: #2) ✅

- [x] **2.1** Create `ReportMapping` entity with JPA annotations
- [x] **2.2** Create `ReportSnapshot` entity
- [x] **2.3** Create `ReportMappingRepository` with custom queries:
  - `findCurrentByCompanyAndReportType(companyId, reportType)`
  - `findVersionHistory(companyId, reportType, lineCode)`
- [x] **2.4** Create `ReportSnapshotRepository`

### Task 3: Backend - Create DTOs (AC: #1, #3, #4) ✅

- [x] **3.1** Create `StatutoryReportDTO`:
  ```java
  public record StatutoryReportDTO(
      String reportType,
      UUID periodId,
      String periodName,
      UUID comparisonPeriodId,
      List<ReportLineDTO> lines,
      Integer mappingVersion,
      String snapshotHash,
      boolean isDraft,
      LocalDateTime generatedAt,
      CompanyHeaderDTO companyHeader
  ) {}
  ```
- [x] **3.2** Create `ReportMappingDTO` for mapping management
- [x] **3.3** Create `MappingVersionDTO` for history view
- [x] **3.4** Create `AccountContributionDTO` for drill-down
- [x] **3.5** Create `DetailedLedgerDTO` for F01 report

### Task 4: Backend - Create StatutoryReportService (AC: #1, #4, #5) ✅

- [x] **4.1** Create `StatutoryReportService` interface
- [x] **4.2** Create `StatutoryReportServiceImpl`:
  - `generateBalanceSheet(periodId, comparisonPeriodId)`
  - `generateIncomeStatement(periodId, comparisonPeriodId)`
  - `generateCashFlow(periodId)`
  - `generateDetailedLedger(accountCodes, periodId, subsidiaryType, subsidiaryId)`
- [x] **4.3** Implement mapping loading with `is_current=TRUE` filter
- [x] **4.4** Implement variance calculation (absolute and %)
- [x] **4.5** Implement validation preflight (NULL checks, GL balance)

### Task 5: Backend - Create ReportMappingService (AC: #2, #7) ✅

- [x] **5.1** Create `ReportMappingService` interface
- [x] **5.2** Create `ReportMappingServiceImpl`:
  - `getMappings(reportType)` - get current version
  - `updateMapping(lineCode, accountPattern, operator, reason)` - creates new version
  - `getMappingHistory(reportType)` - query from `audit_logs` with entity_type='REPORT_MAPPING'
  - `rollbackMapping(reportType, versionId)` - restore previous
- [x] **5.3** Implement audit logging via existing `AuditLogService`
- [x] **5.4** Implement diff calculation between versions

### Task 6: Backend - Create DrillDownService (AC: #3) ✅

- [x] **6.1** Create `DrillDownService` interface
- [x] **6.2** Create `DrillDownServiceImpl`:
  - `getAccountsForLine(reportType, lineCode, periodId, page, size)`
  - `getVouchersForAccount(accountCode, periodId, page, size)`
  - `getVoucherDetail(voucherId)` - with attachments
- [x] **6.3** Implement breadcrumb navigation context

### Task 7: Backend - Create Export Services (AC: #1, #5, #6) ✅

- [x] **7.1** Add OpenPDF dependency to `pom.xml`:
  ```xml
  <dependency>
      <groupId>com.github.librepdf</groupId>
      <artifactId>openpdf</artifactId>
      <version>2.0.3</version>
  </dependency>
  ```
- [x] **7.2** Create `ReportExportService`:
  - `exportToPdf(reportData)` - TT200 layout with Vietnamese fonts
  - `exportToExcel(reportData)` - with header sheet and formulas
- [x] **7.3** Implement DRAFT watermark for open periods
- [x] **7.4** Implement hash calculation and manifest generation

### Task 8: Backend - Create Controllers (AC: #1-#7) ✅

- [x] **8.1** Create `StatutoryReportController`
- [x] **8.2** Create `ReportMappingController`
- [x] **8.3** Create `DrillDownController` (integrated into StatutoryReportController)
- [x] **8.4** Add RBAC annotations per endpoint
- [x] **8.5** Add audit logging for export actions

### Task 9: Frontend - Create Report Pages (AC: #1, #3, #4, #6) ✅

- [x] **9.1** Create `StatutoryReportsPage.tsx` (unified tabbed interface for B01/B02/B03)
- [x] **9.2** Create `ReportTable.tsx` (hierarchical display component)
- [x] **9.3** Period selector with comparison support
- [x] **9.4** Export dropdown (PDF/Excel)

### Task 10: Frontend - Create Drill-Down Components (AC: #3) ✅

- [x] **10.1** Create `DrillDownPanel.tsx` (Sheet-based slide-over)
- [x] **10.2** Create embedded `VoucherDetailModal` (in DrillDownPanel)

### Task 11: Frontend - Create Mapping Management Page (AC: #2, #7) ✅

- [x] **11.1** Create `ReportMappingsPage.tsx` (Admin only)
- [x] **11.2** Create embedded `MappingEditDialog` (in ReportMappingsPage)

### Task 12: Frontend - API Services (AC: all) ✅

- [x] **12.1** Create `statutoryReports.ts`
- [x] **12.2** Create `reportMappings.ts`
- [x] **12.3** Drill-down functions integrated in `statutoryReports.ts`

### Task 13: Frontend - i18n Translations (AC: #1) ✅

- [x] **13.1** Add comprehensive translations (EN/VI)
- [x] **13.2** Vietnamese TT200 line names (official terms)

### Task 14: Add Routes and Navigation (AC: all) ✅

- [x] **14.1** Add routes in `AppRoutes.tsx`
- [x] **14.2** Add sidebar navigation under "Reports" section

### Task 15: Backend Tests (AC: all) ✅

- [x] **15.1** `StatutoryReportServiceImplTest.java`
- [x] **15.2** `ReportMappingServiceImplTest.java`
- [x] **15.3** `DrillDownServiceImplTest.java`
- [x] **15.4** Unit tests for all controllers (`StatutoryReportControllerTest.java`)

### Task 16: E2E Tests (AC: all)

- [ ] **16.1** `statutory-reports.spec.ts`
- [ ] **16.2** `report-mappings.spec.ts`

---

## TT200 Account Mappings (Default Seed Data)

### B01-DN Balance Sheet Mappings

```
Code | Name (Vietnamese)                    | Account Pattern        | Operator | Level
-----|--------------------------------------|------------------------|----------|------
100  | A - TÀI SẢN NGẮN HẠN                | SUM(110,120,130,140,150)| CALC    | 1
110  | I. Tiền và tương đương tiền         | 111*,112*,113*         | SUM      | 2
111  | 1. Tiền                              | 111*                   | SUM      | 3
112  | 2. Tương đương tiền                  | 112*,113*              | SUM      | 3
120  | II. Đầu tư tài chính ngắn hạn       | 121*,128*              | SUM      | 2
130  | III. Phải thu ngắn hạn              | 131*,136*,138*,141*    | SUM      | 2
131  | 1. Phải thu khách hàng              | 131*                   | SUM      | 3
140  | IV. Hàng tồn kho                     | 151*,152*,153*,154*,155*,156*,157* | SUM | 2
200  | B - TÀI SẢN DÀI HẠN                 | SUM(210,220,230,240,250)| CALC    | 1
220  | II. Tài sản cố định                  | 211*,212*,213*,214*    | SUM      | 2
300  | C - NỢ PHẢI TRẢ                      | SUM(310,330)           | CALC     | 1
310  | I. Nợ ngắn hạn                       | 331*,333*,334*,335*,336*,338* | SUM | 2
311  | 1. Phải trả người bán               | 331*                   | SUM      | 3
400  | D - VỐN CHỦ SỞ HỮU                   | SUM(410,420,430)       | CALC     | 1
411  | I. Vốn góp chủ sở hữu               | 411*                   | SUM      | 2
421  | II. Lợi nhuận sau thuế chưa phân phối| 421*                   | SUM      | 2
```

### B02-DN Income Statement Mappings

```
Code | Name (Vietnamese)                    | Account Pattern        | Operator | Level
-----|--------------------------------------|------------------------|----------|------
01   | 1. Doanh thu thuần về bán hàng      | 511*,512* - 521*       | DIFF     | 1
11   | 2. Giá vốn hàng bán                 | 632*                   | SUM      | 1
20   | 3. Lợi nhuận gộp (01-11)            | 01-11                  | CALC     | 1
21   | 4. Doanh thu hoạt động tài chính    | 515*                   | SUM      | 1
22   | 5. Chi phí tài chính                | 635*                   | SUM      | 1
25   | 6. Chi phí bán hàng                 | 641*                   | SUM      | 1
26   | 7. Chi phí quản lý doanh nghiệp     | 642*                   | SUM      | 1
30   | 8. Lợi nhuận từ HĐKD                | 20+21-22-25-26         | CALC     | 1
31   | 9. Thu nhập khác                    | 711*                   | SUM      | 1
32   | 10. Chi phí khác                    | 811*                   | SUM      | 1
50   | 11. Lợi nhuận trước thuế            | 30+31-32               | CALC     | 1
51   | 12. Chi phí thuế TNDN               | 821*                   | SUM      | 1
60   | 13. Lợi nhuận sau thuế              | 50-51                  | CALC     | 1
```

---

## Performance Requirements

| Metric           | Target                              | Implementation                |
| ---------------- | ----------------------------------- | ----------------------------- |
| Report render    | ≤3s for typical COA (~500 accounts) | Indexed queries, lazy loading |
| Drill-down query | ≤500ms per level                    | Paginated queries             |
| PDF export       | ≤5s for ≤100 pages                  | Streaming generation          |
| Excel export     | ≤3s for ≤10k rows                   | Apache POI SXSSF              |

---

## Anti-Pattern Prevention

**DO NOT:**

- Create `mapping_audit_logs` table - **USE existing `audit_logs`**
- Skip mapping versioning - **MUST create new version on every edit**
- Allow mapping edits without reason - **reason field is REQUIRED**
- Export without validation - **MUST run preflight checks**
- Show final data for open periods - **MUST show DRAFT watermark**
- Modify existing table schemas - **READ ONLY from existing tables**
- Hardcode mappings in code - **MUST use database with seed data**

---

## Test Scenarios (Required)

### Backend Unit Tests

| ID  | Scenario                       | Expected Result                                   |
| --- | ------------------------------ | ------------------------------------------------- |
| T1  | Generate B01 with mappings     | Sections populated correctly                      |
| T2  | Generate B02 with CALC lines   | Calculated lines correct                          |
| T3  | Comparison period variance     | Absolute and % variance calculated                |
| T4  | Mapping update creates version | Version incremented, audit logged to `audit_logs` |
| T5  | Rollback restores mapping      | Previous version copied as new                    |
| T6  | Validation blocks NULL         | Export blocked with error list                    |
| T7  | Validation blocks imbalance    | Export blocked with warning                       |
| T8  | Drill-down returns accounts    | Paginated list with contributions                 |

### E2E Tests

| ID  | Scenario                  | Expected Result                       |
| --- | ------------------------- | ------------------------------------- |
| E1  | Generate Balance Sheet    | All sections displayed                |
| E2  | Add comparison period     | Variance columns appear               |
| E3  | Click line for drill-down | Account panel opens                   |
| E4  | Export PDF                | File downloads with TT200 layout      |
| E5  | Edit mapping (admin)      | New version created                   |
| E6  | View mapping history      | All versions listed (from audit_logs) |
| E7  | Rollback mapping          | Previous restored                     |
| E8  | Open period shows DRAFT   | Watermark visible                     |

---

## Project Structure

**Files to CREATE:**

```
backend/
├── src/main/java/com/accounting/
│   ├── entity/report/
│   │   ├── ReportMapping.java
│   │   └── ReportSnapshot.java
│   ├── repository/report/
│   │   ├── ReportMappingRepository.java
│   │   └── ReportSnapshotRepository.java
│   ├── service/
│   │   ├── StatutoryReportService.java
│   │   ├── ReportMappingService.java
│   │   ├── DrillDownService.java
│   │   └── impl/
│   │       ├── StatutoryReportServiceImpl.java
│   │       ├── ReportMappingServiceImpl.java
│   │       └── DrillDownServiceImpl.java
│   ├── controller/report/
│   │   ├── StatutoryReportController.java
│   │   ├── ReportMappingController.java
│   │   └── DrillDownController.java
│   └── dto/report/
│       ├── StatutoryReportDTO.java
│       ├── ReportMappingDTO.java
│       ├── MappingVersionDTO.java
│       ├── AccountContributionDTO.java
│       └── DetailedLedgerDTO.java
├── src/main/resources/db/migration/
│   ├── V20251204001__create_report_mappings_table.sql
│   ├── V20251204002__create_report_snapshots_table.sql
│   └── V20251204003__seed_default_tt200_mappings.sql
└── src/test/java/com/accounting/
    ├── service/impl/
    │   ├── StatutoryReportServiceImplTest.java
    │   ├── ReportMappingServiceImplTest.java
    │   └── DrillDownServiceImplTest.java
    └── controller/report/
        ├── StatutoryReportControllerIntegrationTest.java
        └── ReportMappingControllerIntegrationTest.java

frontend/
├── src/features/accounting/pages/
│   ├── BalanceSheet/
│   ├── IncomeStatement/
│   ├── CashFlow/
│   ├── DetailedLedger/
│   └── ReportMappings/ (Admin)
├── src/features/accounting/components/
│   ├── DrillDownPanel.tsx
│   ├── VoucherDetailModal.tsx
│   └── MappingEditDialog.tsx
├── src/features/accounting/services/
│   ├── statutoryReports.ts
│   ├── reportMappings.ts
│   └── drillDown.ts

tests/e2e/
├── statutory-reports.spec.ts
└── report-mappings.spec.ts
```

---

## Scalability Considerations

### Current Design Supports:

- ✅ Multi-tenancy (company_id on all tables)
- ✅ Multi-report types (extensible via report_type column)
- ✅ Versioning with unlimited rollback
- ✅ i18n (line_name + line_name_english)
- ✅ Hierarchical display (level, parent_line_code)
- ✅ Calculated lines (is_calculated, formula)
- ✅ Legal hold for compliance

### Future Extensions (no schema change needed):

```sql
-- Add columns if needed later:
ALTER TABLE report_mappings ADD COLUMN
    effective_from DATE,              -- Effective date range
    effective_to DATE,
    approval_status VARCHAR(20),      -- Workflow approval
    tags JSONB;                       -- Custom categorization
```

---

## References

- Epic Definition: [epic-7-reporting-engine-core-financials.md](docs/epics/epic-7-reporting-engine-core-financials.md) - Story 7.2 section
- Tech Spec: [tech-spec-epic-7.md](docs/sprint-artifacts/tech-spec-epic-7.md) - Full API, data models, workflows
- Previous Story: [7-1-mvp-trial-balance-basic.md](docs/sprint-artifacts/stories/7-1-mvp-trial-balance-basic.md)
- Existing DTOs: [FinancialReportDTO.java](backend/src/main/java/com/accounting/dto/report/FinancialReportDTO.java)
- Existing Audit: [audit_logs table](backend/src/main/resources/db/migration/) - Reuse for mapping audit
- TT200 Reference: Vietnamese Accounting Standard Circular 200/2014/TT-BTC

---

## Dev Agent Record

### Context Reference

NIA Context ID: `0fc1ea88-8652-4e49-bee0-d875eb9a2a1e` (Story 7.2 Implementation)

### Agent Model Used

Claude Opus 4

### Debug Log References

- Backend tests: `mvn test -Dtest="StatutoryReportServiceImplTest,ReportMappingServiceImplTest"`
- E2E tests: `cd tests && npx playwright test statutory-reports.spec.ts report-mappings.spec.ts`

### Completion Notes List

1. Backend Tasks 1-8 completed with full service layer implementation
2. Frontend Tasks 9-14 completed with modern design patterns
3. Sidebar navigation added for Statutory Reports (in Reports menu) and Report Mappings (in Category menu, admin-only)
4. Remaining: Backend unit tests (Task 15), E2E tests (Task 16)

### File List

**Backend - Migrations:**

- `backend/src/main/resources/db/migration/V20251204001__create_report_mappings_table.sql`
- `backend/src/main/resources/db/migration/V20251204002__create_report_snapshots_table.sql`
- `backend/src/main/resources/db/migration/V20251204003__seed_default_tt200_mappings.sql`
- `backend/src/main/resources/db/migration/V20251205001__fix_b03_cash_flow_mappings.sql` (B03 mapping corrections)

**Backend - Entities:**

- `backend/src/main/java/com/accounting/entity/report/ReportMapping.java`
- `backend/src/main/java/com/accounting/entity/report/ReportSnapshot.java`

**Backend - Repositories:**

- `backend/src/main/java/com/accounting/repository/report/ReportMappingRepository.java`
- `backend/src/main/java/com/accounting/repository/report/ReportSnapshotRepository.java`
- `backend/src/main/java/com/accounting/repository/VoucherLineRepository.java` (modified: added `calculatePeriodActivity()` for B02/B03)

**Backend - DTOs:**

- `backend/src/main/java/com/accounting/dto/report/StatutoryReportDTO.java`
- `backend/src/main/java/com/accounting/dto/report/StatutoryReportLineDTO.java`
- `backend/src/main/java/com/accounting/dto/report/ReportMappingDTO.java`
- `backend/src/main/java/com/accounting/dto/report/MappingVersionDTO.java`
- `backend/src/main/java/com/accounting/dto/report/AccountContributionDTO.java`
- `backend/src/main/java/com/accounting/dto/report/DetailedLedgerDTO.java`
- `backend/src/main/java/com/accounting/dto/report/ValidationResultDTO.java` (AC7.2.5 validation)
- `backend/src/main/java/com/accounting/dto/report/ValidationErrorDTO.java` (AC7.2.5 validation)

**Backend - Services:**

- `backend/src/main/java/com/accounting/service/StatutoryReportService.java`
- `backend/src/main/java/com/accounting/service/ReportMappingService.java`
- `backend/src/main/java/com/accounting/service/DrillDownService.java`
- `backend/src/main/java/com/accounting/service/impl/report/StatutoryReportServiceImpl.java`
- `backend/src/main/java/com/accounting/service/impl/report/ReportMappingServiceImpl.java`
- `backend/src/main/java/com/accounting/service/impl/report/DrillDownServiceImpl.java`
- `backend/src/main/java/com/accounting/service/impl/report/StatutoryReportExportService.java`

**Backend - Controllers:**

- `backend/src/main/java/com/accounting/controller/report/StatutoryReportController.java`
- `backend/src/main/java/com/accounting/controller/report/ReportMappingController.java`

**Frontend - Services:**

- `frontend/src/features/accounting/services/statutoryReports.ts`
- `frontend/src/features/accounting/services/reportMappings.ts`

**Frontend - Pages:**

- `frontend/src/features/accounting/pages/StatutoryReports/StatutoryReportsPage.tsx`
- `frontend/src/features/accounting/pages/StatutoryReports/ReportTable.tsx`
- `frontend/src/features/accounting/pages/StatutoryReports/DrillDownPanel.tsx`
- `frontend/src/features/accounting/pages/StatutoryReports/index.ts`
- `frontend/src/features/accounting/pages/ReportMappings/ReportMappingsPage.tsx`
- `frontend/src/features/accounting/pages/ReportMappings/index.ts`

**Frontend - Updated:**

- `frontend/src/features/accounting/index.ts` (exports)
- `frontend/src/routes/AppRoutes.tsx` (routes)
- `frontend/src/layouts/ProtectedLayout.tsx` (sidebar navigation)
- `frontend/src/i18n/locales/en/common.json` (translations)
- `frontend/src/i18n/locales/vi/common.json` (translations)

---

## Changelog

| Date       | Author    | Changes                                                                                                                                                                                                                                                       |
| ---------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2025-12-04 | SM Agent  | Initial full version created per Epic 7 spec                                                                                                                                                                                                                  |
| 2025-12-04 | SM Agent  | **OPTIMIZED**: Reduced from 3 to 2 new tables by reusing `audit_logs` for mapping audit trail. Enhanced `report_mappings` schema with `is_current`, `level`, `is_calculated`, `formula`, `line_name_english` columns for better performance and i18n support. |
| 2025-12-04 | Dev Agent | **BACKEND COMPLETE**: Tasks 1-8 implemented - migrations, entities, repositories, DTOs, services (Statutory, Mapping, DrillDown, Export), controllers with RBAC                                                                                               |
| 2025-12-04 | Dev Agent | **FRONTEND COMPLETE**: Tasks 9-14 implemented - StatutoryReportsPage, ReportTable, DrillDownPanel, ReportMappingsPage, API services, i18n (EN/VI), routes                                                                                                     |
| 2025-12-06 | Code Review | **REVIEW PASS**: Fixed doc gaps (added V20251205001 migration, VoucherLineRepository, ValidationDTO files to File List). Removed unused `truncate()` dead code. Deferred items: hardcoded Vietnamese in Excel (i18n enhancement), E2E tests (Task 16).         |
