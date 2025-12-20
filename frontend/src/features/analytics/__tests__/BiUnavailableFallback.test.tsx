import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import {
  BiUnavailableFallback,
  PerformanceDegradedBanner,
} from '../components/BiUnavailableFallback'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'analytics.error.title': 'Analytics Unavailable',
        'analytics.error.unavailable': 'Unable to load analytics dashboard',
        'analytics.error.tryAgainLater': 'Please try again later',
        'analytics.error.retry': 'Retry',
        'analytics.degraded.title': 'Performance Degraded',
        'analytics.degraded.message': 'Analytics may be slower than usual',
      }
      return translations[key] || key
    },
  }),
}))

describe('BiUnavailableFallback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders error state with default message', () => {
    render(<BiUnavailableFallback />)

    expect(screen.getByText('Analytics Unavailable')).toBeInTheDocument()
    expect(screen.getByText('Unable to load analytics dashboard')).toBeInTheDocument()
    expect(screen.getByText('Please try again later')).toBeInTheDocument()
  })

  it('renders custom error message', () => {
    render(<BiUnavailableFallback message="Custom error message" />)

    expect(screen.getByText('Custom error message')).toBeInTheDocument()
  })

  it('does not show retry button when onRetry is not provided', () => {
    render(<BiUnavailableFallback />)

    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument()
  })

  it('shows retry button when onRetry is provided', () => {
    const onRetry = vi.fn()
    render(<BiUnavailableFallback onRetry={onRetry} />)

    const retryButton = screen.getByRole('button', { name: /retry/i })
    expect(retryButton).toBeInTheDocument()
  })

  it('calls onRetry when retry button is clicked', () => {
    const onRetry = vi.fn()
    render(<BiUnavailableFallback onRetry={onRetry} />)

    const retryButton = screen.getByRole('button', { name: /retry/i })
    fireEvent.click(retryButton)

    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})

describe('PerformanceDegradedBanner', () => {
  it('renders degraded performance warning', () => {
    render(<PerformanceDegradedBanner />)

    expect(screen.getByText('Performance Degraded')).toBeInTheDocument()
    expect(screen.getByText('Analytics may be slower than usual')).toBeInTheDocument()
  })
})
