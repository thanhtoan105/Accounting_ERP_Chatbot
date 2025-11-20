import { Page, expect } from '@playwright/test';

/**
 * Authentication Helper
 * 
 * Provides helper functions for test authentication setup.
 * Uses network-first pattern: intercept auth API before navigation.
 */
export async function loginAsUser(
  page: Page,
  email: string = 'admin@example.com',
  password: string = 'password',
  role: string = 'admin',
  useRealAuth: boolean = true,
) {
  if (useRealAuth) {
    // Use real authentication - navigate to login and submit
    await page.goto('/login');

    // Wait for login form to be ready
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    // Fill login form and submit
    await page.fill('[data-testid="email-input"]', email);
    await page.fill('[data-testid="password-input"]', password);

    // Click login button and wait for API call to complete
    const [response] = await Promise.all([
      page.waitForResponse(
        (response) => response.url().includes('/api/v1/auth/login') && response.status() === 200,
        { timeout: 10000 },
      ),
      page.click('[data-testid="login-button"]'),
    ]);

    // Verify API response
    expect(response.status()).toBe(200);

    // Wait for navigation away from login page (LoginForm redirects after 1.5s)
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });

    // Verify we're logged in (not on login page)
    const currentUrl = page.url();
    expect(currentUrl).not.toContain('/login');
    
    // Wait for the redirect to complete (LoginForm uses setTimeout 1500ms)
    await page.waitForTimeout(2000);
    
    // Check final URL after redirect
    const finalUrl = page.url();
    
    // If we're on company selection page, navigate to home
    if (finalUrl.includes('/company')) {
      await page.goto('/');
      await page.waitForLoadState('networkidle');
    }
    
    // Verify authentication by checking localStorage for token
    const hasToken = await page.evaluate(() => {
      return !!localStorage.getItem('accessToken');
    });
    
    if (!hasToken) {
      throw new Error('Authentication failed - no access token in localStorage');
    }
  } else {
    // Mock authentication (for CI/testing without backend)
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
              email,
              fullName: 'Test User',
              role,
              companyId: 1,
            },
          },
        }),
      });
    });

    await page.goto('/login');
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });
    await page.fill('[data-testid="email-input"]', email);
    await page.fill('[data-testid="password-input"]', password);

    const [response] = await Promise.all([
      page.waitForResponse(
        (response) => response.url().includes('/api/v1/auth/login') && response.status() === 200,
      ),
      page.click('[data-testid="login-button"]'),
    ]);

    expect(response.status()).toBe(200);
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });
  }
}

