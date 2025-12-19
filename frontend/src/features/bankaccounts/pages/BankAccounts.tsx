'use client'

import React, { useMemo, useState, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Plus,
  Search,
  RefreshCw,
  MoreVertical,
  Edit,
  Trash2,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Download,
  Upload,
  Banknote,
  Landmark,
  CheckCircle,
  XCircle,
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
import {
  getBankAccounts,
  deleteBankAccount,
  activateBankAccount,
  deactivateBankAccount,
  exportBankAccounts,
  importBankAccounts,
  downloadImportTemplate,
} from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount, BankAccountQueryParams, AccountType } from '@/types/bankAccount'
import BankAccountFormSheet from '@/features/bankaccounts/components/BankAccountFormSheet'
import DeleteBankAccountDialog from '@/features/bankaccounts/components/DeleteBankAccountDialog'

const POLLING_INTERVAL = 5 * 60 * 1000 // 5 minutes

export default function BankAccounts() {
  const { t } = useTranslation()
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
  const [importing, setImporting] = useState(false)
  const fileInputRef = React.useRef<HTMLInputElement>(null)

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

  const handleImportClick = () => {
    fileInputRef.current?.click()
  }

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    try {
      setImporting(true)
      const result = await importBankAccounts(file)
      toast.success(result.message)
      await loadBankAccounts()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to import bank accounts'
      toast.error('Import failed', { description: errorMessage })
    } finally {
      setImporting(false)
      // Reset file input
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  const handleDownloadTemplate = async () => {
    try {
      const blob = await downloadImportTemplate()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'bank_accounts_import_template.csv'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      toast.success('Template downloaded')
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to download template'
      toast.error('Failed to download template', { description: errorMessage })
    }
  }

  // Separate active and inactive bank accounts for display
  const activeBankAccounts = useMemo(() => bankAccounts.filter((ba) => ba.active), [bankAccounts])
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
        header: t('bankAccounts.columns.type'),
        accessorKey: 'type',
        cell: ({ row }) => {
          const type = row.getValue<AccountType>('type')
          return (
            <div className="flex items-center gap-2">
              <div
                className={`flex h-8 w-8 items-center justify-center rounded-full ${
                  type === 'CASH'
                    ? 'bg-amber-100 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400'
                    : 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400'
                }`}
              >
                {type === 'CASH' ? (
                  <Banknote className="h-4 w-4" />
                ) : (
                  <Landmark className="h-4 w-4" />
                )}
              </div>
              <span className="text-sm font-medium">
                {type === 'CASH' ? t('bankAccounts.types.cash') : t('bankAccounts.types.bank')}
              </span>
            </div>
          )
        },
      },
      {
        header: t('bankAccounts.columns.accountNumberOrCode'),
        accessorKey: 'accountNumber',
        cell: ({ row }) => {
          const type = row.original.type
          const accountNumber = row.getValue<string>('accountNumber')
          // For CASH, show shorter format if it's auto-generated
          if (type === 'CASH' && accountNumber.startsWith('CASH-')) {
            return <code className="text-xs bg-muted px-1.5 py-0.5 rounded">{accountNumber}</code>
          }
          return <div className="font-medium font-mono">{accountNumber}</div>
        },
      },
      {
        header: t('bankAccounts.columns.nameOrBank'),
        accessorKey: 'bankName',
        cell: ({ row }) => {
          const type = row.original.type
          const name = row.getValue<string>('bankName')
          return (
            <div>
              <div className="font-medium">{name}</div>
              {type === 'BANK' && row.original.branch && (
                <div className="text-xs text-muted-foreground">{row.original.branch}</div>
              )}
            </div>
          )
        },
      },
      {
        header: t('bankAccounts.columns.openingBalance'),
        accessorKey: 'openingBalance',
        cell: ({ row }) => {
          const balance = row.getValue<number>('openingBalance')
          return (
            <div className="text-right font-mono">
              {new Intl.NumberFormat('vi-VN', {
                style: 'currency',
                currency: 'VND',
              }).format(balance)}
            </div>
          )
        },
      },
      {
        header: t('bankAccounts.columns.glAccount'),
        accessorKey: 'glAccountCode',
        cell: ({ row }) => {
          const glCode = row.original.glAccountCode
          return glCode ? (
            <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">{glCode}</code>
          ) : (
            <span className="text-muted-foreground">-</span>
          )
        },
      },
      {
        id: 'status',
        header: t('bankAccounts.columns.status'),
        accessorKey: 'active',
        cell: ({ row }) => {
          const active = row.original.active
          return active ? (
            <div className="flex items-center gap-1.5 text-green-600 dark:text-green-400">
              <CheckCircle className="h-4 w-4" />
              <span className="text-sm">{t('bankAccounts.status.active')}</span>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 text-muted-foreground">
              <XCircle className="h-4 w-4" />
              <span className="text-sm">{t('bankAccounts.status.inactive')}</span>
            </div>
          )
        },
      },
      {
        id: 'actions',
        header: () => <div className="text-right"></div>,
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
                    {t('bankAccounts.actions.edit')}
                  </DropdownMenuItem>
                  {bankAccount.active ? (
                    <DropdownMenuItem onClick={() => handleDeactivateClick(bankAccount)}>
                      <XCircle className="mr-2 h-4 w-4" />
                      {t('bankAccounts.actions.deactivate')}
                    </DropdownMenuItem>
                  ) : (
                    <DropdownMenuItem onClick={() => handleActivateClick(bankAccount)}>
                      <CheckCircle className="mr-2 h-4 w-4" />
                      {t('bankAccounts.actions.activate')}
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => handleDeleteClick(bankAccount)}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t('bankAccounts.actions.delete')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )
        },
      },
    ],
    [t],
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
        <h1 className="text-3xl font-bold tracking-tight">{t('bankAccounts.title')}</h1>
        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" disabled={importing}>
                <Upload className="mr-2 h-4 w-4" />
                {importing ? 'Importing...' : t('common.import')}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={handleImportClick}>
                <Upload className="mr-2 h-4 w-4" />
                Import from file
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleDownloadTemplate}>
                <Download className="mr-2 h-4 w-4" />
                Download template
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept=".csv,.xlsx,.xls"
            className="hidden"
          />
          <Button variant="outline" onClick={handleExport}>
            <Download className="mr-2 h-4 w-4" />
            {t('common.export')}
          </Button>
          <Button onClick={handleAddClick}>
            <Plus className="mr-2 h-4 w-4" />
            {t('common.add')}
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t('bankAccounts.searchBankAccounts')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8"
          />
        </div>
        <Select value={typeFilter} onValueChange={(value: any) => setTypeFilter(value)}>
          <SelectTrigger className="w-36" aria-label={t('bankAccounts.filters.allTypes')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('bankAccounts.filters.allTypes')}</SelectItem>
            <SelectItem value="CASH">{t('bankAccounts.types.cash')}</SelectItem>
            <SelectItem value="BANK">{t('bankAccounts.types.bank')}</SelectItem>
          </SelectContent>
        </Select>
        <Select value={statusFilter} onValueChange={(value: any) => setStatusFilter(value)}>
          <SelectTrigger className="w-40" aria-label={t('bankAccounts.filters.allStatus')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('bankAccounts.filters.allStatus')}</SelectItem>
            <SelectItem value="active">{t('bankAccounts.filters.active')}</SelectItem>
            <SelectItem value="inactive">{t('bankAccounts.filters.inactive')}</SelectItem>
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
          <SelectTrigger className="w-36" aria-label="Sort">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="bankName-asc">{t('bankAccounts.sort.nameAsc')}</SelectItem>
            <SelectItem value="bankName-desc">{t('bankAccounts.sort.nameDesc')}</SelectItem>
            <SelectItem value="accountNumber-asc">{t('bankAccounts.sort.accountAsc')}</SelectItem>
            <SelectItem value="accountNumber-desc">{t('bankAccounts.sort.accountDesc')}</SelectItem>
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
          <p className="text-lg font-medium">{t('bankAccounts.emptyState.title')}</p>
          <p className="text-sm">
            {debouncedSearch
              ? t('bankAccounts.emptyState.searchHint')
              : t('bankAccounts.emptyState.createHint')}
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
                      <TableRow key={row.id} className={!bankAccount.active ? 'opacity-60' : ''}>
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
                {t('bankAccounts.pagination.showing')} {bankAccounts.length}{' '}
                {t('bankAccounts.pagination.of')} {total} {t('bankAccounts.pagination.items')}
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
              <span className="text-sm text-muted-foreground">
                {t('bankAccounts.pagination.perPage')}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={() => setPage(1)} disabled={page === 1}>
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
