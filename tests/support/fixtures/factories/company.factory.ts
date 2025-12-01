import { faker } from '@faker-js/faker';

/**
 * Company Factory
 * Generates test company data with sensible defaults
 * 
 * Pattern: Factory functions with explicit overrides
 * Usage: const company = companyFactory.createCompany({ name: 'Custom Name' });
 */

export class CompanyFactory {
    private createdCompanies: number[] = [];

    /**
     * Create a company with default values and optional overrides
     */
    createCompany(overrides: Partial<Company> = {}): Company {
        const company: Company = {
            id: faker.number.int({ min: 1000, max: 9999 }),
            code: faker.string.alphanumeric(6).toUpperCase(),
            name: faker.company.name(),
            taxId: faker.string.numeric(10),
            address: faker.location.streetAddress(),
            phone: faker.phone.number(),
            email: faker.internet.email(),
            currency: 'VND',
            fiscalYearEnd: 12,
            active: true,
            createdAt: faker.date.recent().toISOString(),
            ...overrides,
        };

        this.createdCompanies.push(company.id);
        return company;
    }

    /**
     * Create multiple companies
     */
    createCompanies(count: number, overrides: Partial<Company> = {}): Company[] {
        return Array.from({ length: count }, () => this.createCompany(overrides));
    }

    /**
     * Clean up created companies via API
     */
    async cleanup(apiRequest: (params: { method: 'DELETE'; url: string }) => Promise<void>) {
        for (const id of this.createdCompanies) {
            try {
                await apiRequest({
                    method: 'DELETE',
                    url: `/api/v1/companies/${id}`,
                });
            } catch (error) {
                console.warn(`Failed to cleanup company ${id}:`, error);
            }
        }
        this.createdCompanies = [];
    }

    /**
     * Reset factory without API cleanup (for mocked tests)
     */
    reset() {
        this.createdCompanies = [];
    }
}

interface Company {
    id: number;
    code: string;
    name: string;
    taxId: string;
    address: string;
    phone: string;
    email: string;
    currency: string;
    fiscalYearEnd: number;
    active: boolean;
    createdAt: string;
}
