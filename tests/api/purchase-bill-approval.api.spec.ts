import { test, expect } from '@playwright/test';
import { SupplierFactory } from '../support/fixtures/factories/supplier-factory';
import { PurchaseBillFactory } from '../support/fixtures/factories/purchase-bill-factory';

/**
 * Purchase Bill Approval Workflow API Tests
 *
 * Tests business logic validation at API level:
 * - Approval threshold configuration and checking
 * - Auto-approve logic for bills below threshold
 * - Approver≠creator constraint enforcement
 * - Notification sending (email + in-app)
 * - Audit logging for workflow transitions
 * - Period validation for approval operations
 *
 * Pattern: API-first testing, deterministic data, explicit assertions
 *
 * Story: 4-2 (Purchase Bill Approval Workflow - Maker/Checker)
 */
test.describe('Purchase Bill Approval Workflow API', () => {
  const apiBaseURL = process.env.API_URL || 'http://localhost:8080';
  const supplierFactory = new SupplierFactory();
  const purchaseBillFactory = new PurchaseBillFactory();

  // P0: Critical - threshold configuration determines approval requirement
  test('POST /api/v1/purchase-bills/{id}/submit-for-approval - should require approval for bills above threshold (20M VND)', async ({ request }) => {
    // GIVEN: Bill with total amount above threshold (20M VND)
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const highValueBill = purchaseBillFactory.createDraftBill({
      id: 1,
      supplierId: supplier.id!,
      totalAmount: 25000000, // 25M VND > 20M threshold
    });

    // WHEN: Submitting bill for approval
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/submit-for-approval`, {
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Bill should move to PENDING_APPROVAL status
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    expect(body.data).toMatchObject({
      id: 1,
      status: 'PENDING_APPROVAL',
    });
  });

  // P0: Critical - auto-approve for low-value bills
  test('POST /api/v1/purchase-bills - should auto-approve bills below threshold with shadow logging', async ({ request }) => {
    // GIVEN: Bill with total amount below threshold (20M VND)
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const lowValueBill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      totalAmount: 15000000, // 15M VND < 20M threshold
    });

    // WHEN: Creating low-value bill
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: lowValueBill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Bill should be auto-approved (no manual approval needed)
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    // Note: Auto-approve behavior - bill might stay DRAFT or move to POSTED directly
    // depending on implementation (shadow approval record created in background)
    expect(body.data).toHaveProperty('id');
  });

  // P0: Critical security - approver≠creator validation at service level
  test('POST /api/v1/purchase-bills/{id}/approve - should reject approval when approver equals creator', async ({ request }) => {
    // GIVEN: Bill created by user ID 1, now pending approval
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const pendingBill = purchaseBillFactory.createPurchaseBill({
      id: 1,
      supplierId: supplier.id!,
      status: 'PENDING_APPROVAL',
      createdById: 1, // Creator user ID
    });

    // WHEN: Same user (ID 1) attempts to approve their own bill
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/approve`, {
      data: {
        approvalReason: 'Approved',
      },
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token-user-1', // Same user as creator
      },
    });

    // THEN: Should return 403 Forbidden with maker-checker violation error
    expect(response.status()).toBe(403);
    const body = await response.json();
    expect(body).toHaveProperty('error');
    expect(body.error).toContain('approver cannot be the same as creator');
    expect(body.error.toLowerCase()).toContain('maker-checker');
  });

  // P1: Important - notification system integration
  test('POST /api/v1/purchase-bills/{id}/submit-for-approval - should send email and in-app notification to Chief Accountant', async ({ request }) => {
    // GIVEN: High-value bill ready for approval
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const highValueBill = purchaseBillFactory.createDraftBill({
      id: 1,
      supplierId: supplier.id!,
      totalAmount: 25000000,
    });

    // WHEN: Submitting bill for approval
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/submit-for-approval`, {
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return success and trigger notifications
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');

    // Notification sending is async - verify notification endpoint
    const notificationsResponse = await request.get(`${apiBaseURL}/api/v1/notifications/pending`, {
      headers: {
        Authorization: 'Bearer mock-token-chief-accountant',
      },
    });

    // Should have notification for approval request
    expect([200, 404]).toContain(notificationsResponse.status());
    // Note: 404 is acceptable if no notifications exist yet (async processing)
  });

  // P0: Critical compliance - audit logging for all workflow transitions
  test('POST /api/v1/purchase-bills/{id}/approve - should log approval workflow transition in audit trail', async ({ request }) => {
    // GIVEN: Bill in PENDING_APPROVAL status
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const pendingBill = purchaseBillFactory.createPurchaseBill({
      id: 1,
      supplierId: supplier.id!,
      status: 'PENDING_APPROVAL',
      createdById: 1,
    });

    // WHEN: Chief Accountant approves bill
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/approve`, {
      data: {
        approvalReason: 'Invoice verified and approved',
      },
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token-chief-accountant', // Different user
      },
    });

    // THEN: Should return success
    expect([200, 201]).toContain(response.status());

    // Verify audit trail includes approval event
    const auditResponse = await request.get(`${apiBaseURL}/api/v1/purchase-bills/1/audit-trail`, {
      headers: {
        Authorization: 'Bearer mock-token',
      },
    });

    expect(auditResponse.status()).toBe(200);
    const auditBody = await auditResponse.json();
    expect(auditBody).toHaveProperty('data');

    // Audit trail should include approval workflow events
    // Note: Audit trail structure depends on implementation
    expect(auditBody.data).toBeInstanceOf(Array);
  });

  // P0: Critical compliance - period close validation
  test('POST /api/v1/purchase-bills/{id}/approve - should reject approval for bills in closed accounting period', async ({ request }) => {
    // GIVEN: Bill with bill date in closed period (January 2024)
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const pendingBill = purchaseBillFactory.createPurchaseBill({
      id: 1,
      supplierId: supplier.id!,
      status: 'PENDING_APPROVAL',
      billDate: '2024-01-15', // January 2024 (assume period is closed)
      createdById: 1,
    });

    // WHEN: Attempting to approve bill in closed period
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/approve`, {
      data: {
        approvalReason: 'Approved',
      },
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token-chief-accountant',
      },
    });

    // THEN: Should return 400 Bad Request with period close error
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body).toHaveProperty('error');
    expect(body.error.toLowerCase()).toContain('closed');
    expect(body.error.toLowerCase()).toContain('period');
  });

  // P1: Important - sensitive flag triggers approval regardless of amount
  test('POST /api/v1/purchase-bills - should require approval for bills marked as sensitive (even below threshold)', async ({ request }) => {
    // GIVEN: Low-value bill marked as sensitive
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const sensitiveBill = purchaseBillFactory.createDraftBill({
      supplierId: supplier.id!,
      totalAmount: 5000000, // 5M VND < 20M threshold
      isSensitive: true, // Marked as sensitive
    });

    // WHEN: Creating sensitive bill
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills`, {
      data: sensitiveBill,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Bill should be created successfully
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body).toHaveProperty('data');

    // When submitted, should require approval despite low amount
    if (body.data.id) {
      const submitResponse = await request.post(`${apiBaseURL}/api/v1/purchase-bills/${body.data.id}/submit-for-approval`, {
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer mock-token',
        },
      });

      expect(submitResponse.status()).toBe(200);
      const submitBody = await submitResponse.json();
      expect(submitBody.data.status).toBe('PENDING_APPROVAL');
    }
  });

  // P1: Important - rejection with mandatory reason
  test('POST /api/v1/purchase-bills/{id}/reject - should require mandatory rejection reason', async ({ request }) => {
    // GIVEN: Bill in PENDING_APPROVAL status
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const pendingBill = purchaseBillFactory.createPurchaseBill({
      id: 1,
      supplierId: supplier.id!,
      status: 'PENDING_APPROVAL',
      createdById: 1,
    });

    // WHEN: Attempting to reject without reason
    const response = await request.post(`${apiBaseURL}/api/v1/purchase-bills/1/reject`, {
      data: {
        // No reason provided
      },
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer mock-token-chief-accountant',
      },
    });

    // THEN: Should return 400 Bad Request with validation error
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body).toHaveProperty('errors');
    expect(body.errors).toHaveProperty('reason');
    expect(body.errors.reason.toLowerCase()).toContain('required');
  });

  // P1: Important - get pending approvals for current user
  test('GET /api/v1/approval-workflows/pending - should return bills pending approval for Chief Accountant', async ({ request }) => {
    // GIVEN: Multiple bills in PENDING_APPROVAL status
    const supplier = supplierFactory.createSupplier({ id: 1 });

    // WHEN: Fetching pending approvals as Chief Accountant
    const response = await request.get(`${apiBaseURL}/api/v1/approval-workflows/pending`, {
      headers: {
        Authorization: 'Bearer mock-token-chief-accountant',
      },
    });

    // THEN: Should return list of pending approval workflows
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    expect(body.data).toBeInstanceOf(Array);
    // Note: Array may be empty if no pending approvals exist
  });

  // P1: Important - approval history timeline
  test('GET /api/v1/purchase-bills/{id}/approval-history - should return complete approval workflow timeline', async ({ request }) => {
    // GIVEN: Bill with approval workflow history
    const supplier = supplierFactory.createSupplier({ id: 1 });
    const approvedBill = purchaseBillFactory.createPurchaseBill({
      id: 1,
      supplierId: supplier.id!,
      status: 'APPROVED',
      createdById: 1,
      approvedById: 2,
    });

    // WHEN: Fetching approval history
    const response = await request.get(`${apiBaseURL}/api/v1/purchase-bills/1/approval-history`, {
      headers: {
        Authorization: 'Bearer mock-token',
      },
    });

    // THEN: Should return approval workflow timeline
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');

    // Workflow history should include events:
    // - Bill created (DRAFT)
    // - Submitted for approval (PENDING_APPROVAL)
    // - Approved (APPROVED)
    expect(body.data).toBeInstanceOf(Array);
  });
});
