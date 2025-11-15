import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright Test Configuration
 * 
 * This configuration follows production-ready patterns:
 * - Standardized timeouts (action: 15s, navigation: 30s, test: 60s)
 * - Failure-only artifact capture (screenshots, videos, traces)
 * - HTML + JUnit reporters for CI integration
 * - Multi-browser support (chromium, firefox, webkit)
 * - Parallel execution with CI-aware worker configuration
 */
export default defineConfig({
  // Test directory
  testDir: './tests/e2e',

  // Parallel execution
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,

  // Timeout configuration
  timeout: 60 * 1000, // Test timeout: 60s
  expect: {
    timeout: 15 * 1000, // Assertion timeout: 15s
  },

  // Global test settings
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15 * 1000, // Action timeout: 15s
    navigationTimeout: 30 * 1000, // Navigation timeout: 30s
  },

  // Reporters
  reporter: [
    ['html', { outputFolder: 'test-results/html' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
    ['list'],
  ],

  // Browser projects
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  // Web server configuration (optional - uncomment if you want Playwright to start the dev server)
  // webServer: {
  //   command: 'cd frontend && pnpm dev',
  //   url: 'http://localhost:5173',
  //   reuseExistingServer: !process.env.CI,
  //   timeout: 120 * 1000,
  // },
});

