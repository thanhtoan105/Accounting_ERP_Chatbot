import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import BankAccounts from '../BankAccounts'
import * as bankAccountService from '../../services/bankAccount'

vi.mock('../../services/bankAccount')
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, email: 'admin@example.com', role: 'admin' },
  }),
}))

describe('BankAccounts', () => {
  const mockGetBankAccounts = vi.mocked(bankAccountService.getBankAccounts)
  const mockDeleteBankAccount = vi.mocked(bankAccountService.deleteBankAccount)
  const mockActivateBankAccount = vi.mocked(bankAccountService.activateBankAccount)
  const mockDeactivateBankAccount = vi.mocked(bankAccountService.deactivateBankAccount)
  const mockExportBankAccounts = vi.mocked(bankAccountService.exportBankAccounts)

  const mockBankAccountsResponse = {
    data: [
      {
        id: 1,
        companyId: 1,
        accountNumber: 'ACC-001',
        bankName: 'Bank One',
        branch: 'Ho Chi Minh City',
        type: 'BANK' as const,
        openingBalance: 10000.0,
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      },
      {
        id: 2,
        companyId: 1,
        accountNumber: 'CASH-001',
        bankName: 'Cash Account',
        branch: null,
        type: 'CASH' as const,
        openingBalance: 5000.0,
        active: false,
        createdAt: '2025-01-02T00:00:00Z',
        updatedAt: '2025-01-02T00:00:00Z',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
    totalPages: 1,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetBankAccounts.mockResolvedValue(mockBankAccountsResponse)
    // Mock window.URL.createObjectURL for export
    global.URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('loads and displays bank accounts', async () => {
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetBankAccounts).toHaveBeenCalled()
    })

    expect(screen.getByText('Bank One')).toBeInTheDocument()
    expect(screen.getByText('Cash Account')).toBeInTheDocument()
    expect(screen.getByText('ACC-001')).toBeInTheDocument()
    expect(screen.getByText('CASH-001')).toBeInTheDocument()
  })

  it('searches bank accounts by account number or bank name', async () => {
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText(/search by account number or bank name/i)
    fireEvent.change(searchInput, { target: { value: 'Bank One' } })

    await waitFor(
      () => {
        expect(mockGetBankAccounts).toHaveBeenCalledWith(
          expect.objectContaining({
            search: 'Bank One',
          }),
        )
      },
      { timeout: 500 },
    )
  })

  it('filters bank accounts by type', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    const typeFilter = screen.getByRole('combobox', { name: /all types/i })
    await user.click(typeFilter)

    await waitFor(() => {
      expect(screen.getByText('Cash')).toBeInTheDocument()
    })

    const cashOption = screen.getByText('Cash')
    await user.click(cashOption)

    await waitFor(() => {
      expect(mockGetBankAccounts).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'CASH',
        }),
      )
    })
  })

  it('filters bank accounts by status', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    const statusFilter = screen.getByRole('combobox', { name: /all status/i })
    await user.click(statusFilter)

    await waitFor(() => {
      expect(screen.getByText('Active')).toBeInTheDocument()
    })

    const activeOption = screen.getByText('Active')
    await user.click(activeOption)

    await waitFor(() => {
      expect(mockGetBankAccounts).toHaveBeenCalledWith(
        expect.objectContaining({
          status: true,
        }),
      )
    })
  })

  it('opens create bank account dialog', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    const addButton = screen.getByRole('button', { name: /add bank account/i })
    await user.click(addButton)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /add bank account/i })).toBeInTheDocument()
    })
  })

  it('opens edit bank account dialog', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    // Find the row with Bank One
    const rows = screen.getAllByRole('row')
    const accountRow = rows.find((row) => within(row).queryByText('Bank One'))

    expect(accountRow).toBeTruthy()

    // Find the menu trigger button in that row
    const buttons = within(accountRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Edit')).toBeInTheDocument()
    })

    const editMenuItem = screen.getByText('Edit')
    await user.click(editMenuItem)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /edit bank account/i })).toBeInTheDocument()
    })
  })

  it('deactivates bank account', async () => {
    const user = userEvent.setup()
    mockDeactivateBankAccount.mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    // Find the row with Bank One
    const rows = screen.getAllByRole('row')
    const accountRow = rows.find((row) => within(row).queryByText('Bank One'))

    expect(accountRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(accountRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Deactivate')).toBeInTheDocument()
    })

    const deactivateMenuItem = screen.getByText('Deactivate')
    await user.click(deactivateMenuItem)

    await waitFor(() => {
      expect(mockDeactivateBankAccount).toHaveBeenCalledWith(1)
    })
  })

  it('activates inactive bank account', async () => {
    const user = userEvent.setup()
    mockActivateBankAccount.mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Cash Account')).toBeInTheDocument()
    })

    // Find the row with Cash Account
    const rows = screen.getAllByRole('row')
    const accountRow = rows.find((row) => within(row).queryByText('Cash Account'))

    expect(accountRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(accountRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Activate')).toBeInTheDocument()
    })

    const activateMenuItem = screen.getByText('Activate')
    await user.click(activateMenuItem)

    await waitFor(() => {
      expect(mockActivateBankAccount).toHaveBeenCalledWith(2)
    })
  })

  it('displays pagination when multiple pages exist', async () => {
    const paginatedResponse = {
      ...mockBankAccountsResponse,
      totalPages: 3,
      total: 50,
    }
    mockGetBankAccounts.mockResolvedValue(paginatedResponse)

    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/showing/i)).toBeInTheDocument()
    })

    expect(screen.getByText(/50/i)).toBeInTheDocument()
    expect(screen.getByText(/bank account/i)).toBeInTheDocument()
    expect(screen.getByText(/page 1/i)).toBeInTheDocument()
    expect(screen.getByText(/3/i)).toBeInTheDocument()
  })

  it('shows visual indication for inactive bank accounts', async () => {
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Cash Account')).toBeInTheDocument()
    })

    // Check for inactive badge or status
    expect(screen.getByText('Inactive')).toBeInTheDocument()
  })

  it('refreshes bank account list', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetBankAccounts).toHaveBeenCalled()
    })

    const refreshButton = screen.getByRole('button', { name: /refresh/i })
    await user.click(refreshButton)

    await waitFor(() => {
      expect(mockGetBankAccounts).toHaveBeenCalledTimes(2)
    })
  })

  it('exports bank accounts', async () => {
    const user = userEvent.setup()
    const mockBlob = new Blob(['mock export data'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    mockExportBankAccounts.mockResolvedValue(mockBlob)

    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    const exportButton = screen.getByRole('button', { name: /export/i })
    await user.click(exportButton)

    await waitFor(() => {
      expect(mockExportBankAccounts).toHaveBeenCalled()
    })
  })

  it('handles delete with 409 conflict error', async () => {
    const user = userEvent.setup()
    const error = {
      status: 409,
      error: { message: 'Cannot delete: account is referenced by vouchers' },
    }
    mockDeleteBankAccount.mockRejectedValue(error)

    render(
      <BrowserRouter>
        <BankAccounts />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Bank One')).toBeInTheDocument()
    })

    // Find the row with Bank One
    const rows = screen.getAllByRole('row')
    const accountRow = rows.find((row) => within(row).queryByText('Bank One'))

    expect(accountRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(accountRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Delete')).toBeInTheDocument()
    })

    const deleteMenuItem = screen.getByText('Delete')
    await user.click(deleteMenuItem)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /delete bank account/i })).toBeInTheDocument()
    })

    const confirmButton = screen.getByRole('button', { name: /delete/i })
    await user.click(confirmButton)

    await waitFor(() => {
      expect(mockDeleteBankAccount).toHaveBeenCalledWith(1)
    })
  })
})

