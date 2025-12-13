import { AlertTriangle } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import type { ComparisonSettingsDTO } from '../../types/multiPeriodReport'

interface MaterialityBadgeProps {
  exceedsThreshold: boolean
  settings?: ComparisonSettingsDTO
}

export function MaterialityBadge({ exceedsThreshold, settings }: MaterialityBadgeProps) {
  if (!exceedsThreshold) {
    return null
  }

  const formatThreshold = () => {
    if (!settings) return 'Exceeds materiality threshold'

    const percent = settings.varianceThresholdPercent
    const absolute = new Intl.NumberFormat('vi-VN').format(settings.varianceThresholdAbsolute)

    return `Exceeds threshold: >${percent}% or >${absolute} VND`
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="inline-flex items-center">
            <AlertTriangle className="h-4 w-4 text-amber-500" />
          </span>
        </TooltipTrigger>
        <TooltipContent>
          <p className="text-xs">{formatThreshold()}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
