# Story 3.5: Audit Trail for Voucher Lifecycle

Status: drafted

## Story

As an auditor or admin,
I want a cryptographically hashed, tamper-proof log of every change to vouchers,
so all edits and actions are legally defensible and reviewable.

[Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]

## Acceptance Criteria

1. Every voucher event (create/edit/post/reverse/unpost/import) generates audit log entry with: JSON snapshot (before/after), SHA-256 diff hash, user ID/role, device/IP (if available). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]
2. Mass/batch actions log aggregated entry with: voucher IDs list, action stats (success/failure counts), start/end timestamp, details summary. [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]
3. "Voucher history" view displays colored field-by-field diff (green=added, red=removed, yellow=changed) and plain English summary (e.g., "Voucher posted by John Doe on 2025-11-13"). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]
4. Full audit logs exportable as PDF (with hash watermark) or JSON; export includes all events for voucher with cryptographic hashes. [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]
5. Admin/audit dashboard displays mass actions, blocked operations, suspicious patterns (e.g., multiple reversal attempts); alerts deferred to post-MVP (logged only). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]

## Tasks / Subtasks

- [ ] Enhance AuditService for voucher lifecycle events (AC: #1, #2)
  - [ ] Review existing `AuditService` and `AuditServiceImpl` from Story 3.4
  - [ ] Add method `logVoucherEvent(Voucher voucher, String action, JsonNode beforeSnapshot, JsonNode afterSnapshot, String diffHash)` for individual voucher events
  - [ ] Implement JSON snapshot generation: serialize voucher entity to JSON (before/after states)
  - [ ] Implement SHA-256 diff hash calculation: hash the JSON diff between before/after snapshots
  - [ ] Capture user ID/role from `CompanyContext` and `SecurityContext`
  - [ ] Capture device/IP from HTTP request (extract from `HttpServletRequest` or `SecurityContext`)
  - [ ] Add method `logBatchVoucherAction(List<Long> voucherIds, String action, BatchActionStats stats, Instant startTime, Instant endTime, String summary)` for mass actions
  - [ ] Create `BatchActionStats` DTO with success/failure counts
  - [ ] Store aggregated batch entry in `AuditLog` with metadata containing voucher IDs list and stats
  - [ ] Ensure all audit log entries are immutable (append-only) and company-scoped
- [ ] Integrate audit logging into voucher lifecycle operations (AC: #1)
  - [ ] Review `VoucherService.create()` - add audit logging after successful creation
  - [ ] Review `VoucherService.update()` - capture before/after snapshots, log edit event
  - [ ] Review `VoucherPostingService.post()` - log posting event with before/after states
  - [ ] Review `VoucherPostingService.reverse()` - log reversal event (already logs in Story 3.3, enhance with diff hash)
  - [ ] Review `VoucherPostingService.unpost()` - log unposting event with before/after states
  - [ ] Review voucher import functionality (if exists) - add audit logging for import events
  - [ ] Ensure all audit logging is non-blocking (async or wrapped in try-catch to not break main flow)
  - [ ] Verify company scoping: all audit logs scoped to voucher's company
- [ ] Create VoucherHistoryService for history retrieval and diff generation (AC: #3)
  - [ ] Create `backend/src/main/java/com/accounting/service/VoucherHistoryService.java` interface
  - [ ] Create `backend/src/main/java/com/accounting/service/impl/VoucherHistoryService.java` implementation
  - [ ] Implement `getVoucherHistory(Long voucherId)` method: query `AuditLog` by `entityType='Voucher'` and `entityId=voucherId`
  - [ ] Implement `generateFieldDiff(JsonNode before, JsonNode after)` method: compare JSON snapshots field-by-field
  - [ ] Return diff structure: `{ field: { before: value, after: value, changeType: 'added'|'removed'|'changed' } }`
  - [ ] Implement `generatePlainEnglishSummary(AuditLog auditLog, JsonNode diff)` method: generate human-readable summary
  - [ ] Format examples: "Voucher posted by {user} on {date}", "Voucher edited: {field} changed from {old} to {new}"
  - [ ] Ensure company scoping: only return history for vouchers in current company
- [ ] Create VoucherHistoryView frontend component (AC: #3)
  - [ ] Create `frontend/src/features/accounting/components/VoucherHistoryView.tsx`
  - [ ] Display audit log entries in chronological order (newest first or oldest first, user preference)
  - [ ] For each entry, display: timestamp, user, action, plain English summary
  - [ ] Implement colored field-by-field diff display:
    - Green highlight for added fields
    - Red highlight for removed fields
    - Yellow highlight for changed fields
  - [ ] Show expandable/collapsible diff details (default collapsed, expand to see full diff)
  - [ ] Add filter/search: filter by action type, user, date range
  - [ ] Integrate into VoucherFormPage: add "History" tab or button to view voucher history
  - [ ] Ensure RBAC: only users with view permissions can see history
- [ ] Implement audit log export functionality (AC: #4)
  - [ ] Review existing `AuditLogExportService` from backend
  - [ ] Enhance `AuditLogExportService.exportVoucherHistory(Long voucherId, ExportFormat format)` method
  - [ ] Implement JSON export: serialize all audit log entries for voucher to JSON array
  - [ ] Include cryptographic hashes (SHA-256 diff hash) in JSON export
  - [ ] Implement PDF export: generate PDF document with all audit log entries
  - [ ] Add hash watermark to PDF: embed SHA-256 hash of PDF content in footer/header
  - [ ] Create PDF template: header with voucher info, table of audit events, footer with hash
  - [ ] Use library: iText or Apache PDFBox for PDF generation
  - [ ] Add endpoint: `GET /api/v1/vouchers/{id}/history/export?format=json|pdf`
  - [ ] Ensure company scoping: only export history for vouchers in current company
  - [ ] Add RBAC: only users with export permissions can export audit logs
- [ ] Create Admin Audit Dashboard (AC: #5, deferred to post-MVP for alerts)
  - [ ] Create `frontend/src/features/admin/pages/AuditDashboard.tsx` (deferred to post-MVP)
  - [ ] Display mass actions: filter audit logs by `action` containing "BATCH" or "MASS"
  - [ ] Display blocked operations: filter audit logs by `success=false` and `reason` containing "BLOCKED"
  - [ ] Display suspicious patterns: filter audit logs by multiple reversal attempts, fraud detection events
  - [ ] Add filters: date range, user, action type, company (for admins)
  - [ ] Add statistics: count of mass actions, blocked operations, suspicious events per period
  - [ ] For MVP: log suspicious patterns only (no alerts, no dashboard UI)
  - [ ] Add logging for suspicious patterns: multiple reversal attempts, rapid-fire edits, bulk operations
  - [ ] Store suspicious pattern flags in `AuditLog.metadata` JSON field
  - [ ] Defer alert sending (email, notifications) to post-MVP
- [ ] Enhance AuditLog entity for voucher-specific metadata (AC: #1, #2)
  - [ ] Review existing `AuditLog` entity structure
  - [ ] Verify `changes` JSON field can store before/after snapshots
  - [ ] Verify `metadata` JSON field can store batch action stats, suspicious pattern flags
  - [ ] Add migration if needed: ensure `changes` and `metadata` columns support large JSON documents
  - [ ] Add index on `(entity_type, entity_id, company_id, created_at)` for efficient history queries
  - [ ] Add index on `(company_id, action, created_at)` for audit dashboard queries
- [ ] Create API endpoints for voucher history (AC: #3, #4)
  - [ ] Create `backend/src/main/java/com/accounting/controller/VoucherHistoryController.java`
  - [ ] Implement `GET /api/v1/vouchers/{id}/history` - get voucher history (list of audit log entries)
  - [ ] Implement `GET /api/v1/vouchers/{id}/history/export?format=json|pdf` - export voucher history
  - [ ] Add pagination support for history endpoint (if history is large)
  - [ ] Add filtering: filter by action type, date range
  - [ ] Ensure company scoping: only return history for vouchers in current company
  - [ ] Add RBAC: verify user has permission to view voucher history
  - [ ] Return field-level diff in response (for frontend colored diff display)
- [ ] Add comprehensive tests for audit trail functionality (AC: #1, #2, #3, #4, #5)
  - [ ] Create `backend/src/test/java/com/accounting/service/impl/VoucherHistoryServiceTest.java`
  - [ ] Test JSON snapshot generation: verify before/after snapshots are correctly serialized
  - [ ] Test SHA-256 diff hash calculation: verify hash is consistent and tamper-proof
  - [ ] Test field-by-field diff generation: verify correct change detection (added/removed/changed)
  - [ ] Test plain English summary generation: verify summaries are human-readable and accurate
  - [ ] Test batch action logging: verify aggregated entry contains voucher IDs and stats
  - [ ] Test company scoping: verify history only returned for vouchers in current company
  - [ ] Create `backend/src/test/java/com/accounting/controller/VoucherHistoryControllerIntegrationTest.java`
  - [ ] Test GET /api/v1/vouchers/{id}/history endpoint: verify response structure, pagination, filtering
  - [ ] Test GET /api/v1/vouchers/{id}/history/export?format=json: verify JSON export structure and hashes
  - [ ] Test GET /api/v1/vouchers/{id}/history/export?format=pdf: verify PDF generation and hash watermark
  - [ ] Test RBAC: verify unauthorized users cannot access history
  - [ ] Test company scoping: verify cross-company access blocked
  - [ ] Create frontend tests: `frontend/src/features/accounting/components/__tests__/VoucherHistoryView.test.tsx`
  - [ ] Test colored diff display: verify green/red/yellow highlights for added/removed/changed fields
  - [ ] Test plain English summary display: verify summaries are rendered correctly
  - [ ] Test filter/search functionality: verify filtering by action, user, date range works
  - [ ] Document QA test cases: create `docs/sprint-artifacts/qa-test-cases-3-5-audit-trail.md` with comprehensive test scenarios

## Dev Notes

### Learnings from Previous Story

**From Story 3.4 (Status: done)**

- **New Service Created**: `AccountControlService` available at `backend/src/main/java/com/accounting/service/AccountControlService.java` - use for company-scoped configuration lookups
- **Audit Service Enhanced**: `AuditService` has `logFraudDetection()` and `logBlockedAttempt()` methods - extend this pattern for voucher lifecycle events
- **Audit Log Entity**: `AuditLog` entity exists with `changes` (JSON) and `metadata` (JSON) fields - use these for storing before/after snapshots and batch action stats
- **Company Scoping Pattern**: All operations use `CompanyContext.getCompanyId()` for company isolation - apply same pattern for audit log queries
- **Error Handling**: Audit logging wrapped in try-catch blocks to not break main flow - follow same pattern for voucher lifecycle audit logging
- **JSON Handling**: System uses `JsonNode` (Jackson) for JSON fields in entities - use `ObjectMapper` to serialize voucher entities to JSON snapshots
- **Testing Pattern**: Comprehensive unit tests (17 for validation service) and integration tests - follow same pattern for audit trail tests
- **Frontend Pattern**: Real-time validation with debounced feedback - consider similar pattern for history view updates

[Source: docs/sprint-artifacts/3-4-leaf-only-and-double-entry-validation-engine.md#completion-notes-list]

### Architecture Patterns and Constraints

- **Audit Trail Integrity**: Audit logs must be immutable (append-only) and cryptographically hashed for tamper-proof verification [Source: docs/architecture/security-architecture.md#data-protection]
- **Company Scoping**: All audit log queries must be scoped to current company via `CompanyContext.getCompanyId()` [Source: docs/architecture/data-architecture.md#core-entities]
- **RBAC Enforcement**: Audit log access requires proper role-based permissions (auditors, admins) [Source: docs/architecture/security-architecture.md#authorization]
- **Service Layer Pattern**: Audit logging should be in service layer, not controller layer, for reusability [Source: docs/architecture/data-architecture.md#core-entities]
- **JSON Storage**: Use `JsonNode` (Jackson) for storing JSON snapshots in `AuditLog.changes` field [Source: backend/src/main/java/com/accounting/entity/AuditLog.java]
- **Non-Blocking Audit**: Audit logging should not block main business logic (async or try-catch wrapped) [Source: docs/sprint-artifacts/3-4-leaf-only-and-double-entry-validation-engine.md#completion-notes-list]

### Testing Strategy

- **Unit Tests**: Target 70% coverage for business logic layer (VoucherHistoryService, AuditService enhancements) using JUnit 5 and Mockito [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- **Integration Tests**: Test critical workflows (audit log generation, history retrieval, export functionality) using Spring Boot Test and TestContainers [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- **API Tests**: Test all REST endpoints for voucher history and export with authentication, RBAC enforcement, and company isolation [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- **Frontend Tests**: Target 60% coverage for VoucherHistoryView component using Vitest and Testing Library [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- **Test Scenarios**: Cover happy path (create → post → view history → export), validation errors, edge cases (large audit logs, concurrent operations) [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]

### Project Structure Notes

- **Backend Services**: Audit services in `backend/src/main/java/com/accounting/service/` and `backend/src/main/java/com/accounting/service/impl/`
- **Backend Controllers**: Voucher controllers in `backend/src/main/java/com/accounting/controller/`
- **Backend Entities**: `AuditLog` entity at `backend/src/main/java/com/accounting/entity/AuditLog.java`
- **Frontend Components**: Voucher components in `frontend/src/features/accounting/components/` and `frontend/src/components/voucher/`
- **Frontend Pages**: Voucher pages in `frontend/src/features/accounting/pages/Vouchers/`
- **Test Files**: Unit tests in `backend/src/test/java/com/accounting/service/impl/`, integration tests in `backend/src/test/java/com/accounting/controller/`

### References

- [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-35-audit-trail-for-voucher-lifecycle]
- [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-35-audit-trail-for-voucher-lifecycle]
- [Source: docs/sprint-artifacts/tech-spec-epic-3.md#traceability-mapping]
- [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- [Source: docs/architecture/security-architecture.md#data-protection]
- [Source: docs/architecture/data-architecture.md#core-entities]
- [Source: docs/architecture/implementation-patterns.md]
- [Source: backend/src/main/java/com/accounting/entity/AuditLog.java]
- [Source: backend/src/main/java/com/accounting/service/AuditService.java]
- [Source: backend/src/main/java/com/accounting/service/impl/AuditServiceImpl.java]
- [Source: docs/sprint-artifacts/3-4-leaf-only-and-double-entry-validation-engine.md#completion-notes-list]
- [Source: docs/sprint-artifacts/3-3-posting-unposting-reversal-workflows.md]

## Dev Agent Record

### Context Reference

<!-- Path(s) to story context XML will be added here by context workflow -->

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2025-01-27: Initial draft created with acceptance criteria, task plan, and learnings from Story 3.4.
- 2025-11-15: Auto-improved based on validation feedback - added Test Strategy Summary citation, Implementation Patterns citation, and Testing Strategy subsection in Dev Notes.
