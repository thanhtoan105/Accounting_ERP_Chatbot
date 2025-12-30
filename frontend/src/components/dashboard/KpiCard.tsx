import * as React from 'react'
import { ChevronUp, ChevronDown } from 'lucide-react'

import { cn } from '@/lib/utils'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export interface KpiCardProps {
  /** Card title/label */
  title: string
  /** Raw numeric value */
  value: string | number
  /** Pre-formatted display value (e.g., "24.5M ₫") */
  formattedValue?: string
  /** Previous period value for comparison (displayed inline, e.g., "from 22.8M") */
  previousValue?: string
  /** Percentage change from previous period */
  delta?: number
  /** Whether delta is positive, negative, or neutral */
  deltaType?: 'positive' | 'negative' | 'neutral'
  /** Subtitle or context text (e.g., "As of 2025-12-28") */
  subtitle?: string
  /** Click handler for drill-down */
  onClick?: () => void
  /** Additional CSS classes */
  className?: string
  /** Slot for sparkline or additional content */
  children?: React.ReactNode
  /** Loading state */
  isLoading?: boolean
  /** Show "View more →" link */
  showViewMore?: boolean
}

const deltaTextColors = {
  positive: 'text-emerald-700 dark:text-emerald-500',
  negative: 'text-red-700 dark:text-red-500',
  neutral: 'text-gray-500 dark:text-gray-400',
} as const

function KpiCard({
  title,
  value,
  formattedValue,
  previousValue,
  delta,
  deltaType = 'neutral',
  subtitle,
  onClick,
  className,
  children,
  isLoading = false,
  showViewMore = false,
}: KpiCardProps) {
  if (isLoading) {
    return <KpiCardSkeleton className={className} />
  }

  return (
    <Card
      className={cn(
        'relative overflow-hidden rounded-xl border-0 bg-white p-6 dark:bg-gray-950',
        'ring-1 ring-gray-200 dark:ring-gray-800',
        'shadow-sm transition-all duration-200',
        onClick && 'cursor-pointer hover:ring-gray-300 hover:shadow-md dark:hover:ring-gray-700',
        className,
      )}
      onClick={onClick}
    >
      {/* Title */}
      <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{title}</p>

      {/* Value Row */}
      <div className="mt-2 flex items-baseline gap-2">
        <p className="text-3xl font-semibold tracking-tight text-gray-900 dark:text-gray-50">
          {formattedValue ?? value}
        </p>
        {previousValue && (
          <span className="text-sm text-gray-500 dark:text-gray-400">{previousValue}</span>
        )}
      </div>

      {/* Delta Row */}
      {delta !== undefined && (
        <div className="mt-3 flex items-center gap-1">
          {deltaType === 'positive' && (
            <span className="flex size-5 items-center justify-center rounded bg-emerald-100 dark:bg-emerald-900/30">
              <ChevronUp
                className="size-4 text-emerald-700 dark:text-emerald-500"
                strokeWidth={2.5}
                aria-hidden="true"
              />
            </span>
          )}
          {deltaType === 'negative' && (
            <span className="flex size-5 items-center justify-center rounded bg-red-100 dark:bg-red-900/30">
              <ChevronDown
                className="size-4 text-red-700 dark:text-red-500"
                strokeWidth={2.5}
                aria-hidden="true"
              />
            </span>
          )}
          <span className={cn('text-sm font-medium', deltaTextColors[deltaType])}>
            {deltaType === 'positive' && delta > 0 ? '+' : ''}
            {delta}%
          </span>
          <span className="text-sm text-gray-500 dark:text-gray-400">from previous month</span>
        </div>
      )}

      {/* Subtitle (alternative to delta, e.g., "As of 2025-12-28") */}
      {!delta && subtitle && (
        <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">{subtitle}</p>
      )}

      {/* Sparkline or children slot */}
      {children && <div className="mt-4">{children}</div>}

      {/* View more link */}
      {showViewMore && (
        <p className="mt-4 text-sm font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400">
          View more →
        </p>
      )}
    </Card>
  )
}

function KpiCardSkeleton({ className }: { className?: string }) {
  return (
    <Card
      className={cn(
        'rounded-xl border-0 bg-white p-6 ring-1 ring-gray-200 dark:bg-gray-950 dark:ring-gray-800',
        className,
      )}
    >
      <Skeleton className="h-4 w-28" />
      <div className="mt-2 flex items-baseline gap-2">
        <Skeleton className="h-9 w-36" />
        <Skeleton className="h-4 w-16" />
      </div>
      <div className="mt-3 flex items-center gap-2">
        <Skeleton className="size-5 rounded" />
        <Skeleton className="h-4 w-12" />
        <Skeleton className="h-4 w-32" />
      </div>
    </Card>
  )
}

export { KpiCard, KpiCardSkeleton }
