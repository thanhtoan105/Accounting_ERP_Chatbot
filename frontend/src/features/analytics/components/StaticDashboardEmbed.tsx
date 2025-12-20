import { useTranslation } from 'react-i18next'
import { useCallback, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getEmbeddedDashboardUrl, logAnalyticsEvent } from '../services/analytics'
import { DashboardSkeleton } from './DashboardSkeleton'
import { BiUnavailableFallback } from './BiUnavailableFallback'
import type { AnalyticsEvent } from '../types'

interface StaticDashboardEmbedProps {
  dashboardId: number
  className?: string
}

export function StaticDashboardEmbed({ dashboardId, className = '' }: StaticDashboardEmbedProps) {
  const { t } = useTranslation()
  const [isIframeLoading, setIsIframeLoading] = useState(true)

  const {
    data: embedUrl,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ['analytics', 'embedUrl', dashboardId],
    queryFn: () => getEmbeddedDashboardUrl(dashboardId),
    staleTime: 1000 * 60 * 5,
    retry: 2,
  })

  const handleIframeLoad = useCallback(() => {
    setIsIframeLoading(false)

    const event: AnalyticsEvent = {
      eventType: 'VIEW_LOADED',
      resourceType: 'DASHBOARD',
      resourceId: String(dashboardId),
    }
    logAnalyticsEvent(event).catch(console.error)
  }, [dashboardId])

  const handleRetry = useCallback(() => {
    setIsIframeLoading(true)
    refetch()
  }, [refetch])

  if (isLoading) {
    return <DashboardSkeleton />
  }

  if (isError || !embedUrl?.url) {
    return <BiUnavailableFallback onRetry={handleRetry} />
  }

  return (
    <div
      className={`relative min-h-[600px] ${className}`}
      role="region"
      aria-label={t('analytics.dashboardRegion')}
    >
      {isIframeLoading && (
        <div className="absolute inset-0 z-10">
          <DashboardSkeleton />
        </div>
      )}
      <iframe
        src={embedUrl.url}
        title={t('analytics.dashboardTitle')}
        className="h-full min-h-[600px] w-full border-0"
        onLoad={handleIframeLoad}
        sandbox="allow-scripts allow-same-origin allow-popups allow-forms"
      />
    </div>
  )
}
