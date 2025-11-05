import { useState, useEffect, useRef, useCallback } from 'react'
// <CHANGE> Replace MUI with shadcn/ui and lucide-react
import { Table, TableBody, TableCell, TableHeader, TableRow, TableHead } from '@/components/ui/table'
import { Input } from '@/components/ui/input'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Trash2, Copy } from 'lucide-react'
import AccountPicker from '../account/AccountPicker'
import type { VoucherLineDTO, VoucherValidationResult } from '../../types/voucher'
import type { ChartOfAccount } from '../../types/chartOfAccount'

interface VoucherLineItemGridProps {
  lines: VoucherLineDTO[]
  onChange: (lines: VoucherLineDTO[]) => void
  errors?: Record<number, Record<string, string>> // lineNumber -> { field -> error message }
  onAutoAddLine?: () => void
  disabled?: boolean
}

/**
 * Editable grid component for voucher line items.
 * Supports keyboard navigation, auto-add line, inline validation, and row operations.
 */
export default function VoucherLineItemGrid({
  lines,
  onChange,
  errors = {},
  onAutoAddLine,
  disabled = false,
}: VoucherLineItemGridProps) {
  const [focusedCell, setFocusedCell] = useState<string | null>(null)
  const inputRefs = useRef<Record<string, HTMLInputElement | HTMLDivElement>>({})

  // Calculate totals
  const totalDebit = lines.reduce((sum, line) => sum + (line.debit || 0), 0)
  const totalCredit = lines.reduce((sum, line) => sum + (line.credit || 0), 0)
  const isBalanced = Math.abs(totalDebit - totalCredit) < 0.01 // Allow small rounding differences

  // Handle line change
  const handleLineChange = useCallback(
    (index: number, field: keyof VoucherLineDTO, value: any) => {
      const newLines = [...lines]
      const line = { ...newLines[index] }
        ; (line as any)[field] = value
      newLines[index] = line
      onChange(newLines)
    },
    [lines, onChange],
  )

  // Handle account selection
  const handleAccountChange = useCallback(
    (index: number, account: ChartOfAccount | null) => {
      handleLineChange(index, 'accountId', account?.id || 0)
    },
    [handleLineChange],
  )

  // Handle debit/credit change with mutual exclusivity
  const handleAmountChange = useCallback(
    (index: number, field: 'debit' | 'credit', value: number) => {
      const newLines = [...lines]
      const line = { ...newLines[index] }
      if (field === 'debit') {
        line.debit = value
        line.credit = 0 // Clear credit when debit is set
      } else {
        line.credit = value
        line.debit = 0 // Clear debit when credit is set
      }
      newLines[index] = line
      onChange(newLines)
    },
    [lines, onChange],
  )

  // Delete line
  const handleDeleteLine = useCallback(
    (index: number) => {
      const newLines = lines.filter((_, i) => i !== index)
      // Renumber lines
      newLines.forEach((line, i) => {
        line.lineNumber = i + 1
      })
      onChange(newLines)
    },
    [lines, onChange],
  )

  // Duplicate line
  const handleDuplicateLine = useCallback(
    (index: number) => {
      const lineToDuplicate = lines[index]
      const newLine: VoucherLineDTO = {
        ...lineToDuplicate,
        lineNumber: undefined, // Will be auto-assigned
        debit: 0,
        credit: 0, // Clear amounts when duplicating
      }
      const newLines = [...lines]
      newLines.splice(index + 1, 0, newLine)
      // Renumber all lines
      newLines.forEach((line, i) => {
        line.lineNumber = i + 1
      })
      onChange(newLines)
    },
    [lines, onChange],
  )

  // Handle keyboard navigation
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent, rowIndex: number, fieldName: string) => {
      // Ctrl+D: Duplicate row
      if (e.ctrlKey && e.key === 'd') {
        e.preventDefault()
        handleDuplicateLine(rowIndex)
        return
      }

      // Delete key: Delete row (if entire row is selected or from delete button)
      if (e.key === 'Delete' && e.shiftKey) {
        e.preventDefault()
        handleDeleteLine(rowIndex)
        return
      }

      // Tab navigation
      if (e.key === 'Tab') {
        const fields = ['account', 'debit', 'credit', 'description']
        const currentFieldIndex = fields.indexOf(fieldName)
        if (currentFieldIndex === -1) return

        if (!e.shiftKey) {
          // Forward tab
          if (currentFieldIndex < fields.length - 1) {
            // Move to next field in same row
            e.preventDefault()
            const nextField = fields[currentFieldIndex + 1]
            const cellKey = `${rowIndex}-${nextField}`
            inputRefs.current[cellKey]?.focus()
          } else if (rowIndex === lines.length - 1) {
            // Last field of last row - auto-add new line
            e.preventDefault()
            if (onAutoAddLine) {
              onAutoAddLine()
              // Focus first field of new row after state update
              setTimeout(() => {
                const newRowIndex = lines.length
                inputRefs.current[`${newRowIndex}-account`]?.focus()
              }, 0)
            }
          } else {
            // Move to first field of next row
            e.preventDefault()
            inputRefs.current[`${rowIndex + 1}-account`]?.focus()
          }
        } else {
          // Backward tab (Shift+Tab)
          if (currentFieldIndex > 0) {
            e.preventDefault()
            const prevField = fields[currentFieldIndex - 1]
            const cellKey = `${rowIndex}-${prevField}`
            inputRefs.current[cellKey]?.focus()
          } else if (rowIndex > 0) {
            // Move to last field of previous row
            e.preventDefault()
            inputRefs.current[`${rowIndex - 1}-description`]?.focus()
          }
        }
      }
    },
    [lines.length, handleDuplicateLine, handleDeleteLine, onAutoAddLine],
  )

  // Get error for a field
  const getFieldError = (lineNumber: number | undefined, field: string): string | undefined => {
    if (!lineNumber) return undefined
    return errors[lineNumber]?.[field]
  }

  // Check if line is incomplete
  const isLineIncomplete = (line: VoucherLineDTO): boolean => {
    return !line.accountId || (line.debit === 0 && line.credit === 0)
  }

  return (
    <div>
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[120px]">Account</TableHead>
              <TableHead className="min-w-[120px] text-right">Debit</TableHead>
              <TableHead className="min-w-[120px] text-right">Credit</TableHead>
              <TableHead className="min-w-[200px]">Description</TableHead>
              <TableHead className="w-[80px] text-center">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.map((line, index) => {
              const lineNumber = line.lineNumber || index + 1
              const incomplete = isLineIncomplete(line)
              const accountError = getFieldError(lineNumber, 'accountId')
              const debitError = getFieldError(lineNumber, 'debit')
              const creditError = getFieldError(lineNumber, 'credit')
              const balanceError = getFieldError(lineNumber, 'balance')

              return (
                <TableRow key={index} className={incomplete ? 'bg-muted/30' : ''}>
                  {/* Account */}
                  <TableCell>
                    <div
                      ref={(el) => {
                        if (el) inputRefs.current[`${index}-account`] = el
                      }}
                      onKeyDown={(e) => handleKeyDown(e, index, 'account')}
                    >
                      <AccountPicker
                        value={line.accountId || null}
                        onChange={(account) => handleAccountChange(index, account)}
                        error={!!accountError}
                        helperText={accountError}
                        disabled={disabled}
                        label=""
                      />
                    </div>
                  </TableCell>

                  {/* Debit */}
                  <TableCell align="right">
                    <div className="space-y-1">
                      <Input
                        ref={(el) => { if (el) inputRefs.current[`${index}-debit`] = el }}
                        type="number"
                        value={line.debit || ''}
                        onChange={(e) => handleAmountChange(index, 'debit', parseFloat((e.target as HTMLInputElement).value) || 0)}
                        onKeyDown={(e) => handleKeyDown(e, index, 'debit')}
                        disabled={disabled}
                        className="text-right"
                        min={0}
                        step={0.01}
                      />
                      {debitError && <div className="text-xs text-destructive">{debitError}</div>}
                    </div>
                  </TableCell>

                  {/* Credit */}
                  <TableCell align="right">
                    <div className="space-y-1">
                      <Input
                        ref={(el) => { if (el) inputRefs.current[`${index}-credit`] = el }}
                        type="number"
                        value={line.credit || ''}
                        onChange={(e) => handleAmountChange(index, 'credit', parseFloat((e.target as HTMLInputElement).value) || 0)}
                        onKeyDown={(e) => handleKeyDown(e, index, 'credit')}
                        disabled={disabled}
                        className="text-right"
                        min={0}
                        step={0.01}
                      />
                      {creditError && <div className="text-xs text-destructive">{creditError}</div>}
                    </div>
                  </TableCell>

                  {/* Description */}
                  <TableCell>
                    <Input
                      ref={(el) => { if (el) inputRefs.current[`${index}-description`] = el }}
                      value={line.description || ''}
                      onChange={(e) => handleLineChange(index, 'description', (e.target as HTMLInputElement).value)}
                      onKeyDown={(e) => handleKeyDown(e, index, 'description')}
                      disabled={disabled}
                      maxLength={500 as any}
                    />
                  </TableCell>

                  {/* Actions */}
                  <TableCell align="center">
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Button size="icon" variant="ghost" onClick={() => handleDuplicateLine(index)} disabled={disabled} aria-label="Duplicate row">
                            <Copy className="size-4" />
                          </Button>
                        </TooltipTrigger>
                        <TooltipContent>Duplicate row (Ctrl+D)</TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Button size="icon" variant="ghost" onClick={() => handleDeleteLine(index)} disabled={disabled || lines.length <= 1} aria-label="Delete row">
                            <Trash2 className="size-4 text-destructive" />
                          </Button>
                        </TooltipTrigger>
                        <TooltipContent>Delete row</TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {/* Totals and balance indicator */}
      <div className="mt-2 flex justify-end gap-3 text-sm">
        <div className="flex items-center gap-3">
          <div><strong>Total Debit:</strong> {totalDebit.toLocaleString('vi-VN')} VND</div>
          <div><strong>Total Credit:</strong> {totalCredit.toLocaleString('vi-VN')} VND</div>
          <Badge variant={isBalanced ? 'secondary' : 'destructive'}>{isBalanced ? 'Balanced ✓' : 'Not Balanced ✗'}</Badge>
        </div>
      </div>
    </div>
  )
}
