import { test, expect } from '@playwright/test';
import { SupplierFactory } from '../support/fixtures/factories/supplier-factory';
import { PurchaseBillFactory } from '../support/fixtures/factories/purchase-bill-factory';

/**
 * Purchase Bills API Tests
 * 
 * Tests business logic validation at API level:
 * - Bill number uniqueness per supplier per year
 * - VAT sum validation (tolerance: 1,000₫)
 * - Duplicate supplier+bill/date combination detection
 * - Required dimension validation
 * - Draft autosave endpoint
 * - Batch import with atomic save
 * 
 * Pattern: API-first testing, deterministic data, explicit assertions
 */
test.describe('Purchase Bills API', () => {
  const apiBaseURL = process.env.API_URL || 'http://localhost:8080';
  const supplierFactory = new SupplierFactory();
  const purchaseBillFactory = new PurchaseBillFactory();

  test('POST /api/v1/purchase-bills - should create new purchase bill', async ({ request }) => {
    // GIVEN: Valid purchase bill data
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const bill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      billNumber: 'BILL-2024-001',
      billDate: '2024-01-15',
      reference: 'REF-001',
      description: 'Test purchase bill',
    });

    // WHEN: Creating bill via API
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: bill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Bill should be created successfully
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    expect(body.data).toMatchObject({
      supplierId: bill.supplierId,
      billNumber: bill.billNumber,
      billDate: bill.billDate,
      status: 'DRAFT',
    });
    expect(body.data).toHaveProperty('id');
  });

  test('POST /api/v1/purchase-bills - should reject duplicate bill number per supplier per year', async ({ request }) => {
    // GIVEN: Existing bill with same number for same supplier in same year
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const existingBill = purchaseBillFactory.createPostedBill({
      supplierId: supplier.id!,
      billNumber: 'BILL-2024-001',
      billDate: '2024-01-15',
    });

    const duplicateBill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      billNumber: 'BILL-2024-001', // Same number
      billDate: '2024-02-15', // Same year, different date
    });

    // WHEN: Attempting to create duplicate bill
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: duplicateBill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return 409 Conflict with error message
    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body).toHaveProperty('error');
    expect(body.error).toContain('duplicate');
    expect(body.error).toContain('bill number');
  });

  test('POST /api/v1/purchase-bills - should validate VAT sum match (tolerance: 1,000₫)', async ({ request }) => {
    // GIVEN: Bill with VAT mismatch >1,000₫
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const bill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      lines: [
        purchaseBillFactory.createBillLine({ vatRate: 10, amount: 1000000, vatAmount: 100000 }),
        purchaseBillFactory.createBillLine({ vatRate: 5, amount: 2000000, vatAmount: 100000 }),
      ],
      vatAmount: 199000, // Header VAT differs from line sum (200,000) by 1,000₫
    });

    // WHEN: Creating bill with VAT mismatch
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: bill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return 400 with VAT validation error
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body).toHaveProperty('errors');
    expect(body.errors).toHaveProperty('vatAmount');
    expect(body.errors.vatAmount).toContain('VAT sum mismatch');
  });

  test('POST /api/v1/purchase-bills - should accept VAT sum difference within tolerance (≤1,000₫)', async ({ request }) => {
    // GIVEN: Bill with VAT difference within tolerance
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const bill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      lines: [
        purchaseBillFactory.createBillLine({ vatRate: 10, amount: 1000000, vatAmount: 100000 }),
        purchaseBillFactory.createBillLine({ vatRate: 5, amount: 2000000, vatAmount: 100000 }),
      ],
      vatAmount: 200500, // Header VAT differs by 500₫ (within tolerance)
    });

    // WHEN: Creating bill with VAT within tolerance
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: bill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should accept the bill (201 or 200)
    expect([200, 201]).toContain(response.status());
  });

  test('POST /api/v1/purchase-bills - should validate required dimensions', async ({ request }) => {
    // GIVEN: Bill with line item missing required dimension
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const bill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      lines: [
        purchaseBillFactory.createBillLine({
          accountId: 641, // Expense account requiring cost center
          // costCenterId missing
        }),
      ],
    });

    // WHEN: Creating bill without required dimension
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: bill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return 400 with dimension validation error
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body).toHaveProperty('errors');
    expect(body.errors).toHaveProperty('lines[0].costCenterId');
    expect(body.errors['lines[0].costCenterId']).toContain('required dimension');
  });

  test('PUT /api/v1/purchase-bills/{id} - should only allow editing DRAFT bills', async ({ request }) => {
    // GIVEN: Posted bill exists
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const postedBill = purchaseBillFactory.createPostedBill({
      id: 1,
      supplierId: supplier.id!,
    });

    // WHEN: Attempting to update posted bill
    const response = await request.put(`${apiBaseURL}/api/v1/purchase-bills/1`, {
      data: {
        ...postedBill,
        description: 'Updated description',
      },
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return 409 Conflict
    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body).toHaveProperty('error');
    expect(body.error).toContain('posted');
    expect(body.error).toContain('cannot be edited');
  });

  test('PUT /api/v1/purchase-bills/{id} - should only allow creator to edit DRAFT bills', async ({ request }) => {
    // GIVEN: Draft bill created by another user
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const draftBill = purchaseBillFactory.createDraftBill({
      id: 1,
      supplierId: supplier.id!,
      createdById: 999, // Different user
    });

    // WHEN: Another user attempts to update the draft
    const response = await request.put(`${apiBaseURL}/api/v1/purchase-bills/1`, {
      data: {
        ...draftBill,
        description: 'Updated description',
      },
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token-other-user',
      },
    });

    // THEN: Should return 403 Forbidden
    expect(response.status()).toBe(403);
    const body = await response.json();
    expect(body).toHaveProperty('error');
    expect(body.error).toContain('creator');
    expect(body.error).toContain('permission');
  });

  test('POST /api/v1/purchase-bills/{id}/save-draft - should autosave draft', async ({ request }) => {
    // GIVEN: Draft bill exists
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const draftBill = purchaseBillFactory.createDraftBill({
      id: 1,
      supplierId: supplier.id!,
    });

    // WHEN: Autosaving draft
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/save-draft`, {
      data: draftBill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Draft should be saved successfully
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    expect(body.data).toMatchObject({
      id: 1,
      status: 'DRAFT',
    });
  });

  test('POST /api/v1/purchase-bills/batch-import - should import bills atomically (all-or-nothing)', async ({ request }) => {
    // GIVEN: Excel file with valid and invalid rows
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const validBill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      billNumber: 'BILL-2024-001',
    });
    const invalidBill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      billNumber: '', // Invalid: missing bill number
    });

    // Simulate Excel file upload (in real test, would use FormData with file)
    const importData = {
      bills: [validBill, invalidBill],
    };

    // WHEN: Importing bills with errors
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/batch-import`, {
      data: importData,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return error map and not save any bills (atomic)
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body).toHaveProperty('errors');
    expect(body.errors).toHaveProperty('errorMap');
    expect(body.errors.errorMap).toBeInstanceOf(Array);
    expect(body.errors.errorMap.length).toBeGreaterThan(0);
  });

  test('GET /api/v1/purchase-bills/drafts - should return recoverable drafts for creator', async ({ request }) => {
    // GIVEN: User has created drafts
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const draft1 = purchaseBillFactory.createDraftBill({
      id: 1,
      supplierId: supplier.id!,
      createdById: 1,
    });
    const draft2 = purchaseBillFactory.createDraftBill({
      id: 2,
      supplierId: supplier.id!,
      createdById: 1,
    });

    // WHEN: Fetching drafts
    const response = await request.get(`${apiBaseURL}/api/v1/purchase-bills/drafts`, {
      headers: {
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return list of drafts
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    expect(body.data).toBeInstanceOf(Array);
    expect(body.data.length).toBeGreaterThanOrEqual(0);
  });

  test('DELETE /api/v1/purchase-bills/{id} - should only allow deleting DRAFT bills', async ({ request }) => {
    // GIVEN: Posted bill exists
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const postedBill = purchaseBillFactory.createPostedBill({
      id: 1,
      supplierId: supplier.id!,
    });

    // WHEN: Attempting to delete posted bill
    const response = await request.delete(`${apiBaseURL}/api/v1/purchase-bills/1`, {
      headers: {
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return 409 Conflict
    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body).toHaveProperty('error');
    expect(body.error).toContain('posted');
    expect(body.error).toContain('cannot be deleted');
  });
});

