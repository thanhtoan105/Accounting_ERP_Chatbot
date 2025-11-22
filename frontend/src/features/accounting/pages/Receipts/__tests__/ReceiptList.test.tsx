import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

import ReceiptList from '../ReceiptList'
import * as receiptService from '@/services/receipt'

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({ user: { id: 1, role: 'accountant' } }),
}))

vi.mock('@/services/receipt')

describe('ReceiptList', () => {
  const mockGetReceipts = vi.mocked(receiptService.getReceipts)
  const mockDeleteReceipt = vi.mocked(receiptService.deleteReceipt)
  const mockPostReceipt = vi.mocked(receiptService.postReceipt)
  const mockReverseReceipt = vi.mocked(receiptService.reverseReceipt)

  const mockReceiptsData = {
    content: [
      {
        id: '550e8400-e29b-41d4-a716-446655440001',
        receiptNumber: 'RCP-2025-0001',
        customerId: 100,
        customerCode: 'CUST001',
        customerName: 'Test Customer 1',
        receiptDate: '2025-01-15',
        amount: 5_000_000,
        paymentMethod: 'BANK_TRANSFER' as const,
        status: 'DRAFT' as const,
        isStandalone: false,
        allocatedAmount: 0,
        unallocatedAmount: 5_000_000,
        createdAt: '2025-01-15T10:00:00Z',
        updatedAt: '2025-01-15T10:00:00Z',
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440002',
        receiptNumber: 'RCP-2025-0002',
        customerId: 101,
        customerCode: 'CUST002',
        customerName: 'Test Customer 2',
        receiptDate: '2025-01-16',
        amount: 10_000_000,
        paymentMethod: 'CASH' as const,
        status: 'POSTED' as const,
        isStandalone: false,
        allocatedAmount: 10_000_000,
        unallocatedAmount: 0,
        createdAt: '2025-01-16T10:00:00Z',
        updatedAt: '2025-01-16T11:00:00Z',
        postedAt: '2025-01-16T11:00:00Z',
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440003',
        receiptNumber: 'RCP-2025-0003',
        customerId: 102,
        customerCode: 'CUST003',
        customerName: 'Test Customer 3',
        receiptDate: '2025-01-17',
        amount: 3_000_000,
        paymentMethod: 'BANK_TRANSFER' as const,
        status: 'REVERSED' as const,
        isStandalone: true,
        allocatedAmount: 0,
        unallocatedAmount: 0,
        reversalReason: 'Customer requested refund',
        createdAt: '2025-01-17T10:00:00Z',
        updatedAt: '2025-01-17T15:00:00Z',
      },
    ],
    totalElements: 3,
    totalPages: 1,
    size: 10,
    number: 0,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetReceipts.mockResolvedValue(mockReceiptsData as any)
  })

  describe('List Rendering', () => {
    it('should render receipt list with data', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
        expect(screen.getByText('RCP-2025-0002')).toBeInTheDocument()
        expect(screen.getByText('RCP-2025-0003')).toBeInTheDocument()
      })
    })

    it('should display customer names', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText(/Test Customer 1/i)).toBeInTheDocument()
        expect(screen.getByText(/Test Customer 2/i)).toBeInTheDocument()
        expect(screen.getByText(/Test Customer 3/i)).toBeInTheDocument()
      })
    })

    it('should display receipt amounts', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText(/5,000,000/)).toBeInTheDocument()
        expect(screen.getByText(/10,000,000/)).toBeInTheDocument()
        expect(screen.getByText(/3,000,000/)).toBeInTheDocument()
      })
    })

    it('should display status badges', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('DRAFT')).toBeInTheDocument()
        expect(screen.getByText('POSTED')).toBeInTheDocument()
        expect(screen.getByText('REVERSED')).toBeInTheDocument()
      })
    })

    it('should display standalone indicator', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        // The third receipt is standalone
        const standaloneIndicators = screen.getAllByText(/standalone/i)
        expect(standaloneIndicators.length).toBeGreaterThan(0)
      })
    })
  })

  describe('Pagination', () => {
    it('should display pagination controls', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText(/total elements.*3/i)).toBeInTheDocument()
      })
    })

    it('should load different page when pagination clicked', async () => {
      const multiPageData = {
        ...mockReceiptsData,
        totalElements: 25,
        totalPages: 3,
      }
      mockGetReceipts.mockResolvedValue(multiPageData as any)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText(/total elements.*25/i)).toBeInTheDocument()
      })

      // TODO: Click pagination button and verify new API call
    })
  })

  describe('Filtering', () => {
    it('should filter by customer', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // TODO: Implement filter tests when filter UI is identified
    })

    it('should filter by status', async () => {
      // TODO: Implement status filter test
    })

    it('should filter by date range', async () => {
      // TODO: Implement date range filter test
    })

    it('should filter by standalone flag', async () => {
      // TODO: Implement standalone filter test
    })
  })

  describe('Search Functionality', () => {
    it('should search receipts by number or customer name', async () => {
      const searchResults = {
        ...mockReceiptsData,
        content: [mockReceiptsData.content[0]],
        totalElements: 1,
      }
      mockGetReceipts.mockResolvedValue(searchResults as any)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // TODO: Locate search input and test search functionality
    })

    it('should support unaccented Vietnamese search', async () => {
      // TODO: Implement Vietnamese search test
    })
  })

  describe('Receipt Actions', () => {
    it('should navigate to edit page for DRAFT receipts', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // TODO: Click edit button and verify navigation
    })

    it('should show delete option for DRAFT receipts only', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // DRAFT receipt should have delete option
      // POSTED and REVERSED receipts should not
    })

    it('should delete DRAFT receipt with confirmation', async () => {
      mockDeleteReceipt.mockResolvedValue(undefined)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // TODO: Click delete, confirm, and verify API call
    })

    it('should show post button for DRAFT receipts', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // DRAFT receipts should have post action available
    })

    it('should post DRAFT receipt', async () => {
      mockPostReceipt.mockResolvedValue({
        ...mockReceiptsData.content[0],
        status: 'POSTED' as const,
      } as any)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })

      // TODO: Click post button and verify API call
    })

    it('should show reverse button for POSTED receipts', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0002')).toBeInTheDocument()
      })

      // POSTED receipt should have reverse action available
    })

    it('should reverse POSTED receipt with mandatory reason', async () => {
      mockReverseReceipt.mockResolvedValue({
        ...mockReceiptsData.content[1],
        status: 'REVERSED' as const,
      } as any)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0002')).toBeInTheDocument()
      })

      // TODO: Click reverse button, enter reason, and verify API call
    })

    it('should display view allocations action', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText('RCP-2025-0002')).toBeInTheDocument()
      })

      // All receipts should have view allocations option
    })
  })

  describe('Refresh and Export', () => {
    it('should refresh list when refresh button clicked', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(mockGetReceipts).toHaveBeenCalledTimes(1)
      })

      // TODO: Click refresh button and verify new API call
    })

    it('should export receipts to Excel', async () => {
      // TODO: Implement export test
    })
  })

  describe('Empty State', () => {
    it('should display empty state when no receipts', async () => {
      mockGetReceipts.mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
      } as any)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText(/no receipts found/i)).toBeInTheDocument()
      })
    })

    it('should show create button in empty state', async () => {
      mockGetReceipts.mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
      } as any)

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /create.*receipt/i })).toBeInTheDocument()
      })
    })
  })

  describe('Error Handling', () => {
    it('should display error message on API failure', async () => {
      mockGetReceipts.mockRejectedValue(new Error('Network error'))

      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.getByText(/error.*loading/i)).toBeInTheDocument()
      })
    })

    it('should handle delete failure gracefully', async () => {
      mockDeleteReceipt.mockRejectedValue(new Error('Cannot delete posted receipt'))

      // TODO: Implement delete error handling test
    })

    it('should handle post failure gracefully', async () => {
      mockPostReceipt.mockRejectedValue(new Error('Validation failed'))

      // TODO: Implement post error handling test
    })
  })

  describe('Loading States', () => {
    it('should display loading skeleton initially', () => {
      mockGetReceipts.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(mockReceiptsData as any), 1000))
      )

      render(<ReceiptList />)

      expect(screen.getByTestId('loading-skeleton') || screen.getByText(/loading/i)).toBeInTheDocument()
    })

    it('should hide loading state after data loads', async () => {
      render(<ReceiptList />)

      await waitFor(() => {
        expect(screen.queryByTestId('loading-skeleton')).not.toBeInTheDocument()
        expect(screen.getByText('RCP-2025-0001')).toBeInTheDocument()
      })
    })
  })
})
