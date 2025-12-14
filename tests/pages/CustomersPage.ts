import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export interface CustomerData {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  taxCode?: string;
}

export class CustomersPage extends BasePage {
  readonly path = '/customers';

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await this.page.waitForSelector('table');
    await this.waitForPageReady();
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async createCustomer(data: CustomerData): Promise<void> {
    await this.page.getByRole('button', { name: /add customer/i }).click();
    await this.waitForModal();
    await this.fillCustomerForm(data);
    await this.page.getByRole('button', { name: /create customer/i }).click();
    await this.expectSuccessToast('Customer created successfully');
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  async editCustomer(name: string, data: Partial<CustomerData>): Promise<void> {
    await this.openCustomerActions(name);
    await this.page.getByRole('menuitem', { name: /edit/i }).click();
    await this.waitForModal();
    await this.fillCustomerForm(data);
    await this.page.getByRole('button', { name: /update customer/i }).click();
    await this.expectSuccessToast('Customer updated successfully');
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  async deleteCustomer(name: string): Promise<void> {
    await this.openCustomerActions(name);
    await this.page.getByRole('menuitem', { name: /delete/i }).click();
    await expect(this.page.locator('[role="alertdialog"]')).toBeVisible();
    await this.page.locator('[role="alertdialog"]').getByRole('button', { name: /delete/i }).click();
    await this.expectSuccessToast('Customer deleted successfully');
  }

  async searchCustomer(query: string): Promise<void> {
    const searchInput = this.page.getByPlaceholder(/search/i);
    await searchInput.fill(query);
    await this.page.waitForTimeout(350);
  }

  async filterByStatus(status: 'active' | 'inactive'): Promise<void> {
    await this.page.getByRole('combobox', { name: /status/i }).click();
    const optionName = status === 'active' ? 'Active' : 'Inactive';
    await this.page.getByRole('option', { name: optionName, exact: true }).click();
    await this.page.waitForTimeout(350);
  }

  async expectCustomerInList(name: string): Promise<void> {
    await expect(this.page.locator('table tbody').getByText(name)).toBeVisible();
  }

  async expectCustomerNotInList(name: string): Promise<void> {
    await expect(this.page.locator('table tbody').getByText(name)).toBeHidden();
  }

  async getCustomerCount(): Promise<number> {
    const totalText = await this.page.locator('text=/Total:.*records/').textContent();
    const match = totalText?.match(/Total:\s*(\d+)\s*records/);
    return match ? parseInt(match[1], 10) : 0;
  }

  private async openCustomerActions(name: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: name });
    await row.getByRole('button').click();
  }

  private async fillCustomerForm(data: Partial<CustomerData>): Promise<void> {
    if (data.name !== undefined) {
      await this.page.locator('#name').fill(data.name);
    }
    if (data.email !== undefined) {
      await this.page.locator('#email').fill(data.email);
    }
    if (data.phone !== undefined) {
      await this.page.locator('#phone').fill(data.phone);
    }
    if (data.address !== undefined) {
      await this.page.locator('#address').fill(data.address);
    }
    if (data.taxCode !== undefined) {
      await this.page.locator('#taxCode').fill(data.taxCode);
    }
  }
}
