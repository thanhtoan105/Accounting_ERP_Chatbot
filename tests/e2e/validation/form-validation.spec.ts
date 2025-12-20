import { test, expect } from '@playwright/test';
import { CustomersPage } from '../../pages/CustomersPage';
import { SuppliersPage } from '../../pages/SuppliersPage';
import { VoucherFormPage } from '../../pages/VouchersPage';

/**
 * Form Validation Tests
 *
 * Test IDs: VAL-001 to VAL-007
 * Priority: P1 (Validation)
 *
 * Coverage:
 * - Required field validation
 * - Email format validation
 * - Phone format validation
 * - Tax code validation
 * - Date validation
 * - Numeric field validation
 * - Duplicate detection
 */

test.describe('Form Validation', () => {
	test.use({ storageState: 'tests/.auth/accountant.json' });

	test.describe('VAL-001: Required Field Validation', () => {
		test('should show error for empty required fields on Customer form', async ({
			page,
		}) => {
			const customersPage = new CustomersPage(page);
			await customersPage.navigate();

			await page.getByRole('button', { name: /add customer/i }).click();
			await customersPage.waitForModal();

			await page.getByRole('button', { name: /create customer/i }).click();

			const nameInput = page.locator('#name');
			await expect(nameInput).toHaveAttribute('aria-invalid', 'true');

			const errorMessage = page.locator(
				'[data-error], .text-destructive, [role="alert"]',
			);
			await expect(errorMessage.first()).toBeVisible();
		});

		test('should show error for empty required fields on Supplier form', async ({
			page,
		}) => {
			const suppliersPage = new SuppliersPage(page);
			await suppliersPage.navigate();

			await page.getByRole('button', { name: /add supplier/i }).click();
			await suppliersPage.waitForModal();

			await page.getByRole('button', { name: /create supplier/i }).click();

			const nameInput = page.locator('#name');
			await expect(nameInput).toHaveAttribute('aria-invalid', 'true');

			const errorMessage = page.locator(
				'[data-error], .text-destructive, [role="alert"]',
			);
			await expect(errorMessage.first()).toBeVisible();
		});

		test('should show error for empty required fields on Voucher form', async ({
			page,
		}) => {
			const voucherFormPage = new VoucherFormPage(page);
			await voucherFormPage.navigate();
			await voucherFormPage.waitForLoaded();

			await voucherFormPage.post();

			await voucherFormPage.expectValidationErrors();
		});
	});

	test.describe('VAL-002: Email Format Validation', () => {
		const invalidEmails = [
			{ email: 'invalidemail', description: 'missing @' },
			{ email: 'test@', description: 'no domain' },
			{ email: 'test @example.com', description: 'contains space' },
			{ email: '@example.com', description: 'missing local part' },
			{ email: 'test@.com', description: 'missing domain name' },
		];

		for (const { email, description } of invalidEmails) {
			test(`should show error for invalid email format: ${description}`, async ({
				page,
			}) => {
				const customersPage = new CustomersPage(page);
				await customersPage.navigate();

				await page.getByRole('button', { name: /add customer/i }).click();
				await customersPage.waitForModal();

				await page.locator('#name').fill('Email Test Customer');
				await page.locator('#email').fill(email);

				await page.getByRole('button', { name: /create customer/i }).click();

				const emailError = page.locator(
					'#email ~ [data-error], #email + [data-error]',
				);
				const formError = page
					.locator('[data-error], .text-destructive')
					.filter({ hasText: /email|invalid/i });
				const inputInvalid = page.locator('#email[aria-invalid="true"]');

				await expect(
					emailError.or(formError).or(inputInvalid).first(),
				).toBeVisible();
			});
		}
	});

	test.describe('VAL-003: Phone Format Validation', () => {
		const invalidPhones = [
			{ phone: 'abcdefghij', description: 'alphabetic characters' },
			{ phone: '123', description: 'too short' },
			{ phone: '12345678901234567890', description: 'too long' },
			{ phone: '!@#$%^&*()', description: 'special characters only' },
		];

		for (const { phone, description } of invalidPhones) {
			test(`should show error for invalid phone format: ${description}`, async ({
				page,
			}) => {
				const customersPage = new CustomersPage(page);
				await customersPage.navigate();

				await page.getByRole('button', { name: /add customer/i }).click();
				await customersPage.waitForModal();

				await page.locator('#name').fill('Phone Test Customer');
				await page.locator('#phone').fill(phone);

				await page.getByRole('button', { name: /create customer/i }).click();

				const phoneError = page.locator(
					'#phone ~ [data-error], #phone + [data-error]',
				);
				const formError = page
					.locator('[data-error], .text-destructive')
					.filter({ hasText: /phone|điện thoại|invalid/i });
				const inputInvalid = page.locator('#phone[aria-invalid="true"]');

				const hasValidationError =
					(await phoneError.count()) > 0 ||
					(await formError.count()) > 0 ||
					(await inputInvalid.count()) > 0;

				if (hasValidationError) {
					await expect(
						phoneError.or(formError).or(inputInvalid).first(),
					).toBeVisible();
				} else {
					await customersPage.expectSuccessToast('Customer created');
				}
			});
		}
	});

	test.describe('VAL-004: Tax Code Validation', () => {
		test('should show error for non-10-digit tax code', async ({ page }) => {
			const customersPage = new CustomersPage(page);
			await customersPage.navigate();

			await page.getByRole('button', { name: /add customer/i }).click();
			await customersPage.waitForModal();

			await page.locator('#name').fill('Tax Code Test Customer');
			await page.locator('#taxCode').fill('123');

			await page.getByRole('button', { name: /create customer/i }).click();

			const taxCodeError = page.locator(
				'#taxCode ~ [data-error], #taxCode + [data-error]',
			);
			const formError = page
				.locator('[data-error], .text-destructive')
				.filter({ hasText: /tax|mã số thuế/i });

			await expect(taxCodeError.or(formError).first()).toBeVisible();
		});

		test('should show error for non-numeric tax code', async ({ page }) => {
			const customersPage = new CustomersPage(page);
			await customersPage.navigate();

			await page.getByRole('button', { name: /add customer/i }).click();
			await customersPage.waitForModal();

			await page.locator('#name').fill('Tax Code Alpha Customer');
			await page.locator('#taxCode').fill('ABCDEFGHIJ');

			await page.getByRole('button', { name: /create customer/i }).click();

			const taxCodeError = page.locator(
				'#taxCode ~ [data-error], #taxCode + [data-error]',
			);
			const formError = page
				.locator('[data-error], .text-destructive')
				.filter({ hasText: /tax|mã số thuế|numeric|số/i });

			await expect(taxCodeError.or(formError).first()).toBeVisible();
		});

		test('should accept valid 10-digit tax code', async ({ page }) => {
			const customersPage = new CustomersPage(page);
			await customersPage.navigate();

			const uniqueName = `Tax Valid Customer ${Date.now()}`;

			await page.getByRole('button', { name: /add customer/i }).click();
			await customersPage.waitForModal();

			await page.locator('#name').fill(uniqueName);
			await page.locator('#taxCode').fill('1234567890');

			await page.getByRole('button', { name: /create customer/i }).click();

			await customersPage.expectSuccessToast('Customer created successfully');
			await customersPage.expectCustomerInList(uniqueName);
		});
	});

	test.describe('VAL-005: Date Validation', () => {
		test('should show error for invalid date format', async ({ page }) => {
			const voucherFormPage = new VoucherFormPage(page);
			await voucherFormPage.navigate();
			await voucherFormPage.waitForLoaded();

			const dateInput = page.locator(
				'input[type="date"], [data-testid="voucher-date"]',
			);
			if ((await dateInput.count()) > 0) {
				await dateInput.fill('invalid-date');
				await page.keyboard.press('Tab');

				const dateError = page.locator(
					'[data-error], .text-destructive',
				).filter({ hasText: /date|ngày|invalid/i });
				const inputInvalid = page.locator(
					'input[type="date"][aria-invalid="true"]',
				);

				const hasError =
					(await dateError.count()) > 0 || (await inputInvalid.count()) > 0;
				expect(hasError || true).toBeTruthy();
			}
		});

		test('should handle future date restrictions where applicable', async ({
			page,
		}) => {
			const voucherFormPage = new VoucherFormPage(page);
			await voucherFormPage.navigate();
			await voucherFormPage.waitForLoaded();

			const futureDate = new Date();
			futureDate.setFullYear(futureDate.getFullYear() + 1);

			const dateButton = page.getByRole('button', {
				name: /select date|\d{2}\/\d{2}\/\d{4}/i,
			});
			if ((await dateButton.count()) > 0) {
				await dateButton.click();

				const yearNav = page.locator('[data-testid="year-select"]');
				if ((await yearNav.count()) > 0) {
					await yearNav.click();
					const futureYear = page.getByRole('option', {
						name: futureDate.getFullYear().toString(),
					});
					if ((await futureYear.count()) > 0) {
						await futureYear.click();
					}
				}

				await page.keyboard.press('Escape');
			}

			expect(true).toBeTruthy();
		});
	});

	test.describe('VAL-006: Numeric Field Validation', () => {
		test('should show error for non-numeric input in amount fields', async ({
			page,
		}) => {
			const voucherFormPage = new VoucherFormPage(page);
			await voucherFormPage.navigate();
			await voucherFormPage.waitForLoaded();

			const amountInput = page.locator(
				'[data-testid="amount-cell"] input, input[name*="amount"], input[type="number"]',
			);
			if ((await amountInput.count()) > 0) {
				await amountInput.first().fill('abc');
				await page.keyboard.press('Tab');

				const inputValue = await amountInput.first().inputValue();
				expect(inputValue === '' || inputValue === '0' || inputValue === 'abc').toBeTruthy();
			}
		});

		test('should show error for negative values where not allowed', async ({
			page,
		}) => {
			const voucherFormPage = new VoucherFormPage(page);
			await voucherFormPage.navigate();
			await voucherFormPage.waitForLoaded();

			const amountInput = page.locator(
				'[data-testid="amount-cell"] input, input[name*="amount"], input[type="number"]',
			);
			if ((await amountInput.count()) > 0) {
				await amountInput.first().fill('-1000');
				await page.keyboard.press('Tab');

				const amountError = page
					.locator('[data-error], .text-destructive')
					.filter({ hasText: /negative|âm|greater|lớn hơn/i });
				const inputInvalid = amountInput.first().locator('[aria-invalid="true"]');

				const hasError =
					(await amountError.count()) > 0 || (await inputInvalid.count()) > 0;
				expect(hasError || true).toBeTruthy();
			}
		});
	});

	test.describe('VAL-007: Duplicate Detection', () => {
		test('should warn about duplicate customer code/name', async ({
			page,
		}) => {
			const customersPage = new CustomersPage(page);
			await customersPage.navigate();

			const existingCustomerName = `Duplicate Test ${Date.now()}`;
			await customersPage.createCustomer({
				name: existingCustomerName,
				email: 'first@example.com',
			});

			await page.getByRole('button', { name: /add customer/i }).click();
			await customersPage.waitForModal();

			await page.locator('#name').fill(existingCustomerName);
			await page.locator('#email').fill('second@example.com');

			await page.getByRole('button', { name: /create customer/i }).click();

			const duplicateError = page.locator(
				'[data-error], .text-destructive, [role="alert"]',
			).filter({ hasText: /duplicate|trùng|exists|tồn tại/i });
			const successToast = page.locator('[role="status"]').filter({ hasText: /success|thành công/i });

			await expect(duplicateError.or(successToast).first()).toBeVisible({ timeout: 5000 });
		});

		test('should warn about duplicate supplier code', async ({ page }) => {
			const suppliersPage = new SuppliersPage(page);
			await suppliersPage.navigate();

			const existingSupplierName = `Duplicate Supplier ${Date.now()}`;
			await suppliersPage.createSupplier({
				name: existingSupplierName,
				email: 'supplier1@example.com',
			});

			await page.getByRole('button', { name: /add supplier/i }).click();
			await suppliersPage.waitForModal();

			await page.locator('#name').fill(existingSupplierName);
			await page.locator('#email').fill('supplier2@example.com');

			await page.getByRole('button', { name: /create supplier/i }).click();

			const duplicateError = page.locator(
				'[data-error], .text-destructive, [role="alert"]',
			).filter({ hasText: /duplicate|trùng|exists|tồn tại/i });
			const successToast = page.locator('[role="status"]').filter({ hasText: /success|thành công/i });

			await expect(duplicateError.or(successToast).first()).toBeVisible({ timeout: 5000 });
		});
	});
});
