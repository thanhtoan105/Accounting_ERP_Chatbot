import { formatDistanceToNow } from 'date-fns'
import {
  AlertCircle,
  ArrowLeftRight,
  CheckCircle,
  FileText,
  Loader2,
  RotateCcw,
  Save,
  ShieldAlert,
  Sparkles,
} from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { VoucherPageHeader } from '@/features/accounting/components/voucher-ui'
import type { VoucherDTO } from '@/types/voucher'

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
  voucherId,
  attachmentCount,
  onAttachmentClick,
  onTemplateClick,
  onValidateClick,
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
  validationSummary,
  onValidationSummaryClick,
}: VoucherFormHeaderProps) {
  return (
    <div className="voucher-form-header space-y-4">
      <VoucherPageHeader
        title={isEditing ? 'Edit accounting voucher' : 'Create accounting voucher'}
        subtitle="Quickly enter by keyboard, apply templates and automatically validate before posting."
        icon={<FileText className="h-6 w-6" />}
      >
        {/* Status badges */}
        <div className="flex items-center gap-2 flex-wrap">
          {editingVoucher?.status && (
            <Badge
              variant="outline"
              className={`uppercase font-medium ${
                editingVoucher.status === 'posted'
                  ? 'bg-voucher-status-posted/10 text-voucher-status-posted border-voucher-status-posted/30'
                  : editingVoucher.status === 'draft'
                    ? 'bg-voucher-status-draft/10 text-voucher-status-draft border-voucher-status-draft/30'
                    : ''
              }`}
            >
              {editingVoucher.status}
            </Badge>
          )}
          {editingVoucher?.voucherNumber && (
            <Badge variant="secondary" className="font-mono">
              {editingVoucher.voucherNumber}
            </Badge>
          )}
          {editingVoucher?.reversedByVoucherId && (
            <Badge
              variant="outline"
              className="cursor-pointer hover:bg-orange-100 transition-colors"
              onClick={() => {
                window.location.href = `/vouchers/${editingVoucher.reversedByVoucherId}`
              }}
            >
              Reversed by
            </Badge>
          )}
          {editingVoucher?.reversalOf && (
            <Badge
              variant="outline"
              className="cursor-pointer hover:bg-blue-100 transition-colors"
              onClick={() => {
                window.location.href = `/vouchers/${editingVoucher.reversalOf}`
              }}
            >
              Reversal of
            </Badge>
          )}
          {voucherId && (
            <Badge
              variant="outline"
              className="gap-1 cursor-pointer hover:bg-accent transition-colors"
              onClick={onAttachmentClick}
            >
              <FileText className="size-3" />
              {attachmentCount} attachments
            </Badge>
          )}
        </div>
      </VoucherPageHeader>

      {/* Action Buttons Row */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            type="button"
            onClick={onTemplateClick}
            disabled={loadingAccounts || formDisabled || isEditing}
            className="voucher-button-secondary"
          >
            <Sparkles className="mr-2 h-4 w-4" />
            Apply template
          </Button>
          <Button
            variant="outline"
            type="button"
            onClick={onValidateClick}
            disabled={formDisabled}
            className="voucher-button-secondary"
          >
            {saving ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <ShieldAlert className="mr-2 h-4 w-4" />
            )}
            Validate
          </Button>
          <Button
            type="button"
            onClick={onSaveClick}
            disabled={formDisabled}
            className="voucher-button-primary"
          >
            {saving ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Save className="mr-2 h-4 w-4" />
            )}
            Save draft
          </Button>

          {/* Post/Unpost/Reverse buttons */}
          {canPost && (
            <Button
              type="button"
              onClick={onPostClick}
              disabled={posting || formDisabled}
              className="bg-green-600 hover:bg-green-700 text-white"
            >
              {posting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle className="mr-2 h-4 w-4" />
              )}
              Post
            </Button>
          )}
          {canUnpost && (
            <Button
              type="button"
              variant="outline"
              onClick={onUnpostClick}
              disabled={unposting || formDisabled}
            >
              {unposting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <RotateCcw className="mr-2 h-4 w-4" />
              )}
              Unpost
            </Button>
          )}
          {canReverse && (
            <Button
              type="button"
              variant="outline"
              onClick={onReverseClick}
              disabled={reversing || formDisabled}
            >
              {reversing ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <ArrowLeftRight className="mr-2 h-4 w-4" />
              )}
              Reverse
            </Button>
          )}
        </div>

        {/* Status indicators */}
        <div className="flex items-center gap-4">
          {/* Auto-save status */}
          <div className="text-xs text-muted-foreground">
            {autoSaveStatus === 'saving' && (
              <span className="flex items-center gap-1">
                <Loader2 className="h-3 w-3 animate-spin" />
                Saving draft...
              </span>
            )}
            {autoSaveStatus === 'saved' && lastSavedAt && (
              <span className="text-voucher-status-posted">
                Draft saved {formatDistanceToNow(lastSavedAt, { addSuffix: true })}
              </span>
            )}
            {autoSaveStatus === 'error' && (
              <span className="text-destructive flex items-center gap-1">
                <AlertCircle className="h-3 w-3" />
                {autoSaveError || 'Cannot save draft'}
              </span>
            )}
          </div>

          {/* Validation summary */}
          {validationSummary.errorCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              type="button"
              onClick={onValidationSummaryClick}
              className="text-destructive border-destructive hover:bg-destructive/10"
            >
              <AlertCircle className="mr-2 h-4 w-4" />
              {validationSummary.errorCount} lines with errors ({validationSummary.totalErrors}{' '}
              errors)
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
