import { test, expect } from '@playwright/test';
import { BankAccountsPage, type BankAccountData } from '../../pages/BankAccountsPage';

/**
 * Bank Accounts Module - E2E Tests
 *
 * Test IDs:
 * - BANK-001: Create bank account (@smoke)
 * - BANK-002: Bank account validation
 * - BANK-003: Edit bank account
 * - BANK-004: Delete bank account
 * - BANK-005: View bank account list
 *
 * Uses authenticated project 'chromium-accountant' for pre-authenticated sessions.
 */
test.describe('Bank Accounts Module', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let bankAccountsPage: BankAccountsPage;

  test.beforeEach(async ({ page }) => {
    bankAccountsPage = new BankAccountsPage(page);
  });

  test.describe('BANK-001: Create bank account', { tag: '@smoke' }, () => {
    test('should create a new bank account with all required fields', async ({ page }) => {
      // GIVEN: User navigates to bank accounts page
      await bankAccountsPage.navigate();

      // WHEN: User creates a new bank account
      const bankAccountData: BankAccountData = {
        name: `Test Bank Account ${Date.now()}`,
        bankName: 'Vietcombank',
        accountNumber: '1234567890123456',
        currency: 'VND',
      };
      await bankAccountsPage.createBankAccount(bankAccountData);

      // THEN: Bank account appears in the list
      await bankAccountsPage.expectBankAccountInList(bankAccountData.name);
    });

    test('should display validation errors for missing required fields', async ({ page }) => {
      // GIVEN: User is on bank accounts page
      await bankAccountsPage.navigate();

      // WHEN: User opens create form and tries to save without data
      await page.getByRole('button', { name: /add bank account/i }).click();
      await bankAccountsPage.waitForModal();
      await page.getByRole('button', { name: /create|save/i }).click();

      // THEN: Validation errors are displayed
      await expect(page.getByText(/required/i)).toBeVisible();
    });
  });

  test.describe('BANK-002: Bank account validation', () => {
    test('should reject invalid account number format', async ({ page }) => {
      // GIVEN: User is on bank accounts page
      await bankAccountsPage.navigate();

      // WHEN: User opens create form and enters invalid account number
      await page.getByRole('button', { name: /add bank account/i }).click();
      await bankAccountsPage.waitForModal();
      await bankAccountsPage.validateAccountNumber('abc123');

      // THEN: Validation error is displayed for invalid format
      await expect(
        page.getByText(/invalid|account number|format/i).or(page.getByText(/numeric/i)),
      ).toBeVisible();
    });

    test('should accept valid account number format', async ({ page }) => {
      // GIVEN: User is on bank accounts create form
      await bankAccountsPage.navigate();
      await page.getByRole('button', { name: /add bank account/i }).click();
      await bankAccountsPage.waitForModal();

      // WHEN: User enters valid account number
      await bankAccountsPage.validateAccountNumber('9876543210123456');

      // THEN: No validation error for account number field
      const accountNumberError = page.locator('#accountNumber ~ [data-error]');
      await expect(accountNumberError).toBeHidden();
    });
  });

  test.describe('BANK-003: Edit bank account', () => {
    test('should edit an existing bank account', async ({ page }) => {
      // GIVEN: User creates a bank account first
      await bankAccountsPage.navigate();
      const originalName = `Edit Test Account ${Date.now()}`;
      await bankAccountsPage.createBankAccount({
        name: originalName,
        bankName: 'Vietinbank',
        accountNumber: '1111222233334444',
        currency: 'VND',
      });

      // WHEN: User edits the bank account
      const updatedName = `Updated Account ${Date.now()}`;
      await bankAccountsPage.editBankAccount(originalName, {
        name: updatedName,
        bankName: 'Techcombank',
      });

      // THEN: Updated bank account appears in the list
      await bankAccountsPage.expectBankAccountInList(updatedName);
    });
  });

  test.describe('BANK-004: Delete bank account', () => {
    test('should delete a bank account with confirmation', async ({ page }) => {
      // GIVEN: User creates a bank account to delete
      await bankAccountsPage.navigate();
      const accountName = `Delete Test Account ${Date.now()}`;
      await bankAccountsPage.createBankAccount({
        name: accountName,
        bankName: 'BIDV',
        accountNumber: '5555666677778888',
        currency: 'VND',
      });

      // WHEN: User deletes the bank account
      await bankAccountsPage.deleteBankAccount(accountName);

      // THEN: Bank account is removed from the list
      await bankAccountsPage.expectBankAccountNotInList(accountName);
    });

    test('should show confirmation dialog before deletion', async ({ page }) => {
      // GIVEN: User creates a bank account
      await bankAccountsPage.navigate();
      const accountName = `Confirm Delete Account ${Date.now()}`;
      await bankAccountsPage.createBankAccount({
        name: accountName,
        bankName: 'ACB',
        accountNumber: '9999888877776666',
        currency: 'VND',
      });

      // WHEN: User clicks delete
      const row = page.locator('table tbody tr', { hasText: accountName });
      await row.getByRole('button').click();
      await page.getByRole('menuitem', { name: /delete/i }).click();

      // THEN: Confirmation dialog is displayed
      await expect(page.locator('[role="alertdialog"]')).toBeVisible();

      // Cleanup: Cancel deletion
      await page.getByRole('button', { name: /cancel/i }).click();
    });
  });

  test.describe('BANK-005: View bank account list', () => {
    test('should display bank accounts in a table', async ({ page }) => {
      // GIVEN: User navigates to bank accounts page
      await bankAccountsPage.navigate();

      // THEN: Table is visible with bank account data
      await expect(page.locator('table')).toBeVisible();
      await expect(page.locator('table thead')).toBeVisible();
    });

    test('should search bank accounts by name', async ({ page }) => {
      // GIVEN: User creates a bank account with unique name
      await bankAccountsPage.navigate();
      const uniqueName = `Searchable Account ${Date.now()}`;
      await bankAccountsPage.createBankAccount({
        name: uniqueName,
        bankName: 'VPBank',
        accountNumber: '1212121212121212',
        currency: 'VND',
      });

      // WHEN: User searches for the bank account
      await bankAccountsPage.searchBankAccount(uniqueName);

      // THEN: Search results show the matching account
      await bankAccountsPage.expectBankAccountInList(uniqueName);
      const rowCount = await bankAccountsPage.getBankAccountCount();
      expect(rowCount).toBeGreaterThanOrEqual(1);
    });

    test('should display bank account details in columns', async ({ page }) => {
      // GIVEN: User navigates to bank accounts page
      await bankAccountsPage.navigate();

      // THEN: Table has expected column headers
      const headers = page.locator('table thead th');
      await expect(headers.filter({ hasText: /name/i })).toBeVisible();
      await expect(headers.filter({ hasText: /bank/i })).toBeVisible();
      await expect(headers.filter({ hasText: /account/i })).toBeVisible();
    });
  });
});
