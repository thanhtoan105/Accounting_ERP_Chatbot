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
  Wallet,
  Trash2,
  MoreVertical,
  Plus,
  Upload,
  Send,
  Eye,
  Edit,
  XCircle,
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
import { useNavigate } from 'react-router-dom'

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
  DialogFooter,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import { getPayments, deletePayment, postPayment, cancelPayment } from '@/services/payment'
import type { APPaymentListDTO, PaymentQueryParams, PaymentStatus } from '@/types/payment'
import { useDebounce } from '@/hooks/use-debounce'
import { PaymentApprovalDialog } from '@/components/payment'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'all', label: 'All Status' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING_APPROVAL', label: 'Pending Approval' },
  { value: 'POSTED', label: 'Posted' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

// localStorage key prefix for payment list filters
const getStorageKey = (key: string): string => {
  const companyId = localStorage.getItem('activeCompanyId') || 'default'
  return `paymentList_${companyId}_${key}`
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

function getStatusBadgeVariant(status: PaymentStatus) {
  switch (status) {
    case 'DRAFT':
      return 'secondary'
    case 'PENDING_APPROVAL':
      return 'outline'
    case 'POSTED':
      return 'default'
    case 'CANCELLED':
      return 'destructive'
    default:
      return 'outline'
  }
}

function getStatusLabel(status: PaymentStatus) {
  switch (status) {
    case 'DRAFT':
      return 'Draft'
    case 'PENDING_APPROVAL':
      return 'Pending Approval'
    case 'POSTED':
      return 'Posted'
    case 'CANCELLED':
      return 'Cancelled'
    default:
      return status
  }
}

export default function PaymentList() {
  const navigate = useNavigate()
  const [payments, setPayments] = useState<APPaymentListDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filters and search - with localStorage persistence
  const [status, setStatus] = useState<string>(() => loadFromStorage('status', 'all'))
  const [supplier, setSupplier] = useState<number | undefined>(() =>
    loadFromStorage('supplier', undefined),
  )
  const [dateFrom, setDateFrom] = useState<string>(() => loadFromStorage('dateFrom', ''))
  const [dateTo, setDateTo] = useState<string>(() => loadFromStorage('dateTo', ''))
  const [search, setSearch] = useState<string>(() => loadFromStorage('search', ''))
  const [standalone, setStandalone] = useState<boolean | undefined>(() =>
    loadFromStorage('standalone', undefined),
  )
  const debouncedSearch = useDebounce(search, 300)
  const [sorting, setSorting] = useState<SortingState>(() =>
    loadFromStorage('sorting', [] as SortingState),
  )

  // Pagination
  const [page, setPage] = useState(() => loadFromStorage('page', 0))
  const [pageSize, setPageSize] = useState(() => loadFromStorage('pageSize', 20))
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Delete dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [paymentToDelete, setPaymentToDelete] = useState<APPaymentListDTO | null>(null)
  const [deleting, setDeleting] = useState(false)

  // Post dialog
  const [postDialogOpen, setPostDialogOpen] = useState(false)
  const [paymentToPost, setPaymentToPost] = useState<APPaymentListDTO | null>(null)
  const [posting, setPosting] = useState(false)

  // Approval dialog (for PENDING_APPROVAL payments)
  const [approvalDialogOpen, setApprovalDialogOpen] = useState(false)
  const [paymentToApprove, setPaymentToApprove] = useState<APPaymentListDTO | null>(null)

  // Import dialog (TODO: Create PaymentImportDialog component)
  const [importDialogOpen, setImportDialogOpen] = useState(false)

  // Save to localStorage whenever filters change
  useEffect(() => {
    saveToStorage('status', status)
  }, [status])
  useEffect(() => {
    saveToStorage('supplier', supplier)
  }, [supplier])
  useEffect(() => {
    saveToStorage('dateFrom', dateFrom)
  }, [dateFrom])
  useEffect(() => {
    saveToStorage('dateTo', dateTo)
  }, [dateTo])
  useEffect(() => {
    saveToStorage('search', search)
  }, [search])
  useEffect(() => {
    saveToStorage('standalone', standalone)
  }, [standalone])
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
        paymentDate: 'paymentDate',
        status: 'status',
        paymentNumber: 'paymentNumber',
        amount: 'amount',
        supplierName: 'supplierName',
      }
      const field = fieldMap[sort.id] || sort.id
      return `${field},${direction}`
    })
  }, [sorting])

  const loadPayments = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const params: PaymentQueryParams = {
        page,
        size: pageSize,
        status: status !== 'all' ? (status as PaymentStatus) : undefined,
        supplier,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        search: debouncedSearch.trim() || undefined,
        standalone: standalone !== undefined ? standalone : undefined,
        sort: sortParams.length > 0 ? sortParams : undefined,
      }

      const response = await getPayments(params)
      setPayments(response.data.content)
      setTotalElements(response.data.totalElements)
      setTotalPages(response.data.totalPages)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load payments'
      setError(message)
      toast.error('Failed to load payments', {
        description: message,
        action: {
          label: 'Retry',
          onClick: () => loadPayments(),
        },
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, status, supplier, dateFrom, dateTo, debouncedSearch, standalone, sortParams])

  useEffect(() => {
    loadPayments()
  }, [loadPayments])

  const handleRefresh = async () => {
    await loadPayments()
    toast.success('Payments refreshed')
  }

  const handleDelete = async () => {
    if (!paymentToDelete) return

    try {
      setDeleting(true)
      await deletePayment(paymentToDelete.id)
      toast.success('Payment deleted successfully')
      setDeleteDialogOpen(false)
      setPaymentToDelete(null)
      // Refresh data
      await loadPayments()
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to delete payment'
      toast.error('Failed to delete payment', { description: message })
    } finally {
      setDeleting(false)
    }
  }

  const handlePost = async () => {
    if (!paymentToPost) return

    try {
      setPosting(true)
      await postPayment(paymentToPost.id)
      toast.success('Payment posted successfully', {
        description: 'Voucher has been generated and bills updated',
      })
      setPostDialogOpen(false)
      setPaymentToPost(null)
      // Refresh data
      await loadPayments()
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to post payment'
      toast.error('Failed to post payment', { description: message })
    } finally {
      setPosting(false)
    }
  }

  const handleResetFilters = () => {
    setStatus('all')
    setSupplier(undefined)
    setDateFrom('')
    setDateTo('')
    setSearch('')
    setStandalone(undefined)
    setSorting([])
    setPage(0)
  }

  const columns = useMemo<ColumnDef<APPaymentListDTO>[]>(
    () => [
      {
        accessorKey: 'paymentNumber',
        header: 'Payment Number',
        enableSorting: true,
      },
      {
        accessorKey: 'supplierName',
        header: 'Supplier',
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <span>{row.original.supplierName || row.original.supplierCode || '-'}</span>
            {row.original.isStandalone && (
              <Badge
                variant="outline"
                className="text-xs border-orange-500 text-orange-700 bg-orange-50 dark:bg-orange-950/20 dark:text-orange-400 dark:border-orange-400"
              >
                <AlertTriangle className="mr-1 h-3 w-3" />
                Standalone
              </Badge>
            )}
          </div>
        ),
        enableSorting: true,
      },
      {
        accessorKey: 'paymentDate',
        header: 'Payment Date',
        cell: ({ row }) => formatDate(row.original.paymentDate),
        enableSorting: true,
      },
      {
        accessorKey: 'amount',
        header: 'Amount',
        cell: ({ row }) => formatCurrency(row.original.amount),
        enableSorting: true,
      },
      {
        accessorKey: 'account',
        header: 'Account',
        cell: ({ row }) => {
          const accountName = row.original.cashAccountName || row.original.bankAccountName
          return accountName || '-'
        },
      },
      {
        accessorKey: 'paymentMethod',
        header: 'Method',
        cell: ({ row }) => {
          const method = row.original.paymentMethod
          return method === 'BANK_TRANSFER' ? 'Bank' : method === 'CASH' ? 'Cash' : method
        },
      },
      {
        accessorKey: 'allocationCount',
        header: 'Allocations',
        cell: ({ row }) => {
          const count = row.original.allocationCount || 0
          return count > 0 ? (
            <Badge variant="outline">
              {count} bill{count !== 1 ? 's' : ''}
            </Badge>
          ) : (
            <span className="text-muted-foreground">-</span>
          )
        },
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <Badge variant={getStatusBadgeVariant(row.original.status)}>
            {getStatusLabel(row.original.status)}
          </Badge>
        ),
        enableSorting: true,
      },
      {
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => {
          const payment = row.original
          const canEdit = payment.status === 'DRAFT'
          const canDelete = payment.status === 'DRAFT'
          const canPost = payment.status === 'DRAFT'
          const canApprove = payment.status === 'PENDING_APPROVAL'
          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" onClick={(e) => e.stopPropagation()}>
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => navigate(`/payments/${payment.id}`)}>
                  <Eye className="mr-2 h-4 w-4" />
                  View
                </DropdownMenuItem>
                {canEdit && (
                  <DropdownMenuItem onClick={() => navigate(`/payments/${payment.id}/edit`)}>
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                )}
                {canPost && (
                  <DropdownMenuItem
                    onClick={(e) => {
                      e.stopPropagation()
                      setPaymentToPost(payment)
                      setPostDialogOpen(true)
                    }}
                  >
                    <Send className="mr-2 h-4 w-4" />
                    Post Payment
                  </DropdownMenuItem>
                )}
                {canApprove && (
                  <DropdownMenuItem
                    onClick={(e) => {
                      e.stopPropagation()
                      setPaymentToApprove(payment)
                      setApprovalDialogOpen(true)
                    }}
                  >
                    <CheckCircle className="mr-2 h-4 w-4" />
                    Approve Payment
                  </DropdownMenuItem>
                )}
                {canDelete && (
                  <>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={(e) => {
                        e.stopPropagation()
                        setPaymentToDelete(payment)
                        setDeleteDialogOpen(true)
                      }}
                      className="text-destructive"
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      Delete
                    </DropdownMenuItem>
                  </>
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
    data: payments,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    manualSorting: true,
    state: {
      sorting,
    },
  })

  const isEmpty = !loading && payments.length === 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <Wallet className="h-6 w-6 text-primary" />
            AP Payments{' '}
            <span className="text-muted-foreground text-lg">/ Thanh toán nhà cung cấp</span>
          </h1>
          <p className="text-muted-foreground">
            View, search, and manage supplier payments with server-side pagination.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button variant="outline" onClick={() => setImportDialogOpen(true)}>
            <Upload className="mr-2 h-4 w-4" />
            Import
          </Button>
          <Button onClick={() => navigate('/payments/new')}>
            <Plus className="mr-2 h-4 w-4" />
            Create Payment
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Search</label>
          <div className="relative">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Payment number, reference, payee..."
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
          <DatePicker
            value={dateFrom}
            onChange={(value) => {
              setDateFrom(value)
              setPage(0)
            }}
            placeholder="Select start date"
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            Date To
          </label>
          <DatePicker
            value={dateTo}
            onChange={(value) => {
              setDateTo(value)
              setPage(0)
            }}
            placeholder="Select end date"
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Supplier ID</label>
          <Input
            type="number"
            placeholder="Supplier ID"
            value={supplier || ''}
            onChange={(e) => {
              const value = e.target.value ? Number(e.target.value) : undefined
              setSupplier(value)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Standalone</label>
          <Select
            value={standalone === undefined ? 'all' : standalone ? 'yes' : 'no'}
            onValueChange={(value) => {
              const newValue = value === 'all' ? undefined : value === 'yes'
              setStandalone(newValue)
              setPage(0)
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="All" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="yes">Standalone Only</SelectItem>
              <SelectItem value="no">Linked Only</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <Button variant="outline" size="sm" onClick={loadPayments}>
              Retry
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Payment</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Are you sure you want to delete payment{' '}
              <strong>{paymentToDelete?.paymentNumber}</strong>? This action cannot be undone.
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? 'Deleting...' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Post Confirmation Dialog */}
      <Dialog open={postDialogOpen} onOpenChange={setPostDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Post Payment</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Are you sure you want to post payment <strong>{paymentToPost?.paymentNumber}</strong>?
              This will:
            </p>
            <ul className="list-disc list-inside text-sm text-muted-foreground space-y-1">
              <li>Generate a voucher (Dr AP 331, Cr cash/bank)</li>
              <li>Update bill statuses to PAID/PARTIALLY_PAID</li>
              <li>Update remaining balances</li>
              <li>Mark payment as POSTED</li>
            </ul>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPostDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handlePost} disabled={posting}>
              {posting ? (
                <>
                  <Send className="mr-2 h-4 w-4 animate-spin" />
                  Posting...
                </>
              ) : (
                <>
                  <Send className="mr-2 h-4 w-4" />
                  Post Payment
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Approval Dialog for PENDING_APPROVAL payments */}
      <PaymentApprovalDialog
        payment={paymentToApprove}
        open={approvalDialogOpen}
        onOpenChange={(open) => {
          setApprovalDialogOpen(open)
          if (!open) {
            setPaymentToApprove(null)
          }
        }}
        onApproved={async () => {
          await loadPayments()
        }}
      />

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
            ) : isEmpty ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 text-center">
                  No payments found.
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => navigate(`/payments/${row.original.id}`)}
                >
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
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">
            Showing {payments.length > 0 ? page * pageSize + 1 : 0} to{' '}
            {Math.min((page + 1) * pageSize, totalElements)} of {totalElements} payments
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
            size="sm"
            onClick={() => setPage(0)}
            disabled={page === 0 || loading}
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="sm"
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
            size="sm"
            onClick={() => setPage(page + 1)}
            disabled={page >= totalPages - 1 || loading}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(totalPages - 1)}
            disabled={page >= totalPages - 1 || loading}
          >
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Import Dialog - TODO: Create PaymentImportDialog component */}
      {importDialogOpen && (
        <Alert>
          <AlertDescription>
            Payment import dialog will be implemented here. For now, use the API endpoint directly.
          </AlertDescription>
        </Alert>
      )}
    </div>
  )
}
