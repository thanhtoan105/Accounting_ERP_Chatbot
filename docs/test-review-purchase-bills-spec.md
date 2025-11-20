# Test Quality Review: purchase-bills.spec.ts

**Quality Score**: 72/100 (B - Acceptable)
**Review Date**: 2025-01-27
**Review Scope**: single
**Reviewer**: TEA Agent (Murat)

---

## Executive Summary

**Overall Assessment**: Acceptable

**Recommendation**: Approve with Comments

### Key Strengths

✅ Excellent network-first pattern implementation (route interception before navigation)
✅ Good BDD structure with clear Given-When-Then comments
✅ Comprehensive fixture usage with auto-cleanup (supplierFactory, purchaseBillFactory)
✅ Data factories follow best practices (faker, overrides, nested factories)
✅ Explicit assertions present throughout tests
✅ Good use of data-testid selectors for resilience

### Key Weaknesses

❌ **Critical**: Hard wait detected (line 225) - `waitForTimeout(35000)` introduces flakiness risk
❌ Missing test IDs - cannot trace tests to requirements/stories
❌ Missing priority markers (P0/P1/P2/P3) - cannot determine criticality
❌ Test file slightly exceeds 300-line guideline (301 lines)
❌ Autosave test uses hard wait instead of deterministic signal

### Summary

The purchase bills E2E tests demonstrate solid architectural patterns with network-first interception, fixture-based setup, and data factories. The tests follow BDD structure and use explicit assertions. However, a critical hard wait violation in the autosave test (35 seconds) introduces flakiness risk. Additionally, missing test IDs and priority markers reduce traceability and risk-based execution capabilities. The test file is well-structured but slightly exceeds the 300-line guideline. Address the hard wait before merging; other improvements can be handled in follow-up PRs.

---

## Quality Criteria Assessment

| Criterion                            | Status      | Violations | Notes                                    |
| ------------------------------------ | ----------- | ---------- | ---------------------------------------- |
| BDD Format (Given-When-Then)         | ✅ PASS     | 0          | Clear GWT comments in all tests          |
| Test IDs                             | ❌ FAIL     | 8          | No test IDs present (e.g., 4.1-E2E-001)  |
| Priority Markers (P0/P1/P2/P3)       | ❌ FAIL     | 8          | No priority classification                |
| Hard Waits (sleep, waitForTimeout)   | ❌ FAIL     | 1          | Line 225: 35s hard wait in autosave test |
| Determinism (no conditionals)        | ✅ PASS     | 0          | No conditionals detected                 |
| Isolation (cleanup, no shared state) | ✅ PASS     | 0          | Fixtures provide auto-cleanup            |
| Fixture Patterns                     | ✅ PASS     | 0          | Excellent fixture architecture           |
| Data Factories                       | ✅ PASS     | 0          | Comprehensive factory usage              |
| Network-First Pattern                | ✅ PASS     | 0          | Routes intercepted before navigation     |
| Explicit Assertions                  | ✅ PASS     | 0          | All tests have explicit assertions       |
| Test Length (≤300 lines)             | ⚠️ WARN     | 301        | Slightly over guideline (301 lines)      |
| Test Duration (≤1.5 min)             | ⚠️ WARN     | 1          | Autosave test requires 35s+ wait         |
| Flakiness Patterns                   | ⚠️ WARN     | 1          | Hard wait in autosave test               |

**Total Violations**: 1 Critical, 2 High, 2 Medium, 0 Low

---

## Quality Score Breakdown

```
Starting Score:          100
Critical Violations:     -1 × 10 = -10
High Violations:         -2 × 5 = -10
Medium Violations:       -2 × 2 = -4
Low Violations:          -0 × 1 = 0

Bonus Points:
  Excellent BDD:         +5
  Comprehensive Fixtures: +5
  Data Factories:        +5
  Network-First:         +5
  Perfect Isolation:     +5
  All Test IDs:          +0
                         --------
Total Bonus:             +25

Final Score:             72/100
Grade:                   B (Acceptable)
```

---

## Critical Issues (Must Fix)

### 1. Hard Wait in Autosave Test (Line 225)

**Severity**: P0 (Critical)
**Location**: `tests/e2e/purchase-bills.spec.ts:225`
**Criterion**: Hard Waits
**Knowledge Base**: [test-quality.md](../../.bmad/bmm/testarch/knowledge/test-quality.md), [network-first.md](../../.bmad/bmm/testarch/knowledge/network-first.md)

**Issue Description**:
The autosave test uses `await page.waitForTimeout(35000)` to wait for the autosave mechanism to trigger. This is a hard wait that introduces flakiness risk and makes the test slow. Hard waits are non-deterministic and can fail if the system is under load or if timing varies.

**Current Code**:

```typescript
// ❌ Bad (current implementation)
test('should autosave draft every 30 seconds', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... setup code ...
  
  // Wait for autosave (30 seconds + buffer)
  await page.waitForTimeout(35000);
  
  // THEN: Autosave API should have been called
  expect(autosaveCallCount).toBeGreaterThan(0);
  await expect(page.locator('[data-testid="autosave-indicator"]')).toBeVisible();
});
```

**Recommended Fix**:

```typescript
// ✅ Good (recommended approach)
test('should autosave draft every 30 seconds', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... setup code ...
  
  // WHEN: User enters bill data
  await page.fill('[data-testid="supplier-picker-input"]', supplier.name);
  await page.waitForSelector('[data-testid="supplier-option"]', { state: 'visible' });
  await page.click('[data-testid="supplier-option"]');
  await page.fill('[data-testid="bill-number-input"]', bill.billNumber);
  
  // Wait for autosave API call deterministically
  await page.waitForResponse(
    (response) => 
      response.url().includes('/api/v1/purchase-bills') && 
      response.url().includes('/save-draft') &&
      response.status() === 200,
    { timeout: 40000 } // Max 40s wait, but will resolve as soon as autosave fires
  );
  
  // THEN: Autosave API should have been called
  expect(autosaveCallCount).toBeGreaterThan(0);
  await expect(page.locator('[data-testid="autosave-indicator"]')).toBeVisible();
});
```

**Alternative Approach (If autosave doesn't trigger API immediately)**:

```typescript
// ✅ Alternative: Wait for autosave indicator with timeout
test('should autosave draft every 30 seconds', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... setup code ...
  
  // WHEN: User enters bill data
  await page.fill('[data-testid="supplier-picker-input"]', supplier.name);
  await page.waitForSelector('[data-testid="supplier-option"]', { state: 'visible' });
  await page.click('[data-testid="supplier-option"]');
  await page.fill('[data-testid="bill-number-input"]', bill.billNumber);
  
  // Wait for autosave indicator to appear (deterministic wait)
  await expect(page.locator('[data-testid="autosave-indicator"]')).toBeVisible({ 
    timeout: 40000 // Max 40s, but resolves as soon as indicator appears
  });
  
  // THEN: Autosave API should have been called
  expect(autosaveCallCount).toBeGreaterThan(0);
});
```

**Why This Matters**:
Hard waits (`waitForTimeout`) are non-deterministic and introduce flakiness. If the system is slow or under load, the autosave might trigger at 36 seconds instead of 30, causing the test to fail. Deterministic waits (waiting for actual signals like API responses or UI indicators) make tests reliable and faster (they resolve as soon as the condition is met, not after a fixed delay).

**Related Violations**:
This is the only hard wait in the file.

---

## Recommendations (Should Fix)

### 1. Add Test IDs for Traceability

**Severity**: P1 (High)
**Location**: `tests/e2e/purchase-bills.spec.ts:16-299`
**Criterion**: Test IDs
**Knowledge Base**: [test-quality.md](../../.bmad/bmm/testarch/knowledge/test-quality.md)

**Issue Description**:
Tests lack test IDs that link them to requirements/stories. Test IDs (e.g., `4.1-E2E-001`) enable traceability from acceptance criteria to test execution, making it easier to identify which tests cover which requirements.

**Current Code**:

```typescript
// ⚠️ Could be improved (current implementation)
test.describe('Purchase Bills - Entry, Edit, and Draft Management', () => {
  test('should create a new purchase bill with supplier and line items', async ({ page, supplierFactory, purchaseBillFactory }) => {
    // ... test code ...
  });
});
```

**Recommended Improvement**:

```typescript
// ✅ Better approach (recommended)
test.describe('Purchase Bills - Entry, Edit, and Draft Management', () => {
  test.describe('4.1-E2E-001: Purchase Bill Creation', () => {
    test('should create a new purchase bill with supplier and line items', async ({ page, supplierFactory, purchaseBillFactory }) => {
      // ... test code ...
    });
  });
  
  test.describe('4.1-E2E-002: Bill Number Validation', () => {
    test('should validate bill number uniqueness per supplier per year', async ({ page, supplierFactory, purchaseBillFactory }) => {
      // ... test code ...
    });
  });
  
  // ... other tests with IDs ...
});
```

**Benefits**:
- Enables traceability from story acceptance criteria to tests
- Makes it easy to identify which tests cover which requirements
- Supports selective test execution based on story/feature
- Improves test reporting and coverage analysis

**Priority**:
High priority (P1) because traceability is essential for maintaining test coverage and understanding which requirements are validated by which tests.

---

### 2. Add Priority Markers for Risk-Based Execution

**Severity**: P1 (High)
**Location**: `tests/e2e/purchase-bills.spec.ts:16-299`
**Criterion**: Priority Markers
**Knowledge Base**: [test-priorities-matrix.md](../../.bmad/bmm/testarch/knowledge/test-priorities-matrix.md)

**Issue Description**:
Tests lack priority classification (P0/P1/P2/P3), making it impossible to run critical tests first or skip low-priority tests in fast feedback loops. Priority markers enable risk-based test execution.

**Current Code**:

```typescript
// ⚠️ Could be improved (current implementation)
test('should create a new purchase bill with supplier and line items', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... test code ...
});
```

**Recommended Improvement**:

```typescript
// ✅ Better approach (recommended)
// P0: Critical user journey - bill creation
test('should create a new purchase bill with supplier and line items', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... test code ...
});

// P1: Important validation - duplicate detection
test('should validate bill number uniqueness per supplier per year', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... test code ...
});

// P2: Nice-to-have - date picker validation
test('should disable future dates in bill date picker', async ({ page }) => {
  // ... test code ...
});
```

**Alternative: Use Playwright test tags**:

```typescript
// ✅ Alternative: Use test tags for priority
test('should create a new purchase bill with supplier and line items', { tag: '@p0' }, async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... test code ...
});

test('should validate bill number uniqueness per supplier per year', { tag: '@p1' }, async ({ page, supplierFactory, purchaseBillFactory }) => {
  // ... test code ...
});

// Run only P0 tests: npx playwright test --grep @p0
```

**Benefits**:
- Enables risk-based test execution (run P0 tests first in CI)
- Supports fast feedback loops (skip P2/P3 tests in pre-commit hooks)
- Makes test criticality explicit
- Improves CI/CD efficiency

**Priority**:
High priority (P1) because priority classification enables efficient test execution strategies and helps teams focus on critical paths first.

---

### 3. Consider Splitting Test File (301 Lines)

**Severity**: P2 (Medium)
**Location**: `tests/e2e/purchase-bills.spec.ts` (entire file)
**Criterion**: Test Length
**Knowledge Base**: [test-quality.md](../../.bmad/bmm/testarch/knowledge/test-quality.md)

**Issue Description**:
The test file has 301 lines, slightly exceeding the 300-line guideline. While not critical, splitting the file into focused test suites would improve maintainability and make it easier to navigate.

**Current Structure**:
- Single file with 8 tests covering multiple concerns:
  - Bill creation
  - Validation (duplicate, dates, VAT)
  - Autosave
  - Error handling
  - Posted bill restrictions

**Recommended Improvement**:

```typescript
// ✅ Better approach: Split into focused files

// tests/e2e/purchase-bills-creation.spec.ts (150 lines)
test.describe('Purchase Bills - Creation', () => {
  test('should create a new purchase bill with supplier and line items', ...);
  test('should auto-calculate due date from bill date and payment terms', ...);
});

// tests/e2e/purchase-bills-validation.spec.ts (120 lines)
test.describe('Purchase Bills - Validation', () => {
  test('should validate bill number uniqueness per supplier per year', ...);
  test('should disable future dates in bill date picker', ...);
  test('should validate VAT sum match between header and line items', ...);
});

// tests/e2e/purchase-bills-draft.spec.ts (100 lines)
test.describe('Purchase Bills - Draft Management', () => {
  test('should autosave draft every 30 seconds', ...);
});

// tests/e2e/purchase-bills-errors.spec.ts (80 lines)
test.describe('Purchase Bills - Error Handling', () => {
  test('should display multi-error summary footer on save', ...);
  test('should block edit/delete for posted bills', ...);
});
```

**Benefits**:
- Each file focuses on one concern (creation, validation, drafts, errors)
- Easier to navigate and understand
- Faster to locate specific tests
- Better parallel execution (smaller files = faster discovery)

**Priority**:
Medium priority (P2) because the file is only slightly over the guideline. This is a nice-to-have improvement that can be done in a follow-up refactoring.

---

## Best Practices Found

### 1. Excellent Network-First Pattern Implementation

**Location**: `tests/e2e/purchase-bills.spec.ts:22-34, 76-85, 134-140, etc.`
**Pattern**: Network-First Route Interception
**Knowledge Base**: [network-first.md](../../.bmad/bmm/testarch/knowledge/network-first.md)

**Why This Is Good**:
All tests correctly register route interceptions **before** navigation, preventing race conditions. This is the foundational pattern for reliable E2E tests.

**Code Example**:

```typescript
// ✅ Excellent pattern demonstrated in this test
test('should create a new purchase bill with supplier and line items', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // GIVEN: Supplier exists and user is authenticated
  const supplier = supplierFactory.createSupplier();
  
  // Intercept supplier API calls BEFORE navigation
  await page.route('**/api/v1/suppliers*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: [supplier],
        total: 1,
        page: 0,
        size: 10,
        totalPages: 1,
      }),
    });
  });

  // Navigate to purchase bills page (AFTER interception is set up)
  await page.goto('/accounting/purchase-bills');
  
  // ... rest of test ...
});
```

**Use as Reference**:
This pattern should be used in all E2E tests that interact with APIs. Always intercept routes before navigation to prevent race conditions.

---

### 2. Comprehensive Fixture Architecture with Auto-Cleanup

**Location**: `tests/support/fixtures/index.ts`, used throughout tests
**Pattern**: Pure Function → Fixture → Auto-Cleanup
**Knowledge Base**: [fixture-architecture.md](../../.bmad/bmm/testarch/knowledge/fixture-architecture.md)

**Why This Is Good**:
The fixture system follows best practices: factories are pure functions, wrapped in fixtures with automatic cleanup. This ensures tests are isolated and don't pollute state.

**Code Example**:

```typescript
// ✅ Excellent fixture pattern
export const test = base.extend<TestFixtures>({
  supplierFactory: async ({ request }, use) => {
    const factory = new SupplierFactory();
    
    await use(factory);
    
    // Auto-cleanup: Delete all suppliers created during test
    await factory.cleanup(apiRequest);
  },
  
  purchaseBillFactory: async ({ request }, use) => {
    const factory = new PurchaseBillFactory();
    
    await use(factory);
    
    // Auto-cleanup: Delete all bills created during test
    await factory.cleanup(apiRequest);
  },
});
```

**Use as Reference**:
This fixture pattern ensures tests are isolated and can run in parallel without state pollution. All fixtures should follow this pattern with automatic cleanup.

---

### 3. Excellent Data Factory Usage

**Location**: `tests/support/fixtures/factories/supplier-factory.ts`, `purchase-bill-factory.ts`
**Pattern**: Factory Functions with Overrides and Faker
**Knowledge Base**: [data-factories.md](../../.bmad/bmm/testarch/knowledge/data-factories.md)

**Why This Is Good**:
Factories use faker for unique data generation, accept overrides for explicit test intent, and support nested factories. This prevents collisions in parallel execution and makes test data clear.

**Code Example**:

```typescript
// ✅ Excellent factory pattern
export class SupplierFactory {
  createSupplier(overrides: Partial<Supplier> = {}): Supplier {
    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      name: faker.company.name(),
      email: faker.internet.email(),
      // ... other fields with faker defaults
      ...overrides, // Explicit overrides show test intent
    };
  }
}

// Usage in tests shows clear intent
const supplier = supplierFactory.createSupplier({ name: 'Test Supplier' });
```

**Use as Reference**:
All test data should be created via factories with faker defaults and explicit overrides. This pattern prevents collisions and makes test intent clear.

---

### 4. Clear BDD Structure with Given-When-Then Comments

**Location**: Throughout all tests
**Pattern**: BDD Format
**Knowledge Base**: [test-quality.md](../../.bmad/bmm/testarch/knowledge/test-quality.md)

**Why This Is Good**:
All tests have clear Given-When-Then comments that make test intent explicit and easy to understand.

**Code Example**:

```typescript
// ✅ Excellent BDD structure
test('should validate bill number uniqueness per supplier per year', async ({ page, supplierFactory, purchaseBillFactory }) => {
  // GIVEN: Supplier exists and a bill with same number already exists
  const supplier = supplierFactory.createSupplier();
  const existingBill = purchaseBillFactory.createPostedBill({
    supplierId: supplier.id!,
    billNumber: 'BILL-2024-001',
    billDate: '2024-01-15',
  });
  
  // ... setup code ...
  
  // WHEN: User enters duplicate bill number for same supplier
  await page.fill('[data-testid="supplier-picker-input"]', supplier.name);
  await page.fill('[data-testid="bill-number-input"]', 'BILL-2024-001');
  
  // THEN: Duplicate error should be displayed and edit should be disabled
  await expect(page.locator('[data-testid="bill-number-error"]')).toBeVisible();
  await expect(page.locator('[data-testid="save-bill-button"]')).toBeDisabled();
});
```

**Use as Reference**:
All tests should follow this BDD structure with clear Given-When-Then comments. This makes tests self-documenting and easy to understand.

---

## Test File Analysis

### File Metadata

- **File Path**: `tests/e2e/purchase-bills.spec.ts`
- **File Size**: 301 lines, ~12 KB
- **Test Framework**: Playwright
- **Language**: TypeScript

### Test Structure

- **Describe Blocks**: 1 (main suite)
- **Test Cases (it/test)**: 8
- **Average Test Length**: ~37 lines per test
- **Fixtures Used**: 2 (supplierFactory, purchaseBillFactory)
- **Data Factories Used**: 2 (SupplierFactory, PurchaseBillFactory)

### Test Coverage Scope

- **Test IDs**: None (missing)
- **Priority Distribution**:
  - P0 (Critical): 0 tests (should be classified)
  - P1 (High): 0 tests (should be classified)
  - P2 (Medium): 0 tests (should be classified)
  - P3 (Low): 0 tests (should be classified)
  - Unknown: 8 tests

### Assertions Analysis

- **Total Assertions**: ~24 assertions across 8 tests
- **Assertions per Test**: ~3 (avg)
- **Assertion Types**: `toBeVisible()`, `toHaveText()`, `toContainText()`, `toHaveValue()`, `toBeDisabled()`, `not.toBeVisible()`, `toBeGreaterThan()`

---

## Context and Integration

### Related Artifacts

- **Story File**: Story 4-1 (Purchase Bills Entry, Edit, and Draft Management) - referenced in sprint-status.yaml
- **Acceptance Criteria Mapped**: Unable to verify without story file access
- **Test Design**: Not found (would help with priority classification)

### Acceptance Criteria Validation

Unable to map tests to acceptance criteria without access to story file. Recommend:
1. Adding test IDs that reference story acceptance criteria
2. Linking tests to story file for traceability
3. Documenting which acceptance criteria each test covers

---

## Knowledge Base References

This review consulted the following knowledge base fragments:

- **[test-quality.md](../../.bmad/bmm/testarch/knowledge/test-quality.md)** - Definition of Done for tests (no hard waits, <300 lines, <1.5 min, self-cleaning)
- **[fixture-architecture.md](../../.bmad/bmm/testarch/knowledge/fixture-architecture.md)** - Pure function → Fixture → mergeTests pattern
- **[network-first.md](../../.bmad/bmm/testarch/knowledge/network-first.md)** - Route intercept before navigate (race condition prevention)
- **[data-factories.md](../../.bmad/bmm/testarch/knowledge/data-factories.md)** - Factory functions with overrides, API-first setup
- **[test-priorities-matrix.md](../../.bmad/bmm/testarch/knowledge/test-priorities-matrix.md)** - P0/P1/P2/P3 classification framework

See [tea-index.csv](../../.bmad/bmm/testarch/tea-index.csv) for complete knowledge base.

---

## Next Steps

### Immediate Actions (Before Merge)

1. **Fix Hard Wait in Autosave Test** - Replace `waitForTimeout(35000)` with deterministic wait
   - Priority: P0 (Critical)
   - Owner: Developer
   - Estimated Effort: 15 minutes
   - Location: `tests/e2e/purchase-bills.spec.ts:225`

### Follow-up Actions (Future PRs)

1. **Add Test IDs** - Add test IDs (e.g., 4.1-E2E-001) to all tests for traceability
   - Priority: P1 (High)
   - Target: Next sprint
   - Estimated Effort: 30 minutes

2. **Add Priority Markers** - Classify tests as P0/P1/P2/P3 for risk-based execution
   - Priority: P1 (High)
   - Target: Next sprint
   - Estimated Effort: 30 minutes

3. **Consider File Splitting** - Split 301-line file into focused test suites
   - Priority: P2 (Medium)
   - Target: Backlog
   - Estimated Effort: 1 hour

### Re-Review Needed?

⚠️ Re-review after critical fix - request changes, then re-review after hard wait is fixed.

---

## Decision

**Recommendation**: Approve with Comments

**Rationale**:
Test quality is acceptable with 72/100 score. The tests demonstrate excellent architectural patterns (network-first, fixtures, data factories, BDD structure) and have explicit assertions. However, one critical issue must be addressed: the hard wait in the autosave test (line 225) introduces flakiness risk and should be replaced with a deterministic wait before merging. Additionally, missing test IDs and priority markers reduce traceability and risk-based execution capabilities, but these can be addressed in follow-up PRs. The test file is well-structured and follows most best practices.

**For Approve with Comments**:

> Test quality is acceptable with 72/100 score. Critical issue (hard wait) must be fixed before merge. High-priority recommendations (test IDs, priority markers) should be addressed but don't block merge. Tests demonstrate excellent patterns (network-first, fixtures, factories) and follow BDD structure. Address hard wait, then approve.

---

## Appendix

### Violation Summary by Location

| Line   | Severity      | Criterion   | Issue                    | Fix                              |
| ------ | ------------- | ----------- | ------------------------ | -------------------------------- |
| 225    | P0 (Critical) | Hard Waits  | `waitForTimeout(35000)`  | Use `waitForResponse` or element wait |
| 16-299 | P1 (High)     | Test IDs    | No test IDs present      | Add test IDs (e.g., 4.1-E2E-001) |
| 16-299 | P1 (High)     | Priorities  | No priority markers      | Add P0/P1/P2/P3 classification   |
| 301    | P2 (Medium)   | Test Length | File exceeds 300 lines   | Consider splitting into focused files |
| 192-230| P2 (Medium)   | Duration    | Autosave test requires 35s+ | Optimize with deterministic wait |

---

## Review Metadata

**Generated By**: BMad TEA Agent (Test Architect)
**Workflow**: testarch-test-review v4.0
**Review ID**: test-review-purchase-bills-spec-20250127
**Timestamp**: 2025-01-27
**Version**: 1.0

---

## Feedback on This Review

If you have questions or feedback on this review:

1. Review patterns in knowledge base: `.bmad/bmm/testarch/knowledge/`
2. Consult tea-index.csv for detailed guidance
3. Request clarification on specific violations
4. Pair with QA engineer to apply patterns

This review is guidance, not rigid rules. Context matters - if a pattern is justified, document it with a comment.

