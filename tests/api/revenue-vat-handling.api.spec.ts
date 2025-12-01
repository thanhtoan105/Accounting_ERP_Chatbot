import { test, expect } from '@playwright/test';

/**
 * Epic 5 - Story 5.6: Revenue & VAT Handling - API Tests
 * 
 * Tests for:
 * - VAT rate validation and override warnings
 * - GL split generation on invoice post
 * - VAT totals validation and rounding
 * - Credit note creation with inverted GL splits
 * - Output VAT report generation and export
 * - VAT correction workflow and approval
 * - Idempotent posting enforcement
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

test.describe('Story 5.6: Revenue & VAT Handling - API Tests', () => {
  let authToken: string;
  let companyId: string;

  // Helper to add required headers
  const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': companyId || '1',
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
    companyId = loginBody.companyId || '1';
  });

  test.describe('AC-VAT-001: VAT Rate Override Warning', () => {
    test('POST /invoices - should return warning when VAT rate overrides default', async ({ request }) => {
      // GIVEN: Invoice with VAT rate override (company default is 10%, line uses 5%)
      const invoiceData = {
        customerId: 'customer-001',
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Service',
            quantity: 1,
            unitPrice: 1000000,
            vatRate: 5, // Override from default 10%
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating invoice via API
      const response = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: Invoice is created with warning in response
      expect(response.status()).toBe(201);
      const body = await response.json();

      expect(body.warnings).toBeDefined();
      expect(body.warnings.length).toBeGreaterThan(0);
      const vatWarning = body.warnings.find((w: any) => w.type === 'VAT_RATE_OVERRIDE');
      expect(vatWarning).toBeDefined();
      expect(vatWarning.message).toContain('overriding the default VAT rate from 10% to 5%');
      expect(vatWarning.message).toContain('Ensure this is correct per customer agreement');
    });

    test('POST /invoices - should validate VAT rate is one of 0/5/10/exempt', async ({ request }) => {
      // GIVEN: Invoice with invalid VAT rate
      const invoiceData = {
        customerId: 'customer-001',
        date: '2025-01-15',
        dueDate: '2025-02-15',
        lineItems: [
          {
            description: 'Service',
            quantity: 1,
            unitPrice: 1000000,
            vatRate: 15, // Invalid rate
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating invoice with invalid VAT rate
      const response = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: invoiceData,
      });

      // THEN: Request is rejected with 400 Bad Request
      expect(response.status()).toBe(400);
      const errorBody = await response.json();
      expect(errorBody.error).toContain('VAT rate');
      expect(errorBody.error).toContain('0, 5, 10, or exempt');
    });
  });

  test.describe('AC-VAT-002: GL Split on Post', () => {
    test('POST /sales-invoices/{id}/post - should create GL splits: Dr 131, Cr 5xx, Cr 3331', async ({ request }) => {
      // GIVEN: Draft invoice exists
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
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
      const postResponse = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      // THEN: Invoice is posted successfully
      expect(postResponse.status()).toBe(200);
      const postedInvoice = await postResponse.json();
      expect(postedInvoice.status).toBe('POSTED');

      // THEN: GL entries are created with correct splits
      const glResponse = await request.get(
        `${API_BASE}/vouchers?documentId=${invoice.id}&documentType=SALES_INVOICE`,
        {
          headers: getHeaders(),
        }
      );

      expect(glResponse.status()).toBe(200);
      const voucher = await glResponse.json();
      expect(voucher.lines).toBeDefined();
      expect(voucher.lines.length).toBe(3);

      // Verify GL splits
      const arEntry = voucher.lines.find((l: any) => l.accountCode === '131');
      expect(arEntry).toBeDefined();
      expect(arEntry.debit).toBe(1100000); // AR = subtotal + VAT

      const revenueEntry = voucher.lines.find((l: any) => l.accountCode === '511001');
      expect(revenueEntry).toBeDefined();
      expect(revenueEntry.credit).toBe(1000000); // Revenue = line total

      const vatEntry = voucher.lines.find((l: any) => l.accountCode === '3331');
      expect(vatEntry).toBeDefined();
      expect(vatEntry.credit).toBe(100000); // Output VAT

      // Verify GL balance: Dr = Cr
      const totalDebit = voucher.lines.reduce((sum: number, l: any) => sum + (l.debit || 0), 0);
      const totalCredit = voucher.lines.reduce((sum: number, l: any) => sum + (l.credit || 0), 0);
      expect(totalDebit).toBe(totalCredit);
    });

    test('POST /sales-invoices/{id}/post - should handle multiple line items with different revenue accounts', async ({
      request,
    }) => {
      // GIVEN: Invoice with multiple line items
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service A',
              quantity: 1,
              unitPrice: 500000,
              vatRate: 10,
              revenueAccount: '511001',
            },
            {
              description: 'Service B',
              quantity: 1,
              unitPrice: 300000,
              vatRate: 10,
              revenueAccount: '511002',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Posting invoice
      const postResponse = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      expect(postResponse.status()).toBe(200);

      // THEN: GL entries created for each revenue account
      const glResponse = await request.get(
        `${API_BASE}/vouchers?documentId=${invoice.id}&documentType=SALES_INVOICE`,
        {
          headers: getHeaders(),
        }
      );

      const voucher = await glResponse.json();

      // AR entry (single debit)
      const arEntries = voucher.lines.filter((l: any) => l.accountCode === '131');
      expect(arEntries.length).toBe(1);
      expect(arEntries[0].debit).toBe(880000); // (500000 + 300000) * 1.1

      // Revenue entries (one per line)
      const revenue511001 = voucher.lines.find((l: any) => l.accountCode === '511001');
      expect(revenue511001).toBeDefined();
      expect(revenue511001.credit).toBe(500000);

      const revenue511002 = voucher.lines.find((l: any) => l.accountCode === '511002');
      expect(revenue511002).toBeDefined();
      expect(revenue511002.credit).toBe(300000);

      // VAT entry (single credit)
      const vatEntry = voucher.lines.find((l: any) => l.accountCode === '3331');
      expect(vatEntry).toBeDefined();
      expect(vatEntry.credit).toBe(80000); // (500000 + 300000) * 0.1
    });
  });

  test.describe('AC-VAT-003: VAT Totals Validation', () => {
    test('POST /sales-invoices/{id}/post - should block post if VAT variance ≥ 1000 VND', async ({ request }) => {
      // GIVEN: Invoice with VAT mismatch (variance ≥ 1000 VND)
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
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
          headerVAT: 20000, // Intentional mismatch (should be 10,000)
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Attempting to post invoice
      const postResponse = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      // THEN: Post is blocked with 400 Bad Request
      expect(postResponse.status()).toBe(400);
      const errorBody = await postResponse.json();
      expect(errorBody.error).toContain('VAT rounding variance');
      expect(errorBody.error).toContain('10000 VND');
      expect(errorBody.details.variance).toBeGreaterThanOrEqual(1000);
    });

    test('POST /sales-invoices/{id}/post - should allow post if VAT variance < 1000 VND', async ({ request }) => {
      // GIVEN: Invoice with small VAT variance (< 1000 VND)
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
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
          headerVAT: 10050, // Small variance: 50 VND
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // WHEN: Posting invoice
      const postResponse = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      // THEN: Post succeeds (with warning in response)
      expect(postResponse.status()).toBe(200);
      const postedInvoice = await postResponse.json();
      expect(postedInvoice.status).toBe('POSTED');

      // Warning should be present but not blocking
      if (postedInvoice.warnings) {
        const vatWarning = postedInvoice.warnings.find((w: any) => w.type === 'VAT_VARIANCE');
        expect(vatWarning).toBeDefined();
        expect(vatWarning.message).toContain('VAT rounding variance: 50 VND');
      }
    });

    test('POST /sales-invoices - should round VAT to nearest 100 VND per Circular 200', async ({ request }) => {
      // GIVEN: Invoice with amount that requires rounding
      // 333,333 * 10% = 33,333.30 → should round to 33,300
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 333333,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createResponse.status()).toBe(201);
      const invoice = await createResponse.json();

      // THEN: VAT is rounded to nearest 100 VND
      // 33,333.30 → rounds to 33,300 (nearest 100)
      expect(invoice.totalVAT).toBe(33300);
    });
  });

  test.describe('AC-VAT-004: Credit Note Support', () => {
    test('POST /sales-invoices/credit-notes - should create credit note with inverted GL splits', async ({
      request,
    }) => {
      // GIVEN: Posted invoice exists
      const createInvoiceResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
              quantity: 1,
              unitPrice: 1000000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      expect(createInvoiceResponse.status()).toBe(201);
      const originalInvoice = await createInvoiceResponse.json();

      // Post the invoice
      await request.post(`${API_BASE}/sales-invoices/${originalInvoice.id}/post`, {
        headers: getHeaders(),
      });

      // WHEN: Creating credit note
      const creditNoteResponse = await request.post(`${API_BASE}/sales-invoices/credit-notes`, {
        headers: getHeaders(),
        data: {
          originalInvoiceId: originalInvoice.id,
          date: '2025-01-20',
          lineItems: [
            {
              description: 'Service (Credit)',
              quantity: 1,
              unitPrice: 1000000,
              vatRate: 10,
              revenueAccount: '511001',
            },
          ],
        },
      });

      // THEN: Credit note is created
      expect(creditNoteResponse.status()).toBe(201);
      const creditNote = await creditNoteResponse.json();
      expect(creditNote.originalInvoiceId).toBe(originalInvoice.id);
      expect(creditNote.totalVAT).toBeLessThan(0); // Negative VAT

      // WHEN: Posting credit note
      const postCreditNoteResponse = await request.post(
        `${API_BASE}/sales-invoices/${creditNote.id}/post`,
        {
          headers: getHeaders(),
        }
      );

      expect(postCreditNoteResponse.status()).toBe(200);

      // THEN: GL splits are inverted (Cr 131, Dr 5xx, Dr 3331)
      const glResponse = await request.get(
        `${API_BASE}/vouchers?documentId=${creditNote.id}&documentType=CREDIT_NOTE`,
        {
          headers: getHeaders(),
        }
      );

      const voucher = await glResponse.json();
      const arEntry = voucher.lines.find((l: any) => l.accountCode === '131');
      expect(arEntry).toBeDefined();
      expect(arEntry.credit).toBe(1100000); // Inverted: Cr instead of Dr

      const revenueEntry = voucher.lines.find((l: any) => l.accountCode === '511001');
      expect(revenueEntry).toBeDefined();
      expect(revenueEntry.debit).toBe(1000000); // Inverted: Dr instead of Cr

      const vatEntry = voucher.lines.find((l: any) => l.accountCode === '3331');
      expect(vatEntry).toBeDefined();
      expect(vatEntry.debit).toBe(100000); // Inverted: Dr instead of Cr
    });

    test('POST /sales-invoices/credit-notes - should require original invoice reference', async ({ request }) => {
      // GIVEN: Credit note data without original invoice ID
      const creditNoteData = {
        date: '2025-01-20',
        lineItems: [
          {
            description: 'Service (Credit)',
            quantity: 1,
            unitPrice: 1000000,
            vatRate: 10,
            revenueAccount: '511001',
          },
        ],
      };

      // WHEN: Creating credit note without original invoice
      const response = await request.post(`${API_BASE}/sales-invoices/credit-notes`, {
        headers: getHeaders(),
        data: creditNoteData,
      });

      // THEN: Request is rejected with 400 Bad Request
      expect(response.status()).toBe(400);
      const errorBody = await response.json();
      expect(errorBody.error).toContain('originalInvoiceId');
    });
  });

  test.describe('AC-VAT-005: Output VAT Report', () => {
    test('GET /ar-vat-report - should generate report with period filter', async ({ request }) => {
      // GIVEN: Posted invoices exist for period 2025-01
      // (Pre-created in test data setup)

      // WHEN: Fetching VAT report for period
      const response = await request.get(`${API_BASE}/ar-vat-report?period=2025-01`, {
        headers: getHeaders(),
      });

      // THEN: Report data is returned
      expect(response.status()).toBe(200);
      const report = await response.json();

      expect(report.data).toBeDefined();
      expect(Array.isArray(report.data)).toBe(true);

      // Verify report structure
      if (report.data.length > 0) {
        const firstRow = report.data[0];
        expect(firstRow).toHaveProperty('invoiceNumber');
        expect(firstRow).toHaveProperty('invoiceDate');
        expect(firstRow).toHaveProperty('customerName');
        expect(firstRow).toHaveProperty('customerTaxCode');
        expect(firstRow).toHaveProperty('revenue0pct');
        expect(firstRow).toHaveProperty('revenue5pct');
        expect(firstRow).toHaveProperty('revenue10pct');
        expect(firstRow).toHaveProperty('revenueExempt');
        expect(firstRow).toHaveProperty('total_vat_collected');
      }
    });

    test('GET /ar-vat-report/export - should export report to Excel in ND123 format', async ({ request }) => {
      // GIVEN: VAT report data exists
      // (Pre-created in test data setup)

      // WHEN: Exporting report to Excel
      const response = await request.get(`${API_BASE}/ar-vat-report/export?period=2025-01&format=EXCEL`, {
        headers: getHeaders(),
      });

      // THEN: Excel file is returned
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      expect(response.headers()['content-disposition']).toContain('.xlsx');

      // Verify file is not empty
      const buffer = await response.body();
      expect(buffer.length).toBeGreaterThan(0);
    });
  });

  test.describe('AC-VAT-006: VAT Correction Workflow', () => {
    test('POST /ar-vat-corrections - should create correction with reason', async ({ request }) => {
      // GIVEN: Posted invoice exists
      // (Pre-created in test data setup - invoice ID: INV-001)

      // WHEN: Creating VAT correction
      const correctionResponse = await request.post(`${API_BASE}/ar-vat-corrections`, {
        headers: getHeaders(),
        data: {
          invoiceId: 'INV-001',
          oldVATAmount: 100000,
          newVATAmount: 150000,
          reason: 'Customer agreement specifies different VAT rate',
        },
      });

      // THEN: Correction is created
      expect(correctionResponse.status()).toBe(201);
      const correction = await correctionResponse.json();

      expect(correction.invoiceId).toBe('INV-001');
      expect(correction.oldVATAmount).toBe(100000);
      expect(correction.newVATAmount).toBe(150000);
      expect(correction.variance).toBe(50000);
      expect(correction.reason).toBe('Customer agreement specifies different VAT rate');
      expect(correction.status).toBe('PENDING');
    });

    test('POST /ar-vat-corrections - should require approval if variance > threshold', async ({ request }) => {
      // GIVEN: Correction with variance > 10M VND threshold
      const correctionResponse = await request.post(`${API_BASE}/ar-vat-corrections`, {
        headers: getHeaders(),
        data: {
          invoiceId: 'INV-001',
          oldVATAmount: 1000000,
          newVATAmount: 11000001, // Variance: 10,000,001 VND > threshold
          reason: 'Large correction per customer agreement',
        },
      });

      expect(correctionResponse.status()).toBe(201);
      const correction = await correctionResponse.json();

      // THEN: Correction status is PENDING and requires approval
      expect(correction.status).toBe('PENDING');
      expect(correction.requiresApproval).toBe(true);
    });

    test('POST /ar-vat-corrections/{id}/approve - should approve and apply correction', async ({ request }) => {
      // GIVEN: Pending correction exists
      // (Pre-created in test data setup - correction ID: CORR-001)

      // WHEN: Chief Accountant approves correction
      const approveResponse = await request.post(`${API_BASE}/ar-vat-corrections/CORR-001/approve`, {
        headers: getHeaders(),
        data: {
          approverNotes: 'Approved per customer agreement',
        },
      });

      // THEN: Correction is approved
      expect(approveResponse.status()).toBe(200);
      const approvedCorrection = await approveResponse.json();

      expect(approvedCorrection.status).toBe('APPROVED');
      expect(approvedCorrection.approvedBy).toBeTruthy();
      expect(approvedCorrection.approvedAt).toBeTruthy();

      // THEN: Invoice VAT amount is updated
      const invoiceResponse = await request.get(`${API_BASE}/sales-invoices/${approvedCorrection.invoiceId}`, {
        headers: getHeaders(),
      });

      const invoice = await invoiceResponse.json();
      expect(invoice.totalVAT).toBe(approvedCorrection.newVATAmount);
    });
  });

  test.describe('AC-VAT-007: Idempotent Posting', () => {
    test('POST /sales-invoices/{id}/post - should be idempotent (no duplicate vouchers)', async ({ request }) => {
      // GIVEN: Draft invoice exists
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
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

      // WHEN: Posting invoice first time
      const postResponse1 = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      expect(postResponse1.status()).toBe(200);
      const postedInvoice1 = await postResponse1.json();
      const voucherId1 = postedInvoice1.voucherId;

      // WHEN: Posting invoice second time (idempotent call)
      const postResponse2 = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      // THEN: Second post returns same result (idempotent)
      expect(postResponse2.status()).toBe(200);
      const postedInvoice2 = await postResponse2.json();
      expect(postedInvoice2.voucherId).toBe(voucherId1); // Same voucher ID

      // THEN: Only one voucher exists for this invoice
      const vouchersResponse = await request.get(
        `${API_BASE}/vouchers?documentId=${invoice.id}&documentType=SALES_INVOICE`,
        {
          headers: getHeaders(),
        }
      );

      const vouchers = await vouchersResponse.json();
      expect(vouchers.length).toBe(1); // No duplicate vouchers
    });

    test('POST /sales-invoices/{id}/post - should prevent double-booking via unique constraint', async ({
      request,
    }) => {
      // GIVEN: Posted invoice exists
      const createResponse = await request.post(`${API_BASE}/sales-invoices`, {
        headers: getHeaders(),
        data: {
          customerId: 'customer-001',
          date: '2025-01-15',
          dueDate: '2025-02-15',
          lineItems: [
            {
              description: 'Service',
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

      // Post it
      await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      // WHEN: Attempting to post again (simulating race condition)
      const postResponse2 = await request.post(`${API_BASE}/sales-invoices/${invoice.id}/post`, {
        headers: getHeaders(),
      });

      // THEN: System prevents double-booking (idempotent or error)
      // Either returns 200 with same voucher (idempotent) or 400 with error
      expect([200, 400]).toContain(postResponse2.status());

      if (postResponse2.status() === 400) {
        const errorBody = await postResponse2.json();
        expect(errorBody.error).toContain('already posted');
      }
    });
  });
});

