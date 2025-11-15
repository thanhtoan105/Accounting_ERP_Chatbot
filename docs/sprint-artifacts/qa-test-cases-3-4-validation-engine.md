# QA Test Cases: Story 3.4 - Leaf-Only and Double-Entry Validation Engine

## Overview

This document contains comprehensive QA test cases for validating the leaf-only account validation, BigDecimal double-entry validation, negative amount blocking, required dimension validation, and bulk validation features implemented in Story 3.4.

## Test Environment Setup

- **Backend**: Spring Boot application running on `http://localhost:8080`
- **Frontend**: React application running on `http://localhost:5173`
- **Database**: PostgreSQL with test data
- **Test User Roles**: Admin, Chief Accountant, Accountant

## Test Cases

### TC-3.4.1: Leaf-Only Account Validation

#### TC-3.4.1.1: UI Blocks Non-Postable Accounts in AccountPicker

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is logged in as Accountant or higher
- Chart of Accounts has both postable (leaf) and non-postable (parent) accounts

**Test Steps**:

1. Navigate to Voucher Form page (`/vouchers/new`)
2. Click on "Tài khoản Nợ" (Debit Account) dropdown
3. Search for a parent account (e.g., account code "111" which has children)
4. Observe the account in the dropdown list

**Expected Results**:

- Parent accounts (non-leaf) are displayed in the dropdown
- Parent accounts show a red badge "Không hạch toán" (Not Postable)
- Parent accounts are disabled (cannot be selected)
- Tooltip appears when hovering over disabled accounts explaining why they cannot be used
- Only leaf accounts (with `isLeaf: true`) can be selected

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.1.2: API Blocks Non-Postable Accounts

**Priority**: High  
**Type**: API Integration  
**Preconditions**:

- User has valid JWT token
- Chart of Accounts has a parent account (e.g., ID: 100, code: "111")

**Test Steps**:

1. Send POST request to `/api/v1/vouchers` with:
   ```json
   {
     "date": "2025-01-15",
     "description": "Test voucher",
     "entryLines": [
       {
         "debitAccountId": 100,
         "creditAccountId": 2,
         "amount": 1000
       }
     ]
   }
   ```
2. Include Authorization header: `Bearer <token>`
3. Include X-Company-Id header

**Expected Results**:

- Response status: `400 Bad Request`
- Response body contains:
  ```json
  {
    "error": {
      "code": "VALIDATION_ERROR",
      "message": "Validation failed",
      "details": {
        "lines": {
          "1": {
            "debitAccount": [
              "Account 111 has child accounts and cannot be used for posting. Please select a leaf level account."
            ]
          }
        }
      }
    }
  }
  ```
- Audit log entry created with action: `VALIDATION_BLOCKED`, failureReason: `NON_LEAF_ACCOUNT`

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.1.3: API Blocks Accounts with Children

**Priority**: High  
**Type**: API Integration  
**Preconditions**:

- User has valid JWT token
- Chart of Accounts has an account with children (verified via `hasChildren()` method)

**Test Steps**:

1. Create a parent account and a child account in the database
2. Send POST request to `/api/v1/vouchers` using the parent account ID
3. Verify the account has children using repository method

**Expected Results**:

- Validation fails even if account has `postable=true` but has children
- Error message indicates account has child accounts
- Audit log entry created

**Actual Results**: [To be filled during testing]

---

### TC-3.4.2: BigDecimal Double-Entry Validation

#### TC-3.4.2.1: Balanced Voucher with Rounding Tolerance

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is logged in
- Two leaf accounts available

**Test Steps**:

1. Create voucher with lines:
   - Line 1: Debit 1000.005, Credit 0
   - Line 2: Debit 0, Credit 1000.004
2. Attempt to post the voucher

**Expected Results**:

- Voucher posts successfully (difference 0.001 < tolerance 0.01)
- Journal entries created with rounded amounts (1000.01 and 1000.00)

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.2.2: Unbalanced Voucher Outside Tolerance

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is logged in
- Two leaf accounts available

**Test Steps**:

1. Create voucher with lines:
   - Line 1: Debit 1000.02, Credit 0
   - Line 2: Debit 0, Credit 1000.00
2. Attempt to post the voucher

**Expected Results**:

- Validation fails with error: "Total Debit (1000.02) does not equal Total Credit (1000.00). Difference: 0.02"
- Voucher remains in draft status
- No journal entries created

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.2.3: Perfect Balance with BigDecimal Precision

**Priority**: Medium  
**Type**: Functional  
**Preconditions**:

- User is logged in

**Test Steps**:

1. Create voucher with perfectly balanced amounts:
   - Line 1: Debit 1000.00, Credit 0
   - Line 2: Debit 0, Credit 1000.00
2. Post the voucher

**Expected Results**:

- Voucher posts successfully
- Journal entries created with exact amounts

**Actual Results**: [To be filled during testing]

---

### TC-3.4.3: Negative Amount Blocking

#### TC-3.4.3.1: UI Blocks Negative Amount Input

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is on Voucher Form page
- `allowNegative` prop is `false` (default)

**Test Steps**:

1. Enter a negative amount in the MoneyInput field (e.g., -100)
2. Attempt to save the voucher

**Expected Results**:

- MoneyInput component prevents negative values (if implemented at UI level)
- OR validation error appears: "Amount must be non-negative"
- Voucher cannot be saved

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.3.2: API Blocks Negative Amounts

**Priority**: High  
**Type**: API Integration  
**Preconditions**:

- User has valid JWT token

**Test Steps**:

1. Send POST request to `/api/v1/vouchers` with negative amount:
   ```json
   {
     "date": "2025-01-15",
     "description": "Test with negative amount",
     "entryLines": [
       {
         "debitAccountId": 1,
         "creditAccountId": 2,
         "amount": -100
       }
     ]
   }
   ```

**Expected Results**:

- Response status: `400 Bad Request`
- Response contains validation error: "Amount must be non-negative"
- Audit log entry created with:
  - Action: `FRAUD_DETECTION`
  - FailureReason: `POSSIBLE_FRAUD`
  - Metadata includes: fraudType, lineNumber, attemptedAmount, accountId, accountCode

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.3.3: Fraud Detection Audit Logging

**Priority**: High  
**Type**: Security  
**Preconditions**:

- User has valid JWT token
- Audit logging is enabled

**Test Steps**:

1. Attempt to create voucher with negative amount (via API)
2. Query audit log table for fraud detection entries

**Expected Results**:

- Audit log entry created in `audit_logs` table:
  - `action` = "FRAUD_DETECTION"
  - `event_type` = "SECURITY"
  - `failure_reason` = "POSSIBLE_FRAUD"
  - `success` = false
  - `metadata` contains: fraudType, lineNumber, attemptedAmount, accountId, accountCode
  - `user_id`, `ip_address`, `user_agent` are populated

**Actual Results**: [To be filled during testing]

---

### TC-3.4.4: Required Dimension Validation (Account Controls)

#### TC-3.4.4.1: Configure Required Dimensions via Account Controls UI

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is logged in as Chief Accountant or Admin
- Account Controls page is accessible

**Test Steps**:

1. Navigate to `/account-controls`
2. Click "Create New" button
3. Select an account (e.g., account code "1311")
4. Check "Requires Customer" checkbox
5. Save the configuration

**Expected Results**:

- Account control configuration is saved
- Configuration appears in the list
- Badge shows "Requires Customer" for the account

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.4.2: Validation Enforces Required Dimensions

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- Account control configured: Account "1311" requires customer
- User is creating a voucher

**Test Steps**:

1. Create voucher line with account "1311" (debit or credit)
2. Leave customer field empty
3. Attempt to post the voucher

**Expected Results**:

- Validation error: "Customer is required for account 1311"
- Error appears in validation summary
- Voucher cannot be posted

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.4.3: Validation Allows Voucher When Required Dimensions Provided

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- Account control configured: Account "1311" requires customer
- Customer exists in the system

**Test Steps**:

1. Create voucher line with account "1311"
2. Select a customer from the customer dropdown
3. Complete other required fields
4. Post the voucher

**Expected Results**:

- Validation passes
- Voucher posts successfully
- Journal entry includes customer dimension

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.4.4: Company-Scoped Account Controls

**Priority**: Medium  
**Type**: Integration  
**Preconditions**:

- Two companies exist (Company A and Company B)
- User has access to both companies

**Test Steps**:

1. As Company A user, configure account control for account "1311" requiring customer
2. Switch to Company B context
3. Create voucher in Company B with account "1311" without customer

**Expected Results**:

- Company B voucher does NOT require customer (account control is company-scoped)
- Voucher can be posted without customer dimension

**Actual Results**: [To be filled during testing]

---

### TC-3.4.5: Bulk Validation Error Collection

#### TC-3.4.5.1: Single-Line Validation Errors

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is creating a voucher

**Test Steps**:

1. Create voucher with one line that has multiple errors:
   - Non-leaf account
   - Negative amount
   - Missing required dimension
2. Click "Kiểm tra" (Validate) button

**Expected Results**:

- All errors for the line are displayed at once
- Error map format:
  ```json
  {
    "1": {
      "debitAccount": ["Account has child accounts..."],
      "amount": ["Amount must be non-negative"],
      "customerId": ["Customer is required..."]
    }
  }
  ```
- Validation summary shows 3 errors for line 1

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.5.2: Multi-Line Validation Errors

**Priority**: High  
**Type**: Functional  
**Preconditions**:

- User is creating a voucher

**Test Steps**:

1. Create voucher with 3 lines, each with different errors:
   - Line 1: Non-leaf account
   - Line 2: Negative amount
   - Line 3: Missing required dimension
2. Click "Kiểm tra" (Validate) button

**Expected Results**:

- All errors from all lines are displayed at once (not sequentially)
- Error map contains errors for lines 1, 2, and 3
- Validation summary modal shows:
  - Line 1: 1 error
  - Line 2: 1 error
  - Line 3: 1 error
  - Total: 3 errors

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.5.3: Field-Level Error Map Format

**Priority**: High  
**Type**: API Integration  
**Preconditions**:

- User has valid JWT token

**Test Steps**:

1. Send POST request to `/api/v1/vouchers` with multiple validation errors
2. Inspect the error response structure

**Expected Results**:

- Response follows field-level error map format:
  ```json
  {
    "error": {
      "code": "VALIDATION_ERROR",
      "message": "Validation failed",
      "details": {
        "lines": {
          "1": {
            "debitAccount": ["Error message 1", "Error message 2"],
            "amount": ["Error message 3"]
          },
          "2": {
            "creditAccount": ["Error message 4"]
          }
        }
      }
    }
  }
  ```
- Each line number maps to an object of field names to error arrays
- All errors are returned in a single response

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.5.4: Real-Time Validation Feedback

**Priority**: Medium  
**Type**: Functional  
**Preconditions**:

- User is on Voucher Form page
- Real-time validation is enabled (1 second debounce)

**Test Steps**:

1. Enter a non-leaf account in a voucher line
2. Wait 1 second (debounce period)
3. Observe validation feedback

**Expected Results**:

- After 1 second, validation runs automatically
- Error appears below the account field
- Row background changes to indicate error
- No toast notification (silent validation)
- "Đang tải…" indicator appears during validation

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.5.5: Validation Summary Modal

**Priority**: Medium  
**Type**: Functional  
**Preconditions**:

- Voucher has validation errors

**Test Steps**:

1. Create voucher with multiple validation errors
2. Click on validation summary button (shows error count)
3. Review the validation summary modal

**Expected Results**:

- Validation summary button appears showing total error count
- Modal displays:
  - All errors grouped by line number
  - Field labels for each error
  - Error count badges per line
  - "Kiểm tra lại" (Re-validate) button
- Errors are sorted by line number
- Modal can be closed and reopened

**Actual Results**: [To be filled during testing]

---

### TC-3.4.6: Audit Logging for Blocked Attempts

#### TC-3.4.6.1: Blocked Attempt Audit Log Entry

**Priority**: High  
**Type**: Security  
**Preconditions**:

- User attempts to use non-postable account

**Test Steps**:

1. Attempt to create voucher with non-postable account (via API)
2. Query audit log table

**Expected Results**:

- Audit log entry created:
  - `action` = "VALIDATION_BLOCKED"
  - `event_type` = "VALIDATION"
  - `failure_reason` = "NON_POSTABLE_ACCOUNT" or "NON_LEAF_ACCOUNT"
  - `success` = false
  - `metadata` contains: attemptType, lineNumber, fieldName, reason, accountId, accountCode
  - `user_id`, `ip_address`, `user_agent` are populated

**Actual Results**: [To be filled during testing]

---

#### TC-3.4.6.2: Fraud Detection Audit Log Entry

**Priority**: High  
**Type**: Security  
**Preconditions**:

- User attempts to enter negative amount

**Test Steps**:

1. Attempt to create voucher with negative amount (via API)
2. Query audit log table for fraud detection entries

**Expected Results**:

- Audit log entry created:
  - `action` = "FRAUD_DETECTION"
  - `event_type` = "SECURITY"
  - `failure_reason` = "POSSIBLE_FRAUD"
  - `success` = false
  - `metadata` contains: fraudType (NEGATIVE_AMOUNT, NEGATIVE_DEBIT, or NEGATIVE_CREDIT), lineNumber, attemptedAmount, accountId, accountCode
  - `user_id`, `ip_address`, `user_agent` are populated

**Actual Results**: [To be filled during testing]

---

## Test Execution Summary

### Test Coverage

- **Total Test Cases**: 20
- **High Priority**: 15
- **Medium Priority**: 5
- **Functional Tests**: 12
- **API Integration Tests**: 5
- **Security Tests**: 3

### Test Results Template

| Test Case ID | Test Case Name                             | Priority | Status | Pass/Fail | Notes |
| ------------ | ------------------------------------------ | -------- | ------ | --------- | ----- |
| TC-3.4.1.1   | UI Blocks Non-Postable Accounts            | High     |        |           |       |
| TC-3.4.1.2   | API Blocks Non-Postable Accounts           | High     |        |           |       |
| TC-3.4.1.3   | API Blocks Accounts with Children          | High     |        |           |       |
| TC-3.4.2.1   | Balanced Voucher with Rounding Tolerance   | High     |        |           |       |
| TC-3.4.2.2   | Unbalanced Voucher Outside Tolerance       | High     |        |           |       |
| TC-3.4.2.3   | Perfect Balance with BigDecimal Precision  | Medium   |        |           |       |
| TC-3.4.3.1   | UI Blocks Negative Amount Input            | High     |        |           |       |
| TC-3.4.3.2   | API Blocks Negative Amounts                | High     |        |           |       |
| TC-3.4.3.3   | Fraud Detection Audit Logging              | High     |        |           |       |
| TC-3.4.4.1   | Configure Required Dimensions              | High     |        |           |       |
| TC-3.4.4.2   | Validation Enforces Required Dimensions    | High     |        |           |       |
| TC-3.4.4.3   | Validation Allows When Dimensions Provided | High     |        |           |       |
| TC-3.4.4.4   | Company-Scoped Account Controls            | Medium   |        |           |       |
| TC-3.4.5.1   | Single-Line Validation Errors              | High     |        |           |       |
| TC-3.4.5.2   | Multi-Line Validation Errors               | High     |        |           |       |
| TC-3.4.5.3   | Field-Level Error Map Format               | High     |        |           |       |
| TC-3.4.5.4   | Real-Time Validation Feedback              | Medium   |        |           |       |
| TC-3.4.5.5   | Validation Summary Modal                   | Medium   |        |           |       |
| TC-3.4.6.1   | Blocked Attempt Audit Log Entry            | High     |        |           |       |
| TC-3.4.6.2   | Fraud Detection Audit Log Entry            | High     |        |           |       |

## Notes

- All test cases should be executed in a test environment with realistic data
- Audit log verification requires database access
- API tests should use proper authentication tokens
- Frontend tests should verify both UI behavior and API integration
- Test data should be cleaned up after test execution

## Related Documentation

- Story 3.4: Leaf-Only and Double-Entry Validation Engine
- Technical Specification: Epic 3 - Voucher Engine
- API Documentation: `/api/docs` (Swagger UI)
