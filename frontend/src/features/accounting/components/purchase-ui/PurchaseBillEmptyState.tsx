import { FileText, Plus, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface PurchaseBillEmptyStateProps {
  variant?: 'no-bills' | 'no-results'
  primaryAction?: {
    label: string
    onClick: () => void
  }
  secondaryAction?: {
    label: string
    onClick: () => void
  }
  className?: string
}

export function PurchaseBillEmptyState({
  variant = 'no-bills',
  primaryAction,
  secondaryAction,
  className,
}: PurchaseBillEmptyStateProps) {
  const isNoResults = variant === 'no-results'

  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center py-16 px-4 text-center voucher-slide-in',
        className,
      )}
    >
      <div className="voucher-empty-illustration mb-6">
        {isNoResults ? (
          <Search className="h-12 w-12 text-muted-foreground/50" />
        ) : (
          <FileText className="h-12 w-12 text-muted-foreground/50" />
        )}
      </div>

      <h3 className="text-lg font-semibold text-foreground mb-2 voucher-font-heading">
        {isNoResults ? 'No bills found' : 'No purchase bills yet'}
      </h3>

      <p className="text-sm text-muted-foreground max-w-sm mb-6">
        {isNoResults
          ? "Try adjusting your search or filter criteria to find what you're looking for."
          : 'Get started by creating your first purchase bill to track supplier invoices.'}
      </p>

      <div className="flex items-center gap-3">
        {primaryAction && (
          <Button onClick={primaryAction.onClick} className="gap-2">
            {!isNoResults && <Plus className="h-4 w-4" />}
            {primaryAction.label}
          </Button>
        )}
        {secondaryAction && (
          <Button variant="outline" onClick={secondaryAction.onClick}>
            {secondaryAction.label}
          </Button>
        )}
      </div>
    </div>
  )
}

export default PurchaseBillEmptyState
