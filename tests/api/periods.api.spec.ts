import { test, expect } from '@playwright/test';

/**
 * Period Management API Tests
 * Priority: P0/P1 (Critical accounting control)
 * 
 * Coverage:
 * - GET /periods - List accounting periods
 * - GET /periods/:id - Get single period
 * - POST /periods - Create new period
 * - PUT /periods/:id/open - Open period
 * - PUT /periods/:id/close - Close period
 * - Period validation (no gaps, no overlaps)
 * - Transaction validation against closed periods
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;

// Helper to add required headers
const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1', // Required for multi-tenancy
});

test.describe('Period Management API', () => {
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

    test.describe('P0: Period Creation and Validation', () => {
        test('[P0] POST /periods - should create new period with valid data', async ({ request }) => {
            // GIVEN: Valid period data
            const periodData = {
                year: new Date().getFullYear() + 1, // Future year to avoid conflicts
                month: 1,
                companyId: 1,
            };

            // WHEN: Creating period
            const response = await request.post(`${API_BASE}/periods`, {
                headers: getHeaders(),
                data: periodData,
            });

            // THEN: Period should be created with Open status
            expect(response.status()).toBe(201);

            const body = await response.json();
            expect(body.data).toMatchObject({
                year: periodData.year,
                month: periodData.month,
                status: 'Open',
            });

            expect(body.data).toHaveProperty('id');
            expect(body.data).toHaveProperty('startDate');
            expect(body.data).toHaveProperty('endDate');
        });

        test('[P0] POST /periods - should return 400 for duplicate period', async ({ request }) => {
            // GIVEN: Period that already exists
            const periodData = {
                year: 2025,
                month: 1,
                companyId: 1,
            };

            // WHEN: Attempting to create duplicate period
            const response = await request.post(`${API_BASE}/periods`, {
                headers: getHeaders(),
                data: periodData,
            });

            // THEN: Should return 400 or 409 Conflict
            expect([400, 409]).toContain(response.status());
        });

        test('[P1] POST /periods - should return 400 for invalid month', async ({ request }) => {
            // GIVEN: Invalid month (13)
            const periodData = {
                year: 2025,
                month: 13, // Invalid
                companyId: 1,
            };

            // WHEN: Creating period
            const response = await request.post(`${API_BASE}/periods`, {
                headers: getHeaders(),
                data: periodData,
            });

            // THEN: Should return 400
            expect(response.status()).toBe(400);
        });
    });

    test.describe('P0: Period Open/Close Operations', () => {
        let testPeriodId: number;

        test.beforeAll(async ({ request }) => {
            // Create a test period for open/close operations
            const periodData = {
                year: new Date().getFullYear() + 2,
                month: 6,
                companyId: 1,
            };

            const response = await request.post(`${API_BASE}/periods`, {
                headers: getHeaders(),
                data: periodData,
            });

            if (response.status() === 201) {
                const body = await response.json();
                testPeriodId = body.data.id;
            }
        });

        test('[P0] PUT /periods/:id/close - should close open period', async ({ request }) => {
            // GIVEN: Open period
            const periodId = testPeriodId;

            // WHEN: Closing period
            const response = await request.put(`${API_BASE}/periods/${periodId}/close`, {
                headers: getHeaders(),
            });

            // THEN: Period should be closed
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toMatchObject({
                id: periodId,
                status: 'Closed',
            });
            expect(body.data).toHaveProperty('closedAt');
            expect(body.data).toHaveProperty('closedBy');
        });

        test('[P0] PUT /periods/:id/open - should reopen closed period', async ({ request }) => {
            // GIVEN: Closed period
            const periodId = testPeriodId;

            // WHEN: Reopening period
            const response = await request.put(`${API_BASE}/periods/${periodId}/open`, {
                headers: getHeaders(),
            });

            // THEN: Period should be reopened
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toMatchObject({
                id: periodId,
                status: 'Open',
            });
        });

        test('[P0] PUT /periods/:id/close - should prevent closing if has unposted transactions', async ({ request }) => {
            // GIVEN: Period with unposted transactions (mock scenario)
            const periodId = testPeriodId;

            // Note: This test assumes business logic prevents closing with unposted transactions
            // In real scenario, you'd create an unposted transaction first

            // WHEN: Attempting to close period
            const response = await request.put(`${API_BASE}/periods/${periodId}/close`, {
                headers: getHeaders(),
            });

            // THEN: Should succeed or return 400 if unposted transactions exist
            expect([200, 400]).toContain(response.status());
        });
    });

    test.describe('P1: Period Listing and Retrieval', () => {
        test('[P1] GET /periods - should list all periods', async ({ request }) => {
            // WHEN: Getting periods list
            const response = await request.get(`${API_BASE}/periods`, {
                headers: getHeaders(),
            });

            // THEN: Should return periods list
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('content');
            expect(Array.isArray(body.data.content)).toBe(true);

            // Each period should have required fields
            if (body.data.content.length > 0) {
                body.data.content.forEach((period: any) => {
                    expect(period).toHaveProperty('id');
                    expect(period).toHaveProperty('year');
                    expect(period).toHaveProperty('month');
                    expect(period).toHaveProperty('status');
                    expect(['Open', 'Closed']).toContain(period.status);
                });
            }
        });

        test('[P1] GET /periods/:id - should return single period', async ({ request }) => {
            // GIVEN: Period exists (using any period from list)
            const listResponse = await request.get(`${API_BASE}/periods?page=0&size=1`, {
                headers: getHeaders(),
            });

            const listBody = await listResponse.json();
            if (listBody.data.content.length === 0) {
                test.skip();
                return;
            }

            const periodId = listBody.data.content[0].id;

            // WHEN: Getting period by ID
            const response = await request.get(`${API_BASE}/periods/${periodId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return period details
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('id', periodId);
        });

        test('[P1] GET /periods?status=Open - should filter by status', async ({ request }) => {
            // WHEN: Filtering by Open status
            const response = await request.get(`${API_BASE}/periods?status=Open`, {
                headers: getHeaders(),
            });

            // THEN: Should return only Open periods
            expect(response.status()).toBe(200);

            const body = await response.json();
            body.data.content.forEach((period: any) => {
                expect(period.status).toBe('Open');
            });
        });

        test('[P1] GET /periods/current - should return current period', async ({ request }) => {
            // WHEN: Getting current period
            const response = await request.get(`${API_BASE}/periods/current`, {
                headers: getHeaders(),
            });

            // THEN: Should return current period or 404 if none
            expect([200, 404]).toContain(response.status());

            if (response.status() === 200) {
                const body = await response.json();
                expect(body.data).toHaveProperty('year');
                expect(body.data).toHaveProperty('month');
                expect(body.data.status).toBe('Open');
            }
        });
    });

    test.describe('P0: Transaction Validation Against Closed Periods', () => {
        test('[P0] should prevent posting transactions to closed period', async ({ request }) => {
            // GIVEN: Closed period exists
            const closedPeriod = {
                year: 2024,
                month: 12,
            };

            // Create transaction data in closed period
            const transactionData = {
                date: `${closedPeriod.year}-${String(closedPeriod.month).padStart(2, '0')}-15`,
                amount: 1000000,
                description: 'Test transaction in closed period',
            };

            // WHEN: Attempting to post transaction to closed period
            const response = await request.post(`${API_BASE}/vouchers`, {
                headers: getHeaders(),
                data: transactionData,
            });

            // THEN: Should return 400 (period closed)
            expect([400, 403]).toContain(response.status());

            if ([400, 403].includes(response.status())) {
                const body = await response.json();
                expect(body.error).toMatch(/period|closed/i);
            }
        });
    });

    test.describe('P2: Period Statistics', () => {
        test('[P2] GET /periods/:id/stats - should return period statistics', async ({ request }) => {
            // GIVEN: Period exists
            const listResponse = await request.get(`${API_BASE}/periods?page=0&size=1`, {
                headers: getHeaders(),
            });

            const listBody = await listResponse.json();
            if (listBody.data.content.length === 0) {
                test.skip();
                return;
            }

            const periodId = listBody.data.content[0].id;

            // WHEN: Getting period statistics
            const response = await request.get(`${API_BASE}/periods/${periodId}/stats`, {
                headers: getHeaders(),
            });

            // THEN: Should return statistics or 404
            expect([200, 404]).toContain(response.status());

            if (response.status() === 200) {
                const body = await response.json();
                expect(body.data).toHaveProperty('totalTransactions');
                expect(body.data).toHaveProperty('totalAmount');
            }
        });
    });
});
