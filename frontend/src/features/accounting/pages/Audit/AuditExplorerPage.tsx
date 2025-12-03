import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Calendar as CalendarIcon,
  Download,
  FileText,
  RefreshCw,
  Search,
  Shield,
  ChevronDown,
  ChevronRight,
  FileJson,
  FileSpreadsheet,
  AlertTriangle,
  CheckCircle,
  XCircle,
} from 'lucide-react'
import { format, subMonths, differenceInMonths } from 'date-fns'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { ColumnDef, SortingState, ExpandedState } from '@tanstack/react-table'
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getExpandedRowModel,
  useReactTable,
} from '@tanstack/react-table'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Calendar } from '@/components/ui/calendar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import {
  queryAuditLogs,
  exportAuditLogs,
  downloadBlob,
  AUDIT_ACTION_TYPES,
  ACTION_TYPE_COLORS,
  type CashAuditLogDTO,
  type CashAuditQueryDTO,
  type ExportFormat,
} from '../../services/cashAudit'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
const MAX_DATE_RANGE_MONTHS = 12

// Map action types to badge variants
function getActionBadgeVariant(
  action: string,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  const color = ACTION_TYPE_COLORS[action]
  if (color === 'red') return 'destructive'
  if (color === 'green') return 'secondary'
  if (color === 'orange' || color === 'yellow') return 'default'
  return 'outline'
}

// Map action types to icons
function getActionIcon(action: string) {
  switch (action) {
    case 'PERIOD_BLOCK_ATTEMPT':
      return <AlertTriangle className="h-4 w-4 text-orange-500" />
    case 'INTEGRITY_CHECK_PASS':
      return <CheckCircle className="h-4 w-4 text-green-500" />
    case 'INTEGRITY_CHECK_FAIL':
      return <XCircle className="h-4 w-4 text-red-500" />
    case 'ANOMALY_DETECTED':
      return <AlertTriangle className="h-4 w-4 text-red-500" />
    default:
      return null
  }
}

export function AuditExplorerPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [items, setItems] = useState<CashAuditLogDTO[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Filters
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [dateFrom, setDateFrom] = useState<Date>(() => subMonths(new Date(), 1))
  const [dateTo, setDateTo] = useState<Date>(() => new Date())
  const [selectedActions, setSelectedActions] = useState<string[]>([])
  const [userIdFilter, setUserIdFilter] = useState<string>('')
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>('')
  const [sorting, setSorting] = useState<SortingState>([])
  const [expanded, setExpanded] = useState<ExpandedState>({})

  // Date range validation
  const dateRangeError = useMemo(() => {
    if (dateFrom && dateTo) {
      const months = differenceInMonths(dateTo, dateFrom)
      if (months > MAX_DATE_RANGE_MONTHS) {
        return t('cashAudit.errors.dateRangeExceeded', { max: MAX_DATE_RANGE_MONTHS })
      }
    }
    return null
  }, [dateFrom, dateTo, t])

  const loadData = useCallback(async () => {
    if (dateRangeError) {
      toast.error(dateRangeError)
      return
    }

    try {
      setLoading(true)
      const filter: CashAuditQueryDTO = {
        dateFrom: format(dateFrom, 'yyyy-MM-dd'),
        dateTo: format(dateTo, 'yyyy-MM-dd'),
        actionType: selectedActions.length > 0 ? selectedActions : undefined,
        userId: userIdFilter ? Number(userIdFilter) : undefined,
        entityType: entityTypeFilter || undefined,
        page,
        size: pageSize,
      }
      const response = await queryAuditLogs(filter)
      setItems(response.data)
      setTotalElements(response.meta.total)
      setTotalPages(Math.ceil(response.meta.total / pageSize))
    } catch (error) {
      toast.error(t('cashAudit.errors.loadFailed'))
      console.error(error)
    } finally {
      setLoading(false)
    }
  }, [
    dateFrom,
    dateTo,
    selectedActions,
    userIdFilter,
    entityTypeFilter,
    page,
    pageSize,
    dateRangeError,
    t,
  ])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleExport = async (format: ExportFormat) => {
    if (dateRangeError) {
      toast.error(dateRangeError)
      return
    }

    try {
      setExporting(true)
      const filter: CashAuditQueryDTO = {
        dateFrom: dateFrom.toISOString().split('T')[0],
        dateTo: dateTo.toISOString().split('T')[0],
        actionType: selectedActions.length > 0 ? selectedActions : undefined,
        userId: userIdFilter ? Number(userIdFilter) : undefined,
        entityType: entityTypeFilter || undefined,
      }
      const result = await exportAuditLogs(filter, format)
      downloadBlob(result.blob, result.filename)
      toast.success(t('cashAudit.export.success', { format }), {
        description: `SHA-256: ${result.hash.substring(0, 16)}...`,
      })
    } catch (error) {
      toast.error(t('cashAudit.export.failed'))
      console.error(error)
    } finally {
      setExporting(false)
    }
  }

  const handleActionToggle = (action: string) => {
    setSelectedActions((prev) =>
      prev.includes(action) ? prev.filter((a) => a !== action) : [...prev, action],
    )
    setPage(0)
  }

  const columns = useMemo<ColumnDef<CashAuditLogDTO>[]>(
    () => [
      {
        id: 'expander',
        header: () => null,
        cell: ({ row }) => (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => row.toggleExpanded()}
            className="h-6 w-6 p-0"
          >
            {row.getIsExpanded() ? (
              <ChevronDown className="h-4 w-4" />
            ) : (
              <ChevronRight className="h-4 w-4" />
            )}
          </Button>
        ),
      },
      {
        accessorKey: 'timestamp',
        header: t('cashAudit.explorer.columns.timestamp'),
        cell: ({ row }) => format(new Date(row.original.timestamp), 'dd/MM/yyyy HH:mm:ss'),
      },
      {
        accessorKey: 'action',
        header: t('cashAudit.explorer.columns.action'),
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            {getActionIcon(row.original.action)}
            <Badge
              variant={getActionBadgeVariant(row.original.action)}
              className="font-mono text-xs"
            >
              {row.original.action}
            </Badge>
          </div>
        ),
      },
      {
        accessorKey: 'userEmail',
        header: t('cashAudit.explorer.columns.user'),
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span className="font-medium">{row.original.userEmail || '-'}</span>
            {row.original.userId && (
              <span className="text-xs text-muted-foreground">ID: {row.original.userId}</span>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'entityType',
        header: t('cashAudit.explorer.columns.entity'),
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span>{row.original.entityType || '-'}</span>
            {row.original.entityId && (
              <span className="text-xs text-muted-foreground font-mono">
                {row.original.entityId.substring(0, 8)}...
              </span>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'metadata.ipAddress',
        header: t('cashAudit.explorer.columns.ipAddress'),
        cell: ({ row }) => row.original.metadata?.ipAddress || '-',
      },
    ],
    [t],
  )

  const table = useReactTable({
    data: items,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    onSortingChange: setSorting,
    onExpandedChange: setExpanded,
    getRowCanExpand: () => true,
    state: {
      sorting,
      expanded,
    },
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            {t('cashAudit.explorer.title')}
          </h1>
          <p className="text-muted-foreground">{t('cashAudit.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/accounting/audit/integrity')}>
            <Shield className="mr-2 h-4 w-4 text-blue-500" />
            {t('cashAudit.integrity.title')}
          </Button>
          <Button variant="outline" onClick={loadData} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            {t('common.refresh')}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button disabled={exporting || items.length === 0}>
                <Download className="mr-2 h-4 w-4" />
                {t('common.export')}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => handleExport('JSON')}>
                <FileJson className="mr-2 h-4 w-4" />
                {t('cashAudit.export.json')}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleExport('CSV')}>
                <FileSpreadsheet className="mr-2 h-4 w-4" />
                {t('cashAudit.export.csv')}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleExport('PDF')}>
                <FileText className="mr-2 h-4 w-4" />
                {t('cashAudit.export.pdf')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-lg">{t('common.filter')}</CardTitle>
          <CardDescription>{t('cashAudit.explorer.filterDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-4 items-end">
            {/* Date Range */}
            <div className="space-y-2">
              <Label>{t('cashAudit.explorer.filters.dateFrom')}</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    className={`w-[140px] justify-start text-left font-normal ${dateRangeError ? 'border-red-500' : ''}`}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {dateFrom
                      ? format(dateFrom, 'dd/MM/yyyy')
                      : t('cashAudit.explorer.filters.dateFrom')}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0">
                  <Calendar
                    mode="single"
                    selected={dateFrom}
                    onSelect={(date) => {
                      if (date) {
                        setDateFrom(date)
                        setPage(0)
                      }
                    }}
                    initialFocus
                  />
                </PopoverContent>
              </Popover>
            </div>
            <div className="space-y-2">
              <Label>{t('cashAudit.explorer.filters.dateTo')}</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    className={`w-[140px] justify-start text-left font-normal ${dateRangeError ? 'border-red-500' : ''}`}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {dateTo ? format(dateTo, 'dd/MM/yyyy') : t('cashAudit.explorer.filters.dateTo')}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0">
                  <Calendar
                    mode="single"
                    selected={dateTo}
                    onSelect={(date) => {
                      if (date) {
                        setDateTo(date)
                        setPage(0)
                      }
                    }}
                    initialFocus
                  />
                </PopoverContent>
              </Popover>
            </div>

            {/* User ID */}
            <div className="space-y-2">
              <Label>{t('cashAudit.explorer.filters.user')}</Label>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="User ID"
                  value={userIdFilter}
                  onChange={(e) => {
                    setUserIdFilter(e.target.value)
                    setPage(0)
                  }}
                  className="pl-8 w-[120px]"
                />
              </div>
            </div>

            {/* Entity Type */}
            <div className="space-y-2">
              <Label>{t('cashAudit.explorer.filters.entityType')}</Label>
              <Input
                placeholder="e.g. CashReceipt"
                value={entityTypeFilter}
                onChange={(e) => {
                  setEntityTypeFilter(e.target.value)
                  setPage(0)
                }}
                className="w-[150px]"
              />
            </div>

            {/* Reset */}
            <Button
              variant="ghost"
              onClick={() => {
                setDateFrom(subMonths(new Date(), 1))
                setDateTo(new Date())
                setSelectedActions([])
                setUserIdFilter('')
                setEntityTypeFilter('')
                setPage(0)
              }}
            >
              {t('common.reset')}
            </Button>
          </div>

          {/* Date range error */}
          {dateRangeError && <p className="text-sm text-red-500 mt-2">{dateRangeError}</p>}

          {/* Action Type Multi-select */}
          <div className="mt-4 space-y-2">
            <Label>{t('cashAudit.explorer.filters.actionType')}</Label>
            <div className="flex flex-wrap gap-2">
              {AUDIT_ACTION_TYPES.map((action) => (
                <div key={action} className="flex items-center space-x-2">
                  <Checkbox
                    id={`action-${action}`}
                    checked={selectedActions.includes(action)}
                    onCheckedChange={() => handleActionToggle(action)}
                  />
                  <Label htmlFor={`action-${action}`} className="text-xs cursor-pointer">
                    {action.replace(/_/g, ' ')}
                  </Label>
                </div>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

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
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 text-center">
                  <RefreshCw className="h-6 w-6 animate-spin mx-auto" />
                </TableCell>
              </TableRow>
            ) : items.length > 0 ? (
              table.getRowModel().rows.map((row) => (
                <>
                  <TableRow
                    key={row.id}
                    className={
                      row.original.action === 'PERIOD_BLOCK_ATTEMPT'
                        ? 'bg-orange-50 dark:bg-orange-950/20'
                        : row.original.action === 'INTEGRITY_CHECK_FAIL' ||
                            row.original.action === 'ANOMALY_DETECTED'
                          ? 'bg-red-50 dark:bg-red-950/20'
                          : ''
                    }
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                  {row.getIsExpanded() && (
                    <TableRow>
                      <TableCell colSpan={columns.length} className="bg-muted/50">
                        <div className="p-4">
                          <h4 className="font-semibold mb-2">
                            {t('cashAudit.explorer.columns.details')}
                          </h4>
                          <pre className="text-xs bg-background p-3 rounded border overflow-auto max-h-48">
                            {JSON.stringify(row.original.details || {}, null, 2)}
                          </pre>
                          {row.original.metadata && (
                            <>
                              <h4 className="font-semibold mt-4 mb-2">Metadata</h4>
                              <pre className="text-xs bg-background p-3 rounded border overflow-auto max-h-48">
                                {JSON.stringify(row.original.metadata, null, 2)}
                              </pre>
                            </>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )}
                </>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 text-center">
                  {t('common.noResults')}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between space-x-2 py-4">
        <div className="flex-1 text-sm text-muted-foreground">
          {t('common.showing')} {items.length} {t('common.of')} {totalElements} {t('common.items')}
        </div>
        <div className="flex items-center space-x-2">
          <p className="text-sm font-medium">{t('common.rowsPerPage')}</p>
          <Select
            value={`${pageSize}`}
            onValueChange={(value) => {
              setPageSize(Number(value))
              setPage(0)
            }}
          >
            <SelectTrigger className="h-8 w-[70px]">
              <SelectValue placeholder={pageSize} />
            </SelectTrigger>
            <SelectContent side="top">
              {PAGE_SIZE_OPTIONS.map((size) => (
                <SelectItem key={size} value={`${size}`}>
                  {size}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-x-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            {t('common.previous')}
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {Math.max(1, totalPages)}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
          >
            {t('common.next')}
          </Button>
        </div>
      </div>
    </div>
  )
}
