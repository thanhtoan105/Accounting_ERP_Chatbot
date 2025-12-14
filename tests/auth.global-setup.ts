import { test as setup, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginViaUI } from './support/auth/login';
import {
  TEST_USERS as SHARED_TEST_USERS,
  type TestRole,
  type UserCredentials,
} from './support/auth/users';

/**
 * Re-export TEST_USERS for backward compatibility.
 * Existing imports: import { TEST_USERS } from '../auth.global-setup';
 */
export const TEST_USERS: Record<string, UserCredentials> = SHARED_TEST_USERS;

export const AUTH_DIR = path.join(__dirname, '.auth');

export function getStorageStatePath(role: string): string {
  return path.join(AUTH_DIR, `${role}.json`);
}

setup.beforeAll(async () => {
  if (!fs.existsSync(AUTH_DIR)) {
    fs.mkdirSync(AUTH_DIR, { recursive: true });
  }
});

const ROLES: TestRole[] = ['admin', 'chief_accountant', 'accountant', 'cfo'];

for (const role of ROLES) {
  const credentials = TEST_USERS[role];

  setup(`authenticate as ${role}`, async ({ page, context }) => {
    const storageStatePath = getStorageStatePath(role);

    await loginViaUI(page, {
      email: credentials.email,
      password: credentials.password,
      waitForAuthResponse: false,
      postLoginUrl: /\/(dashboard)?$/,
      timeout: 30000,
      handleCompanySelection: false,
    });

    await expect(page).not.toHaveURL(/\/login/);

    await context.storageState({ path: storageStatePath });
  });
}
