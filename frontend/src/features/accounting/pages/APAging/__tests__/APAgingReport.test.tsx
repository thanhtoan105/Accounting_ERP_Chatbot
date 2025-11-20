import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import userEvent from '@testing-library/user-event'

import { APAgingReport } from '../APAgingReport'
import * as apAgingService from '@/services/apAging'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('@/components/ap-aging/AgingBillDetailsDialog', () => ({
  AgingBillDetailsDialog: () => <div data-testid="bill-dialog" />,
}))

vi.mock('@/components/ap-aging/ReminderDialog', () => ({
  ReminderDialog: () => <div data-testid="reminder-dialog" />,
}))

vi.mock('@/components/ui/select', () => {
  const Select = ({ children }: { children: ReactNode }) => <div data-testid="select">{children}</div>
  const SelectTrigger = ({ children }: { children: ReactNode }) => <button>{children}</button>
  const SelectContent = ({ children }: { children: ReactNode }) => <div>{children}</div>
  const SelectItem = ({ children }: { children: ReactNode }) => <button>{children}</button>
  const SelectValue = ({ placeholder }: { placeholder?: string }) => <span>{placeholder}</span>
  return { Select, SelectTrigger, SelectContent, SelectItem, SelectValue }
})

vi.mock('@/services/apAging')

describe('APAgingReport', () => {
  const mockGetAgingReport = vi.mocked(apAgingService.getAgingReport)
  const mockExportAgingReport = vi.mocked(apAgingService.exportAgingReport)

  const sampleBuckets = {
    current: 0,
    days1To30: 1200,
    days31To60: 0,
    days61To90: 0,
    daysOver90: 0,
    total: 1200,
  }

  const sampleResponse = {
    content: [
      {
        supplierId: 42,
        supplierName: 'Supplier Alpha',
        supplierCode: 'ALPHA',
        buckets: sampleBuckets,
        totalOutstanding: 1200,
        hasOverdue: true,
      },
    ],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
    first: true,
    last: true,
  }

  beforeEach(() => {
    mockGetAgingReport.mockResolvedValue(sampleResponse)
    mockExportAgingReport.mockResolvedValue(new Blob(['excel']))
    toast.error.mockReset()
    toast.success.mockReset()
    toast.warning.mockReset()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:ap-aging'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders fetched aging rows', async () => {
    render(<APAgingReport />)

    await waitFor(() => expect(screen.getByText('Supplier Alpha')).toBeInTheDocument())

    expect(mockGetAgingReport).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /Remind/i })).toBeEnabled()
  })

  it('calls export service when exporting Excel', async () => {
    const user = userEvent.setup()
    render(<APAgingReport />)
    await waitFor(() => screen.getByText('Supplier Alpha'))

    await user.click(screen.getByRole('button', { name: /Export Excel/i }))

    expect(mockExportAgingReport).toHaveBeenCalledWith('EXCEL', {
      supplier: undefined,
      period: undefined,
      asOfDate: expect.any(String),
      status: undefined,
      bucket: undefined,
    })
    expect(toast.success).toHaveBeenCalledWith('Aging report exported as EXCEL')
  })

  it('shows error alert when service fails', async () => {
    mockGetAgingReport.mockRejectedValueOnce(new Error('boom'))
    render(<APAgingReport />)

    await waitFor(() => expect(toast.error).toHaveBeenCalled())
    expect(await screen.findByText('boom')).toBeInTheDocument()
  })
})


