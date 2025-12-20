import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { RefreshButton } from '../components/RefreshButton'

const mockUseWidgetPermissions = vi.fn()
const mockUseManualRefresh = vi.fn()

vi.mock('../hooks', () => ({
  useWidgetPermissions: () => mockUseWidgetPermissions(),
  useManualRefresh: () => mockUseManualRefresh(),
}))

const mockToastError = vi.fn()
const mockToastInfo = vi.fn()

vi.mock('sonner', () => ({
  toast: {
    error: (msg: string) => mockToastError(msg),
    info: (msg: string) => mockToastInfo(msg),
  },
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'analytics.refresh.label': 'Refresh Data',
        'analytics.refresh.tooltip': 'Refresh dashboard data',
        'analytics.refresh.inProgress': 'Refreshing...',
        'analytics.refresh.started': 'Refresh started',
        'analytics.refresh.rateLimited': 'Too many requests, please wait',
      }
      return translations[key] || key
    },
  }),
}))

describe('RefreshButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseManualRefresh.mockReturnValue({
      trigger: vi.fn(),
      isRefreshing: false,
      isRateLimited: false,
    })
  })

  it('returns null when user cannot refresh', () => {
    mockUseWidgetPermissions.mockReturnValue({
      data: { canRefresh: false },
    })

    const { container } = render(<RefreshButton />)

    expect(container.firstChild).toBeNull()
  })

  it('renders refresh button when user can refresh', () => {
    mockUseWidgetPermissions.mockReturnValue({
      data: { canRefresh: true },
    })

    render(<RefreshButton />)

    expect(screen.getByRole('button', { name: 'Refresh Data' })).toBeInTheDocument()
  })

  it('calls trigger and shows toast on click', () => {
    const mockTrigger = vi.fn()
    mockUseWidgetPermissions.mockReturnValue({
      data: { canRefresh: true },
    })
    mockUseManualRefresh.mockReturnValue({
      trigger: mockTrigger,
      isRefreshing: false,
      isRateLimited: false,
    })

    render(<RefreshButton />)

    const button = screen.getByRole('button', { name: 'Refresh Data' })
    fireEvent.click(button)

    expect(mockTrigger).toHaveBeenCalledTimes(1)
    expect(mockToastInfo).toHaveBeenCalledWith('Refresh started')
  })

  it('shows rate limit error when rate limited', () => {
    const mockTrigger = vi.fn()
    mockUseWidgetPermissions.mockReturnValue({
      data: { canRefresh: true },
    })
    mockUseManualRefresh.mockReturnValue({
      trigger: mockTrigger,
      isRefreshing: false,
      isRateLimited: true,
    })

    render(<RefreshButton />)

    const button = screen.getByRole('button', { name: 'Refresh Data' })
    fireEvent.click(button)

    expect(mockTrigger).not.toHaveBeenCalled()
    expect(mockToastError).toHaveBeenCalledWith('Too many requests, please wait')
  })

  it('disables button while refreshing', () => {
    mockUseWidgetPermissions.mockReturnValue({
      data: { canRefresh: true },
    })
    mockUseManualRefresh.mockReturnValue({
      trigger: vi.fn(),
      isRefreshing: true,
      isRateLimited: false,
    })

    render(<RefreshButton />)

    const button = screen.getByRole('button', { name: 'Refresh Data' })
    expect(button).toBeDisabled()
  })

  it('applies custom className', () => {
    mockUseWidgetPermissions.mockReturnValue({
      data: { canRefresh: true },
    })

    render(<RefreshButton className="custom-class" />)

    const button = screen.getByRole('button', { name: 'Refresh Data' })
    expect(button).toHaveClass('custom-class')
  })
})
