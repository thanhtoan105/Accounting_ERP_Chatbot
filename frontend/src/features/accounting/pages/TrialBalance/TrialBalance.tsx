'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Download, RefreshCw, Search, FileText } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import { Alert, AlertTitle } from '@/components/ui/alert'
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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  getTrialBalance,
  exportTrialBalance,
  exportTrialBalancePdf,
  validateForExport,
  type TrialBalanceResponseDTO,
  type AmountType,
} from '@/services/trialBalance'
import { periodService } from '@/services/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import { DrillDownPanel } from './DrillDownPanel'
import { VoucherDetailModal } from './VoucherDetailModal'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const PERIOD_STORAGE_KEY = 'trialBalance_lastPeriod'

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

export function TrialBalance() {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<TrialBalanceResponseDTO | null>(null)
  const [periods, setPeriods] = useState<AccountingPeriod[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState<string>('')
  const [_currentPeriodId, setCurrentPeriodId] = useState<string>('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(50)
  const [exporting, setExporting] = useState(false)

  // Drill-down state
  const [drillDownOpen, setDrillDownOpen] = useState(false)
  const [drillDownAccount, setDrillDownAccount] = useState<{
    accountId: number
    accountCode: string
    accountName: string
    amountType: AmountType
  } | null>(null)

  // Voucher detail modal state
  const [voucherModalOpen, setVoucherModalOpen] = useState(false)
  const [selectedVoucherId, setSelectedVoucherId] = useState<string | null>(null)

  // Handle amount cell click for drill-down
  const handleAmountClick = useCallback(
    (
      accountId: number,
      accountCode: string,
      accountName: string,
      amountType: AmountType,
      value: number,
    ) => {
      // Block drill-down only for zero values in PERIOD columns (no activity)
      // Allow drill-down for OPENING/CLOSING even if 0 (might have offsetting entries)
      const isPeriodColumn = amountType === 'PERIOD_DEBIT' || amountType === 'PERIOD_CREDIT'
      if (isPeriodColumn && value === 0) return

      setDrillDownAccount({ accountId, accountCode, accountName, amountType })
      setDrillDownOpen(true)
    },
    [],
  )

  const handleDrillDownClose = useCallback(() => {
    setDrillDownOpen(false)
    setDrillDownAccount(null)
  }, [])

  // Handle voucher click from drill-down panel
  const handleVoucherClick = useCallback((voucherId: string) => {
    setSelectedVoucherId(voucherId)
    setVoucherModalOpen(true)
  }, [])

  const handleVoucherModalClose = useCallback(() => {
    setVoucherModalOpen(false)
    setSelectedVoucherId(null)
  }, [])

  // Load periods
  useEffect(() => {
    const loadPeriods = async () => {
      try {
        const openPeriods = await periodService.getOpenPeriods()

        // Limit to last 3 open periods as per AC
        const limitedPeriods = openPeriods.slice(0, 3)
        setPeriods(limitedPeriods)

        // Check for saved period in localStorage
        const savedPeriod = localStorage.getItem(PERIOD_STORAGE_KEY)
        const savedPeriodInList = savedPeriod
          ? limitedPeriods.find((p) => p.id === savedPeriod)
          : null

        // Get current period
        const current = await periodService.getCurrentPeriod()

        if (current) {
          setCurrentPeriodId(current.id)
        }

        // Priority: saved period > current period > first available
        let selectedId = ''
        if (savedPeriodInList && savedPeriod) {
          selectedId = savedPeriod
        } else if (current && limitedPeriods.find((p) => p.id === current.id)) {
          selectedId = current.id
        } else if (limitedPeriods.length > 0) {
          selectedId = limitedPeriods[0].id
        }

        setSelectedPeriodId(selectedId)
      } catch (error) {
        toast.error(t('trialBalance.errors.periodLoadFailed'))
      }
    }
    void loadPeriods()
  }, [t])

  // Handle period change with localStorage persistence
  const handlePeriodChange = useCallback((value: string) => {
    setSelectedPeriodId(value)
    localStorage.setItem(PERIOD_STORAGE_KEY, value)
    setPage(0)
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
      toast.error(t('trialBalance.errors.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [selectedPeriodId, t])

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
      toast.error(t('trialBalance.validation.selectPeriod'))
      return
    }

    try {
      setExporting(true)
      await exportTrialBalance(selectedPeriodId)
      toast.success(t('trialBalance.success.exported'))
    } catch (error) {
      toast.error(t('trialBalance.errors.exportFailed'))
    } finally {
      setExporting(false)
    }
  }, [selectedPeriodId, t])

  // Export to PDF with validation preflight
  const handleExportPdf = useCallback(async () => {
    if (!selectedPeriodId) {
      toast.error(t('trialBalance.validation.selectPeriod'))
      return
    }

    try {
      setExporting(true)

      // Validate before export
      const validation = await validateForExport(selectedPeriodId)
      if (!validation.valid) {
        const firstError = validation.errors[0]
        toast.error(firstError.message || t('trialBalance.errors.validationFailed'))
        return
      }

      await exportTrialBalancePdf(selectedPeriodId)
      toast.success(t('trialBalance.success.exportedPdf'))
    } catch (error) {
      const message = error instanceof Error ? error.message : t('trialBalance.errors.exportFailed')
      toast.error(message)
    } finally {
      setExporting(false)
    }
  }, [selectedPeriodId, t])

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId)

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('trialBalance.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('trialBalance.subtitle')}</p>
        </div>
      </div>

      {/* Balance Warning Banner */}
      {data && data.isBalanced === false && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>
            {t('trialBalance.validation.imbalanceWarning', {
              debit: formatCurrency(data.totalClosingDebit),
              credit: formatCurrency(data.totalClosingCredit),
            })}
          </AlertTitle>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t('trialBalance.reportFilters')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-2">
              <Label htmlFor="period">{t('trialBalance.filters.period')}</Label>
              <Select value={selectedPeriodId} onValueChange={handlePeriodChange}>
                <SelectTrigger id="period">
                  <SelectValue placeholder={t('trialBalance.filters.period')} />
                </SelectTrigger>
                <SelectContent>
                  {periods.map((period) => (
                    <SelectItem
                      key={period.id}
                      value={period.id}
                      disabled={Boolean(
                        period.startDate && new Date(period.startDate) > new Date(),
                      )}
                    >
                      {period.periodName} ({period.fiscalYear})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="search">{t('trialBalance.filters.search')}</Label>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  id="search"
                  placeholder={t('trialBalance.filters.searchPlaceholder')}
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
                {t('trialBalance.actions.refresh')}
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button disabled={exporting || !selectedPeriodId}>
                    <Download className="h-4 w-4 mr-2" />
                    {exporting
                      ? t('trialBalance.actions.exporting')
                      : t('trialBalance.actions.export')}
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={handleExport}>
                    <Download className="h-4 w-4 mr-2" />
                    {t('trialBalance.actions.exportExcel')}
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={handleExportPdf}>
                    <FileText className="h-4 w-4 mr-2" />
                    {t('trialBalance.actions.exportPdf')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>

          {selectedPeriod && (
            <div className="text-sm text-muted-foreground">
              {t('trialBalance.filters.period')}: {selectedPeriod.periodName} | {t('common.date')}:{' '}
              {selectedPeriod.startDate && selectedPeriod.endDate
                ? `${new Date(selectedPeriod.startDate).toLocaleDateString('vi-VN')} - ${new Date(selectedPeriod.endDate).toLocaleDateString('vi-VN')}`
                : 'N/A'}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('trialBalance.table.accountBalances')}</CardTitle>
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
                      <TableHead className="w-[100px]">
                        {t('trialBalance.table.accountCode')}
                      </TableHead>
                      <TableHead>{t('trialBalance.table.accountName')}</TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.table.openingDebit')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.table.openingCredit')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.table.periodDebit')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.table.periodCredit')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.table.closingDebit')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.table.closingCredit')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedAccounts.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={8} className="text-center text-muted-foreground">
                          {t('trialBalance.table.noAccounts')}
                        </TableCell>
                      </TableRow>
                    ) : (
                      <>
                        {paginatedAccounts.map((account) => (
                          <TableRow key={account.accountId}>
                            <TableCell className="font-mono">{account.accountCode}</TableCell>
                            <TableCell>{account.accountName}</TableCell>
                            <TableCell
                              className={`text-right ${account.openingDebit !== 0 ? 'cursor-pointer hover:bg-muted/50 hover:text-blue-600' : ''}`}
                              onClick={() =>
                                handleAmountClick(
                                  account.accountId,
                                  account.accountCode,
                                  account.accountName,
                                  'OPENING_DEBIT',
                                  account.openingDebit,
                                )
                              }
                            >
                              {account.openingDebit !== 0
                                ? formatCurrency(account.openingDebit)
                                : '-'}
                            </TableCell>
                            <TableCell
                              className={`text-right ${account.openingCredit !== 0 ? 'cursor-pointer hover:bg-muted/50 hover:text-blue-600' : ''}`}
                              onClick={() =>
                                handleAmountClick(
                                  account.accountId,
                                  account.accountCode,
                                  account.accountName,
                                  'OPENING_CREDIT',
                                  account.openingCredit,
                                )
                              }
                            >
                              {account.openingCredit !== 0
                                ? formatCurrency(account.openingCredit)
                                : '-'}
                            </TableCell>
                            <TableCell
                              className={`text-right ${account.periodDebit !== 0 ? 'cursor-pointer hover:bg-muted/50 hover:text-blue-600' : ''}`}
                              onClick={() =>
                                handleAmountClick(
                                  account.accountId,
                                  account.accountCode,
                                  account.accountName,
                                  'PERIOD_DEBIT',
                                  account.periodDebit,
                                )
                              }
                            >
                              {account.periodDebit !== 0
                                ? formatCurrency(account.periodDebit)
                                : '-'}
                            </TableCell>
                            <TableCell
                              className={`text-right ${account.periodCredit !== 0 ? 'cursor-pointer hover:bg-muted/50 hover:text-blue-600' : ''}`}
                              onClick={() =>
                                handleAmountClick(
                                  account.accountId,
                                  account.accountCode,
                                  account.accountName,
                                  'PERIOD_CREDIT',
                                  account.periodCredit,
                                )
                              }
                            >
                              {account.periodCredit !== 0
                                ? formatCurrency(account.periodCredit)
                                : '-'}
                            </TableCell>
                            <TableCell
                              className={`text-right ${account.closingDebit !== 0 ? 'cursor-pointer hover:bg-muted/50 hover:text-blue-600' : ''}`}
                              onClick={() =>
                                handleAmountClick(
                                  account.accountId,
                                  account.accountCode,
                                  account.accountName,
                                  'CLOSING_DEBIT',
                                  account.closingDebit,
                                )
                              }
                            >
                              {account.closingDebit !== 0
                                ? formatCurrency(account.closingDebit)
                                : '-'}
                            </TableCell>
                            <TableCell
                              className={`text-right ${account.closingCredit !== 0 ? 'cursor-pointer hover:bg-muted/50 hover:text-blue-600' : ''}`}
                              onClick={() =>
                                handleAmountClick(
                                  account.accountId,
                                  account.accountCode,
                                  account.accountName,
                                  'CLOSING_CREDIT',
                                  account.closingCredit,
                                )
                              }
                            >
                              {account.closingCredit !== 0
                                ? formatCurrency(account.closingCredit)
                                : '-'}
                            </TableCell>
                          </TableRow>
                        ))}
                        {/* Totals row */}
                        <TableRow className="bg-muted/50 font-bold">
                          <TableCell colSpan={2}>{t('trialBalance.table.total')}</TableCell>
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
                  {t('trialBalance.pagination.showing')}{' '}
                  {paginatedAccounts.length > 0 ? page * pageSize + 1 : 0}{' '}
                  {t('trialBalance.pagination.to')}{' '}
                  {Math.min((page + 1) * pageSize, filteredAccounts.length)}{' '}
                  {t('trialBalance.pagination.of')} {filteredAccounts.length}{' '}
                  {t('trialBalance.pagination.accounts')}
                </div>
                <div className="flex items-center gap-2">
                  <Label htmlFor="pageSize" className="text-sm">
                    {t('trialBalance.pagination.perPage')}:
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
                      {t('trialBalance.pagination.first')}
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                    >
                      {t('trialBalance.pagination.previous')}
                    </Button>
                    <div className="flex items-center px-3 text-sm">
                      {t('trialBalance.pagination.page')} {page + 1}{' '}
                      {t('trialBalance.pagination.of')} {totalPages || 1}
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                    >
                      {t('trialBalance.pagination.next')}
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(totalPages - 1)}
                      disabled={page >= totalPages - 1}
                    >
                      {t('trialBalance.pagination.last')}
                    </Button>
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className="text-center text-muted-foreground py-8">
              {t('trialBalance.validation.selectPeriod')}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Drill-Down Panel */}
      {drillDownAccount && (
        <DrillDownPanel
          open={drillDownOpen}
          onClose={handleDrillDownClose}
          periodId={selectedPeriodId}
          accountId={drillDownAccount.accountId}
          accountCode={drillDownAccount.accountCode}
          accountName={drillDownAccount.accountName}
          amountType={drillDownAccount.amountType}
          onVoucherClick={handleVoucherClick}
        />
      )}

      {/* Voucher Detail Modal */}
      <VoucherDetailModal
        open={voucherModalOpen}
        onClose={handleVoucherModalClose}
        voucherId={selectedVoucherId}
        breadcrumb={
          drillDownAccount
            ? {
                accountCode: drillDownAccount.accountCode,
                accountName: drillDownAccount.accountName,
                amountType: drillDownAccount.amountType,
              }
            : undefined
        }
      />
    </div>
  )
}
