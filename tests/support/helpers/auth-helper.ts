import { Page, expect } from '@playwright/test';
import {
  loginViaUI,
  verifyAuthenticated,
  waitForPostLoginStabilization,
} from '../auth/login';
import { getUserByRole, type TestRole } from '../auth/users';

/**
 * Authentication Helper
 *
 * Provides helper functions for test authentication setup.
 * Uses network-first pattern: intercept auth API before navigation.
 */

/**
 * Setup mock authentication state for fully mocked E2E tests.
 *
 * This function bypasses the login page entirely by:
 * 1. Setting up route intercepts for all auth-related APIs
 * 2. Directly injecting authentication tokens into localStorage via addInitScript
 * 3. Mocking the /me endpoint for user info
 *
 * Use this for tests that mock ALL API calls and don't require a real backend.
 */
export async function setupMockAuth(
  page: Page,
  email: string = 'accountant@example.com',
  role: string = 'accountant',
  companyId: number = 1,
) {
  const userId = role === 'chief_accountant' ? 2 : 1;
  const fullName = role === 'chief_accountant' ? 'Chief Accountant' : 'Test User';

  await page.route('**/api/v1/auth/**', async (route) => {
    const url = route.request().url();

    if (url.includes('/login')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            accessToken: 'mock-access-token-' + Date.now(),
            refreshToken: 'mock-refresh-token-' + Date.now(),
            user: { id: userId, email, fullName, role, companyId },
          },
        }),
      });
    } else if (url.includes('/refresh')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            accessToken: 'mock-access-token-refreshed-' + Date.now(),
            refreshToken: 'mock-refresh-token-refreshed-' + Date.now(),
          },
        }),
      });
    } else if (url.includes('/me')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: { id: userId, email, fullName, role, companyId },
        }),
      });
    } else {
      await route.fulfill({ status: 200, body: '{}' });
    }
  });

  await page.route('**/api/v1/companies/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: { id: companyId, name: 'Test Company', code: 'TEST' },
      }),
    });
  });

  await page.addInitScript(
    ({ email, role, userId, fullName, companyId }) => {
      localStorage.setItem('accessToken', 'mock-access-token-' + Date.now());
      localStorage.setItem('refreshToken', 'mock-refresh-token-' + Date.now());
      localStorage.setItem('activeCompanyId', String(companyId));
      localStorage.setItem(
        'user',
        JSON.stringify({
          id: userId,
          email,
          fullName,
          role,
          companyId,
        }),
      );
    },
    { email, role, userId, fullName, companyId },
  );
}

/**
 * Setup mock authentication for a specific role.
 * Uses centralized user definitions.
 */
export async function setupMockAuthForRole(
  page: Page,
  role: TestRole,
  companyId: number = 1,
) {
  const user = getUserByRole(role, true);
  await setupMockAuth(page, user.email, role, companyId);
}

/**
 * Login as a user via UI.
 *
 * @param page - Playwright page
 * @param email - User email (defaults to admin@example.com)
 * @param password - User password (defaults to 'password')
 * @param role - User role (defaults to 'admin')
 * @param useRealAuth - If true, performs real login; if false, mocks the auth API
 */
export async function loginAsUser(
  page: Page,
  email: string = 'admin@example.com',
  password: string = 'password',
  role: string = 'admin',
  useRealAuth: boolean = true,
) {
  if (useRealAuth) {
    await loginViaUI(page, {
      email,
      password,
      waitForAuthResponse: true,
      postLoginUrl: '/',
      timeout: 15000,
      handleCompanySelection: true,
    });

    await waitForPostLoginStabilization(page, 5000);

    const hasToken = await verifyAuthenticated(page);
    if (!hasToken) {
      throw new Error('Authentication failed - no access token in localStorage');
    }
  } else {
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

    await loginViaUI(page, {
      email,
      password,
      waitForAuthResponse: true,
      postLoginUrl: '/',
      timeout: 10000,
      handleCompanySelection: false,
    });
  }
}

/**
 * Login as a specific role using centralized credentials.
 * Convenience method that uses TEST_USERS definitions.
 */
export async function loginAsRole(
  page: Page,
  role: TestRole,
  options: { mode?: 'real' | 'mock' } = { mode: 'real' },
) {
  const useMock = options.mode === 'mock';
  const user = getUserByRole(role, useMock);

  await loginAsUser(page, user.email, user.password, role, !useMock);
}
