import { test as base } from '@playwright/test';
import { UserFactory } from './factories/user-factory';
import { SupplierFactory } from './factories/supplier-factory';
import { PurchaseBillFactory } from './factories/purchase-bill-factory';
import { ApprovalWorkflowFactory } from './factories/approval-workflow-factory';
import { PaymentFactory } from './factories/payment-factory';

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
  supplierFactory: SupplierFactory;
  purchaseBillFactory: PurchaseBillFactory;
  approvalWorkflowFactory: ApprovalWorkflowFactory;
  paymentFactory: PaymentFactory;
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

  /**
   * Supplier factory fixture with automatic cleanup
   * Provides factory methods for creating test suppliers
   */
  supplierFactory: async ({ request }, use) => {
    const factory = new SupplierFactory();

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

    // Auto-cleanup: Delete all suppliers created during test
    await factory.cleanup(apiRequest);
  },

  /**
   * Purchase bill factory fixture with automatic cleanup
   * Provides factory methods for creating test purchase bills
   */
  purchaseBillFactory: async ({ request }, use) => {
    const factory = new PurchaseBillFactory();

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

    // Auto-cleanup: Delete all bills created during test
    await factory.cleanup(apiRequest);
  },

  /**
   * Approval workflow factory fixture with automatic cleanup
   * Provides factory methods for creating test approval workflows
   */
  approvalWorkflowFactory: async ({ request }, use) => {
    const factory = new ApprovalWorkflowFactory();

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

    // Auto-cleanup: Delete all workflows and thresholds created during test
    await factory.cleanup(apiRequest);
  },

  /**
   * Payment factory fixture with automatic cleanup
   * Provides factory methods for creating test payments
   */
  paymentFactory: async ({ request }, use) => {
    const factory = new PaymentFactory();

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

    // Auto-cleanup: Delete all payments created during test
    await factory.cleanup(apiRequest);
  },
});

export { expect } from '@playwright/test';

