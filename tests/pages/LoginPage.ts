import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export class LoginPage extends BasePage {
  readonly path = '/login';

  private readonly emailInput = () => this.page.getByTestId('email-input');
  private readonly passwordInput = () => this.page.getByTestId('password-input');
  private readonly loginButton = () => this.page.getByTestId('login-button');
  private readonly forgotPasswordLink = () => this.page.getByRole('link', { name: /forgot password/i });
  private readonly errorAlert = () => this.page.locator('[data-slot="alert"][data-variant="destructive"]');
  private readonly rememberMeCheckbox = () => this.page.locator('#rememberMe');

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.emailInput()).toBeVisible();
    await expect(this.loginButton()).toBeVisible();
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
  }

  async login(email: string, password: string): Promise<void> {
    await this.emailInput().fill(email);
    await this.passwordInput().fill(password);
    await this.loginButton().click();
  }

  async loginWithRememberMe(email: string, password: string): Promise<void> {
    await this.emailInput().fill(email);
    await this.passwordInput().fill(password);
    await this.rememberMeCheckbox().click();
    await this.loginButton().click();
  }

  async expectLoginError(message?: string): Promise<void> {
    await expect(this.errorAlert()).toBeVisible();
    if (message) {
      await expect(this.errorAlert()).toContainText(message);
    }
  }

  async expectAccountLockedError(): Promise<void> {
    await expect(this.errorAlert()).toContainText(/locked/i);
  }

  async expectInvalidCredentialsError(): Promise<void> {
    await expect(this.errorAlert()).toContainText(/invalid|incorrect/i);
  }

  async clickForgotPassword(): Promise<void> {
    await this.forgotPasswordLink().click();
  }

  async expectForgotPasswordPage(): Promise<void> {
    await expect(this.page).toHaveURL(/forgot-password/);
  }

  async togglePasswordVisibility(): Promise<void> {
    await this.page.locator('button[aria-label*="password"]').click();
  }

  async isPasswordVisible(): Promise<boolean> {
    const type = await this.passwordInput().getAttribute('type');
    return type === 'text';
  }
}
