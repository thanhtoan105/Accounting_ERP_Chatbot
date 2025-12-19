import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { PeriodSelector } from '../PeriodSelector'
import type { AccountingPeriod, PeriodSummary } from '@/types/accountingPeriod'
import { periodService } from '@/services/period'

// Mock the period service
vi.mock('@/services/period', () => ({
  periodService: {
    getCurrentPeriod: vi.fn(),
    getOpenPeriods: vi.fn(),
    getPeriodSummary: vi.fn(),
  },
}))

// Create typed mock references
const mockedPeriodService = vi.mocked(periodService)

describe('PeriodSelector', () => {
  const mockPeriod: AccountingPeriod = {
    id: 'period-1',
    companyId: 1,
    fiscalYear: 2025,
    periodNumber: 1,
    periodName: 'January 2025',
    startDate: '2025-01-01',
    endDate: '2025-01-31',
    status: 'OPEN',
    statusDisplay: 'Open',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
    version: 1,
  }

  const mockClosedPeriod: AccountingPeriod = {
    ...mockPeriod,
    id: 'period-2',
    periodName: 'December 2024',
    startDate: '2024-12-01',
    endDate: '2024-12-31',
    status: 'CLOSED',
    statusDisplay: 'Closed',
  }

  const mockSummary: PeriodSummary = {
    periodName: 'January 2025',
    status: 'OPEN',
    statusDisplay: 'Open',
    startDate: '2025-01-01',
    endDate: '2025-01-31',
    draftVouchersCount: 5,
    postedVouchersCount: 20,
    hasDraftVouchers: true,
    postingFlowStatus: 'Has Drafts',
    isCurrentPeriod: true,
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let mockGetItem: any
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let mockSetItem: any
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let mockRemoveItem: any

  beforeEach(() => {
    vi.clearAllMocks()
    // Mock localStorage using vi.spyOn
    mockGetItem = vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null)
    mockSetItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {})
    mockRemoveItem = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {})
  })

  afterEach(() => {
    mockGetItem.mockRestore()
    mockSetItem.mockRestore()
    mockRemoveItem.mockRestore()
  })

  it('renders loading state initially', () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(null)

    render(<PeriodSelector onPeriodChange={vi.fn()} />)

    expect(screen.getByTestId('skeleton')).toBeInTheDocument()
  })

  it('renders period selector with periods', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)

    const onPeriodChange = vi.fn()
    render(<PeriodSelector onPeriodChange={onPeriodChange} />)

    await waitFor(() => {
      expect(screen.getByText('Select period...')).toBeInTheDocument()
    })

    // Check that the period selector is rendered
    expect(screen.getByRole('combobox')).toBeInTheDocument()
  })

  it('auto-selects current period when no period is initially selected', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)

    const onPeriodChange = vi.fn()
    render(<PeriodSelector onPeriodChange={onPeriodChange} />)

    await waitFor(() => {
      expect(onPeriodChange).toHaveBeenCalledWith(mockPeriod)
    })
  })

  it('handles period selection change', async () => {
    const periods = [mockPeriod, mockClosedPeriod]
    mockedPeriodService.getOpenPeriods.mockResolvedValue(periods)
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)

    const onPeriodChange = vi.fn()
    render(<PeriodSelector onPeriodChange={onPeriodChange} />)

    await waitFor(() => {
      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })

    // Click to open the dropdown
    fireEvent.click(screen.getByRole('combobox'))

    // Wait for options to appear
    await waitFor(() => {
      expect(screen.getByText('January 2025')).toBeInTheDocument()
      expect(screen.getByText('December 2024')).toBeInTheDocument()
    })

    // Select the second period
    fireEvent.click(screen.getByText('December 2024'))

    expect(onPeriodChange).toHaveBeenCalledWith(mockClosedPeriod)
  })

  it('displays period summary when showSummary is true', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)
    mockedPeriodService.getPeriodSummary.mockResolvedValue(mockSummary)

    render(
      <PeriodSelector selectedPeriod={mockPeriod} onPeriodChange={vi.fn()} showSummary={true} />,
    )

    await waitFor(() => {
      expect(screen.getByText('Has Drafts')).toBeInTheDocument()
      expect(screen.getByText('5 drafts')).toBeInTheDocument()
      expect(screen.getByText('Current')).toBeInTheDocument()
    })
  })

  it('displays error state when API call fails', async () => {
    mockedPeriodService.getOpenPeriods.mockRejectedValue(new Error('API Error'))
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(null)

    render(<PeriodSelector onPeriodChange={vi.fn()} />)

    await waitFor(() => {
      expect(screen.getByText('Failed to load periods')).toBeInTheDocument()
    })
  })

  it('displays "No periods available" when periods array is empty', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(null)

    render(<PeriodSelector onPeriodChange={vi.fn()} />)

    await waitFor(() => {
      expect(screen.getByText('No periods available')).toBeInTheDocument()
    })
  })

  it('persists selected period to localStorage', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(null)

    mockGetItem.mockReturnValue(null)

    const onPeriodChange = vi.fn()
    render(<PeriodSelector onPeriodChange={onPeriodChange} />)

    await waitFor(() => {
      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })

    // Simulate user selecting a period
    fireEvent.click(screen.getByRole('combobox'))
    await waitFor(() => {
      expect(screen.getByText('January 2025')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByText('January 2025'))

    // Check if localStorage was called with the selected period
    await waitFor(() => {
      expect(mockSetItem).toHaveBeenCalledWith(
        expect.stringContaining('selectedPeriod_'),
        expect.stringContaining('January 2025'),
      )
    })
  })

  it('restores persisted period from localStorage', async () => {
    const persistedPeriod = JSON.stringify(mockPeriod)
    mockGetItem.mockImplementation((key: string) => {
      if (key?.includes('selectedPeriod_')) {
        return persistedPeriod
      }
      return null
    })

    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(null)

    const onPeriodChange = vi.fn()
    render(<PeriodSelector onPeriodChange={onPeriodChange} />)

    // The component should restore the persisted period and call onPeriodChange
    await waitFor(() => {
      expect(onPeriodChange).toHaveBeenCalledWith(mockPeriod)
    })
  })

  it('displays period status badges correctly', async () => {
    const periods = [mockPeriod, mockClosedPeriod]
    mockedPeriodService.getOpenPeriods.mockResolvedValue(periods)
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)

    render(<PeriodSelector onPeriodChange={vi.fn()} />)

    await waitFor(() => {
      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })

    // Click to open dropdown and check status badges
    fireEvent.click(screen.getByRole('combobox'))

    await waitFor(() => {
      // Check that status badges are rendered
      const statusElements = screen.getAllByRole('status')
      expect(statusElements.length).toBeGreaterThan(0)
    })
  })

  it('disables selector when disabled prop is true', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)

    render(<PeriodSelector onPeriodChange={vi.fn()} disabled={true} />)

    await waitFor(() => {
      const selector = screen.getByRole('combobox')
      expect(selector).toBeDisabled()
    })
  })

  it('shows custom placeholder when provided', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(null)

    render(<PeriodSelector onPeriodChange={vi.fn()} placeholder="Choose accounting period" />)

    await waitFor(() => {
      expect(screen.getByText('Choose accounting period')).toBeInTheDocument()
    })
  })

  it('applies custom className when provided', async () => {
    mockedPeriodService.getOpenPeriods.mockResolvedValue([mockPeriod])
    mockedPeriodService.getCurrentPeriod.mockResolvedValue(mockPeriod)

    render(<PeriodSelector onPeriodChange={vi.fn()} className="custom-period-selector" />)

    await waitFor(() => {
      const container = screen.getByRole('combobox').closest('.custom-period-selector')
      expect(container).toBeInTheDocument()
    })
  })
})
