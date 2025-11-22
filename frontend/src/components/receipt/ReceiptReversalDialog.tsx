'use client'

import { useState } from 'react'
import { Loader2, AlertTriangle } from 'lucide-react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { reverseReceipt } from '@/services/receipt'
import type { ARPaymentDTO } from '@/types/receipt'
import { format } from 'date-fns'

const reversalSchema = z.object({
  reason: z
    .string()
    .min(10, 'Reversal reason must be at least 10 characters')
    .max(500, 'Reversal reason must be at most 500 characters'),
})

type ReversalFormValues = z.infer<typeof reversalSchema>

export interface ReceiptReversalDialogProps {
  receipt: ARPaymentDTO | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

export function ReceiptReversalDialog({
  receipt,
  open,
  onOpenChange,
  onSuccess,
}: ReceiptReversalDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)

  const form = useForm<ReversalFormValues>({
    resolver: zodResolver(reversalSchema),
    defaultValues: {
      reason: '',
    },
  })

  const formatCurrency = (value: number | null | undefined) => {
    if (value == null) return '0'
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  const formatDate = (dateStr: string | null | undefined) => {
    if (!dateStr) return '-'
    try {
      return format(new Date(dateStr), 'dd/MM/yyyy')
    } catch {
      return dateStr
    }
  }

  const onSubmit = async (values: ReversalFormValues) => {
    if (!receipt) return

    setIsSubmitting(true)
    try {
      await reverseReceipt(receipt.id, values.reason)
      toast.success('Receipt reversed successfully', {
        description: `Receipt ${receipt.receiptNumber} has been reversed. A reversal voucher has been created.`,
      })
      form.reset()
      onOpenChange(false)
      onSuccess?.()
    } catch (error: any) {
      console.error('Failed to reverse receipt:', error)
      toast.error('Failed to reverse receipt', {
        description: error?.message || 'An unexpected error occurred. Please try again.',
      })
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleOpenChange = (newOpen: boolean) => {
    if (!isSubmitting) {
      if (!newOpen) {
        form.reset()
      }
      onOpenChange(newOpen)
    }
  }

  if (!receipt) return null

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>Reverse Receipt</DialogTitle>
          <DialogDescription>
            This action will create a linked reversal voucher and revert invoice payment statuses.
            This action cannot be undone.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Receipt Details */}
          <div className="rounded-md border p-4 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Receipt Number:</span>
              <span className="font-bold">{receipt.receiptNumber}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Receipt Date:</span>
              <span>{formatDate(receipt.receiptDate)}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Customer:</span>
              <span>{receipt.customerName}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Amount:</span>
              <span className="font-bold">{formatCurrency(receipt.amount)}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Payment Method:</span>
              <Badge variant="outline">{receipt.paymentMethod}</Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Status:</span>
              <Badge variant={receipt.status === 'POSTED' ? 'default' : 'secondary'}>
                {receipt.status}
              </Badge>
            </div>
          </div>

          {/* Allocations Summary */}
          {receipt.allocations && receipt.allocations.length > 0 && (
            <div className="rounded-md border p-4 space-y-2">
              <h4 className="text-sm font-semibold">Allocations ({receipt.allocations.length})</h4>
              <div className="space-y-1.5">
                {receipt.allocations.map((alloc) => (
                  <div key={alloc.id} className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">{alloc.salesInvoiceNumber}</span>
                    <span className="font-medium">{formatCurrency(alloc.allocatedAmount)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Warning Alert */}
          <Alert variant="destructive">
            <AlertTriangle className="h-4 w-4" />
            <AlertTitle>Warning</AlertTitle>
            <AlertDescription>
              Reversing this receipt will:
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>Create a linked reversal voucher with opposite GL entries</li>
                <li>Revert invoice payment statuses and remaining balances</li>
                <li>Log the reversal in the audit trail with cross-references</li>
                <li>Mark all allocations as reversed (not deleted)</li>
              </ul>
            </AlertDescription>
          </Alert>

          {/* Reversal Reason Form */}
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Reversal Reason <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Textarea
                        placeholder="Enter the reason for reversing this receipt (minimum 10 characters, maximum 500 characters)"
                        className="min-h-[100px] resize-none"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => handleOpenChange(false)}
                  disabled={isSubmitting}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="destructive" disabled={isSubmitting}>
                  {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Confirm Reversal
                </Button>
              </DialogFooter>
            </form>
          </Form>
        </div>
      </DialogContent>
    </Dialog>
  )
}
