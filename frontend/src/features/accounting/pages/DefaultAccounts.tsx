'use client'

import { useMemo, useState, useEffect, useCallback } from 'react'
import {
  Plus,
  Search,
  RefreshCw,
  MoreVertical,
  Edit,
  Copy,
  Trash2,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { getDefaultAccounts, deleteDefaultAccount } from '@/services/defaultAccount'
import type { DefaultAccount, DefaultAccountQueryParams } from '@/types/defaultAccount'
import DefaultAccountDialog from './DefaultAccounts/DefaultAccountDialog'

export default function DefaultAccounts() {
  const [defaultAccounts, setDefaultAccounts] = useState<DefaultAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [_totalElements, setTotalElements] = useState(0)

  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [selectedDefaultAccount, setSelectedDefaultAccount] = useState<DefaultAccount | null>(null)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadDefaultAccounts = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const params: DefaultAccountQueryParams = {}
      if (debouncedSearch.trim()) {
        params.search = debouncedSearch.trim()
      }
      const response = await getDefaultAccounts(params)
      setDefaultAccounts(response.data)
      setTotalElements(typeof response.total === 'number' ? response.total : response.data.length)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load default accounts'
      setError(errorMessage)
      toast.error('Failed to load default accounts', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }, [debouncedSearch])

  useEffect(() => {
    setPage(1)
  }, [debouncedSearch, pageSize])

  useEffect(() => {
    loadDefaultAccounts()
  }, [loadDefaultAccounts])

  const handleDeleteClick = (defaultAccount: DefaultAccount) => {
    setSelectedDefaultAccount(defaultAccount)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (!selectedDefaultAccount) return
    try {
      await deleteDefaultAccount(selectedDefaultAccount.id)
      toast.success('Default account deleted successfully')
      setDeleteDialogOpen(false)
      setSelectedDefaultAccount(null)
      await loadDefaultAccounts()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete default account'
      toast.error('Failed to delete default account', { description: errorMessage })
    }
  }

  const handleDuplicateClick = (defaultAccount: DefaultAccount) => {
    // Client-side duplication: open dialog with pre-filled data
    setSelectedDefaultAccount({
      ...defaultAccount,
      entryName: `${defaultAccount.entryName} (Copy)`,
    })
    setCreateDialogOpen(true)
  }

  const handleEditClick = (defaultAccount: DefaultAccount) => {
    setSelectedDefaultAccount(defaultAccount)
    setEditDialogOpen(true)
  }

  const handleRefresh = () => {
    loadDefaultAccounts()
  }

  // Helper to get debit account from accountDefaults
  const getDebitAccount = (account: DefaultAccount) => {
    const debit = account.accountDefaults.find((ad) =>
      ad.columnName.toLowerCase().includes('debit'),
    )
    return debit ? `${debit.accountCode || ''} - ${debit.accountName || ''}`.trim() : '-'
  }

  // Helper to get credit account from accountDefaults
  const getCreditAccount = (account: DefaultAccount) => {
    const credit = account.accountDefaults.find((ad) =>
      ad.columnName.toLowerCase().includes('credit'),
    )
    return credit ? `${credit.accountCode || ''} - ${credit.accountName || ''}`.trim() : '-'
  }

  const columns = useMemo<ColumnDef<DefaultAccount>[]>(
    () => [
      {
        header: 'Type',
        accessorKey: 'voucherType',
        cell: ({ row }) => <div className="font-medium">{row.getValue<string>('voucherType')}</div>,
      },
      {
        header: 'Debit Account',
        id: 'debitAccount',
        cell: ({ row }) => {
          const account = row.original
          return <div className="text-sm">{getDebitAccount(account)}</div>
        },
      },
      {
        header: 'Credit Account',
        id: 'creditAccount',
        cell: ({ row }) => {
          const account = row.original
          return <div className="text-sm">{getCreditAccount(account)}</div>
        },
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const account = row.original
          return (
            <div className="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm">
                    <MoreVertical className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={() => handleEditClick(account)}>
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => handleDuplicateClick(account)}>
                    <Copy className="mr-2 h-4 w-4" />
                    Duplicate
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => handleDeleteClick(account)}
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
    data: defaultAccounts,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  })

  // Filter first, then paginate rows from tanstack
  const filteredRows = table.getFilteredRowModel().rows
  const totalFiltered = filteredRows.length
  const pagedRows = useMemo(
    () => filteredRows.slice((page - 1) * pageSize, (page - 1) * pageSize + pageSize),
    [filteredRows, page, pageSize],
  )

  const totalPages = Math.ceil(totalFiltered / pageSize)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Default Accounts</h1>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Add New
        </Button>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by type or entry name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8"
          />
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button
            variant="outline"
            onClick={handleRefresh}
            disabled={loading}
            className="h-9 w-9 p-0"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading && defaultAccounts.length === 0 ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : defaultAccounts.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">No default accounts found</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first default account.'}
          </p>
        </div>
      ) : (
        <>
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
          </div>

          {/* Pagination and Record Count */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <p className="text-sm text-muted-foreground">
                Showing {pagedRows.length > 0 ? (page - 1) * pageSize + 1 : 0} to{' '}
                {Math.min(page * pageSize, totalFiltered)} of {totalFiltered} accounts
              </p>
              <Select
                value={String(pageSize)}
                onValueChange={(value) => {
                  setPageSize(Number(value))
                  setPage(1)
                }}
              >
                <SelectTrigger className="w-24 h-8">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="20">20</SelectItem>
                  <SelectItem value="30">30</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={() => setPage(1)} disabled={page === 1}>
                <ChevronsLeft className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {page} of {totalPages || 1}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page >= totalPages}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(totalPages)}
                disabled={page >= totalPages}
              >
                <ChevronsRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </>
      )}

      {/* Delete Confirmation Dialog */}
      {deleteDialogOpen && selectedDefaultAccount && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-background rounded-lg border p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold mb-2">Delete Default Account</h3>
            <p className="text-sm text-muted-foreground mb-4">
              Are you sure you want to delete &quot;{selectedDefaultAccount.entryName}&quot;? This
              action cannot be undone.
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
                Cancel
              </Button>
              <Button variant="destructive" onClick={handleDeleteConfirm}>
                Delete
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Create Dialog */}
      <DefaultAccountDialog
        open={createDialogOpen}
        onClose={() => {
          setCreateDialogOpen(false)
          setSelectedDefaultAccount(null)
        }}
        onSuccess={() => {
          setCreateDialogOpen(false)
          setSelectedDefaultAccount(null)
          loadDefaultAccounts()
        }}
        initialData={
          selectedDefaultAccount
            ? {
                voucherType: selectedDefaultAccount.voucherType as
                  | 'Cash Payment'
                  | 'Bank Payment'
                  | 'Cash Receipt'
                  | 'Bank Receipt'
                  | 'Other Business Voucher',
                entryName: selectedDefaultAccount.entryName,
                accountDefaults: selectedDefaultAccount.accountDefaults
                  .filter((ad) => ad.defaultAccountId != null)
                  .map((ad) => ({
                    columnName: ad.columnName,
                    defaultAccountId: ad.defaultAccountId!,
                  })),
              }
            : undefined
        }
      />

      {/* Edit Dialog */}
      <DefaultAccountDialog
        open={editDialogOpen}
        onClose={() => {
          setEditDialogOpen(false)
          setSelectedDefaultAccount(null)
        }}
        onSuccess={() => {
          setEditDialogOpen(false)
          setSelectedDefaultAccount(null)
          loadDefaultAccounts()
        }}
        defaultAccountId={selectedDefaultAccount?.id}
      />
    </div>
  )
}
