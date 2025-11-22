# Test Design: Epic 5 – Story 5.2 (Invoice Approval Workflow - Maker-Checker)

**Date Generated:** 2025-11-21  
**Epic:** 5 – Accounts Receivable (AR) Module  
**Story:** 5.2 – Invoice Approval Workflow (Maker-Checker)  
**Test Architect:** Murat (Master Test Architect)

---

## Executive Summary

Story 5.2 implements a threshold-based maker-checker approval workflow for sales invoices. High-value invoices (>100M VND) or flagged "sensitive" invoices are routed to the Chief Accountant for review before posting. This workflow enforces segregation of duties (creator ≠ approver), adds audit visibility, and prevents approval on closed periods.

**Risk Profile: CRITICAL** – This story directly mitigates **R5-002 (Maker-Checker Bypass)** from Epic 5 risk assessment. Authorization bypass or missing audit trails could compromise financial controls.

**Test Effort Estimate:** 45-55 hours (~6-7 days)

---

## Risk Assessment: Story 5.2 Specific

| Risk ID | Category | Description | Probability | Impact | Score | Mitigation | Owner |
|---------|----------|-------------|-------------|--------|-------|-----------|-------|
| **R5-2A** | **SEC** | Approver ≠ creator rule violated (same user approves own invoice) | 3 | 3 | **9** | Authorization layer enforces user ID check before approval | QA/Security |
| **R5-2B** | **TECH** | Approval routing logic fails (wrong user notified) | 2 | 2 | **4** | Test threshold & sensitivity flag routing | QA/Dev |
| **R5-2C** | **DATA** | Approval after period close allows posting | 2 | 3 | **6** | Period status check before approval action | QA/Dev |
| **R5-2D** | **BUS** | Approver sees stale data (change history not shown) | 2 | 2 | **4** | Change history visibility test | QA |
| **R5-2E** | **DATA** | Audit trail missing approval reason on rejection | 1 | 2 | **2** | Rejection reason stored in audit | QA/Dev |
| **R5-2F** | **TECH** | Auto-approve flag not recorded in audit | 2 | 2 | **4** | Shadow audit record for auto-approvals | QA/Dev |
| **R5-2G** | **BUS** | Email notifications not sent (approver unaware) | 2 | 2 | **4** | Email service integration test | QA/Ops |
| **R5-2H** | **PERF** | Approval timeout on large change history (10+ edits) | 1 | 1 | **1** | Load test with complex change history | QA |

---

## Story 5.2 Test Scenarios

### Story Overview

**User Story:** As a chief accountant, I want threshold-based approval for invoices, so that high-value or sensitive revenue documents are reviewed before posting.

**Acceptance Criteria:**

1. Threshold configurable by admin (default 100M VND) and rule-based sensitivity flag
2. Above-threshold or sensitive invoices route to "Pending Approval" with notifications
3. Approver must differ from creator; violation attempts blocked and logged
4. Approver UI shows invoice, attachments, change history, and projected AR impact
5. Approve posts invoice; Reject requires reason and returns to draft with message to creator
6. Approval after period close is disabled; attempts logged
7. If workflow not triggered: auto-approve; shadow "auto-approved" record stored in audit
8. All transitions (draft→pending→posted/rejected) fully audit-logged

---

## Test Scenarios by Priority

### P0 Critical Tests (11 tests, ~22 hours)

#### Test 5.2.1: Threshold-based routing (>100M VND)
**Risk Link:** R5-2B  
**Level:** E2E + API  
**Scenario:**

- GIVEN: Admin sets approval threshold to 100M VND
- WHEN: Accountant creates invoice with total > 100M VND (e.g., 150M)
- THEN: Invoice status = "PendingApproval", Chief Accountant receives notification, invoice locked from editing

**Acceptance Criteria:**
- Invoice saved with status "Pending Approval" (not Draft)
- In-app notification sent to Chief Accountant role
- Email notification sent to chief@test.example.com
- Invoice cannot be edited by creator (form disabled)
- Notification includes invoice number, amount, customer name

**Test Data:** Invoice with total 150,000,000 VND

#### Test 5.2.2: Sensitivity flag routing
**Risk Link:** R5-2B  
**Level:** E2E + API  
**Scenario:**

- GIVEN: Invoice below threshold (50M VND) but flagged as "sensitive" (e.g., related-party transaction)
- WHEN: Accountant submits invoice with sensitive flag = true
- THEN: Invoice routed to "Pending Approval" despite low amount

**Acceptance Criteria:**
- Sensitivity flag checkbox on form
- Sensitive invoices always route to approval regardless of amount
- Notification sent to Chief Accountant
- Audit log includes sensitivity reason

**Test Data:** 50M VND invoice + related-party flag

#### Test 5.2.3: Approver ≠ Creator validation (CRITICAL)
**Risk Link:** R5-2A  
**Level:** API + RBAC  
**Scenario:**

- GIVEN: Accountant created invoice pending approval
- WHEN: Same accountant attempts to approve (POST /invoices/{id}/approve with same user)
- THEN: Request rejected with 403 Forbidden

**Acceptance Criteria:**
- API validates: approver_user_id ≠ creator_user_id
- Error message: "Approver must differ from creator"
- Rejection logged in audit with "UNAUTHORIZED_APPROVAL_ATTEMPT"
- In-app alert sent to security/compliance team

**Test Data:** Invoice pending approval

#### Test 5.2.4: Approver receives notifications
**Risk Link:** R5-2G  
**Level:** E2E + Integration (email)  
**Scenario:**

- GIVEN: Invoice routed to "Pending Approval"
- WHEN: Invoice enters pending state
- THEN: Chief Accountant receives in-app notification AND email notification

**Acceptance Criteria:**
- In-app notification visible in notification center
- Email sent to approver with invoice summary
- Email includes: invoice number, amount, customer, due date, link to approval page
- Notification timestamp matches invoice pending time (±5 sec)

**Test Data:** Pending approval invoice

#### Test 5.2.5: Approver UI shows invoice details, attachments, change history, AR impact
**Risk Link:** R5-2D  
**Level:** E2E (Component)  
**Scenario:**

- GIVEN: Chief Accountant opens approval UI for pending invoice
- WHEN: Views invoice details
- THEN: Sees invoice data, attachments, edit history, and projected AR account impact

**Acceptance Criteria:**
- Invoice details section: customer, amount, tax, date, reference
- Attachments section: list of files with preview/download
- Change history: chronological list of all edits before submission
- AR impact: "DR AR (131): +150M VND" with account hierarchy
- Last edit timestamp and editor name
- All data is read-only (approver cannot edit)

**Test Data:** Complex invoice with 5+ edits and 2 attachments

#### Test 5.2.6: Approve action - posts invoice
**Risk Link:** R5-2A  
**Level:** E2E + API  
**Scenario:**

- GIVEN: Chief Accountant views pending invoice
- WHEN: Clicks "Approve" button
- THEN: Invoice status changes to "Posted", GL entries created, creator notified

**Acceptance Criteria:**
- Button: "Approve" (styled as primary action)
- Status immediately changes to "Posted" (optimistic UI)
- GL entries created: Dr AR, Cr Revenue, Cr VAT
- Audit log: APPROVED entry with approver user ID and timestamp
- Creator receives in-app notification: "Your invoice [INV-001] has been approved"
- Page redirects to approval queue

**Test Data:** Pending invoice

#### Test 5.2.7: Reject action - requires reason, returns to Draft
**Risk Link:** R5-2E  
**Level:** E2E  
**Scenario:**

- GIVEN: Chief Accountant views pending invoice
- WHEN: Clicks "Reject" button, enters reason, confirms
- THEN: Invoice returns to Draft status, reason stored in audit, creator notified

**Acceptance Criteria:**
- Button: "Reject" (styled as secondary action)
- Modal dialog opens with:
  - Reason text field (required, max 500 chars)
  - Reason template options: "Missing info", "Incorrect amount", "Duplicate", "Other"
  - "Cancel" and "Confirm Reject" buttons
- Status changes to "Draft" (can be re-edited)
- Audit log: REJECTED entry with reason and approver user ID
- Creator receives notification: "Your invoice [INV-001] was rejected. Reason: [reason text]"
- Creator can see rejection reason in invoice detail view

**Test Data:** Pending invoice, rejection reasons

#### Test 5.2.8: Approval disabled after period close
**Risk Link:** R5-2C  
**Level:** API  
**Scenario:**

- GIVEN: Pending invoice in open period, period is then closed
- WHEN: Chief Accountant attempts to approve
- THEN: Approval action is blocked with 400 Bad Request

**Acceptance Criteria:**
- Approve/Reject buttons disabled (greyed out) on closed period
- API returns 400: "Period closed, cannot approve invoices"
- Audit log: APPROVAL_BLOCKED entry with reason "period_closed"
- Clear UI message: "This period is closed. Contact finance lead to reopen."

**Test Data:** Pending invoice, closed period

#### Test 5.2.9: Auto-approve for non-triggered invoices
**Risk Link:** R5-2F  
**Level:** API  
**Scenario:**

- GIVEN: Invoice below threshold and not sensitive
- WHEN: Accountant submits invoice
- THEN: Invoice auto-posts with shadow "auto_approved" record in audit

**Acceptance Criteria:**
- Invoice status = "Posted" immediately (no approval needed)
- No notification sent (no approver needed)
- Audit log: AUTO_APPROVED entry with system actor ID
- auto_approved flag = true in audit record
- GL entries created immediately

**Test Data:** Invoice 50M VND, not sensitive

#### Test 5.2.10: Threshold configuration by admin
**Risk Link:** R5-2B  
**Level:** API (Admin endpoint)  
**Scenario:**

- GIVEN: Admin user
- WHEN: Updates approval threshold to 200M VND
- THEN: All future invoices use new threshold

**Acceptance Criteria:**
- Endpoint: PUT /api/v1/settings/approval-threshold
- Request body: { "threshold": 200000000, "currency": "VND" }
- Response: 200 OK with new threshold
- Audit log: SETTING_CHANGED entry
- New threshold applied immediately
- Old pending approvals unaffected

**Test Data:** New threshold value

#### Test 5.2.11: Full audit trail for all transitions
**Risk Link:** R5-2E  
**Level:** API  
**Scenario:**

- GIVEN: Invoice created → submitted (auto/pending) → approved/rejected
- WHEN: Fetching audit log
- THEN: All transitions recorded with actor, timestamp, reason

**Acceptance Criteria:**
- Audit entries in order: CREATED → [SUBMITTED|AUTO_APPROVED] → [APPROVED|REJECTED] → [POSTED|DRAFT_RESTORED]
- Each entry includes: action, actor_id, timestamp, before/after diffs, ip_address, user_agent
- Approval/Rejection includes reason field
- Total audit entries = number of transitions
- No audit entries missing

**Test Data:** Invoice through full lifecycle

---

### P1 High Priority Tests (7 tests, ~14 hours)

#### Test 5.2.12: Creator cannot edit invoice in Pending state
**Risk Link:** R5-2A  
**Level:** E2E + API  
**Scenario:**

- GIVEN: Invoice in Pending Approval state
- WHEN: Creator attempts PUT /invoices/{id} or clicks "Edit"
- THEN: Request rejected with 403, edit button disabled in UI

**Acceptance Criteria:**
- Edit button hidden or disabled on pending invoice
- API returns 403: "Cannot edit invoice in Pending Approval state"
- Form fields all read-only
- Creator can see why: "Awaiting approval from Chief Accountant"

#### Test 5.2.13: Approver can view change history with diffs
**Risk Link:** R5-2D  
**Level:** E2E (Component)  
**Scenario:**

- GIVEN: Invoice edited multiple times before approval
- WHEN: Approver opens change history
- THEN: Sees chronological list with before/after values for each edit

**Acceptance Criteria:**
- Change history section shows:
  - Edit #1: Date, Editor, Fields changed
  - Edit #2: Date, Editor, Fields changed
  - ...
- Each edit shows diff: "Changed amount from 100M to 150M"
- Most recent edit highlighted
- Editor name visible for audit trail

**Test Data:** Invoice with 3+ edits

#### Test 5.2.14: Multiple invoices in approval queue
**Risk Link:** R5-2B  
**Level:** E2E + API  
**Scenario:**

- GIVEN: 5 invoices pending approval (various amounts, sensitivities)
- WHEN: Chief Accountant views approval queue
- THEN: All pending invoices listed with sorting/filtering

**Acceptance Criteria:**
- Queue shows all pending invoices for user's company
- Columns: Invoice#, Customer, Amount, Days Pending, Status
- Sort by: Amount (desc), Date (recent first)
- Filter by: Customer, Date Range, Amount Range
- Pagination if >20 items
- Total pending count in header

#### Test 5.2.15: Notification dismissal and history
**Risk Link:** R5-2G  
**Level:** E2E (Component)  
**Scenario:**

- GIVEN: Chief Accountant has pending approval notification
- WHEN: Dismisses notification, then checks notification history
- THEN: Can view dismissed notifications and re-open pending items

**Acceptance Criteria:**
- Notification has "Dismiss" option
- Dismissed notifications stored (not deleted)
- History tab in notification center shows dismissed items
- Can click dismissed notification to go back to pending invoice
- Dismissing doesn't affect invoice approval status

#### Test 5.2.16: Email notification content verification
**Risk Link:** R5-2G  
**Level:** Integration (Email mock)  
**Scenario:**

- GIVEN: Invoice routed to approval
- WHEN: Email sent to approver
- THEN: Email contains invoice details, link, action buttons

**Acceptance Criteria:**
- Email subject: "Invoice [INV-001] from [Customer] requires approval"
- Email body includes:
  - Invoice number and date
  - Customer name
  - Total amount (formatted with currency)
  - Brief summary of invoice
  - "View & Approve" button/link
  - "View & Reject" button/link
- Link goes to approval page (with auth token)
- HTML formatted email (professional appearance)

#### Test 5.2.17: Period closure prevents approval (timing test)
**Risk Link:** R5-2C  
**Level:** API  
**Scenario:**

- GIVEN: Invoice pending in open period
- WHEN: Period is closed while approval UI is open (race condition)
- THEN: Approval attempt fails gracefully

**Acceptance Criteria:**
- Approve button remains enabled initially
- Click approve → API check finds period closed
- Error returned: 400 "Period closed"
- UI shows error message with refresh prompt
- Audit log: APPROVAL_BLOCKED

#### Test 5.2.18: Concurrent approval attempts (race condition)
**Risk Link:** R5-2A  
**Level:** API  
**Scenario:**

- GIVEN: Same invoice in approval, two approvers attempt approval simultaneously
- WHEN: Both send POST /invoices/{id}/approve at same time
- THEN: First succeeds, second fails with 400 "Already approved"

**Acceptance Criteria:**
- First request: 200 OK, status changes to Posted
- Second request: 400 "Invoice already approved by [first approver]"
- GL entries created only once
- Audit log has exactly one APPROVED entry
- Optimistic locking or version check prevents double-approve

---

### P2 Medium Priority Tests (5 tests, ~10 hours)

#### Test 5.2.19: Approval history view
**Risk Link:** —  
**Level:** E2E  
**Scenario:**

- GIVEN: Posted invoices with approval history
- WHEN: User views invoice detail → "Approval History" tab
- THEN: Shows approver, approval date, any rejection reason

**Acceptance Criteria:**
- Tab shows: Approver name, Approval date/time, Status
- If rejected previously, shows: Rejection date, Reason, Re-submitted date

#### Test 5.2.20: Batch approval actions (multiple invoices)
**Risk Link:** —  
**Level:** E2E + API  
**Scenario:**

- GIVEN: 10 invoices pending approval
- WHEN: Select all, click "Batch Approve"
- THEN: All approved at once (with confirmation)

**Acceptance Criteria:**
- Checkboxes on each pending invoice
- "Select All" checkbox in header
- "Batch Approve" button enabled when >1 selected
- Confirmation dialog: "Approve 10 invoices?"
- All approved in transaction (all or nothing)
- Success message: "10 invoices approved"

#### Test 5.2.21: Approval queue search and filters
**Risk Link:** —  
**Level:** E2E (Component)  
**Scenario:**

- GIVEN: 50 pending invoices
- WHEN: Search for invoice number "INV-2025-001"
- THEN: Filtered list shows matching invoice

**Acceptance Criteria:**
- Search box finds by invoice#, customer, amount
- Real-time filtering as user types
- Results count shown: "5 results found"
- Clear filter button available

#### Test 5.2.22: Mobile approval (responsive UI)
**Risk Link:** —  
**Level:** E2E (Responsive)  
**Scenario:**

- GIVEN: Approver on mobile device (iPhone 12)
- WHEN: Views pending invoice and attempts approval
- THEN: UI is fully responsive, buttons accessible

**Acceptance Criteria:**
- Approval queue fits on mobile screen
- Invoice details readable (font size, spacing)
- Approve/Reject buttons large enough to tap
- Notification bell accessible in mobile header

#### Test 5.2.23: Keyboard navigation for accessibility
**Risk Link:** —  
**Level:** E2E (Accessibility)  
**Scenario:**

- GIVEN: Approver using keyboard only (no mouse)
- WHEN: Navigates approval form with Tab, Enter
- THEN: All actions possible without mouse

**Acceptance Criteria:**
- Tab focus order logical (top to bottom)
- Approve/Reject buttons focusable with Tab
- Enter key activates focused button
- Escape key dismisses modals
- Skip to main content link available
- ARIA labels on interactive elements

---

## Test Levels Distribution

| Level | Count | Purpose |
|-------|-------|---------|
| **E2E** | 14 tests | Approval workflows, notifications, UI interactions |
| **API** | 8 tests | Threshold routing, authorization, audit, approval state transitions |
| **Component** | 3 tests | Change history display, approval queue, filters |
| **Integration** | 2 tests | Email notifications, period closure sync |
| **Total** | **27 tests** | Full story coverage |

---

## Data Factories Required

### Approval Rule Factory
```typescript
interface ApprovalRule {
  id: string;
  approvalThreshold: number; // VND
  sensitivityFlags: string[]; // ["related-party", "high-risk", etc]
  approvalRole: "chief_accountant" | "cfo"; // Who approves
  notificationEmail: string;
  requiresApprovalForAutoPost: boolean;
}

export const createApprovalRule = (overrides) => { ... }
```

### Notification Factory
```typescript
interface Notification {
  id: string;
  userId: string;
  type: "invoice_pending_approval" | "invoice_approved" | "invoice_rejected";
  relatedInvoiceId: string;
  title: string;
  message: string;
  actionUrl: string;
  isRead: boolean;
  createdAt: string;
}

export const createNotification = (invoiceId, userId) => { ... }
```

### Email Mock Factory
```typescript
interface EmailMessage {
  to: string;
  subject: string;
  htmlBody: string;
  textBody: string;
  sentAt: string;
}

export const mockEmailService = () => { ... }
export const captureEmailSent = (pattern) => { ... }
```

---

## Fixtures Required

### Approval Workflow Fixture
```typescript
export const test = base.extend<{ 
  approvalWorkflow: { 
    setupThreshold: (amount) => Promise<void>;
    getApprovalQueue: () => Promise<Invoice[]>;
    approvePending: (invoiceId) => Promise<void>;
  }
}>({ ... })
```

### Email Capture Fixture
```typescript
export const test = base.extend<{ 
  emailCapture: { 
    getCapturedEmails: () => Email[];
    findEmailBy: (pattern) => Email | null;
  }
}>({ ... })
```

---

## Implementation Checklist

### Backend (Story 5.2 Implementation)

#### Approval Threshold Configuration
- [ ] Create ApprovalRule entity in database
- [ ] Implement PUT /api/v1/settings/approval-threshold endpoint (admin only)
- [ ] Store default threshold: 100M VND
- [ ] Test: AC1 (threshold-based routing)

#### Approval State Machine
- [ ] Add invoice status: "Draft" → "PendingApproval" → "Posted" OR "Draft"
- [ ] Implement routing logic in InvoiceService.submit():
  - If amount > threshold OR sensitivity_flag → status = PendingApproval
  - If amount ≤ threshold AND no flag → auto_approve = true, status = Posted
- [ ] Test: AC1, AC7, AC9

#### Approval Endpoint
- [ ] Implement POST /api/v1/invoices/{id}/approve
- [ ] Validate: approver_user_id ≠ creator_user_id (403 if same)
- [ ] Validate: period is open (400 if closed)
- [ ] Create GL entries on approval
- [ ] Change status to Posted
- [ ] Send notification to creator
- [ ] Log audit entry (APPROVED)
- [ ] Test: AC3, AC5, AC6, AC8

#### Rejection Endpoint
- [ ] Implement POST /api/v1/invoices/{id}/reject
- [ ] Require reason field (validated, max 500 chars)
- [ ] Change status back to Draft
- [ ] Don't create GL entries
- [ ] Send notification to creator with reason
- [ ] Log audit entry (REJECTED) with reason
- [ ] Test: AC5

#### Notification System
- [ ] Create Notification entity in database
- [ ] Implement in-app notification storage
- [ ] Send email notification on approval route (async)
- [ ] Email service: SMTP or SendGrid integration
- [ ] Test: AC2, AC4

#### Audit Logging
- [ ] Log all state transitions: SUBMITTED, PENDING, APPROVED, REJECTED, POSTED, DRAFT_RESTORED
- [ ] Include approver ID, timestamp, reason (for rejection)
- [ ] For auto-approvals, include AUTO_APPROVED flag
- [ ] Test: AC8, AC11

#### Authorization
- [ ] Implement RBAC check: only Chief Accountant can approve (or CFO)
- [ ] API enforces approver ≠ creator
- [ ] UI hides approval buttons from non-approvers
- [ ] Test: AC3

### Frontend (Story 5.2 UI)

#### Approval Queue Page
- [ ] Create `/invoices/approval` page (Chief Accountant only)
- [ ] Display pending invoices table:
  - Columns: Invoice#, Customer, Amount, Days Pending, Status
  - Sort: Amount (desc), Date (recent first)
  - Filter: Customer, Date Range, Amount Range
- [ ] Pagination if >20 items
- [ ] Test: AC4, AC14

#### Approval Detail Modal
- [ ] Display full invoice with sections:
  - Details (read-only): customer, date, amount, reference
  - Line items (read-only)
  - Attachments: list with preview/download
  - Change history: chronological edits with diffs
  - AR impact: GL account impact summary
- [ ] Approve/Reject buttons
- [ ] Test: AC4, AC5

#### Rejection Modal
- [ ] Modal dialog with:
  - Reason dropdown (templates) + custom text field
  - Character count (max 500)
  - Cancel / Confirm Reject buttons
- [ ] Test: AC5

#### Notifications
- [ ] Notification bell in header
- [ ] List of pending notifications
- [ ] In-app notification badges
- [ ] Notification center with history
- [ ] Mark as read / Dismiss actions
- [ ] Test: AC2, AC4, AC15

#### Disabled States
- [ ] Approval buttons disabled if period closed
- [ ] Approval buttons disabled if not approver role
- [ ] Edit button hidden if status = PendingApproval
- [ ] Test: AC6

#### Email Template
- [ ] Create HTML email template for approval notifications
- [ ] Include invoice summary, customer, amount, due date
- [ ] Include action buttons (View & Approve, View & Reject)
- [ ] Professional styling with company branding
- [ ] Test: AC16

---

## Running Tests (RED Phase)

### E2E Tests
```bash
npx playwright test tests/e2e/invoice-approval-workflow.spec.ts
npx playwright test tests/e2e/invoice-approval-workflow.spec.ts -g "Approver.*Creator"
```

### API Tests
```bash
npx playwright test tests/api/invoice-approval-api.spec.ts
npx playwright test tests/api/invoice-approval-api.spec.ts -g "approval.*rejected"
```

### All Story 5.2 Tests
```bash
npm run test -- --grep "5.2"
```

---

## Quality Gates

**Release Decision (Story 5.2 Complete):**

- ✅ All 27 tests pass (100% green)
- ✅ No high-risk (score ≥6) items unmitigated
- ✅ Maker-Checker bypass prevented (R5-2A mitigation verified)
- ✅ Approval after period close blocked
- ✅ Approver ≠ Creator enforced
- ✅ All transitions audit-logged
- ✅ Email notifications sent and verified
- ✅ Change history visible to approver
- ✅ Auto-approval shadow records stored
- ✅ Performance: approval page load <2s
- ✅ Accessibility: keyboard navigation, ARIA labels
- ✅ Mobile responsive: approval workflow on mobile

---

## Integration with Story 5.1

Story 5.2 builds on Story 5.1:
- **Prerequisite:** Story 5.1 tests must pass first
- **Dependency:** Invoice creation flow (AC1.1 from 5.1)
- **Data Flow:** Draft invoice from 5.1 → Approval workflow (5.2) → Posted invoice
- **Shared Tests:** Some E2E tests cover 5.1 + 5.2 together (invoice creation → approval → GL posting)

---

## Epic 5 Testing Timeline

| Phase | Story | Week | Status |
|-------|-------|------|--------|
| **RED (ATDD)** | 5.1 | W1 | ✅ Complete (35 E2E + API tests) |
| **RED (ATDD)** | 5.2 | W2 | 🟢 In Progress (27 E2E + API tests) |
| **GREEN (Dev)** | 5.1 | W2-3 | 🟡 Pending |
| **GREEN (Dev)** | 5.2 | W3-4 | 🟡 Pending |
| **REFACTOR** | 5.1+5.2 | W4 | 🟡 Pending |
| **Integration** | All AR | W5 | 🟡 Pending |

---

## Document Status

✅ **Test Design Complete – Ready for ATDD Implementation**

- 27 comprehensive tests designed (P0, P1, P2)
- 8 high-risk scenarios covered
- Full authorization & audit trail validation
- Email integration testing
- Responsive & accessibility testing
- Implementation checklist created
- Ready for E2E + API test implementation (next phase)

**Next Step:** Create ATDD test files (sales-invoice-approval.spec.ts, invoice-approval-api.spec.ts)

**Last Updated:** 2025-11-21
