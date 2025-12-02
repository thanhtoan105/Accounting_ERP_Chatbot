'use client'

import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Plus, CheckCircle2, XCircle, AlertCircle, Loader2 } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'

import {
  createAdjustment,
  approveAdjustment,
  rejectAdjustment,
  postAdjustment,
  type AdjustmentType,
  type AdjustmentStatus,
  type ReconciliationAdjustmentDTO,
  type BankStatementLineDTO,
  type CreateAdjustmentRequest,
} from '../../services/bankReconciliation'

// ============================================================================
// Types
// ============================================================================

interface CreateAdjustmentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  reconciliationId: string
  statementLine?: BankStatementLineDTO | null
  existingAdjustment?: ReconciliationAdjustmentDTO | null
  onSuccess?: () => void
}

// ============================================================================
// Constants
// ============================================================================

const ADJUSTMENT_TYPES: { value: AdjustmentType; labelKey: string; defaultAccount: string }[] = [
  { value: 'BANK_FEE', labelKey: 'bankFee', defaultAccount: '6425' },
  { value: 'INTEREST_INCOME', labelKey: 'interestIncome', defaultAccount: '5158' },
  { value: 'INTEREST_EXPENSE', labelKey: 'interestExpense', defaultAccount: '6358' },
  { value: 'OTHER', labelKey: 'other', defaultAccount: '' },
]

const STATUS_COLORS: Record<AdjustmentStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-blue-100 text-blue-800',
  POSTED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
}

// ============================================================================
// Helper Functions
// ============================================================================

function formatCurrency(value: number | undefined | null): string {
  if (value === undefined || value === null) return '-'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

// ============================================================================
// Main Component
// ============================================================================

export function CreateAdjustmentDialog({
  open,
  onOpenChange,
  reconciliationId,
  statementLine,
  existingAdjustment,
  onSuccess,
}: CreateAdjustmentDialogProps) {
  const { t } = useTranslation()

  // Form state
  const [adjustmentType, setAdjustmentType] = useState<AdjustmentType>('BANK_FEE')
  const [amount, setAmount] = useState<string>('')
  const [description, setDescription] = useState<string>('')
  const [accountCode, setAccountCode] = useState<string>('')
  const [rejectReason, setRejectReason] = useState<string>('')

  // UI state
  const [creating, setCreating] = useState(false)
  const [approving, setApproving] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [posting, setPosting] = useState(false)
  const [showRejectDialog, setShowRejectDialog] = useState(false)

  const isEditMode = !!existingAdjustment
  const isLoading = creating || approving || rejecting || posting

  // Pre-fill values when statement line is provided
  useEffect(() => {
    if (open && statementLine && !existingAdjustment) {
      // Calculate the net amount from the statement line
      const netAmount = Math.abs(
        (statementLine.debitAmount || 0) - (statementLine.creditAmount || 0),
      )
      setAmount(netAmount.toString())

      // Pre-fill description from statement line
      setDescription(statementLine.description || '')

      // Auto-detect adjustment type based on description patterns
      const descLower = (statementLine.description || '').toLowerCase()
      if (descLower.includes('fee') || descLower.includes('charge') || descLower.includes('phi')) {
        setAdjustmentType('BANK_FEE')
        setAccountCode('6425')
      } else if (
        descLower.includes('interest') ||
        descLower.includes('lai') ||
        descLower.includes('lãi')
      ) {
        // Determine if it's income or expense based on credit/debit
        if ((statementLine.creditAmount || 0) > (statementLine.debitAmount || 0)) {
          setAdjustmentType('INTEREST_INCOME')
          setAccountCode('5158')
        } else {
          setAdjustmentType('INTEREST_EXPENSE')
          setAccountCode('6358')
        }
      } else {
        setAdjustmentType('OTHER')
        setAccountCode('')
      }
    }

    // Pre-fill from existing adjustment
    if (open && existingAdjustment) {
      setAdjustmentType(existingAdjustment.adjustmentType)
      setAmount(existingAdjustment.amount.toString())
      setDescription(existingAdjustment.description)
      setAccountCode(existingAdjustment.accountCode || '')
    }
  }, [open, statementLine, existingAdjustment])

  // Reset form when dialog closes
  useEffect(() => {
    if (!open) {
      setAdjustmentType('BANK_FEE')
      setAmount('')
      setDescription('')
      setAccountCode('')
      setRejectReason('')
      setShowRejectDialog(false)
    }
  }, [open])

  // Update account code when adjustment type changes
  const handleTypeChange = (type: AdjustmentType) => {
    setAdjustmentType(type)
    const typeConfig = ADJUSTMENT_TYPES.find((t) => t.value === type)
    if (typeConfig?.defaultAccount) {
      setAccountCode(typeConfig.defaultAccount)
    }
  }

  // ============================================================================
  // API Handlers
  // ============================================================================

  const handleCreate = async () => {
    if (!amount || parseFloat(amount) <= 0) {
      toast.error('Please enter a valid amount')
      return
    }
    if (!description.trim()) {
      toast.error('Please enter a description')
      return
    }

    try {
      setCreating(true)

      const request: CreateAdjustmentRequest = {
        statementLineId: statementLine?.id,
        adjustmentType,
        amount: parseFloat(amount),
        description: description.trim(),
        accountCode: accountCode || undefined,
      }

      await createAdjustment(reconciliationId, request)

      toast.success(t('common.success'), {
        description: 'Adjustment created successfully',
      })

      onSuccess?.()
      onOpenChange(false)
    } catch (err: any) {
      toast.error(t('common.error'), {
        description: err?.error?.message || err?.message || 'Failed to create adjustment',
      })
    } finally {
      setCreating(false)
    }
  }

  const handleApprove = async () => {
    if (!existingAdjustment) return

    try {
      setApproving(true)
      await approveAdjustment(reconciliationId, existingAdjustment.id)

      toast.success(t('common.success'), {
        description: 'Adjustment approved successfully',
      })

      onSuccess?.()
      onOpenChange(false)
    } catch (err: any) {
      toast.error(t('common.error'), {
        description: err?.error?.message || err?.message || 'Failed to approve adjustment',
      })
    } finally {
      setApproving(false)
    }
  }

  const handleReject = async () => {
    if (!existingAdjustment) return
    if (!rejectReason.trim()) {
      toast.error('Please provide a rejection reason')
      return
    }

    try {
      setRejecting(true)
      await rejectAdjustment(reconciliationId, existingAdjustment.id, rejectReason.trim())

      toast.success(t('common.success'), {
        description: 'Adjustment rejected',
      })

      onSuccess?.()
      onOpenChange(false)
    } catch (err: any) {
      toast.error(t('common.error'), {
        description: err?.error?.message || err?.message || 'Failed to reject adjustment',
      })
    } finally {
      setRejecting(false)
      setShowRejectDialog(false)
    }
  }

  const handlePost = async () => {
    if (!existingAdjustment) return

    try {
      setPosting(true)
      await postAdjustment(reconciliationId, existingAdjustment.id)

      toast.success(t('common.success'), {
        description: 'Adjustment posted and voucher created',
      })

      onSuccess?.()
      onOpenChange(false)
    } catch (err: any) {
      toast.error(t('common.error'), {
        description: err?.error?.message || err?.message || 'Failed to post adjustment',
      })
    } finally {
      setPosting(false)
    }
  }

  const handleClose = () => {
    if (!isLoading) {
      onOpenChange(false)
    }
  }

  // ============================================================================
  // Render
  // ============================================================================

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Plus className="h-5 w-5" />
            {isEditMode
              ? t('bankReconciliation.adjustment.editTitle')
              : t('bankReconciliation.adjustment.createTitle')}
          </DialogTitle>
          <DialogDescription>
            {isEditMode
              ? 'View or manage this adjustment'
              : 'Create an adjustment for unmatched bank transactions (e.g., bank fees, interest)'}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Existing Adjustment Status */}
          {existingAdjustment && (
            <div className="flex items-center justify-between p-3 bg-muted rounded-lg">
              <span className="text-sm font-medium">Status:</span>
              <Badge className={STATUS_COLORS[existingAdjustment.status]}>
                {t(`bankReconciliation.adjustmentStatus.${existingAdjustment.status.toLowerCase()}`)}
              </Badge>
            </div>
          )}

          {/* Linked Statement Line Info */}
          {statementLine && (
            <Alert>
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>{t('bankReconciliation.adjustment.linkedStatementLine')}</AlertTitle>
              <AlertDescription className="space-y-1">
                <div className="text-sm">
                  <span className="text-muted-foreground">Date:</span>{' '}
                  {statementLine.transactionDate}
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">Description:</span>{' '}
                  {statementLine.description}
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">Amount:</span>{' '}
                  {formatCurrency(
                    (statementLine.debitAmount || 0) - (statementLine.creditAmount || 0),
                  )}
                </div>
              </AlertDescription>
            </Alert>
          )}

          <Separator />

          {/* Adjustment Type */}
          <div className="space-y-2">
            <Label htmlFor="adjustment-type">
              {t('bankReconciliation.adjustment.type')} <span className="text-red-500">*</span>
            </Label>
            <Select
              value={adjustmentType}
              onValueChange={(v) => handleTypeChange(v as AdjustmentType)}
              disabled={isEditMode}
            >
              <SelectTrigger id="adjustment-type" data-testid="adjustment-type-select">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ADJUSTMENT_TYPES.map((type) => (
                  <SelectItem key={type.value} value={type.value}>
                    {t(`bankReconciliation.adjustmentType.${type.labelKey}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Amount */}
          <div className="space-y-2">
            <Label htmlFor="amount">
              {t('bankReconciliation.adjustment.amount')} <span className="text-red-500">*</span>
            </Label>
            <Input
              id="amount"
              type="number"
              step="0.01"
              min="0"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              disabled={isEditMode}
              data-testid="adjustment-amount-input"
            />
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="description">
              {t('bankReconciliation.adjustment.description')} <span className="text-red-500">*</span>
            </Label>
            <Textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Enter adjustment description..."
              rows={2}
              disabled={isEditMode}
              data-testid="adjustment-description-input"
            />
          </div>

          {/* GL Account Code */}
          <div className="space-y-2">
            <Label htmlFor="account-code">{t('bankReconciliation.adjustment.accountCode')}</Label>
            <Input
              id="account-code"
              value={accountCode}
              onChange={(e) => setAccountCode(e.target.value)}
              placeholder="e.g., 6425 (Bank Fees)"
              disabled={isEditMode}
              data-testid="adjustment-account-input"
            />
            <p className="text-xs text-muted-foreground">
              {t('bankReconciliation.adjustment.voucherCreated')}
            </p>
          </div>

          {/* Approval Notice */}
          {!isEditMode && (
            <Alert>
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                {t('bankReconciliation.adjustment.approvalRequired')}
              </AlertDescription>
            </Alert>
          )}

          {/* Rejection Reason Dialog */}
          {showRejectDialog && (
            <div className="space-y-2 p-3 border rounded-lg bg-red-50">
              <Label htmlFor="reject-reason">
                {t('bankReconciliation.adjustment.rejectReason')}{' '}
                <span className="text-red-500">*</span>
              </Label>
              <Textarea
                id="reject-reason"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="Enter reason for rejection..."
                rows={2}
                data-testid="reject-reason-input"
              />
              <div className="flex gap-2 justify-end">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setShowRejectDialog(false)}
                  disabled={rejecting}
                >
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleReject}
                  disabled={rejecting || !rejectReason.trim()}
                >
                  {rejecting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Confirm Rejection
                </Button>
              </div>
            </div>
          )}
        </div>

        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={handleClose} disabled={isLoading}>
            {t('common.cancel')}
          </Button>

          {/* Create Mode Actions */}
          {!isEditMode && (
            <Button onClick={handleCreate} disabled={creating} data-testid="create-adjustment-button">
              {creating && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {t('common.create')}
            </Button>
          )}

          {/* Edit Mode Actions - Based on Status */}
          {isEditMode && existingAdjustment?.status === 'PENDING' && !showRejectDialog && (
            <>
              <Button
                variant="destructive"
                onClick={() => setShowRejectDialog(true)}
                disabled={isLoading}
              >
                <XCircle className="mr-2 h-4 w-4" />
                {t('bankReconciliation.adjustment.reject')}
              </Button>
              <Button onClick={handleApprove} disabled={approving}>
                {approving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                <CheckCircle2 className="mr-2 h-4 w-4" />
                {t('bankReconciliation.adjustment.approve')}
              </Button>
            </>
          )}

          {isEditMode && existingAdjustment?.status === 'APPROVED' && (
            <Button onClick={handlePost} disabled={posting}>
              {posting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {t('bankReconciliation.adjustment.post')}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
