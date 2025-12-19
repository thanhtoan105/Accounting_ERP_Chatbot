import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Calendar as CalendarIcon,
  Download,
  FileText,
  MoreVertical,
  RefreshCw,
  Search,
  ShieldAlert,
  Archive,
} from 'lucide-react'
import { format } from 'date-fns'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Calendar } from '@/components/ui/calendar'
import { apAuditService } from '@/services/apAudit'
import type { APAuditTimelineDTO } from '@/types/apAudit'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

export function APAuditTimeline() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [items, setItems] = useState<APAuditTimelineDTO[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Filters
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [actionFilter, setActionFilter] = useState('')
  const [userIdFilter, setUserIdFilter] = useState<string>('')
  const [startDate, setStartDate] = useState<Date | undefined>()
  const [endDate, setEndDate] = useState<Date | undefined>()
  const [sorting, setSorting] = useState<SortingState>([])

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apAuditService.getTimeline(
        {
          action: actionFilter || undefined,
          userId: userIdFilter ? Number(userIdFilter) : undefined,
          startDate: startDate ? format(startDate, 'yyyy-MM-dd') : undefined,
          endDate: endDate ? format(endDate, 'yyyy-MM-dd') : undefined,
        },
        page,
        pageSize,
      )
      setItems(response.content)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
    } catch (error) {
      toast.error('Failed to load audit timeline')
      console.error(error)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, actionFilter, userIdFilter, startDate, endDate])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleExport = async () => {
    try {
      const blob = await apAuditService.exportTimeline({
        action: actionFilter || undefined,
        userId: userIdFilter ? Number(userIdFilter) : undefined,
        startDate: startDate ? format(startDate, 'yyyy-MM-dd') : undefined,
        endDate: endDate ? format(endDate, 'yyyy-MM-dd') : undefined,
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `audit-timeline-${format(new Date(), 'yyyy-MM-dd')}.pdf`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('Export downloaded')
    } catch (error) {
      toast.error('Failed to export timeline')
    }
  }

  const columns = useMemo<ColumnDef<APAuditTimelineDTO>[]>(
    () => [
      {
        accessorKey: 'timestamp',
        header: 'Time',
        cell: ({ row }) => format(new Date(row.original.timestamp), 'dd/MM/yyyy HH:mm:ss'),
      },
      {
        accessorKey: 'action',
        header: 'Action',
        cell: ({ row }) => (
          <Badge variant="outline" className="font-mono">
            {row.original.action}
          </Badge>
        ),
      },
      {
        accessorKey: 'userName',
        header: 'User',
        cell: ({ row }) => (
          <div className="flex flex-col">
            <span className="font-medium">{row.original.userName}</span>
            <span className="text-xs text-muted-foreground">{row.original.userRole}</span>
          </div>
        ),
      },
      {
        accessorKey: 'entityDisplay',
        header: 'Entity',
        cell: ({ row }) => row.original.entityDisplay || '-',
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <Badge variant={row.original.status === 'SUCCESS' ? 'secondary' : 'destructive'}>
            {row.original.status}
          </Badge>
        ),
      },
      {
        id: 'actions',
        cell: ({ row }) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon">
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => console.log('View details', row.original.id)}>
                <FileText className="mr-2 h-4 w-4" />
                View Details
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    data: items,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    state: {
      sorting,
    },
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            AP Audit Timeline
          </h1>
          <p className="text-muted-foreground">
            Complete history of all Accounts Payable activities and events.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/accounting/ap-audit/abuse')}>
            <ShieldAlert className="mr-2 h-4 w-4 text-orange-500" />
            Abuse Detection
          </Button>
          <Button variant="outline" onClick={() => navigate('/accounting/ap-audit/backups')}>
            <Archive className="mr-2 h-4 w-4 text-blue-500" />
            Backups
          </Button>
          <Button variant="outline" onClick={loadData}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={handleExport}>
            <Download className="mr-2 h-4 w-4" />
            Export PDF
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 items-end">
        <div className="space-y-2">
          <label className="text-sm font-medium">Search Action</label>
          <div className="relative">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="e.g. CREATE, DELETE..."
              value={actionFilter}
              onChange={(e) => setActionFilter(e.target.value)}
              className="pl-8 w-[200px]"
            />
          </div>
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium">User ID</label>
          <Input
            placeholder="User ID"
            value={userIdFilter}
            onChange={(e) => setUserIdFilter(e.target.value)}
            className="w-[100px]"
          />
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium">Date Range</label>
          <div className="flex gap-2">
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  className={`w-[140px] justify-start text-left font-normal ${!startDate && 'text-muted-foreground'}`}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {startDate ? format(startDate, 'dd/MM/yyyy') : 'Start Date'}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0">
                <Calendar mode="single" selected={startDate} onSelect={setStartDate} initialFocus />
              </PopoverContent>
            </Popover>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  className={`w-[140px] justify-start text-left font-normal ${!endDate && 'text-muted-foreground'}`}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {endDate ? format(endDate, 'dd/MM/yyyy') : 'End Date'}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0">
                <Calendar mode="single" selected={endDate} onSelect={setEndDate} initialFocus />
              </PopoverContent>
            </Popover>
          </div>
        </div>
        <Button
          variant="ghost"
          onClick={() => {
            setActionFilter('')
            setUserIdFilter('')
            setStartDate(undefined)
            setEndDate(undefined)
            setPage(0)
          }}
        >
          Reset
        </Button>
      </div>

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
            {items.length > 0 ? (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 text-center">
                  No results.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between space-x-2 py-4">
        <div className="flex-1 text-sm text-muted-foreground">
          Showing {items.length} of {totalElements} events.
        </div>
        <div className="flex items-center space-x-2">
          <p className="text-sm font-medium">Rows per page</p>
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
              {PAGE_SIZE_OPTIONS.map((pageSize) => (
                <SelectItem key={pageSize} value={`${pageSize}`}>
                  {pageSize}
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
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
