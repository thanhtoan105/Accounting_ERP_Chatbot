import { defineConfig, devices } from '@playwright/test';
import path from 'path';

const authDir = path.join(__dirname, 'tests', '.auth');

const ROLES = ['admin', 'chief_accountant', 'accountant', 'cfo'] as const;
type Role = (typeof ROLES)[number];

function getStorageStatePath(role: Role): string {
  return path.join(authDir, `${role}.json`);
}

/**
 * Playwright Test Configuration
 *
 * This configuration follows production-ready patterns:
 * - Global setup for multi-role authentication
 * - Standardized timeouts (action: 15s, navigation: 30s, test: 60s)
 * - Failure-only artifact capture (screenshots, videos, traces)
 * - HTML + JUnit reporters for CI integration
 * - Multi-browser and multi-role support
 * - Parallel execution with CI-aware worker configuration
 */
export default defineConfig({
  // Test directory - includes both E2E and API tests
  testDir: './tests',
  testMatch: ['**/*.spec.ts'],

  // Parallel execution
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,

  // Timeout configuration
  timeout: 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },

  // Global test settings
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15 * 1000,
    navigationTimeout: 30 * 1000,
  },

  // Reporters
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
    ['list'],
  ],

  // Browser projects with role-based authentication
  projects: [
    // Setup project - runs global authentication
    {
      name: 'setup',
      testMatch: /auth\.global-setup\.ts/,
    },

    // === Chromium projects for each role ===
    {
      name: 'chromium-admin',
      use: {
        ...devices['Desktop Chrome'],
        storageState: getStorageStatePath('admin'),
      },
      dependencies: ['setup'],
    },
    {
      name: 'chromium-chief_accountant',
      use: {
        ...devices['Desktop Chrome'],
        storageState: getStorageStatePath('chief_accountant'),
      },
      dependencies: ['setup'],
    },
    {
      name: 'chromium-accountant',
      use: {
        ...devices['Desktop Chrome'],
        storageState: getStorageStatePath('accountant'),
      },
      dependencies: ['setup'],
    },
    {
      name: 'chromium-cfo',
      use: {
        ...devices['Desktop Chrome'],
        storageState: getStorageStatePath('cfo'),
      },
      dependencies: ['setup'],
    },

    // Default chromium project (no auth, for unauthenticated tests)
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    // Cross-browser testing (only in local dev)
    ...(process.env.CI
      ? []
      : [
          {
            name: 'firefox',
            use: { ...devices['Desktop Firefox'] },
          },
          {
            name: 'webkit',
            use: { ...devices['Desktop Safari'] },
          },
        ]),
  ],

  // Web server configuration - start frontend dev server for tests
  webServer: {
    command: 'cd frontend && pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});
