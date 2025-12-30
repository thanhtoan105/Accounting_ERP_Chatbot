'use client'

import {
  ArrowLeft,
  Save,
  Send,
  RotateCcw,
  Loader2,
  CheckCircle,
  XCircle,
  FileText,
  Check,
  X,
} from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { PurchaseBillDTO } from '@/types/purchaseBill'
import { PurchaseBillStatusBadge } from '@/features/accounting/components/purchase-ui'

interface PurchaseBillFormHeaderProps {
  isEditing: boolean
  isReadOnly: boolean
  title: string
  billNumber?: string
  attachmentCount: number
  autoSaveStatus: 'idle' | 'saving' | 'saved' | 'error'
  lastSavedAt: Date | null
  canUndo: boolean
  canRedo: boolean
  saving: boolean
  validating: boolean
  submittingForApproval: boolean
  hasBlockingVatIssues: boolean
  editingBill: PurchaseBillDTO | null
  onUndo: () => void
  onRedo: () => void
  onValidate: () => void
  onSave: () => void
  onSubmitForApproval: () => void
  onAttachmentClick: () => void
  onBack: () => void
  onReject: () => void
  onApprove: () => void
}

export function PurchaseBillFormHeader({
  isEditing,
  isReadOnly,
  title,
  billNumber,
  attachmentCount,
  autoSaveStatus,
  lastSavedAt,
  canUndo,
  canRedo,
  saving,
  validating,
  submittingForApproval,
  hasBlockingVatIssues,
  editingBill,
  onUndo,
  onRedo,
  onValidate,
  onSave,
  onSubmitForApproval,
  onAttachmentClick,
  onBack,
  onReject,
  onApprove,
}: PurchaseBillFormHeaderProps) {
  return (
    <div className="voucher-page-header flex flex-col gap-4 border-b pb-4 lg:flex-row lg:items-center lg:justify-between bg-card p-6 rounded-lg shadow-sm border">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={onBack} className="h-8 w-8">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground voucher-font-heading">
              {title}
            </h1>
            {attachmentCount > 0 && (
              <Badge variant="secondary" className="text-xs font-normal">
                <FileText className="mr-1 h-3 w-3" />
                {attachmentCount}
              </Badge>
            )}
            {editingBill?.status && (
              <PurchaseBillStatusBadge status={editingBill.status} size="sm" />
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {isEditing
              ? `Bill Number: ${billNumber || '---'}`
              : 'Enter purchase bill details and line items'}
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        {/* Auto-save Status */}
        {!isEditing && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground mr-2">
            {autoSaveStatus === 'saving' && (
              <>
                <Loader2 className="h-3 w-3 animate-spin" />
                <span>Saving draft...</span>
              </>
            )}
            {autoSaveStatus === 'saved' && lastSavedAt && (
              <>
                <CheckCircle className="h-3 w-3 text-[var(--voucher-status-posted)]" />
                <span>Saved {formatDistanceToNow(lastSavedAt, { addSuffix: true })}</span>
              </>
            )}
            {autoSaveStatus === 'error' && (
              <>
                <XCircle className="h-3 w-3 text-destructive" />
                <span>Save failed</span>
              </>
            )}
          </div>
        )}

        {/* Undo/Redo */}
        <div className="flex items-center gap-1 border-r border-border/50 pr-3 mr-1">
          <Button
            variant="ghost"
            size="icon"
            onClick={onUndo}
            disabled={!canUndo || isReadOnly}
            className="h-8 w-8"
            title="Undo"
          >
            <RotateCcw className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={onRedo}
            disabled={!canRedo || isReadOnly}
            className="h-8 w-8"
            title="Redo"
          >
            <RotateCcw className="h-4 w-4 rotate-180" />
          </Button>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2 flex-wrap">
          {isEditing && (
            <Button variant="outline" size="sm" onClick={onAttachmentClick} className="gap-2">
              <FileText className="h-4 w-4" />
              Attachments
            </Button>
          )}

          <Button
            variant="outline"
            size="sm"
            onClick={onValidate}
            disabled={saving || validating}
            className="gap-2"
          >
            {validating ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Validating...
              </>
            ) : (
              'Validate'
            )}
          </Button>

          <Button
            onClick={onSave}
            disabled={saving || isReadOnly}
            size="sm"
            className="voucher-button-primary gap-2"
          >
            {saving ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : (
              <>
                <Save className="h-4 w-4" />
                {isEditing ? 'Update' : 'Save'}
              </>
            )}
          </Button>

          {/* Workflow Actions */}
          {isEditing && editingBill?.status === 'DRAFT' && (
            <Button
              variant="secondary"
              size="sm"
              onClick={onSubmitForApproval}
              disabled={submittingForApproval || hasBlockingVatIssues}
              className="ml-2 gap-2"
            >
              {submittingForApproval ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
              Submit
            </Button>
          )}

          {isEditing && editingBill?.status === 'PENDING_APPROVAL' && (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={onReject}
                className="ml-2 gap-2 border-destructive/20 text-destructive hover:bg-destructive/10 hover:text-destructive"
              >
                <X className="h-4 w-4" />
                Reject
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={onApprove}
                className="ml-1 gap-2 border-[var(--voucher-status-posted)]/20 text-[var(--voucher-status-posted)] hover:bg-[var(--voucher-status-posted)]/10"
              >
                <Check className="h-4 w-4" />
                Approve
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
