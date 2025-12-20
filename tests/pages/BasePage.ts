import { Page, Locator, expect } from '@playwright/test';

/**
 * Base Page Object Model class that provides common functionality
 * for all page objects in the E2E test suite.
 */
export abstract class BasePage {
  protected readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  // ==================== Navigation Methods ====================

  /**
   * Navigate to a specific path
   * @param path - The path to navigate to (e.g., '/dashboard')
   */
  async goto(path: string): Promise<void> {
    await this.page.goto(path);
    await this.waitForPageReady();
  }

  /**
   * Abstract method for subclasses to implement page-specific load waiting
   */
  abstract waitForLoaded(): Promise<void>;

  /**
   * Wait for the page to be fully ready (network idle and DOM loaded)
   */
  async waitForPageReady(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.waitForLoadState('networkidle');
  }

  // ==================== Common UI Interactions ====================

  /**
   * Click a button by its text content
   * @param text - The button text to match
   */
  async clickButton(text: string): Promise<void> {
    await this.page.getByRole('button', { name: text }).click();
  }

  /**
   * Click an element by its data-testid attribute
   * @param testId - The data-testid value
   */
  async clickButtonByTestId(testId: string): Promise<void> {
    await this.page.getByTestId(testId).click();
  }

  /**
   * Fill an input field with a value
   * @param selector - CSS selector or locator string
   * @param value - The value to fill
   */
  async fillInput(selector: string, value: string): Promise<void> {
    await this.page.locator(selector).fill(value);
  }

  /**
   * Select an option from a native dropdown
   * @param selector - CSS selector for the select element
   * @param value - The value to select
   */
  async selectOption(selector: string, value: string): Promise<void> {
    await this.page.locator(selector).selectOption(value);
  }

  /**
   * Select a value from a shadcn combobox/select component
   * @param label - The label text of the combobox
   * @param value - The option value to select
   */
  async selectFromCombobox(label: string, value: string): Promise<void> {
    // Click the combobox trigger
    await this.page.getByLabel(label).click();
    // Wait for the dropdown to appear and select the option
    await this.page.getByRole('option', { name: value }).click();
  }

  // ==================== Toast/Notification Assertions ====================

  /**
   * Assert that a toast notification with specific message appears
   * @param message - The expected toast message
   */
  async expectToast(message: string): Promise<void> {
    const toast = this.page.locator('[data-sonner-toast]', { hasText: message });
    await expect(toast).toBeVisible({ timeout: 10000 });
  }

  /**
   * Assert that a success toast notification appears
   * @param message - Optional specific message to match
   */
  async expectSuccessToast(message?: string): Promise<void> {
    const toast = message
      ? this.page.locator('[data-sonner-toast][data-type="success"]', { hasText: message })
      : this.page.locator('[data-sonner-toast][data-type="success"]');
    await expect(toast).toBeVisible({ timeout: 10000 });
  }

  /**
   * Assert that an error toast notification appears
   * @param message - Optional specific message to match
   */
  async expectErrorToast(message?: string): Promise<void> {
    const toast = message
      ? this.page.locator('[data-sonner-toast][data-type="error"]', { hasText: message })
      : this.page.locator('[data-sonner-toast][data-type="error"]');
    await expect(toast).toBeVisible({ timeout: 10000 });
  }

  /**
   * Dismiss any visible toast notification
   */
  async dismissToast(): Promise<void> {
    const closeButton = this.page.locator('[data-sonner-toast] [data-close-button]');
    if (await closeButton.isVisible()) {
      await closeButton.click();
    }
  }

  // ==================== Common Assertions ====================

  /**
   * Assert that the current URL matches the expected path
   * @param path - The expected URL path
   */
  async expectUrl(path: string): Promise<void> {
    await expect(this.page).toHaveURL(path);
  }

  /**
   * Assert that the current URL contains the expected path
   * @param path - The path fragment to check for
   */
  async expectUrlContains(path: string): Promise<void> {
    await expect(this.page).toHaveURL(new RegExp(path));
  }

  /**
   * Assert that an element is visible
   * @param selector - CSS selector or locator string
   */
  async expectVisible(selector: string): Promise<void> {
    await expect(this.page.locator(selector)).toBeVisible();
  }

  /**
   * Assert that an element is hidden
   * @param selector - CSS selector or locator string
   */
  async expectHidden(selector: string): Promise<void> {
    await expect(this.page.locator(selector)).toBeHidden();
  }

  /**
   * Assert that an element contains specific text
   * @param selector - CSS selector or locator string
   * @param text - The expected text content
   */
  async expectText(selector: string, text: string): Promise<void> {
    await expect(this.page.locator(selector)).toHaveText(text);
  }

  // ==================== Table Helpers ====================

  /**
   * Get the number of rows in the main data table
   * @returns The count of table body rows
   */
  async getTableRowCount(): Promise<number> {
    const rows = this.page.locator('table tbody tr');
    return rows.count();
  }

  /**
   * Click on a specific table row by index
   * @param index - Zero-based row index
   */
  async clickTableRow(index: number): Promise<void> {
    await this.page.locator('table tbody tr').nth(index).click();
  }

  /**
   * Get the text content of a specific table cell
   * @param row - Zero-based row index
   * @param column - Zero-based column index
   * @returns The cell text content
   */
  async getTableCellText(row: number, column: number): Promise<string> {
    const cell = this.page.locator('table tbody tr').nth(row).locator('td').nth(column);
    return (await cell.textContent()) ?? '';
  }

  /**
   * Search within the table using the search input
   * @param searchText - The text to search for
   */
  async searchInTable(searchText: string): Promise<void> {
    const searchInput = this.page.getByPlaceholder(/search|tìm kiếm/i);
    await searchInput.fill(searchText);
    await this.page.waitForTimeout(300); // Debounce delay
  }

  // ==================== Modal/Dialog Helpers ====================

  /**
   * Wait for a modal dialog to appear
   */
  async waitForModal(): Promise<void> {
    await expect(this.page.locator('[role="dialog"]')).toBeVisible();
  }

  /**
   * Close the current modal dialog
   */
  async closeModal(): Promise<void> {
    const closeButton = this.page.locator('[role="dialog"] button[aria-label="Close"]');
    if (await closeButton.isVisible()) {
      await closeButton.click();
    } else {
      // Fallback: press Escape key
      await this.page.keyboard.press('Escape');
    }
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  /**
   * Click the confirm/OK button in a dialog
   */
  async confirmDialog(): Promise<void> {
    const confirmButton = this.page
      .locator('[role="alertdialog"], [role="dialog"]')
      .getByRole('button', { name: /confirm|ok|yes|xác nhận|đồng ý/i });
    await confirmButton.click();
  }

  /**
   * Click the cancel button in a dialog
   */
  async cancelDialog(): Promise<void> {
    const cancelButton = this.page
      .locator('[role="alertdialog"], [role="dialog"]')
      .getByRole('button', { name: /cancel|no|hủy|không/i });
    await cancelButton.click();
  }

  // ==================== Form Helpers ====================

  /**
   * Submit the current form
   */
  async submitForm(): Promise<void> {
    const submitButton = this.page.getByRole('button', { name: /submit|save|lưu|gửi/i });
    await submitButton.click();
  }

  /**
   * Clear all input fields in the current form
   */
  async clearForm(): Promise<void> {
    const inputs = this.page.locator('form input:not([type="hidden"]), form textarea');
    const count = await inputs.count();
    for (let i = 0; i < count; i++) {
      await inputs.nth(i).clear();
    }
  }

  /**
   * Get the validation error message for a specific form field
   * @param fieldName - The name or label of the field
   * @returns The error message text, or empty string if no error
   */
  async getFormError(fieldName: string): Promise<string> {
    // Try to find error by field name attribute
    const errorByName = this.page.locator(`[name="${fieldName}"] ~ [data-error], [name="${fieldName}"] + [data-error]`);
    if (await errorByName.isVisible()) {
      return (await errorByName.textContent()) ?? '';
    }

    // Try to find error by label association
    const fieldGroup = this.page.locator(`label:has-text("${fieldName}")`).locator('..');
    const errorInGroup = fieldGroup.locator('[data-error], .text-destructive, [role="alert"]');
    if (await errorInGroup.isVisible()) {
      return (await errorInGroup.textContent()) ?? '';
    }

    return '';
  }

  // ==================== Utility Methods ====================

  /**
   * Get a locator for an element by test ID
   * @param testId - The data-testid value
   * @returns Playwright Locator
   */
  protected getByTestId(testId: string): Locator {
    return this.page.getByTestId(testId);
  }

  /**
   * Take a screenshot for debugging
   * @param name - Name for the screenshot file
   */
  async screenshot(name: string): Promise<void> {
    await this.page.screenshot({ path: `test-results/screenshots/${name}.png` });
  }
}
