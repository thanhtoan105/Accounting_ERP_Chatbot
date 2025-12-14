import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export interface ProfileData {
  name?: string;
  email?: string;
  phone?: string;
}

export class ProfilePage extends BasePage {
  readonly path = '/profile';

  private readonly profileHeading = () => this.page.locator('h1').filter({ hasText: /profile/i });
  private readonly nameInput = () => this.page.getByTestId('profile-name-input');
  private readonly emailInput = () => this.page.getByTestId('profile-email-input');
  private readonly phoneInput = () => this.page.getByTestId('profile-phone-input');
  private readonly saveProfileButton = () => this.page.getByTestId('save-profile-button');

  private readonly currentPasswordInput = () => this.page.getByTestId('current-password-input');
  private readonly newPasswordInput = () => this.page.getByTestId('new-password-input');
  private readonly confirmPasswordInput = () => this.page.getByTestId('confirm-password-input');
  private readonly changePasswordButton = () => this.page.getByTestId('change-password-button');

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.profileHeading()).toBeVisible({ timeout: 10000 });
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async updateProfile(data: ProfileData): Promise<void> {
    if (data.name !== undefined) {
      await this.nameInput().clear();
      await this.nameInput().fill(data.name);
    }
    if (data.email !== undefined) {
      await this.emailInput().clear();
      await this.emailInput().fill(data.email);
    }
    if (data.phone !== undefined) {
      await this.phoneInput().clear();
      await this.phoneInput().fill(data.phone);
    }
    await this.saveProfileButton().click();
  }

  async changePassword(
    currentPassword: string,
    newPassword: string,
    confirmPassword: string
  ): Promise<void> {
    await this.currentPasswordInput().fill(currentPassword);
    await this.newPasswordInput().fill(newPassword);
    await this.confirmPasswordInput().fill(confirmPassword);
    await this.changePasswordButton().click();
  }

  async expectProfileUpdated(): Promise<void> {
    await this.expectSuccessToast();
  }

  async expectPasswordChanged(): Promise<void> {
    await this.expectSuccessToast();
  }

  async expectPasswordError(message: string): Promise<void> {
    await this.expectErrorToast(message);
  }
}
