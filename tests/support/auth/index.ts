/**
 * Auth Module - Centralized authentication utilities for E2E tests
 *
 * Re-exports all auth-related functionality from a single entry point.
 */

export {
  type TestRole,
  type UserCredentials,
  TEST_USERS,
  DEMO_TEST_USERS,
  MOCK_TEST_USERS,
  getTestUsers,
  getUserByRole,
} from './users';

export {
  type LoginViaUIOptions,
  loginViaUI,
  verifyAuthenticated,
  waitForPostLoginStabilization,
} from './login';
