# E2E Test Plan - Accounting System Frontend

## Overview

This document outlines the comprehensive E2E testing strategy for the accounting frontend application using Playwright.

**Technology Stack:**
- **Test Framework:** Playwright
- **Language:** TypeScript
- **Pattern:** Page Object Model (POM)
- **Authentication:** StorageState per role

---

## Test Categories & Priority Matrix

| Priority | Category | Description | CI Strategy |
|----------|----------|-------------|-------------|
| **P0** | Smoke/Critical | Auth + Core accounting operations | Every PR |
| **P1** | Transaction Workflows | AP/AR transaction flows | Daily/Main branch |
| **P2** | Reports & Analytics | VAT, aging, trial balance | Scheduled/Feature merge |
| **P3** | Edge Cases & Error Handling | Validation, error states | Weekly |

---

## Test Cases

### 1. Authentication Module

| ID | Name | Page | Function |
|----|------|------|----------|
| AUTH-001 | Login with valid credentials | `/login` | Verify successful login and redirect to dashboard |
| AUTH-002 | Login with invalid credentials | `/login` | Verify error message for wrong password |
| AUTH-003 | Login with non-existent email | `/login` | Verify appropriate error message |
| AUTH-004 | Logout from application | Dashboard | Verify session termination and redirect to login |
| AUTH-005 | Forgot password request | `/forgot-password` | Verify email sent confirmation |
| AUTH-006 | Reset password with valid token | `/reset-password` | Verify password update success |
| AUTH-007 | Reset password with invalid token | `/reset-password` | Verify error for expired/invalid token |
| AUTH-008 | Accept invitation with valid token | `/invite/:token` | Verify account activation |
| AUTH-009 | Accept invitation with expired token | `/invite/:token` | Verify expiration error message |
| AUTH-010 | Remember me functionality | `/login` | Verify session persistence |

### 2. Dashboard & Navigation

| ID | Name | Page | Function |
|----|------|------|----------|
| NAV-001 | Dashboard loads with data | `/` | Verify widgets and summary data display |
| NAV-002 | Sidebar navigation works | Dashboard | Test all menu items navigate correctly |
| NAV-003 | Awaiting company redirect | `/awaiting-company` | User without company sees awaiting page |
| NAV-004 | Breadcrumb navigation | All pages | Verify breadcrumbs update correctly |
| NAV-005 | Role-based menu visibility | Dashboard | Menu items match user role permissions |

### 3. Customer Management

| ID | Name | Page | Function |
|----|------|------|----------|
| CUST-001 | Create customer with valid data | `/customers` | Fill form, save, verify in list |
| CUST-002 | Create customer - validation errors | `/customers` | Test required fields, email format, phone format |
| CUST-003 | Edit existing customer | `/customers` | Modify customer details, verify update |
| CUST-004 | Delete customer | `/customers` | Delete with confirmation, verify removal |
| CUST-005 | Search customers by name | `/customers` | Search functionality works |
| CUST-006 | Filter customers by status | `/customers` | Active/inactive filter works |
| CUST-007 | Activate/Deactivate customer | `/customers` | Toggle customer status |
| CUST-008 | Customer tax code validation | `/customers` | Validate 10-digit tax code format |
| CUST-009 | View customer AR summary | `/customers/:id` | Display accounts receivable summary |
| CUST-010 | Export customers | `/customers` | Export to CSV/Excel |
| CUST-011 | Import customers | `/customers` | Bulk import from file |

### 4. Supplier Management

| ID | Name | Page | Function |
|----|------|------|----------|
| SUPP-001 | Create supplier with valid data | `/suppliers` | Fill form, save, verify in list |
| SUPP-002 | Create supplier - validation errors | `/suppliers` | Test required fields, email, phone format |
| SUPP-003 | Edit existing supplier | `/suppliers` | Modify supplier details, verify update |
| SUPP-004 | Delete supplier | `/suppliers` | Delete with confirmation, verify removal |
| SUPP-005 | Search suppliers by name | `/suppliers` | Search functionality works |
| SUPP-006 | Filter suppliers by status | `/suppliers` | Active/inactive filter works |
| SUPP-007 | Activate/Deactivate supplier | `/suppliers` | Toggle supplier status |
| SUPP-008 | View supplier AP summary | `/suppliers/:id` | Display accounts payable summary |
| SUPP-009 | Export suppliers | `/suppliers` | Export to CSV/Excel |
| SUPP-010 | Import suppliers | `/suppliers` | Bulk import from file |

### 5. Voucher Management

| ID | Name | Page | Function |
|----|------|------|----------|
| VOUC-001 | Create voucher with single line | `/vouchers` | Create basic voucher with debit/credit |
| VOUC-002 | Create voucher with multi-line | `/vouchers` | Multiple journal lines balanced |
| VOUC-003 | Voucher validation - unbalanced | `/vouchers` | Error when debit != credit |
| VOUC-004 | Post voucher | `/vouchers` | Change status from draft to posted |
| VOUC-005 | Unpost voucher | `/vouchers` | Revert posted voucher to draft |
| VOUC-006 | Reverse voucher | `/vouchers` | Create reversal entry |
| VOUC-007 | Validate voucher before post | `/vouchers` | Run validation checks |
| VOUC-008 | Edit draft voucher | `/vouchers` | Modify and save changes |
| VOUC-009 | Delete draft voucher | `/vouchers` | Remove unposted voucher |
| VOUC-010 | Attach file to voucher | `/vouchers` | Upload attachment |
| VOUC-011 | View voucher details | `/vouchers/:id` | Display full voucher info |
| VOUC-012 | Filter vouchers by date range | `/vouchers` | Date filter works |
| VOUC-013 | Filter vouchers by type | `/vouchers` | Voucher type filter |
| VOUC-014 | Search vouchers | `/vouchers` | Search by number/description |
| VOUC-015 | Use voucher template | `/vouchers` | Create from template |

### 6. Sales Invoice (AR)

| ID | Name | Page | Function |
|----|------|------|----------|
| INV-001 | Create sales invoice draft | `/sales-invoices` | Fill form with line items |
| INV-002 | Create invoice - customer required | `/sales-invoices` | Validate customer selection |
| INV-003 | Add line items to invoice | `/sales-invoices` | Multiple items with VAT calculation |
| INV-004 | Submit invoice for approval | `/sales-invoices` | Change status to pending approval |
| INV-005 | Approve sales invoice | `/sales-invoices` | Approver approves invoice |
| INV-006 | Reject sales invoice | `/sales-invoices` | Approver rejects with reason |
| INV-007 | Edit draft invoice | `/sales-invoices` | Modify before submission |
| INV-008 | Delete draft invoice | `/sales-invoices` | Remove draft invoice |
| INV-009 | Create credit note | `/sales-invoices` | Issue credit against invoice |
| INV-010 | View invoice details | `/sales-invoices/:id` | Display invoice with history |
| INV-011 | Attach file to invoice | `/sales-invoices` | Upload supporting documents |
| INV-012 | Invoice number sequencing | `/sales-invoices` | Auto-generate unique numbers |
| INV-013 | Filter by invoice status | `/sales-invoices` | Draft/Pending/Approved filter |
| INV-014 | Filter by customer | `/sales-invoices` | Customer filter |
| INV-015 | Batch import invoices | `/sales-invoices` | Bulk import from file |

### 7. Purchase Bill (AP)

| ID | Name | Page | Function |
|----|------|------|----------|
| BILL-001 | Create purchase bill draft | `/purchase-bills` | Fill form with line items |
| BILL-002 | Create bill - supplier required | `/purchase-bills` | Validate supplier selection |
| BILL-003 | Add line items to bill | `/purchase-bills` | Multiple items with VAT |
| BILL-004 | Submit bill for approval | `/purchase-bills` | Change status to pending |
| BILL-005 | Approve purchase bill | `/purchase-bills` | Approver approves bill |
| BILL-006 | Reject purchase bill | `/purchase-bills` | Approver rejects with reason |
| BILL-007 | Edit draft bill | `/purchase-bills` | Modify before submission |
| BILL-008 | Delete draft bill | `/purchase-bills` | Remove draft bill |
| BILL-009 | Attach file to bill | `/purchase-bills` | Upload supporting documents |
| BILL-010 | Filter by bill status | `/purchase-bills` | Draft/Pending/Approved filter |
| BILL-011 | Filter by supplier | `/purchase-bills` | Supplier filter |
| BILL-012 | Batch import bills | `/purchase-bills` | Bulk import from file |

### 8. Payments (AP)

| ID | Name | Page | Function |
|----|------|------|----------|
| PAY-001 | Create payment | `/payments` | Record payment to supplier |
| PAY-002 | Allocate payment to bill | `/payments` | Manual allocation |
| PAY-003 | Auto-allocate payment FIFO | `/payments` | FIFO allocation |
| PAY-004 | Partial payment allocation | `/payments` | Allocate partial amount |
| PAY-005 | Post payment | `/payments` | Finalize payment |
| PAY-006 | Cancel payment | `/payments` | Cancel unposted payment |
| PAY-007 | Reverse payment | `/payments` | Reverse posted payment |
| PAY-008 | Approve payment | `/payments` | Approval workflow |
| PAY-009 | Reject payment | `/payments` | Rejection with reason |
| PAY-010 | View payment details | `/payments/:id` | Display payment info |
| PAY-011 | Filter by payment status | `/payments` | Status filter |
| PAY-012 | Filter by supplier | `/payments` | Supplier filter |

### 9. Receipts (AR)

| ID | Name | Page | Function |
|----|------|------|----------|
| REC-001 | Create receipt | `/accounting/receipts` | Record receipt from customer |
| REC-002 | Allocate receipt to invoice | `/accounting/receipts` | Manual allocation |
| REC-003 | Partial receipt allocation | `/accounting/receipts` | Allocate partial amount |
| REC-004 | Post receipt | `/accounting/receipts` | Finalize receipt |
| REC-005 | Reverse receipt | `/accounting/receipts` | Reverse posted receipt |
| REC-006 | Attach file to receipt | `/accounting/receipts` | Upload documents |
| REC-007 | View receipt details | `/accounting/receipts/:id` | Display receipt info |
| REC-008 | Filter by receipt status | `/accounting/receipts` | Status filter |
| REC-009 | Filter by customer | `/accounting/receipts` | Customer filter |
| REC-010 | Import receipts | `/accounting/receipts` | Bulk import |

### 10. Bank Accounts

| ID | Name | Page | Function |
|----|------|------|----------|
| BANK-001 | Create bank account | `/bank-accounts` | Add new bank account |
| BANK-002 | Bank account validation | `/bank-accounts` | Validate account number format |
| BANK-003 | Edit bank account | `/bank-accounts` | Modify bank details |
| BANK-004 | Delete bank account | `/bank-accounts` | Remove bank account |
| BANK-005 | View bank account list | `/bank-accounts` | Display all accounts |

### 11. Reports

| ID | Name | Page | Function |
|----|------|------|----------|
| RPT-001 | Trial balance report | `/accounting/trial-balance` | Generate trial balance |
| RPT-002 | Cash book report | `/accounting/cash-book` | Generate cash book |
| RPT-003 | VAT report run | `/vat/reports` | Run VAT report for period |
| RPT-004 | VAT report export | `/vat/reports` | Export VAT data |
| RPT-005 | VAT corrections | `/vat/corrections` | Submit VAT corrections |
| RPT-006 | AP aging report | `/ap-aging` | Generate AP aging |
| RPT-007 | AR aging report | `/ar-aging` | Generate AR aging |
| RPT-008 | AR statements | `/ar-statements` | Customer statements |
| RPT-009 | Report date range filter | Reports | Filter by date range |
| RPT-010 | Report export PDF | Reports | Export to PDF |
| RPT-011 | Report export Excel | Reports | Export to Excel |

### 12. Analytics Dashboard

| ID | Name | Page | Function |
|----|------|------|----------|
| ANLY-001 | Analytics dashboard access | `/analytics` | Admin/CFO can access |
| ANLY-002 | Dashboard widgets load | `/analytics` | All widgets display data |
| ANLY-003 | Date range selector | `/analytics` | Filter by period |
| ANLY-004 | Chart interactions | `/analytics` | Hover/click on charts |

### 13. User Management

| ID | Name | Page | Function |
|----|------|------|----------|
| USER-001 | View user list | `/users` | Display all users |
| USER-002 | Invite new user | `/users` | Send invitation email |
| USER-003 | Edit user role | `/users` | Change user permissions |
| USER-004 | Deactivate user | `/users` | Disable user account |
| USER-005 | User profile update | `/profile` | Update own profile |
| USER-006 | Change password | `/profile` | Update own password |

### 14. Admin - Tenant Management

| ID | Name | Page | Function |
|----|------|------|----------|
| ADMIN-001 | View tenant list | `/admin/tenants` | Super admin sees all tenants |
| ADMIN-002 | Create tenant | `/admin/tenants` | Add new tenant |
| ADMIN-003 | Edit tenant | `/admin/tenants` | Modify tenant settings |
| ADMIN-004 | Tenant access denied | `/admin/tenants` | Non-super_admin redirected |

### 15. Role-Based Access Control (RBAC)

| ID | Name | Page | Function |
|----|------|------|----------|
| RBAC-001 | Super admin full access | All routes | Can access everything |
| RBAC-002 | Admin limited access | Admin routes | Cannot access super_admin pages |
| RBAC-003 | Chief accountant access | Accounting routes | Has full accounting access |
| RBAC-004 | Accountant access | Accounting routes | Limited to accountant functions |
| RBAC-005 | CFO report access | Report routes | Can view analytics and reports |
| RBAC-006 | Unauthorized redirect | Protected routes | Redirect to login or denied |
| RBAC-007 | Awaiting company redirect | Any protected | No company users redirected |

### 16. Form Validation

| ID | Name | Page | Function |
|----|------|------|----------|
| VAL-001 | Required field validation | All forms | Empty required shows error |
| VAL-002 | Email format validation | Forms with email | Invalid email shows error |
| VAL-003 | Phone format validation | Forms with phone | Invalid format shows error |
| VAL-004 | Tax code validation | Customer/Supplier | 10-digit validation |
| VAL-005 | Date validation | Date fields | Invalid date handling |
| VAL-006 | Numeric field validation | Amount fields | Non-numeric shows error |
| VAL-007 | Duplicate detection | Create forms | Duplicate ref warning |

### 17. Error Handling

| ID | Name | Page | Function |
|----|------|------|----------|
| ERR-001 | API 500 error display | Any page | Toast/banner for server error |
| ERR-002 | Network timeout handling | Any page | Timeout message display |
| ERR-003 | Session expired handling | Any page | Redirect to login |
| ERR-004 | 404 page display | Invalid route | 404 page shown |
| ERR-005 | Permission denied message | Restricted routes | 403 message display |

---

## Multi-Step Workflow Tests

### Workflow 1: Complete Invoice-to-Receipt Flow

```
1. Create customer → 2. Create sales invoice → 3. Submit for approval
→ 4. Approve invoice → 5. Record receipt → 6. Allocate to invoice
→ 7. Verify AR aging updated
```

| ID | Name | Steps |
|----|------|-------|
| WF-001 | Invoice to receipt complete flow | Full AR cycle |

### Workflow 2: Complete Bill-to-Payment Flow

```
1. Create supplier → 2. Create purchase bill → 3. Submit for approval
→ 4. Approve bill → 5. Record payment → 6. Allocate to bill
→ 7. Verify AP aging updated
```

| ID | Name | Steps |
|----|------|-------|
| WF-002 | Bill to payment complete flow | Full AP cycle |

### Workflow 3: Voucher Lifecycle

```
1. Create voucher → 2. Validate → 3. Post → 4. Unpost → 5. Edit
→ 6. Revalidate → 7. Post → 8. Reverse
```

| ID | Name | Steps |
|----|------|-------|
| WF-003 | Voucher lifecycle complete | Draft to reverse |

### Workflow 4: User Invitation Flow

```
1. Admin invites user → 2. Email sent → 3. User accepts invitation
→ 4. User sets password → 5. User logs in → 6. User sees dashboard
```

| ID | Name | Steps |
|----|------|-------|
| WF-004 | User invitation complete flow | Invite to active |

---

## Smoke Test Suite (P0 - Every PR)

| Test ID | Description |
|---------|-------------|
| AUTH-001 | Login with valid credentials |
| AUTH-004 | Logout from application |
| NAV-001 | Dashboard loads with data |
| CUST-001 | Create customer with valid data |
| SUPP-001 | Create supplier with valid data |
| VOUC-001 | Create voucher with single line |
| VOUC-004 | Post voucher |
| INV-001 | Create sales invoice draft |
| INV-004 | Submit invoice for approval |
| INV-005 | Approve sales invoice |
| BILL-001 | Create purchase bill draft |
| BILL-005 | Approve purchase bill |
| PAY-001 | Create payment |
| PAY-002 | Allocate payment to bill |
| REC-001 | Create receipt |
| REC-002 | Allocate receipt to invoice |
| RPT-001 | Trial balance report |
| RBAC-004 | Tenant access denied for accountant |

---

## Test Data Requirements

### Users per Role

| Role | Email | Purpose |
|------|-------|---------|
| super_admin | superadmin@test.com | Full system access |
| admin | admin@test.com | Company admin operations |
| chief_accountant | chief@test.com | Full accounting access |
| accountant | accountant@test.com | Standard accounting |
| cfo | cfo@test.com | Report/analytics access |

### Seed Data

- 1 tenant with company configured
- 1 user without company (awaiting-company test)
- 5 sample customers
- 5 sample suppliers
- 1 bank account
- Chart of accounts
- Voucher types

---

## Folder Structure

```
tests/
├── e2e/
│   ├── auth/
│   │   ├── login.spec.ts
│   │   ├── logout.spec.ts
│   │   ├── forgot-password.spec.ts
│   │   └── invitation.spec.ts
│   ├── navigation/
│   │   └── dashboard.spec.ts
│   ├── customers/
│   │   ├── create-customer.spec.ts
│   │   ├── update-customer.spec.ts
│   │   └── delete-customer.spec.ts
│   ├── suppliers/
│   │   ├── create-supplier.spec.ts
│   │   ├── update-supplier.spec.ts
│   │   └── delete-supplier.spec.ts
│   ├── vouchers/
│   │   ├── create-voucher.spec.ts
│   │   ├── post-voucher.spec.ts
│   │   └── reverse-voucher.spec.ts
│   ├── sales-invoices/
│   │   ├── create-invoice.spec.ts
│   │   ├── approval-workflow.spec.ts
│   │   └── credit-note.spec.ts
│   ├── purchase-bills/
│   │   ├── create-bill.spec.ts
│   │   └── approval-workflow.spec.ts
│   ├── payments/
│   │   ├── create-payment.spec.ts
│   │   └── allocation.spec.ts
│   ├── receipts/
│   │   ├── create-receipt.spec.ts
│   │   └── allocation.spec.ts
│   ├── reports/
│   │   ├── trial-balance.spec.ts
│   │   ├── vat-reports.spec.ts
│   │   └── aging-reports.spec.ts
│   ├── admin/
│   │   ├── tenant-management.spec.ts
│   │   └── user-management.spec.ts
│   ├── rbac/
│   │   └── role-access.spec.ts
│   ├── workflows/
│   │   ├── invoice-receipt-flow.spec.ts
│   │   ├── bill-payment-flow.spec.ts
│   │   └── voucher-lifecycle.spec.ts
│   └── validation/
│       ├── form-validation.spec.ts
│       └── error-handling.spec.ts
├── pages/
│   ├── BasePage.ts
│   ├── LoginPage.ts
│   ├── DashboardPage.ts
│   ├── CustomersPage.ts
│   ├── SuppliersPage.ts
│   ├── VouchersPage.ts
│   ├── InvoicesPage.ts
│   ├── BillsPage.ts
│   ├── PaymentsPage.ts
│   └── ReceiptsPage.ts
├── fixtures/
│   ├── test-fixtures.ts
│   └── test-data.ts
├── auth.setup.ts
├── global-setup.ts
└── global-teardown.ts
```

---

## Execution Commands

```bash
# Run all E2E tests
pnpm test:e2e

# Run smoke tests only
pnpm test:e2e -- --grep @smoke

# Run specific module
pnpm test:e2e -- tests/e2e/customers

# Run in UI mode
pnpm test:e2e:ui

# Generate report
pnpm test:e2e && npx playwright show-report
```

---

## Coverage Summary

| Priority | Scenarios | Run Frequency |
|----------|-----------|---------------|
| P0 (Smoke) | ~20 | Every PR |
| P1 (Workflows) | ~16 | Daily/Main branch |
| P2 (Reports) | ~12 | Feature merges |
| P3 (Edge cases) | ~14 | Weekly |
| **Total** | **~62** | |

---

## Implementation Phases

### Phase 1: Foundation (Week 1)
- Setup Playwright configuration
- Create Page Objects for core pages
- Implement auth fixtures (storageState per role)
- AUTH module tests

### Phase 2: Core CRUD (Week 2)
- Customer management tests
- Supplier management tests
- Bank account tests
- Navigation tests

### Phase 3: Accounting Operations (Week 3)
- Voucher management tests
- Sales invoice tests
- Purchase bill tests

### Phase 4: Transactions (Week 4)
- Payment tests with allocation
- Receipt tests with allocation
- Multi-step workflow tests

### Phase 5: Reports & RBAC (Week 5)
- Report generation tests
- VAT reporting tests
- Role-based access tests
- Admin/tenant tests

### Phase 6: Polish (Week 6)
- Validation tests
- Error handling tests
- CI/CD integration
- Documentation
