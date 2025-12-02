'use client'

import { useCallback, useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  ArrowLeft,
  Upload,
  Wand2,
  Link2,
  Link2Off,
  Plus,
  Download,
  CheckCircle2,
  RotateCcw,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import { toast } from 'sonner'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Progress } from '@/components/ui/progress'
import {
  getReconciliation,
  getStatementLines,
  getLedgerTransactions,
  runAutoMatch,
  manualMatch,
  unmatchLine,
  completeReconciliation,
  reopenReconciliation,
  exportToExcel,
  exportToPdf,
  downloadBlob,
  type BankReconciliationDTO,
  type BankStatementLineDTO,
  type LedgerTransactionDTO,
  type AutoMatchResult,
  type ReconciliationStatus,
  type MatchStatus,
} from '../../services/bankReconciliation'
import { StatementImportDialog } from './StatementImportDialog'
import { CreateAdjustmentDialog } from './CreateAdjustmentDialog'

const PAGE_SIZE = 20

function formatCurrency(value: number | undefined | null): string {
  if (value === undefined || value === null) return '-'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

function formatDate(dateString: string | undefined | null): string {
  if (!dateString) return '-'
  try {
    return format(new Date(dateString), 'dd/MM/yyyy')
  } catch {
    return dateString
  }
}

function formatDateTime(dateString: string | undefined | null): string {
  if (!dateString) return '-'
  try {
    return format(new Date(dateString), 'dd/MM/yyyy HH:mm')
  } catch {
    return dateString
  }
}

function getStatusBadgeVariant(
  status: ReconciliationStatus,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'COMPLETED':
      return 'default'
    case 'IN_PROGRESS':
      return 'secondary'
    case 'NOT_STARTED':
    default:
      return 'outline'
  }
}

function getMatchStatusBadgeVariant(
  status: MatchStatus,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'MATCHED':
      return 'default'
    case 'ADJUSTMENT_REQUIRED':
      return 'secondary'
    case 'UNMATCHED':
    default:
      return 'destructive'
  }
}

export function ReconciliationDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  // Main reconciliation data
  const [loading, setLoading] = useState(true)
  const [reconciliation, setReconciliation] = useState<BankReconciliationDTO | null>(null)

  // Statement lines
  const [statementLines, setStatementLines] = useState<BankStatementLineDTO[]>([])
  const [statementLinesLoading, setStatementLinesLoading] = useState(false)
  const [statementLinesPage, setStatementLinesPage] = useState(0)
  const [statementLinesTotalPages, setStatementLinesTotalPages] = useState(0)
  const [statementLineFilter, setStatementLineFilter] = useState<MatchStatus | ''>('')

  // Ledger transactions
  const [ledgerTransactions, setLedgerTransactions] = useState<LedgerTransactionDTO[]>([])
  const [ledgerLoading, setLedgerLoading] = useState(false)
  const [ledgerPage, setLedgerPage] = useState(0)
  const [ledgerTotalPages, setLedgerTotalPages] = useState(0)

  // Selection for matching
  const [selectedStatementLine, setSelectedStatementLine] = useState<BankStatementLineDTO | null>(
    null,
  )
  const [selectedLedgerTransaction, setSelectedLedgerTransaction] =
    useState<LedgerTransactionDTO | null>(null)

  // Actions state
  const [matching, setMatching] = useState(false)
  const [autoMatching, setAutoMatching] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [exporting, setExporting] = useState(false)

  // Auto-match result dialog
  const [autoMatchResult, setAutoMatchResult] = useState<AutoMatchResult | null>(null)
  const [showAutoMatchResult, setShowAutoMatchResult] = useState(false)

  // Completion confirmation dialog
  const [showCompleteDialog, setShowCompleteDialog] = useState(false)

  // Import dialog placeholder (will be a separate component)
  const [showImportDialog, setShowImportDialog] = useState(false)

  // Adjustment dialog state
  const [showAdjustmentDialog, setShowAdjustmentDialog] = useState(false)
  const [adjustmentStatementLine, setAdjustmentStatementLine] = useState<BankStatementLineDTO | null>(null)

  // Load reconciliation detail
  const loadReconciliation = useCallback(async () => {
    if (!id) return
    try {
      setLoading(true)
      const data = await getReconciliation(id)
      setReconciliation(data)
    } catch (error) {
      console.error('Failed to load reconciliation:', error)
      toast.error(t('errors.generic'))
      navigate('/accounting/bank-reconciliation')
    } finally {
      setLoading(false)
    }
  }, [id, t, navigate])

  // Load statement lines
  const loadStatementLines = useCallback(async () => {
    if (!id) return
    try {
      setStatementLinesLoading(true)
      const result = await getStatementLines(id, {
        matchStatus: statementLineFilter || undefined,
        page: statementLinesPage,
        size: PAGE_SIZE,
      })
      setStatementLines(result.data)
      setStatementLinesTotalPages(result.meta.totalPages)
    } catch (error) {
      console.error('Failed to load statement lines:', error)
      toast.error(t('errors.generic'))
    } finally {
      setStatementLinesLoading(false)
    }
  }, [id, statementLineFilter, statementLinesPage, t])

  // Load ledger transactions
  const loadLedgerTransactions = useCallback(async () => {
    if (!id) return
    try {
      setLedgerLoading(true)
      const result = await getLedgerTransactions(id, {
        page: ledgerPage,
        size: PAGE_SIZE,
        includeMatched: false, // Only show unmatched by default
      })
      setLedgerTransactions(result.data)
      setLedgerTotalPages(result.meta.totalPages)
    } catch (error) {
      console.error('Failed to load ledger transactions:', error)
      toast.error(t('errors.generic'))
    } finally {
      setLedgerLoading(false)
    }
  }, [id, ledgerPage, t])

  // Initial load
  useEffect(() => {
    void loadReconciliation()
  }, [loadReconciliation])

  useEffect(() => {
    if (reconciliation) {
      void loadStatementLines()
      void loadLedgerTransactions()
    }
  }, [reconciliation?.id, loadStatementLines, loadLedgerTransactions])

  // Handle statement line filter change
  useEffect(() => {
    setStatementLinesPage(0)
  }, [statementLineFilter])

  // Handle manual match
  const handleManualMatch = async () => {
    if (!id || !selectedStatementLine || !selectedLedgerTransaction) {
      toast.error(t('bankReconciliation.match.selectStatementLine'))
      return
    }

    try {
      setMatching(true)
      await manualMatch(id, {
        statementLineId: selectedStatementLine.id,
        voucherId: selectedLedgerTransaction.voucherId,
      })
      toast.success(t('bankReconciliation.messages.matchSuccess'))
      setSelectedStatementLine(null)
      setSelectedLedgerTransaction(null)
      void loadStatementLines()
      void loadLedgerTransactions()
      void loadReconciliation()
    } catch (error) {
      console.error('Failed to match:', error)
      toast.error(t('bankReconciliation.messages.failedToMatch'))
    } finally {
      setMatching(false)
    }
  }

  // Handle unmatch
  const handleUnmatch = async (line: BankStatementLineDTO) => {
    if (!id) return
    try {
      setMatching(true)
      await unmatchLine(id, line.id)
      toast.success(t('bankReconciliation.messages.unmatchSuccess'))
      void loadStatementLines()
      void loadLedgerTransactions()
      void loadReconciliation()
    } catch (error) {
      console.error('Failed to unmatch:', error)
      toast.error(t('bankReconciliation.messages.failedToUnmatch'))
    } finally {
      setMatching(false)
    }
  }

  // Handle auto-match
  const handleAutoMatch = async () => {
    if (!id) return
    try {
      setAutoMatching(true)
      const result = await runAutoMatch(id, {
        dateTolerance: 3,
        amountTolerance: 0,
        minimumConfidence: 0.7,
        autoApply: true,
      })
      setAutoMatchResult(result)
      setShowAutoMatchResult(true)
      toast.success(t('bankReconciliation.messages.autoMatchComplete'))
      void loadStatementLines()
      void loadLedgerTransactions()
      void loadReconciliation()
    } catch (error) {
      console.error('Failed to auto-match:', error)
      toast.error(t('bankReconciliation.messages.failedToAutoMatch'))
    } finally {
      setAutoMatching(false)
    }
  }

  // Handle complete reconciliation
  const handleComplete = async () => {
    if (!id) return
    try {
      setCompleting(true)
      await completeReconciliation(id)
      toast.success(t('bankReconciliation.messages.reconciliationCompleted'))
      setShowCompleteDialog(false)
      void loadReconciliation()
    } catch (error) {
      console.error('Failed to complete:', error)
      toast.error(t('bankReconciliation.messages.failedToComplete'))
    } finally {
      setCompleting(false)
    }
  }

  // Handle reopen reconciliation
  const handleReopen = async () => {
    if (!id) return
    try {
      setCompleting(true)
      await reopenReconciliation(id)
      toast.success(t('bankReconciliation.messages.reconciliationReopened'))
      void loadReconciliation()
    } catch (error) {
      console.error('Failed to reopen:', error)
      toast.error(t('errors.generic'))
    } finally {
      setCompleting(false)
    }
  }

  // Handle export
  const handleExport = async (format: 'excel' | 'pdf') => {
    if (!id || !reconciliation) return
    try {
      setExporting(true)
      const blob = format === 'excel' ? await exportToExcel(id) : await exportToPdf(id)
      const ext = format === 'excel' ? 'xlsx' : 'pdf'
      const filename = `reconciliation_${reconciliation.bankAccountNumber}_${formatDate(reconciliation.statementPeriodEnd)}.${ext}`
      downloadBlob(blob, filename)
      toast.success(t('bankReconciliation.messages.exportStarted'))
    } catch (error) {
      console.error('Failed to export:', error)
      toast.error(t('bankReconciliation.messages.failedToExport'))
    } finally {
      setExporting(false)
    }
  }

  if (loading) {
    return (
      <div className="space-y-6 p-6">
        <Skeleton className="h-10 w-64" />
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
        <Skeleton className="h-96" />
      </div>
    )
  }

  if (!reconciliation) {
    return (
      <div className="p-6 text-center">
        <p className="text-muted-foreground">{t('errors.notFound')}</p>
        <Button variant="outline" onClick={() => navigate('/accounting/bank-reconciliation')} className="mt-4">
          <ArrowLeft className="h-4 w-4 mr-2" />
          {t('common.back')}
        </Button>
      </div>
    )
  }

  const isCompleted = reconciliation.status === 'COMPLETED'
  const matchProgress =
    reconciliation.totalLines > 0
      ? ((reconciliation.matchedLines || 0) / reconciliation.totalLines) * 100
      : 0

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" onClick={() => navigate('/accounting/bank-reconciliation')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            {t('common.back')}
          </Button>
          <div>
            <h1 className="text-2xl font-bold">
              {reconciliation.bankName} - {reconciliation.bankAccountNumber}
            </h1>
            <p className="text-muted-foreground">
              {formatDate(reconciliation.statementPeriodStart)} -{' '}
              {formatDate(reconciliation.statementPeriodEnd)}
            </p>
          </div>
          <Badge variant={getStatusBadgeVariant(reconciliation.status)}>
            {t(`bankReconciliation.status.${reconciliation.status === 'NOT_STARTED' ? 'notStarted' : reconciliation.status === 'IN_PROGRESS' ? 'inProgress' : 'completed'}`)}
          </Badge>
        </div>
        <div className="flex gap-2">
          {!isCompleted && (
            <>
              <Button variant="outline" onClick={() => setShowImportDialog(true)}>
                <Upload className="h-4 w-4 mr-2" />
                {t('bankReconciliation.importStatement')}
              </Button>
              <Button variant="outline" onClick={handleAutoMatch} disabled={autoMatching}>
                <Wand2 className={`h-4 w-4 mr-2 ${autoMatching ? 'animate-spin' : ''}`} />
                {t('bankReconciliation.autoMatch')}
              </Button>
              <Button onClick={() => setShowCompleteDialog(true)} disabled={completing}>
                <CheckCircle2 className="h-4 w-4 mr-2" />
                {t('bankReconciliation.complete')}
              </Button>
            </>
          )}
          {isCompleted && (
            <Button variant="outline" onClick={handleReopen} disabled={completing}>
              <RotateCcw className="h-4 w-4 mr-2" />
              {t('bankReconciliation.reopen')}
            </Button>
          )}
          <Button variant="outline" onClick={() => handleExport('excel')} disabled={exporting}>
            <Download className="h-4 w-4 mr-2" />
            {t('bankReconciliation.exportExcel')}
          </Button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-muted-foreground">
              {t('bankReconciliation.statementBalance')}
            </div>
            <div className="text-2xl font-bold">{formatCurrency(reconciliation.statementBalance)}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-muted-foreground">
              {t('bankReconciliation.ledgerBalance')}
            </div>
            <div className="text-2xl font-bold">{formatCurrency(reconciliation.ledgerBalance)}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-muted-foreground">
              {t('bankReconciliation.difference')}
            </div>
            <div
              className={`text-2xl font-bold ${
                Math.abs(reconciliation.difference || 0) < 0.01
                  ? 'text-green-600'
                  : 'text-red-600'
              }`}
            >
              {formatCurrency(reconciliation.difference)}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-muted-foreground">
              {t('bankReconciliation.matchedTotal')}
            </div>
            <div className="text-2xl font-bold text-green-600">
              {reconciliation.matchedLines || 0} / {reconciliation.totalLines || 0}
            </div>
            <Progress value={matchProgress} className="mt-2" />
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-muted-foreground">
              {t('bankReconciliation.unmatchedTotal')}
            </div>
            <div className="text-2xl font-bold text-red-600">
              {reconciliation.unmatchedLines || 0}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Content - Split View */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Statement Lines */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>{t('bankReconciliation.statementLines')}</CardTitle>
              <Select
                value={statementLineFilter}
                onValueChange={(value) => setStatementLineFilter(value as MatchStatus | '')}
              >
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder={t('bankReconciliation.allStatuses')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">{t('bankReconciliation.allStatuses')}</SelectItem>
                  <SelectItem value="UNMATCHED">
                    {t('bankReconciliation.matchStatus.unmatched')}
                  </SelectItem>
                  <SelectItem value="MATCHED">
                    {t('bankReconciliation.matchStatus.matched')}
                  </SelectItem>
                  <SelectItem value="ADJUSTMENT_REQUIRED">
                    {t('bankReconciliation.matchStatus.adjustmentRequired')}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardHeader>
          <CardContent>
            {statementLinesLoading ? (
              <div className="space-y-2">
                {[...Array(5)].map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : statementLines.length === 0 ? (
              <div className="text-center text-muted-foreground py-8">
                {t('bankReconciliation.noStatementLines')}
              </div>
            ) : (
              <>
                <div className="rounded-md border max-h-[400px] overflow-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-[40px]">#</TableHead>
                        <TableHead>{t('bankReconciliation.transactionDate')}</TableHead>
                        <TableHead>{t('common.description')}</TableHead>
                        <TableHead className="text-right">{t('common.amount')}</TableHead>
                        <TableHead>{t('common.status')}</TableHead>
                        <TableHead className="w-[80px]">{t('common.actions')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {statementLines.map((line) => {
                        const amount =
                          (line.creditAmount || 0) > 0
                            ? line.creditAmount
                            : -(line.debitAmount || 0)
                        const isSelected = selectedStatementLine?.id === line.id
                        return (
                          <TableRow
                            key={line.id}
                            className={`cursor-pointer ${isSelected ? 'bg-primary/10' : ''} ${line.matchStatus === 'UNMATCHED' && !isCompleted ? 'hover:bg-muted/50' : ''}`}
                            onClick={() => {
                              if (line.matchStatus === 'UNMATCHED' && !isCompleted) {
                                setSelectedStatementLine(isSelected ? null : line)
                              }
                            }}
                          >
                            <TableCell>{line.lineNumber}</TableCell>
                            <TableCell>{formatDate(line.transactionDate)}</TableCell>
                            <TableCell>
                              <div className="max-w-[200px] truncate" title={line.description}>
                                {line.description}
                              </div>
                              {line.reference && (
                                <div className="text-xs text-muted-foreground">{line.reference}</div>
                              )}
                            </TableCell>
                            <TableCell className="text-right">
                              <span className={amount && amount > 0 ? 'text-green-600' : 'text-red-600'}>
                                {formatCurrency(amount)}
                              </span>
                            </TableCell>
                            <TableCell>
                              <Badge variant={getMatchStatusBadgeVariant(line.matchStatus)}>
                                {t(`bankReconciliation.matchStatus.${line.matchStatus === 'ADJUSTMENT_REQUIRED' ? 'adjustmentRequired' : line.matchStatus.toLowerCase()}`)}
                              </Badge>
                              {line.matchConfidence && (
                                <div className="text-xs text-muted-foreground">
                                  {(line.matchConfidence * 100).toFixed(0)}%
                                </div>
                              )}
                            </TableCell>
                            <TableCell className="w-24">
                              <div className="flex gap-1">
                                {line.matchStatus === 'MATCHED' && !isCompleted && (
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      handleUnmatch(line)
                                    }}
                                    disabled={matching}
                                    title={t('bankReconciliation.unmatch')}
                                  >
                                    <Link2Off className="h-4 w-4" />
                                  </Button>
                                )}
                                {line.matchStatus === 'UNMATCHED' && !isCompleted && (
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      setAdjustmentStatementLine(line)
                                      setShowAdjustmentDialog(true)
                                    }}
                                    title={t('bankReconciliation.adjustment.createTitle')}
                                  >
                                    <Plus className="h-4 w-4" />
                                  </Button>
                                )}
                              </div>
                            </TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                </div>
                {/* Pagination */}
                <div className="flex items-center justify-between mt-4">
                  <div className="text-sm text-muted-foreground">
                    {t('table.page')} {statementLinesPage + 1} {t('table.of')}{' '}
                    {statementLinesTotalPages || 1}
                  </div>
                  <div className="flex gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setStatementLinesPage((p) => Math.max(0, p - 1))}
                      disabled={statementLinesPage === 0}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        setStatementLinesPage((p) => Math.min(statementLinesTotalPages - 1, p + 1))
                      }
                      disabled={statementLinesPage >= statementLinesTotalPages - 1}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        {/* Ledger Transactions */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>{t('bankReconciliation.ledgerTransactions')}</CardTitle>
              <Button
                variant="outline"
                size="sm"
                onClick={loadLedgerTransactions}
                disabled={ledgerLoading}
              >
                <RefreshCw className={`h-4 w-4 ${ledgerLoading ? 'animate-spin' : ''}`} />
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            {ledgerLoading ? (
              <div className="space-y-2">
                {[...Array(5)].map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : ledgerTransactions.length === 0 ? (
              <div className="text-center text-muted-foreground py-8">
                {t('bankReconciliation.noLedgerTransactions')}
              </div>
            ) : (
              <>
                <div className="rounded-md border max-h-[400px] overflow-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('bankReconciliation.voucherNumber')}</TableHead>
                        <TableHead>{t('bankReconciliation.transactionDate')}</TableHead>
                        <TableHead>{t('common.description')}</TableHead>
                        <TableHead className="text-right">{t('common.amount')}</TableHead>
                        <TableHead className="w-[80px]"></TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {ledgerTransactions.map((txn) => {
                        const amount =
                          (txn.debitAmount || 0) > 0 ? txn.debitAmount : -(txn.creditAmount || 0)
                        const isSelected = selectedLedgerTransaction?.voucherId === txn.voucherId
                        const canSelect = !txn.alreadyMatched && !isCompleted
                        return (
                          <TableRow
                            key={txn.voucherId}
                            className={`${canSelect ? 'cursor-pointer hover:bg-muted/50' : ''} ${isSelected ? 'bg-primary/10' : ''} ${txn.alreadyMatched ? 'opacity-50' : ''}`}
                            onClick={() => {
                              if (canSelect) {
                                setSelectedLedgerTransaction(isSelected ? null : txn)
                              }
                            }}
                          >
                            <TableCell>
                              <div className="font-mono">{txn.voucherNumber}</div>
                              <div className="text-xs text-muted-foreground">{txn.voucherType}</div>
                            </TableCell>
                            <TableCell>{formatDate(txn.transactionDate)}</TableCell>
                            <TableCell>
                              <div className="max-w-[200px] truncate" title={txn.description}>
                                {txn.description}
                              </div>
                              {txn.reference && (
                                <div className="text-xs text-muted-foreground">{txn.reference}</div>
                              )}
                            </TableCell>
                            <TableCell className="text-right">
                              <span className={amount && amount > 0 ? 'text-green-600' : 'text-red-600'}>
                                {formatCurrency(amount)}
                              </span>
                            </TableCell>
                            <TableCell>
                              {txn.alreadyMatched && (
                                <Badge variant="outline">{t('bankReconciliation.alreadyMatched')}</Badge>
                              )}
                            </TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                </div>
                {/* Pagination */}
                <div className="flex items-center justify-between mt-4">
                  <div className="text-sm text-muted-foreground">
                    {t('table.page')} {ledgerPage + 1} {t('table.of')} {ledgerTotalPages || 1}
                  </div>
                  <div className="flex gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setLedgerPage((p) => Math.max(0, p - 1))}
                      disabled={ledgerPage === 0}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setLedgerPage((p) => Math.min(ledgerTotalPages - 1, p + 1))}
                      disabled={ledgerPage >= ledgerTotalPages - 1}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Manual Match Action Bar */}
      {!isCompleted && (selectedStatementLine || selectedLedgerTransaction) && (
        <Card className="fixed bottom-6 left-1/2 transform -translate-x-1/2 shadow-lg w-auto max-w-3xl z-50">
          <CardContent className="py-4">
            <div className="flex items-center gap-4">
              <div className="flex-1">
                <div className="text-sm text-muted-foreground">
                  {t('bankReconciliation.match.selectStatementLine')}
                </div>
                <div className="font-medium">
                  {selectedStatementLine
                    ? `#${selectedStatementLine.lineNumber}: ${formatCurrency((selectedStatementLine.creditAmount || 0) - (selectedStatementLine.debitAmount || 0))}`
                    : '-'}
                </div>
              </div>
              <Link2 className="h-6 w-6 text-muted-foreground" />
              <div className="flex-1">
                <div className="text-sm text-muted-foreground">
                  {t('bankReconciliation.match.selectLedgerTransaction')}
                </div>
                <div className="font-medium">
                  {selectedLedgerTransaction
                    ? `${selectedLedgerTransaction.voucherNumber}: ${formatCurrency((selectedLedgerTransaction.debitAmount || 0) - (selectedLedgerTransaction.creditAmount || 0))}`
                    : '-'}
                </div>
              </div>
              <Button
                onClick={handleManualMatch}
                disabled={!selectedStatementLine || !selectedLedgerTransaction || matching}
              >
                <Link2 className="h-4 w-4 mr-2" />
                {matching ? t('common.saving') : t('bankReconciliation.match.confirmMatch')}
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setSelectedStatementLine(null)
                  setSelectedLedgerTransaction(null)
                }}
              >
                {t('common.cancel')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Auto-Match Result Dialog */}
      <AlertDialog open={showAutoMatchResult} onOpenChange={setShowAutoMatchResult}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('bankReconciliation.match.matchResult')}</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2">
                <div>
                  {t('bankReconciliation.match.linesProcessed')}: {autoMatchResult?.totalLinesProcessed}
                </div>
                <div>
                  {t('bankReconciliation.match.matchesFound')}: {autoMatchResult?.matchesFound}
                </div>
                <div>
                  {t('bankReconciliation.match.matchesApplied')}: {autoMatchResult?.matchesApplied}
                </div>
                <div>
                  {t('bankReconciliation.match.noMatchFound')}: {autoMatchResult?.noMatchFound}
                </div>
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction>{t('common.close')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Complete Reconciliation Confirmation */}
      <AlertDialog open={showCompleteDialog} onOpenChange={setShowCompleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('bankReconciliation.completion.confirmTitle')}</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2">
                <p>{t('bankReconciliation.completion.confirmMessage')}</p>
                {(reconciliation.unmatchedLines || 0) > 0 && (
                  <p className="text-yellow-600">
                    {t('bankReconciliation.completion.warningUnmatched')}
                  </p>
                )}
                <p className="text-sm text-muted-foreground">
                  {t('bankReconciliation.completion.bankAccountUpdated')}
                </p>
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={completing}>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={handleComplete} disabled={completing}>
              {completing ? t('common.saving') : t('bankReconciliation.complete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Statement Import Dialog */}
      <StatementImportDialog
        open={showImportDialog}
        onOpenChange={setShowImportDialog}
        reconciliationId={id || ''}
        bankAccountName={reconciliation?.bankName || ''}
        onSuccess={() => {
          loadReconciliation()
          loadStatementLines()
        }}
      />

      {/* Create Adjustment Dialog */}
      <CreateAdjustmentDialog
        open={showAdjustmentDialog}
        onOpenChange={setShowAdjustmentDialog}
        reconciliationId={id || ''}
        statementLine={adjustmentStatementLine}
        onSuccess={() => {
          loadReconciliation()
          loadStatementLines()
          setAdjustmentStatementLine(null)
        }}
      />
    </div>
  )
}
