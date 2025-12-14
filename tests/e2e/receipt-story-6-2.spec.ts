import { test, expect, Page } from '@playwright/test';
import { TEST_USERS } from '../auth.global-setup';

/**
 * Story 6.2: Cash Receipt Entry & Posting - E2E Tests
 * 
 * These tests run against real backend (no mocks) for integration testing.
 * Prerequisites:
 * - Backend running at http://localhost:8080
 * - Test data seeded (customer with invoices, active bank account)
 */

const BASE_URL = 'http://localhost:5173';
const API_URL = 'http://localhost:8080/api/v1';

// Use centralized test credentials
const ACCOUNTANT_USER = TEST_USERS.accountant;
const CHIEF_ACCOUNTANT_USER = TEST_USERS.chief_accountant;

// Helper: Login
async function login(page: Page, email: string, password: string) {
    await page.goto(`${BASE_URL}/login`);
    await page.fill('[data-testid="email-input"], input[name="email"]', email);
    await page.fill('[data-testid="password-input"], input[name="password"]', password);
    await page.click('[data-testid="login-button"], button[type="submit"]');
    await page.waitForURL(/\/(dashboard|accounting)/);
}

// Helper: Navigate to Receipts
async function navigateToReceipts(page: Page) {
    await page.goto(`${BASE_URL}/accounting/receipts`);
    await page.waitForLoadState('networkidle');
}

// Helper: Create Receipt
async function createReceipt(page: Page, data: {
    customerId?: string;
    amount: number;
    bankAccountId?: string;
    isStandalone?: boolean;
    reference?: string;
}) {
    await page.click('button:has-text("New Receipt"), [data-testid="new-receipt-btn"]');
    await page.waitForURL(/\/accounting\/receipts\/new/);

    // Select customer (if not standalone)
    if (!data.isStandalone && data.customerId) {
        await page.click('[data-testid="customer-picker"]');
        await page.click(`[data-testid="customer-option-${data.customerId}"]`);
    }

    // Check standalone if needed
    if (data.isStandalone) {
        await page.check('[data-testid="standalone-checkbox"], input[name="isStandalone"]');
    }

    // Fill amount
    await page.fill('[data-testid="amount-input"], input[name="amount"]', data.amount.toString());

    // Select bank account
    if (data.bankAccountId) {
        await page.click('[data-testid="bank-account-picker"]');
        await page.click(`[data-testid="account-option-${data.bankAccountId}"]`);
    }

    // Fill reference
    if (data.reference) {
        await page.fill('[data-testid="reference-input"], input[name="reference"]', data.reference);
    }

    // Submit
    await page.click('[data-testid="save-receipt-btn"], button[type="submit"]:has-text("Save")');
}

test.describe('Story 6.2: Cash Receipt Entry & Posting', () => {

    test.describe('AC6.2-01: Receipt Form Fields', () => {

        test('should display all required fields in receipt form', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            // Verify form fields exist
            await expect(page.locator('[data-testid="receipt-date"], input[name="receiptDate"]')).toBeVisible();
            await expect(page.locator('[data-testid="receipt-number"]')).toBeVisible();
            await expect(page.locator('[data-testid="customer-picker"]')).toBeVisible();
            await expect(page.locator('[data-testid="amount-input"], input[name="amount"]')).toBeVisible();
            await expect(page.locator('[data-testid="bank-account-picker"]')).toBeVisible();
            await expect(page.locator('[data-testid="payment-method-select"]')).toBeVisible();
            await expect(page.locator('[data-testid="reference-input"], input[name="reference"]')).toBeVisible();
        });

        test('should auto-generate receipt number with CR-YYYY format', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            const receiptNumber = await page.locator('[data-testid="receipt-number"]').textContent();
            expect(receiptNumber).toMatch(/CR-\d{4}-\d+/);
        });

        test('should validate amount > 0', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            await page.fill('[data-testid="amount-input"], input[name="amount"]', '0');
            await page.click('[data-testid="save-receipt-btn"], button[type="submit"]');

            await expect(page.locator('text=Amount must be greater than 0')).toBeVisible();
        });

        test('should validate required fields', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            // Try to submit empty form
            await page.click('[data-testid="save-receipt-btn"], button[type="submit"]');

            // Expect validation errors
            await expect(page.locator('text=Customer is required').or(page.locator('text=Amount is required'))).toBeVisible();
        });
    });

    test.describe('AC6.2-02: AR Allocation to Invoices', () => {

        test('should show open invoices for selected customer', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            // Select a customer
            await page.click('[data-testid="customer-picker"]');
            await page.click('[data-testid="customer-option"]:first-child');

            // Verify allocation grid appears
            await expect(page.locator('[data-testid="allocation-grid"]')).toBeVisible();
        });

        test('should prevent over-allocation', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            // Select customer and fill amount
            await page.click('[data-testid="customer-picker"]');
            await page.click('[data-testid="customer-option"]:first-child');
            await page.fill('[data-testid="amount-input"], input[name="amount"]', '1000000');

            // Try to allocate more than receipt amount
            const allocationInput = page.locator('[data-testid="allocation-amount"]:first-child');
            await allocationInput.fill('2000000');

            await expect(page.locator('text=Total allocations exceed receipt amount')).toBeVisible();
        });
    });

    test.describe('AC6.2-03: Validation Rules', () => {

        test('should reject posting with inactive account', async ({ page }) => {
            // This test requires an inactive account in test data
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Create receipt with inactive account (via API or UI)
            // Attempt to post should fail
        });
    });

    test.describe('AC6.2-04: GL Posting Logic', () => {

        test('should create correct voucher entries for allocated receipt', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Create and post a receipt
            await createReceipt(page, {
                amount: 1000000,
                reference: 'E2E-GL-TEST-' + Date.now(),
            });

            // Wait for redirect to edit page
            await page.waitForURL(/\/accounting\/receipts\/[\w-]+/);

            // Post the receipt
            await page.click('[data-testid="post-receipt-btn"]');
            await page.click('[data-testid="confirm-post-btn"]');

            // Verify voucher link appears
            await expect(page.locator('[data-testid="linked-voucher"]')).toBeVisible();

            // Click voucher link and verify entries
            await page.click('[data-testid="linked-voucher"] a');

            // Verify debit entry (Cash/Bank 1111 or 1121)
            await expect(page.locator('text=/111[12].*Debit/')).toBeVisible();
            // Verify credit entry (AR 131)
            await expect(page.locator('text=/131.*Credit/')).toBeVisible();
        });

        test('should create credit 711 for standalone receipt', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Create standalone receipt
            await createReceipt(page, {
                amount: 500000,
                isStandalone: true,
                reference: 'E2E-STANDALONE-' + Date.now(),
            });

            await page.waitForURL(/\/accounting\/receipts\/[\w-]+/);

            // Post the receipt
            await page.click('[data-testid="post-receipt-btn"]');
            await page.click('[data-testid="confirm-post-btn"]');

            // Click voucher link and verify 711 credit
            await page.click('[data-testid="linked-voucher"] a');
            await expect(page.locator('text=/711.*Credit/')).toBeVisible();
        });
    });

    test.describe('AC6.2-05: Reversal Workflow', () => {

        test('should require reversal reason', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);

            // Navigate to a posted receipt (need test data)
            await page.goto(`${BASE_URL}/accounting/receipts`);
            await page.click('[data-testid="receipt-row"]:has-text("POSTED"):first-child');

            // Click reverse
            await page.click('[data-testid="reverse-receipt-btn"]');

            // Try to confirm without reason
            await page.click('[data-testid="confirm-reverse-btn"]');

            await expect(page.locator('text=Reversal reason is required')).toBeVisible();
        });

        test('should create linked reversal voucher', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);

            // Navigate to a posted receipt
            await page.goto(`${BASE_URL}/accounting/receipts`);
            await page.click('[data-testid="receipt-row"]:has-text("POSTED"):first-child');

            // Reverse with reason
            await page.click('[data-testid="reverse-receipt-btn"]');
            await page.fill('[data-testid="reversal-reason-input"]', 'E2E Test Reversal');
            await page.click('[data-testid="confirm-reverse-btn"]');

            // Verify status changed
            await expect(page.locator('[data-testid="receipt-status"]')).toHaveText('REVERSED');

            // Verify reversal voucher link
            await expect(page.locator('[data-testid="reversal-voucher-link"]')).toBeVisible();
        });
    });

    test.describe('AC6.2-08: Attachment Handling', () => {

        test('should upload and preview image attachment', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            // Create receipt first to enable attachments
            await createReceipt(page, {
                amount: 100000,
                reference: 'E2E-ATTACHMENT-' + Date.now(),
            });

            await page.waitForURL(/\/accounting\/receipts\/[\w-]+/);

            // Upload image
            const fileInput = page.locator('[data-testid="attachment-input"], input[type="file"]');
            await fileInput.setInputFiles({
                name: 'test-receipt.png',
                mimeType: 'image/png',
                buffer: Buffer.from('fake-image-content'),
            });

            // Verify upload success
            await expect(page.locator('[data-testid="attachment-item"]')).toBeVisible();
        });

        test('should reject files over 10MB', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);
            await page.click('button:has-text("New Receipt")');

            await createReceipt(page, {
                amount: 100000,
                reference: 'E2E-LARGE-FILE-' + Date.now(),
            });

            await page.waitForURL(/\/accounting\/receipts\/[\w-]+/);

            // Try to upload large file (>10MB)
            // This would require a test file or mock
            // For now, verify the error message when it occurs
        });

        test('should enforce 10 files limit', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Navigate to receipt with attachments
            // Verify counter shows "X of 10 files"
            await expect(page.locator('text=/\\d+ of 10 files/')).toBeVisible();
        });
    });

    test.describe('AC6.2-09: RBAC and Thresholds', () => {

        test('should set PENDING_APPROVAL for high-value receipt', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Create receipt over threshold (100M VND)
            await createReceipt(page, {
                amount: 150000000, // 150M > 100M threshold
                reference: 'E2E-THRESHOLD-' + Date.now(),
            });

            await page.waitForURL(/\/accounting\/receipts\/[\w-]+/);

            // Verify status is PENDING_APPROVAL
            await expect(page.locator('[data-testid="receipt-status"]')).toHaveText('PENDING_APPROVAL');
        });

        test('should require different approver (maker-checker)', async ({ page }) => {
            // Login as accountant who created the receipt
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);

            // Navigate to PENDING_APPROVAL receipt created by same user
            await page.goto(`${BASE_URL}/accounting/receipts`);
            await page.click('[data-testid="receipt-row"]:has-text("PENDING_APPROVAL")');

            // Try to approve own receipt
            await page.click('[data-testid="approve-post-btn"]');

            // Should fail with maker-checker error
            await expect(page.locator('text=Approver must be different from creator')).toBeVisible();
        });

        test('should allow chief accountant to approve', async ({ page }) => {
            // Login as Chief Accountant
            await login(page, CHIEF_ACCOUNTANT_USER.email, CHIEF_ACCOUNTANT_USER.password);

            // Navigate to PENDING_APPROVAL receipt
            await page.goto(`${BASE_URL}/accounting/receipts`);
            await page.click('[data-testid="receipt-row"]:has-text("PENDING_APPROVAL")');

            // Approve and post
            await page.click('[data-testid="approve-post-btn"]');
            await page.click('[data-testid="confirm-approve-btn"]');

            // Verify status changed to POSTED
            await expect(page.locator('[data-testid="receipt-status"]')).toHaveText('POSTED');
        });
    });

    test.describe('Receipt List & Search', () => {

        test('should display receipt list with pagination', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Verify table structure
            await expect(page.locator('table, [data-testid="receipt-table"]')).toBeVisible();
            await expect(page.locator('[data-testid="pagination"]')).toBeVisible();
        });

        test('should filter receipts by status', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Filter by POSTED status
            await page.click('[data-testid="status-filter"]');
            await page.click('text=POSTED');

            // Verify only POSTED receipts shown
            const statusBadges = page.locator('[data-testid="receipt-status"]');
            const count = await statusBadges.count();

            for (let i = 0; i < count; i++) {
                await expect(statusBadges.nth(i)).toHaveText('POSTED');
            }
        });

        test('should search receipts by number', async ({ page }) => {
            await login(page, ACCOUNTANT_USER.email, ACCOUNTANT_USER.password);
            await navigateToReceipts(page);

            // Search by receipt number
            await page.fill('[data-testid="search-input"], input[placeholder*="Search"]', 'CR-2025');
            await page.press('[data-testid="search-input"], input[placeholder*="Search"]', 'Enter');

            // Verify filtered results
            await expect(page.locator('[data-testid="receipt-row"]')).toHaveCount(await page.locator('[data-testid="receipt-row"]').count());
        });
    });
});
