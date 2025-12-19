import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getEmbedConfig,
  getSsoToken,
  listDashboards,
  getWidgetPermissions,
  getWidgetPermissionsByType,
  logAnalyticsEvent,
  getDashboardFreshness,
  triggerManualRefresh,
  getRefreshJobStatus,
  getDashboardForRole,
} from '../services/analytics'
import type {
  MetabaseEmbedConfig,
  DashboardInfo,
  WidgetPermissions,
  WidgetPermissionsByType,
  FreshnessStatus,
  RefreshJobResponse,
  RefreshJobStatus,
  AnalyticsEvent,
  DashboardConfigResponse,
} from '../types'
import { useCallback, useEffect, useRef, useState } from 'react'

export { useDrillDown } from './useDrillDown'
export { useDashboardKPIs } from './useDashboardKPIs'

const ANALYTICS_QUERY_KEYS = {
  embedConfig: (key: string) => ['analytics', 'embedConfig', key] as const,
  ssoToken: ['analytics', 'ssoToken'] as const,
  dashboards: ['analytics', 'dashboards'] as const,
  widgetPermissions: ['analytics', 'widgetPermissions'] as const,
  freshness: ['analytics', 'freshness'] as const,
  jobStatus: (jobId: string) => ['analytics', 'jobStatus', jobId] as const,
}

export function useMetabaseEmbed(dashboardKey: string) {
  const queryClient = useQueryClient()

  const embedConfigQuery = useQuery<MetabaseEmbedConfig>({
    queryKey: ANALYTICS_QUERY_KEYS.embedConfig(dashboardKey),
    queryFn: () => getEmbedConfig(dashboardKey),
    staleTime: 1000 * 60 * 5,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
  })

  const ssoTokenQuery = useQuery({
    queryKey: ANALYTICS_QUERY_KEYS.ssoToken,
    queryFn: getSsoToken,
    staleTime: 1000 * 60 * 45,
    refetchInterval: 1000 * 60 * 45,
    enabled: embedConfigQuery.isSuccess,
  })

  const refreshToken = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ANALYTICS_QUERY_KEYS.ssoToken })
  }, [queryClient])

  const logEventMutation = useMutation({
    mutationFn: (event: AnalyticsEvent) => logAnalyticsEvent(event),
  })

  const logEvent = useCallback(
    (event: AnalyticsEvent) => {
      logEventMutation.mutate(event)
    },
    [logEventMutation],
  )

  return {
    embedConfig: embedConfigQuery.data,
    ssoToken: ssoTokenQuery.data,
    isLoading: embedConfigQuery.isLoading || ssoTokenQuery.isLoading,
    isError: embedConfigQuery.isError || ssoTokenQuery.isError,
    error: embedConfigQuery.error || ssoTokenQuery.error,
    refreshToken,
    logEvent,
  }
}

export function useDashboards() {
  return useQuery<DashboardInfo[]>({
    queryKey: ANALYTICS_QUERY_KEYS.dashboards,
    queryFn: listDashboards,
    staleTime: 1000 * 60 * 10,
  })
}

export function useWidgetPermissions() {
  return useQuery<WidgetPermissions>({
    queryKey: ANALYTICS_QUERY_KEYS.widgetPermissions,
    queryFn: getWidgetPermissions,
    staleTime: 1000 * 60 * 5,
  })
}

export function useWidgetPermissionsByType() {
  return useQuery<WidgetPermissionsByType>({
    queryKey: ['analytics', 'widget-permissions-by-type'],
    queryFn: getWidgetPermissionsByType,
    staleTime: 5 * 60 * 1000,
  })
}

export function useDashboardFreshness(pollInterval: number = 30000) {
  return useQuery<FreshnessStatus>({
    queryKey: ANALYTICS_QUERY_KEYS.freshness,
    queryFn: getDashboardFreshness,
    staleTime: 1000 * 10,
    refetchInterval: pollInterval,
    retry: 2,
    retryDelay: 1000,
  })
}

export function useManualRefresh() {
  const queryClient = useQueryClient()
  const [jobId, setJobId] = useState<string | null>(null)
  const pollingRef = useRef<NodeJS.Timeout | null>(null)

  const triggerMutation = useMutation<RefreshJobResponse>({
    mutationFn: triggerManualRefresh,
    onSuccess: (data) => {
      setJobId(data.jobId)
    },
    onError: () => {
      setJobId(null)
    },
  })

  const jobStatusQuery = useQuery<RefreshJobStatus>({
    queryKey: ANALYTICS_QUERY_KEYS.jobStatus(jobId ?? ''),
    queryFn: () => getRefreshJobStatus(jobId!),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const data = query.state.data
      if (data?.status === 'COMPLETED' || data?.status === 'FAILED') {
        return false
      }
      return 2000
    },
  })

  useEffect(() => {
    if (jobStatusQuery.data?.status === 'COMPLETED') {
      queryClient.invalidateQueries({ queryKey: ANALYTICS_QUERY_KEYS.freshness })
      setJobId(null)
    } else if (jobStatusQuery.data?.status === 'FAILED') {
      setJobId(null)
    }
  }, [jobStatusQuery.data?.status, queryClient])

  useEffect(() => {
    return () => {
      if (pollingRef.current) {
        clearTimeout(pollingRef.current)
      }
    }
  }, [])

  const trigger = useCallback(() => {
    triggerMutation.mutate()
  }, [triggerMutation])

  const isRefreshing =
    triggerMutation.isPending ||
    (!!jobId &&
      jobStatusQuery.data?.status !== 'COMPLETED' &&
      jobStatusQuery.data?.status !== 'FAILED')

  return {
    trigger,
    isRefreshing,
    isError: triggerMutation.isError,
    error: triggerMutation.error,
    jobStatus: jobStatusQuery.data,
    isRateLimited:
      (triggerMutation.error as { response?: { status?: number } })?.response?.status === 429,
  }
}

export function useTokenRefresh(
  embedConfig: MetabaseEmbedConfig | undefined,
  onRefresh: () => void,
) {
  const intervalRef = useRef<NodeJS.Timeout | null>(null)

  useEffect(() => {
    if (!embedConfig) return

    const refreshBeforeMs = embedConfig.refreshBeforeExpiryMinutes * 60 * 1000

    intervalRef.current = setInterval(() => {
      onRefresh()
    }, refreshBeforeMs)

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
      }
    }
  }, [embedConfig, onRefresh])
}

export function useDashboardConfig() {
  return useQuery<DashboardConfigResponse>({
    queryKey: ['analytics', 'dashboard-config'],
    queryFn: getDashboardForRole,
    staleTime: 1000 * 60 * 5,
  })
}
