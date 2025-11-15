'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Calendar,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Search,
  FileText,
  Trash2,
  MoreVertical,
  Copy,
  X,
  Plus,
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
import { useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
  DialogFooter,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { getVouchers, getVoucherCounts, deleteVoucher } from '@/services/voucher'
import type { VoucherListDTO, VoucherQueryParams, VoucherCountDTO } from '@/types/voucher'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const STATUS_OPTIONS = [
  { value: 'all', label: 'All Status' },
  { value: 'draft', label: 'Draft' },
  { value: 'posted', label: 'Posted' },
  { value: 'unposted', label: 'Unposted' },
]

// localStorage key prefix for voucher list filters
const getStorageKey = (key: string): string => {
  const companyId = localStorage.getItem('activeCompanyId') || 'default'
  return `voucherList_${companyId}_${key}`
}

// Helper to load from localStorage
function loadFromStorage<T>(key: string, defaultValue: T): T {
  try {
    const stored = localStorage.getItem(getStorageKey(key))
    if (stored) {
      return JSON.parse(stored) as T
    }
  } catch {
    // Ignore parse errors
  }
  return defaultValue
}

// Helper to save to localStorage
function saveToStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(getStorageKey(key), JSON.stringify(value))
  } catch {
    // Ignore storage errors
  }
}

function formatDate(value: string | null) {
  if (!value) return '-'
  try {
    return format(new Date(value), 'yyyy-MM-dd')
  } catch {
    return value
  }
}

function formatCurrency(value: number | null | undefined, currency = 'VND') {
  if (value == null) return '-'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

function getStatusBadgeVariant(status: string) {
  switch (status) {
    case 'draft':
      return 'secondary'
    case 'posted':
      return 'default'
    case 'unposted':
      return 'destructive'
    default:
      return 'outline'
  }
}

export default function VoucherList() {
  const navigate = useNavigate()
  const [vouchers, setVouchers] = useState<VoucherListDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [errorDetails, setErrorDetails] = useState<any>(null)
  const [showErrorModal, setShowErrorModal] = useState(false)

  // Filters and search - with localStorage persistence
  const [status, setStatus] = useState<string>(() => loadFromStorage('status', 'all'))
  const [dateFrom, setDateFrom] = useState<string>(() => loadFromStorage('dateFrom', ''))
  const [dateTo, setDateTo] = useState<string>(() => loadFromStorage('dateTo', ''))
  const [accountId, setAccountId] = useState<number | undefined>(() =>
    loadFromStorage('accountId', undefined),
  )
  const [search, setSearch] = useState<string>(() => loadFromStorage('search', ''))
  const [sorting, setSorting] = useState<SortingState>(() =>
    loadFromStorage('sorting', [] as SortingState),
  )

  // Pagination
  const [page, setPage] = useState(() => loadFromStorage('page', 0))
  const [pageSize, setPageSize] = useState(() => loadFromStorage('pageSize', 20))
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Badge counts
  const [counts, setCounts] = useState<VoucherCountDTO>({ draft: 0, posted: 0, unposted: 0 })

  // Delete dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [voucherToDelete, setVoucherToDelete] = useState<VoucherListDTO | null>(null)
  const [deleteReason, setDeleteReason] = useState('')

  // Save to localStorage whenever filters change
  useEffect(() => {
    saveToStorage('status', status)
  }, [status])
  useEffect(() => {
    saveToStorage('dateFrom', dateFrom)
  }, [dateFrom])
  useEffect(() => {
    saveToStorage('dateTo', dateTo)
  }, [dateTo])
  useEffect(() => {
    saveToStorage('accountId', accountId)
  }, [accountId])
  useEffect(() => {
    saveToStorage('search', search)
  }, [search])
  useEffect(() => {
    saveToStorage('sorting', sorting)
  }, [sorting])
  useEffect(() => {
    saveToStorage('page', page)
  }, [page])
  useEffect(() => {
    saveToStorage('pageSize', pageSize)
  }, [pageSize])

  // Convert sorting state to API sort parameters
  const sortParams = useMemo(() => {
    return sorting.map((sort) => {
      const direction = sort.desc ? 'desc' : 'asc'
      // Map column IDs to API field names
      const fieldMap: Record<string, string> = {
        voucherDate: 'voucherDate',
        status: 'status',
        voucherNumber: 'voucherNumber',
        totalDebit: 'totalDebit',
        totalCredit: 'totalCredit',
      }
      const field = fieldMap[sort.id] || sort.id
      return `${field},${direction}`
    })
  }, [sorting])

  const loadVouchers = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setErrorDetails(null)

      const params: VoucherQueryParams = {
        page,
        size: pageSize,
        status: status !== 'all' ? (status as 'draft' | 'posted' | 'unposted') : undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        accountId,
        search: search.trim() || undefined,
        sort: sortParams.length > 0 ? sortParams : undefined,
      }

      const response = await getVouchers(params)
      setVouchers(response.data.content)
      setTotalElements(response.data.totalElements)
      setTotalPages(response.data.totalPages)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load vouchers'
      setError(message)
      setErrorDetails(err)
      toast.error('Failed to load vouchers', {
        description: message,
        action: {
          label: 'Retry',
          onClick: () => loadVouchers(),
        },
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, status, dateFrom, dateTo, accountId, search, sortParams])

  const loadCounts = useCallback(async () => {
    try {
      const countsData = await getVoucherCounts()
      setCounts(countsData)
    } catch (err) {
      // Silently fail for counts - not critical
      console.warn('Failed to load voucher counts:', err)
    }
  }, [])

  useEffect(() => {
    loadVouchers()
  }, [loadVouchers])

  useEffect(() => {
    loadCounts()
    // Refresh counts after voucher operations
    const interval = setInterval(loadCounts, 30000) // Poll every 30 seconds
    return () => clearInterval(interval)
  }, [loadCounts])

  const handleRefresh = async () => {
    await Promise.all([loadVouchers(), loadCounts()])
    toast.success('Vouchers refreshed')
  }

  const handleDelete = async () => {
    if (!voucherToDelete || !deleteReason.trim()) {
      toast.error('Deletion reason is required')
      return
    }

    try {
      await deleteVoucher(voucherToDelete.id, deleteReason.trim())
      toast.success('Voucher deleted successfully')
      setDeleteDialogOpen(false)
      setVoucherToDelete(null)
      setDeleteReason('')
      // Refresh data
      await Promise.all([loadVouchers(), loadCounts()])
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to delete voucher'
      toast.error('Failed to delete voucher', { description: message })
    }
  }

  const handleResetFilters = () => {
    setStatus('all')
    setDateFrom('')
    setDateTo('')
    setAccountId(undefined)
    setSearch('')
    setSorting([])
    setPage(0)
  }

  const handleCopyErrorDetails = () => {
    const errorJson = JSON.stringify(errorDetails, null, 2)
    navigator.clipboard.writeText(errorJson)
    toast.success('Error details copied to clipboard')
  }

  const columns = useMemo<ColumnDef<VoucherListDTO>[]>(
    () => [
      {
        accessorKey: 'voucherNumber',
        header: 'Voucher Number',
        enableSorting: true,
      },
      {
        accessorKey: 'voucherDate',
        header: 'Date',
        cell: ({ row }) => formatDate(row.original.voucherDate),
        enableSorting: true,
      },
      {
        accessorKey: 'type',
        header: 'Type',
      },
      {
        id: 'amount',
        header: 'Amount',
        cell: ({ row }) => {
          const total = row.original.totalDebit || row.original.totalCredit || 0
          return formatCurrency(total, row.original.currency)
        },
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <Badge variant={getStatusBadgeVariant(row.original.status)}>
            {row.original.status.toUpperCase()}
          </Badge>
        ),
        enableSorting: true,
      },
      {
        accessorKey: 'enteredByName',
        header: 'Entered By',
      },
      {
        accessorKey: 'postedByName',
        header: 'Posted By',
        cell: ({ row }) => row.original.postedByName || '-',
      },
      {
        accessorKey: 'arApEntity',
        header: 'AR/AP Entity',
        cell: ({ row }) => row.original.arApEntity || '-',
      },
      {
        id: 'reversal',
        header: 'Reversal',
        cell: ({ row }) => {
          if (row.original.hasReversal && row.original.reversedByVoucherId) {
            return (
              <Badge
                variant="outline"
                className="cursor-pointer hover:bg-orange-100"
                onClick={(e) => {
                  e.stopPropagation()
                  navigate(`/vouchers/${row.original.reversedByVoucherId}`)
                }}
              >
                Reversed by
              </Badge>
            )
          }
          return '-'
        },
      },
      {
        accessorKey: 'attachmentCount',
        header: 'Attachments',
        cell: ({ row }) => row.original.attachmentCount || 0,
      },
      {
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => {
          const voucher = row.original
          const canDelete = voucher.status === 'draft'
          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => navigate(`/vouchers/${voucher.id}`)}>
                  <FileText className="mr-2 h-4 w-4" />
                  View
                </DropdownMenuItem>
                {canDelete && (
                  <DropdownMenuItem
                    onClick={() => {
                      setVoucherToDelete(voucher)
                      setDeleteDialogOpen(true)
                    }}
                    className="text-destructive"
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
    [navigate],
  )

  const table = useReactTable({
    data: vouchers,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    manualSorting: true,
    state: {
      sorting,
    },
  })

  const isEmpty = !loading && vouchers.length === 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Vouchers <span className="text-muted-foreground text-lg">/ Phiếu kế toán</span>
          </h1>
          <p className="text-muted-foreground">
            View, search, and manage voucher entries with server-side pagination.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => navigate('/vouchers/new')}>
            <Plus className="mr-2 h-4 w-4" />
            Create Voucher
          </Button>
        </div>
      </div>

      {/* Badge counts */}
      <div className="flex gap-4">
        <div className="flex items-center gap-2">
          <Badge variant="secondary">Draft: {counts.draft}</Badge>
          <Badge variant="default">Posted: {counts.posted}</Badge>
          <Badge variant="outline">Unposted: {counts.unposted}</Badge>
        </div>
      </div>

      {/* Filters */}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Search</label>
          <div className="relative">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Voucher number, description..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              className="pl-8"
            />
          </div>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Status</label>
          <Select
            value={status}
            onValueChange={(value) => {
              setStatus(value)
              setPage(0)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All Status" />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            Date From
          </label>
          <Input
            type="date"
            value={dateFrom}
            onChange={(e) => {
              setDateFrom(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            Date To
          </label>
          <Input
            type="date"
            value={dateTo}
            onChange={(e) => {
              setDateTo(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Account</label>
          <Input
            type="number"
            placeholder="Account ID"
            value={accountId || ''}
            onChange={(e) => {
              const value = e.target.value ? Number(e.target.value) : undefined
              setAccountId(value)
              setPage(0)
            }}
          />
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => setShowErrorModal(true)}>
                View Details
              </Button>
              <Button variant="outline" size="sm" onClick={loadVouchers}>
                Retry
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      )}

      {/* Error Details Modal */}
      <Dialog open={showErrorModal} onOpenChange={setShowErrorModal}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Error Details</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label>Error Message</Label>
              <p className="text-sm text-muted-foreground">{error}</p>
            </div>
            <div>
              <Label>Error Details (JSON)</Label>
              <pre className="mt-2 rounded bg-muted p-3 text-xs overflow-auto max-h-96">
                {JSON.stringify(errorDetails, null, 2)}
              </pre>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={handleCopyErrorDetails}>
              <Copy className="mr-2 h-4 w-4" />
              Copy Error Details
            </Button>
            <Button onClick={() => setShowErrorModal(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Voucher</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Are you sure you want to delete voucher{' '}
              <strong>{voucherToDelete?.voucherNumber}</strong>? This action cannot be undone.
            </p>
            <div>
              <Label htmlFor="delete-reason">
                Deletion Reason <span className="text-destructive">*</span>
              </Label>
              <Textarea
                id="delete-reason"
                placeholder="Enter reason for deletion (required for audit log)"
                value={deleteReason}
                onChange={(e) => setDeleteReason(e.target.value)}
                className="mt-2"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={!deleteReason.trim()}>
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
                        {header.column.getIsSorted() && (
                          <span>{header.column.getIsSorted() === 'desc' ? '↓' : '↑'}</span>
                        )}
                      </div>
                    )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, index) => (
                <TableRow key={`skeleton-${index}`}>
                  {columns.map((column) => (
                    <TableCell key={`skeleton-${column.id ?? 'cell'}-${index}`}>
                      <Skeleton className="h-10 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : isEmpty ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-center py-12">
                  <div className="flex flex-col items-center gap-4">
                    <FileText className="h-12 w-12 text-muted-foreground" />
                    <div>
                      <p className="text-lg font-medium">No vouchers found</p>
                      <p className="text-sm text-muted-foreground">
                        {search || status !== 'all' || dateFrom || dateTo || accountId
                          ? 'Try adjusting your filters'
                          : 'Get started by creating your first voucher'}
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <Button onClick={() => navigate('/vouchers/new')}>
                        <Plus className="mr-2 h-4 w-4" />
                        Create First Voucher
                      </Button>
                      {(search || status !== 'all' || dateFrom || dateTo || accountId) && (
                        <Button variant="outline" onClick={handleResetFilters}>
                          Reset Filters
                        </Button>
                      )}
                    </div>
                  </div>
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
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="text-sm text-muted-foreground">
          Showing <span className="font-medium text-foreground">{vouchers.length}</span> of{' '}
          <span className="font-medium text-foreground">{totalElements}</span> records
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">Rows per page</span>
            <Select
              value={pageSize.toString()}
              onValueChange={(value) => {
                setPageSize(Number(value))
                setPage(0)
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
              disabled={page <= 0 || loading}
              onClick={() => setPage(0)}
            >
              <ChevronsLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              disabled={page <= 0 || loading}
              onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-sm text-muted-foreground">
              Page <span className="font-semibold text-foreground">{page + 1}</span> of{' '}
              <span className="font-semibold text-foreground">{Math.max(totalPages, 1)}</span>
            </span>
            <Button
              variant="outline"
              size="icon"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((prev) => Math.min(prev + 1, Math.max(totalPages - 1, 0)))}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage(Math.max(totalPages - 1, 0))}
            >
              <ChevronsRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
