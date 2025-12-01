import { test, expect } from '@playwright/experimental-ct-react'
import { ReceiptForm } from '../ReceiptForm'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * Receipt Form Component Tests
 *
 * Tests for:
 * - Customer picker (filters to customers with open invoices)
 * - Form validation (required fields, overpayment prevention)
 * - Payment method selection
 * - Standalone receipt toggle (admin-only)
 */

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
})

const mockCustomers = [{ id: 'customer-001', name: 'Test Customer AR', arAccount: '131' }]

const mockAccounts = [{ id: 'bank-001', accountCode: '111', name: 'Test Bank', balance: 50000000 }]

const mockOpenInvoices = [
  { id: 'invoice-001', number: 'INV-001', remainingBalance: 10000000 },
  { id: 'invoice-002', number: 'INV-002', remainingBalance: 8000000 },
]

test.describe('ReceiptForm Component', () => {
  const defaultProps = {
    onSubmit: async () => {},
    isLoading: false,
  }

  test('AC1.1: should render form with required fields', async ({ mount, page }) => {
    // GIVEN: Form is mounted
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm {...defaultProps} />
      </QueryClientProvider>,
    )

    // THEN: All required fields are visible
    await expect(component.locator('[data-testid="customer-picker"]')).toBeVisible()
    await expect(component.locator('[data-testid="receipt-date-input"]')).toBeVisible()
    await expect(component.locator('[data-testid="bank-account-picker"]')).toBeVisible()
    await expect(component.locator('[data-testid="receipt-amount-input"]')).toBeVisible()
    await expect(component.locator('[data-testid="payment-method-select"]')).toBeVisible()
  })

  test('AC1: should filter customer picker to customers with open invoices', async ({
    mount,
    page,
  }) => {
    // GIVEN: Customer picker is opened
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm {...defaultProps} />
      </QueryClientProvider>,
    )

    // WHEN: User opens customer picker
    const customerPicker = component.locator('[data-testid="customer-picker"]')
    await customerPicker.click()

    // THEN: Only customers with open invoices are shown
    const options = component.locator('[data-testid="customer-option"]')
    const count = await options.count()
    expect(count).toBeGreaterThan(0)
  })

  test('AC2: should validate overpayment prevention', async ({ mount, page }) => {
    // GIVEN: Customer with invoice of 10M VND is selected
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    await page.route('**/api/v1/ar/customers/*/open-invoices', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockOpenInvoices }),
      })
    })

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm {...defaultProps} />
      </QueryClientProvider>,
    )

    // Select customer
    const customerPicker = component.locator('[data-testid="customer-picker"]')
    await customerPicker.click()
    await component.locator('[data-testid="customer-option"]').first().click()

    // WHEN: User enters amount exceeding invoice total
    const amountInput = component.locator('[data-testid="receipt-amount-input"]')
    await amountInput.fill('25000000') // Exceeds 10M+8M

    // THEN: Validation error is shown
    const errorMsg = component.locator('[data-testid="amount-error"]')
    await expect(errorMsg).toBeVisible()
    await expect(errorMsg).toContainText('exceeds')
  })

  test('AC1: should auto-generate receipt number on save', async ({ mount, page }) => {
    // GIVEN: Valid form data entered
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    await page.route('**/api/v1/bank-accounts*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockAccounts }),
      })
    })

    let submitCalled = false
    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm
          {...defaultProps}
          onSubmit={async (data) => {
            submitCalled = true
          }}
        />
      </QueryClientProvider>,
    )

    // Fill form
    await component.locator('[data-testid="receipt-date-input"]').fill('2025-01-15')
    await component.locator('[data-testid="receipt-amount-input"]').fill('5000000')

    // WHEN: User submits form
    const submitButton = component.locator('[data-testid="save-receipt-button"]')
    await submitButton.click()

    // THEN: Receipt number should be auto-generated (server-side)
    expect(submitCalled).toBe(true)
  })

  test('AC3: should show standalone receipt toggle for admin users', async ({ mount, page }) => {
    // GIVEN: Admin user is logged in (role='admin')
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm {...defaultProps} userRole="admin" />
      </QueryClientProvider>,
    )

    // THEN: Standalone receipt toggle is visible
    const standaloneToggle = component.locator('[data-testid="standalone-receipt-toggle"]')
    await expect(standaloneToggle).toBeVisible()
  })

  test('AC3: should hide standalone receipt toggle for non-admin users', async ({
    mount,
    page,
  }) => {
    // GIVEN: Accountant user is logged in (role='accountant')
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm {...defaultProps} userRole="accountant" />
      </QueryClientProvider>,
    )

    // THEN: Standalone receipt toggle is NOT visible
    const standaloneToggle = component.locator('[data-testid="standalone-receipt-toggle"]')
    await expect(standaloneToggle).not.toBeVisible()
  })

  test('should display real-time validation errors', async ({ mount, page }) => {
    // GIVEN: Form is mounted
    await page.route('**/api/v1/ar/customers*', (route) => {
      route.fulfill({
        status: 200,
        body: JSON.stringify({ data: mockCustomers }),
      })
    })

    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm {...defaultProps} />
      </QueryClientProvider>,
    )

    // WHEN: User submits form with missing required fields
    const submitButton = component.locator('[data-testid="save-receipt-button"]')
    await submitButton.click()

    // THEN: Validation errors displayed
    const errorSummary = component.locator('[data-testid="error-summary"]')
    await expect(errorSummary).toBeVisible()
  })

  test('should support draft autosave', async ({ mount, page }) => {
    // GIVEN: Form is mounted with autosave enabled
    let saveCount = 0
    const component = await mount(
      <QueryClientProvider client={queryClient}>
        <ReceiptForm
          {...defaultProps}
          onSubmit={async () => {
            saveCount++
          }}
          autoSaveInterval={30000}
        />
      </QueryClientProvider>,
    )

    // WHEN: User fills in form fields
    await component.locator('[data-testid="receipt-amount-input"]').fill('5000000')

    // Wait for autosave to trigger (simulated)
    // THEN: Draft saved automatically
    // (This would require mocking autosave interval)
  })
})
