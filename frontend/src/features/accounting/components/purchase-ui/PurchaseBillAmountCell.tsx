import { cn } from '@/lib/utils'

export interface PurchaseBillAmountCellProps {
  value: number | null | undefined
  currency?: string
  className?: string
  showSign?: boolean
  colorCode?: boolean
}

export function PurchaseBillAmountCell({
  value,
  currency = 'VND',
  className,
  showSign = false,
  colorCode = false,
}: PurchaseBillAmountCellProps) {
  if (value == null) {
    return <span className={cn('text-muted-foreground', className)}>-</span>
  }

  const formatted = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(Math.abs(value))

  const displayValue =
    showSign && value !== 0 ? (value > 0 ? `+${formatted}` : `-${formatted}`) : formatted

  return (
    <span
      className={cn(
        'voucher-font-mono tabular-nums text-right whitespace-nowrap',
        colorCode && value > 0 && 'text-emerald-600 dark:text-emerald-400',
        colorCode && value < 0 && 'text-red-600 dark:text-red-400',
        className,
      )}
    >
      {displayValue}
    </span>
  )
}

export function formatPurchaseBillAmount(
  value: number | null | undefined,
  currency = 'VND',
): string {
  if (value == null) return '-'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

export default PurchaseBillAmountCell
