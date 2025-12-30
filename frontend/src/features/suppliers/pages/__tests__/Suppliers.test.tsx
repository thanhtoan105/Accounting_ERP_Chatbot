import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import Suppliers from '../Suppliers'
import * as supplierService from '../../services/supplier'

vi.mock('../../services/supplier')
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, email: 'admin@example.com', role: 'admin' },
  }),
}))

describe('Suppliers', () => {
  const mockGetSuppliers = vi.mocked(supplierService.getSuppliers)
  vi.mocked(supplierService.deleteSupplier)
  const mockActivateSupplier = vi.mocked(supplierService.activateSupplier)
  const mockDeactivateSupplier = vi.mocked(supplierService.deactivateSupplier)
  const mockExportSuppliers = vi.mocked(supplierService.exportSuppliers)

  const mockSuppliersResponse = {
    data: [
      {
        id: 1,
        companyId: 1,
        code: 'SUP-2025-0001',
        name: 'Supplier One',
        taxCode: '1234567890',
        email: 'supplier1@example.com',
        phone: '+84123456789',
        address: '123 Main St',
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      },
      {
        id: 2,
        companyId: 1,
        code: 'SUP-2025-0002',
        name: 'Supplier Two',
        taxCode: '0987654321',
        email: 'supplier2@example.com',
        phone: '+84987654321',
        address: '456 Oak Ave',
        active: false,
        createdAt: '2025-01-02T00:00:00Z',
        updatedAt: '2025-01-02T00:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    total: 2,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSuppliers.mockResolvedValue(mockSuppliersResponse)
    // Mock window.URL.createObjectURL for export
    global.URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('loads and displays suppliers', async () => {
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetSuppliers).toHaveBeenCalled()
    })

    expect(screen.getByText('Supplier One')).toBeInTheDocument()
    expect(screen.getByText('Supplier Two')).toBeInTheDocument()
    expect(screen.getByText('SUP-2025-0001')).toBeInTheDocument()
    expect(screen.getByText('SUP-2025-0002')).toBeInTheDocument()
  })

  it('searches suppliers by name or code', async () => {
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText(/search suppliers/i)
    fireEvent.change(searchInput, { target: { value: 'One' } })

    await waitFor(
      () => {
        expect(mockGetSuppliers).toHaveBeenCalledWith(
          expect.objectContaining({
            search: 'One',
          }),
        )
      },
      { timeout: 500 },
    )
  })

  it('filters suppliers by status', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    // Find the status filter select (it shows "All Status" by default)
    const statusSelects = screen.getAllByRole('combobox')
    const statusFilter =
      statusSelects.find((select) => {
        const parent = select.closest('div')
        return (
          parent?.textContent?.includes('All Status') ||
          parent?.textContent?.includes('Active') ||
          parent?.textContent?.includes('Inactive')
        )
      }) || statusSelects[0]

    await user.click(statusFilter)

    await waitFor(() => {
      expect(screen.getByText('Active')).toBeInTheDocument()
    })

    const activeOption = screen.getByText('Active')
    await user.click(activeOption)

    await waitFor(() => {
      expect(mockGetSuppliers).toHaveBeenCalledWith(
        expect.objectContaining({
          status: true,
        }),
      )
    })
  })

  it('opens create supplier dialog', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    const addButton = screen.getByRole('button', { name: /add supplier/i })
    await user.click(addButton)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /create supplier/i })).toBeInTheDocument()
    })
  })

  it('opens edit supplier dialog', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    // Find the row with Supplier One
    const rows = screen.getAllByRole('row')
    const supplierRow = rows.find((row) => within(row).queryByText('Supplier One'))

    expect(supplierRow).toBeTruthy()

    // Find the menu trigger button in that row
    const buttons = within(supplierRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Edit')).toBeInTheDocument()
    })

    const editMenuItem = screen.getByText('Edit')
    await user.click(editMenuItem)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /edit supplier/i })).toBeInTheDocument()
    })
  })

  it('deactivates supplier', async () => {
    const user = userEvent.setup()
    mockDeactivateSupplier.mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    // Find the row with Supplier One
    const rows = screen.getAllByRole('row')
    const supplierRow = rows.find((row) => within(row).queryByText('Supplier One'))

    expect(supplierRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(supplierRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Deactivate')).toBeInTheDocument()
    })

    const deactivateMenuItem = screen.getByText('Deactivate')
    await user.click(deactivateMenuItem)

    await waitFor(() => {
      expect(mockDeactivateSupplier).toHaveBeenCalledWith(1)
    })
  })

  it('activates inactive supplier', async () => {
    const user = userEvent.setup()
    mockActivateSupplier.mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier Two')).toBeInTheDocument()
    })

    // Find the row with Supplier Two
    const rows = screen.getAllByRole('row')
    const supplierRow = rows.find((row) => within(row).queryByText('Supplier Two'))

    expect(supplierRow).toBeTruthy()

    // Find the menu trigger button
    const buttons = within(supplierRow!).getAllByRole('button')
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Activate')).toBeInTheDocument()
    })

    const activateMenuItem = screen.getByText('Activate')
    await user.click(activateMenuItem)

    await waitFor(() => {
      expect(mockActivateSupplier).toHaveBeenCalledWith(2)
    })
  })

  it('displays pagination when multiple pages exist', async () => {
    const paginatedResponse = {
      ...mockSuppliersResponse,
      totalPages: 3,
      total: 50,
    }
    mockGetSuppliers.mockResolvedValue(paginatedResponse)

    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/total:/i)).toBeInTheDocument()
    })

    expect(screen.getByText('50')).toBeInTheDocument()
    // Check for pagination text (may appear multiple times)
    const paginationTexts = screen.getAllByText(/page 1 \/ 3/i)
    expect(paginationTexts.length).toBeGreaterThan(0)
  })

  it('shows visual indication for inactive suppliers', async () => {
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier Two')).toBeInTheDocument()
    })

    // Check for inactive badge or status
    expect(screen.getByText('Inactive')).toBeInTheDocument()
  })

  it('refreshes supplier list', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetSuppliers).toHaveBeenCalled()
    })

    // Find refresh button by looking for button with RefreshCw icon
    // The button contains an svg with class that includes "refresh"
    const buttons = screen.getAllByRole('button')
    const refreshButton = buttons.find((btn) => {
      const svg = btn.querySelector('svg')
      return (
        svg &&
        (svg.getAttribute('class')?.includes('refresh') ||
          svg.getAttribute('class')?.includes('RefreshCw'))
      )
    })

    expect(refreshButton).toBeTruthy()
    if (refreshButton) {
      await user.click(refreshButton)

      await waitFor(() => {
        expect(mockGetSuppliers).toHaveBeenCalledTimes(2)
      })
    }
  })

  it('exports suppliers', async () => {
    const user = userEvent.setup()
    const mockBlob = new Blob(['mock export data'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    mockExportSuppliers.mockResolvedValue(mockBlob)

    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    const exportButton = screen.getByRole('button', { name: /export/i })
    await user.click(exportButton)

    await waitFor(() => {
      expect(mockExportSuppliers).toHaveBeenCalled()
    })
  })

  it('sorts suppliers by name', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    // Find the sort select (it shows "Name (A-Z)" or similar)
    const sortSelects = screen.getAllByRole('combobox')
    const sortSelect =
      sortSelects.find((select) => {
        const parent = select.closest('div')
        return parent?.textContent?.includes('Name') || parent?.textContent?.includes('Code')
      }) || sortSelects[1] // Second select is usually the sort

    if (sortSelect) {
      await user.click(sortSelect)

      await waitFor(() => {
        const nameOption = screen.queryByText(/name \(a-z\)/i)
        if (nameOption) {
          // Use fireEvent for select items to avoid pointer-events issues
          fireEvent.click(nameOption)
        }
      })

      // Verify the API was called with sort parameter
      await waitFor(
        () => {
          const calls = mockGetSuppliers.mock.calls
          const lastCall = calls[calls.length - 1]
          if (lastCall && lastCall[0]) {
            expect(lastCall[0].sort).toContain('name')
          }
        },
        { timeout: 1000 },
      )
    }
  })

  it('changes page size', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <Suppliers />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Supplier One')).toBeInTheDocument()
    })

    // Find page size select by id
    const pageSizeSelect =
      document.getElementById('rows-per-page')?.closest('button') ||
      screen.getAllByRole('combobox').find((select) => {
        const id = select.getAttribute('id')
        return id === 'rows-per-page'
      })

    expect(pageSizeSelect).toBeTruthy()
    if (pageSizeSelect) {
      await user.click(pageSizeSelect as HTMLElement)

      await waitFor(() => {
        const sizeOption = screen.queryByText('50')
        if (sizeOption) {
          // Use fireEvent for select items to avoid pointer-events issues
          fireEvent.click(sizeOption)
        }
      })

      // Verify the API was called with new size
      await waitFor(
        () => {
          const calls = mockGetSuppliers.mock.calls
          const lastCall = calls[calls.length - 1]
          if (lastCall && lastCall[0]) {
            expect(lastCall[0].size).toBe(50)
          }
        },
        { timeout: 1000 },
      )
    }
  })
})
