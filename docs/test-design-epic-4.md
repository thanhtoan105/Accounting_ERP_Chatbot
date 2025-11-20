# Test Design: Epic 4 - Accounts Payable (AP) Module

**Date:** 2025-01-15
**Author:** thanhtoan
**Status:** Draft

---

## Executive Summary

**Scope:** Full test design for Epic 4 - Accounts Payable Module

**Risk Summary:**

- Total risks identified: 18
- High-priority risks (≥6): 6
- Critical categories: SEC (Security), DATA (Data Integrity), BUS (Business Impact)

**Coverage Summary:**

- P0 scenarios: 24 (48 hours)
- P1 scenarios: 35 (35 hours)
- P2/P3 scenarios: 42 (21 hours)
- **Total effort**: 104 hours (~13 days)

---

## Risk Assessment

### High-Priority Risks (Score ≥6)

| Risk ID | Category | Description | Probability | Impact | Score | Mitigation | Owner | Timeline |
| ------- | -------- | ----------- | ----------- | ------ | ----- | ---------- | ------ | -------- |
| R-001   | SEC      | Unauthorized approval bypass - user approves own bills or exceeds role permissions | 2 | 3 | 6 | Enforce approver ≠ creator validation, RBAC checks on approval endpoint, audit all approval attempts | Backend | Sprint start |
| R-002   | DATA     | Double-payment risk - payment allocated to already-paid bill or overpayment allowed | 3 | 2 | 6 | Enforce payment allocation validation (check bill balance before allocation), prevent overpayment with real-time balance checks, atomic transaction for payment posting | Backend | Story 4.3 |
| R-003   | BUS      | VAT calculation errors leading to compliance violations and audit penalties | 2 | 3 | 6 | Automated VAT sum validation (header vs line items), TT200 GL mapping validation, comprehensive VAT test coverage, manual correction workflow with audit | Backend/QA | Story 4.6 |
| R-004   | DATA     | Data loss from bill deletion or period-close operations without proper validation | 2 | 3 | 6 | Prevent deletion of posted bills, validate period status before operations, immutable audit trail, backup/DR procedures | Backend | Story 4.1 |
| R-005   | SEC      | Sensitive bill data exposure - unauthorized access to high-value bills or supplier information | 2 | 3 | 6 | RBAC enforcement on all bill queries, company-scoped filtering, audit all access attempts, test permission boundaries | Backend | Story 4.1 |
| R-006   | BUS      | Approval workflow failure - bills stuck in pending state, no escalation, notification failures | 3 | 2 | 6 | Automated escalation after 48h, notification retry mechanism, workflow state monitoring, manual override for admins with audit | Backend | Story 4.2 |

### Medium-Priority Risks (Score 3-4)

| Risk ID | Category | Description | Probability | Impact | Score | Mitigation | Owner |
| ------- | -------- | ----------- | ----------- | ------ | ----- | ---------- | ------ |
| R-007   | TECH     | Integration failure with voucher engine - GL posting errors when posting AP bills | 2 | 2 | 4 | Integration tests with voucher service, mock voucher engine for unit tests, error handling and rollback on posting failure | Backend | Story 4.1 |
| R-008   | PERF     | Aging report performance degradation with large supplier/bill datasets | 2 | 2 | 4 | Server-side pagination, database indexing on date fields, caching for frequently accessed reports, performance benchmarks | Backend | Story 4.4 |
| R-009   | DATA     | Statement reconciliation mismatches - import parsing errors or comparison logic failures | 2 | 2 | 4 | Comprehensive import validation, detailed mismatch reporting, manual adjustment workflow, reconciliation audit trail | Backend | Story 4.5 |
| R-010   | OPS     | Batch import failures - partial imports, data corruption, no rollback mechanism | 2 | 2 | 4 | Atomic import transactions, detailed error mapping, downloadable error reports, validation before import | Backend | Story 4.1 |
| R-011   | PERF     | Attachment upload/download performance issues with large files or high concurrency | 1 | 3 | 3 | File size limits (20MB total), async upload processing, CDN for downloads, storage quota management | Backend | Story 4.1 |
| R-012   | BUS      | Overdue alert false positives or missed alerts leading to cashflow issues | 2 | 2 | 4 | Accurate aging calculation, configurable alert thresholds, alert delivery confirmation, manual alert trigger | Backend | Story 4.4 |

### Low-Priority Risks (Score 1-2)

| Risk ID | Category | Description | Probability | Impact | Score | Action |
| ------- | -------- | ----------- | ----------- | ------ | ----- | ------- |
| R-013   | OPS      | Export performance degradation for large statement batches | 1 | 2 | 2 | Monitor export times, implement pagination for large exports | Backend |
| R-014   | BUS      | UI/UX issues in bill form causing user errors or confusion | 1 | 2 | 2 | Usability testing, clear error messages, inline validation feedback | Frontend |
| R-015   | TECH     | Template application failures - voucher template not applying correctly to bill | 1 | 2 | 2 | Template validation tests, fallback to manual entry | Backend |
| R-016   | DATA     | Audit log export failures or incomplete audit trail exports | 1 | 1 | 1 | Export validation tests, retry mechanism | Backend |
| R-017   | OPS      | Notification delivery failures (email/in-app) for non-critical alerts | 1 | 1 | 1 | Notification retry logic, delivery status tracking | Backend |
| R-018   | BUS      | Supplier statement formatting issues in PDF/Excel export | 1 | 1 | 1 | Export format validation, visual regression tests | Frontend |

### Risk Category Legend

- **TECH**: Technical/Architecture (flaws, integration, scalability)
- **SEC**: Security (access controls, auth, data exposure)
- **PERF**: Performance (SLA violations, degradation, resource limits)
- **DATA**: Data Integrity (loss, corruption, inconsistency)
- **BUS**: Business Impact (UX harm, logic errors, revenue)
- **OPS**: Operations (deployment, config, monitoring)

---

## Test Coverage Plan

### P0 (Critical) - Run on every commit

**Criteria**: Blocks core journey + High risk (≥6) + No workaround

| Requirement | Test Level | Risk Link | Test Count | Owner | Notes |
| ----------- | ---------- | --------- | ---------- | ----- | ----- |
| Bill creation with required fields validation | API | R-004 | 3 | QA | Validates supplier, date, line items, required dimensions |
| Bill posting workflow (draft → posted) | E2E | R-007 | 2 | QA | End-to-end posting with GL entry generation |
| Payment allocation validation (prevent overpayment) | API | R-002 | 4 | QA | Tests FIFO allocation, balance checks, overpayment blocking |
| Approval workflow - approver ≠ creator enforcement | API | R-001 | 3 | QA | RBAC validation, creator cannot approve own bills |
| VAT calculation and sum validation | API | R-003 | 3 | QA | Header vs line item VAT sum, TT200 GL mapping |
| Period close validation (blocks bill operations) | API | R-004 | 2 | QA | Prevents operations on closed periods |
| Company-scoped data isolation | API | R-005 | 2 | QA | RBAC and company filtering on all queries |
| Approval workflow state transitions | E2E | R-006 | 2 | QA | Draft → Pending → Approved/Rejected flows |
| Bill deletion prevention (posted bills) | API | R-004 | 1 | QA | Posted bills cannot be deleted |
| Payment posting as voucher (Dr AP, Cr cash/bank) | API | R-002 | 2 | QA | GL entry generation validation |

**Total P0**: 24 tests, 48 hours

### P1 (High) - Run on PR to main

**Criteria**: Important features + Medium risk (3-4) + Common workflows

| Requirement | Test Level | Risk Link | Test Count | Owner | Notes |
| ----------- | ---------- | --------- | ---------- | ----- | ----- |
| Bill edit in draft state (creator only) | API | - | 3 | QA | Edit permissions, autosave, undo/redo |
| Bill duplicate detection (supplier + bill no + year) | API | - | 2 | QA | Prevents duplicate bill creation |
| Attachment upload/download (10 files, 20MB limit) | E2E | R-011 | 3 | QA | File validation, preview, deletion for drafts |
| Maker-checker approval threshold configuration | API | - | 2 | QA | Admin configurable threshold, auto-trigger logic |
| Approval notification (in-app/email) | E2E | R-006 | 2 | QA | Notification delivery, escalation after 48h |
| Payment batch allocation (multiple bills) | API | R-002 | 3 | QA | FIFO allocation, manual override, balance validation |
| Standalone payment (advance/ad hoc) | API | - | 2 | QA | Admin-only, warning tags, audit logging |
| AP aging report generation (buckets: Current, 1-30d, etc.) | API | R-008 | 3 | QA | Accurate aging calculation, filtering, sorting |
| Overdue alerts and reminders | E2E | R-012 | 2 | QA | Alert triggers, batch send, audit logging |
| Supplier statement generation (summary/detailed) | API | - | 2 | QA | Statement views, export formats |
| Statement import and reconciliation | E2E | R-009 | 3 | QA | Import parsing, mismatch detection, manual notes |
| VAT report generation (by period/supplier/class) | API | R-003 | 2 | QA | Report accuracy, ND123 compliance |
| Manual VAT correction workflow | API | R-003 | 2 | QA | Correction with diff, reason audit, approval |
| Audit log export (PDF/JSON) | API | R-016 | 2 | QA | Export functionality, completeness |
| Bill batch import with validation | E2E | R-010 | 2 | QA | Template validation, atomic save, error mapping |

**Total P1**: 35 tests, 35 hours

### P2 (Medium) - Run nightly/weekly

**Criteria**: Secondary features + Low risk (1-2) + Edge cases

| Requirement | Test Level | Risk Link | Test Count | Owner | Notes |
| ----------- | ---------- | --------- | ---------- | ----- | ----- |
| Bill date validation (future dates, holidays disabled) | API | - | 3 | QA | Date picker constraints, period validation |
| Due date auto-calculation and editing | Component | - | 2 | DEV | UI component behavior |
| Line item validation (qty × price, positive only) | Component | - | 4 | DEV | Inline validation, error display |
| Account picker (leaf/postable enforcement) | Component | - | 3 | DEV | Account selection, validation feedback |
| VAT rate selection (0/5/10/exempt) | Component | - | 3 | DEV | Badge/warning for 0%, rate validation |
| Draft autosave and recovery | E2E | - | 2 | QA | Autosave frequency, recovery mechanism |
| Multi-error summary footer | Component | R-014 | 2 | DEV | Error aggregation, UX clarity |
| Supplier picker with typeahead/add | Component | - | 3 | DEV | Search, add new supplier, validation |
| Payment proof upload requirement | E2E | - | 2 | QA | Threshold-based requirement, validation |
| Bank account balance display and validation | Component | - | 2 | DEV | Balance display, overdraft warning |
| Aging report drill-down (bucket → bills) | E2E | R-008 | 2 | QA | Navigation, filtering, data accuracy |
| Statement export (PDF/Excel, TT200-compliant) | E2E | R-018 | 3 | QA | Format validation, hash, legal footer |
| Statement email delivery | E2E | R-017 | 2 | QA | Email sending, delivery confirmation |
| Dispute log creation and management | API | - | 2 | QA | Dispute tracking, notes, audit |
| Attachment batch download (ZIP) | E2E | R-013 | 2 | QA | ZIP generation, file integrity |
| Audit log timeline view and filtering | E2E | - | 2 | QA | Timeline display, filter functionality |
| DR backup/export workflow | API | - | 2 | QA | Scheduled backup, archive validation |

**Total P2**: 42 tests, 21 hours

### P3 (Low) - Run on-demand

**Criteria**: Nice-to-have + Exploratory + Performance benchmarks

| Requirement | Test Level | Test Count | Owner | Notes |
| ----------- | ---------- | ---------- | ----- | ----- |
| Performance: Aging report with 10K+ bills | API | 1 | QA | Load testing, response time benchmarks |
| Performance: Statement export with 1K+ items | API | 1 | QA | Export time, memory usage |
| Performance: Concurrent bill creation (10+ users) | API | 1 | QA | Concurrency testing, race conditions |
| Visual regression: Bill form UI components | E2E | 2 | QA | Screenshot comparison, UI consistency |
| Visual regression: Aging report dashboard | E2E | 1 | QA | Dashboard layout, badge counts |
| Exploratory: Edge cases in payment allocation | E2E | 2 | QA | Complex scenarios, boundary conditions |
| Exploratory: VAT calculation edge cases | API | 2 | QA | Negative amounts, over-100% ratios |
| Accessibility: Bill form keyboard navigation | E2E | 1 | QA | WCAG compliance, screen reader support |

**Total P3**: 12 tests, 3 hours

---

## Execution Order

### Smoke Tests (<5 min)

**Purpose**: Fast feedback, catch build-breaking issues

- [ ] Bill creation with minimal required fields (30s)
- [ ] Bill posting workflow (draft → posted) (1min)
- [ ] Payment allocation validation (prevent overpayment) (45s)
- [ ] Approval workflow - approver enforcement (45s)
- [ ] VAT sum validation (30s)

**Total**: 5 scenarios

### P0 Tests (<10 min)

**Purpose**: Critical path validation

- [ ] Bill creation with required fields validation (API)
- [ ] Bill posting workflow (E2E)
- [ ] Payment allocation validation (API)
- [ ] Approval workflow enforcement (API)
- [ ] VAT calculation and validation (API)
- [ ] Period close validation (API)
- [ ] Company-scoped data isolation (API)
- [ ] Approval state transitions (E2E)
- [ ] Bill deletion prevention (API)
- [ ] Payment posting as voucher (API)

**Total**: 24 scenarios

### P1 Tests (<30 min)

**Purpose**: Important feature coverage

- [ ] Bill edit in draft state (API)
- [ ] Bill duplicate detection (API)
- [ ] Attachment upload/download (E2E)
- [ ] Approval threshold configuration (API)
- [ ] Approval notifications (E2E)
- [ ] Payment batch allocation (API)
- [ ] Standalone payment (API)
- [ ] AP aging report generation (API)
- [ ] Overdue alerts (E2E)
- [ ] Supplier statement generation (API)
- [ ] Statement import and reconciliation (E2E)
- [ ] VAT report generation (API)
- [ ] Manual VAT correction (API)
- [ ] Audit log export (API)
- [ ] Bill batch import (E2E)

**Total**: 35 scenarios

### P2/P3 Tests (<60 min)

**Purpose**: Full regression coverage

- [ ] All P2 component and API tests (42 scenarios)
- [ ] All P3 performance and exploratory tests (12 scenarios)

**Total**: 54 scenarios

---

## Resource Estimates

### Test Development Effort

| Priority | Count | Hours/Test | Total Hours | Notes |
| -------- | ----- | ---------- | ----------- | ----- |
| P0       | 24    | 2.0        | 48          | Complex setup, security, integration with voucher engine |
| P1       | 35    | 1.0        | 35          | Standard coverage, API and E2E tests |
| P2       | 42    | 0.5        | 21          | Component tests, edge cases, UI validation |
| P3       | 12    | 0.25       | 3           | Performance benchmarks, exploratory |
| **Total** | **113** | **-** | **107** | **~13.4 days** |

### Prerequisites

**Test Data:**

- `SupplierFactory` - Faker-based supplier generation (name, tax code, contact info)
- `BillFactory` - Bill creation with line items, VAT, attachments
- `PaymentFactory` - Payment creation with bill allocation
- `VoucherFactory` - Voucher templates for GL posting validation
- `PeriodFactory` - Accounting period setup (open/closed states)
- `UserFactory` - User creation with roles (Accountant, Chief Accountant, Admin)

**Tooling:**

- Playwright for E2E tests (already configured)
- API test framework (Axios/HTTP client for backend API tests)
- Component testing (Vitest + Testing Library for React components)
- Faker.js for test data generation (already installed)
- Mock service worker for API mocking in component tests

**Environment:**

- Test database with company-scoped data isolation
- Supabase Storage for attachment testing (test bucket)
- Email service mock for notification testing
- Redis cache for period status caching tests

---

## Quality Gate Criteria

### Pass/Fail Thresholds

- **P0 pass rate**: 100% (no exceptions)
- **P1 pass rate**: ≥95% (waivers required for failures)
- **P2/P3 pass rate**: ≥90% (informational)
- **High-risk mitigations**: 100% complete or approved waivers

### Coverage Targets

- **Critical paths**: ≥80% (bill creation → posting → payment → aging)
- **Security scenarios**: 100% (RBAC, company isolation, approval enforcement)
- **Business logic**: ≥70% (VAT calculation, payment allocation, aging calculation)
- **Edge cases**: ≥50% (duplicate detection, period validation, overpayment prevention)

### Non-Negotiable Requirements

- [ ] All P0 tests pass
- [ ] No high-risk (≥6) items unmitigated
- [ ] Security tests (SEC category) pass 100%
- [ ] VAT calculation and compliance tests pass 100%
- [ ] Payment allocation validation tests pass 100%

---

## Mitigation Plans

### R-001: Unauthorized Approval Bypass (Score: 6)

**Mitigation Strategy:** 
- Enforce approver ≠ creator validation at API level (backend service)
- RBAC checks on approval endpoint (Chief Accountant or CFO role required)
- Audit all approval attempts (success and failure)
- Frontend UI prevents creator from seeing approval button for own bills
- Integration tests validate RBAC enforcement

**Owner:** Backend Team
**Timeline:** Story 4.2 implementation
**Status:** Planned
**Verification:** 
- API tests: Creator cannot approve own bills (returns 403)
- E2E tests: Approval UI hidden for creator
- Audit log: All approval attempts logged with user/role

### R-002: Double-Payment Risk (Score: 6)

**Mitigation Strategy:**
- Real-time bill balance calculation before payment allocation
- Atomic transaction for payment posting (prevents race conditions)
- Validation: Payment amount ≤ (Bill total - Already paid)
- FIFO allocation with balance tracking
- Database constraints prevent negative bill balances

**Owner:** Backend Team
**Timeline:** Story 4.3 implementation
**Status:** Planned
**Verification:**
- API tests: Overpayment blocked with error message
- E2E tests: Payment form shows remaining balance
- Integration tests: Concurrent payment attempts handled correctly

### R-003: VAT Calculation Errors (Score: 6)

**Mitigation Strategy:**
- Automated VAT sum validation: Header VAT = Sum of line item VAT (tolerance: 1,000₫)
- TT200 GL mapping validation (VAT 3331 leg auto-booked)
- Comprehensive VAT test coverage (0%, 5%, 10%, exempt scenarios)
- Manual correction workflow with diff and reason audit
- Negative or over-100% VAT ratio attempts blocked

**Owner:** Backend/QA Team
**Timeline:** Story 4.6 implementation
**Status:** Planned
**Verification:**
- API tests: VAT sum mismatch blocks posting
- E2E tests: VAT calculation UI shows warnings
- Integration tests: TT200 GL mapping validated
- Manual correction workflow tested

### R-004: Data Loss from Bill Deletion (Score: 6)

**Mitigation Strategy:**
- Prevent deletion of posted bills (soft delete for drafts only)
- Validate period status before operations (closed periods block edits)
- Immutable audit trail captures all deletion attempts
- Scheduled backup/DR procedures for AP data
- Database foreign key constraints prevent orphaned records

**Owner:** Backend Team
**Timeline:** Story 4.1 implementation
**Status:** Planned
**Verification:**
- API tests: Posted bill deletion returns 403
- E2E tests: Delete button hidden for posted bills
- Audit log: All deletion attempts logged
- DR backup procedures tested

### R-005: Sensitive Bill Data Exposure (Score: 6)

**Mitigation Strategy:**
- RBAC enforcement on all bill queries (company-scoped filtering)
- API endpoints validate user permissions before data access
- Audit all access attempts (who, when, what data)
- Test permission boundaries (AP clerk sees only assigned bills)
- Chief Accountant/CFO see all company bills

**Owner:** Backend Team
**Timeline:** Story 4.1 implementation
**Status:** Planned
**Verification:**
- API tests: Unauthorized access returns 403
- E2E tests: UI filters data by user role
- Integration tests: Company isolation validated
- Audit log: All data access logged

### R-006: Approval Workflow Failure (Score: 6)

**Mitigation Strategy:**
- Automated escalation after 48h (notify backup approver)
- Notification retry mechanism (3 attempts with exponential backoff)
- Workflow state monitoring (alert on stuck bills)
- Manual override for admins with audit trail
- Health check endpoint for notification service

**Owner:** Backend Team
**Timeline:** Story 4.2 implementation
**Status:** Planned
**Verification:**
- E2E tests: Notification delivery confirmed
- Integration tests: Escalation triggers after 48h
- Monitoring: Workflow state dashboard
- Manual override tested with audit validation

---

## Assumptions and Dependencies

### Assumptions

1. Epic 3 (Voucher Engine) is fully implemented and tested - AP bills will post as vouchers
2. Master data (Suppliers, Chart of Accounts) from Epic 2 is available and stable
3. Authentication and RBAC from Epic 1 is functioning correctly
4. Supabase Storage is configured for attachment handling
5. Email service is available for notification delivery
6. Test environment mirrors production architecture (Spring Boot backend, React frontend)

### Dependencies

1. **Epic 3 Voucher Engine** - Required for bill posting and payment voucher generation (Story 4.1, 4.3)
2. **Epic 2 Master Data** - Suppliers, Chart of Accounts, Bank Accounts required (Story 4.1, 4.3)
3. **Epic 1 RBAC** - Role-based permissions for approval workflow (Story 4.2)
4. **Period Management (Epic 3)** - Period close validation for bill operations (Story 4.1)
5. **Audit Trail Service (Epic 2/3)** - Audit logging infrastructure (Story 4.7)

### Risks to Plan

- **Risk**: Voucher engine API changes break AP integration
  - **Impact**: Delayed implementation, test failures
  - **Contingency**: Mock voucher service for unit tests, integration test suite for API contracts

- **Risk**: Performance issues with large datasets (10K+ bills)
  - **Impact**: Aging report slow, user experience degradation
  - **Contingency**: Performance benchmarks in P3 tests, database indexing optimization

- **Risk**: Supabase Storage quota or performance limits
  - **Impact**: Attachment upload failures
  - **Contingency**: File size limits, async processing, fallback storage option

---

## Approval

**Test Design Approved By:**

- [ ] Product Manager: {name} Date: {date}
- [ ] Tech Lead: {name} Date: {date}
- [ ] QA Lead: {name} Date: {date}

**Comments:**

---

## Appendix

### Knowledge Base References

- `risk-governance.md` - Risk classification framework
- `probability-impact.md` - Risk scoring methodology
- `test-levels-framework.md` - Test level selection
- `test-priorities-matrix.md` - P0-P3 prioritization

### Related Documents

- PRD: Product Brief (docs/product-brief-accounting-2025-10-30.md)
- Epic: Epic 4 - Accounts Payable Module (docs/epics/epic-4-accounts-payable-ap-module.md)
- Architecture: System Architecture (docs/architecture/)
- Tech Spec: Epic 4 Tech Spec (docs/sprint-artifacts/tech-spec-epic-4.md)

### Test Framework Configuration

- **E2E Framework**: Playwright (configured in playwright.config.ts)
- **Test Location**: tests/e2e/
- **Fixtures**: tests/support/fixtures.ts (userFactory, authenticatedPage)
- **Helpers**: tests/support/helpers/ (AuthHelper, etc.)
- **Base URL**: http://localhost:5173 (frontend)
- **API URL**: http://localhost:8080/api/v1 (backend)

---

**Generated by**: BMad TEA Agent - Test Architect Module
**Workflow**: `.bmad/bmm/testarch/test-design`
**Version**: 4.0 (BMad v6)
