import { faker } from '@faker-js/faker';

/**
 * User Factory
 * 
 * Creates test user data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 * 
 * Pattern: Pure function with overrides → API seeding → UI validation
 */
export type User = {
  id?: string;
  email: string;
  name: string;
  password?: string;
  role?: 'user' | 'admin' | 'moderator';
  companyId?: string;
  isActive?: boolean;
  createdAt?: Date;
};

export class UserFactory {
  private createdUsers: string[] = [];

  /**
   * Create a user object with sensible defaults
   * @param overrides - Partial user data to override defaults
   * @returns User object ready for API seeding
   */
  createUser(overrides: Partial<User> = {}): User {
    return {
      id: faker.string.uuid(),
      email: faker.internet.email(),
      name: faker.person.fullName(),
      password: faker.internet.password({ length: 12 }),
      role: 'user',
      isActive: true,
      createdAt: new Date(),
      ...overrides,
    };
  }

  /**
   * Create an admin user (convenience method)
   */
  createAdminUser(overrides: Partial<User> = {}): User {
    return this.createUser({ role: 'admin', ...overrides });
  }

  /**
   * Track a created user ID for cleanup
   */
  trackUser(userId: string): void {
    this.createdUsers.push(userId);
  }

  /**
   * Cleanup all tracked users via API
   * Call this in fixture teardown for automatic cleanup
   */
  async cleanup(apiRequest?: (params: {
    method: 'DELETE';
    url: string;
  }) => Promise<void>): Promise<void> {
    if (!apiRequest) {
      console.warn('UserFactory.cleanup() called without apiRequest - skipping cleanup');
      return;
    }

    for (const userId of this.createdUsers) {
      try {
        await apiRequest({
          method: 'DELETE',
          url: `/api/v1/users/${userId}`,
        });
      } catch (error) {
        console.warn(`Failed to cleanup user ${userId}:`, error);
      }
    }
    this.createdUsers = [];
  }

  /**
   * Get list of tracked user IDs
   */
  getTrackedUsers(): string[] {
    return [...this.createdUsers];
  }
}

