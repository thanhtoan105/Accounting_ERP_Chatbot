import { cn } from '@/lib/utils'
import { TrendingUp, TrendingDown, Minus, AlertTriangle } from 'lucide-react'
import type { VarianceDirection } from '../../types/multiPeriodReport'

interface VarianceIndicatorProps {
  absoluteVariance: number
  percentVariance: number | null
  direction: VarianceDirection
  isMaterial?: boolean
  showAbsolute?: boolean
}

export function VarianceIndicator({
  absoluteVariance,
  percentVariance,
  direction,
  isMaterial,
  showAbsolute = false,
}: VarianceIndicatorProps) {
  const formatNumber = (value: number) => {
    return new Intl.NumberFormat('vi-VN').format(value)
  }

  const formatPercent = (value: number | null) => {
    if (value === null) return 'N/A'
    if (!Number.isFinite(value)) return '∞'
    return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`
  }

  const getIcon = () => {
    if (direction === 'NEUTRAL') {
      return <Minus className="h-3 w-3" />
    }
    if (absoluteVariance > 0) {
      return <TrendingUp className="h-3 w-3" />
    }
    return <TrendingDown className="h-3 w-3" />
  }

  const getColorClass = () => {
    switch (direction) {
      case 'FAVORABLE':
        return 'text-green-600 dark:text-green-400'
      case 'UNFAVORABLE':
        return 'text-red-600 dark:text-red-400'
      case 'NEUTRAL':
      default:
        return 'text-muted-foreground'
    }
  }

  return (
    <div className={cn('flex items-center gap-1 text-xs', getColorClass())}>
      {isMaterial && <AlertTriangle className="h-3 w-3 text-amber-500" />}
      {getIcon()}
      <span className="font-mono">
        {showAbsolute ? formatNumber(absoluteVariance) : formatPercent(percentVariance)}
      </span>
    </div>
  )
}
