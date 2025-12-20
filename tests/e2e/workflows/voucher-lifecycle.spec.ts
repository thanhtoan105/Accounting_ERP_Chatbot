import { test, expect } from "../../support/fixtures";
import { VoucherListPage, VoucherFormPage } from "../../pages/VouchersPage";

/**
 * WF-003: Voucher Lifecycle Complete
 *
 * Multi-step workflow test covering full voucher lifecycle:
 * 1. Create voucher
 * 2. Validate
 * 3. Post
 * 4. Unpost
 * 5. Edit
 * 6. Revalidate
 * 7. Post
 * 8. Reverse
 */
test.describe("WF-003: Voucher Lifecycle @workflow", () => {
  const testData = {
    voucher: {
      description: `E2E Lifecycle Voucher ${Date.now()}`,
      debitAccount: "1111",
      creditAccount: "5111",
      amount: 5000000,
    },
    editedVoucher: {
      description: `Edited Voucher ${Date.now()}`,
      amount: 7500000,
    },
    reversal: {
      description: "Reversal of original voucher",
      reason: "Correction of accounting error",
    },
  };

  let createdVoucherNumber: string;

  test("Complete voucher lifecycle: Create → Post → Unpost → Edit → Repost → Reverse", async ({
    page,
  }) => {
    test.slow();

    // ========================================
    // STEP 1: Create Voucher
    // ========================================
    await test.step("Step 1: Create voucher", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.navigate();
      await listPage.waitForLoaded();

      await listPage.clickNewVoucher();

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectVoucherDate(new Date());
      await formPage.fillDescription(testData.voucher.description);

      await formPage.addLine(
        testData.voucher.debitAccount,
        testData.voucher.creditAccount,
        testData.voucher.amount,
        "Initial entry",
      );

      await formPage.saveDraft();

      createdVoucherNumber = await formPage.getVoucherNumber();
      expect(createdVoucherNumber).toBeTruthy();

      await formPage.expectStatus("draft");
    });

    // ========================================
    // STEP 2: Validate Voucher
    // ========================================
    await test.step("Step 2: Validate voucher", async () => {
      const validateButton = page.getByRole("button", { name: /validate/i });
      if (await validateButton.isVisible()) {
        await validateButton.click();
        await page.waitForLoadState("networkidle");

        const validationSuccess = page.locator("text=/valid|balanced/i");
        await expect(validationSuccess).toBeVisible();
      }
    });

    // ========================================
    // STEP 3: Post Voucher
    // ========================================
    await test.step("Step 3: Post voucher", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.post();
      await formPage.expectStatus("posted");

      const listPage = new VoucherListPage(page);
      await listPage.navigate();
      await listPage.filterByStatus("posted");

      const row = page.locator("table tbody tr", {
        hasText: createdVoucherNumber,
      });
      await expect(row).toBeVisible();
    });

    // ========================================
    // STEP 4: Unpost Voucher
    // ========================================
    await test.step("Step 4: Unpost voucher", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.openVoucherByNumber(createdVoucherNumber);

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.unpost("Need to make corrections");
      await formPage.expectStatus("unposted");

      await listPage.navigate();
      await listPage.filterByStatus("unposted");

      const row = page.locator("table tbody tr", {
        hasText: createdVoucherNumber,
      });
      await expect(row).toBeVisible();
    });

    // ========================================
    // STEP 5: Edit Voucher
    // ========================================
    await test.step("Step 5: Edit voucher", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.openVoucherByNumber(createdVoucherNumber);

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.fillDescription(testData.editedVoucher.description);

      const amountInput = page
        .locator('[data-testid="amount-cell"] input')
        .first();
      await amountInput.fill(testData.editedVoucher.amount.toString());

      await formPage.saveDraft();
    });

    // ========================================
    // STEP 6: Revalidate Voucher
    // ========================================
    await test.step("Step 6: Revalidate voucher", async () => {
      const validateButton = page.getByRole("button", { name: /validate/i });
      if (await validateButton.isVisible()) {
        await validateButton.click();
        await page.waitForLoadState("networkidle");

        const validationSuccess = page.locator("text=/valid|balanced/i");
        await expect(validationSuccess).toBeVisible();
      }
    });

    // ========================================
    // STEP 7: Re-post Voucher
    // ========================================
    await test.step("Step 7: Re-post voucher", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.post();
      await formPage.expectStatus("posted");
    });

    // ========================================
    // STEP 8: Reverse Voucher
    // ========================================
    await test.step("Step 8: Reverse voucher", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.reverse(
        testData.reversal.description,
        testData.reversal.reason,
      );

      const listPage = new VoucherListPage(page);
      await listPage.navigate();

      const reversalRow = page.locator("table tbody tr", {
        hasText: /reversal|REV/i,
      });
      await expect(reversalRow).toBeVisible();

      await reversalRow.click();

      const reversalFormPage = new VoucherFormPage(page);
      await reversalFormPage.waitForLoaded();
      await reversalFormPage.expectStatus("posted");
    });
  });

  test("Voucher validation prevents posting with unbalanced entries", async ({
    page,
  }) => {
    await test.step("Create voucher with unbalanced entries", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.navigate();
      await listPage.clickNewVoucher();

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectVoucherDate(new Date());
      await formPage.fillDescription("Unbalanced Test Voucher");

      const debitAccountCell = page
        .locator('[data-testid="debit-account-cell"]')
        .first();
      await debitAccountCell.click();
      await page.getByRole("option", { name: /1111/i }).click();

      const amountCell = page
        .locator('[data-testid="amount-cell"] input')
        .first();
      await amountCell.fill("1000000");
    });

    await test.step("Attempt to post unbalanced voucher", async () => {
      const postButton = page.getByRole("button", { name: /^post$/i });
      await postButton.click();

      await page.waitForTimeout(500);

      const errorMessage = page.locator(
        "text=/unbalanced|debit.*credit|không cân/i",
      );
      await expect(errorMessage).toBeVisible();
    });
  });

  test("Voucher date affects correct accounting period", async ({ page }) => {
    const pastDate = new Date();
    pastDate.setMonth(pastDate.getMonth() - 1);

    await test.step("Create voucher with past date", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.navigate();
      await listPage.clickNewVoucher();

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectVoucherDate(pastDate);
      await formPage.fillDescription("Past Period Voucher");

      await formPage.addLine("1111", "5111", 1000000, "Past period entry");

      await formPage.saveDraft();

      const voucherNumber = await formPage.getVoucherNumber();
      expect(voucherNumber).toBeTruthy();
    });

    await test.step("Post voucher and verify period", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.post();

      const periodIndicator = page.locator("text=/period|kỳ/i").first();
      if (await periodIndicator.isVisible()) {
        const periodText = await periodIndicator.textContent();
        const expectedMonth = pastDate.getMonth() + 1;
        expect(periodText).toContain(expectedMonth.toString().padStart(2, "0"));
      }
    });
  });

  test("Reversal creates correct opposite entries", async ({ page }) => {
    let originalVoucherNumber: string;

    await test.step("Create and post original voucher", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.navigate();
      await listPage.clickNewVoucher();

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectVoucherDate(new Date());
      await formPage.fillDescription("Original Voucher for Reversal");

      await formPage.addLine("1111", "5111", 2000000, "Original entry");

      await formPage.saveDraft();
      originalVoucherNumber = await formPage.getVoucherNumber();

      await formPage.post();
      await formPage.expectStatus("posted");
    });

    await test.step("Reverse the voucher", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.reverse(
        "Reversal entry",
        "Testing reversal functionality",
      );
    });

    await test.step("Verify reversal entry has opposite debits/credits", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.navigate();

      await listPage.searchVouchers(originalVoucherNumber);

      const reversalRow = page.locator("table tbody tr", {
        hasText: /reversal|REV/i,
      });
      await reversalRow.click();

      const debitCell = page
        .locator('[data-testid="debit-account-cell"]')
        .first();
      const creditCell = page
        .locator('[data-testid="credit-account-cell"]')
        .first();

      const debitText = await debitCell.textContent();
      const creditText = await creditCell.textContent();

      expect(debitText).toContain("5111");
      expect(creditText).toContain("1111");
    });
  });

  test("Voucher with attachments maintains files through lifecycle", async ({
    page,
  }) => {
    test.skip(true, "Attachment functionality requires file upload setup");

    await test.step("Create voucher with attachment", async () => {
      const listPage = new VoucherListPage(page);
      await listPage.navigate();
      await listPage.clickNewVoucher();

      const formPage = new VoucherFormPage(page);
      await formPage.waitForLoaded();

      await formPage.selectVoucherDate(new Date());
      await formPage.fillDescription("Voucher with Attachment");
      await formPage.addLine("1111", "5111", 1000000, "Entry with attachment");

      await formPage.uploadAttachment("tests/fixtures/sample-invoice.pdf");

      await formPage.saveDraft();
    });

    await test.step("Post and verify attachment persists", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.post();

      const attachmentIndicator = page.locator("text=/attachment|đính kèm/i");
      await expect(attachmentIndicator).toBeVisible();
    });

    await test.step("Unpost and verify attachment still exists", async () => {
      const formPage = new VoucherFormPage(page);
      await formPage.unpost("Testing attachment persistence");

      const attachmentIndicator = page.locator("text=/attachment|đính kèm/i");
      await expect(attachmentIndicator).toBeVisible();
    });
  });
});
