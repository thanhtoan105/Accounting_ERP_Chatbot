import { useMemo } from 'react'
import { cn } from '@/lib/utils'

/**
 * VoucherAmountCell - Right-aligned, tabular-nums currency display
 *
 * Features:
 * - JetBrains Mono font for perfect column alignment
 * - Semantic color coding (debit=blue, credit=green, neutral=default)
 * - Automatic currency formatting with locale support
 * - Null/undefined displays as em-dash
 * - Compact mode for dense tables
 */

export type AmountVariant = 'debit' | 'credit' | 'neutral' | 'balanced' | 'unbalanced'

export interface VoucherAmountCellProps {
  /** The amount to display */
  value: number | null | undefined
  /** Currency code (default: VND) */
  currency?: string
  /** Visual variant for semantic coloring */
  variant?: AmountVariant
  /** Show currency symbol */
  showCurrency?: boolean
  /** Compact mode with smaller text */
  compact?: boolean
  /** Additional class names */
  className?: string
  /** Show plus/minus sign */
  showSign?: boolean
  /** Placeholder when value is null */
  placeholder?: string
}

const variantStyles: Record<AmountVariant, string> = {
  debit: 'text-[var(--voucher-debit)]',
  credit: 'text-[var(--voucher-credit)]',
  neutral: 'text-foreground',
  balanced: 'text-[var(--voucher-balanced)]',
  unbalanced: 'text-[var(--voucher-unbalanced)]',
}

export function VoucherAmountCell({
  value,
  currency = 'VND',
  variant = 'neutral',
  showCurrency = true,
  compact = false,
  className,
  showSign = false,
  placeholder = '—',
}: VoucherAmountCellProps) {
  const formattedValue = useMemo(() => {
    if (value == null) return placeholder

    const absValue = Math.abs(value)
    const formatted = new Intl.NumberFormat('vi-VN', {
      style: showCurrency ? 'currency' : 'decimal',
      currency: showCurrency ? currency : undefined,
      minimumFractionDigits: 0,
      maximumFractionDigits: currency === 'VND' ? 0 : 2,
    }).format(absValue)

    if (showSign && value !== 0) {
      const sign = value > 0 ? '+' : '-'
      // Remove the minus if the formatter already added it
      return `${sign}${formatted.replace('-', '')}`
    }

    return formatted
  }, [value, currency, showCurrency, showSign, placeholder])

  if (value == null) {
    return (
      <span
        className={cn(
          'voucher-tabular-nums text-right text-muted-foreground',
          compact ? 'text-xs' : 'text-sm',
          className,
        )}
      >
        {placeholder}
      </span>
    )
  }

  return (
    <span
      className={cn(
        'voucher-tabular-nums text-right font-medium',
        compact ? 'text-xs' : 'text-sm',
        variantStyles[variant],
        className,
      )}
    >
      {formattedValue}
    </span>
  )
}

/**
 * Inline version for use in sentences or labels
 */
export function VoucherAmountInline({
  value,
  currency = 'VND',
  variant = 'neutral',
  className,
}: Pick<VoucherAmountCellProps, 'value' | 'currency' | 'variant' | 'className'>) {
  return (
    <VoucherAmountCell
      value={value}
      currency={currency}
      variant={variant}
      showCurrency={true}
      compact={false}
      className={cn('inline', className)}
    />
  )
}

/**
 * Balance display with balanced/unbalanced indicator
 */
export function VoucherBalanceDisplay({
  debitTotal,
  creditTotal,
  currency = 'VND',
  className,
}: {
  debitTotal: number
  creditTotal: number
  currency?: string
  className?: string
}) {
  const isBalanced = Math.abs(debitTotal - creditTotal) < 0.01
  const difference = Math.abs(debitTotal - creditTotal)

  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <div className="flex items-center justify-between gap-4">
        <span className="text-sm text-muted-foreground">Total Debit:</span>
        <VoucherAmountCell value={debitTotal} currency={currency} variant="debit" />
      </div>
      <div className="flex items-center justify-between gap-4">
        <span className="text-sm text-muted-foreground">Total Credit:</span>
        <VoucherAmountCell value={creditTotal} currency={currency} variant="credit" />
      </div>
      <div
        className={cn(
          'flex items-center justify-between gap-4 pt-2 mt-1 border-t',
          isBalanced
            ? 'border-[var(--voucher-balanced)]/30'
            : 'border-[var(--voucher-unbalanced)]/30',
        )}
      >
        <span className="text-sm font-medium">Balance:</span>
        <div
          className={cn(
            'voucher-balance-indicator',
            isBalanced ? 'voucher-balance-balanced' : 'voucher-balance-unbalanced',
          )}
        >
          {isBalanced ? (
            <span className="text-sm">✓ Balanced</span>
          ) : (
            <VoucherAmountCell value={difference} currency={currency} variant="unbalanced" />
          )}
        </div>
      </div>
    </div>
  )
}

export default VoucherAmountCell
