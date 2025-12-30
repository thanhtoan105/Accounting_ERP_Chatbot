import { useMemo } from 'react'
import { CheckCircle2, Edit3, Clock, XCircle, CreditCard, Wallet } from 'lucide-react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'
import type { PurchaseBillStatus } from '@/types/purchaseBill'

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 font-medium transition-all duration-150',
  {
    variants: {
      variant: {
        DRAFT:
          'bg-[var(--voucher-status-draft-bg)] text-[var(--voucher-status-draft)] border-[var(--voucher-status-draft)]/20',
        PENDING_APPROVAL:
          'bg-amber-50 text-amber-600 border-amber-200 dark:bg-amber-950 dark:text-amber-400 dark:border-amber-800',
        POSTED:
          'bg-[var(--voucher-status-posted-bg)] text-[var(--voucher-status-posted)] border-[var(--voucher-status-posted)]/20',
        REJECTED:
          'bg-red-50 text-red-600 border-red-200 dark:bg-red-950 dark:text-red-400 dark:border-red-800',
        PAID: 'bg-emerald-50 text-emerald-600 border-emerald-200 dark:bg-emerald-950 dark:text-emerald-400 dark:border-emerald-800',
        PARTIALLY_PAID:
          'bg-cyan-50 text-cyan-600 border-cyan-200 dark:bg-cyan-950 dark:text-cyan-400 dark:border-cyan-800',
      },
      size: {
        sm: 'text-xs px-2 py-0.5 rounded-md',
        md: 'text-sm px-2.5 py-1 rounded-lg',
        lg: 'text-base px-3 py-1.5 rounded-lg',
      },
    },
    defaultVariants: {
      variant: 'DRAFT',
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

export interface PurchaseBillStatusBadgeProps
  extends Omit<VariantProps<typeof badgeVariants>, 'variant'> {
  status: PurchaseBillStatus
  showIcon?: boolean
  showDot?: boolean
  animate?: boolean
  className?: string
  label?: string
}

const statusConfig: Record<
  PurchaseBillStatus,
  { label: string; Icon: typeof CheckCircle2; dotClass: string }
> = {
  DRAFT: {
    label: 'Draft',
    Icon: Edit3,
    dotClass: 'bg-[var(--voucher-status-draft)]',
  },
  PENDING_APPROVAL: {
    label: 'Pending',
    Icon: Clock,
    dotClass: 'bg-amber-500',
  },
  POSTED: {
    label: 'Posted',
    Icon: CheckCircle2,
    dotClass: 'bg-[var(--voucher-status-posted)]',
  },
  REJECTED: {
    label: 'Rejected',
    Icon: XCircle,
    dotClass: 'bg-red-500',
  },
  PAID: {
    label: 'Paid',
    Icon: CreditCard,
    dotClass: 'bg-emerald-500',
  },
  PARTIALLY_PAID: {
    label: 'Partial',
    Icon: Wallet,
    dotClass: 'bg-cyan-500',
  },
}

export function PurchaseBillStatusBadge({
  status,
  size = 'md',
  showIcon = false,
  showDot = true,
  animate = false,
  className,
  label,
}: PurchaseBillStatusBadgeProps) {
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

export function PurchaseBillStatusDot({
  status,
  size = 'md',
  className,
}: {
  status: PurchaseBillStatus
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

export default PurchaseBillStatusBadge
