'use client'

import { useEffect, useState, useMemo } from 'react'
import { X, ChevronLeft, ChevronRight } from 'lucide-react'
import { format } from 'date-fns'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
} from '@tanstack/react-table'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { getAgingBillDetails } from '@/services/apAging'
import type { AgingBillDetailsDTO } from '@/types/apAging'
import { formatCurrency } from '@/utils/format'

interface AgingBillDetailsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  supplierId: number
  bucket: string
  period?: number
  asOfDate?: string
}

export function AgingBillDetailsDialog({
  open,
  onOpenChange,
  supplierId,
  bucket,
  period,
  asOfDate,
}: AgingBillDetailsDialogProps) {
  const [loading, setLoading] = useState(false)
  const [bills, setBills] = useState<AgingBillDetailsDTO[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [sorting, setSorting] = useState<SortingState>([])

  const loadBillDetails = async () => {
    if (!open) return

    try {
      setLoading(true)
      const response = await getAgingBillDetails(supplierId, {
        bucket,
        period,
        asOfDate,
        page,
        size: pageSize,
      })
      setBills(response.content)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load bill details'
      toast.error('Failed to load bill details', {
        description: message,
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      loadBillDetails()
    }
  }, [open, supplierId, bucket, period, asOfDate, page, pageSize])

  const columns = useMemo<ColumnDef<AgingBillDetailsDTO>[]>(
    () => [
      {
        id: 'billNumber',
        accessorKey: 'billNumber',
        header: 'Bill Number',
        cell: ({ row }) => row.original.billNumber,
      },
      {
        id: 'billDate',
        accessorKey: 'billDate',
        header: 'Bill Date',
        cell: ({ row }) =>
          row.original.billDate ? format(new Date(row.original.billDate), 'dd/MM/yyyy') : '-',
      },
      {
        id: 'dueDate',
        accessorKey: 'dueDate',
        header: 'Due Date',
        cell: ({ row }) =>
          row.original.dueDate ? format(new Date(row.original.dueDate), 'dd/MM/yyyy') : '-',
      },
      {
        id: 'totalAmount',
        accessorKey: 'totalAmount',
        header: 'Total Amount',
        cell: ({ row }) => (
          <div className="text-right">{formatCurrency(row.original.totalAmount)}</div>
        ),
      },
      {
        id: 'remainingBalance',
        accessorKey: 'remainingBalance',
        header: 'Remaining Balance',
        cell: ({ row }) => (
          <div className="text-right font-semibold">
            {formatCurrency(row.original.remainingBalance)}
          </div>
        ),
      },
      {
        id: 'status',
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <Badge variant={row.original.status === 'POSTED' ? 'default' : 'secondary'}>
            {row.original.status}
          </Badge>
        ),
      },
      {
        id: 'paymentCount',
        header: 'Payments',
        cell: ({ row }) => (
          <div className="text-center">{row.original.paymentHistory.length}</div>
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    data: bills,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    manualSorting: true,
    state: {
      sorting,
    },
  })

  const bucketLabel =
    bucket === 'CURRENT'
      ? 'Current'
      : bucket === 'DAYS_1_30'
        ? '1-30 Days'
        : bucket === 'DAYS_31_60'
          ? '31-60 Days'
          : bucket === 'DAYS_61_90'
            ? '61-90 Days'
            : bucket === 'DAYS_OVER_90'
              ? 'Over 90 Days'
              : bucket

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-6xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center justify-between">
            <span>Bill Details - {bucketLabel} Bucket</span>
            <Button variant="ghost" size="icon" onClick={() => onOpenChange(false)}>
              <X className="h-4 w-4" />
            </Button>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Table */}
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <TableHead key={header.id}>
                        {header.isPlaceholder ? null : (
                          <div
                            className={
                              header.column.getCanSort()
                                ? 'cursor-pointer select-none flex items-center gap-2'
                                : ''
                            }
                            onClick={header.column.getToggleSortingHandler()}
                          >
                            {flexRender(header.column.columnDef.header, header.getContext())}
                            {{
                              asc: ' ↑',
                              desc: ' ↓',
                            }[header.column.getIsSorted() as string] ?? null}
                          </div>
                        )}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {loading ? (
                  Array.from({ length: pageSize }).map((_, i) => (
                    <TableRow key={i}>
                      {columns.map((_, j) => (
                        <TableCell key={j}>
                          <Skeleton className="h-4 w-full" />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                ) : bills.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={columns.length} className="text-center py-8 text-muted-foreground">
                      No bills found in this bucket
                    </TableCell>
                  </TableRow>
                ) : (
                  table.getRowModel().rows.map((row) => (
                    <TableRow key={row.id}>
                      {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id}>
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          {/* Pagination */}
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">
              Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalElements)} of{' '}
              {totalElements} bills
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="icon"
                onClick={() => setPage(0)}
                disabled={page === 0 || loading}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm">
                Page {page + 1} of {totalPages || 1}
              </span>
              <Button
                variant="outline"
                size="icon"
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1 || loading}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

