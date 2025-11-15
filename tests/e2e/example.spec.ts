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

    // Mock login API response (since backend may not be available in CI)
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'mock-access-token',
          refreshToken: 'mock-refresh-token',
          user: {
            id: 1,
            email: user.email,
            fullName: 'Test User',
            role: 'USER',
            companyId: 1,
          },
        }),
      });
    });

    // When: Navigate to login page
    await page.goto('/login');

    // Wait for login form to be ready
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    // Then: Fill login form and submit
    await page.fill('[data-testid="email-input"]', user.email);
    await page.fill('[data-testid="password-input"]', user.password || '');
    
    // Wait for navigation after clicking login button
    // LoginForm redirects after 1.5s (setTimeout), so we wait for URL change
    await Promise.all([
      page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 5000 }),
      page.click('[data-testid="login-button"]'),
    ]);
    
    // Assert: Login success - we should be redirected away from login page
    const currentUrl = page.url();
    expect(currentUrl).not.toContain('/login');
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

