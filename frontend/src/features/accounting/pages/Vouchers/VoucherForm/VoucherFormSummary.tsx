import { useMemo } from 'react'
import { CheckCircle2, AlertTriangle, Paperclip, AlertCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Separator } from '@/components/ui/separator'

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
  onValidationSummaryClick?: () => void
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
  onAttachmentClick,
  onValidationSummaryClick,
}: VoucherFormSummaryProps) {
  const balanceStatus = useMemo(() => {
    if (totals.lineCount === 0) {
      return {
        label: 'Empty',
        color: 'text-muted-foreground',
      }
    }
    if (totals.isBalanced) {
      return {
        label: 'Balanced',
        color: 'text-emerald-600 dark:text-emerald-400',
      }
    }
    return {
      label: 'Unbalanced',
      color: 'text-red-600 dark:text-red-400',
    }
  }, [totals])

  return (
    <div className="flex flex-col md:flex-row items-center justify-between px-6 py-3 h-full gap-4 md:gap-0">
      {/* Left Side: Status & Counts */}
      <div className="flex items-center gap-4 md:gap-6 text-sm w-full md:w-auto justify-between md:justify-start">
        {/* Line Count */}
        <div className="flex items-center gap-2 text-muted-foreground">
          <span className="font-medium text-foreground">{totals.lineCount}</span>
          <span className="hidden sm:inline">lines</span>
          <span className="sm:hidden">L</span>
        </div>

        <Separator orientation="vertical" className="h-4" />

        {/* Attachment Count */}
        <button
          type="button"
          onClick={onAttachmentClick}
          className={cn(
            'flex items-center gap-2 transition-colors',
            attachmentCount > 0
              ? 'text-foreground hover:text-primary'
              : 'text-muted-foreground hover:text-foreground',
          )}
        >
          <Paperclip className="h-4 w-4" />
          <span className="hidden sm:inline">
            <span className="font-medium text-foreground">{attachmentCount}</span> attachments
          </span>
          <span className="sm:hidden font-medium text-foreground">{attachmentCount}</span>
        </button>

        {/* Validation Status */}
        {validationSummary.errorCount > 0 && (
          <>
            <Separator orientation="vertical" className="h-4" />
            <button
              type="button"
              onClick={onValidationSummaryClick}
              className="flex items-center gap-2 text-destructive hover:text-destructive/80 transition-colors"
            >
              <AlertCircle className="h-4 w-4" />
              <span className="font-medium hidden sm:inline">{validationSummary.errorCount} lines with errors</span>
              <span className="font-medium sm:hidden">{validationSummary.errorCount} err</span>
            </button>
          </>
        )}
      </div>

      {/* Right Side: Financials */}
      <div className="flex items-center gap-4 md:gap-8 w-full md:w-auto justify-between md:justify-end">
        <div className="flex items-center gap-4 md:gap-6">
          <div className="flex flex-col items-end">
            <span className="text-[10px] uppercase text-muted-foreground font-semibold tracking-wider">Debit</span>
            <span className="font-mono font-medium text-emerald-600 dark:text-emerald-400 text-sm md:text-base">
              {formatCurrency(totals.totalDebit)}
            </span>
          </div>

          <Separator orientation="vertical" className="h-8" />

          <div className="flex flex-col items-end">
            <span className="text-[10px] uppercase text-muted-foreground font-semibold tracking-wider">Credit</span>
            <span className="font-mono font-medium text-blue-600 dark:text-blue-400 text-sm md:text-base">
              {formatCurrency(totals.totalCredit)}
            </span>
          </div>
        </div>

        {/* Difference / Balance Status */}
        <div
          className={cn(
            'flex items-center gap-2 md:gap-3 px-3 md:px-4 py-1.5 rounded-md border shadow-sm ml-2',
            totals.isBalanced
              ? 'bg-emerald-50/50 border-emerald-100 dark:bg-emerald-900/10 dark:border-emerald-800'
              : 'bg-red-50/50 border-red-100 dark:bg-red-900/10 dark:border-red-800',
          )}
        >
          {totals.isBalanced ? (
            <CheckCircle2
              className={cn('h-4 w-4 md:h-5 md:w-5', totals.isBalanced ? 'text-emerald-600' : 'text-red-600')}
            />
          ) : (
            <AlertTriangle className="h-4 w-4 md:h-5 md:w-5 text-red-600" />
          )}
          <div className="flex flex-col items-end leading-none gap-0.5">
             <span
              className={cn(
                'text-[10px] font-semibold uppercase tracking-wider',
                totals.isBalanced ? 'text-emerald-600' : 'text-red-600',
              )}
            >
              {balanceStatus.label}
            </span>
            {!totals.isBalanced && (
              <span className="font-mono font-bold text-red-600 dark:text-red-400 text-sm">
                {formatCurrency(Math.abs(totals.difference))}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
