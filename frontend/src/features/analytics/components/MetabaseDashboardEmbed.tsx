import { useTranslation } from 'react-i18next'
import { InteractiveDashboard } from '@metabase/embedding-sdk-react'
import { useCallback, useEffect, useState } from 'react'
import { logAnalyticsEvent } from '../services/analytics'
import { DashboardSkeleton } from './DashboardSkeleton'
import { BiUnavailableFallback } from './BiUnavailableFallback'
import { useDrillDown } from '../hooks/useDrillDown'
import type { AnalyticsEvent } from '../types'

interface MetabaseDashboardEmbedProps {
  dashboardId: number
  className?: string
  metabaseOrigin?: string
}

export function MetabaseDashboardEmbed({
  dashboardId,
  className = '',
  metabaseOrigin,
}: MetabaseDashboardEmbedProps) {
  const { t } = useTranslation()
  const [isLoading, setIsLoading] = useState(true)
  const [hasError, setHasError] = useState(false)
  const [retryCount, setRetryCount] = useState(0)

  useDrillDown(metabaseOrigin)

  const handleLoad = useCallback(() => {
    setIsLoading(false)
    setHasError(false)

    const event: AnalyticsEvent = {
      eventType: 'VIEW_LOADED',
      resourceType: 'DASHBOARD',
      resourceId: String(dashboardId),
    }
    logAnalyticsEvent(event).catch(console.error)
  }, [dashboardId])

  const handleRetry = useCallback(() => {
    setRetryCount((prev) => prev + 1)
    setIsLoading(true)
    setHasError(false)
  }, [])

  useEffect(() => {
    setIsLoading(true)
    setHasError(false)
  }, [dashboardId])

  if (hasError) {
    return <BiUnavailableFallback onRetry={handleRetry} />
  }

  return (
    <div
      className={`relative min-h-[600px] ${className}`}
      role="region"
      aria-label={t('analytics.dashboardRegion')}
    >
      {isLoading && (
        <div className="absolute inset-0 z-10">
          <DashboardSkeleton />
        </div>
      )}
      <div className={isLoading ? 'invisible' : 'visible'} style={{ minHeight: '600px' }}>
        <InteractiveDashboard
          key={`dashboard-${dashboardId}-${retryCount}`}
          dashboardId={dashboardId}
          withDownloads
          onLoad={handleLoad}
        />
      </div>
    </div>
  )
}
