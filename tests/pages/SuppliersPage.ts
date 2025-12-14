import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export interface SupplierData {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  taxCode?: string;
}

export class SuppliersPage extends BasePage {
  readonly path = '/suppliers';

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

  async createSupplier(data: SupplierData): Promise<void> {
    await this.page.getByRole('button', { name: /add supplier/i }).click();
    await this.waitForModal();
    await this.fillSupplierForm(data);
    await this.page.getByRole('button', { name: /create supplier/i }).click();
    await this.expectSuccessToast('Supplier created successfully');
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  async editSupplier(name: string, data: Partial<SupplierData>): Promise<void> {
    await this.openSupplierActions(name);
    await this.page.getByRole('menuitem', { name: /edit/i }).click();
    await this.waitForModal();
    await this.fillSupplierForm(data);
    await this.page.getByRole('button', { name: /update supplier/i }).click();
    await this.expectSuccessToast('Supplier updated successfully');
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  async deleteSupplier(name: string): Promise<void> {
    await this.openSupplierActions(name);
    await this.page.getByRole('menuitem', { name: /delete/i }).click();
    await expect(this.page.locator('[role="alertdialog"]')).toBeVisible();
    await this.page.locator('[role="alertdialog"]').getByRole('button', { name: /delete/i }).click();
    await this.expectSuccessToast('Supplier deleted successfully');
  }

  async searchSupplier(query: string): Promise<void> {
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

  async expectSupplierInList(name: string): Promise<void> {
    await expect(this.page.locator('table tbody').getByText(name)).toBeVisible();
  }

  async expectSupplierNotInList(name: string): Promise<void> {
    await expect(this.page.locator('table tbody').getByText(name)).toBeHidden();
  }

  async getSupplierCount(): Promise<number> {
    const totalText = await this.page.locator('text=/Total:.*records/').textContent();
    const match = totalText?.match(/Total:\s*(\d+)\s*records/);
    return match ? parseInt(match[1], 10) : 0;
  }

  private async openSupplierActions(name: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: name });
    await row.getByRole('button').click();
  }

  private async fillSupplierForm(data: Partial<SupplierData>): Promise<void> {
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
