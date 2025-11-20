'use client'

import * as React from 'react'
import { useCallback, useMemo, useRef, useState } from 'react'
import { ArrowUpDown, Copy, GripVertical, Plus, Trash2, AlertCircle } from 'lucide-react'

import { AccountPicker, type AccountSummary } from '@/components/account/AccountPicker'
import { MoneyInput } from '@/components/inputs/MoneyInput'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
import { DimensionPicker } from '@/components/voucher/DimensionPicker'
import type { VoucherDimensionOption } from '@/types/voucher'
import type { VatRate, PurchaseBillLineDTO } from '@/types/purchaseBill'

export interface PurchaseBillLine {
  id: string
  lineNumber?: number
  account: AccountSummary | null
  description: string
  quantity: number | null
  unitPrice: number | null
  amount: number | null
  vatRate: VatRate
  vatAmount: number | null
  costCenterId?: string | null
  itemId?: string | null
  costCenter?: VoucherDimensionOption | null
  item?: VoucherDimensionOption | null
  status?: 'clean' | 'dirty'
}

export interface PurchaseBillLineGridProps {
  accounts: AccountSummary[]
  lines: PurchaseBillLine[]
  onLinesChange?: (lines: PurchaseBillLine[]) => void
  validationMap?: Record<number, Record<string, string[]>>
  readOnly?: boolean
  loading?: boolean
  onCalculateVAT?: (amount: number, rate: VatRate) => number
}

const VAT_RATE_OPTIONS: { value: VatRate; label: string }[] = [
  { value: 'ZERO', label: '0%' },
  { value: 'FIVE', label: '5%' },
  { value: 'TEN', label: '10%' },
  { value: 'EXEMPT', label: 'Exempt' },
]

function getVatRateValue(rate: VatRate): number {
  switch (rate) {
    case 'ZERO':
      return 0
    case 'FIVE':
      return 0.05
    case 'TEN':
      return 0.1
    case 'EXEMPT':
      return 0
    default:
      return 0
  }
}

function calculateVAT(amount: number, rate: VatRate): number {
  const rateValue = getVatRateValue(rate)
  return amount * rateValue
}

const createEmptyLine = (index: number): PurchaseBillLine => ({
  id: `line-${index}-${Date.now()}`,
  lineNumber: index + 1,
  account: null,
  description: '',
  quantity: null,
  unitPrice: null,
  amount: null,
  vatRate: 'ZERO',
  vatAmount: null,
  costCenterId: null,
  itemId: null,
  costCenter: null,
  item: null,
  status: 'clean',
})

function duplicateLine(line: PurchaseBillLine): PurchaseBillLine {
  return {
    ...line,
    id: `line-dup-${Date.now()}`,
    status: 'dirty',
  }
}

export function PurchaseBillLineGrid({
  accounts,
  lines,
  validationMap = {},
  onLinesChange,
  readOnly,
  loading,
  onCalculateVAT,
}: PurchaseBillLineGridProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [activeRowIndex, setActiveRowIndex] = useState(0)

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, line) => {
        acc.amount += line.amount ?? 0
        acc.vatAmount += line.vatAmount ?? 0
        return acc
      },
      { amount: 0, vatAmount: 0 },
    )
  }, [lines])

  const updateLine = useCallback(
    (index: number, patch: Partial<PurchaseBillLine>) => {
      if (!onLinesChange) return
      const line = lines[index]
      const updated = { ...line, ...patch, status: 'dirty' as const }

      // Auto-calculate amount from quantity × unitPrice
      if (patch.quantity !== undefined || patch.unitPrice !== undefined) {
        const qty = patch.quantity !== undefined ? patch.quantity : line.quantity
        const price = patch.unitPrice !== undefined ? patch.unitPrice : line.unitPrice
        if (qty !== null && price !== null) {
          updated.amount = qty * price
        }
      }

      // Auto-calculate VAT amount
      if (
        updated.amount !== null &&
        (patch.vatRate !== undefined || updated.amount !== line.amount)
      ) {
        const vatCalc = onCalculateVAT || calculateVAT
        updated.vatAmount = vatCalc(updated.amount, updated.vatRate)
      }

      const next = lines.map((l, idx) => (idx === index ? updated : l))
      onLinesChange(next)
    },
    [lines, onLinesChange, onCalculateVAT],
  )

  const insertLine = useCallback(
    (index: number) => {
      if (!onLinesChange) return
      const next = [...lines.slice(0, index), createEmptyLine(index), ...lines.slice(index)]
      // Update line numbers
      next.forEach((line, idx) => {
        line.lineNumber = idx + 1
      })
      onLinesChange(next)
      setActiveRowIndex(index)
    },
    [lines, onLinesChange],
  )

  const removeLine = useCallback(
    (index: number) => {
      if (!onLinesChange) return
      if (lines.length === 1) {
        onLinesChange([createEmptyLine(0)])
        setActiveRowIndex(0)
        return
      }
      const next = lines.filter((_, idx) => idx !== index)
      // Update line numbers
      next.forEach((line, idx) => {
        line.lineNumber = idx + 1
      })
      onLinesChange(next)
      setActiveRowIndex(Math.max(0, index - 1))
    },
    [lines, onLinesChange],
  )

  const duplicateLineAt = useCallback(
    (index: number) => {
      if (!onLinesChange) return
      const line = lines[index]
      const dup = duplicateLine(line)
      const next = [...lines.slice(0, index + 1), dup, ...lines.slice(index + 1)]
      // Update line numbers
      next.forEach((line, idx) => {
        line.lineNumber = idx + 1
      })
      onLinesChange(next)
      setActiveRowIndex(index + 1)
    },
    [lines, onLinesChange],
  )

  const getLineErrors = (lineIndex: number): Record<string, string[]> => {
    return validationMap[lineIndex] || {}
  }

  const hasLineErrors = (lineIndex: number): boolean => {
    const errors = getLineErrors(lineIndex)
    return Object.keys(errors).length > 0
  }

  return (
    <div ref={containerRef} className="space-y-4">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12">STT</TableHead>
              <TableHead className="w-64">Account</TableHead>
              <TableHead>Description</TableHead>
              <TableHead className="w-32">Quantity</TableHead>
              <TableHead className="w-32">Unit Price</TableHead>
              <TableHead className="w-32">Amount</TableHead>
              <TableHead className="w-32">VAT Rate</TableHead>
              <TableHead className="w-32">VAT Amount</TableHead>
              <TableHead className="w-48">Cost Center</TableHead>
              <TableHead className="w-48">Item</TableHead>
              <TableHead className="w-24">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={11} className="h-24 text-center">
                  Loading...
                </TableCell>
              </TableRow>
            ) : lines.length === 0 ? (
              <TableRow>
                <TableCell colSpan={11} className="h-24 text-center text-muted-foreground">
                  No line items. Click "Add Line" to add items.
                </TableCell>
              </TableRow>
            ) : (
              lines.map((line, index) => {
                const errors = getLineErrors(index)
                const hasErrors = hasLineErrors(index)
                return (
                  <TableRow
                    key={line.id}
                    className={cn(
                      'group',
                      hasErrors && 'bg-destructive/5 border-destructive/20',
                      activeRowIndex === index && 'bg-muted/50',
                    )}
                  >
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium">{line.lineNumber ?? index + 1}</span>
                        {hasErrors && (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <AlertCircle className="h-4 w-4 text-destructive" />
                              </TooltipTrigger>
                              <TooltipContent>
                                <div className="space-y-1">
                                  {Object.entries(errors).map(([field, msgs]) => (
                                    <div key={field}>
                                      <strong>{field}:</strong> {msgs.join(', ')}
                                    </div>
                                  ))}
                                </div>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <AccountPicker
                        options={accounts}
                        value={line.account}
                        onChange={(account) => updateLine(index, { account })}
                        disabled={readOnly || loading}
                        placeholder="Select account..."
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        value={line.description}
                        onChange={(e) => updateLine(index, { description: e.target.value })}
                        disabled={readOnly || loading}
                        placeholder="Description"
                        className={cn(errors.description && 'border-destructive')}
                      />
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.quantity}
                        onChange={(value) => updateLine(index, { quantity: value })}
                        disabled={readOnly || loading}
                        allowNegative={false}
                        placeholder="0"
                        className={cn(errors.quantity && 'border-destructive')}
                      />
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.unitPrice}
                        onChange={(value) => updateLine(index, { unitPrice: value })}
                        disabled={readOnly || loading}
                        allowNegative={false}
                        placeholder="0"
                        className={cn(errors.unitPrice && 'border-destructive')}
                      />
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.amount}
                        onChange={(value) => updateLine(index, { amount: value })}
                        disabled={readOnly || loading}
                        allowNegative={false}
                        placeholder="0"
                        className={cn(errors.amount && 'border-destructive')}
                      />
                    </TableCell>
                    <TableCell>
                      <Select
                        value={line.vatRate}
                        onValueChange={(value: VatRate) => updateLine(index, { vatRate: value })}
                        disabled={readOnly || loading}
                      >
                        <SelectTrigger className={cn(errors.vatRate && 'border-destructive')}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {VAT_RATE_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {line.vatRate === 'ZERO' && (
                        <Badge variant="outline" className="mt-1 text-xs">
                          0%
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.vatAmount}
                        onChange={(value) => updateLine(index, { vatAmount: value })}
                        disabled={readOnly || loading}
                        allowNegative={false}
                        placeholder="0"
                        className={cn(errors.vatAmount && 'border-destructive')}
                      />
                    </TableCell>
                    <TableCell>
                      <DimensionPicker
                        type="costCenter"
                        value={line.costCenter}
                        onChange={(value) =>
                          updateLine(index, {
                            costCenter: value,
                            costCenterId: value?.id ?? null,
                          })
                        }
                        disabled={readOnly || loading}
                        required={false}
                        error={errors.costCenterId?.[0]}
                      />
                    </TableCell>
                    <TableCell>
                      <DimensionPicker
                        type="supplier"
                        value={line.item}
                        onChange={(value) =>
                          updateLine(index, {
                            item: value,
                            itemId: value?.id ?? null,
                          })
                        }
                        disabled={readOnly || loading}
                        required={false}
                        error={errors.itemId?.[0]}
                      />
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <GripVertical className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => insertLine(index)}>
                            <Plus className="mr-2 h-4 w-4" />
                            Insert Line
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => duplicateLineAt(index)}>
                            <Copy className="mr-2 h-4 w-4" />
                            Duplicate
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => removeLine(index)}
                            className="text-destructive"
                            disabled={lines.length === 1}
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      {/* Totals */}
      <div className="flex justify-end gap-8 pr-4">
        <div className="text-right">
          <div className="text-sm text-muted-foreground">Total Amount</div>
          <div className="text-lg font-semibold">
            {new Intl.NumberFormat('vi-VN', {
              style: 'currency',
              currency: 'VND',
              minimumFractionDigits: 0,
              maximumFractionDigits: 0,
            }).format(totals.amount)}
          </div>
        </div>
        <div className="text-right">
          <div className="text-sm text-muted-foreground">Total VAT</div>
          <div className="text-lg font-semibold">
            {new Intl.NumberFormat('vi-VN', {
              style: 'currency',
              currency: 'VND',
              minimumFractionDigits: 0,
              maximumFractionDigits: 0,
            }).format(totals.vatAmount)}
          </div>
        </div>
        <div className="text-right">
          <div className="text-sm text-muted-foreground">Grand Total</div>
          <div className="text-xl font-bold">
            {new Intl.NumberFormat('vi-VN', {
              style: 'currency',
              currency: 'VND',
              minimumFractionDigits: 0,
              maximumFractionDigits: 0,
            }).format(totals.amount + totals.vatAmount)}
          </div>
        </div>
      </div>
    </div>
  )
}
