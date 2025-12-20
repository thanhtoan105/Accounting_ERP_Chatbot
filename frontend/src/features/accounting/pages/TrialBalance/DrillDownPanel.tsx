'use client'

import { useCallback, useState, useEffect } from 'react'
import { X, ExternalLink, ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import {
  getDrillDownVouchers,
  type AmountType,
  type DrillDownResponseDTO,
  type DrillDownVoucherDTO,
} from '@/services/trialBalance'

interface DrillDownPanelProps {
  open: boolean
  onClose: () => void
  periodId: string
  accountId: number
  accountCode: string
  accountName: string
  amountType: AmountType
  onVoucherClick?: (voucherId: string) => void
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('vi-VN')
}

function getAmountTypeLabel(amountType: AmountType, t: (key: string) => string): string {
  const labels: Record<AmountType, string> = {
    OPENING_DEBIT: t('trialBalance.drillDown.openingDebit'),
    OPENING_CREDIT: t('trialBalance.drillDown.openingCredit'),
    PERIOD_DEBIT: t('trialBalance.drillDown.periodDebit'),
    PERIOD_CREDIT: t('trialBalance.drillDown.periodCredit'),
    CLOSING_DEBIT: t('trialBalance.drillDown.closingDebit'),
    CLOSING_CREDIT: t('trialBalance.drillDown.closingCredit'),
  }
  return labels[amountType] || amountType
}

export function DrillDownPanel({
  open,
  onClose,
  periodId,
  accountId,
  accountCode,
  accountName,
  amountType,
  onVoucherClick,
}: DrillDownPanelProps) {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<DrillDownResponseDTO | null>(null)
  const [page, setPage] = useState(0)
  const pageSize = 20

  const loadData = useCallback(async () => {
    if (!open || !periodId || !accountId) return

    try {
      setLoading(true)
      const result = await getDrillDownVouchers({
        periodId,
        accountId,
        amountType,
        page,
        size: pageSize,
        sortBy: 'voucherDate',
        sortDir: 'desc',
      })
      setData(result)
    } catch (error) {
      console.error('Failed to load drill-down data:', error)
    } finally {
      setLoading(false)
    }
  }, [open, periodId, accountId, amountType, page, pageSize])

  useEffect(() => {
    if (open) {
      setPage(0)
    }
  }, [open, periodId, accountId, amountType])

  useEffect(() => {
    void loadData()
  }, [loadData])

  const handleVoucherClick = (voucher: DrillDownVoucherDTO) => {
    if (onVoucherClick) {
      onVoucherClick(voucher.id)
    } else {
      // Default behavior: open in new tab
      window.open(`/accounting/vouchers/${voucher.id}`, '_blank')
    }
  }

  const totalPages = data ? Math.ceil(data.total / pageSize) : 0

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <SheetContent side="right" className="w-[600px] sm:w-[700px] overflow-y-auto">
        <SheetHeader className="pb-4 border-b">
          <div className="flex items-center justify-between">
            <SheetTitle className="text-lg">{t('trialBalance.drillDown.title')}</SheetTitle>
            <Button variant="ghost" size="icon" onClick={onClose}>
              <X className="h-4 w-4" />
            </Button>
          </div>
          <div className="text-sm text-muted-foreground">
            <div className="font-medium text-foreground">
              {accountCode} - {accountName}
            </div>
            <Badge variant="outline" className="mt-1">
              {getAmountTypeLabel(amountType, t)}
            </Badge>
          </div>
        </SheetHeader>

        <div className="mt-4 space-y-4">
          {/* Summary */}
          {data && (
            <div className="flex items-center justify-between text-sm bg-muted/50 p-3 rounded-md">
              <span>{t('trialBalance.drillDown.voucherCount', { count: data.voucherCount })}</span>
              {data.totalAmount > 0 && (
                <span className="font-medium">
                  {t('trialBalance.drillDown.totalAmount')}: {formatCurrency(data.totalAmount)}
                </span>
              )}
            </div>
          )}

          {/* Voucher table */}
          {loading ? (
            <div className="space-y-2">
              {[...Array(5)].map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : data && data.vouchers.length > 0 ? (
            <>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('trialBalance.drillDown.date')}</TableHead>
                      <TableHead>{t('trialBalance.drillDown.voucherNumber')}</TableHead>
                      <TableHead>{t('trialBalance.drillDown.description')}</TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.drillDown.debit')}
                      </TableHead>
                      <TableHead className="text-right">
                        {t('trialBalance.drillDown.credit')}
                      </TableHead>
                      <TableHead className="w-10"></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.vouchers.map((voucher) => (
                      <TableRow
                        key={voucher.id}
                        className="cursor-pointer hover:bg-muted/50"
                        onClick={() => handleVoucherClick(voucher)}
                      >
                        <TableCell className="font-mono text-sm">
                          {formatDate(voucher.voucherDate)}
                        </TableCell>
                        <TableCell>
                          <span className="text-blue-600 hover:underline">
                            {voucher.voucherNumber}
                          </span>
                        </TableCell>
                        <TableCell className="max-w-[200px] truncate" title={voucher.description}>
                          {voucher.description || '-'}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {voucher.debit > 0 ? formatCurrency(voucher.debit) : '-'}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {voucher.credit > 0 ? formatCurrency(voucher.credit) : '-'}
                        </TableCell>
                        <TableCell>
                          <ExternalLink className="h-4 w-4 text-muted-foreground" />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted-foreground">
                    {t('trialBalance.pagination.page')} {page + 1} {t('trialBalance.pagination.of')}{' '}
                    {totalPages}
                  </span>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0 || loading}
                    >
                      <ChevronLeft className="h-4 w-4" />
                      {t('trialBalance.pagination.previous')}
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1 || loading}
                    >
                      {t('trialBalance.pagination.next')}
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="text-center text-muted-foreground py-8">
              {t('trialBalance.drillDown.noVouchers')}
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
