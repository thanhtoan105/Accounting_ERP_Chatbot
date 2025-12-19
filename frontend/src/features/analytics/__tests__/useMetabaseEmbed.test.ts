import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useMetabaseEmbed, useDashboardFreshness, useDashboards } from '../hooks'
import type { MetabaseEmbedConfig, FreshnessStatus, DashboardInfo } from '../types'
import { createElement, type ReactNode } from 'react'

const mockGetEmbedConfig = vi.fn()
const mockGetSsoToken = vi.fn()
const mockGetDashboardFreshness = vi.fn()
const mockListDashboards = vi.fn()

vi.mock('../services/analytics', () => ({
  getEmbedConfig: (key: string) => mockGetEmbedConfig(key),
  getSsoToken: () => mockGetSsoToken(),
  getDashboardFreshness: () => mockGetDashboardFreshness(),
  listDashboards: () => mockListDashboards(),
  logAnalyticsEvent: vi.fn(),
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  })

  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children)
  }
}

describe('useMetabaseEmbed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns loading state initially', () => {
    mockGetEmbedConfig.mockReturnValue(new Promise(() => {}))

    const { result } = renderHook(() => useMetabaseEmbed('financial-overview'), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.embedConfig).toBeUndefined()
  })

  it('returns embed config on success', async () => {
    const embedConfig: MetabaseEmbedConfig = {
      metabaseInstanceUrl: 'https://metabase.example.com',
      authEnabled: true,
      authProviderUri: '/api/analytics/sso',
      authType: 'jwt',
      tokenExpiryMinutes: 60,
      refreshBeforeExpiryMinutes: 5,
    }
    mockGetEmbedConfig.mockResolvedValue(embedConfig)
    mockGetSsoToken.mockResolvedValue({ jwt: 'token', expiresInMinutes: 60 })

    const { result } = renderHook(() => useMetabaseEmbed('financial-overview'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.embedConfig).toEqual(embedConfig)
    expect(result.current.isError).toBe(false)
  })

  it('calls getEmbedConfig when dashboardKey changes', async () => {
    mockGetEmbedConfig.mockResolvedValue({
      metabaseInstanceUrl: 'https://metabase.example.com',
      authEnabled: true,
      authProviderUri: '/api/analytics/sso',
      authType: 'jwt',
      tokenExpiryMinutes: 60,
      refreshBeforeExpiryMinutes: 5,
    })
    mockGetSsoToken.mockResolvedValue({ jwt: 'token', expiresInMinutes: 60 })

    renderHook(() => useMetabaseEmbed('financial-overview'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(mockGetEmbedConfig).toHaveBeenCalledWith('financial-overview')
    })
  })

  it('calls getEmbedConfig with correct dashboard key', async () => {
    mockGetEmbedConfig.mockResolvedValue({})
    mockGetSsoToken.mockResolvedValue({ jwt: 'token', expiresInMinutes: 60 })

    renderHook(() => useMetabaseEmbed('ar-dashboard'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(mockGetEmbedConfig).toHaveBeenCalledWith('ar-dashboard')
    })
  })
})

describe('useDashboardFreshness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns freshness data on success', async () => {
    const freshness: FreshnessStatus = {
      level: 'GREEN',
      lastRefresh: '2024-01-15T10:30:00Z',
      message: 'Data is fresh',
      consecutiveFailures: 0,
      freshnessStatus: 'GREEN',
      lastRefreshTime: '2024-01-15T10:30:00Z',
      nextScheduledRefresh: '2024-01-15T11:00:00Z',
    }
    mockGetDashboardFreshness.mockResolvedValue(freshness)

    const { result } = renderHook(() => useDashboardFreshness(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.data).toEqual(freshness)
    })
  })

  it('handles loading state initially', () => {
    mockGetDashboardFreshness.mockReturnValue(new Promise(() => {}))

    const { result } = renderHook(() => useDashboardFreshness(), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
  })
})

describe('useDashboards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns dashboards list on success', async () => {
    const dashboards: DashboardInfo[] = [
      {
        key: 'financial-overview',
        name: 'Financial Overview',
        description: '',
        allowedRoles: [],
        metabaseDashboardId: 1,
      },
      {
        key: 'ar-dashboard',
        name: 'AR Dashboard',
        description: '',
        allowedRoles: [],
        metabaseDashboardId: 2,
      },
    ]
    mockListDashboards.mockResolvedValue(dashboards)

    const { result } = renderHook(() => useDashboards(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.data).toEqual(dashboards)
    })
  })
})
