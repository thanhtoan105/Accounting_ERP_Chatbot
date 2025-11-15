'use client'

import * as React from 'react'
import { useCallback, useMemo, useRef, useState } from 'react'
import { ArrowUpDown, Copy, GripVertical, Plus, Redo2, Trash2, Undo2 } from 'lucide-react'

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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { VoucherDimensionOption, VoucherValidationErrorMap } from '@/types/voucher'
import { DimensionPicker } from '@/components/voucher/DimensionPicker'

export interface VoucherEntryLine {
  id: string
  debitAccount: AccountSummary | null
  creditAccount: AccountSummary | null
  amount: number | null
  description?: string
  customerId?: string | null
  supplierId?: string | null
  costCenterId?: string | null
  customer?: VoucherDimensionOption | null
  supplier?: VoucherDimensionOption | null
  costCenter?: VoucherDimensionOption | null
  dimensions?: Record<string, string | null>
  source?: 'template' | 'manual'
  status?: 'clean' | 'dirty'
}

export interface VoucherLineGridProps {
  accounts: AccountSummary[]
  lines: VoucherEntryLine[]
  onLinesChange?: (lines: VoucherEntryLine[]) => void
  validationMap?: VoucherValidationErrorMap
  lockedAccountIds?: string[]
  readOnly?: boolean
  loading?: boolean
  allowNegative?: boolean
  onAddAttachment?: (lineId: string) => void
  onUndo?: () => void
  onRedo?: () => void
  canUndo?: boolean
  canRedo?: boolean
}

const VIRTUAL_ROW_HEIGHT = 72
const VIRTUAL_OVERSCAN = 5
const DEFAULT_VIRTUAL_WINDOW = 30

function fallbackDimensionOption(
  id?: string | null,
  prefix?: string,
): VoucherDimensionOption | null {
  if (!id) return null
  return {
    id,
    name: prefix ? `${prefix} #${id}` : id,
  }
}

function deriveDimensionRequirements(
  debit?: AccountSummary | null,
  credit?: AccountSummary | null,
) {
  const accountCodes = [debit?.code, credit?.code].filter(Boolean) as string[]
  const requiresCustomer = accountCodes.some((code) => code.startsWith('131'))
  const requiresSupplier = accountCodes.some((code) => code.startsWith('331'))
  const requiresCostCenter = accountCodes.some(
    (code) => code.startsWith('154') || code.startsWith('621'),
  )
  return { requiresCustomer, requiresSupplier, requiresCostCenter }
}

const createEmptyLine = (index: number): VoucherEntryLine => ({
  id: `line-${index}-${Date.now()}`,
  debitAccount: null,
  creditAccount: null,
  amount: null,
  description: '',
  customerId: null,
  supplierId: null,
  costCenterId: null,
  customer: null,
  supplier: null,
  costCenter: null,
  source: 'manual',
  status: 'clean',
})

function duplicateLine(line: VoucherEntryLine): VoucherEntryLine {
  return {
    ...line,
    id: `line-dup-${Date.now()}`,
    status: 'dirty',
  }
}

export function VoucherLineGrid({
  accounts,
  lines,
  validationMap,
  onLinesChange,
  lockedAccountIds = [],
  readOnly,
  loading,
  allowNegative = false,
  onAddAttachment,
  onUndo,
  onRedo,
  canUndo = false,
  canRedo = false,
}: VoucherLineGridProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const scrollAreaRef = useRef<HTMLDivElement>(null)
  const [activeRowIndex, setActiveRowIndex] = useState(0)
  const [dragIndex, setDragIndex] = useState<number | null>(null)
  const amountFormatter = useMemo(() => new Intl.NumberFormat('vi-VN'), [])
  const shouldVirtualize = lines.length > DEFAULT_VIRTUAL_WINDOW
  const [virtualWindow, setVirtualWindow] = useState(() => ({
    start: 0,
    end: Math.min(lines.length, DEFAULT_VIRTUAL_WINDOW),
  }))

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, line) => {
        acc.amount += line.amount ?? 0
        return acc
      },
      { amount: 0 },
    )
  }, [lines])

  const reorderLines = useCallback(
    (from: number, to: number) => {
      if (!onLinesChange || from === to) return
      const updated = [...lines]
      const [moved] = updated.splice(from, 1)
      updated.splice(to, 0, moved)
      onLinesChange(updated)
      setActiveRowIndex(to)
    },
    [lines, onLinesChange],
  )

  const updateLine = useCallback(
    (index: number, patch: Partial<VoucherEntryLine>) => {
      if (!onLinesChange) return
      const next = lines.map((line, idx) =>
        idx === index ? { ...line, ...patch, status: 'dirty' } : line,
      )
      onLinesChange(next)
    },
    [lines, onLinesChange],
  )

  const insertLine = useCallback(
    (index: number) => {
      if (!onLinesChange) return
      const next = [...lines.slice(0, index), createEmptyLine(index), ...lines.slice(index)]
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
      onLinesChange(next)
      setActiveRowIndex(Math.max(0, index - 1))
    },
    [lines, onLinesChange],
  )

  const duplicateLineAt = useCallback(
    (index: number) => {
      if (!onLinesChange) return
      const line = lines[index]
      const duplicated = duplicateLine(line)
      const next = [...lines.slice(0, index + 1), duplicated, ...lines.slice(index + 1)]
      onLinesChange(next)
      setActiveRowIndex(index + 1)
    },
    [lines, onLinesChange],
  )

  const handleKeyboardShortcuts = useCallback(
    (event: KeyboardEvent) => {
      if (readOnly || loading) return
      const container = containerRef.current
      if (!container?.contains(document.activeElement)) return
      if (!event.ctrlKey) return

      const key = event.key.toLowerCase()

      if (key === 'n') {
        event.preventDefault()
        insertLine(activeRowIndex + 1)
      } else if (key === 'd') {
        event.preventDefault()
        duplicateLineAt(activeRowIndex)
      } else if (event.key === 'Backspace') {
        event.preventDefault()
        removeLine(activeRowIndex)
      } else if (key === 'z') {
        event.preventDefault()
        if (event.shiftKey) {
          onRedo?.()
        } else {
          onUndo?.()
        }
      } else if (key === 'y') {
        event.preventDefault()
        onRedo?.()
      }
    },
    [activeRowIndex, duplicateLineAt, insertLine, loading, onRedo, onUndo, readOnly, removeLine],
  )

  React.useEffect(() => {
    window.addEventListener('keydown', handleKeyboardShortcuts)
    return () => window.removeEventListener('keydown', handleKeyboardShortcuts)
  }, [handleKeyboardShortcuts])

  const updateVirtualWindow = useCallback(() => {
    if (!shouldVirtualize) {
      setVirtualWindow({ start: 0, end: lines.length })
      return
    }
    const element = scrollAreaRef.current
    if (!element) {
      setVirtualWindow((prev) => ({
        start: 0,
        end: Math.min(lines.length, prev.end),
      }))
      return
    }
    const scrollTop = element.scrollTop
    const viewportHeight = element.clientHeight || 0
    const start = Math.max(0, Math.floor(scrollTop / VIRTUAL_ROW_HEIGHT) - VIRTUAL_OVERSCAN)
    const visibleCount = Math.ceil(viewportHeight / VIRTUAL_ROW_HEIGHT) + VIRTUAL_OVERSCAN * 2
    const end = Math.min(lines.length, start + visibleCount)
    setVirtualWindow({ start, end })
  }, [lines.length, shouldVirtualize])

  React.useEffect(() => {
    updateVirtualWindow()
  }, [lines.length, shouldVirtualize, updateVirtualWindow])

  const visibleLines = useMemo(() => {
    if (!shouldVirtualize) {
      return lines
    }
    return lines.slice(virtualWindow.start, virtualWindow.end)
  }, [lines, shouldVirtualize, virtualWindow.end, virtualWindow.start])
  const topPadding = shouldVirtualize ? virtualWindow.start * VIRTUAL_ROW_HEIGHT : 0
  const bottomPadding = shouldVirtualize
    ? Math.max(0, lines.length - virtualWindow.end) * VIRTUAL_ROW_HEIGHT
    : 0

  const lineErrors = useMemo(() => {
    if (!validationMap) return {}
    return Object.entries(validationMap).reduce<Record<number, Record<string, string[]>>>(
      (acc, [lineNumber, fieldErrors]) => {
        acc[Number(lineNumber)] = fieldErrors
        return acc
      },
      {},
    )
  }, [validationMap])

  return (
    <TooltipProvider>
      <div ref={containerRef} className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm font-medium text-muted-foreground">
            Dòng định khoản
            {loading ? <span className="ml-2 animate-pulse text-xs">Đang tải…</span> : null}
          </div>
          {!readOnly ? (
            <div className="flex flex-wrap items-center gap-2">
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => insertLine(lines.length)}
                  disabled={loading}
                >
                  <Plus className="mr-2 h-4 w-4" />
                  Thêm dòng
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={onUndo}
                  disabled={!canUndo}
                  aria-label="Hoàn tác (Ctrl+Z)"
                >
                  <Undo2 className="mr-2 h-4 w-4" />
                  Hoàn tác
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={onRedo}
                  disabled={!canRedo}
                  aria-label="Làm lại (Ctrl+Shift+Z)"
                >
                  <Redo2 className="mr-2 h-4 w-4" />
                  Làm lại
                </Button>
              </div>
              <div className="text-xs text-muted-foreground">
                Phím tắt: Ctrl+N (thêm), Ctrl+D (nhân bản), Ctrl+Backspace (xóa), Ctrl+Z /
                Ctrl+Shift+Z (hoàn tác), kéo thả để sắp xếp
              </div>
            </div>
          ) : null}
        </div>

        <div
          className={cn(
            'relative rounded-md border',
            shouldVirtualize ? 'max-h-[520px] overflow-auto' : 'overflow-x-auto',
          )}
          ref={scrollAreaRef}
          onScroll={updateVirtualWindow}
        >
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow>
                <TableHead className="w-[40px]" />
                <TableHead className="w-[60px] text-center">STT</TableHead>
                <TableHead className="min-w-[220px]">Tài khoản Nợ</TableHead>
                <TableHead className="min-w-[220px]">Tài khoản Có</TableHead>
                <TableHead className="min-w-[200px]">Mô tả</TableHead>
                <TableHead className="w-[160px] text-right">Số tiền (VND)</TableHead>
                <TableHead className="min-w-[240px]">Dimensions</TableHead>
                <TableHead className="w-[120px] text-center">Trạng thái</TableHead>
                <TableHead className="w-[80px] text-center">Tác vụ</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shouldVirtualize && topPadding > 0 ? (
                <TableRow className="pointer-events-none border-none">
                  <TableCell colSpan={9} className="p-0" style={{ height: topPadding }} />
                </TableRow>
              ) : null}
              {visibleLines.map((line, index) => {
                const actualIndex = shouldVirtualize ? virtualWindow.start + index : index
                const errors = lineErrors[actualIndex + 1] || {}
                const hasErrors = Object.keys(errors).length > 0
                const lockedDebit =
                  line.debitAccount && lockedAccountIds.includes(line.debitAccount.id)
                const lockedCredit =
                  line.creditAccount && lockedAccountIds.includes(line.creditAccount.id)
                const dimensionRequirements = deriveDimensionRequirements(
                  line.debitAccount,
                  line.creditAccount,
                )
                const requireCustomer = dimensionRequirements.requiresCustomer
                const requireSupplier = dimensionRequirements.requiresSupplier
                const requireCostCenter = dimensionRequirements.requiresCostCenter

                return (
                  <TableRow
                    key={line.id}
                    draggable={!readOnly}
                    data-active={activeRowIndex === actualIndex}
                    className={cn(
                      'border-b last:border-b-0',
                      hasErrors && 'bg-destructive/5',
                      activeRowIndex === actualIndex && 'bg-primary/5',
                    )}
                    onDragStart={() => setDragIndex(actualIndex)}
                    onDragOver={(event) => {
                      if (!readOnly) {
                        event.preventDefault()
                        event.dataTransfer.dropEffect = 'move'
                      }
                    }}
                    onDrop={(event) => {
                      event.preventDefault()
                      if (dragIndex !== null) {
                        reorderLines(dragIndex, actualIndex)
                        setDragIndex(null)
                      }
                    }}
                  >
                    <TableCell className="align-middle">
                      {!readOnly ? (
                        <GripVertical className="mx-auto h-4 w-4 cursor-grab text-muted-foreground" />
                      ) : null}
                    </TableCell>
                    <TableCell className="text-center font-medium">{actualIndex + 1}</TableCell>
                    <TableCell>
                      <AccountPicker
                        value={line.debitAccount}
                        options={accounts}
                        onChange={(account) => updateLine(actualIndex, { debitAccount: account })}
                        allowOverride={!lockedDebit && !readOnly}
                        disabled={readOnly || loading}
                        lockReason={
                          lockedDebit && line.source === 'template'
                            ? 'TK Nợ bị khóa bởi mẫu'
                            : undefined
                        }
                      />
                      {errors.debitAccount ? (
                        <p className="mt-1 text-xs text-destructive">
                          {errors.debitAccount.join(', ')}
                        </p>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <AccountPicker
                        value={line.creditAccount}
                        options={accounts}
                        onChange={(account) => updateLine(actualIndex, { creditAccount: account })}
                        allowOverride={!lockedCredit && !readOnly}
                        disabled={readOnly || loading}
                        lockReason={
                          lockedCredit && line.source === 'template'
                            ? 'TK Có bị khóa bởi mẫu'
                            : undefined
                        }
                      />
                      {errors.creditAccount ? (
                        <p className="mt-1 text-xs text-destructive">
                          {errors.creditAccount.join(', ')}
                        </p>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <Input
                        value={line.description ?? ''}
                        onFocus={() => setActiveRowIndex(actualIndex)}
                        onChange={(event) =>
                          updateLine(actualIndex, { description: event.target.value })
                        }
                        disabled={readOnly || loading}
                        placeholder="Diễn giải dòng"
                      />
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.amount ?? null}
                        onFocus={() => setActiveRowIndex(actualIndex)}
                        onChange={(value) => updateLine(actualIndex, { amount: value ?? null })}
                        onKeyDown={(event) => {
                          if (
                            event.key === 'Tab' &&
                            !event.shiftKey &&
                            actualIndex === lines.length - 1 &&
                            (line.amount ?? 0) > 0
                          ) {
                            insertLine(lines.length)
                          }
                        }}
                        allowNegative={allowNegative}
                        disabled={readOnly || loading}
                        decimals={0}
                      />
                      {errors.amount ? (
                        <p className="mt-1 text-xs text-destructive">{errors.amount.join(', ')}</p>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <div className="grid gap-3 sm:grid-cols-2">
                        <DimensionPicker
                          type="customer"
                          value={line.customer ?? fallbackDimensionOption(line.customerId, 'KH')}
                          onChange={(option) =>
                            updateLine(actualIndex, {
                              customer: option,
                              customerId: option?.id ?? null,
                            })
                          }
                          disabled={readOnly || loading}
                          required={requireCustomer}
                          error={
                            requireCustomer && !(line.customerId || line.customer?.id)
                              ? 'Bắt buộc chọn khách hàng'
                              : null
                          }
                        />
                        <DimensionPicker
                          type="supplier"
                          value={line.supplier ?? fallbackDimensionOption(line.supplierId, 'NCC')}
                          onChange={(option) =>
                            updateLine(actualIndex, {
                              supplier: option,
                              supplierId: option?.id ?? null,
                            })
                          }
                          disabled={readOnly || loading}
                          required={requireSupplier}
                          error={
                            requireSupplier && !(line.supplierId || line.supplier?.id)
                              ? 'Bắt buộc chọn nhà cung cấp'
                              : null
                          }
                        />
                        <div className="sm:col-span-2">
                          <DimensionPicker
                            type="costCenter"
                            value={
                              line.costCenter ?? fallbackDimensionOption(line.costCenterId, 'TTCP')
                            }
                            onChange={(option) =>
                              updateLine(actualIndex, {
                                costCenter: option,
                                costCenterId: option?.id ?? null,
                              })
                            }
                            disabled={readOnly || loading}
                            required={requireCostCenter}
                            error={
                              requireCostCenter && !(line.costCenterId || line.costCenter?.id)
                                ? 'Bắt buộc chọn trung tâm chi phí'
                                : null
                            }
                          />
                        </div>
                      </div>
                      {errors.dimensions ? (
                        <p className="mt-1 text-xs text-destructive">
                          {errors.dimensions.join(', ')}
                        </p>
                      ) : null}
                    </TableCell>
                    <TableCell className="text-center">
                      {hasErrors ? (
                        <Badge variant="destructive">Cần xử lý</Badge>
                      ) : (
                        <Badge variant="secondary">Hợp lệ</Badge>
                      )}
                    </TableCell>
                    <TableCell className="flex items-center justify-center gap-1 text-center">
                      {onAddAttachment ? (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              variant="ghost"
                              size="sm"
                              type="button"
                              disabled={loading}
                              onClick={() => onAddAttachment(line.id)}
                            >
                              📎
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Đính kèm chứng từ</TooltipContent>
                        </Tooltip>
                      ) : null}

                      {!readOnly ? (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8"
                              onFocus={() => setActiveRowIndex(actualIndex)}
                            >
                              ⋮
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => insertLine(actualIndex + 1)}>
                              <Plus className="mr-2 h-4 w-4" />
                              Thêm bên dưới
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() =>
                                reorderLines(actualIndex, Math.max(0, actualIndex - 1))
                              }
                            >
                              <ArrowUpDown className="mr-2 h-4 w-4" />
                              Đưa lên
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => {
                                setActiveRowIndex(actualIndex)
                                duplicateLineAt(actualIndex)
                              }}
                            >
                              <Copy className="mr-2 h-4 w-4" />
                              Nhân bản
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              className="text-destructive"
                              onClick={() => removeLine(actualIndex)}
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              Xóa dòng
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      ) : null}
                    </TableCell>
                  </TableRow>
                )
              })}
              {shouldVirtualize && bottomPadding > 0 ? (
                <TableRow className="pointer-events-none border-none">
                  <TableCell colSpan={9} className="p-0" style={{ height: bottomPadding }} />
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
          <div className="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 border-t bg-background/95 px-4 py-2 text-sm backdrop-blur supports-[backdrop-filter]:bg-background/75">
            <span>
              {lines.length} dòng • Tổng tiền{' '}
              <span className="font-semibold">{amountFormatter.format(totals.amount)}</span> VND
            </span>
            <span className="text-xs text-muted-foreground">Tổng Nợ = Tổng Có</span>
          </div>
        </div>

        <div className="flex flex-col gap-2 rounded-md border bg-muted/30 p-3 text-sm md:flex-row md:items-center md:justify-between">
          <div className="flex flex-wrap items-center gap-4">
            <span>
              Tổng tiền: <strong>{totals.amount.toLocaleString('vi-VN')}</strong>
            </span>
            <span className="text-muted-foreground">Số dòng: {lines.length}</span>
          </div>
          <div className="text-xs text-muted-foreground">
            Dòng được đánh dấu đỏ có lỗi cần xử lý trước khi ghi nhận chứng từ.
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}

export default VoucherLineGrid
