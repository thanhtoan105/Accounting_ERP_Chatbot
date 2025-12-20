import { cn } from '@/lib/utils'

/**
 * VoucherSkeleton - Loading states for voucher components
 *
 * Features:
 * - Shimmer animation effect
 * - Multiple variants for different contexts (table, form, card)
 * - Staggered animation delays for natural feel
 * - Matches actual component dimensions
 */

interface SkeletonProps {
  className?: string
}

function SkeletonBase({ className }: SkeletonProps) {
  return <div className={cn('voucher-skeleton', className)} />
}

/**
 * Table row skeleton - mimics a voucher list row
 */
export function VoucherTableRowSkeleton({ columns = 8 }: { columns?: number }) {
  return (
    <tr className="voucher-row-animate">
      {Array.from({ length: columns }).map((_, i) => (
        <td key={i} className="p-3">
          <SkeletonBase
            className={cn(
              'h-5',
              // Vary widths for visual interest
              i === 0 && 'w-24', // Voucher number
              i === 1 && 'w-20', // Date
              i === 2 && 'w-16', // Type
              i === 3 && 'w-28', // Amount
              i === 4 && 'w-16', // Status
              i === 5 && 'w-24', // Entered by
              i === 6 && 'w-20', // Posted by
              i === 7 && 'w-8', // Actions
            )}
          />
        </td>
      ))}
    </tr>
  )
}

/**
 * Multiple table rows with stagger effect
 */
export function VoucherTableSkeleton({
  rows = 5,
  columns = 8,
}: {
  rows?: number
  columns?: number
}) {
  return (
    <tbody className="voucher-stagger">
      {Array.from({ length: rows }).map((_, i) => (
        <VoucherTableRowSkeleton key={i} columns={columns} />
      ))}
    </tbody>
  )
}

/**
 * Card skeleton for voucher type/template cards
 */
export function VoucherCardSkeleton() {
  return (
    <div className="voucher-card-interactive rounded-lg border p-4 space-y-3 voucher-row-animate">
      <div className="flex items-start justify-between gap-4">
        <SkeletonBase className="h-6 w-20" />
        <SkeletonBase className="h-5 w-16 rounded-full" />
      </div>
      <SkeletonBase className="h-4 w-3/4" />
      <SkeletonBase className="h-4 w-1/2" />
      <div className="flex gap-2 pt-2">
        <SkeletonBase className="h-8 w-16 rounded-md" />
        <SkeletonBase className="h-8 w-16 rounded-md" />
      </div>
    </div>
  )
}

/**
 * Multiple cards with stagger effect
 */
export function VoucherCardsGridSkeleton({
  count = 6,
  columns = 3,
}: {
  count?: number
  columns?: number
}) {
  return (
    <div
      className={cn(
        'grid gap-4 voucher-stagger',
        columns === 2 && 'grid-cols-1 md:grid-cols-2',
        columns === 3 && 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
        columns === 4 && 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
      )}
    >
      {Array.from({ length: count }).map((_, i) => (
        <VoucherCardSkeleton key={i} />
      ))}
    </div>
  )
}

/**
 * Form section skeleton
 */
export function VoucherFormSectionSkeleton({ fields = 2 }: { fields?: number }) {
  return (
    <div className="rounded-lg border p-6 space-y-4 voucher-row-animate">
      <SkeletonBase className="h-6 w-40" />
      <div
        className={cn(
          'grid gap-4',
          fields === 1 && 'grid-cols-1',
          fields === 2 && 'grid-cols-1 md:grid-cols-2',
          fields === 3 && 'grid-cols-1 md:grid-cols-3',
        )}
      >
        {Array.from({ length: fields }).map((_, i) => (
          <div key={i} className="space-y-2">
            <SkeletonBase className="h-4 w-24" />
            <SkeletonBase className="h-10 w-full rounded-md" />
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * Line grid skeleton for voucher entry lines
 */
export function VoucherLineGridSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="rounded-lg border overflow-hidden voucher-row-animate">
      {/* Header */}
      <div className="bg-[var(--voucher-surface-2)] p-3 flex gap-4">
        <SkeletonBase className="h-4 w-8" />
        <SkeletonBase className="h-4 w-32" />
        <SkeletonBase className="h-4 w-32" />
        <SkeletonBase className="h-4 w-24" />
        <SkeletonBase className="h-4 w-40" />
        <SkeletonBase className="h-4 w-8" />
      </div>
      {/* Rows */}
      <div className="divide-y voucher-stagger">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="p-3 flex gap-4 items-center">
            <SkeletonBase className="h-6 w-8" />
            <SkeletonBase className="h-9 w-32 rounded-md" />
            <SkeletonBase className="h-9 w-32 rounded-md" />
            <SkeletonBase className="h-9 w-24 rounded-md" />
            <SkeletonBase className="h-9 w-40 rounded-md" />
            <SkeletonBase className="h-8 w-8 rounded-md" />
          </div>
        ))}
      </div>
      {/* Footer */}
      <div className="bg-[var(--voucher-surface-1)] p-3 flex justify-between">
        <SkeletonBase className="h-4 w-32" />
        <SkeletonBase className="h-4 w-24" />
      </div>
    </div>
  )
}

/**
 * Page header skeleton
 */
export function VoucherPageHeaderSkeleton({
  showStats = true,
  actions = 2,
}: {
  showStats?: boolean
  actions?: number
}) {
  return (
    <div className="voucher-page-header voucher-row-animate">
      <div className="flex items-center justify-between gap-4 pt-4">
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <SkeletonBase className="h-6 w-6 rounded" />
            <SkeletonBase className="h-8 w-40" />
          </div>
          <SkeletonBase className="h-4 w-64" />
        </div>
        <div className="flex items-center gap-2">
          {showStats && (
            <div className="flex gap-2 mr-4">
              <SkeletonBase className="h-6 w-20 rounded-full" />
              <SkeletonBase className="h-6 w-20 rounded-full" />
              <SkeletonBase className="h-6 w-20 rounded-full" />
            </div>
          )}
          {Array.from({ length: actions }).map((_, i) => (
            <SkeletonBase key={i} className="h-9 w-24 rounded-md" />
          ))}
        </div>
      </div>
    </div>
  )
}

/**
 * Filter bar skeleton
 */
export function VoucherFilterBarSkeleton() {
  return (
    <div className="flex flex-wrap gap-4 items-center voucher-row-animate">
      <SkeletonBase className="h-9 w-64 rounded-md" />
      <div className="flex gap-2">
        <SkeletonBase className="h-8 w-16 rounded-full" />
        <SkeletonBase className="h-8 w-16 rounded-full" />
        <SkeletonBase className="h-8 w-20 rounded-full" />
        <SkeletonBase className="h-8 w-20 rounded-full" />
      </div>
      <SkeletonBase className="h-9 w-40 rounded-md" />
    </div>
  )
}

/**
 * Summary sidebar skeleton
 */
export function VoucherSummarySkeleton() {
  return (
    <div className="rounded-lg border p-4 space-y-4 voucher-row-animate">
      <SkeletonBase className="h-5 w-24" />
      <div className="space-y-3">
        <div className="flex justify-between">
          <SkeletonBase className="h-4 w-20" />
          <SkeletonBase className="h-4 w-24" />
        </div>
        <div className="flex justify-between">
          <SkeletonBase className="h-4 w-20" />
          <SkeletonBase className="h-4 w-24" />
        </div>
        <div className="pt-2 border-t">
          <div className="flex justify-between">
            <SkeletonBase className="h-4 w-16" />
            <SkeletonBase className="h-6 w-28 rounded-md" />
          </div>
        </div>
      </div>
    </div>
  )
}

export { SkeletonBase as VoucherSkeleton }
export default VoucherTableSkeleton
