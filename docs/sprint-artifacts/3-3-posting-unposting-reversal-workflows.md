# Story 3.3: Posting, Unposting & Reversal Workflows

Status: drafted

## Story

As an accountant or chief accountant,
I want to post, unpost, or reverse vouchers with full double-entry validation,
so that books remain consistent and errors can be properly corrected.

[Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]

## Acceptance Criteria

1. Posting operation changes voucher status atomically (DRAFT → POSTED), returns updated voucher and generated journal entries in single response. [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
2. Unposting checks all dependencies (referenced in payments/receipts from Epic 4-5); blocks with error popup showing conflicting references (e.g., "Cannot unpost – referenced in payment #P123"). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
3. Reversal creates new voucher with number format "REV-{original_number}", status "POSTED", links bi-directionally (original.reversedBy = reversal, reversal.reversalVoucher = original). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
4. "Reversed by" badge on original voucher is clickable, navigates to reversal voucher detail page. [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
5. Export voucher and reversal trail as PDF with barcode/QR code (deferred to post-MVP, basic PDF export in MVP). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
6. Double reversal blocked at UI and API level; 409 Conflict error returned, attempt logged in audit. [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
7. Deletion of posted voucher forbidden (UI disables delete button, API returns 409); all attempted deletions logged as blocked in audit. [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
8. Posting validation failures (period closed, Dr≠Cr, missing dimensions) block posting and display all errors at once (not one-by-one). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]
9. Batch posting/import: any validation error aborts entire batch, logs all issues/results in audit (batch import deferred to post-MVP). [Source: docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows]

## Tasks / Subtasks

- [ ] Implement VoucherPostingService for atomic posting workflow (AC: #1, #8)
  - [ ] Create `backend/src/main/java/com/accounting/service/voucher/VoucherPostingService.java` interface
  - [ ] Create `backend/src/main/java/com/accounting/service/impl/voucher/VoucherPostingServiceImpl.java` implementation
  - [ ] Implement `postVoucher(UUID voucherId)` method with atomic transaction
  - [ ] Validate voucher status is DRAFT before posting
  - [ ] Call VoucherValidationService for bulk validation (double-entry, leaf-only, required dimensions, period open)
  - [ ] If validation fails, return detailed error map with all errors at once
  - [ ] If validation passes, start database transaction
  - [ ] Update voucher status to POSTED, set postedBy/postedAt
  - [ ] Generate JournalEntry records for each voucher line (one entry per line with debit or credit)
  - [ ] Commit transaction atomically (all-or-nothing)
  - [ ] Return PostVoucherResponse with updated voucher and generated journal entries
  - [ ] Log posting event in audit trail with before/after snapshot
- [ ] Create POST /api/v1/vouchers/{voucherId}/post endpoint (AC: #1, #8)
  - [ ] Add `postVoucher()` method to `VoucherController.java`
  - [ ] Request body: `PostVoucherRequest { voucherId, validateOnly?: boolean }`
  - [ ] Response: `PostVoucherResponse { voucher: VoucherDTO, journalEntries: JournalEntryDTO[], validationErrors?: ValidationErrorMap }`
  - [ ] RBAC: `@PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")` - Chief Accountant+ can post
  - [ ] Company scoping via CompanyScopeAspect
  - [ ] Return 400 Bad Request with detailed error map if validation fails
  - [ ] Return 409 Conflict if voucher already posted
  - [ ] Return 200 OK with posted voucher and journal entries if successful
- [ ] Implement VoucherUnpostingService for dependency checking and unposting (AC: #2)
  - [ ] Create `backend/src/main/java/com/accounting/service/voucher/VoucherUnpostingService.java` interface
  - [ ] Create `backend/src/main/java/com/accounting/service/impl/voucher/VoucherUnpostingServiceImpl.java` implementation
  - [ ] Implement `checkDependencies(UUID voucherId)` method to check for references in payments/receipts (Epic 4-5 integration points - placeholder for now)
  - [ ] Implement `unpostVoucher(UUID voucherId, String reason)` method
  - [ ] Check voucher status is POSTED before unposting
  - [ ] Check dependencies - if referenced, return dependency conflict details
  - [ ] If no dependencies, start database transaction
  - [ ] Update voucher status to DRAFT, clear postedBy/postedAt
  - [ ] Delete associated JournalEntry records (cascade delete or explicit delete)
  - [ ] Commit transaction atomically
  - [ ] Log unposting event in audit trail with reason
  - [ ] Return updated voucher (status=DRAFT)
- [ ] Create POST /api/v1/vouchers/{voucherId}/unpost endpoint (AC: #2)
  - [ ] Add `unpostVoucher()` method to `VoucherController.java`
  - [ ] Request body: `{ reason: string }` (required for audit)
  - [ ] Response: `{ data: VoucherDTO, meta: {...} }`
  - [ ] RBAC: `@PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")` - Chief Accountant+ can unpost
  - [ ] Company scoping via CompanyScopeAspect
  - [ ] Return 409 Conflict with dependency details if voucher is referenced
  - [ ] Return 400 Bad Request if voucher is not posted
  - [ ] Return 200 OK with unposted voucher if successful
- [ ] Implement VoucherReversalService for reversal workflow (AC: #3, #6)
  - [ ] Create `backend/src/main/java/com/accounting/service/voucher/VoucherReversalService.java` interface
  - [ ] Create `backend/src/main/java/com/accounting/service/impl/voucher/VoucherReversalServiceImpl.java` implementation
  - [ ] Implement `reverseVoucher(UUID voucherId, String description, String reason)` method
  - [ ] Check voucher status is POSTED before reversal
  - [ ] Check if voucher already reversed (check reversedBy field) - if yes, throw exception (409 Conflict)
  - [ ] Create new voucher with:
    - Voucher number: "REV-{original_voucher_number}"
    - Date: Current date (or next open period)
    - Description: "REV-{original_description}" + user reason
    - Status: DRAFT initially (will be auto-posted)
    - Lines: Copy all lines from original, swap debit/credit amounts
  - [ ] Link bi-directionally:
    - reversal.reversalVoucher = original (OneToOne)
    - original.reversedBy = reversal (OneToOne)
  - [ ] Auto-post reversal voucher (call VoucherPostingService.postVoucher())
  - [ ] Update original voucher: set reversedBy reference
  - [ ] Log reversal event in audit trail with both vouchers
  - [ ] Return both vouchers (original and reversal)
  - [ ] Block double reversal: check reversedBy before creating reversal, return 409 if already reversed
- [ ] Create POST /api/v1/vouchers/{voucherId}/reverse endpoint (AC: #3, #6)
  - [ ] Add `reverseVoucher()` method to `VoucherController.java`
  - [ ] Request body: `{ description: string, reason: string }`
  - [ ] Response: `{ data: { original: VoucherDTO, reversal: VoucherDTO }, meta: {...} }`
  - [ ] RBAC: `@PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")` - Chief Accountant+ can reverse
  - [ ] Company scoping via CompanyScopeAspect
  - [ ] Return 409 Conflict if voucher already reversed (double reversal attempt)
  - [ ] Return 400 Bad Request if voucher is not posted
  - [ ] Return 200 OK with both vouchers if successful
  - [ ] Log all reversal attempts (even blocked ones) in audit trail
- [ ] Update Voucher entity to support reversal relationships (AC: #3)
  - [ ] Add `@OneToOne` relationship fields: `reversalVoucher` and `reversedBy` to `Voucher.java`
  - [ ] Add `reversalVoucherId` and `reversedByVoucherId` fields to VoucherDTO
  - [ ] Update database migration if needed (add foreign key columns)
- [ ] Add reversal badge UI components (AC: #4)
  - [ ] Update `VoucherList.tsx` to display "Reversed by" badge if `reversedByVoucherId` exists
  - [ ] Make badge clickable, navigate to reversal voucher detail page
  - [ ] Update `VoucherForm.tsx` to display reversal badge on voucher detail view
  - [ ] Add "Reversal of" badge on reversal voucher showing link to original
  - [ ] Style badges with appropriate colors (e.g., orange for reversal)
- [ ] Update delete endpoint to block posted vouchers (AC: #7)
  - [ ] Update `DELETE /api/v1/vouchers/{voucherId}` in `VoucherController.java`
  - [ ] Check voucher status before deletion
  - [ ] If status is POSTED, return 409 Conflict with message "Cannot delete posted voucher"
  - [ ] Log blocked deletion attempt in audit trail with user ID, timestamp, voucher ID
  - [ ] Only allow deletion of DRAFT vouchers with no references
- [ ] Update delete button UI to disable for posted vouchers (AC: #7)
  - [ ] Update `VoucherList.tsx` delete action to check voucher status
  - [ ] Disable delete button if status is POSTED or REVERSED
  - [ ] Show tooltip explaining why delete is disabled
  - [ ] Update `VoucherForm.tsx` delete button similarly
- [ ] Implement bulk validation error display (AC: #8)
  - [ ] Update `VoucherPostingService` to collect all validation errors before returning
  - [ ] Return comprehensive ValidationErrorMap with:
    - Global errors (period closed, voucher not found, etc.)
    - Line-level errors (leaf-only violations, missing dimensions, etc.)
    - All errors shown at once (not sequential)
  - [ ] Update frontend `VoucherForm.tsx` to display all errors in error modal/dialog
  - [ ] Show error summary with counts (e.g., "5 validation errors found")
  - [ ] Display errors grouped by category (global, line 1, line 2, etc.)
- [ ] Create JournalEntry entity and repository (AC: #1)
  - [ ] Create `backend/src/main/java/com/accounting/entity/JournalEntry.java` entity
  - [ ] Fields: id, voucherId, accountId, periodId, debitAmount, creditAmount, customerId, supplierId, costCenterId, companyId, postedAt
  - [ ] Create `backend/src/main/java/com/accounting/repository/JournalEntryRepository.java`
  - [ ] Add database migration for `journal_entries` table
  - [ ] Create JournalEntryDTO for API responses
- [ ] Create JournalEntryService for GL entry generation (AC: #1)
  - [ ] Create `backend/src/main/java/com/accounting/service/gl/JournalEntryService.java` interface
  - [ ] Create `backend/src/main/java/com/accounting/service/impl/gl/JournalEntryServiceImpl.java` implementation
  - [ ] Implement `generateJournalEntries(Voucher voucher)` method
  - [ ] For each voucher line, create one JournalEntry record:
    - If line has debitAmount > 0: create entry with debitAmount
    - If line has creditAmount > 0: create entry with creditAmount
    - Copy dimension references (customerId, supplierId, costCenterId) from voucher line
    - Set periodId from voucher.period
    - Set companyId from voucher.companyId
    - Set postedAt to current timestamp
  - [ ] Return list of created JournalEntry records
- [ ] Implement testing (AC: #1-#9)
  - [ ] Integration tests for POST /api/v1/vouchers/{id}/post endpoint:
    - Test successful posting (DRAFT → POSTED, journal entries created)
    - Test validation failures (period closed, Dr≠Cr, missing dimensions) - all errors returned at once
    - Test posting already-posted voucher (409 Conflict)
    - Test RBAC enforcement (Accountant cannot post, Chief Accountant can)
    - Test company scoping (cannot post voucher from different company)
    - Test atomic transaction (rollback on error)
  - [ ] Integration tests for POST /api/v1/vouchers/{id}/unpost endpoint:
    - Test successful unposting (POSTED → DRAFT, journal entries deleted)
    - Test dependency checking (placeholder - will be enhanced in Epic 4-5)
    - Test unposting non-posted voucher (400 Bad Request)
    - Test RBAC enforcement
    - Test company scoping
  - [ ] Integration tests for POST /api/v1/vouchers/{id}/reverse endpoint:
    - Test successful reversal (creates reversal voucher, auto-posts, bi-directional links)
    - Test double reversal block (409 Conflict)
    - Test reversal of non-posted voucher (400 Bad Request)
    - Test reversal voucher number format ("REV-{original}")
    - Test reversal lines (swapped debit/credit amounts)
    - Test RBAC enforcement
    - Test company scoping
    - Test audit logging for all reversal attempts
  - [ ] Integration tests for DELETE /api/v1/vouchers/{id} endpoint:
    - Test deletion of posted voucher (409 Conflict, audit log)
    - Test deletion of draft voucher (success)
    - Test audit logging for blocked deletions
  - [ ] Unit tests for VoucherPostingService:
    - Test atomic transaction rollback on validation error
    - Test journal entry generation logic
    - Test bulk validation error collection
  - [ ] Unit tests for VoucherReversalService:
    - Test reversal voucher creation with swapped amounts
    - Test bi-directional linking
    - Test double reversal prevention
  - [ ] Frontend unit tests (deferred to future iteration):
    - Test Post button UI state changes
    - Test error modal display for bulk validation errors
    - Test reversal badge navigation
    - Test delete button disable logic

## Dev Notes

### Requirements Context Summary

- **Atomic posting workflow:** Posting operation must change voucher status from DRAFT to POSTED and generate journal entries in a single atomic database transaction. If any validation fails or journal entry generation fails, the entire operation must rollback. The response must return both the updated voucher and generated journal entries in a single response. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#voucher-posting-workflow]
- **Comprehensive validation before posting:** All validation errors must be collected and returned at once (not sequentially). Validation includes: double-entry balancing (Dr=Cr), leaf-only account validation, required dimensions per account, period open validation. All errors must be shown to user simultaneously. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#voucher-posting-workflow]
- **Unposting dependency checking:** Before unposting a voucher, the system must check if the voucher is referenced in any payments (Epic 4) or receipts (Epic 5). If referenced, unposting must be blocked with a clear error message showing conflicting references. For MVP, dependency checking can be a placeholder that returns empty (no dependencies) since Epic 4-5 are not yet implemented. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#voucher-reversal-workflow]
- **Reversal workflow with auto-posting:** Reversal creates a new voucher with number format "REV-{original_number}", copies all lines with swapped debit/credit amounts, links bi-directionally to original voucher, and auto-posts the reversal voucher. The reversal voucher must be posted immediately (not left as draft). Double reversal must be blocked at both UI and API level with 409 Conflict error. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#voucher-reversal-workflow]
- **Posted voucher deletion protection:** Posted vouchers cannot be deleted. The UI must disable the delete button for posted vouchers, and the API must return 409 Conflict if deletion is attempted. All blocked deletion attempts must be logged in audit trail for security and compliance. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#security]
- **Journal entry generation:** When a voucher is posted, the system must generate JournalEntry records for each voucher line. Each journal entry represents one side of the double-entry (either debit or credit). Journal entries are immutable after creation and are used for reporting (Trial Balance, Financial Statements in Epic 7). [Source: docs/sprint-artifacts/tech-spec-epic-3.md#data-models-and-contracts] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#journalentry-entity]
- **RBAC enforcement:** Posting, unposting, and reversal operations require Chief Accountant+ role (Chief Accountant, Admin, CFO). Accountant role can only create/edit drafts but cannot post. All endpoints must enforce RBAC at method level using `@PreAuthorize` annotations. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#security--multi-tenancy] [Source: docs/architecture/security-architecture.md]

### Structure Alignment Summary

- **Reuse VoucherService patterns:** Extend existing `VoucherService` and `VoucherController` from Story 3.2. Add new service interfaces (`VoucherPostingService`, `VoucherUnpostingService`, `VoucherReversalService`) following the same service layer patterns. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list]
- **Reuse VoucherValidationService:** Story 3.2 created `VoucherValidationService` for real-time validation. Story 3.3 will reuse this service for bulk validation before posting. The validation service already supports field-level error map generation. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list]
- **Follow REST endpoint conventions:** New endpoints follow the established pattern `/api/v1/vouchers/{id}/{action}` (e.g., `/post`, `/unpost`, `/reverse`). Use standard error response format with detailed error maps. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#rest-endpoints]
- **Journal entry storage:** Create new `journal_entries` table following the data architecture. Journal entries are denormalized for reporting performance (include dimension IDs directly). [Source: docs/architecture/data-architecture.md] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#journalentry-entity]
- **Frontend integration:** Update existing `VoucherForm.tsx` and `VoucherList.tsx` components to add Post/Unpost/Reverse buttons and error display. Reuse error handling patterns from Story 3.2 (toast notifications, error modals). [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list]

### Learnings from Previous Story (3-2)

**From Story 3-2-voucher-form-create-edit-line-item-engine (Status: done)**

- **Entry-to-lines transformation:** Story 3.2 implemented backend transformation logic that converts 1 entry line (debitAccount + creditAccount + amount) → 2 voucher lines (one debit line, one credit line). This transformation ensures double-entry balance automatically. When posting, journal entries should be generated from these voucher lines (not entry lines). [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list]
- **Field-level validation error map:** Story 3.2 established the field-level error map format: `{ error: { code, message, details: { lines: { [lineNumber]: { [field]: [errors] } } } }, meta: {...} }`. Story 3.3 should reuse this format for posting validation errors. The `VoucherValidationService` already supports this format. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list]
- **VoucherValidationService reuse:** Story 3.2 created `VoucherValidationServiceImpl` with comprehensive validation logic (leaf-only, required dimensions, positive amounts, period validation). Story 3.3 should call this service for bulk validation before posting. The service already returns field-level error maps. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#file-list]
- **Company scoping pattern:** All voucher operations in Story 3.2 use `CompanyScopeAspect` for automatic company filtering. Story 3.3 should follow the same pattern for posting, unposting, and reversal operations. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#senior-developer-review-ai]
- **RBAC enforcement:** Story 3.2 enforced RBAC using `@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','CHIEF_ACCOUNTANT','CFO')")` on all endpoints. Story 3.3 should use stricter RBAC for posting operations: `@PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")` - only Chief Accountant+ can post/unpost/reverse. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#senior-developer-review-ai]
- **Error handling patterns:** Story 3.2 implemented comprehensive error handling with toast notifications, error modals, and "Copy Error Details" functionality. Story 3.3 should reuse these patterns for posting validation errors. The frontend should display all validation errors in a modal/dialog, not sequentially. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list]
- **Database transaction patterns:** Story 3.2 used `@Transactional` annotations for voucher CRUD operations. Story 3.3 should use `@Transactional` with proper isolation levels for atomic posting operations. Consider using `@Transactional(isolation = Isolation.SERIALIZABLE)` for posting to prevent race conditions. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#reliabilityavailability]
- **No unresolved review items:** Previous story review closed with no unresolved action items, so no carry-over blockers for this iteration. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#senior-developer-review-ai]

### Project Structure Notes

- **Backend Structure:** Create new service interfaces under `backend/src/main/java/com/accounting/service/voucher/`: `VoucherPostingService.java`, `VoucherUnpostingService.java`, `VoucherReversalService.java`. Implementations under `backend/src/main/java/com/accounting/service/impl/voucher/`. Create `JournalEntryService` under `backend/src/main/java/com/accounting/service/gl/JournalEntryService.java`. Add new endpoints to existing `VoucherController.java`. Create `JournalEntry` entity under `backend/src/main/java/com/accounting/entity/JournalEntry.java`. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#system-architecture-alignment]
- **Frontend Structure:** Update existing `VoucherForm.tsx` to add Post/Unpost/Reverse buttons in the header toolbar. Update `VoucherList.tsx` to add reversal badge and disable delete for posted vouchers. Create error modal component for bulk validation errors (reuse patterns from Story 3.2). Add reversal badge component under `frontend/src/components/voucher/ReversalBadge.tsx`. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#frontend-modules]
- **API Endpoints:** Add new endpoints to existing `VoucherController`: POST `/api/v1/vouchers/{id}/post`, POST `/api/v1/vouchers/{id}/unpost`, POST `/api/v1/vouchers/{id}/reverse`. Update DELETE endpoint to block posted vouchers. Request/response formats follow established DTO patterns. Error responses use standard format with detailed error maps. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#rest-endpoints]
- **Database Migrations:** Create Flyway migration for `journal_entries` table with columns: id (UUID), voucher_id (UUID, FK), account_id (UUID, FK), period_id (UUID, FK), debit_amount (DECIMAL), credit_amount (DECIMAL), customer_id (UUID, nullable), supplier_id (UUID, nullable), cost_center_id (UUID, nullable), company_id (UUID), posted_at (TIMESTAMP). Add indexes: `journal_entries(voucher_id)`, `journal_entries(period_id, account_id, company_id)` for reporting queries. Update `vouchers` table to add `reversal_voucher_id` and `reversed_by_voucher_id` columns (nullable, FK to vouchers). [Source: docs/sprint-artifacts/tech-spec-epic-3.md#data-models-and-contracts] [Source: docs/architecture/data-architecture.md]

### Testing Strategy

- **Integration Testing:** Use `@SpringBootTest` with `IntegrationTest` base class for API endpoint tests, covering atomic posting, unposting, reversal, dependency checking, RBAC enforcement, company scoping, and validation error responses. Reference `VoucherControllerIntegrationTest` from Story 3.2 as a pattern. [Source: docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#senior-developer-review-ai]
- **Unit Testing:** Create unit tests for `VoucherPostingService`, `VoucherReversalService`, and `JournalEntryService` covering transaction atomicity, journal entry generation logic, reversal voucher creation, and double reversal prevention. Use JUnit 5 and Mockito. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- **Validation Testing:** Test bulk validation error collection - ensure all errors (period closed, Dr≠Cr, missing dimensions, leaf-only violations) are returned at once, not sequentially. Test field-level error map format matches Story 3.2 format. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]
- **Edge Case Testing:** Test concurrent posting attempts (race conditions), double reversal attempts, unposting with dependencies (placeholder for Epic 4-5), posting to closed period, posting unbalanced voucher. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary]

### References

- docs/sprint-artifacts/tech-spec-epic-3.md#story-33-posting-unposting--reversal-workflows
- docs/sprint-artifacts/tech-spec-epic-3.md#voucher-posting-workflow
- docs/sprint-artifacts/tech-spec-epic-3.md#voucher-reversal-workflow
- docs/sprint-artifacts/tech-spec-epic-3.md#rest-endpoints
- docs/sprint-artifacts/tech-spec-epic-3.md#data-models-and-contracts
- docs/sprint-artifacts/tech-spec-epic-3.md#system-architecture-alignment
- docs/sprint-artifacts/tech-spec-epic-3.md#security--multi-tenancy
- docs/sprint-artifacts/tech-spec-epic-3.md#reliabilityavailability
- docs/sprint-artifacts/tech-spec-epic-3.md#test-strategy-summary
- docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md
- docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#completion-notes-list
- docs/sprint-artifacts/3-2-voucher-form-create-edit-line-item-engine.md#senior-developer-review-ai
- docs/epics/epic-3-voucher-engine-general-ledger-core.md#story-33-posting-unposting--reversal-workflows
- docs/architecture/data-architecture.md
- docs/architecture/security-architecture.md

## Change Log

- 2025-11-15: Initial draft created with acceptance criteria, task plan, structural alignment guidance, and learnings from Story 3.2.

## Dev Agent Record

### Context Reference

<!-- Path(s) to story context XML will be added here by context workflow -->

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

