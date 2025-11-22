import { test as base, expect } from '@playwright/test';

/**
 * Authentication Fixture
 * 
 * Provides pre-authenticated user contexts for E2E tests
 * Handles login/logout and user role setup
 */

interface UserCredentials {
  email: string;
  password: string;
  role: 'admin' | 'accountant' | 'chief_accountant' | 'cfo';
  name: string;
}

const TEST_USERS: Record<string, UserCredentials> = {
  accountant: {
    email: 'accountant@test.example.com',
    password: 'Test@123456',
    role: 'accountant',
    name: 'Test Accountant',
  },
  chief_accountant: {
    email: 'chief@test.example.com',
    password: 'Test@123456',
    role: 'chief_accountant',
    name: 'Chief Accountant',
  },
  cfo: {
    email: 'cfo@test.example.com',
    password: 'Test@123456',
    role: 'cfo',
    name: 'CFO User',
  },
  admin: {
    email: 'admin@test.example.com',
    password: 'Test@123456',
    role: 'admin',
    name: 'Admin User',
  },
};

/**
 * Authentication context fixture
 * Auto-logs in user before test, logs out after
 */
export const test = base.extend<{ authenticatedUser: UserCredentials; authenticate: (role: string) => Promise<void> }>({
  authenticatedUser: async ({ page }, use) => {
    // Default to accountant
    const user = TEST_USERS.accountant;

    // Setup: Login
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', user.email);
    await page.fill('[data-testid="password-input"]', user.password);
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('/dashboard');

    // Verify authentication token in localStorage
    const token = await page.evaluate(() => localStorage.getItem('token'));
    expect(token).toBeTruthy();

    // Provide user to test
    await use(user);

    // Cleanup: Logout
    await page.click('[data-testid="user-menu-button"]');
    await page.click('[data-testid="logout-button"]');
    await page.waitForURL('/login');
  },

  authenticate: async ({ page }, use) => {
    const authenticate = async (role: string) => {
      const user = TEST_USERS[role] || TEST_USERS.accountant;

      await page.goto('/login');
      await page.fill('[data-testid="email-input"]', user.email);
      await page.fill('[data-testid="password-input"]', user.password);
      await page.click('[data-testid="login-button"]');
      await page.waitForURL('/dashboard');

      const token = await page.evaluate(() => localStorage.getItem('token'));
      expect(token).toBeTruthy();
    };

    await use(authenticate);

    // Cleanup
    await page.goto('/login');
  },
});

export { expect };
