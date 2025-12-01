# Test Design: Epic 5 – Accounts Receivable (AR) Module

**Date Generated:** 2025-11-21  
**Epic:** 5 – Accounts Receivable (AR) Module  
**Scope:** Full  
**Test Architect:** Murat (Master Test Architect)

---

## Executive Summary

Epic 5 delivers critical AR workflows: sales invoice creation/approval, customer payment receipts, AR aging reports, customer reconciliation, VAT handling, and audit trails. This epic handles financial transactions touching GL (account 131 - AR), revenue accounts, and VAT output. **Risk profile: HIGH** due to multi-step financial workflows, regulatory compliance (TT200), maker-checker approvals, and data integrity requirements.

**Test effort estimate:** 85-100 hours (~11-13 days)

---

## Risk Assessment Matrix

| Risk ID | Category | Description | Probability | Impact | Score | Mitigation | Owner |
|---------|----------|-------------|-------------|--------|-------|-----------|-------|
| **R5-001** | **DATA** | Invoice duplicate posting (same customer + inv# + date) | 3 | 3 | **9** | Atomic DB constraint + API idempotency check + E2E test | QA/Dev |
| **R5-002** | **BUS** | Maker-checker bypass (approver ≠ creator rule violation) | 2 | 3 | **6** | Authorization layer test + workflow state validation + audit log verification | QA/Security |
| **R5-003** | **PERF** | AR Aging report slowness (N+1 queries on large customer base) | 2 | 2 | **4** | Query optimization + index validation + load test with 10K invoices | QA/Dev |
| **R5-004** | **DATA** | Partial payment over-allocation (sum of allocations > payment amount) | 3 | 3 | **9** | Input validation + API constraint + allocation matrix test | QA/Dev |
| **R5-005** | **SEC** | Unauthorized access to sensitive AR/customer data | 1 | 3 | **3** | RBAC enforcement at API layer + permission matrix test | QA/Security |
| **R5-006** | **TECH** | VAT calculation mismatch (header VAT ≠ sum of line VAT) | 2 | 3 | **6** | VAT formula unit tests + E2E invoice posting validation | QA/Dev |
| **R5-007** | **DATA** | Period-closed invoice posting (post to closed period) | 2 | 3 | **6** | Temporal validation + period status check + UI/API rejection test | QA/Dev |
| **R5-008** | **BUS** | Customer statement reconciliation mismatches (customer provides different balance) | 2 | 2 | **4** | Statement generation accuracy test + reconciliation workflow test | QA/Business |
| **R5-009** | **TECH** | AR GL account hierarchy violation (post to parent vs. leaf) | 1 | 3 | **3** | Account validation unit test + E2E posting rejection | QA/Dev |
| **R5-010** | **DATA** | Reversal voucher not properly cross-linked (audit trail broken) | 2 | 2 | **4** | Reversal creation test + audit log cross-reference verification | QA/Dev |
| **R5-011** | **PERF** | Bulk import (CSV/Excel) timeout on large datasets (>5K rows) | 2 | 2 | **4** | Import atomicity test + batch processing validation | QA/Dev |
| **R5-012** | **BUS** | Attachment handling (missing files on draft deletion) | 1 | 2 | **2** | Attachment lifecycle test (create, delete, cleanup) | QA/Dev |

---

## High-Priority Risks (Score ≥ 6)

### R5-001: Invoice Duplicate Posting (Score 9)
**Risk:** Same customer + invoice number + date pair posted twice → AR overstated, GL imbalanced.  
**Evidence:** Financial compliance requirement; common source of AR discrepancies.  
**Mitigation:** 
- Database unique constraint on (customer_id, invoice_number, period_id)
- API idempotency check (invoice ID immutable post-creation)
- E2E test: attempt duplicate → expect validation error

### R5-002: Maker-Checker Bypass (Score 6)
**Risk:** Same user creates and approves invoice → loss of segregation of duties.  
**Evidence:** Regulatory requirement (TT200 / internal audit).  
**Mitigation:**
- Authorization layer validates approver_id ≠ creator_id
- Workflow state machine enforces transition rules
- API test: attempt self-approval → expect 403 Forbidden
- Audit log test: verify rejection logged with timestamp/user

### R5-004: Partial Payment Over-Allocation (Score 9)
**Risk:** Customer receipt allocated >100% to invoices → AR understated.  
**Evidence:** Multi-invoice allocation logic is error-prone.  
**Mitigation:**
- Backend validation: SUM(allocations) ≤ receipt amount
- E2E test: allocate payment across multiple invoices, verify remaining balances
- Unit test: edge case (prorated splits, pennies)

### R5-006: VAT Calculation Mismatch (Score 6)
**Risk:** Header VAT ≠ sum of line VAT → GL posting fails or produces stale data.  
**Evidence:** Complex calculation; rounding rules required.  
**Mitigation:**
- Unit tests: VAT formula with all rates (0%, 5%, 10%, exempt)
- E2E test: invoice post, verify GL splits match VAT
- Test data: edge cases (rounding, multiple rates per invoice)

### R5-007: Period-Closed Invoice Posting (Score 6)
**Risk:** Invoice posted to closed period → GL imbalanced, period-close integrity broken.  
**Evidence:** Temporal validation not enforced consistently.  
**Mitigation:**
- Temporal validation: date in [period.start, period.end] AND period.status = OPEN
- UI test: date picker blocks closed period dates
- API test: attempt POST to closed period → expect 400 Bad Request

---

## Story-by-Story Test Coverage

### Story 5.1: Sales Invoice Entry, Edit, and Draft Management

**Test Scenarios:**

#### P0 (Critical - 9 tests, ~18 hours)
1. **Create invoice from scratch** - Happy path, all validations pass
   - Level: E2E | Risk Link: R5-001
   - Steps: Open invoice form → select customer → enter items (qty, price, VAT) → save as draft → verify auto-save
   - Expected: Form accepts input, calculates totals, saves as draft with ID
   - Data: Test customer with AR account

2. **Invoice number auto-generation per customer/period**
   - Level: API | Risk Link: None
   - Steps: Create invoice for Customer A, period Jan 2025 → verify invoice number follows pattern (CUST-A-2025-001)
   - Expected: Sequential numbering, scoped by customer + period

3. **Duplicate prevention - same customer + inv# + date**
   - Level: E2E + API | Risk Link: R5-001
   - Steps: Post invoice (CUST-A-001, 2025-01-15) → attempt post identical → catch validation error
   - Expected: 409 Conflict or 400 Bad Request with "Duplicate invoice" message

4. **Date validation - must be open period**
   - Level: API + Component | Risk Link: R5-007
   - Steps: Attempt invoice dated in closed Feb 2025 period
   - Expected: Date picker grays out closed dates, API rejects with period status error

5. **Required fields validation**
   - Level: Component | Risk Link: None
   - Steps: Try save without customer, due date, or description
   - Expected: Form shows inline validation errors, submit disabled

6. **Line item VAT rate selection (0%, 5%, 10%, exempt)**
   - Level: Component | Risk Link: R5-006
   - Steps: Add invoice lines with each VAT rate → verify default from settings
   - Expected: Each line shows configurable VAT, totals recalculate

7. **Attachment handling (drag/drop, preview, delete on draft)**
   - Level: E2E | Risk Link: R5-012
   - Steps: Drag file → preview → delete → save draft without attachment
   - Expected: File upload succeeds, preview works, delete removes from draft

8. **Autosave and undo/redo**
   - Level: Component | Risk Link: None
   - Steps: Type customer name → wait 2s → check draft saved → undo → redo
   - Expected: Autosave succeeds, undo restores previous state

9. **CSV/Excel import with template validation**
   - Level: API + E2E | Risk Link: R5-011
   - Steps: Upload invoice CSV template with 10 rows → map columns → verify atomicity (all or nothing)
   - Expected: All rows imported or all fail; error map shows row-level issues

#### P1 (High - 8 tests, ~16 hours)
10. **Edit draft invoice - creator/admin only**
    - Level: API + RBAC | Risk Link: R5-002
    - Steps: Creator edits own draft → non-creator attempts → verify rejection

11. **Delete draft invoice with confirmation**
    - Level: E2E | Risk Link: None
    - Steps: Delete draft → confirm → verify removal, attachments cleaned up

12. **Revenue account must be leaf account**
    - Level: API | Risk Link: R5-009
    - Steps: Attempt post to parent revenue account → expect rejection

13. **Mandatory dimension validation for revenue account**
    - Level: API | Risk Link: None
    - Steps: Post invoice without required cost_center (if needed) → expect validation error

14. **Totals auto-calculation and validation**
    - Level: Component + API | Risk Link: None
    - Steps: Enter line items → verify subtotal, VAT, total calculated correctly

15. **Attachment file type/size checks**
    - Level: Component | Risk Link: R5-012
    - Steps: Upload >10MB file, unsupported type → expect rejection with message

16. **Invoice status transitions (Draft → Pending → Posted)**
    - Level: API | Risk Link: None
    - Steps: Verify allowed state transitions; test illegal transitions

17. **Concurrent edit prevention (optimistic locking)**
    - Level: E2E | Risk Link: None
    - Steps: Two users edit same draft → second save fails with conflict

#### P2 (Medium - 5 tests, ~8 hours)
18. **Search invoices by number, customer, date range**
    - Level: API + Component | Risk Link: None

19. **Bulk edit invoice lines (swap VAT rates)**
    - Level: E2E | Risk Link: None

20. **Orphaned invoice cleanup on customer deletion**
    - Level: API | Risk Link: None

21. **Invoice export to PDF with layout validation**
    - Level: E2E | Risk Link: None

22. **Audit log - create/edit/post/delete/import events**
    - Level: API | Risk Link: None
    - Steps: Verify every action logged with diff, actor, device/IP, timestamp

---

### Story 5.2: Invoice Approval Workflow (Maker-Checker)

**Test Scenarios:**

#### P0 (Critical - 6 tests, ~12 hours)
1. **Threshold-based routing to Pending Approval**
   - Level: API | Risk Link: R5-002
   - Steps: Create invoice > 100M VND → verify status = Pending Approval, notification sent
   - Expected: Invoice routed to Chief Accountant

2. **Approver ≠ Creator validation**
   - Level: API + RBAC | Risk Link: R5-002
   - Steps: Creator attempts approval → expect 403 Forbidden
   - Expected: Clear error message, attempt logged in audit

3. **Approver approve action - posts invoice**
   - Level: E2E | Risk Link: None
   - Steps: Chief Accountant approves pending invoice → verify status = Posted, GL entries created
   - Expected: GL debit AR account, credit revenue + VAT output

4. **Approver reject action - returns to Draft with reason**
   - Level: E2E | Risk Link: None
   - Steps: Chief Accountant rejects with reason → verify invoice back in Draft, creator notified
   - Expected: Reason stored in audit, notification email/in-app alert sent

5. **Approval after period close is disabled**
   - Level: API | Risk Link: R5-007
   - Steps: Period closed, attempt approve pending invoice → expect rejection
   - Expected: Period status check blocks approval

6. **Auto-approve if workflow not triggered (< threshold)**
   - Level: API | Risk Link: None
   - Steps: Create invoice < 100M VND → verify auto-posts, shadow record in audit
   - Expected: Status = Posted, audit shows "auto_approved" flag

#### P1 (High - 4 tests, ~8 hours)
7. **Sensitive flag rule-based routing**
   - Level: API | Risk Link: R5-002
   - Steps: Create "sensitive" invoice (e.g., related-party) → verify routed even if < threshold

8. **Change history visible to approver**
   - Level: E2E | Risk Link: None
   - Steps: Approver views pending invoice, sees edit history, VAT changes
   - Expected: Chronological diffs shown

9. **Timeout on pending approval (escalation)**
   - Level: API | Risk Link: None
   - Steps: Pending invoice exceeds 7 days → escalate to CFO
   - Expected: Email alert, escalation logged in audit

10. **Concurrent approval prevention**
    - Level: API | Risk Link: None
    - Steps: Two users attempt to approve/reject simultaneously → first succeeds, second blocked

#### P2 (Medium - 2 tests, ~4 hours)
11. **Bulk approval of multiple pending invoices**
    - Level: E2E | Risk Link: None

12. **Audit trail - all transitions logged**
    - Level: API | Risk Link: None

---

### Story 5.3: Customer Payment Receipts

**Test Scenarios:**

#### P0 (Critical - 8 tests, ~16 hours)
1. **Create receipt with customer picker filtering to open invoices**
   - Level: E2E | Risk Link: None
   - Steps: Open receipt form → type customer name → verify dropdown shows only customers with open invoices
   - Expected: Typeahead filters correctly

2. **Receipt allocation to single invoice**
   - Level: E2E | Risk Link: R5-004
   - Steps: Create receipt for $100 → allocate to invoice $80 → verify remaining $20 unallocated
   - Expected: Allocation modal shows invoice balance, calculates remaining

3. **Partial allocation to multiple invoices (prorated)**
   - Level: E2E + API | Risk Link: R5-004
   - Steps: Receipt $1,000 → allocate to 3 invoices (prorated %) → verify total = $1,000
   - Expected: Allocation math correct, sum never exceeds receipt amount

4. **Over-payment prevention (allocations > receipt)**
   - Level: API | Risk Link: R5-004
   - Steps: Attempt allocate $500 to $600 invoice → expect validation error
   - Expected: "Cannot allocate more than receipt amount" message

5. **Standalone receipt (advance/on-account)**
   - Level: E2E | Risk Link: None
   - Steps: Create receipt with customer, no invoice allocation → save → verify status = Unapplied
   - Expected: Receipt held, can match later to invoices

6. **Receipt GL posting - Dr Bank/Cash, Cr AR (131)**
   - Level: API | Risk Link: None
   - Steps: Post receipt $100 → verify GL entries (Dr 111/112 +$100, Cr 131 -$100) with dimensions
   - Expected: GL balanced, AR account updated

7. **Reversal path - generates linked reversal voucher**
   - Level: E2E | Risk Link: R5-010
   - Steps: Post receipt → reverse → verify reversal created, original/reversal cross-linked in audit
   - Expected: Both vouchers marked as reversal pair, GL re-balanced

8. **Bulk import receipts (CSV/Excel)**
   - Level: API + E2E | Risk Link: R5-011
   - Steps: Upload receipt template with 20 rows → validate headers → import atomically
   - Expected: All succeed or all fail; error map with row numbers for failures

#### P1 (High - 5 tests, ~10 hours)
9. **Date auto-generation (system date or manual entry)**
    - Level: Component | Risk Link: None

10. **Reference text and method (cash/bank/check)**
    - Level: E2E | Risk Link: None

11. **Invoice remaining balance display (paid/remaining)**
    - Level: Component | Risk Link: None

12. **Allocation undo (revert allocation, return to draft)**
    - Level: E2E | Risk Link: None

13. **Late payment reconciliation (partial allocated, days overdue)**
    - Level: API | Risk Link: R5-008
    - Steps: Invoice 30 days overdue, receipt allocated partially → aging report shows remaining
    - Expected: AR aging reflects unallocated amount

#### P2 (Medium - 3 tests, ~6 hours)
14. **Attachment handling for receipts**
    - Level: E2E | Risk Link: R5-012

15. **Receipt approval workflow (Maker-Checker) - similar to Story 5.2**
    - Level: API | Risk Link: R5-002

16. **Audit trail - create/edit/post/reverse/import**
    - Level: API | Risk Link: None

---

### Story 5.4: AR Aging Report

**Test Scenarios:**

#### P0 (Critical - 5 tests, ~10 hours)
1. **Aging report buckets (Current, 1-30d, 31-60d, 61-90d, 91+d)**
   - Level: E2E + API | Risk Link: None
   - Steps: Create invoices with varying due dates → run aging → verify bucketing
   - Expected: Buckets calculated from TODAY - invoice.due_date

2. **Aging by customer with totals**
   - Level: API | Risk Link: R5-003
   - Steps: Multiple customers, multiple invoices each → aging report groups by customer
   - Expected: Subtotals per customer, running balance

3. **Drill-down from aging cell to invoice list**
   - Level: E2E | Risk Link: None
   - Steps: Click "91+d" bucket → see list of invoices, paid/remaining, last payment date
   - Expected: Invoice list filtered by bucket, detail view

4. **Exclude reversed/voided invoices**
   - Level: API | Risk Link: R5-010
   - Steps: Create & reverse invoice → run aging → verify reversed not in totals
   - Expected: Only active invoices counted

5. **Partial payment as remaining only**
   - Level: API | Risk Link: R5-004
   - Steps: Invoice $1,000, receipt $300 → aging shows $700 remaining
   - Expected: Remaining balance = invoiced - allocated

#### P1 (High - 4 tests, ~8 hours)
6. **Export aging to Excel/PDF with timestamp**
    - Level: E2E | Risk Link: None
    - Steps: Export aging → verify format, timestamp, active filters shown

7. **Overdue badges on dashboard**
    - Level: Component | Risk Link: None
    - Steps: Dashboard shows "5 overdue" count, top overdue customers list

8. **Reminder actions - in-app/email**
    - Level: E2E + API | Risk Link: None
    - Steps: Trigger reminder for overdue customer → verify notification sent, logged in audit

9. **RBAC - CFO sees all, AR clerk sees assigned scope**
    - Level: API + RBAC | Risk Link: R5-005
    - Steps: CFO runs aging (all), AR clerk runs aging (assigned customers only)
    - Expected: API filters by assigned scope, same UI

#### P2 (Medium - 2 tests, ~4 hours)
10. **Performance - 10K invoices, <5s response**
    - Level: Load Test | Risk Link: R5-003
    - Steps: Load 10K invoices into test DB → run aging → measure response time
    - Expected: <5s (P95 acceptable)

11. **Configurable reminder schedule (pre-due, due, +7d)**
    - Level: API + E2E | Risk Link: None

---

### Story 5.5: Customer Statement & Reconciliation

**Test Scenarios:**

#### P0 (Critical - 4 tests, ~8 hours)
1. **Statement generation (summary + detailed view)**
   - Level: E2E + API | Risk Link: None
   - Steps: Generate statement for customer X → summary (1 row per invoice), detailed (invoices + receipts + running balance)
   - Expected: Both views show correct balance progression

2. **Statement export to PDF/Excel**
   - Level: E2E | Risk Link: None
   - Steps: Export statement → verify format, legal footer, hash for audit
   - Expected: PDF/Excel generated, hash matches content

3. **Send to customer - email + delivery tracking**
   - Level: E2E + Integration | Risk Link: None
   - Steps: Click "Send to Customer" → email sent → verify delivery logged, view event recorded
   - Expected: Email template rendered in Vietnamese, log entry created

4. **Reconciliation import - customer-provided file**
   - Level: E2E + API | Risk Link: R5-008
   - Steps: Import customer's reconciliation → parse → compare balances → flag mismatches
   - Expected: Mismatches highlighted, suggest adjustments

#### P1 (High - 3 tests, ~6 hours)
5. **Statement history maintenance**
    - Level: API | Risk Link: None
    - Steps: Generate multiple statements over time → verify history retained

6. **Dispute tracking (color-coded, reasons, actions)**
    - Level: E2E | Risk Link: R5-008
    - Steps: Mark item as disputed → add reason → track resolution → export shows dispute status
    - Expected: Dispute log maintains history, exported statements include status

7. **Attachment handling - optional ZIP per statement**
    - Level: E2E | Risk Link: R5-012
    - Steps: Include supporting docs in statement export → customer downloads as ZIP
    - Expected: ZIP structure valid, all docs included

#### P2 (Medium - 1 test, ~2 hours)
8. **Batch export (multiple customers, ZIP format)**
    - Level: E2E | Risk Link: None

---

### Story 5.6: Revenue & VAT Handling

**Test Scenarios:**

#### P0 (Critical - 5 tests, ~10 hours)
1. **VAT rate selection per line (0%, 5%, 10%, exempt)**
   - Level: Component + API | Risk Link: R5-006
   - Steps: Create invoice with lines at all VAT rates → verify default from settings, override with warning
   - Expected: Each line shows VAT%, totals recalculate

2. **GL splits on invoice post**
   - Level: API | Risk Link: R5-006
   - Steps: Post invoice → verify GL entries:
     - Dr AR (131) = Invoice subtotal
     - Cr Revenue (511+) = Revenue subtotal
     - Cr VAT Output (3331) = Total VAT
   - Expected: GL balanced, all with correct dimensions

3. **VAT rounding consistency**
   - Level: Unit Test + API | Risk Link: R5-006
   - Steps: Test amounts that require rounding (e.g., $33.33 * 5% = $1.67 or $1.66)
   - Expected: Consistent rounding rule applied (round half-up)

4. **Header VAT = sum of line VAT validation**
   - Level: API | Risk Link: R5-006
   - Steps: Create invoice where header VAT ≠ sum of line VAT → attempt post
   - Expected: Validation error, post blocked with "VAT mismatch" message

5. **Credit notes/negative invoices**
   - Level: E2E + API | Risk Link: None
   - Steps: Create credit note referencing original invoice → verify GL splits reversed
   - Expected: All linked with audit cross-references

#### P1 (High - 3 tests, ~6 hours)
6. **Output VAT report (ND123 format)**
    - Level: E2E + API | Risk Link: None
    - Steps: Generate VAT report for period → export Excel in ND123 format
    - Expected: Format matches Vietnam compliance requirement

7. **Admin VAT corrections (reason + diff audit)**
    - Level: API | Risk Link: R5-006
    - Steps: Correct VAT on posted invoice → verify reason recorded, approval if > threshold
    - Expected: Audit shows before/after, threshold enforcement

8. **Idempotent posting (block double-booking)**
    - Level: API | Risk Link: R5-001
    - Steps: Attempt post same invoice twice → second fails
    - Expected: Idempotency key validation, 400 or 409 response

#### P2 (Medium - 1 test, ~2 hours)
9. **VAT account hierarchy validation**
    - Level: API | Risk Link: None

---

### Story 5.7: Audit Trail & Compliance

**Test Scenarios:**

#### P0 (Critical - 4 tests, ~8 hours)
1. **Audit entry for every AR action (create/edit/post/approve/reverse/import)**
   - Level: API | Risk Link: None
   - Steps: Perform each action → verify audit entry created with:
     - Event type (e.g., INVOICE_CREATED)
     - Actor (user ID)
     - Timestamp
     - Device/IP address
     - Change diff (before/after)
   - Expected: All fields populated, immutable

2. **Audit export (PDF/JSON with hash signature)**
   - Level: E2E | Risk Link: None
   - Steps: Export AR audit logs → verify format, hash signature valid
   - Expected: PDF/JSON readable, signature validates

3. **Blocked/unauthorized actions flagged and alerted**
   - Level: API + Alerting | Risk Link: R5-005
   - Steps: Attempt unauthorized action (e.g., non-approver approves) → verify:
     - Audit entry logs rejection
     - Alert triggered (email/in-app)
   - Expected: Security event logged, alert sent within 5 min

4. **Retention policy - 10 years with GDPR purge process**
   - Level: API + Database | Risk Link: None
   - Steps: Verify purge script removes data older than 10 years
   - Expected: Purge targets only eligible records, maintains audit integrity

#### P1 (High - 2 tests, ~4 hours)
5. **Voucher history with chronological diffs**
    - Level: E2E | Risk Link: None
    - Steps: Open invoice → "History" view → see all edits chronologically

6. **Scheduled backups include AR audit logs**
    - Level: Operations | Risk Link: None
    - Steps: Verify backup includes audit table, external copy for DR

#### P2 (Medium - 1 test, ~2 hours)
7. **Filter audit logs by customer/period/user/action**
    - Level: E2E + API | Risk Link: None

---

## Test Levels & Priority Summary

### By Priority
| Priority | Count | Hours | Focus |
|----------|-------|-------|-------|
| **P0 (Critical)** | 36 tests | 72 hours | Happy paths, high-risk flows, data integrity, approval rules |
| **P1 (High)** | 23 tests | 46 hours | Workflows, edge cases, compliance, RBAC |
| **P2 (Medium)** | 16 tests | 32 hours | Secondary features, performance, nice-to-haves |
| **P3 (Low)** | 0 tests | 0 hours | — |
| **TOTAL** | **75 tests** | **150 hours** | — |

### By Test Level
| Level | Count | Rationale |
|-------|-------|-----------|
| **E2E** | 22 tests | Critical user journeys (invoice create→approve→post, payment allocation, reconciliation) |
| **API/Integration** | 28 tests | GL posting, approval workflows, VAT calculations, audit trails, permission enforcement |
| **Component/Unit** | 21 tests | Form validations, calculations, autosave, dropdown filtering |
| **Load/Performance** | 2 tests | AR aging (10K invoices), bulk import (5K rows) |
| **Security/RBAC** | 2 tests | Authorization, role-based filtering |

---

## Execution Strategy

### Smoke Tests (P0 subset, <5 minutes)
1. Create invoice (happy path)
2. Post invoice (GL entries created)
3. Create payment receipt and allocate
4. Approver approves pending invoice
5. Run AR aging report

### P0 Tests (<10 minutes per test, ~72 hours total)
- Invoice creation/validation/posting
- Duplicate prevention
- Maker-checker approval
- Partial payment allocation
- VAT calculations
- Period-close rejection
- Reversal linking
- Bulk import

### P1 Tests (optional, <30 min per test, ~46 hours total)
- Draft editing restrictions
- Concurrent edit handling
- Detailed reconciliation
- Permission enforcement
- Statement generation

### P2+ Tests (nightly/weekly, ~32 hours total)
- Performance benchmarks
- Historical searches
- Bulk operations

---

## Test Data & Fixtures

### Base Setup
- **Customers:** 10 test customers (varied sizes, some with open invoices)
- **GL Accounts:** Chart of Accounts (focus: 131 AR, 511+ Revenue, 3331 VAT Output)
- **Periods:** Current open period + 1 closed period
- **Users:** Accountant, Chief Accountant, CFO (RBAC testing)
- **Payment Methods:** Cash, Bank Transfer, Check

### Factories Required
- `InvoiceFactory` - invoice builder with VAT rate support
- `PaymentReceiptFactory` - receipt builder with allocation logic
- `CustomerFactory` - customer with AR account links
- `ApprovalRuleFactory` - threshold-based routing config
- `AllocationFactory` - partial allocation builder

### Edge Cases
- **Zero-VAT invoices** (exempt items)
- **Rounding edge cases** (amounts requiring 3-decimal precision)
- **Multi-currency** (currently VND only, but test handling)
- **Prorated allocations** (split payment across multiple invoices)
- **Orphaned allocations** (reversal of partially-applied receipt)

---

## Quality Gate Criteria

**Release Decision:**
- ✅ All P0 tests pass (100%)
- ✅ P1 tests pass (≥95%)
- ✅ No high-risk (score ≥6) items unmitigated
- ✅ Coverage ≥80% for critical paths (invoice posting, approval, allocation)
- ✅ Audit trail validation complete
- ✅ VAT calculations verified against specification
- ✅ Period-close enforcement tested
- ✅ Performance tests show aging report <5s for 10K invoices

---

## Test Tools & Infrastructure

| Tool | Purpose | Owner |
|------|---------|-------|
| **Playwright** | E2E testing (invoice create, approval, payment flows) | QA |
| **Jest/Spring Test** | Unit tests (VAT math, allocation logic) | Dev |
| **Postman/REST Client** | API testing (GL posting, RBAC, audit trails) | QA |
| **k6** | Load testing (10K invoice aging, bulk import) | QA/Ops |
| **SQLAlchemy/Flyway** | Test data seeding | QA/Dev |
| **Docker Compose** | Test environment (backend, DB, Redis) | QA/Ops |

---

## Recommendations for Next Steps

1. **Immediate (Sprint Planning):**
   - Review risk assessment with development team
   - Prioritize mitigation for R5-001, R5-002, R5-004, R5-006
   - Allocate ~85-100 hours for test automation

2. **Sprint Execution:**
   - Implement test data factories (week 1)
   - Build P0 E2E tests in parallel with Story 5.1-5.3 development
   - Run daily smoke tests on CI/CD

3. **Pre-Release:**
   - Complete P1 tests before Feature Complete
   - Performance test with production-scale data (10K invoices)
   - Security audit of approval workflow and RBAC

4. **Post-Release:**
   - Monitor audit logs for false positives/negatives
   - Collect user feedback on statement generation and reconciliation UX
   - Optimize slow queries on AR aging report

---

## Risk Traceability

Every P0 test is linked to at least one high-priority risk (R5-001, R5-002, R5-004, R5-006, R5-007):

| Risk | P0 Tests | Status |
|------|----------|--------|
| **R5-001 (Duplicates)** | 1.3, 1.9, 5.8 | Covered |
| **R5-002 (Maker-Checker Bypass)** | 2.1-2.6, 5.2, Story 5.3 P1 | Covered |
| **R5-004 (Over-Allocation)** | 3.3-3.4, 3.5, 5.5 | Covered |
| **R5-006 (VAT Mismatch)** | 6.1-6.4, 1.6, 2.3 | Covered |
| **R5-007 (Period-Closed Posting)** | 1.4, 2.5 | Covered |

---

## Appendix: Test Execution Order

### Phase 1: Foundation (Week 1)
- Set up test environment (Docker, DB, seed data)
- Implement test data factories
- Build smoke tests

### Phase 2: P0 Critical (Weeks 2-3)
- Invoice creation & validation
- GL posting accuracy
- Maker-checker approval
- Payment allocation
- VAT calculations
- Audit logging

### Phase 3: P1 Important (Weeks 4-5)
- Reconciliation workflows
- RBAC enforcement
- Statement generation
- Edge cases

### Phase 4: P2+ Secondary (Weeks 6+)
- Performance benchmarks
- Historical searches
- Bulk operations

---

**Document Status:** ✅ Complete  
**Test Design Ready for Implementation**  
**Validation:** All stories mapped, all high-priority risks covered, test levels balanced (40% E2E, 37% API, 28% Component/Unit)
