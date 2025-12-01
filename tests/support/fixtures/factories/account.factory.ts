import { faker } from '@faker-js/faker';

/**
 * Chart of Accounts Factory
 * Generates test account data with sensible defaults
 * 
 * Pattern: Factory functions with explicit overrides
 * Usage: const account = accountFactory.createAccount({ type: 'ASSET' });
 */

export class AccountFactory {
    private createdAccounts: string[] = [];

    /**
     * Create an account with default values and optional overrides
     */
    createAccount(overrides: Partial<Account> = {}): Account {
        const accountTypes: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'];
        const type = overrides.type || faker.helpers.arrayElement(accountTypes);

        // Generate account code based on type
        const code = overrides.code || this.generateAccountCode(type);

        const account: Account = {
            id: faker.number.int({ min: 1000, max: 9999 }),
            code,
            name: this.generateAccountName(type),
            type,
            parentCode: this.getParentCodeForType(type),
            currency: 'VND',
            active: true,
            createdAt: faker.date.recent().toISOString(),
            ...overrides,
        };

        this.createdAccounts.push(account.code);
        return account;
    }

    /**
     * Create a specific asset account
     */
    createAssetAccount(overrides: Partial<Account> = {}): Account {
        return this.createAccount({ type: 'ASSET', ...overrides });
    }

    /**
     * Create a specific liability account
     */
    createLiabilityAccount(overrides: Partial<Account> = {}): Account {
        return this.createAccount({ type: 'LIABILITY', ...overrides });
    }

    /**
     * Create a specific revenue account
     */
    createRevenueAccount(overrides: Partial<Account> = {}): Account {
        return this.createAccount({ type: 'REVENUE', ...overrides });
    }

    /**
     * Create a specific expense account
     */
    createExpenseAccount(overrides: Partial<Account> = {}): Account {
        return this.createAccount({ type: 'EXPENSE', ...overrides });
    }

    /**
     * Create multiple accounts
     */
    createAccounts(count: number, overrides: Partial<Account> = {}): Account[] {
        return Array.from({ length: count }, () => this.createAccount(overrides));
    }

    /**
     * Generate account code based on type
     */
    private generateAccountCode(type: AccountType): string {
        const prefix = {
            ASSET: '1',
            LIABILITY: '3',
            EQUITY: '4',
            REVENUE: '5',
            EXPENSE: '6',
        }[type];

        // Generate 6-digit code with type prefix
        return prefix + faker.string.numeric(5);
    }

    /**
     * Generate account name based on type
     */
    private generateAccountName(type: AccountType): string {
        const names = {
            ASSET: [
                'Cash in Hand',
                'Bank Account',
                'Accounts Receivable',
                'Inventory',
                'Equipment',
                'Prepaid Expenses',
            ],
            LIABILITY: [
                'Accounts Payable',
                'Accrued Expenses',
                'Loans Payable',
                'Deferred Revenue',
            ],
            EQUITY: ['Share Capital', 'Retained Earnings', 'Current Year Profit'],
            REVENUE: ['Sales Revenue', 'Service Revenue', 'Interest Income', 'Other Income'],
            EXPENSE: [
                'Salaries Expense',
                'Rent Expense',
                'Utilities Expense',
                'Depreciation Expense',
                'Marketing Expense',
            ],
        };

        return faker.helpers.arrayElement(names[type]) + ' - Test';
    }

    /**
     * Get parent account code for account type
     */
    private getParentCodeForType(type: AccountType): string {
        return {
            ASSET: '100',
            LIABILITY: '300',
            EQUITY: '400',
            REVENUE: '500',
            EXPENSE: '600',
        }[type];
    }

    /**
     * Clean up created accounts via API
     */
    async cleanup(apiRequest: (params: { method: 'DELETE'; url: string }) => Promise<void>) {
        for (const code of this.createdAccounts) {
            try {
                await apiRequest({
                    method: 'DELETE',
                    url: `/api/v1/accounts/code/${code}`,
                });
            } catch (error) {
                console.warn(`Failed to cleanup account ${code}:`, error);
            }
        }
        this.createdAccounts = [];
    }

    /**
     * Reset factory without API cleanup (for mocked tests)
     */
    reset() {
        this.createdAccounts = [];
    }
}

type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';

interface Account {
    id: number;
    code: string;
    name: string;
    type: AccountType;
    parentCode?: string;
    currency: string;
    active: boolean;
    createdAt: string;
}
