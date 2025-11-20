import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

import { APAgingBadge } from '../APAgingBadge'
import * as apAgingService from '@/services/apAging'

const toast = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))

const mockNavigate = vi.hoisted(() => vi.fn())

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('@/services/apAging')

describe('APAgingBadge', () => {
  const mockGetOverdueCount = vi.mocked(apAgingService.getOverdueCount)
  const mockGetOverdueSuppliers = vi.mocked(apAgingService.getOverdueSuppliers)

  beforeEach(() => {
    mockNavigate.mockReset()
    toast.error.mockReset()
    toast.success.mockReset()
    toast.warning.mockReset()
    mockGetOverdueCount.mockResolvedValue({ count: 2 })
    mockGetOverdueSuppliers.mockResolvedValue([
      {
        supplierId: 1,
        supplierName: 'Supplier Delta',
        overdueAmount: 2500,
        overdueDays: 18,
      },
    ])
  })

  const renderBadge = () =>
    render(
      <MemoryRouter>
        <APAgingBadge autoRefresh={false} />
      </MemoryRouter>,
    )

  it('renders overdue suppliers summary', async () => {
    renderBadge()

    await waitFor(() => expect(screen.getByText('Supplier Delta')).toBeInTheDocument())
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(mockGetOverdueCount).toHaveBeenCalled()
    expect(mockGetOverdueSuppliers).toHaveBeenCalled()
  })

  it('navigates to aging report when clicking View Full Report', async () => {
    const user = userEvent.setup()
    renderBadge()
    await waitFor(() => screen.getByText('Supplier Delta'))

    await user.click(screen.getByRole('button', { name: /View Full Report/i }))
    expect(mockNavigate).toHaveBeenCalledWith('/ap-aging')
  })

  it('shows toast when data fails to load', async () => {
    mockGetOverdueCount.mockRejectedValueOnce(new Error('Network error'))
    renderBadge()

    await waitFor(() => expect(toast.error).toHaveBeenCalled())
  })
})


