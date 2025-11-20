'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  FileText,
  Download,
  Calendar,
  AlertTriangle,
} from 'lucide-react'
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
import { Input } from '@/components/ui/input'
import { DatePicker } from '@/components/ui/date-picker'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getAgingReport,
  exportAgingReport,
} from '@/services/apAging'
import type {
  APAgingReportDTO,
  APAgingQueryParams,
} from '@/types/apAging'
import { AgingBillDetailsDialog } from '@/components/ap-aging/AgingBillDetailsDialog'
import { ReminderDialog } from '@/components/ap-aging/ReminderDialog'
import { formatCurrency } from '@/utils/format'
import { Mail } from 'lucide-react'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const BUCKET_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'All Buckets' },
  { value: 'CURRENT', label: 'Current' },
  { value: 'DAYS_1_30', label: '1-30 Days' },
  { value: 'DAYS_31_60', label: '31-60 Days' },
  { value: 'DAYS_61_90', label: '61-90 Days' },
  { value: 'DAYS_OVER_90', label: 'Over 90 Days' },
]

export function APAgingReport() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reports, setReports] = useState<APAgingReportDTO[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [sorting, setSorting] = useState<SortingState>([])

  // Filters
  const [supplier, setSupplier] = useState<number | undefined>(undefined)
  const [period, setPeriod] = useState<number | undefined>(undefined)
  const [asOfDate, setAsOfDate] = useState<string>(format(new Date(), 'yyyy-MM-dd'))
  const [status, setStatus] = useState<string>('')
  const [bucket, setBucket] = useState<string>('')

  // Drill-down dialog
  const [billDetailsDialogOpen, setBillDetailsDialogOpen] = useState(false)
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | null>(null)
  const [selectedBucket, setSelectedBucket] = useState<string>('')

  // Reminder dialog
  const [reminderDialogOpen, setReminderDialogOpen] = useState(false)
  const [selectedSupplierForReminder, setSelectedSupplierForReminder] = useState<number | undefined>(undefined)

  // Export loading
  const [exporting, setExporting] = useState(false)

  // Convert sorting state to API sort parameters
  const sortParams = useMemo(() => {
    return sorting.map((sort) => {
      const direction = sort.desc ? 'desc' : 'asc'
      const fieldMap: Record<string, string> = {
        supplierName: 'supplierName',
        totalOutstanding: 'totalOutstanding',
        current: 'buckets.current',
        days1To30: 'buckets.days1To30',
        days31To60: 'buckets.days31To60',
        days61To90: 'buckets.days61To90',
        daysOver90: 'buckets.daysOver90',
      }
      const field = fieldMap[sort.id] || sort.id
      return `${field},${direction}`
    })
  }, [sorting])

  const loadAgingReport = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const params: APAgingQueryParams = {
        page,
        size: pageSize,
        supplier,
        period,
        asOfDate: asOfDate || undefined,
        status: status || undefined,
        bucket: bucket || undefined,
        sort: sortParams.length > 0 ? sortParams : undefined,
      }

      const response = await getAgingReport(params)
      setReports(response.content)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load aging report'
      setError(message)
      toast.error('Failed to load aging report', {
        description: message,
        action: {
          label: 'Retry',
          onClick: () => loadAgingReport(),
        },
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, supplier, period, asOfDate, status, bucket, sortParams])

  useEffect(() => {
    loadAgingReport()
  }, [loadAgingReport])

  const handleRefresh = async () => {
    await loadAgingReport()
    toast.success('Aging report refreshed')
  }

  const handleExport = async (format: 'EXCEL' | 'PDF') => {
    try {
      setExporting(true)
      const blob = await exportAgingReport(format, {
        supplier,
        period,
        asOfDate: asOfDate || undefined,
        status: status || undefined,
        bucket: bucket || undefined,
      })

      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `ap-aging-report.${format.toLowerCase() === 'excel' ? 'xlsx' : 'pdf'}`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)

      toast.success(`Aging report exported as ${format}`)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Export failed'
      toast.error('Failed to export aging report', {
        description: message,
      })
    } finally {
      setExporting(false)
    }
  }

  const handleBucketClick = (supplierId: number, bucket: string) => {
    setSelectedSupplierId(supplierId)
    setSelectedBucket(bucket)
    setBillDetailsDialogOpen(true)
  }

  const columns = useMemo<ColumnDef<APAgingReportDTO>[]>(
    () => [
      {
        id: 'supplierCode',
        accessorKey: 'supplierCode',
        header: 'Supplier Code',
        cell: ({ row }) => row.original.supplierCode,
      },
      {
        id: 'supplierName',
        accessorKey: 'supplierName',
        header: 'Supplier Name',
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            {row.original.supplierName}
            {row.original.hasOverdue && (
              <Badge variant="destructive" className="text-xs">
                <AlertTriangle className="h-3 w-3 mr-1" />
                Overdue
              </Badge>
            )}
          </div>
        ),
      },
      {
        id: 'current',
        accessorKey: 'buckets.current',
        header: 'Current',
        cell: ({ row }) => (
          <button
            onClick={() => handleBucketClick(row.original.supplierId, 'CURRENT')}
            className="text-right hover:underline text-primary"
          >
            {formatCurrency(row.original.buckets.current)}
          </button>
        ),
      },
      {
        id: 'days1To30',
        accessorKey: 'buckets.days1To30',
        header: '1-30 Days',
        cell: ({ row }) => (
          <button
            onClick={() => handleBucketClick(row.original.supplierId, 'DAYS_1_30')}
            className="text-right hover:underline text-primary"
          >
            {formatCurrency(row.original.buckets.days1To30)}
          </button>
        ),
      },
      {
        id: 'days31To60',
        accessorKey: 'buckets.days31To60',
        header: '31-60 Days',
        cell: ({ row }) => (
          <button
            onClick={() => handleBucketClick(row.original.supplierId, 'DAYS_31_60')}
            className="text-right hover:underline text-primary"
          >
            {formatCurrency(row.original.buckets.days31To60)}
          </button>
        ),
      },
      {
        id: 'days61To90',
        accessorKey: 'buckets.days61To90',
        header: '61-90 Days',
        cell: ({ row }) => (
          <button
            onClick={() => handleBucketClick(row.original.supplierId, 'DAYS_61_90')}
            className="text-right hover:underline text-primary"
          >
            {formatCurrency(row.original.buckets.days61To90)}
          </button>
        ),
      },
      {
        id: 'daysOver90',
        accessorKey: 'buckets.daysOver90',
        header: 'Over 90 Days',
        cell: ({ row }) => (
          <button
            onClick={() => handleBucketClick(row.original.supplierId, 'DAYS_OVER_90')}
            className="text-right hover:underline text-destructive font-semibold"
          >
            {formatCurrency(row.original.buckets.daysOver90)}
          </button>
        ),
      },
      {
        id: 'totalOutstanding',
        accessorKey: 'totalOutstanding',
        header: 'Total Outstanding',
        cell: ({ row }) => (
          <div className="text-right font-semibold">{formatCurrency(row.original.totalOutstanding)}</div>
        ),
      },
      {
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => {
          const supplier = row.original
          return (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSelectedSupplierForReminder(supplier.supplierId)
                setReminderDialogOpen(true)
              }}
              disabled={!supplier.hasOverdue}
            >
              <Mail className="h-4 w-4 mr-2" />
              Remind
            </Button>
          )
        },
      },
    ],
    [],
  )

  const table = useReactTable({
    data: reports,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    manualSorting: true,
    state: {
      sorting,
    },
  })

  const isEmpty = !loading && reports.length === 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            AP Aging Report <span className="text-muted-foreground text-lg">/ Báo cáo công nợ phải trả</span>
          </h1>
          <p className="text-muted-foreground">
            View aging buckets by supplier with drill-down to bill/payment history.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button
            variant="outline"
            onClick={() => handleExport('EXCEL')}
            disabled={exporting}
          >
            <Download className={`mr-2 h-4 w-4 ${exporting ? 'animate-spin' : ''}`} />
            Export Excel
          </Button>
          <Button
            variant="outline"
            onClick={() => handleExport('PDF')}
            disabled={exporting}
          >
            <Download className={`mr-2 h-4 w-4 ${exporting ? 'animate-spin' : ''}`} />
            Export PDF
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Supplier ID</label>
          <Input
            type="number"
            placeholder="Filter by supplier ID"
            value={supplier || ''}
            onChange={(e) => {
              setSupplier(e.target.value ? Number(e.target.value) : undefined)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Period ID</label>
          <Input
            type="number"
            placeholder="Filter by period ID"
            value={period || ''}
            onChange={(e) => {
              setPeriod(e.target.value ? Number(e.target.value) : undefined)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            As Of Date
          </label>
          <DatePicker
            value={asOfDate}
            onChange={(value) => {
              setAsOfDate(value || format(new Date(), 'yyyy-MM-dd'))
              setPage(0)
            }}
            placeholder="Select date"
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Status</label>
          <Input
            placeholder="Filter by status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Aging Bucket</label>
          <Select
            value={bucket}
            onValueChange={(value) => {
              setBucket(value)
              setPage(0)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All Buckets" />
            </SelectTrigger>
            <SelectContent>
              {BUCKET_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id} className="text-right">
                    {header.isPlaceholder ? null : (
                      <div
                        className={
                          header.column.getCanSort()
                            ? 'cursor-pointer select-none flex items-center justify-end gap-2'
                            : 'flex items-center justify-end'
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
            ) : isEmpty ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-center py-8 text-muted-foreground">
                  No aging data found
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="text-right">
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
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">
            Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalElements)} of{' '}
            {totalElements} suppliers
          </span>
          <Select
            value={String(pageSize)}
            onValueChange={(value) => {
              setPageSize(Number(value))
              setPage(0)
            }}
          >
            <SelectTrigger className="w-[100px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZE_OPTIONS.map((size) => (
                <SelectItem key={size} value={String(size)}>
                  {size}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <span className="text-sm text-muted-foreground">per page</span>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            onClick={() => setPage(0)}
            disabled={page === 0 || loading}
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setPage(page - 1)}
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
          <Button
            variant="outline"
            size="icon"
            onClick={() => setPage(totalPages - 1)}
            disabled={page >= totalPages - 1 || loading}
          >
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Bill Details Dialog */}
      {selectedSupplierId !== null && (
        <AgingBillDetailsDialog
          open={billDetailsDialogOpen}
          onOpenChange={setBillDetailsDialogOpen}
          supplierId={selectedSupplierId}
          bucket={selectedBucket}
          period={period}
          asOfDate={asOfDate || undefined}
        />
      )}

      {/* Reminder Dialog */}
      <ReminderDialog
        open={reminderDialogOpen}
        onOpenChange={setReminderDialogOpen}
        supplierId={selectedSupplierForReminder}
        onSuccess={() => {
          loadAgingReport()
        }}
      />
    </div>
  )
}

