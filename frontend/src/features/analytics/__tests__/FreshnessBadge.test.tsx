import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FreshnessBadge } from '../components/FreshnessBadge'
import type { FreshnessStatus } from '../types'

const mockUseDashboardFreshness = vi.fn()
vi.mock('../hooks', () => ({
  useDashboardFreshness: () => mockUseDashboardFreshness(),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'analytics.freshness.loading': 'Loading...',
        'analytics.freshness.unknown': 'Unknown',
        'analytics.freshness.fresh': 'Fresh',
        'analytics.freshness.stale': 'Stale',
        'analytics.freshness.outdated': 'Outdated',
        'analytics.freshness.dataStatus': 'Data Status',
        'analytics.freshness.lastUpdated': 'Last Updated',
        'analytics.freshness.nextRefresh': 'Next Refresh',
      }
      return translations[key] || key
    },
  }),
}))

describe('FreshnessBadge', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('displays loading state', () => {
    mockUseDashboardFreshness.mockReturnValue({
      data: null,
      isLoading: true,
      isError: false,
    })

    render(<FreshnessBadge />)

    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('displays unknown state on error', () => {
    mockUseDashboardFreshness.mockReturnValue({
      data: null,
      isLoading: false,
      isError: true,
    })

    render(<FreshnessBadge />)

    expect(screen.getByText('Unknown')).toBeInTheDocument()
  })

  it('displays unknown state when no data', () => {
    mockUseDashboardFreshness.mockReturnValue({
      data: null,
      isLoading: false,
      isError: false,
    })

    render(<FreshnessBadge />)

    expect(screen.getByText('Unknown')).toBeInTheDocument()
  })

  it('displays GREEN freshness status', () => {
    const freshness: FreshnessStatus = {
      level: 'GREEN',
      lastRefresh: '2024-01-15T10:30:00Z',
      message: 'Data is fresh',
      consecutiveFailures: 0,
      freshnessStatus: 'GREEN',
      lastRefreshTime: '2024-01-15T10:30:00Z',
    }
    mockUseDashboardFreshness.mockReturnValue({
      data: freshness,
      isLoading: false,
      isError: false,
    })

    render(<FreshnessBadge />)

    expect(screen.getByText('Fresh')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', 'Data Status: Fresh')
  })

  it('displays YELLOW freshness status', () => {
    const freshness: FreshnessStatus = {
      level: 'YELLOW',
      lastRefresh: '2024-01-15T10:30:00Z',
      message: 'Data is slightly stale',
      consecutiveFailures: 0,
      freshnessStatus: 'YELLOW',
      lastRefreshTime: '2024-01-15T10:30:00Z',
    }
    mockUseDashboardFreshness.mockReturnValue({
      data: freshness,
      isLoading: false,
      isError: false,
    })

    render(<FreshnessBadge />)

    expect(screen.getByText('Stale')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', 'Data Status: Stale')
  })

  it('displays RED freshness status', () => {
    const freshness: FreshnessStatus = {
      level: 'RED',
      lastRefresh: '2024-01-15T10:30:00Z',
      message: 'Data is outdated',
      consecutiveFailures: 0,
      freshnessStatus: 'RED',
      lastRefreshTime: '2024-01-15T10:30:00Z',
    }
    mockUseDashboardFreshness.mockReturnValue({
      data: freshness,
      isLoading: false,
      isError: false,
    })

    render(<FreshnessBadge />)

    expect(screen.getByText('Outdated')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', 'Data Status: Outdated')
  })

  it('applies custom className', () => {
    const freshness: FreshnessStatus = {
      level: 'GREEN',
      lastRefresh: '2024-01-15T10:30:00Z',
      message: 'Data is fresh',
      consecutiveFailures: 0,
      freshnessStatus: 'GREEN',
      lastRefreshTime: '2024-01-15T10:30:00Z',
    }
    mockUseDashboardFreshness.mockReturnValue({
      data: freshness,
      isLoading: false,
      isError: false,
    })

    render(<FreshnessBadge className="custom-class" />)

    expect(screen.getByRole('status')).toHaveClass('custom-class')
  })
})
