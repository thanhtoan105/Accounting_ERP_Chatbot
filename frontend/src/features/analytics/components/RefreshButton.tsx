import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { useManualRefresh, useWidgetPermissions, useDashboardFreshness } from '../hooks'

interface RefreshButtonProps {
  className?: string
}

export function RefreshButton({ className = '' }: RefreshButtonProps) {
  const { t } = useTranslation()
  const { data: permissions } = useWidgetPermissions()
  const { data: freshness } = useDashboardFreshness()
  const { trigger, isRefreshing, isRateLimited } = useManualRefresh()

  const canRefresh = permissions?.canRefresh ?? false
  const isPeriodLocked = freshness?.periodLocked === true || freshness?.canManualRefresh === false
  const isDisabled = isRefreshing || isPeriodLocked

  const handleClick = () => {
    if (isRateLimited) {
      toast.error(t('analytics.refresh.rateLimited'))
      return
    }
    trigger()
    toast.info(t('analytics.refresh.started'))
  }

  if (!canRefresh) {
    return null
  }

  const getTooltipMessage = () => {
    if (isPeriodLocked) {
      return t('analytics.periodLock.cannotRefresh')
    }
    if (isRefreshing) {
      return t('analytics.refresh.inProgress')
    }
    return t('analytics.refresh.tooltip')
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="outline"
            size="icon"
            onClick={handleClick}
            disabled={isDisabled}
            className={className}
            aria-label={t('analytics.refresh.label')}
            data-testid="refresh-button"
          >
            <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          <p>{getTooltipMessage()}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
