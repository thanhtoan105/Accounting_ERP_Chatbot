'use client'

import { useEffect, useState } from 'react'
import { X, Printer, Loader2, FileText } from 'lucide-react'
import { format } from 'date-fns'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { toast } from 'sonner'
import { arAgingApi } from '@/features/accounting/services/arAgingApi'
import { formatCurrency } from '@/utils/format'

interface InvoiceDetail {
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  status: string
  customerName: string
  customerCode: string
  totalAmount: number
  paidAmount: number
  remainingBalance: number
  lineItems: Array<{
    description: string
    quantity: number
    unitPrice: number
    amount: number
  }>
  paymentHistory: Array<{
    receiptNumber: string
    receiptDate: string
    amount: number
    paymentMethod?: string
  }>
}

interface InvoiceDetailModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  invoiceId: string
}

export function InvoiceDetailModal({ open, onOpenChange, invoiceId }: InvoiceDetailModalProps) {
  const [loading, setLoading] = useState(false)
  const [invoice, setInvoice] = useState<InvoiceDetail | null>(null)

  useEffect(() => {
    if (open && invoiceId) {
      loadInvoiceDetail()
    }
  }, [open, invoiceId])

  const loadInvoiceDetail = async () => {
    try {
      setLoading(true)
      const response = await arAgingApi.getInvoiceDetail(invoiceId)
      // Backend returns ARInvoiceDetailDTO directly
      if (response?.data) {
        setInvoice(response.data)
      } else if (response && typeof response === 'object' && 'invoiceNumber' in response) {
        // Fallback: if response is the DTO directly
        setInvoice(response as InvoiceDetail)
      } else {
        throw new Error('Invalid response structure from server')
      }
    } catch (err: any) {
      const message = err?.response?.data?.message || 'Failed to load invoice details'
      toast.error('Error', { description: message })
      onOpenChange(false)
    } finally {
      setLoading(false)
    }
  }

  const handlePrint = () => {
    window.print()
  }

  if (loading) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Invoice Details</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-8">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-32 w-full" />
            <Skeleton className="h-48 w-full" />
          </div>
        </DialogContent>
      </Dialog>
    )
  }

  if (!invoice) {
    return null
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto print:max-w-full">
        <DialogHeader className="print:hidden">
          <DialogTitle className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="h-5 w-5" />
              Invoice Details - {invoice.invoiceNumber}
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={handlePrint}>
                <Printer className="h-4 w-4 mr-2" />
                Print
              </Button>
              <Button variant="ghost" size="icon" onClick={() => onOpenChange(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6 print:space-y-4">
          {/* Invoice Header */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Invoice Information</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm font-semibold text-muted-foreground">Invoice Number</p>
                <p className="text-base font-semibold">{invoice.invoiceNumber}</p>
              </div>
              <div>
                <p className="text-sm font-semibold text-muted-foreground">Status</p>
                <Badge variant={invoice.status === 'POSTED' ? 'default' : 'secondary'}>
                  {invoice.status}
                </Badge>
              </div>
              <div>
                <p className="text-sm font-semibold text-muted-foreground">Invoice Date</p>
                <p className="text-base">
                  {invoice.invoiceDate ? format(new Date(invoice.invoiceDate), 'dd/MM/yyyy') : '-'}
                </p>
              </div>
              <div>
                <p className="text-sm font-semibold text-muted-foreground">Due Date</p>
                <p className="text-base">
                  {invoice.dueDate ? format(new Date(invoice.dueDate), 'dd/MM/yyyy') : '-'}
                </p>
              </div>
              <div>
                <p className="text-sm font-semibold text-muted-foreground">Customer Code</p>
                <p className="text-base">{invoice.customerCode}</p>
              </div>
              <div>
                <p className="text-sm font-semibold text-muted-foreground">Customer Name</p>
                <p className="text-base">{invoice.customerName}</p>
              </div>
            </CardContent>
          </Card>

          {/* Line Items */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Line Items</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Description</TableHead>
                      <TableHead className="text-right">Quantity</TableHead>
                      <TableHead className="text-right">Unit Price</TableHead>
                      <TableHead className="text-right">Amount</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {invoice.lineItems && invoice.lineItems.length > 0 ? (
                      invoice.lineItems.map((item, index) => (
                        <TableRow key={index}>
                          <TableCell>{item.description}</TableCell>
                          <TableCell className="text-right">{item.quantity}</TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(item.unitPrice)}
                          </TableCell>
                          <TableCell className="text-right font-medium">
                            {formatCurrency(item.amount)}
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow>
                        <TableCell colSpan={4} className="text-center text-muted-foreground">
                          No line items available
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>

          {/* Totals */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Invoice Totals</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium">Total Amount</span>
                <span className="text-base font-semibold">
                  {formatCurrency(invoice.totalAmount)}
                </span>
              </div>
              <Separator />
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium">Paid Amount</span>
                <span className="text-base text-green-600 font-semibold">
                  {formatCurrency(invoice.paidAmount)}
                </span>
              </div>
              <Separator />
              <div className="flex justify-between items-center">
                <span className="text-base font-semibold">Outstanding Balance</span>
                <span className="text-lg font-bold text-red-600">
                  {formatCurrency(invoice.remainingBalance)}
                </span>
              </div>
            </CardContent>
          </Card>

          {/* Payment History */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Payment History</CardTitle>
            </CardHeader>
            <CardContent>
              {invoice.paymentHistory && invoice.paymentHistory.length > 0 ? (
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Receipt Number</TableHead>
                        <TableHead>Receipt Date</TableHead>
                        <TableHead>Payment Method</TableHead>
                        <TableHead className="text-right">Amount</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {invoice.paymentHistory.map((payment, index) => (
                        <TableRow key={index}>
                          <TableCell className="font-medium">{payment.receiptNumber}</TableCell>
                          <TableCell>
                            {payment.receiptDate
                              ? format(new Date(payment.receiptDate), 'dd/MM/yyyy')
                              : '-'}
                          </TableCell>
                          <TableCell>{payment.paymentMethod || '-'}</TableCell>
                          <TableCell className="text-right font-semibold text-green-600">
                            {formatCurrency(payment.amount)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  <p className="text-sm">No payments recorded for this invoice</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Close Button (Print Hidden) */}
          <div className="flex justify-end print:hidden">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
