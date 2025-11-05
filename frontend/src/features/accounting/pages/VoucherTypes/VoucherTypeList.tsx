import { useMemo, useState, useEffect, useCallback } from 'react'
import { Plus, Search, RefreshCw, MoreVertical, Edit, Trash2, Ban, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  getVoucherTypes,
  deleteVoucherType,
  deactivateVoucherType,
  activateVoucherType,
} from '@/services/voucherType'
import type { VoucherType, VoucherTypeQueryParams } from '@/types/voucherType'
import VoucherTypeDialog from './VoucherTypeDialog'
import DeleteVoucherTypeDialog from '@/components/voucher-type/DeleteVoucherTypeDialog'
// <CHANGE> integrate tanstack table (data-table-04 pattern)
import type { ColumnDef, ColumnFiltersState } from '@tanstack/react-table'
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  useReactTable,
} from '@tanstack/react-table'

export default function VoucherTypeList() {
  const [voucherTypes, setVoucherTypes] = useState<VoucherType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [totalElements, setTotalElements] = useState(0)

  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [selectedVoucherType, setSelectedVoucherType] = useState<VoucherType | null>(null)
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadVoucherTypes = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const params: VoucherTypeQueryParams = {}
      if (debouncedSearch.trim()) {
        params.search = debouncedSearch.trim()
      }
      const response = await getVoucherTypes(params)
      setVoucherTypes(response.data)
      setTotalElements(typeof response.total === 'number' ? response.total : response.data.length)
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to load voucher types'
      setError(errorMessage)
      toast.error('Failed to load voucher types', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }, [debouncedSearch])

  useEffect(() => {
    setPage(1)
  }, [debouncedSearch, pageSize])

  useEffect(() => {
    loadVoucherTypes()
  }, [loadVoucherTypes])

  const handleDeleteClick = (voucherType: VoucherType) => {
    setSelectedVoucherType(voucherType)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (!selectedVoucherType) return
    try {
      await deleteVoucherType(selectedVoucherType.id)
      toast.success('Voucher type deleted successfully')
      setDeleteDialogOpen(false)
      setSelectedVoucherType(null)
      await loadVoucherTypes()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to delete voucher type'
      toast.error('Failed to delete voucher type', { description: errorMessage })
    }
  }

  const handleDeactivateClick = async (voucherType: VoucherType) => {
    try {
      await deactivateVoucherType(voucherType.id)
      toast.success('Voucher type deactivated successfully')
      await loadVoucherTypes()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to deactivate voucher type'
      toast.error('Failed to deactivate voucher type', { description: errorMessage })
    }
  }

  const handleActivateClick = async (voucherType: VoucherType) => {
    try {
      await activateVoucherType(voucherType.id)
      toast.success('Voucher type activated successfully')
      await loadVoucherTypes()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to activate voucher type'
      toast.error('Failed to activate voucher type', { description: errorMessage })
    }
  }

  const handleEditClick = (voucherType: VoucherType) => {
    setSelectedVoucherType(voucherType)
    setEditDialogOpen(true)
  }

  const handleRefresh = () => {
    loadVoucherTypes()
  }

  // <CHANGE> columns for tanstack table (data-table-04 style)
  const columns = useMemo<ColumnDef<VoucherType>[]>(
    () => [
      {
        header: 'Type Code',
        accessorKey: 'typeCode',
        cell: ({ row }) => (
          <div className="font-medium">{row.getValue<string>('typeCode')}</div>
        ),
      },
      {
        header: 'Type Name',
        accessorKey: 'typeName',
      },
      {
        header: 'Status',
        accessorKey: 'status',
        cell: ({ row }) => {
          const status = row.getValue<string>('status')
          return status === 'ACTIVE' ? (
            <Badge className='rounded-full border-none bg-green-600/10 text-green-600 focus-visible:ring-green-600/20 focus-visible:outline-none dark:bg-green-400/10 dark:text-green-400 dark:focus-visible:ring-green-400/40 [a&]:hover:bg-green-600/5 dark:[a&]:hover:bg-green-400/5'>
              <span className='size-1.5 rounded-full bg-green-600 dark:bg-green-400' aria-hidden='true' />
              Active
            </Badge>
          ) : (
            <Badge className='bg-destructive/10 [a&]:hover:bg-destructive/5 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 text-destructive rounded-full border-none focus-visible:outline-none'>
              <span className='bg-destructive size-1.5 rounded-full' aria-hidden='true' />
              Inactive
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const vt = row.original
          return (
            <div className="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm">
                    <MoreVertical className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={() => handleEditClick(vt)}>
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                  {vt.status === 'ACTIVE' ? (
                    <DropdownMenuItem onClick={() => handleDeactivateClick(vt)}>
                      <Ban className="mr-2 h-4 w-4" />
                      Deactivate
                    </DropdownMenuItem>
                  ) : (
                    <DropdownMenuItem onClick={() => handleActivateClick(vt)}>
                      <Ban className="mr-2 h-4 w-4 rotate-180" />
                      Activate
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuItem variant="destructive" onClick={() => handleDeleteClick(vt)}>
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  )

  const table = useReactTable({
    data: voucherTypes,
    columns,
    state: { columnFilters },
    onColumnFiltersChange: setColumnFilters,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  })

  // <CHANGE> filter first, then paginate rows from tanstack
  const filteredRows = table.getFilteredRowModel().rows
  const totalFiltered = filteredRows.length
  const pagedRows = useMemo(
    () => filteredRows.slice((page - 1) * pageSize, (page - 1) * pageSize + pageSize),
    [filteredRows, page, pageSize]
  )

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Voucher Types</h1>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Add Voucher Type
        </Button>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by code or name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8"
          />
        </div>
        {/* <CHANGE> Group Status filter and Refresh so they stay adjacent */}
        <div className="flex items-center gap-2 shrink-0">
          <Select
            value={(table.getColumn('status')?.getFilterValue() as string) ?? 'all'}
            onValueChange={(value) => {
              table.getColumn('status')?.setFilterValue(value === 'all' ? undefined : value)
              setPage(1)
            }}
          >
            <SelectTrigger aria-label="Filter status" className="w-28">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent side="bottom" align="end">
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="INACTIVE">Inactive</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={handleRefresh} disabled={loading} className="h-9 w-9 p-0">
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading && voucherTypes.length === 0 ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : voucherTypes.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">No voucher types found</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first voucher type.'}
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-md border">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="bg-muted/50">
                  {headerGroup.headers.map((header) => (
                    <TableHead key={header.id} className="relative h-10 select-none">
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {pagedRows?.length ? (
                pagedRows.map((row) => (
                  <TableRow key={row.id} data-state={row.getIsSelected() && 'selected'}>
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
          {/* Footer controls: total, page size, pagination */}
          <div className="flex items-center justify-between border-t px-4 py-4">
            <div className="text-sm text-muted-foreground">
              Total: <strong className="text-foreground">{totalFiltered}</strong> records
            </div>
            <div className="flex items-center gap-6">
              <div className="hidden items-center gap-2 lg:flex">
                <Label htmlFor="rows-per-page" className="text-sm font-medium">
                  Number of records per page
                </Label>
                <Select
                  value={`${pageSize}`}
                  onValueChange={(value) => {
                    setPageSize(Number(value))
                    setPage(1)
                  }}
                  disabled={loading}
                >
                  <SelectTrigger size="sm" className="w-20" id="rows-per-page">
                    <SelectValue placeholder={pageSize} />
                  </SelectTrigger>
                  <SelectContent side="top">
                    {[10, 20, 30, 50, 100].map((size) => (
                      <SelectItem key={size} value={`${size}`}>
                        {size}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex items-center justify-center text-sm font-medium">
                Page {page} / {Math.max(1, Math.ceil(totalFiltered / pageSize))}
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 p-0 lg:flex"
                  onClick={() => setPage(1)}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">First page</span>
                  <ChevronsLeft className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="h-8 w-8"
                  size="icon"
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">Previous page</span>
                  <ChevronLeft className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="h-8 w-8"
                  size="icon"
                  onClick={() => setPage((p) => Math.min(Math.max(1, Math.ceil(totalFiltered / pageSize)), p + 1))}
                  disabled={page >= Math.max(1, Math.ceil(totalFiltered / pageSize)) || loading}
                >
                  <span className="sr-only">Next page</span>
                  <ChevronRight className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 lg:flex"
                  size="icon"
                  onClick={() => setPage(Math.max(1, Math.ceil(totalFiltered / pageSize)))}
                  disabled={page >= Math.max(1, Math.ceil(totalFiltered / pageSize)) || loading}
                >
                  <span className="sr-only">Last page</span>
                  <ChevronsRight className="size-4" />
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      <VoucherTypeDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        onSuccess={() => {
          setCreateDialogOpen(false)
          loadVoucherTypes()
        }}
      />

      <VoucherTypeDialog
        open={editDialogOpen}
        onClose={() => {
          setEditDialogOpen(false)
          setSelectedVoucherType(null)
        }}
        onSuccess={() => {
          setEditDialogOpen(false)
          setSelectedVoucherType(null)
          loadVoucherTypes()
        }}
        voucherTypeId={selectedVoucherType?.id}
      />

      <DeleteVoucherTypeDialog
        open={deleteDialogOpen}
        voucherType={selectedVoucherType}
        onClose={() => {
          setDeleteDialogOpen(false)
          setSelectedVoucherType(null)
        }}
        onConfirm={handleDeleteConfirm}
      />
    </div>
  )
}

//


