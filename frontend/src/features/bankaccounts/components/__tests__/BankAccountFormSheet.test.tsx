import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import BankAccountFormSheet from '../BankAccountFormSheet'
import * as bankAccountService from '../../services/bankAccount'

vi.mock('../../services/bankAccount')

describe('BankAccountFormSheet', () => {
  const mockCreateBankAccount = vi.mocked(bankAccountService.createBankAccount)
  const mockUpdateBankAccount = vi.mocked(bankAccountService.updateBankAccount)
  const mockGetBankAccountById = vi.mocked(bankAccountService.getBankAccountById)
  const mockOnSuccess = vi.fn()
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Create Mode', () => {
    it('renders dialog when open', () => {
      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )
      expect(screen.getByRole('heading', { name: /add bank account/i })).toBeInTheDocument()
    })

    it('does not render when closed', () => {
      render(
        <BankAccountFormSheet
          open={false}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )
      expect(screen.queryByText('Add Bank Account')).not.toBeInTheDocument()
    })

    it('validates required account number field', async () => {
      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const submitButton = screen.getByRole('button', { name: /create/i })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/account number is required/i)).toBeInTheDocument()
      })
      expect(mockCreateBankAccount).not.toHaveBeenCalled()
    })

    it('validates required bank name field', async () => {
      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const accountNumberInput = screen.getByLabelText(/^account number/i)
      fireEvent.change(accountNumberInput, { target: { value: 'ACC-001' } })

      const submitButton = screen.getByRole('button', { name: /create/i })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/bank name is required/i)).toBeInTheDocument()
      })
      expect(mockCreateBankAccount).not.toHaveBeenCalled()
    })

    it('validates required account type field', async () => {
      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const accountNumberInput = screen.getByLabelText(/^account number/i)
      const bankNameInput = screen.getByLabelText(/^bank name/i)
      const openingBalanceInput = screen.getByLabelText(/^opening balance/i)

      fireEvent.change(accountNumberInput, { target: { value: 'ACC-001' } })
      fireEvent.change(bankNameInput, { target: { value: 'Test Bank' } })
      fireEvent.change(openingBalanceInput, { target: { value: '1000' } })

      const submitButton = screen.getByRole('button', { name: /create/i })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/account type is required/i)).toBeInTheDocument()
      })
      expect(mockCreateBankAccount).not.toHaveBeenCalled()
    })

    it('validates opening balance is non-negative', async () => {
      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const accountNumberInput = screen.getByLabelText(/^account number/i)
      const bankNameInput = screen.getByLabelText(/^bank name/i)
      const openingBalanceInput = screen.getByLabelText(/^opening balance/i)

      fireEvent.change(accountNumberInput, { target: { value: 'ACC-001' } })
      fireEvent.change(bankNameInput, { target: { value: 'Test Bank' } })
      fireEvent.change(openingBalanceInput, { target: { value: '-100' } })

      const submitButton = screen.getByRole('button', { name: /create/i })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/opening balance must be non-negative/i)).toBeInTheDocument()
      })
      expect(mockCreateBankAccount).not.toHaveBeenCalled()
    })

    it('creates bank account with valid data', async () => {
      const user = userEvent.setup()
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
      mockCreateBankAccount.mockResolvedValue(mockBankAccount)

      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const accountNumberInput = screen.getByLabelText(/^account number/i)
      const bankNameInput = screen.getByLabelText(/^bank name/i)
      const openingBalanceInput = screen.getByLabelText(/^opening balance/i)

      await user.type(accountNumberInput, 'ACC-001')
      await user.type(bankNameInput, 'Test Bank')
      await user.type(openingBalanceInput, '1000')

      // Select account type
      const typeSelect = screen.getByRole('combobox', { name: /account type/i })
      await user.click(typeSelect)
      await waitFor(() => {
        expect(screen.getByText('Bank')).toBeInTheDocument()
      })
      await user.click(screen.getByText('Bank'))

      const submitButton = screen.getByRole('button', { name: /create/i })
      await user.click(submitButton)

      await waitFor(() => {
        expect(mockCreateBankAccount).toHaveBeenCalledWith(
          expect.objectContaining({
            accountNumber: 'ACC-001',
            bankName: 'Test Bank',
            type: 'BANK',
            openingBalance: 1000,
          }),
        )
      })
      expect(mockOnSuccess).toHaveBeenCalled()
    })

    it('shows duplicate account number error', async () => {
      const user = userEvent.setup()
      const error = {
        status: 409,
        error: { message: 'Bank account with account number ACC-001 already exists' },
      }
      mockCreateBankAccount.mockRejectedValue(error)

      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const accountNumberInput = screen.getByLabelText(/^account number/i)
      const bankNameInput = screen.getByLabelText(/^bank name/i)
      const openingBalanceInput = screen.getByLabelText(/^opening balance/i)

      await user.type(accountNumberInput, 'ACC-001')
      await user.type(bankNameInput, 'Test Bank')
      await user.type(openingBalanceInput, '1000')

      // Select account type
      const typeSelect = screen.getByRole('combobox', { name: /account type/i })
      await user.click(typeSelect)
      await waitFor(() => {
        expect(screen.getByText('Bank')).toBeInTheDocument()
      })
      await user.click(screen.getByText('Bank'))

      const submitButton = screen.getByRole('button', { name: /create/i })
      await user.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/duplicate detected/i)).toBeInTheDocument()
      })
      expect(mockOnSuccess).not.toHaveBeenCalled()
    })
  })

  describe('Edit Mode', () => {
    const mockBankAccount = {
      id: 1,
      companyId: 1,
      accountNumber: 'ACC-001',
      bankName: 'Original Bank',
      branch: 'Original Branch',
      type: 'BANK' as const,
      openingBalance: 1000.0,
      active: true,
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-01T00:00:00Z',
    }

    it('loads and displays existing bank account data', async () => {
      mockGetBankAccountById.mockResolvedValue(mockBankAccount)

      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          bankAccount={mockBankAccount}
        />,
      )

      await waitFor(() => {
        expect(mockGetBankAccountById).toHaveBeenCalledWith(1)
      })

      expect(screen.getByDisplayValue('Original Bank')).toBeInTheDocument()
      expect(screen.getByDisplayValue('Original Branch')).toBeInTheDocument()
      // Account number should not be editable in edit mode
      expect(screen.queryByLabelText(/^account number/i)).not.toBeInTheDocument()
    })

    it('updates bank account with valid data', async () => {
      const user = userEvent.setup()
      const updatedBankAccount = {
        ...mockBankAccount,
        bankName: 'Updated Bank',
        branch: 'Updated Branch',
      }
      mockGetBankAccountById.mockResolvedValue(mockBankAccount)
      mockUpdateBankAccount.mockResolvedValue(updatedBankAccount)

      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          bankAccount={mockBankAccount}
        />,
      )

      await waitFor(() => {
        expect(screen.getByDisplayValue('Original Bank')).toBeInTheDocument()
      })

      const bankNameInput = screen.getByLabelText(/^bank name/i)
      await user.clear(bankNameInput)
      await user.type(bankNameInput, 'Updated Bank')

      const branchInput = screen.getByLabelText(/^branch/i)
      await user.clear(branchInput)
      await user.type(branchInput, 'Updated Branch')

      const submitButton = screen.getByRole('button', { name: /update/i })
      await user.click(submitButton)

      await waitFor(() => {
        expect(mockUpdateBankAccount).toHaveBeenCalledWith(
          1,
          expect.objectContaining({
            bankName: 'Updated Bank',
            branch: 'Updated Branch',
          }),
        )
      })
      expect(mockOnSuccess).toHaveBeenCalled()
    })

    it('closes dialog on cancel', async () => {
      const user = userEvent.setup()
      mockGetBankAccountById.mockResolvedValue(mockBankAccount)

      render(
        <BankAccountFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          bankAccount={mockBankAccount}
        />,
      )

      await waitFor(() => {
        expect(screen.getByDisplayValue('Original Bank')).toBeInTheDocument()
      })

      const cancelButton = screen.getByRole('button', { name: /cancel/i })
      await user.click(cancelButton)

      expect(mockOnClose).toHaveBeenCalled()
      expect(mockUpdateBankAccount).not.toHaveBeenCalled()
    })
  })
})

