# Epic 4: Accounts Payable (AP) Module

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
