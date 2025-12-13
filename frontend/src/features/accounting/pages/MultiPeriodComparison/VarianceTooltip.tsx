import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import type { VarianceDTO } from '../../types/multiPeriodReport'

interface VarianceTooltipProps {
  variances: VarianceDTO[]
  children: React.ReactNode
}

export function VarianceTooltip({ variances, children }: VarianceTooltipProps) {
  if (variances.length === 0) {
    return <>{children}</>
  }

  const formatNumber = (value: number) => {
    return new Intl.NumberFormat('vi-VN').format(value)
  }

  const formatPercent = (value: number | null) => {
    if (value === null) return 'N/A'
    if (!Number.isFinite(value)) return '∞'
    return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`
  }

  const getDirectionLabel = (direction: string) => {
    switch (direction) {
      case 'FAVORABLE':
        return '✓ Favorable'
      case 'UNFAVORABLE':
        return '✗ Unfavorable'
      default:
        return '— Neutral'
    }
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>{children}</TooltipTrigger>
        <TooltipContent side="left" className="max-w-xs">
          <div className="space-y-2 text-xs">
            <p className="font-medium">Period Variances</p>
            {variances.map((v, idx) => (
              <div key={idx} className="flex justify-between gap-4 border-t pt-1">
                <span className="text-muted-foreground">
                  Period {idx + 1} → {idx + 2}
                </span>
                <div className="text-right">
                  <div className="font-mono">
                    {formatNumber(v.absoluteVariance)} ({formatPercent(v.percentVariance)})
                  </div>
                  <div className="text-muted-foreground">{getDirectionLabel(v.direction)}</div>
                </div>
              </div>
            ))}
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
