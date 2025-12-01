'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Download, RefreshCw, Search } from 'lucide-react'
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
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  getTrialBalance,
  exportTrialBalance,
  type TrialBalanceResponseDTO,
} from '@/services/trialBalance'
import { periodService } from '@/services/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

export function TrialBalance() {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<TrialBalanceResponseDTO | null>(null)
  const [periods, setPeriods] = useState<AccountingPeriod[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState<string>('')
  const [currentPeriodId, setCurrentPeriodId] = useState<string>('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [exporting, setExporting] = useState(false)

  // Load periods
  useEffect(() => {
    const loadPeriods = async () => {
      try {
        console.log('🔍 [TrialBalance] Fetching open periods...')
        const openPeriods = await periodService.getOpenPeriods()
        console.log('✅ [TrialBalance] Open periods received:', openPeriods)

        // Limit to last 3 open periods as per AC
        const limitedPeriods = openPeriods.slice(0, 3)
        console.log('📋 [TrialBalance] Limited periods (first 3):', limitedPeriods)
        setPeriods(limitedPeriods)

        // Get current period and set as default
        console.log('🔍 [TrialBalance] Fetching current period...')
        const current = await periodService.getCurrentPeriod()
        console.log('✅ [TrialBalance] Current period:', current)

        if (current) {
          setCurrentPeriodId(current.id)
          // Set selected period to current if it's in the list, otherwise use first available
          const currentInList = limitedPeriods.find((p) => p.id === current.id)
          const selectedId = currentInList ? current.id : limitedPeriods[0]?.id || ''
          console.log('🎯 [TrialBalance] Selected period ID:', selectedId)
          setSelectedPeriodId(selectedId)
        } else if (limitedPeriods.length > 0) {
          const selectedId = limitedPeriods[0].id
          console.log('🎯 [TrialBalance] Selected period ID (fallback):', selectedId)
          setSelectedPeriodId(selectedId)
        } else {
          console.warn('⚠️ [TrialBalance] No periods available')
        }
      } catch (error) {
        console.error('❌ [TrialBalance] Failed to load periods:', error)
        toast.error(`Failed to load periods: ${String(error)}`)
      }
    }
    void loadPeriods()
  }, [])

  // Load trial balance data
  const loadData = useCallback(async () => {
    if (!selectedPeriodId) return

    try {
      setLoading(true)
      const result = await getTrialBalance(selectedPeriodId)
      setData(result)
      setPage(0)
    } catch (error) {
      toast.error(`Failed to load trial balance: ${String(error)}`)
    } finally {
      setLoading(false)
    }
  }, [selectedPeriodId])

  useEffect(() => {
    void loadData()
  }, [loadData])

  // Filter and paginate accounts
  const filteredAccounts = useMemo(() => {
    if (!data) return []

    return data.accounts.filter((account) => {
      const searchLower = searchTerm.toLowerCase()
      return (
        account.accountCode?.toLowerCase().includes(searchLower) ||
        account.accountName?.toLowerCase().includes(searchLower)
      )
    })
  }, [data, searchTerm])

  const paginatedAccounts = useMemo(() => {
    const start = page * pageSize
    const end = start + pageSize
    return filteredAccounts.slice(start, end)
  }, [filteredAccounts, page, pageSize])

  const totalPages = Math.ceil(filteredAccounts.length / pageSize)

  // Export to Excel
  const handleExport = useCallback(async () => {
    if (!selectedPeriodId) {
      toast.error('Please select a period')
      return
    }

    try {
      setExporting(true)
      await exportTrialBalance(selectedPeriodId)
      toast.success('Trial balance exported successfully')
    } catch (error) {
      toast.error(`Failed to export trial balance: ${String(error)}`)
    } finally {
      setExporting(false)
    }
  }, [selectedPeriodId])

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId)

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Trial Balance (S06-DN)</h1>
          <p className="text-muted-foreground mt-1">View account balances for selected period</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Report Filters</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-2">
              <Label htmlFor="period">Period</Label>
              <Select
                value={selectedPeriodId}
                onValueChange={(value) => {
                  setSelectedPeriodId(value)
                  setPage(0)
                }}
              >
                <SelectTrigger id="period">
                  <SelectValue placeholder="Select period" />
                </SelectTrigger>
                <SelectContent>
                  {periods.map((period) => (
                    <SelectItem
                      key={period.id}
                      value={period.id}
                      disabled={period.startDate && new Date(period.startDate) > new Date()}
                    >
                      {period.periodName} ({period.fiscalYear})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="search">Search</Label>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  id="search"
                  placeholder="Search by account code or name..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value)
                    setPage(0)
                  }}
                  className="pl-8"
                />
              </div>
            </div>

            <div className="flex items-end gap-2">
              <Button onClick={loadData} variant="outline" disabled={loading}>
                <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
                Refresh
              </Button>
              <Button onClick={handleExport} disabled={exporting || !selectedPeriodId}>
                <Download className="h-4 w-4 mr-2" />
                {exporting ? 'Exporting...' : 'Export Excel'}
              </Button>
            </div>
          </div>

          {selectedPeriod && (
            <div className="text-sm text-muted-foreground">
              Period: {selectedPeriod.periodName} | Date Range:{' '}
              {selectedPeriod.startDate && selectedPeriod.endDate
                ? `${new Date(selectedPeriod.startDate).toLocaleDateString('vi-VN')} - ${new Date(selectedPeriod.endDate).toLocaleDateString('vi-VN')}`
                : 'N/A'}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Account Balances</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              {[...Array(5)].map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : data ? (
            <>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[100px]">Account Code</TableHead>
                      <TableHead>Account Name</TableHead>
                      <TableHead className="text-right">Opening Dr</TableHead>
                      <TableHead className="text-right">Opening Cr</TableHead>
                      <TableHead className="text-right">Period Dr</TableHead>
                      <TableHead className="text-right">Period Cr</TableHead>
                      <TableHead className="text-right">Closing Dr</TableHead>
                      <TableHead className="text-right">Closing Cr</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedAccounts.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={8} className="text-center text-muted-foreground">
                          No accounts found
                        </TableCell>
                      </TableRow>
                    ) : (
                      <>
                        {paginatedAccounts.map((account) => (
                          <TableRow key={account.accountId}>
                            <TableCell className="font-mono">{account.accountCode}</TableCell>
                            <TableCell>{account.accountName}</TableCell>
                            <TableCell className="text-right">
                              {account.openingDebit !== 0
                                ? formatCurrency(account.openingDebit)
                                : '-'}
                            </TableCell>
                            <TableCell className="text-right">
                              {account.openingCredit !== 0
                                ? formatCurrency(account.openingCredit)
                                : '-'}
                            </TableCell>
                            <TableCell className="text-right">
                              {account.periodDebit !== 0
                                ? formatCurrency(account.periodDebit)
                                : '-'}
                            </TableCell>
                            <TableCell className="text-right">
                              {account.periodCredit !== 0
                                ? formatCurrency(account.periodCredit)
                                : '-'}
                            </TableCell>
                            <TableCell className="text-right">
                              {account.closingDebit !== 0
                                ? formatCurrency(account.closingDebit)
                                : '-'}
                            </TableCell>
                            <TableCell className="text-right">
                              {account.closingCredit !== 0
                                ? formatCurrency(account.closingCredit)
                                : '-'}
                            </TableCell>
                          </TableRow>
                        ))}
                        {/* Totals row */}
                        <TableRow className="bg-muted/50 font-bold">
                          <TableCell colSpan={2}>TOTAL</TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(data.totalOpeningDebit)}
                          </TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(data.totalOpeningCredit)}
                          </TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(data.totalPeriodDebit)}
                          </TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(data.totalPeriodCredit)}
                          </TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(data.totalClosingDebit)}
                          </TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(data.totalClosingCredit)}
                          </TableCell>
                        </TableRow>
                      </>
                    )}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              <div className="flex items-center justify-between mt-4">
                <div className="text-sm text-muted-foreground">
                  Showing {paginatedAccounts.length > 0 ? page * pageSize + 1 : 0} to{' '}
                  {Math.min((page + 1) * pageSize, filteredAccounts.length)} of{' '}
                  {filteredAccounts.length} accounts
                </div>
                <div className="flex items-center gap-2">
                  <Label htmlFor="pageSize" className="text-sm">
                    Per page:
                  </Label>
                  <Select
                    value={String(pageSize)}
                    onValueChange={(value) => {
                      setPageSize(Number(value))
                      setPage(0)
                    }}
                  >
                    <SelectTrigger id="pageSize" className="w-[80px]">
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
                  <div className="flex gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(0)}
                      disabled={page === 0}
                    >
                      First
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                    >
                      Previous
                    </Button>
                    <div className="flex items-center px-3 text-sm">
                      Page {page + 1} of {totalPages || 1}
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                    >
                      Next
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(totalPages - 1)}
                      disabled={page >= totalPages - 1}
                    >
                      Last
                    </Button>
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className="text-center text-muted-foreground py-8">
              Select a period to view trial balance
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
