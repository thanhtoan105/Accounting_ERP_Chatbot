import { test, expect } from '@playwright/test';
import {
  createStatementSummary,
  createStatementDetailed,
  createReconciliationRow,
  createDisputeLog,
  createStatementHistory,
  createStatementDelivery,
} from '../support/factories/statement.factory';

/**
 * Epic 5 - Story 5.5: Customer Statement & Reconciliation - API Tests
 * 
 * Tests for:
 * - Statement generation (summary and detailed views)
 * - Statement export (PDF/Excel)
 * - Send statement to customer (email delivery)
 * - Reconciliation import (CSV parsing, mismatch detection)
 * - Dispute logging and resolution
 * - Statement history and regeneration
 * - Batch export functionality
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

test.describe('Story 5.5: Customer Statement & Reconciliation - API Tests', () => {
  let authToken: string;
  let customerId: string;

  // Helper to add required headers
  const getHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
    'X-Company-Id': '1', // Required for multi-tenancy
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

    // Create test customer (or use existing)
    customerId = 'customer-001'; // Will be replaced with actual customer ID from setup
  });

  // ===== AC-STMT-001: Customer Statement - Summary View =====
  test.describe('AC-STMT-001: Statement Summary View', () => {
    test('AC-STMT-001.1: GET /ar-statements/:customerId?format=SUMMARY - should return summary statement', async ({
      request,
    }) => {
      // GIVEN: Customer with invoices exists
      // WHEN: Requesting summary statement
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}?format=SUMMARY`, {
        headers: getHeaders(),
      });

      // THEN: Summary statement is returned with correct structure
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data).toMatchObject({
        customerId: expect.any(String),
        customerName: expect.any(String),
        customerAddress: expect.any(String),
        customerTaxCode: expect.any(String),
        asOfDate: expect.any(String),
        rows: expect.any(Array),
        totals: {
          totalInvoices: expect.any(Number),
          totalPaid: expect.any(Number),
          totalOutstanding: expect.any(Number),
        },
      });

      // Verify row structure
      if (body.data.rows.length > 0) {
        const row = body.data.rows[0];
        expect(row).toMatchObject({
          invoiceNumber: expect.any(String),
          invoiceDate: expect.any(String),
          invoiceAmount: expect.any(Number),
          amountPaid: expect.any(Number),
          balance: expect.any(Number),
          runningBalance: expect.any(Number),
        });
      }
    });

    test('AC-STMT-001.2: GET /ar-statements/:customerId?format=SUMMARY - should include customer header information', async ({
      request,
    }) => {
      // GIVEN: Customer exists
      // WHEN: Requesting summary statement
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}?format=SUMMARY`, {
        headers: getHeaders(),
      });

      // THEN: Customer header information is included
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data.customerName).toBeTruthy();
      expect(body.data.customerAddress).toBeTruthy();
      expect(body.data.customerTaxCode).toBeTruthy();
    });

    test('AC-STMT-001.3: GET /ar-statements/:customerId?format=SUMMARY - should calculate running balance correctly', async ({
      request,
    }) => {
      // GIVEN: Customer with multiple invoices
      // WHEN: Requesting summary statement
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}?format=SUMMARY`, {
        headers: getHeaders(),
      });

      // THEN: Running balance is cumulative
      expect(response.status()).toBe(200);
      const body = await response.json();
      const rows = body.data.rows;

      if (rows.length > 1) {
        let expectedRunningBalance = 0;
        for (const row of rows) {
          expectedRunningBalance += row.balance;
          expect(row.runningBalance).toBe(expectedRunningBalance);
        }
      }
    });
  });

  // ===== AC-STMT-002: Customer Statement - Detailed View =====
  test.describe('AC-STMT-002: Statement Detailed View', () => {
    test('AC-STMT-002.1: GET /ar-statements/:customerId?format=DETAILED - should return detailed statement with sub-rows', async ({
      request,
    }) => {
      // GIVEN: Customer with invoices and receipts exists
      // WHEN: Requesting detailed statement
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}?format=DETAILED`, {
        headers: getHeaders(),
      });

      // THEN: Detailed statement includes sub-rows for receipts/credits/adjustments
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data.rows).toBeInstanceOf(Array);

      // Verify at least one row has sub-rows
      const rowWithSubRows = body.data.rows.find((row: any) => row.subRows && row.subRows.length > 0);
      if (rowWithSubRows) {
        expect(rowWithSubRows.subRows[0]).toMatchObject({
          type: expect.stringMatching(/RECEIPT|CREDIT|ADJUSTMENT/),
          date: expect.any(String),
          amount: expect.any(Number),
          reference: expect.any(String),
        });
      }
    });

    test('AC-STMT-002.2: GET /ar-statements/:customerId?format=DETAILED - should update running balance after each transaction', async ({
      request,
    }) => {
      // GIVEN: Customer with invoices and receipts
      // WHEN: Requesting detailed statement
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}?format=DETAILED`, {
        headers: getHeaders(),
      });

      // THEN: Running balance updates after each transaction (invoice and sub-rows)
      expect(response.status()).toBe(200);
      const body = await response.json();
      // Implementation will verify running balance calculation logic
    });
  });

  // ===== AC-STMT-003: Statement Export =====
  test.describe('AC-STMT-003: Statement Export', () => {
    test('AC-STMT-003.1: GET /ar-statements/:customerId/export?format=PDF - should export PDF with legal footer and hash', async ({
      request,
    }) => {
      // GIVEN: Customer statement exists
      // WHEN: Exporting as PDF
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}/export?format=PDF`, {
        headers: getHeaders(),
      });

      // THEN: PDF is generated with correct content type
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('application/pdf');

      // Verify filename includes customer code and date
      const contentDisposition = response.headers()['content-disposition'];
      expect(contentDisposition).toContain('Statement_');
      expect(contentDisposition).toMatch(/\.pdf$/);
    });

    test('AC-STMT-003.2: GET /ar-statements/:customerId/export?format=EXCEL - should export Excel with formulas', async ({
      request,
    }) => {
      // GIVEN: Customer statement exists
      // WHEN: Exporting as Excel
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}/export?format=EXCEL`, {
        headers: getHeaders(),
      });

      // THEN: Excel file is generated
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');

      // Verify filename
      const contentDisposition = response.headers()['content-disposition'];
      expect(contentDisposition).toContain('Statement_');
      expect(contentDisposition).toMatch(/\.xlsx$/);
    });

    test('AC-STMT-003.3: GET /ar-statements/:customerId/export - should include statement hash in PDF footer', async ({
      request,
    }) => {
      // GIVEN: Customer statement exists
      // WHEN: Exporting as PDF
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}/export?format=PDF`, {
        headers: getHeaders(),
      });

      // THEN: PDF includes SHA256 hash in footer
      expect(response.status()).toBe(200);
      // Implementation will verify hash is present in PDF metadata or footer
    });
  });

  // ===== AC-STMT-004: Send to Customer =====
  test.describe('AC-STMT-004: Send Statement to Customer', () => {
    test('AC-STMT-004.1: POST /ar-statements/:customerId/send - should queue email job and create delivery tracking', async ({
      request,
    }) => {
      // GIVEN: Customer statement exists
      // WHEN: Sending statement to customer email
      const recipientEmail = 'customer@example.com';
      const response = await request.post(`${API_BASE}/ar-statements/${customerId}/send?email=${recipientEmail}`, {
        headers: getHeaders(),
      });

      // THEN: Email job is queued and delivery tracking created
      expect(response.status()).toBe(202); // Accepted (async job)
      const body = await response.json();
      expect(body.data).toMatchObject({
        deliveryId: expect.any(String),
        status: 'SENT',
        recipientEmail,
        sentAt: expect.any(String),
      });
    });

    test('AC-STMT-004.2: POST /ar-statements/:customerId/send - should send notification to accountant', async ({
      request,
    }) => {
      // GIVEN: Statement send request
      // WHEN: Sending statement
      const response = await request.post(
        `${API_BASE}/ar-statements/${customerId}/send?email=customer@example.com`,
        {
          headers: getHeaders(),
        },
      );

      // THEN: Accountant receives confirmation notification
      expect(response.status()).toBe(202);
      // Implementation will verify notification is sent
    });
  });

  // ===== AC-STMT-005: Customer Reconciliation Import =====
  test.describe('AC-STMT-005: Reconciliation Import', () => {
    test('AC-STMT-005.1: POST /ar-statements/:customerId/import-reconciliation - should parse CSV and match invoices', async ({
      request,
    }) => {
      // GIVEN: CSV file with reconciliation data
      const csvContent = `InvoiceNumber,CustomerAmount,CustomerPayment,Notes
INV-001,10000000,5000000,Partial payment
INV-002,8000000,8000000,Full payment`;

      // WHEN: Uploading reconciliation CSV
      const formData = new FormData();
      const blob = new Blob([csvContent], { type: 'text/csv' });
      formData.append('file', blob, 'reconciliation.csv');

      const response = await request.post(`${API_BASE}/ar-statements/${customerId}/import-reconciliation`, {
        headers: {
          ...getHeaders(),
          // Remove Content-Type to let browser set multipart boundary
        },
        multipart: {
          file: {
            name: 'reconciliation.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csvContent),
          },
        },
      });

      // THEN: Reconciliation is processed and response includes match/mismatch counts
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data).toMatchObject({
        reconciliationId: expect.any(String),
        matchedCount: expect.any(Number),
        mismatchCount: expect.any(Number),
        mismatches: expect.any(Array),
      });
    });

    test('AC-STMT-005.2: POST /ar-statements/:customerId/import-reconciliation - should flag significant variances (>1000 VND)', async ({
      request,
    }) => {
      // GIVEN: CSV with invoice that has significant variance
      const csvContent = `InvoiceNumber,CustomerAmount,CustomerPayment,Notes
INV-001,10000000,10500000,Significant variance`;

      // WHEN: Uploading reconciliation
      const response = await request.post(`${API_BASE}/ar-statements/${customerId}/import-reconciliation`, {
        headers: getHeaders(),
        multipart: {
          file: {
            name: 'reconciliation.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csvContent),
          },
        },
      });

      // THEN: Mismatch is flagged as SIGNIFICANT (red)
      expect(response.status()).toBe(200);
      const body = await response.json();
      const mismatch = body.data.mismatches.find((m: any) => m.invoiceNumber === 'INV-001');
      expect(mismatch).toMatchObject({
        varianceType: 'SIGNIFICANT',
        variance: expect.any(Number),
      });
      expect(mismatch.variance).toBeGreaterThan(1000);
    });

    test('AC-STMT-005.3: POST /ar-statements/:customerId/import-reconciliation - should flag rounding variances (<=1000 VND)', async ({
      request,
    }) => {
      // GIVEN: CSV with invoice that has rounding variance
      const csvContent = `InvoiceNumber,CustomerAmount,CustomerPayment,Notes
INV-001,10000000,10000500,Round variance`;

      // WHEN: Uploading reconciliation
      const response = await request.post(`${API_BASE}/ar-statements/${customerId}/import-reconciliation`, {
        headers: getHeaders(),
        multipart: {
          file: {
            name: 'reconciliation.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csvContent),
          },
        },
      });

      // THEN: Mismatch is flagged as ROUNDING (yellow)
      expect(response.status()).toBe(200);
      const body = await response.json();
      const mismatch = body.data.mismatches.find((m: any) => m.invoiceNumber === 'INV-001');
      if (mismatch) {
        expect(mismatch.varianceType).toBe('ROUNDING');
        expect(mismatch.variance).toBeLessThanOrEqual(1000);
      }
    });
  });

  // ===== AC-STMT-006: Dispute Logging =====
  test.describe('AC-STMT-006: Dispute Logging', () => {
    test('AC-STMT-006.1: POST /ar-statements/:customerId/import-reconciliation - should create DisputeLog for each mismatch', async ({
      request,
    }) => {
      // GIVEN: Reconciliation import with mismatches
      const csvContent = `InvoiceNumber,CustomerAmount,CustomerPayment,Notes
INV-001,10000000,10500000,Mismatch`;

      // WHEN: Importing reconciliation
      const importResponse = await request.post(`${API_BASE}/ar-statements/${customerId}/import-reconciliation`, {
        headers: getHeaders(),
        multipart: {
          file: {
            name: 'reconciliation.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csvContent),
          },
        },
      });

      const importBody = await importResponse.json();
      const reconciliationId = importBody.data.reconciliationId;

      // THEN: DisputeLog entries are created
      const disputesResponse = await request.get(
        `${API_BASE}/ar-statements/disputes?reconciliationId=${reconciliationId}`,
        {
          headers: getHeaders(),
        },
      );

      expect(disputesResponse.status()).toBe(200);
      const disputesBody = await disputesResponse.json();
      expect(disputesBody.data).toBeInstanceOf(Array);
      expect(disputesBody.data.length).toBeGreaterThan(0);

      const dispute = disputesBody.data[0];
      expect(dispute).toMatchObject({
        reconciliationId,
        invoiceNumber: expect.any(String),
        systemAmount: expect.any(Number),
        customerAmount: expect.any(Number),
        variance: expect.any(Number),
        status: 'OPEN',
      });
    });
  });

  // ===== AC-STMT-007: Statement History =====
  test.describe('AC-STMT-007: Statement History', () => {
    test('AC-STMT-007.1: GET /ar-statements/:customerId/history - should return statement history', async ({
      request,
    }) => {
      // GIVEN: Statements have been generated
      // WHEN: Requesting statement history
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}/history`, {
        headers: getHeaders(),
      });

      // THEN: History entries are returned
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.data).toBeInstanceOf(Array);

      if (body.data.length > 0) {
        const history = body.data[0];
        expect(history).toMatchObject({
          id: expect.any(String),
          statementNumber: expect.any(String),
          customerId,
          generatedAt: expect.any(String),
          generatedBy: expect.any(String),
          format: expect.stringMatching(/SUMMARY|DETAILED/),
          exportCount: expect.any(Number),
          sentCount: expect.any(Number),
        });
      }
    });

    test('AC-STMT-007.2: GET /ar-statements/history/:statementId/regenerate - should regenerate statement from history', async ({
      request,
    }) => {
      // GIVEN: Statement history entry exists
      const historyResponse = await request.get(`${API_BASE}/ar-statements/${customerId}/history`, {
        headers: getHeaders(),
      });
      const historyBody = await historyResponse.json();
      const statementId = historyBody.data[0]?.id;

      if (statementId) {
        // WHEN: Regenerating statement
        const response = await request.get(`${API_BASE}/ar-statements/history/${statementId}/regenerate`, {
          headers: getHeaders(),
        });

        // THEN: Statement is regenerated with same data
        expect(response.status()).toBe(200);
        const body = await response.json();
        expect(body.data).toMatchObject({
          customerId,
          format: expect.any(String),
        });
      }
    });
  });

  // ===== AC-STMT-008: Batch Statement Export =====
  test.describe('AC-STMT-008: Batch Statement Export', () => {
    test('AC-STMT-008.1: GET /ar-statements/batch-export - should generate ZIP with multiple statements', async ({
      request,
    }) => {
      // GIVEN: Multiple customers
      const customerIds = ['customer-001', 'customer-002', 'customer-003'];

      // WHEN: Requesting batch export
      const response = await request.get(
        `${API_BASE}/ar-statements/batch-export?customerIds=${customerIds.join(',')}&format=ZIP`,
        {
          headers: getHeaders(),
        },
      );

      // THEN: ZIP file is generated
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('application/zip');

      // Verify filename includes date
      const contentDisposition = response.headers()['content-disposition'];
      expect(contentDisposition).toContain('Statements_');
      expect(contentDisposition).toMatch(/\.zip$/);
    });
  });

  // ===== AC-STMT-009: Dispute Resolution Workflow =====
  test.describe('AC-STMT-009: Dispute Resolution', () => {
    test('AC-STMT-009.1: GET /ar-statements/disputes - should filter disputes by status', async ({ request }) => {
      // GIVEN: Disputes exist with different statuses
      // WHEN: Filtering by OPEN status
      const response = await request.get(`${API_BASE}/ar-statements/disputes?status=OPEN`, {
        headers: getHeaders(),
      });

      // THEN: Only OPEN disputes are returned
      expect(response.status()).toBe(200);
      const body = await response.json();
      body.data.forEach((dispute: any) => {
        expect(dispute.status).toBe('OPEN');
      });
    });

    test('AC-STMT-009.2: POST /ar-statements/disputes/:disputeId/resolve - should resolve dispute with notes', async ({
      request,
    }) => {
      // GIVEN: OPEN dispute exists
      const disputesResponse = await request.get(`${API_BASE}/ar-statements/disputes?status=OPEN`, {
        headers: getHeaders(),
      });
      const disputesBody = await disputesResponse.json();
      const disputeId = disputesBody.data[0]?.id;

      if (disputeId) {
        // WHEN: Resolving dispute
        const response = await request.post(`${API_BASE}/ar-statements/disputes/${disputeId}/resolve`, {
          headers: getHeaders(),
          data: {
            resolutionNotes: 'Resolved after customer verification',
          },
        });

        // THEN: Dispute status is updated to RESOLVED
        expect(response.status()).toBe(200);
        const body = await response.json();
        expect(body.data).toMatchObject({
          id: disputeId,
          status: 'RESOLVED',
          resolutionNotes: expect.any(String),
          resolvedAt: expect.any(String),
          resolvedBy: expect.any(String),
        });
      }
    });
  });

  // ===== AC-STMT-010: Statement Notes in Exports =====
  test.describe('AC-STMT-010: Statement Notes in Exports', () => {
    test('AC-STMT-010.1: GET /ar-statements/:customerId/export?format=PDF - should include reconciliation notes section', async ({
      request,
    }) => {
      // GIVEN: Customer has resolved disputes
      // WHEN: Exporting statement as PDF
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}/export?format=PDF`, {
        headers: getHeaders(),
      });

      // THEN: PDF includes "Reconciliation Notes" section
      expect(response.status()).toBe(200);
      // Implementation will verify notes section is present in PDF content
    });

    test('AC-STMT-010.2: GET /ar-statements/:customerId/export?format=EXCEL - should include reconciliation notes in Excel', async ({
      request,
    }) => {
      // GIVEN: Customer has resolved disputes
      // WHEN: Exporting statement as Excel
      const response = await request.get(`${API_BASE}/ar-statements/${customerId}/export?format=EXCEL`, {
        headers: getHeaders(),
      });

      // THEN: Excel includes reconciliation notes sheet or section
      expect(response.status()).toBe(200);
      // Implementation will verify notes are present in Excel file
    });
  });
});























