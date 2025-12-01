'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Download,
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
import { arAgingApi } from '../../services/arAgingApi'
import type { ARAgingReport as ARAgingReportType } from '../../services/arAgingApi'
import { AgingInvoiceDetailsDialog } from '@/components/ar-aging/AgingInvoiceDetailsDialog'
import { getCustomers } from '@/features/customers/services/customer'
import type { Customer } from '@/types/customer'
import { formatCurrency } from '@/utils/format'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const BUCKET_OPTIONS: { value: string; label: string }[] = [
  { value: 'ALL', label: 'All Buckets' },
  { value: 'CURRENT', label: 'Current' },
  { value: 'DAYS_1_30', label: '1-30 Days' },
  { value: 'DAYS_31_60', label: '31-60 Days' },
  { value: 'DAYS_61_90', label: '61-90 Days' },
  { value: 'DAYS_OVER_90', label: 'Over 90 Days' },
]

export function ARAgingReport() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reports, setReports] = useState<ARAgingReportType[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [sorting, setSorting] = useState<SortingState>([])

  // Filters
  const [customerId, setCustomerId] = useState<number | undefined>(undefined)
  const [asOfDate, setAsOfDate] = useState<string>(format(new Date(), 'yyyy-MM-dd'))
  const [status, setStatus] = useState<string>('')
  const [bucket, setBucket] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Customer list for filter
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loadingCustomers, setLoadingCustomers] = useState(false)

  // Export loading
  const [exporting, setExporting] = useState(false)

  // Drill-down dialog
  const [drillDownOpen, setDrillDownOpen] = useState(false)
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | null>(null)
  const [selectedBucket, setSelectedBucket] = useState<string>('')

  // Convert sorting state to API sort parameters
  const sortParams = useMemo(() => {
    if (sorting.length === 0) return undefined
    const sort = sorting[0]
    const direction = sort.desc ? 'desc' : 'asc'
    const fieldMap: Record<string, string> = {
      customerName: 'customerName',
      totalOutstanding: 'totalOutstanding',
      current: 'buckets.current',
      days1To30: 'buckets.days1To30',
      days31To60: 'buckets.days31To60',
      days61To90: 'buckets.days61To90',
      daysOver90: 'buckets.daysOver90',
    }
    const field = fieldMap[sort.id] || sort.id
    return field
  }, [sorting])

  const sortDir = useMemo(() => {
    if (sorting.length === 0) return undefined
    return sorting[0].desc ? 'desc' : 'asc'
  }, [sorting])

  const loadAgingReport = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // Build params object, only including defined values
      const params: Record<string, any> = {
        page,
        size: pageSize,
      }
      
      if (customerId) params.customerId = customerId
      if (asOfDate) params.asOfDate = asOfDate
      if (status) params.status = status
      if (bucket && bucket !== 'ALL') params.bucket = bucket
      if (sortParams) params.sortBy = sortParams
      if (sortDir) params.sortDir = sortDir

      const response = await arAgingApi.getAgingReport(params)
      if (response?.data) {
        setReports(response.data.content || [])
        setTotalElements(response.data.totalElements || 0)
        setTotalPages(response.data.totalPages || 0)
      } else {
        throw new Error('Invalid response structure from server')
      }
    } catch (err: any) {
      const status = err?.response?.status
      let message = err?.response?.data?.error?.message || 
                    err?.response?.data?.message || 
                    err?.message || 
                    'Unable to load aging report'
      
      // Handle specific status codes
      if (status === 403) {
        message = 'Access denied: You do not have permission to view AR aging reports. Please contact your administrator.'
      } else if (status === 401) {
        message = 'Authentication required. Please log in again.'
      } else if (status === 500) {
        message = `Server error: ${message}. Please try again or contact support.`
        console.error('AR Aging Report 500 Error:', err?.response?.data || err)
      }
      
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
  }, [page, pageSize, customerId, asOfDate, status, bucket, sortParams, sortDir])

  useEffect(() => {
    loadAgingReport()
  }, [loadAgingReport])

  // Load customers for filter dropdown
  useEffect(() => {
    const loadCustomers = async () => {
      try {
        setLoadingCustomers(true)
        const response = await getCustomers({ size: 1000, status: true })
        setCustomers(response?.data || [])
      } catch (err: any) {
        console.error('Failed to load customers:', err)
        // Set empty array on error to prevent crash
        setCustomers([])
        if (err?.response?.status === 403) {
          toast.error('Access denied', {
            description: 'You do not have permission to view customers',
          })
        }
      } finally {
        setLoadingCustomers(false)
      }
    }
    loadCustomers()
  }, [])

  const handleRefresh = async () => {
    await loadAgingReport()
    toast.success('Aging report refreshed')
  }

  const handleBucketClick = (customerId: number, bucket: string) => {
    setSelectedCustomerId(customerId)
    setSelectedBucket(bucket)
    setDrillDownOpen(true)
  }

  const handleExport = async (format: 'EXCEL' | 'PDF') => {
    try {
      setExporting(true)
      const blob = await arAgingApi.exportReport(format, {
        customerId,
        asOfDate: asOfDate || undefined,
      })

      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `ar-aging-report.${format.toLowerCase() === 'excel' ? 'xlsx' : 'pdf'}`
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

  const columns = useMemo<ColumnDef<ARAgingReportType>[]>(
    () => [
      {
        id: 'customerCode',
        accessorKey: 'customerCode',
        header: 'Customer Code',
        cell: ({ row }) => row.original.customerCode,
      },
      {
        id: 'customerName',
        accessorKey: 'customerName',
        header: 'Customer Name',
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            {row.original.customerName}
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
            onClick={() => handleBucketClick(row.original.customerId, 'CURRENT')}
            className="text-right hover:underline text-primary w-full text-right"
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
            onClick={() => handleBucketClick(row.original.customerId, 'DAYS_1_30')}
            className="text-right hover:underline text-primary w-full text-right"
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
            onClick={() => handleBucketClick(row.original.customerId, 'DAYS_31_60')}
            className="text-right hover:underline text-primary w-full text-right"
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
            onClick={() => handleBucketClick(row.original.customerId, 'DAYS_61_90')}
            className="text-right hover:underline text-primary w-full text-right"
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
            onClick={() => handleBucketClick(row.original.customerId, 'DAYS_OVER_90')}
            className="text-right hover:underline text-destructive font-semibold w-full text-right"
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
          <div className="text-right font-semibold">
            {formatCurrency(row.original.totalOutstanding)}
          </div>
        ),
      },
    ],
    []
  )

  // Filter reports by search query (client-side)
  const filteredReports = useMemo(() => {
    if (!searchQuery.trim()) return reports
    const query = searchQuery.toLowerCase()
    return reports.filter(
      (report) =>
        report.customerName.toLowerCase().includes(query) ||
        report.customerCode.toLowerCase().includes(query)
    )
  }, [reports, searchQuery])

  const table = useReactTable({
    data: filteredReports,
    columns,
    state: {
      sorting,
    },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    manualPagination: true,
    manualSorting: true,
    pageCount: totalPages,
  })

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">AR Aging Report</h1>
          <p className="text-muted-foreground">
            View accounts receivable aging by customer
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={loading}
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleExport('EXCEL')}
            disabled={exporting}
          >
            <Download className="h-4 w-4 mr-2" />
            Export Excel
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleExport('PDF')}
            disabled={exporting}
          >
            <Download className="h-4 w-4 mr-2" />
            Export PDF
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        <div>
          <label className="text-sm font-medium mb-1 block">Customer</label>
          <Select
            value={customerId ? String(customerId) : 'ALL'}
            onValueChange={(value) => setCustomerId(value === 'ALL' ? undefined : Number(value))}
          >
            <SelectTrigger>
              <SelectValue placeholder="All Customers" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Customers</SelectItem>
              {customers && customers.length > 0 ? (
                customers.map((customer) => (
                  <SelectItem key={customer.id} value={String(customer.id)}>
                    {customer.code} - {customer.name}
                  </SelectItem>
                ))
              ) : (
                <SelectItem value="NO_CUSTOMERS" disabled>
                  {loadingCustomers ? 'Loading customers...' : 'No customers available'}
                </SelectItem>
              )}
            </SelectContent>
          </Select>
        </div>
        <div>
          <label className="text-sm font-medium mb-1 block">Search</label>
          <input
            type="text"
            placeholder="Search customer name..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          />
        </div>
        <div>
          <label className="text-sm font-medium mb-1 block">As Of Date</label>
          <DatePicker
            value={asOfDate || undefined}
            onChange={(date) => setAsOfDate(date || '')}
          />
        </div>
        <div>
          <label className="text-sm font-medium mb-1 block">Bucket</label>
          <Select value={bucket} onValueChange={setBucket}>
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
        <div className="flex items-end">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setCustomerId(undefined)
              setSearchQuery('')
              setBucket('ALL')
            }}
            className="w-full"
          >
            Clear Filters
          </Button>
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
                  <TableHead key={header.id}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                        header.column.columnDef.header,
                        header.getContext()
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
            ) : table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-24 text-center"
                >
                  No results found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalElements)} of{' '}
          {totalElements} results
        </div>
        <div className="flex items-center space-x-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(0)}
            disabled={page === 0}
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(page - 1)}
            disabled={page === 0}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(page + 1)}
            disabled={page >= totalPages - 1}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(totalPages - 1)}
            disabled={page >= totalPages - 1}
          >
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Drill-down Dialog */}
      {selectedCustomerId && (
        <AgingInvoiceDetailsDialog
          open={drillDownOpen}
          onOpenChange={setDrillDownOpen}
          customerId={selectedCustomerId}
          bucket={selectedBucket}
          asOfDate={asOfDate}
        />
      )}
    </div>
  )
}
