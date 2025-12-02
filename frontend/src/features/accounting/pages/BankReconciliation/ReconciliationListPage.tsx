'use client'

import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, RefreshCw, Search, Eye, Trash2, ChevronLeft, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { format } from 'date-fns'

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
import { Badge } from '@/components/ui/badge'
import { DatePicker } from '@/components/ui/date-picker'
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
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { getBankAccounts } from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount } from '@/types/bankAccount'
import {
  listReconciliations,
  createReconciliation,
  deleteReconciliation,
  type BankReconciliationListDTO,
  type ReconciliationStatus,
  STATUS_COLORS,
} from '../../services/bankReconciliation'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50]

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

export function ReconciliationListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  // State
  const [loading, setLoading] = useState(false)
  const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState<string>('')
  const [selectedStatus, setSelectedStatus] = useState<ReconciliationStatus | ''>('')
  const [dateFrom, setDateFrom] = useState<Date | undefined>(undefined)
  const [dateTo, setDateTo] = useState<Date | undefined>(undefined)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [data, setData] = useState<BankReconciliationListDTO[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Create reconciliation modal state
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [newReconciliation, setNewReconciliation] = useState({
    bankAccountId: '',
    statementPeriodStart: undefined as Date | undefined,
    statementPeriodEnd: undefined as Date | undefined,
    statementBalance: '',
    notes: '',
  })

  // Delete confirmation state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [reconciliationToDelete, setReconciliationToDelete] =
    useState<BankReconciliationListDTO | null>(null)
  const [deleting, setDeleting] = useState(false)

  // Load bank accounts
  useEffect(() => {
    const loadBankAccounts = async () => {
      try {
        const response = await getBankAccounts({ status: true, size: 100 })
        setBankAccounts(response.data || [])
      } catch (error) {
        console.error('Failed to load bank accounts:', error)
        toast.error(t('errors.generic'))
      }
    }
    void loadBankAccounts()
  }, [t])

  // Load reconciliations
  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const result = await listReconciliations({
        bankAccountId: selectedAccountId ? Number(selectedAccountId) : undefined,
        status: selectedStatus || undefined,
        dateFrom: dateFrom ? format(dateFrom, 'yyyy-MM-dd') : undefined,
        dateTo: dateTo ? format(dateTo, 'yyyy-MM-dd') : undefined,
        page,
        size: pageSize,
      })
      setData(result.data)
      setTotalElements(result.meta.totalElements)
      setTotalPages(result.meta.totalPages)
    } catch (error) {
      console.error('Failed to load reconciliations:', error)
      toast.error(t('bankReconciliation.messages.failedToCreate'))
    } finally {
      setLoading(false)
    }
  }, [selectedAccountId, selectedStatus, dateFrom, dateTo, page, pageSize, t])

  useEffect(() => {
    void loadData()
  }, [loadData])

  // Handle row click to navigate to detail
  const handleRowClick = (reconciliation: BankReconciliationListDTO) => {
    navigate(`/accounting/bank-reconciliation/${reconciliation.id}`)
  }

  // Handle create new reconciliation
  const handleCreate = async () => {
    if (
      !newReconciliation.bankAccountId ||
      !newReconciliation.statementPeriodStart ||
      !newReconciliation.statementPeriodEnd ||
      !newReconciliation.statementBalance
    ) {
      toast.error(t('errors.validation'))
      return
    }

    try {
      setCreating(true)
      const result = await createReconciliation({
        bankAccountId: Number(newReconciliation.bankAccountId),
        statementPeriodStart: format(newReconciliation.statementPeriodStart, 'yyyy-MM-dd'),
        statementPeriodEnd: format(newReconciliation.statementPeriodEnd, 'yyyy-MM-dd'),
        statementBalance: Number(newReconciliation.statementBalance),
        notes: newReconciliation.notes || undefined,
      })
      toast.success(t('bankReconciliation.messages.createSuccess'))
      setCreateDialogOpen(false)
      setNewReconciliation({
        bankAccountId: '',
        statementPeriodStart: undefined,
        statementPeriodEnd: undefined,
        statementBalance: '',
        notes: '',
      })
      // Navigate to the new reconciliation
      navigate(`/accounting/bank-reconciliation/${result.id}`)
    } catch (error) {
      console.error('Failed to create reconciliation:', error)
      toast.error(t('bankReconciliation.messages.failedToCreate'))
    } finally {
      setCreating(false)
    }
  }

  // Handle delete
  const handleDeleteClick = (e: React.MouseEvent, reconciliation: BankReconciliationListDTO) => {
    e.stopPropagation()
    setReconciliationToDelete(reconciliation)
    setDeleteDialogOpen(true)
  }

  const confirmDelete = async () => {
    if (!reconciliationToDelete) return

    try {
      setDeleting(true)
      await deleteReconciliation(reconciliationToDelete.id)
      toast.success(t('bankReconciliation.messages.deleteSuccess'))
      setDeleteDialogOpen(false)
      setReconciliationToDelete(null)
      void loadData()
    } catch (error) {
      console.error('Failed to delete reconciliation:', error)
      toast.error(t('bankReconciliation.messages.failedToDelete'))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('bankReconciliation.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('bankReconciliation.subtitle')}</p>
        </div>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="h-4 w-4 mr-2" />
          {t('bankReconciliation.newReconciliation')}
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle>{t('common.filter')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="space-y-2">
              <Label htmlFor="account">{t('bankReconciliation.bankAccount')}</Label>
              <Select
                value={selectedAccountId}
                onValueChange={(value) => {
                  setSelectedAccountId(value === 'all' ? '' : value)
                  setPage(0)
                }}
              >
                <SelectTrigger id="account">
                  <SelectValue placeholder={t('bankReconciliation.selectBankAccount')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('bankReconciliation.allBankAccounts')}</SelectItem>
                  {bankAccounts.map((account) => (
                    <SelectItem key={account.id} value={String(account.id)}>
                      {account.bankName} - {account.accountNumber}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="status">{t('common.status')}</Label>
              <Select
                value={selectedStatus}
                onValueChange={(value) => {
                  setSelectedStatus(value === 'all' ? '' : (value as ReconciliationStatus))
                  setPage(0)
                }}
              >
                <SelectTrigger id="status">
                  <SelectValue placeholder={t('bankReconciliation.selectStatus')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('bankReconciliation.allStatuses')}</SelectItem>
                  <SelectItem value="NOT_STARTED">
                    {t('bankReconciliation.status.notStarted')}
                  </SelectItem>
                  <SelectItem value="IN_PROGRESS">
                    {t('bankReconciliation.status.inProgress')}
                  </SelectItem>
                  <SelectItem value="COMPLETED">
                    {t('bankReconciliation.status.completed')}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>{t('bankReconciliation.periodStart')}</Label>
              <DatePicker
                date={dateFrom}
                onDateChange={(date) => {
                  setDateFrom(date)
                  setPage(0)
                }}
                placeholder={t('bankReconciliation.periodStart')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('bankReconciliation.periodEnd')}</Label>
              <DatePicker
                date={dateTo}
                onDateChange={(date) => {
                  setDateTo(date)
                  setPage(0)
                }}
                placeholder={t('bankReconciliation.periodEnd')}
              />
            </div>
          </div>

          <div className="flex justify-end">
            <Button onClick={loadData} variant="outline" disabled={loading}>
              <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
              {t('common.refresh')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Data Table */}
      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2">
              {[...Array(5)].map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : (
            <>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('bankReconciliation.bankAccount')}</TableHead>
                      <TableHead>{t('bankReconciliation.period')}</TableHead>
                      <TableHead>{t('common.status')}</TableHead>
                      <TableHead className="text-right">
                        {t('bankReconciliation.statementBalance')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('bankReconciliation.ledgerBalance')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('bankReconciliation.difference')}
                      </TableHead>
                      <TableHead className="text-center">
                        {t('bankReconciliation.matchedTotal')} /{' '}
                        {t('bankReconciliation.unmatchedTotal')}
                      </TableHead>
                      <TableHead>{t('bankReconciliation.lastUpdated')}</TableHead>
                      <TableHead className="w-[100px]">{t('common.actions')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={9} className="text-center text-muted-foreground py-8">
                          {t('bankReconciliation.noReconciliationsFound')}
                        </TableCell>
                      </TableRow>
                    ) : (
                      data.map((rec) => {
                        const delta =
                          rec.statementBalance !== null && rec.ledgerBalance !== null
                            ? rec.statementBalance - rec.ledgerBalance
                            : null
                        return (
                          <TableRow
                            key={rec.id}
                            className="cursor-pointer hover:bg-muted/50"
                            onClick={() => handleRowClick(rec)}
                          >
                            <TableCell>
                              <div className="font-medium">{rec.bankName}</div>
                              <div className="text-xs text-muted-foreground">
                                {rec.bankAccountNumber}
                              </div>
                            </TableCell>
                            <TableCell>
                              {formatDate(rec.statementPeriodStart)} -{' '}
                              {formatDate(rec.statementPeriodEnd)}
                            </TableCell>
                            <TableCell>
                              <Badge variant={getStatusBadgeVariant(rec.status)}>
                                {t(
                                  `bankReconciliation.status.${rec.status === 'NOT_STARTED' ? 'notStarted' : rec.status === 'IN_PROGRESS' ? 'inProgress' : 'completed'}`,
                                )}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-right">
                              {formatCurrency(rec.statementBalance)}
                            </TableCell>
                            <TableCell className="text-right">
                              {formatCurrency(rec.ledgerBalance)}
                            </TableCell>
                            <TableCell className="text-right">
                              {delta !== null && (
                                <span
                                  className={
                                    Math.abs(delta) < 0.01
                                      ? 'text-green-600'
                                      : 'text-red-600 font-medium'
                                  }
                                >
                                  {formatCurrency(delta)}
                                </span>
                              )}
                            </TableCell>
                            <TableCell className="text-center">
                              <span className="text-green-600">{rec.matchedLines || 0}</span>
                              {' / '}
                              <span className="text-red-600">{rec.unmatchedLines || 0}</span>
                            </TableCell>
                            <TableCell>{formatDate(rec.updatedAt)}</TableCell>
                            <TableCell>
                              <div className="flex gap-1">
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    handleRowClick(rec)
                                  }}
                                  title={t('common.details')}
                                >
                                  <Eye className="h-4 w-4" />
                                </Button>
                                {rec.status !== 'COMPLETED' && (
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => handleDeleteClick(e, rec)}
                                    title={t('common.delete')}
                                    className="text-destructive hover:text-destructive"
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </Button>
                                )}
                              </div>
                            </TableCell>
                          </TableRow>
                        )
                      })
                    )}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              <div className="flex items-center justify-between mt-4">
                <div className="text-sm text-muted-foreground">
                  {t('table.showing')} {data.length > 0 ? page * pageSize + 1 : 0} {t('table.to')}{' '}
                  {Math.min((page + 1) * pageSize, totalElements)} {t('table.of')} {totalElements}{' '}
                  {t('table.entries')}
                </div>
                <div className="flex items-center gap-2">
                  <Label htmlFor="pageSize" className="text-sm">
                    {t('table.rowsPerPage')}:
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
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <div className="flex items-center px-3 text-sm">
                      {t('table.page')} {page + 1} {t('table.of')} {totalPages || 1}
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* Create Reconciliation Dialog */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('bankReconciliation.newReconciliation')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="create-account">{t('bankReconciliation.bankAccount')} *</Label>
              <Select
                value={newReconciliation.bankAccountId}
                onValueChange={(value) =>
                  setNewReconciliation((prev) => ({ ...prev, bankAccountId: value }))
                }
              >
                <SelectTrigger id="create-account">
                  <SelectValue placeholder={t('bankReconciliation.selectBankAccount')} />
                </SelectTrigger>
                <SelectContent>
                  {bankAccounts.map((account) => (
                    <SelectItem key={account.id} value={String(account.id)}>
                      {account.bankName} - {account.accountNumber}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('bankReconciliation.periodStart')} *</Label>
                <DatePicker
                  date={newReconciliation.statementPeriodStart}
                  onDateChange={(date) =>
                    setNewReconciliation((prev) => ({ ...prev, statementPeriodStart: date }))
                  }
                  placeholder={t('bankReconciliation.periodStart')}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('bankReconciliation.periodEnd')} *</Label>
                <DatePicker
                  date={newReconciliation.statementPeriodEnd}
                  onDateChange={(date) =>
                    setNewReconciliation((prev) => ({ ...prev, statementPeriodEnd: date }))
                  }
                  placeholder={t('bankReconciliation.periodEnd')}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="statement-balance">
                {t('bankReconciliation.statementBalance')} *
              </Label>
              <Input
                id="statement-balance"
                type="number"
                value={newReconciliation.statementBalance}
                onChange={(e) =>
                  setNewReconciliation((prev) => ({ ...prev, statementBalance: e.target.value }))
                }
                placeholder="0"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="notes">{t('common.notes')}</Label>
              <Input
                id="notes"
                value={newReconciliation.notes}
                onChange={(e) =>
                  setNewReconciliation((prev) => ({ ...prev, notes: e.target.value }))
                }
                placeholder={t('common.notes')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setCreateDialogOpen(false)}
              disabled={creating}
            >
              {t('common.cancel')}
            </Button>
            <Button onClick={handleCreate} disabled={creating}>
              {creating ? t('common.saving') : t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('bankReconciliation.deleteConfirm.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('bankReconciliation.deleteConfirm.message')}
              <br />
              <span className="text-destructive">
                {t('bankReconciliation.deleteConfirm.warning')}
              </span>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDelete}
              disabled={deleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleting ? t('common.saving') : t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
