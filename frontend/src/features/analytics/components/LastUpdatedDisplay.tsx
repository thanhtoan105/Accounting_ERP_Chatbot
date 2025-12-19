import { useTranslation } from 'react-i18next'
import { formatDistanceToNow, format } from 'date-fns'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Skeleton } from '@/components/ui/skeleton'
import { Clock } from 'lucide-react'
import { useDashboardFreshness } from '../hooks'

interface LastUpdatedDisplayProps {
  className?: string
}

export function LastUpdatedDisplay({ className = '' }: LastUpdatedDisplayProps) {
  const { t } = useTranslation()
  const { data: freshness, isLoading, isError } = useDashboardFreshness()

  if (isLoading) {
    return <Skeleton className={`h-4 w-32 ${className}`} data-testid="last-updated-skeleton" />
  }

  if (isError || !freshness) {
    return (
      <span
        className={`text-xs text-muted-foreground ${className}`}
        data-testid="last-updated-error"
      >
        {t('analytics.freshness.unknown')}
      </span>
    )
  }

  if (!freshness.lastRefreshTime) {
    return (
      <span
        className={`text-xs text-muted-foreground ${className}`}
        data-testid="last-updated-error"
      >
        {t('analytics.freshness.unknown')}
      </span>
    )
  }

  const lastRefreshDate = new Date(freshness.lastRefreshTime)
  const relativeTime = formatDistanceToNow(lastRefreshDate, { addSuffix: true })
  const exactTime = format(lastRefreshDate, 'PPpp')

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            className={`inline-flex cursor-help items-center gap-1 text-xs text-muted-foreground ${className}`}
            data-testid="last-updated-display"
          >
            <Clock className="h-3 w-3" />
            {t('analytics.freshness.lastUpdated')}: {relativeTime}
          </span>
        </TooltipTrigger>
        <TooltipContent>
          <p>{exactTime}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
