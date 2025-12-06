import type { ReactNode } from 'react'
import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * VoucherPageHeader - Consistent page title and actions layout
 *
 * Features:
 * - Gradient accent line at top for visual distinction
 * - Icon + title + subtitle layout
 * - Quick stats section for counts/summaries
 * - Action buttons area with refresh support
 * - Responsive design
 */

export interface VoucherQuickStat {
  label: string
  value: number | string
  variant: 'draft' | 'posted' | 'unposted' | 'neutral'
}

export interface VoucherPageHeaderProps {
  /** Page icon (Lucide icon component) */
  icon?: ReactNode
  /** Page title */
  title: string
  /** Subtitle/description */
  subtitle?: string
  /** Quick stats to display */
  stats?: VoucherQuickStat[]
  /** Action buttons */
  actions?: ReactNode
  /** Show refresh button */
  showRefresh?: boolean
  /** Refresh callback */
  onRefresh?: () => void
  /** Loading state for refresh */
  refreshing?: boolean
  /** Additional class names */
  className?: string
  /** Children rendered below header */
  children?: ReactNode
}

const statVariantStyles: Record<VoucherQuickStat['variant'], string> = {
  draft: 'voucher-quick-stat-draft',
  posted: 'voucher-quick-stat-posted',
  unposted: 'voucher-quick-stat-unposted',
  neutral: 'bg-secondary text-secondary-foreground',
}

export function VoucherPageHeader({
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
}: VoucherPageHeaderProps) {
  return (
    <div className={cn('voucher-page-header space-y-4', className)}>
      <div className="flex flex-col gap-4 pt-4 md:flex-row md:items-start md:justify-between">
        {/* Left side: Icon, Title, Subtitle */}
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            {icon && <span className="text-[var(--voucher-primary)] flex-shrink-0">{icon}</span>}
            <h1 className="text-2xl font-semibold tracking-tight voucher-font-heading">{title}</h1>
          </div>
          {subtitle && <p className="text-sm text-muted-foreground max-w-xl">{subtitle}</p>}
        </div>

        {/* Right side: Stats + Actions */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Quick Stats */}
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

          {/* Action Buttons */}
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

/**
 * Section header for card sections within a page
 */
export function VoucherSectionHeader({
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

export default VoucherPageHeader
