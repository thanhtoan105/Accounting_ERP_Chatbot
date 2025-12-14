import { test, expect } from "../../support/fixtures";
import { CustomersPage } from "../../pages/CustomersPage";
import {
  SalesInvoiceListPage,
  SalesInvoiceFormPage,
} from "../../pages/SalesInvoicesPage";
import { ReceiptListPage, ReceiptFormPage } from "../../pages/ReceiptsPage";

/**
 * WF-001: Complete Invoice-to-Receipt Flow
 *
 * Multi-step workflow test covering full AR cycle:
 * 1. Create customer
 * 2. Create sales invoice
 * 3. Submit for approval
 * 4. Approve invoice (different role)
 * 5. Record receipt
 * 6. Allocate to invoice
 * 7. Verify AR aging updated
 */
test.describe("WF-001: Invoice-to-Receipt Flow @workflow", () => {
  const testData = {
    customer: {
      name: `E2E Customer ${Date.now()}`,
      email: "e2e-customer@test.com",
      phone: "0123456789",
      address: "123 Test Street, Hanoi",
      taxCode: "1234567890",
    },
    invoice: {
      reference: `REF-WF001-${Date.now()}`,
      description: "E2E Workflow Test Invoice",
      lineItems: [
        {
          description: "Consulting Services",
          quantity: 10,
          unitPrice: 1000000,
          vatRate: "TEN" as const,
        },
        {
          description: "Support Services",
          quantity: 5,
          unitPrice: 500000,
          vatRate: "TEN" as const,
        },
      ],
    },
    receipt: {
      reference: `REC-WF001-${Date.now()}`,
      paymentMethod: "BANK_TRANSFER" as const,
    },
  };

  let createdCustomerName: string;
  let createdInvoiceNumber: string;

  test("Complete AR cycle: Customer → Invoice → Approval → Receipt → Allocation", async ({
    page,
    browser,
    customerFactory,
    salesInvoiceFactory,
  }) => {
    test.slow();

    // ========================================
    // STEP 1: Create Customer (Accountant role)
    // ========================================
    test.step("Step 1: Create customer", async () => {
      const customersPage = new CustomersPage(page);
      await customersPage.navigate();

      await customersPage.createCustomer(testData.customer);
      await customersPage.expectCustomerInList(testData.customer.name);
      createdCustomerName = testData.customer.name;
    });

    // ========================================
    // STEP 2: Create Sales Invoice
    // ========================================
    await test.step("Step 2: Create sales invoice", async () => {
      const listPage = new SalesInvoiceListPage(page);
      await listPage.navigate();

      await listPage.clickNewInvoice();

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectCustomer(createdCustomerName);
      await formPage.setReference(testData.invoice.reference);
      await formPage.setDescription(testData.invoice.description);

      for (const lineItem of testData.invoice.lineItems) {
        await formPage.addLineItem(lineItem);
      }

      await formPage.saveDraft();
      await formPage.expectStatus("DRAFT");

      const invoiceNumberElement = page.locator("text=/SI-\\d+/").first();
      createdInvoiceNumber =
        (await invoiceNumberElement.textContent())?.trim() || "";
      expect(createdInvoiceNumber).toBeTruthy();
    });

    // ========================================
    // STEP 3: Submit for Approval
    // ========================================
    await test.step("Step 3: Submit invoice for approval", async () => {
      const formPage = new SalesInvoiceFormPage(page);
      await formPage.submit();
      await formPage.expectStatus("PENDING_APPROVAL");
    });

    // ========================================
    // STEP 4: Approve Invoice (Chief Accountant role)
    // ========================================
    await test.step("Step 4: Approve invoice with chief accountant role", async () => {
      const chiefContext = await browser.newContext({
        storageState: "tests/.auth/chief-accountant.json",
      });
      const chiefPage = await chiefContext.newPage();

      try {
        const chiefListPage = new SalesInvoiceListPage(chiefPage);
        await chiefListPage.navigate();
        await chiefListPage.filterByStatus("pending");
        await chiefListPage.openInvoice(createdInvoiceNumber);

        const chiefFormPage = new SalesInvoiceFormPage(chiefPage);
        await chiefFormPage.waitForLoaded();
        await chiefFormPage.approve();
        await chiefFormPage.expectStatus("POSTED");
      } finally {
        await chiefContext.close();
      }

      await page.reload();
      const formPage = new SalesInvoiceFormPage(page);
      await formPage.waitForLoaded();
      await formPage.expectStatus("POSTED");
    });

    // ========================================
    // STEP 5: Record Receipt
    // ========================================
    await test.step("Step 5: Record receipt", async () => {
      const receiptListPage = new ReceiptListPage(page);
      await receiptListPage.navigate();

      await receiptListPage.clickNewReceipt();

      const receiptFormPage = new ReceiptFormPage(page);
      await receiptFormPage.waitForLoaded();

      await receiptFormPage.selectCustomer(createdCustomerName);
      await receiptFormPage.selectPaymentMethod(testData.receipt.paymentMethod);
      await receiptFormPage.setReference(testData.receipt.reference);

      const expectedAmount = 12500000 + 1250000;
      await receiptFormPage.setAmount(expectedAmount);
    });

    // ========================================
    // STEP 6: Allocate to Invoice
    // ========================================
    await test.step("Step 6: Allocate receipt to invoice", async () => {
      const receiptFormPage = new ReceiptFormPage(page);

      await receiptFormPage.allocateToInvoice(createdInvoiceNumber);

      await receiptFormPage.post();

      const receiptNumber = await receiptFormPage.getReceiptNumber();
      expect(receiptNumber).toBeTruthy();
    });

    // ========================================
    // STEP 7: Verify AR Aging Updated
    // ========================================
    await test.step("Step 7: Verify AR aging updated", async () => {
      const invoiceListPage = new SalesInvoiceListPage(page);
      await invoiceListPage.navigate();
      await invoiceListPage.filterByStatus("paid");
      await invoiceListPage.expectInvoiceInList(createdInvoiceNumber);

      await page.goto("/reports/ar-aging");
      await page.waitForLoadState("networkidle");

      const customerRow = page.locator("table tbody tr", {
        hasText: createdCustomerName,
      });
      const currentBalance = customerRow.locator("td").last();
      await expect(currentBalance).toHaveText(/0|N\/A/);
    });
  });

  test("Partial receipt allocation updates AR aging correctly", async ({
    page,
    browser,
    customerFactory,
    salesInvoiceFactory,
  }) => {
    test.slow();

    const partialCustomer = {
      name: `Partial Customer ${Date.now()}`,
      email: "partial@test.com",
    };

    await test.step("Create customer and invoice", async () => {
      const customersPage = new CustomersPage(page);
      await customersPage.navigate();
      await customersPage.createCustomer(partialCustomer);

      const invoiceListPage = new SalesInvoiceListPage(page);
      await invoiceListPage.navigate();
      await invoiceListPage.clickNewInvoice();

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.waitForLoaded();
      await formPage.selectCustomer(partialCustomer.name);
      await formPage.addLineItem({
        description: "Test Service",
        quantity: 1,
        unitPrice: 10000000,
        vatRate: "TEN",
      });
      await formPage.saveDraft();
      await formPage.submit();
    });

    await test.step("Approve invoice", async () => {
      const chiefContext = await browser.newContext({
        storageState: "tests/.auth/chief-accountant.json",
      });
      const chiefPage = await chiefContext.newPage();

      try {
        const listPage = new SalesInvoiceListPage(chiefPage);
        await listPage.navigate();
        await listPage.filterByStatus("pending");

        const firstRow = chiefPage.locator("table tbody tr").first();
        await firstRow.click();

        const formPage = new SalesInvoiceFormPage(chiefPage);
        await formPage.waitForLoaded();
        await formPage.approve();
      } finally {
        await chiefContext.close();
      }
    });

    await test.step("Create partial receipt (50%)", async () => {
      const receiptListPage = new ReceiptListPage(page);
      await receiptListPage.navigate();
      await receiptListPage.clickNewReceipt();

      const formPage = new ReceiptFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectCustomer(partialCustomer.name);
      await formPage.setAmount(5500000);
      await formPage.selectPaymentMethod("BANK_TRANSFER");

      await formPage.autoAllocateFIFO();
      await formPage.post();
    });

    await test.step("Verify invoice shows partially paid", async () => {
      const listPage = new SalesInvoiceListPage(page);
      await listPage.navigate();
      await listPage.filterByStatus("partially_paid");

      const row = page.locator("table tbody tr", {
        hasText: partialCustomer.name,
      });
      await expect(row).toBeVisible();
    });

    await test.step("Verify AR aging shows outstanding balance", async () => {
      await page.goto("/reports/ar-aging");
      await page.waitForLoadState("networkidle");

      const customerRow = page.locator("table tbody tr", {
        hasText: partialCustomer.name,
      });
      await expect(customerRow).toBeVisible();

      const balanceCell = customerRow.locator("td").last();
      const balanceText = await balanceCell.textContent();
      expect(balanceText).toContain("5,500,000");
    });
  });
});
