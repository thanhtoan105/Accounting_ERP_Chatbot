'use client'

import { format } from 'date-fns'
import { toast } from 'sonner'
import { useTransition, useEffect, useState } from 'react'

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ExternalLink } from 'lucide-react'
import type { VATCorrectionDTO } from '@/types/vat'
import { vatService } from '@/services/vat'
import { getPurchaseBillById } from '@/services/purchaseBill'
import type { PurchaseBillDTO } from '@/types/purchaseBill'

interface ApproveVATCorrectionDialogProps {
  correction: VATCorrectionDTO | null
  open: boolean
  onOpenChange(open: boolean): void
  onApproved?(correction: VATCorrectionDTO): void
}

export function ApproveVATCorrectionDialog({
  correction,
  open,
  onOpenChange,
  onApproved,
}: ApproveVATCorrectionDialogProps) {
  const [pending, startTransition] = useTransition()
  const [bill, setBill] = useState<PurchaseBillDTO | null>(null)
  const [loadingBill, setLoadingBill] = useState(false)

  // Fetch bill details when correction changes
  useEffect(() => {
    if (correction?.purchaseBillId) {
      setLoadingBill(true)
      getPurchaseBillById(correction.purchaseBillId)
        .then((billData) => {
          setBill(billData)
        })
        .catch((error) => {
          toast.error(`Failed to load bill: ${String(error)}`)
          setBill(null)
        })
        .finally(() => {
          setLoadingBill(false)
        })
    } else {
      setBill(null)
    }
  }, [correction?.purchaseBillId])

  const handleApprove = () => {
    if (!correction) return
    startTransition(async () => {
      try {
        const updated = await vatService.approveCorrection(correction.id)
        toast.success('VAT correction approved')
        onApproved?.(updated)
        onOpenChange(false)
      } catch (error) {
        toast.error(`Failed to approve correction: ${String(error)}`)
      }
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Approve VAT Correction</DialogTitle>
        </DialogHeader>
        {correction ? (
          <div className="space-y-4 text-sm text-muted-foreground">
            <div>
              <span className="font-semibold text-foreground">Bill:</span>{' '}
              <span className="font-mono">{correction.purchaseBillId}</span>
            </div>
            <div>
              <span className="font-semibold text-foreground">Line:</span>{' '}
              <span className="font-mono">{correction.purchaseBillLineId ?? 'Bill total'}</span>
            </div>
            <div className="grid grid-cols-2 gap-4 rounded border p-3">
              <div>
                <div className="text-xs uppercase tracking-wide">Old VAT</div>
                <div className="font-mono text-base text-foreground">{correction.oldVatAmount}</div>
              </div>
              <div>
                <div className="text-xs uppercase tracking-wide">New VAT</div>
                <div className="font-mono text-base text-foreground">{correction.newVatAmount}</div>
              </div>
              <div className="col-span-2">
                <div className="text-xs uppercase tracking-wide">Difference</div>
                <div className="font-mono text-base text-foreground">{correction.difference}</div>
              </div>
            </div>
            <div>
              <div className="text-xs uppercase tracking-wide">Reason</div>
              <p className="mt-1 text-foreground">{correction.reason}</p>
            </div>
            <div>
              <div className="text-xs uppercase tracking-wide">Submitted</div>
              <div>
                User #{correction.correctedById} –{' '}
                {format(new Date(correction.correctedAt), 'dd/MM/yyyy HH:mm')}
              </div>
            </div>
            {loadingBill && (
              <div className="text-sm text-muted-foreground">Loading bill details...</div>
            )}
            {bill && (
              <Card>
                <CardContent className="pt-6">
                  <div className="space-y-3">
                    <div className="text-sm font-semibold">Bill Impact</div>
                    <div className="space-y-2 text-sm">
                      <div>
                        <span className="text-muted-foreground">Bill Number:</span>{' '}
                        <span className="font-mono font-semibold">{bill.billNumber}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Status:</span>{' '}
                        <span className="font-semibold">{bill.status}</span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Current Bill VAT:</span>{' '}
                        <span className="font-mono">
                          {bill.vatAmount.toLocaleString('vi-VN', {
                            style: 'currency',
                            currency: 'VND',
                          })}
                        </span>
                      </div>
                      {bill.postedVoucherId && (
                        <div>
                          <span className="text-muted-foreground">Posted Voucher:</span>{' '}
                          <span className="font-mono">{bill.postedVoucherId}</span>
                          <Button
                            variant="link"
                            size="sm"
                            className="ml-2 h-auto p-0"
                            onClick={() => {
                              // Navigate to voucher detail (if route exists)
                              window.open(`/accounting/vouchers/${bill.postedVoucherId}`, '_blank')
                            }}
                          >
                            <ExternalLink className="h-3 w-3" />
                          </Button>
                        </div>
                      )}
                      {bill.status === 'POSTED' && bill.postedVoucherId && (
                        <div className="mt-2 rounded bg-yellow-50 p-2 text-xs text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-200">
                          ⚠️ This bill is already posted. Approving this correction will require
                          voucher adjustment or reversal.
                        </div>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        ) : (
          <div className="text-sm text-muted-foreground">Select a correction to approve.</div>
        )}

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={handleApprove}
            disabled={!correction || pending}
            className="bg-primary text-primary-foreground"
          >
            {pending ? 'Approving...' : 'Approve'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
