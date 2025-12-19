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
  InputVATReportDTO,
  InputVATReportRequest,
  VATExportFormat,
  VATReportHistoryItem,
} from '@/types/vat'
import { cn } from '@/lib/utils'
import { GenerateVATReportDialog } from './GenerateVATReportDialog'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const VAT_CLASS_OPTIONS = ['ALL', 'ZERO', 'FIVE', 'TEN', 'EXEMPT'] as const
const HISTORY_PAGE_SIZE_OPTIONS = [10, 20, 30, 50]

export function VATReportList() {
  const [loading, setLoading] = useState(false)
  const [report, setReport] = useState<InputVATReportDTO | null>(null)

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
  const [formSupplierId, setFormSupplierId] = useState('')

  const [appliedFilters, setAppliedFilters] = useState<InputVATReportRequest>({})
  const [generateDialogOpen, setGenerateDialogOpen] = useState(false)

  // History state
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyItems, setHistoryItems] = useState<VATReportHistoryItem[]>([])
  const [historyPage, setHistoryPage] = useState(0)
  const [historyPageSize, setHistoryPageSize] = useState(20)
  const [historyTotalItems, setHistoryTotalItems] = useState(0)
  const [historyTotalPages, setHistoryTotalPages] = useState(1)
  const [historySortColumn, setHistorySortColumn] = useState<string | null>(null)
  const [historySortDirection, setHistorySortDirection] = useState<'asc' | 'desc'>('asc')
  const [historyFilter, setHistoryFilter] = useState<{
    reportType: 'ALL' | 'INPUT_VAT' | 'OUTPUT_VAT'
    supplierId: string
    vatClass: string
    dateRange: { from?: Date; to?: Date }
  }>({
    reportType: 'ALL',
    supplierId: '',
    vatClass: 'ALL',
    dateRange: {},
  })

  const loadReport = useCallback(async () => {
    try {
      setLoading(true)
      const data = await vatService.generateInputReport(appliedFilters)
      setReport(data)
      setPage(0)
    } catch (error) {
      toast.error(`Failed to load VAT report: ${String(error)}`)
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
        supplierId?: number
        vatClass?: string
        startDate?: string
        endDate?: string
      } = {
        page: historyPage,
        size: historyPageSize,
      }
      if (historyFilter.reportType !== 'ALL') {
        params.reportType = historyFilter.reportType
      }
      if (historyFilter.supplierId) {
        const numericSupplier = Number(historyFilter.supplierId)
        if (Number.isNaN(numericSupplier)) {
          toast.error('Supplier ID must be numeric')
          return
        }
        params.supplierId = numericSupplier
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
    if (formSupplierId && Number.isNaN(Number(formSupplierId))) {
      toast.error('Supplier ID must be numeric')
      return
    }

    const nextFilters: InputVATReportRequest = {}
    if (formDateRange.from) {
      nextFilters.startDate = format(formDateRange.from, 'yyyy-MM-dd')
    }
    if (formDateRange.to) {
      nextFilters.endDate = format(formDateRange.to, 'yyyy-MM-dd')
    }
    if (formVatClass !== 'ALL') {
      nextFilters.vatClass = formVatClass
    }
    if (formSupplierId) {
      nextFilters.supplierId = Number(formSupplierId)
    }

    setAppliedFilters(nextFilters)
  }

  const handleClearFilters = () => {
    setFormDateRange({ from: undefined, to: undefined })
    setFormVatClass('ALL')
    setFormSupplierId('')
    setAppliedFilters({})
  }

  const handleExport = async (format: VATExportFormat) => {
    if (!report) return
    try {
      const blob = await vatService.exportInputReport(report.reportId, format)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `input-vat-report-${report.reportId}.${format === 'EXCEL' ? 'xlsx' : 'pdf'}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success(`VAT report exported as ${format}`)
    } catch (error) {
      toast.error(`Failed to export VAT report: ${String(error)}`)
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

  const handleHistorySort = (column: string) => {
    if (historySortColumn === column) {
      setHistorySortDirection(historySortDirection === 'asc' ? 'desc' : 'asc')
    } else {
      setHistorySortColumn(column)
      setHistorySortDirection('asc')
    }
    setHistoryPage(0)
  }

  const getHistorySortIcon = (column: string) => {
    if (historySortColumn !== column) {
      return <ArrowUpDown className="ml-2 h-4 w-4" />
    }
    return historySortDirection === 'asc' ? (
      <ArrowUp className="ml-2 h-4 w-4" />
    ) : (
      <ArrowDown className="ml-2 h-4 w-4" />
    )
  }

  const sortedHistoryItems = useMemo(() => {
    if (!historySortColumn) return historyItems

    return [...historyItems].sort((a, b) => {
      let aVal: string | number | Date
      let bVal: string | number | Date

      switch (historySortColumn) {
        case 'type':
          aVal = a.reportType
          bVal = b.reportType
          break
        case 'period':
          aVal = new Date(a.startDate).getTime()
          bVal = new Date(b.startDate).getTime()
          break
        case 'supplier':
          aVal = (a.supplierName || '') + (a.supplierCode || '')
          bVal = (b.supplierName || '') + (b.supplierCode || '')
          break
        case 'generated':
          aVal = new Date(a.generationDate).getTime()
          bVal = new Date(b.generationDate).getTime()
          break
        case 'format':
          aVal = a.format
          bVal = b.format
          break
        case 'downloads':
          aVal = a.downloadCount ?? 0
          bVal = b.downloadCount ?? 0
          break
        default:
          return 0
      }

      if (aVal < bVal) return historySortDirection === 'asc' ? -1 : 1
      if (aVal > bVal) return historySortDirection === 'asc' ? 1 : -1
      return 0
    })
  }, [historyItems, historySortColumn, historySortDirection])

  const parseAmount = (amount: string): number => {
    // Remove currency symbols and commas, parse as number
    return parseFloat(amount.replace(/[₫,\s]/g, '')) || 0
  }

  const filteredItems = useMemo(() => {
    if (!report) return []
    const query = searchTerm.trim().toLowerCase()
    const base = report.items
    const searched = !query
      ? base
      : base.filter((item) => {
          const supplier = (item.supplierName || '') + ' ' + (item.supplierCode || '')
          const bill = (item.billNumber || '') + ' ' + format(new Date(item.billDate), 'dd/MM/yyyy')
          return supplier.toLowerCase().includes(query) || bill.toLowerCase().includes(query)
        })

    // Apply sorting
    if (sortColumn) {
      searched.sort((a, b) => {
        let aVal: string | number | Date
        let bVal: string | number | Date

        switch (sortColumn) {
          case 'supplier':
            aVal = (a.supplierName || '') + (a.supplierCode || '')
            bVal = (b.supplierName || '') + (b.supplierCode || '')
            break
          case 'bill':
            aVal = a.billNumber || a.billId
            bVal = b.billNumber || b.billId
            break
          case 'billDate':
            aVal = new Date(a.billDate).getTime()
            bVal = new Date(b.billDate).getTime()
            break
          case 'vatRate':
            aVal = a.vatRate
            bVal = b.vatRate
            break
          case 'baseAmount':
            aVal = parseAmount(a.baseAmount)
            bVal = parseAmount(b.baseAmount)
            break
          case 'vatAmount':
            aVal = parseAmount(a.vatAmount)
            bVal = parseAmount(b.vatAmount)
            break
          case 'totalAmount':
            aVal = parseAmount(a.totalAmount)
            bVal = parseAmount(b.totalAmount)
            break
          default:
            return 0
        }

        if (aVal < bVal) return sortDirection === 'asc' ? -1 : 1
        if (aVal > bVal) return sortDirection === 'asc' ? 1 : -1
        return 0
      })
    }

    const start = page * pageSize
    const end = start + pageSize
    return searched.slice(start, end)
  }, [report, searchTerm, page, pageSize, sortColumn, sortDirection])

  const totalItems = report?.items.length ?? 0
  const totalPages = Math.ceil(totalItems / pageSize) || 1
  const appliedFilterChips = useMemo(() => {
    const chips: string[] = []
    if (appliedFilters.startDate || appliedFilters.endDate) {
      const start = appliedFilters.startDate
        ? format(new Date(appliedFilters.startDate), 'dd/MM/yyyy')
        : '…'
      const end = appliedFilters.endDate
        ? format(new Date(appliedFilters.endDate), 'dd/MM/yyyy')
        : '…'
      chips.push(`Bill Date: ${start} → ${end}`)
    }
    if (appliedFilters.vatClass) {
      chips.push(`VAT Class: ${appliedFilters.vatClass}`)
    }
    if (appliedFilters.supplierId !== undefined) {
      chips.push(`Supplier ID: ${appliedFilters.supplierId}`)
    }
    return chips
  }, [appliedFilters])

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Input VAT Report
          </h1>
          <p className="text-muted-foreground">
            Analyze input VAT by supplier, bill, and VAT rate with ND123-ready data.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button onClick={() => setGenerateDialogOpen(true)}>Generate Report</Button>
          <Button
            variant="outline"
            onClick={handleRefresh}
            disabled={loading}
            className="whitespace-nowrap"
          >
            <RefreshCw className={cn('mr-2 h-4 w-4', loading && 'animate-spin')} />
            Refresh
          </Button>
          <Button
            variant="outline"
            onClick={() => handleExport('EXCEL')}
            disabled={!report}
            className="whitespace-nowrap"
          >
            <Download className="mr-2 h-4 w-4" />
            Export Excel
          </Button>
          <Button
            variant="outline"
            onClick={() => handleExport('PDF')}
            disabled={!report}
            className="whitespace-nowrap"
          >
            <Download className="mr-2 h-4 w-4" />
            Export PDF
          </Button>
        </div>
      </div>

      <GenerateVATReportDialog
        open={generateDialogOpen}
        onOpenChange={setGenerateDialogOpen}
        onReportGenerated={(nextReport, request) => {
          setReport(nextReport)
          setAppliedFilters(request)
          setGenerateDialogOpen(false)
          toast.success('Input VAT report generated')
        }}
      />

      {/* Filters row */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-2">
          <div className="relative w-full max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search by supplier, code, or bill..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value)
                setPage(0)
              }}
              className="pl-9"
            />
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground flex items-center gap-1">
            <SlidersHorizontal className="h-4 w-4" />
            Filters:
          </span>
          <DatePickerWithRange
            value={formDateRange}
            onChange={(v) =>
              setFormDateRange(v as { from: Date | undefined; to: Date | undefined })
            }
          />
          <select
            value={formVatClass}
            onChange={(e) => setFormVatClass(e.target.value as (typeof VAT_CLASS_OPTIONS)[number])}
            className="rounded-md border bg-background px-3 py-2 text-sm"
          >
            {VAT_CLASS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option === 'ALL' ? 'All VAT classes' : option}
              </option>
            ))}
          </select>
          <Input
            placeholder="Supplier ID"
            value={formSupplierId}
            onChange={(e) => setFormSupplierId(e.target.value)}
            className="w-32"
          />
          <Button variant="secondary" onClick={handleApplyFilters} disabled={loading}>
            Apply
          </Button>
          <Button variant="ghost" onClick={handleClearFilters} disabled={loading}>
            Clear
          </Button>
        </div>
      </div>

      {appliedFilterChips.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {appliedFilterChips.map((chip) => (
            <Badge key={chip} variant="secondary" className="flex items-center gap-1">
              {chip}
              <X className="h-3 w-3 cursor-pointer" onClick={handleClearFilters} />
            </Badge>
          ))}
        </div>
      )}

      {/* Metadata cards */}
      {report && (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader>
              <CardTitle>Report Period</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {format(new Date(report.startDate), 'dd/MM/yyyy')} –{' '}
              {format(new Date(report.endDate), 'dd/MM/yyyy')}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Supplier Scope</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {report.supplierName
                ? `${report.supplierName}${report.supplierCode ? ` (${report.supplierCode})` : ''}`
                : 'All suppliers'}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Generated</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {format(new Date(report.generationDate), 'dd/MM/yyyy HH:mm')}
              <div className="text-xs text-foreground">{report.generatedByName ?? 'System'}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Totals</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground space-y-1">
              <div>
                VAT: <span className="font-mono text-foreground">{report.grandTotalVAT}</span>
              </div>
              <div>
                Amount: <span className="font-mono text-foreground">{report.grandTotalAmount}</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>
                <button
                  onClick={() => handleSort('supplier')}
                  className="flex items-center hover:text-foreground"
                >
                  Supplier
                  {getSortIcon('supplier')}
                </button>
              </TableHead>
              <TableHead>
                <button
                  onClick={() => handleSort('bill')}
                  className="flex items-center hover:text-foreground"
                >
                  Bill
                  {getSortIcon('bill')}
                </button>
              </TableHead>
              <TableHead>
                <button
                  onClick={() => handleSort('vatRate')}
                  className="flex items-center hover:text-foreground"
                >
                  VAT Rate
                  {getSortIcon('vatRate')}
                </button>
              </TableHead>
              <TableHead className="text-right">
                <button
                  onClick={() => handleSort('baseAmount')}
                  className="flex items-center justify-end ml-auto hover:text-foreground"
                >
                  Base Amount
                  {getSortIcon('baseAmount')}
                </button>
              </TableHead>
              <TableHead className="text-right">
                <button
                  onClick={() => handleSort('vatAmount')}
                  className="flex items-center justify-end ml-auto hover:text-foreground"
                >
                  VAT Amount
                  {getSortIcon('vatAmount')}
                </button>
              </TableHead>
              <TableHead className="text-right">
                <button
                  onClick={() => handleSort('totalAmount')}
                  className="flex items-center justify-end ml-auto hover:text-foreground"
                >
                  Total Amount
                  {getSortIcon('totalAmount')}
                </button>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-[200px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[160px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[80px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                </TableRow>
              ))
            ) : !report || report.items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                  No input VAT data found for the selected period.
                </TableCell>
              </TableRow>
            ) : filteredItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                  No rows match your search.
                </TableCell>
              </TableRow>
            ) : (
              filteredItems.map((item) => (
                <TableRow key={`${item.billId}-${item.billNumber}-${item.vatRate}`}>
                  <TableCell>
                    <div className="space-y-0.5">
                      <div className="font-medium">{item.supplierName ?? '—'}</div>
                      {item.supplierCode && (
                        <div className="text-xs text-muted-foreground">{item.supplierCode}</div>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="space-y-0.5 text-sm">
                      <div className="font-mono">
                        {item.billNumber ?? 'Bill #' + item.billId.slice(0, 8)}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {format(new Date(item.billDate), 'dd/MM/yyyy')}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{item.vatRate}</Badge>
                  </TableCell>
                  <TableCell className="text-right font-mono text-sm">{item.baseAmount}</TableCell>
                  <TableCell className="text-right font-mono text-sm">{item.vatAmount}</TableCell>
                  <TableCell className="text-right font-mono text-sm">{item.totalAmount}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Summary + pagination */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="space-y-1 text-sm text-muted-foreground">
          {report && (
            <>
              <div>
                Showing {filteredItems.length} of {totalItems} lines
              </div>
              <div className="flex flex-wrap gap-2">
                <span className="font-medium text-foreground">Totals:</span>
                {(['ZERO', 'FIVE', 'TEN', 'EXEMPT'] as const).map((rate) => (
                  <span key={rate}>
                    <span className="uppercase">{rate}</span> VAT:{' '}
                    <span className="font-mono">{report.totalVATByRate?.[rate] ?? '0'}</span>
                  </span>
                ))}
                <span>
                  Grand VAT: <span className="font-mono">{report.grandTotalVAT}</span>
                </span>
                <span>
                  Grand Amount: <span className="font-mono">{report.grandTotalAmount}</span>
                </span>
              </div>
            </>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-muted-foreground">Rows per page:</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value))
                setPage(0)
              }}
              className="rounded border px-2 py-1 text-sm bg-background"
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              Previous
            </Button>
            <span className="text-muted-foreground">
              Page {totalItems === 0 ? 0 : page + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || totalItems === 0}
            >
              Next
            </Button>
          </div>
        </div>
      </div>

      <Card>
        <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <History className="h-5 w-5 text-primary" />
              Report History
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Browse previously generated VAT reports and re-download files when needed.
            </p>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-2">
              <Label>Report Type</Label>
              <select
                value={historyFilter.reportType}
                onChange={(e) =>
                  setHistoryFilter((prev) => ({ ...prev, reportType: e.target.value as any }))
                }
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
              >
                <option value="ALL">All types</option>
                <option value="INPUT_VAT">Input VAT</option>
                <option value="OUTPUT_VAT">Output VAT</option>
              </select>
            </div>
            <div className="space-y-2">
              <Label>Supplier ID</Label>
              <Input
                placeholder="Optional supplier ID"
                value={historyFilter.supplierId}
                onChange={(e) =>
                  setHistoryFilter((prev) => ({ ...prev, supplierId: e.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label>VAT Class</Label>
              <select
                value={historyFilter.vatClass}
                onChange={(e) =>
                  setHistoryFilter((prev) => ({ ...prev, vatClass: e.target.value }))
                }
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
              >
                {VAT_CLASS_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option === 'ALL' ? 'All classes' : option}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label>Generated Date</Label>
              <DatePickerWithRange
                value={historyFilter.dateRange}
                onChange={(range) =>
                  setHistoryFilter((prev) => ({ ...prev, dateRange: range ?? {} }))
                }
              />
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => loadHistory()} disabled={historyLoading}>
              Apply Filters
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setHistoryFilter({
                  reportType: 'ALL',
                  supplierId: '',
                  vatClass: 'ALL',
                  dateRange: {},
                })
                setHistoryPage(0)
              }}
            >
              Clear
            </Button>
          </div>

          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>
                    <button
                      onClick={() => handleHistorySort('type')}
                      className="flex items-center hover:text-foreground"
                    >
                      Type
                      {getHistorySortIcon('type')}
                    </button>
                  </TableHead>
                  <TableHead>
                    <button
                      onClick={() => handleHistorySort('period')}
                      className="flex items-center hover:text-foreground"
                    >
                      Period
                      {getHistorySortIcon('period')}
                    </button>
                  </TableHead>
                  <TableHead>
                    <button
                      onClick={() => handleHistorySort('supplier')}
                      className="flex items-center hover:text-foreground"
                    >
                      Supplier
                      {getHistorySortIcon('supplier')}
                    </button>
                  </TableHead>
                  <TableHead>
                    <button
                      onClick={() => handleHistorySort('generated')}
                      className="flex items-center hover:text-foreground"
                    >
                      Generated
                      {getHistorySortIcon('generated')}
                    </button>
                  </TableHead>
                  <TableHead>
                    <button
                      onClick={() => handleHistorySort('format')}
                      className="flex items-center hover:text-foreground"
                    >
                      Format
                      {getHistorySortIcon('format')}
                    </button>
                  </TableHead>
                  <TableHead>
                    <button
                      onClick={() => handleHistorySort('downloads')}
                      className="flex items-center hover:text-foreground"
                    >
                      Downloads
                      {getHistorySortIcon('downloads')}
                    </button>
                  </TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {historyLoading ? (
                  Array.from({ length: 5 }).map((_, idx) => (
                    <TableRow key={idx}>
                      <TableCell colSpan={7}>
                        <Skeleton className="h-4 w-full" />
                      </TableCell>
                    </TableRow>
                  ))
                ) : sortedHistoryItems.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                      No report history found.
                    </TableCell>
                  </TableRow>
                ) : (
                  sortedHistoryItems.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <Badge variant="outline">{item.reportType}</Badge>
                      </TableCell>
                      <TableCell className="text-sm">
                        {format(new Date(item.startDate), 'dd/MM/yyyy')} –{' '}
                        {format(new Date(item.endDate), 'dd/MM/yyyy')}
                      </TableCell>
                      <TableCell className="text-sm">
                        {item.supplierName
                          ? `${item.supplierName}${
                              item.supplierCode ? ` (${item.supplierCode})` : ''
                            }`
                          : 'All suppliers'}
                      </TableCell>
                      <TableCell className="text-sm">
                        <div>{format(new Date(item.generationDate), 'dd/MM/yyyy HH:mm')}</div>
                        <div className="text-xs text-muted-foreground">
                          {item.generatedByName ?? 'System'}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary">{item.format}</Badge>
                      </TableCell>
                      <TableCell className="text-center text-sm">
                        {item.downloadCount ?? 0}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleExport(item.format)}
                        >
                          <Download className="mr-2 h-4 w-4" />
                          Download
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div className="text-sm text-muted-foreground">
              Showing {sortedHistoryItems.length} of {historyTotalItems} reports
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted-foreground">Rows per page:</span>
                <select
                  value={historyPageSize}
                  onChange={(e) => {
                    setHistoryPageSize(Number(e.target.value))
                    setHistoryPage(0)
                  }}
                  className="rounded border bg-background px-2 py-1 text-sm"
                >
                  {HISTORY_PAGE_SIZE_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                  disabled={historyPage === 0}
                >
                  Previous
                </Button>
                <span className="text-muted-foreground">
                  Page {historyTotalItems === 0 ? 0 : historyPage + 1} of {historyTotalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setHistoryPage((p) => Math.min(historyTotalPages - 1, p + 1))}
                  disabled={historyPage >= historyTotalPages - 1 || historyTotalItems === 0}
                >
                  Next
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
