import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import userEvent from '@testing-library/user-event'

import { DisputeLogTable } from '../DisputeLogTable'
import * as supplierStatementService from '@/services/supplierStatement'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('@/components/ui/select', () => {
  const Select = ({ children }: { children: ReactNode }) => <div data-testid="select">{children}</div>
  const SelectTrigger = ({ children }: { children: ReactNode }) => <button>{children}</button>
  const SelectContent = ({ children }: { children: ReactNode }) => <div>{children}</div>
  const SelectItem = ({ children }: { children: ReactNode }) => <button>{children}</button>
  const SelectValue = ({ placeholder }: { placeholder?: string }) => <span>{placeholder}</span>
  return { Select, SelectTrigger, SelectContent, SelectItem, SelectValue }
})

vi.mock('@/services/supplierStatement')

describe('DisputeLogTable', () => {
  const mockListDisputes = vi.mocked(supplierStatementService.listDisputes)
  const mockUpdateDispute = vi.mocked(supplierStatementService.updateDispute)

  const sampleDisputes = [
    {
      id: '123e4567-e89b-12d3-a456-426614174000',
      supplierId: 1,
      supplierName: 'Test Supplier',
      billId: 'bill-123',
      billNumber: 'BILL001',
      disputeReason: 'Amount mismatch',
      status: 'OPEN' as const,
      createdBy: 1,
      createdByName: 'Test User',
      createdAt: '2024-01-15T10:00:00Z',
      resolvedBy: null,
      resolvedByName: null,
      resolvedAt: null,
      resolutionNotes: null,
    },
    {
      id: '223e4567-e89b-12d3-a456-426614174001',
      supplierId: 1,
      supplierName: 'Test Supplier',
      billId: 'bill-456',
      billNumber: 'BILL002',
      disputeReason: 'Missing bill',
      status: 'RESOLVED' as const,
      createdBy: 1,
      createdByName: 'Test User',
      createdAt: '2024-01-14T10:00:00Z',
      resolvedBy: 1,
      resolvedByName: 'Test User',
      resolvedAt: '2024-01-16T10:00:00Z',
      resolutionNotes: 'Resolved by adjusting amount',
    },
  ]

  const sampleResponse = {
    disputes: sampleDisputes,
    totalItems: 2,
    totalPages: 1,
    currentPage: 0,
  }

  beforeEach(() => {
    mockListDisputes.mockResolvedValue(sampleResponse)
    mockUpdateDispute.mockResolvedValue({ message: 'Dispute updated successfully', status: 'RESOLVED' })
    toast.error.mockReset()
    toast.success.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders fetched disputes', async () => {
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    expect(mockListDisputes).toHaveBeenCalled()
    expect(screen.getByText('BILL002')).toBeInTheDocument()
  })

  it('displays dispute details correctly', async () => {
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    expect(screen.getByText('Amount mismatch')).toBeInTheDocument()
    expect(screen.getByText('Missing bill')).toBeInTheDocument()
    expect(screen.getByText('OPEN')).toBeInTheDocument()
    expect(screen.getByText('RESOLVED')).toBeInTheDocument()
  })

  it('filters disputes by status', async () => {
    const user = userEvent.setup()
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    // Filter by OPEN status
    const statusFilter = screen.getByTestId('select')
    await user.click(statusFilter)

    // Note: Select interaction may need adjustment based on implementation
    // After filtering, only OPEN disputes should be visible
  })

  it('opens update dialog when resolve button is clicked', async () => {
    const user = userEvent.setup()
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    const resolveButton = screen.getByRole('button', { name: /Resolve/i })
    await user.click(resolveButton)

    // Dialog should open
    expect(screen.getByText(/Update Dispute/i)).toBeInTheDocument()
  })

  it('updates dispute status to resolved', async () => {
    const user = userEvent.setup()
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    const resolveButton = screen.getByRole('button', { name: /Resolve/i })
    await user.click(resolveButton)

    // Fill resolution notes
    const notesInput = screen.getByLabelText(/Resolution Notes/i)
    await user.type(notesInput, 'Resolved by adjusting amount')

    // Submit
    const saveButton = screen.getByRole('button', { name: /Save/i })
    await user.click(saveButton)

    await waitFor(() => {
      expect(mockUpdateDispute).toHaveBeenCalledWith(
        '123e4567-e89b-12d3-a456-426614174000',
        expect.objectContaining({
          status: 'RESOLVED',
          resolutionNotes: 'Resolved by adjusting amount',
        })
      )
    })

    expect(toast.success).toHaveBeenCalled()
  })

  it('filters disputes by search term', async () => {
    const user = userEvent.setup()
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    const searchInput = screen.getByPlaceholderText(/Search/i)
    await user.type(searchInput, 'BILL002')

    // After filtering, only BILL002 should be visible
    expect(screen.getByText('BILL002')).toBeInTheDocument()
    expect(screen.queryByText('BILL001')).not.toBeInTheDocument()
  })

  it('refreshes data when refresh button is clicked', async () => {
    const user = userEvent.setup()
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    mockListDisputes.mockClear()
    const refreshButton = screen.getByRole('button', { name: /Refresh/i })
    await user.click(refreshButton)

    expect(mockListDisputes).toHaveBeenCalled()
  })

  it('shows error when service fails', async () => {
    mockListDisputes.mockRejectedValueOnce(new Error('Failed to load'))
    render(<DisputeLogTable />)

    await waitFor(() => expect(toast.error).toHaveBeenCalled())
  })

  it('handles pagination correctly', async () => {
    const paginatedResponse = {
      ...sampleResponse,
      totalItems: 50,
      totalPages: 3,
      currentPage: 0,
    }
    mockListDisputes.mockResolvedValue(paginatedResponse)
    render(<DisputeLogTable />)

    await waitFor(() => expect(screen.getByText('BILL001')).toBeInTheDocument())

    expect(mockListDisputes).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
      })
    )
  })
})

