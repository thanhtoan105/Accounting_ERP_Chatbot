import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import PaymentForm from '../PaymentForm'
import * as paymentService from '@/services/payment'
import * as bankAccountService from '@/features/bankaccounts/services/bankAccount'

vi.mock('@/components/payment/PaymentAllocationGrid', () => ({
  PaymentAllocationGrid: ({ allocations }: { allocations: Array<unknown> }) => (
    <div data-testid="allocation-grid">Allocation count: {allocations.length}</div>
  ),
}))

vi.mock('@/components/payment/AccountBalanceDisplay', () => ({
  AccountBalanceDisplay: ({ accountId }: { accountId?: number | null }) => (
    <div data-testid="account-balance-display">{accountId ? `Account ${accountId}` : 'No account'}</div>
  ),
}))

vi.mock('@/components/purchase/SupplierPicker', () => ({
  SupplierPicker: ({ value, onChange }: { value: any; onChange: (supplier: any) => void }) => (
    <button
      type="button"
      onClick={() =>
        onChange?.({
          id: 99,
          name: 'Mock Supplier',
          code: 'MSUP',
        })
      }
    >
      {value ? `Supplier: ${value.name}` : 'Choose Supplier'}
    </button>
  ),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({ user: { id: 1, role: 'accountant' } }),
}))

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => ({ paymentId: 'new' }),
  }
})

vi.mock('@/services/payment')
vi.mock('@/features/bankaccounts/services/bankAccount')

describe('PaymentForm', () => {
  const mockCreatePayment = vi.mocked(paymentService.createPayment)
  const mockAllocateFIFO = vi.mocked(paymentService.allocateFIFO)
  const mockGetOpenBills = vi.mocked(paymentService.getOpenBillsForSupplier)
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
        bankName: 'Main Bank',
        type: 'BANK' as const,
        openingBalance: 20_000_000,
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
    totalPages: 1,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetBankAccounts.mockResolvedValue(bankAccountsResponse)
    mockGetOpenBills.mockResolvedValue([])
    mockNavigate.mockReset()
  })

  it('renders payment information after accounts load', async () => {
    render(<PaymentForm />)

    expect(await screen.findByText(/Payment Information/i)).toBeInTheDocument()
    expect(screen.getByText(/Choose Supplier/i)).toBeInTheDocument()
    expect(mockGetBankAccounts).toHaveBeenCalled()
  })

  it('saves standalone payment without allocations', async () => {
    mockCreatePayment.mockResolvedValue({
      id: 'payment-123',
      paymentNumber: 'PMT-123',
    } as any)

    const user = userEvent.setup()
    render(<PaymentForm />)

    await screen.findByText(/Payment Information/i)

    await user.click(screen.getByText(/Choose Supplier/i))

    await user.click(screen.getByText(/Select cash account/i))
    await waitFor(() => {
      expect(screen.getByText(/CASH-001 • Main Cash/i)).toBeInTheDocument()
    })
    await user.click(screen.getByText(/CASH-001 • Main Cash/i))

    const amountInput = screen.getByPlaceholderText('0.00')
    await user.clear(amountInput)
    await user.type(amountInput, '500000')

    const standaloneSwitch = screen.getByRole('switch')
    await user.click(standaloneSwitch)

    await user.click(screen.getByRole('button', { name: /Save/i }))

    await waitFor(() => {
      expect(mockCreatePayment).toHaveBeenCalledWith(
        expect.objectContaining({
          supplierId: 99,
          cashAccountId: 1,
          amount: 500000,
          isStandalone: true,
          allocations: [],
        }),
      )
    })
    expect(mockNavigate).toHaveBeenCalledWith('/payments/payment-123')
  })

  it('calls allocateFIFO when supplier and amount are provided', async () => {
    mockAllocateFIFO.mockResolvedValue([
      {
        purchaseBillId: 'bill-1',
        purchaseBillNumber: 'BILL-1',
        purchaseBillDate: '2025-01-10',
        purchaseBillDueDate: '2025-01-20',
        purchaseBillTotalAmount: 1_000_000,
        purchaseBillRemainingBalance: 500_000,
        allocatedAmount: 500_000,
        allocationOrder: 1,
      },
    ])

    const user = userEvent.setup()
    render(<PaymentForm />)

    await screen.findByText(/Payment Information/i)

    await user.click(screen.getByText(/Choose Supplier/i))

    const amountInput = screen.getByPlaceholderText('0.00')
    await user.clear(amountInput)
    await user.type(amountInput, '750000')

    const allocateButton = screen.getByRole('button', { name: /Allocate FIFO/i })
    await user.click(allocateButton)

    await waitFor(() => {
      expect(mockAllocateFIFO).toHaveBeenCalledWith(750000, 99)
    })

    await waitFor(() => {
      expect(screen.getByText(/Allocation count: 1/i)).toBeInTheDocument()
    })
  })
})

