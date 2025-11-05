import { useState } from 'react'
// <CHANGE> Migrate from MUI to shadcn/ui dialog, inputs, buttons, alert
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription } from '@/components/ui/alert'
import type { VoucherListDTO } from '@/types/voucher'

interface DeleteVoucherDialogProps {
  open: boolean
  voucher: VoucherListDTO | null
  onClose: () => void
  onConfirm: (reason: string) => Promise<void>
}

/**
 * Dialog for confirming voucher deletion with reason input.
 */
export default function DeleteVoucherDialog({
  open,
  voucher,
  onClose,
  onConfirm,
}: DeleteVoucherDialogProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleClose = () => {
    if (!loading) {
      setReason('')
      setError(null)
      onClose()
    }
  }

  const handleConfirm = async () => {
    if (!reason.trim()) {
      setError('Deletion reason is required')
      return
    }

    if (!voucher) {
      return
    }

    try {
      setLoading(true)
      setError(null)
      await onConfirm(reason.trim())
      handleClose()
    } catch (err: any) {
      setError(err?.message || 'Failed to delete voucher. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  if (!voucher) {
    return null
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete Voucher</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="text-sm">Are you sure you want to delete this voucher?</div>
          <div className="rounded-md border p-3 text-sm">
            <div><strong>Voucher Number:</strong> {voucher.voucherNumber}</div>
            <div><strong>Date:</strong> {new Date(voucher.voucherDate).toLocaleDateString()}</div>
            <div><strong>Type/Description:</strong> {voucher.type}</div>
            <div><strong>Status:</strong> {voucher.status}</div>
          </div>
          <Alert>
            <AlertDescription>
              Only draft vouchers that are not referenced can be deleted. This action cannot be undone.
            </AlertDescription>
          </Alert>
          <div className="space-y-1">
            <label className="text-sm font-medium">Deletion Reason</label>
            <Textarea
              autoFocus
              rows={3}
              placeholder="Please provide a reason for deleting this voucher..."
              value={reason}
              onChange={(e) => { setReason(e.target.value); setError(null) }}
              disabled={loading}
            />
            <div className={`text-xs ${error ? 'text-destructive' : 'text-muted-foreground'}`}>
              {error || 'This reason will be logged in the audit trail'}
            </div>
          </div>
        </div>
        <DialogFooter className="mt-2">
          <Button variant="outline" onClick={handleClose} disabled={loading}>Cancel</Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={loading}>
            {loading ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
