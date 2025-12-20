import type { ReactNode } from 'react'
import { FileText, Plus, Search, Filter, FolderOpen } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * VoucherEmptyState - Illustrated empty states for voucher pages
 *
 * Features:
 * - Multiple variants for different contexts (no data, no results, error)
 * - Icon illustrations matching the context
 * - Primary and secondary action buttons
 * - Responsive and accessible
 */

export type EmptyStateVariant =
  | 'no-vouchers'
  | 'no-results'
  | 'no-templates'
  | 'no-types'
  | 'no-attachments'
  | 'error'

interface EmptyStateConfig {
  icon: typeof FileText
  title: string
  description: string
  iconColor: string
}

const variantConfig: Record<EmptyStateVariant, EmptyStateConfig> = {
  'no-vouchers': {
    icon: FileText,
    title: 'No vouchers yet',
    description: 'Get started by creating your first voucher to record financial transactions.',
    iconColor: 'text-[var(--voucher-primary)]',
  },
  'no-results': {
    icon: Search,
    title: 'No matching vouchers',
    description: "Try adjusting your search or filters to find what you're looking for.",
    iconColor: 'text-muted-foreground',
  },
  'no-templates': {
    icon: FolderOpen,
    title: 'No templates yet',
    description: 'Create templates to speed up voucher entry for recurring transactions.',
    iconColor: 'text-[var(--voucher-teal)]',
  },
  'no-types': {
    icon: Filter,
    title: 'No voucher types configured',
    description: 'Set up voucher types to organize and categorize your transactions.',
    iconColor: 'text-[var(--voucher-primary)]',
  },
  'no-attachments': {
    icon: FileText,
    title: 'No attachments',
    description: 'Drag and drop files here to attach supporting documents.',
    iconColor: 'text-muted-foreground',
  },
  error: {
    icon: FileText,
    title: 'Something went wrong',
    description:
      "We couldn't load the data. Please try again or contact support if the problem persists.",
    iconColor: 'text-destructive',
  },
}

export interface VoucherEmptyStateProps {
  /** The type of empty state to display */
  variant: EmptyStateVariant
  /** Custom title override */
  title?: string
  /** Custom description override */
  description?: string
  /** Primary action button */
  primaryAction?: {
    label: string
    onClick: () => void
    icon?: ReactNode
  }
  /** Secondary action button */
  secondaryAction?: {
    label: string
    onClick: () => void
  }
  /** Compact mode for inline use */
  compact?: boolean
  /** Additional class names */
  className?: string
}

export function VoucherEmptyState({
  variant,
  title,
  description,
  primaryAction,
  secondaryAction,
  compact = false,
  className,
}: VoucherEmptyStateProps) {
  const config = variantConfig[variant]
  const Icon = config.icon
  const displayTitle = title ?? config.title
  const displayDescription = description ?? config.description

  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center',
        compact ? 'py-8 px-4' : 'py-16 px-6',
        className,
      )}
    >
      {/* Icon Illustration */}
      <div className={cn('voucher-empty-illustration mb-6', compact && 'w-24 h-20 mb-4')}>
        <Icon
          className={cn(config.iconColor, compact ? 'h-10 w-10' : 'h-14 w-14')}
          strokeWidth={1.5}
        />
      </div>

      {/* Text Content */}
      <div className="max-w-sm space-y-2">
        <h3 className={cn('font-semibold voucher-font-heading', compact ? 'text-base' : 'text-lg')}>
          {displayTitle}
        </h3>
        <p className={cn('text-muted-foreground', compact ? 'text-xs' : 'text-sm')}>
          {displayDescription}
        </p>
      </div>

      {/* Action Buttons */}
      {(primaryAction || secondaryAction) && (
        <div className={cn('flex gap-3 mt-6', compact && 'mt-4')}>
          {primaryAction && (
            <Button
              onClick={primaryAction.onClick}
              size={compact ? 'sm' : 'default'}
              className="gap-2"
            >
              {primaryAction.icon ?? <Plus className="h-4 w-4" />}
              {primaryAction.label}
            </Button>
          )}
          {secondaryAction && (
            <Button
              variant="outline"
              onClick={secondaryAction.onClick}
              size={compact ? 'sm' : 'default'}
            >
              {secondaryAction.label}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * Inline empty state for table cells or small containers
 */
export function VoucherEmptyStateInline({
  message = 'No data',
  className,
}: {
  message?: string
  className?: string
}) {
  return (
    <div
      className={cn(
        'flex items-center justify-center py-8 text-sm text-muted-foreground',
        className,
      )}
    >
      {message}
    </div>
  )
}

export default VoucherEmptyState
