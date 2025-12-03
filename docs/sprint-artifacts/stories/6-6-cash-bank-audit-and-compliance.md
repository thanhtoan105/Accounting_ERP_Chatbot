# Story 6.6: Cash & Bank Audit and Compliance

Status: done

## Story

As a chief accountant/auditor,
I want strong controls and audit trails over cash/bank activities,
so that compliance and investigations are fully supported.

[Source: docs/epics/epic-6-cash-bank-management.md#story-66-cash-bank-audit-and-compliance]
[Source: docs/sprint-artifacts/tech-spec-epic-6.md#story-66-cash-bank-audit-and-compliance]

## Requirements Context Summary

**Business Requirements:**
- This story delivers the compliance and audit control layer for the Cash & Bank Management module.
- Primary users are chief accountants requiring closed period protection and auditors needing comprehensive audit trails.
- Must support:
  - Closed period protection: block edits/deletes with clear error messages and audit logging
  - Scheduled backup/export: weekly compressed export of accounts, books, reconciliations, audit logs
  - Integrity checks: daily and period-close verification of Dr/Cr parity and anomaly detection
  - Export controls: PDF/Excel with hash, signature block, DRAFT watermark when period open
  - Audit explorer: filter by account/date/action/user with JSON/CSV/PDF export
  - Notification center integration: alerts for suspicious patterns
  - Compliance versioning: log policy/rule versions active at time of action

**Technical Context from Tech Spec (Epic 6):**
- **New Services Required:**
  - `CashAuditService`: Cash-specific audit queries, integrity checks, export controls
  - `IntegrityCheckService`: Dr/Cr parity verification, anomaly detection, scheduled checks
  - `ComplianceExportService`: Scheduled backups, watermarked exports, hashed documents
- **Key Integration Points:**
  - Existing `AuditService` for centralized audit logging
  - Existing `PeriodManagementService` for period closure detection
  - Existing `BankReconciliationService` for reconciliation data
  - Existing `CashBookService` for book data export
- **Data & Multi-tenancy:**
  - All queries scoped by `CompanyContext.getCompanyId()`
  - Audit log retention: 10 years per compliance requirements
  - Dual approval required for purge operations

**Dependencies:**
- **Prerequisites:**
  - Story 6.1: Cash/Bank Account Management (bank account data)
  - Story 6.2: Cash Receipt Entry & Posting (receipt transactions)
  - Story 6.3: Cash Payment Entry & Posting (payment transactions)
  - Story 6.4: Bank/Cash Book View (cash book data)
  - Story 6.5: Manual Bank Reconciliation (reconciliation data)
- **Reused Components:**
  - `AuditService` from Core for audit logging (already enhanced with `logReconciliationOperation()` in Story 6.5)
  - `PeriodManagementService` from Epic 3 for period status checks
  - `BankReconciliationService` from Story 6.5 for reconciliation export
  - `CashBookService` from Story 6.4 for cash book export
  - `BankAccountService` from Story 6.1 for account export
  - Apache POI for Excel export
  - OpenPDF/iText for PDF generation with watermarks
  - Jackson for JSON serialization

---

## 🔒 Multi-Tenancy Security Requirements (MANDATORY)

**All new services MUST enforce company scoping:**

1. **Service Layer:** Every method MUST call `CompanyContext.getCompanyId()` and include it in all database queries
2. **Repository Queries:** All queries MUST include `WHERE company_id = :companyId` filter
3. **Scheduled Jobs:** MUST iterate over companies and set `CompanyContext.setCompanyId()` before processing each
4. **Cross-Company Validation:** NEVER allow queries without company context; throw `ResponseStatusException(400)` if missing

**Reference Implementation:** [CompanyScopeAspect.java](backend/src/main/java/com/accounting/security/CompanyScopeAspect.java)

**Test Requirement:** At least one integration test MUST verify that Company A cannot access Company B's audit data.

---

## 📅 Period Validation Requirements (MANDATORY)

**All compliance reports MUST indicate period status:**

1. **Report Headers:** Display period status badge (OPEN/CLOSED) on all generated reports
2. **Open Period Warning:** Show warning toast when generating compliance reports for open periods: "Warning: This report includes data from open period {periodName}. Data may change."
3. **Period Filter:** All audit queries with date range MUST validate dates against `PeriodManagementService.findPeriodByDate()`
4. **Blocked Operations:** Any modification attempt on closed period data MUST log `PERIOD_BLOCK_ATTEMPT` before throwing error

**Reference Implementation:** [PeriodManagementService.java](backend/src/main/java/com/accounting/service/PeriodManagementService.java)

---

## Anti-Pattern Prevention

**DO NOT:**
- Create new audit tables; **MUST use existing `AuditLog` table** with new action types
- Implement custom period validation; **MUST call `PeriodManagementService.isPeriodOpen(periodId)`** consistently
- Store backups locally; **MUST use external storage** (Supabase Storage) with signed URLs
- Skip hash calculation on exports; **MUST include SHA256 hash in all exported documents**
- Allow purge without dual approval; **MUST enforce `purgeRequester != purgeApprover`**
- Create blocking checks without audit logging; **EVERY blocked attempt MUST produce audit entry**
- Hardcode policy versions; **MUST capture version from `CompliancePolicy` entity at time of action**
- Query without company context; **EVERY database query MUST include company_id filter**

---

## Structure Alignment and Lessons Learned

### Learnings from Previous Story (6-5)

From **Story 6.5: Manual Bank Reconciliation**:

- **Service Pattern:**
  - Story 6.5 created service interfaces in `com/accounting/service/` with implementations in `com/accounting/service/impl/reconciliation/`
  - Follow same pattern: `CashAuditService`, `IntegrityCheckService` interfaces + `impl/audit/` implementations

- **Audit Logging Pattern:**
  - Story 6.5 added `logReconciliationOperation()` to AuditService
  - Extend with `logAuditExplorerQuery()`, `logIntegrityCheck()`, `logBackupExport()`

- **Testing Pattern:**
  - Unit tests: JUnit 5 + Mockito, `@Nested` class organization, `@DisplayName` annotations
  - Test execution: `mvn test -Dtest="**/audit/*Test"`

- **Controller Pattern:**
  - REST endpoints at `/api/v1/...` with `@PreAuthorize` annotations
  - Standard response wrapper: `{ data, meta, error }`

- **Frontend Pattern:**
  - Pages in `features/accounting/pages/` with feature-specific subdirectory
  - shadcn/ui components for UI
  - React Query for data fetching
  - i18n for all user-facing text

**Files to Reference/Reuse:**
| File | Purpose | Story Task |
|------|---------|------------|
| [AuditService.java](backend/src/main/java/com/accounting/service/AuditService.java) | Audit interface - extend | Task 2 |
| [AuditServiceImpl.java](backend/src/main/java/com/accounting/service/impl/AuditServiceImpl.java) | Audit implementation | Task 2 |
| [AuditLogController.java](backend/src/main/java/com/accounting/controller/admin/AuditLogController.java) | Query/export pattern | Task 8 |
| [AuditLogExportService.java](backend/src/main/java/com/accounting/service/impl/AuditLogExportService.java) | Export with hash pattern | Task 5 |
| [PeriodManagementService.java](backend/src/main/java/com/accounting/service/PeriodManagementService.java) | Period validation | Task 6 |
| [APAuditBackupScheduler.java](backend/src/main/java/com/accounting/scheduled/APAuditBackupScheduler.java) | Scheduled job pattern | Task 4 |
| [CashBookServiceImpl.java](backend/src/main/java/com/accounting/service/impl/cashbook/CashBookServiceImpl.java) | Cash book data source | Task 5 |

---

## Acceptance Criteria

### AC6.6-01: Closed Period Protection
Closed period protection: edits/deletes blocked; attempts show clear error message with period name and produce `PERIOD_BLOCK_ATTEMPT` audit entry with reason, user context, and attempted operation details.

**Verification:**
- [ ] Attempt to edit receipt in closed period → Error: "Cannot modify transactions in closed period: {periodName} ({startDate} - {endDate})"
- [ ] Audit log entry created with: userId, attemptedOperation, entityType, entityId, periodId, timestamp, IP address
- [ ] Error displayed in UI as toast notification (not modal)

### AC6.6-02: Scheduled Backup/Export
Weekly compressed export of accounts, books, reconciliations, and audit logs to external storage (Supabase Storage); restore requires approval and is fully audited.

**Export Contents:**
- Bank accounts master data (JSON)
- Cash book entries for period (JSON)
- Reconciliation sessions and matches (JSON)
- Audit logs for period (JSON)
- Manifest file with SHA256 hashes of each file

**Verification:**
- [ ] Backup runs every Sunday at 3 AM (cron: `0 0 3 * * SUN`)
- [ ] ZIP file uploaded to Supabase Storage with signed URL (7-day expiry)
- [ ] `BACKUP_EXPORT` audit entry created with: fileSize, fileCount, uploadLocation, hash

### AC6.6-03: Integrity Checks
Daily and period-close checks verify Dr/Cr parity and detect anomalies; alerts sent to admin; results logged with detailed checklist.

**Check Types:**
| Check | Frequency | Alert Threshold |
|-------|-----------|-----------------|
| Dr/Cr parity | Daily 2 AM | Any imbalance |
| Duplicate references | Daily 2 AM | Any duplicate |
| Number sequence gaps | Daily 2 AM | >1 gap per week |
| Unusual amounts | Daily 2 AM | >3 std dev from avg |
| Repeated blocked attempts | Hourly | ≥3 per user per hour |

**Verification:**
- [ ] Daily check runs at 2 AM (cron: `0 0 2 * * *`)
- [ ] Failed check creates `INTEGRITY_CHECK_FAIL` audit entry with issue list
- [ ] Email/notification sent to admin on failure
- [ ] Manual check trigger available for admin via UI

### AC6.6-04: Export Controls
All PDF/Excel exports include SHA256 hash in footer and signature block; watermarked "DRAFT" when period is open; only admin/chief_accountant may export full book data.

**Report Header Requirements:**
```
Period: {periodName} ({startDate} - {endDate})
Status: [OPEN] or [CLOSED]  ← Badge color: Open=yellow, Closed=green
Generated: {timestamp} by {userName}
```

**Verification:**
- [ ] Open period exports show "DRAFT" watermark diagonally across each page
- [ ] Footer contains: `Document Hash (SHA-256): {hash}`
- [ ] Signature block at end: "Prepared by: ___ | Reviewed by: ___ | Approved by: ___"
- [ ] Non-admin users receive 403 when attempting full book export

### AC6.6-05: Audit Explorer
Filter audit logs by account/date/action/user/type; export to JSON/CSV/PDF; 10-year retention enforced; purge requires dual approval and documented reason.

**Filter Options:**
| Field | Type | Required |
|-------|------|----------|
| bankAccountId | Long | No |
| dateFrom | ISO-8601 date | Yes |
| dateTo | ISO-8601 date | Yes |
| actionType | enum[] | No (multi-select) |
| userId | Long | No |
| entityType | string | No |

**Constraints:**
- Date range max: 12 months
- Page size: 10, 20, 50 (default 20)
- Export limit: 10,000 records

**Verification:**
- [ ] Query returns paginated results with total count
- [ ] Export respects applied filters
- [ ] Purge request creates `PURGE_REQUEST` audit entry
- [ ] Purge approval validates `approver != requester`

### AC6.6-06: Notification Center Integration
Repeated blocked attempts or suspicious patterns trigger alerts/escalation to designated users.

**Alert Triggers:**
| Trigger | Threshold | Recipients | Escalation |
|---------|-----------|------------|------------|
| Blocked attempts | ≥3 in 1 hour | User's manager | After 5: Chief Accountant |
| Integrity failure | Any | Admin | Immediate |
| Unusual amount | >3 std dev | Chief Accountant | None |
| Purge request | Any | Admin | None |

**Verification:**
- [ ] Email sent within 5 minutes of trigger
- [ ] In-app notification badge updated
- [ ] Alert logged with `ALERT_SENT` action type

### AC6.6-07: Compliance Version Logging
Audit entries include version of policies/rules active at time of action to preserve context for future audits.

**Captured Metadata:**
- `policyVersion`: Current compliance policy version string
- `ruleSetVersion`: Current business rules version
- `applicationVersion`: Application build version

**Verification:**
- [ ] Every audit entry includes `metadata.policyVersion` field
- [ ] Policy version retrieved from `CompliancePolicy` entity (or config)

---

## API Specifications

### Audit Explorer Endpoints

#### GET /api/v1/audit/cash-bank
Query audit logs with filters.

**Request Parameters:**
| Parameter | Type | Required | Default | Validation |
|-----------|------|----------|---------|------------|
| bankAccountId | Long | No | - | Valid bank account ID |
| dateFrom | string (ISO-8601) | Yes | - | Not future, ≤ dateTo |
| dateTo | string (ISO-8601) | Yes | - | ≥ dateFrom |
| actionType | string[] | No | all | Valid enum values |
| userId | Long | No | - | Valid user ID |
| entityType | string | No | - | - |
| page | int | No | 0 | ≥ 0 |
| size | int | No | 20 | 10, 20, or 50 |

**Response:**
```json
{
  "data": [
    {
      "id": "uuid",
      "action": "RECEIPT_CREATED",
      "entityType": "CashReceipt",
      "entityId": "uuid",
      "userId": 123,
      "userEmail": "user@example.com",
      "timestamp": "2025-01-15T10:30:00Z",
      "details": { ... },
      "metadata": {
        "policyVersion": "1.2.0",
        "ipAddress": "192.168.1.1"
      }
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "total": 150,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

**Errors:**
| HTTP Code | Error Code | Message |
|-----------|------------|---------|
| 400 | INVALID_DATE_RANGE | "Date range cannot exceed 12 months" |
| 400 | MISSING_COMPANY_CONTEXT | "X-Company-Id header required" |
| 400 | INVALID_DATE_FORMAT | "dateFrom must be ISO-8601 format" |
| 403 | ACCESS_DENIED | "Requires ADMIN or CHIEF_ACCOUNTANT role" |

#### GET /api/v1/audit/cash-bank/export
Export audit logs to file.

**Additional Parameters:**
| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| format | enum | Yes | - |

**Format Options:** `JSON`, `CSV`, `PDF`

**Response Headers:**
```
Content-Type: application/json | text/csv | application/pdf
Content-Disposition: attachment; filename="audit-export-{timestamp}.{ext}"
X-Content-SHA256: {hash}
X-Record-Count: {count}
```

#### POST /api/v1/audit/cash-bank/purge/request
Request audit log purge (Chief Accountant only).

**Request Body:**
```json
{
  "dateFrom": "2020-01-01",
  "dateTo": "2020-12-31",
  "reason": "GDPR data retention compliance - customer request #12345"
}
```

**Response:**
```json
{
  "data": {
    "requestId": "uuid",
    "status": "PENDING_APPROVAL",
    "requestedBy": 123,
    "createdAt": "2025-01-15T10:30:00Z"
  }
}
```

#### POST /api/v1/audit/cash-bank/purge/{requestId}/approve
Approve purge request (Admin only, must be different user).

**Response:**
```json
{
  "data": {
    "requestId": "uuid",
    "status": "APPROVED",
    "approvedBy": 456,
    "recordsPurged": 5420,
    "completedAt": "2025-01-15T10:35:00Z"
  }
}
```

**Errors:**
| HTTP Code | Error Code | Message |
|-----------|------------|---------|
| 400 | SELF_APPROVAL_FORBIDDEN | "Approver cannot be the same as requester" |
| 404 | PURGE_REQUEST_NOT_FOUND | "Purge request not found" |
| 409 | ALREADY_PROCESSED | "Purge request already processed" |

### Integrity Check Endpoints

#### GET /api/v1/audit/cash-bank/integrity-checks
List integrity check results.

**Response:**
```json
{
  "data": [
    {
      "id": "uuid",
      "checkType": "DAILY",
      "status": "PASSED",
      "executedAt": "2025-01-15T02:00:00Z",
      "duration": 45000,
      "issueCount": 0,
      "issues": []
    }
  ],
  "meta": { "page": 0, "size": 20, "total": 30 }
}
```

#### POST /api/v1/audit/cash-bank/integrity-checks/run
Trigger manual integrity check (Admin only).

**Response:**
```json
{
  "data": {
    "checkId": "uuid",
    "status": "RUNNING",
    "startedAt": "2025-01-15T10:30:00Z"
  }
}
```

---

## Error Handling Specifications

### Standard Error Response
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "details": { ... },
    "timestamp": "2025-01-15T10:30:00Z",
    "traceId": "uuid"
  }
}
```

### Error Catalog
| Code | HTTP | Message | Resolution |
|------|------|---------|------------|
| PERIOD_CLOSED | 400 | "Cannot modify transactions in closed period: {name}" | Contact admin to reopen period |
| DATE_RANGE_EXCEEDED | 400 | "Date range cannot exceed 12 months" | Narrow date range |
| EXPORT_LIMIT_EXCEEDED | 400 | "Export limited to 10,000 records. Apply filters." | Add filters |
| MISSING_COMPANY_CONTEXT | 400 | "X-Company-Id header required" | Add header |
| ACCESS_DENIED | 403 | "Requires {role} role" | Request access |
| SELF_APPROVAL_FORBIDDEN | 400 | "Approver cannot be the same as requester" | Different user must approve |
| INTEGRITY_CHECK_RUNNING | 409 | "Integrity check already in progress" | Wait for completion |
| BACKUP_IN_PROGRESS | 409 | "Backup export already in progress" | Wait for completion |

---

## Tasks / Subtasks

### Task 1: Backend – Audit Action Enums (AC: #1, #2, #3, #5, #7)
- [x] **1.1** Add new audit action types to `AuditAction` enum:
  - `PERIOD_BLOCK_ATTEMPT`, `INTEGRITY_CHECK_PASS`, `INTEGRITY_CHECK_FAIL`
  - `BACKUP_EXPORT`, `AUDIT_EXPLORER_QUERY`, `ANOMALY_DETECTED`
  - `PURGE_REQUEST`, `PURGE_APPROVE`, `PURGE_REJECT`, `ALERT_SENT`
- [x] **1.2** Add metadata schema documentation for each action type
- [x] **1.3** Create unit test verifying all new enums serialize correctly

### Task 2: Backend – CashAuditService (AC: #1, #5, #7)
- [x] **2.1** Create interface `CashAuditService` in `com/accounting/service/`
- [x] **2.2** Create `CashAuditServiceImpl` in `com/accounting/service/impl/audit/`
- [x] **2.3** Implement `queryAuditLogs(CashAuditQueryDTO filter)`:
  - Validate company context present
  - Validate date range ≤ 12 months
  - Apply all filters with pagination
  - Log `AUDIT_EXPLORER_QUERY` action
- [x] **2.4** Implement `exportAuditLogs(CashAuditQueryDTO filter, ExportFormat format)`:
  - Reuse `AuditLogExportService` pattern
  - Add SHA-256 hash calculation
  - Limit to 10,000 records
- [x] **2.5** Implement purge workflow:
  - `requestPurge(PurgeRequestDTO)` - validate Chief Accountant role
  - `approvePurge(UUID requestId)` - validate Admin + different user
  - `rejectPurge(UUID requestId, String reason)`
- [x] **2.6** Create unit tests with ≥85% coverage

### Task 3: Backend – IntegrityCheckService (AC: #3)
- [x] **3.1** Create interface `IntegrityCheckService`
- [x] **3.2** Create `IntegrityCheckServiceImpl`
- [x] **3.3** Implement `runDailyIntegrityCheck(Long companyId)`:
  - Verify Dr/Cr parity for all cash/bank vouchers
  - Detect duplicate transaction references
  - Detect number sequence gaps
  - Return `IntegrityCheckResultDTO` with issue list
- [x] **3.4** Implement `runPeriodCloseCheck(UUID periodId)`:
  - All daily checks plus: reconciliation completion, pending adjustments
- [x] **3.5** Implement `detectAnomalies(Long companyId, LocalDate from, LocalDate to)`:
  - Unusual amounts (>3 std dev)
  - Repeated blocked attempts (≥3 per user per hour)
- [x] **3.6** Create unit tests for each check type with edge cases

### Task 4: Backend – Scheduled Jobs (AC: #2, #3)
- [x] **4.1** Create `AuditScheduler` class with `@EnableScheduling`
- [x] **4.2** Implement daily integrity check job:
  ```java
  @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
  public void runDailyIntegrityCheck()
  ```
  - Iterate all companies, set CompanyContext for each
  - Log results, send alerts on failure
- [x] **4.3** Implement weekly backup job:
  ```java
  @Scheduled(cron = "0 0 3 * * SUN") // 3 AM Sunday
  public void runWeeklyBackupExport()
  ```
  - Export, compress, upload to Supabase
  - Log `BACKUP_EXPORT` audit entry
- [x] **4.4** Implement hourly anomaly detection:
  ```java
  @Scheduled(cron = "0 0 * * * *") // Every hour
  public void checkRepeatedBlockedAttempts()
  ```
- [x] **4.5** Create unit tests for job logic (mock scheduler)

### Task 5: Backend – ComplianceExportService (AC: #4)
- [x] **5.1** Create interface `ComplianceExportService`
- [x] **5.2** Create `ComplianceExportServiceImpl`
- [x] **5.3** Implement `exportWithControls(ExportRequest request)`:
  - Check period status via `PeriodManagementService`
  - Add "DRAFT" watermark if period open
  - Add SHA-256 hash to footer
  - Add signature block template
- [x] **5.4** Implement `calculateDocumentHash(byte[] content)` using SHA-256
- [x] **5.5** Implement `addWatermark(Document doc, String text)` for PDF
- [x] **5.6** Create unit tests for watermark/hash functionality

### Task 6: Backend – Period Protection Aspect (AC: #1)
- [x] **6.1** Create `@PeriodProtected` annotation
- [x] **6.2** Create `PeriodProtectionAspect`:
  - Extract date from method parameters
  - Check period status via `PeriodManagementService`
  - Log `PERIOD_BLOCK_ATTEMPT` if closed
  - Throw `BusinessException("PERIOD_CLOSED", message)`
- [x] **6.3** Apply annotation to cash receipt/payment services
- [x] **6.4** Create integration test verifying aspect blocks correctly

### Task 7: Backend – Notification Integration (AC: #6)
- [x] **7.1** Create or extend `AuditAlertService`:
  - `sendBlockedAttemptAlert(userId, attemptCount)`
  - `sendIntegrityCheckAlert(checkId, issueCount)`
  - `sendAnomalyAlert(anomalyType, details)`
- [x] **7.2** Implement alert thresholds per AC6.6-06 table
- [x] **7.3** Log `ALERT_SENT` for each notification
- [x] **7.4** Create unit tests for alert triggering logic

### Task 8: Backend – CashAuditController (AC: #5)
- [x] **8.1** Create controller at `/api/v1/audit/cash-bank`
- [x] **8.2** Implement endpoints per API Specifications section
- [x] **8.3** Add `@PreAuthorize` annotations:
  - Query/export: `hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')`
  - Purge request: `hasRole('CHIEF_ACCOUNTANT')`
  - Purge approve: `hasRole('ADMIN')`
  - Manual integrity check: `hasRole('ADMIN')`
- [x] **8.4** Create integration tests for each endpoint

### Task 9: Backend – DTOs (AC: #1-#7)
- [x] **9.1** Create in `com/accounting/dto/audit/`:
  - `CashAuditQueryDTO` - filter criteria with validation annotations
  - `CashAuditLogDTO` - single audit entry
  - `CashAuditPageDTO` - paginated results
  - `IntegrityCheckResultDTO` - check results
  - `IntegrityIssueDTO` - single issue
  - `PurgeRequestDTO` - purge request
  - `PurgeResponseDTO` - purge result
  - `AnomalyDTO` - anomaly details
- [x] **9.2** Add Jakarta validation annotations
- [x] **9.3** Create unit tests for DTO validation

### Task 10: Frontend – API Service and Types
- [x] **10.1** Create `frontend/src/features/accounting/services/cashAudit.ts`
- [x] **10.2** Add TypeScript interfaces mirroring backend DTOs
- [x] **10.3** Implement API functions:
  - `queryAuditLogs(filter: CashAuditQueryDTO)`
  - `exportAuditLogs(filter: CashAuditQueryDTO, format: ExportFormat)`
  - `requestPurge(request: PurgeRequestDTO)`
  - `approvePurge(requestId: string)`
  - `getIntegrityChecks()`
  - `triggerIntegrityCheck()`
- [x] **10.4** Add React Query hooks for each API

### Task 11: Frontend – i18n Translation Keys
- [x] **11.1** Add keys to `en/common.json`:
  ```json
  {
    "audit": {
      "title": "Cash & Bank Audit",
      "explorer": { "title": "Audit Explorer", "filters": { ... } },
      "integrity": { "title": "Integrity Checks", "status": { ... } },
      "actions": { "periodBlock": "Period Block Attempt", ... },
      "export": { "json": "Export JSON", ... },
      "purge": { "request": "Request Purge", ... },
      "errors": { "dateRangeExceeded": "Date range cannot exceed 12 months", ... }
    }
  }
  ```
- [x] **11.2** Add Vietnamese translations to `vi/common.json`

### Task 12: Frontend – AuditExplorerPage (AC: #5)
- [x] **12.1** Create `frontend/src/features/accounting/pages/Audit/AuditExplorerPage.tsx`
- [x] **12.2** Implement filter panel:
  - Date range picker (max 12 months validation)
  - Bank account selector
  - Action type multi-select
  - User selector
- [x] **12.3** Implement data table:
  - Columns: Timestamp, Action, User, Entity, Details
  - Row click expands details JSON
  - Color coding: errors=red, blocked=orange, success=green
- [x] **12.4** Implement export buttons (JSON/CSV/PDF)
- [x] **12.5** Implement purge request dialog (Chief Accountant only)
- [x] **12.6** Use all i18n keys for text

### Task 13: Frontend – IntegrityDashboardPage (AC: #3)
- [x] **13.1** Create `IntegrityDashboardPage.tsx`
- [x] **13.2** Display last check status card with badge
- [x] **13.3** Display issue list with severity indicators
- [x] **13.4** Add "Run Check Now" button (Admin only)
- [x] **13.5** Display check history table

### Task 14: Frontend – Routes and Navigation
- [x] **14.1** Add routes in `AppRoutes.tsx`:
  - `/accounting/audit` → AuditExplorerPage
  - `/accounting/audit/integrity` → IntegrityDashboardPage
- [x] **14.2** Configure RBAC: `requiredRoles: ['ADMIN', 'CHIEF_ACCOUNTANT']`
- [x] **14.3** Add sidebar item under Cash & Bank section

### Task 15: Testing – Backend (AC: #1-#7)
- [x] **15.1** Unit tests for `CashAuditServiceImpl` (≥85% coverage)
- [x] **15.2** Unit tests for `IntegrityCheckServiceImpl` (≥85% coverage)
- [x] **15.3** Unit tests for `ComplianceExportServiceImpl`
- [x] **15.4** Unit tests for `PeriodProtectionAspect`
- [x] **15.5** Integration test: dual approval purge workflow
- [x] **15.6** Integration test: multi-tenant isolation (Company A ≠ Company B)
- [x] **15.7** Integration test: scheduled job logic (mock scheduler)

### Task 16: Testing – Frontend (AC: #5)
- [x] **16.1** TypeScript compilation validation
- [x] **16.2** E2E test: audit explorer query and filter
- [x] **16.3** E2E test: export download with hash verification
- [x] **16.4** E2E test: purge request workflow
- [x] **16.5** E2E test: blocked period error display

---

## Test Scenarios (Required)

### Backend Test Scenarios
| ID | Scenario | Expected Result |
|----|----------|-----------------|
| T1 | Query audit logs with 10,000+ records | Pagination works, response < 5s |
| T2 | Company A queries, Company B data exists | Only Company A data returned |
| T3 | Query date range > 12 months | 400 error with DATE_RANGE_EXCEEDED |
| T4 | Purge request by same user as approver | 400 error with SELF_APPROVAL_FORBIDDEN |
| T5 | Edit receipt in closed period | 400 error + PERIOD_BLOCK_ATTEMPT logged |
| T6 | Export PDF with open period | DRAFT watermark present |
| T7 | Export PDF with closed period | No watermark, hash in footer |
| T8 | Integrity check finds imbalance | INTEGRITY_CHECK_FAIL logged, alert sent |
| T9 | 3 blocked attempts in 1 hour | Alert sent to manager |
| T10 | Transaction >3 std dev | ANOMALY_DETECTED logged |

### Frontend Test Scenarios
| ID | Scenario | Expected Result |
|----|----------|-----------------|
| F1 | Apply date filter > 12 months | Validation error shown inline |
| F2 | Click export JSON | File downloads with correct name |
| F3 | Non-admin clicks "Run Check Now" | Button disabled/hidden |
| F4 | View blocked attempt row | Orange background, expandable details |

---

## Dev Notes

### Critical Implementation Patterns

**1. Multi-Tenant Query Pattern:**
```java
public List<CashAuditLogDTO> queryAuditLogs(CashAuditQueryDTO filter) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    return auditLogRepository.findByCompanyIdAndFilters(
        companyId,  // ALWAYS include company_id
        filter.getDateFrom(),
        filter.getDateTo(),
        filter.getActionTypes(),
        filter.getUserId(),
        PageRequest.of(filter.getPage(), filter.getSize())
    );
}
```

**2. Period Protection Check:**
```java
// Use existing PeriodManagementService - DO NOT create new validation logic
@Autowired
private PeriodManagementService periodService;

public void validatePeriodOpen(LocalDate transactionDate) {
    Optional<AccountingPeriodDTO> period = periodService.findPeriodByDate(transactionDate);

    if (period.isEmpty() || !periodService.isPeriodOpen(period.get().getId())) {
        String periodName = period.map(AccountingPeriodDTO::getName).orElse("Unknown");

        // Log blocked attempt BEFORE throwing
        auditService.logBlockedAttempt(
            AuditAction.PERIOD_BLOCK_ATTEMPT,
            "Transaction date falls in closed period",
            Map.of(
                "transactionDate", transactionDate.toString(),
                "periodId", period.map(p -> p.getId().toString()).orElse("null"),
                "periodName", periodName
            )
        );

        throw new BusinessException("PERIOD_CLOSED",
            "Cannot modify transactions in closed period: " + periodName);
    }
}
```

**3. Document Hash Calculation:**
```java
public String calculateDocumentHash(byte[] content) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
}
```

**4. Dual Approval Validation:**
```java
public PurgeResponseDTO approvePurge(UUID requestId) {
    PurgeRequest request = purgeRequestRepository.findById(requestId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purge request not found"));

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // CRITICAL: Validate different user
    if (request.getRequesterId().equals(currentUserId)) {
        throw new BusinessException("SELF_APPROVAL_FORBIDDEN",
            "Approver cannot be the same as requester");
    }

    // Execute purge
    int deletedCount = auditLogRepository.deleteByCompanyIdAndTimestampBetween(
        request.getCompanyId(),
        request.getPurgeFromDate().atStartOfDay().toInstant(ZoneOffset.UTC),
        request.getPurgeToDate().atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
    );

    // Log approval
    auditService.log(AuditAction.PURGE_APPROVE,
        Map.of("requestId", requestId, "recordsPurged", deletedCount));

    return new PurgeResponseDTO(requestId, "APPROVED", currentUserId, deletedCount);
}
```

**5. Scheduled Job with Company Context:**
```java
@Scheduled(cron = "0 0 2 * * *")
public void runDailyIntegrityCheck() {
    logger.info("Starting daily integrity check");

    List<Company> companies = companyRepository.findAllActive();
    for (Company company : companies) {
        try {
            // CRITICAL: Set company context for each iteration
            CompanyContext.setCompanyId(company.getId());

            IntegrityCheckResultDTO result = integrityCheckService.runDailyIntegrityCheck(company.getId());

            if (!result.isPassed()) {
                alertService.sendIntegrityCheckAlert(result);
            }
        } catch (Exception e) {
            logger.error("Integrity check failed for company {}", company.getId(), e);
        } finally {
            // CRITICAL: Always clear context
            CompanyContext.clear();
        }
    }
}
```

---

## Project Structure

**Backend (NEW):**
```
backend/src/main/java/com/accounting/
├── annotation/
│   └── PeriodProtected.java
├── aspect/
│   └── PeriodProtectionAspect.java
├── controller/audit/
│   └── CashAuditController.java
├── dto/audit/
│   ├── CashAuditQueryDTO.java
│   ├── CashAuditLogDTO.java
│   ├── CashAuditPageDTO.java
│   ├── IntegrityCheckResultDTO.java
│   ├── IntegrityIssueDTO.java
│   ├── PurgeRequestDTO.java
│   ├── PurgeResponseDTO.java
│   └── AnomalyDTO.java
├── scheduler/
│   └── AuditScheduler.java
└── service/
    ├── CashAuditService.java
    ├── IntegrityCheckService.java
    ├── ComplianceExportService.java
    └── impl/audit/
        ├── CashAuditServiceImpl.java
        ├── IntegrityCheckServiceImpl.java
        └── ComplianceExportServiceImpl.java
```

**Frontend (NEW):**
```
frontend/src/features/accounting/
├── pages/Audit/
│   ├── AuditExplorerPage.tsx
│   ├── IntegrityDashboardPage.tsx
│   └── index.ts
└── services/
    └── cashAudit.ts
```

---

## References

- Tech Spec – **Epic 6 Story 6.6**: `docs/sprint-artifacts/tech-spec-epic-6.md#story-66-cash-bank-audit-and-compliance`
- Epic Definition – **Epic 6**: `docs/epics/epic-6-cash-bank-management.md#story-66-cash-bank-audit-and-compliance`
- Bank Reconciliation Story (6.5): `docs/sprint-artifacts/stories/6-5-manual-bank-reconciliation.md`
- [AuditService.java](backend/src/main/java/com/accounting/service/AuditService.java)
- [AuditLogController.java](backend/src/main/java/com/accounting/controller/admin/AuditLogController.java)
- [PeriodManagementService.java](backend/src/main/java/com/accounting/service/PeriodManagementService.java)
- [CompanyScopeAspect.java](backend/src/main/java/com/accounting/security/CompanyScopeAspect.java)
- [APAuditBackupScheduler.java](backend/src/main/java/com/accounting/scheduled/APAuditBackupScheduler.java)

---

## Dev Agent Record

### Context Reference
- Nia Context ID: `f6f69a80-4a42-4f56-8916-42178b674a85` (Story 6-6 Backend Complete, Frontend API Ready)
- Beads Issue ID: `accounting-mgi`

### Agent Model Used
Claude Opus 4 (claude-opus-4.5)

### Debug Log References
- Backend unit tests: `mvn test -Dtest="**/audit/*Test"`
- E2E tests listing: `cd tests && npx playwright test --list cash-audit-explorer.spec.ts cash-audit-integrity.spec.ts`

### Completion Notes List
1. **Backend Implementation (Tasks 1-9)**: All services, scheduler, controller, DTOs, and unit tests completed
2. **Frontend Implementation (Tasks 10-14)**: API service, i18n, AuditExplorerPage, IntegrityDashboardPage, routes completed
3. **Testing (Tasks 15-16)**: Backend unit tests (17 passing in CashAuditServiceImplTest), E2E tests (21 total: 11 explorer + 10 integrity)
4. **Key Patterns Used**: Multi-tenancy via CompanyContext, @PeriodProtected aspect, dual approval for purge, SHA-256 hashing

### File List

**Backend - Annotations & Aspects:**
- `backend/src/main/java/com/accounting/annotation/PeriodProtected.java`
- `backend/src/main/java/com/accounting/aspect/PeriodProtectionAspect.java`

**Backend - Controller:**
- `backend/src/main/java/com/accounting/controller/audit/CashAuditController.java`

**Backend - DTOs:**
- `backend/src/main/java/com/accounting/dto/audit/CashAuditLogDTO.java`
- `backend/src/main/java/com/accounting/dto/audit/CashAuditPageDTO.java`
- `backend/src/main/java/com/accounting/dto/audit/CashAuditQueryDTO.java`
- `backend/src/main/java/com/accounting/dto/audit/IntegrityCheckResultDTO.java`
- `backend/src/main/java/com/accounting/dto/audit/IntegrityIssueDTO.java`
- `backend/src/main/java/com/accounting/dto/audit/PurgeRequestDTO.java`
- `backend/src/main/java/com/accounting/dto/audit/PurgeResponseDTO.java`

**Backend - Entities & Repositories:**
- `backend/src/main/java/com/accounting/entity/audit/` (AuditPurgeRequest, IntegrityCheckResult)
- `backend/src/main/java/com/accounting/repository/audit/` (repositories for entities)

**Backend - Enums:**
- `backend/src/main/java/com/accounting/enums/CashBankAuditAction.java`

**Backend - Services:**
- `backend/src/main/java/com/accounting/service/AuditAlertService.java`
- `backend/src/main/java/com/accounting/service/CashAuditService.java`
- `backend/src/main/java/com/accounting/service/ComplianceExportService.java`
- `backend/src/main/java/com/accounting/service/IntegrityCheckService.java`
- `backend/src/main/java/com/accounting/service/impl/audit/` (all implementations)

**Backend - Scheduler:**
- `backend/src/main/java/com/accounting/scheduled/CashBankAuditScheduler.java`

**Backend - Database Migrations:**
- `backend/src/main/resources/db/migration/V20251203001__create_audit_purge_requests_table.sql`
- `backend/src/main/resources/db/migration/V20251203002__create_integrity_check_results_table.sql`

**Backend - Tests:**
- `backend/src/test/java/com/accounting/enums/CashBankAuditActionTest.java`
- `backend/src/test/java/com/accounting/service/impl/audit/` (CashAuditServiceImplTest, etc.)

**Frontend - Pages:**
- `frontend/src/features/accounting/pages/Audit/AuditExplorerPage.tsx`
- `frontend/src/features/accounting/pages/Audit/IntegrityDashboardPage.tsx`
- `frontend/src/features/accounting/pages/Audit/index.ts`

**Frontend - Services:**
- `frontend/src/features/accounting/services/cashAudit.ts`

**Frontend - i18n (modified):**
- `frontend/src/i18n/locales/en/common.json` (cashAudit section)
- `frontend/src/i18n/locales/vi/common.json` (cashAudit section)

**Frontend - Routes (modified):**
- `frontend/src/routes/AppRoutes.tsx`

**E2E Tests:**
- `tests/e2e/cash-audit-explorer.spec.ts` (11 tests)
- `tests/e2e/cash-audit-integrity.spec.ts` (10 tests)

---

## Changelog

| Date       | Author    | Changes                              |
|------------|-----------|--------------------------------------|
| 2025-12-03 | SM Agent  | Initial story draft created from tech spec, epic, and 6.5 learnings |
| 2025-12-03 | SM Agent  | **REWRITE**: Added multi-tenancy security requirements, period validation, API specifications with types, error handling catalog, granular tasks, test scenarios, and reuse references |
| 2025-12-03 | Dev Agent | **IMPLEMENTATION COMPLETE**: All 16 tasks completed. Backend services, scheduler, controller, DTOs, unit tests. Frontend pages, i18n, routes. E2E tests (21 total). Story marked done and ready for code review. |
