import { formatDistanceToNow } from 'date-fns'
import {
  AlertCircle,
  ArrowLeftRight,
  CheckCircle,
  Loader2,
  RotateCcw,
  Save,
  Sparkles,
  ClipboardList,
} from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import type { VoucherDTO } from '@/types/voucher'
import { cn } from '@/lib/utils'

interface VoucherFormHeaderProps {
  isEditing: boolean
  editingVoucher: VoucherDTO | null
  voucherId?: string
  attachmentCount: number
  onAttachmentClick: () => void
  // Actions
  onTemplateClick: () => void
  onValidateClick: () => void
  onSaveClick: () => void
  onPostClick: () => void
  onUnpostClick: () => void
  onReverseClick: () => void
  // States
  formDisabled: boolean
  loadingAccounts: boolean
  saving: boolean
  posting: boolean
  unposting: boolean
  reversing: boolean
  canPost: boolean
  canUnpost: boolean
  canReverse: boolean
  // Auto-save
  autoSaveStatus: 'idle' | 'saving' | 'saved' | 'error'
  lastSavedAt: Date | null
  autoSaveError: string | null
  // Validation
  validationSummary: { errorCount: number; totalErrors: number }
  onValidationSummaryClick: () => void
}

export function VoucherFormHeader({
  isEditing,
  editingVoucher,
  onTemplateClick,
  onSaveClick,
  onPostClick,
  onUnpostClick,
  onReverseClick,
  formDisabled,
  loadingAccounts,
  saving,
  posting,
  unposting,
  reversing,
  canPost,
  canUnpost,
  canReverse,
  autoSaveStatus,
  lastSavedAt,
  autoSaveError,
}: VoucherFormHeaderProps) {
  return (
    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between w-full">
      {/* Left: Title & Status */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-md bg-primary/10 text-primary">
            <ClipboardList className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg font-semibold tracking-tight leading-none">
              {isEditing ? 'Edit Voucher' : 'New Voucher'}
            </h1>
            {editingVoucher?.voucherNumber && (
              <span className="text-xs text-muted-foreground font-mono">
                #{editingVoucher.voucherNumber}
              </span>
            )}
          </div>
        </div>

        {editingVoucher?.status && (
          <>
            <Separator orientation="vertical" className="h-8 hidden md:block" />
            <Badge
              variant="outline"
              className={cn(
                'uppercase font-medium px-2.5 py-0.5 hidden md:inline-flex',
                editingVoucher.status === 'posted'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800'
                  : editingVoucher.status === 'draft'
                    ? 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800/50 dark:text-slate-400 dark:border-slate-700'
                    : '',
              )}
            >
              {editingVoucher.status}
            </Badge>
          </>
        )}

        {/* Reversed/Reversal Badges - Compact */}
        {(editingVoucher?.reversedByVoucherId || editingVoucher?.reversalOf) && (
          <div className="flex gap-2">
            {editingVoucher.reversedByVoucherId && (
              <Badge
                variant="outline"
                className="text-orange-600 border-orange-200 bg-orange-50 text-[10px] h-5 px-1.5"
              >
                Reversed
              </Badge>
            )}
            {editingVoucher.reversalOf && (
              <Badge
                variant="outline"
                className="text-blue-600 border-blue-200 bg-blue-50 text-[10px] h-5 px-1.5"
              >
                Reversal
              </Badge>
            )}
          </div>
        )}
      </div>

      {/* Right: Actions & Auto-save Status */}
      <div className="flex items-center gap-3">
        {/* Auto-save Indicator */}
        <div className="hidden lg:flex items-center gap-2 text-xs text-muted-foreground mr-2">
          {autoSaveStatus === 'saving' && (
            <>
              <Loader2 className="h-3 w-3 animate-spin" />
              <span>Saving...</span>
            </>
          )}
          {autoSaveStatus === 'saved' && lastSavedAt && (
            <span>Saved {formatDistanceToNow(lastSavedAt, { addSuffix: true })}</span>
          )}
          {autoSaveStatus === 'error' && (
            <span className="text-destructive flex items-center gap-1">
              <AlertCircle className="h-3 w-3" />
              {autoSaveError || 'Save failed'}
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          {/* Template Button */}
          <Button
            variant="ghost"
            size="sm"
            onClick={onTemplateClick}
            disabled={loadingAccounts || formDisabled || isEditing}
            className="h-8 px-2 text-muted-foreground hover:text-purple-600"
            title="Apply Template"
          >
            <Sparkles className="h-4 w-4" />
          </Button>

          <Separator orientation="vertical" className="h-6" />

          {/* Primary Actions */}
          <Button
            type="button"
            onClick={onSaveClick}
            disabled={formDisabled || saving}
            variant="outline"
            size="sm"
            className="h-8"
          >
            {saving ? (
              <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
            ) : (
              <Save className="mr-2 h-3.5 w-3.5" />
            )}
            Save Draft
          </Button>

          {canPost && (
            <Button
              type="button"
              onClick={onPostClick}
              disabled={posting || formDisabled}
              size="sm"
              className="h-8 bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              {posting ? (
                <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
              ) : (
                <CheckCircle className="mr-2 h-3.5 w-3.5" />
              )}
              Post
            </Button>
          )}

          {/* Secondary Actions (Unpost/Reverse) */}
          {(canUnpost || canReverse) && (
            <div className="flex gap-2">
              {canUnpost && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={onUnpostClick}
                  disabled={unposting || formDisabled}
                  className="h-8 text-destructive border-destructive/20 hover:bg-destructive/5"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                </Button>
              )}
              {canReverse && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={onReverseClick}
                  disabled={reversing || formDisabled}
                  className="h-8 text-orange-600 border-orange-200 hover:bg-orange-50"
                >
                  <ArrowLeftRight className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
