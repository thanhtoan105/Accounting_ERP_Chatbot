'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Calendar,
  RefreshCw,
  Download,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Search,
  ShieldCheck,
} from 'lucide-react'
import { format } from 'date-fns'
import type { ColumnDef } from '@tanstack/react-table'
import { useReactTable, getCoreRowModel, flexRender } from '@tanstack/react-table'
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { getAuditLogs, exportAuditLogs } from '@/features/audit/services/audit'
import type { AuditLogItem, AuditLogQueryParams } from '@/types/audit'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const ENTITY_OPTIONS = [
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'SUPPLIER', label: 'Supplier' },
  { value: 'CHART_OF_ACCOUNT', label: 'Chart Of Account' },
  { value: 'BANK_ACCOUNT', label: 'Bank Account' },
  { value: 'COMPANY', label: 'Company Settings' },
  { value: 'DATA_INTEGRITY_JOB', label: 'Data Integrity Job' },
  { value: 'AUDIT_LOG', label: 'Audit Log' },
]

const EVENT_TYPE_OPTIONS = [
  { value: 'MASTER_DATA', label: 'Master Data' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'COMPLIANCE', label: 'Compliance' },
  { value: 'GENERAL', label: 'General' },
]

const ROLE_OPTIONS = [
  { value: 'admin', label: 'Admin' },
  { value: 'accountant', label: 'Accountant' },
  { value: 'chief_accountant', label: 'Chief Accountant' },
  { value: 'cfo', label: 'CFO' },
]

function formatDate(value: string | null) {
  if (!value) return '-'
  try {
    return format(new Date(value), 'yyyy-MM-dd HH:mm:ss')
  } catch {
    return value
  }
}

function toIsoDate(date: string | null, endOfDay = false) {
  if (!date) return undefined
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return undefined
  if (endOfDay) {
    d.setHours(23, 59, 59, 999)
  } else {
    d.setHours(0, 0, 0, 0)
  }
  return d.toISOString()
}

function prettyJson(data: Record<string, unknown>) {
  if (!data || Object.keys(data).length === 0) return '—'
  return JSON.stringify(data, null, 2)
}

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLogItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [entityType, setEntityType] = useState<string>('all')
  const [eventType, setEventType] = useState<string>('all')
  const [actorRole, setActorRole] = useState<string>('all')
  const [successFilter, setSuccessFilter] = useState<'all' | 'success' | 'failure'>('all')
  const [userEmail, setUserEmail] = useState<string>('')
  const [actionSearch, setActionSearch] = useState<string>('')
  const [entityId, setEntityId] = useState<string>('')
  const [fromDate, setFromDate] = useState<string>('')
  const [toDate, setToDate] = useState<string>('')

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const loadAuditLogs = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const params: AuditLogQueryParams = {
        page,
        size: pageSize,
        entityType: entityType !== 'all' ? entityType : undefined,
        eventType: eventType !== 'all' ? eventType : undefined,
        actorRole: actorRole !== 'all' ? actorRole : undefined,
        success: successFilter === 'all' ? undefined : successFilter === 'success',
        userEmail: userEmail.trim() || undefined,
        action: actionSearch.trim() || undefined,
        entityId: entityId.trim() || undefined,
        from: toIsoDate(fromDate || null),
        to: toIsoDate(toDate || null, true),
      }
      const response = await getAuditLogs(params)
      setLogs(response.data)
      setTotalElements(response.meta.totalElements)
      setTotalPages(response.meta.totalPages)
      setPage(response.meta.page + 1)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load audit logs'
      setError(message)
      toast.error('Failed to load audit logs', { description: message })
    } finally {
      setLoading(false)
    }
  }, [
    page,
    pageSize,
    entityType,
    eventType,
    actorRole,
    successFilter,
    userEmail,
    actionSearch,
    entityId,
    fromDate,
    toDate,
  ])

  useEffect(() => {
    loadAuditLogs()
  }, [loadAuditLogs])

  const handleRefresh = async () => {
    await loadAuditLogs()
    toast.success('Audit logs refreshed')
  }

  const handleExport = async () => {
    try {
      const params: AuditLogQueryParams = {
        entityType: entityType !== 'all' ? entityType : undefined,
        eventType: eventType !== 'all' ? eventType : undefined,
        actorRole: actorRole !== 'all' ? actorRole : undefined,
        success: successFilter === 'all' ? undefined : successFilter === 'success',
        userEmail: userEmail.trim() || undefined,
        action: actionSearch.trim() || undefined,
        entityId: entityId.trim() || undefined,
        from: toIsoDate(fromDate || null),
        to: toIsoDate(toDate || null, true),
      }
      const blob = await exportAuditLogs(params)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `audit-logs-${format(new Date(), 'yyyyMMdd-HHmmss')}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      toast.success('Audit log export started')
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Export failed'
      toast.error('Failed to export audit logs', { description: message })
    }
  }

  const columns = useMemo<ColumnDef<AuditLogItem>[]>(
    () => [
      {
        accessorKey: 'occurredAt',
        header: 'Timestamp',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span>{formatDate(row.original.occurredAt)}</span>
            <span className="text-xs text-muted-foreground">{row.original.traceId || '—'}</span>
          </div>
        ),
      },
      {
        accessorKey: 'entity',
        header: 'Entity',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span className="font-medium">{row.original.entityType || '—'}</span>
            <span className="text-xs text-muted-foreground">
              {row.original.entityDisplay || row.original.entityId || '—'}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'action',
        header: 'Action',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span>{row.original.action || '—'}</span>
            <span className="text-xs text-muted-foreground">{row.original.eventType || '—'}</span>
          </div>
        ),
      },
      {
        accessorKey: 'actor',
        header: 'Actor',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span>{row.original.actor?.email || '—'}</span>
            <span className="text-xs text-muted-foreground">{row.original.actor?.role || '—'}</span>
          </div>
        ),
      },
      {
        accessorKey: 'success',
        header: 'Outcome',
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <Badge variant={row.original.success ? 'default' : 'destructive'}>
              {row.original.success ? 'Success' : 'Failure'}
            </Badge>
            {row.original.failureReason && (
              <span className="text-xs text-muted-foreground">{row.original.failureReason}</span>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'ipAddress',
        header: 'Network',
        cell: ({ row }) => (
          <div className="flex flex-col text-xs text-muted-foreground">
            <span>{row.original.ipAddress || '—'}</span>
            <span className="line-clamp-2">{row.original.userAgent || '—'}</span>
          </div>
        ),
      },
      {
        id: 'details',
        header: 'Details',
        cell: ({ row }) => (
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="outline" size="sm">
                View
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>Audit Entry Details</DialogTitle>
              </DialogHeader>
              <ScrollArea className="max-h-[70vh]">
                <div className="space-y-4">
                  <section>
                    <h3 className="font-semibold text-sm mb-1">Changes</h3>
                    <pre className="rounded bg-muted p-3 text-xs">
                      {prettyJson(row.original.changes)}
                    </pre>
                  </section>
                  <section>
                    <h3 className="font-semibold text-sm mb-1">Metadata</h3>
                    <pre className="rounded bg-muted p-3 text-xs">
                      {prettyJson(row.original.metadata)}
                    </pre>
                  </section>
                </div>
              </ScrollArea>
            </DialogContent>
          </Dialog>
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    data: logs,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  const isEmpty = !loading && logs.length === 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <ShieldCheck className="h-6 w-6 text-primary" />
            Audit Logs
          </h1>
          <p className="text-muted-foreground">
            Monitor master data changes, security events, and integrity scans.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button variant="secondary" onClick={handleExport}>
            <Download className="mr-2 h-4 w-4" />
            Export CSV
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">User Email</label>
          <div className="relative">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="user@example.com"
              value={userEmail}
              onChange={(e) => {
                setUserEmail(e.target.value)
                setPage(1)
              }}
              className="pl-8"
            />
          </div>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Action Keyword</label>
          <Input
            placeholder="LOGIN_SUCCESS, CUSTOMER_UPDATED..."
            value={actionSearch}
            onChange={(e) => {
              setActionSearch(e.target.value)
              setPage(1)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Entity Type</label>
          <Select
            value={entityType}
            onValueChange={(value) => {
              setEntityType(value)
              setPage(1)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All entities</SelectItem>
              {ENTITY_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Entity ID</label>
          <Input
            placeholder="UUID or numeric id"
            value={entityId}
            onChange={(e) => {
              setEntityId(e.target.value)
              setPage(1)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Event Type</label>
          <Select
            value={eventType}
            onValueChange={(value) => {
              setEventType(value)
              setPage(1)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All events" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All events</SelectItem>
              {EVENT_TYPE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Actor Role</label>
          <Select
            value={actorRole}
            onValueChange={(value) => {
              setActorRole(value)
              setPage(1)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All roles" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All roles</SelectItem>
              {ROLE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Outcome</label>
          <Select
            value={successFilter}
            onValueChange={(value: 'all' | 'success' | 'failure') => {
              setSuccessFilter(value)
              setPage(1)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All outcomes" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="success">Success</SelectItem>
              <SelectItem value="failure">Failure</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            Date Range
          </label>
          <div className="grid grid-cols-2 gap-2">
            <DatePicker
              value={fromDate}
              onChange={(value) => {
                setFromDate(value)
                setPage(1)
              }}
              placeholder="Select start date"
            />
            <DatePicker
              value={toDate}
              onChange={(value) => {
                setToDate(value)
                setPage(1)
              }}
              placeholder="Select end date"
            />
          </div>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder ? null : (header.column.columnDef.header as string)}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, index) => (
                <TableRow key={`skeleton-${index}`}>
                  {columns.map((column, colIndex) => (
                    <TableCell key={`skeleton-${column.id ?? colIndex}-${index}`}>
                      <Skeleton className="h-10 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : isEmpty ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-center text-muted-foreground">
                  No audit entries match the selected filters.
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

      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="text-sm text-muted-foreground">
          Showing <span className="font-medium text-foreground">{logs.length}</span> of{' '}
          <span className="font-medium text-foreground">{totalElements}</span> records
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">Rows per page</span>
            <Select
              value={pageSize.toString()}
              onValueChange={(value) => {
                setPageSize(Number(value))
                setPage(1)
              }}
            >
              <SelectTrigger className="w-24">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZE_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option.toString()}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon"
              disabled={page <= 1 || loading}
              onClick={() => setPage(1)}
            >
              <ChevronsLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              disabled={page <= 1 || loading}
              onClick={() => setPage((prev) => Math.max(prev - 1, 1))}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-sm text-muted-foreground">
              Page <span className="font-semibold text-foreground">{page}</span> of{' '}
              <span className="font-semibold text-foreground">{Math.max(totalPages, 1)}</span>
            </span>
            <Button
              variant="outline"
              size="icon"
              disabled={page >= totalPages || loading}
              onClick={() => setPage((prev) => Math.min(prev + 1, Math.max(totalPages, 1)))}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              disabled={page >= totalPages || loading}
              onClick={() => setPage(Math.max(totalPages, 1))}
            >
              <ChevronsRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
