import { useMemo } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { FileText, Trash2, MoreVertical } from 'lucide-react'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { PurchaseBillListDTO } from '@/types/purchaseBill'
import {
  PurchaseBillStatusBadge,
  PurchaseBillAmountCell,
} from '@/features/accounting/components/purchase-ui'

function formatDate(value: string | null) {
  if (!value) return '-'
  try {
    return format(new Date(value), 'dd/MM/yyyy')
  } catch {
    return value
  }
}

export interface UsePurchaseBillColumnsOptions {
  onDelete?: (bill: PurchaseBillListDTO) => void
  onViewAttachments?: (bill: PurchaseBillListDTO) => void
}

export function usePurchaseBillColumns({
  onDelete,
  onViewAttachments,
}: UsePurchaseBillColumnsOptions = {}): ColumnDef<PurchaseBillListDTO>[] {
  return useMemo<ColumnDef<PurchaseBillListDTO>[]>(
    () => [
      {
        accessorKey: 'billNumber',
        header: 'Bill Number',
        enableSorting: true,
        cell: ({ row }) => (
          <span className="font-medium text-foreground">{row.original.billNumber}</span>
        ),
      },
      {
        accessorKey: 'supplierName',
        header: 'Supplier',
        enableSorting: true,
        cell: ({ row }) => (
          <span className="text-sm">
            {row.original.supplierName || row.original.supplierCode || '-'}
          </span>
        ),
      },
      {
        accessorKey: 'billDate',
        header: 'Bill Date',
        enableSorting: true,
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground">{formatDate(row.original.billDate)}</span>
        ),
      },
      {
        accessorKey: 'dueDate',
        header: 'Due Date',
        enableSorting: true,
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground">{formatDate(row.original.dueDate)}</span>
        ),
      },
      {
        accessorKey: 'reference',
        header: 'Reference',
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground">{row.original.reference || '-'}</span>
        ),
      },
      {
        accessorKey: 'totalAmount',
        header: 'Amount',
        enableSorting: true,
        cell: ({ row }) => <PurchaseBillAmountCell value={row.original.totalAmount} />,
      },
      {
        accessorKey: 'vatAmount',
        header: 'VAT',
        cell: ({ row }) => (
          <PurchaseBillAmountCell value={row.original.vatAmount} className="text-sm" />
        ),
      },
      {
        accessorKey: 'status',
        header: 'Status',
        enableSorting: true,
        cell: ({ row }) => <PurchaseBillStatusBadge status={row.original.status} size="sm" />,
      },
      {
        accessorKey: 'attachmentCount',
        header: 'Files',
        cell: ({ row }) => {
          const count = row.original.attachmentCount || 0
          return (
            <div className="flex items-center gap-1 text-muted-foreground">
              <FileText className="h-3.5 w-3.5" />
              <span className="text-sm">{count}</span>
            </div>
          )
        },
      },
      {
        id: 'actions',
        header: '',
        cell: ({ row }) => {
          const bill = row.original
          const canDelete = bill.status === 'DRAFT'

          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                <Button variant="ghost" size="icon" className="h-8 w-8">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
                {onViewAttachments && (
                  <DropdownMenuItem onClick={() => onViewAttachments(bill)}>
                    <FileText className="mr-2 h-4 w-4" />
                    View Attachments
                  </DropdownMenuItem>
                )}
                {canDelete && onDelete && (
                  <DropdownMenuItem
                    onClick={() => onDelete(bill)}
                    className="text-destructive focus:text-destructive"
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          )
        },
      },
    ],
    [onDelete, onViewAttachments],
  )
}

export default usePurchaseBillColumns
