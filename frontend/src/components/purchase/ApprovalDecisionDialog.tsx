import { useState } from 'react'
import { CheckCircle, XCircle, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { approvePurchaseBill, rejectPurchaseBill } from '@/services/purchaseBill'

interface ApprovalDecisionDialogProps {
  billId: string
  billNumber: string
  action: 'approve' | 'reject' | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: () => void
}

export function ApprovalDecisionDialog({
  billId,
  billNumber,
  action,
  open,
  onOpenChange,
  onSuccess,
}: ApprovalDecisionDialogProps) {
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const isApproval = action === 'approve'
  const isRejection = action === 'reject'

  const handleSubmit = async () => {
    if (isRejection && !reason.trim()) {
      toast.error('Rejection reason is required')
      return
    }

    setSubmitting(true)
    try {
      if (isApproval) {
        await approvePurchaseBill(billId, reason.trim() || undefined)
        toast.success(`Purchase bill ${billNumber} approved successfully`)
      } else if (isRejection) {
        await rejectPurchaseBill(billId, reason.trim())
        toast.success(`Purchase bill ${billNumber} rejected`)
      }

      setReason('')
      onOpenChange(false)
      onSuccess()
    } catch (error: any) {
      const errorMessage = error?.message || (isApproval ? 'Approval failed' : 'Rejection failed')
      toast.error(errorMessage)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[525px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {isApproval ? (
              <>
                <CheckCircle className="h-5 w-5 text-green-600" />
                Approve Purchase Bill
              </>
            ) : (
              <>
                <XCircle className="h-5 w-5 text-red-600" />
                Reject Purchase Bill
              </>
            )}
          </DialogTitle>
          <DialogDescription>
            {isApproval
              ? `You are about to approve purchase bill ${billNumber}. This will post the bill to the ledger.`
              : `You are about to reject purchase bill ${billNumber}. Please provide a reason for rejection.`}
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="reason">
              {isRejection ? 'Rejection Reason *' : 'Approval Reason (Optional)'}
            </Label>
            <Textarea
              id="reason"
              placeholder={
                isRejection
                  ? 'Enter reason for rejection...'
                  : 'Enter optional reason for approval...'
              }
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
              maxLength={1000}
              className="resize-none"
              required={isRejection}
            />
            <p className="text-sm text-muted-foreground">{reason.length}/1000 characters</p>
          </div>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setReason('')
              onOpenChange(false)
            }}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant={isApproval ? 'default' : 'destructive'}
            onClick={handleSubmit}
            disabled={submitting || (isRejection && !reason.trim())}
          >
            {submitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {isApproval ? 'Approving...' : 'Rejecting...'}
              </>
            ) : (
              <>
                {isApproval ? (
                  <CheckCircle className="mr-2 h-4 w-4" />
                ) : (
                  <XCircle className="mr-2 h-4 w-4" />
                )}
                {isApproval ? 'Approve' : 'Reject'}
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
