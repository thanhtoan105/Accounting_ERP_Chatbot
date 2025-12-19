import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VoucherHistoryView } from '../VoucherHistoryView'
import { getVoucherHistory, exportVoucherHistory } from '@/services/voucher'
import type { VoucherHistoryResponse } from '@/types/voucher'

// Mock the voucher service
vi.mock('@/services/voucher', () => ({
  getVoucherHistory: vi.fn(),
  exportVoucherHistory: vi.fn(),
}))

// Mock toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('VoucherHistoryView', () => {
  const mockVoucherId = '123e4567-e89b-12d3-a456-426614174000'

  const mockHistoryResponse: VoucherHistoryResponse = {
    voucherId: mockVoucherId,
    history: [
      {
        id: 1,
        action: 'VOUCHER_CREATED',
        timestamp: '2025-01-27T10:00:00Z',
        userId: 1,
        userEmail: 'user@example.com',
        userRole: 'ACCOUNTANT',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla/5.0',
        success: true,
        failureReason: null,
        summary: 'Voucher created by user@example.com on 2025-01-27',
        diff: {
          description: {
            beforeValue: null,
            afterValue: 'Test voucher',
            changeType: 'ADDED',
          },
        },
        diffHash: 'test-hash-123',
        changes: null,
      },
      {
        id: 2,
        action: 'VOUCHER_POSTED',
        timestamp: '2025-01-27T11:00:00Z',
        userId: 2,
        userEmail: 'admin@example.com',
        userRole: 'CHIEF_ACCOUNTANT',
        ipAddress: '192.168.1.2',
        userAgent: 'Mozilla/5.0',
        success: true,
        failureReason: null,
        summary: 'Voucher posted by admin@example.com on 2025-01-27',
        diff: {
          status: {
            beforeValue: 'draft',
            afterValue: 'posted',
            changeType: 'CHANGED',
          },
        },
        diffHash: 'test-hash-456',
        changes: null,
      },
    ],
    count: 2,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.restoreAllMocks()
    vi.mocked(getVoucherHistory).mockResolvedValue(mockHistoryResponse)
  })

  it('renders loading state initially', () => {
    vi.mocked(getVoucherHistory).mockImplementation(
      () => new Promise(() => {}), // Never resolves
    )

    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    expect(screen.getByText(/Lịch sử chứng từ/i)).toBeInTheDocument()
    // Check for Skeleton components - they have data-slot="skeleton" attribute
    const skeletons = document.querySelectorAll('[data-slot="skeleton"]')
    expect(skeletons.length).toBeGreaterThan(0)
  })

  it('displays voucher history entries', async () => {
    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      expect(screen.getByText(/Lịch sử chứng từ \(2\)/i)).toBeInTheDocument()
    })

    expect(screen.getByText(/Voucher created by user@example.com/i)).toBeInTheDocument()
    expect(screen.getByText(/Voucher posted by admin@example.com/i)).toBeInTheDocument()
  })

  it('displays action badges correctly', async () => {
    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      // Check for badges specifically - they have data-slot="badge" attribute
      const createdBadge = screen
        .getAllByText(/CREATED/i)
        .find((el) => el.closest('[data-slot="badge"]') !== null)
      const postedBadge = screen
        .getAllByText(/POSTED/i)
        .find((el) => el.closest('[data-slot="badge"]') !== null)
      expect(createdBadge).toBeDefined()
      expect(postedBadge).toBeDefined()
    })
  })

  it('allows expanding entry to see details', async () => {
    const user = userEvent.setup()
    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      expect(screen.getByText(/Voucher created/i)).toBeInTheDocument()
    })

    // Find and click expand button
    const expandButtons = screen.getAllByRole('button', { name: '' })
    const firstExpandButton = expandButtons.find((btn) => btn.querySelector('svg'))
    expect(firstExpandButton).toBeDefined()

    if (firstExpandButton) {
      await user.click(firstExpandButton)

      // Should show diff details
      await waitFor(() => {
        expect(screen.getByText(/Thay đổi chi tiết/i)).toBeInTheDocument()
      })
    }
  })

  it('filters history by action type', async () => {
    const user = userEvent.setup()
    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      expect(screen.getByText(/Voucher created/i)).toBeInTheDocument()
    })

    // Find and interact with action filter - need to click the SelectTrigger button
    // The SelectTrigger is a button that contains the text
    const actionFilterButton = screen
      .getAllByRole('combobox')
      .find((btn) => btn.textContent?.includes('Tất cả hành động'))
    expect(actionFilterButton).toBeDefined()

    if (actionFilterButton) {
      await user.click(actionFilterButton)

      // Wait for dropdown to open and select VOUCHER_CREATED option
      await waitFor(() => {
        const createdOption = screen.getByRole('option', { name: /CREATED/i })
        expect(createdOption).toBeInTheDocument()
      })

      const createdOption = screen.getByRole('option', { name: /CREATED/i })
      await user.click(createdOption)

      // Verify filtering worked - should only show CREATED entry
      await waitFor(() => {
        expect(screen.getByText(/Voucher created/i)).toBeInTheDocument()
        expect(screen.queryByText(/Voucher posted/i)).not.toBeInTheDocument()
      })
    }
  })

  it('searches history entries', async () => {
    const user = userEvent.setup()
    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      expect(screen.getByText(/Voucher created/i)).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText(/Tìm kiếm/i)
    await user.type(searchInput, 'posted')

    // Should filter to show only posted entry
    await waitFor(() => {
      expect(screen.getByText(/Voucher posted/i)).toBeInTheDocument()
      expect(screen.queryByText(/Voucher created/i)).not.toBeInTheDocument()
    })
  })

  it('exports history as JSON', async () => {
    const user = userEvent.setup()
    const mockBlob = new Blob(['test json'], { type: 'application/json' })
    vi.mocked(exportVoucherHistory).mockResolvedValue(mockBlob)

    // Mock URL.createObjectURL and document.createElement
    const originalCreateObjectURL = global.URL.createObjectURL
    const originalRevokeObjectURL = global.URL.revokeObjectURL
    const originalCreateElement = document.createElement
    const originalAppendChild = document.body.appendChild
    const originalRemoveChild = document.body.removeChild

    global.URL.createObjectURL = vi.fn(() => 'blob:test-url')
    global.URL.revokeObjectURL = vi.fn()
    const mockAnchor = {
      href: '',
      download: '',
      click: vi.fn(),
    }
    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'a') {
        return mockAnchor as any
      }
      return originalCreateElement.call(document, tagName)
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation((node: any) => {
      if (node === mockAnchor) {
        return node
      }
      return originalAppendChild.call(document.body, node)
    })
    vi.spyOn(document.body, 'removeChild').mockImplementation((node: any) => {
      if (node === mockAnchor) {
        return node
      }
      return originalRemoveChild.call(document.body, node)
    })

    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      expect(screen.getByText(/Lịch sử chứng từ/i)).toBeInTheDocument()
    })

    const jsonButton = screen.getByRole('button', { name: /JSON/i })
    await user.click(jsonButton)

    await waitFor(() => {
      expect(exportVoucherHistory).toHaveBeenCalledWith(mockVoucherId, 'json')
    })

    // Restore mocks
    global.URL.createObjectURL = originalCreateObjectURL
    global.URL.revokeObjectURL = originalRevokeObjectURL
    vi.restoreAllMocks()
  })

  it('displays empty state when no history', async () => {
    vi.mocked(getVoucherHistory).mockResolvedValue({
      voucherId: mockVoucherId,
      history: [],
      count: 0,
    })

    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      expect(screen.getByText(/Không có lịch sử nào/i)).toBeInTheDocument()
    })
  })

  it('handles API errors gracefully', async () => {
    vi.mocked(getVoucherHistory).mockRejectedValue(new Error('API Error'))

    render(<VoucherHistoryView voucherId={mockVoucherId} />)

    await waitFor(() => {
      // Should show empty state or error message
      expect(screen.queryByText(/Lịch sử chứng từ/i)).toBeInTheDocument()
    })
  })
})
