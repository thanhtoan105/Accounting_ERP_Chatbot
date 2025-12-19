import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Circle } from 'lucide-react'
import { useDashboardFreshness } from '../hooks'
import type { FreshnessLevel } from '../types'

interface FreshnessBadgeProps {
  className?: string
}

const FRESHNESS_COLORS: Record<FreshnessLevel, string> = {
  GREEN: 'text-green-500',
  YELLOW: 'text-yellow-500',
  RED: 'text-red-500',
}

const FRESHNESS_LABELS: Record<FreshnessLevel, string> = {
  GREEN: 'analytics.freshness.fresh',
  YELLOW: 'analytics.freshness.stale',
  RED: 'analytics.freshness.outdated',
}

export function FreshnessBadge({ className = '' }: FreshnessBadgeProps) {
  const { t } = useTranslation()
  const { data: freshness, isLoading, isError } = useDashboardFreshness()

  if (isLoading) {
    return (
      <Badge variant="outline" className={`gap-1.5 ${className}`}>
        <Circle className="h-2 w-2 animate-pulse text-muted-foreground" />
        <span className="text-xs">{t('analytics.freshness.loading')}</span>
      </Badge>
    )
  }

  if (isError || !freshness) {
    return (
      <Badge variant="outline" className={`gap-1.5 ${className}`}>
        <Circle className="h-2 w-2 text-muted-foreground" />
        <span className="text-xs">{t('analytics.freshness.unknown')}</span>
      </Badge>
    )
  }

  const freshnessLevel = freshness.level ?? freshness.freshnessStatus ?? 'RED'
  const colorClass = FRESHNESS_COLORS[freshnessLevel]
  const label = t(FRESHNESS_LABELS[freshnessLevel])
  const lastUpdateTime = freshness.lastRefresh ?? freshness.lastRefreshTime
  const lastUpdate = lastUpdateTime ? new Date(lastUpdateTime).toLocaleTimeString() : '-'

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Badge
            variant="outline"
            className={`cursor-help gap-1.5 ${className}`}
            role="status"
            aria-label={`${t('analytics.freshness.dataStatus')}: ${label}`}
            data-testid="freshness-badge"
            data-freshness-status={freshnessLevel}
          >
            <Circle className={`h-2 w-2 fill-current ${colorClass}`} />
            <span className="text-xs">{label}</span>
          </Badge>
        </TooltipTrigger>
        <TooltipContent>
          <p>
            {t('analytics.freshness.lastUpdated')}: {lastUpdate}
          </p>
          {freshness.nextScheduledRefresh && (
            <p className="text-xs text-muted-foreground">
              {t('analytics.freshness.nextRefresh')}:{' '}
              {new Date(freshness.nextScheduledRefresh).toLocaleTimeString()}
            </p>
          )}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
