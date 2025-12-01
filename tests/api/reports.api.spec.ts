import { test, expect } from '@playwright/test';

/**
 * Reporting API Tests
 * Priority: P2 (Business intelligence and compliance)
 * 
 * Coverage:
 * - GET /reports/ap-aging - AP Aging report
 * - GET /reports/ar-aging - AR Aging report
 * - GET /reports/trial-balance - Trial balance
 * - GET /reports/vat-summary - VAT summary report
 * - GET /reports/profit-loss - Profit & Loss statement
 * - GET /reports/balance-sheet - Balance sheet
 * - Report filtering and export
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;

// Helper to add required headers
const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1',
});

test.describe('Reporting API', () => {
    test.beforeAll(async ({ request }) => {
        const loginResponse = await request.post(`${API_BASE}/auth/login`, {
            data: {
                email: 'accountant@example.com',
                password: 'password',
            },
        });

        const loginBody = await loginResponse.json();
        authToken = loginBody.data.accessToken;
    });

    test.describe('P2: AP/AR Aging Reports', () => {
        test('[P2] GET /reports/ap-aging - should return AP aging report', async ({ request }) => {
            // GIVEN: Date for aging calculation
            const asOfDate = new Date().toISOString().split('T')[0];

            // WHEN: Getting AP aging report
            const response = await request.get(`${API_BASE}/reports/ap-aging?asOfDate=${asOfDate}`, {
                headers: getHeaders(),
            });

            // THEN: Should return aging buckets
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('asOfDate');
            expect(body.data).toHaveProperty('suppliers');
            expect(Array.isArray(body.data.suppliers)).toBe(true);

            // Each supplier should have aging buckets
            if (body.data.suppliers.length > 0) {
                body.data.suppliers.forEach((supplier: any) => {
                    expect(supplier).toHaveProperty('supplierId');
                    expect(supplier).toHaveProperty('supplierName');
                    expect(supplier).toHaveProperty('current');
                    expect(supplier).toHaveProperty('days30');
                    expect(supplier).toHaveProperty('days60');
                    expect(supplier).toHaveProperty('days90');
                    expect(supplier).toHaveProperty('over90');
                    expect(supplier).toHaveProperty('total');
                });
            }
        });

        test('[P2] GET /reports/ar-aging - should return AR aging report', async ({ request }) => {
            // GIVEN: Date for aging calculation
            const asOfDate = new Date().toISOString().split('T')[0];

            // WHEN: Getting AR aging report
            const response = await request.get(`${API_BASE}/reports/ar-aging?asOfDate=${asOfDate}`, {
                headers: getHeaders(),
            });

            // THEN: Should return aging buckets
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('customers');
            expect(Array.isArray(body.data.customers)).toBe(true);
        });
    });

    test.describe('P2: Financial Statements', () => {
        test('[P2] GET /reports/trial-balance - should return trial balance', async ({ request }) => {
            // GIVEN: Date range for trial balance
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';

            // WHEN: Getting trial balance
            const response = await request.get(
                `${API_BASE}/reports/trial-balance?startDate=${startDate}&endDate=${endDate}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return trial balance data
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('startDate');
            expect(body.data).toHaveProperty('endDate');
            expect(body.data).toHaveProperty('accounts');
            expect(Array.isArray(body.data.accounts)).toBe(true);

            // Each account should have debit and credit balances
            if (body.data.accounts.length > 0) {
                body.data.accounts.forEach((account: any) => {
                    expect(account).toHaveProperty('accountCode');
                    expect(account).toHaveProperty('accountName');
                    expect(account).toHaveProperty('debit');
                    expect(account).toHaveProperty('credit');
                });
            }

            // Trial balance should have totals
            expect(body.data).toHaveProperty('totalDebit');
            expect(body.data).toHaveProperty('totalCredit');

            // Debits should equal credits
            if (body.data.totalDebit && body.data.totalCredit) {
                expect(body.data.totalDebit).toBeCloseTo(body.data.totalCredit, 2);
            }
        });

        test('[P2] GET /reports/profit-loss - should return P&L statement', async ({ request }) => {
            // GIVEN: Date range for P&L
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';

            // WHEN: Getting P&L statement
            const response = await request.get(
                `${API_BASE}/reports/profit-loss?startDate=${startDate}&endDate=${endDate}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return P&L data
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('revenue');
            expect(body.data).toHaveProperty('expenses');
            expect(body.data).toHaveProperty('netIncome');

            // Net income = Revenue - Expenses
            if (body.data.revenue !== undefined && body.data.expenses !== undefined) {
                expect(body.data.netIncome).toBeCloseTo(
                    body.data.revenue - body.data.expenses,
                    2
                );
            }
        });

        test('[P2] GET /reports/balance-sheet - should return balance sheet', async ({ request }) => {
            // GIVEN: As-of date for balance sheet
            const asOfDate = '2025-01-31';

            // WHEN: Getting balance sheet
            const response = await request.get(
                `${API_BASE}/reports/balance-sheet?asOfDate=${asOfDate}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return balance sheet data
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('assets');
            expect(body.data).toHaveProperty('liabilities');
            expect(body.data).toHaveProperty('equity');

            // Assets = Liabilities + Equity
            if (body.data.assets && body.data.liabilities && body.data.equity) {
                expect(body.data.assets).toBeCloseTo(
                    body.data.liabilities + body.data.equity,
                    2
                );
            }
        });
    });

    test.describe('P2: VAT Reports', () => {
        test('[P2] GET /reports/vat-summary - should return VAT summary', async ({ request }) => {
            // GIVEN: Period for VAT calculation
            const year = 2025;
            const month = 1;

            // WHEN: Getting VAT summary
            const response = await request.get(
                `${API_BASE}/reports/vat-summary?year=${year}&month=${month}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return VAT data
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('period');
            expect(body.data).toHaveProperty('inputVAT');
            expect(body.data).toHaveProperty('outputVAT');
            expect(body.data).toHaveProperty('netVAT');

            // Net VAT = Output VAT - Input VAT
            if (body.data.outputVAT !== undefined && body.data.inputVAT !== undefined) {
                expect(body.data.netVAT).toBeCloseTo(
                    body.data.outputVAT - body.data.inputVAT,
                    2
                );
            }
        });

        test('[P2] GET /reports/vat-detail - should return detailed VAT transactions', async ({ request }) => {
            // GIVEN: Period for VAT detail
            const year = 2025;
            const month = 1;

            // WHEN: Getting VAT detail
            const response = await request.get(
                `${API_BASE}/reports/vat-detail?year=${year}&month=${month}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return detailed transactions
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('transactions');
            expect(Array.isArray(body.data.transactions)).toBe(true);
        });
    });

    test.describe('P2: Report Export', () => {
        test('[P2] GET /reports/trial-balance?format=pdf - should export as PDF', async ({ request }) => {
            // GIVEN: Date range and PDF format
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';

            // WHEN: Requesting PDF export
            const response = await request.get(
                `${API_BASE}/reports/trial-balance?startDate=${startDate}&endDate=${endDate}&format=pdf`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return PDF content
            expect(response.status()).toBe(200);
            expect(response.headers()['content-type']).toContain('application/pdf');
        });

        test('[P2] GET /reports/trial-balance?format=excel - should export as Excel', async ({ request }) => {
            // GIVEN: Date range and Excel format
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';

            // WHEN: Requesting Excel export
            const response = await request.get(
                `${API_BASE}/reports/trial-balance?startDate=${startDate}&endDate=${endDate}&format=excel`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return Excel content
            expect(response.status()).toBe(200);
            const contentType = response.headers()['content-type'];
            expect(
                contentType.includes('application/vnd.openxmlformats') ||
                contentType.includes('application/vnd.ms-excel')
            ).toBe(true);
        });
    });

    test.describe('P2: Report Validation', () => {
        test('[P2] GET /reports/trial-balance - should return 400 for invalid date range', async ({ request }) => {
            // GIVEN: End date before start date
            const startDate = '2025-01-31';
            const endDate = '2025-01-01';

            // WHEN: Requesting report
            const response = await request.get(
                `${API_BASE}/reports/trial-balance?startDate=${startDate}&endDate=${endDate}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return 400
            expect(response.status()).toBe(400);
        });

        test('[P2] should require authentication for all reports', async ({ request }) => {
            // WHEN: Requesting report without auth
            const response = await request.get(`${API_BASE}/reports/trial-balance`);

            // THEN: Should return 401
            expect(response.status()).toBe(401);
        });
    });
});
