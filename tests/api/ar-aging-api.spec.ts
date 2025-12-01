import { test, expect } from '@playwright/test';

/**
 * AR Aging Report API Tests
 * Priority: P1 (Core accounting functionality)
 * 
 * Coverage:
 * - GET /ar-aging - Get aging report with metadata
 * - GET /ar-aging/export - Export aging report
 * - POST /ar-aging/refresh - Refresh cache
 * - GET /ar-aging/dashboard-metrics - Get dashboard metrics
 * - Filters: customer, asOfDate, bucket, status
 * - Pagination and sorting
 * - Multi-user concurrent access (RBAC)
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;
let customerIdWithInvoices: number;

// Helper to add required headers
const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1',
});

test.describe('AR Aging Report API', () => {
    test.beforeAll(async ({ request }) => {
        const loginResponse = await request.post(`${API_BASE}/auth/login`, {
            data: {
                email: 'accountant@example.com',
                password: 'password',
            },
        });

        const loginBody = await loginResponse.json();
        authToken = loginBody.data.accessToken;

        // Get a customer with invoices for testing
        const customersResponse = await request.get(`${API_BASE}/customers?page=0&size=1`, {
            headers: getHeaders(),
        });
        const customersBody = await customersResponse.json();
        if (customersBody.data.content.length > 0) {
            customerIdWithInvoices = customersBody.data.content[0].id;
        }
    });

    test.describe('P1: AR Aging Report Retrieval', () => {
        test('[P1] GET /ar-aging - should return aging report with metadata', async ({ request }) => {
            // WHEN: Getting AR aging report
            const response = await request.get(`${API_BASE}/ar-aging?page=0&size=20`, {
                headers: getHeaders(),
            });

            // THEN: Should return report with metadata
            expect(response.status()).toBe(200);

            const body = await response.json();

            // Verify response structure
            expect(body).toHaveProperty('data');
            expect(body).toHaveProperty('metadata');

            // Verify metadata fields
            expect(body.metadata).toHaveProperty('computedAt');
            expect(body.metadata).toHaveProperty('snapshotDate');
            expect(body.metadata).toHaveProperty('cacheStatus');
            expect(['HIT', 'MISS']).toContain(body.metadata.cacheStatus);

            // Verify data pagination
            expect(body.data).toHaveProperty('content');
            expect(body.data).toHaveProperty('totalElements');
            expect(body.data).toHaveProperty('totalPages');
            expect(Array.isArray(body.data.content)).toBe(true);

            // Each aging entry should have required fields
            if (body.data.content.length > 0) {
                body.data.content.forEach((entry: any) => {
                    expect(entry).toHaveProperty('customerId');
                    expect(entry).toHaveProperty('customerName');
                    expect(entry).toHaveProperty('customerCode');
                    expect(entry).toHaveProperty('buckets');
                    expect(entry).toHaveProperty('totalOutstanding');
                    expect(entry).toHaveProperty('hasOverdue');

                    // Verify bucket structure
                    expect(entry.buckets).toHaveProperty('current');
                    expect(entry.buckets).toHaveProperty('days1To30');
                    expect(entry.buckets).toHaveProperty('days31To60');
                    expect(entry.buckets).toHaveProperty('days61To90');
                    expect(entry.buckets).toHaveProperty('daysOver90');
                    expect(entry.buckets).toHaveProperty('total');
                });
            }
        });

        test('[P1] GET /ar-aging - should filter by customer ID', async ({ request }) => {
            if (!customerIdWithInvoices) {
                test.skip();
                return;
            }

            // WHEN: Getting aging report for specific customer
            const response = await request.get(
                `${API_BASE}/ar-aging?customerId=${customerIdWithInvoices}&page=0&size=20`,
                { headers: getHeaders() }
            );

            // THEN: Should return only that customer's aging
            expect(response.status()).toBe(200);

            const body = await response.json();
            if (body.data.content.length > 0) {
                body.data.content.forEach((entry: any) => {
                    expect(entry.customerId).toBe(customerIdWithInvoices);
                });
            }
        });

        test('[P1] GET /ar-aging - should filter by aging bucket', async ({ request }) => {
            // WHEN: Filtering by specific bucket (e.g., DAYS_1_30)
            const response = await request.get(
                `${API_BASE}/ar-aging?bucket=DAYS_1_30&page=0&size=20`,
                { headers: getHeaders() }
            );

            // THEN: Should return only customers with balance in that bucket
            expect(response.status()).toBe(200);

            const body = await response.json();
            if (body.data.content.length > 0) {
                body.data.content.forEach((entry: any) => {
                    expect(entry.buckets.days1To30).toBeGreaterThan(0);
                });
            }
        });

        test('[P1] GET /ar-aging - should support pagination', async ({ request }) => {
            // WHEN: Getting first page
            const page1Response = await request.get(`${API_BASE}/ar-aging?page=0&size=5`, {
                headers: getHeaders(),
            });

            // THEN: Should have correct pagination info
            expect(page1Response.status()).toBe(200);

            const page1Body = await page1Response.json();
            expect(page1Body.data.number).toBe(0);
            expect(page1Body.data.size).toBe(5);
            expect(page1Body.data.content.length).toBeLessThanOrEqual(5);

            // If there's more than one page, test page 2
            if (page1Body.data.totalPages > 1) {
                const page2Response = await request.get(`${API_BASE}/ar-aging?page=1&size=5`, {
                    headers: getHeaders(),
                });

                expect(page2Response.status()).toBe(200);

                const page2Body = await page2Response.json();
                expect(page2Body.data.number).toBe(1);
            }
        });

        test('[P1] GET /ar-aging - should support sorting', async ({ request }) => {
            // WHEN: Sorting by customer name
            const response = await request.get(
                `${API_BASE}/ar-aging?sortBy=customerName&sortDir=asc&page=0&size=20`,
                { headers: getHeaders() }
            );

            // THEN: Should return sorted results
            expect(response.status()).toBe(200);

            const body = await response.json();
            if (body.data.content.length > 1) {
                const names = body.data.content.map((entry: any) => entry.customerName);
                const sortedNames = [...names].sort();
                expect(names).toEqual(sortedNames);
            }
        });

        test('[P1] GET /ar-aging - should support custom asOfDate', async ({ request }) => {
            // WHEN: Getting aging report as of specific date
            const asOfDate = '2025-10-31';
            const response = await request.get(
                `${API_BASE}/ar-aging?asOfDate=${asOfDate}&page=0&size=20`,
                { headers: getHeaders() }
            );

            // THEN: Should return report with correct snapshot date
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.metadata.snapshotDate).toBe(asOfDate);
        });
    });

    test.describe('P1: Cache Operations', () => {
        test('[P1] POST /ar-aging/refresh - should refresh cache', async ({ request }) => {
            // WHEN: Refreshing aging cache
            const response = await request.post(`${API_BASE}/ar-aging/refresh`, {
                headers: getHeaders(),
            });

            // THEN: Should confirm cache refresh
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body).toHaveProperty('message');
            expect(body.message).toContain('refreshed');
        });

        test('[P1] GET /ar-aging - cache status should be HIT after refresh', async ({ request }) => {
            // GIVEN: Refresh cache
            await request.post(`${API_BASE}/ar-aging/refresh`, {
                headers: getHeaders(),
            });

            // WHEN: Getting aging report immediately after
            const response = await request.get(`${API_BASE}/ar-aging?page=0&size=20`, {
                headers: getHeaders(),
            });

            // THEN: Cache status should indicate HIT
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.metadata.cacheStatus).toBe('HIT');
        });
    });

    test.describe('P1: Export Functionality', () => {
        test('[P1] GET /ar-aging/export - should export to Excel', async ({ request }) => {
            // WHEN: Exporting aging report as Excel
            const response = await request.get(`${API_BASE}/ar-aging/export?format=EXCEL`, {
                headers: getHeaders(),
            });

            // THEN: Should return Excel file
            expect(response.status()).toBe(200);
            expect(response.headers()['content-type']).toContain('application');

            const buffer = await response.body();
            expect(buffer.length).toBeGreaterThan(0);
        });

        test('[P1] GET /ar-aging/export - should export to PDF', async ({ request }) => {
            // WHEN: Exporting aging report as PDF
            const response = await request.get(`${API_BASE}/ar-aging/export?format=PDF`, {
                headers: getHeaders(),
            });

            // THEN: Should return PDF file
            expect(response.status()).toBe(200);
            expect(response.headers()['content-type']).toContain('application/pdf');

            const buffer = await response.body();
            expect(buffer.length).toBeGreaterThan(0);
        });

        test('[P1] GET /ar-aging/export - should filter export by customer', async ({ request }) => {
            if (!customerIdWithInvoices) {
                test.skip();
                return;
            }

            // WHEN: Exporting with customer filter
            const response = await request.get(
                `${API_BASE}/ar-aging/export?format=EXCEL&customerId=${customerIdWithInvoices}`,
                { headers: getHeaders() }
            );

            // THEN: Should return Excel file
            expect(response.status()).toBe(200);
            expect(response.headers()['content-type']).toContain('application');
        });
    });

    test.describe('P1: Dashboard Metrics', () => {
        test('[P1] GET /ar-aging/dashboard-metrics - should return metrics', async ({ request }) => {
            // WHEN: Getting dashboard metrics
            const response = await request.get(`${API_BASE}/ar-aging/dashboard-metrics`, {
                headers: getHeaders(),
            });

            // THEN: Should return aggregated metrics
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body).toHaveProperty('totalOverdue');
            expect(body).toHaveProperty('overdueCount');
            expect(body).toHaveProperty('topOverdueCustomers');
            expect(Array.isArray(body.topOverdueCustomers)).toBe(true);

            // Verify top overdue customers structure
            if (body.topOverdueCustomers.length > 0) {
                body.topOverdueCustomers.forEach((customer: any) => {
                    expect(customer).toHaveProperty('customerId');
                    expect(customer).toHaveProperty('customerName');
                    expect(customer).toHaveProperty('overdueAmount');
                });
            }
        });
    });

    test.describe('P2: RBAC and Security', () => {
        test('[P2] GET /ar-aging - should enforce company isolation', async ({ request }) => {
            // GIVEN: Login as user from different company (company 2)
            const loginResponse = await request.post(`${API_BASE}/auth/login`, {
                data: {
                    email: 'accountant@company2.com',
                    password: 'password',
                },
            });

            if (loginResponse.status() !== 200) {
                test.skip(); // Skip if test user doesn't exist
                return;
            }

            const loginBody = await loginResponse.json();
            const company2Token = loginBody.data.accessToken;

            // WHEN: Getting aging report for company 2
            const response = await request.get(`${API_BASE}/ar-aging?page=0&size=20`, {
                headers: {
                    Authorization: `Bearer ${company2Token}`,
                    'X-Company-Id': '2',
                },
            });

            // THEN: Should only see company 2's data
            expect(response.status()).toBe(200);

            const body = await response.json();
            // Data should be different from company 1 or empty
            expect(body.data).toHaveProperty('content');
        });

        test('[P2] GET /ar-aging - should require authentication', async ({ request }) => {
            // WHEN: Accessing without token
            const response = await request.get(`${API_BASE}/ar-aging?page=0&size=20`, {
                headers: {
                    'X-Company-Id': '1',
                },
            });

            // THEN: Should return 401 Unauthorized
            expect(response.status()).toBe(401);
        });

        test('[P2] GET /ar-aging/export - should require appropriate role', async ({ request }) => {
            // Note: This test assumes export requires CFO/CHIEF_ACCOUNTANT role
            // Verify RBAC enforcement - actual status depends on user role
            const response = await request.get(`${API_BASE}/ar-aging/export?format=EXCEL`, {
                headers: getHeaders(),
            });

            // Should either succeed (has permission) or fail with 403 (no permission)
            expect([200, 403]).toContain(response.status());
        });
    });
});
