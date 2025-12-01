'use client'

import { Fragment, useMemo, useState, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
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
  ChevronDown,
  ChevronUp,
} from 'lucide-react'
import { toast } from 'sonner'
import type { ColumnDef } from '@tanstack/react-table'
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getExpandedRowModel,
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
  getChartOfAccounts,
  deleteChartOfAccount,
  activateChartOfAccount,
  deactivateChartOfAccount,
} from '@/services/chartOfAccounts'
import type { ChartOfAccount, ChartOfAccountQueryParams } from '@/types/chartOfAccount'
import { getAccountTypeLabel, getStatusLabel } from '@/types/chartOfAccount'
import ChartOfAccountFormSheet from '@/components/account/ChartOfAccountFormSheet'

type ChartOfAccountWithChildren = ChartOfAccount & {
  children?: ChartOfAccount[]
}

export default function ChartOfAccounts() {
  const { t } = useTranslation()
  const [accounts, setAccounts] = useState<ChartOfAccountWithChildren[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [childrenCache, setChildrenCache] = useState<Record<number, ChartOfAccount[]>>({})
  const [loadingChildren, setLoadingChildren] = useState<Record<number, boolean>>({})

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const [sheetOpen, setSheetOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState<ChartOfAccount | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [accountToDelete, setAccountToDelete] = useState<ChartOfAccount | null>(null)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadChildren = useCallback(async (parentId: number): Promise<ChartOfAccount[]> => {
    // Use functional updates to check cache synchronously
    let cached: ChartOfAccount[] | undefined
    setChildrenCache((prev) => {
      cached = prev[parentId]
      return prev
    })

    if (cached) {
      return cached
    }

    // Check if already loading
    let isLoading = false
    setLoadingChildren((prev) => {
      isLoading = !!prev[parentId]
      return prev
    })

    if (isLoading) {
      return []
    }

    try {
      setLoadingChildren((prev) => ({ ...prev, [parentId]: true }))
      const response = await getChartOfAccounts({ parentId })
      // When querying by parentId, response.data is always ChartOfAccount[]
      const children = Array.isArray(response.data) ? (response.data as ChartOfAccount[]) : []
      setChildrenCache((prev) => ({ ...prev, [parentId]: children }))
      return children
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load child accounts'
      toast.error('Failed to load child accounts', { description: errorMessage })
      return []
    } finally {
      setLoadingChildren((prev) => {
        const next = { ...prev }
        delete next[parentId]
        return next
      })
    }
  }, [])

  const loadAccounts = useCallback(
    async (preserveExpanded = false, getExpandedIds?: () => number[]) => {
      try {
        setLoading(true)
        setError(null)
        const params: ChartOfAccountQueryParams = {}
        if (debouncedSearch.trim()) {
          params.search = debouncedSearch.trim()
        }
        // Load only root accounts (no parentId) when not searching
        if (!debouncedSearch.trim()) {
          params.parentId = undefined // Explicitly load root accounts
        }
        const response = await getChartOfAccounts(params)
        // When not using hierarchy endpoint, response.data is ChartOfAccount[]
        const accountsData = Array.isArray(response.data) ? (response.data as ChartOfAccount[]) : []

        // Filter to root accounts and add children property
        const allAccounts: ChartOfAccountWithChildren[] = accountsData.map((account) => {
          // Debug logging to check active field from API
          if (
            process.env.NODE_ENV === 'development' &&
            accountsData.length > 0 &&
            accountsData.indexOf(account) < 3
          ) {
            console.log('[ChartOfAccounts] API Response Account:', {
              id: account.id,
              code: account.code,
              name: account.name,
              activeRaw: account.active,
              activeType: typeof account.active,
            })
          }
          return {
            ...account,
            children: undefined, // Will be loaded on demand
          }
        })

        // If searching, show all accounts. Otherwise, show only root accounts (parentId is null)
        const rootAccounts = debouncedSearch.trim()
          ? allAccounts
          : allAccounts.filter((account) => !account.parentId)

        setAccounts(rootAccounts)

        // Only clear children cache if not preserving expanded state
        if (!preserveExpanded) {
          setChildrenCache({})
          setExpanded({})
        } else if (getExpandedIds) {
          // If preserving expanded state, reload children for expanded parents
          const expandedParentIds = getExpandedIds()

          // Reload children for expanded parents
          for (const parentId of expandedParentIds) {
            const children = await loadChildren(parentId)
            // Update the account in the list with children
            setAccounts((prev) =>
              prev.map((acc) => (acc.id === parentId ? { ...acc, children } : acc)),
            )
          }

          // Restore expanded state after reloading children
          // Use account IDs as row IDs (strings)
          const expandedState: Record<string, boolean> = {}
          expandedParentIds.forEach((parentId) => {
            const account = rootAccounts.find((acc) => acc.id === parentId)
            if (account) {
              // Row ID is account ID as string
              expandedState[parentId.toString()] = true
            }
          })
          setExpanded(expandedState)
        }
      } catch (err: any) {
        const errorMessage = err?.error?.message || err?.message || 'Failed to load accounts'
        setError(errorMessage)
        toast.error('Failed to load accounts', { description: errorMessage })
      } finally {
        setLoading(false)
      }
    },
    [debouncedSearch, loadChildren],
  )

  useEffect(() => {
    setPage(1)
  }, [debouncedSearch, pageSize])

  useEffect(() => {
    loadAccounts()
  }, [loadAccounts])

  const handleDeleteClick = (account: ChartOfAccount) => {
    setAccountToDelete(account)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (!accountToDelete) return
    try {
      await deleteChartOfAccount(accountToDelete.id)
      toast.success('Account deleted successfully')
      setDeleteDialogOpen(false)
      setAccountToDelete(null)
      await loadAccounts()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete account'
      toast.error('Failed to delete account', { description: errorMessage })
    }
  }

  const handleDeactivateClick = async (account: ChartOfAccount) => {
    try {
      await deactivateChartOfAccount(account.id)
      toast.success('Account deactivated successfully')

      // Capture current account statuses before reload to preserve all updates
      const currentStatuses = new Map<number, boolean>()
      setAccounts((prev) => {
        prev.forEach((acc) => {
          currentStatuses.set(acc.id, acc.active)
        })
        return prev.map((acc) => (acc.id === account.id ? { ...acc, active: false } : acc))
      })
      currentStatuses.set(account.id, false)

      // If this is a child account, refresh its parent's children cache
      if (account.parentId) {
        setChildrenCache((prev) => {
          const next = { ...prev }
          if (next[account.parentId!]) {
            // Update the child in the cached children
            next[account.parentId!] = next[account.parentId!].map((child) =>
              child.id === account.id ? { ...child, active: false } : child,
            )
          }
          return next
        })
        // Also update in the accounts list if the parent has children loaded
        setAccounts((prev) =>
          prev.map((acc) => {
            if (acc.id === account.parentId && acc.children) {
              return {
                ...acc,
                children: acc.children.map((child) =>
                  child.id === account.id ? { ...child, active: false } : child,
                ),
              }
            }
            return acc
          }),
        )
      } else {
        // If this is a parent account, clear its children cache to force reload
        setChildrenCache((prev) => {
          const next = { ...prev }
          delete next[account.id]
          return next
        })
      }

      // Get expanded parent IDs before reloading
      const expandedParentIds = Object.keys(expanded)
        .filter((key) => expanded[key])
        .map((key) => Number(key))
        .filter((id) => !isNaN(id))

      // Reload accounts to ensure consistency, preserving expanded state
      await loadAccounts(true, () => expandedParentIds)

      // After reload, restore all account statuses that were updated
      setAccounts((prev) =>
        prev.map((acc) => {
          const savedStatus = currentStatuses.get(acc.id)
          if (savedStatus !== undefined) {
            return { ...acc, active: savedStatus }
          }
          return acc
        }),
      )
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to deactivate account'
      toast.error('Failed to deactivate account', { description: errorMessage })
    }
  }

  const handleActivateClick = async (account: ChartOfAccount) => {
    try {
      await activateChartOfAccount(account.id)
      toast.success('Account activated successfully')

      // Capture current account statuses before reload to preserve all updates
      const currentStatuses = new Map<number, boolean>()
      setAccounts((prev) => {
        prev.forEach((acc) => {
          currentStatuses.set(acc.id, acc.active)
        })
        return prev.map((acc) => (acc.id === account.id ? { ...acc, active: true } : acc))
      })
      currentStatuses.set(account.id, true)

      // If this is a child account, refresh its parent's children cache
      if (account.parentId) {
        setChildrenCache((prev) => {
          const next = { ...prev }
          if (next[account.parentId!]) {
            // Update the child in the cached children
            next[account.parentId!] = next[account.parentId!].map((child) =>
              child.id === account.id ? { ...child, active: true } : child,
            )
          }
          return next
        })
        // Also update in the accounts list if the parent has children loaded
        setAccounts((prev) =>
          prev.map((acc) => {
            if (acc.id === account.parentId && acc.children) {
              return {
                ...acc,
                children: acc.children.map((child) =>
                  child.id === account.id ? { ...child, active: true } : child,
                ),
              }
            }
            return acc
          }),
        )
      } else {
        // If this is a parent account, clear its children cache to force reload
        setChildrenCache((prev) => {
          const next = { ...prev }
          delete next[account.id]
          return next
        })
      }

      // Get expanded parent IDs before reloading
      const expandedParentIds = Object.keys(expanded)
        .filter((key) => expanded[key])
        .map((key) => Number(key))
        .filter((id) => !isNaN(id))

      // Reload accounts to ensure consistency, preserving expanded state
      await loadAccounts(true, () => expandedParentIds)

      // After reload, restore all account statuses that were updated
      setAccounts((prev) =>
        prev.map((acc) => {
          const savedStatus = currentStatuses.get(acc.id)
          if (savedStatus !== undefined) {
            return { ...acc, active: savedStatus }
          }
          return acc
        }),
      )
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to activate account'
      toast.error('Failed to activate account', { description: errorMessage })
    }
  }

  const handleEditClick = (account: ChartOfAccount) => {
    setEditingAccount(account)
    setSheetOpen(true)
  }

  const handleAddClick = () => {
    setEditingAccount(null)
    setSheetOpen(true)
  }

  const handleSheetClose = () => {
    setSheetOpen(false)
    setEditingAccount(null)
  }

  const handleSheetSuccess = () => {
    handleSheetClose()
    loadAccounts()
  }

  const handleRefresh = async () => {
    // Get expanded parent IDs from table state (row IDs are account IDs as strings)
    const expandedParentIds = Object.keys(expanded)
      .map((key) => {
        // Row ID is the account ID as string
        const accountId = Number(key)
        return isNaN(accountId) ? undefined : accountId
      })
      .filter((id): id is number => id !== undefined)

    // Reload accounts preserving expanded state
    await loadAccounts(true, () => expandedParentIds)
  }

  const handleToggleExpand = useCallback(
    async (row: any) => {
      const account = row.original as ChartOfAccountWithChildren
      const isExpanded = row.getIsExpanded()

      if (!isExpanded && account.id) {
        // Load children when expanding
        const children = await loadChildren(account.id)
        // Update the account in the list with children
        setAccounts((prev) =>
          prev.map((acc) => (acc.id === account.id ? { ...acc, children } : acc)),
        )
      }

      // Toggle expanded state - this will update the table's expanded state
      row.toggleExpanded()
    },
    [loadChildren],
  )

  const columns = useMemo<ColumnDef<ChartOfAccountWithChildren>[]>(
    () => [
      {
        id: 'expander',
        header: () => null,
        cell: ({ row }) => {
          const account = row.original
          // Only show expander if account has children (postable = false means it has children)
          // postable = true means it's a leaf account (no children)
          const hasChildren = account.postable === false

          if (!hasChildren) {
            return null
          }

          return (
            <Button
              className="size-7 shadow-none text-muted-foreground"
              onClick={() => handleToggleExpand(row)}
              aria-expanded={row.getIsExpanded()}
              aria-label={
                row.getIsExpanded()
                  ? `Collapse details for ${account.code}`
                  : `Expand details for ${account.code}`
              }
              size="icon"
              variant="ghost"
            >
              {row.getIsExpanded() ? (
                <ChevronUp className="opacity-60" size={16} aria-hidden="true" />
              ) : (
                <ChevronDown className="opacity-60" size={16} aria-hidden="true" />
              )}
            </Button>
          )
        },
      },
      {
        header: 'Account Code',
        accessorKey: 'code',
        cell: ({ row }) => <div className="font-medium">{row.getValue<string>('code')}</div>,
      },
      {
        header: 'Account Name',
        accessorKey: 'name',
        cell: ({ row }) => row.getValue<string>('name'),
      },
      {
        header: 'Account Type',
        accessorKey: 'normalSide',
        cell: ({ row }) => {
          const normalSide = row.getValue<string>('normalSide')
          const label = getAccountTypeLabel(normalSide)
          return (
            <Badge variant="outline" className="font-normal">
              {label}
            </Badge>
          )
        },
      },
      {
        header: 'Account Name in English',
        accessorKey: 'nameEnglish',
        cell: ({ row }) => {
          const nameEnglish = row.getValue<string | null>('nameEnglish')
          return <div className="text-muted-foreground">{nameEnglish || '-'}</div>
        },
      },
      {
        header: 'Description',
        accessorKey: 'description',
        cell: ({ row }) => {
          const description = row.getValue<string | null>('description')
          return <div className="text-muted-foreground">{description || '-'}</div>
        },
      },
      {
        id: 'status',
        header: 'Status',
        accessorKey: 'active',
        cell: ({ row }) => {
          // Normalize active value - handle boolean, string, null, undefined
          const activeValue = row.original.active
          let active: boolean
          if (typeof activeValue === 'boolean') {
            active = activeValue
          } else if (activeValue === 'true' || activeValue === 1 || activeValue === '1') {
            active = true
          } else {
            active = false // Handles false, null, undefined, 'false', '0', etc.
          }

          // Debug logging to help identify the issue
          if (process.env.NODE_ENV === 'development') {
            console.log('[ChartOfAccounts] Account:', {
              id: row.original.id,
              code: row.original.code,
              activeRaw: activeValue,
              activeType: typeof activeValue,
              activeNormalized: active,
            })
          }

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
                  {account.active ? (
                    <DropdownMenuItem onClick={() => handleDeactivateClick(account)}>
                      <Ban className="mr-2 h-4 w-4" />
                      Deactivate
                    </DropdownMenuItem>
                  ) : (
                    <DropdownMenuItem onClick={() => handleActivateClick(account)}>
                      <Ban className="mr-2 h-4 w-4 rotate-180" />
                      Activate
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => handleDeleteClick(account)}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Soft Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )
        },
      },
    ],
    [handleToggleExpand],
  )

  const handleExpandedChange = useCallback((updaterOrValue: any) => {
    if (typeof updaterOrValue === 'function') {
      setExpanded(updaterOrValue)
    } else {
      setExpanded(updaterOrValue)
    }
  }, [])

  const table = useReactTable({
    data: accounts,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowId: (row) => row.id.toString(),
    getRowCanExpand: (row) => {
      const account = row.original
      // Only allow expansion if account has children (postable = false means it has children)
      return account.postable === false
    },
    state: {
      expanded,
    },
    onExpandedChange: handleExpandedChange,
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
        <h1 className="text-3xl font-bold tracking-tight">{t('accounts.chartOfAccounts')}</h1>
        <Button onClick={handleAddClick}>
          <Plus className="mr-2 h-4 w-4" />
          {t('accounts.addAccount')}
        </Button>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t('vouchers.searchByCodeOrName')}
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

      {loading && accounts.length === 0 ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : accounts.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">{t('accounts.noAccountsFound')}</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first account.'}
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
                  pagedRows.map((row) => {
                    const account = row.original
                    const children = account.children || childrenCache[account.id] || []
                    const isLoading = loadingChildren[account.id]

                    return (
                      <Fragment key={row.id}>
                        <TableRow data-state={row.getIsSelected() && 'selected'}>
                          {row.getVisibleCells().map((cell) => (
                            <TableCell
                              key={cell.id}
                              className="[&:has([aria-expanded])]:w-px [&:has([aria-expanded])]:py-0"
                            >
                              {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </TableCell>
                          ))}
                        </TableRow>
                        {row.getIsExpanded() && (
                          <TableRow className="hover:bg-transparent">
                            <TableCell colSpan={row.getVisibleCells().length} className="p-0">
                              {isLoading ? (
                                <div className="p-4 text-center text-muted-foreground">
                                  {t('accounts.loadingChildren')}
                                </div>
                              ) : children.length > 0 ? (
                                <div className="bg-muted/30">
                                  <Table>
                                    <TableBody>
                                      {children.map((child) => (
                                        <TableRow
                                          key={child.id}
                                          className="border-b border-border/50"
                                        >
                                          {/* Expander column - empty to match parent structure */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap w-12"></TableCell>
                                          {/* Account Code - only this column content should be indented */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            <div className="font-medium pl-8">{child.code}</div>
                                          </TableCell>
                                          {/* Account Name - align with parent (no indentation, same padding) */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            {child.name}
                                          </TableCell>
                                          {/* Account Type - align with parent (no indentation, same padding) */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            <Badge variant="outline" className="font-normal">
                                              {getAccountTypeLabel(child.normalSide)}
                                            </Badge>
                                          </TableCell>
                                          {/* Account Name in English - align with parent (no indentation, same padding) */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            <div className="text-muted-foreground">
                                              {child.nameEnglish || '-'}
                                            </div>
                                          </TableCell>
                                          {/* Description - align with parent (no indentation, same padding) */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            <div className="text-muted-foreground">
                                              {child.description || '-'}
                                            </div>
                                          </TableCell>
                                          {/* Status - align with parent (no indentation, same padding) */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            {(() => {
                                              // Normalize active value for child accounts
                                              const activeValue = child.active
                                              let active: boolean
                                              if (typeof activeValue === 'boolean') {
                                                active = activeValue
                                              } else if (
                                                activeValue === 'true' ||
                                                activeValue === 1 ||
                                                activeValue === '1'
                                              ) {
                                                active = true
                                              } else {
                                                active = false // Handles false, null, undefined, 'false', '0', etc.
                                              }

                                              return active ? (
                                                <Badge className="rounded-full border-none bg-green-600/10 text-green-600 focus-visible:ring-green-600/20 focus-visible:outline-none dark:bg-green-400/10 dark:text-green-400 dark:focus-visible:ring-green-400/40 [a&]:hover:bg-green-600/5 dark:[a&]:hover:bg-green-400/5">
                                                  <span
                                                    className="size-1.5 rounded-full bg-green-600 dark:bg-green-400"
                                                    aria-hidden="true"
                                                  />
                                                  {getStatusLabel(active)}
                                                </Badge>
                                              ) : (
                                                <Badge className="bg-destructive/10 [a&]:hover:bg-destructive/5 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 text-destructive rounded-full border-none focus-visible:outline-none">
                                                  <span
                                                    className="bg-destructive size-1.5 rounded-full"
                                                    aria-hidden="true"
                                                  />
                                                  {getStatusLabel(active)}
                                                </Badge>
                                              )
                                            })()}
                                          </TableCell>
                                          {/* Actions - align with parent (no indentation, same padding) */}
                                          <TableCell className="p-2 align-middle whitespace-nowrap">
                                            <div className="text-right">
                                              <DropdownMenu>
                                                <DropdownMenuTrigger asChild>
                                                  <Button variant="ghost" size="sm">
                                                    <MoreVertical className="h-4 w-4" />
                                                  </Button>
                                                </DropdownMenuTrigger>
                                                <DropdownMenuContent align="end">
                                                  <DropdownMenuItem
                                                    onClick={() => handleEditClick(child)}
                                                  >
                                                    <Edit className="mr-2 h-4 w-4" />
                                                    Edit
                                                  </DropdownMenuItem>
                                                  {child.active ? (
                                                    <DropdownMenuItem
                                                      onClick={() => handleDeactivateClick(child)}
                                                    >
                                                      <Ban className="mr-2 h-4 w-4" />
                                                      Deactivate
                                                    </DropdownMenuItem>
                                                  ) : (
                                                    <DropdownMenuItem
                                                      onClick={() => handleActivateClick(child)}
                                                    >
                                                      <Ban className="mr-2 h-4 w-4 rotate-180" />
                                                      Activate
                                                    </DropdownMenuItem>
                                                  )}
                                                  <DropdownMenuItem
                                                    variant="destructive"
                                                    onClick={() => handleDeleteClick(child)}
                                                  >
                                                    <Trash2 className="mr-2 h-4 w-4" />
                                                    Soft Delete
                                                  </DropdownMenuItem>
                                                </DropdownMenuContent>
                                              </DropdownMenu>
                                            </div>
                                          </TableCell>
                                        </TableRow>
                                      ))}
                                    </TableBody>
                                  </Table>
                                </div>
                              ) : (
                                <div className="p-4 text-center text-muted-foreground">
                                  {t('accounts.noChildAccounts')}
                                </div>
                              )}
                            </TableCell>
                          </TableRow>
                        )}
                      </Fragment>
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
      {deleteDialogOpen && accountToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-background rounded-lg border p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold mb-2">Delete Account</h3>
            <p className="text-sm text-muted-foreground mb-4">
              Are you sure you want to soft delete account &quot;{accountToDelete.code} -{' '}
              {accountToDelete.name}&quot;? This will set the account status to &quot;Out of
              Use&quot;.
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

      {/* Form Sheet */}
      <ChartOfAccountFormSheet
        open={sheetOpen}
        onClose={handleSheetClose}
        onSuccess={handleSheetSuccess}
        account={editingAccount}
      />
    </div>
  )
}
