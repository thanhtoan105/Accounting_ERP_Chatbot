import { test, expect } from '@playwright/experimental-ct-react';
import { ReceiptAllocationGrid } from '../ReceiptAllocationGrid';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * Receipt Allocation Grid Component Tests
 * 
 * Tests for:
 * - Display open invoices with allocation inputs
 * - Real-time validation (overpayment prevention)
 * - Partial allocation support
 * - Allocation summary (total allocated, unallocated)
 */

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

const mockOpenInvoices = [
  {
    id: 'invoice-001',
    number: 'INV-2025-001',
    date: '2025-01-10',
    dueDate: '2025-02-10',
    totalAmount: 10000000,
    remainingBalance: 10000000,
  },
  {
    id: 'invoice-002',
    number: 'INV-2025-002',
    date: '2025-01-12',
    dueDate: '2025-02-12',
    totalAmount: 8000000,
    remainingBalance: 8000000,
  },
];

test.describe('ReceiptAllocationGrid Component', () => {
  const defaultProps = {
    customerId: 'customer-001',
    receiptAmount: 15000000,
    allocations: [],
    onAllocationsChange: () => {},
    isLoading: false,
  };

  test('AC2: should display open invoices with allocation inputs', async ({ mount, page }) => {
    // GIVEN: Component is mounted with customer ID
    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      });
    });

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptAllocationGrid {...defaultProps} />
      </QueryClientProvider>
    );

    // THEN: All open invoices displayed
    const rows = component.locator('[data-testid="allocation-row"]');
    const count = await rows.count();
    expect(count).toBe(2);

    // Verify invoice details shown
    await expect(
      component.locator('[data-testid="invoice-number"]').first()
    ).toContainText('INV-2025-001');

    await expect(
      component.locator('[data-testid="invoice-remaining-balance"]').first()
    ).toContainText('10,000,000');
  });

  test('AC2: should allow partial allocation to invoice', async ({ mount, page }) => {
    // GIVEN: Allocation grid with open invoices
    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      });
    });

    let allocationsUpdated = false;
    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptAllocationGrid
          {...defaultProps}
          onAllocationsChange={(allocs) => {
            allocationsUpdated = true;
          }}
        />
      </QueryClientProvider>
    );

    // WHEN: User enters partial allocation amount
    const firstAllocationInput = component.locator('[data-testid="allocation-amount-input"]').first();
    await firstAllocationInput.fill('6000000'); // Partial of 10M

    // THEN: Partial allocation recorded
    expect(allocationsUpdated).toBe(true);
    await expect(firstAllocationInput).toHaveValue('6000000');

    // Remaining balance should update
    const remainingDisplay = component.locator('[data-testid="invoice-remaining-after-allocation"]').first();
    await expect(remainingDisplay).toContainText('4,000,000');
  });

  test('AC2: should prevent overpayment allocation', async ({ mount, page }) => {
    // GIVEN: Allocation grid with total receipt of 15M
    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      });
    });

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptAllocationGrid
          {...defaultProps}
          receiptAmount={15000000}
        />
      </QueryClientProvider>
    );

    // WHEN: User attempts to allocate more than invoice balance
    const firstAllocationInput = component.locator('[data-testid="allocation-amount-input"]').first();
    await firstAllocationInput.fill('15000000'); // Exceeds 10M invoice

    // THEN: Overpayment error shown
    const errorMsg = component.locator('[data-testid="overpayment-error"]').first();
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('exceeds remaining');
  });

  test('AC2: should support multiple invoice allocation', async ({ mount, page }) => {
    // GIVEN: Receipt amount covers multiple invoices
    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      });
    });

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptAllocationGrid
          {...defaultProps}
          receiptAmount={18000000}
        />
      </QueryClientProvider>
    );

    // WHEN: User allocates to multiple invoices
    const inputs = component.locator('[data-testid="allocation-amount-input"]');

    // Allocate 10M to first invoice
    await inputs.nth(0).fill('10000000');

    // Allocate 8M to second invoice
    await inputs.nth(1).fill('8000000');

    // THEN: Both allocations recorded
    const allocRows = component.locator('[data-testid="allocation-row"]');
    await expect(allocRows).toHaveCount(2);

    // Verify total allocated
    const totalAllocated = component.locator('[data-testid="total-allocated"]');
    await expect(totalAllocated).toContainText('18,000,000');
  });

  test('should show allocation summary', async ({ mount, page }) => {
    // GIVEN: Allocation grid with partial allocations
    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      });
    });

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptAllocationGrid
          {...defaultProps}
          receiptAmount={15000000}
        />
      </QueryClientProvider>
    );

    // Allocate 10M to first invoice
    await component.locator('[data-testid="allocation-amount-input"]').first().fill('10000000');

    // THEN: Summary shows allocated and unallocated
    const summary = component.locator('[data-testid="allocation-summary"]');
    await expect(summary).toBeVisible();

    const totalAllocated = component.locator('[data-testid="total-allocated"]');
    const unallocated = component.locator('[data-testid="unallocated-amount"]');

    await expect(totalAllocated).toContainText('10,000,000');
    await expect(unallocated).toContainText('5,000,000');
  });

  test('should validate allocation before submission', async ({ mount, page }) => {
    // GIVEN: Allocation grid
    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      });
    });

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptAllocationGrid
          {...defaultProps}
          receiptAmount={10000000}
        />
      </QueryClientProvider>
    );

    // WHEN: No allocations made
    const submitButton = component.locator('[data-testid="submit-allocations-button"]');
    await submitButton.click();

    // THEN: Validation error shown
    const validationError = component.locator('[data-testid="allocation-validation-error"]');
    await expect(validationError).toBeVisible();
  });
});
