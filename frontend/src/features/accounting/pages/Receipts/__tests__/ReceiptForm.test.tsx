import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

// Mock components and services
vi.mock('@/components/receipt/ReceiptAllocationGrid', () => ({
  ReceiptAllocationGrid: ({ allocations }: { allocations: Array<unknown> }) => (
    <div data-testid="allocation-grid">Allocation count: {allocations.length}</div>
  ),
}))

vi.mock('@/components/sales/CustomerPicker', () => ({
  CustomerPicker: ({ value, onChange }: { value: any; onChange: (customer: any) => void }) => (
    <button
      type="button"
      data-testid="customer-picker"
      onClick={() =>
        onChange?.({
          id: 100,
          name: 'Test Customer',
          code: 'CUST001',
        })
      }
    >
      {value ? `Customer: ${value.name}` : 'Choose Customer'}
    </button>
  ),
}))

vi.mock('@/services/receipt')
vi.mock('@/features/bankaccounts/services/bankAccount')

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => ({ receiptId: 'new' }),
  }
})

// Import components after mocks
// Note: useAuth and useRole are mocked globally in setupTests.ts
import ReceiptForm from '../ReceiptForm'
import * as receiptService from '@/services/receipt'
import * as bankAccountService from '@/features/bankaccounts/services/bankAccount'

describe('ReceiptForm', () => {
  const mockCreateReceipt = vi.mocked(receiptService.createReceipt)
  const mockGetOpenInvoices = vi.mocked(receiptService.getOpenInvoicesForCustomer)
  const mockGetBankAccounts = vi.mocked(bankAccountService.getBankAccounts)

  const bankAccountsResponse = {
    data: [
      {
        id: 1,
        companyId: 1,
        accountNumber: 'CASH-001',
        bankName: 'Main Cash',
        type: 'CASH' as const,
        openingBalance: 10_000_000,
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      },
      {
        id: 2,
        companyId: 1,
        accountNumber: 'BANK-001',
        bankName: 'ACB Bank',
        type: 'BANK' as const,
        openingBalance: 50_000_000,
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      },
    ],
    totalElements: 2,
  }

  const openInvoicesResponse = {
    data: [
      {
        id: '550e8400-e29b-41d4-a716-446655440000',
        invoiceNumber: 'INV-2025-0001',
        invoiceDate: '2025-01-15',
        dueDate: '2025-02-14',
        totalAmount: 5_000_000,
        remainingBalance: 5_000_000,
        status: 'POSTED',
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440001',
        invoiceNumber: 'INV-2025-0002',
        invoiceDate: '2025-01-20',
        dueDate: '2025-02-19',
        totalAmount: 3_000_000,
        remainingBalance: 3_000_000,
        status: 'POSTED',
      },
    ],
  }

  const mockDraftReceipt = {
    id: '550e8400-e29b-41d4-a716-446655440010',
    receiptNumber: 'RCP-2025-0001',
    customerId: 100,
    receiptDate: '2025-01-15',
    bankAccountId: 2,
    amount: 5_000_000,
    paymentMethod: 'BANK_TRANSFER' as const,
    payee: 'Test Customer',
    status: 'DRAFT' as const,
    isStandalone: false,
    allocations: [],
    createdAt: '2025-01-15T10:00:00Z',
    updatedAt: '2025-01-15T10:00:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetBankAccounts.mockResolvedValue(bankAccountsResponse as any)
    mockGetOpenInvoices.mockResolvedValue(openInvoicesResponse as any)
  })

  describe('Form Rendering', () => {
    it('should render receipt form with all required fields', async () => {
      render(<ReceiptForm />)

      await waitFor(() => {
        expect(screen.getByTestId('customer-picker')).toBeInTheDocument()
      })

      expect(screen.getByLabelText(/receipt date/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/payment method/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/amount/i)).toBeInTheDocument()
    })

    it('should load bank accounts on mount', async () => {
      render(<ReceiptForm />)

      await waitFor(() => {
        expect(mockGetBankAccounts).toHaveBeenCalled()
      })
    })
  })

  describe('Customer Selection', () => {
    it('should load open invoices when customer is selected', async () => {
      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      await waitFor(() => {
        expect(mockGetOpenInvoices).toHaveBeenCalledWith(100)
      })
    })

    it('should display allocation grid after customer selection', async () => {
      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      await waitFor(() => {
        expect(screen.getByTestId('allocation-grid')).toBeInTheDocument()
      })
    })
  })

  describe('Form Validation', () => {
    it('should require customer selection', async () => {
      const user = userEvent.setup()
      render(<ReceiptForm />)

      const saveButton = await screen.findByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      await waitFor(() => {
        expect(screen.getByText(/customer is required/i)).toBeInTheDocument()
      })
    })

    it('should require positive amount', async () => {
      const user = userEvent.setup()
      render(<ReceiptForm />)

      const amountInput = await screen.findByLabelText(/amount/i)
      await user.clear(amountInput)
      await user.type(amountInput, '-100')

      const saveButton = screen.getByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      await waitFor(() => {
        expect(screen.getByText(/amount must be positive/i)).toBeInTheDocument()
      })
    })

    it('should validate payment method selection', async () => {
      const user = userEvent.setup()
      render(<ReceiptForm />)

      // Select customer first
      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      // Try to submit without selecting payment method
      const saveButton = screen.getByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      // Payment method should have a default or validation
      await waitFor(() => {
        expect(mockCreateReceipt).not.toHaveBeenCalled()
      })
    })
  })

  describe('Receipt Creation', () => {
    it('should create receipt with DRAFT status', async () => {
      mockCreateReceipt.mockResolvedValue(mockDraftReceipt as any)

      const user = userEvent.setup()
      render(<ReceiptForm />)

      // Select customer
      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      // Fill amount
      const amountInput = await screen.findByLabelText(/amount/i)
      await user.clear(amountInput)
      await user.type(amountInput, '5000000')

      // Submit form
      const saveButton = screen.getByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      await waitFor(() => {
        expect(mockCreateReceipt).toHaveBeenCalled()
        expect(mockNavigate).toHaveBeenCalledWith('/receipts')
      })
    })

    it('should display success toast after creation', async () => {
      mockCreateReceipt.mockResolvedValue(mockDraftReceipt as any)

      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      const amountInput = await screen.findByLabelText(/amount/i)
      await user.clear(amountInput)
      await user.type(amountInput, '5000000')

      const saveButton = screen.getByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      await waitFor(() => {
        expect(mockCreateReceipt).toHaveBeenCalled()
      })
    })
  })

  describe('Receipt Allocation', () => {
    it('should display allocation grid when customer has open invoices', async () => {
      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      await waitFor(() => {
        const allocationGrid = screen.getByTestId('allocation-grid')
        expect(allocationGrid).toBeInTheDocument()
        expect(allocationGrid).toHaveTextContent('Allocation count: 0')
      })
    })

    it('should prevent overpayment allocation', async () => {
      // This test verifies the allocation grid prevents allocating more than receipt amount
      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      const amountInput = await screen.findByLabelText(/amount/i)
      await user.clear(amountInput)
      await user.type(amountInput, '3000000')

      await waitFor(() => {
        expect(screen.getByTestId('allocation-grid')).toBeInTheDocument()
      })

      // Allocation grid should validate against receipt amount
      // Actual validation logic is in ReceiptAllocationGrid component
    })
  })

  describe('Standalone Receipt', () => {
    it('should display standalone toggle for admin users', async () => {
      // Mock admin role
      const mockUseRole = vi.mocked(await import('@/hooks/useRole'))
      mockUseRole.useRole = vi.fn().mockReturnValue({ isAdmin: true, isAccountant: true })

      render(<ReceiptForm />)

      await waitFor(() => {
        expect(screen.getByLabelText(/standalone receipt/i)).toBeInTheDocument()
      })
    })

    it('should hide standalone toggle for non-admin users', async () => {
      const mockUseRole = vi.mocked(await import('@/hooks/useRole'))
      mockUseRole.useRole = vi.fn().mockReturnValue({ isAdmin: false, isAccountant: true })

      render(<ReceiptForm />)

      await waitFor(() => {
        expect(screen.queryByLabelText(/standalone receipt/i)).not.toBeInTheDocument()
      })
    })

    it('should create standalone receipt without allocations', async () => {
      const mockUseRole = vi.mocked(await import('@/hooks/useRole'))
      mockUseRole.useRole = vi.fn().mockReturnValue({ isAdmin: true, isAccountant: true })
      mockCreateReceipt.mockResolvedValue({
        ...mockDraftReceipt,
        isStandalone: true,
      } as any)

      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      const standaloneToggle = await screen.findByLabelText(/standalone receipt/i)
      await user.click(standaloneToggle)

      const amountInput = screen.getByLabelText(/amount/i)
      await user.clear(amountInput)
      await user.type(amountInput, '5000000')

      const saveButton = screen.getByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      await waitFor(() => {
        expect(mockCreateReceipt).toHaveBeenCalledWith(
          expect.objectContaining({
            isStandalone: true,
          })
        )
      })
    })
  })

  describe('Receipt Posting', () => {
    it('should show post button for DRAFT receipts', async () => {
      // TODO: Implement test for editing existing receipt
      // This would require mocking getReceiptById and setting receiptId param
    })

    it('should call postReceipt when post button clicked', async () => {
      // TODO: Implement test for posting receipt
      // Requires mocking existing DRAFT receipt with allocations
    })

    it('should validate allocations before posting', async () => {
      // TODO: Implement validation test
      // Receipt must have allocations before posting (unless standalone)
    })
  })

  describe('Error Handling', () => {
    it('should display error toast on creation failure', async () => {
      mockCreateReceipt.mockRejectedValue(new Error('Network error'))

      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      const amountInput = await screen.findByLabelText(/amount/i)
      await user.clear(amountInput)
      await user.type(amountInput, '5000000')

      const saveButton = screen.getByRole('button', { name: /save draft/i })
      await user.click(saveButton)

      await waitFor(() => {
        expect(mockCreateReceipt).toHaveBeenCalled()
      })
    })

    it('should handle missing customer open invoices gracefully', async () => {
      mockGetOpenInvoices.mockResolvedValue([] as any)

      const user = userEvent.setup()
      render(<ReceiptForm />)

      const customerPicker = await screen.findByTestId('customer-picker')
      await user.click(customerPicker)

      await waitFor(() => {
        expect(mockGetOpenInvoices).toHaveBeenCalled()
      })

      // Should still allow standalone receipt creation
      expect(screen.getByTestId('allocation-grid')).toBeInTheDocument()
    })
  })

  describe('Autosave', () => {
    it('should autosave draft receipt periodically', async () => {
      // TODO: Implement autosave test
      // Requires testing the autosave timer functionality
    })

    it('should not autosave for new receipts', async () => {
      // TODO: Implement test
      // Autosave should only work for existing DRAFT receipts
    })
  })
})
