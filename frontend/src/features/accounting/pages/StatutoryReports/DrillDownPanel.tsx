/**
 * DrillDownPanel Component
 *
 * Slide-over panel for exploring report line details:
 * - Level 1: Report Line -> Contributing Accounts
 * - Level 2: Account -> Vouchers
 * - Level 3: Voucher Detail (modal)
 *
 * Features:
 * - Breadcrumb navigation
 * - Paginated data loading
 * - Animated transitions
 */

'use client'

import { useState, useCallback, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, FileText, ExternalLink } from 'lucide-react'

import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

import {
  getDrillDownAccounts,
  getDrillDownVouchers,
  getVoucherDetail,
  type ReportType,
  type AccountContribution,
  type VoucherSummary,
  type VoucherDetail,
} from '../../services/statutoryReports'

interface DrillDownPanelProps {
  open: boolean
  onClose: () => void
  reportType: ReportType
  lineCode: string
  lineName: string
  periodId: string
}

type DrillDownLevel = 'accounts' | 'vouchers'

interface BreadcrumbItem {
  level: DrillDownLevel
  label: string
  code: string
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'decimal',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

export function DrillDownPanel({
  open,
  onClose,
  reportType,
  lineCode,
  lineName,
  periodId,
}: DrillDownPanelProps) {
  const { t } = useTranslation()

  // Navigation state
  const [currentLevel, setCurrentLevel] = useState<DrillDownLevel>('accounts')
  const [breadcrumbs, setBreadcrumbs] = useState<BreadcrumbItem[]>([])

  // Data state
  const [loading, setLoading] = useState(false)
  const [accounts, setAccounts] = useState<AccountContribution[]>([])
  const [vouchers, setVouchers] = useState<VoucherSummary[]>([])
  const [selectedAccount, setSelectedAccount] = useState<{ code: string; name: string } | null>(
    null,
  )

  // Pagination
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Voucher detail modal
  const [voucherDetailOpen, setVoucherDetailOpen] = useState(false)
  const [voucherDetail, setVoucherDetail] = useState<VoucherDetail | null>(null)
  const [loadingDetail, setLoadingDetail] = useState(false)

  // Load accounts for the line
  const loadAccounts = useCallback(async () => {
    if (!periodId || !lineCode) return

    setLoading(true)
    try {
      const response = await getDrillDownAccounts(reportType, lineCode, periodId, page, 20)
      setAccounts(response.content || [])
      setTotalPages(response.totalPages)
    } catch (error) {
      console.error('Failed to load accounts:', error)
      setAccounts([])
    } finally {
      setLoading(false)
    }
  }, [reportType, lineCode, periodId, page])

  // Reset state when panel opens with new line
  useEffect(() => {
    if (open && lineCode) {
      setCurrentLevel('accounts')
      setBreadcrumbs([{ level: 'accounts', label: lineName, code: lineCode }])
      setPage(0)
      setSelectedAccount(null)
      loadAccounts()
    }
  }, [open, lineCode, lineName, loadAccounts])

  // Load vouchers for an account
  const loadVouchers = useCallback(
    async (accountCode: string) => {
      if (!periodId) return

      setLoading(true)
      try {
        const response = await getDrillDownVouchers(accountCode, periodId, 0, 20)
        setVouchers(response.content || [])
        setTotalPages(response.totalPages)
        setPage(0)
      } catch (error) {
        console.error('Failed to load vouchers:', error)
        setVouchers([])
      } finally {
        setLoading(false)
      }
    },
    [periodId],
  )

  // Handle account click
  const handleAccountClick = useCallback(
    (account: AccountContribution) => {
      setSelectedAccount({ code: account.accountCode, name: account.accountName })
      setCurrentLevel('vouchers')
      setBreadcrumbs((prev) => [
        ...prev,
        { level: 'vouchers', label: account.accountName, code: account.accountCode },
      ])
      loadVouchers(account.accountCode)
    },
    [loadVouchers],
  )

  // Handle voucher click
  const handleVoucherClick = useCallback(async (voucherId: string) => {
    setLoadingDetail(true)
    setVoucherDetailOpen(true)
    try {
      const detail = await getVoucherDetail(voucherId)
      setVoucherDetail(detail)
    } catch (error) {
      console.error('Failed to load voucher detail:', error)
    } finally {
      setLoadingDetail(false)
    }
  }, [])

  // Navigate back in breadcrumb
  const navigateBack = useCallback(() => {
    if (currentLevel === 'vouchers') {
      setCurrentLevel('accounts')
      setBreadcrumbs((prev) => prev.slice(0, 1))
      setSelectedAccount(null)
      setPage(0)
      loadAccounts()
    }
  }, [currentLevel, loadAccounts])

  // Close handler
  const handleClose = useCallback(() => {
    onClose()
    // Reset state after animation
    setTimeout(() => {
      setCurrentLevel('accounts')
      setBreadcrumbs([])
      setAccounts([])
      setVouchers([])
      setSelectedAccount(null)
    }, 300)
  }, [onClose])

  // Status badge styling
  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'POSTED':
        return <Badge className="bg-green-100 text-green-800 hover:bg-green-100">Posted</Badge>
      case 'DRAFT':
        return <Badge variant="secondary">Draft</Badge>
      case 'UNPOSTED':
        return (
          <Badge variant="outline" className="text-orange-600 border-orange-300">
            Unposted
          </Badge>
        )
      default:
        return <Badge variant="outline">{status}</Badge>
    }
  }

  return (
    <>
      <Sheet open={open} onOpenChange={(isOpen) => !isOpen && handleClose()}>
        <SheetContent className="w-full sm:max-w-xl">
          <SheetHeader className="pb-4">
            {/* Breadcrumbs */}
            <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
              {breadcrumbs.map((crumb, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  {idx > 0 && <ChevronRight className="h-3 w-3" />}
                  <button
                    onClick={() => idx === 0 && navigateBack()}
                    className={cn(
                      'hover:text-foreground transition-colors',
                      idx === breadcrumbs.length - 1 && 'text-foreground font-medium',
                    )}
                    disabled={idx === breadcrumbs.length - 1}
                  >
                    {crumb.code}
                  </button>
                </div>
              ))}
            </div>

            <SheetTitle className="flex items-center gap-3">
              {currentLevel === 'vouchers' && (
                <Button variant="ghost" size="icon" onClick={navigateBack}>
                  <ChevronLeft className="h-4 w-4" />
                </Button>
              )}
              <span className="flex-1 truncate">
                {currentLevel === 'accounts' ? lineName : selectedAccount?.name}
              </span>
            </SheetTitle>
          </SheetHeader>

          <ScrollArea className="h-[calc(100vh-180px)]">
            {loading ? (
              <div className="space-y-3">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : currentLevel === 'accounts' ? (
              /* Accounts List */
              <div className="space-y-2">
                {accounts.length === 0 ? (
                  <div className="text-center text-muted-foreground py-8">
                    {t('statutoryReports.drillDown.noAccounts')}
                  </div>
                ) : (
                  accounts.map((account) => (
                    <button
                      key={account.accountCode}
                      onClick={() => handleAccountClick(account)}
                      className="w-full p-4 rounded-lg border hover:border-primary/50 hover:bg-muted/50 transition-all text-left group"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-sm text-muted-foreground">
                              {account.accountCode}
                            </span>
                            {account.transactionCount != null && (
                              <span className="text-xs text-muted-foreground">
                                ({account.transactionCount} txns)
                              </span>
                            )}
                          </div>
                          <div className="font-medium truncate">{account.accountName}</div>
                        </div>
                        <div className="text-right shrink-0">
                          <div className="voucher-tabular-nums font-medium">
                            {formatCurrency(account.netAmount)}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            Dr: {formatCurrency(account.debitAmount)} | Cr:{' '}
                            {formatCurrency(account.creditAmount)}
                          </div>
                        </div>
                        <ChevronRight className="h-4 w-4 text-muted-foreground/50 group-hover:text-muted-foreground shrink-0 mt-2" />
                      </div>
                    </button>
                  ))
                )}
              </div>
            ) : (
              /* Vouchers List */
              <div className="space-y-2">
                {vouchers.length === 0 ? (
                  <div className="text-center text-muted-foreground py-8">
                    {t('statutoryReports.drillDown.noVouchers')}
                  </div>
                ) : (
                  vouchers.map((voucher) => (
                    <button
                      key={voucher.voucherId}
                      onClick={() => handleVoucherClick(voucher.voucherId)}
                      className="w-full p-4 rounded-lg border hover:border-primary/50 hover:bg-muted/50 transition-all text-left group"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4 text-muted-foreground" />
                            <span className="font-mono text-sm">{voucher.voucherNumber}</span>
                            {getStatusBadge(voucher.status)}
                          </div>
                          <div className="text-sm text-muted-foreground mt-1">
                            {new Date(voucher.voucherDate).toLocaleDateString('vi-VN')}
                          </div>
                          <div className="text-sm truncate mt-1">{voucher.description}</div>
                        </div>
                        <div className="text-right shrink-0">
                          {voucher.debitAmount > 0 && (
                            <div className="text-sm">
                              <span className="text-muted-foreground">Dr:</span>{' '}
                              <span className="voucher-tabular-nums">
                                {formatCurrency(voucher.debitAmount)}
                              </span>
                            </div>
                          )}
                          {voucher.creditAmount > 0 && (
                            <div className="text-sm">
                              <span className="text-muted-foreground">Cr:</span>{' '}
                              <span className="voucher-tabular-nums">
                                {formatCurrency(voucher.creditAmount)}
                              </span>
                            </div>
                          )}
                        </div>
                        <ExternalLink className="h-4 w-4 text-muted-foreground/50 group-hover:text-muted-foreground shrink-0 mt-2" />
                      </div>
                    </button>
                  ))
                )}
              </div>
            )}

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between mt-4 pt-4 border-t">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  <ChevronLeft className="h-4 w-4 mr-1" />
                  {t('common.previous')}
                </Button>
                <span className="text-sm text-muted-foreground">
                  {t('common.page')} {page + 1} / {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  {t('common.next')}
                  <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            )}
          </ScrollArea>
        </SheetContent>
      </Sheet>

      {/* Voucher Detail Modal */}
      <Dialog open={voucherDetailOpen} onOpenChange={setVoucherDetailOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <FileText className="h-5 w-5" />
              {voucherDetail?.voucherNumber || t('statutoryReports.drillDown.voucherDetail')}
            </DialogTitle>
            <DialogDescription className="sr-only">
              {t('statutoryReports.drillDown.voucherDetailDescription')}
            </DialogDescription>
          </DialogHeader>

          <ScrollArea className="flex-1">
            {loadingDetail ? (
              <div className="space-y-3 py-4">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-2/3" />
                <Skeleton className="h-32 w-full" />
              </div>
            ) : voucherDetail ? (
              <div className="space-y-4 py-4">
                {/* Voucher Header */}
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <span className="text-muted-foreground">{t('common.date')}:</span>{' '}
                    {new Date(voucherDetail.voucherDate).toLocaleDateString('vi-VN')}
                  </div>
                  <div>
                    <span className="text-muted-foreground">{t('common.type')}:</span>{' '}
                    {voucherDetail.voucherType}
                  </div>
                  <div className="col-span-2">
                    <span className="text-muted-foreground">{t('common.description')}:</span>{' '}
                    {voucherDetail.description}
                  </div>
                  <div>
                    <span className="text-muted-foreground">{t('common.status')}:</span>{' '}
                    {getStatusBadge(voucherDetail.status)}
                  </div>
                </div>

                <Separator />

                {/* Voucher Lines */}
                <div>
                  <h4 className="font-medium mb-2">{t('statutoryReports.drillDown.lines')}</h4>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('common.account')}</TableHead>
                        <TableHead>{t('common.description')}</TableHead>
                        <TableHead className="text-right">{t('common.debit')}</TableHead>
                        <TableHead className="text-right">{t('common.credit')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {voucherDetail.lines.map((line) => (
                        <TableRow key={line.lineId}>
                          <TableCell>
                            <div className="font-mono text-sm">{line.accountCode}</div>
                            <div className="text-xs text-muted-foreground">{line.accountName}</div>
                          </TableCell>
                          <TableCell className="text-sm">{line.description || '-'}</TableCell>
                          <TableCell className="text-right voucher-tabular-nums">
                            {line.debitAmount > 0 ? formatCurrency(line.debitAmount) : '-'}
                          </TableCell>
                          <TableCell className="text-right voucher-tabular-nums">
                            {line.creditAmount > 0 ? formatCurrency(line.creditAmount) : '-'}
                          </TableCell>
                        </TableRow>
                      ))}
                      {/* Totals Row */}
                      <TableRow key="totals-row" className="font-medium bg-muted/50">
                        <TableCell colSpan={2}>{t('common.total')}</TableCell>
                        <TableCell className="text-right voucher-tabular-nums">
                          {formatCurrency(voucherDetail.totalDebit)}
                        </TableCell>
                        <TableCell className="text-right voucher-tabular-nums">
                          {formatCurrency(voucherDetail.totalCredit)}
                        </TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </div>

                {/* Attachments */}
                {voucherDetail.attachments.length > 0 && (
                  <>
                    <Separator />
                    <div>
                      <h4 className="font-medium mb-2">
                        {t('statutoryReports.drillDown.attachments')}
                      </h4>
                      <div className="flex flex-wrap gap-2">
                        {voucherDetail.attachments.map((attachment, idx) => (
                          <Badge key={idx} variant="outline" className="gap-1">
                            <FileText className="h-3 w-3" />
                            {attachment}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  </>
                )}

                {/* Audit Info */}
                <Separator />
                <div className="text-xs text-muted-foreground">
                  {t('common.createdBy')}: {voucherDetail.createdBy} |{' '}
                  {new Date(voucherDetail.createdAt).toLocaleString('vi-VN')}
                </div>
              </div>
            ) : null}
          </ScrollArea>

          <div className="flex justify-end gap-2 pt-4 border-t">
            <Button variant="outline" onClick={() => setVoucherDetailOpen(false)}>
              {t('common.close')}
            </Button>
            <Button asChild>
              <a href={`/vouchers/${voucherDetail?.voucherId}`} target="_blank" rel="noreferrer">
                {t('statutoryReports.drillDown.openVoucher')}
                <ExternalLink className="h-4 w-4 ml-2" />
              </a>
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

export default DrillDownPanel
