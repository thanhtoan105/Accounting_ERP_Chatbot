'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
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
  Eye,
  Edit,
  Send,
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
import { ReceiptReversalDialog } from '@/components/receipt'
import { getReceipts, deleteReceipt, postReceipt } from '@/services/receipt'
import type {
  ARPaymentListDTO,
  ReceiptQueryParams,
  ReceiptStatus,
  ARPaymentDTO,
} from '@/types/receipt'
import { useDebounce } from '@/hooks/use-debounce'
import { useRole } from '@/hooks/useRole'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'all', label: 'All Status' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'POSTED', label: 'Posted' },
  { value: 'REVERSED', label: 'Reversed' },
]

const getStorageKey = (key: string): string => {
  const companyId = localStorage.getItem('activeCompanyId') || 'default'
  return `receiptList_${companyId}_${key}`
}

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
    return format(new Date(value), 'dd/MM/yyyy')
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

function getStatusBadgeVariant(status: ReceiptStatus) {
  switch (status) {
    case 'DRAFT':
      return 'secondary'
    case 'POSTED':
      return 'default'
    case 'REVERSED':
      return 'destructive'
    default:
      return 'outline'
  }
}

function getStatusLabel(status: ReceiptStatus) {
  switch (status) {
    case 'DRAFT':
      return 'Draft'
    case 'POSTED':
      return 'Posted'
    case 'REVERSED':
      return 'Reversed'
    default:
      return status
  }
}

export default function ReceiptList() {
  const navigate = useNavigate()
  const { hasRole, hasAnyRole, isAdmin, isChiefAccountant } = useRole()
  const [receipts, setReceipts] = useState<ARPaymentListDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filters and search
  const [status, setStatus] = useState<string>(() => loadFromStorage('status', 'all'))
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
  const [receiptToDelete, setReceiptToDelete] = useState<ARPaymentListDTO | null>(null)
  const [deleting, setDeleting] = useState(false)

  // Reversal dialog
  const [reversalDialogOpen, setReversalDialogOpen] = useState(false)
  const [receiptToReverse, setReceiptToReverse] = useState<ARPaymentDTO | null>(null)

  // Save filters to localStorage
  useEffect(() => {
    saveToStorage('status', status)
    saveToStorage('dateFrom', dateFrom)
    saveToStorage('dateTo', dateTo)
    saveToStorage('search', search)
    saveToStorage('standalone', standalone)
    saveToStorage('sorting', sorting)
    saveToStorage('page', page)
    saveToStorage('pageSize', pageSize)
  }, [status, dateFrom, dateTo, search, standalone, sorting, page, pageSize])

  const fetchReceipts = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const params: ReceiptQueryParams = {
        page,
        size: pageSize,
        search: debouncedSearch || undefined,
        status: status !== 'all' ? (status as ReceiptStatus) : undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        standalone: standalone,
        sort: sorting[0]
          ? `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`
          : 'receiptDate,desc',
      }

      const response = await getReceipts(params)
      setReceipts(response.receipts)
      setTotalElements(response.totalItems)
      setTotalPages(response.totalPages)
    } catch (err: any) {
      console.error('Failed to fetch receipts:', err)
      setError(err?.message || 'Failed to load receipts')
      toast.error('Failed to load receipts', {
        description: err?.message || 'An unexpected error occurred.',
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, debouncedSearch, status, dateFrom, dateTo, standalone, sorting])

  useEffect(() => {
    fetchReceipts()
  }, [fetchReceipts])

  const handleRefresh = useCallback(() => {
    fetchReceipts()
    toast.success('Receipts refreshed')
  }, [fetchReceipts])

  const handleDelete = async () => {
    if (!receiptToDelete) return

    setDeleting(true)
    try {
      await deleteReceipt(receiptToDelete.id)
      toast.success('Receipt deleted successfully', {
        description: `Receipt ${receiptToDelete.receiptNumber} has been deleted.`,
      })
      setDeleteDialogOpen(false)
      setReceiptToDelete(null)
      fetchReceipts()
    } catch (err: any) {
      console.error('Failed to delete receipt:', err)
      toast.error('Failed to delete receipt', {
        description: err?.message || 'An unexpected error occurred.',
      })
    } finally {
      setDeleting(false)
    }
  }

  const handlePost = async (receipt: ARPaymentListDTO) => {
    try {
      await postReceipt(receipt.id)
      toast.success('Receipt posted successfully', {
        description: `Receipt ${receipt.receiptNumber} has been posted and a GL voucher has been created.`,
      })
      fetchReceipts()
    } catch (err: any) {
      console.error('Failed to post receipt:', err)
      toast.error('Failed to post receipt', {
        description: err?.message || 'An unexpected error occurred.',
      })
    }
  }

  const handleReversalClick = (receipt: ARPaymentListDTO) => {
    // Convert list DTO to full DTO for reversal dialog
    const fullReceipt: ARPaymentDTO = {
      ...receipt,
      companyId: 0, // Will be populated from context
      payee: receipt.customerName,
      reference: null,
      receiptProofUrl: null,
      createdById: 0,
      createdByName: '',
      postedById: null,
      postedByName: null,
      reversalReason: null,
      originalReceiptId: null,
      originalReceiptNumber: null,
      reversingReceiptId: null,
      reversingReceiptNumber: null,
      createdAt: '',
      updatedAt: '',
      postedAt: null,
      allocations: [],
      cashAccountNumber: null,
      bankAccountNumber: null,
    }
    setReceiptToReverse(fullReceipt)
    setReversalDialogOpen(true)
  }

  const canEdit = (receipt: ARPaymentListDTO) => {
    return receipt.status === 'DRAFT' && hasAnyRole(['accountant', 'chief_accountant', 'admin'])
  }

  const canDelete = (receipt: ARPaymentListDTO) => {
    return receipt.status === 'DRAFT' && hasAnyRole(['accountant', 'chief_accountant', 'admin'])
  }

  const canPost = (receipt: ARPaymentListDTO) => {
    return receipt.status === 'DRAFT' && hasAnyRole(['accountant', 'chief_accountant', 'admin'])
  }

  const canReverse = (receipt: ARPaymentListDTO) => {
    return receipt.status === 'POSTED' && hasAnyRole(['chief_accountant', 'cfo', 'admin'])
  }

  const columns: ColumnDef<ARPaymentListDTO>[] = useMemo(
    () => [
      {
        accessorKey: 'receiptNumber',
        header: 'Receipt Number',
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <span className="font-medium">{row.original.receiptNumber}</span>
            {row.original.isStandalone && (
              <Badge variant="outline" className="text-xs">
                Standalone
              </Badge>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'receiptDate',
        header: 'Date',
        cell: ({ row }) => formatDate(row.original.receiptDate),
      },
      {
        accessorKey: 'customerName',
        header: 'Customer',
        cell: ({ row }) => (
          <div>
            <div className="font-medium">{row.original.customerName}</div>
            <div className="text-xs text-muted-foreground">{row.original.customerCode}</div>
          </div>
        ),
      },
      {
        accessorKey: 'amount',
        header: 'Amount',
        cell: ({ row }) => (
          <span className="font-medium">{formatCurrency(row.original.amount)}</span>
        ),
      },
      {
        accessorKey: 'paymentMethod',
        header: 'Method',
        cell: ({ row }) => <Badge variant="outline">{row.original.paymentMethod}</Badge>,
      },
      {
        accessorKey: 'account',
        header: 'Account',
        cell: ({ row }) => (
          <span className="text-sm">
            {row.original.cashAccountName || row.original.bankAccountName || '-'}
          </span>
        ),
      },
      {
        accessorKey: 'allocationCount',
        header: 'Allocations',
        cell: ({ row }) => (
          <Badge variant="secondary" className="text-xs">
            {row.original.allocationCount} invoice(s)
          </Badge>
        ),
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => (
          <Badge variant={getStatusBadgeVariant(row.original.status)}>
            {getStatusLabel(row.original.status)}
          </Badge>
        ),
      },
      {
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => {
          const receipt = row.original
          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => navigate(`/accounting/receipts/${receipt.id}`)}>
                  <Eye className="mr-2 h-4 w-4" />
                  View
                </DropdownMenuItem>
                {canEdit(receipt) && (
                  <DropdownMenuItem
                    onClick={() => navigate(`/accounting/receipts/${receipt.id}/edit`)}
                  >
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                )}
                {canPost(receipt) && (
                  <DropdownMenuItem onClick={() => handlePost(receipt)}>
                    <Send className="mr-2 h-4 w-4" />
                    Post
                  </DropdownMenuItem>
                )}
                {canReverse(receipt) && (
                  <DropdownMenuItem onClick={() => handleReversalClick(receipt)}>
                    <RotateCcw className="mr-2 h-4 w-4" />
                    Reverse
                  </DropdownMenuItem>
                )}
                {canDelete(receipt) && (
                  <DropdownMenuItem
                    onClick={() => {
                      setReceiptToDelete(receipt)
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
    [navigate, hasRole, hasAnyRole, isAdmin, isChiefAccountant],
  )

  const table = useReactTable({
    data: receipts,
    columns,
    state: {
      sorting,
    },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    manualSorting: true,
    manualPagination: true,
  })

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Customer Receipts</h1>
          <p className="text-muted-foreground">
            Manage customer payment receipts and allocations
          </p>
        </div>
        <Button onClick={() => navigate('/accounting/receipts/new')}>
          <Plus className="mr-2 h-4 w-4" />
          New Receipt
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4">
        <div className="flex-1 min-w-[200px]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search receipts..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              className="pl-9"
            />
          </div>
        </div>
        <Select
          value={status}
          onValueChange={(value) => {
            setStatus(value)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <DatePicker
          value={dateFrom}
          onChange={(value) => {
            setDateFrom(value)
            setPage(0)
          }}
          placeholder="From date"
        />
        <DatePicker
          value={dateTo}
          onChange={(value) => {
            setDateTo(value)
            setPage(0)
          }}
          placeholder="To date"
        />
        <Button variant="outline" size="icon" onClick={handleRefresh}>
          <RefreshCw className="h-4 w-4" />
        </Button>
      </div>

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
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
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {columns.map((_, j) => (
                    <TableCell key={j}>
                      <Skeleton className="h-4 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : receipts.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <FileText className="h-8 w-8" />
                    <p>No receipts found</p>
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
      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalElements)} of{' '}
          {totalElements} receipts
        </div>
        <div className="flex items-center gap-2">
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
                  {size} / page
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
              <ChevronsLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setPage(page - 1)}
              disabled={page === 0}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-sm px-2">
              Page {page + 1} of {totalPages || 1}
            </span>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setPage(page + 1)}
              disabled={page >= totalPages - 1}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setPage(totalPages - 1)}
              disabled={page >= totalPages - 1}
            >
              <ChevronsRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {/* Delete Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Receipt</DialogTitle>
          </DialogHeader>
          <p>
            Are you sure you want to delete receipt <strong>{receiptToDelete?.receiptNumber}</strong>?
            This action cannot be undone.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)} disabled={deleting}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? 'Deleting...' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reversal Dialog */}
      <ReceiptReversalDialog
        receipt={receiptToReverse}
        open={reversalDialogOpen}
        onOpenChange={setReversalDialogOpen}
        onSuccess={() => {
          setReceiptToReverse(null)
          fetchReceipts()
        }}
      />
    </div>
  )
}
