import type { ReactNode } from 'react'
import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface PurchaseBillQuickStat {
  label: string
  value: number | string
  variant: 'draft' | 'posted' | 'pending' | 'paid' | 'neutral'
}

export interface PurchaseBillPageHeaderProps {
  icon?: ReactNode
  title: string
  subtitle?: string
  stats?: PurchaseBillQuickStat[]
  actions?: ReactNode
  showRefresh?: boolean
  onRefresh?: () => void
  refreshing?: boolean
  className?: string
  children?: ReactNode
}

const statVariantStyles: Record<PurchaseBillQuickStat['variant'], string> = {
  draft: 'voucher-quick-stat-draft',
  posted: 'voucher-quick-stat-posted',
  pending: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  paid: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  neutral: 'bg-secondary text-secondary-foreground',
}

export function PurchaseBillPageHeader({
  icon,
  title,
  subtitle,
  stats,
  actions,
  showRefresh = true,
  onRefresh,
  refreshing = false,
  className,
  children,
}: PurchaseBillPageHeaderProps) {
  return (
    <div className={cn('voucher-page-header space-y-4', className)}>
      <div className="flex flex-col gap-4 pt-4 md:flex-row md:items-start md:justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            {icon && <span className="text-[var(--voucher-primary)] flex-shrink-0">{icon}</span>}
            <h1 className="text-2xl font-semibold tracking-tight voucher-font-heading">{title}</h1>
          </div>
          {subtitle && <p className="text-sm text-muted-foreground max-w-xl">{subtitle}</p>}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {stats && stats.length > 0 && (
            <div className="flex gap-2 flex-wrap">
              {stats.map((stat) => (
                <span
                  key={stat.label}
                  className={cn('voucher-quick-stat', statVariantStyles[stat.variant])}
                >
                  <span className="opacity-80">{stat.label}:</span>
                  <span>{stat.value}</span>
                </span>
              ))}
            </div>
          )}

          <div className="flex items-center gap-2">
            {showRefresh && onRefresh && (
              <Button
                variant="outline"
                size="sm"
                onClick={onRefresh}
                disabled={refreshing}
                className="gap-2"
              >
                <RefreshCw className={cn('h-4 w-4', refreshing && 'voucher-spin')} />
                <span className="hidden sm:inline">Refresh</span>
              </Button>
            )}
            {actions}
          </div>
        </div>
      </div>

      {children}
    </div>
  )
}

export function PurchaseBillSectionHeader({
  title,
  description,
  actions,
  className,
}: {
  title: string
  description?: string
  actions?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex items-start justify-between gap-4 pb-4 border-b', className)}>
      <div className="space-y-1">
        <h2 className="text-lg font-semibold voucher-font-heading">{title}</h2>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

export default PurchaseBillPageHeader
