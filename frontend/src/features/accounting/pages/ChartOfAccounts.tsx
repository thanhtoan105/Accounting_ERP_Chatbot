import { Fragment, useState, useEffect, useCallback, useMemo } from 'react'
import { Search, RefreshCw, Loader2, X, ChevronDown, ChevronUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { getChartOfAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccountHierarchy, ChartOfAccountFilters } from '@/types/chartOfAccount'
import AccountTreeView from '@/components/account/AccountTreeView'
import AccountTable from '@/components/account/AccountTable'
import AccountDetailsModal from '@/components/account/AccountDetailsModal'

export default function ChartOfAccounts() {
  const [accounts, setAccounts] = useState<ChartOfAccountHierarchy[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [postableFilter, setPostableFilter] = useState<boolean | null>(null)
  const [selectedAccount, setSelectedAccount] = useState<ChartOfAccountHierarchy | null>(null)
  const [detailsModalOpen, setDetailsModalOpen] = useState(false)
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [totalCount, setTotalCount] = useState<number>(0)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchTerm), 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadAccounts = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const filters: ChartOfAccountFilters = {}
      if (debouncedSearch.trim()) filters.search = debouncedSearch.trim()
      if (typeFilter) filters.type = typeFilter
      if (postableFilter !== null) filters.postable = postableFilter
      const response = await getChartOfAccounts(filters)

      // Update total count from API response
      if (response.total !== undefined) {
        setTotalCount(response.total)
      }

      if (Array.isArray(response.data) && response.data.length > 0) {
        if ('children' in response.data[0]) {
          setAccounts(response.data as ChartOfAccountHierarchy[])
          // Calculate total count from hierarchy if not provided
          if (response.total === undefined) {
            const countAccounts = (accs: ChartOfAccountHierarchy[]): number => {
              let count = accs.length
              accs.forEach((acc) => {
                if (acc.children && acc.children.length > 0) {
                  count += countAccounts(acc.children)
                }
              })
              return count
            }
            setTotalCount(countAccounts(response.data as ChartOfAccountHierarchy[]))
          }
        } else {
          const flatAccounts = response.data as any[]
          const accountMap = new Map<number, ChartOfAccountHierarchy>()
          const rootAccounts: ChartOfAccountHierarchy[] = []
          flatAccounts.forEach((acc) => {
            accountMap.set(acc.id, { ...acc, children: [] })
          })
          flatAccounts.forEach((acc) => {
            const account = accountMap.get(acc.id)!
            if (acc.parentId == null) rootAccounts.push(account)
            else {
              const parent = accountMap.get(acc.parentId)
              if (parent) {
                parent.children = parent.children || []
                parent.children.push(account)
              }
            }
          })
          const sortAccounts = (accs: ChartOfAccountHierarchy[]) => {
            accs.sort((a, b) => a.orderingPosition - b.orderingPosition)
            accs.forEach((acc) => {
              if (acc.children) sortAccounts(acc.children)
            })
          }
          sortAccounts(rootAccounts)
          setAccounts(rootAccounts)
          // Set total count from flat list
          if (response.total === undefined) {
            setTotalCount(flatAccounts.length)
          }
        }
      } else {
        setAccounts([])
        setTotalCount(0)
      }
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : (err as any)?.error?.message ||
            (err as any)?.message ||
            'Failed to load chart of accounts'
      setError(message)
      setTotalCount(0)
    } finally {
      setLoading(false)
    }
  }, [debouncedSearch, typeFilter, postableFilter])

  useEffect(() => {
    loadAccounts()
  }, [loadAccounts])

  const filteredAccounts = useMemo(() => {
    // Accounts are already filtered by the API, just return them
    return accounts
  }, [accounts])

  // keep previous logic fully intact: rendering handled by AccountTable

  const handleAccountClick = (account: ChartOfAccountHierarchy) => {
    setSelectedAccount(account)
    setDetailsModalOpen(true)
  }

  const clearFilters = () => {
    setSearchTerm('')
    setTypeFilter('')
    setPostableFilter(null)
  }

  const hasActiveFilters = typeFilter || postableFilter !== null || searchTerm

  return (
    <div className="p-6 space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Chart of Accounts</h1>
        </div>
      </div>

      {/* Filters */}
      <div className="p-4 pb-2">
        <div className="flex flex-wrap items-center gap-4">
          {/* Search Input */}
          <div className="relative flex-1 min-w-[250px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground pointer-events-none" />
            <Input
              placeholder="Search by account number, name"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-9"
            />
          </div>

          {/* Account Type Select */}
          <Select
            value={typeFilter ? typeFilter : 'all'}
            onValueChange={(value) => setTypeFilter(value === 'all' ? '' : value)}
          >
            <SelectTrigger className="w-[150px]">
              <SelectValue placeholder="All Types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Types</SelectItem>
              <SelectItem value="Asset">Asset</SelectItem>
              <SelectItem value="Liability">Liability</SelectItem>
              <SelectItem value="Equity">Equity</SelectItem>
              <SelectItem value="Revenue">Revenue</SelectItem>
              <SelectItem value="Expense">Expense</SelectItem>
            </SelectContent>
          </Select>

          {/* Refresh Button */}
          <Button
            variant="outline"
            size="icon"
            onClick={loadAccounts}
            disabled={loading}
            title="Refresh"
          >
            {loading ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <RefreshCw className="size-4" />
            )}
          </Button>

          {/* Clear Filters Badge */}
          {hasActiveFilters && (
            <Badge
              variant="outline"
              className="cursor-pointer gap-1.5 py-1.5 px-2"
              onClick={clearFilters}
            >
              Clear Filters
              <X className="size-3" />
            </Badge>
          )}
        </div>
      </div>

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Loading State */}
      {loading && (
        <Card>
          <CardContent className="py-12">
            <div className="flex flex-col items-center justify-center gap-4">
              <Loader2 className="size-8 animate-spin text-muted-foreground" />
              <p className="text-sm text-muted-foreground">Loading data...</p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Accounts Table */}
      {!loading && !error && (
        <Card className="border-0 shadow-none">
          <CardContent className="pt-2 px-6 pb-6">
            {filteredAccounts.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-sm text-muted-foreground">
                  No accounts found. Please adjust the filters.
                </p>
              </div>
            ) : (
              <AccountTable
                accounts={filteredAccounts}
                onAccountClick={handleAccountClick}
                searchTerm={debouncedSearch}
                totalCount={totalCount}
              />
            )}
          </CardContent>
        </Card>
      )}

      {/* Account Details Modal */}
      {selectedAccount && (
        <AccountDetailsModal
          open={detailsModalOpen}
          onClose={() => {
            setDetailsModalOpen(false)
            setSelectedAccount(null)
          }}
          account={selectedAccount}
        />
      )}
    </div>
  )
}
