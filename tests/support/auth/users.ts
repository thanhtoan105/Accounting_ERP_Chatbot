/**
 * Centralized Test Users Configuration
 *
 * Single source of truth for all test user credentials.
 * Both global-setup and auth-helper consume this module.
 */

export type TestRole = 'admin' | 'chief_accountant' | 'accountant' | 'cfo';

export interface UserCredentials {
  email: string;
  password: string;
  redirectUrl: string;
  fullName: string;
  userId: number;
  companyId: number;
}

/**
 * Demo environment users (real backend with seeded data)
 */
export const DEMO_TEST_USERS: Record<TestRole, UserCredentials> = {
  admin: {
    email: 'admin@demo.local',
    password: 'Demo@12345',
    redirectUrl: '/',
    fullName: 'Admin User',
    userId: 1,
    companyId: 1,
  },
  chief_accountant: {
    email: 'chief@demo.local',
    password: 'Demo@12345',
    redirectUrl: '/',
    fullName: 'Chief Accountant',
    userId: 2,
    companyId: 1,
  },
  accountant: {
    email: 'accountant@demo.local',
    password: 'Demo@12345',
    redirectUrl: '/',
    fullName: 'Accountant User',
    userId: 3,
    companyId: 1,
  },
  cfo: {
    email: 'cfo@demo.local',
    password: 'Demo@12345',
    redirectUrl: '/',
    fullName: 'CFO User',
    userId: 4,
    companyId: 1,
  },
};

/**
 * Example/mock environment users (for fully mocked tests without real backend)
 */
export const MOCK_TEST_USERS: Record<TestRole, UserCredentials> = {
  admin: {
    email: 'admin@example.com',
    password: 'password',
    redirectUrl: '/',
    fullName: 'Test Admin',
    userId: 1,
    companyId: 1,
  },
  chief_accountant: {
    email: 'chief_accountant@example.com',
    password: 'password',
    redirectUrl: '/',
    fullName: 'Chief Accountant',
    userId: 2,
    companyId: 1,
  },
  accountant: {
    email: 'accountant@example.com',
    password: 'password',
    redirectUrl: '/',
    fullName: 'Test User',
    userId: 1,
    companyId: 1,
  },
  cfo: {
    email: 'cfo@example.com',
    password: 'password',
    redirectUrl: '/',
    fullName: 'CFO User',
    userId: 5,
    companyId: 1,
  },
};

/**
 * Get test users based on environment.
 * Uses E2E_USER_SET env var: 'demo' (default) or 'mock'
 */
export function getTestUsers(): Record<TestRole, UserCredentials> {
  const userSet = process.env.E2E_USER_SET || 'demo';
  return userSet === 'mock' ? MOCK_TEST_USERS : DEMO_TEST_USERS;
}

/**
 * Default export - demo users for backward compatibility
 * Existing imports of TEST_USERS from auth.global-setup.ts will work
 */
export const TEST_USERS = DEMO_TEST_USERS;

/**
 * Get credentials for a specific role
 */
export function getUserByRole(
  role: TestRole,
  useMock: boolean = false,
): UserCredentials {
  const users = useMock ? MOCK_TEST_USERS : DEMO_TEST_USERS;
  return users[role];
}
