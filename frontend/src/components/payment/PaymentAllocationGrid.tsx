'use client'

import * as React from 'react'
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
import type { PaymentAllocationDTO, PaymentAllocationRequest } from '@/types/payment'
import { format } from 'date-fns'

export interface PaymentAllocation {
  id?: string
  purchaseBillId: string
  purchaseBillNumber: string | null
  purchaseBillDate: string | null
  purchaseBillDueDate: string | null
  purchaseBillTotalAmount: number | null
  purchaseBillRemainingBalance: number | null
  allocatedAmount: number
  allocationOrder: number
}

export interface PaymentAllocationGridProps {
  allocations: PaymentAllocation[]
  onAllocationsChange?: (allocations: PaymentAllocation[]) => void
  totalPaymentAmount: number
  readOnly?: boolean
  loading?: boolean
  errors?: Record<string, string>
}

export function PaymentAllocationGrid({
  allocations,
  onAllocationsChange,
  totalPaymentAmount,
  readOnly = false,
  loading = false,
  errors = {},
}: PaymentAllocationGridProps) {
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
      const remainingBalance = allocation.purchaseBillRemainingBalance || 0
      if (allocation.allocatedAmount > remainingBalance) {
        newErrors[`allocations.${index}.allocatedAmount`] =
          `Allocated amount (${formatCurrency(allocation.allocatedAmount)}) exceeds remaining balance (${formatCurrency(remainingBalance)})`
      }
    })
    setLocalErrors(newErrors)
  }, [allocations])

  const updateAllocation = useCallback(
    (index: number, updates: Partial<PaymentAllocation>) => {
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
    const unallocated = totalPaymentAmount - totalAllocated
    return {
      totalAllocated,
      unallocated,
      isValid: unallocated === 0,
    }
  }, [allocations, totalPaymentAmount])

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
              <TableHead className="w-48">Bill Number</TableHead>
              <TableHead className="w-32">Bill Date</TableHead>
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
                  No allocations. Use FIFO allocation or add manually.
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
                  allocation.allocatedAmount > (allocation.purchaseBillRemainingBalance || 0)

                return (
                  <TableRow
                    key={allocation.purchaseBillId || index}
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
                        <span className="font-medium">{allocation.purchaseBillNumber || '-'}</span>
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
                    <TableCell>{formatDate(allocation.purchaseBillDate)}</TableCell>
                    <TableCell>{formatDate(allocation.purchaseBillDueDate)}</TableCell>
                    <TableCell className="text-right">
                      {formatCurrency(allocation.purchaseBillTotalAmount)}
                    </TableCell>
                    <TableCell className="text-right">
                      {formatCurrency(allocation.purchaseBillRemainingBalance)}
                    </TableCell>
                    <TableCell>
                      {readOnly ? (
                        <span className="text-right font-medium">
                          {formatCurrency(allocation.allocatedAmount)}
                        </span>
                      ) : (
                        <MoneyInput
                          value={allocation.allocatedAmount}
                          onChange={(value) =>
                            updateAllocation(index, { allocatedAmount: value || 0 })
                          }
                          disabled={loading}
                          allowNegative={false}
                          placeholder="0"
                          className={cn(
                            'text-right',
                            (hasError || overAllocated) && 'border-destructive',
                          )}
                        />
                      )}
                      {hasError && <p className="text-xs text-destructive mt-1">{error}</p>}
                    </TableCell>
                    {!readOnly && (
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => removeAllocation(index)}
                          disabled={loading}
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

      {/* Summary */}
      <div className="flex justify-between items-center p-4 bg-muted/50 rounded-md">
        <div className="text-sm text-muted-foreground">
          {allocations.length} allocation{allocations.length !== 1 ? 's' : ''}
        </div>
        <div className="flex gap-8">
          <div className="text-right">
            <div className="text-sm text-muted-foreground">Total Allocated</div>
            <div className="text-lg font-semibold">{formatCurrency(totals.totalAllocated)}</div>
          </div>
          <div className="text-right">
            <div className="text-sm text-muted-foreground">Unallocated</div>
            <div
              className={cn(
                'text-lg font-semibold',
                totals.unallocated > 0 && 'text-orange-600',
                totals.unallocated < 0 && 'text-destructive',
              )}
            >
              {formatCurrency(totals.unallocated)}
            </div>
          </div>
          <div className="text-right">
            <div className="text-sm text-muted-foreground">Payment Amount</div>
            <div className="text-xl font-bold">{formatCurrency(totalPaymentAmount)}</div>
          </div>
        </div>
        {!totals.isValid && (
          <Badge variant={totals.unallocated > 0 ? 'outline' : 'destructive'}>
            {totals.unallocated > 0 ? 'Under-allocated' : 'Over-allocated'}
          </Badge>
        )}
      </div>
    </div>
  )
}
