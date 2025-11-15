import { test as base } from '@playwright/test';
import { UserFactory } from './factories/user-factory';

/**
 * Test Fixtures
 * 
 * This file extends Playwright's base test with custom fixtures.
 * Following the pure function → fixture pattern for composability.
 * 
 * Pattern: Each fixture solves one isolated concern (auth, API, data factories).
 * Compose capabilities using mergeTests instead of inheritance.
 */

type TestFixtures = {
  userFactory: UserFactory;
};

export const test = base.extend<TestFixtures>({
  /**
   * User factory fixture with automatic cleanup
   * Provides factory methods for creating test users
   */
  userFactory: async ({ request }, use) => {
    const factory = new UserFactory();

    // Helper to make API requests for cleanup
    const apiRequest = async (params: { method: 'DELETE'; url: string }) => {
      const baseURL = process.env.BASE_URL || 'http://localhost:5173';
      const apiBaseURL = process.env.API_URL || 'http://localhost:8080';
      const url = params.url.startsWith('http') ? params.url : `${apiBaseURL}${params.url}`;

      const response = await request.fetch(url, {
        method: params.method,
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok()) {
        throw new Error(`API request failed: ${response.status()} ${await response.text()}`);
      }
    };

    await use(factory);

    // Auto-cleanup: Delete all users created during test
    await factory.cleanup(apiRequest);
  },
});

export { expect } from '@playwright/test';

