import { useMemo } from 'react'
import { CheckCircle2, Edit3, RotateCcw, ArrowLeftRight } from 'lucide-react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

/**
 * VoucherStatusBadge - Enhanced status indicator with icons and animations
 *
 * Features:
 * - Color-coded status with semantic meaning (draft=blue, posted=green, unposted=orange)
 * - Optional icon display for quick visual recognition
 * - Dot indicator for compact display
 * - Pulse animation on status change
 * - Support for reversed voucher status
 */

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 font-medium transition-all duration-150',
  {
    variants: {
      variant: {
        draft:
          'bg-[var(--voucher-status-draft-bg)] text-[var(--voucher-status-draft)] border-[var(--voucher-status-draft)]/20',
        posted:
          'bg-[var(--voucher-status-posted-bg)] text-[var(--voucher-status-posted)] border-[var(--voucher-status-posted)]/20',
        unposted:
          'bg-[var(--voucher-status-unposted-bg)] text-[var(--voucher-status-unposted)] border-[var(--voucher-status-unposted)]/20',
        reversed:
          'bg-purple-50 text-purple-600 border-purple-200 dark:bg-purple-950 dark:text-purple-400 dark:border-purple-800',
      },
      size: {
        sm: 'text-xs px-2 py-0.5 rounded-md',
        md: 'text-sm px-2.5 py-1 rounded-lg',
        lg: 'text-base px-3 py-1.5 rounded-lg',
      },
    },
    defaultVariants: {
      variant: 'draft',
      size: 'md',
    },
  },
)

const iconSizeMap = {
  sm: 'h-3 w-3',
  md: 'h-3.5 w-3.5',
  lg: 'h-4 w-4',
}

const dotSizeMap = {
  sm: 'h-1.5 w-1.5',
  md: 'h-2 w-2',
  lg: 'h-2.5 w-2.5',
}

export type VoucherStatus = 'draft' | 'posted' | 'unposted' | 'reversed'

export interface VoucherStatusBadgeProps
  extends Omit<VariantProps<typeof badgeVariants>, 'variant'> {
  /** The voucher status to display */
  status: VoucherStatus
  /** Show icon alongside text */
  showIcon?: boolean
  /** Show dot indicator instead of icon */
  showDot?: boolean
  /** Enable pulse animation (useful for status changes) */
  animate?: boolean
  /** Additional class names */
  className?: string
  /** Custom label override */
  label?: string
}

const statusConfig: Record<
  VoucherStatus,
  { label: string; Icon: typeof CheckCircle2; dotClass: string }
> = {
  draft: {
    label: 'Draft',
    Icon: Edit3,
    dotClass: 'bg-[var(--voucher-status-draft)]',
  },
  posted: {
    label: 'Posted',
    Icon: CheckCircle2,
    dotClass: 'bg-[var(--voucher-status-posted)]',
  },
  unposted: {
    label: 'Unposted',
    Icon: RotateCcw,
    dotClass: 'bg-[var(--voucher-status-unposted)]',
  },
  reversed: {
    label: 'Reversed',
    Icon: ArrowLeftRight,
    dotClass: 'bg-purple-500',
  },
}

export function VoucherStatusBadge({
  status,
  size = 'md',
  showIcon = false,
  showDot = true,
  animate = false,
  className,
  label,
}: VoucherStatusBadgeProps) {
  const config = useMemo(() => statusConfig[status], [status])
  const { Icon, dotClass } = config
  const displayLabel = label ?? config.label

  return (
    <span
      className={cn(
        badgeVariants({ variant: status, size }),
        'border',
        animate && 'voucher-badge-animate',
        className,
      )}
    >
      {showDot && !showIcon && (
        <span
          className={cn('rounded-full flex-shrink-0', dotSizeMap[size!], dotClass)}
          aria-hidden="true"
        />
      )}
      {showIcon && <Icon className={cn(iconSizeMap[size!], 'flex-shrink-0')} aria-hidden="true" />}
      <span className="uppercase tracking-wide">{displayLabel}</span>
    </span>
  )
}

/**
 * Compact version - just the dot with tooltip
 */
export function VoucherStatusDot({
  status,
  size = 'md',
  className,
}: {
  status: VoucherStatus
  size?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const config = statusConfig[status]

  return (
    <span
      className={cn('rounded-full flex-shrink-0', dotSizeMap[size], config.dotClass, className)}
      title={config.label}
      aria-label={config.label}
    />
  )
}

export default VoucherStatusBadge
