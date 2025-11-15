# Test Framework Documentation

This directory contains the end-to-end (E2E) test suite for the accounting application, built with **Playwright** and following production-ready testing patterns.

## Table of Contents

- [Setup Instructions](#setup-instructions)
- [Running Tests](#running-tests)
- [Architecture Overview](#architecture-overview)
- [Best Practices](#best-practices)
- [CI Integration](#ci-integration)
- [Knowledge Base References](#knowledge-base-references)
- [Troubleshooting](#troubleshooting)

---

## Setup Instructions

### Prerequisites

- Node.js 20.11.0 (see `.nvmrc`)
- pnpm (or npm/yarn)
- Playwright browsers installed

### Installation

1. **Install dependencies** (if not already installed):
   ```bash
   pnpm install
   ```

2. **Install Playwright browsers**:
   ```bash
   npx playwright install --with-deps
   ```

3. **Configure environment variables**:
   - Copy `.env.example` to `.env` (if it exists)
   - Fill in your environment-specific values:
     ```bash
     BASE_URL=http://localhost:5173
     API_URL=http://localhost:8080
     TEST_USER_EMAIL=test@example.com
     TEST_USER_PASSWORD=TestPassword123!
     ```

4. **Start the application** (if not using Playwright's webServer):
   ```bash
   # Start backend
   cd backend && mvn spring-boot:run
   
   # Start frontend (in another terminal)
   cd frontend && pnpm dev
   ```

---

## Running Tests

### Basic Commands

```bash
# Run all tests
pnpm test:e2e

# Run tests in UI mode (interactive)
pnpm test:e2e:ui

# Run tests in headed mode (see browser)
pnpm test:e2e:headed

# Run tests in debug mode
pnpm test:e2e:debug

# View test report
pnpm test:e2e:report
```

### Running Specific Tests

```bash
# Run a specific test file
npx playwright test tests/e2e/example.spec.ts

# Run tests matching a pattern
npx playwright test --grep "login"

# Run tests in a specific browser
npx playwright test --project=chromium

# Run tests with specific tags
npx playwright test --grep @smoke
```

### Environment-Specific Execution

```bash
# Run against local environment (default)
BASE_URL=http://localhost:5173 npx playwright test

# Run against staging
BASE_URL=https://staging.example.com npx playwright test
```

---

## Architecture Overview

### Directory Structure

```
tests/
├── e2e/                    # Test files (organize by feature)
│   └── example.spec.ts     # Example test suite
├── support/                # Framework infrastructure (key pattern)
│   ├── fixtures/          # Test fixtures (data, mocks)
│   │   ├── factories/     # Data factories (faker-based)
│   │   │   └── user-factory.ts
│   │   └── index.ts        # Main fixture exports
│   ├── helpers/           # Utility functions
│   │   └── api-request.ts  # API request helper
│   └── page-objects/      # Page object models (optional)
└── README.md              # This file
```

### Fixture Architecture

**Pattern: Pure Function → Fixture → Composition**

Our test infrastructure follows a composable fixture pattern:

1. **Pure Functions**: Framework-agnostic helpers (e.g., `apiRequest`)
2. **Fixtures**: Playwright-specific wrappers that inject dependencies
3. **Composition**: Use `mergeTests` to combine capabilities

**Example Usage**:

```typescript
import { test, expect } from '../support/fixtures';

test('user can create order', async ({ page, userFactory }) => {
  // Create test user via factory
  const user = userFactory.createUser({ email: 'test@example.com' });
  
  // Seed via API (fast!)
  // await apiRequest({ method: 'POST', url: '/api/users', data: user });
  
  // Test UI
  await page.goto('/orders');
  await page.click('[data-testid="create-order"]');
  
  // Assert
  await expect(page.getByText('Order created')).toBeVisible();
  
  // Auto-cleanup: userFactory.cleanup() runs automatically
});
```

### Data Factories

**Pattern: Factory Functions with Overrides**

Factories generate test data with sensible defaults and explicit overrides:

```typescript
import { UserFactory } from '../support/fixtures/factories/user-factory';

const factory = new UserFactory();

// Default user
const user = factory.createUser();

// Admin user (explicit override)
const admin = factory.createAdminUser({ email: 'admin@example.com' });
```

**Key Benefits**:
- **Parallel-safe**: UUIDs and timestamps prevent collisions
- **Schema evolution**: Defaults adapt to schema changes
- **Explicit intent**: Overrides show what matters for each test
- **Auto-cleanup**: Factories track created entities for automatic cleanup

### API-First Setup

**Principle**: Seed test state through APIs, never via slow UI interactions.

```typescript
// ✅ GOOD: API setup (fast)
const user = userFactory.createUser();
await apiRequest({ method: 'POST', url: '/api/users', data: user });
await page.goto('/dashboard'); // UI is for validation only

// ❌ BAD: UI setup (slow, brittle)
await page.goto('/register');
await page.fill('[data-testid="email"]', 'test@example.com');
// ... 10 more UI steps
```

---

## Best Practices

### Selector Strategy

**Always use `data-testid` attributes**:

```typescript
// ✅ GOOD
await page.click('[data-testid="submit-button"]');
await page.fill('[data-testid="email-input"]', email);

// ❌ BAD: Brittle CSS selectors
await page.click('.btn-primary');
await page.fill('#email', email);
```

### Test Isolation

- **Each test is independent**: No shared state between tests
- **Auto-cleanup**: Fixtures handle cleanup automatically
- **Parallel execution**: Tests can run in parallel safely

### Test Structure

Follow **Given-When-Then** pattern:

```typescript
test('user can login', async ({ page, userFactory }) => {
  // Given: Test user exists
  const user = userFactory.createUser();
  
  // When: User logs in
  await page.goto('/login');
  await page.fill('[data-testid="email"]', user.email);
  await page.click('[data-testid="login-button"]');
  
  // Then: User is redirected to dashboard
  await expect(page).toHaveURL(/.*dashboard/);
});
```

### Timeout Standards

- **Action timeout**: 15s (click, fill, etc.)
- **Navigation timeout**: 30s (page.goto, page.reload)
- **Expect timeout**: 15s (assertions)
- **Test timeout**: 60s (entire test)

Override only when necessary:

```typescript
// Per-assertion override
await expect(element).toBeVisible({ timeout: 20000 });

// Per-test override (use fixture)
test.setTimeout(180000); // 3 minutes for slow test
```

### Network Interception

**Pattern: Intercept before navigate**

```typescript
test('handles API errors', async ({ page }) => {
  // Set up route interception BEFORE navigation
  await page.route('**/api/orders', (route) => {
    route.fulfill({
      status: 500,
      body: JSON.stringify({ error: 'Server Error' }),
    });
  });
  
  // Now navigate
  await page.goto('/orders');
  
  // Assert error handling
  await expect(page.getByText(/error/i)).toBeVisible();
});
```

### Failure Artifacts

Artifacts are captured **only on failure**:
- **Screenshots**: `test-results/` directory
- **Videos**: `test-results/` directory (retain on failure)
- **Traces**: `test-results/` directory (retain on failure)

View traces:
```bash
npx playwright show-trace test-results/trace.zip
```

---

## CI Integration

### GitHub Actions Example

```yaml
name: E2E Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version-file: '.nvmrc'
      
      - name: Install dependencies
        run: pnpm install
      
      - name: Install Playwright browsers
        run: npx playwright install --with-deps
      
      - name: Run tests
        run: pnpm test:e2e
        env:
          BASE_URL: ${{ secrets.STAGING_URL }}
          API_URL: ${{ secrets.STAGING_API_URL }}
      
      - name: Upload test results
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: test-results/
          retention-days: 30
      
      - name: Upload Playwright report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: test-results/html/
          retention-days: 30
```

### CI Configuration

- **Retries**: 2 retries in CI (configured in `playwright.config.ts`)
- **Workers**: 1 worker in CI for stability
- **Artifacts**: Uploaded on failure with 30-day retention
- **Parallelization**: Use sharding for large test suites

---

## Knowledge Base References

This test framework follows patterns from the TEA (Test Architect) knowledge base:

- **Fixture Architecture** (`.bmad/bmm/testarch/knowledge/fixture-architecture.md`)
  - Pure function → fixture → mergeTests composition
  - Auto-cleanup patterns
  - Composable capabilities

- **Data Factories** (`.bmad/bmm/testarch/knowledge/data-factories.md`)
  - Factory functions with overrides
  - Faker-based data generation
  - API-first setup patterns

- **Playwright Configuration** (`.bmad/bmm/testarch/knowledge/playwright-config.md`)
  - Environment-based configuration
  - Timeout standards
  - Artifact output configuration
  - Parallelization settings

- **Network-First Safeguards** (`.bmad/bmm/testarch/knowledge/network-first.md`)
  - Intercept-before-navigate workflow
  - Deterministic waits
  - Edge case mocking

---

## Troubleshooting

### Tests Fail with "Timeout"

- **Check BASE_URL**: Ensure the application is running
- **Increase timeout**: Use `test.setTimeout()` for slow operations
- **Check network**: Verify API endpoints are accessible

### Tests Fail in CI but Pass Locally

- **Check environment variables**: Ensure CI has correct BASE_URL, API_URL
- **Check browser installation**: Run `npx playwright install --with-deps` in CI
- **Check parallelization**: Reduce workers if tests are flaky

### Artifacts Not Generated

- **Check failure status**: Artifacts only generated on failure
- **Check output directory**: Verify `test-results/` directory exists
- **Check permissions**: Ensure write permissions for test-results/

### Fixture Cleanup Not Working

- **Verify apiRequest**: Ensure cleanup has access to API request context
- **Check API endpoints**: Verify DELETE endpoints exist and work
- **Check logs**: Look for cleanup warnings in test output

### Selector Not Found

- **Verify data-testid**: Ensure elements have `data-testid` attributes
- **Check timing**: Use `await expect()` instead of immediate checks
- **Check visibility**: Ensure element is visible before interaction

---

## Next Steps

1. **Review example tests**: See `tests/e2e/example.spec.ts` for patterns
2. **Add your tests**: Create new test files in `tests/e2e/`
3. **Extend factories**: Add more factories in `tests/support/fixtures/factories/`
4. **Add helpers**: Create utility functions in `tests/support/helpers/`
5. **Set up CI**: Configure CI pipeline to run tests automatically

---

## Additional Resources

- [Playwright Documentation](https://playwright.dev)
- [Playwright Best Practices](https://playwright.dev/docs/best-practices)
- [Testing Library Best Practices](https://kentcdodds.com/blog/common-mistakes-with-react-testing-library)

---

**Framework**: Playwright  
**Version**: 1.56.1  
**Last Updated**: 2025-01-XX

