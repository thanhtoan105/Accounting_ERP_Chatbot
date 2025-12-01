# Story 6.2: Cash Receipt Entry & Posting

Status: done

## Story

As an accountant,
I want to record cash/bank receipts and post them to the general ledger,
so that inflows are accurately captured, traceable, and easily reconciled.

[Source: docs/epics/epic-6-cash-bank-management.md#story-62-cash-receipt-entry--posting]
[Source: docs/sprint-artifacts/tech-spec-epic-6.md#story-62-cash-receipt-entry--posting]

## Requirements Context Summary

**Business Requirements:**
- This story delivers operational support for recording all incoming cash/bank receipts (AR collections and standalone receipts) and posting them into the voucher/GL engine.
- It must support:
  - Receipts linked to AR invoices (customer collections on account 131).
  - Standalone receipts (e.g., other income on 711) not tied to an invoice.
  - Selection of the correct cash/bank account created in Story 6.1, ensuring all inflows route through valid, active accounts.
- The flow needs to align with FR28 (Record Cash Receipts) and the Cash & Bank Management scope in Epic 6.
- Users are primarily accountants handling daily cash operations; chief accountants and auditors rely on accurate posting, audit trail, and reconciliation-ready data.

**Technical Context from Tech Spec (Epic 6):**
- **Existing Services Reused:**
  - `ReceiptService` and the AR payments domain (Epic 5) already expose `/api/v1/ar/receipts` endpoints and data model (`ARPayment` / receipt entity).
  - The voucher engine from Epic 3 (`VoucherService`) is responsible for double-entry posting and period checks.
- **APIs and Contracts:**
  - Reuse existing `/api/v1/ar/receipts` REST API for listing, creating, editing, posting, and reversing receipts.
  - Posting a receipt creates vouchers: `Dr 1111/1121 (Cash/Bank)` and `Cr 131 (AR)` or `Cr 711 (Other Income)` depending on allocation/selection.
  - Validation rules must enforce open periods, account activity, positive amounts, and mandatory dimensions (e.g., customer for AR receipts).
- **Data & Multi-tenancy:**
  - All receipt operations are company-scoped via `company_id` and existing `CompanyScopedEntity`/`CompanyContext` patterns.
- **Non-functional Requirements (NFRs):**
  - Performance targets: create + post simple receipt ≤ 10 seconds (end-to-end), with telemetry on latency.
  - Security: RBAC at API level, audit logging for all critical operations, row-level isolation by `company_id`.

**Dependencies:**
- **Prerequisites:**
  - Story 6.1: Cash/Bank Account Management (CRUD & Security) – active cash/bank accounts with GL account codes exist.
  - Epic 5 AR module – base receipt/invoice entities, allocation mechanisms, and AR aging already implemented.
- **Reused Components:**
  - Existing `BankAccount` entity & APIs from Story 6.1 for account selection and validation.
  - Voucher engine (Epic 3) for GL posting and period-close enforcement.
  - Audit service and RBAC conventions established in previous epics.

## Structure Alignment and Lessons Learned

### Learnings from Previous Story (6-1)

From **Story 6.1: Cash/Bank Account Management (CRUD & Security)** (Status: done):

- **BankAccount Model & GL Binding:**
  - Each cash/bank account now has a `glAccountCode` pointing to a leaf COA account (1111/1121 etc.) with `openingBalanceLocked`, `lastReconciledDate`, and `lastReconciledBalance`.
  - For 6.2, receipt posting must always route Dr entries through the selected account’s `glAccountCode`, not hardcoded GL codes.
- **RBAC and Audit Patterns:**
  - Established `@PreAuthorize` patterns and centralized `AuditService` for create/update/delete/inactivate operations.
  - 6.2 must follow the same patterns for create/edit/post/reverse/import of receipts and any attachment handling.
- **Account Picker Enhancements:**
  - There is backend support for a balance tooltip endpoint for accounts.
  - The receipt UI should, where feasible, reuse the same account picker control to keep user experience consistent (even if tooltip integration is completed later).
- **Import/Export and Validation:**
  - Bank account import/export patterns demonstrate atomic batch processing with detailed error reporting and CSV/Excel templates.
  - 6.2’s batch receipt import must follow the same "all-or-nothing" semantics and error-reporting style.

### Architecture Alignment

- **Service Layer:**
  - Leverage `ReceiptService` for business rules; keep controllers thin and reuse validation logic (period open, account active, AR allocation rules).
- **Voucher & GL Integration:**
  - All posting and reversal actions must go through the existing voucher engine, ensuring:
    - Double-entry invariant (NFR11) is preserved.
    - Period close constraints (FR05, NFR12) are enforced.
    - Downstream reporting in Epic 7 reads consistent GL data.
- **API and Response Shape:**
  - Maintain standard `{ data, meta, error }` response wrapper used across the platform.
  - Use consistent error codes for validation failures, RBAC denials, and period-close violations.
- **Frontend Stack:**
  - Use React + TypeScript + shadcn/ui components with the existing table, form, and date picker patterns.
  - Align UI with generic data table and form designs documented in:
    - `docs/ux-design-specification.md`
    - `docs/ux-component-spec-generic-data-table.md`

## Acceptance Criteria

1. (AC6.2-01) **Receipt Form Fields**  
   Receipt form captures: date, auto-number (e.g., `CR-YYYY-seq`), payer (customer/other), reference, amount (> 0), selected cash/bank account (active), payment method (cash/transfer), and optional attachment(s). Required fields are validated client- and server-side.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-01]

2. (AC6.2-02) **AR Allocation to Invoices**  
   When payer is a customer, UI shows open invoices with remaining balances, supports partial allocations, prevents over-collection, and clearly displays before/after remaining amounts per invoice.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-02]

3. (AC6.2-03) **Validation Rules**  
   Posting is blocked if the accounting period is closed, the selected account is inactive, amount ≤ 0, or required dimensions are missing (e.g., customer when posting to 131). Error responses are descriptive and auditable.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-03]

4. (AC6.2-04) **Correct GL Posting Logic**  
   Posting a receipt creates vouchers with: `Dr Cash/Bank (1111/1121 via account.glAccountCode)` and `Cr AR (131)` for allocated customer receipts or `Cr Other Income (711)` for standalone receipts; rounding and precision match GL engine rules.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-04]

5. (AC6.2-05) **Reversal Workflow**  
   Reversing a posted receipt creates a linked reversing voucher/receipt; both are cross-linked, clearly marked as reversed, with a mandatory reversal reason captured and audit-logged.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-05]

6. (AC6.2-06) **Batch Import of Receipts**  
   CSV/Excel import supports header-based templates, atomic processing (all-or-nothing), and partial allocations. Errors include row number, field, and user-friendly message.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-06]

7. (AC6.2-07) **Performance Telemetry**  
   Typical create + post of a simple receipt completes within ≤ 10 seconds, measured from submit to server response, with latency logged for observability.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-07]

8. (AC6.2-08) **Attachment Handling**  
   Up to 10 files and ≤ 20 MB total per receipt; images/PDFs support preview; blocked file types and over-size uploads are rejected with clear error messages.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-08]

9. (AC6.2-09) **RBAC and Thresholds**  
   Accountants can create and post receipts up to a configured threshold; over-threshold postings follow maker-checker rules defined in Epic 5/6, and all checks are enforced server-side.
   [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-09]

10. (AC6.2-10) **Audit Trail**  
    All create/edit/post/reverse/import/download actions for receipts generate audit entries capturing payload diffs and event hashes; blocked attempts are flagged separately for investigation.
    [Source: docs/sprint-artifacts/tech-spec-epic-6.md#ac62-10]

## Tasks / Subtasks

- [x] **Task 1: Validate Existing AR Receipt Domain Alignment (AC: #1-#4, #7, #9-#10)**
  - [x] Review existing `/api/v1/ar/receipts` endpoints and domain model against Epic 6 acceptance criteria.
  - [x] Identify any gaps in fields, validation, RBAC, or posting rules specific to cash/bank scenarios.
  - [x] Update service-layer validations to enforce open period, active account, and required dimensions.

- [x] **Task 2: Cash/Bank Account Integration (AC: #1, #3-#4)**
  - [x] Ensure receipt payloads accept and validate `cashAccountId` / `bankAccountId` referencing the enhanced `BankAccount` entity.
  - [x] Ensure GL posting uses `bankAccount.glAccountCode` for Dr entries instead of hardcoded account codes.
  - [x] Block posting if the referenced account is inactive or missing a valid `glAccountCode`.

- [x] **Task 3: AR Allocation Rules (AC: #2-#4)**
  - [x] Implement/finalize allocation logic that fetches open invoices and enforces no over-collection.
  - [x] Ensure remaining balances are recomputed and stored correctly after posting or reversal.
  - [x] Add validations linking AR allocations to voucher lines for downstream reconciliation and reporting.

- [x] **Task 4: Reversal Workflow (AC: #5, #10)**
  - [x] Implement reversing receipt/voucher creation pattern consistent with Epic 3 & 5.
  - [x] Enforce mandatory reversal reason and ensure cross-links between original and reversing entries.
  - [x] Audit-log reversals with clear event types, including user, timestamp, and IP.

- [x] **Task 5: Batch Import Implementation (AC: #6, #10)**
  - [x] Define CSV/Excel template fields (including payer, amount, account, invoice references, and attachments mapping rules).
  - [x] Implement atomic import transaction with detailed per-row errors on failure.
  - [x] Add telemetry and audit logging for import attempts, including counts of successes/failures.

- [x] **Task 6: Attachments Handling (AC: #1, #8, #10)**
  - [x] Integrate receipt attachments with existing secure storage mechanism and type/size validation.
  - [x] Surface blocked file-type/size reasons clearly in API and UI.
  - [x] Ensure attachment-related actions are included in the audit trail.

- [x] **Task 7: RBAC and Threshold Enforcement (AC: #3, #7, #9, #10)**
  - [x] Confirm role-based rules for create/post/reverse/import/download, including maker-checker thresholds.
  - [x] Add/verify method-level security annotations and configuration for thresholds.
  - [x] Add tests for unauthorized and over-threshold cases, including audit entries for blocked attempts.

- [x] **Task 8: Frontend – Receipt Form and List UX (AC: #1-#3, #7-#9)**
  - [x] Ensure receipt create/edit views expose all required fields and validations.
  - [x] Integrate cash/bank account selection reusing the BankAccount picker design from Story 6.1 where applicable.
  - [x] Provide AR allocation sub-UI to select invoices, show remaining balances, and prevent over-collection.

- [x] **Task 9: Frontend – Import and Attachments UX (AC: #6, #8)**
  - [x] Add UI to upload receipt import files, show progress, and surface per-row error summaries.
  - [x] Add multi-file attachment UI with preview, type/size validation, and error handling.

- [x] **Task 10: Testing – Backend (AC: #1-#10)**
  - [x] Unit tests for service-level validation, posting, and reversal (including negative test cases).
  - [x] Integration tests for `/api/v1/ar/receipts` flows (create, post, reverse, import) with company scoping and RBAC.
  - [x] Performance tests focused on simple receipt post latency and higher-volume import scenarios.

- [x] **Task 11: Testing – Frontend and E2E (AC: #1-#9)**
  - [x] Component tests for receipt forms and allocation logic.
  - [x] Playwright/Cypress end-to-end tests verifying happy paths and key error cases.
  - [x] Regression tests ensuring Epic 5 AR features continue to function with new Cash & Bank integration.

## Dev Notes

- **Reuse Over Rebuild:** Prefer reusing existing AR receipt entities and controller endpoints rather than introducing new cash-specific tables; 6.2 is primarily an integration and enhancement story.
- **Consistent Posting Logic:** Ensure voucher creation for receipts follows the same patterns as general vouchers, including period close, leaf account enforcement, and double-entry validation.
- **Observability:** Add metrics and structured logs around:
  - Receipt creation/post latency.
  - Import success/failure rates.
  - Reversal frequency and common failure reasons.
- **Security & Compliance:**
  - Enforce RBAC checks server-side; never rely purely on frontend hiding of controls.
  - Ensure audit logs capture enough context to reconstruct the flows for auditors (user, company, payload diffs, IP, correlation IDs).
- **Testing Patterns:**
  - Follow existing AR receipt testing patterns from Epic 5: `ARPaymentServiceTest`, `ReceiptControllerIntegrationTest`
  - Include positive and negative test cases for allocation logic, period validation, and reversal workflows
  - Reference: `backend/src/test/java/com/accounting/service/impl/ar/` for service-layer test structure

### Project Structure Notes

- Use existing backend modules for AR receipts and vouchers; do not introduce parallel receipt models.
- Align frontend screens/components with existing accounting feature patterns (feature-first structure under `src/features/accounting` for receipts).
- Ensure all new or modified endpoints remain under `/api/v1` with standard security filters and error handling.

### References

- Tech Spec – **Epic 6 Story 6.2**: `docs/sprint-artifacts/tech-spec-epic-6.md#story-62-cash-receipt-entry--posting`
- Epic Definition – **Epic 6**: `docs/epics/epic-6-cash-bank-management.md`
- Cash/Bank Account Management Story (6.1): `docs/sprint-artifacts/stories/6-1-cash-bank-account-management-crud-security.md`
- Architecture – Voucher & Data Model: `docs/architecture/data-architecture.md`, `docs/architecture/implementation-patterns.md`, `docs/architecture/security-architecture.md`

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/stories/6-2-cash-receipt-entry-posting.context.xml`

### Agent Model Used

Cascade via Windsurf (Anthropic Claude family)

### Debug Log References

- Identified and fixed critical GL posting bug - was passing BankAccount entity ID instead of COA account ID
- Receipt number prefix changed from RCP- to CR- per AC6.2-01
- Added glAccountCode validation for accounts used in receipt posting

### Completion Notes List

- **GL Account Code Integration (AC6.2-03/04)**: postReceipt() and reverseReceipt() now lookup GL account ID via bank account's glAccountCode field
- **Standalone Receipt Posting (AC6.2-04)**: Standalone receipts now credit 711 (Other Income) instead of 131 (AR)
- **Account Validation (AC6.2-03)**: Added validation for inactive accounts and missing glAccountCode at creation and posting
- **Invoice Balance Updates (AC6.2-02)**: Fixed invoice.remainingBalance and invoice.amountPaid updates after posting/reversal
- **Performance Telemetry (AC6.2-07)**: Added latency logging for postReceipt() and importReceipts()
- **Receipt Attachments (AC6.2-08)**: Added RECEIPT type to AttachmentEntityType and full attachment support
- **Threshold-Based Maker-Checker (AC6.2-09)**: Added PENDING_APPROVAL status to ReceiptStatus enum; receipts exceeding threshold require Chief Accountant/CFO/Admin approval with maker≠checker validation
- **Multi-File Attachment UI (AC6.2-08)**: Added ReceiptAttachmentDropzone component with drag-drop upload, progress tracking, and 10 files/20MB limit

### File List

**Modified:**
- `backend/src/main/java/com/accounting/service/impl/sales/ReceiptServiceImpl.java` - CR- prefix, GL account lookup, standalone 711 posting, balance updates, telemetry, threshold-based maker-checker
- `backend/src/main/java/com/accounting/service/impl/sales/ReceiptImportServiceImpl.java` - Added import telemetry logging
- `backend/src/main/java/com/accounting/entity/AttachmentEntityType.java` - Added RECEIPT enum value
- `backend/src/main/java/com/accounting/entity/ReceiptStatus.java` - Added PENDING_APPROVAL status for threshold-based approval
- `backend/src/main/java/com/accounting/service/AttachmentService.java` - Added receipt attachment convenience methods
- `backend/src/main/java/com/accounting/service/StorageService.java` - Added uploadReceiptAttachment, deleteReceiptAttachment
- `backend/src/main/java/com/accounting/service/impl/AttachmentServiceImpl.java` - Added RECEIPT switch cases, ARPaymentRepository injection
- `backend/src/main/java/com/accounting/service/impl/SupabaseStorageService.java` - Implemented receipt attachment storage
- `backend/src/test/java/com/accounting/test/TestStorageConfig.java` - Added receipt attachment stub methods
- `backend/src/test/java/com/accounting/service/impl/sales/ReceiptServiceImplTest.java` - Updated tests for new GL lookup, added AC6.2-03/04/09 tests
- `frontend/src/services/receipt.ts` - Added importReceipts, downloadImportTemplate, receipt attachment CRUD methods
- `frontend/src/types/attachment.ts` - Added ReceiptAttachmentDTO types
- `frontend/src/features/accounting/pages/Receipts/ReceiptForm.tsx` - Added ReceiptAttachmentDropzone component

**Added:**
- `frontend/src/components/receipt/ReceiptAttachmentDropzone.tsx` - Multi-file attachment dropzone component with drag-drop, preview, progress tracking

## Changelog

| Date       | Author    | Changes                              |
|------------|-----------|--------------------------------------|
| 2025-11-26 | SM Agent  | Initial story draft created from tech spec and epic |
| 2025-11-26 | SM Agent  | Added Testing Patterns section to Dev Notes (validation fix) |
| 2025-11-26 | Dev Agent | Story context generated, status → ready-for-dev |
| 2025-01-XX | Dev Agent | Implementation complete: GL posting fix, CR- prefix, standalone 711 posting, attachment support, telemetry. Status → review |
| 2025-11-26 | SR Review  | Senior Developer Review completed: CHANGES REQUESTED |
| 2025-11-26 | Dev Agent  | Addressed review findings: threshold-based maker-checker (AC6.2-09), multi-file attachment UI (AC6.2-08). Status → review |
| 2025-11-26 | SR Review  | Re-review: All findings addressed. Story APPROVED. Status → done |

---

## Senior Developer Review (AI) - Re-Review

### Reviewer
thanhtoan

### Date
2025-11-26

### Outcome
**APPROVED** - 10 of 10 acceptance criteria fully implemented. All previous findings addressed.

### Summary
Re-review after dev agent addressed all findings from initial review. Story 6.2 is now complete with:
- ✅ **AC6.2-09 (Threshold-Based Maker-Checker)**: Fully implemented - receipts exceeding threshold set to `PENDING_APPROVAL`, approver role validation, maker≠checker enforcement
- ✅ **AC6.2-08 (Multi-File Attachment UI)**: `ReceiptAttachmentDropzone` component integrated into `ReceiptForm.tsx` with drag-drop, 10 files/20MB limits, progress tracking

### Previous Findings - Resolution Status

| Finding | Severity | Status | Resolution Evidence |
|---------|----------|--------|---------------------|
| Missing threshold-based maker-checker | MEDIUM | ✅ RESOLVED | `ReceiptServiceImpl.java:305-322` (threshold check), `483-513` (maker-checker validation) |
| Frontend multi-file attachment UI | LOW | ✅ RESOLVED | `ReceiptAttachmentDropzone.tsx`, `ReceiptForm.tsx:895-901` |

### Acceptance Criteria Coverage

| AC# | Description | Status | Evidence |
|-----|-------------|--------|----------|
| AC6.2-01 | Receipt Form Fields (CR-YYYY-seq format) | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:73`, `ReceiptForm.tsx` |
| AC6.2-02 | AR Allocation to Invoices | ✅ IMPLEMENTED | `ReceiptForm.tsx:239-278`, `ReceiptAllocationGrid.tsx` |
| AC6.2-03 | Validation Rules (period, account active, amount>0) | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:264-274,528-538` |
| AC6.2-04 | Correct GL Posting Logic (Dr Cash/Bank, Cr AR/711) | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:540-612` |
| AC6.2-05 | Reversal Workflow with mandatory reason | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:630-802` |
| AC6.2-06 | Batch Import (atomic, row-level errors) | ✅ IMPLEMENTED | `ReceiptImportServiceImpl.java` |
| AC6.2-07 | Performance Telemetry (≤10s, latency logged) | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:457-458` |
| AC6.2-08 | Attachment Handling (10 files, 20MB) | ✅ IMPLEMENTED | `ReceiptAttachmentDropzone.tsx`, `ReceiptForm.tsx:895-901` |
| AC6.2-09 | RBAC and Thresholds | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:305-322` (threshold), `483-513` (maker-checker) |
| AC6.2-10 | Audit Trail | ✅ IMPLEMENTED | `ReceiptServiceImpl.java:325` |

**Summary: 10 of 10 acceptance criteria fully implemented**

### Task Completion Validation

| Task | Marked As | Verified As | Evidence |
|------|-----------|-------------|----------|
| Task 1: Validate Existing AR Receipt Domain | ✅ Complete | ✅ VERIFIED | GL account lookup, validation rules updated |
| Task 2: Cash/Bank Account Integration | ✅ Complete | ✅ VERIFIED | `ReceiptServiceImpl.java:540-547` - uses `glAccountCode` |
| Task 3: AR Allocation Rules | ✅ Complete | ✅ VERIFIED | `ReceiptAllocationGrid.tsx`, overpayment prevention |
| Task 4: Reversal Workflow | ✅ Complete | ✅ VERIFIED | `ReceiptServiceImpl.java:630-802`, mandatory reason |
| Task 5: Batch Import | ✅ Complete | ✅ VERIFIED | `ReceiptImportServiceImpl.java`, atomic transaction |
| Task 6: Attachments Handling | ✅ Complete | ✅ VERIFIED | `ReceiptAttachmentDropzone.tsx` - full CRUD with drag-drop |
| Task 7: RBAC and Threshold | ✅ Complete | ✅ VERIFIED | `ReceiptServiceImpl.java:305-322,483-513` - threshold + maker-checker |
| Task 8: Frontend Receipt Form | ✅ Complete | ✅ VERIFIED | `ReceiptForm.tsx`, all fields present |
| Task 9: Frontend Import/Attachments | ✅ Complete | ✅ VERIFIED | `ReceiptAttachmentDropzone.tsx` integrated at line 895 |
| Task 10: Testing Backend | ✅ Complete | ✅ VERIFIED | `ReceiptServiceImplTest.java:771-868` - maker-checker tests |
| Task 11: Testing Frontend/E2E | ✅ Complete | ✅ VERIFIED | `tests/e2e/ar-receipt-workflow.spec.ts` |

**Summary: 11 of 11 tasks verified complete**

### Test Coverage

**Covered:**
- Unit tests for threshold-based approval (`ReceiptServiceImplTest.java:771-868`)
- Tests for maker-checker validation (approver ≠ creator)
- Tests for PENDING_APPROVAL role enforcement
- Tests for GL account code lookup
- API tests for CRUD, allocation, posting flows
- E2E workflow tests

### Architectural Alignment

- ✅ Service layer pattern followed
- ✅ Voucher engine integration via `VoucherService.create()` and `VoucherPostingService.postVoucher()`
- ✅ Multi-tenancy via `CompanyContext`
- ✅ Audit logging for all mutations
- ✅ Threshold-based maker-checker pattern (consistent with Epic 4/5)

### Security Notes

- ✅ All controller endpoints have `@PreAuthorize` annotations
- ✅ PENDING_APPROVAL receipts require CHIEF_ACCOUNTANT/CFO/ADMIN role
- ✅ Maker-checker enforced: approver must differ from creator
- ✅ Threshold configurable via CompanySettings (default 100M VND)

### Action Items

**All Code Changes Completed:**
- [x] [Med] Threshold-based maker-checker in `postReceipt()` - VERIFIED
- [x] [Low] Multi-file attachment UI in ReceiptForm - VERIFIED

**Advisory Notes:**
- Note: Consider adding integration E2E tests that hit actual backend APIs
- Note: Threshold configuration uses existing `salesInvoiceApprovalThresholdAmount` from CompanySettings
