import { test, expect } from '../../support/fixtures';
import { ProfilePage } from '../../pages/ProfilePage';

/**
 * User Profile Management Tests
 * Priority: P1 (Core user functionality)
 *
 * Coverage:
 * - USER-005: User profile update
 * - USER-006: Change password (success and validation errors)
 */

test.describe('User Profile Management @chromium-accountant', () => {
  test.use({ storageState: '.auth/accountant.json' });

  let profilePage: ProfilePage;

  test.beforeEach(async ({ page }) => {
    profilePage = new ProfilePage(page);
  });

  test.describe('USER-005: User Profile Update', () => {
    test('[P1] should update user profile successfully', async ({ page }) => {
      // Mock profile update API
      await page.route('**/api/v1/users/profile', async (route) => {
        if (route.request().method() === 'PUT') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              data: {
                id: 1,
                fullName: 'Updated User Name',
                email: 'updated@example.com',
                phone: '0987654321',
              },
            }),
          });
        } else {
          await route.continue();
        }
      });

      // GIVEN: User navigates to profile page
      await profilePage.navigate();

      // WHEN: User updates profile information
      await profilePage.updateProfile({
        name: 'Updated User Name',
        email: 'updated@example.com',
        phone: '0987654321',
      });

      // THEN: Success message is displayed
      await profilePage.expectProfileUpdated();
    });

    test('[P1] should display validation error for invalid email format', async ({ page }) => {
      // GIVEN: User is on profile page
      await profilePage.navigate();

      // WHEN: User enters invalid email
      await profilePage.updateProfile({
        email: 'invalid-email-format',
      });

      // THEN: Email validation error should appear
      const emailInput = page.getByTestId('profile-email-input');
      await expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    });

    test('[P2] should display validation error for empty required fields', async ({ page }) => {
      // GIVEN: User is on profile page
      await profilePage.navigate();

      // WHEN: User clears required name field and submits
      await profilePage.updateProfile({
        name: '',
      });

      // THEN: Required field error should appear
      const nameInput = page.getByTestId('profile-name-input');
      await expect(nameInput).toHaveAttribute('aria-invalid', 'true');
    });
  });

  test.describe('USER-006: Change Password', () => {
    test('[P1] should change password successfully', async ({ page }) => {
      // Mock password change API
      await page.route('**/api/v1/users/password', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Password changed successfully',
          }),
        });
      });

      // GIVEN: User navigates to profile page
      await profilePage.navigate();

      // WHEN: User changes password with valid inputs
      await profilePage.changePassword('CurrentPass123!', 'NewPass456!', 'NewPass456!');

      // THEN: Success message is displayed
      await profilePage.expectPasswordChanged();
    });

    test('[P1] should display error when current password is incorrect', async ({ page }) => {
      // Mock password change API with error
      await page.route('**/api/v1/users/password', async (route) => {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            error: 'Invalid current password',
            message: 'Current password is incorrect',
          }),
        });
      });

      // GIVEN: User navigates to profile page
      await profilePage.navigate();

      // WHEN: User enters wrong current password
      await profilePage.changePassword('WrongPassword123!', 'NewPass456!', 'NewPass456!');

      // THEN: Error message should be displayed
      await profilePage.expectPasswordError('Current password is incorrect');
    });

    test('[P1] should display error when passwords do not match', async ({ page }) => {
      // GIVEN: User navigates to profile page
      await profilePage.navigate();

      // WHEN: User enters mismatched new passwords
      await profilePage.changePassword('CurrentPass123!', 'NewPass456!', 'DifferentPass789!');

      // THEN: Password mismatch error should appear
      const confirmInput = page.getByTestId('confirm-password-input');
      await expect(confirmInput).toHaveAttribute('aria-invalid', 'true');
    });

    test('[P2] should display error for weak password', async ({ page }) => {
      // Mock password change API with weak password error
      await page.route('**/api/v1/users/password', async (route) => {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            error: 'Weak password',
            message: 'Password must be at least 8 characters with uppercase, lowercase, and number',
          }),
        });
      });

      // GIVEN: User navigates to profile page
      await profilePage.navigate();

      // WHEN: User enters weak password
      await profilePage.changePassword('CurrentPass123!', 'weak', 'weak');

      // THEN: Weak password error should be displayed
      await profilePage.expectPasswordError(
        'Password must be at least 8 characters with uppercase, lowercase, and number'
      );
    });
  });
});
