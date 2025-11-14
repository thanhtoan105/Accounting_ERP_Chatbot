# Epic 3: Voucher Engine & General Ledger Core

**Expanded Goal:**
Enable end-to-end voucher entry, double-entry journal logic, posting/unposting, stringent validations, audit trail, enforcement of "leaf-only" posting, and period management for compliant reporting.

```markdown
**Story 3.1: Voucher List and Search**
As an accountant, I want to see, filter, and search all vouchers for my company, so that I can quickly locate, review, and take action on transactional documents.
**Acceptance Criteria:**
1. Table shows columns: Voucher #, Date, Type, Amount, Status, Entered by, Posted by, AR/AP entity, Reversal badge, Attachment count.
2. Filters, query text, and sort order persist for each user/company session.
3. "Live" badges for count of draft vs posted vouchers.
4. Fuzzy/text/Unicode search on number, description, and supports Vietnamese terms.
5. Sort by multiple columns (e.g., date DESC + status).
6. Delete only allowed for unreferenced drafts, with mandatory reason captured in audit log.
7. Lazy loading/pagination (infinite scroll optional, MVP OK with page)
8. "No vouchers" state and UI guides users to create or adjust filters.
9. API error recovery: retry button, error details in toast/modal, printable JSON for support.
10. RBAC: non-admins see only their company's vouchers; user filters may be scoped by department/role if configured.
**Prerequisites:** Epic 2
```

```markdown
**Story 3.2: Voucher Form (Create/Edit) – Line Item Engine**
As an accountant, I want to create and edit vouchers with line items, so that journal entries are always captured with full detail, accuracy, and compliance.
**Acceptance Criteria:**
1. Tab/keyboard navigation through header/line grid; auto-add line on last field tab-out.
2. Inline error indicators and tooltips for any required field or dimension.
3. Date picker: disables non-open/closed periods, jumps to latest open period.
4. Currency set to VND, read-only in MVP/hidden on form.
5. Row reordering (drag/drop); hotkeys for duplicate/delete/move.
6. Invalid/incomplete lines saved as draft, clearly marked—cannot post until fully valid.
7. List supports 20+ lines with smooth rendering and on-the-fly entry (target: 20 lines < 60s for QA).
8. Attachments (see 3.7) allowed before and after draft save; in-line error for type, size, or virus scan error.
9. "Save draft" is optimistic and tolerant of browser close/crash; clears edit lock if session lost >5 min.
10. Undo supports row/cell revert, persists on draft save.
11. API returns precise error map for all field validation failures (not just generic 400).
**Prerequisites:** Story 3.1
```

```markdown
**Story 3.3: Posting, Unposting & Reversal Workflows**
As an accountant or chief, I want to post, unpost, or reverse vouchers with full double-entry validation, so that books remain consistent and errors can be properly corrected.
**Acceptance Criteria:**
1. Posting changes status atomically, returning updated voucher and generated GL entries.
2. Unposting checks all dependencies; blocks with error popup if referenced (e.g., "cannot unpost – referenced in payment #P123").
3. Reversal auto-creates voucher (REV-{linked}), status "posted", links bi-directionally; badge on both.
4. "Reversed by" badge is clickable on original and links to reversal voucher.
5. Export voucher and reversal trail as PDF with barcode/QR.
6. Double reversal not allowed (UI/API blocks and logs); 409 error on attempt.
7. Deletion of posted voucher forbidden; all attempted deletions logged as blocked in audit.
8. All failed posting (period closed, Dr≠Cr, dims missing) blocks and all errors shown at once.
9. Batch posting/import: any error aborts batch and logs all issues/results.
**Prerequisites:** Story 3.2
```

```markdown
**Story 3.4: Leaf-Only and Double-Entry Validation Engine**
As a user, I want the system to enforce leaf-only and double-entry checks on all vouchers, so that input errors are prevented and compliance is assured.
**Acceptance Criteria:**
1. UI disables and API blocks non-postable (parent) accounts, with audit log for blocked attempt.
2. Dr/Cr must always sum using BigDecimal; rounding logic documented.
3. Negative Dr/Cr values blocked, and attempt logs "possible fraud" and admin alert.
4. Required-dimension (e.g., customer, project) engine must be company/config-driven and all errors shown at once.
5. Bulk validation for all failed lines; QA test cases include field-level errors for all bulk/single paths.
**Prerequisites:** Story 3.2
```

```markdown
**Story 3.5: Audit Trail for Voucher Lifecycle**
As an auditor or admin, I want a cryptographically hashed, tamper-proof log of every change to vouchers, so all edits and actions are legally defensible and reviewable.
**Acceptance Criteria:**
1. Every voucher event (create/edit/post/reverse/unpost/import) generates JSON snapshot, diff hash, user/role, and device/IP.
2. Mass/batch actions log voucher IDs, stats, start/end time, and details.
3. "Voucher history" view shows colored field-by-field diff and plain English summary of changes.
4. Full audit logs exportable (PDF/JSON), with hash watermark.
5. Admin/audit dashboard for mass/blocked/suspicious actions; alerts sent on apparent fraud or abuse.
**Prerequisites:** Stories 3.1, 3.2, 3.3
```

```markdown
**Story 3.6: Period Selector & Voucher-Period Mapping**
As a user, I want the period picker to control voucher scope and prevent posting in closed/future periods, so that reporting and closing flows are always consistent.
**Acceptance Criteria:**
1. Always-visible period selector on voucher screens (shows current, 3 prior/next open if allowed).
2. No create/post in closed/future period; API 400 and UI error with log if attempted.
3. On period close, batch-lock all vouchers, audit event for who/when/hash/note.
4. Period reopen: reason/approval, audit all attempts (even if not approved).
5. System automates required reversal of prior-period adjustments by creating offsetting entry in next open period.
6. Period summary dashboard badge shows closing/posting flows and any pending actions.
**Prerequisites:** Stories 3.1, 3.3
```

```markdown
**Story 3.7: Attachments and Voucher Documentation**
As an accountant, I want to upload and manage voucher attachments for compliance, so that all supporting documentation is always available, secure, and auditable.
**Acceptance Criteria:**
1. Drag-and-drop uploader, inline image/PDF preview, download for all types; unsupported filetypes blocked and logged.
2. Attachments stored externally with randomized file names and metadata in DB; access limited by company.
3. Each download/view/delete logged with user, time, IP.
4. Delete allowed only for draft and by creator/admin; requires confirm modal and reason.
5. Download links signed/expiring (token, 10 min expiry).
6. Trigger simulated virus scan; block type if fails.
7. Voucher icon always shows current attachment count; click for manage modal.
8. Manage uploads for size/multi-part upload, robust to network error.
**Prerequisites:** Stories 3.2, 3.3
```
