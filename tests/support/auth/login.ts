import { Page, expect } from '@playwright/test';

/**
 * Shared Login UI Helper
 *
 * Centralizes all login form interaction logic.
 * Used by both global-setup (real auth) and auth-helper (real/mock auth).
 */

export interface LoginViaUIOptions {
  email: string;
  password: string;
  waitForAuthResponse?: boolean;
  postLoginUrl?: string | RegExp;
  timeout?: number;
  handleCompanySelection?: boolean;
}

const SELECTORS = {
  emailInput: '[data-testid="email-input"], input[name="email"]',
  passwordInput: '[data-testid="password-input"], input[name="password"]',
  loginButton: '[data-testid="login-button"], button[type="submit"]',
};

/**
 * Perform login via the UI form.
 *
 * @param page - Playwright page instance
 * @param options - Login configuration
 * @returns Promise that resolves when login is complete
 */
export async function loginViaUI(
  page: Page,
  options: LoginViaUIOptions,
): Promise<void> {
  const {
    email,
    password,
    waitForAuthResponse = false,
    postLoginUrl = /\/(dashboard)?$/,
    timeout = 30000,
    handleCompanySelection = true,
  } = options;

  await page.goto('/login');

  await page.waitForSelector(SELECTORS.emailInput, {
    state: 'visible',
    timeout,
  });

  const emailInput = page.locator(SELECTORS.emailInput).first();
  const passwordInput = page.locator(SELECTORS.passwordInput).first();
  const loginButton = page.locator(SELECTORS.loginButton).first();

  await emailInput.fill(email);
  await passwordInput.fill(password);

  if (waitForAuthResponse) {
    const [response] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/auth/login') && res.status() === 200,
        { timeout },
      ),
      loginButton.click(),
    ]);
    expect(response.status()).toBe(200);
  } else {
    await loginButton.click();
  }

  await page.waitForURL(
    (url) => {
      if (typeof postLoginUrl === 'string') {
        return url.pathname === postLoginUrl || url.pathname.includes(postLoginUrl);
      }
      return postLoginUrl.test(url.pathname);
    },
    { timeout },
  );

  await expect(page).not.toHaveURL(/\/login/);

  if (handleCompanySelection) {
    const currentUrl = page.url();
    if (currentUrl.includes('/company')) {
      await page.goto('/');
      await page.waitForLoadState('networkidle');
    }
  }
}

/**
 * Verify authentication by checking localStorage for token.
 */
export async function verifyAuthenticated(page: Page): Promise<boolean> {
  return page.evaluate(() => !!localStorage.getItem('accessToken'));
}

/**
 * Wait for page to stabilize after login.
 */
export async function waitForPostLoginStabilization(
  page: Page,
  timeout: number = 5000,
): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout });
}
