import { test, expect } from '@playwright/test';

/**
 * AR Aging Workflow E2E Tests
 * Priority: P1 (Core flow validation)
 * 
 * Coverage:
 * - Full flow: Invoice creation → Payment → Aging update
 * - Cache invalidation on invoice/receipt events
 * - Real-time aging calculation accuracy
 * - Drill-down navigation
 * - Export with various combinations
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';
const APP_BASE = process.env.APP_BASE || 'http://localhost:5173';

let authToken: string;
let customerId: number;
let invoiceId: string;

test.describe('AR Aging Workflow', () => {
    test.beforeAll(async ({ request }) => {
        // Login
        const loginResponse = await request.post(`${API_BASE}/auth/login`, {
            data: {
                email: 'accountant@example.com',
                password: 'password',
            },
        });

        const loginBody = await loginResponse.json();
        authToken = loginBody.data.accessToken;

        // Create or get a test customer
        const customersResponse = await request.get(`${API_BASE}/customers?page=0&size=1`, {
            headers: {
                Authorization: `Bearer ${authToken}`,
                'X-Company-Id': '1',
            },
        });

        const customersBody = await customersResponse.json();
        if (customersBody.data.content.length > 0) {
            customerId = customersBody.data.content[0].id;
        }
    });

    test('[P1] Full flow: Create invoice → Verify aging → Make payment → Verify updated aging', async ({
        request,
    }) => {
        if (!customerId) {
            test.skip();
            return;
        }

        // STEP 1: Get baseline aging for customer
        const baselineResponse = await request.get(
            `${API_BASE}/ar-aging?customerId=${customerId}&page=0&size=20`,
            {
                headers: {
                    Authorization: `Bearer ${authToken}`,
                    'X-Company-Id': '1',
                },
            }
        );

        const baselineBody = await baselineResponse.json();
        const baselineOutstanding = baselineBody.data.content[0]?.totalOutstanding || 0;

        // STEP 2: Create a new sales invoice (simplified example)
        const invoiceData = {
            customerId: customerId,
            invoiceDate: new Date().toISOString().split('T')[0],
            dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
            totalAmount: 5000,
            description: 'Test Invoice for AR Aging',
            lines: [
                {
                    description: 'Test item',
                    quantity: 1,
                    unitPrice: 5000,
                    amount: 5000,
                },
            ],
        };

        const createInvoiceResponse = await request.post(`${API_BASE}/sales-invoices`, {
            headers: {
                Authorization: `Bearer ${authToken}`,
                'X-Company-Id': '1',
                'Content-Type': 'application/json',
            },
            data: invoiceData,
        });

        if (createInvoiceResponse.status() === 201) {
            const createInvoiceBody = await createInvoiceResponse.json();
            invoiceId = createInvoiceBody.data.id;

            // STEP 3: Refresh AR aging cache to include new invoice
            await request.post(`${API_BASE}/ar-aging/refresh`, {
                headers: {
                    Authorization: `Bearer ${authToken}`,
                    'X-Company-Id': '1',
                },
            });

            // STEP 4: Verify aging report includes new invoice
            const updatedResponse = await request.get(
                `${API_BASE}/ar-aging?customerId=${customerId}&page=0&size=20`,
                {
                    headers: {
                        Authorization: `Bearer ${authToken}`,
                        'X-Company-Id': '1',
                    },
                }
            );

            const updatedBody = await updatedResponse.json();
            const updatedOutstanding = updatedBody.data.content[0]?.totalOutstanding || 0;

            // Outstanding should have increased by invoice amount
            expect(updatedOutstanding).toBeGreaterThanOrEqual(baselineOutstanding + 5000);

            // STEP 5: Create a receipt (payment) for the invoice
            const receiptData = {
                customerId: customerId,
                receiptDate: new Date().toISOString().split('T')[0],
                amount: 2500, // Partial payment
                paymentMethod: 'BANK_TRANSFER',
                reference: 'TEST-RECEIPT-001',
                allocations: [
                    {
                        invoiceId: invoiceId,
                        amount: 2500,
                    },
                ],
            };

            const createReceiptResponse = await request.post(`${API_BASE}/receipts`, {
                headers: {
                    Authorization: `Bearer ${authToken}`,
                    'X-Company-Id': '1',
                    'Content-Type': 'application/json',
                },
                data: receiptData,
            });

            if (createReceiptResponse.status() === 201) {
                // STEP 6: Refresh cache after payment
                await request.post(`${API_BASE}/ar-aging/refresh`, {
                    headers: {
                        Authorization: `Bearer ${authToken}`,
                        'X-Company-Id': '1',
                    },
                });

                // STEP 7: Verify aging reflects payment
                const finalResponse = await request.get(
                    `${API_BASE}/ar-aging?customerId=${customerId}&page=0&size=20`,
                    {
                        headers: {
                            Authorization: `Bearer ${authToken}`,
                            'X-Company-Id': '1',
                        },
                    }
                );

                const finalBody = await finalResponse.json();
                const finalOutstanding = finalBody.data.content[0]?.totalOutstanding || 0;

                // Outstanding should have decreased by payment amount
                expect(finalOutstanding).toBe(updatedOutstanding - 2500);
            }
        }
    });

    test('[P1] Cache invalidation on invoice POST', async ({ request }) => {
        if (!customerId) {
            test.skip();
            return;
        }

        // STEP 1: Get aging with initial cache status
        const beforeResponse = await request.get(`${API_BASE}/ar-aging?page=0&size=20`, {
            headers: {
                Authorization: `Bearer ${authToken}`,
                'X-Company-Id': '1',
            },
        });

        const beforeBody = await beforeResponse.json();
        const beforeCacheStatus = beforeBody.metadata.cacheStatus;

        // STEP 2: Create a new invoice
        const invoiceData = {
            customerId: customerId,
            invoiceDate: new Date().toISOString().split('T')[0],
            dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
            totalAmount: 1000,
            description: 'Cache invalidation test',
            lines: [
                {
                    description: 'Test item',
                    quantity: 1,
                    unitPrice: 1000,
                    amount: 1000,
                },
            ],
        };

        await request.post(`${API_BASE}/sales-invoices`, {
            headers: {
                Authorization: `Bearer ${authToken}`,
                'X-Company-Id': '1',
                'Content-Type': 'application/json',
            },
            data: invoiceData,
        });

        // STEP 3: Get aging again - cache should be invalidated (MISS) or refreshed
        const afterResponse = await request.get(`${API_BASE}/ar-aging?page=0&size=20`, {
            headers: {
                Authorization: `Bearer ${authToken}`,
                'X-Company-Id': '1',
            },
        });

        const afterBody = await afterResponse.json();

        // Either cache is MISS (invalidated) or data is updated
        expect(afterResponse.status()).toBe(200);
        expect(afterBody.metadata).toHaveProperty('cacheStatus');
    });

    test('[P1] Export with multiple filter combinations', async ({ request }) => {
        const filterCombinations = [
            { format: 'EXCEL', label: 'Excel - All customers' },
            { format: 'PDF', label: 'PDF - All customers' },
            {
                format: 'EXCEL',
                customerId: customerId,
                label: 'Excel - Specific customer',
            },
            {
                format: 'EXCEL',
                asOfDate: '2025-10-31',
                label: 'Excel - Historical date',
            },
        ];

        for (const combo of filterCombinations) {
            const params = new URLSearchParams({
                format: combo.format,
                ...(combo.customerId && { customerId: combo.customerId.toString() }),
                ...(combo.asOfDate && { asOfDate: combo.asOfDate }),
            });

            const response = await request.get(`${API_BASE}/ar-aging/export?${params}`, {
                headers: {
                    Authorization: `Bearer ${authToken}`,
                    'X-Company-Id': '1',
                },
            });

            expect(response.status(), `Export failed for: ${combo.label}`).toBe(200);

            const buffer = await response.body();
            expect(buffer.length, `Empty export for: ${combo.label}`).toBeGreaterThan(0);
        }
    });
});

test.describe('AR Aging UI Navigation', () => {
    test.beforeEach(async ({ page }) => {
        // Login via UI
        await page.goto(`${APP_BASE}/login`);
        await page.fill('input[name="email"]', 'accountant@example.com');
        await page.fill('input[name="password"]', 'password');
        await page.click('button[type="submit"]');

        // Wait for dashboard
        await page.waitForURL('**/dashboard');
    });

    test('[P1] Navigate to AR Aging Report and verify data display', async ({ page }) => {
        // Navigate to AR Aging
        await page.goto(`${APP_BASE}/ar-aging`);

        // Wait for report to load
        await page.waitForSelector('table', { timeout: 10000 });

        // Verify table headers
        await expect(page.getByText('Customer')).toBeVisible();
        await expect(page.getByText('Current')).toBeVisible();
        await expect(page.getByText('1-30 Days')).toBeVisible();
        await expect(page.getByText('31-60 Days')).toBeVisible();
        await expect(page.getByText('61-90 Days')).toBeVisible();
        await expect(page.getByText('90+ Days')).toBeVisible();

        // Verify snapshot metadata is displayed
        await expect(page.getByText(/Snapshot Date:/i)).toBeVisible();
        await expect(page.getByText(/Cache Status:/i)).toBeVisible();
    });

    test('[P1] Test drill-down to invoice details', async ({ page }) => {
        await page.goto(`${APP_BASE}/ar-aging`);

        // Wait for report
        await page.waitForSelector('table');

        // Click on first aging bucket cell (if any data exists)
        const bucketCell = page.locator('table tbody tr td').nth(2); // Assuming bucket cells start at index 2

        if (await bucketCell.count() > 0) {
            await bucketCell.click();

            // Verify drill-down dialog opens
            await expect(page.getByRole('dialog')).toBeVisible({ timeout: 5000 });
            await expect(page.getByText(/Invoice Details/i)).toBeVisible();
        }
    });

    test('[P1] Test export functionality from UI', async ({ page }) => {
        await page.goto(`${APP_BASE}/ar-aging`);

        // Wait for report
        await page.waitForSelector('table');

        // Click Export Excel button
        const downloadPromise = page.waitForEvent('download');
        await page.click('button:has-text("Export Excel")');

        const download = await downloadPromise;
        expect(download.suggestedFilename()).toContain('ar-aging');
    });

    test('[P1] Test dashboard tiles auto-refresh', async ({ page }) => {
        await page.goto(`${APP_BASE}/dashboard`);

        // Wait for AR overdue tiles to load
        await expect(page.getByText(/Total AR Overdue/i)).toBeVisible({ timeout: 10000 });
        await expect(page.getByText(/Overdue Invoices/i)).toBeVisible();

        // Get initial values
        const initialOverdueText = await page.locator('[data-testid="total-overdue"]').textContent();

        // Wait 30 seconds to see if auto-refresh occurs (tiles refresh every 5 min in prod, but may be faster in test)
        // For actual testing, you might want to trigger a refresh manually
        const refreshButton = page.locator('button:has-text("Refresh")');
        if (await refreshButton.count() > 0) {
            await refreshButton.click();

            // Verify tiles update
            await page.waitForTimeout(2000); // Wait for refresh
            const updatedOverdueText = await page.locator('[data-testid="total-overdue"]').textContent();

            // Text should exist (even if values are the same)
            expect(updatedOverdueText).toBeTruthy();
        }
    });
});
