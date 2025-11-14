# Epic 7: Reporting Engine – Core Financials

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
