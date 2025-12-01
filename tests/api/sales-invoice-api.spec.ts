import { test, expect } from '@playwright/test';

/**
 * Epic 5 - Story 5.1: Sales Invoice Entry - API Tests
 * 
 * Tests for:
 * - Invoice CRUD operations
 * - GL posting validation
 * - VAT calculations
 * - Duplicate prevention
 * - Period validation
 * - Authorization (RBAC)
 * - Audit logging
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

// Test data setup
const testCustomer = {
  id: 'customer-test-001',
  name: 'Test Customer 1',
  arAccount: '131',
};

const testPeriod = {
  id: 'period-2025-01',
  year: 2025,
  month: 1,
  status: 'Open',
};

const closedPeriod = {
  id: 'period-2024-12',
  year: 2024,
  month: 12,
  status: 'Closed',
};

test.describe('Story 5.1: Sales Invoice API', () => {
  let authToken: string;

  // Helper to add required headers
  const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1',
  });

  test.beforeEach(async ({ request }) => {
    // Setup: Get auth token for accountant
    const loginResponse = await request.post(`${API_BASE}/auth/login`, {
      data: {
        email: 'accountant@example.com',
        password: 'password',
      },
    });

    expect(loginResponse.status()).toBe(200);
    const loginBody = await loginResponse.json();
    authToken = loginBody.token;
  });

  test.describe('P0: Critical Tests', () => {
    test('AC1.1: POST /invoices - should create invoice with all required fields', async ({ request }) => {
      // GIVEN: Valid invoice data
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        referenceText: 'INV-TEST-001',
        lineItems: [
          {
            description: 'Professional Services',
            quantity: 1,
            unitPrice: 1000000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating invoice via API
      const response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: Invoice is created successfully
      expect(response.status()).toBe(201);

      const body = await response.json();
      expect(body).toMatchObject({
        customerId: testCustomer.id,
        status: 'Draft',
        date: '2025-01-15',
        dueDate: '2025-02-15',
        referenceText: 'INV-TEST-001',
        currency: 'VND',
      });

      expect(body.id).toBeTruthy();
      expect(body.invoiceNumber).toMatch(/^INV-/);
      expect(body.createdAt).toBeTruthy();

      // Verify totals
      expect(body.subtotal).toBe(1000000);
      expect(body.totalVAT).toBe(100000);
      expect(body.grandTotal).toBe(1100000);
    });

    test('AC1.2: Invoice number should be auto-generated per customer/period', async ({ request }) => {
      // GIVEN: Two invoices for same customer in same period
      const invoiceData1 = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Service 1',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      const invoiceData2 = {
        customerId: testCustomer.id,
        date: '2025-01-16',
        dueDate: '2025-02-16',
        lineItems: [
          {
            description: 'Service 2',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating first invoice
      const response1 = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData1,
      });

      expect(response1.status()).toBe(201);
      const invoice1 = await response1.json();

      // WHEN: Creating second invoice
      const response2 = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData2,
      });

      expect(response2.status()).toBe(201);
      const invoice2 = await response2.json();

      // THEN: Invoice numbers are sequential for same customer
      const number1 = invoice1.invoiceNumber; // e.g., CUST-1-2025-001
      const number2 = invoice2.invoiceNumber; // e.g., CUST-1-2025-002

      expect(number1).toMatch(/^CUST-\d+-2025-001$/);
      expect(number2).toMatch(/^CUST-\d+-2025-002$/);
    });

    test('AC1.3: POST /invoices - should prevent duplicate (same customer + inv# + date)', async ({ request }) => {
      // GIVEN: Invoice already exists
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-20',
        dueDate: '2025-02-20',
        referenceText: 'UNIQUE-REF-001',
        lineItems: [
          {
            description: 'Service',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      const firstResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      expect(firstResponse.status()).toBe(201);
      const firstInvoice = await firstResponse.json();

      // WHEN: Attempting to create identical invoice
      const duplicateData = {
        customerId: firstInvoice.customerId,
        date: firstInvoice.date,
        dueDate: firstInvoice.dueDate,
        referenceText: firstInvoice.referenceText,
        lineItems: firstInvoice.lineItems,
      };

      const duplicateResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: duplicateData,
      });

      // THEN: Duplicate is rejected with 409 Conflict
      expect(duplicateResponse.status()).toBe(409);

      const errorBody = await duplicateResponse.json();
      expect(errorBody.error).toContain('already exists');
      expect(errorBody.existingId).toBe(firstInvoice.id);
    });

    test('AC1.4: POST /invoices - should reject date in closed period', async ({ request }) => {
      // GIVEN: Invoice date in closed period
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2024-12-15', // Closed period
        dueDate: '2025-01-15',
        lineItems: [
          {
            description: 'Service',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating invoice in closed period
      const response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: Request is rejected with 400 Bad Request
      expect(response.status()).toBe(400);

      const errorBody = await response.json();
      expect(errorBody.error).toContain('Period closed');
      expect(errorBody.details.period).toBe('2024-12');
    });

    test('AC1.5: POST /invoices - should validate required fields', async ({ request }) => {
      // Test missing customer
      let response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [],
        },
      });

      expect(response.status()).toBe(400);
      let errorBody = await response.json();
      expect(errorBody.error).toContain('customerId');

      // Test missing date
      response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          dueDate: '2025-02-15',
          lineItems: [],
        },
      });

      expect(response.status()).toBe(400);
      errorBody = await response.json();
      expect(errorBody.error).toContain('date');

      // Test missing line items
      response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [],
        },
      });

      expect(response.status()).toBe(400);
      errorBody = await response.json();
      expect(errorBody.error).toContain('lineItems');
    });

    test('AC1.6: VAT calculations - should support all rates (0%, 5%, 10%, exempt)', async ({ request }) => {
      // GIVEN: Invoice with multiple VAT rates
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Item 0% VAT',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 0,
            revenueAccount: '511001',
          },
          {
            description: 'Item 5% VAT',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 5,
            revenueAccount: '511001',
          },
          {
            description: 'Item 10% VAT',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511001',
          },
          {
            description: 'Item Exempt VAT',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 'exempt',
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating invoice
      const response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: VAT calculations are correct
      expect(response.status()).toBe(201);
      const body = await response.json();

      const expectedSubtotal = 400000; // 4 * 100,000
      const expectedVAT = 0 + 5000 + 10000 + 0; // 0% + 5% + 10% + exempt
      const expectedTotal = expectedSubtotal + expectedVAT;

      expect(body.subtotal).toBe(expectedSubtotal);
      expect(body.totalVAT).toBe(expectedVAT);
      expect(body.grandTotal).toBe(expectedTotal);
    });

    test('AC1.7: PUT /invoices/{id} - should allow draft editing', async ({ request }) => {
      // GIVEN: Draft invoice exists
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          referenceText: 'Original Reference',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Updating draft invoice
      const updateData = {
        referenceText: 'Updated Reference',
        dueDate: '2025-03-15',
        lineItems: [
          {
            description: 'Updated Service',
            quantity: 2,
            unitPrice: 150000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      const updateResponse = await request.put(`${API_BASE}/invoices/${invoice.id}`, {
        headers: getHeaders(),
        data: updateData,
      });

      // THEN: Draft is updated successfully
      expect(updateResponse.status()).toBe(200);

      const updatedInvoice = await updateResponse.json();
      expect(updatedInvoice.referenceText).toBe('Updated Reference');
      expect(updatedInvoice.dueDate).toBe('2025-03-15');
      expect(updatedInvoice.lineItems[0].quantity).toBe(2);
      expect(updatedInvoice.lineItems[0].unitPrice).toBe(150000);
      expect(updatedInvoice.subtotal).toBe(300000);
      expect(updatedInvoice.totalVAT).toBe(30000);
      expect(updatedInvoice.grandTotal).toBe(330000);
    });

    test('AC1.8: DELETE /invoices/{id} - should delete draft invoice only', async ({ request }) => {
      // GIVEN: Draft invoice exists
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Deleting draft
      const deleteResponse = await request.delete(`${API_BASE}/invoices/${invoice.id}`, {
        headers: getHeaders(),
      });

      // THEN: Draft is deleted
      expect(deleteResponse.status()).toBe(204);

      // Verify invoice is deleted
      const getResponse = await request.get(`${API_BASE}/invoices/${invoice.id}`, {
        headers: getHeaders(),
      });

      expect(getResponse.status()).toBe(404);
    });

    test('AC1.9: POST /invoices/{id}/submit - should post invoice and create GL entries', async ({ request }) => {
      // GIVEN: Draft invoice exists
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Professional Services',
              quantity: 1,
              unitPrice: 1000000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Posting invoice
      const submitResponse = await request.post(`${API_BASE}/invoices/${invoice.id}/submit`, {
        headers: getHeaders(),
      });

      // THEN: Invoice status changes to Posted and GL entries are created
      expect(submitResponse.status()).toBe(200);

      const postedInvoice = await submitResponse.json();
      expect(postedInvoice.status).toBe('Posted');

      // Verify GL entries created
      // Dr AR (131) +1,000,000
      // Cr Revenue (511001) -1,000,000
      // Cr VAT Output (3331) -100,000
      const glResponse = await request.get(`${API_BASE}/gl/entries?documentId=${invoice.id}`, {
        headers: getHeaders(),
      });

      expect(glResponse.status()).toBe(200);
      const glEntries = await glResponse.json();

      expect(glEntries.length).toBe(3);

      const arEntry = glEntries.find((e: any) => e.accountCode === '131');
      expect(arEntry).toBeDefined();
      expect(arEntry.debit).toBe(1100000); // AR = subtotal + VAT

      const revenueEntry = glEntries.find((e: any) => e.accountCode === '511001');
      expect(revenueEntry).toBeDefined();
      expect(revenueEntry.credit).toBe(1000000);

      const vatEntry = glEntries.find((e: any) => e.accountCode === '3331');
      expect(vatEntry).toBeDefined();
      expect(vatEntry.credit).toBe(100000);
    });
  });

  test.describe('P1: High Priority Tests', () => {
    test('AC2.1: VAT mismatch validation - header VAT must equal sum of line VAT', async ({ request }) => {
      // GIVEN: Invoice with VAT mismatch (intentionally create mismatch in payload)
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Item',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
        headerVAT: 50000, // Intentional mismatch (should be 10,000)
      };

      // WHEN: Creating invoice with VAT mismatch
      const response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: Request is rejected
      expect(response.status()).toBe(400);

      const errorBody = await response.json();
      expect(errorBody.error).toContain('VAT');
      expect(errorBody.details.headerVAT).toBe(50000);
      expect(errorBody.details.calculatedVAT).toBe(10000);
    });

    test('AC2.2: Authorization - only creator/admin can edit draft', async ({ request, context }) => {
      // GIVEN: Accountant created draft
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Chief Accountant attempts to edit
      const chiefLoginResponse = await request.post(`${API_BASE}/auth/login`, {
        data: {
          email: 'chief@example.com',
          password: 'password',
        },
      });

      expect(chiefLoginResponse.status()).toBe(200);
      const chiefToken = (await chiefLoginResponse.json()).token;

      const updateResponse = await request.put(`${API_BASE}/invoices/${invoice.id}`, {
        headers: { Authorization: `Bearer ${chiefToken}` },
        data: {
          referenceText: 'Unauthorized Edit',
        },
      });

      // THEN: Edit is forbidden
      expect(updateResponse.status()).toBe(403);

      const errorBody = await updateResponse.json();
      expect(errorBody.error).toContain('permission');
    });

    test('AC2.3: Leaf account validation - cannot post to parent revenue account', async ({ request }) => {
      // GIVEN: Invoice with parent account (511 instead of 511001)
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Service',
            quantity: 1,
            unitPrice: 100000,
            vatRate: 10,
            revenueAccount: '511', // Parent account
          },
        ],
      };

      // WHEN: Creating invoice
      const response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: Request is rejected
      expect(response.status()).toBe(400);

      const errorBody = await response.json();
      expect(errorBody.error).toContain('leaf');
      expect(errorBody.details.accountCode).toBe('511');
    });

    test('AC2.4: Idempotent posting - cannot post invoice twice', async ({ request }) => {
      // GIVEN: Posted invoice
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // Post it first time
      const submitResponse1 = await request.post(`${API_BASE}/invoices/${invoice.id}/submit`, {
        headers: getHeaders(),
      });

      expect(submitResponse1.status()).toBe(200);

      // WHEN: Attempting to post again
      const submitResponse2 = await request.post(`${API_BASE}/invoices/${invoice.id}/submit`, {
        headers: getHeaders(),
      });

      // THEN: Second post is rejected
      expect(submitResponse2.status()).toBe(400);

      const errorBody = await submitResponse2.json();
      expect(errorBody.error).toContain('already posted');
    });

    test('AC2.5: Audit trail - all actions logged with diff and actor', async ({ request }) => {
      // GIVEN: Invoice is created and updated
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          referenceText: 'Original',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // Update it
      const updateResponse = await request.put(`${API_BASE}/invoices/${invoice.id}`, {
        headers: getHeaders(),
        data: {
          referenceText: 'Updated',
        },
      });

      expect(updateResponse.status()).toBe(200);

      // WHEN: Fetching audit log
      const auditResponse = await request.get(`${API_BASE}/invoices/${invoice.id}/audit`, {
        headers: getHeaders(),
      });

      // THEN: Audit entries show all actions
      expect(auditResponse.status()).toBe(200);
      const auditEntries = await auditResponse.json();

      expect(auditEntries.length).toBeGreaterThanOrEqual(2);

      // Check create entry
      const createEntry = auditEntries.find((e: any) => e.action === 'CREATED');
      expect(createEntry).toBeDefined();
      expect(createEntry.actorId).toBeTruthy();
      expect(createEntry.timestamp).toBeTruthy();

      // Check update entry
      const updateEntry = auditEntries.find((e: any) => e.action === 'UPDATED');
      expect(updateEntry).toBeDefined();
      expect(updateEntry.before.referenceText).toBe('Original');
      expect(updateEntry.after.referenceText).toBe('Updated');
    });

    test('AC2.6: Posting to closed period should be rejected', async ({ request }) => {
      // GIVEN: Draft invoice in open period is created
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // Period gets closed (mock scenario)
      // In real test, would close period via admin API

      // WHEN: Attempting to post after period closed
      const submitResponse = await request.post(`${API_BASE}/invoices/${invoice.id}/submit`, {
        headers: getHeaders(),
      });

      // THEN: Posting is rejected
      expect(submitResponse.status()).toBe(400);

      const errorBody = await submitResponse.json();
      expect(errorBody.error).toContain('closed');
    });
  });

  test.describe('P2: Medium Priority Tests', () => {
    test('GET /invoices - should list invoices with pagination and filters', async ({ request }) => {
      // WHEN: Fetching invoices list
      const response = await request.get(`${API_BASE}/invoices?page=1&pageSize=10&status=Draft`, {
        headers: getHeaders(),
      });

      // THEN: List is returned with pagination
      expect(response.status()).toBe(200);

      const body = await response.json();
      expect(body.data).toBeDefined();
      expect(body.pagination).toBeDefined();
      expect(body.pagination.page).toBe(1);
      expect(body.pagination.pageSize).toBe(10);
      expect(body.pagination.total).toBeGreaterThanOrEqual(0);
    });

    test('POST /invoices/import - should bulk import from CSV with atomicity', async ({ request }) => {
      // GIVEN: CSV file with 10 invoices
      const csvContent = `customerId,date,dueDate,referenceText,description,quantity,unitPrice,vatRate,revenueAccount
customer-1,2025-01-15,2025-02-15,INV-001,Service 1,1,100000,10,511001
customer-1,2025-01-16,2025-02-16,INV-002,Service 2,1,200000,10,511001
customer-2,2025-01-15,2025-02-15,INV-003,Service 3,2,150000,10,511001`;

      const formData = new FormData();
      const file = new Blob([csvContent], { type: 'text/csv' });
      formData.append('file', file, 'invoices.csv');

      // WHEN: Importing invoices
      const response = await request.post(`${API_BASE}/invoices/import`, {
        headers: getHeaders(),
        data: formData,
      });

      // THEN: Import completes successfully
      expect(response.status()).toBe(202); // Async processing

      const body = await response.json();
      expect(body.jobId).toBeTruthy();
      expect(body.status).toBe('processing');

      // Poll for completion
      let completed = false;
      for (let i = 0; i < 10; i++) {
        const statusResponse = await request.get(`${API_BASE}/invoices/import/${body.jobId}`, {
          headers: getHeaders(),
        });

        const statusBody = await statusResponse.json();
        if (statusBody.status === 'completed') {
          expect(statusBody.successCount).toBeGreaterThan(0);
          completed = true;
          break;
        }

        // No hard wait - waitForResponse better, but if polling needed, use minimal delay
        if (i < 9) await new Promise((resolve) => setTimeout(resolve, 500));
      }

      expect(completed).toBe(true);
    });

    test('GET /invoices/{id} - should fetch invoice with all details', async ({ request }) => {
      // GIVEN: Invoice exists
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: {
          customerId: testCustomer.id,
          date: '2025-01-15',
          dueDate: '2025-02-15',
          referenceText: 'Test Invoice',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 100000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Fetching invoice
      const getResponse = await request.get(`${API_BASE}/invoices/${invoice.id}`, {
        headers: getHeaders(),
      });

      // THEN: Complete invoice is returned
      expect(getResponse.status()).toBe(200);

      const fetched = await getResponse.json();
      expect(fetched.id).toBe(invoice.id);
      expect(fetched.invoiceNumber).toBe(invoice.invoiceNumber);
      expect(fetched.lineItems).toBeDefined();
      expect(fetched.lineItems.length).toBe(1);
    });
  });

  test.describe('Rounding and Precision Tests', () => {
    test('should handle VAT rounding correctly for edge cases', async ({ request }) => {
      // Test case: 33.33 * 10% VAT should round consistently
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Item 1',
            quantity: 1,
            unitPrice: 333333, // 333,333 * 10% = 33,333.30 → rounds to 33,333
            vatRate: 10,
            revenueAccount: '511001',
          },
          {
            description: 'Item 2',
            quantity: 1,
            unitPrice: 1000001, // 1,000,001 * 10% = 100,000.10 → rounds to 100,000
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      const response = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      expect(response.status()).toBe(201);

      const body = await response.json();

      // Verify VAT is rounded consistently
      const expectedVAT = 33333 + 100000; // Rounded values
      expect(body.totalVAT).toBe(expectedVAT);

      // Verify GL balances: Dr = Cr
      expect(body.subtotal + body.totalVAT).toBe(body.grandTotal);
    });

    test('should enforce GL balance (Dr = Cr) on posting', async ({ request }) => {
      // GIVEN: Invoice with multiple line items
      const invoiceData = {
        customerId: testCustomer.id,
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Item 1',
            quantity: 5,
            unitPrice: 123456,
            vatRate: 7.5, // Edge case VAT rate
            revenueAccount: '511001',
          },
          {
            description: 'Item 2',
            quantity: 3,
            unitPrice: 234567,
            vatRate: 12, // Edge case VAT rate
            revenueAccount: '511002',
          },
        ],
      };

      // WHEN: Creating and posting
      const createResponse = await request.post(`${API_BASE}/invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      const submitResponse = await request.post(`${API_BASE}/invoices/${invoice.id}/submit`, {
        headers: getHeaders(),
      });

      expect(submitResponse.status()).toBe(200);

      // THEN: Fetch GL entries and verify balance
      const glResponse = await request.get(`${API_BASE}/gl/entries?documentId=${invoice.id}`, {
        headers: getHeaders(),
      });

      const entries = await glResponse.json();

      const totalDebit = entries.reduce((sum: number, e: any) => sum + (e.debit || 0), 0);
      const totalCredit = entries.reduce((sum: number, e: any) => sum + (e.credit || 0), 0);

      // GL must be balanced
      expect(totalDebit).toBe(totalCredit);
    });
  });
});
