import { useMemo } from 'react'
import { CheckCircle2, AlertTriangle, Calculator, FileText, History } from 'lucide-react'
import { cn } from '@/lib/utils'

interface VoucherFormSummaryProps {
  totals: {
    totalDebit: number
    totalCredit: number
    lineCount: number
    isBalanced: boolean
    difference: number
  }
  attachmentCount: number
  validationSummary: { errorCount: number; totalErrors: number }
  editingVoucher?: {
    status?: string
    createdAt?: string
    updatedAt?: string
    createdBy?: string
  } | null
  isEditing: boolean
  onAttachmentClick?: () => void
}

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'decimal',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount)
}

export function VoucherFormSummary({
  totals,
  attachmentCount,
  validationSummary,
  editingVoucher,
  isEditing,
  onAttachmentClick,
}: VoucherFormSummaryProps) {
  const balanceStatus = useMemo(() => {
    if (totals.lineCount === 0) {
      return {
        icon: Calculator,
        label: 'No entries',
        color: 'text-muted-foreground',
        bgColor: 'bg-muted/50',
      }
    }
    if (totals.isBalanced) {
      return {
        icon: CheckCircle2,
        label: 'Balanced',
        color: 'text-voucher-status-posted',
        bgColor: 'bg-voucher-status-posted/10',
      }
    }
    return {
      icon: AlertTriangle,
      label: 'Unbalanced',
      color: 'text-voucher-status-unposted',
      bgColor: 'bg-voucher-status-unposted/10',
    }
  }, [totals])

  const BalanceIcon = balanceStatus.icon

  return (
    <div className="voucher-form-summary sticky top-6 space-y-4">
      {/* Balance Card */}
      <div
        className={cn(
          'rounded-xl border p-5 transition-all duration-300',
          totals.isBalanced
            ? 'border-voucher-status-posted/30'
            : 'border-voucher-status-unposted/30',
          balanceStatus.bgColor,
        )}
      >
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-semibold text-base">Summary</h3>
          <div
            className={cn(
              'flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium',
              balanceStatus.color,
              balanceStatus.bgColor,
            )}
          >
            <BalanceIcon className="h-3.5 w-3.5" />
            {balanceStatus.label}
          </div>
        </div>

        <div className="space-y-3">
          {/* Total Debit */}
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">Total Debit</span>
            <span className="font-mono text-base font-semibold text-voucher-debit tabular-nums">
              {formatCurrency(totals.totalDebit)}
            </span>
          </div>

          {/* Total Credit */}
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">Total Credit</span>
            <span className="font-mono text-base font-semibold text-voucher-credit tabular-nums">
              {formatCurrency(totals.totalCredit)}
            </span>
          </div>

          {/* Divider */}
          <div className="border-t border-dashed pt-3">
            {/* Difference */}
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">Difference</span>
              <span
                className={cn(
                  'font-mono text-base font-bold tabular-nums',
                  totals.isBalanced ? 'text-voucher-status-posted' : 'text-voucher-status-unposted',
                )}
              >
                {totals.difference >= 0 ? '+' : ''}
                {formatCurrency(totals.difference)}
              </span>
            </div>
          </div>
        </div>

        {/* Line Count */}
        <div className="mt-4 pt-3 border-t flex items-center justify-between text-xs text-muted-foreground">
          <span>Entry lines</span>
          <span className="font-medium">{totals.lineCount} lines</span>
        </div>
      </div>

      {/* Validation Status Card */}
      {validationSummary.errorCount > 0 && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4">
          <div className="flex items-center gap-2 text-destructive">
            <AlertTriangle className="h-4 w-4" />
            <span className="text-sm font-medium">Validation Issues</span>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            {validationSummary.errorCount} lines with {validationSummary.totalErrors} errors
          </p>
        </div>
      )}

      {/* Attachments Card */}
      <div
        className={cn(
          'rounded-xl border bg-card p-4 transition-colors',
          onAttachmentClick && 'cursor-pointer hover:bg-accent/50',
        )}
        onClick={onAttachmentClick}
      >
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
            <FileText className="h-5 w-5 text-primary" />
          </div>
          <div>
            <p className="text-sm font-medium">Attachments</p>
            <p className="text-xs text-muted-foreground">{attachmentCount} files</p>
          </div>
        </div>
      </div>

      {/* History Card (only for editing) */}
      {isEditing && editingVoucher && (
        <div className="rounded-xl border bg-card p-4">
          <div className="flex items-center gap-2 mb-3">
            <History className="h-4 w-4 text-muted-foreground" />
            <span className="text-sm font-medium">History</span>
          </div>
          <div className="space-y-2 text-xs text-muted-foreground">
            {editingVoucher.createdAt && (
              <div className="flex justify-between">
                <span>Created</span>
                <span>{new Date(editingVoucher.createdAt).toLocaleDateString('vi-VN')}</span>
              </div>
            )}
            {editingVoucher.updatedAt && (
              <div className="flex justify-between">
                <span>Last updated</span>
                <span>{new Date(editingVoucher.updatedAt).toLocaleDateString('vi-VN')}</span>
              </div>
            )}
            {editingVoucher.createdBy && (
              <div className="flex justify-between">
                <span>Created by</span>
                <span className="truncate max-w-[120px]">{editingVoucher.createdBy}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
