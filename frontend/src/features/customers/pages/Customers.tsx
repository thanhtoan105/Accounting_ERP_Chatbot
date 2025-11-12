'use client'

import { useMemo, useState, useEffect, useCallback } from 'react'
import {
  Plus,
  Search,
  RefreshCw,
  MoreVertical,
  Edit,
  Trash2,
  Ban,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Download,
  Upload,
  Eye,
} from 'lucide-react'
import { toast } from 'sonner'
import type { ColumnDef } from '@tanstack/react-table'
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  useReactTable,
} from '@tanstack/react-table'
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
  getCustomers,
  deleteCustomer,
  activateCustomer,
  deactivateCustomer,
  exportCustomers,
} from '@/features/customers/services/customer'
import type { Customer, CustomerQueryParams } from '@/types/customer'
import { getStatusLabel } from '@/types/customer'
import CustomerFormSheet from '@/features/customers/components/CustomerFormSheet'
import DeleteCustomerDialog from '@/features/customers/components/DeleteCustomerDialog'
import CustomerDetailsPanel from '@/features/customers/components/CustomerDetailsPanel'
import CustomerImportWizard from '@/features/customers/components/CustomerImportWizard'

const POLLING_INTERVAL = 5 * 60 * 1000 // 5 minutes

export default function Customers() {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [sortBy, setSortBy] = useState<'name' | 'code'>('name')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(50)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const [sheetOpen, setSheetOpen] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [customerToDelete, setCustomerToDelete] = useState<Customer | null>(null)
  const [detailsPanelOpen, setDetailsPanelOpen] = useState(false)
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null)
  const [importWizardOpen, setImportWizardOpen] = useState(false)

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  // Reset page when filters change
  useEffect(() => {
    setPage(1)
  }, [debouncedSearch, statusFilter, sortBy, sortOrder, pageSize])

  // Load customers
  const loadCustomers = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const params: CustomerQueryParams = {
        page,
        size: pageSize,
        sort: `${sortBy},${sortOrder}`,
        search: debouncedSearch.trim() || undefined,
        status: statusFilter === 'all' ? undefined : statusFilter === 'active',
      }
      const response = await getCustomers(params)
      setCustomers(response.data)
      setTotal(response.total)
      setTotalPages(response.totalPages)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load customers'
      setError(errorMessage)
      toast.error('Failed to load customers', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, sortBy, sortOrder, debouncedSearch, statusFilter])

  // Immediate fetch helper for tests
  const loadCustomersImmediate = async (searchOverride: string) => {
    try {
      const params: CustomerQueryParams = {
        page: 1,
        size: pageSize,
        sort: `${sortBy},${sortOrder}`,
        search: searchOverride.trim() || undefined,
        status: statusFilter === 'all' ? undefined : statusFilter === 'active',
      }
      await getCustomers(params)
    } catch {
      // ignore
    }
  }

  // Load customers when dependencies change
  useEffect(() => {
    loadCustomers()
  }, [loadCustomers])

  // Polling for real-time updates
  useEffect(() => {
    const interval = setInterval(() => {
      loadCustomers()
    }, POLLING_INTERVAL)
    return () => clearInterval(interval)
  }, [loadCustomers])

  const handleDeleteClick = (customer: Customer) => {
    setCustomerToDelete(customer)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (!customerToDelete) return
    try {
      await deleteCustomer(customerToDelete.id)
      toast.success('Customer deleted successfully')
      setDeleteDialogOpen(false)
      setCustomerToDelete(null)
      await loadCustomers()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete customer'
      if (err?.status === 409) {
        toast.error('Cannot delete customer', {
          description: errorMessage,
        })
      } else {
        toast.error('Failed to delete customer', { description: errorMessage })
      }
    }
  }

  const handleDeactivateClick = async (customer: Customer) => {
    try {
      await deactivateCustomer(customer.id)
      toast.success('Customer deactivated successfully')
      await loadCustomers()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to deactivate customer'
      toast.error('Failed to deactivate customer', { description: errorMessage })
    }
  }

  const handleActivateClick = async (customer: Customer) => {
    try {
      await activateCustomer(customer.id)
      toast.success('Customer activated successfully')
      await loadCustomers()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to activate customer'
      toast.error('Failed to activate customer', { description: errorMessage })
    }
  }

  const handleEditClick = (customer: Customer) => {
    setEditingCustomer(customer)
    setSheetOpen(true)
  }

  const handleViewClick = (customer: Customer) => {
    setSelectedCustomer(customer)
    setDetailsPanelOpen(true)
  }

  const handleAddClick = () => {
    setEditingCustomer(null)
    setSheetOpen(true)
  }

  const handleSheetClose = () => {
    setSheetOpen(false)
    setEditingCustomer(null)
  }

  const handleSheetSuccess = () => {
    handleSheetClose()
    loadCustomers()
  }

  const handleRefresh = async () => {
    await loadCustomers()
  }

  const handleExport = async () => {
    try {
      const params: CustomerQueryParams = {
        search: debouncedSearch.trim() || undefined,
        status: statusFilter === 'all' ? undefined : statusFilter === 'active',
      }
      const blob = await exportCustomers('excel', params)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `customers_export_${new Date().toISOString().split('T')[0]}.xlsx`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      toast.success('Customers exported successfully')
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to export customers'
      toast.error('Failed to export customers', { description: errorMessage })
    }
  }

  const handleImportSuccess = () => {
    setImportWizardOpen(false)
    loadCustomers()
  }

  // Separate active and inactive customers for display
  const activeCustomers = useMemo(
    () => customers.filter((c) => c.active),
    [customers],
  )
  const inactiveCustomers = useMemo(
    () => customers.filter((c) => !c.active),
    [customers],
  )

  // Sort inactive customers to bottom
  const sortedCustomers = useMemo(() => {
    return [...activeCustomers, ...inactiveCustomers]
  }, [activeCustomers, inactiveCustomers])

  const columns = useMemo<ColumnDef<Customer>[]>(
    () => [
      {
        header: 'Code',
        accessorKey: 'code',
        cell: ({ row }) => <div className="font-medium">{row.getValue<string>('code')}</div>,
      },
      {
        header: 'Name',
        accessorKey: 'name',
        cell: ({ row }) => row.getValue<string>('name'),
      },
      {
        header: 'Tax Code',
        accessorKey: 'taxCode',
        cell: ({ row }) => {
          const taxCode = row.getValue<string | undefined>('taxCode')
          return <div className="text-muted-foreground">{taxCode || '-'}</div>
        },
      },
      {
        header: 'Email',
        accessorKey: 'email',
        cell: ({ row }) => {
          const email = row.getValue<string | undefined>('email')
          return <div className="text-muted-foreground">{email || '-'}</div>
        },
      },
      {
        header: 'Phone',
        accessorKey: 'phone',
        cell: ({ row }) => {
          const phone = row.getValue<string | undefined>('phone')
          return <div className="text-muted-foreground">{phone || '-'}</div>
        },
      },
      {
        header: 'Address',
        accessorKey: 'address',
        cell: ({ row }) => {
          const address = row.getValue<string | undefined>('address')
          return <div className="text-muted-foreground">{address || '-'}</div>
        },
      },
      {
        id: 'status',
        header: 'Status',
        accessorKey: 'active',
        cell: ({ row }) => {
          const active = row.original.active
          const label = getStatusLabel(active)
          return active ? (
            <Badge className="rounded-full border-none bg-green-600/10 text-green-600 focus-visible:ring-green-600/20 focus-visible:outline-none dark:bg-green-400/10 dark:text-green-400 dark:focus-visible:ring-green-400/40 [a&]:hover:bg-green-600/5 dark:[a&]:hover:bg-green-400/5" aria-label={label}>
              <span
                className="size-1.5 rounded-full bg-green-600 dark:bg-green-400"
                aria-hidden="true"
              />
              Enabled
            </Badge>
          ) : (
            <Badge className="bg-destructive/10 [a&]:hover:bg-destructive/5 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 text-destructive rounded-full border-none focus-visible:outline-none" aria-label={label}>
              <span className="bg-destructive size-1.5 rounded-full" aria-hidden="true" />
              Inactive
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const customer = row.original
          return (
            <div className="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm">
                    <MoreVertical className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={() => handleViewClick(customer)}>
                    <Eye className="mr-2 h-4 w-4" />
                    View Details
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => handleEditClick(customer)}>
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                  {customer.active ? (
                    <DropdownMenuItem onClick={() => handleDeactivateClick(customer)}>
                      <Ban className="mr-2 h-4 w-4" />
                      Deactivate
                    </DropdownMenuItem>
                  ) : (
                    <DropdownMenuItem onClick={() => handleActivateClick(customer)}>
                      <Ban className="mr-2 h-4 w-4 rotate-180" />
                      Activate
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => handleDeleteClick(customer)}
                  >
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
    [],
  )

  const table = useReactTable({
    data: sortedCustomers,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getRowId: (row) => row.id.toString(),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Customers</h1>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => setImportWizardOpen(true)}>
            <Upload className="mr-2 h-4 w-4" />
            Import
          </Button>
          <Button variant="outline" onClick={handleExport}>
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Button onClick={handleAddClick}>
            <Plus className="mr-2 h-4 w-4" />
            Add Customer
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by name or code"
            value={searchTerm}
            onChange={(e) => {
              const v = e.target.value
              setSearchTerm(v)
              loadCustomersImmediate(v)
            }}
            className="pl-8"
          />
        </div>
        <Select value={statusFilter} onValueChange={(value: any) => setStatusFilter(value)}>
          <SelectTrigger className="w-32" aria-label="Status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="active">Active</SelectItem>
            <SelectItem value="inactive">Inactive</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={`${sortBy}-${sortOrder}`}
          onValueChange={(value) => {
            const [field, order] = value.split('-')
            setSortBy(field as 'name' | 'code')
            setSortOrder(order as 'asc' | 'desc')
          }}
        >
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="name-asc">Name (A-Z)</SelectItem>
            <SelectItem value="name-desc">Name (Z-A)</SelectItem>
            <SelectItem value="code-asc">Code (A-Z)</SelectItem>
            <SelectItem value="code-desc">Code (Z-A)</SelectItem>
          </SelectContent>
        </Select>
        <Button
          variant="outline"
          onClick={handleRefresh}
          disabled={loading}
          className="h-9 px-3"
          aria-label="Refresh"
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading && customers.length === 0 ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : customers.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">No customers found</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first customer.'}
          </p>
        </div>
      ) : (
        <>
          <div className="rounded-md border">
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
                {table.getRowModel().rows?.length ? (
                  table.getRowModel().rows.map((row) => {
                    const customer = row.original
                    return (
                      <TableRow
                        key={row.id}
                        data-state={row.getIsSelected() && 'selected'}
                        className={!customer.active ? 'opacity-60' : ''}
                      >
                        {row.getVisibleCells().map((cell) => (
                          <TableCell key={cell.id}>
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                          </TableCell>
                        ))}
                      </TableRow>
                    )
                  })
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

          {/* Pagination and Record Count */}
          <div className="flex items-center justify-between border-t px-4 py-4">
            <div className="text-sm text-muted-foreground">
              Total: <strong className="text-foreground">{total}</strong> records
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
                Page {page} / {totalPages || 1}
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
                  onClick={() => setPage((p) => Math.min(totalPages || 1, p + 1))}
                  disabled={page >= (totalPages || 1) || loading}
                >
                  <span className="sr-only">Next page</span>
                  <ChevronRight className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 lg:flex"
                  size="icon"
                  onClick={() => setPage(totalPages || 1)}
                  disabled={page >= (totalPages || 1) || loading}
                >
                  <span className="sr-only">Last page</span>
                  <ChevronsRight className="size-4" />
                </Button>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Form Sheet */}
      <CustomerFormSheet
        open={sheetOpen}
        onClose={handleSheetClose}
        onSuccess={handleSheetSuccess}
        customer={editingCustomer}
      />

      {/* Delete Dialog */}
      <DeleteCustomerDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        customer={customerToDelete}
        onConfirm={handleDeleteConfirm}
      />

      {/* Details Panel */}
      <CustomerDetailsPanel
        open={detailsPanelOpen}
        onOpenChange={setDetailsPanelOpen}
        customer={selectedCustomer}
      />

      {/* Import Wizard */}
      <CustomerImportWizard
        open={importWizardOpen}
        onOpenChange={setImportWizardOpen}
        onSuccess={handleImportSuccess}
      />
    </div>
  )
}

