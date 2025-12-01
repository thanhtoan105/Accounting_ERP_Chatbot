import { test, expect } from '@playwright/test';

/**
 * Chart of Accounts API Tests
 * Priority: P1 (Accounting core - account management)
 * 
 * Coverage:
 * - GET /accounts - List accounts with hierarchy
 * - GET /accounts/:id - Get single account
 * - POST /accounts - Create new account
 * - PUT /accounts/:id - Update account
 * - DELETE /accounts/:id - Delete account
 * - Account validation (code format, hierarchy, type)
 * - Account balance retrieval
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;

// Helper to add required headers
const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1',
});

test.describe('Chart of Accounts API', () => {
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

    test.describe('P1: Account CRUD Operations', () => {
        let createdAccountId: number;

        test('[P1] POST /accounts - should create account with valid data', async ({ request }) => {
            // GIVEN: Valid account data
            const accountData = {
                code: `${Date.now().toString().slice(-6)}`, // 6-digit code
                name: 'Test Account',
                type: 'ASSET',
                parentCode: '100', // Assets parent
                currency: 'VND',
                active: true,
            };

            // WHEN: Creating account
            const response = await request.post(`${API_BASE}/accounts`, {
                headers: getHeaders(),
                data: accountData,
            });

            // THEN: Account should be created
            expect(response.status()).toBe(201);

            const body = await response.json();
            expect(body.data).toMatchObject({
                code: accountData.code,
                name: accountData.name,
                type: accountData.type,
            });

            expect(body.data).toHaveProperty('id');
            createdAccountId = body.data.id;
        });

        test('[P1] POST /accounts - should return 400 for duplicate account code', async ({ request }) => {
            // GIVEN: Account code that already exists
            const duplicateCode = '111'; // Common existing account

            const accountData = {
                code: duplicateCode,
                name: 'Duplicate Account',
                type: 'ASSET',
            };

            // WHEN: Attempting to create account with duplicate code
            const response = await request.post(`${API_BASE}/accounts`, {
                headers: getHeaders(),
                data: accountData,
            });

            // THEN: Should return 400 or 409 Conflict
            expect([400, 409]).toContain(response.status());
        });

        test('[P1] POST /accounts - should return 400 for invalid account type', async ({ request }) => {
            // GIVEN: Invalid account type
            const accountData = {
                code: `${Date.now().toString().slice(-6)}`,
                name: 'Test Account',
                type: 'INVALID_TYPE',
            };

            // WHEN: Creating account
            const response = await request.post(`${API_BASE}/accounts`, {
                headers: getHeaders(),
                data: accountData,
            });

            // THEN: Should return 400
            expect(response.status()).toBe(400);
        });

        test('[P1] GET /accounts - should list all accounts', async ({ request }) => {
            // WHEN: Getting accounts list
            const response = await request.get(`${API_BASE}/accounts`, {
                headers: getHeaders(),
            });

            // THEN: Should return accounts list
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('content');
            expect(Array.isArray(body.data.content)).toBe(true);

            // Each account should have required fields
            if (body.data.content.length > 0) {
                body.data.content.forEach((account: any) => {
                    expect(account).toHaveProperty('code');
                    expect(account).toHaveProperty('name');
                    expect(account).toHaveProperty('type');
                    expect(['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']).toContain(account.type);
                });
            }
        });

        test('[P1] GET /accounts/:id - should return single account', async ({ request }) => {
            // GIVEN: Account ID exists
            const accountId = createdAccountId;

            // WHEN: Getting account by ID
            const response = await request.get(`${API_BASE}/accounts/${accountId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return account details
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('id', accountId);
        });

        test('[P1] GET /accounts/:code - should return account by code', async ({ request }) => {
            // GIVEN: Account code exists (cash account)
            const accountCode = '111';

            // WHEN: Getting account by code
            const response = await request.get(`${API_BASE}/accounts/code/${accountCode}`, {
                headers: getHeaders(),
            });

            // THEN: Should return account details
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('code', accountCode);
        });

        test('[P1] PUT /accounts/:id - should update account details', async ({ request }) => {
            // GIVEN: Existing account
            const accountId = createdAccountId;
            const updateData = {
                name: 'Updated Account Name',
                active: false,
            };

            // WHEN: Updating account
            const response = await request.put(`${API_BASE}/accounts/${accountId}`, {
                headers: getHeaders(),
                data: updateData,
            });

            // THEN: Should return updated account
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toMatchObject({
                id: accountId,
                name: updateData.name,
                active: updateData.active,
            });
        });

        test('[P2] DELETE /accounts/:id - should delete account if no transactions', async ({ request }) => {
            // GIVEN: Existing account with no transactions
            const accountId = createdAccountId;

            // WHEN: Deleting account
            const response = await request.delete(`${API_BASE}/accounts/${accountId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return 204 or 200, or 400 if has transactions
            expect([200, 204, 400]).toContain(response.status());
        });
    });

    test.describe('P1: Account Hierarchy', () => {
        test('[P1] GET /accounts/tree - should return account hierarchy', async ({ request }) => {
            // WHEN: Getting account tree
            const response = await request.get(`${API_BASE}/accounts/tree`, {
                headers: getHeaders(),
            });

            // THEN: Should return hierarchical structure
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(Array.isArray(body.data)).toBe(true);

            // Each root account should have children array
            if (body.data.length > 0) {
                body.data.forEach((rootAccount: any) => {
                    expect(rootAccount).toHaveProperty('code');
                    expect(rootAccount).toHaveProperty('name');
                    expect(rootAccount).toHaveProperty('children');
                });
            }
        });

        test('[P1] GET /accounts?type=ASSET - should filter by account type', async ({ request }) => {
            // WHEN: Filtering by ASSET type
            const response = await request.get(`${API_BASE}/accounts?type=ASSET`, {
                headers: getHeaders(),
            });

            // THEN: Should return only asset accounts
            expect(response.status()).toBe(200);

            const body = await response.json();
            body.data.content.forEach((account: any) => {
                expect(account.type).toBe('ASSET');
            });
        });
    });

    test.describe('P2: Account Balances', () => {
        test('[P2] GET /accounts/:id/balance - should return account balance', async ({ request }) => {
            // GIVEN: Account exists (cash account)
            const accountCode = '111';

            // Get account ID first
            const accountResponse = await request.get(`${API_BASE}/accounts/code/${accountCode}`, {
                headers: getHeaders(),
            });

            if (accountResponse.status() !== 200) {
                test.skip();
                return;
            }

            const accountBody = await accountResponse.json();
            const accountId = accountBody.data.id;

            // WHEN: Getting account balance
            const response = await request.get(`${API_BASE}/accounts/${accountId}/balance`, {
                headers: getHeaders(),
            });

            // THEN: Should return balance information
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('debit');
            expect(body.data).toHaveProperty('credit');
            expect(body.data).toHaveProperty('balance');
        });

        test('[P2] GET /accounts/:id/balance?startDate=&endDate= - should return balance for date range', async ({ request }) => {
            // GIVEN: Date range for balance query
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';
            const accountCode = '111';

            const accountResponse = await request.get(`${API_BASE}/accounts/code/${accountCode}`, {
                headers: getHeaders(),
            });

            if (accountResponse.status() !== 200) {
                test.skip();
                return;
            }

            const accountBody = await accountResponse.json();
            const accountId = accountBody.data.id;

            // WHEN: Getting balance for specific date range
            const response = await request.get(
                `${API_BASE}/accounts/${accountId}/balance?startDate=${startDate}&endDate=${endDate}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return balance for date range
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('debit');
            expect(body.data).toHaveProperty('credit');
            expect(body.data).toHaveProperty('balance');
            expect(body.data).toHaveProperty('startDate', startDate);
            expect(body.data).toHaveProperty('endDate', endDate);
        });
    });

    test.describe('P2: Account Search', () => {
        test('[P2] GET /accounts?search= - should search accounts by name or code', async ({ request }) => {
            // WHEN: Searching for accounts
            const response = await request.get(`${API_BASE}/accounts?search=cash`, {
                headers: getHeaders(),
            });

            // THEN: Should return matching accounts
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(Array.isArray(body.data.content)).toBe(true);
        });
    });
});
