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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  getBankAccounts,
  deleteBankAccount,
  activateBankAccount,
  deactivateBankAccount,
  exportBankAccounts,
} from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount, BankAccountQueryParams, AccountType } from '@/types/bankAccount'
import { getStatusLabel, getTypeLabel } from '@/types/bankAccount'
import BankAccountFormSheet from '@/features/bankaccounts/components/BankAccountFormSheet'
import DeleteBankAccountDialog from '@/features/bankaccounts/components/DeleteBankAccountDialog'

const POLLING_INTERVAL = 5 * 60 * 1000 // 5 minutes

export default function BankAccounts() {
  const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [typeFilter, setTypeFilter] = useState<'all' | AccountType>('all')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [sortBy, setSortBy] = useState<'bankName' | 'accountNumber'>('bankName')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const [sheetOpen, setSheetOpen] = useState(false)
  const [editingBankAccount, setEditingBankAccount] = useState<BankAccount | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [bankAccountToDelete, setBankAccountToDelete] = useState<BankAccount | null>(null)

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
  }, [debouncedSearch, typeFilter, statusFilter, sortBy, sortOrder, pageSize])

  // Load bank accounts
  const loadBankAccounts = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const params: BankAccountQueryParams = {
        page,
        size: pageSize,
        sort: `${sortBy},${sortOrder}`,
        search: debouncedSearch.trim() || undefined,
        type: typeFilter === 'all' ? undefined : typeFilter,
        status: statusFilter === 'all' ? undefined : statusFilter === 'active',
      }
      const response = await getBankAccounts(params)
      setBankAccounts(response.data)
      setTotal(response.total)
      setTotalPages(response.totalPages)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load bank accounts'
      setError(errorMessage)
      toast.error('Failed to load bank accounts', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, sortBy, sortOrder, debouncedSearch, typeFilter, statusFilter])

  // Load bank accounts when dependencies change
  useEffect(() => {
    loadBankAccounts()
  }, [loadBankAccounts])

  // Polling for real-time updates
  useEffect(() => {
    const interval = setInterval(() => {
      loadBankAccounts()
    }, POLLING_INTERVAL)
    return () => clearInterval(interval)
  }, [loadBankAccounts])

  const handleDeleteClick = (bankAccount: BankAccount) => {
    setBankAccountToDelete(bankAccount)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (!bankAccountToDelete) return
    try {
      await deleteBankAccount(bankAccountToDelete.id)
      toast.success('Bank account deleted successfully')
      setDeleteDialogOpen(false)
      setBankAccountToDelete(null)
      await loadBankAccounts()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete bank account'
      if (err?.status === 409) {
        toast.error('Cannot delete bank account', {
          description: errorMessage,
        })
        // Keep dialog open to show error
      } else {
        toast.error('Failed to delete bank account', { description: errorMessage })
        setDeleteDialogOpen(false)
        setBankAccountToDelete(null)
      }
    }
  }

  const handleDeactivateClick = async (bankAccount: BankAccount) => {
    try {
      await deactivateBankAccount(bankAccount.id)
      toast.success('Bank account deactivated successfully')
      await loadBankAccounts()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to deactivate bank account'
      toast.error('Failed to deactivate bank account', { description: errorMessage })
    }
  }

  const handleActivateClick = async (bankAccount: BankAccount) => {
    try {
      await activateBankAccount(bankAccount.id)
      toast.success('Bank account activated successfully')
      await loadBankAccounts()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to activate bank account'
      toast.error('Failed to activate bank account', { description: errorMessage })
    }
  }

  const handleEditClick = (bankAccount: BankAccount) => {
    setEditingBankAccount(bankAccount)
    setSheetOpen(true)
  }

  const handleAddClick = () => {
    setEditingBankAccount(null)
    setSheetOpen(true)
  }

  const handleSheetClose = () => {
    setSheetOpen(false)
    setEditingBankAccount(null)
  }

  const handleSheetSuccess = () => {
    handleSheetClose()
    loadBankAccounts()
  }

  const handleRefresh = async () => {
    await loadBankAccounts()
  }

  const handleExport = async () => {
    try {
      const params: BankAccountQueryParams = {
        search: debouncedSearch.trim() || undefined,
        type: typeFilter === 'all' ? undefined : typeFilter,
        status: statusFilter === 'all' ? undefined : statusFilter === 'active',
      }
      const blob = await exportBankAccounts('xlsx', params)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `bank_accounts_export_${new Date().toISOString().split('T')[0]}.xlsx`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      toast.success('Bank accounts exported successfully')
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to export bank accounts'
      toast.error('Failed to export bank accounts', { description: errorMessage })
    }
  }

  // Separate active and inactive bank accounts for display
  const activeBankAccounts = useMemo(
    () => bankAccounts.filter((ba) => ba.active),
    [bankAccounts],
  )
  const inactiveBankAccounts = useMemo(
    () => bankAccounts.filter((ba) => !ba.active),
    [bankAccounts],
  )

  // Sort inactive bank accounts to bottom
  const sortedBankAccounts = useMemo(() => {
    return [...activeBankAccounts, ...inactiveBankAccounts]
  }, [activeBankAccounts, inactiveBankAccounts])

  const columns = useMemo<ColumnDef<BankAccount>[]>(
    () => [
      {
        header: 'Account Number',
        accessorKey: 'accountNumber',
        cell: ({ row }) => <div className="font-medium">{row.getValue<string>('accountNumber')}</div>,
      },
      {
        header: 'Bank Name',
        accessorKey: 'bankName',
        cell: ({ row }) => row.getValue<string>('bankName'),
      },
      {
        header: 'Branch',
        accessorKey: 'branch',
        cell: ({ row }) => {
          const branch = row.getValue<string | undefined>('branch')
          return <div className="text-muted-foreground">{branch || '-'}</div>
        },
      },
      {
        header: 'Type',
        accessorKey: 'type',
        cell: ({ row }) => {
          const type = row.getValue<AccountType>('type')
          return <Badge variant="outline">{getTypeLabel(type)}</Badge>
        },
      },
      {
        header: 'Opening Balance',
        accessorKey: 'openingBalance',
        cell: ({ row }) => {
          const balance = row.getValue<number>('openingBalance')
          return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
          }).format(balance)
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
            <Badge className="rounded-full border-none bg-green-600/10 text-green-600 focus-visible:ring-green-600/20 focus-visible:outline-none dark:bg-green-400/10 dark:text-green-400 dark:focus-visible:ring-green-400/40 [a&]:hover:bg-green-600/5 dark:[a&]:hover:bg-green-400/5">
              <span
                className="size-1.5 rounded-full bg-green-600 dark:bg-green-400"
                aria-hidden="true"
              />
              {label}
            </Badge>
          ) : (
            <Badge className="bg-destructive/10 [a&]:hover:bg-destructive/5 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 text-destructive rounded-full border-none focus-visible:outline-none">
              <span className="bg-destructive size-1.5 rounded-full" aria-hidden="true" />
              {label}
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const bankAccount = row.original
          return (
            <div className="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm">
                    <MoreVertical className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={() => handleEditClick(bankAccount)}>
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                  {bankAccount.active ? (
                    <DropdownMenuItem onClick={() => handleDeactivateClick(bankAccount)}>
                      <Ban className="mr-2 h-4 w-4" />
                      Deactivate
                    </DropdownMenuItem>
                  ) : (
                    <DropdownMenuItem onClick={() => handleActivateClick(bankAccount)}>
                      <Ban className="mr-2 h-4 w-4 rotate-180" />
                      Activate
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => handleDeleteClick(bankAccount)}
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
    data: sortedBankAccounts,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getRowId: (row) => row.id.toString(),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Bank Accounts</h1>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleExport}>
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Button onClick={handleAddClick}>
            <Plus className="mr-2 h-4 w-4" />
            Add
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by account number or bank name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8"
          />
        </div>
        <Select value={typeFilter} onValueChange={(value: any) => setTypeFilter(value)}>
          <SelectTrigger
            className="w-32"
            aria-label={typeFilter === 'all' ? 'All Types' : typeFilter === 'CASH' ? 'Cash' : 'Bank'}
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Types</SelectItem>
            <SelectItem value="CASH">Cash</SelectItem>
            <SelectItem value="BANK">Bank</SelectItem>
          </SelectContent>
        </Select>
        <Select value={statusFilter} onValueChange={(value: any) => setStatusFilter(value)}>
          <SelectTrigger
            className="w-32"
            aria-label={statusFilter === 'all' ? 'All Status' : statusFilter === 'active' ? 'Active' : 'Inactive'}
          >
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
            setSortBy(field as 'bankName' | 'accountNumber')
            setSortOrder(order as 'asc' | 'desc')
          }}
        >
          <SelectTrigger className="w-40" aria-label="Sort">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="bankName-asc">Bank Name (A-Z)</SelectItem>
            <SelectItem value="bankName-desc">Bank Name (Z-A)</SelectItem>
            <SelectItem value="accountNumber-asc">Account # (A-Z)</SelectItem>
            <SelectItem value="accountNumber-desc">Account # (Z-A)</SelectItem>
          </SelectContent>
        </Select>
        <Button
          variant="outline"
          onClick={handleRefresh}
          disabled={loading}
          className="h-9 w-9 p-0"
          aria-label="Refresh"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading && bankAccounts.length === 0 ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : bankAccounts.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">No bank accounts found</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first bank account.'}
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
                    const bankAccount = row.original
                    return (
                      <TableRow
                        key={row.id}
                        className={!bankAccount.active ? 'opacity-60' : ''}
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

          {/* Pagination */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <p className="text-sm text-muted-foreground">
                Showing {bankAccounts.length} of {total} bank accounts
              </p>
              <Select
                value={String(pageSize)}
                onValueChange={(value) => {
                  setPageSize(Number(value))
                  setPage(1)
                }}
              >
                <SelectTrigger className="w-20">
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
              <span className="text-sm text-muted-foreground">per page</span>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(1)}
                disabled={page === 1}
              >
                <ChevronsLeft className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page - 1)}
                disabled={page === 1}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm">
                Page {page} of {totalPages || 1}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page + 1)}
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

      <BankAccountFormSheet
        open={sheetOpen}
        onClose={handleSheetClose}
        onSuccess={handleSheetSuccess}
        bankAccount={editingBankAccount}
      />

      <DeleteBankAccountDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        bankAccount={bankAccountToDelete}
        onConfirm={handleDeleteConfirm}
      />
    </div>
  )
}

