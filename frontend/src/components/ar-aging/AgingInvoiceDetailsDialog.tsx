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
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
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
import { arAgingApi } from '@/features/accounting/services/arAgingApi'
import { InvoiceDetailModal } from './InvoiceDetailModal'
import { formatCurrency } from '@/utils/format'

interface AgingInvoiceDetail {
  invoiceId: string
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  totalAmount: number
  outstandingAmount: number
  remainingBalance?: number // Alias for outstandingAmount
  amountPaid: number
  daysOverdue: number
  status: string
  lastPaymentDate?: string
  customerName?: string
  paymentHistory?: Array<{
    receiptNumber: string
    receiptDate: string
    amount: number
  }>
}

interface AgingInvoiceDetailsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customerId: number
  bucket: string
  asOfDate?: string
}

export function AgingInvoiceDetailsDialog({
  open,
  onOpenChange,
  customerId,
  bucket,
  asOfDate,
}: AgingInvoiceDetailsDialogProps) {
  const [loading, setLoading] = useState(false)
  const [invoices, setInvoices] = useState<AgingInvoiceDetail[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const pageSize = 20
  const [sorting, setSorting] = useState<SortingState>([])
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null)
  const [invoiceDetailOpen, setInvoiceDetailOpen] = useState(false)

  const loadInvoiceDetails = async () => {
    if (!open) return

    try {
      setLoading(true)
      const response = await arAgingApi.getDrillDownDetail(customerId, bucket, {
        asOfDate,
        page,
        size: pageSize,
      })
      // Backend returns Page object directly (content, totalElements, totalPages)
      if (response?.content !== undefined) {
        setInvoices(response.content || [])
        setTotalElements(response.totalElements || 0)
        setTotalPages(response.totalPages || 0)
      } else if (Array.isArray(response)) {
        // Fallback: if response is array directly
        setInvoices(response)
        setTotalElements(response.length)
        setTotalPages(1)
      } else {
        throw new Error('Invalid response structure from server')
      }
    } catch (err: any) {
      const message =
        err?.response?.data?.message || err?.message || 'Unable to load invoice details'
      toast.error('Failed to load invoice details', {
        description: message,
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      loadInvoiceDetails()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, customerId, bucket, asOfDate, page, pageSize])

  const columns = useMemo<ColumnDef<AgingInvoiceDetail>[]>(
    () => [
      {
        id: 'invoiceNumber',
        accessorKey: 'invoiceNumber',
        header: 'Invoice Number',
        cell: ({ row }) => (
          <button
            onClick={() => {
              // Use invoiceId (UUID) if available, fallback to invoiceNumber
              const id = row.original.invoiceId || row.original.invoiceNumber
              if (!id) {
                toast.error('Invoice ID not available')
                return
              }
              setSelectedInvoiceId(id)
              setInvoiceDetailOpen(true)
            }}
            className="text-primary hover:underline font-medium"
          >
            {row.original.invoiceNumber}
          </button>
        ),
      },
      {
        id: 'invoiceDate',
        accessorKey: 'invoiceDate',
        header: 'Invoice Date',
        cell: ({ row }) =>
          row.original.invoiceDate ? format(new Date(row.original.invoiceDate), 'dd/MM/yyyy') : '-',
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
        id: 'outstandingAmount',
        accessorKey: 'outstandingAmount',
        header: 'Outstanding',
        cell: ({ row }) => (
          <div className="text-right font-semibold">
            {formatCurrency(row.original.outstandingAmount || row.original.remainingBalance || 0)}
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
        header: 'Receipts',
        cell: ({ row }) => (
          <div className="text-center">{row.original.paymentHistory?.length || 0}</div>
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    data: invoices,
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
            <span>Invoice Details - {bucketLabel} Bucket</span>
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
                ) : invoices.length === 0 ? (
                  <TableRow>
                    <TableCell
                      colSpan={columns.length}
                      className="text-center py-8 text-muted-foreground"
                    >
                      No invoices found in this bucket
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
              {totalElements} invoices
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

      {/* Invoice Detail Modal */}
      {selectedInvoiceId && (
        <InvoiceDetailModal
          open={invoiceDetailOpen}
          onOpenChange={setInvoiceDetailOpen}
          invoiceId={selectedInvoiceId}
        />
      )}
    </Dialog>
  )
}
