'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Download,
  FileText,
  RefreshCw,
  Search,
  SlidersHorizontal,
  X,
  History,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
} from 'lucide-react'
import { format } from 'date-fns'
import { toast } from 'sonner'

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
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { DatePickerWithRange } from '@/components/ui/date-picker'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { vatService } from '@/services/vat'
import { Label } from '@/components/ui/label'
import type {
  OutputVATReportDTO,
  OutputVATReportRequest,
  VATExportFormat,
  VATReportHistoryItem,
} from '@/types/vat'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const VAT_CLASS_OPTIONS = ['ALL', 'ZERO', 'FIVE', 'TEN', 'EXEMPT'] as const
const HISTORY_PAGE_SIZE_OPTIONS = [10, 20, 30, 50]

/**
 * Output VAT Report List Component (AC-VAT-005)
 * Displays output VAT reports for sales invoices with customer-centric data.
 * Supports filtering by period, customer, and VAT class, with ND123-compliant Excel export.
 */
export function OutputVATReportList() {
  const [loading, setLoading] = useState(false)
  const [report, setReport] = useState<OutputVATReportDTO | null>(null)

  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [sortColumn, setSortColumn] = useState<string | null>(null)
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc')

  const [formDateRange, setFormDateRange] = useState<{
    from: Date | undefined
    to: Date | undefined
  }>({ from: undefined, to: undefined })
  const [formVatClass, setFormVatClass] = useState<(typeof VAT_CLASS_OPTIONS)[number]>('ALL')
  const [formCustomerId, setFormCustomerId] = useState('')

  const [appliedFilters, setAppliedFilters] = useState<OutputVATReportRequest>({})
  const [generateDialogOpen, setGenerateDialogOpen] = useState(false)

  // History state
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyItems, setHistoryItems] = useState<VATReportHistoryItem[]>([])
  const [historyPage, setHistoryPage] = useState(0)
  const [historyPageSize, setHistoryPageSize] = useState(20)
  const [historyTotalItems, setHistoryTotalItems] = useState(0)
  const [historyTotalPages, setHistoryTotalPages] = useState(1)
  const [_historySortColumn, _setHistorySortColumn] = useState<string | null>(null)
  const [_historySortDirection, _setHistorySortDirection] = useState<'asc' | 'desc'>('asc')
  const [historyFilter, _setHistoryFilter] = useState<{
    reportType: 'ALL' | 'INPUT_VAT' | 'OUTPUT_VAT'
    customerId: string
    vatClass: string
    dateRange: { from?: Date; to?: Date }
  }>({
    reportType: 'OUTPUT_VAT',
    customerId: '',
    vatClass: 'ALL',
    dateRange: {},
  })

  const loadReport = useCallback(async () => {
    try {
      setLoading(true)
      const data = await vatService.generateOutputReport(appliedFilters)
      setReport(data)
      setPage(0)
    } catch (error) {
      toast.error(`Failed to load output VAT report: ${String(error)}`)
    } finally {
      setLoading(false)
    }
  }, [appliedFilters])

  useEffect(() => {
    void loadReport()
  }, [loadReport])

  const loadHistory = useCallback(async () => {
    try {
      setHistoryLoading(true)
      const params: {
        page?: number
        size?: number
        reportType?: string
        customerId?: number
        vatClass?: string
        startDate?: string
        endDate?: string
      } = {
        page: historyPage,
        size: historyPageSize,
        reportType: 'OUTPUT_VAT',
      }
      if (historyFilter.customerId) {
        const numericCustomer = Number(historyFilter.customerId)
        if (Number.isNaN(numericCustomer)) {
          toast.error('Customer ID must be numeric')
          return
        }
        params.customerId = numericCustomer
      }
      if (historyFilter.vatClass !== 'ALL') {
        params.vatClass = historyFilter.vatClass
      }
      if (historyFilter.dateRange.from) {
        params.startDate = format(historyFilter.dateRange.from, 'yyyy-MM-dd')
      }
      if (historyFilter.dateRange.to) {
        params.endDate = format(historyFilter.dateRange.to, 'yyyy-MM-dd')
      }

      const response = await vatService.listReportHistory(params)
      setHistoryItems(response.items)
      setHistoryTotalItems(response.totalItems)
      setHistoryTotalPages(response.totalPages || 1)
    } catch (error) {
      toast.error(`Failed to load VAT report history: ${String(error)}`)
    } finally {
      setHistoryLoading(false)
    }
  }, [historyFilter, historyPage, historyPageSize])

  useEffect(() => {
    void loadHistory()
  }, [loadHistory])

  const handleRefresh = () => {
    setPage(0)
    void loadReport()
  }

  const handleApplyFilters = () => {
    if (formCustomerId && Number.isNaN(Number(formCustomerId))) {
      toast.error('Customer ID must be numeric')
      return
    }

    const nextFilters: OutputVATReportRequest = {}
    if (formDateRange.from) {
      nextFilters.startDate = format(formDateRange.from, 'yyyy-MM-dd')
    }
    if (formDateRange.to) {
      nextFilters.endDate = format(formDateRange.to, 'yyyy-MM-dd')
    }
    if (formVatClass !== 'ALL') {
      nextFilters.vatClass = formVatClass
    }
    if (formCustomerId) {
      nextFilters.customerId = Number(formCustomerId)
    }

    setAppliedFilters(nextFilters)
  }

  const handleClearFilters = () => {
    setFormDateRange({ from: undefined, to: undefined })
    setFormVatClass('ALL')
    setFormCustomerId('')
    setAppliedFilters({})
  }

  const handleExport = async (format: VATExportFormat) => {
    if (!report) return
    try {
      const blob = await vatService.exportOutputReport(report.reportId, format)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `output-vat-report-${report.reportId}.${format === 'EXCEL' ? 'xlsx' : 'pdf'}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success(`Output VAT report exported as ${format}`)
    } catch (error) {
      toast.error(`Failed to export output VAT report: ${String(error)}`)
    }
  }

  const handleSort = (column: string) => {
    if (sortColumn === column) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc')
    } else {
      setSortColumn(column)
      setSortDirection('asc')
    }
    setPage(0)
  }

  const getSortIcon = (column: string) => {
    if (sortColumn !== column) {
      return <ArrowUpDown className="ml-2 h-4 w-4" />
    }
    return sortDirection === 'asc' ? (
      <ArrowUp className="ml-2 h-4 w-4" />
    ) : (
      <ArrowDown className="ml-2 h-4 w-4" />
    )
  }

  const parseAmount = (amount: string): number => {
    return parseFloat(amount.replace(/[₫,\s]/g, '')) || 0
  }

  const formatCurrency = (amount: string | number): string => {
    const num = typeof amount === 'string' ? parseAmount(amount) : amount
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(num)
  }

  const filteredItems = useMemo(() => {
    if (!report) return []
    const query = searchTerm.trim().toLowerCase()
    const base = report.items
    const searched = !query
      ? base
      : base.filter((item) => {
          const customer = (item.customerName || '') + ' ' + (item.customerTaxCode || '')
          const invoice =
            (item.invoiceNumber || '') + ' ' + format(new Date(item.invoiceDate), 'dd/MM/yyyy')
          return customer.toLowerCase().includes(query) || invoice.toLowerCase().includes(query)
        })

    // Apply sorting
    if (sortColumn) {
      searched.sort((a, b) => {
        let aVal: string | number | Date
        let bVal: string | number | Date

        switch (sortColumn) {
          case 'customer':
            aVal = (a.customerName || '') + (a.customerTaxCode || '')
            bVal = (b.customerName || '') + (b.customerTaxCode || '')
            break
          case 'invoiceNumber':
            aVal = a.invoiceNumber || a.invoiceId
            bVal = b.invoiceNumber || b.invoiceId
            break
          case 'invoiceDate':
            aVal = new Date(a.invoiceDate).getTime()
            bVal = new Date(b.invoiceDate).getTime()
            break
          case 'revenue0pct':
            aVal = parseAmount(a.revenue0pct)
            bVal = parseAmount(b.revenue0pct)
            break
          case 'revenue5pct':
            aVal = parseAmount(a.revenue5pct)
            bVal = parseAmount(b.revenue5pct)
            break
          case 'revenue10pct':
            aVal = parseAmount(a.revenue10pct)
            bVal = parseAmount(b.revenue10pct)
            break
          case 'revenueExempt':
            aVal = parseAmount(a.revenueExempt)
            bVal = parseAmount(b.revenueExempt)
            break
          case 'totalVatCollected':
            aVal = parseAmount(a.vatAmount)
            bVal = parseAmount(b.vatAmount)
            break
          default:
            return 0
        }

        if (aVal < bVal) return sortDirection === 'asc' ? -1 : 1
        if (aVal > bVal) return sortDirection === 'asc' ? 1 : -1
        return 0
      })
    }

    return searched
  }, [report, searchTerm, sortColumn, sortDirection])

  const paginatedItems = useMemo(() => {
    const start = page * pageSize
    const end = start + pageSize
    return filteredItems.slice(start, end)
  }, [filteredItems, page, pageSize])

  const totalPages = Math.ceil(filteredItems.length / pageSize)
  const hasFilters = Boolean(
    formDateRange.from || formDateRange.to || formVatClass !== 'ALL' || formCustomerId,
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Output VAT Report{' '}
            <span className="text-muted-foreground text-lg">/ Báo cáo VAT đầu ra</span>
          </h1>
          <p className="text-muted-foreground">
            Generate and export output VAT reports for sales invoices (ND123 compliant)
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => setGenerateDialogOpen(true)}>
            <SlidersHorizontal className="mr-2 h-4 w-4" />
            Generate Report
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <SlidersHorizontal className="h-5 w-5" />
            Filters
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="flex flex-col gap-2">
              <Label>Date Range</Label>
              <DatePickerWithRange
                value={formDateRange}
                onChange={(v) =>
                  setFormDateRange(v as { from: Date | undefined; to: Date | undefined })
                }
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>Customer ID (optional)</Label>
              <Input
                type="number"
                placeholder="e.g. 1001"
                value={formCustomerId}
                onChange={(e) => setFormCustomerId(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>VAT Class (optional)</Label>
              <Select value={formVatClass} onValueChange={(value: any) => setFormVatClass(value)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {VAT_CLASS_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {option === 'ALL' ? 'All VAT Classes' : option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={handleApplyFilters} className="flex-1">
                Apply Filters
              </Button>
              {hasFilters && (
                <Button variant="outline" onClick={handleClearFilters}>
                  <X className="h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Report Table */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Report Data</CardTitle>
            {report && (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleExport('EXCEL')}
                  disabled={loading}
                >
                  <Download className="mr-2 h-4 w-4" />
                  Export Excel (ND123)
                </Button>
              </div>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : !report ? (
            <div className="text-center py-12 text-muted-foreground">
              <FileText className="mx-auto h-12 w-12 mb-4 opacity-50" />
              <p>No report generated yet. Click "Generate Report" to create a report.</p>
            </div>
          ) : (
            <div className="space-y-4">
              {/* Report Summary */}
              {report && (
                <div className="rounded-md border bg-muted/50 p-4">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                      <div className="text-muted-foreground">Period</div>
                      <div className="font-semibold">
                        {format(new Date(report.startDate), 'dd/MM/yyyy')} –{' '}
                        {format(new Date(report.endDate), 'dd/MM/yyyy')}
                      </div>
                    </div>
                    <div>
                      <div className="text-muted-foreground">Customer</div>
                      <div className="font-semibold">
                        {report.customerName || 'All Customers'}
                        {report.customerTaxCode && ` (${report.customerTaxCode})`}
                      </div>
                    </div>
                    <div>
                      <div className="text-muted-foreground">Total VAT Collected</div>
                      <div className="font-semibold">
                        {formatCurrency(report.totalVatCollected)}
                      </div>
                    </div>
                    <div>
                      <div className="text-muted-foreground">Generated</div>
                      <div className="font-semibold">
                        {report.generatedByName || 'System'} •{' '}
                        {format(new Date(report.generationDate), 'dd/MM/yyyy HH:mm')}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Search */}
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search by customer, invoice number, or date..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value)
                    setPage(0)
                  }}
                  className="pl-8"
                />
              </div>

              {/* Table */}
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead
                        className="cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('invoiceNumber')}
                      >
                        <div className="flex items-center">
                          Invoice Number
                          {getSortIcon('invoiceNumber')}
                        </div>
                      </TableHead>
                      <TableHead
                        className="cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('invoiceDate')}
                      >
                        <div className="flex items-center">
                          Invoice Date
                          {getSortIcon('invoiceDate')}
                        </div>
                      </TableHead>
                      <TableHead
                        className="cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('customer')}
                      >
                        <div className="flex items-center">
                          Customer Name
                          {getSortIcon('customer')}
                        </div>
                      </TableHead>
                      <TableHead>Customer Tax Code</TableHead>
                      <TableHead
                        className="text-right cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('revenue0pct')}
                      >
                        <div className="flex items-center justify-end">
                          Revenue (0%)
                          {getSortIcon('revenue0pct')}
                        </div>
                      </TableHead>
                      <TableHead
                        className="text-right cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('revenue5pct')}
                      >
                        <div className="flex items-center justify-end">
                          Revenue (5%)
                          {getSortIcon('revenue5pct')}
                        </div>
                      </TableHead>
                      <TableHead
                        className="text-right cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('revenue10pct')}
                      >
                        <div className="flex items-center justify-end">
                          Revenue (10%)
                          {getSortIcon('revenue10pct')}
                        </div>
                      </TableHead>
                      <TableHead
                        className="text-right cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('revenueExempt')}
                      >
                        <div className="flex items-center justify-end">
                          Revenue (Exempt)
                          {getSortIcon('revenueExempt')}
                        </div>
                      </TableHead>
                      <TableHead
                        className="text-right cursor-pointer hover:bg-muted/50"
                        onClick={() => handleSort('totalVatCollected')}
                      >
                        <div className="flex items-center justify-end">
                          Total VAT Collected
                          {getSortIcon('totalVatCollected')}
                        </div>
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedItems.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={9} className="h-24 text-center text-muted-foreground">
                          No data available
                        </TableCell>
                      </TableRow>
                    ) : (
                      <>
                        {paginatedItems.map((item) => (
                          <TableRow key={item.invoiceId}>
                            <TableCell className="font-medium">{item.invoiceNumber}</TableCell>
                            <TableCell>
                              {format(new Date(item.invoiceDate), 'dd/MM/yyyy')}
                            </TableCell>
                            <TableCell>{item.customerName || '-'}</TableCell>
                            <TableCell>{item.customerTaxCode || '-'}</TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {formatCurrency(item.revenue0pct)}
                            </TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {formatCurrency(item.revenue5pct)}
                            </TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {formatCurrency(item.revenue10pct)}
                            </TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {formatCurrency(item.revenueExempt)}
                            </TableCell>
                            <TableCell className="text-right font-mono font-semibold">
                              {formatCurrency(item.vatAmount)}
                            </TableCell>
                          </TableRow>
                        ))}
                        {/* Summary Totals Row */}
                        {report && filteredItems.length > 0 && (
                          <TableRow className="bg-muted/50 font-semibold">
                            <TableCell colSpan={4} className="text-right">
                              <strong>Summary Totals:</strong>
                            </TableCell>
                            <TableCell className="text-right font-mono">
                              {formatCurrency(report.revenue0pct)}
                            </TableCell>
                            <TableCell className="text-right font-mono">
                              {formatCurrency(report.revenue5pct)}
                            </TableCell>
                            <TableCell className="text-right font-mono">
                              {formatCurrency(report.revenue10pct)}
                            </TableCell>
                            <TableCell className="text-right font-mono">
                              {formatCurrency(report.revenueExempt)}
                            </TableCell>
                            <TableCell className="text-right font-mono">
                              {formatCurrency(report.totalVatCollected)}
                            </TableCell>
                          </TableRow>
                        )}
                      </>
                    )}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              {filteredItems.length > 0 && (
                <div className="flex items-center justify-between">
                  <div className="text-sm text-muted-foreground">
                    Showing {page * pageSize + 1} to{' '}
                    {Math.min((page + 1) * pageSize, filteredItems.length)} of{' '}
                    {filteredItems.length} records
                  </div>
                  <div className="flex items-center gap-2">
                    <Label htmlFor="page-size" className="text-sm">
                      Records per page:
                    </Label>
                    <Select
                      value={String(pageSize)}
                      onValueChange={(value) => {
                        setPageSize(Number(value))
                        setPage(0)
                      }}
                    >
                      <SelectTrigger id="page-size" className="w-20">
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
                    <div className="flex items-center gap-1">
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setPage(0)}
                        disabled={page === 0}
                      >
                        <span className="sr-only">First page</span>«
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={page === 0}
                      >
                        <span className="sr-only">Previous page</span>‹
                      </Button>
                      <span className="text-sm px-2">
                        Page {page + 1} of {totalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                      >
                        <span className="sr-only">Next page</span>›
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setPage(totalPages - 1)}
                        disabled={page >= totalPages - 1}
                      >
                        <span className="sr-only">Last page</span>»
                      </Button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Report History */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <History className="h-5 w-5" />
            Report History
          </CardTitle>
        </CardHeader>
        <CardContent>
          {historyLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : historyItems.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              No report history available
            </div>
          ) : (
            <div className="space-y-4">
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Generated Date</TableHead>
                      <TableHead>Period</TableHead>
                      <TableHead>Customer</TableHead>
                      <TableHead>Format</TableHead>
                      <TableHead>Generated By</TableHead>
                      <TableHead className="text-right">Downloads</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {historyItems.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell>
                          {format(new Date(item.generationDate), 'dd/MM/yyyy HH:mm')}
                        </TableCell>
                        <TableCell>
                          {format(new Date(item.startDate), 'dd/MM/yyyy')} –{' '}
                          {format(new Date(item.endDate), 'dd/MM/yyyy')}
                        </TableCell>
                        <TableCell>{item.supplierName || item.customerName || 'All'}</TableCell>
                        <TableCell>
                          <Badge variant="outline">{item.format}</Badge>
                        </TableCell>
                        <TableCell>{item.generatedByName || 'System'}</TableCell>
                        <TableCell className="text-right">{item.downloadCount || 0}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              {/* History Pagination */}
              {historyTotalPages > 1 && (
                <div className="flex items-center justify-between">
                  <div className="text-sm text-muted-foreground">
                    Showing {historyPage * historyPageSize + 1} to{' '}
                    {Math.min((historyPage + 1) * historyPageSize, historyTotalItems)} of{' '}
                    {historyTotalItems} records
                  </div>
                  <div className="flex items-center gap-2">
                    <Select
                      value={String(historyPageSize)}
                      onValueChange={(value) => {
                        setHistoryPageSize(Number(value))
                        setHistoryPage(0)
                      }}
                    >
                      <SelectTrigger className="w-20">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {HISTORY_PAGE_SIZE_OPTIONS.map((size) => (
                          <SelectItem key={size} value={String(size)}>
                            {size}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setHistoryPage(0)}
                        disabled={historyPage === 0}
                      >
                        «
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                        disabled={historyPage === 0}
                      >
                        ‹
                      </Button>
                      <span className="text-sm px-2">
                        Page {historyPage + 1} of {historyTotalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() =>
                          setHistoryPage((p) => Math.min(historyTotalPages - 1, p + 1))
                        }
                        disabled={historyPage >= historyTotalPages - 1}
                      >
                        ›
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setHistoryPage(historyTotalPages - 1)}
                        disabled={historyPage >= historyTotalPages - 1}
                      >
                        »
                      </Button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Generate Report Dialog */}
      <Dialog open={generateDialogOpen} onOpenChange={setGenerateDialogOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>Generate Output VAT Report</DialogTitle>
          </DialogHeader>
          <div className="grid gap-6 md:grid-cols-2">
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>Report Type</Label>
                <Input value="Output VAT" disabled />
              </div>
              <div className="space-y-2">
                <Label>Invoice Date Range *</Label>
                <DatePickerWithRange
                  value={formDateRange}
                  onChange={(v) =>
                    setFormDateRange(v as { from: Date | undefined; to: Date | undefined })
                  }
                />
              </div>
              <div className="space-y-2">
                <Label>Customer ID (optional)</Label>
                <Input
                  type="number"
                  placeholder="e.g. 1001"
                  value={formCustomerId}
                  onChange={(e) => setFormCustomerId(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label>VAT Class (optional)</Label>
                <Select value={formVatClass} onValueChange={(value: any) => setFormVatClass(value)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {VAT_CLASS_OPTIONS.map((option) => (
                      <SelectItem key={option} value={option}>
                        {option === 'ALL' ? 'All VAT Classes' : option}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button onClick={handleApplyFilters} className="w-full">
                Generate Report
              </Button>
            </div>
            <div className="space-y-4">
              <Label>Report Preview</Label>
              {report ? (
                <ScrollArea className="h-96 rounded border p-4">
                  <div className="space-y-2 text-sm">
                    <div>
                      <strong>Period:</strong> {format(new Date(report.startDate), 'dd/MM/yyyy')} –{' '}
                      {format(new Date(report.endDate), 'dd/MM/yyyy')}
                    </div>
                    <div>
                      <strong>Customer:</strong> {report.customerName || 'All Customers'}
                      {report.customerTaxCode && ` (${report.customerTaxCode})`}
                    </div>
                    <div>
                      <strong>Total VAT Collected:</strong>{' '}
                      {formatCurrency(report.totalVatCollected)}
                    </div>
                    <div className="pt-2">
                      <strong>Revenue by VAT Rate:</strong>
                      <div className="mt-1 space-y-1 pl-4">
                        <div>0%: {formatCurrency(report.revenue0pct)}</div>
                        <div>5%: {formatCurrency(report.revenue5pct)}</div>
                        <div>10%: {formatCurrency(report.revenue10pct)}</div>
                        <div>Exempt: {formatCurrency(report.revenueExempt)}</div>
                      </div>
                    </div>
                    <div className="pt-2">
                      <strong>Sample Items ({report.items.length} total):</strong>
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>Invoice</TableHead>
                            <TableHead>Date</TableHead>
                            <TableHead>Customer</TableHead>
                            <TableHead className="text-right">VAT</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {report.items.slice(0, 5).map((item) => (
                            <TableRow key={item.invoiceId}>
                              <TableCell className="font-medium text-xs">
                                {item.invoiceNumber}
                              </TableCell>
                              <TableCell className="text-xs">
                                {format(new Date(item.invoiceDate), 'dd/MM/yyyy')}
                              </TableCell>
                              <TableCell className="text-xs">{item.customerName || '-'}</TableCell>
                              <TableCell className="text-right text-xs font-mono">
                                {formatCurrency(item.vatAmount)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  </div>
                </ScrollArea>
              ) : (
                <div className="h-96 rounded border flex items-center justify-center text-muted-foreground text-sm">
                  Configure filters and click "Generate Report" to see preview
                </div>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setGenerateDialogOpen(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
