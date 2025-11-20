'use client'

import { useState, useTransition, useEffect, useMemo } from 'react'
import { toast } from 'sonner'

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent } from '@/components/ui/card'
import { vatService } from '@/services/vat'
import { getPurchaseBillById } from '@/services/purchaseBill'
import type { VATCorrectionCreateRequest, VATCorrectionDTO } from '@/types/vat'
import type { PurchaseBillDTO } from '@/types/purchaseBill'

interface VATCorrectionDialogProps {
  open: boolean
  onOpenChange(open: boolean): void
  defaultBillId?: string
  onCreated?(correction: VATCorrectionDTO): void
}

export function VATCorrectionDialog({
  open,
  onOpenChange,
  defaultBillId,
  onCreated,
}: VATCorrectionDialogProps) {
  const [billId, setBillId] = useState(defaultBillId ?? '')
  const [lineId, setLineId] = useState('')
  const [newAmount, setNewAmount] = useState('')
  const [reason, setReason] = useState('')
  const [pending, startTransition] = useTransition()
  const [bill, setBill] = useState<PurchaseBillDTO | null>(null)
  const [loadingBill, setLoadingBill] = useState(false)

  const reset = () => {
    setBillId(defaultBillId ?? '')
    setLineId('')
    setNewAmount('')
    setReason('')
    setBill(null)
  }

  // Fetch bill details when billId changes
  useEffect(() => {
    if (billId && billId.trim().length > 0) {
      setLoadingBill(true)
      getPurchaseBillById(billId)
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
  }, [billId])

  // Calculate current VAT amount (from bill or line)
  const currentVatAmount = useMemo(() => {
    if (!bill) return null
    if (lineId && lineId.trim().length > 0) {
      const line = bill.lines?.find((l) => l.id === lineId)
      return line?.vatAmount ? parseFloat(String(line.vatAmount)) : null
    }
    return bill.vatAmount ? parseFloat(String(bill.vatAmount)) : null
  }, [bill, lineId])

  // Calculate diff preview
  const diffPreview = useMemo(() => {
    if (!currentVatAmount || !newAmount) return null
    const newAmountNum = parseFloat(newAmount)
    if (isNaN(newAmountNum)) return null
    const diff = newAmountNum - currentVatAmount
    return {
      old: currentVatAmount,
      new: newAmountNum,
      diff,
      isIncrease: diff > 0,
    }
  }, [currentVatAmount, newAmount])

  const handleClose = (next: boolean) => {
    if (!next) {
      reset()
    }
    onOpenChange(next)
  }

  const handleSubmit = () => {
    if (!billId) {
      toast.error('Purchase bill ID is required')
      return
    }
    if (!newAmount) {
      toast.error('New VAT amount is required')
      return
    }
    if (!reason || reason.trim().length < 5) {
      toast.error('Reason must be at least 5 characters')
      return
    }

    const request: VATCorrectionCreateRequest = {
      purchaseBillId: billId,
      newVatAmount: newAmount,
      reason: reason.trim(),
    }
    if (lineId) {
      request.purchaseBillLineId = lineId
    }

    startTransition(async () => {
      try {
        const correction = await vatService.createCorrection(request)
        toast.success('VAT correction submitted')
        onCreated?.(correction)
        handleClose(false)
      } catch (error) {
        toast.error(`Failed to create correction: ${String(error)}`)
      }
    })
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create VAT Correction</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="bill-id">Purchase Bill ID</Label>
            <Input
              id="bill-id"
              placeholder="e.g. 5a0b0f4b-..."
              value={billId}
              onChange={(e) => setBillId(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="line-id">Bill Line ID (optional)</Label>
            <Input
              id="line-id"
              placeholder="If blank, correction applies to bill total"
              value={lineId}
              onChange={(e) => setLineId(e.target.value)}
            />
          </div>
          {loadingBill && (
            <div className="text-sm text-muted-foreground">Loading bill details...</div>
          )}
          {bill && currentVatAmount !== null && (
            <div className="space-y-2">
              <Label>Current VAT Amount</Label>
              <div className="text-lg font-mono font-semibold">
                {currentVatAmount.toLocaleString('vi-VN', {
                  style: 'currency',
                  currency: 'VND',
                })}
              </div>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="new-amount">New VAT Amount</Label>
            <Input
              id="new-amount"
              type="number"
              min="0"
              step="0.01"
              placeholder="Enter new VAT amount"
              value={newAmount}
              onChange={(e) => setNewAmount(e.target.value)}
            />
          </div>
          {diffPreview && (
            <Card>
              <CardContent className="pt-6">
                <div className="space-y-3">
                  <div className="text-sm font-semibold">Diff Preview</div>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <div className="text-xs text-muted-foreground uppercase tracking-wide">
                        Old Amount
                      </div>
                      <div className="font-mono text-base">
                        {diffPreview.old.toLocaleString('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                        })}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground uppercase tracking-wide">
                        New Amount
                      </div>
                      <div className="font-mono text-base">
                        {diffPreview.new.toLocaleString('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                        })}
                      </div>
                    </div>
                    <div className="col-span-2">
                      <div className="text-xs text-muted-foreground uppercase tracking-wide">
                        Difference
                      </div>
                      <div
                        className={`font-mono text-lg font-semibold ${
                          diffPreview.isIncrease ? 'text-green-600' : 'text-red-600'
                        }`}
                      >
                        {diffPreview.isIncrease ? '+' : ''}
                        {diffPreview.diff.toLocaleString('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}
          <div className="space-y-2">
            <Label htmlFor="reason">Reason</Label>
            <Textarea
              id="reason"
              placeholder="Explain why this correction is necessary"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => handleClose(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={pending}>
            {pending ? 'Submitting...' : 'Submit Correction'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
