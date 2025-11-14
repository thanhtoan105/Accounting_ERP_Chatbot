# Epic 5: Accounts Receivable (AR) Module

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
