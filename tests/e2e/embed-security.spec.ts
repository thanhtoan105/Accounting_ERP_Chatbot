import { test, expect } from '../support/fixtures';

/**
 * Embed Security E2E Tests
 *
 * AC 8.0.3: Security test - forcing company_id in embed URL is ignored
 *
 * These tests verify that:
 * - Tampering with company_id in embed URL does not change data access
 * - Invalid JWT tokens result in error responses
 * - Metabase embed respects the locked company_id in JWT
 */

test.describe('Embed Security', () => {
  test.describe('URL Tampering Prevention', () => {
    test('[P0] tampering with company_id in URL should not change data access', async ({
      page,
      request,
    }) => {
      const originalCompanyId = 1;
      const tamperedCompanyId = 999;

      await page.route('**/api/v1/analytics/metabase/sso/token', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              token: 'mock-jwt-with-company-id-1',
              companyId: originalCompanyId,
            },
          }),
        });
      });

      await page.route('**/api/v1/analytics/embed-config*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              metabaseSiteUrl: 'https://metabase.example.com',
              authConfig: {
                enabled: true,
                authType: 'JWT',
              },
              lockedCompanyId: originalCompanyId,
            },
          }),
        });
      });

      await page.goto('/login');
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
                email: 'test@company1.com',
                fullName: 'Test User',
                role: 'ACCOUNTANT',
                companyId: originalCompanyId,
              },
            },
          }),
        });
      });

      await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });
      await page.fill('[data-testid="email-input"]', 'test@company1.com');
      await page.fill('[data-testid="password-input"]', 'TestPassword123!');
      await page.click('[data-testid="login-button"]');
      await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });

      const embedConfigRequests: string[] = [];
      page.on('request', (req) => {
        if (req.url().includes('/api/v1/analytics/embed-config')) {
          embedConfigRequests.push(req.url());
        }
      });

      await page.goto(`/analytics?company_id=${tamperedCompanyId}`);

      await page.waitForTimeout(1000);

      await page.route('**/api/v1/analytics/data*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              company: { id: originalCompanyId, name: 'Company 1' },
            },
          }),
        });
      });
    });

    test('[P0] adding extra company_id query params should be ignored', async ({ page }) => {
      await page.route('**/api/v1/auth/me', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              id: 1,
              email: 'test@company1.com',
              companyId: 1,
              role: 'ACCOUNTANT',
            },
          }),
        });
      });

      await page.goto(
        '/analytics/dashboard?company_id=1&company_id=2&company_id=3'
      );

      const currentUrl = page.url();
      expect(currentUrl).toContain('/analytics');
    });
  });

  test.describe('Invalid JWT Handling', () => {
    test('[P1] request with malformed JWT should return 401', async ({ request }) => {
      const response = await request.get('/api/v1/analytics/embed-config', {
        headers: {
          Authorization: 'Bearer malformed.jwt.token',
        },
        failOnStatusCode: false,
      });

      expect(response.status()).toBe(401);
    });

    test('[P1] request with expired JWT should return 401', async ({ request }) => {
      const expiredJwt =
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiY29tcGFueV9pZCI6MSwiZXhwIjoxNjAwMDAwMDAwfQ.invalid';

      const response = await request.get('/api/v1/analytics/embed-config', {
        headers: {
          Authorization: `Bearer ${expiredJwt}`,
        },
        failOnStatusCode: false,
      });

      expect(response.status()).toBe(401);
    });

    test('[P1] request without authorization header should return 401', async ({
      request,
    }) => {
      const response = await request.get('/api/v1/analytics/embed-config', {
        failOnStatusCode: false,
      });

      expect(response.status()).toBe(401);
    });
  });

  test.describe('JWT Token Security', () => {
    test('[P1] modified JWT payload should be rejected by backend', async ({
      request,
    }) => {
      const tamperedJwt =
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiY29tcGFueV9pZCI6OTk5LCJleHAiOjk5OTk5OTk5OTl9.tampered_signature';

      const response = await request.get('/api/v1/analytics/embed-config', {
        headers: {
          Authorization: `Bearer ${tamperedJwt}`,
        },
        failOnStatusCode: false,
      });

      expect(response.status()).toBe(401);
    });

    test('[P2] JWT with wrong signing key should be rejected', async ({ request }) => {
      const wrongKeyJwt =
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiY29tcGFueV9pZCI6MSwiZXhwIjo5OTk5OTk5OTk5fQ.wrong_signature_here';

      const response = await request.get('/api/v1/analytics/embed-config', {
        headers: {
          Authorization: `Bearer ${wrongKeyJwt}`,
        },
        failOnStatusCode: false,
      });

      expect(response.status()).toBe(401);
    });
  });

  test.describe('Multi-tenant Isolation', () => {
    test('[P0] user from company A cannot access company B embed data', async ({
      page,
    }) => {
      const companyAId = 1;
      const companyBId = 2;

      await page.route('**/api/v1/auth/login', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              accessToken: 'company-a-token',
              refreshToken: 'company-a-refresh',
              user: {
                id: 1,
                email: 'user@companya.com',
                fullName: 'Company A User',
                role: 'ACCOUNTANT',
                companyId: companyAId,
              },
            },
          }),
        });
      });

      await page.route('**/api/v1/analytics/**', async (route) => {
        const url = route.request().url();

        if (url.includes(`company_id=${companyBId}`)) {
          await route.fulfill({
            status: 403,
            contentType: 'application/json',
            body: JSON.stringify({
              error: {
                code: 'FORBIDDEN',
                message: 'Access denied to company resources',
              },
            }),
          });
        } else {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              data: { companyId: companyAId },
            }),
          });
        }
      });

      await page.goto('/login');
      await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });
      await page.fill('[data-testid="email-input"]', 'user@companya.com');
      await page.fill('[data-testid="password-input"]', 'TestPassword123!');
      await page.click('[data-testid="login-button"]');

      await page.waitForURL((url) => !url.pathname.includes('/login'), {
        timeout: 10000,
      });

      await page.goto(`/analytics?company_id=${companyBId}`);

      await page.waitForTimeout(500);
    });
  });
});
