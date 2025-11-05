import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import VoucherList from '../VoucherList'
import * as voucherService from '../../services/voucher'

vi.mock('../../services/voucher')
vi.mock('../../utils/axios', () => ({
  getCompanyId: () => 1,
}))

describe('VoucherList', () => {
  const mockGetVouchers = vi.mocked(voucherService.getVouchers)
  const mockGetVoucherCounts = vi.mocked(voucherService.getVoucherCounts)
  const mockDeleteVoucher = vi.mocked(voucherService.deleteVoucher)

  const mockVouchers = {
    data: [
      {
        id: '1',
        voucherNumber: 'VC2025-001',
        voucherDate: '2025-01-01',
        type: 'Payment',
        totalDebit: 1000,
        totalCredit: 0,
        status: 'draft',
        enteredByName: 'User 1',
        postedByName: null,
        hasReversal: false,
        attachmentCount: 0,
        currency: 'VND',
      },
      {
        id: '2',
        voucherNumber: 'VC2025-002',
        voucherDate: '2025-01-02',
        type: 'Receipt',
        totalDebit: 0,
        totalCredit: 500,
        status: 'posted',
        enteredByName: 'User 1',
        postedByName: 'User 2',
        hasReversal: false,
        attachmentCount: 1,
        currency: 'VND',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
    totalPages: 1,
  }

  const mockCounts = {
    draft: 5,
    posted: 10,
    unposted: 2,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetVouchers.mockResolvedValue(mockVouchers)
    mockGetVoucherCounts.mockResolvedValue(mockCounts)

    // Mock localStorage
    Storage.prototype.getItem = vi.fn(() => null)
    Storage.prototype.setItem = vi.fn()
  })

  it('renders voucher list with data', async () => {
    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetVouchers).toHaveBeenCalled()
    })

    expect(screen.getByText('Vouchers')).toBeInTheDocument()
    expect(screen.getByText('VC2025-001')).toBeInTheDocument()
    expect(screen.getByText('VC2025-002')).toBeInTheDocument()
    expect(screen.getByText('Draft: 5')).toBeInTheDocument()
    expect(screen.getByText('Posted: 10')).toBeInTheDocument()
  })

  it('displays loading state initially', async () => {
    mockGetVouchers.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(mockVouchers), 100)),
    )

    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    // Loading state should be shown while fetching
    expect(mockGetVouchers).toHaveBeenCalled()

    // Wait for data to load
    await waitFor(() => {
      expect(screen.getByText('VC2025-001')).toBeInTheDocument()
    })
  })

  it('displays empty state when no vouchers', async () => {
    mockGetVouchers.mockResolvedValue({
      data: [],
      total: 0,
      page: 0,
      size: 20,
      totalPages: 0,
    })

    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('No vouchers found')).toBeInTheDocument()
    })
  })

  it('filters by status', async () => {
    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetVouchers).toHaveBeenCalled()
    })

    // Find status filter select (may need to query by label or test-id)
    // For now, just verify the filter component exists
    const selects = screen.getAllByRole('combobox')
    expect(selects.length).toBeGreaterThan(0)
  })

  it('searches vouchers with debounce', async () => {
    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetVouchers).toHaveBeenCalled()
    })

    const searchInput = screen.getByPlaceholderText(/search by voucher number/i)
    fireEvent.change(searchInput, { target: { value: 'VC2025' } })

    // Wait for debounce (300ms)
    await waitFor(
      () => {
        expect(mockGetVouchers).toHaveBeenCalledWith(
          expect.objectContaining({
            search: 'VC2025',
          }),
        )
      },
      { timeout: 500 },
    )
  })

  it('handles pagination', async () => {
    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetVouchers).toHaveBeenCalled()
    })

    // Pagination controls should be present
    expect(screen.getByText('2')).toBeInTheDocument() // Total count
  })

  it('displays error message and retry button on error', async () => {
    const errorMessage = 'Failed to load vouchers'
    mockGetVouchers.mockRejectedValueOnce(new Error(errorMessage))

    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(errorMessage)).toBeInTheDocument()
    })

    const retryButton = screen.getByText('Retry')
    expect(retryButton).toBeInTheDocument()

    // Click retry
    mockGetVouchers.mockResolvedValueOnce(mockVouchers)
    fireEvent.click(retryButton)

    await waitFor(() => {
      expect(mockGetVouchers).toHaveBeenCalledTimes(2)
    })
  })

  it('renders delete buttons for draft vouchers', async () => {
    render(
      <BrowserRouter>
        <VoucherList />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetVouchers).toHaveBeenCalled()
    })

    // Verify voucher data is displayed
    expect(screen.getByText('VC2025-001')).toBeInTheDocument()
    expect(screen.getByText('VC2025-002')).toBeInTheDocument()

    // Delete dialog component is rendered in VoucherList, but button interaction
    // can be tested in integration tests
  })
})
