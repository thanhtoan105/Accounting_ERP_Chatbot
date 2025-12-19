'use client'

import { useCallback, useMemo, useState, useEffect } from 'react'
import { AlertCircle, Trash2 } from 'lucide-react'

import { MoneyInput } from '@/components/inputs/MoneyInput'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { format } from 'date-fns'

export interface ReceiptAllocation {
  id?: string
  salesInvoiceId: string
  salesInvoiceNumber: string | null
  salesInvoiceDate: string | null
  salesInvoiceDueDate: string | null
  salesInvoiceTotalAmount: number | null
  salesInvoiceRemainingBalance: number | null
  allocatedAmount: number
  allocationOrder: number
}

export interface ReceiptAllocationGridProps {
  allocations: ReceiptAllocation[]
  onAllocationsChange?: (allocations: ReceiptAllocation[]) => void
  totalReceiptAmount: number
  readOnly?: boolean
  loading?: boolean
  errors?: Record<string, string>
}

export function ReceiptAllocationGrid({
  allocations,
  onAllocationsChange,
  totalReceiptAmount,
  readOnly = false,
  loading = false,
  errors = {},
}: ReceiptAllocationGridProps) {
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({})

  const formatCurrency = (value: number | null | undefined) => {
    if (value == null) return '0'
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  // Real-time validation: check for overpayment as user types
  useEffect(() => {
    const newErrors: Record<string, string> = {}
    allocations.forEach((allocation, index) => {
      const remainingBalance = allocation.salesInvoiceRemainingBalance || 0
      if (allocation.allocatedAmount > remainingBalance) {
        newErrors[`allocations.${index}.allocatedAmount`] =
          `Allocated amount (${formatCurrency(allocation.allocatedAmount)}) exceeds remaining balance (${formatCurrency(remainingBalance)})`
      }
    })
    setLocalErrors(newErrors)
  }, [allocations])

  const updateAllocation = useCallback(
    (index: number, updates: Partial<ReceiptAllocation>) => {
      if (!onAllocationsChange) return
      const updated = [...allocations]
      updated[index] = { ...updated[index], ...updates }
      onAllocationsChange(updated)
    },
    [allocations, onAllocationsChange],
  )

  const removeAllocation = useCallback(
    (index: number) => {
      if (!onAllocationsChange) return
      const updated = allocations.filter((_, i) => i !== index)
      // Reorder allocation orders
      updated.forEach((alloc, idx) => {
        alloc.allocationOrder = idx + 1
      })
      onAllocationsChange(updated)
    },
    [allocations, onAllocationsChange],
  )

  const totals = useMemo(() => {
    const totalAllocated = allocations.reduce((sum, alloc) => sum + (alloc.allocatedAmount || 0), 0)
    const unallocated = totalReceiptAmount - totalAllocated
    return {
      totalAllocated,
      unallocated,
      isValid: unallocated >= 0, // Allow partial allocations (unallocated > 0) but not overpayment
    }
  }, [allocations, totalReceiptAmount])

  const formatDate = (dateStr: string | null | undefined) => {
    if (!dateStr) return '-'
    try {
      return format(new Date(dateStr), 'dd/MM/yyyy')
    } catch {
      return dateStr
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12">STT</TableHead>
              <TableHead className="w-48">Invoice Number</TableHead>
              <TableHead className="w-32">Invoice Date</TableHead>
              <TableHead className="w-32">Due Date</TableHead>
              <TableHead className="w-32 text-right">Total Amount</TableHead>
              <TableHead className="w-32 text-right">Remaining</TableHead>
              <TableHead className="w-32 text-right">Allocated</TableHead>
              {!readOnly && <TableHead className="w-24">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={readOnly ? 7 : 8} className="h-24 text-center">
                  Loading...
                </TableCell>
              </TableRow>
            ) : allocations.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={readOnly ? 7 : 8}
                  className="h-24 text-center text-muted-foreground"
                >
                  No allocations. Select customer to view open invoices.
                </TableCell>
              </TableRow>
            ) : (
              allocations.map((allocation, index) => {
                // Combine server errors and local real-time validation errors
                const serverError = errors[`allocations.${index}.allocatedAmount`]
                const localError = localErrors[`allocations.${index}.allocatedAmount`]
                const error = serverError || localError
                const hasError = Boolean(error)
                const overAllocated =
                  allocation.allocatedAmount > (allocation.salesInvoiceRemainingBalance || 0)

                return (
                  <TableRow
                    key={allocation.salesInvoiceId || index}
                    className={cn(
                      'group',
                      hasError && 'bg-destructive/5 border-destructive/20',
                      overAllocated && 'bg-orange-50 border-orange-200',
                    )}
                  >
                    <TableCell>
                      <span className="text-sm font-medium">{allocation.allocationOrder}</span>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{allocation.salesInvoiceNumber || '-'}</span>
                        {overAllocated && (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <AlertCircle className="h-4 w-4 text-orange-600" />
                              </TooltipTrigger>
                              <TooltipContent>
                                <p>Allocated amount exceeds remaining balance</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>{formatDate(allocation.salesInvoiceDate)}</TableCell>
                    <TableCell>{formatDate(allocation.salesInvoiceDueDate)}</TableCell>
                    <TableCell className="text-right">
                      {formatCurrency(allocation.salesInvoiceTotalAmount)}
                    </TableCell>
                    <TableCell className="text-right">
                      {formatCurrency(allocation.salesInvoiceRemainingBalance)}
                    </TableCell>
                    <TableCell>
                      {readOnly ? (
                        <span className="text-right font-medium">
                          {formatCurrency(allocation.allocatedAmount)}
                        </span>
                      ) : (
                        <div className="w-full">
                          <MoneyInput
                            value={allocation.allocatedAmount}
                            onChange={(value) => {
                              updateAllocation(index, { allocatedAmount: value ?? 0 })
                            }}
                            className={cn('h-9 text-right', hasError && 'border-destructive')}
                            disabled={readOnly}
                            aria-label={`Allocated amount for invoice ${allocation.salesInvoiceNumber}`}
                          />
                          {error && (
                            <p className="text-xs text-destructive mt-1 max-w-xs">{error}</p>
                          )}
                        </div>
                      )}
                    </TableCell>
                    {!readOnly && (
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => removeAllocation(index)}
                          className="h-8 w-8 p-0"
                          aria-label={`Remove allocation for invoice ${allocation.salesInvoiceNumber}`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      {/* Allocation Summary */}
      <div className="flex flex-col gap-2 rounded-md border bg-muted/20 p-4">
        <div className="flex items-center justify-between text-sm">
          <span className="font-medium">Total Receipt Amount:</span>
          <span className="font-bold">{formatCurrency(totalReceiptAmount)}</span>
        </div>
        <div className="flex items-center justify-between text-sm">
          <span className="font-medium">Total Allocated:</span>
          <span
            className={cn(
              'font-bold',
              totals.totalAllocated > totalReceiptAmount && 'text-destructive',
            )}
          >
            {formatCurrency(totals.totalAllocated)}
          </span>
        </div>
        <div className="flex items-center justify-between text-sm border-t pt-2">
          <span className="font-medium">Unallocated Amount:</span>
          <Badge
            variant={
              totals.unallocated < 0
                ? 'destructive'
                : totals.unallocated === 0
                  ? 'default'
                  : 'secondary'
            }
          >
            {formatCurrency(totals.unallocated)}
          </Badge>
        </div>
        {totals.unallocated < 0 && (
          <div className="flex items-center gap-2 text-sm text-destructive border-t pt-2">
            <AlertCircle className="h-4 w-4" />
            <span className="font-medium">
              Overpayment detected! Total allocated exceeds receipt amount.
            </span>
          </div>
        )}
        {totals.unallocated > 0 && allocations.length > 0 && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground border-t pt-2">
            <AlertCircle className="h-4 w-4" />
            <span>
              Partial allocation. {formatCurrency(totals.unallocated)} remains unallocated.
            </span>
          </div>
        )}
      </div>
    </div>
  )
}
