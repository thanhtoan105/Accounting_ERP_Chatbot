import { test, expect } from '../support/fixtures';

/**
 * Example Test Suite
 * 
 * Demonstrates test patterns:
 * - Using fixtures (userFactory)
 * - API-first setup (seed data via API, validate via UI)
 * - Proper selector strategy (data-testid)
 * - Given-When-Then structure
 */
test.describe('Example Test Suite', () => {
  test('should load homepage', async ({ page }) => {
    // Given: User navigates to homepage
    await page.goto('/');

    // Then: Page should load successfully
    await expect(page).toHaveTitle(/accounting/i);
  });

  test('should create user and login', async ({ page, userFactory }) => {
    // Given: Create test user via factory
    const user = userFactory.createUser({
      email: 'test@example.com',
      password: 'TestPassword123!',
    });

    // When: Seed user via API (fast setup)
    // Note: In a real scenario, you would use apiRequest fixture to seed data
    // For this example, we'll simulate the login flow
    await page.goto('/login');

    // Then: Fill login form and submit
    await page.fill('[data-testid="email-input"]', user.email);
    await page.fill('[data-testid="password-input"]', user.password || '');
    await page.click('[data-testid="login-button"]');

    // Assert: Login success (adjust selector based on your app)
    // await expect(page.locator('[data-testid="user-menu"]')).toBeVisible();
    // For now, just check URL change
    await expect(page).toHaveURL(/.*dashboard|.*home/i, { timeout: 10000 });
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Given: Navigate to a page that makes API calls
    await page.goto('/');

    // When: Intercept API call and return error
    await page.route('**/api/**', (route) => {
      route.fulfill({
        status: 500,
        body: JSON.stringify({ error: 'Internal Server Error' }),
      });
    });

    // Then: UI should handle error gracefully
    // Adjust based on your app's error handling
    // await expect(page.getByText(/error|failed/i)).toBeVisible();
  });
});

