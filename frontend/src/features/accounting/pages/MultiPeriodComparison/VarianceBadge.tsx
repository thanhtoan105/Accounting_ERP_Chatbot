import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import type { VarianceDirection } from '../../types/multiPeriodReport'

interface VarianceBadgeProps {
  percentVariance: number | null
  direction: VarianceDirection
  className?: string
}

export function VarianceBadge({ percentVariance, direction, className }: VarianceBadgeProps) {
  const formatPercent = (value: number | null) => {
    if (value === null) return 'N/A'
    if (!Number.isFinite(value)) return '∞'
    return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`
  }

  const getColorClass = () => {
    switch (direction) {
      case 'FAVORABLE':
        return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400 border-green-200'
      case 'UNFAVORABLE':
        return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400 border-red-200'
      case 'NEUTRAL':
      default:
        return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400 border-gray-200'
    }
  }

  return (
    <Badge
      variant="outline"
      className={cn('px-1.5 py-0 font-mono text-xs', getColorClass(), className)}
    >
      {formatPercent(percentVariance)}
    </Badge>
  )
}
