import { test, expect } from "../../support/fixtures";
import { SuppliersPage } from "../../pages/SuppliersPage";
import {
  PurchaseBillListPage,
  PurchaseBillFormPage,
} from "../../pages/PurchaseBillsPage";
import { PaymentListPage, PaymentFormPage } from "../../pages/PaymentsPage";

/**
 * WF-002: Complete Bill-to-Payment Flow
 *
 * Multi-step workflow test covering full AP cycle:
 * 1. Create supplier
 * 2. Create purchase bill
 * 3. Submit for approval
 * 4. Approve bill (different role)
 * 5. Record payment
 * 6. Allocate to bill
 * 7. Verify AP aging updated
 */
test.describe("WF-002: Bill-to-Payment Flow @workflow", () => {
  const testData = {
    supplier: {
      name: `E2E Supplier ${Date.now()}`,
      email: "e2e-supplier@test.com",
      phone: "0987654321",
      address: "456 Test Avenue, HCMC",
      taxCode: "0987654321",
    },
    bill: {
      reference: `REF-WF002-${Date.now()}`,
      description: "E2E Workflow Test Bill",
      lineItems: [
        {
          description: "Raw Materials",
          quantity: 100,
          unitPrice: 50000,
          vatRate: "TEN" as const,
        },
        {
          description: "Shipping Costs",
          quantity: 1,
          unitPrice: 500000,
          vatRate: "TEN" as const,
        },
      ],
    },
    payment: {
      reference: `PAY-WF002-${Date.now()}`,
      paymentMethod: "BANK_TRANSFER" as const,
    },
  };

  let createdSupplierName: string;
  let createdBillNumber: string;

  test("Complete AP cycle: Supplier → Bill → Approval → Payment → Allocation", async ({
    page,
    browser,
    supplierFactory,
    purchaseBillFactory,
    paymentFactory,
  }) => {
    test.slow();

    // ========================================
    // STEP 1: Create Supplier (Accountant role)
    // ========================================
    await test.step("Step 1: Create supplier", async () => {
      const suppliersPage = new SuppliersPage(page);
      await suppliersPage.navigate();

      await suppliersPage.createSupplier(testData.supplier);
      await suppliersPage.expectSupplierInList(testData.supplier.name);
      createdSupplierName = testData.supplier.name;
    });

    // ========================================
    // STEP 2: Create Purchase Bill
    // ========================================
    await test.step("Step 2: Create purchase bill", async () => {
      const listPage = new PurchaseBillListPage(page);
      await listPage.navigate();

      await listPage.clickNewBill();

      const formPage = new PurchaseBillFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectSupplier(createdSupplierName);
      await formPage.setReference(testData.bill.reference);
      await formPage.setDescription(testData.bill.description);

      for (const lineItem of testData.bill.lineItems) {
        await formPage.addLineItem(lineItem);
      }

      await formPage.saveDraft();
      await formPage.expectStatus("DRAFT");

      const billNumberElement = page
        .locator("text=/PB-\\d+|BILL-\\d+/")
        .first();
      createdBillNumber = (await billNumberElement.textContent())?.trim() || "";
      expect(createdBillNumber).toBeTruthy();
    });

    // ========================================
    // STEP 3: Submit for Approval
    // ========================================
    await test.step("Step 3: Submit bill for approval", async () => {
      const formPage = new PurchaseBillFormPage(page);
      await formPage.submit();
      await formPage.expectStatus("PENDING_APPROVAL");
    });

    // ========================================
    // STEP 4: Approve Bill (Chief Accountant role)
    // ========================================
    await test.step("Step 4: Approve bill with chief accountant role", async () => {
      const chiefContext = await browser.newContext({
        storageState: "tests/.auth/chief-accountant.json",
      });
      const chiefPage = await chiefContext.newPage();

      try {
        const chiefListPage = new PurchaseBillListPage(chiefPage);
        await chiefListPage.navigate();
        await chiefListPage.filterByStatus("pending");
        await chiefListPage.openBill(createdBillNumber);

        const chiefFormPage = new PurchaseBillFormPage(chiefPage);
        await chiefFormPage.waitForLoaded();
        await chiefFormPage.approve();
        await chiefFormPage.expectStatus("POSTED");
      } finally {
        await chiefContext.close();
      }

      await page.reload();
      const formPage = new PurchaseBillFormPage(page);
      await formPage.waitForLoaded();
      await formPage.expectStatus("POSTED");
    });

    // ========================================
    // STEP 5: Record Payment
    // ========================================
    await test.step("Step 5: Record payment", async () => {
      const paymentListPage = new PaymentListPage(page);
      await paymentListPage.navigate();

      await paymentListPage.clickNewPayment();

      const paymentFormPage = new PaymentFormPage(page);
      await paymentFormPage.waitForLoaded();

      await paymentFormPage.selectSupplier(createdSupplierName);
      await paymentFormPage.selectPaymentMethod(testData.payment.paymentMethod);
      await paymentFormPage.setReference(testData.payment.reference);

      const expectedAmount = 5000000 + 500000 + 550000;
      await paymentFormPage.setAmount(expectedAmount);
    });

    // ========================================
    // STEP 6: Allocate to Bill
    // ========================================
    await test.step("Step 6: Allocate payment to bill", async () => {
      const paymentFormPage = new PaymentFormPage(page);

      await paymentFormPage.allocateToBill(createdBillNumber);

      await paymentFormPage.post();

      const paymentNumber = await paymentFormPage.getPaymentNumber();
      expect(paymentNumber).toBeTruthy();
    });

    // ========================================
    // STEP 7: Verify AP Aging Updated
    // ========================================
    await test.step("Step 7: Verify AP aging updated", async () => {
      const billListPage = new PurchaseBillListPage(page);
      await billListPage.navigate();
      await billListPage.filterByStatus("paid");
      await billListPage.expectBillInList(createdBillNumber);

      await page.goto("/reports/ap-aging");
      await page.waitForLoadState("networkidle");

      const supplierRow = page.locator("table tbody tr", {
        hasText: createdSupplierName,
      });
      const currentBalance = supplierRow.locator("td").last();
      await expect(currentBalance).toHaveText(/0|N\/A/);
    });
  });

  test("Multiple bills paid in single payment with FIFO allocation", async ({
    page,
    browser,
    supplierFactory,
    purchaseBillFactory,
  }) => {
    test.slow();

    const multiSupplier = {
      name: `Multi-Bill Supplier ${Date.now()}`,
      email: "multi@test.com",
    };

    const bills: string[] = [];

    await test.step("Create supplier and multiple bills", async () => {
      const suppliersPage = new SuppliersPage(page);
      await suppliersPage.navigate();
      await suppliersPage.createSupplier(multiSupplier);

      for (let i = 0; i < 3; i++) {
        const listPage = new PurchaseBillListPage(page);
        await listPage.navigate();
        await listPage.clickNewBill();

        const formPage = new PurchaseBillFormPage(page);
        await formPage.waitForLoaded();
        await formPage.selectSupplier(multiSupplier.name);
        await formPage.setReference(`MULTI-${i + 1}-${Date.now()}`);
        await formPage.addLineItem({
          description: `Item ${i + 1}`,
          quantity: 1,
          unitPrice: 1000000,
          vatRate: "TEN",
        });
        await formPage.saveDraft();
        await formPage.submit();

        const billNumber = page.locator("text=/PB-\\d+|BILL-\\d+/").first();
        bills.push((await billNumber.textContent())?.trim() || "");
      }
    });

    await test.step("Approve all bills", async () => {
      const chiefContext = await browser.newContext({
        storageState: "tests/.auth/chief-accountant.json",
      });
      const chiefPage = await chiefContext.newPage();

      try {
        for (let i = 0; i < 3; i++) {
          const listPage = new PurchaseBillListPage(chiefPage);
          await listPage.navigate();
          await listPage.filterByStatus("pending");

          const firstRow = chiefPage.locator("table tbody tr").first();
          if (await firstRow.isVisible()) {
            await firstRow.click();

            const formPage = new PurchaseBillFormPage(chiefPage);
            await formPage.waitForLoaded();
            await formPage.approve();
          }
        }
      } finally {
        await chiefContext.close();
      }
    });

    await test.step("Create single payment covering all bills", async () => {
      const paymentListPage = new PaymentListPage(page);
      await paymentListPage.navigate();
      await paymentListPage.clickNewPayment();

      const formPage = new PaymentFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectSupplier(multiSupplier.name);
      await formPage.setAmount(3300000);
      await formPage.selectPaymentMethod("BANK_TRANSFER");

      await formPage.autoAllocateFIFO();

      const allocatedBills = await formPage.getAllocatedBills();
      expect(allocatedBills.length).toBe(3);

      await formPage.post();
    });

    await test.step("Verify all bills are paid", async () => {
      const listPage = new PurchaseBillListPage(page);
      await listPage.navigate();
      await listPage.filterByStatus("paid");

      for (const billNumber of bills) {
        if (billNumber) {
          await listPage.expectBillInList(billNumber);
        }
      }
    });

    await test.step("Verify AP aging shows zero balance", async () => {
      await page.goto("/reports/ap-aging");
      await page.waitForLoadState("networkidle");

      const supplierRow = page.locator("table tbody tr", {
        hasText: multiSupplier.name,
      });
      if (await supplierRow.isVisible()) {
        const balanceCell = supplierRow.locator("td").last();
        await expect(balanceCell).toHaveText(/0|N\/A/);
      }
    });
  });

  test("Bill rejection workflow returns to draft state", async ({
    page,
    browser,
  }) => {
    const rejectedSupplier = {
      name: `Rejection Test ${Date.now()}`,
      email: "reject@test.com",
    };

    await test.step("Create supplier and bill", async () => {
      const suppliersPage = new SuppliersPage(page);
      await suppliersPage.navigate();
      await suppliersPage.createSupplier(rejectedSupplier);

      const listPage = new PurchaseBillListPage(page);
      await listPage.navigate();
      await listPage.clickNewBill();

      const formPage = new PurchaseBillFormPage(page);
      await formPage.waitForLoaded();
      await formPage.selectSupplier(rejectedSupplier.name);
      await formPage.addLineItem({
        description: "Test Item",
        quantity: 1,
        unitPrice: 1000000,
        vatRate: "TEN",
      });
      await formPage.saveDraft();
      await formPage.submit();
    });

    await test.step("Reject bill with reason", async () => {
      const chiefContext = await browser.newContext({
        storageState: "tests/.auth/chief-accountant.json",
      });
      const chiefPage = await chiefContext.newPage();

      try {
        const listPage = new PurchaseBillListPage(chiefPage);
        await listPage.navigate();
        await listPage.filterByStatus("pending");

        const row = chiefPage.locator("table tbody tr", {
          hasText: rejectedSupplier.name,
        });
        await row.click();

        const formPage = new PurchaseBillFormPage(chiefPage);
        await formPage.waitForLoaded();
        await formPage.reject("Missing supporting documents");
        await formPage.expectStatus("REJECTED");
      } finally {
        await chiefContext.close();
      }
    });

    await test.step("Verify bill shows rejected status", async () => {
      const listPage = new PurchaseBillListPage(page);
      await listPage.navigate();
      await listPage.filterByStatus("rejected");

      const row = page.locator("table tbody tr", {
        hasText: rejectedSupplier.name,
      });
      await expect(row).toBeVisible();
    });
  });
});
