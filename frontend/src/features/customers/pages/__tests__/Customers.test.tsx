import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import Customers from '../Customers'
import * as customerService from '../../services/customer'

vi.mock('../../services/customer')
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, email: 'admin@example.com', role: 'admin' },
  }),
}))

describe('Customers', () => {
  const mockGetCustomers = vi.mocked(customerService.getCustomers)
  vi.mocked(customerService.deleteCustomer)
  const mockActivateCustomer = vi.mocked(customerService.activateCustomer)
  const mockDeactivateCustomer = vi.mocked(customerService.deactivateCustomer)
  const mockExportCustomers = vi.mocked(customerService.exportCustomers)

  const mockCustomersResponse = {
    data: [
      {
        id: 1,
        companyId: 1,
        code: 'CUST-2025-0001',
        name: 'Customer One',
        taxCode: '1234567890',
        email: 'customer1@example.com',
        phone: '+84123456789',
        address: '123 Main St',
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      },
      {
        id: 2,
        companyId: 1,
        code: 'CUST-2025-0002',
        name: 'Customer Two',
        taxCode: '0987654321',
        email: 'customer2@example.com',
        phone: '+84987654321',
        address: '456 Oak Ave',
        active: false,
        createdAt: '2025-01-02T00:00:00Z',
        updatedAt: '2025-01-02T00:00:00Z',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetCustomers.mockResolvedValue(mockCustomersResponse)
    // Mock window.URL.createObjectURL for export
    global.URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('loads and displays customers', async () => {
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetCustomers).toHaveBeenCalled()
    })

    expect(screen.getByText('Customer One')).toBeInTheDocument()
    expect(screen.getByText('Customer Two')).toBeInTheDocument()
    expect(screen.getByText('CUST-2025-0001')).toBeInTheDocument()
    expect(screen.getByText('CUST-2025-0002')).toBeInTheDocument()
  })

  it('searches customers by name or code', async () => {
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer One')).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText(/search by name or code/i)
    fireEvent.change(searchInput, { target: { value: 'One' } })

    await waitFor(
      () => {
        expect(mockGetCustomers).toHaveBeenCalledWith(
          expect.objectContaining({
            search: 'One',
          }),
        )
      },
      { timeout: 500 },
    )
  })

  it('filters customers by status', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer One')).toBeInTheDocument()
    })

    const statusFilter = screen.getByLabelText(/^status$/i)
    await user.click(statusFilter)

    await waitFor(() => {
      expect(screen.getByText('Active')).toBeInTheDocument()
    })

    const activeOption = screen.getByText('Active')
    await user.click(activeOption)

    await waitFor(() => {
      expect(mockGetCustomers).toHaveBeenCalledWith(
        expect.objectContaining({
          status: true,
        }),
      )
    })
  })

  it('opens create customer dialog', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer One')).toBeInTheDocument()
    })

    const addButton = screen.getByRole('button', { name: /add customer/i })
    await user.click(addButton)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /create customer/i })).toBeInTheDocument()
    })
  })

  it('opens edit customer dialog', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer One')).toBeInTheDocument()
    })

    // Find the row with Customer One
    const rows = screen.getAllByRole('row')
    const customerRow = rows.find((row) => within(row).queryByText('Customer One'))

    expect(customerRow).toBeTruthy()

    // Find the menu trigger button in that row
    const buttons = within(customerRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Edit')).toBeInTheDocument()
    })

    const editMenuItem = screen.getByText('Edit')
    await user.click(editMenuItem)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /edit customer/i })).toBeInTheDocument()
    })
  })

  it('deactivates customer', async () => {
    const user = userEvent.setup()
    mockDeactivateCustomer.mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer One')).toBeInTheDocument()
    })

    // Find the row with Customer One
    const rows = screen.getAllByRole('row')
    const customerRow = rows.find((row) => within(row).queryByText('Customer One'))

    expect(customerRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(customerRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Deactivate')).toBeInTheDocument()
    })

    const deactivateMenuItem = screen.getByText('Deactivate')
    await user.click(deactivateMenuItem)

    await waitFor(() => {
      expect(mockDeactivateCustomer).toHaveBeenCalledWith(1)
    })
  })

  it('activates inactive customer', async () => {
    const user = userEvent.setup()
    mockActivateCustomer.mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer Two')).toBeInTheDocument()
    })

    // Find the row with Customer Two
    const rows = screen.getAllByRole('row')
    const customerRow = rows.find((row) => within(row).queryByText('Customer Two'))

    expect(customerRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(customerRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Activate')).toBeInTheDocument()
    })

    const activateMenuItem = screen.getByText('Activate')
    await user.click(activateMenuItem)

    await waitFor(() => {
      expect(mockActivateCustomer).toHaveBeenCalledWith(2)
    })
  })

  it('displays pagination when multiple pages exist', async () => {
    const paginatedResponse = {
      ...mockCustomersResponse,
      totalPages: 3,
      totalElements: 50,
    }
    mockGetCustomers.mockResolvedValue(paginatedResponse)

    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/total:/i)).toBeInTheDocument()
    })

    expect(screen.getByText('50')).toBeInTheDocument()
    expect(screen.getByText(/customers/i)).toBeInTheDocument()
    expect(screen.getByText(/page 1 \/ 3/i)).toBeInTheDocument()
  })

  it('shows visual indication for inactive customers', async () => {
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer Two')).toBeInTheDocument()
    })

    // Check for inactive badge or status
    expect(screen.getByText('Inactive')).toBeInTheDocument()
  })

  it('refreshes customer list', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetCustomers).toHaveBeenCalled()
    })

    const refreshButton = screen.getByRole('button', { name: /refresh/i })
    await user.click(refreshButton)

    await waitFor(() => {
      expect(mockGetCustomers).toHaveBeenCalledTimes(2)
    })
  })

  it('exports customers', async () => {
    const user = userEvent.setup()
    const mockBlob = new Blob(['mock export data'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    mockExportCustomers.mockResolvedValue(mockBlob)

    render(
      <BrowserRouter>
        <Customers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Customer One')).toBeInTheDocument()
    })

    const exportButton = screen.getByRole('button', { name: /export/i })
    await user.click(exportButton)

    await waitFor(() => {
      expect(mockExportCustomers).toHaveBeenCalled()
    })
  })
})
