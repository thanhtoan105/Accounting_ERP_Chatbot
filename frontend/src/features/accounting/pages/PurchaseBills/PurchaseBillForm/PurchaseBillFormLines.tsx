'use client'

import { useCallback } from 'react'
import { Plus, Trash2, AlertCircle } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { AccountPicker, type AccountSummary } from '@/components/account/AccountPicker'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { PurchaseBillLine } from '@/components/purchase/PurchaseBillLineGrid'
import type { VatRate } from '@/types/purchaseBill'
import { PurchaseBillSectionHeader } from '@/features/accounting/components/purchase-ui'

interface PurchaseBillFormLinesProps {
  accounts: AccountSummary[]
  lines: PurchaseBillLine[]
  onLinesChange: (lines: PurchaseBillLine[]) => void
  validationMap: Record<number, Record<string, string[]>>
  readOnly: boolean
  loading: boolean
  onCalculateVAT: (amount: number, rate: VatRate) => number
}

const VAT_RATES: { value: VatRate; label: string }[] = [
  { value: 'ZERO', label: '0%' },
  { value: 'FIVE', label: '5%' },
  { value: 'TEN', label: '10%' },
  { value: 'EXEMPT', label: 'Exempt' },
]

export function PurchaseBillFormLines({
  accounts,
  lines,
  onLinesChange,
  validationMap,
  readOnly,
  loading,
  onCalculateVAT,
}: PurchaseBillFormLinesProps) {
  const updateLine = useCallback(
    (index: number, updates: Partial<PurchaseBillLine>) => {
      if (readOnly) return
      const newLines = [...lines]
      newLines[index] = { ...newLines[index], ...updates }

      // Recalculate Amount if Quantity or Unit Price changes
      if ('quantity' in updates || 'unitPrice' in updates) {
        const qty = newLines[index].quantity ?? 0
        const price = newLines[index].unitPrice ?? 0
        // Only auto-calc if both exist, otherwise if one is cleared, amount might remain manual or clear?
        // Let's stick to simple logic: Qty * Price = Amount
        newLines[index].amount = qty * price

        // Also update VAT Amount based on new Amount
        const rate = newLines[index].vatRate || 'ZERO'
        newLines[index].vatAmount = onCalculateVAT(newLines[index].amount, rate)
      }

      // Recalculate VAT Amount if Amount or VAT Rate changes directly
      if ('amount' in updates || 'vatRate' in updates) {
        const amount = newLines[index].amount ?? 0
        const rate = newLines[index].vatRate || 'ZERO'
        newLines[index].vatAmount = onCalculateVAT(amount, rate)
      }

      onLinesChange(newLines)
    },
    [lines, onLinesChange, readOnly, onCalculateVAT],
  )

  const addLine = useCallback(() => {
    if (readOnly) return
    const newLine: PurchaseBillLine = {
      id: `line-${Date.now()}`,
      lineNumber: lines.length + 1,
      account: null,
      description: '',
      quantity: null,
      unitPrice: null,
      amount: null,
      vatRate: 'ZERO',
      vatAmount: null,
      itemId: null,
      status: 'clean',
    }
    onLinesChange([...lines, newLine])
  }, [lines, onLinesChange, readOnly])

  const deleteLine = useCallback(
    (index: number) => {
      if (readOnly) return
      const newLines = lines
        .filter((_, i) => i !== index)
        .map((line, i) => ({
          ...line,
          lineNumber: i + 1, // Renumber lines
        }))
      onLinesChange(newLines)
    },
    [lines, onLinesChange, readOnly],
  )

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Line Items</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="voucher-card shadow-sm">
      <CardHeader className="bg-[var(--voucher-surface-1)] pb-4 pt-5">
        <PurchaseBillSectionHeader
          title="Line Items"
          description="Add expense lines with accounts, amounts, and VAT details"
          actions={
            <Button onClick={addLine} disabled={readOnly} size="sm" variant="outline">
              <Plus className="mr-2 h-4 w-4" />
              Add Line
            </Button>
          }
        />
      </CardHeader>
      <CardContent className="p-0">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader className="voucher-table-header-sticky">
              <TableRow className="bg-[var(--voucher-surface-2)] hover:bg-[var(--voucher-surface-2)]">
                <TableHead className="w-[50px] text-center">#</TableHead>
                <TableHead className="min-w-[200px]">Account</TableHead>
                <TableHead className="min-w-[200px]">Description</TableHead>
                <TableHead className="min-w-[100px] text-right">Qty</TableHead>
                <TableHead className="min-w-[120px] text-right">Price</TableHead>
                <TableHead className="min-w-[120px] text-right">Amount</TableHead>
                <TableHead className="min-w-[100px]">VAT Rate</TableHead>
                <TableHead className="min-w-[120px] text-right">VAT Amt</TableHead>
                <TableHead className="w-[50px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className="voucher-stagger">
              {lines.map((line, index) => {
                const errors = validationMap[index + 1] || {}
                const hasError = (field: string) => !!errors[field]
                const getError = (field: string) => errors[field]?.[0]

                return (
                  <TableRow
                    key={line.id}
                    className="voucher-row-animate group hover:bg-[var(--voucher-surface-1)]"
                  >
                    <TableCell className="text-center font-medium text-muted-foreground">
                      {index + 1}
                    </TableCell>

                    {/* Account */}
                    <TableCell className="align-top">
                      <div className="flex flex-col gap-1">
                        <AccountPicker
                          options={accounts}
                          value={line.account}
                          onChange={(account) => updateLine(index, { account })}
                          disabled={readOnly}
                        />
                        {hasError('accountId') && (
                          <span className="text-[10px] text-destructive">
                            {getError('accountId')}
                          </span>
                        )}
                      </div>
                    </TableCell>

                    {/* Description */}
                    <TableCell className="align-top">
                      <Input
                        value={line.description || ''}
                        onChange={(e) => updateLine(index, { description: e.target.value })}
                        disabled={readOnly}
                        className={cn(
                          'h-9',
                          hasError('description') &&
                            'border-destructive focus-visible:ring-destructive',
                        )}
                        placeholder="Description"
                      />
                    </TableCell>

                    {/* Quantity */}
                    <TableCell className="align-top">
                      <Input
                        type="number"
                        min={0}
                        step="any"
                        value={line.quantity ?? ''}
                        onChange={(e) => {
                          const val = e.target.value ? parseFloat(e.target.value) : null
                          updateLine(index, { quantity: val })
                        }}
                        disabled={readOnly}
                        className="h-9 text-right"
                      />
                    </TableCell>

                    {/* Unit Price */}
                    <TableCell className="align-top">
                      <Input
                        type="number"
                        min={0}
                        step="any"
                        value={line.unitPrice ?? ''}
                        onChange={(e) => {
                          const val = e.target.value ? parseFloat(e.target.value) : null
                          updateLine(index, { unitPrice: val })
                        }}
                        disabled={readOnly}
                        className="h-9 text-right"
                      />
                    </TableCell>

                    {/* Amount */}
                    <TableCell className="align-top">
                      <div className="relative">
                        <Input
                          type="number"
                          value={line.amount ?? ''}
                          onChange={(e) => {
                            const val = e.target.value ? parseFloat(e.target.value) : null
                            updateLine(index, { amount: val })
                          }}
                          disabled={readOnly} // Often readonly if calc'd, but sometimes editable? Let's allow edit override
                          className={cn(
                            'h-9 text-right font-medium',
                            hasError('amount') &&
                              'border-destructive focus-visible:ring-destructive',
                          )}
                        />
                        {hasError('amount') && (
                          <div className="absolute right-0 top-full mt-1 z-10">
                            <TooltipProvider>
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <AlertCircle className="h-4 w-4 text-destructive" />
                                </TooltipTrigger>
                                <TooltipContent>
                                  <p>{getError('amount')}</p>
                                </TooltipContent>
                              </Tooltip>
                            </TooltipProvider>
                          </div>
                        )}
                      </div>
                    </TableCell>

                    {/* VAT Rate */}
                    <TableCell className="align-top">
                      <Select
                        value={line.vatRate}
                        onValueChange={(val: VatRate) => updateLine(index, { vatRate: val })}
                        disabled={readOnly}
                      >
                        <SelectTrigger className="h-9 w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {VAT_RATES.map((rate) => (
                            <SelectItem key={rate.value} value={rate.value}>
                              {rate.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>

                    {/* VAT Amount */}
                    <TableCell className="align-top">
                      <Input
                        type="number"
                        value={line.vatAmount ?? ''}
                        onChange={(e) => {
                          const val = e.target.value ? parseFloat(e.target.value) : null
                          updateLine(index, { vatAmount: val })
                        }}
                        disabled={readOnly}
                        className="h-9 text-right"
                      />
                    </TableCell>

                    {/* Actions */}
                    <TableCell className="align-top text-center">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => deleteLine(index)}
                        disabled={readOnly || lines.length === 1} // Keep at least one line? Or allow deleting all? Usually keep 1
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>

        {!readOnly && (
          <div className="p-4 bg-muted/10 border-t">
            <Button onClick={addLine} variant="ghost" className="w-full border-dashed border">
              <Plus className="mr-2 h-4 w-4" /> Add Line Item
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
