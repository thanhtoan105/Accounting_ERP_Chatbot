# Accounting Platform – Detailed Epic Breakdowns

## Table of Contents
- [Accounting Platform – Detailed Epic Breakdowns](#accounting-platform--detailed-epic-breakdowns)
  - [Table of Contents](#table-of-contents)
  - [Epic 1: Project Foundation \& Secure Authentication](#epic-1-project-foundation--secure-authentication)
  - [Epic 2: Master Data Management](#epic-2-master-data-management)
  - [Epic 3: Voucher Engine \& General Ledger Core](#epic-3-voucher-engine--general-ledger-core)
  - [Epic 4: Accounts Payable (AP) Module](#epic-4-accounts-payable-ap-module)
  - [Epic 5: Accounts Receivable (AR) Module](#epic-5-accounts-receivable-ar-module)
  - [Epic 6: Cash \& Bank Management](#epic-6-cash--bank-management)
  - [Epic 7: Reporting Engine – Core Financials](#epic-7-reporting-engine--core-financials)
  - [Epic 8: BI Dashboard \& Analytics](#epic-8-bi-dashboard--analytics)
  - [Epic 9: AI RAG Chatbot \& Contextual Help](#epic-9-ai-rag-chatbot--contextual-help)
  - [Epic 10: Administration \& Utilities](#epic-10-administration--utilities)

---

## Epic 1: Project Foundation & Secure Authentication

**Expanded Goal:**
Set up a secure, scalable full-stack foundation with robust authentication, multi-tenancy, company/user management, and developer/devops tooling to enable all subsequent module builds and deployments.

```markdown
**Story 1.1: Initialize Project Repositories & DevOps**
As a developer,
I want to have a clear project structure, development tooling, and CI/CD baseline,
So that all team members can build, test, and deploy reliably from day one.
**Acceptance Criteria:**
1. Repository structure for Spring Boot (backend), React (frontend), and infra/config (docker, scripts) is clearly documented.
2. Docker Compose can start all dev dependencies: Supabase/Postgres (with dev schema seeded), Maildev (for password reset tests), Redis (if used).
3. Pre-commit hooks for linting/format; clear README for local setup/env vars/troubleshooting.
4. GitHub Actions: build/test/lint/publish for each module.
5. Health check endpoint `/health` returns 200 OK + info.
6. OpenAPI/Swagger UI at `/api/docs` with baseline template.
**Prerequisites:** None.
```

```markdown
**Story 1.2: Company Bootstrap & Multitenancy**
As an admin,
I want to initialize and configure one or more companies with isolated data and branding,
So that the system cleanly supports multiple tenants from the start.
**Acceptance Criteria:**
1. First login (or CLI/script) prompts to create the first company; reject duplicate company codes.
2. Company form validates: unique 10-digit tax code, company name (VN charset), address, logo.
3. All core data (users, chart of accounts, vouchers, master data) associated with a `company_id` and enforced at the DB and API layer.
4. Demo company auto-created with realistic data for preview/demo
5. Switching company context (if user belongs to multiple) works without session corruption.
6. Company branding (logo, name) appears in dashboard shell and voucher/report exports.
**Prerequisites:** Story 1.1
```

```markdown
**Story 1.3: User Registration & Secure Authentication**
As a user/admin,
I want secure registration, login, and account security features,
So that I can trust platform access is safe and compliant.
**Acceptance Criteria:**
1. Signup form (email, password, full name); shows password policy and enforces complexity.
2. Backend: password stored using Argon2 or Bcrypt; never logged/stored in plaintext.
3. JWT issued on login; access token short-lived, refresh token long-lived.
4. Session uses HttpOnly, Secure cookie setup (works with HTTPS locally/in prod).
5. "Remember me" persists session up to 30 days using refresh tokens/cookies.
6. "Forgot password" sends secure email from Maildev/Supabase, supports token expiration/reset link.
7. Repeated failed login attempts lock out account for 5 minutes and log IP/user agent.
8. Audit trail logs all logins, failed attempts, resets.
**Prerequisites:** Stories 1.1, 1.2
```

```markdown
**Story 1.4: Role-Based Access Control (RBAC)**
As an admin,
I want to define roles and enforce permissions,
So that API/UX only exposes functions allowed by user's assigned role.
**Acceptance Criteria:**
1. User entity includes a `role` field (`admin`, `accountant`, `chief_accountant`, `cfo`).
2. Permission matrix defined and documented (API + UI): which endpoints/resources each role can access.
3. Middleware checks JWT and role on each API request; 403 returned with context-aware error message.
4. UI hides menu items/screens based on role, but API rejects unauthorized backend access regardless of UI.
5. All role/permission changes are logged in audit trail.
6. Changing one's own role is not permitted (only admin/chief can edit others).
**Prerequisites:** Stories 1.2, 1.3
```

```markdown
**Story 1.5: User & Profile Management**
As an admin,
I want to list, filter, create, edit, or deactivate users and assign roles,
So that I can manage security and onboard/change staff.
**Acceptance Criteria:**
1. User listing supports filtering by role, status, and search by email/name.
2. Create/edit user form validates all fields; cannot duplicate email.
3. Deactivated users cannot log in (and are visually separated).
4. "Reset password" flow allows admin to send an email to users; link expires after 30 minutes.
5. Profile screen allows user to edit their own name, view effective role, change password.
6. Role badge displayed on each user in lists and details.
7. User creation, edit, activation, deactivation, and role changes are all audit-logged with who/when.
**Prerequisites:** Stories 1.2, 1.4
```

```markdown
**Story 1.6: Company Settings**
As an admin,
I want to configure company details, logo, address, tax code, and fiscal year,
So that branding appears correctly and platform has legal entity details for all outputs.
**Acceptance Criteria:**
1. Company Settings screen: logo upload (image size/type validated), name, address, tax code (unique check), fiscal year start, contact info.
2. Changes previewed before saving; logo scaled for dashboard header and reports.
3. Validation: no blank fields, VN address requirements, unique company/tax code enforced.
4. All changes to company settings written to audit log with old/new value, who, and when.
5. Display company logo and legal info in the print/export footer of each financial report.
**Prerequisites:** Story 1.2
```

```markdown
**Story 1.7: MVP Branding & App Layout**
As a user,
I want a branded, professional shell and responsive layout,
So that the UI is clear, trustworthy and usable for critical flows.
**Acceptance Criteria:**
1. Branded login page: logo, color scheme based on company, support for dark mode.
2. Authenticated layout has persistent sidebar, header (user avatar), and theme.
3. Logged-in username, company, and effective role always visible.
4. Professional, unobtrusive design (MUI tokens); all screens handle loading/error states.
5. Responsive to 1920x1080 and 1366x768; tablet screens at least usable for critical flows.
**Prerequisites:** Stories 1.2, 1.6
```

```markdown
**Story 1.8: Admin Demo Data Seed**
As a developer,
I want to bootstrap a demo company, users, and chart of accounts for tests,
So that everyone can develop, demo, and QA flows instantly.
**Acceptance Criteria:**
- CLI/dev command populates demo company, sample users (1/admin, 1/accountant, 1/chief, 1/cfo), and minimal chart of accounts.
- Demo data covers most mainline flows ("happy path").
- Clear, tested rollback (data reset, truncate tables); instructions in README.
- Demo company marked as [DEMO] in UI and audit logs.
**Prerequisites:** Stories 1.1, 1.2
```

---

## Epic 2: Master Data Management

**Expanded Goal:**
Enable precise, compliant master data management for all legal entities, customers, suppliers, and bank/cash accounts, with robust import/export flows, auditability, and baseline data integrity, to ensure all transactional and reporting modules operate with clean, well-governed reference data.

```markdown
**Story 2.1: Chart of Accounts (COA) – TT200 Preload & Read-Only Management**
As a chief accountant/admin, I want the TT200 COA to be preloaded, viewable in a tree, and protected from accidental edits so all postings are mapped correctly from day one.
**Acceptance Criteria:**
1. Full TT200 COA (≥154 accounts), seeded on initial migration; re-usable as import for fresh companies.
2. Each account has: code, name (VN, accents allowed), type (Asset, Liability, ...), normal side (Debit/Credit), postable flag, parent (nullable), and ordering position.
3. Tree view: Expand/collapse to 3 levels minimum; shows full account hierarchy and allows navigation (keyboard and mouse).
4. UX: Select/search input with typeahead for account code or name; unaccented search must match accented field reliably.
5. Filtering by account class (e.g., asset, income), postable-only toggle, and code prefix filter (e.g., all "131").
6. "View Details" modal must clearly display all account fields; "Postable" visually distinguished with tag or icon.
7. COA CRUD operations: Admin and chief accountant can create, update, soft delete, activate, and deactivate accounts via table UI with Sheet modal form. Table displays 6 columns: Account Code, Account Name, Account Type (Debit/Credit/Hermaphrodite), Account Name in English, Description, Status (In Use/Out of Use). Form includes 5 fields: Account Number (numeric, required), Account Name (required), Primary Account (optional parent), Account Type/Characteristic (required: "Debit Balance", "Credit Balance", "Hermaphrodite", "No Balance"), Description (optional). All operations require RBAC (admin/chief_accountant roles) and are logged in audit.
8. Only postable-leaf accounts appear in voucher account pickers. Root accounts cannot be added to vouchers (validation both client and server).
9. API endpoint for COA returns hierarchy, allows filter by postable, code prefix, and parent–child relationships for cascading selectors.
10. No two accounts can ever have the same code per company. Attempt to add/seed duplicate code must fail with detailed error message.
11. "Balance" column available in COA tree (optional for MVP), showing real-time balance for that account in the current period.
**Prerequisites:** Epic 1.
```

```markdown
**Story 2.2: Customer Master Data Management (CRUD)**
As an accountant,
I want robust CRUD for all customers, including search/filter, import/export,
So that AR tracking, validations, and compliance are always real-time accurate.
**Acceptance Criteria:**
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
**Prerequisites:** Story 2.1.
```

```markdown
**Story 2.3: Supplier Master Data Management (CRUD)**
As an accountant,
I want robust CRUD for all suppliers with import/export,
So that AP tracking, validations, and integration to bills/payments is seamless.
**Acceptance Criteria:**
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
**Prerequisites:** Story 2.2.
```

```markdown
**Story 2.4: Bank Account Management (CRUD)**
As an accountant, I want to manage cash and bank accounts for all cash receipt/payment flows.
**Acceptance Criteria:**
1. List view shows all cash and bank accounts; enables filtering by type/status, sort by last balance update.
2. "Add" form: Account number (required, must be unique), bank name (validated from list, open entry allowed), opening balance (must match accounting entry at migration).
3. On edit, audit trail logs all changes with reason if field value changed.
4. Cannot delete bank/cash account if referenced in any voucher, period, or reconciliation.
5. Closing (inactivating) an account removes it from pickers, but does not break referential integrity with historical vouchers.
6. Tooltip on bank account selector shows current and prior period balances.
7. Export list of accounts and balances to Excel for audit.
8. Multi-company: Accounts scoped to company_id.

**Prerequisites:** Stories 2.1, 2.2
```

```markdown
**Story 2.5: Company Settings Expansion (Advanced Fields)**
As an admin,
I want to update legal/fiscal settings and reporting options with audit,
So that regulatory compliance and custom numbering/logos are always traceable.
**Acceptance Criteria:**
1. Fiscal year is pre-selected on company creation but can be edited (future years only, no overlap allowed).
2. Currency is VND (locked/read-only in MVP).
3. Admin can set up VAT rates for invoices/bills; must validate unique % per company.
4. Document sequences: optionally editable; cannot change to a code that causes a collision with existing records.
5. Report export config: logo, footer lines—shows preview of financial statements with these values before saving.
6. All changes are transactional: field edit either succeeds fully with all validations or fails leaving old values untouched.
7. Full audit trail on any change.
**Prerequisites:** Story 2.4
```

```markdown
**Story 2.6: Data Import & Migration**
As a developer/admin,
I want to import opening balances, customer/supplier lists, and validate migration for smooth project cut-over,
So that initial rollout or bulk ops never lose compliance or leave orphans.
**Acceptance Criteria:**
1. Import wizard accepts only template files (Excel/CSV); checks that required headers and data types are present.
2. Validator parses all rows before saving: highlights errors and summary after validation (e.g., "Row 12: Invalid tax code, Row 25: Duplicate supplier").
3. On commit, bulk creates master records within transaction; if any row fails, no partial import occurs.
4. Opening balance import only available to admin, only before first period close. Enforces Dr = Cr invariant and warning if negative or zero-value entries.
5. All import errors downloadable as report for offline review.
5. Migration script logs result: inserted count, skipped, errors, and reference to audit trail.
6. "Demo import" uses sample data compatible with dev demo company.
**Prerequisites:** Story 2.1, 2.2, 2.3
```

```markdown
**Story 2.7: Audit Trail & Data Integrity for Master Data**
As an admin/auditor,
I want full audit and orphan detection on all master records,
So that no silent/incorrect changes, failed deletes, or stale data ever persist.
**Acceptance Criteria:**
1. UI "Audit Log" per entity: shows all changes for that customer/supplier/account, filterable by date/user/action type.
2. Exposes audit log API for download/filtering (respecting role-based access).
3. Log format includes: record type, affected fields (old/new), user/email/role, datetime, IP/user agent if present.
4. Detects and logs failed modification attempts (e.g., user tried to delete protected account).
5. Data integrity check endpoint for admin: runs background job to check for orphaned master records across all entities (should return zero in normal state).
6. Bulk actions are split-out in logs for transparency (each import row has separate audit entry).
**Prerequisites:** Story 2.2, 2.3, 2.4, 2.5, 2.6
```

## Epic 3: Voucher Engine & General Ledger Core

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

## Epic 4: Accounts Payable (AP) Module

**Expanded Goal:**
Deliver end-to-end supplier bill, approval, and payment workflows with strict validations (Maker-Checker), linkage to master data (supplier, chart of accounts), automated VAT/bookkeeping, full compliance, and auditable period/role controls.

```markdown
**Story 4.1: Purchase Bills – Entry, Edit, and Draft Management**
As an accountant, I want to create, edit, and validate supplier bills, so that all AP data is accurate, well-documented, and easily retrievable.
**Acceptance Criteria:**
1. Supplier picker with typeahead/add; unique Bill No per supplier/year; disables edit if duplicate found.
2. Date: disables future/holidays; lock after post or period close.
3. Due date auto-calculated, editable in draft only.
4. Reference/description required; Unicode, 100 char limit.
5. Attachments: drag/drop, 10 files/20MB total; inline preview; deletion allowed for drafts only.
6. Line items: qty × price, positive only; description required; account enforced leaf/postable.
7. VAT: supports 0/5/10/exempt; badge/warning for 0%, sum check on header/lines.
8. Missing required dimension = error at save/post (UX pointer, blocks operation).
9. Draft autosave, creator-only edit/delete; undo/redo; recoverable by creator/admin.
10. Multi-error summary footer on save; duplicate supplier+bill/date blocks save/post.
11. Batch import: validated template, atomic save, downloadable error map, auto-add unknown supplier pending confirm.
12. Audit log for every create, edit, draft, import, delete attempt (who/when/diff).
**Prerequisites:** Epic 3
```

```markdown
**Story 4.2: Purchase Bill Approval Workflow (Maker-Checker)**
As a chief accountant, I want all sensitive/high-value bills to require a separate approver (maker-checker), so that compliance/risk controls are enforced for AP.
**Acceptance Criteria:**
1. Approval threshold is admin-configurable (default 20M VND).
2. If bill exceeds threshold or marked sensitive: auto-moves to 'Pending Approval'.
3. In-app/email notification to Chief Accountant (with backup/escalation after 48h/no action).
4. Approver ≠ creator enforced by system.
5. Approver UI: Approve/Reject (with reason mandatory on reject), all attachments/history shown.
6. All workflow state transitions (draft→pending→posted/rejected) logged and visible in bill audit.
7. On approval, mark "Posted by" as approver.
8. Approval after period close disabled; error on attempt, logged.
9. If workflow not triggered: auto-approve, logs shadow "auto-approved" record.
10. All notification/rejection/status updates audit-logged.
**Prerequisites:** Story 4.1
```

```markdown
**Story 4.3: Cash Payments (Linked to Bills, Standalone)**
As an accountant, I want to enter, allocate, and track payments against supplier bills or ad-hoc, so that AP balances and actual outflows are transparent and controlled.
**Acceptance Criteria:**
1. New payment form: supplier picker lists only with open/unpaid bills, supports batch/link.
2. Allows multiple bills per payment: allocates via FIFO by default, allows override/modification before post.
3. Required fields: date, auto-generated number, cash/bank account (shows balance), payee, amount, reference, payment proof (required if above-config threshold), method.
4. Disallows overpayment (enforces by current bill balance), error if attempted.
5. Standalone payment (advance/ad hoc): allowed for admin with warning tag.
6. Suggests "quick add" supplier if non-master; logs ad hoc tag.
7. Payment approval for over-threshold follows Story 4.2 workflow.
8. Sufficient balance confirmed for each payment; overdraft triggers warning/block (configurable).
9. Posts payment as voucher (Dr AP, Cr cash/bank); batch import allowed with atomic error reporting.
10. All audit rules as for voucher entry/posting apply.
**Prerequisites:** Stories 4.1, 4.2
```

```markdown
**Story 4.4: AP Aging and Overdue Alerts**
As an AP clerk or chief, I want to see a segmented AP aging report, spot overdue bills, and trigger follow-up, so cashflow risk is controlled real-time.
**Acceptance Criteria:**
1. Aging buckets: Current, 1–30d, 31–60d, 61–90d, >90d; per supplier.
2. Lists/badges overdue suppliers and bill totals; sort/filter by segment.
3. Drill-down from bucket → bill/payment history; filters by status/period.
4. Exportable to Excel/PDF with applied snapshot filters/criteria.
5. Dashboard badge: count of overdue payables and top overdue suppliers.
6. "Remind" triggers in-app/email, with audit log; batch send for escalated/due payables.
7. Alerts auto-notify relevant roles per schedule/config.
8. RBAC: CFO/Chief see all; AP clerk limited to assigned/own. All API/UI filtered by permissions.
9. Do not display/aggregate deleted/reversed bills; partial payments shown remaining only.
10. All alert, view, export events logged with initiator/user.
**Prerequisites:** Story 4.3
```

```markdown
**Story 4.5: Supplier Statement & Reconciliation**
As an accountant, I want to generate official supplier statements and support reconciliation for disputes/matching, so payables are resolved with confidence.
**Acceptance Criteria:**
1. Statement views: summary (by bill) and detailed (by payment/event).
2. Export as TT200-compliant PDF/Excel, includes hash and legal footer.
3. "Send to supplier" emails exported statement with delivery/audit logging.
4. Import supplier-provided statement: parses/compares items, flags mismatches/missing/applied, supports manual notes and adjustment vouchers.
5. History: all statements sent, viewed, exported stored per supplier; batch ZIP/email allowed (max size configured/audited).
6. Dispute log: reason/actioned/edited by AR/accountant; included in next export and in audit trail.
7. Attachments: batch download ZIP per-statement; logs every download/email.
**Prerequisites:** Story 4.4
```

```markdown
**Story 4.6: VAT Handling and Reporting**
As an accountant/auditor, I want accurate, validated VAT calculation, reporting, and audit for all AP bills and payments, so that statutory compliance is always maintained.
**Acceptance Criteria:**
1. Each line item requires VAT rate (company default, override with warning/audit); supports 0/5/10/exempt only.
2. Sum of VAT on doc must match total of line-level VAT; mismatch >1,000₫ blocks post.
3. TT200 GL mapping: AP bills auto-book VAT 3331 leg; system ensures legs balance by template logic.
4. Input VAT report by period/supplier/class; exports formatted to ND123 compliance.
5. Admin screen for manual VAT corrections, with diff and reason audit.
6. Negative or over-100% VAT ratio attempts blocked; triggers audit entry.
7. All VAT-related actions: create, override, correct events are fully audit-tracked (who/when/IP/old/new).
**Prerequisites:** Story 4.5
```

```markdown
**Story 4.7: Audit Trail and Compliance for All AP Activities**
As an auditor or chief accountant, I want a complete, filterable history of every AP event and a disaster recovery (DR) plan, so statutory and business compliance is bulletproof.
**Acceptance Criteria:**
1. Every bill/payment create, edit, post, approve, import, fail, delete-attempt produces detailed audit log (user, time, action, payload diff, hash).
2. Timeline view: colored by action type, exportable as PDF with legal appendix/hash.
3. Unauthorized/deletion attempts/abuse visible in admin/audit view; notification for abuse/repeat blocked operations.
4. DR plan: scheduled weekly backup/export of AP audits to external/secure/compressed archive.
5. Reviewer filter/export: by user, action, amount, supplier, attachment, or outcome.
6. All actions have cryptographic chain hash; 10+ years retention, GDPR purge on demand.
**Prerequisites:** All previous stories in Epic 4
```

## Epic 5: Accounts Receivable (AR) Module

**Expanded Goal:**
Deliver complete AR flows for invoicing, maker-checker approvals, receipt allocations, AR aging and reminders, statements/reconciliation, VAT handling, and comprehensive audit—ensuring accuracy, compliance (TT200/ND123), and traceability.

```markdown
**Story 5.1: Sales Invoice Entry, Edit, and Draft Management**
As an accountant, I want to create and edit sales invoices with strict validations and attachments, so that revenue recognition and receivables are accurate and auditable.
**Acceptance Criteria:**
1. Invoice form: Customer picker (typeahead/add), invoice number auto-generated (per customer/period), date (must be open period), due date (default net 30), reference text, VND currency.
2. Line items: description required, quantity and unit price positive, VAT%, revenue account (leaf), item/service if required by account rules.
3. Total and subtotals auto-calculated; inline validation on all required fields and account leaf rule.
4. Save as draft at any time; autosave and undo/redo; only creator/admin can edit/delete drafts.
5. Attachments: drag/drop, preview, delete allowed only on drafts; size/type checks.
6. Duplicate prevention: same customer + invoice number/date pair blocked at save/post.
7. Import (CSV/Excel): atomic, template-validated, row-level error map available for download.
8. Audit log records all create/edit/post/import/delete attempts with before/after diff and actor.
**Prerequisites:** Epic 4
```

```markdown
**Story 5.2: Invoice Approval Workflow (Maker-Checker)**
As a chief accountant, I want threshold-based approval for invoices, so that high-value or sensitive revenue documents are reviewed before posting.
**Acceptance Criteria:**
1. Threshold configurable by admin (default 100M VND) and rule-based sensitivity flag.
2. Above-threshold or sensitive invoices route to "Pending Approval" with in-app/email notifications to Chief Accountant.
3. Approver must differ from creator; violation attempts blocked and logged.
4. Approver UI shows invoice, attachments, change history, and projected AR impact.
5. Approve posts invoice; Reject requires reason and returns to draft with message to creator.
6. Approval after period close is disabled; attempts logged.
7. If workflow not triggered: auto-approve; shadow "auto-approved" record stored in audit.
8. All transitions (draft→pending→posted/rejected) fully audit-logged.
**Prerequisites:** Story 5.1
```

```markdown
**Story 5.3: Customer Payment Receipts (Linked Receivables, Standalone Entry)**
As an accountant, I want to record receipts and allocate them to invoices (including partials and advances), so that AR balances and customer statements are correct.
**Acceptance Criteria:**
1. Receipt form: customer picker filters to customers with open invoices; date/number auto; cash/bank account; amount; reference; attachment; method.
2. Allocation UI: select one/many invoices; supports partial/prorated allocations; prevents overpayments; shows remaining per invoice.
3. Standalone receipts (advances/on-account) allowed; can later match to invoices.
4. Posting entries: Dr Bank/Cash, Cr AR (131) with all configured dimensions.
5. Reversal path: generates linked reversal voucher; keeps both vouchers cross-linked and audit-tagged.
6. Import receipts: atomic, template-based; returns detailed error map with row numbers.
7. Full audit on create/edit/post/reverse/import, including allocation changes.
**Prerequisites:** Stories 5.1, 5.2
```

```markdown
**Story 5.4: AR Aging Report and Overdue Alerts**
As an AR clerk or chief accountant, I want a clear aging report and automated reminders, so that late collections are addressed promptly and cashflow risk is reduced.
**Acceptance Criteria:**
1. Buckets: Current, 1–30d, 31–60d, 61–90d, 91+d; by customer; totals and running balances shown.
2. Drill-down from any cell to invoice list with paid/remaining and last-payment details.
3. Export to Excel/PDF with snapshot timestamp and active filters displayed on the export.
4. Overdue badges on dashboard with counts and top overdue customers list.
5. Reminder actions: trigger in-app/email reminders (single/batch); configurable schedule (pre-due, due, +7d cadence).
6. RBAC: CFO/Chief see all; AR clerk sees assigned scope; API filters enforce permissions.
7. Exclude reversed/voided invoices; show partial payments as remaining only.
8. All alerts, views, and exports recorded in audit.
**Prerequisites:** Story 5.3
```

```markdown
**Story 5.5: Customer Statement & Reconciliation**
As an accountant, I want to generate and distribute customer statements and reconcile discrepancies, so that balances are agreed and disputes are documented.
**Acceptance Criteria:**
1. Statement view: summary (one row per invoice) and detailed (including receipts/credits) with running balance.
2. Export: PDF/Excel; includes legal footer and hash for audit; per-customer and batch ZIP exports.
3. "Send to customer" emails statement and records delivery/view events.
4. Import customer-provided reconciliation: parse/compare, flag mismatches/unapplied items, suggest adjustments; color-coded dispute tracking.
5. Maintain statement history and dispute log with reasons/actions; include notes in subsequent exports.
6. Attachments: optional ZIP per statement; every download/email logged with user/time/IP.
**Prerequisites:** Story 5.4
```

```markdown
**Story 5.6: Revenue & VAT Handling**
As an accountant/auditor, I want correct revenue and output VAT accounting and reporting, so that statutory filings and financial statements are accurate.
**Acceptance Criteria:**
1. Each invoice line has VAT rate (default from settings, override with warning); supports 0/5/10/exempt only.
2. GL splits on post: Dr AR (131), Cr Revenue (511+), Cr VAT Output (3331); rounding rules applied consistently.
3. Totals validation: header VAT equals sum of line VAT and GL VAT; block post if mismatch beyond tolerance.
4. Credit notes/negative invoices supported; must reference original; all linked with audit cross-references.
5. Output VAT report: filter by period/customer/VAT class; Excel export in ND123 format.
6. Admin VAT corrections allowed with reason and diff audit; overrides require approval if above threshold.
7. API enforces idempotent posting and blocks double-booking.
**Prerequisites:** Story 5.5
```

```markdown
**Story 5.7: Audit Trail and Compliance for All AR Activities**
As an auditor or chief accountant, I want complete, filterable AR audit logs with long-term retention, so that internal and statutory audits are efficient and defensible.
**Acceptance Criteria:**
1. Every create/edit/post/approve/reverse/import action produces an audit entry with diff, actor, device/IP, and event hash.
2. Voucher/invoice/receipt histories show chronological diffs and human-readable summaries.
3. Export of AR audits to PDF/JSON with hash signature; blocked/unauthorized actions flagged and alerted.
4. Scheduled backups include AR audit logs; external copy available for DR/review.
5. Reviewer export: filter by customer/period/user/action; compute aggregates; default retention 10y with GDPR purge process.
**Prerequisites:** All prior stories in Epic 5
```

## Epic 6: Cash & Bank Management

**Expanded Goal:**
Provide robust cash/bank account master data, receipts/payments entry, cash/bank books with running balances, statement-based reconciliation, and complete compliance/audit—so cash movements are accurate, controlled, and fully traceable.

```markdown
**Story 6.1: Cash/Bank Account Management (CRUD & Security)**
As an accountant/admin, I want to create, edit, and inactivate cash/bank accounts with validations, so that downstream transactions use only valid accounts and history remains intact.
**Acceptance Criteria:**
1. Fields: account name, bank name, account number (required, unique per company), branch, type (cash/bank), opening balance, currency (VND, fixed for MVP).
2. Account number uniqueness enforced at DB and API; friendly error shows conflicting account.
3. Inactivation removes account from pickers but preserves historical links/balances and does not allow reactivation without audit trail.
4. Delete blocked if referenced in any voucher/reconciliation; attempted deletes show blocking modal with reference count/examples and produce audit log with reason.
5. List supports filter by type/status, search by name/number/bank, sort by balance/last activity; export list to CSV/Excel with applied filters in header.
6. Account picker displays only active entries with tooltip (current balance, last TX date, last reconciled statement date).
7. RBAC: only admin/chief may create/inactivate; accountant may view and select; all permissions enforced server-side.
8. Every create/edit/inactivate attempt logged (who/when/old/new/IP); blocked attempts logged distinctly with reason and message code.
9. Import accounts (optional): template-validated, atomic; duplicates reported with line numbers; audit each row created.
10. Opening balance lock: once first period closes, opening balance editing is disabled; any change requires admin override with reason and audit.
**Prerequisites:** Epic 5
```

```markdown
**Story 6.2: Cash Receipt Entry & Posting**
As an accountant, I want to record cash/bank receipts and post them to GL, so that inflows are accurately captured and reconciled.
**Acceptance Criteria:**
1. Receipt form: date, auto number, payer (customer/other), reference, amount (>0), cash/bank account (active), method (cash/transfer), attachment (optional).
2. Optional AR allocation: invoice list with remaining balances; supports partial allocations; prevents over-collection; shows before/after remaining per invoice.
3. Validations: period must be open, account must be active, amount positive, required dimensions enforced by account rules (e.g., customer required when posting to 131).
4. Post creates Dr Cash/Bank, Cr AR (131) or Other Income (711) per selection; rounding and decimal precision consistent with GL engine.
5. Reversal creates linked reversing voucher; both vouchers cross-linked; reversal badge on both; reasons required for reversal and audit-logged.
6. Batch import: CSV/Excel template with headers; atomic; error report includes row number, field, message; partial allocations supported in import with stable IDs.
7. UI performance target: create and post a simple receipt in ≤ 10 seconds after data entry; latency logged to performance telemetry.
8. Attachments: allow up to 10 files/20MB total; preview images/PDF; virus scan simulated; blocked types rejected with user-friendly message.
9. RBAC: accountants can create/post up to threshold; over-threshold requires maker-checker approval; all checks enforced server-side.
10. All actions (create/edit/post/reverse/import/download) generate audit entries with payload diffs and event hash; blocked attempts are flagged.
**Prerequisites:** Story 6.1
```

```markdown
**Story 6.3: Cash Payment Entry & Posting**
As an accountant, I want to record cash/bank payments and allocate them to AP bills or expenses, so that outflows are controlled and correctly posted.
**Acceptance Criteria:**
1. Payment form: date, auto number, payee (supplier/other), reference, amount (>0), cash/bank account (active), method, attachment (required above threshold).
2. AP linkage: select one or more unpaid/part-paid bills; default allocation FIFO; allow manual override; show running remaining per bill and overall.
3. Prevent overpayment; show blocking error listing bills exceeded; require adjustment or removal to proceed.
4. Standalone payments to expense accounts permitted per policy; if payee not in master data, show admin-only quick add or require reason for ad-hoc payee with audit tag.
5. Posting creates Cr Cash/Bank, Dr AP (331) or expense (6xx/8xx) per lines; all required dimensions enforced.
6. Balance guard: sufficient cash/bank balance check (configurable block/warn) before posting; overdraft attempt logged and optionally disallowed.
7. Batch import supported with template; atomic; row-level error map; attachments mapped by filename; large file handling with progress.
8. Maker-checker: above threshold or sensitive payments route to approval; approver ≠ maker enforced; notifications sent; full audit of state transitions.
9. UI/UX: quick keyboard entry; performance target to post a simple payment in ≤ 10 seconds post-data entry.
10. All actions fully audit-logged; reversal path mirrors receipts with linked reversing voucher and mandatory reason.
**Prerequisites:** Story 6.2
```

```markdown
**Story 6.4: Bank Book / Cash Book View & Running Balances**
As a finance user, I want to view cash/bank books with running balances and drill-down, so that I can analyze movements and verify balances.
**Acceptance Criteria:**
1. Per account: filters by date/type/reference; columns show opening balance, inflow, outflow, closing; running balance after each TX with signed values.
2. Drill into any row to voucher detail with attachments; show posting user and timestamps; highlight negative balances with color and tooltip.
3. Multi-account view yields aggregated totals; toggle grouping by account; quick switcher between bank/cash; remembers last view per user.
4. Export to Excel/PDF; include company branding/logo, filter snapshot, timestamp, generated-by; file footer includes document hash.
5. Pagination or virtual list for >5k rows; download full dataset asynchronously with notification when ready; rate-limited to prevent abuse.
6. RBAC enforced for all queries/exports; attempts to export outside role scope fail and are audit-logged.
7. Performance: initial load ≤ 2s for typical month (≤5k TX) with indexed queries; slow queries logged.
8. All views/exports logged in audit with filters, user, IP, hash of snapshot data.
**Prerequisites:** Story 6.3
```

```markdown
**Story 6.5: Manual Bank Reconciliation**
As an accountant, I want to reconcile bank statements to ledger transactions, so that differences are identified and bank balances are certified.
**Acceptance Criteria:**
1. Import statement (CSV/Excel) with column mapping wizard (date, amount, ref, description); persistent format profiles per bank.
2. Duplicate import detection via file hash and date range overlap; friendly warning and block if duplicate; audit log includes file metadata.
3. Auto-suggest matches by date±N days (configurable), amount tolerance (configurable), and reference similarity; explain match reason in UI.
4. Manual match/unmatch with notes; retain unmatched list with reasons (timing, missing voucher, bank fee) and next-step tags.
5. Adjustment suggestions: create bank fee/interest vouchers directly from reconciliation UI with pre-filled values and required approvals.
6. Reconciliation summary shows matched count/value, unmatched count/value, and delta; export matched/unmatched reports to Excel/PDF.
7. Error handling: import errors list row numbers and reasons; partial import disallowed; user can download error file.
8. Audit: every file upload, parse, match/unmatch, adjustment, export logged with user/time/IP and hash; rollbacks traceable.
9. Reconciliation status per account/month stored and displayed (e.g., Not started/In progress/Completed) with last updated timestamp.
**Prerequisites:** Story 6.4
```

```markdown
**Story 6.6: Cash & Bank Audit and Compliance**
As a chief accountant/auditor, I want strong controls and audit trails over cash/bank activities, so that compliance and investigations are fully supported.
**Acceptance Criteria:**
1. Closed period protection: edits/deletes blocked; attempts show clear error and produce blocked-event audit with reason and user context.
2. Scheduled backup/export: weekly compressed export of accounts, books, reconciliations, and audit logs to external storage; restore requires approval and is fully audited.
3. Integrity checks: daily and period-close checks verify Dr/Cr parity and detect anomalies (duplicates, gaps); alerts to admin and logs with checklist.
4. Export controls: all PDF/Excel carry hash and signature block; watermarked DRAFT when period open; only admin/chief may export full book data.
5. Audit explorer: filter by account/date/action/user/type; export JSON/CSV/PDF; 10-year retention enforced; purge requires dual approval and reason.
6. Notification center integration: repeated blocked attempts or suspicious patterns trigger alerts/escalation.
7. Compliance logs include version of policies/rules active at time of action to preserve context for future audits.
**Prerequisites:** Story 6.5
```

## Epic 7: Reporting Engine – Core Financials

**Expanded Goal:**
Generate compliant core financial reports (S06-DN Trial Balance, B01-DN Balance Sheet, B02-DN Income Statement, B03-DN Cash Flow), support scheduling/exports, drill-down lineage, and comprehensive reporting audit.

```markdown
**Story 7.1: Trial Balance (S06-DN) Generation & Export**
As a chief accountant, I want to produce TT200-compliant Trial Balance (S06-DN) with drill-down, so that balances are reconciled and auditable.
**Acceptance Criteria:**
1. Period selector persists last-used period per user; future periods disabled; period must be open for DRAFT watermark to be present.
2. Columns per S06-DN: account code/name, opening Dr/Cr, period Dr/Cr, closing Dr/Cr; totals must satisfy sum(Dr)=sum(Cr) at report and period levels.
3. Data pulls only from posted GL; optional diagnostic toggle to include drafts (default off) requires admin and adds conspicuous banner; state recorded in snapshot.
4. Drill-down from any amount opens voucher list constrained by account/period and amount sign; supports pagination, sorting, and export of detailed list.
5. Voucher drill shows document lineage (voucher → GL lines → attachments) with breadcrumb back to S06; downloads signed and expiring.
6. Export PDF: TT200 layout, logo/footer/timestamp/hash; Excel/CSV export includes a header sheet with filters, user, generated timestamp, and data hash.
7. Performance: render ≤ 2s for up to 50k GL rows; for larger sets use streaming export path with progress and notification; slow query threshold logged.
8. Security: RBAC enforced at query and drill; signed URLs (≤7 days) for attachments/exports; all access logged with IP and user agent.
9. Validation preflight: blocks export if GL out-of-balance or period locks inconsistent; user sees actionable message and help link.
10. Snapshot reproducibility: storing mapping/version and parameters enables deterministic regeneration; snapshot hash verified on re-export.
**Prerequisites:** Epic 6
```

```markdown
**Story 7.2: Statutory Reports (B01-DN, B02-DN, B03-DN, F01) with TT200 Mapping**
As a CFO, I want statutory financial reports with transparent line mappings, so that filings are accurate and explainable.
**Acceptance Criteria:**
1. Reports: B01 (Balance Sheet), B02 (Income Statement), B03 (Cash Flow – direct), and F01 rendered per TT200 templates; headers include company legal details.
2. Mapping tables: each line item references account ranges and operators; mappings are versioned; edits require admin, reason, and produce an audit record with diff.
3. Tooltip/inline help: each line shows mapping explainer and example accounts contributing; drill to contributing accounts then to vouchers.
4. Period compare mode: current vs prior with absolute and % variance; toggles for hide zeros/immaterial lines with threshold (configurable).
5. Validation: report blocks export if any required mapping yields nulls or GL is unbalanced; error panel lists problematic lines and suggested fixes.
6. Export: TT200-styled PDF and Excel with mapping version and parameters embedded; DRAFT watermark for open periods; footer hash and manifest.
7. Performance/UX: partial render with skeletons; users can open sections while remaining sections compute; long runs offer background processing with notification.
8. Security: RBAC per company/role enforced; row-level constraints respected for departmental scoping if enabled.
9. Change management: mapping change log view with who/when/what and rollback to prior version; rerun affected snapshots on demand.
10. Reconciliation aids: link out to S06-DN for accounts contributing to specific lines; variance explanation mode highlights top drivers.
**Prerequisites:** Story 7.1
```

```markdown
**Story 7.3: Report Scheduling, Export Distribution, and Access Control**
As a finance lead, I want to schedule recurring report runs and manage recipients and permissions, so that reporting is timely and secure.
**Acceptance Criteria:**
1. Scheduling UI: create jobs (cron/monthly) with selected reports, period rules (e.g., last closed month), formats (PDF/Excel), and recipients (email list).
2. Access control: only users with report permissions can create/edit/cancel schedules; recipients must have access or receive access-restricted redaction (configurable).
3. Distribution: emails contain signed links (expire ≤7 days) and optional password-protected attachments; bounce/failure handling with retry/backoff.
4. Report Center: lists upcoming and historical runs with status, duration, actor, error logs; supports rerun and cancel with audit.
5. Idempotency: runs keyed by schedule+period to prevent duplicates; re-runs create new snapshot entries with linkage to original.
6. Snapshot immutability: each run stores parameters, mapping version, and data hash; any export references that immutable snapshot.
7. Failure management: partial failures captured with granular error records; configurable retry policy; notifications to owners and observers.
8. Audit: every schedule change, run, download, and email is logged with user, IP, and result; export manifest lists files and hashes.
9. Performance: queuing ensures concurrency control; SLA targets reported in Report Center; warns if cluster capacity risks deadlines.
10. Governance: schedules require owner; orphaned schedules identified and disabled automatically after grace period.
**Prerequisites:** Story 7.2
```

```markdown
**Story 7.4: Multi-Period Comparison & Variance Analysis**
As a CFO, I want configurable multi-period comparisons with variance analysis, so that I can understand trends and anomalies quickly.
**Acceptance Criteria:**
1. Compare 3–12 periods (month/quarter/year); variance columns show absolute and %; arrows colored by business rules; thresholds configurable.
2. Segmentation: filters by department, customer group, region where applicable; respects RBAC and data partitioning; redaction where user lacks scope.
3. Drill: from comparison cell to filtered base report; from there to accounts and vouchers; breadcrumbs and back-stack preserved.
4. Exports: PDF/Excel include all filters, variance method, and materiality thresholds; include manifest and hash.
5. Explanations: top drivers listing with contribution percentages; annotate cells with notes for month-end commentary; notes appear in exports.
6. Performance: cache most-recent periods with freshness indicator; cache invalidated on GL changes or mapping edits.
7. UX: sticky headers, horizontal scroll for many periods; keyboard navigation and accessibility compliant.
8. Data quality: cross-check variance math against base reports; automated test suite for edge cases (rounding/zeros/negatives).
**Prerequisites:** Story 7.3
```

```markdown
**Story 7.5: Reporting Audit, Compliance, and Integrity**
As an auditor, I want a full audit of report views/exports and chain-of-custody for snapshots, so that reporting is defensible and tamper-evident.
**Acceptance Criteria:**
1. Audit timeline: captures who/when/IP/UA, filters used, mapping version, snapshot hash, and actions (view/export/schedule/cancel/rerun).
2. File integrity: every exported file includes footer hash; batch exports include manifest JSON (filename, size, hash); manifest is signed.
3. Retention: closed-period snapshots auto-archived to WORM storage; default 10y retention; purge requires dual approval, reason, and generates a purge certificate.
4. Unauthorized attempts: blocked export/view triggers alerts and appear in security dashboard; repeated offenses escalate to admins/CISO.
5. Integrity checks: periodic job replays snapshots to verify reproducibility; differences flagged; mapping changes since snapshot are logged and linked.
6. Legal holds: mark snapshots as on-hold to prevent purge/alteration; hold history tracked with who/when/why.
7. Disaster recovery: periodic cross-region backup of report snapshots and audit logs; restore drills logged and reviewed.
**Prerequisites:** Story 7.4
```

```markdown
**Story 7.6: F01 Detailed Ledger (Sổ kế toán chi tiết) Report Implementation**
As a chief accountant or tax officer, I want to generate, review, and export a compliant F01 (Sổ kế toán chi tiết) report per TT200, so that subsidiary ledgers meet regulatory and audit needs for tax authorities.
**Acceptance Criteria:**
1. F01 report template(s) precisely follow TT200/ND2004/2014/BTC format; table headers/footers, required summary rows, and section signatures included by default.
2. User selects account(s) and, where applicable, subsidiary detail (e.g., customer, supplier, item, project, cost center); multi-selection of accounts allowed for batch export; all selection fields support search/filter.
3. Reporting period selection: single period or multi-period; disables future periods and warns if period not closed (show prominent DRAFT watermark).
4. Data extraction logic: only posted vouchers included, validate all required dimensions (e.g., Sổ chi tiết TK131 by customer, TK331 by supplier, etc.); catch and report missing dimension data as error/alert to user before export.
5. Main columns: date, voucher number, description, counterparty, amounts (debit/credit), running balance after each entry, reference to originating document (with drill-down link to voucher)
6. Drill-down enabled from any transaction line to the full source voucher, including audit history, attachments, and user info.
7. Export: Excel (TT200 sheet-protected, proper page break/signature, auto-fit, legal footers); PDF (TT200 layout, header/signature fields, system timestamp/hash; DRAFT if not closed); export includes applied filter summary.
8. Compliance: blocks export if GL is unbalanced or required dimension data missing; all view/filter/export actions audited with user/time/IP and export hash; supports re-export with immutable snapshot hash for audit trail.
9. Performance: load ≤2s for typical ledger (≤10k rows), supports progressive paging for large ledgers; export of up to 100k rows as streamed background job with notification when ready.
10. RBAC: Only roles authorized for given account/ledger can generate or export; attempts to access unauthorized ledgers/vouchers are blocked and logged as audit events with notifier option for admin/tax manager.
**Prerequisites:** Story 7.2
```

## Epic 8: BI Dashboard & Analytics

**Expanded Goal:**
Deliver a real-time, configurable BI dashboard and analytics suite with data pipeline integrity, drilldown, trends, widget personalization, forecasting, segmentation, and robust access control and audit for actionable financial monitoring.

```markdown
**Story 8.1: Real-Time Financial Dashboard Setup & Data Pipeline**
As a CFO or finance manager, I want a real-time dashboard with core widgets and reliable data, so that I have actionable visibility into current financial KPIs.
**Acceptance Criteria:**
1. Out-of-the-box widgets: Revenue vs Expenses, AR/AP balance, overdue counts, cash, top 5 debtors/creditors, summary for current/selected period.
2. Data ETL pipeline: job status, errors, last run, and row counts visible to admin; pipeline logs all runs, errors, refreshes with timestamp, user.
3. Data freshness: "Last updated" display, red/yellow/green badge (<5/30/>30min), manual refresh (button, permissioned, rate-limited), auto-refresh every 5m.
4. Missing data or failed widgets display error banner; export/interaction disabled until pipeline state is healthy.
5. All widget queries complete <2s (≤50k txns); slow/failed widget logs include widget, filter, query time, error.
6. RBAC: all widget and pipeline config visible/editable only by admin/chief; no cross-company/segment leaks.
7. All manual/auto refreshes and configuration changes audit-logged with old/new, user, IP, and job duration.
**Prerequisites:** Epic 7
```

```markdown
**Story 8.2: Widget Configuration & User Personalization**
As a user, I want to personalize, add, or rearrange dashboard widgets, so that my view is adapted to my role and preference for faster insights.
**Acceptance Criteria:**
1. Add/reorder/resize widgets via drag/drop; save layout per user (persisted server-side across devices).
2. "Add Widget" panel lists all available widgets, preview and subscribe/unsubscribe; reset dashboard to company/user defaults any time.
3. Role-based widget access: certain widgets only visible to admin/chief/finance, others available to all; enforced server- and client-side.
4. Widget-specific configuration: set filters, period, groupings per widget instance; each widget retains its own view.
5. Export current widget (table/chart) as CSV/PNG/Excel, each export writes to audit log.
6. Full accessibility: ARIA support, keyboard nav, color contrast (WCAG AA+ compliance).
7. All widget config/views/exports logged with user, timestamp, and delta vs previous settings.
**Prerequisites:** Story 8.1
```

```markdown
**Story 8.3: Drill-Down, Filtering, and Interactive Exploration**
As a user, I want to interactively filter and click into dashboard visualizations, so that I can perform root-cause and detailed analysis instantly.
**Acceptance Criteria:**
1. Charts/tables: All graph/table elements clickable; clicking refines dashboard filters or opens filtered drilldown modals (e.g., AR Ageing → invoice list).
2. Global dashboard filters: period, company, account, customer, department; filter selection applies to all widgets (unless overridden).
3. Segment/compare view: current vs previous period, by group/region/customer/top/bottom N; interactive legend filter.
4. Drill path: click from dashboard → report → voucher → attachment, with clear UI breadcrumbs and back.
5. Empty state: clear display and hints for fixing filters; widget-level error states have actionable help or retry link.
6. Deep links: user can copy/share dashboard URL with filters applied (only for same-permission users); RBAC checked server-side.
7. Keyboard navigation/click support for all drills and filters; session remembers current drill state/stack.
**Prerequisites:** Story 8.2
```

```markdown
**Story 8.4: Trends, Forecasts, and Predictive Analytics**
As a CFO or analyst, I want to view trends and run simple forecast scenarios for financial KPIs, so that I can anticipate and prepare for risks or opportunities.
**Acceptance Criteria:**
1. Time series widgets: next to each KPI show sparkline/mini chart with up/down indicator and percent change vs prior period; arrows colored by materiality.
2. Forecast widgets: simple linear or configurable short-term projections (next 30/60d), shown with "Forecast" badge and rationale.
3. Scenario planner: user can adjust forecast assumptions (planned income/expense), save/train, and revert; all simulation runs logged by user.
4. Anomaly detection: system flags KPIs out-of-tolerance compared to rules (configurable); badge and alert email to admin/chief.
5. All forecasts, anomalies, and user scenario runs tagged in audit log for review.
**Prerequisites:** Story 8.3
```

```markdown
**Story 8.5: Data Quality, Integrity, and BI Security**
As an admin/auditor, I want to be confident that all dashboard data is current, accurate, and compliant with our security controls, so that decisions are always made on trustworthy data.
**Acceptance Criteria:**
1. Widget data lineage popover shows last ETL run, source table/time, GL link and row count for each widget.
2. ETL: daily/periodic integrity self-check jobs; mismatches (e.g., Dr≠Cr/orphans) block dashboard, send alert to admin, and show widget-level banners.
3. Error banners: any widget with stale/missing/invalid data disables export/interactions, includes action link for details.
4. All pipeline runs, refreshes, and data integrity checks logged for audit with inputs/outcomes; dashboard audit explorer for BI reviewed events.
5. Never exposes data across company or roles in violation of RBAC; widget data scoped by company/user.
6. All dashboard and pipeline data/metadata backed up with defined retention (10y+); purge requires audit and dual approval.
**Prerequisites:** Story 8.4
```

## Epic 9: AI RAG Chatbot & Contextual Help

**Expanded Goal:**
Deliver an AI-powered Retrieval-Augmented Generation (RAG) chatbot tightly integrated with the accounting and documentation system. Enable secure, traceable contextual Q&A, semantic search over docs and ledgers, workflow assistance, feedback capture, and usage analytics—drives productivity, support, and regulatory compliance.

```markdown
**Story 9.1: Chatbot Widget Integration and Security**
As a user, I want an in-app chatbot widget that honors my role and data permissions, so I get secure, context-relevant help without leaving my workflow.
**Acceptance Criteria:**
1. Widget loads on all main pages (hideable, floating, accessible via shortcut); loading state, error handling, and fallback if backend/network down.
2. SSO session and RBAC enforced—chatbot only responds to questions against data and docs user is allowed to access; all queries logged with user and context.
3. Privacy mode: never shows/show traces of confidential data in suggestions or answers if user lacks permission; attempts blocked and logged.
4. Widget UI allows full audit/download/clear chat; conversations stored user-by-user and hidden from other roles unless permitted (admin/audit).
5. Frontend sanitization: detects/prevents dangerous input/output code injection/links.
6. Help menu has chatbot onboarding, privacy/data use explainer, and report-issue button.
7. All sessions, queries, and errors are audit-logged with user/session, chat ID, and event hash; suspicious/frequent error patterns flagged for review.
**Prerequisites:** Epic 8
```

```markdown
**Story 9.2: Contextual Retrieval-Augmented Generation (RAG) for Q&A**
As a user, I want to ask natural language questions and get relevant, cited answers from our documentation, policies, and select (read-only) ledger data, so that I can self-serve support and compliance needs.
**Acceptance Criteria:**
1. Chatbot uses hybrid search (semantic + keyword + metadata filters) to locate relevant doc/ledger excerpts; supports Vietnamese and English queries.
2. Every answer includes citations: clickable links to doc/transaction source, summary at top, full trace chain on expand; answers never returned without source.
3. Chatbot never fabricates accounting figures/numbers; always pulls from actual transactions/docs with timestamp and context; guardrails block if evidence unavailable.
4. For queries requesting ledger info: required filters are enforced (company, period, role), configurable answer detail level per role (e.g., no drilldown for clerk).
5. Error handling: if data out-of-date or index failed, clear error given and fallback with workaround suggestion (contact support, link).
6. Hardcoded "I don't know" or redirect-to-human triggers if system confidence < threshold; explain reason and show confidence badge to user.
7. User can refine/follow-up question, reset context, view prior history thread.
8. Indexed data audited with version/timestamp in answer; model outputs rate-limited and monitored for drift/hallucinations; feedback linked.
9. New docs/ledger data indexed nightly and after every major release; indexing logs errors per doc/tx and required admin review to close gaps.
**Prerequisites:** Story 9.1
```

```markdown
**Story 9.3: Workflow Automation and Guided Journeys**
As a user, I want the chatbot to initiate and guide me through frequent workflows (e.g., "how to post a journal", "how to upload opening balance"), so I resolve issues faster and reduce errors.
**Acceptance Criteria:**
1. FAQ trigger phrases recognized; chatbot offers walk-through with step-by-step guide (text + checklist + links to screen sections).
2. Guided flows context-aware: tracks user location (screen/module) to give inline advice or navigation quick-links; e.g., points to voucher screen when asked about posting.
3. Suggests related actions/documents (e.g., links "see VAT export" after VAT Q&A, proposes "open reconciliation screen").
4. Step completion tracking: user can check-off inline and review previous steps; chatbot saves incomplete sessions for resume.
5. Tracks failed/frustrated interaction patterns, prompts for feedback or offers escalation to human.
6. Escalation flows: optionally open support ticket (pre-filled context), email admin, or join group support room.
7. All automation/integration actions audited (user, session, screen, parameters, outcome).
8. Model supports controlled plug-ins, e.g., n8n task triggers, only for white-listed flows (admin-editable list).
**Prerequisites:** Story 9.2
```

```markdown
**Story 9.4: Feedback, Training Data Capture, and Guardrail Monitoring**
As a product owner/auditor, I want to capture user feedback and monitor system guardrails, so the AI assistant continuously improves without compliance risk.
**Acceptance Criteria:**
1. After every answer, user can rate (helpful/not, with comment); open issue reports go to admin inbox and are ticketed/audited.
2. Feedback integrated into RAG retraining/refresh process (weekly) with admin review step; no end-user PI or sensitive data leaks to models without explicit consent.
3. Guardrail logs: each time AI/LLM blocks/restricts answer, creates context+reason record; admin view shows top blocked queries, breakdown by type, user, and trend.
4. Periodic reviews required of feedback, blocked queries, and model audit logs (weekly or after major update); all reviews/audits are themselves tracked with approval and closure structure.
5. User requests for access/override of restricted responses require approval workflow and audit (with full event chain/log).
6. Admins can download anonymized feedback and guardrail data for regulatory submission or security review; purge requires dual signoff.
**Prerequisites:** Story 9.3
```

```markdown
**Story 9.5: Usage Analytics and Continuous Improvement**
As a product owner or admin, I want comprehensive analytics on chatbot usage, answer quality, and operational issues, so we can prove value and drive iterative enhancement.
**Acceptance Criteria:**
1. Dashboard shows query volume, unique users, active/inactive users by role/company, success/deflection metrics, QA scores, feedback trends.
2. Tracks average latency per question, slowest queries, fallbacks/"I don't know" rates, escalation counts, top repeated issues.
3. Quality metrics: answer traceability (with sources), manual QA scoring, feedback outcome over time with drilldown by answer and user segment.
4. Monitors and alerts for abnormal failures (data mismatch, hallucination detection, RBAC policy violation, API faults); escalation triggers admin notification.
5. Tracks retraining/model update events, with sharpness/drift benchmarks after each deploy; auto alerts for drops in accuracy/conformance.
6. Export/download permitted for anonymized data for audit/regulatory/compliance; direct connection to DR/backup process.
**Prerequisites:** Story 9.4
```

## Epic 10: Administration & Utilities

**Expanded Goal:**
Provide resilient configuration management, environment monitoring, user and access administration, audit tools, backup/restore utilities, scheduled tasks, and global settings—so all platform management, compliance, and security needs are met for sustainable operation.

```markdown
**Story 10.1: Environment & Configuration Management**
As a system administrator, I want to manage core environment and config values (env vars, endpoints, feature flags) via a secure panel, so that deployments and troubleshooting are efficient and controlled.
**Acceptance Criteria:**
1. Config UI: sections for environment, integrations, feature flags, branding, backups; edit, add, remove settings with per-field audit log.
2. Permissions: only admin/infra roles may view/edit; all changes require explicit reason and dual-review for sensitive settings.
3. Secrets (API keys, DB credentials) masked; edits require re-auth, are versioned and roll-backable; all exposures tracked.
4. Change history view for each parameter; diff-tool between any two points; restore past values with warning and audit.
5. Automated validation/health-check for key settings (connectivity/ping, expiry warning); issues flagged on dashboard and in notification center.
**Prerequisites:** Epic 9
```

```markdown
**Story 10.2: User & Access Management Utilities**
As an admin, I want to list, filter, bulk edit, and unlock/reset passwords for users, so access issues can be resolved promptly and securely.
**Acceptance Criteria:**
1. User directory with filter by company, role, activation, last-login, created/modified dates; search for email, name, phone.
2. Bulk select for activation, role change, password reset (not via email for security, but via admin-initiated re-auth); batch actions audit-logged per user.
3. Unlocks: admin can unlock locked accounts (with reason, modal explaining last lock event, forced password reset optional).
4. Delete user only possible if not referenced; show clear referential warning and block if in use; soft-delete leaves audit for at least 12 months.
5. Impersonate mode: permitted only for support/infra, with color banner and permanent session audit mark; all actions repeated in impersonation attributed to original user in logs.
6. Export user listing to Excel; all exports/critical views logged.
**Prerequisites:** Story 10.1
```

```markdown
**Story 10.3: Audit Log Explorer & Legal Holds**
As an auditor/chief, I want a filterable, exportable explorer for all activity logs and a legal hold facility, so that investigations and statutory reviews are exhaustive and responsive.
**Acceptance Criteria:**
1. Explorer UI: filter by type, user, entity, date, action, status; multi-column sort, supports custom date ranges, and frozen column headers for large result sets.
2. Export filtered logs to CSV/JSON/PDF with manifest of applied filters; signed hash and export history for chain of custody.
3. Legal hold tool: freeze logs or entities (vouchers, users, periods) for investigation/litigation; hold actions/dialogs audit-logged and require dual approval.
4. All views, filters, exports, and legal hold actions logged with user/time/IP/event hash and, if triggered from impersonation, with impersonation flag.
5. Scheduled deletion/purge jobs for logs have pre-flight simulated reports and dual-approver policy; purges generate final manifest and signed certificate.
**Prerequisites:** Story 10.2
```

```markdown
**Story 10.4: Backup, Restore, and Disaster Recovery Utilities**
As an infra admin, I want secure, automated backups of all critical data with simple restore flows, so business continuity and compliance are assured.
**Acceptance Criteria:**
1. Backup manager: schedules/retains rolling backups of DB, files, audit logs, config, et al; configurable frequencies, retention, excluded entities (flag/annotate), offsite copy)
2. Backups encrypted, stored on WORM/offsite if licensed; keys managed with MFA and recovery log; rotation policy enforced.
3. Restore: select backup, dry-run preview of restored entities/settings; must not overwrite active DB unless confirmed by dual approval process and impact log.
4. Restore events fully logged with who/what/when/rollback safety net; partial/failed restores flag system and escalate via notification center.
5. DR drills: run quarterly; drill logs, duration, issues tracked; report generated and reviewed by management.
**Prerequisites:** Story 10.3
```

```markdown
**Story 10.5: Global Settings & Scheduled Utilities**
As an admin or product owner, I want to configure global system settings, manage scheduled jobs, and review system cron tasks, so platform behavior is tunable and performance is observable.
**Acceptance Criteria:**
1. System settings UI: global toggles, default behaviors, system-wide notices/messages, e.g., maintenance banners; validates and requires audit log update.
2. Job scheduler: view, enable/disable, edit next run, and trace historical/failed/successful job runs; job logs exportable for audit.
3. Custom cron: admin can implement custom scripts (Python, shell, JS) with restrictive sandboxing; all runs logged with resource/time limits and output captured.
4. Job failure alerts: notification to designated support/infra/admin groups for any failure, latency, or skipped execution.
5. Scheduler supports dry-run/test mode; jobs flagged as test appear with clear icon/badge, logs segregated from production.
6. All changes, runs, and setting edits logged and recoverable for 5 years minimum.
**Prerequisites:** Story 10.4
```
