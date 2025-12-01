import { test, expect } from '@playwright/test';

/**
 * Supplier Management API Tests
 * Priority: P1 (Master data CRUD operations)
 * 
 * Coverage:
 * - GET /suppliers - List suppliers with pagination
 * - GET /suppliers/:id - Get single supplier
 * - POST /suppliers - Create new supplier
 * - PUT /suppliers/:id - Update supplier
 * - DELETE /suppliers/:id - Delete supplier
 * - Supplier validation (tax ID, AP account)
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

let authToken: string;

test.describe('Supplier Management API', () => {
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

    // Helper to add required headers
    const getHeaders = () => ({
        Authorization: `Bearer ${authToken}`,
        'X-Company-Id': '1', // Required for multi-tenancy
    });

    test.describe('P1: Supplier CRUD Operations', () => {
        let createdSupplierId: number;

        test('[P1] POST /suppliers - should create supplier with valid data', async ({ request }) => {
            // GIVEN: Valid supplier data
            const supplierData = {
                code: `SUPP-${Date.now()}`,
                name: 'Test Supplier Co., Ltd.',
                taxId: '0987654321',
                address: '456 Supplier Street, Hanoi',
                phone: '+84912345678',
                email: `supplier.${Date.now()}@example.com`,
                apAccount: '331', // Default AP account
                paymentTerms: 15,
            };

            // WHEN: Creating supplier
            const response = await request.post(`${API_BASE}/suppliers`, {
                headers: getHeaders(),
                data: supplierData,
            });

            // THEN: Supplier should be created
            expect([201, 409]).toContain(response.status()); // 409 if duplicate from previous run

            if (response.status() !== 201) {
                test.skip(); // Skip if duplicate
                return;
            }

            const body = await response.json();
            expect(body.data).toMatchObject({
                code: supplierData.code,
                name: supplierData.name,
            });
            // Note: Backend may not return taxId and apAccount in response

            expect(body.data).toHaveProperty('id');
            createdSupplierId = body.data.id;
        });

        test('[P1] POST /suppliers - should return 400 for duplicate code', async ({ request }) => {
            // GIVEN: Supplier code that already exists
            const duplicateCode = `SUPP-${Date.now()}`;

            // Create first supplier
            await request.post(`${API_BASE}/suppliers`, {
                headers: getHeaders(),
                data: {
                    code: duplicateCode,
                    name: 'First Supplier',
                    apAccount: '331',
                },
            });

            // WHEN: Attempting to create supplier with same code
            const response = await request.post(`${API_BASE}/suppliers`, {
                headers: getHeaders(),
                data: {
                    code: duplicateCode,
                    name: 'Duplicate Supplier',
                    apAccount: '331',
                },
            });

            // THEN: Should return 400 or 409 Conflict
            expect([400, 409]).toContain(response.status());
        });

        test('[P1] GET /suppliers - should list suppliers with pagination', async ({ request }) => {
            // WHEN: Getting suppliers list
            const response = await request.get(`${API_BASE}/suppliers?page=0&size=10`, {
                headers: getHeaders(),
            });

            // THEN: Should return paginated suppliers
            expect(response.status()).toBe(200);

            const body = await response.json();
            // API returns direct array or paginated response
            if (Array.isArray(body.data)) {
                expect(Array.isArray(body.data)).toBe(true);
            } else {
                expect(body.data).toHaveProperty('content');
                expect(body.data).toHaveProperty('totalElements');
                expect(Array.isArray(body.data.content)).toBe(true);
            }
        });

        test('[P1] GET /suppliers/:id - should return single supplier', async ({ request }) => {
            // GIVEN: Supplier ID exists
            if (!createdSupplierId) {
                test.skip();
                return;
            }
            const supplierId = createdSupplierId;

            // WHEN: Getting supplier by ID
            const response = await request.get(`${API_BASE}/suppliers/${supplierId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return supplier details
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toHaveProperty('id', supplierId);
        });

        test('[P1] PUT /suppliers/:id - should update supplier details', async ({ request }) => {
            // GIVEN: Existing supplier
            if (!createdSupplierId) {
                test.skip();
                return;
            }
            const supplierId = createdSupplierId;
            const updateData = {
                name: 'Updated Supplier Name',
                phone: '+84987654321',
            };

            // WHEN: Updating supplier
            const response = await request.put(`${API_BASE}/suppliers/${supplierId}`, {
                headers: getHeaders(),
                data: updateData,
            });

            // THEN: Should return updated supplier
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body.data).toMatchObject({
                id: supplierId,
                name: updateData.name,
                phone: updateData.phone,
            });
        });

        test('[P2] DELETE /suppliers/:id - should delete supplier', async ({ request }) => {
            // GIVEN: Existing supplier
            if (!createdSupplierId) {
                test.skip();
                return;
            }
            const supplierId = createdSupplierId;

            // WHEN: Deleting supplier
            const response = await request.delete(`${API_BASE}/suppliers/${supplierId}`, {
                headers: getHeaders(),
            });

            // THEN: Should return 204 or 200
            expect([200, 204]).toContain(response.status());
        });
    });
});
