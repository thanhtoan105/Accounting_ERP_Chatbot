import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { format } from 'date-fns'
import type { ColumnDef } from '@tanstack/react-table'
import { FileText, Trash2, MoreVertical, Eye } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { VoucherListDTO } from '@/types/voucher'
import { VoucherStatusBadge, VoucherAmountCell } from '@/features/accounting/components/voucher-ui'

/**
 * VoucherList Column Definitions
 *
 * Features:
 * - Sortable columns for date, status, number, amounts
 * - Right-aligned amounts with tabular numbers
 * - Status badges with semantic colors
 * - Inline actions dropdown
 */

function formatDate(value: string | null) {
  if (!value) return '—'
  try {
    return format(new Date(value), 'dd/MM/yyyy')
  } catch {
    return value
  }
}

export interface VoucherColumnsOptions {
  onDelete: (voucher: VoucherListDTO) => void
  onViewAttachments: (voucher: VoucherListDTO) => void
}

export function useVoucherColumns(options: VoucherColumnsOptions) {
  const navigate = useNavigate()
  const { onDelete, onViewAttachments } = options

  return useMemo<ColumnDef<VoucherListDTO>[]>(
    () => [
      {
        accessorKey: 'voucherNumber',
        header: 'Voucher No.',
        cell: ({ row }) => (
          <span className="font-medium text-[var(--voucher-primary)]">
            {row.original.voucherNumber}
          </span>
        ),
        enableSorting: true,
      },
      {
        accessorKey: 'voucherDate',
        header: 'Date',
        cell: ({ row }) => (
          <span className="text-muted-foreground">{formatDate(row.original.voucherDate)}</span>
        ),
        enableSorting: true,
      },
      {
        accessorKey: 'type',
        header: 'Type',
        cell: ({ row }) => (
          <Badge variant="outline" className="font-normal">
            {row.original.type || '—'}
          </Badge>
        ),
      },
      {
        id: 'amount',
        header: () => <div className="text-right">Amount</div>,
        cell: ({ row }) => {
          const total = row.original.totalDebit || row.original.totalCredit || 0
          return (
            <VoucherAmountCell value={total} currency={row.original.currency} variant="neutral" />
          )
        },
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <VoucherStatusBadge
            status={row.original.status as 'draft' | 'posted' | 'unposted'}
            size="sm"
          />
        ),
        enableSorting: true,
      },
      {
        accessorKey: 'enteredByName',
        header: 'Entered By',
        cell: ({ row }) => <span className="text-sm">{row.original.enteredByName || '—'}</span>,
      },
      {
        accessorKey: 'postedByName',
        header: 'Posted By',
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground">{row.original.postedByName || '—'}</span>
        ),
      },
      {
        accessorKey: 'arApEntity',
        header: 'AR/AP Entity',
        cell: ({ row }) => <span className="text-sm">{row.original.arApEntity || '—'}</span>,
      },
      {
        id: 'reversal',
        header: 'Reversal',
        cell: ({ row }) => {
          if (row.original.hasReversal && row.original.reversedByVoucherId) {
            return (
              <Badge
                variant="outline"
                className="cursor-pointer hover:bg-purple-50 dark:hover:bg-purple-950 text-purple-600 dark:text-purple-400 border-purple-200"
                onClick={(e) => {
                  e.stopPropagation()
                  navigate(`/vouchers/${row.original.reversedByVoucherId}`)
                }}
              >
                Reversed
              </Badge>
            )
          }
          return <span className="text-muted-foreground">—</span>
        },
      },
      {
        accessorKey: 'attachmentCount',
        header: () => <div className="text-center">Files</div>,
        cell: ({ row }) => {
          const count = row.original.attachmentCount || 0
          return (
            <Button
              variant="ghost"
              size="sm"
              className="h-auto p-1.5 gap-1"
              onClick={(e) => {
                e.stopPropagation()
                onViewAttachments(row.original)
              }}
            >
              <FileText className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="voucher-tabular-nums text-xs">{count}</span>
            </Button>
          )
        },
      },
      {
        id: 'actions',
        header: '',
        cell: ({ row }) => {
          const voucher = row.original
          const canDelete = voucher.status === 'draft'

          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-8 w-8">
                  <MoreVertical className="h-4 w-4" />
                  <span className="sr-only">Actions</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-40">
                <DropdownMenuItem
                  onClick={() => navigate(`/vouchers/${voucher.id}`)}
                  className="gap-2"
                >
                  <Eye className="h-4 w-4" />
                  View Details
                </DropdownMenuItem>
                {canDelete && (
                  <DropdownMenuItem
                    onClick={() => onDelete(voucher)}
                    className="gap-2 text-destructive focus:text-destructive"
                  >
                    <Trash2 className="h-4 w-4" />
                    Delete
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          )
        },
      },
    ],
    [navigate, onDelete, onViewAttachments],
  )
}

export default useVoucherColumns
