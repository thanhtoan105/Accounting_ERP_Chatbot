'use client'

import * as React from 'react'
import { useMemo } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { AccountPicker, type AccountSummary } from '@/components/account/AccountPicker'
import { MoneyInput } from '@/components/inputs/MoneyInput'
import { PlusIcon, TrashIcon } from 'lucide-react'

export type VoucherDimension = 'customer' | 'vendor' | 'costCenter' | 'item'

export interface VoucherLine {
  id: string
  account: AccountSummary | null
  description?: string
  debit?: number | null
  credit?: number | null
  customerId?: string | null
  vendorId?: string | null
  costCenterId?: string | null
  itemId?: string | null
  attachmentCount?: number
  source?: 'template' | 'manual'
}

export interface VoucherLineGridProps {
  accounts: AccountSummary[]
  lines: VoucherLine[]
  onLinesChange?: (lines: VoucherLine[]) => void
  lockedAccountIds?: string[]
  readOnly?: boolean
  loading?: boolean
  renderDimensionCell?: (
    dimension: VoucherDimension,
    line: VoucherLine,
    index: number,
    update: (patch: Partial<VoucherLine>) => void,
  ) => React.ReactNode
  onAddAttachment?: (lineId: string) => void
  onRemoveAttachment?: (lineId: string, attachmentId: string) => void
  allowNegative?: boolean
}

const createEmptyLine = (index: number): VoucherLine => ({
  id: `line_${index}_${Date.now()}`,
  account: null,
  description: '',
  debit: null,
  credit: null,
  customerId: null,
  vendorId: null,
  costCenterId: null,
  itemId: null,
  attachmentCount: 0,
  source: 'manual',
})

export function VoucherLineGrid({
  accounts,
  lines,
  onLinesChange,
  lockedAccountIds = [],
  readOnly,
  loading,
  renderDimensionCell,
  onAddAttachment,
  allowNegative = false,
}: VoucherLineGridProps) {
  const totals = useMemo(() => {
    return lines.reduce(
      (acc, line) => {
        acc.debit += line.debit ?? 0
        acc.credit += line.credit ?? 0
        return acc
      },
      { debit: 0, credit: 0 },
    )
  }, [lines])

  const difference = totals.debit - totals.credit
  const isBalanced = Math.abs(difference) < 0.0001

  const updateLine = React.useCallback(
    (index: number, patch: Partial<VoucherLine>) => {
      if (!onLinesChange) return
      const next = lines.map((line, idx) => (idx === index ? { ...line, ...patch } : line))
      onLinesChange(next)
    },
    [lines, onLinesChange],
  )

  const insertLine = React.useCallback(
    (index: number) => {
      if (!onLinesChange) return
      const next = [...lines.slice(0, index), createEmptyLine(index), ...lines.slice(index)]
      onLinesChange(next)
    },
    [lines, onLinesChange],
  )

  const removeLine = React.useCallback(
    (index: number) => {
      if (!onLinesChange) return
      if (lines.length === 1) {
        onLinesChange([createEmptyLine(0)])
        return
      }
      const next = lines.filter((_, idx) => idx !== index)
      onLinesChange(next)
    },
    [lines, onLinesChange],
  )

  return (
    <TooltipProvider>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="text-sm font-medium text-muted-foreground">
            Dòng chứng từ
            {loading ? <span className="ml-2 animate-pulse text-xs">Đang tải...</span> : null}
          </div>
          {!readOnly ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => insertLine(lines.length)}
              disabled={loading}
            >
              <PlusIcon className="mr-2 h-4 w-4" />
              Thêm dòng
            </Button>
          ) : null}
        </div>
        <div className="rounded-md border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow>
                <TableHead className="w-[40px] text-center">#</TableHead>
                <TableHead className="min-w-[220px]">Tài khoản</TableHead>
                <TableHead className="min-w-[180px]">Diễn giải</TableHead>
                <TableHead className="w-[160px] text-right">Nợ</TableHead>
                <TableHead className="w-[160px] text-right">Có</TableHead>
                <TableHead className="min-w-[140px]">Khách hàng</TableHead>
                <TableHead className="min-w-[140px]">Nhà cung cấp</TableHead>
                <TableHead className="min-w-[140px]">Trung tâm chi phí</TableHead>
                <TableHead className="min-w-[140px]">Vật tư hàng hóa</TableHead>
                <TableHead className="w-[1%] text-center">Đính kèm</TableHead>
                <TableHead className="w-[1%]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {lines.map((line, index) => {
                const locked = lockedAccountIds.includes(line.account?.id ?? '')
                return (
                  <TableRow key={line.id}>
                    <TableCell className="text-center align-middle">{index + 1}</TableCell>
                    <TableCell className="align-middle">
                      <AccountPicker
                        value={line.account}
                        options={accounts}
                        onChange={(account) => updateLine(index, { account })}
                        allowOverride={!locked && !readOnly}
                        disabled={readOnly || loading}
                        lockReason={
                          locked && line.source === 'template'
                            ? 'Tài khoản được cố định bởi mẫu chứng từ'
                            : undefined
                        }
                      />
                      {line.source === 'template' ? (
                        <Badge variant="secondary" className="mt-1">
                          Từ mẫu
                        </Badge>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <Input
                        value={line.description ?? ''}
                        onChange={(event) => updateLine(index, { description: event.target.value })}
                        disabled={readOnly || loading}
                        placeholder="Mô tả dòng chứng từ"
                      />
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.debit ?? null}
                        onChange={(amount) => {
                          updateLine(index, { debit: amount, credit: amount ? 0 : null })
                        }}
                        allowNegative={allowNegative}
                        disabled={readOnly || loading}
                        decimals={0}
                      />
                    </TableCell>
                    <TableCell>
                      <MoneyInput
                        value={line.credit ?? null}
                        onChange={(amount) => {
                          updateLine(index, { credit: amount, debit: amount ? 0 : null })
                        }}
                        allowNegative={allowNegative}
                        disabled={readOnly || loading}
                        decimals={0}
                      />
                    </TableCell>
                    <TableCell>
                      {renderDimensionCell ? (
                        renderDimensionCell('customer', line, index, (patch) =>
                          updateLine(index, patch),
                        )
                      ) : (
                        <Input
                          value={line.customerId ?? ''}
                          onChange={(event) =>
                            updateLine(index, { customerId: event.target.value })
                          }
                          disabled={readOnly || loading}
                          placeholder="Khách hàng"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {renderDimensionCell ? (
                        renderDimensionCell('vendor', line, index, (patch) =>
                          updateLine(index, patch),
                        )
                      ) : (
                        <Input
                          value={line.vendorId ?? ''}
                          onChange={(event) => updateLine(index, { vendorId: event.target.value })}
                          disabled={readOnly || loading}
                          placeholder="Nhà cung cấp"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {renderDimensionCell ? (
                        renderDimensionCell('costCenter', line, index, (patch) =>
                          updateLine(index, patch),
                        )
                      ) : (
                        <Input
                          value={line.costCenterId ?? ''}
                          onChange={(event) =>
                            updateLine(index, { costCenterId: event.target.value })
                          }
                          disabled={readOnly || loading}
                          placeholder="Trung tâm chi phí"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {renderDimensionCell ? (
                        renderDimensionCell('item', line, index, (patch) =>
                          updateLine(index, patch),
                        )
                      ) : (
                        <Input
                          value={line.itemId ?? ''}
                          onChange={(event) => updateLine(index, { itemId: event.target.value })}
                          disabled={readOnly || loading}
                          placeholder="Vật tư"
                        />
                      )}
                    </TableCell>
                    <TableCell className="text-center">
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => onAddAttachment?.(line.id)}
                            disabled={readOnly || loading}
                          >
                            📎
                          </Button>
                        </TooltipTrigger>
                        <TooltipContent>
                          {line.attachmentCount
                            ? `${line.attachmentCount} tập tin`
                            : 'Thêm đính kèm'}
                        </TooltipContent>
                      </Tooltip>
                    </TableCell>
                    <TableCell>
                      {!readOnly ? (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="size-8">
                              ⋮
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => insertLine(index + 1)}>
                              Chèn dòng bên dưới
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => insertLine(index)}>
                              Chèn dòng bên trên
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => updateLine(index, createEmptyLine(index))}
                            >
                              Xóa nội dung
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              className="text-destructive"
                              onClick={() => removeLine(index)}
                            >
                              <TrashIcon className="mr-2 size-4" />
                              Xóa dòng
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      ) : null}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>

        <div className="flex flex-col gap-2 rounded-md border bg-muted/30 p-3 text-sm md:flex-row md:items-center md:justify-between">
          <div className="flex flex-wrap items-center gap-3">
            <span>
              Tổng nợ: <strong>{totals.debit.toLocaleString('vi-VN')}</strong>
            </span>
            <span>
              Tổng có: <strong>{totals.credit.toLocaleString('vi-VN')}</strong>
            </span>
          </div>
          <div className={cn('font-medium', isBalanced ? 'text-success' : 'text-destructive')}>
            {isBalanced
              ? '✓ Đã cân đối'
              : `✗ Lệch ${Math.abs(difference).toLocaleString('vi-VN')} (${difference > 0 ? 'thừa nợ' : 'thừa có'})`}
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}

export default VoucherLineGrid
