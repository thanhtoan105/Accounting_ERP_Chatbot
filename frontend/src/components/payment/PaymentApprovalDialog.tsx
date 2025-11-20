import { useState } from 'react'
import { CheckCircle, Loader2 } from 'lucide-react'
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
import type { APPaymentListDTO } from '@/types/payment'
import { postPayment } from '@/services/payment'

interface PaymentApprovalDialogProps {
  payment: APPaymentListDTO | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onApproved: () => void
}

export function PaymentApprovalDialog({
  payment,
  open,
  onOpenChange,
  onApproved,
}: PaymentApprovalDialogProps) {
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (!payment) {
    return null
  }

  const handleApprove = async () => {
    if (!payment) return

    setSubmitting(true)
    try {
      await postPayment(payment.id)

      toast.success('Payment approved and posted successfully', {
        description: `Payment ${payment.paymentNumber} has been posted. Allocations and balances have been updated.`,
      })

      setReason('')
      onOpenChange(false)
      onApproved()
    } catch (error: any) {
      const errorMessage = error?.message || 'Failed to approve payment'
      toast.error('Failed to approve payment', {
        description: errorMessage,
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setReason('')
        }
        onOpenChange(next)
      }}
    >
      <DialogContent className="sm:max-w-[525px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <CheckCircle className="h-5 w-5 text-green-600" />
            Approve Payment
          </DialogTitle>
          <DialogDescription>
            You are about to approve payment{' '}
            <span className="font-medium">{payment.paymentNumber}</span> to{' '}
            <span className="font-medium">{payment.supplierName || payment.supplierCode}</span>.
            This will post the payment, generate a voucher, and update related bill balances.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <label htmlFor="payment-approval-reason" className="text-sm font-medium">
              Approval Note (Optional)
            </label>
            <Textarea
              id="payment-approval-reason"
              placeholder="Add an optional note for this approval..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
              maxLength={1000}
              className="resize-none"
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
            onClick={handleApprove}
            disabled={submitting || payment.status !== 'PENDING_APPROVAL'}
          >
            {submitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Approving...
              </>
            ) : (
              <>
                <CheckCircle className="mr-2 h-4 w-4" />
                Approve &amp; Post
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
