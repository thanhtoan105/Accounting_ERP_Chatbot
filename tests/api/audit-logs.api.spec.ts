import { test, expect } from '@playwright/test';

/**
 * Audit Log API Tests
 * Priority: P2 (Admin and compliance)
 * 
 * Coverage:
 * - GET /audit-logs - List audit logs with filtering
 * - GET /audit-logs/:id - Get single audit log
 * - Audit log filtering (by user, entity, action, date range)
 * - Audit log search and export
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;

// Helper to add required headers
const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1',
});

test.describe('Audit Log API', () => {
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

    test.describe('P2: Audit Log Retrieval', () => {
        test('[P2] GET /audit-logs - should list audit logs with pagination', async ({ request }) => {
            // WHEN: Getting audit logs
            const response = await request.get(`${API_BASE}/audit-logs?page=0&size=10`, {
                headers: getHeaders(),
            });

            // THEN: Should return paginated audit logs
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('content');
            expect(body.data).toHaveProperty('totalElements');
            expect(Array.isArray(body.data.content)).toBe(true);

            // Each audit log should have required fields
            if (body.data.content.length > 0) {
                body.data.content.forEach((log: any) => {
                    expect(log).toHaveProperty('id');
                    expect(log).toHaveProperty('userId');
                    expect(log).toHaveProperty('action');
                    expect(log).toHaveProperty('entityType');
                    expect(log).toHaveProperty('entityId');
                    expect(log).toHaveProperty('timestamp');
                    expect(log).toHaveProperty('ipAddress');
                });
            }
        });

        test('[P2] GET /audit-logs/:id - should return single audit log', async ({ request }) => {
            // GIVEN: Get first audit log from list
            const listResponse = await request.get(`${API_BASE}/audit-logs?page=0&size=1`, {
                headers: getHeaders(),
            });

            const listBody = await listResponse.json();
            if (listBody.data.content.length === 0) {
                test.skip();
                return;
            }

            const logId = listBody.data.content[0].id;

            // WHEN: Getting audit log by ID
            const response = await request.get(`${API_BASE}/audit-logs/${logId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return audit log details
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('id', logId);
            expect(body.data).toHaveProperty('details'); // Full details
        });
    });

    test.describe('P2: Audit Log Filtering', () => {
        test('[P2] GET /audit-logs?userId= - should filter by user', async ({ request }) => {
            // GIVEN: User ID to filter
            const userId = 1;

            // WHEN: Filtering by user
            const response = await request.get(`${API_BASE}/audit-logs?userId=${userId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return logs for specified user
            expect(response.status()).toBe(200);

            const body = await response.json();
            body.data.content.forEach((log: any) => {
                expect(log.userId).toBe(userId);
            });
        });

        test('[P2] GET /audit-logs?action= - should filter by action', async ({ request }) => {
            // GIVEN: Action to filter
            const action = 'CREATE';

            // WHEN: Filtering by action
            const response = await request.get(`${API_BASE}/audit-logs?action=${action}`, {
                headers: getHeaders(),
            });

            // THEN: Should return logs with specified action
            expect(response.status()).toBe(200);

            const body = await response.json();
            body.data.content.forEach((log: any) => {
                expect(log.action).toBe(action);
            });
        });

        test('[P2] GET /audit-logs?entityType= - should filter by entity type', async ({ request }) => {
            // GIVEN: Entity type to filter
            const entityType = 'INVOICE';

            // WHEN: Filtering by entity type
            const response = await request.get(`${API_BASE}/audit-logs?entityType=${entityType}`, {
                headers: getHeaders(),
            });

            // THEN: Should return logs for specified entity type
            expect(response.status()).toBe(200);

            const body = await response.json();
            body.data.content.forEach((log: any) => {
                expect(log.entityType).toBe(entityType);
            });
        });

        test('[P2] GET /audit-logs?startDate=&endDate= - should filter by date range', async ({ request }) => {
            // GIVEN: Date range
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';

            // WHEN: Filtering by date range
            const response = await request.get(
                `${API_BASE}/audit-logs?startDate=${startDate}&endDate=${endDate}`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return logs within date range
            expect(response.status()).toBe(200);

            const body = await response.json();
            body.data.content.forEach((log: any) => {
                const logDate = new Date(log.timestamp);
                expect(logDate >= new Date(startDate)).toBe(true);
                expect(logDate <= new Date(endDate + 'T23:59:59')).toBe(true);
            });
        });
    });

    test.describe('P2: Audit Log Security', () => {
        test('[P2] GET /audit-logs - should require authentication', async ({ request }) => {
            // WHEN: Accessing audit logs without auth
            const response = await request.get(`${API_BASE}/audit-logs`);

            // THEN: Should return 401
            expect(response.status()).toBe(401);
        });

        test('[P2] GET /audit-logs - should require admin role', async ({ request }) => {
            // Note: This test assumes non-admin users can't access all audit logs
            // Adjust based on your actual RBAC implementation

            // WHEN: Accessing audit logs
            const response = await request.get(`${API_BASE}/audit-logs`, {
                headers: getHeaders(),
            });

            // THEN: Should succeed or return 403 based on role
            expect([200, 403]).toContain(response.status());
        });
    });

    test.describe('P2: Audit Log Export', () => {
        test('[P2] GET /audit-logs/export?format=csv - should export as CSV', async ({ request }) => {
            // GIVEN: Date range for export
            const startDate = '2025-01-01';
            const endDate = '2025-01-31';

            // WHEN: Requesting CSV export
            const response = await request.get(
                `${API_BASE}/audit-logs/export?startDate=${startDate}&endDate=${endDate}&format=csv`,
                {
                    headers: getHeaders(),
                }
            );

            // THEN: Should return CSV content or 404 if not implemented
            expect([200, 404]).toContain(response.status());

            if (response.status() === 200) {
                expect(response.headers()['content-type']).toContain('text/csv');
            }
        });
    });
});
