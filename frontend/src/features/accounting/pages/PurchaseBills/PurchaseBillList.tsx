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
  Plus,
  Upload,
  RotateCcw,
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
} from '@/components/ui/dropdown-menu'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import {
  getPurchaseBills,
  deletePurchaseBill,
} from '@/services/purchaseBill'
import { PurchaseBillImportDialog, DraftRecoveryDialog } from '@/components/purchase'
import type {
  PurchaseBillListDTO,
  PurchaseBillQueryParams,
  PurchaseBillStatus,
} from '@/types/purchaseBill'
import { useDebounce } from '@/hooks/use-debounce'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'all', label: 'All Status' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING_APPROVAL', label: 'Pending Approval' },
  { value: 'POSTED', label: 'Posted' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'PAID', label: 'Paid' },
  { value: 'PARTIALLY_PAID', label: 'Partially Paid' },
]

// localStorage key prefix for purchase bill list filters
const getStorageKey = (key: string): string => {
  const companyId = localStorage.getItem('activeCompanyId') || 'default'
  return `purchaseBillList_${companyId}_${key}`
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

function getStatusBadgeVariant(status: PurchaseBillStatus) {
  switch (status) {
    case 'DRAFT':
      return 'secondary'
    case 'PENDING_APPROVAL':
      return 'outline'
    case 'POSTED':
      return 'default'
    case 'REJECTED':
      return 'destructive'
    case 'PAID':
      return 'default'
    case 'PARTIALLY_PAID':
      return 'outline'
    default:
      return 'outline'
  }
}

function getStatusLabel(status: PurchaseBillStatus) {
  switch (status) {
    case 'DRAFT':
      return 'Draft'
    case 'PENDING_APPROVAL':
      return 'Pending Approval'
    case 'POSTED':
      return 'Posted'
    case 'REJECTED':
      return 'Rejected'
    case 'PAID':
      return 'Paid'
    case 'PARTIALLY_PAID':
      return 'Partially Paid'
    default:
      return status
  }
}

export default function PurchaseBillList() {
  const navigate = useNavigate()
  const [bills, setBills] = useState<PurchaseBillListDTO[]>([])
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
  const [billToDelete, setBillToDelete] = useState<PurchaseBillListDTO | null>(null)
  const [deleteReason, setDeleteReason] = useState('')
  const [deleting, setDeleting] = useState(false)

  // Import dialog
  const [importDialogOpen, setImportDialogOpen] = useState(false)

  // Draft recovery dialog
  const [draftRecoveryDialogOpen, setDraftRecoveryDialogOpen] = useState(false)

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
        billDate: 'billDate',
        status: 'status',
        billNumber: 'billNumber',
        totalAmount: 'totalAmount',
        supplierName: 'supplierName',
      }
      const field = fieldMap[sort.id] || sort.id
      return `${field},${direction}`
    })
  }, [sorting])

  const loadBills = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const params: PurchaseBillQueryParams = {
        page,
        size: pageSize,
        status: status !== 'all' ? (status as PurchaseBillStatus) : undefined,
        supplier,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        search: debouncedSearch.trim() || undefined,
        sort: sortParams.length > 0 ? sortParams : undefined,
      }

      const response = await getPurchaseBills(params)
      setBills(response.data.content)
      setTotalElements(response.data.totalElements)
      setTotalPages(response.data.totalPages)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load purchase bills'
      setError(message)
      toast.error('Failed to load purchase bills', {
        description: message,
        action: {
          label: 'Retry',
          onClick: () => loadBills(),
        },
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, status, supplier, dateFrom, dateTo, debouncedSearch, sortParams])

  useEffect(() => {
    loadBills()
  }, [loadBills])

  const handleRefresh = async () => {
    await loadBills()
    toast.success('Purchase bills refreshed')
  }

  const handleDelete = async () => {
    if (!billToDelete || !deleteReason.trim()) {
      toast.error('Deletion reason is required')
      return
    }

    try {
      setDeleting(true)
      await deletePurchaseBill(billToDelete.id, deleteReason.trim())
      toast.success('Purchase bill deleted successfully')
      setDeleteDialogOpen(false)
      setBillToDelete(null)
      setDeleteReason('')
      // Refresh data
      await loadBills()
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to delete purchase bill'
      toast.error('Failed to delete purchase bill', { description: message })
    } finally {
      setDeleting(false)
    }
  }

  const handleResetFilters = () => {
    setStatus('all')
    setSupplier(undefined)
    setDateFrom('')
    setDateTo('')
    setSearch('')
    setSorting([])
    setPage(0)
  }

  const columns = useMemo<ColumnDef<PurchaseBillListDTO>[]>(
    () => [
      {
        accessorKey: 'billNumber',
        header: 'Bill Number',
        enableSorting: true,
      },
      {
        accessorKey: 'supplierName',
        header: 'Supplier',
        cell: ({ row }) => row.original.supplierName || row.original.supplierCode || '-',
        enableSorting: true,
      },
      {
        accessorKey: 'billDate',
        header: 'Bill Date',
        cell: ({ row }) => formatDate(row.original.billDate),
        enableSorting: true,
      },
      {
        accessorKey: 'dueDate',
        header: 'Due Date',
        cell: ({ row }) => formatDate(row.original.dueDate),
        enableSorting: true,
      },
      {
        accessorKey: 'reference',
        header: 'Reference',
      },
      {
        accessorKey: 'totalAmount',
        header: 'Amount',
        cell: ({ row }) => formatCurrency(row.original.totalAmount),
        enableSorting: true,
      },
      {
        accessorKey: 'vatAmount',
        header: 'VAT',
        cell: ({ row }) => formatCurrency(row.original.vatAmount),
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
        accessorKey: 'createdByName',
        header: 'Created By',
      },
      {
        accessorKey: 'approvedByName',
        header: 'Approved By',
        cell: ({ row }) => row.original.approvedByName || '-',
      },
      {
        accessorKey: 'attachmentCount',
        header: 'Attachments',
        cell: ({ row }) => {
          const count = row.original.attachmentCount || 0
          return (
            <div className="flex items-center gap-1">
              <FileText className="h-3 w-3 text-muted-foreground" />
              {count}
            </div>
          )
        },
      },
      {
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => {
          const bill = row.original
          const canDelete = bill.status === 'DRAFT'
          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => navigate(`/purchase-bills/${bill.id}`)}>
                  <FileText className="mr-2 h-4 w-4" />
                  View
                </DropdownMenuItem>
                {canDelete && (
                  <DropdownMenuItem
                    onClick={() => {
                      setBillToDelete(bill)
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
    data: bills,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    manualSorting: true,
    state: {
      sorting,
    },
  })

  const isEmpty = !loading && bills.length === 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Purchase Bills <span className="text-muted-foreground text-lg">/ Hóa đơn mua hàng</span>
          </h1>
          <p className="text-muted-foreground">
            View, search, and manage purchase bills with server-side pagination.
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
          <Button variant="outline" onClick={() => setDraftRecoveryDialogOpen(true)}>
            <RotateCcw className="mr-2 h-4 w-4" />
            Recover Draft
          </Button>
          <Button onClick={() => navigate('/purchase-bills/new')}>
            <Plus className="mr-2 h-4 w-4" />
            Create Purchase Bill
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium">Search</label>
          <div className="relative">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Bill number, reference..."
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
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <Button variant="outline" size="sm" onClick={loadBills}>
              Retry
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Purchase Bill</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Are you sure you want to delete purchase bill{' '}
              <strong>{billToDelete?.billNumber}</strong>? This action cannot be undone.
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
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={!deleteReason.trim() || deleting}
            >
              {deleting ? 'Deleting...' : 'Delete'}
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
                  No purchase bills found.
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => navigate(`/purchase-bills/${row.original.id}`)}
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
            Showing {bills.length > 0 ? page * pageSize + 1 : 0} to{' '}
            {Math.min((page + 1) * pageSize, totalElements)} of {totalElements} purchase bills
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

      {/* Import Dialog */}
      <PurchaseBillImportDialog
        open={importDialogOpen}
        onOpenChange={setImportDialogOpen}
        onSuccess={() => {
          loadBills()
          toast.success('Purchase bills imported successfully')
        }}
      />

      {/* Draft Recovery Dialog */}
      <DraftRecoveryDialog
        open={draftRecoveryDialogOpen}
        onOpenChange={setDraftRecoveryDialogOpen}
      />
    </div>
  )
}

