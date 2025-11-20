import { test, expect } from '../support/fixtures';

/**
 * Cash Payments API Tests
 * 
 * Tests API contracts and business logic for payment operations:
 * - Payment CRUD operations
 * - FIFO allocation algorithm
 * - Overpayment prevention
 * - Account balance validation
 * - Voucher posting integration
 * - Payment approval workflow
 * 
 * Pattern: Given-When-Then structure, API request validation,
 * one assertion per test
 * 
 * Story: 4.3 (Cash Payments - Linked to Bills, Standalone)
 */
test.describe('Cash Payments API', () => {
  // AC#1: Supplier picker API
  test.describe('4.3-API-001: Get Open Bills for Supplier', () => {
    test('GET /api/v1/ap-payments/suppliers/{supplierId}/open-bills - should return only open/unpaid bills', async ({ 
      request, 
      supplierFactory, 
      purchaseBillFactory 
    }) => {
      // GIVEN: Supplier with open and paid bills exists
      const supplier = supplierFactory.createSupplier();
      const openBill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        status: 'POSTED' // Open (not fully paid)
      });
      const paidBill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        status: 'PAID' // Fully paid
      });

      // WHEN: Requesting open bills for supplier
      const response = await request.get(`/api/v1/ap-payments/suppliers/${supplier.id}/open-bills`);

      // THEN: Only open bills are returned
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data).toHaveLength(1);
      expect(body.data[0].id).toBe(openBill.id);
      expect(body.data[0].status).not.toBe('PAID');
    });
  });

  // AC#2: FIFO allocation algorithm
  test.describe('4.3-API-002: FIFO Allocation Algorithm', () => {
    test('POST /api/v1/ap-payments/{id}/allocate - should allocate payment to bills in FIFO order (oldest due date first)', async ({ 
      request, 
      supplierFactory, 
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Supplier with multiple bills having different due dates
      const supplier = supplierFactory.createSupplier();
      const bill1 = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        dueDate: '2024-01-15', // Oldest
        totalAmount: 1000000,
        status: 'POSTED'
      });
      const bill2 = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        dueDate: '2024-02-15', // Newer
        totalAmount: 2000000,
        status: 'POSTED'
      });

      const payment = paymentFactory.createDraftPayment({
        supplierId: supplier.id!,
        amount: 2500000,
      });

      // WHEN: Requesting FIFO allocation
      const response = await request.post(`/api/v1/ap-payments/${payment.id}/allocate`, {
        data: {
          autoAllocate: true, // Trigger FIFO
        },
      });

      // THEN: Allocation is ordered by due date ASC (oldest first)
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data.allocations).toHaveLength(2);
      expect(body.data.allocations[0].purchaseBillId).toBe(bill1.id);
      expect(body.data.allocations[0].allocationOrder).toBe(1);
      expect(body.data.allocations[1].purchaseBillId).toBe(bill2.id);
      expect(body.data.allocations[1].allocationOrder).toBe(2);
    });
  });

  // AC#4: Overpayment prevention
  test.describe('4.3-API-004: Overpayment Prevention', () => {
    test('POST /api/v1/ap-payments - should reject payment when allocated amount exceeds bill remaining balance', async ({ 
      request, 
      supplierFactory, 
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Bill with remaining balance of 1M VND
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        totalAmount: 1000000,
        status: 'POSTED'
      });

      const payment = paymentFactory.createDraftPayment({
        supplierId: supplier.id!,
        amount: 2000000,
        allocations: [{
          purchaseBillId: bill.id!,
          allocatedAmount: 1500000, // Exceeds remaining balance
          allocationOrder: 1,
        }],
      });

      // WHEN: Attempting to create payment with overpayment
      const response = await request.post('/api/v1/ap-payments', {
        data: payment,
      });

      // THEN: Request is rejected with 409 Conflict
      expect(response.status()).toBe(409);
      const body = await response.json();
      expect(body.error).toContain('overpayment');
      expect(body.error).toContain('exceeds remaining balance');
    });

    test('POST /api/v1/ap-payments/{id}/allocate - should reject manual allocation exceeding bill remaining balance', async ({ 
      request, 
      supplierFactory, 
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Payment and bill with known remaining balance
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        totalAmount: 1000000,
        status: 'POSTED'
      });

      const payment = paymentFactory.createDraftPayment({
        supplierId: supplier.id!,
        amount: 2000000,
      });

      // WHEN: Attempting to allocate more than remaining balance
      const response = await request.post(`/api/v1/ap-payments/${payment.id}/allocate`, {
        data: {
          allocations: [{
            purchaseBillId: bill.id!,
            allocatedAmount: 1500000, // Exceeds remaining balance
          }],
        },
      });

      // THEN: Request is rejected with 409 Conflict
      expect(response.status()).toBe(409);
      const body = await response.json();
      expect(body.error).toContain('overpayment');
    });
  });

  // AC#8: Account balance validation
  test.describe('4.3-API-008: Account Balance Validation', () => {
    test('POST /api/v1/ap-payments - should validate sufficient account balance before creating payment', async ({ 
      request, 
      supplierFactory,
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Account with balance of 1M VND and payment amount of 2M VND
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ supplierId: supplier.id! });

      const payment = paymentFactory.createDraftPayment({
        supplierId: supplier.id!,
        amount: 2000000,
        cashAccountId: 1, // Account with 1M balance
      });

      // WHEN: Attempting to create payment exceeding balance
      const response = await request.post('/api/v1/ap-payments', {
        data: payment,
      });

      // THEN: Request is rejected with 400 Bad Request
      expect(response.status()).toBe(400);
      const body = await response.json();
      expect(body.error).toContain('insufficient balance');
    });

    test('GET /api/v1/accounts/{id}/balance - should return current account balance', async ({ request }) => {
      // GIVEN: Account exists
      // WHEN: Requesting account balance
      const response = await request.get('/api/v1/accounts/1/balance');

      // THEN: Balance is returned
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body).toHaveProperty('balance');
      expect(typeof body.balance).toBe('number');
    });
  });

  // AC#9: Voucher posting integration
  test.describe('4.3-API-009: Voucher Posting Integration', () => {
    test('POST /api/v1/ap-payments/{id}/post - should generate voucher with correct journal entries (Dr AP 331, Cr cash/bank 111/112)', async ({ 
      request, 
      supplierFactory,
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Posted payment with allocations
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        totalAmount: 1000000,
        status: 'POSTED'
      });

      const payment = paymentFactory.createDraftPayment({
        supplierId: supplier.id!,
        amount: 1000000,
        cashAccountId: 111, // Cash account
        allocations: [{
          purchaseBillId: bill.id!,
          allocatedAmount: 1000000,
          allocationOrder: 1,
        }],
      });

      // WHEN: Posting payment
      const response = await request.post(`/api/v1/ap-payments/${payment.id}/post`);

      // THEN: Voucher is created with correct journal entries
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data).toHaveProperty('linkedVoucherId');
      expect(body.data.status).toBe('POSTED');

      // Verify voucher journal entries
      const voucherResponse = await request.get(`/api/v1/vouchers/${body.data.linkedVoucherId}`);
      const voucher = await voucherResponse.json();
      
      // Debit: AP 331 (Accounts Payable)
      const debitEntry = voucher.data.entries.find((e: any) => e.accountCode === '331' && e.debitAmount > 0);
      expect(debitEntry).toBeDefined();
      expect(debitEntry.debitAmount).toBe(1000000);

      // Credit: Cash/Bank 111/112
      const creditEntry = voucher.data.entries.find((e: any) => 
        (e.accountCode === '111' || e.accountCode === '112') && e.creditAmount > 0
      );
      expect(creditEntry).toBeDefined();
      expect(creditEntry.creditAmount).toBe(1000000);
    });
  });

  // AC#7: Payment approval workflow
  test.describe('4.3-API-007: Payment Approval Workflow', () => {
    test('POST /api/v1/ap-payments - should create approval workflow for payments exceeding threshold', async ({ 
      request, 
      supplierFactory,
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Payment amount exceeds approval threshold (20M VND)
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ supplierId: supplier.id! });

      const payment = paymentFactory.createPaymentAboveThreshold(20_000_000, {
        supplierId: supplier.id!,
        amount: 25000000,
      });

      // WHEN: Creating payment above threshold
      const response = await request.post('/api/v1/ap-payments', {
        data: payment,
      });

      // THEN: Payment status is PENDING_APPROVAL
      expect(response.status()).toBe(201);
      const body = await response.json();
      expect(body.data.status).toBe('PENDING_APPROVAL');
      expect(body.data).toHaveProperty('approvalWorkflowId');
    });

    test('POST /api/v1/ap-payments/{id}/approve - should approve payment and post voucher', async ({ 
      request, 
      supplierFactory,
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Payment pending approval
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ supplierId: supplier.id! });

      const payment = paymentFactory.createPaymentAboveThreshold(20_000_000, {
        supplierId: supplier.id!,
        amount: 25000000,
        status: 'PENDING_APPROVAL',
      });

      // WHEN: Approving payment
      const response = await request.post(`/api/v1/ap-payments/${payment.id}/approve`, {
        data: {
          approved: true,
          reason: 'Approved by CFO',
        },
      });

      // THEN: Payment is posted and voucher is generated
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data.status).toBe('POSTED');
      expect(body.data).toHaveProperty('linkedVoucherId');
      expect(body.data.approvedById).toBeDefined();
    });
  });

  // AC#10: Audit trail
  test.describe('4.3-API-010: Audit Trail', () => {
    test('POST /api/v1/ap-payments - should create audit log entry for payment creation', async ({ 
      request, 
      supplierFactory,
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Payment data
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ supplierId: supplier.id! });

      const payment = paymentFactory.createDraftPayment({
        supplierId: supplier.id!,
        amount: 1000000,
      });

      // WHEN: Creating payment
      const response = await request.post('/api/v1/ap-payments', {
        data: payment,
      });

      // THEN: Audit log entry is created
      expect(response.status()).toBe(201);
      const body = await response.json();
      
      // Verify audit log exists
      const auditLogResponse = await request.get(`/api/v1/audit-logs?entityType=APPayment&entityId=${body.data.id}`);
      const auditLogs = await auditLogResponse.json();
      expect(auditLogs.data.length).toBeGreaterThan(0);
      expect(auditLogs.data[0].action).toBe('CREATE');
    });
  });
});


