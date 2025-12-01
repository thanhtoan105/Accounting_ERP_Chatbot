import { test as base } from '@playwright/test';
import { APIRequestContext } from '@playwright/test';
import { createAgingReportEntry, createOverdueInvoice, AgingReportEntry, DrillDownInvoice } from '../factories/ar-aging.factory';

/**
 * AR Aging Fixture
 * 
 * Provides pre-configured AR aging test data and helpers
 * Handles cache setup/teardown and data seeding
 */

interface ARAgingFixture {
  arAgingData: {
    seedAgingReport: (customerId: number, buckets: Partial<AgingReportEntry['buckets']>) => Promise<void>;
    seedDrillDownInvoices: (customerId: number, bucketKey: string, count: number) => Promise<void>;
    clearAgingCache: () => Promise<void>;
    refreshAgingCache: () => Promise<void>;
  };
  authenticatedARUser: {
    token: string;
    companyId: number;
    role: 'accountant' | 'chief_accountant' | 'cfo';
  };
}

export const test = base.extend<ARAgingFixture>({
  authenticatedARUser: async ({ request }, use) => {
    // Login as accountant (AR clerk role)
    const loginResponse = await request.post('http://localhost:8080/api/v1/auth/login', {
      data: {
        email: 'accountant@example.com',
        password: 'password',
      },
    });

    const loginBody = await loginResponse.json();
    const token = loginBody.data.accessToken;

    await use({
      token,
      companyId: 1,
      role: 'accountant',
    });

    // Cleanup: No specific cleanup needed for auth
  },

  arAgingData: async ({ request, authenticatedARUser }, use) => {
    const { token, companyId } = authenticatedARUser;

    const getHeaders = () => ({
      Authorization: `Bearer ${token}`,
      'X-Company-Id': companyId.toString(),
    });

    const seedAgingReport = async (customerId: number, buckets: Partial<AgingReportEntry['buckets']>) => {
      // This would typically seed via API or database
      // For now, we'll use the refresh endpoint to trigger recalculation
      await request.post('http://localhost:8080/api/v1/ar-aging/refresh', {
        headers: getHeaders(),
      });
    };

    const seedDrillDownInvoices = async (customerId: number, bucketKey: string, count: number) => {
      // Create invoices via API that will appear in the specified bucket
      // This is a helper - actual implementation depends on invoice creation API
      for (let i = 0; i < count; i++) {
        // Note: This would need actual invoice creation logic
        // For now, this is a placeholder showing the pattern
      }
    };

    const clearAgingCache = async () => {
      // Clear cache by invalidating it
      await request.post('http://localhost:8080/api/v1/ar-aging/refresh', {
        headers: getHeaders(),
      });
    };

    const refreshAgingCache = async () => {
      await request.post('http://localhost:8080/api/v1/ar-aging/refresh', {
        headers: getHeaders(),
      });
    };

    await use({
      seedAgingReport,
      seedDrillDownInvoices,
      clearAgingCache,
      refreshAgingCache,
    });

    // Cleanup: Clear cache after test
    await clearAgingCache();
  },
});

export { expect } from '@playwright/test';

