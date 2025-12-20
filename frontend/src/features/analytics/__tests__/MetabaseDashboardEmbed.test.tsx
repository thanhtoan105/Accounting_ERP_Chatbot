import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MetabaseDashboardEmbed } from '../components/MetabaseDashboardEmbed'

const mockLogAnalyticsEvent = vi.fn()

vi.mock('../services/analytics', () => ({
  logAnalyticsEvent: (event: unknown) => mockLogAnalyticsEvent(event),
}))

vi.mock('@metabase/embedding-sdk-react', () => ({
  InteractiveDashboard: ({ dashboardId, onLoad }: { dashboardId: number; onLoad?: () => void }) => {
    return (
      <div data-testid="interactive-dashboard" data-dashboard-id={dashboardId}>
        <button onClick={onLoad} data-testid="trigger-load">
          Simulate Load
        </button>
      </div>
    )
  },
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'analytics.dashboardRegion': 'Dashboard Region',
        'analytics.error.title': 'Analytics Unavailable',
        'analytics.error.unavailable': 'Unable to load analytics',
        'analytics.error.tryAgainLater': 'Try again later',
        'analytics.error.retry': 'Retry',
      }
      return translations[key] || key
    },
  }),
}))

describe('MetabaseDashboardEmbed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLogAnalyticsEvent.mockResolvedValue({})
  })

  it('renders with loading skeleton initially', () => {
    render(<MetabaseDashboardEmbed dashboardId={1} />)

    expect(screen.getByRole('region', { name: 'Dashboard Region' })).toBeInTheDocument()
    expect(screen.getByTestId('interactive-dashboard')).toBeInTheDocument()
  })

  it('passes correct dashboardId to InteractiveDashboard', () => {
    render(<MetabaseDashboardEmbed dashboardId={42} />)

    const dashboard = screen.getByTestId('interactive-dashboard')
    expect(dashboard).toHaveAttribute('data-dashboard-id', '42')
  })

  it('applies custom className', () => {
    render(<MetabaseDashboardEmbed dashboardId={1} className="custom-class" />)

    const region = screen.getByRole('region', { name: 'Dashboard Region' })
    expect(region).toHaveClass('custom-class')
  })

  it('logs analytics event on load', async () => {
    render(<MetabaseDashboardEmbed dashboardId={5} />)

    const loadButton = screen.getByTestId('trigger-load')
    fireEvent.click(loadButton)

    await waitFor(() => {
      expect(mockLogAnalyticsEvent).toHaveBeenCalledWith({
        eventType: 'VIEW_LOADED',
        resourceType: 'DASHBOARD',
        resourceId: '5',
      })
    })
  })

  it('hides skeleton after load', async () => {
    const { container } = render(<MetabaseDashboardEmbed dashboardId={1} />)

    const loadButton = screen.getByTestId('trigger-load')
    fireEvent.click(loadButton)

    await waitFor(() => {
      const dashboardContainer = container.querySelector('.visible')
      expect(dashboardContainer).toBeInTheDocument()
    })
  })
})
