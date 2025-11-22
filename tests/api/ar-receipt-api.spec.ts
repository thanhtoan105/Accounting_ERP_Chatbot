import { test, expect } from '@playwright/test';

/**
 * Epic 5 - Story 5.3: Customer Payment Receipts - API Tests
 * 
 * Tests for:
 * - Receipt form validation (customer, amount, account, date)
 * - Invoice allocation (single, multiple, partial, overpayment prevention)
 * - Standalone receipts (advance payments, flagging)
 * - GL posting (Dr Bank/Cash 111/112, Cr AR 131, dimensions)
 * - Receipt reversal (linked voucher, audit trail)
 * - Batch import (atomic, error handling)
 * - Full audit logging
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

// Test data
const testCustomer = {
  id: 'customer-001',
  name: 'Test Customer AR',
};

const testBankAccount = {
  id: 'bank-001',
  accountCode: '111',
  name: 'Test Bank Account',
  balance: 50000000, // VND
};

const testARAccount = {
  id: 'ar-001',
  accountCode: '131',
  name: 'Accounts Receivable',
};

const testInvoice = {
  id: 'invoice-001',
  number: 'INV-001',
  customerId: testCustomer.id,
  amount: 10000000,
  remainingBalance: 10000000,
  status: 'POSTED',
};

test.describe('Story 5.3: Customer Payment Receipts - API Tests', () => {
  let authToken: string;

  test.beforeEach(async ({ request }) => {
    // Setup: Get auth token for accountant
    const loginResponse = await request.post(`${API_BASE}/auth/login`, {
      data: {
        email: 'accountant@test.example.com',
        password: 'Test@123456',
      },
    });
    expect(loginResponse.status()).toBe(200);
    const loginBody = await loginResponse.json();
    authToken = loginBody.token;
  });

  // ===== AC1: Receipt Form Validation =====
  test.describe('AC1: Receipt Form Fields', () => {
    test('AC1.1: POST /receipts - should create receipt with required fields', async ({ request }) => {
      // GIVEN: Valid receipt data with customer, date, account, amount
      const receiptData = {
        customerId: testCustomer.id,
        receiptDate: '2025-01-15',
        bankAccountId: testBankAccount.id,
        amount: 5000000,
        reference: 'REC-TEST-001',
        paymentMethod: 'BANK_TRANSFER',
      };

      // WHEN: Creating receipt via API
      const response = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: receiptData,
      });

      // THEN: Receipt is created with auto-generated number
      expect(response.status()).toBe(201);
      const body = await response.json();
      expect(body.data).toMatchObject({
        customerId: testCustomer.id,
        amount: 5000000,
        status: 'DRAFT',
      });
      expect(body.data.receiptNumber).toBeTruthy();
      expect(body.data.receiptNumber).toMatch(/\d{4}\/\d+/); // Format: YYYY/#### (auto-generated)
    });

    test('AC1.2: POST /receipts - should validate customer has open invoices', async ({ request }) => {
      // GIVEN: Customer with NO open invoices
      const receiptData = {
        customerId: 'customer-no-invoices',
        receiptDate: '2025-01-15',
        bankAccountId: testBankAccount.id,
        amount: 5000000,
        reference: 'REC-TEST-002',
        paymentMethod: 'BANK_TRANSFER',
      };

      // WHEN: Creating receipt for customer without open invoices
      const response = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: receiptData,
      });

      // THEN: Request fails with validation error (unless standalone)
      expect(response.status()).toBe(400);
      const body = await response.json();
      expect(body.errors.customerId).toContain('has no open invoices');
    });

    test('AC1.3: POST /receipts - should auto-generate receipt number per customer/period', async ({ request }) => {
      // GIVEN: Two receipts for same customer in same period
      const receiptData1 = {
        customerId: testCustomer.id,
        receiptDate: '2025-01-15',
        bankAccountId: testBankAccount.id,
        amount: 1000000,
        reference: 'REC-001',
        paymentMethod: 'CASH',
      };

      // WHEN: Creating first receipt
      const response1 = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: receiptData1,
      });
      expect(response1.status()).toBe(201);
      const body1 = await response1.json();
      const number1 = body1.data.receiptNumber;

      // Create second receipt
      const receiptData2 = { ...receiptData1, amount: 2000000 };
      const response2 = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: receiptData2,
      });
      expect(response2.status()).toBe(201);
      const body2 = await response2.json();
      const number2 = body2.data.receiptNumber;

      // THEN: Receipt numbers are unique per customer/period
      expect(number1).not.toBe(number2);
      expect(number1).toMatch(/\d{4}\/\d+/);
      expect(number2).toMatch(/\d{4}\/\d+/);
    });
  });

  // ===== AC2: Invoice Allocation =====
  test.describe('AC2: Invoice Allocation (Single, Multiple, Partial)', () => {
    test('AC2.1: POST /receipts/{id}/allocate - should allocate to single invoice', async ({ request }) => {
      // GIVEN: Receipt in DRAFT status with open invoice
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 5000000,
          reference: 'REC-TEST-003',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // WHEN: Allocating receipt to invoice
      const allocationResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            {
              salesInvoiceId: testInvoice.id,
              allocatedAmount: 5000000,
            },
          ],
        },
      });

      // THEN: Allocation succeeds, remaining balance updated
      expect(allocationResponse.status()).toBe(200);
      const body = await allocationResponse.json();
      expect(body.data.allocations).toHaveLength(1);
      expect(body.data.allocations[0]).toMatchObject({
        salesInvoiceId: testInvoice.id,
        allocatedAmount: 5000000,
      });
    });

    test('AC2.2: POST /receipts/{id}/allocate - should allocate to multiple invoices', async ({ request }) => {
      // GIVEN: Receipt with amount that covers multiple invoices
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 15000000, // Covers multiple invoices
          reference: 'REC-TEST-004',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // WHEN: Allocating to multiple invoices
      const allocationResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: 'invoice-001', allocatedAmount: 10000000 },
            { salesInvoiceId: 'invoice-002', allocatedAmount: 5000000 },
          ],
        },
      });

      // THEN: Multiple allocations recorded
      expect(allocationResponse.status()).toBe(200);
      const body = await allocationResponse.json();
      expect(body.data.allocations).toHaveLength(2);
    });

    test('AC2.3: POST /receipts/{id}/allocate - should prevent overpayment', async ({ request }) => {
      // GIVEN: Receipt attempting to allocate more than invoice balance
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 15000000,
          reference: 'REC-TEST-005',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // WHEN: Attempting to allocate more than remaining balance
      const allocationResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 20000000 }, // Exceeds remaining
          ],
        },
      });

      // THEN: Allocation fails with overpayment error
      expect(allocationResponse.status()).toBe(409); // Conflict
      const body = await allocationResponse.json();
      expect(body.error).toContain('overpayment');
    });

    test('AC2.4: POST /receipts/{id}/allocate - should support partial allocation', async ({ request }) => {
      // GIVEN: Receipt with partial amount for invoice
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 6000000,
          reference: 'REC-TEST-006',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // WHEN: Allocating partial amount to invoice
      const allocationResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 6000000 },
          ],
        },
      });

      // THEN: Partial allocation recorded, invoice status = PARTIALLY_PAID
      expect(allocationResponse.status()).toBe(200);
      const body = await allocationResponse.json();
      expect(body.data.allocations[0].allocatedAmount).toBe(6000000);
    });
  });

  // ===== AC3: Standalone Receipts =====
  test.describe('AC3: Standalone Receipts (Advances, On-Account)', () => {
    test('AC3.1: POST /receipts - should create standalone receipt (admin-only)', async ({ request }) => {
      // GIVEN: Admin user creating standalone receipt
      const receiptData = {
        customerId: testCustomer.id,
        receiptDate: '2025-01-15',
        bankAccountId: testBankAccount.id,
        amount: 5000000,
        reference: 'ADVANCE-001',
        paymentMethod: 'BANK_TRANSFER',
        isStandalone: true,
      };

      // WHEN: Admin creates standalone receipt
      const response = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: receiptData,
      });

      // THEN: Standalone receipt created, flagged as advance
      expect(response.status()).toBe(201);
      const body = await response.json();
      expect(body.data.isStandalone).toBe(true);
      expect(body.data.status).toBe('DRAFT');
    });

    test('AC3.2: POST /receipts - should reject standalone receipt for non-admin', async ({ request }) => {
      // GIVEN: Non-admin user
      const receiptData = {
        customerId: testCustomer.id,
        receiptDate: '2025-01-15',
        bankAccountId: testBankAccount.id,
        amount: 5000000,
        reference: 'ADVANCE-002',
        paymentMethod: 'BANK_TRANSFER',
        isStandalone: true,
      };

      // WHEN: Non-admin attempts standalone receipt
      const response = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` }, // Accountant token
        data: receiptData,
      });

      // THEN: Forbidden
      expect(response.status()).toBe(403);
    });
  });

  // ===== AC4-5: GL Posting with Dimensions =====
  test.describe('AC4-5: GL Posting (Dr Bank/Cash, Cr AR, Dimensions)', () => {
    test('AC4-5.1: POST /receipts/{id}/post - should generate GL voucher', async ({ request }) => {
      // GIVEN: Receipt with allocations in DRAFT status
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 5000000,
          reference: 'REC-TEST-007',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // Allocate to invoice first
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 5000000 },
          ],
        },
      });

      // WHEN: Posting receipt
      const postResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/post`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      // THEN: GL voucher generated (Dr 111/112, Cr 131)
      expect(postResponse.status()).toBe(200);
      const body = await postResponse.json();
      expect(body.data.status).toBe('POSTED');
      expect(body.data.linkedVoucherId).toBeTruthy();

      // Verify voucher has correct GL lines
      const voucherResponse = await request.get(
        `${API_BASE}/vouchers/${body.data.linkedVoucherId}`,
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      const voucher = await voucherResponse.json();
      expect(voucher.data.lines).toContainEqual(
        expect.objectContaining({
          accountCode: '111', // or 112 for cash
          amount: 5000000,
          type: 'DEBIT',
        })
      );
      expect(voucher.data.lines).toContainEqual(
        expect.objectContaining({
          accountCode: '131',
          amount: 5000000,
          type: 'CREDIT',
        })
      );
    });

    test('AC4-5.2: POST /receipts/{id}/post - should include dimensions in GL lines', async ({ request }) => {
      // GIVEN: Receipt for customer with cost center dimension
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 5000000,
          reference: 'REC-TEST-008',
          paymentMethod: 'BANK_TRANSFER',
          costCenterId: 'cc-001',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // Allocate and post
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 5000000 },
          ],
        },
      });

      const postResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/post`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      expect(postResponse.status()).toBe(200);
      const body = await postResponse.json();

      // Verify voucher dimensions included
      const voucherResponse = await request.get(
        `${API_BASE}/vouchers/${body.data.linkedVoucherId}`,
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      const voucher = await voucherResponse.json();
      expect(voucher.data.lines[0].dimensions).toEqual(
        expect.objectContaining({ costCenter: 'cc-001' })
      );
    });
  });

  // ===== AC6-7: Receipt Reversal =====
  test.describe('AC6-7: Receipt Reversal (Linked Voucher, Mandatory Reason)', () => {
    test('AC6-7.1: POST /receipts/{id}/reverse - should generate linked reversal voucher', async ({ request }) => {
      // GIVEN: Posted receipt
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 5000000,
          reference: 'REC-TEST-009',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // Allocate and post
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 5000000 },
          ],
        },
      });

      const postResp = await request.post(`${API_BASE}/ar/receipts/${receiptId}/post`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });
      const originalVoucherId = (await postResp.json()).data.linkedVoucherId;

      // WHEN: Reversing receipt with mandatory reason
      const reverseResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/reverse`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: { reversalReason: 'Customer requested refund' },
      });

      // THEN: Linked reversal voucher created, audit trail maintained
      expect(reverseResponse.status()).toBe(200);
      const body = await reverseResponse.json();
      expect(body.data.status).toBe('REVERSED');
      expect(body.data.reversalVoucherId).toBeTruthy();
      expect(body.data.reversalVoucherId).not.toBe(originalVoucherId);

      // Verify reversal voucher exists and is linked
      const voucherResponse = await request.get(
        `${API_BASE}/vouchers/${body.data.reversalVoucherId}`,
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      const voucher = await voucherResponse.json();
      expect(voucher.data.linkedVoucherId).toBe(originalVoucherId);
    });

    test('AC6-7.2: POST /receipts/{id}/reverse - should require reversal reason', async ({ request }) => {
      // GIVEN: Posted receipt
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 5000000,
          reference: 'REC-TEST-010',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // Post receipt
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 5000000 },
          ],
        },
      });
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/post`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      // WHEN: Reversing without reason
      const reverseResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/reverse`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: { reversalReason: '' }, // Empty reason
      });

      // THEN: Request fails - reason is mandatory
      expect(reverseResponse.status()).toBe(400);
      const body = await reverseResponse.json();
      expect(body.errors.reversalReason).toContain('mandatory');
    });
  });

  // ===== AC8-9: Batch Import =====
  test.describe('AC8-9: Batch Import (Atomic, Error Handling)', () => {
    test('AC8-9.1: POST /receipts/batch-import - should import receipts atomically', async ({ request }) => {
      // GIVEN: Valid Excel file with receipts
      const excelFile = Buffer.from([
        // This is a placeholder - in real test use actual Excel file
        // Columns: customer_id, receipt_date, amount, invoice_id
      ]);

      // TODO: Implement actual Excel file generation for batch import test
      // WHEN: Uploading batch file
      // THEN: All receipts imported or none if any error
    });

    test('AC8-9.2: POST /receipts/batch-import - should return detailed error map', async ({ request }) => {
      // GIVEN: Excel file with validation errors
      // TODO: Implement Excel file with errors

      // WHEN: Uploading batch file with errors
      // THEN: Error response contains row numbers and failure reasons
    });
  });

  // ===== AC10-11: Audit Logging =====
  test.describe('AC10-11: Audit Logging', () => {
    test('AC10.1: POST /receipts - should audit log receipt creation', async ({ request }) => {
      // GIVEN: Creating receipt
      const receiptData = {
        customerId: testCustomer.id,
        receiptDate: '2025-01-15',
        bankAccountId: testBankAccount.id,
        amount: 5000000,
        reference: 'REC-TEST-011',
        paymentMethod: 'BANK_TRANSFER',
      };

      // WHEN: Creating receipt
      const response = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: receiptData,
      });

      expect(response.status()).toBe(201);
      const receiptId = (await response.json()).data.id;

      // THEN: Audit log created with user, timestamp, old/new values
      const auditResponse = await request.get(
        `${API_BASE}/audit-logs?entityId=${receiptId}&action=CREATE`,
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      expect(auditResponse.status()).toBe(200);
      const auditBody = await auditResponse.json();
      expect(auditBody.data).toHaveLength(1);
      expect(auditBody.data[0]).toMatchObject({
        entityType: 'ARPayment',
        action: 'CREATE',
        userId: expect.any(String),
        timestamp: expect.any(String),
      });
    });

    test('AC11.1: POST /receipts/{id}/reverse - should audit log reversal with reason', async ({ request }) => {
      // GIVEN: Posted receipt
      const receiptCreateResp = await request.post(`${API_BASE}/ar/receipts`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          customerId: testCustomer.id,
          receiptDate: '2025-01-15',
          bankAccountId: testBankAccount.id,
          amount: 5000000,
          reference: 'REC-TEST-012',
          paymentMethod: 'BANK_TRANSFER',
        },
      });
      const receiptId = (await receiptCreateResp.json()).data.id;

      // Allocate and post
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/allocate`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          allocations: [
            { salesInvoiceId: testInvoice.id, allocatedAmount: 5000000 },
          ],
        },
      });
      await request.post(`${API_BASE}/ar/receipts/${receiptId}/post`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      // WHEN: Reversing with reason
      const reverseResponse = await request.post(`${API_BASE}/ar/receipts/${receiptId}/reverse`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: { reversalReason: 'Duplicate payment received' },
      });

      expect(reverseResponse.status()).toBe(200);

      // THEN: Audit log includes reversal reason and before/after state
      const auditResponse = await request.get(
        `${API_BASE}/audit-logs?entityId=${receiptId}&action=REVERSE`,
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      expect(auditResponse.status()).toBe(200);
      const auditBody = await auditResponse.json();
      expect(auditBody.data[0]).toMatchObject({
        action: 'REVERSE',
        changes: expect.objectContaining({
          reversalReason: 'Duplicate payment received',
        }),
      });
    });
  });
});
