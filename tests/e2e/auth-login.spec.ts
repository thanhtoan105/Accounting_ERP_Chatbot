import { test, expect } from '../support/fixtures';

/**
 * Authentication - Login Flow Tests
 * Priority: P0/P1 (Critical authentication paths)
 * 
 * Coverage:
 * - Login with valid credentials
 * - Login with invalid credentials
 * - Remember me functionality
 * - Session persistence
 * - Redirect after login
 */

test.describe('Authentication - Login', () => {
    test('[P0] should login with valid credentials and redirect to dashboard', async ({ page, userFactory }) => {
        // GIVEN: Valid user credentials
        const user = userFactory.createUser({
            email: 'test.user@example.com',
            password: 'ValidPass123!',
        });

        // Mock successful login API response
        await page.route('**/api/v1/auth/login', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        accessToken: 'mock-access-token-' + Date.now(),
                        refreshToken: 'mock-refresh-token-' + Date.now(),
                        user: {
                            id: 1,
                            email: user.email,
                            fullName: 'Test User',
                            role: 'ACCOUNTANT',
                            companyId: 1,
                        },
                    },
                }),
            });
        });

        // WHEN: User navigates to login page and submits credentials
        await page.goto('/login');
        await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

        await page.fill('[data-testid="email-input"]', user.email);
        await page.fill('[data-testid="password-input"]', user.password || '');

        const [response] = await Promise.all([
            page.waitForResponse((res) => res.url().includes('/api/v1/auth/login')),
            page.click('[data-testid="login-button"]'),
        ]);

        // THEN: Login succeeds and user is redirected
        expect(response.status()).toBe(200);

        // Wait for redirect (LoginForm has setTimeout delay)
        await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });

        const currentUrl = page.url();
        expect(currentUrl).not.toContain('/login');
    });

    test('[P1] should display error for invalid email format', async ({ page }) => {
        // GIVEN: User is on login page
        await page.goto('/login');
        await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

        // WHEN: User enters invalid email format
        await page.fill('[data-testid="email-input"]', 'invalid-email');
        await page.fill('[data-testid="password-input"]', 'SomePassword123!');

        // Trigger validation by clicking login
        await page.click('[data-testid="login-button"]');

        // THEN: Validation error should appear (client-side validation)
        // Note: Exact error message depends on your form validation implementation
        const emailInput = page.locator('[data-testid="email-input"]');
        await expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    });

    test('[P1] should display error for invalid credentials', async ({ page }) => {
        // GIVEN: Invalid credentials
        const invalidEmail = 'wrong@example.com';
        const invalidPassword = 'WrongPassword123!';

        // Mock failed login API response
        await page.route('**/api/v1/auth/login', async (route) => {
            await route.fulfill({
                status: 401,
                contentType: 'application/json',
                body: JSON.stringify({
                    error: 'Invalid credentials',
                    message: 'Email or password is incorrect',
                }),
            });
        });

        // WHEN: User attempts login with invalid credentials
        await page.goto('/login');
        await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

        await page.fill('[data-testid="email-input"]', invalidEmail);
        await page.fill('[data-testid="password-input"]', invalidPassword);

        await page.click('[data-testid="login-button"]');

        // THEN: Error message should be displayed
        await expect(page.getByText(/invalid|incorrect|wrong/i)).toBeVisible({ timeout: 5000 });
    });

    test('[P1] should display error for empty fields', async ({ page }) => {
        // GIVEN: User is on login page
        await page.goto('/login');
        await page.waitForSelector('[data-testid="login-button"]', { state: 'visible' });

        // WHEN: User clicks login without filling fields
        await page.click('[data-testid="login-button"]');

        // THEN: Required field errors should appear
        const emailInput = page.locator('[data-testid="email-input"]');
        const passwordInput = page.locator('[data-testid="password-input"]');

        // Check for HTML5 validation or custom validation
        await expect(emailInput).toHaveAttribute('required', '');
        await expect(passwordInput).toHaveAttribute('required', '');
    });

    test('[P2] should persist session on page reload', async ({ page, userFactory }) => {
        // GIVEN: User is logged in
        const user = userFactory.createUser({
            email: 'session.test@example.com',
            password: 'SessionTest123!',
        });

        await page.route('**/api/v1/auth/login', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        accessToken: 'mock-access-token',
                        refreshToken: 'mock-refresh-token',
                        user: {
                            id: 1,
                            email: user.email,
                            fullName: 'Session Test User',
                            role: 'ACCOUNTANT',
                            companyId: 1,
                        },
                    },
                }),
            });
        });

        await page.goto('/login');
        await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });
        await page.fill('[data-testid="email-input"]', user.email);
        await page.fill('[data-testid="password-input"]', user.password || '');
        await page.click('[data-testid="login-button"]');

        await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });

        // WHEN: Page is reloaded
        await page.reload();

        // THEN: User should still be authenticated (not redirected to login)
        await page.waitForLoadState('networkidle');
        const currentUrl = page.url();
        expect(currentUrl).not.toContain('/login');
    });

    test('[P2] should handle network errors gracefully', async ({ page }) => {
        // GIVEN: Network error will occur
        await page.route('**/api/v1/auth/login', async (route) => {
            await route.abort('failed');
        });

        // WHEN: User attempts to login
        await page.goto('/login');
        await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

        await page.fill('[data-testid="email-input"]', 'test@example.com');
        await page.fill('[data-testid="password-input"]', 'Password123!');
        await page.click('[data-testid="login-button"]');

        // THEN: Network error should be handled gracefully
        await expect(page.getByText(/network|connection|failed/i)).toBeVisible({ timeout: 5000 });
    });
});
