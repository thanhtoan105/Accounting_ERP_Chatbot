import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DeleteBankAccountDialog from '../DeleteBankAccountDialog'
import * as bankAccountService from '../../services/bankAccount'

vi.mock('../../services/bankAccount')

describe('DeleteBankAccountDialog', () => {
  const mockOnConfirm = vi.fn()
  const mockOnOpenChange = vi.fn()
  const mockDeactivateBankAccount = vi.mocked(bankAccountService.deactivateBankAccount)

  const mockBankAccount = {
    id: 1,
    companyId: 1,
    accountNumber: 'ACC-001',
    bankName: 'Test Bank',
    branch: null,
    type: 'BANK' as const,
    openingBalance: 1000.0,
    active: true,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockDeactivateBankAccount.mockResolvedValue(undefined)
  })

  it('renders dialog when open', () => {
    render(
      <DeleteBankAccountDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        bankAccount={mockBankAccount}
        onConfirm={mockOnConfirm}
      />,
    )

    expect(screen.getByRole('heading', { name: /delete bank account/i })).toBeInTheDocument()
    expect(screen.getByText(/ACC-001 - Test Bank/i)).toBeInTheDocument()
  })

  it('does not render when bank account is null', () => {
    render(
      <DeleteBankAccountDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        bankAccount={null}
        onConfirm={mockOnConfirm}
      />,
    )

    expect(screen.queryByRole('heading', { name: /delete bank account/i })).not.toBeInTheDocument()
  })

  it('calls onConfirm when delete is confirmed', async () => {
    const user = userEvent.setup()
    mockOnConfirm.mockResolvedValue(undefined)

    render(
      <DeleteBankAccountDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        bankAccount={mockBankAccount}
        onConfirm={mockOnConfirm}
      />,
    )

    const deleteButton = screen.getByRole('button', { name: /delete/i })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(mockOnConfirm).toHaveBeenCalled()
    })
  })

  it('shows deactivation option when 409 conflict error occurs', async () => {
    const user = userEvent.setup()
    const error = {
      status: 409,
      error: { message: 'Cannot delete: account is referenced by vouchers' },
    }
    mockOnConfirm.mockRejectedValue(error)

    render(
      <DeleteBankAccountDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        bankAccount={mockBankAccount}
        onConfirm={mockOnConfirm}
      />,
    )

    const deleteButton = screen.getByRole('button', { name: /delete/i })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(screen.getByText(/referenced/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /deactivate instead/i })).toBeInTheDocument()
    })
  })

  it('deactivates account when deactivate button is clicked', async () => {
    const user = userEvent.setup()
    const error = {
      status: 409,
      error: { message: 'Cannot delete: account is referenced by vouchers' },
    }
    mockOnConfirm.mockRejectedValue(error)

    render(
      <DeleteBankAccountDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        bankAccount={mockBankAccount}
        onConfirm={mockOnConfirm}
      />,
    )

    const deleteButton = screen.getByRole('button', { name: /delete/i })
    await user.click(deleteButton)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /deactivate instead/i })).toBeInTheDocument()
    })

    const deactivateButton = screen.getByRole('button', { name: /deactivate instead/i })
    await user.click(deactivateButton)

    await waitFor(() => {
      expect(mockDeactivateBankAccount).toHaveBeenCalledWith(1)
      expect(mockOnOpenChange).toHaveBeenCalledWith(false)
    })
  })

  it('closes dialog on cancel', async () => {
    const user = userEvent.setup()

    render(
      <DeleteBankAccountDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        bankAccount={mockBankAccount}
        onConfirm={mockOnConfirm}
      />,
    )

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await user.click(cancelButton)

    expect(mockOnOpenChange).toHaveBeenCalledWith(false)
    expect(mockOnConfirm).not.toHaveBeenCalled()
  })
})
