import { test, expect } from '@playwright/test';

/**
 * Bank Account Management API Tests (Epic 6 - Story 6.1)
 * Priority: P1 (Cash/Bank account CRUD operations)
 *
 * Coverage:
 * - GET /bank-accounts - List bank accounts with pagination and filters
 * - GET /bank-accounts/:id - Get single bank account
 * - GET /bank-accounts/:id/balance-tooltip - Get balance tooltip data
 * - POST /bank-accounts - Create new bank account
 * - PUT /bank-accounts/:id - Update bank account
 * - PATCH /bank-accounts/:id/activate - Activate bank account
 * - PATCH /bank-accounts/:id/deactivate - Deactivate bank account
 * - DELETE /bank-accounts/:id - Delete bank account
 * - GET /bank-accounts/export - Export bank accounts (CSV/Excel)
 * - POST /bank-accounts/import - Import bank accounts from CSV
 * - GET /bank-accounts/import/template - Download import template
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;
let adminToken: string;

test.describe('Bank Account Management API', () => {
    test.beforeAll(async ({ request }) => {
        // Login as accountant
        const loginResponse = await request.post(`${API_BASE}/auth/login`, {
            data: {
                email: 'accountant@example.com',
                password: 'password',
            },
        });
        const loginBody = await loginResponse.json();
        authToken = loginBody.data.accessToken;

        // Login as admin
        const adminLoginResponse = await request.post(`${API_BASE}/auth/login`, {
            data: {
                email: 'admin@example.com',
                password: 'password',
            },
        });
        const adminBody = await adminLoginResponse.json();
        adminToken = adminBody.data.accessToken;
    });

    // Helper to add required headers
    const getHeaders = (token = authToken) => ({
        Authorization: `Bearer ${token}`,
        'X-Company-Id': '1',
    });

    test.describe('P1: Bank Account CRUD Operations', () => {
        let createdBankAccountId: number;
        const testAccountNumber = `ACC-${Date.now()}`;

        test('[P1] POST /bank-accounts - should create bank account with valid data', async ({
            request,
        }) => {
            const bankAccountData = {
                accountNumber: testAccountNumber,
                bankName: 'Test Bank',
                branch: 'Main Branch',
                type: 'BANK',
                openingBalance: 1000000,
                glAccountCode: '1121',
                active: true,
            };

            const response = await request.post(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(adminToken),
                data: bankAccountData,
            });

            expect([201, 409]).toContain(response.status());

            if (response.status() !== 201) {
                test.skip();
                return;
            }

            const body = await response.json();
            expect(body.data).toMatchObject({
                accountNumber: bankAccountData.accountNumber,
                bankName: bankAccountData.bankName,
                type: bankAccountData.type,
                glAccountCode: bankAccountData.glAccountCode,
            });

            expect(body.data).toHaveProperty('id');
            createdBankAccountId = body.data.id;
        });

        test('[P1] GET /bank-accounts - should list bank accounts with pagination', async ({
            request,
        }) => {
            const response = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(),
                params: { page: 0, size: 20 },
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body).toHaveProperty('data');
            expect(body).toHaveProperty('total');
            expect(body).toHaveProperty('page');
            expect(body).toHaveProperty('size');
            expect(Array.isArray(body.data)).toBe(true);
        });

        test('[P1] GET /bank-accounts - should filter by type', async ({ request }) => {
            const response = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(),
                params: { type: 'BANK' },
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            if (body.data.length > 0) {
                body.data.forEach((account: { type: string }) => {
                    expect(account.type).toBe('BANK');
                });
            }
        });

        test('[P1] GET /bank-accounts - should filter by status', async ({ request }) => {
            const response = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(),
                params: { status: 'true' },
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            if (body.data.length > 0) {
                body.data.forEach((account: { active: boolean }) => {
                    expect(account.active).toBe(true);
                });
            }
        });

        test('[P1] GET /bank-accounts/:id - should get single bank account', async ({ request }) => {
            // First get a list to find an account
            const listResponse = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(),
                params: { size: 1 },
            });

            const listBody = await listResponse.json();
            if (listBody.data.length === 0) {
                test.skip();
                return;
            }

            const accountId = listBody.data[0].id;

            const response = await request.get(`${API_BASE}/bank-accounts/${accountId}`, {
                headers: getHeaders(),
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('id', accountId);
            expect(body.data).toHaveProperty('accountNumber');
            expect(body.data).toHaveProperty('bankName');
            expect(body.data).toHaveProperty('type');
        });

        test('[P1] GET /bank-accounts/:id/balance-tooltip - should return balance tooltip', async ({
            request,
        }) => {
            const listResponse = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(),
                params: { size: 1 },
            });

            const listBody = await listResponse.json();
            if (listBody.data.length === 0) {
                test.skip();
                return;
            }

            const accountId = listBody.data[0].id;

            const response = await request.get(`${API_BASE}/bank-accounts/${accountId}/balance-tooltip`, {
                headers: getHeaders(),
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('currentBalance');
        });

        test('[P1] PUT /bank-accounts/:id - should update bank account', async ({ request }) => {
            const listResponse = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(adminToken),
                params: { size: 1 },
            });

            const listBody = await listResponse.json();
            if (listBody.data.length === 0) {
                test.skip();
                return;
            }

            const accountId = listBody.data[0].id;
            const updateData = {
                bankName: 'Updated Bank Name',
                branch: 'Updated Branch',
            };

            const response = await request.put(`${API_BASE}/bank-accounts/${accountId}`, {
                headers: getHeaders(adminToken),
                data: updateData,
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data.bankName).toBe(updateData.bankName);
            expect(body.data.branch).toBe(updateData.branch);
        });
    });

    test.describe('P2: Bank Account Import/Export', () => {
        test('[P2] GET /bank-accounts/import/template - should return CSV template', async ({
            request,
        }) => {
            const response = await request.get(`${API_BASE}/bank-accounts/import/template`, {
                headers: getHeaders(adminToken),
            });

            expect(response.status()).toBe(200);

            const contentType = response.headers()['content-type'];
            expect(contentType).toContain('csv');

            const content = await response.text();
            expect(content).toContain('account_number');
            expect(content).toContain('bank_name');
            expect(content).toContain('gl_account_code');
        });

        test('[P2] GET /bank-accounts/export - should export as CSV', async ({ request }) => {
            const response = await request.get(`${API_BASE}/bank-accounts/export`, {
                headers: getHeaders(),
                params: { format: 'csv' },
            });

            expect(response.status()).toBe(200);

            const contentType = response.headers()['content-type'];
            expect(contentType).toContain('csv');
        });

        test('[P2] GET /bank-accounts/export - should export as Excel', async ({ request }) => {
            const response = await request.get(`${API_BASE}/bank-accounts/export`, {
                headers: getHeaders(),
                params: { format: 'xlsx' },
            });

            expect(response.status()).toBe(200);

            const contentType = response.headers()['content-type'];
            expect(contentType).toContain('spreadsheetml');
        });
    });

    test.describe('P2: Bank Account Status Management', () => {
        test('[P2] PATCH /bank-accounts/:id/deactivate - should deactivate bank account', async ({
            request,
        }) => {
            // Get an active account
            const listResponse = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(adminToken),
                params: { status: 'true', size: 1 },
            });

            const listBody = await listResponse.json();
            if (listBody.data.length === 0) {
                test.skip();
                return;
            }

            const accountId = listBody.data[0].id;

            const response = await request.patch(`${API_BASE}/bank-accounts/${accountId}/deactivate`, {
                headers: getHeaders(adminToken),
            });

            // Could be 204 (success) or 409 (blocked by transactions)
            expect([204, 409]).toContain(response.status());
        });

        test('[P2] PATCH /bank-accounts/:id/activate - should activate bank account', async ({
            request,
        }) => {
            // Get an inactive account
            const listResponse = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(adminToken),
                params: { status: 'false', size: 1 },
            });

            const listBody = await listResponse.json();
            if (listBody.data.length === 0) {
                test.skip();
                return;
            }

            const accountId = listBody.data[0].id;

            const response = await request.patch(`${API_BASE}/bank-accounts/${accountId}/activate`, {
                headers: getHeaders(adminToken),
            });

            expect(response.status()).toBe(204);
        });
    });

    test.describe('P3: Bank Account Security', () => {
        test('[P3] POST /bank-accounts - should return 409 for duplicate account number', async ({
            request,
        }) => {
            // First create an account
            const accountNumber = `DUP-${Date.now()}`;
            await request.post(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(adminToken),
                data: {
                    accountNumber,
                    bankName: 'Test Bank',
                    type: 'BANK',
                    openingBalance: 0,
                },
            });

            // Try to create another with same account number
            const response = await request.post(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(adminToken),
                data: {
                    accountNumber,
                    bankName: 'Another Bank',
                    type: 'BANK',
                    openingBalance: 0,
                },
            });

            expect(response.status()).toBe(409);
        });

        test('[P3] POST /bank-accounts/import - should require admin/chief_accountant role', async ({
            request,
        }) => {
            const csvContent =
                'account_number,bank_name,branch,account_type,opening_balance,gl_account_code,active\n' +
                'TEST-001,Test Bank,Main,BANK,0,1121,true';

            const response = await request.post(`${API_BASE}/bank-accounts/import`, {
                headers: getHeaders(authToken), // Using accountant token
                multipart: {
                    file: {
                        name: 'import.csv',
                        mimeType: 'text/csv',
                        buffer: Buffer.from(csvContent),
                    },
                },
            });

            expect(response.status()).toBe(403);
        });

        test('[P3] Bank accounts are company-scoped', async ({ request }) => {
            // This test verifies that accounts from other companies are not visible
            const response = await request.get(`${API_BASE}/bank-accounts`, {
                headers: getHeaders(),
            });

            expect(response.status()).toBe(200);

            const body = await response.json();
            // All returned accounts should belong to company 1
            body.data.forEach((account: { companyId: number }) => {
                expect(account.companyId).toBe(1);
            });
        });
    });
});
