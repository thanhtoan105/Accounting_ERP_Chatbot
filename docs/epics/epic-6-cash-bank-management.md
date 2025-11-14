# Epic 6: Cash & Bank Management

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
