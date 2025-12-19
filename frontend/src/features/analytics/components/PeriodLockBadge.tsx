import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Lock } from 'lucide-react'
import { useDashboardFreshness } from '../hooks'

interface PeriodLockBadgeProps {
  className?: string
}

export function PeriodLockBadge({ className = '' }: PeriodLockBadgeProps) {
  const { t } = useTranslation()
  const { data: freshness } = useDashboardFreshness()

  if (!freshness?.periodLocked) {
    return null
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Badge
            variant="destructive"
            className={`cursor-help gap-1.5 ${className}`}
            role="status"
            aria-label={t('analytics.periodLock.locked')}
            data-testid="period-lock-badge"
          >
            <Lock className="h-3 w-3" />
            <span className="text-xs">{t('analytics.periodLock.locked')}</span>
          </Badge>
        </TooltipTrigger>
        <TooltipContent>
          <p>{t('analytics.periodLock.lockedTooltip')}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
